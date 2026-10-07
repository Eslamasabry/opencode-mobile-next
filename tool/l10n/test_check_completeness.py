"""CLI regression tests with synthetic ARBs and isolated Git history."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from check_completeness import BASELINE_HEADER, BASELINE_REPO_PATH


SCRIPT = Path(os.environ.get("L10N_CHECK_SCRIPT", Path(__file__).with_name("check_completeness.py"))).resolve()
GENERATOR = Path(os.environ.get("L10N_GENERATOR_SCRIPT", Path(__file__).with_name("update_arabic_baseline.py"))).resolve()


class Fixtures:
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.baseline = self.root / BASELINE_REPO_PATH
        self.baseline.parent.mkdir(parents=True)
        self.write("en.arb", {"@@locale": "en", "hello": "Hello", "@hello": {"description": "Greeting"}})
        self.write("ar.arb", {"@@locale": "ar", "hello": "مرحبا"})
        self.write_baseline([])

    def write(self, name, data):
        (self.root / name).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    def write_baseline(self, keys):
        self.baseline.write_text(BASELINE_HEADER + "".join(f"{key}\n" for key in keys), encoding="utf-8")

    def run_script(self, script, extra=()):
        return subprocess.run(
            [sys.executable, str(script), "--source", str(self.root / "en.arb"),
             "--target", str(self.root / "ar.arb"), "--baseline", str(self.baseline), *extra],
            capture_output=True, text=True, check=False, cwd=self.root,
        )

    def run_check(self, extra=()):
        return self.run_script(SCRIPT, extra)


class CompletenessTest(Fixtures, unittest.TestCase):
    def test_complete_messages_pass_without_metadata_parity(self):
        self.assertEqual(self.run_check().returncode, 0)

    def test_current_baseline_debt_passes(self):
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy"})
        self.write_baseline(["legacy"])
        self.assertEqual(self.run_check().returncode, 0)

    def test_new_missing_arabic_message_fails(self):
        self.write("en.arb", {"hello": "Hello", "newMessage": "New"})
        result = self.run_check()
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("newMessage", result.stdout)

    def test_legacy_allowlist_cannot_exempt_a_new_missing_key(self):
        self.write("en.arb", {"hello": "Hello", "newMessage": "New"})
        self.write("missing_arabic_allowlist.json", {"missing_arabic_keys": {"newMessage": "Exception"}})
        self.assertEqual(self.run_check().returncode, 1)

    def test_translated_baseline_entry_is_stale(self):
        self.write_baseline(["hello"])
        result = self.run_check()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Prune translated or removed baseline keys", result.stdout)

    def test_removed_or_unknown_source_key_is_stale(self):
        self.write_baseline(["removed"])
        self.assertEqual(self.run_check().returncode, 1)

    def test_unsorted_baseline_fails(self):
        self.write_baseline(["zeta", "alpha"])
        self.assertEqual(self.run_check().returncode, 2)

    def test_duplicate_baseline_keys_fail(self):
        self.write_baseline(["hello", "hello"])
        self.assertEqual(self.run_check().returncode, 2)

    def test_baseline_wildcards_and_whitespace_fail(self):
        for key in ["*", "hello ", " hello"]:
            with self.subTest(key=key):
                self.write_baseline([key])
                self.assertEqual(self.run_check().returncode, 2)

    def test_missing_policy_header_fails(self):
        self.baseline.write_text("hello\n")
        self.assertEqual(self.run_check().returncode, 2)

    def test_missing_baseline_fails_instead_of_resetting_debt(self):
        self.baseline.unlink()
        self.assertEqual(self.run_check().returncode, 2)
        self.assertEqual(self.run_script(GENERATOR).returncode, 2)
        self.assertFalse(self.baseline.exists())

    def test_generator_prunes_translated_and_removed_keys(self):
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy"})
        self.write_baseline(["hello", "legacy", "removed"])
        result = self.run_script(GENERATOR)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.baseline.read_text(), BASELINE_HEADER + "legacy\n")
        self.assertEqual(self.run_check().returncode, 0)

    def test_generator_refuses_growth_before_pruning_or_writing(self):
        self.write("en.arb", {"hello": "Hello", "newMessage": "New"})
        self.write_baseline(["removed"])
        before = self.baseline.read_bytes()
        result = self.run_script(GENERATOR)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(self.baseline.read_bytes(), before)

    def test_generator_no_change_preserves_baseline_bytes(self):
        before = self.baseline.read_bytes()
        self.assertEqual(self.run_script(GENERATOR).returncode, 0)
        self.assertEqual(self.baseline.read_bytes(), before)

    def test_generator_requires_sorted_unique_existing_baseline(self):
        for keys in [["zeta", "alpha"], ["hello", "hello"]]:
            with self.subTest(keys=keys):
                self.write_baseline(keys)
                before = self.baseline.read_bytes()
                self.assertEqual(self.run_script(GENERATOR).returncode, 2)
                self.assertEqual(self.baseline.read_bytes(), before)

    def test_empty_arabic_message_fails(self):
        self.write("ar.arb", {"hello": " "})
        self.assertEqual(self.run_check().returncode, 2)

    def test_duplicate_arb_keys_fail(self):
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


class BaselineHistoryTest(Fixtures, unittest.TestCase):
    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def initialize_history(self, baseline_exists=True):
        self.git("init", "--quiet", "--initial-branch=bd3-fixture")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.hooksPath", "/dev/null")
        if not baseline_exists:
            self.baseline.unlink()
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "test: initialize fixture [skip ci]")
        return self.git("rev-parse", "HEAD")

    def test_manually_grown_baseline_fails_against_base_commit(self):
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy"})
        self.write_baseline(["legacy"])
        base = self.initialize_history()
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy", "newMessage": "New"})
        self.write_baseline(["legacy", "newMessage"])
        self.assertEqual(self.run_check().returncode, 0)
        result = self.run_check(["--base-ref", base])
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("Baseline may not grow", result.stdout)

    def test_shrunk_baseline_passes_against_base_commit(self):
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy"})
        self.write_baseline(["legacy"])
        base = self.initialize_history()
        self.write("ar.arb", {"hello": "مرحبا", "legacy": "قديم"})
        self.write_baseline([])
        self.assertEqual(self.run_check(["--base-ref", base]).returncode, 0)

    def test_initial_introduction_requires_exact_current_debt(self):
        base = self.initialize_history(baseline_exists=False)
        self.write("en.arb", {"hello": "Hello", "legacy": "Legacy"})
        self.write_baseline(["legacy"])
        self.assertEqual(self.run_check(["--base-ref", base]).returncode, 0)
        self.write_baseline(["legacy", "removed"])
        self.assertEqual(self.run_check(["--base-ref", base]).returncode, 1)

    def test_invalid_or_option_like_base_ref_fails_closed(self):
        self.initialize_history()
        for ref in ["missing-ref", "--help"]:
            with self.subTest(ref=ref):
                self.assertEqual(self.run_check(["--base-ref=" + ref]).returncode, 2)


if __name__ == "__main__":
    unittest.main()
