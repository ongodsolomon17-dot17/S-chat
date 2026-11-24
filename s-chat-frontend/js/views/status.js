// Status screens: the list page, the full-screen story viewer, and the composers (text, photo/video, voice note).
import { html, raw, $, $$, mount, esc, avatar, safeUrl, appName } from "../core/util.js";
import { state, bus } from "../core/state.js";
import { icon, ICONS, openMenu, openModal, confirmDialog, toast, emptyState, spinnerBlock } from "../core/ui.js";
import * as st from "../core/status.js";
import { mainEl } from "./shell.js";

const IMG_MAX_MB = 50;
const bgOf = (s) => s.backgroundColor || st.DEFAULT_BG;

// ================================================================ page
export async function renderStatusPage() {
  const main = mainEl();
  mount(main, html`<div class="page-wrap"><div class="page"><div class="top" style="display:flex;align-items:center;gap:10px;margin-bottom:16px"><h1 style="margin:0;flex:1">Status</h1>
    <button class="btn" id="add-status">${icon("plus")} Add status</button></div><div id="st-body">${spinnerBlock()}</div></div></div>`);
  $("#add-status").addEventListener("click", (e) => openAddMenu(e.currentTarget));
  const draw = () => {
    const body = $("#st-body"); if (!body) return off();
    const mine = state.status.mine, groups = st.groupFeed(), me = state.me;
    const myRow = `<div class="row st-row" id="my-status" role="button" tabindex="0" style="cursor:pointer;padding-left:0;padding-right:0">
        <span class="ring ${mine.length ? "ring-mine" : "ring-none"}">${avatar(me, 48).s}</span>
        <div class="meta"><div class="name">My status</div><div class="small muted">${mine.length ? `${mine.length} update${mine.length > 1 ? "s" : ""} · ${esc(st.ago(mine[mine.length - 1].createdAt))}` : "Tap + to share a photo, video, voice note or text"}</div></div></div>`;
    const feed = groups.length ? groups.map((g) => `<div class="row st-row" data-author="${esc(g.author.id)}" role="button" tabindex="0" style="padding-left:0;padding-right:0">
        <span class="ring ${g.unseen ? "ring-new" : "ring-seen"}">${avatar(g.author, 48).s}</span>
        <div class="meta"><div class="name">${esc(g.author.username)}</div><div class="small muted">${g.items.length} update${g.items.length > 1 ? "s" : ""} · ${esc(st.ago(g.latestAt))}${g.unseen ? ` · <b style="color:var(--brand)">${g.unseen} new</b>` : ""}</div></div></div>`).join("")
      : emptyState("status", "No updates yet", "When your friends post a status it shows up here for 24 hours.").s;
    body.innerHTML = `<div class="card">${myRow}</div><div class="card"><h3>Recent updates</h3>${feed}</div>`;
    $("#my-status").addEventListener("click", () => (mine.length ? openViewer([{ author: me, items: mine, own: true }], 0) : openAddMenu($("#add-status"))));
    $$("[data-author]", body).forEach((r) => r.addEventListener("click", () => { const gs = st.groupFeed(); openViewer(gs, gs.findIndex((g) => g.author.id === r.dataset.author)); }));
  };
  const off = bus.on("status", draw);
  draw();
  try { await st.loadStatuses(); } catch (e) { toast(e.message, "error"); const b = $("#st-body"); if (b && !state.status.loaded) b.innerHTML = `<div class="alert error">${esc(e.message)}</div>`; }
}

function openAddMenu(anchor) {
  openMenu(anchor, [
    { label: "Photo or video", icon: "plus", onClick: openMediaComposer },
    { label: "Text", icon: "edit", onClick: openTextComposer },
    { label: "Voice note", icon: "mic", onClick: openVoiceComposer },
  ]);
}

