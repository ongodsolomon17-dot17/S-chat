import { html, raw, $, $$, mount, esc, avatar, fmtTime, fmtDayLabel, safeUrl, isImage, isVideo, isAudio, fileName, debounce, throttle, chatLink } from "../core/util.js";
import { state, bus } from "../core/state.js";
import { icon, ICONS, openMenu, openModal, confirmDialog, toast, emptyState, spinnerBlock } from "../core/ui.js";
import { api, upload } from "../core/api.js";
import { navigate } from "../router.js";
import * as chats from "../core/chats.js";
import * as calls from "../core/calls.js";
import { mainEl, renderList } from "./shell.js";

const EMOJIS = "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 🤗 🤔 🤨 😐 😶 🙄 😏 😴 😌 😔 😪 🤤 😷 🤒 🥵 🥶 😵 🤯 🥳 😎 🤓 😕 😟 🙁 😮 😲 😳 🥺 😢 😭 😱 😖 😞 😤 😡 🤬 💀 👍 👎 👏 🙌 🙏 💪 👋 🤝 ✌️ 🤞 👌 👀 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 🔥 ✨ 🎉 🎂 🎁 ✅ ❌ ⭐ 💯".split(" ");
const MAX_UPLOAD_MB = 50;
let cleanups = [];
const disposeAll = () => { cleanups.splice(0).forEach((f) => f()); };

export function leaveConversation() { disposeAll(); state.activeChatId = null; }

