/* ============================================================
 * PaperAdda Admin — UI (vanilla JS, no build step).
 * Workflow: Home tiles → one modal → done. Advanced fields hidden.
 * ============================================================ */
"use strict";

const PREFS_KEY = "paperadda-admin-prefs";

const state = {
  view: "dashboard",
  subjects: [],
  papers: [],
  notes: [],
  paperFilter: { branch: "", q: "" },
  subjectFilter: { branch: "", year: "" },
  noteFilter: { branch: "", subject: "", q: "" },
  selectedPapers: new Set(),
  pendingFile: null,
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

function loadPrefs() {
  try { return JSON.parse(localStorage.getItem(PREFS_KEY) || "{}"); }
  catch { return {}; }
}
function savePrefs(p) {
  localStorage.setItem(PREFS_KEY, JSON.stringify({ ...loadPrefs(), ...p }));
}

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
    btn.className = "btn " + (b.kind || "secondary");
    btn.textContent = b.label;
    btn.onclick = async () => {
      try {
        if (b.onClick) await b.onClick();
      } catch (err) {
        toast(err.message || "Something failed", "err");
        return;
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
  openModal(title, `<p class="muted">${esc(message)}</p>`, [
    { label: "Cancel", kind: "secondary" },
    { label: confirmLabel || "Delete", kind: "danger", onClick: onConfirm },
  ]);
}

function wireDropzone(dropId, fileId, infoId, onFile) {
  const drop = $("#" + dropId), input = $("#" + fileId);
  if (!drop || !input) return;
  drop.addEventListener("click", () => input.click());
  ["dragover", "dragenter"].forEach((ev) => drop.addEventListener(ev, (e) => {
    e.preventDefault(); drop.classList.add("over");
  }));
  ["dragleave", "drop"].forEach((ev) => drop.addEventListener(ev, (e) => {
    e.preventDefault(); drop.classList.remove("over");
  }));
  drop.addEventListener("drop", (e) => {
    if (e.dataTransfer.files.length) onFile(e.dataTransfer.files[0]);
  });
  input.addEventListener("change", () => {
    if (input.files.length) onFile(input.files[0]);
  });
}

function acceptPdf(file) {
  if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
    toast("Please choose a PDF", "err");
    return false;
  }
  return true;
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
  toast("Connected to Supabase", "ok");
  switchView("dashboard");
}

async function reloadAll() {
  const [subjects, papers, notes] = await Promise.all([
    DB.getAll("subjects"), DB.getAll("papers"), DB.getAll("notes"),
  ]);
  state.subjects = subjects.sort((a, b) =>
    a.branchCode.localeCompare(b.branchCode) ||
    a.academicYear - b.academicYear ||
    a.name.localeCompare(b.name));
  state.papers = papers.sort((a, b) => String(b.year).localeCompare(String(a.year)));
  state.notes = notes.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
}

function switchView(view) {
  state.view = view;
  document.querySelectorAll("#mainNav .nav-item").forEach((b) =>
    b.classList.toggle("active", b.dataset.view === view));
  document.querySelectorAll(".view").forEach((s) =>
    s.classList.toggle("active", s.id === "view-" + view));
  ({
    dashboard: renderDashboard,
    papers: renderPapers,
    subjects: renderSubjects,
    notes: renderNotes,
    data: renderData,
  })[view]();
}

const papersOfSubject = (branchCode, subjectName) =>
  state.papers.filter((p) => p.branchCode === branchCode && p.subjectName === subjectName);

/* ============================================================
 * HOME — three big actions, nothing else to think about
 * ============================================================ */
async function renderDashboard() {
  $("#viewTitle").textContent = "Home";
  $("#viewSubtitle").textContent = "Pick an action — the app updates as soon as you save";
  $("#topbarActions").innerHTML = "";

  const pdfsLive =
    state.papers.filter((p) => p.fileId).length +
    state.notes.filter((n) => n.storagePath).length;
  const recent = [
    ...state.papers.filter((p) => p.uploadedAt).map((p) => ({
      kind: "Paper", title: p.title, meta: `${p.branchCode} · ${p.subjectName} · ${p.year}`, at: p.uploadedAt, hasPdf: !!p.fileId,
    })),
    ...state.notes.filter((n) => n.updatedAt).map((n) => ({
      kind: "Note", title: n.title, meta: `${n.branchCode} · ${yearLabel(n.academicYear)}`, at: n.updatedAt, hasPdf: !!n.storagePath,
    })),
  ].sort((a, b) => b.at - a.at).slice(0, 6);

  $("#view-dashboard").innerHTML = `
    <div class="action-grid">
      <button class="action-tile" id="tilePaper">
        <div class="tile-ico">📄</div>
        <h3>Upload paper</h3>
        <p>Drop a PYQ PDF → branch, subject, year → save</p>
      </button>
      <button class="action-tile" id="tileNote">
        <div class="tile-ico">📝</div>
        <h3>Add note</h3>
        <p>Study notes with optional PDF for students</p>
      </button>
      <button class="action-tile" id="tileSubject">
        <div class="tile-ico">📚</div>
        <h3>Add subject</h3>
        <p>New subject under a branch &amp; year</p>
      </button>
    </div>

    <div class="cards">
      <div class="card"><div class="stat-num">${state.subjects.length}</div><div class="stat-label">Subjects</div></div>
      <div class="card"><div class="stat-num">${state.papers.length}</div><div class="stat-label">Question papers</div></div>
      <div class="card"><div class="stat-num">${state.notes.length}</div><div class="stat-label">Study notes</div></div>
      <div class="card"><div class="stat-num">${pdfsLive}</div><div class="stat-label">PDFs in cloud</div></div>
    </div>

    <div class="panel">
      <h3>Recent activity <span class="panel-sub">newest first</span></h3>
      ${recent.length === 0
        ? `<p class="muted" style="margin:0">Nothing yet — start with <b>Upload paper</b> above.</p>`
        : `<ul class="list-plain">${recent.map((r) => `
            <li>
              <span>
                <b>${esc(r.title)}</b><br>
                <span class="muted">${esc(r.kind)} · ${esc(r.meta)}</span>
              </span>
              <span class="badge ${r.hasPdf ? "green" : "grey"}">${r.hasPdf ? "PDF ✓" : "—"}</span>
            </li>`).join("")}</ul>`}
    </div>`;

  $("#tilePaper").addEventListener("click", () => openPaperModal(null));
  $("#tileNote").addEventListener("click", () => openNoteModal(null));
  $("#tileSubject").addEventListener("click", () => openSubjectModal(null));
}

/* ============================================================
 * PAPERS — branch filter + search only; title auto-filled
 * ============================================================ */
function renderPapers() {
  $("#viewTitle").textContent = "Papers";
  $("#viewSubtitle").textContent = "Question papers students open from the app";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnUpload">+ Upload paper</button>`;
  $("#btnUpload").addEventListener("click", () => openPaperModal(null));

  const f = state.paperFilter;
  const branchOpts = [`<option value="">All branches</option>`,
    ...BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code} — ${esc(b.fullName)}</option>`)].join("");

  const rows = state.papers.filter((p) => {
    if (f.branch && p.branchCode !== f.branch) return false;
    if (f.q && !(p.title + " " + p.subjectName + " " + p.year).toLowerCase().includes(f.q.toLowerCase())) return false;
    return true;
  });

  // Drop selections for papers that no longer exist
  for (const id of [...state.selectedPapers]) {
    if (!state.papers.some((p) => p.id === id)) state.selectedPapers.delete(id);
  }
  const selCount = rows.filter((p) => state.selectedPapers.has(p.id)).length;
  const allChecked = rows.length > 0 && selCount === rows.length;

  $("#view-papers").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="fBranch">${branchOpts}</select>
        <input id="fQ" type="search" placeholder="Search title, subject, year…" value="${esc(f.q)}" />
        ${selCount > 0 ? `
        <button class="btn danger small" id="btnBulkDel">🗑 Delete selected (${selCount})</button>
        <button class="btn ghost small" id="btnBulkClear">Clear</button>` : ""}
      </div>
      <div class="table-wrap"><table>
        <thead><tr>
          <th class="col-check"><input type="checkbox" id="checkAll" ${allChecked ? "checked" : ""} aria-label="Select all papers" /></th>
          <th>Paper</th><th>Branch</th><th>Year</th><th>PDF</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="6" class="empty"><strong>No papers match</strong>Adjust filters or hit Upload paper.</td></tr>`
            : rows.map((p) => `
          <tr class="${state.selectedPapers.has(p.id) ? "row-selected" : ""}">
            <td class="col-check"><input type="checkbox" data-check="${esc(p.id)}" ${state.selectedPapers.has(p.id) ? "checked" : ""} aria-label="Select ${esc(p.title)}" /></td>
            <td>
              <b>${esc(p.title)}</b><br>
              <span class="muted">${esc(p.subjectName)}${p.examType ? " · " + esc(p.examType) : ""}</span>
            </td>
            <td><span class="badge">${esc(p.branchCode)}</span></td>
            <td>${esc(p.year)}</td>
            <td>${p.fileId
              ? `<span class="badge green">PDF ✓ ${esc(p.fileSize || "")}</span>`
              : `<span class="badge grey">No file</span>`}</td>
            <td><div class="row-actions">
              ${p.fileId ? `<button class="icon-btn" data-act="view" data-id="${esc(p.id)}" title="Open PDF">⤴</button>` : ""}
              <button class="icon-btn" data-act="edit" data-id="${esc(p.id)}" title="Edit">✎</button>
              <button class="icon-btn danger" data-act="del" data-id="${esc(p.id)}" title="Delete">🗑</button>
            </div></td>
          </tr>`).join("")}
        </tbody>
      </table></div>
      <p class="muted" style="margin:12px 0 0">${rows.length} paper(s)${selCount ? ` · ${selCount} selected` : ""}</p>
    </div>`;

  $("#fBranch").addEventListener("change", (e) => { state.paperFilter.branch = e.target.value; renderPapers(); });
  $("#fQ").addEventListener("input", (e) => {
    state.paperFilter.q = e.target.value;
    clearTimeout(window.__pq);
    window.__pq = setTimeout(renderPapers, 200);
  });
  $("#checkAll").addEventListener("change", (e) => {
    if (e.target.checked) rows.forEach((p) => state.selectedPapers.add(p.id));
    else rows.forEach((p) => state.selectedPapers.delete(p.id));
    renderPapers();
  });
  const bulkDel = $("#btnBulkDel");
  if (bulkDel) bulkDel.addEventListener("click", deleteSelectedPapers);
  const bulkClear = $("#btnBulkClear");
  if (bulkClear) bulkClear.addEventListener("click", () => { state.selectedPapers.clear(); renderPapers(); });
  $("#view-papers").querySelectorAll("input[data-check]").forEach((cb) => {
    cb.addEventListener("change", () => {
      const id = cb.dataset.check;
      if (cb.checked) state.selectedPapers.add(id);
      else state.selectedPapers.delete(id);
      renderPapers();
    });
  });
  $("#view-papers").querySelectorAll("button[data-act]").forEach((b) => {
    const id = b.dataset.id;
    if (b.dataset.act === "edit") b.addEventListener("click", () => openPaperModal(state.papers.find((p) => p.id === id)));
    if (b.dataset.act === "del") b.addEventListener("click", () => deletePaper(id));
    if (b.dataset.act === "view") b.addEventListener("click", () => openPdf(id));
  });
}

function subjectSelectOptions(branchCode, selectedName, opts) {
  const allowEmpty = !!(opts && opts.allowEmpty);
  const pool = state.subjects.filter((s) => s.branchCode === branchCode);
  const empty = allowEmpty ? `<option value="">— none —</option>` : "";
  if (pool.length === 0) {
    return empty + `<option value="">— add a subject first —</option>`;
  }
  return empty + pool.map((s) =>
    `<option value="${esc(s.name)}" ${s.name === selectedName ? "selected" : ""}>${esc(s.name)} (Y${s.academicYear})</option>`
  ).join("");
}

function openPaperModal(existing) {
  const prefs = loadPrefs();
  const p = existing || {
    branchCode: prefs.branch || "ENTC",
    subjectName: "",
    title: "",
    examType: "End Semester Examination",
    year: String(new Date().getFullYear()),
    duration: "3 Hours",
    maxMarks: 100,
    fileFormat: "PDF",
    fileSize: "",
    sampleQuestions: [],
  };
  state.pendingFile = null;

  openModal(existing ? "Edit paper" : "Upload paper", `
    <div class="field"><label>Branch</label>
      <select id="mBranch">${BRANCHES.map((b) =>
        `<option value="${b.code}" ${b.code === p.branchCode ? "selected" : ""}>${b.code} — ${esc(b.fullName)}</option>`).join("")}</select>
    </div>
    <div class="field"><label>Subject</label><select id="mSubject"></select></div>
    <div class="field-row">
      <div class="field"><label>Exam year</label><input id="mYear" value="${esc(p.year)}" /></div>
      <div class="field"><label>Exam type</label>
        <select id="mExamType">
          ${["End Semester Examination", "Mid Semester Examination", "Unit Test", "Other"].map((t) =>
            `<option ${t === (p.examType || "End Semester Examination") ? "selected" : ""}>${t}</option>`).join("")}
        </select>
      </div>
    </div>
    <div class="field"><label>Paper title <span class="muted">(optional — auto if empty)</span></label>
      <input id="mTitle" value="${esc(p.title)}" placeholder="auto: subject + exam type + year" />
    </div>

    <button type="button" class="adv-toggle" id="advToggle">▸ More options (duration, marks…)</button>
    <div class="adv-fields" id="advFields" hidden>
      <div class="field-row">
        <div class="field"><label>Duration</label><input id="mDuration" value="${esc(p.duration || "3 Hours")}" /></div>
        <div class="field"><label>Max marks</label><input id="mMarks" type="number" value="${p.maxMarks || 100}" /></div>
      </div>
      <div class="field"><label>Sample questions (one per line)</label>
        <textarea id="mSamples" rows="3">${esc((p.sampleQuestions || []).join("\n"))}</textarea>
      </div>
    </div>

    <div class="field"><label>PDF file</label>
      <div class="dropzone" id="mDrop">
        <strong>Drop PDF or browse</strong>
        <span class="dz-hint">Students download this from the app</span>
        <input type="file" id="mFile" accept="application/pdf" hidden />
      </div>
      <div id="mFileInfo">${p.fileName
        ? `<div class="file-chip">📄 ${esc(p.fileName)} · ${esc(p.fileSize || "")} — attached (drop a new file to replace)</div>`
        : `<p class="hint">Required for students to open the paper.</p>`}</div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Upload", onClick: () => savePaper(existing) },
  ]);

  const branchSel = $("#mBranch"), subjectSel = $("#mSubject");
  const refreshSubjects = () => {
    subjectSel.innerHTML = subjectSelectOptions(branchSel.value, p.subjectName || undefined);
  };
  branchSel.addEventListener("change", () => {
    p.subjectName = "";
    refreshSubjects();
    savePrefs({ branch: branchSel.value });
  });
  refreshSubjects();
  savePrefs({ branch: p.branchCode });

  // Advanced toggle
  const advBtn = $("#advToggle"), advFields = $("#advFields");
  advBtn.addEventListener("click", () => {
    const open = advFields.hidden;
    advFields.hidden = !open;
    advBtn.textContent = (open ? "▾" : "▸") + (open ? " Hide options" : " More options (duration, marks…)");
  });

  wireDropzone("mDrop", "mFile", "mFileInfo", (file) => {
    if (!acceptPdf(file)) return;
    state.pendingFile = file;
    $("#mFileInfo").innerHTML = `<div class="file-chip">📄 ${esc(file.name)} · ${fmtBytes(file.size)} — ready to upload</div>`;
    // Suggest title from filename if empty
    const titleInput = $("#mTitle");
    if (!titleInput.value) {
      const base = file.name.replace(/\.pdf$/i, "").replace(/[_-]+/g, " ").trim();
      if (base && base.length < 80) titleInput.placeholder = base;
    }
  });
}

