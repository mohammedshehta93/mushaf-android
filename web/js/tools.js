// أدوات المسلم: المواقيت، القبلة، الأذكار، السبحة، التقويم الهجري، رمضان، حديث اليوم، الأسماء الحسنى، الزكاة.
import { $, $$, ar, esc, store, json, normalize, toast, ui, isIOS, library } from './core.js';
import { METHODS, NAMES, method, compute, qibla, distanceToKaaba, fmt, CITIES } from './prayer.js';
import { app } from './app.js';
import { hadithHtml, openVirtues, openHadithLibrary, openEnglish } from './extra.js';
import { openHadithCheck } from './hadith.js';
import { openSurahInfo } from './surahs.js';
import { openNotebook } from './tadabbur.js';

// ---------------------------------------------------------------- Hijri (Umm al-Qura calendar built into the browser)
const HMONTHS = ['محرّم', 'صفر', 'ربيع الأول', 'ربيع الآخر', 'جمادى الأولى', 'جمادى الآخرة', 'رجب', 'شعبان', 'رمضان', 'شوّال', 'ذو القعدة', 'ذو الحجة'];
let hijriFmt = null;
export function hijri(date = new Date()) {
  const d = new Date(date.getTime() + store.get('hijriAdj', 0) * 864e5);
  if (!hijriFmt) {
    for (const cal of ['islamic-umalqura', 'islamic-civil', 'islamic']) {
      try {
        const f = new Intl.DateTimeFormat(`en-u-ca-${cal}-nu-latn`, { day: 'numeric', month: 'numeric', year: 'numeric' });
        if (f.resolvedOptions().calendar.startsWith('islamic')) { hijriFmt = f; break; }
      } catch (e) {}
    }
  }
  const parts = hijriFmt.formatToParts(d);
  const g = (t) => parseInt(parts.find((p) => p.type === t).value, 10);
  return { y: g('year'), m: g('month'), d: g('day') };
}
const hijriText = (h) => `${ar(h.d)} ${HMONTHS[h.m - 1]} ${ar(h.y)}هـ`;
const OCCASIONS = [
  ['رأس السنة الهجرية', 1, 1, ''], ['يوم تاسوعاء', 1, 9, '«لئن بقيت إلى قابل لأصومن التاسع» (مسلم 1134)'], ['يوم عاشوراء', 1, 10, 'صيامه يكفّر السنة التي قبله (مسلم 1162)'], ['بداية شهر رمضان', 9, 1, ''],
  ['العشر الأواخر من رمضان', 9, 21, 'التماس ليلة القدر'], ['عيد الفطر', 10, 1, ''], ['بداية عشر ذي الحجة', 12, 1, '«ما العمل في أيام العشر أفضل من العمل في هذه» (البخاري 969)'],
  ['يوم عرفة', 12, 9, 'صيامه يكفّر السنة التي قبله والتي بعده (مسلم 1162)'], ['عيد الأضحى', 12, 10, ''],
];
function findHijri(y, m, d) {
  const start = Date.now() - 400 * 864e5;
  for (let i = 0; i < 800; i++) { const t = new Date(start + i * 864e5); const h = hijri(t); if (h.y === y && h.m === m && h.d === d) return t; }
  return null;
}

// ---------------------------------------------------------------- prayer times
const loc = () => store.get('loc', null);
export function times(date = new Date()) {
  const l = loc(); if (!l) return null;
  const t = compute(date, l.lat, l.lng, -date.getTimezoneOffset() / 60, method(store.get('method', 'umm_alqura')),
    store.get('hanafi', false) ? 2 : 1, hijri(date).m === 9);
  const adj = store.get('padj', [0, 0, 0, 0, 0, 0]);
  return t.map((h, i) => h + adj[i] / 60);
}
export function nextPrayer(now = new Date()) {
  for (let d = 0; d < 2; d++) {
    const day = new Date(now.getTime() + d * 864e5);
    const t = times(day); if (!t) return null;
    const mid = new Date(day); mid.setHours(0, 0, 0, 0);
    for (let i = 0; i < 6; i++) {
      if (i === 1 || isNaN(t[i])) continue;
      const at = mid.getTime() + t[i] * 3600e3;
      if (at > now.getTime()) return { i, at, h: t[i] };
    }
  }
  return null;
}
const countdown = (ms) => { const m = Math.max(0, Math.floor(ms / 60000)); const h = Math.floor(m / 60); return h ? `${ar(h)} س ${ar(m % 60)} د` : `${ar(m)} دقيقة`; };
export function prayerChip() { const n = nextPrayer(); return n ? ` · ${NAMES[n.i]} ${fmt(n.h).split(' ')[0]}` : ''; }

// ---------------------------------------------------------------- home hub
const HOME_SECTIONS = [
  ['القرآن الكريم', [['index', '☰', 'فهرس المصحف'], ['virtues', '💚', 'فضل القرآن'], ['surahinfo', '📖', 'مقاصد السور وأسباب النزول'], ['search', '🔍', 'البحث في القرآن'], ['tajweed', '🗣', 'تعلّم التجويد'], ['notebook', '📝', 'دفتر التدبّر'], ['marks', '🔖', 'العلامات']]],
  ['الصلاة والعبادات اليومية', [['prayer', '🕰', 'مواقيت الصلاة'], ['qibla', '🧭', 'القبلة'], ['azkar', '📿', 'الأذكار'], ['tasbih', '👆', 'السبحة'],
    ['hijri', '🗓', 'التقويم الهجري'], ['ramadan', '🌙', 'رمضان'], ['zakat', '🧮', 'الزكاة']]],
  ['طلب العلم', [['sheikhs', '🎓', 'الشيوخ والدروس'], ['poems', '📜', 'المتون والمنظومات'], ['hadiths', '❝', 'الأحاديث المشروحة'], ['hadithcheck', '✔', 'تحقّق من حديث'], ['names', '✨', 'أسماء الله الحسنى']]],
  ['English', [['english', '🌍', 'English corner']]],
  ['استمع', [['radio', '📻', 'إذاعات القرآن']]],
  ['التطبيق', [['settings', '⚙', 'الإعدادات'], ['share', '↗', 'شارك التطبيق']]],
];

