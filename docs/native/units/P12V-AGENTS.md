VERIFIED: green

# P12V-AGENTS — cloud verification of the AGENTS parity unit (2026-11-02)

Unit: AGENTS (report `docs/native/units/P12-AGENTS.md`, written by a session that could not reach Google Maven).
Verified on branch `claude/verify-agents-parity-unit-l3phzy` (this session's push branch), starting from `30fee1e3`;
base `native/m1-2026-11-03` at `8a4eee69`. Final candidate: `6343f275` plus this report.

## Environment

- `curl https://dl.google.com/android/repository/repository2-3.xml` → HTTP 200 (Google Maven reachable).
- Android SDK at `/opt/android-sdk`: cmdline-tools `commandlinetools-linux-16111833`, licenses accepted,
  `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`, `build-tools;36.1.0`. `native/local.properties`
  (gitignored) holds `sdk.dir`.
- Gradle 9.7.1 on JDK 21 (OpenJDK 21); JDK 17 toolchain through foojay.
- `repo.maven.apache.org` answered **429 Too Many Requests** to this host on the first configure. A user-level init
  script (`~/.gradle/init.d/central-mirror.gradle.kts`, outside the repo) puts Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com/maven2/`) first. No repository file changed for this.

## Commands and results (all on the final candidate, `--rerun-tasks`)

```
cd native
./gradlew --rerun-tasks :protocol:paseo:desktopTest :engine:agents:desktopTest :feature:agents:desktopTest \
  :feature:servers:desktopTest :ui:routes:desktopTest :ui:words:desktopTest :quality:test \
  :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug --continue     → BUILD SUCCESSFUL
./gradlew -p build-logic test                                                          → BUILD SUCCESSFUL
```

| Task | Tests | Failures |
|---|---|---|
| `:protocol:paseo:desktopTest` | 231 | 0 |
| `:engine:agents:desktopTest` | 225 | 0 |
| `:feature:agents:desktopTest` (incl. `AgentsScreenTest`, 5 Compose tests, en + ar) | 45 | 0 |
| `:feature:servers:desktopTest` | 14 | 0 |
| `:ui:routes:desktopTest` | 1 | 0 |
| `:ui:words:desktopTest` | 4 | 0 |
| `:quality:test` (architecture rules + graph smoke) | 48 | 0 |
| `:androidApp:testPreviewDebugUnitTest` | 11 | 0 |
| `build-logic test` (module rules) | 20 | 0 |
| `:androidApp:assemblePreviewDebug` | — | APK built (`androidApp-preview-debug.apk`, 23 MB) |

The unit's KMP modules have no Android host-test task (`testDebugUnitTest`/`testAndroidHostTest` do not exist for
them); their Android sources compile as part of `assemblePreviewDebug`. The one Android-only module the unit touched,
`:androidApp` (`AgentsWiring.kt`), ran `testPreviewDebugUnitTest`.

## Failures fixed

1. **Inside the write set** — `AgentsScreenTest` "B-PROTO-070 the add form checks before saving and names the ready
   agents" recorded no clicks (`expected [check, save] but was []`). The add form is taller than the desktop test
   window, so the click landed outside it. Fix: `performScrollTo()` before each click (commit `92540452`). Same
   assertions; the Compose code was right. No other Compose API fix was needed in `AgentsScreen.kt`.
2. **Outside the write set, base `native/m1-2026-11-03` breakage that blocked `:quality:test`** (commit `6343f275`):
   - `native/quality/src/test/.../ChatWorldReviewTest.kt`: did not compile — `BasicComposerViewModel` gained a
     `filePickers: Set<PromptFilePicker>` parameter in the base composer WIP. Now passes `emptySet()`.
   - `native/feature/chat/src/commonMain/.../composer/BasicComposerHost.kt`: K1 (kit-only imports) refused
     `org.jetbrains.compose.resources.StringResource`. `attachmentWords` is now a `@Composable` that returns the
     string through `stringResource(...)`; the mapping and the words shown are unchanged. (`feature/chat` is the chat
     owner's library; this is the minimal edit, flagged for that owner.)

No other shared file was edited by this verification.

## Remaining gaps

- **`:feature:chat:desktopTest` does not compile on the base** (not run as a gate here, not part of this unit; left
  for the chat/composer owner): `ComposerDraftViewModelTest.kt:42` (missing `filePickers`),
  `WorldComposerFilesModelsTest.kt:163` (`SelectionGateway` lacks `createSelectedSession`/`setSessionAgent`),
  `ComposerFilesViewModelTest.kt:44/46` (extends final `FakeChatWorld`). Production `:feature:chat` compiles.
- No device or emulator run: add a computer running `paseo start --no-relay`, check, save, see its agents in
  Conversations, start one, send a prompt — still pending (see P12-AGENTS "Integrator checklist").
- Phone agents (built-in Linux), the composer agent chip and Claude Agent via OpenCode stay unavailable, as P12-AGENTS
  "Not done" describes; this verification did not change product scope.
- The unit's work was pushed by its author to `claude/native-agents-parity-ee753d`; the brief names
  `native/p12-agents-2026-11-03`. This verification is pushed to `claude/verify-agents-parity-unit-l3phzy` only.

## Shipping states

Implemented: as P12-AGENTS. **Verified:** compile + host/desktop tests + preview APK assembly (above). Committed and
pushed to the verification branch. Not deployed, not released, no PR, no CI run.
