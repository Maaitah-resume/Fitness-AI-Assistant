from openai import OpenAI
import os

from db import (
    save_message,
    load_chat_history,
    get_user_profile,
    update_user_profile,
    create_chat,
)
from config import OPENAI_API_KEY, OPENAI_MODEL

# ── OpenAI client ─────────────────────────────────────────────────────────────
_client = OpenAI(api_key=OPENAI_API_KEY)

# ── RAG ───────────────────────────────────────────────────────────────────────
try:
    from rag.chain import get_rag_chain
    rag_chain = get_rag_chain()
    print("[RAG] Chain loaded successfully.")
except Exception as _rag_err:
    print(f"[RAG] Could not load chain: {_rag_err}. Continuing without RAG.")
    rag_chain = None
# ─────────────────────────────────────────────────────────────────────────────


def _get_rag_context(user_message: str) -> str:
    """Run RAG retrieval and return context text, or '' on any failure."""
    if rag_chain is None:
        return ""
    try:
        result = rag_chain.invoke({"query": user_message})
        return result.get("result", "") if isinstance(result, dict) else str(result)
    except Exception as e:
        print(f"[RAG] Retrieval error: {e}")
        return ""


def ask_openai(user_message: str, profile: dict, history: list) -> str:
    context = _get_rag_context(user_message)

    system_prompt = """
You are GymAI — a friendly, knowledgeable fitness assistant with the ability to
read and answer questions about documents the user has uploaded.

==========================================================
1) DOCUMENT / PDF READING
==========================================================
- The user may upload fitness-related PDFs (programs, nutrition guides, research, etc.).
- When a CONTEXT FROM UPLOADED DOCUMENT section appears in the conversation, you
    MUST use that content to answer the user's question directly and specifically.
- Quote or reference the document where helpful (e.g. "According to your document…").
- If the context covers the question, answer from it — do NOT say you cannot read files.
- If the context is empty or does not cover the question, answer from your general
    fitness knowledge and let the user know the document did not contain that info.
- You can use the document context in combination with your general knowledge to answer questions, but always prioritize the document if it contains relevant information. If the user refers to a previous chat or uploaded document, try to use that document as context and ask a clarifying question if it's ambiguous which document they mean.
==========================================================
2) GENERAL FITNESS QUESTIONS (NO PROFILE NEEDED)
==========================================================
- Answer immediately for questions about workouts, calories, BMI, reps, form,
    diets, supplements, recovery, etc.
- Keep answers concise unless more detail is requested.
- Never force a profile unless the user explicitly wants personalisation.

==========================================================
3) PERSONALISED PLANS (PROFILE MODE)
==========================================================
Only enter PROFILE MODE when the user says something like:
    "build a plan for me", "create a profile", "tailor it to me",
    "make a custom plan", "I want a personalized program"

Collect ONE field at a time: age, weight, height, goal, fitness level.
When all fields are collected, summarise and ask: workout plan, nutrition plan, or both?

==========================================================
4) USING STORED PROFILE DATA
==========================================================
- Tailor every answer using the stored profile when available.
- Provide structured plans: Workout | Nutrition | Tips.
- Keep advice safe and realistic.

==========================================================
5) GENERAL BEHAVIOUR
==========================================================
- Be supportive, friendly, and concise.
- Adapt your tone to match the user.
- Never tell the user you "cannot read files" — you can, via the document context provided.
"""

    # Build the messages list
    messages = [{"role": "system", "content": system_prompt}]

    # ── List previously uploaded PDFs so the model can reference past uploads ──
    try:
        data_dir = os.path.join(os.path.dirname(__file__), "data")
        uploaded = []
        if os.path.isdir(data_dir):
            for fn in sorted(os.listdir(data_dir)):
                if fn.lower().endswith(".pdf"):
                    uploaded.append(fn)
        if uploaded:
            uploads_text = "Previously uploaded documents (indexed):\n" + "\n".join(f"- {p}" for p in uploaded)
            uploads_text += "\n\nThese documents have been indexed and may be used as context for user questions. If multiple documents are relevant, ask the user to clarify which one to use."
            messages.append({"role": "system", "content": uploads_text})
    except Exception:
        pass

    # ── Inject document context prominently so the model can't miss it ────────
    if context and context.strip():
        messages.append({
            "role": "system",
            "content": (
                "CONTEXT FROM UPLOADED DOCUMENT — use this to answer the user's question:\n\n"
                + context.strip()
            ),
        })

    # ── Inject user profile ───────────────────────────────────────────────────
    if profile:
        messages.append({
            "role": "system",
            "content": f"USER FITNESS PROFILE:\n{profile}",
        })

    # ── Conversation history ──────────────────────────────────────────────────
    for m in history:
        role = "assistant" if m["role"] == "assistant" else "user"
        messages.append({"role": role, "content": m["message"]})

    # ── Current message ───────────────────────────────────────────────────────
    messages.append({"role": "user", "content": user_message})

    try:
        response = _client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=messages,
        )
        return response.choices[0].message.content
    except Exception as e:
        print("OpenAI error:", e)
        return "I'm having trouble reaching the AI right now — please try again later."


