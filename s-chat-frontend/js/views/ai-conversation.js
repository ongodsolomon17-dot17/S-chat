// STech AI conversation — looks and behaves like any other chat, but the other side is the assistant.
// History is stored server-side per user (/api/ai/history); this view only renders it and sends new turns.
import { html, raw, $, mount, esc, avatar, fmtTime, fmtDayLabel, mdLite, uid } from "../core/util.js";
import { state } from "../core/state.js";
import { api } from "../core/api.js";
import { icon, ICONS, openMenu, confirmDialog, toast, emptyState } from "../core/ui.js";
import { navigate } from "../router.js";
import { AI_ID, AI_USER, setAiEntry, loadChats } from "../core/chats.js";
import { mainEl, renderList } from "./shell.js";

const SUGGESTIONS = ["Explain something simply", "Help me write a polite message", "Give me ideas for a project"];

export async function renderAi() {
  state.activeChatId = AI_ID; renderList();
  const main = mainEl();
  mount(main, html`
    <header class="conv-head"><button class="icon-btn back-btn" id="back" aria-label="Back to chats">${icon("back")}</button>
      <div class="who-av">${avatar(AI_USER, 40)}</div>
      <div class="who" style="flex:1"><div class="name">STech AI <span class="pill ai-pill">AI</span></div><div class="status" id="status">AI assistant</div></div>
      <button class="icon-btn" id="more" aria-label="More options">${icon("more")}</button></header>
    <div id="ai-banner"></div>
    <div class="messages" id="msgs" aria-live="polite"></div>
    <div class="composer-wrap"><div class="composer">
      <textarea id="input" rows="1" placeholder="Ask STech AI anything" aria-label="Message to STech AI" maxlength="4000"></textarea>
      <button class="icon-btn send-btn" id="send" aria-label="Send">${icon("send")}</button></div></div>`);
  const msgsEl = $("#msgs"), input = $("#input");
  let loaded = false;

  const status = () => { const s = $("#status"); if (s) { s.textContent = state.ai.busy ? "typing…" : "AI assistant"; s.className = "status" + (state.ai.busy ? " online" : ""); } };
  const nearBottom = () => msgsEl.scrollHeight - msgsEl.scrollTop - msgsEl.clientHeight < 140;
  const bubble = (m) => {
    const mine = m.role === "user";
    const body = mine ? esc(m.content) : mdLite(m.content);
    const foot = m._pending ? `<span class="tick" title="Sending">${ICONS.clock}</span>` : m._failed ? '<span class="tick" style="color:#fca5a5">!</span>' : "";
    return `<div class="msg ${mine ? "out" : "in"}" data-cid="${esc(m.clientId || "")}"><div><div class="bubble ${mine ? "" : "md"}">${body}<div class="foot"><span>${esc(fmtTime(m.createdAt))}</span>${foot}</div></div>${m._failed ? `<div class="small" style="color:var(--danger);margin-top:2px"><button class="btn sm ghost" data-retry="${esc(m.clientId)}">Retry</button><button class="btn sm ghost" data-discard="${esc(m.clientId)}">Discard</button></div>` : ""}</div></div>`;
  };
  const paint = () => {
    const keep = nearBottom() || !loaded;
    if (!loaded) { msgsEl.innerHTML = '<div class="splash"><span class="spinner"></span></div>'; return; }
    const all = [...(state.ai.messages || []), ...state.ai.local];
    let out = "";
    if (!all.length) out = `<div class="no-chat">${emptyState("chat", "Hi, I'm STech AI ✨", "Ask me anything — I can explain things, help you write, brainstorm ideas and more.", html`<div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:center">${SUGGESTIONS.map((s) => html`<button class="btn secondary sm" data-suggest="${s}">${s}</button>`)}</div>`).s}</div>`;
    let lastDay = "";
    for (const m of all) { const d = new Date(m.createdAt).toDateString(); if (d !== lastDay) { out += `<div class="day-chip">${esc(fmtDayLabel(m.createdAt))}</div>`; lastDay = d; } out += bubble(m); }
    if (state.ai.busy) out += `<div class="msg in"><div class="bubble"><span class="typing-dots"><i></i><i></i><i></i></span></div></div>`;
    msgsEl.innerHTML = out; if (keep) msgsEl.scrollTop = msgsEl.scrollHeight; status();
  };
  const banner = () => { $("#ai-banner").innerHTML = state.ai.available === false ? '<div class="alert info" style="margin:10px 14px 0">STech AI isn\'t switched on for this server yet. The owner needs to add the AI key (see OWNER_CONFIGURATION).</div>' : ""; };

  // ---- load history ----
  paint(); banner();
  try {
    const rows = await api("/ai/history");
    state.ai.messages = rows.map((r) => ({ role: r.role, content: r.content, createdAt: r.createdAt || new Date().toISOString() }));
    if (state.activeChatId !== AI_ID) return; // user navigated away
  } catch (e) { toast(e.message, "error"); state.ai.messages = state.ai.messages || []; }
  loaded = true; paint(); banner(); msgsEl.scrollTop = msgsEl.scrollHeight;

  // ---- sending ----
  const syncListRow = () => {
    const last = [...(state.ai.messages || [])].pop();
    setAiEntry(state.chats, last ? { available: state.ai.available, latestRole: last.role, latestContent: last.content.slice(0, 160), latestAt: last.createdAt } : null);
    renderList();
  };
  async function deliver(local) {
    state.ai.busy = true; local._pending = true; local._failed = false; paint(); renderList();
    try {
      const res = await api("/ai/chat", { method: "POST", body: { messages: [{ role: "user", content: local.content }], clientMessageId: local.clientId } });
      state.ai.messages = res.history.map((r) => ({ role: r.role, content: r.content, createdAt: r.createdAt || new Date().toISOString() }));
      state.ai.local = state.ai.local.filter((x) => x !== local);
    } catch (e) {
      local._pending = false; local._failed = true; toast(e.message, "error");
      if (e.status === 503) { await api("/ai/summary").then((s) => { state.ai.available = s.available; banner(); }).catch(() => {}); }
    } finally { state.ai.busy = false; paint(); syncListRow(); }
  }
  async function send(text) {
    text = (text ?? input.value).trim(); if (!text || state.ai.busy || state.ai.available === false) return;
    const local = { role: "user", content: text, createdAt: new Date().toISOString(), clientId: uid() };
    state.ai.local.push(local); input.value = ""; autosize(); msgsEl.scrollTop = msgsEl.scrollHeight;
    await deliver(local);
  }
  const autosize = () => { input.style.height = "auto"; input.style.height = Math.min(input.scrollHeight, 140) + "px"; };
  input.addEventListener("input", autosize);
  input.addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); } });
  $("#send").addEventListener("click", () => send());
  msgsEl.addEventListener("click", (e) => {
    const s = e.target.closest("[data-suggest]"); if (s) return send(s.dataset.suggest);
    const r = e.target.closest("[data-retry]"); if (r) { const l = state.ai.local.find((x) => x.clientId === r.dataset.retry); if (l) deliver(l); return; }
    const d = e.target.closest("[data-discard]"); if (d) { state.ai.local = state.ai.local.filter((x) => x.clientId !== d.dataset.discard); paint(); }
  });

  $("#back").addEventListener("click", () => navigate("/app"));
  $("#more").addEventListener("click", (e) => openMenu(e.currentTarget, [{ label: "Clear conversation", icon: "trash", danger: true, onClick: async () => {
    if (!(await confirmDialog({ title: "Clear this conversation?", message: "This deletes your chat history with STech AI. It can't be undone.", confirmText: "Clear", danger: true }))) return;
    try { await api("/ai/history", { method: "DELETE" }); state.ai.messages = []; state.ai.local = []; paint(); syncListRow(); toast("Conversation cleared", "success"); } catch (err) { toast(err.message, "error"); }
  } }]));
  setTimeout(() => input.focus(), 50);
}
