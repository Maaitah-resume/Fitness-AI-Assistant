from backend.db import (
    get_or_create_user,
    save_message,
    load_chat_history,
    get_user_profile,
    update_user_profile
)

from backend.config import GEMINI_MODEL_NAME
import google.generativeai as genai



def get_client():
    try:
        return genai.GenerativeModel(GEMINI_MODEL_NAME)
    except Exception as e:
        print("Client init error:", e)
        return None


def ask_gemini(user_message, profile, history):
    client = get_client()
    if client is None:
        return "I'm having trouble reaching Gemini right now — please try again later."

    conversation = ""
    for msg in history:
        conversation += f"{msg['role'].upper()}: {msg['message']}\n"

    SYSTEM_PROMPT = """
        You are GymAI — a friendly and knowledgeable fitness assistant.

        Your behavior must adapt based on the USER'S INTENT:

        ==========================================================
        1) WHEN THE USER WANTS GENERAL ANSWERS (NO PROFILE MODE)
        ==========================================================
        - If the user asks a simple question (e.g., workout advice, calories, BMI, reps, form tips, diets), 
        answer immediately WITHOUT asking for profile fields.
        - Keep responses short unless the user asks for more detail.
        - Provide helpful follow-ups naturally (e.g., “Would you like a beginner or advanced version?”).
        - Never force a fitness profile unless the user explicitly shows interest.

        Triggers for NO-PROFILE mode:
        - “give me a plan”
        - “workout for fat loss”
        - “how many calories”
        - “what is BMI”
        - “exercise for chest”
        - “meal plan”
        - “form tips”
        - “diet advice”
        - Any general fitness question

        ==========================================================
        2) WHEN THE USER WANTS A PERSONALIZED PLAN (PROFILE MODE)
        ==========================================================
        Only enter PROFILE MODE if the user says something like:
        - “build a plan for me”
        - “create a profile”
        - “tailor it to me”
        - “make a custom plan”
        - “I want a personalized program”

        When entering profile mode, ask:
        “Would you like me to build a personal fitness profile for you so I can tailor everything?”

        If they say YES:
        Collect the following fields ONE AT A TIME:
        - age
        - weight
        - height
        - goal (fat loss, muscle gain, endurance)
        - fitness level (beginner / intermediate / advanced)

        Rules:
        1. Ask ONLY ONE question at a time.
        2. After each answer, confirm and ask for the next missing field.
        3. If user gives multiple fields in one message, accept them.
        4. If something is unclear, ask politely for clarification.
        5. When ALL fields are collected:
        - Summarize the full profile.
        - Ask: “Would you like a personalized workout plan, nutrition plan, or both?”

        ==========================================================
        3) USING STORED PROFILE DATA
        ==========================================================
        When the user asks for advice *after* the profile is complete:
        - Always tailor the answer using their stored age, weight, height, goal, and level.
        - Keep answers short unless the user specifically requests more detail.
        - Provide structured plans (Workout | Nutrition | Tips).
        - Avoid extreme advice; keep everything safe and realistic.

        ==========================================================
        4) GENERAL BEHAVIOR
        ==========================================================
        - Be supportive, friendly, and concise.
        - Adapt your tone to match the user.
        - If unclear, ask a gentle clarifying question.
        - If Gemini fails, respond: “I’m having trouble reaching Gemini right now — please try again later.”
        """


    conversation = ""
    for msg in history:
        conversation += f"{msg['role'].upper()}: {msg['message']}\n"

    prompt = f"""
    SYSTEM:
    {SYSTEM_PROMPT}

    CONVERSATION HISTORY:
    {conversation}

    USER PROFILE:
    {profile}

    USER MESSAGE:
    {user_message}

    Respond naturally.
    """

    try:
        response = client.generate_content(prompt)
        return response.text if hasattr(response, "text") else str(response)
    except Exception as e:
        print("Gemini error:", e)
        return "I'm having trouble reaching Gemini right now — please try again later."
