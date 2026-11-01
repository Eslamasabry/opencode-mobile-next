#!/usr/bin/env python3
"""FX-LINUXDL: a static mirror of the built-in Linux downloads (E2E-17, E2E-19, E2E-39 Part A).

Layout. A real download URL ``https://<host>/<path>`` is served at ``<base>/<host>/<path>``, i.e. the
file lives at ``<mirror>/<host>/<path>``. The app's ``QaHooks.downloadBaseUrl`` is ``<base>``
(``http://10.0.2.2:8700`` from the emulator); the SHA-256 pins are still checked by the app.
Example: ``<mirror>/cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz``.

Filling the mirror (the runner's job; nothing here downloads):
  for each entry of ``PINS`` below (also printed in ready.json "pins"): fetch ``url`` into
  ``<mirror>/<host>/<path>`` and check its sha256, then write ``<mirror>/manifest.json``:
      {"files": [{"path": "<host>/<path>", "url": "https://<host>/<path>", "sha256": "<hex>"}, ...]}
  Extra entries (other tools, voice packs) may be listed the same way. On start every manifest entry is
  hashed; ready.json "manifest" reports {"ok", "missing", "mismatch"} and "pins" reports, per real pin,
  whether the mirror holds the exact pinned bytes ("mirrored").

Options (each also read from the environment, since fxctl passes only ``--port``):
  --mirror DIR       FX_LINUXDL_MIRROR    default <run-dir>/FX-LINUXDL/mirror
  --manifest FILE    FX_LINUXDL_MANIFEST  default <mirror>/manifest.json
  --synth            FX_LINUXDL_SYNTH=1   write small synthetic files at the real relative paths
                                          (never over an existing file) plus a manifest of their
                                          SHA-256s, for self-tests; ready.json "synthetic": true

HTTP: GET/HEAD with Content-Length, ETag ("<size hex>-<mtime_ns hex>"), Last-Modified, Accept-Ranges,
single ``Range: bytes=a-b | a- | -n`` (206 + Content-Range, 416 when unsatisfiable) and If-Range.

Control (POST on ctl, JSON body), besides the standard /health /state /log /reset /fault ...:
  throttle {"bytes_per_sec": N}                        0 = off; applies to every body write
  cut      {"path": prefix, "after_bytes": N, "count"?: n}  send N body bytes then close the socket
  corrupt  {"path": prefix, "on": bool}                flip one byte (offset size//2) of matching files
  offline  {"on": bool}                                every connection is closed at once (RST)
  /reset turns all of these off. Current settings: GET /state.
Request log lines add ``range``, ``bytes_sent``, ``cut``, ``corrupt``, ``throttled``.
"""

from __future__ import annotations

import argparse
import email.utils
import hashlib
import json
import os
import re
import socket
import sys
import threading
import time
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fxlib.core import LOOPBACK, Fixture, FxHandler, base_parser, run_fixture  # noqa: E402

EMULATOR_HOST = "10.0.2.2"

