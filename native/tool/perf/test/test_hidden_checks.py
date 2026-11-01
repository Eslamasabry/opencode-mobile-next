#!/usr/bin/env python3
"""Self-tests for the PR-01 / PR-04 tooling (S2-BACKOFF). No device, no Gradle, loopback only.

    python3 -I native/tool/perf/test/test_hidden_checks.py

Each budget is checked against a clean synthetic run (it must pass) and a planted violation (it must fail).
"""
from __future__ import annotations

import contextlib
import json
import os
import random
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PERF = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PERF)
import hidden_checks as hc  # noqa: E402

T0 = 1_000_000


class Log:
    """Builds proxy.jsonl and marks.jsonl for a synthetic run."""

    def __init__(self) -> None:
        self.events: list[dict] = []
        self.marks: list[dict] = []
        self.conn = 0

    def stream(self, t: int, close_after: int | None = 50, path: str = "/api/event") -> None:
        self.conn += 1
        self.events.append({"t": t, "ev": "accept", "conn": self.conn})
        self.events.append({"t": t + 1, "ev": "request", "conn": self.conn, "method": "GET", "path": path})
        if close_after is not None:
            self.events.append({"t": t + close_after, "ev": "close", "conn": self.conn})

    def mark(self, t: int, name: str) -> None:
        self.marks.append({"t": t, "mark": name})

    def write(self, d: str, readings: dict | None = None) -> str:
        with open(os.path.join(d, "proxy.jsonl"), "w") as f:
            f.writelines(json.dumps(e) + "\n" for e in self.events)
        with open(os.path.join(d, "marks.jsonl"), "w") as f:
            f.writelines(json.dumps(m) + "\n" for m in self.marks)
        if readings is not None:
            with open(os.path.join(d, "readings.json"), "w") as f:
                json.dump(readings, f)
        return d


