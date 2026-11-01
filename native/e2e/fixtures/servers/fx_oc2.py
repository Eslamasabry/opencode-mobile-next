#!/usr/bin/env python3
"""fx_oc2.py - FX-OC2: a real `opencode2 serve` behind a fault-injecting front proxy,
answering through the deterministic stub model (plan 14 §4.4; e2e-flows.md fixtures).

Started by fxctl.py (``python3 -I fx_oc2.py --run-dir DIR [--port name=N]``). Layout
under ``<run-dir>/FX-OC2/``:

    root/{home,data,config,state,cache,tmp}   HOME and XDG_* of the server (isolated)
    root/config/opencode/opencode.json        global config (provider, permissions)
    projects/app     git repo: README.md, notes.txt, data.csv, lib/main.dart, assets/icon.png
    projects/other   plain folder with files, no Git history
    opencode2.log    the server's output;  model.jsonl  the stub model's metadata log
    requests.jsonl   front proxy request log (metadata only)

Ports (ports.json): ``front`` (what the emulator uses), ``upstream`` (opencode2),
``model`` (stub model, in-process), ``ctl`` (control API).

Environment:
    FX_OC2_PASSWORD        per-run Basic password (user ``opencode``); required. Given to
                           the server as OPENCODE_PASSWORD; never on argv, never logged.
    FX_OC2_BIN             server binary (default ``opencode2`` on PATH). Missing -> exit 2.
    FX_OC2_PERMISSIONS     ``ask`` (default: asks first for edit and shell) or ``allow``.
    FX_OC2_SESSIONS        comma list: ``basic`` (always), ``long-history`` (400 messages),
                           ``perf-history`` (1,000 messages + one 5,000-part reply).
    FX_USAGE_PORT          where /ocmn/quota/* goes (default ports.json FX-USAGE http).
    FX_STUB_SPEED          stub model pace multiplier (see stub_model.py).
  (FX-OC2-B reads the same names with the FX_OC2_B_ prefix; FX_OC2_B_BIN falls back to
  FX_OC2_BIN.) CLI flags ``--permissions`` and ``--sessions`` override the env.

Front proxy: forwards every request to upstream byte for byte (Host rewritten to the
upstream address), streams responses without a length (SSE) chunk by chunk with a
flush per chunk, tunnels ``Upgrade`` requests (PTY WebSockets), applies the core
Faults (delay/refuse/hang/drop/cut; ``cut`` ends a streaming response after ``ms``).
``/ocmn/quota/*`` goes to FX-USAGE instead (404 when FX-USAGE is not listening).

Control actions (POST ctl/<name>, JSON body) besides the core ones:
    drop-streams                    close every open streaming response now -> {"dropped": n}
    streams                         -> {"open": n, "paths": [...]}
    lose-response {path, method?, count?}  forward to the server, then close the socket
                                    without the answer (the server did act on it)
    lose-clear                      remove lose-response rules (also done by /reset)
    prompt {session, text, delivery?}  send a prompt through the server with auth -> {"status"}
    sessions                        -> {"sessions": {key: id}, "projects": {...}}
    model-speed {speed}             stub pace (null = back to FX_STUB_SPEED)
    model-log                       -> {"requests": [...]} the stub model's metadata log
"""

from __future__ import annotations

import argparse
import base64
import http.client
import json
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable
from urllib.parse import quote

HERE = Path(__file__).resolve().parent
# python3 -I drops the script dir from sys.path: add it (stub_model, fx_oc2) and the
# fixtures root (fxlib) explicitly, as fxctl does.
for _p in (str(HERE), str(HERE.parent)):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import stub_model  # noqa: E402
from fxlib.core import (LOOPBACK, SCRUB, Fixture, FxHandler, base_parser, load_ports,  # noqa: E402
                        run_fixture, wait_for_port)

USER = "opencode"
PROVIDER_ID = "fx-stub"
# [U] OC2 2.1.1: the provider package id and settings keys are verified on 2.0.29 only
# (tool/qa/relswitch_permission_probe.py).
PROVIDER_PACKAGE = "@opencode/ai/providers/openai-compatible"
# [U] action names: "shell" is verified (probe), "edit" is the v1 name for file
# changes and "write" the OC2 tool name; unknown actions are expected to be inert.
ASK_ACTIONS = ("edit", "write", "shell")
READY_SECONDS = 90.0
GIT_DATE = "2026-10-01T09:00:00+00:00"
# 1x1 transparent PNG: the "binary" file of project app.
ICON_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")


