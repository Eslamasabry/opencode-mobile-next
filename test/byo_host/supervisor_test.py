"""Offline stdlib tests: python3 -m unittest discover -s test/byo_host -p '*_test.py'."""

import argparse
import base64
import hashlib
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import io
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2] / "scripts/byo-host"


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


host = module("byo_host_supervisor", ROOT / "supervisor.py")
builder = module("byo_host_package", ROOT / "package.py")
installer = types.ModuleType("byo_host_install")
exec(
    compile(
        (ROOT / "install.sh").read_text().split("<<'PY'\n", 1)[1].rsplit("\nPY", 1)[0],
        str(ROOT / "install.sh"),
        "exec",
    ),
    installer.__dict__,
)


def phone(device="phone-a", seed=b"a"):
    key = struct.pack(">I", 11) + b"ssh-ed25519" + struct.pack(">I", 32) + seed * 32
    return {
        "deviceId": device,
        "token": seed.decode() * 48,
        "publicKey": "ssh-ed25519 " + base64.b64encode(key).decode(),
    }


def auth(payload):
    return (
        "Basic "
        + base64.b64encode(
            (payload["deviceId"] + ":" + payload["token"]).encode()
        ).decode()
    )


class StateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.state = host.State(self.root / "state", self.root / ".ssh/authorized_keys")

    def test_pair_is_idempotent_and_only_hash_persisted(self):
        one = self.state.pair(phone())
        two = self.state.pair(phone())
        self.assertEqual(one, two)
        self.assertEqual(self.state.path.stat().st_mode & 0o777, 0o600)
        self.assertNotIn(phone()["token"], self.state.path.read_text())
        self.assertEqual(len(self.state.authorized_keys.read_text().splitlines()), 1)
        key = self.state.authorized_keys.read_text()
        self.assertIn('restrict,port-forwarding,permitopen="127.0.0.1:4096"', key)
        self.assertIn('command="/bin/false"', key)
        self.assertTrue(self.state.authenticate("phone-a", phone()["token"]))

    def test_keystore_p256_public_key_pairs_without_private_material(self):
        x = bytes.fromhex("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")
        y = bytes.fromhex("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        fields = [b"ecdsa-sha2-nistp256", b"nistp256", b"\x04" + x + y]
        wire = b"".join(struct.pack(">I", len(field)) + field for field in fields)
        public = "ecdsa-sha2-nistp256 " + base64.b64encode(wire).decode()
        payload = dict(phone(), publicKey=public)
        self.state.pair(payload)
        self.assertIn(public, self.state.authorized_keys.read_text())
        self.assertNotIn(payload["token"], self.state.path.read_text())
        invalid = wire[:-1] + bytes([wire[-1] ^ 1])
        with self.assertRaisesRegex(host.ProtocolError, "invalidPublicKey"):
            self.state.pair(dict(payload, publicKey="ecdsa-sha2-nistp256 " + base64.b64encode(invalid).decode()))

    def test_owner_enrolled_marker_is_restricted_then_revoked(self):
        path = self.state.authorized_keys
        path.parent.mkdir()
        path.write_text(phone()["publicKey"] + " oc-byo-phone-a\n")
        self.state.pair(phone())
        self.assertTrue(path.read_text().startswith("restrict,port-forwarding"))
        self.state.revoke("phone-a")
        self.assertEqual(path.read_text(), "")

    def test_unmarked_duplicate_key_cannot_bypass_revocation(self):
        path = self.state.authorized_keys
        path.parent.mkdir()
        path.write_text(phone()["publicKey"] + " other-comment\n")
        with self.assertRaisesRegex(host.ProtocolError, "deviceKeyConflict"):
            self.state.pair(phone())
        self.assertIn("other-comment", path.read_text())

    def test_pair_conflicts_never_replace_identity(self):
        descriptor = self.state.pair(phone())
        with self.assertRaisesRegex(host.ProtocolError, "deviceConflict"):
            self.state.pair(dict(phone(), token="b" * 48))
        with self.assertRaisesRegex(host.ProtocolError, "deviceConflict"):
            self.state.pair(
                dict(phone(), publicKey=phone("phone-b", b"b")["publicKey"])
            )
        with self.assertRaisesRegex(host.ProtocolError, "deviceKeyConflict"):
            self.state.pair(dict(phone(), deviceId="phone-b"))
        self.assertEqual(self.state.info()["hostId"], descriptor["hostId"])

    def test_revoke_removes_only_exact_device_marker_and_survives_reload(self):
        self.state.pair(phone())
        self.state.pair(phone("phone-a-extra", b"b"))
        with self.state.authorized_keys.open("a") as stream:
            stream.write("ssh-ed25519 administrator-key administrator\n")
        receipt = self.state.revoke("phone-a")
        self.assertEqual(receipt, self.state.revoke("phone-a"))
        keys = self.state.authorized_keys.read_text()
        self.assertNotIn(" oc-byo-phone-a\n", keys)
        self.assertIn(" oc-byo-phone-a-extra\n", keys)
        self.assertIn(" administrator\n", keys)
        reloaded = host.State(self.state.directory, self.state.authorized_keys)
        self.assertFalse(reloaded.authenticate("phone-a", phone()["token"]))
        self.assertTrue(
            reloaded.authenticate("phone-a", phone()["token"], allow_revoked=True)
        )
        with self.assertRaisesRegex(host.ProtocolError, "deviceConflict"):
            reloaded.pair(phone())

    def test_pair_validates_input_and_symlinks(self):
        for bad in (
            dict(phone(), deviceId="../a"),
            dict(phone(), token="short"),
            dict(phone(), publicKey=phone()["publicKey"] + "\nattack"),
            dict(phone(), publicKey="ssh-ed25519 YQ=="),
        ):
            with self.assertRaises(host.ProtocolError):
                self.state.pair(bad)
        target = self.root / "target"
        target.write_text("protected")
        self.state.authorized_keys.parent.mkdir()
        self.state.authorized_keys.symlink_to(target)
        with self.assertRaisesRegex(host.ProtocolError, "unsafePath"):
            self.state.pair(phone())
        self.assertEqual(target.read_text(), "protected")

    def test_cli_never_echoes_invalid_pairing(self):
        secret = "sensitive-invalid-credential"
        result = subprocess.run(
            [
                sys.executable,
                str(ROOT / "supervisor.py"),
                "pair",
                "--state-dir",
                str(self.root / "cli"),
            ],
            input=secret,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 1)
        self.assertNotIn(secret, result.stdout + result.stderr)
        self.assertEqual(json.loads(result.stdout), {"error": "hostOperationFailed"})


class Upstream(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_GET(self):
        self.server.authorizations.append(self.headers.get("Authorization"))
        if self.headers.get("Upgrade") == "websocket":
            key = self.headers["Sec-WebSocket-Key"]
            accept = base64.b64encode(
                hashlib.sha1(
                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()
                ).digest()
            ).decode()
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.send_header("Sec-WebSocket-Accept", accept)
            self.end_headers()
            self.wfile.flush()
            while True:
                frame = self.rfile.read(2)
                if not frame:
                    break
                mask = self.rfile.read(4)
                data = self.rfile.read(frame[1] & 127)
                if len(mask) != 4:
                    break
                plain = bytes(
                    value ^ mask[index % 4] for index, value in enumerate(data)
                )
                self.wfile.write(bytes([frame[0], len(plain)]) + plain)
                self.wfile.flush()
            self.close_connection = True
        elif self.path == "/event":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.write(b"data: ready\n\n")
            self.wfile.flush()
            self.server.stop_events.wait(5)
            self.close_connection = True
        else:
            body = json.dumps(
                {"sessions": self.server.sessions, "path": self.path}
            ).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def do_POST(self):
        self.server.authorizations.append(self.headers.get("Authorization"))
        data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self.server.sessions.append(data["title"])
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class ProxyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.state = host.State(self.root / "state", self.root / "authorized_keys")
        self.a, self.b = phone(), phone("phone-b", b"b")
        self.state.pair(self.a)
        self.state.pair(self.b)
        self.upstream = ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        self.upstream.daemon_threads = True
        self.upstream.sessions = []
        self.upstream.authorizations = []
        self.upstream.stop_events = threading.Event()
        self.server = host.HostServer(
            self.state, self.upstream.server_port, "private-upstream-token", port=0
        )
        self.threads = []
        for server in (self.upstream, self.server):
            thread = threading.Thread(
                target=server.serve_forever, kwargs={"poll_interval": 0.02}, daemon=True
            )
            thread.start()
            self.threads.append(thread)

    def tearDown(self):
        self.upstream.stop_events.set()
        for device in list(self.server.connections):
            self.server.disconnect(device)
        for server in (self.server, self.upstream):
            server.shutdown()
            server.server_close()
        for thread in self.threads:
            thread.join(timeout=2)
        self.temporary.cleanup()

    def request(self, method, path, payload=None, body=None, headers=None):
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_port, timeout=2
        )
        request_headers = dict(headers or {})
        if payload:
            request_headers["Authorization"] = auth(payload)
        connection.request(method, path, body=body, headers=request_headers)
        response = connection.getresponse()
        data = response.read()
        connection.close()
        return response.status, json.loads(data)

    def event(self, payload):
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_port, timeout=2
        )
        connection.request("GET", "/event", headers={"Authorization": auth(payload)})
        response = connection.getresponse()
        self.assertEqual(response.status, 200)
        self.assertEqual(response.read(13), b"data: ready\n\n")
        self.addCleanup(connection.close)
        self.addCleanup(response.close)
        return response

    def test_auth_descriptor_and_malformed_websocket_rejection(self):
        self.assertEqual(self.server.server_address[0], "127.0.0.1")
        self.assertEqual(self.request("GET", "/_oc/host")[0], 401)
        self.assertEqual(
            self.request("GET", "/_oc/host", dict(self.a, token="wrong" * 12))[0], 401
        )
        status, data = self.request("GET", "/_oc/host", self.a)
        self.assertEqual(status, 200)
        self.assertEqual(data, self.state.info())
        self.assertEqual(
            self.request(
                "GET", "/pty/abc/connect", self.a, headers={"Upgrade": "websocket"}
            ),
            (400, {"error": "invalidWebsocket"}),
        )

    def test_websocket_echo_and_revoke_closes_only_one_device(self):
        def connect(payload):
            stream = socket.create_connection(
                ("127.0.0.1", self.server.server_port), timeout=2
            )
            self.addCleanup(stream.close)
            key = base64.b64encode(b"1234567890abcdef").decode()
            request = (
                "GET /pty/pty_abc/connect?cursor=0 HTTP/1.1\r\nHost: localhost\r\n"
                "Connection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\n"
                "Sec-WebSocket-Key: "
                + key
                + "\r\nAuthorization: "
                + auth(payload)
                + "\r\n\r\n"
            )
            stream.sendall(request.encode())
            data = b""
            while b"\r\n\r\n" not in data:
                data += stream.recv(4096)
            self.assertTrue(data.startswith(b"HTTP/1.1 101"))
            return stream

        def echo(stream, data):
            mask = b"abcd"
            frame = (
                bytes([0x81, 0x80 | len(data)])
                + mask
                + bytes(value ^ mask[i % 4] for i, value in enumerate(data))
            )
            stream.sendall(frame)
            received = b""
            while len(received) < len(data) + 2:
                received += stream.recv(1024)
            self.assertEqual(received, bytes([0x81, len(data)]) + data)

        first, second = connect(self.a), connect(self.b)
        echo(first, b"before revoke")
        self.assertEqual(self.request("POST", "/_oc/revoke", self.a)[0], 200)
        self.assertEqual(first.recv(1024), b"")
        echo(second, b"still connected")
        self.assertEqual(self.request("GET", "/session", self.b)[0], 200)

    def test_rest_tokens_replaced_sessions_survive_and_only_one_phone_revoked(self):
        self.assertEqual(
            self.request("POST", "/session", self.a, '{"title":"keep me"}')[0], 200
        )
        first = self.event(self.a)
        second = self.event(self.b)
        status, receipt = self.request("POST", "/_oc/revoke", self.a)
        self.assertEqual(status, 200)
        self.assertEqual(receipt["deviceId"], self.a["deviceId"])
        self.assertTrue(receipt["revoked"])
        self.assertEqual(
            first.read(), b""
        )  # Existing SSE closed, not merely future auth.
        self.assertEqual(self.request("POST", "/_oc/revoke", self.a), (200, receipt))
        self.assertEqual(self.request("GET", "/session", self.a)[0], 401)
        status, result = self.request(
            "GET", "/session?directory=%2Fhome%2Fuser", self.b
        )
        self.assertEqual(status, 200)
        self.assertEqual(result["sessions"], ["keep me"])
        self.assertIn("?directory=", result["path"])
        self.assertIn("phone-b", self.server.connections)
        self.assertNotIn("phone-a", self.server.connections)
        self.assertTrue(
            all(
                value
                == "Basic "
                + base64.b64encode(b"opencode:private-upstream-token").decode()
                for value in self.upstream.authorizations
            )
        )
        second.close()

    def test_cannot_revoke_other_device_or_smuggle_chunked_request(self):
        self.assertEqual(
            self.request("POST", "/_oc/revoke", self.a, '{"deviceId":"phone-b"}')[0],
            400,
        )
        self.assertEqual(self.request("GET", "/_oc/host", self.b)[0], 200)
        self.assertEqual(
            self.request(
                "POST", "/session", self.a, "", {"Transfer-Encoding": "chunked"}
            )[0],
            400,
        )


