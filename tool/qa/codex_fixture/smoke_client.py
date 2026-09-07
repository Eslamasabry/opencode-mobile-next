#!/usr/bin/env python3
"""Stdlib-only smoke client; prints protocol method/status facts only."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import socket
import struct
import time
from pathlib import Path


ROOT = Path(os.environ.get("OCMN_CODEX_FIXTURE_DIR", "/tmp/ocmn-codex-fixture"))
HOST = "127.0.0.1"
PORT = int(os.environ.get("OCMN_CODEX_FIXTURE_PORT", "4126"))


def frame(payload: bytes) -> bytes:
    mask = os.urandom(4)
    length = len(payload)
    if length < 126:
        head = bytes([0x81, 0x80 | length])
    elif length <= 0xFFFF:
        head = bytes([0x81, 0x80 | 126]) + struct.pack(">H", length)
    else:
        head = bytes([0x81, 0x80 | 127]) + struct.pack(">Q", length)
    return head + mask + bytes(value ^ mask[index % 4] for index, value in enumerate(payload))


def read_frame(sock: socket.socket) -> dict:
    head = sock.recv(2)
    if len(head) != 2:
        raise RuntimeError("socket closed")
    first, second = head
    length = second & 0x7F
    if length == 126:
        length = struct.unpack(">H", sock.recv(2))[0]
    elif length == 127:
        length = struct.unpack(">Q", sock.recv(8))[0]
    data = bytearray()
    while len(data) < length:
        data.extend(sock.recv(length - len(data)))
    if first & 0x0F == 8:
        raise RuntimeError("server closed socket")
    return json.loads(bytes(data).decode("utf-8"))


def connect(token: str = "fixture-token") -> socket.socket:
    sock = socket.create_connection((HOST, PORT), timeout=4)
    key = base64.b64encode(os.urandom(16)).decode()
    sock.sendall(
        (
            f"GET / HTTP/1.1\r\nHost: {HOST}:{PORT}\r\n"
            f"Authorization: Bearer {token}\r\nUpgrade: websocket\r\n"
            "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
            f"Sec-WebSocket-Key: {key}\r\n\r\n"
        ).encode()
    )
    response = b""
    while b"\r\n\r\n" not in response:
        response += sock.recv(4096)
    if not response.startswith(b"HTTP/1.1 101"):
        sock.close()
        raise RuntimeError(f"unexpected websocket status {response.splitlines()[0]!r}")
    return sock


def send(sock: socket.socket, request: dict) -> None:
    sock.sendall(frame(json.dumps(request, separators=(",", ":")).encode()))


def call(sock: socket.socket, request_id: int, method: str, params: dict | None = None) -> dict:
    send(sock, {"id": request_id, "method": method, "params": params or {}})
    while True:
        result = read_frame(sock)
        if result.get("id") == request_id and ("result" in result or "error" in result):
            if "error" in result:
                raise RuntimeError(f"{method} returned an error")
            return result["result"]


def wait_for(sock: socket.socket, method: str, request_id: int | None = None) -> dict:
    while True:
        value = read_frame(sock)
        if value.get("method") == method and (request_id is None or value.get("id") == request_id):
            return value


def main() -> None:
    sock = connect()
    sock.settimeout(4)
    send(sock, {"id": 1, "method": "initialize", "params": {"clientInfo": {"name": "smoke", "version": "1"}}})
    call_result = None
    while call_result is None:
        value = read_frame(sock)
        if value.get("id") == 1:
            call_result = value.get("result")
    send(sock, {"method": "initialized", "params": {}})
    listed = call(sock, 2, "thread/list", {"cwd": str(ROOT / "project"), "limit": 100, "archived": False})
    history_id = listed["data"][0]["id"]
    call(sock, 3, "thread/read", {"threadId": history_id, "includeTurns": True})
    call(sock, 4, "thread/resume", {"threadId": history_id})
    models = call(sock, 5, "model/list", {"limit": 100})
    created = call(sock, 6, "thread/start", {"cwd": str(ROOT / "project"), "approvalPolicy": "on-request", "sandbox": "read-only"})
    thread_id = created["thread"]["id"]
    call(sock, 7, "turn/start", {"threadId": thread_id, "input": [{"type": "text", "text": "normal journey", "text_elements": []}]})
    wait_for(sock, "turn/completed")
    call(sock, 8, "turn/start", {"threadId": thread_id, "input": [{"type": "text", "text": "approval journey", "text_elements": []}]})
    approval = wait_for(sock, "item/commandExecution/requestApproval")
    approval_id = approval["id"]
    send(sock, {"id": approval_id, "result": {"decision": "accept"}})
    wait_for(sock, "turn/completed")
    call(sock, 9, "turn/start", {"threadId": thread_id, "input": [{"type": "text", "text": "long-running journey", "text_elements": []}]})
    call(sock, 10, "thread/read", {"threadId": thread_id, "includeTurns": True})
    call(sock, 11, "turn/interrupt", {"threadId": thread_id, "turnId": "turn-3"})
    wait_for(sock, "turn/completed")
    sock.close()

    bad = socket.create_connection((HOST, PORT), timeout=4)
    bad.sendall(f"GET / HTTP/1.1\r\nHost: {HOST}:{PORT}\r\nConnection: close\r\n\r\n".encode())
    rejected = bad.recv(128).startswith(b"HTTP/1.1 401")
    bad.close()

    toggle = ROOT / "force_disconnect"
    toggle.write_text("smoke\n", encoding="utf-8")
    disconnected = False
    try:
        probe = connect()
        probe.settimeout(2)
        time.sleep(0.4)
        try:
            read_frame(probe)
        except (RuntimeError, TimeoutError, socket.timeout):
            disconnected = True
        probe.close()
    finally:
        toggle.unlink(missing_ok=True)

    print(json.dumps({"initialize": "ok", "history": history_id, "model": models["data"][0]["model"], "normal": "completed", "approval": "accepted", "interrupt": "completed", "bad_auth": rejected, "force_disconnect": disconnected}, sort_keys=True))


if __name__ == "__main__":
    main()
