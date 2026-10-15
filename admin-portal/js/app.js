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
  requests: [],
  feedback: [],
  requestLoadError: "",
  feedbackLoadError: "",
  paperFilter: { branch: "", q: "" },
  subjectFilter: { branch: "", year: "" },
  noteFilter: { branch: "", subject: "", q: "" },
  requestFilter: { status: "", kind: "", branch: "", q: "" },
  feedbackFilter: { status: "", category: "", q: "" },
  selectedPapers: new Set(),
  selectedNotes: new Set(),
  selectedSubjects: new Set(),
  pendingFile: null,
  bulkFiles: [],
  bulk: {},
};
const BULK_MAX_FILES = 50;

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

function isSessionError(error) {
  return /session expired|not logged in/i.test(String(error && error.message || error || ""));
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
    if (b.id) btn.id = b.id;
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
    $("#loginError").textContent = isSessionError(err)
      ? "Session expired — please sign in again."
      : (err.message || "Could not load the portal.");
    return;
  }
  const s = JSON.parse(localStorage.getItem("paperadda-admin-session") || "{}");
  $("#adminEmail").textContent = s.displayName ? s.displayName + " · " + (s.email || "") : (s.email || "");
  toast("Connected to Supabase", "ok");
  switchView("dashboard");
}

async function reloadAll() {
  const [subjects, papers, notes] = await Promise.all([
    DB.getAll("subjects"), DB.getAll("papers"), DB.getAll("notes"),
  ]);
  let requests = [];
  let requestLoadError = "";
  try {
    requests = await DB.listAllRequests();
  } catch (err) {
    if (isSessionError(err)) throw err;
    requestLoadError = err.message || "Could not load requests";
  }
  let feedback = [];
  let feedbackLoadError = "";
  try {
    feedback = await DB.listAllFeedback();
  } catch (err) {
    if (isSessionError(err)) throw err;
    feedbackLoadError = err.message || "Could not load feedback";
  }
  state.subjects = subjects.sort((a, b) =>
    a.branchCode.localeCompare(b.branchCode) ||
    a.academicYear - b.academicYear ||
    a.name.localeCompare(b.name));
  state.papers = papers.sort((a, b) => String(b.year).localeCompare(String(a.year)));
  state.notes = notes.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  state.requests = requests.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
  state.requestLoadError = requestLoadError;
  state.feedback = feedback.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
  state.feedbackLoadError = feedbackLoadError;
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
    requests: renderRequests,
    feedback: renderFeedback,
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

  const pendingRequests = state.requests.filter((r) => r.status === "pending").length;
  const newFeedback = state.feedback.filter((f) => f.status === "new").length;
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
        <p>Drop a question-paper PDF → branch, subject, year → save</p>
      </button>
      <button class="action-tile" id="tileBulk">
        <div class="tile-ico">🗂</div>
        <h3>Bulk upload</h3>
        <p>Add a whole batch of question-paper PDFs in one go</p>
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
      <button class="action-tile" id="tileRequests">
        <div class="tile-ico">✉</div>
        <h3>Review requests</h3>
        <p>See what students need and update its status</p>
      </button>
      <button class="action-tile" id="tileFeedback">
        <div class="tile-ico">💬</div>
        <h3>Read feedback</h3>
        <p>See what users report and follow up on issues</p>
      </button>
    </div>

    <div class="cards">
      <div class="card"><div class="stat-num">${state.subjects.length}</div><div class="stat-label">Subjects</div></div>
      <div class="card"><div class="stat-num">${state.papers.length}</div><div class="stat-label">Question papers</div></div>
      <div class="card"><div class="stat-num">${state.notes.length}</div><div class="stat-label">Study notes</div></div>
      <div class="card"><div class="stat-num">${pendingRequests}</div><div class="stat-label">Pending requests</div></div>
      <div class="card"><div class="stat-num">${newFeedback}</div><div class="stat-label">New feedback</div></div>
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
  $("#tileRequests").addEventListener("click", () => switchView("requests"));
  $("#tileFeedback").addEventListener("click", () => switchView("feedback"));
}

/* ============================================================
 * PAPERS — branch filter + search only; title auto-filled
 * ============================================================ */
