// Reading companions and new sections (same data as the Android app):
// intentions + one tadabbur reminder when reading starts, virtues of the Quran, explained hadiths,
// the English corner and the «what's new» note after every update.
import { $, ar, esc, store, json, normalize, ui, library } from './core.js';
import { app } from './app.js';

const yt = (q, sp) => `https://www.youtube.com/results?search_query=${encodeURIComponent(q)}${sp ? `&sp=${sp}` : ''}`;
const PLAYLISTS = 'EgIQAw%3D%3D', CHANNELS = 'EgIQAg%3D%3D';
const today = () => Math.floor(Date.now() / 864e5);
const reading = () => json('data/reading.json');

// ---------------------------------------------------------------- a hadith with its explanation
export function hadithHtml(h, big = 17) {
  const x = h.x ? `<details style="margin-top:6px"><summary class="link" style="display:inline">الشرح</summary>
      <div style="font-size:16px;line-height:1.8;text-align:justify;margin-top:6px">${esc(h.x)}</div>
      ${h.hl && h.hl.length ? `<b style="color:var(--gharib);display:block;margin-top:8px">من فوائد الحديث</b>${h.hl.map((t) => `<div style="line-height:1.7">• ${esc(t)}</div>`).join('')}` : ''}
      ${h.w && h.w.length ? `<b style="color:var(--gharib);display:block;margin-top:8px">معاني الكلمات</b>${h.w.map(([w, m]) => `<div style="line-height:1.7">${esc(w)}: ${esc(m)}</div>`).join('')}` : ''}
      <div class="muted" style="margin-top:6px">الشرح من «موسوعة الأحاديث النبوية» hadeethenc.com</div></details>` : '';
  return `${h.title ? `<b style="color:var(--gharib)">${esc(h.title)}</b>` : ''}<div style="font-size:${big}px;line-height:1.75;margin:6px 0;text-align:justify">${esc(h.t)}</div>
    <div class="muted">${esc(h.s || h.src || '')}</div>${x}`;
}

function itemHtml(it, withGo = true) {
  if (it.k === 'a') {
    return `<div class="quran" style="font-size:22px;line-height:2">﴿${esc(it.t)}﴾</div><div class="row"><span class="muted grow">${esc(it.src)}</span>
      ${withGo ? `<button class="link" data-go-s="${it.s}" data-go-a="${it.a}">افتح في المصحف</button>` : ''}</div>`;
  }
  return hadithHtml({ ...it, s: it.src, hl: it.hl });
}

function wireGo(el) {
  el.addEventListener('click', (e) => {
    const b = e.target.closest('[data-go-s]'); if (!b) return;
    const s = +b.dataset.goS, a = +b.dataset.goA;
    app.goTo(app.meta().ap[s - 1][a - 1], { s, a });
  });
}

// ---------------------------------------------------------------- intentions & tadabbur when reading starts
let started = false;
export function startReading() {
  if (started) return;
  started = true;
  const mode = store.get('intentMode', 1);
  if (mode === 2 || (mode === 1 && store.get('intentDay', 0) !== today())) openIntentions(true);
  else showTadabbur();
}

export function intentionOptionsHtml() {
  const mode = store.get('intentMode', 1);
  return `<div class="muted" style="margin-top:10px">النوايا عند بدء القراءة</div>
    <div class="chips">${[[1, 'مرة يوميًا'], [2, 'كل مرة'], [0, 'لا تظهر']].map(([v, n]) => `<button class="chip ${mode === v ? 'on' : ''}" data-im="${v}">${n}</button>`).join('')}</div>
    <button class="item" data-tad><span class="ic">${store.get('tadabburStart', true) ? '☑' : '☐'}</span>وقفة تدبّر واحدة عند بدء القراءة</button>`;
}
export function handleIntentionOption(e) {
  const m = e.target.closest('[data-im]'); if (m) { store.set('intentMode', +m.dataset.im); return true; }
  if (e.target.closest('[data-tad]')) { store.set('tadabburStart', !store.get('tadabburStart', true)); return true; }
  return false;
}

