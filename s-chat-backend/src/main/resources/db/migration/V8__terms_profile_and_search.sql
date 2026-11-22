-- Terms/privacy acceptance, public profile bio, lookup + search indexes.
-- Idempotent so it is safe to re-run against a database Hibernate has already altered.

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS bio                  VARCHAR(160);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS terms_version        VARCHAR(32);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS terms_accepted_at    TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS privacy_version      VARCHAR(32);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS privacy_accepted_at  TIMESTAMPTZ;

-- Append-only audit trail: one row per acceptance event (signup and any re-acceptance).
CREATE TABLE IF NOT EXISTS terms_acceptances (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    terms_version    VARCHAR(32) NOT NULL,
    privacy_version  VARCHAR(32) NOT NULL,
    accepted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_terms_acceptances_user ON terms_acceptances (user_id, accepted_at DESC);

-- Case-insensitive username lookups (chat links) hit this instead of a seq scan.
CREATE INDEX IF NOT EXISTS idx_app_users_username_lower ON app_users (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_app_users_email_lower    ON app_users (LOWER(email));

-- Message search (ILIKE '%term%') — trigram index. pg_trgm is available on Neon.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_chat_messages_content_trgm
    ON chat_messages USING gin (content gin_trgm_ops);

-- Lets a user switch their shareable chat link off.
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS chat_link_enabled BOOLEAN NOT NULL DEFAULT TRUE;
