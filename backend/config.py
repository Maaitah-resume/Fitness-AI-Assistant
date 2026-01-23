import os
import google.generativeai as genai
from dotenv import load_dotenv
import os

load_dotenv()

GOOGLE_GEMINI_API_KEY = os.getenv("GOOGLE_GEMINI_API_KEY")

GEMINI_MODEL_NAME = "gemini-flash-latest"


genai.configure(api_key=GOOGLE_GEMINI_API_KEY)
