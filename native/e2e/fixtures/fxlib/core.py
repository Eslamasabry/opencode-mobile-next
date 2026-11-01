"""Shared plumbing for the FX-* fixture servers (plan 14 §4.4).

Python 3.10+ standard library only. Every server:

* binds 127.0.0.1 only (the emulator reaches the PC as 10.0.2.2);
* takes its ports from ``ports.json`` (overridable with ``--port name=NUMBER``
  so tests can run on free ports in parallel);
* writes ``<run-dir>/<ID>/ready.json`` once it listens, and removes it on exit;
* appends one JSON line per request to ``<run-dir>/<ID>/requests.jsonl``
  holding metadata only (method, path without query, status, which auth
  scheme was present, transport) and never a credential, body or query;
* exposes a control API on its ``ctl`` port: ``GET /health``, ``GET /log``,
  ``GET /state``, ``POST /reset``, ``POST /fault``, ``POST /faults/clear`` and
  ``POST /release`` plus fixture-specific actions;
* exits on SIGTERM/SIGINT after stopping the children it started, each by its
  exact PID (never by pattern: the PC also runs the owner's live server).
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import secrets
import signal
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable
from urllib.parse import parse_qs, urlsplit

FIXTURES_ROOT = Path(__file__).resolve().parent.parent
PORTS_FILE = FIXTURES_ROOT / "ports.json"
LOOPBACK = "127.0.0.1"


# --------------------------------------------------------------------------- ports


def load_ports(fixture_id: str, overrides: dict[str, int] | None = None) -> dict[str, int]:
    """The port map of one fixture from ports.json, with overrides applied."""
    table = json.loads(PORTS_FILE.read_text(encoding="utf-8"))
    if fixture_id not in table:
        raise KeyError(f"{fixture_id} is not in ports.json")
    ports = {k: int(v) for k, v in table[fixture_id].items() if not k.startswith("_")}
    for key, value in (overrides or {}).items():
        ports[key] = int(value)
    return ports


def free_port() -> int:
    with socket.socket() as s:
        s.bind((LOOPBACK, 0))
        return s.getsockname()[1]


def wait_for_port(port: int, timeout: float = 30.0, host: str = LOOPBACK) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.5):
                return True
        except OSError:
            time.sleep(0.1)
    return False


# --------------------------------------------------------------------------- secrets


class Scrubber:
    """Replaces every registered secret with <redacted> in text written to logs."""

    def __init__(self) -> None:
        self._secrets: set[str] = set()
        self._lock = threading.Lock()

    def add(self, *values: str | None) -> None:
        with self._lock:
            for value in values:
                if value and len(value) >= 4:
                    self._secrets.add(value)
                    # The Basic header form and URL-safe base64 of opencode:<pw> too.
                    raw = f"opencode:{value}".encode()
                    self._secrets.add(base64.b64encode(raw).decode())
                    self._secrets.add(base64.urlsafe_b64encode(raw).decode().rstrip("="))

    def __call__(self, text: str) -> str:
        with self._lock:
            for value in sorted(self._secrets, key=len, reverse=True):
                text = text.replace(value, "<redacted>")
        return text


SCRUB = Scrubber()


def new_password() -> str:
    return secrets.token_urlsafe(24)


# --------------------------------------------------------------------------- logs


class RequestLog:
    """Append-only JSON-lines request log. Metadata only; scrubbed again on write."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def write(self, **entry: Any) -> None:
        entry.setdefault("t", round(time.time(), 3))
        line = SCRUB(json.dumps(entry, sort_keys=True))
        with self._lock, self.path.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    def read(self) -> list[dict[str, Any]]:
        if not self.path.exists():
            return []
        with self._lock:
            lines = self.path.read_text(encoding="utf-8").splitlines()
        return [json.loads(line) for line in lines if line.strip()]

    def clear(self) -> None:
        with self._lock:
            self.path.write_text("", encoding="utf-8")


def auth_scheme(headers: Any) -> str:
    """Which auth scheme a request carried, never its value."""
    value = headers.get("Authorization") or ""
    if not value:
        return "none"
    return value.split(" ", 1)[0].lower()


