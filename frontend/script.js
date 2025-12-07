// Initialize chat on page load
document.addEventListener('DOMContentLoaded', function() {
    loadPersistedMessages();
    focusInput();
});

// Add message to chat with enhanced formatting
function addMessage(text, role, isError = false, skipSave = false) {
    const chatBox = document.getElementById("chat-box");

    // Remove welcome message if it exists
    const welcomeMsg = chatBox.querySelector(".welcome-msg");
    if (welcomeMsg) {
        welcomeMsg.style.animation = 'fadeOut 0.3s ease';
        setTimeout(() => welcomeMsg.remove(), 300);
    }

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

    // Smooth scroll to bottom
    setTimeout(() => {
        chatBox.scrollTop = chatBox.scrollHeight;
    }, 100);

    if (!skipSave) {
        persistMessage({ role, content: text, isError });
    }
}

// Format message text with basic markdown support
function formatMessage(text) {
    if (!text) return '';
    
    // Convert line breaks
    text = text.replace(/\n/g, '<br>');
    
    // Bold text
    text = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    
    // Inline code
    text = text.replace(/`([^`]+)`/g, '<code>$1</code>');
    
    // Links (basic)
    text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
    
    return text;
}

// Show typing indicator
function showTyping() {
    const indicator = document.getElementById('typing-indicator');
    if (indicator) {
        indicator.style.display = 'block';
        const chatBox = document.getElementById("chat-box");
        setTimeout(() => {
            chatBox.scrollTop = chatBox.scrollHeight;
        }, 100);
    }
}

// Hide typing indicator
function hideTyping() {
    const indicator = document.getElementById('typing-indicator');
    if (indicator) {
        indicator.style.display = 'none';
    }
}

// Send message function
async function sendMessage() {
    const input = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");
    const text = input.value.trim();

    if (!text) return;

    // Disable input while sending
    input.disabled = true;
    sendBtn.disabled = true;

    // Add user message
    addMessage(text, "user");
    input.value = "";

    // Show typing indicator
    showTyping();

    try {
        // Send to backend
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ message: text })
        });

        const data = await response.json();

        // Hide typing indicator
        hideTyping();

        if (response.ok && data.response) {
            addMessage(data.response, "assistant");
        } else {
            addMessage(data.error || "Sorry, I couldn't process your request. Please try again.", "assistant", true);
        }
    } catch (error) {
        hideTyping();
        addMessage("Connection error. Please check your internet and try again.", "assistant", true);
        console.error('Error:', error);
    } finally {
        // Re-enable input
        input.disabled = false;
        sendBtn.disabled = false;
        focusInput();
    }
}

// Handle Enter key press
function handleKeyPress(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

// Quick question from welcome menu
function quickQuestion(question) {
    const input = document.getElementById("user-input");
    input.value = question;
    input.focus();
    sendMessage();
}

// Focus on input field
function focusInput() {
    const input = document.getElementById("user-input");
    if (input) {
        input.focus();
    }
}

// Persist messages to storage (if available)
function persistMessage(message) {
    try {
        const messages = JSON.parse(localStorage.getItem('chatHistory') || '[]');
        messages.push({
            ...message,
            timestamp: new Date().toISOString()
        });
        
        // Keep only last 50 messages
        if (messages.length > 50) {
            messages.splice(0, messages.length - 50);
        }
        
        localStorage.setItem('chatHistory', JSON.stringify(messages));
    } catch (error) {
        console.warn('Could not persist message:', error);
    }
}

// Load persisted messages on page load
function loadPersistedMessages() {
    try {
        const messages = JSON.parse(localStorage.getItem('chatHistory') || '[]');
        
        // Only load recent messages (last 10)
        const recentMessages = messages.slice(-10);
        
        if (recentMessages.length > 0) {
            // Remove welcome message
            const chatBox = document.getElementById("chat-box");
            const welcomeMsg = chatBox.querySelector(".welcome-msg");
            if (welcomeMsg) welcomeMsg.remove();
            
            // Add all persisted messages
            recentMessages.forEach(msg => {
                addMessage(msg.content, msg.role, msg.isError || false, true);
            });
        }
    } catch (error) {
        console.warn('Could not load persisted messages:', error);
    }
}

// Clear chat history
function clearChatHistory() {
    try {
        localStorage.removeItem('chatHistory');
        location.reload();
    } catch (error) {
        console.warn('Could not clear chat history:', error);
    }
}

// Add CSS for fadeOut animation
const style = document.createElement('style');
style.textContent = `
    @keyframes fadeOut {
        from { opacity: 1; transform: scale(1); }
        to { opacity: 0; transform: scale(0.95); }
    }
`;
document.head.appendChild(style);