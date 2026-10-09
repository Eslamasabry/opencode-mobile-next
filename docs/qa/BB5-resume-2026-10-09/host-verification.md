# BB5 host acceptance adapter: normal APK 2202

Date: 2026-10-09. Scope: `tool/qa/bb5_runtime_acceptance.py` and
`tool/qa/test_bb5_runtime_acceptance.py`; no device commands, Flutter, Gradle,
builds, copies, or commits were performed by this host slice.

Finish line: accept the known normal APK 2202 for the locked BB5 session and
require its restoration while preserving artifact hash, signer, and exact
installed-artifact downgrade guards. Non-goal: change QA version 2198 or the
shared BB9 implementation.

## Observed verification

The following counts summarize tool output observed during this session. Raw
stdout was returned through tool calls and was not captured to persistent log
files; this receipt does not represent itself as a verbatim transcript.

After updating the test fixtures from normal 2199 to 2202 and adding stale-normal
refusal tests, the implementation was still unchanged. This command ran twice
(the second run refined the new tests to assert the specific admission refusal
and avoid an unrelated XML parsing error):

```sh
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 -m unittest discover -s tool/qa -p test_bb5_runtime_acceptance.py
```

- First failing-first run: 37 tests; 1 failure, 3 errors, exit 1.
- Refined failing-first run: 37 tests; 3 failures, 1 error, exit 1.
- Failure causes in the refined run: valid normal 2202 rejected; restoration
  rejected installed 2202; previous normal 2199 passed version admission; stale
  installed 2199 could reach successful restoration.

Implementation change: replace the normal-version constant, installed-version
check, documentation, and restoration evidence labels from 2199 to 2202. The
same command then reported **37 tests, OK, exit 0**. The inherited exact hash,
signer, and private downgrade guard implementation was not changed. Its tests
continue to require matching installed bytes and explicit authorization.

## Exact removed-fix proof

After the passing run, the following command re-executed the BB5 module source
with only `2202` replaced by `2199` in memory. The on-disk implementation remained
fixed throughout. It reproduced **37 tests; 3 failures, 1 error**, followed by
`EXPECTED_REMOVED_FIX_FAILURES 3 ERRORS 1`. The wrapper intentionally exits
successfully when the removed-fix suite fails; unittest itself reported failure.

```sh
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 - <<'PYPROOF'
import pathlib
import sys
import unittest
sys.path.insert(0, 'tool/qa')
import bb5_runtime_acceptance as module
source = pathlib.Path('tool/qa/bb5_runtime_acceptance.py').read_text()
exec(compile(source.replace('2202', '2199'), module.__file__, 'exec'), module.__dict__)
suite = unittest.defaultTestLoader.discover('tool/qa', pattern='test_bb5_runtime_acceptance.py')
result = unittest.TextTestRunner().run(suite)
if result.wasSuccessful():
    raise SystemExit('removed-fix proof unexpectedly passed')
print('EXPECTED_REMOVED_FIX_FAILURES', len(result.failures), 'ERRORS', len(result.errors))
PYPROOF
```

Diff validation command returned no output, exit 0:

```sh
git diff --check -- tool/qa/bb5_runtime_acceptance.py tool/qa/test_bb5_runtime_acceptance.py
```

## Existing normal artifact: read-only inspection

The following commands inspected the existing normal APK without copying it:

```sh
cat /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk.sha256
sha256sum /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk
/home/eslam/Android/Sdk/build-tools/36.1.0/apksigner verify --print-certs /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk
/home/eslam/Android/Sdk/build-tools/36.1.0/aapt dump badging /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk
```

Observed metadata:

- Package: `io.github.eslamasabry.opencode_mobile`.
- versionCode: `2202`; versionName: `1.2.0`.
- APK and sidecar SHA-256:
  `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`.
- Signer SHA-256:
  `1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c`.

This host receipt proves focused host behavior only. It does not claim the
native device scenario, successful APK restoration on the emulator, full-suite
verification, or release qualification.
