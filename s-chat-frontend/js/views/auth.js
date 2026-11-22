import { html, raw, $, mount, esc, appName } from "../core/util.js";
import { api, login, signup, ApiError } from "../core/api.js";
import { openModal, toast } from "../core/ui.js";
import { TERMS_SUMMARY, termsHtml } from "./legal-content.js";
import { phoneFieldHtml, readPhone } from "./phone-field.js";

const cfg = () => window.S_CHAT_CONFIG;
const hero = () => html`<aside class="auth-hero"><div class="brand" style="color:#fff"><img class="hero-logo" src="/assets/favicon.svg" alt="">${appName()}</div>
  <h1>${cfg().TAGLINE}</h1><p>Chat one-to-one in real time, share files, make voice and video calls, and give friends a personal link to start talking to you.</p>
  <ul><li>Real-time messages with read receipts</li><li>Your own shareable chat link</li><li>Voice &amp; video calls</li><li>Block and report tools you control</li></ul></aside>`;

function fieldErr(form, name, msg) {
  const f = $(`[name="${name}"]`, form); if (!f) return;
  f.classList.toggle("invalid", !!msg);
  const holder = f.closest(".field"); holder.querySelector(".err")?.remove();
  if (msg) { const s = document.createElement("span"); s.className = "err"; s.textContent = msg; holder.appendChild(s); }
}

export function renderLogin({ onDone, next }) {
  mount($("#app"), html`<div class="auth">${hero()}<main class="auth-panel"><div class="auth-card">
    <div class="brand"><img src="/assets/favicon.svg" alt="">${appName()}</div>
    <h2>Welcome back</h2><p class="muted">Sign in to continue to your chats.</p>
    <div id="form-alert"></div>
    <form id="login-form" novalidate>
      <div class="field"><label for="l-id">Username or email</label><input class="input" id="l-id" name="id" autocomplete="username" required></div>
      <div class="field"><label for="l-pw">Password</label><input class="input" id="l-pw" name="pw" type="password" autocomplete="current-password" required></div>
      <button class="btn block" type="submit">Sign in</button>
      <p class="center small" style="margin:12px 0 0"><a href="/forgot" data-link>Forgot your password?</a></p>
    </form>
    <p class="center small" style="margin-top:18px">New here? <a href="/signup" data-link>Create an account</a></p>
    <p class="center small muted"><a href="/terms" data-link>Terms</a> · <a href="/privacy" data-link>Privacy</a></p>
  </div></main></div>`);
  const form = $("#login-form");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = form.id.value.trim(), pw = form.pw.value;
    $("#form-alert").innerHTML = "";
    if (!id || !pw) return ($("#form-alert").innerHTML = '<div class="alert error">Enter your username/email and password.</div>');
    const btn = form.querySelector("button"); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> Signing in…';
    try { await login(id, pw); await onDone(next); }
    catch (err) {
      $("#form-alert").innerHTML = `<div class="alert error">${esc(err.message)}</div>`;
      btn.disabled = false; btn.textContent = "Sign in";
    }
  });
  form.id.focus();
}

/** The Terms popup: summary + checkbox; "See more" swaps in the full document with a Back button. */
function openTermsPopup({ onAgree }) {
  let checked = false, version = null, busy = false;
  const summaryBody = () => html`${raw(TERMS_SUMMARY())}
    <p><button class="link-btn" id="see-more" type="button">See more — read the full Terms and Conditions →</button></p>
    <p class="small muted">Please also read our <a href="/privacy" target="_blank" rel="noopener">Privacy Policy</a> (opens in a new tab).</p>
    <label class="check" style="margin-top:12px"><input type="checkbox" id="agree-box" ${checked ? raw("checked") : ""}><span>I have read and agree to the <b>Terms and Conditions</b> and the <b>Privacy Policy</b>.</span></label>
    <div id="terms-err"></div>`;
  const summaryFoot = () => html`<button class="btn secondary" id="decline" type="button">Cancel</button><button class="btn" id="agree" type="button" ${checked ? "" : raw("disabled")}>Agree &amp; create account</button>`;
  const m = openModal({ title: "Terms and Conditions", body: summaryBody(), footer: summaryFoot(), labelledBy: "terms-title" });
  const showSummary = () => m.set({ title: "Terms and Conditions", body: summaryBody(), footer: summaryFoot() });
  const showFull = () => m.set({ title: "Terms and Conditions — full document", body: raw(termsHtml()), footer: html`<button class="btn secondary" id="back" type="button">← Back</button>` });
  m.el.addEventListener("change", (e) => { if (e.target.id === "agree-box") { checked = e.target.checked; $("#agree", m.el).disabled = !checked; } });
  m.el.addEventListener("click", async (e) => {
    const id = e.target.closest("button")?.id;
    if (id === "see-more") showFull();
    else if (id === "back") showSummary();
    else if (id === "decline") m.close();
    else if (id === "agree" && checked && !busy) {
      busy = true; const btn = $("#agree", m.el); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> Creating account…';
      try {
        version = (await api("/public/legal", { auth: false })).termsVersion;
        await onAgree(version);
        m.close();
      } catch (err) {
        busy = false; btn.disabled = false; btn.textContent = "Agree & create account";
        $("#terms-err").innerHTML = `<div class="alert error" style="margin-top:12px">${esc(err.message)}</div>`;
        if (err instanceof ApiError && err.status === 400) { /* validation or stale terms version: user can retry */ }
      }
    }
  });
}

