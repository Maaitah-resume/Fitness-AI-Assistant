-- ── Safe teardown (reverse FK order) ────────────────────────────────────────
DROP TABLE IF EXISTS chat_history;
DROP TABLE IF EXISTS recent_chats;
DROP TABLE IF EXISTS userInfo;
DROP TABLE IF EXISTS users;

-- ── Users ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username   TEXT UNIQUE NOT NULL,
    email      TEXT UNIQUE,
    name       TEXT,
    password   TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ── User fitness profile ──────────────────────────────────────────────────────
CREATE TABLE userInfo (
    user_id        INTEGER PRIMARY KEY,
    age            INTEGER,
    gender         TEXT,
    goal           TEXT,
    fitness_level  TEXT,
    profile_active INTEGER DEFAULT 0,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ── Chat sessions ─────────────────────────────────────────────────────────────
CREATE TABLE recent_chats (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    title        TEXT,
    last_message TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ── Messages ──────────────────────────────────────────────────────────────────
CREATE TABLE chat_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL,
    chat_id    INTEGER,
    role       TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    message    TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (chat_id) REFERENCES recent_chats(id)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_chat_user ON chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_time ON chat_history(created_at);
CREATE INDEX IF NOT EXISTS idx_chat_chat_id ON chat_history(chat_id);
CREATE INDEX IF NOT EXISTS idx_recent_user ON recent_chats(user_id);
CREATE INDEX IF NOT EXISTS idx_recent_updated ON recent_chats(updated_at);
