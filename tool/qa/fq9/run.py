"""FQ9 checklist CLI. Default is an offline plan; --execute is device-only.

Device runs use coordinator-posted APK 2198. No builds, downloads, user
creation, uninstall, clearing, credential output or network uploads.
"""

import argparse
import fcntl
import json
from pathlib import Path
import re
import subprocess
import sys

from . import background, fixture, fresh, ports, upgrade
from .common import (
    CANDIDATE_BUILD,
    LOCK,
    PACKAGE,
    SHARED_SERIAL,
    DriverFailure,
    load_manifest,
    load_session_receipt,
)

FAIL_CODES = (
    ports.FAIL_CODES
    | upgrade.FAIL_CODES
    | background.FAIL_CODES
    | fresh.FAIL_CODES
    | frozenset(
        (
            "driver_failure",
            "driver_unexpected_failure",
            "manifest_required",
            "baseline_artifact_required",
            "emulator_busy",
            "evidence_unavailable",
            "normal_restore_failed",
            "device_identity_changed",
            "invalid_run_id",
            "invalid_serial",
            "dedicated_avd_required",
            "input_too_large",
            "duplicate_input_field",
            "input_unavailable_or_invalid",
            "invalid_manifest",
            "invalid_artifact",
            "invalid_artifact_identity",
            "normal_candidate_mismatch",
            "unapproved_candidate_signer",
            "published_stable_receipt_required",
            "unexpected_published_source",
            "invalid_session_receipt",
            "invalid_session_directory",
            "duplicate_session_receipt",
            "fixture_title_mismatch",
            "session_receipt_required",
            "evidence_already_exists",
            "discovery_arguments_invalid",
            "fixture_seed_arguments_invalid",
            "fixture_receipt_unavailable",
        )
    )
)


def failure_code(error):
    return (
        error.code
        if isinstance(error, DriverFailure) and error.code in FAIL_CODES
        else "driver_unexpected_failure"
    )


def plan(args, artifacts=None):
    result = {
        "schema": 1,
        "state": "plan",
        "deviceTouched": False,
        "case": args.case,
        "requiredCandidateBuild": CANDIDATE_BUILD,
        "package": PACKAGE,
        "requirements": [
            "coordinator_posted_apk_2198",
            "reviewed_artifact_receipt",
            "same_signer_before_update",
            "shared_emulator_lock",
            "preserve_application_data",
            "normal_candidate_restoration",
        ],
    }
    if args.case in ("upgrade", "stable"):
        result["requirements"] += [
            "exact_installed_baseline",
            "seeded_preferences_profiles_and_sign_in",
            "preservation_history_receipt",
            "manual_keystore_and_history_check",
        ]
        if args.case == "stable":
            result["requirements"] += [
                "reviewed_published_stable_1_2_0_apk",
                "second_avd_if_shared_baseline_is_newer",
            ]
        if getattr(args, "seed_history_receipt", None):
            result["fixtureSeeding"] = {
                "engineScope": "app_managed_oc1",
                "projectBacking": "files/projects",
                "guestRoot": "/root/projects",
                "manualReopenAndCleanupRequired": True,
            }
        baseline = "stable" if args.case == "stable" else "previous"
        if artifacts and baseline in artifacts:
            result["signerCompatible"] = (
                artifacts[baseline].signer == artifacts["candidate"].signer
            )
            if not result["signerCompatible"]:
                result["executionBlocked"] = "baseline_candidate_signer_mismatch"
    elif args.case == "background":
        result["requirements"] += [
            "app_started_owned_oc1_fixture",
            "sleep_120_tool_transitions",
            "background_service_and_ongoing_notification",
            "book_30_minute_lock",
        ]
        result.update(
            checkpointsSeconds=[300, 1800], engineScope="opencode_1_18_32_app_managed"
        )
    else:
        result["freshPlan"] = fresh.plan_fresh(
            {
                "currentUser": 0,
                "users": [0],
                "maxUsers": 1,
                "supportsManagedProfiles": False,
                "appUsesUserScopedRuntime": False,
            }
        )
        result["requirements"] += [
            "separately_provisioned_avd",
            "no_prior_package_or_data",
            "first_run_onboarding",
        ]
    return result


def exact_identity(actual, artifact):
    return type(actual) is dict and all(
        actual.get(k) == getattr(artifact, k)
        for k in ("build", "version", "sha256", "signer")
    )


