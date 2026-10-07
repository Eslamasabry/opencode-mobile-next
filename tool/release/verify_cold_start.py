#!/usr/bin/env python3
"""Require measured launch evidence for the exact release APK before upload."""
import argparse
import re
import json
from pathlib import Path
import statistics
import sys
import zipfile

from apk_payload import payload_digest


def verify(apk, report):
    if not isinstance(report, dict) or report.get('schema') != 1 or report.get('apk_payload_sha256') != payload_digest(apk):
        raise ValueError('Exact executable APK payload receipt required')
    if not isinstance(report.get('apk_sha256'), str) or re.fullmatch('[0-9a-f]{64}', report['apk_sha256']) is None:
        raise ValueError('Measured test APK identity required')
    if report.get('serial') != 'emulator-5554' or report.get('metric') != 'android_activity_total_time_ms':
        raise ValueError('Expected dev-emulator Activity launch measurement')
    samples = report.get('samples_ms')
    if not isinstance(samples, list) or len(samples) != 3 or any(type(x) is not int or x <= 0 for x in samples):
        raise ValueError('Three positive measured timings required')
    median = statistics.median(samples)
    if report.get('median_ms') != median or median > 3000:
        raise ValueError('Measured median must be at most 3000ms')
    return median


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--receipt', required=True, type=Path)
    args = parser.parse_args()
    try:
        value = verify(args.apk, json.loads(args.receipt.read_text()))
    except (OSError, ValueError, TypeError, zipfile.BadZipFile):
        print('Release blocked: measure three cold Activity launches of this matching APK payload on emulator-5554 and record the receipt; median budget is 3000ms.', file=sys.stderr)
        return 1
    print(f'Cold Activity launch median={value}ms; budget=3000ms; matching APK payload receipt verified')
    return 0


if __name__ == '__main__': sys.exit(main())