function renderPapers() {
  $("#viewTitle").textContent = "Papers";
  $("#viewSubtitle").textContent = "Question papers students open from the app";
  $("#topbarActions").innerHTML =
    `<button class="btn" id="btnUpload">+ Upload paper</button>` +
    `<button class="btn secondary" id="btnBulkUpload">Bulk upload</button>`;
  $("#btnUpload").addEventListener("click", () => openPaperModal(null));
  $("#btnBulkUpload").addEventListener("click", () => openBulkPaperModal());

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
    duration: "",
    maxMarks: 0,
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
      <input id="mTitle" value="${esc(p.title)}" placeholder="auto: exam type + year" />
    </div>

    <button type="button" class="adv-toggle" id="advToggle">▸ More options (duration, marks…)</button>
    <div class="adv-fields" id="advFields" hidden>
      <div class="field-row">
        <div class="field"><label>Duration</label>
          <input id="mDuration" value="${esc(p.duration || "")}" placeholder="e.g. 3 Hours" /></div>
        <div class="field"><label>Max marks</label>
          <input id="mMarks" type="number" min="0" value="${Number(p.maxMarks) > 0 ? Number(p.maxMarks) : ""}" placeholder="e.g. 70" /></div>
      </div>
      <p class="hint">Check the actual question paper. Leave blank if you are not sure — the app
        hides these instead of showing a guessed number.</p>
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
    // The app already shows the subject as the screen heading, so the stored
    // title must not repeat it — that produced "PPS (Programming for Problem
    // Solving) - End Semester 2025" under a heading that said the same thing.
    title = `${examType} ${year}`;
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
    duration: advOpen ? $("#mDuration").value.trim() : (existing?.duration || ""),
    maxMarks: advOpen ? (parseInt($("#mMarks").value, 10) || 0) : (existing?.maxMarks || 0),
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

/* ============================================================
 * BULK PAPER UPLOAD — one form for a whole batch of PDFs
 * ============================================================ */

/**
 * Pulls an exam year out of a file name so a mixed batch (2024, 2025, 2026)
 * does not need typing per file. Looks for a 4-digit year in a believable
 * range and prefers the last one, because names usually end with the year
 * ("PPS_ESE_2025", "final-2024-scheme").
 */
function detectYearFromFilename(name) {
  // No \b: a word boundary does not exist after "_", which is most filenames.
  // Take every 4-digit run in a plausible exam range and prefer the last one,
  // because names usually end with the year ("PPS_ESE_2025", "final-2024").
  const runs = String(name || "").match(/\d{4}/g) || [];
  const plausible = runs.filter((r) => {
    const n = Number(r);
    return n >= 2010 && n <= 2035;
  });
  return plausible.length ? plausible[plausible.length - 1] : "";
}

/** "PPS_ESE_2025 (copy).pdf" -> "PPS ESE 2025 (copy)" */
function titleFromFilename(name) {
  return String(name || "")
    .replace(/\.[a-z0-9]+$/i, "")
    .replace(/[_\-.]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function openBulkPaperModal() {
  const prefs = loadPrefs();
  const thisYear = String(new Date().getFullYear());
  state.bulkFiles = [];
  state.bulk = {
    branchCode: prefs.branch || "ENTC",
    detectYear: true,
    titleMode: "auto", // "auto" = exam type + year, "filename" = file name
    year: thisYear,
    done: false,
  };

  openModal("Bulk upload papers", `
    <div class="field-row">
      <div class="field"><label>Branch</label>
        <select id="bBranch">${BRANCHES.map((b) =>
          `<option value="${b.code}" ${b.code === state.bulk.branchCode ? "selected" : ""}>${b.code} — ${esc(b.fullName)}</option>`).join("")}</select>
      </div>
      <div class="field"><label>Subject</label><select id="bSubject"></select></div>
    </div>
    <div class="field-row">
      <div class="field"><label>Exam type</label>
        <select id="bExamType">
          ${["End Semester Examination", "Mid Semester Examination", "Unit Test", "Other"].map((t) =>
            `<option ${t === "End Semester Examination" ? "selected" : ""}>${t}</option>`).join("")}
        </select>
      </div>
      <div class="field"><label>Fallback year</label>
        <input id="bYear" value="${thisYear}" />
        <p class="hint">Used when a file name has no year.</p>
      </div>
    </div>
    <div class="field-row">
      <div class="field"><label>Duration <span class="muted">(optional)</span></label>
        <input id="bDuration" placeholder="e.g. 3 Hours" /></div>
      <div class="field"><label>Max marks <span class="muted">(optional)</span></label>
        <input id="bMarks" type="number" min="0" placeholder="e.g. 70" /></div>
    </div>
    <p class="hint">Leave marks and duration blank unless you are sure — the app hides
      fields that were never set rather than showing a guess.</p>

    <div class="field">
      <label>PDF files</label>
      <div class="dropzone" id="bDrop">
        <strong>Drop PDFs here (many at once) or browse</strong>
        <span class="dz-hint">Up to ${BULK_MAX_FILES} files · every file uses the settings above</span>
        <input type="file" id="bFiles" accept="application/pdf,.pdf" multiple hidden />
      </div>
    </div>

    <div class="field">
      <label>Options</label>
      <label class="check"><input type="checkbox" id="bDetectYear" checked />
        Read the exam year from each file name</label>
      <label class="check"><input type="radio" name="bTitleMode" value="auto" checked />
        Titles: exam type + year</label>
      <label class="check"><input type="radio" name="bTitleMode" value="filename" />
        Titles: use the file name</label>
    </div>

    <div id="bSummary"></div>
  `, [
    { label: "Cancel", kind: "secondary" },
    { label: "Upload all", id: "bGo", keepOpen: true, onClick: () => runBulkUpload() },
  ]);

  const branchSel = $("#bBranch"), subjectSel = $("#bSubject");
  const refreshSubjects = () => {
    subjectSel.innerHTML = subjectSelectOptions(branchSel.value);
  };
  branchSel.addEventListener("change", () => { refreshSubjects(); savePrefs({ branch: branchSel.value }); });
  refreshSubjects();
  savePrefs({ branch: state.bulk.branchCode });

  wireBulkModal();
  renderBulkRows();
}

/** Adds chosen files, ignoring duplicates and non-PDFs. */
function addBulkFiles(fileList) {
  const incoming = Array.from(fileList || []);
  const skipped = { dup: 0, type: 0, full: 0 };
  for (const f of incoming) {
    const isPdf = f.type === "application/pdf" || /\.pdf$/i.test(f.name);
    if (!isPdf) { skipped.type++; continue; }
    const already = state.bulkFiles.some((b) =>
      b.file.name === f.name && b.file.size === f.size);
    if (already) { skipped.dup++; continue; }
    if (state.bulkFiles.length >= BULK_MAX_FILES) { skipped.full++; continue; }
    state.bulkFiles.push({ file: f, year: "", title: "", status: "pending", error: "" });
  }
  applyBulkDefaults();
  renderBulkRows();
  const notes = [];
  if (skipped.type) notes.push(`${skipped.type} non-PDF skipped`);
  if (skipped.dup) notes.push(`${skipped.dup} duplicate skipped`);
  if (skipped.full) notes.push(`limit of ${BULK_MAX_FILES} reached`);
  if (notes.length) toast(notes.join(" · "), "err");
}

/** Fills each row's year/title from the shared settings, without clobbering edits. */
function applyBulkDefaults() {
  const examType = ($("#bExamType") || {}).value || "End Semester Examination";
  const fallbackYear = (($("#bYear") || {}).value || "").trim();
  const detect = ($("#bDetectYear") || {}).checked;
  const mode = (document.querySelector('input[name="bTitleMode"]:checked') || {}).value || "auto";
  for (const row of state.bulkFiles) {
    if (row.yearTouched) continue;
    // Detection on: use the year from the file name, and leave it blank if
    // there is none so the upload reports it, rather than quietly stamping
    // today's year onto an old paper. Detection off: use the fallback year.
    row.year = detect ? detectYearFromFilename(row.file.name) : fallbackYear;
    if (row.titleTouched) continue;
    row.title = mode === "filename" ? titleFromFilename(row.file.name)
      : (row.year ? `${examType} ${row.year}` : examType);
  }
}

function renderBulkRows() {
  const box = $("#bBulkRows");
  if (!box) return;
  const total = state.bulkFiles.reduce((n, b) => n + b.file.size, 0);
  const done = state.bulkFiles.filter((b) => b.status === "done").length;
  const failed = state.bulkFiles.filter((b) => b.status === "failed").length;

  $("#bSummary").innerHTML = state.bulkFiles.length === 0 ? "" : `
    <p class="hint">${state.bulkFiles.length} file(s) · ${esc(fmtBytes(total))}
      ${failed ? ` · <span style="color:var(--error)">${failed} failed</span>` : ""}
      ${done ? ` · <span style="color:var(--success)">${done} uploaded</span>` : ""}</p>`;

  if (state.bulkFiles.length === 0) {
    box.innerHTML = `<p class="hint">No files chosen yet.</p>`;
    return;
  }

  box.innerHTML = `<div class="bulk-list">` + state.bulkFiles.map((row, i) => `
    <div class="bulk-row" data-bulk-idx="${i}">
      <div class="bulk-row-main">
        <div class="bulk-file" title="${esc(row.file.name)}">📄 ${esc(row.file.name)}</div>
        <div class="bulk-sub">${esc(fmtBytes(row.file.size))}</div>
      </div>
      <div class="bulk-row-fields">
        <input data-bulk-year="${i}" value="${esc(row.year)}" placeholder="Year" title="Exam year for this file" />
        <input data-bulk-title="${i}" value="${esc(row.title)}" placeholder="Title" title="Title for this file" />
      </div>
      <div class="bulk-row-actions">
        <span class="bulk-status ${row.status}">${bulkStatusLabel(row.status)}</span>
        ${row.status === "pending"
          ? `<button type="button" class="icon-btn danger" data-bulk-remove="${i}" title="Remove">✕</button>`
          : `<button type="button" class="icon-btn" data-bulk-retry="${i}" title="Retry">↻</button>`}
      </div>
      ${row.error ? `<div class="bulk-error">${esc(row.error)}</div>` : ""}
    </div>`).join("") + `</div>`;

  box.querySelectorAll("[data-bulk-year]").forEach((el) => {
    const idx = Number(el.getAttribute("data-bulk-year"));
    el.addEventListener("input", () => {
      const row = state.bulkFiles[idx];
      if (!row) return;
      row.year = el.value.trim();
      row.yearTouched = true;
      renderBulkRows();
      const again = box.querySelector(`[data-bulk-year="${idx}"]`);
      if (again) {
        again.focus();
        again.setSelectionRange(again.value.length, again.value.length);
      }
    });
  });
  box.querySelectorAll("[data-bulk-title]").forEach((el) => {
    const idx = Number(el.getAttribute("data-bulk-title"));
    el.addEventListener("input", () => {
      const row = state.bulkFiles[idx];
      if (!row) return;
      row.title = el.value;
      row.titleTouched = true;
    });
  });
  box.querySelectorAll("[data-bulk-remove]").forEach((b) =>
    b.addEventListener("click", () => {
      state.bulkFiles.splice(Number(b.getAttribute("data-bulk-remove")), 1);
      renderBulkRows();
    }));
  box.querySelectorAll("[data-bulk-retry]").forEach((b) => {
    const idx = Number(b.getAttribute("data-bulk-retry"));
    b.addEventListener("click", () => {
      const row = state.bulkFiles[idx];
      if (!row) return;
      row.status = "pending";
      row.error = "";
      renderBulkRows();
    });
  });
}

function bulkStatusLabel(status) {
  return { pending: "waiting", uploading: "uploading…", done: "done", failed: "failed" }[status] || status;
}

function wireBulkModal() {
  const drop = $("#bDrop");
  const input = $("#bFiles");
  drop.addEventListener("click", () => input.click());
  input.addEventListener("change", () => { addBulkFiles(input.files); input.value = ""; });
  ["dragenter", "dragover"].forEach((ev) =>
    drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add("over"); }));
  ["dragleave", "drop"].forEach((ev) =>
    drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove("over"); }));
  drop.addEventListener("drop", (e) => addBulkFiles(e.dataTransfer.files));

  ["#bExamType", "#bYear", "#bDetectYear"].forEach((sel) => {
    const el = $(sel);
    if (el) el.addEventListener("input", () => { applyBulkDefaults(); renderBulkRows(); });
  });
  document.querySelectorAll('input[name="bTitleMode"]').forEach((r) =>
    r.addEventListener("change", () => {
      for (const row of state.bulkFiles) row.titleTouched = false;
      applyBulkDefaults();
      renderBulkRows();
    }));

  const host = document.createElement("div");
  host.id = "bBulkRows";
  $("#bSummary").insertAdjacentElement("afterend", host);
}

