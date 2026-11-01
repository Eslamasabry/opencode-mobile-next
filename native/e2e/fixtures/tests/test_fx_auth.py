"""FX-AUTH self-tests: the sign-in stub for phone agents (E2E-31).

Rows served: B-AGENT-129 and B-LINUX-125 (the sign-in terminal runs the
sign-in script and nothing else; the stub prints a fixed code the flow can
assert on). The shim is run here with plain python3, as the phone's terminal
would run it inside built-in Linux.
"""

import os
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

from tests.fxtest import FixtureCase

FIXED_CODE = "FXAU-4242"


class FxAuthTest(FixtureCase):
    FIXTURE = "FX-AUTH"

    def setUp(self) -> None:
        self.ctl("reset")

    def shim(self, agent: str) -> Path:
        r = self.http("GET", f"/shim/{agent}")
        self.assertEqual(r.status, 200)
        path = Path(tempfile.mkdtemp(prefix="fx-auth-")) / agent
        path.write_bytes(r.body)
        path.chmod(0o755)
        return path

    def run_login(self, agent: str, args: list[str], timeout: float = 20) -> subprocess.CompletedProcess:
        env = {**os.environ, "FX_AUTH_URL": f"http://127.0.0.1:{self.port('http')}", "FX_AUTH_POLL_MS": "100"}
        return subprocess.run([sys.executable, str(self.shim(agent)), *args], capture_output=True, text=True,
                              timeout=timeout, env=env)

    def test_FX_AUTH_start_gives_the_fixed_code(self) -> None:
        r = self.http("POST", "/device/start", {"agent": "claude"})
        self.assertEqual(r.json()["user_code"], FIXED_CODE)

    def test_B_AGENT_129_claude_login_prints_the_code_and_succeeds_after_approval(self) -> None:
        def approve_soon() -> None:
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                if self.ctl("state", method="GET").json()["pending"]:
                    self.ctl("approve", {})
                    return
                time.sleep(0.05)
        threading.Thread(target=approve_soon, daemon=True).start()
        result = self.run_login("claude", ["auth", "login"])
        self.assertIn(FIXED_CODE, result.stdout)
        self.assertIn("Signed in", result.stdout)
        self.assertEqual(result.returncode, 0)

    def test_B_LINUX_125_codex_login_denied_exits_non_zero(self) -> None:
        def deny_soon() -> None:
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                if self.ctl("state", method="GET").json()["pending"]:
                    self.ctl("deny", {})
                    return
                time.sleep(0.05)
        threading.Thread(target=deny_soon, daemon=True).start()
        result = self.run_login("codex", ["login"])
        self.assertIn(FIXED_CODE, result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_FX_AUTH_shim_version_works_offline(self) -> None:
        result = self.run_login("claude", ["--version"])
        self.assertEqual(result.returncode, 0)
        self.assertIn("fixture", result.stdout)

    def test_FX_AUTH_status_after_approval(self) -> None:
        self.http("POST", "/device/start", {"agent": "pi"})
        self.ctl("approve", {})
        state = self.ctl("state", method="GET").json()
        self.assertIn("pi", state["signed_in"])

    def test_FX_AUTH_unknown_shim_is_404(self) -> None:
        self.assertEqual(self.http("GET", "/shim/rm").status, 404)


if __name__ == "__main__":
    unittest.main()