def sid(tag: str, n: int) -> str:
    """A fixed session id: ``ses_`` + 26 characters, like the server's own."""
    body = f"fx{tag}"
    return "ses_" + body + str(n).zfill(26 - len(body))


def mid(n: int) -> str:
    return "msg_fx" + str(n).zfill(24)


@dataclass(frozen=True)
class SeedSession:
    key: str
    id: str
    title: str
    project: str = "app"


ISO_TITLE_A = "New session - 2026-11-01T09:30:00.000Z"
ISO_TITLE_B = "New session - 2026-10-31T18:05:00.000Z"
MARKUP_TITLE = '<tool_call>{"name":"bash"}</tool_call> Fix the tests'

# Shared ids: the same ids exist on FX-OC2 and FX-OC2-B (with different titles).
SHARED_IDS = {"shared-login": sid("shared", 1), "shared-notes": sid("shared", 2)}

OC2_SESSIONS = [
    SeedSession("shared-login", SHARED_IDS["shared-login"], "Fix the login bug"),
    SeedSession("shared-notes", SHARED_IDS["shared-notes"], "Write the release notes"),
    SeedSession("iso-title", sid("a", 1), ISO_TITLE_A),
    SeedSession("markup-title", sid("a", 2), MARKUP_TITLE),
    SeedSession("other-plan", sid("a", 3), "Plan the other project", "other"),
]
LONG_HISTORY = SeedSession("long-history", sid("long", 1), "Long history (400 messages)")
PERF_HISTORY = SeedSession("perf-history", sid("perf", 1), "perf:history-5000")
SESSION_SETS = ("basic", "long-history", "perf-history")


@dataclass
class Profile:
    fixture_id: str
    env_prefix: str
    sessions: list[SeedSession]
    projects: tuple[str, ...] = ("app", "other")
    label: str = "FX-OC2"

    def env(self, name: str, default: str | None = None) -> str | None:
        return os.environ.get(f"{self.env_prefix}_{name}") or default

    @property
    def password_env(self) -> str:
        return f"{self.env_prefix}_PASSWORD"


OC2 = Profile("FX-OC2", "FX_OC2", OC2_SESSIONS)


# --------------------------------------------------------------------------- seeding files


def _git(cwd: Path, *args: str) -> None:
    env = {"PATH": os.environ.get("PATH", "/usr/bin:/bin"), "HOME": str(cwd), "GIT_CONFIG_NOSYSTEM": "1",
           "GIT_AUTHOR_DATE": GIT_DATE, "GIT_COMMITTER_DATE": GIT_DATE, "LC_ALL": "C"}
    subprocess.run(["git", "-c", "user.name=fx", "-c", "user.email=fx@example.invalid",
                    "-c", "commit.gpgsign=false", "-c", "init.defaultBranch=main", *args],
                   cwd=cwd, env=env, check=True, capture_output=True)


def seed_projects(base: Path, names: tuple[str, ...]) -> dict[str, Path]:
    """Create the projects (idempotent: an existing project is left alone)."""
    out: dict[str, Path] = {}
    for name in names:
        path = base / name
        out[name] = path
        if path.exists():
            continue
        path.mkdir(parents=True)
        if name == "app":
            (path / "README.md").write_text(stub_model.README_MD, encoding="utf-8")
            (path / "notes.txt").write_text(stub_model.NOTES_TXT, encoding="utf-8")
            (path / "data.csv").write_text(stub_model.DATA_CSV, encoding="utf-8")
            (path / "lib").mkdir()
            (path / "lib" / "main.dart").write_text(stub_model.MAIN_DART, encoding="utf-8")
            (path / "assets").mkdir()
            (path / "assets" / "icon.png").write_bytes(ICON_PNG)
            _git(path, "init", "-q")
            # opencode.json holds per-run ports: keep it out of `git status`.
            with (path / ".git" / "info" / "exclude").open("a", encoding="utf-8") as handle:
                handle.write("opencode.json\n")
            _git(path, "add", "-A")
            _git(path, "commit", "-q", "-m", "Initial fixture commit")
        else:
            (path / "README.md").write_text(f"# {name}\n\nA second project with files but no Git history.\n",
                                            encoding="utf-8")
            (path / "todo.txt").write_text("- [ ] plan the work\n", encoding="utf-8")
    return out


