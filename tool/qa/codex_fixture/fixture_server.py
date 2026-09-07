#!/usr/bin/env python3
"""Small authenticated Codex app-server fixture for local journey checks.

This intentionally implements only the JSON-RPC methods used by the mobile
Codex gateway.  It does not invoke a model, provider, account, or network.
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import os
import secrets
import time
from pathlib import Path
from typing import Any


ROOT = Path(os.environ.get("OCMN_CODEX_FIXTURE_DIR", "/tmp/ocmn-codex-fixture"))
PROJECT = ROOT / "project"
TOKEN = "fixture-token"
PORT = int(os.environ.get("OCMN_CODEX_FIXTURE_PORT", "4126"))
FORCE_DISCONNECT = ROOT / "force_disconnect"
LOG = ROOT / "request_log.jsonl"
MAX_FRAME = 4 * 1024 * 1024

THREADS: dict[str, dict[str, Any]] = {}
CONNECTIONS: set["Connection"] = set()
NEXT_TURN = 1
NEXT_ITEM = 1
NEXT_REQUEST = 100


def now_ms() -> int:
    return int(time.time() * 1000)


def log(method: str, status: int) -> None:
    # Deliberately record only protocol metadata. No authorization, params,
    # user text, provider data, or response bodies reach this file.
    with LOG.open("a", encoding="utf-8") as handle:
        json.dump({"method": method, "status": status}, handle)
        handle.write("\n")


def clone(value: Any) -> Any:
    return json.loads(json.dumps(value))


def thread(thread_id: str, name: str, turns: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    stamp = now_ms()
    return {
        "id": thread_id,
        "cwd": str(PROJECT),
        "name": name,
        "createdAt": stamp,
        "updatedAt": stamp,
        "status": {"type": "idle"},
        "turns": turns or [],
    }


def seed() -> None:
    user = {
        "id": "history-user-1",
        "type": "userMessage",
        "content": [{"type": "text", "text": "Show the fixture history."}],
    }
    agent = {
        "id": "history-agent-1",
        "type": "agentMessage",
        "text": "This is a retained synthetic Codex session.",
    }
    turn = {
        "id": "history-turn-1",
        "status": "completed",
        "startedAt": now_ms() - 1000,
        "completedAt": now_ms() - 500,
        "items": [user, agent],
    }
    THREADS["thread-history"] = thread(
        "thread-history", "Synthetic Codex history", [turn]
    )
    (PROJECT / "README.md").write_text(
        "# Synthetic Codex project\n\nThis fixture is offline and read-only.\n",
        encoding="utf-8",
    )


def new_id(prefix: str, counter_name: str) -> str:
    global NEXT_TURN, NEXT_ITEM, NEXT_REQUEST
    if counter_name == "turn":
        value = NEXT_TURN
        NEXT_TURN += 1
    elif counter_name == "item":
        value = NEXT_ITEM
        NEXT_ITEM += 1
    else:
        value = NEXT_REQUEST
        NEXT_REQUEST += 1
    return f"{prefix}-{value}"


def error(code: int, message: str) -> dict[str, Any]:
    return {"code": code, "message": message}


class Connection:
    def __init__(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        self.reader = reader
        self.writer = writer
        self.write_lock = asyncio.Lock()
        self.closed = False
        self.approvals: dict[int, asyncio.Future[str]] = {}
        self.turn_tasks: set[asyncio.Task[Any]] = set()

    async def send(self, payload: dict[str, Any]) -> None:
        if self.closed:
            return
        raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        if len(raw) > MAX_FRAME:
            await self.close()
            return
        async with self.write_lock:
            if not self.closed:
                self.writer.write(ws_frame(raw))
                await self.writer.drain()

    async def close(self) -> None:
        if self.closed:
            return
        self.closed = True
        for task in list(self.turn_tasks):
            task.cancel()
        for future in self.approvals.values():
            if not future.done():
                future.cancel()
        self.writer.close()
        try:
            await self.writer.wait_closed()
        except (ConnectionError, asyncio.IncompleteReadError):
            pass


def ws_frame(payload: bytes, opcode: int = 1) -> bytes:
    first = 0x80 | opcode
    length = len(payload)
    if length < 126:
        return bytes([first, length]) + payload
    if length <= 0xFFFF:
        return bytes([first, 126]) + length.to_bytes(2, "big") + payload
    return bytes([first, 127]) + length.to_bytes(8, "big") + payload


async def read_frame(reader: asyncio.StreamReader) -> tuple[int, bytes] | None:
    header = await reader.readexactly(2)
    first, second = header
    opcode = first & 0x0F
    length = second & 0x7F
    masked = bool(second & 0x80)
    if length == 126:
        length = int.from_bytes(await reader.readexactly(2), "big")
    elif length == 127:
        length = int.from_bytes(await reader.readexactly(8), "big")
    if length > MAX_FRAME:
        raise ValueError("frame too large")
    mask = await reader.readexactly(4) if masked else b""
    payload = bytearray(await reader.readexactly(length))
    if masked:
        for index in range(length):
            payload[index] ^= mask[index % 4]
    return opcode, bytes(payload)


async def upgrade(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> bool:
    try:
        raw = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 5)
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError):
        writer.close()
        await writer.wait_closed()
        return False
    lines = raw.decode("latin1").split("\r\n")
    request = lines[0].split(" ")
    headers: dict[str, str] = {}
    for line in lines[1:]:
        if ":" in line:
            key, value = line.split(":", 1)
            headers[key.lower().strip()] = value.strip()
    method = request[0] if request else "?"
    if (
        len(request) != 3
        or method != "GET"
        or headers.get("authorization") != f"Bearer {TOKEN}"
        or headers.get("upgrade", "").lower() != "websocket"
        or "upgrade" not in headers.get("connection", "").lower()
        or "sec-websocket-key" not in headers
    ):
        log("websocket/upgrade", 401)
        writer.write(b"HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        await writer.drain()
        writer.close()
        await writer.wait_closed()
        return False
    accept = base64.b64encode(
        hashlib.sha1(
            (headers["sec-websocket-key"] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()
        ).digest()
    ).decode()
    response = (
        "HTTP/1.1 101 Switching Protocols\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
    ).encode("latin1")
    writer.write(response)
    await writer.drain()
    log("websocket/upgrade", 101)
    return True


async def event(connection: Connection, method: str, params: dict[str, Any], request_id: Any = None) -> None:
    payload: dict[str, Any] = {"method": method, "params": params}
    if request_id is not None:
        payload["id"] = request_id
    await connection.send(payload)


async def response(connection: Connection, request_id: Any, result: dict[str, Any]) -> None:
    await connection.send({"id": request_id, "result": result})


async def failure(connection: Connection, request_id: Any, code: int, message: str) -> None:
    await connection.send({"id": request_id, "error": error(code, message)})


async def scripted_turn(connection: Connection, thread_id: str, turn_id: str, text: str) -> None:
    current = THREADS[thread_id]
    turn = next(turn for turn in current["turns"] if turn["id"] == turn_id)
    await event(connection, "turn/started", {"threadId": thread_id, "turn": clone(turn)})
    item_id = new_id("agent", "item")
    command_mode = "approval" in text.lower() or "approve" in text.lower()
    long_mode = "long-running" in text.lower() or "long running" in text.lower()
    if command_mode:
        command_id = new_id("command", "item")
        command = {
            "id": command_id,
            "type": "commandExecution",
            "status": "inProgress",
            "command": "printf synthetic-codex",
            "aggregatedOutput": "",
        }
        turn["items"].append(command)
        await event(connection, "item/started", {"threadId": thread_id, "turnId": turn_id, "item": clone(command)})
        request_id = NEXT_REQUEST
        globals()["NEXT_REQUEST"] += 1
        approval: asyncio.Future[str] = asyncio.get_running_loop().create_future()
        connection.approvals[request_id] = approval
        await event(
            connection,
            "item/commandExecution/requestApproval",
            {
                "threadId": thread_id,
                "turnId": turn_id,
                "itemId": command_id,
                "command": command["command"],
                "reason": "Synthetic command approval requested.",
            },
            request_id,
        )
        try:
            decision = await asyncio.wait_for(approval, 120)
        except (asyncio.TimeoutError, asyncio.CancelledError):
            decision = "cancel"
        finally:
            connection.approvals.pop(request_id, None)
        command["status"] = "completed" if decision == "accept" else "failed"
        command["exitCode"] = 0 if decision == "accept" else 1
        command["aggregatedOutput"] = "Synthetic command accepted." if decision == "accept" else "Synthetic command declined."
        await event(connection, "item/completed", {"threadId": thread_id, "turnId": turn_id, "item": clone(command)})
        if decision != "accept":
            await finish_turn(connection, current, turn, "failed")
            return
    agent = {"id": item_id, "type": "agentMessage", "text": ""}
    turn["items"].append(agent)
    await event(connection, "item/started", {"threadId": thread_id, "turnId": turn_id, "item": clone(agent)})
    pieces = [
        "Synthetic ",
        "Codex ",
        "response for the local journey fixture.",
    ]
    for piece in pieces:
        if connection.closed:
            return
        agent["text"] += piece
        await event(connection, "item/agentMessage/delta", {"threadId": thread_id, "turnId": turn_id, "itemId": item_id, "delta": piece})
        await asyncio.sleep(0.08)
    await event(connection, "item/completed", {"threadId": thread_id, "turnId": turn_id, "item": clone(agent)})
    if long_mode:
        # Keep the turn inProgress until thread/read + turn/interrupt arrive.
        while not connection.closed and turn["status"] == "inProgress":
            await asyncio.sleep(0.2)
        return
    await finish_turn(connection, current, turn, "completed")


async def finish_turn(connection: Connection, current: dict[str, Any], turn: dict[str, Any], status: str) -> None:
    turn["status"] = status
    turn["completedAt"] = now_ms()
    current["updatedAt"] = turn["completedAt"]
    current["status"] = {"type": "idle"}
    await event(connection, "turn/completed", {"threadId": current["id"], "turn": clone(turn)})


async def handle_request(connection: Connection, request: dict[str, Any]) -> None:
    method = request.get("method")
    request_id = request.get("id")
    params = request.get("params")
    if not isinstance(method, str):
        return
    log(method, 200)
    if request_id is None:
        # initialized is the only notification the client sends in this slice.
        return
    if not isinstance(params, dict):
        params = {}
    if method == "initialize":
        await response(connection, request_id, {"userAgent": "synthetic-codex/0.153.4", "serverInfo": {"name": "synthetic-codex", "version": "0.153.4"}})
    elif method == "thread/list":
        cwd = params.get("cwd")
        rows = [clone(value) for value in THREADS.values() if cwd == value["cwd"]]
        await response(connection, request_id, {"data": rows, "nextCursor": None})
    elif method == "thread/start":
        thread_id = f"thread-journey-{secrets.token_hex(4)}"
        created = thread(thread_id, "New synthetic Codex thread")
        THREADS[thread_id] = created
        await response(connection, request_id, {"thread": clone(created)})
        await event(connection, "thread/started", {"thread": clone(created)})
    elif method in {"thread/read", "thread/resume"}:
        thread_id = params.get("threadId")
        value = THREADS.get(thread_id)
        if not isinstance(thread_id, str) or value is None:
            await failure(connection, request_id, -32004, "Synthetic thread not found")
        else:
            await response(connection, request_id, {"thread": clone(value)})
    elif method == "thread/unsubscribe":
        await response(connection, request_id, {})
    elif method == "thread/delete":
        thread_id = params.get("threadId")
        THREADS.pop(thread_id, None)
        await response(connection, request_id, {})
    elif method == "thread/name/set":
        value = THREADS.get(params.get("threadId"))
        if value is None:
            await failure(connection, request_id, -32004, "Synthetic thread not found")
        else:
            value["name"] = params.get("name", value["name"])
            value["updatedAt"] = now_ms()
            await response(connection, request_id, {})
    elif method == "model/list":
        await response(connection, request_id, {"data": [{"model": "fixture/mock-model", "isDefault": True, "hidden": False}], "nextCursor": None})
    elif method in {"fs/list", "file/list"}:
        # Read-only synthetic filesystem surface for callers that probe it.
        await response(connection, request_id, {"data": [{"path": "README.md", "type": "file"}]})
    elif method in {"fs/read", "file/read"}:
        await response(connection, request_id, {"path": "README.md", "content": (PROJECT / "README.md").read_text(encoding="utf-8")})
    elif method == "turn/start":
        thread_id = params.get("threadId")
        value = THREADS.get(thread_id)
        if value is None:
            await failure(connection, request_id, -32004, "Synthetic thread not found")
            return
        text = ""
        inputs = params.get("input")
        if isinstance(inputs, list) and inputs and isinstance(inputs[0], dict):
            text = inputs[0].get("text", "") if isinstance(inputs[0].get("text", ""), str) else ""
        turn_id = new_id("turn", "turn")
        user_id = new_id("user", "item")
        turn = {"id": turn_id, "status": "inProgress", "startedAt": now_ms(), "completedAt": None, "items": [{"id": user_id, "type": "userMessage", "content": [{"type": "text", "text": text}]}]}
        value["turns"].append(turn)
        value["status"] = {"type": "active"}
        value["updatedAt"] = now_ms()
        await response(connection, request_id, {"turn": clone(turn)})
        task = asyncio.create_task(scripted_turn(connection, thread_id, turn_id, text))
        connection.turn_tasks.add(task)
        task.add_done_callback(connection.turn_tasks.discard)
    elif method == "turn/interrupt":
        thread_id = params.get("threadId")
        turn_id = params.get("turnId")
        value = THREADS.get(thread_id)
        turn = next((item for item in value.get("turns", []) if item.get("id") == turn_id), None) if value else None
        if turn is None or turn.get("status") != "inProgress":
            await failure(connection, request_id, -32005, "Synthetic turn is not in progress")
        else:
            turn["status"] = "interrupted"
            turn["completedAt"] = now_ms()
            value["updatedAt"] = turn["completedAt"]
            value["status"] = {"type": "idle"}
            await response(connection, request_id, {})
            await event(connection, "turn/completed", {"threadId": thread_id, "turn": clone(turn)})
    else:
        await failure(connection, request_id, -32601, "Synthetic method unavailable")


async def disconnect_watcher(connection: Connection) -> None:
    while not connection.closed:
        if FORCE_DISCONNECT.exists():
            log("websocket/forced_disconnect", 499)
            await connection.close()
            return
        await asyncio.sleep(0.2)


async def client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    if not await upgrade(reader, writer):
        return
    connection = Connection(reader, writer)
    CONNECTIONS.add(connection)
    watcher = asyncio.create_task(disconnect_watcher(connection))
    try:
        while not connection.closed:
            frame = await read_frame(reader)
            if frame is None:
                break
            opcode, payload = frame
            if opcode == 8:
                break
            if opcode == 9:
                async with connection.write_lock:
                    writer.write(ws_frame(payload, opcode=10))
                    await writer.drain()
                continue
            if opcode != 1:
                continue
            try:
                request = json.loads(payload.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue
            if isinstance(request, dict):
                request_id = request.get("id")
                if isinstance(request_id, int) and request_id in connection.approvals:
                    result = request.get("result")
                    decision = result.get("decision") if isinstance(result, dict) else None
                    if isinstance(decision, str):
                        future = connection.approvals[request_id]
                        if not future.done():
                            future.set_result(decision)
                        continue
                await handle_request(connection, request)
    except (asyncio.IncompleteReadError, ConnectionError, ValueError, json.JSONDecodeError):
        pass
    finally:
        watcher.cancel()
        CONNECTIONS.discard(connection)
        await connection.close()


async def main() -> None:
    PROJECT.mkdir(parents=True, exist_ok=True)
    seed()
    server = await asyncio.start_server(client, "127.0.0.1", PORT, limit=MAX_FRAME + 8192)
    log("server/listen", 200)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
