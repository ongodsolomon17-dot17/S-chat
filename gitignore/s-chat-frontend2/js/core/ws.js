import { api, Session, refreshSession } from "./api.js";
import { bus, state } from "./state.js";

let socket = null, intentionalClose = false, connecting = false, retryDelay = 1000, retryTimer = null, pingTimer = null;

function tokenExpiringSoon(token, leeway = 60) {
  try {
    const p = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return !p.exp || Date.now() >= (p.exp - leeway) * 1000;
  } catch { return true; }
}

export async function connect() {
  intentionalClose = false;
  if (connecting || (socket && socket.readyState <= WebSocket.OPEN)) return;
  if (!Session.token()) return;
  connecting = true;
  try {
    if (tokenExpiringSoon(Session.token())) await refreshSession();
    if (!Session.token() || intentionalClose) { connecting = false; return; }
    // One-time ticket: the access token never appears in the WebSocket URL.
    const { ticket } = await api("/auth/ws-ticket", { method: "POST", headers: { "X-S-Chat-Client": "web" } });
    if (!ticket || intentionalClose) { connecting = false; return; }
    socket = new WebSocket(window.S_CHAT_CONFIG.WS_URL + "?ticket=" + encodeURIComponent(ticket));
    socket.onopen = () => {
      connecting = false; retryDelay = 1000; state.wsConnected = true; bus.emit("ws:status", true);
      clearInterval(pingTimer);
      // Keeps the connection from being closed by proxies/hosts that drop idle sockets.
      pingTimer = setInterval(() => { if (socket?.readyState === WebSocket.OPEN) socket.send('{"type":"ping"}'); }, 25000);
    };
    socket.onmessage = (ev) => {
      let data; try { data = JSON.parse(ev.data); } catch { return; }
      if (data.type === "pong") return;
      bus.emit("ws:frame", data);
    };
    socket.onclose = () => {
      connecting = false; socket = null; clearInterval(pingTimer);
      state.wsConnected = false; state.online.clear(); bus.emit("ws:status", false);
      if (intentionalClose) return;
      clearTimeout(retryTimer); retryTimer = setTimeout(connect, retryDelay);
      retryDelay = Math.min(retryDelay * 2, 15000);
    };
    socket.onerror = () => { try { socket?.close(); } catch {} };
  } catch {
    connecting = false;
    if (!intentionalClose) { clearTimeout(retryTimer); retryTimer = setTimeout(connect, retryDelay); retryDelay = Math.min(retryDelay * 2, 15000); }
  }
}

export function disconnect() {
  intentionalClose = true; clearTimeout(retryTimer); clearInterval(pingTimer);
  try { socket?.close(); } catch {}
  socket = null; state.wsConnected = false;
}

/** Returns true if the frame was handed to an open socket. */
export function send(frame) {
  if (!socket || socket.readyState !== WebSocket.OPEN) { connect(); return false; }
  socket.send(JSON.stringify(frame));
  return true;
}