def run_locked(args, artifacts, receipt, output, *, port_factory=ports.AndroidPorts):
    result = {
        "schema": 1,
        "case": args.case,
        "runID": args.run_id,
        "candidateBuild": CANDIDATE_BUILD,
        "candidateSha256": artifacts["candidate"].sha256,
        "signerSha256": artifacts["candidate"].signer,
        "device": args.serial,
        "state": "fail",
        "deviceTouched": False,
        "normalRestored": False,
        "deviceQualified": False,
        "automatedChecksPassed": False,
        "manualChecksPending": args.case in ("upgrade", "stable"),
        "sourceRevision": source_revision(),
    }
    device = port_factory(
        args.serial,
        args.run_id,
        history=receipt if args.case in ("upgrade", "stable") else None,
        turn=receipt if args.case == "background" else None,
        dedicated_avd=args.dedicated_avd,
    )
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    with LOCK.open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise DriverFailure("emulator_busy") from None
        device.locked = True
        try:
            result["deviceTouched"] = True
            device.device_ready()
            if args.case == "fresh":
                # The clean AVD must remain clean until run_fresh's fences pass.
                result["driver"] = fresh.run_fresh(device, artifacts["candidate"])
            elif args.case in ("upgrade", "stable"):
                baseline = artifacts["stable" if args.case == "stable" else "previous"]
                seed_output = getattr(args, "seed_history_receipt", None)
                if seed_output is not None:
                    if not exact_identity(device.installed_identity(), baseline):
                        raise DriverFailure("upgrade_previous_mismatch")
                    fixture.seed_history_fixture(
                        device, lambda value: write_private_receipt(seed_output, value)
                    )
                    result["historySeeded"] = True
                    result["fixtureUsesAppProjectBacking"] = True
                result["driver"] = upgrade.run_upgrade(
                    device,
                    baseline,
                    artifacts["candidate"],
                    published=args.case == "stable",
                )
            else:
                if not exact_identity(
                    device.installed_identity(), artifacts["candidate"]
                ):
                    raise DriverFailure("device_identity_changed")
                if getattr(args, "discover_turn_receipt", None):
                    value = device.find_live_receipt(args.directory)
                    write_report(args.discover_turn_receipt, value)
                    result["readinessOnly"] = True
                    result["driver"] = {
                        "state": "ready",
                        "code": "fixture_receipt_written",
                    }
                else:
                    result["driver"] = background.run_background(device)
            # Driver-level failure may be returned (background), not raised.
            result["state"] = result["driver"]["state"]
        except Exception as error:
            result.update(state="fail", code=failure_code(error))
        finally:
            try:
                device.close_protocol()
            except Exception as error:
                result.update(state="fail", protocolCleanupError=failure_code(error))
            if device.mutated:
                try:
                    device.restore_normal(artifacts["normal"])
                    result["normalRestored"] = True
                except Exception:
                    result.update(state="fail", restoreError="normal_restore_failed")
            elif args.case == "background" and "driver" in result:
                # No APK mutation was required: verify the candidate still
                # installed, rather than interrupt a failed/unclean live turn.
                try:
                    result["normalRestored"] = exact_identity(
                        device.installed_identity(), artifacts["normal"]
                    )
                    if not result["normalRestored"]:
                        result.update(
                            state="fail", restoreError="normal_restore_failed"
                        )
                except Exception:
                    result.update(state="fail", restoreError="normal_restore_failed")
            result["automatedChecksPassed"] = (
                result["state"] == "pass" and result["normalRestored"]
            )
            result["deviceQualified"] = (
                result["automatedChecksPassed"] and not result["manualChecksPending"]
            )
            # Evidence write and restoration finish while the lock is owned.
            write_report(output, result)
            device.locked = False
            fcntl.flock(handle, fcntl.LOCK_UN)
    return result


def source_revision():
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"], capture_output=True, timeout=5, check=False
        )
        revision = result.stdout.decode().strip()
        return (
            revision
            if result.returncode == 0 and re.fullmatch("[0-9a-f]{40}", revision)
            else None
        )
    except (OSError, ValueError, subprocess.TimeoutExpired):
        return None


def write_report(output, report):
    # Output is a closed projection assembled here/by the pure drivers. Never
    # dump port snapshots, exception objects, argv or raw subprocess output.
    try:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x") as stream:
            json.dump(report, stream, indent=2)
            stream.write("\n")
    except FileExistsError:
        raise DriverFailure("evidence_already_exists") from None
    except OSError:
        raise DriverFailure("evidence_unavailable") from None


