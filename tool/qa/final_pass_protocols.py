"""Final-pass adapters for the pinned FQ3/FQ9 drivers and reviewed next upgrade.

FQ9 rows require {manifest, session_receipt} or upgrade-only
{manifest, seed_history_receipt}. The seed path must be new and private, outside
the checkout; seeding happens only inside the inherited device reservation. Optional run_id must be the fixture's fq9-prefixed run ID (otherwise
context.run_id is used). Upgrade needs a reviewed previous artifact and history
receipt. This row runs before candidate installation so the driver can verify
the actual installed baseline; the adapter never downgrades to manufacture one.
Background needs one operator-started OC1 turn titled
<run_id>-background; a single invocation observes both 5/30-minute checkpoints.

The context supplies root/output/candidate Paths, candidate_build, run_id,
adopt_lock(module), and capture(callable). adopt_lock must temporarily replace
ONLY the driver's LOCK/fcntl references, validate the inherited descriptor,
and retain the outer lock when the driver requests LOCK_UN. capture returns
the callable's value while capturing output privately. No standalone locking
CLI, fixture creation, provider credential read, build, or download is added.
FQ3 borrows the same descriptor and runs its phases in-process without nested flock.
"""

import math
from pathlib import Path
import re


def _result(status, reason, receipts=(), **data):
    result = {
        "status": status,
        "reason": reason,
        "receipts": [str(p) for p in receipts if p.is_file() and not p.is_symlink()],
    }
    if data:
        result["data"] = data
    return result


def _file(value):
    if not isinstance(value, (str, Path)):
        raise ValueError()
    path = Path(value)
    if not path.is_absolute() or path.is_symlink() or not path.is_file():
        raise ValueError()
    return path.resolve(strict=True)


def _number(value):
    if type(value) is int:
        return 0 <= value < 2**53
    return type(value) is float and math.isfinite(value) and 0 <= value < 2**53


def _background_facts(report, terminal):
    """Validate and export only closed checkpoint facts, never raw snapshots."""
    driver = report.get("driver")
    if type(driver) is not dict or type(terminal) is not dict:
        return None
    checkpoints = driver.get("checkpoints")
    if type(checkpoints) is not list or len(checkpoints) != 2:
        return None
    facts = []
    for point, seconds in zip(checkpoints, (300, 1800)):
        if (
            type(point) is not dict
            or type(point.get("seconds")) is not int
            or point["seconds"] != seconds
            or not _number(point.get("elapsedSeconds"))
            or point["elapsedSeconds"] < seconds
            or point.get("background") is not True
            or point.get("turnActive") is not True
            or type(point.get("progressDelta")) is not int
            or not 0 < point["progressDelta"] < 2**53
        ):
            return None
        facts.append({key: point[key] for key in (
            "seconds", "elapsedSeconds", "background", "turnActive", "progressDelta"
        )})
    if (
        driver.get("state") != "pass"
        or driver.get("code") != "verified"
        or not _number(driver.get("dwellSeconds"))
        or driver["dwellSeconds"] < 1800
        or any(driver.get(k) is not True for k in (
            "homeAttempted", "resumed", "cleanupAttempted", "cleanupSucceeded",
            "evidenceCaptured",
        ))
        or driver.get("sessionRetained") is not False
        or driver.get("errorCodes") != []
        or terminal.get("beforeResumeAndCleanup") is not True
        or terminal.get("windowReached") is not True
        or terminal.get("windowEndReached") is not True
        or terminal.get("errors") != []
        or not _number(terminal.get("capturedThroughElapsedSeconds"))
        or terminal["capturedThroughElapsedSeconds"] < 1800
        or not isinstance(terminal.get("terminal"), list)
        or not terminal["terminal"]
        or not isinstance(terminal.get("logs"), list)
        or not terminal["logs"]
    ):
        return None
    return dict(checkpoints=facts, dwellSeconds=driver["dwellSeconds"],
                resumed=True, cleanupSucceeded=True, evidenceCaptured=True,
                sessionRetained=False)


