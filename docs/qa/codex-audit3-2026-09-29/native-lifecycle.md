# Native lifecycle and permission audit

Finish line: launch-time replacement respects Stop and profile ownership, reply wake leases remain bounded, and Termux permission refusal/cancellation returns a usable answer. Non-goal: UI changes, owner-phone operations, broad process discovery, signing or delivery.

Reviewed merged changes: `72eb991a` (reply wake lock), `94d6a32e` (Termux permission clarity), `bf5b8b24` and `4f62f559` (launch/stale replacement), against the audit worktree base. Changes are backend/native and focused tests only. Integration commit IDs are recorded by the coordinator in README.md.

| ID | Severity | Failure scenario and evidence | Fix location | Status |
| --- | --- | --- | --- | --- |
| NL-1 | Medium | Stop during launch's asynchronous password-rejection probe, or during password-file writing, was overwritten by `starter.start` using the unconditional manual native start. Both failing-first tests observed one start when zero were allowed. | `lib/builtin/phone_server_healing.dart:194`, `lib/builtin/builtin_server.dart:221`, `lib/builtin/builtin_linux.dart:296`, `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:340` | Fixed: reread native intent after probing; require readable selected runtime owner and current policy; pass native foreground/generation admission through new `startServerForLaunch`; reuse native restart admission and confirmation. Explicit manual Start remains unchanged; launch success still resets its crash budget. |
| NL-2 | Low | A renewal could observe a live server, let concurrent native Stop release the wake lock, then acquire it after shutdown. Separately, the last Dart renewal requested ten minutes with only five minutes left before the six-hour ceiling; delayed Dart release could exceed that ceiling. | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:561`, `lib/builtin/reply_watch.dart:186` | Fixed: service shutdown and wake admission share the runtime monitor; lease length is clipped to remaining ceiling, timer tick tracks elapsed periods, queued renewals recheck current demand/disposal. |
| NL-3 | Medium | A service's Java `Process` can already have exited before its watcher removes the service. Its old PID can be reused; `stopTree` previously scanned and signaled descendants without checking that the tracked Process was alive. | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:935` | Fixed: an already-exited Process returns before PID lookup/descendant enumeration. Native harness passes an exited Process and verifies zero descendant lookups. Live process-tree shutdown remains limited to the tracked tree; no process-name matching was added. |
| NL-4 | Low | An older native bridge missing `requestRunCommandAccess` falls back to the old permission request. A platform refusal thrown inside that catch bypassed the sibling catch and escaped to the caller. Native cancellation with empty grant results was also mislabeled permanently denied. | `lib/termux/termux_reach.dart:108`, `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt:829` | Fixed: catch compatibility-path failures locally, return denied, and classify empty native grant results as cancellation/denied. |

## Verification

All Flutter invocations used the repository's pinned Flutter 3.47.1 through `tool/qa/machine_lock.sh test --`. Dart files were formatted with its bundled Dart and `format --language-version=3.10`.

* `native-red.log`: failing-first reply ceiling and both Stop race tests, **28 passed / 3 failed** before fixes.
* `permission-red.log`: failing-first compatibility permission request, **0 passed / 1 failed** with `PlatformException(permission_in_progress)`.
* `native-green.log`: **47 passed**, reply watch, phone healing, existing starter, bridge and executed native race/PID harness. The bridge test later gained another token assertion, and the native harness later gained cancellation cases; those final tests are checked separately below.
* `native-race-before.log`: executable harness using the original committed native wake/stop methods; expected failure proves Stop can be followed by stale wake admission. This is a before-source replay, not an Android device run.
* `native-final.log`: **7 passed, 0 skipped**, final permission fallback, bridge token/refusal, and Kotlin race/PID/permission-cancellation behavior tests.

The Kotlin test compiles the actual production method bodies (including the synchronization annotation) into a small host harness with deterministic concurrent Stop/admission and fake Android collaborators. It also executes the production permission-answer expression. It is a behavior test, not a source-text assertion. Missing Kotlin or Java is an explicit skip on other hosts; this machine executes it. This does not substitute for an Android compile or runtime battery measurement; the coordinator owns the integration compile/analyzer/gates.

No owner phone was accessed. No credentials were logged. No UI hook-up is required: existing launch, permission and reply-watch consumers retain their public user flows; the internal native/Dart launch method ships together. Older native runners that lack the guarded method fail closed instead of falling back to an unconditional restart. A selected local profile with ambiguous or different runtime ownership requires the existing explicit Start flow to claim ownership.

Commands (using the pinned Flutter path above):

```sh
tool/qa/machine_lock.sh test -- "$F" test --concurrency=1 test/reply_watch_test.dart test/phone_server_healing_test.dart
tool/qa/machine_lock.sh test -- "$F" test --no-pub --concurrency=1 test/reply_watch_test.dart test/phone_server_healing_test.dart test/builtin_recovery_bridge_test.dart test/builtin_server_test.dart test/native_lifecycle_contract_test.dart
tool/qa/machine_lock.sh test -- "$F" test --no-pub --concurrency=1 test/termux_access_audit_test.dart test/builtin_recovery_bridge_test.dart test/native_lifecycle_contract_test.dart
```

`F=/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`. Scoped `git diff --check` passed after the final source/test edits. The first command is the recorded red run; the second and third are green runs. No source edits occurred after the green lifecycle run; the third run covers final additions to bridge/native tests and the permission fallback fix.
