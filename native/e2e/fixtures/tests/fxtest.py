"""Test helpers for the fixture self-tests (stdlib unittest).

Each test class names FIXTURE; setUpClass starts it through fxctl on free
ports in a temporary run dir, tearDownClass stops it by PID and checks the PID
is gone. Run every self-test with:

    python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures
"""

from __future__ import annotations

import base64
import http.client
import json
import os
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))

from fxlib.core import free_port, load_ports  # noqa: E402

FXCTL = ROOT / "fxctl.py"


def fxctl(*args: str, env: dict[str, str] | None = None, timeout: float = 200) -> subprocess.CompletedProcess[str]:
    return subprocess.run([sys.executable, "-I", str(FXCTL), *args], capture_output=True, text=True,
                          timeout=timeout, env=env)


def pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    try:
        state = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()[0]
    except OSError:
        return False
    return state != "Z"


class Response:
    def __init__(self, status: int, headers: dict[str, str], body: bytes) -> None:
        self.status, self.headers, self.body = status, headers, body

    def json(self) -> Any:
        return json.loads(self.body)

    @property
    def text(self) -> str:
        return self.body.decode("utf-8", "replace")


def request(method: str, port: int, path: str, body: Any = None, headers: dict[str, str] | None = None,
            timeout: float = 10, form: bool = False, tls: ssl.SSLContext | None = None,
            host: str = "127.0.0.1") -> Response:
    if tls is not None:
        conn: http.client.HTTPConnection = http.client.HTTPSConnection(host, port, timeout=timeout, context=tls)
    else:
        conn = http.client.HTTPConnection(host, port, timeout=timeout)
    hdrs = dict(headers or {})
    raw: bytes | None = None
    if body is not None:
        if isinstance(body, bytes):
            raw = body
        elif form:
            from urllib.parse import urlencode
            raw = urlencode(body).encode()
            hdrs.setdefault("Content-Type", "application/x-www-form-urlencoded")
        else:
            raw = json.dumps(body).encode()
            hdrs.setdefault("Content-Type", "application/json")
    try:
        conn.request(method, path, body=raw, headers=hdrs)
        resp = conn.getresponse()
        return Response(resp.status, {k.lower(): v for k, v in resp.getheaders()}, resp.read())
    finally:
        conn.close()


def basic(user: str, password: str) -> dict[str, str]:
    return {"Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()}


class FixtureCase(unittest.TestCase):
    FIXTURE: str = ""
    EXTRA_ENV: dict[str, str] = {}
    START_TIMEOUT: float | None = None

    @classmethod
    def setUpClass(cls) -> None:
        cls.run_dir = Path(tempfile.mkdtemp(prefix="fx-test-"))
        cls.ports = {name: free_port() for name in load_ports(cls.FIXTURE)}
        args = ["start", "--run-dir", str(cls.run_dir), cls.FIXTURE]
        for name, port in cls.ports.items():
            args += ["--port", f"{cls.FIXTURE}:{name}={port}"]
        if cls.START_TIMEOUT:
            args += ["--timeout", str(cls.START_TIMEOUT)]
        env = {**os.environ, **cls.EXTRA_ENV}
        result = fxctl(*args, env=env)
        if result.returncode != 0:
            log = cls.run_dir / "logs" / f"{cls.FIXTURE}.log"
            detail = log.read_text(errors="replace") if log.exists() else ""
            shutil.rmtree(cls.run_dir, ignore_errors=True)
            raise RuntimeError(f"start failed: {result.stdout}{result.stderr}\n{detail}")
        cls.record = json.loads((cls.run_dir / "pids" / f"{cls.FIXTURE}.json").read_text())
        cls.ready = json.loads((cls.run_dir / cls.FIXTURE / "ready.json").read_text())
        cls.secrets = dict(line.split("=", 1) for line in
                           (cls.run_dir / "secrets.env").read_text().splitlines() if "=" in line)

    @classmethod
    def tearDownClass(cls) -> None:
        pid = cls.record["pid"]
        children_file = cls.run_dir / cls.FIXTURE / "children.json"
        children = json.loads(children_file.read_text()) if children_file.exists() else []
        result = fxctl("stop", "--run-dir", str(cls.run_dir), cls.FIXTURE)
        try:
            assert result.returncode == 0, result.stdout + result.stderr
            deadline = time.monotonic() + 5
            while pid_alive(pid) and time.monotonic() < deadline:
                time.sleep(0.05)
            assert not pid_alive(pid), f"{cls.FIXTURE} pid {pid} survived stop"
            for row in children:
                assert not pid_alive(row["pid"]), f"child {row} survived stop"
        finally:
            shutil.rmtree(cls.run_dir, ignore_errors=True)

    # -- helpers ---------------------------------------------------------------

    def port(self, name: str) -> int:
        return self.ports[name]

    def http(self, method: str, path: str, body: Any = None, port: str = "http", **kw: Any) -> Response:
        return request(method, self.ports[port], path, body, **kw)

    def ctl(self, action: str, body: Any = None, method: str = "POST") -> Response:
        return request(method, self.ports["ctl"], "/" + action.lstrip("/"), body if body is not None else ({} if method == "POST" else None))

    def requests_log(self) -> list[dict[str, Any]]:
        return self.ctl("log", method="GET").json()["requests"]

    def assert_no_secret_in_run_dir(self, *values: str) -> None:
        """No file in the run dir (logs, request log, state) holds a secret, except secrets.env."""
        for path in self.run_dir.rglob("*"):
            if not path.is_file() or path.name == "secrets.env":
                continue
            data = path.read_bytes()
            for value in values:
                if value:
                    self.assertNotIn(value.encode(), data, f"secret leaked into {path}")
