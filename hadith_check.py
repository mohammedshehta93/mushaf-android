"""«أحاديث مشهورة لا تصح» — tools/content/hadith_check.json → OUT/hadith/not_sahih.json (see the note in the json)."""
import json, os
import hadith_books as hb

HERE = os.path.dirname(os.path.abspath(__file__))
RULING = "لا يصح عن النبي ﷺ — لا يوجد في الكتب الستة ولا الموطأ، وحكم عليه العلماء بالضعف أو الوضع أو أنه لا أصل له."


def build(D, OUT):
    cfg = json.load(open(f"{HERE}/content/hadith_check.json", encoding="utf-8"))
    books = hb.load(D)
    norm = {b: [hb.norm_with_index(r["t"])[0] for r in recs] for b, recs in books.items()}
    out = []
    for it in cfg["items"]:
        if "book" in it:
            r = hb.find(D, it)
            if r["c"] in (0, None): raise SystemExit(f"ERROR: hadith_check: {it['book']} {r['n']} is not graded weak")
            e = {"t": hb.excerpt(r["t"], it.get("from"), it.get("to")), "src": f"{hb.NAMES[it['book']]} ({r['n']})",
                 "r": "؛ ".join(f"{a}: {x}" for a, x in r["g"]), "c": r["c"]}
        else:
            q = hb.norm_with_index(it["t"])[0]
            for b, ns in norm.items():
                for i, t in enumerate(ns):
                    if q in t:   # then it must be quoted from the book with its grading, not as «not in the books»
                        raise SystemExit(f"ERROR: hadith_check: «{it['t']}» is in {b} {books[b][i]['n']}")
            e = {"t": it["t"], "r": RULING, "c": 1}
        if "alt" in it:
            a = hb.resolve(D, it["alt"])
            e["alt"] = {"t": a["t"], "src": a["src"]}
        out.append(e)
    json.dump(out, open(f"{OUT}/hadith/not_sahih.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("not-authentic list:", len(out))