export async function renderConversation(username) {
  disposeAll();
  const main = mainEl();
  let chat = chats.chatByUsername(username);
  if (!chat) {
    try { await chats.loadChats(); } catch { /* handled below */ }
    chat = chats.chatByUsername(username);
  }
  if (!chat) {
    state.activeChatId = null;
    mount(main, html`<div class="no-chat">${emptyState("users", "Conversation not found", `You aren't connected with “${username}” yet. Add them as a friend to start chatting.`, html`<button class="btn" id="nf-back">Back to chats</button>`)}</div>`);
    $("#nf-back").addEventListener("click", () => navigate("/app"));
    // If the request is accepted while this page is open, show the conversation without a manual refresh.
    const off = bus.on("chats", () => { if (chats.chatByUsername(username)) { off(); renderConversation(username); } });
    cleanups.push(off);
    return;
  }
  const friend = chat.user, fid = friend.id;
  state.activeChatId = fid; renderList();
  let replyTo = null, editing = null, uploading = false, loadingMore = false;

  mount(main, html`
    <header class="conv-head"><button class="icon-btn back-btn" id="back" aria-label="Back to chats">${icon("back")}</button>
      <div class="who-av" id="who" tabindex="0" role="button" aria-label="View profile">${avatar(friend, 40)}</div>
      <div class="who" style="flex:1" id="who2"><div class="name">${friend.username}</div><div class="status" id="status"></div></div>
      <button class="icon-btn" id="call-voice" aria-label="Voice call" title="Voice call">${icon("phone")}</button>
      <button class="icon-btn" id="call-video" aria-label="Video call" title="Video call">${icon("video")}</button>
      <button class="icon-btn" id="find" aria-label="Search in conversation" title="Search in conversation">${icon("search")}</button>
      <button class="icon-btn" id="more" aria-label="More options">${icon("more")}</button></header>
    <div class="searchbox hidden" id="find-bar" style="margin:8px 14px"><input class="input" id="find-input" type="search" placeholder="Search this conversation" aria-label="Search this conversation"><div id="find-results" class="card hidden" style="margin:6px 0 0;padding:6px;max-height:220px;overflow:auto"></div></div>
    <div class="messages" id="msgs" aria-live="polite"></div>
    <div class="composer-wrap">
      <div id="ctx"></div><div id="up-progress" class="progress hidden"><i></i></div>
      <div class="emoji-panel hidden" id="emoji-panel"></div>
      <div class="composer"><button class="icon-btn" id="emoji" aria-label="Emoji">${icon("smile")}</button>
        <button class="icon-btn" id="attach" aria-label="Attach a file">${icon("clip")}</button><input type="file" id="file" class="hidden">
        <textarea id="input" rows="1" placeholder="Write a message" aria-label="Message" maxlength="5000"></textarea>
        <button class="icon-btn send-btn" id="send" aria-label="Send">${icon("send")}</button></div></div>`);
  renderList();

  const msgsEl = $("#msgs"), input = $("#input");

  // ---- header status -----------------------------------------------------------------
  const paintStatus = () => {
    const s = $("#status"); if (!s) return;
    if (state.typing.has(fid)) { s.className = "status online"; s.innerHTML = 'typing <span class="typing-dots"><i></i><i></i><i></i></span>'; }
    else if (state.online.has(fid)) { s.className = "status online"; s.textContent = "Online"; }
    else { s.className = "status"; s.textContent = "Offline"; }
  };
  paintStatus();
  cleanups.push(bus.on("presence", paintStatus), bus.on("typing", (id) => id === fid && (paintStatus(), stickyScroll())));

  // ---- message rendering ---------------------------------------------------------------
  const nearBottom = () => msgsEl.scrollHeight - msgsEl.scrollTop - msgsEl.clientHeight < 120;
  const stickyScroll = () => { if (nearBottom()) msgsEl.scrollTop = msgsEl.scrollHeight; };
  const tick = (m) => {
    if (m.senderId !== state.me.id) return "";
    if (m._failed) return '<span class="tick" title="Not sent" style="color:#fca5a5">!</span>';
    if (m._pending) return `<span class="tick" title="Sending">${ICONS.clock}</span>`;
    if (m.readAt) return `<span class="tick read" title="Read">${ICONS.checks}</span>`;
    if (m.deliveredAt) return `<span class="tick" title="Delivered">${ICONS.checks}</span>`;
    return `<span class="tick" title="Sent">${ICONS.check}</span>`;
  };
  const attachHtml = (u) => {
    const url = safeUrl(u); if (!url) return "";
    if (isImage(url)) return `<div class="attach"><img src="${esc(url)}" alt="Image attachment" data-zoom="${esc(url)}" loading="lazy"></div>`;
    if (isVideo(url)) return `<div class="attach"><video src="${esc(url)}" controls preload="metadata"></video></div>`;
    if (isAudio(url)) return `<div class="attach"><audio src="${esc(url)}" controls preload="none"></audio></div>`;
    return `<a class="file-chip" href="${esc(url)}" target="_blank" rel="noopener noreferrer" download>${ICONS.clip}<span>${esc(fileName(url))}</span></a>`;
  };
  const statusReplyHtml = (m) => {
    const s = m.replyToStatus; if (!s) return "";
    const thumb = s.mediaUrl && /\.(png|jpe?g|gif|webp|avif)(\?|$)/i.test(s.mediaUrl) ? `<img src="${esc(safeUrl(s.mediaUrl))}" alt="">` : `<span class="st-dot" style="background:${esc(s.backgroundColor || "#0aa89a")}"></span>`;
    const text = s.textContent || s.caption || (s.mediaUrl ? "Status" : "Status no longer available");
    return `<div class="reply-preview status-reply">${thumb}<div><b>Status${s.authorName ? " · " + esc(s.authorName) : ""}</b>${esc(String(text).slice(0, 100))}</div></div>`;
  };
  const replyHtml = (m) => {
    if (m.replyToStatus) return statusReplyHtml(m);
    if (!m.replyTo) return "";
    const r = m.replyTo; const who = r.senderId === state.me.id ? "You" : friend.username;
    const text = r.deleted ? "Message deleted" : r.snippet || (r.attachmentUrl ? "Attachment" : "");
    return `<div class="reply-preview" data-goto="${esc(r.messageId)}"><b>${esc(who)}</b>${esc(String(text).slice(0, 120))}</div>`;
  };
  const msgHtml = (m) => {
    const mine = m.senderId === state.me.id;
    const body = m.deleted ? `<div class="bubble deleted">🚫 This message was deleted</div>` :
      `<div class="bubble">${replyHtml(m)}${attachHtml(m.attachmentUrl)}${m.content ? esc(m.content) : ""}<div class="foot">${m.editedAt ? "<span>edited</span>" : ""}<span>${esc(fmtTime(m.sentAt))}</span>${tick(m)}</div></div>`;
    const reacts = !m.deleted && m.reactions?.length ? `<div class="reactions">${m.reactions.map((r) => `<button class="reaction ${r.reactedByMe ? "mine" : ""}" data-react="${esc(r.reactionType)}" aria-label="${esc(r.reactionType)} ${r.count}">${esc(r.reactionType)} ${r.count}</button>`).join("")}</div>` : "";
    const fail = m._failed ? `<div class="small" style="color:var(--danger);margin-top:2px"><button class="btn sm ghost" data-retry>Retry</button><button class="btn sm ghost" data-discard>Discard</button></div>` : "";
    return `<div class="msg ${mine ? "out" : "in"}" data-id="${esc(m.id)}"><div>${body}${reacts}${fail}</div>${m._pending || m._failed ? "" : `<button class="icon-btn msg-menu-btn" data-menu aria-label="Message options">${ICONS.more}</button>`}</div>`;
  };
  const paint = () => {
    const list = state.messages.get(fid);
    if (!list) { msgsEl.innerHTML = '<div class="splash"><span class="spinner"></span></div>'; return; }
    const keepBottom = nearBottom(), prevH = msgsEl.scrollHeight, prevTop = msgsEl.scrollTop;
    let out = state.hasMore.get(fid) ? `<button class="btn secondary sm load-more" id="load-more">Load earlier messages</button>` : "";
    if (!list.length) out += `<div class="no-chat">${emptyState("chat", "No messages yet", `Say hello to ${friend.username} 👋`).s}</div>`;
    let lastDay = "";
    for (const m of list) {
      const day = new Date(m.sentAt).toDateString();
      if (day !== lastDay) { out += `<div class="day-chip">${esc(fmtDayLabel(m.sentAt))}</div>`; lastDay = day; }
      out += msgHtml(m);
    }
    if (state.typing.has(fid)) out += `<div class="msg in"><div class="bubble"><span class="typing-dots"><i></i><i></i><i></i></span></div></div>`;
    msgsEl.innerHTML = out;
    if (loadingMore) { msgsEl.scrollTop = msgsEl.scrollHeight - prevH + prevTop; loadingMore = false; }
    else if (keepBottom || !prevH) msgsEl.scrollTop = msgsEl.scrollHeight;
    else msgsEl.scrollTop = prevTop;
  };
  let pinned = true;
  msgsEl.addEventListener("scroll", () => { pinned = nearBottom(); }, { passive: true });
  msgsEl.addEventListener("load", (e) => { if (e.target.tagName === "IMG" && pinned) msgsEl.scrollTop = msgsEl.scrollHeight; }, true);
  cleanups.push(bus.on("conv:" + fid, paint));
  const findMsg = (id) => (state.messages.get(fid) || []).find((m) => m.id === id);

  // ---- load history ------------------------------------------------------------------------
  paint();
  chats.loadHistory(fid, 0).then(() => { paint(); msgsEl.scrollTop = msgsEl.scrollHeight; chats.markRead(fid, true); }).catch((e) => { toast(e.message, "error"); state.messages.set(fid, state.messages.get(fid) || []); paint(); });
  let page = 0;
  msgsEl.addEventListener("click", async (e) => {
    if (e.target.closest("#load-more")) { loadingMore = true; page += 1; try { await chats.loadHistory(fid, page); } catch (err) { loadingMore = false; page -= 1; toast(err.message, "error"); } return; }
    const row = e.target.closest(".msg"); const m = row && findMsg(row.dataset.id);
    const z = e.target.closest("[data-zoom]"); if (z) return openModal({ title: "Image", body: html`<img src="${z.dataset.zoom}" alt="" style="max-width:100%;border-radius:10px">`, footer: "" });
    const g = e.target.closest("[data-goto]"); if (g) { const t = msgsEl.querySelector(`.msg[data-id="${CSS.escape(g.dataset.goto)}"]`); if (t) { t.scrollIntoView({ block: "center", behavior: "smooth" }); t.animate([{ background: "rgba(99,102,241,.25)" }, { background: "transparent" }], 1200); } else toast("That message is older — load earlier messages to see it."); return; }
    if (!m) return;
    if (e.target.closest("[data-retry]")) return chats.retry(m);
    if (e.target.closest("[data-discard]")) return chats.discardFailed(m);
    const rc = e.target.closest("[data-react]"); if (rc) return react(m, rc.dataset.react);
    const mb = e.target.closest("[data-menu]"); if (mb) openMessageMenu(mb, m);
  });
  msgsEl.addEventListener("dblclick", (e) => { const row = e.target.closest(".msg"); const m = row && findMsg(row.dataset.id); if (m && !m.deleted && !m._pending) react(m, "❤️"); });
  const menuFor = (row) => { const m = row && findMsg(row.dataset.id); if (m && !m._pending && !m._failed) openMessageMenu(row.querySelector(".bubble") || row, m); };
  msgsEl.addEventListener("contextmenu", (e) => { const row = e.target.closest(".msg"); if (row) { e.preventDefault(); menuFor(row); } });
  let lp = null;
  msgsEl.addEventListener("touchstart", (e) => { const row = e.target.closest(".msg"); if (row) lp = setTimeout(() => menuFor(row), 450); }, { passive: true });
  ["touchend", "touchmove", "touchcancel"].forEach((ev) => msgsEl.addEventListener(ev, () => clearTimeout(lp), { passive: true }));
  async function react(m, emoji) { try { await chats.react(m, fid, emoji); } catch (err) { toast(err.message, "error"); } }

  function openMessageMenu(anchor, m) {
    const mine = m.senderId === state.me.id;
    const canEdit = mine && !m.deleted && m.content && Date.now() - Date.parse(m.sentAt) < (window.S_CHAT_CONFIG.EDIT_WINDOW_MINUTES || 15) * 60000;
    const items = [];
    if (!m.deleted) {
      items.push({ label: "Reply", icon: "reply", onClick: () => setReply(m) });
      if (m.content) items.push({ label: "Copy text", icon: "copy", onClick: () => navigator.clipboard.writeText(m.content).then(() => toast("Copied", "success"), () => toast("Copy failed", "error")) });
      if (canEdit) items.push({ label: "Edit", icon: "edit", onClick: () => startEdit(m) });
      if (!mine) items.push({ label: "Report message", icon: "flag", onClick: () => openReport(m) });
    }
    items.push({ label: "Delete for me", icon: "trash", onClick: async () => { try { await chats.deleteForMe(m, fid); } catch (e) { toast(e.message, "error"); } } });
    if (mine && !m.deleted) items.push({ label: "Delete for everyone", icon: "trash", danger: true, onClick: async () => { if (await confirmDialog({ title: "Delete for everyone?", message: "This removes the message for both of you.", confirmText: "Delete", danger: true })) try { await chats.deleteForEveryone(m); } catch (e) { toast(e.message, "error"); } } });
    openMenu(anchor, items, m.deleted ? {} : { quick: { emojis: chats.QUICK_REACTIONS, onPick: (em) => react(m, em) } });
  }

  // ---- composer ---------------------------------------------------------------------------
  const ctx = $("#ctx");
  const paintCtx = () => {
    if (editing) ctx.innerHTML = `<div class="ctx-bar">${ICONS.edit}<div class="t"><b>Editing message</b> — ${esc(editing.content.slice(0, 80))}</div><button class="icon-btn" id="ctx-x" aria-label="Cancel">${ICONS.x}</button></div>`;
    else if (replyTo) ctx.innerHTML = `<div class="ctx-bar">${ICONS.reply}<div class="t"><b>Replying to ${replyTo.senderId === state.me.id ? "yourself" : esc(friend.username)}</b> — ${esc((replyTo.content || "Attachment").slice(0, 80))}</div><button class="icon-btn" id="ctx-x" aria-label="Cancel">${ICONS.x}</button></div>`;
    else ctx.innerHTML = "";
    $("#ctx-x")?.addEventListener("click", () => { replyTo = null; editing = null; input.value = ""; autosize(); paintCtx(); });
  };
  const setReply = (m) => { editing = null; replyTo = m; paintCtx(); input.focus(); };
  const startEdit = (m) => { replyTo = null; editing = m; input.value = m.content; autosize(); paintCtx(); input.focus(); };
  const autosize = () => { input.style.height = "auto"; input.style.height = Math.min(input.scrollHeight, 140) + "px"; };
  const stopTyping = debounce(() => chats.notifyTyping(fid, false), 3000);
  input.addEventListener("input", () => { autosize(); if (input.value.trim()) { chats.notifyTyping(fid, true); stopTyping(); } });
  input.addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); submit(); } if (e.key === "Escape" && (editing || replyTo)) $("#ctx-x")?.click(); });
  $("#send").addEventListener("click", submit);
  async function submit() {
    const text = input.value.trim();
    if (!text || uploading) return;
    if (editing) {
      const target = editing;
      if (text === target.content) { editing = null; input.value = ""; paintCtx(); return; }
      try { await chats.editMessage(target, text); editing = null; input.value = ""; autosize(); paintCtx(); } catch (e) { toast(e.message, "error"); }
      return;
    }
    chats.sendMessage(fid, text, { replyTo });
    chats.notifyTyping(fid, false);
    replyTo = null; input.value = ""; autosize(); paintCtx(); input.focus();
    msgsEl.scrollTop = msgsEl.scrollHeight;
  }

  // emoji
  const panel = $("#emoji-panel");
  panel.innerHTML = EMOJIS.map((e) => `<button type="button" data-e="${e}">${e}</button>`).join("");
  $("#emoji").addEventListener("click", (e) => { e.stopPropagation(); panel.classList.toggle("hidden"); });
  panel.addEventListener("click", (e) => { const b = e.target.closest("[data-e]"); if (!b) return; const s = input.selectionStart ?? input.value.length; input.value = input.value.slice(0, s) + b.dataset.e + input.value.slice(input.selectionEnd ?? s); input.focus(); input.setSelectionRange(s + b.dataset.e.length, s + b.dataset.e.length); });
  const closePanel = (e) => { if (!panel.contains(e.target) && e.target.closest("#emoji") == null) panel.classList.add("hidden"); };
  document.addEventListener("click", closePanel); cleanups.push(() => document.removeEventListener("click", closePanel));

  // attachments
  $("#attach").addEventListener("click", () => $("#file").click());
  $("#file").addEventListener("change", async (e) => {
    const file = e.target.files[0]; e.target.value = ""; if (!file) return;
    if (file.size > MAX_UPLOAD_MB * 1024 * 1024) return toast(`That file is larger than ${MAX_UPLOAD_MB} MB.`, "error");
    uploading = true; const bar = $("#up-progress"); bar.classList.remove("hidden");
    try {
      const { url } = await upload("/chat/attachment", file, (p) => { bar.firstElementChild.style.width = Math.round(p * 100) + "%"; });
      chats.sendMessage(fid, input.value.trim(), { attachmentUrl: url, replyTo }); input.value = ""; replyTo = null; autosize(); paintCtx();
    } catch (err) { toast(err.message, "error"); }
    finally { uploading = false; bar.classList.add("hidden"); bar.firstElementChild.style.width = "0"; }
  });

  // ---- header actions ----------------------------------------------------------------------
  $("#back").addEventListener("click", () => navigate("/app"));
  const openProfile = () => openModal({ title: friend.username, body: html`<div class="center">${avatar(friend, 88)}<h3 style="margin-top:10px">${friend.username}</h3><p class="muted small">Chat link: <a href="/chat/${friend.username}" data-link>${chatLink(friend.username)}</a></p></div>`, footer: html`<button class="btn secondary" data-close>Close</button>` });
  $("#who").addEventListener("click", openProfile); $("#who2").addEventListener("click", openProfile);
  $("#call-voice").addEventListener("click", () => calls.start(fid, friend.username, "VOICE"));
  $("#call-video").addEventListener("click", () => calls.start(fid, friend.username, "VIDEO"));
  $("#more").addEventListener("click", (e) => openMenu(e.currentTarget, [
    { label: "View profile", icon: "users", onClick: openProfile },
    { label: "Report user", icon: "flag", onClick: () => openReport(null) },
    { label: "Block user", icon: "block", danger: true, onClick: async () => { if (await confirmDialog({ title: `Block ${friend.username}?`, message: "They won't be able to message or call you, and you won't see each other online. You can unblock them in Settings.", confirmText: "Block", danger: true })) try { await chats.blockUser(fid); toast("User blocked", "success"); navigate("/app"); } catch (err) { toast(err.message, "error"); } } },
    { label: "Remove friend", icon: "trash", danger: true, onClick: async () => { if (await confirmDialog({ title: `Remove ${friend.username}?`, message: "You'll no longer be able to message each other until you reconnect.", confirmText: "Remove", danger: true })) try { await chats.removeFriend(fid); navigate("/app"); } catch (err) { toast(err.message, "error"); } } },
  ]));

  // in-conversation search
  const run = debounce(async () => {
    const q = $("#find-input").value.trim(), box = $("#find-results");
    if (q.length < 2) return box.classList.add("hidden");
    try {
      const r = await chats.searchMessages(q, fid); box.classList.remove("hidden");
      box.innerHTML = r.length ? r.map((x) => `<button class="row" data-mid="${esc(x.message.id)}"><div class="meta"><div class="top"><span class="name small">${esc(x.message.senderId === state.me.id ? "You" : friend.username)}</span><span class="time">${esc(fmtDayLabel(x.message.sentAt))}</span></div><div class="preview"><span class="t">${esc(x.message.content || "")}</span></div></div></button>`).join("") : '<div class="small muted" style="padding:10px">No messages found.</div>';
    } catch (e) { toast(e.message, "error"); }
  }, 350);
  $("#find").addEventListener("click", () => { $("#find-bar").classList.toggle("hidden"); $("#find-input").focus(); });
  $("#find-input").addEventListener("input", run);
  $("#find-results").addEventListener("click", (e) => { const r = e.target.closest("[data-mid]"); if (!r) return; const t = msgsEl.querySelector(`.msg[data-id="${CSS.escape(r.dataset.mid)}"]`); if (t) { t.scrollIntoView({ block: "center", behavior: "smooth" }); t.animate([{ background: "rgba(99,102,241,.25)" }, { background: "transparent" }], 1400); } else toast("That message is older — use “Load earlier messages”."); });

  function openReport(msg) {
    const m = openModal({
      title: `Report ${friend.username}`,
      body: html`<div id="rp-alert"></div>${msg ? html`<div class="alert info">Reporting this message: “${(msg.content || "Attachment").slice(0, 120)}”</div>` : ""}
        <div class="field"><label for="rp-reason">Reason</label><select class="select" id="rp-reason"><option value="SPAM">Spam</option><option value="HARASSMENT">Harassment or bullying</option><option value="INAPPROPRIATE">Inappropriate content</option><option value="IMPERSONATION">Impersonation</option><option value="OTHER">Something else</option></select></div>
        <div class="field"><label for="rp-details">Details <span class="muted">(optional)</span></label><textarea class="textarea" id="rp-details" rows="3" maxlength="1000"></textarea></div>
        <p class="small muted">Our moderators can see the reported message and the details you give. We won't tell ${friend.username} who reported them.</p>`,
      footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn" id="rp-send">Submit report</button>`,
    });
    $("#rp-send", m.el).addEventListener("click", async (e) => {
      const b = e.currentTarget; b.disabled = true;
      try { await chats.reportUser({ reportedUserId: fid, messageId: msg?.id || null, reason: $("#rp-reason", m.el).value, details: $("#rp-details", m.el).value.trim() || null }); toast("Report submitted. Thank you.", "success"); m.close(); }
      catch (err) { $("#rp-alert", m.el).innerHTML = `<div class="alert error">${esc(err.message)}</div>`; b.disabled = false; }
    });
  }
  setTimeout(() => input.focus(), 50);
}
