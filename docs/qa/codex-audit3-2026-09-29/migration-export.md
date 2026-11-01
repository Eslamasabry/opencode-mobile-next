# Migration and project-export audit slice

Finish line: secure the existing foreground migration and export paths, preserve credential opt-in, and record executable regressions. Non-goal: UI edits, device access, release/signing or new migration features.

## Findings and fixes

| ID | Severity | Failure / attack before fix | Location after fix | Resolution |
|---|---|---|---|---|
| ME-1 | High | A plain backup leaked `.ssh/config` when `.ssh` itself was a top-level project; the scanner removed its privacy-bearing parent component. Git credentials in username-only URLs, `extraHeader`, `.gitconfig`, worktree config, or config larger than 64 KiB also bypassed the narrow text regex. | `lib/builtin/project_export.dart:143`, `:253` | Preserve complete relative paths, classify private names/folders case-insensitively, and always keep Git configuration in private exports. No content/credential printing. |
| ME-2 | High | `lstat` followed by `FileInputStream(path)` let a source symlink swapped between check and open redirect a backup to a credential or other file; canonical parent checks also permitted intermediate links. | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ProjectExportBridge.kt:211` | Open every component relative to a held parent directory descriptor, with `O_NOFOLLOW`; reject nonregular/hardlinked leaves, and use `O_NONBLOCK` to avoid FIFO hangs. Stream the already-open descriptor. |
| ME-3 | Medium | Explicit launches of exported ManageSpaceActivity could supply Flutter cached-engine, route, entrypoint arguments or URL data. No intent filter does not block explicit intents; the inherited embedding consumed extras despite the class comment. | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ManageSpaceActivity.kt:26` | Replace incoming launch/new intents with app-authored empty explicit intents before the embedding sees them. Kotlin behavioral harness proved red before fix. |
| ME-4 | Medium | Stop/dispose while the picker or plan was pending still dispatched export afterward; two export calls could open concurrent pickers. | `lib/builtin/project_export_controller.dart:112` | Reserve the operation before the picker and check cancellation/disposal after every awaited preparation stage. Plan cleanup covers all exits. Cache clearing cannot race an export on this controller. |
| ME-5 | Medium | A “successful” archive could silently omit conflicting legacy project roots, unframeable names, unreadable/missing planned files. The user could then clear storage believing the backup complete. | `lib/builtin/project_export.dart:292`, `:324`, `writeExportPlan`; `ProjectExportBridge.kt:140` | Export planning fails on ambiguous roots/read failures or unframeable names; native missing/unsafe sources fail instead of continuing to success. Deliberately excluded symlinks/special files remain excluded. |
| ME-6 | Medium | A private Manage Space export omitted `.oc-migration-exports`, so imported Termux config/session exports could be erased by Clear storage even after a private backup. | `lib/builtin/project_export.dart:169` | Add the private migration-export root only to opted-in private archives. |
| ME-7 | Medium | Any local app able to reach the ephemeral loopback migration port could send duplicate Authorization headers. `HttpHeaders.value` threw before the receiver's catch, causing an uncaught asynchronous error and a failed/stalled transfer without knowing its bearer. | `lib/builtin/migration/termux_migration_transport.dart:373`, `:558` | Require exactly one header via a nonthrowing list lookup; reject malformed/unauthorized requests and continue accepting the legitimate transfer. Raw socket red/green regression sends duplicate lines, then the authorized chunk. |
| ME-8 | Low | Migration-offer dismissal wrote its scoped preference without closing/draining admission during profile deletion, allowing an in-flight write to race the sweep. | `lib/state/termux_migration_offer.dart:14`, `lib/domain/termux_migration_service.dart:42` | Dedicated serialized owner for this one boolean; ProfileStore/ConnectionController close and drain it before sweeping and reopen only after deletion abort with durable membership. Integration owned by links/storage worker. Controlled pending platform-write test uses actual `ProfileStore.removeScopedPreferences`. |

## Verification

Pinned Flutter: `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
All test commands were wrapped in `tool/qa/machine_lock.sh test --`; formatting used pinned `dart format --language-version=3.10`.

Red runs:

- `flutter test --concurrency=1 test/manage_space/project_export_test.dart test/termux_migration_transport_test.dart --reporter expanded`: two expected failures for privacy-bearing root paths and missing private migration exports; 20 other tests passed. `migration-export-red.log`.
- `flutter test --concurrency=1 test/manage_space/manage_space_native_test.dart --reporter expanded`: expected Kotlin behavioral failure, `caller extras reached Flutter`. `manage-native-red.log`.
- `flutter test --concurrency=1 test/manage_space/project_export_controller_test.dart --reporter expanded`: three expected failures, cancellation/disposal still called export and two picker calls occurred. `export-controller-red.log`.
- `flutter test --concurrency=1 test/termux_migration_transport_test.dart --plain-name 'unauthenticated duplicate headers' --reporter expanded`: expected uncaught `_HttpHeaders.value` and `TermuxMigrationException(timedOut)` after raw duplicate headers. `migration-headers-red.log`. An initial HttpClient-based attempt folded headers and did not exercise the bug; the committed regression uses a raw local socket. One intermediate test compilation error was corrected before this actual red result.

Focused green command: **69 passed, 0 failed, 0 skipped**, completed in 27 seconds after the shared machine slot became available. This covers the final slice source, including native intent harness and actual ProfileStore sweep drainage:

```sh
tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 \
  test/manage_space/project_export_test.dart \
  test/manage_space/project_export_controller_test.dart \
  test/manage_space/manage_space_native_test.dart \
  test/manage_space/manage_space_manifest_test.dart \
  test/manage_space/manage_space_screen_test.dart \
  test/termux_migration_transport_test.dart \
  test/termux_migration_archive_test.dart \
  test/termux_migration_controller_test.dart \
  test/termux_migration_profile_test.dart \
  test/termux_migration_offer_test.dart --reporter expanded
```

Log: `migration-export-green.log`. Native OS descriptor behavior has source review and the coordinator's passing Android compilation check (`native-compile.log`). Android does not expose `O_DIRECTORY`; the final helper checks directory type on the opened descriptor with `fstat` instead. This preserves no-follow traversal. The 69-test focused run and final full suite cover Dart behavior; this slice did not exercise Android filesystem APIs on a device. The exported-activity harness executes the real activity class against minimal embedding stubs and is not an Android instrumented test. Kotlin/java absence is an explicit skip; the compiler exists locally. No owner phone or emulator was touched.

## UI hook-up contract

No UI source was edited. Existing APIs remain compatible. Export now enters `preparing` before opening the picker, blocks double submission, and returns `ProjectExportFailure.cancelled` for Stop/dispose before native dispatch. Existing failure UI must remain visible for source errors and must not imply an archive completed. All Git config now requires private opt-in; source-error guidance should suggest resolving duplicate old/new project roots or retrying after stopping file changes. A plain export excludes recognized credential files; arbitrary secrets in source code, example env files or Git history cannot be guaranteed absent. Copy should not promise a generally secret-free archive.

Migration journal and dismissal keys retain `oc.<what>.<profileId>` ownership. Imported files/private migration exports intentionally survive source-profile removal; only orphan transfer caches are cleaned, preventing accidental deletion of already imported user projects.

Logical commits are made by the coordinator; this slice makes no commits or pushes. Final commit IDs and stable integration gates belong in the parent README.
