"""The six books of hadith and al-Muwatta (Arabic text with the graders' rulings), from fawazahmed0/hadith-api.

Used for:
  * the hadith search / authenticity window (OUT/hadith/*.txt — every hadith verbatim, with its rulings);
  * quoting hadiths by book and number in tools/content/reading.json (verbatim, optionally an excerpt between two
    anchor phrases — never re-typed).
Bukhari and Muslim carry no grading (the whole book is sahih). For the others the rulings are the dataset's: al-Albani,
Shu'ayb al-Arna'ut, Ahmad Shakir, Bashar Awwad, Salim al-Hilali (al-Muwatta) and the editors; the grade words are
translated back to their standard Arabic terms with the table below (the build stops on an unknown term).
"""
import json, os, re, urllib.request

URL = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/editions/ara-{}.min.json"
BOOKS = [("bukhari", "صحيح البخاري"), ("muslim", "صحيح مسلم"), ("abudawud", "سنن أبي داود"), ("tirmidhi", "جامع الترمذي"),
         ("nasai", "سنن النسائي"), ("ibnmajah", "سنن ابن ماجه"), ("malik", "موطأ مالك")]
NAMES = dict(BOOKS)
GRADERS = {"Al-Albani": "الألباني", "Shuaib Al Arnaut": "شعيب الأرنؤوط", "Ahmad Muhammad Shakir": "أحمد شاكر",
           "Bashar Awad Maarouf": "بشار عواد", "Salim al-Hilali": "سليم الهلالي", "Abu Ghuddah": "عبد الفتاح أبو غدة",
           "Muhammad Muhyi Al-Din Abdul Hamid": "محيي الدين عبد الحميد", "Muhammad Fouad Abd al-Baqi": "محمد فؤاد عبد الباقي"}
# Zubair Ali Zai's grades (Urdu/English edition) are not shown.
GRADE = {
    "Sahih": "صحيح", "Daif": "ضعيف", "Hasan": "حسن", "Hasan Sahih": "حسن صحيح", "Sahih Lighairihi": "صحيح لغيره",
    "Hasan Lighairihi": "حسن لغيره", "Sahih Isnaad": "صحيح الإسناد", "Daif Isnaad": "ضعيف الإسناد", "Isnaad Daif": "ضعيف الإسناد",
    "Sanad Daif": "ضعيف الإسناد", "Hasan Isnaad": "حسن الإسناد", "Hasan Sahih Isnaad": "حسن صحيح الإسناد",
    "Very Daif": "ضعيف جدًا", "Very Daif Isnaad": "ضعيف الإسناد جدًا", "Shadh": "شاذ", "Shadh, Sahih": "شاذ، والمحفوظ صحيح",
    "Munkar": "منكر", "Munkar Daif": "منكر ضعيف", "Daif Munkar": "ضعيف منكر", "Mawdu": "موضوع", "Batil": "باطل",
    "Sahih Hadith": "صحيح", "Sahih Mutawatir": "صحيح متواتر", "Sahih Matn": "صحيح المتن", "Isnaad Malool": "إسناده معلول",
    "Mursal": "مرسل", "Sahih Isnaad Mursal": "صحيح الإسناد مرسل", "Mursal Sahih Isnaad": "مرسل صحيح الإسناد",
    "Mauquf": "موقوف", "Mauquf Sahih": "موقوف صحيح", "Sahih Muquf": "صحيح موقوف", "Sahih Isnaad Mauquf": "صحيح الإسناد موقوف",
    "Mauquf Hasan": "موقوف حسن", "Mauquf Daif": "موقوف ضعيف", "Daif Muquf": "ضعيف موقوف", "Mauquf Munkar": "موقوف منكر",
    "Mauquf Sahih Lighairihi": "موقوف صحيح لغيره", "Mauquf Hasan Lighairihi": "موقوف حسن لغيره",
    "Maqtu": "مقطوع", "Maqtu Sahih": "مقطوع صحيح", "Sahih Maqtu": "صحيح مقطوع", "Sahih Isnaad Maqtu": "صحيح الإسناد مقطوع",
    "Maqtu Daif": "مقطوع ضعيف", "Daif Maqtu": "ضعيف مقطوع", "Daif Isnaad Maqtu": "ضعيف الإسناد مقطوع",
    "Maqtu Hasan": "مقطوع حسن", "Hasan Maqtu": "حسن مقطوع", "Maqtu Sahih Lighairihi": "مقطوع صحيح لغيره",
}


def category_ar(term):
    """Like category(), for an Arabic term of the GRADE table."""
    en = [k for k, v in GRADE.items() if v == term]
    return category(en[0]) if en else None


def category(grade_en):
    """0 sound (sahih/hasan), 1 weak, 2 very weak / munkar / shadh, 3 fabricated; None: no verdict."""
    g = grade_en.strip()
    if not g or g == "-": return None
    if "Mawdu" in g or "Batil" in g: return 3
    if "Very" in g or "Munkar" in g or (g.startswith("Shadh") and "Sahih" not in g): return 2
    if "Daif" in g or "Malool" in g or g in ("Mursal", "Maqtu", "Mauquf"): return 1
    if "Sahih" in g or "Hasan" in g: return 0
    raise SystemExit(f"ERROR: unknown hadith grade {grade_en!r}")


