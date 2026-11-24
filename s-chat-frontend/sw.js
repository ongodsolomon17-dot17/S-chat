/* S-Chat service worker — makes the app installable and gives a friendly offline page.
 * Strategy: NETWORK-FIRST for the app's own files (so a new deploy is picked up immediately and old/new
 * files are never mixed), with the cache only as an offline fallback. API calls and WebSockets are never
 * touched. Bump VERSION on every release to clear old caches. */
const VERSION = "2026-11-22.1";
const CACHE = "schat-shell-" + VERSION;
const PRECACHE = [
  "/",
  "/assets/apple-touch-icon.png",
  "/assets/favicon-16x16.png",
  "/assets/favicon-32x32.png",
  "/assets/icon-192.png",
  "/assets/icon-512.png",
  "/assets/icon-maskable-512.png",
  "/assets/logo-128.png",
  "/assets/logo-64.png",
  "/css/app.css",
  "/favicon.ico",
  "/js/config.js",
  "/js/core/api.js",
  "/js/core/calls.js",
  "/js/core/chats.js",
  "/js/core/install.js",
  "/js/core/state.js",
  "/js/core/status.js",
  "/js/core/ui.js",
  "/js/core/util.js",
  "/js/core/ws.js",
  "/js/main.js",
  "/js/router.js",
  "/js/views/ai-conversation.js",
  "/js/views/auth.js",
  "/js/views/conversation.js",
  "/js/views/legal-content.js",
  "/js/views/legal.js",
  "/js/views/pages.js",
  "/js/views/phone-field.js",
  "/js/views/profile-link.js",
  "/js/views/recovery.js",
  "/js/views/shell.js",
  "/js/views/status.js",
  "/manifest.webmanifest",
  "/offline.html"
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(PRECACHE.map((u) => new Request(u, { cache: "reload" })))).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith("schat-shell-") && k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== self.location.origin) return;      // API/ws/storage are cross-origin: never intercepted
  if (url.pathname === "/sw.js") return;

  if (req.mode === "navigate") {
    event.respondWith(fetch(req).catch(() => caches.match("/offline.html")));
    return;
  }
  event.respondWith(
    fetch(req).then((res) => {
      if (res && res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req))
  );
});
