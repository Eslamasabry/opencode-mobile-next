"""Conservative fresh-install qualification on a separately provisioned AVD.

Finish line: refuse shared devices/users before mutation, observe a verified
clean candidate install, and require truthful first-run projections. Non-goal:
create/switch/remove users, clear data, uninstall, enroll providers or wipe an
existing device. Root verifies the serial/boot identity and approved artifact.

Private runtime anchors use context.filesDir (BuiltinLinux.kt:42 and
PhoneAgentPaths.kt:11), but this is not whole-runtime user isolation:
android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/
  BuiltinLinux.kt:2115 binds the primary /storage/emulated/0 alias;
  MainActivity.kt:1478 and TermuxSetupRunner.kt:160 use /data/data/com.termux.
lib/builtin/builtin_linux.dart:316 and agents/phone_agents_host.dart:535 use
fixed loopback ports. These are feasibility limitations, not claims of a
reproduced secondary-user defect. An isolation boolean alone cannot qualify
those paths; user lifecycle operations remain excluded by the FQ9 contract.
"""

try:
    from .common import Artifact, CANDIDATE_BUILD, DriverFailure
except ImportError:
    from common import Artifact, CANDIDATE_BUILD, DriverFailure


_PLAN_FIELDS = frozenset(
    {
        "currentUser",
        "users",
        "maxUsers",
        "supportsManagedProfiles",
        "appUsesUserScopedRuntime",
    }
)
_FRESH_FIELDS = frozenset(
    {
        "dedicated",
        "currentUser",
        "packageInstalled",
        "appDataPresent",
    }
)
_FIRST_RUN_CODES = {
    "appVisible": "fresh_app_not_visible",
    "onboardingVisible": "fresh_onboarding_not_visible",
    "profilesEmpty": "fresh_profiles_not_empty",
    "setupAbsent": "fresh_setup_not_absent",
    "crashFree": "fresh_crash_free_not_verified",
}

FAIL_CODES = frozenset(
    {
        "fresh_plan_snapshot_invalid",
        "fresh_candidate_invalid",
        "fresh_snapshot_failed",
        "fresh_snapshot_invalid",
        "fresh_device_not_dedicated",
        "fresh_user_not_zero",
        "fresh_package_present",
        "fresh_app_data_present",
        "fresh_install_failed",
        "fresh_install_not_observed",
        "fresh_launch_failed",
        "fresh_first_run_snapshot_failed",
        "fresh_first_run_snapshot_invalid",
        *_FIRST_RUN_CODES.values(),
    }
)


def _fail(code):
    raise DriverFailure(code)


def _call(ports, method, code, *args):
    """Ports may fail with arbitrary exceptions; never copy their text."""
    try:
        return getattr(ports, method)(*args)
    except Exception:
        raise DriverFailure(code) from None


def plan_fresh(snapshot):
    """Return a read-only second-AVD plan, never Android user operations.

    Snapshot users are bounded integer IDs; capacity/profile support is only
    descriptive. Even a claimed user-scoped runtime requires separate evidence
    before this driver can authorize secondary-user lifecycle operations.
    """
    if type(snapshot) is not dict or set(snapshot) != _PLAN_FIELDS:
        _fail("fresh_plan_snapshot_invalid")
    users = snapshot["users"]
    current = snapshot["currentUser"]
    maximum = snapshot["maxUsers"]
    if (
        type(current) is not int
        or current < 0
        or current > 2147483647
        or type(maximum) is not int
        or not 1 <= maximum <= 128
        or type(users) is not list
        or not 1 <= len(users) <= 128
        or any(type(user) is not int or not 0 <= user <= 2147483647 for user in users)
        or len(set(users)) != len(users)
        or current not in users
        or type(snapshot["supportsManagedProfiles"]) is not bool
        or type(snapshot["appUsesUserScopedRuntime"]) is not bool
    ):
        _fail("fresh_plan_snapshot_invalid")
    return {
        "state": "needs_second_avd",
        "code": "shared_user_isolation_unqualified",
        "mutatesUsers": False,
        "requiresDedicatedAvd": True,
        "requiresUserZero": True,
        "requiresPackageAbsent": True,
        "requiresAppDataAbsent": True,
    }


def _fresh_snapshot(ports):
    snapshot = _call(ports, "fresh_snapshot", "fresh_snapshot_failed")
    if type(snapshot) is not dict or set(snapshot) != _FRESH_FIELDS:
        _fail("fresh_snapshot_invalid")
    if type(snapshot["currentUser"]) is not int or any(
        type(snapshot[field]) is not bool
        for field in ("dedicated", "packageInstalled", "appDataPresent")
    ):
        _fail("fresh_snapshot_invalid")
    if not snapshot["dedicated"]:
        _fail("fresh_device_not_dedicated")
    if snapshot["currentUser"] != 0:
        _fail("fresh_user_not_zero")
    return snapshot


def run_fresh(ports, candidate):
    """Run only on a root-verified dedicated, clean AVD; retain the candidate.

    Root owns artifact/hash/signer verification and the device lock. This
    driver does not convert a reused or wiped shared device into clean proof.
    It rechecks scope after install and after launch without restoration or
    deleting anything; unsuccessful observations remain fixed failures.
    """
    if (
        not isinstance(candidate, Artifact)
        or type(candidate.build) is not int
        or candidate.build != CANDIDATE_BUILD
        or candidate.origin != "coordinator-approved"
    ):
        _fail("fresh_candidate_invalid")
    build = candidate.build
    initial = _fresh_snapshot(ports)
    if initial["packageInstalled"]:
        _fail("fresh_package_present")
    if initial["appDataPresent"]:
        _fail("fresh_app_data_present")

    _call(ports, "install_fresh", "fresh_install_failed", candidate)
    installed = _fresh_snapshot(ports)
    if not installed["packageInstalled"]:
        _fail("fresh_install_not_observed")
    _call(ports, "launch", "fresh_launch_failed")
    launched = _fresh_snapshot(ports)
    if not launched["packageInstalled"]:
        _fail("fresh_install_not_observed")
    first = _call(ports, "first_run_snapshot", "fresh_first_run_snapshot_failed")
    if (
        type(first) is not dict
        or set(first) != set(_FIRST_RUN_CODES)
        or any(type(value) is not bool for value in first.values())
    ):
        _fail("fresh_first_run_snapshot_invalid")
    for field, code in _FIRST_RUN_CODES.items():
        if not first[field]:
            _fail(code)
    return {
        "state": "pass",
        "code": "verified",
        "candidateBuild": build,
        "dedicated": True,
        "userZero": True,
        "cleanInstall": True,
        "installed": True,
        "candidateRetained": True,
        "firstRun": dict(first),
    }