def write_private_receipt(output, receipt):
    """Exclusive private identity receipt; never contains a password or key."""
    import os

    try:
        fd = os.open(output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, "w") as stream:
            json.dump(receipt, stream, indent=2)
            stream.write("\n")
    except FileExistsError:
        raise DriverFailure("evidence_already_exists") from None
    except OSError:
        raise DriverFailure("fixture_receipt_unavailable") from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--case", choices=("upgrade", "stable", "background", "fresh"), required=True
    )
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--session-receipt", type=Path)
    parser.add_argument("--seed-history-receipt", type=Path)
    parser.add_argument("--discover-turn-receipt", type=Path)
    parser.add_argument("--directory")
    parser.add_argument("--serial", default=SHARED_SERIAL)
    parser.add_argument("--dedicated-avd")
    parser.add_argument("--run-id", default="fq9-plan")
    parser.add_argument("--output", type=Path, default=Path("docs/qa/FQ9-2026-10-08"))
    args = parser.parse_args(argv)
    try:
        if not re.fullmatch(r"fq9-[A-Za-z0-9_-]{1,80}", args.run_id):
            raise DriverFailure("invalid_run_id")
        if not re.fullmatch(r"emulator-[0-9]{4,5}", args.serial):
            raise DriverFailure("invalid_serial")
        if args.serial != SHARED_SERIAL and (
            type(args.dedicated_avd) is not str
            or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.dedicated_avd)
        ):
            raise DriverFailure("dedicated_avd_required")
        artifacts = load_manifest(args.manifest) if args.manifest else None
        if args.seed_history_receipt and (
            args.case != "upgrade"
            or args.session_receipt is not None
            or not args.seed_history_receipt.is_absolute()
            or args.seed_history_receipt.resolve().is_relative_to(Path.cwd().resolve())
        ):
            raise DriverFailure("fixture_seed_arguments_invalid")
        if args.seed_history_receipt and args.seed_history_receipt.exists():
            raise DriverFailure("evidence_already_exists")
        if args.discover_turn_receipt and (
            args.case != "background"
            or args.session_receipt is not None
            or not args.discover_turn_receipt.is_absolute()
            or type(args.directory) is not str
            or not re.fullmatch(r"/root/projects/[A-Za-z0-9_-]{1,100}", args.directory)
        ):
            raise DriverFailure("discovery_arguments_invalid")
        if not args.execute:
            print(json.dumps(plan(args, artifacts), indent=2))
            return 0
        if artifacts is None:
            raise DriverFailure("manifest_required")
        baseline = "stable" if args.case == "stable" else "previous"
        if args.case in ("upgrade", "stable") and baseline not in artifacts:
            raise DriverFailure("baseline_artifact_required")
        if args.case == "fresh" and args.serial == SHARED_SERIAL:
            raise DriverFailure("dedicated_avd_required")
        receipt = None
        if (
            args.case != "fresh"
            and not args.discover_turn_receipt
            and not args.seed_history_receipt
        ):
            if args.session_receipt is None:
                raise DriverFailure("session_receipt_required")
            receipt = load_session_receipt(
                args.session_receipt, run_id=args.run_id, live=args.case == "background"
            )
        # Offline receipt failures precede APK tools, lock and ADB. Incompatible
        # signer paths stop here; never establish stable by downgrading modern.
        if (
            args.case in ("upgrade", "stable")
            and artifacts[baseline].signer != artifacts["candidate"].signer
        ):
            raise DriverFailure("upgrade_signer_mismatch")
        for name in (
            set(("candidate", "normal", baseline))
            if args.case in ("upgrade", "stable")
            else ("candidate", "normal")
        ):
            ports.verify_artifact(artifacts[name])
        suffix = "background-ready" if args.discover_turn_receipt else args.case
        output = args.output / f"{args.run_id}-{suffix}.json"
        if output.exists():
            raise DriverFailure("evidence_already_exists")
        result = run_locked(args, artifacts, receipt, output)
        print(json.dumps(result, indent=2))
        ready = (
            result.get("readinessOnly")
            and result["state"] == "ready"
            and result["normalRestored"]
        )
        return 0 if result["automatedChecksPassed"] or ready else 1
    except Exception as error:
        print(
            json.dumps(
                {
                    "schema": 1,
                    "state": "blocked",
                    "deviceQualified": False,
                    "code": failure_code(error),
                },
                indent=2,
            )
        )
        return 1


if __name__ == "__main__":
    sys.exit(main())
