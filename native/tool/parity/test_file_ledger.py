#!/usr/bin/env python3
"""Fixture-level CLI tests: python3 -I native/tool/parity/test_file_ledger.py."""

import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("file_ledger.py").resolve()
REPO = SCRIPT.parents[3]
CATALOG = "docs/design/native-rewrite-2026-11-01/catalog/behaviors.jsonl"
SCOPE = "native/catalog/scope.jsonl"
LEDGER = "docs/native/parity/ledger.json"
CSV = "docs/native/parity/FILE-LEDGER.csv"
MD = "docs/native/parity/FILE-LEDGER.md"
TEST_SOURCE = (
    "void main() {\n"
    "  test('one', () {});\n"
    "  testWidgets('two', (tester) async {});\n"
    "}\n"
)


class FileLedgerTest(unittest.TestCase):
    def test_named_function_declarations_are_not_test_calls(self):
        self.write(
            "test/declarations_test.dart",
            "void test(String name, void Function() body) {}\n"
            "testWidgets(String name) => null;\n"
            "void main() { test('real', () {}); }\n",
        )
        self.inputs()
        self.run_script()
        row = self.records()["test/declarations_test.dart"]
        self.assertEqual(int(row["test_calls"]), 1)
        self.assertEqual(json.loads(row["test_call_lines"]), [3])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="file-ledger-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write("pubspec.yaml", "name: fixture_app\n")
        self.behaviors = []
        self.scopes = []
        self.ledger = []

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def behavior(
        self,
        row_id,
        sources,
        scope="switch",
        state="proven",
        module=":engine:fixture",
        area="FIXTURE",
    ):
        self.behaviors.append({"id": row_id, "area": area, "sources": sources})
        self.scopes.append({"id": row_id, "scope": scope, "module": module})
        if state is not None:
            self.ledger.append({"id": row_id, "state": state})

    def inputs(self):
        self.write(CATALOG, "".join(json.dumps(r) + "\n" for r in self.behaviors))
        self.write(SCOPE, "".join(json.dumps(r) + "\n" for r in self.scopes))
        self.write(LEDGER, json.dumps(self.ledger) + "\n")

    def run_script(self, *args, ok=True):
        result = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--root", str(self.root), *args],
            cwd=REPO,
            text=True,
            capture_output=True,
            timeout=30,
        )
        if ok:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def records(self):
        with (self.root / CSV).open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        self.assertEqual(len(rows), len({row["file"] for row in rows}))
        return {row["file"]: row for row in rows}

    def test_status_rollups_require_only_switch_rows_and_proven_state(self):
        for name in ("covered", "partial", "missing", "dropped", "uncited", "no_state"):
            self.write(f"test/{name}_test.dart", TEST_SOURCE)
        self.behavior("B-FIXTURE-001", ["test/covered_test.dart:2"])
        self.behavior(
            "B-FIXTURE-002",
            ["test/covered_test.dart:3"],
            scope="after-switch",
            state="missing",
        )
        self.behavior("B-FIXTURE-003", ["test/partial_test.dart:2"])
        self.behavior("B-FIXTURE-004", ["test/partial_test.dart:3"], state="missing")
        self.behavior("B-FIXTURE-005", ["test/missing_test.dart:2"], state="cited")
        for index, scope in enumerate(
            ("after-switch", "dropped", "desktop-phase", "ios-phase"), 6
        ):
            self.behavior(
                f"B-FIXTURE-{index:03}", ["test/dropped_test.dart:2"], scope=scope
            )
        self.behavior("B-FIXTURE-010", ["test/no_state_test.dart:2"], state=None)
        self.inputs()
        self.run_script()
        rows = self.records()
        expected = {
            "covered": "COVERED",
            "partial": "PARTIAL",
            "missing": "MISSING",
            "dropped": "DROPPED",
            "uncited": "UNCITED",
            "no_state": "MISSING",
        }
        for name, status in expected.items():
            with self.subTest(name=name):
                row = rows[f"test/{name}_test.dart"]
                self.assertEqual(row["kind"], "test")
                self.assertEqual(row["status"], status)
                self.assertEqual(int(row["test_calls"]), 2)
        self.assertEqual(
            json.loads(rows["test/no_state_test.dart"]["states"]),
            {"B-FIXTURE-010": "missing"},
        )

    def test_call_lexer_and_exact_unique_citation_lines(self):
        source = (
            "\n".join(
                [
                    "void main() {",
                    "  // test('fake', () {});",
                    "  /* outer test(",
                    "     /* nested testWidgets( */ still comment */",
                    '  final ordinary = "test(\\"fake\\", () {})";',
                    "  final raw = r\"testWidgets('fake', (_) {})\";",
                    "  final triple = '''",
                    "  testWidgets('fake', (_) {});",
                    "  ''';",
                    "  test",
                    "    ('real', () {});",
                    "  testWidgets /* gap */ (",
                    "    'real 2', (_) {});",
                    "  test('same line', () {}); test('second', () {});",
                    "}",
                ]
            )
            + "\n"
        )
        self.write("test/nested/calls_test.dart", source)
        self.behavior(
            "B-FIXTURE-001",
            ["test/nested/calls_test.dart:10", "test/nested/calls_test.dart:10"],
        )
        self.behavior(
            "B-FIXTURE-002",
            ["test/nested/calls_test.dart:10", "test/nested/calls_test.dart:14"],
        )
        self.behavior("B-FIXTURE-003", ["test/nested/calls_test.dart:11"])
        self.inputs()
        self.run_script()
        row = self.records()["test/nested/calls_test.dart"]
        self.assertEqual(int(row["test_calls"]), 4)
        self.assertEqual(json.loads(row["test_call_lines"]), [10, 12, 14, 14])
        self.assertEqual(int(row["cited_call_lines"]), 2)
        self.assertEqual(json.loads(row["cited_test_call_lines"]), [10, 14])
        self.assertEqual(
            json.loads(row["row_ids"]),
            ["B-FIXTURE-001", "B-FIXTURE-002", "B-FIXTURE-003"],
        )

    def test_nested_interpolation_strings_and_comments_remain_opaque(self):
        self.write(
            "test/interpolation_test.dart",
            (
                "void main() {\n"
                "  final label = '${helper('test(')}';\n"
                "  final nested = '${() { /* } */ return \"${helper('testWidgets(')}\"; }()}';\n"
                "  test('real', () {});\n"
                "  testWidgets('real widget', (_) {});\n"
                "}\n"
            ),
        )
        self.behavior("B-FIXTURE-001", ["test/interpolation_test.dart:4"])
        self.inputs()
        self.run_script()
        row = self.records()["test/interpolation_test.dart"]
        self.assertEqual(int(row["test_calls"]), 2)
        self.assertEqual(json.loads(row["test_call_lines"]), [4, 5])
        self.assertEqual(json.loads(row["cited_test_call_lines"]), [4])

    def test_imports_and_exactly_one_reexport_hop(self):
        self.write(
            "test/imports_test.dart",
            "import 'package:fixture_app/direct.dart';\n"
            "import '../lib/relative.dart';\n"
            "import 'package:fixture_app/barrel.dart';\n"
            "import 'package:other_app/foreign.dart';\n" + TEST_SOURCE,
        )
        self.write("lib/direct.dart", "import 'ordinary_dependency.dart';\n")
        self.write("lib/relative.dart", "const relative = 1;\n")
        self.write(
            "lib/barrel.dart",
            "export 'exported.dart' show Exported;\n"
            "export 'package:fixture_app/package_export.dart';\n",
        )
        self.write("lib/exported.dart", "export 'second_hop.dart';\n")
        for name in ("package_export", "second_hop", "ordinary_dependency", "foreign"):
            self.write(
                f"lib/{name}.dart", f"class {name.title().replace('_', '')} {{}}\n"
            )
        self.behavior(
            "B-FIXTURE-001", ["test/imports_test.dart:6"], module=":feature:example"
        )
        self.inputs()
        self.run_script()
        rows = self.records()
        for name in ("direct", "relative", "barrel", "exported", "package_export"):
            with self.subTest(name=name):
                row = rows[f"lib/{name}.dart"]
                self.assertEqual(row["status"], "COVERED")
                self.assertEqual(
                    json.loads(row["test_files"]), ["test/imports_test.dart"]
                )
                self.assertEqual(
                    json.loads(row["native_modules"]), [":feature:example"]
                )
                self.assertEqual(json.loads(row["areas"]), ["FIXTURE"])
        for name in ("second_hop", "ordinary_dependency", "foreign"):
            with self.subTest(name=name):
                self.assertEqual(rows[f"lib/{name}.dart"]["status"], "NO_TESTS")

    def test_library_rollup_unions_all_importing_tests(self):
        self.write("lib/shared.dart", "class Shared {}\n")
        self.write("lib/uncited.dart", "class Uncited {}\n")
        for name in ("proven", "missing"):
            self.write(
                f"test/{name}_test.dart",
                "import 'package:fixture_app/shared.dart';\n" + TEST_SOURCE,
            )
        self.write(
            "test/uncited_test.dart", "import '../lib/uncited.dart';\n" + TEST_SOURCE
        )
        self.behavior("B-FIXTURE-001", ["test/proven_test.dart:3"], module=":engine:a")
        self.behavior(
            "B-FIXTURE-002",
            ["test/missing_test.dart:3"],
            state="missing",
            module=":engine:b",
        )
        self.inputs()
        self.run_script()
        rows = self.records()
        shared = rows["lib/shared.dart"]
        self.assertEqual(shared["status"], "PARTIAL")
        self.assertEqual(
            json.loads(shared["test_files"]),
            ["test/missing_test.dart", "test/proven_test.dart"],
        )
        self.assertEqual(
            json.loads(shared["native_modules"]), [":engine:a", ":engine:b"]
        )
        self.assertEqual(rows["lib/uncited.dart"]["status"], "UNCITED")

    def test_only_generated_localization_outputs_are_excluded(self):
        self.write("test/empty_test.dart", TEST_SOURCE)
        self.write("lib/l10n/app_localizations.dart", "// generated\n")
        self.write("lib/l10n/app_localizations_ar.dart", "// generated\n")
        self.write("lib/l10n/handwritten.dart", "const manual = 1;\n")
        self.write("lib/main.dart", "void main() {}\n")
        self.inputs()
        self.run_script()
        rows = self.records()
        self.assertEqual(
            set(rows),
            {"test/empty_test.dart", "lib/l10n/handwritten.dart", "lib/main.dart"},
        )

    def test_outputs_are_byte_deterministic_and_check_detects_stale_files(self):
        self.write("test/one_test.dart", TEST_SOURCE)
        self.write("lib/one.dart", "// one\n")
        self.inputs()
        self.run_script("--check", ok=False)
        self.assertFalse((self.root / CSV).exists())
        self.assertFalse((self.root / MD).exists())
        self.run_script()
        before = {name: (self.root / name).read_bytes() for name in (CSV, MD)}
        self.run_script()
        self.assertEqual(
            before, {name: (self.root / name).read_bytes() for name in (CSV, MD)}
        )
        self.run_script("--check")
        self.write(MD, "stale\n")
        self.run_script("--check", ok=False)
        self.assertEqual((self.root / MD).read_text(), "stale\n")
        self.run_script()
        (self.root / CSV).unlink()
        self.run_script("--check", ok=False)
        self.assertFalse((self.root / CSV).exists())

    def test_missing_scope_fails_without_creating_outputs(self):
        self.write("test/one_test.dart", TEST_SOURCE)
        self.behavior("B-FIXTURE-001", ["test/one_test.dart:2"])
        self.scopes.clear()
        self.inputs()
        result = self.run_script(ok=False)
        self.assertIn("scope", (result.stdout + result.stderr).lower())
        self.assertFalse((self.root / CSV).exists())
        self.assertFalse((self.root / MD).exists())

    def test_null_module_is_valid_only_outside_switch(self):
        self.write("test/deferred_test.dart", TEST_SOURCE)
        self.behavior(
            "B-FIXTURE-001",
            ["test/deferred_test.dart:2"],
            scope="after-switch",
            module=None,
        )
        self.inputs()
        self.run_script()
        row = self.records()["test/deferred_test.dart"]
        self.assertEqual(row["status"], "DROPPED")
        self.assertEqual(json.loads(row["native_modules"]), [])
        self.scopes[0]["scope"] = "switch"
        self.inputs()
        result = self.run_script(ok=False)
        self.assertIn("module", (result.stdout + result.stderr).lower())

    def test_context_and_stale_coordinates_and_exact_markdown_aggregates(self):
        self.write(
            "test/stale_test.dart", "import '../lib/stale.dart';\n" + TEST_SOURCE
        )
        self.write("lib/stale.dart", "class Stale {}\n")
        self.behavior(
            "B-STALE-001",
            ["test/stale_test.dart:999"],
            state="missing",
            area="STALE",
            module=":engine:stale",
        )
        self.behavior(
            "B-CONTEXT-001",
            ["context:day-1-update"],
            area="CONTEXT",
            module=":engine:context",
        )
        self.behavior(
            "B-LATER-001",
            ["context:deferred"],
            scope="after-switch",
            area="LATER",
            module=None,
        )
        self.inputs()
        self.run_script()
        rows = self.records()
        for name in ("test/stale_test.dart", "lib/stale.dart"):
            self.assertEqual(json.loads(rows[name]["row_ids"]), ["B-STALE-001"])
            self.assertEqual(rows[name]["status"], "MISSING")
            self.assertEqual(int(rows[name]["cited_call_lines"]), 0)
        md = (self.root / MD).read_text(encoding="utf-8")
        md_rows = [
            line.strip("| ").split(" | ")
            for line in md.splitlines()
            if line.startswith("| ")
        ]
        for kind in ("test", "lib"):
            self.assertIn([kind, "1", "0", "0", "1", "0", "0", "0"], md_rows)
            self.assertIn(
                ["STALE", kind, "1", "1", "0", "1", "0", "0", "1", "0", "0"], md_rows
            )
            self.assertIn(
                [":engine:stale", kind, "1", "1", "0", "1", "0", "0", "1", "0", "0"],
                md_rows,
            )
            self.assertIn(
                ["CONTEXT", kind, "1", "1", "1", "0", "0", "0", "0", "0", "0"], md_rows
            )
            self.assertIn(
                [":engine:context", kind, "1", "1", "1", "0", "0", "0", "0", "0", "0"],
                md_rows,
            )
            self.assertIn(
                ["LATER", kind, "1", "0", "0", "0", "0", "0", "0", "0", "0"], md_rows
            )
            self.assertIn(
                ["(unassigned)", kind, "1", "0", "0", "0", "0", "0", "0", "0", "0"],
                md_rows,
            )
        self.assertIn("Test calls: 2; distinct call lines: 2; cited call lines: 0.", md)
        self.assertIn(
            "Catalog source occurrences not matching an inventoried test-call line: 3.",
            md,
        )
        self.assertIn("context:day-1-update", md)
        self.assertIn("test/stale_test.dart:999", md)

    def test_conditional_imports_ignore_commented_and_quoted_fake_imports(self):
        self.write(
            "test/imports_test.dart",
            "// import 'package:fixture_app/fake_comment.dart';\n"
            "/* import '../lib/fake_block.dart'; */\n"
            "final fake = \"import 'package:fixture_app/fake_string.dart';\";\n"
            "import 'package:fixture_app/default.dart'\n"
            "    if (dart.library.io) 'package:fixture_app/io.dart'\n"
            "    if (dart.library.html) '../lib/web.dart';\n" + TEST_SOURCE,
        )
        for name in (
            "default",
            "io",
            "web",
            "fake_comment",
            "fake_block",
            "fake_string",
        ):
            self.write(f"lib/{name}.dart", "// fixture\n")
        self.inputs()
        self.run_script()
        rows = self.records()
        for name in ("default", "io", "web"):
            self.assertEqual(
                json.loads(rows[f"lib/{name}.dart"]["test_files"]),
                ["test/imports_test.dart"],
            )
            self.assertEqual(rows[f"lib/{name}.dart"]["status"], "UNCITED")
        for name in ("fake_comment", "fake_block", "fake_string"):
            self.assertEqual(rows[f"lib/{name}.dart"]["status"], "NO_TESTS")

    def test_invalid_scope_and_duplicate_input_ids_fail_closed(self):
        self.write("test/one_test.dart", TEST_SOURCE)
        self.behavior("B-FIXTURE-001", ["test/one_test.dart:2"])
        original = [self.behaviors, self.scopes, self.ledger]
        for mutation in (
            "scope",
            "catalog duplicate",
            "scope duplicate",
            "ledger duplicate",
        ):
            with self.subTest(mutation=mutation):
                self.behaviors, self.scopes, self.ledger = json.loads(
                    json.dumps(original)
                )
                if mutation == "scope":
                    self.scopes[0]["scope"] = "unknown"
                else:
                    target = {
                        "catalog duplicate": self.behaviors,
                        "scope duplicate": self.scopes,
                        "ledger duplicate": self.ledger,
                    }[mutation]
                    target.append(dict(target[0]))
                self.inputs()
                result = self.run_script(ok=False)
                self.assertIn(
                    "scope" if mutation == "scope" else "duplicate",
                    (result.stdout + result.stderr).lower(),
                )
                self.assertFalse((self.root / CSV).exists())
                self.assertFalse((self.root / MD).exists())

    def test_markdown_counts_and_top_lists_are_computed_and_limited(self):
        for index in range(61):
            self.write(
                f"test/rank_{index:02}_test.dart",
                "void main() {\n" + "  test('case', () {});\n" * (index + 1) + "}\n",
            )
            self.write(f"lib/rank_{index:02}.dart", "// line\n" * (index + 1))
        self.inputs()
        self.run_script()
        md = (self.root / MD).read_text(encoding="utf-8")
        self.assertIn("UNCITED", md)
        self.assertIn("NO_TESTS", md)
        self.assertIn("61", md)
        self.assertIn("test/rank_60_test.dart", md)
        self.assertIn("lib/rank_60.dart", md)
        self.assertNotIn("test/rank_00_test.dart", md)
        self.assertNotIn("lib/rank_00.dart", md)
        self.assertLess(
            md.index("test/rank_60_test.dart"), md.index("test/rank_59_test.dart")
        )
        self.assertLess(md.index("lib/rank_60.dart"), md.index("lib/rank_59.dart"))


if __name__ == "__main__":
    unittest.main()
