#!/usr/bin/env bash
# Mocked publication contract: no GitHub access, signing keys or build processes.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
task_root="$(mktemp -d)"
cases_root="$(mktemp -d)"
trap 'rm -rf -- "$task_root" "$cases_root"' EXIT
mkdir -p "$task_root/dist" "$task_root/scripts" "$task_root/mock-bin" "$task_root/docs/releases" "$task_root/artifact"
cp "$repo_root/scripts/"{release.sh,release_github.sh,verify_github_release.py} "$task_root/scripts/"
# Preflight reads the candidate's own quality workflow for its shard count.
mkdir -p "$task_root/.github/workflows"
cp "$repo_root/.github/workflows/android-quality.yml" "$task_root/.github/workflows/"
printf 'version: 1.0.43+49\n' > "$task_root/pubspec.yaml"
printf '# OpenCode Mobile 1.0.43+49\n\nFixture release notes.\n' > "$task_root/docs/releases/v1.0.43+49.md"
printf 'fixture signed APK bytes\n' > "$task_root/artifact/opencode-mobile-1.0.43+49.apk"
printf 'fixture arm64 APK bytes\n' > "$task_root/artifact/opencode-mobile-1.0.43+49-arm64-v8a.apk"
printf 'fixture armv7 APK bytes\n' > "$task_root/artifact/opencode-mobile-1.0.43+49-armeabi-v7a.apk"
python3 - "$task_root" <<'PY'
import hashlib
from pathlib import Path
import sys
root = Path(sys.argv[1])
names = ['opencode-mobile-1.0.43+49.apk', 'opencode-mobile-1.0.43+49-arm64-v8a.apk', 'opencode-mobile-1.0.43+49-armeabi-v7a.apk']
(root/'artifact/SHA256SUMS').write_text(''.join(hashlib.sha256((root/'artifact'/name).read_bytes()).hexdigest()+'  '+name+'\n' for name in names))
(root/'artifact/RELEASE_NOTES.md').write_text((root/'docs/releases/v1.0.43+49.md').read_text()+'\n## Verify this build\n\n- Source commit: `'+('a'*40)+'`\n- Build: Shorebird release 1.0.43+49, Flutter 3.47.1 (Shorebird engine)\n')
PY
cat > "$task_root/mock-bin/git" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  status) printf '%s' "${MOCK_DIRTY:-}" ;;
  symbolic-ref) echo "${MOCK_BRANCH:-master}" ;;
  rev-list) echo "${MOCK_COUNTS:-0 0}" ;;
  remote) echo "${MOCK_REMOTE:-https://github.com/Eslamasabry/opencode-mobile-next.git}" ;;
  fetch) [[ "${MOCK_FETCH_FAIL:-false}" == false ]] ;;
  rev-parse)
    case "$2" in
      --is-inside-work-tree) echo true ;;
      --abbrev-ref) echo mobile-next/master ;;
      --verify)
        [[ "${MOCK_TAG_MISSING:-false}" == false ]] || exit 1
        echo "${MOCK_TAG_HEAD:-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}"
        ;;
      HEAD) echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ;;
      *) exit 99 ;;
    esac ;;
  *) exit 99 ;;
esac
MOCK
cat > "$task_root/mock-bin/apksigner" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
echo "Signer #1 certificate SHA-256 digest: ${MOCK_CERT:-842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C}"
MOCK
cat > "$task_root/mock-bin/aapt" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
echo "package: name='${MOCK_PACKAGE:-io.github.eslamasabry.opencode_mobile}' versionCode='${MOCK_CODE:-49}' versionName='1.0.43'"
MOCK
cat > "$task_root/mock-bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_ROOT/commands.log"
exec python3 "$MOCK_ROOT/gh-fixture.py" "$@"
MOCK
for tool in flutter shorebird; do
  printf '#!/usr/bin/env bash\necho "Unexpected build process" >&2\nexit 99\n' > "$task_root/mock-bin/$tool"
done
cat > "$task_root/gh-fixture.py" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
args = sys.argv[1:]
root = Path(os.environ['MOCK_ROOT'])
head = os.getenv('MOCK_CI_HEAD', 'a'*40)
def flag(name):
    return os.getenv(name) == 'true'
def without_provenance(text):
    return ''.join(line for line in text.splitlines(True) if 'Shorebird release' not in line)
notes = (root/'artifact/RELEASE_NOTES.md').read_text()
if flag('MOCK_NO_PROVENANCE'):
    notes = without_provenance(notes)
