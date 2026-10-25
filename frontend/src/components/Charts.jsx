import React, { useState } from 'react';
import { useI18n } from '../i18n';

// Every chart here is single-series, so one hue (raspberry #C8285A, over 4.5:1 on the
// paper surface) carries all marks. The chart title names the series, so no legend;
// values and labels use text colours, never the series colour. Styles: index.css (.viz-*).
// Plots always run left-to-right (dir="ltr"), so time reads the same way in Arabic.

export function StatTile({ label, value, hint }) {
    return (
        <div className="viz-stat">
            <span className="viz-stat-value">{value ?? '–'}</span>
            <span className="viz-stat-label">{label}</span>
            {hint && <span className="viz-stat-hint">{hint}</span>}
        </div>
    );
}

/** Per-day counts ({"2026-10-01": 3, ...}) as vertical bars with a hover tooltip. */
export function DailyBars({ title, data, unit = '' }) {
    const { t, number, shortDate } = useI18n();
    const [hover, setHover] = useState(null);
    const entries = Object.entries(data || {});
    const max = Math.max(1, ...entries.map(([, v]) => v));
    const total = entries.reduce((s, [, v]) => s + v, 0);

    return (
        <figure className="viz-card">
            <figcaption className="viz-title">
                {title}
                <span className="viz-subtitle">{t('chart.inLast', { total: number(total), days: number(entries.length) })}</span>
            </figcaption>
            {entries.length === 0 ? (
                <p className="viz-empty">{t('chart.noData')}</p>
            ) : (
                <>
                    <div className="viz-plot" dir="ltr" role="img"
                         aria-label={`${title}: ${number(total)}, ${shortDate(entries[0][0])} – ${shortDate(entries[entries.length - 1][0])}`}>
                        <span className="viz-max">{number(max)}</span>
                        <div className="viz-bars" onMouseLeave={() => setHover(null)}>
                            {entries.map(([day, v], i) => (
                                <div key={day} className="viz-col" onMouseEnter={() => setHover(i)}>
                                    {v > 0 && <div className="viz-bar" style={{ height: `${(v / max) * 100}%` }} />}
                                    {hover === i && (
                                        <div className={`viz-tip ${i > entries.length * 0.7 ? 'left' : ''}`}>
                                            <strong>{number(v)}{unit}</strong> {shortDate(day)}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                        <div className="viz-xaxis">
                            <span>{shortDate(entries[0][0])}</span>
                            <span>{shortDate(entries[entries.length - 1][0])}</span>
                        </div>
                    </div>
                    <DataTable rows={entries.map(([d, v]) => [shortDate(d), number(v)])} head={[t('chart.day'), t('chart.count')]} />
                </>
            )}
        </figure>
    );
}

/** Category counts ({"fat_loss": 4, ...}) as labelled horizontal bars, largest first. */
export function DistributionBars({ title, data, labelFor = (k) => k.replace(/_/g, ' ') }) {
    const { number } = useI18n();
    const [hover, setHover] = useState(null);
    const entries = Object.entries(data || {}).sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    const total = entries.reduce((s, [, v]) => s + v, 0);

    return (
        <figure className="viz-card">
            <figcaption className="viz-title">{title}</figcaption>
            {entries.length === 0 ? (
                <p className="viz-empty">–</p>
            ) : (
                <div className="viz-hbars" role="img"
                     aria-label={`${title}: ` + entries.map(([k, v]) => `${labelFor(k)} ${v}`).join(', ')}>
                    {entries.map(([k, v]) => (
                        <div key={k} className="viz-hrow" onMouseEnter={() => setHover(k)} onMouseLeave={() => setHover(null)}>
                            <span className="viz-hlabel">{labelFor(k)}</span>
                            <div className="viz-htrack">
                                <div className="viz-hbar" style={{ width: `${(v / max) * 100}%` }} />
                            </div>
                            <span className="viz-hvalue">
                                {hover === k ? `${number(Math.round((v / total) * 100))}%` : number(v)}
                            </span>
                        </div>
                    ))}
                </div>
            )}
        </figure>
    );
}

/**
 * Values over calendar dates ([{date: "2026-10-01", value: 60.5}, ...], oldest first) as a line with
 * a dot per reading. x is proportional to time, so gaps between sessions show as gaps. Each dot is a
 * focusable button, so the tooltip works by keyboard too.
 */
export function LineChart({ title, points, unit = '', digits = 1 }) {
    const { t, number, shortDate } = useI18n();
    const [active, setActive] = useState(null);
    if (!points || points.length === 0) return null;

    const times = points.map((p) => Date.parse(p.date + 'T00:00:00Z'));
    const t0 = times[0];
    const span = Math.max(1, times[times.length - 1] - t0);
    const values = points.map((p) => p.value);
    const lo = Math.min(...values);
    const hi = Math.max(...values);
    const pad = Math.max((hi - lo) * 0.15, 1); // never a flat, full-height line
    const min = lo - pad;
    const max = hi + pad;
    const x = (i) => (points.length === 1 ? 50 : ((times[i] - t0) / span) * 100);
    const y = (v) => ((v - min) / (max - min)) * 100;
    const fmt = (v) => `${number(v, digits)}${unit}`;

    return (
        <figure className="viz-card">
            <figcaption className="viz-title">{title}</figcaption>
            <div className="viz-plot" dir="ltr" role="img"
                 aria-label={`${title}: ${fmt(values[0])} (${shortDate(points[0].date)}) – ${fmt(values[values.length - 1])} (${shortDate(points[points.length - 1].date)})`}>
                <span className="viz-max">{number(hi, digits)}</span>
                <span className="viz-min">{number(lo, digits)}</span>
                <div className="viz-line-area" onMouseLeave={() => setActive(null)}>
                    <svg className="viz-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                        <line x1="0" x2="100" y1={100 - y(hi)} y2={100 - y(hi)} className="viz-guide" vectorEffect="non-scaling-stroke" />
                        <line x1="0" x2="100" y1={100 - y(lo)} y2={100 - y(lo)} className="viz-guide" vectorEffect="non-scaling-stroke" />
                        <polyline points={points.map((p, i) => `${x(i)},${100 - y(p.value)}`).join(' ')}
                                  vectorEffect="non-scaling-stroke" />
                    </svg>
                    {points.map((p, i) => (
                        <button key={p.date + i} type="button" className={`viz-dot${active === i ? ' on' : ''}`}
                                style={{ left: `${x(i)}%`, bottom: `${y(p.value)}%` }}
                                onMouseEnter={() => setActive(i)} onFocus={() => setActive(i)} onBlur={() => setActive(null)}
                                aria-label={`${fmt(p.value)}, ${shortDate(p.date)}`}>
                            {active === i && (
                                <span className={`viz-tip ${x(i) > 70 ? 'left' : ''} ${x(i) < 15 ? 'right' : ''}`}>
                                    <strong>{fmt(p.value)}</strong> {shortDate(p.date)}
                                </span>
                            )}
                        </button>
                    ))}
                </div>
                <div className="viz-xaxis">
                    <span>{shortDate(points[0].date)}</span>
                    {points.length > 1 && <span>{shortDate(points[points.length - 1].date)}</span>}
                </div>
            </div>
            <DataTable rows={points.map((p) => [shortDate(p.date), fmt(p.value)])} head={[t('chart.date'), t('chart.value')]} />
        </figure>
    );
}

function DataTable({ head, rows }) {
    const { t } = useI18n();
    return (
        <details className="viz-table">
            <summary>{t('chart.viewTable')}</summary>
            <table>
                <thead><tr>{head.map((h) => <th key={h}>{h}</th>)}</tr></thead>
                <tbody>{rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody>
            </table>
        </details>
    );
}