export function openHome() {
  ui.screen({
    title: 'تِبيان', sub: `${hijriText(hijri())} · ${new Date().toLocaleDateString('ar', { weekday: 'long', day: 'numeric', month: 'long' })}`,
    async render(body) {
      const n = nextPrayer(), t = times();
      const now = new Date(), h = now.getHours() + now.getMinutes() / 60;
      let az = ['أذكار الصباح', '☀'];
      if (t && h >= t[3] && h < t[5] + 1) az = ['أذكار المساء', '🌙'];
      else if (t && (h >= t[5] + 1 || h < t[0])) az = ['أذكار النوم', '🌙'];
      const friday = now.getDay() === 5;
      const lp = app.current(), [ls] = app.meta().pageRanges[lp - 1];
      body.innerHTML = `
        <button class="card row" style="width:100%" data-go="mushaf"><span style="font-size:26px">📖</span>
          <span class="grow" style="text-align:right"><b style="font-size:20px">المصحف</b><br><span class="muted">تابع القراءة: سورة ${esc(app.meta().surahs[ls - 1].name)} · صفحة ${ar(lp)}</span></span>
          <span style="color:var(--gharib)">اقرأ ›</span></button>
        <button class="card" style="display:block;width:100%;text-align:right" data-go="prayer">
          ${n ? `<div style="font-size:20px;font-weight:700;color:var(--gharib)">${NAMES[n.i]} بعد ${countdown(n.at - Date.now())}</div>
            <div class="muted">${esc(loc().name)}</div>
            <div class="row" style="justify-content:space-between;margin-top:10px">${t.map((x, i) => (i === 1 ? '' : `<div style="text-align:center"><div style="font-size:12px;${i === n.i ? 'font-weight:700;color:var(--gharib)' : ''}">${NAMES[i]}</div><div style="font-size:13px">${fmt(x).split(' ')[0]}</div></div>`)).join('')}</div>`
            : '<b>مواقيت الصلاة</b><div class="muted">اضغط لتحديد موقعك وعرض المواقيت.</div>'}
        </button>
        <button class="card row" style="width:100%" data-azkar="${az[0]}"><span style="font-size:22px">${az[1]}</span><b class="grow" style="text-align:right">${az[0]}</b><span style="color:var(--gharib)">ابدأ ›</span></button>
        ${HOME_SECTIONS.map(([title, tiles]) => `<div class="sec-title">${title}</div><div class="grid g4">${tiles.map(([id, ic, l]) => `<button class="tile" data-go="${id}"><span>${ic}</span>${l}</button>`).join('')}</div>`).join('')}
        ${friday ? '<button class="card" style="display:block;width:100%;text-align:right;margin-top:12px" data-kahf><b>يوم الجمعة</b><div>اقرأ سورة الكهف وأكثِر من الصلاة على النبي ﷺ — اضغط لفتح السورة.</div></button>' : ''}
        <div class="card" data-hadith style="margin-top:12px"></div>`;
      let off = 0;
      const list = await json('data/muslim/hadith_daily.json');
      const drawHadith = () => {
        const day = Math.floor(Date.now() / 864e5);
        const hd = list[(day * 7919 + off) % list.length];
        $('[data-hadith]', body).innerHTML = `<b style="color:var(--gharib)">حديث اليوم</b>${hadithHtml(hd)}
          <div class="row"><button class="link" data-more>حديث آخر</button><button class="link" data-sh>مشاركة</button></div>`;
        $('[data-more]', body).onclick = () => { off++; drawHadith(); };
        $('[data-sh]', body).onclick = () => (navigator.share ? navigator.share({ text: `${hd.t}\n— ${hd.s}` }).catch(() => {}) : null);
      };
      drawHadith();
      body.onclick = (e) => {
        const a = e.target.closest('[data-azkar]'); if (a) return openAzkar(a.dataset.azkar);
        if (e.target.closest('[data-kahf]')) return app.goTo(293);
        const g = e.target.closest('[data-go]'); if (!g) return;
        ({
          prayer: () => openPrayer(), qibla: openQibla, azkar: () => openAzkar(), tasbih: openTasbih, hijri: openHijri,
          ramadan: openRamadan, names: openNames, zakat: openZakat, radio: () => openRadio(0), nasheed: () => openRadio(1),
          sheikhs: openSheikhs, poems: openPoems, tajweed: openTajweed, virtues: openVirtues, hadiths: openHadithLibrary, hadithcheck: openHadithCheck, surahinfo: () => openSurahInfo(), notebook: openNotebook, mushaf: () => app.goTo(app.current()), english: openEnglish, settings: app.openSettings, tafsir: () => app.openTafsir?.(), index: () => app.openIndex(), search: app.openSearch,
          marks: () => app.openIndex('marks'), share: app.shareApp,
        })[g.dataset.go]?.();
      };
    },
  });
}

// ---------------------------------------------------------------- prayer screen
export function openPrayer(tab) {
  ui.screen({
    title: 'مواقيت الصلاة', sub: loc() ? loc().name : 'حدّد موقعك أولًا', tab: tab || (loc() ? 'today' : 'settings'),
    tabs: [{ id: 'today', label: 'اليوم' }, { id: 'track', label: 'صلواتي' }, { id: 'settings', label: 'الإعدادات' }],
    render(body, id) {
      if (id === 'today') {
        const t = times();
        if (!t) { body.innerHTML = '<p class="muted" style="text-align:center;margin-top:40px">اختر مدينتك من تبويب «الإعدادات».</p>'; return; }
        const n = nextPrayer();
        body.innerHTML = `<div class="muted">${hijriText(hijri())}</div>
          ${n ? `<div class="card"><div class="muted">الصلاة القادمة</div><div style="font-size:22px;font-weight:700;color:var(--gharib)">${NAMES[n.i]} — بعد ${countdown(n.at - Date.now())}</div></div>` : ''}
          <table class="times">${t.map((h, i) => `<tr class="${n && n.i === i ? 'next' : ''}"><td>${NAMES[i]}</td><td style="text-align:left">${fmt(h)}</td></tr>`).join('')}</table>
          <p class="muted" style="line-height:1.7">طريقة الحساب: ${esc(method(store.get('method', 'umm_alqura')).name)}. قارن بمواقيت مسجدك وعدّل بالدقائق من الإعدادات إن لزم.
          ${isIOS ? '<br>تنبيهات الأذان متاحة في تطبيق الأندرويد؛ في الآيفون تظهر المواقيت والعدّاد هنا.' : ''}</p>`;
      } else if (id === 'track') {
        const log = store.get('plog', {});
        const day = (d) => new Date(Date.now() - d * 864e5).toISOString().slice(0, 10);
        const P = ['الفجر', 'الظهر', 'العصر', 'المغرب', 'العشاء'];
        const draw = () => {
          body.innerHTML = `<p class="muted">علّم الصلوات التي أدّيتها:</p><table class="times" style="font-size:14px"><tr><td></td>${P.map((p) => `<td style="font-size:12px;text-align:center">${p}</td>`).join('')}</tr>
            ${[...Array(10).keys()].map((d) => `<tr><td style="font-size:14px">${d === 0 ? 'اليوم' : d === 1 ? 'أمس' : new Date(Date.now() - d * 864e5).toLocaleDateString('ar', { weekday: 'long' })}</td>
              ${P.map((_, i) => `<td style="text-align:center"><button data-d="${day(d)}" data-i="${i}" style="font-size:22px">${(log[day(d)] || 0) >> i & 1 ? '☑' : '☐'}</button></td>`).join('')}</tr>`).join('')}</table>`;
        };
        draw();
        body.onclick = (e) => { const b = e.target.closest('[data-d]'); if (!b) return; log[b.dataset.d] = (log[b.dataset.d] || 0) ^ (1 << +b.dataset.i); store.set('plog', log); draw(); };
      } else {
        const m = store.get('method', 'umm_alqura'), adj = store.get('padj', [0, 0, 0, 0, 0, 0]);
        body.innerHTML = `<b>الموقع</b><div class="muted">${loc() ? `${esc(loc().name)} (${loc().lat.toFixed(3)}، ${loc().lng.toFixed(3)})` : 'لم يُحدَّد بعد'}</div>
          <div class="row" style="gap:8px;margin:8px 0"><button class="btn" data-geo>📍 موقعي الحالي</button></div>
          <input class="input" placeholder="أو ابحث عن مدينتك" data-cq><div data-cities></div>
          <b style="display:block;margin-top:14px">طريقة الحساب</b>
          ${METHODS.map((x) => `<button class="item" data-m="${x.id}"><span class="ic">${x.id === m ? '◉' : '○'}</span>${esc(x.name)}</button>`).join('')}
          <button class="item" data-hanafi><span class="ic">${store.get('hanafi', false) ? '☑' : '☐'}</span>حساب العصر على المذهب الحنفي</button>
          <b style="display:block;margin-top:14px">تعديل بالدقائق</b>
          ${NAMES.map((nm, i) => `<div class="row" style="padding:4px 0"><span class="grow">${nm}</span><button class="icon" data-adj="${i}" data-v="-1">−</button><span style="width:40px;text-align:center">${ar(adj[i])}</span><button class="icon" data-adj="${i}" data-v="1">+</button></div>`).join('')}
          <b style="display:block;margin-top:14px">التاريخ الهجري (تعديل بالأيام)</b>
          <div class="chips">${[-2, -1, 0, 1, 2].map((v) => `<button class="chip ${store.get('hijriAdj', 0) === v ? 'on' : ''}" data-h="${v}">${v > 0 ? '+' : ''}${ar(v)}</button>`).join('')}</div>`;
        const drawCities = (q) => {
          const nq = normalize(q);
          $('[data-cities]', body).innerHTML = nq ? CITIES.filter((c) => normalize(c[0]).includes(nq)).map((c) => `<button class="item" data-city="${esc(c[0])}">${esc(c[0])}</button>`).join('') : '';
        };
        $('[data-cq]', body).oninput = (e) => drawCities(e.target.value);
        body.onclick = (e) => {
          const t = e.target;
          const re = () => openPrayer('settings');
          if (t.closest('[data-geo]')) {
            if (!navigator.geolocation) return toast('المتصفح لا يدعم تحديد الموقع');
            toast('جارٍ تحديد موقعك…');
            navigator.geolocation.getCurrentPosition((p) => {
              const { latitude: la, longitude: lo } = p.coords;
              const near = CITIES.reduce((b, c) => ((c[1] - la) ** 2 + (c[2] - lo) ** 2 < (b[1] - la) ** 2 + (b[2] - lo) ** 2 ? c : b));
              const close = Math.abs(near[1] - la) < 0.35 && Math.abs(near[2] - lo) < 0.35;
              store.set('loc', { name: close ? near[0] : 'موقعي الحالي', lat: la, lng: lo }); re();
            }, () => toast('تعذّر تحديد الموقع — اختر مدينتك بالبحث'), { enableHighAccuracy: false, timeout: 15000, maximumAge: 3600e3 });
          }
          const c = t.closest('[data-city]'); if (c) { const x = CITIES.find((k) => k[0] === c.dataset.city); store.set('loc', { name: x[0], lat: x[1], lng: x[2] }); re(); }
          const mm = t.closest('[data-m]'); if (mm) { store.set('method', mm.dataset.m); re(); }
          if (t.closest('[data-hanafi]')) { store.set('hanafi', !store.get('hanafi', false)); re(); }
          const a = t.closest('[data-adj]'); if (a) { adj[+a.dataset.adj] += +a.dataset.v; store.set('padj', adj); re(); }
          const hh = t.closest('[data-h]'); if (hh) { store.set('hijriAdj', +hh.dataset.h); re(); }
        };
      }
    },
  });
}

