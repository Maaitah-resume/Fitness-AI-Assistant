// "Download my data": gathers everything the user owns across the services, as JSON or a printable PDF.
import { apiFetch, errorMessage } from './api';

export async function gatherData(fallbackError) {
    const data = async (url) => {
        const r = await apiFetch(url);
        if (r.status !== 'success') throw new Error(errorMessage(r, fallbackError));
        return r.data;
    };
    const [account, profile, preferences, history, progress, nutrition, achievements, recent, files] = await Promise.all([
        data('/api/v1/auth/me'), data('/api/v1/profile/me'), data('/api/v1/profile/preferences/me'),
        data('/api/v1/profile/me/history'), data('/api/v1/profile/progress/me'), data('/api/v1/profile/nutrition/me'),
        data('/api/v1/profile/achievements/me'), data('/api/v1/chat/recent'), data('/api/v1/files'),
    ]);
    const conversations = await Promise.all((recent.chats || []).map(async (c) => ({
        ...c, messages: (await data(`/api/v1/chat/${c.id}/messages`)).messages,
    })));
    const plans = (files.files || []).map(({ downloadUrl, ...meta }) => meta);
    return {
        exportedAt: new Date().toISOString(), account, profile, preferences, history,
        progress: { weights: progress.weights, lifts: progress.lifts },
        nutrition: nutrition.meals, achievements, conversations, files: plans,
    };
}

const stamp = () => new Date().toISOString().slice(0, 10);

