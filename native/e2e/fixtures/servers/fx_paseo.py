#!/usr/bin/env python3
"""FX-PASEO - a Paseo daemon with scripted Claude Code and Pi agents (E2E-30).

Two modes (ready.json "mode"):

real  A real ``paseo`` daemon (FX_PASEO_BIN, else ``paseo`` on PATH) runs as a
      child with HOME=<dir>/home and its daemon home <dir>/paseo-home, set up
      the way docs/qa/pins-2026-10-31/paseoprobe2.sh does it (``daemon config
      set daemon.listen 127.0.0.1:<upstream>``, relay/MCP/web UI off, then
      ``daemon run --home``). PATH starts with <dir>/bin, which holds the fake
      ``claude`` and ``pi`` below. A byte proxy on the ``daemon`` port forwards
      to it so ``drop-socket`` works on a real daemon too. Not run here: no
      real daemon is installed in the dev container [U].
stub  (the fallback) A Python stub of Paseo protocol 1 on the ``daemon`` port,
      covering what E2E-30 needs. Shapes come from the app's client
      (lib/paseo/transport.dart, lib/paseo/gateway.dart and gateway/*.dart)
      and its test daemons (test/paseo_gateway_test.dart FakeDaemon/agentJson,
      test/paseo_project_tools_test.dart, test/paseo_host_update_test.dart,
      test/paseo_provider_import_test.dart).

Either mode writes, before it is ready:
  <dir>/bin/claude   fake Claude Code 2.1.316: ``--version``, ``auth status``,
                     ``-p PROMPT --output-format stream-json`` (system init,
                     assistant tool_use, user tool_result, assistant text,
                     result) and ``--input-format stream-json`` (Agent SDK
                     control protocol: control_request -> control_response,
                     one turn per user line)
  <dir>/bin/pi       fake Pi [U: event names from pi-coding-agent's RPC mode,
                     not from this repo]: ``--version``, ``--mode rpc`` (JSON
                     commands on stdin -> response + agent events), ``--mode
                     json PROMPT``
  <dir>/home/.claude/projects/<slug>/<session>.jsonl   Claude history (two
                     sessions in the project, one in another project)

Stub wire (one WebSocket at /ws, one JSON object per text frame):
  hello -> {"type":"session","message":{"type":"status","payload":
           {"status":"server_info","version":...}}}; ping -> pong.
  Password (FX_PASEO_PASSWORD, optional): Sec-WebSocket-Protocol
  ``paseo.bearer.<pw>``, Authorization Bearer, or hello.auth.password. Wrong or
  missing: hello.rejected {reason, accepts} when the hello claims
  ``hello_rejection``, then close 4401.
  Requests answer {type: <name>_response, payload: {requestId, ...}} except
  create/import (``status`` agent_created / agent_resumed / agent_create_failed)
  and archive (``agent_archived``, broadcast, so other sockets see it too).
  Pushes: agent_update, agent_stream {agentId, event, timestamp, seq, epoch},
  agent_archived, providers_snapshot_update, terminal_stream_exit, and binary
  terminal frames [opcode 1 output | 5 restore, slot, bytes...].

Scripted turns (substring of the prompt, case-insensitive): ``long-running``
stays running until cancel_agent_request; ``slow`` paces deltas 0.5 s;
``fail`` ends turn_failed; anything else: (Claude: reasoning,) a Read tool
call, a streamed reply, turn_completed. A message sent while a turn runs joins
it (steer: a user_message and a short reply in the same turn).

Terminals: real shells on a pty in the project folder: ``ticker`` prints
``tick N`` every 0.5 s, ``shell`` is /bin/sh. Input and resize reach them.

Control (ctl port, POST, JSON):
  drop-socket {}                               close every socket -> {"closed": n}
  set-version {"daemon"?, "latest"?, "fail"?}  update report; fail = error text (stub)
  set-provider {"provider", "status"?, "error"?, "enabled"?}   (stub)
  archive-agent {"agentId"}                    archive "on another device" (stub)
  terminal-emit {"terminalId", "text", "split"?}  bytes to subscribers; split
                                               cuts the first multi-byte char
                                               across two frames (stub)
  terminal-exit {"terminalId"}                 end that shell (stub)
  fail-request {"type", "on", "error"?}        answer that type with rpc_error (stub)
  POST /reset                                  stub: agents, terminals, versions,
                                               providers back to the seed
State: GET /state (agents, terminals, versions, connections).
ready.json: mode, url, emulator_url, project, other_project, home, bin,
  claude_history [paths], daemon_version, password_required, (real) upstream_port.
Env: FX_PASEO_MODE auto|stub|real (default auto), FX_PASEO_BIN, FX_PASEO_PASSWORD.
Flag: --mode auto|stub|real (wins over FX_PASEO_MODE).
"""

from __future__ import annotations

import asyncio
import base64
import fcntl
import hashlib
import json
import os
import pty
import re
import shutil
import signal
import struct
import subprocess
import sys
import termios
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import (LOOPBACK, SCRUB, Fixture, base_parser, free_port, run_fixture,  # noqa: E402
                        stop_pid, wait_for_port)

FIXTURE_ID = "FX-PASEO"
GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
MAX_FRAME = 8 * 1024 * 1024
DAEMON_VERSION = "0.11.2"
CLAUDE_LONG_MODEL = "claude-sonnet-fixture-20261001"

# --------------------------------------------------------------------------- fake agents

FAKE_CLAUDE = r'''#!/usr/bin/env python3
"""Fake Claude Code 2.1.316 for FX-PASEO (test stand-in; no network, no account).

stream-json lines as Claude Code prints them with --output-format stream-json
--verbose: system/init, assistant (tool_use), user (tool_result), assistant
(text), result. With --input-format stream-json it reads the Agent SDK
control protocol on stdin (control_request -> control_response) and runs one
turn per user message.
"""
import json, os, sys, uuid

VERSION = "2.1.316"
args = sys.argv[1:]
if "--version" in args or "-v" in args:
    print(VERSION + " (Claude Code)")
    sys.exit(0)
if args[:2] == ["auth", "status"]:
    print(json.dumps({"loggedIn": True, "authMethod": "fixture", "apiProvider": "firstParty"}))
    sys.exit(0)

def opt(name, default=None):
    for i, a in enumerate(args):
        if a == name and i + 1 < len(args):
            return args[i + 1]
        if a.startswith(name + "="):
            return a.split("=", 1)[1]
    return default

model = opt("--model", "claude-sonnet-fixture-20261001")
session = opt("--session-id") or opt("--resume") or str(uuid.uuid4())
out_format = opt("--output-format", "text")
in_format = opt("--input-format", "text")

def emit(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()

def base(obj):
    obj.setdefault("session_id", session)
    obj.setdefault("uuid", str(uuid.uuid4()))
    return obj

def usage():
    return {"input_tokens": 12, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0, "output_tokens": 20}

def assistant(content, stop):
    return base({"type": "assistant", "parent_tool_use_id": None, "message": {
        "id": "msg_fx_" + uuid.uuid4().hex[:12], "type": "message", "role": "assistant", "model": model,
        "content": content, "stop_reason": stop, "stop_sequence": None, "usage": usage()}})

def turn(prompt):
    emit(base({"type": "system", "subtype": "init", "cwd": os.getcwd(), "session_id": session,
               "tools": ["Bash", "Edit", "Read", "Write"], "mcp_servers": [], "model": model,
               "permissionMode": "default", "slash_commands": [], "apiKeySource": "none",
               "claude_code_version": VERSION, "output_style": "default", "agents": [], "skills": [], "plugins": []}))
    tool_id = "toolu_fx_" + uuid.uuid4().hex[:12]
    path = os.path.join(os.getcwd(), "README.md")
    emit(assistant([{"type": "tool_use", "id": tool_id, "name": "Read", "input": {"file_path": path}}], "tool_use"))
    try:
        with open(path, encoding="utf-8") as handle:
            content = handle.read(2000)
    except OSError:
        content = "(no README.md)"
    emit(base({"type": "user", "parent_tool_use_id": None, "message": {"role": "user", "content": [
        {"tool_use_id": tool_id, "type": "tool_result", "content": content, "is_error": False}]}}))
    answer = "Synthetic Claude reply: I read README.md in this folder."
    emit(assistant([{"type": "text", "text": answer}], "end_turn"))
    emit(base({"type": "result", "subtype": "success", "is_error": False, "duration_ms": 900,
               "duration_api_ms": 800, "num_turns": 2, "result": answer, "stop_reason": "end_turn",
               "total_cost_usd": 0, "usage": usage(), "modelUsage": {}, "permission_denials": []}))
    return answer

if in_format == "stream-json":
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            msg = json.loads(raw)
        except ValueError:
            continue
        if msg.get("type") == "control_request":
            sub = (msg.get("request") or {}).get("subtype")
            body = {}
            if sub == "initialize":
                body = {"commands": [], "models": [{"value": model, "displayName": "Sonnet"}],
                        "account": {"tokenSource": "fixture", "apiProvider": "firstParty"}}
            elif sub == "interrupt":
                body = {"still_queued": []}
            emit({"type": "control_response", "response": {"subtype": "success",
                                                           "request_id": msg.get("request_id"), "response": body}})
        elif msg.get("type") == "user":
            content = (msg.get("message") or {}).get("content")
            turn(content if isinstance(content, str) else "")
    sys.exit(0)

prompt = None
if "-p" in args or "--print" in args:
    rest = [a for a in args if not a.startswith("-")]
    flag_values = {opt(n) for n in ("--model", "--session-id", "--resume", "--output-format", "--input-format")}
    rest = [a for a in rest if a not in flag_values]
    prompt = rest[0] if rest else sys.stdin.read()
if prompt is None:
    print("fake claude: use -p PROMPT or --input-format stream-json", file=sys.stderr)
    sys.exit(2)
if out_format == "stream-json":
    turn(prompt)
else:
    saved = sys.stdout
    sys.stdout = open(os.devnull, "w")
    answer = turn(prompt)
    sys.stdout = saved
    print(json.dumps({"type": "result", "result": answer}) if out_format == "json" else answer)
'''

