// Prayer times (PrayTimes algorithm) and Qibla — a direct port of data/PrayerTimes.kt in the Android app.
export const METHODS = [
  { id: 'umm_alqura', name: 'أم القرى (مكة المكرمة)', fajr: 18.5, isha: 0, ishaMin: 90, ishaRamadan: 120 },
  { id: 'gulf', name: 'منطقة الخليج', fajr: 19.5, isha: 0, ishaMin: 90 },
  { id: 'egypt', name: 'الهيئة المصرية العامة للمساحة', fajr: 19.5, isha: 17.5 },
  { id: 'mwl', name: 'رابطة العالم الإسلامي', fajr: 18, isha: 17 },
  { id: 'kuwait', name: 'الكويت', fajr: 18, isha: 17.5 },
  { id: 'qatar', name: 'قطر', fajr: 18, isha: 0, ishaMin: 90 },
  { id: 'dubai', name: 'دبي', fajr: 18.2, isha: 18.2 },
  { id: 'karachi', name: 'جامعة العلوم الإسلامية بكراتشي', fajr: 18, isha: 18 },
  { id: 'isna', name: 'أمريكا الشمالية (ISNA)', fajr: 15, isha: 15 },
  { id: 'turkey', name: 'تركيا (ديانت)', fajr: 18, isha: 17 },
];
export const NAMES = ['الفجر', 'الشروق', 'الظهر', 'العصر', 'المغرب', 'العشاء'];
export const method = (id) => METHODS.find((m) => m.id === id) || METHODS[0];

const rad = (d) => (d * Math.PI) / 180, deg = (r) => (r * 180) / Math.PI;
const dsin = (d) => Math.sin(rad(d)), dcos = (d) => Math.cos(rad(d)), dtan = (d) => Math.tan(rad(d));
const fix = (a, b) => { const r = a - b * Math.floor(a / b); return r < 0 ? r + b : r; };

function julian(y, m, d) {
  if (m <= 2) { y -= 1; m += 12; }
  const a = Math.floor(y / 100), b = 2 - a + Math.floor(a / 4);
  return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
}
function sunPosition(jd) {
  const d = jd - 2451545.0;
  const g = fix(357.529 + 0.98560028 * d, 360), q = fix(280.459 + 0.98564736 * d, 360);
  const l = fix(q + 1.915 * dsin(g) + 0.02 * dsin(2 * g), 360);
  const e = 23.439 - 0.00000036 * d;
  const ra = fix(deg(Math.atan2(dcos(e) * dsin(l), dcos(l))) / 15, 24);
  return { decl: deg(Math.asin(dsin(e) * dsin(l))), eqt: q / 15 - ra };
}

/** Times as hours from local midnight: [fajr, sunrise, dhuhr, asr, maghrib, isha]. */
export function compute(date, lat, lng, tz, m, asrFactor = 1, ramadan = false) {
  const jDate = julian(date.getFullYear(), date.getMonth() + 1, date.getDate()) - lng / (15 * 24);
  const midDay = (t) => fix(12 - sunPosition(jDate + t).eqt, 24);
  const sunAngleTime = (angle, t, ccw) => {
    const decl = sunPosition(jDate + t).decl, noon = midDay(t);
    const v = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
    if (v < -1 || v > 1) return NaN;
    const tt = deg(Math.acos(v)) / 15;
    return noon + (ccw ? -tt : tt);
  };
  const asrTime = (f, t) => {
    const decl = sunPosition(jDate + t).decl;
    return sunAngleTime(-deg(Math.atan(1 / (f + dtan(Math.abs(lat - decl))))), t, false);
  };
  let t = [5, 6, 12, 13, 18, 18, 18];
  for (let k = 0; k < 2; k++) {
    const p = t.map((x) => x / 24);
    t = [sunAngleTime(m.fajr, p[0], true), sunAngleTime(0.833, p[1], true), midDay(p[2]), asrTime(asrFactor, p[3]),
      sunAngleTime(0.833, p[4], false), sunAngleTime(0.833, p[5], false), m.ishaMin ? 0 : sunAngleTime(m.isha, p[6], false)];
  }
  const adj = tz - lng / 15;
  let fajr = t[0] + adj; const sunrise = t[1] + adj, dhuhr = t[2] + adj, asr = t[3] + adj, maghrib = t[5] + adj;
  const ishaMin = ramadan && m.ishaRamadan ? m.ishaRamadan : m.ishaMin;
  let isha = ishaMin ? maghrib + ishaMin / 60 : t[6] + adj;
  const night = 24 - (maghrib - sunrise);
  if (isNaN(fajr) || sunrise - fajr > night / 2) fajr = sunrise - night / 2;
  if (isNaN(isha) || isha - maghrib > night / 2) isha = maghrib + night / 2;
  return [fajr, sunrise, dhuhr, asr, maghrib, isha];
}

