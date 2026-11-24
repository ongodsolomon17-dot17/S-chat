// Status (24-hour stories). Data layer only; the screens live in views/status.js.
import { api, upload } from "./api.js";
import { state, bus } from "./state.js";

const seenKey = () => "schat_seen_status_" + (state.me?.id || "anon");
function readSeen() { try { return new Set(JSON.parse(localStorage.getItem(seenKey()) || "[]")); } catch { return new Set(); } }
let seen = null;
export function isSeen(id) { seen ||= readSeen(); return seen.has(id); }
export function markSeen(id) {
  seen ||= readSeen(); if (seen.has(id)) return; seen.add(id);
  try { localStorage.setItem(seenKey(), JSON.stringify([...seen].slice(-500))); } catch { /* storage full/blocked: seen-state is cosmetic */ }
  bus.emit("status");
}
export const resetSeenCache = () => { seen = null; };

export async function loadStatuses() {
  const [feed, mine] = await Promise.all([api("/status/feed"), api("/status/mine")]);
  state.status.feed = feed; state.status.mine = mine; state.status.loaded = true;
  bus.emit("status");
}

/** Friends' statuses grouped per author, each group oldest→newest, groups with unseen items first. */
export function groupFeed(feed = state.status.feed) {
  const by = new Map();
  for (const s of feed) { const g = by.get(s.author.id) || { author: s.author, items: [] }; g.items.push(s); by.set(s.author.id, g); }
  const groups = [...by.values()].map((g) => {
    g.items.sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
    g.unseen = g.items.filter((i) => !isSeen(i.id)).length;
    g.latestAt = g.items[g.items.length - 1].createdAt;
    return g;
  });
  return groups.sort((a, b) => (b.unseen > 0) - (a.unseen > 0) || Date.parse(b.latestAt) - Date.parse(a.latestAt));
}
export const unseenCount = () => state.status.feed.filter((s) => !isSeen(s.id)).length;

export const TEXT_COLORS = ["#0aa89a", "#7c3aed", "#dc2626", "#ea580c", "#0284c7", "#16a34a", "#111827"];
export const DEFAULT_BG = "#0aa89a";

export async function postText(text, backgroundColor) {
  const fd = new FormData(); fd.append("textContent", text); fd.append("backgroundColor", backgroundColor || DEFAULT_BG);
  const post = await api("/status", { method: "POST", body: fd });
  await loadStatuses(); return post;
}
export async function postFile(file, caption, onProgress) {
  const post = await upload("/status", file, onProgress, { caption });
  await loadStatuses(); return post;
}
export async function deleteStatus(id) { await api(`/status/${id}`, { method: "DELETE" }); await loadStatuses(); }
export const replyToStatus = (id, content) => api(`/status/${id}/reply`, { method: "POST", body: { content } });

/** Same rule the original app used: the backend stores voice notes and video both as .webm, so .webm means audio. */
export function kindOf(s) {
  const u = s.mediaUrl || "";
  if (!u) return "text";
  if (/\.(mp4|mov|m4v)(\?|$)/i.test(u)) return "video";
  if (/\.(ogg|oga|m4a|mp3|aac|wav|weba|webm|opus)(\?|$)/i.test(u)) return "audio";
  return "image";
}
export function timeLeft(expiresAt) {
  const ms = Date.parse(expiresAt) - Date.now(); if (ms <= 0) return "expired";
  const h = Math.floor(ms / 3600000), m = Math.floor((ms % 3600000) / 60000);
  return h ? `${h}h left` : `${Math.max(1, m)}m left`;
}
export function ago(iso) {
  const m = Math.floor((Date.now() - Date.parse(iso)) / 60000);
  if (m < 1) return "just now"; if (m < 60) return `${m}m ago`; const h = Math.floor(m / 60); return h < 24 ? `${h}h ago` : `${Math.floor(h / 24)}d ago`;
}
