// Chat domain logic: loading, sending, realtime frames. Views never talk to the API directly for chat data;
// they read `state` and listen for bus events ("chats", "conv:<id>", "presence", "typing", "requests").
import { api } from "./api.js";
import { state, bus, totalUnread } from "./state.js";
import { send as wsSend } from "./ws.js";
import { uid, appName } from "./util.js";
import { toast } from "./ui.js";
import * as calls from "./calls.js";

const PAGE = 30;
const pendingAcks = new Map(); // clientMessageId -> timeout

export const QUICK_REACTIONS = ["❤️", "😂", "👍", "😮", "😢", "🙏"];

function updateTitle() {
  const n = totalUnread();
  document.title = (n ? `(${n}) ` : "") + appName();
}
const emitChats = () => { updateTitle(); bus.emit("chats"); };
const emitConv = (id) => bus.emit("conv:" + id);

export function chatFor(userId) { return state.chats.get(userId); }
export function chatByUsername(username) {
  const u = String(username).toLowerCase();
  for (const c of state.chats.values()) if (c.user.username.toLowerCase() === u) return c;
  return null;
}

export const AI_ID = "ai";
export const AI_USER = { id: AI_ID, username: "STech AI", publicId: "", profilePictureUrl: null, deleted: false, isAi: true };

/** Puts/refreshes the assistant in the chat list. It sorts by latest message exactly like a friend — never pinned. */
export function setAiEntry(map, summary) {
  const mine = summary?.latestRole === "user";
  map.set(AI_ID, {
    user: AI_USER, ai: true, unreadCount: 0,
    latestMessage: summary?.latestContent || null, latestAttachmentUrl: null,
    latestMessageAt: summary?.latestAt || null, latestSenderId: summary?.latestRole ? (mine ? state.me.id : AI_ID) : null,
  });
  if (summary) state.ai.available = summary.available !== false;
}

export async function loadChats() {
  const [list, aiSummary] = await Promise.all([api("/chat/list"), api("/ai/summary").catch(() => null)]);
  const next = new Map();
  for (const it of list) {
    next.set(it.otherUser.id, {
      user: it.otherUser, latestMessage: it.latestMessage, latestAttachmentUrl: it.latestAttachmentUrl,
      latestMessageAt: it.latestMessageAt, latestSenderId: it.latestSenderId, unreadCount: it.unreadCount || 0,
    });
  }
  setAiEntry(next, aiSummary);
  state.chats = next; emitChats();
}
export async function loadRequests() {
  state.requests = await api("/friends/requests/incoming"); bus.emit("requests");
}

export async function loadHistory(friendId, page = 0) {
  const rows = await api(`/chat/history/${friendId}?page=${page}&size=${PAGE}`);
  const asc = rows.slice().reverse();
  const cur = state.messages.get(friendId) || [];
  state.messages.set(friendId, page === 0 ? asc : [...asc, ...cur]);
  state.hasMore.set(friendId, rows.length === PAGE);
  emitConv(friendId);
  return rows.length;
}

/** Marks the conversation read on the server. `force` is used while the chat is open on screen, where the
 *  local unread counter is already 0 but the server still has the just-arrived message as unread. */
export async function markRead(friendId, force = false) {
  const c = state.chats.get(friendId);
  if (!c || (!c.unreadCount && !force)) return;
  if (c.unreadCount) { c.unreadCount = 0; emitChats(); }
  try { await api(`/chat/read/${friendId}`, { method: "POST" }); } catch { /* next open retries */ }
}

function touchChat(friendId, msg, mine) {
  const c = state.chats.get(friendId); if (!c) return;
  c.latestMessage = msg.deleted ? "This message was deleted" : msg.content;
  c.latestAttachmentUrl = msg.attachmentUrl || null;
  c.latestMessageAt = msg.sentAt; c.latestSenderId = msg.senderId;
  if (!mine && state.activeChatId !== friendId) c.unreadCount = (c.unreadCount || 0) + 1;
  emitChats();
}
const findMsg = (friendId, id) => (state.messages.get(friendId) || []).find((m) => m.id === id);

