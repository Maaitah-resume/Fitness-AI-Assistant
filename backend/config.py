import os
import secrets
import warnings
from dotenv import load_dotenv

load_dotenv()

DB_PATH = os.path.join(os.path.dirname(__file__), "fitness.db")

OPENAI_API_KEY  = os.getenv("OPENAI_API_KEY")
OPENAI_MODEL    = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

# ── Auth / JWT ────────────────────────────────────────────────────────────────
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY")
if not JWT_SECRET_KEY:
    warnings.warn(
        "JWT_SECRET_KEY is not set in .env — using a random key generated for "
        "this process only. Every restart will invalidate existing tokens. "
        "Set JWT_SECRET_KEY in .env for a stable secret.",
        stacklevel=2,
    )
    JWT_SECRET_KEY = secrets.token_hex(32)

JWT_ALGORITHM       = os.getenv("JWT_ALGORITHM", "HS256")
JWT_EXPIRE_MINUTES  = int(os.getenv("JWT_EXPIRE_MINUTES", "1440"))

