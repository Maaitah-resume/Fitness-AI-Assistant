import google.generativeai as genai
from dotenv import load_dotenv
import os

load_dotenv()

api_key = os.getenv("GOOGLE_GEMINI_API_KEY")

if not api_key:
    print("❌ GOOGLE_GEMINI_API_KEY not found in .env file")
else:
    print(f"🔑 Key found (starts with: {api_key[:10]}...)")
    
    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-2.5-flash")
        response = model.generate_content("Say 'test'")
        print("✅ Your API key is valid and working!")
        print(f"Response: {response.text}")
    except Exception as e:
        print("❌ Invalid API key or connection issue.")
        print(f"Error: {e}")