def opencode_config(model_port: int, permissions: str) -> dict[str, Any]:
    """opencode.json for OC2: the stub provider and, in ``ask`` mode, asks-first rules.

    Verified on OC2 2.0.29 (relswitch_permission_probe.py): ``providers.<id>.package``,
    ``settings.baseURL``, ``settings.apiKey``, ``models.<id>.{name,limit,capabilities}``,
    rule shape ``{action, resource, effect}``.
    [U] the top-level ``model`` selection string and the top-level ``permissions``
    ruleset (the probe verified rules on agents and on sessions, not top level).
    """
    config: dict[str, Any] = {
        "model": f"{PROVIDER_ID}/{stub_model.MODEL_ID}",
        "providers": {PROVIDER_ID: {
            "name": "FX stub",
            "package": PROVIDER_PACKAGE,
            # The fake provider key: E2E-34 needs it registered as a provider key.
            "settings": {"baseURL": f"http://{LOOPBACK}:{model_port}/v1", "apiKey": stub_model.FAKE_KEY},
            "models": {stub_model.MODEL_ID: {"name": "FX stub model",
                                             "limit": {"context": 128000, "output": 8192},
                                             "capabilities": {"tools": True}}},
        }},
    }
    if permissions == "ask":
        config["permissions"] = [{"action": a, "resource": "*", "effect": "ask"} for a in ASK_ACTIONS]
    return config


# --------------------------------------------------------------------------- upstream API