def run(row, config, context):
    """Return pass/fail/blocked with fixed reasons and existing receipt paths."""
    if row == "fq3":
        return _fq3(config, context)
    if row not in ("fq9-upgrade", "fq9-background"):
        return _result("blocked", "protocol_row_unsupported")
    next_upgrade = (
        row in ("fq9-upgrade", "fq9-background")
        and type(context.candidate_build) is int
        and 2202 < context.candidate_build <= 999999
    )
    if type(context.candidate_build) is not int or (
        context.candidate_build != 2202 and not next_upgrade
    ):
        return _result("blocked", "fq9_requires_build_2202")
    if not callable(getattr(context, "adopt_lock", None)) or not callable(
        getattr(context, "capture", None)
    ):
        return _result("blocked", "fq9_inherited_lock_unavailable")

    from tool.qa.fq9 import common, run as driver

    if driver.CANDIDATE_BUILD != 2202:
        return _result("blocked", "fq9_driver_build_mismatch")
    case = row.removeprefix("fq9-")
    try:
        if type(config) is not dict or set(config) - {
            "manifest",
            "session_receipt",
            "seed_history_receipt",
            "run_id",
        }:
            raise ValueError()
        manifest = _file(config.get("manifest"))
        seed = "seed_history_receipt" in config
        if seed:
            if case != "upgrade" or "session_receipt" in config:
                raise ValueError()
            raw = config["seed_history_receipt"]
            if not isinstance(raw, (str, Path)):
                raise ValueError()
            session_receipt = Path(raw)
            if (
                not session_receipt.is_absolute()
                or session_receipt.exists()
                or any(
                    p.is_symlink() for p in (session_receipt, *session_receipt.parents)
                )
                or not session_receipt.parent.is_dir()
                or session_receipt.resolve().is_relative_to(context.root.resolve())
            ):
                raise ValueError()
        else:
            session_receipt = _file(config.get("session_receipt"))
        candidate = _file(context.candidate)
        run_id = config.get(
            "run_id",
            context.run_id
            if context.run_id.startswith("fq9-")
            else "fq9-" + context.run_id,
        )
        if type(run_id) is not str or not re.fullmatch(
            r"fq9-[A-Za-z0-9_-]{1,80}", run_id
        ):
            raise ValueError()
        artifacts = common.load_manifest(
            manifest, candidate_build=context.candidate_build
        )
        receipt = (
            None
            if seed
            else common.load_session_receipt(
                session_receipt, run_id=run_id, live=case == "background"
            )
        )
        artifact = artifacts["candidate"]
        digest = common.digest_file(candidate)
        if (
            artifact.build != context.candidate_build
            or _file(artifact.apk) != candidate
            or artifact.sha256 != digest
        ):
            return _result("blocked", "fq9_candidate_identity_mismatch")
        if case == "upgrade" and (
            "previous" not in artifacts
            or artifacts["previous"].signer != artifact.signer
            or (next_upgrade and artifacts["previous"].build != 2202)
        ):
            return _result("blocked", "fq9_upgrade_baseline_unavailable")
        if case == "background" and receipt["engine"] != "opencode":
            return _result("blocked", "fq9_background_requires_oc1")
        output = Path(context.output) / row
        output.mkdir(mode=0o700, parents=True, exist_ok=False)
        report_path = output / f"{run_id}-{case}.json"
        terminal_path = report_path.with_name(report_path.stem + "-terminal.json")
    except (OSError, ValueError, TypeError, KeyError):
        return _result("blocked", "fq9_configuration_invalid")

    argv = [
        "--case",
        case,
        "--execute",
        "--manifest",
        str(manifest),
        "--seed-history-receipt" if seed else "--session-receipt",
        str(session_receipt),
        "--run-id",
        run_id,
        "--serial",
        "emulator-5554",
        "--output",
        str(output),
    ]
    if next_upgrade:
        argv.extend(["--candidate-build", str(context.candidate_build)])
    invocation_failed = False
    try:
        with context.adopt_lock(driver):
            code = context.capture(lambda: driver.main(argv))
    except Exception:
        invocation_failed, code = True, None
    receipts = [p for p in (report_path, terminal_path) if p.is_file()]
    try:
        report = common.load_json(_file(report_path), limit=262144)
        if type(report) is not dict or any(
            report.get(k) != v
            for k, v in (
                ("schema", 1),
                ("case", case),
                ("runID", run_id),
                ("candidateBuild", context.candidate_build),
                ("candidateSha256", digest),
                ("signerSha256", artifact.signer),
                ("device", "emulator-5554"),
            )
        ):
            raise ValueError()
    except (OSError, ValueError, TypeError):
        return _result(
            "fail" if invocation_failed else "blocked",
            "fq9_receipt_unavailable_or_invalid",
            receipts,
        )
    facts = {
        key: report.get(key) is True
        for key in (
            "automatedChecksPassed",
            "normalRestored",
            "manualChecksPending",
            "deviceQualified",
        )
    }
    facts["candidateBuild"] = context.candidate_build
    driver_report = report.get("driver")
    if (
        (
            type(driver_report) is dict
            and (
                driver_report.get("sessionRetained") is True
                or driver_report.get("cleanupSucceeded") is False
            )
        )
        or "protocolCleanupError" in report
        or "restoreError" in report
    ):
        # Preserve the failed session/evidence from subsequent device rows.
        # The coordinator still owns the final normal-APK restoration policy.
        facts["safe_to_continue"] = False
    if (
        invocation_failed
        or type(code) is not int
        or code != 0
        or report.get("state") != "pass"
        or report.get("automatedChecksPassed") is not True
        or report.get("normalRestored") is not True
    ):
        return _result("fail", "fq9_checks_failed", receipts, **facts)
    if facts.get("safe_to_continue") is False:
        return _result("fail", "fq9_cleanup_incomplete", receipts, **facts)
    if report.get("manualChecksPending") is not False:
        return _result("blocked", "fq9_manual_checks_pending", receipts, **facts)
    if report.get("deviceQualified") is not True:
        return _result("fail", "fq9_qualification_incomplete", receipts, **facts)
    if case == "background":
        try:
            terminal = common.load_json(_file(terminal_path), limit=1048576)
            background = _background_facts(report, terminal)
        except (OSError, ValueError, TypeError):
            background = None
        if background is None:
            return _result("fail", "fq9_background_proof_incomplete", receipts, **facts)
        facts.update(background)
    return _result("pass", "fq9_device_verified", receipts, **facts)