async function savePaper(existing) {
  const branchCode = $("#mBranch").value;
  const subjectName = $("#mSubject").value;
  const year = $("#mYear").value.trim();
  const examType = $("#mExamType").value;
  let title = $("#mTitle").value.trim();

  if (!subjectName) { toast("Pick a subject (add one from Subjects if missing)", "err"); return; }
  if (!year) { toast("Exam year is required", "err"); return; }
  if (!title) {
    title = `${subjectName} — ${examType} ${year}`;
  }

  let id = existing ? existing.id
    : (slug(branchCode + "_" + subjectName) + "_" + slug(year));
  if (!existing) {
    let n = 2;
    while (state.papers.some((p) => p.id === id)) {
      id = slug(branchCode + "_" + subjectName) + "_" + slug(year) + "_" + (n++);
    }
  }

  const advOpen = $("#advFields") && !$("#advFields").hidden;
  const samples = advOpen
    ? ($("#mSamples").value || "").split("\n").map((s) => s.trim()).filter(Boolean)
    : (existing ? existing.sampleQuestions || [] : []);

  const paper = {
    id,
    title,
    subjectName,
    branchCode,
    year,
    examType,
    duration: advOpen ? $("#mDuration").value.trim() : (existing?.duration || "3 Hours"),
    maxMarks: advOpen ? (parseInt($("#mMarks").value, 10) || 100) : (existing?.maxMarks || 100),
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
  savePrefs({ branch: branchCode });
  toast(existing ? "Paper updated" : "Paper published", "ok");
  await reloadAll();
  renderPapers();
}

async function deletePaper(id) {
  const p = state.papers.find((x) => x.id === id);
  confirmDialog("Delete paper?", `"${p.title}" (${p.branchCode} · ${p.subjectName}) will be removed from the app.`, "Delete", async () => {
    await DB.del("papers", id);
    if (p.fileId) { try { await DB.delFile(p.fileId); } catch { /* gone */ } }
    state.selectedPapers.delete(id);
    toast("Paper deleted", "ok");
    await reloadAll();
    renderPapers();
  });
}

async function deleteSelectedPapers() {
  const ids = [...state.selectedPapers];
  if (ids.length === 0) return;
  confirmDialog(
    `Delete ${ids.length} paper(s)?`,
    `Selected papers and their PDFs will be removed from the app. This cannot be undone.`,
    `Delete ${ids.length}`,
    async () => {
      let failed = 0;
      for (const id of ids) {
        const p = state.papers.find((x) => x.id === id);
        try {
          await DB.del("papers", id);
          if (p && p.fileId) { try { await DB.delFile(p.fileId); } catch { /* already gone */ } }
        } catch { failed++; }
      }
      state.selectedPapers.clear();
      toast(failed ? `Deleted with ${failed} error(s)` : `${ids.length} paper(s) deleted`, failed ? "err" : "ok");
      await reloadAll();
      renderPapers();
    }
  );
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
  $("#viewSubtitle").textContent = "What students see under each branch & year";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnAddSub">+ Add subject</button>`;
  $("#btnAddSub").addEventListener("click", () => openSubjectModal(null));

  const f = state.subjectFilter;
  const rows = state.subjects.filter((s) =>
    (!f.branch || s.branchCode === f.branch) && (!f.year || s.academicYear === Number(f.year)));

  $("#view-subjects").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="sBranch">
          <option value="">All branches</option>
          ${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}
        </select>
        <select id="sYear">
          <option value="">All years</option>
          ${YEARS.map((y) => `<option value="${y.year}" ${String(f.year) === String(y.year) ? "selected" : ""}>${y.label}</option>`).join("")}
        </select>
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Subject</th><th>Branch</th><th>Year</th><th>Papers</th><th></th></tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="5" class="empty"><strong>No subjects here</strong>Add one to start publishing papers.</td></tr>`
            : rows.map((s) => {
              const n = papersOfSubject(s.branchCode, s.name).length;
              return `<tr>
                <td><b>${esc(s.name)}</b></td>
                <td><span class="badge">${esc(s.branchCode)}</span></td>
                <td>${yearLabel(s.academicYear)}</td>
                <td>${n}</td>
                <td><div class="row-actions">
                  <button class="icon-btn" data-act="edit" data-id="${esc(s.id)}" title="Edit">✎</button>
                  <button class="icon-btn danger" data-act="del" data-id="${esc(s.id)}" title="Delete">🗑</button>
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
  const prefs = loadPrefs();
  const s = existing || {
    branchCode: prefs.branch || "ENTC",
    academicYear: 2,
    name: "",
    paperCount: 2,
    iconName: "graphic_eq",
  };
  const icons = ["graphic_eq", "memory", "cell_tower", "smart_toy", "computer", "language"];

  openModal(existing ? "Edit subject" : "Add subject", `
    <div class="field"><label>Subject name</label>
      <input id="sName" value="${esc(s.name)}" placeholder="e.g. Data Structures & Algorithms" />
    </div>
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="sBranchM">${BRANCHES.map((b) =>
          `<option value="${b.code}" ${b.code === s.branchCode ? "selected" : ""}>${b.code}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Year</label>
        <select id="sYearM">${YEARS.map((y) =>
          `<option value="${y.year}" ${s.academicYear === y.year ? "selected" : ""}>${y.label}</option>`).join("")}</select>
      </div>
    </div>
    <div class="field-row">
      <div class="field"><label>Shown paper count</label>
        <input id="sCount" type="number" min="0" value="${s.paperCount}" />
        <p class="hint">Display number in the app (can exceed files uploaded).</p>
      </div>
      <div class="field"><label>Icon</label>
        <select id="sIcon">${icons.map((i) =>
          `<option ${i === s.iconName ? "selected" : ""}>${i}</option>`).join("")}</select>
      </div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Add subject", onClick: async () => {
      const name = $("#sName").value.trim();
      if (!name) { toast("Subject name is required", "err"); return; }
      const branchCode = $("#sBranchM").value;
      const academicYear = Number($("#sYearM").value);
      const dupe = state.subjects.find((x) =>
        x.name.toLowerCase() === name.toLowerCase() &&
        x.branchCode === branchCode &&
        (!existing || x.id !== existing.id));
      if (dupe) { toast("This subject already exists for " + branchCode, "err"); return; }
      const id = existing ? existing.id : slug(branchCode + "_" + name);
      await DB.put("subjects", {
        id, name, branchCode, academicYear,
        paperCount: parseInt($("#sCount").value, 10) || 0,
        iconName: $("#sIcon").value,
      });
      savePrefs({ branch: branchCode });
      toast(existing ? "Subject updated" : "Subject added", "ok");
      await reloadAll();
      renderSubjects();
    } },
  ]);
}

