#!/usr/bin/env python3
"""Tests for the control-plane front (TEAM-201).

Run with ``python3 -m unittest discover -s tool/host/cp_front/tests -p 'test_*.py'``.
Every test boots a stub supervisor and a front on ephemeral loopback ports.
Peers are chosen by the client's *source* address: fake_whois.py maps
127.0.0.2 to alice (allowlisted), 127.0.0.3 to bob (identified, not
allowlisted), 127.0.0.4 to a node tagged ``tag:phone`` and anything else to
"not on the tailnet".
"""

from __future__ import annotations

import contextlib
import http.client
import io
import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
FIXTURE_DIR = HERE.parents[2] / "qa" / "gascity_fixture"
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(FIXTURE_DIR))

import fixture_server as fx  # noqa: E402
import front  # noqa: E402

FAKE_WHOIS = f"{sys.executable} {HERE / 'fake_whois.py'} --json"
ALICE, BOB, PHONE, STRANGER = "127.0.0.2", "127.0.0.3", "127.0.0.4", "127.0.0.9"
CITY = "bright-lights"
RESPOND = f"/v0/city/{CITY}/session/bl-48k/respond"
STREAM = f"/v0/city/{CITY}/events/stream"


# -- stub supervisor ---------------------------------------------------------


class StubSupervisor(ThreadingHTTPServer):
    """Records every request; answers like the supervisor for a few routes."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self) -> None:
        super().__init__(("127.0.0.1", 0), StubHandler)
        self.requests: list[dict[str, Any]] = []
        self.lock = threading.Lock()
        self.open_streams = 0
        self.stopping = threading.Event()
        self.counter = 0
        # Merge-role data (TEAM-205), filled by the merge tests only.
        self.convoys: list[dict[str, Any]] = []
        self.beads: dict[str, dict[str, Any]] = {}
        self.rigs: dict[str, dict[str, Any]] = {}
        self.runs: list[dict[str, Any]] = []
        self.patches: list[tuple[str, dict[str, Any]]] = []

    def record(self, handler: BaseHTTPRequestHandler, body: bytes) -> dict[str, Any]:
        """Remember one request (headers lower-cased) and hand back the record."""
        entry = {"method": handler.command, "path": handler.path, "headers": {k.lower(): v for k, v in handler.headers.items()}, "body": body}
        with self.lock:
            self.requests.append(entry)
            self.counter += 1
            entry["n"] = self.counter
        return entry

    def stop(self) -> None:
        self.stopping.set()
        self.shutdown()
        self.server_close()


class StubHandler(BaseHTTPRequestHandler):
    """A very small Gas City: /health, /v0/cities, one read, respond, SSE."""

    protocol_version = "HTTP/1.1"
    server: StubSupervisor

    def log_message(self, fmt: str, *args: Any) -> None:  # noqa: D401
        return

    def _json(self, status: int, body: Any, content_type: str = "application/json", extra: dict[str, str] | None = None) -> None:
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("X-GC-Request-Id", f"gc-{self.server.counter}")
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(raw)

    def _problem(self, status: int, code: str, detail: str) -> None:
        body = {"type": f"urn:gascity:error:{code}", "title": code, "status": status, "detail": detail, "code": code}
        self._json(status, body, "application/problem+json")

    def do_GET(self) -> None:  # noqa: N802
        if self.path not in ("/health", "/v0/cities"):
            self.server.record(self, b"")  # startup probes are not interesting
        if self.path == "/health":
            self._json(200, {"status": "ok", "version": "stub-1.4.1", "uptime_sec": 3, "cities_total": 1, "cities_running": 1})
        elif self.path == "/v0/cities":
            self._json(200, {"items": [{"name": CITY, "path": "/tmp/city", "running": True, "status": "running"}], "total": 1})
        elif self.path.startswith(f"/v0/city/{CITY}/status"):
            rig_details = [{"name": name, "path": rig["path"]} for name, rig in self.server.rigs.items()]
            self._json(200, {"name": CITY, "echo_host": self.headers.get("Host"), "rig_details": rig_details}, extra={"Set-Cookie": "leak=1"})
        elif self.path.startswith(STREAM):
            self._stream()
        elif self.path == f"/v0/city/{CITY}/convoys":
            self._json(200, {"items": self.server.convoys, "total": len(self.server.convoys)})
        elif self.path == f"/v0/city/{CITY}/runs":
            self._json(200, {"runs": self.server.runs, "partial": False})
        elif self.path == f"/v0/city/{CITY}/beads":
            items = [*self.server.beads.values()]
            self._json(200, {"items": items, "total": len(items)})
        elif self.path.startswith(f"/v0/city/{CITY}/bead/"):
            bead = self.server.beads.get(self.path.rsplit("/", 1)[1])
            if bead is None:
                self._problem(404, "not-found", "no such bead")
            else:
                self._json(200, bead)
        elif self.path.startswith(f"/v0/city/{CITY}/rig/"):
            rig = self.server.rigs.get(self.path.rsplit("/", 1)[1])
            if rig is None:
                self._problem(404, "not-found", "no such rig")
            else:
                self._json(200, {"name": self.path.rsplit("/", 1)[1], "suspended": False, "agent_count": 0, "running_count": 0, **rig})
        else:
            self._problem(404, "not-found", "no route")

    def do_PATCH(self) -> None:  # noqa: N802
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        self.server.record(self, body)
        if not self.headers.get("X-GC-Request"):
            self._problem(403, "forbidden", "X-GC-Request header is required on mutation requests")
            return
        bead_id = self.path.rsplit("/", 1)[1]
        bead = self.server.beads.get(bead_id)
        if not self.path.startswith(f"/v0/city/{CITY}/bead/") or bead is None:
            self._problem(404, "not-found", "no such bead")
            return
        doc = json.loads(body)
        self.server.patches.append((bead_id, doc))
        bead.setdefault("metadata", {}).update(doc.get("metadata") or {})
        bead["labels"] = [label for label in bead.get("labels") or [] if label not in (doc.get("remove_labels") or [])]
        self._json(200, bead)

    def do_POST(self) -> None:  # noqa: N802
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        self.server.record(self, body)
        if not self.headers.get("X-GC-Request"):
            self._problem(403, "forbidden", "X-GC-Request header is required on mutation requests")
            return
        if self.path == RESPOND:
            try:
                doc = json.loads(body)
            except json.JSONDecodeError:
                doc = {}
            if not doc.get("action"):
                self._problem(422, "invalid-request", "action is required")
            else:
                self._json(202, {"status": "accepted", "id": "bl-48k"})
        else:
            self._problem(404, "not-found", "no route")

    def _stream(self) -> None:
        after = int(self.headers.get("Last-Event-ID") or 0)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.send_header("X-GC-Request-Id", "gc-stream")
        self.end_headers()
        with self.server.lock:
            self.server.open_streams += 1
        try:
            for seq in range(after + 1, 6):
                self.wfile.write(f"id: {seq}\nevent: event\ndata: {json.dumps({'seq': seq, 'type': 'bead.updated'})}\n\n".encode())
                self.wfile.flush()
            while not self.server.stopping.is_set():
                self.server.stopping.wait(0.1)
                self.wfile.write(b'event: heartbeat\ndata: {"timestamp": "now"}\n\n')
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError):
            return
        finally:
            with self.server.lock:
                self.server.open_streams -= 1


# -- harness -----------------------------------------------------------------


class FrontCase(unittest.TestCase):
    """Boots a stub supervisor and a front per test."""

    extra_args: list[str] = []

    def setUp(self) -> None:
        self.stub = StubSupervisor()
        threading.Thread(target=self.stub.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True).start()
        self.addCleanup(self.stub.stop)
        self.state_dir = Path(tempfile.mkdtemp(prefix="front-test-"))
        self.addCleanup(shutil.rmtree, self.state_dir, True)
        self.log_lines: list[str] = []
        handler = logging.Handler()
        handler.emit = lambda record: self.log_lines.append(record.getMessage())  # type: ignore[method-assign]
        front.log.addHandler(handler)
        front.log.setLevel(logging.INFO)
        self.addCleanup(front.log.removeHandler, handler)
        self.front = self.start_front(self.extra_args)

    def front_args(self, extra: list[str]) -> list[str]:
        return [
            "--supervisor", f"http://127.0.0.1:{self.stub.server_address[1]}",
            "--bind", "127.0.0.1", "--port", "0",
            "--whois-cmd", FAKE_WHOIS, "--state-dir", str(self.state_dir),
            *extra,
        ]

    def start_front(self, extra: list[str]) -> front.FrontServer:
        server = front.FrontServer(front.parse_args(self.front_args(["--allow", "alice@example.com", *extra])))
        threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True).start()
        self.addCleanup(server.stop)
        return server

    def connection(self, peer: str) -> http.client.HTTPConnection:
        port = self.front.server_address[1]
        return http.client.HTTPConnection("127.0.0.1", port, timeout=5, source_address=(peer, 0))

    def request(self, peer: str, method: str, path: str, headers: dict[str, str] | None = None, body: Any = None):
        """One request from ``peer``; returns (status, headers, decoded body)."""
        conn = self.connection(peer)
        payload = json.dumps(body).encode() if body is not None else None
        conn.request(method, path, body=payload, headers={"Connection": "close", **(headers or {})})
        response = conn.getresponse()
        raw = response.read()
        conn.close()
        decoded = json.loads(raw) if "json" in response.getheader("Content-Type", "") else raw
        return response.status, {k.lower(): v for k, v in response.getheaders()}, decoded

    def upstream_posts(self) -> list[dict[str, Any]]:
        return [r for r in self.stub.requests if r["method"] == "POST"]

    def wait_for(self, predicate, timeout: float = 5.0) -> None:
        deadline = time.monotonic() + timeout
        while not predicate():
            self.assertLess(time.monotonic(), deadline, "condition not met in time")
            time.sleep(0.05)


# -- startup checks ----------------------------------------------------------


class StartupTests(unittest.TestCase):
    """The documented refusals happen before the socket is bound."""

    def setUp(self) -> None:
        self.stub = StubSupervisor()
        threading.Thread(target=self.stub.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True).start()
        self.addCleanup(self.stub.stop)
        self.state_dir = Path(tempfile.mkdtemp(prefix="front-test-"))
        self.addCleanup(shutil.rmtree, self.state_dir, True)

    def build(self, *extra: str, supervisor: str | None = None) -> front.FrontServer:
        url = supervisor or f"http://127.0.0.1:{self.stub.server_address[1]}"
        args = ["--supervisor", url, "--port", "0", "--whois-cmd", FAKE_WHOIS, "--state-dir", str(self.state_dir), *extra]
        return front.FrontServer(front.parse_args(args))

    def test_refuses_non_tailnet_bind(self) -> None:
        for address in ("192.168.1.20", "0.0.0.0", "10.0.0.5", "example.ts.net"):
            with self.assertRaises(front.ConfigError, msg=address):
                self.build("--bind", address, "--allow", "alice@example.com")

    def test_accepts_tailnet_and_loopback_binds(self) -> None:
        self.assertTrue(front.is_local_bind("100.101.102.103"))
        self.assertTrue(front.is_local_bind("fd7a:115c:a1e0::1"))
        self.assertTrue(front.is_local_bind("127.0.0.1"))
        self.assertFalse(front.is_local_bind("100.128.0.1"))

    def test_refuses_empty_allowlist(self) -> None:
        with self.assertRaises(front.ConfigError):
            self.build("--bind", "127.0.0.1")
        server = self.build("--bind", "127.0.0.1", "--allow-tags", "tag:phone")
        server.server_close()

    def test_insecure_flag_replaces_the_allowlist_with_a_warning(self) -> None:
        with self.assertLogs(front.log, level="WARNING") as captured:
            server = self.build("--bind", "127.0.0.1", "--insecure-allow-any-peer")
        server.server_close()
        self.assertTrue(any("insecure-allow-any-peer" in line for line in captured.output))

    def test_refuses_when_supervisor_is_down(self) -> None:
        with self.assertRaises(front.ConfigError):
            self.build("--bind", "127.0.0.1", "--allow", "alice@example.com", supervisor="http://127.0.0.1:1")

    def test_main_exits_2_on_refusal_and_print_config_exits_0(self) -> None:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            self.assertEqual(front.main(["--bind", "192.168.1.20", "--allow", "x@example.com", "--supervisor", "http://127.0.0.1:1"]), 2)
            self.assertEqual(front.main(["--bind", "192.168.1.20", "--print-config", "--allow", "x@example.com"]), 0)
        self.assertIn("refusing to start", err.getvalue())
        config = json.loads(out.getvalue())
        self.assertEqual(config["allow"], ["x@example.com"])
        self.assertFalse(config["bind_is_local"])


# -- behaviour ---------------------------------------------------------------


class WellKnownTests(FrontCase):
    def test_shape_for_allowlisted_and_identified_peers(self) -> None:
        status, headers, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual(body, {
            "provider": "gascity",
            "supervisorUrl": f"http://127.0.0.1:{self.front.server_address[1]}",
            "city": CITY,
            "front": True,
            "version": "stub-1.4.1",
            "capabilities": {"read": True, "control": True, "merge": False},
            "identity": {"login": "alice@example.com", "allowed": True},
        })
        status, _, body = self.request(BOB, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual(body["identity"], {"login": "bob@example.com", "allowed": False})

    def test_strangers_do_not_see_the_document(self) -> None:
        status, headers, body = self.request(STRANGER, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 403)
        self.assertEqual(headers["content-type"], "application/problem+json")
        self.assertEqual(body["code"], "peer-not-on-tailnet")

    def test_city_flag_overrides_discovery(self) -> None:
        server = self.start_front(["--city", "other"])
        self.front = server
        _, _, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(body["city"], "other")


class ReadTests(FrontCase):
    def test_get_allowed_for_allowlisted_peer_and_host_rewritten(self) -> None:
        status, headers, body = self.request(ALICE, "GET", f"/v0/city/{CITY}/status", {"Host": "pc.tail.ts.net:8373", "Authorization": "Bearer secret", "Cookie": "a=b"})
        self.assertEqual(status, 200)
        self.assertEqual(body["echo_host"], f"127.0.0.1:{self.stub.server_address[1]}")
        self.assertEqual(headers["x-gc-request-id"], "gc-1")
        self.assertNotIn("set-cookie", headers)
        seen = self.stub.requests[-1]["headers"]
        self.assertNotIn("authorization", seen)
        self.assertNotIn("cookie", seen)
        self.assertEqual(seen["x-gc-request"], "opencode-mobile-front")

    def test_get_refused_for_identified_but_not_allowlisted_peer(self) -> None:
        status, _, body = self.request(BOB, "GET", f"/v0/city/{CITY}/status")
        self.assertEqual(status, 403)
        self.assertEqual(body["code"], "identity-not-allowed")
        self.assertEqual(len(self.stub.requests), 0)

    def test_reads_any_peer_opens_reads_but_not_writes(self) -> None:
        self.front = self.start_front(["--reads-any-peer"])
        status, _, _ = self.request(BOB, "GET", f"/v0/city/{CITY}/status")
        self.assertEqual(status, 200)
        status, _, _ = self.request(STRANGER, "GET", f"/v0/city/{CITY}/status")
        self.assertEqual(status, 403)
        status, _, _ = self.request(BOB, "POST", RESPOND, body={"action": "allow"})
        self.assertEqual(status, 403)

    def test_tag_allowlist(self) -> None:
        self.front = self.start_front(["--allow-tags", "tag:phone"])
        status, _, body = self.request(PHONE, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual(body["identity"], {"login": "tagged-devices", "allowed": True})

    def test_whois_answers_are_cached(self) -> None:
        counter = self.state_dir / "whois-calls"
        self.front.whois.argv = [sys.executable, str(HERE / "fake_whois.py"), "--json"]
        import os
        os.environ["FAKE_WHOIS_COUNT"] = str(counter)
        self.addCleanup(os.environ.pop, "FAKE_WHOIS_COUNT", None)
        for _ in range(3):
            self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(counter.read_text().count(ALICE), 1)

    def test_whois_timeout_reuses_the_cached_identity(self) -> None:
        # TEAM-117: the phone's front answered 403 when one `tailscale
        # whois` hung past its timeout. A peer resolved within the hour
        # keeps its identity; an IP never resolved still gets 403.
        counter = self.state_dir / "whois-calls"
        self.front.whois.argv = [sys.executable, str(HERE / "fake_whois.py"), "--json"]
        self.front.whois.ttl = 0  # every request runs whois again
        os.environ["FAKE_WHOIS_COUNT"] = str(counter)
        self.addCleanup(os.environ.pop, "FAKE_WHOIS_COUNT", None)
        status, _, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual(body["identity"]["login"], "alice@example.com")

        # From here whois hangs; keep the wait short for the test.
        os.environ["FAKE_WHOIS_SLEEP_AFTER"] = "1"
        self.addCleanup(os.environ.pop, "FAKE_WHOIS_SLEEP_AFTER", None)
        original = front.WHOIS_TIMEOUT
        front.WHOIS_TIMEOUT = 0.3
        self.addCleanup(setattr, front, "WHOIS_TIMEOUT", original)

        status, _, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual(body["identity"]["login"], "alice@example.com")
        self.assertEqual(counter.read_text().count(ALICE), 2)
        self.assertTrue(any("using cached identity" in line for line in self.log_lines), self.log_lines)

        status, _, body = self.request(STRANGER, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 403)
        self.assertEqual(body["code"], "peer-not-on-tailnet")

        # An identity older than the stale window is not reused.
        self.front.whois.stale_max = 0
        status, _, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(status, 403)
        self.assertEqual(body["code"], "peer-not-on-tailnet")


class MutationTests(FrontCase):
    def test_post_refused_for_non_allowlisted_is_problem_json(self) -> None:
        status, headers, body = self.request(BOB, "POST", RESPOND, {"Idempotency-Key": "k1"}, {"action": "allow"})
        self.assertEqual(status, 403)
        self.assertEqual(headers["content-type"], "application/problem+json")
        self.assertEqual(body["code"], "identity-not-allowed")
        self.assertEqual(self.upstream_posts(), [])

    def test_post_forwarded_with_csrf_header_and_receipt(self) -> None:
        payload = {"action": "allow", "request_id": "req-1"}
        status, headers, receipt = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-a", "Authorization": "Bearer nope"}, payload)
        self.assertEqual(status, 202)
        upstream = self.upstream_posts()[0]
        self.assertEqual(upstream["path"], RESPOND)
        self.assertEqual(json.loads(upstream["body"]), payload)
        self.assertEqual(upstream["headers"]["x-gc-request"], "opencode-mobile-front")
        self.assertEqual(upstream["headers"]["host"], f"127.0.0.1:{self.stub.server_address[1]}")
        self.assertNotIn("authorization", upstream["headers"])
        self.assertNotIn("idempotency-key", upstream["headers"])
        self.assertEqual(receipt, {
            "request_id": "gc-1",
            "status": "accepted",
            "upstream_status": 202,
            "body": {"status": "accepted", "id": "bl-48k"},
            "idempotency_key": "key-a",
        })
        self.assertEqual(headers["x-gc-request-id"], "gc-1")

    def test_rejected_upstream_is_a_rejected_receipt(self) -> None:
        status, _, receipt = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-bad"}, {"text": "no action"})
        self.assertEqual(status, 422)
        self.assertEqual(receipt["status"], "rejected")
        self.assertEqual(receipt["upstream_status"], 422)
        self.assertEqual(receipt["body"]["code"], "invalid-request")

    def test_idempotent_replay_returns_the_same_receipt_without_a_second_upstream_call(self) -> None:
        first = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-r"}, {"action": "allow"})
        second = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-r"}, {"action": "allow"})
        self.assertEqual(first[0], second[0])
        self.assertEqual(first[2], second[2])
        self.assertEqual(second[1]["idempotent-replayed"], "true")
        self.assertEqual(len(self.upstream_posts()), 1)
        receipts = json.loads((self.state_dir / "receipts.json").read_text())
        self.assertEqual(receipts["key-r"]["identity"], "alice@example.com")

    def test_replay_survives_a_front_restart(self) -> None:
        first = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-p"}, {"action": "allow"})
        self.front = self.start_front([])
        second = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-p"}, {"action": "allow"})
        self.assertEqual(first[2], second[2])
        self.assertEqual(len(self.upstream_posts()), 1)

    def test_same_key_different_identity_is_409(self) -> None:
        self.front = self.start_front(["--allow", "bob@example.com"])
        self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-s"}, {"action": "allow"})
        status, _, body = self.request(BOB, "POST", RESPOND, {"Idempotency-Key": "key-s"}, {"action": "allow"})
        self.assertEqual(status, 409)
        self.assertEqual(body["code"], "idempotency-mismatch")
        self.assertEqual(len(self.upstream_posts()), 1)

    def test_missing_key_forwards_without_storing(self) -> None:
        status, _, receipt = self.request(ALICE, "POST", RESPOND, body={"action": "allow"})
        self.assertEqual(status, 202)
        self.assertIsNone(receipt["idempotency_key"])
        self.request(ALICE, "POST", RESPOND, body={"action": "allow"})
        self.assertEqual(len(self.upstream_posts()), 2)

    def test_upstream_down_is_502_and_not_stored(self) -> None:
        self.front.upstream.port = 1
        status, _, body = self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "key-d"}, {"action": "allow"})
        self.assertEqual(status, 502)
        self.assertEqual(body["code"], "upstream-unavailable")
        self.assertFalse((self.state_dir / "receipts.json").exists())


class StreamTests(FrontCase):
    def read_frames(self, response, count: int) -> list[dict[str, str]]:
        frames: list[dict[str, str]] = []
        current: dict[str, str] = {}
        while len(frames) < count:
            line = response.readline().decode().rstrip("\n")
            if line == "":
                if current:
                    frames.append(current)
                    current = {}
                continue
            key, _, value = line.partition(": ")
            current[key] = value
        return frames

    def test_sse_pass_through_delivers_events_and_forwards_last_event_id(self) -> None:
        conn = self.connection(ALICE)
        conn.request("GET", STREAM, headers={"Last-Event-ID": "3", "Accept": "text/event-stream"})
        response = conn.getresponse()
        self.assertEqual(response.status, 200)
        self.assertEqual(response.getheader("Content-Type"), "text/event-stream")
        frames = self.read_frames(response, 3)
        self.assertEqual([f.get("id") for f in frames[:2]], ["4", "5"])
        self.assertEqual(frames[2]["event"], "heartbeat")
        self.assertEqual(self.stub.requests[-1]["headers"]["last-event-id"], "3")
        response.close()
        conn.close()
        self.wait_for(lambda: self.stub.open_streams == 0)

    def test_client_disconnect_closes_upstream(self) -> None:
        conn = self.connection(ALICE)
        conn.request("GET", STREAM)
        response = conn.getresponse()
        self.read_frames(response, 1)
        self.assertEqual(self.stub.open_streams, 1)
        response.close()  # http.client keeps the fd open until the response is closed too
        conn.close()
        self.wait_for(lambda: self.stub.open_streams == 0)

    def test_stream_refused_for_stranger(self) -> None:
        status, _, body = self.request(STRANGER, "GET", STREAM)
        self.assertEqual(status, 403)
        self.assertEqual(body["code"], "peer-not-on-tailnet")


class LoggingTests(FrontCase):
    def test_log_lines_carry_ids_but_never_keys_bodies_or_whois(self) -> None:
        self.request(ALICE, "POST", RESPOND, {"Idempotency-Key": "secret-key-123"}, {"action": "allow", "text": "SECRET BODY"})
        self.request(BOB, "GET", f"/v0/city/{CITY}/status?after_seq=1")
        joined = "\n".join(self.log_lines)
        self.assertIn(f"POST {RESPOND} login=alice@example.com status=202 request_id=gc-1", joined)
        self.assertIn(f"GET /v0/city/{CITY}/status login=bob@example.com status=403", joined)
        for forbidden in ("secret-key-123", "SECRET BODY", "UserProfile", "LoginName", "after_seq"):
            self.assertNotIn(forbidden, joined)


class FixtureIntegrationTests(unittest.TestCase):
    """The front in front of the recorded Gas City fixture (tool/qa/gascity_fixture)."""

    def setUp(self) -> None:
        options = fx.parse_args(["--port", "0", "--pace", "0", "--heartbeat", "0.2"])
        self.fixture = fx.FixtureServer(("127.0.0.1", 0), options)
        threading.Thread(target=self.fixture.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True).start()
        self.addCleanup(self.fixture.stop)
        state_dir = Path(tempfile.mkdtemp(prefix="front-test-"))
        self.addCleanup(shutil.rmtree, state_dir, True)
        args = ["--supervisor", f"http://127.0.0.1:{self.fixture.server_address[1]}", "--bind", "127.0.0.1", "--port", "0",
                "--allow", "alice@example.com", "--whois-cmd", FAKE_WHOIS, "--state-dir", str(state_dir)]
        self.front = front.FrontServer(front.parse_args(args))
        threading.Thread(target=self.front.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True).start()
        self.addCleanup(self.front.stop)

    def request(self, method: str, path: str, headers: dict[str, str] | None = None, body: Any = None):
        conn = http.client.HTTPConnection("127.0.0.1", self.front.server_address[1], timeout=5, source_address=(ALICE, 0))
        conn.request(method, path, body=json.dumps(body).encode() if body is not None else None, headers={"Connection": "close", **(headers or {})})
        response = conn.getresponse()
        raw = response.read()
        conn.close()
        return response.status, {k.lower(): v for k, v in response.getheaders()}, json.loads(raw)

    def test_well_known_names_the_recorded_city_and_version(self) -> None:
        status, _, body = self.request("GET", front.WELL_KNOWN)
        self.assertEqual(status, 200)
        self.assertEqual((body["city"], body["version"], body["front"]), (fx.CITY, "1.4.1", True))

    def test_sling_receipt_carries_the_fixture_request_id_and_replays(self) -> None:
        path = f"/v0/city/{fx.CITY}/sling"
        status, headers, receipt = self.request("POST", path, {"Idempotency-Key": "fx-1"}, {"target": "polecat", "bead": fx.BEAD})
        self.assertEqual(status, 200)
        self.assertEqual(receipt["status"], "accepted")
        self.assertEqual(receipt["body"]["status"], "slung")
        self.assertEqual(receipt["request_id"], headers["x-gc-request-id"])
        self.assertEqual(len(receipt["request_id"]), 32)
        self.assertEqual(receipt["body"]["target"], "polecat")
        again = self.request("POST", path, {"Idempotency-Key": "fx-1"}, {"target": "polecat", "bead": fx.BEAD})
        self.assertEqual(again[2], receipt)

    def stream_ids(self, cursor: str | None, count: int) -> list[int]:
        """Open the replayed event stream through the front; first ``count`` ids."""
        conn = http.client.HTTPConnection("127.0.0.1", self.front.server_address[1], timeout=5, source_address=(ALICE, 0))
        headers = {"Last-Event-ID": cursor} if cursor else {}
        conn.request("GET", f"/v0/city/{fx.CITY}/events/stream", headers=headers)
        response = conn.getresponse()
        self.assertEqual(response.getheader("Content-Type"), "text/event-stream")
        ids: list[int] = []
        while len(ids) < count:
            line = response.readline().decode()
            if line.startswith("id: "):
                ids.append(int(line[4:]))
        response.close()
        conn.close()
        return ids

    def test_events_stream_passes_through_with_cursor(self) -> None:
        # The fixture's head starts before its first recorded event and only
        # advances once a stream is open; a cursor beyond the head replays
        # from the head, so the resume is checked after the head moved.
        self.assertEqual(self.stream_ids(None, 10)[:3], [1026, 1027, 1028])
        self.assertEqual(self.stream_ids("1030", 2), [1031, 1032])


# -- merge roles (TEAM-205) --------------------------------------------------


GIT_ENV = {
    "GIT_AUTHOR_NAME": "test", "GIT_AUTHOR_EMAIL": "test@example.com",
    "GIT_COMMITTER_NAME": "test", "GIT_COMMITTER_EMAIL": "test@example.com",
    "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1",
}
RIG = "ocproof"
READINESS = f"/v0/city/{CITY}/front/merge-readiness/oc-xru"
MERGE = f"/v0/city/{CITY}/front/merge/oc-xru"
APPROVE = f"/v0/city/{CITY}/front/mr/oc-xru/approve"


def git(*args: str, cwd: Path) -> str:
    """Run git in a test repo (fails loudly)."""
    done = subprocess.run(["git", *args], cwd=cwd, env={**os.environ, **GIT_ENV}, capture_output=True, text=True, check=False)
    if done.returncode != 0:
        raise AssertionError(f"git {' '.join(args)} failed: {done.stderr}")
    return done.stdout.strip()


class MergeCase(FrontCase):
    """A bare origin, a rig clone with ``main`` and ``polecat/oc-1`` pushed, and a stub convoy tracking oc-1."""

    def setUp(self) -> None:
        super().setUp()
        root = Path(tempfile.mkdtemp(prefix="front-merge-"))
        self.addCleanup(shutil.rmtree, root, True)
        self.origin = root / "origin.git"
        self.rig = root / "rig"
        git("init", "--quiet", "--bare", "--initial-branch=main", str(self.origin), cwd=root)
        git("clone", "--quiet", str(self.origin), str(self.rig), cwd=root)
        (self.rig / "calc.py").write_text("def add(a, b):\n    return a + b\n")
        git("add", "calc.py", cwd=self.rig)
        git("commit", "--quiet", "-m", "initial", cwd=self.rig)
        git("push", "--quiet", "-u", "origin", "main", cwd=self.rig)
        self.base = git("rev-parse", "HEAD", cwd=self.rig)
        self.tip = self.branch("polecat/oc-1", "calc.py", "def add(a, b):\n    return a + b\n\n\ndef subtract(a, b):\n    return a - b\n", "Add subtract")
        self.stub.rigs[RIG] = {"path": str(self.rig), "default_branch": "main"}
        self.stub.convoys = [{
            "id": "oc-xru", "title": "sling-oc-1", "status": "open", "issue_type": "convoy",
            "metadata": {"rig": RIG},
            "dependencies": [{"issue_id": "oc-xru", "depends_on_id": "oc-1", "type": "tracks"}],
        }]
        self.stub.beads = {
            "oc-xru": self.stub.convoys[0],
            "oc-1": {"id": "oc-1", "title": "Add subtract", "status": "closed", "issue_type": "task",
                     "metadata": {"rig": RIG, "branch": "polecat/oc-1", "target": "main"}, "labels": []},
        }

    def branch(self, name: str, path: str, content: str, message: str, start: str = "main") -> str:
        """Commit ``content`` on a new branch of the origin (from a throwaway clone) and return its sha."""
        work = Path(tempfile.mkdtemp(prefix="front-branch-"))
        self.addCleanup(shutil.rmtree, work, True)
        git("clone", "--quiet", "--branch", start, str(self.origin), str(work / "w"), cwd=work)
        git("checkout", "--quiet", "-b", name, cwd=work / "w")
        (work / "w" / path).write_text(content)
        git("add", path, cwd=work / "w")
        git("commit", "--quiet", "-m", message, cwd=work / "w")
        git("push", "--quiet", "origin", name, cwd=work / "w")
        return git("rev-parse", "HEAD", cwd=work / "w")

    def origin_head(self, branch: str = "main") -> str:
        return git("rev-parse", f"refs/heads/{branch}", cwd=self.origin)

    def rig_config(self, doc: dict[str, Any]) -> None:
        rigs = self.state_dir / "rigs"
        rigs.mkdir(parents=True, exist_ok=True)
        (rigs / f"{RIG}.json").write_text(json.dumps(doc))

    def approve_bead(self, bead_id: str = "oc-xru", login: str = "alice@example.com") -> None:
        self.stub.beads[bead_id].setdefault("metadata", {})["review.approved_by"] = login

    def readiness(self, peer: str = ALICE):
        return self.request(peer, "GET", READINESS)

    def lines(self, doc: dict[str, Any]) -> dict[str, dict[str, Any]]:
        return {line["key"]: line for line in doc["lines"]}

    def merge(self, key: str = "m-1", peer: str = ALICE):
        return self.request(peer, "POST", MERGE, {"Idempotency-Key": key}, {})

    def assert_no_forbidden_git(self) -> None:
        for argv in self.front.git_calls:
            self.assertNotIn("worktree", argv, argv)
            self.assertNotIn("--force", argv, argv)
            self.assertNotIn("reset", argv, argv)
            self.assertNotIn("-D", argv, argv)
            self.assertNotIn("--delete", argv, argv)
            if "push" in argv:
                self.assertNotIn("-f", argv, argv)
                self.assertFalse(any(a.startswith("+") or a.startswith(":") for a in argv), argv)


class MergeReadinessTests(MergeCase):
    def test_ready_run_lists_every_line_and_the_diff(self) -> None:
        status, _, doc = self.readiness()
        self.assertEqual(status, 200)
        self.assertTrue(doc["ready"], doc)
        self.assertEqual([line["key"] for line in doc["lines"]], ["work", "tests", "build", "review", "conflicts", "acceptance"])
        self.assertTrue(all(line["ok"] for line in doc["lines"]))
        self.assertEqual((doc["files"], doc["additions"], doc["deletions"]), (1, 4, 0))
        self.assertEqual(doc["changes"], [{"path": "calc.py", "additions": 4, "deletions": 0}])
        self.assertEqual(doc["branches"], ["polecat/oc-1"])
        self.assertEqual(doc["mergeRequest"], {"id": "oc-xru", "title": "sling-oc-1", "approvedBy": None, "approvedAt": None})
        self.assertEqual((doc["rig"], doc["targetBranch"], doc["runId"]), (RIG, "main", "oc-xru"))
        self.assertEqual(doc["mergeCommit"], self.tip)  # a fast-forward candidate is the branch tip
        self.assertEqual(doc["boundaries"], [{"key": "require_approval", "satisfied": False, "text": "Never merge without approval"}])
        self.assertEqual(self.lines(doc)["tests"]["detail"], "not configured on the host")
        self.assertEqual(self.lines(doc)["acceptance"]["detail"], "not reported by the host")
        self.assert_no_forbidden_git()
        self.assertFalse(any((self.state_dir / "tmp").iterdir()) if (self.state_dir / "tmp").exists() else False)

    def test_open_work_item_fails_the_work_line(self) -> None:
        self.stub.beads["oc-1"]["status"] = "open"
        _, _, doc = self.readiness()
        self.assertFalse(doc["ready"])
        self.assertEqual(self.lines(doc)["work"], {"key": "work", "ok": False, "detail": "1 open: oc-1"})
        # Review-ready counts as done for the work line, but the review line waits.
        self.stub.beads["oc-1"]["labels"] = ["needs-review"]
        _, _, doc = self.readiness()
        self.assertTrue(self.lines(doc)["work"]["ok"])
        self.assertEqual(self.lines(doc)["review"], {"key": "review", "ok": False, "detail": "waiting for review: oc-1"})
        self.assertFalse(doc["ready"])

    def test_conflicting_branch_names_the_branch_and_file(self) -> None:
        other = self.branch("polecat/oc-2", "calc.py", "def add(a, b):\n    return a + b\n\n\ndef multiply(a, b):\n    return a * b\n", "Add multiply")
        self.stub.convoys[0]["dependencies"].append({"issue_id": "oc-xru", "depends_on_id": "oc-2", "type": "tracks"})
        self.stub.beads["oc-2"] = {"id": "oc-2", "title": "Add multiply", "status": "closed", "issue_type": "task",
                                   "metadata": {"rig": RIG, "branch": "polecat/oc-2"}, "labels": []}
        _, _, doc = self.readiness()
        self.assertFalse(doc["ready"])
        line = self.lines(doc)["conflicts"]
        self.assertFalse(line["ok"])
        self.assertIn("conflicts merging polecat/oc-2", line["detail"])
        self.assertIn("calc.py", line["detail"])
        self.assertIsNone(doc["mergeCommit"])
        self.assertEqual(sorted(doc["branches"]), ["polecat/oc-1", "polecat/oc-2"])
        self.assertNotEqual(other, self.tip)
        self.assertEqual(self.origin_head(), self.base)

    def test_missing_branch_for_an_open_item_fails_conflicts(self) -> None:
        self.stub.beads["oc-3"] = {"id": "oc-3", "title": "No branch yet", "status": "open", "issue_type": "task",
                                   "metadata": {"rig": RIG}, "labels": ["needs-review"]}
        self.stub.convoys[0]["dependencies"].append({"issue_id": "oc-xru", "depends_on_id": "oc-3", "type": "tracks"})
        _, _, doc = self.readiness()
        self.assertEqual(self.lines(doc)["conflicts"], {"key": "conflicts", "ok": False, "detail": "no branch polecat/oc-3 on origin for oc-3"})
        # A closed item without a branch is taken as merged by the refinery.
        self.stub.beads["oc-3"]["status"] = "closed"
        _, _, doc = self.readiness()
        self.assertTrue(self.lines(doc)["conflicts"]["ok"])

    def test_acceptance_line_reads_validation_metadata(self) -> None:
        self.stub.beads["oc-1"]["metadata"]["validation"] = "passed"
        _, _, doc = self.readiness()
        self.assertEqual(self.lines(doc)["acceptance"], {"key": "acceptance", "ok": True, "detail": "1 validated"})
        self.stub.beads["oc-1"]["metadata"]["validation"] = json.dumps({"status": "failed", "summary": "2 tests failed"})
        _, _, doc = self.readiness()
        self.assertEqual(self.lines(doc)["acceptance"], {"key": "acceptance", "ok": False, "detail": "validation failed: oc-1"})
        self.assertFalse(doc["ready"])

    def test_unknown_run_and_unusable_rig_are_problems(self) -> None:
        status, headers, body = self.request(ALICE, "GET", f"/v0/city/{CITY}/front/merge-readiness/nope")
        self.assertEqual((status, body["code"]), (404, "run-not-found"))
        self.assertEqual(headers["content-type"], "application/problem+json")
        git("remote", "remove", "origin", cwd=self.rig)
        status, _, body = self.readiness()
        self.assertEqual((status, body["code"]), (422, "merge-unavailable"))
        self.assertIn("origin", body["detail"])

    def test_formula_run_resolves_work_by_run_id_metadata(self) -> None:
        self.stub.convoys = []
        self.stub.runs = [{"run_id": "run-1", "title": "Ship", "status": "completed", "scope": {"kind": "rig", "ref": RIG}}]
        self.stub.beads["oc-1"]["metadata"]["run_id"] = "run-1"
        status, _, doc = self.request(ALICE, "GET", f"/v0/city/{CITY}/front/merge-readiness/run-1")
        self.assertEqual(status, 200)
        self.assertTrue(doc["ready"], doc)
        self.assertEqual(doc["mergeRequest"]["id"], "run-1")
        self.assertEqual(doc["branches"], ["polecat/oc-1"])

    def test_merge_request_bead_is_preferred_over_the_convoy(self) -> None:
        self.stub.beads["gc-mr-14"] = {"id": "gc-mr-14", "title": "MR: subtract", "status": "open", "issue_type": "merge-request",
                                       "labels": ["needs-review"], "metadata": {"review.approved_by": "bob@example.com", "review.approved_at": "2026-09-11T10:00:00Z"},
                                       "dependencies": [{"issue_id": "gc-mr-14", "depends_on_id": "oc-xru", "type": "tracks"}]}
        _, _, doc = self.readiness()
        self.assertEqual(doc["mergeRequest"], {"id": "gc-mr-14", "title": "MR: subtract", "approvedBy": "bob@example.com", "approvedAt": "2026-09-11T10:00:00Z"})
        self.assertEqual(self.lines(doc)["review"]["detail"], "approved by bob@example.com")
        self.assertEqual(doc["boundaries"][0]["satisfied"], True)

    def test_readiness_is_gated_like_every_read(self) -> None:
        self.assertEqual(self.readiness(BOB)[0], 403)
        self.assertEqual(self.readiness(STRANGER)[0], 403)
        self.assertEqual(self.request(ALICE, "POST", READINESS, body={})[0], 405)


class MergeCheckTests(MergeCase):
    def wait_settled(self, key: str) -> dict[str, Any]:
        deadline = time.monotonic() + 10
        while True:
            _, _, doc = self.readiness()
            line = self.lines(doc)[key]
            if not line.get("pending"):
                return doc
            self.assertLess(time.monotonic(), deadline, "checks never settled")
            time.sleep(0.05)

    def test_checks_run_once_per_tree_and_are_cached(self) -> None:
        marker = self.state_dir / "ran"
        self.rig_config({"merge_checks": [
            {"key": "tests", "command": f"{sys.executable} -c \"import pathlib,sys; pathlib.Path(sys.argv[1]).open('a').write('x'); sys.exit(0)\" {marker}"},
            {"key": "build", "command": f"{sys.executable} -c \"import os; assert os.path.exists('calc.py')\""},
            f"{sys.executable} -c \"import calc; assert calc.subtract(3, 1) == 2\"",
        ]})
        _, _, first = self.readiness()
        self.assertFalse(first["ready"])
        self.assertEqual([line["key"] for line in first["lines"]], ["work", "tests", "build", "check-3", "review", "conflicts", "acceptance"])
        pending = self.lines(first)["tests"]
        self.assertTrue(pending.get("pending") or pending["ok"])
        self.assertEqual([b["key"] for b in first["boundaries"]], ["require_approval", "require_tests"])
        doc = self.wait_settled("tests")
        lines = self.lines(doc)
        self.assertEqual(lines["tests"], {"key": "tests", "ok": True, "detail": "passed"})
        self.assertEqual(lines["build"]["ok"], True)
        self.assertEqual(lines["check-3"]["ok"], True)
        self.assertTrue(doc["ready"], doc)
        self.assertEqual([b for b in doc["boundaries"] if b["key"] == "require_tests"][0]["satisfied"], True)
        for _ in range(3):
            self.readiness()
        self.assertEqual(marker.read_text(), "x")
        cache = json.loads((self.state_dir / "merge-checks.json").read_text())
        self.assertEqual(len(cache), 1)
        self.assertTrue(next(iter(cache)).startswith(f"{RIG}:"))
        self.wait_for(lambda: not any((self.state_dir / "tmp").iterdir()))

    def test_failing_check_disables_merge_and_names_the_output(self) -> None:
        self.rig_config({"merge_checks": [{"key": "tests", "command": f"{sys.executable} -c \"import sys; print('2 failed'); sys.exit(1)\""}]})
        doc = self.wait_settled("tests")
        self.assertEqual(self.lines(doc)["tests"], {"key": "tests", "ok": False, "detail": "exit 1: 2 failed"})
        self.assertFalse(doc["ready"])
        self.approve_bead()
        status, _, receipt = self.merge()
        self.assertEqual(status, 409)
        self.assertEqual(receipt["status"], "rejected")
        self.assertEqual(receipt["body"]["code"], "not-ready")
        self.assertEqual(receipt["body"]["line"], "tests")
        self.assertEqual(receipt["body"]["detail"], "tests: exit 1: 2 failed")
        self.assertEqual(self.origin_head(), self.base)

    def test_require_tests_without_a_configured_check_blocks(self) -> None:
        self.rig_config({"boundaries": {"require_tests": True}})
        self.approve_bead()
        _, _, doc = self.readiness()
        self.assertTrue(doc["ready"])
        self.assertEqual(doc["boundaries"], [
            {"key": "require_approval", "satisfied": True, "text": "Never merge without approval"},
            {"key": "require_tests", "satisfied": False, "text": "Require tests before merge"},
        ])
        status, _, receipt = self.merge()
        self.assertEqual((status, receipt["body"]["code"], receipt["body"]["boundary"]), (403, "boundary", "require_tests"))
        self.assertEqual(receipt["body"]["detail"], "Require tests before merge")

    def test_malformed_config_falls_back_to_defaults(self) -> None:
        (self.state_dir / "rigs").mkdir()
        (self.state_dir / "rigs" / f"{RIG}.json").write_text("{not json")
        _, _, doc = self.readiness()
        self.assertTrue(doc["ready"])
        self.assertEqual([b["key"] for b in doc["boundaries"]], ["require_approval"])


class PolicyTests(MergeCase):
    """``GET .../front/policy`` (TEAM-207): the rig's supervision and boundary texts, read-only."""

    POLICY = f"/v0/city/{CITY}/front/policy"

    def test_defaults_without_a_rig_config(self) -> None:
        status, headers, doc = self.request(ALICE, "GET", self.POLICY)
        self.assertEqual(status, 200)
        self.assertIn("application/json", headers["content-type"])
        self.assertEqual(doc, {"rig": RIG, "supervision": "balanced",
                               "boundaries": [{"key": "require_approval", "text": "Never merge without approval"}]})

    def test_reads_supervision_and_boundaries_from_the_rig_config(self) -> None:
        self.rig_config({"supervision": "High",
                         "merge_checks": [{"key": "tests", "command": "true"}],
                         "boundaries": {"require_approval": True, "require_tests": True,
                                        "allowed_logins": ["alice@example.com"],
                                        "extra": ["Never touch production", 7, "  ", "Ask before adding dependencies"]}})
        _, _, doc = self.request(ALICE, "GET", f"{self.POLICY}?rig={RIG}")
        self.assertEqual(doc["rig"], RIG)
        self.assertEqual(doc["supervision"], "high")
        self.assertEqual(doc["boundaries"], [
            {"key": "require_approval", "text": "Never merge without approval"},
            {"key": "require_tests", "text": "Require tests before merge"},
            {"key": "allowed_logins", "text": "Only alice@example.com may merge"},
            {"key": "extra-1", "text": "Never touch production"},
            {"key": "extra-2", "text": "Ask before adding dependencies"},
        ])

    def test_boundaries_off_and_unknown_supervision_fall_back(self) -> None:
        self.rig_config({"supervision": "yolo", "boundaries": {"require_approval": False}})
        _, _, doc = self.request(ALICE, "GET", self.POLICY)
        self.assertEqual(doc, {"rig": RIG, "supervision": "balanced", "boundaries": []})
        self.assertTrue(any("supervision" in line and "yolo" in line for line in self.log_lines))

    def test_unknown_rig_answers_the_defaults_under_that_name(self) -> None:
        _, _, doc = self.request(ALICE, "GET", f"{self.POLICY}?rig=other")
        self.assertEqual((doc["rig"], doc["supervision"]), ("other", "balanced"))
        status, _, problem = self.request(ALICE, "GET", f"{self.POLICY}?rig=../x")
        self.assertEqual((status, problem["code"]), (422, "rig-unusable"))

    def test_no_rig_in_the_city_answers_the_defaults_with_an_empty_rig(self) -> None:
        self.stub.rigs.clear()
        _, _, doc = self.request(ALICE, "GET", self.POLICY)
        self.assertEqual(doc, {"rig": "", "supervision": "balanced",
                               "boundaries": [{"key": "require_approval", "text": "Never merge without approval"}]})

    def test_policy_is_gated_like_every_read_and_never_a_mutation(self) -> None:
        status, _, problem = self.request(BOB, "GET", self.POLICY)
        self.assertEqual((status, problem["type"]), (403, "urn:opencode-mobile:front:identity-not-allowed"))
        status, _, problem = self.request(STRANGER, "GET", self.POLICY)
        self.assertEqual((status, problem["type"]), (403, "urn:opencode-mobile:front:peer-not-on-tailnet"))
        status, _, _ = self.request(ALICE, "POST", self.POLICY, {"Idempotency-Key": "p-1"}, {})
        self.assertEqual(status, 405)
        self.assertEqual(self.upstream_posts(), [])