def generate_response(user_message: str, user_id: int, chat_id: int = None):
    """
    Main chatbot entry point.

    Parameters
    ----------
    user_message : str
    user_id      : int   — real DB user ID (resolved from email by the router)
    chat_id      : int | None

    Returns
    -------
    reply  : str
    meta   : dict  — forwarded to the frontend
    """
    meta = {}

    try:
        # ── Chat session ──────────────────────────────────────────────────────
        if not chat_id:
            chat_id = create_chat(user_id, title=user_message[:50])
        meta["chat_id"] = chat_id

        save_message(user_id, "user", user_message, chat_id=chat_id)

        history        = load_chat_history(user_id, chat_id=chat_id)
        profile        = get_user_profile(user_id) or {}
        profile_active = profile.get("profile_active", 0)

        msg = user_message.lower().strip()

        # ── Exit profile mode ─────────────────────────────────────────────────
        if any(k in msg for k in [
            "exit profile", "stop profile", "disable profile",
            "no profile", "general mode",
        ]):
            update_user_profile(user_id, profile_active=0)
            reply = (
                "Understood. Profile mode is now paused.\n\n"
                "I'll answer in general fitness mode. What would you like help with?"
            )
            save_message(user_id, "assistant", reply, chat_id=chat_id)
            return reply, meta

        # ── Enter profile mode ────────────────────────────────────────────────
        if msg in ["create profile", "start profile", "yes",
                   "yes please", "sure", "ok"] and not profile_active:
            update_user_profile(user_id, profile_active=1)
            reply = (
                "Great! Let's build your fitness profile.\n\n"
                "First question: what is your age?"
            )
            save_message(user_id, "assistant", reply, chat_id=chat_id)
            return reply, meta

        # ── Collect profile fields ────────────────────────────────────────────
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
                    "beginner"     if "beginner"     in msg else
                    "intermediate" if "intermediate" in msg else
                    "advanced"
                )

            if updated:
                update_user_profile(user_id, **updated)
                profile  = get_user_profile(user_id)
                required = ["age", "gender", "goal", "level"]
                missing  = [f for f in required if not profile.get(f)]

                if missing:
                    reply = f"Saved! Still need: {', '.join(missing)}."
                else:
                    reply = (
                        "Fantastic! Your fitness profile is complete.\n\n"
                        "Would you like a personalized workout plan, "
                        "nutrition plan, or both?"
                    )
                    meta["profile_completed"] = True
                    meta["profile"] = {k: profile.get(k) for k in required}

                save_message(user_id, "assistant", reply, chat_id=chat_id)
                return reply, meta

            reply = (
                "Got it. Just to continue building your profile, "
                "could you answer the last question I asked?"
            )
            save_message(user_id, "assistant", reply, chat_id=chat_id)
            return reply, meta

        # ── OpenAI + RAG ──────────────────────────────────────────────────────
        try:
            reply = ask_openai(user_message, profile, history)
        except Exception as e:
            print("OpenAI error:", e)
            reply = (
                "I'm temporarily offline for advanced reasoning, "
                "but I can still help with general fitness guidance.\n\n"
                "What would you like to know?"
            )

        save_message(user_id, "assistant", reply, chat_id=chat_id)
        return reply, meta

    except Exception as e:
        print("generate_response error:", e)
        return (
            "I ran into a small issue on my side.\nNothing was lost. Please try again.",
            meta,
        )
    
    