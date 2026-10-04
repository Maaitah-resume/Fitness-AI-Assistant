"""
vector_store.py — Hybrid dense + sparse indexing and RRF search.

Dense  : OpenAI text-embedding-3-small (1536-dim, via API)
Sparse : scikit-learn TF-IDF           (pure Python, no Rust needed)
Fusion : Reciprocal Rank Fusion (RRF)  via Qdrant native query_points
"""
import os
import pickle
import uuid
from dotenv import load_dotenv

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from openai import OpenAI
from qdrant_client import QdrantClient, models

load_dotenv()

# ── Config ────────────────────────────────────────────────────────────────────
BACKEND_DIR      = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QDRANT_PATH      = os.path.join(BACKEND_DIR, "qdrant_storage")
VECTORIZER_PATH  = os.path.join(BACKEND_DIR, "tfidf_vectorizer.pkl")
COLLECTION_NAME  = "fitness_docs"

DENSE_DIM        = 1536        # text-embedding-3-small
DENSE_NAME       = "dense"
SPARSE_NAME      = "sparse"

# ── Clients / model singletons ────────────────────────────────────────────────
_openai_client  = None
_tfidf          = None          # fitted TfidfVectorizer (loaded from disk)


def _get_openai() -> OpenAI:
    global _openai_client
    if _openai_client is None:
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise ValueError("OPENAI_API_KEY is not set in your .env file.")
        _openai_client = OpenAI(api_key=api_key)
    return _openai_client


def _get_tfidf() -> TfidfVectorizer | None:
    """Load the fitted TF-IDF vectorizer from disk (None if not created yet)."""
    global _tfidf
    if _tfidf is not None:
        return _tfidf
    if os.path.exists(VECTORIZER_PATH):
        with open(VECTORIZER_PATH, "rb") as f:
            _tfidf = pickle.load(f)
    return _tfidf


def _save_tfidf(vectorizer: TfidfVectorizer):
    global _tfidf
    _tfidf = vectorizer
    with open(VECTORIZER_PATH, "wb") as f:
        pickle.dump(vectorizer, f)
    print(f"[TF-IDF] Vectorizer saved to '{VECTORIZER_PATH}'.")


def _get_client() -> QdrantClient:
    return QdrantClient(path=QDRANT_PATH)


# ── Collection setup ──────────────────────────────────────────────────────────
def _ensure_collection(client: QdrantClient):
    """Create hybrid collection (dense + sparse) if it doesn't exist yet."""
    existing = [c.name for c in client.get_collections().collections]
    if COLLECTION_NAME in existing:
        return

    client.create_collection(
        collection_name=COLLECTION_NAME,
        vectors_config={
            DENSE_NAME: models.VectorParams(
                size=DENSE_DIM,
                distance=models.Distance.COSINE,
            )
        },
        sparse_vectors_config={
            SPARSE_NAME: models.SparseVectorParams(
                index=models.SparseIndexParams(on_disk=False)
            )
        },
    )
    print(f"[Qdrant] Hybrid collection '{COLLECTION_NAME}' created.")


# ── Dense embedding via OpenAI ────────────────────────────────────────────────
def _embed_dense(texts: list[str]) -> list[list[float]]:
    """Embed a list of texts using OpenAI in one batched API call."""
    client = _get_openai()
    response = client.embeddings.create(
        model="text-embedding-3-small",
        input=texts,
    )
    return [item.embedding for item in response.data]


# ── Sparse embedding via TF-IDF ───────────────────────────────────────────────
def _sparse_from_matrix(row) -> models.SparseVector:
    """Convert one row of a scipy sparse matrix → Qdrant SparseVector."""
    cx = row.tocoo()
    indices = cx.col.tolist()
    values  = cx.data.tolist()
    return models.SparseVector(indices=indices, values=values)


