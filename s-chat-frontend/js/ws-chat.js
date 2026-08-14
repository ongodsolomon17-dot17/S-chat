// Thin WebSocket client for 1:1 chat with authenticated reconnects.
window.SChatWS = (function () {
  let socket = null;
  let reconnectDelay = 1000;
  let handlers = [];
  let intentionalClose = false;
  let reconnectTimer = null;
  let connecting = false;

  async function connect() {
    if (intentionalClose || connecting) return;
    const token = SChat.Auth.getAccessToken();
    if (!token) return;
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;

    connecting = true;
    try {
      const fresh = await SChat.refreshAccessToken();
      if (fresh) {
        // Refreshing is harmless when the current access token is still valid and
        // prevents reconnect loops with an expired token after a long idle period.
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

  function send(toUserId, content, attachmentUrl) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      SChat.showToast("Not connected. Reconnecting…", "error");
      connect();
      return false;
    }
    socket.send(JSON.stringify({ type: "chat", to: toUserId, content, attachmentUrl: attachmentUrl || null }));
    return true;
  }

  return { connect, disconnect, onMessage, send };
})();
