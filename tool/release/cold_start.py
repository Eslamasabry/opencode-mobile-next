#!/usr/bin/env python3
"""Measure Android launch TotalTime on emulator-5554 for an exact installed APK.

Requires the caller to hold oc-emulator.lock. TotalTime measures Activity launch,
not Flutter first-frame/server readiness or physical-phone performance.
"""
import argparse
import hashlib
import json
import re
import statistics
import subprocess
from pathlib import Path
import sys
import zipfile

from apk_payload import payload_digest

PACKAGE = 'io.github.eslamasabry.opencode_mobile'
SERIAL = 'emulator-5554'


def parse_launch(output):
    if re.search(r'^Status: ok$', output, re.M) is None:
        raise ValueError('launch failed')
    match = re.search(r'^TotalTime: ([0-9]+)$', output, re.M)
    if match is None:
        raise ValueError('launch timing missing')
    return int(match.group(1))


def installed_apk_path(paths):
    if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_/=+~.-]+/base.apk', paths[0]):
        raise ValueError('exact installed single APK required')
    return paths[0].removeprefix('package:')


def adb(*args):
    result = subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise ValueError('device command failed')
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--max-median-ms', type=int, default=3000)
    args = parser.parse_args()
    try:
        if args.max_median_ms <= 0:
            raise ValueError('invalid budget')
        digest = hashlib.sha256()
        with args.apk.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        paths = adb('shell', 'pm', 'path', PACKAGE).splitlines()
        path = installed_apk_path(paths)
        installed = adb('shell', 'sha256sum', path).split()[0]
        if installed != digest.hexdigest():
            raise ValueError('candidate does not match installed APK')
        samples = []
        for _ in range(3):
            adb('shell', 'am', 'force-stop', PACKAGE)
            samples.append(parse_launch(adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')))
        median = statistics.median(samples)
        report = {'schema':1,'serial':SERIAL,'apk_sha256':installed,'apk_payload_sha256':payload_digest(args.apk),'metric':'android_activity_total_time_ms',
                  'samples_ms':samples,'median_ms':median,'max_median_ms':args.max_median_ms,
                  'passed':median <= args.max_median_ms}
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(report,indent=2)+'\n')
        print(f'Android Activity launch median={median}ms; budget={args.max_median_ms}ms; passed={report["passed"]}')
        return 0 if report['passed'] else 1
    except (OSError, ValueError, IndexError, zipfile.BadZipFile, subprocess.TimeoutExpired):
        print('Cold-start measurement unavailable: install the exact candidate on emulator-5554 and retry under the emulator lock.',file=sys.stderr)
        return 2


if __name__ == '__main__': sys.exit(main())
