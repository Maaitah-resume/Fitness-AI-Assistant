from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import os

from .chat_logic import generate_response
from .chat_models import ChatMessage, ChatRequest, ChatResponse


app = FastAPI(title="Fitness AI Assistant", version="1.0.0")

# Enable CORS for frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, replace with specific frontend URL
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve static files from frontend directory
frontend_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "frontend")
if os.path.exists(frontend_path):
    app.mount("/static", StaticFiles(directory=frontend_path), name="static")


@app.post("/chat", response_model=ChatResponse)
def chat_endpoint(req: ChatRequest):
    """
    POST /chat
    Body: { "message": "your text here" }
    Returns: { "reply": "assistant's answer" }
    """
    # Always keep history optional so existing simple requests continue to work.
    reply, updated_history = generate_response(req.message, req.history or [])
    return ChatResponse(reply=reply, history=updated_history)


@app.get("/")
def root():
    """
    Serve the frontend index page.
    """
    frontend_index = os.path.join(os.path.dirname(os.path.dirname(__file__)), "frontend", "index.html")
    if os.path.exists(frontend_index):
        return FileResponse(frontend_index)
    return {"status": "running", "service": "Fitness AI Assistant", "message": "Frontend not found"}


@app.get("/health")
def health():
    """
    Health check endpoint.
    """
    return {"status": "running", "service": "Fitness AI Assistant"}

