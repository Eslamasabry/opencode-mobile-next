"""versionCode allocator over native/BUILD_NUMBERS.md (E01-S09, plan 05 §9.1). Run through tool/version_code.sh.

Exit codes follow plan 02 §8: 0 done, 1 refused / the check failed, 2 could not run (bad input, adb failed).
"""
import argparse
import datetime
import os
import re
import subprocess
import sys

FLOOR = 10000  # ADR D3: the switch base; a floor only, never a range
# seen: a number on a device or server that this tool did not hand out; it raises the floor.
# retired: a number whose devices were wiped (decided in BUILD_NUMBERS.md); kept for history, never reused, no floor.
KINDS = ("build", "release", "seen", "retired")
PACKAGES = ("io.github.eslamasabry.opencode_mobile", "io.github.eslamasabry.opencode_mobile.native")
ROW = re.compile(r"^\|\s*(\d+)\s*\|\s*([a-z]+)\s*\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*(.*?)\s*\|\s*$")


class Refused(Exception):
    pass


class CannotRun(Exception):
    pass


def native_dir():
    return os.environ.get("OC_NATIVE") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def paths():
    n = native_dir()
    return os.path.join(n, "BUILD_NUMBERS.md"), os.path.join(n, "version.properties")


def read_ledger(path):
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().split("\n")
    except OSError as e:
        raise CannotRun(f"cannot read the ledger: {e.strerror}")
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == "## Ledger")
    except StopIteration:
        raise CannotRun("the ledger has no '## Ledger' section")
    rows = []
    for i in range(start + 1, len(lines)):
        line = lines[i]
        if line.startswith("## "):
            break
        m = ROW.match(line)
        if m:
            rows.append({"code": int(m.group(1)), "kind": m.group(2), "date": m.group(3), "for": m.group(4), "line": i})
        elif line.startswith("|") and not re.match(r"^\|\s*(versionCode|-+)\s*\|", line):
            raise CannotRun(f"ledger line {i + 1} is not a row: {line}")
    return lines, rows


def read_props(path):
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        raise CannotRun(f"cannot read version.properties: {e.strerror}")
    m = re.search(r"^versionCode=(\d+)\s*$", text, re.M)
    if not m:
        raise CannotRun("version.properties has no versionCode")
    return text, int(m.group(1))


def device_codes(serial):
    adb = os.environ.get("OC_ADB", "adb")
    found = []
    for pkg in PACKAGES:
        try:
            r = subprocess.run([adb, "-s", serial, "shell", "dumpsys", "package", pkg],
                               capture_output=True, text=True, timeout=60)
        except (OSError, subprocess.TimeoutExpired):
            raise CannotRun(f"adb could not read {serial}")
        if r.returncode != 0:
            raise CannotRun(f"adb could not read {serial} (exit {r.returncode})")
        found += [int(c) for c in re.findall(r"versionCode=(\d+)", r.stdout)]
    return found


def check_rows(rows):
    """Problems with the ledger itself, as plain sentences."""
    problems, seen_codes, highest = [], set(), 0
    for r in rows:
        if r["kind"] not in KINDS:
            problems.append(f"{r['code']}: unknown kind '{r['kind']}' (build, release, seen or retired)")
        if r["code"] in seen_codes:
            problems.append(f"{r['code']}: used twice (a number is never handed out again)")
        if r["kind"] in ("build", "release"):
            if r["code"] < FLOOR:
                problems.append(f"{r['code']}: below the floor {FLOOR}")
            if r["code"] <= highest:
                problems.append(f"{r['code']}: not above {highest}, which the ledger already held when it was taken")
            if r["kind"] == "release" and r["code"] % 10:
                problems.append(f"{r['code']}: a release number is a multiple of 10")
        seen_codes.add(r["code"])
        if r["kind"] != "retired":
            highest = max(highest, r["code"])
    return problems