_M = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭـ‏‎]")
_MAP = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ة": "ه"}


def norm_with_index(t):
    """Plain letters (as the app's search normalises them) and, for every letter, its index in t."""
    out, idx = [], []
    for i, ch in enumerate(t):
        if _M.match(ch) or ch in "ؤئء": continue
        ch = _MAP.get(ch, ch)
        if "ا" <= ch <= "ي" or ch == "ء": out.append(ch); idx.append(i)
        elif out and out[-1] != " ": out.append(" "); idx.append(i)
    return "".join(out), idx


def clean(t):
    return re.sub(r"[ \t]+", " ", t.replace("‏", "").replace("‎", "")).strip()


_cache = {}


def load(D):
    if _cache: return _cache
    for b, _ in BOOKS:
        path = f"{D}/hadith/ara-{b}.json"
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            urllib.request.urlretrieve(URL.format(b), path)
        recs = []
        last = None
        hs = json.load(open(path, encoding="utf-8"))["hadiths"]
        nxt = [None] * len(hs); cur = None
        for i in range(len(hs) - 1, -1, -1):
            nxt[i] = cur
            if hs[i].get("arabicnumber"): cur = str(hs[i]["arabicnumber"])
        for i, h in enumerate(hs):
            # Muslim is cited by Fu'ad Abd al-Baqi's numbers («arabicnumber»); a narration without one is a further
            # chain of the hadith before it (or of the introduction). The dataset's running number must never be shown
            # for Muslim: it collides with real numbers (audit: «Muslim 1354» was shown for «باعد بيني وبين خطاياي»).
            if b == "muslim":
                if h.get("arabicnumber"): last = str(h["arabicnumber"]).split(".")[0]; n = last
                else:
                    # the first narration of a hadith whose further chains are numbered «598.02…» has lost its number
                    f = nxt[i]
                    fi = int(f.split(".")[0]) if f else None
                    li = int(last) if last and last.isdigit() else None
                    if f and "." in f and f.split(".")[1] not in ("01", "1") and f.split(".")[0] != last: n = f.split(".")[0]
                    elif fi and li and fi - li >= 2: n = str(li + 1)          # a whole hadith number is missing here
                    else: n = last or "المقدمة"
                    if clean(h["text"]): last = n if n != "المقدمة" else last
            else:
                n = str(h["hadithnumber"]).split(".")[0]
            t = clean(h["text"])
            if not t: continue
            grades, cat = [], None
            for g in h.get("grades", []):
                if g["name"] not in GRADERS or g["grade"].strip() in ("", "-"): continue
                ar = GRADE.get(g["grade"].strip())
                if ar is None: raise SystemExit(f"ERROR: hadith grade {g['grade']!r} has no Arabic term")
                grades.append((GRADERS[g["name"]], ar))
                if g["name"] in ("Al-Albani", "Salim al-Hilali") and cat is None: cat = category(g["grade"])
            if b in ("bukhari", "muslim"): cat = 0
            # graders who disagree on sound vs not sound: say so, with what most of them said
            kinds = [category_ar(x) for _, x in grades]
            kinds = [k for k in kinds if k is not None]
            note = ""
            if kinds and len({k == 0 for k in kinds}) > 1:
                sound = sum(1 for k in kinds if k == 0); weak = len(kinds) - sound
                note = ("مختلف فيه، والأكثر على " + ("التصحيح أو التحسين" if sound > weak else "التضعيف")
                        if sound != weak else "مختلف فيه (متكافئ)")
            recs.append({"id": h["hadithnumber"], "n": n, "t": t, "g": grades, "c": cat, "note": note})
        if len(recs) < 1500: raise SystemExit(f"ERROR: hadith book {b} has only {len(recs)} hadiths")
        _cache[b] = recs
    return _cache


def build_db(D, OUT):
    """OUT/hadith/<book>.txt: one hadith per line — number, verdict (0-3, empty), rulings, text — and books.json."""
    books = load(D)
    os.makedirs(f"{OUT}/hadith", exist_ok=True)
    meta = []
    for b, name in BOOKS:
        with open(f"{OUT}/hadith/{b}.txt", "w", encoding="utf-8") as f:
            for r in books[b]:
                g = "؛ ".join(f"{a}: {x}" for a, x in r["g"]) + (f" — {r['note']}" if r.get("note") else "")
                f.write(f"{r['n']}\t{'' if r['c'] is None else r['c']}\t{g}\t{r['t']}\n")
        meta.append({"id": b, "name": name, "count": len(books[b])})
    json.dump(meta, open(f"{OUT}/hadith/books.json", "w", encoding="utf-8"), ensure_ascii=False)
    print("hadith search:", ", ".join(f"{m['id']} {m['count']}" for m in meta))


