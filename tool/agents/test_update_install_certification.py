"""Install evidence validation and preservation checks; no device or Flutter use."""

import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


_SPEC = importlib.util.spec_from_file_location(
    "install_matrix_updater", Path(__file__).with_name("update_install_certification.py")
)
updater = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(updater)


def report(agent_id="codex"):
    results = {}
    for name in updater.RESULTS:
        facts = {"asserted": True,
                 **{key: True for key in updater.PASS_FACTS.get(name, ())}}
        if name == "uninstall":
            facts["bytesFreed"] = 1000000
        results[name] = {"state": "pass", "facts": facts}
    return {
        "schemaVersion": 1, "runID": "fq-install-codex-001",
        "device": "emulator-5554", "appBuild": 2196,
        "sourceRevision": "a" * 40, "architecture": "x64",
        "agentId": agent_id, "expectedVersion": updater.PINS[agent_id],
        "observedVersion": updater.PINS[agent_id], "helperVersion": "0.9.2",
        "evidence": "docs/qa/FQ-install-2026-10-08/README.md#codex",
        "results": results,
    }


class InstallEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for directory in ("docs/qa/FQ-install-2026-10-08", "docs/verification", "lib/domain"):
            (self.root / directory).mkdir(parents=True)
        (self.root / "docs/qa/FQ-install-2026-10-08/README.md").write_text("# Evidence\n")
        catalog = updater.REPO / "lib/domain/agent_catalog.dart"
        (self.root / "lib/domain/agent_catalog.dart").write_text(catalog.read_text())
        self.matrix = updater.renderer.read_json(updater.REPO / updater.renderer.MATRIX_PATH)
        self.matrix["agents"][1]["protocolCertification"] = {
            "scope": updater.renderer.PROTOCOL_SCOPE,
            "expectedVersion": "1.18.32", "observedVersion": "1.18.32",
            "deviceBuild": 2195, "runID": "fq3-preserved",
            "evidence": "docs/qa/FQ3-2026-10-08/fq3-preserved.json",
            "capabilities": {
                name: {"state": "fail", "code": "not_observed", "facts": {}}
                for name in updater.renderer.ALL_CAPABILITIES
            },
        }
        (self.root / updater.renderer.MATRIX_PATH).write_text(
            json.dumps(self.matrix, ensure_ascii=False, indent=1) + "\n"
        )
        (self.root / updater.renderer.MARKDOWN_PATH).write_text(
            updater.renderer.render_markdown(self.matrix)
        )

    def rejected(self, value):
        with self.assertRaises(updater.InvalidEvidence):
            updater.validate_report(value, self.root)

    def write_report(self, value):
        path = self.root / (value["agentId"] + ".json")
        path.write_text(json.dumps(value))
        return path

    def test_catalog_pins_match_every_current_install_agent(self):
        updater.verify_catalog_pins(self.root)
        for agent_id in updater.PINS:
            value = report(agent_id)
            self.assertEqual(updater.validate_report(value, self.root)["agentId"], agent_id)
        (self.root / "lib/domain/agent_catalog.dart").write_text("not a catalog")
        with self.assertRaises(updater.InvalidEvidence):
            updater.verify_catalog_pins(self.root)

    def test_only_three_cells_and_target_install_metadata_change(self):
        original = copy.deepcopy(self.matrix)
        result = updater.apply_report(self.matrix, report(), self.root)
        self.assertEqual(self.matrix, original)
        changed = next(row for row in result["agents"] if row["id"] == "codex")
        prior = next(row for row in original["agents"] if row["id"] == "codex")
        for key in updater.CELL_RESULTS:
            self.assertEqual(changed["cells"][key], {
                "state": "pass", "evidence": report()["evidence"],
            })
        for key, cell in prior["cells"].items():
            if key not in updater.CELL_RESULTS:
                self.assertEqual(changed["cells"][key], cell)
        self.assertEqual(set(changed["cells"]), set(prior["cells"]))
        self.assertEqual(changed["architecture"], "x64")
        self.assertEqual(set(changed["installCertification"]["results"]), set(updater.RESULTS))
        for index, row in enumerate(original["agents"]):
            if row["id"] != "codex":
                self.assertEqual(result["agents"][index], row)

    def test_unknown_helper_is_not_inherited_from_old_evidence(self):
        for helper in (None, "omitted"):
            value = report()
            if helper is None:
                value["helperVersion"] = None
            else:
                value.pop("helperVersion")
            codex = next(row for row in self.matrix["agents"] if row["id"] == "codex")
            codex["helperVersion"] = "0.9.2"
            changed = updater.apply_report(self.matrix, value, self.root)
            codex = next(row for row in changed["agents"] if row["id"] == "codex")
            self.assertNotIn("helperVersion", codex)

    def test_version_pass_requires_exact_pin_and_app_install_pass(self):
        for observed in (None, "9.9.9"):
            value = report()
            value["observedVersion"] = observed
            self.rejected(value)
        value = report()
        value["results"]["install"] = {"state": "partial", "facts": {}}
        self.rejected(value)
        value["results"]["version"] = {"state": "untested", "facts": {}}
        value.pop("observedVersion")
        updater.validate_report(value, self.root)

    def test_pass_requires_observed_facts_and_numeric_reclaimed_bytes(self):
        for name, keys in updater.PASS_FACTS.items():
            for key in ("asserted", *keys):
                value = report()
                value["results"][name]["facts"].pop(key)
                self.rejected(value)
        for amount in (None, False, 0, -1):
            value = report()
            value["results"]["uninstall"]["facts"]["bytesFreed"] = amount
            self.rejected(value)

    def test_manual_cleanup_is_partial_and_cannot_be_promoted(self):
        value = report()
        facts = value["results"]["uninstall"]["facts"]
        facts["removedViaApp"] = False
        self.rejected(value)
        value["results"]["uninstall"]["state"] = "partial"
        facts["asserted"] = False
        valid = updater.validate_report(value, self.root)
        self.assertEqual(valid["results"]["uninstall"]["state"], "partial")

    def test_credentials_free_bounded_fact_schema(self):
        for invalid in ("token-value", {}, [], None, float("nan"), float("inf"), 10**13):
            value = report()
            value["results"]["install"]["facts"]["observation"] = invalid
            self.rejected(value)
        value = report()
        value["results"]["install"]["code"] = "token-value"
        self.rejected(value)
        value = report()
        value["secret"] = "never print this"
        self.rejected(value)

    def test_other_devices_architectures_agents_and_bad_fields_rejected(self):
        for key, invalid in (
            ("device", "emulator-5556"), ("architecture", "arm64"),
            ("appBuild", 2195), ("appBuild", True), ("agentId", "claude"),
            ("expectedVersion", "9.9.9"), ("sourceRevision", "HEAD"),
            ("runID", "unexpected"), ("schemaVersion", True),
            ("helperVersion", "0.9.2 secret"),
        ):
            with self.subTest(key=key, invalid=invalid):
                value = report()
                value[key] = invalid
                self.rejected(value)
        value = report()
        value["results"].pop("lowStorage")
        self.rejected(value)

    def test_document_path_must_exist_and_stay_inside_qa(self):
        for evidence in (
            "docs/qa/missing.md", "docs/qa/../secret.md", "/tmp/evidence.md",
            "https://example.com/evidence.md", "docs/qa/FQ-install-2026-10-08/README.md?token=x",
        ):
            value = report()
            value["evidence"] = evidence
            self.rejected(value)
        path = self.root / "docs/qa/link.md"
        path.symlink_to(self.root / "docs/qa/FQ-install-2026-10-08/README.md")
        value = report()
        value["evidence"] = "docs/qa/link.md"
        self.rejected(value)

    def test_not_applicable_and_failed_checks_do_not_claim_asserted(self):
        value = report()
        value["results"]["lowStorage"] = {"state": "n/a", "facts": {}}
        self.assertEqual(updater.validate_report(value, self.root)["results"]["lowStorage"]["code"],
                         "not_applicable")
        value["results"]["lowStorage"]["facts"]["asserted"] = True
        self.rejected(value)

    def test_generator_check_is_read_only_and_detects_markdown_drift(self):
        paths = [self.write_report(report()), self.write_report(report("fx"))]
        updated = updater.update_paths(self.root, paths)
        matrix_before = (self.root / updater.renderer.MATRIX_PATH).read_bytes()
        markdown_before = (self.root / updater.renderer.MARKDOWN_PATH).read_bytes()
        updater.update_paths(self.root, paths, check=True)
        self.assertEqual((self.root / updater.renderer.MATRIX_PATH).read_bytes(), matrix_before)
        self.assertEqual((self.root / updater.renderer.MARKDOWN_PATH).read_bytes(), markdown_before)
        text = markdown_before.decode()
        self.assertIn("fq3-preserved", text)
        self.assertIn("## Phone-agent install certification", text)
        self.assertIn("removedViaApp", text)
        self.assertEqual(text, updater.render_markdown(updated))
        (self.root / updater.renderer.MARKDOWN_PATH).write_text(text + "stale\n")
        with self.assertRaises(updater.InvalidEvidence):
            updater.update_paths(self.root, paths, check=True)

    def test_invalid_later_report_and_duplicates_leave_both_outputs_unchanged(self):
        valid = self.write_report(report())
        invalid_value = report("fx")
        invalid_value["expectedVersion"] = "9.9.9"
        invalid = self.write_report(invalid_value)
        original_json = (self.root / updater.renderer.MATRIX_PATH).read_bytes()
        original_md = (self.root / updater.renderer.MARKDOWN_PATH).read_bytes()
        for paths in ([valid, invalid], [valid, valid]):
            with self.assertRaises(updater.InvalidEvidence):
                updater.update_paths(self.root, paths)
            self.assertEqual((self.root / updater.renderer.MATRIX_PATH).read_bytes(), original_json)
            self.assertEqual((self.root / updater.renderer.MARKDOWN_PATH).read_bytes(), original_md)

    def test_duplicate_json_fields_oversize_and_symbolic_reports_rejected(self):
        path = self.write_report(report())
        path.write_text('{"agentId":"codex","agentId":"fx"}')
        with self.assertRaises(updater.renderer.InvalidEvidence):
            updater.update_paths(self.root, [path])
        path.write_text(" " * (updater.MAX_REPORT_BYTES + 1))
        with self.assertRaises(updater.renderer.InvalidEvidence):
            updater.update_paths(self.root, [path])
        path = self.write_report(report())
        link = self.root / "linked.json"
        link.symlink_to(path)
        with self.assertRaises(updater.InvalidEvidence):
            updater.update_paths(self.root, [link])

    def test_cli_refusal_does_not_echo_untrusted_input(self):
        value = report()
        value["secret"] = "never-echo-this"
        path = self.write_report(value)
        stderr = io.StringIO()
        with mock.patch.object(updater, "REPO", self.root), contextlib.redirect_stderr(stderr):
            with self.assertRaises(SystemExit) as stopped:
                updater.main(["--report", str(path)])
        self.assertEqual(stopped.exception.code, 1)
        self.assertNotIn("never-echo-this", stderr.getvalue())
        self.assertIn("invalid evidence", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
