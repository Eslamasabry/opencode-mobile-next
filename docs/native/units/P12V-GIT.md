VERIFIED: green

# P12V-GIT — cloud verification of the Git parity unit

Scope: the Git unit (`docs/native/units/P12-GIT.md`). Base `native/m1-2026-11-03` at `8a4eee69`. The unit's commits
(`c84d85f5..31db1187`) were already on the verify branch `claude/verify-native-parity-p12-v6sx69`, which is where this
verification is pushed (the harness allows only that branch).

**Green here means:** every module the unit touched compiles for desktop and Android, every Git test passes, the
`:feature:chat` and `:quality` results are identical to the base with the Git edits taken out, and the preview APK
assembles. **Two base reds are still there and are not Git's:** 8 composer tests in `:feature:chat` and Konsist K1 on
`BasicComposerHost.kt` (§3). So `:feature:chat:desktopTest` and `:quality:test` are not fully green on this branch, and
they are not fully green on the base either.

Shipping state: compiled, tested and committed/pushed. Not installed and not run on a device or emulator. No PR, CI,
signing or release.

## 1. Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` → **200**.
- Android SDK at `/opt/android-sdk`: cmdline-tools `commandlinetools-linux-16111833_latest`, licenses accepted,
  `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`. `native/local.properties` sets `sdk.dir` (gitignored).
- JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), Gradle 9.7.1 wrapper.
- `repo.maven.apache.org` answered **HTTP 429** to Gradle (curl got 429 too), so the build could not even resolve the
  foojay settings plugin. A session-local init script (`~/.gradle/init.d/central-mirror.gradle.kts`, **not in the
  repo**) points Maven Central at Google's mirror `https://maven-central.storage-download.googleapis.com/maven2/`.
  It changes no repository file.

## 2. Commands and results (from `native/`)

