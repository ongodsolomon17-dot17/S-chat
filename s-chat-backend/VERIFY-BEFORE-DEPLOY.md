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

9. **Failover** (only if `NEON_DB_URL` is set): follow the staging test plan at the bottom of `docs/DATABASE_FAILOVER.md`.

## Highest-risk spots (look here first if something fails)
- `ChatMessageRepository.searchAll/searchInConversation` — JPQL `LIKE ... ESCAPE '!'`.
- `ChatMessageRepository.markDeliveredByIds` — bulk `UPDATE ... IN :ids`.
- `config/FailoverDataSource` + `DataSourceConfig` (new DataSource bean replaces Spring Boot's auto-configured one; Flyway uses its own `spring.flyway.url`).
- `V0_1__core_schema.sql` on a database that Hibernate already created (should be a no-op) and on an empty Neon DB (should build everything).
- `AuthService` constructor (now takes `LegalService`) and `SignupRequest` (two new required fields).
- `ChatWebSocketHandler` constructor (now takes `FriendService`).