// ================================================================ composers
function openTextComposer() {
  let bg = st.DEFAULT_BG;
  const m = openModal({
    title: "Text status",
    body: html`<div id="txt-prev" class="st-card-prev" style="background:${bg}"><textarea id="txt" maxlength="700" placeholder="Type a status…" aria-label="Status text"></textarea></div>
      <div class="swatches" id="sw" role="radiogroup" aria-label="Background colour">${st.TEXT_COLORS.map((c) => html`<button type="button" class="swatch${c === bg ? " active" : ""}" data-c="${c}" style="background:${c}" role="radio" aria-label="Colour ${c}"></button>`)}</div>
      <div class="small muted" style="margin-top:8px"><span id="cnt">0</span>/700 · visible to your friends for 24 hours</div><div id="txt-err"></div>`,
    footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn" id="post" disabled>Post status</button>`,
  });
  const ta = $("#txt", m.el), post = $("#post", m.el); ta.focus();
  ta.addEventListener("input", () => { $("#cnt", m.el).textContent = ta.value.length; post.disabled = !ta.value.trim(); });
  $("#sw", m.el).addEventListener("click", (e) => { const b = e.target.closest(".swatch"); if (!b) return; bg = b.dataset.c; $$(".swatch", m.el).forEach((x) => x.classList.toggle("active", x === b)); $("#txt-prev", m.el).style.background = bg; });
  post.addEventListener("click", async () => {
    post.disabled = true; post.innerHTML = '<span class="spinner"></span> Posting…';
    try { await st.postText(ta.value.trim(), bg); toast("Status posted", "success"); m.close(); } catch (e) { $("#txt-err", m.el).innerHTML = `<div class="alert error" style="margin-top:10px">${esc(e.message)}</div>`; post.disabled = false; post.textContent = "Post status"; }
  });
}

function openMediaComposer() {
  let file = null, url = null;
  const m = openModal({
    title: "Photo or video status",
    body: html`<input type="file" id="f" accept="image/*,video/*,audio/*" class="hidden"><div id="prev" class="st-media-prev"><button class="btn secondary" id="pick">${icon("plus")} Choose a photo or video</button></div>
      <div class="field" style="margin-top:12px"><label for="cap">Caption <span class="muted">(optional)</span></label><input class="input" id="cap" maxlength="280"></div>
      <div id="prog" class="progress hidden"><i></i></div><div id="m-err"></div>`,
    footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn" id="post" disabled>Post status</button>`,
    onClose: () => url && URL.revokeObjectURL(url),
  });
  const input = $("#f", m.el), post = $("#post", m.el);
  $("#pick", m.el).addEventListener("click", () => input.click());
  input.addEventListener("change", () => {
    const f = input.files[0]; if (!f) return;
    if (f.size > IMG_MAX_MB * 1024 * 1024) return ($("#m-err", m.el).innerHTML = `<div class="alert error">That file is larger than ${IMG_MAX_MB} MB.</div>`);
    if (f.type === "video/webm") return ($("#m-err", m.el).innerHTML = '<div class="alert error">WebM files are treated as voice notes. Please use an MP4 or MOV video.</div>');
    $("#m-err", m.el).innerHTML = ""; file = f; if (url) URL.revokeObjectURL(url); url = URL.createObjectURL(f);
    const t = f.type.startsWith("video/") ? `<video src="${url}" controls playsinline></video>` : f.type.startsWith("audio/") ? `<audio src="${url}" controls></audio>` : `<img src="${url}" alt="Preview">`;
    $("#prev", m.el).innerHTML = t + `<div><button class="link-btn" id="pick2" type="button">Choose a different file</button></div>`; $("#pick2", m.el).addEventListener("click", () => input.click()); post.disabled = false;
  });
  post.addEventListener("click", async () => {
    post.disabled = true; $("#prog", m.el).classList.remove("hidden");
    try { await st.postFile(file, $("#cap", m.el).value.trim(), (p) => ($("#prog i", m.el).style.width = Math.round(p * 100) + "%")); toast("Status posted", "success"); m.close(); }
    catch (e) { $("#m-err", m.el).innerHTML = `<div class="alert error">${esc(e.message)}</div>`; post.disabled = false; $("#prog", m.el).classList.add("hidden"); }
  });
}