export function sendMessage(friendId, content, { attachmentUrl = null, replyTo = null } = {}) {
  const clientId = uid();
  const msg = {
    id: clientId, clientMessageId: clientId, senderId: state.me.id, receiverId: friendId, content, attachmentUrl,
    sentAt: new Date().toISOString(), deliveredAt: null, readAt: null, editedAt: null, deleted: false, reactions: [],
    replyTo: replyTo ? { messageId: replyTo.id, senderId: replyTo.senderId, snippet: replyTo.content, attachmentUrl: replyTo.attachmentUrl, deleted: false } : null,
    _pending: true,
  };
  const list = state.messages.get(friendId) || []; list.push(msg); state.messages.set(friendId, list);
  touchChat(friendId, msg, true); emitConv(friendId);
  transmit(msg, replyTo?.id);
  return msg;
}
function transmit(msg, replyToId) {
  msg._failed = false; msg._pending = true;
  const ok = wsSend({ type: "chat", to: msg.receiverId, content: msg.content, attachmentUrl: msg.attachmentUrl, replyToMessageId: replyToId || msg.replyTo?.messageId || null, clientMessageId: msg.clientMessageId });
  if (!ok) return fail(msg, "Not connected. Reconnecting — tap retry to send.");
  clearTimeout(pendingAcks.get(msg.clientMessageId));
  pendingAcks.set(msg.clientMessageId, setTimeout(() => fail(msg, null), 12000));
}
function fail(msg, text) {
  if (!msg._pending) return;
  msg._pending = false; msg._failed = true;
  if (text) toast(text, "error");
  emitConv(msg.receiverId);
}
export function retry(msg) { transmit(msg); emitConv(msg.receiverId); }
export function discardFailed(msg) {
  const list = state.messages.get(msg.receiverId) || [];
  state.messages.set(msg.receiverId, list.filter((m) => m !== msg)); emitConv(msg.receiverId);
}

export async function editMessage(msg, content) {
  const dto = await api(`/chat/messages/${msg.id}`, { method: "PATCH", body: { content } });
  Object.assign(msg, dto); emitConv(msg.receiverId === state.me.id ? msg.senderId : msg.receiverId);
}
export async function deleteForEveryone(msg) {
  const dto = await api(`/chat/messages/${msg.id}`, { method: "DELETE" });
  Object.assign(msg, dto); const other = msg.receiverId;
  const c = state.chats.get(other); if (c && c.latestMessageAt === msg.sentAt) c.latestMessage = "This message was deleted";
  emitChats(); emitConv(other);
}
export async function deleteForMe(msg, friendId) {
  await api(`/chat/messages/${msg.id}/mine`, { method: "DELETE" });
  state.messages.set(friendId, (state.messages.get(friendId) || []).filter((m) => m.id !== msg.id)); emitConv(friendId);
}
export async function react(msg, friendId, emoji) {
  const mine = msg.reactions?.find((r) => r.reactedByMe);
  const dto = mine && mine.reactionType === emoji
    ? await api(`/chat/messages/${msg.id}/reactions`, { method: "DELETE" })
    : await api(`/chat/messages/${msg.id}/reactions`, { method: "POST", body: { reactionType: emoji } });
  msg.reactions = dto.reactions; emitConv(friendId);
}

// ---- Friends ---------------------------------------------------------------------------------
export async function sendFriendRequest(identifier, viaId) {
  const r = await api("/friends/request", { method: "POST", body: { identifier, viaId } });
  await loadChats(); return r;
}
export async function respondRequest(requestId, accept) {
  await api(`/friends/requests/${requestId}/${accept ? "accept" : "decline"}`, { method: "POST" });
  await Promise.all([loadRequests(), loadChats()]);
}
export async function removeFriend(id) { await api(`/friends/${id}`, { method: "DELETE" }); state.chats.delete(id); state.messages.delete(id); emitChats(); }
export async function blockUser(id) { await api(`/friends/${id}/block`, { method: "POST" }); state.chats.delete(id); emitChats(); }
export async function unblockUser(id) { await api(`/friends/${id}/block`, { method: "DELETE" }); await loadChats(); }
export const listBlocked = () => api("/friends/blocked");
export const reportUser = (body) => api("/reports", { method: "POST", body });
export const searchMessages = (q, friendId) => api(`/chat/search?q=${encodeURIComponent(q)}${friendId ? `&friendId=${friendId}` : ""}`);

// ---- Typing -----------------------------------------------------------------------------------
let lastTypingSent = 0;
export function notifyTyping(friendId, isTyping) {
  const now = Date.now();
  if (isTyping && now - lastTypingSent < 2500) return;
  lastTypingSent = isTyping ? now : 0;
  wsSend({ type: "typing", to: friendId, isTyping });
}

