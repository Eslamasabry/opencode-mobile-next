"""Offline next-candidate upgrade input regressions; no device or APK tools."""

import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import final_pass_protocols as protocols
from tool.qa.fq9 import common, run as driver
from tool.qa.test_final_pass_protocols import FakeContext


class NextUpgradeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.ctx = FakeContext(self.root)
        self.ctx.root = self.root / "checkout"
        self.ctx.root.mkdir()
        self.ctx.candidate_build = 2203
        self.ctx.candidate.write_bytes(b"next approved candidate")
        self.artifact = dict(
            apk=str(self.ctx.candidate),
            build=2203,
            version="1.2.0",
            sha256=common.digest_file(self.ctx.candidate),
            signer=common.LOCAL_SIGNER,
            origin="coordinator-approved",
        )
        self.previous = dict(
            self.artifact,
            apk=str(self.root / "2202.apk"),
            build=2202,
            sha256="b" * 64,
            origin="previous-approved",
        )
        self.manifest = self.root / "artifacts.json"
        self.write_manifest()
        self.config = dict(
            manifest=str(self.manifest),
            seed_history_receipt=str(self.root / "history.json"),
            run_id=self.ctx.run_id,
        )

    def write_manifest(self):
        self.manifest.write_text(
            json.dumps(
                dict(
                    candidate=self.artifact,
                    normal=self.artifact,
                    previous=self.previous,
                )
            )
        )

    def test_default_manifest_pin_remains_and_explicit_next_requires_2202_baseline(
        self,
    ):
        with self.assertRaises(common.DriverFailure):
            common.load_manifest(self.manifest)
        self.assertEqual(
            common.load_manifest(self.manifest, candidate_build=2203)[
                "candidate"
            ].build,
            2203,
        )
        self.previous["build"] = 2201
        self.write_manifest()
        with self.assertRaises(common.DriverFailure):
            common.load_manifest(self.manifest, candidate_build=2203)

    def test_upgrade_seed_inputs_reach_driver_under_inherited_lock(self):
        def call(argv):
            self.assertTrue(self.ctx.adopted)
            self.assertIn("--seed-history-receipt", argv)
            self.assertNotIn("--session-receipt", argv)
            self.assertEqual(argv[argv.index("--candidate-build") + 1], "2203")
            report = dict(
                schema=1,
                case="upgrade",
                runID=self.ctx.run_id,
                candidateBuild=2203,
                candidateSha256=self.artifact["sha256"],
                signerSha256=common.LOCAL_SIGNER,
                device="emulator-5554",
                state="pass",
                automatedChecksPassed=True,
                normalRestored=True,
                manualChecksPending=True,
                deviceQualified=False,
                historySeeded=True,
            )
            (
                self.ctx.output / "fq9-upgrade" / f"{self.ctx.run_id}-upgrade.json"
            ).write_text(json.dumps(report))
            return 0

        with patch.object(driver, "main", side_effect=call) as main:
            result = protocols.run("fq9-upgrade", self.config, self.ctx)
        main.assert_called_once()
        self.assertEqual(result["reason"], "fq9_manual_checks_pending")
        self.assertEqual(self.ctx.events, ["adopt", "capture", "release"])
        self.assertFalse(Path(self.config["seed_history_receipt"]).exists())

    def test_next_build_driver_stamps_actual_candidate_and_keeps_manual_gate(self):
        import argparse
        import types

        artifact = common.Artifact(
            Path(self.artifact["apk"]),
            2203,
            "1.2.0",
            self.artifact["sha256"],
            common.LOCAL_SIGNER,
            "coordinator-approved",
        )
        previous = common.Artifact(
            Path(self.previous["apk"]),
            2202,
            "1.2.0",
            self.previous["sha256"],
            common.LOCAL_SIGNER,
            "previous-approved",
        )
        args = argparse.Namespace(
            case="upgrade",
            serial="emulator-5554",
            run_id="fq9-next",
            dedicated_avd=None,
        )
        device = types.SimpleNamespace(
            locked=False,
            mutated=True,
            device_ready=lambda: None,
            close_protocol=lambda: None,
            restore_normal=lambda normal: self.assertEqual(normal, artifact),
        )
        with (
            patch.object(driver, "LOCK", self.root / "lock"),
            patch.object(
                driver.upgrade,
                "run_upgrade",
                return_value={"state": "pass", "code": "verified"},
            ),
            patch.object(driver, "source_revision", return_value="a" * 40),
        ):
            result = driver.run_locked(
                args,
                dict(candidate=artifact, normal=artifact, previous=previous),
                None,
                self.root / "result.json",
                port_factory=lambda *a, **k: device,
            )
        self.assertEqual(result["candidateBuild"], 2203)
        self.assertTrue(result["automatedChecksPassed"])
        self.assertTrue(result["manualChecksPending"])
        self.assertFalse(result["deviceQualified"])

    def test_nondurable_receipt_stops_before_posting_marker(self):
        import os
        import types
        from tool.qa.fq9 import fixture

        calls = []
        port = types.SimpleNamespace(
            run_id="fq9-test",
            history=None,
            require_idle_setup=lambda: None,
            _connect=lambda engine: None,
            prepare_fixture_project=lambda: "/root/projects/fq9-test",
        )

        def protocol(method, path, **kwargs):
            calls.append(path)
            if path == "/session/status":
                return {}
            return {
                "id": "ses_test",
                "title": "fq9-test-retained",
                "directory": "/root/projects/fq9-test",
            }

        port.protocol = protocol
        with patch.object(os, "fsync", side_effect=OSError("disk failure")):
            with self.assertRaises(common.DriverFailure):
                fixture.seed_history_fixture(
                    port,
                    lambda value: driver.write_private_receipt(
                        self.root / "private.json", value
                    ),
                )
        self.assertNotIn("/session/ses_test/message", calls)
        self.assertIsNone(port.history)

    def test_next_build_does_not_enable_other_rows(self):
        with patch.object(driver, "main") as main:
            result = protocols.run("fq9-background", self.config, self.ctx)
        self.assertEqual(result["reason"], "fq9_requires_build_2202")
        main.assert_not_called()

    def test_seed_collision_or_mixed_receipts_refuse_before_driver(self):
        for mixed in (False, True):
            config = dict(self.config)
            if mixed:
                config["session_receipt"] = str(self.root / "other.json")
            else:
                Path(config["seed_history_receipt"]).touch()
            with patch.object(driver, "main") as main:
                result = protocols.run("fq9-upgrade", config, self.ctx)
            self.assertEqual(result["reason"], "fq9_configuration_invalid")
            main.assert_not_called()
            Path(config["seed_history_receipt"]).unlink(missing_ok=True)

    def test_cli_next_upgrade_plan_is_inert_and_other_cases_refuse_override(self):
        for case, expected in (
            ("upgrade", 0),
            ("background", 1),
            ("fresh", 1),
            ("stable", 1),
        ):
            with (
                self.subTest(case=case),
                patch.object(driver, "run_locked") as run,
                patch.object(driver.ports, "verify_artifact") as verify,
                contextlib.redirect_stdout(io.StringIO()) as output,
            ):
                self.assertEqual(
                    driver.main(["--case", case, "--candidate-build", "2203"]), expected
                )
                value = json.loads(output.getvalue())
                if expected == 0:
                    self.assertEqual(value["requiredCandidateBuild"], 2203)
                    self.assertIn("coordinator_posted_apk_2203", value["requirements"])
                    self.assertFalse(value["deviceTouched"])
                run.assert_not_called()
                verify.assert_not_called()


if __name__ == "__main__":
    unittest.main()