export function renderSignup({ onDone }) {
  mount($("#app"), html`<div class="auth">${hero()}<main class="auth-panel"><div class="auth-card">
    <div class="brand"><img src="/assets/favicon.svg" alt="">${appName()}</div>
    <h2>Create your account</h2><p class="muted">Takes less than a minute.</p>
    <div id="form-alert"></div>
    <form id="signup-form" novalidate>
      <div class="field"><label for="s-user">Username</label><input class="input" id="s-user" name="username" autocomplete="username" maxlength="24" required><span class="small muted">3–24 characters: letters, numbers, dots, underscores. This becomes your chat link.</span></div>
      <div class="field"><label for="s-email">Email</label><input class="input" id="s-email" name="email" type="email" autocomplete="email" required></div>
      <div class="field"><label for="s-phone-num">Phone <span class="muted">(optional)</span></label>${phoneFieldHtml("s-phone")}<span class="small muted">Used only to recover your account by SMS or WhatsApp.</span></div>
      <div class="field"><label for="s-pw">Password</label><input class="input" id="s-pw" name="pw" type="password" autocomplete="new-password" required><span class="small muted">At least 8 characters with upper-case, lower-case and a number.</span></div>
      <div class="field"><label for="s-pw2">Confirm password</label><input class="input" id="s-pw2" name="pw2" type="password" autocomplete="new-password" required></div>
      <button class="btn block" type="submit">Continue</button>
    </form>
    <p class="center small" style="margin-top:18px">Already have an account? <a href="/login" data-link>Sign in</a></p>
  </div></main></div>`);
  const form = $("#signup-form");
  const validate = () => {
    const v = { username: form.username.value.trim(), email: form.email.value.trim(), pw: form.pw.value, pw2: form.pw2.value };
    const errs = {};
    if (!/^[a-zA-Z0-9_.]{3,24}$/.test(v.username)) errs.username = "Use 3–24 letters, numbers, dots or underscores.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)) errs.email = "Enter a valid email address.";
    if (!(v.pw.length >= 8 && /[a-z]/.test(v.pw) && /[A-Z]/.test(v.pw) && /\d/.test(v.pw))) errs.pw = "Needs 8+ characters with upper-case, lower-case and a number.";
    if (v.pw2 !== v.pw) errs.pw2 = "Passwords do not match.";
    const ph = readPhone(form, "s-phone");
    const phoneErr = $("#s-phone-num").closest(".field"); phoneErr.querySelector(".err")?.remove();
    if (ph.error) { errs.phone = ph.error; const s = document.createElement("span"); s.className = "err"; s.textContent = ph.error; phoneErr.appendChild(s); }
    v.phone = ph.value;
    for (const n of ["username", "email", "pw", "pw2"]) fieldErr(form, n, errs[n]);
    return Object.keys(errs).length ? null : v;
  };
  form.addEventListener("submit", (e) => {
    e.preventDefault(); $("#form-alert").innerHTML = "";
    const v = validate(); if (!v) return;
    // Nothing is sent until the user explicitly agrees in the popup.
    openTermsPopup({
      onAgree: async (termsVersion) => {
        try {
          await signup({ username: v.username, email: v.email, password: v.pw, phoneNumber: v.phone, acceptedTerms: true, termsVersion });
        } catch (err) { throw err; }
        await onDone();
      },
    });
  });
  form.username.focus();
}
