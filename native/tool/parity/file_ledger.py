#!/usr/bin/env python3
"""Deterministic Flutter file parity inventory; run with python3 -I from root.

This is a lexical inventory, not a Dart analyzer or a test runner. Native proof
is consumed from ledger.json, never inferred from source text or row names.
"""

import argparse
import collections
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import sys


CATALOG = "docs/design/native-rewrite-2026-11-01/catalog/behaviors.jsonl"
SCOPE = "native/catalog/scope.jsonl"
LEDGER = "docs/native/parity/ledger.json"
OUTPUT = "docs/native/parity/FILE-LEDGER"
SCOPES = {"switch", "after-switch", "dropped", "desktop-phase", "ios-phase"}
STATUSES = ("COVERED", "PARTIAL", "MISSING", "DROPPED", "UNCITED", "NO_TESTS")
FIELDS = (
    "kind",
    "file",
    "line_count",
    "test_calls",
    "test_call_lines",
    "cited_call_lines",
    "cited_test_call_lines",
    "test_files",
    "row_ids",
    "scopes",
    "states",
    "areas",
    "native_modules",
    "status",
)


def comment_end(text, start):
    depth, i = 1, start + 2
    while i < len(text) and depth:
        if text.startswith("/*", i):
            depth += 1
            i += 2
        elif text.startswith("*/", i):
            depth -= 1
            i += 2
        else:
            i += 1
    return i


def string_end(text, start):
    """Return (body start, body end, token end), skipping interpolation bodies."""
    raw = text[start] == "r"
    i = start + int(raw)
    quote = text[i] * (3 if text.startswith(text[i] * 3, i) else 1)
    i += len(quote)
    body = i
    while i < len(text):
        if text.startswith(quote, i):
            return body, i, i + len(quote)
        if not raw and text[i] == "\\":
            i += 2
        elif not raw and text.startswith("${", i):
            i = interpolation_end(text, i + 2)
        else:
            i += 1
    return body, len(text), len(text)


def interpolation_end(text, start):
    depth, i = 1, start
    while i < len(text) and depth:
        if text.startswith("//", i):
            end = text.find("\n", i)
            i = len(text) if end < 0 else end
        elif text.startswith("/*", i):
            i = comment_end(text, i)
        elif text[i] in "'\"" or (
            text[i] == "r" and i + 1 < len(text) and text[i + 1] in "'\""
        ):
            _, _, i = string_end(text, i)
        else:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
    return i


def tokens(text):
    """Yield (kind, value, line) ignoring Dart comments and string contents.

    Strings are retained as single tokens for import/export URIs. Nested block
    comments, raw strings and triple quoted strings are supported. Interpolated
    string bodies are intentionally opaque, including any embedded call text.
    """
    i, line, size = 0, 1, len(text)
    while i < size:
        start, at = i, line
        if text[i].isspace():
            i += 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = size if end < 0 else end
        elif text.startswith("/*", i):
            i = comment_end(text, i)
        elif text[i] in "'\"" or (
            text[i] == "r" and i + 1 < size and text[i + 1] in "'\""
        ):
            body, end, i = string_end(text, i)
            yield "string", text[body:end], at
        elif text[i].isalpha() or text[i] in "_$":
            i += 1
            while i < size and (text[i].isalnum() or text[i] in "_$"):
                i += 1
            yield "identifier", text[start:i], at
        else:
            i += 1
            yield "symbol", text[start:i], at
        line += text.count("\n", start, i)


def scan_dart(path):
    text = path.read_text(encoding="utf-8")
    stream = list(tokens(text))
    calls, directives = [], {"import": [], "export": []}
    for i, (kind, value, line) in enumerate(stream):
        if kind != "identifier":
            continue
        if value in {"test", "testWidgets"} and i + 1 < len(stream):
            if stream[i + 1][1] == "(":
                # A declaration's parameter list is followed by a body, while
                # a registration call is followed by punctuation or chaining.
                depth, end = 0, i + 1
                while end < len(stream):
                    if stream[end][0] == "symbol":
                        if stream[end][1] == "(":
                            depth += 1
                        elif stream[end][1] == ")":
                            depth -= 1
                            if depth == 0:
                                break
                    end += 1
                following = stream[end + 1][1] if end + 1 < len(stream) else ""
                previous = stream[i - 1][1] if i else ""
                declaration = (
                    following in {"{", "=", "async", "sync"} or previous == "void"
                )
                if not declaration:
                    calls.append(line)
        if value in directives:
            for following in stream[i + 1 :]:
                if following[1] == ";":
                    break
                if following[0] == "string":
                    directives[value].append(following[1])
    return len(text.splitlines()), calls, directives


