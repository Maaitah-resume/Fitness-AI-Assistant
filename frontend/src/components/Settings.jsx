import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch, errorMessage, getUser, logout } from '../api';
import { useI18n } from '../i18n';
import { downloadJson, gatherData } from '../dataExport';
import { contrast, hexToRgb, usePrefs } from '../prefs';

const EQUIPMENT = ['full_gym', 'dumbbells', 'home', 'bodyweight'];

function Settings() {
    const { t, lang, setLang } = useI18n();
    const { prefs, update, theme, setTheme } = usePrefs();
    const [status, setStatus] = useState({}); // per-section { ok, text }

    // Option groups save as soon as one is picked.
    const save = async (section, patch) => {
        setStatus((s) => ({ ...s, [section]: { ok: true, text: t('set.saving') } }));
        try {
            await update(patch);
            setStatus((s) => ({ ...s, [section]: { ok: true, text: t('set.saved') } }));
        } catch (e) {
            setStatus((s) => ({ ...s, [section]: { ok: false, text: e.message } }));
        }
    };

    return (
        <>
            <h1 className="page-title">{t('set.title')}</h1>
            <p className="page-lead">{t('set.lead')}</p>

            <div className="set-sections">
                <OptionGroup title={t('set.language')} help={t('set.languageHelp')} name="language" value={lang} onChange={setLang}
                             options={[
                                 { value: 'en', label: 'English', hint: 'Left-to-right layout', lang: 'en' },
                                 { value: 'ar', label: 'العربية', hint: 'تخطيط من اليمين إلى اليسار', lang: 'ar' },
                             ]} />

                <AppearanceSection />

                <OptionGroup title={t('set.units')} help={t('set.unitsHelp')} name="units" value={prefs.units} status={status.units}
                             onChange={(v) => save('units', { units: v })}
                             options={[
                                 { value: 'metric', label: t('set.metric'), hint: t('set.metricHint') },
                                 { value: 'imperial', label: t('set.imperial'), hint: t('set.imperialHint') },
                             ]} />

                <section className="set-section">
                    <h2 className="set-title">{t('set.coach')}</h2>
                    <p className="set-help">{t('set.coachHelp')}</p>
                    <OptionGroup sub title={t('set.length')} name="answerLength" value={prefs.answerLength}
                                 onChange={(v) => save('coach', { answerLength: v })}
                                 options={['short', 'detailed'].map((v) => ({ value: v, label: t(`set.${v}`), hint: t(`set.${v}Hint`) }))} />
                    <OptionGroup sub title={t('set.tone')} name="tone" value={prefs.tone} status={status.coach}
                                 onChange={(v) => save('coach', { tone: v })}
                                 options={['friendly', 'strict'].map((v) => ({ value: v, label: t(`set.${v}`), hint: t(`set.${v}Hint`) }))} />
                </section>

                <TrainingSection />
                <AccountSection />
            </div>
        </>
    );
}

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/** Radio choices drawn as cards. `sub` renders a smaller heading inside a section. */
function OptionGroup({ title, help, name, value, options, onChange, status, columns = 2, sub = false }) {
    const body = (
        <fieldset className={`set-options cols-${columns}`}>
            <legend className={sub ? 'set-subtitle' : 'set-title'}>{title}</legend>
            {help && <p className="set-help">{help}</p>}
            {options.map((o) => (
                <label key={o.value} className={`set-option${value === o.value ? ' is-on' : ''}`}
                       lang={o.lang} dir={o.lang ? (o.lang === 'ar' ? 'rtl' : 'ltr') : undefined}>
                    <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)} />
                    {o.swatch && <span className={`set-swatch ${o.swatch}`} aria-hidden="true" />}
                    <span className="set-option-label">{o.label}</span>
                    <span className="set-option-hint">{o.hint}</span>
                </label>
            ))}
            {status && <Status status={status} />}
        </fieldset>
    );
    return sub ? body : <section className="set-section">{body}</section>;
}