def release():
    assets = [dict(id=1, name='opencode-mobile-1.0.43+49.apk'), dict(id=2, name='SHA256SUMS'),
              dict(id=5, name='opencode-mobile-1.0.43+49-arm64-v8a.apk'), dict(id=6, name='opencode-mobile-1.0.43+49-armeabi-v7a.apk')]
    if flag('MOCK_UNEXPECTED_APK'):
        assets.append(dict(id=3, name='unverified.apk'))
    if flag('MOCK_DUPLICATE_ASSET'):
        assets.append(dict(id=4, name='opencode-mobile-1.0.43+49.apk'))
    # CI publishes a pre-release; MOCK_DRAFT is a hand-made draft and
    # MOCK_PUBLISHED one that was already promoted.
    return dict(id=int(os.getenv('MOCK_PAYLOAD_ID', '1')), tag_name=os.getenv('MOCK_RELEASE_TAG', 'v1.0.43+49'), assets=assets, draft=flag('MOCK_DRAFT'), prerelease=not flag('MOCK_PUBLISHED'), body='wrong notes' if flag('MOCK_NOTES') else notes)
def quality_jobs():
    """Jobs of one android-quality run; MOCK_JOBS selects a broken variant."""
    import re
    workflow = (root/'.github/workflows/android-quality.yml').read_text()
    shards = len(re.search(r'shard: \[([^\]]*)\]', workflow).group(1).split(','))
    variant = os.getenv('MOCK_JOBS', 'full')
    checks = ['Verify generated OpenCode SDK integrity', 'Test generated OpenCode SDK', 'Analyze generated OpenCode SDK', 'Analyze', 'Check the serial test runner', 'Run Android release lint']
    build = ['Check out source', 'Compile test-signed release APK', 'Verify release artifact exists', 'Upload test-signed APK']
    def job(name, steps, conclusion='success', step_conclusion='success'):
        return dict(name=name, conclusion=conclusion, steps=[dict(name=step, conclusion=step_conclusion) for step in steps])
    if variant == 'legacy':
        # The old single-job shape: every step in one successful job.
        steps = checks + ['Test'] + build
        jobs = [job('verify', steps)]
        return dict(total_count=len(jobs), jobs=jobs)
    if variant == 'apk-only':
        jobs = [job('checks', [], 'skipped'), job('test (shard ${{ matrix.shard }}/${{ strategy.job-total }})', [], 'skipped'), job('build', build), job('gate', ['Require every quality job'])]
        return dict(total_count=len(jobs), jobs=jobs)
    count = shards - 1 if variant == 'fewer-shards' else shards
    jobs = [job('checks', checks, step_conclusion='skipped' if variant == 'lint-skipped' else 'success')]
    for index in range(1, count + 1):
        if variant == 'shard-missing' and index == count:
            continue
        conclusion = 'failure' if variant == 'shard-failed' and index == 3 else 'success'
        step = 'skipped' if variant == 'shard-test-skipped' and index == 2 else 'success'
        jobs.append(job(f'test (shard {index}/{count})', ['Set up pinned Flutter', 'Test'], conclusion, step))
    if variant == 'duplicate-shard':
        jobs.append(job(f'test (shard 1/{count})', ['Test']))
    jobs.append(job('build', build[:-2] if variant == 'build-unverified' else build))
    jobs.append(job('gate', ['Require every quality job'], 'failure' if variant == 'gate-failed' else 'success'))
    if variant == 'gate-missing':
        jobs.pop()
    return dict(total_count=len(jobs) + (1 if variant == 'truncated' else 0), jobs=jobs)
