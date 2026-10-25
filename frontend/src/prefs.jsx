import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiFetch, errorMessage, getToken } from './api';
import { useI18n } from './i18n';

// User settings. Units, coach style and training preferences live in profile-service (the coach reads
// them); theme is per device. Weights and heights are always stored in kg and cm: these helpers only
// convert for display and convert form input back.
const KG_PER_LB = 0.45359237;
const CM_PER_IN = 2.54;
const DEFAULTS = { units: 'metric', answerLength: 'short', tone: 'friendly', trainingDays: null, equipment: null, injuries: null };

const read = (key, fallback) => { try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; } };
const write = (key, value) => { try { localStorage.setItem(key, value); } catch { /* storage blocked */ } };

const PrefsContext = createContext(null);

/** Appearance (per device): theme style, accent and text colours, text size. '' = the theme's own value. */
const APPEARANCE_DEFAULTS = { style: 'auto', accent: '', textColor: '', fontScale: 'md' };
const FONT_SCALES = { sm: '93.75%', md: '100%', lg: '112.5%', xl: '125%' };
const readAppearance = () => {
    try { return { ...APPEARANCE_DEFAULTS, ...JSON.parse(localStorage.getItem('appearance') || '{}') }; } catch { return APPEARANCE_DEFAULTS; }
};

