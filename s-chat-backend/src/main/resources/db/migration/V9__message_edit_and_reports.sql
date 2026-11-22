-- Message editing + user/message reporting.

ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS user_reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id      UUID        NOT NULL,
    reported_user_id UUID        NOT NULL,
    message_id       UUID,
    reason           VARCHAR(24) NOT NULL,
    details          VARCHAR(1000),
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ,
    resolved_by      UUID,
    CONSTRAINT ck_report_not_self CHECK (reporter_id <> reported_user_id),
    CONSTRAINT ck_report_status   CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);
CREATE INDEX IF NOT EXISTS idx_reports_status_created ON user_reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_reported ON user_reports (reported_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_created ON user_reports (reporter_id, created_at DESC);

-- Fast lookup of messages that still need a "delivered" receipt when a user reconnects.
CREATE INDEX IF NOT EXISTS idx_chat_undelivered ON chat_messages (receiver_id) WHERE delivered_at IS NULL;
