# S-Chat — Project Status & Roadmap

## What's in this delivery (Phase 1)

**Backend** (`/backend`) — Spring Boot, Java 21
- `POST /api/auth/signup` — create account (username/email/password), returns JWT pair
- `POST /api/auth/login` — login by username or email, returns JWT pair
- BCrypt password hashing (cost 12), JWT access (15 min) + refresh (7 day) tokens
- Progressive account lockout: 5 failed logins → 15-minute lock
- Per-IP rate limiting on auth endpoints (10 req/min)
- CORS locked to your Vercel origin(s), CSP headers, stateless sessions
- Role model ready for USER / ADMIN / SUPERADMIN (superadmin endpoints scaffolded but not yet implemented — see roadmap)
- Dockerfile + `render.yaml` ready to deploy as-is

**Frontend** (`/frontend`) — rebuilt HTML/CSS/JS
- Fixed invalid HTML structure (content was sitting inside `<head>` on 6 of 9 pages)
- Fixed invalid CSS (`@import` nested inside a rule was silently dropped; duplicate `@keyframes`)
- Removed `S-chat-staus.html` (typo duplicate of `S-chat-status.html` with diverging content)
- Removed `S-chat-already-had-an-account.html` (duplicate login form pointing at a login.html that didn't exist — merged into `S-chat-log-in.html`)
- Signup form previously had no username or password field at all — rebuilt to actually support login (username, email, password). The original name/age/phone/country-code fields are dropped for now since nothing used them; happy to add a profile-details step back once `/api/users/profile` exists.
- Chat composer no longer POSTs to a nonexistent `.java` servlet — it's wired for the real send endpoint (stubbed until phase 2 ships)
- Added `js/config.js`, `js/api.js`, `js/auth.js`, `js/nav.js` — shared fetch client, token storage, form wiring, active-nav highlighting, route guarding for logged-out users
- Added `rel="noopener noreferrer"` to the external Gemini link
- New `S-chat-superadmin.html` — professional dashboard shell, gated by real login + role check

## What you need to do before this runs

1. **Rename your video file.** `images/WhatsApp Video 2026-08-01 at 12.14.30 AM.mp4` has spaces, which breaks reliably as a URL on Vercel. Rename it to `images/s-tech-ai-orb.mp4` (already referenced under that name in the new HTML) and re-upload.
2. **Add your real image/icon assets** to `frontend/images/` and `frontend/my icons/` — I didn't have the binary files, only filenames, so the folders are currently empty in this delivery.
3. **Set environment variables on Render** (see `backend/.env.example`): `JWT_SECRET`, `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `FRONTEND_ORIGINS`, `RESEND_API_KEY`.
4. **Update `frontend/js/config.js`** with your real Render backend URL once deployed.
5. **Supabase**: create the project, grab the pooler connection string (port 6543) for `SUPABASE_DB_URL` — the app creates its own `app_users` table on first boot (`ddl-auto: update`).

## Deploy steps

```bash
# Backend
cd backend
git init && git add . && git commit -m "S-Chat backend: auth phase 1"
git remote add origin https://github.com/ongodsolomon17-dot17/S-Chat-Backend.git
git push -u origin main
# then: connect the repo on Render, it will pick up render.yaml + Dockerfile

# Frontend
cd frontend
git init && git add . && git commit -m "S-Chat frontend: fixed + wired to backend"
git remote add origin https://github.com/ongodsolomon17-dot17/S-Chat-Frontend.git
git push -u origin main
# then: import the repo on Vercel as a static site
```

## Roadmap (in the order we agreed)

1. ✅ **Auth** — signup/login/JWT (this delivery)
2. **Real-time chat (WebSocket/STOMP)** — message persistence table, `/ws` endpoint, `js/chats.js` client, delivery/read receipts
3. **File attachments** — server-side MIME/magic-byte validation (not just extension), storage via Supabase Storage
4. **Superadmin API** — `/api/superadmin/users`, `/api/superadmin/stats`, ban/unban, role changes — to back the dashboard shell already built
5. **Resend integration** — email verification on signup, password reset flow
6. **Status & Calls features** — currently placeholder pages

Tell me when you're ready for #2 and I'll build the WebSocket layer + wire `S-chat-chatpage.html` to it end-to-end.
