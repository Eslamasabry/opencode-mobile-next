#!/usr/bin/env python3
"""FX-TLS: reverse proxies in front of FX-OC2 for transport-trust flows (E2E-03, E2E-04).

Listeners (ports.json FX-TLS; all bind 127.0.0.1, the emulator reaches them as 10.0.2.2):

  selfsigned     HTTPS, self-signed cert A (or B after ``swap-cert``), SANs 127.0.0.1, 10.0.2.2, localhost
  expired        HTTPS, self-signed cert that expired in 2021 (same SANs)
  mismatch       HTTPS, self-signed cert for ``wrong-host.example.invalid`` only
  trusted        HTTPS with ``--trusted-cert/--trusted-key`` (the PC's tailscale cert); unavailable without them
  redirect       plain HTTP (``--redirect-transport https`` for cert A) answering every request with a
                 redirect to another origin; default 307 to the plain_public listener, path kept
  plain_private  plain HTTP proxy, meant to be reached on a private address (10.0.2.2)
  plain_public   plain HTTP proxy, meant to be reached on a public-looking name (``--public-name``,
                 default oc-e2e.example.net; mapping that name to the PC is the runner's job)

Certificates are made per run in ``<run-dir>/FX-TLS/certs/`` with the ``openssl`` CLI and never committed.

Options (each also read from the environment, since fxctl passes only ``--port``):
  --upstream PORT            FX_TLS_UPSTREAM_PORT   default ports.json FX-OC2.front
  --trusted-cert FILE        FX_TLS_TRUSTED_CERT
  --trusted-key FILE         FX_TLS_TRUSTED_KEY
  --trusted-name NAME        FX_TLS_TRUSTED_NAME    default: first DNS SAN of the trusted cert
  --public-name NAME         FX_TLS_PUBLIC_NAME     default oc-e2e.example.net
  --redirect-transport T     FX_TLS_REDIRECT_TRANSPORT  http (default) | https

Control (POST on ctl, JSON body), besides the standard /health /state /log /reset /fault ...:
  swap-cert     {"to": "A"|"B"}                -> {"current": "A"|"B", "sha256": ...}; new handshakes only
  set-redirect  {"location"?: str, "status"?: 301|302|307|308, "append_path"?: bool}
                location is a prefix: the request path and query are appended unless append_path is false
  set-upstream  {"port": N}                     -> point the proxies at another upstream port
  /reset restores cert A, the default redirect and the default upstream.

Request log lines (requests.jsonl) carry ``listener``, ``transport`` (http|https), ``auth`` (scheme only),
``basic_on_plaintext`` and ``upstream_status``; failed TLS handshakes are logged as
``{"event": "handshake", "ok": false, "listener", "error"}``.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import http.client
import os
import socket
import ssl
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import (  # noqa: E402
    LOOPBACK,
    SCRUB,
    Fixture,
    FxHandler,
    FxServer,
    base_parser,
    load_ports,
    run_fixture,
)

EMULATOR_HOST = "10.0.2.2"
DEFAULT_PUBLIC_NAME = "oc-e2e.example.net"
MISMATCH_NAME = "wrong-host.example.invalid"
SANS = "IP:127.0.0.1,IP:10.0.2.2,DNS:localhost"
HOP_BY_HOP = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "proxy-connection",
              "te", "trailer", "trailers", "transfer-encoding", "upgrade"}
HTTPS_LISTENERS = ("selfsigned", "expired", "mismatch", "trusted")
PLAIN_LISTENERS = ("plain_private", "plain_public")

# --------------------------------------------------------------------------- certificates

_OPENSSL_CFG = """\
[ ca ]
default_ca = fxca
[ fxca ]
dir = {work}
database = $dir/index.txt
new_certs_dir = $dir/new
default_md = sha256
policy = fxpolicy
unique_subject = no
copy_extensions = none
email_in_dn = no
rand_serial = yes
[ fxpolicy ]
commonName = supplied
[ req ]
distinguished_name = dn
prompt = no
[ dn ]
CN = {cn}
[ ext ]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = {san}
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
"""


def _asn1(when: dt.datetime) -> str:
    return when.astimezone(dt.timezone.utc).strftime("%Y%m%d%H%M%SZ")


def make_cert(out: Path, name: str, cn: str, san: str, start: dt.datetime, end: dt.datetime) -> tuple[Path, Path]:
    """A self-signed leaf (CA:FALSE, serverAuth) valid from start to end, via ``openssl ca -selfsign``."""
    work = out / f".work-{name}"
    (work / "new").mkdir(parents=True, exist_ok=True)
    (work / "index.txt").write_text("", encoding="utf-8")
    cfg = work / "openssl.cnf"
    cfg.write_text(_OPENSSL_CFG.format(work=work, cn=cn, san=san), encoding="utf-8")
    key, csr, crt = out / f"{name}.key", work / "req.csr", out / f"{name}.crt"

    def run(*argv: str) -> None:
        result = subprocess.run(["openssl", *argv], capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            raise RuntimeError(f"openssl {argv[0]} failed for {name}: {result.stderr.strip()[-400:]}")

    run("genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-256", "-out", str(key))
    os.chmod(key, 0o600)
    run("req", "-new", "-config", str(cfg), "-key", str(key), "-out", str(csr))
    run("ca", "-batch", "-config", str(cfg), "-selfsign", "-keyfile", str(key), "-in", str(csr), "-out", str(crt),
        "-extensions", "ext", "-startdate", _asn1(start), "-enddate", _asn1(end), "-notext")
    return crt, key


def cert_facts(crt: Path) -> dict[str, Any]:
    pem = crt.read_text(encoding="utf-8")
    start = pem.index("-----BEGIN CERTIFICATE-----")
    end = pem.index("-----END CERTIFICATE-----") + len("-----END CERTIFICATE-----")
    der = ssl.PEM_cert_to_DER_cert(pem[start:end])
    digest = hashlib.sha256(der).hexdigest()
    facts: dict[str, Any] = {
        "sha256": digest,
        "sha256_colons": ":".join(digest[i:i + 2] for i in range(0, 64, 2)).upper(),
        "file": str(crt),
    }
    out = subprocess.run(["openssl", "x509", "-in", str(crt), "-noout", "-enddate", "-ext", "subjectAltName"],
                         capture_output=True, text=True, timeout=30).stdout
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("notAfter="):
            facts["not_after"] = line.split("=", 1)[1]
            facts["not_after_epoch"] = ssl.cert_time_to_seconds(facts["not_after"])
        elif "DNS:" in line or "IP Address:" in line:
            facts["sans"] = [part.strip() for part in line.split(",")]
    return facts


def server_context(crt: Path, key: Path) -> ssl.SSLContext:
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(str(crt), str(key))
    return ctx


# --------------------------------------------------------------------------- servers


class TlsServer(FxServer):
    """An FxServer whose sockets are wrapped per connection, in the connection's own
    thread, with whatever context ``context_for()`` returns at that moment (so a
    swap affects new handshakes only)."""

    listener = ""
    transport = "https"

    def __init__(self, port: int, handler: Any, fixture: Fixture, listener: str, context_for: Any) -> None:
        self.listener = listener
        self.context_for = context_for
        super().__init__(port, handler, fixture)

    def finish_request(self, request: Any, client_address: Any) -> None:
        request.settimeout(15)
        try:
            tls = self.context_for().wrap_socket(request, server_side=True)
        except (ssl.SSLError, OSError) as err:
            reason = getattr(err, "reason", None) or type(err).__name__
            self.fixture.log.write(event="handshake", ok=False, listener=self.listener, transport="https",
                                   error=str(reason))
            return
        try:
            tls.settimeout(None)
            self.RequestHandlerClass(tls, client_address, self)
        finally:
            try:
                tls.close()
            except OSError:
                pass


class PlainServer(FxServer):
    transport = "http"

    def __init__(self, port: int, handler: Any, fixture: Fixture, listener: str) -> None:
        self.listener = listener
        super().__init__(port, handler, fixture)


class ProxyHandler(FxHandler):
    """Forwards every request to the upstream; streams bodies without buffering."""

    def setup(self) -> None:
        super().setup()
        self.transport = self.server.transport  # type: ignore[attr-defined]
        self.listener = self.server.listener  # type: ignore[attr-defined]

    def parse_request(self) -> bool:
        # Local workaround: core caches _body/_extra on the handler, which lives for the whole
        # keep-alive connection; clear them per request.
        self.__dict__.pop("_body", None)
        self.__dict__.pop("_extra", None)
        return super().parse_request()

    def _request_body(self) -> bytes:
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    break
                parts.append(self.rfile.read(size))
                self.rfile.readline()
            self._body = b"".join(parts)
            return self._body
        return self.body_bytes()

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        state: TlsState = self.fixture.tls  # type: ignore[attr-defined]
        scheme = (self.headers.get("Authorization") or "").split(" ", 1)[0].lower()
        self.note(listener=self.listener, basic_on_plaintext=(self.transport == "http" and scheme == "basic"))
        body = self._request_body()
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP and k.lower() != "host"}
        headers["Host"] = f"{LOOPBACK}:{state.upstream_port}"
        headers["X-Forwarded-Proto"] = self.transport
        headers["X-Forwarded-Host"] = self.headers.get("Host", "")
        headers["X-Forwarded-For"] = self.client_address[0]
        if body or method in ("POST", "PUT", "PATCH"):
            headers["Content-Length"] = str(len(body))
        upstream = http.client.HTTPConnection(LOOPBACK, state.upstream_port, timeout=300)
        try:
            try:
                upstream.request(method, self.path, body=body or None, headers=headers)
                resp = upstream.getresponse()
            except OSError as err:
                self.note(upstream_error=type(err).__name__)
                self.send_json({"error": "upstream unavailable"}, 502)
                return
            self.note(upstream_status=resp.status)
            self._relay(resp)
        finally:
            upstream.close()

    def _relay(self, resp: http.client.HTTPResponse) -> None:
        self.send_response(resp.status, resp.reason)
        length = resp.getheader("Content-Length")
        no_body = self.command == "HEAD" or resp.status in (204, 304) or 100 <= resp.status < 200
        for key, value in resp.getheaders():
            if key.lower() in HOP_BY_HOP or key.lower() == "content-length":
                continue
            self.send_header(key, value)
        chunked = False
        if no_body:
            if length is not None:
                self.send_header("Content-Length", length)
        elif length is not None:
            self.send_header("Content-Length", length)
        else:
            chunked = True
            self.send_header("Transfer-Encoding", "chunked")
            if "text/event-stream" in (resp.getheader("Content-Type") or ""):
                self.send_header("X-Accel-Buffering", "no")
        self.end_headers()
        self.wfile.flush()
        self._status = resp.status
        if no_body:
            return
        deadline = time.monotonic() + self.cut_after_ms / 1000 if self.cut_after_ms is not None else None
        while True:
            if deadline is not None and time.monotonic() >= deadline:
                self.note(cut=True)
                self.hard_close()
                return
            data = resp.read1(65536)
            if not data:
                break
            if chunked:
                self.wfile.write(b"%x\r\n%s\r\n" % (len(data), data))
            else:
                self.wfile.write(data)
            self.wfile.flush()
        if chunked:
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()


class RedirectHandler(FxHandler):
    def setup(self) -> None:
        super().setup()
        self.transport = self.server.transport  # type: ignore[attr-defined]

    def parse_request(self) -> bool:
        # Local workaround: core caches _body/_extra on the handler, which lives for the whole
        # keep-alive connection; clear them per request.
        self.__dict__.pop("_body", None)
        self.__dict__.pop("_extra", None)
        return super().parse_request()

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        state: TlsState = self.fixture.tls  # type: ignore[attr-defined]
        self.note(listener="redirect")
        self.body_bytes()  # drain so the connection stays usable
        location = state.redirect_location
        if state.redirect_append_path:
            location = location.rstrip("/") + self.path
        self.note(redirect_status=state.redirect_status)
        self.send_empty(state.redirect_status, {"Location": location})


# --------------------------------------------------------------------------- state


class TlsState:
    def __init__(self, fx: Fixture, upstream_port: int) -> None:
        self.fx = fx
        self.lock = threading.Lock()
        self.default_upstream = upstream_port
        self.upstream_port = upstream_port
        self.contexts: dict[str, ssl.SSLContext] = {}
        self.current = "A"
        self.default_redirect = f"http://{LOOPBACK}:{fx.ports['plain_public']}"
        self.redirect_location = self.default_redirect
        self.redirect_status = 307
        self.redirect_append_path = True

    def selfsigned_context(self) -> ssl.SSLContext:
        with self.lock:
            return self.contexts[self.current]

    def publish(self) -> None:
        self.fx.set_state(current_cert=self.current, upstream_port=self.upstream_port,
                          redirect={"location": self.redirect_location, "status": self.redirect_status,
                                    "append_path": self.redirect_append_path})

    def reset(self) -> None:
        with self.lock:
            self.current = "A"
            self.upstream_port = self.default_upstream
            self.redirect_location = self.default_redirect
            self.redirect_status = 307
            self.redirect_append_path = True
        self.publish()


def _env_default(name: str, fallback: str | None = None) -> str | None:
    return os.environ.get(name) or fallback


def build_parser() -> argparse.ArgumentParser:
    parser = base_parser("FX-TLS reverse proxies in front of FX-OC2")
    upstream_env = _env_default("FX_TLS_UPSTREAM_PORT")
    parser.add_argument("--upstream", type=int, default=int(upstream_env) if upstream_env else None,
                        help="upstream port on 127.0.0.1 (default FX-OC2.front)")
    parser.add_argument("--trusted-cert", default=_env_default("FX_TLS_TRUSTED_CERT"))
    parser.add_argument("--trusted-key", default=_env_default("FX_TLS_TRUSTED_KEY"))
    parser.add_argument("--trusted-name", default=_env_default("FX_TLS_TRUSTED_NAME"))
    parser.add_argument("--public-name", default=_env_default("FX_TLS_PUBLIC_NAME", DEFAULT_PUBLIC_NAME))
    parser.add_argument("--redirect-transport", choices=("http", "https"),
                        default=_env_default("FX_TLS_REDIRECT_TRANSPORT", "http"))
    return parser


def setup(fx: Fixture) -> dict[str, Any]:
    args = fx.args
    for name in ("FX_OC2_PASSWORD", "FX_OC2_B_PASSWORD", "FX_GIT_PASSWORD"):
        SCRUB.add(os.environ.get(name))
    upstream = args.upstream or load_ports("FX-OC2")["front"]
    state = TlsState(fx, upstream)
    fx.tls = state  # type: ignore[attr-defined]

    certs = fx.dir / "certs"
    certs.mkdir(parents=True, exist_ok=True)
    now = dt.datetime.now(dt.timezone.utc)
    valid = (now - dt.timedelta(days=1), now + dt.timedelta(days=365))
    paths = {
        "A": make_cert(certs, "A", "fx-tls-A", SANS, *valid),
        "B": make_cert(certs, "B", "fx-tls-B", SANS, *valid),
        "expired": make_cert(certs, "expired", "fx-tls-expired", SANS,
                             dt.datetime(2020, 1, 1, tzinfo=dt.timezone.utc),
                             dt.datetime(2021, 1, 1, tzinfo=dt.timezone.utc)),
        "mismatch": make_cert(certs, "mismatch", MISMATCH_NAME, f"DNS:{MISMATCH_NAME}", *valid),
    }
    fingerprints = {name: cert_facts(crt) for name, (crt, _key) in paths.items()}
    fingerprints["mismatch"]["name"] = MISMATCH_NAME
    state.contexts = {name: server_context(crt, key) for name, (crt, key) in paths.items()}

    listeners: dict[str, dict[str, Any]] = {}

    def https(name: str, context_for: Any, host: str = EMULATOR_HOST, **extra: Any) -> None:
        port = fx.ports[name]
        fx.serve(name, ProxyHandler, TlsServer(port, ProxyHandler, fx, name, context_for))
        listeners[name] = {"available": True, "transport": "https", "port": port, "host": host,
                           "url": f"https://{host}:{port}", "local_url": f"https://{LOOPBACK}:{port}", **extra}

    https("selfsigned", state.selfsigned_context, cert="A (swappable to B)")
    expired_ctx = state.contexts["expired"]
    https("expired", lambda: expired_ctx, cert="expired")
    mismatch_ctx = state.contexts["mismatch"]
    https("mismatch", lambda: mismatch_ctx, cert="mismatch", cert_name=MISMATCH_NAME)

    if args.trusted_cert and args.trusted_key:
        trusted_crt, trusted_key = Path(args.trusted_cert), Path(args.trusted_key)
        trusted_ctx = server_context(trusted_crt, trusted_key)
        facts = cert_facts(trusted_crt)
        name = args.trusted_name or next(
            (s.split(":", 1)[1] for s in facts.get("sans", []) if s.startswith("DNS:")), EMULATOR_HOST)
        https("trusted", lambda: trusted_ctx, host=name, cert_sha256=facts["sha256"])
    else:
        listeners["trusted"] = {"available": False, "transport": "https", "port": fx.ports["trusted"],
                                "reason": "no --trusted-cert/--trusted-key (FX_TLS_TRUSTED_CERT/KEY) given"}

    for name, host in (("plain_private", EMULATOR_HOST), ("plain_public", args.public_name)):
        port = fx.ports[name]
        fx.serve(name, ProxyHandler, PlainServer(port, ProxyHandler, fx, name))
        listeners[name] = {"available": True, "transport": "http", "port": port, "host": host,
                           "url": f"http://{host}:{port}", "local_url": f"http://{LOOPBACK}:{port}"}
    listeners["plain_public"]["needs_host_mapping"] = f"{args.public_name} -> the PC (10.0.2.2 from the emulator)"

    port = fx.ports["redirect"]
    if args.redirect_transport == "https":
        fx.serve("redirect", RedirectHandler, TlsServer(port, RedirectHandler, fx, "redirect", state.selfsigned_context))
    else:
        fx.serve("redirect", RedirectHandler, PlainServer(port, RedirectHandler, fx, "redirect"))
    listeners["redirect"] = {"available": True, "transport": args.redirect_transport, "port": port,
                             "host": EMULATOR_HOST, "url": f"{args.redirect_transport}://{EMULATOR_HOST}:{port}",
                             "local_url": f"{args.redirect_transport}://{LOOPBACK}:{port}",
                             "default_location": state.default_redirect, "default_status": 307}

    @fx.control("swap-cert")
    def swap_cert(body: dict[str, Any]) -> dict[str, Any]:
        to = body.get("to")
        if to not in ("A", "B"):
            raise ValueError('to must be "A" or "B"')
        with state.lock:
            state.current = to
        state.publish()
        return {"current": to, "sha256": fingerprints[to]["sha256"]}

    @fx.control("set-redirect")
    def set_redirect(body: dict[str, Any]) -> dict[str, Any]:
        status = body.get("status", state.redirect_status)
        if status not in (301, 302, 307, 308):
            raise ValueError("status must be 301, 302, 307 or 308")
        location = body.get("location", state.redirect_location)
        if not isinstance(location, str) or "://" not in location:
            raise ValueError("location must be an absolute URL")
        with state.lock:
            state.redirect_status = status
            state.redirect_location = location
            state.redirect_append_path = bool(body.get("append_path", True))
        state.publish()
        return {"location": location, "status": status, "append_path": state.redirect_append_path}

    @fx.control("set-upstream")
    def set_upstream(body: dict[str, Any]) -> dict[str, Any]:
        port = body.get("port")
        if not isinstance(port, int) or not 0 < port < 65536:
            raise ValueError("port must be an integer port number")
        with state.lock:
            state.upstream_port = port
        state.publish()
        return {"upstream_port": port}

    fx.on_reset_hook(state.reset)
    state.publish()
    return {
        "listeners": listeners,
        "fingerprints": fingerprints,
        "upstream": {"host": LOOPBACK, "port": upstream},
        "certs_dir": str(certs),
    }


def main() -> int:
    return run_fixture("FX-TLS", setup, parser=build_parser())


if __name__ == "__main__":
    socket.setdefaulttimeout(None)
    sys.exit(main())
