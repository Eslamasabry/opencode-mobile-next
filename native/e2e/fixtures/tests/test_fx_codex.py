"""FX-CODEX self-test: the Codex app-server stub (plan 14 §4.4, E2E-29).

    python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures -p 'test_fx_codex.py' -v
"""

from __future__ import annotations

import base64
import json
import os
import socket
import struct
import time
from typing import Any

from tests.fxtest import FixtureCase, request

TOKEN = "fixture-token"


class WsClosed(Exception):
    pass


class Ws:
    """A tiny stdlib WebSocket client (masked client frames, RFC 6455)."""

    def __init__(self, port: int, *, path: str = "/", headers: dict[str, str] | None = None,
                 timeout: float = 5.0) -> None:
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        lines = [f"GET {path} HTTP/1.1", f"Host: 127.0.0.1:{port}", "Upgrade: websocket",
                 "Connection: Upgrade", "Sec-WebSocket-Version: 13", f"Sec-WebSocket-Key: {key}"]
        lines += [f"{k}: {v}" for k, v in (headers or {}).items()]
        raw = b""
        try:
            self.sock.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
            while b"\r\n\r\n" not in raw:
                chunk = self.sock.recv(4096)
                if not chunk:
                    break
                raw += chunk
        except OSError:
            self.sock.close()
            raise
        head, _, self.buffer = raw.partition(b"\r\n\r\n")
        self.status = int(head.split(b" ", 2)[1]) if head.startswith(b"HTTP/1.1 ") else 0
        self.headers = {}
        for line in head.decode("latin1").split("\r\n")[1:]:
            k, _, v = line.partition(":")
            self.headers[k.strip().lower()] = v.strip()

    def _read(self, n: int) -> bytes:
        while len(self.buffer) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise WsClosed("eof")
            self.buffer += chunk
        out, self.buffer = self.buffer[:n], self.buffer[n:]
        return out

    def send_frame(self, payload: bytes, opcode: int = 1) -> None:
        mask = os.urandom(4)
        n = len(payload)
        if n < 126:
            head = bytes([0x80 | opcode, 0x80 | n])
        elif n <= 0xFFFF:
            head = bytes([0x80 | opcode, 0x80 | 126]) + struct.pack(">H", n)
        else:
            head = bytes([0x80 | opcode, 0x80 | 127]) + struct.pack(">Q", n)
        self.sock.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def send(self, obj: Any) -> None:
        self.send_frame(json.dumps(obj).encode())

    def recv_frame(self, timeout: float = 5.0) -> tuple[int, bytes]:
        self.sock.settimeout(timeout)
        first, second = self._read(2)
        n = second & 0x7F
        if n == 126:
            n = struct.unpack(">H", self._read(2))[0]
        elif n == 127:
            n = struct.unpack(">Q", self._read(8))[0]
        payload = self._read(n)
        opcode = first & 0x0F
        if opcode == 8:
            code = struct.unpack(">H", payload[:2])[0] if len(payload) >= 2 else 1005
            raise WsClosed(code)
        return opcode, payload

    def recv(self, timeout: float = 5.0) -> dict[str, Any]:
        while True:
            opcode, payload = self.recv_frame(timeout)
            if opcode == 1:
                return json.loads(payload)

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


class Rpc(Ws):
    """JSON-RPC helpers for the Codex app-server protocol."""

    def __init__(self, port: int, token: str = TOKEN, **kw: Any) -> None:
        super().__init__(port, headers={"Authorization": f"Bearer {token}"}, **kw)
        self.next_id = 0
        self.seen: list[dict[str, Any]] = []

    def call(self, method: str, params: dict[str, Any] | None = None, timeout: float = 5.0) -> dict[str, Any]:
        self.next_id += 1
        rid = self.next_id
        self.send({"id": rid, "method": method, "params": params or {}})
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            msg = self.recv(timeout)
            if msg.get("id") == rid and ("result" in msg or "error" in msg):
                return msg
            self.seen.append(msg)
        raise TimeoutError(method)

    def init(self) -> dict[str, Any]:
        reply = self.call("initialize", {"clientInfo": {"name": "fxtest", "version": "1"},
                                          "capabilities": {"experimentalApi": False}})
        self.send({"method": "initialized", "params": {}})
        return reply

    def wait(self, method: str, timeout: float = 10.0, **match: Any) -> dict[str, Any]:
        """The next notification/request named ``method`` (earlier ones kept in seen)."""
        for i, msg in enumerate(self.seen):
            if msg.get("method") == method and all(msg.get("params", {}).get(k) == v for k, v in match.items()):
                return self.seen.pop(i)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            msg = self.recv(max(0.1, deadline - time.monotonic()))
            if msg.get("method") == method and all(msg.get("params", {}).get(k) == v for k, v in match.items()):
                return msg
            self.seen.append(msg)
        raise TimeoutError(method)


