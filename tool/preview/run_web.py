#!/usr/bin/env python3
"""Run the real Flutter web app and hot reload after saved Dart changes."""

from __future__ import annotations

import argparse
from pathlib import Path
import signal
import subprocess
import tempfile
import time


ROOT = Path(__file__).resolve().parents[2]
PINNED_FLUTTER = (
    Path.home()
    / ".shorebird/bin/cache/flutter/e16cf749ccaa38d7050335ff305def49b1c7c84c/bin/flutter"
)


def dart_snapshot() -> dict[str, tuple[int, int]]:
    snapshot = {}
    for directory in (ROOT / "lib", ROOT / "packages/opencode_sdk/lib"):
        for path in directory.rglob("*.dart"):
            try:
                stat = path.stat()
            except FileNotFoundError:
                continue
            snapshot[str(path)] = (stat.st_mtime_ns, stat.st_size)
    return snapshot


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flutter", type=Path, default=PINNED_FLUTTER)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8789)
    args = parser.parse_args()
    flutter = args.flutter.expanduser().resolve()
    if not flutter.is_file():
        parser.error(f"Pinned Flutter not found: {flutter}")
    if not 1 <= args.port <= 65535:
        parser.error("Port must be between 1 and 65535")

    with tempfile.TemporaryDirectory(prefix="oc-flutter-web-") as temporary:
        pid_file = Path(temporary) / "flutter.pid"
        command = [
            str(flutter), "run", "-d", "web-server", "--hot",
            "--no-web-resources-cdn", "--web-hostname", args.host,
            "--web-port", str(args.port), "--pid-file", str(pid_file),
        ]
        process = subprocess.Popen(command, cwd=ROOT)
        stopping = False

        def stop(_signum: int, _frame: object) -> None:
            nonlocal stopping
            stopping = True
            if process.poll() is None:
                process.send_signal(signal.SIGINT)

        signal.signal(signal.SIGINT, stop)
        signal.signal(signal.SIGTERM, stop)
        previous = dart_snapshot()
        changed_at: float | None = None
        last_reload = 0.0
        try:
            while process.poll() is None and not stopping:
                current = dart_snapshot()
                now = time.monotonic()
                if current != previous:
                    previous = current
                    changed_at = now
                if (
                    changed_at is not None
                    and now - changed_at >= 0.8
                    and now - last_reload >= 2
                    and pid_file.is_file()
                ):
                    # Signal our live child, never a PID from a stale file.
                    process.send_signal(signal.SIGUSR1)
                    print("Saved Dart changes: hot reload requested.", flush=True)
                    last_reload = now
                    changed_at = None
                time.sleep(0.4)
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGINT)
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
        return process.returncode or 0


if __name__ == "__main__":
    raise SystemExit(main())