/**
 * Uploads every pending file, one at a time so a slow connection cannot
 * starve the portal. A failure marks that row and the batch carries on, so one
 * bad PDF never costs you the other nine.
 */
async function runBulkUpload() {
  const branchCode = $("#bBranch").value;
  const subjectName = $("#bSubject").value;
  const examType = $("#bExamType").value;
  const duration = $("#bDuration").value.trim();
  const maxMarks = parseInt($("#bMarks").value, 10) || 0;

  if (!subjectName) { toast("Pick a subject for the whole batch", "err"); return; }
  const queued = state.bulkFiles.filter((b) => b.status === "pending");
  if (queued.length === 0) { toast("Add some PDF files first", "err"); return; }

  const goBtn = $("#bGo");
  if (goBtn) { goBtn.disabled = true; goBtn.textContent = "Uploading…"; }

  // Ids must not collide with existing papers or with each other in this batch.
  const used = new Set(state.papers.map((p) => p.id));
  const base = slug(branchCode + "_" + subjectName);
  let uploaded = 0;

  for (const row of queued) {
    const year = (row.year || "").trim();
    if (!year) {
      row.status = "failed";
      row.error = "No exam year — set one for this file";
      continue;
    }
    let id = base + "_" + slug(year);
    let n = 2;
    while (used.has(id)) id = base + "_" + slug(year) + "_" + (n++);
    used.add(id);

    row.status = "uploading";
    row.error = "";
    renderBulkRows();
    try {
      const path = id + ".pdf";
      await DB.putFile(path, row.file);
      await DB.put("papers", {
        id,
        title: (row.title || "").trim() || `${examType} ${year}`,
        subjectName,
        branchCode,
        year,
        examType,
        duration,
        maxMarks,
        fileFormat: "PDF",
        fileSize: fmtBytes(row.file.size),
        sampleQuestions: [],
        fileId: path,
        fileName: row.file.name,
        uploadedAt: Date.now(),
      });
      row.status = "done";
      uploaded++;
    } catch (err) {
      row.status = "failed";
      row.error = err.message || "Upload failed";
    }
    renderBulkRows();
  }

  savePrefs({ branch: branchCode });
  await reloadAll();
  state.bulk.done = true;
  if (goBtn) { goBtn.disabled = false; goBtn.textContent = "Upload all"; }
  renderBulkRows();
  renderPapers();

  // Count from the final state: a row that had no usable year fails before the
  // try block, so it never reaches the `failed` list.
  const failedCount = state.bulkFiles.filter((b) => b.status === "failed").length;
  if (failedCount === 0) {
    toast(`${uploaded} paper(s) published`, "ok");
    closeModal();
  } else {
    toast(`${uploaded} published, ${failedCount} failed — fix and retry`, "err");
  }
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

  for (const id of [...state.selectedSubjects]) {
    if (!state.subjects.some((s) => s.id === id)) state.selectedSubjects.delete(id);
  }
  const selCount = rows.filter((s) => state.selectedSubjects.has(s.id)).length;
  const allChecked = rows.length > 0 && selCount === rows.length;

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
        ${selCount > 0 ? `
        <button class="btn danger small" id="btnBulkDelSub">🗑 Delete selected (${selCount})</button>
        <button class="btn ghost small" id="btnBulkClearSub">Clear</button>` : ""}
      </div>
      <div class="table-wrap"><table>
        <thead><tr>
          <th class="col-check"><input type="checkbox" id="checkAllSub" ${allChecked ? "checked" : ""} aria-label="Select all subjects" /></th>
          <th>Subject</th><th>Branch</th><th>Year</th><th>Papers</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="6" class="empty"><strong>No subjects here</strong>Add one to start publishing papers.</td></tr>`
            : rows.map((s) => {
              const n = papersOfSubject(s.branchCode, s.name).length;
              return `<tr class="${state.selectedSubjects.has(s.id) ? "row-selected" : ""}">
                <td class="col-check"><input type="checkbox" data-check="${esc(s.id)}" ${state.selectedSubjects.has(s.id) ? "checked" : ""} aria-label="Select ${esc(s.name)}" /></td>
                <td><span class="subj-cell">${iconPreview(ICON_IDS.has(s.iconName) ? s.iconName : autoIconFor(s.name), 18)}<b>${esc(s.name)}</b></span></td>
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
      <p class="muted" style="margin:12px 0 0">${rows.length} subject(s)${selCount ? ` · ${selCount} selected` : ""}</p>
    </div>`;

  $("#sBranch").addEventListener("change", (e) => { state.subjectFilter.branch = e.target.value; renderSubjects(); });
  $("#sYear").addEventListener("change", (e) => { state.subjectFilter.year = e.target.value; renderSubjects(); });
  $("#checkAllSub").addEventListener("change", (e) => {
    if (e.target.checked) rows.forEach((s) => state.selectedSubjects.add(s.id));
    else rows.forEach((s) => state.selectedSubjects.delete(s.id));
    renderSubjects();
  });
  const bulkDelSub = $("#btnBulkDelSub");
  if (bulkDelSub) bulkDelSub.addEventListener("click", deleteSelectedSubjects);
  const bulkClearSub = $("#btnBulkClearSub");
  if (bulkClearSub) bulkClearSub.addEventListener("click", () => { state.selectedSubjects.clear(); renderSubjects(); });
  $("#view-subjects").querySelectorAll("input[data-check]").forEach((cb) => {
    cb.addEventListener("change", () => {
      const id = cb.dataset.check;
      if (cb.checked) state.selectedSubjects.add(id);
      else state.selectedSubjects.delete(id);
      renderSubjects();
    });
  });
  $("#view-subjects").querySelectorAll("button[data-act]").forEach((b) => {
    const sub = state.subjects.find((s) => s.id === b.dataset.id);
    if (b.dataset.act === "edit") b.addEventListener("click", () => openSubjectModal(sub));
    if (b.dataset.act === "del") b.addEventListener("click", () => deleteSubject(sub));
  });
}

function openSubjectModal(existing) {
  const prefs = loadPrefs();
  const raw = existing || {
    branchCode: prefs.branch || "ENTC",
    academicYear: 2,
    name: "",
    paperCount: 2,
    iconName: "",
  };
  // An icon id the catalogue does not know is treated as "not chosen yet",
  // so the app falls back to matching the subject name.
  const s = { ...raw, iconName: ICON_IDS.has(raw.iconName) ? raw.iconName : "" };

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
    <div class="field"><label>Shown paper count</label>
      <input id="sCount" type="number" min="0" value="${s.paperCount}" />
      <p class="hint">Display number in the app (can exceed files uploaded).</p>
    </div>
    ${iconPickerMarkup(s.iconName)}
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
      // No explicit pick means the app guesses from the name at render time.
      const picked = $("#sIconPicker")?.dataset.selected || "";
      const iconName = ICON_IDS.has(picked) ? picked : autoIconFor(name);
      await DB.put("subjects", {
        id, name, branchCode, academicYear,
        paperCount: parseInt($("#sCount").value, 10) || 0,
        iconName,
      });
      savePrefs({ branch: branchCode });
      toast(existing ? "Subject updated" : "Subject added", "ok");
      await reloadAll();
      renderSubjects();
    } },
  ]);

  wireIconPicker();
}

function deleteSubject(s) {
  const n = papersOfSubject(s.branchCode, s.name).length;
  if (n > 0) {
    toast(`Cannot delete — ${n} paper(s) still use this subject.`, "err");
    return;
  }
  confirmDialog("Delete subject?", `"${s.name}" (${s.branchCode}) will be removed.`, "Delete", async () => {
    await DB.del("subjects", s.id);
    state.selectedSubjects.delete(s.id);
    toast("Subject deleted", "ok");
    await reloadAll();
    renderSubjects();
  });
}

async function deleteSelectedSubjects() {
  const ids = [...state.selectedSubjects];
  if (ids.length === 0) return;
  const blocked = ids.filter((id) => {
    const s = state.subjects.find((x) => x.id === id);
    return s && papersOfSubject(s.branchCode, s.name).length > 0;
  });
  const deletable = ids.filter((id) => !blocked.includes(id));
  if (deletable.length === 0) {
    toast("Selected subjects still have papers — delete those first.", "err");
    return;
  }
  confirmDialog(
    `Delete ${deletable.length} subject(s)?` + (blocked.length ? ` (${blocked.length} skipped — still have papers)` : ""),
    `Selected subjects will be removed from the app.`,
    `Delete ${deletable.length}`,
    async () => {
      let failed = 0;
      for (const id of deletable) {
        try { await DB.del("subjects", id); state.selectedSubjects.delete(id); }
        catch { failed++; }
      }
      toast(failed ? `Deleted with ${failed} error(s)` : `${deletable.length} subject(s) deleted`, failed ? "err" : "ok");
      await reloadAll();
      renderSubjects();
    }
  );
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

  for (const id of [...state.selectedNotes]) {
    if (!state.notes.some((n) => n.id === id)) state.selectedNotes.delete(id);
  }
  const selCount = rows.filter((n) => state.selectedNotes.has(n.id)).length;
  const allChecked = rows.length > 0 && selCount === rows.length;

  $("#view-notes").innerHTML = `
    <div class="panel">
      <div class="filters">
        <select id="nBranch">
          <option value="">All branches</option>
          ${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}
        </select>
        <select id="nSubject">${subjectOpts}</select>
        <input id="nQ" type="search" placeholder="Search notes…" value="${esc(f.q || "")}" />
        ${selCount > 0 ? `
        <button class="btn danger small" id="btnBulkDelNote">🗑 Delete selected (${selCount})</button>
        <button class="btn ghost small" id="btnBulkClearNote">Clear</button>` : ""}
      </div>
      <div class="table-wrap"><table>
        <thead><tr>
          <th class="col-check"><input type="checkbox" id="checkAllNote" ${allChecked ? "checked" : ""} aria-label="Select all notes" /></th>
          <th>Note</th><th>Branch</th><th>Year</th><th>PDF</th><th>Updated</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="7" class="empty"><strong>No notes yet</strong>Add one so students have something to revise.</td></tr>`
            : sortedKeys.map((subj) => `
          <tr class="group-row"><td colspan="7">${esc(subj)} <span class="muted">· ${groups.get(subj).length}</span></td></tr>
          ${groups.get(subj).map((n) => `
          <tr class="${state.selectedNotes.has(n.id) ? "row-selected" : ""}">
            <td class="col-check"><input type="checkbox" data-check="${esc(n.id)}" ${state.selectedNotes.has(n.id) ? "checked" : ""} aria-label="Select ${esc(n.title)}" /></td>
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
      <p class="muted" style="margin:12px 0 0">${rows.length} note(s) · ${sortedKeys.length} subject group(s)${selCount ? ` · ${selCount} selected` : ""}</p>
    </div>`;

  $("#nBranch").addEventListener("change", (e) => { state.noteFilter.branch = e.target.value; renderNotes(); });
  $("#nSubject").addEventListener("change", (e) => { state.noteFilter.subject = e.target.value; renderNotes(); });
  $("#nQ").addEventListener("input", (e) => {
    state.noteFilter.q = e.target.value;
    clearTimeout(window.__nq);
    window.__nq = setTimeout(renderNotes, 200);
  });
  $("#checkAllNote").addEventListener("change", (e) => {
    if (e.target.checked) rows.forEach((n) => state.selectedNotes.add(n.id));
    else rows.forEach((n) => state.selectedNotes.delete(n.id));
    renderNotes();
  });
  const bulkDelNote = $("#btnBulkDelNote");
  if (bulkDelNote) bulkDelNote.addEventListener("click", deleteSelectedNotes);
  const bulkClearNote = $("#btnBulkClearNote");
  if (bulkClearNote) bulkClearNote.addEventListener("click", () => { state.selectedNotes.clear(); renderNotes(); });
  $("#view-notes").querySelectorAll("input[data-check]").forEach((cb) => {
    cb.addEventListener("change", () => {
      const id = Number(cb.dataset.check);
      if (cb.checked) state.selectedNotes.add(id);
      else state.selectedNotes.delete(id);
      renderNotes();
    });
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
        state.selectedNotes.delete(id);
        toast("Note deleted", "ok");
        await reloadAll();
        renderNotes();
      });
    });
  });
}

