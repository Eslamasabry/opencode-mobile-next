"""FX-CTL: the flows' one control address forwards [fx] steps to the right fixture (S0-FX, E2E-39 Part A/C).

Starts FX-OC2 (on the fake opencode2) and FX-CTL in one run dir on free ports, then drives FX-CTL the way
flows/_fx/fx.js does: POST /fx {target, action, args}.
"""

from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import time
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from fxtest import basic, fxctl, pid_alive, request  # noqa: E402
from fxlib.core import free_port, load_ports  # noqa: E402

FAKE = HERE / "fake_opencode2.py"


class FxCtlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.run_dir = Path(tempfile.mkdtemp(prefix="fx-ctl-test-"))
        args = ["start", "--run-dir", str(cls.run_dir), "FX-OC2", "FX-CTL"]
        cls.ports = {}
        for fixture_id in ("FX-OC2", "FX-CTL", "FX-USAGE"):
            cls.ports[fixture_id] = {name: free_port() for name in load_ports(fixture_id)}
        for fixture_id in ("FX-OC2", "FX-CTL"):
            for name, port in cls.ports[fixture_id].items():
                args += ["--port", f"{fixture_id}:{name}={port}"]
        env = {**os.environ, "FX_OC2_BIN": str(FAKE), "FX_USAGE_PORT": str(cls.ports["FX-USAGE"]["http"])}
        result = fxctl(*args, env=env)
        if result.returncode != 0:
            shutil.rmtree(cls.run_dir, ignore_errors=True)
            raise RuntimeError(f"start failed: {result.stdout}{result.stderr}")
        cls.pids = [json.loads((cls.run_dir / "pids" / f"{f}.json").read_text())["pid"] for f in ("FX-OC2", "FX-CTL")]
        cls.secrets = dict(line.split("=", 1) for line in
                           (cls.run_dir / "secrets.env").read_text().splitlines() if "=" in line)

    @classmethod
    def tearDownClass(cls) -> None:
        result = fxctl("stop", "--run-dir", str(cls.run_dir))
        try:
            assert result.returncode == 0, result.stdout + result.stderr
            deadline = time.monotonic() + 5
            while any(pid_alive(p) for p in cls.pids) and time.monotonic() < deadline:
                time.sleep(0.05)
            assert not any(pid_alive(p) for p in cls.pids), "a fixture survived stop"
        finally:
            shutil.rmtree(cls.run_dir, ignore_errors=True)

    def fx(self, target: str, action: str, args: dict | None = None):
        return request("POST", self.ports["FX-CTL"]["http"], "/fx",
                       {"target": target, "action": action, "args": args or {}}, timeout=30)

    def front(self, method: str, path: str, body=None):
        return request(method, self.ports["FX-OC2"]["front"], path, body,
                       headers=basic("opencode", self.secrets["FX_OC2_PASSWORD"]))

    def test_FX_CTL_seed_sessions_creates_titled_sessions_once(self) -> None:
        resp = self.fx("FX-OC2", "seed-sessions", {"project": "app", "titles": ["Seed one", "Seed two"]})
        self.assertEqual(resp.status, 200, resp.text)
        first = resp.json()["sessions"]
        self.assertEqual(sorted(first), ["Seed one", "Seed two"])
        again = self.fx("FX-OC2", "seed-sessions", {"project": "app", "titles": ["Seed one"]}).json()["sessions"]
        self.assertEqual(again["Seed one"], first["Seed one"])
        titles = [s.get("title") for s in self.front("GET", "/api/session").json()["data"]]
        self.assertEqual(titles.count("Seed one"), 1)

    def test_FX_CTL_request_log_counts_prompts_with_that_text(self) -> None:
        sid = self.fx("FX-OC2", "seed-sessions", {"titles": ["Counted"]}).json()["sessions"]["Counted"]
        self.assertEqual(self.fx("FX-OC2", "request-log", {"text": "e2e:echo counted"}).json()["count"], 0)
        self.front("POST", f"/api/session/{sid}/prompt", {"text": "e2e:echo counted"})
        self.assertEqual(self.fx("FX-OC2", "request-log", {"text": "e2e:echo counted"}).json()["count"], 1)

    def test_FX_CTL_cut_host_drops_the_front_port_and_restore_brings_it_back(self) -> None:
        port = self.ports["FX-OC2"]["front"]
        self.assertEqual(self.front("GET", "/api/info").status, 200)
        cut = self.fx("fx-proxy", "cut-host", {"host": "10.0.2.2", "port": port})
        self.assertEqual(cut.status, 200, cut.text)
        self.assertEqual(cut.json()["cut"], "FX-OC2")
        with self.assertRaises(OSError):
            self.front("GET", "/api/info")
        self.assertEqual(self.fx("fx-proxy", "restore-host", {"host": "10.0.2.2", "port": port}).status, 200)
        self.assertEqual(self.front("GET", "/api/info").status, 200)

    def test_FX_CTL_unknown_targets_and_actions_fail_the_step(self) -> None:
        self.assertEqual(self.fx("FX-NOPE", "anything").status, 404)
        self.assertEqual(self.fx("fx-proxy", "explode", {"port": 1}).status, 404)
        self.assertEqual(self.fx("fx-proxy", "cut-host", {"port": 1}).status, 404)
        self.assertEqual(self.fx("FX-OC2", "no-such-action").status, 404)
        self.assertEqual(self.fx("FX-OC2", "seed-sessions", {"titles": []}).status, 400)


if __name__ == "__main__":
    unittest.main()
