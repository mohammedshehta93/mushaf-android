"""Extra offline data for the app: display text + tajweed, morphology, Bulugh al-Maram, recitation timings."""
import json, os, re, sys, io, zipfile, urllib.request, urllib.parse, hashlib, collections
import concurrent.futures as cf

sys.path.insert(0, os.path.dirname(__file__))
import tajweed_prep

RAW = "https://raw.githubusercontent.com"
MORPH = RAW + "/mustafa0x/quran-morphology/master/quran-morphology.txt"
BULUGH = RAW + "/AhmedBaset/hadith-json/v1.2.0/db/by_book/other_books/bulugh_almaram.json"
RLM = "‏"

# everyayah.com folder -> Arabic name (used by the app; timings are added when quran-align has them)
RECITERS = [
    ("Husary_128kbps", "محمود خليل الحصري"),
    ("Husary_Muallim_128kbps", "الحصري (المصحف المعلّم)"),
    ("Minshawy_Murattal_128kbps", "محمد صديق المنشاوي"),
    ("Abdul_Basit_Murattal_192kbps", "عبد الباسط عبد الصمد (مرتّل)"),
    ("Maher_AlMuaiqly_64kbps", "ماهر المعيقلي"),
    ("Ghamadi_40kbps", "سعد الغامدي"),
]


def fetch(url, path):
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        urllib.request.urlretrieve(url, path)
    return path


