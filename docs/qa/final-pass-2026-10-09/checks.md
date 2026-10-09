# Focused verification

Before the device batch:

- Failing first: three candidate/default/skip regressions failed; two BA target
  preparation tests failed before implementation ([red](red.txt), [BA red](ba-red.txt)).
- Final-pass adapters, prior-runner, upgrade-input and saved-report tests:
  81 passed ([log](python.txt)).
- BA device-install tests: 12 passed ([log](ba-green.txt)).
- Candidate/live preparation checks: 5 passed ([log](live-tests.txt)).
- `python3 -m unittest discover -t . -s tool/qa/fq9`: 174 passed ([log](fq9-tests.txt)).
- Pinned Flutter, via `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test --`,
  `test --concurrency 1 test/final_pass_lock_test.dart test/fq3_evidence_test.dart
  test/fq3_history_manifest_test.dart test/fq3_session_ownership_test.dart`:
  33 passed. No full-suite claim; the coordinator's
  [candidate full suite](../full-suite-2026-10-09/README.md) is separate.
- Pinned Flutter `analyze` through the same machine lock: clean ([log](analyze.txt)).

After the batch:

- New receipt tests reproduced relative-path and 2202/2203 allowlist failures
  ([red](receipts-red.txt)). After fixing them, the receipt tests, runner tests
  and matrix tests passed: 46 tests ([green](receipts-green.txt)).
- Combined final-pass/matrix regression command: 66 tests passed
  ([log](final-python.txt)); no device rerun.
- Python compilation, changed-file diff checks and Markdown local links checked.
- No production UI/native changes, builds, emulator restart, full Flutter suite,
  external share, owner-phone operation or push.
