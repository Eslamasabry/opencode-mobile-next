#!/usr/bin/env python3
"""FX-CTL - the one control address flows talk to (S0-FX contract, ``flows/_fx/fx.js``).

Flows cannot know each fixture's control port, so every ``[fx]`` step POSTs one body to
``$FX_CTL/fx`` (run.py sets FX_CTL to this server, http://127.0.0.1:8898):

    POST /fx {"target": "FX-OC2", "action": "seed-sessions", "args": {...}}

and gets the fixture's JSON answer back (200), or ``{"error": ...}`` with 4xx/5xx, which fails the
step. Started by fxctl.py like any fixture, in the same run dir as the fixtures it fronts: it reads
``<run-dir>/<target>/ready.json`` for the target's ``ctl`` port at each call, so fixtures may start
before or after it.

Targets:
  FX-*        forwarded as POST <ctl>/<action> with ``args`` as the body (each server's own actions,
              plus the shared /fault, /reset, ... ones; "faults-clear" means POST /faults/clear).
  fx-proxy    the fixtures' own front proxies, by the address the app uses:
                cut-host {host, port}      every request to that front port is dropped (the socket is
                                           closed, as when the network is gone) and open streams end
                restore-host {host, port}  that front port answers again
              ``port`` picks the fixture whose ready.json lists it as a port; ``host`` is informational
              (the emulator reaches every fixture as 10.0.2.2).
GET /fx/targets -> the fixtures with a ready.json in this run dir and their ctl ports.
"""

from __future__ import annotations

import http.client
import json
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import LOOPBACK, Fixture, FxHandler, run_fixture  # noqa: E402

PROXY_ACTIONS = ("cut-host", "restore-host")


class Dispatcher:
    def __init__(self, run_dir: Path) -> None:
        self.run_dir = run_dir

    def ready(self) -> dict[str, dict[str, Any]]:
        out = {}
        for path in sorted(self.run_dir.glob("FX-*/ready.json")):
            try:
                out[path.parent.name] = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
        out.pop("FX-CTL", None)
        return out

    def call(self, target: str, path: str, body: Any, timeout: float = 120) -> tuple[int, Any]:
        info = self.ready().get(target)
        ctl = (info or {}).get("ports", {}).get("ctl")
        if not ctl:
            return 404, {"error": f"{target} is not running here (no ready.json with a ctl port)"}
        conn = http.client.HTTPConnection(LOOPBACK, int(ctl), timeout=timeout)
        try:
            conn.request("POST", path, body=json.dumps(body if body is not None else {}).encode(),
                         headers={"Content-Type": "application/json"})
            resp = conn.getresponse()
            raw = resp.read()
        except OSError as err:
            return 502, {"error": f"{target} control port {ctl}: {err.__class__.__name__}"}
        finally:
            conn.close()
        try:
            return resp.status, json.loads(raw) if raw else {}
        except ValueError:
            return 502, {"error": f"{target} answered non-JSON"}

    def owner_of_port(self, port: int) -> str | None:
        for fixture_id, info in self.ready().items():
            ports = info.get("ports") or {}
            if port in {int(v) for k, v in ports.items() if k != "ctl" and isinstance(v, int)}:
                return fixture_id
        return None

    def proxy(self, action: str, args: dict[str, Any]) -> tuple[int, Any]:
        try:
            port = int(args.get("port"))
        except (TypeError, ValueError):
            return 400, {"error": "fx-proxy needs args.port (the front port the app uses)"}
        target = self.owner_of_port(port)
        if not target:
            return 404, {"error": f"no running fixture listens on port {port}"}
        if action == "cut-host":
            status, data = self.call(target, "/fault", {"path": "/", "action": "drop"})
            if status != 200:
                return status, data
            # Streams already open (SSE) are not new requests: end them too. Not every fixture has them.
            self.call(target, "/drop-streams", {})
            return 200, {"cut": target, "port": port}
        status, data = self.call(target, "/faults/clear", {})
        return (200, {"restored": target, "port": port}) if status == 200 else (status, data)

    def dispatch(self, body: Any) -> tuple[int, Any]:
        if not isinstance(body, dict):
            return 400, {"error": "body must be a JSON object"}
        target, action, args = body.get("target"), body.get("action"), body.get("args") or {}
        if not isinstance(target, str) or not isinstance(action, str) or not action:
            return 400, {"error": "body needs target and action"}
        if not isinstance(args, dict):
            return 400, {"error": "args must be a JSON object"}
        if target == "fx-proxy":
            if action not in PROXY_ACTIONS:
                return 404, {"error": f"fx-proxy has no action {action!r}; known: {list(PROXY_ACTIONS)}"}
            return self.proxy(action, args)
        if not target.startswith("FX-"):
            return 404, {"error": f"unknown target {target!r}"}
        path = "/faults/clear" if action == "faults-clear" else "/" + action.strip("/")
        return self.call(target, path, args)


class Handler(FxHandler):
    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        disp: Dispatcher = self.fixture.dispatcher  # type: ignore[attr-defined]
        if method == "POST" and path == "/fx":
            status, data = disp.dispatch(self.body_json())
            self.note(target=(self._body_target()), status_code=status)
            return self.send_json(data, status)
        if method == "GET" and path == "/fx/targets":
            return self.send_json({k: (v.get("ports") or {}).get("ctl") for k, v in disp.ready().items()})
        return self.send_json({"error": "not found"}, 404)

    def _body_target(self) -> str | None:
        body = self.body_json()
        return body.get("target") if isinstance(body, dict) else None


def setup(fx: Fixture) -> dict[str, Any]:
    fx.dispatcher = Dispatcher(fx.dir.parent)  # type: ignore[attr-defined]
    fx.serve("http", Handler)
    return {"url": f"http://{LOOPBACK}:{fx.ports['http']}"}


if __name__ == "__main__":
    sys.exit(run_fixture("FX-CTL", setup))
