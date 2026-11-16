"""Surah aims and «ما ورد في نزول الآية» — OUT/surahs_info.json.

* Aims (مقاصد السورة) and the reason for each surah's name: verbatim from the surah introductions that come with the
  Tafsir al-Muyassar edition of quran.com / QUL (resource 38, via spa5k/tafsir_api), ayah 1 of each surah.
* Revelation reports: only narrations of the six books that are sahih/hasan (al-Bukhari, Muslim, or al-Albani for the
  Sunan), found where the narration says «فنزلت / فأنزل الله / نزلت / أُنزلت …» and then quotes the ayah; the quote
  must identify exactly one ayah of the official text. The narration is shown in full, verbatim, with its number and
  rulings. Nothing is written by hand.
"""
import json, os, re, collections, urllib.request
import hadith_books as hb

MUYASSAR = "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/ar-tafsir-muyassar/{}.json"
SRC_AIMS = "مقدمات السور في «التفسير الميسّر» كما نشرته منصة quran.com/QUL"
TRIG = re.compile(r"(فَ?نَزَلَتْ|فَأَنْزَلَ اللَّهُ|فَأَنْزَلَ|فَأُنْزِلَتْ|أُنْزِلَتْ|نَزَلَتْ|فَنَزَلَ|أَنْزَلَ اللَّهُ)")
_MAP = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي", "ی": "ي"}


def _sk(t):
    """Consonant skeleton (no long vowels): Uthmani and ordinary spelling meet here."""
    return "".join(_MAP.get(c, c) for c in t if "ء" < c <= "ي" and c != "ـ").replace("ا", "").replace("و", "").replace("ي", "")


def _intro(text):
    """Bullets of «تسمية السورة» and «من مقاصد السورة» at the start of ayah 1."""
    name, aims, cur = [], [], None
    for line in [l.strip() for l in text.split("\n") if l.strip()]:
        if line.startswith("تسمية السورة"): cur = name; continue
        if line.startswith("من مقاصد السورة"): cur = aims; continue
        if line.startswith("•") and cur is not None: cur.append(line.lstrip("• ").strip()); continue
        break
    return name, aims


def build(D, OUT):
    meta = json.load(open(f"{OUT}/meta.json", encoding="utf-8"))
    text = json.load(open(f"{OUT}/text.json", encoding="utf-8"))
    info = {}
    for s in range(1, 115):
        path = f"{D}/muyassar/{s}.json"
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            urllib.request.urlretrieve(MUYASSAR.format(s), path)
        first = next(e for e in json.load(open(path, encoding="utf-8")) if int(e["ayah"]) == 1)
        name, aims = _intro(first["text"])
        if not aims: raise SystemExit(f"ERROR: no aims found for surah {s}")
        info[str(s)] = {"why": name, "aims": aims}

    # revelation reports
    ay = {k: _sk(v) for k, v in text.items()}
    by_start = collections.defaultdict(set)
    for k, v in ay.items():
        for i in range(0, max(1, len(v) - 11)): by_start[v[i:i + 12]].add(k)

    def ayah_of(q):
        s = _sk(q)
        if len(s) < 8: return None
        cands = by_start.get(s[:12], set()) if len(s) >= 12 else [k for k, v in ay.items() if s in v]
        hits = [k for k in cands if s[:40] in ay[k]]
        return hits[0] if len(hits) == 1 else None

    order = {b: i for i, (b, _) in enumerate(hb.BOOKS)}
    reports = collections.defaultdict(list)
    for b, recs in hb.load(D).items():
        if b == "malik": continue
        for r in recs:
            if r["c"] != 0: continue
            t = r["t"]
            for m in TRIG.finditer(t):
                q = re.search(r"[{(]([^})]{6,})[})]", t[m.end():m.end() + 120])
                if not q or q.start() > 70: continue
                # «هكذا أنزلت» is about the wording, not the occasion; «الآية التي في {سورة…}» names the surah, not the ayah
                bare = lambda x: re.sub(r"[\u064b-\u0652\u0670]", "", x).strip(" {(")
                if bare(t[max(0, m.start() - 12):m.start()]).endswith("هكذا"): continue
                if bare(t[m.end():m.end() + q.start() + 1]).endswith("التي في"): continue
                k = ayah_of(q.group(1))
                if not k: continue
                if any(x["b"] == b and x["n"] == r["n"] for x in reports[k]): break
                # shown from a little before the report (the chain of narrators comes first); «النص كاملًا» shows all
                st = max(0, m.start() - 350)
                while st > 0 and t[st - 1] not in " ،.": st -= 1
                reports[k].append({"b": b, "n": r["n"], "t": t, "st": st,
                                   "src": f"{hb.NAMES[b]} ({r['n']})" + ("" if b in ("bukhari", "muslim") else
                                          " — " + "؛ ".join(f"{a}: {x}" for a, x in r["g"]))})
                break
    for k in reports:     # the two Sahihs first, at most four narrations per ayah
        reports[k] = sorted(reports[k], key=lambda x: (order[x["b"]], len(x["t"])))[:4]
    for k, v in reports.items():
        s = k.split(":")[0]
        info[s].setdefault("nuzul", {})[k.split(":")[1]] = v
    json.dump({"src": SRC_AIMS, "surahs": info}, open(f"{OUT}/surahs_info.json", "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    print("surah aims: 114 · ayahs with revelation reports:", len(reports), "· narrations:", sum(len(v) for v in reports.values()))
