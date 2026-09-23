/* ============================================================
 * PYQ Hub Admin — UI (vanilla JS, no build step).
 * If/when Supabase lands, only js/db.js gets swapped.
 * ============================================================ */
"use strict";

const state = {
  view: "dashboard",
  subjects: [],
  papers: [],
  notes: [],
  paperFilter: { branch: "", subject: "", q: "" },
  subjectFilter: { branch: "ENTC", year: "" },
  noteFilter: { branch: "" },
  pendingFile: null, // File object chosen in the paper modal
};

/* ---------------- helpers ---------------- */
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s == null ? "" : s)
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;").replace(/'/g, "&#39;");

const fmtBytes = (n) => {
  if (!n && n !== 0) return "—";
  if (n < 1024) return n + " B";
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
  return (n / (1024 * 1024)).toFixed(2) + " MB";
};

const fmtDate = (ts) => {
  if (!ts) return "—";
  try { return new Date(ts).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }); }
  catch { return "—"; }
};

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").slice(0, 40);

function toast(msg, kind) {
  const el = document.createElement("div");
  el.className = "toast" + (kind ? " " + kind : "");
  el.textContent = msg;
  $("#toastWrap").appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

function openModal(title, bodyHTML, buttons) {
  $("#modalTitle").textContent = title;
  $("#modalBody").innerHTML = bodyHTML;
  const foot = $("#modalFoot");
  foot.innerHTML = "";
  (buttons || [{ label: "Close" }]).forEach((b) => {
    const btn = document.createElement("button");
    btn.className = "btn " + (b.kind || "");
    btn.textContent = b.label;
    btn.onclick = async () => {
      try {
        if (b.onClick) await b.onClick();
      } catch (err) {
        toast(err.message || "Something failed", "err");
        return; // keep the modal open so nothing is lost
      }
      if (!b.keepOpen) closeModal();
    };
    foot.appendChild(btn);
  });
  $("#modalBackdrop").hidden = false;
}
function closeModal() {
  $("#modalBackdrop").hidden = true;
  state.pendingFile = null;
}
$("#modalClose").addEventListener("click", closeModal);
$("#modalBackdrop").addEventListener("click", (e) => {
  if (e.target.id === "modalBackdrop") closeModal();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !$("#modalBackdrop").hidden) closeModal();
});

function confirmDialog(title, message, confirmLabel, onConfirm) {
  openModal(title, `<p>${esc(message)}</p>`, [
    { label: "Cancel", kind: "secondary" },
    { label: confirmLabel || "Delete", kind: "danger", onClick: onConfirm },
  ]);
}

/* ---------------- boot ---------------- */
document.addEventListener("DOMContentLoaded", async () => {
  await DB.open();
  document.querySelectorAll("#mainNav .nav-item").forEach((btn) => {
    btn.addEventListener("click", () => switchView(btn.dataset.view));
  });
  $("#btnLogout").addEventListener("click", () => {
    DB.logout();
    showLogin();
    toast("Logged out");
  });
  $("#btnLogin").addEventListener("click", doLogin);
  $("#loginPassword").addEventListener("keydown", (e) => { if (e.key === "Enter") doLogin(); });
  if (!DB.loggedIn()) {
    showLogin();
    return;
  }
  await bootApp();
});

async function doLogin() {
  const btn = $("#btnLogin");
  $("#loginError").textContent = "";
  btn.disabled = true;
  btn.textContent = "Signing in…";
  try {
    const s = await DB.login($("#loginEmail").value.trim(), $("#loginPassword").value);
    $("#loginBackdrop").hidden = true;
    await bootApp();
    toast("Welcome, " + (s.email || "admin"), "ok");
  } catch (err) {
    $("#loginError").textContent = err.message;
  } finally {
    btn.disabled = false;
    btn.textContent = "Sign in";
  }
}

function showLogin() {
  $("#loginPassword").value = "";
  $("#loginError").textContent = "";
  $("#loginBackdrop").hidden = false;
  setTimeout(() => $("#loginEmail").focus(), 100);
}

