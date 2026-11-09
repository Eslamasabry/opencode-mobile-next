#!/usr/bin/env python3
"""Translation pipeline for the native strings (see ../README.md).

  translate.py status   [--locales es pt]
  translate.py baseline --locales ru            adopt today's translations as in sync with today's English
  translate.py plan     --locales es --out DIR [--files strings_chat.xml ...] [--max-files N] [--batch-size 60]
  translate.py apply    BATCH.json ...          read BATCH.out.json ({key: translation}), write it into the XML, update the state

Standard library only. English (values/) is the source of truth. A key is "open" for a locale when it is
missing there, or when the English text changed since the locale was translated (state file, one TSV per
locale in translation-state/: key<TAB>sha1-of-English). Everything is read and written as plain text: the
batch holds the person-readable string (real newlines, real apostrophes); this tool applies the XML escaping.
"""
import argparse, fcntl, glob, hashlib, json, os, re, sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
STRINGS = os.path.dirname(HERE)                       # native/tools/strings
NATIVE = os.path.dirname(os.path.dirname(STRINGS))    # native
STATE_DIR = os.path.join(STRINGS, "translation-state")

# (name, resource base dir relative to native, folder kind). The folder holds values/ and values-<lang>/.
ROOTS = [
    ("words", "ui/words/src/commonMain/composeResources", "cmp"),
    ("words-android", "ui/words/android-res", "android"),
    ("androidApp", "androidApp/src/main/res", "android"),
    ("notifications", "android/notifications/src/main/res", "android"),
    ("background", "android/background/src/main/res", "android"),
    ("linux", "android/linux/src/main/res", "android"),
    ("platform", "android/platform/src/main/res", "android"),
    ("schedules", "android/schedules/src/main/res", "android"),
    ("tiles", "android/tiles/src/main/res", "android"),
    ("widgets", "android/widgets/src/main/res", "android"),
]
# words-android is generated from composeResources strings_os.xml by `strings convert`; it is mirrored by `apply`.
GENERATED = {"words-android"}

# The Android folder name of a language when it is not the code (Indonesian is "in" to aapt2 and to Android's Locale).
ANDROID_FOLDER = {"id": "in"}
CMP_FOLDER = {"id": "id"}

PLURALS = {
    "en": ["one", "other"], "ar": ["zero", "one", "two", "few", "many", "other"],
    "es": ["one", "many", "other"], "pt": ["one", "many", "other"], "fr": ["one", "many", "other"],
    "ru": ["one", "few", "many", "other"], "ja": ["other"], "zh": ["other"], "vi": ["other"], "id": ["other"],
}
PH = re.compile(r"%(?:\d+\$)?[sd]|%%")

ENTRY = re.compile(r'(?P<ind>[ \t]*)<(?P<tag>string|plurals)\b(?P<attrs>[^>]*?)(?:/>|>(?P<body>.*?)</(?P=tag)>)[ \t]*\n?', re.S)
ITEM = re.compile(r'<item\s+quantity="(\w+)"\s*>(.*?)</item>', re.S)


def folder(root_kind, loc):
    return "values" if loc == "en" else "values-" + (ANDROID_FOLDER if root_kind == "android" else CMP_FOLDER).get(loc, loc)


def xml_text(raw):
    """File text of one element → plain text (what a person reads)."""
    t = ET.fromstring("<x>%s</x>" % raw).text or ""
    return unescape(t)


def unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "n": out.append("\n"); i += 2; continue
            if n == "t": out.append("\t"); i += 2; continue
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", s[i + 2:i + 6]):
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            out.append(n); i += 2; continue   # \\ \' \" \@ \?
        out.append(c); i += 1
    return "".join(out)


def invisible(c):
    o = ord(c)
    return ((o < 32 and c not in "\n\t") or 0x7F <= o <= 0x9F or o in (0xA0, 0xAD, 0x61C, 0xFEFF)
            or 0x200B <= o <= 0x200F or 0x2028 <= o <= 0x202E or 0x2060 <= o <= 0x2069)


