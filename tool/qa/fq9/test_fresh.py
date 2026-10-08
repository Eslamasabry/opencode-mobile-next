"""Pure fresh-driver safety tests; fake ports perform no device work."""

import copy
from dataclasses import replace
from pathlib import Path
import unittest

try:
    from .common import Artifact, CANDIDATE_BUILD, DriverFailure, LOCAL_SIGNER
    from .fresh import FAIL_CODES, plan_fresh, run_fresh
except ImportError:
    from common import Artifact, CANDIDATE_BUILD, DriverFailure, LOCAL_SIGNER
    from fresh import FAIL_CODES, plan_fresh, run_fresh


def candidate(build=CANDIDATE_BUILD):
    # Root verifies the complete Artifact before invoking this driver.
    return Artifact(
        Path(f"/reviewed/oc-{CANDIDATE_BUILD}.apk"),
        build,
        "1.2.0",
        "a" * 64,
        LOCAL_SIGNER,
        "coordinator-approved",
    )


def plan_snapshot(**changes):
    return {
        "currentUser": 0,
        "users": [0],
        "maxUsers": 4,
        "supportsManagedProfiles": True,
        "appUsesUserScopedRuntime": False,
        **changes,
    }


class FakePorts:
    def __init__(self):
        self.calls = []
        self.snapshot = {
            "dedicated": True,
            "currentUser": 0,
            "packageInstalled": False,
            "appDataPresent": False,
        }
        self.first = {
            "appVisible": True,
            "onboardingVisible": True,
            "profilesEmpty": True,
            "setupAbsent": True,
            "crashFree": True,
        }
        self.fail_at = None
        self.after_install = {}
        self.after_launch = {}
        self.install_observed = True
        self.installed_artifact = None

    def _record(self, method):
        self.calls.append(method)
        if self.fail_at == method:
            raise RuntimeError("DO-NOT-PRINT /private/path credential=secret")

    def fresh_snapshot(self):
        self._record("fresh_snapshot")
        return copy.deepcopy(self.snapshot)

    def install_fresh(self, artifact):
        self._record("install_fresh")
        self.installed_artifact = artifact
        self.snapshot["packageInstalled"] = self.install_observed
        self.snapshot.update(self.after_install)

    def launch(self):
        self._record("launch")
        self.snapshot["appDataPresent"] = True
        self.snapshot.update(self.after_launch)

    def first_run_snapshot(self):
        self._record("first_run_snapshot")
        return copy.deepcopy(self.first)

    def __getattr__(self, name):
        # Any cleanup/user/clear-data/install-update call is a test failure.
        raise AssertionError("Unexpected port outside fresh contract")


