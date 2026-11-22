import { esc, html, raw, $ } from "./util.js";

export const ICONS = {
  chat: '<svg viewBox="0 0 24 24"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>',
  phone: '<svg viewBox="0 0 24 24"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/></svg>',
  video: '<svg viewBox="0 0 24 24"><path d="m22 8-6 4 6 4V8z"/><rect x="2" y="6" width="14" height="12" rx="2"/></svg>',
  users: '<svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/></svg>',
  settings: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
  shield: '<svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>',
  send: '<svg viewBox="0 0 24 24"><path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z"/></svg>',
  clip: '<svg viewBox="0 0 24 24"><path d="m21.4 11.1-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5"/></svg>',
  smile: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg>',
  back: '<svg viewBox="0 0 24 24"><path d="m15 18-6-6 6-6"/></svg>',
  more: '<svg viewBox="0 0 24 24"><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></svg>',
  x: '<svg viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"/></svg>',
  check: '<svg viewBox="0 0 24 24"><path d="m5 12 5 5L20 7"/></svg>',
  checks: '<svg viewBox="0 0 24 24"><path d="m2 13 4 4 9-10M10 17l1 1 10-12"/></svg>',
  clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  copy: '<svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
  mic: '<svg viewBox="0 0 24 24"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M19 10a7 7 0 0 1-14 0M12 19v3"/></svg>',
  micoff: '<svg viewBox="0 0 24 24"><path d="m2 2 20 20M9 9v2a3 3 0 0 0 5.1 2.1M15 9.3V5a3 3 0 0 0-5.9-.6M19 10a7 7 0 0 1-.9 3.4M5 10a7 7 0 0 0 11 5.7M12 19v3"/></svg>',
  videooff: '<svg viewBox="0 0 24 24"><path d="m2 2 20 20M16 16v1a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2m5 0h3a2 2 0 0 1 2 2v3.3l6-4.3v12"/></svg>',
  phoneend: '<svg viewBox="0 0 24 24"><path d="M10.7 13.3a16 16 0 0 1-3-2.1l1.4-1.4a2 2 0 0 0 .5-2.1c-.3-.9-.6-1.8-.7-2.8A2 2 0 0 0 6.9 3.2h-3A2 2 0 0 0 2 5.3c.3 3 1.4 5.9 3.1 8.6M22 2 2 22"/></svg>',
  edit: '<svg viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>',
  trash: '<svg viewBox="0 0 24 24"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>',
  reply: '<svg viewBox="0 0 24 24"><path d="m9 17-5-5 5-5M20 18v-2a4 4 0 0 0-4-4H4"/></svg>',
  link: '<svg viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/></svg>',
  moon: '<svg viewBox="0 0 24 24"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>',
  logout: '<svg viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg>',
  flag: '<svg viewBox="0 0 24 24"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1zM4 22v-7"/></svg>',
  block: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m4.9 4.9 14.2 14.2"/></svg>',
  inbox: '<svg viewBox="0 0 24 24"><path d="M22 12h-6l-2 3h-4l-2-3H2M5.5 5.1 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.7 4H7.3a2 2 0 0 0-1.8 1.1z"/></svg>',
};
export const icon = (name) => raw(ICONS[name] || "");

export function toast(message, type = "") {
  const stack = $("#toasts");
  const t = document.createElement("div");
  t.className = "toast " + type; t.textContent = message;
  stack.appendChild(t);
  setTimeout(() => t.remove(), type === "error" ? 5000 : 3500);
}

let openModals = [];
/**
 * Opens a modal. Returns { el, close, setContent }. `build` receives helpers and returns {title, body, footer}.
 * Content can be swapped in place (used by the Terms popup "See more" / "Back" navigation).
 */