def escape(value, kind):
    """Plain text → file text (same rules as XmlText.escape: CMP files keep quotes, Android files escape them)."""
    out, last = [], len(value) - 1
    for i, c in enumerate(value):
        if c == "\\": out.append("\\\\")
        elif c == "\n": out.append("\\n")
        elif c == "\t": out.append("\\t")
        elif c == "&": out.append("&amp;")
        elif c == "<": out.append("&lt;")
        elif c == ">": out.append("&gt;")
        elif invisible(c): out.append("\\u%04X" % ord(c))
        elif kind == "android" and c in "'\"": out.append("\\u%04X" % ord(c))
        elif kind == "android" and i == 0 and c in "@?": out.append("\\u%04X" % ord(c))
        elif kind == "android" and c == " " and (i == 0 or i == last or value[i - 1] == " "): out.append("\\u0020")
        else: out.append(c)
    return "".join(out)


def read_file(path):
    """[(name, kind 'string'|'plurals', raw_comment_before, value)] in document order. Value: str or {quantity: str}."""
    text = open(path, encoding="utf-8").read()
    out = []
    for m in ENTRY.finditer(text):
        name = re.search(r'name="([^"]+)"', m.group("attrs"))
        if not name: continue
        body = m.group("body") or ""
        before = text[:m.start()]
        cm = re.search(r"<!--((?:(?!<!--).)*?)-->\s*$", before[-600:], re.S)
        comment = re.sub(r"\s+", " ", cm.group(1)).strip() if cm else ""
        if m.group("tag") == "string":
            out.append((name.group(1), "string", comment, xml_text(body), m.group("attrs")))
        else:
            out.append((name.group(1), "plurals", comment, {q: xml_text(v) for q, v in ITEM.findall(body)}, m.group("attrs")))
    return out


def load_locale(root_dir, kind, loc):
    """{file: {key: (type, comment, value)}} for one locale of one root."""
    d = os.path.join(NATIVE, root_dir, folder(kind, loc))
    res = {}
    for p in sorted(glob.glob(os.path.join(d, "*.xml"))):
        ents = read_file(p)
        if ents: res[os.path.basename(p)] = {n: (t, c, v) for n, t, c, v, _ in ents}
    return res


def digest(typ, val):
    blob = json.dumps(val if typ == "string" else {k: val[k] for k in sorted(val)}, ensure_ascii=False, sort_keys=True)
    return hashlib.sha1(blob.encode()).hexdigest()[:10]


def read_state(loc):
    p = os.path.join(STATE_DIR, loc + ".tsv")
    if not os.path.exists(p): return {}
    return dict(l.rstrip("\n").split("\t", 1) for l in open(p, encoding="utf-8") if "\t" in l)


def write_state(loc, st):
    os.makedirs(STATE_DIR, exist_ok=True)
    with open(os.path.join(STATE_DIR, loc + ".tsv"), "w", encoding="utf-8") as f:
        for k in sorted(st): f.write("%s\t%s\n" % (k, st[k]))


def skey(root, key):
    """State key: the plain key in the words root, root:key elsewhere (Android modules can reuse a name)."""
    return key if root == "words" else root + ":" + key


def priority_rank(fname):
    pat = os.path.join(STRINGS, "translation-priority.tsv")
    rules = [l.split("\t")[0] for l in open(pat) if l.strip() and not l.startswith("#")] if os.path.exists(pat) else []
    for i, r in enumerate(rules):
        if re.search(r, fname): return i
    return len(rules)


def analyse(loc):
    """Open work for [loc]: [(root, kind, file, key, typ, comment, en, existing, reason)] and totals."""
    st = read_state(loc)
    work, total, done = [], 0, 0
    for root, base, kind in ROOTS:
        if root in GENERATED: continue
        en = load_locale(base, kind, "en")
        tr = load_locale(base, kind, loc)
        for fname, ents in en.items():
            for key, (typ, comment, val) in ents.items():
                total += 1
                have = next((tr[f][key] for f in ([fname] if fname in tr else []) + [x for x in tr if x != fname] if key in tr[f]), None)
                h = digest(typ, val)
                if have is None: work.append((root, kind, fname, key, typ, comment, val, None, "missing"))
                elif skey(root, key) in st and st[skey(root, key)] != h: work.append((root, kind, fname, key, typ, comment, val, have[2], "changed"))
                else: done += 1
    return work, total, done


def placeholders(typ, val):
    vals = [val] if typ == "string" else list(val.values())
    return sorted({m for v in vals for m in PH.findall(v) if m != "%%"})


def max_length(key, comment):
    m = re.search(r"max(?:imum)?\s*(?:length|len|chars?)?\s*[:=]?\s*(\d+)", comment, re.I) or re.search(r"Max(\d+)$", key)
    return int(m.group(1)) if m else None


