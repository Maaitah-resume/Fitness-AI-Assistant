// Auto-detect API URL based on current host
const API_URL = window.location.origin + "/chat";

// Initialize - add Enter key support
document.addEventListener('DOMContentLoaded', function() {
    const input = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");
    
    input.addEventListener("keypress", function(e) {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // Focus input on load
    input.focus();
});

function addMessage(text, sender, isError = false) {
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

function sendQuickMessage(message) {
    const input = document.getElementById("user-input");
    input.value = message;
    sendMessage();
}
