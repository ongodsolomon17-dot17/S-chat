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
