// Secondary sections rendered in the main pane: Calls, Friends, Settings, Admin.
import { html, raw, $, $$, mount, esc, avatar, fmtListTime, fmtDuration, chatLink, appName } from "../core/util.js";
import { state, bus } from "../core/state.js";
import { api, upload, logout as apiLogout } from "../core/api.js";
import { icon, openModal, confirmDialog, toast, emptyState, spinnerBlock } from "../core/ui.js";
import { navigate } from "../router.js";
import * as chats from "../core/chats.js";
import * as calls from "../core/calls.js";
import { mainEl } from "./shell.js";
import { installState, promptInstall, showInstallHelp } from "../core/install.js";
import { phoneFieldHtml, readPhone } from "./phone-field.js";

const page = (inner) => mount(mainEl(), html`<div class="page-wrap"><div class="page">${inner}</div></div>`);
const nameOf = (id) => (id === state.me.id ? "You" : state.chats.get(id)?.user.username || "Unknown user");

// ---------------------------------------------------------------- Calls
export async function renderCalls() {
  page(html`<h1>Calls</h1><div id="calls-body">${spinnerBlock()}</div>`);
  const body = () => $("#calls-body");
  try {
    const rows = await calls.callHistory();
    if (!body()) return;
    if (!rows.length) return (body().innerHTML = emptyState("phone", "No calls yet", "Start a voice or video call from any conversation.").s);
    body().innerHTML = `<div class="card" style="padding:0">${rows.map((c) => {
      const out = c.callerId === state.me.id, other = out ? c.calleeId : c.callerId;
      const missed = !out && (c.status === "MISSED" || c.status === "REJECTED");
      const dur = c.answeredAt && c.endedAt ? fmtDuration((Date.parse(c.endedAt) - Date.parse(c.answeredAt)) / 1000) : "";
      const user = state.chats.get(other)?.user || { username: nameOf(other) };
      const statusText = c.status === "ENDED" ? (dur ? `Duration ${dur}` : "Ended") : c.status === "MISSED" ? "Missed" : c.status === "REJECTED" ? "Declined" : c.status[0] + c.status.slice(1).toLowerCase();
      return `<div class="row" style="cursor:default" data-other="${esc(other)}">${avatar(user, 44).s}<div class="meta"><div class="top"><span class="name ${missed ? "missed" : ""}">${esc(user.username)}</span><span class="time">${esc(fmtListTime(c.startedAt))}</span></div>
        <div class="preview"><span class="t">${out ? "Outgoing" : "Incoming"} ${c.callType === "VIDEO" ? "video" : "voice"} · ${esc(statusText)}</span></div></div>
        ${state.chats.has(other) ? `<button class="icon-btn" data-call="${esc(other)}" data-type="${esc(c.callType)}" aria-label="Call back">${c.callType === "VIDEO" ? icon("video").s : icon("phone").s}</button>` : ""}</div>`;
    }).join("")}</div>`;
    $$("[data-call]", body()).forEach((b) => b.addEventListener("click", () => { const id = b.dataset.call; calls.start(id, state.chats.get(id).user.username, b.dataset.type); }));
  } catch (e) { if (body()) body().innerHTML = `<div class="alert error">${esc(e.message)}</div>`; }
}

