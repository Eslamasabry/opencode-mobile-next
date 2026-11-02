VERIFIED: green
(Every module the unit touched compiles and passes, and both APKs assemble. `:quality:test` shows 47/48: the one failure is K1 on
`feature/chat/.../composer/BasicComposerHost.kt`, which is base composer WIP from `native/m1-2026-11-03`. This unit never touched it, and
it does not block the unit. See §3.)

# P12V: cloud verification of P12-MIGRATION

Date: 2026-11-02. Branch verified: `native/p12-migration-2026-11-03` (`769758c6`), worked on and pushed as
`claude/p12-migration-verify-cqwpik`. Base: `native/m1-2026-11-03` at `8a4eee69`. Final candidate: the head of the pushed branch
(two fix commits on top of `769758c6`, plus this report).

## 0. Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` → **200**.
- Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`. Licences accepted.
  Installed `platform-tools`, `platforms;android-37.1` and `build-tools;37.0.0` (from `build-logic/.../Sdk.kt` and `native/AGENTS.md`).
  `native/local.properties` has `sdk.dir=/opt/android-sdk` (gitignored). JDK 21 (OpenJDK 21) runs Gradle. The JDK 17 toolchain was
  not needed on this host.
- **Maven Central answered Gradle with HTTP 429** (Too Many Requests), even though plain curl got 200. It did this for the plugin
  portal's redirects and for Robolectric's runtime `android-all-instrumented` fetch. Two workarounds, both session-local and neither
  committed:
  1. `~/.gradle/init.d/central-mirror.gradle` puts Google's Maven Central mirror
     (`https://maven-central.storage-download.googleapis.com/maven2/`) first, then `google()` and the plugin portal, ahead of the
     repositories the settings files declare.
  2. `JAVA_TOOL_OPTIONS += -Drobolectric.dependency.repo.url=<the same mirror>` for the Robolectric host tests.
  Neither workaround changes what gets built; they only change where the same artifacts are downloaded from.

## 1. Commands and results (final candidate, test results cleaned first so every test ran fresh)

```bash
cd native
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk
./gradlew --no-daemon <clean*Test for each> :engine:migration:desktopTest :feature:gate:desktopTest :ui:words:desktopTest \
  :android:migration:testQaUnitTest :androidApp:testPreviewDebugUnitTest \
  :androidApp:assemblePreviewDebug :androidApp:assembleSwitchDebug :quality:test --continue
```

| Task | Result |
|---|---|
| `:engine:migration:desktopTest` | 12 tests, 0 failures (`MigrationEngineTest`) |
| `:feature:gate:desktopTest` | 24 tests, 0 failures (`GateBindingTest`, `GatePresenterTest`, plus **`GateScreenDesktopTest`, the Compose test, which ran for the first time**) |
| `:ui:words:desktopTest` | 4 tests, 0 failures |
| `:android:migration:testQaUnitTest` | 64 tests, 0 failures (the 60 JVM tests, plus **the Robolectric and `qa` tests, which ran for the first time**). `testDebugUnitTest` does not exist: the module sets `testBuildType = "qa"` |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:androidApp:assemblePreviewDebug` | OK: `io.github.eslamasabry.opencode_mobile.native`, versionCode 10003. This compiled the Compose gate, `MigrationGateHost`, `MigrationWiring`, `MainActivity`/`PreviewStores` and the Metro graph |
| `:androidApp:assembleSwitchDebug` | OK: package **`io.github.eslamasabry.opencode_mobile`** (the same package as the Flutter app), versionCode 10003 (checked with `aapt2 dump badging`) |
| `:quality:test` | 48 tests, **1 failure**: K1 on `feature/chat/.../BasicComposerHost.kt` (base, outside the unit; see §3). K1 found no violation in `feature/gate`. K2, K10, K-SEC, ModuleOwnersTest and the graph smoke tests pass |

The unit's own commands from P12 §"First thing the integrator must do" all ran. `:engine:migration`, `:feature:gate`,
`:android:migration:testQaUnitTest`, both assemble tasks and `:quality:test` are covered above.

## 2. Failures fixed

1. **Inside the write set:** `native/android/migration/.../android/AndroidMigration.kt`. `StartFreshTransaction(paths) { clock… }`
   passed the clock as a trailing lambda. That lambda bound to the last parameter (`faults: FaultPoints`), not to `now`, so it did not
   compile (`No value passed for parameter 'now'`). The fix passes `now = { … }` by name. This was the only compile slip in the unit's
   Android and Compose code that the previous session could not compile.
2. **Outside the write set (shared file, base breakage that blocked `:quality:test` from compiling):**
   `native/quality/src/test/kotlin/.../ChatWorldReviewTest.kt`. The base's composer WIP added a `filePickers: Set<PromptFilePicker>`
   constructor parameter to `BasicComposerViewModel`, and this test did not pass one. The test is text-only, so it now passes
   `filePickers = emptySet()`. Its assertions are unchanged.

No test was weakened, skipped or deleted.

## 3. Remaining failure, outside the unit (reported, not fixed)

`KonsistRulesTest > K1 features arrange kit parts only` fails on
`native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt`. That file has
non-kit Compose imports (`Column`, `Row`, `Arrangement`, `dp`, `testTag`, `TextFieldState`, …). It comes from the base snapshot
`8a4eee69`: RUNBOOK §7 lists that model/file composer WIP as never having passed the architecture gates. Making the file kit-only
means adding kit parts and rewriting the chat composer. That belongs to the chat/composer owner (`feature/chat` and the kit are
single-owner), not to this unit, and it does not block the unit from compiling or testing.

## 4. Remaining gaps (unchanged from P12 unless noted)

- No device or emulator run: J1 (install the switch build over Flutter 2.1.0+57 with the matching signer), J2 kill points, J8 low space.
- No release build, signing, lint or detekt in this verification. The debug APKs are signed with this container's debug key only.
- P12 §7 integrator items 2–6 are still open: `:android:widgets`/tiles in the app before a same-package ship (B-MIGR-013),
  the ledgered secret writer, `MigrationSignals` → analytics/diagnostics, R2 RestoreTransaction, and B-MIGR-012.
- P12 deviation 5 (prefs opened before S0) is unchanged.
