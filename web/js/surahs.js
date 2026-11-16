// «مقاصد السور وأسباب النزول» (same data as the Android app: tools/surah_info.py).
import { $, ar, esc, json, normalize, ui } from './core.js';
import { app } from './app.js';

const info = () => json('data/surahs_info.json');
// «ﷺ» as words: some browsers have no font for the ligature and then break the shaping of the whole line
const words = (t) => esc(t).replace(/ﷺ/g, 'صلى الله عليه وسلم');

function reportHtml(r) {
  const short = r.st > 0 ? `… ${r.t.slice(r.st).trimStart()}` : r.t;
  return `<div style="margin-top:8px"><div style="font-size:16px;line-height:1.8;text-align:justify" data-t data-full="${esc(r.t)}">${esc(short)}</div>
    <div class="row"><span class="muted grow">${esc(r.src)}</span>${r.st > 0 ? '<button class="link" data-full-btn>النص كاملًا</button>' : ''}</div></div>`;
}
function wireFull(el) {
  el.addEventListener('click', (e) => {
    const b = e.target.closest('[data-full-btn]'); if (!b) return;
    const t = b.closest('div').parentElement.querySelector('[data-t]'); t.textContent = t.dataset.full; b.remove();
  });
}

/** The revelation reports of ayahs from..to of surah s, as an HTML card ('' when there are none). */
export async function nuzulHtml(s, from, to) {
  let d; try { d = await info(); } catch { return ''; }
  const nz = (d.surahs[s] || {}).nuzul || {};
  const parts = [];
  for (let a = from; a <= to; a++) (nz[a] || []).forEach((r) => parts.push((from !== to ? `<div class="muted">الآية ${ar(a)}</div>` : '') + reportHtml(r)));
  return parts.length ? `<div class="card" data-nuzul><b style="color:var(--gharib)">ما ورد في نزول الآية</b>${parts.join('')}</div>` : '';
}
export { wireFull };

export async function openSurahInfo(start = 0) {
  const [d, text] = await Promise.all([info(), json('data/text.json')]);
  const meta = app.meta();
  const detail = (s) => ui.screen({
    title: `سورة ${meta.surahs[s - 1].name}`, sub: `${meta.surahs[s - 1].type} · ${ar(meta.surahs[s - 1].ayahs)} آية`,
    render(body) {
      const x = d.surahs[s]; const nz = x.nuzul || {}; const keys = Object.keys(nz).map(Number).sort((a, b) => a - b);
      body.innerHTML = `${x.why.length ? `<div class="sec-title">سبب التسمية</div>${x.why.map((t) => `<p style="line-height:1.8">• ${words(t)}</p>`).join('')}` : ''}
        <div class="sec-title">من مقاصد السورة</div>${x.aims.map((t) => `<p style="line-height:1.8">• ${words(t)}</p>`).join('')}
        <p class="muted" style="font-size:12px">المصدر: ${esc(d.src)}</p>
        <div class="sec-title">ما ورد في نزول آياتها</div>
        <p class="muted">${keys.length ? 'روايات صحيحة أو حسنة من الكتب الستة تذكر نزول الآية وقصتها، بنصها ورقمها.' : 'لم يُعثر في الكتب الستة على رواية صحيحة أو حسنة تذكر سبب نزول آية من هذه السورة.'}</p>
        ${keys.map((a) => `<div class="card"><div class="quran" style="font-size:20px;line-height:2">﴿${esc(text[`${s}:${a}`])}﴾ ${ar(a)}</div>
          <button class="link" data-go="${a}">افتح في المصحف</button>${nz[a].map(reportHtml).join('')}</div>`).join('')}`;
      wireFull(body);
      body.addEventListener('click', (e) => { const g = e.target.closest('[data-go]'); if (g) { const a = +g.dataset.go; app.goTo(meta.ap[s - 1][a - 1], { s, a }); } });
    },
  });
  if (start) return detail(start);
  ui.screen({
    title: 'مقاصد السور وأسباب النزول', sub: 'موضوع كل سورة، وما صحّ في نزول آياتها',
    render(body) {
      body.innerHTML = `<input class="input" data-q placeholder="اسم السورة أو رقمها"><div data-list></div>`;
      const draw = (q) => {
        const n = normalize(q || '');
        $('[data-list]', body).innerHTML = meta.surahs.map((m, i) => i + 1).filter((s) => !n || normalize(meta.surahs[s - 1].name).includes(n) || String(s) === q.trim())
          .map((s) => { const x = d.surahs[s]; const c = Object.keys(x.nuzul || {}).length;
            return `<button class="card" style="display:block;width:100%;text-align:right" data-s="${s}"><div class="row"><b class="grow" style="color:var(--gharib)">${ar(s)}. سورة ${esc(meta.surahs[s - 1].name)}</b>${c ? `<span class="muted">${ar(c)} آية لها سبب نزول</span>` : ''}</div>
              <div style="line-height:1.7;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${words(x.aims[0] || '')}</div></button>`; }).join('');
      };
      $('[data-q]', body).oninput = (e) => draw(e.target.value);
      body.addEventListener('click', (e) => { const b = e.target.closest('[data-s]'); if (b) detail(+b.dataset.s); });
      draw('');
    },
  });
}
