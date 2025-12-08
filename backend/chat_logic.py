import google.generativeai as genai
from .config import GEMINI_MODEL_NAME
from backend.user_memory import get_profile, update_profile, missing_fields

# ----------------------------
#   SYSTEM PROMPT (Improved)
# ----------------------------
SYSTEM_PROMPT = """
You are GymAI — a friendly and knowledgeable fitness assistant.

GOALS:
- Be supportive, clear, and simple.
- Keep answers short unless the user asks for more detail.
- If the user wants more information, offer follow-ups naturally.
- Adapt to the user’s tone and fitness level.

PROFILE MODE:
If the user wants a fitness profile OR a custom plan:
1. Ask: “Would you like me to build a personal fitness profile for you?”
2. If yes, collect fields ONE at a time:
   age, weight, height, gender, goal, level, training_days, equipment.
3. After each answer, confirm and ask for the next missing field.
4. When the profile is complete:
   - Summarize it clearly.
   - Ask: “Would you like a workout plan, nutrition plan, or both?”

BEHAVIOR:
- If unclear, ask a gentle clarifying question.
- If user message is not fitness related, answer normally.
- Never generate long multi-section essays unless the user asks.
- Provide concise and helpful responses.
- If Gemini fails, respond: “I’m having trouble reaching Gemini right now — please try again later.”
"""

# ----------------------------
#   Gemini Client (Flash Lite)
# ----------------------------
def get_client():
    """
    Always uses gemini-2.0-flash-lite (free unlimited).
    """
    try:
        return genai.GenerativeModel(GEMINI_MODEL_NAME)
    except Exception as e:
        print("Client init error →", e)
        return None


# ----------------------------
#   Main Response Generator
# ----------------------------
def generate_response(user_message: str) -> str:
    profile = get_profile()

    # --------------------------------------
    # 1) User explicitly wants a profile
    # --------------------------------------
    if "profile" in user_message.lower() or "plan" in user_message.lower():
        missing = missing_fields(profile)
        if missing:
            return (
                "I can create a personalized plan for you.\n"
                f"Missing fields: {', '.join(missing)}.\n"
                "Please provide one detail at a time."
            )
        else:
            return (
                "Your fitness profile is complete! 🎉\n"
                "Do you want a workout plan, nutrition plan, or both?"
            )

    # --------------------------------------
    # 2) User may be giving profile data
    # --------------------------------------
    if any(token.isdigit() for token in user_message.split()):
        update_profile(user_message)
        missing = missing_fields(get_profile())
        if missing:
            return f"Great — I saved that. I still need: {', '.join(missing)}."
        return "Your profile is now complete! Would you like a workout plan, nutrition plan, or both?"

    # --------------------------------------
    # 3) Default → Ask Gemini with system prompt
    # --------------------------------------
    try:
        client = get_client()
        if client is None:
            return "I’m having trouble reaching Gemini right now — please try again later."

        prompt = f"""
        SYSTEM INSTRUCTIONS:
        {SYSTEM_PROMPT}

        USER PROFILE (may be empty):
        {profile}

        USER MESSAGE:
        {user_message}

        Respond as GymAI.
                """

        reply = client.generate_content(prompt)
        return reply.text if hasattr(reply, "text") else str(reply)

    except Exception as e:
        print("Gemini error →", e)
        return "I’m having trouble reaching Gemini right now — please try again later."