def cmd_status(a):
    for loc in a.locales:
        work, total, done = analyse(loc)
        byfile = {}
        for w in work: byfile.setdefault(w[2], [0, 0])[0 if w[8] == "missing" else 1] += 1
        miss = sum(1 for w in work if w[8] == "missing")
        print("%s: %d of %d keys translated, %d missing, %d changed in English" % (loc, done, total, miss, len(work) - miss))
        for f, (m, c) in sorted(byfile.items(), key=lambda kv: (priority_rank(kv[0]), kv[0]))[: a.top]:
            print("   %-46s missing %4d  changed %3d" % (f, m, c))


def cmd_baseline(a):
    for loc in a.locales:
        st, n = read_state(loc), 0
        for root, base, kind in ROOTS:
            if root in GENERATED: continue
            en, tr = load_locale(base, kind, "en"), load_locale(base, kind, loc)
            have = {k for f in tr.values() for k in f}
            for ents in en.values():
                for key, (typ, _, val) in ents.items():
                    if key in have and skey(root, key) not in st: st[skey(root, key)] = digest(typ, val); n += 1
        write_state(loc, st)
        print("%s: adopted %d existing translations" % (loc, n))


def cmd_plan(a):
    glossary = os.path.relpath(os.path.join(STRINGS, "glossary-%s.tsv" % a.locale), NATIVE)
    work, _, _ = analyse(a.locale)
    if a.files: work = [w for w in work if w[2] in a.files or w[0] + ":" + w[2] in a.files]
    files = sorted({(w[0], w[2]) for w in work}, key=lambda rf: (priority_rank(rf[1]), rf[0], rf[1]))
    if a.max_files: files = files[: a.max_files]
    os.makedirs(a.out, exist_ok=True)
    n = 0
    for root, fname in files:
        items = [w for w in work if w[0] == root and w[2] == fname]
        for b in range(0, len(items), a.batch_size):
            chunk = items[b:b + a.batch_size]
            doc = {"locale": a.locale, "root": root, "file": fname, "glossary": glossary,
                   "needs": PLURALS[a.locale],
                   "items": []}
            for _, kind, _, key, typ, comment, en, old, reason in chunk:
                it = {"key": key, "kind": typ, "en": en, "reason": reason}
                ph = placeholders(typ, en)
                if ph: it["placeholders"] = ph
                if typ == "plurals": it["forms"] = PLURALS[a.locale]
                ml = max_length(key, comment)
                if ml: it["max_length"] = ml
                if comment: it["note"] = comment
                if old is not None: it["previous"] = old
                doc["items"].append(it)
            n += 1
            path = os.path.join(a.out, "%s-%03d-%s.json" % (a.locale, n, fname.replace(".xml", "")))
            head = {k: v for k, v in doc.items() if k != "items"}
            with open(path, "w", encoding="utf-8") as f:   # one item per line: cheap to read, easy to diff
                f.write(json.dumps(head, ensure_ascii=False)[:-1] + ', "items": [\n')
                f.write(",\n".join(json.dumps(i, ensure_ascii=False) for i in doc["items"]) + "\n]}\n")
            print(path, len(chunk))


def entry_xml(key, typ, val, kind, ind="    "):
    if typ == "string":
        return '%s<string name="%s">%s</string>\n' % (ind, key, escape(val, kind))
    items = "".join('%s    <item quantity="%s">%s</item>\n' % (ind, q, escape(val[q], kind)) for q in val)
    return '%s<plurals name="%s">\n%s%s</plurals>\n' % (ind, key, items, ind)


