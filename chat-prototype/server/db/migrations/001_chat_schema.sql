-- channels
CREATE TABLE channels (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    is_private BOOLEAN DEFAULT FALSE
);

-- users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT UNIQUE NOT NULL
);

-- messages
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL,
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    mentions TEXT[] DEFAULT '{}',
    parent_message_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);