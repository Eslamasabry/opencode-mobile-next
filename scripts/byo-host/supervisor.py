#!/usr/bin/env python3
"""Per-phone authentication in front of a loopback OpenCode 1 process.

No third-party Python dependencies. This is an SSH-forward-only endpoint, not a
public HTTP server. A host account is a trust boundary; phone tokens never reach
OpenCode and host state stores only their digests. Logs never contain requests.
"""

import argparse
import base64
import contextlib
import fcntl
import hashlib
import hmac
import http.client
import io
import json
import os
from pathlib import Path
import re
import secrets
import selectors
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BUNDLE_VERSION = "1.1.0"
OPENCODE_VERSION = "1.18.32"
DEFAULT_PORT = 4096
MAX_BODY = 16 * 1024 * 1024
ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z")
TOKEN = re.compile(r"[A-Za-z0-9_-]{32,256}\Z")
HOP_HEADERS = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "authorization",
    "host",
}


class ProtocolError(Exception):
    """Only fixed, caller-safe error codes may be constructed."""


def atomic_write(path, data, mode=0o600):
    path = Path(path)
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    if path.is_symlink():
        raise ProtocolError("unsafePath")
    fd, temporary = tempfile.mkstemp(prefix=".oc-byo-", dir=path.parent)
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


@contextlib.contextmanager
def file_lock(path, nonblocking=False):
    path = Path(path)
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        os.fchmod(fd, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | (fcntl.LOCK_NB if nonblocking else 0))
        except BlockingIOError:
            raise ProtocolError("hostBusy") from None
        yield
    finally:
        os.close(fd)


def validate_pair(payload):
    if not isinstance(payload, dict) or set(payload) != {
        "deviceId",
        "token",
        "publicKey",
    }:
        raise ProtocolError("invalidPairing")
    device, token, public_key = (payload[k] for k in ("deviceId", "token", "publicKey"))
    if not isinstance(device, str) or not ID.fullmatch(device):
        raise ProtocolError("invalidPairing")
    if not isinstance(token, str) or not TOKEN.fullmatch(token):
        raise ProtocolError("invalidPairing")
    if not isinstance(public_key, str) or "\n" in public_key or "\r" in public_key:
        raise ProtocolError("invalidPublicKey")
    # Device public keys only; private signing stays in Android Keystore.
    parts = public_key.split()
    if len(parts) not in (2, 3):
        raise ProtocolError("invalidPublicKey")
    try:
        key = base64.b64decode(parts[1], validate=True)
        if parts[0] == "ssh-ed25519":
            if key != struct.pack(">I", 11) + b"ssh-ed25519" + struct.pack(">I", 32) + key[-32:]:
                raise ValueError()
        elif parts[0] == "ecdsa-sha2-nistp256":
            prefix = (struct.pack(">I", len(b"ecdsa-sha2-nistp256")) + b"ecdsa-sha2-nistp256"
                      + struct.pack(">I", 8) + b"nistp256" + struct.pack(">I", 65))
            if len(key) != len(prefix) + 65 or not key.startswith(prefix + b"\x04"):
                raise ValueError()
            point = key[len(prefix):]
            x, y = int.from_bytes(point[1:33], "big"), int.from_bytes(point[33:], "big")
            prime = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff
            b = 0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b
            if x >= prime or y >= prime or (y*y - (x*x*x - 3*x + b)) % prime:
                raise ValueError()
        else:
            raise ValueError()
    except (ValueError, TypeError):
        raise ProtocolError("invalidPublicKey") from None
    return device, token, " ".join(parts[:2])


