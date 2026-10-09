import argparse
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

from . import run
from .app_runtime import AppRuntimeUi, RuntimeTransaction
from .common import Artifact, CANDIDATE_BUILD, DriverFailure, LOCAL_SIGNER, PACKAGE


class Device:
    def __init__(self, engine="opencode2"):
        self.engine = engine
        self.locked = True
        self.mutated = False
        self._socket = None
        self._runtime_mismatch = None
        self.now = 0
        self.calls = []
        self.busy = False

    def close_protocol(self):
        self.calls.append("close")

    def _connect(self, engine):
        self._runtime_mismatch = None
        if engine != self.engine:
            if self.engine == "opencode2":
                self._runtime_mismatch = {
                    "expected": "opencode1",
                    "observed": "opencode2",
                }
            raise DriverFailure("app_managed_engine_unavailable")

    def require_idle_setup(self):
        self.calls.append("idle-setup")

    def services(self):
        return ""

    def protocol(self, method, path):
        self.calls.append(path)
        status = {"private-id": {"type": "busy"}} if self.busy else {}
        return {"data": status} if self.engine == "opencode2" else status

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class RuntimeTransactionTests(unittest.TestCase):
    def transaction(self, device):
        ui = Mock()
        ui.switch.side_effect = lambda target: setattr(device, "engine", target)
        return RuntimeTransaction(device, ui=ui, timeout=2), ui

    def test_switch_and_restore_prior_through_injected_ui_only(self):
        device = Device()
        transaction, ui = self.transaction(device)
        self.assertEqual(transaction.begin(), "opencode2")
        self.assertTrue(transaction.armed)
        self.assertTrue(device.mutated)
        self.assertEqual(device.engine, "opencode")
        transaction.restore()
        self.assertEqual(device.engine, "opencode2")
        self.assertEqual(
            [call.args for call in ui.switch.call_args_list],
            [("opencode",), ("opencode2",)],
        )
        self.assertEqual(
            transaction.facts(),
            {"prior": "opencode2", "selected": "opencode", "restored": True},
        )
        self.assertEqual(device.calls[-1], "close")

    def test_oc1_baseline_needs_no_switch_but_is_verified_on_restore(self):
        device = Device("opencode")
        transaction, ui = self.transaction(device)
        transaction.begin()
        device.busy = True
        transaction.restore()
        ui.switch.assert_not_called()
        self.assertFalse(device.mutated)
        self.assertTrue(transaction.restored)

    def test_foreground_background_work_refuses_before_switch(self):
        device = Device()
        transaction, ui = self.transaction(device)
        with patch("tool.qa.fq9.app_runtime.service_foreground", return_value=True):
            with self.assertRaisesRegex(DriverFailure, "runtime_not_idle"):
                transaction.begin()
            transaction.restore()
        ui.switch.assert_not_called()
        self.assertFalse(device.mutated)

    def test_arm_precedes_ui_failure_and_restoration_still_works(self):
        device = Device()
        transaction, ui = self.transaction(device)

        def switch(target):
            self.assertTrue(transaction.armed)
            device.engine = target
            if target == "opencode":
                raise DriverFailure("runtime_switch_failed")

        ui.switch.side_effect = switch
        with self.assertRaises(DriverFailure):
            transaction.begin()
        transaction.restore()
        self.assertEqual(device.engine, "opencode2")

    def test_busy_and_unknown_runtime_refuse_before_ui_mutation(self):
        device = Device()
        device.busy = True
        transaction, ui = self.transaction(device)
        with self.assertRaisesRegex(DriverFailure, "runtime_not_idle"):
            transaction.begin()
        transaction.restore()
        ui.switch.assert_not_called()
        self.assertFalse(device.mutated)
        device = Device("unknown")
        transaction, ui = self.transaction(device)
        with self.assertRaises(DriverFailure):
            transaction.begin()
        ui.switch.assert_not_called()

    def test_switch_wait_is_bounded_and_failed_restore_is_not_pass(self):
        device = Device()
        transaction, ui = self.transaction(device)
        ui.switch.side_effect = None
        with self.assertRaisesRegex(DriverFailure, "runtime_switch_timeout"):
            transaction.begin()
        self.assertEqual(device.now, 2)
        # The prior engine never left; restore observes it without another tap.
        transaction.restore()
        self.assertTrue(transaction.restored)

    def test_seed_failure_keeps_primary_code_and_restores_after_normal_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            device = Device()
            artifact = Artifact(
                Path("/unused"),
                CANDIDATE_BUILD,
                "1.2.0",
                "a" * 64,
                LOCAL_SIGNER,
                "coordinator-approved",
            )
            device.device_ready = Mock()
            device.installed_identity = lambda: dict(
                build=artifact.build,
                version=artifact.version,
                sha256=artifact.sha256,
                signer=artifact.signer,
            )
            device.restore_normal = lambda value: device.calls.append("normal-apk")
            transaction, ui = self.transaction(device)
            original_restore = transaction.restore

            def restore():
                self.assertIn("normal-apk", device.calls)
                original_restore()

            transaction.restore = restore
            args = argparse.Namespace(
                case="upgrade",
                serial="emulator-5554",
                run_id="fq9-runtime",
                dedicated_avd=None,
                seed_history_receipt=root / "private.json",
            )
            with (
                patch.object(run, "LOCK", root / "lock"),
                patch.object(run, "RuntimeTransaction", return_value=transaction),
                patch.object(
                    run.fixture,
                    "seed_history_fixture",
                    side_effect=DriverFailure("fixture_seed_invalid"),
                ),
            ):
                result = run.run_locked(
                    args,
                    {"candidate": artifact, "normal": artifact, "previous": artifact},
                    None,
                    root / "report.json",
                    port_factory=lambda *a, **kw: device,
                )
            self.assertEqual(result["code"], "fixture_seed_invalid")
            self.assertEqual(result["runtimeTransaction"]["restored"], True)
            self.assertEqual(device.engine, "opencode2")
            self.assertFalse(result["automatedChecksPassed"])
            self.assertNotIn("private-id", json.dumps(result))