def generate_response(user_message: str):
    """
    Main brain of the chatbot.
    Returns:
        reply_text (str),
        meta (dict) → used by frontend (dashboard, state, etc.)
    """

    meta = {}

    try:
    
        user_id = get_or_create_user(username="default_user")
        save_message(user_id, "user", user_message)

        history = load_chat_history(user_id)
        profile = get_user_profile(user_id) or {}
        profile_active = profile.get("profile_active", 0)

        msg = user_message.lower().strip()

       
        if any(k in msg for k in [
            "exit profile",
            "stop profile",
            "disable profile",
            "no profile",
            "general mode"
        ]):
            update_user_profile(user_id, profile_active=0)

            reply = (
                "Understood ✅ Profile mode is now paused.\n\n"
                "I’ll answer in general fitness mode. "
                "What would you like help with?"
            )

            save_message(user_id, "assistant", reply)
            return reply, meta


        if msg in [
            "create profile",
            "start profile",
            "yes",
            "yes please",
            "sure",
            "ok"
        ] and not profile_active:
            update_user_profile(user_id, profile_active=1)

            reply = (
                "Great 👍 Let’s build your fitness profile.\n\n"
                "First question: what is your age?"
            )

            save_message(user_id, "assistant", reply)
            return reply, meta

        
        if profile_active:
            updated = {}

            if msg.isdigit() and 10 < int(msg) < 100:
                updated["age"] = int(msg)

            if "male" in msg:
                updated["gender"] = "male"
            elif "female" in msg:
                updated["gender"] = "female"

            if "muscle" in msg:
                updated["goal"] = "muscle_gain"
            elif "fat" in msg:
                updated["goal"] = "fat_loss"
            elif "endurance" in msg:
                updated["goal"] = "endurance"

            if any(lvl in msg for lvl in ["beginner", "intermediate", "advanced"]):
                updated["level"] = (
                    "beginner" if "beginner" in msg else
                    "intermediate" if "intermediate" in msg else
                    "advanced"
                )

            if updated:
                update_user_profile(user_id, **updated)
                profile = get_user_profile(user_id)

                required = ["age", "gender", "goal", "level"]
                missing = [f for f in required if not profile.get(f)]

                if missing:
                    reply = f"Saved 👍 Still need: {', '.join(missing)}."
                else:
                    update_user_profile(user_id, profile_active=1)

                    reply = (
                        "Fantastic! 🎉 Your fitness profile is complete.\n\n"
                        "Would you like a personalized workout plan, "
                        "nutrition plan, or both?"
                    )

                    meta["profile_completed"] = True
                    meta["profile"] = {
                        "age": profile.get("age"),
                        "gender": profile.get("gender"),
                        "goal": profile.get("goal"),
                        "level": profile.get("level"),
                    }

                save_message(user_id, "assistant", reply)
                return reply, meta

            reply = (
                "Got it 👍 Just to continue building your profile, "
                "could you answer the last question I asked?"
            )
            save_message(user_id, "assistant", reply)
            return reply, meta


        try:
            reply = ask_gemini(user_message, profile, history)
        except Exception as e:
            print("Gemini error:", e)

            if profile.get("goal") == "muscle_gain":
                reply = (
                    "Let’s keep things moving 💪\n\n"
                    "For muscle gain, focus on:\n"
                    "• Progressive overload\n"
                    "• 7–9 hours sleep\n"
                    "• Protein at each meal\n\n"
                    "Ask me about training, nutrition, or recovery."
                )
            else:
                reply = (
                    "I’m temporarily offline for advanced reasoning, "
                    "but I can still help with general fitness guidance.\n\n"
                    "What would you like to know?"
                )

        save_message(user_id, "assistant", reply)

        return reply, meta

    except Exception as e:
        print("generate_response error:", e)

        reply = (
            "I ran into a small issue on my side 😅\n"
            "Nothing was lost. Please try again."
        )
        return reply, meta
