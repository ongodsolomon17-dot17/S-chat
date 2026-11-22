// Shared API wrapper, authentication/session handling, refresh and toast notifications.
(function () {
  const API_BASE_URL = window.S_CHAT_CONFIG.API_BASE_URL.replace(/\/$/, "");

  const TOKEN_KEY = "schat_access_token";
  const USER_KEY = "schat_user";
  let refreshPromise = null;

  const Auth = {
    saveSession(authResponse) {
      if (!authResponse || !authResponse.accessToken) {
        throw new Error("Invalid authentication response from server.");
      }
      sessionStorage.setItem(TOKEN_KEY, authResponse.accessToken);
      if (authResponse.username) {
        localStorage.setItem(USER_KEY, JSON.stringify({
          username: authResponse.username,
          role: authResponse.role,
          publicId: authResponse.publicId
        }));
      }
    },
    getAccessToken() { return sessionStorage.getItem(TOKEN_KEY); },
    getUser() {
      try {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
      } catch { return null; }
    },
    isLoggedIn() { return !!this.getAccessToken(); },
    clearSession() {
      sessionStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    },
    logout() {
      fetch(API_BASE_URL + "/auth/logout", { method: "POST", headers: { "X-S-Chat-Client": "web" }, credentials: "include", keepalive: true }).finally(() => {
        this.clearSession();
        window.location.href = "S-chat-log-in.html";
      });
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
    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      try {
        const response = await fetch(API_BASE_URL + "/auth/refresh", {
          method: "POST",
          headers: { "X-S-Chat-Client": "web" },
          credentials: "include"
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
      response = await fetch(API_BASE_URL + path, { ...requestOptions, headers, credentials: "include" });
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
      response = await fetch(API_BASE_URL + path, { method: "POST", headers, body: formData, credentials: "include" });
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

  function apiUploadWithProgress(path, formData, onProgress, retried = false) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const token = Auth.getAccessToken();
      xhr.open("POST", API_BASE_URL + path, true);
      xhr.withCredentials = true;
      if (token) xhr.setRequestHeader("Authorization", "Bearer " + token);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && typeof onProgress === "function") onProgress(e.loaded / e.total);
      };
      xhr.onerror = () => reject(new Error("Can't reach the server. Check your connection and try again."));
      xhr.onload = async () => {
        let body = null;
        try { body = xhr.getResponseHeader("content-type")?.includes("application/json") ? JSON.parse(xhr.responseText) : null; } catch {}
        if (xhr.status === 401) {
          const refreshed = await refreshAccessToken();
          if (refreshed && !retried) {
            try { resolve(await apiUploadWithProgress(path, formData, onProgress, true)); } catch (e) { reject(e); }
          } else {
            Auth.clearSession();
            window.location.href = "S-chat-log-in.html";
            reject(new Error("Session expired. Please log in again."));
          }
          return;
        }
        if (xhr.status < 200 || xhr.status >= 300) {
          reject(new Error(errorFromBody(body, "Upload failed.")));
          return;
        }
        resolve(body);
      };
      xhr.send(formData);
    });
  }

  window.SChat = { Auth, apiFetch, apiUpload, apiUploadWithProgress, showToast, refreshAccessToken };
})();