class State:
    def __init__(self, directory, authorized_keys, port=DEFAULT_PORT):
        self.directory = Path(directory)
        self.path = self.directory / "state.json"
        self.lock_path = self.directory / "state.lock"
        self.authorized_keys = Path(authorized_keys)
        self.port = port

    def _load(self):
        if self.path.is_symlink():
            raise ProtocolError("unsafePath")
        if not self.path.exists():
            return None
        try:
            data = json.loads(self.path.read_text())
            uuid.UUID(data["hostId"])
            if (
                data["bundleVersion"] != BUNDLE_VERSION
                or data["openCodeVersion"] != OPENCODE_VERSION
                or data["port"] != self.port
                or not isinstance(data["devices"], dict)
            ):
                raise ValueError()
            if self.path.stat().st_mode & 0o077:
                raise ValueError()
            return data
        except (ValueError, KeyError, TypeError):
            raise ProtocolError("hostConflict") from None

    def _save(self, data):
        atomic_write(self.path, (json.dumps(data, sort_keys=True) + "\n").encode())

    @staticmethod
    def descriptor(data):
        return {
            key: data[key]
            for key in ("hostId", "bundleVersion", "openCodeVersion", "port")
        }

    def info(self):
        with file_lock(self.lock_path):
            data = self._load()
            if data is None:
                raise ProtocolError("notInstalled")
            return self.descriptor(data)

    def _update_key(self, device, public_key=None):
        path = self.authorized_keys
        if path.is_symlink() or path.parent.is_symlink():
            raise ProtocolError("unsafePath")
        marker = "oc-byo-" + device
        lines = path.read_text().splitlines() if path.exists() else []
        # Compare a whole final field; a phone id cannot match a prefix of another.
        kept = [
            line for line in lines if not line.split() or line.split()[-1] != marker
        ]
        if public_key is not None:
            if any(public_key.split()[1] in line.split() for line in kept):
                raise ProtocolError("deviceKeyConflict")
            kept.append(
                'restrict,port-forwarding,permitopen="127.0.0.1:%d",'
                'command="/bin/false" %s %s' % (self.port, public_key, marker)
            )
        atomic_write(path, ("\n".join(kept) + ("\n" if kept else "")).encode())

    def pair(self, payload):
        device, token, public_key = validate_pair(payload)
        digest = hashlib.sha256(token.encode()).hexdigest()
        with file_lock(self.lock_path):
            data = self._load()
            if data is None:
                data = {
                    "hostId": str(uuid.uuid4()),
                    "bundleVersion": BUNDLE_VERSION,
                    "openCodeVersion": OPENCODE_VERSION,
                    "port": self.port,
                    "devices": {},
                }
            old = data["devices"].get(device)
            if old and (
                old.get("revoked")
                or not hmac.compare_digest(old["tokenHash"], digest)
                or old["publicKey"] != public_key
            ):
                raise ProtocolError("deviceConflict")
            if any(
                key != device and value["publicKey"] == public_key
                for key, value in data["devices"].items()
            ):
                raise ProtocolError("deviceKeyConflict")
            data["devices"][device] = {
                "tokenHash": digest,
                "publicKey": public_key,
                "revoked": False,
            }
            self._save(data)
            self._update_key(device, public_key)
            return dict(self.descriptor(data), deviceId=device)

    def authenticate(self, device, token, allow_revoked=False):
        if not ID.fullmatch(device) or not TOKEN.fullmatch(token):
            return False
        digest = hashlib.sha256(token.encode()).hexdigest()
        with file_lock(self.lock_path):
            data = self._load()
            record = data["devices"].get(device) if data else None
            return bool(
                record
                and hmac.compare_digest(record["tokenHash"], digest)
                and (allow_revoked or not record["revoked"])
            )

    def revoke(self, device):
        with file_lock(self.lock_path):
            data = self._load()
            if not data or device not in data["devices"]:
                raise ProtocolError("unauthorized")
            data["devices"][device]["revoked"] = True
            self._save(data)  # Fail closed even if writing authorized_keys fails.
            self._update_key(device)
            return {"hostId": data["hostId"], "deviceId": device, "revoked": True}


def close_socket(sock):
    with contextlib.suppress(OSError):
        sock.shutdown(socket.SHUT_RDWR)
    with contextlib.suppress(OSError):
        sock.close()


class HostServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, state, upstream_port, upstream_token, port=None):
        self.state = state
        self.upstream_port = upstream_port
        self.upstream_token = upstream_token
        self.connections = {}
        self.connections_lock = threading.RLock()
        super().__init__(("127.0.0.1", state.port if port is None else port), Handler)

    def handle_error(self, request, client_address):
        pass  # A request/exception can contain credentials; never log it.

    def register(self, device, token, sock, allow_revoked=False):
        with self.connections_lock:
            if not self.state.authenticate(device, token, allow_revoked):
                return False
            self.connections.setdefault(device, set()).add(sock)
            return True

    def unregister(self, device, sock):
        with self.connections_lock:
            sockets = self.connections.get(device)
            if sockets is not None:
                sockets.discard(sock)
                if not sockets:
                    del self.connections[device]

    def disconnect(self, device):
        with self.connections_lock:
            sockets = self.connections.pop(device, set())
        for sock in sockets:
            close_socket(sock)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "OpenCodeMobileHost/1"
    sys_version = ""
    rbufsize = 0  # Do not prefetch a pipelined WebSocket frame into rfile.

    def log_message(self, format, *args):
        pass

    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def send_error(self, code, message=None, explain=None):
        self.reply(code, {"error": "invalidRequest"})

    def reply(self, status, payload):
        body = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        if status == 401:
            self.send_header("WWW-Authenticate", 'Basic realm="oc-byo-host"')
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)
        self.wfile.flush()
        self.close_connection = True

    def _credentials(self):
        headers = self.headers.get_all("Authorization", [])
        if len(headers) != 1 or not headers[0].startswith("Basic "):
            return None
        try:
            value = base64.b64decode(headers[0][6:], validate=True).decode("ascii")
            device, token = value.split(":", 1)
            return device, token
        except (ValueError, UnicodeError):
            return None

    def _body(self):
        lengths = self.headers.get_all("Content-Length", [])
        if self.headers.get("Transfer-Encoding") or len(lengths) > 1:
            raise ProtocolError("invalidRequest")
        if not lengths:
            return b""
        if not re.fullmatch(r"[0-9]+", lengths[0]):
            raise ProtocolError("invalidRequest")
        length = int(lengths[0])
        if length > MAX_BODY:
            raise ProtocolError("requestTooLarge")
        body = self.rfile.read(length)
        if len(body) != length:
            raise ProtocolError("invalidRequest")
        return body

    def do_GET(self):
        self.dispatch()

    do_POST = do_GET
    do_PUT = do_GET
    do_PATCH = do_GET
    do_DELETE = do_GET
    do_HEAD = do_GET
    do_OPTIONS = do_GET

    def dispatch(self):
        device = None
        try:
            credentials = self._credentials()
            revoke = self.path == "/_oc/revoke" and self.command == "POST"
            if not credentials:
                self.reply(401, {"error": "unauthorized"})
                return
            device, token = credentials
            if not self.server.register(
                device, token, self.connection, allow_revoked=revoke
            ):
                self.reply(401, {"error": "unauthorized"})
                return
            body = self._body()
            if self.headers.get("Upgrade"):
                self.websocket(device, token, body)
            elif self.path == "/_oc/host" and self.command == "GET":
                self.reply(200, self.server.state.info())
            elif revoke:
                # No caller-supplied target: credentials select exactly one device.
                if body not in (b"", b"{}"):
                    self.reply(400, {"error": "invalidRequest"})
                    return
                try:
                    receipt = self.server.state.revoke(device)
                    self.reply(200, receipt)
                finally:
                    # Includes existing SSE/upstream sockets, AFTER flushing receipt.
                    self.server.disconnect(device)
            elif self.path.startswith("/_oc/"):
                self.reply(404, {"error": "notFound"})
            elif not self.path.startswith("/") or self.path.startswith("//"):
                self.reply(400, {"error": "invalidRequest"})
            else:
                self.proxy(device, token, body)
        except ProtocolError as error:
            self.reply(400, {"error": str(error)})
        except (OSError, ValueError, http.client.HTTPException):
            self.close_connection = True
        finally:
            if device is not None:
                self.server.unregister(device, self.connection)

    def upstream_headers(self):
        connection_headers = {
            name.strip().lower()
            for value in self.headers.get_all("Connection", [])
            for name in value.split(",")
        }
        headers = {
            name: value
            for name, value in self.headers.items()
            if name.lower() not in HOP_HEADERS | connection_headers | {"content-length"}
        }
        internal = base64.b64encode(
            ("opencode:" + self.server.upstream_token).encode()
        ).decode()
        headers.update({"Authorization": "Basic " + internal, "Connection": "close"})
        return headers

    def websocket(self, device, token, body):
        keys = self.headers.get_all("Sec-WebSocket-Key", [])
        try:
            if (
                self.command != "GET"
                or body
                or not re.fullmatch(r"/pty/[^/?]+/connect(?:\?[^\r\n]*)?", self.path)
                or self.headers.get("Upgrade", "").lower() != "websocket"
                or "upgrade"
                not in {
                    part.strip().lower()
                    for part in self.headers.get("Connection", "").split(",")
                }
                or self.headers.get_all("Sec-WebSocket-Version", []) != ["13"]
                or len(keys) != 1
                or len(base64.b64decode(keys[0], validate=True)) != 16
            ):
                raise ValueError()
        except ValueError:
            self.reply(400, {"error": "invalidWebsocket"})
            return
        upstream = http.client.HTTPConnection(
            "127.0.0.1", self.server.upstream_port, timeout=30
        )
        sock = None
        upgraded = False
        try:
            upstream.connect()
            sock = upstream.sock
            if not self.server.register(device, token, sock):
                self.reply(401, {"error": "unauthorized"})
                return
            headers = self.upstream_headers()
            headers.update({"Connection": "Upgrade", "Upgrade": "websocket"})
            upstream.request("GET", self.path, headers=headers)
            raw = b""
            while b"\r\n\r\n" not in raw:
                data = sock.recv(4096)
                if not data:
                    raise ValueError()
                raw += data
                if len(raw) > 16384:
                    raise ValueError()
            handshake, pending = raw.split(b"\r\n\r\n", 1)
            status, header_bytes = handshake.split(b"\r\n", 1)
            response_headers = http.client.parse_headers(
                io.BytesIO(header_bytes + b"\r\n\r\n")
            )
            expected = base64.b64encode(
                hashlib.sha1(
                    (keys[0] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()
                ).digest()
            ).decode()
            if (
                not re.fullmatch(rb"HTTP/1\.[01] 101(?: [^\r\n]*)?", status)
                or response_headers.get("Upgrade", "").lower() != "websocket"
                or "upgrade"
                not in {
                    part.strip().lower()
                    for part in response_headers.get("Connection", "").split(",")
                }
                or response_headers.get_all("Sec-WebSocket-Accept", []) != [expected]
            ):
                raise ValueError()
            self.send_response(101)
            self.send_header("Connection", "Upgrade")
            self.send_header("Upgrade", "websocket")
            self.send_header("Sec-WebSocket-Accept", expected)
            for name in ("Sec-WebSocket-Protocol", "Sec-WebSocket-Extensions"):
                if response_headers.get(name):
                    self.send_header(name, response_headers[name])
            self.end_headers()
            self.wfile.flush()
            upgraded = True
            self.close_connection = True
            self.connection.settimeout(None)
            sock.settimeout(None)
            if pending:
                self.connection.sendall(pending)
            with selectors.DefaultSelector() as selector:
                selector.register(self.connection, selectors.EVENT_READ, sock)
                selector.register(sock, selectors.EVENT_READ, self.connection)
                while True:
                    if self.connection.fileno() < 0 or sock.fileno() < 0:
                        return
                    for event, unused_mask in selector.select(timeout=1):
                        data = event.fileobj.recv(65536)
                        if not data:
                            return
                        event.data.sendall(data)
        except (OSError, ValueError, http.client.HTTPException):
            if not upgraded:
                self.reply(502, {"error": "websocketUnavailable"})
        finally:
            if sock is not None:
                self.server.unregister(device, sock)
            upstream.close()

    def proxy(self, device, token, body):
        upstream = http.client.HTTPConnection(
            "127.0.0.1", self.server.upstream_port, timeout=30
        )
        sock = None
        sent_headers = False
        try:
            upstream.connect()
            sock = upstream.sock
            if not self.server.register(device, token, sock):
                self.reply(401, {"error": "unauthorized"})
                return
            headers = self.upstream_headers()
            upstream.request(self.command, self.path, body=body, headers=headers)
            response = upstream.getresponse()
            self.send_response(response.status)
            blocked = HOP_HEADERS | {"content-length", "server", "date"}
            for name, value in response.getheaders():
                if name.lower() not in blocked:
                    self.send_header(name, value)
            self.send_header("Connection", "close")
            self.end_headers()
            sent_headers = True
            self.close_connection = True
            # SSE can be idle indefinitely; revocation closes the registered socket.
            sock.settimeout(None)
            if self.command != "HEAD":
                while True:
                    chunk = response.read1(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    self.wfile.flush()
        except (OSError, http.client.HTTPException):
            if not sent_headers:
                self.reply(502, {"error": "upstreamUnavailable"})
        finally:
            if sock is not None:
                self.server.unregister(device, sock)
            upstream.close()


def free_loopback_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def serve(state, binary):
    with file_lock(state.directory / "serve.lock", nonblocking=True):
        state.info()
        password = secrets.token_urlsafe(48)
        upstream_port = free_loopback_port()
        environment = dict(
            os.environ,
            OPENCODE_SERVER_USERNAME="opencode",
            OPENCODE_SERVER_PASSWORD=password,
        )
        server = HostServer(state, upstream_port, password)
        child = subprocess.Popen(
            [
                str(Path(binary).resolve()),
                "serve",
                "--hostname",
                "127.0.0.1",
                "--port",
                str(upstream_port),
            ],
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        stop = threading.Event()
        serving = threading.Event()

        def request_stop(*unused):
            stop.set()
            if serving.is_set():
                threading.Thread(target=server.shutdown, daemon=True).start()

        old_handlers = {
            sig: signal.signal(sig, request_stop)
            for sig in (signal.SIGTERM, signal.SIGINT)
        }
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline and not stop.is_set():
                if child.poll() is not None:
                    raise ProtocolError("upstreamUnavailable")
                probe = http.client.HTTPConnection(
                    "127.0.0.1", upstream_port, timeout=1
                )
                try:
                    auth = base64.b64encode(("opencode:" + password).encode()).decode()
                    probe.request(
                        "GET",
                        "/global/health",
                        headers={"Authorization": "Basic " + auth},
                    )
                    response = probe.getresponse()
                    if response.status == 200:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                finally:
                    probe.close()
                stop.wait(0.1)
            else:
                raise ProtocolError("upstreamUnavailable")
            if stop.is_set():
                return

            def monitor_child():
                child.wait()
                request_stop()

            serving.set()
            threading.Thread(target=monitor_child, daemon=True).start()
            server.serve_forever(poll_interval=0.2)
        finally:
            serving.clear()
            for sig, handler in old_handlers.items():
                signal.signal(sig, handler)
            for device in list(server.connections):
                server.disconnect(device)
            server.server_close()
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("info", "pair", "serve"))
    parser.add_argument(
        "--state-dir", default=str(Path.home() / ".local/share/oc-byo-host")
    )
    parser.add_argument(
        "--authorized-keys", default=str(Path.home() / ".ssh/authorized_keys")
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--opencode", default=str(Path(__file__).parent / "opencode"))
    args = parser.parse_args()
    try:
        if not 1024 <= args.port <= 65535:
            raise ProtocolError("invalidPort")
        state = State(args.state_dir, args.authorized_keys, args.port)
        if args.command == "info":
            result = state.info()
        elif args.command == "pair":
            raw = sys.stdin.buffer.read(16385)
            if len(raw) > 16384:
                raise ProtocolError("invalidPairing")
            result = state.pair(json.loads(raw))
        else:
            serve(state, args.opencode)
            return 0
        print(json.dumps(result, sort_keys=True))
        return 0
    except ProtocolError as error:
        print(json.dumps({"error": str(error)}))
    except Exception:
        print(json.dumps({"error": "hostOperationFailed"}))
    return 1


if __name__ == "__main__":
    sys.exit(main())
