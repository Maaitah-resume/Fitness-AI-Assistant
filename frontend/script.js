// Auto-detect API URL based on current host
const API_URL = window.location.origin + "/chat";
const STORAGE_KEY = "fitness_ai_chat_history";

// Local, in-memory history that mirrors what is persisted and sent to the backend
let chatHistory = [];

// Initialize - add Enter key support
document.addEventListener('DOMContentLoaded', function() {
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

function addMessage(text, role, isError = false, skipSave = false) {
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

        // Keep the drawer view in sync with any restored history.
        renderHistoryDrawer();
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

        // Refresh the drawer contents if it is currently visible.
        renderHistoryDrawer();
    } catch (error) {
        console.error("Failed to save chat history:", error);
    }
}

// Toggle the side drawer that lets users review previously saved messages.
function toggleHistoryDrawer() {
    const drawer = document.getElementById("history-drawer");
    if (!drawer) return;

    const isOpen = drawer.classList.contains("open");
    if (isOpen) {
        drawer.classList.remove("open");
        drawer.setAttribute("aria-hidden", "true");
        return;
    }

    // Render the latest history snapshot right before showing the drawer.
    renderHistoryDrawer();
    drawer.classList.add("open");
    drawer.setAttribute("aria-hidden", "false");
}

// Render the history entries inside the drawer using the in-memory history array.
function renderHistoryDrawer() {
    const list = document.getElementById("history-list");
    if (!list) return;

    list.innerHTML = "";

    if (!chatHistory.length) {
        const empty = document.createElement("div");
        empty.className = "history-empty";
        empty.textContent = "No saved messages yet. Start chatting to build history.";
        list.appendChild(empty);
        return;
    }

    chatHistory.forEach((entry, idx) => {
        const item = document.createElement("div");
        item.className = "history-entry";

        const label = document.createElement("strong");
        label.textContent = `${idx + 1}. ${entry.role === "assistant" ? "Assistant" : "You"}`;

        const content = document.createElement("div");
        content.innerHTML = formatMessage(entry.content);

        item.appendChild(label);
        item.appendChild(content);
        list.appendChild(item);
    });
}

// Clear local history storage and update both the drawer and the in-memory array.
function clearHistory() {
    chatHistory = [];
    localStorage.removeItem(STORAGE_KEY);
    renderHistoryDrawer();
}

function formatMessage(text) {
    // Convert line breaks to <br>
    let formatted = text.replace(/\n/g, "<br>");
    
    // Format numbered lists
    formatted = formatted.replace(/(\d+\.\s+[^\n]+)/g, "<div style='margin: 4px 0; padding-left: 10px;'>$1</div>");
    
    // Format bold text (**text**)
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    
    // Format code-like text (backticks)
    formatted = formatted.replace(/`([^`]+)`/g, "<code style='background: rgba(0,0,0,0.1); padding: 2px 6px; border-radius: 4px; font-family: monospace;'>$1</code>");
    
    return formatted;
}

function setLoading(isLoading) {
    const sendBtn = document.getElementById("send-btn");
    const sendText = document.getElementById("send-text");
    const spinner = document.getElementById("loading-spinner");
    const input = document.getElementById("user-input");
    
    if (isLoading) {
        sendBtn.disabled = true;
        sendText.style.display = "none";
        spinner.style.display = "inline";
        input.disabled = true;
    } else {
        sendBtn.disabled = false;
        sendText.style.display = "inline";
        spinner.style.display = "none";
        input.disabled = false;
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
            body: JSON.stringify(payload)
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
            renderHistoryDrawer();
        } else {
            // Fallback: append the two most recent messages if history was missing.
            chatHistory.push({ role: "user", content: text });
            chatHistory.push({ role: "assistant", content: data.reply });
            localStorage.setItem(STORAGE_KEY, JSON.stringify(chatHistory.slice(-100)));
            renderHistoryDrawer();
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
