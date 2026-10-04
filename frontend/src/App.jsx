import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import Login from './components/Login';
import Register from './components/Register';
import Dashboard from './components/Dashboard';
import AdminPanel from './components/AdminPanel';
import logo from './assets/logo.png';
import { apiFetch, isAdmin, isAuthenticated } from './api';


function RequireAuth({ children }) {
    return isAuthenticated() ? children : <Navigate to="/login" replace />;
}

function RequireAdmin({ children }) {
    if (!isAuthenticated()) return <Navigate to="/login" replace />;
    return isAdmin() ? children : <Navigate to="/dashboard" replace />;
}


function MainLayout({ chats, currentChatId, onSelectChat, onNewChat, logo, onChatCreated }) {
    return (
        <div className="main-layout-wrapper">
            <Sidebar
                chats={chats}
                currentChatId={currentChatId}
                onSelectChat={onSelectChat}
                onNewChat={onNewChat}
                logo={logo}
            />
            <ChatArea
                currentChatId={currentChatId}
                onChatCreated={onChatCreated}
            />
            <style jsx>{`
        .main-layout-wrapper {
          width: 100%;
          max-width: 1200px;
          height: 850px;
          display: flex;
          gap: 1.5rem;
          animation: fadeIn 0.8s ease-out;
        }
      `}</style>
        </div>
    );
}

function App() {
    const [currentChatId, setCurrentChatId] = useState(null);
    const [chats, setChats] = useState([]);

    useEffect(() => {
        if (isAuthenticated()) fetchChats();
    }, []);

    const fetchChats = async () => {
        try {
            const result = await apiFetch('/api/v1/chats/recent');
            if (result.status === 'success') {
                setChats(result.data.chats);
            }
        } catch (error) {
            console.error('Failed to fetch chats:', error);
        }
    };

    return (
        <Router>
            <div className="app-container">
                <div className="ambient-light light-1"></div>
                <div className="ambient-light light-2"></div>

                <Routes>
                    <Route path="/" element={<Navigate to="/login" replace />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    <Route
                        path="/dashboard"
                        element={<RequireAuth><Dashboard /></RequireAuth>}
                    />
                    <Route
                        path="/admin"
                        element={<RequireAdmin><AdminPanel /></RequireAdmin>}
                    />

                    <Route
                        path="/chat"
                        element={
                            <RequireAuth>
                                <MainLayout
                                    chats={chats}
                                    currentChatId={currentChatId}
                                    onSelectChat={setCurrentChatId}
                                    onNewChat={() => setCurrentChatId(null)}
                                    logo={logo}
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

                <style jsx>{`
          .app-container {
            height: 100vh;
            width: 100vw;
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 2rem;
            overflow: hidden;
          }
        `}</style>
            </div>
        </Router>
    );
}

export default App;
