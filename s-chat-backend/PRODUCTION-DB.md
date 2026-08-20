# S-Chat production database hardening

The current application deliberately keeps `JPA_DDL_AUTO=update` as the safe default because the live
Supabase database already contains the existing S-Chat schema. Switching an existing project directly to
`validate` without a complete baseline migration can prevent the application from starting.

This update adds Flyway and a versioned migration for the new `call_records` table.

## Render

Keep:

- `FLYWAY_ENABLED=true`
- `JPA_DDL_AUTO=update`

After the complete existing schema has been captured as Flyway migrations and tested against a copy of the
production database, change:

- `JPA_DDL_AUTO=validate`

At that point Hibernate will stop changing the schema and Flyway becomes the source of truth.

## Important

Do not delete or reset the current Supabase database just to introduce migrations. The existing data should be
preserved and the full baseline migration should be produced from a backup/staging copy first.


## Security gate before production

1. Ensure `app_users`, `chat_messages`, `friend_requests`, `status_posts`, `message_reactions`, `message_hidden_for` and `call_records` are not exposed to anonymous/public clients through Supabase Data API unless the application explicitly needs that path.
2. If the frontend ever uses Supabase Data API directly, enable RLS on every exposed table and write least-privilege policies. Supabase recommends RLS for exposed schemas and warns that service-role/secret keys bypass RLS and must remain backend-only.
3. Keep `SUPABASE_SERVICE_KEY` only in Render backend environment variables. Never put it in Vercel/frontend JavaScript.
4. Prefer private Storage buckets for private chat attachments. If the current public bucket is retained, remember that possession of a public object URL is sufficient to read that object; application authorization cannot make an already-public URL private.
5. Do not edit Supabase `storage` schema rows directly; use the Storage API.
6. After the live database has a complete Flyway baseline, change `JPA_DDL_AUTO` from `update` to `validate` so schema drift cannot silently modify production.

This checklist follows Supabase's current guidance for RLS, Storage access control, ownership, and secret-key handling.
