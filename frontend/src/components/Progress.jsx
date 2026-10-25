import React, { useEffect, useMemo, useState } from 'react';
import { Trash2, Trophy, X } from 'lucide-react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { LineChart } from './Charts';

const BASE = '/api/v1/profile/progress/me';
const COMMON_LIFTS = ['Back squat', 'Bench press', 'Deadlift', 'Overhead press', 'Barbell row', 'Pull-up',
    'Romanian deadlift', 'Leg press', 'Lat pulldown', 'Dumbbell bench press'];
const RECENT_ROWS = 12;

// Local calendar date as YYYY-MM-DD (what a date input expects).
const today = () => {
    const d = new Date();
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};
const keyOf = (name) => name.trim().replace(/\s+/g, ' ').toLowerCase();

function Progress() {
    const { t, number, shortDate, date } = useI18n();
    // stored in kg; kg() formats in the user's unit
    const { weight: kg, fromKg, weightUnit } = usePrefs();
    const [log, setLog] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [exerciseKey, setExerciseKey] = useState('');
    const [record, setRecord] = useState(null); // the lift that just set a personal record

    const load = () => apiFetch(BASE)
        .then((r) => {
            if (r.status !== 'success') throw new Error(errorMessage(r, t('prog.loadError')));
            setLog(r.data);
        })
        .catch((e) => setLoadError(e.message || t('prog.loadError')));

    useEffect(() => { load(); }, []);

    // Exercises, most recently trained first, each with its best estimated 1RM per day.
    const exercises = useMemo(() => {
        const byKey = new Map();
        for (const l of log?.lifts || []) {
            const k = keyOf(l.exercise);
            if (!byKey.has(k)) byKey.set(k, { key: k, name: l.exercise, days: new Map(), last: l.date });
            const ex = byKey.get(k);
            ex.name = l.exercise;
            ex.last = l.date > ex.last ? l.date : ex.last;
            ex.days.set(l.date, Math.max(ex.days.get(l.date) ?? 0, l.estimatedOneRepMax));
        }
        return [...byKey.values()]
            .map((ex) => ({ ...ex, points: [...ex.days].sort(([a], [b]) => a.localeCompare(b)).map(([d, v]) => ({ date: d, value: v })) }))
            .sort((a, b) => b.last.localeCompare(a.last));
    }, [log]);

    const selected = exercises.find((e) => e.key === exerciseKey) || exercises[0];
    const weights = log?.weights || [];

    const recent = useMemo(() => {
        const rows = [
            ...(log?.weights || []).map((w) => ({ kind: 'weight', id: w.id, date: w.date, sort: `${w.date}-0-${w.id}`, w })),
            ...(log?.lifts || []).map((l) => ({ kind: 'lift', id: l.id, date: l.date, sort: `${l.date}-1-${String(l.id).padStart(9, '0')}`, l })),
        ];
        return rows.sort((a, b) => b.sort.localeCompare(a.sort)).slice(0, RECENT_ROWS);
    }, [log]);

    const remove = async (row) => {
        const path = row.kind === 'weight' ? 'weight' : 'lifts';
        const r = await apiFetch(`${BASE}/${path}/${row.id}`, { method: 'DELETE' }).catch(() => null);
        if (r?.status === 'success') load();
        else alert(errorMessage(r, t('prog.deleteError')));
    };

    const liftLine = (l) => {
        const weight = l.weightKg === 0 ? t('prog.bodyweightShort') : kg(l.weightKg);
        return l.sets ? t('prog.setsLine', { weight, reps: number(l.reps), sets: number(l.sets) })
            : t('prog.setLine', { weight, reps: number(l.reps) });
    };

    return (
        <>
            <h1 className="page-title">{t('prog.title')}</h1>
            <p className="page-lead">{t('prog.lead')}</p>
            {loadError && <p className="notice notice-error" role="alert">{loadError}</p>}

            {record && (
                <div className="pr-celebrate" role="status">
                    <Trophy size={34} aria-hidden="true" className="pr-celebrate-icon" />
                    <div>
                        <p className="pr-celebrate-title" dir="auto">
                            {t('ach.celebrate', { exercise: record.exercise, weight: kg(record.weightKg), reps: number(record.reps) })}
                        </p>
                        <p className="pr-celebrate-sub">{t('ach.celebrateSub')}</p>
                    </div>
                    <button type="button" className="icon-btn" onClick={() => setRecord(null)} aria-label={t('plans.close')}><X size={18} aria-hidden="true" /></button>
                </div>
            )}

            <div className="prog-forms">
                <WeighInForm onSaved={load} />
                <LiftForm onSaved={(saved) => { load(); setRecord(saved?.personalRecord ? saved : null); }}
                          suggestions={[...new Set([...exercises.map((e) => e.name), ...COMMON_LIFTS])]} />
            </div>
            <p className="prog-tip">{t('prog.tip')}</p>

            <h2 className="section-title">{t('prog.bodyWeight')}</h2>
            {log && weights.length === 0 && <p className="muted">{t('prog.noWeights')}</p>}
            {weights.length > 0 && (
                <>
                    <div className="prog-headline">
                        <span className="prog-big">{kg(weights[weights.length - 1].weightKg)}</span>
                        {weights.length > 1 && (
                            <span className="prog-change">
                                {t('prog.change', {
                                    change: `${weights[weights.length - 1].weightKg - weights[0].weightKg >= 0 ? '+' : '−'}${kg(Math.abs(weights[weights.length - 1].weightKg - weights[0].weightKg))}`,
                                    date: date(weights[0].date + 'T00:00:00', { day: 'numeric', month: 'long' }),
                                })}
                            </span>
                        )}
                    </div>
                    <LineChart title={t('prog.bodyWeightChart', { unit: weightUnit })} points={weights.map((w) => ({ date: w.date, value: fromKg(w.weightKg) }))} />
                </>
            )}

            <h2 className="section-title">{t('prog.strength')}</h2>
            {log && exercises.length === 0 && <p className="muted">{t('prog.noLifts')}</p>}
            {selected && (
                <>
                    <div className="prog-picker">
                        <label className="field">
                            <span className="field-label">{t('prog.chooseExercise')}</span>
                            <select className="input" dir="auto" value={selected.key} onChange={(e) => setExerciseKey(e.target.value)}>
                                {exercises.map((e) => <option key={e.key} value={e.key}>{e.name}</option>)}
                            </select>
                        </label>
                        <div className="prog-headline">
                            <span className="prog-big">{kg(selected.points[selected.points.length - 1].value)}</span>
                            {selected.points.length > 1 && (
                                <span className="prog-change">
                                    {t('prog.change', {
                                        change: `${selected.points[selected.points.length - 1].value - selected.points[0].value >= 0 ? '+' : '−'}${kg(Math.abs(selected.points[selected.points.length - 1].value - selected.points[0].value))}`,
                                        date: shortDate(selected.points[0].date),
                                    })}
                                </span>
                            )}
                        </div>
                    </div>
                    <LineChart title={t('prog.e1rmChart', { exercise: selected.name, unit: weightUnit })}
                               points={selected.points.map((p) => ({ date: p.date, value: fromKg(p.value) }))} />
                    <p className="prog-note">{t('prog.e1rmExplain')}</p>
                </>
            )}

            {recent.length > 0 && (
                <>
                    <h2 className="section-title">{t('prog.recent')}</h2>
                    <div className="adm-table-wrap">
                        <table className="adm-table prog-table">
                            <thead>
                                <tr>
                                    <th>{t('prog.date')}</th><th>{t('prog.entryCol')}</th><th>{t('prog.detailCol')}</th>
                                    <th><span className="visually-hidden">{t('prog.deleteEntry')}</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {recent.map((row) => (
                                    <tr key={`${row.kind}-${row.id}`}>
                                        <td className="soft">{shortDate(row.date)}</td>
                                        <td className="adm-name" dir="auto">
                                            {row.kind === 'weight' ? t('prog.weighIn') : row.l.exercise}
                                            {row.kind === 'lift' && row.l.personalRecord && <span className="pr-tag"><Trophy size={12} aria-hidden="true" /> {t('ach.prTag')}</span>}
                                        </td>
                                        <td>{row.kind === 'weight' ? kg(row.w.weightKg) : liftLine(row.l)}</td>
                                        <td>
                                            <button type="button" className="icon-btn" onClick={() => remove(row)} aria-label={t('prog.deleteEntry')}>
                                                <Trash2 size={15} aria-hidden="true" />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </>
    );
}

function useSubmit(path, onSaved, successKey) {
    const { t } = useI18n();
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null); // { ok, text }
    const submit = async (body, reset) => {
        setBusy(true);
        setStatus(null);
        try {
            const r = await apiFetch(`${BASE}/${path}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('prog.saveError')));
            setStatus({ ok: true, text: t(successKey) });
            reset();
            onSaved(r.data);
        } catch (e) {
            setStatus({ ok: false, text: e.message || t('prog.saveError') });
        } finally {
            setBusy(false);
        }
    };
    return { busy, status, submit };
}

function WeighInForm({ onSaved }) {
    const { t } = useI18n();
    const { imperial, toKg, weightUnit } = usePrefs();
    const [weight, setWeight] = useState('');
    const [day, setDay] = useState(today());
    const { busy, status, submit } = useSubmit('weight', onSaved, 'prog.weightSaved');

    return (
        <form className="prog-form" onSubmit={(e) => { e.preventDefault(); submit({ date: day, weightKg: toKg(weight) }, () => setWeight('')); }}>
            <h3 className="prog-form-title">{t('prog.logWeighIn')}</h3>
            <div className="prog-row">
                <label className="field">
                    <span className="field-label">{t('prog.bodyWeightKg', { unit: weightUnit })}</span>
                    <input className="input" type="number" min={imperial ? 56 : 25} max={imperial ? 770 : 350} step="0.1" inputMode="decimal" required
                           value={weight} onChange={(e) => setWeight(e.target.value)} />
                </label>
                <label className="field">
                    <span className="field-label">{t('prog.date')}</span>
                    <input className="input" type="date" max={today()} required value={day} onChange={(e) => setDay(e.target.value)} />
                </label>
            </div>
            <FormFooter busy={busy} status={status} label={t('prog.saveWeighIn')} />
        </form>
    );
}

function LiftForm({ onSaved, suggestions }) {
    const { t } = useI18n();
    const { imperial, toKg, weightUnit } = usePrefs();
    const [exercise, setExercise] = useState('');
    const [weight, setWeight] = useState('');
    const [reps, setReps] = useState('');
    const [sets, setSets] = useState('');
    const [day, setDay] = useState(today());
    const { busy, status, submit } = useSubmit('lifts', onSaved, 'prog.liftSaved');

    const onSubmit = (e) => {
        e.preventDefault();
        submit({ date: day, exercise, weightKg: toKg(weight), reps: Number(reps), sets: sets === '' ? null : Number(sets) },
            () => { setWeight(''); setReps(''); setSets(''); });
    };

    return (
        <form className="prog-form" onSubmit={onSubmit}>
            <h3 className="prog-form-title">{t('prog.logLift')}</h3>
            <label className="field">
                <span className="field-label">{t('prog.exercise')}</span>
                <input className="input" list="lift-suggestions" maxLength={60} required dir="auto"
                       value={exercise} onChange={(e) => setExercise(e.target.value)} />
                <datalist id="lift-suggestions">{suggestions.map((s) => <option key={s} value={s} />)}</datalist>
            </label>
            <div className="prog-row prog-row-3">
                <label className="field">
                    <span className="field-label">{t('prog.liftWeightKg', { unit: weightUnit })}</span>
                    <input className="input" type="number" min="0" max={imperial ? 1100 : 500} step="0.5" inputMode="decimal" required
                           value={weight} onChange={(e) => setWeight(e.target.value)} aria-describedby="lift-weight-hint" />
                </label>
                <label className="field">
                    <span className="field-label">{t('prog.reps')}</span>
                    <input className="input" type="number" min="1" max="100" inputMode="numeric" required
                           value={reps} onChange={(e) => setReps(e.target.value)} />
                </label>
                <label className="field">
                    <span className="field-label">{t('prog.sets')}</span>
                    <input className="input" type="number" min="1" max="20" inputMode="numeric" placeholder={t('prog.optional')}
                           value={sets} onChange={(e) => setSets(e.target.value)} />
                </label>
            </div>
            <span className="field-hint" id="lift-weight-hint">{t('prog.liftWeightHint')}</span>
            <label className="field">
                <span className="field-label">{t('prog.date')}</span>
                <input className="input" type="date" max={today()} required value={day} onChange={(e) => setDay(e.target.value)} />
            </label>
            <FormFooter busy={busy} status={status} label={t('prog.saveLift')} />
        </form>
    );
}

function FormFooter({ busy, status, label }) {
    const { t } = useI18n();
    return (
        <div className="prog-form-foot">
            <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('prog.saving') : label}</button>
            {status && (
                <span className={status.ok ? 'prog-ok' : 'prog-err'} role={status.ok ? 'status' : 'alert'}>{status.text}</span>
            )}
        </div>
    );
}

export default Progress;
