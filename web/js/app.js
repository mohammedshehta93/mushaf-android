// تِبيان — web version (iPhone / any browser). Same data and fonts as the Android app.
import { $, $$, ar, esc, store, json, font, loadMeta, normalize, toast, ui, isIOS, standalone, library } from './core.js';
import { openHome, prayerChip } from './tools.js';
import { startReading, intentionOptionsHtml, handleIntentionOption, whatsNew } from './extra.js';
import { nuzulHtml, wireFull } from './surahs.js';
import { openTadabbur, noteSheet, tadabburOptionsHtml, handleTadabburOption } from './tadabbur.js';

const PAGES = 604;
// QCF V2 (the current Madinah print). meta.maxLine = widest line of the whole Mushaf at size 100 (measured at build).
const FONT_STEPS = [1, 0.95, 0.91]; const FONT_NAMES = ['كالمطبوع', 'كبير', 'أكبر'];   // same steps as the Android app
const BASMALA = '\uFB51\uFB52\uFB53';
const JUZ_NAMES = ['الأول', 'الثاني', 'الثالث', 'الرابع', 'الخامس', 'السادس', 'السابع', 'الثامن', 'التاسع', 'العاشر', 'الحادي عشر', 'الثاني عشر', 'الثالث عشر', 'الرابع عشر', 'الخامس عشر', 'السادس عشر', 'السابع عشر', 'الثامن عشر', 'التاسع عشر', 'العشرون', 'الحادي والعشرون', 'الثاني والعشرون', 'الثالث والعشرون', 'الرابع والعشرون', 'الخامس والعشرون', 'السادس والعشرون', 'السابع والعشرون', 'الثامن والعشرون', 'التاسع والعشرون', 'الثلاثون'];
const hizbLabel = (p) => { const r = rubOfPage(p); const h = Math.floor((r.n - 1) / 4) + 1, q = (r.n - 1) % 4; return `${q ? `${['', '١/٤', '١/٢', '٣/٤'][q]} ` : ''}الحزب ${ar(h)}`; };
let meta, current = store.get('page', 1);
let selection = null;              // {s, a, wordOnly, g}
const pager = $('#pager');
const measureCtx = document.createElement('canvas').getContext('2d');

// ---------------------------------------------------------------- helpers
const surahOfPage = (p) => meta.surahs[meta.pageRanges[p - 1][0] - 1];
const pageOf = (s, a) => meta.ap[s - 1][a - 1];
const juzOfPage = (p) => { let j = 1; for (const x of meta.juz) if (x.page <= p) j = x.n; return j; };
const rubOfPage = (p) => { let r = meta.rub[0]; for (const x of meta.rub) if (x.page <= p) r = x; return r; };
export const ayahText = async (s, a) => (await json('data/text.json'))[`${s}:${a}`] || '';
export function applyTheme() {
  document.body.className = `theme-${store.get('theme', 'cream')}` + (document.body.classList.contains('bars-on') ? ' bars-on' : '');
  pager.classList.toggle('colored', store.get('colored', true));
  document.querySelector('meta[name=theme-color]').content = store.get('theme', 'cream') === 'night' ? '#121416' : '#0F4C3A';
}

