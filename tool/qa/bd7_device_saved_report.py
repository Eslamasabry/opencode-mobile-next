#!/usr/bin/env python3
"""Crash-only saved-report and share-preview proof on approved APK 2199.

The standalone command waits at most 3600 seconds for the shared emulator lock.
An outer lock owner may instead call run_locked. No ANR, provider interaction,
external share, synthetic report, private diagnostics write or baseline erase.
"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tool.qa.bd7_device_crash_smoke import DeviceSession, DeviceFailure, FILES
from tool.qa.bd7_device_ui import Bd7UiFailure
from tool.qa.bd7_exit_proof import ProofFailure
from tool.qa.fq9.common import (
    Artifact,
    PACKAGE,
    DriverFailure,
    LOCAL_SIGNER,
    LOCK,
    SHARED_SERIAL,
)
from tool.qa.fq9.ports import AndroidPorts, apk_identity, verify_artifact, FAIL_CODES

NORMAL_APK = Path("/home/eslam/Storage/tmp/oc-apk-share/oc-2199.apk")
SAVED_CODES = (
    frozenset(
        {
            "device_lock_required",
            "device_command_failed",
            "diagnostic_baseline_not_empty",
            "diagnostic_baseline_invalid",
            "crash_identity_changed",
            "saved_report_count_mismatch",
            "consent_invalid",
            "consent_unavailable",
            "consent_not_enabled",
            "consent_not_disabled",
            "main_process_unavailable",
            "main_process_identity_invalid",
            "process_did_not_exit",
            "saved_crash_ring_unavailable",
            "capture_status_invalid",
            "visible_exit_reason_mismatch",
            "app_navigation_not_ready",
            "preview_unavailable",
            "report_delete_failed",
            "navigation_target_unavailable",
            "ui_unavailable",
            "ui_action_failed",
            "unsafe_screenshot",
            "exit_proof_invalid",
            "crash_ring_invalid",
            "normal_restore_identity_mismatch",
            "candidate_identity_mismatch",
            "device_failure",
            "lock_timeout",
            "artifact_checksum_unavailable",
        }
    )
    | FAIL_CODES
)


def failure_code(error):
    code = getattr(error, "code", None)
    if code is None and isinstance(error, (DeviceFailure, Bd7UiFailure, ProofFailure)):
        code = error.args[0] if error.args else None
    return code if isinstance(code, str) and code in SAVED_CODES else "device_failure"


def same_artifact(identity, artifact):
    return all(
        identity[k] == getattr(artifact, k)
        for k in ("build", "version", "sha256", "signer")
    )


def load_artifact(apk):
    apk = Path(apk)
    try:
        checksum = Path(str(apk) + ".sha256").read_text().split()[0]
        if not re.fullmatch("[0-9a-fA-F]{64}", checksum):
            raise ValueError()
    except (OSError, ValueError, IndexError):
        raise DriverFailure("artifact_checksum_unavailable") from None
    identity = apk_identity(apk)
    if identity["build"] != 2199 or identity["signer"] != LOCAL_SIGNER:
        raise DriverFailure("candidate_identity_mismatch")
    artifact = Artifact(
        apk,
        2199,
        identity["version"],
        checksum.lower(),
        LOCAL_SIGNER,
        "coordinator-approved",
    )
    verify_artifact(artifact)
    return artifact


class SavedReportSession(DeviceSession):
    def __init__(self, adb, output, *, ports=None):
        super().__init__(adb, output)
        self.ports = ports
        self.consent_owned = False

    navigation_timeout_seconds = 60

    @staticmethod
    def owned_nodes(*nodes):
        return all(
            node is not None and node.get("package") == PACKAGE for node in nodes
        )

    def launch(self):
        self.navigation_recovered = False
        return super().launch()

    def navigate_current_page(self):
        # Reuse the current report page even when a root tab remains visible.
        # Tapping that tab would leave this already-ready diagnostics surface.
        if (
            self.ports is not None
            and self.ports.app_visible()
            and self.owned_nodes(
                self.ui.find("Report a problem"),
                self.ui.find("Save crash reports on this phone"),
                self.ui.find("Back"),
            )
        ):
            return True
        return super().navigate_current_page()

    def recover_navigation(self):
        if (
            self.ports is None
            or not self.ports.app_visible()
            or getattr(self, "navigation_recovered", False)
        ):
            return
        stopped = self.ui.find("OpenCode inside the app is stopped")
        start = self.ui.find("Start and connect")
        if self.owned_nodes(stopped, start) and start.get("enabled") == "true":
            # Main-process crash/reinstall may stop the managed server. Use
            # only this exact product action, after strict installer admission.
            self.ports.require_idle_setup()
            self.ui.tap("Start and connect")
            self.navigation_recovered = True
            return
        agents, back = self.ui.find("Agents"), self.ui.find("Back")
        if self.owned_nodes(agents, back):
            self.execute(["shell", "input", "keyevent", "4"])
            self.navigation_recovered = True

    def ring(self):
        path = FILES + "/crash-diagnostics.json"
        if not self.ports.exists(path):
            return []
        try:
            values = json.loads(self.ports.private_bytes(path, limit=8192))
            if type(values) is not list or len(values) > 20:
                raise ValueError()
            return values
        except (ValueError, DriverFailure):
            raise DeviceFailure("diagnostic_baseline_invalid") from None

    def require_clean_baseline(self):
        if self.consent() != 0 or self.ring():
            raise DeviceFailure("diagnostic_baseline_not_empty")
        for name in (
            "crash-diagnostics.pending",
            "native-last-crash.properties",
            "native-last-crash.properties.tmp",
            "diagnostics/report_problem.pending",
        ):
            if self.ports.exists(FILES + "/" + name):
                raise DeviceFailure("diagnostic_baseline_not_empty")
        path = FILES + "/diagnostics/report_problem.json"
        if self.ports.exists(path):
            try:
                value = json.loads(self.ports.private_bytes(path, limit=262144))
                if (
                    type(value) is not dict
                    or value.get("version") != 1
                    or type(value.get("entries")) is not list
                ):
                    raise ValueError()
                for entry in value["entries"]:
                    if (
                        type(entry) is not dict
                        or set(entry) - {"kind", "time", "source", "message", "stack"}
                        or not {"kind", "time", "source", "message"} <= set(entry)
                        or entry.get("kind")
                        not in ("error", "timing", "androidExit", "thermal")
                        or any(
                            type(entry.get(key)) is not str
                            for key in ("time", "source", "message")
                        )
                        or ("stack" in entry and type(entry["stack"]) is not str)
                    ):
                        raise ValueError()
                    if entry["kind"] == "error":
                        raise DeviceFailure("diagnostic_baseline_not_empty")
                # The explicitly authorized product consent change clears its
                # automatically captured timings/exits/thermal observations.
            except (ValueError, DriverFailure) as error:
                if isinstance(error, DeviceFailure):
                    raise
                raise DeviceFailure("diagnostic_baseline_invalid") from None
        return {"consent_enabled": False, "saved_count": 0}

    def enable_consent(self):
        # Claim only an empty disabled baseline, before UI can mutate it. If the
        # switch call times out after tapping, finally still owns its cleanup.
        self.require_clean_baseline()
        self.consent_owned = True
        return super().enable_consent()

    def proof(self, identity, reason, source, after, label):
        # BD7 qualifies saved capture and its previews. FD1's separate UI
        # navigation must not block this flow; exact OS exit proof stays required.
        return super().proof(
            identity, reason, source, after, label, include_exit_ui=False
        )

    def crash(self):
        # Consent/navigation can outlive the outer setup preflight. Never
        # interrupt a newly admitted installer or its durable restoration ticket.
        self.ports.require_idle_setup()
        identity = self.identity()
        after = int(self.execute(["shell", "date", "+%s%3N"]).strip())
        if not self.still_owned(identity):
            raise DeviceFailure("crash_identity_changed")
        self.execute(["shell", "am", "crash", "--user", "0", str(identity[0])])
        self.died(identity)
        try:
            close = self.ui.find("Close app")
            title = self.ui.find("OpenCode Mobile keeps stopping")
            if title is None:
                title = self.ui.find("OpenCode Mobile has stopped")
            if (
                close is not None
                and title is not None
                and close.get("package") == "android"
            ):
                self.ui.tap("Close app")
        except Bd7UiFailure:
            pass
        self.launch()
        proof = self.proof(identity, 4, "native", after, "The app closed unexpectedly")
        if len(self.ring()) != 1:
            raise DeviceFailure("saved_report_count_mismatch")
        return proof

    def share_preview(self):
        # 2199's recent-exit details expand inline on the report page. Back
        # here would leave that page rather than dismiss a details sheet.
        self.ui.scroll("up")
        self.ui.scroll_find("Share saved crash reports")
        self.ui.tap("Share saved crash reports")
        if (
            self.ui.find("Preview crash report") is None
            or self.ui.find("Share report") is None
        ):
            raise DeviceFailure("preview_unavailable")
        shot = self.ui.screenshot(
            self.output / "saved-report-share-preview.jpg", section="share"
        )
        # No Share report action: preview never sends data or opens an app.
        self.execute(["shell", "input", "keyevent", "4"])
        return {"visible": True, "external_share_opened": False, "screenshot": shot}

    def cleanup_owned_reports(self):
        if not self.consent_owned:
            return
        # Reenter through Settings to dismiss any partially open proof sheet.
        self.execute(["shell", "input", "keyevent", "4"])
        self.execute(["shell", "input", "keyevent", "4"])
        self.launch()
        deletion_failure = None
        try:
            count = len(self.ring())
            if count > 1:
                raise DeviceFailure("saved_report_count_mismatch")
            if count:
                self.ui.scroll_find("Delete 1 saved crash report")
                self.ui.tap("Delete 1 saved crash report")
                if self.ui.find("Delete saved crash reports?") is None:
                    raise DeviceFailure("report_delete_failed")
                self.ui.tap("Delete crash reports")
                deadline = time.monotonic() + 10
                while self.ring():
                    if time.monotonic() >= deadline:
                        raise DeviceFailure("report_delete_failed")
                    time.sleep(0.25)
                self.ui.scroll_find("No crash reports yet")
                self.ui.screenshot(self.output / "saved-report-deleted.jpg")
        except Exception as error:
            deletion_failure = error
            # An incomplete delete dialog must not prevent restoring OFF.
            self.execute(["shell", "input", "keyevent", "4"])
        self.ui.scroll("up")
        self.ui.scroll_find("Save crash reports on this phone")
        if self.consent() > 0:
            self.ui.tap("Save crash reports on this phone", contains=True)
        deadline = time.monotonic() + 10
        while self.consent() != 0:
            if time.monotonic() >= deadline:
                raise DeviceFailure("consent_not_disabled")
            time.sleep(0.25)
        if self.ring() or self.ui.find("The app closed unexpectedly") is not None:
            raise DeviceFailure("report_delete_failed")
        self.ui.screenshot(self.output / "consent-off-after.jpg")
        self.consent_owned = False
        if deletion_failure is not None:
            raise deletion_failure


def run_locked(artifact, output, *, device=None, session=None):
    """Caller holds one emulator reservation; no nested flock or old driver run."""
    if artifact.build != 2199 or artifact.signer != LOCAL_SIGNER:
        raise DriverFailure("candidate_identity_mismatch")
    device = device or AndroidPorts(SHARED_SERIAL, "bd7-saved-" + str(time.time_ns()))
    if not device.locked:
        raise DriverFailure("device_unavailable")
    session = session or SavedReportSession("adb", Path(output), ports=device)
    if not session.locked:
        session.locked = True
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    report = {
        "device": SHARED_SERIAL,
        "version_code": artifact.build,
        "result": "FAIL",
        "apk_sha256": artifact.sha256,
        "scope": "real_crash_saved_report_share_preview",
        "external_share_opened": False,
        "anr_triggered": False,
    }
    stage, uid = "candidate_identity", None
    touched = False
    try:
        verify_artifact(artifact)
        device.device_ready()
        identity = device.installed_identity()
        uid = identity["uid"]
        if not same_artifact(identity, artifact):
            raise DriverFailure("candidate_identity_mismatch")
        device.require_idle_setup()
        stage = "baseline"
        report["baseline"] = session.require_clean_baseline()
        stage = "app_navigation"
        touched = True
        session.launch()
        stage = "consent"
        _, epoch = session.enable_consent()
        report["consent_enabled"] = epoch > 0
        stage = "native_crash"
        report["native_crash"] = session.crash()
        stage = "share_preview"
        report["share_preview"] = session.share_preview()
        report["result"] = "PASS"
    except Exception as error:
        report.update(failure_stage=stage, failure_code=failure_code(error))
    finally:
        if session.consent_owned:
            try:
                session.cleanup_owned_reports()
                report["owned_report_cleanup"] = "PASS"
            except Exception as error:
                report.update(
                    result="FAIL",
                    owned_report_cleanup="FAIL",
                    cleanup_failure_code=failure_code(error),
                )
        if uid is not None and touched:
            try:
                verify_artifact(artifact)
                actual = device.installed_identity()
                if actual["signer"] != artifact.signer or actual["uid"] != uid:
                    raise DriverFailure("normal_restore_identity_mismatch")
                # User requested installation of normal2199 after every run.
                device.adb("install", "-r", str(artifact.apk), timeout=180)
                actual = device.installed_identity()
                if not same_artifact(actual, artifact) or actual["uid"] != uid:
                    raise DriverFailure("normal_restore_identity_mismatch")
                device.launch()
                report["normal_app_restore"] = "PASS"
            except Exception as error:
                report.update(
                    result="FAIL",
                    normal_app_restore="FAIL",
                    restore_failure_code=failure_code(error),
                )
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def locked_run(artifact, output):
    with LOCK.open("a") as lock:
        try:
            # flock receives this open FD, waits bounded3600, and leaves the
            # parent descriptor locked until restoration finishes and closes.
            subprocess.run(
                ["flock", "-w", "3600", str(lock.fileno())],
                pass_fds=(lock.fileno(),),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=True,
                timeout=3605,
            )
        except (OSError, subprocess.SubprocessError):
            raise DriverFailure("lock_timeout") from None
        device = AndroidPorts(SHARED_SERIAL, "bd7-saved-" + str(time.time_ns()))
        device.locked = True
        return run_locked(artifact, output, device=device)


def run(args):
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        artifact = load_artifact(args.apk)
        report = locked_run(artifact, args.output)
    except Exception as error:
        report = {
            "result": "FAIL",
            "failure_stage": "host_preflight_or_lock",
            "failure_code": failure_code(error),
        }
        (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print("BD7 saved-report " + report["result"], flush=True)
    return 0 if report["result"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=NORMAL_APK)
    parser.add_argument("--output", type=Path, default=Path("build/bd7-saved-report"))
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
