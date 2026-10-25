import React, { useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { Trash2, RefreshCw, KeyRound } from 'lucide-react';
import { apiFetch, errorMessage, getUser } from '../api';
import { useI18n } from '../i18n';
import { DailyBars, DistributionBars, StatTile } from './Charts';
import AdminPrompt from './AdminPrompt';
import AdminKnowledge from './AdminKnowledge';
import AdminBadges from './AdminBadges';

function formatBytes(n) {
    if (!n) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const i = Math.min(units.length - 1, Math.floor(Math.log(n) / Math.log(1024)));
    return `${(n / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`;
}

const TABS = [
    { to: '/admin', key: 'adm.tabOverview', end: true },
    { to: '/admin/users', key: 'adm.tabUsers' },
    { to: '/admin/prompt', key: 'adm.tabPrompt' },
    { to: '/admin/knowledge', key: 'adm.tabKnowledge' },
    { to: '/admin/badges', key: 'adm.tabBadges' },
];

/** Admin portal: one page with a tab per area, each tab its own URL. */
function AdminPanel() {
    const { t } = useI18n();
    return (
        <>
            <h1 className="page-title">{t('adm.portal')}</h1>
            <nav className="adm-tabs" aria-label={t('adm.tabs')}>
                {TABS.map(({ to, key, end }) => (
                    <NavLink key={to} to={to} end={end} className={({ isActive }) => `adm-tab${isActive ? ' active' : ''}`}>
                        {t(key)}
                    </NavLink>
                ))}
            </nav>
            <Routes>
                <Route index element={<AdminOverview />} />
                <Route path="users" element={<AdminUsers />} />
                <Route path="prompt" element={<AdminPrompt />} />
                <Route path="knowledge" element={<AdminKnowledge />} />
                <Route path="badges" element={<AdminBadges />} />
                <Route path="*" element={<Navigate to="/admin" replace />} />
            </Routes>
        </>
    );
}

function AdminOverview() {
    const { t, tv, number } = useI18n();
    const [overview, setOverview] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);

    const load = async () => {
        setLoading(true);
        try {
            const o = await apiFetch('/api/v1/charts/admin/overview');
            if (o.status === 'success') { setOverview(o.data); setError(''); }
            else setError(errorMessage(o, t('adm.loadError')));
        } catch {
            setError(t('adm.loadError'));
        } finally {
            setLoading(false);
        }
    };
    useEffect(() => { load(); }, []);

    const k = overview?.kpis;
    return (
        <>
            <div className="adm-top">
                <p className="page-lead">{t('adm.lead')}</p>
                <RefreshButton loading={loading} onClick={load} />
            </div>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
            {overview?.unavailable?.length > 0 && <p className="notice notice-warn">{t('adm.partial', { list: overview.unavailable.join(', ') })}</p>}
            {loading && !overview && <p className="muted">{t('adm.loading')}</p>}
            {k && (
                <>
                    <div className="adm-kpis">
                        <StatTile label={t('adm.users')} value={number(k.totalUsers)}
                                  hint={k.admins === 1 ? t('adm.admin1') : t('adm.admins', { n: number(k.admins) })} />
                        <StatTile label={t('adm.active7')} value={number(k.activeUsersLast7Days)} />
                        <StatTile label={t('adm.profiles')} value={number(k.profilesCreated)}
                                  hint={k.averageAge ? t('adm.avgAge', { n: number(k.averageAge, 1) }) : undefined} />
                        <StatTile label={t('adm.conversations')} value={number(k.conversations)} />
                        <StatTile label={t('adm.userMessages')} value={number(k.userMessages)} />
                        <StatTile label={t('adm.files')} value={number(k.files)} hint={formatBytes(k.storageBytes)} />
                    </div>
                    <div className="adm-charts two">
                        <DailyBars title={t('adm.messagesPerDay')} data={overview.series.messagesByDay} />
                        <DailyBars title={t('adm.signups')} data={overview.series.signupsByDay} />
                    </div>
                    <div className="adm-charts three">
                        <DistributionBars title={t('adm.goals')} data={overview.distributions.goals} labelFor={(v) => tv('goal', v)} />
                        <DistributionBars title={t('adm.levels')} data={overview.distributions.levels} labelFor={(v) => tv('level', v)} />
                        <DistributionBars title={t('adm.genders')} data={overview.distributions.genders} labelFor={(v) => tv('gender', v)} />
                    </div>
                </>
            )}
        </>
    );
}