// ---------------------------------------------------------------- page rendering
async function renderPage(el, n) {
  if (el.dataset.state === 'done' || el.dataset.state === 'loading') return;
  el.dataset.state = 'loading';
  let lines;
  try {
    [lines] = await Promise.all([json(`data/pages/${n}.json`), font(`p${n}`, `fonts/p${n}.woff2`), font('bsml', 'fonts/bsml.woff2')]);
  } catch (e) { el.dataset.state = ''; el.innerHTML = '<div class="pno">تعذّر تحميل الصفحة — تحقق من الاتصال</div>'; return; }
  const inner = document.createElement('div'); inner.className = 'page-inner';
  el.innerHTML = ''; el.appendChild(inner);
  const W = inner.clientWidth, H = inner.clientHeight;
  const special = n <= 2, rows = lines.length;
  const lineH = special ? H / (rows + 5) : H / Math.max(rows, 15);
  const contentW = special ? W * 0.76 : W;
  const left = (W - contentW) / 2;
  measureCtx.font = `100px p${n}`;
  const nat = lines.map((l) => (l.t === 'x' ? [...l.g].reduce((t, c) => t + measureCtx.measureText(c).width, 0) : 0));
  const maxNat = Math.max(1, ...nat);
  const ref = (meta.maxLine || 1610) * (FONT_STEPS[store.get('fontStep2', 0)] || 1);
  const size = special ? Math.min((100 * W) / ref, (100 * contentW) / maxNat, lineH * 0.8) : Math.min((100 * W) / ref, lineH * 0.8);
  const startY = special ? (H - rows * lineH) / 2 : 0;
  const frag = document.createDocumentFragment();
  lines.forEach((l, i) => {
    const d = document.createElement('div');
    d.className = 'line';
    d.style.cssText = `position:absolute;top:${startY + i * lineH}px;left:${left}px;width:${contentW}px;height:${lineH}px`;
    if (l.t === 'h') {
      d.innerHTML = `<div class="hdr" style="width:100%;height:${lineH * 0.8}px;font-size:${lineH * 0.42}px">سورة ${esc(meta.surahs[l.s - 1].name)}</div>`;
    } else if (l.t === 'b') {
      measureCtx.font = '100px bsml';
      const w = measureCtx.measureText(BASMALA).width || 1;
      const bs = Math.min((100 * contentW * 0.7) / w, lineH * 1.05);
      d.innerHTML = `<div class="bsml" style="width:100%;font-size:${bs}px">${BASMALA}</div>`;
    } else {
      d.style.fontFamily = `p${n}`; d.style.fontSize = `${size}px`;
      const px = (nat[i] * size) / 100;
      if (px > contentW) {   // too long at the larger sizes: narrow this line slightly (line breaks never change)
        d.style.width = `${px}px`; d.style.left = `${left + contentW - px}px`;
        d.style.transformOrigin = 'right center'; d.style.transform = `scaleX(${contentW / px})`;
      } else if (!special && px > contentW * 0.9 && l.k.length > 1) d.classList.add('full');
      const glyphs = [...l.g];
      d.innerHTML = glyphs.map((c, j) => {
        const [s, a, w] = l.k[j]; const m = l.m[j];
        const g = m >= 0 ? m : m <= -2 ? -(m + 2) : -1;
        const cls = w === 0 ? 'mk' : m >= 0 ? 'gh' : '';
        return `<span class="${cls}" data-s="${s}" data-a="${a}" data-w="${w}" data-g="${g}" data-sa="${s}:${a}">${c}</span>`;
      }).join('');
    }
    frag.appendChild(d);
  });
  inner.appendChild(frag);
  // like the printed page: juz and surah on top, hizb and page number at the bottom
  const head = document.createElement('div'); head.className = 'pghead';
  head.innerHTML = `<span>الجزء ${JUZ_NAMES[juzOfPage(n) - 1]}</span><span>${esc(surahOfPage(n).name)}</span>`;
  const foot = document.createElement('div'); foot.className = 'pgfoot';
  foot.innerHTML = `<span>${hizbLabel(n)}</span><b>${ar(n)}</b>`;
  el.appendChild(head); el.appendChild(foot);
  if (isBookmarked(n)) { const r = document.createElement('div'); r.className = 'ribbon'; el.appendChild(r); }
  el.dataset.state = 'done';
  decorate();
}

function unrender(el) { el.innerHTML = ''; el.dataset.state = ''; }

function decorate() {
  $$('.sel,.selw,.playing', pager).forEach((e) => e.classList.remove('sel', 'selw', 'playing'));
  if (selection) {
    const q = `[data-sa="${selection.s}:${selection.a}"]`;
    if (selection.wordOnly) $$(`${q}[data-g="${selection.g}"]`, pager).forEach((e) => e.classList.add('selw'));
    else $$(q, pager).forEach((e) => e.classList.add('sel'));
  }
  if (player.cur && !player.cur.basmala) $$(`[data-sa="${player.cur.s}:${player.cur.a}"]`, pager).forEach((e) => e.classList.add('playing'));
}

// ---------------------------------------------------------------- pager
function buildPager() {
  const frag = document.createDocumentFragment();
  for (let n = 1; n <= PAGES; n++) { const p = document.createElement('section'); p.className = 'page'; p.dataset.n = n; frag.appendChild(p); }
  pager.appendChild(frag);
  // which page is on screen: from the scroll position (RTL scrollers report negative scrollLeft)
  let raf = 0;
  pager.addEventListener('scroll', () => {
    if (raf) return;
    raf = requestAnimationFrame(() => {
      raf = 0;
      const n = Math.round(Math.abs(pager.scrollLeft) / pager.clientWidth) + 1;
      if (n >= 1 && n <= PAGES && n !== current) onPageChange(n);
    });
  }, { passive: true });
}

function pageEl(n) { return pager.children[n - 1]; }

function ensureAround(n) {
  for (let k = Math.max(1, n - 2); k <= Math.min(PAGES, n + 2); k++) renderPage(pageEl(k), k);
  $$('.page[data-state="done"]', pager).forEach((el) => { if (Math.abs(+el.dataset.n - n) > 4) unrender(el); });
}

let pageChanges = 0;
function onPageChange(n) {
  if (n !== current && ++pageChanges >= 1 && meta) startReading();   // intentions + one tadabbur reminder when reading starts
  current = n; store.set('page', n);
  ensureAround(n);
  if (selection && pageOf(selection.s, selection.a) !== n) { selection = null; hideMeaning(); decorate(); }
  updateBars();
}

export function goTo(n, sel = null) {
  n = Math.max(1, Math.min(PAGES, n));
  ui.closeScreen(); ui.closeSheet();
  selection = sel;
  setTimeout(startReading, 400);
  renderPage(pageEl(n), n);
  pageEl(n).scrollIntoView({ block: 'nearest', inline: 'start' });
  onPageChange(n);
  decorate();
}

