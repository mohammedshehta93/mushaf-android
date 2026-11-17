// «تحقّق من حديث»: search the six books and al-Muwatta with the scholars' rulings, and «مشهور لا يصح».
// Same data as the Android app (tools/hadith_books.py, tools/hadith_check.py). The plain-letter index
// (data/hadith/<book>.plain) is loaded on first use; full texts (<book>.txt) only for the hits shown.
import { $, $$, ar, esc, json, toast, ui } from './core.js';

const BOOKS = [['bukhari', 'صحيح البخاري'], ['muslim', 'صحيح مسلم'], ['abudawud', 'سنن أبي داود'], ['tirmidhi', 'جامع الترمذي'],
  ['nasai', 'سنن النسائي'], ['ibnmajah', 'سنن ابن ماجه'], ['malik', 'موطأ مالك']];
const NAME = Object.fromEntries(BOOKS);
const VERDICT = ['صحيح أو حسن', 'ضعيف', 'ضعيف جدًا / منكر', 'موضوع'];
const COLOR = ['#2E7D32', '#E08A00', '#D84315', '#C62828'];

/** Same as HadithDb.plain (Kotlin) and hadith_books.norm_with_index (Python). [map]: plain index → index in s. */
export function plain(s, map = null) {
  let out = '', space = true, i = -1;
  for (let ch of s) {
    i += ch.length;
    if ('ٱأإآ'.includes(ch)) ch = 'ا'; else if (ch === 'ى') ch = 'ي'; else if (ch === 'ة') ch = 'ه';
    const c = ch.codePointAt(0);
    if ((c >= 0x0610 && c <= 0x061a) || (c >= 0x064b && c <= 0x065f) || c === 0x0670 || (c >= 0x06d6 && c <= 0x06ed) || ch === 'ـ') continue;
    if ('ؤئءۥۦ'.includes(ch)) continue;
    if (ch >= 'ا' && ch <= 'ي') { if (map) map[out.length] = i; out += ch; space = false; } else if (!space) { if (map) map[out.length] = i; out += ' '; space = true; }
  }
  return out.endsWith(' ') ? out.slice(0, -1) : out;
}

let indexP = null;
function loadIndex() {
  if (!indexP) {
    indexP = Promise.all(BOOKS.map(([id]) => Promise.all([
      fetch(`data/hadith/${id}.plain`).then((r) => { if (!r.ok) throw new Error(id); return r.text(); }),
      fetch(`data/hadith/${id}.meta`).then((r) => { if (!r.ok) throw new Error(id); return r.text(); }),
    ]).then(([p, m]) => ({ id, plain: p.split('\n'), meta: m.split('\n').map((l) => l.split('\t')) }))));
    indexP.catch(() => { indexP = null; });
  }
  return indexP;
}

function has(t, w) {
  if (w.length > 2) return t.includes(w);
  for (let k = t.indexOf(w); k >= 0; k = t.indexOf(w, k + 1)) {
    if ((k === 0 || t[k - 1] === ' ') && (k + w.length === t.length || t[k + w.length] === ' ')) return true;
  }
  return false;
}

/** Index in text a little before the searched words (see HadithDb.startOf). */
function startOf(text, q, words) {
  const map = [], p = plain(text, map);
  let k = p.indexOf(q);
  if (k < 0) { const ks = words.map((w) => p.indexOf(w)).filter((x) => x >= 0); if (!ks.length) return 0; k = Math.min(...ks); }
  let i = map[k];
  if (i < 120) return 0;
  const stop = Math.max(0, i - 90);
  while (i > stop && !'،."'.includes(text[i - 1])) i--;
  if (i === stop) while (i < text.length && text[i] !== ' ') i++;
  return i;
}

