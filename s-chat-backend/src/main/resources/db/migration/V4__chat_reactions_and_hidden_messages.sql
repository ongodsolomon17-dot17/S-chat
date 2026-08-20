-- Persisted chat reactions and per-user "delete for me" state.
CREATE TABLE IF NOT EXISTS chat_message_reactions (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reaction_message_user
    ON chat_message_reactions (message_id, user_id);

CREATE INDEX IF NOT EXISTS idx_reaction_message
    ON chat_message_reactions (message_id);

CREATE TABLE IF NOT EXISTS chat_message_hidden_for (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    hidden_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hidden_message_user
    ON chat_message_hidden_for (message_id, user_id);

CREATE INDEX IF NOT EXISTS idx_hidden_message
    ON chat_message_hidden_for (message_id);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS reply_to_message_id UUID;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS reply_to_status_id UUID;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE chat_message_reactions
    ALTER COLUMN reaction_type TYPE VARCHAR(32);
