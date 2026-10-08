"""CLI regression tests for the partial-locale gate, with synthetic ARBs."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from check_partial_locales import CORE_HEADER, CORE_REPO_PATH, LOCALES, icu_shape


SCRIPT = Path(os.environ.get("L10N_PARTIAL_SCRIPT", Path(__file__).with_name("check_partial_locales.py"))).resolve()

ENGLISH = {
    "hello": "Hello",
    "count": "{count, plural, =1{1 file} other{{count} files}}",
    "named": "Open {name}",
    "untranslated": "Not translated yet",
}


class Fixtures(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.l10n = self.root / "lib/l10n"
        self.l10n.mkdir(parents=True)
        self.core_path = self.root / CORE_REPO_PATH
        self.core_path.parent.mkdir(parents=True)
        self.write("app_en.arb", ENGLISH)
        for locale in LOCALES:
            self.write_locale(locale)
        self.write_core(["count", "hello", "named"])

    def write(self, name, data):
        (self.l10n / name).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    def write_locale(self, locale, **changes):
        data = {
            "@@locale": locale,
            "hello": f"hello-{locale}",
            "count": "{count, plural, other{{count} f}}",
            "named": "{name} open",
        }
        data.update(changes)
        self.write(f"app_{locale}.arb", {k: v for k, v in data.items() if v is not None})

    def write_core(self, keys):
        self.core_path.write_text(CORE_HEADER + "".join(f"{key}\n" for key in keys), encoding="utf-8")

    def run_check(self, extra=()):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--l10n-dir", str(self.l10n), "--core", str(self.core_path), *extra],
            capture_output=True, text=True, check=False, cwd=self.root,
        )


class IcuShapeTest(unittest.TestCase):
    def test_plain_plural_and_select(self):
        self.assertEqual(icu_shape("Hello")[0], set())
        names, structure, problem = icu_shape("{n, plural, one{{n} a} other{{n} b {x}}}")
        self.assertEqual((names, structure, problem), ({"n", "x"}, [("n", "plural")], None))
        self.assertEqual(icu_shape("{who, select, a{A} other{B}}")[1], [("who", "select")])

    def test_problems(self):
        self.assertIsNotNone(icu_shape("{n, plural, one{x}}")[2])
        self.assertIsNotNone(icu_shape("Open {name")[2])
        self.assertIsNotNone(icu_shape("stray } brace")[2])


class CheckTest(Fixtures):
    def test_translated_core_passes_and_reports_counts_without_text(self):
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Partial locale ja: 3 of 4 messages", result.stdout)
        self.assertNotIn("hello-ja", result.stdout)

    def test_core_key_missing_in_one_locale_fails(self):
        self.write_locale("ru", hello=None)
        result = self.run_check()
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("ru: core key not translated", result.stdout)
        self.assertIn("hello", result.stdout)

    def test_untranslated_non_core_key_is_not_debt_that_fails(self):
        # English has "untranslated"; no locale carries it, and that is fine.
        self.assertEqual(self.run_check().returncode, 0)

    def test_placeholder_renamed_or_lost_fails(self):
        for value in ["{nom} open", "open"]:
            with self.subTest(value=value):
                self.write_locale("es", named=value)
                result = self.run_check()
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("arguments differ from English", result.stdout)

    def test_plural_dropped_fails(self):
        self.write_locale("pt", count="{count} f")
        result = self.run_check()
        self.assertEqual(result.returncode, 1)
        self.assertIn("pt:", result.stdout)

    def test_plural_without_other_fails(self):
        self.write_locale("zh", count="{count, plural, one{{count} f}}")
        self.assertEqual(self.run_check().returncode, 1)

    def test_key_english_lacks_fails(self):
        self.write_locale("ja", ghost="x")
        result = self.run_check()
        self.assertEqual(result.returncode, 1)
        self.assertIn("key English does not have", result.stdout)

    def test_wrong_locale_header_fails(self):
        self.write_locale("es", **{"@@locale": "pt"})
        self.assertEqual(self.run_check().returncode, 1)

    def test_empty_message_and_duplicate_key_are_invalid_input(self):
        self.write_locale("ru", hello=" ")
        self.assertEqual(self.run_check().returncode, 2)
        self.write_locale("ru")
        (self.l10n / "app_ru.arb").write_text('{"@@locale": "ru", "hello": "a", "hello": "b"}')
        self.assertEqual(self.run_check().returncode, 2)

    def test_missing_locale_file_is_invalid_input(self):
        (self.l10n / "app_ja.arb").unlink()
        self.assertEqual(self.run_check().returncode, 2)

    def test_core_list_needs_header_sorted_unique_exact_keys(self):
        self.core_path.write_text("hello\n")
        self.assertEqual(self.run_check().returncode, 2)
        for keys in [["named", "hello"], ["hello", "hello"], ["*"], ["hello "]]:
            with self.subTest(keys=keys):
                self.write_core(keys)
                self.assertEqual(self.run_check().returncode, 2)

    def test_core_key_english_lacks_fails(self):
        self.write_core(["count", "ghost", "hello", "named"])
        self.assertEqual(self.run_check().returncode, 1)


class CoreHistoryTest(Fixtures):
    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def initialize_history(self):
        self.git("init", "--quiet", "--initial-branch=fg5-fixture")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.hooksPath", "/dev/null")
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "test: initialize fixture [skip ci]")
        return self.git("rev-parse", "HEAD")

    def test_shrunk_core_list_fails_against_base_commit(self):
        base = self.initialize_history()
        self.write_core(["count", "hello"])
        result = self.run_check(["--base-ref", base])
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("Core list may not shrink", result.stdout)

    def test_grown_core_list_passes_against_base_commit(self):
        base = self.initialize_history()
        for locale in LOCALES:
            self.write_locale(locale, untranslated=f"u-{locale}")
        self.write_core(["count", "hello", "named", "untranslated"])
        self.assertEqual(self.run_check(["--base-ref", base]).returncode, 0)

    def test_base_without_a_core_list_accepts_the_introduction(self):
        self.core_path.unlink()
        base = self.initialize_history()
        self.write_core(["count", "hello", "named"])
        self.assertEqual(self.run_check(["--base-ref", base]).returncode, 0)

    def test_invalid_or_option_like_base_ref_fails_closed(self):
        self.initialize_history()
        for ref in ["missing-ref", "--help"]:
            with self.subTest(ref=ref):
                self.assertEqual(self.run_check(["--base-ref=" + ref]).returncode, 2)


if __name__ == "__main__":
    unittest.main()