| Command | Result |
|---|---|
| `./gradlew :engine:git:desktopTest` | **49 tests, 0 failures** (GitScript 6, GitParsers 7, GitRepository 11, ServerGitShell 4, GitHub 10, GitPage 7, RealGit 4 against real `git`) |
| `./gradlew :feature:git:desktopTest` | First run: 13 tests, **2 failures** in `GitScreenTest` (both were test lookups, see §4). After the fix: **13 tests, 0 failures** (GitPresenterTest 7, GitScreenTest 6) |
| `./gradlew :ui:routes:desktopTest` | 1 test, 0 failures |
| `./gradlew :feature:chat:desktopTest` | First run: the **test sources did not compile on the base** (4 errors in the composer test fakes, §3). After the compile-only fix: **103 tests, 8 failures**. All 8 are base composer rows; the 1 new Git test passes |
| Same `:feature:chat:desktopTest` on a clean `8a4eee69` worktree with only the compile fix applied | **102 tests, the same 8 failures**: the Git edit adds no regression |
| `./gradlew :androidApp:assemblePreviewDebug` | **BUILD SUCCESSFUL**: `androidApp-preview-debug.apk` (22,715,634 bytes). The Android targets of `:engine:git` and `:feature:git`, `GitWiring` (Metro graph), `GitRoute`/entries and the chat edit all compile |
| `./gradlew :androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures (existing picker/files tests; the unit adds none) |
| `./gradlew :quality:test` | First run: test sources did not compile (`ChatWorldReviewTest` missing `filePickers`, base). Then 48 tests, 2 failures: `ModuleOwnersTest` (no Git rows; fixed, §4) and K1. Final: **48 tests, 1 failure**: K1 on base `BasicComposerHost.kt` (§3) |
| `testDebugUnitTest` / `testAndroidHostTest` for `:engine:git`, `:feature:git` | Not available: host tests are not enabled for these KMP modules (as for the other KMP modules; the convention plugin would need `withHostTest {}`). Their Android compile is covered by the APK assemble |
| `tool/check_commits.sh 31db1187 HEAD` | OK for the verification commits |

The final unit set was rerun once with `--rerun-tasks`:
`:feature:chat:desktopTest :engine:git:desktopTest :feature:git:desktopTest :ui:routes:desktopTest` → 103/8 (base),
49/0, 13/0, 1/0.

## 3. Base failures outside the unit (not fixed)

These come from the unfinished model/file composer WIP in the base snapshot (RUNBOOK §7 calls it unqualified):

- `:feature:chat` — `ComposerFilesViewModelTest` (4 × B-STATE-103), `ComposerModelsFilesTest` (3 × B-STATE-103: missing
  "Loading models…", the Attach content description, the Arabic "نموذج" model control), `WorldComposerFilesModelsTest`
  (B-CHAT-107 retry reuses files and model). They fail the same way on the base.
- `:quality` — `KonsistRulesTest` K1 "features arrange kit parts only" flags
  `feature/chat/src/commonMain/.../composer/BasicComposerHost.kt`, which the Git unit does not change (no Git file is flagged).

The composer is the chat lead's work. These need product fixes there, so I didn't weaken or skip any of these tests.

## 4. Fixes made

Inside the write set:

- `feature/git/src/desktopTest/.../GitScreenTest.kt`
  - B-NAV-020 "ready page": `onNodeWithText("src/app.kt")` matched two nodes (the file row and the **Changes** row,
    which also names the path). The test now asserts the file row by its path **and** `Modified · +3 −1` on one node.
    That is stronger than before, because both separate checks were folded into it. The clicks on the push row and the
    Changes row now `performScrollTo()` first.
  - B-SEC-084 "sign-in is a visible row": the GitHub row sits below the 412-dp test window in the page's scroll
    column, so the click missed and `calls` stayed empty. It now scrolls to the row before clicking. The assertion is
    unchanged.
- `feature/chat/src/desktopTest/.../ChatScreenHostTest.kt`: new test **B-NAV-020 the chat top bar has a visible Git
  action that opens the Git page**. It clicks the visible "Git" action on the real `ChatScreen` and checks that
  `openGit` is dispatched. The unit's chat entry had no test before.

Outside the write set (needed to compile or to pass the gate; listed for the integrator):

- `native/MODULE_OWNERS`: added `:engine:git F P12-GIT parity-p12` and `:feature:git F P12-GIT parity-p12`
  (P12-GIT §5.2). The coordinator owns this file, so please review the kind and owner.
- `feature/chat/src/commonTest/.../ChatFakes.kt`: `FakeChatWorld` is now `open`, because the base
  `ComposerFilesViewModelTest` extends it.
- `feature/chat/src/desktopTest/.../ComposerFilesViewModelTest.kt`: dropped the duplicate `calls` field. It hid the
  inherited `FakeChatWorld.calls`, which has the same type.
- `feature/chat/src/commonTest/.../ComposerDraftViewModelTest.kt` and
  `quality/src/test/.../ChatWorldReviewTest.kt`: pass `emptySet()` for the new `BasicComposerViewModel(filePickers)`
  parameter.
- `feature/chat/src/commonTest/.../WorldComposerFilesModelsTest.kt`: the `SelectionGateway` fake implements the
  facet's new `createSelectedSession` (answers Unavailable) and `setSessionAgent` (Ok).

All of these are compile-only. No assertion changed. Production code was not changed.

## 5. Remaining gaps

- Not run on a device or emulator: the Git page journey (open from chat → status → save/send → GitHub sign-in) has not
  been exercised in a running APK.
- The unit's integrator items P12-GIT §5.4–5.6 are still open: register `oc.githubToken.` in the profile-deletion
  transaction and "reset saved sign-ins", OC2 `remoteShell = true`, and the shared `ExternalLinkOpener`.
- B-SEC-081, B-SEC-082 (as offered), B-NAV-023 (branch chip), remotes/clone/fetch and "Write it for me" are still not
  ported (P12-GIT §2).
- No Android host tests exist for `:engine:git` and `:feature:git`.
- The unit's earlier pushed commits (`c84d85f5..31db1187`) fail `tool/check_commits.sh` (scope `native/git`, long
  subjects, one has no body). They were already pushed by the earlier session, so I left them as they are rather than
  rewrite them.
- `tool/format-changed.sh` from native/AGENTS.md does not exist in this tree, so no formatter ran. The edits follow
  the surrounding style.
