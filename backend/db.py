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


# ── Schema migration ─────────────────────────────────────────────────────────

def ensure_schema():
    """
    Apply any additive, non-destructive schema changes to an existing DB file
    (new installs get everything via schema.sql already). Safe to call on
    every startup.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute("PRAGMA table_info(users)")
    columns = [row["name"] for row in cur.fetchall()]
    if "role" not in columns:
        cur.execute(
            "ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'"
        )
        conn.commit()
    conn.close()


# ── User management ───────────────────────────────────────────────────────────

class UserExistsError(Exception):
    """Raised when registering a username/email that's already taken."""


def create_user(username: str, password_hash: str, email: str, name: str = None) -> int:
    """
    Insert a brand-new user with role='user'. `password_hash` must already be
    a bcrypt hash — hashing happens in the auth router, not here.
    Raises UserExistsError if the username or email is already taken.
    """
    conn = get_db()
    cur = conn.cursor()
    try:
        cur.execute(
            "INSERT INTO users (username, password, email, name, role) "
            "VALUES (?, ?, ?, ?, 'user')",
            (username, password_hash, email, name),
        )
        conn.commit()
        return cur.lastrowid
    except sqlite3.IntegrityError:
        raise UserExistsError(f"Username or email already in use.")
    finally:
        conn.close()


def get_user_by_username(username: str) -> dict | None:
    """
    Return a user row as a dict (id, username, email, password, role) or None.
    Used by the login endpoint to verify credentials.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, username, email, password, role FROM users WHERE username = ?",
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
        "role": row["role"],
    }


def get_user_by_id(user_id: int) -> dict | None:
    """Return a user (id, username, email, name, role) without the password hash."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, username, email, name, role FROM users WHERE id = ?",
        (user_id,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return None
    return {
        "id": row["id"],
        "username": row["username"],
        "email": row["email"],
        "name": row["name"],
        "role": row["role"],
    }


def list_users() -> list[dict]:
    """Return every user (no password hashes) for the admin panel."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "SELECT id, username, email, name, role, created_at FROM users "
        "ORDER BY created_at DESC"
    )
    rows = cur.fetchall()
    conn.close()
    return [
        {
            "id": r["id"],
            "username": r["username"],
            "email": r["email"],
            "name": r["name"],
            "role": r["role"],
            "created_at": str(r["created_at"]) if r["created_at"] else None,
        }
        for r in rows
    ]


def update_user_role(user_id: int, role: str) -> None:
    conn = get_db()
    cur = conn.cursor()
    cur.execute("UPDATE users SET role = ? WHERE id = ?", (role, user_id))
    conn.commit()
    conn.close()


def delete_user(user_id: int) -> None:
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "DELETE FROM chat_history WHERE user_id = ?", (user_id,)
    )
    cur.execute(
        "DELETE FROM recent_chats WHERE user_id = ?", (user_id,)
    )
    cur.execute("DELETE FROM userInfo WHERE user_id = ?", (user_id,))
    cur.execute("DELETE FROM users WHERE id = ?", (user_id,))
    conn.commit()
    conn.close()


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