async function bootApp() {
  try {
    await reloadAll();
  } catch (err) {
    showLogin();
    $("#loginError").textContent = "Session expired — please sign in again.";
    return;
  }
  const s = JSON.parse(localStorage.getItem("pyq-admin-session") || "{}");
  $("#adminEmail").textContent = s.email || "";
  toast("Connected to Supabase ☁", "ok");
  switchView("dashboard");
}

async function reloadAll() {
  const [subjects, papers, notes] = await Promise.all([
    DB.getAll("subjects"), DB.getAll("papers"), DB.getAll("notes"),
  ]);
  state.subjects = subjects.sort((a, b) => a.branchCode.localeCompare(b.branchCode) || a.academicYear - b.academicYear || a.name.localeCompare(b.name));
  state.papers = papers.sort((a, b) => String(b.year).localeCompare(String(a.year)));
  state.notes = notes.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
}

function switchView(view) {
  state.view = view;
  document.querySelectorAll("#mainNav .nav-item").forEach((b) =>
    b.classList.toggle("active", b.dataset.view === view));
  document.querySelectorAll(".view").forEach((s) =>
    s.classList.toggle("active", s.id === "view-" + view));
  ({ dashboard: renderDashboard, papers: renderPapers, subjects: renderSubjects, notes: renderNotes, data: renderData })[view]();
}

const subjectOfPaper = (p) =>
  state.subjects.find((s) => s.name === p.subjectName && s.branchCode === p.branchCode);

const papersOfSubject = (branchCode, subjectName) =>
  state.papers.filter((p) => p.branchCode === branchCode && p.subjectName === subjectName);

/* ============================================================
 * DASHBOARD
 * ============================================================ */
async function renderDashboard() {
  $("#viewTitle").textContent = "Dashboard";
  $("#viewSubtitle").textContent = "Overview of your catalog";
  $("#topbarActions").innerHTML = "";
  const withFiles = state.papers.filter((p) => p.fileId).length;
  const recent = state.papers.filter((p) => p.uploadedAt)
    .sort((a, b) => b.uploadedAt - a.uploadedAt).slice(0, 5);

  $("#view-dashboard").innerHTML = `
    <div class="cards">
      <div class="card"><div class="stat-num">${state.subjects.length}</div><div class="stat-label">Subjects</div></div>
      <div class="card"><div class="stat-num">${state.papers.length}</div><div class="stat-label">Question papers</div></div>
      <div class="card"><div class="stat-num">${state.notes.length}</div><div class="stat-label">Study notes</div></div>
      <div class="card"><div class="stat-num">${withFiles}</div><div class="stat-label">PDFs attached</div></div>
    </div>
    <div class="panel">
      <h3>Cloud storage</h3>
      <p class="muted" style="margin:0">☁ Connected to Supabase (Mumbai) · <b style="color:var(--text)">${withFiles} PDFs</b> in the <span class="mono">papers</span> bucket · students download straight from here.</p>
    </div>
    <div class="panel">
      <h3>Recently uploaded PDFs</h3>
      ${recent.length === 0
        ? `<p class="muted" style="margin:0">Nothing uploaded yet — open <b>Papers → Upload paper</b> to add your first PDF.</p>`
        : `<ul class="list-plain">${recent.map((p) =>
            `<li><span><b>${esc(p.title)}</b><br><span class="muted">${esc(p.branchCode)} · ${esc(p.subjectName)} · ${esc(p.year)}</span></span><span class="badge green">PDF ✓</span></li>`).join("")}</ul>`}
    </div>`;
}

/* ============================================================
 * PAPERS
 * ============================================================ */
