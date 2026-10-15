/* Subject icon catalogue — must stay in sync with the Android app's
 * app/src/main/java/com/example/ui/components/SubjectIcons.kt
 *
 * Ids are the snake_case Material icon names. They are also Material Symbols
 * ligatures, so the browser can render a true preview with the icon font:
 *   <span class="ms">calculate</span>  ->  the calculator glyph.
 */
"use strict";

/* [id, human label, group] — the picker shows these grouped, in this order. */
const ICON_GROUPS = [
  ["Maths & data", [
    ["calculate", "Calculator"], ["functions", "Functions"], ["timeline", "Timeline"],
    ["bar_chart", "Bar chart"], ["percent", "Percentage"], ["insights", "Insights"],
    ["trending_up", "Trending up"], ["query_builder", "Query builder"],
    ["schema", "Schema"], ["table_chart", "Table"], ["data_object", "Data object"],
  ]],
  ["Physics & electrical", [
    ["science", "Science"], ["bolt", "Bolt"], ["electric_bolt", "Electric bolt"],
    ["energy_savings_leaf", "Energy saving"], ["speed", "Speed"], ["waves", "Waves"],
    ["thermostat", "Thermostat"],
  ]],
  ["Chemistry & biology", [
    ["biotech", "Biotech"], ["water_drop", "Water drop"],
    ["local_fire_department", "Fire / heat"], ["color_lens", "Colour lens"],
  ]],
  ["Electronics & communication", [
    ["memory", "Memory / chip"], ["cell_tower", "Signal tower"], ["router", "Router"],
    ["wifi", "Wi-Fi"], ["battery_charging_full", "Battery"],
    ["electric_meter", "Meter"], ["power", "Power"], ["lightbulb", "Light bulb"],
    ["solar_power", "Solar"], ["wind_power", "Wind"], ["tv", "Broadcast"],
    ["electrical_services", "Electrical"], ["device_hub", "Device hub"],
    ["hub", "Hub"], ["lan", "LAN"], ["sensors", "Sensors"],
  ]],
  ["Civil & mechanical", [
    ["construction", "Construction"], ["engineering", "Engineering"],
    ["architecture", "Architecture"], ["straighten", "Ruler / drawing"],
    ["landscape", "Survey / geo"], ["factory", "Factory"],
    ["warehouse", "Warehouse"], ["handyman", "Tools / workshop"],
    ["build", "Build"], ["scale", "Scale"],
  ]],
  ["Computing", [
    ["computer", "Computer"], ["developer_mode", "Programming"],
    ["code", "Code"], ["storage", "Database / storage"], ["security", "Security"],
    ["cloud", "Cloud"], ["dns", "Networks / OS"], ["bug_report", "Debugging"],
    ["terminal", "Shell"], ["phone_android", "Mobile"],
  ]],
  ["Business & commerce", [
    ["account_balance", "Bank / economics"], ["wallet", "Finance"],
    ["business", "Business"], ["store", "Commerce"], ["receipt", "Accounting"],
    ["monetization_on", "Economics"], ["gavel", "Law"], ["balance", "Balance"],
    ["paid", "Payments"],
  ]],
  ["Humanities & skills", [
    ["language", "Language"], ["translate", "Translation"],
    ["record_voice_over", "Communication"], ["menu_book", "Book"],
    ["history", "History"], ["psychology", "Psychology"], ["groups", "Teamwork"],
    ["forum", "Discussion"], ["auto_stories", "Literature"],
    ["library_books", "Library"],
  ]],
  ["Design & media", [
    ["design_services", "Design"], ["brush", "Art / drawing"],
    ["palette", "Colour / paint"], ["videocam", "Video"], ["camera", "Camera"],
  ]],
  ["Health, safety & sport", [
    ["health_and_safety", "Safety"], ["local_hospital", "Medical"],
    ["fitness_center", "Fitness"], ["sports_score", "Sports"],
  ]],
  ["General", [
    ["school", "School"], ["home_work", "Homework"], ["star", "Star"],
    ["verified", "Verified"], ["verified_user", "Verified user"],
    ["auto_awesome", "Sparkles"], ["emoji_events", "Achievement"],
    ["rocket", "Rocket"], ["rocket_launch", "Project"],
    ["travel_explore", "Travel"], ["key", "Key / API"],
    ["volunteer_activism", "Community"], ["self_improvement", "Personality"],
    ["restaurant", "Hospitality"], ["agriculture", "Agriculture"],
    ["forest", "Environment"], ["sailing", "Marine"],
    ["satellite_alt", "Satellite"], ["flight", "Aviation"],
    ["military_tech", "Defence"], ["settings", "Mechanisms / settings"],
    ["smart_toy", "Robotics / AI"],
  ]],
];

