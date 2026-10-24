import React, { useEffect, useState } from 'react';
import { ArrowLeft, Download, Eye, FileText, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiDownload, apiFetch, apiObjectUrl, errorMessage } from '../api';

// Workout and nutrition plans GymAI generated as PDFs in chat; uploads are excluded.
function MyPlans() {
    const navigate = useNavigate();
    const [files, setFiles] = useState(null);
    const [error, setError] = useState('');
    const [preview, setPreview] = useState(null); // { file, url }
    const [busyId, setBusyId] = useState(null);

    useEffect(() => {
        // Index any older plans first so their status is current; a failure here shouldn't block the list.
        apiFetch('/api/v1/files/generated/index-pending', { method: 'POST' })
            .catch(() => {})
            .then(() => apiFetch('/api/v1/files'))
            .then((result) => {
                if (result.status !== 'success') throw new Error(errorMessage(result, 'Could not load your plans'));
                const generated = (result.data?.files || []).filter((f) => f.type === 'generated');
                generated.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                setFiles(generated);
            })
            .catch((e) => {
                setError(e.message || 'Could not load your plans');
                setFiles([]);
            });
    }, []);

    // Free the blob URL when the preview closes or the page unmounts.
    useEffect(() => () => preview && URL.revokeObjectURL(preview.url), [preview]);

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

    return (
        <div className="plans-page">
            <div className="plans-container">
                <button className="back-btn" onClick={() => navigate('/dashboard')}><ArrowLeft size={18} /> Dashboard</button>
                <h1>My Plans</h1>
                <p className="subtitle">Workout routines and nutrition guides GymAI generated for you.</p>
                {error && <p className="error">{error}</p>}

                {files === null ? <p className="status">Loading your plans...</p>
                    : files.length === 0 ? (
                        <div className="empty glass">
                            <FileText size={40} />
                            <p>No plans yet. Ask GymAI in chat for a workout or meal plan as a PDF.</p>
                            <button className="action primary" onClick={() => navigate('/chat')}>Open chat</button>
                        </div>
                    ) : (
                        <div className="plans-list">
                            {files.map((f) => (
                                <div key={f.id} className="plan glass">
                                    <FileText size={28} className="plan-icon" />
                                    <div className="plan-info">
                                        <strong>{f.filename}</strong>
                                        <span>{new Date(f.createdAt).toLocaleDateString()} · {formatSize(f.sizeBytes)}</span>
                                    </div>
                                    <button className="action" onClick={() => openPreview(f)} disabled={busyId === f.id}>
                                        <Eye size={16} /> {busyId === f.id ? 'Opening...' : 'Preview'}
                                    </button>
                                    <button className="action primary" onClick={() => download(f)}>
                                        <Download size={16} /> Download
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
            </div>

            {preview && (
                <div className="modal" onClick={() => setPreview(null)}>
                    <div className="modal-box glass" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-head">
                            <strong>{preview.file.filename}</strong>
                            <button className="action primary" onClick={() => download(preview.file)}><Download size={16} /> Download</button>
                            <button className="icon-btn" onClick={() => setPreview(null)} aria-label="Close preview"><X size={20} /></button>
                        </div>
                        <iframe title={preview.file.filename} src={preview.url} />
                    </div>
                </div>
            )}

            <style jsx>{`
                .plans-page { width: 100%; height: 100%; overflow-y: auto; padding: 2rem; }
                .plans-container { width: min(900px, 100%); margin: 0 auto; }
                .back-btn { display: flex; align-items: center; gap: .5rem; background: transparent; border: 0; color: var(--primary-light); cursor: pointer; }
                h1 { margin-top: 1.5rem; font-size: 2.5rem; }
                .subtitle, .status { color: var(--text-muted); margin: .5rem 0 2rem; }
                .error { color: #fb7185; margin-bottom: 1rem; }
                .plans-list { display: flex; flex-direction: column; gap: 1rem; }
                .plan { display: flex; align-items: center; gap: 1rem; padding: 1.25rem 1.5rem; border-radius: 20px; }
                .plan-icon { color: #10b981; flex-shrink: 0; }
                .plan-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: .25rem; }
                .plan-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .plan-info span { color: var(--text-muted); font-size: .85rem; }
                .action { display: flex; align-items: center; gap: .4rem; padding: .6rem 1rem; border-radius: 12px; border: 1px solid var(--glass-border); background: transparent; color: white; cursor: pointer; font: inherit; white-space: nowrap; }
                .action.primary { background: var(--primary); border-color: var(--primary); }
                .action:disabled { opacity: .55; cursor: wait; }
                .empty { display: flex; flex-direction: column; align-items: center; gap: 1rem; padding: 3rem; border-radius: 24px; color: var(--text-muted); text-align: center; }
                .modal { position: fixed; inset: 0; background: rgba(0,0,0,.7); display: grid; place-items: center; padding: 2rem; z-index: 50; }
                .modal-box { width: min(1000px, 100%); height: 90vh; display: flex; flex-direction: column; border-radius: 20px; overflow: hidden; }
                .modal-head { display: flex; align-items: center; gap: 1rem; padding: 1rem 1.25rem; }
                .modal-head strong { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .icon-btn { background: transparent; border: 0; color: white; cursor: pointer; }
                iframe { flex: 1; width: 100%; border: 0; background: white; }
                @media (max-width: 600px) { .plan { flex-wrap: wrap; } .plan-info { flex-basis: calc(100% - 3rem); } }
            `}</style>
        </div>
    );
}

function formatSize(bytes) {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default MyPlans;