function renderPapers() {
  $("#viewTitle").textContent = "Question Papers";
  $("#viewSubtitle").textContent = "Upload and manage PDFs, branch → year → subject";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnUpload">+ Upload paper</button>`;
  $("#btnUpload").addEventListener("click", () => openPaperModal(null));

  const f = state.paperFilter;
  const branchOpts = [`<option value="">All branches</option>`,
    ...BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code} — ${esc(b.fullName)}</option>`)].join("");
  const subjectPool = state.subjects.filter((s) => !f.branch || s.branchCode === f.branch);
  const subjectOpts = [`<option value="">All subjects</option>`,
    ...subjectPool.map((s) => `<option value="${esc(s.name)}" ${f.subject === s.name ? "selected" : ""}>${esc(s.name)} (${esc(s.branchCode)} · Y${esc(s.academicYear)})</option>`)].join("");

  const rows = state.papers.filter((p) => {
    if (f.branch && p.branchCode !== f.branch) return false;
    if (f.subject && p.subjectName !== f.subject) return false;
    if (f.q && !(p.title + " " + p.subjectName + " " + p.year).toLowerCase().includes(f.q.toLowerCase())) return false;
    return true;
  });

  $("#view-papers").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="fBranch">${branchOpts}</select>
        <select id="fSubject">${subjectOpts}</select>
        <input id="fQ" type="search" placeholder="Search title / year..." value="${esc(f.q)}" />
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Title</th><th>Branch</th><th>Subject</th><th>Exam year</th><th>PDF</th><th></th></tr></thead>
        <tbody>
          ${rows.length === 0 ? `<tr><td colspan="6" class="empty">No papers match. Adjust filters or upload one.</td></tr>` : rows.map((p) => `
          <tr>
            <td><b>${esc(p.title)}</b><br><span class="muted">${esc(p.examType || "")} · ${esc(p.duration || "")} · ${p.maxMarks || ""} marks</span></td>
            <td><span class="badge">${esc(p.branchCode)}</span></td>
            <td>${esc(p.subjectName)}</td>
            <td>${esc(p.year)}</td>
            <td>${p.fileId
              ? `<span class="badge green">PDF ✓ ${esc(p.fileSize || "")}</span>`
              : `<span class="badge grey">No file</span>`}</td>
            <td><div class="row-actions">
              ${p.fileId ? `<button class="icon-btn" data-act="view" data-id="${esc(p.id)}" title="Open PDF">⤴</button>` : ""}
              <button class="icon-btn" data-act="edit" data-id="${esc(p.id)}" title="Edit">✎</button>
              <button class="icon-btn" data-act="del" data-id="${esc(p.id)}" title="Delete">🗑</button>
            </div></td>
          </tr>`).join("")}
        </tbody>
      </table></div>
      <p class="muted" style="margin:12px 0 0">${rows.length} paper(s) shown</p>
    </div>`;

  $("#fBranch").addEventListener("change", (e) => { state.paperFilter.branch = e.target.value; state.paperFilter.subject = ""; renderPapers(); });
  $("#fSubject").addEventListener("change", (e) => { state.paperFilter.subject = e.target.value; renderPapers(); });
  $("#fQ").addEventListener("input", (e) => {
    state.paperFilter.q = e.target.value;
    clearTimeout(window.__pq);
    window.__pq = setTimeout(renderPapers, 250);
  });
  $("#view-papers").querySelectorAll("button[data-act]").forEach((b) => {
    const id = b.dataset.id;
    if (b.dataset.act === "edit") b.addEventListener("click", () => openPaperModal(state.papers.find((p) => p.id === id)));
    if (b.dataset.act === "del") b.addEventListener("click", () => deletePaper(id));
    if (b.dataset.act === "view") b.addEventListener("click", () => openPdf(id));
  });
}

function subjectSelectOptions(branchCode, selectedName) {
  return state.subjects
    .filter((s) => s.branchCode === branchCode)
    .map((s) => `<option value="${esc(s.name)}" ${s.name === selectedName ? "selected" : ""}>${esc(s.name)} (Y${s.academicYear})</option>`)
    .join("");
}

