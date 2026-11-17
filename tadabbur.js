// «وقفة تدبّر» and «دفتر التدبّر» (same content as the Android app). Everything here can be turned off in Settings.
import { $, ar, esc, store, json, ui, toast } from './core.js';
import { app } from './app.js';

const words = (t) => esc(t).replace(/ﷺ/g, 'صلى الله عليه وسلم');
const notes = () => store.get('notes', {});
const saveNotes = (n) => store.set('notes', n);

export const repeatLabel = () => {
  const n = store.get('tdRepeat', 5), g = store.get('tdGap', 3);
  return `ردّد الآية (${n ? `${ar(n)} مرات` : 'حتى توقفه'}${g ? `، وقفة ${ar(g)} ث` : ''})`;
};

/** Settings: the tadabbur options and the azkar option. */
export function tadabburOptionsHtml() {
  const chk = (k) => (store.get(k, true) ? '☑' : '☐');
  const rep = store.get('tdRepeat', 5), gap = store.get('tdGap', 3);
  return `<button class="item" data-t="tdSheet"><span class="ic">${chk('tdSheet')}</span>«وقفة تدبّر» في قائمة الآية</button>
    ${store.get('tdSheet', true) ? `<button class="item" data-t="tdSimilar"><span class="ic">${chk('tdSimilar')}</span>إظهار الآيات المتشابهة الألفاظ فيها</button>` : ''}
    <div class="muted" style="margin-top:10px">ترديد الآية: عدد المرات</div>
    <div class="chips">${[3, 5, 10, 0].map((v) => `<button class="chip ${rep === v ? 'on' : ''}" data-tdr="${v}">${v ? ar(v) : 'حتى أوقفه'}</button>`).join('')}</div>
    <div class="muted" style="margin-top:10px">وقفة صامتة بين كل ترديد</div>
    <div class="chips">${[0, 3, 5, 10].map((v) => `<button class="chip ${gap === v ? 'on' : ''}" data-tdg="${v}">${v ? `${ar(v)} ث` : 'بدون'}</button>`).join('')}</div>
    <div class="muted" style="margin-top:10px">الأذكار</div>
    <button class="item" data-t="azkarHide"><span class="ic">${chk('azkarHide')}</span>إخفاء الذِّكر بعد إتمام عدده (يرجع كل يوم)</button>`;
}
export function handleTadabburOption(e) {
  const r = e.target.closest('[data-tdr]'); if (r) { store.set('tdRepeat', +r.dataset.tdr); return true; }
  const g = e.target.closest('[data-tdg]'); if (g) { store.set('tdGap', +g.dataset.tdg); return true; }
  return false;
}

/** «دفتر التدبّر» for one ayah: what he reflected on, and what he will do with it. */
export async function noteSheet(s, a, after) {
  const text = (await json('data/text.json'))[`${s}:${a}`];
  const n = notes()[`${s}:${a}`] || {};
  const sh = ui.sheet(`<div style="font-weight:700">دفتر التدبّر · سورة ${esc(app.meta().surahs[s - 1].name)} ${ar(a)}</div>
    <div class="quran" style="max-height:20vh;overflow:auto;font-size:20px">${esc(text)}</div>
    <textarea class="input" data-x rows="4" placeholder="اكتب تدبّرك أو فائدة…" style="width:100%;margin-top:8px">${esc(n.x || '')}</textarea>
    <input class="input" data-w placeholder="ماذا ستعمل بها؟ (اختياري) — مثلًا: أصل رحمي هذا الأسبوع" value="${esc(n.w || '')}" style="width:100%;margin-top:8px">
    <div class="row" style="gap:10px;margin-top:12px"><button class="btn grow" data-save>حفظ</button>${n.t ? '<button class="btn o" data-del>حذف</button>' : ''}</div>`);
  $('[data-save]', sh).onclick = () => {
    const x = $('[data-x]', sh).value.trim(), w = $('[data-w]', sh).value.trim();
    const all = notes();
    if (!x && !w) delete all[`${s}:${a}`];
    else all[`${s}:${a}`] = { s, a, x, w, d: n.w === w ? !!n.d : false, t: Date.now() };
    saveNotes(all); toast('تم الحفظ'); ui.closeSheet(); if (after) after();
  };
  const del = $('[data-del]', sh);
  if (del) del.onclick = () => { const all = notes(); delete all[`${s}:${a}`]; saveNotes(all); ui.closeSheet(); if (after) after(); };
}

function noteHtml(n) {
  if (!n) return '';
  return `${n.x ? `<div style="line-height:1.7">${esc(n.x)}</div>` : ''}
    ${n.w ? `<div class="row"><span class="grow" style="color:var(--gharib)">${n.d ? '✓ عملتُ بها: ' : 'نويت: '}${esc(n.w)}</span>
      <button class="link" data-done="${n.s}:${n.a}">${n.d ? 'تراجع' : 'عملتُ بها'}</button></div>` : ''}`;
}
function toggleDone(key) { const all = notes(); if (all[key]) { all[key].d = !all[key].d; saveNotes(all); } }

