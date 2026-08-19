// Thin WebSocket client for 1:1 chat with authenticated reconnects.
window.SChatWS = (function () {
  let socket = null;
  let reconnectDelay = 1000;
  let handlers = [];
  let intentionalClose = false;
  let reconnectTimer = null;
  let connecting = false;

  // Decodes the JWT payload client-side (no network call) so we can tell whether
  // the access token is actually close to expiring, instead of unconditionally
  // hitting /api/auth/refresh on every single connect/reconnect attempt. That
  // unconditional refresh added a full extra request — and its round-trip latency
  // — before the socket could even open, on every page load and every reconnect.
  function isTokenExpiringSoon(token, leewaySeconds) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
      if (!payload.exp) return true; // can't tell — be safe and refresh
      return Date.now() >= (payload.exp * 1000) - leewaySeconds * 1000;
    } catch {
      return true; // malformed/unreadable token — let the normal refresh flow handle it
    }
  }

  async function connect() {
    if (intentionalClose || connecting) return;
    const token = SChat.Auth.getAccessToken();
    if (!token) return;
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;

    connecting = true;
    try {
      // Only proactively refresh when the current token is expired or about to
      // expire (60s leeway). A still-valid token connects straight away.
      if (isTokenExpiringSoon(token, 60)) {
        await SChat.refreshAccessToken();
      }
      const currentToken = SChat.Auth.getAccessToken();
      if (!currentToken || intentionalClose) return;
      socket = new WebSocket(window.S_CHAT_CONFIG.WS_URL + "?token=" + encodeURIComponent(currentToken));

      socket.onopen = () => { reconnectDelay = 1000; connecting = false; };
      socket.onmessage = (event) => {
        let data;
        try { data = JSON.parse(event.data); } catch { return; }
        handlers.slice().forEach((h) => h(data));
      };
      socket.onclose = () => {
        connecting = false;
        socket = null;
        if (intentionalClose) return;
        clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(connect, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 15000);
      };
      socket.onerror = () => { if (socket) socket.close(); };
    } catch {
      connecting = false;
      clearTimeout(reconnectTimer);
      reconnectTimer = setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 15000);
    }
  }

  function disconnect() {
    intentionalClose = true;
    clearTimeout(reconnectTimer);
    if (socket) socket.close();
    socket = null;
  }

  function onMessage(handler) {
    if (typeof handler !== "function") return () => {};
    handlers.push(handler);
    return () => { handlers = handlers.filter((h) => h !== handler); };
  }

  function send(toUserId, content, attachmentUrl, replyToMessageId) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      SChat.showToast("Not connected. Reconnecting…", "error");
      connect();
      return false;
    }
    socket.send(JSON.stringify({
      type: "chat",
      to: toUserId,
      content,
      attachmentUrl: attachmentUrl || null,
      replyToMessageId: replyToMessageId || null
    }));
    return true;
  }

  return { connect, disconnect, onMessage, send };
})();