def text_input(text: str) -> list[dict[str, Any]]:
    return [{"type": "text", "text": text, "text_elements": []}]


class FxCodexTest(FixtureCase):
    FIXTURE = "FX-CODEX"

    def setUp(self) -> None:
        self.ctl("reset")
        self.project = self.ready["project"]

    def rpc(self, **kw: Any) -> Rpc:
        client = Rpc(self.port("ws"), **kw)
        self.addCleanup(client.close)
        self.assertEqual(client.status, 101)
        return client

    def new_thread(self, client: Rpc) -> str:
        reply = client.call("thread/start", {"cwd": self.project, "approvalPolicy": "on-request",
                                             "sandbox": "read-only"})
        return reply["result"]["thread"]["id"]

    # -- contract ----------------------------------------------------------------

    def test_ready_names_project_and_token(self) -> None:
        self.assertTrue(os.path.isdir(self.project))
        self.assertTrue(self.project.startswith(str(self.run_dir)))
        # The token is a public constant; it is never written into the run dir.
        self.assertEqual(self.ready["auth"], "bearer")
        self.assertEqual(self.ready["url"], f"ws://127.0.0.1:{self.port('ws')}")
        self.assertIn("other_project", self.ready)

    def test_initialize_and_health_paths(self) -> None:
        reply = self.rpc().init()
        self.assertIn("0.153.4", reply["result"]["userAgent"])
        for path in ("/readyz", "/healthz"):
            self.assertEqual(request("GET", self.port("ws"), path).status, 200)
        self.ctl("health", {"ok": False})
        self.assertEqual(request("GET", self.port("ws"), "/readyz").status, 503)
        self.assertIn("error", self.rpc().call("initialize", {}))
        self.ctl("health", {"ok": True})
        self.assertIn("result", self.rpc().init())

    def test_bad_token_is_refused(self) -> None:
        bad = Ws(self.port("ws"), headers={"Authorization": "Bearer wrong-token"})
        self.addCleanup(bad.close)
        self.assertEqual(bad.status, 401)
        none = Ws(self.port("ws"))
        self.addCleanup(none.close)
        self.assertEqual(none.status, 401)

    def test_thread_list_is_scoped_by_cwd(self) -> None:
        client = self.rpc()
        client.init()
        rows = client.call("thread/list", {"cwd": self.project, "limit": 100, "archived": False})["result"]["data"]
        self.assertEqual([r["id"] for r in rows], ["thread-history"])
        self.assertTrue(all(r["cwd"] == self.project for r in rows))
        other = client.call("thread/list", {"cwd": self.ready["other_project"]})["result"]["data"]
        self.assertEqual([r["id"] for r in other], ["thread-other"])
        read = client.call("thread/read", {"threadId": "thread-history", "includeTurns": True})["result"]
        self.assertEqual(read["thread"]["turns"][0]["status"], "completed")
        self.assertIn("result", client.call("model/list", {"limit": 100}))

    def test_turn_streams_deltas_and_completes(self) -> None:
        client = self.rpc()
        client.init()
        thread = self.new_thread(client)
        started = client.call("turn/start", {"threadId": thread, "input": text_input("normal journey")})
        turn_id = started["result"]["turn"]["id"]
        done = client.wait("turn/completed")
        self.assertEqual(done["params"]["turn"]["id"], turn_id)
        self.assertEqual(done["params"]["turn"]["status"], "completed")
        deltas = [m for m in client.seen if m.get("method") == "item/agentMessage/delta"]
        self.assertGreaterEqual(len(deltas), 2)
        self.assertTrue(all(d["params"]["turnId"] == turn_id for d in deltas))

    def test_approval_accept_and_decline(self) -> None:
        client = self.rpc()
        client.init()
        thread = self.new_thread(client)
        for decision, status in (("accept", "completed"), ("decline", "failed")):
            client.call("turn/start", {"threadId": thread, "input": text_input("approval journey")})
            ask = client.wait("item/commandExecution/requestApproval")
            self.assertIsInstance(ask["id"], int)
            client.send({"id": ask["id"], "result": {"decision": decision}})
            done = client.wait("turn/completed")
            self.assertEqual(done["params"]["turn"]["status"], status, decision)
            client.seen.clear()

    def test_steer_changes_the_in_progress_turn_then_interrupt(self) -> None:
        client = self.rpc()
        client.init()
        thread = self.new_thread(client)
        turn_id = client.call("turn/start", {"threadId": thread, "input": text_input("long-running journey")})[
            "result"]["turn"]["id"]
        client.wait("item/completed", turnId=turn_id)
        wrong = client.call("turn/steer", {"threadId": thread, "input": text_input("x"), "expectedTurnId": "turn-x"})
        self.assertIn("error", wrong)
        steered = client.call("turn/steer", {"threadId": thread, "input": text_input("steer it"),
                                             "expectedTurnId": turn_id})
        self.assertEqual(steered["result"]["turnId"], turn_id)
        user = client.wait("item/started", turnId=turn_id)
        while user["params"]["item"]["type"] != "userMessage":
            user = client.wait("item/started", turnId=turn_id)
        reply = client.wait("item/completed", turnId=turn_id)
        while reply["params"]["item"]["type"] != "agentMessage":
            reply = client.wait("item/completed", turnId=turn_id)
        self.assertIn("steer", reply["params"]["item"]["text"].lower())
        read = client.call("thread/read", {"threadId": thread, "includeTurns": True})["result"]["thread"]
        turn = read["turns"][-1]
        self.assertEqual(turn["status"], "inProgress")
        self.assertEqual([i["type"] for i in turn["items"]].count("userMessage"), 2)
        self.assertIn("result", client.call("turn/interrupt", {"threadId": thread, "turnId": turn_id}))
        done = client.wait("turn/completed", threadId=thread)
        self.assertEqual(done["params"]["turn"]["status"], "interrupted")
        idle = client.call("turn/steer", {"threadId": thread, "input": text_input("late"), "expectedTurnId": turn_id})
        self.assertIn("error", idle)

    def test_drop_socket_mid_turn_keeps_authoritative_state(self) -> None:
        client = self.rpc()
        client.init()
        thread = self.new_thread(client)
        client.call("turn/start", {"threadId": thread, "input": text_input("slow journey")})
        client.wait("item/agentMessage/delta")
        dropped = self.ctl("drop-socket").json()
        self.assertGreaterEqual(dropped["closed"], 1)
        with self.assertRaises((WsClosed, OSError)):
            for _ in range(200):
                client.recv(3)
        again = self.rpc()
        again.init()
        deadline = time.monotonic() + 15
        while True:
            turn = again.call("thread/read", {"threadId": thread, "includeTurns": True})["result"]["thread"]["turns"][-1]
            if turn["status"] != "inProgress" or time.monotonic() > deadline:
                break
            time.sleep(0.3)
        self.assertEqual(turn["status"], "completed")
        self.assertEqual([i["type"] for i in turn["items"]], ["userMessage", "agentMessage"])

    def test_refuse_connections(self) -> None:
        self.ctl("refuse-connections", {"on": True})
        try:
            with self.assertRaises((OSError, WsClosed, IndexError, ValueError)):
                probe = Rpc(self.port("ws"), timeout=2)
                self.addCleanup(probe.close)
                if probe.status == 101:
                    probe.init()
                else:
                    raise WsClosed(probe.status)
        finally:
            self.ctl("refuse-connections", {"on": False})
        self.assertIn("result", self.rpc().init())

    def test_fail_method(self) -> None:
        client = self.rpc()
        client.init()
        self.ctl("fail-method", {"method": "thread/list", "on": True})
        self.assertIn("error", client.call("thread/list", {"cwd": self.project}))
        self.ctl("fail-method", {"method": "thread/list", "on": False})
        self.assertIn("result", client.call("thread/list", {"cwd": self.project}))

    def test_request_log_is_metadata_only(self) -> None:
        marker = "prompt-marker-" + os.urandom(6).hex()
        client = self.rpc()
        client.init()
        thread = self.new_thread(client)
        client.call("turn/start", {"threadId": thread, "input": text_input(marker)})
        client.wait("turn/completed")
        log = self.requests_log()
        methods = {row.get("method") for row in log}
        self.assertTrue({"websocket/upgrade", "initialize", "turn/start"} <= methods, methods)
        for row in log:
            self.assertLessEqual(set(row), {"method", "path", "status", "transport", "t", "auth", "ms"}, row)
        self.assert_no_secret_in_run_dir(TOKEN, marker)


if __name__ == "__main__":
    import unittest
    unittest.main()
