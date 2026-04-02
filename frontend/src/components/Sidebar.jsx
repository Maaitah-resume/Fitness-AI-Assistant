import React from 'react';
import { Plus, Trash2, LayoutDashboard } from 'lucide-react';

function Sidebar({ chats, currentChatId, onSelectChat, onNewChat, logo }) {
    const user_email = localStorage.getItem('user_email') || 'default_user@example.com';

    const handleDeleteChat = async (e, chatId) => {
        e.stopPropagation();
        try {
            const resp = await fetch(`/api/v1/chats/${chatId}/${encodeURIComponent(user_email)}`, {
                method: 'DELETE'
            });
            const result = await resp.json();
            if (result.status === 'success') {
                window.location.reload();
            }
        } catch (error) {
            console.error('Delete error:', error);
        }
    };
    return (
        <aside className="sidebar glass">
            <div className="sidebar-header">
                <img src={logo} alt="Fitness AI Logo" className="logo-img" />
                <h2>Fitness AI</h2>
            </div>

            <button className="new-chat-btn" onClick={onNewChat}>
                <Plus size={20} />
                <span>New Conversation</span>
            </button>

            <div className="chat-list">
                {chats.length === 0 ? (
                    <div className="empty-chats">No recent chats</div>
                ) : (
                    chats.map(chat => (
                        <div
                            key={chat.id}
                            className={`chat-item ${currentChatId === chat.id ? 'active' : ''}`}
                            onClick={() => onSelectChat(chat.id)}
                        >
                            <div className="chat-info">
                                <span className="chat-title">{chat.title || 'New Chat'}</span>
                                <span className="chat-preview">{chat.last_message || 'No messages yet'}</span>
                            </div>
                            <button className="delete-btn" onClick={(e) => handleDeleteChat(e, chat.id)}>
                                <Trash2 size={16} />
                            </button>
                        </div>
                    ))
                )}
            </div>

            <div className="sidebar-footer">
                <a href="/dashboard" className="footer-link">
                    <LayoutDashboard size={18} />
                    <span>Dashboard</span>
                </a>
            </div>

            <style jsx>{`
        .sidebar {
          width: 320px;
          height: 100%;
          border-radius: 24px;
          display: flex;
          flex-direction: column;
          padding: 1.5rem;
          overflow: hidden;
        }
        .sidebar-header {
          display: flex;
          align-items: center;
          gap: 1rem;
          margin-bottom: 2rem;
        }
        .logo-img {
          width: 40px;
          height: 40px;
          object-fit: cover;
          border-radius: 10px;
          box-shadow: 0 5px 15px rgba(0,0,0,0.3);
        }
        .sidebar-header h2 {
          font-size: 1.25rem;
          background: linear-gradient(135deg, var(--text-white), var(--primary-light));
          -webkit-background-clip: text;
          background-clip: text;
          -webkit-text-fill-color: transparent;
        }
        .new-chat-btn {
          width: 100%;
          padding: 1rem;
          border-radius: 16px;
          background: var(--primary);
          border: none;
          color: white;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 0.75rem;
          font-weight: 600;
          margin-bottom: 2rem;
          box-shadow: 0 10px 20px -5px rgba(99, 102, 241, 0.4);
        }
        .new-chat-btn:hover {
          transform: translateY(-2px);
          filter: brightness(1.1);
        }
        .chat-list {
          flex: 1;
          overflow-y: auto;
          margin-bottom: 1rem;
        }
        .chat-item {
          padding: 1rem;
          border-radius: 16px;
          margin-bottom: 0.5rem;
          display: flex;
          justify-content: space-between;
          align-items: center;
          transition: all 0.2s;
          cursor: pointer;
          border: 1px solid transparent;
        }
        .chat-item:hover {
          background: rgba(255, 255, 255, 0.05);
          border-color: var(--glass-border);
        }
        .chat-item.active {
          background: rgba(99, 102, 241, 0.15);
          border-color: var(--primary-light);
        }
        .chat-info {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
          overflow: hidden;
        }
        .chat-title {
          font-size: 0.95rem;
          font-weight: 600;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .chat-preview {
          font-size: 0.8rem;
          color: var(--text-muted);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .delete-btn {
          background: none;
          border: none;
          color: var(--text-muted);
          padding: 0.5rem;
          opacity: 0;
          transition: 0.3s;
        }
        .chat-item:hover .delete-btn {
          opacity: 1;
          color: var(--accent);
        }
        .sidebar-footer {
          padding-top: 1rem;
          border-top: 1px solid var(--glass-border);
        }
        .footer-link {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          color: var(--text-muted);
          text-decoration: none;
          font-weight: 500;
          transition: 0.3s;
        }
        .footer-link:hover {
          color: var(--text-white);
        }
      `}</style>
        </aside>
    );
}

export default Sidebar;
