import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, FileText, RefreshCw, Sparkles } from 'lucide-react';
import { apiDownload, apiFetch, errorMessage } from '../api';
import { useI18n } from '../i18n';

/**
 * Last week's report. Asking is idempotent: the first visit after Monday makes the coach write it
 * (and saves the PDF to Plans); later visits get the stored one.
 */
function WeeklyReportCard() {
    const { t, lang, date } = useI18n();
    const [state, setState] = useState({ status: 'loading' });
    const asked = useRef(false);

    const request = async (force = false) => {
        setState((s) => ({ ...s, status: force ? 'regenerating' : 'loading' }));
        try {
            const r = await apiFetch(`/api/v1/chat/reports/weekly?language=${lang}${force ? '&force=true' : ''}`, { method: 'POST' });
            if (r.status !== 'success') throw new Error(errorMessage(r, t('wr.error')));
            setState({ status: r.data.status, report: r.data });
        } catch (e) {
            setState({ status: 'error', error: e.message || t('wr.error') });
        }
    };

    useEffect(() => {
        if (asked.current) return; // StrictMode mounts twice in development
        asked.current = true;
        request();
    }, []);

    const r = state.report;
    const range = r ? `${date(r.weekStart + 'T00:00:00', { day: 'numeric', month: 'short' })} – ${date(r.weekEnd + 'T00:00:00', { day: 'numeric', month: 'short' })}` : '';

    return (
        <section className="wr-card" aria-live="polite">
            <div className="wr-head">
                <FileText size={22} aria-hidden="true" className="wr-icon" />
                <div>
                    <h2 className="wr-title">{t('wr.title')}</h2>
                    {range && <p className="wr-range">{range}</p>}
                </div>
            </div>

            {state.status === 'loading' && <p className="muted">{t('wr.checking')}</p>}
            {state.status === 'regenerating' && <p className="muted"><Sparkles size={15} aria-hidden="true" /> {t('wr.writing')}</p>}
            {state.status === 'error' && <p className="notice notice-error" role="alert">{state.error}</p>}
            {state.status === 'no_data' && <p className="wr-summary">{t('wr.noData')}</p>}
            {state.status === 'ready' && (
                <>
                    <p className="wr-summary" dir="auto">{r.summary}</p>
                    {r.aiGenerated === false && <p className="field-hint">{t('wr.dataOnly')}</p>}
                    <div className="wr-actions">
                        <button type="button" className="btn btn-primary btn-small" onClick={() => apiDownload(r.downloadUrl, r.filename).catch(() => {})}>
                            <Download size={15} aria-hidden="true" /> {t('wr.download')}
                        </button>
                        <Link to="/plans" className="btn btn-small">{t('wr.inPlans')}</Link>
                        <button type="button" className="btn btn-quiet btn-small" onClick={() => request(true)}>
                            <RefreshCw size={14} aria-hidden="true" /> {t('wr.regenerate')}
                        </button>
                    </div>
                </>
            )}
        </section>
    );
}

export default WeeklyReportCard;