function openPaperModal(existing) {
  const p = existing || {
    branchCode: "ENTC", subjectName: "", title: "End Semester Examination 2025",
    examType: "End Semester Examination", year: String(new Date().getFullYear()),
    duration: "3 Hours", maxMarks: 100, fileFormat: "PDF", fileSize: "", sampleQuestions: [],
  };
  state.pendingFile = null;
  openModal(existing ? "Edit paper" : "Upload paper", `
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="mBranch">${BRANCHES.map((b) => `<option ${b.code === p.branchCode ? "selected" : ""}>${b.code}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Subject</label><select id="mSubject"></select></div>
    </div>
    <div class="field"><label>Paper title</label><input id="mTitle" value="${esc(p.title)}" /></div>
    <div class="field-row">
      <div class="field"><label>Exam type</label><input id="mExamType" value="${esc(p.examType || "")}" /></div>
      <div class="field"><label>Exam year</label><input id="mYear" value="${esc(p.year)}" /></div>
    </div>
    <div class="field-row">
      <div class="field"><label>Duration</label><input id="mDuration" value="${esc(p.duration || "3 Hours")}" /></div>
      <div class="field"><label>Max marks</label><input id="mMarks" type="number" value="${p.maxMarks || 100}" /></div>
    </div>
    <div class="field"><label>Sample questions (one per line, optional)</label>
      <textarea id="mSamples" rows="4">${esc((p.sampleQuestions || []).join("\n"))}</textarea>
    </div>
    <div class="field"><label>PDF file</label>
      <div class="dropzone" id="mDrop">Drop a PDF here or <strong>browse</strong>
        <input type="file" id="mFile" accept="application/pdf" hidden />
      </div>
      <div id="mFileInfo">${p.fileName ? `<div class="file-chip">📄 ${esc(p.fileName)} · ${esc(p.fileSize || "")} (already attached — choose a new file to replace)</div>` : `<p class="hint">No PDF attached yet. You can save metadata first and attach later.</p>`}</div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Upload", onClick: () => savePaper(existing) },
  ]);

  const branchSel = $("#mBranch"), subjectSel = $("#mSubject");
  const refreshSubjects = () => { subjectSel.innerHTML = subjectSelectOptions(branchSel.value, p.subjectName || undefined); };
  branchSel.addEventListener("change", () => { p.subjectName = ""; refreshSubjects(); });
  refreshSubjects();

  const drop = $("#mDrop"), fileInput = $("#mFile");
  drop.addEventListener("click", () => fileInput.click());
  ["dragover", "dragenter"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add("over"); }));
  ["dragleave", "drop"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove("over"); }));
  drop.addEventListener("drop", (e) => { if (e.dataTransfer.files.length) pickFile(e.dataTransfer.files[0]); });
  fileInput.addEventListener("change", () => { if (fileInput.files.length) pickFile(fileInput.files[0]); });

  function pickFile(file) {
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      toast("Please choose a PDF file", "err"); return;
    }
    state.pendingFile = file;
    $("#mFileInfo").innerHTML = `<div class="file-chip">📄 ${esc(file.name)} · ${fmtBytes(file.size)} (will attach on save)</div>`;
  }
}

async function savePaper(existing) {
  const branchCode = $("#mBranch").value;
  const subjectName = $("#mSubject").value;
  const title = $("#mTitle").value.trim();
  const year = $("#mYear").value.trim();
  if (!subjectName) { toast("Pick a subject first", "err"); return; }
  if (!title || !year) { toast("Title and exam year are required", "err"); return; }

  let id = existing ? existing.id
    : (slug(branchCode + "_" + subjectName) + "_" + slug(year));
  if (!existing) {
    let n = 2;
    while (state.papers.some((p) => p.id === id)) id = slug(branchCode + "_" + subjectName) + "_" + slug(year) + "_" + (n++);
  }
  const samples = $("#mSamples").value.split("\n").map((s) => s.trim()).filter(Boolean);
  const paper = {
    id,
    title,
    subjectName,
    branchCode,
    year,
    examType: $("#mExamType").value.trim(),
    duration: $("#mDuration").value.trim(),
    maxMarks: parseInt($("#mMarks").value, 10) || 100,
    fileFormat: "PDF",
    fileSize: existing ? (existing.fileSize || "") : "",
    sampleQuestions: samples,
    fileId: existing ? (existing.fileId || null) : null,
    fileName: existing ? (existing.fileName || null) : null,
    uploadedAt: existing ? (existing.uploadedAt || null) : null,
  };
  if (state.pendingFile) {
    const f = state.pendingFile;
    const filePath = id + ".pdf";
    if (existing && existing.fileId && existing.fileId !== filePath) {
      try { await DB.delFile(existing.fileId); } catch { /* already gone */ }
    }
    await DB.putFile(filePath, f);
    paper.fileId = filePath;
    paper.fileName = f.name;
    paper.fileSize = fmtBytes(f.size);
    paper.uploadedAt = Date.now();
  }
  await DB.put("papers", paper);
  toast(existing ? "Paper updated" : "Paper saved", "ok");
  await reloadAll();
  renderPapers();
}

