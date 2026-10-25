import { useState } from 'react';

/** A collapsed/expanded flag remembered in localStorage (storage may be unavailable). */
export function usePersistentToggle(key) {
    const [on, setOn] = useState(() => {
        try { return localStorage.getItem(key) === '1'; } catch { return false; }
    });
    const toggle = () => setOn((v) => {
        try { localStorage.setItem(key, v ? '0' : '1'); } catch { /* storage blocked */ }
        return !v;
    });
    return [on, toggle];
}
