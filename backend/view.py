import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "fitness.db")

conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row
cur = conn.cursor()


cur.execute("SELECT * FROM users")


rows = cur.fetchall()
for row in rows:
    print(dict(row))

conn.close()
