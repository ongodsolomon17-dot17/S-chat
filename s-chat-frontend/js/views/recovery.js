// /forgot — account recovery by one-time code over email, SMS or WhatsApp (whichever the server has configured).
import { html, raw, $, mount, esc, appName } from "../core/util.js";
import { api } from "../core/api.js";
import { navigate } from "../router.js";
import { toast, ICONS } from "../core/ui.js";

const LABEL = { EMAIL: ["Email", "Send a code to the email on your account"], SMS: ["Text message (SMS)", "Send a code to the phone number on your account"], WHATSAPP: ["WhatsApp", "Send a code to your WhatsApp number"] };

export async function renderForgot() {
  document.title = `Reset password · ${appName()}`;
  const shell = (inner) => mount($("#app"), html`<div class="auth"><main class="auth-panel" style="grid-column:1/-1"><div class="auth-card"><div class="brand"><img src="/assets/logo-128.png" alt="">${appName()}</div>${inner}</div></main></div>`);
  shell(html`<div class="splash" style="height:200px"><span class="spinner"></span></div>`);
  let channels = [];
  try { channels = (await api("/auth/recovery/channels", { auth: false })).channels || []; } catch (e) { return shell(html`<div class="alert error">${e.message}</div><a class="btn secondary block" href="/login" data-link>Back to sign in</a>`); }
  let identifier = "", channel = channels[0] || "";

  const stepRequest = () => {
    shell(html`<h2>Reset your password</h2><p class="muted">Tell us who you are and where to send a 6-digit code.</p><div id="alert"></div>
      ${channels.length ? html`<form id="req" novalidate>
        <div class="field"><label for="fid">Username or email</label><input class="input" id="fid" autocomplete="username" value="${identifier}" required></div>
        <div class="field"><label>Send the code by</label><div id="channels" style="display:grid;gap:8px">${channels.map((c) => html`<label class="check card" style="padding:12px;margin:0"><input type="radio" name="ch" value="${c}" ${c === channel ? raw("checked") : ""}><span><b>${LABEL[c][0]}</b><br><span class="small muted">${LABEL[c][1]}</span></span></label>`)}</div></div>
        <button class="btn block" type="submit">Send code</button></form>`
        : html`<div class="alert info">Password recovery isn't switched on for this server yet. Please contact support.</div>`}
      <p class="center small" style="margin-top:16px"><a href="/login" data-link>← Back to sign in</a></p>`);
    const form = $("#req"); if (!form) return;
    $("#fid").focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault(); identifier = $("#fid").value.trim(); channel = form.ch.value; $("#alert").innerHTML = "";
      if (!identifier) return ($("#alert").innerHTML = '<div class="alert error">Enter your username or email.</div>');
      const b = form.querySelector("button"); b.disabled = true; b.innerHTML = '<span class="spinner"></span> Sending…';
      try { await api("/auth/recovery/request", { method: "POST", body: { identifier, channel }, auth: false }); stepCode(); }
      catch (err) { $("#alert").innerHTML = `<div class="alert error">${esc(err.message)}</div>`; b.disabled = false; b.textContent = "Send code"; }
    });
  };

  const stepCode = () => {
    shell(html`<h2>Enter your code</h2><p class="muted">If an account matches <b>${identifier}</b>, a 6-digit code is on its way by ${LABEL[channel][0]}. It expires in a few minutes.</p><div id="alert"></div>
      <form id="reset" novalidate>
        <div class="field"><label for="code">6-digit code</label><input class="input" id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]*" required></div>
        <div class="field"><label for="np">New password</label><input class="input" id="np" type="password" autocomplete="new-password" required><span class="small muted">At least 8 characters with upper-case, lower-case and a number.</span></div>
        <div class="field"><label for="np2">Confirm new password</label><input class="input" id="np2" type="password" autocomplete="new-password" required></div>
        <button class="btn block" type="submit">Reset password</button></form>
      <p class="center small" style="margin-top:14px"><button class="link-btn" id="resend" type="button" disabled>Resend code (<span id="cd">60</span>s)</button> · <button class="link-btn" id="change" type="button">Use a different method</button></p>`);
    $("#code").focus();
    let left = 60; const t = setInterval(() => { left--; const cd = $("#cd"); if (!cd) return clearInterval(t); if (left <= 0) { clearInterval(t); const r = $("#resend"); if (r) { r.disabled = false; r.textContent = "Resend code"; } } else cd.textContent = left; }, 1000);
    $("#change").addEventListener("click", () => { clearInterval(t); stepRequest(); });
    $("#resend").addEventListener("click", async () => { try { await api("/auth/recovery/request", { method: "POST", body: { identifier, channel }, auth: false }); toast("A new code was sent (if the account exists).", "success"); stepCode(); } catch (e) { toast(e.message, "error"); } });
    $("#reset").addEventListener("submit", async (e) => {
      e.preventDefault(); $("#alert").innerHTML = "";
      const code = $("#code").value.trim(), np = $("#np").value, np2 = $("#np2").value;
      const err = !/^\d{6}$/.test(code) ? "Enter the 6-digit code." : !(np.length >= 8 && /[a-z]/.test(np) && /[A-Z]/.test(np) && /\d/.test(np)) ? "Password needs 8+ characters with upper-case, lower-case and a number." : np !== np2 ? "Passwords do not match." : "";
      if (err) return ($("#alert").innerHTML = `<div class="alert error">${err}</div>`);
      const b = e.currentTarget.querySelector("button"); b.disabled = true; b.innerHTML = '<span class="spinner"></span> Resetting…';
      try { await api("/auth/recovery/reset", { method: "POST", body: { identifier, code, newPassword: np }, auth: false }); clearInterval(t); toast("Password changed. Please sign in.", "success"); navigate("/login", { replace: true }); }
      catch (e2) { $("#alert").innerHTML = `<div class="alert error">${esc(e2.message)}</div>`; b.disabled = false; b.textContent = "Reset password"; }
    });
  };
  stepRequest();
}
