"""Extra offline data for the app: display text + tajweed, morphology, Bulugh al-Maram, recitation timings."""
import json, os, re, sys, io, zipfile, urllib.request, hashlib, collections

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
            if n.startswith(g):
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
