#!/usr/bin/env python3
"""Compare a candidate APK with the exact APK asset of the previous release tag."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

MIB = 1024 * 1024


def measure(apk, release, baseline_tag, max_growth_mib):
    if not math.isfinite(max_growth_mib) or max_growth_mib < 0:
        raise ValueError('Growth budget must be a finite nonnegative MiB value')
    if not isinstance(release, dict):
        raise ValueError('Baseline release must be an object')
    if not isinstance(release.get('assets'), list) or any(not isinstance(a, dict) for a in release['assets']):
        raise ValueError('Baseline assets must be objects')
    if not baseline_tag.startswith('v') or release.get('tag_name') != baseline_tag:
        raise ValueError('Baseline release must match the previous tag exactly')
    if release.get('draft') is not False or release.get('prerelease') is not False:
        raise ValueError('Baseline must be a published stable release')
    name = f'opencode-mobile-{baseline_tag[1:]}.apk'
    assets = [a for a in release.get('assets', []) if a.get('name') == name]
    if len(assets) != 1 or type(assets[0].get('size')) is not int or assets[0]['size'] <= 0:
        raise ValueError('Baseline requires one nonempty exact-version APK asset')
    size = apk.stat().st_size
    if size <= 0:
        raise ValueError('Candidate APK must be nonempty')
    baseline_size = assets[0]['size']
    growth = size - baseline_size
    digest = hashlib.sha256()
    with apk.open('rb') as stream:
        for chunk in iter(lambda: stream.read(MIB), b''):
            digest.update(chunk)
    return {'schema': 1, 'apk_sha256': digest.hexdigest(), 'apk_bytes': size,
            'baseline_tag': baseline_tag, 'baseline_apk_bytes': baseline_size,
            'growth_bytes': growth, 'max_growth_mib': max_growth_mib,
            'passed': growth <= max_growth_mib * MIB}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--baseline-release-json', required=True, type=Path)
    parser.add_argument('--baseline-tag', required=True)
    parser.add_argument('--max-growth-mib', type=float, default=5)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    try:
        report = measure(args.apk, json.loads(args.baseline_release_json.read_text()), args.baseline_tag, args.max_growth_mib)
    except (OSError, ValueError, TypeError, KeyError):
        print('APK size unavailable: provide the exact published previous-release APK metadata and candidate APK.', file=sys.stderr)
        return 2
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f"APK bytes={report['apk_bytes']}; baseline bytes={report['baseline_apk_bytes']}; growth bytes={report['growth_bytes']}; budget MiB={report['max_growth_mib']}; passed={report['passed']}")
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
