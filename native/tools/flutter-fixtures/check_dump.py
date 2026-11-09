#!/usr/bin/env python3
"""Check a seeded Flutter 2.1.4 fixture dump before it is committed (E00-S02).

    python3 -I check_dump.py <dump-dir> [--ledger <repo>/docs/native/migration/store-ledger.json]

<dump-dir> is native/e2e/fixtures/flutter-2.1.4/ after seed_2_1_4.sh. Checks, without printing any value:
  1. seed-manifest.json is filled in: release 2.1.5+63, a 40-hex commit, versionCode >= minVersionCode (above every
     owner build), the signer is the local release key, the e2e AVD (never the walk AVD) and its snapshot name, and a
     named reviewer;
  2. the dump files exist (prefs tar, prefs.json, tree listing, permissions, app-ops, shortcuts, channels);
  3. every name a seed step expects has a store-ledger row and is present where that row's kind lives;
  4. only fixture plaintexts: every listed plaintext starts with "fixture-", and nothing token-shaped is in the dump.
Exit status 0 when clean; otherwise one line per problem (names and file names only) and status 1.
"""
import json
import re
import sys
import tarfile
from pathlib import Path

LOCAL_RELEASE_KEY = '1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C'
REQUIRED_FILES = ['seed-manifest.json', 'shared_prefs.tar', 'prefs.json', 'tree.txt', 'package-before.txt',
                  'appops-before.txt', 'shortcuts-before.txt', 'channels-before.txt']
OS_FILES = ['package-before.txt', 'shortcuts-before.txt', 'channels-before.txt', 'appops-before.txt']
# Shapes of real credentials that must never be in a fixture dump.
TOKEN_SHAPES = [re.compile(p) for p in (
    r'ghp_[A-Za-z0-9]{20,}', r'github_pat_[A-Za-z0-9_]{20,}', r'\bsk-[A-Za-z0-9_-]{20,}', r'xox[abposr]-[A-Za-z0-9-]{10,}',
    r'\bAKIA[0-9A-Z]{16}\b', r'-----BEGIN [A-Z ]*PRIVATE KEY-----', r'\bey[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.',
)]
TEXT_DUMP_FILES = ['prefs.json', 'tree.txt'] + OS_FILES


def load_ledger(path: Path) -> dict:
    rows = json.loads(path.read_text(encoding='utf-8'))['rows']
    by = {}
    for r in rows:
        for n in [r['name']] + r.get('aliases', []):
            by.setdefault(n, []).append(r)
    return by


def check(dump: Path, ledger_path: Path) -> list:
    problems = []
    for f in REQUIRED_FILES:
        if not (dump / f).is_file():
            problems.append(f'missing file {f}')
    if (dump / 'seed-manifest.json').is_file() is False:
        return problems
    m = json.loads((dump / 'seed-manifest.json').read_text(encoding='utf-8'))
    b, avd, rev = m.get('build', {}), m.get('avd', {}), m.get('reviewed', {})

    if m.get('release') != '2.1.5+63':
        problems.append('release is not 2.1.5+63')
    if not re.fullmatch(r'[0-9a-f]{40}', str(b.get('commit') or '')):
        problems.append('build.commit is not a full commit id')
    vc, floor = b.get('versionCode'), b.get('minVersionCode', 4701)
    if not isinstance(vc, int) or vc < floor:
        problems.append(f'build.versionCode must be an integer >= {floor} (above every installed owner build)')
    if (b.get('signerSha256') or '').replace(':', '').upper() != LOCAL_RELEASE_KEY:
        problems.append('build.signerSha256 is not the local release key')
    if avd.get('role') != 'e2e' or not avd.get('name') or 'walk' in str(avd.get('name')).lower():
        problems.append('avd must be the e2e AVD (never the walk AVD), named')
    if avd.get('snapshot') != 'flutter-2.1.4-seeded':
        problems.append('avd.snapshot must be flutter-2.1.4-seeded')
    if not rev.get('by') or not rev.get('at'):
        problems.append('reviewed.by and reviewed.at must be filled in after reading the dump')

    for p in m.get('fixturePlaintexts', []):
        if not str(p).startswith('fixture-'):
            problems.append('a fixture plaintext does not start with "fixture-"')

    ledger = load_ledger(ledger_path)
    prefs = json.loads((dump / 'prefs.json').read_text(encoding='utf-8')) if (dump / 'prefs.json').is_file() else {}
    tree = (dump / 'tree.txt').read_text(encoding='utf-8', errors='replace') if (dump / 'tree.txt').is_file() else ''
    os_text = '\n'.join((dump / f).read_text(encoding='utf-8', errors='replace') for f in OS_FILES if (dump / f).is_file())
    tar_names, fss = set(), ''
    if (dump / 'shared_prefs.tar').is_file():
        with tarfile.open(dump / 'shared_prefs.tar') as t:
            for mem in t.getmembers():
                name = mem.name.lstrip('./')
                tar_names.add(name)
                if mem.isfile() and name == 'shared_prefs/FlutterSecureStorage.xml':
                    fss = t.extractfile(mem).read().decode('utf-8', 'replace')

    def present(name: str, kind: str) -> bool:
        if kind == 'pref':
            return any(k == 'flutter.' + name or (name.endswith('.') and k.startswith('flutter.' + name)) for k in prefs)
        if kind == 'secret':
            return name in fss
        if kind == 'prefs-file':
            return f'shared_prefs/{name}.xml' in tar_names
        if kind == 'file':
            return f'files/{name}' in tree
        return name in os_text  # widgets, tiles, shortcuts, components, channels, permissions

    for seed in m.get('seeds', []):
        for name in seed.get('expect', []):
            rows = ledger.get(name)
            if not rows:
                problems.append(f'seed {seed["id"]}: {name} has no store-ledger row')
            elif not any(present(name, r['kind']) for r in rows):
                problems.append(f'seed {seed["id"]}: {name} is not in the dump')

    for f in TEXT_DUMP_FILES:
        if (dump / f).is_file():
            text = (dump / f).read_text(encoding='utf-8', errors='replace')
            if any(rx.search(text) for rx in TOKEN_SHAPES):
                problems.append(f'{f} holds something shaped like a real credential; remove it and reseed')
    return problems


def main(argv):
    args = argv[1:]
    if not args:
        raise SystemExit(__doc__)
    dump = Path(args[0])
    ledger = Path(args[args.index('--ledger') + 1]) if '--ledger' in args else \
        Path(__file__).resolve().parents[3] / 'docs/native/migration/store-ledger.json'
    problems = check(dump, ledger)
    for p in problems:
        print(p)
    print('clean' if not problems else f'{len(problems)} problem(s)')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
