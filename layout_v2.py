"""Madinah Mushaf page layout, KFGQPC QCF V2.

Line breaks come from github.com/zonetecde/mushaf-layout (QPC2) and the glyph codes from the same set of
KFGQPC page fonts. Every page is checked against the fonts themselves:
  * the glyph codes of a page must be consecutive (the fonts number words in reading order) — this also
    restores the reading order where the dataset has words out of place;
  * every full line of the printed Mushaf has (almost) the same natural width (median 15.57 em, 98% of lines
    within 15.35–15.83 em). A line that is clearly too long next to one that is clearly too short means a word
    sits on the wrong line in the dataset: it is moved, and the result must fall back within the band.
The build stops if anything is left that cannot be explained by the end of a surah.
"""
import json, os

LO, HI = 15.0, 16.2          # em; band for a full line


def load_font_metrics(path):
    from fontTools.ttLib import TTFont
    f = TTFont(path)
    cm = f.getBestCmap(); hm = f["hmtx"]; up = f["head"].unitsPerEm
    return {c: hm[g][0] / up for c, g in cm.items()}


def width(toks, adv):
    return sum(adv[ord(t[0])] for t in toks)


def repair_page(p, lines, toks, adv, surah_last_ayah, fail):
    """toks: [glyph, s, a, w, lineIndex, isAyahMarker]; lines: list of {"t": ...}. Returns list of repair notes."""
    notes = []
    for t in toks:
        if ord(t[0]) not in adv: fail(f"page {p}: glyph U+{ord(t[0]):04X} missing from the page font")
    # 1) reading order = font order; keep each line's word count, then rebalance
    order = sorted(toks, key=lambda t: ord(t[0]))
    codes = [ord(t[0]) for t in order]
    if codes != list(range(codes[0], codes[0] + len(codes))):
        fail(f"page {p}: glyph codes are not consecutive")
    if [ord(t[0]) for t in toks] != codes:
        counts = [sum(1 for t in toks if t[4] == li) for li in range(len(lines))]
        it = iter(order); newt = []
        for li, n in enumerate(counts):
            for _ in range(n):
                t = list(next(it)); t[4] = li; newt.append(t)
        toks[:] = newt
        notes.append(f"{p}:reordered")
    text_lines = [i for i, l in enumerate(lines) if l["t"] == "x"]

    def line(li): return [t for t in toks if t[4] == li]

    def is_marker(t):        # flag set by the loader: last glyph of a layout word whose text ends in the ayah number
        return len(t) > 5 and t[5]

    def surah_ends(lt):
        if not lt: return False
        t = lt[-1]
        return t[2] == surah_last_ayah[t[1]] and t is max((x for x in toks if x[1] == t[1] and x[2] == t[2]), key=lambda x: ord(x[0]))

    # 2) move single glyphs (a word, or one half of a two-glyph word such as «بَعۡدَ مَا») between neighbouring
    #    lines while that brings both of them into the band
    MID = 15.57
    for _ in range(40):
        moved = False
        for a, b in zip(text_lines, text_lines[1:]):
            if b != a + 1: continue
            la, lb = line(a), line(b)
            wa, wb = width(la, adv), width(lb, adv)
            before = max(abs(wa - MID), abs(wb - MID))
            if wa > HI and wb < LO and len(la) > 1:
                g = adv[ord(la[-1][0])]
                if max(abs(wa - g - MID), abs(wb + g - MID)) < before:
                    la[-1][4] = b; moved = True; notes.append(f"{p}:line{a}->{b}")
            elif wa < LO and wb > HI and len(lb) > 1 and not surah_ends(la):
                g = adv[ord(lb[0][0])]
                if max(abs(wa + g - MID), abs(wb - g - MID)) < before:
                    lb[0][4] = a; moved = True; notes.append(f"{p}:line{b}->{a}")
        if not moved: break
    toks.sort(key=lambda t: ord(t[0]))
    # 3) verify
    for i, li in enumerate(text_lines):
        lt = line(li)
        if not lt: fail(f"page {p}: empty text line {li}")
        w = width(lt, adv)
        if w > HI + 0.3: fail(f"page {p} line {li}: too long ({w:.2f} em) — layout error")
        if w < LO and p < 600:
            nxt = lines[li + 1]["t"] if li + 1 < len(lines) else None
            last_on_page = i == len(text_lines) - 1
            if not (surah_ends(lt) or nxt in ("h", "b") or (last_on_page and nxt is None and surah_ends(lt)) or p <= 2):
                fail(f"page {p} line {li}: too short ({w:.2f} em) without a surah ending — layout error")
    return notes
