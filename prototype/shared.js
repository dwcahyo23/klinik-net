/* ============================================================
   KLINIK NET — Shared runtime (offline prototype, no backend)
   Depends on: data.js (window.klinikStore)
   ============================================================ */

(function () {
  "use strict";

  /* ---------- Formatting helpers ---------- */
  const fmt = {
    rp(n) {
      const v = Math.round(Number(n) || 0);
      return "Rp " + v.toLocaleString("id-ID");
    },
    rpShort(n) {
      const v = Number(n) || 0;
      if (v >= 1e9) return "Rp " + (v / 1e9).toFixed(1).replace(".0", "") + "M";
      if (v >= 1e6) return "Rp " + (v / 1e6).toFixed(1).replace(".0", "") + "jt";
      if (v >= 1e3) return "Rp " + (v / 1e3).toFixed(0) + "rb";
      return fmt.rp(v);
    },
    num(n) { return (Number(n) || 0).toLocaleString("id-ID"); },
    pct(n) { return (Number(n) || 0).toFixed(1).replace(".0", "") + "%"; },
    date(iso) {
      if (!iso) return "—";
      const [y, m, d] = iso.split("-");
      const names = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"];
      return `${d} ${names[parseInt(m, 10) - 1]} ${y}`;
    },
    dateShort(iso) {
      if (!iso) return "—";
      const [y, m, d] = iso.split("-");
      const names = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"];
      return `${d} ${names[parseInt(m, 10) - 1]}`;
    },
    dayName(iso) {
      const names = ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"];
      return names[new Date(iso).getDay()];
    },
    daysBetween(a, b) {
      return Math.round((new Date(b) - new Date(a)) / 86400000);
    }
  };
  window.fmt = fmt;

  /* ---------- Icon set (inline SVG, stroke-based) ---------- */
  const I = {
    dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="2"/><rect x="14" y="3" width="7" height="5" rx="2"/><rect x="14" y="12" width="7" height="9" rx="2"/><rect x="3" y="16" width="7" height="5" rx="2"/></svg>',
    car: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M5 17h14M3 17v-4l2-5a3 3 0 0 1 2.8-2h8.4A3 3 0 0 1 19 8l2 5v4"/><circle cx="7.5" cy="17" r="2"/><circle cx="16.5" cy="17" r="2"/><path d="M4 12h16"/></svg>',
    users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M17 5.2a3.5 3.5 0 0 1 0 6.6M18.5 20a6.4 6.4 0 0 0-2.2-4.8"/></svg>',
    wallet: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6" width="18" height="14" rx="3"/><path d="M3 10h18M17 14.5h2.5"/></svg>',
    alert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M10.3 3.9 2.4 17.5A2 2 0 0 0 4.1 20.5h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4.5M12 17h.01"/></svg>',
    report: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z"/><path d="M14 3v5h5M9 13h6M9 17h4"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z"/></svg>',
    grid: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="8" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/><rect x="13" y="13" width="8" height="8" rx="2"/></svg>',
    plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>',
    close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
    backspace: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 5H9.5a2 2 0 0 0-1.4.6L3 12l5.1 6.4a2 2 0 0 0 1.4.6H20a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1Z"/><path d="m17 9-4 6M13 9l4 6"/></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m4.5 12.5 5 5 10-11"/></svg>',
    checkCircle: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.7 2.7L16 9.5"/></svg>',
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>',
    xCircle: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1L18 7"/><path d="M10 11v6M14 11v6"/></svg>',
    edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17v3Z"/><path d="m14.5 6.5 3 3"/></svg>',
    moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/></svg>',
    sun: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>',
    rotate: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4a8 8 0 1 0 7.5 5.2"/><path d="M12 4 8 8M12 4l4 4"/><rect x="4" y="13" width="16" height="8" rx="2"/></svg>',
    lock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="10" width="16" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>',
    download: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12M8 11l4 4 4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>',
    upload: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 15V3M8 7l4-4 4 4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>',
    phone: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M5 3h3.5a2 2 0 0 1 2 1.7l.7 3a2 2 0 0 1-.5 1.9L9 11a13 13 0 0 0 4 4l1.4-1.7a2 2 0 0 1 1.9-.5l3 .7a2 2 0 0 1 1.7 2V19a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2Z"/></svg>',
    wa: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5.1-1.3A10 10 0 1 0 12 2Zm5.3 14.1c-.2.6-1.3 1.2-1.8 1.2-.5.1-1 .1-1.7-.1a12 12 0 0 1-5.4-4.7c-.4-.6-1-1.6-1-3s.6-2.1.9-2.4c.2-.3.5-.3.7-.3h.5c.2 0 .4 0 .6.5l.8 2c.1.2.1.4 0 .6l-.4.5-.3.3c-.1.2-.3.3-.1.6a9 9 0 0 0 4.1 3.5c.3.1.5.1.7-.1l.8-1c.2-.2.4-.2.6-.1l2 1c.2.1.4.2.4.3v.6Z"/></svg>',
    trendUp: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m3 16 6-6 4 4 7-7"/><path d="M15 7h5v5"/></svg>',
    trendDown: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m3 8 6 6 4-4 7 7"/><path d="M15 17h5v-5"/></svg>',
    calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 10h18"/></svg>',
    wrench: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 6.5a4.5 4.5 0 0 0 5.9 5.9l-8 8a2.8 2.8 0 1 1-4-4l8-8Z"/><path d="M9 6 6.5 3.5 3 7l2.5 2.5"/></svg>',
    key: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="14" r="4"/><path d="m11 11 8-8M17 5l2 2M14 8l2 2"/></svg>',
    bank: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10h18L12 4 3 10Z"/><path d="M5 10v8M19 10v8M9 10v8M15 10v8M3 18h18"/></svg>',
    building: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V6l8-3 8 3v15"/><path d="M9 21v-5h6v5M8 9h2M14 9h2M8 12h2M14 12h2"/></svg>',
    shield: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 4.5 6v6c0 5 3.2 8.4 7.5 9.5 4.3-1.1 7.5-4.5 7.5-9.5V6Z"/><path d="m9 12 2.2 2.2L15.5 10"/></svg>',
    copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="12" height="12" rx="2.5"/><path d="M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1"/></svg>',
    print: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M7 9V3h10v6"/><rect x="3" y="9" width="18" height="7" rx="2"/><path d="M7 16h10v5H7z"/></svg>',
    filter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 5h18l-7 8v6l-4 2v-8Z"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5.5l3.5 2"/></svg>',
    mail: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m3.5 7 8.5 6 8.5-6"/></svg>',
    sparkle: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v5M12 16v5M3 12h5M16 12h5M6.3 6.3l3 3M14.7 14.7l3 3M17.7 6.3l-3 3M9.3 14.7l-3 3"/></svg>',
    empty: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13h3l2-7h8l2 7h3"/><circle cx="7.5" cy="17.5" r="2"/><circle cx="16.5" cy="17.5" r="2"/><path d="M9 10h6M10.5 6h3"/></svg>',
    undo: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9h11a5 5 0 0 1 0 10H9"/><path d="M8 5 4 9l4 4"/></svg>',
    file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z"/><path d="M14 3v5h5"/></svg>'
  };
  window.I = I;

  /* ---------- Navigation model ---------- */
  const PAGES = [
    { id: "dashboard", file: "dashboard.html", label: "Dashboard", icon: "dashboard" },
    { id: "payments", file: "payments.html", label: "Setoran", icon: "wallet" },
    { id: "cars", file: "cars.html", label: "Mobil", icon: "car" },
    { id: "renters", file: "renters.html", label: "Penyewa", icon: "users" },
    { id: "arrears", file: "arrears.html", label: "Tunggakan", icon: "alert" },
    { id: "reports", file: "reports.html", label: "Laporan", icon: "report" },
    { id: "components", file: "components.html", label: "Komponen", icon: "grid" },
    { id: "settings", file: "settings.html", label: "Pengaturan", icon: "settings" }
  ];
  window.PAGES = PAGES;

  /* ---------- Device frame: orientation + theme ---------- */
  const Device = {
    isPortrait: false,

    init() {
      const frame = document.getElementById("device-frame");
      if (!frame) return;
      this.applyScale();
      window.addEventListener("resize", () => this.applyScale());
    },

    applyScale() {
      const frame = document.getElementById("device-frame");
      if (!frame) return;
      const portrait = frame.classList.contains("portrait");
      const w = portrait ? 800 : 1280;
      const h = portrait ? 1280 : 800;
      const pad = 80;
      const scale = Math.min((window.innerWidth - pad) / w, (window.innerHeight - pad) / h, 1);
      frame.style.setProperty("--scale", Math.max(0.28, scale).toFixed(3));
    },

    toggleOrientation() {
      const frame = document.getElementById("device-frame");
      if (!frame) return;
      frame.classList.toggle("portrait");
      this.isPortrait = frame.classList.contains("portrait");
      this.applyScale();
      document.querySelectorAll("[data-orient-btn]").forEach(b => b.classList.remove("active"));
      const btn = document.querySelector(this.isPortrait ? "[data-orient='portrait']" : "[data-orient='landscape']");
      if (btn) btn.classList.add("active");
      window.dispatchEvent(new CustomEvent("orientation_changed", { detail: { portrait: this.isPortrait } }));
      UI.toast(this.isPortrait ? "Mode portrait — bottom sheet & 1 kolom" : "Mode landscape — nav rail & master-detail");
    },

    setTheme(theme) {
      document.documentElement.setAttribute("data-theme", theme);
      localStorage.setItem("KLINIK_THEME", theme);
      document.querySelectorAll("[data-theme-btn]").forEach(b => {
        b.classList.toggle("active", b.getAttribute("data-theme-btn") === theme);
      });
    },

    toggleTheme() {
      const cur = document.documentElement.getAttribute("data-theme") || "light";
      const next = cur === "dark" ? "light" : "dark";
      this.setTheme(next);
      UI.toast(next === "dark" ? "Mode gelap aktif" : "Mode terang aktif");
    }
  };
  window.Device = Device;

  /* ---------- UI primitives ---------- */
  const UI = {
    toast(message, type = "success", actionLabel = "", onAction = null) {
      const host = document.getElementById("snackbar-host") || document.body;
      host.innerHTML = `
        <div class="snackbar ${type === "success" ? "success" : "error"} show" role="status">
          ${type === "success" ? I.checkCircle : I.xCircle}
          <span class="grow">${message}</span>
          ${actionLabel ? `<button class="sn-action">${actionLabel}</button>` : ""}
        </div>`;
      const el = host.firstElementChild;
      if (actionLabel && onAction) {
        el.querySelector(".sn-action").addEventListener("click", () => { onAction(); el.remove(); });
      }
      clearTimeout(this._toastT);
      this._toastT = setTimeout(() => {
        el.style.transform = "translate(-50%, 140%)";
        el.style.opacity = "0";
        setTimeout(() => el.remove(), 320);
      }, actionLabel ? 5200 : 2800);
    },

    openModal(id) {
      const scrim = document.getElementById("scrim");
      const modal = document.getElementById(id);
      if (!modal) return;
      if (scrim) scrim.classList.add("show");
      modal.classList.add("show");
    },

    closeModal(id) {
      const scrim = document.getElementById("scrim");
      const modal = id ? document.getElementById(id) : null;
      if (scrim) scrim.classList.remove("show");
      if (modal) modal.classList.remove("show");
      else document.querySelectorAll(".modal.show, .bottom-sheet.show").forEach(m => m.classList.remove("show"));
    },

    confirm(opts) {
      return new Promise(resolve => {
        const box = document.createElement("div");
        box.className = "modal show";
        box.style.width = "min(460px, calc(100% - 48px))";
        box.innerHTML = `
          <div class="modal-header">
            <div>
              <div class="card-title">${opts.title}</div>
              <div class="card-sub">${opts.subtitle || "Tindakan ini tidak dapat dibatalkan"}</div>
            </div>
          </div>
          <div class="modal-body">
            <p class="text-sm text-secondary" style="line-height:1.6">${opts.message}</p>
            ${opts.reason ? `
              <div class="field mt-4">
                <label class="field-label">Alasan (wajib, untuk jejak audit)</label>
                <input class="input" id="cf-reason" placeholder="Contoh: salah input nominal rekening" />
                <div class="field-error" id="cf-err"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 8v5M12 16h.01"/></svg>Alasan wajib diisi</div>
              </div>` : ""}
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" data-act="cancel">Batal</button>
            <button class="btn ${opts.danger ? "btn-danger" : "btn-primary"}" data-act="ok">${opts.okLabel || "Ya, lanjutkan"}</button>
          </div>`;
        document.getElementById("device-frame").appendChild(box);

        const finish = (val, reason) => { box.remove(); UI.lastConfirmReason = reason || ""; resolve(val); };
        box.querySelector('[data-act="cancel"]').addEventListener("click", () => finish(null));
        box.querySelector('[data-act="ok"]').addEventListener("click", () => {
          if (opts.reason) {
            const r = box.querySelector("#cf-reason").value.trim();
            if (!r) {
              box.querySelector("#cf-err").classList.add("show");
              box.querySelector("#cf-reason").classList.add("is-error");
              return;
            }
            finish(true, r);
          } else finish(true);
        });
      });
    },

    copy(text, label = "Disalin ke clipboard") {
      navigator.clipboard?.writeText(text).catch(() => {});
      this.toast(label);
    },

    download(filename, content, mime = "text/plain") {
      const blob = new Blob([content], { type: mime });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 1000);
      this.toast("Berkas diunduh: " + filename);
    },

    /* Rupiah numpad controller */
    createNumpad(opts) {
      let digits = ""; // raw digits
      const displayEl = opts.display;
      const metaEl = opts.meta;
      const container = opts.container;

      function render() {
        const value = digits === "" ? 0 : parseInt(digits, 10);
        displayEl.innerHTML = `<span class="rp">Rp</span> ${value.toLocaleString("id-ID")}`;
        if (metaEl) metaEl.textContent = opts.metaText ? opts.metaText(value) : "";
        if (opts.onChange) opts.onChange(value);
      }

      function press(key) {
        if (key === "del") {
          digits = digits.slice(0, -1);
        } else if (key === "clear") {
          digits = "";
        } else if (key === "000") {
          if (digits !== "") digits += "000";
        } else if (key === "00") {
          if (digits !== "") digits += "00";
        } else if (key === "save") {
          const value = digits === "" ? 0 : parseInt(digits, 10);
          if (value <= 0) {
            displayEl.style.color = "var(--danger)";
            setTimeout(() => { displayEl.style.color = ""; }, 500);
            return;
          }
          if (opts.onSave) opts.onSave(value);
          digits = "";
        } else {
          digits = (digits + key).replace(/^0+(?=\d)/, "");
          if (digits.length > 12) digits = digits.slice(0, 12);
        }
        render();
      }

      container.addEventListener("click", e => {
        const btn = e.target.closest("[data-key]");
        if (btn) press(btn.getAttribute("data-key"));
      });

      render();
      return {
        set(v) { digits = v ? String(Math.round(v)) : ""; render(); },
        get() { return digits === "" ? 0 : parseInt(digits, 10); }
      };
    },

    /* WhatsApp reminder template */
    waTemplate(renterName, carPlate, amount, daysLate, companyName, phone) {
      const d = new Date();
      const tgl = `${d.getDate()}/${d.getMonth() + 1}/${d.getFullYear()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
      return {
        text: `Halo ${renterName},oreminder setoran sewa mobil ${carPlate} di ${companyName}.\n\n` +
              `Sisa tunggakan: ${fmt.rp(amount)}\nMenunggak: ${daysLate} hari\n\n` +
              `Mohon lakukan setoran via transfer. Terima kasih 🙏`,
        time: tgl,
        link: `https://wa.me/${phone.replace(/\D/g, "")}?text=${encodeURIComponent(
          `Halo ${renterName},oreminder setoran sewa mobil ${carPlate} di ${companyName}.\n\nSisa tunggakan: ${fmt.rp(amount)}\nMenunggak: ${daysLate} hari\n\nMohon lakukan setoran via transfer. Terima kasih 🙏`
        )}`
      };
    }
  };
  window.UI = UI;

  /* ---------- Shell renderer ---------- */
  var Shell = {
    render(activeId, opts = {}) {
      const rail = document.getElementById("nav-rail");
      const topTitle = document.getElementById("page-title");
      const topSub = document.getElementById("page-subtitle");
      if (!rail) return;

      const arrearsCount = window.klinikStore.getDashboardMetrics().criticalArrearsCount;

      rail.innerHTML = `
        <div class="rail-logo"><img src="assets/logo-mark.svg" alt="Klinik Net" /></div>
        ${PAGES.map(p => `
          <a class="rail-item ${p.id === activeId ? "active" : ""}" href="${p.file}" title="${p.label}">
            ${I[p.icon]}
            <span>${p.label}</span>
            ${p.id === "arrears" && arrearsCount > 0 ? `<span class="rail-badge">${arrearsCount}</span>` : ""}
          </a>`).join("")}
        <div class="rail-spacer"></div>
      `;

      if (topTitle) topTitle.textContent = opts.title || (PAGES.find(p => p.id === activeId) || {}).label || "";
      if (topSub) topSub.textContent = opts.subtitle || "Sistem Rental Mobil Harian";
    }
  };
  window.Shell = Shell;

  /* ---------- CSV export ---------- */
  var CSV = {
    toCSV(rows) {
      return rows.map(r => r.map(c => `"${String(c == null ? "" : c).replace(/"/g, '""')}"`).join(",")).join("\n");
    },
    download(filename, rows) {
      UI.download(filename, "﻿" + CSV.toCSV(rows), "text/csv;charset=utf-8");
    }
  };
  window.CSV = CSV;

  /* ---------- Global boot ---------- */
  document.addEventListener("DOMContentLoaded", () => {
    const savedTheme = localStorage.getItem("KLINIK_THEME") || "light";
    Device.setTheme(savedTheme);
    Device.init();

    const scrim = document.getElementById("scrim");
    if (scrim) scrim.addEventListener("click", () => UI.closeModal());

    document.querySelectorAll("[data-orient-btn]").forEach(b => {
      b.addEventListener("click", () => Device.toggleOrientation());
    });
    const themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) themeBtn.addEventListener("click", () => Device.toggleTheme());

    document.addEventListener("keydown", e => {
      if (e.key === "Escape") UI.closeModal();
    });
  });

  /* ---------- Page load skeleton helper ---------- */
  window.showSkeleton = function (container, count = 5) {
    container.innerHTML = Array.from({ length: count }).map(() =>
      `<div class="card mb-4"><div class="skel skel-title"></div><div class="skel skel-line"></div><div class="skel skel-line" style="width:72%"></div></div>`
    ).join("");
  };
})();