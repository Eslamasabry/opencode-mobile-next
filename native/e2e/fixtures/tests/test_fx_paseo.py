"""FX-PASEO self-test: the Paseo daemon stub (plan 14 §4.4, E2E-30).

Runs the stub (``FX_PASEO_MODE=stub``) so it needs no real daemon:

    python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures -p 'test_fx_paseo.py' -v
"""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path
from typing import Any

from tests.fxtest import FixtureCase
from tests.test_fx_codex import Ws, WsClosed

PASSWORD = "fx-paseo-test-" + os.urandom(6).hex()


class Paseo(Ws):
    """The app's side of the Paseo protocol 1 (lib/paseo/transport.dart)."""

    def __init__(self, port: int, password: str = "", hello: bool = True, **kw: Any) -> None:
        headers = {"Sec-WebSocket-Protocol": f"paseo.bearer.{password}"} if password else {}
        super().__init__(port, path="/ws", headers=headers, **kw)
        self.n = 0
        self.events: list[dict[str, Any]] = []
        self.binary: list[tuple[int, int, bytes]] = []
        self.server_info: dict[str, Any] | None = None
        if hello and self.status == 101:
            self.hello(password)

    def hello(self, password: str = "") -> dict[str, Any]:
        body: dict[str, Any] = {"type": "hello", "clientId": "cid_fxtest", "clientType": "mobile",
                                "protocolVersion": 1, "appVersion": "0.8.0",
                                "capabilities": {"all_providers": True, "hello_rejection": True}}
        if password:
            body["auth"] = {"kind": "password", "password": password}
        self.send(body)
        while True:
            msg = self.recv()
            if msg.get("type") == "session" and msg["message"]["type"] == "status" \
                    and msg["message"]["payload"].get("status") == "server_info":
                self.server_info = msg["message"]["payload"]
                return self.server_info

    def next_message(self, timeout: float = 5.0) -> tuple[str, dict[str, Any]]:
        while True:
            opcode, payload = self.recv_frame(timeout)
            if opcode == 2:
                self.binary.append((payload[0], payload[1], payload[2:]))
                continue
            if opcode != 1:
                continue
            msg = json.loads(payload)
            if msg.get("type") != "session":
                continue
            inner = msg["message"]
            return inner["type"], inner.get("payload") or {}

    def request(self, type_: str, body: dict[str, Any] | None = None, timeout: float = 10.0,
                progress: list[dict[str, Any]] | None = None) -> tuple[str, dict[str, Any]]:
        self.n += 1
        rid = f"m1_{self.n}"
        self.send({"type": "session", "message": {**(body or {}), "type": type_, "requestId": rid}})
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            kind, payload = self.next_message(max(0.1, deadline - time.monotonic()))
            if payload.get("requestId") == rid:
                if kind.endswith(".progress"):
                    if progress is not None:
                        progress.append(payload)
                    continue
                return kind, payload
            self.events.append({"type": kind, "payload": payload})
        raise TimeoutError(type_)

    def push(self, type_: str, body: dict[str, Any]) -> None:
        self.send({"type": "session", "message": {**body, "type": type_}})

    def wait_event(self, type_: str, timeout: float = 10.0, pred: Any = None) -> dict[str, Any]:
        for i, event in enumerate(self.events):
            if event["type"] == type_ and (pred is None or pred(event["payload"])):
                return self.events.pop(i)["payload"]
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            kind, payload = self.next_message(max(0.1, deadline - time.monotonic()))
            if kind == type_ and (pred is None or pred(payload)):
                return payload
            self.events.append({"type": kind, "payload": payload})
        raise TimeoutError(type_)

    def stream_until(self, agent_id: str, end: set[str], timeout: float = 15.0) -> list[dict[str, Any]]:
        """agent_stream inner events for one agent until one of ``end`` arrives."""
        seen: list[dict[str, Any]] = []
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            payload = self.wait_event("agent_stream", timeout=max(0.1, deadline - time.monotonic()),
                                      pred=lambda p: p.get("agentId") == agent_id)
            seen.append(payload["event"])
            if payload["event"]["type"] in end:
                return seen
        raise TimeoutError(end)

    def terminal_text(self, slot: int, needle: str, timeout: float = 10.0) -> str:
        deadline = time.monotonic() + timeout
        while True:
            data = b"".join(p for op, s, p in self.binary if s == slot)
            text = data.decode("utf-8", "replace")
            if needle in text:
                return text
            if time.monotonic() > deadline:
                raise AssertionError(f"{needle!r} not in terminal output {text[-300:]!r}")
            try:
                kind, payload = self.next_message(0.5)
                self.events.append({"type": kind, "payload": payload})
            except (TimeoutError, OSError):
                pass


