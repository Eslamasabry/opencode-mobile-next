"""Owned FB1 driver tests: no ADB, no setup, no Flutter, no provider calls."""

from contextlib import redirect_stdout
import fcntl
from io import BytesIO, StringIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

try:
    from PIL import Image
except ImportError:  # CI runners lack Pillow; screenshot-crop tests skip cleanly
    Image = None

needs_pillow = unittest.skipIf(Image is None, 'Pillow is not installed')

from tool.qa.fb1_first_run import (
    COPY,
    FirstRunPorts,
    device_lock,
    main,
    run_first_run,
    scope,
)
from tool.qa.fq9.common import Artifact, DriverFailure, LOCAL_SIGNER, PACKAGE

CANDIDATE = Artifact(
    Path("/approved/candidate.apk"),
    2199,
    "1.2.0",
    "a" * 64,
    LOCAL_SIGNER,
    "coordinator-approved",
)


class FakePorts:
    serial = "emulator-5560"
    dedicated_avd = "fb1-api37-clean"
    run_id = "fb1-unit"

    def __init__(self):
        self.calls = []
        self.package = False
        self.data = False
        self.clock = 0
        self.setup = iter(
            [
                "download-linux",
                "download-linux",
                "unpack-linux",
                "battery-consent",
                "start-opencode",
                "ready",
            ]
        )
        self.reply = iter(["waiting", "completed"])
        self.fail = None
        self.first = dict.fromkeys(
            [
                "appVisible",
                "onboardingVisible",
                "profilesEmpty",
                "setupAbsent",
                "crashFree",
            ],
            True,
        )

    def event(self, name):
        self.calls.append(name)
        if self.fail == name:
            raise ValueError("provider-secret-must-not-escape")

    def verify_candidate(self, candidate):
        self.event("verify")

    def fresh_snapshot(self):
        self.event("fresh")
        return {
            "dedicated": True,
            "currentUser": 0,
            "packageInstalled": self.package,
            "appDataPresent": self.data,
        }

    def install_fresh(self, candidate):
        self.event("install")

    def launch(self):
        self.event("launch")

    def first_run_snapshot(self):
        self.event("first-run")
        return self.first

    def wait_copy(self, key, timeout):
        self.event("wait-" + key)

    def tap_copy(self, key):
        self.event("tap-" + key)

    def screen(self, path):
        self.event("screen-" + path.stem.split("-", 1)[1])
        return {"bytes": 100, "safeRectangles": 4}

    def setup_state(self):
        return next(self.setup, "setup-progress")

    def reply_state(self):
        return next(self.reply, "waiting")

    def model_state(self):
        return "ready"

    def enter_owned(self, key, value):
        self.event("enter-" + key)

    def monotonic(self):
        return self.clock

    def sleep(self, seconds):
        self.clock += seconds


def node(text, box="[0,0][200,100]", *, package=PACKAGE, editable=False):
    return ET.Element(
        "node",
        {
            "text": text,
            "bounds": box,
            "package": package,
            "visible-to-user": "true",
            "class": "android.widget.EditText"
            if editable
            else "android.widget.TextView",
        },
    )


