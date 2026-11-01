"""Self-tests for FX-OC2 and FX-OC2-B (servers/fx_oc2.py, servers/fx_oc2_b.py).

opencode2 is not installed in the dev container, so these run the launchers against
tests/fake_opencode2.py (FX_OC2_BIN points at that executable file).

    python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures -p 'test_fx_oc2.py' -v
"""

from __future__ import annotations

import base64
import http.client
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from tests.fxtest import FixtureCase, basic, fxctl, pid_alive, request

HERE = Path(__file__).resolve().parent
SERVERS = HERE.parent / "servers"
sys.path.insert(0, str(SERVERS))

import fx_oc2  # noqa: E402
import fx_oc2_b  # noqa: E402
from fxlib.core import free_port, load_ports  # noqa: E402

FAKE = HERE / "fake_opencode2.py"
PROMPT_MARKER = "walrus-velvet-prompt-marker"


def sse_lines(port: int, path: str, headers: dict[str, str], want: int, timeout: float = 5.0) -> tuple[list[str], bool]:
    """Read up to ``want`` data lines from an SSE response; returns (lines, ended_early)."""
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
    conn.request("GET", path, headers=headers)
    resp = conn.getresponse()
    assert resp.status == 200, resp.status
    buf, lines, ended = b"", [], False
    deadline = time.monotonic() + timeout
    try:
        while len(lines) < want and time.monotonic() < deadline:
            data = resp.read1(4096)
            if not data:
                ended = True
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                if line.startswith(b"data: "):
                    lines.append(line[6:].decode())
    except (OSError, http.client.HTTPException):
        ended = True
    finally:
        conn.close()
    return lines, ended


class Oc2Base(FixtureCase):
    PASSWORD_KEY = "FX_OC2_PASSWORD"

    def setUp(self) -> None:
        self.auth = basic("opencode", self.secrets[self.PASSWORD_KEY])

    def tearDown(self) -> None:
        self.ctl("faults/clear")
        self.ctl("lose-clear")

    def front(self, method: str, path: str, body: Any = None, auth: bool = True, **kw: Any):
        return self.http(method, path, body, port="front", headers=self.auth if auth else {}, **kw)

    def wait_log(self, pred, timeout: float = 5) -> list[dict[str, Any]]:
        deadline = time.monotonic() + timeout
        rows: list[dict[str, Any]] = []
        while time.monotonic() < deadline:
            rows = self.requests_log()
            if any(pred(r) for r in rows):
                return rows
            time.sleep(0.05)
        return rows


