"""Shared Pydantic models for chat endpoints."""

from typing import List, Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """Represents a single chat message exchanged with the assistant."""

    role: Literal["user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    message: str
    history: List[ChatMessage] = Field(
        default_factory=list,
        description="Ordered list of prior messages so the assistant keeps context.",
    )


class ChatResponse(BaseModel):
    reply: str
    history: List[ChatMessage]
