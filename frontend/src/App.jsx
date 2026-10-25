import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import AppShell from './components/AppShell';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import Login from './components/Login';
import Register from './components/Register';
import Dashboard from './components/Dashboard';
import Profile from './components/Profile';
import MyPlans from './components/MyPlans';
import Progress from './components/Progress';
import Settings from './components/Settings';
import Nutrition from './components/Nutrition';
import AdminPanel from './components/AdminPanel';
import { apiFetch, isAdmin, isAuthenticated } from './api';


function RequireAuth({ children }) {
    return isAuthenticated() ? children : <Navigate to="/login" replace />;
}

function RequireAdmin({ children }) {
    if (!isAuthenticated()) return <Navigate to="/login" replace />;
    return isAdmin() ? children : <Navigate to="/dashboard" replace />;
}


function ChatPage({ chats, currentChatId, onSelectChat, onNewChat, onChatCreated, onRefreshChats, chatHistoryState }) {
    useEffect(() => {
        onRefreshChats();
        // Make older generated plans searchable before the user asks about them.
        apiFetch('/api/v1/files/generated/index-pending', { method: 'POST' }).catch(() => {});
    }, []);

    return (
        <AppShell bare>
            <div className="chat">
                <Sidebar
                    chats={chats}
                    currentChatId={currentChatId}
                    onSelectChat={onSelectChat}
                    onNewChat={onNewChat}
                    historyState={chatHistoryState}
                />
                <ChatArea
                    currentChatId={currentChatId}
                    onChatCreated={onChatCreated}
                />
            </div>
        </AppShell>
    );
}

function App() {
    const [currentChatId, setCurrentChatId] = useState(null);
    const [chats, setChats] = useState([]);
    const [chatHistoryState, setChatHistoryState] = useState('idle');

    const fetchChats = async () => {
        setChatHistoryState('loading');
        try {
            const result = await apiFetch('/api/v1/chat/recent');
            if (result.status === 'success') {
                setChats(result.data?.chats || []);
                setChatHistoryState('ready');
            } else {
                setChatHistoryState('error');
            }
        } catch (error) {
            console.error('Failed to fetch chats:', error);
            setChatHistoryState('error');
        }
    };

    return (
        <Router>
            <Routes>
                <Route path="/" element={<Navigate to="/login" replace />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route path="/dashboard" element={<RequireAuth><AppShell><Dashboard /></AppShell></RequireAuth>} />
                <Route path="/profile" element={<RequireAuth><AppShell><Profile /></AppShell></RequireAuth>} />
                <Route path="/progress" element={<RequireAuth><AppShell><Progress /></AppShell></RequireAuth>} />
                <Route path="/nutrition" element={<RequireAuth><AppShell><Nutrition /></AppShell></RequireAuth>} />
                <Route path="/settings" element={<RequireAuth><AppShell><Settings /></AppShell></RequireAuth>} />
                <Route path="/plans" element={<RequireAuth><AppShell><MyPlans /></AppShell></RequireAuth>} />
                <Route path="/admin/*" element={<RequireAdmin><AppShell><AdminPanel /></AppShell></RequireAdmin>} />
                <Route
                    path="/chat"
                    element={
                        <RequireAuth>
                            <ChatPage
                                chats={chats}
                                currentChatId={currentChatId}
                                onSelectChat={setCurrentChatId}
                                onNewChat={() => setCurrentChatId(null)}
                                onRefreshChats={fetchChats}
                                chatHistoryState={chatHistoryState}
                                onChatCreated={(id) => {
                                    setCurrentChatId(id);
                                    fetchChats();
                                }}
                            />
                        </RequireAuth>
                    }
                />
                {/* Fallback */}
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
        </Router>
    );
}

export default App;
