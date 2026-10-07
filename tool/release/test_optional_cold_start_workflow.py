"""Execute only the release workflow's optional receipt block with fake tools."""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

WORKFLOW = Path(os.environ.get(
    "RELEASE_RECEIPT_TEST_WORKFLOW",
    Path(__file__).resolve().parents[2] / ".github/workflows/android-release.yml",
))
RECORD_STEP = "Record optional cold-start receipt"
UPLOAD_STEP = "Upload optional cold-start receipt"


class OptionalColdStartWorkflowTest(unittest.TestCase):
    def setUp(self):
        # Extract literal steps/block scalars from the actual source without a
        # third-party YAML dependency in CI's stdlib release-test discovery.
        # Full YAML parsing is a separate local workflow validation.
        self.steps = []
        for block in re.split(r"(?m)^      - name: ", WORKFLOW.read_text())[1:]:
            name, body = block.split("\n", 1)
            step = {"name": name}
            for field in ["if", "continue-on-error"]:
                value = re.search(rf"(?m)^        {field}: (.+)$", body)
                if value:
                    step[field] = value[1] == "true" if field == "continue-on-error" else value[1]
            for field in ["path", "if-no-files-found"]:
                value = re.search(rf"(?m)^          {field}: (.+)$", body)
                if value:
                    step.setdefault("with", {})[field] = value[1]
            if "        run: |\n" in body:
                lines = body.split("        run: |\n", 1)[1].splitlines()
                run = []
                for line in lines:
                    if line and not line.startswith("          "):
                        break
                    run.append(line[10:] if line else "")
                step["run"] = "\n".join(run) + "\n"
            else:
                scalar = re.search(r"(?m)^        run: (.+)$", body)
                if scalar:
                    step["run"] = scalar[1] + "\n"
            self.steps.append(step)
        self.record = next(step for step in self.steps if step.get("name") == RECORD_STEP)
        self.upload = next(step for step in self.steps if step.get("name") == UPLOAD_STEP)
        self.temp = tempfile.TemporaryDirectory(prefix="optional-cold-start-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        tools = self.root / "fake-bin"
        tools.mkdir()
        python = tools / "python3"
        python.write_text(
            '#!/bin/bash\nprintf "%s\\n" "$@" > "$FAKE_CALLS"\n'
            'exit "${FAKE_VERIFY_EXIT:-0}"\n',
        )
        python.chmod(0o700)
        self.receipt = self.root / "docs/releases/v1.2.3+45-cold-start.json"
        self.copied = self.root / "build/release-metrics/cold-start.json"
        self.calls = self.root / "calls.txt"
        self.env = dict(os.environ, BUILD_NAME="1.2.3", BUILD_NUMBER="45",
                        FAKE_CALLS=str(self.calls), PATH=f"{tools}:{os.environ['PATH']}")

    def create_receipt(self):
        self.receipt.parent.mkdir(parents=True)
        self.receipt.write_text('{"synthetic": "receipt-only"}\n')

    def run_block(self, verifier_exit=0):
        return subprocess.run(
            ["bash", "-c", self.record["run"]], cwd=self.root,
            env=dict(self.env, FAKE_VERIFY_EXIT=str(verifier_exit)),
            capture_output=True, text=True, check=False,
        )

    def test_missing_receipt_warns_and_does_not_block_or_call_verifier(self):
        result = self.run_block(verifier_exit=1)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("::warning::No cold-start receipt", result.stdout)
        self.assertFalse(self.calls.exists())
        self.assertFalse(self.copied.exists())

    def test_present_receipt_is_copied_and_verified(self):
        self.create_receipt()
        result = self.run_block()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.copied.read_bytes(), self.receipt.read_bytes())
        self.assertEqual(self.calls.read_text().splitlines(), [
            "tool/release/verify_cold_start.py", "--apk",
            "build/app/outputs/flutter-apk/app-release.apk", "--receipt",
            "docs/releases/v1.2.3+45-cold-start.json",
        ])

    def test_verification_failure_warns_but_keeps_receipt_and_continues(self):
        self.create_receipt()
        result = self.run_block(verifier_exit=7)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("::warning::Optional cold-start receipt verification failed", result.stdout)
        self.assertEqual(self.copied.read_bytes(), self.receipt.read_bytes())

    def test_copy_failure_warns_and_continues(self):
        self.create_receipt()
        (self.root / "build").mkdir()
        (self.root / "build/release-metrics").write_text("not a directory")
        result = self.run_block()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("::warning::Could not copy", result.stdout)
        self.assertTrue(self.calls.exists())

    def test_receipt_recording_and_upload_failures_are_nonblocking(self):
        self.assertIs(self.record.get("continue-on-error"), True)
        self.assertIs(self.upload.get("continue-on-error"), True)
        self.assertIn("always()", self.record["if"])
        self.assertIn("always()", self.upload["if"])
        self.assertEqual(self.upload["with"]["path"], "build/release-metrics/cold-start.json")
        self.assertEqual(self.upload["with"]["if-no-files-found"], "ignore")

    def test_apk_size_remains_blocking_and_no_receipt_check_precedes_upload(self):
        build = next(step for step in self.steps if step.get("name") == "Build and upload Shorebird release APK")
        self.assertNotIn("continue-on-error", build)
        self.assertEqual(build["run"].count("python3 tool/release/check_apk_size.py"), 2)
        self.assertNotIn("verify_cold_start.py", build["run"])
        # No build/release commands are executed by this test.
        self.assertIn("set -euo pipefail", build["run"])
        self.assertNotIn("|| true", build["run"])

    def test_all_shell_blocks_parse_without_running_them(self):
        for step in self.steps:
            if "run" in step:
                with self.subTest(step=step["name"]):
                    result = subprocess.run(["bash", "-n"], input=step["run"],
                                            capture_output=True, text=True, check=False)
                    self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
