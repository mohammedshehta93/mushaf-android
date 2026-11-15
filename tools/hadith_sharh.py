"""Hadith explanations from HadeethEnc — «موسوعة الأحاديث النبوية» (hadeethenc.com, by the Rowad Translation Center,
reviewed by specialists): the hadith, its grade and source, an explanation, lessons and the meaning of difficult words,
in Arabic and English.

Used for: the hadith of the day (with its explanation), the library of explained hadiths, the English corner, and to
attach an explanation to any other hadith shown in the app when the same hadith is found there.
Never fails the build: if the site cannot be reached, the app keeps working without explanations.
"""
import concurrent.futures as cf, json, os, re, urllib.request

API = "https://hadeethenc.com/api/v1"
MAX_AR = 2500
MAX_EN = 800


def _get(url, path=None):
    if path and os.path.exists(path):
        try: return json.load(open(path, encoding="utf-8"))
        except Exception: pass
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Mushaf app build)", "Accept": "application/json"})
    data = json.load(urllib.request.urlopen(req, timeout=40))
    if path:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(data, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    return data


def _items(x):
    if isinstance(x, list): return x
    if isinstance(x, dict):
        for k in ("data", "items", "hadeeths", "categories"):
            if isinstance(x.get(k), list): return x[k]
    return []


def _ids(D, lang):
    cats = _items(_get(f"{API}/categories/list/?language={lang}", f"{D}/hadeethenc/{lang}/categories.json"))
    ids = []
    for c in cats:
        cid = c.get("id")
        page = 1
        while page < 60:
            r = _get(f"{API}/hadeeths/list/?language={lang}&category_id={cid}&page={page}&per_page=100",
                     f"{D}/hadeethenc/{lang}/list-{cid}-{page}.json")
            for h in _items(r):
                if h.get("id") and h["id"] not in ids: ids.append(h["id"])
            meta = r.get("meta", {}) if isinstance(r, dict) else {}
            last = int(meta.get("last_page") or 1)
            if page >= last: break
            page += 1
    return ids


def _one(D, lang, hid):
    try:
        return _get(f"{API}/hadeeths/one/?language={lang}&id={hid}", f"{D}/hadeethenc/{lang}/{hid}.json")
    except Exception:
        return None


def _clean(t):
    return re.sub(r"\s+\n", "\n", (t or "").strip())


def _pack(h, lang):
    if not h or not h.get("hadeeth") or not h.get("explanation"): return None
    grade = (h.get("grade") or "").strip()
    if lang == "ar" and ("ضعيف" in grade or "موضوع" in grade or not ("صحيح" in grade or "حسن" in grade)): return None
    if lang == "en" and ("weak" in grade.lower() or "fabricated" in grade.lower()): return None
    words = []
    for w in h.get("words_meanings") or []:
        if isinstance(w, dict) and w.get("word") and w.get("meaning"): words.append([w["word"].strip(), w["meaning"].strip()])
    hints = [x.strip() for x in (h.get("hints") or []) if isinstance(x, str) and x.strip()]
    return {"id": str(h.get("id")), "title": (h.get("title") or "").strip(), "t": _clean(h["hadeeth"]),
            "s": (h.get("attribution") or "").strip(), "g": grade, "x": _clean(h["explanation"]), "h": hints, "w": words}


def build(D, OUT):
    os.makedirs(f"{OUT}/muslim", exist_ok=True); os.makedirs(f"{OUT}/english", exist_ok=True)
    result = {"ar": [], "en": []}
    for lang, cap in (("ar", MAX_AR), ("en", MAX_EN)):
        try:
            ids = _ids(D, lang)[:cap]
            with cf.ThreadPoolExecutor(10) as ex:
                got = list(ex.map(lambda i: _one(D, lang, i), ids))
            result[lang] = [p for p in (_pack(h, lang) for h in got) if p]
        except Exception as ex:
            print(f"WARNING: HadeethEnc ({lang}) unavailable:", ex)
    print("explained hadiths: ar", len(result["ar"]), "· en", len(result["en"]))
    json.dump(result["ar"], open(f"{OUT}/muslim/hadith_sharh.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(result["en"], open(f"{OUT}/english/hadith.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    return result["ar"]


# ---------------------------------------------------------------- matching another collection's hadith to an explanation
_MARKS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭـ]")


def skel(t):
    t = _MARKS.sub("", t or "")
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ى", "ي"), ("ة", "ه"), ("ؤ", "و"), ("ئ", "ي")): t = t.replace(a, b)
    return re.sub("[^ء-ي]", "", t)


def matn(t):
    """The Prophet's words: the longest quoted part, else the whole text."""
    parts = re.findall(r"[\"“«]([^\"”»]{12,})[\"”»]", t or "")
    return max(parts, key=len) if parts else t


class Index:
    def __init__(self, items):
        self.items = [(skel(it["t"]), it) for it in items]

    def find(self, text):
        k = skel(matn(text))
        if len(k) < 25: return None
        probe = k[:60]
        for s, it in self.items:
            if probe in s: return it
        return None
