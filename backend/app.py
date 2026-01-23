from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
import os

from .chat_logic import generate_response

app = FastAPI(title="Fitness AI Assistant")


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
FRONTEND_DIR = os.path.join(BASE_DIR, "..", "frontend")

app.mount("/static", StaticFiles(directory=FRONTEND_DIR), name="static")

class ChatRequest(BaseModel):
    message: str

@app.post("/chat")
def chat_endpoint(req: ChatRequest):
    reply, meta = generate_response(req.message)

    response = {
        "response": reply
    }

    if meta.get("profile_completed"):
        response["profile_completed"] = True
        response["profile"] = meta.get("profile")

    return response



@app.get("/")
def root():
    return FileResponse(os.path.join(FRONTEND_DIR, "index.html"))


