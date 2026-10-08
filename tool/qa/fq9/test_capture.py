import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from .capture import BackgroundCapture, durable_write
from .common import DriverFailure


class CaptureTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "capture.json"

    def test_exclusive_durable_projection_precedes_cleanup_permission(self):
        class Ports:
            now = 0

            def monotonic(self):
                return self.now

            def diagnostic_epoch_ms(self):
                return 1000000

            def terminal_snapshot(self):
                return {"outcome": "running"}

            def diagnostic_logs(self, start, end):
                return {"app": {"captured": True}, "server": {"captured": True}}

        p = Ports()
        c = BackgroundCapture(p, self.path)
        c.begin()
        p.now = 1200
        c.observe()
        self.assertTrue(c.finish())
        r = json.loads(self.path.read_text())
        self.assertTrue(r["beforeResumeAndCleanup"])
        self.assertEqual(r["terminal"][0]["snapshot"]["outcome"], "running")
        self.assertEqual(r["logs"][0]["elapsedSeconds"], 1200)
        self.assertFalse(c.finish())  # never overwrite an earlier evidence file

    def test_capture_failure_is_durable_but_does_not_authorize_cleanup(self):
        class Ports:
            def monotonic(self):
                return 1200

            def diagnostic_epoch_ms(self):
                return 1000000

            def terminal_snapshot(self):
                raise DriverFailure("terminal_prompt_mismatch")

            def diagnostic_logs(self, *args):
                return {"app": {}, "server": {}}

        c = BackgroundCapture(Ports(), self.path)
        c.begin()
        self.assertFalse(c.finish())
        self.assertEqual(
            json.loads(self.path.read_text())["errors"], ["terminal_prompt_mismatch"]
        )

    def test_arbitrary_error_body_never_reaches_report(self):
        class Ports:
            def monotonic(self):
                return 0

            def diagnostic_epoch_ms(self):
                return 1000000

            def terminal_snapshot(self):
                raise RuntimeError("provider-password-private")

            def diagnostic_logs(self, *args):
                raise RuntimeError("account-private")

        c = BackgroundCapture(Ports(), self.path)
        c.begin()
        self.assertFalse(c.finish())
        self.assertNotIn("private", self.path.read_text())

    def test_fsync_failure_never_authorizes_and_existing_file_is_preserved(self):
        with patch("tool.qa.fq9.capture.os.fsync", side_effect=OSError):
            with self.assertRaises(DriverFailure):
                durable_write(self.path, {"schema": 1})
        self.path.unlink()
        durable_write(self.path, {"schema": 1})
        with self.assertRaises(DriverFailure):
            durable_write(self.path, {"schema": 2})
        self.assertEqual(json.loads(self.path.read_text())["schema"], 1)


if __name__ == "__main__":
    unittest.main()