def basic_password(headers: Any) -> tuple[str, str] | None:
    value = headers.get("Authorization") or ""
    if not value.lower().startswith("basic "):
        return None
    try:
        user, _, password = base64.b64decode(value[6:].strip()).decode("utf-8").partition(":")
    except (ValueError, UnicodeDecodeError):
        return None
    return user, password


# --------------------------------------------------------------------------- faults


class Faults:
    """Fault rules set through the control API and applied by handlers.

    A rule: {"path": "/api/event", "method": "GET"?, "action": ..., "ms": int?,
    "status": int?, "count": int?}. ``path`` is a prefix of the request path.
    Actions:
      delay   sleep ``ms`` (default 3000) then serve normally;
      refuse  answer ``status`` (default 503) with a short JSON body;
      hang    hold the request until ``ms`` (default 120000) passes or
              POST /release, then close the socket without an answer
              (a pending request that times out);
      drop    close the socket at once without an answer;
      cut     serve, but close the connection after ``ms`` (default 1000)
              (handlers that stream honour it; e.g. drop an SSE stream).
    ``count`` limits how many requests the rule applies to (default: all).
    """

    ACTIONS = {"delay", "refuse", "hang", "drop", "cut"}

    def __init__(self) -> None:
        self._rules: list[dict[str, Any]] = []
        self._lock = threading.Lock()
        self.release = threading.Event()

    def add(self, rule: dict[str, Any]) -> dict[str, Any]:
        if rule.get("action") not in self.ACTIONS:
            raise ValueError(f"action must be one of {sorted(self.ACTIONS)}")
        if not isinstance(rule.get("path"), str) or not rule["path"].startswith("/"):
            raise ValueError("path must be an absolute path prefix")
        clean = {k: rule[k] for k in ("path", "method", "action", "ms", "status", "count") if k in rule}
        with self._lock:
            self._rules.append(clean)
        self.release.clear()
        return clean

    def clear(self) -> None:
        with self._lock:
            self._rules.clear()
        self.release.set()

    def rules(self) -> list[dict[str, Any]]:
        with self._lock:
            return [dict(r) for r in self._rules]

    def match(self, method: str, path: str) -> dict[str, Any] | None:
        with self._lock:
            for rule in self._rules:
                if not path.startswith(rule["path"]):
                    continue
                if rule.get("method") and rule["method"].upper() != method.upper():
                    continue
                if "count" in rule:
                    if rule["count"] <= 0:
                        continue
                    rule["count"] -= 1
                return dict(rule)
        return None


# --------------------------------------------------------------------------- HTTP


class FxServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, port: int, handler: type[BaseHTTPRequestHandler], fixture: "Fixture") -> None:
        self.fixture = fixture
        super().__init__((LOOPBACK, port), handler)


