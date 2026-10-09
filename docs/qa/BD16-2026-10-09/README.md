# BD16 — final-pass runner for APK 2202

Implemented runner/driver compatibility; **the real fast attempt did not qualify
an end-to-end device pass**. It completed under one shared emulator reservation
and restored approved normal APK 2202. See the [generated row summary](fast-run/README.md).
No 30-minute background row or GIF row ran. No owner-phone access, build, push,
publication, app data clear or application uninstall was performed by this slice.

Finish line: compatible drivers, inert dry-run, then one locked fast sequence
with normal 2202 restored and honest receipts. Non-goals: background/GIF execution,
application/UI changes, builds and account operations.

## Changes

- Normal defaults and BB5/FQ3/FQ9/BD7 eligibility now target 2202. Historical
  FQ3 evidence validation remains intact; new reports use a separate directory.
- `--fast` removes background and GIF rows from dispatch; `--output` preserves
  distinct attempts. Upgrade now precedes candidate replacement so its existing
  baseline can actually be checked. A prior build is never installed to invent
  an upgrade baseline.
- BA uses a parameterized fresh-install/removal driver from the reviewed BA
  implementation (`1530daff8`), replacing the missing version-named entry point.
  It retains account masking, read-only inventory, native job/checksum evidence,
  product removal, retained-state comparisons and normal restoration checks.
- BB5 accepts a distinct reviewed QA-enabled 2202 artifact and signed runner,
  then restores normal 2202. Normal release APKs disable its native QA hooks;
  the adapter no longer confuses the normal candidate with the QA target.
- FQ3 validates the inherited lock descriptor and runs phases in the same Dart
  process, retaining the parent's reservation. `--no-matrix` leaves the global
  certification matrix untouched. FQ9, BD7 and recorder use current build pins.

No UI changes; no UI contract required. Driver usage and input schemas:
[final-pass-runner.md](../final-pass-runner.md).

## Verification

Failing-first checks: [current-build red](red.txt) and
[upgrade ordering / distinct QA target red](sequencing-red.txt).

All Python commands used `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test --`:

- [81 adapter/BB5/BD7 tests](adapter-tests.txt):
  `env PYTHONPATH=tool/qa python3 -m unittest test_bb5_runtime_acceptance test_bd7_device_saved_report test_final_pass_2202 test_final_pass test_final_pass_install test_final_pass_protocols test_final_pass_misc`.
- [69 BA tests](ba-tests.txt):
  `python3 -m unittest discover -s tool/qa/fq_install2 -p 'test_*.py'`.
- [164 FQ9 tests](fq9-tests.txt):
  `python3 -m unittest discover -t . -s tool/qa/fq9 -p 'test_*.py'`.
  An initial invocation omitted `-t .` and failed relative imports; the corrected
  command loaded all files and passed.
- [26 FQ3 validator tests](fq3-validator-tests.txt):
  `python3 -m unittest tool.qa.fq3.test_update_matrix`.
- [33 focused Flutter tests](flutter-tests.txt), pinned Flutter 3.47.1 under the
  same lock prefix, `test --concurrency 1`: final_pass_lock, fq3_evidence,
  fq3_history_manifest and fq3_session_ownership.

Python F/E9 lint, Dart formatting and `git diff --check` passed. Full
[analyzer](analyzer.txt) returned one existing `unnecessary_import` info at
`test/goldens/connector_card_golden_test.dart:17`; that file was byte-identical
to branch base `02670b857`. No new analyzer finding. No full test suite claimed.

## One real fast attempt

[Dry-run plan](dry-run.json) was generated before device execution:

```bash
python3 tool/qa/final_pass.py --dry-run --fast \
  --output docs/qa/BD16-2026-10-09/fast-run
python3 tool/qa/final_pass.py --execute --fast \
  --candidate-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk \
  --candidate-build 2202 --inputs /tmp/bd16-inputs/rows.json \
  --output docs/qa/BD16-2026-10-09/fast-run
```

The runner waited for BC's existing reservation, then acquired its own lock.
Its candidate and normal SHA256 were both
`e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`.
The supplied BA manifest used this 2202 artifact at source `02670b857` and the
existing reviewed QA90001 storage-floor artifact from `1530daff8`; no new build
was produced. FQ3 selected the public `opencode/big-pickle` model for both engines.

Observed results:

- FQ9 upgrade: blocked, no reviewed baseline/history inputs supplied.
- BA fresh install: failed before install, `target_not_absent`; the existing fx
  inventory had leftovers, a pin mismatch, no target PIDs, and no staging/lock.
  The completed native setup record was preserved. No manual payload deletion
  or account action was attempted.
- Later device rows: blocked by the BA driver's unconfirmed safe continuation.
  Consequently this attempt did not exercise the new FQ3 inherited-lock path,
  BB5, BA removal/storage, or BD7. BB5 also still needs a QA-enabled **2202**
  target and matching runner; the known older QA2198 artifact is insufficient.
- FB1 offline fresh-AVD plan: passed; this is not fresh-install qualification.
- [Normal restoration](fast-run/normal-restore.json): install succeeded and
  installed version 2202 verified before the reservation ended. Exit status 1
  correctly reflects failed/blocked rows. No retry was made under the one-run scope.

[Driver source hashes](driver-sources.json) identify this attempt's code.
Remaining prerequisites are a product-path cleanup/repair decision for the
existing fx payload, reviewed BB5 QA2202 artifacts, and an actual FQ9 baseline
with its history receipt. A subsequent full successful fast pass is still needed.

## BA follow-up

[Authorized app install/removal on 2202](../BA10-2202-2026-10-09/app-install-remove/README.md)
repaired then removed the 45,056-byte partial fx leftover: the app reported
13 MB freed and retained account/chat projections matched. Normal 2202 is
verified. The driver confirms fresh recovery before allowing later rows and
preserves that decision through multi-agent failures. The original final-pass
attempt above has not been rerun or reclassified. The missing-Remove affordance
for a partial payload has a separate three-line contract; clean reinstall via
the existing Install action is now device-proven.
