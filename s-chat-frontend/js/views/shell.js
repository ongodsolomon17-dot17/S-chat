// App shell: nav rail + chat list pane + main pane. Built once per session, then the main pane swaps per route.
import { html, raw, $, $$, mount, esc, avatar, fmtListTime, debounce, appName, isImage, isVideo, isAudio, stripMd } from "../core/util.js";
import { state, bus, totalUnread } from "../core/state.js";
import { icon, openModal, toast, emptyState } from "../core/ui.js";
import { navigate } from "../router.js";
import * as chats from "../core/chats.js";
import { unseenCount } from "../core/status.js";
import { phoneFieldHtml, readPhone } from "./phone-field.js";

let built = false, filter = "all", query = "", msgResults = null, searching = false;
const cleanups = [];
const SECTIONS = [["chats", "/app", "chat", "Chats"], ["status", "/app/status", "status", "Status"], ["calls", "/app/calls", "phone", "Calls"], ["friends", "/app/friends", "users", "Friends"], ["settings", "/app/settings", "settings", "Settings"]];

export function mainEl() { return $("#main-pane"); }
export function resetShell() { cleanups.splice(0).forEach((f) => f()); built = false; filter = "all"; query = ""; msgResults = null; }

export function renderShell(section, { conv = false } = {}) {
  if (!built) {
    const items = [...SECTIONS, ...(state.me?.role && state.me.role !== "USER" ? [["admin", "/app/admin", "shield", "Admin"]] : [])];
    mount($("#app"), html`<div class="shell" id="shell">
      <nav class="rail" aria-label="Main"><img class="logo" src="/assets/logo-128.png" alt="${appName()}">
        ${items.map(([id, href, ic, label]) => html`<a class="nav" data-nav="${id}" href="${href}" data-link aria-label="${label}" title="${label}">${icon(ic)}<span class="badge hidden" data-badge="${id}"></span></a>`)}
        <span class="spacer"></span></nav>
      <aside class="list-pane" id="list-pane" aria-label="Conversations"></aside>
      <section class="main-pane" id="main-pane"></section></div>`);
    built = true;
    cleanups.push(bus.on("chats", () => { renderList(); renderBadges(); }), bus.on("presence", renderList), bus.on("typing", renderList), bus.on("requests", renderBadges), bus.on("status", renderBadges));
    cleanups.push(bus.on("ws:status", (ok) => { const b = $("#conn-banner"); if (b) b.classList.toggle("hidden", ok); }));
    buildListPane();
  }
  const shell = $("#shell");
  shell.dataset.section = section;
  shell.dataset.pane = section === "chats" && !conv ? "list" : "main";
  shell.dataset.conv = conv ? "1" : "0";
  $$(".rail .nav").forEach((a) => a.classList.toggle("active", a.dataset.nav === section));
  renderBadges();
}

function renderBadges() {
  const set = (id, n) => { const b = $(`[data-badge="${id}"]`); if (b) { b.textContent = n > 99 ? "99+" : n; b.classList.toggle("hidden", !n); } };
  set("chats", totalUnread()); set("friends", state.requests.length); set("status", unseenCount());
}

function buildListPane() {
  mount($("#list-pane"), html`<div id="conn-banner" class="conn-banner hidden">Reconnecting…</div>
    <div class="pane-head"><h2>Chats</h2><button class="icon-btn" id="add-friend" aria-label="Add a friend" title="Add a friend">${icon("plus")}</button></div>
    <div class="searchbox">${icon("search")}<input class="input" id="chat-search" type="search" placeholder="Search chats and messages" aria-label="Search chats and messages" autocomplete="off"></div>
    <div class="tabs" role="tablist"><button class="tab active" data-f="all" role="tab">All</button><button class="tab" data-f="unread" role="tab">Unread</button></div>
    <div class="scroll" id="chat-rows"></div>`);
  $("#add-friend").addEventListener("click", openAddFriend);
  $$(".tab").forEach((t) => t.addEventListener("click", () => { filter = t.dataset.f; $$(".tab").forEach((x) => x.classList.toggle("active", x === t)); renderList(); }));
  const run = debounce(async () => {
    const q = query.trim();
    if (q.length < 2) { msgResults = null; return renderList(); }
    searching = true; renderList();
    try { msgResults = await chats.searchMessages(q); } catch (e) { msgResults = []; toast(e.message, "error"); }
    searching = false; renderList();
  }, 350);
  $("#chat-search").addEventListener("input", (e) => { query = e.target.value; renderList(); run(); });
  renderList();
}