async function search(q, only) {
  const idx = await loadIndex();
  const pq = plain(q), words = pq.split(' ').filter((w) => w.length >= 2);
  if (!words.length) return { hits: [], total: 0 };
  const exact = [], loose = [];
  for (const b of idx) {
    if (only && b.id !== only) continue;
    b.plain.forEach((t, i) => {
      if (words.every((w) => has(t, w))) (words.length > 1 && t.includes(pq) ? exact : loose).push({ book: b.id, line: i, n: b.meta[i][0], v: b.meta[i][1] === '' ? -1 : +b.meta[i][1] });
    });
  }
  const all = words.length > 1 ? exact.concat(loose) : loose;
  const hits = all.slice(0, 60);
  // texts: one fetch per book that has hits (the browser caches the file)
  for (const id of [...new Set(hits.map((h) => h.book))]) {
    const lines = (await fetch(`data/hadith/${id}.txt`).then((r) => r.text())).split('\n');
    hits.filter((h) => h.book === id).forEach((h) => { const p = lines[h.line].split('\t'); h.g = p[2]; h.t = p.slice(3).join('\t'); h.st = startOf(h.t, pq, words); });
  }
  return { hits, total: all.length };
}

const chip = (v, label) => `<span style="background:${COLOR[v] || '#888'};color:#fff;border-radius:999px;padding:2px 10px;font-size:12px;font-weight:bold">${esc(label || VERDICT[v] || 'لا حكم في البيانات')}</span>`;
const dorar = (t) => `https://dorar.net/hadith/search?q=${encodeURIComponent(t.slice(0, 120))}`;

function hitHtml(h) {
  const sahihain = h.book === 'bukhari' || h.book === 'muslim';
  const ruling = h.g ? `الحكم: ${h.g}` : sahihain ? `الحكم: صحيح (من أحاديث ${NAME[h.book]})` : '';
  return `<div class="card"><div class="row" style="gap:8px;align-items:center">${chip(h.v)}<b style="color:var(--gharib)">${esc(NAME[h.book])} (${esc(h.n)})</b></div>
    <div style="font-size:16px;line-height:1.8;margin:6px 0;text-align:justify;display:-webkit-box;-webkit-line-clamp:7;-webkit-box-orient:vertical;overflow:hidden" data-t data-full="${esc(h.t)}">${esc(h.st ? '… ' + h.t.slice(h.st).trimStart() : h.t)}</div>
    ${ruling ? `<div class="muted">${esc(ruling)}</div>` : ''}
    <div class="row" style="gap:12px"><button class="link" data-more>النص كاملًا</button>
      <button class="link" data-copy="${esc(`${h.t}\n— ${NAME[h.book]} (${h.n})${h.g ? `، ${h.g}` : ''}`)}">نسخ</button></div></div>`;
}

