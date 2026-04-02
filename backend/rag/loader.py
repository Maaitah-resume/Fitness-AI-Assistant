from langchain_community.document_loaders import PyPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter


def load_documents(path: str):
    """
    Load a PDF file from *path* and split it into small overlapping chunks
    suitable for embedding and retrieval.
    """
    loader = PyPDFLoader(path)
    docs = loader.load()

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=500,
        chunk_overlap=50,
    )

    chunks = splitter.split_documents(docs)
    print(f"[RAG] Loaded {len(docs)} page(s), split into {len(chunks)} chunks.")
    return chunks