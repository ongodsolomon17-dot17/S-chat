import { bus, state } from "./state.js";

const cfg = () => window.S_CHAT_CONFIG;
const base = () => cfg().API_BASE_URL.replace(/\/$/, "");
const TOKEN_KEY = "schat_access_token";
let refreshing = null;

export const Session = {
  token: () => sessionStorage.getItem(TOKEN_KEY),
  set(t) { t ? sessionStorage.setItem(TOKEN_KEY, t) : sessionStorage.removeItem(TOKEN_KEY); },
  clear() { sessionStorage.removeItem(TOKEN_KEY); state.me = null; },
};

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function parse(res) {
  if (res.status === 204) return null;
  if (!(res.headers.get("content-type") || "").includes("application/json")) return null;
  try { return await res.json(); } catch { return null; }
}
const errMsg = (body, fallback) => (body && Array.isArray(body.messages) && body.messages[0]) || fallback;

/** Exchanges the HttpOnly refresh cookie for a new access token. Returns true on success. */
export function refreshSession() {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const res = await fetch(base() + "/auth/refresh", { method: "POST", headers: { "X-S-Chat-Client": "web" }, credentials: "include" });
      const body = await parse(res);
      if (!res.ok || !body?.accessToken) { Session.clear(); return false; }
      Session.set(body.accessToken);
      return true;
    } catch { return false; }
    finally { refreshing = null; }
  })();
  return refreshing;
}

/**
 * JSON request. Options: method, body (object), auth (default true), retryOn401 (default true).
 * On 401 it refreshes once and retries; if that fails it emits "auth:expired".
 */
export async function api(path, { method = "GET", body, auth = true, retryOn401 = true, headers = {} } = {}) {
  const h = { ...headers };
  if (body !== undefined && !(body instanceof FormData)) h["Content-Type"] = "application/json";
  if (auth && Session.token()) h.Authorization = "Bearer " + Session.token();
  let res;
  try {
    res = await fetch(base() + path, { method, headers: h, credentials: "include", body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body) });
  } catch {
    throw new ApiError("Can't reach the server. Check your connection and try again.", 0);
  }
  if (res.status === 401 && auth && retryOn401) {
    if (await refreshSession()) return api(path, { method, body, auth, retryOn401: false, headers });
    bus.emit("auth:expired");
    throw new ApiError("Your session has expired. Please sign in again.", 401);
  }
  const data = await parse(res);
  if (!res.ok) throw new ApiError(errMsg(data, res.status === 429 ? "Too many requests. Please wait a moment." : "Something went wrong. Please try again."), res.status);
  return data;
}

/** Multipart upload with progress (fetch cannot report upload progress, XHR can). */
export function upload(path, file, onProgress, fields = {}, retried = false) {
  return new Promise((resolve, reject) => {
    const fd = new FormData(); fd.append("file", file);
    for (const [k, v] of Object.entries(fields)) if (v != null && v !== "") fd.append(k, v);
    const xhr = new XMLHttpRequest();
    xhr.open("POST", base() + path); xhr.withCredentials = true;
    if (Session.token()) xhr.setRequestHeader("Authorization", "Bearer " + Session.token());
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress?.(e.loaded / e.total);
    xhr.onerror = () => reject(new ApiError("Upload failed. Check your connection.", 0));
    xhr.onload = async () => {
      let body = null; try { body = JSON.parse(xhr.responseText); } catch {}
      if (xhr.status === 401 && !retried && (await refreshSession())) return upload(path, file, onProgress, fields, true).then(resolve, reject);
      if (xhr.status < 200 || xhr.status >= 300) return reject(new ApiError(errMsg(body, "Upload failed."), xhr.status));
      resolve(body);
    };
    xhr.send(fd);
  });
}

export async function login(usernameOrEmail, password) {
  const r = await api("/auth/login", { method: "POST", body: { usernameOrEmail, password }, auth: false });
  Session.set(r.accessToken); return r;
}
export async function signup(payload) {
  const r = await api("/auth/signup", { method: "POST", body: payload, auth: false });
  Session.set(r.accessToken); return r;
}
export async function logout() {
  try { await fetch(base() + "/auth/logout", { method: "POST", headers: { "X-S-Chat-Client": "web" }, credentials: "include", keepalive: true }); } catch {}
  Session.clear();
}
