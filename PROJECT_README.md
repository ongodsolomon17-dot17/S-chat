# S-Chat — Project Status & Roadmap

## What's in this delivery

**Backend** (`/backend`) — Spring Boot, Java 21
- Auth: `POST /api/auth/signup`, `POST /api/auth/login` — BCrypt hashing, JWT access (15min) + refresh (7day), progressive lockout (5 fails → 15min), per-IP rate limiting
- Profile: `GET/PATCH /api/users/me`, `POST /api/users/me/profile-picture`, `DELETE /api/users/me` (soft delete)
- Friends: `POST /api/friends/request`, `/accept`, `/decline`, `GET /api/friends`, `GET /api/friends/requests/incoming`
- Chat: WebSocket `/ws` (JWT via query param) for real-time messaging + `GET /api/chat/history/{friendId}` + `POST /api/chat/attachment`
- Status: `POST /api/status`, `GET /api/status/feed`, `GET /api/status/mine`, `DELETE /api/status/{id}` — 24h auto-expiring
- File storage via Supabase Storage (profile pictures, status media, chat attachments) — validated by MIME type and size server-side
- Role model (USER/ADMIN/SUPERADMIN), CORS locked to your Vercel origin(s), CSP headers, non-root Docker user
- Dockerfile + `render.yaml` ready to deploy as-is

**How the features you asked for map to the API:**
- *Add by contact (default) or by ID*: `POST /api/friends/request` with `viaId: false` searches phone/email; `viaId: true` searches the user's S-Chat ID. A user can flip `addByIdOnly` in their own settings to reject contact-based adds entirely.
- *Approve/decline adds*: the `approvalRequired` toggle — off means new requests auto-accept, on means they sit in `/api/friends/requests/incoming` until accepted/declined.
- *Editable ID*: `PATCH /api/users/me` with `publicId` — this is a separate field from the internal database ID, never the same value, checked for uniqueness on change.
- *Delete account, keep history*: `DELETE /api/users/me` sets `deleted=true` and `accountEnabled=false` on the User row only. `chat_messages`, `friend_requests`, and `status_posts` rows are never touched or cascaded — friends still see the full conversation, with the deleted party shown as "Deleted User."

**Frontend** (`/frontend`) — rebuilt HTML/CSS/JS
- Fixed invalid HTML structure (content was sitting inside `<head>` on 6 of 9 pages) and invalid CSS (`@import` nested inside a rule, duplicate `@keyframes`)
- Removed duplicate/dead pages (`S-chat-staus.html`, `S-chat-already-had-an-account.html`)
- Signup rebuilt to actually support login (username, email, password + optional phone)
- Fully responsive: fluid `clamp()`-based sizing from phones through tablets, and a centered 640px "app shell" on laptop/desktop so the UI doesn't stretch edge-to-edge
- Draggable AI orb (`js/orb-drag.js`) — pointer-based drag with click-vs-drag detection, position persists across pages via localStorage; still opens Gemini on a plain tap
- `S-chat-home.html` — real friend list from `/api/friends`, click-through to chat
- `S-chat-chatpage.html` — real-time messaging over WebSocket, history load, image/video attachments
- `S-chat-settings.html` — profile picture upload (replaces the header logo), editable S-Chat ID, "add by ID only" and "approve requests" toggles, phone number, add-a-friend form (contact/ID tabs), incoming requests with accept/decline, delete account with confirmation
- `S-chat-status.html` — post a photo/video from device storage with an optional caption, 24h-expiring feed of friends' statuses, tap-to-view lightbox
- `S-chat-superadmin.html` — professional dashboard shell gated by real login + role check, ready for the admin API endpoints in phase 2

## What you need to do before this runs

1. **Rename your video file.** `images/WhatsApp Video 2026-08-01 at 12.14.30 AM.mp4` has spaces, which breaks reliably as a URL on Vercel. Rename it to `images/s-tech-ai-orb.mp4` and re-upload.
2. **Add your real image/icon assets** to `frontend/images/` and `frontend/my icons/` — I didn't have the binary files, only filenames.
3. **Create a Supabase Storage bucket** named `s-chat-media` (or your own name, matching `SUPABASE_STORAGE_BUCKET`) and set it to public read.
4. **Set environment variables on Render** (see `backend/.env.example`): `JWT_SECRET`, `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `FRONTEND_ORIGINS`, `RESEND_API_KEY`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` (the **service_role** key — never expose this client-side).
5. **Update `frontend/js/config.js`** with your real Render backend URL and WebSocket URL (`wss://...`) once deployed.

