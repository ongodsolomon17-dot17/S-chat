// Small, dependency-free helpers. All dynamic text goes through esc()/html`` so user content can never inject markup.
const ESC = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
export const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ESC[c]);

class Raw { constructor(s) { this.s = s; } toString() { return this.s; } }
export const raw = (s) => new Raw(s);
/** Tagged template: interpolations are escaped unless wrapped in raw() (or arrays of raw()). */
export function html(strings, ...vals) {
  let out = strings[0];
  vals.forEach((v, i) => {
    const part = Array.isArray(v) ? v.map((x) => (x instanceof Raw ? x.s : esc(x))).join("") : v instanceof Raw ? v.s : esc(v);
    out += part + strings[i + 1];
  });
  return new Raw(out);
}
export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
export function mount(target, content) { target.innerHTML = content instanceof Raw ? content.s : String(content); return target; }

export const debounce = (fn, ms = 250) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
export const throttle = (fn, ms) => { let last = 0; return (...a) => { const n = Date.now(); if (n - last >= ms) { last = n; fn(...a); } }; };
export const uid = () => (crypto.randomUUID ? crypto.randomUUID() : "id-" + Math.random().toString(36).slice(2) + Date.now());

export function safeUrl(u) { return typeof u === "string" && /^(https?:|blob:)/i.test(u) ? u : ""; }
export function initials(name) { return (String(name || "?").trim()[0] || "?").toUpperCase(); }
const COLORS = ["#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#ef4444", "#ec4899", "#8b5cf6", "#14b8a6"];
export function colorFor(name) { let h = 0; for (const ch of String(name || "")) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return COLORS[h % COLORS.length]; }
export function avatar(user, size = 44, online = null) {
  const name = user?.username || "?";
  const url = safeUrl(user?.profilePictureUrl);
  const inner = url ? html`<img src="${url}" alt="" loading="lazy">` : html`${initials(name)}`;
  return html`<span class="avatar-wrap"><span class="avatar" style="--s:${size}px;--c:${colorFor(name)}">${inner}</span>${online ? raw('<span class="presence" title="Online"></span>') : ""}</span>`;
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" });
const dayFmt = new Intl.DateTimeFormat(undefined, { weekday: "short", day: "numeric", month: "short" });
export const fmtTime = (iso) => (iso ? timeFmt.format(new Date(iso)) : "");
export function fmtDayLabel(iso) {
  const d = new Date(iso), now = new Date();
  const start = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((start(now) - start(d)) / 86400000);
  if (diff === 0) return "Today"; if (diff === 1) return "Yesterday";
  return dayFmt.format(d) + (d.getFullYear() !== now.getFullYear() ? " " + d.getFullYear() : "");
}
export function fmtListTime(iso) {
  if (!iso) return "";
  const d = new Date(iso), now = new Date();
  if (d.toDateString() === now.toDateString()) return fmtTime(iso);
  return fmtDayLabel(iso);
}
export function fmtDuration(sec) { const m = Math.floor(sec / 60), s = Math.floor(sec % 60); return `${m}:${String(s).padStart(2, "0")}`; }
export const isImage = (u) => /\.(png|jpe?g|gif|webp|avif|bmp)(\?|$)/i.test(u);
export const isVideo = (u) => /\.(mp4|webm|mov|m4v|ogv)(\?|$)/i.test(u);
export const isAudio = (u) => /\.(mp3|wav|ogg|m4a|aac|weba|opus)(\?|$)/i.test(u);
export function fileName(u) { try { return decodeURIComponent(new URL(u).pathname.split("/").pop() || "file"); } catch { return "file"; } }
export function legalValue(key, label) {
  const v = (window.S_CHAT_CONFIG?.[key] || "").trim();
  return v ? esc(v) : `<span class="placeholder" title="Set ${key} in js/config.js">[${esc(label)} — owner to provide]</span>`;
}
export const appName = () => window.S_CHAT_CONFIG?.APP_NAME || "S-Chat";
export function publicBase() { return (window.S_CHAT_CONFIG?.PUBLIC_BASE_URL || location.origin).replace(/\/$/, ""); }
export function chatLink(username) { return `${publicBase()}/chat/${encodeURIComponent(username)}`; }