class BundleTests(unittest.TestCase):
    def check_policy(self):
        with mock.patch.object(installer, "check_ssh_listener"):
            installer.check_ssh_policy("alice", Path("/home/alice"))

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        binary = bytearray(64)
        binary[:6] = b"\x7fELF\x02\x01"
        binary[18:20] = struct.pack("<H", 62)
        self.binary = self.root / "opencode"
        self.binary.write_bytes(binary)
        self.digest = hashlib.sha256(binary).hexdigest()
        self.archive = self.root / "bundle.tar.gz"

    def package(self):
        return builder.package(self.binary, self.digest, "amd64", self.archive)

    def test_deterministic_bundle_and_verified_safe_extract(self):
        first = self.package()
        content = self.archive.read_bytes()
        os.utime(self.binary, (999, 999))
        self.assertEqual(first, self.package())
        self.assertEqual(content, self.archive.read_bytes())
        destination = self.root / "extract"
        destination.mkdir()
        with mock.patch.object(installer.platform, "machine", return_value="x86_64"):
            manifest = installer.safe_bundle(
                self.archive, first["archiveSha256"], destination
            )
        self.assertEqual(manifest["bundleVersion"], "1.1.0")
        self.assertEqual(manifest["openCodeVersion"], "1.18.32")
        self.assertEqual(
            set(x.name for x in destination.iterdir()),
            {"supervisor.py", "install.sh", "opencode", "manifest.json"},
        )

    def test_checksum_and_architecture_mismatch_fail_before_extract(self):
        with self.assertRaisesRegex(ValueError, "checksumMismatch"):
            builder.package(self.binary, "f" * 64, "amd64", self.archive)
        with self.assertRaisesRegex(ValueError, "unsupportedBinary"):
            builder.package(self.binary, self.digest, "arm64", self.archive)
        first = self.package()
        with self.assertRaisesRegex(installer.InstallError, "checksumMismatch"):
            installer.safe_bundle(self.archive, "f" * 64, self.root)
        with mock.patch.object(installer.platform, "machine", return_value="aarch64"):
            with self.assertRaisesRegex(installer.InstallError, "unsupportedBundle"):
                installer.safe_bundle(self.archive, first["archiveSha256"], self.root)

    def test_archive_path_traversal_duplicate_and_symlink_rejected(self):
        self.package()
        with tarfile.open(self.archive) as source:
            files = [
                (entry.name, source.extractfile(entry).read())
                for entry in source.getmembers()
            ]
        for malicious_name, kind in (
            ("../escaped", tarfile.REGTYPE),
            ("opencode", tarfile.SYMTYPE),
            ("opencode", tarfile.REGTYPE),
        ):
            bad = self.root / "bad.tar.gz"
            with tarfile.open(bad, "w:gz") as target:
                for name, data in files:
                    entry = tarfile.TarInfo(name)
                    entry.size = len(data)
                    target.addfile(entry, io.BytesIO(data))
                extra = tarfile.TarInfo(malicious_name)
                extra.type = kind
                extra.linkname = "/etc/passwd" if kind == tarfile.SYMTYPE else ""
                extra.size = 0
                target.addfile(extra, io.BytesIO())
            digest = hashlib.sha256(bad.read_bytes()).hexdigest()
            with self.assertRaisesRegex(installer.InstallError, "unsafeArchive"):
                installer.safe_bundle(bad, digest, self.root)
        self.assertFalse((self.root.parent / "escaped").exists())

    def test_install_reuses_matching_live_service_without_restart(self):
        artifact = self.package()
        home = self.root / "home"
        home.mkdir()
        calls = []
        active = False

        def run(arguments, check=True):
            nonlocal active
            calls.append(arguments)
            if arguments[:2] == ["loginctl", "show-user"]:
                return subprocess.CompletedProcess(arguments, 0, b"yes\n")
            if "is-active" in arguments:
                return subprocess.CompletedProcess(arguments, 0 if active else 3, b"")
            if "start" in arguments:
                active = True
            return subprocess.CompletedProcess(arguments, 0, b"")

        args = argparse.Namespace(
            archive=str(self.archive), sha256=artifact["archiveSha256"], port=4096
        )
        with (
            mock.patch.object(installer.Path, "home", return_value=home),
            mock.patch.object(installer, "check_host"),
            mock.patch.object(installer, "command", side_effect=run),
            mock.patch.object(installer, "check_ssh_policy"),
            mock.patch.object(installer, "probe", return_value=True),
            mock.patch.object(installer.platform, "machine", return_value="x86_64"),
        ):
            first = installer.install(args, phone())
            second = installer.install(args, phone("phone-b", b"b"))
        self.assertEqual(first["hostId"], second["hostId"])
        self.assertEqual(sum("start" in call for call in calls), 1)
        self.assertFalse(any("restart" in call or "sudo" in call for call in calls))
        text = (home / ".config/systemd/user/oc-byo-host.service").read_text()
        self.assertNotIn(phone()["token"], text)
        self.assertIn("--port 4096", text)

    def test_linger_privilege_failure_never_pairs_or_prompts(self):
        artifact = self.package()
        home = self.root / "home"
        home.mkdir()
        calls = []

        def run(arguments, check=True):
            calls.append(arguments)
            return subprocess.CompletedProcess(arguments, 1, b"")

        args = argparse.Namespace(
            archive=str(self.archive), sha256=artifact["archiveSha256"], port=4096
        )
        with (
            mock.patch.object(installer.Path, "home", return_value=home),
            mock.patch.object(installer, "check_host"),
            mock.patch.object(installer, "command", side_effect=run),
            mock.patch.object(installer, "check_ssh_policy"),
            mock.patch.object(installer.platform, "machine", return_value="x86_64"),
        ):
            with self.assertRaisesRegex(installer.InstallError, "lingerRequired"):
                installer.install(args, phone())
        self.assertFalse((home / ".ssh/authorized_keys").exists())
        self.assertFalse(any("enable-linger" in call or "sudo" in call for call in calls))

    def test_root_or_wrong_os_install_refused(self):
        with (
            mock.patch.object(installer.os, "getuid", return_value=0),
            mock.patch.object(
                installer.Path,
                "read_text",
                return_value='ID=ubuntu\nVERSION_ID="24.04"',
            ),
        ):
            with self.assertRaisesRegex(installer.InstallError, "unsupportedHost"):
                installer.check_host()
        with (
            mock.patch.object(installer.os, "getuid", return_value=1000),
            mock.patch.object(
                installer.Path, "read_text", return_value='ID=debian\nVERSION_ID="12"'
            ),
        ):
            with self.assertRaisesRegex(installer.InstallError, "unsupportedHost"):
                installer.check_host()

    def test_ssh_policy_proves_actual_connection_and_rejects_reverse_or_unix_forwarding(
        self,
    ):
        safe = (
            "allowtcpforwarding local\nallowstreamlocalforwarding no\npermittunnel no\n"
            "usedns no\ndisableforwarding no\npubkeyauthentication yes\n"
            "authorizedkeysfile .ssh/authorized_keys .ssh/authorized_keys2\npermitlisten any\n"
        )
        with (
            mock.patch.dict(
                installer.os.environ,
                {"SSH_CONNECTION": "100.64.0.1 1234 100.64.0.2 22"},
            ),
            mock.patch.object(installer, "command") as run,
        ):
            run.return_value = subprocess.CompletedProcess([], 0, safe.encode())
            self.check_policy()
            self.assertIn(
                "user=alice,host=100.64.0.1,addr=100.64.0.1,laddr=100.64.0.2,lport=22",
                run.call_args[0][0],
            )
            for bad in (
                safe.replace("allowtcpforwarding local", "allowtcpforwarding yes"),
                safe.replace(
                    "allowstreamlocalforwarding no", "allowstreamlocalforwarding local"
                ),
                safe.replace("permittunnel no", "permittunnel yes"),
                safe.replace("usedns no", "usedns yes"),
                safe.replace(
                    ".ssh/authorized_keys .ssh/authorized_keys2", "/etc/custom_keys"
                ),
            ):
                run.return_value = subprocess.CompletedProcess([], 0, bad.encode())
                with self.assertRaisesRegex(
                    installer.InstallError, "sshPolicyRequired"
                ):
                    self.check_policy()
            run.return_value = subprocess.CompletedProcess(
                [],
                0,
                safe.replace("allowtcpforwarding local", "allowtcpforwarding yes")
                .replace("permitlisten any", "permitlisten none")
                .encode(),
            )
            self.check_policy()
        with mock.patch.dict(installer.os.environ, {}, clear=True):
            with self.assertRaisesRegex(installer.InstallError, "sshPolicyRequired"):
                self.check_policy()


