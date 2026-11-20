/* =====================================================================
   CropDisease AI Pro - shared app logic (every app page loads this)
   ===================================================================== */

const CD = (() => {

    const USER_KEY = "cropdiseaseUser";

    // ------------------------------------------------------------------
    // Session (account is optional: guests can scan, nothing is stored)
    // ------------------------------------------------------------------
    function user() {
        try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch (e) { return null; }
    }
    function setUser(u) { if (u) localStorage.setItem(USER_KEY, JSON.stringify(u)); }
    function logout() { localStorage.removeItem(USER_KEY); location.href = "login.html"; }
    function requireUser() {
        const u = user();
        if (!u || !u.id) { location.replace("login.html?next=" + encodeURIComponent(location.pathname.slice(1) + location.search)); throw new Error("login"); }
        return u;
    }

    // ------------------------------------------------------------------
    // API helper
    // ------------------------------------------------------------------
    async function api(path, options = {}) {
        const init = { method: options.method || "GET", headers: {} };
        if (options.form) init.body = options.form;
        else if (options.body !== undefined) {
            init.headers["Content-Type"] = "application/json";
            init.body = JSON.stringify(options.body);
        }
        let res;
        try { res = await fetch(path, init); }
        catch (e) { const err = new Error("Can't reach the CropDisease AI server. Is it running?"); err.status = 0; throw err; }
        const text = await res.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
        if (!res.ok) {
            const err = new Error((data && data.message) || "Something went wrong (" + res.status + ").");
            err.status = res.status;
            throw err;
        }
        return data;
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    const $ = (s, el = document) => el.querySelector(s);
    const split = s => (s || "").split("|").filter(Boolean);
    const pct = v => Math.round((v || 0) * 100);

    function toast(msg, type = "") {
        let wrap = $(".toast-wrap");
        if (!wrap) { wrap = document.createElement("div"); wrap.className = "toast-wrap"; document.body.appendChild(wrap); }
        const t = document.createElement("div");
        t.className = "toast-x " + type;
        const ico = type === "err" ? "bi-exclamation-octagon text-red" : type === "ok" ? "bi-check-circle text-lime" : "bi-info-circle text-sky";
        t.innerHTML = `<i class="bi ${ico}"></i><span>${esc(msg)}</span>`;
        wrap.appendChild(t);
        setTimeout(() => { t.style.opacity = "0"; t.style.transition = "opacity .3s"; setTimeout(() => t.remove(), 300); }, 3400);
    }

    function fmtDate(s) {
        if (!s) return "";
        const d = new Date(s);
        return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) + " · " +
            d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
    }
    function timeAgo(s) {
        const sec = (Date.now() - new Date(s).getTime()) / 1000;
        if (sec < 60) return "just now";
        if (sec < 3600) return Math.floor(sec / 60) + " min ago";
        if (sec < 86400) return Math.floor(sec / 3600) + " h ago";
        if (sec < 604800) return Math.floor(sec / 86400) + " d ago";
        return new Date(s).toLocaleDateString();
    }
    function initials(name) { return (name || "?").split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase(); }

    let cropCache = null;
    async function crops() {
        if (!cropCache) cropCache = await api("/api/crops");
        return cropCache;
    }
    async function cropName(code) {
        if (!code || code === "auto") return "Auto-detect";
        const c = (await crops()).find(x => x.code === code);
        return c ? c.name : code;
    }
    async function cropEmoji(code) {
        const c = (await crops()).find(x => x.code === code);
        return c ? c.emoji : "🌿";
    }

    const typeBadge = t => ({ Fungal: "b-amber", Bacterial: "b-red", Viral: "b-violet", Oomycete: "b-sky", Nutrient: "b-green" }[t] || "b-gray");
    const sevBadge = s => ({ Low: "b-green", Moderate: "b-amber", High: "b-red", Critical: "b-red" }[s] || "b-gray");
    const sevColor = s => ({ Low: "#22C55E", Moderate: "#FBBF24", High: "#FB923C", Critical: "#F87171" }[s] || "#8FA89A");

    function weatherIcon(code) {
        if (code === 0) return "bi-sun";
        if (code <= 2) return "bi-cloud-sun";
        if (code === 3) return "bi-clouds";
        if (code <= 48) return "bi-cloud-fog2";
        if (code <= 57) return "bi-cloud-drizzle";
        if (code <= 67) return "bi-cloud-rain-heavy";
        if (code <= 77) return "bi-cloud-snow";
        if (code <= 82) return "bi-cloud-rain";
        return "bi-cloud-lightning-rain";
    }

    function speak(text) {
        if (!("speechSynthesis" in window)) { toast("Voice is not supported in this browser."); return; }
        speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(text);
        u.rate = 0.95;
        const v = speechSynthesis.getVoices().find(v => /en-IN/i.test(v.lang)) || speechSynthesis.getVoices().find(v => /^en/i.test(v.lang));
        if (v) u.voice = v;
        speechSynthesis.speak(u);
    }

    // ------------------------------------------------------------------
    // App shell (sidebar + bottom nav)
    // ------------------------------------------------------------------
    const NAV = [
        { id: "home", href: "home.html", icon: "bi-grid-1x2", label: "Dashboard" },
        { id: "scan", href: "scan.html", icon: "bi-camera", label: "AI Leaf Scan" },
        { id: "history", href: "history.html", icon: "bi-clock-history", label: "Scan History" },
        { section: "Crop doctor" },
        { id: "library", href: "library.html", icon: "bi-journal-medical", label: "Disease Library" },
        { id: "symptoms", href: "symptoms.html", icon: "bi-ui-checks-grid", label: "Symptom Checker" },
        { id: "assistant", href: "assistant.html", icon: "bi-chat-dots", label: "Krishi Mitra AI" },
        { section: "Farm tools" },
        { id: "weather", href: "weather.html", icon: "bi-cloud-sun", label: "Weather Risk" },
        { id: "spray", href: "spray.html", icon: "bi-droplet-half", label: "Spray Calculator" },
        { id: "qr", href: "qr.html", icon: "bi-qr-code", label: "Open on Phone" },
    ];

    function shell(active) {
        const u = user();
        const side = $("#sidebar");
        if (side) {
            side.innerHTML = `
                <a href="index.html" class="brand"><span class="brand-mark"><i class="bi bi-flower1"></i></span>
                    <span><b>CropDisease <span class="text-grad">AI</span></b><small>Pro · v2</small></span></a>
                ${NAV.map(n => n.section ? `<div class="side-section small-caps">${n.section}</div>` :
                `<a href="${n.href}" class="side-link ${n.id === active ? "active" : ""}"><i class="bi ${n.icon}"></i>${n.label}</a>`).join("")}
                <div class="side-foot">
                    ${u ? `<a href="profile.html" class="d-flex align-items-center gap-2 text-decoration-none text-reset">
                            <span class="avatar">${esc(initials(u.fullName))}</span>
                            <span class="lh-sm"><b class="d-block small">${esc(u.fullName)}</b><span class="text-muted" style="font-size:.75rem">${esc(u.village || "My farm")}</span></span></a>`
                    : `<div class="small fw-bold mb-1">Guest mode</div><div class="text-muted mb-2" style="font-size:.78rem">Sign in to save scans & see your farm dashboard.</div>
                       <a href="login.html" class="btn btn-grad btn-sm w-100">Sign in</a>`}
                </div>`;
        }
        const bn = $("#bottomNav");
        if (bn) {
            const items = [
                { id: "home", href: "home.html", icon: "bi-grid-1x2", label: "Home" },
                { id: "library", href: "library.html", icon: "bi-journal-medical", label: "Library" },
                { id: "scan", href: "scan.html", icon: "bi-camera-fill", label: "", fab: true },
                { id: "weather", href: "weather.html", icon: "bi-cloud-sun", label: "Weather" },
                { id: "assistant", href: "assistant.html", icon: "bi-chat-dots", label: "Mitra" },
            ];
            bn.innerHTML = items.map(i => `<a href="${i.href}" class="${i.fab ? "fab" : ""} ${i.id === active ? "active" : ""}"><i class="bi ${i.icon}"></i>${i.label}</a>`).join("");
        }
        const mh = $("#mobileHead");
        if (mh) {
            mh.innerHTML = `<a href="index.html" class="brand p-0"><span class="brand-mark"><i class="bi bi-flower1"></i></span><span><b>CropDisease <span class="text-grad">AI</span></b></span></a>
                <div class="ms-auto d-flex gap-2"><a href="history.html" class="icon-btn"><i class="bi bi-clock-history"></i></a>
                <a href="${u ? "profile.html" : "login.html"}" class="icon-btn"><i class="bi ${u ? "bi-person" : "bi-box-arrow-in-right"}"></i></a></div>`;
        }
    }

    // ------------------------------------------------------------------
    // Diagnosis result renderer (scan page, history, report)
    // ------------------------------------------------------------------
    function ringColor(status, conf) {
        if (status === "HEALTHY") return "#22C55E";
        if (status === "UNCERTAIN") return "#FBBF24";
        return conf > 0.7 ? "#A3E635" : "#FBBF24";
    }

    function treatmentPlan(dx, d) {
        if (!d) {
            return [
                ["Today", "Leaf looks healthy. Note the date and keep this scan as your baseline."],
                ["Every 7 days", "Scout 10-20 plants across the field, especially lower leaves and field edges."],
                ["Before rain", "Check the Weather Risk page - spray a protectant only when disease risk is high."],
                ["Monthly", "Balanced nutrition (soil test), weed control and good spacing keep plants resistant."],
            ];
        }
        const org = split(d.organicTreatment), chem = split(d.chemicalTreatment), prev = split(d.prevention);
        const severe = ["Severe", "Critical"].includes(dx.stage) || d.severity === "Critical";
        const viral = d.type === "Viral";
        const nutrient = d.type === "Nutrient";
        const plan = [];
        plan.push(["Day 0 - today", viral ? "Uproot and destroy clearly infected plants to stop the virus spreading. " + (org[0] || "")
            : nutrient ? (chem[0] || org[0]) : (org[0] || "Remove infected leaves and destroy them away from the field.")]);
        if (!nutrient) plan.push(["Day 0-1", severe ? "Spray: " + (chem[0] || org[1] || "") : "Spray (start gentle): " + (org[1] || chem[0] || "")]);
        plan.push(["Day 3", "Re-check the field. Count how many plants show new symptoms and remove fresh infected leaves."]);
        if (!nutrient) plan.push(["Day 7", severe ? "Second spray with a different group to avoid resistance: " + (chem[1] || chem[0] || "") : "If spots are still spreading, use: " + (chem[0] || org[2] || "")]);
        plan.push(["Day 14", "Re-scan the same plant with CropDisease AI and compare the health score with today's."]);
        plan.push(["Next season", prev[0] || "Rotate crops and use healthy seed."]);
        return plan;
    }

    function renderResult(el, data, opts = {}) {
        const dx = data.diagnosis, f = data.features, d = data.disease;
        const conf = pct(dx.confidence);
        const color = ringColor(dx.status, dx.confidence);
        const statusBadge = dx.status === "HEALTHY" ? `<span class="badge-soft b-green"><i class="bi bi-shield-check"></i> Healthy</span>`
            : dx.status === "UNCERTAIN" ? `<span class="badge-soft b-amber"><i class="bi bi-question-circle"></i> Needs confirmation</span>`
                : `<span class="badge-soft b-red"><i class="bi bi-bug"></i> Disease detected</span>`;
        const mix = dx.lesionMix || {};
        const mixColors = { "chlorosis": "#FFD228", "necrosis": "#FF4632", "dark lesion": "#DC28DC", "powdery / mildew": "#3CDCFF", "rust pustule": "#FF8C14" };
        const healthColor = dx.healthScore >= 80 ? "text-lime" : dx.healthScore >= 55 ? "text-amber" : "text-red";
        const plan = treatmentPlan(dx, d);
        const sayText = dx.status === "HEALTHY"
            ? `The leaf looks healthy with a health score of ${dx.healthScore}. Keep monitoring weekly.`
            : `${dx.title}. Confidence ${conf} percent. About ${dx.severityPct} percent of the leaf is affected, stage ${dx.stage}. ${dx.urgency}. ` +
            (d ? `First step: ${split(d.organicTreatment)[0] || ""}. Chemical option: ${split(d.chemicalTreatment)[0] || ""}.` : "");

        el.innerHTML = `
        <div class="row g-3">
          <div class="col-12">
            <div class="glass card-outline-grad reveal d1">
              <div class="d-flex flex-column flex-md-row gap-4 align-items-md-center">
                <div class="ring" style="--p:${conf};--c:${color}"><div><span><b>${conf}%</b><small>confidence</small></span></div></div>
                <div class="flex-grow-1">
                  <div class="d-flex flex-wrap gap-2 mb-2">${statusBadge}
                    ${d ? `<span class="badge-soft ${typeBadge(d.type)}">${esc(d.type)}</span><span class="badge-soft ${sevBadge(d.severity)}">${esc(d.severity)} risk</span>` : ""}
                    <span class="badge-soft b-gray" data-crop>${esc(data.crop)}</span></div>
                  <h2 class="fw-800 mb-1" style="letter-spacing:-.02em">${esc(dx.title)}</h2>
                  ${d ? `<div class="text-muted mb-2"><i>${esc(d.pathogen)}</i> · possible yield loss ${esc(d.yieldLoss)}</div>
                         <p class="mb-0 text-muted" style="max-width:720px">${esc(d.description)}</p>`
                     : `<p class="mb-0 text-muted">No significant disease symptoms were found on this leaf.</p>`}
                </div>
              </div>
              <div class="row g-3 mt-2">
                <div class="col-6 col-lg-3"><div class="glass glass-sm h-100"><div class="small-caps">Health score</div><div class="stat-val ${healthColor} mt-1">${dx.healthScore}<span class="fs-6 text-muted">/100</span></div></div></div>
                <div class="col-6 col-lg-3"><div class="glass glass-sm h-100"><div class="small-caps">Leaf affected</div><div class="stat-val mt-1">${dx.severityPct}<span class="fs-6 text-muted">%</span></div></div></div>
                <div class="col-6 col-lg-3"><div class="glass glass-sm h-100"><div class="small-caps">Stage</div><div class="stat-val mt-1" style="font-size:1.35rem">${esc(dx.stage)}</div></div></div>
                <div class="col-6 col-lg-3"><div class="glass glass-sm h-100"><div class="small-caps">Action</div><div class="fw-700 mt-1 small ${dx.status === "HEALTHY" ? "text-lime" : "text-amber"}"><i class="bi bi-alarm"></i> ${esc(dx.urgency)}</div></div></div>
              </div>
              <div class="d-flex flex-wrap gap-2 mt-3 no-print">
                <button class="btn btn-glass btn-sm" data-act="speak"><i class="bi bi-volume-up"></i> Listen</button>
                ${data.scanId ? `<a class="btn btn-glass btn-sm" href="report.html?id=${data.scanId}"><i class="bi bi-file-earmark-pdf"></i> Report / PDF</a>` : `<button class="btn btn-glass btn-sm" data-act="print"><i class="bi bi-printer"></i> Print</button>`}
                ${d ? `<a class="btn btn-glass btn-sm" href="disease.html?code=${encodeURIComponent(d.code)}"><i class="bi bi-journal-medical"></i> Full disease guide</a>
                       <a class="btn btn-glass btn-sm" href="spray.html"><i class="bi bi-droplet-half"></i> Spray calculator</a>` : ""}
                ${opts.newScan ? `<button class="btn btn-grad btn-sm" data-act="new"><i class="bi bi-camera"></i> Scan another leaf</button>` : ""}
              </div>
              ${dx.qualityWarnings && dx.qualityWarnings.length ? `<div class="mt-3 p-3 rounded-4" style="background:rgba(251,191,36,.08);border:1px solid rgba(251,191,36,.3)">
                 <div class="fw-700 text-amber small mb-1"><i class="bi bi-camera"></i> Photo quality tips (confidence was reduced)</div>
                 <ul class="small mb-0 ps-3">${dx.qualityWarnings.map(w => `<li>${esc(w)}</li>`).join("")}</ul></div>` : ""}
            </div>
          </div>

          <div class="col-lg-7">
            <div class="glass h-100 reveal d2">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <div><div class="small-caps">AI vision</div><h5 class="fw-800 mb-0">Disease heatmap</h5></div>
                <span class="text-muted small">Drag to compare</span>
              </div>
              <div class="compare" id="cmp">
                <img src="${data.imageUrl}" alt="Leaf photo">
                <img src="${data.heatmapUrl}" class="top" alt="AI heatmap">
                <span class="lbl" style="left:10px">PHOTO</span><span class="lbl" style="right:10px">AI HEATMAP</span>
                <div class="handle"></div>
                <input type="range" min="0" max="100" value="50" aria-label="Compare photo and heatmap">
              </div>
              <div class="d-flex flex-wrap gap-3 mt-3 small text-muted">
                ${Object.entries(mixColors).map(([k, c]) => `<span><span class="legend-dot" style="background:${c}"></span>${k}</span>`).join("")}
                <span><span class="legend-dot" style="background:#3c9a5a"></span>healthy tissue</span>
              </div>
            </div>
          </div>

          <div class="col-lg-5">
            <div class="glass h-100 reveal d3">
              <div class="small-caps">Lesion analysis</div><h5 class="fw-800 mb-3">What the AI measured</h5>
              ${Object.entries(mix).map(([k, v]) => `
                <div class="mb-2"><div class="d-flex justify-content-between small fw-600"><span>${esc(k)}</span><span class="text-muted">${v}%</span></div>
                <div class="meter"><span style="width:${v}%;background:${mixColors[k]}"></span></div></div>`).join("")}
              <ul class="tick-list small mt-3">${(dx.observations || []).map(o => `<li>${esc(o)}</li>`).join("")}</ul>
              <details class="mt-2 small"><summary class="text-muted fw-700" style="cursor:pointer">Technical features</summary>
                <table class="table table-sm small mt-2 mb-0"><tbody>
                  <tr><td class="text-muted">Lesion regions</td><td>${f.spotCount}</td></tr>
                  <tr><td class="text-muted">Largest lesion share</td><td>${pct(f.largestSpotShare)}%</td></tr>
                  <tr><td class="text-muted">Damage on margins</td><td>${pct(f.marginShare)}%</td></tr>
                  <tr><td class="text-muted">Leaf coverage</td><td>${pct(f.leafCoverage)}%</td></tr>
                  <tr><td class="text-muted">Sharpness (Laplacian var.)</td><td>${f.sharpness}</td></tr>
                  <tr><td class="text-muted">Brightness</td><td>${pct(f.brightness)}%</td></tr>
                  <tr><td class="text-muted">Pattern</td><td>${esc(dx.dominantPattern)}</td></tr>
                  <tr><td class="text-muted">Analysis time</td><td>${data.analysisMs ?? "-"} ms</td></tr>
                </tbody></table></details>
            </div>
          </div>

          <div class="col-lg-5">
            <div class="glass h-100 reveal d3">
              <div class="small-caps">Differential diagnosis</div><h5 class="fw-800 mb-2">${dx.status === "HEALTHY" ? "Closest signatures (weak)" : "Other possibilities"}</h5>
              ${!(dx.candidates || []).length ? `<div class="empty py-4"><i class="bi bi-shield-check"></i>No disease signature matched this leaf.</div>` : ""}
              ${(dx.candidates || []).map((c, i) => `
                <a class="cand text-decoration-none text-reset" href="disease.html?code=${encodeURIComponent(c.code)}">
                  <span class="fw-800 text-muted" style="width:18px">${i + 1}</span>
                  <span class="flex-grow-1"><b class="d-block small">${esc(c.name)}${data.crop === "auto" && c.cropCode !== "general" ? ` <span class="text-muted fw-600">· ${esc(c.cropCode)}</span>` : ""}</b>
                    <span class="meter mt-1 d-block" style="height:6px"><span style="width:${pct(c.probability)}%"></span></span></span>
                  <span class="fw-800 small">${pct(c.probability)}%</span></a>`).join("")}
              <div class="text-muted small mt-2"><i class="bi bi-info-circle"></i> Probabilities compare visual signatures${data.symptoms && data.symptoms.length ? " and your symptoms" : ""} of every disease for this crop.</div>
            </div>
          </div>

          <div class="col-lg-7">
            <div class="glass h-100 reveal d4">
              <div class="small-caps">Treatment</div><h5 class="fw-800 mb-3">${d ? "How to treat it" : "Keep it healthy"}</h5>
              ${d ? `<div class="tabs-pill mb-3" id="tTabs">
                    <button class="active" data-t="org"><i class="bi bi-flower2"></i> Organic</button>
                    <button data-t="chem"><i class="bi bi-eyedropper"></i> Chemical</button>
                    <button data-t="prev"><i class="bi bi-shield-check"></i> Prevention</button>
                    <button data-t="sym"><i class="bi bi-search"></i> Symptoms</button></div>
                  <ul class="tick-list" data-p="org">${split(d.organicTreatment).map(x => `<li>${esc(x)}</li>`).join("")}</ul>
                  <ul class="tick-list flask d-none" data-p="chem">${split(d.chemicalTreatment).map(x => `<li>${esc(x)}</li>`).join("")}
                     <li class="text-muted small">Always follow the product label and pre-harvest interval.</li></ul>
                  <ul class="tick-list shield d-none" data-p="prev">${split(d.prevention).map(x => `<li>${esc(x)}</li>`).join("")}</ul>
                  <ul class="tick-list warn d-none" data-p="sym">${split(d.symptoms).map(x => `<li>${esc(x)}</li>`).join("")}</ul>
                  <div class="small text-muted mt-2"><i class="bi bi-thermometer-half"></i> Favoured by: ${esc(d.favourableConditions)}</div>`
                : `<ul class="tick-list shield"><li>Keep 1-2 m between rows for airflow</li><li>Water at the base of plants in the morning</li><li>Remove weeds that host pests and viruses</li><li>Use the weather risk page before rainy spells</li></ul>`}
            </div>
          </div>

          <div class="col-12">
            <div class="glass reveal d5">
              <div class="small-caps">Action plan</div><h5 class="fw-800 mb-3">Your next 14 days</h5>
              <div class="timeline row">${plan.map(([t, s]) => `<div class="t-item col-md-6"><b class="d-block small text-lime">${esc(t)}</b><span class="small">${esc(s)}</span></div>`).join("")}</div>
              <div class="small text-muted mt-1"><i class="bi bi-exclamation-triangle"></i> AI-assisted estimate from one photo. For high-value fields or unclear results, confirm with your local Krishi Vigyan Kendra / agriculture officer.</div>
            </div>
          </div>
        </div>`;

        // interactions
        const cmp = el.querySelector("#cmp");
        if (cmp) {
            const range = cmp.querySelector("input"), top = cmp.querySelector(".top"), handle = cmp.querySelector(".handle");
            range.addEventListener("input", () => { top.style.clipPath = `inset(0 0 0 ${range.value}%)`; handle.style.left = range.value + "%"; });
        }
        const tabs = el.querySelector("#tTabs");
        if (tabs) tabs.addEventListener("click", e => {
            const b = e.target.closest("button"); if (!b) return;
            tabs.querySelectorAll("button").forEach(x => x.classList.toggle("active", x === b));
            el.querySelectorAll("[data-p]").forEach(p => p.classList.toggle("d-none", p.dataset.p !== b.dataset.t));
        });
        el.querySelector('[data-act="speak"]')?.addEventListener("click", () => speak(sayText));
        el.querySelector('[data-act="print"]')?.addEventListener("click", () => window.print());
        if (opts.newScan) el.querySelector('[data-act="new"]')?.addEventListener("click", opts.newScan);
        cropName(data.crop).then(n => { const c = el.querySelector("[data-crop]"); if (c) c.textContent = n; });
    }

    return { user, setUser, logout, requireUser, api, esc, $, split, pct, toast, fmtDate, timeAgo, initials,
        crops, cropName, cropEmoji, typeBadge, sevBadge, sevColor, weatherIcon, speak, shell, renderResult };
})();