def find(D, ref):
    """{"book": "bukhari", "n": 5054} (the book's own number) or {"book": "muslim", "id": 1745} (dataset number)."""
    recs = load(D).get(ref["book"])
    if recs is None: raise SystemExit(f"ERROR: unknown hadith book {ref['book']}")
    for r in recs:
        if ("id" in ref and r["id"] == ref["id"]) or ("n" in ref and r["n"] == str(ref["n"])): return r
    raise SystemExit(f"ERROR: hadith {ref} not found")


def excerpt(text, frm=None, to=None):
    """The text between two anchor phrases (anchors included), matched without diacritics; build stops if missing."""
    nt, idx = norm_with_index(text)
    a, b = 0, len(text)
    if frm:
        k = nt.find(norm_with_index(frm)[0])
        if k < 0: raise SystemExit(f"ERROR: excerpt start {frm!r} not found in: {text[:80]}")
        a = idx[k]
    if to:
        q = norm_with_index(to)[0]
        k = nt.find(q, max(0, nt.find(norm_with_index(frm)[0]) if frm else 0))
        if k < 0: raise SystemExit(f"ERROR: excerpt end {to!r} not found in: {text[:80]}")
        b = idx[k + len(q) - 1] + 1
        while b < len(text) and _M.match(text[b]): b += 1          # keep the last letter's diacritics
        close = re.match(r"\s*([}﴾)])", text[b:])                  # the bracket closing a quote left open by the cut
        if close and text[a:b].count({"}": "{", "﴾": "﴿", ")": "("}[close.group(1)]) > text[a:b].count(close.group(1)):
            b += close.end()
    out = text[a:b].strip().strip("،").strip()
    if a > 0 or b < len(text):          # a quote mark / full stop left unpaired by the cut
        out = out.rstrip(" .")
        if out.endswith('"') and out.count('"') % 2: out = out[:-1].rstrip(" .")
        if out.startswith('"') and out.count('"') % 2: out = out[1:].lstrip()
    return ("… " if a > 0 else "") + out + (" …" if b < len(text) and text[b:].strip(" .\"«»") else "")


def resolve(D, ref):
    r = find(D, ref)
    t = excerpt(r["t"], ref.get("from"), ref.get("to"))
    src = f"{NAMES[ref['book']]} ({r['n']})"
    main = [x for a, x in r["g"] if a in ("الألباني", "سليم الهلالي")]
    if main: src += f" — حكم {'الألباني' if ref['book'] != 'malik' else 'الهلالي'}: {main[0]}"
    if r["c"] not in (0, None) and not ref.get("allowWeak"):
        raise SystemExit(f"ERROR: {src} is not sahih/hasan and is quoted as evidence")
    return {"k": "h", "t": t, "src": src}


_shingles = None


def grade_of(D, text):
    """The narration in the six books / al-Muwatta that a quoted hadith (e.g. from Riyad as-Salihin) comes from,
    found by 6-word overlaps of its plain text: (book, number, verdict 0-3 or None, rulings) or None if not found."""
    global _shingles
    books = load(D)
    if _shingles is None:
        _shingles = {}
        for b, recs in books.items():
            for i, r in enumerate(recs):
                w = norm_with_index(r["t"])[0].split()
                for k in range(0, max(1, len(w) - 5)):
                    _shingles.setdefault(" ".join(w[k:k + 6]), set()).add((b, i))
    text = re.sub(r"\{[^}]*\}|﴿[^﴾]*﴾", " ", text)        # Quran quotations would match unrelated hadiths
    q = re.findall(r'["“«]([^"”»]{15,})["”»]', text)
    w = norm_with_index(max(q, key=len) if q else text)[0].split()
    if len(w) < 6: w = norm_with_index(text)[0].split()
    wins = [" ".join(w[k:k + 6]) for k in range(0, max(1, len(w) - 5), 2)]
    # formulae shared by hundreds of hadiths («رضي الله عنه عن النبي صلى الله عليه وسلم قال») say nothing
    wins = [x for x in wins if len(_shingles.get(x, ())) <= 25]
    votes = {}
    for x in wins:
        for hit in _shingles.get(x, ()): votes[hit] = votes.get(hit, 0) + 1
    if not votes: return None
    order = {b: n for n, (b, _) in enumerate(BOOKS)}
    best = max(votes.items(), key=lambda kv: (kv[1], -order[kv[0][0]]))
    if best[1] < max(2, len(wins) // 3): return None
    b, i = best[0]; r = books[b][i]
    # the same words are often in several narrations: a sound one that carries most of them counts
    sound = [(v, b2, i2) for (b2, i2), v in votes.items()
             if books[b2][i2]["c"] == 0 and v >= 0.8 * best[1] and v >= max(2, len(wins) // 2)]
    if sound and r["c"] != 0:
        v, b, i = max(sound, key=lambda x: (x[0], -order[x[1]])); r = books[b][i]
    return b, r["n"], r["c"], "؛ ".join(f"{a}: {x}" for a, x in r["g"])
