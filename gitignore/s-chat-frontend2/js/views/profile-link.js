// /chat/:username — the shareable chat link. Anonymous visitors see a public profile; signed-in visitors can start a chat.
import { html, raw, mount, $, avatar, appName, esc, chatLink } from "../core/util.js";
import { api } from "../core/api.js";
import { state } from "../core/state.js";
import { navigate } from "../router.js";
import { toast, icon } from "../core/ui.js";
import { sendFriendRequest, respondRequest, chatByUsername, loadChats } from "../core/chats.js";

export async function renderChatLink({ username }, authed) {
  document.title = `@${username} · ${appName()}`;
  mount($("#app"), html`<div class="link-page"><div class="card link-card"><div class="splash" style="height:200px"><span class="spinner"></span></div></div></div>`);
  const card = $(".link-card");
  try {
    const p = authed ? await api(`/users/by-username/${encodeURIComponent(username)}`) : await api(`/public/users/${encodeURIComponent(username)}`, { auth: false });
    const link = chatLink(p.username);
    const rel = authed ? p.relationship : null;
    const action = !authed
      ? html`<a class="btn block" href="/login?next=${encodeURIComponent("/chat/" + p.username)}" data-link>Sign in to message ${p.username}</a>
             <p class="small muted" style="margin-top:12px">No account? <a href="/signup" data-link>Create one</a></p>`
      : rel === "SELF" ? html`<div class="alert info">This is your own chat link.</div><div class="copy-row"><input class="input" readonly value="${link}" aria-label="Your chat link"><button class="btn" id="copy">${icon("copy")} Copy</button></div>`
      : rel === "FRIENDS" ? html`<button class="btn block" id="open">${icon("chat")} Open chat</button>`
      : rel === "REQUEST_SENT" ? html`<button class="btn block" disabled>Request sent — waiting for approval</button>`
      : rel === "REQUEST_RECEIVED" ? html`<button class="btn block" id="accept">Accept request &amp; chat</button>`
      : html`<button class="btn block" id="start">${icon("chat")} Start chat</button>`;
    card.innerHTML = html`${avatar(p, 96)}<h1 style="margin-bottom:2px">${p.username}</h1><div class="handle">@${p.username}</div>
      ${p.bio ? html`<p style="margin:14px 0">${p.bio}</p>` : raw('<p class="muted">No bio yet.</p>')}<div style="margin-top:18px">${action}</div>
      <p class="small muted" style="margin-top:18px"><a href="${authed ? "/app" : "/login"}" data-link>${appName()}</a> · <a href="/privacy" data-link>Privacy</a></p>`.s;
    $("#copy", card)?.addEventListener("click", () => navigator.clipboard.writeText(link).then(() => toast("Link copied", "success"), () => toast("Copy failed — select the link and copy manually", "error")));
    const openChat = async () => { if (!chatByUsername(p.username)) await loadChats(); navigate(`/app/c/${encodeURIComponent(p.username)}`); };
    $("#open", card)?.addEventListener("click", openChat);
    $("#start", card)?.addEventListener("click", async (e) => {
      const b = e.currentTarget; b.disabled = true;
      try {
        const r = await sendFriendRequest(p.publicId, true);
        if (r.status === "ACCEPTED") { toast("You can chat now", "success"); await openChat(); }
        else { toast("Request sent", "success"); b.textContent = "Request sent — waiting for approval"; }
      } catch (err) { toast(err.message, "error"); b.disabled = false; }
    });
    $("#accept", card)?.addEventListener("click", async (e) => {
      const b = e.currentTarget; b.disabled = true;
      try { await respondRequest(p.pendingRequestId, true); await openChat(); } catch (err) { toast(err.message, "error"); b.disabled = false; }
    });
  } catch (err) {
    const notFound = err.status === 404;
    card.innerHTML = html`<div class="art" style="margin:0 auto 12px;width:64px;height:64px;border-radius:20px;background:var(--brand-soft);display:grid;place-items:center">${icon("users")}</div>
      <h2>${notFound ? "Profile not available" : "Something went wrong"}</h2>
      <p class="muted">${notFound ? "This chat link doesn't exist, or the person has turned it off." : err.message}</p>
      <a class="btn" href="${authed ? "/app" : "/login"}" data-link>Go to ${appName()}</a>`.s;
  }
}