export async function openHadithCheck() {
  let list = [];
  try { list = await json('data/hadith/not_sahih.json'); } catch { /* missing in this build */ }
  ui.screen({
    title: 'تحقّق من حديث', sub: 'الكتب الستة وموطأ مالك مع أحكام العلماء',
    tabs: [{ id: 's', label: 'ابحث عن حديث' }, { id: 'n', label: 'مشهور لا يصح' }],
    render(body, tab) {
      if (tab === 'n') {
        body.innerHTML = `<p class="muted">أحاديث يتداولها الناس وهي لا تصح عن النبي ﷺ؛ فلا تنسبها إليه ولا تنشرها. وتحت بعضها ما صحّ في معناها.</p>
          ${list.map((n) => `<div class="card"><div class="row" style="gap:8px;align-items:center">${chip(Math.max(1, n.c), n.src ? VERDICT[Math.min(3, Math.max(1, n.c))] : 'لا يصح')}${n.src ? `<span class="muted">${esc(n.src)}</span>` : ''}</div>
            <div style="font-size:18px;line-height:1.8;margin:6px 0">«${esc(n.t)}»</div>
            <div style="color:#C62828;font-size:13px;line-height:1.7">${esc(n.r)}</div>
            ${n.alt ? `<b style="color:${COLOR[0]};display:block;margin-top:8px">الصحيح في معناه:</b><div style="font-size:16px;line-height:1.8">${esc(n.alt.t)}</div><div class="muted">${esc(n.alt.src)}</div>` : ''}
            <a class="link" target="_blank" rel="noopener" href="${dorar(n.t)}">حكم العلماء بالتفصيل (الدرر السنية) ›</a></div>`).join('')}`;
        return;
      }
      let only = '', q = '', timer = 0, seq = 0;
      body.innerHTML = `<input class="input" data-q placeholder="اكتب كلمات من الحديث" enterkeyhint="search">
        <div class="chips"><button class="chip on" data-b="">الكل</button>${BOOKS.map(([id, n]) => `<button class="chip" data-b="${id}">${n}</button>`).join('')}</div>
        <p class="muted" style="font-size:12px">الحكم المذكور لهذه الرواية بإسنادها كما حكم عليها العلماء (الألباني وغيره)؛ أحاديث البخاري ومسلم كلها صحيحة. قد يَرِد الحديث نفسه بإسناد آخر بحكم آخر، فإن لم تجده أو احتجت التفصيل فابحث في «الدرر السنية».</p>
        <div data-list><div class="card"><b style="color:var(--gharib)">كيف تتحقق من حديث وصلك؟</b><div>اكتب كلمتين أو ثلاثًا مميزة من نصه (بدون تشكيل). ستظهر الروايات في الكتب الستة والموطأ بأرقامها وحكم كل رواية. وفي التبويب الثاني أحاديث مشهورة على الألسنة لا تصح.</div></div></div>`;
      const out = $('[data-list]', body);
      const run = async () => {
        const my = ++seq;
        if (plain(q).length < 3) return;
        out.innerHTML = `<p class="muted">${indexP ? 'يبحث…' : 'يجهّز البحث في نحو ٣٦ ألف حديث (مرة واحدة)…'}</p>`;
        let r;
        try { r = await search(q, only); } catch { if (my === seq) out.innerHTML = '<p class="muted">تعذّر تحميل كتب الحديث. تأكد من الاتصال بالإنترنت ثم أعد المحاولة.</p>'; return; }
        if (my !== seq) return;
        out.innerHTML = r.total === 0
          ? `<div class="card"><b>لم يُعثر عليه في الكتب الستة ولا الموطأ.</b><div class="muted">قد يكون بلفظ آخر (جرّب كلمات أقل)، أو في كتب أخرى، أو لا أصل له. ابحث عنه في الموسوعة الحديثية بالدرر السنية:</div>
             <a class="link" target="_blank" rel="noopener" href="${dorar(q)}">ابحث في الدرر السنية ›</a></div>`
          : `<p class="muted">${ar(r.total)} رواية${r.total > r.hits.length ? ` — تظهر أول ${ar(r.hits.length)}؛ زد كلمات للتضييق` : ''}</p>${r.hits.map(hitHtml).join('')}
             <a class="link" target="_blank" rel="noopener" href="${dorar(q)}">التفصيل في الدرر السنية ›</a>`;
      };
      $('[data-q]', body).oninput = (e) => { q = e.target.value; clearTimeout(timer); timer = setTimeout(run, 400); };
      body.addEventListener('click', (e) => {
        const b = e.target.closest('[data-b]');
        if (b) { only = b.dataset.b; $$('[data-b]', body).forEach((x) => x.classList.toggle('on', x === b)); run(); return; }
        const m = e.target.closest('[data-more]');
        if (m) { const t = $('[data-t]', m.closest('.card')); t.textContent = t.dataset.full; t.style.webkitLineClamp = 'unset'; t.style.display = 'block'; m.remove(); return; }
        const c = e.target.closest('[data-copy]');
        if (c) navigator.clipboard?.writeText(c.dataset.copy).then(() => toast('نُسخ'), () => {});
      });
    },
  });
}