# ------------------------------------------------------------------ text + tajweed
def build_text_and_tajweed(D, OUT, src_text):
    cache = f"{D}/tajweed_result.json"
    h = hashlib.sha1(json.dumps(src_text, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    res = None
    if os.path.exists(cache):
        c = json.load(open(cache))
        if c.get("hash") == h: res = c["spans"]
    if res is None:
        print("classifying tajweed (a few minutes)…", flush=True)
        res = tajweed_prep.classify(D, src_text)
        json.dump({"hash": h, "spans": res}, open(cache, "w"))
    disp, tj = {}, {}
    for k, v in src_text.items():
        d, idx = tajweed_prep.display_text(v)
        disp[k] = d
        flat = []
        for r, s, e in tajweed_prep.remap(res.get(k, []), idx):
            flat += [r, s, e]
        tj[k] = flat
    counts = collections.Counter(tajweed_prep.RULES[x] for v in tj.values() for x in v[0::3])
    print("tajweed spans:", sum(counts.values()))
    if sum(counts.values()) < 50000: raise SystemExit("ERROR: tajweed annotation count too low")
    json.dump(disp, open(f"{OUT}/text.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(tj, open(f"{OUT}/tajweed.json", "w"), separators=(",", ":"))


# ------------------------------------------------------------------ morphology
VERB_FORMS = {1: "فَعَلَ", 2: "فَعَّلَ", 3: "فَاعَلَ", 4: "أَفْعَلَ", 5: "تَفَعَّلَ", 6: "تَفَاعَلَ", 7: "انْفَعَلَ",
              8: "افْتَعَلَ", 9: "افْعَلَّ", 10: "اسْتَفْعَلَ", 11: "افْعَالَّ", 12: "افْعَوْعَلَ"}


def describe(tags, pos):
    t = set(x for x in tags if ":" not in x)
    kv = dict(x.split(":", 1) for x in tags if ":" in x)
    parts = []
    if pos == "V":
        parts.append("فعل " + ("ماضٍ" if "PERF" in t else "مضارع" if "IMPF" in t else "أمر" if "IMPV" in t else ""))
        if "PASS" in t: parts.append("مبني للمجهول")
    elif pos == "N":
        if "PN" in t: parts.append("اسم علم")
        elif "ACT_PCPL" in t: parts.append("اسم فاعل")
        elif "PASS_PCPL" in t: parts.append("اسم مفعول")
        elif "VN" in t: parts.append("مصدر")
        elif "ADJ" in t: parts.append("صفة")
        elif "PRON" in t: parts.append("ضمير")
        elif "REL" in t: parts.append("اسم موصول")
        elif "DEM" in t: parts.append("اسم إشارة")
        elif "T" in t: parts.append("ظرف زمان")
        elif "LOC" in t: parts.append("ظرف مكان")
        elif "INTG" in t: parts.append("اسم استفهام")
        elif "COND" in t: parts.append("اسم شرط")
        else: parts.append("اسم")
    else:
        parts.append("حرف")
    vf = kv.get("VF")
    if vf and vf.isdigit() and int(vf) in VERB_FORMS and pos == "V":
        parts.append(("مجرد" if vf == "1" else "مزيد") + " على وزن " + VERB_FORMS[int(vf)])
    return " — ".join(p.strip() for p in parts if p.strip())


def build_morph(D, OUT, word_counts):
    path = fetch(MORPH, f"{D}/morph.txt")
    words = collections.defaultdict(list)          # (s,a,w) -> segments
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 4: continue
        s, a, w, _ = map(int, f[0].split(":"))
        words[(s, a, w)].append((f[1], f[2], f[3].split("|")))
    out = collections.defaultdict(list)
    for (s, a, w) in sorted(words):
        segs = words[(s, a, w)]
        stem = next((x for x in segs if any(t.startswith("ROOT:") for t in x[2])), None) \
            or next((x for x in reversed(segs) if "PREF" not in x[2] and "SUFF" not in x[2]), segs[-1])
        tags = stem[2]
        root = next((t[5:] for t in tags if t.startswith("ROOT:")), "")
        lem = next((t[4:] for t in tags if t.startswith("LEM:")), "")
        out[f"{s}:{a}"].append([root, lem, describe(tags, stem[1])])
    bad = [k for k, n in word_counts.items() if len(out.get(k, [])) != n]
    print("morphology ayahs with word-count mismatch:", len(bad))
    if len(bad) > 30: raise SystemExit("ERROR: morphology does not align with the Mushaf words")
    json.dump(out, open(f"{OUT}/morph.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))


# ------------------------------------------------------------------ Bulugh al-Maram (fiqh)
GRADES = ["صحيح لغيره", "حسن لغيره", "ضعيف جدا", "صحيح", "حسن", "ضعيف", "منكر", "موضوع", "شاذ"]


def parse_hadith(t):
    t = t.replace("‎", "")
    m = re.search(r"\.?" + RLM + "*" + r"1\s*" + RLM + r"?-", t)
    main, notes = (t[:m.start()], t[m.start():]) if m else (t, "")
    main = re.sub(r"\s+\d+" + RLM + r"?(?=\s|$|\.)", "", main).replace(RLM, "").strip(" .")
    parts = re.split(RLM + r"*(\d+)\s*" + RLM + r"?-\s*", notes)
    nts = [parts[i + 1].replace(RLM, "").strip() for i in range(1, len(parts) - 1, 2)]
    grade = ""
    for n in nts:
        for g in GRADES:
            if n.lstrip(". ").startswith(g):     # some notes begin «. صحيح» (audit: 10 grades were missed)
                grade = g; break
        if grade: break
    close = main.rfind("}")
    matn, takhrij = (main[:close + 1], main[close + 1:].strip(" .،,")) if close > 0 else (main, "")
    nar = re.match(r"^وَ?عَنْ\s+(.+?)\s*(?:-\s*رض|رَضِيَ|رضي|;|؛|,|،|\s-\s|قَالَ|أَنَّ)", main)
    narrator = re.sub(r"[\[\]]", "", nar.group(1)).strip(" -") if nar else ""
    if len(narrator.split()) > 6: narrator = ""
    words = re.findall(r"\{([^}]*)\}", matn)
    return {"m": matn.strip(), "t": takhrij, "g": grade, "r": narrator, "q": " ".join(w.strip() for w in words), "n": nts}


def build_fiqh(D, OUT):
    d = json.load(open(fetch(BULUGH, f"{D}/bulugh.json"), encoding="utf-8"))
    books = []
    for ch in d["chapters"]:
        hs = [h for h in d["hadiths"] if h["chapterId"] == ch["id"]]
        books.append({"title": ch["arabic"], "h": [dict(parse_hadith(h["arabic"]), i=h["idInBook"]) for h in hs]})
    total = sum(len(b["h"]) for b in books)
    print("bulugh hadiths:", total)
    if total < 1500: raise SystemExit("ERROR: Bulugh al-Maram incomplete")
    os.makedirs(f"{OUT}/fiqh", exist_ok=True)
    json.dump({"title": "بلوغ المرام من أدلة الأحكام", "author": "الحافظ ابن حجر العسقلاني", "books": books},
              open(f"{OUT}/fiqh/bulugh.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))


SOUND_GRADES = ("صحيح", "حسن", "صحيح لغيره", "حسن لغيره")


def finish_fiqh(D, OUT, sharh_items=None):
    """Bulugh al-Maram as studied in the app: sahih and hasan hadiths only (the editor's grade, or — where he gave
    none — a hadith Ibn Hajar attributes to al-Bukhari or Muslim), each with its explanation from HadeethEnc when
    the same hadith is there. Weak, very weak, munkar, shadh and fabricated hadiths are left out."""
    import hadith_sharh
    path = f"{OUT}/fiqh/bulugh.json"
    d = json.load(open(path, encoding="utf-8"))
    idx = hadith_sharh.Index(sharh_items or [])
    kept = dropped = explained = 0
    for b in d["books"]:
        out = []
        for h in b["h"]:
            g = h["g"]
            if not g:
                t = re.sub("[\u064b-\u0652]", "", h["t"])
                if re.search(r"متفق عليه|البخاري|مسلم", t) and not re.search(r"شرط|ضعف|ضعيف", t): g = h["g"] = "صحيح"
            if g not in SOUND_GRADES: dropped += 1; continue
            sh = idx.find("«" + h["q"] + "»") if len(h["q"]) >= 25 else idx.find(h["m"])
            if sh: h.update({"x": sh["x"], "hl": sh["h"], "w": sh["w"]}); explained += 1
            out.append(h); kept += 1
        b["h"] = out
    d["books"] = [b for b in d["books"] if b["h"]]
    if kept < 1000: raise SystemExit(f"ERROR: too few sound hadiths left in Bulugh al-Maram ({kept})")
    json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(f"bulugh (fiqh): kept {kept} sahih/hasan, dropped {dropped} weak or ungraded · with explanation {explained}")


def build_hadith_library(D, OUT, sharh_items=None):
    """«الأحاديث المشروحة», arranged: the Forty of an-Nawawi (in their order), then what al-Bukhari and Muslim both
    narrate, then al-Bukhari, then Muslim, then other sahih/hasan hadiths. Every hadith with its explanation from
    HadeethEnc (sahih/hasan only). Items are positions in muslim/hadith_sharh.json, so nothing is stored twice."""
    import hadith_sharh
    items = sharh_items or []
    path = f"{OUT}/muslim/hadith_library.json"
    if len(items) < 100:
        json.dump({"groups": []}, open(path, "w", encoding="utf-8"))
        print("hadith library: no explanations available in this build"); return
    pos = {id(h): i for i, h in enumerate(items)}
    idx = hadith_sharh.Index(items)
    nw = json.load(open(fetch(NAWAWI, f"{D}/nawawi40.json"), encoding="utf-8"))
    forty, nums, used = [], [], set()
    for h in sorted(nw["hadiths"], key=lambda x: x["idInBook"]):
        it = idx.find(h["arabic"])
        if it and pos[id(it)] not in used: forty.append(pos[id(it)]); nums.append(h["idInBook"]); used.add(pos[id(it)])
    plain = lambda t: re.sub("[\u064b-\u0652]", "", t or "")
    agreed, bukhari, muslim, other = [], [], [], []
    for i, h in enumerate(items):
        if i in used: continue
        src = plain(h["s"])
        if "متفق عليه" in src: agreed.append(i)
        elif "البخاري" in src and "مسلم" in src: agreed.append(i)
        elif re.search(r"رواه البخاري|أخرجه البخاري", src): bukhari.append(i)
        elif re.search(r"رواه مسلم|أخرجه مسلم", src): muslim.append(i)
        else: other.append(i)
    groups = [
        {"title": "الأربعون النووية", "desc": "الأحاديث الأربعون التي جمعها الإمام النووي، كل حديث بشرحه", "items": forty, "nums": nums},
        {"title": "متفق عليه", "desc": "ما رواه البخاري ومسلم كلاهما", "items": agreed},
        {"title": "من صحيح البخاري", "desc": "", "items": bukhari},
        {"title": "من صحيح مسلم", "desc": "", "items": muslim},
        {"title": "أحاديث صحيحة وحسنة أخرى", "desc": "من السنن والمسانيد، بحكم أهل العلم عليها", "items": other},
    ]
    groups = [g for g in groups if g["items"]]
    json.dump({"src": "«موسوعة الأحاديث النبوية» hadeethenc.com — بإشراف متخصصين", "groups": groups},
              open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("hadith library:", " · ".join(f"{g['title']} {len(g['items'])}" for g in groups))


# ------------------------------------------------------------------ recitation word timings (optional)
def build_timings(D, OUT):
    """Word timings from github.com/cpfair/quran-align releases (CC-BY 4.0). Optional: never fails the build."""
    os.makedirs(f"{OUT}/timings", exist_ok=True)
    found = {}
    try:
        req = urllib.request.Request("https://api.github.com/repos/cpfair/quran-align/releases",
                                     headers={"Accept": "application/vnd.github+json"})
        tok = os.environ.get("GITHUB_TOKEN")
        if tok: req.add_header("Authorization", "Bearer " + tok)
        rels = json.load(urllib.request.urlopen(req, timeout=60))
        blobs = {}
        for rel in rels:
            for a in rel.get("assets", []):
                name = a["name"]
                p = fetch(a["browser_download_url"], f"{D}/align/{rel['id']}/{name}")
                if name.endswith(".zip"):
                    with zipfile.ZipFile(p) as z:
                        for n in z.namelist():
                            if n.endswith(".json"): blobs.setdefault(os.path.basename(n), z.read(n))
                elif name.endswith(".json"):
                    blobs.setdefault(name, open(p, "rb").read())
        for folder, _ in RECITERS:
            key = folder.lower().split("_")[0].replace("-", "")
            cand = [n for n in blobs if folder.lower() in n.lower()] or \
                   [n for n in blobs if key in n.lower().replace("-", "").replace("_", "")]
            if not cand: continue
            data = json.loads(blobs[sorted(cand, key=len)[0]])
            out = {}
            for e in data:
                segs = []
                for sg in e.get("segments", []):
                    if len(sg) >= 4 and sg[1] > sg[0]:
                        segs += [sg[0] + 1, int(sg[2]), int(sg[3])]     # 1-based word, start ms, end ms
                out[f"{e['surah']}:{e['ayah']}"] = segs
            if len(out) > 6000:
                json.dump(out, open(f"{OUT}/timings/{folder}.json", "w"), separators=(",", ":"))
                found[folder] = len(out)
    except Exception as ex:
        print("WARNING: recitation timings unavailable:", ex)
    print("word timings for:", found or "none (audio still works, highlighting per ayah)")
    json.dump([{"id": f, "name": n, "timed": f in found} for f, n in RECITERS],
              open(f"{OUT}/reciters.json", "w", encoding="utf-8"), ensure_ascii=False)


def build_all(D, OUT, src_text, word_counts, ayah_counts=None):
    build_text_and_tajweed(D, OUT, src_text)
    if ayah_counts: build_muslim(D, OUT, ayah_counts)
    build_morph(D, OUT, word_counts)
    build_fiqh(D, OUT)
    build_timings(D, OUT)
    build_radios(D, OUT)
    build_learning(OUT)


# ------------------------------------------------------------------ daily-life Muslim tools data
AZKAR = RAW + "/osamayy/azkar-db/master/azkar.json"                       # Hisn al-Muslim
RIYAD = RAW + "/AhmedBaset/hadith-json/v1.2.0/db/by_book/other_books/riyad_assalihin.json"
NAWAWI = RAW + "/AhmedBaset/hadith-json/v1.2.0/db/by_book/forties/nawawi40.json"
NAMES = RAW + "/KabDeveloper/99-Names-Of-Allah/main/99_Names_Of_Allah.json"


def build_muslim(D, OUT, ayah_counts):
    os.makedirs(f"{OUT}/muslim", exist_ok=True)
    # Azkar (Hisn al-Muslim): keep the original order of categories and items
    a = json.load(open(fetch(AZKAR, f"{D}/azkar.json"), encoding="utf-8"))
    cols = [c["name"] for c in a["columns"]]
    cats = {}
    for r in a["rows"]:
        row = dict(zip(cols, r))
        cat = (row["category"] or "").strip()
        z = (row["zekr"] or "").strip()
        if not cat or not z: continue
        cats.setdefault(cat, []).append({"z": z, "d": (row.get("description") or "").strip(),
                                         "c": int(row.get("count") or 1) or 1, "r": (row.get("reference") or "").strip()})
    if len(cats) < 100 or sum(len(v) for v in cats.values()) < 300: raise SystemExit("ERROR: azkar incomplete")
    json.dump([{"t": k, "i": v} for k, v in cats.items()], open(f"{OUT}/muslim/azkar.json", "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))

    # Hadith of the day: all of the Nawawi forty + the shorter hadiths of Riyad as-Salihin
    daily = []
    nw = json.load(open(fetch(NAWAWI, f"{D}/nawawi40.json"), encoding="utf-8"))
    for h in nw["hadiths"]:
        daily.append({"t": h["arabic"].replace(RLM, "").strip(), "s": f"الأربعون النووية ({h['idInBook']})"})
    rs = json.load(open(fetch(RIYAD, f"{D}/riyad.json"), encoding="utf-8"))
    chap = {c["id"]: c["arabic"] for c in rs["chapters"]}
    for h in rs["hadiths"]:
        t = re.sub(r"\s+", " ", h["arabic"].replace(RLM, "")).strip()
        if t.startswith(("وعنه", "وعنها", "وفي رواية", "وفى رواية", "وله", "وفي لفظ")): continue   # needs the previous hadith
        if 60 <= len(t) <= 450 and ("صلى الله عليه وسلم" in t or "ﷺ" in t):
            daily.append({"t": t, "s": f"رياض الصالحين ({h['idInBook']}) — {chap.get(h['chapterId'], '')}".strip(" —")})
    if len(daily) < 300: raise SystemExit("ERROR: daily hadith list too short")
    json.dump(daily, open(f"{OUT}/muslim/hadith_daily.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

    # The Beautiful Names with the ayahs they appear in
    n = json.load(open(fetch(NAMES, f"{D}/names99.json"), encoding="utf-8"))["data"]
    out = []
    for x in n:
        refs = []
        for s, ay in re.findall(r"\((\d+)\s*:\s*(\d+)\)", x.get("found", "")):
            s, ay = int(s), int(ay)
            if 1 <= s <= 114 and 1 <= ay <= ayah_counts[s - 1]: refs.append([s, ay])
        out.append({"n": x["name"].strip(), "r": refs})
    if len(out) != 99: raise SystemExit("ERROR: expected 99 names")
    json.dump(out, open(f"{OUT}/muslim/names.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("muslim data: azkar categories", len(cats), "· daily hadith", len(daily), "· names", len(out))


# ------------------------------------------------------------------ live Quran radios (mp3quran.net, official streams)
RADIOS_API = "https://mp3quran.net/api/v3/radios?language=ar"


def _alive(url):
    """True if the stream answers with audio within a few seconds."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Mushaf app build check)", "Icy-MetaData": "0"})
        with urllib.request.urlopen(req, timeout=10) as r:
            ct = (r.headers.get("Content-Type") or "").lower()
            r.read(2048)
            return r.status == 200 and ("audio" in ct or "mpeg" in ct or "ogg" in ct or "aac" in ct or ct == "")
    except Exception:
        return False


def build_radios(D, OUT):
    """Never fails the build: if the list cannot be fetched, the app shows an empty radio section."""
    os.makedirs(f"{OUT}/listen", exist_ok=True)
    radios = []
    try:
        req = urllib.request.Request(RADIOS_API, headers={"User-Agent": "Mozilla/5.0"})
        data = json.load(urllib.request.urlopen(req, timeout=60))
        items = data.get("radios", data) if isinstance(data, dict) else data
        cand = []
        for x in items:
            name = (x.get("name") or "").strip()
            url = (x.get("url") or x.get("radio_url") or "").strip()
            if name and url.startswith("http"): cand.append((name, url))
        seen = set(); cand = [c for c in cand if not (c[1] in seen or seen.add(c[1]))]

        def check(c):
            u = c[1]
            if u.startswith("http://"):          # prefer https (required by the web version)
                s = "https://" + u[7:]
                if _alive(s): return s
            return u if _alive(u) else None
        with cf.ThreadPoolExecutor(16) as ex:
            ok = list(ex.map(check, cand))
        radios = [{"n": n, "u": good} for (n, _), good in zip(cand, ok) if good]
    except Exception as ex:
        print("WARNING: radio list unavailable:", ex)
    json.dump(radios, open(f"{OUT}/listen/radios.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("live radios (checked):", len(radios))


# ------------------------------------------------------------------ learning: library catalog + tajweed lessons
HERE = os.path.dirname(os.path.abspath(__file__))
_THROAT = "ءأإؤئهعحغخ"
_REGEX_RULES = {
    # noon with a written sukun, or a (stacked) tanween, followed by a throat letter
    "izhar_halqi": re.compile("(نۡ|[ً-ٍ])[ؐ-ًؚ-ٟۖ-ۭ]*[اى]?\\s?[" + _THROAT + "]"),
    # meem with a written sukun, followed by any letter except meem and baa
    "izhar_shafawi": re.compile("مۡ\\s?[ء-غف-يٱ](?<![مب])"),
    # the article al- with a written sukun on the lam
    "lam_qamariyyah": re.compile("ٱلۡ[ء-ي]"),
}


_WITH_NEXT = {"idghaam_mutajanisayn", "idghaam_mutaqaribayn", "madd_munfasil"}


def _word_snippet(text, start, end, with_next=False):
    """Whole word(s) covering [start, end) (plus the following word for rules that act across words)."""
    a = text.rfind(" ", 0, start) + 1
    b = text.find(" ", max(end - 1, start))
    if b < 0: b = len(text)
    if with_next and b < len(text):
        nb = text.find(" ", b + 1)
        b = len(text) if nb < 0 else nb
    return text[a:b], start - a, end - a


def build_learning(OUT):
    os.makedirs(f"{OUT}/learn", exist_ok=True)
    lib = load_library()
    resolve_poem_audio(lib)
    json.dump(lib, open(f"{OUT}/learn/library.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

    lessons = json.load(open(f"{HERE}/content/tajweed_lessons.json", encoding="utf-8"))
    text = json.load(open(f"{OUT}/text.json", encoding="utf-8"))
    tj = json.load(open(f"{OUT}/tajweed.json"))
    order = [(s, a) for s in [1] + list(range(78, 115)) + list(range(2, 78)) for a in range(1, 400) if f"{s}:{a}" in text]
    wanted = set()
    for l in lessons["lessons"]:
        for sec in l["sections"]:
            for k in ("ex", "ex2"):
                if sec.get(k): wanted.add(sec[k])
        wanted.update(l.get("quiz", []))
    found = {r: [] for r in wanted}
    seen = {r: set() for r in wanted}
    for s, a in order:
        t = text[f"{s}:{a}"]
        spans = tj.get(f"{s}:{a}", [])
        hits = []
        for i in range(0, len(spans), 3):
            name = tajweed_prep.RULES[spans[i]]
            if name in wanted: hits.append((name, spans[i + 1], spans[i + 2]))
        for name, rx in _REGEX_RULES.items():
            if name in wanted:
                for m in rx.finditer(t):
                    hits.append((name, m.start(), m.end()))
        for name, st, en in hits:
            if len(found[name]) >= 24: continue
            w, hs, he = _word_snippet(t, st, en, name in _WITH_NEXT)
            if w in seen[name] or len(w) > 40: continue
            seen[name].add(w)
            found[name].append([s, a, w, hs, he])
    missing = [r for r in wanted if len(found[r]) < 3]
    if missing: raise SystemExit(f"tajweed examples missing for: {missing}")
    lessons["examples"] = found
    json.dump(lessons, open(f"{OUT}/learn/tajweed.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("learning: tajweed examples", {k: len(v) for k, v in found.items()})


LIBRARY = os.path.join(HERE, "..", "content", "library.json")


def load_library():
    """content/library.json is edited by the owner on GitHub; a broken file must stop the build (and the app ignores it)."""
    try:
        lib = json.load(open(LIBRARY, encoding="utf-8"))
    except Exception as ex:
        raise SystemExit(f"content/library.json is not valid JSON: {ex}")
    for k in ("sheikhGroups", "poemGroups"):
        if not isinstance(lib.get(k), list): raise SystemExit(f"content/library.json: '{k}' is missing")
        for g in lib[k]:
            if not g.get("title") or not isinstance(g.get("items"), list): raise SystemExit(f"content/library.json: bad group in {k}")
            for it in g["items"]:
                if not it.get("n"): raise SystemExit(f"content/library.json: item without n in {k}")
    if not isinstance(lib.get("hide", {}), dict): raise SystemExit("content/library.json: 'hide' must be an object")
    return lib


_BAD = re.compile(r"موسيق|music|song|أغني|اغني|غناء|عزف", re.I)
_AR_MARKS = re.compile("[\u064B-\u0652\u0670\u0640]")


def _norm(t):
    t = _AR_MARKS.sub("", t or "")
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ة", "ه"), ("ى", "ي")): t = t.replace(a, b)
    return t


def _get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Mushaf app build)"})
    return json.load(urllib.request.urlopen(req, timeout=30))


def _archive_tracks(query):
    """Spoken recordings of a poem on archive.org: the item title must contain every word of the query."""
    skip = {"متن", "منظومه", "المنظومه", "نظم", "في", "شرح"}
    words = [w for w in _norm(query).split() if len(w) > 1 and w not in skip]
    q = urllib.parse.quote(f"({query}) AND mediatype:(audio)")
    res = _get_json(f"https://archive.org/advancedsearch.php?q={q}&fl[]=identifier&fl[]=title&rows=15&output=json")
    for doc in res.get("response", {}).get("docs", []):
        title = doc.get("title") or ""
        if isinstance(title, list): title = " ".join(title)
        nt = _norm(title)
        if _BAD.search(title) or not all(w in nt for w in words): continue
        ident = doc["identifier"]
        meta = _get_json(f"https://archive.org/metadata/{urllib.parse.quote(ident)}")
        files = [f for f in meta.get("files", []) if f.get("name", "").lower().endswith(".mp3") and f.get("source") == "original"]
        if not files: files = [f for f in meta.get("files", []) if f.get("name", "").lower().endswith(".mp3")]
        if not files or len(files) > 40: continue
        files.sort(key=lambda f: f["name"])
        return {"src": f"archive.org/details/{ident}", "srcTitle": title.strip(), "tracks": [
            {"t": os.path.splitext(os.path.basename(f["name"]))[0].replace("_", " "),
             "u": f"https://archive.org/download/{urllib.parse.quote(ident)}/{urllib.parse.quote(f['name'])}"} for f in files]}
    return None


def resolve_poem_audio(lib):
    """Never fails the build. An item with its own url keeps it; otherwise archive.org is searched once."""
    found = 0
    for g in lib["poemGroups"]:
        for it in g["items"]:
            if it.get("url"):
                it["tracks"] = [{"t": it["n"], "u": it["url"]}]; found += 1; continue
            try:
                r = _archive_tracks(it.get("search") or it.get("q") or it["n"])
            except Exception as ex:
                print("WARNING: audio search failed for", it["n"], ex); r = None
            if r: it.update(r); found += 1
    print("poem audio found:", found)


# ------------------------------------------------------------------ reading: intentions, tadabbur reminders, virtues of the Quran
def build_reading(D, OUT, sharh_items=None):
    """Every ayah comes from the official text, every hadith verbatim from Riyad as-Salihin by its id."""
    import hadith_sharh
    cfg = json.load(open(f"{HERE}/content/reading.json", encoding="utf-8"))
    text = json.load(open(f"{OUT}/text.json", encoding="utf-8"))
    rs = json.load(open(fetch(RIYAD, f"{D}/riyad.json"), encoding="utf-8"))
    by_id = {h["id"]: h for h in rs["hadiths"]}
    names = json.load(open(f"{OUT}/meta.json", encoding="utf-8"))["surahs"]
    idx = hadith_sharh.Index(sharh_items or [])
    english = rs["hadiths"][0].get("english") is not None

    def resolve(ref):
        if "ayah" in ref:
            s, a = map(int, ref["ayah"].split(":"))
            t = text.get(ref["ayah"])
            if not t: raise SystemExit(f"ERROR: reading.json: ayah {ref['ayah']} not found")
            return {"k": "a", "s": s, "a": a, "t": t, "src": f"سورة {names[s - 1]['name']} — الآية {a}"}
        if "tafsir" in ref:                     # Ibn Kathir (the app's tafsir), verbatim between two anchors, footnotes dropped
            import hadith_books
            s, a = map(int, ref["tafsir"].split(":"))
            rows = json.load(open(f"{OUT}/tafsir/{s}.json", encoding="utf-8"))
            row = next(x for x in rows if x[0] <= a <= x[1])
            t = re.sub(r" *\[\[.*?\]\]", "", row[2], flags=re.S)
            p, ix = hadith_books.norm_with_index(t)
            f, e = hadith_books.norm_with_index(ref["from"])[0].strip(), hadith_books.norm_with_index(ref["to"])[0].strip()
            i = p.find(f); j = p.find(e, i)
            if i < 0 or j < 0 or p.find(f, i + 1) >= 0:
                raise SystemExit(f"ERROR: reading.json: tafsir excerpt not found exactly once in {ref['tafsir']}: {ref['from']}")
            end = ix[j + len(e) - 1] + 1
            while end < len(t) and t[end] not in " \n": end += 1          # the rest of the last word and its punctuation
            ex = re.sub(r"[ \t]+", " ", t[ix[i]:end]).strip()
            if ex[-1] in "،؛,": ex = ex[:-1] + " …"                   # the passage goes on in the book
            ay = f"الآية {a}" if row[0] == row[1] else f"الآيات {row[0]}–{row[1]}"
            return {"k": "h", "t": ex, "src": f"تفسير ابن كثير — سورة {names[s - 1]['name']}، {ay}"}
        if "book" in ref:                       # the six books / al-Muwatta, verbatim (an excerpt between two anchors)
            import hadith_books
            out = hadith_books.resolve(D, ref)
            sh = idx.find(out["t"])
            if sh: out.update({"x": sh["x"], "hl": sh["h"], "w": sh["w"], "g": sh["g"]})
            return out
        h = by_id.get(ref["riyad"])
        if not h: raise SystemExit(f"ERROR: reading.json: Riyad hadith {ref['riyad']} not found")
        t = re.sub(r"[ \t]+", " ", h["arabic"].replace(RLM, "")).strip().lstrip("-").strip()
        import hadith_books
        g = hadith_books.grade_of(D, t)
        if "same" in ref:          # checked by hand: the same hadith in the six books (must be sahih/hasan there)
            r = hadith_books.find(D, ref["same"]); g = (ref["same"]["book"], r["n"], r["c"], "")
        if not ((g and g[2] == 0) or (not g and SAHIHAIN.search(t))):
            raise SystemExit(f"ERROR: reading.json: Riyad {h['idInBook']} is not confirmed sahih/hasan ({g})")
        out = {"k": "h", "t": t, "src": f"رياض الصالحين ({h['idInBook']})"}
        sh = idx.find(t)
        if sh: out.update({"x": sh["x"], "hl": sh["h"], "w": sh["w"], "g": sh["g"]})
        if english and isinstance(h.get("english"), dict):
            out["en"] = (h["english"].get("narrator", "") + " " + h["english"].get("text", "")).strip()
        return out

    td = cfg["tadabbur"]
    res = {
        # «وقفة تدبّر» on an ayah: the three questions (wording approved by the project owner) and the hadith of
        # repeating one ayah all night
        "tadabbur": {"questions": td["questions"], "repeat": resolve(td["repeat"])},
        "intentions": [dict(resolve(x["ref"]), n=x["t"]) for x in cfg["intentions"]],
        "reminders": [dict(resolve(x["ref"]), h=x["h"]) for x in cfg["reminders"]],
        "virtues": [dict({"title": g["title"], "items": [dict(resolve(r), **({"h": r["h"]} if r.get("h") else {})) for r in g["items"]]},
                         **({"note": g["note"]} if g.get("note") else {})) for g in cfg["virtues"]],
    }
    json.dump(res, open(f"{OUT}/reading.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    n_x = sum(1 for g in res["virtues"] for i in g["items"] if i.get("x"))
    print("reading: intentions", len(res["intentions"]), "· reminders", len(res["reminders"]), "· virtues with explanation", n_x)


ENGLISH_QURAN = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/eng-ummmuhammad.min.json"


def build_english(D, OUT):
    """Saheeh International translation (by Umm Muhammad) for the English corner."""
    os.makedirs(f"{OUT}/english", exist_ok=True)
    d = json.load(open(fetch(ENGLISH_QURAN, f"{D}/eng-saheeh.json"), encoding="utf-8"))
    tr = {f"{v['chapter']}:{v['verse']}": v["text"].strip() for v in d["quran"]}
    if len(tr) != 6236: raise SystemExit(f"ERROR: English translation has {len(tr)} ayahs")
    json.dump(tr, open(f"{OUT}/english/saheeh.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    # The Forty Hadith of an-Nawawi in English (fallback when the explained English hadiths are unavailable)
    nw = json.load(open(fetch(NAWAWI, f"{D}/nawawi40.json"), encoding="utf-8"))
    out = []
    for h in nw["hadiths"]:
        e = h.get("english") or {}
        t = (e.get("narrator", "") + " " + e.get("text", "")).strip() if isinstance(e, dict) else str(e)
        if t: out.append({"t": t, "s": f"40 Hadith of an-Nawawi, no. {h['idInBook']}", "ar": h["arabic"].replace(RLM, "").strip()})
    out = sound_only(D, out, "ar", "English Forty (fallback)")
    json.dump(out, open(f"{OUT}/english/nawawi40.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))


SAHIHAIN = re.compile(r"(متفق عليه|رواه البخاري|رواه مسلم|رواه البخارى|أخرجه البخاري|أخرجه مسلم|البخاري ومسلم)")


def sound_only(D, items, text_key="t", label="hadiths"):
    """Keeps only hadiths found sahih/hasan in the six books (al-Albani / the two Sahihs), or — when not found there —
    those the source itself attributes to al-Bukhari or Muslim. Riyad as-Salihin and the Forty contain a few weak
    hadiths (the data audit found 39 in the old daily list, e.g. «إذا رأيتم الرجل يعتاد المساجد»)."""
    import hadith_books
    out, dropped = [], []
    for it in items:
        g = hadith_books.grade_of(D, it[text_key])
        ok = (g[2] == 0) if g else bool(SAHIHAIN.search(it[text_key]))
        (out if ok else dropped).append(it)
    print(f"{label}: kept {len(out)}, dropped {len(dropped)} weak or unverified")
    return out


def attach_daily_sharh(OUT, sharh_items, D=None):
    """Hadith of the day = the explained hadiths (sahih/hasan) when available; otherwise the old list, sahih/hasan
    only, attaching an explanation wherever the same hadith is found."""
    import hadith_sharh
    path = f"{OUT}/muslim/hadith_daily.json"
    if len(sharh_items) >= 300:
        daily = [{"t": h["t"], "s": h["s"] + (f" — {h['g']}" if h["g"] else ""), "x": h["x"], "hl": h["h"], "w": h["w"]}
                 for h in sharh_items if len(h["t"]) <= 700]
    else:
        daily = json.load(open(path, encoding="utf-8"))
        if D: daily = sound_only(D, daily, label="hadith of the day (fallback list)")
        idx = hadith_sharh.Index(sharh_items)
        for d in daily:
            sh = idx.find(d["t"])
            if sh: d.update({"x": sh["x"], "hl": sh["h"], "w": sh["w"]})
    seen, uniq = set(), []                      # the same hadith only once (some are in the list twice)
    for d in daily:
        k = re.sub("[^ء-ي]", "", re.sub("[\u064b-\u0652]", "", d["t"]))[:100]
        if k not in seen: seen.add(k); uniq.append(d)
    daily = uniq
    json.dump(daily, open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("hadith of the day:", len(daily), "· with explanation:", sum(1 for d in daily if d.get("x")))
