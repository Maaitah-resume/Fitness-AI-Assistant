import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Camera, PenLine, Trash2, Loader2 } from 'lucide-react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { DailyBars } from './Charts';

const BASE = '/api/v1/profile/nutrition/me';
const CHART_DAYS = 14;

const today = () => {
    const d = new Date();
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};

/** Shrink a photo in the browser (longest side 1280 px, JPEG) so uploads stay small and fast. */
async function downscale(file, max = 1280) {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
}

/**
 * Daily targets from the profile: protein 1.6 g/kg; calories by Mifflin-St Jeor with an activity
 * factor from training days and a goal adjustment. Null when the profile is missing the inputs.
 */
function targets(profile, trainingDays) {
    if (!profile?.weightKg) return null;
    const protein = Math.round(profile.weightKg * 1.6);
    if (!profile.heightCm || !profile.age) return { protein, calories: null };
    const base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age;
    const bmr = profile.gender === 'male' ? base + 5 : profile.gender === 'female' ? base - 161 : base - 78;
    const days = trainingDays ?? 3;
    const activity = days >= 6 ? 1.725 : days >= 3 ? 1.55 : days >= 1 ? 1.375 : 1.2;
    const goal = { muscle_gain: 300, fat_loss: -400 }[profile.goal] ?? 0;
    return { protein, calories: Math.round((bmr * activity + goal) / 10) * 10 };
}

