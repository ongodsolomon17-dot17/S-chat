// Shared fetch wrapper + token storage + toast notifications used across every page.
(function () {
  const API_BASE_URL = window.S_CHAT_CONFIG.API_BASE_URL;

  const TOKEN_KEY = "schat_access_token";
  const REFRESH_KEY = "schat_refresh_token";
  const USER_KEY = "schat_user";

  const Auth = {
    saveSession(authResponse) {
      // Access token kept in memory-friendly localStorage for PWA persistence
      // across reloads (same pattern as the rest of the S-TECH suite).
      localStorage.setItem(TOKEN_KEY, authResponse.accessToken);
      localStorage.setItem(REFRESH_KEY, authResponse.refreshToken);
      localStorage.setItem(USER_KEY, JSON.stringify({
        username: authResponse.username,
        role: authResponse.role
      }));
    },
    getAccessToken() {
      return localStorage.getItem(TOKEN_KEY);
    },
    getUser() {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    },
    isLoggedIn() {
      return !!this.getAccessToken();
    },
    clearSession() {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(USER_KEY);
    },
    logout() {
      this.clearSession();
      window.location.href = "S-chat-log-in.html";
    }
  };

  function showToast(message, type) {
    let stack = document.querySelector(".toast-stack");
    if (!stack) {
      stack = document.createElement("div");
      stack.className = "toast-stack";
      document.body.appendChild(stack);
    }
    const toast = document.createElement("div");
    toast.className = "toast " + (type || "");
    toast.textContent = message;
    stack.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
  }

  /**
   * Authenticated JSON fetch. Throws an Error with a user-facing `.message`
   * on any non-2xx response so callers can show it directly.
   */
  async function apiFetch(path, options = {}) {
    const headers = Object.assign(
      { "Content-Type": "application/json" },
      options.headers || {}
    );

    const token = Auth.getAccessToken();
    if (token && !options.skipAuth) {
      headers["Authorization"] = "Bearer " + token;
    }

    let response;
    try {
      response = await fetch(API_BASE_URL + path, {
        ...options,
        headers
      });
    } catch (networkErr) {
      throw new Error("Can't reach the server. Check your connection and try again.");
    }

    if (response.status === 401 && !options.skipAuth) {
      Auth.clearSession();
      window.location.href = "S-chat-log-in.html";
      throw new Error("Session expired. Please log in again.");
    }

    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;

    if (!response.ok) {
      const message = body && body.messages && body.messages.length
        ? body.messages[0]
        : "Something went wrong. Please try again.";
      throw new Error(message);
    }

    return body;
  }

  window.SChat = { Auth, apiFetch, showToast };
})();