export function downloadJson(exportFile) {
    const url = URL.createObjectURL(new Blob([JSON.stringify(exportFile, null, 2)], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `gymai-data-${stamp()}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const esc = (v) => String(v ?? '–').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const table = (head, rows) => rows.length === 0 ? '<p class="muted">–</p>' :
    `<table><thead><tr>${head.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${
        rows.map((r) => `<tr>${r.map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;

/**
 * Writes a print-ready report into `win` (opened synchronously on click so popup blockers allow it)
 * and opens the browser's print dialog, where "Save as PDF" produces the file.
 */
export function printReport(win, d, { t, tv, lang }) {
    const p = d.profile || {};
    const prefs = d.preferences || {};
    const h = d.history || {};
    const L = (k, vars) => t(k, vars);
    const html = `<!doctype html><html lang="${lang}" dir="${lang === 'ar' ? 'rtl' : 'ltr'}"><head><meta charset="utf-8">
<title>GymAI – ${esc(d.account?.username)} – ${stamp()}</title>
<style>
  body { font: 11pt/1.45 system-ui, "Segoe UI", Tahoma, sans-serif; color: #1d2125; margin: 24px; }
  h1 { font-size: 22pt; margin: 0 0 4px; } h2 { font-size: 13pt; margin: 22px 0 6px; border-bottom: 2px solid #1d2125; padding-bottom: 3px; }
  .muted { color: #5b6670; } table { width: 100%; border-collapse: collapse; font-size: 9.5pt; }
  th, td { text-align: start; padding: 4px 6px; border-bottom: 1px solid #d6dadd; vertical-align: top; }
  th { background: #eef0ee; } dl { display: grid; grid-template-columns: max-content 1fr; gap: 3px 14px; margin: 0; }
  dt { color: #5b6670; } dd { margin: 0; font-weight: 600; } .msg { margin: 0 0 6px; } .who { font-weight: 700; }
  .chat { break-inside: avoid-page; margin-bottom: 12px; } @page { margin: 14mm; }
</style></head><body>
<h1>GymAI</h1>
<p class="muted">${esc(L('exp.reportFor', { name: d.account?.username || '' }))} · ${esc(new Date(d.exportedAt).toLocaleString(lang))}</p>

<h2>${esc(L('prof.title'))}</h2>
<dl>
  <dt>${esc(L('prof.goal'))}</dt><dd>${esc(tv('goal', p.goal))}</dd>
  <dt>${esc(L('prof.level'))}</dt><dd>${esc(tv('level', p.level))}</dd>
  <dt>${esc(L('prof.age'))}</dt><dd>${esc(p.age)}</dd>
  <dt>${esc(L('prof.gender'))}</dt><dd>${esc(tv('gender', p.gender))}</dd>
  <dt>${esc(L('prof.height'))}</dt><dd>${esc(p.heightCm && `${p.heightCm} cm`)}</dd>
  <dt>${esc(L('prof.weight'))}</dt><dd>${esc(p.weightKg && `${p.weightKg} kg`)}</dd>
  <dt>${esc(L('ov.bmi'))}</dt><dd>${esc(p.bmi)}</dd>
</dl>

<h2>${esc(L('health.title'))}</h2>
<dl>
  <dt>${esc(L('set.injuries'))}</dt><dd>${esc(prefs.injuries)}</dd>
  <dt>${esc(L('health.allergies'))}</dt><dd>${esc(prefs.allergies)}</dd>
  <dt>${esc(L('health.diet'))}</dt><dd>${esc((prefs.diet || []).map((c) => L(`diet.${c}`)).join(', ') || null)}</dd>
</dl>

<h2>${esc(L('hist.title'))}</h2>
${table([L('hist.date'), L('hist.event')], (h.events || []).map((e) => [e.date, eventText(e, { t, tv })]))}

<h2>${esc(L('nav.progress'))}</h2>
${table([L('hist.date'), L('prof.weight')], (d.progress?.weights || []).map((w) => [w.date, `${w.weightKg} kg`]))}
<p></p>
${table([L('hist.date'), L('exp.exercise'), 'kg', L('exp.reps'), L('exp.sets'), 'e1RM'],
        (d.progress?.lifts || []).map((l) => [l.date, l.exercise, l.weightKg, l.reps, l.sets, l.estimatedOneRepMax]))}

<h2>${esc(L('nav.nutrition'))}</h2>
${table([L('hist.date'), L('exp.meal'), 'kcal', L('exp.protein')],
        (d.nutrition || []).map((m) => [m.date, m.name, m.calories, m.proteinG]))}

<h2>${esc(L('nav.plans'))}</h2>
${table([L('exp.file'), L('hist.date')], (d.files || []).map((f) => [f.filename, (f.createdAt || f.uploadedAt || '').slice(0, 10)]))}

<h2>${esc(L('chat.conversations'))}</h2>
${(d.conversations || []).map((c) => `<div class="chat"><h3>${esc(c.title)}</h3>${
        (c.messages || []).map((m) => `<p class="msg"><span class="who">${esc(m.role === 'user' ? L('chat.you') : L('chat.coach'))}:</span> ${esc(m.message)}</p>`).join('')
    }</div>`).join('') || '<p class="muted">–</p>'}
<script>window.onload = () => setTimeout(() => window.print(), 300);</script>
</body></html>`;
    win.document.open();
    win.document.write(html);
    win.document.close();
}

/** One timeline line, shared by the Profile page and the PDF. */
export function eventText(e, { t, tv }) {
    switch (e.type) {
        case 'joined': return t('hist.joined');
        case 'first_weighin': return t('hist.firstWeighin', { kg: e.to });
        case 'first_workout': return t('hist.firstWorkout', { exercise: e.to });
        case 'goal': return e.from ? t('hist.goalChanged', { from: tv('goal', e.from), to: tv('goal', e.to) }) : t('hist.goalSet', { to: tv('goal', e.to) });
        case 'level': return e.from ? t('hist.levelChanged', { from: tv('level', e.from), to: tv('level', e.to) }) : t('hist.levelSet', { to: tv('level', e.to) });
        case 'badge': {
            const key = `badge.${e.from}`;
            const translated = t(key);
            return t('hist.badge', { badge: translated === key ? e.to : translated });
        }
        default: return e.type;
    }
}
