/* ============================================================
 * PYQ Hub Admin — data layer (Supabase backend).
 *
 * Same API surface the UI was built against; underneath it is
 * PostgREST + Storage + Auth over plain fetch (no dependencies).
 * Tables: subjects, papers, notes. PDFs: storage bucket `papers`.
 * Reads are public; writes require the admin login (RLS enforced
 * server-side — the app can never bypass this).
 * ============================================================ */
"use strict";

const DB = (() => {
  const base = SUPABASE.url;
  const BUCKET = "papers";
  const SESSION_KEY = "pyq-admin-session";

  let session = null;
  try { session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null"); } catch { session = null; }

  const saveSession = () => {
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    else localStorage.removeItem(SESSION_KEY);
  };

  const headers = (authed, json) => {
    const h = { apikey: SUPABASE.anonKey };
    h["Authorization"] = "Bearer " + (authed && session ? session.access_token : SUPABASE.anonKey);
    if (json) h["Content-Type"] = "application/json";
    return h;
  };

  async function api(path, { method = "GET", body, authed = false, prefer } = {}) {
    if (authed) await ensureAuth();
    const res = await fetch(base + path, {
      method,
      headers: { ...headers(authed, body !== undefined), ...(prefer ? { Prefer: prefer } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (res.status === 401 && authed) { logout(); throw new Error("Session expired — please log in again."); }
    if (!res.ok) {
      let msg = "Request failed (" + res.status + ")";
      try { const j = await res.json(); if (j.message) msg = j.message; else if (j.error) msg = j.error; } catch { /* ignore */ }
      throw new Error(msg);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  /* ---------------- auth ---------------- */
  async function login(email, password) {
    const res = await fetch(base + "/auth/v1/token?grant_type=password", {
      method: "POST",
      headers: { apikey: SUPABASE.anonKey, "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
      let msg = "Login failed (" + res.status + ")";
      try { const j = await res.json(); if (j.msg) msg = j.msg; else if (j.error_description) msg = j.error_description; } catch { /* ignore */ }
      throw new Error(msg);
    }
    const j = await res.json();
    session = { access_token: j.access_token, refresh_token: j.refresh_token, email: (j.user && j.user.email) || email, expires_at: Date.now() + (j.expires_in || 3600) * 1000 };
    saveSession();
    return session;
  }

  function logout() {
    if (session) {
      fetch(base + "/auth/v1/logout", {
        method: "POST",
        headers: { apikey: SUPABASE.anonKey, Authorization: "Bearer " + session.access_token },
      }).catch(() => {});
    }
    session = null;
    saveSession();
  }

  const loggedIn = () => !!session;

  async function ensureAuth() {
    if (!session) throw new Error("Not logged in.");
    if (session.expires_at && Date.now() > session.expires_at - 60000 && session.refresh_token) {
      const res = await fetch(base + "/auth/v1/token?grant_type=refresh_token", {
        method: "POST",
        headers: { apikey: SUPABASE.anonKey, "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: session.refresh_token }),
      });
      if (res.ok) {
        const j = await res.json();
        session.access_token = j.access_token;
        session.refresh_token = j.refresh_token || session.refresh_token;
        session.expires_at = Date.now() + (j.expires_in || 3600) * 1000;
        saveSession();
      } else {
        logout();
        throw new Error("Session expired — please log in again.");
      }
    }
  }

  /* ---------------- field mapping (snake_case <-> app camelCase) ---------------- */
  const fromRow = (table, r) => {
    if (table === "subjects") return { id: r.id, name: r.name, branchCode: r.branch_code, academicYear: r.academic_year, paperCount: r.paper_count, iconName: r.icon_name };
    if (table === "papers") return {
      id: r.id, title: r.title, subjectName: r.subject_name, branchCode: r.branch_code,
      year: r.year, examType: r.exam_type, fileFormat: r.file_format, fileSize: r.file_size,
      duration: r.duration, maxMarks: r.max_marks, sampleQuestions: r.sample_questions || [],
      fileId: r.storage_path || null,
      fileName: r.storage_path ? r.storage_path.split("/").pop() : null,
      uploadedAt: r.updated_at ? Date.parse(r.updated_at) : null,
    };
    if (table === "notes") return {
      id: r.id, title: r.title, content: r.content || "", subjectName: r.subject_name,
      branchCode: r.branch_code, academicYear: r.academic_year, fileUrl: r.file_url || "",
      storagePath: r.storage_path || "",
      fileName: r.storage_path ? r.storage_path.split("/").pop() : null,
      updatedAt: r.updated_at ? Date.parse(r.updated_at) : Date.now(),
    };
    return { id: r.id, title: r.title, content: r.content, subjectName: r.subject_name, branchCode: r.branch_code, academicYear: r.academic_year, fileUrl: r.file_url || "", updatedAt: r.updated_at ? Date.parse(r.updated_at) : Date.now() };
  };

  const toRow = (table, o) => {
    if (table === "subjects") return { id: o.id, name: o.name, branch_code: o.branchCode, academic_year: o.academicYear, paper_count: o.paperCount, icon_name: o.iconName };
    if (table === "papers") return {
      id: o.id, title: o.title, subject_name: o.subjectName, branch_code: o.branchCode,
      year: o.year, exam_type: o.examType, file_format: o.fileFormat, file_size: o.fileSize,
      duration: o.duration, max_marks: o.maxMarks, sample_questions: o.sampleQuestions || [],
      storage_path: o.fileId || "",
    };
    const row = { title: o.title, content: o.content || "", subject_name: o.subjectName || "", branch_code: o.branchCode || "", academic_year: o.academicYear || 1, file_url: o.fileUrl || "", storage_path: o.storagePath || "" };
    if (o.id) row.id = o.id;
    return row;
  };

  /* ---------------- CRUD (same names the UI already uses) ---------------- */
  const open = async () => true; // nothing local to open; session validated at boot

  const getAll = async (table) => {
    const rows = await api(`/rest/v1/${table}?select=*&order=${table === "papers" ? "year.desc" : table === "notes" ? "updated_at.desc" : "id.asc"}`, {});
    return (rows || []).map((r) => fromRow(table, r));
  };

  const put = async (table, obj) => {
    const rows = await api(`/rest/v1/${table}?on_conflict=id`, {
      method: "POST", body: toRow(table, obj), authed: true, prefer: "resolution=merge-duplicates,return=representation",
    });
    return rows && rows[0] ? fromRow(table, rows[0]) : null;
  };

  const del = async (table, key) => {
    await api(`/rest/v1/${table}?id=eq.${encodeURIComponent(key)}`, { method: "DELETE", authed: true });
  };

  const getMeta = async () => null;
  const seedIfEmpty = async () => false; // backend is pre-seeded by setup

  /* ---------------- files (Storage bucket) ---------------- */
  const putFile = async (path, blob) => {
    await ensureAuth();
    const res = await fetch(`${base}/storage/v1/object/${BUCKET}/${path}`, {
      method: "POST",
      headers: { apikey: SUPABASE.anonKey, Authorization: "Bearer " + session.access_token, "Content-Type": blob.type || "application/pdf", "x-upsert": "true" },
      body: blob,
    });
    if (res.status === 401) { logout(); throw new Error("Session expired — please log in again."); }
    if (!res.ok) throw new Error("Upload failed (" + res.status + ")");
    return { size: blob.size };
  };

  const fileUrl = (path) => `${base}/storage/v1/object/public/${BUCKET}/${path}`;

  const getFile = async (path) => ({ url: fileUrl(path) });

  const delFile = async (path) => {
    await api(`/storage/v1/object/${BUCKET}`, {
      method: "DELETE", body: { prefixes: [path] }, authed: true,
    });
  };

  /* ---------------- backup / sync ---------------- */
  const exportJSON = async () => {
    const [subjects, papers, notes] = await Promise.all([
      getAll("subjects"), getAll("papers"), getAll("notes"),
    ]);
    const cleanPapers = papers.map((p) => ({ ...p, hasFile: !!p.fileId }));
    return {
      app: "paperadda-admin",
      version: 2,
      backend: "supabase",
      exportedAt: new Date().toISOString(),
      counts: { subjects: subjects.length, papers: papers.length, notes: notes.length },
      subjects, papers: cleanPapers, notes,
    };
  };

  const importJSON = async (data) => {
    if (!data || !Array.isArray(data.subjects) || !Array.isArray(data.papers) || !Array.isArray(data.notes)) {
      throw new Error("Not a valid PYQ Hub backup file.");
    }
    // Replace table contents (files in Storage are untouched; re-link by re-uploading).
    for (const t of ["notes", "papers", "subjects"]) {
      const existing = await getAll(t);
      for (const row of existing) await del(t, row.id);
    }
    for (const s of data.subjects) await put("subjects", s);
    for (const p of data.papers) await put("papers", { ...p, fileId: null, fileName: null, uploadedAt: null });
    for (const n of data.notes) {
      const { id, ...rest } = n; // let the DB assign fresh ids
      await put("notes", rest);
    }
  };

  const resetToSeed = async () => {
    for (const t of ["notes", "papers", "subjects"]) {
      const existing = await getAll(t);
      for (const row of existing) await del(t, row.id);
    }
  };

  const storageEstimate = async () => null; // cloud: shown as connection status instead

  return { open, getAll, put, del, getMeta, seedIfEmpty, putFile, getFile, delFile, fileUrl, exportJSON, importJSON, resetToSeed, storageEstimate, login, logout, loggedIn };
})();
