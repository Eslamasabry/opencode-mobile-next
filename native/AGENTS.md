# native/AGENTS.md

Rules for the native (Kotlin Multiplatform) rewrite. Binding sources: `docs/design/native-rewrite-2026-11-01/plan/01`
(decisions) and `plan/03` (modules). Slice-0 results and deviations: `docs/native/slice0/P1-skeleton.md`.

## Toolchain

- Run Gradle on **JDK 21** (`JAVA_HOME`): Metro 1.5.1's Gradle plugin requires a JVM 21 runtime. Module bytecode
  targets 17 through the convention toolchains, so **JDK 17 must also be installed** (or let foojay download it).
- Android SDK: `platforms;android-37.1` (compileSdk 37, minor 1: Compose 1.13 requires it), `build-tools;37.0.0`.
  `local.properties` holds `sdk.dir` (gitignored).

## Rules (plan 03 §19.7, verbatim)

1. Read plan sections 01 (decisions) and 03 (modules) before touching code. The module rules fail the build; don't fight them.
2. Features arrange kit parts. A missing component is added to the kit by its owner, never drawn in a feature.
3. One screen = XUi + XActions + XViewModel + XScreen(state, actions) + XEntries. No copy, no Compose, no platform types in presentation.
4. Engines own the truth: StateFlow out, suspend commands returning Outcome in, one serial lane per engine.
5. Expected failures are Failure values. Use attempt {}; never runCatching around suspend code; never show exception text.
6. No Dispatchers.*, Clock.System, GlobalScope or runBlocking outside :core:common. Inject AppDispatchers and Clock.
7. Log only through AppLog with lambdas. Never log credentials, tokens, prompt text, URLs with queries or file contents.
8. Every URL the app did not author goes through ExternalLinkPolicy → ExternalLinkOpener.
9. Per-profile keys are oc.<what>.<profileId> and registered with ProfileScope; per-profile files and tables too.
10. UI gates on ServerCapabilities / PlatformCapabilities, never on BackendKind.
11. Name every test after its catalog row: `B-AREA-NNN what it proves`. Write it red first, from the row.
12. Run target tasks only (desktopTest, testAndroidHostTest, compileKotlinDesktop). Never `build`. One Gradle build at a time on the PC.
13. Format only the files you changed (tool/format-changed.sh). Commit with [skip ci].
14. Shared modules (kit, words, routes, shell, core, connection, transcript) have one owner: propose, don't edit.
15. Steer, never queue, where steering exists. Actions name their target. No new bar or row levels. Plain words.

## Tasks

| Need | Command |
|---|---|
| Module rules | `./gradlew -p build-logic test` |
| One module's common tests | `./gradlew :<module>:desktopTest` |
| Architecture rules + graph smoke | `./gradlew :quality:test` |
| App APK (preview id `io.github.eslamasabry.opencode_mobile.native`) | `./gradlew :androidApp:assembleDebug` |
| Desktop compile | `./gradlew :desktopApp:compileKotlin` |
| iOS klibs (VPS nightly only) | `tool/qa/ios-klib-nightly.sh` |

Desktop compile per change vs nightly (ADR §4 row 17): decided by P8 on the PC and the VPS; until then, a local T0
compiles one target and the T1/T2 runs compile both (slice-0 container numbers are in the P1 report).
