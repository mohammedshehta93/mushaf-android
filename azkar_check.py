"""Grades the adhkar (azkar-db, based on Hisn al-Muslim) against the six books, and removes additions that are weak.

For every dhikr found in a narration of the six books / al-Muwatta (≥ 90% of its words in one narration):
  * in al-Bukhari or Muslim → «رواه البخاري (رقم)»;
  * otherwise the rulings of the graders recorded with that narration (al-Albani, Shu'ayb al-Arna'ut, …) and a summary:
    agreed sound / agreed weak / disputed — with what most of those graders said («الأكثر على»), or «متكافئ» on a tie.
    When the narration is weak but a sound narration carries most of the same words, that one is named too.
Nothing is concluded beyond what the recorded graders said (the app does not make its own tarjih).

REMOVE lists additions of the azkar-db file that are not in Hisn al-Muslim and are weak in every recorded ruling, or
whose source could not be verified (docs/AUDIT.md).
"""
import json, re
import hadith_books as hb

REMOVE = [
    # (category or None, start of the text) — reason
    (None, "يا رب لك الحمد كما ينبغي لجلال وجهك"),                   # ابن ماجه 3801: ضعيف (الألباني، شعيب، فؤاد عبد الباقي)
    ("أذكار الصباح", "اللهم اني اعوذ بك من الهم والحزن"),          # بهذا السياق: أبو داود 1555 ضعيف؛ الدعاء نفسه في البخاري (باب الهم والحزن)
    ("أذكار المساء", "اللهم اني اعوذ بك من الهم والحزن"),
    (None, "اللهم انا نسالك من خير ما سالك منه نبيك"),             # الترمذي 3521: ضعيف
    (None, "ربنا الله الذي في السماء"),                              # أبو داود 3892: ضعيف
    (None, "بسم الله العظيم اعوذ بالله الكبير"),                     # الترمذي 2075: ضعيف
    (None, "اللهم اني اعوذ بوجهك الكريم وكلماتك التامات"),          # أبو داود 5052: ضعيف
    (None, "اللهم انت ربي لا اله الا انت عليك توكلت"),              # ليس في حصن المسلم، ولم يُعثر على مصدره
    (None, "اللهم اشف عبدك وصدق رسولك"),                            # ليس في حصن المسلم، ولم يُعثر على مصدره
    (None, "اللهم بارك عليه واذهب عنه حر العين"),                   # ليس في حصن المسلم، ولم يُعثر على مصدره
]

# (category or None, start of the text) → the narration to cite instead of the automatic match:
#   a book/number, or None = do not grade (Quran text that also appears in tafsir narrations, or a dhikr whose wording is
#   in another context in the six books)
MANUAL = [
    (None, "حسبي الله لا اله الا هو عليه توكلت", ("abudawud", "5081")),    # النص آية (التوبة ١٢٩)؛ الذكر صباحًا ومساءً: أبو داود 5081
    (None, "ربنا اتنا في الدنيا حسنه", ("abudawud", "1892")),             # بين الركنين: أبو داود 1892
    ("دعاء السجود", "اللهم اني اعوذ برضاك من سخطك", ("muslim", "486")),
    ("الذكر بعد الفراغ من الوضوء", "سبحانك اللهم وبحمدك", None),          # بعد الوضوء: عند النسائي في عمل اليوم والليلة (ليس في الكتب الستة)
    ("الرقية الشرعية من القرآن الكريم", "", None),                      # آيات وسور
    ("الرقية الشرعية من السنة النبوية", "قل هو الله احد", None),
]

SOUND = ("صحيح", "حسن")
WHO = {"bukhari": "البخاري", "muslim": "مسلم", "abudawud": "أبو داود", "tirmidhi": "الترمذي", "nasai": "النسائي",
       "ibnmajah": "ابن ماجه", "malik": "مالك في الموطأ"}
EVENING = [("امسينا", "اصبحنا"), ("امسيت", "اصبحت"), ("امسي", "اصبح"), ("امسى", "اصبح")]


def _plain(t): return hb.norm_with_index(t)[0]


def _morning(p):
    """Evening wording → the morning wording of the same narration («أمسينا» → «أصبحنا»)."""
    return " ".join(dict(EVENING).get(w, w) for w in p.split())


def coverage(text, rec):
    """Share of the dhikr's distinctive 6-word runs found in the narration (formulae found in more than 25
    narrations, like the tahlil, say nothing about which narration it is and are left out)."""
    w = _morning(_plain(text)).split()
    if len(w) < 8: return 0.0
    wins = [" ".join(w[k:k + 6]) for k in range(0, max(1, len(w) - 5), 2)]
    wins = [x for x in wins if len((hb._shingles or {}).get(x, ())) <= 25]
    if len(wins) < 2: return 0.0
    p = _plain(rec["t"])
    return sum(1 for x in wins if x in p) / max(1, len(wins))


