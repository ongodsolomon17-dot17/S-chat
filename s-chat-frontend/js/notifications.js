// Real OS-level notifications via the standard Notification API + a minimal service
// worker. This shows genuine native OS notification popups whenever the S-Chat tab/app
// is running but not focused — the same mechanism Gmail, Slack's web app, etc. use for
// their web notifications.
//
// What this deliberately does NOT do: wake the app up when it's fully closed/killed.
// That needs the Push API with a VAPID-signed subscription and a backend that can send
// encrypted pushes (RFC 8291) — real infrastructure work (key generation, a subscription
// table, a push-sending library) that's a deploy-time decision, not something to bolt on
// silently. See the redeploy guideline for what adding that later would involve.
window.SChatNotify = (function () {
  let swRegistration = null;

  async function registerServiceWorker() {
    if (!("serviceWorker" in navigator)) return null;
    try {
      swRegistration = await navigator.serviceWorker.register("/sw.js");
      return swRegistration;
    } catch {
      return null; // notifications still work via new Notification(), just without SW persistence
    }
  }

  function permissionState() {
    if (!("Notification" in window)) return "unsupported";
    return Notification.permission; // "default" | "granted" | "denied"
  }

  async function requestPermission() {
    if (!("Notification" in window)) return "unsupported";
    if (Notification.permission !== "default") return Notification.permission;
    try {
      return await Notification.requestPermission();
    } catch {
      return "denied";
    }
  }

  async function notify(title, body, opts) {
    if (permissionState() !== "granted") return;
    const options = {
      body,
      icon: "images/MY S.png",
      badge: "images/MY S.png",
      tag: (opts && opts.tag) || undefined, // same tag replaces/collapses instead of stacking
      data: (opts && opts.data) || {}
    };
    // Prefer the service-worker-backed notification (survives better on mobile Chrome);
    // fall back to a plain Notification if no SW registration is available.
    if (swRegistration && swRegistration.showNotification) {
      try {
        await swRegistration.showNotification(title, options);
        return;
      } catch { /* fall through to plain Notification */ }
    }
    try {
      const n = new Notification(title, options);
      n.onclick = () => {
        window.focus();
        if (options.data && options.data.url) window.location.href = options.data.url;
        n.close();
      };
    } catch { /* Notification constructor can throw on some mobile browsers — ignore */ }
  }

  // Only worth showing a notification if the person genuinely isn't looking at the tab.
  function shouldNotify() {
    return document.hidden || !document.hasFocus();
  }

  return { registerServiceWorker, permissionState, requestPermission, notify, shouldNotify };
})();
