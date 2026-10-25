import React, { useEffect, useState } from 'react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';

const BASE = '/api/v1/chat/admin/prompt';
const MIN = 50;
const MAX = 8000;

/** Editor for the coach's base instructions, with version history. */
function AdminPrompt() {
    const { t, number, date } = useI18n();
    const [state, setState] = useState(null);
    const [draft, setDraft] = useState('');
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [preview, setPreview] = useState(null); // { id, text }

    const apply = (data) => { setState(data); setDraft(data.text); };

    useEffect(() => {
        apiFetch(BASE)
            .then((r) => { if (r.status !== 'success') throw new Error(errorMessage(r, t('pr.loadError'))); apply(r.data); })
            .catch((e) => setLoadError(e.message || t('pr.loadError')));
    }, []);

    const call = async (method, path, body, doneKey) => {
        setBusy(true);
        setStatus(null);
        try {
            const r = await apiFetch(BASE + path, {
                method, headers: body ? { 'Content-Type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined,
            });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('pr.saveError')));
            apply(r.data);
            setPreview(null);
            setStatus({ ok: true, text: t(doneKey) });
        } catch (e) {
            setStatus({ ok: false, text: e.message || t('pr.saveError') });
        } finally {
            setBusy(false);
        }
    };

    const togglePreview = async (id) => {
        if (preview?.id === id) { setPreview(null); return; }
        const r = await apiFetch(`${BASE}/versions/${id}`).catch(() => null);
        if (r?.status === 'success') setPreview({ id, text: r.data });
    };

    if (loadError) return <p className="notice notice-error" role="alert">{loadError}</p>;
    if (!state) return <p className="muted">{t('pr.loading')}</p>;

    const dirty = draft !== state.text;
    const tooShort = draft.trim().length < MIN;
    const actionLabel = (a) => (a?.startsWith('restore') ? `${t('pr.action.restore')} ${a.slice('restore '.length)}` : t(`pr.action.${a}`));

    return (
        <div className="pr-layout">
            <section className="pr-editor">
                <p className="page-lead">{t('pr.lead')}</p>
                <div className="pr-meta">
                    <span className={`pr-badge${state.isDefault ? '' : ' custom'}`}>{state.isDefault ? t('pr.default') : t('pr.custom')}</span>
                    {dirty && <span className="pr-unsaved">{t('pr.unsaved')}</span>}
                    <span className={`pr-count${draft.length > MAX ? ' over' : ''}`}>{t('pr.chars', { n: number(draft.length) })}</span>
                </div>
                <label htmlFor="prompt-text" className="visually-hidden">{t('pr.title')}</label>
                <textarea id="prompt-text" className="input pr-text" dir="auto" spellCheck="true" maxLength={MAX}
                          value={draft} onChange={(e) => { setDraft(e.target.value); setStatus(null); }} />
                <div className="prog-form-foot">
                    <button type="button" className="btn btn-primary" disabled={busy || !dirty || tooShort}
                            onClick={() => call('PUT', '', { text: draft }, 'pr.saved')}>
                        {busy ? t('adm.saving') : t('pr.save')}
                    </button>
                    <button type="button" className="btn" disabled={busy || !dirty} onClick={() => { setDraft(state.text); setStatus(null); }}>
                        {t('pr.discard')}
                    </button>
                    <button type="button" className="btn btn-quiet" disabled={busy || state.isDefault}
                            onClick={() => window.confirm(t('pr.resetConfirm')) && call('POST', '/reset', null, 'pr.resetDone')}>
                        {t('pr.reset')}
                    </button>
                    {dirty && tooShort && <span className="prog-err">{t('pr.tooShort')}</span>}
                    {status && <span className={status.ok ? 'prog-ok' : 'prog-err'} role={status.ok ? 'status' : 'alert'}>{status.text}</span>}
                </div>
            </section>

            <aside className="pr-side">
                <section className="set-section">
                    <h2 className="set-title">{t('pr.appended')}</h2>
                    <p className="set-help">{t('pr.appendedHelp')}</p>
                    <ul className="pr-appended">
                        {state.appended.map((a) => <li key={a}>{a}</li>)}
                    </ul>
                </section>

                <section className="set-section">
                    <h2 className="set-title">{t('pr.history')}</h2>
                    {state.history.length === 0 ? <p className="set-help">{t('pr.noHistory')}</p> : (
                        <ol className="pr-history">
                            {state.history.map((v) => (
                                <li key={v.id} className="pr-version">
                                    <div className="pr-version-head">
                                        <strong>{actionLabel(v.action)}</strong>
                                        {state.current?.id === v.id && <span className="pr-badge custom">{t('pr.inUse')}</span>}
                                    </div>
                                    <div className="pr-version-meta">
                                        {date(v.createdAt, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                                        {v.createdByName && <>, {t('pr.by', { name: v.createdByName })}</>}
                                    </div>
                                    <p className="pr-version-preview" dir="auto">{v.preview}</p>
                                    <div className="pr-version-actions">
                                        <button type="button" className="btn btn-small" onClick={() => togglePreview(v.id)} aria-expanded={preview?.id === v.id}>
                                            {preview?.id === v.id ? t('pr.hide') : t('pr.view')}
                                        </button>
                                        {state.current?.id !== v.id && (
                                            <button type="button" className="btn btn-small" disabled={busy}
                                                    onClick={() => window.confirm(t('pr.restoreConfirm')) && call('POST', `/versions/${v.id}/restore`, null, 'pr.restored')}>
                                                {t('pr.restore')}
                                            </button>
                                        )}
                                    </div>
                                    {preview?.id === v.id && <pre className="pr-version-full" dir="auto">{preview.text}</pre>}
                                </li>
                            ))}
                        </ol>
                    )}
                </section>
            </aside>
        </div>
    );
}

export default AdminPrompt;
