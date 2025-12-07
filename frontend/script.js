// Auto-detect API URL based on current host
const API_URL = window.location.origin + "/chat";
const STORAGE_KEY = "fitness_ai_chat_history";

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

function addMessage(text, sender, isError = false, skipSave = false) {
    const chatBox = document.getElementById("chat-box");
    
    // Remove welcome message if it exists
    const welcomeMsg = chatBox.querySelector(".welcome-msg");
    if (welcomeMsg) {
        welcomeMsg.remove();
    }
    
    const messageDiv = document.createElement("div");
    messageDiv.className = `message ${sender}-msg`;
    
    const avatar = document.createElement("div");
    avatar.className = "message-avatar";
    avatar.textContent = sender === "user" ? "👤" : "🤖";
    
    const content = document.createElement("div");
    content.className = `message-content ${isError ? "error-msg" : ""}`;
    
    // Format text with line breaks
    content.innerHTML = formatMessage(text);
    
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(content);
    
    chatBox.appendChild(messageDiv);
    chatBox.scrollTop = chatBox.scrollHeight;

    if (!skipSave) {
        persistMessage({ text, sender, isError });
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

        history.forEach((entry) => {
            if (entry?.text && entry?.sender) {
                addMessage(entry.text, entry.sender, entry.isError, true);
            }
        });
    } catch (error) {
        console.error("Failed to load chat history:", error);
        localStorage.removeItem(STORAGE_KEY);
    }
}

function persistMessage(message) {
    try {
        const rawHistory = localStorage.getItem(STORAGE_KEY);
        const history = rawHistory ? JSON.parse(rawHistory) : [];

        history.push(message);

        // Keep recent history to avoid unbounded growth
        const trimmed = history.slice(-100);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    } catch (error) {
        console.error("Failed to save chat history:", error);
    }
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
    
    addMessage(text, "user");
    input.value = "";
    setLoading(true);
    
    const payload = {
        message: text
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
        addMessage(data.reply, "ai");
        
    } catch (error) {
        console.error("Error:", error);
        let errorMsg = "Error: Could not connect to the backend server.";
        
        if (error.message.includes("Failed to fetch")) {
            errorMsg += "<br><br>Make sure the backend server is running on <code>http://localhost:8000</code>";
        } else {
            errorMsg += `<br><br>${error.message}`;
        }
        
        addMessage(errorMsg, "ai", true);
    } finally {
        setLoading(false);
    }
}