// ---------------------------------------------------------------- bars
let barsOn = false;
function setBars(on) {
  barsOn = on;
  $('#topbar').classList.toggle('hidden', !on); $('#bottombar').classList.toggle('hidden', !on);
  document.body.classList.toggle('bars-on', on);
  if (on) updateBars();
}
function updateBars() {
  if (!barsOn || !meta) return;
  const s = surahOfPage(current), r = rubOfPage(current);
  const q = ['', ' · ربع', ' · نصف', ' · ثلاثة أرباع'][(r.n - 1) % 4];
  $('#tb-surah').textContent = `سورة ${s.name}`;
  $('#tb-sub').innerHTML = `الجزء ${ar(juzOfPage(current))} · الحزب ${ar(Math.floor((r.n - 1) / 4) + 1)}${q} ${prayerChip()}`;
  $('#tb-page').textContent = `صفحة ${ar(current)}`;
  $('#tb-mark').textContent = isBookmarked(current) ? '📌' : '🔖';
  $('#slider').value = current; sliderLabel(current);
}
function sliderLabel(p) { $('#slider-label').textContent = `صفحة ${ar(p)} — سورة ${surahOfPage(p).name}`; }

// ---------------------------------------------------------------- bookmarks
const bookmarks = () => store.get('bookmarks', []);
const isBookmarked = (p) => bookmarks().some((b) => b.p === p);
function addBookmark(p, s, a) { store.set('bookmarks', [{ p, s, a, t: Date.now() }, ...bookmarks().filter((b) => b.p !== p)]); refreshPage(p); }
function removeBookmark(p) { store.set('bookmarks', bookmarks().filter((b) => b.p !== p)); refreshPage(p); }
function refreshPage(p) { const el = pageEl(p); unrender(el); renderPage(el, p); updateBars(); }

// ---------------------------------------------------------------- touch: tap = meaning / bars, long press = ayah menu
let press = null;
pager.addEventListener('pointerdown', (e) => {
  press = { x: e.clientX, y: e.clientY, target: e.target, timer: setTimeout(() => { const p = press; press = null; if (p) onLongPress(p.target); }, 480) };
});
pager.addEventListener('pointermove', (e) => {
  if (press && Math.hypot(e.clientX - press.x, e.clientY - press.y) > 10) { clearTimeout(press.timer); press = null; }
});
pager.addEventListener('pointerup', () => { if (press) { clearTimeout(press.timer); const t = press.target; press = null; onTap(t); } });
pager.addEventListener('pointercancel', () => { if (press) { clearTimeout(press.timer); press = null; } });
pager.addEventListener('contextmenu', (e) => e.preventDefault());

function onTap(target) {
  const span = target.closest('span[data-s]');
  if (!$('#meaning').classList.contains('hidden') || selection) { selection = null; hideMeaning(); decorate(); return; }
  if (span && span.classList.contains('gh') && store.get('colored', true)) { showMeaning(span); return; }
  setBars(!barsOn);
}

async function showMeaning(span) {
  const g = +span.dataset.g, s = +span.dataset.s, a = +span.dataset.a;
  const list = await json('data/gharib.json');
  const e = list[g]; if (!e) return;
  selection = { s, a, g, wordOnly: true }; decorate(); setBars(false);
  const m = $('#meaning');
  m.innerHTML = `<div class="row"><div class="phrase grow">${esc(e[2])}</div><button class="icon" data-x>✕</button></div>
    <div class="mean">${esc(e[3])}</div>
    <div class="row"><div class="muted grow">${esc(meta.surahs[s - 1].name)} · ${ar(a)}</div><button class="link" data-taf>التفسير الكامل</button></div>`;
  m.classList.remove('hidden');
  $('[data-x]', m).onclick = () => { selection = null; hideMeaning(); decorate(); };
  $('[data-taf]', m).onclick = () => { hideMeaning(); openTafsir(s, a); };
}
function hideMeaning() { $('#meaning').classList.add('hidden'); }

