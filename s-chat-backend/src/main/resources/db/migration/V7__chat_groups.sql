CREATE TABLE chat_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(512),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_groups_created_by ON chat_groups(created_by);

CREATE TABLE chat_group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES chat_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_group_member UNIQUE (group_id, user_id),
    CONSTRAINT ck_chat_group_member_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE INDEX idx_group_members_user ON chat_group_members(user_id);
CREATE INDEX idx_group_members_group ON chat_group_members(group_id);

CREATE TABLE chat_group_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES chat_groups(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    content TEXT NOT NULL,
    attachment_url VARCHAR(512),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);

CREATE INDEX idx_group_messages_group_sent ON chat_group_messages(group_id, sent_at DESC);