// ---------------------------------------------------------------- qibla
function openQibla() {
  const l = loc();
  ui.screen({
    title: 'اتجاه القبلة', sub: l ? l.name : '',
    render(body) {
      if (!l) { body.innerHTML = '<p class="muted" style="text-align:center;margin-top:40px">حدّد موقعك من «مواقيت الصلاة ← الإعدادات» أولًا.</p>'; return; }
      const q = qibla(l.lat, l.lng);
      body.innerHTML = `<div style="text-align:center;font-size:20px;font-weight:700" data-msg>اضغط «تشغيل البوصلة»</div>
        <div class="compass"><svg viewBox="-100 -100 200 200" width="100%" height="100%" data-dial style="transition:transform .15s">
          <circle r="96" fill="var(--frameFill)" stroke="var(--frame)" stroke-width="3"/>
          ${[...Array(72).keys()].map((i) => `<line x1="0" y1="-96" x2="0" y2="${i % 18 === 0 ? -84 : i % 6 === 0 ? -89 : -93}" stroke="${i === 0 ? '#B0303F' : 'var(--muted)'}" stroke-width="${i % 18 === 0 ? 2.5 : 1}" transform="rotate(${i * 5})"/>`).join('')}
          <text y="-70" text-anchor="middle" font-size="12" fill="#B0303F">ش</text>
          <g transform="rotate(${q})"><line y1="0" y2="-58" stroke="var(--marker)" stroke-width="5"/><polygon points="0,-82 -9,-56 9,-56" fill="var(--marker)"/><rect x="-7" y="-98" width="14" height="14" fill="var(--ink)"/></g>
          <circle r="5" fill="var(--ink)"/></svg>
          <div style="position:absolute;top:-10px;left:50%;width:4px;height:22px;background:var(--ink);transform:translateX(-50%)"></div></div>
        <div style="text-align:center" class="muted">القبلة ${ar(Math.round(q))}° من الشمال · المسافة إلى الكعبة ${ar(Math.round(distanceToKaaba(l.lat, l.lng)))} كم</div>
        <div style="text-align:center;margin-top:14px"><button class="btn" data-start>تشغيل البوصلة</button></div>
        <p class="muted" style="text-align:center">ضع الهاتف أفقيًا على كفّك وابتعد عن المعادن.</p>`;
      const dial = $('[data-dial]', body), msg = $('[data-msg]', body);
      const onHeading = (heading) => {
        dial.style.transform = `rotate(${-heading}deg)`;
        let d = ((q - heading + 540) % 360) - 180;
        msg.textContent = Math.abs(d) < 4 ? 'أنت متجه إلى القبلة ✓' : d > 0 ? `استدر يمينًا ${ar(Math.round(Math.abs(d)))}°` : `استدر يسارًا ${ar(Math.round(Math.abs(d)))}°`;
        msg.style.color = Math.abs(d) < 4 ? 'var(--gharib)' : 'var(--ink)';
      };
      const handler = (e) => {
        if (e.webkitCompassHeading != null) onHeading(e.webkitCompassHeading);
        else if (e.absolute && e.alpha != null) onHeading(360 - e.alpha);
      };
      $('[data-start]', body).onclick = async () => {
        try {
          if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) {
            if ((await DeviceOrientationEvent.requestPermission()) !== 'granted') return toast('يلزم السماح بالوصول للبوصلة');
          }
          window.addEventListener('deviceorientationabsolute', handler); window.addEventListener('deviceorientation', handler);
          $('[data-start]', body).classList.add('hidden'); msg.textContent = 'حرّك الهاتف…';
          ui.onScreenClose = () => { window.removeEventListener('deviceorientationabsolute', handler); window.removeEventListener('deviceorientation', handler); };
        } catch (e) { toast('البوصلة غير متاحة على هذا الجهاز'); }
      };
    },
  });
}

