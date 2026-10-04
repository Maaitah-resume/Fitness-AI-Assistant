"""
Run once to pre-index every PDF in backend/data/ into Qdrant before starting
the server:
    python rag/build_index.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.loader import load_documents
from rag.vector_store import create_vectorstore

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")

pdf_paths = [
    os.path.join(DATA_DIR, fn)
    for fn in sorted(os.listdir(DATA_DIR))
    if fn.lower().endswith(".pdf")
] if os.path.isdir(DATA_DIR) else []

if not pdf_paths:
    print(f"[build_index] No PDFs found in: {DATA_DIR}")
    sys.exit(1)

for pdf_path in pdf_paths:
    print(f"[build_index] Indexing {pdf_path} ...")
    docs = load_documents(pdf_path)
    create_vectorstore(docs)   # upserts into Qdrant persistent storage

print(f"[build_index] Indexed {len(pdf_paths)} PDF(s) successfully in Qdrant.")