/* Every id, flat, in picker order. */
const SUBJECT_ICON_IDS = ICON_GROUPS.flatMap(([, items]) => items.map(([id]) => id));

const ICON_LABELS = Object.fromEntries(
  ICON_GROUPS.flatMap(([, items]) => items.map(([id, label]) => [id, label]))
);

const ICON_IDS = new Set(SUBJECT_ICON_IDS);

/* Must mirror the `rules` list in SubjectIcons.kt. First match wins. */
const ICON_RULES = [
  [["data structure", "data struct"], "data_object"],
  [["operating system", "os "], "computer"],
  [["computer network", "networking"], "router"],
  [["electrical", "electric"], "electrical_services"],
  [["microprocessor", "microcontroller", "embedded", "vlsi", "cmos", "semiconductor",
    "microelectronic", "analog circuit", "digital circuit", "electronic"], "memory"],
  [["machine learning", "artificial intelligence", "ai "], "smart_toy"],
  [["signal processing", "digital signal", "signal & system", "signals and system"], "cell_tower"],
  [["communication skill", "soft skill", "business communication",
    "professional communication", "technical communication"], "record_voice_over"],
  [["civil", "building construction", "structural engineering", "structural design", "construction"], "construction"],
  [["architect", "town planning"], "architecture"],
  [["mathemat", "math ", "calculus", "algebra", "geometry", "trigonometr"], "calculate"],
  [["control system", "control engineer"], "settings"],
  [["mechanic", "machine"], "settings"],
  [["statistics", "probability", "statistic"], "bar_chart"],
  [["physics"], "science"],
  [["chemistr", "organic", "polymer"], "biotech"],
  [["biology", "botany", "zoology"], "forest"],
  [["geology", "survey", "geo"], "landscape"],
  [["thermal", "heat", "thermodynam"], "thermostat"],
  [["wave", "optics", "sound"], "waves"],
  [["kinematic", "motion"], "speed"],
  [["power", "energy"], "electric_bolt"],
  [["program", "coding", "software", "algorithm", "dsa"], "developer_mode"],
  [["web", "html", "javascript"], "code"],
  [["database", "dbms", "sql"], "storage"],
  [["network"], "lan"],
  [["cyber", "security", "crypt"], "security"],
  [["cloud", "devops"], "cloud"],
  [["operating"], "dns"],
  [["linux", "shell", "command"], "terminal"],
  [["economi", "finance", "accounting"], "account_balance"],
  [["manage", "business", "entrepreneur"], "business"],
  [["market", "commerce", "sales"], "store"],
  [["law", "legal", "constitution"], "gavel"],
  [["english", "language"], "language"],
  [["humanities", "history"], "history"],
  [["psycholog", "behaviour", "behavior"], "psychology"],
  [["graphic", "drawing", "drafting"], "straighten"],
  [["ui/ux", "ui design", "product design", "design engineering", "graphic design"], "design_services"],
  [["mobile", "android", "app development"], "phone_android"],
  [["environment", "ecology"], "forest"],
  [["safety"], "health_and_safety"],
  [["physics lab"], "science"],
  [["workshop", "practical"], "handyman"],
  [["project", "seminar"], "rocket_launch"],
];

/** Best-guess icon id for a subject name. Always returns a usable id. */
function autoIconFor(subjectName) {
  const name = ` ${String(subjectName || "").toLowerCase()} `;
  for (const [keywords, id] of ICON_RULES) {
    if (keywords.some((k) => name.includes(k))) return id;
  }
  return "menu_book";
}

const iconLabel = (id) => ICON_LABELS[id] || id.replace(/_/g, " ");

