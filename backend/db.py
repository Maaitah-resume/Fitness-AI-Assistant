import sqlite3
from config import DB_PATH


# ── Connection ────────────────────────────────────────────────────────────────

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


# ── Internal helper ───────────────────────────────────────────────────────────

def _read_clob(value):
    """Return a plain string from SQLite value."""
    return str(value) if value is not None else ""


# ── User management ───────────────────────────────────────────────────────────

def register_user(username: str, password: str, email: str = None, name: str = None):
    """
    Insert a brand-new user.  Caller is responsible for hashing the password
    before passing it here.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO users (username, password, email, name) VALUES (?, ?, ?, ?)",
        (username, password, email, name),
    )
    conn.commit()
    conn.close()


def get_user_by_username(username: str) -> dict | None:
    """
    Return a user row as a dict (id, username, email, password) or None.
    Used by the login endpoint to verify credentials.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, username, email, password FROM users WHERE username = ?",
        (username,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return None
    return {
        "id": row["id"],
        "username": row["username"],
        "email": row["email"],
        "password": row["password"],
    }


def get_or_create_user(username: str, password: str = None, email: str = None) -> int:
    """
    Look up a user by username; create them if they don't exist.
    Used by the registration flow.  Returns user_id.
    """
    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT id FROM users WHERE username = ?", (username,))
    row = cur.fetchone()

    if row:
        conn.close()
        return row["id"]

    cur.execute(
        "INSERT INTO users (username, password, email) VALUES (?, ?, ?)",
        (username, password, email),
    )
    conn.commit()

    cur.execute("SELECT id FROM users WHERE username = ?", (username,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise RuntimeError(f"Failed to create user '{username}'.")
    return row["id"]


def get_or_create_user_by_email(email: str) -> int:
    """
    Look up a user by email; create a minimal record if they don't exist.
    Used by every chat route so the frontend only needs to pass an email.
    Returns user_id.
    """
    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT id FROM users WHERE email = ?", (email,))
    row = cur.fetchone()

    if row:
        conn.close()
        return row["id"]

    # Auto-create: username defaults to the email address
    cur.execute(
        "INSERT INTO users (username, email, password) VALUES (?, ?, ?)",
        (email, email, ""),
    )
    conn.commit()

    cur.execute("SELECT id FROM users WHERE email = ?", (email,))
    row = cur.fetchone()
    conn.close()

    if not row:
        raise RuntimeError(f"Failed to create user for email '{email}'.")
    return row["id"]


# ── User fitness profile ──────────────────────────────────────────────────────

def get_user_profile(user_id: int) -> dict:
    """Return the userInfo row for user_id, or {} if none exists yet."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT age, gender, goal, fitness_level, profile_active
        FROM userInfo
        WHERE user_id = ?
        """,
        (user_id,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return {}
    return {
        "age": row["age"],
        "gender": row["gender"],
        "goal": row["goal"],
        "level": row["fitness_level"],
        "profile_active": row["profile_active"] if row["profile_active"] is not None else 0,
    }


def update_user_profile(user_id: int, **fields):
    """
    Upsert the userInfo row for user_id.
    Accepted keyword args: age, gender, goal, level, profile_active.
    'level' maps to the DB column fitness_level.
    """
    if not fields:
        return

    # Map Python key → DB column name
    COLUMN_MAP = {
        "level": "fitness_level",
        "profile_active": "profile_active",
        "age": "age",
        "gender": "gender",
        "goal": "goal",
    }

    set_clauses = []
    values = []

    for key, val in fields.items():
        db_col = COLUMN_MAP.get(key)
        if db_col is None:
            continue
        set_clauses.append(f"{db_col} = ?")
        values.append(val)

    if not set_clauses:
        return

    values.append(user_id)

    conn = get_db()
    cur = conn.cursor()

    # Ensure the row exists before updating
    cur.execute("SELECT 1 FROM userInfo WHERE user_id = ?", (user_id,))
    if not cur.fetchone():
        cur.execute("INSERT INTO userInfo (user_id) VALUES (?)", (user_id,))
        conn.commit()

    sql = f"UPDATE userInfo SET {', '.join(set_clauses)} WHERE user_id = ?"
    cur.execute(sql, values)
    conn.commit()
    conn.close()


# ── Chat sessions ─────────────────────────────────────────────────────────────

def create_chat(user_id: int, title: str = None) -> int:
    conn = get_db()
    cur = conn.cursor()

    cur.execute(
        """
        INSERT INTO recent_chats (user_id, title)
        VALUES (?, ?)
        """,
        (user_id, title or "New Chat"),
    )
    conn.commit()
    chat_id = cur.lastrowid
    conn.close()
    return chat_id


def get_recent_chats(user_id: int, limit: int = 20) -> list[dict]:
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, title, last_message, created_at, updated_at
        FROM recent_chats
        WHERE user_id = ?
        ORDER BY updated_at DESC
        LIMIT ?
        """,
        (user_id, limit),
    )
    rows = cur.fetchall()
    conn.close()

    return [
        {
            "id": r["id"],
            "title": r["title"],
            "last_message": _read_clob(r["last_message"]),
            "created_at": str(r["created_at"]) if r["created_at"] else None,
            "updated_at": str(r["updated_at"]) if r["updated_at"] else None,
        }
        for r in rows
    ]


def update_chat(chat_id: int, title: str = None, last_message: str = None):
    conn = get_db()
    cur = conn.cursor()

    if title:
        cur.execute(
            "UPDATE recent_chats SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (title, chat_id),
        )
    if last_message:
        cur.execute(
            "UPDATE recent_chats SET last_message = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (last_message, chat_id),
        )

    conn.commit()
    conn.close()


def delete_chat(chat_id: int):
    conn = get_db()
    cur = conn.cursor()
    # Delete messages first (FK constraint)
    cur.execute("DELETE FROM chat_history WHERE chat_id = ?", (chat_id,))
    cur.execute("DELETE FROM recent_chats WHERE id = ?", (chat_id,))
    conn.commit()
    conn.close()


# ── Messages ──────────────────────────────────────────────────────────────────

def save_message(user_id: int, role: str, message: str, chat_id: int = None):
    conn = get_db()
    cur = conn.cursor()

    cur.execute(
        """
        INSERT INTO chat_history (user_id, chat_id, role, message)
        VALUES (?, ?, ?, ?)
        """,
        (user_id, chat_id, role, message),
    )
    conn.commit()

    # Keep recent_chats.last_message in sync
    if chat_id:
        preview = message[:100]
        cur.execute(
            """
            UPDATE recent_chats
            SET last_message = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (preview, chat_id),
        )
        conn.commit()

    conn.close()


def load_chat_history(user_id: int, chat_id: int = None, limit: int = 10) -> list[dict]:
    conn = get_db()
    cur = conn.cursor()

    if chat_id:
        cur.execute(
            """
            SELECT role, message
            FROM chat_history
            WHERE user_id = ? AND chat_id = ?
            ORDER BY id DESC
            LIMIT ?
            """,
            (user_id, chat_id, limit),
        )
    else:
        cur.execute(
            """
            SELECT role, message
            FROM chat_history
            WHERE user_id = ?
            ORDER BY id DESC
            LIMIT ?
            """,
            (user_id, limit),
        )

    rows = cur.fetchall()
    conn.close()

    # Fetched newest-first; reverse so the oldest message is at index 0
    return [
        {"role": r["role"], "message": _read_clob(r["message"])}
        for r in reversed(rows)
    ]
