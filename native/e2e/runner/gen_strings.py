r"""Builds the selector table the flows read as ${output.<form>.<key>} (plan 14 §4.3).

Sources, all frozen copies the two apps share:
  * lib/l10n/app_<lang>.arb               (keys as-is)
  * android/app/src/main/res/values*/strings.xml  (keys prefixed "android_": tile, widget, shortcut labels)
  * native/e2e/flows/_strings/extra*.<lang>.json  (keys prefixed "x_": labels the Flutter app hard-codes in Dart)
A key missing in Arabic falls back to English, so both tables have the same keys.

Forms per key:
  t   raw text, for inputText
  s   exact text as a Java regex (Maestro text selectors are regexes)
  c   contains the exact text (Flutter merges semantics labels, for example "Title\nSubtitle")
  re  full pattern: {placeholders} -> .*, ICU plural/select -> alternation, counts -> \d+
  rc  contains the pattern

Usage: python3 -I gen_strings.py --lang en --out out/strings.js [--fixture-data FILE]
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

HERE = pathlib.Path(__file__).resolve().parent
E2E = HERE.parent
REPO = E2E.parent.parent
ARB_DIR = REPO / "lib/l10n"
RES_DIR = REPO / "android/app/src/main/res"
EXTRA_DIR = E2E / "flows/_strings"
FIXTURE_DEFAULTS = HERE / "fixture-defaults.json"
FIXTURE_DATA = E2E / "fixtures/fixture-data.json"  # owned by S0-FX; read when present

_SPECIAL = set("\\.[]{}()*+?^$|")


def escape(text: str) -> str:
    return "".join("\\" + ch if ch in _SPECIAL else ch for ch in text)


class _Parser:
    """Tiny ICU MessageFormat reader: literals, {arg}, {arg, plural|select|selectordinal, k{msg} ...}."""

    def __init__(self, text: str):
        self.text, self.i = text, 0

    def message(self, stop_at_brace: bool = False, in_plural: bool = False) -> str:
        out = []
        lit = []

        def flush():
            if lit:
                out.append(escape("".join(lit)))
                lit.clear()

        while self.i < len(self.text):
            ch = self.text[self.i]
            if ch == "'" and self.i + 1 < len(self.text) and self.text[self.i + 1] == "'":
                lit.append("'")
                self.i += 2
            elif ch == "{":
                flush()
                out.append(self.argument())
            elif ch == "}" and stop_at_brace:
                break
            elif ch == "#" and in_plural:
                flush()
                out.append(r"\d+")
                self.i += 1
            else:
                lit.append(ch)
                self.i += 1
        flush()
        return "".join(out)

    def argument(self) -> str:
        assert self.text[self.i] == "{"
        self.i += 1
        start = self.i
        while self.i < len(self.text) and self.text[self.i] not in ",}":
            self.i += 1
        if self.i >= len(self.text):
            return ".*"
        if self.text[self.i] == "}":
            self.i += 1
            return ".*"
        self.i += 1  # ','
        kind_start = self.i
        while self.i < len(self.text) and self.text[self.i] not in ",}":
            self.i += 1
        kind = self.text[kind_start:self.i].strip()
        if self.i < len(self.text) and self.text[self.i] == "}":
            self.i += 1
            return ".*"
        self.i += 1  # ','
        forms = []
        while self.i < len(self.text):
            while self.i < len(self.text) and self.text[self.i].isspace():
                self.i += 1
            if self.i >= len(self.text):
                break
            if self.text[self.i] == "}":
                self.i += 1
                break
            sel_start = self.i
            while self.i < len(self.text) and self.text[self.i] != "{":
                self.i += 1
            _selector = self.text[sel_start:self.i].strip()
            self.i += 1  # '{'
            forms.append(self.message(stop_at_brace=True, in_plural=kind in ("plural", "selectordinal")))
            self.i += 1  # '}'
        if kind in ("plural", "selectordinal"):
            forms = [f.replace(".*", r"\d+") if f.count(".*") == 1 and f.startswith(".*") else f for f in forms]
        return "(?:" + "|".join(forms) + ")"


def convert(text: str) -> dict:
    s = escape(text)
    has_args = "{" in text
    re_ = _Parser(text).message() if has_args else s
    return {"t": text, "s": s, "c": "(?s).*" + s + ".*", "re": re_, "rc": "(?s).*" + re_ + ".*"}


def _read_arb(lang: str) -> dict[str, str]:
    path = ARB_DIR / f"app_{lang}.arb"
    data = json.loads(path.read_text(encoding="utf-8"))
    return {k: v for k, v in data.items() if not k.startswith("@") and isinstance(v, str)}


def _read_android(lang: str) -> dict[str, str]:
    folder = RES_DIR / ("values" if lang == "en" else f"values-{lang}")
    path = folder / "strings.xml"
    if not path.exists():
        return {}
    out = {}
    for el in ET.parse(path).getroot().iter("string"):
        name = el.get("name")
        text = "".join(el.itertext())
        if name and text:
            text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
            out["android_" + name] = text
    return out


def _read_extra(lang: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for path in sorted(EXTRA_DIR.glob(f"extra*.{lang}.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        out.update({k: v["text"] for k, v in data.items() if not k.startswith("_")})
    return out


def raw_table(lang: str) -> dict[str, str]:
    table = {}
    for reader in (_read_arb, _read_android, _read_extra):
        table.update(reader("en"))
    if lang != "en":
        for reader in (_read_arb, _read_android, _read_extra):
            # Only keys English still has: a stale translation is not a selector.
            table.update({k: v for k, v in reader(lang).items() if k in table})
    return table


def build(lang: str) -> dict[str, dict[str, str]]:
    forms: dict[str, dict[str, str]] = {"t": {}, "s": {}, "c": {}, "re": {}, "rc": {}}
    for key, text in raw_table(lang).items():
        for form, value in convert(text).items():
            forms[form][key] = value
    return forms


def fixture_data() -> dict:
    data = json.loads(FIXTURE_DEFAULTS.read_text(encoding="utf-8"))
    data.pop("_comment", None)
    if FIXTURE_DATA.exists():
        data.update(json.loads(FIXTURE_DATA.read_text(encoding="utf-8")))
    return data


def write_js(lang: str, out: pathlib.Path) -> None:
    table = build(lang)
    out.parent.mkdir(parents=True, exist_ok=True)
    parts = [f"output.{form} = {json.dumps(values, ensure_ascii=False, sort_keys=True)};" for form, values in table.items()]
    parts.append(f"output.fx = {json.dumps(fixture_data(), ensure_ascii=False, sort_keys=True)};")
    parts.append(f"output.lang = {json.dumps(lang)};")
    out.write_text("\n".join(parts) + "\n", encoding="utf-8")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", choices=["en", "ar"], default="en")
    ap.add_argument("--out", type=pathlib.Path, default=HERE / "out/strings.js")
    args = ap.parse_args(argv)
    write_js(args.lang, args.out)
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