/* Inline preview of one icon. The ligature text doubles as a readable
 * fallback if the icon font is blocked, so the picker is never blank. */
function iconPreview(id, size = 22) {
  const safe = ICON_IDS.has(id) ? id : "menu_book";
  return `<span class="ms icon-glyph" style="font-size:${size}px" aria-hidden="true">${safe}</span>`;
}

/** Category heading for a rendered .icon-group element. */
function groupName(groupEl) {
  const t = groupEl.querySelector(".icon-group-title");
  return t ? t.textContent.toLowerCase() : "";
}

/**
 * Searchable, grouped icon picker. Returns the markup for the modal body and
 * wires itself up; the chosen id is read back from #sIconPicker [data-selected].
 */
function iconPickerMarkup(current) {
  const groups = ICON_GROUPS.map(([group, items]) => `
    <div class="icon-group">
      <div class="icon-group-title">${esc(group)}</div>
      <div class="icon-grid">
        ${items.map(([id, label]) => `
          <button type="button" class="icon-tile${id === current ? " selected" : ""}"
                  data-icon="${id}" title="${esc(label)}" aria-label="${esc(label)}">
            ${iconPreview(id)}
            <span class="icon-tile-label">${esc(label)}</span>
          </button>`).join("")}
      </div>
    </div>`).join("");

  return `
    <div class="field">
      <label>Icon</label>
      <div class="icon-picker" id="sIconPicker" data-selected="${esc(current || "")}">
        <div class="icon-picker-head">
          <input type="search" id="sIconSearch" placeholder="Search icons (try &quot;math&quot;, &quot;code&quot;, &quot;power&quot;)"
                 autocomplete="off" />
          <button type="button" class="btn secondary small" id="sIconAuto">Auto-match name</button>
        </div>
        <div class="icon-picker-selected">
          <span class="muted">Selected</span>
          <span id="sIconNow">${current && ICON_IDS.has(current)
            ? `${iconPreview(current, 18)} <b>${esc(iconLabel(current))}</b>`
            : `<span class="muted">none — the app will guess from the subject name</span>`}</span>
        </div>
        <div class="icon-picker-body" id="sIconGrid">${groups}</div>
      </div>
      <p class="hint">Preview shows the exact glyph students will see. "Auto-match name" picks from the subject name.</p>
    </div>`;
}

/** Attach behaviour to the markup from iconPickerMarkup. Reads #sName live. */
function wireIconPicker(onChange) {
  const root = $("#sIconPicker");
  if (!root) return;
  const grid = $("#sIconGrid", root);
  const search = $("#sIconSearch", root);
  const now = $("#sIconNow", root);

  const select = (id) => {
    root.dataset.selected = id;
    grid.querySelectorAll(".icon-tile").forEach((b) => {
      b.classList.toggle("selected", b.dataset.icon === id);
    });
    now.innerHTML = ICON_IDS.has(id)
      ? `${iconPreview(id, 18)} <b>${esc(iconLabel(id))}</b>`
      : `<span class="muted">none — the app will guess from the subject name</span>`;
    if (onChange) onChange(id);
  };

  grid.addEventListener("click", (e) => {
    const tile = e.target.closest(".icon-tile");
    if (tile) select(tile.dataset.icon);
  });

  search.addEventListener("input", () => {
    const q = search.value.trim().toLowerCase();
    grid.querySelectorAll(".icon-group").forEach((group) => {
      // Match the category name too, so "math" finds the whole Maths & data
      // group even though no individual icon is called "math".
      const groupHit = groupName(group).includes(q);
      let anyVisible = false;
      group.querySelectorAll(".icon-tile").forEach((tile) => {
        const id = tile.dataset.icon;
        const hit = !q || groupHit ||
          id.includes(q) || iconLabel(id).toLowerCase().includes(q);
        tile.style.display = hit ? "" : "none";
        if (hit) anyVisible = true;
      });
      group.style.display = anyVisible ? "" : "none";
    });
  });

  $("#sIconAuto", root).addEventListener("click", () => {
    const guess = autoIconFor($("#sName")?.value || "");
    select(guess);
    search.value = "";
    search.dispatchEvent(new Event("input"));
  });
}
