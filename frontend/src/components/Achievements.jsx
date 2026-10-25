import React, { useEffect, useState } from 'react';
import { Flame, Trophy, Utensils } from 'lucide-react';
import { badgeIcon, badgeText } from '../badgeIcons';
import { apiFetch } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';

/** Streaks, recent personal records and the badge wall (all derived from the user's logs). */
function Achievements() {
    const { t, number, shortDate, date, lang } = useI18n();
    const { weight } = usePrefs();
    const [data, setData] = useState(null);

    useEffect(() => {
        apiFetch('/api/v1/profile/achievements/me').then((r) => r.status === 'success' && setData(r.data)).catch(() => {});
    }, []);

    if (!data) return null;
    const earned = data.badges.filter((b) => b.earned).length;
    const tw = data.trainingWeeks;

    return (
        <section className="ach">
            <h2 className="section-title">{t('ach.title')}</h2>

            <div className="ach-streaks">
                <div className={`ach-streak${tw.current > 0 ? ' hot' : ''}`}>
                    <Flame size={30} aria-hidden="true" />
                    <div>
                        <div className="ach-streak-value">{tw.current === 1 ? t('ach.week1') : t('ach.weeks', { n: number(tw.current) })}</div>
                        <div className="ach-streak-label">{t('ach.trainingStreak')}</div>
                        <div className="ov-log-hint">{t('ach.trainingHint', { target: number(tw.target), best: number(tw.best) })}</div>
                    </div>
                </div>
                <div className={`ach-streak${data.mealDays.current > 0 ? ' hot' : ''}`}>
                    <Utensils size={28} aria-hidden="true" />
                    <div>
                        <div className="ach-streak-value">{data.mealDays.current === 1 ? t('ach.day1') : t('ach.days', { n: number(data.mealDays.current) })}</div>
                        <div className="ach-streak-label">{t('ach.mealStreak')}</div>
                        <div className="ov-log-hint">{t('ach.best', { best: number(data.mealDays.best) })}</div>
                    </div>
                </div>
                <div className="ach-streak">
                    <Trophy size={28} aria-hidden="true" />
                    <div>
                        <div className="ach-streak-value">{number(data.totalPrs)}</div>
                        <div className="ach-streak-label">{t('ach.prsTotal')}</div>
                        <div className="ov-log-hint">{t('ach.workoutsTotal', { n: number(data.totalWorkouts) })}</div>
                    </div>
                </div>
            </div>

            {data.recentPrs.length > 0 && (
                <>
                    <h3 className="ach-sub">{t('ach.recentPrs')}</h3>
                    <ul className="ach-prs">
                        {data.recentPrs.map((p, i) => (
                            <li key={`${p.exercise}-${p.date}-${i}`} className="ach-pr">
                                <Trophy size={18} aria-hidden="true" className="ach-pr-icon" />
                                <span className="ach-pr-text" dir="auto">
                                    {t('ach.prLine', { exercise: p.exercise, weight: weight(p.weightKg), reps: number(p.reps) })}
                                </span>
                                <span className="ach-pr-meta">
                                    {t('ach.prGain', { gain: weight(p.estimatedOneRepMax - p.previousBest) })}, {shortDate(p.date)}
                                </span>
                            </li>
                        ))}
                    </ul>
                </>
            )}

            <h3 className="ach-sub">{t('ach.badges', { earned: number(earned), total: number(data.badges.length) })}</h3>
            <ul className="ach-badges">
                {data.badges.map((b) => {
                    const Icon = badgeIcon(b.icon);
                    const text = badgeText(b, lang);
                    const pct = b.target > 0 ? Math.round((b.current / b.target) * 100) : 0;
                    return (
                        <li key={b.id} className={`ach-badge${b.earned ? ' earned' : ''}`} data-plate={b.color}>
                            <span className="ach-badge-icon" aria-hidden="true"><Icon size={22} /></span>
                            <span className="ach-badge-name" dir="auto">{text.name}</span>
                            <span className="ach-badge-desc" dir="auto">{text.description}</span>
                            {b.earned ? (
                                <span className="ach-badge-when">{t('ach.earnedOn', { date: date(b.earnedOn + 'T00:00:00', { day: 'numeric', month: 'short' }) })}</span>
                            ) : b.target > 0 ? (
                                <span className="ach-badge-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}
                                      aria-label={text.name}>
                                    <span style={{ width: `${pct}%` }} />
                                </span>
                            ) : <span className="ach-badge-when">{t('ach.needsWeight')}</span>}
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

export default Achievements;