function Nutrition() {
    const { t, number, shortDate, lang } = useI18n();
    const { prefs } = usePrefs();
    const [data, setData] = useState(null);
    const [profile, setProfile] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [draft, setDraft] = useState(null); // meal being reviewed before saving
    const [analysing, setAnalysing] = useState(false);
    const [photoError, setPhotoError] = useState('');
    const [preview, setPreview] = useState(null);
    const fileRef = useRef(null);

    const load = () => apiFetch(BASE)
        .then((r) => { if (r.status !== 'success') throw new Error(errorMessage(r, t('nut.loadError'))); setData(r.data); })
        .catch((e) => setLoadError(e.message || t('nut.loadError')));

    useEffect(() => {
        load();
        apiFetch('/api/v1/profile/me').then((r) => r.status === 'success' && setProfile(r.data)).catch(() => {});
    }, []);
    useEffect(() => () => preview && URL.revokeObjectURL(preview), [preview]);

    const goal = targets(profile, prefs.trainingDays);
    const todayTotal = data?.days?.find((d) => d.date === today()) || { calories: 0, proteinG: 0, meals: 0 };

    const chart = useMemo(() => {
        const out = {};
        for (let i = CHART_DAYS - 1; i >= 0; i--) {
            const d = new Date(Date.now() - i * 86400000);
            out[new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)] = 0;
        }
        (data?.days || []).forEach((d) => { if (d.date in out) out[d.date] = d.calories; });
        return out;
    }, [data]);

    const onPhoto = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = '';
        if (!file) return;
        setPhotoError('');
        setDraft(null);
        setAnalysing(true);
        setPreview(URL.createObjectURL(file));
        try {
            const small = await downscale(file);
            const form = new FormData();
            form.append('image', small, 'meal.jpg');
            const r = await apiFetch(`/api/v1/chat/meals/analyze?language=${lang}`, { method: 'POST', body: form });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('nut.photoError')));
            const est = r.data;
            if (!est.isFood) { setPhotoError(t('nut.notFood')); return; }
            setDraft({
                source: 'photo', date: today(), name: est.name || '', calories: String(est.calories ?? ''),
                proteinG: String(est.proteinG ?? ''), carbsG: String(est.carbsG ?? ''), fatG: String(est.fatG ?? ''),
                items: est.items || [], confidence: est.confidence, notes: est.notes,
            });
        } catch (err) {
            setPhotoError(err.message || t('nut.photoError'));
        } finally {
            setAnalysing(false);
        }
    };

    const manual = () => {
        setPhotoError('');
        setPreview(null);
        setDraft({ source: 'manual', date: today(), name: '', calories: '', proteinG: '', carbsG: '', fatG: '', items: [] });
    };

    const remove = async (meal) => {
        const r = await apiFetch(`${BASE}/${meal.id}`, { method: 'DELETE' }).catch(() => null);
        if (r?.status === 'success') load();
        else alert(errorMessage(r, t('nut.deleteError')));
    };

    return (
        <>
            <h1 className="page-title">{t('nut.title')}</h1>
            <p className="page-lead">{t('nut.lead')}</p>
            {loadError && <p className="notice notice-error" role="alert">{loadError}</p>}

            <div className="ov-log nut-today">
                <div className="ov-log-item">
                    <div className="ov-log-value">{number(todayTotal.calories)}</div>
                    <div className="ov-log-label">{t('nut.kcalToday')}</div>
                    <div className="ov-log-hint">{goal?.calories ? t('nut.ofTarget', { n: number(goal.calories) }) : t('nut.noTarget')}</div>
                    {goal?.calories && <Meter value={todayTotal.calories} max={goal.calories} />}
                </div>
                <div className="ov-log-item">
                    <div className="ov-log-value">{number(Math.round(todayTotal.proteinG))}&nbsp;{t('nut.g')}</div>
                    <div className="ov-log-label">{t('nut.proteinToday')}</div>
                    <div className="ov-log-hint">{goal?.protein ? t('nut.ofTargetG', { n: number(goal.protein) }) : t('nut.noTarget')}</div>
                    {goal?.protein && <Meter value={todayTotal.proteinG} max={goal.protein} />}
                </div>
                <div className="ov-log-item">
                    <div className="ov-log-value">{number(todayTotal.meals)}</div>
                    <div className="ov-log-label">{t('nut.mealsToday')}</div>
                </div>
            </div>
            {goal?.calories && <p className="ov-note">{t('nut.targetNote')}</p>}

            <h2 className="section-title">{t('nut.logMeal')}</h2>
            <div className="nut-actions">
                <input ref={fileRef} type="file" accept="image/*" capture="environment" className="visually-hidden" id="meal-photo" onChange={onPhoto} />
                <label htmlFor="meal-photo" className="btn btn-primary nut-snap"><Camera size={18} aria-hidden="true" /> {t('nut.snap')}</label>
                <button type="button" className="btn" onClick={manual}><PenLine size={18} aria-hidden="true" /> {t('nut.manual')}</button>
            </div>
            <p className="prog-tip">{t('nut.photoPrivacy')}</p>

            {(analysing || draft || photoError) && (
                <div className="nut-review">
                    {preview && <img src={preview} alt="" className="nut-photo" />}
                    <div className="nut-review-body">
                        {analysing && <p className="nut-analysing"><Loader2 size={18} className="spin" aria-hidden="true" /> {t('nut.analysing')}</p>}
                        {photoError && <p className="notice notice-error" role="alert">{photoError}</p>}
                        {draft && <MealForm draft={draft} setDraft={setDraft} onSaved={() => { setDraft(null); setPreview(null); load(); }} />}
                    </div>
                </div>
            )}

            <h2 className="section-title">{t('nut.history')}</h2>
            <DailyBars title={t('nut.kcalPerDay')} data={chart} />

            {data?.meals?.length > 0 && (
                <div className="adm-table-wrap nut-table">
                    <table className="adm-table">
                        <thead>
                            <tr>
                                <th>{t('prog.date')}</th><th>{t('nut.meal')}</th>
                                <th className="num">{t('nut.kcal')}</th><th className="num">{t('nut.protein')}</th>
                                <th><span className="visually-hidden">{t('prog.deleteEntry')}</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.meals.slice(0, 20).map((m) => (
                                <tr key={m.id}>
                                    <td className="soft">{shortDate(m.date)}</td>
                                    <td>
                                        <span className="adm-name" dir="auto">{m.name}</span>
                                        {m.source === 'photo' && <span className="nut-tag"><Camera size={12} aria-hidden="true" /> {t('nut.fromPhoto')}</span>}
                                    </td>
                                    <td className="num">{number(m.calories)}</td>
                                    <td className="num">{number(Math.round(m.proteinG))}&nbsp;{t('nut.g')}</td>
                                    <td>
                                        <button type="button" className="icon-btn" onClick={() => remove(m)} aria-label={t('prog.deleteEntry')}>
                                            <Trash2 size={15} aria-hidden="true" />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            {data && data.meals.length === 0 && <p className="muted">{t('nut.none')}</p>}
        </>
    );
}

function Meter({ value, max }) {
    const pct = Math.min(100, Math.round((value / max) * 100));
    return (
        <div className="nut-meter" role="meter" aria-valuemin={0} aria-valuemax={max} aria-valuenow={Math.round(value)}>
            <span style={{ width: `${pct}%` }} className={value > max * 1.1 ? 'over' : ''} />
        </div>
    );
}

function MealForm({ draft, setDraft, onSaved }) {
    const { t } = useI18n();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const set = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }));
    const num = (v) => (v === '' ? null : Number(v));

    const save = async (e) => {
        e.preventDefault();
        setBusy(true);
        setError('');
        try {
            const r = await apiFetch(BASE, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    date: draft.date, name: draft.name, calories: Math.round(Number(draft.calories)), proteinG: Number(draft.proteinG),
                    carbsG: num(draft.carbsG), fatG: num(draft.fatG), source: draft.source,
                }),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('prog.saveError')));
            onSaved();
        } catch (err) {
            setError(err.message);
            setBusy(false);
        }
    };

    return (
        <form className="set-form" onSubmit={save}>
            {draft.source === 'photo' && (
                <div className="nut-estimate">
                    <p className="set-subtitle">{t('nut.estimate')} <span className={`nut-conf ${draft.confidence}`}>{t(`nut.conf.${draft.confidence || 'medium'}`)}</span></p>
                    {draft.items.length > 0 && <ul className="nut-items">{draft.items.map((i) => <li key={i} dir="auto">{i}</li>)}</ul>}
                    {draft.notes && <p className="field-hint" dir="auto">{draft.notes}</p>}
                    <p className="field-hint">{t('nut.checkNumbers')}</p>
                </div>
            )}
            <label className="field">
                <span className="field-label">{t('nut.meal')}</span>
                <input className="input" required maxLength={120} dir="auto" value={draft.name} onChange={set('name')} />
            </label>
            <div className="prog-row nut-macros">
                <Num label={t('nut.kcal')} value={draft.calories} onChange={set('calories')} max={5000} step="1" required />
                <Num label={t('nut.proteinG')} value={draft.proteinG} onChange={set('proteinG')} max={400} required />
                <Num label={t('nut.carbsG')} value={draft.carbsG} onChange={set('carbsG')} max={800} />
                <Num label={t('nut.fatG')} value={draft.fatG} onChange={set('fatG')} max={400} />
            </div>
            <label className="field nut-date">
                <span className="field-label">{t('prog.date')}</span>
                <input className="input" type="date" max={today()} required value={draft.date} onChange={set('date')} />
            </label>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
            <div className="prog-form-foot">
                <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('prog.saving') : t('nut.save')}</button>
                <button type="button" className="btn" onClick={() => setDraft(null)}>{t('prof.cancel')}</button>
            </div>
        </form>
    );
}

function Num({ label, value, onChange, max, step = '0.1', required = false }) {
    return (
        <label className="field">
            <span className="field-label">{label}</span>
            <input className="input" type="number" min="0" max={max} step={step} inputMode="decimal" required={required} value={value} onChange={onChange} />
        </label>
    );
}

export default Nutrition;