class FxOc2Test(Oc2Base):
    FIXTURE = "FX-OC2"
    EXTRA_ENV = {"FX_OC2_BIN": str(FAKE), "FX_STUB_SPEED": "100", "FX_OC2_SESSIONS": "basic,long-history",
                 "FX_USAGE_PORT": "0"}

    @classmethod
    def setUpClass(cls) -> None:
        # A stand-in FX-USAGE on a free port, to prove /ocmn/quota/* routing.
        seen: list[dict[str, str]] = []

        class Usage(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def log_message(self, *a: Any) -> None:
                return

            def do_GET(self) -> None:  # noqa: N802
                seen.append({"path": self.path, "auth": self.headers.get("Authorization") or ""})
                raw = json.dumps({"usage": "stub"}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

        cls.usage_seen = seen
        cls.usage = ThreadingHTTPServer(("127.0.0.1", free_port()), Usage)
        threading.Thread(target=cls.usage.serve_forever, daemon=True).start()
        cls.EXTRA_ENV = {**cls.EXTRA_ENV, "FX_USAGE_PORT": str(cls.usage.server_address[1])}
        super().setUpClass()

    @classmethod
    def tearDownClass(cls) -> None:
        try:
            super().tearDownClass()
        finally:
            cls.usage.shutdown()
            cls.usage.server_close()

    # -- start and seeding ----------------------------------------------------------

    def test_FX_OC2_ready_json_names_front_projects_sessions_never_password(self) -> None:
        r = self.ready
        self.assertEqual(r["front_url"], f"http://127.0.0.1:{self.port('front')}")
        self.assertEqual(r["model_url"], f"http://127.0.0.1:{self.port('model')}/v1")
        self.assertEqual(r["password_env"], "FX_OC2_PASSWORD")
        self.assertEqual(set(r["projects"]), {"app", "other"})
        self.assertEqual(r["sessions"]["shared-login"], fx_oc2.SHARED_IDS["shared-login"])
        self.assertEqual(r["shared_session_ids"], sorted(fx_oc2.SHARED_IDS.values()))
        self.assertEqual(r["permissions"], "ask")
        self.assertIn("long-history", r["session_sets"])
        self.assertNotIn(self.secrets["FX_OC2_PASSWORD"], json.dumps(r))
        state = self.ctl("health", method="GET").json()["state"]
        self.assertEqual(state["upstream_pid"], r["upstream_pid"])

    def test_FX_OC2_app_project_is_git_repo_with_seeded_files(self) -> None:
        app = Path(self.ready["projects"]["app"])
        for name in ("README.md", "notes.txt", "data.csv", "lib/main.dart", "assets/icon.png"):
            self.assertTrue((app / name).is_file(), name)
        self.assertGreaterEqual(len((app / "lib/main.dart").read_text().splitlines()), 42)
        self.assertTrue((app / "assets/icon.png").read_bytes().startswith(b"\x89PNG"))
        log = subprocess.run(["git", "-C", str(app), "log", "--format=%an <%ae>"], capture_output=True, text=True)
        self.assertEqual(log.stdout.splitlines(), ["fx <fx@example.invalid>"])
        status = subprocess.run(["git", "-C", str(app), "status", "--porcelain"], capture_output=True, text=True)
        self.assertEqual(status.stdout, "")  # opencode.json is excluded
        other = Path(self.ready["projects"]["other"])
        self.assertTrue((other / "README.md").is_file())
        self.assertFalse((other / ".git").exists())

    def test_FX_OC2_opencode_json_points_at_stub_with_fake_key_and_asks_first(self) -> None:
        root = Path(self.ready["data_root"])
        for path in (root / "config/opencode/opencode.json", Path(self.ready["projects"]["app"]) / "opencode.json",
                     Path(self.ready["projects"]["other"]) / "opencode.json"):
            config = json.loads(path.read_text())
            provider = config["providers"]["fx-stub"]
            self.assertEqual(provider["settings"]["baseURL"], f"http://127.0.0.1:{self.port('model')}/v1")
            self.assertEqual(provider["settings"]["apiKey"], "sk-e2e-FAKE-0123456789abcdef")
            self.assertEqual(config["model"], "fx-stub/stub")
            asks = {(r["action"], r["effect"]) for r in config["permissions"]}
            self.assertIn(("edit", "ask"), asks)
            self.assertIn(("shell", "ask"), asks)

    def test_FX_OC2_permissions_allow_drops_ask_rules(self) -> None:
        self.assertNotIn("permissions", fx_oc2.opencode_config(1, "allow"))

    def test_FX_OC2_server_runs_in_isolated_data_root(self) -> None:
        info = self.front("GET", "/api/fx/env").json()
        root = self.ready["data_root"]
        for key, value in info["env"].items():
            self.assertTrue(value.startswith(root + "/"), f"{key}={value}")
        self.assertEqual(info["cwd"], self.ready["projects"]["app"])
        self.assertTrue(info["has_password"])

    def test_FX_OC2_front_without_auth_gets_upstream_401(self) -> None:
        resp = self.front("GET", "/api/health", auth=False)
        self.assertEqual(resp.status, 401)
        self.assertIn("basic", resp.headers.get("www-authenticate", "").lower())
        self.assertEqual(self.front("GET", "/api/health").status, 200)

    def test_FX_OC2_seeded_sessions_have_fixed_ids_and_odd_titles(self) -> None:
        rows = self.front("GET", "/api/session").json()["data"]
        titles = {s["id"]: s.get("title") for s in rows}
        for seed in fx_oc2.OC2_SESSIONS:
            self.assertEqual(titles.get(seed.id), seed.title)
            self.assertEqual(len(seed.id), 30)
        self.assertIn(fx_oc2.ISO_TITLE_A, titles.values())
        self.assertIn('<tool_call>{"name":"bash"}</tool_call> Fix the tests', titles.values())
        other = self.ready["projects"]["other"]
        in_other = self.front("GET", "/api/session?directory=" + other).json()["data"]
        self.assertEqual([s["id"] for s in in_other], [self.ready["sessions"]["other-plan"]])

    def test_FX_OC2_long_history_session_imported_with_400_messages(self) -> None:
        session = self.ready["sessions"]["long-history"]
        page = self.front("GET", f"/api/session/{session}/message?limit=200").json()
        self.assertEqual(page["total"], 400)
        self.assertEqual(len(page["data"]), 200)

    def test_FX_OC2_perf_history_body_has_1000_messages_and_5000_parts(self) -> None:
        body = fx_oc2.history_import(fx_oc2.PERF_HISTORY, Path("/x"), "p", "perf-history")
        plain = [m for m in body["messages"] if len(m.get("content", [None])) == 1]
        self.assertEqual(len(plain), 1001)  # 1,000 plain + the user prompt before the long reply
        self.assertEqual(len(body["messages"][-1]["content"]), 5000)
        ids = [m["id"] for m in body["messages"]]
        self.assertEqual(ids, sorted(ids))

    # -- proxy --------------------------------------------------------------------

    def test_FX_OC2_front_streams_sse_chunk_by_chunk(self) -> None:
        start = time.monotonic()
        lines, ended = sse_lines(self.port("front"), "/api/event", self.auth, want=4, timeout=5)
        self.assertFalse(ended)
        self.assertEqual(json.loads(lines[0])["type"], "server.connected")
        self.assertEqual([json.loads(x)["type"] for x in lines[1:]], ["fx.heartbeat"] * 3)
        self.assertLess(time.monotonic() - start, 3)  # heartbeats arrive as sent, not buffered

    def test_FX_OC2_drop_streams_closes_open_event_streams(self) -> None:
        result: dict[str, Any] = {}

        def reader() -> None:
            result["lines"], result["ended"] = sse_lines(self.port("front"), "/api/event", self.auth, 1000, 10)

        thread = threading.Thread(target=reader)
        thread.start()
        deadline = time.monotonic() + 5
        while self.ctl("streams").json()["open"] == 0 and time.monotonic() < deadline:
            time.sleep(0.05)
        self.assertEqual(self.ctl("streams").json()["paths"], ["/api/event"])
        self.assertEqual(self.ctl("drop-streams").json()["dropped"], 1)
        thread.join(5)
        self.assertFalse(thread.is_alive())
        self.assertTrue(result["ended"])
        self.assertEqual(self.ctl("streams").json()["open"], 0)
        rows = self.wait_log(lambda r: r["path"] == "/api/event" and r.get("dropped"))
        self.assertTrue(any(r["path"] == "/api/event" and r.get("dropped") for r in rows))

    def test_FX_OC2_cut_fault_ends_event_stream_after_ms(self) -> None:
        self.ctl("fault", {"path": "/api/event", "action": "cut", "ms": 700})
        start = time.monotonic()
        lines, ended = sse_lines(self.port("front"), "/api/event", self.auth, 1000, 8)
        elapsed = time.monotonic() - start
        self.assertTrue(ended)
        self.assertGreaterEqual(len(lines), 2)
        self.assertTrue(0.5 < elapsed < 3, elapsed)

    def test_FX_OC2_refuse_fault_answers_status_for_one_endpoint_only(self) -> None:
        self.ctl("fault", {"path": "/api/session", "method": "GET", "action": "refuse", "status": 503})
        self.assertEqual(self.front("GET", "/api/session").status, 503)
        self.assertEqual(self.front("GET", "/api/health").status, 200)

    def test_FX_OC2_delay_fault_slows_model_catalog(self) -> None:
        self.ctl("fault", {"path": "/api/model", "action": "delay", "ms": 600})
        start = time.monotonic()
        self.assertEqual(self.front("GET", "/api/model").status, 200)
        self.assertGreaterEqual(time.monotonic() - start, 0.55)

    def test_FX_OC2_hang_fault_holds_request_until_release(self) -> None:
        self.ctl("fault", {"path": "/api/health", "action": "hang", "ms": 20000})
        outcome: dict[str, Any] = {}

        def call() -> None:
            try:
                outcome["status"] = self.front("GET", "/api/health", timeout=15).status
            except (OSError, http.client.HTTPException) as err:
                outcome["error"] = type(err).__name__

        thread = threading.Thread(target=call)
        thread.start()
        time.sleep(0.6)
        self.assertTrue(thread.is_alive())
        self.ctl("release")
        thread.join(5)
        self.assertFalse(thread.is_alive())
        self.assertIn("error", outcome)  # closed without an answer

    def test_FX_OC2_lose_response_reaches_server_but_answer_is_lost(self) -> None:
        session = self.ready["sessions"]["shared-notes"]
        before = self.front("GET", f"/api/session/{session}/message").json()["total"]
        self.ctl("lose-response", {"path": f"/api/session/{session}/prompt", "method": "POST", "count": 1})
        with self.assertRaises((OSError, http.client.HTTPException)):
            self.front("POST", f"/api/session/{session}/prompt", {"text": "e2e:echo lost"})
        after = self.front("GET", f"/api/session/{session}/message").json()["total"]
        self.assertEqual(after, before + 1)

    def test_FX_OC2_upgrade_requests_are_tunnelled(self) -> None:
        with socket.create_connection(("127.0.0.1", self.port("front")), timeout=5) as sock:
            auth = self.auth["Authorization"]
            sock.sendall(("GET /api/fx/upgrade HTTP/1.1\r\nHost: x\r\nConnection: Upgrade\r\n"
                          f"Upgrade: websocket\r\nAuthorization: {auth}\r\n\r\n").encode())
            head = b""
            while b"\r\n\r\n" not in head:
                head += sock.recv(1024)
            self.assertTrue(head.startswith(b"HTTP/1.1 101"))
            sock.sendall(b"ping-bytes")
            got = b""
            while len(got) < 10:
                got += sock.recv(1024)
            self.assertEqual(got, b"ping-bytes")
        rows = self.wait_log(lambda r: r["path"] == "/api/fx/upgrade")
        row = [r for r in rows if r["path"] == "/api/fx/upgrade"][-1]
        self.assertEqual((row["status"], row["upgrade"]), (101, True))

    def test_FX_OC2_quota_paths_go_to_fx_usage_with_auth_unchanged(self) -> None:
        resp = self.front("GET", "/ocmn/quota/codex?x=1")
        self.assertEqual(resp.status, 200)
        self.assertEqual(resp.json(), {"usage": "stub"})
        self.assertEqual(self.usage_seen[-1]["path"], "/ocmn/quota/codex?x=1")
        self.assertEqual(self.usage_seen[-1]["auth"], self.auth["Authorization"])
        rows = self.wait_log(lambda r: r["path"] == "/ocmn/quota/codex")
        self.assertEqual([r["upstream"] for r in rows if r["path"] == "/ocmn/quota/codex"][-1], "fx-usage")

    def test_FX_OC2_prompt_control_reaches_server(self) -> None:
        session = self.ready["sessions"]["iso-title"]
        result = self.ctl("prompt", {"session": session, "text": f"e2e:echo {PROMPT_MARKER}"}).json()
        self.assertEqual(result["status"], 200)
        rows = self.front("GET", f"/api/session/{session}/message").json()["data"]
        self.assertEqual(rows[0]["text"], f"e2e:echo {PROMPT_MARKER}")
        self.assertEqual(self.ctl("prompt", {"session": "nope"}).status, 400)

    def test_FX_OC2_model_port_serves_stub_and_logs_command_only(self) -> None:
        body = {"stream": False, "messages": [{"role": "user", "content": f"e2e:echo {PROMPT_MARKER}"}]}
        reply = request("POST", self.port("model"), "/v1/chat/completions", body).json()
        self.assertEqual(reply["choices"][0]["message"]["content"], f"ECHO {PROMPT_MARKER}")
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            rows = self.ctl("model-log").json()["requests"]
            if any(r.get("command") == "e2e:echo" for r in rows):
                break
            time.sleep(0.05)
        self.assertIn("e2e:echo", [r.get("command") for r in rows])
        self.assertEqual(self.ctl("model-speed", {"speed": 5}).json()["speed"], 5)
        self.assertEqual(self.ctl("model-speed", {"speed": None}).json()["speed"], 100)
        self.assert_no_secret_in_run_dir(PROMPT_MARKER)

    def test_FX_OC2_front_logs_auth_scheme_never_password(self) -> None:
        self.front("GET", "/api/health")
        self.front("GET", "/api/health", auth=False)
        rows = self.wait_log(lambda r: r["path"] == "/api/health" and r["auth"] == "none")
        health = [r for r in rows if r["path"] == "/api/health"]
        self.assertIn(("basic", 200), {(r["auth"], r["status"]) for r in health})
        self.assertIn(("none", 401), {(r["auth"], r["status"]) for r in health})
        password = self.secrets["FX_OC2_PASSWORD"]
        token = base64.b64encode(f"opencode:{password}".encode()).decode()
        self.assert_no_secret_in_run_dir(password, token, self.secrets["FX_OC2_B_PASSWORD"])

    def test_FX_OC2_child_is_recorded_by_exact_pid(self) -> None:
        children = json.loads((self.run_dir / "FX-OC2" / "children.json").read_text())
        self.assertEqual([c["pid"] for c in children], [self.ready["upstream_pid"]])
        self.assertTrue(pid_alive(children[0]["pid"]))


class FxOc2BTest(Oc2Base):
    FIXTURE = "FX-OC2-B"
    PASSWORD_KEY = "FX_OC2_B_PASSWORD"
    EXTRA_ENV = {"FX_OC2_BIN": str(FAKE), "FX_STUB_SPEED": "100", "FX_USAGE_PORT": "1"}

    def test_FX_OC2_B_shares_session_ids_with_FX_OC2_under_its_own_titles(self) -> None:
        rows = {s["id"]: s.get("title") for s in self.front("GET", "/api/session").json()["data"]}
        for key, session in fx_oc2.SHARED_IDS.items():
            self.assertIn(session, rows)
            self.assertEqual(self.ready["sessions"][key], session)
            oc2_title = next(s.title for s in fx_oc2.OC2_SESSIONS if s.id == session)
            self.assertNotEqual(rows[session], oc2_title)
        own = {s.id for s in fx_oc2_b.B_SESSIONS} - {s.id for s in fx_oc2.OC2_SESSIONS}
        self.assertTrue(own and own <= set(rows))

    def test_FX_OC2_B_has_iso_placeholder_and_leaked_markup_titles(self) -> None:
        titles = {s.get("title") for s in self.front("GET", "/api/session").json()["data"]}
        self.assertIn(fx_oc2.ISO_TITLE_B, titles)
        self.assertIn('<tool_call>{"name":"bash"}</tool_call> Fix the tests', titles)

    def test_FX_OC2_B_uses_own_password_ports_and_data_root(self) -> None:
        self.assertEqual(self.front("GET", "/api/health").status, 200)
        wrong = basic("opencode", self.secrets["FX_OC2_PASSWORD"])
        self.assertEqual(self.http("GET", "/api/health", port="front", headers=wrong).status, 401)
        self.assertIn("/FX-OC2-B/", self.ready["data_root"])
        self.assertEqual(set(self.ready["projects"]), {"app", "other", "web"})
        self.assertEqual(self.ready["password_env"], "FX_OC2_B_PASSWORD")

    def test_FX_OC2_B_quota_without_fx_usage_is_404(self) -> None:
        self.assertEqual(self.front("GET", "/ocmn/quota/codex").status, 404)

    def test_FX_OC2_B_logs_never_hold_passwords(self) -> None:
        self.front("GET", "/api/session")
        self.assert_no_secret_in_run_dir(self.secrets["FX_OC2_B_PASSWORD"], self.secrets["FX_OC2_PASSWORD"])


class FxOc2LifecycleTest(unittest.TestCase):
    def _start(self, env: dict[str, str]) -> tuple[Path, subprocess.CompletedProcess[str]]:
        run_dir = Path(tempfile.mkdtemp(prefix="fx-oc2-life-"))
        self.addCleanup(shutil.rmtree, run_dir, True)
        args = ["start", "--run-dir", str(run_dir), "FX-OC2"]
        for name in load_ports("FX-OC2"):
            args += ["--port", f"FX-OC2:{name}={free_port()}"]
        return run_dir, fxctl(*args, env={**os.environ, **env})

    def test_FX_OC2_missing_binary_fails_fast_with_clear_message(self) -> None:
        start = time.monotonic()
        run_dir, result = self._start({"FX_OC2_BIN": "/nonexistent/opencode2"})
        self.assertEqual(result.returncode, 1)
        self.assertLess(time.monotonic() - start, 20)
        self.assertIn("exited with 2", result.stderr)
        self.assertIn("OpenCode 2 server binary not found", result.stderr)
        self.assertIn("FX_OC2_BIN", result.stderr)
        self.assertFalse((run_dir / "pids" / "FX-OC2.json").exists())

    def test_FX_OC2_stop_by_pid_kills_fake_opencode2_child(self) -> None:
        run_dir, result = self._start({"FX_OC2_BIN": str(FAKE)})
        self.assertEqual(result.returncode, 0, result.stderr)
        server_pid = json.loads((run_dir / "pids" / "FX-OC2.json").read_text())["pid"]
        child_pid = json.loads((run_dir / "FX-OC2" / "children.json").read_text())[0]["pid"]
        self.assertTrue(pid_alive(child_pid))
        stop = fxctl("stop", "--run-dir", str(run_dir), "FX-OC2")
        self.assertEqual(stop.returncode, 0, stop.stdout + stop.stderr)
        deadline = time.monotonic() + 5
        while (pid_alive(server_pid) or pid_alive(child_pid)) and time.monotonic() < deadline:
            time.sleep(0.05)
        self.assertFalse(pid_alive(server_pid))
        self.assertFalse(pid_alive(child_pid))


if __name__ == "__main__":
    unittest.main()
