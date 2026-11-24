// "Download the app": S-Chat is an installable web app (PWA). Chrome/Edge/Android offer a real install prompt,
// iOS needs Share → Add to Home Screen. This module captures the prompt event and explains the manual steps.
import { bus } from "./state.js";
import { toast, openModal } from "./ui.js";
import { html, raw, appName } from "./util.js";

let deferred = null;

window.addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); deferred = e; bus.emit("install:changed"); });
window.addEventListener("appinstalled", () => { deferred = null; toast(appName() + " installed", "success"); bus.emit("install:changed"); });

export const isStandalone = () => matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
export const isIos = () => /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

/** "installed" | "available" (one-tap install) | "ios" (manual) | "manual" (browser menu) */
export function installState() {
  if (isStandalone()) return "installed";
  if (deferred) return "available";
  return isIos() ? "ios" : "manual";
}

export async function promptInstall() {
  if (!deferred) return null;
  deferred.prompt();
  const choice = await deferred.userChoice.catch(() => ({ outcome: "dismissed" }));
  deferred = null; bus.emit("install:changed");
  return choice.outcome;
}

export function showInstallHelp() {
  const st = installState();
  const steps = st === "ios"
    ? html`<ol><li>Open S-Chat in <b>Safari</b>.</li><li>Tap the <b>Share</b> button (square with an arrow).</li><li>Choose <b>Add to Home Screen</b>, then tap <b>Add</b>.</li></ol>`
    : html`<ol><li><b>Android (Chrome):</b> menu ⋮ → <b>Install app</b> (or <b>Add to Home screen</b>).</li><li><b>Windows / Mac / Linux (Chrome or Edge):</b> click the install icon at the right of the address bar, or menu → <b>Install S-Chat</b>.</li><li><b>iPhone / iPad:</b> use Safari → Share → <b>Add to Home Screen</b>.</li></ol>`;
  const m = openModal({
    title: "Get the " + appName() + " app",
    body: html`${st === "installed" ? html`<p>✅ ${appName()} is already installed on this device.</p>` : st === "available" ? html`<p>Install ${appName()} on this device for a faster, full-screen experience with its own icon.</p>` : html`<p>Add ${appName()} to your device like an app — it opens full-screen and gets its own icon.</p>${steps}`}`,
    footer: st === "available" ? html`<button class="btn secondary" data-close>Not now</button><button class="btn" id="do-install">Install now</button>` : html`<button class="btn" data-close>Got it</button>`,
  });
  m.el.querySelector("#do-install")?.addEventListener("click", async () => { m.close(); await promptInstall(); });
}
