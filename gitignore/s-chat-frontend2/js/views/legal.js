import { html, raw, mount, $, appName } from "../core/util.js";
import { termsHtml, privacyHtml } from "./legal-content.js";
import { state } from "../core/state.js";

export function renderLegal(kind) {
  const isTerms = kind === "terms";
  const backTo = state.me ? "/app" : "/login";
  mount($("#app"), html`<div style="height:100%;overflow-y:auto"><div class="top-bar"><a class="brand" href="${backTo}" data-link style="margin:0;color:inherit"><img src="/assets/favicon.svg" alt="" width="30" height="30">${appName()}</a>
    <a class="btn secondary sm" href="${backTo}" data-link>← Back to app</a></div>
    <main class="legal-page card" style="margin-top:8px"><h1>${isTerms ? "Terms and Conditions" : "Privacy Policy"}</h1>${raw(isTerms ? termsHtml() : privacyHtml())}</main>
    <div class="legal-foot"><a href="/terms" data-link>Terms and Conditions</a><a href="/privacy" data-link>Privacy Policy</a></div></div>`);
  document.title = `${isTerms ? "Terms and Conditions" : "Privacy Policy"} · ${appName()}`;
}
