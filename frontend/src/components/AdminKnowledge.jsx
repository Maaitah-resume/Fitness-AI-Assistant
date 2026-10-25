import React, { useEffect, useRef, useState } from 'react';
import { FileText, Trash2, Upload } from 'lucide-react';
import { apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';

/** Shared PDFs indexed for every user's coach (file-service "shared" uploads). */
function AdminKnowledge() {
    const { t, number, date } = useI18n();
    const [docs, setDocs] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [file, setFile] = useState(null);
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState(null);
    const inputRef = useRef(null);

    const load = () => apiFetch('/api/v1/files/admin/all')
        .then((r) => {
            if (r.status !== 'success') throw new Error(errorMessage(r, t('kb.loadError')));
            setDocs((r.data.files || []).filter((f) => f.shared && f.type === 'upload')
                .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
        })
        .catch((e) => setLoadError(e.message || t('kb.loadError')));

    useEffect(() => { load(); }, []);

    const upload = async (e) => {
        e.preventDefault();
        if (!file) return;
        setBusy(true);
        setStatus(null);
        const form = new FormData();
        form.append('file', file);
        try {
            const r = await apiFetch('/api/v1/files/upload?shared=true', { method: 'POST', body: form });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('kb.uploadError')));
            setStatus(r.data.status === 'indexed'
                ? { ok: true, text: t('kb.uploaded', { name: r.data.filename }) }
                : { ok: false, text: r.message });
            setFile(null);
            if (inputRef.current) inputRef.current.value = '';
            load();
        } catch (err) {
            setStatus({ ok: false, text: err.message || t('kb.uploadError') });
        } finally {
            setBusy(false);
        }
    };

    const remove = async (doc) => {
        if (!window.confirm(t('kb.deleteConfirm', { name: doc.filename }))) return;
        const r = await apiFetch(`/api/v1/files/${doc.id}`, { method: 'DELETE' }).catch(() => null);
        if (r?.status === 'success') load();
        else alert(errorMessage(r, t('kb.deleteError')));
    };

    const statusText = (d) => (d.status === 'indexed' ? t('kb.status.indexed', { n: number(d.chunkCount ?? 0) }) : t(`kb.status.${d.status}`));

    return (
        <>
            <p className="page-lead">{t('kb.lead')}</p>

            <form className="set-section set-form kb-upload" onSubmit={upload}>
                <h2 className="set-title">{t('kb.upload')}</h2>
                <p className="set-help">{t('kb.uploadHelp')}</p>
                <div className="prog-form-foot">
                    <input ref={inputRef} id="kb-file" type="file" accept=".pdf,application/pdf" className="visually-hidden"
                           onChange={(e) => { setFile(e.target.files[0] || null); setStatus(null); }} />
                    <label htmlFor="kb-file" className="btn"><FileText size={16} aria-hidden="true" /> {t('kb.choose')}</label>
                    {file && <span className="kb-file" dir="auto">{file.name}</span>}
                    <button type="submit" className="btn btn-primary" disabled={!file || busy}>
                        <Upload size={16} aria-hidden="true" /> {busy ? t('kb.uploading') : t('kb.uploadBtn')}
                    </button>
                </div>
                {status && <p className={status.ok ? 'prog-ok' : 'prog-err'} role={status.ok ? 'status' : 'alert'}>{status.text}</p>}
            </form>

            <h2 className="section-title">{t('kb.docs')}</h2>
            {loadError && <p className="notice notice-error" role="alert">{loadError}</p>}
            {docs === null && !loadError && <p className="muted">{t('kb.loading')}</p>}
            {docs?.length === 0 && <p className="muted">{t('kb.none')}</p>}
            {docs?.length > 0 && (
                <ul className="plans" role="list">
                    {docs.map((d) => (
                        <li key={d.id} className="plan">
                            <span className="plan-mark kb-mark" aria-hidden="true"><FileText size={22} /></span>
                            <div className="plan-text">
                                <div className="plan-name" dir="auto">{d.filename}</div>
                                <div className="plan-detail">
                                    {t('kb.added', { date: date(d.createdAt) })}, <span className={d.status === 'indexed' ? '' : 'prog-err'}>{statusText(d)}</span>
                                    {d.error && <> ({d.error})</>}
                                </div>
                            </div>
                            <div className="plan-actions">
                                <button type="button" className="btn btn-danger btn-small" onClick={() => remove(d)}>
                                    <Trash2 size={15} aria-hidden="true" /> {t('kb.delete')}
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </>
    );
}

export default AdminKnowledge;
