#!/usr/bin/env python3
"""fake_opencode2.py serve --port N --hostname H - a tiny stand-in for `opencode2 serve`.

Test-only (opencode2 is not installed in the dev container). The FX-OC2 self-tests set
FX_OC2_BIN to this file (it is executable; the shebang finds python3 on PATH).

* HTTP Basic auth, user ``opencode``, password from env OPENCODE_PASSWORD (falls back
  to OPENCODE_SERVER_PASSWORD; refuses to start without one; never prints it).
* GET /api/health, GET/POST /api/session (honours ``id`` and ``title``; 409 on a
  reused id), GET /api/session/{id}, GET /api/session/{id}/message,
  POST /api/session/{id}/prompt, POST /api/session/import, GET /api/location,
  GET /api/model.
* GET /api/event: SSE over chunked transfer encoding: ``server.connected`` then an
  ``fx.heartbeat`` data event every 200 ms.
* Test hooks: GET /api/fx/env (HOME/XDG paths and cwd, never the password) and
  GET /api/fx/upgrade with ``Upgrade:`` (answers 101, then echoes raw bytes).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

PASSWORD = os.environ.get("OPENCODE_PASSWORD") or os.environ.get("OPENCODE_SERVER_PASSWORD") or ""
SESSIONS: dict[str, dict] = {}
MESSAGES: dict[str, list[dict]] = {}
LOCK = threading.Lock()


def project_id(directory: str) -> str:
    return hashlib.sha1(directory.encode()).hexdigest()


def now_ms() -> int:
    return int(time.time() * 1000)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args: object) -> None:
        return

    def send(self, status: int, obj: object = None, headers: dict | None = None) -> None:
        raw = b"" if obj is None else json.dumps(obj).encode()
        self.send_response(status)
        if obj is not None:
            self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if raw and self.command != "HEAD":
            self.wfile.write(raw)

    def body(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        try:
            data = json.loads(raw or b"{}")
        except ValueError:
            return {}
        return data if isinstance(data, dict) else {}

    def authed(self) -> bool:
        value = self.headers.get("Authorization") or ""
        if not value.lower().startswith("basic "):
            return False
        try:
            user, _, pw = base64.b64decode(value[6:]).decode().partition(":")
        except ValueError:
            return False
        return user == "opencode" and secrets.compare_digest(pw, PASSWORD)

    def handle_any(self) -> None:
        split = urlsplit(self.path)
        path, query = split.path, {k: v[0] for k, v in parse_qs(split.query).items()}
        if not self.authed():
            return self.send(401, {"_tag": "UnauthorizedError", "message": "Unauthorized"},
                             {"WWW-Authenticate": 'Basic realm="Secure Area"'})
        m = self.command
        parts = [p for p in path.split("/") if p]
        if m == "GET" and path == "/api/health":
            return self.send(200, {"healthy": True, "version": "0.0.0-fake", "pid": os.getpid()})
        if m == "GET" and path == "/api/fx/env":
            keys = ["HOME", "XDG_DATA_HOME", "XDG_CONFIG_HOME", "XDG_STATE_HOME", "XDG_CACHE_HOME", "TMPDIR"]
            return self.send(200, {"env": {k: os.environ.get(k) for k in keys}, "cwd": os.getcwd(),
                                   "has_password": bool(PASSWORD)})
        if m == "GET" and path == "/api/fx/upgrade":
            return self.upgrade()
        if m == "GET" and path == "/api/event":
            return self.events()
        if m == "GET" and path == "/api/model":
            return self.send(200, {"data": [{"id": "stub", "providerID": "fx-stub", "name": "FX stub"}]})
        if m == "GET" and path == "/api/location":
            directory = query.get("location[directory]") or os.getcwd()
            return self.send(200, {"directory": directory, "project": {"id": project_id(directory),
                                   "directory": directory, "canonical": directory}})
        if path == "/api/session" and m == "GET":
            with LOCK:
                rows = [s for s in SESSIONS.values()
                        if not query.get("directory") or s["location"]["directory"] == query["directory"]]
            rows.sort(key=lambda s: s["time"]["updated"], reverse=True)
            return self.send(200, {"data": rows, "cursor": {}})
        if path == "/api/session" and m == "POST":
            body = self.body()
            sid = body.get("id") or "ses_" + secrets.token_hex(13)
            if not str(sid).startswith("ses"):
                return self.send(400, {"_tag": "InvalidRequestError", "message": "bad id"})
            directory = (body.get("location") or {}).get("directory") or os.getcwd()
            t = now_ms()
            info = {"id": sid, "projectID": project_id(directory), "cost": 0,
                    "tokens": {"input": 0, "output": 0, "reasoning": 0, "cache": {"read": 0, "write": 0}},
                    "time": {"created": t, "updated": t}, "location": {"directory": directory}}
            if body.get("title"):
                info["title"] = body["title"]
            with LOCK:
                if sid in SESSIONS:
                    return self.send(409, {"_tag": "ConflictError", "message": "exists", "resource": sid})
                SESSIONS[sid] = info
                MESSAGES[sid] = []
            return self.send(200, {"data": info})
        if path == "/api/session/import" and m == "POST":
            body = self.body()
            info = body.get("info") or {}
            sid = info.get("id")
            if not sid or not str(sid).startswith("ses") or not isinstance(body.get("messages"), list):
                return self.send(400, {"_tag": "InvalidRequestError", "message": "bad import"})
            with LOCK:
                if sid in SESSIONS:
                    return self.send(409, {"_tag": "ConflictError", "message": "exists", "resource": sid})
                SESSIONS[sid] = info
                MESSAGES[sid] = list(body["messages"])
            return self.send(200, {"data": info})
        if len(parts) >= 3 and parts[:2] == ["api", "session"]:
            sid = parts[2]
            with LOCK:
                info = SESSIONS.get(sid)
            if info is None:
                return self.send(404, {"_tag": "SessionNotFoundError", "sessionID": sid,
                                       "message": f"Session not found: {sid}"})
            if len(parts) == 3 and m == "GET":
                return self.send(200, {"data": info})
            if parts[3:] == ["message"] and m == "GET":
                limit = int(query.get("limit") or 50)
                with LOCK:
                    rows = list(reversed(MESSAGES[sid]))[:limit]
                return self.send(200, {"data": rows, "cursor": {}, "total": len(MESSAGES[sid])})
            if parts[3:] == ["prompt"] and m == "POST":
                body = self.body()
                mid = body.get("id") or "msg_" + secrets.token_hex(13)
                with LOCK:
                    MESSAGES[sid].append({"id": mid, "type": "user", "time": {"created": now_ms()},
                                          "text": body.get("text", "")})
                return self.send(200, {"data": {"id": mid, "sessionID": sid, "timeCreated": now_ms(),
                                                "type": "user", "payload": {"text": body.get("text", "")},
                                                "delivery": body.get("delivery", "steer")}})
        return self.send(404, {"_tag": "NotFound", "message": "not found"})

    def events(self) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()

        def chunk(obj: dict) -> None:
            data = b"data: " + json.dumps(obj).encode() + b"\n\n"
            self.wfile.write(b"%x\r\n%s\r\n" % (len(data), data))
            self.wfile.flush()

        try:
            chunk({"type": "server.connected", "data": {}})
            n = 0
            while True:
                time.sleep(0.2)
                n += 1
                chunk({"type": "fx.heartbeat", "data": {"n": n}})
        except OSError:
            self.close_connection = True

    def upgrade(self) -> None:
        if (self.headers.get("Upgrade") or "").lower() != "websocket":
            return self.send(400, {"message": "upgrade required"})
        self.send_response_only(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.end_headers()
        self.wfile.flush()
        self.close_connection = True
        try:
            while True:
                data = self.connection.recv(65536)
                if not data:
                    break
                self.connection.sendall(data)
        except OSError:
            pass

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = handle_any


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["serve"])
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--hostname", default="127.0.0.1")
    args = parser.parse_args()
    if not PASSWORD:
        print("fake_opencode2: OPENCODE_PASSWORD is not set", file=sys.stderr)
        return 1
    srv = Server((args.hostname, args.port), Handler)
    print(f"fake opencode2 listening on http://{args.hostname}:{args.port}", flush=True)
    srv.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