// ---------------------------------------------------------------- azkar
async function openAzkar(start) {
  const all = await json('data/muslim/azkar.json');
  const today = new Date().toDateString();
  let prog = store.get('azkarProg', {}); if (prog.day !== today) prog = { day: today, c: {} };
  const save = () => store.set('azkarProg', prog);
  const featured = ['أذكار الصباح', 'أذكار المساء', 'الأذكار بعد السلام من الصلاة', 'أذكار النوم', 'أذكار الاستيقاظ من النوم'];
  const openCat = (title) => {
    const cat = all.find((c) => c.t === title); if (!cat) return;
    ui.screen({
      title, sub: 'حصن المسلم', actions: '<button class="link" style="color:var(--onBar)" data-reset>من جديد</button>',
      render(body) {
        let showDone = false;
        const draw = () => {
          const isDone = (i) => (prog.c[`${title}#${i}`] || 0) >= cat.i[i].c;
          const done = cat.i.filter((z, i) => isDone(i)).length;
          // «إخفاء الذكر بعد إتمامه»: a completed dhikr leaves the list like a task ticked off; all come back the next day
          const hide = store.get('azkarHide', true) && !showDone;
          const all = done === cat.i.length;
          body.innerHTML = `<div class="progress"><i style="width:${(done / cat.i.length) * 100}%"></i></div>
            <div class="muted">أنجزت ${ar(done)} من ${ar(cat.i.length)} — اضغط على الذكر بعد قوله</div>
            ${all ? `<div class="zikr done" style="text-align:center"><div style="font-size:30px;color:var(--gharib)">✓</div><b>أتممتَ «${esc(title)}»</b><div class="muted">تعود من جديد غدًا</div></div>` : ''}` +
            cat.i.map((z, i) => {
              const n = prog.c[`${title}#${i}`] || 0, ok = n >= z.c;
              if (ok && hide) return '';
              return `<div class="zikr ${ok ? 'done' : ''}" data-i="${i}"><div class="t">${esc(z.z).replace(/\n/g, '<br>')}</div>
              ${z.d ? `<div style="color:var(--gharib);font-size:13px;margin-top:8px;line-height:1.6">${esc(z.d)}</div>` : ''}
              <div class="row" style="margin-top:8px"><span class="muted grow">${esc(z.r)}</span><span class="counter">${ok ? '✓' : ar(z.c - n)}</span></div></div>`;
            }).join('') +
            (store.get('azkarHide', true) && done ? `<button class="btn o" style="width:100%;margin-top:8px" data-show>${showDone ? 'إخفاء ما أتممته' : `عرض ما أتممته (${ar(done)})`}</button>` : '');
        };
        draw();
        body.onclick = (e) => {
          if (e.target.closest('[data-show]')) { showDone = !showDone; return draw(); }
          const z = e.target.closest('[data-i]'); if (!z) return;
          const k = `${title}#${z.dataset.i}`, max = cat.i[+z.dataset.i].c;
          if ((prog.c[k] || 0) >= max) return;
          const day = new Date().toDateString(); if (prog.day !== day) prog = { day, c: {} };     // a new day while open
          prog.c[k] = (prog.c[k] || 0) + 1; save(); if (navigator.vibrate) navigator.vibrate(prog.c[k] >= max ? 30 : 8);
          const sc = $('.scr-body'), y = sc.scrollTop;
          if (prog.c[k] >= max && store.get('azkarHide', true) && !showDone) {
            z.style.transition = 'opacity .35s, transform .35s'; z.style.opacity = '0'; z.style.transform = 'scale(.96)';
            setTimeout(() => { draw(); sc.scrollTop = y; }, 350);
          } else { draw(); sc.scrollTop = y; }
        };
        $('[data-reset]', $('#screen')).onclick = () => { for (const k of Object.keys(prog.c)) if (k.startsWith(`${title}#`)) delete prog.c[k]; save(); draw(); };
      },
    });
  };
  if (start) return openCat(start);
  ui.screen({
    title: 'الأذكار', sub: 'حصن المسلم',
    render(body) {
      body.innerHTML = `<div class="grid">${featured.map((t) => `<button class="tile" data-c="${esc(t)}"><span>📿</span>${esc(t.replace(/^(الأذكار|أذكار) /, ''))}</button>`).join('')}</div>
        <input class="input" placeholder="ابحث عن دعاء: السفر، المطر، الهم…" data-q style="margin:12px 0"><div data-l></div>`;
      const draw = (q) => {
        const nq = normalize(q);
        $('[data-l]', body).innerHTML = all.filter((c) => !featured.includes(c.t) && (!nq || normalize(c.t).includes(nq) || c.i.some((z) => normalize(z.z).includes(nq))))
          .map((c) => `<button class="item" data-c="${esc(c.t)}">${esc(c.t)}</button>`).join('');
      };
      draw(''); $('[data-q]', body).oninput = (e) => draw(e.target.value);
      body.onclick = (e) => { const c = e.target.closest('[data-c]'); if (c) openCat(c.dataset.c); };
    },
  });
}

// ---------------------------------------------------------------- tasbih
const PHRASES = ['سُبْحَانَ اللهِ', 'الْحَمْدُ للهِ', 'اللهُ أَكْبَرُ', 'لَا إِلَهَ إِلَّا اللهُ', 'أَسْتَغْفِرُ اللهَ', 'اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ', 'سُبْحَانَ اللهِ وَبِحَمْدِهِ', 'لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللهِ'];
function openTasbih() {
  ui.screen({
    title: 'السبحة',
    render(body) {
      const st = store.get('tasbih', { n: 0, target: 33, p: 0, day: '', today: 0, total: 0 });
      const draw = () => {
        const reached = st.n >= st.target;
        body.innerHTML = `<div class="chips">${PHRASES.map((p, i) => `<button class="chip ${st.p === i ? 'on' : ''}" data-p="${i}">${p}</button>`).join('')}</div>
          <div class="chips">${[33, 99, 100, 1000].map((t) => `<button class="chip ${st.target === t ? 'on' : ''}" data-t="${t}">${ar(t)}</button>`).join('')}</div>
          <div class="quran" style="text-align:center;font-size:28px">${PHRASES[st.p]}</div>
          <button class="tasbih ${reached ? 'reached' : ''}" data-tap><div style="font-size:64px;font-weight:700">${ar(st.n)}</div><div>من ${ar(st.target)}</div></button>
          <div style="text-align:center"><button class="link" data-reset>تصفير</button></div>
          <p class="muted" style="text-align:center">اليوم ${ar(st.today)} · الإجمالي ${ar(st.total)}</p>`;
      };
      draw();
      body.onclick = (e) => {
        const t = e.target;
        if (t.closest('[data-tap]')) {
          const d = new Date().toDateString(); if (st.day !== d) { st.day = d; st.today = 0; }
          st.n++; st.today++; st.total++;
          if (navigator.vibrate) navigator.vibrate(st.n % st.target === 0 ? 120 : 10);
        }
        const p = t.closest('[data-p]'); if (p) { st.p = +p.dataset.p; st.n = 0; }
        const tg = t.closest('[data-t]'); if (tg) { st.target = +tg.dataset.t; st.n = 0; }
        if (t.closest('[data-reset]')) st.n = 0;
        store.set('tasbih', st); draw();
      };
    },
  });
}

