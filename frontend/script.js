//-----------------------------------------------------
// RESET CHAT ON PAGE LOAD
//-----------------------------------------------------
window.addEventListener("DOMContentLoaded", () => {
    const chatBox = document.getElementById("chat-box");

    if (chatBox) {
        chatBox.innerHTML = ""; // Clear previous messages
    }

    // Add single clean welcome message
    addMessage("Hey there! I'm GymAI. How can I help you achieve your fitness goals today?", "assistant");
});


//-----------------------------------------------------
// ADD MESSAGE TO CHAT
//-----------------------------------------------------
function addMessage(text, role, isError = false) {
    const chatBox = document.getElementById("chat-box");

    const row = document.createElement("div");
    row.className = `message ${role === "assistant" ? "ai-msg" : "user-msg"}`;

    const avatar = document.createElement("div");
    avatar.className = "message-avatar";
    avatar.textContent = role === "assistant" ? "🤖" : "👤";

    const bubble = document.createElement("div");
    bubble.className = "message-content" + (isError ? " error-msg" : "");
    bubble.innerHTML = formatMessage(text);

    row.appendChild(avatar);
    row.appendChild(bubble);
    chatBox.appendChild(row);

    // Auto scroll to bottom
    setTimeout(() => {
        chatBox.scrollTop = chatBox.scrollHeight;
    }, 100);
}


//-----------------------------------------------------
// MESSAGE FORMATTING
//-----------------------------------------------------
function formatMessage(text) {
    if (!text) return '';

    return text
        .replace(/\n/g, '<br>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
}


//-----------------------------------------------------
// TYPING INDICATOR
//-----------------------------------------------------
function showTyping() {
    document.getElementById("typing-indicator").style.display = "block";
}
function hideTyping() {
    document.getElementById("typing-indicator").style.display = "none";
}


//-----------------------------------------------------
// SEND MESSAGE
//-----------------------------------------------------
async function sendMessage() {
    const input = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");
    const text = input.value.trim();

    if (!text) return;

    // Disable UI while processing
    input.disabled = true;
    sendBtn.disabled = true;

    addMessage(text, "user");
    input.value = "";

    showTyping();

    try {
        const response = await fetch("/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message: text })
        });

        const data = await response.json();
        hideTyping();

        if (response.ok && data.response) {
            addMessage(data.response, "assistant");
        } else {
            addMessage("Sorry, I couldn't process your request.", "assistant", true);
        }
    } catch (err) {
        hideTyping();
        addMessage("Connection error. Please try again.", "assistant", true);
    }

    // Re-enable UI
    input.disabled = false;
    sendBtn.disabled = false;
    input.focus();
}


//-----------------------------------------------------
// ENTER KEY HANDLER
//-----------------------------------------------------
function handleKeyPress(event) {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}


//-----------------------------------------------------
// FOCUS INPUT FIELD
//-----------------------------------------------------
function focusInput() {
    const input = document.getElementById("user-input");
    if (input) input.focus();
}
