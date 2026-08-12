CREATE TABLE users (
    id             INTEGER PRIMARY KEY,
    username       VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(255) NOT NULL,
    last_name      VARCHAR(255) NOT NULL,
    is_disabled    BOOLEAN NOT NULL DEFAULT FALSE,
    is_admin       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE chats (
    id             INTEGER PRIMARY KEY,
    owner_id       INTEGER NOT NULL REFERENCES users(id),
    room_name      VARCHAR(255) NOT NULL,
    is_private     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE chat_members (
    chat_id        INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (chat_id, user_id)
);

CREATE TABLE messages (
    id             INTEGER PRIMARY KEY,
    created_at     BIGINT NOT NULL,
    content        TEXT NOT NULL,
    author_id      INTEGER NOT NULL REFERENCES users(id),
    chat_id        INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_members_user_id ON chat_members(user_id);
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
