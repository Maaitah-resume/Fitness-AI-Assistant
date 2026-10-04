import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Trash2, ArrowLeft } from 'lucide-react';
import { apiFetch, getUser } from '../api';

function AdminPanel() {
    const navigate = useNavigate();
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const currentUserId = Number(getUser().userId);

    const loadUsers = async () => {
        setLoading(true);
        try {
            const result = await apiFetch('/api/v1/users/');
            if (result.status === 'success') {
                setUsers(result.data.users);
                setError('');
            } else {
                setError(result.detail || 'Failed to load users.');
            }
        } catch (err) {
            setError('Failed to load users.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadUsers();
    }, []);

    const toggleRole = async (user) => {
        const newRole = user.role === 'admin' ? 'user' : 'admin';
        try {
            const result = await apiFetch(`/api/v1/users/${user.id}/role`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ role: newRole }),
            });
            if (result.status === 'success') loadUsers();
            else alert(result.detail || 'Failed to update role.');
        } catch (err) {
            alert('Failed to update role.');
        }
    };

    const removeUser = async (user) => {
        if (!window.confirm(`Delete user "${user.username}"? This cannot be undone.`)) return;
        try {
            const result = await apiFetch(`/api/v1/users/${user.id}`, { method: 'DELETE' });
            if (result.status === 'success') loadUsers();
            else alert(result.detail || 'Failed to delete user.');
        } catch (err) {
            alert('Failed to delete user.');
        }
    };

    return (
        <div className="admin-container">
            <header className="admin-header">
                <button className="back-btn" onClick={() => navigate('/dashboard')}>
                    <ArrowLeft size={18} /> Back
                </button>
                <h1><ShieldCheck size={28} /> Admin Panel</h1>
                <p>Manage registered users and roles.</p>
            </header>

            {error && <div className="admin-error">{error}</div>}

            {loading ? (
                <p className="admin-loading">Loading users…</p>
            ) : (
                <div className="user-table glass">
                    <div className="user-row user-row-head">
                        <span>Username</span>
                        <span>Email</span>
                        <span>Role</span>
                        <span>Joined</span>
                        <span>Actions</span>
                    </div>
                    {users.map((u) => (
                        <div className="user-row" key={u.id}>
                            <span>{u.username}</span>
                            <span>{u.email}</span>
                            <span className={`role-badge ${u.role}`}>{u.role}</span>
                            <span>{u.created_at ? new Date(u.created_at).toLocaleDateString() : '-'}</span>
                            <span className="actions">
                                <button className="action-btn" onClick={() => toggleRole(u)}>
                                    {u.role === 'admin' ? 'Demote' : 'Promote'}
                                </button>
                                <button
                                    className="action-btn danger"
                                    onClick={() => removeUser(u)}
                                    disabled={u.id === currentUserId}
                                    title={u.id === currentUserId ? "You can't delete yourself" : 'Delete user'}
                                >
                                    <Trash2 size={14} />
                                </button>
                            </span>
                        </div>
                    ))}
                </div>
            )}

            <style jsx>{`
        .admin-container {
          width: 100%;
          max-width: 1000px;
          padding: 3rem 2rem;
          margin: 0 auto;
        }
        .admin-header { margin-bottom: 2rem; }
        .admin-header h1 {
          display: flex;
          align-items: center;
          gap: 0.6rem;
          font-size: 2.2rem;
          font-weight: 800;
          margin: 0.75rem 0 0.25rem;
        }
        .admin-header p { color: var(--text-muted); }
        .back-btn {
          display: flex;
          align-items: center;
          gap: 0.4rem;
          background: none;
          border: none;
          color: var(--text-muted);
          cursor: pointer;
          font-size: 0.9rem;
        }
        .back-btn:hover { color: var(--text-white); }
        .admin-error {
          background: rgba(244,63,94,0.15);
          border: 1px solid rgba(244,63,94,0.35);
          color: #fca5a5;
          padding: 1rem;
          border-radius: 12px;
          margin-bottom: 1.5rem;
        }
        .admin-loading { color: var(--text-muted); }
        .user-table {
          border-radius: 20px;
          overflow: hidden;
        }
        .user-row {
          display: grid;
          grid-template-columns: 1.2fr 1.6fr 0.8fr 1fr 1fr;
          gap: 1rem;
          padding: 1rem 1.5rem;
          align-items: center;
          border-bottom: 1px solid var(--glass-border);
          font-size: 0.9rem;
        }
        .user-row:last-child { border-bottom: none; }
        .user-row-head {
          font-weight: 700;
          color: var(--text-muted);
          text-transform: uppercase;
          font-size: 0.75rem;
          letter-spacing: 0.05em;
        }
        .role-badge {
          display: inline-block;
          padding: 0.2rem 0.6rem;
          border-radius: 100px;
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          width: fit-content;
        }
        .role-badge.admin {
          background: rgba(99,102,241,0.2);
          color: var(--primary-light);
        }
        .role-badge.user {
          background: rgba(255,255,255,0.08);
          color: var(--text-muted);
        }
        .actions { display: flex; gap: 0.5rem; }
        .action-btn {
          background: rgba(255,255,255,0.06);
          border: 1px solid var(--glass-border);
          color: white;
          border-radius: 8px;
          padding: 0.35rem 0.7rem;
          font-size: 0.8rem;
          cursor: pointer;
        }
        .action-btn:hover { background: rgba(255,255,255,0.12); }
        .action-btn.danger { color: #fca5a5; }
        .action-btn:disabled { opacity: 0.35; cursor: not-allowed; }
      `}</style>
        </div>
    );
}

export default AdminPanel;
