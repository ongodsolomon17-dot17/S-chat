// App shell: nav rail + chat list pane + main pane. Built once per session, then the main pane swaps per route.
import { html, raw, $, $$, mount, esc, avatar, fmtListTime, debounce, appName, isImage, isVideo, isAudio } from "../core/util.js";
import { state, bus, totalUnread } from "../core/state.js";
import { icon, openModal, toast, emptyState } from "../core/ui.js";
import { navigate } from "../router.js";
import * as chats from "../core/chats.js";

let built = false, filter = "all", query = "", msgResults = null, searching = false;
const cleanups = [];
const SECTIONS = [["chats", "/app", "chat", "Chats"], ["calls", "/app/calls", "phone", "Calls"], ["friends", "/app/friends", "users", "Friends"], ["settings", "/app/settings", "settings", "Settings"]];

export function mainEl() { return $("#main-pane"); }
export function resetShell() { cleanups.splice(0).forEach((f) => f()); built = false; filter = "all"; query = ""; msgResults = null; }

export function renderShell(section, { conv = false } = {}) {
  if (!built) {
    const items = [...SECTIONS, ...(state.me?.role && state.me.role !== "USER" ? [["admin", "/app/admin", "shield", "Admin"]] : [])];
    mount($("#app"), html`<div class="shell" id="shell">
      <nav class="rail" aria-label="Main"><img class="logo" src="/assets/favicon.svg" alt="${appName()}">
        ${items.map(([id, href, ic, label]) => html`<a class="nav" data-nav="${id}" href="${href}" data-link aria-label="${label}" title="${label}">${icon(ic)}<span class="badge hidden" data-badge="${id}"></span></a>`)}
        <span class="spacer"></span></nav>
      <aside class="list-pane" id="list-pane" aria-label="Conversations"></aside>
      <section class="main-pane" id="main-pane"></section></div>`);
    built = true;
    cleanups.push(bus.on("chats", () => { renderList(); renderBadges(); }), bus.on("presence", renderList), bus.on("typing", renderList), bus.on("requests", renderBadges));
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
  set("chats", totalUnread()); set("friends", state.requests.length);
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
  if (state.typing.has(c.user.id)) return html`<span class="typing">typing…</span>`;
  const mine = c.latestSenderId === state.me.id ? "You: " : "";
  if (c.latestMessage) return html`${mine}${c.latestMessage}`;
  const u = c.latestAttachmentUrl;
  if (u) return html`${mine}${isImage(u) ? "📷 Photo" : isVideo(u) ? "🎥 Video" : isAudio(u) ? "🎤 Voice message" : "📎 Attachment"}`;
  return html`<span class="muted">Say hello 👋</span>`;
}
function rowHtml(c) {
  const online = state.online.has(c.user.id);
  const sortUnread = c.unreadCount > 0;
  return html`<button class="row ${state.activeChatId === c.user.id ? "active" : ""} ${sortUnread ? "unread" : ""}" data-id="${c.user.id}" data-username="${c.user.username}">
    ${avatar(c.user, 46, online)}<div class="meta"><div class="top"><span class="name">${c.user.username}</span><span class="time">${fmtListTime(c.latestMessageAt)}</span></div>
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
  if (!state.chats.size && !q) out = emptyState("chat", "No conversations yet", "Add a friend, or share your personal chat link so people can message you.", html`<button class="btn" id="empty-add">${icon("plus")} Add a friend</button>`).s;
  else {
    out = items.map((c) => rowHtml(c).s).join("");
    if (!items.length && !(q.length >= 2)) out = emptyState("inbox", filter === "unread" ? "All caught up" : "No matches", filter === "unread" ? "You have no unread conversations." : "No chats match your search.").s;
  }
  if (q.length >= 2) {
    out += `<div class="small muted" style="padding:12px 16px 4px;font-weight:700">Messages</div>`;
    if (searching) out += `<div class="empty"><span class="spinner"></span></div>`;
    else if (msgResults && !msgResults.length) out += `<div class="small muted" style="padding:6px 16px 16px">No messages found.</div>`;
    else out += (msgResults || []).map((r) => `<button class="row" data-id="${esc(r.otherUser.id)}" data-username="${esc(r.otherUser.username)}"><div class="meta"><div class="top"><span class="name">${esc(r.otherUser.username)}</span><span class="time">${esc(fmtListTime(r.message.sentAt))}</span></div><div class="preview"><span class="t">${mark(r.message.content || "", query.trim())}</span></div></div></button>`).join("");
  }
  const top = box.scrollTop; box.innerHTML = out; box.scrollTop = top;
  $$(".row[data-username]", box).forEach((r) => r.addEventListener("click", () => navigate(`/app/c/${encodeURIComponent(r.dataset.username)}`)));
  $("#empty-add", box)?.addEventListener("click", openAddFriend);
}

export function openAddFriend() {
  const m = openModal({
    title: "Add a friend",
    body: html`<div id="af-alert"></div><div class="field"><label for="af-mode">Find them by</label><select class="select" id="af-mode"><option value="name">Username, email or phone</option><option value="id">Public ID</option></select></div>
      <div class="field"><label for="af-id">Who?</label><input class="input" id="af-id" autocomplete="off" placeholder="e.g. alex_k"></div>
      <p class="small muted">Tip: you can also share your personal chat link from <a href="/app/settings" data-link>Settings</a>.</p>`,
    footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn" id="af-send">Send request</button>`,
  });
  $("#af-id", m.el).focus();
  const submit = async () => {
    const v = $("#af-id", m.el).value.trim(); if (!v) return;
    const b = $("#af-send", m.el); b.disabled = true;
    try {
      const r = await chats.sendFriendRequest(v, $("#af-mode", m.el).value === "id");
      toast(r.status === "ACCEPTED" ? "Added — you can chat now" : "Request sent", "success"); m.close();
    } catch (e) { $("#af-alert", m.el).innerHTML = `<div class="alert error">${esc(e.message)}</div>`; b.disabled = false; }
  };
  $("#af-send", m.el).addEventListener("click", submit);
  $("#af-id", m.el).addEventListener("keydown", (e) => e.key === "Enter" && submit());
}
