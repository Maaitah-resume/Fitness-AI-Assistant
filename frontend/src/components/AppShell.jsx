import React, { useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { LayoutDashboard, MessageSquare, FileText, TrendingUp, Utensils, User, Shield, LogOut, Settings as SettingsIcon, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { getUser, isAdmin, logout } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { usePersistentToggle } from '../usePersistentToggle';

// Each section keeps one competition-plate colour; the rail stripe shows where you are.
const SECTIONS = [
    { to: '/dashboard', key: 'nav.overview', icon: LayoutDashboard, plate: 'white' },
    { to: '/chat', key: 'nav.coach', icon: MessageSquare, plate: 'blue' },
    { to: '/progress', key: 'nav.progress', icon: TrendingUp, plate: 'red' },
    { to: '/nutrition', key: 'nav.nutrition', icon: Utensils, plate: 'yellow' },
    { to: '/plans', key: 'nav.plans', icon: FileText, plate: 'green' },
    { to: '/profile', key: 'nav.profile', icon: User, plate: 'steel' },
];

function AppShell({ children, bare = false }) {
    const navigate = useNavigate();
    const { t } = useI18n();
    const { username } = getUser();
    const { ensureLoaded } = usePrefs();
    const [collapsed, toggleCollapsed] = usePersistentToggle('gymai.rail.collapsed');
    useEffect(() => { ensureLoaded(); }, [ensureLoaded]);
    const sections = isAdmin()
        ? [...SECTIONS, { to: '/admin', key: 'nav.admin', icon: Shield, plate: 'steel' }]
        : SECTIONS;

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="shell">
            <nav className={`rail${collapsed ? ' is-collapsed' : ''}`} aria-label={t('nav.main')}>
                <div className="rail-top">
                    <NavLink to="/dashboard" className="rail-brand">{collapsed ? 'G' : 'GymAI'}</NavLink>
                    <button type="button" className="panel-toggle rail-toggle" onClick={toggleCollapsed}
                            aria-expanded={!collapsed} title={collapsed ? t('nav.expand') : t('nav.collapse')}
                            aria-label={collapsed ? t('nav.expand') : t('nav.collapse')}>
                        {collapsed ? <PanelLeftOpen size={18} aria-hidden="true" className="flip-rtl" />
                                   : <PanelLeftClose size={18} aria-hidden="true" className="flip-rtl" />}
                    </button>
                </div>
                <div className="rail-nav">
                    {sections.map(({ to, key, icon: Icon, plate }) => (
                        <NavLink key={to} to={to} data-plate={plate} aria-label={t(key)} title={collapsed ? t(key) : undefined}
                                 className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`}>
                            <Icon size={20} aria-hidden="true" />
                            <span>{t(key)}</span>
                        </NavLink>
                    ))}
                    {/* phones: the footer is hidden, so Settings joins the bottom bar */}
                    <NavLink to="/settings" data-plate="steel" aria-label={t('nav.settings')}
                             className={({ isActive }) => `rail-link rail-settings-mobile${isActive ? ' active' : ''}`}>
                        <SettingsIcon size={20} aria-hidden="true" />
                    </NavLink>
                </div>
                <div className="rail-foot">
                    {username && <span className="rail-user">{t('nav.signedInAs', { name: username })}</span>}
                    <NavLink to="/settings" className={({ isActive }) => `rail-logout rail-settings${isActive ? ' active' : ''}`}>
                        <SettingsIcon size={18} aria-hidden="true" /> <span className="rail-text">{t('nav.settings')}</span>
                    </NavLink>
                    <button type="button" className="rail-logout" onClick={handleLogout} title={t('nav.logout')}>
                        <LogOut size={18} aria-hidden="true" className="flip-rtl" /> <span className="rail-text">{t('nav.logout')}</span>
                    </button>
                </div>
            </nav>
            <main className="page">
                {bare ? children : <div className="page-inner">{children}</div>}
            </main>
        </div>
    );
}

export default AppShell;
