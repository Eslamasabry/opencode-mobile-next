VERIFIED: green

# P12V-LINUX: cloud verification of the P12-LINUX unit

Date: 2026-11-02. Branch: `claude/verify-native-parity-linux-t2xkv6` (the session's assigned branch; it carries the
unit's commits `ab5cf7b6..d1442b98` on base `8a4eee69`). Verified head: `51b84380` plus this report.

## Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml`: **200**.
- Android SDK at `/opt/android-sdk` from `commandlinetools-linux-16111833_latest.zip`; licenses accepted;
  `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`, `build-tools;36.1.0`. `native/local.properties`
  (gitignored) holds `sdk.dir`.
- Gradle 9.7.1 on JDK 21 (OpenJDK 21); the JDK 17 toolchain came through foojay.
- **Maven Central answered 429 (Too Many Requests)** to this container. A local, uncommitted init script
  (`~/.gradle/init.d/central-mirror.gradle`) puts `google()` and Google's Central mirror
  (`maven-central.storage-download.googleapis.com/maven2`) first. It changes only where the same artifacts are
  downloaded from, not what the build uses. Nothing in the repo changed for this.

## Commands and results (final run, all on the verified head)

| Command | Result |
|---|---|
| `:feature:linux:desktopTest` | **47 tests, 0 failures** (LinuxScreensTest 5 Compose tests now run, plus the 42 the unit had run in its scratch build) |
| `:feature:phone:desktopTest` | **42 tests, 0 failures** (includes PhoneScreensTest) |
| `:feature:servers:desktopTest` | **14 tests, 0 failures** |
| `:ui:routes:desktopTest` | **1 test, 0 failures** |
| `:feature:linux:compileAndroidMain`, `:feature:chat:compileAndroidMain`, `:feature:chat:compileKotlinDesktop` | compile |
| `:androidApp:assemblePreviewDebug` | **BUILD SUCCESSFUL**, `androidApp-preview-debug.apk` 23,331,539 bytes (Metro graph with LinuxWiring; no duplicate Pacer) |
| `:androidApp:testPreviewDebugUnitTest` | **11 tests, 0 failures** |
| `:quality:test` (Konsist + ModuleOwners + graph smoke) | **48 tests, 0 failures** |

The touched feature modules have no Android host-test task (`testDebugUnitTest`/`testAndroidHostTest` do not exist
for them); their Android side was checked by `compileAndroidMain` and by the APK.

The first compile of everything the unit wrote (Compose screens, ViewModels, entries, Metro, LinuxWiring) succeeded
without changes. All fixes below came from `:quality:test`.

## Failures found and fixed

1. **`:quality:test` did not compile (base, not the unit).** `ChatWorldReviewTest` called
   `BasicComposerViewModel(target, worlds)`; the base composer WIP made `filePickers` required. Now passes
   `emptySet()`. The test is about words kept until delivery admission and predates files, so its meaning is unchanged.
2. **ModuleOwnersTest**: `:feature:linux` was missing (the unit report's integrator item 2). Row added:
   `:feature:linux	F	P12-LINUX	parity`. (`:feature:phone` was already registered.)
3. **Konsist K1 (features import kit parts only)**: 4 unit files and 1 base file.
   - `LinuxWords.kt` imported `StringResource`: the seven word functions are now `@Composable … : String =
     stringResource(when …)` (the phone module's pattern); callers in the four screens drop their `stringResource(…)`
     wrapper; `LinuxHomeScreen.PageRow` takes Strings instead of fully-qualified `StringResource`s.
   - `LinuxJobNoticeWords.kt` imported `getString` (the notice is read outside composition): the binding moved into
     `LinuxWiring.linuxJobNotices()` (androidApp is outside K1); the feature file is deleted. `androidApp` gains
     `implementation(libs.compose.resources)` for `getString`.
   - `LinuxManager.kt` imported `delay` for the passing-failure retry: new `fun interface LinuxRetryWait`, injected into
     `LinuxManager` and bound in `LinuxWiring.linuxRetryWait()` as `delay(LinuxManager.RETRY_DELAY)` (700 ms, the
     Flutter `linux_manager.dart` value). Tests pass `VirtualRetryWait` (the same 700 ms on the test clock), so the
     B-LINUX-092 retry tests keep their meaning.
   - `LinuxJobWatch.kt` imported `kotlinx.coroutines.sync.Mutex`: the job, the start epoch and the dismissed flag are
     now one value in a `MutableStateFlow`, changed only by atomic `update {}` (the stale-read guard is the same epoch
     compare, now inside the atomic update). `job` is `Flow<LinuxJobState>` (`map { shown }.distinctUntilChanged()`);
     every consumer only collected or combined it. `dismiss()` no longer suspends.
   - `BasicComposerHost.kt` (base composer WIP) imported `StringResource` for `attachmentWords`: same
     `stringResource(when …)` form, one call site.

No assertion was weakened and no test was removed or skipped.

## Files changed outside the unit's write set

- `native/MODULE_OWNERS` (coordinator file): one row, as the unit report asked.
- `native/quality/src/test/.../ChatWorldReviewTest.kt`: one argument (base breakage, item 1).
- `native/feature/chat/src/commonMain/.../composer/BasicComposerHost.kt` (single-owner chat library): the K1 fix in
  item 3, needed for a green `:quality:test`. The chat owner should review it.

In the write set: `feature/linux/**`, `androidApp/.../graph/LinuxWiring.kt`, `androidApp/build.gradle.kts` (one more
line).

## Commits

- `fd4a2460` fix(feature-linux): keep Linux pages inside the K1 kit-only imports
- `85471a5c` build(native): register feature-linux in MODULE_OWNERS
- `51b84380` fix(feature-chat): base composer WIP blocked quality test compile

`tool/check_commits.sh HEAD~3 HEAD`: OK. All carry `[skip ci]`.

## Remaining gaps

- Not run: `:feature:chat:desktopTest`. By inspection, the base's
  `feature/chat/src/commonTest/.../ComposerDraftViewModelTest.kt:42` also omits `filePickers`, so that suite will not
  compile until the composer WIP owner fixes it. Outside this unit; left alone.
- Not run: `./gradlew -p build-logic test`, a desktop app compile, any emulator or device check. The APK is an
  unsigned-for-release debug preview build; it was not installed anywhere.
- The functional gaps in P12-LINUX §2, §3 and §5 items 3–6 are unchanged: the built-in Linux is still not in the APK
  (`AbsentLinuxHost(NotOnThisBuild)`), `LinuxResetFlow.resumeIfPending()` is not called at start, and the two
  proposed `:ui:words` strings are not added. If another unit binds `Pacer`, delete `LinuxWiring.pacer()`.
- `LinuxRetryWait` is a new seam: whoever writes the BuiltinLinux adapter (§5 item 5) keeps the
  `LinuxWiring.linuxRetryWait()` binding.
