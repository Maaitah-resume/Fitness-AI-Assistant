import React, { useEffect, useState } from 'react';
import { ArrowLeft, Pencil, Save } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiFetch, errorMessage } from '../api';

const empty = { age: '', gender: '', heightCm: '', weightKg: '', goal: '', level: '' };

function Profile() {
    const navigate = useNavigate();
    const [form, setForm] = useState(empty);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    // A complete profile opens as a read-only summary; the form shows on first visit or after "Edit".
    const [editing, setEditing] = useState(false);
    const [bmi, setBmi] = useState(null);

    useEffect(() => {
        apiFetch('/api/v1/profile/me')
            .then((result) => {
                if (result.status !== 'success') throw new Error(errorMessage(result, 'Could not load profile'));
                const profile = result.data || {};
                setForm(Object.fromEntries(Object.keys(empty).map((key) => [key, profile[key] ?? ''])));
                setBmi(profile.bmi ?? null);
                setEditing(!profile.complete);
            })
            .catch((e) => setError(e.message || 'Could not load profile'))
            .finally(() => setLoading(false));
    }, []);

    const change = (e) => setForm((old) => ({ ...old, [e.target.name]: e.target.value }));

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
            if (result.status !== 'success') throw new Error(errorMessage(result, 'Could not save profile'));
            const profile = result.data;
            setForm(Object.fromEntries(Object.keys(empty).map((key) => [key, profile[key] ?? ''])));
            setBmi(profile.bmi ?? null);
            setEditing(!profile.complete);
            setMessage(`Profile saved${profile.bmi ? ` - BMI ${Number(profile.bmi).toFixed(1)}`: ''}`);
        } catch (e) {
            setError(e.message || 'Could not save profile');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="profile-page">
            <form className="profile-card glass" onSubmit={save}>
                <button type="button" className="back-btn" onClick={() => navigate('/dashboard')}><ArrowLeft size={18} /> Dashboard</button>
                <h1>My Profile</h1>
                <p className="subtitle">Personalize your workouts and nutrition guidance.</p>
                {loading ? <p className="status">Loading profile...</p> : !editing ? <div className="profile-grid">
                    <Info label="Age" value={form.age} />
                    <Info label="Gender" value={pretty(form.gender)} />
                    <Info label="Height" value={form.heightCm && `${form.heightCm} cm`} />
                    <Info label="Weight" value={form.weightKg && `${form.weightKg} kg`} />
                    <Info label="Goal" value={pretty(form.goal)} />
                    <Info label="Fitness level" value={pretty(form.level)} />
                    {bmi != null && <Info label="BMI" value={Number(bmi).toFixed(1)} />}
                </div> : <div className="profile-grid">
                    <Field label="Age"><input name="age" type="number" min="10" max="100" value={form.age} onChange={change} /></Field>
                    <Field label="Gender"><select name="gender" value={form.gender} onChange={change}><option value="">Select</option><option value="male">Male</option><option value="female">Female</option><option value="other">Other</option></select></Field>
                    <Field label="Height (cm)"><input name="heightCm" type="number" min="80" max="250" step="0.1" value={form.heightCm} onChange={change} /></Field>
                    <Field label="Weight (kg)"><input name="weightKg" type="number" min="25" max="350" step="0.1" value={form.weightKg} onChange={change} /></Field>
                    <Field label="Goal"><select name="goal" value={form.goal} onChange={change}><option value="">Select</option><option value="muscle_gain">Muscle gain</option><option value="fat_loss">Fat loss</option><option value="endurance">Endurance</option><option value="general_fitness">General fitness</option></select></Field>
                    <Field label="Fitness level"><select name="level" value={form.level} onChange={change}><option value="">Select</option><option value="beginner">Beginner</option><option value="intermediate">Intermediate</option><option value="advanced">Advanced</option></select></Field>
                </div>}
                {error && <p className="feedback error">{error}</p>}
                {message && <p className="feedback success">{message}</p>}
                {!loading && !editing
                    ? <button key="edit" className="save-btn" type="button" onClick={() => { setMessage(''); setEditing(true); }}><Pencil size={18} /> Edit profile</button>
                    : <button key="save" className="save-btn" type="submit" disabled={loading || saving}><Save size={18} /> {saving ? 'Saving...' : 'Save profile'}</button>}
            </form>
            <style jsx>{`
                .profile-page { width: 100%; height: 100%; overflow-y: auto; display: grid; place-items: center; padding: 2rem; }
                .profile-card { width: min(760px, 100%); padding: 2.5rem; border-radius: 28px; }
                h1 { margin-top: 1.5rem; font-size: 2.5rem; }
                .subtitle, .status { color: var(--text-muted); margin: .5rem 0 2rem; }
                .back-btn, .save-btn { display: flex; align-items: center; justify-content: center; gap: .5rem; border: 0; color: white; cursor: pointer; }
                .back-btn { background: transparent; color: var(--primary-light); }
                .profile-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1.25rem; }
                .field { display: flex; flex-direction: column; gap: .5rem; color: var(--text-muted); }
                input, select { width: 100%; padding: 1rem; border-radius: 14px; border: 1px solid var(--glass-border); background: #111c30; color: white; font: inherit; }
                input:focus, select:focus { outline: 2px solid var(--primary); }
                .save-btn { width: 100%; margin-top: 1.5rem; padding: 1rem; border-radius: 14px; background: var(--primary); font-weight: 700; font-size: 1rem; }
                .save-btn:disabled { opacity: .55; cursor: wait; }
                .info { padding: 1rem; border-radius: 14px; border: 1px solid var(--glass-border); background: #111c30; }
                .info span { display: block; color: var(--text-muted); font-size: .85rem; margin-bottom: .35rem; }
                .info strong { color: white; font-size: 1.1rem; }
                .feedback { margin-top: 1rem; }
                .error { color: #fb7185; } .success { color: #34d399; }
                @media (max-width: 600px) { .profile-grid { grid-template-columns: 1fr; } .profile-card { padding: 1.5rem; } }
            `}</style>
        </div>
    );
}

function Field({ label, children }) {
    return <label className="field"><span>{label}</span>{children}</label>;
}

function Info({ label, value }) {
    return <div className="info"><span>{label}</span><strong>{value || '—'}</strong></div>;
}

// "muscle_gain" -> "Muscle gain"
function pretty(value) {
    return value ? value.charAt(0).toUpperCase() + value.slice(1).replace(/_/g, ' ') : '';
}

export default Profile;