function previewOf(c) {
  if (c.ai && state.ai.busy) return html`<span class="typing">typing…</span>`;
  if (c.ai && !c.latestMessage) return html`<span class="muted">Ask me anything ✨</span>`;
  if (state.typing.has(c.user.id)) return html`<span class="typing">typing…</span>`;
  const mine = c.latestSenderId === state.me.id ? "You: " : "";
  if (c.latestMessage) return html`${mine}${c.ai ? stripMd(c.latestMessage) : c.latestMessage}`;
  const u = c.latestAttachmentUrl;
  if (u) return html`${mine}${isImage(u) ? "📷 Photo" : isVideo(u) ? "🎥 Video" : isAudio(u) ? "🎤 Voice message" : "📎 Attachment"}`;
  return html`<span class="muted">Say hello 👋</span>`;
}
function rowHtml(c) {
  const online = state.online.has(c.user.id);
  const sortUnread = c.unreadCount > 0;
  return html`<button class="row ${state.activeChatId === c.user.id ? "active" : ""} ${sortUnread ? "unread" : ""}" data-id="${c.user.id}" data-username="${c.user.username}" ${c.ai ? raw('data-href="/app/ai"') : ""}>
    ${avatar(c.user, 46, online)}<div class="meta"><div class="top"><span class="name">${c.user.username}${c.ai ? raw(' <span class="pill ai-pill">AI</span>') : ""}</span><span class="time">${fmtListTime(c.latestMessageAt)}</span></div>
    <div class="preview"><span class="t">${previewOf(c)}</span>${sortUnread ? html`<span class="badge">${c.unreadCount}</span>` : ""}</div></div></button>`;
}
const mark = (text, q) => {
  const i = text.toLowerCase().indexOf(q.toLowerCase()); if (i < 0) return esc(text.slice(0, 90));
  const s = Math.max(0, i - 25); const part = text.slice(s, s + 100);
  const j = i - s; return esc(part.slice(0, j)) + "<mark>" + esc(part.slice(j, j + q.length)) + "</mark>" + esc(part.slice(j + q.length));
};

export function renderList() {
  const box = $("#chat-rows"); if (!box) return;
  const q = query.trim().toLowerCase();
  let items = [...state.chats.values()].sort((a, b) => (Date.parse(b.latestMessageAt || 0) || 0) - (Date.parse(a.latestMessageAt || 0) || 0));
  if (filter === "unread") items = items.filter((c) => c.unreadCount > 0);
  if (q) items = items.filter((c) => c.user.username.toLowerCase().includes(q));
  let out = "";
  const friendCount = [...state.chats.values()].filter((c) => !c.ai).length;
  {
    out = items.map((c) => rowHtml(c).s).join("");
    if (!friendCount && !q && filter === "all") out += emptyState("users", "No friends yet", "Add a friend, or share your personal chat link so people can message you.", html`<button class="btn" id="empty-add">${icon("plus")} Add a friend</button>`).s;
    if (!items.length && !(q.length >= 2)) out = emptyState("inbox", filter === "unread" ? "All caught up" : "No matches", filter === "unread" ? "You have no unread conversations." : "No chats match your search.").s;
  }
  if (q.length >= 2) {
    out += `<div class="small muted" style="padding:12px 16px 4px;font-weight:700">Messages</div>`;
    if (searching) out += `<div class="empty"><span class="spinner"></span></div>`;
    else if (msgResults && !msgResults.length) out += `<div class="small muted" style="padding:6px 16px 16px">No messages found.</div>`;
    else out += (msgResults || []).map((r) => `<button class="row" data-id="${esc(r.otherUser.id)}" data-username="${esc(r.otherUser.username)}"><div class="meta"><div class="top"><span class="name">${esc(r.otherUser.username)}</span><span class="time">${esc(fmtListTime(r.message.sentAt))}</span></div><div class="preview"><span class="t">${mark(r.message.content || "", query.trim())}</span></div></div></button>`).join("");
  }
  const top = box.scrollTop; box.innerHTML = out; box.scrollTop = top;
  $$(".row[data-username]", box).forEach((r) => r.addEventListener("click", () => navigate(r.dataset.href || `/app/c/${encodeURIComponent(r.dataset.username)}`)));
  $("#empty-add", box)?.addEventListener("click", openAddFriend);
}