class FxPaseoStubTest(FixtureCase):
    FIXTURE = "FX-PASEO"
    EXTRA_ENV = {"FX_PASEO_MODE": "stub"}

    def setUp(self) -> None:
        self.ctl("reset")
        self.project = self.ready["project"]

    def client(self, **kw: Any) -> Paseo:
        c = Paseo(self.port("daemon"), **kw)
        self.addCleanup(c.close)
        self.assertEqual(c.status, 101)
        return c

    def agents(self, c: Paseo) -> list[dict[str, Any]]:
        _, reply = c.request("fetch_agents_request", {"sort": [{"key": "updated_at", "direction": "desc"}],
                                                      "page": {"limit": 200},
                                                      "subscribe": {"subscriptionId": "opencode-mobile"}})
        return [e["agent"] for e in reply["entries"]]

    # -- contract ----------------------------------------------------------------

    def test_ready_and_files_on_disk(self) -> None:
        self.assertEqual(self.ready["mode"], "stub")
        self.assertEqual(self.ready["url"], f"ws://127.0.0.1:{self.port('daemon')}/ws")
        self.assertEqual(self.ready["daemon_version"], "0.11.2")
        self.assertTrue(Path(self.project).is_dir())
        files = [Path(p) for p in self.ready["claude_history"]]
        self.assertGreaterEqual(len(files), 2)
        for path in files:
            self.assertTrue(path.is_file(), path)
            self.assertIn("/.claude/projects/", str(path))
            rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
            self.assertTrue({"user", "assistant"} <= {r["type"] for r in rows})
        for name in ("claude", "pi"):
            script = Path(self.ready["bin"]) / name
            self.assertTrue(os.access(script, os.X_OK), script)

    def test_fake_claude_speaks_stream_json(self) -> None:
        claude = str(Path(self.ready["bin"]) / "claude")
        version = subprocess.run([claude, "--version"], capture_output=True, text=True, timeout=10)
        self.assertIn("(Claude Code)", version.stdout)
        out = subprocess.run([claude, "-p", "say hello", "--output-format", "stream-json", "--verbose"],
                             capture_output=True, text=True, timeout=20, cwd=self.project)
        self.assertEqual(out.returncode, 0, out.stderr)
        rows = [json.loads(line) for line in out.stdout.splitlines() if line.strip()]
        self.assertEqual((rows[0]["type"], rows[0]["subtype"]), ("system", "init"))
        blocks = [b["type"] for r in rows if r["type"] == "assistant" for b in r["message"]["content"]]
        self.assertIn("tool_use", blocks)
        self.assertIn("text", blocks)
        self.assertEqual((rows[-1]["type"], rows[-1]["subtype"], rows[-1]["is_error"]), ("result", "success", False))
        self.assertTrue(all(r.get("session_id") == rows[0]["session_id"] for r in rows))
        # SDK mode: stream-json in, control protocol answered.
        sdk = subprocess.run([claude, "--input-format", "stream-json", "--output-format", "stream-json", "--verbose"],
                             input=json.dumps({"type": "control_request", "request_id": "r1",
                                               "request": {"subtype": "initialize"}}) + "\n" +
                             json.dumps({"type": "user", "message": {"role": "user", "content": "hi"}}) + "\n",
                             capture_output=True, text=True, timeout=20, cwd=self.project)
        rows = [json.loads(line) for line in sdk.stdout.splitlines() if line.strip()]
        self.assertEqual(rows[0]["type"], "control_response")
        self.assertEqual(rows[-1]["type"], "result")

    def test_fake_pi_speaks_rpc(self) -> None:
        pi = str(Path(self.ready["bin"]) / "pi")
        out = subprocess.run([pi, "--mode", "rpc"], input=json.dumps({"id": "1", "type": "prompt", "message": "hi"}) + "\n",
                             capture_output=True, text=True, timeout=20, cwd=self.project)
        rows = [json.loads(line) for line in out.stdout.splitlines() if line.strip()]
        types = [r["type"] for r in rows]
        self.assertEqual(rows[0], {"id": "1", "type": "response", "command": "prompt", "success": True})
        for kind in ("agent_start", "message_update", "tool_execution_end", "agent_end"):
            self.assertIn(kind, types)

    def test_probe_version_and_password_free_hello(self) -> None:
        c = self.client()
        self.assertEqual(c.server_info["version"], "0.11.2")
        c.send({"type": "ping"})
        self.assertEqual(c.recv()["type"], "pong")

    def test_provider_snapshot_lists_models_and_modes(self) -> None:
        c = self.client()
        _, snap = c.request("get_providers_snapshot_request", {"cwd": self.project})
        by = {e["provider"]: e for e in snap["entries"]}
        self.assertEqual(by["claude"]["status"], "ready")
        self.assertEqual(by["pi"]["status"], "ready")
        claude_models = {m["id"]: m for m in by["claude"]["models"]}
        self.assertTrue(any(m.get("isDefault") for m in claude_models.values()))
        self.assertTrue(any(m.get("aliases") for m in claude_models.values()))
        self.assertIn("plan", [m["id"] for m in by["claude"]["modes"]])
        self.assertTrue(by["pi"]["models"])
        self.ctl("set-provider", {"provider": "pi", "status": "error", "error": "AuthRequired"})
        _, snap = c.request("get_providers_snapshot_request", {"cwd": self.project})
        self.assertEqual({e["provider"]: e for e in snap["entries"]}["pi"]["error"], "AuthRequired")

    def test_agent_list_is_seeded_and_timeline_reads(self) -> None:
        c = self.client()
        agents = self.agents(c)
        mine = [a for a in agents if a["cwd"] == self.project]
        self.assertEqual({a["provider"] for a in mine}, {"claude", "pi"})
        self.assertTrue(all(a["archivedAt"] is None for a in agents))
        self.assertTrue(any(a["cwd"] != self.project for a in agents))
        claude = next(a for a in mine if a["provider"] == "claude")
        _, reply = c.request("fetch_agent_request", {"agentId": claude["id"]})
        self.assertEqual(reply["agent"]["id"], claude["id"])
        _, timeline = c.request("fetch_agent_timeline_request", {"agentId": claude["id"], "direction": "tail",
                                                                 "limit": 0, "projection": "projected"})
        kinds = [e["item"]["type"] for e in timeline["entries"]]
        self.assertEqual(kinds[0], "user_message")
        self.assertIn("tool_call", kinds)
        self.assertEqual(kinds[-1], "assistant_message")
        self.assertEqual(timeline["endCursor"]["seq"], timeline["entries"][-1]["seqStart"])
        _, missing = c.request("fetch_agent_request", {"agentId": "nope"})
        self.assertIsNone(missing["agent"])

    def test_scripted_claude_turn_from_create(self) -> None:
        c = self.client()
        self.agents(c)
        kind, created = c.request("create_agent_request", {
            "config": {"provider": "claude", "cwd": self.project, "modeId": "default"},
            "initialPrompt": "create a turn", "clientMessageId": "client-1", "labels": {}})
        agent = created["agent"]
        self.assertEqual((kind, created["status"]), ("status", "agent_created"))
        self.assertEqual((agent["provider"], agent["cwd"]), ("claude", self.project))
        events = c.stream_until(agent["id"], {"turn_completed", "turn_failed"})
        self.assertEqual(events[0]["type"], "turn_started")
        items = [e["item"] for e in events if e["type"] == "timeline"]
        self.assertEqual(items[0], {"type": "user_message", "text": "create a turn", "messageId": "client-1"})
        self.assertIn("tool_call", [i["type"] for i in items])
        deltas = [i for i in items if i["type"] == "assistant_message"]
        self.assertGreaterEqual(len(deltas), 2)
        self.assertEqual(len({i["messageId"] for i in deltas}), 1)
        self.assertEqual(events[-1]["type"], "turn_completed")
        _, timeline = c.request("fetch_agent_timeline_request", {"agentId": agent["id"], "direction": "tail",
                                                                 "limit": 0, "projection": "projected"})
        merged = [e["item"] for e in timeline["entries"] if e["item"]["type"] == "assistant_message"]
        self.assertEqual(len(merged), 1)
        self.assertEqual(merged[0]["text"], "".join(i["text"] for i in deltas))

    def test_scripted_pi_turn_steer_and_cancel(self) -> None:
        c = self.client()
        pi = next(a for a in self.agents(c) if a["provider"] == "pi" and a["cwd"] == self.project)
        _, sent = c.request("send_agent_message_request", {"agentId": pi["id"], "text": "long-running job",
                                                           "messageId": "m-1", "activeTurnBehavior": "steer"})
        self.assertTrue(sent["accepted"])
        first = c.stream_until(pi["id"], {"timeline"})
        self.assertEqual(first[0]["type"], "turn_started")
        c.wait_event("agent_stream", pred=lambda p: p.get("agentId") == pi["id"]
                     and p["event"].get("item", {}).get("type") == "assistant_message")
        _, steer = c.request("send_agent_message_request", {"agentId": pi["id"], "text": "steer now",
                                                            "messageId": "m-2", "activeTurnBehavior": "steer"})
        self.assertTrue(steer["accepted"])
        user = c.wait_event("agent_stream", pred=lambda p: p.get("agentId") == pi["id"]
                            and p["event"].get("item", {}).get("messageId") == "m-2")
        self.assertEqual(user["event"]["provider"], "pi")
        _, cancel = c.request("cancel_agent_request", {"agentId": pi["id"]})
        end = c.stream_until(pi["id"], {"turn_canceled", "turn_completed"})
        self.assertEqual(end[-1]["type"], "turn_canceled")
        self.assertEqual(sum(1 for e in end if e["type"] == "turn_started"), 0, "steer joined the same turn")

    def test_archive_and_archive_elsewhere(self) -> None:
        c = self.client()
        other = self.client()
        agents = [a for a in self.agents(c) if a["cwd"] == self.project]
        self.agents(other)
        kind, archived = c.request("archive_agent_request", {"agentId": agents[0]["id"]})
        self.assertEqual(kind, "agent_archived")
        self.assertEqual(archived["agentId"], agents[0]["id"])
        seen = other.wait_event("agent_archived")
        self.assertEqual(seen["agentId"], agents[0]["id"])
        self.assertNotIn(agents[0]["id"], [a["id"] for a in self.agents(c)])
        self.ctl("archive-agent", {"agentId": agents[1]["id"]})
        self.assertEqual(c.wait_event("agent_archived")["agentId"], agents[1]["id"])

    def test_terminals_stream_take_input_and_close(self) -> None:
        c = self.client()
        _, listed = c.request("list_terminals_request", {"cwd": self.project})
        by_title = {t["title"]: t for t in listed["terminals"]}
        self.assertTrue({"ticker", "shell"} <= set(by_title))
        _, sub = c.request("subscribe_terminal_request", {"terminalId": by_title["ticker"]["id"],
                                                          "restore": {"mode": "visible-snapshot", "scrollbackLines": 500}})
        ticker = sub["slot"]
        c.terminal_text(ticker, "tick 2")
        self.assertEqual(c.binary[0][0], 0x05, "restore frame first")
        _, sub = c.request("subscribe_terminal_request", {"terminalId": by_title["shell"]["id"]})
        shell = sub["slot"]
        self.assertNotEqual(shell, ticker)
        c.push("terminal_input", {"terminalId": by_title["shell"]["id"],
                                  "message": {"type": "resize", "rows": 30, "cols": 100}})
        c.push("terminal_input", {"terminalId": by_title["shell"]["id"],
                                  "message": {"type": "input", "data": "stty size; echo fx-$((40+2))\n"}})
        c.terminal_text(shell, "fx-42")
        c.terminal_text(shell, "30 100")
        self.ctl("terminal-emit", {"terminalId": by_title["shell"]["id"], "text": "split-é-end\r\n", "split": True})
        text = c.terminal_text(shell, "split-é-end")
        self.assertNotIn("�", text)
        _, renamed = c.request("terminal.rename.request", {"terminalId": by_title["shell"]["id"], "title": "renamed"})
        self.assertTrue(renamed["success"])
        _, killed = c.request("kill_terminal_request", {"terminalId": by_title["shell"]["id"]})
        self.assertTrue(killed["success"])
        c.wait_event("terminal_stream_exit", pred=lambda p: p.get("terminalId") == by_title["shell"]["id"])
        _, again = c.request("kill_terminal_request", {"terminalId": by_title["shell"]["id"]})
        self.assertFalse(again["success"])
        _, bad = c.request("subscribe_terminal_request", {"terminalId": "nope"})
        self.assertIsNone(bad["slot"])
        self.assertTrue(bad["error"])
        _, made = c.request("create_terminal_request", {"cwd": self.project, "name": "fresh"})
        self.assertEqual(made["terminal"]["title"], "fresh")
        _, listed = c.request("list_terminals_request", {"cwd": self.project})
        titles = [t["title"] for t in listed["terminals"]]
        self.assertIn("fresh", titles)
        self.assertNotIn("renamed", titles)

    def test_update_reports_versions_and_restarts(self) -> None:
        c = self.client()
        _, same = c.request("daemon.update.request", {})
        self.assertEqual((same["success"], same["previousVersion"], same["newVersion"]), (True, "0.11.2", "0.11.2"))
        self.ctl("set-version", {"daemon": "0.11.2", "latest": "0.12.0"})
        progress: list[dict[str, Any]] = []
        _, done = c.request("daemon.update.request", {}, progress=progress)
        self.assertEqual([p["phase"] for p in progress], ["starting", "downloading", "installing", "complete"])
        self.assertEqual((done["success"], done["previousVersion"], done["newVersion"]), (True, "0.11.2", "0.12.0"))
        with self.assertRaises((WsClosed, OSError)):
            for _ in range(50):
                c.next_message(3)
        self.assertEqual(self.client().server_info["version"], "0.12.0")
        self.ctl("set-version", {"latest": "0.13.2", "fail": "registry unreachable"})
        _, failed = self.client().request("daemon.update.request", {})
        self.assertFalse(failed["success"])
        self.assertEqual(failed["error"], "registry unreachable")

    def test_import_claude_history(self) -> None:
        c = self.client()
        _, recent = c.request("fetch_recent_provider_sessions_request", {"cwd": self.project, "providers": ["claude"],
                                                                         "limit": 50})
        entries = recent["entries"]
        self.assertGreaterEqual(len(entries), 2)
        stamps = [e["lastActivityAt"] for e in entries]
        self.assertEqual(stamps, sorted(stamps, reverse=True))
        self.assertTrue(all(e["cwd"] == self.project and e["providerId"] == "claude" for e in entries))
        kind, imported = c.request("import_agent_request", {"providerId": "claude",
                                                            "providerHandleId": entries[0]["providerHandleId"],
                                                            "cwd": self.project})
        self.assertEqual((kind, imported["status"]), ("status", "agent_resumed"))
        _, timeline = c.request("fetch_agent_timeline_request", {"agentId": imported["agent"]["id"],
                                                                 "direction": "tail", "limit": 0})
        kinds = [e["item"]["type"] for e in timeline["entries"]]
        self.assertEqual(kinds[0], "user_message")
        self.assertIn("tool_call", kinds)
        self.assertEqual(kinds[-1], "assistant_message")
        _, refused = c.request("import_agent_request", {"providerId": "claude", "providerHandleId": "missing",
                                                        "cwd": self.project})
        self.assertEqual(refused["status"], "agent_create_failed")
        _, recent = c.request("fetch_recent_provider_sessions_request", {"cwd": self.project, "providers": ["claude"]})
        self.assertEqual(recent["filteredAlreadyImportedCount"], 1)

    def test_drop_socket(self) -> None:
        c = self.client()
        self.assertGreaterEqual(self.ctl("drop-socket").json()["closed"], 1)
        with self.assertRaises((WsClosed, OSError)):
            for _ in range(50):
                c.next_message(3)
        self.assertEqual(self.client().server_info["version"], "0.11.2")

    def test_fail_request(self) -> None:
        c = self.client()
        self.ctl("fail-request", {"type": "list_terminals_request", "on": True})
        kind, reply = c.request("list_terminals_request", {"cwd": self.project})
        self.assertEqual(kind, "rpc_error")
        self.ctl("fail-request", {"type": "list_terminals_request", "on": False})
        kind, _ = c.request("list_terminals_request", {"cwd": self.project})
        self.assertEqual(kind, "list_terminals_response")

    def test_request_log_is_metadata_only(self) -> None:
        marker = "prompt-marker-" + os.urandom(6).hex()
        c = self.client()
        c.request("create_agent_request", {"config": {"provider": "claude", "cwd": self.project},
                                           "initialPrompt": marker, "clientMessageId": "x"})
        log = self.requests_log()
        types = {row.get("method") for row in log}
        self.assertTrue({"hello", "create_agent_request"} <= types, types)
        self.assert_no_secret_in_run_dir(marker)