def read_rows(root, relative):
    text = (root / relative).read_text(encoding="utf-8")
    rows = (
        json.loads(text)
        if relative.endswith(".json")
        else [json.loads(line) for line in text.splitlines() if line.strip()]
    )
    if not isinstance(rows, list):
        raise ValueError(f"{relative}: expected a row list")
    indexed = {}
    for row in rows:
        row_id = row["id"]
        if row_id in indexed:
            raise ValueError(f"{relative}: duplicate id {row_id}")
        indexed[row_id] = row
    return indexed


def resolve_uri(root, importing, uri, app):
    prefix = f"package:{app}/"
    if uri.startswith(prefix):
        target = root / "lib" / uri[len(prefix) :]
    elif ":" in uri:
        return None
    else:
        target = root / importing.parent / uri
    try:
        relative = target.resolve().relative_to(root.resolve())
    except ValueError:
        return None
    return (
        relative.as_posix() if target.is_file() and target.suffix == ".dart" else None
    )


def status_for(row_ids, scope, states, no_tests=False):
    if no_tests:
        return "NO_TESTS"
    if not row_ids:
        return "UNCITED"
    required = [row for row in row_ids if scope[row]["scope"] == "switch"]
    if not required:
        return "DROPPED"
    proven = sum(
        states.get(row, {}).get("state", "missing") == "proven" for row in required
    )
    if proven == len(required):
        return "COVERED"
    return "PARTIAL" if proven else "MISSING"


def inventory(root):
    catalog = read_rows(root, CATALOG)
    scope = read_rows(root, SCOPE)
    states = read_rows(root, LEDGER)
    for row_id in catalog:
        if row_id not in scope:
            raise ValueError(f"scope missing for {row_id}")
        if scope[row_id]["scope"] not in SCOPES:
            raise ValueError(f"invalid scope for {row_id}")
        module = scope[row_id]["module"]
        if not isinstance(module, str) and not (
            module is None and scope[row_id]["scope"] != "switch"
        ):
            raise ValueError(f"invalid native module for {row_id}")
    match = re.search(
        r"(?m)^name:\s*([A-Za-z0-9_]+)\s*$", (root / "pubspec.yaml").read_text()
    )
    if not match:
        raise ValueError("pubspec.yaml: cannot read app package name")
    app = match[1]
    test_paths = sorted(
        p.relative_to(root).as_posix() for p in (root / "test").rglob("*_test.dart")
    )
    lib_paths = sorted(
        p.relative_to(root).as_posix()
        for p in (root / "lib").rglob("*.dart")
        if not (
            p.parent == root / "lib/l10n"
            and re.fullmatch(r"app_localizations(?:_[a-zA-Z_]+)?\.dart", p.name)
        )
    )
    test_names, lib_names = set(test_paths), set(lib_paths)
    scans = {}

    def scan(name):
        if name not in scans:
            scans[name] = scan_dart(root / name)
        return scans[name]

    citations = collections.defaultdict(set)
    rows_by_test = collections.defaultdict(set)
    unmatched_sources = []
    for row_id, row in sorted(catalog.items()):
        for source in row["sources"]:
            name, separator, coordinate = source.rpartition(":")
            if source.startswith("context:"):
                unmatched_sources.append(
                    (row_id, source, "owner context, not a file citation")
                )
                continue
            if not separator or not coordinate.isdecimal() or int(coordinate) < 1:
                raise ValueError(f"invalid source coordinate for {row_id}")
            if name not in test_names:
                unmatched_sources.append(
                    (row_id, source, "not an inventoried test file")
                )
                continue
            rows_by_test[name].add(row_id)
            citations[name].add(int(coordinate))
            if int(coordinate) not in scan(name)[1]:
                unmatched_sources.append((row_id, source, "not a test-call line"))

    importing_tests = collections.defaultdict(set)
    for name in test_paths:
        for uri in scan(name)[2]["import"]:
            direct = resolve_uri(root, Path(name), uri, app)
            if direct is None:
                continue
            reached = {direct}
            for exported in scan(direct)[2]["export"]:
                target = resolve_uri(root, Path(direct), exported, app)
                if target:
                    reached.add(target)
            for target in reached:
                if target in lib_names:
                    importing_tests[target].add(name)

    records = []
    for kind, names in (("test", test_paths), ("lib", lib_paths)):
        for name in names:
            tests = [name] if kind == "test" else sorted(importing_tests[name])
            row_ids = sorted(set().union(*(rows_by_test[test] for test in tests)))
            line_count, calls, _ = scan(name)
            matched = sorted(set(calls) & citations[name]) if kind == "test" else []
            records.append(
                {
                    "kind": kind,
                    "file": name,
                    "line_count": line_count,
                    "test_calls": len(calls) if kind == "test" else 0,
                    "test_call_lines": calls if kind == "test" else [],
                    "cited_call_lines": len(matched),
                    "cited_test_call_lines": matched,
                    "test_files": tests,
                    "row_ids": row_ids,
                    "scopes": {row: scope[row]["scope"] for row in row_ids},
                    "states": {
                        row: states.get(row, {}).get("state", "missing")
                        for row in row_ids
                    },
                    "areas": sorted({catalog[row]["area"] for row in row_ids}),
                    "native_modules": sorted(
                        {
                            scope[row]["module"]
                            for row in row_ids
                            if scope[row]["module"] is not None
                        }
                    ),
                    "status": status_for(
                        row_ids, scope, states, kind == "lib" and not tests
                    ),
                }
            )
    return records, catalog, scope, states, unmatched_sources


