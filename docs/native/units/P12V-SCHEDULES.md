VERIFIED: green

# P12V-SCHEDULES — cloud verification of P12-SCHEDULES

Date: 2026-11-02. Branch: `claude/verify-schedules-parity-7pbyqy`. It holds the P12 unit's commits on top of
`native/m1-2026-11-03` (`8a4eee69`), plus the two fix commits below.

## Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` → 200.
- Android SDK at `/opt/android-sdk` (commandlinetools 16111833): `platform-tools`, `platforms;android-37.1`,
  `build-tools;37.0.0`. JDK 21 runs Gradle 9.7.1. `native/local.properties` is gitignored.
- `repo.maven.apache.org` answered **429 Too Many Requests** through the session proxy. A local, **uncommitted**
  `~/.gradle/init.d/central-mirror.gradle.kts` sends Maven Central requests to Google's mirror
  (`maven-central.storage-download.googleapis.com/maven2`). The repo's build files were not changed for this.

## Commands and results

All runs used `./gradlew --no-daemon` from `native/`. The final run passed in one invocation:

```
./gradlew --no-daemon :engine:schedules:desktopTest :feature:schedules:desktopTest \
  :android:schedules:testDebugUnitTest :feature:servers:desktopTest :protocol:oc2:desktopTest \
  :feature:chat:compileKotlinDesktop :androidApp:assemblePreviewDebug :quality:test --continue
→ BUILD SUCCESSFUL
./gradlew --no-daemon -p build-logic test → BUILD SUCCESSFUL
```

| Task | Tests | Failed | Skipped |
|---|---|---|---|
| `:engine:schedules:desktopTest` | 45 | 0 | 0 |
| `:feature:schedules:desktopTest` (common + `SchedulesScreenUiTest`) | 18 | 0 | 0 |
| `:android:schedules:testDebugUnitTest` (`NoticeTextTest`) | 1 | 0 | 0 |
| `:feature:servers:desktopTest` | 14 | 0 | 0 |
| `:protocol:oc2:desktopTest` (P12 changed `Capabilities.scheduledPrompts`) | 92 | 0 | 1 (already skipped on the base) |
| `:quality:test` (Konsist K1/K2/K4/K11, ModuleOwners, graph) | 48 | 0 | 0 |
| `build-logic` module rules | 20 | 0 | 0 |
| `:androidApp:assemblePreviewDebug` | — | — | built `androidApp-preview-debug.apk` (23.5 MB) |

All of the unit's Kotlin, Compose and Android code compiled on the first Gradle run. Nothing in
`SchedulesWiring.kt`, the routes or the Android module needed a change.

## Failures fixed

1. **Unit, real behaviour:** `SchedulesScreenUiTest › the editor saves what was typed` failed because `save` was
   never called. The editor's Save button sat at the end of the scrolling form, so it was off-screen. The screen
   standard says the one main action must be "visible without scrolling", so the code was wrong, not the test. The
   fix moves the typed fields into an `EditorDraft` holder, keyed on `seed`, and puts Save in `KitScreen(bottom = …)`,
   as the K01 list already does. The test is unchanged.
   File: `feature/schedules/.../ui/ScheduleEditScreen.kt`. Commit `b417fad7`.
2. **Base `native/m1-2026-11-03`, outside the unit:** `:quality:test` was blocked in two ways:
   - `ChatWorldReviewTest` did not compile: `BasicComposerViewModel` gained a required `filePickers` parameter.
     Fix: pass `filePickers = emptySet()`.
   - K1 failed on `feature/chat/.../composer/BasicComposerHost.kt`, which imported
     `org.jetbrains.compose.resources.StringResource` (not on the allowlist). Fix: `attachmentWords` became
     `@Composable` and returns the resolved `String`. Behaviour is unchanged.

   Commit `c1700ac6`.

## Files changed outside the unit's write set

- `native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/ChatWorldReviewTest.kt` (+1 argument)
- `native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt`
  (chat-lead's single-owner library: one import removed, one private helper reshaped. Chat lead, please review.)

## Remaining gaps

- **Base, not fixed (not blocking this unit):** `:feature:chat` test sources do not compile on the base. The
  composer WIP broke them in three places:
  - `ComposerDraftViewModelTest` is missing `filePickers`.
  - `WorldComposerFilesModelsTest.SelectionGateway` is missing `createSelectedSession` and `setSessionAgent`.
  - `ComposerFilesViewModelTest` extends the now-final `FakeChatWorld`.

  These belong to the chat lead. `:feature:chat:compileKotlinDesktop` (main sources) passes.
- No emulator was available. The WorkManager runs, the notice replacement, the `ScheduleOpenActivity` trampoline
  (B-NAV-054), the Flutter-prefs migration on a real device and Doze timing are still unproven on-device.
- No formatter script exists at `native/tool/format-changed.sh`, so the changed files are hand-formatted.
- These items are carried over unchanged from P12-SCHEDULES:
  - K4 date and time wheels (kit owner)
  - chat ⋯ / send-later doors (chat lead)
  - `ScheduleWords` → `:ui:words` (l10n owner)
  - POST_NOTIFICATIONS runtime request (notifications owner)
  - worker FQCN vs `ComponentFreezeTest` at the production switch
  - `schedules` vs `schedule` engine name