/** «وقفة تدبّر» on one ayah. Links to the tafsir and the surah page instead of repeating them. */
export async function openTadabbur(s, a) {
  const [text, r, info, sim] = await Promise.all([json('data/text.json'), json('data/reading.json'),
    json('data/surahs_info.json').catch(() => ({ surahs: {} })),
    store.get('tdSimilar', true) ? json('data/similar.json').catch(() => ({})) : {}]);
  const meta = app.meta(), name = meta.surahs[s - 1].name;
  const x = info.surahs[s] || {}, aim = (x.aims || [])[0], hasNuzul = !!(x.nuzul || {})[a];
  const td = r.tadabbur || { questions: [] };
  const similar = sim[`${s}:${a}`] || [];
  ui.screen({
    title: 'وقفة تدبّر', sub: `سورة ${name} · الآية ${ar(a)}`,
    render(body) {
      const draw = () => {
        const n = notes()[`${s}:${a}`];
        body.innerHTML = `<div class="card quran" style="font-size:24px;line-height:2">﴿${esc(text[`${s}:${a}`])}﴾</div>
          ${aim ? `<div class="card"><b style="color:var(--gharib)">من مقاصد سورة ${esc(name)}</b><div style="line-height:1.8;margin-top:4px">${words(aim)}</div>
            <div class="row"><button class="link" data-info>مقاصد السورة كاملة</button>${hasNuzul ? '<button class="link" data-taf>ما ورد في نزولها</button>' : ''}</div></div>` : ''}
          ${td.questions.length ? `<div class="card"><b style="color:var(--gharib)">اسأل نفسك</b>${td.questions.map((q, i) => `<div style="line-height:1.8;margin-top:6px">${ar(i + 1)}. ${esc(q)}</div>`).join('')}
            <button class="link" data-taf>افتح التفسير</button></div>` : ''}
          <div class="card"><button class="btn" style="width:100%" data-rep>🔁 ${repeatLabel()}</button>
            ${td.repeat ? `<div style="line-height:1.7;margin-top:8px;font-size:15px">${esc(td.repeat.t)}</div><div class="muted">${esc(td.repeat.src)}</div>` : ''}</div>
          <div class="card"><b style="color:var(--gharib)">دفتر التدبّر</b>
            ${n ? noteHtml(n) : '<div class="muted" style="margin-top:4px">اكتب ما وقع في قلبك من هذه الآية، وما ستعمل به.</div>'}
            <button class="btn o" style="width:100%;margin-top:8px" data-note>${n ? 'تعديل' : 'اكتب تدبّرك'}</button></div>
          ${similar.length ? `<div class="sec-title">آيات متشابهة الألفاظ</div>${similar.map(([k, w]) => {
            const [s2, a2] = k.split(':').map(Number);
            return `<button class="card" style="display:block;width:100%;text-align:right" data-go="${k}"><div class="quran" style="font-size:19px;line-height:1.9">${esc(text[k])}</div>
              <div class="muted">سورة ${esc(meta.surahs[s2 - 1].name)} · الآية ${ar(a2)} — تشترك في ${ar(w)} ${w <= 10 ? 'كلمات' : 'كلمة'} متتالية</div></button>`;
          }).join('')}` : ''}`;
      };
      draw();
      body.onclick = (e) => {
        const t = e.target;
        if (t.closest('[data-info]')) return import('./surahs.js').then((m) => m.openSurahInfo(s));
        if (t.closest('[data-taf]')) return app.openTafsir(s, a);
        if (t.closest('[data-rep]')) { app.repeatAyah(s, a, store.get('tdRepeat', 5), store.get('tdGap', 3) * 1000); return ui.closeScreen(); }
        if (t.closest('[data-note]')) return noteSheet(s, a, draw);
        const d = t.closest('[data-done]'); if (d) { toggleDone(d.dataset.done); return draw(); }
        const g = t.closest('[data-go]');
        if (g) { const [s2, a2] = g.dataset.go.split(':').map(Number); app.goTo(app.pageOf(s2, a2), { s: s2, a: a2 }); }
      };
    },
  });
}

/** «دفتر التدبّر»: everything he wrote, with «عملتُ بها». */
export async function openNotebook() {
  const text = await json('data/text.json');
  const meta = app.meta();
  ui.screen({
    title: 'دفتر التدبّر', sub: 'ما كتبته عند الآيات، وما نويت العمل به',
    render(body) {
      const draw = () => {
        const list = Object.values(notes()).sort((x, y) => x.s - y.s || x.a - y.a);
        body.innerHTML = list.length ? list.map((n) => `<div class="card">
            <div class="row"><b class="grow" style="color:var(--gharib)">سورة ${esc(meta.surahs[n.s - 1].name)} · الآية ${ar(n.a)}</b>
              <button class="link" data-open="${n.s}:${n.a}">الآية</button><button class="link" data-edit="${n.s}:${n.a}">تعديل</button></div>
            ${!n.x && !n.w ? `<div class="quran">${esc(text[`${n.s}:${n.a}`])}</div>` : ''}${noteHtml(n)}</div>`).join('')
          : '<p class="muted" style="text-align:center;line-height:1.9;margin-top:40px">لم تكتب شيئًا بعد.<br>اضغط مطولًا على آية واختر «وقفة تدبّر»، واكتب تدبّرك وما ستعمل به.</p>';
      };
      draw();
      body.onclick = (e) => {
        const d = e.target.closest('[data-done]'); if (d) { toggleDone(d.dataset.done); return draw(); }
        const ed = e.target.closest('[data-edit]'); if (ed) { const [s, a] = ed.dataset.edit.split(':').map(Number); return noteSheet(s, a, draw); }
        const o = e.target.closest('[data-open]'); if (o) { const [s, a] = o.dataset.open.split(':').map(Number); app.goTo(app.pageOf(s, a), { s, a }); }
      };
    },
  });
}
