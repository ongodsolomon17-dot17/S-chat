-- Baseline for the four core tables that were historically created by Hibernate
-- (ddl-auto=update) rather than by a migration. Every statement is idempotent, so this is
-- a no-op on an existing database and builds the schema on a brand-new Neon database.
-- Version 0.1 sorts before V1; spring.flyway.out-of-order=true lets it run on databases
-- that already have V1..V7 recorded.

CREATE TABLE IF NOT EXISTS app_users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(32)  NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    phone_number          VARCHAR(32),
    public_id             VARCHAR(32)  NOT NULL,
    profile_picture_url   VARCHAR(512),
    add_by_id_only        BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_required     BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(16)  NOT NULL DEFAULT 'USER',
    account_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    refresh_token_version BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at         TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_username  ON app_users (username);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_email     ON app_users (email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_public_id ON app_users (public_id);

CREATE TABLE IF NOT EXISTS friend_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id  UUID        NOT NULL,
    addressee_id  UUID        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    matched_via   VARCHAR(16),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_friend_req_pair ON friend_requests (requester_id, addressee_id);
CREATE INDEX IF NOT EXISTS idx_friend_req_addressee_status ON friend_requests (addressee_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_req_requester ON friend_requests (requester_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id           UUID        NOT NULL,
    receiver_id         UUID        NOT NULL,
    content             TEXT        NOT NULL,
    attachment_url      VARCHAR(512),
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    reply_to_message_id UUID,
    reply_to_status_id  UUID,
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID
);
CREATE INDEX IF NOT EXISTS idx_chat_sender_receiver_sent ON chat_messages (sender_id, receiver_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_chat_receiver_sender_sent ON chat_messages (receiver_id, sender_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_chat_reply_to ON chat_messages (reply_to_message_id);

CREATE TABLE IF NOT EXISTS status_posts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    media_url        VARCHAR(512),
    caption          VARCHAR(280),
    text_content     VARCHAR(700),
    background_color VARCHAR(16),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_status_user_expires ON status_posts (user_id, expires_at);
