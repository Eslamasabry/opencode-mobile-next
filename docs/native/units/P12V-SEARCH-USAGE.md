VERIFIED: green

# P12V-SEARCH-USAGE: cloud verification of P12-SEARCH-USAGE

Branch: `claude/verify-search-usage-parity-tzwzrh`. It is the unit branch (`3a53b8d2`, on base snapshot `8a4eee69`) plus four
fix commits: `259f3b3d`, `29aee2e1`, `dd5ad9d6`, `8469eaa0`. The brief named `native/p12-search-2026-11-03`, but this
session can push only to its assigned branch.

## Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` returned HTTP 200.
- Android SDK: commandlinetools `16111833` in `/opt/android-sdk/cmdline-tools/latest`, with `platform-tools`,
  `platforms;android-37.1` and `build-tools;37.0.0`. AGP installed `build-tools;36.0.0` by itself. `native/local.properties`
  is gitignored.
- Gradle 9.7.1 on OpenJDK 21. No JDK 17 was needed for these tasks.
- **Maven Central returned HTTP 429** (Too Many Requests) to Gradle from this container's IP, on both
  `repo.maven.apache.org` and `repo1.maven.org`. To get past it, I put a machine-local init script in
  `~/.gradle/init.d/central-mirror.gradle.kts`, outside the repo and not committed. It redirects Central to Google's
  mirror, `maven-central.storage-download.googleapis.com/maven2`. A cloud verifier that hits the same 429s needs the
  same script.

## Commands and results

Every command ran from `native/` with `ANDROID_HOME=/opt/android-sdk JAVA_HOME=<jdk21>`. The final run used
`--rerun-tasks`, so 509 of 509 tasks ran and nothing came from cache.

| Command | Result |
|---|---|
| `:feature:search:desktopTest` | 22 tests passed, 0 failed (match 5, ViewModel 11, screen 6) |
| `:feature:usage:desktopTest` | 20 tests passed, 0 failed (query 7, ViewModel 8, screen 5) |
| `:feature:chats:desktopTest` | 23 passed |
| `:feature:servers:desktopTest` | 14 passed |
| `:protocol:oc2:desktopTest` | 92 tests: 91 passed, 1 skipped (that skip was already in the base), 0 failed |
| `:ui:routes:desktopTest` / `:ui:words:desktopTest` | 1 / 4 passed |
| `:engine:usage:desktopTest` | NO-SOURCE. Its behaviour is tested through `UsageQueryTest` and `UsageViewModelTest`. |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL, `androidApp-preview-debug.apk` (22.7 MB) |
| `:quality:test` | 48 passed, 0 failed (it was 0 run, then 1 failed, before the fixes below) |
| `./gradlew -p build-logic test` | 20 passed |

**Android unit tests:** the touched feature modules (`oc.kmp.feature`) have no Android host-test task. Their Android
code is compiled by `assemblePreviewDebug`. `:androidApp` has no unit tests for the wiring.

## What failed and how it was fixed

1. **The unit's code compiled on the first try**, and all 42 of its tests passed as written. No test was changed.
2. **K1 (features arrange kit parts only) rejected 5 unit files.** It checks imports against an allowlist.
   - `SearchViewModel` and `UsageViewModel` imported `kotlinx.coroutines.Job`. They now keep a `cancel: () -> Unit`
     lambda instead, the same pattern as `GraphChatWorld` and `SignInTerminalSession`. Cancellation behaves the same.
   - `SearchScreen` imported the `clearText` and `setTextAndPlaceCursorAtEnd` extensions. It now calls the member
     `TextFieldState.edit { replace(0, length, …); placeCursorBeforeCharAt(length) }`, which does the same thing.
   - `UsageQuery` and `UsageScreen` imported `kotlinx.datetime`. The calendar arithmetic moved to a new module,
     **`:engine:usage`** (`UsageCalendar`: start of day N days back, start of year, day label, zone readable). This
     follows the `FeedDay` precedent in `:engine:agents`. `UsageQuery.forRange` keeps the same contract: calendar days,
     not 24-hour steps; an unreadable zone gives null, never UTC; all time sends no `from`. The B-PROTO-122 tests still
     exercise the real code through `forRange`.
3. **`ModuleOwnersTest`**: the new modules had no `MODULE_OWNERS` rows. I added `:feature:search`, `:feature:usage`
   and `:engine:usage`.

## Files changed outside the unit's write set (please review)

- `native/MODULE_OWNERS`: 3 rows. The unit report says only the coordinator edits it, so the owner and `since` values
  (`P12-SEARCH-USAGE`, `slice-5`) are placeholders for the coordinator to correct.
- `native/engine/usage/` (new module): it was needed for K1. It depends only on `kotlinx-datetime` and is not in
  `ModuleRules.engineOrder`, because it uses no other engine.
- Base fixes that blocked `:quality:test` (both came from the `8a4eee69` composer WIP, not this unit):
  - `quality/…/ChatWorldReviewTest.kt`: added the missing `filePickers` argument (`emptySet()`) to
    `BasicComposerViewModel`. The test is about delivery admission, so its meaning is unchanged.
  - `feature/chat/…/composer/BasicComposerHost.kt`: `attachmentWords` is now a `@Composable` that returns `String`, so
    the file no longer imports `StringResource`, which K1 rejects. The words are the same.

## Remaining gaps

- **The `:feature:chat` test sources do not compile on the base.** This is outside this unit and I left it alone. It
  reproduces with my changes stashed, with the same 4 errors:
  - `ComposerDraftViewModelTest` is missing `filePickers`.
  - `WorldComposerFilesModelsTest.SelectionGateway` lacks `createSelectedSession` and `setSessionAgent`.
  - `ComposerFilesViewModelTest` extends a final `FakeChatWorld` and is missing an `override` for `calls`.

  The chat lead needs to fix these. `:feature:chat:compileKotlinDesktop` (main code) passes.
- Not done here: the device check (no emulator in this container), the Arabic/RTL visual pass and the iOS klibs.
- The open items from the unit report are unchanged:
  - no anchor to open a chat at the matching message (`ChatRoute`);
  - recent searches are app-wide, not per profile;
  - not built: usage budgets, Plans, the command launcher and Settings search.
- `Oc2SearchUsage.kt` in `:protocol:oc2` is still a stand-in until `HostGateway.listGlobalSessions` and a usage facet
  exist.