export function openAddFriend() {
  const m = openModal({
    title: "Add a friend",
    body: html`<div id="af-alert"></div><div class="field"><label for="af-mode">Find them by</label><select class="select" id="af-mode"><option value="username">Username</option><option value="email">Email address</option><option value="phone">Phone number</option><option value="id">Public ID</option></select></div>
      <div class="field" id="af-text"><label for="af-id" id="af-label">Username</label><input class="input" id="af-id" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="e.g. alex_k"></div>
      <div class="field hidden" id="af-phone"><label for="af-phone-num">Phone number</label>${phoneFieldHtml("af-phone")}</div>
      <p class="small muted">Tip: you can also share your personal chat link from <a href="/app/settings" data-link>Settings</a>.</p>`,
    footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn" id="af-send">Send request</button>`,
  });
  const mode = () => $("#af-mode", m.el).value;
  const PH = { username: ["Username", "e.g. alex_k"], email: ["Email address", "name@example.com"], id: ["Public ID", "Their S-Chat public ID"] };
  $("#af-mode", m.el).addEventListener("change", () => {
    const md = mode(), phone = md === "phone";
    $("#af-text", m.el).classList.toggle("hidden", phone); $("#af-phone", m.el).classList.toggle("hidden", !phone);
    if (!phone) { $("#af-label", m.el).textContent = PH[md][0]; $("#af-id", m.el).placeholder = PH[md][1]; $("#af-id", m.el).value = ""; $("#af-id", m.el).focus(); }
  });
  $("#af-id", m.el).focus();
  const submit = async () => {
    let value, viaId = mode() === "id";
    if (mode() === "phone") { const ph = readPhone(m.el, "af-phone"); if (ph.error || !ph.value) return ($("#af-alert", m.el).innerHTML = `<div class="alert error">${esc(ph.error || "Enter their phone number.")}</div>`); value = ph.value; }
    else { value = $("#af-id", m.el).value.trim(); if (!value) return; }
    const b = $("#af-send", m.el); b.disabled = true; $("#af-alert", m.el).innerHTML = "";
    try {
      const r = await chats.sendFriendRequest(value, viaId);
      m.close();
      if (r.status === "ACCEPTED") { toast(`You're connected with ${r.otherUser.username}`, "success"); navigate(`/app/c/${encodeURIComponent(r.otherUser.username)}`); }
      else toast(`Request sent to ${r.otherUser.username}. You can chat as soon as they accept.`, "success");
    } catch (e) { $("#af-alert", m.el).innerHTML = `<div class="alert error">${esc(e.message)}</div>`; b.disabled = false; }
  };
  $("#af-send", m.el).addEventListener("click", submit);
  $("#af-id", m.el).addEventListener("keydown", (e) => e.key === "Enter" && submit());
}