def next_code(kind, known, used=()):
    """known: numbers that raise the floor; used: every number in the ledger, which is never handed out again."""
    top = max(known) if known else 0
    step = 10 if kind == "release" else 1
    code = max((top // 10 + 1) * 10 if kind == "release" else top + 1, FLOOR)  # FLOOR is a multiple of 10
    while code in used:
        code += step
    return code


def clean_for(text):
    text = " ".join((text or "").split())
    if not text or "|" in text:
        raise CannotRun("--for needs a short description without '|'")
    return text


def append_rows(ledger_path, lines, rows, new):
    """Insert rows after the last ledger row."""
    at = rows[-1]["line"] + 1 if rows else next(i for i, l in enumerate(lines) if l.strip() == "## Ledger") + 1
    if not rows:
        header = ["", "| versionCode | kind | date | for |", "|---|---|---|---|"]
        lines[at:at] = header
        at += len(header)
    lines[at:at] = [f"| {c} | {k} | {d} | {w} |" for c, k, d, w in new]
    with open(ledger_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main(argv):
    p = argparse.ArgumentParser(prog="version_code.sh")
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("next", "allocate"):
        s = sub.add_parser(name)
        s.add_argument("--kind", choices=("build", "release"), default="build")
        s.add_argument("--installed", type=int, action="append", default=[])
        s.add_argument("--device", action="append", default=[])
        if name == "allocate":
            s.add_argument("--for", dest="what", required=True)
            s.add_argument("--code", type=int, help="ask for this exact number; refused unless it is above every known one")
    s = sub.add_parser("seen")
    s.add_argument("--code", type=int, required=True)
    s.add_argument("--for", dest="what", required=True)
    sub.add_parser("check")
    a = p.parse_args(argv)

    ledger_path, props_path = paths()
    lines, rows = read_ledger(ledger_path)
    props, current = read_props(props_path)
    today = os.environ.get("OC_TODAY") or datetime.date.today().isoformat()
    ledger_codes = [r["code"] for r in rows]
    live_codes = [r["code"] for r in rows if r["kind"] != "retired"]

    if a.cmd == "check":
        problems = check_rows(rows)
        allocated = {r["code"] for r in rows if r["kind"] in ("build", "release")}
        if current not in allocated:
            problems.append(f"version.properties versionCode {current} was not handed out by the ledger")
        above = [c for c in live_codes if c > current]
        seen_above = [r["code"] for r in rows if r["kind"] == "seen" and r["code"] >= current]
        if seen_above:
            problems.append(f"version.properties versionCode {current} is not above {max(seen_above)}, which a device holds")
        for line in problems:
            print(f"ledger: {line}")
        print(f"ledger: {'FAIL' if problems else 'ok'} ({len(rows)} rows, version.properties {current}"
              + (f", newest {max(above)}" if above else "") + ")")
        return 1 if problems else 0

    if a.cmd == "seen":
        what = clean_for(a.what)
        if a.code in ledger_codes:
            raise Refused(f"{a.code} is already in the ledger")
        append_rows(ledger_path, lines, rows, [(a.code, "seen", today, what)])
        print(f"recorded seen {a.code}")
        return 0

    problems = check_rows(rows)
    if problems:
        raise Refused("the ledger is inconsistent; fix it first: " + "; ".join(problems))
    observed = list(a.installed)
    for serial in a.device:
        observed += device_codes(serial)
    known = live_codes + [current] + observed
    code = next_code(a.kind, known, set(ledger_codes))
    if a.cmd == "next":
        print(code)
        return 0

    what = clean_for(a.what)
    if a.code is not None:
        if a.code in ledger_codes:
            raise Refused(f"{a.code} is already in the ledger; a number is never handed out twice")
        if a.code <= max(known):
            raise Refused(f"{a.code} is not above {max(known)}; Android would refuse it as a downgrade")
        if a.kind == "release" and a.code % 10:
            raise Refused(f"{a.code}: a release number is a multiple of 10")
        code = a.code
    top_ledger = max(live_codes) if live_codes else 0
    new = [(c, "seen", today, "installed on a device when " + str(code) + " was taken")
           for c in sorted(set(observed)) if c > top_ledger and c not in ledger_codes]
    new.append((code, a.kind, today, what))
    append_rows(ledger_path, lines, rows, new)
    with open(props_path, "w", encoding="utf-8") as f:
        f.write(re.sub(r"^versionCode=\d+[ \t]*$", f"versionCode={code}", props, count=1, flags=re.M))
    print(code)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Refused as e:
        print(f"refused: {e}", file=sys.stderr)
        sys.exit(1)
    except CannotRun as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(2)
