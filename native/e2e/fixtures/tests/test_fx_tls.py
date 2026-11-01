"""FX-TLS self-tests: listener certificates, cert swap, redirect, request log, SSE pass-through."""

from __future__ import annotations

import base64
import hashlib
import http.client
import json
import socket
import ssl
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from tests.fxtest import FixtureCase, basic, request


class _Upstream(BaseHTTPRequestHandler):
    """A tiny stand-in for FX-OC2's front: /echo and a two-part SSE stream."""

    protocol_version = "HTTP/1.1"
    sse_release = threading.Event()

    def log_message(self, *args: object) -> None:
        return

    def do_GET(self) -> None:  # noqa: N802
        if self.path.startswith("/sse"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()  # close-delimited stream: no length, no chunking
            self.close_connection = True
            self.wfile.write(b"data: first\n\n")
            self.wfile.flush()
            self.sse_release.wait(10)
            self.wfile.write(b"data: second\n\n")
            self.wfile.flush()
            return
        self._echo()

    def do_POST(self) -> None:  # noqa: N802
        self._echo()

    def _echo(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        auth = self.headers.get("Authorization") or ""
        raw = json.dumps({
            "method": self.command,
            "path": self.path,
            "auth_scheme": auth.split(" ", 1)[0].lower() if auth else "none",
            "body_len": len(body),
            "forwarded_proto": self.headers.get("X-Forwarded-Proto"),
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)


def _sha256(der: bytes) -> str:
    return hashlib.sha256(der).hexdigest()


def _peer_der(port: int) -> bytes:
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with socket.create_connection(("127.0.0.1", port), timeout=5) as raw:
        with ctx.wrap_socket(raw, server_hostname="127.0.0.1") as tls:
            return tls.getpeercert(binary_form=True)


def _verify(port: int, cafile: Path | None, hostname: str = "127.0.0.1") -> ssl.SSLSocket:
    ctx = ssl.create_default_context(cafile=str(cafile) if cafile else None)
    if cafile:
        # Only the given certificate is a trust anchor (no system roots involved).
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.load_verify_locations(cafile=str(cafile))
    raw = socket.create_connection(("127.0.0.1", port), timeout=5)
    try:
        return ctx.wrap_socket(raw, server_hostname=hostname)
    except Exception:
        raw.close()
        raise


class FxTlsTest(FixtureCase):
    FIXTURE = "FX-TLS"

    @classmethod
    def setUpClass(cls) -> None:
        cls.upstream = ThreadingHTTPServer(("127.0.0.1", 0), _Upstream)
        cls.upstream.daemon_threads = True
        threading.Thread(target=cls.upstream.serve_forever, daemon=True).start()
        cls.EXTRA_ENV = {"FX_TLS_UPSTREAM_PORT": str(cls.upstream.server_address[1])}
        try:
            super().setUpClass()
        except Exception:
            cls.upstream.shutdown()
            cls.upstream.server_close()
            raise
        cls.certs = cls.run_dir / "FX-TLS" / "certs"

    @classmethod
    def tearDownClass(cls) -> None:
        try:
            super().tearDownClass()
        finally:
            _Upstream.sse_release.set()
            cls.upstream.shutdown()
            cls.upstream.server_close()

    def setUp(self) -> None:
        self.ctl("reset")
        self.ctl("swap-cert", {"to": "A"})

    # -- ready.json --------------------------------------------------------------

    def test_ready_lists_listeners_and_fingerprints(self) -> None:
        listeners = self.ready["listeners"]
        for name in ("selfsigned", "expired", "trusted", "redirect", "plain_private", "plain_public", "mismatch"):
            self.assertIn(name, listeners)
        self.assertFalse(listeners["trusted"]["available"])  # no --trusted-cert in this test
        self.assertEqual(listeners["plain_public"]["host"], "oc-e2e.example.net")
        self.assertTrue(listeners["selfsigned"]["url"].startswith("https://"))
        self.assertTrue(listeners["plain_private"]["url"].startswith("http://10.0.2.2:"))
        fps = self.ready["fingerprints"]
        self.assertEqual(set(fps), {"A", "B", "expired", "mismatch"})
        self.assertEqual(len({fp["sha256"] for fp in fps.values()}), 4)
        self.assertRegex(fps["A"]["sha256_colons"], r"^([0-9A-F]{2}:){31}[0-9A-F]{2}$")
        self.assertEqual(self.ready["upstream"]["port"], self.upstream.server_address[1])

    # -- certificates ------------------------------------------------------------

    def test_selfsigned_presents_A_and_verifies_when_pinned(self) -> None:
        der = _peer_der(self.port("selfsigned"))
        self.assertEqual(_sha256(der), self.ready["fingerprints"]["A"]["sha256"])
        for host in ("127.0.0.1", "localhost"):
            with _verify(self.port("selfsigned"), self.certs / "A.crt", host) as tls:
                self.assertEqual(_sha256(tls.getpeercert(binary_form=True)), self.ready["fingerprints"]["A"]["sha256"])
        # Not trusted without the pin.
        with self.assertRaises(ssl.SSLCertVerificationError):
            _verify(self.port("selfsigned"), self.certs / "B.crt").close()

    def test_swap_cert_changes_presented_fingerprint(self) -> None:
        self.assertEqual(self.ctl("swap-cert", {"to": "B"}).json()["current"], "B")
        self.assertEqual(_sha256(_peer_der(self.port("selfsigned"))), self.ready["fingerprints"]["B"]["sha256"])
        with self.assertRaises(ssl.SSLCertVerificationError):
            _verify(self.port("selfsigned"), self.certs / "A.crt").close()
        self.assertEqual(self.ctl("swap-cert", {"to": "C"}).status, 400)
        self.ctl("swap-cert", {"to": "A"})
        self.assertEqual(_sha256(_peer_der(self.port("selfsigned"))), self.ready["fingerprints"]["A"]["sha256"])

    def test_expired_cert_fails_as_expired(self) -> None:
        self.assertEqual(_sha256(_peer_der(self.port("expired"))), self.ready["fingerprints"]["expired"]["sha256"])
        with self.assertRaises(ssl.SSLCertVerificationError) as err:
            _verify(self.port("expired"), self.certs / "expired.crt").close()
        self.assertEqual(err.exception.verify_code, 10)  # X509_V_ERR_CERT_HAS_EXPIRED
        self.assertLess(self.ready["fingerprints"]["expired"]["not_after_epoch"], time.time())

    def test_mismatch_cert_fails_on_name(self) -> None:
        self.assertEqual(_sha256(_peer_der(self.port("mismatch"))), self.ready["fingerprints"]["mismatch"]["sha256"])
        with self.assertRaises(ssl.SSLCertVerificationError) as err:
            _verify(self.port("mismatch"), self.certs / "mismatch.crt").close()
        self.assertIn("mismatch", str(err.exception).lower())
        # The same cert verifies for the name it was made for.
        name = self.ready["fingerprints"]["mismatch"]["name"]
        with _verify(self.port("mismatch"), self.certs / "mismatch.crt", name):
            pass

    def test_untrusted_selfsigned_fails_with_default_roots(self) -> None:
        with self.assertRaises(ssl.SSLCertVerificationError) as err:
            _verify(self.port("selfsigned"), None).close()
        self.assertIn(err.exception.verify_code, (18, 19, 20))

    # -- proxying ----------------------------------------------------------------

    def _pinned(self, name: str = "A") -> ssl.SSLContext:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.load_verify_locations(cafile=str(self.certs / f"{name}.crt"))
        return ctx

    def test_proxies_over_https_and_http(self) -> None:
        r = request("POST", self.port("selfsigned"), "/echo?x=1", {"a": 1}, tls=self._pinned())
        self.assertEqual(r.status, 200)
        self.assertEqual(r.json()["path"], "/echo?x=1")
        self.assertEqual(r.json()["forwarded_proto"], "https")
        self.assertGreater(r.json()["body_len"], 0)
        for name in ("plain_private", "plain_public"):
            r = request("GET", self.port(name), "/echo")
            self.assertEqual(r.json()["forwarded_proto"], "http")

    def test_keep_alive_post_after_get_forwards_its_body(self) -> None:
        conn = http.client.HTTPConnection("127.0.0.1", self.port("plain_private"), timeout=5)
        try:
            conn.request("GET", "/echo")
            self.assertEqual(json.loads(conn.getresponse().read())["body_len"], 0)
            conn.request("POST", "/echo", body=b'{"k": 1}', headers={"Content-Type": "application/json"})
            self.assertEqual(json.loads(conn.getresponse().read())["body_len"], 8)
        finally:
            conn.close()

    def test_log_has_transport_listener_and_auth_scheme_never_the_value(self) -> None:
        password = self.secrets["FX_OC2_PASSWORD"]
        self.ctl("log/clear")
        request("GET", self.port("selfsigned"), "/echo", headers=basic("opencode", password), tls=self._pinned())
        request("GET", self.port("plain_private"), "/echo", headers=basic("opencode", password))
        request("GET", self.port("plain_public"), "/echo")
        log = [e for e in self.requests_log() if e.get("path") == "/echo"]
        by_listener = {e["listener"]: e for e in log}
        self.assertEqual(by_listener["selfsigned"]["transport"], "https")
        self.assertEqual(by_listener["selfsigned"]["auth"], "basic")
        self.assertEqual(by_listener["plain_private"]["transport"], "http")
        self.assertEqual(by_listener["plain_private"]["auth"], "basic")
        self.assertEqual(by_listener["plain_public"]["auth"], "none")
        self.assertTrue(by_listener["plain_private"]["basic_on_plaintext"])
        self.assertFalse(by_listener["plain_public"]["basic_on_plaintext"])
        token = base64.b64encode(f"opencode:{password}".encode()).decode()
        self.assert_no_secret_in_run_dir(password, token)

    def test_handshake_failures_are_logged(self) -> None:
        self.ctl("log/clear")
        with self.assertRaises(ssl.SSLCertVerificationError):
            _verify(self.port("expired"), self.certs / "expired.crt").close()
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            events = [e for e in self.requests_log() if e.get("event") == "handshake" and e.get("listener") == "expired"]
            if events:
                break
            time.sleep(0.05)
        self.assertTrue(events)
        self.assertFalse(events[0]["ok"])

    def test_redirect_default_and_configured(self) -> None:
        r = request("GET", self.port("redirect"), "/api/session?x=1")
        self.assertEqual(r.status, 307)
        self.assertEqual(r.headers["location"], f"http://127.0.0.1:{self.port('plain_public')}/api/session?x=1")
        set_r = self.ctl("set-redirect", {"location": "https://elsewhere.example.invalid", "status": 301})
        self.assertEqual(set_r.status, 200)
        r = request("POST", self.port("redirect"), "/x", {"k": 1})
        self.assertEqual((r.status, r.headers["location"]), (301, "https://elsewhere.example.invalid/x"))
        self.assertEqual(self.ctl("set-redirect", {"status": 200}).status, 400)
        log = [e for e in self.requests_log() if e.get("listener") == "redirect"]
        self.assertTrue(log)

    def test_sse_streams_without_buffering(self) -> None:
        _Upstream.sse_release.clear()
        conn = http.client.HTTPSConnection("127.0.0.1", self.port("selfsigned"), timeout=5, context=self._pinned())
        try:
            conn.request("GET", "/sse")
            resp = conn.getresponse()
            self.assertEqual(resp.status, 200)
            self.assertIn("text/event-stream", resp.getheader("Content-Type"))
            first = b""
            while b"\n\n" not in first:
                chunk = resp.read1(1024)
                self.assertTrue(chunk, "stream ended early")
                first += chunk
            # The second event is held back upstream: the first arrived on its own.
            self.assertIn(b"data: first", first)
            self.assertNotIn(b"second", first)
            _Upstream.sse_release.set()
            rest = resp.read()
            self.assertIn(b"data: second", first + rest)
        finally:
            _Upstream.sse_release.set()
            conn.close()

    def test_upstream_down_answers_502(self) -> None:
        r = self.ctl("set-upstream", {"port": 1})
        self.assertEqual(r.status, 200)
        try:
            self.assertEqual(request("GET", self.port("plain_private"), "/echo").status, 502)
        finally:
            self.ctl("set-upstream", {"port": self.upstream.server_address[1]})
