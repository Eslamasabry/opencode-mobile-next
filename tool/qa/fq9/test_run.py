import argparse
import contextlib
import fcntl
import io
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

from . import run
from .common import Artifact, LOCAL_SIGNER, STABLE_SIGNER


class RunTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.lock = self.root / "emulator.lock"
        self.artifact = Artifact(
            Path("/unused/candidate.apk"),
            2197,
            "1.2.0",
            "a" * 64,
            LOCAL_SIGNER,
            "coordinator-approved",
        )
        self.args = argparse.Namespace(
            case="upgrade",
            serial="emulator-5554",
            run_id="fq9-fixture",
            dedicated_avd=None,
        )
        self.artifacts = dict(
            candidate=self.artifact,
            normal=self.artifact,
            previous=Artifact(
                Path("/unused/previous.apk"),
                2196,
                "1.2.0",
                "b" * 64,
                LOCAL_SIGNER,
                "previous-approved",
            ),
        )

    def test_default_plan_never_verifies_apk_acquires_lock_or_loads_device(self):
        for case in ["upgrade", "stable", "background", "fresh"]:
            with (
                self.subTest(case=case),
                patch.object(
                    run.ports,
                    "verify_artifact",
                    side_effect=AssertionError("APK invoked"),
                ),
                patch.object(
                    run, "run_locked", side_effect=AssertionError("lock/device invoked")
                ),
                contextlib.redirect_stdout(io.StringIO()) as output,
            ):
                self.assertEqual(run.main(["--case", case]), 0)
                plan = json.loads(output.getvalue())
                self.assertFalse(plan["deviceTouched"])
                if case == "fresh":
                    self.assertTrue(plan["freshPlan"]["requiresDedicatedAvd"])
                if case == "background":
                    self.assertEqual(plan["checkpointsSeconds"], [300, 1800])

    def test_fresh_shared_serial_refused_before_host_tools(self):
        with (
            patch.object(run, "load_manifest", return_value=self.artifacts),
            patch.object(
                run.ports, "verify_artifact", side_effect=AssertionError("APK invoked")
            ),
            contextlib.redirect_stdout(io.StringIO()) as output,
        ):
            self.assertEqual(
                run.main(["--case", "fresh", "--execute", "--manifest", "/unused"]), 1
            )
        self.assertEqual(
            json.loads(output.getvalue())["code"], "dedicated_avd_required"
        )

    def test_stable_signer_mismatch_refused_before_any_tools_or_device(self):
        self.artifacts["stable"] = Artifact(
            Path("/unused/stable.apk"),
            52,
            "1.2.0",
            "c" * 64,
            STABLE_SIGNER,
            "published-stable",
        )
        with (
            patch.object(run, "load_manifest", return_value=self.artifacts),
            patch.object(run, "load_session_receipt", return_value={}),
            patch.object(
                run.ports, "verify_artifact", side_effect=AssertionError("APK invoked")
            ),
            patch.object(
                run, "run_locked", side_effect=AssertionError("device invoked")
            ),
            contextlib.redirect_stdout(io.StringIO()) as output,
        ):
            self.assertEqual(
                run.main(
                    [
                        "--case",
                        "stable",
                        "--execute",
                        "--manifest",
                        "/unused",
                        "--session-receipt",
                        "/unused",
                    ]
                ),
                1,
            )
        self.assertEqual(
            json.loads(output.getvalue())["code"], "upgrade_signer_mismatch"
        )

    def fake(self, *, restore=False, cleanup=False):
        calls = []

        def locked_call(name):
            def invoke(*args):
                self.assertTrue(device.locked)
                with self.lock.open("a") as handle:
                    with self.assertRaises(BlockingIOError):
                        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                calls.append(name)
                if (name == "restore" and restore) or (name == "close" and cleanup):
                    raise RuntimeError("sensitive payload")

            return invoke

        device = types.SimpleNamespace(
            locked=False,
            mutated=True,
            device_ready=locked_call("ready"),
            close_protocol=locked_call("close"),
            restore_normal=locked_call("restore"),
        )
        return device, calls

    def locked(self, device, *, driver=None):
        output = self.root / "result.json"
        with (
            patch.object(run, "LOCK", self.lock),
            patch.object(
                run.upgrade,
                "run_upgrade",
                return_value=driver or dict(state="pass", code="verified"),
            ),
            patch.object(run, "source_revision", return_value="c" * 40),
        ):
            result = run.run_locked(
                self.args,
                self.artifacts,
                None,
                output,
                port_factory=lambda *a, **kw: device,
            )
        self.assertEqual(json.loads(output.read_text()), result)
        with self.lock.open("a") as handle:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return result

    def test_seed_receipt_uses_exact_baseline_and_is_saved_privately_under_lock(self):
        device, calls = self.fake()
        self.args.seed_history_receipt = self.root / "private-receipt.json"
        previous = self.artifacts["previous"]
        device.installed_identity = lambda: dict(
            build=previous.build,
            version=previous.version,
            sha256=previous.sha256,
            signer=previous.signer,
        )
        receipt = dict(
            engine="opencode",
            directory="/root/projects/fq9-fixture",
            sessions=[dict(id="ses_1", title="fq9-fixture-retained")],
        )

        def seed(port, save):
            self.assertTrue(port.locked)
            calls.append("seed")
            save(receipt)
            port.history = receipt

        with patch.object(run.fixture, "seed_history_fixture", side_effect=seed):
            result = self.locked(device)
        self.assertTrue(result["historySeeded"])
        self.assertTrue(result["fixtureUsesAppProjectBacking"])
        self.assertEqual(
            json.loads(self.args.seed_history_receipt.read_text()), receipt
        )
        self.assertEqual(self.args.seed_history_receipt.stat().st_mode & 0o777, 0o600)
        self.assertEqual(calls, ["ready", "seed", "close", "restore"])

    def test_seed_never_manufactures_a_previous_baseline(self):
        device, calls = self.fake()
        self.args.seed_history_receipt = self.root / "private-receipt.json"
        device.installed_identity = lambda: dict(
            build=2197, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER
        )
        with patch.object(
            run.fixture,
            "seed_history_fixture",
            side_effect=AssertionError("seeded modern baseline"),
        ):
            result = self.locked(device)
        self.assertEqual(result["code"], "upgrade_previous_mismatch")
        self.assertFalse(self.args.seed_history_receipt.exists())

    def test_invalid_seed_arguments_refuse_before_device_or_apk_tools(self):
        variants = [
            ["--case", "background"],
            ["--case", "upgrade", "--session-receipt", "/unused"],
        ]
        for variant in variants:
            with (
                patch.object(
                    run.ports, "verify_artifact", side_effect=AssertionError("APK tool")
                ),
                patch.object(run, "run_locked", side_effect=AssertionError("device")),
                contextlib.redirect_stdout(io.StringIO()) as output,
            ):
                self.assertEqual(
                    run.main(
                        variant
                        + [
                            "--execute",
                            "--seed-history-receipt",
                            str(self.root / "receipt.json"),
                        ]
                    ),
                    1,
                )
            self.assertEqual(
                json.loads(output.getvalue())["code"], "fixture_seed_arguments_invalid"
            )

    def test_success_restores_normal_and_writes_evidence_inside_lock(self):
        device, calls = self.fake()
        result = self.locked(device)
        self.assertTrue(result["automatedChecksPassed"])
        self.assertFalse(result["deviceQualified"])
        self.assertTrue(result["manualChecksPending"])
        self.assertTrue(result["normalRestored"])
        self.assertEqual(calls, ["ready", "close", "restore"])
        self.assertFalse(device.locked)

    def test_restore_failure_invalidates_pass_and_sanitizes_detail(self):
        device, calls = self.fake(restore=True)
        result = self.locked(device)
        self.assertFalse(result["deviceQualified"])
        self.assertEqual(result["restoreError"], "normal_restore_failed")
        self.assertNotIn("sensitive", repr(result))
        self.assertEqual(calls[-1], "restore")

    def test_forward_cleanup_failure_invalidates_pass_but_still_restores(self):
        device, calls = self.fake(cleanup=True)
        result = self.locked(device)
        self.assertFalse(result["deviceQualified"])
        self.assertEqual(result["protocolCleanupError"], "driver_unexpected_failure")
        self.assertTrue(result["normalRestored"])
        self.assertEqual(calls[-1], "restore")

    def test_returned_background_failure_does_not_become_success(self):
        device, calls = self.fake()
        self.args.case = "background"
        device.installed_identity = lambda: dict(
            build=2197, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER, uid=10217
        )
        with patch.object(
            run.background,
            "run_background",
            return_value=dict(state="fail", code="background_turn_finished_early"),
        ):
            result = self.locked(device)
        self.assertFalse(result["deviceQualified"])
        self.assertEqual(result["driver"]["code"], "background_turn_finished_early")

    def test_preflight_refusal_never_restores_unmutated_device(self):
        device, calls = self.fake()
        device.mutated = False
        device.device_ready = lambda: (_ for _ in ()).throw(
            RuntimeError("secret device error")
        )
        result = self.locked(device)
        self.assertFalse(result["deviceQualified"])
        self.assertFalse(result["normalRestored"])
        self.assertNotIn("restore", calls)
        self.assertNotIn("secret", repr(result))

    def test_existing_evidence_not_overwritten(self):
        output = self.root / "existing.json"
        output.write_text("original")
        with self.assertRaisesRegex(run.DriverFailure, "evidence_already_exists"):
            run.write_report(output, {"state": "pass"})
        self.assertEqual(output.read_text(), "original")

    def test_unknown_failure_tokens_cannot_reach_reports(self):
        self.assertEqual(
            run.failure_code(run.DriverFailure("private_provider_credential")),
            "driver_unexpected_failure",
        )
        self.assertEqual(
            run.failure_code(ValueError("sensitive")), "driver_unexpected_failure"
        )

    def test_discovery_uses_same_lock_and_is_never_survival_qualification(self):
        device, calls = self.fake()
        device.mutated = False
        self.args.case = "background"
        self.args.discover_turn_receipt = self.root / "receipt.json"
        self.args.directory = "/root/projects/fq9-fixture"
        value = {
            "engine": "opencode",
            "directory": self.args.directory,
            "sessions": [
                {"id": "ses_1", "title": "fq9-fixture-background", "promptID": "msg_1"}
            ],
        }
        device.installed_identity = lambda: dict(
            build=2197, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER, uid=10217
        )

        def find(directory):
            self.assertTrue(device.locked)
            self.assertEqual(directory, self.args.directory)
            return value

        device.find_live_receipt = find
        result = self.locked(device)
        self.assertTrue(result["readinessOnly"])
        self.assertEqual(result["state"], "ready")
        self.assertFalse(result["deviceQualified"])
        self.assertFalse(result["automatedChecksPassed"])
        self.assertEqual(json.loads(self.args.discover_turn_receipt.read_text()), value)
        self.assertNotIn("restore", calls)

    def test_discovery_plan_requires_scope_and_never_touches_device(self):
        with (
            patch.object(
                run, "run_locked", side_effect=AssertionError("device called")
            ),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            self.assertEqual(
                run.main(
                    [
                        "--case",
                        "background",
                        "--discover-turn-receipt",
                        "/tmp/receipt",
                        "--directory",
                        "/root/projects/fq9-fixture",
                    ]
                ),
                0,
            )
            self.assertEqual(
                run.main(
                    [
                        "--case",
                        "background",
                        "--discover-turn-receipt",
                        "/tmp/receipt",
                        "--directory",
                        "/root/projects/../private",
                    ]
                ),
                1,
            )


if __name__ == "__main__":
    unittest.main()
