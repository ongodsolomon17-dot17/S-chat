// ============================================================================
//  PROJECT OWNER CONFIGURATION  (this file is public — never put secrets here)
//  See docs/OWNER_CONFIGURATION.md for what each value does and where to get it.
// ============================================================================
window.S_CHAT_CONFIG = {
  // --- Backend (Render) ------------------------------------------------------
  API_BASE_URL: "https://s-chat-u8fs.onrender.com/api",
  WS_URL: "wss://s-chat-u8fs.onrender.com/ws",

  // --- Branding --------------------------------------------------------------
  APP_NAME: "S-Chat",
  TAGLINE: "Fast messaging wep app",
  // Public URL of THIS frontend. Leave "" to use whatever origin the app is served from.
  PUBLIC_BASE_URL: "",

  // --- Legal / contact (shown in Terms and Privacy Policy) -------------------
  // Leave a value as "" and the documents show a clearly marked placeholder.
  COMPANY_NAME: "Stech",          // legal name of the person/company operating the service
  SUPPORT_EMAIL: "stechtechnologies9@gmail.com",         // e.g. support@yourdomain.com
  PRIVACY_EMAIL: "null",         // e.g. privacy@yourdomain.com (can equal SUPPORT_EMAIL)
  COMPANY_ADDRESS: "null",       // postal address, if you want one published
  GOVERNING_LAW: "null",         // e.g. "the laws of the Federal Republic of Nigeria"
  MINIMUM_AGE: "13",           // e.g. "13" or "16" — confirm what applies where you operate

  // --- Behaviour ------------------------------------------------------------
  EDIT_WINDOW_MINUTES: 15    // must match MESSAGE_EDIT_WINDOW_MIN on the backend
};
