// Tiny pub/sub store. Views subscribe to the events they care about; nothing polls.
const listeners = new Map();
export const bus = {
  on(evt, fn) { (listeners.get(evt) || listeners.set(evt, new Set()).get(evt)).add(fn); return () => listeners.get(evt)?.delete(fn); },
  emit(evt, data) { listeners.get(evt)?.forEach((fn) => { try { fn(data); } catch (e) { console.error(`[bus:${evt}]`, e); } }); },
};

export const state = {
  me: null,                 // ProfileDto
  chats: new Map(),         // userId -> { user, latestMessage, latestAttachmentUrl, latestMessageAt, latestSenderId, unreadCount }
  online: new Set(),        // userIds of friends currently online
  typing: new Map(),        // userId -> timeout handle
  messages: new Map(),      // userId -> ChatMessageDto[] (ascending)
  hasMore: new Map(),       // userId -> boolean
  activeChatId: null,
  requests: [],             // incoming friend requests
  legal: null,              // { termsVersion, privacyVersion }
  wsConnected: false,
  ai: { available: true, messages: null, busy: false, local: [] },  // STech AI conversation (server keeps the history)
};

export function totalUnread() { let n = 0; state.chats.forEach((c) => (n += c.unreadCount || 0)); return n; }