class FxHandler(BaseHTTPRequestHandler):
    """Base handler: JSON helpers, metadata request log, fault rules.

    Subclasses implement ``route(method, path, query)`` and may call the helpers.
    ``self.fixture`` is the owning Fixture; ``self.transport`` is "http" unless a
    TLS server sets it.
    """

    protocol_version = "HTTP/1.1"
    server_version = "fx"
    sys_version = ""
    transport = "http"
    log_requests = True
    apply_faults = True

    # quiet the default stderr access log; the request log replaces it
    def log_message(self, fmt: str, *args: Any) -> None:  # noqa: D401
        return

    @property
    def fixture(self) -> "Fixture":
        return self.server.fixture  # type: ignore[attr-defined]

    # -- helpers ---------------------------------------------------------------

    def body_bytes(self) -> bytes:
        if hasattr(self, "_body"):
            return self._body
        length = int(self.headers.get("Content-Length") or 0)
        self._body = self.rfile.read(length) if length else b""
        return self._body

    def body_json(self) -> Any:
        raw = self.body_bytes()
        if not raw:
            return {}
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return None

    def body_form(self) -> dict[str, str]:
        ctype = self.headers.get("Content-Type", "")
        raw = self.body_bytes()
        if "json" in ctype:
            data = self.body_json()
            return {k: str(v) for k, v in data.items()} if isinstance(data, dict) else {}
        return {k: v[0] for k, v in parse_qs(raw.decode("utf-8", "replace")).items()}

    def send_json(self, obj: Any, status: int = 200, headers: dict[str, str] | None = None) -> None:
        raw = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)
        self._status = status

    def send_text(self, text: str, status: int = 200, ctype: str = "text/plain; charset=utf-8") -> None:
        raw = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)
        self._status = status

    def send_empty(self, status: int = 204, headers: dict[str, str] | None = None) -> None:
        self.send_response(status)
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.send_header("Content-Length", "0")
        self.end_headers()
        self._status = status

    def hard_close(self) -> None:
        """Close the socket without an answer (RST where possible)."""
        self.close_connection = True
        try:
            self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, b"\x01\x00\x00\x00\x00\x00\x00\x00")
        except OSError:
            pass
        try:
            self.connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self._status = 0

    def log_extra(self) -> dict[str, Any]:
        """Extra metadata for this request's log line (subclasses)."""
        return getattr(self, "_extra", {})

    def note(self, **extra: Any) -> None:
        self._extra = {**getattr(self, "_extra", {}), **extra}

    # -- dispatch --------------------------------------------------------------

    def _dispatch(self) -> None:
        split = urlsplit(self.path)
        path, query = split.path, {k: v[0] for k, v in parse_qs(split.query).items()}
        # The handler object lives for the whole keep-alive connection: reset
        # per-request state so a POST after a GET does not see the GET's body.
        self.__dict__.pop("_body", None)
        self._extra = {}
        self._status = None
        started = time.monotonic()
        self.cut_after_ms: int | None = None
        rule = self.fixture.faults.match(self.command, path) if self.apply_faults else None
        try:
            if rule:
                self.note(fault=rule["action"])
                action = rule["action"]
                if action == "delay":
                    time.sleep(rule.get("ms", 3000) / 1000)
                elif action == "refuse":
                    status = int(rule.get("status", 503))
                    self.send_json({"error": "refused by fixture"}, status)
                    return
                elif action == "hang":
                    self.fixture.faults.release.wait(rule.get("ms", 120000) / 1000)
                    self.hard_close()
                    return
                elif action == "drop":
                    self.hard_close()
                    return
                elif action == "cut":
                    self.cut_after_ms = int(rule.get("ms", 1000))
            self.route(self.command, path, query)
            if self._status is None:
                self._status = 0
        except (BrokenPipeError, ConnectionResetError):
            self._status = self._status or 0
            self.close_connection = True
        finally:
            if self.log_requests:
                self.fixture.log.write(
                    method=self.command,
                    path=path,
                    status=self._status,
                    auth=auth_scheme(self.headers),
                    transport=self.transport,
                    ms=int((time.monotonic() - started) * 1000),
                    **self.log_extra(),
                )

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        self.send_json({"error": "not found"}, 404)

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = _dispatch  # type: ignore[assignment]


class ControlHandler(FxHandler):
    """The control API on the ``ctl`` port. Fixtures add actions with
    ``Fixture.control(name)``; each receives the JSON body and returns a JSON-able
    value (or raises ValueError for a 400)."""

    log_requests = False
    apply_faults = False

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        fx = self.fixture
        if method == "GET" and path == "/health":
            return self.send_json({"id": fx.fixture_id, "pid": os.getpid(), "ports": fx.ports, "state": fx.state()})
        if method == "GET" and path == "/state":
            return self.send_json(fx.state())
        if method == "GET" and path == "/log":
            return self.send_json({"requests": fx.log.read()})
        if method == "GET" and path == "/faults":
            return self.send_json({"faults": fx.faults.rules()})
        if method == "POST" and path == "/fault":
            body = self.body_json()
            try:
                return self.send_json({"added": fx.faults.add(body if isinstance(body, dict) else {})})
            except ValueError as err:
                return self.send_json({"error": str(err)}, 400)
        if method == "POST" and path == "/faults/clear":
            fx.faults.clear()
            return self.send_json({"ok": True})
        if method == "POST" and path == "/release":
            fx.faults.release.set()
            return self.send_json({"ok": True})
        if method == "POST" and path == "/log/clear":
            fx.log.clear()
            return self.send_json({"ok": True})
        if method == "POST" and path == "/reset":
            fx.faults.clear()
            fx.log.clear()
            fx.on_reset()
            return self.send_json({"ok": True})
        action = fx.actions.get(path.strip("/"))
        if action and method == "POST":
            body = self.body_json()
            try:
                result = action(body if isinstance(body, dict) else {})
            except ValueError as err:
                return self.send_json({"error": str(err)}, 400)
            return self.send_json(result if result is not None else {"ok": True})
        self.send_json({"error": "unknown control", "actions": sorted(fx.actions)}, 404)


