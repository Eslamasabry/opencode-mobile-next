"""CLI regression tests using synthetic ARB data; no Flutter process required."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(os.environ.get("L10N_CHECK_SCRIPT", Path(__file__).with_name("check_completeness.py")))


class CompletenessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write("en.arb", {"@@locale": "en", "hello": "Hello", "@hello": {"description": "Greeting"}})
        self.write("ar.arb", {"@@locale": "ar", "hello": "مرحبا"})
        self.write("allowlist.json", {"source_document": "docs/localization-todo.md", "missing_arabic_keys": {}})
        (self.root / "todo.md").write_text("Server-provided text is excluded.\n", encoding="utf-8")

    def write(self, name, data):
        (self.root / name).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    def run_check(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--source", str(self.root / "en.arb"),
             "--target", str(self.root / "ar.arb"), "--allowlist", str(self.root / "allowlist.json"),
             "--todo", str(self.root / "todo.md")],
            capture_output=True, text=True, check=False,
        )

    def test_complete_messages_pass_without_metadata_parity(self):
        self.assertEqual(self.run_check().returncode, 0)

    def test_missing_arabic_message_fails(self):
        self.write("en.arb", {"hello": "Hello", "newMessage": "New"})
        result = self.run_check()
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("newMessage", result.stdout)

    def test_exact_documented_exception_passes(self):
        self.write("en.arb", {"hello": "Hello", "pending": "Pending"})
        self.write("allowlist.json", {"source_document": "docs/localization-todo.md", "missing_arabic_keys": {"pending": "Awaiting translation review"}})
        (self.root / "todo.md").write_text("Temporary exception: `pending` awaiting translation review.\n")
        self.assertEqual(self.run_check().returncode, 0)

    def test_undocumented_exception_fails(self):
        self.write("en.arb", {"hello": "Hello", "pending": "Pending"})
        self.write("allowlist.json", {"source_document": "docs/localization-todo.md", "missing_arabic_keys": {"pending": "Awaiting translation review"}})
        self.assertEqual(self.run_check().returncode, 2)

    def test_translated_exception_must_be_removed(self):
        self.write("allowlist.json", {"source_document": "docs/localization-todo.md", "missing_arabic_keys": {"hello": "Old exception"}})
        self.assertEqual(self.run_check().returncode, 2)

    def test_unknown_or_wildcard_exception_fails(self):
        for key in ["unknown", "*"]:
            with self.subTest(key=key):
                self.write("allowlist.json", {"source_document": "docs/localization-todo.md", "missing_arabic_keys": {key: "Exception"}})
                self.assertEqual(self.run_check().returncode, 2)

    def test_empty_arabic_message_fails(self):
        self.write("ar.arb", {"hello": " "})
        self.assertEqual(self.run_check().returncode, 2)

    def test_duplicate_keys_fail(self):
        (self.root / "ar.arb").write_text('{"hello": "first", "hello": "second"}')
        self.assertEqual(self.run_check().returncode, 2)

    def test_malformed_json_fails_without_echoing_values(self):
        (self.root / "ar.arb").write_text('{"hello": "synthetic-private-value"')
        result = self.run_check()
        self.assertEqual(result.returncode, 2)
        self.assertNotIn("synthetic-private-value", result.stdout + result.stderr)

    def test_missing_source_file_fails(self):
        (self.root / "en.arb").unlink()
        self.assertEqual(self.run_check().returncode, 2)

    def test_empty_source_fails(self):
        self.write("en.arb", {})
        self.assertEqual(self.run_check().returncode, 2)


if __name__ == "__main__":
    unittest.main()