export async function openIntentions(thenTadabbur = false) {
  const r = await reading();
  ui.screen({
    title: 'نوايا قبل القراءة', sub: '«إنما الأعمال بالنيات» — متفق عليه',
    render(body) {
      const draw = () => {
        body.innerHTML = `<p class="muted" style="line-height:1.8">استحضر قبل أن تقرأ ما تستطيع من هذه النوايا؛ فكلما تعددت النوايا الصالحة في العمل الواحد تعدد أجره بإذن الله.</p>
          ${r.intentions.map((it, i) => `<details class="card"><summary style="font-size:17px;line-height:1.7"><b style="color:var(--gharib)">${ar(i + 1)}.</b> ${esc(it.n)}</summary><div style="margin-top:6px">${itemHtml(it, false)}</div></details>`).join('')}
          ${intentionOptionsHtml()}
          <button class="btn" data-start style="width:100%;margin:12px 0;font-size:17px">بسم الله، ابدأ القراءة</button>`;
      };
      draw();
      body.onclick = (e) => {
        if (handleIntentionOption(e)) return draw();
        if (e.target.closest('[data-start]')) {
          store.set('intentDay', today()); ui.closeScreen();
          if (thenTadabbur) setTimeout(showTadabbur, 350);
        }
      };
    },
  });
}

async function showTadabbur() {
  if (!store.get('tadabburStart', true)) return;
  const r = await reading(); if (!r.reminders.length) return;
  const i = store.get('tadIdx', 0); store.set('tadIdx', i + 1);
  const it = r.reminders[i % r.reminders.length];
  const card = document.createElement('div');
  card.className = 'card-float';
  card.innerHTML = `<b style="color:var(--gharib)">وقفة تدبّر · ${esc(it.h)}</b>
    ${it.k === 'a' ? `<div class="quran" style="font-size:20px;line-height:2">﴿${esc(it.t)}﴾</div><div class="muted">${esc(it.src)}</div>`
      : `<div style="font-size:15px;line-height:1.7;max-height:40vh;overflow:auto">${esc(it.t)}</div><div class="muted">${esc(it.src)}</div>`}
    <div class="row" style="justify-content:flex-end"><button class="link" data-ok>جزاك الله خيرًا</button></div>`;
  document.body.appendChild(card);
  card.querySelector('[data-ok]').onclick = () => card.remove();
}

// ---------------------------------------------------------------- virtues of the Quran
export async function openVirtues() {
  const r = await reading();
  ui.screen({
    title: 'فضل القرآن', sub: 'قراءته وحفظه وتعلّمه والعمل به',
    render(body) {
      body.innerHTML = `<button class="card" style="display:block;width:100%;text-align:right" data-int><b style="color:var(--gharib);font-size:17px">نوايا قبل القراءة ›</b><div class="muted">استحضر نواياك قبل أن تبدأ.</div></button>
        <p class="muted">الآيات من النص الرسمي لمجمع الملك فهد، والأحاديث بنصها من «رياض الصالحين» والكتب الستة مع رقم الحديث وحكمه.</p>
        ${r.virtues.map((g) => `<div class="sec-title" style="font-size:17px">${esc(g.title)}</div>${g.note ? `<p class="muted">${esc(g.note)}</p>` : ''}${g.items.map((it) => `<div class="card">${it.h ? `<b style="color:var(--gharib);display:block;margin-bottom:4px">${esc(it.h)}</b>` : ''}${itemHtml(it)}</div>`).join('')}`).join('')}`;
      wireGo(body);
      body.addEventListener('click', (e) => { if (e.target.closest('[data-int]')) openIntentions(false); });
    },
  });
}