def _is_sound(term): return term.startswith(SOUND) or term.startswith(("موقوف صحيح", "موقوف حسن"))


def _best_in(D, text, book):
    """The narration of [book] that carries most of the dhikr's words, with its coverage."""
    best = (0, None)
    for r in hb.load(D)[book]:
        c = coverage(text, r)
        if c > best[0]: best = (c, r)
    return best


def _manual(cat, text):
    p = _plain(text)
    for c, start, target in MANUAL:
        if (c is None or c == cat) and p.startswith(_plain(start)): return True, target
    return False, None


def grading(D, text, ref="", cat=""):
    books = hb.load(D)
    hit, target = _manual(cat, text)
    if hit:
        if target is None: return None
        rec = next(r for r in books[target[0]] if r["n"] == target[1])
        return _describe(D, text, target, rec, check_origin=False)
    hb.grade_of(D, "ا ب ت ث ج ح خ")          # builds the shared index
    g = hb.grade_of(D, text) or hb.grade_of(D, _morning(_plain(text)))
    rec = next(r for r in books[g[0]] if r["n"] == g[1]) if g else None
    if not rec or coverage(text, rec) < 0.9:
        # the book named by Hisn al-Muslim's own reference: accept a narration there that carries most of the words
        named = [b for b, w in WHO.items() if ref.strip().startswith(w)]
        if not named: return None
        c, r = _best_in(D, text, named[0])
        if c < 0.85: return None
        g, rec = (named[0], r["n"]), r
    return _describe(D, text, g, rec)


def _describe(D, text, g, rec, check_origin=True):
    books = hb.load(D)
    name = WHO[g[0]]
    if g[0] in ("bukhari", "muslim"): return f"رواه {name} ({g[1]})", True
    rulings = rec["g"]
    if not rulings: return f"رواه {name} ({g[1]})", None
    sound = [a for a, x in rulings if _is_sound(x)]
    weak = [a for a, x in rulings if not _is_sound(x)]
    detail = "، ".join(f"{a}: {x}" for a, x in rulings)
    if not weak: summary, ok = "صحيح أو حسن", True
    elif not sound:
        terms = [x for _, x in rulings]
        summary, ok = max(set(terms), key=terms.count), False
    else:
        ok = None
        summary = ("مختلف فيه، والأكثر على: " + ("التصحيح أو التحسين" if len(sound) > len(weak) else "التضعيف")
                   if len(sound) != len(weak) else "مختلف فيه (متكافئ بين من ذُكر)")
    line = f"رواه {name} ({g[1]}) — {detail}. الخلاصة: {summary}"
    if ok is not True and check_origin:      # a sound narration with most of the same words
        hits = []
        for b in ("bukhari", "muslim"):
            for r in books[b]:
                if coverage(text, r) >= 0.6: hits.append(f"{WHO[b]} ({r['n']})"); break
        if hits: line += f". وأصله صحيح عند {hits[0]}"
    return line, ok


def build(D, OUT):
    path = f"{OUT}/muslim/azkar.json"
    cats = json.load(open(path, encoding="utf-8"))
    removed, graded, disputed = [], 0, []
    rm = [(c, _plain(t)) for c, t in REMOVE]
    for cat in cats:
        keep = []
        for it in cat["i"]:
            p = _plain(it["z"])
            if any((c is None or c == cat["t"]) and p.startswith(t[:40]) for c, t in rm):
                removed.append(f"{cat['t']}: {it['z'][:40]}"); continue
            r = grading(D, it["z"], it.get("r", ""), cat["t"])
            if r:
                graded += 1
                it["r"] = r[0]
                if p.startswith(_plain("حسبي الله لا اله الا هو عليه توكلت")):
                    it["r"] = "النص آية من القرآن (التوبة ١٢٩) تُقرأ في كل وقت، والحكم هنا على ما ورد في فضل قولها سبعًا صباحًا ومساءً: " + it["r"]
                if r[1] is not True: disputed.append(f"{cat['t']}: {r[0]}")
            keep.append(it)
        cat["i"] = keep
    cats = [c for c in cats if c["i"]]
    if len(removed) < 10: raise SystemExit(f"ERROR: azkar removals not applied: {removed}")
    json.dump(cats, open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(f"azkar: graded {graded}, removed {len(removed)}, weak or disputed kept with their rulings {len(disputed)}")
    for d in disputed: print("   ", d)