function deleteSubject(s) {
  const n = papersOfSubject(s.branchCode, s.name).length;
  if (n > 0) {
    toast(`Cannot delete — ${n} paper(s) still use this subject.`, "err");
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
  $("#viewTitle").textContent = "Notes";
  $("#viewSubtitle").textContent = "Study notes grouped by subject — like papers";
  $("#topbarActions").innerHTML = `<button class="btn" id="btnAddNote">+ Add note</button>`;
  $("#btnAddNote").addEventListener("click", () => openNoteModal(null));

  const f = state.noteFilter;
  const subjectsInNotes = [...new Set(state.notes.map((n) => n.subjectName || "General"))]
    .sort((a, b) => a.localeCompare(b));

  const rows = state.notes.filter((n) => {
    if (f.branch && n.branchCode !== f.branch) return false;
    const subj = n.subjectName || "General";
    if (f.subject && subj !== f.subject) return false;
    if (f.q && !(n.title + " " + (n.subjectName || "") + " " + (n.content || "")).toLowerCase().includes(f.q.toLowerCase())) return false;
    return true;
  });

  // Group by subject (same mental model as the app's paper library)
  const groups = new Map();
  for (const n of rows) {
    const key = n.subjectName || "General";
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(n);
  }
  const sortedKeys = [...groups.keys()].sort((a, b) => a.localeCompare(b));

  const subjectOpts = [
    `<option value="">All subjects</option>`,
    ...subjectsInNotes.map((s) => `<option value="${esc(s)}" ${f.subject === s ? "selected" : ""}>${esc(s)}</option>`),
  ].join("");

  $("#view-notes").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="nBranch">
          <option value="">All branches</option>
          ${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}
        </select>
        <select id="nSubject">${subjectOpts}</select>
        <input id="nQ" type="search" placeholder="Search notes…" value="${esc(f.q || "")}" />
      </div>
      <div class="table-wrap"><table>
        <thead><tr><th>Note</th><th>Branch</th><th>Year</th><th>PDF</th><th>Updated</th><th></th></tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="6" class="empty"><strong>No notes yet</strong>Add one so students have something to revise.</td></tr>`
            : sortedKeys.map((subj) => `
          <tr class="group-row"><td colspan="6">${esc(subj)} <span class="muted">· ${groups.get(subj).length}</span></td></tr>
          ${groups.get(subj).map((n) => `
          <tr>
            <td>
              <b>${esc(n.title)}</b>
              ${n.content ? `<br><span class="muted">${esc(n.content.slice(0, 80))}${n.content.length > 80 ? "…" : ""}</span>` : ""}
            </td>
            <td><span class="badge">${esc(n.branchCode)}</span></td>
            <td>${yearLabel(n.academicYear)}</td>
            <td>${n.storagePath ? `<span class="badge green">PDF ✓</span>` : `<span class="badge grey">—</span>`}</td>
            <td>${fmtDate(n.updatedAt)}</td>
            <td><div class="row-actions">
              ${n.storagePath ? `<button class="icon-btn" data-act="open" data-id="${n.id}" title="Open PDF">📄</button>` : ""}
              <button class="icon-btn" data-act="edit" data-id="${n.id}" title="Edit">✎</button>
              <button class="icon-btn danger" data-act="del" data-id="${n.id}" title="Delete">🗑</button>
            </div></td>
          </tr>`).join("")}`).join("")}
        </tbody>
      </table></div>
      <p class="muted" style="margin:12px 0 0">${rows.length} note(s) · ${sortedKeys.length} subject group(s)</p>
    </div>`;

  $("#nBranch").addEventListener("change", (e) => { state.noteFilter.branch = e.target.value; renderNotes(); });
  $("#nSubject").addEventListener("change", (e) => { state.noteFilter.subject = e.target.value; renderNotes(); });
  $("#nQ").addEventListener("input", (e) => {
    state.noteFilter.q = e.target.value;
    clearTimeout(window.__nq);
    window.__nq = setTimeout(renderNotes, 200);
  });
  $("#view-notes").querySelectorAll("button[data-act]").forEach((b) => {
    const id = Number(b.dataset.id);
    const note = state.notes.find((n) => n.id === id);
    if (b.dataset.act === "edit") b.addEventListener("click", () => openNoteModal(note));
    if (b.dataset.act === "open") b.addEventListener("click", () => {
      if (note.storagePath) window.open(DB.fileUrl(note.storagePath), "_blank");
      else toast("No PDF on this note", "err");
    });
    if (b.dataset.act === "del") b.addEventListener("click", () => {
      confirmDialog("Delete note?", `"${note.title}" will be removed.`, "Delete", async () => {
        await DB.del("notes", id);
        if (note.storagePath) { try { await DB.delFile(note.storagePath); } catch { /* gone */ } }
        toast("Note deleted", "ok");
        await reloadAll();
        renderNotes();
      });
    });
  });
}

function openNoteModal(existing) {
  const prefs = loadPrefs();
  const n = existing || {
    title: "", content: "", subjectName: "",
    branchCode: prefs.branch || "COMMON",
    academicYear: 1,
    storagePath: "",
  };
  state.pendingFile = null;

  openModal(existing ? "Edit note" : "Add note", `
    <div class="field"><label>Title</label>
      <input id="nTitle" value="${esc(n.title)}" placeholder="e.g. Maths I — Important Formulas" />
    </div>
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="nBranchM">${BRANCHES.map((b) =>
          `<option value="${b.code}" ${b.code === n.branchCode ? "selected" : ""}>${b.code}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Year</label>
        <select id="nYearM">${YEARS.map((y) =>
          `<option value="${y.year}" ${n.academicYear === y.year ? "selected" : ""}>${y.label}</option>`).join("")}</select>
        <p class="hint" id="yearHint">First Year is COMMON for all branches.</p>
      </div>
    </div>
    <div class="field"><label>Subject <span class="muted">(groups the note like papers)</span></label>
      <select id="nSubject"></select>
    </div>
    <div class="field"><label>Short description <span class="muted">(shown under title in app)</span></label>
      <textarea id="nContent" rows="2" placeholder="One line is enough">${esc(n.content || "")}</textarea>
    </div>
    <div class="field"><label>Note PDF</label>
      <div class="dropzone" id="nDrop">
        <strong>Drop PDF or browse</strong>
        <span class="dz-hint">Opens when the student taps the note</span>
        <input type="file" id="nFile" accept="application/pdf" hidden />
      </div>
      <div id="nFileInfo">${n.storagePath
        ? `<div class="file-chip">📄 ${esc(n.fileName || n.storagePath.split("/").pop())} — attached</div>`
        : `<p class="hint">Optional — without a PDF the note is text-only.</p>`}</div>
    </div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: existing ? "Save changes" : "Add note", onClick: () => saveNote(existing) },
  ]);

  const branchSel = $("#nBranchM"), yearSel = $("#nYearM"), yearHint = $("#yearHint");
  const subjectSel = $("#nSubject");
  const refreshNoteSubjects = () => {
    subjectSel.innerHTML = subjectSelectOptions(branchSel.value, n.subjectName || undefined, { allowEmpty: true });
  };
  const syncYear = () => {
    if (branchSel.value === "COMMON") {
      yearSel.value = "1";
      yearSel.disabled = true;
      yearHint.textContent = "First Year is COMMON for all branches.";
      yearHint.style.display = "";
    } else {
      yearSel.disabled = false;
      if (yearSel.value === "1") yearSel.value = "2";
      yearHint.style.display = "none";
    }
  };
  branchSel.addEventListener("change", () => {
    n.subjectName = "";
    syncYear();
    refreshNoteSubjects();
    savePrefs({ branch: branchSel.value });
  });
  syncYear();
  refreshNoteSubjects();

  wireDropzone("nDrop", "nFile", "nFileInfo", (file) => {
    if (!acceptPdf(file)) return;
    state.pendingFile = file;
    $("#nFileInfo").innerHTML = `<div class="file-chip">📄 ${esc(file.name)} · ${fmtBytes(file.size)} — ready</div>`;
  });
}