async function onLongPress(target) {
  const span = target.closest('span[data-s]'); if (!span) return;
  if (navigator.vibrate) navigator.vibrate(15);
  const s = +span.dataset.s, a = +span.dataset.a;
  selection = { s, a }; hideMeaning(); decorate();
  const [text, gh] = await Promise.all([ayahText(s, a), json('data/gharib.json')]);
  const meanings = gh.filter((x) => x[0] === s && x[1] === a);
  ui.onSheetClose = () => { selection = null; decorate(); };
  const sh = ui.sheet(`<div style="font-weight:700">سورة ${esc(meta.surahs[s - 1].name)} · الآية ${ar(a)}</div>
    <div class="quran" style="max-height:30vh;overflow:auto">${esc(text)} ${ar(a)}</div>
    ${store.get('tdSheet', true) ? '<button class="item" data-a="tad"><span class="ic">💡</span><span class="grow">وقفة تدبّر<br><span class="muted">مقصد السورة، أسئلة، ترديد، دفتر التدبّر</span></span></button>' : ''}
    <button class="item" data-a="taf"><span class="ic">📖</span>التفسير</button>
    ${meanings.length ? `<button class="item" data-a="mean"><span class="ic">💬</span>معاني كلمات الآية (${ar(meanings.length)})</button>` : ''}
    <button class="item" data-a="play"><span class="ic">🎧</span>استمع من هذه الآية</button>
    <button class="item" data-a="note"><span class="ic">📝</span>${store.get('notes', {})[`${s}:${a}`] ? 'تعديل ما كتبته في دفتر التدبّر' : 'اكتب في دفتر التدبّر'}</button>
    <button class="item" data-a="mark"><span class="ic">🔖</span>علامة توقف هنا</button>
    <button class="item" data-a="copy"><span class="ic">📋</span>نسخ الآية</button>
    <button class="item" data-a="share"><span class="ic">↗</span>مشاركة الآية</button>`);
  const share = `﴿${text}﴾ [${meta.surahs[s - 1].name}: ${ar(a)}]`;
  sh.onclick = async (e) => {
    const b = e.target.closest('[data-a]'); if (!b) return;
    const act = b.dataset.a;
    if (act === 'taf') { ui.closeSheet(); openTafsir(s, a); }
    if (act === 'tad') { ui.closeSheet(); openTadabbur(s, a); }
    if (act === 'note') noteSheet(s, a);
    if (act === 'mean') ui.sheet(`<div style="font-weight:700;margin-bottom:6px">معاني كلمات الآية ${ar(a)}</div>` +
      meanings.map((x) => `<div style="padding:8px 0;border-bottom:1px solid rgba(128,128,128,.15)"><div class="quran" style="color:var(--gharib)">${esc(x[2])}</div><div style="font-size:17px;line-height:1.6">${esc(x[3])}</div></div>`).join(''));
    if (act === 'play') { ui.closeSheet(); player.play(s, a); }
    if (act === 'mark') { ui.closeSheet(); addBookmark(pageOf(s, a), s, a); toast('تم حفظ علامة التوقف'); }
    if (act === 'copy') { try { await navigator.clipboard.writeText(share); toast('تم نسخ الآية'); } catch (err) { toast('تعذّر النسخ'); } ui.closeSheet(); }
    if (act === 'share') { ui.closeSheet(); if (navigator.share) navigator.share({ text: share }).catch(() => {}); else { navigator.clipboard?.writeText(share); toast('تم النسخ للمشاركة'); } }
  };
}

// ---------------------------------------------------------------- tafsir (Ibn Kathir)
function styleTafsir(p) {
  return esc(p).replace(/﴿[^﴾]*﴾/g, (m) => `<span class="q">${m}</span>`).replace(/\[\[(.*?)\]\]/g, (m, x) => `<span class="fn"> (${x}) </span>`);
}
async function openTafsir(s, a) {
  let blocks;
  try { blocks = await json(`data/tafsir/${s}.json`); } catch (e) { toast('تعذّر تحميل التفسير — تحقق من الاتصال'); return; }
  const b = blocks.find((x) => a >= x[0] && a <= x[1]) || blocks[0];
  const text = await json('data/text.json');
  const ayat = []; for (let k = b[0]; k <= b[1]; k++) ayat.push(`${text[`${s}:${k}`]} ${ar(k)}`);
  const range = b[0] === b[1] ? `الآية ${ar(b[0])}` : `الآيات ${ar(b[0])}–${ar(b[1])}`;
  const nuzul = await nuzulHtml(s, b[0], b[1]);
  ui.screen({
    title: 'تفسير ابن كثير', sub: `سورة ${meta.surahs[s - 1].name} · ${range}`,
    actions: '<button class="icon" data-dec>أ-</button><button class="icon" data-inc>أ+</button>',
    render(body) {
      body.style.setProperty('--tafSize', `${store.get('tafSize', 19)}px`);
      body.innerHTML = `<div class="card quran">${ayat.map(esc).join(' ')}</div>
        <div class="row"><button class="link" data-go>اذهب للآية في المصحف</button></div>${nuzul}
        <div class="taf">${b[2].split(/\n+/).filter((x) => x.trim()).map((p) => `<p>${styleTafsir(p)}</p>`).join('')}</div>
        <div class="row" style="gap:10px;margin-top:16px"><button class="btn o grow" data-prev>السابق</button><button class="btn o grow" data-next>التالي</button></div>
        <p class="muted">المصدر: تفسير القرآن العظيم للحافظ ابن كثير. النص الباهت بين قوسين من حواشي المحقق.</p>`;
      $('[data-go]', body).onclick = () => goTo(pageOf(s, b[0]), { s, a: b[0] });
      wireFull(body);
      $('[data-prev]', body).onclick = () => (b[0] > 1 ? openTafsir(s, b[0] - 1) : s > 1 && openTafsir(s - 1, meta.surahs[s - 2].ayahs));
      $('[data-next]', body).onclick = () => (b[1] < meta.surahs[s - 1].ayahs ? openTafsir(s, b[1] + 1) : s < 114 && openTafsir(s + 1, 1));
      const sc = $('#screen');
      $('[data-dec]', sc).onclick = () => { store.set('tafSize', Math.max(14, store.get('tafSize', 19) - 1)); body.style.setProperty('--tafSize', `${store.get('tafSize')}px`); };
      $('[data-inc]', sc).onclick = () => { store.set('tafSize', Math.min(30, store.get('tafSize', 19) + 1)); body.style.setProperty('--tafSize', `${store.get('tafSize')}px`); };
    },
  });
}