def release_jobs():
    """Jobs of one android-release run; MOCK_BUILD_JOBS selects how the APK was built."""
    variant = os.getenv('MOCK_BUILD_JOBS', 'shorebird')
    secrets = 'Require the release signing and Shorebird secrets'
    upload = 'Build and upload Shorebird release APK'
    dry_run = 'Build Shorebird dry-run APK (nothing uploaded)'
    tail = ['Verify APK signer and version', 'Prepare stable release notes', 'Upload signed APK', 'Publish GitHub pre-release']
    steps = {
        'shorebird': [(secrets, 'success'), (upload, 'success'), (dry_run, 'skipped')] + [(name, 'success') for name in tail],
        # The pre-Shorebird workflow: an unpatchable plain `flutter build apk`.
        'flutter-build': [('Require the release signing secrets', 'success'), ('Compile signed release APK', 'success')] + [(name, 'success') for name in tail],
        # A manual run off a tag: the APK exists but Shorebird has no release.
        'dry-run': [(secrets, 'success'), (upload, 'skipped'), (dry_run, 'success')] + [(name, 'success') for name in tail],
        # A plain build step slipped in next to the Shorebird one.
        'mixed': [(secrets, 'success'), (upload, 'success'), ('Compile signed release APK', 'success')] + [(name, 'success') for name in tail],
        'upload-skipped': [(secrets, 'success'), (upload, 'skipped'), (dry_run, 'skipped')] + [(name, 'success') for name in tail],
    }
    if variant == 'missing':
        jobs = []
    else:
        chosen = steps['shorebird' if variant == 'duplicate' else variant]
        jobs = [dict(name='build', conclusion='success', steps=[dict(name=name, conclusion=conclusion) for name, conclusion in chosen])]
    if variant == 'duplicate':
        jobs = jobs * 2
    return dict(total_count=len(jobs), jobs=jobs)
if args[0] == 'api':
    path = args[1]
    assert path.startswith('repos/Eslamasabry/opencode-mobile-next/')
    if '/runs/102/jobs?' in path:
        print(json.dumps(release_jobs()))
    elif '/jobs?' in path:
        print(json.dumps(quality_jobs()))
    elif '/actions/runs/' in path:
        workflow = 'android-quality' if path.endswith('/101') else 'android-release'
        ref = os.getenv('MOCK_BUILD_REF', 'v1.0.43+49') if workflow == 'android-release' else 'master'
        print(json.dumps(dict(head_sha=head, head_branch=ref, path=f'.github/workflows/{workflow}.yml', status='completed', conclusion='failure' if flag('MOCK_CI_FAILED') else 'success', event='workflow_dispatch')))
    elif '/releases/tags/' in path:
        print('HTTP 404: draft releases are not visible through the tag endpoint', file=sys.stderr)
        raise SystemExit(1)
    elif path.endswith('/releases/1'):
        counter = root/'release-fetch-count'
        count = int(counter.read_text())+1 if counter.exists() else 1
        counter.write_text(str(count))
        data = release()
        if count > 1 and flag('MOCK_RECHECK_ID_MISMATCH'):
            data['id'] = 2
        print(json.dumps(data))
    elif path.endswith('/releases/latest'):
        latest = dict(id=7, tag_name=os.getenv('MOCK_LATEST_TAG', 'v1.0.42+47'))
        print(latest['tag_name'] if '--jq' in args else json.dumps(latest))
    else:
        raise RuntimeError(path)
elif args[:2] in (['run', 'download'], ['release', 'download']):
    assert args[args.index('--repo')+1] == 'Eslamasabry/opencode-mobile-next'
    target = Path(args[args.index('--dir')+1])
    for item in (root/'artifact').iterdir():
        shutil.copyfile(item, target/item.name)
    if flag('MOCK_NO_PROVENANCE') and (target/'RELEASE_NOTES.md').exists():
        (target/'RELEASE_NOTES.md').write_text(notes)
    if args[0] == 'run':
        assert args[args.index('--name')+1] == 'opencode-mobile-signed-'+('a'*40)
    elif flag('MOCK_DRAFT_DIFFERENT'):
        apk = target/'opencode-mobile-1.0.43+49.apk'
        apk.write_text('different bytes')
        sums = (target/'SHA256SUMS').read_text().splitlines()
        sums = [hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name if line.endswith('  '+apk.name) else line for line in sums]
        (target/'SHA256SUMS').write_text('\n'.join(sums)+'\n')
    elif flag('MOCK_BAD_CHECKSUM'):
        sums = (target/'SHA256SUMS').read_text().splitlines()
        sums = ['0'*64+'  opencode-mobile-1.0.43+49.apk' if line.endswith('  opencode-mobile-1.0.43+49.apk') else line for line in sums]
        (target/'SHA256SUMS').write_text('\n'.join(sums)+'\n')
elif args[:2] == ['release', 'view']:
    fields = args[args.index('--json')+1]
    if fields == 'isDraft,isPrerelease':
        # The workflow's check after publishing.
        print(json.dumps(dict(isDraft=False, isPrerelease=not flag('MOCK_STAGED_STABLE'))))
        raise SystemExit(0)
    if flag('MOCK_RELEASE_ABSENT'):
        raise SystemExit(1)
    if fields == 'databaseId':
        assert args[2] == 'v1.0.43+49'
        assert args[args.index('--repo')+1] == 'Eslamasabry/opencode-mobile-next'
        assert args[args.index('--jq')+1] == '.databaseId'
        print(os.getenv('MOCK_RELEASE_ID', '1'))
    else:
        print(json.dumps(dict(isDraft=not flag('MOCK_PUBLISHED'))))
