#!/usr/bin/env python3
"""FX-CODEX - a Codex app-server stub for E2E-29 (plan 14 §4.4).

A twin of tool/qa/codex_fixture/fixture_server.py on fxlib: one WebSocket
(JSON-RPC 2.0 without the "jsonrpc" key, one object per text frame) on the
``ws`` port, bearer token ``fixture-token`` (a public test value, never a real
credential; it is not written into the run dir). No model, account, provider,
shell command or network is ever used.

Methods (wire truth: lib/codex/gateway.dart, lib/codex/transport.dart):
  initialize (+ the ``initialized`` notification), thread/list (filtered by
  ``cwd``), thread/start, thread/read, thread/resume, thread/unsubscribe,
  thread/delete, thread/name/set, model/list, fs|file/list, fs|file/read,
  turn/start, turn/steer {threadId, input, expectedTurnId} -> {turnId},
  turn/interrupt {threadId, turnId}.
Server -> client: thread/started, turn/started, item/started,
  item/agentMessage/delta, item/completed, turn/completed and the approval
  request item/commandExecution/requestApproval (answered {"decision": ...}).

Scripted prompts (case-insensitive substring of the turn's text):
  approval | approve   a command asks for approval first; accept -> the turn
                       completes, decline/cancel -> the turn ends "failed"
  long-running         stays inProgress after its reply until turn/interrupt
  slow                 streams six deltas 0.5 s apart (time to steer or drop)
  anything else        three deltas, then completed
A steer adds a userMessage item and a short agentMessage reply to the
in-progress turn; the turn then goes on as scripted.

Turns run on the server, not on the socket: a dropped socket does not stop
them, and every event goes to every initialized socket (the app filters by
thread). A pending approval is sent again to a socket that resumes its thread.

HTTP on the same port, before any upgrade: GET /readyz and GET /healthz answer
200 "ok" (503 while unhealthy) without auth [U: codex app-server's websocket
listener health paths; the app itself only probes by connect + initialize].

Control (ctl port, POST, JSON body):
  drop-socket {}                      close every open WebSocket now -> {"closed": n}
  refuse-connections {"on": bool}     abort new TCP connections at once
  health {"ok": bool}                 false: /readyz 503 and initialize errors
  fail-method {"method": str, "on": bool, "code"?: int}
                                      answer that method with a JSON-RPC error
  POST /reset                         threads back to the seed, flags cleared,
                                      open sockets closed
State (GET /health "state", GET /state): connections, threads, flags.
ready.json: url, project, other_project, auth "bearer", token_hint.
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import secrets
import sys
import threading
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import LOOPBACK, SCRUB, Fixture, run_fixture  # noqa: E402

FIXTURE_ID = "FX-CODEX"
TOKEN = "fixture-token"
MAX_FRAME = 4 * 1024 * 1024
USER_AGENT = "synthetic-codex/0.153.4"
GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def now_ms() -> int:
    return int(time.time() * 1000)


def clone(value: Any) -> Any:
    return json.loads(json.dumps(value))


def ws_frame(payload: bytes, opcode: int = 1) -> bytes:
    first = 0x80 | opcode
    n = len(payload)
    if n < 126:
        return bytes([first, n]) + payload
    if n <= 0xFFFF:
        return bytes([first, 126]) + n.to_bytes(2, "big") + payload
    return bytes([first, 127]) + n.to_bytes(8, "big") + payload


async def read_frame(reader: asyncio.StreamReader) -> tuple[int, bytes]:
    first, second = await reader.readexactly(2)
    opcode = first & 0x0F
    n = second & 0x7F
    if n == 126:
        n = int.from_bytes(await reader.readexactly(2), "big")
    elif n == 127:
        n = int.from_bytes(await reader.readexactly(8), "big")
    if n > MAX_FRAME:
        raise ValueError("frame too large")
    mask = await reader.readexactly(4) if second & 0x80 else b""
    payload = bytearray(await reader.readexactly(n))
    if mask:
        for i in range(n):
            payload[i] ^= mask[i % 4]
    return opcode, bytes(payload)


class Connection:
    def __init__(self, server: "CodexServer", reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        self.server = server
        self.reader = reader
        self.writer = writer
        self.lock = asyncio.Lock()
        self.closed = False
        self.initialized = False

    async def send(self, payload: dict[str, Any]) -> None:
        if self.closed:
            return
        raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        async with self.lock:
            if self.closed:
                return
            try:
                self.writer.write(ws_frame(raw))
                await self.writer.drain()
            except (ConnectionError, RuntimeError):
                await self.close(graceful=False)

    async def close(self, graceful: bool = True) -> None:
        if self.closed:
            return
        self.closed = True
        self.server.connections.discard(self)
        try:
            if graceful:
                self.writer.write(ws_frame((1001).to_bytes(2, "big"), opcode=8))
            self.writer.close()
        except (ConnectionError, RuntimeError):
            pass


class CodexServer:
    """All state lives on the asyncio loop thread; ctl calls hop onto it."""

    def __init__(self, fx: Fixture) -> None:
        self.fx = fx
        self.project = fx.dir / "project"
        self.other = fx.dir / "other"
        self.loop = asyncio.new_event_loop()
        self.connections: set[Connection] = set()
        self.threads: dict[str, dict[str, Any]] = {}
        self.turn_tasks: dict[str, asyncio.Task[Any]] = {}
        self.approvals: dict[int, tuple[str, asyncio.Future[str], dict[str, Any]]] = {}
        self.steers: dict[str, list[str]] = {}
        self.refuse = False
        self.healthy = True
        self.failing: dict[str, int] = {}
        self.counters = {"turn": 1, "item": 1, "request": 100}
        self.server: asyncio.base_events.Server | None = None

    # -- state ---------------------------------------------------------------

    def new_id(self, prefix: str, kind: str) -> str:
        value = self.counters[kind]
        self.counters[kind] += 1
        return f"{prefix}-{value}"

    def thread(self, thread_id: str, name: str, cwd: Path, turns: list[dict[str, Any]] | None = None) -> dict[str, Any]:
        stamp = now_ms()
        return {"id": thread_id, "cwd": str(cwd), "name": name, "createdAt": stamp, "updatedAt": stamp,
                "status": {"type": "idle"}, "turns": turns or []}

    def seed(self) -> None:
        self.threads.clear()
        self.approvals.clear()
        self.steers.clear()
        self.counters = {"turn": 1, "item": 1, "request": 100}
        turn = {"id": "history-turn-1", "status": "completed", "startedAt": now_ms() - 1000,
                "completedAt": now_ms() - 500, "items": [
                    {"id": "history-user-1", "type": "userMessage",
                     "content": [{"type": "text", "text": "Show the fixture history."}]},
                    {"id": "history-agent-1", "type": "agentMessage",
                     "text": "This is a retained synthetic Codex session."}]}
        self.threads["thread-history"] = self.thread("thread-history", "Synthetic Codex history", self.project, [turn])
        self.threads["thread-other"] = self.thread("thread-other", "Another project's thread", self.other)
        for folder in (self.project, self.other):
            folder.mkdir(parents=True, exist_ok=True)
        (self.project / "README.md").write_text(
            "# Synthetic Codex project\n\nThis fixture is offline and read-only.\n", encoding="utf-8")
        (self.other / "README.md").write_text("# Another project\n", encoding="utf-8")

    def publish_state(self) -> None:
        self.fx.set_state(connections=len(self.connections), threads=sorted(self.threads),
                          refuse_connections=self.refuse, healthy=self.healthy,
                          failing=sorted(self.failing), pending_approvals=len(self.approvals))

    def log(self, method: str, status: int) -> None:
        # Metadata only: never params, prompt text or the Authorization value.
        self.fx.log.write(method=method, status=status, transport="ws")

    # -- io ------------------------------------------------------------------

    async def broadcast(self, payload: dict[str, Any]) -> None:
        for conn in list(self.connections):
            if conn.initialized:
                await conn.send(payload)

    async def notify(self, method: str, params: dict[str, Any], request_id: Any = None) -> None:
        payload: dict[str, Any] = {"method": method, "params": params}
        if request_id is not None:
            payload["id"] = request_id
        await self.broadcast(payload)

    async def http_answer(self, writer: asyncio.StreamWriter, status: int, reason: str, body: bytes = b"") -> None:
        writer.write(f"HTTP/1.1 {status} {reason}\r\nContent-Type: text/plain\r\nContent-Length: {len(body)}\r\n"
                     "Connection: close\r\n\r\n".encode("latin1") + body)
        try:
            await writer.drain()
        except ConnectionError:
            pass
        writer.close()

    async def upgrade(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> bool:
        try:
            raw = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 5)
        except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError, ConnectionError):
            writer.close()
            return False
        lines = raw.decode("latin1").split("\r\n")
        request = lines[0].split(" ")
        headers: dict[str, str] = {}
        for line in lines[1:]:
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.lower().strip()] = value.strip()
        method = request[0] if request else "?"
        path = request[1].split("?", 1)[0] if len(request) > 1 else "/"
        if headers.get("upgrade", "").lower() != "websocket" and path in ("/readyz", "/healthz"):
            status = 200 if self.healthy else 503
            self.fx.log.write(method=method, path=path, status=status, transport="http")
            await self.http_answer(writer, status, "OK" if status == 200 else "Service Unavailable",
                                   b"ok" if status == 200 else b"unhealthy")
            return False
        if (len(request) != 3 or method != "GET"
                or headers.get("authorization") != f"Bearer {TOKEN}"
                or headers.get("upgrade", "").lower() != "websocket"
                or "upgrade" not in headers.get("connection", "").lower()
                or "sec-websocket-key" not in headers):
            self.log("websocket/upgrade", 401)
            await self.http_answer(writer, 401, "Unauthorized")
            return False
        accept = base64.b64encode(hashlib.sha1((headers["sec-websocket-key"] + GUID).encode()).digest()).decode()
        writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                      f"Sec-WebSocket-Accept: {accept}\r\n\r\n").encode("latin1"))
        await writer.drain()
        self.log("websocket/upgrade", 101)
        return True

    async def client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        if self.refuse:
            self.log("websocket/refused", 0)
            writer.transport.abort()
            return
        if not await self.upgrade(reader, writer):
            return
        conn = Connection(self, reader, writer)
        self.connections.add(conn)
        self.publish_state()
        try:
            while not conn.closed:
                opcode, payload = await read_frame(reader)
                if opcode == 8:
                    break
                if opcode == 9:
                    async with conn.lock:
                        writer.write(ws_frame(payload, opcode=10))
                        await writer.drain()
                    continue
                if opcode != 1:
                    continue
                try:
                    message = json.loads(payload.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    continue
                if not isinstance(message, dict):
                    continue
                rid = message.get("id")
                if "method" not in message and isinstance(rid, int) and rid in self.approvals:
                    result = message.get("result")
                    decision = result.get("decision") if isinstance(result, dict) else None
                    future = self.approvals[rid][1]
                    self.log("approval/reply", 200)
                    if isinstance(decision, str) and not future.done():
                        future.set_result(decision)
                    continue
                await self.handle(conn, message)
        except (asyncio.IncompleteReadError, ConnectionError, ValueError):
            pass
        finally:
            await conn.close(graceful=False)
            self.publish_state()

    # -- rpc -----------------------------------------------------------------

    async def reply(self, conn: Connection, rid: Any, result: dict[str, Any]) -> None:
        await conn.send({"id": rid, "result": result})

    async def fail(self, conn: Connection, rid: Any, code: int, message: str) -> None:
        await conn.send({"id": rid, "error": {"code": code, "message": message}})

    async def handle(self, conn: Connection, request: dict[str, Any]) -> None:
        method = request.get("method")
        rid = request.get("id")
        params = request.get("params") if isinstance(request.get("params"), dict) else {}
        if not isinstance(method, str):
            return
        if rid is None:
            self.log(method, 200)
            return
        if method in self.failing:
            self.log(method, 500)
            await self.fail(conn, rid, self.failing[method], "Synthetic failure")
            return
        if method != "initialize" and not conn.initialized:
            self.log(method, 400)
            await self.fail(conn, rid, -32002, "Not initialized")
            return
        status = 200
        try:
            status = await self.dispatch(conn, rid, method, params)
        finally:
            self.log(method, status)

    async def dispatch(self, conn: Connection, rid: Any, method: str, params: dict[str, Any]) -> int:
        if method == "initialize":
            if not self.healthy:
                await self.fail(conn, rid, -32603, "Synthetic server is unhealthy")
                return 503
            conn.initialized = True
            await self.reply(conn, rid, {"userAgent": USER_AGENT,
                                         "serverInfo": {"name": "synthetic-codex", "version": "0.153.4"}})
            return 200
        if method == "thread/list":
            cwd = params.get("cwd")
            rows = [clone(t) for t in sorted(self.threads.values(), key=lambda t: -t["updatedAt"]) if t["cwd"] == cwd]
            for row in rows:
                row["turns"] = []
            await self.reply(conn, rid, {"data": rows, "nextCursor": None})
            return 200
        if method == "thread/start":
            cwd = params.get("cwd")
            thread_id = f"thread-journey-{secrets.token_hex(4)}"
            created = self.thread(thread_id, "New synthetic Codex thread",
                                  Path(cwd) if isinstance(cwd, str) and cwd else self.project)
            self.threads[thread_id] = created
            await self.reply(conn, rid, {"thread": clone(created)})
            await self.notify("thread/started", {"thread": clone(created)})
            self.publish_state()
            return 200
        if method in ("thread/read", "thread/resume"):
            thread = self.threads.get(params.get("threadId"))
            if thread is None:
                await self.fail(conn, rid, -32004, "Synthetic thread not found")
                return 404
            value = clone(thread)
            if method == "thread/read" and params.get("includeTurns") is False:
                value["turns"] = []
            await self.reply(conn, rid, {"thread": value})
            if method == "thread/resume":
                for request_id, (thread_id, future, params_) in list(self.approvals.items()):
                    if thread_id == thread["id"] and not future.done():
                        await conn.send({"id": request_id, "method": "item/commandExecution/requestApproval",
                                         "params": params_})
            return 200
        if method == "thread/unsubscribe":
            await self.reply(conn, rid, {})
            return 200
        if method == "thread/delete":
            self.threads.pop(params.get("threadId"), None)
            await self.reply(conn, rid, {})
            self.publish_state()
            return 200
        if method == "thread/name/set":
            thread = self.threads.get(params.get("threadId"))
            if thread is None:
                await self.fail(conn, rid, -32004, "Synthetic thread not found")
                return 404
            name = params.get("name")
            if isinstance(name, str):
                thread["name"] = name
            thread["updatedAt"] = now_ms()
            await self.reply(conn, rid, {})
            return 200
        if method == "model/list":
            await self.reply(conn, rid, {"data": [{"model": "fixture/mock-model", "isDefault": True, "hidden": False}],
                                         "nextCursor": None})
            return 200
        if method in ("fs/list", "file/list"):
            await self.reply(conn, rid, {"data": [{"path": "README.md", "type": "file"}]})
            return 200
        if method in ("fs/read", "file/read"):
            await self.reply(conn, rid, {"path": "README.md",
                                         "content": (self.project / "README.md").read_text(encoding="utf-8")})
            return 200
        if method == "turn/start":
            return await self.turn_start(conn, rid, params)
        if method == "turn/steer":
            return await self.turn_steer(conn, rid, params)
        if method == "turn/interrupt":
            return await self.turn_interrupt(conn, rid, params)
        await self.fail(conn, rid, -32601, "Synthetic method unavailable")
        return 404

    @staticmethod
    def input_text(params: dict[str, Any]) -> str:
        inputs = params.get("input")
        if isinstance(inputs, list):
            texts = [i.get("text") for i in inputs if isinstance(i, dict) and isinstance(i.get("text"), str)]
            return "\n".join(texts)
        return ""

    def active_turn(self, thread: dict[str, Any]) -> dict[str, Any] | None:
        active = [t for t in thread["turns"] if t.get("status") == "inProgress"]
        return active[-1] if active else None

    async def turn_start(self, conn: Connection, rid: Any, params: dict[str, Any]) -> int:
        thread = self.threads.get(params.get("threadId"))
        if thread is None:
            await self.fail(conn, rid, -32004, "Synthetic thread not found")
            return 404
        if self.active_turn(thread) is not None:
            await self.fail(conn, rid, -32600, "A turn is already in progress")
            return 409
        text = self.input_text(params)
        turn_id = self.new_id("turn", "turn")
        user_id = self.new_id("user", "item")
        turn = {"id": turn_id, "status": "inProgress", "startedAt": now_ms(), "completedAt": None,
                "items": [{"id": user_id, "type": "userMessage", "content": [{"type": "text", "text": text}]}]}
        thread["turns"].append(turn)
        thread["status"] = {"type": "active"}
        thread["updatedAt"] = now_ms()
        await self.reply(conn, rid, {"turn": clone(turn)})
        task = self.loop.create_task(self.scripted_turn(thread["id"], turn_id, text.lower()))
        self.turn_tasks[turn_id] = task
        task.add_done_callback(lambda _t, key=turn_id: self.turn_tasks.pop(key, None))
        return 200

    async def turn_steer(self, conn: Connection, rid: Any, params: dict[str, Any]) -> int:
        # Codex 0.153.4 turn/steer [U: shape from lib/codex/gateway.dart _steer]:
        # input only, precondition expectedTurnId == the active turn.
        thread = self.threads.get(params.get("threadId"))
        if thread is None:
            await self.fail(conn, rid, -32004, "Synthetic thread not found")
            return 404
        active = self.active_turn(thread)
        if active is None:
            await self.fail(conn, rid, -32600, "No active turn to steer")
            return 409
        if params.get("expectedTurnId") != active["id"]:
            await self.fail(conn, rid, -32600, "expectedTurnId does not match the active turn")
            return 409
        text = self.input_text(params)
        if not text:
            await self.fail(conn, rid, -32602, "input is required")
            return 400
        self.steers.setdefault(active["id"], []).append(text)
        await self.reply(conn, rid, {"turnId": active["id"]})
        return 200

    async def turn_interrupt(self, conn: Connection, rid: Any, params: dict[str, Any]) -> int:
        thread = self.threads.get(params.get("threadId"))
        turn_id = params.get("turnId")
        turn = next((t for t in thread["turns"] if t.get("id") == turn_id), None) if thread else None
        if thread is None or turn is None or turn.get("status") != "inProgress":
            await self.fail(conn, rid, -32005, "Synthetic turn is not in progress")
            return 409
        task = self.turn_tasks.pop(turn_id, None)
        if task is not None:
            task.cancel()
        for request_id, (thread_id, future, _p) in list(self.approvals.items()):
            if thread_id == thread["id"] and not future.done():
                future.cancel()
        await self.reply(conn, rid, {})
        await self.finish(thread, turn, "interrupted")
        return 200

    async def finish(self, thread: dict[str, Any], turn: dict[str, Any], status: str) -> None:
        if turn["status"] != "inProgress":
            return
        turn["status"] = status
        turn["completedAt"] = now_ms()
        thread["updatedAt"] = turn["completedAt"]
        thread["status"] = {"type": "idle"}
        self.steers.pop(turn["id"], None)
        await self.notify("turn/completed", {"threadId": thread["id"], "turn": clone(turn)})

    async def agent_message(self, thread_id: str, turn: dict[str, Any], pieces: list[str], pause: float) -> None:
        item_id = self.new_id("agent", "item")
        agent = {"id": item_id, "type": "agentMessage", "text": ""}
        turn["items"].append(agent)
        await self.notify("item/started", {"threadId": thread_id, "turnId": turn["id"], "item": clone(agent)})
        for piece in pieces:
            agent["text"] += piece
            await self.notify("item/agentMessage/delta",
                              {"threadId": thread_id, "turnId": turn["id"], "itemId": item_id, "delta": piece})
            await asyncio.sleep(pause)
        await self.notify("item/completed", {"threadId": thread_id, "turnId": turn["id"], "item": clone(agent)})

    async def apply_steers(self, thread_id: str, turn: dict[str, Any]) -> None:
        while self.steers.get(turn["id"]):
            text = self.steers[turn["id"]].pop(0)
            user = {"id": self.new_id("user", "item"), "type": "userMessage",
                    "content": [{"type": "text", "text": text}]}
            turn["items"].append(user)
            await self.notify("item/started", {"threadId": thread_id, "turnId": turn["id"], "item": clone(user)})
            await self.notify("item/completed", {"threadId": thread_id, "turnId": turn["id"], "item": clone(user)})
            await self.agent_message(thread_id, turn, ["Steer ", "received; ", "adjusting the synthetic turn."], 0.05)

    async def scripted_turn(self, thread_id: str, turn_id: str, text: str) -> None:
        thread = self.threads[thread_id]
        turn = next(t for t in thread["turns"] if t["id"] == turn_id)
        try:
            await self.notify("turn/started", {"threadId": thread_id, "turn": clone(turn)})
            if "approval" in text or "approve" in text:
                command = {"id": self.new_id("command", "item"), "type": "commandExecution", "status": "inProgress",
                           "command": "printf synthetic-codex", "aggregatedOutput": ""}
                turn["items"].append(command)
                await self.notify("item/started", {"threadId": thread_id, "turnId": turn_id, "item": clone(command)})
                request_id = self.counters["request"]
                self.counters["request"] += 1
                future: asyncio.Future[str] = self.loop.create_future()
                ask = {"threadId": thread_id, "turnId": turn_id, "itemId": command["id"],
                       "command": command["command"], "reason": "Synthetic command approval requested."}
                self.approvals[request_id] = (thread_id, future, ask)
                self.publish_state()
                await self.notify("item/commandExecution/requestApproval", ask, request_id)
                try:
                    decision = await asyncio.wait_for(future, 120)
                except (asyncio.TimeoutError, asyncio.CancelledError):
                    decision = "cancel"
                finally:
                    self.approvals.pop(request_id, None)
                    self.publish_state()
                if turn["status"] != "inProgress":
                    return
                await self.notify("serverRequest/resolved", {"threadId": thread_id, "requestId": request_id})
                accepted = decision in ("accept", "acceptForSession")
                command["status"] = "completed" if accepted else "failed"
                command["exitCode"] = 0 if accepted else 1
                command["aggregatedOutput"] = "Synthetic command accepted." if accepted else "Synthetic command declined."
                await self.notify("item/completed", {"threadId": thread_id, "turnId": turn_id, "item": clone(command)})
                if not accepted:
                    await self.finish(thread, turn, "failed")
                    return
            if "slow" in text:
                pieces, pause = ["Slow ", "synthetic ", "Codex ", "response ", "for ", "steering."], 0.5
            else:
                pieces, pause = ["Synthetic ", "Codex ", "response for the local journey fixture."], 0.08
            await self.agent_message(thread_id, turn, pieces, pause)
            await self.apply_steers(thread_id, turn)
            if "long-running" in text or "long running" in text:
                while turn["status"] == "inProgress":
                    await self.apply_steers(thread_id, turn)
                    await asyncio.sleep(0.1)
                return
            await self.finish(thread, turn, "completed")
        except asyncio.CancelledError:
            pass

    # -- lifecycle / control (called from ctl threads) -------------------------

    def call(self, coro: Any, timeout: float = 10.0) -> Any:
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(timeout)

    async def drop_all(self) -> int:
        conns = list(self.connections)
        for conn in conns:
            await conn.close(graceful=False)
            try:
                conn.writer.transport.abort()
            except (AttributeError, RuntimeError):
                pass
        self.publish_state()
        return len(conns)

    async def reset(self) -> None:
        for task in list(self.turn_tasks.values()):
            task.cancel()
        self.turn_tasks.clear()
        await self.drop_all()
        self.refuse = False
        self.healthy = True
        self.failing.clear()
        self.seed()
        self.publish_state()

    async def start(self) -> None:
        self.server = await asyncio.start_server(self.client, LOOPBACK, self.fx.ports["ws"], limit=MAX_FRAME + 8192)

    def run(self, started: threading.Event) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self.start())
        started.set()
        self.loop.run_forever()

    def stop(self) -> None:
        async def _stop() -> None:
            await self.drop_all()
            if self.server is not None:
                self.server.close()
        try:
            self.call(_stop(), timeout=5)
        except Exception:  # noqa: BLE001 - shutting down anyway
            pass
        self.loop.call_soon_threadsafe(self.loop.stop)


def setup(fx: Fixture) -> dict[str, Any]:
    SCRUB.add(TOKEN)
    server = CodexServer(fx)
    server.seed()
    started = threading.Event()
    threading.Thread(target=server.run, args=(started,), name="FX-CODEX:ws", daemon=True).start()
    if not started.wait(10):
        raise RuntimeError("websocket listener did not start")
    server.publish_state()
    fx.at_exit(server.stop)

    @fx.control("drop-socket")
    def _drop(body: dict[str, Any]) -> dict[str, Any]:
        return {"closed": server.call(server.drop_all())}

    @fx.control("refuse-connections")
    def _refuse(body: dict[str, Any]) -> dict[str, Any]:
        on = body.get("on", True)
        if not isinstance(on, bool):
            raise ValueError("on must be a boolean")

        async def _set() -> None:
            server.refuse = on
            server.publish_state()
        server.call(_set())
        return {"refuse_connections": on}

    @fx.control("health")
    def _health(body: dict[str, Any]) -> dict[str, Any]:
        ok = body.get("ok", True)
        if not isinstance(ok, bool):
            raise ValueError("ok must be a boolean")

        async def _set() -> None:
            server.healthy = ok
            server.publish_state()
        server.call(_set())
        return {"healthy": ok}

    @fx.control("fail-method")
    def _fail(body: dict[str, Any]) -> dict[str, Any]:
        method = body.get("method")
        if not isinstance(method, str) or not method:
            raise ValueError("method is required")
        on = body.get("on", True)
        code = body.get("code", -32603)
        if not isinstance(code, int):
            raise ValueError("code must be an integer")

        async def _set() -> None:
            if on:
                server.failing[method] = code
            else:
                server.failing.pop(method, None)
            server.publish_state()
        server.call(_set())
        return {"failing": method if on else None}

    fx.on_reset_hook(lambda: server.call(server.reset()))

    return {
        "url": f"ws://{LOOPBACK}:{fx.ports['ws']}",
        "emulator_url": f"ws://10.0.2.2:{fx.ports['ws']}",
        "project": str(server.project),
        "other_project": str(server.other),
        "auth": "bearer",
        "token_hint": "the public test token from tool/qa/codex_fixture/README.md",
    }


if __name__ == "__main__":
    sys.exit(run_fixture(FIXTURE_ID, setup))
