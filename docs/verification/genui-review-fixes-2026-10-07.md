# Agent cards backend review fixes — 2026-10-07

Base: `e6ded6ac`, branch `feat/genui-be`. No commits made by this worker.
All changes are confined to backend Dart, tests, and this evidence record; frozen
public types, UI, native code, assets and dependencies are unchanged.

Six-fix checkpoint SHA-256 (sorted changed Dart paths + NUL + contents + NUL):
`1288cdf1c914bab6ba4af87f6e53f9a315388ab4f5ab61109ed7205c1befa3e4`.

## Failing-then-passing evidence

For findings 1–6, tests were added and executed before their corresponding
production fixes. For finding 7, the unchanged existing ratchet reproduced the
failure before the fix.
All commands used the pinned Shorebird Flutter, `--no-pub --concurrency=1`, and
`OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test --` to serialize heavy work.

| Review | Observed failure before fix | Resulting behavior and passing coverage |
|---|---|---|
| 1 — qualification and setup status | Installer called Claude + OC1 + OC2 instead of Claude only. Controller also requested OC1. Missing/failed verifier returned `RestartRequired` instead of unavailable/failure. | Enable requests and installs only qualified Claude; legacy registrations remain removable on disable. Verified Claude returns `On([claude])`. Failed/missing verification never promises a restart will fix it. Installer `review 1` cases and controller `review 1 controller requests only qualified agents` pass. |
| 2 — sparse OC1 status | `OC1 sparse status admits an existing idle session`: expected true, actual false. | A bounded session lookup verifies existence and directory, then an absent entry in the fresh status map means idle. Busy/retry, malformed responses, missing/foreign session, and changed scope remain blocked. Full history file passes. |
| 3 — streaming notifications | Unknown-session stale calls notified twice (expected zero); repeated stale notified twice (expected once). Controller pure deltas still notified once after the state-level fix (expected zero). | Pure delta/progress events exit the card handler before synchronization/invalidation. `stale()` does nothing for unknown sessions, still invalidates active reads, and notifies only a known complete-to-incomplete view transition. State regressions and controller `review 3` pass. |
| 4 — never-sent dispatch marker | Closing the profile between journal reservation and the final send fence left `deliveryUnknown` after reopen (expected idle). | Narrow serialized `discardUnsent` can remove the exact identity/revision/endpoint/token reservation even while closed. It does not recreate an absent journal, remove a replacement token, or clear unrelated/uncertain delivery. State regressions pass. |
| 5 — repeated-enable outage | Unchanged enable returned verification exit 24 because its marker had been removed during the helper check. | Unchanged owned helper/config/manifest/marker returns without rewriting helper or marker; independent readiness verification still runs. `review 5 unchanged enable keeps marker throughout verification` checks live marker availability and unchanged modification times. |
| 6 — repair and deletion | Owned helper/marker repair, modified-helper disable, and exact orphan recovery returned conflict 21. Controller deletion threw for both a failed cleanup status and a thrown cleanup error. | Write ownership manifest before replacing helper; repair owned bytes/marker mismatches; recover only the exact app-authored orphan with no existing registration/marker. Unknown files/collisions and symlinks stay protected. Modified helper bytes do not prevent disable. Profile deletion attempts registration cleanup but continues credential/local-data removal after cleanup failure. Installer `review 6` and both controller `review 6` cases pass. |
| 7 — bidi source ratchet | Unchanged G7 failed with three bidi-literal hits in the Dart validator and six in the JavaScript validator. | Build the directional control characters from numeric code points in both validators. Keep stripping all 12 characters and rejecting them in links; the shared Dart/JavaScript corpus now covers each. The unchanged ratchet passes. |

Red commands (each exited 1 with the failures above):

```text
flutter test --no-pub --concurrency=1 test/gen_ui_install_test.dart --name 'review [156]'
flutter test --no-pub --concurrency=1 test/gen_ui_install_test.dart --name 'review 1 (failed|missing) verifier'
flutter test --no-pub --concurrency=1 test/gen_ui_history_test.dart --plain-name 'OC1 sparse status admits an existing idle session'
flutter test --no-pub --concurrency=1 test/gen_ui_state_test.dart
flutter test --no-pub --concurrency=1 test/gen_ui_controller_test.dart --name '^review '
flutter test --no-pub --concurrency=1 test/kit_ratchet_test.dart
```

## OC1 contract evidence

`contracts/README.md:3-5` pins `f12e14cf1640cbf0dfb6b1ff425b2daaef459eec`.
`contracts/opencode-openapi-f12e14cf.json:5537-5546` defines the session-status map;
`:5578-5618` describes session lookup. The schema alone does not promise idle
entries. The exact pinned [status implementation](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/session/status.ts#L28-L41)
defaults missing entries to idle and removes idle entries from the stored map.
Its [HTTP handler](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/server/handlers/session.ts#L73-L82)
returns that map and exposes the separate session lookup.

## Six-fix checkpoint checks

Each focused file ran separately with:
`flutter test --no-pub --concurrency=1 <file>` under the serial machine lock.

| File | Passed |
|---|---:|
| `test/gen_ui_install_test.dart` | 23 |
| `test/gen_ui_history_test.dart` | 17 |
| `test/gen_ui_state_test.dart` | 22 |
| `test/gen_ui_controller_test.dart` | 14 |
| `test/profile_deletion_test.dart` | 17 |

Total: **93 passed**, no failures or skips.

Full `flutter analyze --no-pub`: no issues. `git diff --check`: clean.
All changed Dart files remain below 1,500 lines. Formatting used pinned Dart
with `--language-version=3.10`.

No full repository test suite or new emulator runtime qualification was run for
these fixes. OpenCode remains unqualified and is not enabled by the OC1 reader
repair. Registration cleanup is best effort during profile deletion: unrelated
or unowned configuration is preserved instead of blocking data removal.

## Finding 7 final checkpoint

Final candidate SHA-256 (same algorithm as above):
`72f2cc7348d723523632c6911b9744b4ee1f91427157423479792d9b84f95b67`.

After the numeric-code-point sanitizer change, the following checks ran serially
with the pinned SDK and machine lock:

```text
flutter test --no-pub --concurrency=1 test/kit_ratchet_test.dart test/gen_ui_codec_test.dart test/gen_ui_server_test.dart
flutter test --no-pub --concurrency=1 test/gen_ui_install_test.dart
flutter analyze --no-pub
```

Results: **57 passed** (ratchet 38, codec 11, helper server 8), followed by
**23 installer tests passed**. The codec test compares Dart and JavaScript across
82 corpus cases, including stripping all 12 directional controls and rejecting
each in URLs. Full analyzer: no issues. Formatting and `git diff --check`: clean.
The ratchet itself is unchanged. The earlier 93-test checkpoint is recorded
separately above; its other files were not rerun after this sanitizer-only change.