const ACCENTS = ['#0E8F63', '#2453FF', '#C0306F', '#F0601F', '#7C4DDB', '#0891B2', '#D2342F', '#B7700A'];
const TEXTS = ['#1C2321', '#10151B', '#000000', '#1E2A4A', '#34193A', '#3B2A1A'];

/** Theme style, light/dark, accent and text colours and text size, all per device and previewed live. */
function AppearanceSection() {
    const { t } = useI18n();
    const { theme, setTheme, appearance, setAppearance, resetAppearance, persona } = usePrefs();
    const page = typeof window === 'undefined' ? '#ffffff'
        : getComputedStyle(document.documentElement).getPropertyValue('--chalk').trim() || '#ffffff';
    const textRatio = hexToRgb(appearance.textColor) && hexToRgb(page) ? contrast(appearance.textColor, page) : null;
    const accentRatio = hexToRgb(appearance.accent) ? contrast(appearance.accent, '#ffffff') : null;
    const autoName = persona === 'male' ? t('app.styleMale') : persona === 'female' ? t('app.styleFemale') : t('app.styleNeutral');

    return (
        <section className="set-section app-section">
            <div className="app-head">
                <div>
                    <h2 className="set-title">{t('set.appearance')}</h2>
                    <p className="set-help">{t('app.help')}</p>
                </div>
                <button type="button" className="btn btn-quiet" onClick={() => { resetAppearance(); setTheme('system'); }}>{t('app.reset')}</button>
            </div>

            <OptionGroup sub title={t('app.style')} name="style" value={appearance.style} onChange={(v) => setAppearance({ style: v })} columns={4}
                         options={[
                             { value: 'auto', label: t('app.styleAuto'), hint: t('app.styleAutoHint', { name: autoName }), swatch: 'p-auto' },
                             { value: 'male', label: t('app.styleMale'), hint: t('app.styleMaleHint'), swatch: 'p-male' },
                             { value: 'female', label: t('app.styleFemale'), hint: t('app.styleFemaleHint'), swatch: 'p-female' },
                             { value: 'neutral', label: t('app.styleNeutral'), hint: t('app.styleNeutralHint'), swatch: 'p-neutral' },
                         ]} />

            <OptionGroup sub title={t('app.mode')} name="theme" value={theme} onChange={setTheme} columns={3}
                         options={['system', 'light', 'dark'].map((v) => ({
                             value: v, label: t(`set.theme${cap(v)}`), hint: t(`set.theme${cap(v)}Hint`), swatch: v,
                         }))} />

            <ColorChoice title={t('app.accent')} help={t('app.accentHelp')} value={appearance.accent} presets={ACCENTS}
                         onChange={(v) => setAppearance({ accent: v })} t={t}
                         warning={accentRatio !== null && accentRatio < 3 ? t('app.accentLow') : ''} />
            <ColorChoice title={t('app.text')} help={t('app.textHelp')} value={appearance.textColor} presets={TEXTS}
                         onChange={(v) => setAppearance({ textColor: v })} t={t}
                         warning={textRatio !== null && textRatio < 4.5 ? t('app.textLow', { ratio: textRatio.toFixed(1) }) : ''} />

            <OptionGroup sub title={t('app.size')} name="fontScale" value={appearance.fontScale} onChange={(v) => setAppearance({ fontScale: v })} columns={4}
                         options={['sm', 'md', 'lg', 'xl'].map((v) => ({ value: v, label: t(`app.size.${v}`), hint: t(`app.size.${v}Hint`) }))} />

            <div className="app-preview" aria-label={t('app.preview')}>
                <span className="set-subtitle">{t('app.preview')}</span>
                <div className="app-preview-card">
                    <h3>{t('app.previewTitle')}</h3>
                    <p>{t('app.previewBody')}</p>
                    <div className="app-preview-row">
                        <button type="button" className="btn btn-primary btn-small" tabIndex={-1}>{t('app.previewBtn')}</button>
                        <a href="#appearance" onClick={(e) => e.preventDefault()} tabIndex={-1}>{t('app.previewLink')}</a>
                    </div>
                </div>
            </div>
        </section>
    );
}

