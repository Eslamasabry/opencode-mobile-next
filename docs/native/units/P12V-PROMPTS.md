VERIFIED: green

# P12V-PROMPTS — cloud verification of the PROMPTS unit (2026-11-02)

Verified branch: `claude/verify-prompts-parity-1kdfl2` (the unit's commits `02d840a6`, `57ccaf7c` on top of base
snapshot `8a4eee69`). The brief named `native/p12-prompts-2026-11-03`; this session can push only its assigned branch.

**Verdict:** every module the unit touched compiles. All of the unit's own tests pass (21 in `:feature:prompts`), the
preview APK builds, and `:quality:test` passes. The one red task is `:feature:chat:desktopTest`, with **8 failures that
are not caused by this unit**: the same 8 fail identically on the base `8a4eee69` (checked in a separate worktree with
the same test-compile fixes). They belong to the base snapshot's composer model/file WIP (rows B-STATE-103,
B-CHAT-107) and were left for that unit's owner (see Remaining gaps).

## Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` → 200.
- Android SDK: cmdline-tools `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`,
  licenses accepted, `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`. `native/local.properties`
  (gitignored) → `sdk.dir=/opt/android-sdk`. Gradle on JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`); foojay
  supplied the JDK 17 toolchain.
- Maven Central (`repo.maven.apache.org`, and from Robolectric `repo1.maven.org`) answered **HTTP 429** to this
  session. Workaround, session-local only (nothing committed): `~/.gradle/init.d/central-mirror.gradle` adds Google's
  Central mirror `https://maven-central.storage-download.googleapis.com/maven2` first in plugin and dependency
  repositories (plus `gradlePluginPortal()` for `build-logic`, which declares none). It also sets the Test system
  property `robolectric.dependency.repo.url` to that mirror. Without it the build can't configure, and the
  Robolectric goldens fail to fetch `android-all-instrumented`.

## Commands and results (final run, at `6da6b01f`)

`cd native && ./gradlew --no-daemon --continue <tasks>`:

| Task | Result |
|---|---|
| `:feature:prompts:desktopTest` | 21 tests, 0 failures (SavedPromptsTest 9, ServerListsTest 5, PromptsScreensTest 7) |
| `:ui:routes:desktopTest` | 1 test, 0 failures |
| `:ui:kit-chat:desktopTest` | 109 tests, 0 failures |
| `:ui:kit-chat:testAndroidHostTest` | 39 tests, 0 failures (goldens included, via Robolectric) |
| `:feature:chat:desktopTest` | 102 tests, **8 failures, all pre-existing on base** (listed below) |
| `:androidApp:assemblePreviewDebug` | BUILD OK → `androidApp/build/outputs/apk/preview/debug/androidApp-preview-debug.apk` |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:quality:test` | 48 tests, 0 failures (Konsist K1–K…, module owners, graph smoke) |

`:feature:prompts`, `:ui:routes` and `:feature:chat` expose no Android host-test task (`testAndroidHostTest` /
`testDebugUnitTest` don't exist for them). `:ui:kit-chat:testAndroidHostTest` and
`:androidApp:testPreviewDebugUnitTest` are the Android test tasks among the touched modules.

The 8 base failures in `:feature:chat:desktopTest` (identical on `8a4eee69`):

- ComposerFilesViewModelTest: B-STATE-103 a picked file appends to the latest draft files, not the picker snapshot;
  a refused commit surfaces as an attachment failure the person can dismiss; an unsettled draft fails closed with the
  picked file; a picker failure surfaces without touching the draft.
- ComposerModelsFilesTest: B-STATE-103 the model sheet shows loading and empty copy; attach is explained-disabled
  without a picker or when the chosen model refuses files; Arabic copy renders for the model control and the sheet
  title.
- WorldComposerFilesModelsTest: B-CHAT-107 retry reuses the original files and model, not the current composer.

## Fixes made (commits, all `[skip ci]`, pushed)

1. `56f3aeaf` — **outside the write set, base breakage that blocked `:feature:chat:desktopTest`**: the base snapshot
   changed `BasicComposerViewModel` (new `filePickers`), `SessionSelectionGateway` (`createSelectedSession`,
   `setSessionAgent`) and subclasses `FakeChatWorld`, but its tests weren't updated to match, so the test sources didn't
   compile. Fakes only, no assertion touched:
   `feature/chat/src/commonTest/.../ChatFakes.kt` (`open class FakeChatWorld`),
   `.../ComposerDraftViewModelTest.kt` (passes `emptySet()`),
   `.../WorldComposerFilesModelsTest.kt` (the two new facet members return `Unavailable`; the test never calls them),
   `feature/chat/src/desktopTest/.../ComposerFilesViewModelTest.kt` (uses the inherited `calls` list instead of
   hiding it; every assertion filters by prefix).
2. `44e24301` — **unit bug**:
   `androidApp/.../graph/PromptsWiring.kt` called `.id` on the nullable `toServerProfile()`. It now reads the stored
   profile's `id`. That is also safer, because a profile whose address doesn't parse still counts as live, so its
   prompts are never retired.
3. `0ae90c2a` — **outside the write set**
   (`quality/src/test/.../ChatWorldReviewTest.kt`, +1 line `emptySet()`). Same base API change as fix 1. It blocked
   `:quality:test` from compiling.
4. `6da6b01f` — `feature/chat/.../composer/BasicComposerHost.kt` (a file the unit edits). Konsist K1 failed because the
   base snapshot imported `org.jetbrains.compose.resources.StringResource` into a feature file. `attachmentWords` is
   now a `@Composable` that returns the resolved string: same cases, same strings, and the import is gone. Base
   breakage, fixed because it blocked `:quality:test`.

The unit's own code (prompts module, routes, `ComposerInbox`, chat door, `ChatScreen`/`ChatEntries`/insert effect)
compiled and passed with no changes other than fix 2.

## Remaining gaps

- **Base composer WIP: 8 red tests** in `:feature:chat` (above). They are owned by the composer model/file unit and
  were not attempted here, because they fail the same way without this unit.
- **B-SEC-172 ordering** (from the unit report): `PreviewRuntime`'s deletion command still doesn't call
  `PromptShelf.clearForProfile` before the secret is deleted. The shelf retires a removed server's data on next use
  instead. `PreviewRuntime` is outside the write set, so this goes to the integrator.
- Not run on a device or emulator. The preview APK was built (debug, preview flavour) but never installed.
- The unit report's own "Not done" items still stand: the background-work line and running-tasks chips, "Save
  prompt for later" from the composer, attachments on insert, and the OpenCode 2 `commands()`/todos protocol work.
- Maven Central 429s: the build machine needs either working Central access or the same mirror init script.