elif args[:2] == ['release', 'edit']:
    # Nothing in this contract may make a release latest: only promote does.
    assert '--latest' not in args and '--prerelease=false' not in args
    if '--draft=false' in args:
        assert all(value in args for value in ['--prerelease', '--latest=false'])
        (root/'published').touch()
    else:
        assert all(value in args for value in ['--draft', '--prerelease'])
        (root/'draft-touched').touch()
elif args[:2] == ['release', 'create']:
    assert all(value in args for value in ['--verify-tag', '--prerelease', '--latest=false'])
    assert '--draft' not in args and '--prerelease=false' not in args
    (root/'draft-touched').touch()
elif args[:2] == ['release', 'upload']:
    (root/'draft-touched').touch()
else:
    raise RuntimeError(args)
PY
chmod +x "$task_root/mock-bin/"*

# release.sh prefers configured Android SDK tools; supply fixture tools there too.
mkdir -p "$task_root/sdk/build-tools/99.0.0"
cp "$task_root/mock-bin/"{apksigner,aapt} "$task_root/sdk/build-tools/99.0.0/"
base_root="$task_root"
case_number=0
# Every case runs in its own clone of the fixture (its own logs and state
# files), a few at a time; a failure leaves a marker and the end reports it.
max_parallel=6
spawn_case() {
  local body="$1" number="$2"
  shift 2
  while [[ "$(jobs -rp | wc -l)" -ge "$max_parallel" ]]; do wait -n || true; done
  (
    set +e
    task_root="$cases_root/$number"
    mkdir -p "$task_root"
    cp -a "$base_root/." "$task_root"
    ( set -e; "$body" "$number" "$@" ) || touch "$cases_root/failed"
  ) &
}
run_case() {
  case_number=$((case_number + 1))
  spawn_case run_case_body "$case_number" "$@"
}
run_draft_case() {
  case_number=$((case_number + 1))
  spawn_case run_draft_case_body "$case_number" "$@"
}
run_case_body() {
  local case_number="$1" expected="$2" mode="$3"
  shift 3
  rm -f "$task_root/published" "$task_root/release-fetch-count"
  : > "$task_root/commands.log"
  local result=0
  local publish_args=()
  [[ "$mode" == publish ]] && publish_args=(--publish)
  env PATH="$task_root/mock-bin:$PATH" MOCK_ROOT="$task_root" \
    ANDROID_HOME="$task_root/sdk" OC_RELEASE_BUILD_RUN_ID=102 OC_RELEASE_QUALITY_RUN_ID=101 \
    "$@" bash "$task_root/scripts/release.sh" github "${publish_args[@]}" \
    > "$task_root/result.log" 2>&1 || result=$?
  if [[ "$expected" == pass && "$result" != 0 ]] || [[ "$expected" == fail && "$result" == 0 ]]; then
    cat "$task_root/result.log"
    echo "FAIL: case $case_number expected $expected, exit $result" >&2
    exit 1
  fi
  # github only verifies; promotion is release.sh promote's job.
  [[ ! -f "$task_root/published" ]] || { echo 'Unexpected publication'; exit 1; }
  if grep -q '^release edit' "$task_root/commands.log"; then
    echo 'github changed the release'; exit 1
  fi
  if [[ "$mode" == publish ]]; then
    grep -q 'promote v1.0.43+49 --publish' "$task_root/result.log" || {
      cat "$task_root/result.log"; echo 'github --publish did not point at promote'; exit 1; }
  fi
  if grep -q '/releases/tags/' "$task_root/commands.log"; then
    echo 'Draft lookup used the published-only tag endpoint'; exit 1
  fi
}
# The success case runs against a mock whose by-tag release endpoint is 404.
run_case pass dry
# github never publishes any more: CI publishes a pre-release and
# `release.sh promote` makes it latest (tool/release/test_release_promote.py).
run_case fail publish
run_case fail dry MOCK_BRANCH=dev
run_case fail dry MOCK_DIRTY=' M pubspec.yaml'
run_case fail dry MOCK_COUNTS='1 0'
run_case fail dry MOCK_REMOTE=https://github.com/Eslamasabry/opencode-mobile.git
run_case fail dry MOCK_TAG_MISSING=true
run_case fail dry MOCK_TAG_HEAD=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
run_case fail dry MOCK_CI_HEAD=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
run_case fail dry MOCK_CI_FAILED=true
# Release evidence: the APK must come from an uploaded Shorebird release built
# on the candidate tag, never a plain flutter build or a dry-run.
for variant in flutter-build dry-run mixed upload-skipped missing duplicate; do
  run_case fail dry MOCK_BUILD_JOBS="$variant"
