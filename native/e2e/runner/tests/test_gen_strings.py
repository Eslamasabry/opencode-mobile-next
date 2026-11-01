"""ARB -> selector table conversion (plan 14 §4.3)."""
import unittest

from runner import gen_strings as g


class ConvertTest(unittest.TestCase):
    def test_plain_text_is_escaped_for_java_regex(self):
        e = g.convert("Stop OpenCode? (now) a.b")
        self.assertEqual(r"Stop OpenCode\? \(now\) a\.b", e["s"])
        self.assertEqual("Stop OpenCode? (now) a.b", e["t"])

    def test_placeholder_becomes_wildcard(self):
        e = g.convert("Paired with {host}.")
        self.assertEqual(r"Paired with .*\.", e["re"])
        self.assertEqual(r"(?s).*Paired with .*\..*", e["rc"])

    def test_plural_becomes_alternation_with_digits(self):
        e = g.convert("{count, plural, =1{1 attachment} other{{count} attachments}}")
        self.assertEqual(r"(?:1 attachment|\d+ attachments)", e["re"])

    def test_select_and_nested_text_around_icu(self):
        e = g.convert("Hi {n, select, a{A {x}} other{B}} end")
        self.assertEqual(r"Hi (?:A .*|B) end", e["re"])

    def test_contains_form(self):
        self.assertEqual(r"(?s).*Allow once.*", g.convert("Allow once")["c"])

    def test_build_reads_arb_android_and_extra(self):
        table = g.build("en")
        self.assertEqual("Connect to my computer", table["t"]["welcomeComputerConnect"])
        self.assertIn("android_tile_label", table["t"])
        self.assertIn("x_retry", table["t"])  # from flows/_strings/extra.en.json

    def test_arabic_falls_back_to_english_for_missing_keys(self):
        en, ar = g.build("en"), g.build("ar")
        self.assertEqual(set(en["t"]), set(ar["t"]))
        self.assertNotEqual(en["t"]["welcomeComputerConnect"], ar["t"]["welcomeComputerConnect"])


if __name__ == "__main__":
    unittest.main()