// ---------------------------------------------------------------- index & search
export function openIndex(tab = 'surahs') {
  ui.screen({
    title: 'الفهرس', sub: `أنت في صفحة ${ar(current)}`, tab,
    tabs: [{ id: 'surahs', label: 'السور' }, { id: 'juz', label: 'الأجزاء' }, { id: 'marks', label: 'العلامات' }],
    render(body, id) {
      if (id === 'surahs') {
        body.innerHTML = `<input class="input" placeholder="اسم السورة أو رقم الصفحة" data-q><div data-list></div>`;
        const cur = surahOfPage(current).n;
        const draw = (q) => {
          const nq = normalize(q.trim()); const num = parseInt(q.replace(/[٠-٩]/g, (d) => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)), 10);
          const list = meta.surahs.filter((s) => !nq || normalize(s.name).includes(nq) || s.n === num);
          $('[data-list]', body).innerHTML = (num >= 1 && num <= PAGES ? `<button class="item" data-p="${num}"><span class="ic">↩</span>انتقال إلى صفحة ${ar(num)}</button>` : '') +
            list.map((s) => `<button class="item" data-p="${s.page}"><span class="badge ${s.n === cur ? 'on' : ''}">${ar(s.n)}</span>
              <span class="grow"><b>سورة ${esc(s.name)}</b><br><span class="muted">${s.type} · ${ar(s.ayahs)} آية</span></span><span class="muted">${ar(s.page)}</span></button>`).join('');
        };
        draw(''); $('[data-q]', body).oninput = (e) => draw(e.target.value);
        setTimeout(() => $(`[data-p="${surahOfPage(current).page}"]`, body)?.scrollIntoView({ block: 'center' }), 50);
      } else if (id === 'juz') {
        const cj = juzOfPage(current);
        body.innerHTML = meta.juz.map((j) => `<button class="item" data-p="${j.page}"><span class="badge ${j.n === cj ? 'on' : ''}">${ar(j.n)}</span>
          <span class="grow"><b>الجزء ${JUZ_NAMES[j.n - 1]}</b><br><span class="muted">${esc(meta.surahs[j.s - 1].name)} ${ar(j.a)}</span></span><span class="muted">${ar(j.page)}</span></button>`).join('');
      } else {
        const bs = bookmarks();
        body.innerHTML = bs.length ? bs.map((b) => `<div class="item"><span class="ic" style="color:var(--ribbon)">▮</span>
          <button class="grow" style="text-align:right" data-p="${b.p}" data-s="${b.s}" data-a="${b.a}"><b>سورة ${esc(meta.surahs[b.s - 1].name)} · الآية ${ar(b.a)}</b><br>
          <span class="muted">صفحة ${ar(b.p)} · ${new Date(b.t).toLocaleDateString('ar')}</span></button><button class="icon" data-del="${b.p}">🗑</button></div>`).join('')
          : '<p class="muted" style="text-align:center;margin-top:40px">لا توجد علامات بعد.<br>اضغط مطولًا على أي آية واختر «علامة توقف هنا».</p>';
      }
      body.onclick = (e) => {
        const del = e.target.closest('[data-del]');
        if (del) { removeBookmark(+del.dataset.del); openIndex('marks'); return; }
        const b = e.target.closest('[data-p]'); if (!b) return;
        goTo(+b.dataset.p, b.dataset.s ? { s: +b.dataset.s, a: +b.dataset.a } : null);
      };
    },
  });
}

export function openSearch() {
  ui.screen({
    title: 'البحث في القرآن', sub: 'اكتب بدون تشكيل',
    render(body) {
      body.innerHTML = '<input class="input" placeholder="مثال: الرحمن علم القرآن" data-q><div class="muted" data-n style="margin:8px 0"></div><div data-r></div>';
      let index = null, t = null;
      const run = async (q) => {
        const nq = normalize(q); const out = $('[data-r]', body), n = $('[data-n]', body);
        if (nq.length < 2) { out.innerHTML = ''; n.textContent = ''; return; }
        const text = await json('data/text.json');
        index ||= Object.entries(text).map(([k, v]) => [k, normalize(v)]);
        const hits = index.filter(([, v]) => v.includes(nq)).slice(0, 200);
        n.textContent = hits.length >= 200 ? 'أكثر من ٢٠٠ نتيجة — زد كلمات البحث' : `${ar(hits.length)} نتيجة`;
        out.innerHTML = hits.map(([k]) => { const [s, a] = k.split(':').map(Number);
          return `<button class="item" style="display:block" data-s="${s}" data-a="${a}"><div class="muted">سورة ${esc(meta.surahs[s - 1].name)} · ${ar(a)} · صفحة ${ar(pageOf(s, a))}</div><div class="quran" style="font-size:20px">${esc(text[k])}</div></button>`; }).join('');
      };
      const inp = $('[data-q]', body);
      inp.oninput = () => { clearTimeout(t); t = setTimeout(() => run(inp.value), 250); };
      setTimeout(() => inp.focus(), 100);
      body.onclick = (e) => { const b = e.target.closest('[data-s]'); if (b) { const s = +b.dataset.s, a = +b.dataset.a; goTo(pageOf(s, a), { s, a }); } };
    },
  });
}

