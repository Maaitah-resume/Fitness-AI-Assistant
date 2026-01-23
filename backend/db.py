import sqlite3
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "fitness.db")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn



def get_or_create_user(username, age=None, gender=None, goal=None, level=None):
    conn = get_db()
    cur = conn.cursor()

    cur.execute(
        "SELECT id FROM users WHERE username = ?",
        (username,)
    )
    row = cur.fetchone()

    if row:
        conn.close()
        return row["id"]

    cur.execute("""
        INSERT INTO users (username, age, gender, goal, level)
        VALUES (?, ?, ?, ?, ?)
    """, (username, age, gender, goal, level))

    conn.commit()
    user_id = cur.lastrowid
    conn.close()
    return user_id

def save_message(user_id, role, message):
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
        INSERT INTO chat_history (user_id, role, message)
        VALUES (?, ?, ?)
    """, (user_id, role, message))

    conn.commit()
    conn.close()


def load_chat_history(user_id, limit=10):
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
        SELECT role, message
        FROM messages
        WHERE user_id = ?
        ORDER BY id DESC
        LIMIT ?
    """, (user_id, limit))

    rows = cur.fetchall()
    conn.close()

    return rows[::-1]  



def get_user_profile(user_id):
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
        SELECT age, gender, goal, level
        FROM users
        WHERE id = ?
    """, (user_id,))

    row = cur.fetchone()
    conn.close()

    return dict(row) if row else {}


def update_user_profile(user_id, **fields):
    if not fields:
        return

    keys = []
    values = []

    for k, v in fields.items():
        keys.append(f"{k} = ?")
        values.append(v)

    values.append(user_id)

    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        f"UPDATE users SET {', '.join(keys)} WHERE id = ?",
        values
    )
    conn.commit()
    conn.close()



