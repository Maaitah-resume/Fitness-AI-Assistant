# backend/routers/chats.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from typing import Optional

# ----- ABSOLUTE IMPORTS -------------------------------------------------
from chat_logic import generate_response
from db import (
    get_or_create_user_by_email,
    create_chat,
    get_recent_chats,
    load_chat_history,
    delete_chat,
)
# ---------------------------------------------------------------------

router = APIRouter(prefix="/api/v1/chats", tags=["chats"])


# ── Schemas ───────────────────────────────────────────────────────────────────
class ChatRequest(BaseModel):
    message: str
    user_email: EmailStr            # real user identity sent by the frontend
    chat_id: Optional[int] = None


# ── Routes ────────────────────────────────────────────────────────────────────
@router.post("/send")
async def chat_endpoint(req: ChatRequest):
    """Send a message and get an AI reply."""
    try:
        # Resolve the real user_id from their email
        user_id = get_or_create_user_by_email(req.user_email)

        reply, meta = generate_response(
            user_message=req.message,
            user_id=user_id,          # pass real identity into the brain
            chat_id=req.chat_id,
        )
        return {
            "status": "success",
            "data": {
                "response": reply,
                "chat_id": meta.get("chat_id"),
                "profile_completed": meta.get("profile_completed"),
                "profile": meta.get("profile"),
            },
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/recent/{user_email}")
async def get_recent(user_email: EmailStr):
    """Return the most recent chats for a user."""
    try:
        user_id = get_or_create_user_by_email(user_email)
        chats = get_recent_chats(user_id)
        return {"status": "success", "data": {"chats": chats}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{chat_id}/messages/{user_email}")
async def get_messages(user_email: EmailStr, chat_id: int):
    """Return messages for a specific chat."""
    try:
        user_id = get_or_create_user_by_email(user_email)
        messages = load_chat_history(user_id, chat_id=chat_id, limit=50)
        return {"status": "success", "data": {"messages": messages}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/{chat_id}/{user_email}")
async def do_delete_chat(user_email: EmailStr, chat_id: int):
    """Delete a chat by its ID."""
    try:
        user_id = get_or_create_user_by_email(user_email)   # verifies user exists
        delete_chat(chat_id)
        return {"status": "success", "message": "Chat deleted."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/new/{user_email}")
async def start_new_chat(user_email: EmailStr):
    """Create a new empty chat session."""
    try:
        user_id = get_or_create_user_by_email(user_email)
        chat_id = create_chat(user_id)
        return {"status": "success", "data": {"chat_id": chat_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