// ---------------------------------------------------------------- recitation
let reciters = [];
const player = {
  audio: new Audio(), queue: [], i: 0, cur: null,
  reciter() { return reciters.find((r) => r.id === store.get('reciter')) || reciters[0]; },
  url(id, s, a) { return `https://everyayah.com/data/${id}/${String(s).padStart(3, '0')}${String(a).padStart(3, '0')}.mp3`; },
  play(s, a, toA = null) {
    const end = toA || meta.surahs[s - 1].ayahs;
    const rep = store.get('repeat', 1);
    this.queue = [];
    for (let k = a; k <= end; k++) {
      if (k === 1 && s !== 1 && s !== 9) this.queue.push({ s, a: 1, basmala: true });
      for (let r = 0; r < rep; r++) this.queue.push({ s, a: k });
    }
    this.gap = 0; this.endless = false;
    this.i = 0; this.start();
  },
  /** «ردّد الآية»: one ayah [times] times (0 = until stopped) with a silent pause of [gap] ms between repetitions. */
  repeatOne(s, a, times, gap) {
    this.queue = []; for (let r = 0; r < Math.max(1, times); r++) this.queue.push({ s, a });
    this.gap = gap; this.endless = times <= 0;
    this.i = 0; this.start();
  },
  start() {
    clearTimeout(this.waiting); this.waiting = null;
    this.cur = this.queue[this.i];
    if (!this.cur) return this.stop();
    const r = this.reciter();
    document.dispatchEvent(new Event('mushaf:recite'));
    this.audio.src = this.cur.basmala ? this.url(r.id, 1, 1) : this.url(r.id, this.cur.s, this.cur.a);
    this.audio.play().catch(() => toast('اضغط تشغيل للمتابعة'));
    $('#player').classList.remove('hidden');
    $('#pl-title').textContent = this.cur.basmala ? 'البسملة' : `سورة ${meta.surahs[this.cur.s - 1].name} · الآية ${ar(this.cur.a)}`;
    $('#pl-sub').textContent = r.name;
    if (!this.cur.basmala && store.get('follow', true)) { const p = pageOf(this.cur.s, this.cur.a); if (p !== current) goTo(p); }
    decorate();
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({ title: $('#pl-title').textContent, artist: r.name, album: 'تِبيان' });
    }
  },
  next() { if (this.i < this.queue.length - 1) { this.i++; this.start(); } },
  prev() { if (this.i > 0) { this.i--; this.start(); } },
  toggle() {
    if (this.waiting) { clearTimeout(this.waiting); this.waiting = null; $('#pl-toggle').textContent = '▶'; return; }   // pause during the silence
    if (this.audio.paused) this.audio.play(); else this.audio.pause();
  },
  stop() { clearTimeout(this.waiting); this.waiting = null; this.audio.pause(); this.queue = []; this.cur = null; $('#player').classList.add('hidden'); decorate(); },
};
player.audio.addEventListener('ended', () => {
  const more = player.i < player.queue.length - 1 || player.endless;
  const go = () => { if (player.i < player.queue.length - 1) player.i++; player.start(); };
  if (!more) return player.stop();
  if (player.gap) { player.waiting = setTimeout(go, player.gap); $('#pl-toggle').textContent = '⏸'; } else go();
});
player.audio.addEventListener('play', () => ($('#pl-toggle').textContent = '⏸'));
player.audio.addEventListener('pause', () => { if (!player.waiting) $('#pl-toggle').textContent = '▶'; });
player.audio.addEventListener('error', () => { if (player.cur) toast('تعذّر تشغيل التلاوة — تحقق من الاتصال'); });
$('#pl-next').onclick = () => player.next(); $('#pl-prev').onclick = () => player.prev();
$('#pl-toggle').onclick = () => player.toggle(); $('#pl-stop').onclick = () => player.stop();
if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('nexttrack', () => player.next());
  navigator.mediaSession.setActionHandler('previoustrack', () => player.prev());
}

function openListen() {
  const [s, a] = meta.pageRanges[current - 1];
  const r = player.reciter();
  const sh = ui.sheet(`<div style="font-weight:700;font-size:18px">الاستماع · سورة ${esc(meta.surahs[s - 1].name)}</div>
    <div class="muted" style="margin:6px 0">القارئ</div>
    ${reciters.map((x) => `<button class="item" data-r="${x.id}"><span class="ic">${x.id === r.id ? '◉' : '○'}</span>${esc(x.name)}</button>`).join('')}
    <div class="muted" style="margin:10px 0 4px">تكرار كل آية (للحفظ)</div>
    <div class="chips">${[1, 2, 3, 5, 10].map((n) => `<button class="chip ${store.get('repeat', 1) === n ? 'on' : ''}" data-rep="${n}">${ar(n)}</button>`).join('')}</div>
    <div class="row" style="gap:8px;margin-top:12px"><button class="btn grow" data-go="surah">من أول الصفحة حتى آخر السورة</button></div>
    <div class="row" style="gap:8px;margin-top:8px"><button class="btn o grow" data-go="page">هذه الصفحة فقط</button></div>`);
  sh.onclick = (e) => {
    const t = e.target.closest('[data-r],[data-rep],[data-go]'); if (!t) return;
    if (t.dataset.r) { store.set('reciter', t.dataset.r); openListen(); }
    else if (t.dataset.rep) { store.set('repeat', +t.dataset.rep); openListen(); }
    else {
      ui.closeSheet();
      const [ps, pa, es, ea] = meta.pageRanges[current - 1];
      player.play(ps, pa, t.dataset.go === 'page' && es === ps ? ea : null);
    }
  };
}

