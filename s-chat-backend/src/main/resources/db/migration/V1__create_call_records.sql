CREATE TABLE IF NOT EXISTS call_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_id UUID NOT NULL,
    callee_id UUID NOT NULL,
    call_type VARCHAR(12) NOT NULL,
    status VARCHAR(12) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ NULL,
    ended_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_call_caller_started
    ON call_records (caller_id, started_at);

CREATE INDEX IF NOT EXISTS idx_call_callee_started
    ON call_records (callee_id, started_at);
