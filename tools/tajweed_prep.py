"""Tajweed annotations for the app's ayah text.

Uses the decision trees of github.com/cpfair/quran-tajweed (CC-BY 4.0) on our own text, after
normalising a few encoding differences, and maps every annotation back to our text's indices.
"""
import glob, json, os, re, sys, importlib, urllib.request

REPO = "https://raw.githubusercontent.com/cpfair/quran-tajweed/master"
RULES = ["ghunnah", "idghaam_ghunnah", "idghaam_no_ghunnah", "idghaam_mutajanisayn", "idghaam_mutaqaribayn",
         "idghaam_shafawi", "ikhfa", "ikhfa_shafawi", "iqlab", "madd_2", "madd_246", "madd_muttasil",
         "madd_munfasil", "madd_6", "qalqalah", "hamzat_wasl", "lam_shamsiyyah", "silent"]
NOON_RULES = {"ghunnah", "idghaam_ghunnah", "idghaam_no_ghunnah", "idghaam_mutajanisayn", "idghaam_mutaqaribayn",
              "idghaam_shafawi", "ikhfa", "ikhfa_shafawi", "iqlab"}
TANWEEN = "ًٌٍ"
PAUSE = "ۖۗۘۙۚۛۜ"


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        urllib.request.urlretrieve(url, path)


def variant(text, attach_pause):
    """Returns (normalised text, index map back into `text`)."""
    out, idx = [], []
    n = len(text)
    i = 0
    while i < n:
        ch = text[i]
        if ch == "ـ" and i + 1 < n and text[i + 1] == "ٰ":   # tatweel before dagger alif
            i += 1; continue
        if ch in "ۭۢ" and i > 0 and text[i - 1] in TANWEEN:
            rest = re.sub("[\\s" + PAUSE + "]", "", text[i + 1:i + 6])
            if rest.startswith("ب"):
                out.append("ۢ"); idx.append(i)       # iqlab: small high meem
            i += 1; continue                               # idghaam/ikhfa marker: dropped
        if attach_pause and ch == " " and i + 1 < n and text[i + 1] in PAUSE:
            i += 1; continue
        out.append(ch); idx.append(i)
        i += 1
    return "".join(out), idx


def classify(D, texts):
    tj = f"{D}/tajweed"
    fetch(f"{REPO}/tajweed_classifier.py", f"{tj}/tajweed_classifier.py")
    fetch(f"{REPO}/tree.py", f"{tj}/tree.py")
    for r in RULES:
        for k in ("start", "end"):
            fetch(f"{REPO}/rule_trees/{r}.{k}.json", f"{tj}/rule_trees/{r}.{k}.json")
    sys.path.insert(0, tj)
    clf = importlib.import_module("tajweed_classifier")
    tree = importlib.import_module("tree")
    trees = {r: {"start": tree.json2tree(json.load(open(f"{tj}/rule_trees/{r}.start.json"))),
                 "end": tree.json2tree(json.load(open(f"{tj}/rule_trees/{r}.end.json")))} for r in RULES}
    result = {}
    for key, text in texts.items():
        s, a = map(int, key.split(":"))
        spans = []
        for attach, wanted in ((False, lambda r: r not in NOON_RULES), (True, lambda r: r in NOON_RULES)):
            vt, idx = variant(text, attach)
            for an in clf.label_ayah((s, a, vt, trees))["annotations"]:
                if not wanted(an["rule"]) or an["end"] <= an["start"]: continue
                st = idx[an["start"]]; en = idx[an["end"] - 1] + 1
                spans.append([RULES.index(an["rule"]), st, en])
        spans.sort(key=lambda x: (x[1], x[2]))
        result[key] = spans
    return result


if __name__ == "__main__":
    texts = json.load(open(sys.argv[1]))
    res = classify(sys.argv[2], texts)
    json.dump(res, open(sys.argv[3], "w"), separators=(",", ":"))


def display_text(text):
    """Converts the layout's ayah text to the encoding the KFGQPC Uthmanic Hafs font renders cleanly.
    Returns (display text, index map display->source)."""
    out, idx = [], []
    n = len(text)
    for i, ch in enumerate(text):
        if ch == "۞":                       # ۞ rub' al-hizb sign: not part of the ayah
            continue
        if ch in "ۭۢ" and i > 0 and text[i - 1] in TANWEEN:
            rest = re.sub("[\\s" + PAUSE + "]", "", text[i + 1:i + 6])
            if rest.startswith("ب"):
                out.append("ۢ"); idx.append(i)
            continue
        if ch == "ۭ":
            continue
        if ch == "ْ": ch = "ۡ"         # sukun
        elif ch == "۟": ch = "ْ"       # small high rounded zero (silent letter)
        out.append(ch); idx.append(i)
    s = "".join(out).strip()
    lead = len("".join(out)) - len("".join(out).lstrip())
    return s, idx[lead:]


def remap(spans, idx):
    """Maps [rule, start, end] spans from source indices to display indices."""
    inv = {}
    for d, src in enumerate(idx):
        inv.setdefault(src, d)
    out = []
    for r, st, en in spans:
        ds = next((inv[k] for k in range(st, en) if k in inv), None)
        de = next((inv[k] + 1 for k in range(en - 1, st - 1, -1) if k in inv), None)
        if ds is not None and de is not None and de > ds:
            out.append([r, ds, de])
    return out