# --------------------------------------------------------------------------- fixture


class Fixture:
    """One running fixture: ports, run dir, log, faults, control actions, children."""

    def __init__(self, fixture_id: str, args: argparse.Namespace) -> None:
        self.fixture_id = fixture_id
        self.args = args
        self.run_dir = Path(args.run_dir).resolve()
        self.dir = self.run_dir / fixture_id
        self.dir.mkdir(parents=True, exist_ok=True)
        self.ports = load_ports(fixture_id, args.port_overrides)
        self.log = RequestLog(self.dir / "requests.jsonl")
        self.faults = Faults()
        self.actions: dict[str, Callable[[dict[str, Any]], Any]] = {}
        self.servers: list[ThreadingHTTPServer] = []
        self.children: list[subprocess.Popen[Any]] = []
        self.stopping = threading.Event()
        self._reset_hooks: list[Callable[[], None]] = []
        self._state: dict[str, Any] = {}
        self._cleanups: list[Callable[[], None]] = []

    # state shown by GET /health
    def state(self) -> dict[str, Any]:
        return dict(self._state)

    def set_state(self, **values: Any) -> None:
        self._state.update(values)

    def control(self, name: str) -> Callable[[Callable[[dict[str, Any]], Any]], Callable[[dict[str, Any]], Any]]:
        def deco(fn: Callable[[dict[str, Any]], Any]) -> Callable[[dict[str, Any]], Any]:
            self.actions[name] = fn
            return fn
        return deco

    def on_reset_hook(self, fn: Callable[[], None]) -> None:
        self._reset_hooks.append(fn)

    def on_reset(self) -> None:
        for hook in self._reset_hooks:
            hook()

    def at_exit(self, fn: Callable[[], None]) -> None:
        self._cleanups.append(fn)

    def serve(self, port_name: str, handler: type[BaseHTTPRequestHandler], server: ThreadingHTTPServer | None = None) -> ThreadingHTTPServer:
        """Start an HTTP server for ``ports[port_name]`` on a daemon thread."""
        srv = server or FxServer(self.ports[port_name], handler, self)
        thread = threading.Thread(target=srv.serve_forever, name=f"{self.fixture_id}:{port_name}", daemon=True)
        thread.start()
        self.servers.append(srv)
        return srv

    def spawn(self, argv: list[str], *, env: dict[str, str] | None = None, cwd: str | Path | None = None,
              log_name: str | None = None) -> subprocess.Popen[Any]:
        """Start a child process and record its exact PID in children.json."""
        log_path = self.dir / (log_name or f"child-{len(self.children)}.log")
        out = log_path.open("ab")
        proc = subprocess.Popen(argv, env=env, cwd=cwd, stdout=out, stderr=subprocess.STDOUT,
                                stdin=subprocess.DEVNULL)
        self.children.append(proc)
        self._write_children()
        return proc

    def adopt(self, proc: subprocess.Popen[Any]) -> subprocess.Popen[Any]:
        """Track a child started elsewhere (e.g. on a PTY) by its exact PID."""
        self.children.append(proc)
        self._write_children()
        return proc

    def _write_children(self) -> None:
        rows = [{"pid": p.pid, "argv0": Path(str(p.args[0])).name if isinstance(p.args, list) else str(p.args)}
                for p in self.children if p.poll() is None]
        (self.dir / "children.json").write_text(json.dumps(rows), encoding="utf-8")

    def stop_children(self, timeout: float = 5.0) -> None:
        for proc in reversed(self.children):
            stop_pid(proc.pid, timeout=timeout, proc=proc)
        self._write_children()

    def ready(self, **extra: Any) -> None:
        info = {"id": self.fixture_id, "pid": os.getpid(), "ports": self.ports, **extra}
        tmp = self.dir / "ready.json.tmp"
        tmp.write_text(json.dumps(info, indent=1), encoding="utf-8")
        tmp.replace(self.dir / "ready.json")

    def shutdown(self) -> None:
        if self.stopping.is_set():
            return
        self.stopping.set()
        self.faults.release.set()
        (self.dir / "ready.json").unlink(missing_ok=True)
        for fn in reversed(self._cleanups):
            try:
                fn()
            except Exception as err:  # noqa: BLE001 - cleanup must go on
                print(SCRUB(f"cleanup failed: {err!r}"), file=sys.stderr)
        self.stop_children()
        for srv in self.servers:
            srv.shutdown()
            srv.server_close()


