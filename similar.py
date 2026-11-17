"""«آيات متشابهة» — OUT/similar.json, computed from the official text only (no outside source).

Two ayahs are listed together when they share a run of at least MIN consecutive words (letters compared without
diacritics, so the Uthmani spelling of the same words matches). Very common formulae (a 5-word run found in more than
COMMON ayahs) only count when the shared run is longer. At most LIMIT per ayah, the longest shared runs first.
Output: {"s:a": [["s:a", words], ...]}.
"""
import collections, json, re

MIN, COMMON, LONG, LIMIT = 5, 12, 8, 6
_MAP = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي", "ی": "ي"}


def _word(w):
    w = "".join(_MAP.get(c, c) for c in w if "ء" <= c <= "ي" or c in _MAP)
    return w.replace("ا", "").replace("و", "").replace("ي", "")      # spelling differences of long vowels


def words_of(text):
    return [x for x in (_word(w) for w in re.split(r"\s+", text)) if x]


def build(D, OUT):
    text = json.load(open(f"{OUT}/text.json", encoding="utf-8"))
    keys = list(text)
    W = {k: words_of(text[k]) for k in keys}
    grams = collections.defaultdict(set)
    for k in keys:
        w = W[k]
        for i in range(len(w) - MIN + 1):
            grams[tuple(w[i:i + MIN])].add(k)

    def longest(a, b):
        """Longest run of consecutive words shared by ayahs a and b."""
        x, y, best = W[a], W[b], 0
        prev = [0] * (len(y) + 1)
        for i in range(1, len(x) + 1):
            cur = [0] * (len(y) + 1)
            for j in range(1, len(y) + 1):
                if x[i - 1] == y[j - 1]:
                    cur[j] = prev[j - 1] + 1
                    if cur[j] > best: best = cur[j]
            prev = cur
        return best

    pairs = collections.defaultdict(dict)
    for g, ks in grams.items():
        if len(ks) < 2: continue
        common = len(ks) > COMMON
        ks = sorted(ks)
        for i, a in enumerate(ks):
            for b in ks[i + 1:]:
                if b in pairs[a]: continue
                n = longest(a, b)
                if n < MIN or (common and n < LONG): continue
                pairs[a][b] = n; pairs[b][a] = n
    order = {k: i for i, k in enumerate(keys)}
    out = {k: [[b, n] for b, n in sorted(v.items(), key=lambda x: (-x[1], order[x[0]]))[:LIMIT]] for k, v in pairs.items() if v}
    json.dump(out, open(f"{OUT}/similar.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("similar ayahs: ayahs with similar ones", len(out), "· links", sum(len(v) for v in out.values()))
    return out
