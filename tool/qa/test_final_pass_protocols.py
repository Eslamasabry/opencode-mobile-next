"""Offline protocol-wrapper tests; no device, APK tools, or real lock calls."""

from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import final_pass_protocols as protocols
from tool.qa.fq9 import common, run as driver


class FakeContext:
    def __init__(self, root):
        self.root = root
        self.output = root / "output"
        self.candidate = root / "candidate.apk"
        self.candidate_build = 2202
        self.run_id = "fq9-offline"
        self.lock_fd = 99
        self.adopted = False
        self.events = []

    @contextmanager
    def adopt_lock(self, module):
        if self.adopted or module is not driver:
            raise AssertionError("unexpected lock adoption")
        self.events.append("adopt")
        self.adopted = True
        try:
            yield
        finally:
            self.adopted = False
            self.events.append("release")

    def capture(self, call):
        if not self.adopted:
            raise AssertionError("driver called outside inherited reservation")
        self.events.append("capture")
        return call()


class ProtocolWrapperTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.context = FakeContext(self.root)
        self.context.candidate.write_bytes(b"offline candidate fixture")
        self.digest = hashlib.sha256(self.context.candidate.read_bytes()).hexdigest()
        self.artifact = dict(
            apk=str(self.context.candidate), build=2202, version="1.2.0",
            sha256=self.digest, signer=common.LOCAL_SIGNER,
            origin="coordinator-approved",
        )
        for name in ("AndroidPorts", "verify_artifact"):
            guard = patch.object(driver.ports, name, side_effect=AssertionError(
                "offline test attempted device or APK tooling"
            ))
            fake = guard.start()
            self.addCleanup(guard.stop)
            self.addCleanup(fake.assert_not_called)

    def config(self, case):
        manifest = self.root / "manifest.json"
        previous = dict(self.artifact, origin="previous-approved")
        manifest.write_text(json.dumps(dict(
            candidate=self.artifact, normal=self.artifact, previous=previous,
        )), encoding="utf-8")
        receipt = self.root / "session.json"
        session = dict(id="ses_offline", title="preserved history")
        if case == "background":
            session.update(title=f"{self.context.run_id}-background", promptID="msg_offline")
        receipt.write_text(json.dumps(dict(
            engine="opencode", directory="/root/projects/offline", sessions=[session],
        )), encoding="utf-8")
        return dict(manifest=str(manifest), session_receipt=str(receipt))

    def write_mock_receipt(self, argv, *, manual=False, background=None, errors=None):
        self.assertTrue(self.context.adopted)
        self.assertIn("--execute", argv)
        value = lambda key: argv[argv.index(key) + 1]
        self.assertEqual(value("--serial"), "emulator-5554")
        case, run_id = value("--case"), value("--run-id")
        output = Path(value("--output")) / f"{run_id}-{case}.json"
        report = dict(
            schema=1, case=case, runID=run_id, candidateBuild=2202,
            candidateSha256=self.digest, signerSha256=common.LOCAL_SIGNER,
            device="emulator-5554", state="pass", automatedChecksPassed=True,
            normalRestored=True, manualChecksPending=manual,
            deviceQualified=not manual,
        )
        if errors:
            report.update(errors)
        if background is not None:
            report["driver"] = background
            terminal = output.with_name(output.stem + "-terminal.json")
            terminal.write_text(json.dumps(dict(
                beforeResumeAndCleanup=True, windowReached=True,
                windowEndReached=True, errors=[], capturedThroughElapsedSeconds=1800,
                terminal=[{}], logs=[{}],
            )), encoding="utf-8")
        output.write_text(json.dumps(report), encoding="utf-8")
        return 0

    def test_fq3_rejects_old_build_before_any_device_call(self):
        self.context.candidate_build = 2197
        result = protocols.run("fq3", {}, self.context)
        self.assertEqual(result['reason'], 'fq3_requires_build_2203')
        self.assertEqual(self.context.events, [])

    def test_unreviewed_next_build_blocks_before_driver_or_output_creation(self):
        self.context.candidate_build = 2203
        with patch.object(driver, "main") as main:
            for row in ("fq9-upgrade", "fq9-background"):
                with self.subTest(row=row):
                    result = protocols.run(row, {}, self.context)
                    self.assertEqual(result["status"], "blocked")
                    self.assertEqual(result["reason"], "fq9_configuration_invalid")
            main.assert_not_called()
        self.assertFalse(self.context.output.exists())
        self.assertEqual(self.context.events, [])

    def test_missing_configuration_blocks_before_driver(self):
        with patch.object(driver, "main") as main:
            result = protocols.run("fq9-upgrade", {}, self.context)
            main.assert_not_called()
        self.assertEqual(result, dict(
            status="blocked", reason="fq9_configuration_invalid", receipts=[],
        ))
        self.assertFalse(self.context.output.exists())
        self.assertEqual(self.context.events, [])

    def test_successful_automation_with_manual_checks_pending_never_passes(self):
        with patch.object(driver, "main", side_effect=lambda argv:
                          self.write_mock_receipt(argv, manual=True)) as main:
            result = protocols.run("fq9-upgrade", self.config("upgrade"), self.context)
            main.assert_called_once()
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(result["reason"], "fq9_manual_checks_pending")
        self.assertIs(result["data"]["automatedChecksPassed"], True)
        self.assertIs(result["data"]["manualChecksPending"], True)
        self.assertIs(result["data"]["deviceQualified"], False)
        self.assertEqual(len(result["receipts"]), 1)
        self.assertTrue(all(Path(p).is_file() for p in result["receipts"]))
        self.assertEqual(self.context.events, ["adopt", "capture", "release"])
        self.assertFalse(self.context.adopted)

    def test_upgrade_distinct_reviewed_baseline_reaches_driver(self):
        config = self.config("upgrade")
        manifest = Path(config["manifest"])
        value = json.loads(manifest.read_text())
        value["previous"]["build"] = 2201
        manifest.write_text(json.dumps(value))
        with patch.object(driver, "main", side_effect=lambda argv:
                          self.write_mock_receipt(argv, manual=True)) as main:
            result = protocols.run("fq9-upgrade", config, self.context)
        main.assert_called_once()
        self.assertEqual(result["reason"], "fq9_manual_checks_pending")

    def test_background_missing_checkpoints_fails_despite_success_flags(self):
        with patch.object(driver, "main", side_effect=lambda argv:
                          self.write_mock_receipt(argv, background={})) as main:
            result = protocols.run("fq9-background", self.config("background"), self.context)
            main.assert_called_once()
        self.assertEqual(result["status"], "fail")
        self.assertEqual(result["reason"], "fq9_background_proof_incomplete")
        self.assertEqual(len(result["receipts"]), 2)
        self.assertTrue(all(Path(p).is_file() for p in result["receipts"]))
        self.assertEqual(self.context.events, ["adopt", "capture", "release"])
        self.assertFalse(self.context.adopted)

    def test_failed_cleanup_or_retained_session_stops_later_device_rows(self):
        for index, failure in enumerate((
            {"driver": {"sessionRetained": True}},
            {"driver": {"cleanupSucceeded": False}},
            {"protocolCleanupError": "driver_failure"},
            {"restoreError": "normal_restore_failed"},
        )):
            with self.subTest(failure=failure):
                self.context.output = self.root / f"cleanup-{index}"
                with patch.object(driver, "main", side_effect=lambda argv:
                                  self.write_mock_receipt(argv, manual=True, errors=failure)):
                    result = protocols.run("fq9-upgrade", self.config("upgrade"), self.context)
                self.assertEqual(result["status"], "fail")
                self.assertEqual(result["reason"], "fq9_cleanup_incomplete")
                self.assertIs(result["data"]["safe_to_continue"], False)
                self.assertTrue(all(Path(p).is_file() for p in result["receipts"]))


if __name__ == "__main__":
    unittest.main()
