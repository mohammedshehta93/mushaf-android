// Offline support: the app shell is cached per version; Quran data and fonts are cached the first time they are used
// (or all at once from Settings → «تنزيل المصحف»), and then served from the phone.
const VERSION = '__VERSION__';
const SHELL = `mushaf-shell-${VERSION}`;
const DATA = 'mushaf-data';
const SHELL_FILES = ['./', 'index.html', 'css/app.css', 'js/app.js', 'js/core.js', 'js/tools.js', 'js/prayer.js',
  'manifest.webmanifest', 'icons/icon-192.png', 'icons/apple-touch-icon.png', 'fonts/hafs.woff2', 'fonts/bsml.woff2', 'data/meta.json', 'data/reciters.json'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(SHELL_FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith('mushaf-shell-') && k !== SHELL).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;     // recitations stream from everyayah.com
  const isData = url.pathname.includes('/data/') || url.pathname.includes('/fonts/');
  if (isData) {
    // data never changes within a version: cache first
    e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(DATA).then((c) => c.put(e.request, copy)); }
      return res;
    })));
  } else {
    // app shell: network first (to get updates), cache when offline
    e.respondWith(fetch(e.request).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(SHELL).then((c) => c.put(e.request, copy)); }
      return res;
    }).catch(() => caches.match(e.request).then((r) => r || caches.match('index.html'))));
  }
});