class AppRuntimeUiTests(unittest.TestCase):
    def page(self, labels, package=PACKAGE):
        root = ET.Element("hierarchy")
        parent = ET.SubElement(root, "node", package=package, bounds="[0,0][480,800]")
        for index, label in enumerate(labels):
            ET.SubElement(
                parent,
                "node",
                package=package,
                text=label,
                bounds=f"[10,{20 + index * 50}][460,{60 + index * 50}]",
                enabled="true",
            )
        return ET.tostring(root)

    def test_app_page_requires_real_confirmation_before_switch(self):
        device = Device()
        pages = [
            self.page(["In-app Ubuntu", "Switch to OpenCode 1"]),
            self.page(
                [
                    "Switch to OpenCode 1?",
                    "Stops this phone’s server and running tasks. Conversations, provider settings and credentials stay separate; project files and configuration are shared. You can switch back.",
                    "Switch to OpenCode 1",
                ]
            ),
        ]
        taps = []
        device.launch = Mock()

        def adb(*args, **kwargs):
            if args[0] == "exec-out":
                return pages[min(len(taps), 1)]
            if args[:3] == ("shell", "input", "tap"):
                taps.append(args)
            return b""

        device.adb = adb
        AppRuntimeUi(device).switch("opencode")
        self.assertEqual(len(taps), 2)
        self.assertTrue(all(args[:3] == ("shell", "input", "tap") for args in taps))

    def test_settings_tab_returns_to_conversations_before_runtime_navigation(self):
        device = Device()
        ui = AppRuntimeUi(device)
        settings = ET.fromstring(self.page(["Conversations", "Settings", "Conversations"]))
        settings.findall(".//node")[-1].set("clickable", "true")
        pages = [
            ET.tostring(settings),
            self.page(["In-app Ubuntu", "Switch to OpenCode 1"]),
            self.page([
                next(iter(ui.labels("setupSwitchConfirmTitle", "opencode"))),
                next(iter(ui.labels("setupSwitchConfirmDetail"))),
                next(iter(ui.labels("setupSwitchConfirm", "opencode"))),
            ]),
        ]
        taps = []
        device.launch = Mock()

        def adb(*args, **kwargs):
            if args[0] == "exec-out":
                return pages[min(len(taps), 2)]
            if args[:3] == ("shell", "input", "tap"):
                taps.append(args)
            return b""

        device.adb = adb
        ui.switch("opencode")
        self.assertEqual(len(taps), 3)
        self.assertEqual(taps[0][-2:], ("235", "140"))

    def test_merged_server_pill_matches_only_fixed_action_suffix(self):
        ui = AppRuntimeUi(Device())
        node = ET.Element("node", text="private server, Running, Switch server", clickable="true")
        self.assertIs(ui.server_switcher([node]), node)
        self.assertIsNone(ui.server_switcher([node, node]))
        node.set("text", "Switch server, private server")
        self.assertIsNone(ui.server_switcher([node]))

    def test_foreign_app_copy_is_never_tapped(self):
        device = Device()
        device.launch = Mock()
        device.adb = Mock(
            return_value=self.page(
                ["In-app Ubuntu", "Switch to OpenCode 1"], package="foreign.app"
            )
        )
        with self.assertRaises(DriverFailure):
            AppRuntimeUi(device).switch("opencode")
        self.assertFalse(
            any(
                call.args[:3] == ("shell", "input", "tap")
                for call in device.adb.call_args_list
            )
        )


if __name__ == "__main__":
    unittest.main()
