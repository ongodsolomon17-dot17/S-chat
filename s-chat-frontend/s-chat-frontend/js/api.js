// Shared API wrapper, authentication/session handling, refresh and toast notifications.
(function () {
  const API_BASE_URL = window.S_CHAT_CONFIG.API_BASE_URL.replace(/\/$/, "");

  const TOKEN_KEY = "schat_access_token";
  const REFRESH_KEY = "schat_refresh_token";
  const USER_KEY = "schat_user";
  let refreshPromise = null;

  const Auth = {
    saveSession(authResponse) {
      if (!authResponse || !authResponse.accessToken) {
        throw new Error("Invalid authentication response from server.");
      }
      localStorage.setItem(TOKEN_KEY, authResponse.accessToken);
      if (authResponse.refreshToken) localStorage.setItem(REFRESH_KEY, authResponse.refreshToken);
      if (authResponse.username) {
        localStorage.setItem(USER_KEY, JSON.stringify({
          username: authResponse.username,
          role: authResponse.role,
          publicId: authResponse.publicId
        }));
      }
    },
    getAccessToken() { return localStorage.getItem(TOKEN_KEY); },
    getRefreshToken() { return localStorage.getItem(REFRESH_KEY); },
    getUser() {
      try {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
      } catch { return null; }
    },
    isLoggedIn() { return !!this.getAccessToken(); },
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

  async function parseBody(response) {
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) return null;
    try { return await response.json(); } catch { return null; }
  }

  function errorFromBody(body, fallback) {
    if (body && Array.isArray(body.messages) && body.messages.length) return body.messages[0];
    return fallback;
  }

  async function refreshAccessToken() {
    const refreshToken = Auth.getRefreshToken();
    if (!refreshToken) return false;

    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      try {
        const response = await fetch(API_BASE_URL + "/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken })
        });
        const body = await parseBody(response);
        if (!response.ok || !body || !body.accessToken) {
          Auth.clearSession();
          return false;
        }
        Auth.saveSession(body);
        return true;
      } catch {
        Auth.clearSession();
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
    return refreshPromise;
  }

  async function apiFetch(path, options = {}) {
    const requestOptions = { ...options };
    const skipAuth = !!requestOptions.skipAuth;
    delete requestOptions.skipAuth;
    const allowRefresh = !skipAuth && !requestOptions._retriedAfterRefresh;
    delete requestOptions._retriedAfterRefresh;

    const headers = Object.assign({ "Content-Type": "application/json" }, requestOptions.headers || {});
    const token = Auth.getAccessToken();
    if (token && !skipAuth) headers.Authorization = "Bearer " + token;

    let response;
    try {
      response = await fetch(API_BASE_URL + path, { ...requestOptions, headers });
    } catch {
      throw new Error("Can't reach the server. Check your connection and try again.");
    }

    if (response.status === 401 && allowRefresh) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return apiFetch(path, { ...options, _retriedAfterRefresh: true });
      }
      Auth.clearSession();
      window.location.href = "S-chat-log-in.html";
      throw new Error("Session expired. Please log in again.");
    }

    const body = await parseBody(response);
    if (!response.ok) throw new Error(errorFromBody(body, "Something went wrong. Please try again."));
    return body;
  }

  async function apiUpload(path, formData, retried = false) {
    const token = Auth.getAccessToken();
    const headers = token ? { Authorization: "Bearer " + token } : {};
    let response;
    try {
      response = await fetch(API_BASE_URL + path, { method: "POST", headers, body: formData });
    } catch {
      throw new Error("Can't reach the server. Check your connection and try again.");
    }
    if (response.status === 401) {
      const refreshed = await refreshAccessToken();
      if (refreshed && !retried) return apiUpload(path, formData, true);
      Auth.clearSession();
      window.location.href = "S-chat-log-in.html";
      throw new Error("Session expired. Please log in again.");
    }
    const body = await parseBody(response);
    if (!response.ok) throw new Error(errorFromBody(body, "Upload failed."));
    return body;
  }

  window.SChat = { Auth, apiFetch, apiUpload, showToast, refreshAccessToken };
})();
