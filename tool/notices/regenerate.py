#!/usr/bin/env python3
"""Regenerate the "Package inventory" table of THIRD_PARTY_NOTICES.md.

Reads every hosted package in pubspec.lock, finds its LICENSE in the pub
cache (<cache>/hosted/pub.dev/<name>-<version>/LICENSE), classifies the
license from the text, takes the first copyright line from the same file,
and decides runtime versus test-only by walking the dependency graph from
pubspec.yaml (and the path packages under packages/).

Only the table between the "| Package | Version |" header and the
"### Notes on individual entries" heading is rewritten. The prose around it
(bundled components, notes, data flow) is hand-written and untouched.

Usage:
  python3 tool/notices/regenerate.py            rewrite the table in place
  python3 tool/notices/regenerate.py --check    exit 1 when the file differs
                                                (no writes, offline, ~1 s)

The pub cache is $PUB_CACHE or ~/.pub-cache. Without it the tool exits 2
and says why; it never guesses a license.
A package whose LICENSE is missing or unrecognised is listed as
"UNKNOWN" and fails --check, so it gets reviewed by a person.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTICES = ROOT / "THIRD_PARTY_NOTICES.md"
HEADER = "| Package | Version | License | Copyright line in the package's LICENSE | Role |"
TAIL = "### Notes on individual entries"


def cache_dir() -> Path:
    base = Path(os.environ.get("PUB_CACHE", Path.home() / ".pub-cache"))
    return base / "hosted" / "pub.dev"


def read_lock() -> dict[str, str]:
    """name -> version for every hosted package."""
    packages: dict[str, str] = {}
    current = None
    hosted = False
    version = None
    for line in (ROOT / "pubspec.lock").read_text().splitlines():
        m = re.match(r"^  ([A-Za-z0-9_]+):$", line)
        if m:
            current, hosted, version = m.group(1), False, None
            continue
        if current is None:
            continue
        if line == "    source: hosted":
            hosted = True
        v = re.match(r'^    version: "(.+)"$', line)
        if v:
            version = v.group(1)
        if hosted and version:
            packages[current] = version
    return packages


def dependency_names(pubspec: Path, sections=("dependencies",)) -> set[str]:
    """Top-level keys of the given sections of a pubspec.yaml (no YAML dep)."""
    names: set[str] = set()
    section = None
    for line in pubspec.read_text().splitlines():
        if re.match(r"^\S", line):
            m = re.match(r"^([a-z_]+):\s*$", line)
            section = m.group(1) if m else None
            continue
        if section in sections:
            m = re.match(r"^  ([A-Za-z0-9_]+):", line)
            if m:
                names.add(m.group(1))
    return names


# The Flutter SDK package is not in pubspec.lock (source: sdk), so its own
# runtime dependencies are listed here. They come from
# <flutter>/packages/flutter/pubspec.yaml; update this if Flutter changes them.
FLUTTER_RUNTIME_ROOTS = {
    "characters",
    "collection",
    "material_color_utilities",
    "meta",
    "vector_math",
}


def runtime_closure(packages: dict[str, str], cache: Path) -> set[str]:
    roots = dependency_names(ROOT / "pubspec.yaml") | FLUTTER_RUNTIME_ROOTS
    for sub in sorted((ROOT / "packages").glob("*/pubspec.yaml")):
        roots |= dependency_names(sub)
    seen: set[str] = set()
    todo = [r for r in roots if r in packages]
    while todo:
        name = todo.pop()
        if name in seen:
            continue
        seen.add(name)
        spec = cache / f"{name}-{packages[name]}" / "pubspec.yaml"
        if spec.exists():
            todo.extend(d for d in dependency_names(spec) if d in packages)
    return seen


def classify(text: str) -> str | None:
    found = []
    low = " ".join(text.split())
    if "Permission is hereby granted, free of charge" in low:
        found.append("MIT")
    if "Redistribution and use in source and binary forms" in low and (
        "Neither the name" in low
    ):
        found.append("BSD-3-Clause")
    if "Apache License" in low and "Version 2.0" in low:
        found.append("Apache-2.0")
    if "Mozilla Public License" in low:
        found.append("MPL-2.0")
    if not found:
        return None
    order = ["MIT", "BSD-3-Clause", "Apache-2.0", "MPL-2.0"]
    return " AND ".join(sorted(set(found), key=order.index))


def copyright_lines(text: str) -> list[str]:
    """Distinct copyright lines, in file order, boilerplate excluded."""
    lines: list[str] = []
    for raw in text.splitlines():
        line = raw.strip().lstrip("#/* ").strip()
        if not re.match(r"^Copyright\s*(\(c\)|©)?\s*\d", line, re.I):
            continue
        # The Apache appendix describes a copyright line; it is not one.
        if "notice that is included" in line or "{yyyy}" in line:
            continue
        line = line.rstrip(".")
        if line not in lines:
            lines.append(line)
    return lines


def build_rows() -> list[str]:
    cache = cache_dir()
    if not cache.is_dir():
        print(f"pub cache not found at {cache}; run `flutter pub get` or set PUB_CACHE", file=sys.stderr)
        sys.exit(2)
    packages = read_lock()
    runtime = runtime_closure(packages, cache)
    rows = []
    for name in sorted(packages):
        version = packages[name]
        folder = cache / f"{name}-{version}"
        license_file = next(
            (folder / n for n in ("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING") if (folder / n).exists()),
            None,
        )
        if license_file is None:
            if not folder.is_dir():
                print(f"{name} {version} is not in the pub cache; run `flutter pub get`", file=sys.stderr)
                sys.exit(2)
            kind, copy = "UNKNOWN", "no LICENSE file in the package"
        else:
            text = license_file.read_text(errors="replace")
            kind = classify(text) or "UNKNOWN"
            lines = copyright_lines(text)
            if kind in ("Apache-2.0", "MPL-2.0") or not lines:
                copy = "—"
            elif " AND " in kind:
                copy = "; ".join(lines)
            else:
                copy = lines[0]
            if kind == "UNKNOWN":
                copy = "license text not recognised; review by hand"
        role = "runtime" if name in runtime else "test-only"
        rows.append(f"| `{name}` | {version} | {kind} | {copy.replace('|', '/')} | {role} |")
    return rows


def render(current: str, rows: list[str]) -> str:
    start = current.index(HEADER)
    end = current.index(TAIL)
    table = "\n".join([HEADER, "|---|---|---|---|---|", *rows]) + "\n\n"
    return current[:start] + table + current[end:]


def main(argv: list[str]) -> int:
    check = "--check" in argv
    current = NOTICES.read_text()
    rows = build_rows()
    unknown = [r for r in rows if "| UNKNOWN |" in r]
    updated = render(current, rows)
    if check:
        if unknown:
            print("packages needing review:\n" + "\n".join(unknown), file=sys.stderr)
            return 1
        if updated != current:
            print(
                "THIRD_PARTY_NOTICES.md is out of date; run "
                "`python3 tool/notices/regenerate.py` and review the diff",
                file=sys.stderr,
            )
            return 1
        print("THIRD_PARTY_NOTICES.md package inventory is up to date")
        return 0
    NOTICES.write_text(updated)
    if unknown:
        print("packages needing review (marked UNKNOWN):\n" + "\n".join(unknown), file=sys.stderr)
        return 1
    print(f"wrote {len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
