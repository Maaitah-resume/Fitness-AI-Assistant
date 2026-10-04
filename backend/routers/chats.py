# backend/routers/chats.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from typing import Optional

from chat_logic import generate_response
from db import create_chat, delete_chat, get_recent_chats, load_chat_history
from security import get_current_user

router = APIRouter(prefix="/api/v1/chats", tags=["chats"])


# ── Schemas ───────────────────────────────────────────────────────────────────
class ChatRequest(BaseModel):
    message: str
    chat_id: Optional[int] = None


# ── Routes ────────────────────────────────────────────────────────────────────
@router.post("/send")
async def chat_endpoint(req: ChatRequest, current_user: dict = Depends(get_current_user)):
    """Send a message and get an AI reply."""
    try:
        reply, meta = generate_response(
            user_message=req.message,
            user_id=current_user["id"],
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


@router.get("/recent")
async def get_recent(current_user: dict = Depends(get_current_user)):
    """Return the most recent chats for the authenticated user."""
    try:
        chats = get_recent_chats(current_user["id"])
        return {"status": "success", "data": {"chats": chats}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{chat_id}/messages")
async def get_messages(chat_id: int, current_user: dict = Depends(get_current_user)):
    """Return messages for a specific chat belonging to the authenticated user."""
    try:
        messages = load_chat_history(current_user["id"], chat_id=chat_id, limit=50)
        return {"status": "success", "data": {"messages": messages}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/{chat_id}")
async def do_delete_chat(chat_id: int, current_user: dict = Depends(get_current_user)):
    """Delete a chat by its ID (must belong to the authenticated user)."""
    try:
        owned = {c["id"] for c in get_recent_chats(current_user["id"], limit=1000)}
        if chat_id not in owned:
            raise HTTPException(status_code=404, detail="Chat not found.")
        delete_chat(chat_id)
        return {"status": "success", "message": "Chat deleted."}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/new")
async def start_new_chat(current_user: dict = Depends(get_current_user)):
    """Create a new empty chat session for the authenticated user."""
    try:
        chat_id = create_chat(current_user["id"])
        return {"status": "success", "data": {"chat_id": chat_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
