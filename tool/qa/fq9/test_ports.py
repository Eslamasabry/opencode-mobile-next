import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from .common import Artifact, DriverFailure, LOCAL_SIGNER, PACKAGE
from .observations import (
    ongoing_notification,
    process_start,
    profile_projection,
    service_foreground,
    socket_identity,
)
from .ports import AndroidPorts, BASE, FILES, verify_artifact
from .runtime import _NoRedirect


class ObservationTests(unittest.TestCase):
    def test_foreground_service_flag_cannot_leak_from_foreign_block(self):
        text = f"  * ServiceRecord{{a u0 {PACKAGE}/.BackgroundConnectionService}}\n    isForeground=false\n  * ServiceRecord{{b u0 foreign/.Service}}\n    isForeground=true\n"
        self.assertFalse(
            service_foreground(text, PACKAGE + "/.BackgroundConnectionService")
        )
        self.assertTrue(
            service_foreground(
                text.replace("isForeground=false", "isForeground=true"),
                PACKAGE + "/.BackgroundConnectionService",
            )
        )
        self.assertFalse(service_foreground(text, "foreign/.Missing"))

    def test_notification_requires_exact_package_id_channel_and_ongoing_flag(self):
        good = f"  NotificationRecord(a: pkg={PACKAGE} id=4747)\n    channel=opencode_live_connection flags=0x62\n"
        self.assertTrue(ongoing_notification(good))
        for text in [
            good.replace("4747", "4748"),
            good.replace(PACKAGE, "foreign"),
            good.replace("0x62", "0x60"),
            good.replace("opencode_live_connection", "foreign"),
        ]:
            self.assertFalse(ongoing_notification(text))
        mixed = (
            good.replace("0x62", "0x60")
            + "  NotificationRecord(b: pkg=foreign id=4747)\n channel=opencode_live_connection flags=0x62\n"
        )
        self.assertFalse(ongoing_notification(mixed))

    def test_socket_requires_single_loopback_listener_owned_by_app_uid(self):
        line = "  0: 0100007F:1001 00000000:0000 0A 0:0 0:0 0 10217 0 123456 1\n"
        self.assertEqual(socket_identity(line, 10217), "123456")
        for text in [
            line.replace("10217", "1000"),
            line.replace("0A", "01"),
            line.replace("0100007F", "00000000"),
            line + line,
        ]:
            with self.assertRaisesRegex(
                DriverFailure, "app_managed_socket_unavailable"
            ):
                socket_identity(text, 10217)

    def test_process_start_handles_spaces_in_comm_and_refuses_bad_stat(self):
        self.assertEqual(
            process_start(
                "12 (app (worker)) " + " ".join(["S"] + ["0"] * 18 + ["9988"])
            ),
            "9988",
        )
        self.assertIsNone(process_start("12 (app) S 0"))

    def prefs(self, profile='[{"id":"fixture"}]', extra=""):
        return f'<map><string name="flutter.oc.profiles">{profile}</string><string name="flutter.oc.appearance">dark</string>{extra}</map>'

    def test_selected_projection_preserves_logical_json_not_whole_xml(self):
        a = profile_projection(
            self.prefs(
                '[{"id":"fixture","name":"test"}]',
                '<boolean name="flutter.oc.quotaMonitor" value="true"/>',
            )
        )
        b = profile_projection(self.prefs('[{"name":"test","id":"fixture"}]'))
        self.assertEqual(a, b)
        self.assertNotEqual(a, profile_projection(self.prefs('[{"id":"changed"}]')))

    def test_empty_malformed_duplicate_profiles_do_not_qualify_retention(self):
        for raw in [
            self.prefs("[]"),
            self.prefs("null"),
            self.prefs("invalid"),
            self.prefs(extra='<string name="flutter.oc.profiles">[]</string>'),
            "<map/>",
        ]:
            with self.assertRaises(DriverFailure):
                profile_projection(raw)


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "candidate.apk"
        self.path.write_bytes(b"fixture-apk")
        self.artifact = Artifact(
            self.path,
            2197,
            "1.2.0",
            hashlib.sha256(b"fixture-apk").hexdigest(),
            LOCAL_SIGNER,
            "coordinator-approved",
        )

    def test_hash_and_symlink_refusal_precedes_apk_tools(self):
        bad = Artifact(
            self.path, 2197, "1.2.0", "a" * 64, LOCAL_SIGNER, "coordinator-approved"
        )
        with patch(
            "tool.qa.fq9.ports.apk_identity",
            side_effect=AssertionError("APK tool called"),
        ):
            with self.assertRaisesRegex(DriverFailure, "artifact_hash_mismatch"):
                verify_artifact(bad)
            link = self.path.parent / "linked.apk"
            link.symlink_to(self.path)
            with self.assertRaisesRegex(DriverFailure, "artifact_unavailable"):
                verify_artifact(
                    Artifact(
                        link,
                        2197,
                        "1.2.0",
                        self.artifact.sha256,
                        LOCAL_SIGNER,
                        "coordinator-approved",
                    )
                )

    def test_artifact_change_during_signature_verification_is_refused(self):
        actual = dict(
            build=2197,
            version="1.2.0",
            sha256=self.artifact.sha256,
            signer=LOCAL_SIGNER,
        )

        def verify(path):
            self.path.write_bytes(b"changed")
            return actual

        with patch("tool.qa.fq9.ports.apk_identity", side_effect=verify):
            with self.assertRaisesRegex(DriverFailure, "artifact_changed"):
                verify_artifact(self.artifact)

    def test_verified_certificate_must_match_receipt(self):
        actual = dict(
            build=2197, version="1.2.0", sha256=self.artifact.sha256, signer="b" * 64
        )
        with patch("tool.qa.fq9.ports.apk_identity", return_value=actual):
            with self.assertRaisesRegex(DriverFailure, "artifact_signer_mismatch"):
                verify_artifact(self.artifact)


