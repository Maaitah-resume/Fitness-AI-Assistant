// Basic chat client for the Fitness AI Assistant (no persistent history)
const API_URL = window.location.origin + "/chat";

// Keep an in-memory array so we can re-render if needed
const chatMessages = [];

window.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");
    const closeBtn = document.getElementById("close-chat");

    // Seed with a welcome message
    addMessage("Hi! I'm your Gym Club chatbot. Ask me about workouts, nutrition, or quick calculations like `bmi 70 175`.", "assistant");

    input.addEventListener("keypress", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    sendBtn.addEventListener("click", sendMessage);

    closeBtn.addEventListener("click", () => {
        document.querySelector(".chat-window").classList.toggle("minimized");
    });
});

function addMessage(text, role) {
    const chatArea = document.getElementById("chat-area");
    const row = document.createElement("div");
    row.className = `message-row ${role}`;

    const avatar = document.createElement("div");
    avatar.className = "avatar";
    avatar.textContent = role === "user" ? "🙂" : "💪";

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

    addMessage(text, "user");
    input.value = "";
    setLoading(true);

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
        addMessage(data.reply, "assistant");
    } catch (error) {
        const hint = error.message.includes("Failed to fetch")
            ? "Make sure the backend is running on http://localhost:8000."
            : error.message;
        addMessage(`Error: ${hint}`, "assistant");
    } finally {
        setLoading(false);
    }
}

function formatMessage(text) {
    let formatted = text.replace(/\n/g, "<br>");
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    formatted = formatted.replace(/`([^`]+)`/g, "<code>$1</code>");
    return formatted;
}
