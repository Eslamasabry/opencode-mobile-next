"""Flow lint rules (plan 14 §4.2) on small synthetic flows, then on the real tree."""
import pathlib
import tempfile
import textwrap
import unittest

from runner import lint_flows as lint

GOOD = textwrap.dedent('''\
    # E2E-01 First launch, welcome screen and first server by typed address
    # slice: 2   avd: A   fixtures: FX-OC2   snapshot: base
    # rows: B-NAV-017, B-NAV-018, B-NAV-024, B-NAV-037
    # KNOWN-FLUTTER-GAPS: none
    appId: ${APP_ID}
    tags: [parity, slice-2]
    ---
    - runScript: ../../runner/out/strings.js
    # S1 [B-NAV-017]
    - launchApp:
        clearState: true
        label: "S1 clean launch [B-NAV-017]"
    - assertVisible: ${output.s.welcomePhoneSetUp}
    # S2 [B-NAV-018, B-NAV-024, B-NAV-037]
    # adb: net
    - runScript:
        file: ../_hooks/adb.js
        env:
          HOOK: net
          ARGS: '{"wifi": "on"}'
        label: "S2 wifi on [B-NAV-018, B-NAV-024, B-NAV-037]"
    - tapOn: "Literal" # literal-ok: test
    ''')


class LintTest(unittest.TestCase):
    def lint_text(self, text, name="connect/E2E-01-x.yaml"):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / name
            p.parent.mkdir(parents=True)
            (pathlib.Path(d) / "_hooks").mkdir()
            (pathlib.Path(d) / "_hooks" / "adb.js").write_text("")
            p.write_text(text)
            return lint.lint_file(p, check_keys=True)

    def test_good_flow_passes(self):
        self.assertEqual([], self.lint_text(GOOD))

    def test_rows_must_match_catalog(self):
        errs = self.lint_text(GOOD.replace("B-NAV-037\n", "B-NAV-099\n", 1))
        self.assertTrue(any("rows" in e for e in errs), errs)

    def test_literal_appid_refused(self):
        errs = self.lint_text(GOOD.replace("${APP_ID}", "io.github.eslamasabry.opencode_mobile"))
        self.assertTrue(any("appId" in e for e in errs), errs)

    def test_id_selector_refused(self):
        errs = self.lint_text(GOOD + "- tapOn:\n    id: \"send\"\n")
        self.assertTrue(any("id:" in e for e in errs), errs)

    def test_bare_literal_selector_refused(self):
        errs = self.lint_text(GOOD + '- tapOn: "Send"\n')
        self.assertTrue(any("literal" in e for e in errs), errs)

    def test_unknown_string_key_refused(self):
        errs = self.lint_text(GOOD + "- assertVisible: ${output.s.noSuchKeyAnywhere}\n")
        self.assertTrue(any("noSuchKeyAnywhere" in e for e in errs), errs)

    def test_unknown_hook_refused(self):
        errs = self.lint_text(GOOD.replace("HOOK: net", "HOOK: rm-rf").replace("# adb: net", "# adb: rm-rf"))
        self.assertTrue(any("hook" in e for e in errs), errs)

    def test_long_wait_refused(self):
        errs = self.lint_text(GOOD + "- extendedWaitUntil:\n    visible: ${output.s.welcomePhoneSetUp}\n    timeout: 90000\n")
        self.assertTrue(any("timeout" in e for e in errs), errs)

    def test_retry_needs_reason(self):
        errs = self.lint_text(GOOD + "- retry:\n    maxRetries: 2\n    commands:\n      - back\n")
        self.assertTrue(any("retry" in e for e in errs), errs)

    def test_every_header_row_is_labelled_by_a_step(self):
        errs = self.lint_text(GOOD.replace("[B-NAV-018, B-NAV-024, B-NAV-037]\"", "[B-NAV-018, B-NAV-024]\""))
        self.assertTrue(any("B-NAV-037" in e for e in errs), errs)

    def test_clear_state_refused_in_linux_flow(self):
        errs = self.lint_text(GOOD.replace("snapshot: base", "snapshot: flutter-linux"))
        self.assertTrue(any("clearState" in e for e in errs), errs)

    def test_real_tree_is_clean_and_complete(self):
        report = lint.lint_tree()
        self.assertEqual([], report["errors"])
        self.assertEqual(39, len(report["flows"]))
        self.assertEqual(240, len(report["rows"]))


if __name__ == "__main__":
    unittest.main()


class PathTest(unittest.TestCase):
    def test_missing_subflow_is_reported(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "_sub" / "x.yaml"
            p.parent.mkdir()
            p.write_text("appId: ${APP_ID}\n---\n- runFlow: nope.yaml\n")
            self.assertTrue(any("nope.yaml" in e for e in lint.lint_file(p, check_keys=False)))