class FreshTest(unittest.TestCase):
    def assert_failure(self, code, operation):
        with self.assertRaises(DriverFailure) as caught:
            operation()
        self.assertEqual(caught.exception.code, code)
        self.assertIn(code, FAIL_CODES)
        self.assertNotIn("DO-NOT-PRINT", str(caught.exception))
        self.assertNotIn("credential", str(caught.exception))

    def test_every_valid_plan_requires_second_avd_without_mutating_snapshot(self):
        for snapshot in (
            plan_snapshot(),
            plan_snapshot(maxUsers=1, supportsManagedProfiles=False),
            plan_snapshot(appUsesUserScopedRuntime=True),
            plan_snapshot(currentUser=10, users=[0, 10]),
            plan_snapshot(users=[0, 10], maxUsers=1),
        ):
            before = copy.deepcopy(snapshot)
            result = plan_fresh(snapshot)
            self.assertEqual(result["state"], "needs_second_avd")
            self.assertFalse(result["mutatesUsers"])
            self.assertTrue(result["requiresDedicatedAvd"])
            self.assertTrue(result["requiresUserZero"])
            self.assertEqual(snapshot, before)

    def test_invalid_or_unknown_plan_facts_do_not_grant_isolation(self):
        for changes in (
            {"currentUser": True},
            {"currentUser": -1},
            {"users": [0, True]},
            {"users": [0, 0]},
            {"users": []},
            {"maxUsers": 0},
            {"maxUsers": "4"},
            {"supportsManagedProfiles": "true"},
            {"appUsesUserScopedRuntime": None},
            {"credential": "DO-NOT-PRINT"},
        ):
            self.assert_failure(
                "fresh_plan_snapshot_invalid",
                lambda: plan_fresh(plan_snapshot(**changes)),
            )
        incomplete = plan_snapshot()
        del incomplete["appUsesUserScopedRuntime"]
        self.assert_failure(
            "fresh_plan_snapshot_invalid", lambda: plan_fresh(incomplete)
        )

    def test_dedicated_clean_first_run_retains_exact_candidate(self):
        ports = FakePorts()
        artifact = candidate()
        result = run_fresh(ports, artifact)
        self.assertEqual(result["state"], "pass")
        self.assertTrue(result["cleanInstall"])
        self.assertTrue(result["candidateRetained"])
        self.assertEqual(result["firstRun"], ports.first)
        self.assertIs(ports.installed_artifact, artifact)
        self.assertEqual(
            ports.calls,
            [
                "fresh_snapshot",
                "install_fresh",
                "fresh_snapshot",
                "launch",
                "fresh_snapshot",
                "first_run_snapshot",
            ],
        )
        self.assertTrue(ports.snapshot["packageInstalled"])
        ports.first["crashFree"] = False
        self.assertTrue(result["firstRun"]["crashFree"])

    def test_shared_users_and_existing_package_or_data_refuse_before_mutation(self):
        for changes, code in (
            ({"dedicated": False}, "fresh_device_not_dedicated"),
            ({"currentUser": 10}, "fresh_user_not_zero"),
            ({"packageInstalled": True}, "fresh_package_present"),
            ({"appDataPresent": True}, "fresh_app_data_present"),
        ):
            ports = FakePorts()
            ports.snapshot.update(changes)
            self.assert_failure(code, lambda: run_fresh(ports, candidate()))
            self.assertEqual(ports.calls, ["fresh_snapshot"])

    def test_unknown_or_incomplete_absence_is_not_a_clean_install(self):
        for field in FakePorts().snapshot:
            for value in (None, "false", 0.0):
                ports = FakePorts()
                ports.snapshot[field] = value
                self.assert_failure(
                    "fresh_snapshot_invalid", lambda: run_fresh(ports, candidate())
                )
                self.assertEqual(ports.calls, ["fresh_snapshot"])
            ports = FakePorts()
            del ports.snapshot[field]
            self.assert_failure(
                "fresh_snapshot_invalid", lambda: run_fresh(ports, candidate())
            )

    def test_wrong_candidate_is_refused_before_device_observation(self):
        for build in (2196, 2197, True, str(CANDIDATE_BUILD), float(CANDIDATE_BUILD)):
            ports = FakePorts()
            self.assert_failure(
                "fresh_candidate_invalid", lambda: run_fresh(ports, candidate(build))
            )
            self.assertEqual(ports.calls, [])
        for artifact in (
            None,
            object(),
            replace(candidate(), origin="previous-approved"),
        ):
            ports = FakePorts()
            self.assert_failure(
                "fresh_candidate_invalid", lambda: run_fresh(ports, artifact)
            )
            self.assertEqual(ports.calls, [])

    def test_failed_install_is_not_assumed_to_have_installed(self):
        ports = FakePorts()
        ports.install_observed = False
        self.assert_failure(
            "fresh_install_not_observed", lambda: run_fresh(ports, candidate())
        )
        self.assertNotIn("launch", ports.calls)

    def test_scope_changes_after_install_or_launch_stay_failed(self):
        for stage in ("after_install", "after_launch"):
            for changes, code in (
                ({"dedicated": False}, "fresh_device_not_dedicated"),
                ({"currentUser": 10}, "fresh_user_not_zero"),
                ({"packageInstalled": False}, "fresh_install_not_observed"),
            ):
                ports = FakePorts()
                setattr(ports, stage, changes)
                self.assert_failure(code, lambda: run_fresh(ports, candidate()))
                self.assertNotIn("first_run_snapshot", ports.calls)
                if stage == "after_install":
                    self.assertNotIn("launch", ports.calls)

    def test_each_first_run_claim_requires_true_observation(self):
        codes = {
            "appVisible": "fresh_app_not_visible",
            "onboardingVisible": "fresh_onboarding_not_visible",
            "profilesEmpty": "fresh_profiles_not_empty",
            "setupAbsent": "fresh_setup_not_absent",
            "crashFree": "fresh_crash_free_not_verified",
        }
        for field, code in codes.items():
            ports = FakePorts()
            ports.first[field] = False
            self.assert_failure(code, lambda: run_fresh(ports, candidate()))
            for value in (None, "true", 1):
                ports = FakePorts()
                ports.first[field] = value
                self.assert_failure(
                    "fresh_first_run_snapshot_invalid",
                    lambda: run_fresh(ports, candidate()),
                )
            ports = FakePorts()
            del ports.first[field]
            self.assert_failure(
                "fresh_first_run_snapshot_invalid",
                lambda: run_fresh(ports, candidate()),
            )

    def test_port_errors_are_fixed_redacted_and_never_trigger_destructive_cleanup(self):
        for method, code in (
            ("fresh_snapshot", "fresh_snapshot_failed"),
            ("install_fresh", "fresh_install_failed"),
            ("launch", "fresh_launch_failed"),
            ("first_run_snapshot", "fresh_first_run_snapshot_failed"),
        ):
            ports = FakePorts()
            ports.fail_at = method
            self.assert_failure(code, lambda: run_fresh(ports, candidate()))
            self.assertEqual(ports.calls[-1], method)


if __name__ == "__main__":
    unittest.main()