done
run_case fail dry MOCK_BUILD_REF=master
run_case fail dry MOCK_BUILD_REF=v1.0.42+47
run_case fail dry MOCK_NO_PROVENANCE=true
# Quality evidence: one run whose checks, build, gate and every test shard of
# the workflow passed. The old single-job shape and partial runs are refused.
for variant in apk-only legacy shard-missing shard-failed shard-test-skipped \
  fewer-shards duplicate-shard lint-skipped build-unverified gate-failed \
  gate-missing truncated; do
  run_case fail dry MOCK_JOBS="$variant"
done
run_case fail dry MOCK_PUBLISHED=true
run_case fail dry MOCK_DRAFT=true
run_case fail dry MOCK_NOTES=true
run_case fail dry MOCK_DRAFT_DIFFERENT=true
run_case fail dry MOCK_BAD_CHECKSUM=true
run_case fail dry MOCK_UNEXPECTED_APK=true
run_case fail dry MOCK_DUPLICATE_ASSET=true
run_case fail dry MOCK_CERT=2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC
run_case fail dry MOCK_PACKAGE=other.application
run_case fail dry MOCK_CODE=48
run_case fail dry OC_RELEASE_BUILD_RUN_ID=
run_case fail dry MOCK_RELEASE_ID=0
run_case fail dry MOCK_RELEASE_ID=-1
run_case fail dry MOCK_RELEASE_ID=null
run_case fail dry MOCK_RELEASE_ID=1.5
run_case fail dry MOCK_RELEASE_ID=
run_case fail dry MOCK_PAYLOAD_ID=2
run_case fail dry MOCK_RELEASE_TAG=v1.0.42+47
# Execute the actual workflow pre-release publishing shell with the same no-network gh.
python3 - "$repo_root" "$task_root" <<'PYWORKFLOW'
from pathlib import Path
import sys
source = (Path(sys.argv[1])/'.github/workflows/android-release.yml').read_text()
body = source.split('      - name: Publish GitHub pre-release\n', 1)[1].split('        run: |\n', 1)[1]
(Path(sys.argv[2])/'stage-draft.sh').write_text('\n'.join(line[10:] if line.startswith('          ') else line for line in body.splitlines())+'\n')
PYWORKFLOW
run_draft_case_body() {
  local case_number="$1" expected="$2"
  shift 2
  rm -f "$task_root/draft-touched" "$task_root/published"
  local result=0
  env PATH="$task_root/mock-bin:$PATH" MOCK_ROOT="$task_root" \
    GITHUB_REF_NAME=v1.0.43+49 GITHUB_REPOSITORY=Eslamasabry/opencode-mobile-next \
    RUNNER_TEMP="$task_root" "$@" \
    bash "$task_root/stage-draft.sh" > "$task_root/result.log" 2>&1 || result=$?
  if [[ "$expected" == pass ]]; then
    [[ "$result" == 0 && -f "$task_root/draft-touched" ]] || {
      cat "$task_root/result.log"; echo 'Pre-release publishing failed'; exit 1;
    }
  elif [[ "$expected" == unproven ]]; then
    # Published, but the outcome check must stop the run.
    [[ "$result" != 0 ]] || {
      cat "$task_root/result.log"; echo 'Unproven pre-release passed'; exit 1;
    }
  else
    [[ "$result" != 0 && ! -f "$task_root/draft-touched" ]] || {
      cat "$task_root/result.log"; echo 'Published release was modified'; exit 1;
    }
  fi
}
run_draft_case pass
run_draft_case pass MOCK_RELEASE_ABSENT=true
run_draft_case fail MOCK_PUBLISHED=true
run_draft_case unproven MOCK_RELEASE_ABSENT=true MOCK_STAGED_STABLE=true
run_draft_case unproven MOCK_RELEASE_ABSENT=true MOCK_LATEST_TAG=v1.0.43+49
wait
if [[ -e "$cases_root/failed" ]]; then
  echo 'FAIL: a publication contract case failed (output above)' >&2
  exit 1
fi
echo "PASS: $case_number GitHub publication contract cases"