class ListenerTests(unittest.TestCase):
    def test_actual_private_ssh_listener_required(self):
        for address in ("100.80.1.2", "[fd7a:115c:a1e0::1234]", "127.0.0.1"):
            with mock.patch.object(installer, "command", return_value=subprocess.CompletedProcess([], 0, f"LISTEN 0 128 {address}:22 *:*\n".encode())):
                installer.check_ssh_listener("22")
        for text in ("", "LISTEN 0 128 0.0.0.0:22 *:*", "LISTEN 0 128 [::]:22 *:*", "LISTEN 0 128 203.0.113.1:22 *:*", "malformed"):
            with mock.patch.object(installer, "command", return_value=subprocess.CompletedProcess([], 0, text.encode())):
                with self.assertRaisesRegex(installer.InstallError, "sshPolicyRequired"):
                    installer.check_ssh_listener("22")


class ChildProcessTests(unittest.TestCase):
    def test_child_gets_only_private_env_and_revoke_does_not_kill_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binary = root / "fake-opencode"
            binary.write_text("""#!/usr/bin/env python3
import argparse, base64, json, os
from http.server import HTTPServer, BaseHTTPRequestHandler
p=argparse.ArgumentParser();p.add_argument('serve');p.add_argument('--hostname');p.add_argument('--port',type=int);a=p.parse_args()
assert a.hostname == '127.0.0.1'
assert os.environ['OPENCODE_SERVER_USERNAME'] == 'opencode'
print(os.environ['OPENCODE_SERVER_PASSWORD'], flush=True)
class H(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self):
  expected='Basic '+base64.b64encode(('opencode:'+os.environ['OPENCODE_SERVER_PASSWORD']).encode()).decode()
  status=200 if self.headers.get('Authorization') == expected else 401
  data=json.dumps({'pid':os.getpid()}).encode()
  self.send_response(status);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
HTTPServer((a.hostname,a.port),H).serve_forever()
""")
            binary.chmod(0o700)
            port = host.free_loopback_port()
            state = host.State(root / "state", root / "keys", port)
            a, b = phone(), phone("phone-b", b"b")
            state.pair(a)
            state.pair(b)
            process = subprocess.Popen(
                [
                    sys.executable,
                    str(ROOT / "supervisor.py"),
                    "serve",
                    "--state-dir",
                    str(state.directory),
                    "--authorized-keys",
                    str(root / "keys"),
                    "--port",
                    str(port),
                    "--opencode",
                    str(binary),
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

            def request(method, path, payload):
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
                try:
                    connection.request(
                        method, path, headers={"Authorization": auth(payload)}
                    )
                    response = connection.getresponse()
                    return response.status, json.loads(response.read())
                finally:
                    connection.close()

            try:
                deadline = time.monotonic() + 5
                while True:
                    try:
                        _, before = request("GET", "/global/health", b)
                        break
                    except (OSError, http.client.HTTPException):
                        if time.monotonic() > deadline:
                            self.fail("fake child did not become ready")
                        time.sleep(0.05)
                self.assertEqual(request("POST", "/_oc/revoke", a)[0], 200)
                self.assertEqual(request("GET", "/global/health", b)[1], before)
                self.assertIsNone(process.poll())
            finally:
                process.terminate()
                stdout, stderr = process.communicate(timeout=8)
            self.assertEqual(stdout, b"")
            self.assertEqual(stderr, b"")


if __name__ == "__main__":
    unittest.main()
