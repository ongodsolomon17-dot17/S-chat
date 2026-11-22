-- Account recovery codes (email / SMS / WhatsApp). Only a keyed hash of the 6-digit code is stored.
CREATE TABLE IF NOT EXISTS account_recovery_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    channel     VARCHAR(12) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    attempts    INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_recovery_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP'))
);
CREATE INDEX IF NOT EXISTS idx_recovery_user_created ON account_recovery_requests (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recovery_expires ON account_recovery_requests (expires_at);