# The app's real pinned downloads. Keep in step with the sources named in each "source".
PINS: list[dict[str, str]] = [
    {"name": "ubuntu-base arm64",
     "url": "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
     "sha256": "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2",
     "source": "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:4490-4495"},
    {"name": "ubuntu-base amd64",
     "url": "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz",
     "sha256": "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86",
     "source": "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:4496-4499"},
    {"name": "ubuntu-base armhf (Termux path)",
     "url": "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-armhf.tar.gz",
     "sha256": "4fcee4d278f1c5232e085a021a85e4c6cef3853557a88d98ff380b5e5d5841bb",
     "source": "lib/termux/scripts/setup_base_script.dart:11,23"},
    {"name": "opencode 2.2.5 arm64",
     "url": "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.5.tgz",
     "sha256": "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053",
     "source": "lib/builtin/setup/components.dart:249-255"},
    {"name": "opencode 2.2.5 x64",
     "url": "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.5.tgz",
     "sha256": "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08",
     "source": "lib/builtin/setup/components.dart:256-262"},
    {"name": "node v24.21.0 arm64",
     "url": "https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-arm64.tar.gz",
     "sha256": "724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5",
     "source": "lib/termux/scripts/local_agents_script.dart:22-26; URL lib/builtin/setup/components.dart:374-381"},
    {"name": "node v24.21.0 x64",
     "url": "https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.gz",
     "sha256": "6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff",
     "source": "lib/termux/scripts/local_agents_script.dart:22-28; URL lib/builtin/setup/components.dart:374-381"},
    {"name": "claude code 2.1.316 arm64",
     "url": "https://downloads.claude.ai/claude-code-releases/2.1.316/linux-arm64/claude",
     "sha256": "55cdb8e162d190432696ea54010e0fca2e18d06fa7a7127fd563b9ffd4f22624",
     "source": "lib/builtin/setup/claude_scripts.dart:5-7,38"},
    {"name": "claude code 2.1.316 x64",
     "url": "https://downloads.claude.ai/claude-code-releases/2.1.316/linux-x64/claude",
     "sha256": "fd13c70000fec3f2116dfbcbb6121210b4a29328bf0e46210a59dd594a67d824",
     "source": "lib/builtin/setup/claude_scripts.dart:5,8-9,38"},
]


def mirror_path(url: str) -> str:
    """https://host/a/b -> host/a/b (the relative path inside the mirror)."""
    split = urlsplit(url)
    return f"{split.hostname}{split.path}"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def synth_bytes(seed: str, size: int) -> bytes:
    out = bytearray()
    counter = 0
    while len(out) < size:
        out += hashlib.sha256(f"{seed}:{counter}".encode()).digest()
        counter += 1
    return bytes(out[:size])


def write_synth(mirror: Path, manifest: Path) -> None:
    files = []
    for index, pin in enumerate(PINS):
        rel = mirror_path(pin["url"])
        target = mirror / rel
        size = 256 * 1024 if "ubuntu-base" in rel else 32 * 1024 + index * 1024
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(synth_bytes(rel, size))
        files.append({"path": rel, "url": pin["url"], "sha256": sha256_file(target), "size": target.stat().st_size})
    manifest.write_text(json.dumps({"synthetic": True, "files": files}, indent=1), encoding="utf-8")


class DlState:
    def __init__(self, fx: Fixture, mirror: Path) -> None:
        self.fx = fx
        self.mirror = mirror.resolve()
        self.lock = threading.Lock()
        self.reset()

    def reset(self) -> None:
        with self.lock:
            self.bytes_per_sec = 0
            self.cuts: list[dict[str, Any]] = []
            self.corrupt: set[str] = set()
            self.offline = False
        self.publish()

    def publish(self) -> None:
        with self.lock:
            self.fx.set_state(throttle_bytes_per_sec=self.bytes_per_sec, cuts=[dict(c) for c in self.cuts],
                              corrupt=sorted(self.corrupt), offline=self.offline)

    def take_cut(self, path: str) -> int | None:
        with self.lock:
            for rule in self.cuts:
                if path.startswith(rule["path"]) and rule.get("count", 1) != 0:
                    if "count" in rule:
                        rule["count"] -= 1
                    return int(rule["after_bytes"])
        return None

    def corrupted(self, path: str) -> bool:
        with self.lock:
            return any(path.startswith(prefix) for prefix in self.corrupt)


RANGE_RE = re.compile(r"^bytes=(\d*)-(\d*)$")


