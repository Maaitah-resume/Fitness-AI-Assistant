import sqlite3
import os
import sys
import io

# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from config import DB_PATH

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCHEMA_PATH = os.path.join(BASE_DIR, "schema.sql")

def init_db():
    print(f"Initializing SQLite database at: {DB_PATH}...")

    if not os.path.exists(SCHEMA_PATH):
        print(f"Error: Schema file not found at {SCHEMA_PATH}")
        return

    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        schema = f.read()

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    statements = schema.split(';')

    for statement in statements:
        stmt = statement.strip()
        if stmt:
            try:
                cursor.execute(stmt)
            except sqlite3.DatabaseError as e:
                if "already exists" in str(e):
                    print(f"Skipping (already exists): {stmt[:50]}...")
                else:
                    print(f"Error: {e}")

    conn.commit()
    conn.close()

    print("Database initialization complete.")

if __name__ == "__main__":
    init_db()