export function openModal({ title, body, footer = "", onClose, labelledBy = "modal-title" }) {
  const root = $("#modal-root");
  const back = document.createElement("div");
  back.className = "modal-backdrop";
  back.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-labelledby="${labelledBy}">
    <div class="modal-head"><h2 id="${labelledBy}"></h2><button class="icon-btn" data-close aria-label="Close">${ICONS.x}</button></div>
    <div class="modal-body"></div><div class="modal-foot"></div></div>`;
  root.appendChild(back);
  const api = {
    el: back,
    set({ title: t, body: b, footer: f }) {
      if (t != null) $("#" + labelledBy, back).textContent = t;
      if (b != null) $(".modal-body", back).innerHTML = b instanceof Object && "s" in b ? b.s : b;
      if (f != null) { const foot = $(".modal-foot", back); foot.innerHTML = f instanceof Object && "s" in f ? f.s : f; foot.classList.toggle("hidden", !f); }
      $(".modal-body", back).scrollTop = 0;
    },
    close() { back.remove(); openModals = openModals.filter((m) => m !== api); document.removeEventListener("keydown", onKey); onClose?.(); },
  };
  const onKey = (e) => { if (e.key === "Escape" && openModals[openModals.length - 1] === api) api.close(); };
  document.addEventListener("keydown", onKey);
  back.addEventListener("click", (e) => { if (e.target === back || e.target.closest("[data-close]")) api.close(); });
  openModals.push(api);
  api.set({ title, body, footer });
  return api;
}

export function confirmDialog({ title, message, confirmText = "Confirm", danger = false }) {
  return new Promise((resolve) => {
    let done = false;
    const m = openModal({
      title, body: html`<p>${message}</p>`,
      footer: html`<button class="btn secondary" data-no>Cancel</button><button class="btn ${danger ? "danger" : ""}" data-yes>${confirmText}</button>`,
      onClose: () => { if (!done) resolve(false); },
    });
    m.el.addEventListener("click", (e) => {
      if (e.target.closest("[data-yes]")) { done = true; m.close(); resolve(true); }
      else if (e.target.closest("[data-no]")) { m.close(); }
    });
  });
}

/** Floating context menu anchored to an element. items: [{label, icon, danger, onClick}]; quick: {emojis, onPick}. */
export function openMenu(anchor, items, { quick } = {}) {
  closeMenu();
  const m = document.createElement("div");
  m.className = "menu"; m.id = "ctx-menu"; m.setAttribute("role", "menu");
  const quickHtml = quick ? `<div class="quick">${quick.emojis.map((e) => `<button data-q="${esc(e)}" aria-label="React ${esc(e)}">${esc(e)}</button>`).join("")}</div>` : "";
  m.innerHTML = quickHtml +
    items.map((it, i) => `<button role="menuitem" data-i="${i}" class="${it.danger ? "danger" : ""}">${it.icon ? ICONS[it.icon] : ""}<span>${esc(it.label)}</span></button>`).join("");
  document.body.appendChild(m);
  const r = anchor.getBoundingClientRect(), w = m.offsetWidth, h = m.offsetHeight;
  m.style.left = Math.max(8, Math.min(r.left, innerWidth - w - 8)) + "px";
  m.style.top = (r.bottom + h + 8 > innerHeight ? Math.max(8, r.top - h - 4) : r.bottom + 4) + "px";
  m.addEventListener("click", (e) => {
    const b = e.target.closest("button"); if (!b) return;
    closeMenu();
    if (b.dataset.i != null) items[+b.dataset.i]?.onClick?.();
    else if (b.dataset.q) quick?.onPick?.(b.dataset.q);
  });
  setTimeout(() => document.addEventListener("click", closeMenu, { once: true }), 0);
  return m;
}
export function closeMenu() { $("#ctx-menu")?.remove(); }

export function emptyState(iconName, title, text, action = "") {
  return html`<div class="empty"><div class="art">${icon(iconName)}</div><h3>${title}</h3><p>${text}</p>${action}</div>`;
}
export function spinnerBlock(text = "Loading…") { return html`<div class="splash"><span class="spinner"></span><span>${text}</span></div>`; }
