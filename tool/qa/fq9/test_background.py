"""Virtual-clock background qualification tests: no ADB/network/real sleeps."""

import json
import unittest

from .background import FAIL_CODES, run_background
from .common import DriverFailure


class FakePorts:
    def __init__(self):
        self.now = 0
        self.visible = True
        self.resumed = False
        self.calls = []
        self.waits = []
        self.initial = {}
        self.changes = []
        self.fail_stage = None
        self.stall_clock = False
        self.reverse_clock = False
        self.freeze_progress_at = None
        self.final_completion = False

    def live_snapshot(self):
        self.calls.append(("snapshot", self.now))
        if self.fail_stage == "setup" or (
            self.fail_stage == "observation" and self.now >= 60
        ):
            raise RuntimeError("private server detail sk-unlogged")
        progress_time = self.now
        if self.freeze_progress_at is not None:
            progress_time = min(progress_time, self.freeze_progress_at)
        value = {
            "ownedTurn": True,
            "appManaged": True,
            "appVisible": self.visible,
            "appAlive": True,
            "serverAlive": True,
            "foregroundService": True,
            "ongoingNotification": True,
            "turnActive": True,
            "progressCounter": 1 + int(progress_time // 120),
            "completed": False,
            "failed": False,
            "ignoredPrivateField": "private transcript sk-unlogged",
        }
        value.update(self.initial)
        for when, changes in self.changes:
            if self.now >= when:
                value.update(changes)
        if self.resumed and self.final_completion:
            value.update(completed=True, turnActive=False)
        return value

    def home(self):
        self.calls.append(("home", self.now))
        self.visible = False
        if self.fail_stage == "home":
            raise RuntimeError("private home failure")

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.calls.append(("sleep", seconds))
        self.waits.append(seconds)
        if self.fail_stage == "sleep":
            raise RuntimeError("private sleep failure")
        if self.reverse_clock:
            self.now -= seconds
        elif not self.stall_clock:
            self.now += seconds

    def resume(self):
        self.calls.append(("resume", self.now))
        if self.fail_stage == "resume":
            raise DriverFailure("private_resume_token_sk_unlogged")
        self.visible = True
        self.resumed = True

    def cleanup_turn(self):
        self.calls.append(("cleanup", self.now))
        if self.fail_stage == "cleanup":
            raise DriverFailure("private_cleanup_token_sk_unlogged")


class BackgroundTests(unittest.TestCase):
    def assert_safe_failure(self, result, code):
        self.assertEqual(result["state"], "fail")
        self.assertEqual(result["code"], code)
        self.assertIn(code, result["errorCodes"])
        self.assertTrue(set(result["errorCodes"]).issubset(FAIL_CODES))
        self.assertNotIn("private", json.dumps(result))
        self.assertNotIn("sk-unlogged", json.dumps(result))

    def test_one_home_dwell_proves_five_and_thirty_minute_active_turn(self):
        ports = FakePorts()
        result = run_background(ports)
        self.assertEqual(result["state"], "pass")
        self.assertEqual(result["code"], "verified")
        self.assertEqual(result["dwellSeconds"], 1800)
        self.assertEqual([x["seconds"] for x in result["checkpoints"]], [300, 1800])
        self.assertEqual(
            [x["elapsedSeconds"] for x in result["checkpoints"]], [300, 1800]
        )
        self.assertEqual([x["progressDelta"] for x in result["checkpoints"]], [2, 15])
        self.assertTrue(
            all(x["turnActive"] and x["background"] for x in result["checkpoints"])
        )
        self.assertTrue(result["resumed"])
        self.assertTrue(result["cleanupSucceeded"])
        self.assertEqual([x[0] for x in ports.calls].count("home"), 1)
        self.assertEqual([x[0] for x in ports.calls].count("resume"), 1)
        self.assertEqual(ports.calls[-1][0], "cleanup")
        self.assertEqual(len(ports.waits), 60)
        self.assertLessEqual(max(ports.waits), 30)
        self.assertEqual(result["errorCodes"], [])
        self.assertNotIn("sk-unlogged", json.dumps(result))

    def test_non_dividing_poll_budget_hits_both_real_deadlines(self):
        result = run_background(FakePorts(), poll_seconds=7)
        self.assertEqual(result["state"], "pass")
        self.assertEqual(
            [x["elapsedSeconds"] for x in result["checkpoints"]], [300, 1800]
        )

    def test_final_completion_only_after_resume_is_accepted(self):
        ports = FakePorts()
        ports.final_completion = True
        result = run_background(ports)
        self.assertEqual(result["state"], "pass")
        self.assertTrue(result["resumed"])

    def test_early_completion_cannot_substitute_for_background_checkpoint(self):
        for finished_at, expected_checkpoints in [(90, 0), (900, 1), (1800, 1)]:
            with self.subTest(finished_at=finished_at):
                ports = FakePorts()
                ports.changes = [
                    (finished_at, {"completed": True, "turnActive": False})
                ]
                result = run_background(ports)
                self.assert_safe_failure(result, "background_turn_finished_early")
                self.assertEqual(len(result["checkpoints"]), expected_checkpoints)
                self.assertTrue(result["resumed"])
                self.assertTrue(result["cleanupSucceeded"])

    def test_failed_turn_stays_failed_with_separate_resume_failure(self):
        ports = FakePorts()
        ports.changes = [(60, {"failed": True})]
        result = run_background(ports)
        self.assert_safe_failure(result, "background_turn_failed")
        self.assertEqual(
            result["errorCodes"], ["background_turn_failed", "background_resume_failed"]
        )
        self.assertTrue(result["cleanupSucceeded"])

    def test_background_identity_service_notification_and_active_loss_fail(self):
        fields = {
            "ownedTurn": "background_fixture_not_owned",
            "appManaged": "background_fixture_not_app_managed",
            "appAlive": "background_app_lost",
            "serverAlive": "background_server_lost",
            "foregroundService": "background_service_lost",
            "ongoingNotification": "background_notification_lost",
            "turnActive": "background_turn_inactive",
        }
        for field, code in fields.items():
            with self.subTest(field=field):
                ports = FakePorts()
                ports.changes = [(60, {field: False})]
                result = run_background(ports)
                self.assert_safe_failure(result, code)
                self.assertEqual(result["checkpoints"], [])
                self.assertTrue(result["cleanupAttempted"])

    def test_foreground_visibility_during_dwell_is_not_background_proof(self):
        ports = FakePorts()
        ports.changes = [(60, {"appVisible": True})]
        self.assert_safe_failure(run_background(ports), "background_app_still_visible")

    def test_absent_unrelated_or_non_app_managed_fixture_never_mutates(self):
        for changes, code in [
            ({"ownedTurn": False}, "background_fixture_not_owned"),
            ({"appManaged": False}, "background_fixture_not_app_managed"),
            ({"ownedTurn": "true"}, "background_snapshot_invalid"),
        ]:
            with self.subTest(changes=changes):
                ports = FakePorts()
                ports.initial = changes
                result = run_background(ports)
                self.assert_safe_failure(result, code)
                self.assertFalse(result["homeAttempted"])
                self.assertFalse(result["cleanupAttempted"])
                self.assertEqual([x[0] for x in ports.calls], ["snapshot"])

    def test_owned_setup_failure_cleans_only_fixture_without_home_or_resume(self):
        for changes, code in [
            ({"appVisible": False}, "background_initial_app_not_visible"),
            ({"foregroundService": False}, "background_service_lost"),
            ({"ongoingNotification": False}, "background_notification_lost"),
            ({"completed": True}, "background_turn_finished_early"),
            ({"failed": True}, "background_turn_failed"),
            ({"progressCounter": 0}, "background_real_progress_missing"),
            ({"progressCounter": True}, "background_snapshot_invalid"),
        ]:
            with self.subTest(changes=changes):
                ports = FakePorts()
                ports.initial = changes
                result = run_background(ports)
                self.assert_safe_failure(result, code)
                self.assertFalse(result["homeAttempted"])
                self.assertTrue(result["cleanupSucceeded"])
                self.assertEqual([x[0] for x in ports.calls], ["snapshot", "cleanup"])

    def test_retry_only_or_stale_progress_cannot_pass_either_checkpoint(self):
        for freeze_at, expected_checkpoints in [(0, 0), (300, 1)]:
            with self.subTest(freeze_at=freeze_at):
                ports = FakePorts()
                ports.freeze_progress_at = freeze_at
                result = run_background(ports)
                self.assert_safe_failure(
                    result, "background_checkpoint_progress_missing"
                )
                self.assertEqual(len(result["checkpoints"]), expected_checkpoints)

    def test_persisted_progress_regression_fails_closed(self):
        ports = FakePorts()
        ports.changes = [(180, {"progressCounter": 1})]
        self.assert_safe_failure(run_background(ports), "background_progress_regressed")

    def test_stalled_and_reversed_virtual_clock_do_not_loop(self):
        for mode, code in [
            ("stall_clock", "background_clock_stalled"),
            ("reverse_clock", "background_clock_invalid"),
        ]:
            with self.subTest(mode=mode):
                ports = FakePorts()
                setattr(ports, mode, True)
                result = run_background(ports)
                self.assert_safe_failure(result, code)
                self.assertEqual(len(ports.waits), 1)
                self.assertTrue(result["cleanupSucceeded"])

    def test_invalid_clock_values_fail_without_sleeping(self):
        for value in [True, "0", None, -1, float("nan"), float("inf"), 10**1000]:
            with self.subTest(value=type(value).__name__):
                ports = FakePorts()
                ports.monotonic = lambda value=value: value
                result = run_background(ports)
                self.assert_safe_failure(result, "background_clock_invalid")
                self.assertEqual(ports.waits, [])
                self.assertTrue(result["resumed"])
                self.assertTrue(result["cleanupSucceeded"])

    def test_port_stage_failures_are_sanitized_and_restore_after_home_attempt(self):
        for stage, code in [
            ("setup", "background_setup_failed"),
            ("home", "background_home_failed"),
            ("sleep", "background_sleep_failed"),
            ("observation", "background_observation_failed"),
        ]:
            with self.subTest(stage=stage):
                ports = FakePorts()
                ports.fail_stage = stage
                result = run_background(ports)
                self.assert_safe_failure(result, code)
                if stage == "setup":
                    self.assertFalse(result["cleanupAttempted"])
                else:
                    self.assertTrue(result["cleanupSucceeded"])
                    self.assertIn("resume", [x[0] for x in ports.calls])

    def test_resume_failure_invalidates_pass_and_does_not_skip_cleanup(self):
        ports = FakePorts()
        ports.fail_stage = "resume"
        result = run_background(ports)
        self.assert_safe_failure(result, "background_resume_failed")
        self.assertEqual(len(result["checkpoints"]), 2)
        self.assertFalse(result["resumed"])
        self.assertTrue(result["cleanupSucceeded"])

    def test_resume_must_observe_app_visibility_and_owned_live_or_final_turn(self):
        for change in [
            {"appVisible": False},
            {"ownedTurn": False},
            {"appManaged": False},
            {"appAlive": False},
            {"serverAlive": False},
            {"turnActive": False},
        ]:
            with self.subTest(change=change):
                ports = FakePorts()
                original = ports.resume

                def resume(original=original, change=change):
                    original()
                    ports.changes.append((1800, change))

                ports.resume = resume
                result = run_background(ports)
                self.assert_safe_failure(result, "background_resume_failed")
                self.assertTrue(result["cleanupSucceeded"])

    def test_cleanup_failure_invalidates_completed_qualification(self):
        ports = FakePorts()
        ports.fail_stage = "cleanup"
        result = run_background(ports)
        self.assert_safe_failure(result, "background_cleanup_failed")
        self.assertTrue(result["resumed"])
        self.assertFalse(result["cleanupSucceeded"])

    def test_primary_failure_is_preserved_when_cleanup_also_fails(self):
        ports = FakePorts()
        ports.initial = {"foregroundService": False}
        ports.fail_stage = "cleanup"
        result = run_background(ports)
        self.assert_safe_failure(result, "background_service_lost")
        self.assertEqual(
            result["errorCodes"],
            ["background_service_lost", "background_cleanup_failed"],
        )

    def test_invalid_plan_refuses_all_ports(self):
        plans = [
            {"checkpoints": ()},
            {"checkpoints": (1800, 300)},
            {"checkpoints": (300, 300)},
            {"checkpoints": (True, 1800)},
            {"checkpoints": (float("nan"),)},
            {"checkpoints": (1801,)},
            {"poll_seconds": 31},
            {"poll_seconds": 0},
            {"poll_seconds": float("inf")},
            {"poll_seconds": True},
            {"poll_seconds": 10**1000},
            {"checkpoints": ("300",)},
        ]
        for plan in plans:
            with self.subTest(plan=plan):
                ports = FakePorts()
                self.assert_safe_failure(
                    run_background(ports, **plan), "background_plan_invalid"
                )
                self.assertEqual(ports.calls, [])


if __name__ == "__main__":
    unittest.main()
