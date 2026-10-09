"""Offline saved-report proof guards; no device, provider or native process."""

import argparse
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tool.qa import bd7_device_saved_report as proof
from tool.qa.fq9.common import Artifact, DriverFailure, LOCAL_SIGNER


class SavedReportTest(unittest.TestCase):
    def artifact(self):
        return Artifact(
            Path("/approved/2199.apk"),
            2199,
            "1.2.0",
            "a" * 64,
            LOCAL_SIGNER,
            "coordinator-approved",
        )

    def fixture(self, fail_at=None):
        output = Path(self.addCleanupDirectory())
        device, session = Mock(), Mock()
        device.locked = session.locked = True
        device.installed_identity.return_value = dict(
            build=2199, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER, uid=10217
        )
        session.require_clean_baseline.return_value = {
            "consent_enabled": False,
            "saved_count": 0,
        }
        session.enable_consent.return_value = (0, 123)
        session.crash.return_value = {"visible_report": True}
        session.share_preview.return_value = {
            "visible": True,
            "external_share_opened": False,
        }
        session.consent_owned = False
        order = []
        for name in (
            "launch",
            "require_clean_baseline",
            "enable_consent",
            "crash",
            "share_preview",
            "cleanup_owned_reports",
        ):
            result = getattr(session, name).return_value

            def action(*_, name=name, result=result):
                order.append(name)
                if name == "enable_consent":
                    session.consent_owned = True
                if name == fail_at:
                    raise proof.DeviceFailure("saved_crash_ring_unavailable")
                return result

            getattr(session, name).side_effect = action

        def restore(*_, **__):
            self.assertTrue(session.locked)
            self.assertTrue(device.locked)
            order.append("normal_restore")

        device.adb.side_effect = restore
        with patch.object(proof, "verify_artifact"):
            receipt = proof.run_locked(
                self.artifact(), output, device=device, session=session
            )
        return receipt, order, session, device

    def addCleanupDirectory(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        return temp.name

    def test_crash_preview_delete_off_and_restore_under_owned_lock(self):
        receipt, order, session, _ = self.fixture()
        self.assertEqual(receipt["result"], "PASS")
        self.assertLess(order.index("crash"), order.index("share_preview"))
        self.assertLess(
            order.index("share_preview"), order.index("cleanup_owned_reports")
        )
        self.assertLess(
            order.index("cleanup_owned_reports"), order.index("normal_restore")
        )
        session.anr.assert_not_called()
        session.backup_diagnostics.assert_not_called()
        session.restore_diagnostics.assert_not_called()

    def test_crash_failure_still_cleans_owned_consent_and_restores(self):
        receipt, order, session, _ = self.fixture("crash")
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["failure_code"], "saved_crash_ring_unavailable")
        self.assertIn("cleanup_owned_reports", order)
        self.assertEqual(order[-1], "normal_restore")
        session.share_preview.assert_not_called()

    def test_unowned_baseline_never_enables_or_deletes(self):
        receipt, _, session, _ = self.fixture("require_clean_baseline")
        self.assertEqual(receipt["result"], "FAIL")
        session.enable_consent.assert_not_called()
        session.cleanup_owned_reports.assert_not_called()

    def test_run_locked_refuses_missing_lock_before_any_device_action(self):
        device = Mock(locked=False)
        with self.assertRaisesRegex(DriverFailure, "^device_unavailable$"):
            proof.run_locked(self.artifact(), Path("."), device=device)
        device.device_ready.assert_not_called()

    def test_reused_pid_starttime_never_receives_am_crash(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        with (
            patch.object(session, "identity", return_value=(1234, 88)),
            patch.object(session, "still_owned", return_value=False),
            patch.object(session, "execute", return_value=b"1001") as command,
        ):
            with self.assertRaisesRegex(
                proof.DeviceFailure, "^crash_identity_changed$"
            ):
                session.crash()
        self.assertFalse(
            any("crash" in call.args[0] for call in command.call_args_list)
        )

    def test_existing_records_or_enabled_consent_are_preserved(self):
        session = proof.SavedReportSession("adb", Path("."))
        for consent, records in ((1, []), (0, [{"source": "native"}])):
            with (
                patch.object(session, "consent", return_value=consent),
                patch.object(session, "ring", return_value=records),
                patch.object(session, "execute") as command,
            ):
                with self.assertRaisesRegex(
                    proof.DeviceFailure, "^diagnostic_baseline_not_empty$"
                ):
                    session.require_clean_baseline()
            command.assert_not_called()

    def test_baseline_allows_incidental_nonerror_rows_but_preserves_errors(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.exists.side_effect = lambda path: path.endswith(
            "report_problem.json"
        )
        for kinds, accepted in (
            (["timing", "androidExit", "thermal"], True),
            (["error"], False),
            (["unknown"], False),
        ):
            entries = [
                {
                    "kind": kind,
                    "time": "2026-10-09T12:30:00.000Z",
                    "source": "app",
                    "message": "synthetic-private-value",
                }
                for kind in kinds
            ]
            session.ports.private_bytes.return_value = proof.json.dumps(
                {"version": 1, "entries": entries}
            ).encode()
            with (
                patch.object(session, "consent", return_value=0),
                patch.object(session, "ring", return_value=[]),
            ):
                if accepted:
                    session.require_clean_baseline()
                else:
                    with self.assertRaises(proof.DeviceFailure) as failed:
                        session.require_clean_baseline()
                    self.assertNotIn("synthetic-private-value", str(failed.exception))

    def test_failed_named_delete_still_turns_owned_switch_off(self):
        session = proof.SavedReportSession("adb", Path("."))
        session.consent_owned = True
        session.ui = Mock()
        session.ui.find.return_value = None
        session.ui.tap.side_effect = lambda label, **_: (
            (_ for _ in ()).throw(proof.Bd7UiFailure("navigation_target_unavailable"))
            if label == "Delete 1 saved crash report"
            else None
        )
        with (
            patch.object(session, "launch"),
            patch.object(session, "execute"),
            patch.object(session, "ring", side_effect=[[{}], []]),
            patch.object(session, "consent", side_effect=[1, 0]),
        ):
            with self.assertRaisesRegex(
                proof.Bd7UiFailure, "^navigation_target_unavailable$"
            ):
                session.cleanup_owned_reports()
        session.ui.tap.assert_any_call(
            "Save crash reports on this phone", contains=True
        )
        self.assertFalse(session.consent_owned)

    def test_new_installer_ticket_refuses_crash_before_reading_or_signalling_pid(self):
        ports = Mock()
        ports.require_idle_setup.side_effect = DriverFailure("setup_active_or_unknown")
        session = proof.SavedReportSession("adb", Path("."), ports=ports)
        with (
            patch.object(session, "identity") as identity,
            patch.object(session, "execute") as command,
        ):
            with self.assertRaisesRegex(DriverFailure, "^setup_active_or_unknown$"):
                session.crash()
        ports.require_idle_setup.assert_called_once()
        identity.assert_not_called()
        command.assert_not_called()

    def test_unhashable_hostile_failure_code_is_fixed(self):
        error = Exception("synthetic-private-message")
        error.code = ["synthetic-private-value"]
        self.assertEqual(proof.failure_code(error), "device_failure")

    def test_outer_lock_caller_rejects_wrong_build_or_signer_before_device(self):
        for build, signer in ((2198, LOCAL_SIGNER), (2199, "b" * 64)):
            artifact = Artifact(
                Path("/approved/candidate.apk"),
                build,
                "1.2.0",
                "a" * 64,
                signer,
                "coordinator-approved",
            )
            device = Mock(locked=True)
            with self.assertRaisesRegex(DriverFailure, "^candidate_identity_mismatch$"):
                proof.run_locked(
                    artifact, Path(self.addCleanupDirectory()), device=device
                )
            device.device_ready.assert_not_called()
            device.installed_identity.assert_not_called()

    def test_lock_wait_preserves_the_parent_descriptor_for_one_hour(self):
        output = Path(self.addCleanupDirectory())
        lock_path = output / "device.lock"
        with (
            patch.object(proof, "LOCK", lock_path),
            patch.object(proof.subprocess, "run") as flock,
            patch.object(proof, "run_locked", return_value={"result": "PASS"}),
        ):
            proof.locked_run(self.artifact(), output)
        args = flock.call_args
        self.assertEqual(args.args[0][:3], ["flock", "-w", "3600"])
        self.assertEqual(args.kwargs["timeout"], 3605)
        self.assertEqual(args.kwargs["pass_fds"], (int(args.args[0][3]),))

    def test_bad_artifact_host_preflight_never_acquires_lock(self):
        output = Path(self.addCleanupDirectory())
        args = argparse.Namespace(apk=Path("/missing/2199.apk"), output=output)
        with (
            patch.object(
                proof,
                "load_artifact",
                side_effect=DriverFailure("artifact_hash_mismatch"),
            ),
            patch.object(proof, "locked_run") as device,
        ):
            self.assertEqual(proof.run(args), 1)
        device.assert_not_called()


if __name__ == "__main__":
    unittest.main()
