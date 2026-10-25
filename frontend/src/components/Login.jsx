import { Dumbbell, Lock, User } from 'lucide-react';
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage, storeSession } from '../api';
import { useI18n } from '../i18n';
import AuthLayout from './AuthLayout';

function Login() {
    const { t } = useI18n();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        setBusy(true);
        try {
            const response = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const result = await response.json();
            if (result.status === 'success') {
                storeSession(result.data);
                // admins land in the admin portal
                navigate(result.data.role === 'admin' ? '/admin' : '/dashboard');
            } else {
                setError(errorMessage(result, t('auth.loginFailed')));
            }
        } catch (err) {
            console.error('Login error:', err);
            setError(t('common.offline'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <AuthLayout headline={t('auth.loginHeadline')} title={t('auth.loginTitle')} subtitle={t('auth.loginSub')}>
            <form onSubmit={handleLogin} className="auth-form">
                <label className="field">
                    <span className="field-label">{t('auth.username')}</span>
                    <span className="input-with-icon"><User size={18} aria-hidden="true" /><input className="input" type="text" autoComplete="username" dir="auto" value={username}
                           onChange={(e) => setUsername(e.target.value)} required /></span>
                </label>
                <label className="field">
                    <span className="field-label">{t('auth.password')}</span>
                    <span className="input-with-icon"><Lock size={18} aria-hidden="true" /><input className="input" type="password" autoComplete="current-password" value={password}
                           onChange={(e) => setPassword(e.target.value)} required /></span>
                </label>
                {error && <p className="notice notice-error" role="alert">{error}</p>}
                <button type="submit" className="btn btn-primary btn-block auth-submit" disabled={busy}>
                    <Dumbbell size={18} aria-hidden="true" />
                    {busy ? t('auth.loggingIn') : t('auth.loginBtn')}
                </button>
            </form>
            <p className="auth-switch">{t('auth.newHere')} <Link to="/register">{t('auth.createAccountLink')}</Link></p>
        </AuthLayout>
    );
}

export default Login;