def _fq3(config, context):
    from tool.qa.fq3.update_matrix import validate_run, InvalidEvidence
    if context.candidate_build != 2203:
        return _result("blocked", "fq3_requires_build_2203")
    if type(config) is not dict or set(config) - {"oc1_model", "oc2_model"}:
        return _result("blocked", "fq3_configuration_invalid")
    run_id = "fq3-" + context.run_id
    dart = Path.home() / ".shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart"
    receipt = context.root / "docs/qa/FQ3e-2026-10-09" / (run_id + ".json")
    if receipt.exists():
        return _result("blocked", "evidence_already_exists")
    argv = [str(dart), "run", "tool/qa/fq3_certify.dart", "--run-id", run_id,
            "--inherited-emulator-lock-fd", str(context.lock_fd)]
    for key in ("oc1_model", "oc2_model"):
        if key in config:
            value = config[key]
            if type(value) is not str or not re.fullmatch(r"[A-Za-z0-9._-]+/[A-Za-z0-9._-]+", value):
                return _result("blocked", "fq3_configuration_invalid")
            argv.extend(["--" + key.replace("_", "-"), value])
    result = context.command(argv, timeout=1800)
    try:
        from tool.qa.fq9.common import load_json
        report = load_json(receipt, limit=262144)
        validate_run(report)
        if report["runID"] != run_id or report["appBuild"] != context.candidate_build:
            raise ValueError()
        clean = report["attestation"]["cleanupCompleted"] is True
        checks = [report["protocolSwitch"], *[check for engine in report["engines"].values()
                  for check in engine["results"].values()]]
        passed = result.returncode == 0 and clean and all(check["state"] == "pass" for check in checks)
    except (ValueError, KeyError, TypeError, OSError, InvalidEvidence):
        return _result("fail", "fq3_receipt_invalid", [receipt], safe_to_continue=False)
    return _result("pass" if passed else "fail", "fq3_verified" if passed else "fq3_checks_failed",
                   [receipt], safe_to_continue=clean)