// ---- Realtime frames ----------------------------------------------------------------------
export function handleFrame(f) {
  switch (f.type) {
    case "chat": {
      const friendId = f.senderId;
      const incoming = { id: f.id, senderId: f.senderId, receiverId: f.receiverId, content: f.content, attachmentUrl: f.attachmentUrl || null, sentAt: f.sentAt, deliveredAt: null, readAt: null, editedAt: null, deleted: false, reactions: [], replyTo: f.replyTo || null };
      if (!state.chats.has(friendId)) { loadChats().then(() => { /* new friend appears */ }); }
      const list = state.messages.get(friendId);
      if (list && !list.some((m) => m.id === incoming.id)) list.push(incoming);
      touchChat(friendId, incoming, false);
      clearTypingFor(friendId);
      emitConv(friendId);
      if (state.activeChatId === friendId && !document.hidden) markRead(friendId, true);
      else notifyIncoming(friendId, incoming);
      break;
    }
    case "ack": {
      clearTimeout(pendingAcks.get(f.clientMessageId)); pendingAcks.delete(f.clientMessageId);
      for (const [fid, list] of state.messages) {
        const m = list.find((x) => x.clientMessageId && x.clientMessageId === f.clientMessageId);
        if (m) { m.id = f.id; m._pending = false; m._failed = false; emitConv(fid); break; }
      }
      break;
    }
    case "delivered": {
      const list = state.messages.get(f.conversationWith);
      list?.forEach((m) => { if (f.messageIds?.includes(m.id)) m.deliveredAt = f.deliveredAt; });
      emitConv(f.conversationWith); break;
    }
    case "read": {
      const list = state.messages.get(f.conversationWith);
      list?.forEach((m) => { if (f.messageIds?.includes(m.id)) { m.readAt = f.readAt; m.deliveredAt = m.deliveredAt || f.readAt; } });
      emitConv(f.conversationWith); break;
    }
    case "message_deleted": {
      const m = findMsg(f.conversationWith, f.id);
      if (m) { m.deleted = true; m.content = "This message was deleted"; m.attachmentUrl = null; }
      const c = state.chats.get(f.conversationWith); if (c && m && c.latestMessageAt === m.sentAt) { c.latestMessage = m.content; emitChats(); }
      emitConv(f.conversationWith); break;
    }
    case "message_edited": {
      const m = findMsg(f.conversationWith, f.id);
      if (m) { m.content = f.content; m.editedAt = f.editedAt; }
      const c = state.chats.get(f.conversationWith); if (c && m && c.latestMessageAt === m.sentAt) { c.latestMessage = f.content; emitChats(); }
      emitConv(f.conversationWith); break;
    }
    case "reaction": {
      for (const [fid, list] of state.messages) { const m = list.find((x) => x.id === f.messageId); if (m) { m.reactions = f.reactions; emitConv(fid); break; } }
      break;
    }
    case "typing": {
      if (f.isTyping) {
        clearTimeout(state.typing.get(f.from));
        state.typing.set(f.from, setTimeout(() => clearTypingFor(f.from), 5000));
      } else clearTypingFor(f.from, true);
      bus.emit("typing", f.from); break;
    }
    case "presence": f.online ? state.online.add(f.userId) : state.online.delete(f.userId); bus.emit("presence"); break;
    case "presence_snapshot": state.online = new Set(f.online || []); bus.emit("presence"); break;
    case "error": toast(f.message || "Something went wrong", "error"); break;
    default:
      if (String(f.type).startsWith("call_") || f.type === "webrtc_offer" || f.type === "webrtc_answer" || f.type === "ice_candidate") calls.handleFrame(f);
  }
}
function clearTypingFor(id, silent) { clearTimeout(state.typing.get(id)); state.typing.delete(id); if (!silent) bus.emit("typing", id); }
function notifyIncoming(friendId, msg) {
  const c = state.chats.get(friendId);
  const who = c?.user.username || "New message";
  toast(`${who}: ${msg.content ? msg.content.slice(0, 80) : "Sent an attachment"}`);
}
export function syncPresenceReset() { state.online.clear(); bus.emit("presence"); }
document.addEventListener("visibilitychange", () => { if (!document.hidden && state.activeChatId) markRead(state.activeChatId, true); });