function openVoiceComposer() {
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") return toast("Voice notes aren't supported in this browser.", "error");
  let rec = null, chunks = [], stream = null, timer = null, secs = 0, blob = null, url = null;
  const MAX = 180;
  const m = openModal({
    title: "Voice note status",
    body: html`<div class="center" style="padding:10px 0"><div class="rec-dot" id="dot"></div><div id="time" style="font-size:32px;font-weight:700;margin:8px 0">0:00</div><div class="muted small" id="hint">Tap record and start talking (up to 3 minutes)</div><div id="audio-prev" style="margin-top:12px"></div></div><div id="v-err"></div>`,
    footer: html`<button class="btn secondary" data-close>Cancel</button><button class="btn danger" id="rec">● Record</button><button class="btn hidden" id="post">Post status</button>`,
    onClose: () => { cleanup(); url && URL.revokeObjectURL(url); },
  });
  const fmt = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
  function cleanup() { clearInterval(timer); try { if (rec && rec.state !== "inactive") rec.stop(); } catch {} stream?.getTracks().forEach((t) => t.stop()); }
  const stop = () => { clearInterval(timer); if (rec && rec.state !== "inactive") rec.stop(); };
  $("#rec", m.el).addEventListener("click", async (e) => {
    const b = e.currentTarget;
    if (rec && rec.state === "recording") return stop();
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mime = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/mp4"].find((t) => MediaRecorder.isTypeSupported?.(t)) || "";
      rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined); chunks = []; secs = 0;
      rec.ondataavailable = (ev) => ev.data.size && chunks.push(ev.data);
      rec.onstop = () => {
        stream.getTracks().forEach((t) => t.stop()); blob = new Blob(chunks, { type: rec.mimeType || "audio/webm" });
        if (!blob.size) return;
        url = URL.createObjectURL(blob); $("#audio-prev", m.el).innerHTML = `<audio src="${url}" controls></audio>`;
        $("#hint", m.el).textContent = "Preview your voice note, then post it."; $("#dot", m.el).classList.remove("on"); b.textContent = "● Record again"; $("#post", m.el).classList.remove("hidden");
      };
      rec.start(); $("#dot", m.el).classList.add("on"); b.textContent = "■ Stop"; $("#post", m.el).classList.add("hidden"); $("#audio-prev", m.el).innerHTML = ""; $("#hint", m.el).textContent = "Recording…";
      timer = setInterval(() => { secs++; $("#time", m.el).textContent = fmt(secs); if (secs >= MAX) stop(); }, 1000);
    } catch { $("#v-err", m.el).innerHTML = '<div class="alert error">Allow microphone access to record a voice note.</div>'; }
  });
  $("#post", m.el).addEventListener("click", async (e) => {
    const b = e.currentTarget; b.disabled = true; b.innerHTML = '<span class="spinner"></span> Posting…';
    const ext = /ogg/.test(blob.type) ? "ogg" : /mp4/.test(blob.type) ? "m4a" : "webm";
    try { await st.postFile(new File([blob], `voice-note.${ext}`, { type: blob.type }), "", null); toast("Status posted", "success"); m.close(); }
    catch (err) { $("#v-err", m.el).innerHTML = `<div class="alert error">${esc(err.message)}</div>`; b.disabled = false; b.textContent = "Post status"; }
  });
}

