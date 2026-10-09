"""Final-candidate regression checks; no device access."""

import unittest
from pathlib import Path
from unittest.mock import patch
from tool.qa import final_pass as batch
from tool.qa.fq9 import run as fq9


class FinalCandidateTests(unittest.TestCase):
    def test_normal_restoration_targets_2203(self):
        args = batch.parser().parse_args([])
        self.assertEqual(args.normal_apk.name, "oc-2203.apk")
        self.assertEqual(batch.plan(args)["normalBuild"], 2203)

    def test_explicit_2203_background_plan_is_allowed_without_device_access(self):
        with patch.object(fq9, "run_locked") as run, patch("builtins.print"):
            self.assertEqual(
                fq9.main(["--case", "background", "--candidate-build", "2203"]), 0
            )
        run.assert_not_called()

    def test_final_pass_can_explicitly_skip_stale_floor_with_receipt(self):
        import tempfile
        from types import SimpleNamespace

        with tempfile.TemporaryDirectory() as directory:
            context = SimpleNamespace(output=Path(directory))
            value = batch.dispatch(
                "ba-storage-floor",
                {"skip_reason": "stale_storage_floor_artifact"},
                context,
            )
            self.assertEqual(value["status"], "blocked")
            self.assertEqual(value["reason"], "stale_storage_floor_artifact")
            self.assertTrue(Path(value["receipts"][0]).is_file())


class LivePreparationTests(unittest.TestCase):
    def test_low_resource_fresh_row_stops_before_provisioning(self):
        import tempfile
        from types import SimpleNamespace
        from tool.qa import final_pass_live as live

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(live, "resources", return_value={"admitted": False}),
            patch.object(live.subprocess, "run") as command,
        ):
            value = live.fresh(SimpleNamespace(output=Path(directory)))
            self.assertEqual(value["reason"], "fresh_avd_resource_budget")
            command.assert_not_called()

    def test_background_fixture_refusal_uses_closed_receipt(self):
        import tempfile
        from types import SimpleNamespace
        from tool.qa import final_pass_live as live
        from tool.qa.fq9.common import DriverFailure

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(live, "AndroidPorts") as factory,
            patch.object(live, "device_ui", return_value=(None, None)),
        ):
            factory.return_value.device_ready.side_effect = DriverFailure(
                "device_unavailable"
            )
            value = live.background(
                SimpleNamespace(output=Path(directory)),
                {"session_receipt": str(Path(directory) / "receipt.json")},
            )
            self.assertEqual(value["reason"], "device_unavailable")
            self.assertFalse(value.get("data", {}).get("safe_to_continue") is False)


class BackgroundIdentityOrderTest(unittest.TestCase):
    def test_app_uid_is_resolved_before_idle_setup_check(self):
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import Mock
        from tool.qa import final_pass_live as live
        from tool.qa.fq9.common import DriverFailure

        order = Mock()
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(live, "AndroidPorts") as factory,
            patch.object(live, "device_ui", return_value=(None, None)),
        ):
            port = factory.return_value
            order.attach_mock(port.device_ready, "ready")
            order.attach_mock(port.installed_identity, "identity")
            order.attach_mock(port.require_idle_setup, "idle")
            port.require_idle_setup.side_effect = DriverFailure("stop_here")
            live.background(
                SimpleNamespace(output=Path(directory)),
                {"session_receipt": str(Path(directory) / "r.json")},
            )
        self.assertEqual(
            [c[0] for c in order.mock_calls], ["ready", "identity", "idle"]
        )


class BackgroundRuntimeSelectionTest(unittest.TestCase):
    def test_opencode1_is_selected_before_connect_and_restored_after_refusal(self):
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import Mock
        from tool.qa import final_pass_live as live
        from tool.qa.fq9.common import DriverFailure

        events = Mock()
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(live, "AndroidPorts") as factory,
            patch.object(live, "RuntimeTransaction") as transaction,
            patch.object(live, "device_ui", return_value=(None, None)),
        ):
            port = factory.return_value
            events.attach_mock(transaction.return_value.begin, "begin")
            events.attach_mock(port._connect, "connect")
            events.attach_mock(transaction.return_value.restore, "restore")
            port._connect.side_effect = DriverFailure("another_live_turn")
            value = live.background(
                SimpleNamespace(output=Path(directory)),
                {"session_receipt": str(Path(directory) / "r.json")},
            )
        self.assertEqual(
            [c[0] for c in events.mock_calls], ["begin", "connect", "restore"]
        )
        self.assertEqual(value["reason"], "another_live_turn")


class FreshRerunLocationTest(unittest.TestCase):
    def test_new_run_does_not_reuse_previous_avd_directory(self):
        from types import SimpleNamespace
        from tool.qa import final_pass_live as live

        checked = []

        def exists(path):
            checked.append(path)
            return True

        context = SimpleNamespace(output=Path("/evidence"), run_id="final-new-run")
        with (
            patch.object(live, "resources", return_value={"admitted": True}),
            patch.object(Path, "exists", exists),
            patch.object(live, "result", return_value={}),
        ):
            live.fresh(context)
        self.assertEqual(
            checked, [Path("/home/eslam/Storage/android-qa-fb1-final-new-run")]
        )