def stop_pid(pid: int, timeout: float = 5.0, proc: subprocess.Popen[Any] | None = None) -> bool:
    """SIGTERM one exact PID, wait, then SIGKILL that same PID. True if gone."""
    def alive() -> bool:
        if proc is not None:
            return proc.poll() is None
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return False
        except PermissionError:
            return True
        # a zombie child of someone else counts as gone
        try:
            stat = Path(f"/proc/{pid}/stat").read_text()
            return stat.rsplit(")", 1)[1].split()[0] != "Z"
        except OSError:
            return True

    if not alive():
        return True
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        return True
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not alive():
            return True
        time.sleep(0.05)
    try:
        os.kill(pid, signal.SIGKILL)
    except ProcessLookupError:
        return True
    if proc is not None:
        try:
            proc.wait(2)
        except subprocess.TimeoutExpired:
            return False
    return not alive()


# --------------------------------------------------------------------------- main


def parse_port_override(text: str) -> tuple[str, int]:
    name, _, value = text.partition("=")
    if not name or not value.isdigit():
        raise argparse.ArgumentTypeError("use --port name=NUMBER")
    return name, int(value)


def base_parser(description: str) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--run-dir", required=True, help="per-run directory (logs, ready.json, state)")
    parser.add_argument("--port", action="append", default=[], type=parse_port_override,
                        help="override a port from ports.json: name=NUMBER (repeatable)")
    return parser


def run_fixture(fixture_id: str, setup: Callable[[Fixture], dict[str, Any] | None],
                parser: argparse.ArgumentParser | None = None, argv: list[str] | None = None) -> int:
    """Parse args, build the fixture with ``setup``, serve the control API, write
    ready.json, then block until SIGTERM/SIGINT and shut down cleanly.

    ``setup`` starts the fixture's own servers/children and may return extra
    fields for ready.json (never secrets)."""
    parser = parser or base_parser(fixture_id)
    args = parser.parse_args(argv)
    args.port_overrides = dict(args.port)
    fx = Fixture(fixture_id, args)
    done = threading.Event()

    def on_signal(signum: int, _frame: Any) -> None:
        done.set()

    signal.signal(signal.SIGTERM, on_signal)
    signal.signal(signal.SIGINT, on_signal)
    try:
        extra = setup(fx) or {}
        if "ctl" in fx.ports:
            fx.serve("ctl", ControlHandler)
        fx.ready(**extra)
        print(f"{fixture_id} ready pid={os.getpid()} ports={json.dumps(fx.ports, sort_keys=True)}", flush=True)
        while not done.wait(0.5):
            pass
    except Exception as err:  # noqa: BLE001 - report and stop children
        print(SCRUB(f"{fixture_id} failed: {err!r}"), file=sys.stderr, flush=True)
        fx.shutdown()
        return 2
    fx.shutdown()
    print(f"{fixture_id} stopped", flush=True)
    return 0
