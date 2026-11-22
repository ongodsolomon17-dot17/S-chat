import { route, navigate, resolve, notFound } from "./router.js";
import { api, Session, refreshSession, logout as apiLogout } from "./core/api.js";
import { state, bus } from "./core/state.js";
import { connect, disconnect } from "./core/ws.js";
import * as chats from "./core/chats.js";
import { toast, openModal } from "./core/ui.js";
import { html, raw, $, mount, appName } from "./core/util.js";
import { renderLogin, renderSignup } from "./views/auth.js";
import { renderLegal } from "./views/legal.js";
import { renderChatLink } from "./views/profile-link.js";
import { renderShell, resetShell } from "./views/shell.js";
import { renderConversation, leaveConversation } from "./views/conversation.js";
import { renderAi } from "./views/ai-conversation.js";
import { renderForgot } from "./views/recovery.js";
import { renderCalls, renderFriends, renderSettings, renderAdmin } from "./views/pages.js";

document.title = appName();
let sessionReady = null; // memoised "are we signed in?" check

async function ensureSession() {
  if (state.me) return true;
  if (!Session.token() && !(await refreshSession())) return false;
  try { state.me = await api("/users/me"); } catch { return false; }
  startRealtime();
  return true;
}
let started = false, statusTimer = null;

/** Shows a banner while the backend is serving from its read-only backup database. */
async function checkStatus() {
  try {
    const s = await api("/public/status", { auth: false });
    let b = $("#sys-banner");
    if (s.database === "fallback") {
      if (!b) { b = document.createElement("div"); b.id = "sys-banner"; b.setAttribute("role", "status"); document.body.prepend(b); }
      b.textContent = "We're running on a backup system. You can read your messages, but sending and changes are paused for a moment.";
      document.body.classList.add("has-banner");
    } else if (b) { b.remove(); document.body.classList.remove("has-banner"); }
  } catch { /* status is informational only */ }
}
async function startRealtime() {
  if (started) return; started = true;
  checkStatus(); clearInterval(statusTimer); statusTimer = setInterval(checkStatus, 45000);
  connect();
  chats.loadChats().catch((e) => toast(e.message, "error"));
  chats.loadRequests().catch(() => {});
  try { state.legal = await api("/public/legal", { auth: false }); } catch { /* optional */ }
  if (state.legal && state.me.termsVersion !== state.legal.termsVersion) promptReaccept();
}
function promptReaccept() {
  import("./views/legal-content.js").then(({ termsHtml }) => {
    let ok = false;
    const m = openModal({ title: "We've updated our Terms", body: html`<p>Please review and accept the latest Terms and Privacy Policy to keep using ${appName()}.</p><details><summary>Read the Terms</summary>${raw(termsHtml())}</details><p class="small"><a href="/privacy" target="_blank" rel="noopener">Privacy Policy</a></p><label class="check"><input type="checkbox" id="ra-box"><span>I agree to the updated Terms and Privacy Policy.</span></label>`, footer: html`<button class="btn secondary" id="ra-out">Sign out</button><button class="btn" id="ra-go" disabled>Accept &amp; continue</button>`, onClose: () => { if (!ok) setTimeout(promptReaccept, 0); } });
    $(".modal-head [data-close]", m.el)?.remove();
    $("#ra-box", m.el).addEventListener("change", (e) => ($("#ra-go", m.el).disabled = !e.target.checked));
    $("#ra-out", m.el).addEventListener("click", () => { ok = true; m.close(); doLogout(); });
    $("#ra-go", m.el).addEventListener("click", async () => { try { await api("/account/accept-terms", { method: "POST", body: { accepted: true, termsVersion: state.legal.termsVersion } }); state.me = await api("/users/me"); ok = true; m.close(); toast("Thanks!", "success"); } catch (e) { toast(e.message, "error"); } });
  });
}

async function doLogout() {
  disconnect(); clearInterval(statusTimer); await apiLogout(); started = false; resetShell(); leaveConversation();
  state.chats.clear(); state.messages.clear(); state.requests = []; state.online.clear(); state.activeChatId = null;
  navigate("/login", { replace: true });
}
async function afterAuth(next) {
  state.me = await api("/users/me"); started = false; startRealtime();
  navigate(next && next.startsWith("/") && !next.startsWith("//") ? next : "/app", { replace: true });
}
const guard = async () => { if (await ensureSession()) return true; navigate("/login?next=" + encodeURIComponent(location.pathname), { replace: true }); return false; };
const qs = () => new URLSearchParams(location.search);

route("/", async () => navigate((await ensureSession()) ? "/app" : "/login", { replace: true }));
route("/login", async () => { if (await ensureSession()) return navigate("/app", { replace: true }); renderLogin({ onDone: afterAuth, next: qs().get("next") }); });
route("/signup", async () => { if (await ensureSession()) return navigate("/app", { replace: true }); renderSignup({ onDone: () => afterAuth("/app") }); });
route("/terms", async () => { await ensureSession(); resetIfNeeded(); renderLegal("terms"); });
route("/privacy", async () => { await ensureSession(); resetIfNeeded(); renderLegal("privacy"); });
route("/chat/:username", async (p) => { const authed = await ensureSession(); resetIfNeeded(); renderChatLink(p, authed); });
route("/app", async () => { if (!(await guard())) return; leaveConversation(); renderShell("chats"); mount($("#main-pane"), html`<div class="no-chat empty"><div class="art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg></div><h3>Select a conversation</h3><p>Choose a chat from the list, or share your chat link from Settings.</p></div>`); document.title = appName(); });
route("/app/c/:username", async (p) => { if (!(await guard())) return; renderShell("chats", { conv: true }); await renderConversation(p.username); });
route("/app/ai", async () => { if (!(await guard())) return; leaveConversation(); renderShell("chats", { conv: true }); await renderAi(); });
route("/forgot", async () => { if (await ensureSession()) return navigate("/app", { replace: true }); renderForgot(); });
route("/app/calls", async () => { if (!(await guard())) return; leaveConversation(); renderShell("calls"); renderCalls(); });
route("/app/friends", async () => { if (!(await guard())) return; leaveConversation(); renderShell("friends"); renderFriends(); });
route("/app/settings", async () => { if (!(await guard())) return; leaveConversation(); renderShell("settings"); renderSettings({ onLogout: doLogout }); });
route("/app/admin", async () => { if (!(await guard())) return; leaveConversation(); renderShell("admin"); renderAdmin(); });
notFound(async () => { mount($("#app"), html`<div class="splash"><h1>Page not found</h1><a class="btn" href="/" data-link>Go home</a></div>`); });

function resetIfNeeded() { leaveConversation(); resetShell(); }

bus.on("ws:frame", (f) => chats.handleFrame(f));
bus.on("auth:expired", () => { if (state.me) doLogout(); });
window.addEventListener("online", () => state.me && connect());

mount($("#app"), html`<div class="splash"><span class="spinner"></span></div>`);
resolve();
