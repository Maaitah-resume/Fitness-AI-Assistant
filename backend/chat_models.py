"""Shared Pydantic models for the chat endpoint."""

from pydantic import BaseModel


class ChatRequest(BaseModel):
    """Single-message chat request body."""

    message: str


class ChatResponse(BaseModel):
    """Minimal chat response containing only the assistant reply."""

    reply: str
