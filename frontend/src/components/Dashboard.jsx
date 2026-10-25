import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch, getUser } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { LineChart } from './Charts';
import WeeklyReportCard from './WeeklyReportCard';
import Achievements from './Achievements';

const DAY_MS = 86400000;

// Same thresholds as charts-service, so the label matches what admins see.
const bmiCategory = (bmi) => (bmi < 18.5 ? 'underweight' : bmi < 25 ? 'normal' : bmi < 30 ? 'overweight' : 'obese');

function Dashboard() {
    const { t, tv, number, lang, shortDate } = useI18n();
    const { weight, height, fromKg, weightUnit } = usePrefs();
    const { username } = getUser();
    const [profile, setProfile] = useState(null);
    const [profileLoaded, setProfileLoaded] = useState(false);
    const [log, setLog] = useState(null);
    const [logError, setLogError] = useState(false);

    useEffect(() => {
        apiFetch('/api/v1/profile/me')
            .then((r) => r.status === 'success' && setProfile(r.data))
            .catch(() => {})
            .finally(() => setProfileLoaded(true));
        apiFetch('/api/v1/profile/progress/me')
            .then((r) => {
                if (r.status !== 'success') throw new Error();
                setLog(r.data);
            })
            .catch(() => setLogError(true));
    }, []);

    // English reads "Muscle gain, beginner." so the level is lower-cased there; Arabic has no case.
    const level = profile?.level ? (lang === 'en' ? tv('level', profile.level).toLowerCase() : tv('level', profile.level)) : '';
    const body = profile?.heightCm && profile?.weightKg
        ? t('ov.bodyFacts', { h: height(profile.heightCm), w: weight(profile.weightKg) })
        : '';

    // Most recently trained exercise, as its best estimated 1RM per day.
    const lift = useMemo(() => {
        const lifts = log?.lifts || [];
        if (lifts.length === 0) return null;
        const latest = lifts.reduce((a, b) => (b.date >= a.date ? b : a));
        const key = latest.exercise.trim().toLowerCase();
        const days = new Map();
        lifts.filter((l) => l.exercise.trim().toLowerCase() === key)
            .forEach((l) => days.set(l.date, Math.max(days.get(l.date) ?? 0, l.estimatedOneRepMax)));
        const points = [...days].sort(([a], [b]) => a.localeCompare(b)).map(([date, value]) => ({ date, value }));
        return { name: latest.exercise, points };
    }, [log]);

    const weights = log?.weights || [];
    const workouts30 = useMemo(() => {
        const since = Date.now() - 30 * DAY_MS;
        return new Set((log?.lifts || []).filter((l) => Date.parse(l.date) >= since).map((l) => l.date)).size;
    }, [log]);
    const change = (first, last) => `${last - first >= 0 ? '+' : '−'}${weight(Math.abs(last - first))}`;
    const hasLog = weights.length > 0 || lift;

    return (
        <>
            <p className="ov-greeting">{username ? t('ov.welcomeName', { name: username }) : t('ov.welcome')}</p>

            {!profileLoaded ? (
                <h1 className="ov-hero" aria-busy="true">&nbsp;</h1>
            ) : profile?.complete ? (
                <>
                    <h1 className="ov-hero">{t('ov.hero', { goal: tv('goal', profile.goal), level })}</h1>
                    <p className="ov-facts">{t('ov.facts', { age: number(profile.age), body })}</p>
                </>
            ) : (
                <>
                    <h1 className="ov-hero">{t('ov.setGoal')}</h1>
                    <p className="ov-facts">{t('ov.setGoalFacts')}</p>
                </>
            )}

            <div className="ov-actions">
                {profileLoaded && !profile?.complete
                    ? <Link to="/profile" className="btn btn-primary">{t('ov.fillProfile')}</Link>
                    : <Link to="/chat" className="btn btn-primary">{t('ov.askCoach')}</Link>}
                <Link to="/progress" className="btn">{t('ov.logProgress')}</Link>
                <Link to="/plans" className="btn">{t('ov.seePlans')}</Link>
            </div>

            <WeeklyReportCard />

            <h2 className="section-title">{t('ov.yourProgress')}</h2>
            {logError && <p className="notice notice-warn">{t('ov.progressError')}</p>}
            {log && !hasLog && (
                <div className="plans-empty">
                    <p>{t('ov.noProgress')}</p>
                    <Link to="/progress" className="btn btn-primary">{t('ov.logProgress')}</Link>
                </div>
            )}
            {log && hasLog && (
                <>
                    <div className="ov-log">
                        <LogItem label={t('prog.bodyWeight')}
                                 value={weights.length ? weight(weights[weights.length - 1].weightKg) : '–'}
                                 hint={weights.length > 1
                                     ? t('prog.change', { change: change(weights[0].weightKg, weights[weights.length - 1].weightKg), date: shortDate(weights[0].date) })
                                     : null} />
                        <LogItem label={lift ? t('ov.bestLift', { exercise: lift.name }) : t('ov.bestLiftNone')}
                                 value={lift ? weight(lift.points[lift.points.length - 1].value) : '–'}
                                 hint={lift && lift.points.length > 1
                                     ? t('prog.change', { change: change(lift.points[0].value, lift.points[lift.points.length - 1].value), date: shortDate(lift.points[0].date) })
                                     : t('ov.e1rm')} />
                        <LogItem label={t('ov.workouts30')} value={number(workouts30)} />
                        <LogItem label={t('ov.bmi')} value={profile?.bmi != null ? number(profile.bmi, 1) : '–'}
                                 hint={profile?.bmi != null ? tv('bmi', bmiCategory(profile.bmi)) : t('ov.addHW')} />
                    </div>
                    <div className="ov-charts">
                        {weights.length > 0 && (
                            <LineChart title={t('prog.bodyWeightChart', { unit: weightUnit })}
                                       points={weights.map((w) => ({ date: w.date, value: fromKg(w.weightKg) }))} />
                        )}
                        {lift && (
                            <LineChart title={t('prog.e1rmChart', { exercise: lift.name, unit: weightUnit })}
                                       points={lift.points.map((p) => ({ date: p.date, value: fromKg(p.value) }))} />
                        )}
                    </div>
                    <p className="ov-note"><Link to="/progress">{t('ov.openProgress')}</Link></p>
                </>
            )}
            <Achievements />
        </>
    );
}

function LogItem({ value, label, hint }) {
    return (
        <div className="ov-log-item">
            <div className="ov-log-value">{value ?? '–'}</div>
            <div className="ov-log-label" dir="auto">{label}</div>
            {hint && <div className="ov-log-hint">{hint}</div>}
        </div>
    );
}

export default Dashboard;
