#!/usr/bin/env python3
"""Own one PC server and pass its startup secret to the real Dart client.

Invoke through machine_lock. No raw child output or credential is forwarded.
The inherited PC XDG/config context is preserved; no provider is enrolled.
"""

import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import socket
import shutil
import subprocess
import tempfile
import time
import threading

BINARY = Path(
    os.environ.get(
        "FQ3D_BINARY",
        "/home/eslam/Storage/Code/oc2-spike/new/node_modules/@opencode/cli-linux-x64/bin/opencode",
    )
)
FLUTTER = "/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter"
OUTPUT = Path("docs/qa/FQ3d-2026-10-08/pc-client-proof.json")


def run():
    server = None
    temporary = None
    drainers = []
    report = {"platform": "PC Linux; no emulator", "cases": {}, "stoppedAt": "startup"}
    status = 1
    try:
        try:
            with socket.create_connection(("127.0.0.1", 4097), timeout=1):
                report["stopReason"] = "port_4097_already_owned"
                return 1
        except OSError:
            pass
        version = subprocess.run(
            [str(BINARY), "--version"], capture_output=True, timeout=15
        )
        if version.returncode or version.stdout.strip() != b"opencode v2.0.10":
            report["stopReason"] = "binary_version_mismatch"
            return 1
        digest = hashlib.sha256(BINARY.read_bytes()).hexdigest()
        report["binarySha256"] = digest
        temporary = Path(tempfile.mkdtemp(prefix="oc-fq3d-pc-"))
        project = temporary / "project"
        project.mkdir()
        alias = temporary / "opencode2"
        alias.symlink_to(BINARY)
        env = os.environ.copy()
        if os.environ.get("FQ3D_ISOLATED_CONFIG") == "1":
            report["configurationContext"] = "isolated XDG; no copied credentials"
            for key, directory in (
                ("XDG_CONFIG_HOME", "config"),
                ("XDG_DATA_HOME", "data"),
                ("XDG_CACHE_HOME", "cache"),
                ("XDG_STATE_HOME", "state"),
            ):
                target = temporary / directory
                target.mkdir()
                env[key] = str(target)
        else:
            report["configurationContext"] = "inherited PC XDG/config"
        for key in ("OPENCODE_PASSWORD", "OPENCODE_SERVER_PASSWORD", "FQ3D_PASSWORD"):
            env.pop(key, None)
        server = subprocess.Popen(
            [str(alias), "serve", "--port", "4097", "--hostname", "127.0.0.1"],
            cwd=project,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        report["ownedServerPID"] = server.pid
        report["run"] = str(time.time_ns())
        OUTPUT.write_text(json.dumps(report, indent=2) + "\n")
        password = None
        listening = False
        with selectors.DefaultSelector() as selector:
            for pipe in (server.stdout, server.stderr):
                selector.register(pipe, selectors.EVENT_READ, bytearray())
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline and not (password and listening):
                if server.poll() is not None:
                    break
                for key, _ in selector.select(timeout=0.5):
                    chunk = os.read(key.fd, 4096)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    buffer = key.data
                    buffer.extend(chunk)
                    if len(buffer) > 65536:
                        report["stopReason"] = "startup_output_bound"
                        return 1
                    while b"\n" in buffer:
                        line, _, tail = buffer.partition(b"\n")
                        buffer[:] = tail
                        line = line.rstrip(b"\r")
                        match = re.fullmatch(
                            rb"server password ([A-Za-z0-9_-]{43})", line
                        )
                        if match:
                            if password is not None:
                                report["stopReason"] = "duplicate_startup_password"
                                return 1
                            password = match[1].decode("ascii")
                            report["startupPasswordParsed"] = True
                        if line in (
                            b"server listening on http://127.0.0.1:4097",
                            b"server listening on http://127.0.0.1:4097/",
                        ):
                            listening = True
                            report["startupListeningLineSeen"] = True
                        # Drop each raw line, including the secret, here.
        if not (password and listening):
            report["stopReason"] = (
                "pc_server_startup_timeout"
                if server.poll() is None
                else "pc_server_startup_exited"
            )
            return 1

        # Drain continuously without a log; verbose server output must never
        # block inference or turn into a transcript artifact.
        def drain(pipe):
            try:
                while pipe.read(4096):
                    pass
            except (OSError, ValueError):
                pass

        for pipe in (server.stdout, server.stderr):
            thread = threading.Thread(target=drain, args=(pipe,), daemon=True)
            thread.start()
            drainers.append(thread)
        env.update(
            FQ3D_LIVE="1",
            FQ3D_PASSWORD=password,
            FQ3D_DIRECTORY=str(project),
            FQ3D_PID=str(server.pid),
            FQ3D_BINARY_SHA256=digest,
        )
        result = subprocess.run(
            [
                FLUTTER,
                "test",
                "--no-pub",
                "--concurrency=1",
                "--reporter=expanded",
                "tool/qa/fq3d_live_test.dart",
            ],
            env=env,
            timeout=540,
        )
        status = result.returncode
        context = report["configurationContext"]
        report = json.loads(OUTPUT.read_text())
        report["configurationContext"] = context
        report["startupPasswordParsed"] = True
        report["startupListeningLineSeen"] = True
    except (OSError, subprocess.TimeoutExpired, ValueError):
        report["stopReason"] = "bounded_launcher_failure"
        status = 1
    finally:
        if server is not None:
            report["ownedServerPID"] = server.pid
            try:
                server.terminate()
                server.wait(timeout=8)
            except (OSError, subprocess.TimeoutExpired):
                try:
                    server.kill()
                    server.wait(timeout=5)
                except (OSError, subprocess.TimeoutExpired):
                    report["cleanupOK"] = False
            for pipe in (server.stdout, server.stderr):
                try:
                    pipe.close()
                except (OSError, ValueError):
                    report["cleanupOK"] = False
            report["ownedServerExited"] = server.poll() is not None
            report["serverExitCode"] = server.returncode
        for thread in drainers:
            thread.join(timeout=1)
        if temporary is not None and (
            server is None or report.get("ownedServerExited")
        ):
            try:
                shutil.rmtree(temporary)
            except OSError:
                report["cleanupOK"] = False
        if report.get("cleanupOK") is False or report.get("ownedServerExited") is False:
            status = 1
        OUTPUT.write_text(json.dumps(report, indent=2) + "\n")
    return status


if __name__ == "__main__":
    raise SystemExit(run())
