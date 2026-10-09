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