async function deleteSelectedNotes() {
  const ids = [...state.selectedNotes];
  if (ids.length === 0) return;
  confirmDialog(
    `Delete ${ids.length} note(s)?`,
    `Selected notes and their PDFs will be removed. This cannot be undone.`,
    `Delete ${ids.length}`,
    async () => {
      let failed = 0;
      for (const id of ids) {
        const n = state.notes.find((x) => x.id === id);
        try {
          await DB.del("notes", id);
          if (n && n.storagePath) { try { await DB.delFile(n.storagePath); } catch { /* gone */ } }
        } catch { failed++; }
      }
      state.selectedNotes.clear();
      toast(failed ? `Deleted with ${failed} error(s)` : `${ids.length} note(s) deleted`, failed ? "err" : "ok");
      await reloadAll();
      renderNotes();
    }
  );
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

function requestBadgeClass(status) {
  if (status === "fulfilled") return "green";
  if (status === "rejected") return "red";
  return "";
}

function renderRequests() {
  $("#viewTitle").textContent = "Requests";
  $("#viewSubtitle").textContent = "Review the latest 200 student content requests";
  $("#topbarActions").innerHTML = `<button class="btn tonal" id="btnRefreshRequests">↻ Refresh</button>`;

  const f = state.requestFilter;
  const rows = state.requests.filter((r) => {
    if (f.status && r.status !== f.status) return false;
    if (f.kind && r.kind !== f.kind) return false;
    if (f.branch && r.branchCode !== f.branch) return false;
    if (f.q && !(r.title + " " + (r.details || "") + " " + r.branchCode).toLowerCase().includes(f.q.toLowerCase())) return false;
    return true;
  });
  const pending = state.requests.filter((r) => r.status === "pending").length;
  const fulfilled = state.requests.filter((r) => r.status === "fulfilled").length;
  const rejected = state.requests.filter((r) => r.status === "rejected").length;

  $("#view-requests").innerHTML = `
    <div class="cards">
      <div class="card"><div class="stat-num">${pending}</div><div class="stat-label">Pending</div></div>
      <div class="card"><div class="stat-num">${fulfilled}</div><div class="stat-label">Fulfilled</div></div>
      <div class="card"><div class="stat-num">${rejected}</div><div class="stat-label">Rejected</div></div>
    </div>
    <div class="panel">
      <div class="filters">
        <select id="rStatusFilter">
          <option value="">All statuses</option>
          ${["pending", "fulfilled", "rejected"].map((s) => `<option value="${s}" ${f.status === s ? "selected" : ""}>${s[0].toUpperCase() + s.slice(1)}</option>`).join("")}
        </select>
        <select id="rKindFilter">
          <option value="">Papers and notes</option>
          <option value="paper" ${f.kind === "paper" ? "selected" : ""}>Question papers</option>
          <option value="note" ${f.kind === "note" ? "selected" : ""}>Study notes</option>
        </select>
        <select id="rBranchFilter">
          <option value="">All branches</option>
          ${BRANCHES.map((b) => `<option value="${b.code}" ${f.branch === b.code ? "selected" : ""}>${b.code}</option>`).join("")}
        </select>
        <input id="rSearch" type="search" placeholder="Search requests…" value="${esc(f.q)}" />
      </div>
      ${state.requestLoadError ? `<p class="hint" style="color:var(--error)">Requests unavailable: ${esc(state.requestLoadError)}. Use Refresh to retry.</p>` : ""}
      <div class="table-wrap"><table>
        <thead><tr>
          <th>Request</th><th>Type</th><th>Branch &amp; year</th><th>Status</th><th>Submitted</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="6" class="empty"><strong>${state.requests.length ? "No requests match" : "No requests yet"}</strong>${state.requests.length ? "Adjust the filters to see more requests." : "Student requests will appear here automatically."}</td></tr>`
            : rows.map((r) => `
          <tr>
            <td>
              <b>${esc(r.title)}</b>
              <br><span class="muted">${esc(r.details || "No additional details")}</span>
              ${r.adminNote ? `<br><span class="muted"><b>Update:</b> ${esc(r.adminNote)}</span>` : ""}
            </td>
            <td>${r.kind === "paper" ? "Question paper" : "Study note"}</td>
            <td><span class="badge">${esc(r.branchCode)}</span><br>${yearLabel(r.academicYear)}</td>
            <td><span class="badge ${requestBadgeClass(r.status)}">${esc(r.status)}</span></td>
            <td>${fmtDate(r.createdAt)}</td>
            <td><div class="row-actions"><button class="btn secondary small" data-request-id="${esc(r.id)}">Review</button><button class="btn danger small" data-request-delete="${esc(r.id)}">Delete</button></div></td>
          </tr>`).join("")}
        </tbody>
      </table></div>
      <p class="muted" style="margin:12px 0 0">${rows.length} request${rows.length === 1 ? "" : "s"} shown</p>
    </div>`;

  $("#btnRefreshRequests").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
      state.requests = await DB.listAllRequests();
      state.requests.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
      state.requestLoadError = "";
      renderRequests();
      toast("Requests refreshed", "ok");
    } catch (err) {
      state.requestLoadError = err.message || "Could not refresh requests";
      button.disabled = false;
      toast(state.requestLoadError, "err");
    }
  });
  $("#rStatusFilter").addEventListener("change", (e) => { f.status = e.target.value; renderRequests(); });
  $("#rKindFilter").addEventListener("change", (e) => { f.kind = e.target.value; renderRequests(); });
  $("#rBranchFilter").addEventListener("change", (e) => { f.branch = e.target.value; renderRequests(); });
  $("#rSearch").addEventListener("input", (e) => {
    f.q = e.target.value;
    clearTimeout(window.__requestSearch);
    window.__requestSearch = setTimeout(renderRequests, 200);
  });
  $("#view-requests").querySelectorAll("[data-request-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const request = state.requests.find((r) => r.id === button.dataset.requestId);
      if (request) openRequestModal(request);
    });
  });
  $("#view-requests").querySelectorAll("[data-request-delete]").forEach((button) => {
    button.addEventListener("click", () => deleteRequest(button.dataset.requestDelete));
  });
}

async function deleteRequest(id) {
  const request = state.requests.find((r) => r.id === id);
  if (!request) return;
  confirmDialog(
    "Delete request?",
    `"${request.title}" will be permanently removed from the request list.`,
    "Delete",
    async () => {
      await DB.deleteRequest(id);
      state.requests = state.requests.filter((r) => r.id !== id);
      state.requestLoadError = "";
      toast("Request deleted", "ok");
      renderRequests();
    }
  );
}

function openRequestModal(existing) {
  const statuses = [
    ["pending", "Pending"],
    ["fulfilled", "Fulfilled"],
    ["rejected", "Rejected"],
  ];
  const details = existing.details || "No additional details were provided.";

  openModal("Review request", `
    <div class="codebox">${existing.kind === "paper" ? "Question paper" : "Study note"} · ${esc(existing.branchCode)} · ${yearLabel(existing.academicYear)}
Submitted ${fmtDate(existing.createdAt)}</div>
    <div class="field"><label>Requested content</label>
      <div style="white-space:pre-wrap">${esc(existing.title)}</div>
    </div>
    <div class="field"><label>Student details</label>
      <div class="muted" style="white-space:pre-wrap">${esc(details)}</div>
    </div>
    <div class="field"><label>Status</label>
      <select id="requestStatus">${statuses.map(([value, label]) =>
        `<option value="${value}" ${existing.status === value ? "selected" : ""}>${label}</option>`).join("")}</select>
    </div>
    <div class="field"><label>Update for the student</label>
      <textarea id="requestAdminNote" rows="3" maxlength="300" placeholder="Optional for fulfilled requests; required when rejecting.">${esc(existing.adminNote || "")}</textarea>
      <p class="hint">This message appears in the student's request history.</p>
    </div>`, [
    { label: "Cancel", kind: "secondary" },
    { label: "Save update", onClick: () => saveRequest(existing) },
  ]);
}

async function saveRequest(existing) {
  const status = $("#requestStatus").value;
  const adminNote = $("#requestAdminNote").value.trim();
  if (status === "rejected" && !adminNote) throw new Error("Add a short reason when rejecting a request.");
  await DB.updateRequest(existing.id, status, adminNote);
  state.requests = await DB.listAllRequests();
  state.requests.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
  toast("Request updated", "ok");
  renderRequests();
}

function feedbackBadgeClass(status) {
  if (status === "resolved") return "green";
  if (status === "reviewed" || status === "dismissed") return "grey";
  return "";
}

function renderFeedback() {
  $("#viewTitle").textContent = "Feedback";
  $("#viewSubtitle").textContent = "Anonymous reports from app users";
  $("#topbarActions").innerHTML = `<button class="btn tonal" id="btnRefreshFeedback">↻ Refresh</button>`;

  const f = state.feedbackFilter;
  const rows = state.feedback.filter((item) => {
    if (f.status && item.status !== f.status) return false;
    if (f.category && item.category !== f.category) return false;
    if (f.q && !(item.message + " " + (item.email || "") + " " + item.appVersion).toLowerCase().includes(f.q.toLowerCase())) return false;
    return true;
  });
  const newCount = state.feedback.filter((item) => item.status === "new").length;
  const reviewedCount = state.feedback.filter((item) => item.status === "reviewed").length;
  const resolvedCount = state.feedback.filter((item) => item.status === "resolved").length;

  $("#view-feedback").innerHTML = `
    <div class="cards">
      <div class="card"><div class="stat-num">${newCount}</div><div class="stat-label">New</div></div>
      <div class="card"><div class="stat-num">${reviewedCount}</div><div class="stat-label">Reviewed</div></div>
      <div class="card"><div class="stat-num">${resolvedCount}</div><div class="stat-label">Resolved</div></div>
    </div>
    <div class="panel">
      <div class="filters">
        <select id="fStatusFilter">
          <option value="">All statuses</option>
          ${["new", "reviewed", "resolved", "dismissed"].map((status) => `<option value="${status}" ${f.status === status ? "selected" : ""}>${status[0].toUpperCase() + status.slice(1)}</option>`).join("")}
        </select>
        <select id="fCategoryFilter">
          <option value="">All categories</option>
          ${["bug", "content", "feature", "other"].map((category) => `<option value="${category}" ${f.category === category ? "selected" : ""}>${category[0].toUpperCase() + category.slice(1)}</option>`).join("")}
        </select>
        <input id="fFeedbackSearch" type="search" placeholder="Search feedback…" value="${esc(f.q)}" />
      </div>
      ${state.feedbackLoadError ? `<p class="hint" style="color:var(--error)">Feedback unavailable: ${esc(state.feedbackLoadError)}. Use Refresh to retry.</p>` : ""}
      <div class="table-wrap"><table>
        <thead><tr>
          <th>Feedback</th><th>Category</th><th>App</th><th>Status</th><th>Received</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.length === 0
            ? `<tr><td colspan="6" class="empty"><strong>${state.feedback.length ? "No feedback matches" : "No feedback yet"}</strong>${state.feedback.length ? "Adjust the filters to see more feedback." : "User reports will appear here."}</td></tr>`
            : rows.map((item) => `
          <tr>
            <td>
              <b>${esc(item.message.length > 140 ? item.message.slice(0, 140) + "…" : item.message)}</b>
              ${item.email ? `<br><span class="muted">${esc(item.email)}</span>` : ""}
              ${item.adminNote ? `<br><span class="muted"><b>Note:</b> ${esc(item.adminNote)}</span>` : ""}
            </td>
            <td>${esc(item.category[0].toUpperCase() + item.category.slice(1))}</td>
            <td>${esc(item.platform)} ${esc(item.appVersion || "")}</td>
            <td><span class="badge ${feedbackBadgeClass(item.status)}">${esc(item.status)}</span></td>
            <td>${fmtDate(item.createdAt)}</td>
            <td><div class="row-actions">
              <button class="btn secondary small" data-feedback-id="${esc(item.id)}">Review</button>
              <button class="btn danger small" data-feedback-delete="${esc(item.id)}">Delete</button>
            </div></td>
          </tr>`).join("")}
        </tbody>
      </table></div>
      <p class="muted" style="margin:12px 0 0">${rows.length} feedback message${rows.length === 1 ? "" : "s"} shown</p>
    </div>`;

  $("#btnRefreshFeedback").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
      state.feedback = await DB.listAllFeedback();
      state.feedback.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
      state.feedbackLoadError = "";
      renderFeedback();
      toast("Feedback refreshed", "ok");
    } catch (err) {
      state.feedbackLoadError = err.message || "Could not refresh feedback";
      button.disabled = false;
      toast(state.feedbackLoadError, "err");
    }
  });
  $("#fStatusFilter").addEventListener("change", (e) => { f.status = e.target.value; renderFeedback(); });
  $("#fCategoryFilter").addEventListener("change", (e) => { f.category = e.target.value; renderFeedback(); });
  $("#fFeedbackSearch").addEventListener("input", (e) => {
    f.q = e.target.value;
    clearTimeout(window.__feedbackSearch);
    window.__feedbackSearch = setTimeout(renderFeedback, 200);
  });
  $("#view-feedback").querySelectorAll("[data-feedback-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const item = state.feedback.find((entry) => entry.id === button.dataset.feedbackId);
      if (item) openFeedbackModal(item);
    });
  });
  $("#view-feedback").querySelectorAll("[data-feedback-delete]").forEach((button) => {
    button.addEventListener("click", () => deleteFeedbackItem(button.dataset.feedbackDelete));
  });
}

function openFeedbackModal(existing) {
  const statuses = [["new", "New"], ["reviewed", "Reviewed"], ["resolved", "Resolved"], ["dismissed", "Dismissed"]];
  openModal("Review feedback", `
    <div class="codebox">${esc(existing.category)} · ${esc(existing.platform)} ${esc(existing.appVersion || "")} · ${fmtDate(existing.createdAt)}</div>
    <div class="field"><label>Message</label>
      <div style="white-space:pre-wrap">${esc(existing.message)}</div>
    </div>
    <div class="field"><label>Contact</label>
      <div class="muted">${esc(existing.email || "Anonymous")}</div>
    </div>
    <div class="field"><label>Status</label>
      <select id="feedbackStatus">${statuses.map(([value, label]) =>
        `<option value="${value}" ${existing.status === value ? "selected" : ""}>${label}</option>`).join("")}</select>
    </div>
    <div class="field"><label>Internal note</label>
      <textarea id="feedbackAdminNote" rows="3" maxlength="500" placeholder="Optional note for your team">${esc(existing.adminNote || "")}</textarea>
    </div>`, [
    { label: "Cancel", kind: "secondary" },
    { label: "Save update", onClick: () => saveFeedback(existing) },
  ]);
}

async function saveFeedback(existing) {
  const status = $("#feedbackStatus").value;
  const adminNote = $("#feedbackAdminNote").value.trim();
  await DB.updateFeedback(existing.id, status, adminNote);
  state.feedback = await DB.listAllFeedback();
  state.feedback.sort((a, b) => Date.parse(b.createdAt || 0) - Date.parse(a.createdAt || 0));
  toast("Feedback updated", "ok");
  renderFeedback();
}

function deleteFeedbackItem(id) {
  confirmDialog(
    "Delete feedback?",
    "This feedback message will be permanently removed.",
    "Delete",
    async () => {
      await DB.deleteFeedback(id);
      state.feedback = state.feedback.filter((item) => item.id !== id);
      toast("Feedback deleted", "ok");
      renderFeedback();
    }
  );
}

/* ============================================================
 * SETTINGS (backup / advanced — not part of daily flow)
 * ============================================================ */
function renderData() {
  $("#viewTitle").textContent = "Settings";
  $("#viewSubtitle").textContent = "Account, backup, and rare tools";
  $("#topbarActions").innerHTML = "";
  const session = JSON.parse(localStorage.getItem("paperadda-admin-session") || "{}");
  $("#view-data").innerHTML = `
    <div class="panel">
      <h3>Account</h3>
      <p class="muted">${esc(session.email || "")}</p>
      <div class="field"><label>Display name</label>
        <input id="acctName" value="${esc(session.displayName || "")}" placeholder="Your name" autocomplete="off" /></div>
      <div class="field-row">
        <div class="field"><label>New password</label>
          <input id="acctPass" type="password" placeholder="••••••••" autocomplete="new-password" /></div>
        <div class="field"><label>Confirm password</label>
          <input id="acctPass2" type="password" placeholder="••••••••" autocomplete="new-password" /></div>
      </div>
      <p class="hint">Leave passwords blank to keep the current one. Min 8 characters.</p>
      <button class="btn tonal" id="btnAcctSave">Save account</button>
      <button class="btn secondary" id="btnSettingsLogout">Log out</button>
    </div>
    <div class="panel">
      <h3>Team</h3>
      <p class="muted">People who can log in and upload papers. New members sign in with their email + the password you set.</p>
      <div id="teamList"><p class="muted">Loading…</p></div>
      <div class="field-row">
        <div class="field"><label>Email</label>
          <input id="teamEmail" type="email" placeholder="teammate@example.com" autocomplete="off" /></div>
        <div class="field"><label>Temp password</label>
          <input id="teamPass" type="password" placeholder="min 8 characters" autocomplete="new-password" /></div>
      </div>
      <button class="btn tonal" id="btnTeamInvite">Add member</button>
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
       academicYear, fileUrl, storagePath, updatedAt)
