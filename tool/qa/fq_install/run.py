#!/usr/bin/env python3
"""One agent, one shared emulator lock, actual APK2196 install UI."""
import argparse
import fcntl
import json
import os
import subprocess
from pathlib import Path

import agent
import device
import probe

IDS = ('codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx')
REPO = Path(__file__).resolve().parents[3]
DART = Path.home() / '.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart'
LOCK = Path('/home/eslam/Storage/tmp/oc-emulator.lock')


def catalog():
    package_config = REPO / '.dart_tool/package_config.json'
    if not package_config.is_file():
        raise RuntimeError('Package config missing; prepare this worktree using the pinned Flutter first')
    result = subprocess.run(
        [str(DART), '--packages=' + str(package_config), str(Path(__file__).with_name('catalog.dart'))],
        cwd=REPO, capture_output=True, timeout=90,
    )
    if result.returncode:
        raise RuntimeError('Pinned catalog generation failed; output was withheld')
    value = json.loads(result.stdout)
    if set(value) != set(IDS):
        raise RuntimeError('Unexpected generated catalog IDs')
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('agent', choices=IDS)
    parser.add_argument('--slow-download', action='store_true', help='For fx only: temporarily limit the cancellation download to 1024 kbps')
    parser.add_argument('--wait', action='store_true', help='Queue behind another lane in the shared emulator lock')
    parser.add_argument('--output', type=Path, default=REPO / 'docs/qa/FQ-install-2026-10-08')
    args = parser.parse_args()
    if args.slow_download and args.agent != 'fx':
        parser.error('--slow-download is reserved for the tiny fx cancellation case')
    metadata = catalog()
    # fcntl is the same Linux flock API as the coordinator's shell command.
    # Default refuses contention; --wait explicitly queues without using the device.
    with LOCK.open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | (0 if args.wait else fcntl.LOCK_NB))
        except BlockingIOError:
            raise SystemExit('Emulator is in use; rerun after the other lane finishes')
        device.configure(args.output.resolve())
        try:
            if device.adb('get-state').strip() != 'device':
                raise RuntimeError('The dev emulator is unavailable')
            if device.adb('shell', 'getprop', 'ro.product.cpu.abi').strip() != 'x86_64':
                raise RuntimeError('This pinned catalog probe requires the x64 dev emulator')
            probe.require_idle_setup()
            agent.run(args.agent, metadata, slow_download=args.slow_download)
        except Exception as error:
            # Driver/bridge exception messages may contain private paths/output.
            raise SystemExit('Certification run stopped: ' + type(error).__name__)
        finally:
            device.end_session()
            fcntl.flock(lock, fcntl.LOCK_UN)


if __name__ == '__main__':
    main()
