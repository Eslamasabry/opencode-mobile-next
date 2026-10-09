#!/usr/bin/env python3
"""FB1 product first run on an already provisioned, dedicated fresh AVD.

Finish line: clean installation through the phone setup UI to a completed
first reply, with privacy-projected screenshots of each observed screen.
Non-goals: AVD provisioning, shared-device mutation, provider enrollment,
account interaction, uninstall, data clearing, setup shell scripts or uploads.
Imports and the default CLI plan do not contact a device.
"""

import argparse
from contextlib import contextmanager
import fcntl
from io import BytesIO
import json
import os
from pathlib import Path
import re
import stat
import sys
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from PIL import Image

from tool.qa.bd7_device_ui import Bd7Ui, _normalize, _rect
from tool.qa.fq9.common import (
    Artifact,
    DriverFailure,
    LOCAL_SIGNER,
    LOCK,
    PACKAGE,
    SHARED_SERIAL,
)
from tool.qa.fq9.fresh import _fresh_snapshot
from tool.qa.fq9.ports import AndroidPorts, verify_artifact

ROOT = Path(__file__).resolve().parents[2]
COPY = json.loads((ROOT / "lib/l10n/app_en.arb").read_text())
PUBLIC = frozenset(
    value
    for key, value in COPY.items()
    if not key.startswith("@") and isinstance(value, str) and "{" not in value
)
CODES = frozenset(
    {
        "fb1_scope_invalid",
        "fb1_candidate_invalid",
        "fb1_artifact_failed",
        "fb1_fresh_failed",
        "fb1_package_present",
        "fb1_data_present",
        "fb1_install_failed",
        "fb1_launch_failed",
        "fb1_onboarding_failed",
        "fb1_screen_failed",
        "fb1_setup_navigation_failed",
        "fb1_setup_failed",
        "fb1_setup_timeout",
        "fb1_project_failed",
        "fb1_composer_failed",
        "fb1_prompt_failed",
        "fb1_reply_timeout",
        "fb1_provider_required",
        "fb1_ui_failed",
        "fb1_unexpected_failure",
        "fb1_output_exists",
        "fb1_lock_invalid",
        "fb1_model_selection_required",
        "fb1_catalog_failed",
        "fb1_model_unavailable",
    }
)


def safe_failure(error, fallback):
    return (
        error.code
        if isinstance(error, DriverFailure) and error.code in CODES
        else fallback
    )


def scope(serial, avd, run_id):
    if (
        not isinstance(serial, str)
        or not re.fullmatch(r"emulator-[0-9]{4,5}", serial)
        or serial == SHARED_SERIAL
        or not isinstance(avd, str)
        or not re.fullmatch(r"fb1-[A-Za-z0-9_-]{1,60}", avd)
        or not isinstance(run_id, str)
        or not re.fullmatch(r"fb1-[A-Za-z0-9_-]{1,40}", run_id)
    ):
        raise DriverFailure("fb1_scope_invalid")


def candidate_valid(candidate):
    return (
        isinstance(candidate, Artifact)
        and candidate.origin == "coordinator-approved"
        and type(candidate.build) is int
        and 1 <= candidate.build <= 999999
        and candidate.signer == LOCAL_SIGNER
        and isinstance(candidate.sha256, str)
        and re.fullmatch(r"[0-9a-f]{64}", candidate.sha256) is not None
    )