/** #rrggbb -> [r, g, b] */
export const hexToRgb = (hex) => {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
    if (!m) return null;
    const n = parseInt(m[1], 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};
const shade = (hex, amount) => {
    const rgb = hexToRgb(hex);
    if (!rgb) return hex;
    return '#' + rgb.map((c) => Math.max(0, Math.min(255, Math.round(c * (1 - amount))))).map((c) => c.toString(16).padStart(2, '0')).join('');
};
/** WCAG contrast ratio between two #rrggbb colours. */
export const contrast = (a, b) => {
    const lum = (hex) => {
        const [r, g, b2] = hexToRgb(hex).map((c) => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b2;
    };
    const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
    return (x + 0.05) / (y + 0.05);
};

export function PreferencesProvider({ children }) {
    const { t, number } = useI18n();
    // cached units avoid a flash of kg for imperial users before the server answers
    const [prefs, setPrefs] = useState({ ...DEFAULTS, units: read('units', 'metric') });
    const [loadedFor, setLoadedFor] = useState(null);
    const [theme, setThemeState] = useState(() => read('theme', 'system'));
    // the colour persona follows the profile's gender: "male", "female" or "" (neutral)
    const [persona, setPersonaState] = useState(() => read('persona', ''));
    const [appearance, setAppearanceState] = useState(readAppearance);

    // the persona actually shown: the gender's, unless the user picked a theme style
    const effectivePersona = appearance.style === 'auto' ? persona : appearance.style === 'neutral' ? '' : appearance.style;

    useEffect(() => {
        const root = document.documentElement;
        if (effectivePersona) root.setAttribute('data-persona', effectivePersona);
        else root.removeAttribute('data-persona');
        write('persona', persona);
    }, [persona, effectivePersona]);

    // custom colours and size override the theme's tokens inline on <html>
    useEffect(() => {
        const st = document.documentElement.style;
        const rgb = hexToRgb(appearance.accent);
        const vars = rgb ? {
            '--plate-blue': appearance.accent, '--plate-blue-ink': shade(appearance.accent, 0.18), '--link': shade(appearance.accent, 0.12),
            '--series': appearance.accent, '--focus-ring': `0 0 0 3px rgba(${rgb.join(', ')}, 0.24)`,
        } : {};
        ['--plate-blue', '--plate-blue-ink', '--link', '--series', '--focus-ring'].forEach((k) => (vars[k] ? st.setProperty(k, vars[k]) : st.removeProperty(k)));
        if (hexToRgb(appearance.textColor)) st.setProperty('--iron', appearance.textColor); else st.removeProperty('--iron');
        st.fontSize = FONT_SCALES[appearance.fontScale] || '';
        write('appearance', JSON.stringify(appearance));
    }, [appearance]);

    const setAppearance = useCallback((patch) => setAppearanceState((a) => ({ ...a, ...patch })), []);
    const resetAppearance = useCallback(() => setAppearanceState(APPEARANCE_DEFAULTS), []);

    /** Map a profile gender to its persona (anything else, or no gender, stays neutral). */
    const setGenderPersona = useCallback((gender) => {
        setPersonaState(gender === 'male' || gender === 'female' ? gender : '');
    }, []);

    useEffect(() => {
        const root = document.documentElement;
        if (theme === 'system') root.removeAttribute('data-theme');
        else root.setAttribute('data-theme', theme);
        write('theme', theme);
    }, [theme]);

    /** Fetch once per signed-in token; AppShell calls this on mount. */
    const ensureLoaded = useCallback(() => {
        const token = getToken();
        if (!token || loadedFor === token) return;
        setLoadedFor(token);
        apiFetch('/api/v1/profile/preferences/me')
            .then((r) => { if (r.status === 'success') { setPrefs({ ...DEFAULTS, ...r.data }); write('units', r.data.units); } })
            .catch(() => {});
        apiFetch('/api/v1/profile/me')
            .then((r) => { if (r.status === 'success') setGenderPersona(r.data?.gender); })
            .catch(() => {});
    }, [loadedFor, setGenderPersona]);

    /** Save a partial change; resolves to the saved settings or throws with the server's message. */
    const update = useCallback(async (patch) => {
        const r = await apiFetch('/api/v1/profile/preferences/me', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(patch),
        });
        if (r.status !== 'success') throw new Error(errorMessage(r, t('set.saveError')));
        setPrefs({ ...DEFAULTS, ...r.data });
        write('units', r.data.units);
        return r.data;
    }, [t]);

    const value = useMemo(() => {
        const imperial = prefs.units === 'imperial';
        return {
            prefs, update, ensureLoaded, theme, setTheme: setThemeState, imperial, persona, setGenderPersona,
            appearance, setAppearance, resetAppearance, effectivePersona,
            weightUnit: imperial ? t('unit.lb') : t('unit.kg'),
            /** kg -> number in the user's unit */
            fromKg: (kg) => (imperial ? kg / KG_PER_LB : kg),
            /** number typed in the user's unit -> kg, rounded to 0.1 kg */
            toKg: (v) => Math.round((imperial ? Number(v) * KG_PER_LB : Number(v)) * 10) / 10,
            /** "61.6 kg" / "135.8 lb", at most one decimal ("60 kg", not "60.0 kg") */
            weight: (kg) => (kg == null || kg === '' ? '' : `${number(Math.round((imperial ? kg / KG_PER_LB : kg) * 10) / 10)} ${imperial ? t('unit.lb') : t('unit.kg')}`),
            /** "170 cm" / "5′ 7″" */
            height: (cm) => {
                if (cm == null || cm === '') return '';
                if (!imperial) return `${number(cm)} ${t('unit.cm')}`;
                const totalIn = Math.round(cm / CM_PER_IN);
                return `${number(Math.floor(totalIn / 12))}′ ${number(totalIn % 12)}″`;
            },
            /** cm -> { ft, in } for the imperial height inputs */
            cmToFtIn: (cm) => {
                if (cm == null || cm === '') return { ft: '', in: '' };
                const totalIn = Math.round(cm / CM_PER_IN);
                return { ft: Math.floor(totalIn / 12), in: totalIn % 12 };
            },
            ftInToCm: (ft, inches) => Math.round(((Number(ft) || 0) * 12 + (Number(inches) || 0)) * CM_PER_IN * 10) / 10,
        };
    }, [prefs, update, ensureLoaded, theme, persona, setGenderPersona, appearance, setAppearance, resetAppearance, effectivePersona, t, number]);

    return <PrefsContext.Provider value={value}>{children}</PrefsContext.Provider>;
}

export function usePrefs() {
    return useContext(PrefsContext);
}