// ---------------------------------------------------------------- Hijri calendar
function openHijri() {
  const today = hijri();
  ui.screen({
    title: 'التقويم الهجري', sub: hijriText(today),
    render(body) {
      const first = new Date(Date.now() - (today.d - 1) * 864e5);
      const days = [...Array(30).keys()].map((i) => new Date(first.getTime() + i * 864e5)).filter((d) => hijri(d).m === today.m);
      const pad = (first.getDay() + 1) % 7; // Saturday first
      const cell = (d) => {
        const h = hijri(d), isT = h.d === today.d, sp = OCCASIONS.some((o) => o[1] === h.m && o[2] === h.d) || (h.d >= 13 && h.d <= 15);
        return `<div style="background:${isT ? 'var(--gharib)' : sp ? 'var(--hl)' : 'var(--frameFill)'};color:${isT ? 'var(--paper)' : 'inherit'};border-radius:10px;padding:5px 0;text-align:center">
          <b>${ar(h.d)}</b><div style="font-size:9px;opacity:.75">${d.toLocaleDateString('ar', { day: 'numeric', month: 'short' })}</div></div>`;
      };
      const ups = OCCASIONS.map((o) => {
        let y = today.y; if (o[1] < today.m || (o[1] === today.m && o[2] < today.d)) y++;
        return [o, findHijri(y, o[1], o[2])];
      }).filter((x) => x[1]).sort((a, b) => a[1] - b[1]);
      body.innerHTML = `<h3 style="text-align:center;margin:6px 0">${HMONTHS[today.m - 1]} ${ar(today.y)}</h3>
        <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:4px">${['سبت', 'أحد', 'اثنين', 'ثلاثاء', 'أربعاء', 'خميس', 'جمعة'].map((d) => `<div class="muted" style="text-align:center;font-size:11px">${d}</div>`).join('')}
        ${'<div></div>'.repeat(pad)}${days.map(cell).join('')}</div>
        <p class="muted">المظلل: مناسبات أو الأيام البيض (١٣–١٥). يمكن تعديل التاريخ بيوم أو يومين من إعدادات المواقيت.</p>
        <h3>المناسبات القادمة</h3>
        ${ups.map(([o, d]) => { const left = Math.max(0, Math.ceil((d - Date.now()) / 864e5));
          return `<div class="item"><span class="grow"><b>${o[0]}</b><br><span class="muted">${ar(o[2])} ${HMONTHS[o[1] - 1]} · ${d.toLocaleDateString('ar', { weekday: 'long', day: 'numeric', month: 'long' })}${o[3] ? ' · ' + o[3] : ''}</span></span><span style="color:var(--gharib)">${left ? `بعد ${ar(left)} يوم` : 'اليوم'}</span></div>`; }).join('')}`;
    },
  });
}

// ---------------------------------------------------------------- Ramadan
function openRamadan() {
  const today = hijri();
  const y = today.m <= 9 ? today.y : today.y + 1;
  const start = findHijri(y, 9, 1);
  ui.screen({
    title: `رمضان ${ar(y)}هـ`, sub: start ? `يبدأ تقريبًا ${start.toLocaleDateString('ar', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}` : '',
    tabs: [{ id: 'imsak', label: 'الإمساكية' }, { id: 'fast', label: 'صيامي' }],
    render(body, id) {
      if (id === 'imsak') {
        if (!loc() || !start) { body.innerHTML = '<p class="muted" style="text-align:center;margin-top:40px">حدّد موقعك من «مواقيت الصلاة» لعرض الإمساكية.</p>'; return; }
        const rows = [];
        for (let i = 0; i < 30; i++) {
          const d = new Date(start.getTime() + i * 864e5); if (hijri(d).m !== 9) break;
          const t = times(d);
          rows.push(`<tr><td>${ar(i + 1)}</td><td>${d.toLocaleDateString('ar', { day: 'numeric', month: 'numeric' })}</td><td>${fmt(t[0] - 10 / 60).split(' ')[0]}</td><td>${fmt(t[0]).split(' ')[0]}</td><td>${fmt(t[4]).split(' ')[0]}</td></tr>`);
        }
        body.innerHTML = `<table class="times" style="font-size:14px"><tr style="font-weight:700"><td>اليوم</td><td>التاريخ</td><td>الإمساك</td><td>الفجر</td><td>المغرب</td></tr>${rows.join('')}</table>
          <p class="muted">الإمساك قبل الفجر بعشر دقائق احتياطًا؛ والعبرة بأذان الفجر.</p>`;
      } else {
        const f = store.get('fast', {}); const k = String(y); f[k] ||= [];
        const draw = () => { body.innerHTML = `<p>صمت ${ar(f[k].length)} يومًا</p><div style="display:grid;grid-template-columns:repeat(6,1fr);gap:6px">${[...Array(30).keys()].map((i) => `<button class="chip ${f[k].includes(i + 1) ? 'on' : ''}" data-d="${i + 1}">${ar(i + 1)}</button>`).join('')}</div>`; };
        draw();
        body.onclick = (e) => { const b = e.target.closest('[data-d]'); if (!b) return; const d = +b.dataset.d; f[k] = f[k].includes(d) ? f[k].filter((x) => x !== d) : [...f[k], d]; store.set('fast', f); draw(); };
      }
    },
  });
}

// ---------------------------------------------------------------- the Beautiful Names
async function openNames() {
  const [names, text] = await Promise.all([json('data/muslim/names.json'), json('data/text.json')]);
  const meta = app.meta();
  ui.screen({
    title: 'أسماء الله الحسنى', sub: '«ولله الأسماء الحسنى فادعوه بها»',
    render(body) {
      body.innerHTML = `<p class="muted" style="line-height:1.8">قال النبي ﷺ: «إِنَّ لِلَّهِ تِسْعَةً وَتِسْعِينَ اسْمًا مِائَةً إِلاَّ وَاحِدًا، مَنْ أَحْصَاهَا دَخَلَ الْجَنَّةَ» (صحيح البخاري 7392). أما سرد الأسماء بهذا الترتيب فهو في جامع الترمذي (3507) وإسناده ضعيف (الألباني)، ولم يثبت تعيينها في حديث صحيح. والآيات تحت كل اسم: مواضع الاسم أو ما دلّ على معناه.</p>` + names.map((n, i) => `<details style="border-bottom:1px solid rgba(128,128,128,.15);padding:8px 0"><summary class="row" style="list-style:none">
        <span class="muted" style="width:32px">${ar(i + 1)}</span><span class="quran grow" style="color:var(--gharib);font-size:26px">${esc(n.n)}</span><span class="muted">${ar(n.r.length)} آية</span></summary>
        ${n.r.slice(0, 6).map(([s, a]) => `<button class="item" style="display:block" data-s="${s}" data-a="${a}"><div class="muted">سورة ${esc(meta.surahs[s - 1].name)} · ${ar(a)}</div><div class="quran" style="font-size:19px">${esc(text[`${s}:${a}`] || '')}</div></button>`).join('')}</details>`).join('');
      body.onclick = (e) => { const b = e.target.closest('[data-s]'); if (b) { const s = +b.dataset.s, a = +b.dataset.a; app.goTo(meta.ap[s - 1][a - 1], { s, a }); } };
    },
  });
}