def csv_text(records):
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    for record in records:
        writer.writerow(
            {
                key: json.dumps(
                    value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
                )
                if isinstance(value, (list, dict))
                else value
                for key, value in record.items()
            }
        )
    return output.getvalue()


def table(headers, rows):
    def cell(value):
        return str(value).replace("|", "\\|").replace("\n", " ")

    return (
        "\n".join(
            "| " + " | ".join(map(cell, row)) + " |"
            for row in [headers, ["---"] * len(headers), *rows]
        )
        + "\n"
    )


def markdown(root, records, catalog, scope, states, unmatched):
    lines = [
        "# Flutter-to-native file parity ledger",
        "",
        "Generated by `python3 -I native/tool/parity/file_ledger.py` from the repository root.",
        "Verify without writing: `python3 -I native/tool/parity/file_ledger.py --check`.",
        "",
        "## Counting rules",
        "",
        "- A row is proven only when its input ledger `state` is exactly `proven`. No native tests run here.",
        "- Only `switch` rows are required. All required rows proven = COVERED; some = PARTIAL; none = MISSING.",
        "- Rows exist but none are `switch` = DROPPED (including deferred platform/after-switch rows).",
        "- No citing rows = UNCITED; a lib file with no importing test = NO_TESTS.",
        "- A status describes catalog rows, not proof that every Flutter test or implementation was ported.",
        "- Test calls are lexical `test(` / `testWidgets(` tokens, with whitespace allowed; comments and string bodies are ignored.",
        "  Function declarations with a body or an explicit `void` return are excluded; this is not a full Dart parser.",
        "  Runtime expansion, helper registrations, interpolation bodies and conditional execution are not evaluated.",
        "- Cited call lines are the distinct call-start lines cited exactly by a catalog source; calls on one line remain separate calls.",
        "  All file citations reach rows even when their coordinates do not coincide with a call start.",
        "- Lib links follow direct test imports plus one export hop from each imported file. All conditional URI alternatives count;",
        "  ordinary transitive imports, further export hops and export show/hide symbol filtering are not evaluated.",
        "- Only this app's package URIs and existing in-repo relative Dart paths resolve. Generated `lib/l10n/app_localizations*.dart`",
        "  outputs are excluded; handwritten localization files remain. File line counts use `splitlines()`.",
        "- Lists/maps in CSV cells are JSON, sorted by path or row id. Call lines retain source order and duplicates.",
        "  Missing ledger rows count as `missing`; absent/invalid catalog scopes fail generation.",
        "",
        "## Input fingerprints",
        "",
    ]
    lines.append(
        table(
            ["Input", "SHA-256", "Rows"],
            [
                [
                    name,
                    hashlib.sha256((root / name).read_bytes()).hexdigest(),
                    len(rows),
                ]
                for name, rows in ((CATALOG, catalog), (SCOPE, scope), (LEDGER, states))
            ],
        )
    )
    lines += [
        "The committed ledger is a native JUnit result snapshot; these outputs do not refresh or independently qualify that evidence.",
        f"Scope-only IDs absent from the catalog: {len(set(scope) - set(catalog))}. "
        f"Catalog IDs absent from the ledger: {len(set(catalog) - set(states))}.",
        "",
        "## File totals",
        "",
    ]
    lines.append(
        table(
            ["Kind", "Files", *STATUSES],
            [
                [
                    kind,
                    sum(r["kind"] == kind for r in records),
                    *(
                        sum(
                            r["kind"] == kind and r["status"] == status for r in records
                        )
                        for status in STATUSES
                    ),
                ]
                for kind in ("test", "lib")
            ],
        )
    )
    tests = [r for r in records if r["kind"] == "test"]
    lines += [
        f"Test calls: {sum(r['test_calls'] for r in tests)}; distinct call lines: "
        f"{sum(len(set(r['test_call_lines'])) for r in tests)}; cited call lines: "
        f"{sum(r['cited_call_lines'] for r in tests)}.",
        "",
    ]
    for title, key in (("Catalog area", "areas"), ("Native module", "native_modules")):
        lines += [
            f"## By {title.lower()}",
            "",
            "Files can occur in multiple groups. Status counts roll up only the rows in that group; totals above roll up all rows.",
            "",
        ]

        def row_group(row):
            return (
                catalog[row]["area"]
                if key == "areas"
                else scope[row]["module"] or "(unassigned)"
            )

        groups = sorted({row_group(row) for row in catalog})
        grouped = []
        for value in groups:
            ids = {row for row in catalog if row_group(row) == value}
            for kind in ("test", "lib"):
                members = [
                    r for r in records if r["kind"] == kind and set(r["row_ids"]) & ids
                ]
                counts = collections.Counter(
                    status_for(set(r["row_ids"]) & ids, scope, states) for r in members
                )
                grouped.append(
                    [
                        value,
                        kind,
                        len(ids),
                        sum(scope[row]["scope"] == "switch" for row in ids),
                        sum(
                            scope[row]["scope"] == "switch"
                            and states.get(row, {}).get("state") == "proven"
                            for row in ids
                        ),
                        len(members),
                        *(counts[status] for status in STATUSES[:-1]),
                    ]
                )
        lines.append(
            table(
                [
                    title,
                    "Kind",
                    "Rows",
                    "Switch rows",
                    "Proven switch rows",
                    "Files",
                    *STATUSES[:-1],
                ],
                grouped,
            )
        )
    lines += [
        "## Top 60 UNCITED test files",
        "",
        "Sorted by test-call count descending, then path.",
        "",
    ]
    lines.append(
        table(
            ["File", "Test calls", "Lines"],
            [
                [r["file"], r["test_calls"], r["line_count"]]
                for r in sorted(
                    (r for r in tests if r["status"] == "UNCITED"),
                    key=lambda r: (-r["test_calls"], r["file"]),
                )[:60]
            ],
        )
    )
    lines += [
        "## Top 60 NO_TESTS lib files",
        "",
        "Sorted by line count descending, then path.",
        "",
    ]
    lines.append(
        table(
            ["File", "Lines"],
            [
                [r["file"], r["line_count"]]
                for r in sorted(
                    (r for r in records if r["status"] == "NO_TESTS"),
                    key=lambda r: (-r["line_count"], r["file"]),
                )[:60]
            ],
        )
    )
    lines += [
        "## Citation diagnostics",
        "",
        f"Catalog source occurrences not matching an inventoried test-call line: {len(unmatched)}.",
        "These are reported, never promoted to cited call coverage or silently repaired.",
        "",
    ]
    lines.append(table(["Row", "Source", "Reason"], unmatched))
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root", type=Path, default=Path.cwd(), help="repository root (default: cwd)"
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if outputs are absent or stale; do not write",
    )
    args = parser.parse_args()
    try:
        records, catalog, scope, states, unmatched = inventory(args.root)
        outputs = {
            "csv": csv_text(records),
            "md": markdown(args.root, records, catalog, scope, states, unmatched),
        }
        stale = []
        for extension, content in outputs.items():
            path = args.root / f"{OUTPUT}.{extension}"
            if args.check:
                if not path.is_file() or path.read_bytes() != content.encode("utf-8"):
                    stale.append(path.relative_to(args.root).as_posix())
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content.encode("utf-8"))
        if stale:
            print("Stale or missing: " + ", ".join(stale), file=sys.stderr)
            return 1
        print(
            f"{'Checked' if args.check else 'Generated'} {len(records)} file rows; {len(unmatched)} citation diagnostics."
        )
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"file ledger: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