// ================================================================ viewer
const IMAGE_MS = 5000;
export function openViewer(groups, startGroup) {
  if (!groups.length || startGroup < 0) return;
  let gi = startGroup, ii = 0, timer = null, paused = false, startedAt = 0, remaining = IMAGE_MS;
  const el = document.createElement("div"); el.className = "story-overlay"; el.setAttribute("role", "dialog"); el.setAttribute("aria-label", "Status viewer"); document.body.appendChild(el);
  const cur = () => groups[gi].items[ii];
  const firstUnseen = (g) => Math.max(0, g.items.findIndex((i) => !st.isSeen(i.id)));
  ii = groups[gi].own ? 0 : firstUnseen(groups[gi]);

  const close = () => { clearTimeout(timer); document.removeEventListener("keydown", onKey); el.remove(); bus.emit("status"); };
  const next = () => { if (ii < groups[gi].items.length - 1) { ii++; } else if (gi < groups.length - 1) { gi++; ii = groups[gi].own ? 0 : firstUnseen(groups[gi]); } else return close(); show(); };
  const prev = () => { if (ii > 0) ii--; else if (gi > 0) { gi--; ii = groups[gi].items.length - 1; } show(); };
  const onKey = (e) => { if (e.target.tagName === "INPUT") { if (e.key === "Escape") e.target.blur(); return; } if (e.key === "Escape") close(); else if (e.key === "ArrowRight") next(); else if (e.key === "ArrowLeft") prev(); };
  document.addEventListener("keydown", onKey);

  function schedule(ms) { clearTimeout(timer); remaining = ms; startedAt = Date.now(); if (!paused) timer = setTimeout(next, ms); const fill = $(".seg.cur i", el); if (fill) { fill.style.transition = "none"; fill.style.width = "0"; void fill.offsetWidth; fill.style.transition = `width ${ms}ms linear`; fill.style.width = "100%"; } }
  function pause() { if (paused) return; paused = true; clearTimeout(timer); remaining = Math.max(200, remaining - (Date.now() - startedAt)); const fill = $(".seg.cur i", el); if (fill) { const w = getComputedStyle(fill).width; fill.style.transition = "none"; fill.style.width = w; } $("video,audio", el)?.pause?.(); }
  function resume() { if (!paused) return; paused = false; schedule(remaining); $("video,audio", el)?.play?.().catch(() => {}); }

  function show() {
    clearTimeout(timer); paused = false;
    const g = groups[gi], s = cur(), kind = st.kindOf(s), own = !!g.own || s.author.id === state.me.id; st.markSeen(s.id);
    const media = kind === "text" ? `<div class="story-text" style="background:${esc(bgOf(s))}"><p>${esc(s.textContent)}</p></div>`
      : kind === "video" ? `<video class="story-media" src="${esc(safeUrl(s.mediaUrl))}" autoplay playsinline controls></video>`
      : kind === "audio" ? `<div class="story-text" style="background:${esc(bgOf(s))}"><div style="font-size:64px">🎤</div><audio class="story-audio" src="${esc(safeUrl(s.mediaUrl))}" autoplay controls></audio></div>`
      : `<img class="story-media" src="${esc(safeUrl(s.mediaUrl))}" alt="Status from ${esc(s.author.username)}">`;
    el.innerHTML = `<div class="story-progress">${g.items.map((_, i) => `<span class="seg ${i < ii ? "done" : i === ii ? "cur" : ""}"><i></i></span>`).join("")}</div>
      <div class="story-head">${avatar(s.author, 36).s}<div style="flex:1;min-width:0"><b>${esc(own ? "My status" : s.author.username)}</b><div class="small" style="opacity:.8">${esc(st.ago(s.createdAt))} · ${esc(st.timeLeft(s.expiresAt))}</div></div>
        ${own ? `<button class="icon-btn story-btn" id="sv-del" aria-label="Delete status">${ICONS.trash}</button>` : ""}<button class="icon-btn story-btn" id="sv-close" aria-label="Close">${ICONS.x}</button></div>
      <div class="story-stage"><button class="story-nav left" id="sv-prev" aria-label="Previous"></button>${media}<button class="story-nav right" id="sv-next" aria-label="Next"></button></div>
      ${s.caption ? `<div class="story-caption">${esc(s.caption)}</div>` : ""}
      ${own ? "" : `<form class="story-reply" id="sv-reply"><input class="input" id="sv-input" maxlength="1000" placeholder="Reply to ${esc(s.author.username)}…" aria-label="Reply to status" autocomplete="off"><button class="icon-btn send-btn" aria-label="Send reply">${ICONS.send}</button></form>`}`;
    $("#sv-close", el).addEventListener("click", close); $("#sv-next", el).addEventListener("click", next); $("#sv-prev", el).addEventListener("click", prev);
    const stage = $(".story-stage", el); stage.addEventListener("pointerdown", pause); ["pointerup", "pointerleave", "pointercancel"].forEach((ev) => stage.addEventListener(ev, resume));
    const inp = $("#sv-input", el); inp?.addEventListener("focus", pause); inp?.addEventListener("blur", resume);
    $("#sv-reply", el)?.addEventListener("submit", async (e) => {
      e.preventDefault(); const text = inp.value.trim(); if (!text) return; inp.disabled = true;
      try { await st.replyToStatus(s.id, text); inp.value = ""; toast("Reply sent", "success"); } catch (err) { toast(err.message, "error"); } finally { inp.disabled = false; inp.focus(); }
    });
    $("#sv-del", el)?.addEventListener("click", async () => {
      pause(); if (!(await confirmDialog({ title: "Delete this status?", message: "It will be removed for everyone.", confirmText: "Delete", danger: true }))) return resume();
      try { await st.deleteStatus(s.id); toast("Status deleted", "success"); groups[gi].items = groups[gi].items.filter((x) => x.id !== s.id); if (!groups[gi].items.length) return close(); ii = Math.min(ii, groups[gi].items.length - 1); show(); } catch (err) { toast(err.message, "error"); resume(); }
    });
    if (kind === "video" || kind === "audio") { const mEl = $("video,audio", el); mEl.addEventListener("ended", next); mEl.addEventListener("error", () => schedule(IMAGE_MS)); $(".seg.cur i", el).style.width = "0"; }
    else schedule(IMAGE_MS);
  }
  show();
}
