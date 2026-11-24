# Backend verification checklist (run on your machine or in CI)

The backend changes in this delivery were written and syntax-checked but **never compiled or run** by the author
(no Maven or network was available). Run these before you deploy:

```bash
cd backend
mvn -q -DskipTests package        # 1. must compile — fix any error it reports
java -jar target/*.jar            # 2. boots against Neon (env vars set); Flyway should apply V0_1, V8, V9
curl localhost:8080/actuator/health
```

## What to confirm
1. **Boot log** shows Flyway migrating (`Successfully applied N migrations`) with no errors, and Hibernate starts.
2. `GET /api/public/legal` returns the two version strings; `GET /api/public/status` returns `{"database":"primary"}`.
3. `POST /api/auth/signup` **without** `acceptedTerms:true` → 400; with the current `termsVersion` → 200; check `terms_acceptances` has a row.
4. `GET /api/public/users/<username>` (no token) → profile; unknown name → 404.
5. Two accounts: send a message over `/ws`; edit it with `PATCH /api/chat/messages/{id}`; `GET /api/chat/search?q=...`.
6. Open two WebSocket sessions: you should receive `presence`, `typing`, and `delivered` frames.
7. `POST /api/reports` then `GET /api/admin/reports` with an ADMIN token (promote with SQL: `UPDATE app_users SET role='ADMIN' WHERE username='you';`).
8. After everything works, set `JPA_DDL_AUTO=validate` and redeploy; it must still boot (proves entities match the migrations).

9. **Recovery (email first):** set `RESEND_API_KEY` + `RESEND_FROM_ADDRESS`; `GET /api/auth/recovery/channels` → `{"channels":["EMAIL"]}`; `POST /api/auth/recovery/request` with your username/email → an email arrives; `POST /api/auth/recovery/reset` with the code sets a new password and old sessions stop refreshing. Logs show `Account recovery via EMAIL: ENABLED`. Repeat for SMS/WhatsApp once Twilio is configured.
10. **STech AI:** with `GEMINI_API_KEY` set, `GET /api/ai/summary` → `available:true`; `POST /api/ai/chat` replies; `GET /api/ai/history` rows carry `createdAt`.
11. **Status (regression check):** `POST /api/status` (text), `GET /api/status/feed` from a friend, `POST /api/status/{id}/reply` — these come from the **original** `StatusController`, which an earlier version of this delivery accidentally overwrote; it is restored now.
12. **Friend flow:** with two accounts, `POST /api/friends/request {identifier:"<username>",viaId:false}`; the other account's open WebSocket receives `friend_request`; accepting makes the first account's socket receive `friend_accepted` and both receive `presence`.
13. **Failover** (only if `NEON_DB_URL` is set): follow the staging test plan at the bottom of `docs/DATABASE_FAILOVER.md`.

## Highest-risk spots (look here first if something fails)
- `ChatMessageRepository.searchAll/searchInConversation` — JPQL `LIKE ... ESCAPE '!'`.
- `ChatMessageRepository.markDeliveredByIds` — bulk `UPDATE ... IN :ids`.
- `service/FriendService` constructor (now also takes `WebSocketSessionRegistry`) and its after-commit push helpers; `resolveTarget` username fallback.
- `service/RecoveryService` (`@Transactional(noRollbackFor=...)` so wrong-guess counters persist; afterCommit async delivery) and the three senders.
- `AiChatMessage` record gained a third component (a 2-arg constructor was kept).
- `config/FailoverDataSource` + `DataSourceConfig` (new DataSource bean replaces Spring Boot's auto-configured one; Flyway uses its own `spring.flyway.url`).
- `V0_1__core_schema.sql` on a database that Hibernate already created (should be a no-op) and on an empty Neon DB (should build everything).
- `AuthService` constructor (now takes `LegalService`) and `SignupRequest` (two new required fields).
- `ChatWebSocketHandler` constructor (now takes `FriendService`).