def put(path, entries, kind, loc):
    """Insert or replace entries [(key, typ, val)] in the XML file at [path], leaving everything else byte for byte."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path): text = open(path, encoding="utf-8").read()
    else: text = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Translated with native/tools/strings/translate; English is the source. -->\n<resources>\n</resources>\n'
    for key, typ, val in entries:
        new = entry_xml(key, typ, val, kind)
        pat = re.compile(r'[ \t]*<(string|plurals)\s+name="%s"[^>]*?(?:/>|>.*?</\1>)[ \t]*\n?' % re.escape(key), re.S)
        if pat.search(text): text = pat.sub(lambda m: new, text, count=1)
        else: text = text.replace("</resources>", new + "</resources>")
    open(path, "w", encoding="utf-8").write(text)


class Reject(Exception): pass


def check_item(loc, it, tr):
    en, typ = it["en"], it["kind"]
    if typ == "string":
        if not isinstance(tr, str) or not tr.strip(): raise Reject("empty or not a string")
        got, want = sorted({m for m in PH.findall(tr) if m != "%%"}), sorted({m for m in PH.findall(en) if m != "%%"})
        if got != want: raise Reject("placeholders %s, English has %s" % (got, want))
    else:
        if not isinstance(tr, dict): raise Reject("plurals need an object of forms")
        need = PLURALS[loc]
        if set(tr) != set(need): raise Reject("forms %s, need exactly %s" % (sorted(tr), need))
        union = {m for v in en.values() for m in PH.findall(v) if m != "%%"}
        for q, v in tr.items():
            if not v.strip(): raise Reject("empty form " + q)
            extra = {m for m in PH.findall(v) if m != "%%"} - union
            if extra: raise Reject("form %s uses %s, English has %s" % (q, sorted(extra), sorted(union)))
        used = {m for v in tr.values() for m in PH.findall(v) if m != "%%"}
        if used != union: raise Reject("forms use %s, English uses %s" % (sorted(used), sorted(union)))
    for v in ([tr] if typ == "string" else tr.values()):
        en_text = en if isinstance(en, str) else " ".join(en.values())
        if re.search(r"\\[ntu]|&(?:amp|lt|gt);", v) and not re.search(r"\\[ntu]|&(?:amp|lt|gt);", en_text):
            raise Reject("plain text only: no \\n, \\u or XML entities (write real characters)")


def cmd_apply(a):
    os.makedirs(STATE_DIR, exist_ok=True)
    with open(os.path.join(STATE_DIR, ".lock"), "w") as lock:   # agents apply batches of one locale side by side
        fcntl.flock(lock, fcntl.LOCK_EX)
        _apply(a)


def _apply(a):
    for path in a.batches:
        doc = json.load(open(path, encoding="utf-8"))
        out = path[:-5] + ".out.json" if path.endswith(".json") and not path.endswith(".out.json") else path
        tr = doc.get("tr") or (json.load(open(out, encoding="utf-8")) if os.path.exists(out) and out != path else {})
        loc, root = doc["locale"], doc["root"]
        base, kind = next((b, k) for r, b, k in ROOTS if r == root)
        st, entries, bad = read_state(loc), [], []
        for it in doc["items"]:
            key = it["key"]
            if key not in tr: bad.append("%s: not translated" % key); continue
            try: check_item(loc, it, tr[key])
            except Reject as e: bad.append("%s: %s" % (key, e)); continue
            entries.append((key, it["kind"], tr[key]))
            st[skey(root, key)] = digest(it["kind"], it["en"])
        if bad:
            print("%s: %d rejected" % (path, len(bad)), *bad[:40], sep="\n  ")
            if not a.partial: sys.exit(1)
        target = os.path.join(NATIVE, base, folder(kind, loc), doc["file"])
        put(target, entries, kind, loc)
        # strings_os.xml of the words root is mirrored into the generated Android res copy.
        if root == "words" and doc["file"] == "strings_os.xml":
            put(os.path.join(NATIVE, "ui/words/android-res", folder("android", loc), "strings_os.xml"), entries, "android", loc)
        write_state(loc, st)
        print("%s: wrote %d into %s" % (os.path.basename(path), len(entries), os.path.relpath(target, NATIVE)))


def main():
    p = argparse.ArgumentParser(); sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("status"); s.add_argument("--locales", nargs="+", required=True); s.add_argument("--top", type=int, default=12); s.set_defaults(f=cmd_status)
    s = sub.add_parser("baseline"); s.add_argument("--locales", nargs="+", required=True); s.set_defaults(f=cmd_baseline)
    s = sub.add_parser("plan"); s.add_argument("--locale", required=True); s.add_argument("--out", required=True)
    s.add_argument("--files", nargs="*"); s.add_argument("--max-files", type=int); s.add_argument("--batch-size", type=int, default=60); s.set_defaults(f=cmd_plan)
    s = sub.add_parser("apply"); s.add_argument("batches", nargs="+"); s.add_argument("--partial", action="store_true"); s.set_defaults(f=cmd_apply)
    a = p.parse_args(); a.f(a)


if __name__ == "__main__":
    main()
