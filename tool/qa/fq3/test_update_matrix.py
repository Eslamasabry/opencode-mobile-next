"""FQ3 evidence acceptance and fail-closed matrix preservation contracts."""

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
    "fq3_update_matrix", Path(__file__).with_name("update_matrix.py")
)
updater = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(updater)


def passed(capability):
    return {
        "state": "pass",
        "code": "verified",
        "facts": {
            "asserted": True,
            **{key: True for key in updater.PASS_FACTS.get(capability, ())},
        },
    }


def live_run():
    return {
        "schemaVersion": 1,
        "runID": "fq3-test-001",
        "device": "emulator-5554",
        "appBuild": 2195,
        "sourceRevision": "a" * 40,
        "startedAt": "2026-10-08T04:05:06.123456Z",
        "scope": "phone-runtime",
        "engines": {
            key: {
                "expectedVersion": version,
                "observedVersion": version,
                "results": {capability: passed(capability)
                            for capability in updater.CAPABILITIES},
            }
            for key, version in updater.VERSIONS.items()
        },
        "protocolSwitch": passed("protocolSwitch"),
        "evidence": f"{updater.EVIDENCE_DIRECTORY}/fq3-test-001.json",
        "attestation": {
            "runtime": "in-app-ubuntu",
            "transport": "adb-forward",
            "live": True,
            "appUID": 10123,
            "versionProbeUID": 10123,
            "buildVerified": True,
            "credentialSource": "runtime-launch-config",
        },
    }


def existing_matrix():
    """Distinct existing cells and an unrelated agent catch accidental promotion."""
    return {
        "updated": "2026-10-07 (late)",
        "device": "emulator-5554, not the owner phone",
        "rule": "BA4 capabilities are granted from existing pass cells only.",
        "metadata": "Existing catalog and helper evidence remains unchanged.",
        "columns": [
            {"id": "install", "means": "Pinned download and checksum verified"},
            {"id": "cards", "means": "Cards answered from chat and list"},
        ],
        "agents": [
            {
                "id": "opencode", "name": "OpenCode 1", "route": "OC1 server",
                "agentVersion": "1.18.32", "helperVersion": "0.9.2",
                "cells": {
                    "install": {"state": "untested", "evidence": None},
                    "cards": {"state": "pass", "evidence": "docs/qa/old-card.md"},
                },
            },
            {
                "id": "opencode2", "name": "OpenCode 2", "route": "OC2 server",
                "cells": {
                    "install": {"state": "untested", "evidence": None},
                    "cards": {"state": "off", "evidence": "BA6"},
                },
            },
            {
                "id": "claude", "name": "Claude Code", "route": "Paseo native",
                "agentVersion": "2.1.283", "customMetadata": {"preserve": True},
                "cells": {
                    "install": {"state": "pass", "evidence": "docs/qa/claude.md"},
                    "cards": {"state": "blocked:OW1", "evidence": "Owner needed"},
                },
            },
        ],
        "otherMetadata": {"retained": [1, 2, 3]},
    }