// ---------------------------------------------------------------- explained hadiths
export async function openHadithLibrary() {
  let all = [], lib = { groups: [] };
  try { [all, lib] = await Promise.all([json('data/muslim/hadith_sharh.json'), json('data/muslim/hadith_library.json')]); } catch { /* missing in this build */ }
  const groups = lib.groups || [];
  ui.screen({
    title: 'الأحاديث المشروحة', sub: 'كل حديث صحيح أو حسن، ومعه شرحه وفوائده',
    render(body) {
      if (!all.length || !groups.length) { body.innerHTML = '<p class="muted">الأحاديث المشروحة غير متوفرة في هذه النسخة.</p>'; return; }
      body.innerHTML = groups.map((g, i) => `<button class="card" style="display:block;width:100%;text-align:right" data-g="${i}">
          <div class="row"><b class="grow" style="color:var(--gharib);font-size:18px">${esc(g.title)}</b><span class="muted">${ar(g.items.length)} حديثًا ›</span></div>
          ${g.desc ? `<div class="muted">${esc(g.desc)}</div>` : ''}</button>`).join('') + `<p class="muted">المصدر: ${esc(lib.src || '')}</p>`;
      body.onclick = (e) => { const b = e.target.closest('[data-g]'); if (b) openGroup(groups[+b.dataset.g]); };
    },
  });
  const openGroup = (g) => {
    const norm = g.items.map((i) => normalize(`${all[i].title} ${all[i].t}`));
    ui.screen({
      title: g.title, sub: `${ar(g.items.length)} حديثًا مع الشرح`,
      render(body) {
        let q = '';
        const draw = () => {
          const nq = normalize(q);
          const ks = g.items.map((_, k) => k).filter((k) => nq.length < 2 || norm[k].includes(nq));
          $('[data-list]', body).innerHTML = ks.slice(0, 200).map((k) => `<div class="card">${g.nums ? `<div class="muted">الحديث ${ar(g.nums[k])}</div>` : ''}${hadithHtml(all[g.items[k]], 16)}</div>`).join('') || '<p class="muted">لا نتائج.</p>';
        };
        body.innerHTML = '<input class="input" data-q placeholder="ابحث بكلمة من الحديث"><div data-list></div>';
        $('[data-q]', body).oninput = (e) => { q = e.target.value; draw(); };
        draw();
      },
    });
  };
}