export function qibla(lat, lng) {
  const kLat = 21.4225, kLng = 39.8262;
  return fix(deg(Math.atan2(dsin(kLng - lng), dcos(lat) * dtan(kLat) - dsin(lat) * dcos(kLng - lng))), 360);
}
export function distanceToKaaba(lat, lng) {
  const kLat = 21.4225, kLng = 39.8262;
  const a = dsin((kLat - lat) / 2) ** 2 + dcos(lat) * dcos(kLat) * dsin((kLng - lng) / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const AR = '٠١٢٣٤٥٦٧٨٩';
export const ar = (n) => String(n).replace(/\d/g, (d) => AR[d]);
export function fmt(h) {
  if (isNaN(h)) return '—';
  const mins = Math.round(fix(h, 24) * 60) % 1440, hh = Math.floor(mins / 60), mm = mins % 60;
  return `${ar(hh % 12 === 0 ? 12 : hh % 12)}:${ar(String(mm).padStart(2, '0'))} ${hh < 12 ? 'ص' : 'م'}`;
}

export const CITIES = [
  ['المنامة', 26.2235, 50.5876], ['المحرق', 26.2572, 50.6119], ['الرفاع', 26.13, 50.555], ['مدينة عيسى', 26.1736, 50.5478],
  ['مدينة حمد', 26.1153, 50.507], ['سترة', 26.1547, 50.6206], ['مكة المكرمة', 21.4225, 39.8262], ['المدينة المنورة', 24.4686, 39.6142],
  ['الرياض', 24.7136, 46.6753], ['جدة', 21.5433, 39.1728], ['الدمام', 26.4207, 50.0888], ['الخبر', 26.2172, 50.1971],
  ['الكويت', 29.3759, 47.9774], ['الدوحة', 25.2854, 51.531], ['أبوظبي', 24.4539, 54.3773], ['دبي', 25.2048, 55.2708],
  ['الشارقة', 25.3463, 55.4209], ['مسقط', 23.588, 58.3829], ['القاهرة', 30.0444, 31.2357], ['الجيزة', 30.0131, 31.2089],
  ['الإسكندرية', 31.2001, 29.9187], ['المنصورة', 31.0409, 31.3785], ['طنطا', 30.7865, 31.0004], ['الزقازيق', 30.5877, 31.502],
  ['دمنهور', 31.0341, 30.4682], ['كفر الشيخ', 31.1107, 30.9388], ['بنها', 30.466, 31.1848], ['شبين الكوم', 30.5594, 31.0076],
  ['دمياط', 31.4175, 31.8144], ['بورسعيد', 31.2653, 32.3019], ['الإسماعيلية', 30.5965, 32.2715], ['السويس', 29.9668, 32.5498],
  ['الفيوم', 29.3084, 30.8428], ['بني سويف', 29.0661, 31.0994], ['المنيا', 28.0871, 30.7618], ['أسيوط', 27.181, 31.1837],
  ['سوهاج', 26.5591, 31.6957], ['قنا', 26.1551, 32.716], ['الأقصر', 25.6872, 32.6396], ['أسوان', 24.0889, 32.8998],
  ['الغردقة', 27.2579, 33.8116], ['مرسى مطروح', 31.3543, 27.2373], ['عمّان', 31.9454, 35.9284], ['القدس', 31.7683, 35.2137],
  ['بيروت', 33.8938, 35.5018], ['دمشق', 33.5138, 36.2765], ['بغداد', 33.3152, 44.3661], ['صنعاء', 15.3694, 44.191],
  ['الخرطوم', 15.5007, 32.5599], ['طرابلس', 32.8872, 13.1913], ['تونس', 36.8065, 10.1815], ['الجزائر', 36.7538, 3.0588],
  ['الرباط', 34.0209, -6.8416], ['الدار البيضاء', 33.5731, -7.5898], ['إسطنبول', 41.0082, 28.9784], ['لندن', 51.5074, -0.1278],
];
