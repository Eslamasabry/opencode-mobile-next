"""Small final-pass adapters; the caller owns the shared emulator reservation.

No driver is rewritten or invoked through its standalone lock-taking CLI.
Successful planning/recording never substitutes for device qualification.
"""

from contextlib import contextmanager
import json
from pathlib import Path
from types import SimpleNamespace


_DEMO_CUES = frozenset({
    "Use synthetic/demo data only. Hide notifications and every real account value.",
    "0s: show demo installation/setup.",
    "Pick the demo agent now.",
    "Approve a harmless demo tool now.",
    "Private GIF recorded. Review capture.mp4 and demo.gif before manual export.",
})


def _demo_emit(message):
    if type(message) is str and message in _DEMO_CUES:
        print(message, flush=True)


def _result(status, reason, receipts=(), **data):
    result = {
        "status": status,
        "reason": reason,
        "receipts": [str(path) for path in receipts if Path(path).is_file()],
    }
    if data:
        result["data"] = data
    return result


def _write(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as output:
        json.dump(value, output, indent=2)
        output.write("\n")
    return path


@contextmanager
def _borrowed(context):
    # .open validates and duplicates the already-held descriptor. Never flock
    # a new descriptor: that would deadlock behind the caller's reservation.
    with context.borrowed_lock().open("a"):
        yield


def _bd7(context):
    if context.candidate_build != 2202:
        return _result("blocked", "bd7_requires_build_2202")
    from tool.qa import bd7_device_saved_report as driver
    from tool.qa.fq9.common import SHARED_SERIAL
    from tool.qa.fq9.ports import AndroidPorts

    output = Path(context.output) / "bd7-saved-report"
    if output.exists():
        return _result("blocked", "evidence_already_exists")

    def execute():
        artifact = driver.load_artifact(context.candidate)
        with _borrowed(context):
            device = AndroidPorts(SHARED_SERIAL, context.run_id)
            device.locked = True
            return driver.run_locked(artifact, output, device=device)

    report = context.capture(execute)
    receipt = output / "report.json"
    if not receipt.is_file() or receipt.stat().st_size > 65536:
        return _result("fail", "bd7_receipt_missing")
    saved = json.loads(receipt.read_text(encoding="utf-8"))
    if type(report) is not dict or saved != report:
        return _result("fail", "bd7_receipt_invalid", [receipt])
    crash = report.get("native_crash")
    preview = report.get("share_preview")
    qualified = (
        report.get("result") == "PASS"
        and report.get("version_code") == 2202
        and report.get("scope") == "real_crash_saved_report_share_preview"
        and report.get("consent_enabled") is True
        and type(crash) is dict
        and crash.get("visible_report") is True
        and crash.get("visible_recent_exit") is True
        and type(preview) is dict
        and preview.get("visible") is True
        and preview.get("external_share_opened") is False
        and report.get("external_share_opened") is False
        and report.get("anr_triggered") is False
        and report.get("owned_report_cleanup") == "PASS"
        and report.get("normal_app_restore") == "PASS"
    )
    return _result(
        "pass" if qualified else "fail",
        "saved_report_device_verified" if qualified else "bd7_proof_incomplete",
        [receipt],
        device_qualified=qualified,
        candidate_build=2202,
        external_share_opened=False if qualified else None,
        anr_triggered=False if qualified else None,
    )


def _fb1(context):
    from tool.qa.fq9 import run as driver

    plan = context.capture(lambda: driver.plan(SimpleNamespace(case="fresh")))
    if (
        type(plan) is not dict
        or plan.get("state") != "plan"
        or plan.get("case") != "fresh"
        or plan.get("deviceTouched") is not False
        or type(plan.get("freshPlan")) is not dict
        or plan["freshPlan"].get("requiresDedicatedAvd") is not True
    ):
        return _result("fail", "fresh_plan_invalid")
    receipt = _write(Path(context.output) / "fb1-plan.json", plan)
    return _result(
        "pass", "plan_only_not_device_qualification", [receipt],
        device_qualified=False, device_touched=False, requires_dedicated_avd=True,
    )


def _demo(config, context):
    if config.get("synthetic_demo_attested") is not True:
        return _result("blocked", "synthetic_demo_attestation_required")
    from tool.qa import record_demo_gif as driver

    receipt = Path(context.output) / "demo.json"
    if receipt.exists():
        return _result("blocked", "evidence_already_exists")
    options = driver.parser().parse_args([])
    options.record = True
    options.attest_synthetic_demo = True
    # Keep the recorder's private output outside the checkout. The outer runner
    # installs/restores the candidate; recording must not install another APK.
    options.private_dir = driver.PRIVATE_ROOT
    options.candidate_apk = None
    # The operator must see the existing preparation prompt and timed cues.
    # Only fixed instructions pass through emit; suppress the final path print.
    gif = Path(driver.record(
        options, lock=lambda: _borrowed(context), emit=_demo_emit,
    ))
    raw = gif.parent / "capture.mp4"
    review = gif.parent / "REVIEW_REQUIRED.json"
    private_root = driver.PRIVATE_ROOT.expanduser().resolve()
    if (
        private_root not in gif.resolve().parents
        or gif.name != "demo.gif"
        or any(path.is_symlink() or not path.is_file() for path in (gif, raw, review))
        or review.stat().st_size > 16384
    ):
        return _result("fail", "demo_artifacts_invalid")
    review_data = json.loads(review.read_text(encoding="utf-8"))
    if (
        type(review_data) is not dict
        or review_data.get("synthetic_demo_attested") is not True
        or review_data.get("reviewed_for_export") is not False
        or review_data.get("duration_requested_seconds") != options.duration
    ):
        return _result("fail", "demo_review_receipt_invalid")
    _write(receipt, {
        "schema": 1,
        "captured": True,
        "reviewed_for_export": False,
        "exported": False,
        "device_qualified": False,
        "duration_requested_seconds": options.duration,
        "private_artifacts": {
            "gif": str(gif), "mp4": str(raw), "review_receipt": str(review),
        },
    })
    return _result(
        "blocked", "manual_review_required", [receipt, review],
        captured=True, reviewed_for_export=False, exported=False,
        device_qualified=False,
    )


def run(row, config, context):
    """Return bounded, credential-free facts for one selected final-pass row."""
    if type(config) is not dict:
        return _result("blocked", "invalid_row_configuration")
    try:
        if row == "bd7":
            return _bd7(context)
        if row == "fb1":
            return _fb1(context)
        if row == "demo":
            return _demo(config, context)
        return _result("blocked", "unsupported_row")
    except FileExistsError:
        return _result("blocked", "evidence_already_exists")
    except Exception:
        # Raw subprocess, credential, path and driver exception text stays out
        # of public summaries. Demo instructions are separately allowlisted.
        return _result("fail", "misc_driver_failed")