def read_json(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


def run_checks(d: str, scenario: str) -> tuple[int, dict]:
    with open(os.devnull, "w") as quiet, contextlib.redirect_stdout(quiet), contextlib.redirect_stderr(quiet):
        rc = hc.main([scenario, "--run", d])
    out = os.path.join(d, "PR-01.json" if scenario == "pr01" else "PR-04.json")
    return rc, (read_json(out) if os.path.exists(out) else {})


GOOD_READINGS = {"cpu_ticks": {"start": 1000, "end": 1050}, "clk_tck": 100, "wakeups": {"start": 3, "end": 3}}


def pr01_log(stream_closes_at: int = 2_000, late_request: bool = False) -> Log:
    log = Log()
    log.stream(T0, close_after=None)
    log.events.append({"t": T0 + 10_000 + stream_closes_at, "ev": "close", "conn": 1})
    log.mark(T0 + 10_000, "hidden")
    if late_request:
        log.stream(T0 + 10_000 + 600_000, path="/api/session")
    log.mark(T0 + 10_000 + 3_600_000, "end")
    return log


def pr04_log(gaps: list[int] | None = None, airplane_hits: int = 0, back: int = 1, return_ms: int = 800) -> Log:
    log = Log()
    log.stream(T0, close_after=None)
    log.mark(T0 + 5_000, "watch_hidden")
    on, off = T0 + 40_000, T0 + 640_000
    log.mark(on, "airplane_on")
    for i in range(airplane_hits):
        log.stream(on + 60_000 * (i + 1))
    log.mark(off, "airplane_off")
    for i in range(back):
        log.stream(off + 4_000 + i * 1_000, close_after=None if i == back - 1 else 10)
    down = off + 60_000
    log.mark(down, "server_down")
    rng = random.Random(9)
    gaps = gaps if gaps is not None else [2_000] + [rng.randint(60_000, 120_000) for _ in range(6)]
    t = down + 1_050
    log.stream(t)
    for g in gaps:
        t += g
        log.stream(t)
    fg = down + 600_000
    assert t < fg, "synthetic attempts must end before the foreground mark"
    log.mark(fg, "foreground")
    log.stream(fg + return_ms)
    log.mark(fg + 10_000, "server_up")
    log.mark(fg + 20_000, "end")
    return log


class Pr01Test(unittest.TestCase):
    def test_PB_53_and_PB_40_clean_idle_run_passes(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr01_log().write(d, GOOD_READINGS), "pr01")
            self.assertEqual(0, rc, out)
            self.assertEqual({"PB-53": "pass", "PB-40": "pass"}, out["budgets"])

    def test_PB_53_a_stream_still_open_after_the_grace_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr01_log(stream_closes_at=45_000).write(d, GOOD_READINGS), "pr01")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-53"])

    def test_PB_53_a_request_while_idle_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr01_log(late_request=True).write(d, GOOD_READINGS), "pr01")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-53"])
            self.assertEqual("pass", out["budgets"]["PB-40"])

    def test_PB_40_cpu_over_one_second_per_hour_fails(self) -> None:
        busy = dict(GOOD_READINGS, cpu_ticks={"start": 0, "end": 120})  # 1.2 s over ~59.5 min
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr01_log().write(d, busy), "pr01")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-40"])

    def test_PB_40_a_fired_wakeup_alarm_fails(self) -> None:
        woke = dict(GOOD_READINGS, wakeups={"start": 3, "end": 4})
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr01_log().write(d, woke), "pr01")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-40"])

    def test_missing_readings_could_not_run(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(2, run_checks(pr01_log().write(d), "pr01")[0])


class Pr04Test(unittest.TestCase):
    def test_PB_51_and_PB_52_clean_offline_run_passes(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log().write(d), "pr04")
            self.assertEqual(0, rc, out)
            self.assertEqual({"PB-52": "pass", "PB-51": "pass"}, out["budgets"])

    def test_PB_52_a_request_in_airplane_mode_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log(airplane_hits=1).write(d), "pr04")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-52"])

    def test_PB_52_a_burst_of_tries_on_return_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log(back=3).write(d), "pr04")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-52"])

    def test_PB_51_the_old_16_s_schedule_fails(self) -> None:
        # Audit #6: reconnect every 16 s hidden, a radio wake every 16 s.
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log(gaps=[2_000, 4_000, 8_000] + [16_000] * 30).write(d), "pr04")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-51"])

    def test_PB_51_a_wait_longer_than_120_s_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log(gaps=[2_000, 90_000, 200_000, 90_000]).write(d), "pr04")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-51"])

    def test_PB_51_a_slow_return_to_the_foreground_fails(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            rc, out = run_checks(pr04_log(return_ms=45_000).write(d), "pr04")
            self.assertEqual(1, rc)
            self.assertEqual("fail", out["budgets"]["PB-51"])

    def test_a_missing_mark_could_not_run(self) -> None:
        log = pr04_log()
        log.marks = [m for m in log.marks if m["mark"] != "foreground"]
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(2, run_checks(log.write(d), "pr04")[0])


class _Upstream(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path.startswith("/api/event"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            try:
                while True:
                    self.wfile.write(b": heartbeat\n\n")
                    self.wfile.flush()
                    time.sleep(0.1)
            except OSError:
                return
        body = b"{}"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args: object) -> None:
        pass


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class ProxyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.dir = tempfile.TemporaryDirectory()
        self.upstream = ThreadingHTTPServer(("127.0.0.1", 0), _Upstream)
        threading.Thread(target=self.upstream.serve_forever, daemon=True).start()
        self.port = free_port()
        self.log = os.path.join(self.dir.name, "proxy.jsonl")
        self.down = os.path.join(self.dir.name, "down")
        self.proc = subprocess.Popen([
            sys.executable, "-I", os.path.join(PERF, "conn_log_proxy.py"), "--listen-port", str(self.port),
            "--upstream-port", str(self.upstream.server_address[1]), "--log", self.log, "--down-flag", self.down,
        ])
        for _ in range(50):
            try:
                socket.create_connection(("127.0.0.1", self.port), timeout=0.2).close()
                break
            except OSError:
                time.sleep(0.1)

    def tearDown(self) -> None:
        self.proc.kill()  # exact PID, never by pattern
        self.proc.wait()
        self.upstream.shutdown()
        self.dir.cleanup()

    def events(self) -> list[dict]:
        with open(self.log) as f:
            return [json.loads(line) for line in f if line.strip()]

    def request(self, path: str) -> socket.socket:
        s = socket.create_connection(("127.0.0.1", self.port), timeout=5)
        auth = "b3BlbmNvZGU6c2VjcmV0"  # never logged
        s.sendall(f"GET {path} HTTP/1.1\r\nHost: x\r\nAuthorization: Basic {auth}\r\n\r\n".encode())
        return s

    def test_logs_attempts_without_queries_or_credentials(self) -> None:
        s = self.request("/api/session?directory=/home/someone")
        self.assertIn(b"200", s.recv(1024))
        s.close()
        time.sleep(0.3)
        with open(self.log) as f:
            text = f.read()
        self.assertNotIn("directory", text)
        self.assertNotIn("b3BlbmNvZGU6c2VjcmV0", text)
        self.assertNotIn("Authorization", text)
        reqs = [e for e in self.events() if e["ev"] == "request"]
        self.assertEqual([("GET", "/api/session")], [(e["method"], e["path"]) for e in reqs])

    def test_down_flag_cuts_the_live_stream_and_logs_each_refused_attempt(self) -> None:
        s = self.request("/api/event")
        self.assertIn(b"200", s.recv(1024))
        open(self.down, "w").close()
        s.settimeout(3)
        deadline = time.time() + 3
        while time.time() < deadline:
            if not s.recv(1024):
                break
        else:
            self.fail("the live stream was not cut")
        s.close()
        for _ in range(3):
            r = self.request("/api/event")
            r.settimeout(3)
            self.assertEqual(b"", r.recv(1024))  # closed with no response
            r.close()
        time.sleep(0.3)
        attempts = [e for e in self.events() if e["ev"] == "request" and e["path"] == "/api/event"]
        self.assertEqual(4, len(attempts))


if __name__ == "__main__":
    unittest.main(verbosity=2)
