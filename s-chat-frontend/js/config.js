// Single source of truth for backend endpoints.
// TURN values are optional now; add a TURN relay before production-wide calling.
window.S_CHAT_CONFIG = {
  API_BASE_URL: "https://s-chat-u8fs.onrender.com/api",
  WS_URL: "wss://s-chat-u8fs.onrender.com/ws",
  TURN_URL: "",
  TURN_USERNAME: "",
  TURN_CREDENTIAL: ""
};
