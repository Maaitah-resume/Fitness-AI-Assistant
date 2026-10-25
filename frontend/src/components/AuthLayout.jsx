import React from 'react';
import { ClipboardList, HeartPulse, Trophy } from 'lucide-react';
import LogoMark from './LogoMark';
import { useI18n } from '../i18n';
import { LanguageToggle } from '../i18n';

// A full-bleed gym photo with the headline on one side, the form on chalk on the other.
// photo: the base name of a pair in public/auth (<name>-1200.jpg and <name>-2400.jpg).
function AuthLayout({ headline, title, subtitle, photo = 'login', children }) {
    const { t } = useI18n();
    return (
        <div className="auth">
            <section className="auth-board">
                <img className="auth-photo" alt="" aria-hidden="true" decoding="async" fetchpriority="high"
                     src={`/auth/${photo}-1200.jpg`}
                     srcSet={`/auth/${photo}-1200.jpg 1200w, /auth/${photo}-2400.jpg 2400w`}
                     sizes="(max-width: 860px) 100vw, 55vw" />
                <div className="auth-board-top">
                    <span className="auth-brand">GymAI</span>
                    <LanguageToggle className="auth-lang" />
                </div>
                <h1 className="auth-headline">{headline}</h1>
                <div className="auth-plates" aria-hidden="true">
                    <span /><span /><span /><span /><span />
                </div>
            </section>
            <section className="auth-panel">
                <div className="auth-card">
                    {/* a loaded barbell: plates from heavy to light on both ends */}
                    <div className="auth-card-bar" aria-hidden="true">
                        <span /><span /><span /><i /><span /><span /><span />
                    </div>
                    <div className="auth-card-head">
                        <LogoMark size={56} className="auth-logo" />
                        <div>
                            <h2 className="auth-title">{title}</h2>
                            <p className="auth-sub">{subtitle}</p>
                        </div>
                    </div>
                    {children}
                </div>
                <ul className="auth-perks">
                    <li><ClipboardList size={18} aria-hidden="true" />{t('auth.perkPlans')}</li>
                    <li><Trophy size={18} aria-hidden="true" />{t('auth.perkPrs')}</li>
                    <li><HeartPulse size={18} aria-hidden="true" />{t('auth.perkMemory')}</li>
                </ul>
            </section>
        </div>
    );
}

export default AuthLayout;