contentRequests(id, kind, branchCode, academicYear, title,
                details, status, adminNote, createdAt, updatedAt)
feedback(id, category, message, email, appVersion,
         platform, status, adminNote, createdAt, updatedAt)</div>
    </div>
    <div class="panel">
      <h3>Legal</h3>
      <p class="muted">Public pages for students and visitors.</p>
      <p>
        <a href="privacy" target="_blank" rel="noopener">Privacy Policy</a> &middot;
        <a href="terms" target="_blank" rel="noopener">Terms of Use &amp; Copyright Notice</a> &middot;
        <a href="delete-data" target="_blank" rel="noopener">Delete Your Data</a>
      </p>
    </div>
    <div class="panel">
      <h3 style="color:var(--error)">Danger zone</h3>
      <p class="muted">Erase all subjects, papers and notes from the cloud. Uploaded PDFs remain until deleted per-item. Requires typing ERASE to confirm.</p>
      <button class="btn danger" id="btnReset">Erase cloud data</button>
    </div>`;

  $("#btnSettingsLogout").addEventListener("click", () => {
    DB.logout();
    showLogin();
    toast("Logged out");
  });
  $("#btnAcctSave").addEventListener("click", async () => {
    try {
      const pass = $("#acctPass").value;
      const pass2 = $("#acctPass2").value;
      if (pass !== pass2) { toast("Passwords do not match", "err"); return; }
      const patch = { displayName: $("#acctName").value.trim() };
      if (pass) patch.password = pass;
      await DB.updateOwnAccount(patch);
      toast("Account updated", "ok");
      await bootApp();
      switchView("data");
    } catch (err) {
      toast(err.message || "Update failed", "err");
    }
  });
  const loadTeam = async () => {
    const box = $("#teamList");
    try {
      const users = await DB.listTeam();
      const me = (JSON.parse(localStorage.getItem("paperadda-admin-session") || "{}").email || "").toLowerCase();
      if (!users.length) { box.innerHTML = `<p class="muted">No members yet.</p>`; return; }
      box.innerHTML = `<ul class="list-plain">${users.map((u) => `
        <li>${esc(u.email || "")}
          ${String(u.email || "").toLowerCase() === me ? ` <span class="badge green">you</span>` : ``}
          ${u.is_admin ? `` : ` <span class="badge grey">no access</span>`}
          <div class="row-actions">
            <button class="btn secondary" data-team-reset="${esc(u.id)}">Reset password</button>
            ${String(u.email || "").toLowerCase() === me ? `` : `<button class="btn secondary" data-team-remove="${esc(u.id)}" data-team-email="${esc(u.email || "")}">Remove</button>`}
          </div>
        </li>`).join("")}</ul>`;
      box.querySelectorAll("[data-team-reset]").forEach((b) => b.addEventListener("click", () => {
        const id = b.getAttribute("data-team-reset");
        openModal("Reset password", `
          <div class="field"><label>New temp password (min 8 characters)</label>
            <input id="resetPass" type="password" autocomplete="new-password" /></div>
          <p class="hint">Share it with the member privately — they can change it under Account.</p>`, [
          { label: "Cancel", kind: "secondary" },
          {
            label: "Set password", kind: "danger", onClick: async () => {
              const p = $("#resetPass").value;
              if (p.length < 8) throw new Error("Password must be at least 8 characters.");
              await DB.resetTeamPassword(id, p);
              toast("Password updated", "ok");
              await loadTeam();
            }
          },
        ]);
      }));
      box.querySelectorAll("[data-team-remove]").forEach((b) => b.addEventListener("click", () => {
        const id = b.getAttribute("data-team-remove");
        const email = b.getAttribute("data-team-email");
        confirmDialog("Remove member?", `"${email}" will lose access immediately. Their uploads stay.`, "Remove", async () => {
          await DB.removeTeamMember(id);
          toast("Member removed", "ok");
          await loadTeam();
        });
      }));
    } catch (err) {
      box.innerHTML = `<p class="muted">Could not load team: ${esc(err.message || "error")}</p>`;
    }
  };
  $("#btnTeamInvite").addEventListener("click", async () => {
    try {
      const email = $("#teamEmail").value.trim();
      const password = $("#teamPass").value;
      if (!email) { toast("Email is required", "err"); return; }
      await DB.inviteTeamMember(email, password);
      toast("Member added", "ok");
      $("#teamEmail").value = "";
      $("#teamPass").value = "";
      await loadTeam();
    } catch (err) {
      toast(err.message || "Invite failed", "err");
    }
  });
  loadTeam();
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
    // Type-to-confirm: a single misclick must never wipe the catalog that
    // every student's app mirrors. Throwing inside onClick keeps the modal
    // open (openModal only closes on success) and surfaces the message.
    openModal("Erase everything?",
      `<p class="muted">All subjects, papers and notes will be deleted from Supabase. This cannot be undone.</p>
       <div class="field"><label>Type <b>ERASE</b> to confirm</label>
         <input id="eraseConfirm" placeholder="ERASE" autocomplete="off" /></div>`,
      [
        { label: "Cancel", kind: "secondary" },
        {
          label: "Erase everything", kind: "danger", onClick: async () => {
            const typed = (($("#eraseConfirm") || {}).value || "").trim();
            if (typed !== "ERASE") throw new Error("Type ERASE to confirm");
            await DB.eraseAllData();
            toast("Cloud data erased", "ok");
            await reloadAll();
            switchView("dashboard");
          }
        },
      ]);
  });
}
