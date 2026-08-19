// Minimal service worker — exists solely to back showNotification()/notificationclick
// for the Notification-API-based alerts in js/notifications.js.
//
// This intentionally has NO 'push' event handler. Wiring that up is the extension point
// for true closed-app push notifications, and needs a matching backend (VAPID keys +
// a push-subscription table + a way to send RFC 8291 encrypted pushes) — see the
// redeploy guideline. Adding a 'push' listener here without that backend would do nothing.

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || "/S-chat-home.html";

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        // Reuse an already-open S-Chat tab instead of opening a new one, if one exists.
        if ("focus" in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      if (self.clients.openWindow) return self.clients.openWindow(url);
    })
  );
});