class TestRun(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "new"
        self.ports = FakePorts()

    def run_driver(self, **kwargs):
        return run_first_run(self.ports, CANDIDATE, self.output, **kwargs)

    def test_complete_product_journey_and_screen_receipts(self):
        report = self.run_driver()
        self.assertEqual(report["result"], "PASS")
        self.assertTrue(
            report["cleanInstall"] and report["phoneSetup"] and report["firstReply"]
        )
        self.assertEqual(self.ports.calls[:3], ["verify", "fresh", "install"])
        stages = [screen["stage"] for screen in report["screens"]]
        self.assertEqual(
            stages,
            [
                "welcome",
                "phone-setup-start",
                "download-linux",
                "unpack-linux",
                "battery-consent",
                "start-opencode",
                "project-name",
                "project-entered",
                "first-conversation",
                "first-prompt",
                "first-reply",
            ],
        )
        self.assertEqual(stages.count("download-linux"), 1)
        self.assertIn("tap-consentNotNow", self.ports.calls)
        self.assertIn("tap-phoneSetupReadyCreateOpen", self.ports.calls)
        self.assertIn("tap-kitComposerSend", self.ports.calls)
        self.assertEqual(json.loads((self.output / "report.json").read_text()), report)

    def test_refuses_shared_device_before_artifact_or_install(self):
        self.ports.serial = "emulator-5554"
        with self.assertRaisesRegex(DriverFailure, "fb1_scope_invalid"):
            self.run_driver()
        self.assertEqual(self.ports.calls, [])

    def test_scope_requires_dedicated_owned_avd_and_run_names(self):
        for serial, avd, run in [
            ("phone", "fb1-clean", "fb1-x"),
            ("emulator-5560", "shared", "fb1-x"),
            ("emulator-5560", "fb1-clean", "../secret"),
        ]:
            with self.subTest(serial=serial, avd=avd, run=run):
                with self.assertRaises(DriverFailure):
                    scope(serial, avd, run)

    def test_installed_package_refuses_before_mutation(self):
        self.ports.package = True
        report = self.run_driver()
        self.assertEqual(report["failureCode"], "fb1_package_present")
        self.assertNotIn("install", self.ports.calls)

    def test_existing_private_data_refuses_before_mutation(self):
        self.ports.data = True
        report = self.run_driver()
        self.assertEqual(report["failureCode"], "fb1_data_present")
        self.assertNotIn("install", self.ports.calls)

    def test_artifact_failure_never_contacts_device(self):
        self.ports.fail = "verify"
        report = self.run_driver()
        self.assertEqual(report["failureCode"], "fb1_artifact_failed")
        self.assertEqual(self.ports.calls, ["verify"])
        self.assertNotIn("provider-secret", json.dumps(report))

    def test_first_run_projection_must_be_clean(self):
        self.ports.first["profilesEmpty"] = False
        report = self.run_driver()
        self.assertEqual(report["failureCode"], "fb1_onboarding_failed")
        self.assertNotIn("tap-onboardingTermuxSetup", self.ports.calls)

    def test_unknown_screen_failure_is_fixed_and_stops_journey(self):
        self.ports.fail = "screen-welcome"
        report = self.run_driver()
        self.assertEqual(report["result"], "FAIL")
        self.assertNotIn("provider-secret", json.dumps(report))
        self.assertNotIn("tap-onboardingTermuxSetup", self.ports.calls)

    def test_setup_timeout_retains_partial_truth(self):
        self.ports.setup = iter(["setup-progress"] * 5)
        report = self.run_driver(setup_seconds=3)
        self.assertEqual(report["failureCode"], "fb1_setup_timeout")
        self.assertTrue(report["cleanInstall"])
        self.assertFalse(report["phoneSetup"])
        self.assertFalse(report["firstReply"])
        self.assertNotIn("enter-phoneSetupReadyNameLabel", self.ports.calls)

    def test_provider_requirement_is_blocked_not_pass(self):
        self.ports.reply = iter(["provider-required"])
        report = self.run_driver()
        self.assertEqual(report["result"], "BLOCKED")
        self.assertEqual(report["failureCode"], "fb1_provider_required")
        self.assertTrue(report["connectModelNeeded"])
        self.assertFalse(report["firstReply"])
        self.assertEqual(report["screens"][-1]["stage"], "model-blocked")
        self.assertFalse(report["providerEnrollment"])

    def test_model_selection_block_stops_before_sending_prompt(self):
        self.ports.model_state = lambda: "selection-required"
        report = self.run_driver()
        self.assertEqual(report["result"], "BLOCKED")
        self.assertEqual(report["failureCode"], "fb1_model_selection_required")
        self.assertTrue(report["phoneSetup"])
        self.assertNotIn("tap-kitComposerSend", self.ports.calls)

    def test_reply_timeout_is_failure_not_setup_pass(self):
        self.ports.reply = iter(["waiting"] * 10)
        report = self.run_driver(reply_seconds=2)
        self.assertEqual(report["result"], "FAIL")
        self.assertEqual(report["failureCode"], "fb1_reply_timeout")
        self.assertTrue(report["phoneSetup"])
        self.assertFalse(report["firstReply"])

    def test_preexisting_output_refused_without_mutation(self):
        self.output.mkdir()
        with self.assertRaisesRegex(DriverFailure, "fb1_output_exists"):
            self.run_driver()
        self.assertEqual(self.ports.calls, [])

    def test_invalid_signer_refused_without_mutation(self):
        invalid = Artifact(
            CANDIDATE.apk, 2199, "1.2.0", "a" * 64, "b" * 64, "coordinator-approved"
        )
        with self.assertRaisesRegex(DriverFailure, "fb1_candidate_invalid"):
            run_first_run(self.ports, invalid, self.output)
        self.assertEqual(self.ports.calls, [])

    def test_default_cli_plan_does_not_create_ports_or_files(self):
        with (
            patch("tool.qa.fb1_first_run.FirstRunPorts") as ports,
            redirect_stdout(StringIO()) as printed,
        ):
            code = main(
                [
                    "--serial",
                    "emulator-5560",
                    "--avd",
                    "fb1-clean",
                    "--run-id",
                    "fb1-unit",
                    "--apk",
                    "/approved/candidate.apk",
                    "--build",
                    "2199",
                    "--sha256",
                    "a" * 64,
                    "--output",
                    str(self.output),
                ]
            )
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(printed.getvalue())["result"], "PLAN")
        ports.assert_not_called()
        self.assertFalse(self.output.exists())