FAKE_PI = r'''#!/usr/bin/env python3
"""Fake Pi for FX-PASEO (test stand-in; no network, no account).

[U] Event names follow pi-coding-agent's RPC/JSON modes as published upstream
(agent_start, turn_start, message_start/update/end, tool_execution_start/end,
turn_end, agent_end); nothing in this repo pins them. ``--mode rpc`` reads one
JSON command per line ({"type": "prompt", "message": ...}, abort, get_state,
get_available_models) and answers {"type": "response", "command": ...}.
"""
import json, os, sys, time

VERSION = "0.85.1"
args = sys.argv[1:]
if "--version" in args or "-v" in args:
    print(VERSION)
    sys.exit(0)

def opt(name, default=None):
    for i, a in enumerate(args):
        if a == name and i + 1 < len(args):
            return args[i + 1]
    return default

MODEL = {"provider": "fixture", "id": "pi-model", "name": "Fixture Pi model", "api": "fixture",
         "reasoning": False, "contextWindow": 128000, "maxTokens": 8192}

def emit(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()

def now():
    return int(time.time() * 1000)

def usage():
    return {"input": 12, "output": 20, "cacheRead": 0, "cacheWrite": 0, "totalTokens": 32,
            "cost": {"input": 0, "output": 0, "cacheRead": 0, "cacheWrite": 0, "total": 0}}

def assistant(content, stop):
    return {"role": "assistant", "content": content, "api": "fixture", "provider": "fixture", "model": "pi-model",
            "usage": usage(), "stopReason": stop, "timestamp": now()}

def run(prompt):
    user = {"role": "user", "content": [{"type": "text", "text": prompt}], "timestamp": now()}
    emit({"type": "agent_start"})
    emit({"type": "turn_start"})
    emit({"type": "message_start", "message": user})
    emit({"type": "message_end", "message": user})
    call = {"type": "toolCall", "id": "call_fx_1", "name": "read", "arguments": {"path": "README.md"}}
    first = assistant([call], "toolUse")
    emit({"type": "message_start", "message": assistant([], "toolUse")})
    emit({"type": "message_end", "message": first})
    emit({"type": "tool_execution_start", "toolCallId": "call_fx_1", "toolName": "read", "args": call["arguments"]})
    try:
        with open("README.md", encoding="utf-8") as handle:
            text = handle.read(2000)
    except OSError:
        text = "(no README.md)"
    result = {"content": [{"type": "text", "text": text}], "details": {}}
    emit({"type": "tool_execution_end", "toolCallId": "call_fx_1", "toolName": "read", "result": result,
          "isError": False})
    tool_msg = {"role": "toolResult", "toolCallId": "call_fx_1", "toolName": "read", "content": result["content"],
                "isError": False, "timestamp": now()}
    emit({"type": "message_start", "message": tool_msg})
    emit({"type": "message_end", "message": tool_msg})
    emit({"type": "turn_end", "message": first, "toolResults": [tool_msg]})
    emit({"type": "turn_start"})
    answer = "Synthetic Pi reply: I read README.md."
    partial = assistant([{"type": "text", "text": ""}], "stop")
    emit({"type": "message_start", "message": partial})
    for piece in ("Synthetic Pi reply: ", "I read README.md."):
        partial["content"][0]["text"] += piece
        emit({"type": "message_update", "message": partial,
              "assistantMessageEvent": {"type": "text_delta", "contentIndex": 0, "delta": piece}})
    final = assistant([{"type": "text", "text": answer}], "stop")
    emit({"type": "message_end", "message": final})
    emit({"type": "turn_end", "message": final, "toolResults": []})
    emit({"type": "agent_end", "messages": [user, first, tool_msg, final]})
    return answer

mode = opt("--mode", "text")
if mode == "rpc":
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            cmd = json.loads(raw)
        except ValueError:
            emit({"type": "response", "command": "parse", "success": False, "error": "invalid JSON"})
            continue
        kind = cmd.get("type")
        reply = {"type": "response", "command": kind, "success": True}
        if "id" in cmd:
            reply = {"id": cmd["id"], **reply}
        if kind == "get_state":
            reply["data"] = {"model": MODEL, "thinkingLevel": "off", "isStreaming": False,
                             "sessionId": "pi-fx-session", "messageCount": 0}
        elif kind == "get_available_models":
            reply["data"] = {"models": [MODEL]}
        emit(reply)
        if kind == "prompt":
            run(str(cmd.get("message") or ""))
    sys.exit(0)
rest = [a for a in args if not a.startswith("-") and a not in (mode, opt("--model"))]
prompt = rest[0] if rest else sys.stdin.read()
if mode == "json":
    run(prompt)
else:
    saved, sys.stdout = sys.stdout, open(os.devnull, "w")
    answer = run(prompt)
    sys.stdout = saved
    print(answer)
'''


