import React, { useEffect, useState } from 'react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';
import { usePrefs } from '../prefs';
import { DataExport, HealthNotes, ProfileHistory } from './ProfileExtras';

const empty = { age: '', gender: '', heightCm: '', weightKg: '', goal: '', level: '' };
const toForm = (profile) => Object.fromEntries(Object.keys(empty).map((key) => [key, profile[key] ?? '']));
const GOALS = ['muscle_gain', 'fat_loss', 'endurance', 'general_fitness'];
const LEVELS = ['beginner', 'intermediate', 'advanced'];
const GENDERS = ['male', 'female', 'other'];

function Profile() {
    const { t, tv, number } = useI18n();
    const { imperial, weight, height, weightUnit, fromKg, toKg, cmToFtIn, ftInToCm, setGenderPersona } = usePrefs();
    const [form, setForm] = useState(empty);
    const [saved, setSaved] = useState(empty);
    const [complete, setComplete] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    // A complete profile opens as a read-only sheet; the form shows on first visit or after "Edit profile".
    const [editing, setEditing] = useState(false);
    const [bmi, setBmi] = useState(null);
    const [savedCount, setSavedCount] = useState(0); // refreshes the history after a save

    const apply = (profile) => {
        setForm(toForm(profile));
        setSaved(toForm(profile));
        setBmi(profile.bmi ?? null);
        setComplete(!!profile.complete);
        setEditing(!profile.complete);
    };

    useEffect(() => {
        apiFetch('/api/v1/profile/me')
            .then((result) => {
                if (result.status !== 'success') throw new Error(errorMessage(result, t('prof.loadError')));
                apply(result.data || {});
            })
            .catch((e) => setError(e.message || t('prof.loadError')))
            .finally(() => setLoading(false));
    }, []);

    const change = (e) => setForm((old) => ({ ...old, [e.target.name]: e.target.value }));

    // Weight and height inputs are typed in the user's unit; form keeps kg/cm for saving.
    const [disp, setDisp] = useState({ weight: '', height: '', ft: '', inch: '' });
    useEffect(() => {
        const { ft, in: inch } = cmToFtIn(form.heightCm);
        setDisp({ weight: form.weightKg === '' ? '' : String(Math.round(fromKg(form.weightKg) * 10) / 10), height: form.heightCm, ft, inch });
    }, [editing, imperial]); // re-derive when the form opens or the unit changes
    const changeWeight = (v) => {
        setDisp((d) => ({ ...d, weight: v }));
        setForm((f) => ({ ...f, weightKg: v === '' ? '' : toKg(v) }));
    };
    const changeHeight = (v) => {
        setDisp((d) => ({ ...d, height: v }));
        setForm((f) => ({ ...f, heightCm: v }));
    };
    const changeFtIn = (ft, inch) => {
        setDisp((d) => ({ ...d, ft, inch }));
        setForm((f) => ({ ...f, heightCm: ft === '' && inch === '' ? '' : ftInToCm(ft, inch) }));
    };

    const save = async (e) => {
        e.preventDefault();
        setSaving(true);
        setError('');
        setMessage('');
        // Blank fields go as null (left unchanged by the backend); '' would fail its @Pattern validation.
        const numeric = ['age', 'heightCm', 'weightKg'];
        const body = Object.fromEntries(Object.entries(form).map(([key, value]) =>
            [key, value === '' ? null : numeric.includes(key) ? Number(value) : value]));
        try {
            const result = await apiFetch('/api/v1/profile/me', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (result.status !== 'success') throw new Error(errorMessage(result, t('prof.saveError')));
            apply(result.data);
            setGenderPersona(result.data.gender);
            setSavedCount((n) => n + 1);
            setMessage(result.data.complete ? t('prof.savedComplete') : t('prof.savedIncomplete'));
        } catch (e) {
            setError(e.message || t('prof.saveError'));
        } finally {
            setSaving(false);
        }
    };

    const cancel = () => {
        setForm(saved);
        setError('');
        setEditing(false);
    };


    return (
        <>
            <h1 className="page-title">{t('prof.title')}</h1>
            <p className="page-lead">{t('prof.lead')}</p>

            {loading ? <p className="muted">{t('prof.loading')}</p> : !editing ? (
                <dl className="prof-sheet">
                    <Fact label={t('prof.goal')} value={tv('goal', form.goal)} />
                    <Fact label={t('prof.level')} value={tv('level', form.level)} />
                    <Fact label={t('prof.age')} value={form.age === '' ? '' : number(form.age)} />
                    <Fact label={t('prof.height')} value={height(form.heightCm)} />
                    <Fact label={t('prof.weight')} value={weight(form.weightKg)} />
                    <Fact label={t('ov.bmi')} value={bmi != null ? number(bmi, 1) : ''} />
                    <Fact label={t('prof.gender')} value={tv('gender', form.gender)} />
                </dl>
            ) : (
                <form id="profile-form" className="prof-form" onSubmit={save}>
                    <Field label={t('prof.goal')}>
                        <select className="input" name="goal" value={form.goal} onChange={change}>
                            <option value="">{t('prof.chooseGoal')}</option>
                            {GOALS.map((g) => <option key={g} value={g}>{tv('goal', g)}</option>)}
                        </select>
                    </Field>
                    <Field label={t('prof.fitnessLevel')}>
                        <select className="input" name="level" value={form.level} onChange={change}>
                            <option value="">{t('prof.chooseLevel')}</option>
                            {LEVELS.map((l) => <option key={l} value={l}>{tv('level', l)}</option>)}
                        </select>
                    </Field>
                    <Field label={t('prof.age')}>
                        <input className="input" name="age" type="number" min="10" max="100" inputMode="numeric" value={form.age} onChange={change} />
                    </Field>
                    <Field label={t('prof.gender')}>
                        <select className="input" name="gender" value={form.gender} onChange={change}>
                            <option value="">{t('prof.choose')}</option>
                            {GENDERS.map((g) => <option key={g} value={g}>{tv('gender', g)}</option>)}
                        </select>
                    </Field>
                    {imperial ? (
                        <fieldset className="field prof-ftin">
                            <legend className="field-label">{t('prof.heightCm', { unit: `${t('unit.ft')} / ${t('unit.in')}` })}</legend>
                            <div className="prof-ftin-row">
                                <label><input className="input" type="number" min="2" max="8" inputMode="numeric" value={disp.ft}
                                              onChange={(e) => changeFtIn(e.target.value, disp.inch)} aria-label={t('unit.ft')} /> <span>{t('unit.ft')}</span></label>
                                <label><input className="input" type="number" min="0" max="11" inputMode="numeric" value={disp.inch}
                                              onChange={(e) => changeFtIn(disp.ft, e.target.value)} aria-label={t('unit.in')} /> <span>{t('unit.in')}</span></label>
                            </div>
                            <span className="field-hint">{t('prof.optionalBmi')}</span>
                        </fieldset>
                    ) : (
                        <Field label={t('prof.heightCm', { unit: t('unit.cm') })} hint={t('prof.optionalBmi')}>
                            <input className="input" type="number" min="80" max="250" step="0.1" inputMode="decimal" value={disp.height} onChange={(e) => changeHeight(e.target.value)} />
                        </Field>
                    )}
                    <Field label={t('prof.weightKg', { unit: weightUnit })} hint={t('prof.optionalBmi')}>
                        <input className="input" type="number" min={imperial ? 56 : 25} max={imperial ? 770 : 350} step="0.1" inputMode="decimal"
                               value={disp.weight} onChange={(e) => changeWeight(e.target.value)} />
                    </Field>
                </form>
            )}

            {(error || message) && (
                <div className="prof-feedback">
                    {error && <p className="notice notice-error" role="alert">{error}</p>}
                    {message && <p className="notice notice-success" role="status">{message}</p>}
                </div>
            )}

            {!loading && (
                <div className="prof-actions">
                    {!editing ? (
                        <button key="edit" type="button" className="btn btn-primary" onClick={() => { setMessage(''); setEditing(true); }}>
                            {t('prof.edit')}
                        </button>
                    ) : (
                        <>
                            <button key="save" type="submit" form="profile-form" className="btn btn-primary" disabled={saving}>
                                {saving ? t('prof.saving') : t('prof.save')}
                            </button>
                            {complete && <button type="button" className="btn" onClick={cancel}>{t('prof.cancel')}</button>}
                        </>
                    )}
                </div>
            )}

            {!loading && complete && (
                <div className="prof-more">
                    <HealthNotes />
                    <ProfileHistory refreshKey={savedCount} />
                    <DataExport />
                </div>
            )}
        </>
    );
}

function Field({ label, hint, children }) {
    return (
        <label className="field">
            <span className="field-label">{label}</span>
            {children}
            {hint && <span className="field-hint">{hint}</span>}
        </label>
    );
}

function Fact({ label, value }) {
    return (
        <div className="prof-fact">
            <dt className="prof-fact-label">{label}</dt>
            <dd className="prof-fact-value">{value || '–'}</dd>
        </div>
    );
}

export default Profile;
