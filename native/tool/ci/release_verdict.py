#!/usr/bin/env python3
"""The Native CI verdict for one commit, for scripts/native-release.sh.

    python3 -I tool/ci/release_verdict.py <commit-sha> [<owner/repo>]      exit 0 = PASS, 1 = no passing run, 2 = could not ask

Looks at the completed Native CI runs for exactly that commit, newest first, and reads each run's `ci-verdict` artifact:
the small verdict.json the summary job writes next to the run summary (summary.py's exit code, never a job colour).
The newest run that counts decides. A run counts only when it was a complete candidate lane run that
compiled, ran the desktop tests AND the Android library unit tests, with an executed passing full security suite and no skipped guard test (a guard whose reference picture was missing); a tasks-only, apk-only, goldens or no-Android-tests
run (including daily fast/full runs) says nothing about a release and is skipped. A missing or mismatched verdict is unproved candidate coverage and refuses release. Older green runs never
rescue a newer red one. Uses `gh` as the person running it (OC_GH overrides the binary); no token is read or printed.
Prints one line naming the run it used.
"""
import json, os, subprocess, sys, tempfile

sha = sys.argv[1] if len(sys.argv) > 1 else ''
repo = sys.argv[2] if len(sys.argv) > 2 else ''
GH = os.environ.get('OC_GH', 'gh')
if len(sha) != 40:
    print('release verdict: pass the full 40-character commit', file=sys.stderr)
    sys.exit(2)


def gh(*args):
    cmd = [GH, *args] + (['-R', repo] if repo else [])
    return subprocess.run(cmd, capture_output=True, text=True, timeout=300)


p = gh('run', 'list', '--workflow', 'native-ci.yml', '--commit', sha, '--status', 'completed', '--limit', '30',
       '--json', 'databaseId,createdAt')
if p.returncode != 0:
    print(f'release verdict: could not list the CI runs ({p.stderr.strip()[:200]})', file=sys.stderr)
    sys.exit(2)
runs = sorted(json.loads(p.stdout or '[]'), key=lambda r: r['createdAt'], reverse=True)
skipped = []
for r in runs:
    rid = str(r['databaseId'])
    with tempfile.TemporaryDirectory() as tmp:
        d = gh('run', 'download', rid, '-n', 'ci-verdict', '-D', tmp)
        path = os.path.join(tmp, 'verdict.json')
        if d.returncode != 0 or not os.path.exists(path):
            print(f'release verdict: FAIL, newest run {rid} has no readable verdict; candidate coverage is unknown '
                  '(older green runs do not count)')
            sys.exit(1)
        v = json.load(open(path))
    lane = str(v.get('lane', ''))
    if v.get('sha') != sha:
        print(f'release verdict: FAIL, run {rid} has a candidate commit mismatch (older green runs do not count)')
        sys.exit(1)
    elif lane not in ('candidate', 'fast', 'full', 'full (push)', 'goldens', 'apk only', 'custom tasks'):
        print(f'release verdict: FAIL, run {rid} has unknown candidate coverage (older green runs do not count)')
        sys.exit(1)
    elif lane != 'candidate' or v.get('custom_tasks'):
        skipped.append(f'{rid}: {lane} run')
    elif not (v.get('compile') and v.get('desktop_tests') and v.get('android_unit') and v.get('candidate_complete') is True):
        print(f'release verdict: FAIL, the newest counting run {rid} (candidate lane) has incomplete candidate evidence '
              '(older green runs do not count)')
        sys.exit(1)
    elif v.get('security') is not True:
        print(f'release verdict: FAIL, the newest counting run {rid} ({lane} lane) has no executed passing full security suite '
              '(older green runs do not count)')
        sys.exit(1)
    elif v.get('guards_skipped') != 0:
        count = v.get('guards_skipped')
        print(f'release verdict: FAIL, the newest counting run {rid} ({lane} lane) left '
              f'{"an unknown number of" if count is None else count} guard test(s) skipped because their references were not fetched; '
              'a candidate must run every guard (older green runs do not count)')
        sys.exit(1)
    elif v.get('exit') == 0:
        print(f'release verdict: PASS, run {rid} ({lane} lane, with the Android unit tests)')
        sys.exit(0)
    else:
        print(f'release verdict: FAIL, the newest counting run {rid} ({lane} lane) has verdict exit {v.get("exit")} '
              f'(open its summary; older green runs do not count)')
        sys.exit(1)
extra = f' (looked at {len(runs)} run(s): {"; ".join(skipped[:5])})' if runs else ' (no completed Native CI run for this commit)'
print(f'release verdict: none, no complete candidate lane run for {sha[:10]}{extra}')
sys.exit(1)
