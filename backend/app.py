from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import os
import sys
import uvicorn

# ── Make sure backend/ and the sibling AI/ dir are both importable ───────────
# Routers import AI modules directly (chat_logic, rag.*) as top-level names,
# and AI/chat_logic.py imports backend's db/config the same way — so both
# directories need to be on sys.path.
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
AI_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "AI"))
if BASE_DIR not in sys.path:
    sys.path.append(BASE_DIR)
if AI_DIR not in sys.path:
    sys.path.append(AI_DIR)

# ── Routers ───────────────────────────────────────────────────────────────────
from routers.auth import router as auth_router
from routers.chats import router as chats_router
from routers.users import router as users_router
from routers.upload import router as upload_router
from db import ensure_schema

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(title="Fitness AI Assistant")


@app.on_event("startup")
async def run_migrations():
    ensure_schema()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    # No cookies are used (auth is a Bearer JWT), so credentials aren't needed
    # and we can keep a plain wildcard origin instead of the
    # allow_origins=["*"] + allow_credentials=True combination browsers reject.
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Register all API routers first ────────────────────────────────────────────
app.include_router(auth_router)
app.include_router(chats_router)
app.include_router(users_router)
app.include_router(upload_router)


@app.get("/health", include_in_schema=False)
async def health():
    return {"status": "ok"}

# ── Frontend static files (only if built) ────────────────────────────────────
FRONTEND_DIR = os.path.join(BASE_DIR, "..", "frontend", "dist")
ASSETS_DIR   = os.path.join(FRONTEND_DIR, "assets")

if os.path.exists(FRONTEND_DIR):
    if os.path.exists(ASSETS_DIR):
        app.mount("/assets",   StaticFiles(directory=ASSETS_DIR),   name="assets")
    app.mount("/frontend", StaticFiles(directory=FRONTEND_DIR), name="frontend")


@app.get("/", include_in_schema=False)
async def root():
    index_path = os.path.join(FRONTEND_DIR, "index.html")
    if os.path.exists(index_path):
        return FileResponse(index_path)
    return {"status": "ok", "message": "Fitness AI Assistant API is running."}


# Catch-all for React Router — must be registered LAST
@app.get("/{full_path:path}", include_in_schema=False)
async def serve_react_app(full_path: str):
    index_path = os.path.join(FRONTEND_DIR, "index.html")
    if os.path.exists(index_path):
        return FileResponse(index_path)
    return {"status": "error", "message": "Frontend not found."}


if __name__ == "__main__":
    uvicorn.run("app:app", host="127.0.0.1", port=8000, reload=False)