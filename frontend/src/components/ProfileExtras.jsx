import React, { useEffect, useState } from 'react';
import { Award, CalendarPlus, Dumbbell, Flag, Gauge, Scale, Download, FileText } from 'lucide-react';
import { apiFetch } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { downloadJson, eventText, gatherData, printReport } from '../dataExport';

const DIETS = ['halal', 'vegetarian', 'vegan', 'pescatarian', 'lactose_free', 'gluten_free', 'keto'];

/** Injuries, allergies and dietary style: saved with the preferences and given to the coach. */
export function HealthNotes() {
    const { t } = useI18n();
    const { prefs, update } = usePrefs();
    const [injuries, setInjuries] = useState('');
    const [allergies, setAllergies] = useState('');
    const [diet, setDiet] = useState([]);
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null);

    useEffect(() => {
        setInjuries(prefs.injuries ?? '');
        setAllergies(prefs.allergies ?? '');
        setDiet(prefs.diet ?? []);
    }, [prefs.injuries, prefs.allergies, prefs.diet]);

    const toggleDiet = (code) => setDiet((d) => (d.includes(code) ? d.filter((x) => x !== code) : [...d, code]));

    const submit = async (e) => {
        e.preventDefault();
        setBusy(true);
        setStatus(null);
        try {
            await update({ injuries, allergies, diet });
            setStatus({ ok: true, text: t('health.saved') });
        } catch (err) {
            setStatus({ ok: false, text: err.message });
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="prof-section">
            <h2 className="section-title">{t('health.title')}</h2>
            <p className="set-help">{t('health.help')}</p>
            <form className="set-form" onSubmit={submit}>
                <fieldset className="field">
                    <legend className="field-label">{t('health.diet')}</legend>
                    <div className="chip-group">
                        {DIETS.map((code) => (
                            <label key={code} className={`chip${diet.includes(code) ? ' on' : ''}`}>
                                <input type="checkbox" checked={diet.includes(code)} onChange={() => toggleDiet(code)} />
                                {t(`diet.${code}`)}
                            </label>
                        ))}
                    </div>
                </fieldset>
                <div className="prog-row">
                    <label className="field">
                        <span className="field-label">{t('health.allergies')}</span>
                        <textarea className="input set-textarea" rows={3} maxLength={300} dir="auto" value={allergies}
                                  onChange={(e) => setAllergies(e.target.value)} aria-describedby="allergies-hint" />
                        <span className="field-hint" id="allergies-hint">{t('health.allergiesHint')}</span>
                    </label>
                    <label className="field">
                        <span className="field-label">{t('set.injuries')}</span>
                        <textarea className="input set-textarea" rows={3} maxLength={300} dir="auto" value={injuries}
                                  onChange={(e) => setInjuries(e.target.value)} aria-describedby="injuries-hint" />
                        <span className="field-hint" id="injuries-hint">{t('set.injuriesHint')}</span>
                    </label>
                </div>
                <div className="prog-form-foot">
                    <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('set.saving') : t('health.save')}</button>
                    {status && <p className={`set-status ${status.ok ? 'prog-ok' : 'prog-err'}`} role={status.ok ? 'status' : 'alert'}>{status.text}</p>}
                </div>
            </form>
        </section>
    );
}

const EVENT_ICONS = { joined: CalendarPlus, first_weighin: Scale, first_workout: Dumbbell, goal: Flag, level: Gauge, badge: Award };

/** "60 → 61.3 kg since you joined" and a newest-first milestone timeline. */
export function ProfileHistory({ refreshKey }) {
    const { t, tv, number, shortDate } = useI18n();
    const { weight } = usePrefs();
    const [h, setH] = useState(null);
    const [showAll, setShowAll] = useState(false);

    useEffect(() => {
        apiFetch('/api/v1/profile/me/history').then((r) => r.status === 'success' && setH(r.data)).catch(() => {});
    }, [refreshKey]);

    if (!h) return null;
    const events = showAll ? h.events : h.events.slice(0, 8);
    const change = h.weightChangeKg;

    return (
        <section className="prof-section">
            <h2 className="section-title">{t('hist.title')}</h2>
            {h.startWeightKg != null && h.currentWeightKg != null && (
                <p className="hist-summary">
                    <span className="hist-weights">{weight(h.startWeightKg)} <span aria-hidden="true">→</span><span className="visually-hidden">{t('hist.to')}</span> {weight(h.currentWeightKg)}</span>
                    <span className={`hist-delta${change > 0 ? ' up' : change < 0 ? ' down' : ''}`}>
                        {change > 0 ? '+' : ''}{weight(change)}
                    </span>
                    <span className="hist-since">
                        {h.since ? t('hist.since', { date: shortDate(h.since) }) : ''}
                        {' '}{t('hist.counts', { weighIns: number(h.weighIns), workouts: number(h.workouts) })}
                    </span>
                </p>
            )}
            {events.length === 0 ? <p className="muted">{t('hist.empty')}</p> : (
                <ol className="hist-timeline">
                    {events.map((e, i) => {
                        const Icon = EVENT_ICONS[e.type] || CalendarPlus;
                        return (
                            <li key={`${e.type}-${e.date}-${i}`} className={`hist-item hist-${e.type}`}>
                                <span className="hist-dot" aria-hidden="true"><Icon size={15} /></span>
                                <span className="hist-text">{eventText(e, { t, tv })}</span>
                                <time className="hist-date" dateTime={e.date}>{shortDate(e.date)}</time>
                            </li>
                        );
                    })}
                </ol>
            )}
            {h.events.length > 8 && (
                <button type="button" className="btn btn-small" onClick={() => setShowAll((v) => !v)}>
                    {showAll ? t('hist.showLess') : t('hist.showAll', { n: number(h.events.length) })}
                </button>
            )}
        </section>
    );
}

/** Download everything as JSON, or a printable report the browser saves as PDF. */
export function DataExport() {
    const { t, tv, lang } = useI18n();
    const [busy, setBusy] = useState(null);
    const [error, setError] = useState('');

    const run = async (kind) => {
        setBusy(kind);
        setError('');
        // open the report window inside the click, before any await, so popup blockers allow it
        const win = kind === 'pdf' ? window.open('', '_blank') : null;
        if (win) win.document.write(`<p style="font-family:sans-serif">${t('exp.preparing')}</p>`);
        try {
            const data = await gatherData(t('set.exportError'));
            if (kind === 'json') downloadJson(data);
            else if (win) printReport(win, data, { t, tv, lang });
            else throw new Error(t('exp.popupBlocked'));
        } catch (err) {
            if (win) win.close();
            setError(err.message || t('set.exportError'));
        } finally {
            setBusy(null);
        }
    };

    return (
        <section className="prof-section">
            <h2 className="section-title">{t('exp.title')}</h2>
            <p className="set-help">{t('exp.help')}</p>
            <div className="prog-form-foot">
                <button type="button" className="btn" onClick={() => run('pdf')} disabled={!!busy}>
                    <FileText size={16} aria-hidden="true" /> {busy === 'pdf' ? t('set.exporting') : t('exp.pdf')}
                </button>
                <button type="button" className="btn" onClick={() => run('json')} disabled={!!busy}>
                    <Download size={16} aria-hidden="true" /> {busy === 'json' ? t('set.exporting') : t('exp.json')}
                </button>
                {error && <p className="set-status prog-err" role="alert">{error}</p>}
            </div>
        </section>
    );
}