class ApproveTests(MergeCase):
    def test_approve_patches_metadata_and_clears_the_label(self) -> None:
        self.stub.beads["oc-xru"]["labels"] = ["needs-review"]
        status, headers, receipt = self.request(ALICE, "POST", APPROVE, {"Idempotency-Key": "a-1"}, {})
        self.assertEqual(status, 200)
        self.assertEqual(receipt["status"], "accepted")
        self.assertEqual(receipt["upstream_status"], 200)
        self.assertEqual(receipt["idempotency_key"], "a-1")
        self.assertTrue(receipt["request_id"].startswith("front-"))
        self.assertEqual(headers["x-gc-request-id"], receipt["request_id"])
        self.assertEqual(len(self.stub.patches), 1)
        bead_id, body = self.stub.patches[0]
        self.assertEqual(bead_id, "oc-xru")
        self.assertEqual(body["metadata"]["review.approved_by"], "alice@example.com")
        self.assertRegex(body["metadata"]["review.approved_at"], r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$")
        self.assertEqual(body["remove_labels"], ["needs-review"])
        self.assertEqual(receipt["body"]["metadata"]["review.approved_by"], "alice@example.com")
        self.assertEqual(self.stub.requests[-1]["headers"]["x-gc-request"], "opencode-mobile-front")
        again = self.request(ALICE, "POST", APPROVE, {"Idempotency-Key": "a-1"}, {})
        self.assertEqual(again[2], receipt)
        self.assertEqual(again[1]["idempotent-replayed"], "true")
        self.assertEqual(len(self.stub.patches), 1)
        _, _, doc = self.readiness()
        self.assertEqual(doc["mergeRequest"]["approvedBy"], "alice@example.com")
        self.assertEqual(doc["boundaries"][0]["satisfied"], True)

    def test_approve_unknown_bead_is_a_rejected_receipt(self) -> None:
        status, _, receipt = self.request(ALICE, "POST", f"/v0/city/{CITY}/front/mr/nope/approve", {"Idempotency-Key": "a-2"}, {})
        self.assertEqual(status, 404)
        self.assertEqual(receipt["status"], "rejected")
        self.assertEqual(receipt["upstream_status"], 404)
        self.assertEqual(receipt["body"]["code"], "not-found")
        self.assertEqual(self.stub.patches, [])

    def test_approve_is_gated_like_every_write(self) -> None:
        self.assertEqual(self.request(BOB, "POST", APPROVE, {"Idempotency-Key": "a-3"}, {})[0], 403)
        self.assertEqual(self.request(STRANGER, "POST", APPROVE, body={})[0], 403)
        self.assertEqual(self.request(ALICE, "GET", APPROVE)[0], 405)
        self.assertEqual(self.stub.patches, [])


class MergeTests(MergeCase):
    def test_merge_without_approval_is_refused_by_the_boundary(self) -> None:
        status, _, receipt = self.merge()
        self.assertEqual(status, 403)
        self.assertEqual(receipt["status"], "rejected")
        self.assertEqual(receipt["body"]["code"], "boundary")
        self.assertEqual(receipt["body"]["boundary"], "require_approval")
        self.assertEqual(receipt["body"]["detail"], "Never merge without approval")
        self.assertEqual(self.origin_head(), self.base)
        self.assertFalse(any("push" in argv for argv in self.front.git_calls))

    def test_allowed_logins_boundary(self) -> None:
        self.rig_config({"boundaries": {"require_approval": False, "allowed_logins": ["carol@example.com"]}})
        _, _, doc = self.readiness()
        self.assertEqual(doc["boundaries"], [{"key": "allowed_logins", "satisfied": False, "text": "Only carol@example.com may merge"}])
        status, _, receipt = self.merge()
        self.assertEqual((status, receipt["body"]["boundary"]), (403, "allowed_logins"))
        self.assertEqual(self.origin_head(), self.base)

    def test_not_ready_merge_names_the_first_failing_line(self) -> None:
        self.stub.beads["oc-1"]["status"] = "open"
        self.approve_bead()
        status, _, receipt = self.merge()
        self.assertEqual(status, 409)
        self.assertEqual(receipt["body"]["code"], "not-ready")
        self.assertEqual(receipt["body"]["line"], "work")
        self.assertEqual(receipt["body"]["detail"], "work: 1 open: oc-1")
        self.assertEqual(self.origin_head(), self.base)

    def test_fast_forward_merge_pushes_and_replays(self) -> None:
        self.approve_bead()
        before = (self.rig / "calc.py").read_text()
        status, headers, receipt = self.merge("m-ff")
        self.assertEqual(status, 200, receipt)
        self.assertEqual(receipt["status"], "accepted")
        self.assertEqual(receipt["upstream_status"], 200)
        self.assertEqual(receipt["body"], {"status": "merged", "mergeCommit": self.tip, "branch": "main", "alreadyMerged": False,
                                           "branches": ["polecat/oc-1"], "fastForward": True})
        self.assertEqual(headers["x-gc-request-id"], receipt["request_id"])
        self.assertEqual(self.origin_head(), self.tip)
        self.assertEqual(git("rev-parse", "refs/heads/polecat/oc-1", cwd=self.origin), self.tip)  # branch kept
        self.assertEqual((self.rig / "calc.py").read_text(), before)  # the rig's checkout is untouched
        self.assertEqual(git("rev-parse", "HEAD", cwd=self.rig), self.base)
        self.assertEqual(git("rev-parse", "origin/main", cwd=self.rig), self.tip)  # but it fetched
        pushes = [argv for argv in self.front.git_calls if "push" in argv]
        self.assertEqual(len(pushes), 1)
        self.assertEqual(pushes[0][-4:], ["push", "--quiet", "origin", "HEAD:refs/heads/main"])
        self.assert_no_forbidden_git()
        self.wait_for(lambda: not any((self.state_dir / "tmp").iterdir()))

        _, _, doc = self.readiness()
        self.assertTrue(doc["ready"])
        self.assertEqual(doc["branches"], [])
        self.assertEqual(self.lines(doc)["conflicts"]["detail"], "nothing left to merge · already on main")
        self.assertEqual(doc["mergeCommit"], self.tip)
        self.assertEqual((doc["files"], doc["additions"], doc["deletions"]), (0, 0, 0))

        again = self.merge("m-ff")
        self.assertEqual(again[2], receipt)
        self.assertEqual(again[1]["idempotent-replayed"], "true")
        self.assertEqual(len([argv for argv in self.front.git_calls if "push" in argv]), 1)

        fresh = self.merge("m-ff-2")
        self.assertEqual(fresh[0], 200)
        self.assertEqual(fresh[2]["body"]["alreadyMerged"], True)
        self.assertEqual(fresh[2]["body"]["mergeCommit"], self.tip)
        self.assertEqual(len([argv for argv in self.front.git_calls if "push" in argv]), 1)

    def test_two_branches_merge_with_a_merge_commit(self) -> None:
        second = self.branch("polecat/oc-2", "README.md", "# calc\n", "Add readme")
        self.stub.convoys[0]["dependencies"].append({"issue_id": "oc-xru", "depends_on_id": "oc-2", "type": "tracks"})
        self.stub.beads["oc-2"] = {"id": "oc-2", "title": "Add readme", "status": "closed", "issue_type": "task",
                                   "metadata": {"rig": RIG, "branch": "polecat/oc-2"}, "labels": []}
        self.approve_bead()
        _, _, doc = self.readiness()
        self.assertTrue(doc["ready"], doc)
        self.assertEqual((doc["files"], doc["additions"], doc["deletions"]), (2, 5, 0))
        status, _, receipt = self.merge("m-2")
        self.assertEqual(status, 200, receipt)
        commit = receipt["body"]["mergeCommit"]
        self.assertEqual(self.origin_head(), commit)
        self.assertFalse(receipt["body"]["fastForward"])
        parents = git("log", "-1", "--format=%P", commit, cwd=self.origin).split()
        self.assertEqual(len(parents), 2)
        self.assertEqual(git("log", "-1", "--format=%an <%ae>", commit, cwd=self.origin), "alice@example.com <alice@example.com>")
        self.assertEqual(git("log", "-1", "--format=%cn", commit, cwd=self.origin), "opencode-mobile-front")
        self.assertIn("Merge polecat/oc-2 (Add readme)", git("log", "-1", "--format=%s", commit, cwd=self.origin))
        self.assertEqual(git("rev-parse", "refs/heads/polecat/oc-2", cwd=self.origin), second)
        self.assertEqual(git("cat-file", "-p", f"{commit}:README.md", cwd=self.origin), "# calc")
        self.assert_no_forbidden_git()

    def test_merge_is_gated_like_every_write(self) -> None:
        self.approve_bead()
        self.assertEqual(self.merge(peer=BOB)[0], 403)
        self.assertEqual(self.merge(peer=STRANGER)[0], 403)
        self.assertEqual(self.request(ALICE, "GET", MERGE)[0], 405)
        self.assertEqual(self.origin_head(), self.base)

    def test_well_known_advertises_merge_for_a_rig_with_origin_and_default_branch(self) -> None:
        _, _, body = self.request(ALICE, "GET", front.WELL_KNOWN)
        self.assertEqual(body["capabilities"], {"read": True, "control": True, "merge": True})
        self.assertEqual(body["version"], "stub-1.4.1")

    def test_log_lines_name_the_front_routes_without_bodies(self) -> None:
        self.approve_bead()
        self.merge("m-log")
        joined = "\n".join(self.log_lines)
        self.assertIn(f"POST {MERGE} login=alice@example.com status=200 request_id=front-", joined)
        self.assertNotIn("m-log", joined)


if __name__ == "__main__":
    unittest.main()
