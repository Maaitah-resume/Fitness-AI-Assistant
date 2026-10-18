import React, { useState } from 'react';

// Every chart here is single-series, so one validated hue (#6366f1, the app's
// --primary: passes lightness/chroma/contrast on the dark card surface) carries
// all marks. The chart title names the series, so no legend; values and labels
// use text colors, never the series color.

const nf = new Intl.NumberFormat();

export function StatTile({ label, value, hint }) {
    return (
        <div className="viz-stat glass">
            <span className="viz-stat-label">{label}</span>
            <span className="viz-stat-value">{value ?? '–'}</span>
            {hint && <span className="viz-stat-hint">{hint}</span>}
        </div>
    );
}

function shortDate(iso) {
    const d = new Date(iso + 'T00:00:00Z');
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/** Per-day counts ({"2026-10-01": 3, ...}) as vertical bars with a hover tooltip. */
export function DailyBars({ title, data, unit = '' }) {
    const [hover, setHover] = useState(null);
    const entries = Object.entries(data || {});
    const max = Math.max(1, ...entries.map(([, v]) => v));
    const total = entries.reduce((s, [, v]) => s + v, 0);

    return (
        <figure className="viz-card glass">
            <figcaption className="viz-title">
                {title}
                <span className="viz-subtitle">{nf.format(total)} in the last {entries.length} days</span>
            </figcaption>
            {entries.length === 0 ? (
                <p className="viz-empty">No data yet.</p>
            ) : (
                <>
                    <div className="viz-plot" role="img"
                         aria-label={`${title}: ${nf.format(total)} over ${entries.length} days, peak ${nf.format(max)}`}>
                        <span className="viz-max">{nf.format(max)}</span>
                        <div className="viz-bars" onMouseLeave={() => setHover(null)}>
                            {entries.map(([day, v], i) => (
                                <div key={day} className="viz-col" onMouseEnter={() => setHover(i)}>
                                    {v > 0 && <div className="viz-bar" style={{ height: `${(v / max) * 100}%` }} />}
                                    {hover === i && (
                                        <div className={`viz-tip ${i > entries.length * 0.7 ? 'left' : ''}`}>
                                            <strong>{nf.format(v)}{unit}</strong> {shortDate(day)}
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
                    <DataTable rows={entries.map(([d, v]) => [shortDate(d), nf.format(v)])} head={['Day', 'Count']} />
                </>
            )}
            <ChartStyles />
        </figure>
    );
}

/** Category counts ({"fat_loss": 4, ...}) as labelled horizontal bars, largest first. */
export function DistributionBars({ title, data }) {
    const [hover, setHover] = useState(null);
    const entries = Object.entries(data || {}).sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    const total = entries.reduce((s, [, v]) => s + v, 0);
    const label = (k) => k.replace(/_/g, ' ');

    return (
        <figure className="viz-card glass">
            <figcaption className="viz-title">{title}</figcaption>
            {entries.length === 0 ? (
                <p className="viz-empty">No data yet.</p>
            ) : (
                <div className="viz-hbars" role="img"
                     aria-label={`${title}: ` + entries.map(([k, v]) => `${label(k)} ${v}`).join(', ')}>
                    {entries.map(([k, v]) => (
                        <div key={k} className="viz-hrow" onMouseEnter={() => setHover(k)} onMouseLeave={() => setHover(null)}>
                            <span className="viz-hlabel">{label(k)}</span>
                            <div className="viz-htrack">
                                <div className="viz-hbar" style={{ width: `${(v / max) * 100}%` }} />
                            </div>
                            <span className="viz-hvalue">
                                {hover === k ? `${Math.round((v / total) * 100)}%` : nf.format(v)}
                            </span>
                        </div>
                    ))}
                </div>
            )}
            <ChartStyles />
        </figure>
    );
}

function DataTable({ head, rows }) {
    return (
        <details className="viz-table">
            <summary>View as table</summary>
            <table>
                <thead><tr>{head.map((h) => <th key={h}>{h}</th>)}</tr></thead>
                <tbody>{rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody>
            </table>
        </details>
    );
}

function ChartStyles() {
    return (
        <style jsx>{`
          .viz-card, .viz-stat {
            --viz-series: #6366f1;
            --viz-grid: rgba(148, 163, 184, 0.18);
          }
          .viz-stat {
            display: flex; flex-direction: column; gap: 0.3rem;
            padding: 1.1rem 1.3rem; border-radius: 18px; min-width: 0;
          }
          .viz-stat-label { font-size: 0.78rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
          .viz-stat-value { font-size: 1.8rem; font-weight: 800; color: var(--text-white); font-variant-numeric: tabular-nums; }
          .viz-stat-hint { font-size: 0.8rem; color: var(--text-muted); }

          .viz-card { margin: 0; padding: 1.3rem 1.4rem; border-radius: 20px; min-width: 0; }
          .viz-title { font-weight: 700; color: var(--text-white); margin-bottom: 0.6rem; display: flex;
                       justify-content: space-between; align-items: baseline; gap: 1rem; flex-wrap: wrap; }
          .viz-subtitle { font-weight: 400; font-size: 0.8rem; color: var(--text-muted); }
          .viz-empty { color: var(--text-muted); font-size: 0.9rem; }

          /* top margin is the tooltip's lane, so it never covers the title */
          .viz-plot { position: relative; padding-left: 2rem; margin-top: 1.9rem; }
          .viz-max { position: absolute; left: 0; top: -0.4rem; font-size: 0.7rem; color: var(--text-muted);
                     font-variant-numeric: tabular-nums; }
          .viz-bars { height: 140px; display: flex; align-items: flex-end; gap: 2px;
                      border-bottom: 1px solid var(--viz-grid);
                      background: linear-gradient(to bottom, var(--viz-grid) 1px, transparent 1px) top / 100% 100% no-repeat; }
          /* the column is the hit target: full height, so short bars are still easy to hover */
          .viz-col { position: relative; flex: 1; height: 100%; display: flex; align-items: flex-end; cursor: default; }
          .viz-col:hover { background: rgba(255, 255, 255, 0.04); }
          .viz-bar { width: 100%; min-height: 2px; background: var(--viz-series); border-radius: 4px 4px 0 0; }
          .viz-tip { position: absolute; top: -1.8rem; left: 50%; transform: translateX(-50%);
                     white-space: nowrap; background: #0f172a; border: 1px solid var(--glass-border);
                     color: var(--text-muted); font-size: 0.75rem; padding: 0.3rem 0.55rem; border-radius: 8px;
                     pointer-events: none; z-index: 2; }
          .viz-tip strong { color: var(--text-white); margin-right: 0.25rem; }
          .viz-tip.left { left: auto; right: 0; transform: none; }
          .viz-xaxis { display: flex; justify-content: space-between; font-size: 0.7rem; color: var(--text-muted);
                       margin-top: 0.4rem; }

          .viz-hbars { display: flex; flex-direction: column; gap: 0.55rem; }
          .viz-hrow { display: grid; grid-template-columns: minmax(80px, 30%) 1fr 3rem; align-items: center; gap: 0.75rem; }
          .viz-hlabel { font-size: 0.85rem; color: var(--text-muted); text-transform: capitalize;
                        overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .viz-htrack { height: 12px; }
          .viz-hbar { height: 100%; min-width: 2px; background: var(--viz-series); border-radius: 0 4px 4px 0; }
          .viz-hrow:hover .viz-hbar { filter: brightness(1.2); }
          .viz-hvalue { font-size: 0.85rem; color: var(--text-white); text-align: right; font-variant-numeric: tabular-nums; }

          .viz-table { margin-top: 0.9rem; font-size: 0.8rem; color: var(--text-muted); }
          .viz-table summary { cursor: pointer; }
          .viz-table table { margin-top: 0.5rem; border-collapse: collapse; width: 100%; max-height: 200px; }
          .viz-table th, .viz-table td { text-align: left; padding: 0.2rem 0.5rem; border-bottom: 1px solid var(--viz-grid); }
        `}</style>
    );
}