/** Preset swatches plus a free colour picker; the first option ("Theme") clears the override. */
function ColorChoice({ title, help, value, presets, onChange, warning, t }) {
    return (
        <fieldset className="app-colors">
            <legend className="set-subtitle">{title}</legend>
            <p className="set-help">{help}</p>
            <div className="app-swatches">
                <label className={`app-swatch app-swatch-theme${!value ? ' on' : ''}`}>
                    <input type="radio" checked={!value} onChange={() => onChange('')} />
                    <span>{t('app.themeColor')}</span>
                </label>
                {presets.map((c) => (
                    <label key={c} className={`app-swatch${value.toLowerCase() === c.toLowerCase() ? ' on' : ''}`} title={c}>
                        <input type="radio" checked={value.toLowerCase() === c.toLowerCase()} onChange={() => onChange(c)} />
                        <span className="app-dot" style={{ background: c }} aria-hidden="true" /><span className="visually-hidden">{c}</span>
                    </label>
                ))}
                <label className={`app-swatch app-custom${value && !presets.some((c) => c.toLowerCase() === value.toLowerCase()) ? ' on' : ''}`}>
                    <input type="color" value={value || '#888888'} onChange={(e) => onChange(e.target.value)} aria-label={t('app.custom')} />
                    <span>{t('app.custom')}</span>
                </label>
            </div>
            {warning && <p className="set-status prog-err" role="status">{warning}</p>}
        </fieldset>
    );
}

function Status({ status }) {
    return <p className={`set-status ${status.ok ? 'prog-ok' : 'prog-err'}`} role={status.ok ? 'status' : 'alert'}>{status.text}</p>;
}

function TrainingSection() {
    const { t } = useI18n();
    const { prefs, update } = usePrefs();
    const [days, setDays] = useState('');
    const [equipment, setEquipment] = useState('');
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null);

    // fill the form once the saved preferences arrive
    useEffect(() => {
        setDays(prefs.trainingDays ?? '');
        setEquipment(prefs.equipment ?? '');
    }, [prefs.trainingDays, prefs.equipment]);

    const submit = async (e) => {
        e.preventDefault();
        setBusy(true);
        setStatus(null);
        try {
            // 0 / "none" / "" clear a field on the server
            await update({ trainingDays: days === '' ? 0 : Number(days), equipment: equipment || 'none' });
            setStatus({ ok: true, text: t('set.saved') });
        } catch (err) {
            setStatus({ ok: false, text: err.message });
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="set-section">
            <form className="set-form" onSubmit={submit}>
                <h2 className="set-title">{t('set.training')}</h2>
                <p className="set-help">{t('set.trainingHelp')}</p>
                <div className="prog-row">
                    <label className="field">
                        <span className="field-label">{t('set.days')}</span>
                        <select className="input" value={days} onChange={(e) => setDays(e.target.value)}>
                            <option value="">{t('set.notSet')}</option>
                            {[1, 2, 3, 4, 5, 6, 7].map((n) => <option key={n} value={n}>{n}</option>)}
                        </select>
                    </label>
                    <label className="field">
                        <span className="field-label">{t('set.equipment')}</span>
                        <select className="input" value={equipment} onChange={(e) => setEquipment(e.target.value)}>
                            <option value="">{t('set.notSet')}</option>
                            {EQUIPMENT.map((v) => <option key={v} value={v}>{t(`set.eq.${v}`)}</option>)}
                        </select>
                    </label>
                </div>
                <p className="field-hint">{t('set.healthMoved')}</p>
                <div className="prog-form-foot">
                    <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('set.saving') : t('set.saveTraining')}</button>
                    {status && <Status status={status} />}
                </div>
            </form>
        </section>
    );
}

