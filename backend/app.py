from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
import os
from backend.user_memory import reset_profile   

from .chat_logic import generate_response

app = FastAPI(title="Fitness AI Assistant")
 
# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve frontend
FRONTEND_DIR = os.path.join(os.path.dirname(__file__), "..", "frontend")
app.mount("/static", StaticFiles(directory=FRONTEND_DIR), name="static")

class ChatRequest(BaseModel):
    message: str   # ONLY message, no history

@app.on_event("startup")
def startup_event():
    os.makedirs("backend/memory", exist_ok=True)
    reset_profile()



@app.post("/chat")
def chat_endpoint(req: ChatRequest):
    reply = generate_response(req.message)
    return {"response": reply}

@app.get("/")
def root():
    return FileResponse(os.path.join(FRONTEND_DIR, "index.html"))