async function saveNote(existing) {
  const title = $("#nTitle").value.trim();
  const content = $("#nContent").value.trim();
  if (!title) { toast("Title is required", "err"); return; }
  const branchCode = $("#nBranchM").value;
  const academicYear = branchCode === "COMMON" ? 1 : Number($("#nYearM").value);

  const row = {
    title,
    content,
    subjectName: $("#nSubject").value || "",
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
    const filePath = "notes/" + slug(title) + "_" + Date.now() + ".pdf";
    if (existing && existing.storagePath && existing.storagePath !== filePath) {
      try { await DB.delFile(existing.storagePath); } catch { /* gone */ }
    }
    await DB.putFile(filePath, f);
    row.storagePath = filePath;
    row.fileName = f.name;
    row.fileUrl = DB.fileUrl(filePath);
  }

  await DB.put("notes", row);
  savePrefs({ branch: branchCode });
  toast(existing ? "Note updated" : "Note published", "ok");
  await reloadAll();
  renderNotes();
}

/* ============================================================
 * SETTINGS (backup / advanced — not part of daily flow)
 * ============================================================ */
function renderData() {
  $("#viewTitle").textContent = "Settings";
  $("#viewSubtitle").textContent = "Account, backup, and rare tools";
  $("#topbarActions").innerHTML = "";
  const session = JSON.parse(localStorage.getItem("pyq-admin-session") || "{}");
  $("#view-data").innerHTML = `
    <div class="panel">
      <h3>Account</h3>
      <p class="muted">${esc(session.email || "")}</p>
      <button class="btn secondary" id="btnSettingsLogout">Log out</button>
    </div>
    <div class="panel">
      <h3>Export backup</h3>
      <p class="muted">Downloads subjects, papers and notes as JSON. PDFs stay in cloud storage — re-attach after a restore.</p>
      <button class="btn tonal" id="btnExport">Download JSON</button>
    </div>
    <div class="panel">
      <h3>Import backup</h3>
      <p class="muted">Replaces the current catalog with a previously exported file.</p>
      <input type="file" id="importFile" accept="application/json" hidden />
      <button class="btn secondary" id="btnImport">Choose file…</button>
    </div>
    <div class="panel">
      <h3>Database fields</h3>
      <p class="muted">Supabase tables (already live):</p>
      <div class="codebox">subjects(id, name, branchCode, academicYear, paperCount, iconName)
papers(id, title, subjectName, branchCode, year, examType,
       fileFormat, fileSize, duration, maxMarks,
       sampleQuestions[], storagePath)
notes(id, title, content, subjectName, branchCode,
      academicYear, fileUrl, storagePath, updatedAt)</div>
    </div>
    <div class="panel">
      <h3 style="color:var(--error)">Danger zone</h3>
      <p class="muted">Erase all subjects, papers and notes from the cloud. Uploaded PDFs remain until deleted per-item.</p>
      <button class="btn danger" id="btnReset">Erase cloud data</button>
    </div>`;

  $("#btnSettingsLogout").addEventListener("click", () => {
    DB.logout();
    showLogin();
    toast("Logged out");
  });
  $("#btnExport").addEventListener("click", async () => {
    const data = await DB.exportJSON();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    const d = new Date();
    a.download = `paperadda-backup-${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    toast("Backup downloaded", "ok");
  });
  $("#btnImport").addEventListener("click", () => $("#importFile").click());
  $("#importFile").addEventListener("change", async (e) => {
    if (!e.target.files.length) return;
    try {
      const data = JSON.parse(await e.target.files[0].text());
      confirmDialog("Import backup?",
        `This REPLACES ${state.subjects.length} subjects, ${state.papers.length} papers and ${state.notes.length} notes.`,
        "Import", async () => {
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
    confirmDialog("Erase everything?",
      "All subjects, papers and notes will be deleted from Supabase. This cannot be undone.",
      "Erase", async () => {
        await DB.resetToSeed();
        toast("Cloud data erased", "ok");
        await reloadAll();
        switchView("dashboard");
      });
  });
}
