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
