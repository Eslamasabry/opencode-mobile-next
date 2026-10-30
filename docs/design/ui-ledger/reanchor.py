#!/usr/bin/env python3
"""Re-anchor ledger elements and pages whose `line` fell outside their file.

Usage (repo root):  python3 docs/design/ui-ledger/reanchor.py [--write] [--verbose]

For every page or element in parts/*.json whose effective file/line is out of
range (the check_ui_ledger.py "line N outside" error), find where it lives now:

  1. its `key` as a quoted string literal;
  2. the English label's ARB key used as `.arbKey` in code;
  3. for pages, `class <widget>` or the presenting function's definition;
  4. the first identifier named in the `effect` (a function/class definition).

Search order: the entry's own file, then its library (part-of siblings), then
the rest of lib/ and tool-relevant Dart sources. A unique hit in the nearest
tier wins. Several hits in one tier are ambiguous and left untouched, as are
entries with no hit. Nothing is guessed. Without --write nothing is changed.
After --write, rebuild: python3 docs/design/ui-ledger/build_ledger.py
"""
import json, os, re, sys, glob

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
WRITE = "--write" in sys.argv
VERBOSE = "--verbose" in sys.argv

src = {}  # rel -> list[str]
for dp, _d, fs in os.walk(os.path.join(ROOT, "lib")):
    for n in fs:
        if n.endswith(".dart") and ".g." not in n and "l10n/app_localizations" not in dp + n:
            rel = os.path.relpath(os.path.join(dp, n), ROOT).replace(os.sep, "/")
            with open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace") as fh:
                src[rel] = fh.read().split("\n")

# library membership via `part` / `part of`
owner = {}
for rel, lines in src.items():
    for ln in lines[:80]:
        m = re.match(r"\s*part of\s+['\"](.+?)['\"]", ln)
        if m:
            owner[rel] = os.path.normpath(os.path.join(os.path.dirname(rel), m.group(1))).replace(os.sep, "/")
library = {}
for rel in src:
    library.setdefault(owner.get(rel, rel), set()).add(rel)

def siblings(rel):
    return sorted(library.get(owner.get(rel, rel), {rel}))

arb = json.load(open(os.path.join(ROOT, "lib/l10n/app_en.arb"), encoding="utf-8"))
by_text = {}
for k, v in arb.items():
    if not k.startswith("@") and isinstance(v, str):
        by_text.setdefault(v.strip(), []).append(k)

def hits(files, pattern, exclude=None):
    rx = re.compile(pattern)
    ex = re.compile(exclude) if exclude else None
    out = []
    for f in files:
        for i, ln in enumerate(src.get(f, []), 1):
            if rx.search(ln) and not (ex and ex.search(ln)):
                out.append((f, i))
    return out

def tiers(own):
    t1 = [own] if own in src else []
    t2 = [f for f in siblings(own) if f not in t1] if own in src else []
    t3 = [f for f in sorted(src) if f not in t1 and f not in t2]
    return [t1, t2, t3]

def nlines(f):
    return len(src[f]) if f in src else 0

def resolve(own, entry, is_page, near=None):
    """Return (file, line, how) | ('ambiguous', cands) | None"""
    probes = []  # (name, regex)
    key = entry.get("key")
    if key and isinstance(key, str) and re.fullmatch(r"[\w\-./:]+", key):
        probes.append(("key", r"""['"]%s['"]""" % re.escape(key)))
    if is_page:
        w = entry.get("widget") or ""
        if re.fullmatch(r"[A-Za-z_]\w*", w):
            probes.append(("class", r"\b(class|mixin|enum|extension)\s+%s\b" % w))
            probes.append(("fn-def", r"^\s*(?:static\s+)?(?:Future<[^>]*>\??|void|Widget|bool|\w+(?:<[^>]*>)?\??)\s+%s\s*(?:<[^>]*>)?\s*\(" % w))
        for pb in entry.get("presentedBy") or []:
            if re.fullmatch(r"[A-Za-z_]\w*", pb):
                probes.append(("presentedBy", r"^\s*(Future<[^>]*>\??|void|Widget|bool|\w+)\s+%s\s*(<[^>]*>)?\s*\(" % pb))
    else:
        label = (entry.get("label") or "")
        for seg in [s.strip() for s in label.split("|")]:
            for k in by_text.get(seg, []):
                probes.append(("arb", r"\.%s\b" % k))
        eff = entry.get("effect") or ""
        skip = {"Navigator", "Calls", "Opens", "Shows", "Pops", "Clipboard", "Text", "Scaffold", "AppBar", "SnackBar", "Icon", "Icons", "Row", "Column", "Padding"}
        seen = set()
        for m in re.finditer(r"\b(_?[A-Za-z][A-Za-z0-9]*)\(", eff):
            n = m.group(1)
            if len(n) <= 4 or n in skip or n in seen or len(seen) >= 3:
                continue
            seen.add(n)
            probes.append(("effect-call", r"(?<![\w.])%s\s*\(" % re.escape(n), r"^\s*(?:static\s+)?(?:Future<[^>]*>\??|void|Widget|bool|[\w<>?,]+)\s+%s\s*(?:<[^>]*>)?\s*\(" % re.escape(n)))
            probes.append(("effect-def", r"^\s*(?:static\s+)?(?:Future<[^>]*>\??|void|Widget|bool|\w+(?:<[^>]*>)?\??)\s+%s\s*(?:<[^>]*>)?\s*\(" % re.escape(n)))
    for pr in probes:
        how, rx = pr[0], pr[1]
        ex = pr[2] if len(pr) > 2 else None
        if near and near[0] in src:
            # proximity: a hit inside the host page's own region (its file, from just
            # above its anchor to 250 lines below) when exactly one exists
            h = [(f, l) for f, l in hits([near[0]], rx, ex) if near[1] - 10 <= l <= near[1] + 250]
            if len(h) == 1:
                return (h[0][0], h[0][1], how + "-near")
        for ti, tier in enumerate(tiers(own)):
            if ti == 2 and how not in ("key", "class"):
                continue  # generic probes (labels, functions) only within the library
            h = hits(tier, rx, ex)
            if len(h) == 1:
                return (h[0][0], h[0][1], how)
            if h:
                if how == "effect-def" and ti == 2:
                    continue
                files = {f for f, _ in h}
                if how in ("key", "class") and len(files) == 1:
                    return (h[0][0], h[0][1], how + "-first")
                return ("ambiguous", [(f, l) for f, l in h])
    return None

