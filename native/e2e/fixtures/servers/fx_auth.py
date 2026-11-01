#!/usr/bin/env python3
"""FX-AUTH - sign-in stub for the agents on the phone (E2E-31).

The app's "Sign in" for a phone agent opens a terminal that runs the agent's
own login (``claude auth login``, ``codex login``, ...; see
lib/ui/screens/agents/agent_sign_in_terminal.dart). On the emulator those must
not reach the real providers, so the runner installs a shim in place of the
agent's binary inside built-in Linux ([adb] hook):

    curl -fsS http://10.0.2.2:8704/shim/claude -o <rootfs>/usr/local/bin/claude

The shim (python3, no dependencies) answers ``--version`` offline and, for
login subcommands, asks this stub for a code, prints the fixed code
``FXAU-4242`` and the line "Waiting for sign-in...", then polls until the code
is approved ("Signed in.", exit 0), denied or expired (exit 1). Any other
arguments print a fixed fixture reply so "run by name" can be asserted.

HTTP:  POST /device/start {agent} -> {user_code, verification_uri, request_id}
       GET  /device/poll?request_id= -> {state: pending|approved|denied|expired}
       GET  /shim/<agent>   agent in AGENTS
Control: approve {request_id?}, deny {request_id?}, expire {request_id?},
         GET state -> pending and signed-in agents.
"""

from __future__ import annotations

import secrets
import sys
import threading
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import Fixture, FxHandler, run_fixture  # noqa: E402

FIXED_CODE = "FXAU-4242"
AGENTS = ("claude", "codex", "pi", "gemini", "opencode")
LOGIN_WORDS = {"login", "auth", "signin", "sign-in", "/login"}

SHIM = r'''#!/usr/bin/env python3
"""FX-AUTH fixture shim for `__AGENT__` (never a real agent; test emulators only)."""
import json, os, sys, time, urllib.request

AGENT = "__AGENT__"
BASE = os.environ.get("FX_AUTH_URL", "__BASE__").rstrip("/")
POLL = int(os.environ.get("FX_AUTH_POLL_MS", "1000")) / 1000.0
args = sys.argv[1:]

def call(path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())

if args[:1] in (["--version"], ["-v"], ["version"]):
    print(AGENT + " 0.0.0-fixture (FX-AUTH shim)")
    sys.exit(0)
if any(a in __LOGIN__ for a in args):
    start = call("/device/start", {"agent": AGENT})
    print("To sign in, open " + start["verification_uri"] + " and enter the code:")
    print(start["user_code"])
    print("Waiting for sign-in...", flush=True)
    while True:
        state = call("/device/poll?request_id=" + start["request_id"])["state"]
        if state == "approved":
            print("Signed in.")
            sys.exit(0)
        if state in ("denied", "expired"):
            print("Sign-in " + state + ".")
            sys.exit(1)
        time.sleep(POLL)
print(AGENT + " fixture: " + " ".join(args))
'''


class State:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.reset()

    def reset(self) -> None:
        with self.lock:
            self.requests: dict[str, dict[str, Any]] = {}
            self.signed_in: set[str] = set()

    def pick(self, body: dict[str, Any]) -> dict[str, Any]:
        rid = body.get("request_id")
        if rid:
            if rid not in self.requests:
                raise ValueError("unknown request_id")
            return self.requests[rid]
        pending = [r for r in self.requests.values() if r["state"] == "pending"]
        if not pending:
            raise ValueError("no pending sign-in")
        return max(pending, key=lambda r: r["created"])

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {"pending": [{"request_id": k, "agent": r["agent"]} for k, r in self.requests.items()
                                if r["state"] == "pending"],
                    "signed_in": sorted(self.signed_in), "code": FIXED_CODE}


class Handler(FxHandler):
    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        st: State = self.fixture.auth  # type: ignore[attr-defined]
        if method == "POST" and path == "/device/start":
            body = self.body_json() or {}
            agent = body.get("agent") if body.get("agent") in AGENTS else "unknown"
            rid = secrets.token_hex(8)
            with st.lock:
                st.requests[rid] = {"agent": agent, "state": "pending", "created": time.monotonic()}
            self.note(agent=agent)
            return self.send_json({"user_code": FIXED_CODE, "request_id": rid,
                                   "verification_uri": "https://example.com/fx-auth/device"})
        if method == "GET" and path == "/device/poll":
            with st.lock:
                record = st.requests.get(query.get("request_id", ""))
                state = record["state"] if record else "expired"
            return self.send_json({"state": state})
        if method == "GET" and path.startswith("/shim/"):
            agent = path[len("/shim/"):]
            if agent not in AGENTS:
                return self.send_json({"error": "unknown agent"}, 404)
            host = self.headers.get("Host") or f"127.0.0.1:{self.fixture.ports['http']}"
            body = (SHIM.replace("__AGENT__", agent).replace("__BASE__", f"http://{host}")
                    .replace("__LOGIN__", repr(sorted(LOGIN_WORDS))))
            return self.send_text(body, ctype="text/x-python; charset=utf-8")
        self.send_json({"error": "not found"}, 404)


def setup(fx: Fixture) -> dict[str, Any]:
    st = State()
    fx.auth = st  # type: ignore[attr-defined]
    fx.state = st.snapshot  # type: ignore[method-assign]
    fx.on_reset_hook(st.reset)

    def mark(state: str):
        def action(body: dict[str, Any]) -> dict[str, Any]:
            with st.lock:
                record = st.pick(body)
                record["state"] = state
                if state == "approved":
                    st.signed_in.add(record["agent"])
                return {"agent": record["agent"], "state": state}
        return action

    fx.actions["approve"] = mark("approved")
    fx.actions["deny"] = mark("denied")
    fx.actions["expire"] = mark("expired")
    fx.serve("http", Handler)
    return {"url": f"http://127.0.0.1:{fx.ports['http']}", "code": FIXED_CODE, "agents": list(AGENTS)}


if __name__ == "__main__":
    sys.exit(run_fixture("FX-AUTH", setup))
