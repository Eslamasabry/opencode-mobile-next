VERIFIED: green

# P12V-PLATFORM — cloud verification of the PLATFORM unit

Unit branch `native/p12-platform-2026-11-03` (checked out here as `claude/verify-platform-parity-dv28am`, head
`5691e8c9` before verification). Base `native/m1-2026-11-03` at `8a4eee69`. Unit report: `P12-PLATFORM.md`.
Verified 2026-11-02 in a Claude Code cloud container (4 vCPU, 16 GB, no emulator).

## Environment

- `https://dl.google.com/android/repository/repository2-3.xml` → HTTP 200 (Google Maven reachable this time).
- Android SDK: `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`, licences
  accepted, `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0` (and 36.0.0). `native/local.properties`
  → `sdk.dir=/opt/android-sdk` (gitignored).
- Gradle on OpenJDK 21; the JDK 17 toolchain was provisioned by foojay.
- Maven Central (`repo.maven.apache.org`) returned intermittent **HTTP 429** to this container. Workaround, kept
  **outside the repo** in `~/.gradle/init.d/mirror.gradle.kts`: Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com/maven2`) is put first for plugin and dependency resolution, and
  test tasks get `robolectric.dependency.repo.url` set to it (Robolectric downloads `android-all` itself). The same
  artifacts, from a different host. No build file was changed for this.

## Commands and results (final pass from a clean task state, `--rerun-tasks`)

```bash
cd native
./gradlew :feature:chat:compileKotlinDesktop :engine:updates:desktopTest :feature:updates:desktopTest \
  :feature:chats:desktopTest :android:tiles:testDebugUnitTest :android:widgets:testDebugUnitTest \
  :android:notifications:testDebugUnitTest :quality:test :androidApp:assemblePreviewDebug --continue --rerun-tasks
./gradlew :ui:routes:desktopTest :androidApp:testPreviewDebugUnitTest
./gradlew -p build-logic test
```

| Task | Result |
|---|---|
| `:engine:updates:desktopTest` | 21/21 pass |
| `:feature:updates:desktopTest` | 28/28 pass (1 failed at first, fixed below) |
| `:feature:chats:desktopTest` | 24/24 pass |
| `:android:tiles:testDebugUnitTest` (Robolectric) | 27/27 pass (did not compile at first, fixed below) |
| `:android:widgets:testDebugUnitTest` | 172/172 pass |
| `:android:notifications:testDebugUnitTest` | 81/81 pass |
| `:ui:routes:desktopTest` | 1/1 pass |
| `:androidApp:testPreviewDebugUnitTest` | 11/11 pass |
| `:quality:test` (Konsist, module owners, graph smoke) | 48/48 pass (did not compile, then 2 failures; fixed below) |
| `:androidApp:assemblePreviewDebug` (Metro graph included) | BUILD SUCCESSFUL, `androidApp-preview-debug.apk` 24.3 MB |
| `./gradlew -p build-logic test` (module rules) | BUILD SUCCESSFUL |

Every never-compiled file the unit listed (tile services, `PinnedShortcuts`, `PlatformWiring.kt`, the receivers,
`MainActivity` / `OcApplication` edits, the `:feature:updates` screen/VM/Compose tests, the `:android:tiles`
Robolectric tests and the `:feature:chats` edit) now compiles, and its tests run green.

## Fixes

Inside the unit's write set:

1. `feature/updates/.../UpdatesScreenUiTest.kt` — `B-MIGR-012 downloading shows Cancel and the percent`.
   - "Downloading version 2.2.0" matched two nodes: the status row, and the disabled **Check now** row, which
     repeats the status line as its disabled reason (intended). The assertion now reads the status row
     (`updates-state`).
   - "42% done" is drawn under the bar with cleared semantics; the kit reads it once as the progress bar's label. The
     assertion now checks that label (`kit-state-progress` content description).
   - Same behaviour, more precise targets; nothing weakened.
2. `android/tiles/build.gradle.kts` — `compileOptions` source/target 17. The Android library convention leaves AGP's
   default 11, which cannot inline `:core:common`'s JVM 17 `attempt {}` (4 errors in `AndroidUpdates.kt`). It is the
   same module-local seam `:android:agenthost` already uses.

Outside the write set (needed for `:quality:test`):

3. `native/MODULE_OWNERS` — rows for `:engine:updates` (S), `:feature:updates` (F), `:android:tiles` (F), owner
   `platform (P12-PLATFORM)`, since `parity`. The unit report asked the coordinator for this; `ModuleOwnersTest` fails
   without it. The coordinator may rename the owner/since cells.
4. **Base breakage, not this unit** (from `8a4eee69`'s composer WIP). Both changes are minimal:
   - `quality/.../ChatWorldReviewTest.kt`: `BasicComposerViewModel` gained a required `filePickers`, so the test now
     passes `filePickers = emptySet()`. Without it, `:quality:compileTestKotlin` failed.
   - `feature/chat/.../composer/BasicComposerHost.kt`: Konsist **K1** failed on the import
     `org.jetbrains.compose.resources.StringResource`. `attachmentWords` is now a `@Composable` that returns the
     `stringResource(...)` text. It returns the same words for the same failures, and the import is gone.
     `:feature:chat` is chat-lead's library, so they should review this edit.

## Remaining gaps

- **`:feature:chat` test sources do not compile on the base.** This is outside the unit and not caused by the edit
  above. The errors come from the composer WIP:
  - `ComposerDraftViewModelTest` does not pass `filePickers`.
  - `WorldComposerFilesModelsTest.SelectionGateway` is missing new abstract members.
  - `ComposerFilesViewModelTest` extends the now-final `FakeChatWorld`.

  The `:feature:chat` main source compiles and is part of the green APK. chat-lead owns those tests, so they were not
  rewritten here.
- Nothing was checked on a device. The device checks from the unit report (§"What the integrator must do" step 4) are
  still open: both tiles, both widget families, the launcher shortcuts, sharing text into the app, Allow once from the
  shade, and the finished-reply alert.
- The functional gaps the unit report lists are unchanged: no foreground service or background live mode, quiet hours
  not read, the widget action runners not bound, no `MY_PACKAGE_REPLACED` hook, and no native release offered to the
  preview package yet.
- Build-logic request (not done here): set JVM 17 in `OcAndroidLibraryPlugin`, so that `:android:tiles` and
  `:android:agenthost` can drop their local `compileOptions`.

## Commits (all `[skip ci]`)

- `9492f599` test(native): updates screen test reads the status row and the bar's label
- `9f8d8099` fix(native): :android:tiles compiles to JVM 17 so it can inline attempt {}
- `f551029e` chore(native): MODULE_OWNERS rows for :engine:updates, :feature:updates, :android:tiles
- `441c54fe` fix(native): base compile and K1 blockers for :quality:test
- this report
