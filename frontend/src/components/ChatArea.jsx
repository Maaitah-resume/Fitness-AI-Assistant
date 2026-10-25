// ChatArea.jsx – the coaching thread: messages plus one composer for text and PDF upload
import React, { useState, useEffect, useRef } from "react";
import { Send, Loader2, Paperclip, X, FileDown, BookOpen, Check, TrendingUp, Globe } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { apiDownload, apiFetch, errorMessage } from "../api";
import { useI18n } from "../i18n";

// Inline source markers from the coach: [S1] (a document) or [W2] (a web page), possibly grouped: [S1, W2].
const CITE_MARKER = /\[((?:[SW]\d+)(?:\s*,\s*[SW]\d+)*)\]/g;

/** Turns markers into numbered links ReactMarkdown can render; markers without a known source are dropped. */
function linkCitations(text, citations = []) {
  if (!text) return text;
  return text.replace(CITE_MARKER, (_, ids) =>
    ids.split(/\s*,\s*/)
      .map((id) => citations.findIndex((c) => c.id === id))
      .filter((n) => n >= 0)
      .map((n) => `[${n + 1}](#cite-${citations[n].id})`)
      .join(""));
}

/** One assistant reply: Markdown with superscript citation links. */
function AssistantText({ content, citations }) {
  const components = {
    a: ({ href = "", children, ...rest }) => {
      if (href.startsWith("#cite-")) {
        const c = citations.find((x) => `#cite-${x.id}` === href);
        if (!c) return null;
        return (
          <sup className="cite">
            {c.url
              ? <a href={c.url} target="_blank" rel="noreferrer noopener" title={c.title}>{children}</a>
              : <span title={c.title}>{children}</span>}
          </sup>
        );
      }
      return <a href={href} target="_blank" rel="noreferrer noopener" {...rest}>{children}</a>;
    },
  };
  return (
    <div className="markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>{linkCitations(content, citations)}</ReactMarkdown>
    </div>
  );
}

