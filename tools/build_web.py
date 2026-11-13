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
    sw = os.path.join(OUT, "sw.js")
    open(sw, "w").write(open(sw).read().replace("__VERSION__", version))

    # data (only what the web version uses)
    data = os.path.join(OUT, "data")
    os.makedirs(data)
    for f in ["meta.json", "gharib.json", "text.json", "reciters.json"]:
        shutil.copy(os.path.join(A, f), data)
    for d in ["pages", "tafsir", "muslim", "listen", "learn"]:
        shutil.copytree(os.path.join(A, d), os.path.join(data, d))

    # fonts (WOFF2: about a third of the TTF size)
    jobs = [(f"{RAW}/mushaf-woff2/QCF_P{n:03d}.woff2", f"{D}/woff2/p{n}.woff2") for n in range(1, 605)]
    jobs += [(f"{RAW}/mushaf-woff2/QCF_BSML.woff2", f"{D}/woff2/bsml.woff2"),
             (f"{RAW}/various-woff2/UthmanicHafs1%20Ver09.woff2", f"{D}/woff2/hafs.woff2")]
    with cf.ThreadPoolExecutor(8) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))
    shutil.copytree(f"{D}/woff2", os.path.join(OUT, "fonts"))

    # files for "download the Mushaf for offline use" (tafsir stays on-demand)
    offline = ["data/learn/library.json", "data/learn/tajweed.json", "data/gharib.json", "data/text.json", "data/muslim/azkar.json", "data/muslim/hadith_daily.json", "data/muslim/names.json"]
    offline += [f"data/pages/{n}.json" for n in range(1, 605)] + [f"fonts/p{n}.woff2" for n in range(1, 605)]
    for f in offline:
        if not os.path.exists(os.path.join(OUT, f)): raise SystemExit(f"ERROR: missing {f}")
    json.dump(offline, open(os.path.join(OUT, "offline.json"), "w"))
    open(os.path.join(OUT, ".nojekyll"), "w").close()

    size = sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(OUT) for f in fs)
    print(f"OK: web site in {OUT} ({size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
