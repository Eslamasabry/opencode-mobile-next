"""Offline miscellaneous adapters: no APK tools, device calls or recording."""

from contextlib import redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tool.qa import final_pass_misc as subject
from tool.qa import record_demo_gif as recorder


class FinalPassMiscTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.output = self.base / "output"
        self.lock = self.base / "emulator.lock"
        self.private = self.base / "private"
        self.hidden = io.StringIO()

        def capture(callback):
            with redirect_stdout(self.hidden), redirect_stderr(self.hidden):
                return callback()

        self.context = SimpleNamespace(
            root=Path(__file__).resolve().parents[2], output=self.output,
            candidate=self.base / "candidate.apk", candidate_build=2203,
            lock_fd=99, run_id="offline-misc", command=Mock(),
            capture=Mock(side_effect=capture),
            borrowed_lock=Mock(return_value=self.lock),
        )

    def test_bd7_incompatible_build_never_reads_artifact_or_touches_device(self):
        self.context.candidate_build = 2204
        result = subject.run("bd7", {}, self.context)
        self.assertEqual(result, {
            "status": "blocked", "reason": "bd7_requires_build_2203", "receipts": [],
        })
        self.context.command.assert_not_called()
        self.context.capture.assert_not_called()
        self.context.borrowed_lock.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_fb1_real_plan_is_explicitly_not_device_qualification(self):
        result = subject.run("fb1", {}, self.context)
        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["reason"], "plan_only_not_device_qualification")
        self.assertFalse(result["data"]["device_qualified"])
        self.assertFalse(result["data"]["device_touched"])
        self.assertTrue(result["data"]["requires_dedicated_avd"])
        self.assertEqual(len(result["receipts"]), 1)
        plan = json.loads(Path(result["receipts"][0]).read_text())
        self.assertEqual(plan["case"], "fresh")
        self.assertFalse(plan["deviceTouched"])
        self.assertTrue(plan["freshPlan"]["requiresDedicatedAvd"])
        self.context.command.assert_not_called()
        self.context.borrowed_lock.assert_not_called()

    def test_demo_requires_explicit_synthetic_attestation(self):
        with patch.object(recorder, "record") as record:
            result = subject.run("demo", {}, self.context)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(result["reason"], "synthetic_demo_attestation_required")
        record.assert_not_called()
        self.context.capture.assert_not_called()

    def test_demo_prompt_and_fixed_cues_visible_but_paths_and_arbitrary_text_hidden(self):
        def record(options, *, lock, emit, **kwargs):
            # Keep the recorder's existing interactive prompt; no replacement
            # prompt may silently bypass preparation by the operator.
            self.assertNotIn("prompt", kwargs)
            self.assertIsNone(options.candidate_apk)
            self.assertEqual(options.private_dir, self.private)
            self.assertTrue(options.attest_synthetic_demo)
            with lock():
                print("Prepare the setup/install screen, then press Enter to start recording: ")
                emit("0s: show demo installation/setup.")
                emit("Pick the demo agent now.")
                emit("Approve a harmless demo tool now.")
                emit("PRIVATE_ACCOUNT_VALUE")
                session = self.private / "demo-fixture"
                session.mkdir(parents=True)
                emit(str(session))
                (session / "capture.mp4").write_bytes(b"mock recording")
                (session / "demo.gif").write_bytes(b"mock gif")
                (session / "REVIEW_REQUIRED.json").write_text(json.dumps({
                    "synthetic_demo_attested": True, "reviewed_for_export": False,
                    "duration_requested_seconds": options.duration,
                }))
                return session / "demo.gif"

        visible = io.StringIO()
        with patch.object(recorder, "PRIVATE_ROOT", self.private), \
                patch.object(recorder, "record", side_effect=record), \
                redirect_stdout(visible):
            result = subject.run("demo", {"synthetic_demo_attested": True}, self.context)
        text = visible.getvalue()
        self.assertIn("Prepare the setup/install screen", text)
        self.assertIn("0s: show demo installation/setup.", text)
        self.assertIn("Pick the demo agent now.", text)
        self.assertIn("Approve a harmless demo tool now.", text)
        self.assertNotIn("PRIVATE_ACCOUNT_VALUE", text)
        self.assertNotIn(str(self.private), text)
        self.context.capture.assert_not_called()
        self.context.command.assert_not_called()
        self.context.borrowed_lock.assert_called_once_with()
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(result["reason"], "manual_review_required")
        self.assertTrue(result["data"]["captured"])
        self.assertFalse(result["data"]["reviewed_for_export"])
        self.assertFalse(result["data"]["device_qualified"])
        self.assertFalse(result["data"]["exported"])
        self.assertTrue(all(Path(path).is_file() for path in result["receipts"]))
        receipt = json.loads((self.output / "demo.json").read_text())
        self.assertTrue(Path(receipt["private_artifacts"]["gif"]).is_file())
        self.assertFalse(receipt["reviewed_for_export"])
        self.assertFalse(receipt["exported"])
        self.assertFalse((self.output / "demo.gif").exists())


if __name__ == "__main__":
    unittest.main()