def iso(ts: float | None = None) -> str:
    return datetime.fromtimestamp(ts if ts is not None else time.time(), tz=timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def claude_slug(path: str) -> str:
    return re.sub(r"[^a-zA-Z0-9]", "-", path)


# Claude history on disk: (session id, project key, title or None, [(role, text, timestamp)])
HISTORY = [
    ("6f1e3c52-1d1a-4b7e-9c55-0a6f3f1c0001", "project", None, [
        ("user", "Fix the login bug", "2026-10-01T10:00:00.000Z"),
        ("assistant", "The login form now trims the address before checking it.", "2026-10-01T10:00:20.000Z")]),
    ("6f1e3c52-1d1a-4b7e-9c55-0a6f3f1c0002", "project", "Release notes", [
        ("user", "Draft the release notes for 1.2", "2026-10-09T10:00:00.000Z"),
        ("tool", "Read CHANGELOG.md", "2026-10-09T10:00:05.000Z"),
        ("assistant", "Here is a draft: faster start, fewer crashes.", "2026-10-09T10:00:30.000Z")]),
    ("6f1e3c52-1d1a-4b7e-9c55-0a6f3f1c0003", "other", None, [
        ("user", "Clean the other project", "2026-10-05T09:00:00.000Z"),
        ("assistant", "Nothing to clean.", "2026-10-05T09:00:10.000Z")]),
]


def write_history(home: Path, folders: dict[str, Path]) -> list[str]:
    paths = []
    for session, key, title, turns in HISTORY:
        cwd = str(folders[key])
        target = home / ".claude" / "projects" / claude_slug(cwd) / f"{session}.jsonl"
        target.parent.mkdir(parents=True, exist_ok=True)
        rows: list[dict[str, Any]] = []
        parent = None
        last_uuid = None
        for i, (role, text, stamp) in enumerate(turns):
            row_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{session}/{i}"))
            common = {"parentUuid": parent, "isSidechain": False, "userType": "external", "cwd": cwd,
                      "sessionId": session, "version": "2.1.316", "gitBranch": "main", "uuid": row_uuid,
                      "timestamp": stamp}
            if role == "user":
                rows.append({**common, "type": "user", "message": {"role": "user", "content": text}})
            elif role == "tool":
                tool_id = "toolu_hist_" + row_uuid[:8]
                rows.append({**common, "type": "assistant", "message": {
                    "id": "msg_hist_" + row_uuid[:8], "type": "message", "role": "assistant",
                    "model": CLAUDE_LONG_MODEL, "content": [{"type": "tool_use", "id": tool_id, "name": "Read",
                                                             "input": {"file_path": f"{cwd}/CHANGELOG.md"}}],
                    "stop_reason": "tool_use", "usage": {"input_tokens": 10, "output_tokens": 5}}})
            else:
                rows.append({**common, "type": "assistant", "message": {
                    "id": "msg_hist_" + row_uuid[:8], "type": "message", "role": "assistant",
                    "model": CLAUDE_LONG_MODEL, "content": [{"type": "text", "text": text}],
                    "stop_reason": "end_turn", "usage": {"input_tokens": 10, "output_tokens": 5}}})
            parent = last_uuid = row_uuid
        if title:
            rows.insert(0, {"type": "summary", "summary": title, "leafUuid": last_uuid})
        target.write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")
        paths.append(str(target))
    return paths


def read_history(path: Path) -> dict[str, Any] | None:
    try:
        rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    except (OSError, ValueError):
        return None
    title = next((r.get("summary") for r in rows if r.get("type") == "summary"), None)
    entries = []
    prompts = []
    last = None
    cwd = None
    for r in rows:
        if r.get("type") not in ("user", "assistant"):
            continue
        cwd = r.get("cwd") or cwd
        last = r.get("timestamp") or last
        content = (r.get("message") or {}).get("content")
        if r["type"] == "user":
            text = content if isinstance(content, str) else ""
            prompts.append(text)
            entries.append((r["timestamp"], {"type": "user_message", "text": text, "messageId": r["uuid"]}))
            continue
        for block in content if isinstance(content, list) else []:
            if block.get("type") == "text":
                entries.append((r["timestamp"], {"type": "assistant_message", "text": block.get("text", ""),
                                                 "messageId": (r.get("message") or {}).get("id")}))
            elif block.get("type") == "tool_use":
                entries.append((r["timestamp"], {"type": "tool_call", "callId": block.get("id"), "name": "Read",
                                                 "status": "completed",
                                                 "detail": {"type": "read",
                                                            "filePath": (block.get("input") or {}).get("file_path"),
                                                            "content": ""}}))
    return {"title": title, "prompts": prompts, "last": last, "cwd": cwd, "entries": entries}


# --------------------------------------------------------------------------- websocket


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
    return first & 0x0F, bytes(payload)


class Conn:
    def __init__(self, writer: asyncio.StreamWriter, header_ok: bool) -> None:
        self.writer = writer
        self.header_ok = header_ok
        self.ready = False
        self.closed = False
        self.lock = asyncio.Lock()
        self.slots: dict[int, str] = {}

    async def raw(self, data: bytes) -> None:
        if self.closed:
            return
        async with self.lock:
            if self.closed:
                return
            try:
                self.writer.write(data)
                await self.writer.drain()
            except (ConnectionError, RuntimeError):
                self.abort()

    async def send(self, obj: dict[str, Any]) -> None:
        await self.raw(ws_frame(json.dumps(obj, separators=(",", ":")).encode("utf-8")))

    async def session(self, type_: str, payload: dict[str, Any]) -> None:
        await self.send({"type": "session", "message": {"type": type_, "payload": payload}})

    async def close(self, code: int, reason: str = "") -> None:
        await self.raw(ws_frame(code.to_bytes(2, "big") + reason.encode()[:100], opcode=8))
        if not self.closed:
            self.closed = True
            try:
                self.writer.close()
            except (ConnectionError, RuntimeError):
                pass

    def abort(self) -> None:
        if self.closed:
            return
        self.closed = True
        try:
            self.writer.transport.abort()
        except (AttributeError, RuntimeError):
            pass


# --------------------------------------------------------------------------- terminals


class Terminal:
    def __init__(self, tid: str, name: str, cwd: str, proc: subprocess.Popen[Any], master: int) -> None:
        self.id = tid
        self.name = name
        self.title = name
        self.cwd = cwd
        self.proc = proc
        self.master = master
        self.scrollback = bytearray()
        self.subscribers: list[tuple[Conn, int]] = []
        self.exited = False

    def info(self) -> dict[str, Any]:
        return {"id": self.id, "name": self.name, "title": self.title, "cwd": self.cwd}


def kill_exact(proc: subprocess.Popen[Any]) -> None:
    """SIGHUP one exact PID (a shell on a pty ends on hang-up), then stop_pid."""
    if proc.poll() is not None:
        return
    try:
        os.kill(proc.pid, signal.SIGHUP)
    except ProcessLookupError:
        return
    try:
        proc.wait(1.5)
    except subprocess.TimeoutExpired:
        stop_pid(proc.pid, timeout=1.0, proc=proc)


# --------------------------------------------------------------------------- stub daemon


class Stub:
    def __init__(self, fx: Fixture, folders: dict[str, Path], home: Path, password: str) -> None:
        self.fx = fx
        self.folders = folders
        self.project = str(folders["project"])
        self.home = home
        self.password = password
        self.loop = asyncio.new_event_loop()
        self.conns: set[Conn] = set()
        self.agents: dict[str, dict[str, Any]] = {}
        self.timelines: dict[str, list[dict[str, Any]]] = {}
        self.seq: dict[str, int] = {}
        self.turns: dict[str, dict[str, Any]] = {}
        self.terminals: dict[str, Terminal] = {}
        self.failing: dict[str, str] = {}
        self.version = DAEMON_VERSION
        self.latest = DAEMON_VERSION
        self.update_error: str | None = None
        self.providers: dict[str, dict[str, Any]] = {}
        self.term_counter = 0
        self.server: Any = None

    # -- seed ---------------------------------------------------------------

    def provider_seed(self) -> None:
        self.providers = {
            "claude": {"provider": "claude", "label": "Claude Code", "status": "ready", "enabled": True,
                       "models": [
                           {"provider": "claude", "id": "sonnet", "label": "Sonnet", "isDefault": True,
                            "aliases": [CLAUDE_LONG_MODEL], "defaultThinkingOptionId": "high",
                            "thinkingOptions": [{"id": "low", "label": "Low"}, {"id": "high", "label": "High"}]},
                           {"provider": "claude", "id": "opus", "label": "Opus",
                            "aliases": ["claude-opus-fixture-20261001"]},
                           {"provider": "claude", "id": "haiku", "label": "Haiku",
                            "aliases": ["claude-haiku-fixture-20261001"]}],
                       "modes": [{"id": "default", "label": "Always Ask"},
                                 {"id": "acceptEdits", "label": "Accept File Edits"},
                                 {"id": "plan", "label": "Plan Mode"},
                                 {"id": "bypassPermissions", "label": "Bypass"}],
                       "defaultModeId": "default"},
            "pi": {"provider": "pi", "label": "Pi", "status": "ready", "enabled": True,
                   "models": [{"provider": "pi", "id": "fixture/pi-model", "label": "Fixture Pi model",
                               "isDefault": True}],
                   "modes": [], "defaultModeId": None},
        }

    def agent(self, aid: str, provider: str, cwd: str, title: str, *, model: str, updated: str,
              created: str | None = None, archived: str | None = None, session: str | None = None) -> dict[str, Any]:
        modes = self.providers[provider]["modes"]
        return {"id": aid, "provider": provider,
                "persistence": {"provider": provider, "sessionId": session or str(uuid.uuid4())},
                "cwd": cwd, "model": model, "title": title, "status": "idle",
                "createdAt": created or updated, "updatedAt": updated, "activeTurn": None,
                "currentModeId": self.providers[provider]["defaultModeId"], "availableModes": modes,
                "thinkingOptionId": None, "pendingPermissions": [], "requiresAttention": False,
                "lastUsage": {"totalCostUsd": 0.0}, "labels": {}, "archivedAt": archived}

    def seed(self) -> None:
        self.provider_seed()
        self.agents.clear()
        self.timelines.clear()
        self.seq.clear()
        self.version = self.latest = DAEMON_VERSION
        self.update_error = None
        self.failing.clear()
        other = str(self.folders["other"])
        rows = [
            self.agent("a0f1c2d3-0000-4000-8000-000000000001", "claude", self.project, "Explain the fixture project",
                       model=CLAUDE_LONG_MODEL, created="2026-10-20T09:00:00.000Z", updated="2026-10-20T09:01:00.000Z"),
            self.agent("a0f1c2d3-0000-4000-8000-000000000002", "pi", self.project, "Pi fixture conversation",
                       model="fixture/pi-model", updated="2026-10-19T09:00:00.000Z"),
            self.agent("a0f1c2d3-0000-4000-8000-000000000003", "claude", other, "Another project's conversation",
                       model=CLAUDE_LONG_MODEL, updated="2026-10-18T09:00:00.000Z"),
            self.agent("a0f1c2d3-0000-4000-8000-000000000004", "claude", self.project, "Archived conversation",
                       model=CLAUDE_LONG_MODEL, updated="2026-10-17T09:00:00.000Z",
                       archived="2026-10-17T10:00:00.000Z"),
        ]
        for row in rows:
            self.agents[row["id"]] = row
            self.timelines[row["id"]] = []
            self.seq[row["id"]] = 0
        claude, pi = rows[0]["id"], rows[1]["id"]
        readme = f"{self.project}/README.md"
        for item, at in (
                ({"type": "user_message", "text": "What is in this project?", "messageId": "hist-u1"},
                 "2026-10-20T09:00:00.000Z"),
                ({"type": "reasoning", "text": "Reading the README first."}, "2026-10-20T09:00:02.000Z"),
                ({"type": "tool_call", "callId": "toolu_hist_1", "name": "Read", "status": "completed",
                  "detail": {"type": "read", "filePath": readme, "content": "# Fixture project"}},
                 "2026-10-20T09:00:05.000Z"),
                ({"type": "assistant_message", "text": "A small fixture project with a README.",
                  "messageId": "msg_hist_1"}, "2026-10-20T09:01:00.000Z")):
            self.store(claude, "claude", item, at)
        for item, at in (({"type": "user_message", "text": "Hello Pi", "messageId": "hist-p1"},
                          "2026-10-19T08:59:00.000Z"),
                         ({"type": "assistant_message", "text": "Hello from the fixture Pi.",
                           "messageId": "msg_hist_p1"}, "2026-10-19T09:00:00.000Z")):
            self.store(pi, "pi", item, at)

    def store(self, aid: str, provider: str, item: dict[str, Any], at: str | None = None) -> int:
        """Adds an item to the projected timeline (merging deltas and tool updates)."""
        self.seq[aid] = self.seq.get(aid, 0) + 1
        seq = self.seq[aid]
        entries = self.timelines.setdefault(aid, [])
        key = item.get("callId") if item["type"] == "tool_call" else (
            item.get("messageId") if item["type"] == "assistant_message" else None)
        for entry in entries:
            old = entry["item"]
            if key and old["type"] == item["type"] and old.get("callId" if item["type"] == "tool_call" else "messageId") == key:
                if item["type"] == "assistant_message":
                    old["text"] += item["text"]
                else:
                    entry["item"] = {**old, **item}
                entry["seqEnd"] = seq
                return seq
        entries.append({"provider": provider, "item": dict(item), "timestamp": at or iso(), "seqStart": seq,
                        "seqEnd": seq})
        return seq

    def epoch(self, aid: str) -> str:
        return f"epoch-{aid[:8]}"

    def publish_state(self) -> None:
        self.fx.set_state(
            mode="stub", daemon_version=self.version, latest_version=self.latest, connections=len(self.conns),
            agents={a["id"]: {"provider": a["provider"], "status": a["status"], "archived": a["archivedAt"] is not None}
                    for a in self.agents.values()},
            terminals={t.id: t.title for t in self.terminals.values() if not t.exited},
            failing=sorted(self.failing))

    # -- terminals -------------------------------------------------------------

    def spawn_terminal(self, name: str, argv: list[str]) -> Terminal:
        self.term_counter += 1
        tid = f"term-{self.term_counter}"
        master, slave = pty.openpty()
        fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 80, 0, 0))
        env = {"HOME": str(self.home), "PATH": "/usr/bin:/bin", "TERM": "xterm-256color", "LANG": "C.UTF-8",
               "PS1": "fx$ "}
        proc = subprocess.Popen(argv, stdin=slave, stdout=slave, stderr=slave, cwd=self.project, env=env,
                                start_new_session=True, close_fds=True)
        os.close(slave)
        self.fx.adopt(proc)
        term = Terminal(tid, name, self.project, proc, master)
        self.terminals[tid] = term
        threading.Thread(target=self.pump, args=(term,), name=f"FX-PASEO:{tid}", daemon=True).start()
        return term

    def pump(self, term: Terminal) -> None:
        while True:
            try:
                data = os.read(term.master, 4096)
            except OSError:
                data = b""
            if not data:
                break
            self.loop.call_soon_threadsafe(lambda d=data: self.loop.create_task(self.term_output(term, d)))
        try:
            term.proc.wait(5)
        except subprocess.TimeoutExpired:
            pass
        try:
            os.close(term.master)
        except OSError:
            pass
        self.loop.call_soon_threadsafe(lambda: self.loop.create_task(self.term_exit(term)))

    async def term_output(self, term: Terminal, data: bytes) -> None:
        term.scrollback.extend(data)
        del term.scrollback[:-65536]
        for conn, slot in list(term.subscribers):
            await conn.raw(ws_frame(bytes([0x01, slot]) + data, opcode=2))

    async def term_exit(self, term: Terminal) -> None:
        if term.exited:
            return
        term.exited = True
        for conn, slot in list(term.subscribers):
            conn.slots.pop(slot, None)
            await conn.session("terminal_stream_exit", {"terminalId": term.id, "exitCode": term.proc.returncode})
        term.subscribers.clear()
        self.publish_state()

    def seed_terminals(self) -> None:
        self.spawn_terminal("ticker", ["/bin/sh", "-c",
                                       'i=0; while :; do i=$((i+1)); echo "tick $i"; sleep 0.5; done'])
        self.spawn_terminal("shell", ["/bin/sh", "-i"])

    def kill_terminals(self) -> None:
        for term in list(self.terminals.values()):
            kill_exact(term.proc)
        self.terminals.clear()

    # -- io ----------------------------------------------------------------------

    async def broadcast(self, type_: str, payload: dict[str, Any]) -> None:
        for conn in list(self.conns):
            if conn.ready:
                await conn.session(type_, payload)

    async def client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        try:
            raw = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 5)
        except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError, ConnectionError):
            writer.close()
            return
        lines = raw.decode("latin1").split("\r\n")
        request = lines[0].split(" ")
        headers: dict[str, str] = {}
        for line in lines[1:]:
            if ":" in line:
                k, v = line.split(":", 1)
                headers[k.strip().lower()] = v.strip()
        path = request[1].split("?", 1)[0] if len(request) > 1 else "/"
        if (len(request) != 3 or request[0] != "GET" or path not in ("/ws", "/")
                or headers.get("upgrade", "").lower() != "websocket" or "sec-websocket-key" not in headers):
            self.fx.log.write(method=request[0] if request else "?", path=path, status=404, transport="http")
            writer.write(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            writer.close()
            return
        offered = [p.strip() for p in headers.get("sec-websocket-protocol", "").split(",") if p.strip()]
        bearer = headers.get("authorization", "")
        header_ok = bool(self.password) and (f"paseo.bearer.{self.password}" in offered
                                             or bearer == f"Bearer {self.password}")
        accept = base64.b64encode(hashlib.sha1((headers["sec-websocket-key"] + GUID).encode()).digest()).decode()
        # The subprotocol is not echoed: it carries the password (the app accepts no echo).
        writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                      f"Sec-WebSocket-Accept: {accept}\r\n\r\n").encode("latin1"))
        await writer.drain()
        self.fx.log.write(method="websocket/upgrade", path=path, status=101, transport="ws")
        conn = Conn(writer, header_ok)
        self.conns.add(conn)
        self.publish_state()
        try:
            while not conn.closed:
                opcode, payload = await read_frame(reader)
                if opcode == 8:
                    break
                if opcode == 9:
                    await conn.raw(ws_frame(payload, opcode=10))
                    continue
                if opcode != 1:
                    continue
                try:
                    msg = json.loads(payload.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    continue
                if isinstance(msg, dict):
                    await self.on_message(conn, msg)
        except (asyncio.IncompleteReadError, ConnectionError, ValueError):
            pass
        finally:
            conn.abort()
            self.conns.discard(conn)
            for term in self.terminals.values():
                term.subscribers = [(c, s) for c, s in term.subscribers if c is not conn]
            self.publish_state()

    async def on_message(self, conn: Conn, msg: dict[str, Any]) -> None:
        outer = msg.get("type")
        if outer == "ping":
            await conn.send({"type": "pong"})
            return
        if outer == "hello":
            await self.hello(conn, msg)
            return
        if outer != "session" or not conn.ready or not isinstance(msg.get("message"), dict):
            return
        inner = msg["message"]
        type_ = inner.get("type")
        if not isinstance(type_, str):
            return
        rid = inner.get("requestId")
        if type_ in self.failing and rid:
            self.fx.log.write(method=type_, status=500, transport="ws")
            await conn.session("rpc_error", {"requestId": rid, "error": self.failing[type_]})
            return
        handled = type_.endswith("request") or type_ == "terminal_input"
        handler = getattr(self, "on_" + re.sub(r"[^a-z0-9]", "_", type_), None) if handled else None
        if handler is None:
            self.fx.log.write(method=type_, status=404, transport="ws")
            if rid:
                await conn.session("rpc_error", {"requestId": rid, "error": f"Unknown request: {type_}"})
            return
        status = 200
        try:
            result = await handler(conn, inner)
            status = result if isinstance(result, int) else 200
        finally:
            self.fx.log.write(method=type_, status=status, transport="ws")

    async def hello(self, conn: Conn, msg: dict[str, Any]) -> None:
        auth = msg.get("auth") if isinstance(msg.get("auth"), dict) else {}
        given = auth.get("password") if isinstance(auth.get("password"), str) else None
        if self.password and not conn.header_ok and given != self.password:
            reason = "password_required" if given is None else "incorrect_password"
            self.fx.log.write(method="hello", status=401, transport="ws")
            caps = msg.get("capabilities") if isinstance(msg.get("capabilities"), dict) else {}
            if caps.get("hello_rejection"):
                await conn.send({"type": "hello.rejected", "reason": reason, "accepts": ["password"]})
            await conn.close(4401, reason)
            return
        if msg.get("protocolVersion") != 1:
            self.fx.log.write(method="hello", status=409, transport="ws")
            await conn.send({"type": "hello.rejected", "reason": "incompatible_protocol", "accepts": ["password"]})
            await conn.close(4003, "incompatible_protocol")
            return
        conn.ready = True
        self.fx.log.write(method="hello", status=200, transport="ws")
        await conn.session("status", {"status": "server_info", "serverId": "fx-paseo", "hostname": "fx-host",
                                      "version": self.version, "features": {"workspaceFileEditing": False}})

    async def reply(self, conn: Conn, inner: dict[str, Any], type_: str, payload: dict[str, Any]) -> None:
        await conn.session(type_, {**payload, "requestId": inner.get("requestId")})

    # -- agents ------------------------------------------------------------------

    def live_agents(self) -> list[dict[str, Any]]:
        rows = [a for a in self.agents.values() if a["archivedAt"] is None]
        return sorted(rows, key=lambda a: a["updatedAt"], reverse=True)

    async def upsert(self, agent: dict[str, Any]) -> None:
        agent["updatedAt"] = iso()
        await self.broadcast("agent_update", {"kind": "upsert", "agent": agent})
        self.publish_state()

    async def on_fetch_agents_request(self, conn: Conn, m: dict[str, Any]) -> None:
        page = m.get("page") if isinstance(m.get("page"), dict) else {}
        limit = page.get("limit") if isinstance(page.get("limit"), int) else 200
        rows = self.live_agents()[:max(1, min(limit, 200))]
        await self.reply(conn, m, "fetch_agents_response", {
            "entries": [{"agent": a} for a in rows],
            "pageInfo": {"nextCursor": None, "prevCursor": None, "hasMore": False}})

    async def on_fetch_agent_request(self, conn: Conn, m: dict[str, Any]) -> int:
        agent = self.agents.get(m.get("agentId"))
        if agent is None:
            await self.reply(conn, m, "fetch_agent_response", {"agent": None, "error": "Agent not found"})
            return 404
        await self.reply(conn, m, "fetch_agent_response", {"agent": agent})
        return 200

    async def on_fetch_agent_timeline_request(self, conn: Conn, m: dict[str, Any]) -> int:
        aid = m.get("agentId")
        agent = self.agents.get(aid)
        if agent is None:
            await self.reply(conn, m, "fetch_agent_timeline_response",
                             {"agentId": aid, "agent": None, "entries": [], "error": "Agent not found"})
            return 404
        entries = self.timelines.get(aid, [])
        limit = m.get("limit")
        if isinstance(limit, int) and limit > 0:
            entries = entries[-limit:]
        epoch = self.epoch(aid)
        seq = self.seq.get(aid, 0)
        await self.reply(conn, m, "fetch_agent_timeline_response", {
            "agentId": aid, "agent": agent, "direction": m.get("direction", "tail"),
            "projection": m.get("projection", "projected"), "epoch": epoch,
            "startCursor": {"epoch": epoch, "seq": entries[0]["seqStart"] if entries else 0},
            "endCursor": {"epoch": epoch, "seq": seq}, "hasOlder": False, "hasNewer": False,
            "entries": entries})
        return 200

    async def on_get_providers_snapshot_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "get_providers_snapshot_response",
                         {"entries": list(self.providers.values()), "generatedAt": iso()})

    async def on_refresh_providers_snapshot_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "refresh_providers_snapshot_response", {"accepted": True})
        await self.broadcast("providers_snapshot_update", {"entries": list(self.providers.values()),
                                                           "generatedAt": iso()})

    async def on_list_provider_features_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "list_provider_features_response", {"features": []})

    async def on_list_commands_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "list_commands_response", {"agentId": m.get("agentId"), "commands": []})

    async def on_agent_provider_subagents_list_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "agent.provider_subagents.list.response",
                         {"parentAgentId": m.get("agentId") or m.get("parentAgentId"), "subagents": [], "error": None})

    async def on_checkout_status_request(self, conn: Conn, m: dict[str, Any]) -> None:
        await self.reply(conn, m, "checkout_status_response", {"cwd": m.get("cwd"), "isGit": False})

    def default_model(self, provider: str) -> str:
        models = self.providers[provider]["models"]
        model = next((x for x in models if x.get("isDefault")), models[0])
        return (model.get("aliases") or [model["id"]])[0]

    async def on_create_agent_request(self, conn: Conn, m: dict[str, Any]) -> int:
        config = m.get("config") if isinstance(m.get("config"), dict) else {}
        provider = config.get("provider")
        entry = self.providers.get(provider) if isinstance(provider, str) else None
        if entry is None or entry.get("status") != "ready" or entry.get("error") or entry.get("enabled") is False:
            await self.reply(conn, m, "status", {"status": "agent_create_failed",
                                                 "error": (entry or {}).get("error") or f"Provider {provider} is not ready"})
            return 409
        cwd = config.get("cwd") if isinstance(config.get("cwd"), str) else self.project
        prompt = m.get("initialPrompt") if isinstance(m.get("initialPrompt"), str) else ""
        title = config.get("title") if isinstance(config.get("title"), str) else prompt.strip()[:60]
        model = config.get("model") if isinstance(config.get("model"), str) else self.default_model(provider)
        for item in entry["models"]:
            if item["id"] == model and item.get("aliases"):
                model = item["aliases"][0]
        aid = str(uuid.uuid4())
        agent = self.agent(aid, provider, cwd, title, model=model, updated=iso())
        if isinstance(config.get("modeId"), str):
            agent["currentModeId"] = config["modeId"]
        if isinstance(config.get("thinkingOptionId"), str):
            agent["thinkingOptionId"] = config["thinkingOptionId"]
        self.agents[aid] = agent
        self.timelines[aid] = []
        self.seq[aid] = 0
        await self.reply(conn, m, "status", {"status": "agent_created", "agentId": aid, "agent": agent})
        await self.broadcast("agent_update", {"kind": "upsert", "agent": agent})
        if prompt:
            self.start_turn(agent, prompt, m.get("clientMessageId"))
        self.publish_state()
        return 200

    async def on_send_agent_message_request(self, conn: Conn, m: dict[str, Any]) -> int:
        agent = self.agents.get(m.get("agentId"))
        text = m.get("text") if isinstance(m.get("text"), str) else ""
        if agent is None or agent["archivedAt"] is not None:
            await self.reply(conn, m, "send_agent_message_response",
                             {"agentId": m.get("agentId"), "accepted": False, "error": "Agent not found"})
            return 404
        provider = self.providers.get(agent["provider"], {})
        if provider.get("status") != "ready" or provider.get("error"):
            await self.reply(conn, m, "send_agent_message_response",
                             {"agentId": agent["id"], "accepted": False,
                              "error": provider.get("error") or "Provider is not ready"})
            return 409
        await self.reply(conn, m, "send_agent_message_response", {"agentId": agent["id"], "accepted": True})
        running = self.turns.get(agent["id"])
        if running and not running["task"].done():
            if m.get("activeTurnBehavior") == "steer":
                running["steers"].append((text, m.get("messageId")))
            else:
                running["task"].cancel()
                await self.end_turn(agent, running, "turn_canceled")
                self.start_turn(agent, text, m.get("messageId"))
        else:
            self.start_turn(agent, text, m.get("messageId"))
        return 200

    async def on_cancel_agent_request(self, conn: Conn, m: dict[str, Any]) -> None:
        agent = self.agents.get(m.get("agentId"))
        await self.reply(conn, m, "cancel_agent_response", {"agentId": m.get("agentId"), "accepted": agent is not None})
        running = self.turns.get(agent["id"]) if agent else None
        if agent and running and not running["task"].done():
            running["task"].cancel()
            await self.end_turn(agent, running, "turn_canceled")

    async def archive(self, aid: str, rid: Any = None) -> bool:
        agent = self.agents.get(aid)
        if agent is None or agent["archivedAt"] is not None:
            return False
        running = self.turns.get(aid)
        if running and not running["task"].done():
            running["task"].cancel()
            await self.end_turn(agent, running, "turn_canceled")
        agent["archivedAt"] = iso()
        agent["status"] = "closed"
        payload: dict[str, Any] = {"agentId": aid, "archivedAt": agent["archivedAt"]}
        if rid is not None:
            payload["requestId"] = rid
        await self.broadcast("agent_archived", payload)
        self.publish_state()
        return True

    async def on_archive_agent_request(self, conn: Conn, m: dict[str, Any]) -> int:
        if not await self.archive(m.get("agentId"), m.get("requestId")):
            await self.reply(conn, m, "rpc_error", {"error": "Agent not found or already archived"})
            return 404
        return 200

    async def on_update_agent_request(self, conn: Conn, m: dict[str, Any]) -> int:
        agent = self.agents.get(m.get("agentId"))
        if agent is None:
            await self.reply(conn, m, "update_agent_response", {"agentId": m.get("agentId"), "accepted": False,
                                                                "error": "Agent not found"})
            return 404
        if isinstance(m.get("name"), str):
            agent["title"] = m["name"]
        await self.reply(conn, m, "update_agent_response", {"agentId": agent["id"], "accepted": True})
        await self.upsert(agent)
        return 200

    async def set_field(self, conn: Conn, m: dict[str, Any], response: str, field: str, key: str) -> int:
        agent = self.agents.get(m.get("agentId"))
        if agent is None:
            await self.reply(conn, m, response, {"agentId": m.get("agentId"), "accepted": False,
                                                 "error": "Agent not found"})
            return 404
        agent[field] = m.get(key)
        await self.reply(conn, m, response, {"agentId": agent["id"], "accepted": True})
        await self.upsert(agent)
        return 200

    async def on_set_agent_mode_request(self, conn: Conn, m: dict[str, Any]) -> int:
        return await self.set_field(conn, m, "set_agent_mode_response", "currentModeId", "modeId")

    async def on_set_agent_model_request(self, conn: Conn, m: dict[str, Any]) -> int:
        return await self.set_field(conn, m, "set_agent_model_response", "model", "modelId")

    async def on_set_agent_thinking_request(self, conn: Conn, m: dict[str, Any]) -> int:
        key = "thinkingOptionId" if "thinkingOptionId" in m else "optionId"
        return await self.set_field(conn, m, "set_agent_thinking_response", "thinkingOptionId", key)

    # -- import --------------------------------------------------------------------

    def history_dir(self, cwd: str) -> Path:
        return self.home / ".claude" / "projects" / claude_slug(cwd)

    def imported_sessions(self) -> set[str]:
        return {a["persistence"]["sessionId"] for a in self.agents.values()
                if a["archivedAt"] is None and a["provider"] == "claude"}

    async def on_fetch_recent_provider_sessions_request(self, conn: Conn, m: dict[str, Any]) -> None:
        cwd = m.get("cwd") if isinstance(m.get("cwd"), str) else self.project
        limit = m.get("limit") if isinstance(m.get("limit"), int) else 50
        taken = self.imported_sessions()
        entries, skipped = [], 0
        folder = self.history_dir(cwd)
        for path in sorted(folder.glob("*.jsonl")) if folder.is_dir() else []:
            info = read_history(path)
            if info is None or not info["last"]:
                continue
            if path.stem in taken:
                skipped += 1
                continue
            entries.append({"providerId": "claude", "providerLabel": "Claude Code", "providerHandleId": path.stem,
                            "cwd": info["cwd"] or cwd, "title": info["title"],
                            "firstPromptPreview": info["prompts"][0][:120] if info["prompts"] else None,
                            "lastPromptPreview": info["prompts"][-1][:120] if info["prompts"] else None,
                            "lastActivityAt": info["last"]})
        entries.sort(key=lambda e: e["lastActivityAt"], reverse=True)
        await self.reply(conn, m, "fetch_recent_provider_sessions_response",
                         {"entries": entries[:limit], "filteredAlreadyImportedCount": skipped})

    async def on_import_agent_request(self, conn: Conn, m: dict[str, Any]) -> int:
        handle = m.get("providerHandleId")
        cwd = m.get("cwd") if isinstance(m.get("cwd"), str) else self.project
        path = self.history_dir(cwd) / f"{handle}.jsonl" if isinstance(handle, str) and "/" not in handle else None
        info = read_history(path) if path is not None and path.is_file() else None
        if m.get("providerId") != "claude" or info is None:
            await self.reply(conn, m, "status", {"status": "agent_create_failed",
                                                 "error": "No Claude Code session with that id in this folder"})
            return 404
        aid = str(uuid.uuid4())
        title = info["title"] or (info["prompts"][0][:60] if info["prompts"] else "")
        agent = self.agent(aid, "claude", cwd, title, model=CLAUDE_LONG_MODEL, updated=info["last"],
                           session=handle)
        self.agents[aid] = agent
        self.timelines[aid] = []
        self.seq[aid] = 0
        for at, item in info["entries"]:
            self.store(aid, "claude", item, at)
        await self.reply(conn, m, "status", {"status": "agent_resumed", "agentId": aid, "agent": agent})
        await self.broadcast("agent_update", {"kind": "upsert", "agent": agent})
        self.publish_state()
        return 200

    # -- turns ---------------------------------------------------------------------

    async def stream(self, agent: dict[str, Any], event: dict[str, Any], seq: int | None = None) -> None:
        aid = agent["id"]
        if seq is None:
            self.seq[aid] = self.seq.get(aid, 0) + 1
            seq = self.seq[aid]
        await self.broadcast("agent_stream", {"agentId": aid, "event": event, "timestamp": iso(),
                                              "seq": seq, "epoch": self.epoch(aid)})

    async def timeline(self, agent: dict[str, Any], turn_id: str, item: dict[str, Any]) -> None:
        seq = self.store(agent["id"], agent["provider"], item)
        await self.stream(agent, {"type": "timeline", "provider": agent["provider"], "item": item,
                                  "turnId": turn_id}, seq)

    def start_turn(self, agent: dict[str, Any], text: str, message_id: Any) -> None:
        turn_id = f"turn-{uuid.uuid4().hex[:8]}"
        state: dict[str, Any] = {"id": turn_id, "steers": []}
        state["task"] = self.loop.create_task(self.run_turn(agent, state, text,
                                                            message_id if isinstance(message_id, str) else None))
        self.turns[agent["id"]] = state

    async def end_turn(self, agent: dict[str, Any], state: dict[str, Any], kind: str) -> None:
        if state.get("ended"):
            return
        state["ended"] = True
        event: dict[str, Any] = {"type": kind, "provider": agent["provider"], "turnId": state["id"]}
        if kind == "turn_failed":
            event["error"] = "Synthetic failure"
        await self.stream(agent, event)
        agent["status"] = "error" if kind == "turn_failed" else "idle"
        agent["activeTurn"] = None
        if kind == "turn_failed":
            agent["lastError"] = "Synthetic failure"
        await self.upsert(agent)

    async def reply_text(self, agent: dict[str, Any], turn_id: str, pieces: list[str], pause: float) -> None:
        mid = f"msg_fx_{uuid.uuid4().hex[:12]}"
        for piece in pieces:
            await self.timeline(agent, turn_id, {"type": "assistant_message", "text": piece, "messageId": mid})
            await asyncio.sleep(pause)

    async def apply_steers(self, agent: dict[str, Any], state: dict[str, Any]) -> None:
        while state["steers"]:
            text, mid = state["steers"].pop(0)
            await self.timeline(agent, state["id"], {"type": "user_message", "text": text,
                                                     "messageId": mid or str(uuid.uuid4())})
            await self.reply_text(agent, state["id"], ["Steer received; ", "adjusting the synthetic reply."], 0.05)

    async def run_turn(self, agent: dict[str, Any], state: dict[str, Any], text: str, message_id: str | None) -> None:
        provider, turn_id, lower = agent["provider"], state["id"], text.lower()
        try:
            agent["status"] = "running"
            agent["activeTurn"] = {"turnId": turn_id, "startedAt": iso()}
            agent.pop("lastError", None)
            await self.upsert(agent)
            await self.stream(agent, {"type": "turn_started", "provider": provider, "turnId": turn_id})
            await self.timeline(agent, turn_id, {"type": "user_message", "text": text,
                                                 "messageId": message_id or str(uuid.uuid4())})
            if "fail" in lower:
                await asyncio.sleep(0.05)
                await self.end_turn(agent, state, "turn_failed")
                return
            if provider == "claude":
                await self.timeline(agent, turn_id, {"type": "reasoning", "text": "Planning the synthetic reply."})
            call = f"toolu_fx_{uuid.uuid4().hex[:10]}"
            readme = f"{agent['cwd']}/README.md"
            tool = {"type": "tool_call", "callId": call, "name": "Read" if provider == "claude" else "read",
                    "status": "running", "detail": {"type": "read", "filePath": readme}}
            await self.timeline(agent, turn_id, tool)
            await asyncio.sleep(0.05)
            await self.timeline(agent, turn_id, {**tool, "status": "completed",
                                                 "detail": {"type": "read", "filePath": readme,
                                                            "content": "# Fixture project"}})
            who = "Claude" if provider == "claude" else "Pi"
            pause = 0.5 if "slow" in lower else 0.05
            await self.reply_text(agent, turn_id, [f"Synthetic {who} ", "reply: I read ", "README.md."], pause)
            await self.apply_steers(agent, state)
            if "long-running" in lower or "long running" in lower:
                while True:
                    await self.apply_steers(agent, state)
                    await asyncio.sleep(0.1)
            await self.end_turn(agent, state, "turn_completed")
        except asyncio.CancelledError:
            pass

    # -- terminals (requests) --------------------------------------------------------

    def live_terminal(self, tid: Any) -> Terminal | None:
        term = self.terminals.get(tid) if isinstance(tid, str) else None
        return term if term is not None and not term.exited else None

    async def on_list_terminals_request(self, conn: Conn, m: dict[str, Any]) -> None:
        cwd = m.get("cwd")
        rows = [t.info() for t in self.terminals.values() if not t.exited and (cwd is None or t.cwd == cwd)]
        await self.reply(conn, m, "list_terminals_response", {"cwd": cwd, "terminals": rows})

    async def on_create_terminal_request(self, conn: Conn, m: dict[str, Any]) -> None:
        name = m.get("name") if isinstance(m.get("name"), str) and m.get("name") else \
            f"Terminal {self.term_counter + 1}"
        term = self.spawn_terminal(name, ["/bin/sh", "-i"])
        await self.reply(conn, m, "create_terminal_response", {"terminal": term.info(), "error": None})
        self.publish_state()

    async def on_subscribe_terminal_request(self, conn: Conn, m: dict[str, Any]) -> int:
        term = self.live_terminal(m.get("terminalId"))
        free = next((s for s in range(256) if s not in conn.slots), None)
        if term is None or free is None:
            await self.reply(conn, m, "subscribe_terminal_response",
                             {"terminalId": m.get("terminalId"), "slot": None, "error": "Terminal not found"})
            return 404
        conn.slots[free] = term.id
        term.subscribers.append((conn, free))
        await self.reply(conn, m, "subscribe_terminal_response", {"terminalId": term.id, "slot": free, "error": None})
        await conn.raw(ws_frame(bytes([0x05, free]) + bytes(term.scrollback[-16384:]), opcode=2))
        return 200

    async def on_unsubscribe_terminal_request(self, conn: Conn, m: dict[str, Any]) -> None:
        term = self.terminals.get(m.get("terminalId"))
        if term is not None:
            for c, s in [(c, s) for c, s in term.subscribers if c is conn]:
                conn.slots.pop(s, None)
            term.subscribers = [(c, s) for c, s in term.subscribers if c is not conn]
        if m.get("requestId"):
            await self.reply(conn, m, "unsubscribe_terminal_response", {"terminalId": m.get("terminalId")})

    async def on_terminal_input(self, conn: Conn, m: dict[str, Any]) -> None:
        term = self.live_terminal(m.get("terminalId"))
        message = m.get("message") if isinstance(m.get("message"), dict) else {}
        if term is None:
            return
        try:
            if message.get("type") == "input" and isinstance(message.get("data"), str):
                os.write(term.master, message["data"].encode("utf-8"))
            elif message.get("type") == "resize":
                rows, cols = message.get("rows"), message.get("cols")
                if isinstance(rows, int) and isinstance(cols, int) and rows > 0 and cols > 0:
                    fcntl.ioctl(term.master, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))
        except OSError:
            pass

    async def on_kill_terminal_request(self, conn: Conn, m: dict[str, Any]) -> None:
        term = self.live_terminal(m.get("terminalId"))
        if term is None:
            await self.reply(conn, m, "kill_terminal_response", {"terminalId": m.get("terminalId"), "success": False})
            return
        await self.loop.run_in_executor(None, kill_exact, term.proc)
        await self.reply(conn, m, "kill_terminal_response", {"terminalId": term.id, "success": True})

    async def on_terminal_rename_request(self, conn: Conn, m: dict[str, Any]) -> None:
        term = self.live_terminal(m.get("terminalId"))
        title = m.get("title")
        ok = term is not None and isinstance(title, str) and bool(title.strip())
        if ok:
            term.title = title.strip()
            self.publish_state()
        await self.reply(conn, m, "terminal.rename.response",
                         {"success": ok, "error": None if ok else "Terminal not found"})

    # -- update ----------------------------------------------------------------------

    async def on_daemon_update_request(self, conn: Conn, m: dict[str, Any]) -> None:
        rid = m.get("requestId")
        before = self.version
        if self.update_error:
            await conn.session("daemon.update.progress", {"requestId": rid, "phase": "starting"})
            await self.reply(conn, m, "daemon.update.response", {"success": False, "error": self.update_error,
                                                                 "previousVersion": before, "newVersion": None})
            return
        if self.latest == before:
            await self.reply(conn, m, "daemon.update.response", {"success": True, "error": None,
                                                                 "previousVersion": before, "newVersion": before})
            return
        for phase in ("starting", "downloading", "installing", "complete"):
            await conn.session("daemon.update.progress", {"requestId": rid, "phase": phase})
            await asyncio.sleep(0.15)
        self.version = self.latest
        await self.reply(conn, m, "daemon.update.response", {"success": True, "error": None,
                                                             "previousVersion": before, "newVersion": self.version})
        self.publish_state()
        await asyncio.sleep(0.2)
        await self.drop_all()  # the daemon restarts itself after it answers

    # -- control -----------------------------------------------------------------------

    async def drop_all(self) -> int:
        conns = list(self.conns)
        for conn in conns:
            conn.abort()
        self.conns.clear()
        self.publish_state()
        return len(conns)

    async def reset(self) -> None:
        for state in list(self.turns.values()):
            state["task"].cancel()
        self.turns.clear()
        await self.drop_all()
        await self.loop.run_in_executor(None, self.kill_terminals)
        self.seed()
        self.seed_terminals()
        self.publish_state()

    async def emit_terminal(self, tid: str, text: str, split: bool) -> None:
        term = self.live_terminal(tid)
        if term is None:
            raise ValueError("no such running terminal")
        data = text.encode("utf-8")
        cut = next((i + 1 for i, b in enumerate(data) if b >= 0xC0), None) if split else None
        for chunk in ([data[:cut], data[cut:]] if cut else [data]):
            await self.term_output(term, chunk)
            await asyncio.sleep(0.05)

    async def start(self) -> None:
        self.server = await asyncio.start_server(self.client, LOOPBACK, self.fx.ports["daemon"],
                                                 limit=MAX_FRAME + 8192)

    def call(self, coro: Any, timeout: float = 15.0) -> Any:
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(timeout)


