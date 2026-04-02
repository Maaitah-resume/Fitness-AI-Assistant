from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import os
import sys
import uvicorn

# ── Make sure the backend directory is on the path ───────────────────────────
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.append(BASE_DIR)

# ── Routers ───────────────────────────────────────────────────────────────────
from routers.auth import router as auth_router
from routers.chats import router as chats_router
from routers.users import router as users_router
from routers.upload import router as upload_router

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(title="Fitness AI Assistant")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Register all API routers first ────────────────────────────────────────────
app.include_router(auth_router)
app.include_router(chats_router)
app.include_router(users_router)
app.include_router(upload_router)

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