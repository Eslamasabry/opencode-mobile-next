"""fxctl and fxlib self-tests: start/stop by exact PID, faults, request-log scrubbing.

Uses FX-GH as the carrier fixture because it is the smallest.
"""

import json
import os
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

from tests.fxtest import FixtureCase, ROOT, fxctl, pid_alive, request

sys.path.insert(0, str(ROOT))
from fxlib.core import SCRUB, Faults, Scrubber, free_port, load_ports  # noqa: E402


class FxctlLifecycleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.run_dir = Path(tempfile.mkdtemp(prefix="fx-ctl-"))
        self.ports = [f"FX-GH:{name}={free_port()}" for name in load_ports("FX-GH")]

    def tearDown(self) -> None:
        fxctl("stop", "--run-dir", str(self.run_dir))
        shutil.rmtree(self.run_dir, ignore_errors=True)

    def start(self) -> subprocess.CompletedProcess:
        args = ["start", "--run-dir", str(self.run_dir), "FX-GH"]
        for p in self.ports:
            args += ["--port", p]
        return fxctl(*args)

    def test_FXCTL_start_writes_pid_ready_log_and_secrets_0600(self) -> None:
        self.assertEqual(self.start().returncode, 0)
        record = json.loads((self.run_dir / "pids" / "FX-GH.json").read_text())
        self.assertTrue(pid_alive(record["pid"]))
        ready = json.loads((self.run_dir / "FX-GH" / "ready.json").read_text())
        self.assertEqual(ready["pid"], record["pid"])
        self.assertIn("ready", (self.run_dir / "logs" / "FX-GH.log").read_text())
        mode = stat.S_IMODE((self.run_dir / "secrets.env").stat().st_mode)
        self.assertEqual(mode, 0o600)

    def test_FXCTL_second_start_reuses_the_running_fixture(self) -> None:
        self.start()
        first = json.loads((self.run_dir / "pids" / "FX-GH.json").read_text())["pid"]
        out = self.start()
        self.assertIn("already running", out.stdout)
        self.assertEqual(json.loads((self.run_dir / "pids" / "FX-GH.json").read_text())["pid"], first)

    def test_FXCTL_stop_kills_exactly_that_pid(self) -> None:
        bystander = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
        try:
            self.start()
            pid = json.loads((self.run_dir / "pids" / "FX-GH.json").read_text())["pid"]
            self.assertEqual(fxctl("stop", "--run-dir", str(self.run_dir)).returncode, 0)
            time.sleep(0.2)
            self.assertFalse(pid_alive(pid))
            self.assertIsNone(bystander.poll())
            self.assertFalse((self.run_dir / "pids" / "FX-GH.json").exists())
        finally:
            bystander.kill()
            bystander.wait()

    def test_FXCTL_never_kills_a_reused_pid(self) -> None:
        # A stale PID file pointing at an unrelated live process (PID reuse) is left alone.
        bystander = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
        try:
            (self.run_dir / "pids").mkdir(parents=True)
            (self.run_dir / "pids" / "FX-GH.json").write_text(json.dumps({
                "pid": bystander.pid, "starttime": "1", "script": str(ROOT / "servers" / "fx_gh.py"),
                "run_dir": str(self.run_dir)}))
            fxctl("stop", "--run-dir", str(self.run_dir), "FX-GH")
            time.sleep(0.2)
            self.assertIsNone(bystander.poll())
        finally:
            bystander.kill()
            bystander.wait()

    def test_FXCTL_unknown_fixture_is_a_usage_error(self) -> None:
        self.assertEqual(fxctl("start", "--run-dir", str(self.run_dir), "FX-NOPE").returncode, 2)

    def test_FXCTL_port_in_use_fails_to_start_and_leaves_nothing(self) -> None:
        port = int(self.ports[0].rsplit("=", 1)[1])
        with socket.socket() as blocker:
            blocker.bind(("127.0.0.1", port))
            blocker.listen()
            out = self.start()
        self.assertEqual(out.returncode, 1)
        self.assertIn("failed to start", out.stderr)
        self.assertFalse((self.run_dir / "pids" / "FX-GH.json").exists())


class FaultsTest(FixtureCase):
    FIXTURE = "FX-GH"

    def setUp(self) -> None:
        self.ctl("reset")

    def test_FXLIB_refuse_answers_the_status(self) -> None:
        self.ctl("fault", {"path": "/user", "action": "refuse", "status": 502})
        self.assertEqual(self.http("GET", "/user").status, 502)

    def test_FXLIB_count_limits_a_rule(self) -> None:
        self.ctl("fault", {"path": "/user", "action": "refuse", "count": 1})
        self.assertEqual(self.http("GET", "/user").status, 503)
        self.assertEqual(self.http("GET", "/user").status, 401)

    def test_FXLIB_delay_slows_the_answer(self) -> None:
        self.ctl("fault", {"path": "/user", "action": "delay", "ms": 400})
        started = time.monotonic()
        self.http("GET", "/user")
        self.assertGreaterEqual(time.monotonic() - started, 0.38)

    def test_FXLIB_hang_holds_until_release_then_closes(self) -> None:
        self.ctl("fault", {"path": "/user", "action": "hang", "ms": 10000})
        import threading
        threading.Timer(0.4, lambda: self.ctl("release")).start()
        started = time.monotonic()
        with self.assertRaises(Exception):
            self.http("GET", "/user", timeout=5)
        self.assertGreaterEqual(time.monotonic() - started, 0.35)
        self.assertLess(time.monotonic() - started, 4)

    def test_FXLIB_drop_closes_without_an_answer(self) -> None:
        self.ctl("fault", {"path": "/user", "action": "drop"})
        with self.assertRaises(Exception):
            self.http("GET", "/user")
        self.assertEqual(self.requests_log()[-1]["fault"], "drop")

    def test_FXLIB_bad_fault_is_400(self) -> None:
        self.assertEqual(self.ctl("fault", {"path": "/user", "action": "explode"}).status, 400)
        self.assertEqual(self.ctl("fault", {"path": "user", "action": "drop"}).status, 400)

    def test_FXLIB_health_reports_pid_and_ports(self) -> None:
        health = self.ctl("health", method="GET").json()
        self.assertEqual(health["pid"], self.record["pid"])
        self.assertEqual(health["ports"]["http"], self.port("http"))

    def test_FXLIB_log_has_no_query_strings(self) -> None:
        self.http("GET", "/user?access_token=leak-me-123")
        self.assertTrue(all("?" not in e["path"] for e in self.requests_log()))
        self.assert_no_secret_in_run_dir("leak-me-123")


class ScrubberTest(unittest.TestCase):
    def test_FXLIB_scrubber_masks_value_and_basic_forms(self) -> None:
        import base64
        s = Scrubber()
        s.add("hunter2-secret")
        b64 = base64.b64encode(b"opencode:hunter2-secret").decode()
        self.assertEqual(s(f"a hunter2-secret b {b64}"), "a <redacted> b <redacted>")

    def test_FXLIB_faults_match_method_and_prefix(self) -> None:
        f = Faults()
        f.add({"path": "/api/event", "method": "GET", "action": "cut"})
        self.assertIsNone(f.match("POST", "/api/event"))
        self.assertEqual(f.match("GET", "/api/event/x")["action"], "cut")


if __name__ == "__main__":
    unittest.main()
