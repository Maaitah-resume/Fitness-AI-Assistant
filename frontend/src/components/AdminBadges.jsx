import React, { useEffect, useMemo, useState } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';
import { badgeIcon, badgeText } from '../badgeIcons';

const API = '/api/v1/profile/admin/badges';
const EMPTY = { name: '', nameAr: '', description: '', descriptionAr: '', icon: 'star', color: 'blue', rule: 'workouts', threshold: 10, exercise: '', active: true };

/** Admins define badges (look + rule); users earn them automatically from their logs. */
function AdminBadges() {
    const { t, number, lang } = useI18n();
    const [badges, setBadges] = useState(null);
    const [options, setOptions] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [form, setForm] = useState(null); // null = closed; { id?, ...fields } = creating or editing
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null);

    const load = () => apiFetch(API).then((r) => {
        if (r.status !== 'success') throw new Error(errorMessage(r, t('bdg.loadError')));
        setBadges(r.data.badges);
    }).catch((e) => setLoadError(e.message || t('bdg.loadError')));

    useEffect(() => {
        load();
        apiFetch(`${API}/options`).then((r) => r.status === 'success' && setOptions(r.data)).catch(() => {});
    }, []);

    const rules = options?.rules || [];
    const rule = useMemo(() => rules.find((r) => r.key === form?.rule), [rules, form?.rule]);
    const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

    const save = async (e) => {
        e.preventDefault();
        setBusy(true);
        setStatus(null);
        const body = { ...form, threshold: Number(form.threshold), exercise: rule?.needsExercise ? form.exercise : null };
        try {
            const r = await apiFetch(form.id ? `${API}/${form.id}` : API, {
                method: form.id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('bdg.saveError')));
            setStatus({ ok: true, text: form.id ? t('bdg.updated', { name: r.data.name }) : t('bdg.created', { name: r.data.name }) });
            setForm(null);
            load();
        } catch (err) {
            setStatus({ ok: false, text: err.message });
        } finally {
            setBusy(false);
        }
    };

    const toggle = async (b) => {
        const r = await apiFetch(`${API}/${b.id}`, {
            method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...b, active: !b.active }),
        });
        if (r.status === 'success') load(); else setStatus({ ok: false, text: errorMessage(r, t('bdg.saveError')) });
    };

    const remove = async (b) => {
        if (!window.confirm(t('bdg.deleteConfirm', { name: b.name }))) return;
        const r = await apiFetch(`${API}/${b.id}`, { method: 'DELETE' });
        if (r.status === 'success') { setStatus({ ok: true, text: t('bdg.deleted', { name: b.name }) }); load(); }
        else setStatus({ ok: false, text: errorMessage(r, t('bdg.saveError')) });
    };

    const ruleLine = (b) => {
        const info = rules.find((r) => r.key === b.rule);
        return t(`bdg.rule.${b.rule}.line`, { n: number(b.threshold), exercise: b.exercise || '' }) || info?.unit;
    };

    return (
        <section>
            <div className="adm-users-head">
                <p className="set-help">{t('bdg.help')}</p>
                {!form && (
                    <button type="button" className="btn btn-primary" onClick={() => { setStatus(null); setForm({ ...EMPTY }); }}>
                        <Plus size={16} aria-hidden="true" /> {t('bdg.new')}
                    </button>
                )}
            </div>

            {status && <p className={`set-status ${status.ok ? 'prog-ok' : 'prog-err'}`} role={status.ok ? 'status' : 'alert'}>{status.text}</p>}

            {form && (
                <form className="bdg-form" onSubmit={save}>
                    <h3 className="set-subtitle">{form.id ? t('bdg.editTitle') : t('bdg.newTitle')}</h3>
                    <div className="bdg-form-grid">
                        <div className="bdg-fields">
                            <div className="prog-row">
                                <label className="field"><span className="field-label">{t('bdg.name')}</span>
                                    <input className="input" required maxLength={60} value={form.name} onChange={set('name')} /></label>
                                <label className="field"><span className="field-label">{t('bdg.nameAr')}</span>
                                    <input className="input" dir="rtl" maxLength={60} value={form.nameAr || ''} onChange={set('nameAr')} /></label>
                            </div>
                            <div className="prog-row">
                                <label className="field"><span className="field-label">{t('bdg.desc')}</span>
                                    <input className="input" maxLength={200} value={form.description || ''} onChange={set('description')} /></label>
                                <label className="field"><span className="field-label">{t('bdg.descAr')}</span>
                                    <input className="input" dir="rtl" maxLength={200} value={form.descriptionAr || ''} onChange={set('descriptionAr')} /></label>
                            </div>

                            <fieldset className="field bdg-rule">
                                <legend className="field-label">{t('bdg.rule')}</legend>
                                <div className="prog-row">
                                    <label className="field"><span className="visually-hidden">{t('bdg.rule')}</span>
                                        <select className="input" value={form.rule} onChange={set('rule')}>
                                            {rules.map((r) => <option key={r.key} value={r.key}>{t(`bdg.rule.${r.key}`)}</option>)}
                                        </select></label>
                                    <label className="field"><span className="field-label">{t('bdg.threshold')}</span>
                                        <input className="input" type="number" required min="0.1" step="0.1" max={form.rule === 'protein_days_in_week' ? 7 : undefined}
                                               value={form.threshold} onChange={set('threshold')} aria-describedby="bdg-unit" />
                                        <span className="field-hint" id="bdg-unit">{rule ? t(`bdg.rule.${rule.key}.unit`) : ''}</span></label>
                                    {rule?.needsExercise && (
                                        <label className="field"><span className="field-label">{t('bdg.exercise')}</span>
                                            <input className="input" required maxLength={60} placeholder={t('bdg.exercisePh')} value={form.exercise || ''} onChange={set('exercise')} /></label>
                                    )}
                                </div>
                            </fieldset>

                            <fieldset className="field">
                                <legend className="field-label">{t('bdg.icon')}</legend>
                                <div className="bdg-icons">
                                    {(options?.icons || []).map((name) => {
                                        const Icon = badgeIcon(name);
                                        return (
                                            <label key={name} className={`bdg-icon-opt${form.icon === name ? ' on' : ''}`} title={name}>
                                                <input type="radio" name="icon" value={name} checked={form.icon === name} onChange={set('icon')} />
                                                <Icon size={18} aria-hidden="true" /><span className="visually-hidden">{name}</span>
                                            </label>
                                        );
                                    })}
                                </div>
                            </fieldset>
                            <fieldset className="field">
                                <legend className="field-label">{t('bdg.color')}</legend>
                                <div className="bdg-colors">
                                    {(options?.colors || []).map((c) => (
                                        <label key={c} className={`bdg-color-opt${form.color === c ? ' on' : ''}`} data-plate={c}>
                                            <input type="radio" name="color" value={c} checked={form.color === c} onChange={set('color')} />
                                            <span className="bdg-swatch" aria-hidden="true" />{t(`bdg.color.${c}`)}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>
                            <label className="bdg-active"><input type="checkbox" checked={form.active} onChange={set('active')} /> {t('bdg.activeLabel')}</label>
                        </div>

                        <div className="bdg-preview" aria-label={t('bdg.preview')}>
                            <span className="field-label">{t('bdg.preview')}</span>
                            <BadgeTile badge={form} lang={lang} line={ruleLine(form)} />
                        </div>
                    </div>
                    <div className="prog-form-foot">
                        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? t('set.saving') : form.id ? t('bdg.saveChanges') : t('bdg.create')}</button>
                        <button type="button" className="btn" onClick={() => setForm(null)}>{t('prof.cancel')}</button>
                    </div>
                </form>
            )}

            {loadError && <p className="notice notice-error" role="alert">{loadError}</p>}
            {badges && (
                <div className="adm-table-wrap">
                    <table className="adm-table">
                        <thead><tr>
                            <th>{t('bdg.colBadge')}</th><th>{t('bdg.rule')}</th><th className="num">{t('bdg.colEarned')}</th>
                            <th>{t('bdg.colStatus')}</th><th><span className="visually-hidden">{t('bdg.colActions')}</span></th>
                        </tr></thead>
                        <tbody>
                            {badges.map((b) => {
                                const Icon = badgeIcon(b.icon);
                                const text = badgeText(b, lang);
                                return (
                                    <tr key={b.id} className={b.active ? '' : 'bdg-off'}>
                                        <td><span className="bdg-cell" data-plate={b.color}>
                                            <span className="bdg-dot"><Icon size={16} aria-hidden="true" /></span>
                                            <span><span className="adm-name" dir="auto">{text.name}</span><span className="adm-email" dir="auto">{text.description}</span></span>
                                        </span></td>
                                        <td dir="auto">{ruleLine(b)}</td>
                                        <td className="num">{number(b.earnedBy)}</td>
                                        <td><button type="button" className={`bdg-status${b.active ? ' on' : ''}`} onClick={() => toggle(b)}
                                                    aria-pressed={b.active}>{b.active ? t('bdg.active') : t('bdg.off')}</button></td>
                                        <td className="bdg-actions">
                                            <button type="button" className="icon-btn" onClick={() => { setStatus(null); setForm({ ...b, exercise: b.exercise || '' }); window.scrollTo({ top: 0 }); }}
                                                    aria-label={t('bdg.editLabel', { name: b.name })}><Pencil size={16} /></button>
                                            <button type="button" className="icon-btn danger" onClick={() => remove(b)}
                                                    aria-label={t('bdg.deleteLabel', { name: b.name })}><Trash2 size={16} /></button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}

/** The badge as users will see it (earned state). */
function BadgeTile({ badge, lang, line }) {
    const Icon = badgeIcon(badge.icon);
    const text = badgeText(badge, lang);
    return (
        <div className="ach-badge earned" data-plate={badge.color}>
            <span className="ach-badge-icon" aria-hidden="true"><Icon size={22} /></span>
            <span className="ach-badge-name" dir="auto">{text.name || '…'}</span>
            <span className="ach-badge-desc" dir="auto">{text.description || line}</span>
        </div>
    );
}

export default AdminBadges;