async function deletePaper(id) {
  const p = state.papers.find((x) => x.id === id);
  confirmDialog("Delete paper?", `"${p.title}" (${p.branchCode} · ${p.subjectName} · ${p.year}) will be removed. This cannot be undone.`, "Delete", async () => {
    await DB.del("papers", id);
    if (p.fileId) await DB.delFile(p.fileId);
    toast("Paper deleted", "ok");
    await reloadAll();
    renderPapers();
  });
}

async function openPdf(id) {
  const p = state.papers.find((x) => x.id === id);
  if (!p || !p.fileId) return;
  window.open(DB.fileUrl(p.fileId), "_blank");
}

/* ============================================================
 * SUBJECTS
 * ============================================================ */
function renderSubjects() {
  $("#viewTitle").textContent = "Subjects";
  $("#viewSubtitle").textContent = "Branch → year → subjects (mirrors the app)";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnAddSub">+ Add subject</button>`;
  $("#btnAddSub").addEventListener("click", () => openSubjectModal(null));

  const f = state.subjectFilter;
  const rows = state.subjects.filter((s) =>
    (!f.branch || s.branchCode === f.branch) && (!f.year || s.academicYear === Number(f.year)));

  $("#view-subjects").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="sBranch">${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}</select>
        <select id="sYear">
          <option value="">All years</option>
          ${YEARS.map((y) => `<option value="${y.year}" ${String(f.year) === String(y.year) ? "selected" : ""}>${y.label}</option>`).join("")}
        </select>
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Subject</th><th>Branch</th><th>Year</th><th>Papers</th><th></th></tr></thead>
        <tbody>
          ${rows.length === 0 ? `<tr><td colspan="5" class="empty">No subjects here yet — add one.</td></tr>` : rows.map((s) => {
            const n = papersOfSubject(s.branchCode, s.name).length;
            return `<tr>
              <td><b>${esc(s.name)}</b></td>
              <td><span class="badge">${esc(s.branchCode)}</span></td>
              <td>${yearLabel(s.academicYear)}</td>
              <td>${n}</td>
              <td><div class="row-actions">
                <button class="icon-btn" data-act="edit" data-id="${esc(s.id)}" title="Edit">✎</button>
                <button class="icon-btn" data-act="del" data-id="${esc(s.id)}" title="Delete">🗑</button>
              </div></td>
            </tr>`;
          }).join("")}
        </tbody>
      </table></div>
    </div>`;

  $("#sBranch").addEventListener("change", (e) => { state.subjectFilter.branch = e.target.value; renderSubjects(); });
  $("#sYear").addEventListener("change", (e) => { state.subjectFilter.year = e.target.value; renderSubjects(); });
  $("#view-subjects").querySelectorAll("button[data-act]").forEach((b) => {
    const sub = state.subjects.find((s) => s.id === b.dataset.id);
    if (b.dataset.act === "edit") b.addEventListener("click", () => openSubjectModal(sub));
    if (b.dataset.act === "del") b.addEventListener("click", () => deleteSubject(sub));
  });
}

function openSubjectModal(existing) {
  const s = existing || { branchCode: "ENTC", academicYear: 2, name: "", paperCount: 2, iconName: "graphic_eq" };
  const icons = ["graphic_eq", "memory", "cell_tower", "smart_toy", "computer", "language"];
  openModal(existing ? "Edit subject" : "Add subject", `
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="sBranchM">${BRANCHES.map((b) => `<option ${b.code === s.branchCode ? "selected" : ""}>${b.code}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Year</label>
        <select id="sYearM">${YEARS.map((y) => `<option value="${y.year}" ${s.academicYear === y.year ? "selected" : ""}>${y.label}</option>`).join("")}</select>
      </div>
    </div>
    <div class="field"><label>Subject name</label><input id="sName" value="${esc(s.name)}" placeholder="e.g. Data Structures & Algorithms" /></div>
    <div class="field-row">
      <div class="field"><label>Shown paper count</label><input id="sCount" type="number" min="0" value="${s.paperCount}" /><p class="hint">Display number shown in the app (can exceed uploaded files).</p></div>
      <div class="field"><label>Icon</label>
        <select id="sIcon">${icons.map((i) => `<option ${i === s.iconName ? "selected" : ""}>${i}</option>`).join("")}</select>
      </div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Add subject", onClick: async () => {
        const name = $("#sName").value.trim();
        if (!name) { toast("Subject name is required", "err"); return; }
        const branchCode = $("#sBranchM").value;
        const academicYear = Number($("#sYearM").value);
        const dupe = state.subjects.find((x) => x.name.toLowerCase() === name.toLowerCase() && x.branchCode === branchCode && (!existing || x.id !== existing.id));
        if (dupe) { toast("This subject already exists for " + branchCode, "err"); return; }
        const id = existing ? existing.id : (slug(branchCode + "_" + name));
        await DB.put("subjects", {
          id, name, branchCode, academicYear,
          paperCount: parseInt($("#sCount").value, 10) || 0,
          iconName: $("#sIcon").value,
        });
        toast(existing ? "Subject updated" : "Subject added", "ok");
        await reloadAll();
        renderSubjects();
      } },
  ]);
}

function deleteSubject(s) {
  const n = papersOfSubject(s.branchCode, s.name).length;
  if (n > 0) {
    toast(`Cannot delete — ${n} paper(s) still use this subject. Delete them first.`, "err");
    return;
  }
  confirmDialog("Delete subject?", `"${s.name}" (${s.branchCode}) will be removed.`, "Delete", async () => {
    await DB.del("subjects", s.id);
    toast("Subject deleted", "ok");
    await reloadAll();
    renderSubjects();
  });
}

/* ============================================================
 * NOTES
 * ============================================================ */
function renderNotes() {
  $("#viewTitle").textContent = "Study Notes";
  $("#viewSubtitle").textContent = "Admin-published notes, branch → year";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnAddNote">+ Add note</button>`;
  $("#btnAddNote").addEventListener("click", () => openNoteModal(null));

  const f = state.noteFilter;
  const rows = state.notes.filter((n) => !f.branch || n.branchCode === f.branch);

  $("#view-notes").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="nBranch">
          <option value="">All branches</option>
          ${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}
        </select>
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Title</th><th>Branch</th><th>Year</th><th>Subject</th><th>PDF</th><th>Updated</th><th></th></tr></thead>
        <tbody>
          ${rows.length === 0 ? `<tr><td colspan="7" class="empty">No notes yet — add one.</td></tr>` : rows.map((n) => `
          <tr>
            <td><b>${esc(n.title)}</b><br><span class="muted">${esc((n.content || "").slice(0, 80))}${(n.content || "").length > 80 ? "…" : ""}</span></td>
            <td><span class="badge">${esc(n.branchCode)}</span></td>
            <td>${yearLabel(n.academicYear)}</td>
            <td>${esc(n.subjectName || "—")}</td>
            <td>${n.storagePath ? `<span class="badge green">PDF ✓</span>` : `<span class="muted">—</span>`}</td>
            <td>${fmtDate(n.updatedAt)}</td>
            <td><div class="row-actions">
              ${n.storagePath ? `<button class="icon-btn" data-act="open" data-id="${n.id}" title="Open PDF">📄</button>` : ""}
              <button class="icon-btn" data-act="edit" data-id="${n.id}" title="Edit">✎</button>
              <button class="icon-btn" data-act="del" data-id="${n.id}" title="Delete">🗑</button>
            </div></td>
          </tr>`).join("")}
        </tbody>
      </table></div>
    </div>`;

  $("#nBranch").addEventListener("change", (e) => { state.noteFilter.branch = e.target.value; renderNotes(); });
  $("#view-notes").querySelectorAll("button[data-act]").forEach((b) => {
    const id = Number(b.dataset.id);
    const note = state.notes.find((n) => n.id === id);
    if (b.dataset.act === "edit") b.addEventListener("click", () => openNoteModal(note));
    if (b.dataset.act === "open") b.addEventListener("click", () => {
      if (note.storagePath) window.open(DB.fileUrl(note.storagePath), "_blank");
      else toast("No PDF attached to this note yet", "err");
    });
    if (b.dataset.act === "del") b.addEventListener("click", () => {
      confirmDialog("Delete note?", `"${note.title}" will be removed.`, "Delete", async () => {
        await DB.del("notes", id);
        if (note.storagePath) { try { await DB.delFile(note.storagePath); } catch { /* already gone */ } }
        toast("Note deleted", "ok");
        await reloadAll();
        renderNotes();
      });
    });
  });
}

function openNoteModal(existing) {
  const n = existing || { title: "", content: "", subjectName: "", branchCode: "COMMON", academicYear: 1, storagePath: "" };
  state.pendingFile = null;
  openModal(existing ? "Edit note" : "Add note", `
    <div class="field"><label>Title</label><input id="nTitle" value="${esc(n.title)}" /></div>
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="nBranchM">${BRANCHES.map((b) => `<option value="${b.code}" ${b.code === n.branchCode ? "selected" : ""}>${b.code}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Year</label>
        <select id="nYearM">${YEARS.map((y) => `<option value="${y.year}" ${n.academicYear === y.year ? "selected" : ""}>${y.label}</option>`).join("")}</select>
      </div>
    </div>
    <div class="field"><label>Subject (optional)</label><input id="nSubject" value="${esc(n.subjectName || "")}" placeholder="e.g. Signals and Systems" /></div>
    <div class="field"><label>Description (optional — list subtitle)</label>
      <textarea id="nContent" rows="3" placeholder="One-line summary shown under the title in the app">${esc(n.content || "")}</textarea>
    </div>
    <div class="field"><label>Note PDF</label>
      <div class="dropzone" id="nDrop">Drop a PDF here or <strong>browse</strong>
        <input type="file" id="nFile" accept="application/pdf" hidden />
      </div>
      <div id="nFileInfo">${n.storagePath ? `<div class="file-chip">📄 ${esc(n.fileName || n.storagePath.split("/").pop())} (already attached — choose a new file to replace)</div>` : `<p class="hint">No PDF attached yet. The app opens this file when the note is tapped.</p>`}</div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Add note", onClick: () => saveNote(existing) },
  ]);

  const branchSel = $("#nBranchM"), yearSel = $("#nYearM");
  const syncYear = () => {
    if (branchSel.value === "COMMON") {
      yearSel.value = "1";
      yearSel.disabled = true;
    } else {
      yearSel.disabled = false;
      if (yearSel.value === "1" && branchSel.value !== "COMMON") yearSel.value = "2";
    }
  };
  branchSel.addEventListener("change", syncYear);
  syncYear();

  const drop = $("#nDrop"), fileInput = $("#nFile");
  drop.addEventListener("click", () => fileInput.click());
  ["dragover", "dragenter"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add("over"); }));
  ["dragleave", "drop"].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove("over"); }));
  drop.addEventListener("drop", (e) => { if (e.dataTransfer.files.length) pickNoteFile(e.dataTransfer.files[0]); });
  fileInput.addEventListener("change", () => { if (fileInput.files.length) pickNoteFile(fileInput.files[0]); });

  function pickNoteFile(file) {
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      toast("Please choose a PDF file", "err"); return;
    }
    state.pendingFile = file;
    $("#nFileInfo").innerHTML = `<div class="file-chip">📄 ${esc(file.name)} · ${fmtBytes(file.size)} (will attach on save)</div>`;
  }
}

async function saveNote(existing) {
  const title = $("#nTitle").value.trim();
  const content = $("#nContent").value.trim(); // optional description only
  if (!title) { toast("Title is required", "err"); return; }
  const branchCode = $("#nBranchM").value;
  const academicYear = branchCode === "COMMON" ? 1 : Number($("#nYearM").value);

  const row = {
    title,
    content,
    subjectName: $("#nSubject").value.trim(),
    branchCode,
    academicYear,
    fileUrl: existing ? (existing.fileUrl || "") : "",
    storagePath: existing ? (existing.storagePath || "") : "",
    fileName: existing ? (existing.fileName || null) : null,
    updatedAt: Date.now(),
  };
  if (existing) row.id = existing.id;

  if (state.pendingFile) {
    const f = state.pendingFile;
    const basePath = "notes/";
    const filePath = basePath + slug(title) + "_" + Date.now() + ".pdf";
    if (existing && existing.storagePath && existing.storagePath !== filePath) {
      try { await DB.delFile(existing.storagePath); } catch { /* already gone */ }
    }
    await DB.putFile(filePath, f);
    row.storagePath = filePath;
    row.fileName = f.name;
    row.fileUrl = DB.fileUrl(filePath);
  }

  await DB.put("notes", row);
  toast(existing ? "Note updated" : "Note added", "ok");
  await reloadAll();
  renderNotes();
}

/* ============================================================
 * BACKUP & SYNC
 * ============================================================ */
function renderData() {
  $("#viewTitle").textContent = "Backup & Sync";
  $("#viewSubtitle").textContent = "Move this catalog to the backend when ready";
  $("#topbarActions").innerHTML = "";
  $("#view-data").innerHTML = `
    <div class="panel">
      <h3>Export catalog (JSON)</h3>
      <p class="muted">Downloads <span class="mono">subjects + papers + notes</span> with the exact field names the Supabase tables use. PDF bytes are <b>not</b> inside (they stay in Supabase Storage) — papers carry a <span class="mono">hasFile</span> flag so you know which to re-upload after an import.</p>
      <button class="btn" id="btnExport">⇅ Export JSON</button>
    </div>
    <div class="panel">
      <h3>Import catalog (JSON)</h3>
      <p class="muted">Replaces subjects, papers and notes with a previously exported file. Attached PDFs are dropped on import (re-attach afterwards).</p>
      <input type="file" id="importFile" accept="application/json" hidden />
      <button class="btn secondary" id="btnImport">Choose backup file…</button>
    </div>
    <div class="panel">
      <h3>Backend field map</h3>
      <p class="muted">Create these Supabase tables later — names and fields already match:</p>
      <div class="codebox">subjects(id, name, branchCode, academicYear, paperCount, iconName)
papers(id, title, subjectName, branchCode, year, examType,
       fileFormat, fileSize, duration, maxMarks,
       sampleQuestions[], storagePath)
notes(id, title, content, subjectName, branchCode,
      academicYear, fileUrl, storagePath, updatedAt)</div>
    </div>
    <div class="panel">
      <h3 style="color:var(--danger)">Danger zone</h3>
      <p class="muted">Erase ALL subjects, papers and notes from the cloud database. Uploaded PDFs stay in Storage — delete them from each paper first if needed.</p>
      <button class="btn danger" id="btnReset">Erase cloud data</button>
    </div>`;

  $("#btnExport").addEventListener("click", async () => {
    const data = await DB.exportJSON();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    const d = new Date();
    a.download = `pyq-hub-backup-${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    toast("Backup downloaded", "ok");
  });
  $("#btnImport").addEventListener("click", () => $("#importFile").click());
  $("#importFile").addEventListener("change", async (e) => {
    if (!e.target.files.length) return;
    try {
      const data = JSON.parse(await e.target.files[0].text());
      confirmDialog("Import backup?", `This REPLACES all ${state.subjects.length} subjects, ${state.papers.length} papers and ${state.notes.length} notes currently here.`, "Import", async () => {
        await DB.importJSON(data);
        toast("Backup imported", "ok");
        await reloadAll();
        switchView("dashboard");
      });
    } catch (err) {
      toast("Could not read that file: " + err.message, "err");
    }
    e.target.value = "";
  });
  $("#btnReset").addEventListener("click", () => {
    confirmDialog("Erase everything?", "All subjects, papers and notes will be deleted from Supabase. This cannot be undone.", "Erase", async () => {
      await DB.resetToSeed();
      toast("Cloud data erased", "ok");
      await reloadAll();
      switchView("dashboard");
    });
  });
}
