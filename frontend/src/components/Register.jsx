import { Dumbbell, Lock, Mail, User } from 'lucide-react';
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage, storeSession } from '../api';
import { useI18n } from '../i18n';
import AuthLayout from './AuthLayout';

function Register() {
    const { t } = useI18n();
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const navigate = useNavigate();

    const handleRegister = async (e) => {
        e.preventDefault();
        setError('');
        setBusy(true);
        try {
            const response = await fetch('/api/v1/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, email })
            });
            const result = await response.json();
            if (result.status === 'success') {
                storeSession(result.data);
                navigate('/profile');
            } else {
                setError(errorMessage(result, t('auth.registerFailed')));
            }
        } catch (err) {
            console.error('Registration error:', err);
            setError(t('common.offline'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <AuthLayout headline={t('auth.registerHeadline')} title={t('auth.registerTitle')} subtitle={t('auth.registerSub')} photo="register">
            <form onSubmit={handleRegister} className="auth-form">
                <label className="field">
                    <span className="field-label">{t('auth.username')}</span>
                    <span className="input-with-icon"><User size={18} aria-hidden="true" /><input className="input" type="text" autoComplete="username" dir="auto" minLength={3} value={username}
                           onChange={(e) => setUsername(e.target.value)} required /></span>
                </label>
                <label className="field">
                    <span className="field-label">{t('auth.email')}</span>
                    <span className="input-with-icon"><Mail size={18} aria-hidden="true" /><input className="input" type="email" autoComplete="email" dir="ltr" value={email}
                           onChange={(e) => setEmail(e.target.value)} required /></span>
                </label>
                <label className="field">
                    <span className="field-label">{t('auth.password')}</span>
                    <span className="input-with-icon"><Lock size={18} aria-hidden="true" /><input className="input" type="password" autoComplete="new-password" minLength={8} value={password}
                           onChange={(e) => setPassword(e.target.value)} required aria-describedby="pw-hint" /></span>
                    <span className="field-hint" id="pw-hint">{t('auth.pwHint')}</span>
                </label>
                {error && <p className="notice notice-error" role="alert">{error}</p>}
                <button type="submit" className="btn btn-primary btn-block auth-submit" disabled={busy}>
                    <Dumbbell size={18} aria-hidden="true" />
                    {busy ? t('auth.creatingAccount') : t('auth.createAccountBtn')}
                </button>
            </form>
            <p className="auth-switch">{t('auth.haveAccount')} <Link to="/login">{t('auth.loginLink')}</Link></p>
        </AuthLayout>
    );
}

export default Register;
