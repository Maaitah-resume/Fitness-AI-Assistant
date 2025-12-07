// Basic chat client for the Fitness AI Assistant (no persistent history)
const API_URL = window.location.origin + "/chat";
const STORAGE_KEY = "fitness_ai_chat_history";

// Local, in-memory history that mirrors what is persisted and sent to the backend
let chatHistory = [];

// Keep an in-memory array so we can re-render if needed
const chatMessages = [];

window.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");

    loadChatHistory();
    
    input.addEventListener("keypress", function(e) {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Focus input on load
    input.focus();
});

function addMessage(text, sender, isError = false, skipSave = false) {
    const chatBox = document.getElementById("chat-box");

    // Remove welcome message if it exists
    const welcomeMsg = chatBox.querySelector(".welcome-msg");
    if (welcomeMsg) {
        welcomeMsg.remove();
    }
    
    const messageDiv = document.createElement("div");
    const bubbleSender = role === "assistant" ? "ai" : "user";
    messageDiv.className = `message ${bubbleSender}-msg`;
    
    const avatar = document.createElement("div");
    avatar.className = "message-avatar";
    avatar.textContent = role === "user" ? "👤" : "🤖";
    
    const content = document.createElement("div");
    content.className = `message-content ${isError ? "error-msg" : ""}`;
    
    // Format text with line breaks
    content.innerHTML = formatMessage(text);
    
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(content);
    
    chatBox.appendChild(messageDiv);
    chatBox.scrollTop = chatBox.scrollHeight;

    if (!skipSave) {
        persistMessage({ content: text, role, isError });
    }
}

function loadChatHistory() {
    const chatBox = document.getElementById("chat-box");
    const rawHistory = localStorage.getItem(STORAGE_KEY);

    if (!rawHistory) return;

    try {
        const history = JSON.parse(rawHistory);
        if (!Array.isArray(history)) return;

        chatBox.querySelector(".welcome-msg")?.remove();
        chatHistory = history
            .map((entry) => {
                // Support both new (role/content) and legacy (sender/text) keys
                if (entry?.role && entry?.content) {
                    return entry;
                }
                if (entry?.sender && entry?.text) {
                    return { role: entry.sender === "ai" ? "assistant" : entry.sender, content: entry.text, isError: entry.isError };
                }
                return null;
            })
            .filter(Boolean);

        chatHistory.forEach((entry) => {
            addMessage(entry.content, entry.role, entry.isError, true);
        });
    } catch (error) {
        console.error("Failed to load chat history:", error);
        localStorage.removeItem(STORAGE_KEY);
    }
}

function persistMessage(message) {
    try {
        chatHistory.push({ role: message.role, content: message.content, isError: message.isError });

        // Keep recent history to avoid unbounded growth
        const trimmed = chatHistory.slice(-100);
        chatHistory = trimmed;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    } catch (error) {
        console.error("Failed to save chat history:", error);
    }
}

    const bubble = document.createElement("div");
    bubble.className = "bubble fade-in";
    bubble.innerHTML = formatMessage(text);

    row.appendChild(avatar);
    row.appendChild(bubble);
    chatArea.appendChild(row);

    chatMessages.push({ role, content: text });
    chatArea.scrollTop = chatArea.scrollHeight;
}

function setLoading(isLoading) {
    const sendBtn = document.getElementById("send-btn");
    const input = document.getElementById("user-input");
    const icon = document.getElementById("send-icon");

    if (isLoading) {
        sendBtn.disabled = true;
        input.disabled = true;
        icon.classList.add("spinning");
    } else {
        sendBtn.disabled = false;
        input.disabled = false;
        icon.classList.remove("spinning");
        input.focus();
    }
}

async function sendMessage() {
    const input = document.getElementById("user-input");
    const text = input.value.trim();

    if (!text) return;
    
    // Show the user message immediately but avoid saving it until we have the official history from the backend.
    addMessage(text, "user", false, true);
    input.value = "";
    setLoading(true);

    const payload = {
        message: text,
        // Only send the role/content fields required by the API.
        history: chatHistory.map(({ role, content }) => ({ role, content }))
    };
    
    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message: text })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        // Trust the server's history to stay aligned, then re-render the latest assistant reply.
        if (Array.isArray(data.history)) {
            const trimmed = data.history.slice(-100);
            chatHistory = trimmed;
            localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
        } else {
            // Fallback: append the two most recent messages if history was missing.
            chatHistory.push({ role: "user", content: text });
            chatHistory.push({ role: "assistant", content: data.reply });
            localStorage.setItem(STORAGE_KEY, JSON.stringify(chatHistory.slice(-100)));
        }

        addMessage(data.reply, "assistant", false, true);

    } catch (error) {
        console.error("Error:", error);
        // Preserve the user's latest message locally so it isn't lost if the request fails.
        chatHistory.push({ role: "user", content: text });
        localStorage.setItem(STORAGE_KEY, JSON.stringify(chatHistory.slice(-100)));
        let errorMsg = "Error: Could not connect to the backend server.";

        if (error.message.includes("Failed to fetch")) {
            errorMsg += "<br><br>Make sure the backend server is running on <code>http://localhost:8000</code>";
        } else {
            errorMsg += `<br><br>${error.message}`;
        }
        
        addMessage(errorMsg, "assistant", true);
    } finally {
        setLoading(false);
    }
}