// ---------------------------------------------------------------- zakat
function openZakat() {
  ui.screen({
    title: 'حاسبة الزكاة', sub: 'المال والذهب والفضة وعروض التجارة',
    render(body) {
      const z = store.get('zakat', { gp: '', sp: '', cur: 'د.ب' });
      const f = (k, l) => `<label style="display:block;margin:6px 0"><span class="muted">${l}</span><input class="input" inputmode="decimal" data-k="${k}" value="${esc(z[k] || '')}"></label>`;
      body.innerHTML = `${f('gp', 'سعر جرام الذهب عيار ٢٤')}${f('sp', 'سعر جرام الفضة')}${f('cur', 'العملة')}<hr>
        ${f('cash', 'النقود في البيت والبنك')}${f('gold', 'الذهب (جرام)')}
        <div class="chips">${[24, 22, 21, 18].map((k) => `<button class="chip ${(z.karat || 21) === k ? 'on' : ''}" data-karat="${k}">عيار ${ar(k)}</button>`).join('')}</div>
        ${f('silver', 'الفضة (جرام)')}${f('trade', 'قيمة عروض التجارة')}${f('owed', 'ديون لك مرجوّة السداد')}${f('debts', 'ديون عليك حالّة (تُخصم)')}
        <div class="chips"><button class="chip ${!z.silverN ? 'on' : ''}" data-n="0">النصاب بالذهب (٨٥ جم)</button><button class="chip ${z.silverN ? 'on' : ''}" data-n="1">بالفضة (٥٩٥ جم)</button></div>
        <div class="card" data-res></div>
        <p class="muted" style="line-height:1.7">الحساب تقريبي للحالات الشائعة؛ ويشترط مرور حول هجري على بلوغ النصاب. في ذهب الزينة المستعمل خلاف بين العلماء، وللمسائل الخاصة اسأل أهل العلم.</p>`;
      const num = (k) => parseFloat(String(z[k] || '').replace(/[٠-٩]/g, (d) => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace('٫', '.')) || 0;
      const money = (v) => `${v.toLocaleString('ar', { maximumFractionDigits: 2 })} ${z.cur || ''}`;
      const calc = () => {
        const gp = num('gp'), sp = num('sp');
        const total = num('cash') + (num('gold') * (z.karat || 21)) / 24 * gp + num('silver') * sp + num('trade') + num('owed') - num('debts');
        const nisab = z.silverN ? 595 * sp : 85 * gp;
        $('[data-res]', body).innerHTML = !nisab ? `أدخل سعر ${z.silverN ? 'الفضة' : 'الذهب'} لحساب النصاب.`
          : `<div>مجموع المال الزكوي: ${money(Math.max(0, total))}</div><div class="muted">النصاب: ${money(nisab)}</div>
             <div style="font-size:20px;font-weight:700;color:var(--gharib);margin-top:6px">${total >= nisab ? `الزكاة الواجبة (٢٫٥٪): ${money(total * 0.025)}` : 'لم يبلغ المال النصاب؛ لا زكاة فيه.'}</div>`;
        store.set('zakat', { gp: z.gp, sp: z.sp, cur: z.cur, karat: z.karat, silverN: z.silverN });
      };
      body.oninput = (e) => { const k = e.target.dataset.k; if (k) { z[k] = e.target.value; calc(); } };
      body.onclick = (e) => {
        const k = e.target.closest('[data-karat]'); if (k) { z.karat = +k.dataset.karat; $$('[data-karat]', body).forEach((b) => b.classList.toggle('on', b === k)); calc(); }
        const n = e.target.closest('[data-n]'); if (n) { z.silverN = n.dataset.n === '1'; $$('[data-n]', body).forEach((b) => b.classList.toggle('on', b === n)); calc(); }
      };
      calc();
    },
  });
}

// ---------------------------------------------------------------- «استمع»: live Quran radios + spoken poems (played in the app)
const yt = (q, sp) => `https://www.youtube.com/results?search_query=${encodeURIComponent(q)}${sp ? `&sp=${sp}` : ''}`;
const noMusic = (q) => (q.includes('بدون موسيقى') ? q : `${q} بدون موسيقى`);
const radio = { audio: new Audio(), url: null, name: '' };
radio.audio.preload = 'none';
radio.audio.addEventListener('error', () => { if (radio.url) toast('تعذّر تشغيل البث — تحقق من الاتصال أو جرّب إذاعة أخرى'); });
document.addEventListener('mushaf:recite', () => { radio.audio.pause(); });
function radioPlay(u, n, resume = false) {
  if (radio.url === u) { if (radio.audio.paused) radio.audio.play().catch(() => {}); else radio.audio.pause(); return; }
  app.stopAudio?.();
  radio.url = u; radio.name = n; radio.resume = resume; radio.audio.src = u;
  if (resume) { const at = store.get('pos', {})[u]; if (at) radio.audio.currentTime = at; }
  if ('mediaSession' in navigator) navigator.mediaSession.metadata = new MediaMetadata({ title: n, artist: resume ? 'المتون والقصائد' : 'إذاعة مباشرة', album: 'المصحف' });
  radio.audio.play().catch(() => toast('اضغط مرة أخرى للتشغيل'));
}
radio.audio.addEventListener('loadedmetadata', () => { if (radio.resume) { const at = store.get('pos', {})[radio.url]; if (at && Math.abs(radio.audio.currentTime - at) > 2) radio.audio.currentTime = at; } });
setInterval(() => { if (radio.resume && radio.url && !radio.audio.paused) { const p = store.get('pos', {}); p[radio.url] = radio.audio.currentTime; store.set('pos', p); } }, 10000);
radio.audio.addEventListener('ended', () => { if (radio.resume) { const p = store.get('pos', {}); delete p[radio.url]; store.set('pos', p); } });

async function openRadio(startTab = 0) {
  let radios = []; let lib = { poemGroups: [], hidden: () => false };
  try { lib = await library(); } catch { /* offline */ }
  try { radios = (await json('data/listen/radios.json')).filter((r) => r.u.startsWith('https://') && !lib.hidden('radios', r.n)); } catch { /* list missing in this build */ }
  const poems = lib.poemGroups.map((g) => ({ title: g.title, items: g.items.filter((it) => it.tracks.length) })).filter((g) => g.items.length);
  ui.screen({
    title: 'استمع', sub: 'إذاعات القرآن، والقصائد والمتون بالصوت',
    render(body) {
      let tab = startTab, filter = '', open = null;
      const draw = () => {
        const tabs = `<div class="chips">${['إذاعات مباشرة', 'قصائد ومتون'].map((t, i) => `<button class="chip ${tab === i ? 'on' : ''}" data-tab="${i}">${t}</button>`).join('')}</div>`;
        const playing = radio.url && !radio.audio.paused;
        const now = radio.url ? `<div class="card row"><span style="font-size:22px">${playing ? '🔊' : '⏸'}</span><b class="grow">${esc(radio.name)}</b><button class="chip" data-toggle>${playing ? 'إيقاف مؤقت' : 'تشغيل'}</button><button class="chip" data-stop>إغلاق</button></div>` : '';
        if (tab === 0) {
          const shown = radios.filter((r) => !filter || r.n.includes(filter.trim()));
          body.innerHTML = `${tabs}${now}
            ${radios.length ? `<input class="input" data-f placeholder="ابحث باسم القارئ أو الإذاعة" value="${esc(filter)}"><p class="muted">بث مباشر من إذاعات mp3quran.net — يحتاج إنترنت، ويستمر عند قفل الشاشة.</p>` : '<p class="muted">قائمة الإذاعات غير متوفرة في هذه النسخة.</p>'}
            ${shown.map((r) => `<button class="item" data-u="${esc(r.u)}" data-n="${esc(r.n)}"><span class="ic">${radio.url === r.u ? '🔊' : '📻'}</span><span class="grow" style="${radio.url === r.u ? 'font-weight:700;color:var(--gharib)' : ''}">${esc(r.n)}</span></button>`).join('')}`;
          const f = $('[data-f]', body);
          if (f) f.oninput = () => { filter = f.value; const pos = f.selectionStart; draw(); const g = $('[data-f]', body); g.focus(); g.setSelectionRange(pos, pos); };
        } else {
          body.innerHTML = `${tabs}${now}<p class="muted">قصائد ومتون مُلقاة بالصوت، تُشغَّل هنا وتكمل من حيث توقفت.</p>
            ${poems.length ? poems.map((g) => `<div class="sec-title">${esc(g.title)}</div>${g.items.map((p) => `<div class="card"><button class="row" style="width:100%;text-align:right" data-p="${esc(p.n)}"><span style="font-size:20px">${p.tracks.some((t) => t.u === radio.url) ? '🔊' : '🎧'}</span><span class="grow"><b>${esc(p.n)}</b><div class="muted">${esc([p.a, p.tracks.length > 1 ? `${ar(p.tracks.length)} مقاطع` : ''].filter(Boolean).join(' · '))}</div></span></button>
              ${open === p.n || p.tracks.length === 1 ? '' : ''}${open === p.n ? p.tracks.map((t, i) => `<button class="item" data-u="${esc(t.u)}" data-n="${esc(`${p.n}${p.tracks.length > 1 ? ` — ${t.t}` : ''}`)}" data-live="0"><span class="ic">${radio.url === t.u ? '🔊' : '▶'}</span><span class="grow">${p.tracks.length > 1 ? `${ar(i + 1)}. ${esc(t.t)}` : 'تشغيل'}</span></button>`).join('') + (p.srcTitle ? `<div class="muted">التسجيل: ${esc(p.srcTitle)}</div>` : '') : ''}</div>`).join('')}`).join('')
              : '<p class="muted">لا توجد تسجيلات صوتية بعد.</p>'}`;
        }
      };
      draw();
      const sync = () => { if (document.body.contains(body)) draw(); };
      radio.audio.onplay = sync; radio.audio.onpause = sync;
      body.addEventListener('click', (e) => {
        const t = e.target.closest('[data-tab]'); if (t) { tab = +t.dataset.tab; return draw(); }
        const pp = e.target.closest('[data-p]'); if (pp) { open = open === pp.dataset.p ? null : pp.dataset.p; return draw(); }
        const r = e.target.closest('[data-u]'); if (r) { radioPlay(r.dataset.u, r.dataset.n, r.dataset.live === '0'); return draw(); }
        if (e.target.closest('[data-toggle]')) return radioPlay(radio.url, radio.name);
        if (e.target.closest('[data-stop]')) { radio.audio.pause(); radio.audio.removeAttribute('src'); radio.audio.load(); radio.url = null; return draw(); }
      });
    },
  });
}

// ---------------------------------------------------------------- طلب العلم: الشيوخ، المتون، التجويد
const choice = (title, sub, rows) => ui.sheet(`<div style="font-weight:700;font-size:19px">${esc(title)}</div>${sub ? `<div class="muted">${esc(sub)}</div>` : ''}
  ${rows.map(([ic, t, d, href]) => `<a class="card row" style="display:flex;text-decoration:none;color:inherit;margin:8px 0" target="_blank" rel="noopener" href="${esc(href)}"><span style="font-size:22px">${ic}</span><span class="grow"><b>${esc(t)}</b><div class="muted">${esc(d)}</div></span></a>`).join('')}`);

async function openSheikhs() {
  const lib = await library();
  ui.screen({
    title: 'الشيوخ والدروس', sub: 'دروس صوتية، ومعها الفيديو على يوتيوب',
    render(body) {
      body.innerHTML = `<p class="muted">اختر الشيخ لتسمع دروسه صوتًا أو تشاهدها فيديو على يوتيوب. البحث في المواعظ والمقاطع القصيرة مقيّد بـ«بدون موسيقى».</p>
        ${lib.sheikhGroups.map((g) => `<div class="sec-title">${esc(g.title)}</div>${g.items.map((x) => `<button class="card" style="display:block;width:100%;text-align:right" data-q="${esc(x.q)}" data-n="${esc(x.n)}"><b>${esc(x.n)}</b><div class="muted">${esc(x.d || '')}</div></button>`).join('')}`).join('')}`;
      body.onclick = (e) => {
        const b = e.target.closest('[data-q]'); if (!b) return; const q = b.dataset.q;
        choice(b.dataset.n, '', [
          ['🎧', 'استمع لدروسه (صوت)', 'دروسه الصوتية في Apple Podcasts', `https://podcasts.apple.com/search?term=${encodeURIComponent(q)}`],
          ['📚', 'دروس وسلاسل منظّمة (فيديو)', 'قوائم تشغيل مرتبة (شرح كتاب أو سلسلة كاملة)', yt(q, 'EgIQAw%3D%3D')],
          ['🎙', 'محاضرات ومواعظ (فيديو)', 'خطب ودروس منفردة', yt(noMusic(`${q} محاضرة`))],
          ['📱', 'مقاطع قصيرة', 'Shorts — بدون موسيقى', yt(noMusic(`${q} #shorts`))],
          ['▶️', 'القناة على يوتيوب', 'ابحث عن القناة الرسمية واشترك فيها', yt(q, 'EgIQAg%3D%3D')],
        ]);
      };
    },
  });
}

async function openPoems() {
  const lib = await library();
  ui.screen({
    title: 'المتون والمنظومات', sub: 'قصائد تعلّم العقيدة والآداب والتجويد والفقه',
    render(body) {
      body.innerHTML = `<p class="muted">مرتبة حسب العلم. 🎧 = لها تسجيل صوتي يُشغَّل هنا. اضغط على المنظومة لتسمعها أو تسمع شرحها أو تقرأ نصها.</p>
        ${lib.poemGroups.map((g) => `<div class="sec-title">${esc(g.title)}</div>${g.items.map((x) => `<button class="card" style="display:block;width:100%;text-align:right" data-q="${esc(x.q)}" data-n="${esc(x.n)}" data-a="${esc(x.a)}"><b>${esc(x.n)}${x.tracks && x.tracks.length ? ' 🎧' : ''}</b><div class="muted">${esc([x.a, x.d].filter(Boolean).join(' — '))}</div></button>`).join('')}`).join('')}`;
      body.onclick = (e) => {
        const b = e.target.closest('[data-q]'); if (!b) return; const q = b.dataset.q;
        const poem = lib.poemGroups.flatMap((g) => g.items).find((x) => x.n === b.dataset.n);
        if (poem && poem.tracks.length) {
          const sh = choice(b.dataset.n, b.dataset.a, []);
          sh.insertAdjacentHTML('beforeend', `<div class="card"><b>🎧 استمع للمنظومة</b>${poem.tracks.map((t, i) => `<button class="item" data-tu="${esc(t.u)}" data-tn="${esc(poem.n + (poem.tracks.length > 1 ? ` — ${t.t}` : ''))}"><span class="ic">▶</span>${poem.tracks.length > 1 ? `${ar(i + 1)}. ${esc(t.t)}` : 'تشغيل'}</button>`).join('')}</div>`);
          sh.onclick = (ev) => { const x = ev.target.closest('[data-tu]'); if (x) { radioPlay(x.dataset.tu, x.dataset.tn, true); toast('جارٍ التشغيل — تحكّم فيه من «استمع»'); } };
          return sh.insertAdjacentHTML('beforeend', [
            ['📚', 'شرح المنظومة', 'سلاسل شرح كاملة للمشايخ', yt(`شرح ${q}`, 'EgIQAw%3D%3D')],
            ['📖', 'نص المنظومة', 'ابحث عن النص المضبوط بالشكل', `https://www.google.com/search?q=${encodeURIComponent(`نص ${q} مشكول`)}`],
          ].map(([ic, t, d, href]) => `<a class="card row" style="display:flex;text-decoration:none;color:inherit;margin:8px 0" target="_blank" rel="noopener" href="${esc(href)}"><span style="font-size:22px">${ic}</span><span class="grow"><b>${esc(t)}</b><div class="muted">${esc(d)}</div></span></a>`).join(''));
        }
        choice(b.dataset.n, b.dataset.a, [
          ['📚', 'شرح المنظومة', 'سلاسل شرح كاملة للمشايخ', yt(`شرح ${q}`, 'EgIQAw%3D%3D')],
          ['📖', 'نص المنظومة', 'ابحث عن النص المضبوط بالشكل', `https://www.google.com/search?q=${encodeURIComponent(`نص ${q} مشكول`)}`],
        ]);
      };
    },
  });
}

function exampleHtml(x, big) {
  const [s, a, w, f, t] = x;
  return `<button class="ex quran" style="font-size:${big ? 34 : 24}px" data-s="${s}" data-a="${a}">${esc(w.slice(0, f))}<span style="color:#c62828">${esc(w.slice(f, t))}</span>${esc(w.slice(t))}</button>`;
}

async function openTajweed() {
  const d = await json('data/learn/tajweed.json');
  const meta = app.meta();
  ui.screen({
    title: 'تعلّم التجويد', sub: 'شرح ميسّر برواية حفص',
    render(body) {
      const done = new Set(store.get('tajweedDone', []));
      const list = () => {
        body.innerHTML = `<p class="muted">دروس قصيرة بالترتيب، وكل حكم معه أمثلة حقيقية من المصحف ملوّنة (اضغط المثال لفتح الآية)، واختبار في آخر الدرس. التجويد يُتلقّى بالمشافهة، فاستمع للقارئ وطبّق مع معلّم.</p>
          ${d.lessons.map((l, i) => `<button class="card row" style="width:100%;text-align:right" data-l="${i}"><span class="num">${ar(i + 1)}</span><span class="grow"><b>${esc(l.title)}</b>${l.quiz ? '<div class="muted">فيه اختبار</div>' : ''}</span>${done.has(l.id) ? '<span style="color:var(--gharib)">✓</span>' : ''}</button>`).join('')}`;
        body.scrollTop = 0;
      };
      const lesson = (i) => {
        const l = d.lessons[i];
        body.innerHTML = `<button class="link" data-back>‹ كل الدروس</button><h2 style="margin:4px 0">${esc(l.title)}</h2><div class="muted">الدرس ${ar(i + 1)} من ${ar(d.lessons.length)}</div>
          ${l.sections.map((sec) => `<div class="card"><b style="color:var(--gharib);font-size:17px">${esc(sec.h)}</b><div style="font-size:17px;line-height:1.9;margin-top:4px">${esc(sec.t).replace(/﴿([^﴾]*)﴾/g, '<span class="quran" style="font-size:20px">﴿$1﴾</span>')}</div>
            ${[sec.ex, sec.ex2].filter(Boolean).map((r) => `<div class="muted" style="margin-top:8px">أمثلة من المصحف${sec.ex2 ? ` — ${esc(d.ruleNames[r])}` : ''}:</div><div class="exs">${(d.examples[r] || []).slice(0, 6).map((x) => exampleHtml(x)).join('')}</div>`).join('')}</div>`).join('')}
          <a class="card row" style="display:flex;text-decoration:none;color:inherit" target="_blank" rel="noopener" href="${yt(`شرح ${l.title} تجويد`, 'EgIQAw%3D%3D')}">📚 <span class="grow">استمع لشرح هذا الدرس</span></a>
          ${l.quiz ? '<button class="btn" data-quiz style="width:100%">اختبر نفسك</button>' : ''}
          <button class="link" data-next style="width:100%">${i + 1 < d.lessons.length ? 'الدرس التالي ›' : 'انتهت الدروس ✓'}</button>`;
        body.scrollTop = 0;
        done.add(l.id); store.set('tajweedDone', [...done]);
        body.onclick = (e) => {
          if (e.target.closest('[data-back]')) { list(); body.onclick = onList; return; }
          if (e.target.closest('[data-next]')) { if (i + 1 < d.lessons.length) lesson(i + 1); else { list(); body.onclick = onList; } return; }
          if (e.target.closest('[data-quiz]')) return quiz(i);
          const x = e.target.closest('[data-s]'); if (x) { const s = +x.dataset.s, a = +x.dataset.a; app.goTo(meta.ap[s - 1][a - 1], { s, a }); }
        };
      };
      const quiz = (i) => {
        const l = d.lessons[i];
        const shuffle = (arr) => arr.map((v) => [Math.random(), v]).sort((p, q) => p[0] - q[0]).map((p) => p[1]);
        const qs = shuffle(l.quiz.flatMap((r) => shuffle(d.examples[r] || []).slice(0, 4).map((x) => [r, x]))).slice(0, 10);
        let k = 0, score = 0, picked = null;
        const draw = () => {
          if (k >= qs.length) {
            body.innerHTML = `<h2 style="color:var(--gharib)">النتيجة: ${ar(score)} من ${ar(qs.length)}</h2><p>${score * 10 >= qs.length * 8 ? 'ممتاز! انتقل للدرس التالي.' : 'راجع الدرس وأعد المحاولة؛ التكرار يثبّت الأحكام.'}</p>
              <button class="btn" data-again style="width:100%">أعد الاختبار</button><button class="link" data-lesson style="width:100%">رجوع للدرس</button>`;
            return;
          }
          const [rule, x] = qs[k];
          body.innerHTML = `<button class="link" data-lesson>‹ رجوع للدرس</button><div class="muted">السؤال ${ar(k + 1)} من ${ar(qs.length)}</div>
            <p style="font-weight:600;font-size:17px">ما حكم الجزء الملوّن؟</p><div style="text-align:center">${exampleHtml(x, true)}</div>
            <div class="muted">سورة ${esc(meta.surahs[x[0] - 1].name)} · الآية ${ar(x[1])}</div>
            ${l.quiz.map((r) => `<button class="card" style="display:block;width:100%;text-align:right;font-size:17px;${picked ? (r === rule ? 'background:rgba(40,167,69,.2)' : r === picked ? 'background:rgba(198,40,40,.2)' : '') : ''}" data-r="${r}">${esc(d.ruleNames[r])}</button>`).join('')}
            ${picked ? `<p style="font-weight:700;color:${picked === rule ? 'var(--gharib)' : '#c62828'}">${picked === rule ? 'إجابة صحيحة ✓' : `الصواب: ${esc(d.ruleNames[rule])}`}</p><button class="btn" data-nextq style="width:100%">${k + 1 < qs.length ? 'التالي' : 'النتيجة'}</button>` : ''}`;
        };
        draw();
        body.onclick = (e) => {
          if (e.target.closest('[data-lesson]')) return lesson(i);
          if (e.target.closest('[data-again]')) return quiz(i);
          if (e.target.closest('[data-nextq]')) { k++; picked = null; return draw(); }
          const r = e.target.closest('[data-r]'); if (r && !picked) { picked = r.dataset.r; if (picked === qs[k][0]) score++; draw(); }
        };
      };
      const onList = (e) => { const b = e.target.closest('[data-l]'); if (b) lesson(+b.dataset.l); };
      list(); body.onclick = onList;
    },
  });
}
