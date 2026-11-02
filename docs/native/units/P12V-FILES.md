VERIFIED: green

Scope of "green": every test the FILES unit added or changed passes, the APK assembles, and Konsist finds nothing in
the unit's files. Two red results remain in modules the unit shares with the base. **Both reproduce identically on
bare `native/m1-2026-11-03` without the unit**, so they are base failures, not FILES failures (see "Base failures
left red").

# P12V-FILES — cloud verification of the FILES unit (2026-11-02)

- Verified branch: `native/p12-files-2026-11-03` at `7c8b3002` (pushed here as `claude/files-unit-verification-35lkrb`,
  the name this session's git rules fixed). Base: `native/m1-2026-11-03` at `8a4eee69`.
- Machine: Claude Code cloud, JDK 21 (OpenJDK, Gradle runtime; toolchain JDK 17 provisioned by Gradle), Android SDK
  from `commandlinetools-linux-16111833` with `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`.
- `dl.google.com` answered 200 (the original unit's blocker is gone).

## Environment workaround (not committed)

Maven Central (`repo.maven.apache.org` / `repo1.maven.org`) answered **429 Too Many Requests** to this machine on every
attempt. Builds used an uncommitted Gradle init script, `~/.gradle/init.d/central-mirror.gradle.kts`, that puts
Google's Maven Central mirror (`https://maven-central.storage-download.googleapis.com/maven2`) in front of the
repositories in `pluginManagement` and `dependencyResolutionManagement`. Artifacts are the same coordinates; no repo
file changed.

## Commands and results

All from `native/` with `JAVA_HOME=<jdk21> ANDROID_HOME=/opt/android-sdk`, `--no-daemon --continue`.

| Command | Result |
|---|---|
| `:engine:files:desktopTest` | **24/24 pass** |
| `:feature:files:desktopTest` | 25/26 on first run; **26/26 pass** after the fix below (includes the Compose `FilesScreensTest`, never run before) |
| `:protocol:oc2:desktopTest` | **98 pass, 1 skipped, 0 fail** (includes `Oc2FilesTest`, and the fixture suite the unit could not run) |
| `:ui:kit-content:desktopTest` | **50/50 pass** (includes `KitViewerBytesDesktopTest`, never run before) |
| `:feature:chat:desktopTest` | test sources did not compile (base); after the compile fixes below: **102 run, 94 pass, 8 fail** — the same 8 fail on the bare base |
| `:androidApp:assemblePreviewDebug` | **BUILD OK**, `androidApp-preview-debug.apk` (22.6 MB). Compiles `FilesWiring.kt`, `FilesGraph.kt` (Metro), `FilesEntries.kt`, the FileProvider manifest entry, `KitViewerBytes.kt` and the chat edit |
| `:androidApp:testPreviewDebugUnitTest` | **12/12 pass**, including `FilesWiringTest` (real socket over HTTP, 1/1) |
| `:quality:test` | test sources did not compile (base); after the fix: **48 run, 47 pass, 1 fail** — K1 on base file `BasicComposerHost.kt`, untouched by the unit |

No module of the unit has Android host-test sources (`engine/files` and `feature/files` are commonMain/commonTest/
desktopTest only), so there is no `testDebugUnitTest`/`testAndroidHostTest` for them; their Android targets compile as
part of `assemblePreviewDebug`.

The integrator risks the unit report listed all compiled without a touch: Metro's `Set<ConversationDrafts>` multibinding
and `FilesGraphAccessors`, `androidx.core.content.FileProvider` on `:androidApp`'s classpath, and the
`decodeToImageBitmap` opt-in.

## Failures fixed

1. **Inside the write set** — `feature/files/src/desktopTest/.../FilesScreensTest.kt`,
   `B-NAV-026 the project root lists its folders and files as rows that open`. The test looked for the visible text
   `README.md`; the kit's `KitRow(titleIsFileName = true)` inserts a zero-width space before the extension so long names
   wrap there, and sets the row's content description to the exact name. The test now finds the row by that spoken
   name (`onNodeWithContentDescription("README.md")`). Same assertion (both rows open, in order); the code was right.
   Commit `3d86f6a2`.

## Files changed outside the write set (all test-only, all base drift)

The `8a4eee69` snapshot ("…unqualified model/file composer WIP") added `BasicComposerViewModel(filePickers)` and two
`SessionSelectionGateway` members without updating every test caller. Without these, `:feature:chat:desktopTest`
(a module the unit touched) and `:quality:test` cannot compile at all. No assertion changed.

| File | Change | Commit |
|---|---|---|
| `feature/chat/src/commonTest/.../ComposerDraftViewModelTest.kt` | pass `emptySet()` for `filePickers` | `45708691` |
| `feature/chat/src/commonTest/.../WorldComposerFilesModelsTest.kt` | selection fake implements `createSelectedSession` / `setSessionAgent` as `Failure.Unavailable` (no test calls them) | `45708691` |
| `feature/chat/src/commonTest/.../ChatFakes.kt` | `FakeChatWorld` is `open` (a desktop test subclasses it) | `45708691` |
| `feature/chat/src/desktopTest/.../ComposerFilesViewModelTest.kt` | the subclass uses the inherited `calls` list instead of hiding it; every assertion filters by prefix (`replace…`), so the result is unchanged | `45708691` |
| `quality/src/test/.../ChatWorldReviewTest.kt` | pass `emptySet()` for `filePickers` | `bbfbbfca` |

## Base failures left red (not the unit's; reproduced on the bare base)

Checked in a separate worktree of `native/m1-2026-11-03` with only the compile fixes above applied:
`:feature:chat:desktopTest` → the same **102 run, 8 fail**, the same 8 names. They belong to the composer model/file
WIP (owner: composer/chat), not to FILES:

- `ComposerFilesViewModelTest` (4): B-STATE-103 a picked file appends to the latest draft files…; a refused commit
  surfaces as an attachment failure…; an unsettled draft fails closed…; a picker failure surfaces…
- `ComposerModelsFilesTest` (3): B-STATE-103 the model sheet shows loading and empty copy; attach is
  explained-disabled…; Arabic copy renders for the model control…
- `WorldComposerFilesModelsTest` (1): B-CHAT-107 retry reuses the original files and model…

`:quality:test` → `KonsistRulesTest > K1 features arrange kit parts only` fails on
`feature/chat/.../composer/BasicComposerHost.kt` only (byte-identical to the base). Every FILES file passes K1/K2.

These were left alone deliberately: they encode composer behaviour that the WIP has not finished, they are outside the
FILES write set, and they do not block the FILES unit from compiling or testing.

## Remaining gaps (from the unit report, still open)

- No emulator here: the device journeys (chat → Files → folder → file → Ask about this file; Open with on a PNG; Share
  on Wi-Fi scanned from a laptop, `.env` refused; a PDF) still need the PC emulator.
- Deviations 1–9 of `P12-FILES.md` are unchanged (reply path chips, rendered Markdown, isolated PDF renderer, viewer
  menu items, Files hub, phone-file links, Arabic copy review, `Mentions.resolveFileMentions` on send).
- `:desktopApp:compileKotlin` and the full `native` test suite were not run (outside the brief).