function AccountSection() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const { username } = getUser();

    const [pw, setPw] = useState({ current: '', next: '', confirm: '' });
    const [pwBusy, setPwBusy] = useState(false);
    const [pwStatus, setPwStatus] = useState(null);

    const [exporting, setExporting] = useState(false);
    const [exportError, setExportError] = useState('');

    const [deletePw, setDeletePw] = useState('');
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState('');

    const changePassword = async (e) => {
        e.preventDefault();
        setPwStatus(null);
        if (pw.next !== pw.confirm) { setPwStatus({ ok: false, text: t('set.pwMismatch') }); return; }
        setPwBusy(true);
        try {
            const r = await apiFetch('/api/v1/auth/password', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword: pw.current, newPassword: pw.next }),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('set.saveError')));
            setPw({ current: '', next: '', confirm: '' });
            setPwStatus({ ok: true, text: t('set.pwChanged') });
        } catch (err) {
            setPwStatus({ ok: false, text: err.message });
        } finally {
            setPwBusy(false);
        }
    };

    const exportData = async () => {
        setExporting(true);
        setExportError('');
        try {
            downloadJson(await gatherData(t('set.exportError')));
        } catch (err) {
            setExportError(err.message || t('set.exportError'));
        } finally {
            setExporting(false);
        }
    };

    const deleteAccount = async (e) => {
        e.preventDefault();
        if (!window.confirm(t('set.deleteConfirm'))) return;
        setDeleting(true);
        setDeleteError('');
        try {
            const r = await apiFetch('/api/v1/auth/me', {
                method: 'DELETE', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: deletePw }),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('set.saveError')));
            logout();
            navigate('/login');
        } catch (err) {
            setDeleteError(err.message);
            setDeleting(false);
        }
    };

    return (
        <section className="set-section">
            <h2 className="set-title">{t('set.account')}</h2>
            {username && <p className="set-help">{t('set.signedInAs', { name: username })}</p>}

            <form className="set-form set-block" onSubmit={changePassword}>
                <h3 className="set-subtitle">{t('set.password')}</h3>
                <label className="field">
                    <span className="field-label">{t('set.currentPw')}</span>
                    <input className="input" type="password" autoComplete="current-password" required value={pw.current}
                           onChange={(e) => setPw({ ...pw, current: e.target.value })} />
                </label>
                <div className="prog-row">
                    <label className="field">
                        <span className="field-label">{t('set.newPw')}</span>
                        <input className="input" type="password" autoComplete="new-password" minLength={8} required value={pw.next}
                               onChange={(e) => setPw({ ...pw, next: e.target.value })} aria-describedby="new-pw-hint" />
                        <span className="field-hint" id="new-pw-hint">{t('auth.pwHint')}</span>
                    </label>
                    <label className="field">
                        <span className="field-label">{t('set.confirmPw')}</span>
                        <input className="input" type="password" autoComplete="new-password" minLength={8} required value={pw.confirm}
                               onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />
                    </label>
                </div>
                <div className="prog-form-foot">
                    <button type="submit" className="btn btn-primary" disabled={pwBusy}>{pwBusy ? t('set.saving') : t('set.changePw')}</button>
                    {pwStatus && <Status status={pwStatus} />}
                </div>
            </form>

            <div className="set-block">
                <h3 className="set-subtitle">{t('set.export')}</h3>
                <p className="set-help">{t('set.exportHelp')}</p>
                <div className="prog-form-foot">
                    <button type="button" className="btn" onClick={exportData} disabled={exporting}>
                        {exporting ? t('set.exporting') : t('set.exportBtn')}
                    </button>
                    {exportError && <Status status={{ ok: false, text: exportError }} />}
                </div>
            </div>

            <form className="set-form set-block set-danger" onSubmit={deleteAccount}>
                <h3 className="set-subtitle">{t('set.delete')}</h3>
                <p className="set-help">{t('set.deleteHelp')}</p>
                <label className="field">
                    <span className="field-label">{t('set.deletePw')}</span>
                    <input className="input" type="password" autoComplete="current-password" required value={deletePw}
                           onChange={(e) => setDeletePw(e.target.value)} />
                </label>
                <div className="prog-form-foot">
                    <button type="submit" className="btn btn-danger" disabled={deleting}>{deleting ? t('set.deleting') : t('set.deleteBtn')}</button>
                    {deleteError && <Status status={{ ok: false, text: deleteError }} />}
                </div>
            </form>
        </section>
    );
}

export default Settings;
