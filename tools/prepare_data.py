#!/usr/bin/env python3
"""Builds the offline assets of the Mushaf app.

Sources (all public, fetched from GitHub):
  * Page fonts: King Fahd Glorious Quran Printing Complex (KFGQPC) QCF V2 fonts
    and the page/line layout with per-word glyph codes — github.com/zonetecde/mushaf-layout (QPC2), pinned
  * Official KFGQPC Hafs text (v22) and its font, for every place the Quran is shown as text
      github.com/mustafa0x/qpc-fonts/text-mushafs/UthmanicHafs_V22
  * Tafsir Ibn Kathir (Arabic) and "Al-Muyassar fi Gharib Al-Quran" (KFGQPC)
      github.com/spa5k/tafsir_api   (from quran.com / QUL)
  * Juz / hizb / sajda / revelation metadata
      github.com/fawazahmed0/quran-api  info.json

Usage:  python3 prepare_data.py <download_dir> <android_assets_dir>
The script FAILS (non-zero exit) on any integrity problem, so a broken
data set can never reach a release build.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import json, os, re, sys,  difflib, collections, shutil, urllib.request, concurrent.futures as cf

def ensure_fonttools():
    """fontTools (+brotli for WOFF2) is the only non-standard dependency; installed on demand in CI."""
    try:
        import fontTools, brotli  # noqa: F401
    except ImportError:
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", "fonttools", "brotli"])

def dehint(f):
    """Remove the TrueType hinting programs. The page fonts of pages 1–7, 11–16, 256 and 270 carry hinting instructions that
    Android's font engine runs at the page's text size and draws as broken pieces of letters; the other pages have
    none. The outlines and advance widths are unchanged, so the page looks exactly the same."""
    if "glyf" in f:
        glyf = f["glyf"]
        for name in f.getGlyphOrder():
            g = glyf[name]
            if hasattr(g, "program"):
                from fontTools.ttLib.tables import ttProgram
                g.program = ttProgram.Program(); g.program.fromBytecode(b"")
    for t in ("fpgm", "prep", "cvt ", "hdmx", "VDMX", "LTSH"):
        if t in f: del f[t]
    if "maxp" in f and hasattr(f["maxp"], "maxSizeOfInstructions"):
        m = f["maxp"]
        m.maxSizeOfInstructions = 0; m.maxFunctionDefs = 0; m.maxInstructionDefs = 0; m.maxStackElements = 0
        m.maxTwilightPoints = 0; m.maxStorage = 0; m.maxZones = 1


RAW = "https://raw.githubusercontent.com"
LAYOUT = RAW + "/zonetecde/mushaf-layout/421e9eaa991897ecd50d7abb79160651a45976d3"     # pinned: data must not change under us
QPC = RAW + "/mustafa0x/qpc-fonts/master"
SRC = {
    "layout": LAYOUT + "/mushaf/QPC2/page-{n:03d}.json",
    "font": LAYOUT + "/fonts/QPC2/p{n:03d}.woff2",
    "bsml": LAYOUT + "/fonts/QPC2/QPC2BSML.ttf",
    "hafs": QPC + "/text-mushafs/UthmanicHafs_V22/uthmanic_hafs_v22.ttf",
    "hafs_text": QPC + "/text-mushafs/UthmanicHafs_V22/UthmanicHafs%20v22.json",
    "tafsir": RAW + "/spa5k/tafsir_api/main/tafsir/ar-tafsir-ibn-kathir/{n}.json",
    "gharib": RAW + "/spa5k/tafsir_api/main/tafsir/al-muyassar-fi-al-gharib/{n}.json",
    "info": RAW + "/fawazahmed0/quran-api/1/info.json",
}

NAMES = "الفاتحة البقرة آل_عمران النساء المائدة الأنعام الأعراف الأنفال التوبة يونس هود يوسف الرعد إبراهيم الحجر النحل الإسراء الكهف مريم طه الأنبياء الحج المؤمنون النور الفرقان الشعراء النمل القصص العنكبوت الروم لقمان السجدة الأحزاب سبأ فاطر يس الصافات ص الزمر غافر فصلت الشورى الزخرف الدخان الجاثية الأحقاف محمد الفتح الحجرات ق الذاريات الطور النجم القمر الرحمن الواقعة الحديد المجادلة الحشر الممتحنة الصف الجمعة المنافقون التغابن الطلاق التحريم الملك القلم الحاقة المعارج نوح الجن المزمل المدثر القيامة الإنسان المرسلات النبأ النازعات عبس التكوير الانفطار المطففين الانشقاق البروج الطارق الأعلى الغاشية الفجر البلد الشمس الليل الضحى الشرح التين العلق القدر البينة الزلزلة العاديات القارعة التكاثر العصر الهمزة الفيل قريش الماعون الكوثر الكافرون النصر المسد الإخلاص الفلق الناس".split()

def fail(msg):
    print("ERROR:", msg); sys.exit(1)

def fetch(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r, open(path + ".part", "wb") as f:
                shutil.copyfileobj(r, f)
            os.replace(path + ".part", path)
            return
        except Exception as e:
            err = e
    fail(f"download failed {url}: {err}")

def download(D):
    jobs = []
    for n in range(1, 605):
        jobs.append((SRC["layout"].format(n=n), f"{D}/layout2/page-{n:03d}.json"))
        jobs.append((SRC["font"].format(n=n), f"{D}/fonts2/p{n:03d}.woff2"))
    for n in range(1, 115):
        jobs.append((SRC["tafsir"].format(n=n), f"{D}/tafsir/{n}.json"))
        jobs.append((SRC["gharib"].format(n=n), f"{D}/gharib/{n}.json"))
    jobs += [(SRC["bsml"], f"{D}/fonts2/bsml.ttf"), (SRC["hafs"], f"{D}/fonts2/hafs_v22.ttf"),
             (SRC["hafs_text"], f"{D}/hafs_v22.json"), (SRC["info"], f"{D}/info.json")]
    with cf.ThreadPoolExecutor(8) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))

# ---------------------------------------------------------------- text utils
MARKS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـۥۦ]")
def skel(s):
    s = MARKS.sub("", s)
    s = re.sub("[٠-٩۰-۹  ()﴿﴾]", "", s)
    s = s.replace("ٱ", "ا").replace("آ", "ا").replace("أ", "ا").replace("إ", "ا")
    s = s.replace("ى", "ي").replace("ؤ", "ء").replace("ئ", "ء").replace("ة", "ه").replace("ۥ", "").replace("ۦ", "")
    return s

# ---------------------------------------------------------------- build
def main():
    if len(sys.argv) != 3:
        print(__doc__); sys.exit(2)
    D, OUT = sys.argv[1], sys.argv[2]
    download(D)
    info = json.load(open(f"{D}/info.json"))

    ensure_fonttools()
    import layout_v2
    surah_last = {ch["chapter"]: len(ch["verses"]) for ch in info["chapters"]}
    os.makedirs(f"{D}/ttf2", exist_ok=True)

    pages = []
    words_text = {}           # (s,a,w) -> word text
    repaired = []
    max_line = 0.0
    for p in range(1, 605):
        ttf = f"{D}/ttf2/p{p}.ttf"
        if not os.path.exists(ttf) or not os.path.exists(ttf + ".unhinted"):
            from fontTools.ttLib import TTFont
            f = TTFont(f"{D}/fonts2/p{p:03d}.woff2"); f.flavor = None
            for t in ("DSIG",):
                if t in f: del f[t]
            dehint(f)
            f.save(ttf)
            open(ttf + ".unhinted", "w").close()
        adv = layout_v2.load_font_metrics(ttf)
        d = json.load(open(f"{D}/layout2/page-{p:03d}.json"))
        if d["page"] != p: fail(f"page mismatch {p}")
        lines = []
        toks = []             # flat list [glyph, s, a, w, lineIndex, isMarker]
        for ln in d["lines"]:
            t = ln["type"]
            if t == "surah-header":
                lines.append({"t": "h"})
            elif t == "basmala":
                lines.append({"t": "b"})
            elif t == "text":
                li = len(lines); lines.append({"t": "x"})
                for w in ln["words"]:
                    s, a, i = map(int, w["location"].split(":"))
                    wt = re.sub(r"\s*[٠-٩]+$", "", w["word"]).strip()
                    if wt: words_text[(s, a, i)] = wt
                    gs = w["qpcV2"].replace(" ", "")
                    ends_num = w["word"].strip()[-1:] in "٠١٢٣٤٥٦٧٨٩"
                    for gi, c in enumerate(gs):
                        toks.append([c, s, a, i, li, ends_num and gi == len(gs) - 1])
            else:
                fail(f"unknown line type {t} p{p}")
        repaired += layout_v2.repair_page(p, lines, toks, adv, surah_last, fail)
        if p > 2:
            for li, l in enumerate(lines):
                if l["t"] == "x": max_line = max(max_line, layout_v2.width([t for t in toks if t[4] == li], adv))
        # ---- headers: assign the surah that actually starts after them
        hdr_lines = [i for i, l in enumerate(lines) if l["t"] == "h"]
        starts = [t for t in toks if t[2] == 1]
        # surahs starting on this page in reading order
        new_surahs = list(dict.fromkeys(t[1] for t in toks if t[2] == 1 and t[3] == 1))
        for hi in hdr_lines:
            nxt = [t[1] for t in toks if t[4] > hi and t[2] == 1]
            if nxt: lines[hi]["s"] = nxt[0]
            else:       # header at the very bottom: next page's first surah
                nd = json.load(open(f"{D}/layout2/page-{p+1:03d}.json"))
                loc = [w["location"] for l in nd["lines"] if l["type"] == "text" for w in l["words"]][0]
                lines[hi]["s"] = int(loc.split(":")[0])
        # a header followed by nothing of its surah on this page belongs at the bottom
        for hi in hdr_lines:
            if not any(t[4] > hi and t[2] == 1 and t[1] == lines[hi]["s"] for t in toks) and hi != len(lines) - 1:
                h = lines.pop(hi); lines.append(h)
                for t in toks:
                    if t[4] > hi: t[4] -= 1
                repaired.append(f"{p}:moved-hdr{h['s']}")
        # ---- encode text lines
        for li, l in enumerate(lines):
            if l["t"] != "x": continue
            lt = [t for t in toks if t[4] == li]
            l["g"] = "".join(t[0] for t in lt)
            l["k"] = [[t[1], t[2], t[3]] for t in lt]
        pages.append({"p": p, "lines": lines})

    # ---- post pass: every surah (except 1) needs a header, and a basmala (except 9)
    def starts_on(pg):
        out = []
        for li, l in enumerate(pg["lines"]):
            if l["t"] == "x":
                for k in l["k"]:
                    if k[1] == 1 and k[2] == 1: out.append((k[0], li))
        return out
    for i, pg in enumerate(pages):
        for s, li in starts_on(pg):
            L = pg["lines"]
            has_b = s in (1, 9) or (li > 0 and L[li - 1]["t"] == "b")
            hpos = li - (2 if s not in (1, 9) else 1)
            has_h_here = hpos >= 0 and L[hpos]["t"] == "h" and L[hpos].get("s") == s
            prev = pages[i - 1]["lines"] if i else None
            has_h_prev = hpos < 0 and prev and prev[-1]["t"] == "h" and prev[-1].get("s") == s
            if s == 1: continue
            if has_b and (has_h_here or has_h_prev): continue
            if not has_b:
                L.insert(li, {"t": "b"}); li += 1; repaired.append(f"{pg['p']}:+basmala{s}")
            hpos = li - (2 if s != 9 else 1)
            if hpos >= 0 and L[hpos]["t"] == "h" and L[hpos].get("s") == s: continue
            if hpos < 0 and prev is not None and len(prev) < 15:
                prev.append({"t": "h", "s": s}); repaired.append(f"{pg['p']-1}:+hdr{s}@bottom")
            else:
                L.insert(max(hpos + 1, 0) if hpos < 0 else hpos + 1, {"t": "h", "s": s}); repaired.append(f"{pg['p']}:+hdr{s}")
    for pg in pages:
        if any(l["t"] == "h" and "s" not in l for l in pg["lines"]): fail(f"unlabelled header p{pg['p']}")

    # ---- mark ayah-end glyphs (last glyph of every ayah)
    last = {}
    for pg in pages:
        for li, l in enumerate(pg["lines"]):
            if l["t"] != "x": continue
            for ti, k in enumerate(l["k"]):
                last[(k[0], k[1])] = (pg["p"], li, ti)
    for (s, a), (p, li, ti) in last.items():
        pages[p - 1]["lines"][li]["k"][ti][2] = 0      # w=0 means ayah marker
    if len(last) != 6236: fail(f"ayah count {len(last)} != 6236")

    # ---- every page has 15 lines (except 1, 2)
    odd = [(pg["p"], len(pg["lines"])) for pg in pages if len(pg["lines"]) != 15 and pg["p"] > 2]
    if odd: fail(f"pages without 15 lines: {odd}")
    print("layout repairs:", repaired)

    # ---- gharib (word meanings) matched to word positions
    gharib = []
    gidx = {}     # (s,a,w) -> gharib id
    unmatched = 0
    ayah_skel = {}
    for k in sorted(words_text): ayah_skel[(k[0], k[1])] = ayah_skel.get((k[0], k[1]), "") + skel(words_text[k])
    gharib_fixed = []
    for s in range(1, 115):
        for e in json.load(open(f"{D}/gharib/{s}.json")):
            for m in re.finditer(r"﴿([^﴾]+)﴾\s*:?\s*(.+?)(?=\n\s*\n﴿|\n﴿|$)", e["text"], re.S):
                a = e["ayah"]
                phrase, meaning = m.group(1).strip(), m.group(2).strip()
                # source slips found by the data audit (docs/AUDIT.md): a meaning paired with the wrong word,
                # entries filed under the neighbouring ayah, and fragments with no meaning
                if (s, a) == (10, 28) and skel(phrase).startswith("قطع"): phrase = "مَكَانَكُمۡ"; gharib_fixed.append(f"{s}:{a} word")
                if not re.search(r"[ء-ي]", meaning) or meaning.startswith("["):     # «.» / «[الأنفال: ٣٠].»
                    gharib_fixed.append(f"{s}:{a} dropped"); continue
                if skel(phrase) not in ayah_skel.get((s, a), ""):
                    near = [b for b in (a + 1, a - 1, a + 2, a - 2) if skel(phrase) in ayah_skel.get((s, b), "")]
                    if near: gharib_fixed.append(f"{s}:{a}->{near[0]}"); a = near[0]
                ws = sorted((k for k in words_text if k[0] == s and k[1] == a), key=lambda k: k[2])
                target = skel(phrase)
                best = None
                for i in range(len(ws)):
                    acc = ""
                    for j in range(i, min(i + 8, len(ws))):
                        acc += skel(words_text[ws[j]])
                        r = 1.0 if acc == target else difflib.SequenceMatcher(None, acc, target).ratio()
                        if best is None or r > best[0]: best = (r, i, j)
                        if len(acc) > len(target) + 3: break
                gid = len(gharib)
                gharib.append({"s": s, "a": a, "w": phrase, "m": meaning})
                if best and best[0] >= 0.8:
                    for j in range(best[1], best[2] + 1):
                        gidx.setdefault(ws[j], gid)
                else:
                    unmatched += 1
    print(f"gharib entries {len(gharib)}, unmatched {unmatched}, corrected {gharib_fixed}")
    if unmatched > len(gharib) * 0.03: fail("too many unmatched gharib entries")
    for pg in pages:
        for l in pg["lines"]:
            if l["t"] == "x":
                # ids >= 0: coloured "difficult word"; ids <= -2: phrase explanation (tappable, not coloured)
                ms = []
                for k in l["k"]:
                    gid = gidx.get((k[0], k[1], k[2]), -1) if k[2] else -1
                    if gid >= 0 and len(gharib[gid]["w"].split()) > 3: gid = -(gid + 2)
                    ms.append(gid)
                l["m"] = ms

    # ---- tafsir ibn kathir: dedupe ranges, fix the 1:7 / 2:1 boundary
    taf = {}
    for s in range(1, 115):
        taf[s] = [x["text"] for x in json.load(open(f"{D}/tafsir/{s}.json"))]
    mk = "تَفْسِيرُ سُورَةِ الْبَقَرَةِ"
    t21 = taf[2][0]
    if mk in t21:
        cut = t21.rfind("[بِسْمِ", 0, t21.find(mk))
        if cut <= 0: cut = t21.find(mk)
        old17 = taf[1][6]
        taf[1][6] = old17 + "\n\n" + t21[:cut].strip()
        newt = t21[cut:].strip()
        for i, t in enumerate(taf[2]):
            if t == t21: taf[2][i] = newt
    os.makedirs(f"{OUT}/tafsir", exist_ok=True)
    for s in range(1, 115):
        groups = []
        for a, t in enumerate(taf[s], 1):
            if groups and groups[-1]["x"] == t: groups[-1]["to"] = a
            else: groups.append({"from": a, "to": a, "x": t})
        json.dump([[g["from"], g["to"], g["x"]] for g in groups],
                  open(f"{OUT}/tafsir/{s}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

    # ---- metadata
    surahs = []
    first_page = {}
    for pg in pages:
        for l in pg["lines"]:
            if l["t"] == "x":
                for k in l["k"]:
                    if k[1] == 1: first_page.setdefault(k[0], pg["p"])
    ayah_page = {}
    for pg in pages:
        for l in pg["lines"]:
            if l["t"] == "x":
                for k in l["k"]: ayah_page.setdefault((k[0], k[1]), pg["p"])
    for ch in info["chapters"]:
        s = ch["chapter"]
        hdrname = None
        surahs.append({"n": s, "name": NAMES[s - 1].replace("_", " "), "ayahs": len(ch["verses"]),
                       "type": "مكية" if ch["revelation"].lower().startswith("mec") else "مدنية",
                       "page": first_page[s]})
    juz = [{"n": j["juz"], "s": j["start"]["chapter"], "a": j["start"]["verse"],
            "page": ayah_page[(j["start"]["chapter"], j["start"]["verse"])]} for j in info["juzs"]["references"]]
    rub = [{"n": q["maqra"], "s": q["start"]["chapter"], "a": q["start"]["verse"],
            "page": ayah_page[(q["start"]["chapter"], q["start"]["verse"])]} for q in info["maqras"]["references"]]
    sajda = [{"s": x["chapter"], "a": x["verse"]} for x in info["sajdas"]["references"]]
    if len(juz) != 30 or len(rub) != 240: fail("juz/rub metadata")
    page_ranges = []
    for pg in pages:
        ks = [(k[0], k[1]) for l in pg["lines"] if l["t"] == "x" for k in l["k"]]
        page_ranges.append([ks[0][0], ks[0][1], ks[-1][0], ks[-1][1]])
    os.makedirs(f"{OUT}/fonts", exist_ok=True)
    ap = [[ayah_page[(s, a)] for a in range(1, surahs[s - 1]["ayahs"] + 1)] for s in range(1, 115)]
    json.dump({"ap": ap, "surahs": surahs, "juz": juz, "rub": rub, "sajda": sajda, "pageRanges": page_ranges,
               "maxLine": round(max_line * 100 + 0.5, 1)},
              open(f"{OUT}/meta.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    if len(NAMES) != 114: fail("names")
    os.makedirs(f"{OUT}/pages", exist_ok=True)
    for pg in pages:
        json.dump(pg["lines"], open(f"{OUT}/pages/{pg['p']}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump([[g["s"], g["a"], g["w"], g["m"]] for g in gharib],
              open(f"{OUT}/gharib.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    # word text (for copy / search), per ayah
    ayah_words = collections.defaultdict(list)
    for (s, a, w) in sorted(words_text): ayah_words[f"{s}:{a}"].append(words_text[(s, a, w)])
    src_text = {k: " ".join(v) for k, v in ayah_words.items()}
    word_counts = {k: len(v) for k, v in ayah_words.items()}
    import extras_prep
    extras_prep.build_all(D, OUT, src_text, word_counts, [x["ayahs"] for x in surahs])
    # every text the app shows comes from the official KFGQPC Hafs text + its own font (no conversions)
    import official_text
    official_text.apply(D, OUT, pages, surahs, words_text)
    for pg in pages:
        json.dump(pg["lines"], open(f"{OUT}/pages/{pg['p']}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    for n in range(1, 605):
        shutil.copy(f"{D}/ttf2/p{n}.ttf", f"{OUT}/fonts/p{n}.ttf")
    shutil.copy(f"{D}/fonts2/bsml.ttf", f"{OUT}/fonts/bsml.ttf")
    shutil.copy(f"{D}/fonts2/hafs_v22.ttf", f"{OUT}/fonts/hafs.otf")
    print("OK: assets written to", OUT)

if __name__ == "__main__":
    main()
