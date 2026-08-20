-- Status text/background support. Safe for databases where Hibernate already added them.
ALTER TABLE status_posts
    ADD COLUMN IF NOT EXISTS text_content VARCHAR(700);

ALTER TABLE status_posts
    ADD COLUMN IF NOT EXISTS background_color VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_status_user_expires
    ON status_posts (user_id, expires_at);