fixed, ambiguous, notfound, unchanged = [], [], [], 0
for pf in sorted(glob.glob(os.path.join(HERE, "parts", "*.json"))):
    if os.path.basename(pf).startswith("_"):
        continue
    data = json.load(open(pf, encoding="utf-8"))
    dirty = False
    for p in data["pages"]:
        def bad(file, line):
            return isinstance(line, int) and file in src and not (1 <= line <= nlines(file))
        if bad(p.get("file"), p.get("line")):
            r = resolve(p.get("file"), p, True)
            tag = f"page {p['id']}"
            if r and r[0] != "ambiguous":
                if VERBOSE: print("fix", tag, p.get("file"), p["line"], "->", r)
                p["file"], p["line"] = r[0], r[1]; dirty = True; fixed.append(tag)
            elif r:
                ambiguous.append((tag, r[1]))
            else:
                notfound.append((tag, p.get("file"), p["line"]))
        pend = []
        for e in p["elements"]:
            ef = e.get("file") or p.get("file")
            if not bad(ef, e.get("line")):
                continue
            tag = f"element {e['id']}"
            r = resolve(ef, e, False, (p.get("file"), p.get("line")) if isinstance(p.get("line"), int) else None)
            if r and r[0] != "ambiguous":
                if VERBOSE: print("fix", tag, ef, e["line"], "->", r)
                if r[0] != p.get("file") or e.get("file"):
                    e["file"] = r[0]
                e["line"] = r[1]; dirty = True; fixed.append(tag)
            elif r:
                ambiguous.append((tag, r[1])); pend.append((e, ef, r[1]))
            else:
                notfound.append((tag, ef, e["line"]))
        # second pass: an ambiguous element sitting between two anchored neighbours
        # (list order follows source order) in the same file takes the one candidate
        # that lies between them
        for e, ef, cands in pend:
            idx = p["elements"].index(e)
            def anchored(x):
                f = x.get("file") or p.get("file")
                return f, x.get("line")
            def okline(x):
                f, l = anchored(x)
                return f in src and isinstance(l, int) and 1 <= l <= nlines(f)
            prev = next((anchored(x) for x in reversed(p["elements"][:idx]) if okline(x)), None)
            nxt = next((anchored(x) for x in p["elements"][idx + 1:] if okline(x)), None)
            if not prev or not nxt:
                continue
            ok = [(f, l) for f, l in cands if f == prev[0] == nxt[0] and prev[1] <= l <= nxt[1]]
            if len(ok) == 1 and len({f for f, _ in cands if f == prev[0]}) >= 1:
                e["file"] = ok[0][0]; e["line"] = ok[0][1]; dirty = True
                tag = f"element {e['id']}"
                fixed.append(tag + " (neighbour order)")
                ambiguous[:] = [a for a in ambiguous if a[0] != tag]
                if VERBOSE: print("fix", tag, "->", ok[0], "neighbour")
    if dirty and WRITE:
        with open(pf, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=True)
            fh.write("\n")
print(f"fixed: {len(fixed)}  ambiguous: {len(ambiguous)}  not found: {len(notfound)}")
for t, c in ambiguous:
    print("AMBIGUOUS", t, c)
for t, f, l in notfound:
    print("NOTFOUND", t, f, l)
