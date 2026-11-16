#!/usr/bin/env python3
"""Builds the web (iPhone) version into a static site for GitHub Pages.

Usage: python3 tools/build_web.py <download_cache_dir> <assets_dir from prepare_data.py> <site_out_dir> [version]
Uses the same verified data as the Android build, plus the WOFF2 editions of the KFGQPC fonts.
"""
import json, os, shutil, sys, urllib.request, concurrent.futures as cf

RAW = "https://raw.githubusercontent.com/mustafa0x/qpc-fonts/master"


def fetch(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for _ in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r, open(path + ".part", "wb") as f:
                shutil.copyfileobj(r, f)
            os.replace(path + ".part", path)
            return
        except Exception as e:
            err = e
    raise SystemExit(f"ERROR: download failed {url}: {err}")


def main():
    D, A, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
    version = sys.argv[4] if len(sys.argv) > 4 else "dev"
    here = os.path.dirname(os.path.abspath(__file__))
    web = os.path.join(here, "..", "web")
    if os.path.exists(OUT): shutil.rmtree(OUT)
    shutil.copytree(web, OUT)
    # service worker cache version
    for f in (os.path.join(OUT, "sw.js"), os.path.join(OUT, "index.html")):
        txt = open(f, encoding="utf-8").read()          # read first: opening for writing truncates the file
        open(f, "w", encoding="utf-8").write(txt.replace("__VERSION__", version))

    # data (only what the web version uses)
    data = os.path.join(OUT, "data")
    os.makedirs(data)
    for f in ["meta.json", "gharib.json", "text.json", "reciters.json", "reading.json", "whatsnew.json", "surahs_info.json"]:
        shutil.copy(os.path.join(A, f), data)
    for d in ["pages", "tafsir", "muslim", "listen", "learn", "english", "hadith"]:
        shutil.copytree(os.path.join(A, d), os.path.join(data, d))
    # hadith search: the plain-letter index and number/verdict per line (the texts are fetched only for the hits)
    sys.path.insert(0, here)
    import hadith_books
    for b, _ in hadith_books.BOOKS:
        lines = open(os.path.join(data, "hadith", f"{b}.txt"), encoding="utf-8").read().rstrip("\n").split("\n")
        rows = [l.split("\t", 3) for l in lines]
        open(os.path.join(data, "hadith", f"{b}.plain"), "w", encoding="utf-8").write(
            "\n".join(hadith_books.norm_with_index(r[3])[0].rstrip() for r in rows))
        open(os.path.join(data, "hadith", f"{b}.meta"), "w", encoding="utf-8").write("\n".join(f"{r[0]}\t{r[1]}" for r in rows))

    # fonts: the QCF V2 page fonts are already WOFF2 (prepared by prepare_data.py); the official Hafs font and the
    # basmala font are compressed here
    fonts = os.path.join(OUT, "fonts"); os.makedirs(fonts)
    for n in range(1, 605):
        shutil.copy(f"{D}/fonts2/p{n:03d}.woff2", f"{fonts}/p{n}.woff2")
    from fontTools.ttLib import TTFont
    for src, dst in ((f"{D}/fonts2/bsml.ttf", "bsml.woff2"), (f"{D}/fonts2/hafs_v22.ttf", "hafs.woff2")):
        f = TTFont(src); f.flavor = "woff2"; f.save(f"{fonts}/{dst}")

    # files for "download the Mushaf for offline use" (tafsir stays on-demand)
    offline = ["data/reading.json", "data/learn/library.json", "data/learn/tajweed.json", "data/gharib.json", "data/text.json", "data/muslim/azkar.json", "data/muslim/hadith_daily.json", "data/muslim/names.json"]
    offline += [f"data/pages/{n}.json" for n in range(1, 605)] + [f"fonts/p{n}.woff2" for n in range(1, 605)]
    for f in offline:
        if not os.path.exists(os.path.join(OUT, f)): raise SystemExit(f"ERROR: missing {f}")
    json.dump(offline, open(os.path.join(OUT, "offline.json"), "w"))
    open(os.path.join(OUT, ".nojekyll"), "w").close()

    size = sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(OUT) for f in fs)
    print(f"OK: web site in {OUT} ({size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
