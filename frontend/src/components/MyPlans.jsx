import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, Eye, FileText, X } from 'lucide-react';
import { apiDownload, apiFetch, apiObjectUrl, errorMessage } from '../api';
import { useI18n } from '../i18n';

// Workout and nutrition plans GymAI generated as PDFs in chat; uploads are excluded.
function MyPlans() {
    const { t, number, date } = useI18n();
    const [files, setFiles] = useState(null);
    const [error, setError] = useState('');
    const [preview, setPreview] = useState(null); // { file, url }
    const [busyId, setBusyId] = useState(null);
    const closeRef = useRef(null);

    useEffect(() => {
        // Index any older plans first so their status is current; a failure here shouldn't block the list.
        apiFetch('/api/v1/files/generated/index-pending', { method: 'POST' })
            .catch(() => {})
            .then(() => apiFetch('/api/v1/files'))
            .then((result) => {
                if (result.status !== 'success') throw new Error(errorMessage(result, t('plans.loadError')));
                const generated = (result.data?.files || []).filter((f) => f.type === 'generated');
                generated.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                setFiles(generated);
            })
            .catch((e) => {
                setError(e.message || t('plans.loadError'));
                setFiles([]);
            });
    }, []);

    // Free the blob URL when the preview closes or the page unmounts.
    useEffect(() => () => preview && URL.revokeObjectURL(preview.url), [preview]);

    // Viewer: focus the close button on open, close on Escape.
    useEffect(() => {
        if (!preview) return;
        closeRef.current?.focus();
        const onKey = (e) => e.key === 'Escape' && setPreview(null);
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [preview]);

    const openPreview = async (file) => {
        setBusyId(file.id);
        setError('');
        try {
            setPreview({ file, url: await apiObjectUrl(file.downloadUrl) });
        } catch (e) {
            setError(e.message);
        } finally {
            setBusyId(null);
        }
    };

    const download = async (file) => {
        setError('');
        try {
            await apiDownload(file.downloadUrl, file.filename);
        } catch (e) {
            setError(e.message);
        }
    };

    const size = (bytes) => (bytes < 1024 * 1024
        ? `${number(Math.max(1, Math.round(bytes / 1024)))} KB`
        : `${number(bytes / 1024 / 1024, 1)} MB`);

    return (
        <>
            <h1 className="page-title">{t('plans.title')}</h1>
            <p className="page-lead">{t('plans.lead')}</p>
            {error && <p className="notice notice-error" role="alert">{error}</p>}

            {files === null ? <p className="muted">{t('plans.loading')}</p>
                : files.length === 0 ? (
                    <div className="plans-empty">
                        <p>{t('plans.empty')}</p>
                        <Link to="/chat" className="btn btn-primary">{t('ov.askCoach')}</Link>
                    </div>
                ) : (
                    <ul className="plans" role="list">
                        {files.map((f) => (
                            <li key={f.id} className="plan">
                                <span className="plan-mark" aria-hidden="true"><FileText size={22} /></span>
                                <div className="plan-text">
                                    <div className="plan-name" dir="auto">{f.filename.replace(/\.pdf$/i, '')}</div>
                                    <div className="plan-detail">{t('plans.made', { date: date(f.createdAt), size: size(f.sizeBytes) })}</div>
                                </div>
                                <div className="plan-actions">
                                    <button type="button" className="btn btn-small" onClick={() => openPreview(f)} disabled={busyId === f.id}>
                                        <Eye size={15} aria-hidden="true" /> {busyId === f.id ? t('plans.opening') : t('plans.preview')}
                                    </button>
                                    <button type="button" className="btn btn-primary btn-small" onClick={() => download(f)}>
                                        <Download size={15} aria-hidden="true" /> {t('plans.download')}
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}

            {preview && (
                <div className="viewer" onClick={() => setPreview(null)}>
                    <div className="viewer-box" role="dialog" aria-modal="true" aria-label={preview.file.filename}
                         onClick={(e) => e.stopPropagation()}>
                        <div className="viewer-head">
                            <span className="viewer-title" dir="auto">{preview.file.filename}</span>
                            <button type="button" className="btn btn-primary btn-small" onClick={() => download(preview.file)}>
                                <Download size={15} aria-hidden="true" /> {t('plans.download')}
                            </button>
                            <button ref={closeRef} type="button" className="icon-btn" onClick={() => setPreview(null)} aria-label={t('plans.close')}>
                                <X size={20} aria-hidden="true" />
                            </button>
                        </div>
                        <iframe className="viewer-frame" title={preview.file.filename} src={preview.url} />
                    </div>
                </div>
            )}
        </>
    );
}

export default MyPlans;
