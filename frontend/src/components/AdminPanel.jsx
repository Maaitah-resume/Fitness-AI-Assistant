import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Trash2, ArrowLeft, RefreshCw } from 'lucide-react';
import { apiFetch, errorMessage, getUser } from '../api';
import { DailyBars, DistributionBars, StatTile } from './Charts';

const STATUS_LABEL = { active: 'Active', idle: 'Idle', new: 'New' };

function formatBytes(n) {
    if (!n) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const i = Math.min(units.length - 1, Math.floor(Math.log(n) / Math.log(1024)));
    return `${(n / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`;
}

function formatDate(iso) {
    return iso ? new Date(iso).toLocaleDateString() : '–';
}

function AdminPanel() {
    const navigate = useNavigate();
    const [overview, setOverview] = useState(null);
    const [table, setTable] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const currentUserId = Number(getUser().userId);

    const load = async () => {
        setLoading(true);
        try {
            const [o, u] = await Promise.all([
                apiFetch('/api/v1/charts/admin/overview'),
                apiFetch('/api/v1/charts/admin/users'),
            ]);
            if (o.status === 'success') setOverview(o.data);
            if (u.status === 'success') setTable(u.data);
            setError(o.status === 'success' && u.status === 'success' ? '' : errorMessage(o.status !== 'success' ? o : u, 'Failed to load dashboard.'));
        } catch {
            setError('Failed to load dashboard.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const toggleRole = async (user) => {
        const newRole = user.role === 'admin' ? 'USER' : 'ADMIN';
        const result = await apiFetch(`/api/v1/users/${user.id}/role`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: newRole }),
        }).catch(() => null);
        if (result?.status === 'success') load();
        else alert(errorMessage(result, 'Failed to update role.'));
    };

    const removeUser = async (user) => {
        if (!window.confirm(`Delete user "${user.username}" and all their data? This cannot be undone.`)) return;
        const result = await apiFetch(`/api/v1/users/${user.id}`, { method: 'DELETE' }).catch(() => null);
        if (result?.status === 'success') load();
        else alert(errorMessage(result, 'Failed to delete user.'));
    };

    const k = overview?.kpis;
    const unavailable = [...new Set([...(overview?.unavailable || []), ...(table?.unavailable || [])])];

    return (
        <div className="admin-container">
            <header className="admin-header">
                <button className="back-btn" onClick={() => navigate('/dashboard')}>
                    <ArrowLeft size={18} /> Back
                </button>
                <div className="admin-title-row">
                    <h1><ShieldCheck size={28} /> Admin Panel</h1>
                    <button className="refresh-btn" onClick={load} disabled={loading}>
                        <RefreshCw size={16} className={loading ? 'spin' : ''} /> Refresh
                    </button>
                </div>
                <p>Platform activity, user status and role management. Figures refresh at most once a minute.</p>
            </header>

            {error && <div className="admin-error">{error}</div>}
            {unavailable.length > 0 && (
                <div className="admin-warn">Partial data — unavailable: {unavailable.join(', ')}.</div>
            )}

            {k && (
                <>
                    <div className="kpi-grid">
                        <StatTile label="Users" value={k.totalUsers} hint={`${k.admins} admin${k.admins === 1 ? '' : 's'}`} />
                        <StatTile label="Active (7 days)" value={k.activeUsersLast7Days} />
                        <StatTile label="Profiles created" value={k.profilesCreated}
                                  hint={k.averageAge ? `avg age ${k.averageAge}` : undefined} />
                        <StatTile label="Conversations" value={k.conversations} />
                        <StatTile label="User messages" value={k.userMessages} />
                        <StatTile label="Files" value={k.files} hint={formatBytes(k.storageBytes)} />
                    </div>

                    <div className="chart-grid two">
                        <DailyBars title="Messages per day" data={overview.series.messagesByDay} />
                        <DailyBars title="Sign-ups per day" data={overview.series.signupsByDay} />
                    </div>
                    <div className="chart-grid three">
                        <DistributionBars title="Goals" data={overview.distributions.goals} />
                        <DistributionBars title="Fitness levels" data={overview.distributions.levels} />
                        <DistributionBars title="Gender" data={overview.distributions.genders} />
                    </div>
                </>
            )}

            {table && (
                <section className="users-section">
                    <div className="users-head">
                        <h2>Users</h2>
                        <div className="status-counts">
                            {Object.entries(table.statusCounts).map(([s, n]) => (
                                <span key={s} className={`status-badge ${s}`}>
                                    <span className="dot" aria-hidden="true" />{STATUS_LABEL[s]} {n}
                                </span>
                            ))}
                        </div>
                    </div>
                    <div className="table-wrap glass">
                        <table className="user-table">
                            <thead>
                                <tr>
                                    <th>User</th><th>Role</th><th>Status</th><th>Profile</th>
                                    <th className="num">Chats</th><th className="num">Messages</th>
                                    <th>Last active</th><th>Joined</th><th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {table.users.map((u) => (
                                    <tr key={u.id}>
                                        <td>
                                            <div className="user-name">{u.username}</div>
                                            <div className="user-email" title={u.email}>{u.email}</div>
                                        </td>
                                        <td><span className={`role-badge ${u.role}`}>{u.role}</span></td>
                                        <td>
                                            <span className={`status-badge ${u.status}`}>
                                                <span className="dot" aria-hidden="true" />{STATUS_LABEL[u.status]}
                                            </span>
                                        </td>
                                        <td className="muted">
                                            {u.profileComplete
                                                ? `${(u.goal || '').replace(/_/g, ' ')} · ${u.level}`
                                                : 'Incomplete'}
                                        </td>
                                        <td className="num">{u.conversations}</td>
                                        <td className="num">{u.messagesSent}</td>
                                        <td className="muted">{formatDate(u.lastActive)}</td>
                                        <td className="muted">{formatDate(u.joined)}</td>
                                        <td>
                                            <span className="actions">
                                                <button className="action-btn" onClick={() => toggleRole(u)}
                                                        disabled={u.id === currentUserId && u.role === 'admin'}
                                                        title={u.id === currentUserId ? "You can't demote yourself" : ''}>
                                                    {u.role === 'admin' ? 'Demote' : 'Promote'}
                                                </button>
                                                <button className="action-btn danger" onClick={() => removeUser(u)}
                                                        disabled={u.id === currentUserId}
                                                        title={u.id === currentUserId ? "You can't delete yourself" : 'Delete user'}
                                                        aria-label={`Delete ${u.username}`}>
                                                    <Trash2 size={14} />
                                                </button>
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>
            )}

            {loading && !overview && <p className="admin-loading">Loading dashboard…</p>}

            <style jsx>{`
        .admin-container { width: 100%; max-width: 1200px; height: 100%; overflow-y: auto; padding: 2rem; margin: 0 auto; }
        .admin-header { margin-bottom: 1.5rem; }
        .admin-title-row { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
        .admin-header h1 { display: flex; align-items: center; gap: 0.6rem; font-size: 2.2rem; font-weight: 800; margin: 0.75rem 0 0.25rem; }
        .admin-header p { color: var(--text-muted); }
        .back-btn, .refresh-btn { display: flex; align-items: center; gap: 0.4rem; background: none; border: none;
                                  color: var(--text-muted); cursor: pointer; font-size: 0.9rem; }
        .refresh-btn { border: 1px solid var(--glass-border); border-radius: 100px; padding: 0.45rem 0.9rem; }
        .back-btn:hover, .refresh-btn:hover { color: var(--text-white); }
        .spin { animation: spin 1s linear infinite; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .admin-error, .admin-warn { padding: 0.9rem 1rem; border-radius: 12px; margin-bottom: 1.25rem; font-size: 0.9rem; }
        .admin-error { background: rgba(244,63,94,0.15); border: 1px solid rgba(244,63,94,0.35); color: #fca5a5; }
        .admin-warn { background: rgba(234,179,8,0.12); border: 1px solid rgba(234,179,8,0.35); color: #fde68a; }
        .admin-loading { color: var(--text-muted); }

        .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin-bottom: 1.25rem; }
        .chart-grid { display: grid; gap: 1.25rem; margin-bottom: 1.25rem; }
        /* min(..., 100%) lets a single column shrink to fit narrow screens */
        .chart-grid.two { grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); }
        .chart-grid.three { grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr)); }
        @media (max-width: 600px) {
          .admin-container { padding: 1rem; }
          .admin-header h1 { font-size: 1.6rem; }
        }

        .users-section { margin-top: 2rem; }
        .users-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 0.9rem; }
        .users-head h2 { font-size: 1.4rem; }
        .status-counts { display: flex; gap: 0.6rem; flex-wrap: wrap; }
        .table-wrap { border-radius: 20px; overflow-x: auto; }
        .user-table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
        .user-table th { text-align: left; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em;
                         color: var(--text-muted); padding: 0.9rem 1rem; border-bottom: 1px solid var(--glass-border); }
        .user-table td { padding: 0.75rem 0.8rem; border-bottom: 1px solid var(--glass-border); vertical-align: middle; }
        .user-table th { padding-left: 0.8rem; padding-right: 0.8rem; }
        .user-table tr:last-child td { border-bottom: none; }
        .user-table .num { text-align: right; font-variant-numeric: tabular-nums; }
        .user-table .muted { color: var(--text-muted); text-transform: capitalize; white-space: nowrap; }
        .user-name { font-weight: 600; }
        .user-email { font-size: 0.78rem; color: var(--text-muted); max-width: 200px;
                      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

        .role-badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 100px; font-size: 0.72rem;
                      font-weight: 700; text-transform: uppercase; }
        .role-badge.admin { background: rgba(99,102,241,0.2); color: var(--primary-light); }
        .role-badge.user { background: rgba(255,255,255,0.08); color: var(--text-muted); }
        /* Status always pairs its color with a label, never color alone. */
        .status-badge { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.8rem; color: var(--text-white); }
        .status-badge .dot { width: 8px; height: 8px; border-radius: 50%; }
        .status-badge.active .dot { background: #22c55e; }
        .status-badge.idle .dot { background: #eab308; }
        .status-badge.new .dot { background: #94a3b8; }

        .actions { display: flex; gap: 0.5rem; }
        .action-btn { background: rgba(255,255,255,0.06); border: 1px solid var(--glass-border); color: white;
                      border-radius: 8px; padding: 0.35rem 0.7rem; font-size: 0.8rem; cursor: pointer; }
        .action-btn:hover:not(:disabled) { background: rgba(255,255,255,0.12); }
        .action-btn.danger { color: #fca5a5; }
        .action-btn:disabled { opacity: 0.35; cursor: not-allowed; }
      `}</style>
        </div>
    );
}

export default AdminPanel;
