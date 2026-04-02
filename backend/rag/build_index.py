"""
Run once to pre-index a PDF into Qdrant before starting the server:
    python rag/build_index.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.loader import load_documents
from rag.vector_store import create_vectorstore

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
pdf_path = os.path.join(BASE_DIR, "data", "docs.pdf")

if not os.path.exists(pdf_path):
    print(f"[build_index] PDF not found at: {pdf_path}")
    sys.exit(1)

docs = load_documents(pdf_path)
create_vectorstore(docs)   # upserts into Qdrant persistent storage

print("[build_index] Index created successfully in Qdrant.")