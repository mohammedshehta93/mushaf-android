// Shared helpers: storage, data loading, Arabic text, and the screen / sheet / toast UI.
export const $ = (s, el = document) => el.querySelector(s);
export const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const AR = '٠١٢٣٤٥٦٧٨٩';
export const ar = (n) => String(n).replace(/\d/g, (d) => AR[d]);
export const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// ---------------------------------------------------------------- storage (one JSON object in localStorage)
const KEY = 'mushaf:v1';
let state = {};
try { state = JSON.parse(localStorage.getItem(KEY) || '{}'); } catch (e) { state = {}; }
export const store = {
  get: (k, d) => (k in state ? state[k] : d),
  set: (k, v) => { state[k] = v; try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} },
};

// ---------------------------------------------------------------- data
const cache = new Map();
export function json(url) {
  if (!cache.has(url)) {
    cache.set(url, fetch(url).then((r) => { if (!r.ok) throw new Error(url); return r.json(); })
      .catch((e) => { cache.delete(url); throw e; }));
  }
  return cache.get(url);
}
const fonts = new Map();
export function font(name, url) {
  if (!fonts.has(name)) {
    const f = new FontFace(name, `url(${url})`);
    fonts.set(name, f.load().then((ff) => { document.fonts.add(ff); return ff; }).catch((e) => { fonts.delete(name); throw e; }));
  }
  return fonts.get(name);
}
export let meta = null;
export async function loadMeta() { meta = await json('data/meta.json'); return meta; }

// ---------------------------------------------------------------- Arabic search normalisation (same rules as the Android app)
const MARKS = /[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]/g;
export function normalize(s) {
  return s.replace(/[٠-٩۰-۹0-9]/g, '').replace(MARKS, '')
    .replace(/[ٱأإآ]/g, 'ا').replace(/ى/g, 'ي').replace(/[ؤئء]/g, '').replace(/ة/g, 'ه').replace(/[ۥۦ]/g, '')
    .replace(/\s+/g, ' ').trim();
}

// ---------------------------------------------------------------- UI: full screens, bottom sheets, toast
const stack = [];   // open layers, so the Android/browser back button closes them
export function toast(msg, ms = 2200) {
  const t = $('#toast'); t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add('hidden'), ms);
}
function push(kind) { stack.push(kind); history.pushState({ layer: stack.length }, ''); }
let ignorePops = 0;
window.addEventListener('popstate', () => {
  if (ignorePops > 0) { ignorePops--; return; }
  const k = stack.pop();
  if (k === 'sheet') hideSheet(); else if (k === 'screen') hideScreen();
});
function hideSheet() { $('#sheet').classList.add('hidden'); $('#scrim').classList.add('hidden'); $('#sheet').innerHTML = ''; ui.onSheetClose?.(); ui.onSheetClose = null; }
function hideScreen() { $('#screen').classList.add('hidden'); $('#screen').innerHTML = ''; ui.onScreenClose?.(); ui.onScreenClose = null; }

/** Close now (synchronously), then drop its history entry without reacting to that popstate. */
function closeLayer(kind, hide) {
  const i = stack.lastIndexOf(kind);
  if (i < 0) return;
  stack.splice(i, 1); hide(); ignorePops++; history.back();
}

export const ui = {
  onSheetClose: null, onScreenClose: null,
  sheet(html) {
    const sh = $('#sheet');
    const wasOpen = !sh.classList.contains('hidden');
    sh.innerHTML = html; sh.scrollTop = 0; sh.classList.remove('hidden'); $('#scrim').classList.remove('hidden');
    if (!wasOpen) push('sheet');
    return sh;
  },
  closeSheet() { closeLayer('sheet', hideSheet); },
  /** A full-screen page. tabs: [{id,label}], render(body, tabId) fills the body. */
  screen({ title, sub = '', tabs = null, tab = null, render, actions = '' }) {
    const sc = $('#screen');
    const wasOpen = !sc.classList.contains('hidden');
    sc.innerHTML = `<div class="scr-head"><button class="icon" data-close aria-label="رجوع">→</button>
      <div class="grow"><h1>${esc(title)}</h1>${sub ? `<div class="sub">${esc(sub)}</div>` : ''}</div>${actions}</div>
      ${tabs ? `<div class="tabs">${tabs.map((t) => `<button data-tab="${t.id}">${esc(t.label)}</button>`).join('')}</div>` : ''}
      <div class="scr-body"></div>`;
    sc.classList.remove('hidden');
    if (!wasOpen) push('screen');
    const body = $('.scr-body', sc);
    $('[data-close]', sc).onclick = () => ui.closeScreen();
    const show = (id) => {
      $$('.tabs button', sc).forEach((b) => b.classList.toggle('on', b.dataset.tab === id));
      body.innerHTML = ''; body.scrollTop = 0; render(body, id);
    };
    if (tabs) $$('.tabs button', sc).forEach((b) => (b.onclick = () => show(b.dataset.tab)));
    show(tab || (tabs ? tabs[0].id : null));
    return sc;
  },
  closeScreen() { closeLayer('screen', hideScreen); },
};
$('#scrim').addEventListener('click', () => ui.closeSheet());

export const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
export const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

// ---------------------------------------------------------------- the owner's live content file (content/library.json on GitHub)
const REMOTE_LIBRARY = 'https://raw.githubusercontent.com/mohammedshehta93/mushaf-android/main/content/library.json';
let libP = null;
/** Bundled library (with audio found at build time), overridden by the live file; hide rules applied. */
export function library() {
  if (!libP) {
    libP = Promise.all([
      json('data/learn/library.json'),
      fetch(REMOTE_LIBRARY, { cache: 'no-store' }).then((r) => (r.ok ? r.json() : null)).catch(() => null),
    ]).then(([b, r]) => {
      const lib = r && Array.isArray(r.sheikhGroups) && Array.isArray(r.poemGroups) ? r : b;
      const hide = lib.hide || {};
      const hidden = (k, ...t) => (hide[k] || []).some((h) => String(h).trim() && normalize(t.join(' ')).includes(normalize(String(h))));
      const built = {};
      (b.poemGroups || []).forEach((g) => g.items.forEach((it) => { if (it.tracks) built[it.n] = it; }));
      const groups = (gs, k, name) => (gs || []).map((g) => ({ ...g, items: (g.items || []).filter((it) => !hidden(k, name(it))) })).filter((g) => g.items.length);
      return {
        hidden,
        podcastTopics: lib.podcastTopics || [],
        sheikhGroups: groups(lib.sheikhGroups, 'sheikhs', (it) => it.n),
        poemGroups: groups(lib.poemGroups, 'poems', (it) => `${it.n} ${it.a || ''}`).map((g) => ({
          ...g,
          items: g.items.map((it) => (it.url ? { ...it, tracks: [{ t: it.n, u: it.url }], srcTitle: '' }
            : built[it.n] ? { ...it, tracks: built[it.n].tracks, srcTitle: built[it.n].srcTitle } : { ...it, tracks: [] })),
        })),
      };
    }).catch((e) => { libP = null; throw e; });
  }
  return libP;
}