class TestUiPrivacy(unittest.TestCase):
    def setUp(self):
        self.ports = FirstRunPorts("emulator-5560", "fb1-unit", "fb1-clean")
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.destination = Path(self.temp.name) / "screen.jpg"

    def test_prompt_echo_never_qualifies_completed_reply(self):
        with patch.object(self.ports, "labels", return_value={"FB1_OK_fb1_unit"}):
            self.assertEqual(self.ports.reply_state(), "waiting")

    def test_completion_requires_marker_and_app_first_reply_offer(self):
        with patch.object(
            self.ports,
            "labels",
            return_value={"FB1_OK_fb1_unit", COPY["firstRunNotifyTitle"]},
        ):
            self.assertEqual(self.ports.reply_state(), "completed")
        with patch.object(
            self.ports, "labels", return_value={COPY["firstRunNotifyTitle"]}
        ):
            self.assertEqual(self.ports.reply_state(), "waiting")

    def test_auth_error_classifies_provider_block(self):
        with patch.object(
            self.ports, "labels", return_value={COPY["chatErrorProviderAuth"]}
        ):
            self.assertEqual(self.ports.reply_state(), "provider-required")

    def test_model_ui_blocks_have_distinct_fixed_categories(self):
        for key, state in [
            ("kitModelSignIn", "provider-required"),
            ("modelPickerSignInTitle", "provider-required"),
            ("kitModelChoose", "selection-required"),
            ("e7ModelUiLoadFailed", "catalog-failed"),
            ("modelUnavailableSelection", "model-unavailable"),
        ]:
            with self.subTest(key=key):
                self.assertEqual(self.ports.model_state({COPY[key]}), state)

    @needs_pillow

    def test_shipped_project_default_is_safe_only_on_first_project_screen(self):
        png = BytesIO()
        Image.new("RGB", (200, 200), "white").save(png, format="PNG")
        nodes = [
            node(COPY["phoneSetupReadyNameTitle"]),
            node("my-app", "[0,100][200,200]", editable=True),
        ]
        with (
            patch.object(self.ports.ui, "nodes", return_value=nodes),
            patch.object(self.ports, "adb", return_value=png.getvalue()),
        ):
            self.assertEqual(self.ports.screen(self.destination)["safeRectangles"], 2)
        nodes[0] = node(COPY["composerFieldLabel"])
        with (
            patch.object(self.ports.ui, "nodes", return_value=nodes),
            patch.object(self.ports, "adb") as adb,
        ):
            with self.assertRaisesRegex(DriverFailure, "fb1_screen_failed"):
                self.ports.screen(self.destination)
            adb.assert_not_called()

    @needs_pillow

    def test_unknown_text_blacked_out_while_public_screen_is_recorded(self):
        png = BytesIO()
        Image.new("RGB", (200, 200), "white").save(png, format="PNG")
        nodes = [
            node(COPY["firstRunWhereQuestion"]),
            node("SECRET_NOT_FOR_REPORT", "[0,100][200,200]"),
        ]
        with (
            patch.object(self.ports.ui, "nodes", return_value=nodes),
            patch.object(self.ports, "adb", return_value=png.getvalue()),
        ):
            result = self.ports.screen(self.destination)
        self.assertEqual(result["safeRectangles"], 1)
        self.assertLessEqual(result["bytes"], 100_000)
        with Image.open(self.destination) as image:
            self.assertLess(max(image.getpixel((100, 150))), 5)
            self.assertGreater(min(image.getpixel((100, 50))), 245)

    def test_credential_editable_field_refuses_image(self):
        nodes = [node(COPY["firstRunWhereQuestion"]), node("secret-key", editable=True)]
        with (
            patch.object(self.ports.ui, "nodes", return_value=nodes),
            patch.object(self.ports, "adb") as adb,
        ):
            with self.assertRaisesRegex(DriverFailure, "fb1_screen_failed"):
                self.ports.screen(self.destination)
            adb.assert_not_called()
        self.assertFalse(self.destination.exists())

    def test_foreign_package_labels_are_not_exposed(self):
        nodes = [node(COPY["firstRunWhereQuestion"], package="evil.app")]
        with patch.object(self.ports.ui, "nodes", return_value=nodes):
            with self.assertRaisesRegex(DriverFailure, "fb1_screen_failed"):
                self.ports.screen(self.destination)
        self.assertFalse(self.destination.exists())

    def test_layout_change_discards_image(self):
        nodes = [node(COPY["firstRunWhereQuestion"])]
        moved = [node(COPY["firstRunWhereQuestion"], "[0,5][200,105]")]
        with (
            patch.object(self.ports.ui, "nodes", side_effect=[nodes, moved]),
            patch.object(self.ports, "adb", return_value=b"not-used"),
        ):
            with self.assertRaisesRegex(DriverFailure, "fb1_screen_failed"):
                self.ports.screen(self.destination)
        self.assertFalse(self.destination.exists())

    @needs_pillow

    def test_screenshot_masks_raw_reply_except_owned_exact_marker(self):
        png = BytesIO()
        Image.new("RGB", (200, 200), "white").save(png, format="PNG")
        nodes = [
            node("FB1_OK_fb1_unit"),
            node("free-form assistant message", "[0,100][200,200]"),
        ]
        with (
            patch.object(self.ports.ui, "nodes", return_value=nodes),
            patch.object(self.ports, "adb", return_value=png.getvalue()),
        ):
            self.ports.screen(self.destination)
        with Image.open(self.destination) as image:
            self.assertLess(max(image.getpixel((100, 150))), 5)


