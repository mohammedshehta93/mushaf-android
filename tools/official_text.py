"""The official KFGQPC Hafs text (Uthmanic Hafs v22) with its own font, for every place the Quran is shown as text.

The KFGQPC text uses its own encoding (e.g. open tanween, small high signs), so it is only ever rendered with the
font it was made for — never converted. This module:
  * maps the tajweed annotations (computed on the word-by-word text) onto the official text, character by character;
  * assigns every word of the official text to its word in the page layout, so the coloured tajweed page has
    exactly the lines of the printed Mushaf;
  * replaces text.json / tajweed.json with the official text and its annotations.
The build fails if anything cannot be matched.
"""
import difflib, json

EQ = {"ْ": "ۡ", "ࣰ": "ً", "ࣱ": "ٌ", "ࣲ": "ٍ", "ـ": ""}
LETTER_MAP = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ۦ": "", "ۥ": "", "ة": "ه", "ؤ": "ء", "ئ": "ء"}
PAUSE = set("ۖۗۘۙۚۛۜ۩")


def _norm(t):
    out, idx = [], []
    for i, c in enumerate(t):
        c = EQ.get(c, c)
        if c in ("", " ", "۞", "۩"): continue
        out.append(c); idx.append(i)
    return out, idx


def _skel(t):
    out, idx = [], []
    for i, c in enumerate(t):
        c = LETTER_MAP.get(c, c)
        if c and "ء" <= c <= "ي": out.append(c); idx.append(i)
    return "".join(out), idx


def map_spans(old_text, spans, new_text):
    on, oi = _norm(old_text); nn, ni = _norm(new_text)
    m = {}
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, on, nn, autojunk=False).get_opcodes():
        if op == "equal" or (op == "replace" and i2 - i1 == j2 - j1):
            for o in range(i2 - i1): m[oi[i1 + o]] = ni[j1 + o]
    out, lost = [], 0
    for i in range(0, len(spans), 3):
        r, st, en = spans[i:i + 3]
        tgt = [m[x] for x in range(st, en) if x in m]
        if not tgt:
            if old_text[st:en].strip(): lost += 1
            continue
        out += [r, min(tgt), max(tgt) + 1]
    return out, lost


def word_tokens(text, words):
    """Splits the official ayah text into tokens and gives each the index (into `words`) of its layout word."""
    ls, lidx = "", []
    for wi, w in enumerate(words):
        sk, _ = _skel(w); ls += sk; lidx += [wi] * len(sk)
    vs, vidx = _skel(text)
    vmap = {}
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, vs, ls, autojunk=False).get_opcodes():
        if op == "equal":
            for o in range(i2 - i1): vmap[vidx[i1 + o]] = lidx[j1 + o]
        elif op == "replace":
            for o in range(i2 - i1): vmap[vidx[i1 + o]] = lidx[min(j1 + o, j2 - 1)]
    toks, pos = [], 0
    for tok in text.split(" "):
        start = pos; pos += len(tok) + 1
        if not tok: continue
        ws = [vmap[i] for i in range(start, start + len(tok)) if i in vmap]
        toks.append([tok, start, min(ws) if ws else None, max(ws) if ws else None])
    for i, t in enumerate(toks):          # ۞ goes with the next word, a lone pause sign with the previous one
        if t[2] is None:
            if t[0] == "۞":
                nx = [x[2] for x in toks[i + 1:] if x[2] is not None]; t[2] = t[3] = nx[0] if nx else 0
            else:
                pv = [x[3] for x in toks[:i] if x[3] is not None]; t[2] = t[3] = pv[-1] if pv else 0
    return toks


def apply(D, OUT, pages, surahs, words_text):
    v22 = json.load(open(f"{D}/hafs_v22.json", encoding="utf-8"))
    keys = [(s["n"], a) for s in surahs for a in range(1, s["ayahs"] + 1)]
    if len(v22) != 6236 or any(v[1] != i + 1 for i, v in enumerate(v22)):
        raise SystemExit("ERROR: official Hafs text does not have the 6236 ayahs in order")
    official = {f"{s}:{a}": v[0] for (s, a), v in zip(keys, v22)}

    old_text = json.load(open(f"{OUT}/text.json", encoding="utf-8"))
    old_tj = json.load(open(f"{OUT}/tajweed.json"))
    tj, lost = {}, 0
    for k, t in official.items():
        tj[k], l = map_spans(old_text[k], old_tj.get(k, []), t); lost += l
    print("tajweed spans moved to the official text; lost:", lost)
    if lost > 20: raise SystemExit("ERROR: too many tajweed annotations could not be placed on the official text")

    # words of every ayah in layout order, and where each word is on the pages
    words = {}            # (s,a) -> [(w, text)]
    where = {}            # (s,a,w) -> (page index, line index, glyph index of the word's first glyph)
    for pi, pg in enumerate(pages):
        for li, l in enumerate(pg["lines"]):
            if l["t"] != "x": continue
            for gi, k in enumerate(l["k"]):
                where.setdefault(tuple(k), (pi, li, gi))
    for (s, a, w), txt in words_text.items(): words.setdefault((s, a), []).append((w, txt))
    problems = []
    for (s, a) in keys:
        k = f"{s}:{a}"
        ws = sorted(words[(s, a)])
        toks = word_tokens(official[k], [x[1] for x in ws])
        spans = tj[k]
        for tok, start, lo, hi in toks:
            w = ws[lo][0]
            if lo != hi:
                # a token spanning two layout words (e.g. «لَّوۡمَا»): both must be on the same line
                if where.get((s, a, ws[lo][0]), (0, 0))[:2] != where.get((s, a, ws[hi][0]), (1, 1))[:2]:
                    problems.append(f"{k} {tok}")
            loc = where.get((s, a, w))
            if loc is None: problems.append(f"{k} word {w} not on any page"); continue
            pi, li, gi = loc
            rel = []
            for i in range(0, len(spans), 3):
                r, st, en = spans[i:i + 3]
                a0, b0 = max(st, start), min(en, start + len(tok))
                if a0 < b0: rel += [r, a0 - start, b0 - start]
            pages[pi]["lines"][li].setdefault("v", []).append([gi, tok] + ([rel] if rel else []))
    if problems: raise SystemExit("ERROR: official text does not fit the layout: " + "; ".join(problems[:10]))
    for pg in pages:          # keep reading order within each line
        for l in pg["lines"]:
            if "v" in l: l["v"].sort(key=lambda x: x[0])

    json.dump(official, open(f"{OUT}/text.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(tj, open(f"{OUT}/tajweed.json", "w"), separators=(",", ":"))
    import extras_prep, hadith_sharh
    extras_prep.build_learning(OUT)          # tajweed lesson examples, now from the official text
    sharh = hadith_sharh.build(D, OUT)       # explanations (network; never fails)
    extras_prep.attach_daily_sharh(OUT, sharh)
    extras_prep.build_reading(D, OUT, sharh) # intentions, tadabbur reminders, virtues — from the official text
    extras_prep.build_english(D, OUT)
