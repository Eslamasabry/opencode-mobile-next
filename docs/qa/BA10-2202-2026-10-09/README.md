# BA10 / BD16 follow-up on APK 2202

**Update: the authorized [app install and removal](app-install-remove/README.md) succeeded.**
The app freed 13 MB, fx is absent, retained state matches, and normal 2202 is
verified. The earlier read-only blocker below is preserved as historical evidence.

## Earlier read-only observation

**Device removal blocked: APK 2202 offers no Remove fx action for this leftover.**
Driver recovery confirmation is implemented and tested; this is not a passing
fresh-install or BA10 removal row. No certification matrix was promoted.

Finish line: remove the leftover through the app, measure freed bytes and
retention, and let later final-pass rows proceed only after confirmed recovery.
Non-goal: shell cleanup, account changes, rebuilding or replacing product UI.

## Locked device observation

Only `emulator-5554` was accessed, under
`flock -w 3600 /home/eslam/Storage/tmp/oc-emulator.lock`, held through final
normal-APK verification. APK 2202's file signer, SHA-256, installed hash and
version were verified before navigation and at the end. No APK replacement was
needed. No install, removal, sign-in, sign-out, or manual payload deletion ran.

- [Before inventory and retention](before.json): fx has **45,056 allocated
  bytes**, its launcher matches, its pin does not, and no target process,
  staging directory, or install lock is present.
- Settings > Agents shows **fx / Not installed**. Opening its own Install chip
  opens the setup sheet; that sheet contains **Install fx only**.
  [Closed UI observation](removal-unavailable.json), [masked screenshot](sheet.jpg).
  No confirmation or Remove action was available to press.
- [After observation](after.json): the same 45,056 bytes remain. **Freed bytes
  are unproven; no removal occurred.** The filesystem free-space delta is
  +4,096 bytes and must not be attributed to removal.
- Retention projections match: **27 Claude chat IDs**, phone-check metadata,
  two Claude account-home directories, all six target-home counts, and shared
  Node/Paseo presence. Claude's signed-in UI boolean was false both times;
  this proves retained state, not successful authentication. No credential
  contents or account identities were recorded.
- [Final normal verification](normal-restore.json): build **2202**, SHA-256
  `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`,
  approved local signer `1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c`.
  The emulator lock was then released.

Completing the installation and then removing it would be a different device
action; coordinator direction was requested. Until the leftover is removed,
`target_not_absent` remains valid and later device rows must remain blocked.

## Driver change and verification

BA install/removal/storage receipts now include a fresh `continuation` record:
verified normal APK, idle native/app setup, sufficient free storage, and every
managed target absent (including allocated bytes, launcher, staging, lock and
processes). The adapter checks every required boolean. Failed rows remain
failed; only confirmed recovery permits subsequent device rows. Multi-agent
failure aggregation preserves the decision. Rejected preflight does not mutate
or clean up unexpected files.

- Failing-first cases covered missing confirmation, retained payloads, failed
  normal verification/restoration, active setup, low storage, malformed facts,
  and propagation to later rows. [Production-revert proof](revert-proof.txt)
  fails against the original implementation with the new tests retained.
- [74 install-driver tests](fq-install2-tests.txt) and
  [46 final-pass tests](final-pass-tests.txt) pass through
  `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- ...`, one command at a time.
- `ruff check --select F,E9` on changed Python and `git diff --check` pass.
- No Flutter/native changes, build, full device batch, or new BA10 removal proof.
