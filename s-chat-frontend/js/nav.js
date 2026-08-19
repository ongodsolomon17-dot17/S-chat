// Highlights the current page in the bottom nav and guards logged-in-only pages.
(function () {
  const PROTECTED_PAGES = [
    "S-chat-home.html",
    "S-chat-status.html",
    "S-chat-calls.html",
    "S-chat-settings.html",
    "S-chat-chatpage.html"
  ];

  const currentPage = window.location.pathname.split("/").pop();

  if (PROTECTED_PAGES.includes(currentPage) && window.SChat && !SChat.Auth.isLoggedIn()) {
    window.location.href = "S-chat-log-in.html";
    return;
  }

  document.querySelectorAll(".pages-btn-container a").forEach((link) => {
    const href = link.getAttribute("href");
    if (href === currentPage) {
      const btn = link.querySelector(".primary-btn");
      if (btn) btn.classList.add("nav-active");
    }
  });
})();

/* Global calling layer: loads on authenticated pages so incoming calls can ring
   even when the user is not currently viewing a chat. */
(function loadSChatCalls() {
  if (!window.SChat || !SChat.Auth.isLoggedIn()) return;
  if (document.querySelector("script[data-schat-calls]")) return;

  const loadCalls = () => {
    if (document.querySelector("script[data-schat-calls]")) return;
    const script = document.createElement("script");
    script.src = "js/calls.js";
    script.defer = true;
    script.dataset.schatCalls = "true";
    document.body.appendChild(script);
  };

  if (typeof window.SChatWS !== "undefined") {
    loadCalls();
  } else {
    const wsScript = document.createElement("script");
    wsScript.src = "js/ws-chat.js";
    wsScript.onload = loadCalls;
    document.body.appendChild(wsScript);
  }
})();
