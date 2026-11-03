VERIFIED: green

# P12-FIX-MIGRATION — Codex findings on the day-1 migration (2026-11-03)

Branch `claude/native-parity-migration-fixes-4bzhwz`, based on `native/p12-qa-wave4-2026-11-03`, with
`native/migration-proof-2026-11-03` merged first (the re-enter sheet takes the password, and the `qa` build type with
the kill/delay seam). Review: `docs/native/reviews/p12/migration.md` (4 MAJOR, 1 MINOR). **All five findings are
real; all five are fixed**, each with a test that fails without the fix.

Write set used: `native/android/migration`, `native/feature/gate`, `native/androidApp/.../graph/MigrationWiring.kt`,
the migration strings (`ui/words/.../strings_migration.xml`, English and Arabic, as the migration proof did), plus
**one line in `graph/PreviewStores.kt`** (see "Integrator" below). Linux and voice wiring were not touched.

## Findings

| # | Finding | Verdict | Fix | Test (red before → green) |
|---|---|---|---|---|
| 1 MAJOR | App secret writes/deletes bypass the credential ledger, so a GitHub sign-in + sign-out through Git leaves no record and the next launch's M2 retry restores the old Flutter token | **Real.** `PreviewStores.secrets` was the plain gated store; `GitHubTokenStore`, `ProfileStore`, sign-in reset and profile deletion all write through it; `retryFlagged()` only checks the ledger and "store has a value" | `MigrationRunner.beforeNativeChange(name, deleted)` (ledger `rev+1` → R1 stale marker, the existing rule-1 order). `MigrationWiring.ledgered(store)` wraps the app's secret store: waits for the gate, records, then writes/deletes; a record that cannot be saved refuses the change. At every open, `M2Secrets.resolveChanged()` drops re-entry flags whose secret has a ledger record (the person dealt with it natively), so the sheet stops asking | `SecretsMigrationTest` "B-SEC-084 a GitHub sign-out after the update is never undone by the retry". Mutant (hook = no-op) → fails: the old `gho_…` token is restored |
| 2 MAJOR | Every re-entry row opens `ServersRoute.Home`; no credential editor; GitHub/BYO rows go to an unrelated page; sheet acknowledged permanently | **Real**, partly fixed already by the migration proof (password/token typed in the sheet, saved on the same profile id). Still wrong on this base: GitHub and BYO rows closed the sheet for good and opened Servers | GitHub row: the sheet says "To sign in to GitHub again for ‹server›, open one of its chats, then Git." with **Open chats** (→ `ChatsRoute.Home`; GitHub sign-in lives on a chat's Git page) and Back to the list. Opening the chats closes the sheet for now **without** acknowledging it, so it comes back next start until the sign-in is done (resolved through the ledger, finding 1) or Later is picked. BYO row: "Setting up ‹host› again isn't in this version yet. Its saved details stay on this phone." + Back; opens nothing | `GatePresenterTest` "B-MIGR-006 a GitHub row says where to sign in again and opens the chats without answering the sheet", "B-MIGR-007 a BYO set-up row says plainly it is not in this version and opens nothing"; `GateScreenDesktopTest` "B-MIGR-006 a GitHub row … offers the chats" (English), "B-MIGR-007 a BYO set-up row …" (Arabic). Red before: `onReentryOpen` / the new words did not exist; the old presenter closed and acknowledged the sheet |
| 3 MAJOR | Preference loading races the S0 safety backup (`getSharedPreferences` in the store's constructor; Android's loader renames `.bak` while S0 copies) | **Real.** `PreviewStores` is built right after the gate is handed out; S0 ran asynchronously in `engine.start()` | StartupFence: `MigrationRunner.backupBeforeOpen()` runs S0 synchronously inside `MigrationWiring` before the gate (and so any store) exists. It is the same journaled step (attempts counted, LowSpace/BackupInvalid block as before), never throws; a failure is kept for this process and shown as `Blocked(StepCrashed, S0)` with Try again (the file has finished loading by then). The qa delay moved to the start of the async run so the Updating screen can still be filmed | `MigrationRunnerTest` "B-MIGR-010 the safety backup is complete before any store opens the prefs file" (`.xml` + authoritative `.bak` both byte-exact in a verifying backup, then a store opens the file) and "… a backup fence that fails shows Try again and the retry finishes". Red before: no fence API |
| 4 MAJOR | Start fresh phase 2 runs in `Application.onCreate`; an I/O failure escapes and crash-loops every start with no failure screen | **Real** | `StartFreshTransaction.recoverSafely()` (used by `AndroidMigration.recoverBeforeOpen`) never throws. The runner shows a still-pending Start fresh as `Blocked(WriteRefused, "fresh")`, or `LowSpace` when under 16 MB free: Try again / Open storage settings / Report. `retry()` never resets steps under a pending Start fresh; in the app, Try again restarts the process (`RunnerPort.retry` → restart), so phase 2 runs again before anything opens the prefs file | `StartFreshTest` "B-MIGR-010 a Start fresh that cannot finish never crashes the start and offers Try again" (IOException on the secret-file move → `Recovery.Failed`, Blocked "fresh", LowSpace when full, then a clean finish opens an empty app) |
| 5 MINOR | The deferred AI Team/Memory notice needs `firstNativeLaunch`; a migration finished in a background start never shows it | **Real** | The presenter shows the notice whenever the runner reports deferred data; the runner already keeps that set only until it is acknowledged, so it is still shown once | `GatePresenterTest` "B-MIGR-009 the deferred-features notice still shows when the migration finished in a background start". Mutant (old condition) → fails. The old "first native launch only" test was renamed to "… while it is unacknowledged and is recorded once" |

## Journeys now working (Android app, `switch` package over the Flutter app)

- Update over Flutter: S0 backup is taken before any store opens the prefs file; the rest of M1–M6 as before.
- Re-enter sheet: password/token typed in the sheet (migration proof); **GitHub** row → explanation → Open chats →
  a chat → Git → Sign in; the sheet stops asking once the token is changed natively. **BYO** row → plain "not in this
  version" line → Back. Later still acknowledges the sheet once.
- Signing in or out of GitHub, typing a new password, resetting sign-ins or deleting a server after the update is
  recorded in the credential ledger first; the M2 retry and an R1 rollback build never resurrect those values.
- Start fresh that hits a full disk: failure screen (not a crash loop) → free space → Try again restarts and finishes.
- Deferred-features notice shows on the first foreground open even when a widget/schedule start finished the migration.

How to reach: the gate wraps the whole app (`MainActivity` → `MigrationRoot`); the sheet shows on the first open with
flagged secrets; failure screens show while the gate is blocked. The side-by-side preview package has no Flutter data,
so there the gate is open from the start and `ledgered()` returns the store unchanged.

## Catalog rows

Covered by new tests: B-MIGR-006, B-MIGR-007, B-MIGR-009, B-MIGR-010, B-SEC-084 (sign-out stays signed out).
Still not covered here: B-MIGR-008 (Linux rootfs start — needs `:android:linux`, another session), B-MIGR-011 is
covered by the existing privacy tests (unchanged).

## Tests and results

Commands run on this cloud container (JDK 21, Android SDK platforms 36 and 37.1, build-tools 36.0.0/37.0.0):

```
./gradlew :android:migration:testQaUnitTest :engine:migration:desktopTest :feature:gate:desktopTest
./gradlew :androidApp:assemblePreviewDebug :quality:test :ui:words:desktopTest
```

(The `:android:migration` host tests run on the `qa` build type: `testQaUnitTest`, not `testDebugUnitTest`.)

| Task | Result |
|---|---|
| `:android:migration:testQaUnitTest` | 68 tests, 0 failed, 0 skipped (4 new) |
| `:feature:gate:desktopTest` | 33 tests, 0 failed (4 new, 2 rewritten, 1 removed) |
| `:engine:migration:desktopTest` | 12 tests, 0 failed |
| `:ui:words:desktopTest` | 4 tests, 0 failed |
| `:quality:test` (architecture rules, Konsist, graph smoke) | 59 tests, 0 failed |
| `:androidApp:assemblePreviewDebug` | built `androidApp-preview-debug.apk` |

Red before: the two mutants above (ledger hook as a no-op; the old `firstNativeLaunch` condition) each fail their new
test; the other new tests did not compile before the fix (new APIs/words).

Environment notes (not committed): Maven Central answered 429 to this container, so a Gradle init script in
`~/.gradle/init.d` puts Google's Maven Central mirror first, and Robolectric gets
`-Drobolectric.dependency.repo.url=<same mirror>` through `JAVA_TOOL_OPTIONS`. No build file was changed for this.

## Deviations

- `graph/PreviewStores.kt` changed by one constructor parameter and one line (`migration.ledgered(...)` around the
  secret store). There is no other place to put the ledger in front of the app's store without editing `:core:data`.
- `ui/words` migration strings: three new keys (English + Arabic). Other languages have no `strings_migration.xml`.
- No device run in this session (no emulator here). The fence and the Start-fresh guard are covered by JVM tests on
  the real file layouts; the migration-proof device runs (J1–J5) predate these changes.

## For the integrator

- Another `getSharedPreferences("FlutterSharedPreferences")` outside the gate exists in `SettingsWiring` (the locale
  read in `MainActivity.attachBaseContext`) and in `:android:linux` (`NativeStrings`, `BuiltinLinux`). Both run after
  `Application.onCreate`, i.e. after the S0 fence, so they are safe now; keep any new early reader after the graph.
- Re-run the migration proof J1 + J5 on `OC_API35` with this branch's `switch` build (adds: GitHub row → Open chats;
  Start fresh with `qa-free-bytes` low after phase 1).
- Flutter's own WorkManager job (proof bug 6) and the English schedule notifications (proof bug 5) are outside this
  write set and still open.