// ---------------------------------------------------------------- Friends
export async function renderFriends() {
  const draw = async (fromEvent = false) => {
    if (!$("#fr-root")) return;
    let friends = [], blocked = [];
    try { [friends, blocked] = await Promise.all([api("/friends"), chats.listBlocked(), fromEvent ? null : chats.loadRequests()]); } catch (e) { $("#fr-root").innerHTML = `<div class="alert error">${esc(e.message)}</div>`; return; }
    if (!$("#fr-root")) return;
    const req = state.requests;
    $("#fr-root").innerHTML = `
      <div class="card"><h3>Friend requests ${req.length ? `<span class="badge">${req.length}</span>` : ""}</h3>
        ${req.length ? req.map((r) => `<div class="row" style="cursor:default;padding-left:0;padding-right:0">${avatar(r.otherUser, 42).s}<div class="meta"><div class="name">${esc(r.otherUser.username)}</div><div class="small muted">wants to connect</div></div>
          <button class="btn sm" data-acc="${esc(r.requestId)}">Accept</button><button class="btn sm secondary" data-dec="${esc(r.requestId)}">Decline</button></div>`).join("") : '<p class="muted small">No pending requests.</p>'}</div>
      <div class="card"><h3>Your friends (${friends.length})</h3>
        ${friends.length ? friends.map((f) => `<div class="row" data-chat="${esc(f.otherUser.username)}">${avatar(f.otherUser, 42, state.online.has(f.otherUser.id)).s}<div class="meta"><div class="name">${esc(f.otherUser.username)}</div></div></div>`).join("") : '<p class="muted small">You haven\'t added anyone yet. Use the + button in Chats, or share your chat link.</p>'}</div>
      <div class="card"><h3>Blocked users</h3>${blocked.length ? blocked.map((b) => `<div class="row" style="cursor:default;padding-left:0;padding-right:0">${avatar(b, 42).s}<div class="meta"><div class="name">${esc(b.username)}</div></div><button class="btn sm secondary" data-unblock="${esc(b.id)}">Unblock</button></div>`).join("") : '<p class="muted small">You haven\'t blocked anyone.</p>'}</div>`;
    const root = $("#fr-root");
    $$("[data-acc]", root).forEach((b) => b.addEventListener("click", async () => { b.disabled = true; try { await chats.respondRequest(b.dataset.acc, true); toast("Friend added", "success"); draw(); } catch (e) { toast(e.message, "error"); b.disabled = false; } }));
    $$("[data-dec]", root).forEach((b) => b.addEventListener("click", async () => { b.disabled = true; try { await chats.respondRequest(b.dataset.dec, false); draw(); } catch (e) { toast(e.message, "error"); b.disabled = false; } }));
    $$("[data-unblock]", root).forEach((b) => b.addEventListener("click", async () => { b.disabled = true; try { await chats.unblockUser(b.dataset.unblock); toast("User unblocked", "success"); draw(); } catch (e) { toast(e.message, "error"); b.disabled = false; } }));
    $$("[data-chat]", root).forEach((r) => r.addEventListener("click", () => navigate(`/app/c/${encodeURIComponent(r.dataset.chat)}`)));
  };
  page(html`<h1>Friends</h1><div id="fr-root">${spinnerBlock()}</div>`);
  // Live updates: a push (new request / acceptance) or a refresh redraws this page without a manual reload.
  let t = null; const redraw = () => { clearTimeout(t); t = setTimeout(() => { if (!$("#fr-root")) { offReq(); offChats(); return; } draw(true); }, 200); };
  const offReq = bus.on("requests", redraw), offChats = bus.on("chats", redraw);
  draw();
}