class MirrorHandler(FxHandler):
    def parse_request(self) -> bool:
        # Local workaround: core caches _body/_extra on the handler, which lives for the whole
        # keep-alive connection; clear them per request.
        self.__dict__.pop("_body", None)
        self.__dict__.pop("_extra", None)
        return super().parse_request()

    @property
    def state(self) -> DlState:
        return self.fixture.dl  # type: ignore[attr-defined]

    def handle(self) -> None:
        if self.state.offline:
            self.fixture.log.write(event="offline", transport="http", status=0)
            self.hard_close()
            return
        super().handle()

    def route(self, method: str, path: str, query: dict[str, str]) -> None:
        state = self.state
        if state.offline:
            self.hard_close()
            return
        if method not in ("GET", "HEAD"):
            self.send_json({"error": "method not allowed"}, 405)
            return
        rel = unquote(path).lstrip("/")
        target = (state.mirror / rel).resolve()
        try:
            target.relative_to(state.mirror)
        except ValueError:
            self.send_json({"error": "not found"}, 404)
            return
        if not rel or not target.is_file():
            self.send_json({"error": "not found"}, 404)
            return
        stat = target.stat()
        size = stat.st_size
        etag = f'"{size:x}-{stat.st_mtime_ns:x}"'
        start, end, status = 0, size - 1, 200
        range_header = self.headers.get("Range")
        if_range = self.headers.get("If-Range")
        if range_header and (not if_range or if_range == etag):
            self.note(range=range_header)
            match = RANGE_RE.match(range_header.strip())
            if match and (match.group(1) or match.group(2)):
                first, last = match.group(1), match.group(2)
                if first:
                    start = int(first)
                    end = min(int(last), size - 1) if last else size - 1
                else:
                    start = max(0, size - int(last))
                if start >= size or start > end:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    self._status = 416
                    return
                status = 206
        length = end - start + 1 if size else 0
        self.send_response(status)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("ETag", etag)
        self.send_header("Last-Modified", email.utils.formatdate(stat.st_mtime, usegmt=True))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        self._status = status
        if method == "HEAD" or not length:
            return
        self._send_body(target, path, start, length, size)

    def _close_mid_body(self) -> None:
        """End the connection after the bytes already written (FIN, no RST: they still arrive)."""
        self.close_connection = True
        try:
            self.wfile.flush()
            self.connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass

    def _send_body(self, target: Path, path: str, start: int, length: int, size: int) -> None:
        state = self.state
        cut_after = state.take_cut(path)
        corrupt_at = size // 2 if state.corrupted(path) else None
        if corrupt_at is not None:
            self.note(corrupt=True)
        deadline = time.monotonic() + self.cut_after_ms / 1000 if self.cut_after_ms is not None else None
        sent = 0
        began = time.monotonic()
        with target.open("rb") as handle:
            handle.seek(start)
            while sent < length:
                if state.offline:
                    self.note(bytes_sent=sent, cut="offline")
                    self.hard_close()
                    return
                bps = state.bytes_per_sec
                block = min(65536, length - sent)
                if bps:
                    block = max(1, min(block, bps // 20))
                    self.note(throttled=bps)
                if cut_after is not None:
                    block = min(block, cut_after - sent)
                    if block <= 0:
                        self.wfile.flush()
                        self.note(bytes_sent=sent, cut=True)
                        self._close_mid_body()
                        return
                if deadline is not None and time.monotonic() >= deadline:
                    self.note(bytes_sent=sent, cut=True)
                    self._close_mid_body()
                    return
                data = bytearray(handle.read(block))
                if not data:
                    break
                offset = start + sent
                if corrupt_at is not None and offset <= corrupt_at < offset + len(data):
                    data[corrupt_at - offset] ^= 0xFF
                self.wfile.write(data)
                sent += len(data)
                if bps:
                    self.wfile.flush()
                    ahead = sent / bps - (time.monotonic() - began)
                    if ahead > 0:
                        time.sleep(ahead)
        self.wfile.flush()
        self.note(bytes_sent=sent)


def check_manifest(manifest: Path, mirror: Path, hashes: dict[str, str]) -> dict[str, Any]:
    report: dict[str, Any] = {"file": str(manifest), "present": manifest.exists(), "ok": [], "missing": [],
                              "mismatch": []}
    if not manifest.exists():
        return report
    try:
        entries = json.loads(manifest.read_text(encoding="utf-8")).get("files", [])
    except (json.JSONDecodeError, AttributeError) as err:
        report["error"] = f"unreadable manifest: {err}"
        return report
    for entry in entries:
        rel = entry.get("path") or (mirror_path(entry["url"]) if entry.get("url") else None)
        if not rel:
            continue
        target = mirror / rel
        if not target.is_file():
            report["missing"].append(rel)
            continue
        actual = hashes.get(rel) or sha256_file(target)
        hashes[rel] = actual
        if actual != str(entry.get("sha256", "")).lower():
            report["mismatch"].append({"path": rel, "expected": entry.get("sha256"), "actual": actual})
        else:
            report["ok"].append(rel)
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = base_parser("FX-LINUXDL download mirror")
    parser.add_argument("--mirror", default=os.environ.get("FX_LINUXDL_MIRROR"))
    parser.add_argument("--manifest", default=os.environ.get("FX_LINUXDL_MANIFEST"))
    parser.add_argument("--synth", action="store_true",
                        default=os.environ.get("FX_LINUXDL_SYNTH", "") not in ("", "0", "false"))
    return parser


def setup(fx: Fixture) -> dict[str, Any]:
    args = fx.args
    mirror = Path(args.mirror).resolve() if args.mirror else fx.dir / "mirror"
    mirror.mkdir(parents=True, exist_ok=True)
    manifest = Path(args.manifest).resolve() if args.manifest else mirror / "manifest.json"
    if args.synth:
        write_synth(mirror, manifest)
    hashes: dict[str, str] = {}
    report = check_manifest(manifest, mirror, hashes)
    pins = []
    for pin in PINS:
        rel = mirror_path(pin["url"])
        target = mirror / rel
        if target.is_file() and rel not in hashes:
            hashes[rel] = sha256_file(target)
        pins.append({**pin, "path": rel, "present": target.is_file(), "mirrored": hashes.get(rel) == pin["sha256"]})

    state = DlState(fx, mirror)
    fx.dl = state  # type: ignore[attr-defined]
    fx.serve("http", MirrorHandler)

    def prefix(body: dict[str, Any]) -> str:
        path = body.get("path")
        if not isinstance(path, str) or not path.startswith("/"):
            raise ValueError("path must be an absolute path prefix, e.g. /cdimage.ubuntu.com/")
        return path

    @fx.control("throttle")
    def throttle(body: dict[str, Any]) -> dict[str, Any]:
        bps = body.get("bytes_per_sec")
        if not isinstance(bps, int) or bps < 0:
            raise ValueError("bytes_per_sec must be a non-negative integer (0 = off)")
        with state.lock:
            state.bytes_per_sec = bps
        state.publish()
        return {"bytes_per_sec": bps}

    @fx.control("cut")
    def cut(body: dict[str, Any]) -> dict[str, Any]:
        path = prefix(body)
        after = body.get("after_bytes")
        if not isinstance(after, int) or after < 0:
            raise ValueError("after_bytes must be a non-negative integer")
        rule: dict[str, Any] = {"path": path, "after_bytes": after}
        if "count" in body:
            if not isinstance(body["count"], int) or body["count"] < 1:
                raise ValueError("count must be a positive integer")
            rule["count"] = body["count"]
        with state.lock:
            state.cuts.append(rule)
        state.publish()
        return {"added": rule}

    @fx.control("corrupt")
    def corrupt(body: dict[str, Any]) -> dict[str, Any]:
        path = prefix(body)
        on = body.get("on")
        if not isinstance(on, bool):
            raise ValueError("on must be true or false")
        with state.lock:
            (state.corrupt.add if on else state.corrupt.discard)(path)
        state.publish()
        return {"path": path, "on": on}

    @fx.control("offline")
    def offline(body: dict[str, Any]) -> dict[str, Any]:
        on = body.get("on")
        if not isinstance(on, bool):
            raise ValueError("on must be true or false")
        with state.lock:
            state.offline = on
        state.publish()
        return {"offline": on}

    fx.on_reset_hook(state.reset)
    port = fx.ports["http"]
    return {
        "mirror": str(mirror),
        "synthetic": bool(args.synth),
        "base_url": {"local": f"http://{LOOPBACK}:{port}", "emulator": f"http://{EMULATOR_HOST}:{port}"},
        "layout": "<base_url>/<host>/<path of the real https URL>",
        "manifest": report,
        "pins": pins,
    }


def main() -> int:
    return run_fixture("FX-LINUXDL", setup, parser=build_parser())


if __name__ == "__main__":
    sys.exit(main())