// ---------------------------------------------------------------- English corner
export async function openEnglish() {
  const lib = await library().catch(() => null);
  const en = (lib && lib.english) || (await json('data/learn/library.json')).english || {};
  const meta = app.meta();
  let surah = 0;
  ui.screen({
    title: 'English corner', sub: 'Quran · Hadith · Lectures · Learn',
    tabs: [{ id: 'q', label: 'Quran' }, { id: 'h', label: 'Hadith' }, { id: 'l', label: 'Lectures' }, { id: 's', label: 'Learn' }],
    async render(body, tab) {
      body.dir = 'ltr'; body.style.textAlign = 'left';
      if (tab === 'q') {
        const tr = await json('data/english/saheeh.json');
        const text = await json('data/text.json');
        const draw = () => {
          if (!surah) {
            body.innerHTML = `<p class="muted">The meaning of the Quran in English — Saheeh International. The Arabic is the official text of the King Fahd Complex.</p>
              ${meta.surahs.map((s) => `<button class="item" data-s="${s.n}"><b style="width:30px;color:var(--gharib)">${s.n}</b><span class="grow"><span class="quran" style="font-size:20px">${esc(s.name)}</span><br><span class="muted">${s.ayahs} verses</span></span></button>`).join('')}`;
          } else {
            const s = meta.surahs[surah - 1];
            body.innerHTML = `<button class="link" data-back>‹ All surahs</button><h2 style="margin:4px 0">${surah}. <span class="quran">${esc(s.name)}</span></h2>
              ${Array.from({ length: s.ayahs }, (_, i) => i + 1).map((a) => `<div class="card"><div class="quran" dir="rtl" style="font-size:22px;line-height:2;text-align:right">${esc(text[`${surah}:${a}`] || '')}</div>
                <div style="font-size:16px;line-height:1.6;margin-top:6px">${a}. ${esc(tr[`${surah}:${a}`] || '')}</div><button class="link" data-go-s="${surah}" data-go-a="${a}">Open in the Mushaf</button></div>`).join('')}
              <p class="muted">Translation: Saheeh International.</p>`;
          }
          body.scrollTop = 0;
        };
        draw();
        body.onclick = (e) => {
          const b = e.target.closest('[data-s]'); if (b) { surah = +b.dataset.s; return draw(); }
          if (e.target.closest('[data-back]')) { surah = 0; return draw(); }
          const g = e.target.closest('[data-go-s]'); if (g) { const s = +g.dataset.goS, a = +g.dataset.goA; app.goTo(meta.ap[s - 1][a - 1], { s, a }); }
        };
      } else if (tab === 'h') {
        let list = [];
        try { list = await json('data/english/hadith.json'); } catch { /* */ }
        if (!list.length) { try { list = await json('data/english/nawawi40.json'); } catch { /* */ } }
        body.innerHTML = `<p class="muted">Authentic hadith with explanation, lessons and word meanings (HadeethEnc.com).</p>
          ${list.map((h) => `<div class="card">${h.title ? `<b style="color:var(--gharib)">${esc(h.title)}</b>` : ''}<div style="font-size:16px;line-height:1.6;margin:4px 0">${esc(h.t)}</div><div class="muted">${esc(h.s || '')}</div>
            ${h.x ? `<details><summary class="link" style="display:inline">Explanation</summary><div style="line-height:1.6;margin-top:6px">${esc(h.x)}</div>
              ${(h.h || []).length ? `<b style="color:var(--gharib)">Lessons</b>${h.h.map((t) => `<div>• ${esc(t)}</div>`).join('')}` : ''}</details>` : ''}</div>`).join('')}`;
      } else if (tab === 'l') {
        body.innerHTML = `<p class="muted">Choose a speaker, then the kind of content. Videos open on YouTube.</p>
          ${(en.speakers || []).map((s) => `<details class="card"><summary><b style="font-size:17px">${esc(s.n)}</b><div class="muted">${esc(s.d || '')}</div></summary>
            ${[['📚', 'Series & playlists', yt(s.q, PLAYLISTS)], ['🎙', 'Lectures', yt(`${s.q} lecture`)], ['📱', 'Short reminders', yt(`${s.q} #shorts no music`)],
              ['🎧', 'Podcast (Apple Podcasts)', `https://podcasts.apple.com/search?term=${encodeURIComponent(s.q)}`], ['▶️', 'YouTube channel', yt(s.q, CHANNELS)]]
              .map(([ic, t, u]) => `<a class="item" target="_blank" rel="noopener" href="${esc(u)}"><span class="ic">${ic}</span>${t}</a>`).join('')}</details>`).join('')}`;
      } else {
        body.innerHTML = `<p class="muted">Step-by-step learning: for new Muslims, prayer, reading the Quran, Seerah and belief. Each opens complete playlists on YouTube.</p>
          ${(en.series || []).map((g) => `<div class="sec-title">${esc(g.title)}</div>${g.items.map((t) => `<a class="item" target="_blank" rel="noopener" href="${esc(yt(t.q, PLAYLISTS))}"><span class="ic">📖</span>${esc(t.n)}</a>`).join('')}`).join('')}`;
      }
    },
  });
}

// ---------------------------------------------------------------- what's new (once after every update)
export async function whatsNew(version) {
  const seen = store.get('seenVersion', null);
  const hadPage = store.get('page', null) !== null;
  store.set('seenVersion', version);
  const existingUser = seen !== null || hadPage;
  if (!existingUser || seen === version || version === 'dev' || version === 'test' || version.startsWith('__')) return;
  let d; try { d = await json('data/whatsnew.json'); } catch { return; }
  ui.sheet(`<div style="font-weight:700;font-size:18px">${esc(d.title || 'تحديث جديد')}</div>
    ${(d.items || []).map((t) => `<div style="line-height:1.8;margin:6px 0">• ${esc(t)}</div>`).join('')}
    <button class="btn" style="width:100%;margin-top:10px" onclick="document.querySelector('#scrim').click()">تمام</button>`);
}