class FxPaseoPasswordTest(FixtureCase):
    FIXTURE = "FX-PASEO"
    EXTRA_ENV = {"FX_PASEO_MODE": "stub", "FX_PASEO_PASSWORD": PASSWORD}

    def test_password_required_and_never_written(self) -> None:
        self.assertTrue(self.ready["password_required"])
        good = Paseo(self.port("daemon"), password=PASSWORD)
        self.addCleanup(good.close)
        self.assertEqual(good.server_info["version"], "0.11.2")
        bad = Paseo(self.port("daemon"), hello=False)
        self.addCleanup(bad.close)
        bad.send({"type": "hello", "clientId": "c", "clientType": "mobile", "protocolVersion": 1,
                  "auth": {"kind": "password", "password": "wrong"}, "capabilities": {"hello_rejection": True}})
        self.assertEqual(bad.recv(), {"type": "hello.rejected", "reason": "incorrect_password",
                                      "accepts": ["password"]})
        with self.assertRaises(WsClosed) as closed:
            bad.recv()
        self.assertEqual(closed.exception.args[0], 4401)
        plain = Paseo(self.port("daemon"), hello=False)
        self.addCleanup(plain.close)
        plain.send({"type": "hello", "clientId": "c", "clientType": "mobile", "protocolVersion": 1})
        with self.assertRaises(WsClosed) as closed:
            plain.recv()
        self.assertEqual(closed.exception.args[0], 4401)
        self.assert_no_secret_in_run_dir(PASSWORD)


if __name__ == "__main__":
    import unittest
    unittest.main()
