// ChatArea.jsx – ChatGPT-style: one input bar, file upload + chat
import React, { useState, useEffect, useRef } from "react";
import { Send, Bot, User, Loader2, Paperclip, X } from "lucide-react";

function ChatArea({ currentChatId, onChatCreated }) {
  /* ─────────────────────────  STATE  ───────────────────────── */
  const [messages,     setMessages]     = useState([]);
  const [input,        setInput]        = useState("");
  const [loading,      setLoading]      = useState(false);
  const [uploading,    setUploading]    = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [activeChatId, setActiveChatId] = useState(currentChatId);

  const messagesEndRef = useRef(null);
  const fileInputRef   = useRef(null);

  const userEmail = localStorage.getItem("user_email") || "default_user@example.com";

  /* ─────────────────────────  HELPERS  ─────────────────────── */
  const scrollToBottom = () =>
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });

  const pushMessage = (role, content, isError = false) =>
    setMessages((prev) => [...prev, { role, content, isError }]);

  /* ─────────────────────────  EFFECTS  ─────────────────────── */
  useEffect(scrollToBottom, [messages]);

  useEffect(() => {
    setActiveChatId(currentChatId);
    if (currentChatId) {
      loadHistory(currentChatId);
    } else {
      setMessages([
        {
          role: "assistant",
          content:
            "Welcome! I'm your Fitness AI. Ask me anything about fitness, nutrition, or upload a PDF and I'll answer questions about it.",
        },
      ]);
    }
    setSelectedFile(null);
  }, [currentChatId]);

  /* ─────────────────────────  API CALLS  ───────────────────── */
  const loadHistory = async (chatId) => {
    try {
      const res = await fetch(
        `/api/v1/chats/${chatId}/messages/${encodeURIComponent(userEmail)}`
      );
      const result = await res.json();
      if (result.status === "success") {
        setMessages(
          result.data.messages.map((m) => ({
            role:    m.role,
            content: m.message,
          }))
        );
      }
    } catch (err) {
      console.error("History load error:", err);
    }
  };

  // ── UPLOAD PDF ──────────────────────────────────────────────
  const handleUpload = async () => {
    if (!selectedFile) return;
    setUploading(true);
    pushMessage("user", `📎 Uploading: ${selectedFile.name}`);

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      const res  = await fetch("/api/v1/upload", { method: "POST", body: formData });
      const data = await res.json();

      if (data.status === "success") {
        pushMessage("assistant", `✅ ${data.message} You can now ask me about it!`);
      } else {
        pushMessage("assistant", `❌ Upload failed: ${data.message}`, true);
      }
    } catch (err) {
      console.error("Upload error:", err);
      pushMessage("assistant", "❌ Upload failed — check file type and try again.", true);
    } finally {
      setUploading(false);
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  // ── SEND CHAT MESSAGE ───────────────────────────────────────
  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMsg = input.trim();
    setInput("");
    pushMessage("user", userMsg);
    setLoading(true);

    try {
      const res = await fetch("/api/v1/chats/send", {
        method:  "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message:    userMsg,
          user_email: userEmail,
          chat_id:    activeChatId || null,
        }),
      });
      const data = await res.json();

      if (data.status === "success") {
        // If backend created a new chat, store it
        if (data.data?.chat_id && !activeChatId) {
          setActiveChatId(data.data.chat_id);
          onChatCreated?.(data.data.chat_id);
        }
        pushMessage("assistant", data.data?.response || "No response received.");
      } else {
        pushMessage("assistant", "⚠️ Something went wrong. Please try again.", true);
      }
    } catch (err) {
      console.error("Chat error:", err);
      pushMessage("assistant", "❌ Connection error — try again later.", true);
    } finally {
      setLoading(false);
    }
  };

  // ── UNIFIED SUBMIT ──────────────────────────────────────────
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (selectedFile) {
      await handleUpload();       // upload first
    }
    if (input.trim()) {
      await handleSend();         // then send the message (or send alone)
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  /* ─────────────────────────  RENDER  ──────────────────────── */
  return (
    <section className="chat-area glass">
      {/* ── Message list ── */}
      <div className="messages-container">
        {messages.map((msg, i) => (
          <div key={i} className={`message-row ${msg.role}`}>
            <div className="avatar">
              {msg.role === "assistant" ? <Bot size={20} /> : <User size={20} />}
            </div>
            <div className={`message-bubble ${msg.isError ? "error" : ""}`}>
              {msg.content}
            </div>
          </div>
        ))}

        {/* Thinking indicator */}
        {loading && (
          <div className="message-row assistant">
            <div className="avatar"><Bot size={20} /></div>
            <div className="message-bubble typing">
              <Loader2 className="spinner" size={18} />
              <span>Thinking…</span>
            </div>
          </div>
        )}

        {/* Upload indicator */}
        {uploading && (
          <div className="message-row assistant">
            <div className="avatar"><Bot size={20} /></div>
            <div className="message-bubble typing">
              <Loader2 className="spinner" size={18} />
              <span>Indexing your PDF…</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* ── Input bar ── */}
      <form className="input-area" onSubmit={handleSubmit}>
        <div className="input-glass">

          {/* Hidden file input */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf"
            style={{ display: "none" }}
            onChange={(e) => setSelectedFile(e.target.files[0] || null)}
          />

          {/* Paperclip button */}
          <button
            type="button"
            className="icon-btn paperclip"
            title="Upload PDF"
            onClick={() => fileInputRef.current?.click()}
            disabled={loading || uploading}
          >
            <Paperclip size={18} />
          </button>

          {/* File pill + text input always visible together */}
          <div className="input-middle">
            {selectedFile && (
              <div className="file-pill">
                <span className="file-name">{selectedFile.name}</span>
                <button
                  type="button"
                  className="remove-file"
                  onClick={() => {
                    setSelectedFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = "";
                  }}
                >
                  <X size={14} />
                </button>
              </div>
            )}
            <input
              type="text"
              className="text-input"
              placeholder={selectedFile ? "Ask about your PDF…" : "Ask a fitness question…"}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading || uploading}
            />
          </div>

          {/* Send / Upload button */}
          <button
            type="submit"
            className="icon-btn send"
            disabled={loading || uploading || (!input.trim() && !selectedFile)}
          >
            {loading || uploading
              ? <Loader2 size={18} className="spinner" />
              : <Send size={18} />}
          </button>
        </div>
      </form>

      {/* ── Styles ── */}
      <style jsx>{`
        .chat-area {
          flex: 1;
          border-radius: 24px;
          display: flex;
          flex-direction: column;
          overflow: hidden;
        }
        .messages-container {
          flex: 1;
          padding: 2.5rem;
          overflow-y: auto;
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
        }
        .message-row {
          display: flex;
          gap: 1rem;
          max-width: 85%;
          animation: messageReveal 0.3s ease-out backwards;
        }
        @keyframes messageReveal {
          from { opacity: 0; transform: translateY(8px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .message-row.user {
          align-self: flex-end;
          flex-direction: row-reverse;
        }
        .avatar {
          width: 38px;
          height: 38px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(255,255,255,0.05);
          border: 1px solid var(--glass-border);
          flex-shrink: 0;
        }
        .assistant .avatar {
          background: rgba(99,102,241,0.2);
          border-color: rgba(99,102,241,0.3);
          color: var(--primary-light);
        }
        .user .avatar {
          background: rgba(244,63,94,0.2);
          border-color: rgba(244,63,94,0.3);
          color: var(--accent);
        }
        .message-bubble {
          padding: 0.9rem 1.3rem;
          border-radius: 18px;
          line-height: 1.65;
          white-space: pre-wrap;
          box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        }
        .assistant .message-bubble {
          background: rgba(255,255,255,0.04);
          border: 1px solid var(--glass-border);
          border-top-left-radius: 4px;
        }
        .user .message-bubble {
          background: linear-gradient(135deg, var(--primary), var(--primary-dark));
          border-top-right-radius: 4px;
        }
        .message-bubble.error {
          border-color: var(--accent);
          color: var(--accent);
        }
        .typing {
          display: flex;
          align-items: center;
          gap: 0.6rem;
          color: var(--text-muted);
        }
        .spinner {
          animation: spin 1.2s linear infinite;
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }

        /* ── Input area ── */
        .input-area {
          padding: 1.5rem 2rem;
          background: rgba(15,23,42,0.3);
          border-top: 1px solid var(--glass-border);
        }
        .input-glass {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          background: rgba(255,255,255,0.05);
          padding: 0.5rem 0.75rem 0.5rem 1rem;
          border-radius: 100px;
          border: 1px solid var(--glass-border);
          transition: border-color 0.25s, box-shadow 0.25s;
        }
        .input-glass:focus-within {
          border-color: var(--primary);
          box-shadow: 0 0 0 4px rgba(99,102,241,0.15);
        }
        .text-input {
          flex: 1;
          background: transparent;
          border: none;
          color: white;
          outline: none;
          font-family: inherit;
          font-size: 1rem;
          min-width: 0;
        }
        .text-input::placeholder { color: var(--text-muted); }

        /* File pill shown when a file is selected */
        .file-pill {
          flex: 1;
          display: flex;
          align-items: center;
          gap: 0.5rem;
          background: rgba(99,102,241,0.15);
          border: 1px solid rgba(99,102,241,0.35);
          border-radius: 100px;
          padding: 0.3rem 0.75rem;
          min-width: 0;
        }
        .file-name {
          flex: 1;
          font-size: 0.85rem;
          color: var(--primary-light);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .remove-file {
          background: transparent;
          border: none;
          cursor: pointer;
          color: var(--text-muted);
          display: flex;
          align-items: center;
          padding: 0;
          flex-shrink: 0;
        }
        .remove-file:hover { color: var(--accent); }

        /* Icon buttons (paperclip + send) */
        .icon-btn {
          background: transparent;
          border: none;
          cursor: pointer;
          color: var(--text-muted);
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 6px;
          border-radius: 50%;
          transition: color 0.2s, background 0.2s;
          flex-shrink: 0;
        }
        .icon-btn:hover:not(:disabled) {
          color: white;
          background: rgba(255,255,255,0.08);
        }
        .icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
        .icon-btn.paperclip:hover:not(:disabled) { color: var(--primary-light); }
        .icon-btn.send {
          background: var(--primary);
          color: white;
          padding: 8px;
        }
        .icon-btn.send:hover:not(:disabled) { background: var(--primary-dark); }
        .icon-btn.send:disabled { background: rgba(99,102,241,0.3); }
        /* Middle flex area holding pill + text input */
        .input-middle {
          flex: 1;
          display: flex;
          align-items: center;
          gap: 0.5rem;
          min-width: 0;
        }
        .input-middle .text-input {
          flex: 1;
          min-width: 0;
        }

      `}</style>
    </section>
  );
}

export default ChatArea;