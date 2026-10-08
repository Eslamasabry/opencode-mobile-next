"""Observe one operator-owned app-managed turn across a bounded HOME dwell.

The caller owns APK/package/runtime identity checks and the emulator lock.
Ports bind every observation and cleanup to the same dedicated fixture turn.
No HTTP prompt, heartbeat, foreground launch, or real clock is supplied here.
"""

import math

try:
    from .common import DriverFailure
except ImportError:
    from common import DriverFailure


_BOOLS = (
    "ownedTurn",
    "appManaged",
    "appVisible",
    "appAlive",
    "serverAlive",
    "foregroundService",
    "ongoingNotification",
    "turnActive",
    "completed",
    "failed",
)

FAIL_CODES = frozenset(
    {
        "background_plan_invalid",
        "background_snapshot_invalid",
        "background_fixture_not_owned",
        "background_fixture_not_app_managed",
        "background_app_lost",
        "background_server_lost",
        "background_service_lost",
        "background_notification_lost",
        "background_turn_failed",
        "background_turn_finished_early",
        "background_turn_inactive",
        "background_initial_app_not_visible",
        "background_app_still_visible",
        "background_real_progress_missing",
        "background_checkpoint_progress_missing",
        "background_clock_invalid",
        "background_clock_stalled",
        "background_progress_regressed",
        "background_setup_failed",
        "background_home_failed",
        "background_observation_failed",
        "background_sleep_failed",
        "background_resume_failed",
        "background_cleanup_failed",
    }
)


def _snapshot(value):
    if not isinstance(value, dict):
        raise DriverFailure("background_snapshot_invalid")
    if any(type(value.get(key)) is not bool for key in _BOOLS):
        raise DriverFailure("background_snapshot_invalid")
    count = value.get("progressCounter")
    if type(count) is not int or not 0 <= count <= 2**53 - 1:
        raise DriverFailure("background_snapshot_invalid")
    return {key: value[key] for key in (*_BOOLS, "progressCounter")}


def _number(value):
    if type(value) is int:
        return -(2**53) < value < 2**53
    return type(value) is float and math.isfinite(value)


def _check_live(snapshot, *, background=False, initial=False):
    for field, code in (
        ("ownedTurn", "background_fixture_not_owned"),
        ("appManaged", "background_fixture_not_app_managed"),
        ("appAlive", "background_app_lost"),
        ("serverAlive", "background_server_lost"),
        ("foregroundService", "background_service_lost"),
        ("ongoingNotification", "background_notification_lost"),
    ):
        if not snapshot[field]:
            raise DriverFailure(code)
    if snapshot["failed"]:
        raise DriverFailure("background_turn_failed")
    if snapshot["completed"]:
        raise DriverFailure("background_turn_finished_early")
    if not snapshot["turnActive"]:
        raise DriverFailure("background_turn_inactive")
    if initial and not snapshot["appVisible"]:
        raise DriverFailure("background_initial_app_not_visible")
    if background and snapshot["appVisible"]:
        raise DriverFailure("background_app_still_visible")
    if snapshot["progressCounter"] == 0:
        raise DriverFailure("background_real_progress_missing")