class Upstream:
    def __init__(self, port: int, password: str) -> None:
        self.port = port
        self._auth = "Basic " + base64.b64encode(f"{USER}:{password}".encode()).decode()

    def call(self, method: str, path: str, body: Any = None, timeout: float = 30) -> tuple[int, Any]:
        conn = http.client.HTTPConnection(LOOPBACK, self.port, timeout=timeout)
        headers = {"Authorization": self._auth}
        raw = None
        if body is not None:
            raw = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        try:
            conn.request(method, path, body=raw, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
        finally:
            conn.close()
        try:
            return resp.status, json.loads(data) if data else None
        except ValueError:
            return resp.status, None


def wait_ready(proc: subprocess.Popen[Any], api: Upstream, log: Path, binary: str) -> None:
    deadline = time.monotonic() + READY_SECONDS
    while time.monotonic() < deadline:
        code = proc.poll()
        if code is not None:
            raise RuntimeError(f"{binary} exited with code {code} before it was ready; see {log}")
        if wait_for_port(api.port, timeout=0.3):
            try:
                status, _ = api.call("GET", "/api/health", timeout=5)
            except OSError:
                status = 0
            if status == 200:
                return
        time.sleep(0.2)
    raise RuntimeError(f"{binary} not healthy on port {api.port} after {READY_SECONDS:.0f}s; see {log}")


def _project_id(api: Upstream, directory: Path, known: dict[str, str], name: str) -> str:
    if known.get(name):
        return known[name]
    status, data = api.call("GET", "/api/location?" + quote("location[directory]") + "=" + quote(str(directory)))
    if status == 200 and isinstance(data, dict):
        data = data.get("data", data)
        project = data.get("project") if isinstance(data, dict) else None
        if isinstance(project, dict) and project.get("id"):
            return str(project["id"])
    raise RuntimeError(f"cannot resolve the project id of {name} (GET /api/location -> {status})")


def history_import(seed: SeedSession, directory: Path, project_id: str, kind: str) -> dict[str, Any]:
    """Body for POST /api/session/import [U: accepted shape on 2.1.1 not live-verified;
    built from the 18600 OpenAPI schema]."""
    t0 = 1790000000000
    messages: list[dict[str, Any]] = []
    model = {"id": stub_model.MODEL_ID, "providerID": PROVIDER_ID}
    n = 0

    def user(text: str) -> None:
        nonlocal n
        n += 1
        messages.append({"id": mid(n), "type": "user", "time": {"created": t0 + n * 1000}, "text": text})

    def assistant(content: list[dict[str, Any]]) -> None:
        nonlocal n
        n += 1
        messages.append({"id": mid(n), "type": "assistant", "time": {"created": t0 + n * 1000,
                         "completed": t0 + n * 1000 + 500}, "agent": "build", "model": model,
                         "content": content, "finish": "stop"})

    if kind == "long-history":
        for i in range(1, 201):  # 400 messages
            user(f"Question {i}: what is {i} plus {i}?")
            assistant([{"type": "text", "text": f"Answer {i}: {i} plus {i} is {2 * i}."}])
    else:  # perf-history: 1,000 plain messages and one reply of 5,000 parts
        for i in range(1, 501):
            user(f"Turn {i}: keep going.")
            assistant([{"type": "text", "text": f"Reply {i}: still going, step {i} of 500."}])
        user("Now the long fragmented reply.")
        assistant([{"type": "text", "text": f"part {i} "} for i in range(1, 5001)])
    t_last = t0 + (n + 1) * 1000
    info = {"id": seed.id, "projectID": project_id, "title": seed.title, "cost": 0,
            "tokens": {"input": 0, "output": 0, "reasoning": 0, "cache": {"read": 0, "write": 0}},
            "time": {"created": t0, "updated": t_last}, "location": {"directory": str(directory)},
            "agent": "build", "model": model}
    return {"info": info, "messages": messages, "location": {"directory": str(directory)}}


def seed_sessions(api: Upstream, profile: Profile, projects: dict[str, Path], sets: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    project_ids: dict[str, str] = {}
    for seed in profile.sessions:
        status, data = api.call("POST", "/api/session", {"id": seed.id, "title": seed.title,
                                                         "location": {"directory": str(projects[seed.project])}})
        if status == 200 and isinstance(data, dict):
            info = data.get("data") or {}
            out[seed.key] = info.get("id", seed.id)
            if info.get("projectID"):
                project_ids.setdefault(seed.project, info["projectID"])
        elif status == 409:  # restarted on the same data root: already there
            out[seed.key] = seed.id
        else:
            raise RuntimeError(f"seeding session {seed.key} failed: HTTP {status}")
    for kind, seed in (("long-history", LONG_HISTORY), ("perf-history", PERF_HISTORY)):
        if kind not in sets:
            continue
        pid = _project_id(api, projects["app"], project_ids, "app")
        status, data = api.call("POST", "/api/session/import",
                                history_import(seed, projects["app"], pid, kind), timeout=120)
        if status == 200 and isinstance(data, dict):
            out[seed.key] = (data.get("data") or {}).get("id", seed.id)
        elif status == 409:
            out[seed.key] = seed.id
        else:
            raise RuntimeError(f"importing session {seed.key} failed: HTTP {status}")
    return out


# --------------------------------------------------------------------------- front proxy

HOP_BY_HOP = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
              "trailers", "transfer-encoding", "upgrade", "host", "content-length", "proxy-connection"}
QUOTA_PREFIX = "/ocmn/quota/"


@dataclass
class ProxyState:
    upstream_port: int
    usage_port: int
    lock: threading.Lock = field(default_factory=threading.Lock)
    streams: dict[int, tuple[str, Callable[[], None]]] = field(default_factory=dict)
    lose_rules: list[dict[str, Any]] = field(default_factory=list)
    _next: int = 0

    def add_stream(self, path: str, kill: Callable[[], None]) -> int:
        with self.lock:
            self._next += 1
            self.streams[self._next] = (path, kill)
            return self._next

    def remove_stream(self, token: int) -> None:
        with self.lock:
            self.streams.pop(token, None)

    def drop_all(self) -> int:
        with self.lock:
            kills = list(self.streams.values())
        for _, kill in kills:
            kill()
        return len(kills)

    def open_paths(self) -> list[str]:
        with self.lock:
            return [p for p, _ in self.streams.values()]

    def add_lose(self, body: dict[str, Any]) -> dict[str, Any]:
        path = body.get("path")
        if not isinstance(path, str) or not path.startswith("/"):
            raise ValueError("path must be an absolute path prefix")
        rule = {"path": path}
        if body.get("method"):
            rule["method"] = str(body["method"]).upper()
        if "count" in body:
            rule["count"] = int(body["count"])
        with self.lock:
            self.lose_rules.append(rule)
        return rule

    def take_lose(self, method: str, path: str) -> bool:
        with self.lock:
            for rule in self.lose_rules:
                if not path.startswith(rule["path"]) or rule.get("method", method) != method:
                    continue
                if "count" in rule:
                    if rule["count"] <= 0:
                        continue
                    rule["count"] -= 1
                return True
        return False

    def clear_lose(self) -> None:
        with self.lock:
            self.lose_rules.clear()


def _shutdown(sock: socket.socket | None) -> None:
    if sock is None:
        return
    try:
        sock.shutdown(socket.SHUT_RDWR)
    except OSError:
        pass


class ProxyHandler(FxHandler):
    """Front port: forward to opencode2 (or FX-USAGE for /ocmn/quota/*)."""

    @property
    def state(self) -> ProxyState:
        return self.fixture.proxy  # type: ignore[attr-defined]

    def _request_body(self) -> bytes | None:
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            out = bytearray()
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    return bytes(out)
                out += self.rfile.read(size)
                self.rfile.readline()
        if self.headers.get("Content-Length") is not None:
            return self.body_bytes()
        return None

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        st = self.state
        target = st.upstream_port
        if path.startswith(QUOTA_PREFIX):
            target = st.usage_port
            self.note(upstream="fx-usage")
        if self.headers.get("Upgrade") and "upgrade" in (self.headers.get("Connection") or "").lower():
            return self._tunnel(target, path)
        body = self._request_body()
        lose = target == st.upstream_port and st.take_lose(method, path)
        conn = http.client.HTTPConnection(LOOPBACK, target, timeout=None)
        try:
            try:
                conn.putrequest(method, self.path, skip_host=True, skip_accept_encoding=True)
                for key, value in self.headers.items():
                    if key.lower() not in HOP_BY_HOP:
                        conn.putheader(key, value)
                conn.putheader("Host", f"{LOOPBACK}:{target}")
                if body is not None:
                    conn.putheader("Content-Length", str(len(body)))
                conn.endheaders(body if body else None)
                resp = conn.getresponse()
            except OSError:
                if target != st.upstream_port:  # FX-USAGE not running: "unsupported"
                    return self.send_json({"error": "not found"}, 404)
                self.note(upstream="unavailable")
                return self.send_json({"error": "fixture upstream unavailable"}, 502)
            if lose:
                resp.read()
                self.note(fault="lose-response")
                self.hard_close()
                return
            self._relay(resp, conn, method, path)
        finally:
            conn.close()

    def _relay(self, resp: http.client.HTTPResponse, conn: http.client.HTTPConnection, method: str, path: str) -> None:
        status = resp.status
        length = resp.getheader("Content-Length")
        ctype = (resp.getheader("Content-Type") or "").lower()
        no_body = method == "HEAD" or status in (204, 304) or 100 <= status < 200
        streaming = not no_body and (length is None or "text/event-stream" in ctype)
        self.send_response_only(status, resp.reason)
        for key, value in resp.getheaders():
            if key.lower() not in HOP_BY_HOP:
                self.send_header(key, value)
        self._status = status
        if not streaming:
            if length is not None:
                self.send_header("Content-Length", length)
            self.end_headers()
            if not no_body:
                while True:
                    data = resp.read1(65536)
                    if not data:
                        break
                    self.wfile.write(data)
            self.wfile.flush()
            return
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        self.wfile.flush()
        killed = threading.Event()

        def kill() -> None:
            killed.set()
            _shutdown(self.connection)
            _shutdown(conn.sock)

        token = self.state.add_stream(path, kill)
        timer = None
        if self.cut_after_ms is not None:
            timer = threading.Timer(self.cut_after_ms / 1000, kill)
            timer.daemon = True
            timer.start()
        self.note(stream=True)
        try:
            while True:
                data = resp.read1(65536)
                if not data:
                    break
                self.wfile.write(b"%x\r\n%s\r\n" % (len(data), data))
                self.wfile.flush()
            if not killed.is_set():
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
        except (OSError, http.client.HTTPException, ValueError):
            self.close_connection = True
        finally:
            if timer:
                timer.cancel()
            self.state.remove_stream(token)
        if killed.is_set():
            self.close_connection = True
            self.note(dropped=True)
            self._status = status

    def _tunnel(self, target: int, path: str) -> None:
        try:
            up = socket.create_connection((LOOPBACK, target), timeout=10)
        except OSError:
            return self.send_json({"error": "fixture upstream unavailable"}, 502)
        up.settimeout(None)
        lines = [f"{self.command} {self.path} {self.request_version}"]
        lines += [f"{k}: {v}" for k, v in self.headers.items() if k.lower() != "host"]
        lines.append(f"Host: {LOOPBACK}:{target}")
        up.sendall(("\r\n".join(lines) + "\r\n\r\n").encode("latin-1"))
        client = self.connection
        done = threading.Event()

        def kill() -> None:
            done.set()
            _shutdown(client)
            _shutdown(up)

        token = self.state.add_stream(path, kill)
        first = bytearray()

        def up_to_client() -> None:
            try:
                while True:
                    data = up.recv(65536)
                    if not data:
                        break
                    if len(first) < 16:
                        first.extend(data[:16])
                    client.sendall(data)
            except OSError:
                pass
            kill()

        reader = threading.Thread(target=up_to_client, daemon=True)
        reader.start()
        try:
            while not done.is_set():
                data = self.rfile.read1(65536)
                if not data:
                    break
                up.sendall(data)
        except OSError:
            pass
        kill()
        reader.join(5)
        up.close()
        self.state.remove_stream(token)
        self.close_connection = True
        self.note(upgrade=True)
        try:
            self._status = int(bytes(first).split(b" ")[1])
        except (IndexError, ValueError):
            self._status = 0


# --------------------------------------------------------------------------- fixture


def build_parser(profile: Profile) -> argparse.ArgumentParser:
    parser = base_parser(f"{profile.fixture_id}: opencode2 + stub model + front proxy")
    parser.add_argument("--permissions", choices=["ask", "allow"], default=profile.env("PERMISSIONS", "ask"),
                        help="ask = asks first for edit and shell (default); allow = no asks")
    parser.add_argument("--sessions", default=profile.env("SESSIONS", "basic"),
                        help=f"comma list of {', '.join(SESSION_SETS)}")
    return parser


def resolve_binary(profile: Profile) -> str:
    name = profile.env("BIN") or os.environ.get("FX_OC2_BIN") or "opencode2"
    if os.sep in name:
        found = name if os.path.isfile(name) and os.access(name, os.X_OK) else None
    else:
        found = shutil.which(name)
    if not found:
        names = sorted({f"{profile.env_prefix}_BIN", "FX_OC2_BIN"}, key=len, reverse=True)
        raise RuntimeError(f"OpenCode 2 server binary not found: {name!r}. Install the pinned opencode2 "
                           f"binary or set {' or '.join(names)} to its path.")
    return found


def usage_port() -> int:
    value = os.environ.get("FX_USAGE_PORT")
    if value and value.isdigit():
        return int(value)
    try:
        return load_ports("FX-USAGE")["http"]
    except (KeyError, OSError, ValueError):
        return 8703


def make_setup(profile: Profile) -> Callable[[Fixture], dict[str, Any]]:
    def setup(fx: Fixture) -> dict[str, Any]:
        args = fx.args
        sets = [s.strip() for s in str(args.sessions).split(",") if s.strip()]
        unknown = [s for s in sets if s not in SESSION_SETS]
        if unknown:
            raise RuntimeError(f"unknown session set(s) {unknown}; known: {list(SESSION_SETS)}")
        password = os.environ.get(profile.password_env) or ""
        if not password:
            raise RuntimeError(f"{profile.password_env} is not set (fxctl.py sets it per run)")
        SCRUB.add(password)
        binary = resolve_binary(profile)

        root = fx.dir / "root"
        dirs = {name: root / name for name in ("home", "data", "config", "state", "cache", "tmp")}
        for path in dirs.values():
            path.mkdir(parents=True, exist_ok=True)
        projects = seed_projects(fx.dir / "projects", profile.projects)
        config = json.dumps(opencode_config(fx.ports["model"], args.permissions), indent=1)
        (dirs["config"] / "opencode").mkdir(exist_ok=True)
        (dirs["config"] / "opencode" / "opencode.json").write_text(config, encoding="utf-8")
        for path in projects.values():
            (path / "opencode.json").write_text(config, encoding="utf-8")

        stub = stub_model.make_server(fx.ports["model"], log_path=fx.dir / "model.jsonl",
                                      default_dir=str(projects["app"]))
        fx.serve("model", stub_model.StubHandler, server=stub)

        env = {k: os.environ[k] for k in ("PATH", "LANG", "LC_ALL", "TZ") if k in os.environ}
        env.update(HOME=str(dirs["home"]), XDG_DATA_HOME=str(dirs["data"]), XDG_CONFIG_HOME=str(dirs["config"]),
                   XDG_STATE_HOME=str(dirs["state"]), XDG_CACHE_HOME=str(dirs["cache"]), TMPDIR=str(dirs["tmp"]),
                   OPENCODE_PASSWORD=password)
        upstream = fx.ports["upstream"]
        log_path = fx.dir / "opencode2.log"
        child = fx.spawn([binary, "serve", "--port", str(upstream), "--hostname", LOOPBACK],
                         env=env, cwd=projects["app"], log_name=log_path.name)
        api = Upstream(upstream, password)
        wait_ready(child, api, log_path, Path(binary).name)
        sessions = seed_sessions(api, profile, projects, sets)

        proxy = ProxyState(upstream, usage_port())
        fx.proxy = proxy  # type: ignore[attr-defined]

        @fx.control("drop-streams")
        def _drop(_body: dict[str, Any]) -> Any:
            return {"dropped": proxy.drop_all()}

        @fx.control("streams")
        def _streams(_body: dict[str, Any]) -> Any:
            paths = proxy.open_paths()
            return {"open": len(paths), "paths": paths}

        @fx.control("lose-response")
        def _lose(body: dict[str, Any]) -> Any:
            return {"added": proxy.add_lose(body)}

        @fx.control("lose-clear")
        def _lose_clear(_body: dict[str, Any]) -> Any:
            proxy.clear_lose()
            return {"ok": True}

        @fx.control("prompt")
        def _prompt(body: dict[str, Any]) -> Any:
            session, text = body.get("session"), body.get("text")
            if not isinstance(session, str) or not session.startswith("ses") or not isinstance(text, str):
                raise ValueError("body needs session (ses_...) and text")
            payload: dict[str, Any] = {"text": text}
            if body.get("delivery") in ("steer", "queue"):
                payload["delivery"] = body["delivery"]
            status, data = api.call("POST", f"/api/session/{quote(session)}/prompt", payload)
            item = (data or {}).get("data") if isinstance(data, dict) else None
            return {"status": status, "id": item.get("id") if isinstance(item, dict) else None}

        @fx.control("sessions")
        def _sessions(_body: dict[str, Any]) -> Any:
            return {"sessions": sessions, "projects": {k: str(v) for k, v in projects.items()}}

        @fx.control("model-speed")
        def _speed(body: dict[str, Any]) -> Any:
            speed = body.get("speed")
            if speed is not None and (not isinstance(speed, (int, float)) or speed <= 0):
                raise ValueError("speed must be a positive number or null")
            stub.speed = speed
            return {"speed": stub.current_speed()}

        @fx.control("model-log")
        def _model_log(_body: dict[str, Any]) -> Any:
            return {"requests": stub.read_log()}

        def _reset() -> None:
            proxy.clear_lose()
            stub.speed = None
            stub.clear_log()

        fx.on_reset_hook(_reset)
        fx.serve("front", ProxyHandler)
        fx.set_state(upstream_pid=child.pid, sessions=sessions, permissions=args.permissions)
        return {
            "front_url": f"http://{LOOPBACK}:{fx.ports['front']}",
            "model_url": f"http://{LOOPBACK}:{fx.ports['model']}/v1",
            "user": USER,
            "password_env": profile.password_env,
            "server_binary": Path(binary).name,
            "upstream_pid": child.pid,
            "data_root": str(root),
            "projects": {k: str(v) for k, v in projects.items()},
            "sessions": sessions,
            "shared_session_ids": sorted(v for k, v in sessions.items() if k in SHARED_IDS),
            "session_sets": ["basic"] + [s for s in sets if s != "basic"],
            "permissions": args.permissions,
            "provider": {"id": PROVIDER_ID, "model": stub_model.MODEL_ID},
            "usage_port": proxy.usage_port,
        }

    return setup


def main(profile: Profile = OC2, argv: list[str] | None = None) -> int:
    return run_fixture(profile.fixture_id, make_setup(profile), build_parser(profile), argv)


if __name__ == "__main__":
    sys.exit(main())
