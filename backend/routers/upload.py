# backend/routers/upload.py
from fastapi import APIRouter, UploadFile, File
from dotenv import load_dotenv
import os
import traceback

from rag.loader import load_documents
from rag.vector_store import create_vectorstore
import chat_logic  # we keep a reference so we can hot‑swap the RAG chain

load_dotenv()

router = APIRouter(prefix="/api/v1", tags=["upload"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_DIR = os.path.join(BASE_DIR, "data")
FAISS_INDEX_PATH = os.path.join(BASE_DIR, "faiss_index")
os.makedirs(UPLOAD_DIR, exist_ok=True)


@router.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    """
    Accept a PDF, index it into FAISS, then hot‑reload the RAG chain
    so every subsequent chat benefits from the new knowledge immediately.
    """
    try:
        if not file.filename.lower().endswith(".pdf"):
            return {"status": "error", "message": "Only PDF files are allowed."}

        # ── Save to disk ─────────────────────────────────────
        file_path = os.path.join(UPLOAD_DIR, file.filename)
        with open(file_path, "wb") as f:
            f.write(await file.read())

        # ── Load, chunk & index ───────────────────────────────
        docs = load_documents(file_path)
        if not docs:
            return {
                "status": "error",
                "message": "The PDF appears to be empty or unreadable.",
            }

        create_vectorstore(docs)   # saves to faiss_index/ internally

        # ── Hot‑reload the RAG chain ───────────────────────────────
        try:
            from rag.chain import get_rag_chain
            chat_logic.rag_chain = get_rag_chain()
            print("[RAG] Chain reloaded after new upload.")
        except Exception as reload_err:
            print(f"[RAG] Chain reload warning (non‑fatal): {reload_err}")

        return {
            "status": "success",
            "message": (
                f"'{file.filename}' uploaded and indexed successfully! "
                f"({len(docs)} chunks created)"
            ),
        }

    except Exception as e:
        print("Upload error:", repr(e))
        traceback.print_exc()
        return {"status": "error", "message": str(e)}
