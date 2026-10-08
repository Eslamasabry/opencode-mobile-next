"""Virtual-clock background qualification tests: no ADB/network/real sleeps."""

import json
import unittest

from .background import FAIL_CODES, run_background
from .common import DriverFailure
from .runtime import ProtocolHTTPFailure


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
    def test_evidence_is_saved_before_resume_and_cleanup_on_every_owned_outcome(self):
        for outcome in ("pass", "observation", "setup"):
            with self.subTest(outcome=outcome):
                ports = FakePorts()
                if outcome == "observation":
                    ports.changes = [(60, {"turnActive": False})]
                elif outcome == "setup":
                    ports.initial = {"foregroundService": False}

                def capture():
                    ports.calls.append(("capture", ports.now))
                    self.assertFalse(ports.resumed)
                    self.assertNotIn("cleanup", [call[0] for call in ports.calls])
                    return True

                result = run_background(ports, before_cleanup=capture)
                actions = [call[0] for call in ports.calls]
                self.assertEqual(actions.count("capture"), 1)
                self.assertLess(actions.index("capture"), actions.index("cleanup"))
                if outcome != "setup":
                    self.assertLess(actions.index("capture"), actions.index("resume"))
                self.assertTrue(result["evidenceCaptured"])
                self.assertFalse(result["sessionRetained"])
                self.assertTrue(result["cleanupSucceeded"])

    def test_capture_requires_literal_true_and_failure_retains_session_without_resume(
        self,
    ):
        for returned in (False, None, "true", 1, {"saved": True}, []):
            with self.subTest(returned=type(returned).__name__):
                ports = FakePorts()

                def capture():
                    ports.calls.append(("capture", ports.now))
                    return returned

                result = run_background(ports, before_cleanup=capture)
                self.assert_safe_failure(result, "background_evidence_capture_failed")
                self.assertFalse(result["evidenceCaptured"])
                self.assertTrue(result["sessionRetained"])
                self.assertFalse(result["cleanupAttempted"])
                self.assertFalse(result["resumed"])
                actions = [call[0] for call in ports.calls]
                self.assertNotIn("resume", actions)
                self.assertNotIn("cleanup", actions)
                self.assertEqual(
                    result["adapterFailure"],
                    {
                        "stage": "background_evidence_capture_failed",
                        "code": "background_evidence_capture_failed",
                    },
                )
                self.assertEqual(result["lastGoodObservation"]["elapsedSeconds"], 1800)

    def test_failed_capture_preserves_original_diagnostics_and_last_good_observation(
        self,
    ):
        ports = self.failing_observation(ProtocolHTTPFailure(503))

        def capture():
            raise RuntimeError("private transcript sk-unlogged")

        result = run_background(ports, before_cleanup=capture)
        self.assert_safe_failure(result, "background_observation_failed")
        self.assertEqual(
            result["errorCodes"],
            ["background_observation_failed", "background_evidence_capture_failed"],
        )
        self.assertEqual(
            result["adapterFailure"],
            {
                "stage": "background_observation_failed",
                "code": "protocol_response_invalid",
                "httpStatus": 503,
            },
        )
        self.assertEqual(result["lastGoodObservation"]["elapsedSeconds"], 30)
        self.assertTrue(result["sessionRetained"])
        self.assertFalse(result["cleanupAttempted"])
        self.assertFalse(result["resumed"])

    def test_invalid_capture_callback_retains_owned_session(self):
        for invalid in (False, "private callback sk-unlogged", {}, 1):
            with self.subTest(invalid=type(invalid).__name__):
                ports = FakePorts()
                result = run_background(ports, before_cleanup=invalid)
                self.assert_safe_failure(result, "background_evidence_capture_failed")
                self.assertTrue(result["sessionRetained"])
                self.assertFalse(result["cleanupAttempted"])
                self.assertFalse(result["resumed"])

    def test_unowned_fixture_never_calls_evidence_callback(self):
        ports = FakePorts()
        ports.initial = {"ownedTurn": False}

        def capture():
            self.fail("An unrelated fixture must not be inspected or mutated")

        result = run_background(ports, before_cleanup=capture)
        self.assert_safe_failure(result, "background_fixture_not_owned")
        self.assertIsNone(result["evidenceCaptured"])
        self.assertFalse(result["cleanupAttempted"])
        self.assertFalse(result["resumed"])

    def test_legacy_no_callback_does_not_claim_evidence_capture(self):
        result = run_background(FakePorts())
        self.assertEqual(result["state"], "pass")
        self.assertIsNone(result["evidenceCaptured"])
        self.assertFalse(result["sessionRetained"])
        self.assertTrue(result["cleanupSucceeded"])

    def failing_observation(self, failure):
        ports = FakePorts()
        original = ports.live_snapshot

        def observe():
            if ports.now >= 60 and not ports.resumed:
                raise failure
            return original()

        ports.live_snapshot = observe
        return ports

    def test_exact_adapter_category_and_last_good_observation_survive_resume(self):
        for failure, category, status in [
            (
                DriverFailure("live_fixture_command_invalid"),
                "live_fixture_command_invalid",
                None,
            ),
            (ProtocolHTTPFailure(503), "protocol_response_invalid", 503),
        ]:
            with self.subTest(category=category):
                ports = self.failing_observation(failure)
                result = run_background(ports)
                self.assert_safe_failure(result, "background_observation_failed")
                expected = {"stage": "background_observation_failed", "code": category}
                if status is not None:
                    expected["httpStatus"] = status
                self.assertEqual(result["adapterFailure"], expected)
                last = result["lastGoodObservation"]
                self.assertEqual(last["elapsedSeconds"], 30)
                self.assertFalse(last["snapshot"]["appVisible"])
                self.assertEqual(last["snapshot"]["progressCounter"], 1)
                self.assertEqual(len(last["snapshot"]), 11)
                self.assertTrue(result["resumed"])
                self.assertTrue(result["cleanupSucceeded"])

    def test_adapter_diagnostics_reject_hostile_codes_and_unbounded_http_status(self):
        for failure in [
            DriverFailure("private token sk-unlogged"),
            DriverFailure(["private token sk-unlogged"]),
            RuntimeError("private response body sk-unlogged"),
        ]:
            with self.subTest(kind=type(failure).__name__):
                result = run_background(self.failing_observation(failure))
                self.assert_safe_failure(result, "background_observation_failed")
                self.assertEqual(
                    result["adapterFailure"],
                    {
                        "stage": "background_observation_failed",
                        "code": "unexpected_exception",
                    },
                )
        for status in [True, "503", 99, 600, 10**1000]:
            with self.subTest(status_type=type(status).__name__):
                result = run_background(
                    self.failing_observation(ProtocolHTTPFailure(status))
                )
                self.assertEqual(
                    result["adapterFailure"],
                    {
                        "stage": "background_observation_failed",
                        "code": "protocol_response_invalid",
                    },
                )

    def test_original_diagnostics_are_not_overwritten_by_restoration_failures(self):
        ports = self.failing_observation(ProtocolHTTPFailure(503))

        def fail_resume():
            raise DriverFailure("app_launch_failed")

        def fail_cleanup():
            raise DriverFailure("owned_turn_cleanup_failed")

        ports.resume = fail_resume
        ports.cleanup_turn = fail_cleanup
        result = run_background(ports)
        self.assertEqual(
            result["adapterFailure"],
            {
                "stage": "background_observation_failed",
                "code": "protocol_response_invalid",
                "httpStatus": 503,
            },
        )
        self.assertEqual(result["lastGoodObservation"]["elapsedSeconds"], 30)
        self.assertEqual(
            result["errorCodes"],
            [
                "background_observation_failed",
                "background_resume_failed",
                "background_cleanup_failed",
            ],
        )

    def test_first_restoration_failure_has_diagnostics_without_replacing_last_live_snapshot(
        self,
    ):
        ports = FakePorts()

        def fail_resume():
            raise DriverFailure("app_launch_failed")

        ports.resume = fail_resume
        result = run_background(ports)
        self.assertEqual(
            result["adapterFailure"],
            {"stage": "background_resume_failed", "code": "app_launch_failed"},
        )
        self.assertEqual(result["lastGoodObservation"]["elapsedSeconds"], 1800)
        self.assertFalse(result["lastGoodObservation"]["snapshot"]["appVisible"])

    def test_no_good_snapshot_on_setup_failure_and_no_adapter_failure_on_success(self):
        ports = FakePorts()
        ports.fail_stage = "setup"
        result = run_background(ports)
        self.assertIsNone(result["lastGoodObservation"])
        self.assertEqual(
            result["adapterFailure"],
            {"stage": "background_setup_failed", "code": "unexpected_exception"},
        )
        result = run_background(FakePorts())
        self.assertIsNone(result["adapterFailure"])
        self.assertEqual(result["lastGoodObservation"]["elapsedSeconds"], 1800)

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