def run_background(ports, *, checkpoints=(300, 1800), poll_seconds=30):
    """Return safe qualification and restoration facts, including failures.

    Ports must bound each operation themselves; Python cannot preempt a hung
    device adapter. Clock and sleep are injected, so offline tests use virtual
    time. Initial real tool progress excludes admission/retry-only fixtures.
    Each checkpoint requires newer transitions than the previous checkpoint;
    use an operator-started fixture with 20 sequential sleep-120 tool calls.
    Qualification requires the turn to stay active until the last checkpoint;
    a completed result is accepted only after the explicit resume operation.
    Cleanup is attempted only after positive dedicated ownership and runtime
    scope evidence, and restoration errors cannot preserve a pass.
    """
    result = {
        "state": "fail",
        "code": "background_plan_invalid",
        "checkpoints": [],
        "dwellSeconds": 0,
        "homeAttempted": False,
        "resumed": False,
        "cleanupAttempted": False,
        "cleanupSucceeded": False,
        "errorCodes": [],
    }
    if not isinstance(checkpoints, (tuple, list)) or not checkpoints:
        result["errorCodes"] = ["background_plan_invalid"]
        return result
    if (
        any(not _number(x) or not 0 < x <= 1800 for x in checkpoints)
        or any(a >= b for a, b in zip(checkpoints, checkpoints[1:]))
        or not _number(poll_seconds)
        or not 0 < poll_seconds <= 30
    ):
        result["errorCodes"] = ["background_plan_invalid"]
        return result

    cleanup_allowed = False
    previous_time = None
    stage = "background_setup_failed"

    def clock():
        nonlocal previous_time
        value = ports.monotonic()
        if (
            not _number(value)
            or value < 0
            or (previous_time is not None and value < previous_time)
        ):
            raise DriverFailure("background_clock_invalid")
        previous_time = value
        return value

    try:
        raw = ports.live_snapshot()
        # Ownership is sufficient for exact cleanup even if later setup checks
        # fail. Never use a truthy string or an unrelated turn as authorization.
        cleanup_allowed = (
            isinstance(raw, dict)
            and raw.get("ownedTurn") is True
            and raw.get("appManaged") is True
        )
        initial = _snapshot(raw)
        _check_live(initial, initial=True)
        baseline = initial["progressCounter"]
        previous_count = baseline
        checkpoint_count = baseline
        stage = "background_home_failed"
        result["homeAttempted"] = True
        ports.home()
        began = clock()
        stage = "background_observation_failed"
        current = _snapshot(ports.live_snapshot())
        _check_live(current, background=True)
        if current["progressCounter"] < previous_count:
            raise DriverFailure("background_progress_regressed")
        previous_count = current["progressCounter"]
        for checkpoint in checkpoints:
            while True:
                elapsed = clock() - began
                result["dwellSeconds"] = elapsed
                if elapsed >= checkpoint:
                    break
                step = min(poll_seconds, checkpoint - elapsed)
                before = previous_time
                stage = "background_sleep_failed"
                ports.sleep(step)
                stage = "background_observation_failed"
                if clock() <= before:
                    raise DriverFailure("background_clock_stalled")
                result["dwellSeconds"] = previous_time - began
                current = _snapshot(ports.live_snapshot())
                _check_live(current, background=True)
                if current["progressCounter"] < previous_count:
                    raise DriverFailure("background_progress_regressed")
                previous_count = current["progressCounter"]
            # A slow adapter read must not make a pre-deadline snapshot count as
            # proof at the checkpoint. Re-observe at or after its deadline.
            current = _snapshot(ports.live_snapshot())
            _check_live(current, background=True)
            if current["progressCounter"] < previous_count:
                raise DriverFailure("background_progress_regressed")
            previous_count = current["progressCounter"]
            if previous_count <= checkpoint_count:
                raise DriverFailure("background_checkpoint_progress_missing")
            checkpoint_count = previous_count
            elapsed = clock() - began
            result["dwellSeconds"] = elapsed
            result["checkpoints"].append(
                {
                    "seconds": checkpoint,
                    "elapsedSeconds": elapsed,
                    "background": True,
                    "turnActive": True,
                    "progressDelta": previous_count - baseline,
                }
            )
        result["state"] = "pass"
        result["code"] = "verified"
    except DriverFailure as error:
        # Only driver-authored codes escape. A port may also throw DriverFailure
        # carrying an arbitrary server/command detail, which is never exported.
        result["code"] = error.code if error.code in FAIL_CODES else stage
    except Exception:
        result["code"] = stage
    finally:
        if result["state"] == "fail":
            result["errorCodes"].append(result["code"])
        if result["homeAttempted"]:
            try:
                ports.resume()
                final = _snapshot(ports.live_snapshot())
                if (
                    not final["ownedTurn"]
                    or not final["appManaged"]
                    or not final["appVisible"]
                    or not final["appAlive"]
                    or not final["serverAlive"]
                    or final["failed"]
                    or not (final["turnActive"] or final["completed"])
                    or final["progressCounter"] < initial["progressCounter"]
                ):
                    raise DriverFailure("background_resume_failed")
                result["resumed"] = True
            except Exception:
                result["errorCodes"].append("background_resume_failed")
        if cleanup_allowed:
            result["cleanupAttempted"] = True
            try:
                ports.cleanup_turn()
                result["cleanupSucceeded"] = True
            except Exception:
                result["errorCodes"].append("background_cleanup_failed")
        if result["errorCodes"]:
            result["state"] = "fail"
            result["code"] = result["errorCodes"][0]
    return result