# ── Indexing ──────────────────────────────────────────────────────────────────
def create_vectorstore(docs) -> None:
    """
    Embed every chunk with both dense (OpenAI) and sparse (TF-IDF) vectors,
    then upsert into the persistent Qdrant collection.

    The TF-IDF vectorizer is fitted on ALL texts currently being uploaded,
    then merged with any existing vectorizer on disk so previous PDFs
    remain searchable.
    """
    global _tfidf

    client = _get_client()
    _ensure_collection(client)

    texts     = [doc.page_content for doc in docs]
    metadatas = [doc.metadata     for doc in docs]

    # ── 1. Fit / update TF-IDF vectorizer ────────────────────────────────────
    existing = _get_tfidf()
    if existing is not None:
        # Re-fit on old vocabulary union new texts so old queries still work
        all_texts = list(existing.get_feature_names_out()) + texts
        new_vect  = TfidfVectorizer(sublinear_tf=True)
        new_vect.fit(all_texts)
    else:
        new_vect = TfidfVectorizer(sublinear_tf=True)
        new_vect.fit(texts)

    _save_tfidf(new_vect)

    # ── 2. Compute sparse vectors ─────────────────────────────────────────────
    sparse_matrix  = new_vect.transform(texts)   # scipy csr_matrix

    # ── 3. Compute dense vectors (batched) ────────────────────────────────────
    dense_vectors = _embed_dense(texts)

    # ── 4. Build and upsert PointStructs ─────────────────────────────────────
    points = []
    for i, (text, meta, dense_vec) in enumerate(
        zip(texts, metadatas, dense_vectors)
    ):
        sparse_vec = _sparse_from_matrix(sparse_matrix[i])
        points.append(
            models.PointStruct(
                id=str(uuid.uuid4()),
                vector={
                    DENSE_NAME:  dense_vec,
                    SPARSE_NAME: sparse_vec,
                },
                payload={"text": text, **meta},
            )
        )

    batch_size = 64
    for start in range(0, len(points), batch_size):
        client.upsert(
            collection_name=COLLECTION_NAME,
            points=points[start : start + batch_size],
        )

    print(
        f"[Qdrant] {len(points)} chunks upserted "
        f"(dense OpenAI + sparse TF-IDF) into '{COLLECTION_NAME}'."
    )


# ── Hybrid RRF search (exact docs pattern) ────────────────────────────────────
def rrf_search(query: str, k: int = 4) -> list[models.ScoredPoint]:
    """
    Hybrid search using Qdrant's native prefetch + RRF fusion.

    Sparse prefetch  → TF-IDF keyword match  (top k candidates)
    Dense  prefetch  → OpenAI semantic match  (top k candidates)
    FusionQuery(RRF) → merges both ranked lists into one final ranking
    """
    tfidf = _get_tfidf()
    if tfidf is None:
        print("[RAG] No TF-IDF vectorizer found. Upload a PDF first.")
        return []

    client = _get_client()

    # Compute query vectors
    query_sparse_matrix = tfidf.transform([query])
    query_sparse        = _sparse_from_matrix(query_sparse_matrix[0])
    query_dense         = _embed_dense([query])[0]

    response = client.query_points(
        collection_name=COLLECTION_NAME,
        prefetch=[
            # ── Sparse: TF-IDF keyword search ────────────────────────────────
            models.Prefetch(
                query=query_sparse,
                using=SPARSE_NAME,
                limit=k,
            ),
            # ── Dense: OpenAI semantic search ─────────────────────────────────
            models.Prefetch(
                query=query_dense,
                using=DENSE_NAME,
                limit=k,
            ),
        ],
        # ── Fuse with RRF ─────────────────────────────────────────────────────
        query=models.FusionQuery(fusion=models.Fusion.RRF),
        limit=k,
    )

    return response.points


# ── Health check ──────────────────────────────────────────────────────────────
def is_collection_ready() -> bool:
    """True if the collection exists, has vectors, and TF-IDF is fitted."""
    try:
        if not os.path.exists(VECTORIZER_PATH):
            return False
        client   = _get_client()
        existing = [c.name for c in client.get_collections().collections]
        if COLLECTION_NAME not in existing:
            return False
        info = client.get_collection(COLLECTION_NAME)
        return info.points_count > 0
    except Exception:
        return False