function RefreshButton({ loading, onClick }) {
    const { t } = useI18n();
    return (
        <button type="button" className="btn btn-small" onClick={onClick} disabled={loading}>
            <RefreshCw size={15} className={loading ? 'spin' : ''} aria-hidden="true" /> {t('adm.refresh')}
        </button>
    );
}

function AdminUsers() {
    const { t, tv, number, date } = useI18n();
    const [table, setTable] = useState(null);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState('');
    const [status, setStatus] = useState('');
    const [resetting, setResetting] = useState(null); // user being given a new password
    const currentUserId = Number(getUser().userId);
    const formatDate = (iso) => (iso ? date(iso, { day: 'numeric', month: 'short', year: 'numeric' }) : '–');

    const load = async () => {
        setLoading(true);
        try {
            const u = await apiFetch('/api/v1/charts/admin/users');
            if (u.status === 'success') { setTable(u.data); setError(''); }
            else setError(errorMessage(u, t('adm.loadError')));
        } catch {
            setError(t('adm.loadError'));
        } finally {
            setLoading(false);
        }
    };
    useEffect(() => { load(); }, []);

    const users = useMemo(() => {
        const q = query.trim().toLowerCase();
        return (table?.users || []).filter((u) => (!status || u.status === status)
            && (!q || u.username.toLowerCase().includes(q) || (u.email || '').toLowerCase().includes(q)));
    }, [table, query, status]);

    const toggleRole = async (user) => {
        const result = await apiFetch(`/api/v1/users/${user.id}/role`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: user.role === 'admin' ? 'USER' : 'ADMIN' }),
        }).catch(() => null);
        if (result?.status === 'success') load();
        else alert(errorMessage(result, t('adm.roleError')));
    };

    const removeUser = async (user) => {
        if (!window.confirm(t('adm.deleteConfirm', { name: user.username }))) return;
        const result = await apiFetch(`/api/v1/users/${user.id}`, { method: 'DELETE' }).catch(() => null);
        if (result?.status === 'success') load();
        else alert(errorMessage(result, t('adm.deleteError')));
    };

    return (
        <>
            <div className="adm-filters">
                <label className="field">
                    <span className="field-label">{t('adm.search')}</span>
                    <input className="input" type="search" dir="auto" placeholder={t('adm.searchPlaceholder')} value={query} onChange={(e) => setQuery(e.target.value)} />
                </label>
                <label className="field">
                    <span className="field-label">{t('adm.filterStatus')}</span>
                    <select className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
                        <option value="">{t('adm.allStatuses')}</option>
                        {['active', 'idle', 'new'].map((s) => <option key={s} value={s}>{tv('status', s)}</option>)}
                    </select>
                </label>
                <RefreshButton loading={loading} onClick={load} />
            </div>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
            {notice && <p className="notice notice-success" role="status">{notice}</p>}
            {loading && !table && <p className="muted">{t('adm.loading')}</p>}
            {table && (
                <>
                    <p className="adm-count muted">{t('adm.showing', { shown: number(users.length), total: number(table.users.length) })}</p>
                    <div className="adm-table-wrap">
                        <table className="adm-table">
                            <thead>
                                <tr>
                                    <th>{t('adm.colUser')}</th><th>{t('adm.colRole')}</th><th>{t('adm.colStatus')}</th><th>{t('adm.colProfile')}</th>
                                    <th className="num">{t('adm.colChats')}</th><th className="num">{t('adm.colMessages')}</th>
                                    <th>{t('adm.colLastActive')}</th><th>{t('adm.colJoined')}</th>
                                    <th><span className="visually-hidden">{t('adm.colActions')}</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.length === 0 && (
                                    <tr><td colSpan={9} className="soft">{t('adm.noMatch')}</td></tr>
                                )}
                                {users.map((u) => (
                                    <tr key={u.id}>
                                        <td>
                                            <div className="adm-name" dir="auto">{u.username}</div>
                                            <div className="adm-email" title={u.email} dir="ltr">{u.email}</div>
                                        </td>
                                        <td><span className={`adm-role ${u.role}`}>{u.role === 'admin' ? t('adm.roleAdmin') : t('adm.roleUser')}</span></td>
                                        <td><span className={`adm-status ${u.status}`}><span className="dot" aria-hidden="true" />{tv('status', u.status)}</span></td>
                                        <td className="soft">{u.profileComplete ? `${tv('goal', u.goal)}, ${tv('level', u.level)}` : t('adm.incomplete')}</td>
                                        <td className="num">{number(u.conversations)}</td>
                                        <td className="num">{number(u.messagesSent)}</td>
                                        <td className="soft">{formatDate(u.lastActive)}</td>
                                        <td className="soft">{formatDate(u.joined)}</td>
                                        <td>
                                            <span className="adm-row-actions">
                                                <button type="button" className="btn btn-small" onClick={() => toggleRole(u)}
                                                        disabled={u.id === currentUserId && u.role === 'admin'}
                                                        title={u.id === currentUserId ? t('adm.cantDemoteSelf') : ''}>
                                                    {u.role === 'admin' ? t('adm.makeUser') : t('adm.makeAdmin')}
                                                </button>
                                                <button type="button" className="btn btn-small" onClick={() => { setNotice(''); setResetting(u); }}
                                                        aria-label={`${t('adm.resetPw')}: ${u.username}`} title={t('adm.resetPw')}>
                                                    <KeyRound size={14} aria-hidden="true" />
                                                </button>
                                                <button type="button" className="btn btn-danger btn-small" onClick={() => removeUser(u)}
                                                        disabled={u.id === currentUserId}
                                                        title={u.id === currentUserId ? t('adm.cantDeleteSelf') : t('adm.deleteUser', { name: u.username })}
                                                        aria-label={t('adm.deleteUser', { name: u.username })}>
                                                    <Trash2 size={14} aria-hidden="true" />
                                                </button>
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
            {resetting && (
                <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)}
                                     onDone={() => { setNotice(t('adm.pwReset', { name: resetting.username })); setResetting(null); }} />
            )}
        </>
    );
}

function ResetPasswordDialog({ user, onClose, onDone }) {
    const { t } = useI18n();
    const [password, setPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const inputRef = useRef(null);

    useEffect(() => {
        inputRef.current?.focus();
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const submit = async (e) => {
        e.preventDefault();
        setBusy(true);
        setError('');
        try {
            const r = await apiFetch(`/api/v1/users/${user.id}/password`, {
                method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ newPassword: password }),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('adm.pwResetError')));
            onDone();
        } catch (err) {
            setError(err.message);
            setBusy(false);
        }
    };

    return (
        <div className="viewer" onClick={onClose}>
            <form className="dialog" role="dialog" aria-modal="true" aria-labelledby="reset-title"
                  onClick={(e) => e.stopPropagation()} onSubmit={submit}>
                <h2 id="reset-title" className="set-title">{t('adm.resetPwTitle', { name: user.username })}</h2>
                <p className="set-help">{t('adm.resetPwHelp')}</p>
                <label className="field">
                    <span className="field-label">{t('adm.newPw')}</span>
                    <input ref={inputRef} className="input" type="text" autoComplete="off" minLength={8} required
                           value={password} onChange={(e) => setPassword(e.target.value)} aria-describedby="reset-hint" />
                    <span className="field-hint" id="reset-hint">{t('auth.pwHint')}</span>
                </label>
                {error && <p className="notice notice-error" role="alert">{error}</p>}
                <div className="prog-form-foot">
                    <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('adm.saving') : t('adm.save')}</button>
                    <button type="button" className="btn" onClick={onClose}>{t('adm.cancel')}</button>
                </div>
            </form>
        </div>
    );
}

export default AdminPanel;