// ---------------------------------------------------------------- settings
function openSettings() {
  const sh = ui.sheet(`<div style="font-weight:700;font-size:18px">الإعدادات</div>
    <div class="muted" style="margin-top:10px">لون الصفحة</div>
    <div class="chips">${[['cream', 'كريمي'], ['white', 'أبيض'], ['night', 'ليلي']].map(([k, n]) => `<button class="chip ${store.get('theme', 'cream') === k ? 'on' : ''}" data-theme="${k}">${n}</button>`).join('')}</div>
    <div class="muted" style="margin-top:10px">حجم خط الصفحة</div>
    <div class="chips">${FONT_NAMES.map((n, i) => `<button class="chip ${store.get('fontStep2', 0) === i ? 'on' : ''}" data-fs="${i}">${n}</button>`).join('')}</div>
    ${intentionOptionsHtml()}
    ${tadabburOptionsHtml()}
    <button class="item" data-t="colored"><span class="ic">${store.get('colored', true) ? '☑' : '☐'}</span>تلوين الكلمات الغريبة (اضغط عليها لمعرفة المعنى)</button>
    <button class="item" data-t="follow"><span class="ic">${store.get('follow', true) ? '☑' : '☐'}</span>تقليب الصفحات مع التلاوة</button>
    <button class="item" data-a="offline"><span class="ic">⬇</span><span class="grow">تنزيل المصحف للاستخدام بدون إنترنت<br><span class="muted" data-off>${store.get('offline') ? 'تم التنزيل ✓' : 'حوالي ٢٥ ميجا'}</span></span></button>
    <button class="item" data-a="install"><span class="ic">📲</span>تثبيت التطبيق على الشاشة الرئيسية</button>
    <button class="item" data-a="share"><span class="ic">↗</span>شارك التطبيق</button>
    <p class="muted" style="line-height:1.7">المصادر: صفحات وخطوط مصحف المدينة — مجمع الملك فهد لطباعة المصحف الشريف (رواية حفص). معاني الكلمات: «المُيسَّر في غريب القرآن». التفسير: ابن كثير. التلاوات: everyayah.com. الأذكار: حصن المسلم.</p>`);
  sh.onclick = async (e) => {
    if (handleIntentionOption(e) || handleTadabburOption(e)) return openSettings();
    const fs = e.target.closest('[data-fs]');
    if (fs) { store.set('fontStep2', +fs.dataset.fs); window.dispatchEvent(new Event('resize')); openSettings(); return; }
    const t = e.target.closest('[data-theme],[data-t],[data-a]'); if (!t) return;
    if (t.dataset.theme) { store.set('theme', t.dataset.theme); applyTheme(); openSettings(); }
    else if (t.dataset.t) { store.set(t.dataset.t, !store.get(t.dataset.t, true)); applyTheme(); openSettings(); }
    else if (t.dataset.a === 'offline') downloadAll($('[data-off]', sh));
    else if (t.dataset.a === 'install') showInstall(true);
    else if (t.dataset.a === 'share') shareApp();
  };
}

export function shareApp() {
  const url = location.origin + location.pathname;
  const text = 'تطبيق «تِبيان» مجاني وبدون إعلانات: مصحف المدينة بخط مجمع الملك فهد، معاني الكلمات، التفسير، التلاوة، مواقيت الصلاة والأذكار.';
  if (navigator.share) navigator.share({ title: 'تِبيان', text, url }).catch(() => {});
  else { navigator.clipboard?.writeText(`${text}\n${url}`); toast('تم نسخ الرابط'); }
}

