"""Offline saved-report proof guards; no device, provider or native process."""

import argparse
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET
from tool.qa.test_bd7_device_ui import UiFixture, node, xml
from tool.qa.bd7_device_ui import Bd7Ui

from tool.qa import bd7_device_saved_report as proof
from tool.qa.fq9.common import Artifact, DriverFailure, LOCAL_SIGNER


class SavedReportTest(unittest.TestCase):
    def artifact(self):
        return Artifact(
            Path("/approved/2202.apk"),
            2202,
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
            build=2202, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER, uid=10217
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

    def test_saved_report_proof_does_not_require_recent_exit_ui(self):
        session = proof.SavedReportSession(
            "adb", Path(self.addCleanupDirectory()), ports=Mock()
        )
        session.ui = Mock()
        session.ui.nodes.return_value = []
        session.ui.scroll_find.side_effect = lambda label: (
            None
            if label == "The app closed unexpectedly"
            else (_ for _ in ()).throw(
                proof.Bd7UiFailure("navigation_target_unavailable")
            )
        )
        os_exit = {"reason": 4, "pid": 42}
        record = {
            "source": "native",
            "category": "Native application error",
            "time": 100,
        }
        with (
            patch.object(session, "execute", return_value=b"[]"),
            patch.object(session, "root", return_value=b"1"),
            patch(
                "tool.qa.bd7_device_crash_smoke.parse_exit_history",
                return_value=os_exit,
            ),
            patch(
                "tool.qa.bd7_device_crash_smoke.parse_crash_ring", return_value=record
            ),
        ):
            result = session.proof(
                (42, 1), 4, "native", 99, "The app closed unexpectedly"
            )
        self.assertTrue(result["visible_report"])
        self.assertEqual(result["os_exit"], os_exit)
        session.ui.details_number.assert_not_called()
        self.assertEqual(session.ui.scroll_find.call_count, 1)

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

    def test_existing_app_agents_page_returns_before_settings_navigation(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ui = Mock()
        returned = [False]
        node = ET.Element("node", {"package": proof.PACKAGE, "enabled": "true"})

        def find(label):
            if label == "Settings":
                return node if returned[0] else None
            return node if label in ("Agents", "Back") else None

        def execute(command, **_):
            if command == ["shell", "input", "keyevent", "4"]:
                returned[0] = True

        session.ui.find.side_effect = find
        with (
            patch.object(session, "execute", side_effect=execute) as command,
            patch.object(proof.time, "monotonic", side_effect=[0, 1, 16]),
            patch.object(proof.time, "sleep"),
        ):
            session.launch()
        self.assertTrue(returned[0])
        session.ui.navigate_report.assert_called_once()
        self.assertGreater(
            command.call_args_list.index(
                unittest.mock.call(["shell", "input", "keyevent", "4"])
            ),
            next(i for i, c in enumerate(command.call_args_list) if "am" in c.args[0]),
        )

    def test_foreign_agents_label_never_receives_back_navigation(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ui = Mock()
        session.ui.find.side_effect = lambda label: (
            None
            if label == "Settings"
            else ET.Element("node", {"package": "other.app"})
        )
        with patch.object(session, "execute") as command:
            session.recover_navigation()
        command.assert_not_called()
        session.ui.tap.assert_not_called()
        session.ports.require_idle_setup.assert_not_called()

    def test_stopped_builtin_server_uses_product_start_before_navigation(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ui = Mock()
        node = ET.Element("node", {"package": proof.PACKAGE, "enabled": "true"})
        session.ui.find.side_effect = lambda label: (
            node
            if label in ("OpenCode inside the app is stopped", "Start and connect")
            else None
        )
        session.recover_navigation()
        session.recover_navigation()  # A stalled restart must never redispatch.
        session.ports.require_idle_setup.assert_called_once()
        session.ui.tap.assert_called_once_with("Start and connect")

    def test_active_setup_refuses_product_restart_without_tapping(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ports.require_idle_setup.side_effect = DriverFailure(
            "setup_active_or_unknown"
        )
        session.ui = Mock()
        node = ET.Element("node", {"package": proof.PACKAGE, "enabled": "true"})
        session.ui.find.side_effect = lambda label: (
            node
            if label in ("OpenCode inside the app is stopped", "Start and connect")
            else None
        )
        with self.assertRaisesRegex(DriverFailure, "^setup_active_or_unknown$"):
            session.recover_navigation()
        session.ui.tap.assert_not_called()

    def test_existing_report_page_does_not_wait_for_absent_settings_tab(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ui = Mock()
        owned = ET.Element("node", {"package": proof.PACKAGE, "enabled": "true"})
        session.ui.find.side_effect = lambda label: (
            owned
            if label in ("Report a problem", "Save crash reports on this phone", "Back")
            else None
        )
        with patch.object(
            proof.DeviceSession, "navigate_current_page", return_value=False
        ):
            self.assertTrue(session.navigate_current_page())
        session.ui.tap.assert_not_called()

    def test_authored_sheet_actions_are_safe_without_allowing_private_suffixes(self):
        for word in ("Dismiss", "Hide details"):
            for private, accepted in ((False, True), (True, False)):
                document = xml(
                    node("The app closed unexpectedly", "[0,400][800,450]"),
                    node(
                        word + (" synthetic-private-value" if private else ""),
                        "[0,500][800,550]",
                    ),
                )
                ui = Bd7Ui(UiFixture(document).execute)
                with tempfile.TemporaryDirectory() as directory:
                    target = Path(directory) / "preview.jpg"
                    if accepted:
                        ui.screenshot(target, section="preview")
                        self.assertTrue(target.exists())
                    else:
                        with self.assertRaises(proof.Bd7UiFailure):
                            ui.screenshot(target, section="preview")
                        self.assertFalse(target.exists())

    def test_cold_stopped_server_recovers_after_activity_launch(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ui = Mock()
        state = {"visible": False, "connected": False}
        owned = ET.Element("node", {"package": proof.PACKAGE, "enabled": "true"})
        session.ports.app_visible.side_effect = lambda: state["visible"]

        def find(label):
            if not state["visible"]:
                return None
            if label == "Settings":
                return owned if state["connected"] else None
            return (
                owned
                if not state["connected"]
                and label in ("OpenCode inside the app is stopped", "Start and connect")
                else None
            )

        def execute(command, **_):
            if "am" in command:
                state["visible"] = True

        session.ui.find.side_effect = find
        session.ui.tap.side_effect = lambda label: state.update(connected=True)
        with (
            patch.object(session, "execute", side_effect=execute),
            patch.object(proof.time, "monotonic", side_effect=[0, 1, 16]),
            patch.object(proof.time, "sleep"),
        ):
            session.launch()
        session.ui.tap.assert_called_once_with("Start and connect")
        session.ui.navigate_report.assert_called_once()

    def test_inline_recent_exit_stays_on_report_page_before_share(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ui = Mock()
        session.ui.find.return_value = ET.Element("node", {"package": proof.PACKAGE})
        events = []
        session.ui.tap.side_effect = lambda *a, **kw: events.append("open_share")
        session.ui.screenshot.side_effect = lambda *a, **kw: events.append("screenshot")
        with patch.object(
            session, "execute", side_effect=lambda *a, **kw: events.append("back")
        ):
            session.share_preview()
        self.assertEqual(events, ["open_share", "screenshot", "back"])

    def test_report_page_is_reused_even_when_settings_tab_is_visible(self):
        session = proof.SavedReportSession("adb", Path("."), ports=Mock())
        session.ports.app_visible.return_value = True
        session.ui = Mock()
        session.ui.find.return_value = ET.Element("node", {"package": proof.PACKAGE})
        with patch.object(
            proof.DeviceSession, "navigate_current_page", return_value=True
        ) as root_navigation:
            self.assertTrue(session.navigate_current_page())
        root_navigation.assert_not_called()

    def test_report_entry_is_found_above_restored_bottom_scroll_position(self):
        initial = xml(node("Settings", "[10,1000][300,1190]"))
        bottom = xml(node("About", "[10,500][790,600]"))
        above = xml(node("Report a problem", "[10,500][790,600]"))
        consent = xml(node("Save crash reports on this phone", "[10,500][790,600]"))
        fixture = UiFixture(initial)

        def execute(command, **kwargs):
            value = fixture.execute(command, **kwargs)
            if command[:3] == ["shell", "input", "tap"]:
                fixture.document = bottom if fixture.document == initial else consent
            elif command[:3] == ["shell", "input", "swipe"] and int(command[4]) < int(
                command[6]
            ):
                fixture.document = above
            return value

        ui = Bd7Ui(execute)
        ui.navigate_report()
        self.assertIsNotNone(ui.find("Save crash reports on this phone"))

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

    def test_switch_off_erases_report_before_cleanup_is_accepted(self):
        session = proof.SavedReportSession("adb", Path(self.addCleanupDirectory()))
        session.consent_owned = True
        session.ui = Mock()
        session.ui.find.return_value = None
        state = {"enabled": True}

        def tap(label, **_):
            self.assertEqual(label, "Save crash reports on this phone")
            state["enabled"] = False

        session.ui.tap.side_effect = tap
        with (
            patch.object(session, "launch"),
            patch.object(session, "execute"),
            patch.object(
                session, "ring", side_effect=lambda: [{}] if state["enabled"] else []
            ),
            patch.object(session, "consent", side_effect=lambda: int(state["enabled"])),
        ):
            session.cleanup_owned_reports()
        self.assertFalse(session.consent_owned)
        session.ui.tap.assert_called_once_with(
            "Save crash reports on this phone", contains=True
        )
        self.assertTrue((session.output / "cleanup.json").is_file())

    def test_crash_checkpoint_survives_reopen_failure_and_refuses_second_trigger(self):
        session = proof.SavedReportSession(
            "adb", Path(self.addCleanupDirectory()), ports=Mock()
        )
        session.ui = Mock()
        session.ui.find.return_value = None
        with (
            patch.object(session, "identity", return_value=(1234, 88)),
            patch.object(session, "still_owned", return_value=True),
            patch.object(session, "execute", return_value=b"1001") as command,
            patch.object(session, "died"),
            patch.object(
                session,
                "launch",
                side_effect=proof.DeviceFailure("app_navigation_not_ready"),
            ),
        ):
            with self.assertRaisesRegex(
                proof.DeviceFailure, "app_navigation_not_ready"
            ):
                session.crash()
            checkpoint = proof.json.loads(
                (session.output / "crash-trigger.json").read_text()
            )
            self.assertEqual(
                checkpoint,
                {
                    "pid": 1234,
                    "start_ticks": 88,
                    "after_millis": 1001,
                    "command_returned": True,
                },
            )
            with self.assertRaisesRegex(proof.DeviceFailure, "crash_already_attempted"):
                session.crash()
        calls = [c for c in command.call_args_list if "crash" in c.args[0]]
        self.assertEqual(len(calls), 1)

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
        for build, signer in ((2198, LOCAL_SIGNER), (2202, "b" * 64)):
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
        args = argparse.Namespace(apk=Path("/missing/2202.apk"), output=output)
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
