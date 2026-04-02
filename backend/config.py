import os
from dotenv import load_dotenv

load_dotenv()

DB_PATH = os.path.join(os.path.dirname(__file__), "fitness.db")

OPENAI_API_KEY  = os.getenv("OPENAI_API_KEY")
OPENAI_MODEL    = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