// ---------------------------------------------------------------- update button
// Every time the app is opened or comes back to the screen, the published version is compared with this one; if it
// changed, a bar with «تحديث» appears (an app left open in the background would otherwise keep the old version).
let lastCheck = 0;
async function checkUpdate() {
  const mine = document.querySelector('meta[name=app-version]')?.content;
  if (!mine || mine === 'dev' || Date.now() - lastCheck < 60000 || $('#update-bar')) return;
  lastCheck = Date.now();
  try {
    const html = await (await fetch('index.html', { cache: 'no-store' })).text();
    const live = /name=["']?app-version["']?\s+content=["']([^"']+)/.exec(html)?.[1];
    if (!live || live === mine) return;
    const bar = document.createElement('div');
    bar.id = 'update-bar';
    bar.style.cssText = 'position:fixed;inset-inline:12px;top:calc(env(safe-area-inset-top) + 10px);z-index:1000;display:flex;gap:10px;align-items:center;padding:12px 14px;border-radius:14px;background:var(--bar);color:var(--onBar);box-shadow:0 6px 24px rgba(0,0,0,.25);font-size:15px';
    bar.innerHTML = '<span style="flex:1">يوجد تحديث جديد للتطبيق</span><button data-up style="background:#fff;color:#0F4C3A;border:0;border-radius:10px;padding:8px 16px;font:inherit;font-weight:700">تحديث</button><button data-later style="background:none;border:0;color:var(--onBar);font:inherit;opacity:.8">لاحقًا</button>';
    bar.querySelector('[data-up]').onclick = async () => {
      bar.querySelector('[data-up]').textContent = '…';
      // let the new version's service worker take over first, so the reload gets the new files and data
      try {
        const r = await navigator.serviceWorker?.getRegistration();
        if (r) {
          await r.update();
          const sw = r.installing || r.waiting;
          if (sw) await new Promise((ok) => { sw.addEventListener('statechange', () => { if (sw.state === 'activated' || sw.state === 'redundant') ok(); }); setTimeout(ok, 15000); });
        }
      } catch (e) {}
      location.reload();
    };
    bar.querySelector('[data-later]').onclick = () => bar.remove();
    document.body.appendChild(bar);
  } catch (e) { /* offline: try again next time */ }
}

async function downloadAll(label) {
  if (!('caches' in window)) { toast('المتصفح لا يدعم التنزيل'); return; }
  try {
    const list = await (await fetch('offline.json')).json();
    const c = await caches.open('mushaf-data');
    let done = 0;
    const worker = async () => {
      while (list.length) {
        const u = list.shift();
        if (!(await c.match(u))) { try { if (navigator.serviceWorker?.controller) await fetch(u); else await c.add(u); } catch (e) {} }
        done++; if (done % 20 === 0) label.textContent = `جارٍ التنزيل… ${ar(Math.round((done / (done + list.length)) * 100))}٪`;
      }
    };
    await Promise.all([worker(), worker(), worker(), worker()]);
    store.set('offline', true); label.textContent = 'تم التنزيل ✓'; toast('المصحف متاح الآن بدون إنترنت');
  } catch (e) { toast('تعذّر التنزيل — حاول مرة أخرى'); }
}

function showInstall(force = false) {
  if (standalone || (!force && store.get('installHintShown'))) return;
  store.set('installHintShown', true);
  ui.sheet(isIOS
    ? `<div style="font-weight:700;font-size:18px">ثبّت «تِبيان» على الآيفون</div>
       <ol style="line-height:2;font-size:16px"><li>افتح الرابط من <b>Safari</b>.</li><li>اضغط زر <b>المشاركة</b> (المربع والسهم ⬆) أسفل الشاشة.</li>
       <li>اختر <b>«إضافة إلى الشاشة الرئيسية»</b> ثم <b>«إضافة»</b>.</li></ol><p class="muted">سيظهر كتطبيق ويفتح ملء الشاشة.</p>`
    : `<div style="font-weight:700;font-size:18px">ثبّت «تِبيان»</div>
       <p style="line-height:1.8">من قائمة المتصفح (⋮) اختر <b>«تثبيت التطبيق»</b> أو <b>«إضافة إلى الشاشة الرئيسية»</b>.<br>لأجهزة أندرويد يتوفر أيضًا تطبيق كامل بالأذان والإشعارات.</p>`);
}

// ---------------------------------------------------------------- wiring
$('#slider').addEventListener('input', (e) => sliderLabel(+e.target.value));
$('#slider').addEventListener('change', (e) => goTo(+e.target.value));
$('#tb-mark').onclick = () => {
  if (isBookmarked(current)) removeBookmark(current);
  else { const [s, a] = meta.pageRanges[current - 1]; addBookmark(current, s, a); toast('تم حفظ علامة التوقف'); }
};
$('#bottombar').addEventListener('click', (e) => {
  const b = e.target.closest('[data-act]'); if (!b) return;
  const act = b.dataset.act;
  if (act === 'index') openIndex();
  if (act === 'listen') openListen();
  if (act === 'search') openSearch();
  if (act === 'home') openHome();
  if (act === 'settings') openSettings();
});
window.addEventListener('resize', () => {
  clearTimeout(window._rz);
  window._rz = setTimeout(() => { $$('.page[data-state="done"]', pager).forEach(unrender); ensureAround(current); pageEl(current).scrollIntoView({ inline: 'start' }); }, 200);
});

export const app = { pageOf: (s, a) => pageOf(s, a), repeatAyah: (s, a, times, gap) => player.repeatOne(s, a, times, gap), stopAudio: () => { if (player.cur) player.stop(); }, goTo, openIndex, openSearch, openTafsir, openListen, openSettings, shareApp, current: () => current, meta: () => meta };

(async function init() {
  applyTheme();
  try {
    font('hafs', 'fonts/hafs.woff2').catch(() => {});
    meta = await loadMeta();
    reciters = await json('data/reciters.json');
    library().then((l) => { const v = reciters.filter((r) => !l.hidden('reciters', r.name)); if (v.length) reciters = v; }).catch(() => {});
  } catch (e) {
    $('.splash-sub').textContent = 'تعذّر التحميل. تحقق من الاتصال ثم أعد فتح الصفحة.'; return;
  }
  buildPager();
  await renderPage(pageEl(current), current);
  pageEl(current).scrollIntoView({ block: 'nearest', inline: 'start' });
  onPageChange(current);
  $('#splash').style.opacity = 0; setTimeout(() => $('#splash').remove(), 300);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).catch(() => {});
  checkUpdate();
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') checkUpdate(); });
  whatsNew(document.querySelector('meta[name=app-version]')?.content || 'dev');
  setTimeout(() => { if ($('#sheet').classList.contains('hidden')) showInstall(); }, 2500);
})();