class TestInheritedReservation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.lock_path = Path(self.temp.name) / "emulator.lock"

    def test_already_held_descriptor_is_reused_and_stays_held(self):
        with (
            self.lock_path.open("a") as held,
            patch("tool.qa.fb1_first_run.LOCK", self.lock_path),
        ):
            fcntl.flock(held, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with device_lock(held.fileno()):
                pass
            with self.lock_path.open("a") as other:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def test_unlocked_descriptor_is_not_an_inherited_reservation(self):
        with (
            self.lock_path.open("a") as unlocked,
            patch("tool.qa.fb1_first_run.LOCK", self.lock_path),
        ):
            with self.assertRaisesRegex(DriverFailure, "fb1_lock_invalid"):
                with device_lock(unlocked.fileno()):
                    self.fail("entered device scope without a reservation")

    def test_descriptor_for_other_file_is_refused(self):
        self.lock_path.touch()
        with (
            (Path(self.temp.name) / "other").open("a") as other,
            patch("tool.qa.fb1_first_run.LOCK", self.lock_path),
        ):
            fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(DriverFailure, "fb1_lock_invalid"):
                with device_lock(other.fileno()):
                    self.fail("entered scope for another lock file")

    def test_other_owner_cannot_be_impersonated_with_unlocked_descriptor(self):
        with (
            self.lock_path.open("a") as owner,
            self.lock_path.open("a") as other,
            patch("tool.qa.fb1_first_run.LOCK", self.lock_path),
        ):
            fcntl.flock(owner, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(DriverFailure, "fb1_lock_invalid"):
                with device_lock(other.fileno()):
                    self.fail("entered scope while another descriptor owns lock")


if __name__ == "__main__":
    unittest.main()
