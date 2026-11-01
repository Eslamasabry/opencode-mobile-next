"""Lints the flow files against plan 14 §4.2 and the catalog.

python3 -I lint_flows.py            lint the tree, print errors, exit 1 on any
python3 -I lint_flows.py --report   also print the fixtures, fx actions, hooks and string keys the flows use
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
    __package__ = "runner"

from . import catalog, gen_strings, hooks  # noqa: E402

FLOWS_DIR = catalog.FLOWS_DIR
STRINGS_RUN = "- runScript: ../../runner/out/strings.js"
SELECTOR_KEYS = ("tapOn", "longPressOn", "doubleTapOn", "assertVisible", "assertNotVisible", "text", "visible",
                 "notVisible", "element", "copyTextFrom", "childOf", "below", "above", "leftOf", "rightOf",
                 "containsChild", "scrollUntilVisible")
_SEL = re.compile(r"^\s*(?:-\s*)?(" + "|".join(SELECTOR_KEYS) + r")\s*:\s*(\S.*)$")
_ID = re.compile(r"^\s*(?:-\s*)?id\s*:")
_KEYREF = re.compile(r"output\.(t|s|c|re|rc|fx)\.([A-Za-z0-9_]+)")
_LABEL = re.compile(r'label:\s*"S(\d+)\b[^"]*?(?:\[([^\]]*)\])?\s*"')
_SECTION = re.compile(r"^#\s*S(\d+)\b")
_ROW = re.compile(r"B-[A-Z0-9]+-\d{3}")
_TIMEOUT = re.compile(r"^\s*timeout:\s*(\d+)")
_HOOK = re.compile(r"^\s*HOOK:\s*([A-Za-z0-9-]+)")
_ADB = re.compile(r"^\s*#\s*adb:\s*([A-Za-z0-9-]+)")
_FX_T = re.compile(r"^\s*TARGET:\s*([A-Za-z0-9-]+)")
_FX_A = re.compile(r"^\s*ACTION:\s*([A-Za-z0-9-]+)")
SNAPSHOTS = {"base", "flutter-linux", "widgets", "flutter-final-seeded"}
AVDS = {"A", "S", "F", "T", "AR", "36", "26"}


def _string_keys() -> set[str]:
    return set(gen_strings.raw_table("en"))


def _fx_keys() -> set[str]:
    return set(gen_strings.fixture_data())


def parse_header(lines: list[str]) -> dict:
    h = {}
    for line in lines[:8]:
        m = re.match(r"^# (E2E-\d\d) (.+)$", line)
        if m:
            h["id"], h["title"] = m.group(1), m.group(2)
        for key in ("slice", "avd", "fixtures", "snapshot"):
            m = re.search(key + r":\s*(.+?)(?:\s{2,}\w+:|$)", line)
            if m and line.startswith("# slice"):
                h[key] = m.group(1).strip()
        if line.startswith("# rows:"):
            h["rows"] = _ROW.findall(line)
        if line.startswith("# KNOWN-FLUTTER-GAPS:"):
            h["gaps"] = line.split(":", 1)[1].strip()
    return h


def _blocks(lines: list[str]) -> list[tuple[int, int]]:
    """(start, end) line ranges of top-level commands (lines starting with '- ')."""
    starts = [i for i, l in enumerate(lines) if l.startswith("- ")]
    return [(s, (starts[k + 1] if k + 1 < len(starts) else len(lines))) for k, s in enumerate(starts)]


def lint_file(path: pathlib.Path, check_keys: bool = True, flows=None, keys=None, fx_keys=None) -> list[str]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    errs: list[str] = []
    name = path.name
    is_sub = name.startswith("_") or "/_" in path.as_posix() or not name.startswith("E2E-")

    def err(i, msg):
        errs.append(f"{path.name}:{i + 1}: {msg}")

    if "appId: ${APP_ID}" not in lines:
        err(0, "appId must be ${APP_ID} (never a literal package)")
    if "---" not in lines:
        err(0, "missing the '---' separator")

    header = parse_header(lines)
    rows_labelled: set[str] = set()
    tags_line = next((l for l in lines if l.startswith("tags:")), "")
    if not is_sub:
        flows = flows if flows is not None else catalog.load_flows()
        fid = name[:6]
        if header.get("id") != fid:
            err(0, f"first line must be '# {fid} <title>'")
        for k in ("slice", "avd", "fixtures", "snapshot", "rows", "gaps"):
            if k not in header:
                err(0, f"header lacks {k if k != 'gaps' else 'KNOWN-FLUTTER-GAPS'}")
        if header.get("avd") and not set(re.split(r"[ ,]+", header["avd"])) <= AVDS:
            err(1, f"unknown avd role {header['avd']}")
        if header.get("snapshot") and header["snapshot"] not in SNAPSHOTS:
            err(1, f"unknown snapshot {header['snapshot']}")
        want = flows.get(fid, {}).get("rows")
        if want is None:
            err(0, f"{fid} is not in e2e-flows.md")
        elif sorted(header.get("rows", [])) != sorted(want):
            err(2, f"rows {header.get('rows')} differ from the catalog {want}")
        body = lines[lines.index("---") + 1:] if "---" in lines else []
        first_cmd = next((l for l in body if l.startswith("- ")), "")
        if first_cmd.strip() != STRINGS_RUN:
            err(0, f"the first command must be '{STRINGS_RUN}'")
        if header.get("snapshot", "base") != "base" and any("clearState: true" in l for l in lines):
            err(0, "clearState: true is not allowed in a flow that starts from a snapshot (built-in Linux lives in app data)")

    long_ok = "linux-setup" in tags_line
    keys = keys if keys is not None else (_string_keys() if check_keys else set())
    fx_keys = fx_keys if fx_keys is not None else (_fx_keys() if check_keys else set())

    for i, line in enumerate(lines):
        stripped = line.split(" #", 1)[0] if not line.lstrip().startswith("#") else ""
        if _ID.match(line) and not line.lstrip().startswith("#"):
            err(i, "id: selectors are banned in parity flows")
        m = _SEL.match(stripped)
        if m:
            value = m.group(2).strip()
            if value and not value.startswith("{") and not value.startswith("${output.") \
                    and "literal-ok" not in line \
                    and not (i and lines[i - 1].lstrip().startswith("#") and "literal-ok" in lines[i - 1]):
                err(i, f"literal selector {value!r} needs ${{output.*}} or a '# literal-ok: <reason>' comment")
        if check_keys:
            for form, key in _KEYREF.findall(stripped):
                if form == "fx":
                    if key not in fx_keys:
                        err(i, f"unknown fixture key fx.{key}")
                elif key not in keys:
                    err(i, f"unknown string key {key} (not in the ARB, android strings.xml or _strings/extra.en.json)")
        m = _TIMEOUT.match(stripped)
        if m:
            limit = 300000 if long_ok else 60000
            if int(m.group(1)) > limit:
                err(i, f"timeout {m.group(1)} is over {limit} ms")
        if re.match(r"^\s*-\s*retry\s*:", stripped):
            window = "\n".join(lines[max(0, i - 2):i + 1])
            if "retry-reason:" not in window:
                err(i, "retry: needs a '# retry-reason:' comment (system UI only, never our own assertions)")
        for lm in _LABEL.finditer(stripped):
            for r in _ROW.findall(lm.group(2) or ""):
                rows_labelled.add(r)
                if not is_sub and r not in header.get("rows", []):
                    err(i, f"label names {r}, which is not in the header rows")

    # Referenced files exist (the generated strings.js excepted).
    for i, line in enumerate(lines):
        m = re.match(r"^\s*(?:-\s*(?:runFlow|runScript)\s*:|file\s*:)\s*([^\s#]+\.(?:yaml|js))\s*$", line)
        if m and not m.group(1).endswith("runner/out/strings.js") and "${" not in m.group(1):
            if not (path.parent / m.group(1)).exists():
                err(i, f"{m.group(1)} does not exist")

    # Hooks: allowlisted and announced by '# adb: <name>' just above their block.
    for start, end in _blocks(lines):
        block = lines[start:end]
        hook = next((m.group(1) for l in block for m in [_HOOK.match(l)] if m), None)
        if hook:
            if hook not in hooks.HOOKS:
                err(start, f"unknown hook {hook}")
            pre = lines[max(0, start - 3):start]
            if not any((m := _ADB.match(l)) and m.group(1) == hook for l in pre):
                err(start, f"hook {hook} needs a '# adb: {hook}' comment right above it")
        if any("_fx/fx.js" in l for l in block):
            if not any(_FX_T.match(l) for l in block) or not any(_FX_A.match(l) for l in block):
                err(start, "fx.js calls need TARGET and ACTION")

    # Sections: the first command after '# S<n>' carries label "S<n> ...".
    if not is_sub:
        section = None
        pending = False
        blocks = dict(_blocks(lines))
        for i, line in enumerate(lines):
            m = _SECTION.match(line)
            if m:
                section, pending = m.group(1), True
                continue
            if pending and line.startswith("- "):
                block = "\n".join(lines[i:blocks.get(i, i + 1)])
                if not re.search(r'label:\s*"S' + section + r"\b", block):
                    err(i, f"the first command of S{section} must carry label \"S{section} ...\"")
                pending = False
        missing = set(header.get("rows", [])) - rows_labelled
        if missing:
            err(0, f"header rows not named by any step label: {sorted(missing)}")
    return errs


def flow_files() -> list[pathlib.Path]:
    return sorted(p for p in FLOWS_DIR.rglob("*.yaml"))


def lint_tree(check_keys: bool = True) -> dict:
    flows = catalog.load_flows()
    keys = _string_keys() if check_keys else set()
    fx_keys = _fx_keys() if check_keys else set()
    errors: list[str] = []
    found: dict[str, str] = {}
    rows: set[str] = set()
    used = {"strings": set(), "fx_keys": set(), "hooks": set(), "fx_actions": set(), "fixtures": set()}
    for p in flow_files():
        errors += lint_file(p, check_keys, flows, keys, fx_keys)
        text = p.read_text(encoding="utf-8")
        for form, key in _KEYREF.findall(text):
            (used["fx_keys"] if form == "fx" else used["strings"]).add(key)
        used["hooks"].update(re.findall(r"(?m)^\s*HOOK:\s*([A-Za-z0-9-]+)", text))
        # Every (TARGET, ACTION) pair, nested runFlow commands included: an ACTION pairs with the TARGET
        # written just before it.
        target = None
        for line in text.splitlines():
            if (m := _FX_T.match(line)):
                target = m.group(1)
            elif (m := _FX_A.match(line)) and target:
                used["fx_actions"].add(f"{target} {m.group(1)}")
        if p.name.startswith("E2E-"):
            fid = p.name[:6]
            if fid in found and not p.name.endswith("-qa.yaml"):
                errors.append(f"{p.name}: {fid} has two main files")
            if not p.name.endswith("-qa.yaml"):
                found[fid] = p.relative_to(FLOWS_DIR).as_posix()
            h = parse_header(text.splitlines())
            rows.update(h.get("rows", []))
            used["fixtures"].update(x.strip() for x in h.get("fixtures", "").split(",") if x.strip())
    for fid in sorted(set(flows) - set(found)):
        errors.append(f"{fid}: no flow file")
    portable = catalog.portable_rows()
    if rows != portable:
        errors.append(f"flow rows differ from portable_e2e rows: missing {sorted(portable - rows)[:10]}, "
                      f"extra {sorted(rows - portable)[:10]}")
    return {"errors": errors, "flows": found, "rows": rows, "used": used}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--report", action="store_true")
    args = ap.parse_args(argv)
    rep = lint_tree()
    for e in rep["errors"]:
        print("ERROR", e)
    print(f"{len(rep['flows'])} flows, {len(rep['rows'])} rows, {len(rep['errors'])} errors")
    if args.report:
        print(json.dumps({k: sorted(v) for k, v in rep["used"].items()}, indent=1, ensure_ascii=False))
    return 1 if rep["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