// ---------------------------------------------------------------- Settings
export function renderSettings({ onLogout }) {
  const me = state.me, link = chatLink(me.username);
  const theme = document.documentElement.getAttribute("data-theme") || "auto";
  page(html`<h1>Settings</h1>
    <div class="card"><div class="profile-head"><div id="avatar-slot">${avatar(me, 72)}</div><div><h3 style="margin:0">${me.username}</h3><div class="muted small">${me.email}</div>
      <button class="btn secondary sm" id="pic-btn" style="margin-top:8px">Change photo</button><input type="file" id="pic" accept="image/png,image/jpeg,image/webp,image/gif" class="hidden"></div></div>
      <div id="prof-alert"></div>
      <div class="field"><label for="bio">Bio <span class="muted">(shown on your chat link, max 160)</span></label><textarea class="textarea" id="bio" rows="2" maxlength="160">${me.bio || ""}</textarea></div>
      <div class="field"><label for="phone-num">Phone <span class="muted">(optional, never shown publicly)</span></label>${phoneFieldHtml("phone", me.phoneNumber || "")}<span class="small muted">Used to recover your account by SMS or WhatsApp. For WhatsApp recovery use your WhatsApp number.</span></div>
      <button class="btn" id="save-prof">Save profile</button></div>

    <div class="card"><h3>Your chat link</h3><p class="muted small">Anyone with this link can view your public profile and, if signed in, start a chat with you.</p>
      <div class="copy-row"><input class="input" readonly id="link-val" value="${link}" aria-label="Your chat link"><button class="btn" id="copy-link">${icon("copy")} Copy</button>
      ${navigator.share ? html`<button class="btn secondary" id="share-link">Share</button>` : ""}</div>
      <div class="switch"><div><b>Chat link enabled</b><div class="small muted">Turn off to hide your profile from people who aren't already your friends.</div></div><input type="checkbox" id="link-on" style="width:20px;height:20px;accent-color:var(--brand)" ${me.chatLinkEnabled ? raw("checked") : ""}></div></div>

    <div class="card"><h3>Privacy</h3>
      <div class="switch"><div><b>Require approval for friend requests</b><div class="small muted">Off means anyone who finds you can add you instantly.</div></div><input type="checkbox" id="approval" style="width:20px;height:20px;accent-color:var(--brand)" ${me.approvalRequired ? raw("checked") : ""}></div>
      <div class="switch" style="border:0"><div><b>Only add me by Public ID</b><div class="small muted">Your Public ID: <code>${me.publicId}</code></div></div><input type="checkbox" id="byid" style="width:20px;height:20px;accent-color:var(--brand)" ${me.addByIdOnly ? raw("checked") : ""}></div></div>

    <div class="card"><h3>Appearance</h3><div class="field" style="margin:0"><label for="theme">Theme</label><select class="select" id="theme"><option value="auto">Match my device</option><option value="light">Light</option><option value="dark">Dark</option></select></div></div>

    <div class="card"><h3>Password</h3><div id="pw-alert"></div>
      <div class="field"><label for="pw-cur">Current password</label><input class="input" id="pw-cur" type="password" autocomplete="current-password"></div>
      <div class="field"><label for="pw-new">New password</label><input class="input" id="pw-new" type="password" autocomplete="new-password"></div>
      <button class="btn secondary" id="pw-save">Change password</button><p class="small muted">You'll be signed out on all devices after changing it.</p></div>

    <div class="card" id="app-card"><h3>Get the app</h3><p class="small muted">Install ${appName()} on this device — it opens full-screen with its own icon, like a normal app.</p><div id="app-card-body"></div></div>

    <div class="card"><h3>Legal</h3><p class="small muted">You accepted Terms version <b>${me.termsVersion || "—"}</b> and Privacy Policy version <b>${me.privacyVersion || "—"}</b>.</p><a href="/terms" data-link>Terms and Conditions</a> · <a href="/privacy" data-link>Privacy Policy</a></div>

    <div class="card"><button class="btn secondary" id="logout">${icon("logout")} Sign out</button></div>
    <div class="card danger-zone"><h3>Delete account</h3><p class="small muted">Your profile is removed and you are signed out everywhere. This cannot be undone.</p><button class="btn danger" id="del">Delete my account</button></div>`);
  const paintInstall = () => {
    const b = $("#app-card-body"); if (!b) return; const stt = installState();
    b.innerHTML = stt === "installed" ? '<div class="alert success" style="margin:0">✅ Installed on this device.</div>' : stt === "available" ? '<button class="btn" id="do-install">Install app</button>' : '<button class="btn secondary" id="how-install">How to install</button>';
    $("#do-install")?.addEventListener("click", () => promptInstall()); $("#how-install")?.addEventListener("click", showInstallHelp);
  };
  paintInstall(); bus.on("install:changed", paintInstall);
  $("#theme").value = theme;
  $("#theme").addEventListener("change", (e) => { const v = e.target.value; try { v === "auto" ? localStorage.removeItem("schat_theme") : localStorage.setItem("schat_theme", v); } catch {} v === "auto" ? document.documentElement.removeAttribute("data-theme") : document.documentElement.setAttribute("data-theme", v); });
  const patch = async (body, okMsg) => { try { state.me = await api("/users/me", { method: "PATCH", body }); if (okMsg) toast(okMsg, "success"); return true; } catch (e) { toast(e.message, "error"); return false; } };
  $("#save-prof").addEventListener("click", async (e) => { const b = e.currentTarget; b.disabled = true; const ph = readPhone(document, "phone"); if (ph.error) { toast(ph.error, "error"); b.disabled = false; return; }
    await patch({ bio: $("#bio").value, phoneNumber: ph.value }, "Profile saved"); b.disabled = false; });
  $("#link-on").addEventListener("change", async (e) => { if (!(await patch({ chatLinkEnabled: e.target.checked }, e.target.checked ? "Chat link enabled" : "Chat link disabled"))) e.target.checked = !e.target.checked; });
  $("#approval").addEventListener("change", async (e) => { if (!(await patch({ approvalRequired: e.target.checked }, "Saved"))) e.target.checked = !e.target.checked; });
  $("#byid").addEventListener("change", async (e) => { if (!(await patch({ addByIdOnly: e.target.checked }, "Saved"))) e.target.checked = !e.target.checked; });
  $("#copy-link").addEventListener("click", () => navigator.clipboard.writeText(link).then(() => toast("Link copied", "success"), () => { $("#link-val").select(); toast("Press Ctrl/Cmd+C to copy", "error"); }));
  $("#share-link")?.addEventListener("click", () => navigator.share({ title: appName(), text: `Chat with me on ${appName()}`, url: link }).catch(() => {}));
  $("#pic-btn").addEventListener("click", () => $("#pic").click());
  $("#pic").addEventListener("change", async (e) => {
    const f = e.target.files[0]; e.target.value = ""; if (!f) return;
    if (f.size > 5 * 1024 * 1024) return toast("Choose an image under 5 MB.", "error");
    try { state.me = await upload("/users/me/profile-picture", f); $("#avatar-slot").innerHTML = avatar(state.me, 72).s; toast("Photo updated", "success"); } catch (err) { toast(err.message, "error"); }
  });
  $("#pw-save").addEventListener("click", async (e) => {
    const b = e.currentTarget, cur = $("#pw-cur").value, nw = $("#pw-new").value; $("#pw-alert").innerHTML = "";
    if (!cur || !nw) return ($("#pw-alert").innerHTML = '<div class="alert error">Fill in both fields.</div>');
    b.disabled = true;
    try { await api("/account/password", { method: "POST", body: { currentPassword: cur, newPassword: nw } }); toast("Password changed. Please sign in again.", "success"); await onLogout(); }
    catch (err) { $("#pw-alert").innerHTML = `<div class="alert error">${esc(err.message)}</div>`; b.disabled = false; }
  });
  $("#logout").addEventListener("click", () => onLogout());
  $("#del").addEventListener("click", () => {
    const m = openModal({ title: "Delete your account?", body: html`<p>This will remove your profile and sign you out everywhere. Type <b>DELETE</b> to confirm.</p><input class="input" id="del-confirm" autocomplete="off" aria-label="Type DELETE to confirm">`, footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn danger" id="del-go" disabled>Delete account</button>` });
    $("#del-confirm", m.el).addEventListener("input", (e) => ($("#del-go", m.el).disabled = e.target.value.trim() !== "DELETE"));
    $("#del-go", m.el).addEventListener("click", async () => { try { await api("/users/me", { method: "DELETE" }); m.close(); await onLogout(); toast("Your account was deleted."); } catch (e) { toast(e.message, "error"); } });
  });
}

// ---------------------------------------------------------------- Admin (ADMIN / SUPERADMIN)
export async function renderAdmin() {
  if (!state.me || state.me.role === "USER") return navigate("/app", { replace: true });
  page(html`<h1>Admin</h1><div id="adm">${spinnerBlock()}</div>`);
  const load = async (status = "OPEN") => {
    try {
      const [stats, reports] = await Promise.all([api("/admin/stats"), api(`/admin/reports?status=${status}`)]);
      if (!$("#adm")) return;
      $("#adm").innerHTML = `<div class="stats"><div class="stat"><b>${stats.totalUsers}</b>Users</div><div class="stat"><b>${stats.newUsersLast7Days}</b>New (7 days)</div><div class="stat"><b>${stats.totalMessages}</b>Messages</div><div class="stat"><b>${stats.openReports}</b>Open reports</div></div>
        <div class="tabs" style="padding:0 0 12px">${["OPEN", "RESOLVED", "DISMISSED"].map((s) => `<button class="tab ${s === status ? "active" : ""}" data-s="${s}">${s[0] + s.slice(1).toLowerCase()}</button>`).join("")}</div>
        ${reports.length ? reports.map((r) => `<div class="card"><div class="top" style="display:flex;justify-content:space-between;gap:8px"><b>${esc(r.reportedUsername)}</b><span class="pill">${esc(r.reason)}</span></div>
          <div class="small muted">Reported by ${esc(r.reporterUsername)} · ${esc(fmtListTime(r.createdAt))}</div>
          ${r.details ? `<p>${esc(r.details)}</p>` : ""}${r.messageExcerpt ? `<blockquote style="margin:8px 0;padding:8px 12px;border-left:3px solid var(--border);color:var(--text-2)">${esc(r.messageExcerpt)}</blockquote>` : ""}
          ${r.status === "OPEN" ? `<div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn sm" data-res="${esc(r.id)}" data-a="RESOLVED">Mark resolved</button><button class="btn sm secondary" data-res="${esc(r.id)}" data-a="DISMISSED">Dismiss</button><button class="btn sm danger" data-sus="${esc(r.reportedUserId)}">Suspend user</button></div>` : `<span class="pill">${esc(r.status)}</span>`}</div>`).join("") : emptyState("shield", "Nothing here", `No ${status.toLowerCase()} reports.`).s}`;
      $$(".tab", $("#adm")).forEach((t) => t.addEventListener("click", () => load(t.dataset.s)));
      $$("[data-res]", $("#adm")).forEach((b) => b.addEventListener("click", async () => { b.disabled = true; try { await api(`/admin/reports/${b.dataset.res}/resolve`, { method: "POST", body: { action: b.dataset.a } }); load(status); } catch (e) { toast(e.message, "error"); b.disabled = false; } }));
      $$("[data-sus]", $("#adm")).forEach((b) => b.addEventListener("click", async () => { if (!(await confirmDialog({ title: "Suspend this user?", message: "They will be signed out and unable to sign in until restored.", confirmText: "Suspend", danger: true }))) return; try { await api(`/admin/users/${b.dataset.sus}/suspend`, { method: "POST" }); toast("User suspended", "success"); } catch (e) { toast(e.message, "error"); } }));
    } catch (e) { if ($("#adm")) $("#adm").innerHTML = `<div class="alert error">${esc(e.message)}</div>`; }
  };
  load();
}
