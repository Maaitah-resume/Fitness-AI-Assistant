"""
chain.py — Custom LangChain retriever that calls Qdrant's native
           hybrid RRF search (prefetch sparse + dense → FusionQuery RRF).
"""
import os
from typing import List

from langchain_openai import ChatOpenAI
from langchain_classic.chains import RetrievalQA
from langchain_core.retrievers import BaseRetriever
from langchain_core.documents import Document
from langchain_core.callbacks.manager import CallbackManagerForRetrieverRun

from .vector_store import rrf_search, is_collection_ready


# ── Custom retriever that uses Qdrant's native RRF ────────────────────────────
class HybridRRFRetriever(BaseRetriever):
    """
    Calls vector_store.rrf_search() which runs:
      Prefetch(sparse BM25) + Prefetch(dense MiniLM) → FusionQuery(RRF)

    Converts Qdrant ScoredPoints → LangChain Documents so RetrievalQA
    can inject them as context into the OpenAI prompt.
    """
    k: int = 4

    def _get_relevant_documents(
        self,
        query: str,
        *,
        run_manager: CallbackManagerForRetrieverRun = None,
        **kwargs,
    ) -> List[Document]:
        scored_points = rrf_search(query, k=self.k)

        docs = []
        for point in scored_points:
            text     = point.payload.get("text", "")
            metadata = {
                k: v for k, v in point.payload.items() if k != "text"
            }
            metadata["rrf_score"] = point.score
            docs.append(Document(page_content=text, metadata=metadata))

        return docs

    async def _aget_relevant_documents(
        self,
        query: str,
        *,
        run_manager: CallbackManagerForRetrieverRun = None,
        **kwargs,
    ) -> List[Document]:
        # Reuse the sync version (Qdrant local client is fast enough)
        return self._get_relevant_documents(query, run_manager=run_manager)


# ── Empty fallback ────────────────────────────────────────────────────────────
class _EmptyRetriever(BaseRetriever):
    """Used when no PDF has been indexed yet."""

    def _get_relevant_documents(self, query, **kwargs) -> List[Document]:
        return []

    async def _aget_relevant_documents(self, query, **kwargs) -> List[Document]:
        return []


# ── Chain builder ─────────────────────────────────────────────────────────────
def get_rag_chain() -> RetrievalQA:
    """
    Build a RetrievalQA chain using the custom HybridRRFRetriever.

    Search flow per query:
        1. Sparse prefetch  → BM25 keyword match, top-k candidates
        2. Dense  prefetch  → semantic similarity, top-k candidates
        3. FusionQuery(RRF) → merges both ranked lists
        4. Top-k results    → injected as context into OpenAI prompt
    """
    if is_collection_ready():
        retriever = HybridRRFRetriever(k=4)
        print("[RAG] HybridRRFRetriever ready (sparse BM25 + dense MiniLM → RRF).")
    else:
        retriever = _EmptyRetriever()
        print("[RAG] Empty retriever — no PDF indexed yet.")

    llm = ChatOpenAI(
        model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        temperature=0,
        api_key=os.getenv("OPENAI_API_KEY"),
    )

    return RetrievalQA.from_chain_type(
        llm=llm,
        retriever=retriever,
        return_source_documents=False,
    )