function ChatArea({ currentChatId, onChatCreated }) {
  const { t, lang } = useI18n();
  /* ─────────────────────────  STATE  ───────────────────────── */
  const [messages,     setMessages]     = useState([]);
  const [input,        setInput]        = useState("");
  const [loading,      setLoading]      = useState(false);
  const [uploading,    setUploading]    = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [activeChatId, setActiveChatId] = useState(currentChatId);

  const messagesEndRef = useRef(null);
  const fileInputRef   = useRef(null);
  const createdChatIdRef = useRef(null);

  /* ─────────────────────────  HELPERS  ─────────────────────── */
  const scrollToBottom = () =>
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });

  // isNew marks messages added in this session, so only they animate in (not loaded history).
  const pushMessage = (role, content, isError = false, extras = {}) =>
    setMessages((prev) => [...prev, { role, content, isError, isNew: true, ...extras }]);

  const download = async (file) => {
    try {
      await apiDownload(file.downloadUrl, file.filename);
    } catch (err) {
      pushMessage("assistant", err.message, true);
    }
  };

  /* ─────────────────────────  EFFECTS  ─────────────────────── */
  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    setActiveChatId(currentChatId);
    // A chat we just created in handleSend is already on screen; reloading would
    // drop replies the server didn't store (e.g. the "AI unavailable" fallback).
    if (currentChatId && currentChatId === createdChatIdRef.current) {
      createdChatIdRef.current = null;
      return;
    }
    if (currentChatId) {
      loadHistory(currentChatId);
    } else {
      // Rendered from the dictionary at display time, so it follows the language toggle.
      setMessages([{ role: "assistant", welcome: true }]);
    }
    setSelectedFile(null);
  }, [currentChatId]);

  /* ─────────────────────────  API CALLS  ───────────────────── */
  const loadHistory = async (chatId) => {
    try {
      const result = await apiFetch(`/api/v1/chat/${chatId}/messages`);
      if (result.status === "success") {
        setMessages(
          result.data.messages.map((m) => ({
            role:    m.role,
            content: m.message,
            citations: m.citations || [],
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
    pushMessage("user", t("chat.attached", { name: selectedFile.name }));

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      const data = await apiFetch("/api/v1/files/upload", { method: "POST", body: formData });

      if (data.status === "success" && data.data?.status === "indexed") {
        pushMessage("assistant", t("chat.uploadOk", { msg: data.message }));
      } else if (data.status === "success") {
        pushMessage("assistant", data.message, true);
      } else {
        pushMessage("assistant", t("chat.uploadFailed", { err: errorMessage(data, "?") }), true);
      }
    } catch (err) {
      console.error("Upload error:", err);
      pushMessage("assistant", t("chat.uploadFailedType"), true);
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
      const data = await apiFetch("/api/v1/chat/send", {
        method:  "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: userMsg,
          chat_id: activeChatId || null,
          language: lang,
        }),
      });

      if (data.status === "success") {
        // If backend created a new chat, store it
        if (data.data?.chat_id && !activeChatId) {
          setActiveChatId(data.data.chat_id);
          createdChatIdRef.current = data.data.chat_id;
          onChatCreated?.(data.data.chat_id);
        }
        pushMessage("assistant", data.data?.response || t("chat.noResponse"), !!data.data?.degraded, {
          files: data.data?.files || [],
          citations: data.data?.citations || [],
          profileUpdated: !!data.data?.profile_updated,
          progressLogged: !!data.data?.progress_logged,
        });
      } else {
        pushMessage("assistant", errorMessage(data, t("chat.sendFailed")), true);
      }
    } catch (err) {
      console.error("Chat error:", err);
      pushMessage("assistant", t("chat.offline"), true);
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
  const busy = loading || uploading;

  return (
    <section className="thread" aria-label={t("chat.region")}>
      <div className="thread-messages" aria-live="polite">
        {messages.map((msg, i) => (
          <div key={i} className={`msg msg-${msg.role}${msg.isError ? " msg-error" : ""}${msg.isNew ? " msg-new" : ""}`}>
            <span className="msg-who">{msg.role === "assistant" ? t("chat.coach") : t("chat.you")}</span>
            <div className="msg-body" dir="auto">
              {/* Assistant replies are Markdown (headings, bold, lists, tables); user text stays literal. */}
              {msg.welcome
                ? t("chat.welcome")
                : msg.role === "assistant"
                  ? <AssistantText content={msg.content} citations={msg.citations || []} />
                  : msg.content}
            </div>
            {msg.files?.length > 0 && (
              <div className="msg-files">
                {msg.files.map((f) => (
                  <button key={f.id} type="button" className="btn btn-small" onClick={() => download(f)}>
                    <FileDown size={15} aria-hidden="true" /> {t("chat.download", { name: f.filename })}
                  </button>
                ))}
              </div>
            )}
            {msg.citations?.length > 0 && (
              <div className="msg-sources">
                <span className="msg-sources-title">{t("chat.sources")}</span>
                <ol>
                  {msg.citations.map((c) => (
                    <li key={c.id}>
                      {c.kind === "web"
                        ? <><Globe size={13} aria-hidden="true" /> <a href={c.url} target="_blank" rel="noreferrer noopener" dir="auto">{c.title}</a></>
                        : <><BookOpen size={13} aria-hidden="true" /> <span dir="auto">{c.title}</span> <span className="msg-sources-kind">{t("chat.sourceDoc")}</span></>}
                    </li>
                  ))}
                </ol>
              </div>
            )}
            {(msg.profileUpdated || msg.progressLogged) && (
              <div className="msg-meta">
                {msg.progressLogged && <span><TrendingUp size={13} aria-hidden="true" /> {t("chat.progressLogged")}</span>}
                {msg.profileUpdated && <span><Check size={13} aria-hidden="true" /> {t("chat.profileUpdated")}</span>}
              </div>
            )}
          </div>
        ))}

        {busy && (
          <div className="msg msg-assistant msg-typing msg-new">
            <span className="msg-who">{t("chat.coach")}</span>
            <div className="msg-body">
              <Loader2 className="spin" size={16} aria-hidden="true" />
              <span>{uploading ? t("chat.readingPdf") : t("chat.thinking")}</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <form className="composer" onSubmit={handleSubmit}>
        <div className="composer-box">
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf"
            hidden
            onChange={(e) => setSelectedFile(e.target.files[0] || null)}
          />
          <button
            type="button"
            className="icon-btn"
            aria-label={t("chat.attachPdf")}
            title={t("chat.attachPdf")}
            onClick={() => fileInputRef.current?.click()}
            disabled={busy}
          >
            <Paperclip size={18} aria-hidden="true" />
          </button>

          {selectedFile && (
            <span className="composer-file">
              <span className="composer-file-name">{selectedFile.name}</span>
              <button
                type="button"
                className="icon-btn"
                aria-label={t("chat.remove", { name: selectedFile.name })}
                onClick={() => {
                  setSelectedFile(null);
                  if (fileInputRef.current) fileInputRef.current.value = "";
                }}
              >
                <X size={14} aria-hidden="true" />
              </button>
            </span>
          )}

          <label htmlFor="composer-input" className="visually-hidden">{t("chat.messageCoach")}</label>
          <input
            id="composer-input"
            type="text"
            className="composer-input"
            placeholder={selectedFile ? t("chat.placeholderPdf") : t("chat.placeholder")}
            dir="auto"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={busy}
            autoComplete="off"
          />

          <button
            type="submit"
            className="btn btn-primary btn-small"
            disabled={busy || (!input.trim() && !selectedFile)}
          >
            {busy ? <Loader2 size={16} className="spin" aria-hidden="true" /> : <Send size={16} aria-hidden="true" className="flip-rtl" />}
            {t("chat.send")}
          </button>
        </div>
        <p className="composer-hint">{t("chat.hint")}</p>
      </form>
    </section>
  );
}

export default ChatArea;