# --------------------------------------------------------------------------- real daemon


class Proxy:
    """Byte proxy daemon port -> real daemon, so sockets can be dropped on demand."""

    def __init__(self, fx: Fixture, upstream: int) -> None:
        self.fx = fx
        self.upstream = upstream
        self.loop = asyncio.new_event_loop()
        self.pairs: set[tuple[asyncio.StreamWriter, asyncio.StreamWriter]] = set()

    async def pipe(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        try:
            while data := await reader.read(65536):
                writer.write(data)
                await writer.drain()
        except (ConnectionError, RuntimeError):
            pass
        finally:
            writer.close()

    async def client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        try:
            up_reader, up_writer = await asyncio.open_connection(LOOPBACK, self.upstream)
        except OSError:
            writer.transport.abort()
            return
        pair = (writer, up_writer)
        self.pairs.add(pair)
        self.fx.log.write(method="proxy/connect", status=200, transport="tcp")
        await asyncio.gather(self.pipe(reader, up_writer), self.pipe(up_reader, writer))
        self.pairs.discard(pair)

    async def drop_all(self) -> int:
        n = len(self.pairs)
        for a, b in list(self.pairs):
            a.transport.abort()
            b.transport.abort()
        self.pairs.clear()
        return n

    async def start(self) -> None:
        await asyncio.start_server(self.client, LOOPBACK, self.fx.ports["daemon"])

    def call(self, coro: Any, timeout: float = 15.0) -> Any:
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(timeout)


def start_real(fx: Fixture, binary: str, home: Path, bin_dir: Path, password: str) -> tuple[Proxy, int]:
    paseo_home = fx.dir / "paseo-home"
    paseo_home.mkdir(parents=True, exist_ok=True)
    upstream = free_port()
    env = {"HOME": str(home), "PATH": f"{bin_dir}:{os.environ.get('PATH', '/usr/bin:/bin')}",
           "LANG": "C.UTF-8", "DISABLE_AUTOUPDATER": "1", "PASEO_DICTATION_ENABLED": "false",
           "PASEO_VOICE_MODE_ENABLED": "false", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "1"}
    for key, value in (("daemon.listen", f"{LOOPBACK}:{upstream}"), ("daemon.relay.enabled", "false"),
                       ("daemon.mcp.injectIntoAgents", "false"), ("features.webUi.enabled", "false"),
                       ("features.dictation.enabled", "false"), ("features.voiceMode.enabled", "false")):
        done = subprocess.run([binary, "daemon", "config", "set", key, value, "--home", str(paseo_home)],
                              env=env, capture_output=True, timeout=60)
        if done.returncode != 0:
            raise RuntimeError(f"paseo daemon config set {key} failed ({done.returncode})")
    run_env = dict(env)
    if password:
        run_env["PASEO_PASSWORD"] = password
    fx.spawn([binary, "daemon", "run", "--home", str(paseo_home)], env=run_env, cwd=str(home),
             log_name="paseo-daemon.log")
    if not wait_for_port(upstream, timeout=50):
        raise RuntimeError("the real paseo daemon did not listen")
    proxy = Proxy(fx, upstream)
    return proxy, upstream


# --------------------------------------------------------------------------- setup


def run_loop(loop: asyncio.AbstractEventLoop, start: Any, started: threading.Event) -> None:
    asyncio.set_event_loop(loop)
    loop.run_until_complete(start())
    started.set()
    loop.run_forever()


def setup(fx: Fixture) -> dict[str, Any]:
    mode = fx.args.mode or os.environ.get("FX_PASEO_MODE") or "auto"
    if mode not in ("auto", "stub", "real"):
        raise ValueError("mode must be auto, stub or real")
    password = os.environ.get("FX_PASEO_PASSWORD", "")
    SCRUB.add(password)
    binary = os.environ.get("FX_PASEO_BIN") or shutil.which("paseo") or ""
    if mode == "auto":
        mode = "real" if binary and os.access(binary, os.X_OK) else "stub"
    if mode == "real" and not (binary and os.access(binary, os.X_OK)):
        raise RuntimeError("real mode needs FX_PASEO_BIN or paseo on PATH")

    folders = {"project": fx.dir / "project", "other": fx.dir / "other"}
    for key, folder in folders.items():
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "README.md").write_text(f"# Fixture {key}\n\nSynthetic Paseo project for FX-PASEO.\n",
                                          encoding="utf-8")
    (folders["project"] / "CHANGELOG.md").write_text("# Changelog\n\n## 1.2\n- faster start\n", encoding="utf-8")
    home = fx.dir / "home"
    bin_dir = fx.dir / "bin"
    bin_dir.mkdir(parents=True, exist_ok=True)
    for name, body in (("claude", FAKE_CLAUDE), ("pi", FAKE_PI)):
        script = bin_dir / name
        script.write_text(body, encoding="utf-8")
        script.chmod(0o755)
    history = write_history(home, folders)

    extra: dict[str, Any] = {
        "mode": mode, "url": f"ws://{LOOPBACK}:{fx.ports['daemon']}/ws",
        "emulator_url": f"ws://10.0.2.2:{fx.ports['daemon']}/ws",
        "project": str(folders["project"]), "other_project": str(folders["other"]),
        "home": str(home), "bin": str(bin_dir), "claude_history": history,
        "password_required": bool(password),
    }

    def stub_only(name: str) -> None:
        @fx.control(name)
        def _refuse(body: dict[str, Any]) -> Any:
            raise ValueError(f"{name} works in stub mode only")

    if mode == "real":
        proxy, upstream = start_real(fx, binary, home, bin_dir, password)
        started = threading.Event()
        threading.Thread(target=run_loop, args=(proxy.loop, proxy.start, started), daemon=True).start()
        if not started.wait(10):
            raise RuntimeError("proxy did not start")
        fx.at_exit(lambda: proxy.loop.call_soon_threadsafe(proxy.loop.stop))

        @fx.control("drop-socket")
        def _drop_real(body: dict[str, Any]) -> dict[str, Any]:
            return {"closed": proxy.call(proxy.drop_all())}

        for name in ("set-version", "set-provider", "archive-agent", "terminal-emit", "terminal-exit",
                     "fail-request"):
            stub_only(name)
        fx.set_state(mode="real")
        return {**extra, "upstream_port": upstream, "daemon_version": None}

    stub = Stub(fx, folders, home, password)
    stub.seed()
    started = threading.Event()
    threading.Thread(target=run_loop, args=(stub.loop, stub.start, started), name="FX-PASEO:daemon",
                     daemon=True).start()
    if not started.wait(10):
        raise RuntimeError("stub daemon did not start")
    stub.call(asyncio.sleep(0))
    stub.loop.call_soon_threadsafe(stub.seed_terminals)
    stub.call(asyncio.sleep(0.05))
    stub.publish_state()

    def _stop() -> None:
        try:
            stub.call(stub.drop_all(), timeout=5)
        except Exception:  # noqa: BLE001 - shutting down anyway
            pass
        stub.kill_terminals()
        stub.loop.call_soon_threadsafe(stub.loop.stop)

    fx.at_exit(_stop)
    fx.on_reset_hook(lambda: stub.call(stub.reset(), timeout=30))

    @fx.control("drop-socket")
    def _drop(body: dict[str, Any]) -> dict[str, Any]:
        return {"closed": stub.call(stub.drop_all())}

    @fx.control("set-version")
    def _version(body: dict[str, Any]) -> dict[str, Any]:
        for key in ("daemon", "latest"):
            if key in body and not (isinstance(body[key], str) and re.fullmatch(r"\d+\.\d+\.\d+", body[key])):
                raise ValueError(f"{key} must look like 0.11.2")
        if "fail" in body and body["fail"] is not None and not isinstance(body["fail"], str):
            raise ValueError("fail must be a string or null")

        async def _set() -> dict[str, Any]:
            stub.version = body.get("daemon", stub.version)
            stub.latest = body.get("latest", stub.latest)
            stub.update_error = body.get("fail") or None
            stub.publish_state()
            return {"daemon": stub.version, "latest": stub.latest, "fail": stub.update_error}
        return stub.call(_set())

    @fx.control("set-provider")
    def _provider(body: dict[str, Any]) -> dict[str, Any]:
        name = body.get("provider")
        if name not in stub.providers:
            raise ValueError("provider must be claude or pi")

        async def _set() -> dict[str, Any]:
            entry = stub.providers[name]
            for key in ("status", "error", "enabled"):
                if key in body:
                    entry[key] = body[key]
            await stub.broadcast("providers_snapshot_update", {"entries": list(stub.providers.values()),
                                                               "generatedAt": iso()})
            return {k: entry.get(k) for k in ("provider", "status", "error", "enabled")}
        return stub.call(_set())

    @fx.control("archive-agent")
    def _archive(body: dict[str, Any]) -> dict[str, Any]:
        if not stub.call(stub.archive(body.get("agentId"))):
            raise ValueError("no such live agent")
        return {"archived": body.get("agentId")}

    @fx.control("terminal-emit")
    def _emit(body: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(body.get("text"), str):
            raise ValueError("text is required")
        stub.call(stub.emit_terminal(body.get("terminalId"), body["text"], bool(body.get("split"))))
        return {"ok": True}

    @fx.control("terminal-exit")
    def _exit(body: dict[str, Any]) -> dict[str, Any]:
        term = stub.terminals.get(body.get("terminalId"))
        if term is None or term.exited:
            raise ValueError("no such running terminal")
        kill_exact(term.proc)
        return {"ok": True}

    @fx.control("fail-request")
    def _fail(body: dict[str, Any]) -> dict[str, Any]:
        type_ = body.get("type")
        if not isinstance(type_, str) or not type_:
            raise ValueError("type is required")
        error = body.get("error") if isinstance(body.get("error"), str) else "Synthetic daemon refusal"

        async def _set() -> None:
            if body.get("on", True):
                stub.failing[type_] = error
            else:
                stub.failing.pop(type_, None)
            stub.publish_state()
        stub.call(_set())
        return {"failing": sorted(stub.failing)}

    return {**extra, "daemon_version": stub.version}


def main() -> int:
    parser = base_parser(FIXTURE_ID)
    parser.add_argument("--mode", choices=("auto", "stub", "real"), default=None,
                        help="real paseo daemon, the Python stub, or auto (real when installed)")
    return run_fixture(FIXTURE_ID, setup, parser=parser)


if __name__ == "__main__":
    sys.exit(main())