class EvidenceValidationTest(unittest.TestCase):
    def assert_rejected(self, run):
        with self.assertRaises(updater.InvalidEvidence):
            updater.validate_run(run)

    def test_live_assertions_record_only_the_separate_protocol_namespace(self):
        matrix = existing_matrix()
        original = copy.deepcopy(matrix)
        run = live_run()
        updated = updater.apply_run(matrix, run)
        self.assertEqual(matrix, original)
        without_protocol = copy.deepcopy(updated)
        for row in without_protocol["agents"][:2]:
            protocol = row.pop("protocolCertification")
            self.assertEqual(protocol["scope"], updater.PROTOCOL_SCOPE)
            self.assertEqual(protocol["deviceBuild"], 2195)
            self.assertEqual(protocol["expectedVersion"], updater.VERSIONS[row["id"]])
            self.assertEqual(protocol["observedVersion"], protocol["expectedVersion"])
            self.assertEqual(protocol["runID"], run["runID"])
            self.assertEqual(protocol["evidence"], run["evidence"])
            self.assertEqual(set(protocol["capabilities"]), set(updater.ALL_CAPABILITIES))
            self.assertEqual(protocol["capabilities"]["protocolSwitch"],
                             run["protocolSwitch"])
        self.assertEqual(without_protocol, original)
        run["engines"]["opencode"]["results"]["stream"]["facts"].clear()
        self.assertTrue(updated["agents"][0]["protocolCertification"]
                        ["capabilities"]["stream"]["facts"]["completedReply"])

    def test_version_pass_requires_exact_observed_pin(self):
        for engine in updater.VERSIONS:
            for observed in (None, "9.9.9"):
                with self.subTest(engine=engine, observed=observed):
                    run = live_run()
                    run["engines"][engine]["observedVersion"] = observed
                    self.assert_rejected(run)
            run = live_run()
            run["engines"][engine]["expectedVersion"] = "9.9.9"
            self.assert_rejected(run)

    def test_version_failure_can_record_mismatch_without_promoting_existing_cells(self):
        run = live_run()
        run["engines"]["opencode2"]["observedVersion"] = "2.0.9"
        run["engines"]["opencode2"]["results"]["version"] = {
            "state": "fail", "code": "version_mismatch", "facts": {},
        }
        updated = updater.apply_run(existing_matrix(), run)
        self.assertEqual(updated["agents"][1]["cells"]["install"]["state"], "untested")
        self.assertEqual(updated["agents"][1]["protocolCertification"]
                         ["capabilities"]["version"]["state"], "fail")

    def test_unit_scope_wrong_device_or_build_cannot_qualify(self):
        for field, value in (("scope", "unit-test"), ("device", "emulator-5556"),
                             ("appBuild", 2194), ("appBuild", True),
                             ("schemaVersion", True)):
            with self.subTest(field=field, value=value):
                run = live_run()
                run[field] = value
                self.assert_rejected(run)

    def test_live_app_uid_attestation_is_mandatory(self):
        run = live_run()
        del run["attestation"]
        self.assert_rejected(run)
        for field, value in (("live", False), ("live", 1), ("buildVerified", False),
                             ("appUID", 0), ("appUID", 2000), ("appUID", 99999),
                             ("versionProbeUID", 0), ("versionProbeUID", 10124),
                             ("runtime", "host-unit-test"), ("transport", "localhost"),
                             ("credentialSource", "secure-storage")):
            with self.subTest(field=field, value=value):
                run = live_run()
                run["attestation"][field] = value
                self.assert_rejected(run)

    def test_every_engine_and_switch_capability_must_be_present(self):
        for engine in updater.VERSIONS:
            for key in updater.CAPABILITIES:
                with self.subTest(engine=engine, key=key):
                    run = live_run()
                    del run["engines"][engine]["results"][key]
                    self.assert_rejected(run)
        run = live_run()
        del run["protocolSwitch"]
        self.assert_rejected(run)

    def test_pass_requires_asserted_true_not_a_truthy_value(self):
        for key in updater.ALL_CAPABILITIES:
            for asserted in (None, False, 1):
                with self.subTest(key=key, asserted=asserted):
                    run = live_run()
                    result = (run["protocolSwitch"] if key == "protocolSwitch"
                              else run["engines"]["opencode"]["results"][key])
                    if asserted is None:
                        del result["facts"]["asserted"]
                    else:
                        result["facts"]["asserted"] = asserted
                    self.assert_rejected(run)

    def test_pass_requires_each_content_or_lifecycle_observation(self):
        for key, observations in updater.PASS_FACTS.items():
            for observation in observations:
                for value in (None, False, 1):
                    with self.subTest(key=key, observation=observation, value=value):
                        run = live_run()
                        result = (run["protocolSwitch"] if key == "protocolSwitch"
                                  else run["engines"]["opencode2"]["results"][key])
                        if value is None:
                            del result["facts"][observation]
                        else:
                            result["facts"][observation] = value
                        self.assert_rejected(run)

    def test_only_bounded_numeric_or_boolean_facts_are_accepted(self):
        for fact in ("server-secret-DO-NOT-PRINT", {"token": "secret"}, [], None,
                     float("nan"), float("inf"), -(10**12 + 1), 10**12 + 1):
            with self.subTest(fact_type=type(fact).__name__):
                run = live_run()
                run["engines"]["opencode"]["results"]["models"]["facts"]["count"] = fact
                self.assert_rejected(run)
        run = live_run()
        run["engines"]["opencode"]["results"]["models"]["facts"]["count"] = 42
        self.assertEqual(updater.validate_run(run), run)

    def test_untrusted_extra_fields_and_raw_strings_are_rejected_without_echo(self):
        secret = "DO-NOT-PRINT-provider-secret"
        for container in ("root", "engine", "result", "attestation"):
            with self.subTest(container=container):
                run = live_run()
                target = {
                    "root": run,
                    "engine": run["engines"]["opencode"],
                    "result": run["engines"]["opencode"]["results"]["create"],
                    "attestation": run["attestation"],
                }[container]
                target["Authorization"] = secret
                with self.assertRaises(updater.InvalidEvidence) as caught:
                    updater.validate_run(run)
                self.assertNotIn(secret, str(caught.exception))
        for field in ("observedVersion", "code"):
            run = live_run()
            if field == "observedVersion":
                run["engines"]["opencode"][field] = secret
            else:
                run["engines"]["opencode"]["results"]["version"][field] = secret
            self.assert_rejected(run)
        run = live_run()
        run["engines"]["opencode"]["results"]["models"]["facts"][secret] = True
        self.assert_rejected(run)

    def test_safe_http_failure_is_retained_but_cannot_claim_verified(self):
        for code in ("http_401", "timeout", "scenario_failed"):
            result = {"state": "fail", "code": code, "facts": {}}
            self.assertEqual(updater.validate_result(result, "cards"), result)
        for result in (
            {"state": "fail", "code": "verified", "facts": {}},
            {"state": "fail", "code": "timeout", "facts": {"asserted": True}},
            {"state": "pass", "code": "request_failed", "facts": {"asserted": True}},
        ):
            with self.assertRaises(updater.InvalidEvidence):
                updater.validate_result(result, "version")

    def test_bounded_identifiers_time_and_evidence_path(self):
        for field, value in (("runID", "../secret"), ("runID", "fq3-" + "a" * 97),
                             ("sourceRevision", "abc123"),
                             ("startedAt", "2026-02-30T04:05:06Z"),
                             ("startedAt", "2026-10-08T04:05:06+00:00"),
                             ("evidence", "https://server.invalid/secrets")):
            with self.subTest(field=field):
                run = live_run()
                run[field] = value
                self.assert_rejected(run)


class MatrixFilesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.matrix = existing_matrix()
        self.run = live_run()
        self.matrix_path = self.root / updater.MATRIX_PATH
        self.markdown_path = self.root / updater.MARKDOWN_PATH
        self.run_path = self.root / self.run["evidence"]
        self.matrix_path.parent.mkdir(parents=True)
        self.run_path.parent.mkdir(parents=True)
        self.matrix_path.write_text(json.dumps(self.matrix), encoding="utf-8")
        self.markdown_path.write_text("original markdown\n", encoding="utf-8")
        self.write_run()

    def write_run(self):
        self.run_path.write_text(json.dumps(self.run), encoding="utf-8")

    def assert_files_unchanged(self, action):
        before = (self.matrix_path.read_bytes(), self.markdown_path.read_bytes())
        with self.assertRaises(updater.InvalidEvidence):
            action()
        self.assertEqual((self.matrix_path.read_bytes(), self.markdown_path.read_bytes()), before)

    def test_update_generates_both_files_from_json_and_preserves_old_meanings(self):
        updated = updater.update_paths(self.root, self.run["evidence"])
        self.assertEqual(json.loads(self.matrix_path.read_text(encoding="utf-8")), updated)
        markdown = self.markdown_path.read_text(encoding="utf-8")
        self.assertEqual(markdown, updater.render_markdown(updated))
        for value in (self.matrix["rule"], self.matrix["metadata"],
                      "Pinned download and checksum verified", "Cards answered from chat and list",
                      "docs/qa/old-card.md", "docs/qa/claude.md", "Owner needed"):
            self.assertIn(value, markdown)
        self.assertIn("| OpenCode 2 |  | OC2 server | · | ⛔ |", markdown)
        self.assertIn("| Claude Code | 2.1.283 | Paseo native | ✅ | 🔒 |", markdown)
        self.assertIn(updater.PROTOCOL_SCOPE, markdown)
        self.assertIn("other CPU architectures", markdown)
        self.assertIn("../qa/FQ3-2026-10-08/fq3-test-001.json", markdown)
        for key in updater.ALL_CAPABILITIES:
            self.assertIn(f"- {key}: pass", markdown)

    def test_missing_prerequisite_is_explicit_fail_in_generated_protocol_table(self):
        self.run["engines"]["opencode2"]["results"]["cards"] = {
            "state": "fail", "code": "cards_tool_missing", "facts": {},
        }
        self.write_run()
        updated = updater.update_paths(self.root, self.run_path)
        self.assertEqual(updated["agents"][1]["cells"], self.matrix["agents"][1]["cells"])
        markdown = self.markdown_path.read_text(encoding="utf-8")
        self.assertIn("- cards: fail — `cards_tool_missing`; facts `{}`", markdown)
        self.assertIn("❌", markdown)

    def test_invalid_assertion_cannot_write_either_matrix_file(self):
        del self.run["engines"]["opencode"]["results"]["image"]["facts"]["asserted"]
        self.write_run()
        self.assert_files_unchanged(lambda: updater.update_paths(self.root, self.run_path))

    def test_wrong_evidence_location_or_symlink_is_rejected(self):
        other = self.root / "not-the-evidence.json"
        other.write_bytes(self.run_path.read_bytes())
        self.assert_files_unchanged(lambda: updater.update_paths(self.root, other))
        self.run_path.unlink()
        self.run_path.symlink_to(other)
        self.assert_files_unchanged(lambda: updater.update_paths(self.root, self.run_path))

    def test_oversized_or_duplicate_key_json_is_rejected_before_writes(self):
        self.run_path.write_text(" " * (updater.MAX_RUN_BYTES + 1), encoding="utf-8")
        self.assert_files_unchanged(lambda: updater.update_paths(self.root, self.run_path))
        self.run_path.write_text('{"schemaVersion":1,"schemaVersion":1}', encoding="utf-8")
        self.assert_files_unchanged(lambda: updater.update_paths(self.root, self.run_path))

    def test_missing_or_duplicate_engine_row_cannot_update_existing_matrix(self):
        for agents in (self.matrix["agents"][1:],
                       self.matrix["agents"] + [copy.deepcopy(self.matrix["agents"][0])]):
            with self.subTest(row_count=len(agents)):
                broken = copy.deepcopy(self.matrix)
                broken["agents"] = agents
                self.matrix_path.write_text(json.dumps(broken), encoding="utf-8")
                self.assert_files_unchanged(lambda: updater.update_paths(self.root, self.run_path))

    def test_cli_error_uses_fixed_copy_and_never_echoes_raw_json(self):
        self.run_path.write_text('{"token":"DO-NOT-PRINT-provider-secret",', encoding="utf-8")
        output, errors = io.StringIO(), io.StringIO()
        before = (self.matrix_path.read_bytes(), self.markdown_path.read_bytes())
        with mock.patch.object(updater, "REPO", self.root), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            with self.assertRaises(SystemExit) as caught:
                updater.main(["--run", str(self.run_path)])
        self.assertEqual(caught.exception.code, 1)
        self.assertEqual(output.getvalue(), "")
        self.assertEqual(errors.getvalue(),
                         "FQ3 matrix update refused: invalid evidence or unavailable files.\n")
        self.assertEqual((self.matrix_path.read_bytes(), self.markdown_path.read_bytes()), before)


if __name__ == "__main__":
    unittest.main()