@contextmanager
def device_lock(inherited_fd=None):
    """Reuse an actually held, inherited reservation without opening a second lock.

    Validate both file identity and ownership of the exclusive flock. Merely
    passing a descriptor number or an unlocked descriptor is insufficient.
    The caller keeps ownership through boot, this driver and owned shutdown.
    """
    if inherited_fd is not None:
        try:
            actual = os.fstat(inherited_fd)
            expected = LOCK.stat()
            if not stat.S_ISREG(actual.st_mode) or (actual.st_dev, actual.st_ino) != (
                expected.st_dev,
                expected.st_ino,
            ):
                raise DriverFailure("fb1_lock_invalid")
            with LOCK.open("a") as probe:
                try:
                    fcntl.flock(probe, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    pass
                else:
                    fcntl.flock(probe, fcntl.LOCK_UN)
                    raise DriverFailure("fb1_lock_invalid")
            # Succeeds only if this open-file description owns the reservation,
            # or can take exclusive ownership after the prior owner released it.
            fcntl.flock(inherited_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except (OSError, ValueError):
            raise DriverFailure("fb1_lock_invalid") from None
        yield
        return
    with LOCK.open("a") as locked:
        deadline = time.monotonic() + 3600
        while True:
            try:
                fcntl.flock(locked, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise DriverFailure("fb1_lock_invalid")
                time.sleep(1)
        yield


def run_first_run(ports, candidate, output, *, setup_seconds=2700, reply_seconds=180):
    """Injected ports make admission, timeout and provider-block paths testable.

    No cleanup is performed: this dedicated AVD retains its installed candidate
    and owned first-run project for inspection. A failed/blocked row never
    becomes a clean-install or first-reply PASS through partial observations.
    """
    scope(ports.serial, ports.dedicated_avd, ports.run_id)
    if (
        not candidate_valid(candidate)
        or type(setup_seconds) is not int
        or not 1 <= setup_seconds <= 7200
        or type(reply_seconds) is not int
        or not 1 <= reply_seconds <= 900
    ):
        raise DriverFailure("fb1_candidate_invalid")
    output = Path(output)
    if output.exists():
        raise DriverFailure("fb1_output_exists")
    report = {
        "schema": 1,
        "result": "FAIL",
        "candidateBuild": candidate.build,
        "candidateSha256": candidate.sha256,
        "screens": [],
        "cleanInstall": False,
        "phoneSetup": False,
        "firstReply": False,
        "candidateRetained": False,
        "screenPolicy": "public-copy-only-blackout",
        "providerEnrollment": False,
    }
    failure = "fb1_artifact_failed"
    try:
        ports.verify_candidate(candidate)
        failure = "fb1_fresh_failed"
        initial = _fresh_snapshot(ports)
        if initial["packageInstalled"]:
            raise DriverFailure("fb1_package_present")
        if initial["appDataPresent"]:
            raise DriverFailure("fb1_data_present")
        failure = "fb1_install_failed"
        ports.install_fresh(candidate)
        report["cleanInstall"] = True
        report["candidateRetained"] = True
        failure = "fb1_launch_failed"
        ports.launch()
        failure = "fb1_onboarding_failed"
        ports.wait_copy("firstRunWhereQuestion", 30)
        first = ports.first_run_snapshot()
        if (
            type(first) is not dict
            or set(first)
            != {
                "appVisible",
                "onboardingVisible",
                "profilesEmpty",
                "setupAbsent",
                "crashFree",
            }
            or any(value is not True for value in first.values())
        ):
            raise DriverFailure("fb1_onboarding_failed")
        report["firstRun"] = first

        def screen(stage):
            receipt = ports.screen(
                output / (f"{len(report['screens']):02d}-{stage}.jpg")
            )
            report["screens"].append({"stage": stage, **receipt})

        screen("welcome")
        failure = "fb1_setup_navigation_failed"
        ports.tap_copy("onboardingTermuxSetup")
        ports.wait_copy("phoneSetupStartSetUp", 30)
        screen("phone-setup-start")
        ports.tap_copy("phoneSetupStartSetUp")
        failure = "fb1_setup_failed"
        deadline = ports.monotonic() + setup_seconds
        observed = set()
        while True:
            state = ports.setup_state()
            if state == "ready":
                screen("project-name")
                report["phoneSetup"] = True
                break
            if state == "failed":
                screen("setup-failed")
                raise DriverFailure("fb1_setup_failed")
            if state in ("battery-consent", "maker-consent"):
                if state not in observed:
                    screen(state)
                    observed.add(state)
                ports.tap_copy("consentNotNow")
            elif state not in observed:
                screen(state)
                observed.add(state)
            if ports.monotonic() >= deadline:
                raise DriverFailure("fb1_setup_timeout")
            ports.sleep(2)
        failure = "fb1_project_failed"
        ports.enter_owned("phoneSetupReadyNameLabel", ports.run_id)
        screen("project-entered")
        ports.tap_copy("phoneSetupReadyCreateOpen")
        failure = "fb1_composer_failed"
        ports.wait_copy("composerFieldLabel", 60)
        screen("first-conversation")

        def model_block(state):
            code = {
                "provider-required": "fb1_provider_required",
                "selection-required": "fb1_model_selection_required",
                "catalog-failed": "fb1_catalog_failed",
                "model-unavailable": "fb1_model_unavailable",
            }.get(state)
            if code is None:
                return
            screen("model-blocked")
            report["result"] = "BLOCKED"
            report["modelBlockage"] = state
            report["connectModelNeeded"] = state == "provider-required"
            raise DriverFailure(code)

        model_block(ports.model_state())
        failure = "fb1_prompt_failed"
        prompt = f"Reply with only FB1_OK_{ports.run_id.replace('-', '_')}"
        ports.enter_owned("composerFieldLabel", prompt)
        screen("first-prompt")
        ports.tap_copy("kitComposerSend")
        deadline = ports.monotonic() + reply_seconds
        failure = "fb1_reply_timeout"
        while True:
            state = ports.reply_state()
            model_block(state)
            if state == "completed":
                screen("first-reply")
                report["firstReply"] = True
                report["result"] = "PASS"
                break
            if ports.monotonic() >= deadline:
                screen("reply-timeout")
                raise DriverFailure("fb1_reply_timeout")
            ports.sleep(1)
    except Exception as error:
        report["failureCode"] = safe_failure(error, failure)
        if failure == "fb1_install_failed" and isinstance(error, DriverFailure):
            from tool.qa.fq9.ports import FAIL_CODES
            if error.code in FAIL_CODES:
                report["installVerificationError"] = error.code
    diagnostic = getattr(ports, "install_diagnostic", None)
    if diagnostic is not None:
        report["installDiagnostic"] = diagnostic
    output.mkdir(parents=True, exist_ok=True)
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


class FirstRunPorts(AndroidPorts):
    def install_apk(self, artifact):
        from tool.qa.fb1_install_diagnostic import install
        if not self.locked:
            raise DriverFailure("fb1_lock_invalid")
        self.install_diagnostic = install(self.serial, artifact.apk)
        if not self.install_diagnostic['success']:
            raise DriverFailure("fb1_install_failed")

    def __init__(self, serial, run_id, avd):
        scope(serial, avd, run_id)
        super().__init__(serial, run_id, dedicated_avd=avd)
        self.ui = Bd7Ui(lambda args, **kwargs: self.adb(*args, **kwargs))
        self.owned = {
            run_id,
            f"FB1_OK_{run_id.replace('-', '_')}",
            f"Reply with only FB1_OK_{run_id.replace('-', '_')}",
        }

    def verify_candidate(self, candidate):
        verify_artifact(candidate)

    def labels(self):
        return {
            label
            for n in self.ui.nodes()
            if n.get("package") == PACKAGE
            and n.get("visible-to-user", "true") != "false"
            for field in ("text", "content-desc")
            for label in [
                _normalize(n.get(field, "")),
                *(_normalize(line) for line in n.get(field, "").splitlines()),
            ]
            if label
        }

    def wait_copy(self, key, seconds):
        deadline = self.monotonic() + seconds
        while self.monotonic() < deadline:
            if COPY[key] in self.labels():
                return
            self.sleep(1)
        raise DriverFailure("fb1_ui_failed")

    def tap_copy(self, key):
        nodes = [n for n in self.ui.nodes() if n.get("package") == PACKAGE]
        node = self.ui._find(COPY[key], nodes)
        if node is None or node.get("enabled", "true") != "true":
            raise DriverFailure("fb1_ui_failed")
        x, y = self.ui.centre(node)
        self.adb("shell", "input", "tap", str(x), str(y))

    def enter_owned(self, key, value):
        if value not in self.owned:
            raise DriverFailure("fb1_ui_failed")
        nodes = self.ui.nodes()
        fields = [
            n
            for n in nodes
            if n.get("package") == PACKAGE
            and n.get("class") == "android.widget.EditText"
            and n.get("enabled", "true") == "true"
            and n.get("visible-to-user", "true") != "false"
        ]
        if len(fields) != 1 or COPY[key] not in self.labels():
            raise DriverFailure("fb1_ui_failed")
        x, y = self.ui.centre(fields[0])
        self.adb("shell", "input", "tap", str(x), str(y))
        # Fresh single field: select-all prevents shipped default project text
        # being silently appended. Values are bounded authored ASCII only.
        self.adb("shell", "input", "keycombination", "113", "29")
        self.adb("shell", "input", "text", value.replace(" ", "%s"))
        self.adb("shell", "input", "keyevent", "111")  # hide only the keyboard
        if value not in self.labels():
            raise DriverFailure("fb1_ui_failed")

    def setup_state(self):
        labels = self.labels()
        checks = (
            ("phoneSetupReadyNameTitle", "ready"),
            ("consentBatteryTitle", "battery-consent"),
            ("consentMakerTitle", "maker-consent"),
            ("phoneSetupStageDownloadingLinux", "download-linux"),
            ("phoneSetupStageUnpackingLinux", "unpack-linux"),
            ("phoneSetupStageStarting", "start-opencode"),
        )
        for key, state in checks:
            if COPY[key] in labels:
                return state
        if any(
            COPY[key] in labels
            for key in ("phoneSetupErrorNoInternet", "phoneSetupErrorCannotStart")
        ):
            return "failed"
        return "setup-progress"

    def reply_state(self):
        labels = self.labels()
        model = self.model_state(labels)
        if model != "ready":
            return model
        marker = f"FB1_OK_{self.run_id.replace('-', '_')}"
        # The exact marker alone could be a prompt echo. The first-reply offer
        # additionally requires an error-free assistant and idle in app source
        # (chat_notices.dart _replyCompleted); fresh onboarding makes it new.
        if marker in labels and COPY["firstRunNotifyTitle"] in labels:
            return "completed"
        return "waiting"

    def model_state(self, labels=None):
        labels = self.labels() if labels is None else labels
        # Public, source-backed UI states only. A generic Connect a provider
        # action may coexist with usable free models and is NOT a blocker.
        if any(
            COPY[key] in labels
            for key in (
                "chatErrorProviderAuth",
                "kitModelSignIn",
                "modelPickerSignInTitle",
            )
        ):
            return "provider-required"
        if COPY["kitModelChoose"] in labels:
            return "selection-required"
        if COPY["e7ModelUiLoadFailed"] in labels:
            return "catalog-failed"
        if COPY["modelUnavailableSelection"] in labels:
            return "model-unavailable"
        return "ready"

    def screen(self, path):
        """Project safe leaf rectangles, blacking everything else out first.

        No full screenshots, unknown text, UI XML, provider names/tokens,
        download log output or free-form model replies are persisted. Screen
        geometry must be stable across capture; unknown content stays black.
        """

        def rectangles(nodes):
            allowed = []
            context = {
                self.ui.text(node) for node in nodes if node.get("package") == PACKAGE
            }
            owned_field_context = (
                COPY["phoneSetupReadyNameTitle"] in context
                or COPY["composerFieldLabel"] in context
            )
            # PhoneSetupReadyScreen.suggestedName is fixed app-authored copy,
            # permitted only on the verified first-project screen.
            defaults = (
                {"my-app"} if COPY["phoneSetupReadyNameTitle"] in context else set()
            )
            for node in nodes:
                # A provider credential page is not a first-run evidence
                # surface, even if its surrounding public labels are safe.
                if (
                    node.get("class") == "android.widget.EditText"
                    or node.get("editable") == "true"
                ):
                    value = self.ui.text(node)
                    hints = {
                        COPY["composerFieldLabel"],
                        COPY["phoneSetupReadyNameLabel"],
                        COPY["chatUiAskOpenCode"],
                        "",
                    }
                    if (
                        node.get("password") == "true"
                        or not owned_field_context
                        or value not in self.owned | hints | defaults
                    ):
                        raise DriverFailure("fb1_screen_failed")
                box = _rect(node)
                if (
                    box is None
                    or node.get("package") != PACKAGE
                    or list(node)
                    or node.get("visible-to-user", "true") == "false"
                ):
                    continue
                labels = [
                    _normalize(line)
                    for field in ("text", "content-desc")
                    for line in node.get(field, "").splitlines()
                    if _normalize(line)
                ]
                if not labels or any(
                    label not in PUBLIC
                    and label not in self.owned | defaults
                    and not re.fullmatch(r"[0-9]{1,3}% done", label)
                    for label in labels
                ):
                    continue
                if node.get("class") == "android.widget.EditText" and not all(
                    label in self.owned | defaults
                    or label
                    in {
                        COPY["composerFieldLabel"],
                        COPY["phoneSetupReadyNameLabel"],
                        COPY["chatUiAskOpenCode"],
                    }
                    for label in labels
                ):
                    continue
                allowed.append(box)
            return sorted(set(allowed))

        first = rectangles(self.ui.nodes())
        if not first:
            raise DriverFailure("fb1_screen_failed")
        raw = self.adb("exec-out", "screencap", "-p", timeout=10, limit=8_000_000)
        if first != rectangles(self.ui.nodes()):
            raise DriverFailure("fb1_screen_failed")
        try:
            with Image.open(BytesIO(raw)) as image:
                if image.format != "PNG" or image.width * image.height > 16_000_000:
                    raise ValueError()
                projected = Image.new("RGB", image.size, "black")
                for box in first:
                    if box[2] > image.width or box[3] > image.height:
                        raise ValueError()
                    projected.paste(image.crop(box).convert("RGB"), box[:2])
                projected.thumbnail((480, 960))
            buffer = BytesIO()
            projected.save(buffer, format="JPEG", quality=60)
            if buffer.tell() > 100_000:
                raise ValueError()
            Path(path).parent.mkdir(parents=True, exist_ok=True)
            Path(path).write_bytes(buffer.getvalue())
            return {"bytes": buffer.tell(), "safeRectangles": len(first)}
        except Exception:
            raise DriverFailure("fb1_screen_failed") from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument(
        "--lock-fd",
        type=int,
        help="inherited descriptor already holding the emulator reservation",
    )
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build", type=int, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--version", default="1.2.0")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        scope(args.serial, args.avd, args.run_id)
        candidate = Artifact(
            args.apk,
            args.build,
            args.version,
            args.sha256,
            LOCAL_SIGNER,
            "coordinator-approved",
        )
        if not candidate_valid(candidate):
            raise DriverFailure("fb1_candidate_invalid")
        if not args.execute:
            print(
                json.dumps(
                    {
                        "result": "PLAN",
                        "deviceContact": False,
                        "requiresDedicatedFreshAvd": True,
                        "stages": [
                            "welcome",
                            "phone-setup-start",
                            "setup-progress",
                            "project-name",
                            "first-conversation",
                            "first-reply",
                        ],
                    }
                )
            )
            return 0
        # One reservation covers all setup and reply observations. Provisioning
        # and AVD lifecycle remain separate, explicitly authorized operations.
        with device_lock(args.lock_fd):
            ports = FirstRunPorts(args.serial, args.run_id, args.avd)
            ports.locked = True
            report = run_first_run(ports, candidate, args.output)
        print(
            json.dumps(
                {"result": report["result"], "failureCode": report.get("failureCode")}
            )
        )
        return 0 if report["result"] == "PASS" else 1
    except Exception as error:
        print(
            json.dumps(
                {
                    "result": "FAIL",
                    "failureCode": safe_failure(error, "fb1_unexpected_failure"),
                }
            )
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