class PortTests(unittest.TestCase):
    def port(self, **kwargs):
        return AndroidPorts("emulator-5554", "fq9-fixture", **kwargs)

    def test_adb_cannot_run_outside_lock_scope(self):
        p = self.port()
        with patch(
            "tool.qa.fq9.ports.execute", side_effect=AssertionError("ADB called")
        ):
            with self.assertRaisesRegex(DriverFailure, "device_unavailable"):
                p.adb("get-state")

    def test_private_absence_observation_uses_privileged_emulator_identity(self):
        p = self.port()
        calls = []
        p.text = lambda *args, **kw: calls.append(args) or "no"
        self.assertFalse(p.exists(BASE))
        self.assertIn("su 0 sh -c", calls[0][1])
        p._uid = 10217
        p.exists(BASE)
        self.assertIn("su 10217 sh -c", calls[1][1])

    def test_fresh_install_cannot_act_on_shared_or_used_avd(self):
        artifact = Artifact(
            Path("/unused"),
            2197,
            "1.2.0",
            "a" * 64,
            LOCAL_SIGNER,
            "coordinator-approved",
        )
        for snapshot in [
            dict(
                dedicated=False,
                currentUser=0,
                packageInstalled=False,
                appDataPresent=False,
            ),
            dict(
                dedicated=True,
                currentUser=0,
                packageInstalled=False,
                appDataPresent=True,
            ),
        ]:
            p = self.port()
            p.fresh_snapshot = lambda: snapshot
            with patch(
                "tool.qa.fq9.ports.verify_artifact",
                side_effect=AssertionError("APK tool called"),
            ):
                with self.assertRaisesRegex(DriverFailure, "dedicated_avd_required"):
                    p.install_fresh(artifact)

    def test_install_update_is_only_replace_downgrade_and_rechecks_artifact(self):
        p = self.port()
        calls = []
        p.require_idle_setup = lambda: None
        p.adb = lambda *args, **kw: calls.append(args)
        artifact = Artifact(
            Path("/unused"),
            2197,
            "1.2.0",
            "a" * 64,
            LOCAL_SIGNER,
            "coordinator-approved",
        )
        with patch("tool.qa.fq9.ports.verify_artifact") as verified:
            p.install_update(artifact)
        verified.assert_called_once_with(artifact)
        self.assertEqual(calls, [("install", "-r", "-d", "/unused")])
        self.assertTrue(p.mutated)

    def test_restore_same_normal_does_not_interrupt_unclean_turn_or_change_signer(self):
        p = self.port()
        p.mutated = True
        p.close_protocol = lambda: None
        artifact = Artifact(
            Path("/unused"),
            2197,
            "1.2.0",
            "a" * 64,
            LOCAL_SIGNER,
            "coordinator-approved",
        )
        p.installed_identity = lambda: dict(
            build=2197, version="1.2.0", sha256="a" * 64, signer=LOCAL_SIGNER, uid=10217
        )
        calls = []
        p.launch = lambda: calls.append("launch")
        p.install_update = lambda _: self.fail("normal reinstall")
        p.restore_normal(artifact)
        self.assertEqual(calls, ["launch"])
        p.installed_identity = lambda: dict(
            build=2197, version="1.2.0", sha256="a" * 64, signer="b" * 64, uid=10217
        )
        with self.assertRaisesRegex(DriverFailure, "normal_restore_identity_mismatch"):
            p.restore_normal(artifact)

    def test_http_redirect_cannot_forward_runtime_credential(self):
        with self.assertRaisesRegex(DriverFailure, "protocol_response_invalid"):
            _NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.test")

    def live(
        self,
        *,
        new_prompt=False,
        retry=False,
        command="sleep 120",
        wrong_owner=False,
        pending=False,
    ):
        receipt = dict(
            engine="opencode",
            directory="/root/projects/fq9-fixture",
            sessions=[
                dict(id="ses_1", title="fq9-fixture-background", promptID="msg_1")
            ],
        )
        p = self.port(turn=receipt)
        p._uid = 10217
        p._socket = "123456"
        p._runtime_version = "1.18.32"
        history = [
            dict(
                info=dict(id="msg_1", role="user", sessionID="ses_1"),
                parts=[dict(type="text", text="FQ9_BACKGROUND_FIXTURE")],
            ),
            dict(
                info=dict(
                    id="msg_2", role="assistant", sessionID="ses_1", parentID="msg_1"
                ),
                parts=[
                    dict(
                        type="tool",
                        tool="bash",
                        sessionID="ses_1",
                        messageID="foreign" if wrong_owner else "msg_2",
                        callID="call_1",
                        state=dict(status="completed", input=dict(command=command)),
                    )
                ],
            ),
        ]
        if new_prompt:
            history.append(
                dict(info=dict(id="msg_new", role="user", sessionID="ses_1"), parts=[])
            )
        p._session_history = lambda *args: history
        p.protocol = (
            lambda method, path, **kw: ([{"sessionID": "ses_1"}] if pending else [])
            if path in ("/permission", "/question")
            else {"ses_1": {"type": "retry" if retry else "busy"}}
        )
        p._main_process = lambda: ("123", "999")
        p.text = lambda *args, **kw: (
            "  0: 0100007F:1001 0:0 0A 0:0 0:0 0 10217 0 123456 1"
            if args[0:2] == ("shell", "cat")
            else f"NotificationRecord(a: pkg={PACKAGE} id=4747)\n channel=opencode_live_connection flags=0x62"
        )
        p.services = (
            lambda: f"  * ServiceRecord{{a u0 {PACKAGE}/.BuiltinServerService}}\n isForeground=true\n  * ServiceRecord{{b u0 {PACKAGE}/.BackgroundConnectionService}}\n isForeground=true"
        )
        p.app_visible = lambda: True
        return p

    def test_live_busy_inference_gap_requires_actual_owned_tool_progress(self):
        snapshot = self.live().live_snapshot()
        self.assertTrue(snapshot["turnActive"])
        self.assertEqual(snapshot["progressCounter"], 1)
        self.assertFalse(self.live(retry=True).live_snapshot()["turnActive"])
        self.assertFalse(self.live(pending=True).live_snapshot()["turnActive"])

    def test_live_new_prompt_foreign_part_and_unbounded_command_refused(self):
        for kw, code in [
            ({"new_prompt": True}, "live_prompt_mismatch"),
            ({"wrong_owner": True}, "live_scope_mismatch"),
            ({"command": "sleep 120; echo sensitive"}, "live_fixture_command_invalid"),
        ]:
            with self.subTest(kw=kw), self.assertRaisesRegex(DriverFailure, code):
                self.live(**kw).live_snapshot()

    def test_live_oc2_cannot_be_certified_from_lagging_http_history(self):
        p = self.port(turn={"engine": "opencode2"})
        with self.assertRaisesRegex(DriverFailure, "live_oc2_observation_unavailable"):
            p.live_snapshot()

    def test_cleanup_revalidates_scope_before_abort_and_delete(self):
        p = self.live()
        calls = []
        p._session_history = lambda *args: (_ for _ in ()).throw(
            DriverFailure("history_scope_mismatch")
        )
        p.protocol = lambda *args, **kw: calls.append(args)
        with self.assertRaisesRegex(DriverFailure, "history_scope_mismatch"):
            p.cleanup_turn()
        self.assertEqual(calls, [])

    def test_cleanup_never_aborts_a_newer_prompt_in_our_fixture_row(self):
        p = self.live(new_prompt=True)
        calls = []
        p.protocol = lambda *args, **kwargs: calls.append(args)
        with self.assertRaisesRegex(DriverFailure, "owned_turn_cleanup_failed"):
            p.cleanup_turn()
        self.assertEqual(calls, [])

    def test_cleanup_rechecks_newer_prompt_after_abort_before_delete(self):
        p = self.live()
        initial = p._session_history(None, None)
        changed = self.live(new_prompt=True)._session_history(None, None)
        reads = iter([initial, changed])
        p._session_history = lambda *args: next(reads)
        calls = []

        def protocol(method, path, **kwargs):
            calls.append((method, path))
            return True if method == "POST" else {"ses_1": {"type": "idle"}}

        p.protocol = protocol
        with self.assertRaisesRegex(DriverFailure, "owned_turn_cleanup_failed"):
            p.cleanup_turn()
        self.assertEqual(
            calls, [("POST", "/session/ses_1/abort"), ("GET", "/session/status")]
        )

    def test_failed_forward_removal_remains_retryable_and_never_echoes_password(self):
        p = self.port()
        p._forward = 43210
        p._password = b"sensitive"
        p.adb = lambda *a: (_ for _ in ()).throw(DriverFailure("command_failed"))
        with self.assertRaisesRegex(DriverFailure, "forward_cleanup_failed"):
            p.close_protocol()
        self.assertEqual(p._forward, 43210)
        self.assertIsNone(p._password)
        p.adb = lambda *a: b""
        p.close_protocol()
        self.assertIsNone(p._forward)

    def test_private_bodies_are_device_bounded_and_never_spooled_to_host_disk(self):
        p = self.port()
        p.locked = True
        p._uid = 10217
        response = type("Response", (), {"returncode": 0, "stdout": b"sensitive"})()
        with (
            patch(
                "tool.qa.fq9.ports.execute",
                side_effect=AssertionError("spooled private data"),
            ),
            patch(
                "tool.qa.fq9.ports.subprocess.run", return_value=response
            ) as executed,
        ):
            self.assertEqual(
                p.private_bytes(FILES + "/runtime-config", limit=512), b"sensitive"
            )
        self.assertIn("head -c 513", executed.call_args.args[0][-1])
        response.stdout = b"x" * 513
        with patch("tool.qa.fq9.ports.subprocess.run", return_value=response):
            with self.assertRaisesRegex(DriverFailure, "private_data_too_large"):
                p.private_bytes(FILES + "/runtime-config", limit=512)

    def test_component_projection_accepts_full_class_and_rejects_foreign_activity(self):
        p = self.port()
        p.text = (
            lambda *a: f"mResumedActivity: ActivityRecord{{a {PACKAGE}/{PACKAGE}.MainActivity t1}}"
        )
        self.assertTrue(p.app_visible())
        p.text = (
            lambda *a: "mResumedActivity: ActivityRecord{a foreign/.MainActivity t1}"
        )
        self.assertFalse(p.app_visible())
        full = f"  * ServiceRecord{{a u0 {PACKAGE}/{PACKAGE}.BackgroundConnectionService}}\n isForeground=true"
        self.assertTrue(
            service_foreground(full, PACKAGE + "/.BackgroundConnectionService")
        )

    def test_home_waits_for_observed_background_before_starting_dwell(self):
        p = self.port()
        calls = []
        p.adb = lambda *args: calls.append(args)
        visibility = iter([True, False])
        p.app_visible = lambda: next(visibility)
        with (
            patch("tool.qa.fq9.ports.time.monotonic", return_value=1),
            patch("tool.qa.fq9.ports.time.sleep") as sleep,
        ):
            p.home()
        self.assertEqual(calls, [("shell", "input", "keyevent", "KEYCODE_HOME")])
        sleep.assert_called_once_with(0.2)
        p.app_visible = lambda: True
        with (
            patch("tool.qa.fq9.ports.time.monotonic", side_effect=[1, 7]),
            patch(
                "tool.qa.fq9.ports.time.sleep", side_effect=AssertionError("real sleep")
            ),
        ):
            with self.assertRaisesRegex(DriverFailure, "background_app_still_visible"):
                p.home()

    def discovery(self, *, duplicate=False, ready=True):
        p = self.live()
        p._connect = lambda engine: None
        session = {
            "id": "ses_1",
            "title": "fq9-fixture-background",
            "directory": "/root/projects/fq9-fixture",
        }
        catalog = (
            [session, session]
            if duplicate
            else [
                session,
                {
                    "id": "ses_foreign",
                    "title": "private title",
                    "directory": session["directory"],
                },
            ]
        )
        p.protocol = lambda *args, **kw: catalog
        snapshot = {
            key: True
            for key in (
                "ownedTurn",
                "appManaged",
                "appVisible",
                "appAlive",
                "serverAlive",
                "foregroundService",
                "ongoingNotification",
                "turnActive",
            )
        }
        snapshot.update(progressCounter=1, completed=False, failed=False)
        if not ready:
            snapshot["turnActive"] = False
        p.live_snapshot = lambda: snapshot
        return p

    def test_read_only_fixture_discovery_exports_only_owned_public_ids(self):
        value = self.discovery().find_live_receipt("/root/projects/fq9-fixture")
        self.assertEqual(
            value,
            {
                "engine": "opencode",
                "directory": "/root/projects/fq9-fixture",
                "sessions": [
                    {
                        "id": "ses_1",
                        "title": "fq9-fixture-background",
                        "promptID": "msg_1",
                    }
                ],
            },
        )
        self.assertNotIn("private", repr(value))

    def test_discovery_refuses_duplicate_titles_or_unready_turn(self):
        for options, code in [
            ({"duplicate": True}, "live_fixture_not_unique"),
            ({"ready": False}, "live_fixture_not_ready"),
        ]:
            with (
                self.subTest(options=options),
                self.assertRaisesRegex(DriverFailure, code),
            ):
                self.discovery(**options).find_live_receipt(
                    "/root/projects/fq9-fixture"
                )


if __name__ == "__main__":
    unittest.main()