## Deploy steps

```bash
# Backend
cd backend
git init && git add . && git commit -m "S-Chat backend: auth + friends + chat + status"
git remote add origin https://github.com/ongodsolomon17-dot17/S-Chat-Backend.git
git push -u origin main
# then: connect the repo on Render, it will pick up render.yaml + Dockerfile

# Frontend
cd frontend
git init && git add . && git commit -m "S-Chat frontend: friends, real-time chat, status, settings"
git remote add origin https://github.com/ongodsolomon17-dot17/S-Chat-Frontend.git
git push -u origin main
# then: import the repo on Vercel as a static site
```

## Roadmap

1. ✅ Auth — signup/login/JWT
2. ✅ Real-time chat, friends/contacts, status posts, profile pictures, soft delete
3. **Superadmin API** — `/api/superadmin/users`, `/api/superadmin/stats`, ban/unban, role changes — to back the dashboard shell already built
4. **Resend integration** — email verification on signup, password reset flow
5. **Calls** — you said this can wait; happy to scope it (WebRTC signaling over the existing WebSocket) whenever you're ready
6. **"S-Tech" AI assistant** — turning the draggable orb into a named assistant backed by the Gemini API, once you're ready for that phase

Tell me when you're ready for #3 (superadmin API) or #6 (Gemini-backed orb) and I'll build it.



## Production deployment notes (current S-Chat setup)

- Frontend entry page: `frontend/index.html`.
- Frontend production URL: `https://s-chat-zeta.vercel.app`.
- Backend production URL: `https://s-chat-u8fs.onrender.com`.
- `frontend/js/config.js` is the single frontend API/WebSocket URL source.
- Render must contain `FRONTEND_ORIGINS=https://s-chat-zeta.vercel.app`.
- Render must contain a strong `JWT_SECRET` of at least 32 UTF-8 bytes.
- Supabase service-role credentials remain backend-only; never copy them into frontend JavaScript.
- The frontend uses short-lived access tokens plus refresh tokens.
- WebSocket authentication uses the access token in the handshake query because browser WebSocket APIs do not provide a normal Authorization-header option.
- Do not upload `.env` files or real credentials to GitHub.

## Deep security/reliability hardening (2026-08-20)

- Refresh tokens are now `HttpOnly`, `Secure`, `SameSite=None` cookies and are no longer exposed to frontend JavaScript/localStorage.
- Access tokens are kept in `sessionStorage` instead of long-lived `localStorage`.
- Refresh/logout require the custom `X-S-Chat-Client: web` header to reduce CSRF risk.
- WebSocket messages are capped at 64 KB and rate-limited per authenticated connection.
- Call signaling is accepted only after the call has been accepted by the callee; stale calls expire after 90 seconds.
- Caller display names are taken from the authenticated account, not trusted from client input.
- Chat attachment URLs must belong to the configured S-Chat Supabase bucket before they can be persisted as messages.
- Profile pictures are restricted to images; status uploads are restricted to images, video, and audio.
- Status text, captions, and background colors are validated server-side.
- TURN credentials are generated server-side using the coturn shared-secret REST credential mechanism. No TURN secret or long-lived TURN credential belongs in frontend JavaScript.
- Frontend calls obtain short-lived ICE server credentials from `/api/calls/ice-servers` before creating the `RTCPeerConnection`.

### Production TURN environment variables

Set these on Render only:

```text
TURN_URLS=turn:turn.yourdomain.com:3478?transport=udp,turns:turn.yourdomain.com:5349?transport=tcp
TURN_SHARED_SECRET=<same secret configured in coturn>
TURN_CREDENTIAL_TTL_SECONDS=3600
```

Do not put `TURN_SHARED_SECRET` or generated TURN credentials into Vercel/frontend files.

## Final security hardening notes

- Refresh tokens are HttpOnly cookies and are version-revocable on logout.
- WebSocket authentication uses short-lived, one-time tickets rather than putting the access JWT in the WebSocket URL.
- WebSocket frames are size/rate limited and call signaling is authorized against the persisted call record.
- Chat attachment URLs are server-validated as S-Chat-managed storage URLs.
- Supabase service credentials remain backend-only.
- Production Supabase tables/storage should still be reviewed against the current RLS/access-control policies before enabling any direct browser Data API access.
- Once the production schema has a complete Flyway baseline, set `JPA_DDL_AUTO=validate` instead of `update`.
