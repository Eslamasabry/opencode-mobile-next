# 03. Architecture and modules: the buildable skeleton

Written 2026-11-01. This section turns `01-architecture-decision.md` (the ADR) into files, folders, build scripts and
Kotlin signatures that an agent can create without asking questions. **The ADR is binding.** Where this section adds a
choice the ADR did not make, the choice is listed in §0.2 with its reason. Where the two ever disagree, the ADR wins and
this file gets corrected.

## For the owner, in five lines

1. This is the blueprint for the new app's code: every folder, every module, which module may use which, and the
   exact library versions, so 60-odd small pieces can be built by different agents without stepping on each other.
2. The new code lives in a `native/` folder of today's repository, beside the frozen Flutter app; adding a screen
   means adding one folder, with no shared list for agents to fight over.
3. Each layer has one job: protocols talk to servers, engines are the app's brain, the kit draws every pixel, and
   screens only arrange kit parts. The build refuses a wrong import before any test runs.
4. Errors turn into plain words by construction, secrets are scrubbed before any log line is written, and background
   work always runs inside a scope that ends cleanly when its server, screen or app goes away.
5. Slice 0 builds about a dozen of these modules and proves them with the checks in §22–§23. You have one decision
   here (D03-1): keep the native code in `native/` of the current repository (recommended) or start a new repository.

Tags: **[V]** checked today (2026-11-01) against Maven/Google Maven metadata, published sources or official docs.
**[R]** read in the repo, a map, a research file, the ADR or memory. **[U]** unverified; the named slice-0 proof (P1–P10
from ADR §11, or P-03-x from §22.3) settles it. Behaviour ids such as `B-STATE-042` are rows of
`../catalog/BEHAVIORS.md`.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read | Then |
|---|---|---|
| Coordinator, slice 0 | all of it | create the files in §1–§8 and §22, run §23 |
| Engine or protocol builder (mostly Codex) | §0, §2, §7, §9–§13, §16–§18, §20.2–§20.3 | write the module's `contracts/<slice>.md` entries first |
| Feature builder (mostly Sonnet) | §0, §2, §7, §14–§16, §19.4, §20.1, §20.7 | build against frozen `XUi`/`XActions` |
| Kit builder | §0, §2, §12 (`KitPlatform`), §15, §19.4–§19.6, §20.5 | follow the kit section of the plan for parts |
| Android-module builder | §0, §2.3, §12, §13.6, §20.4, §21.1 | never touch `:feature:*` |
| Reviewer | §19.7 (the rules text) and §20.7 (definition of done) | reviewers never launch test processes (ADR §10.3) |

What this section does **not** decide: dates and slice contents (delivery plan), the kit part list and visual
rules (kit section), migration steps (migration section, ADR §9.7), test tooling beyond module wiring (testing
section). It gives them a place to live.

### 0.2 Choices this section adds to the ADR

| # | Choice | Why (and what it removes) |
|---|---|---|
| S1 | The Gradle root is `native/` inside the existing `oc_app` repository (owner decision **D03-1**, default yes). | Same history, same worktree habit, same CI home, the Flutter Kotlin can be copied with blame intact. Removes a second repo to keep in sync. |
| S2 | `native/settings.gradle.kts` discovers modules from folders; `MODULE_OWNERS` is the gate (a `:quality` test fails on a module without an owner row). | No central include list that every feature agent edits. |
| S3 | Backends register as a **set** of `GatewayFactory` (each knows its `BackendKind`), not a Metro map with a custom key. | One fewer Metro feature to learn; a duplicate backend fails a startup check and the graph smoke test. |
| S4 | Connection-scoped engines are reached through **accessor extensions** on `ConnectionGraph` (`graph.transcript`), each contributed by its own engine with `@ContributesTo(ConnectionScope::class)`. | `:engine:connection` sits below transcript, delivery and requests (ADR §6.2) and must not import them. |
| S5 | ViewModels that need a route argument use Metro's `ManualViewModelAssistedFactory` + `assistedMetroViewModel`. | Type-safe route arguments, no `SavedStateHandle` string keys. |
| S6 | The `Redacted` *type* lives in `:core:common` with its constructor behind the opt-in `@RedactorApi`; only `:core:security` (the `Redactor`) opts in. | `Failure` (in `:core:common`) must name `Redacted`, and `:core:common` cannot depend on `:core:security`. The ADR rule "only the Redactor constructs it" still holds and is checked by Konsist rule K13. |
| S7 | `ExternalLinkOpener.open()` accepts only an `ApprovedLink`, which only `ExternalLinkPolicy` creates. | The "every URL the app did not author goes through the link policy" invariant becomes a type, not a review item. |
| S8 | Formatting is **ktfmt 0.64, kotlinlang style**, run on changed files only by `tool/format-changed.sh`; no Gradle formatting plugin. | Agents reflowed unrelated files before (memory `agent-format-churn`); no config-time cost. |
| S9 | Flutter-era Kotlin is **copied, not `git mv`ed**, into `:android:*` modules until the switch. `tool/native/kotlin-drift.sh` lists Flutter-side Kotlin commits made after the copy point. | The frozen Flutter app still ships bug fixes until parity (ADR §10). |
| S10 | `EntryInstaller`, `AppNavigator`, `RouteMotion` and the sealed `AppRoute` live in `:ui:routes`. | Features and `:ui:shell` both depend on `:ui:routes`; neither may depend on the other. |
| S11 | Kit content slots (terminal, scanner, web preview) are an interface `KitContentSlots` declared in `:ui:kit`, implemented in `:ui:kit-content` `androidMain`, and handed to `AndroidKitPlatform` by `:androidApp`. | `:ui:kit` cannot depend on `:ui:kit-content`; Termux and CameraX stay in one `androidMain`. |
| S12 | `EntryRouter` hands targets over as **state plus acknowledgement** (`pending: StateFlow`, `consume(id)`), never a channel. | Same "no one-shot events" rule as ViewModels; survives configuration changes. |
| S13 | `AppLog` is a process-wide facade with a 64-line pre-install buffer that is flushed *through* the redactor at install. | Lines logged before the writers exist can never escape unredacted. |
| S14 | A repository interface lives in the **engine that owns the data**. Its implementation is over `KeyValueStore` now and over Room (`:core:db`) later, in the same engine. | The Room switch (ADR §9.8) changes one class per store and no caller. |
| S15 | `withHostTest {}` (Android host tests) is on only in KMP modules that have an `androidMain` (ADR §7.2 list). Every other KMP module runs `commonTest` once, as `desktopTest`. | Halves JVM test time for 40+ modules with identical code on both targets. |
| S16 | The ADR's product flavour "`main`" is named **`stable`** in Gradle. | A flavour named `main` collides with the `src/main` source set. The `.native` preview flavour keeps the ADR name `preview`. |
| S17 | Every route key implements one sealed `AppRoute : NavKey` in `:ui:routes`; the `SerializersModule` registers `subclassesOfSealed<AppRoute>()` once. | Registration can no longer be forgotten (ADR rule K8 becomes a round-trip test). |
| S18 | Feature modules generate **no** `Res` class: all copy comes from `:ui:words`, all icons from the kit. | One string source (ADR §7.5); no stray resources in features. |

---

## 1. Repository layout

### 1.1 Where the code lives (D03-1)

```
oc_app/                                  existing repository (Flutter app at the root, frozen; ADR §1)
├── native/                              ← Gradle root of the rewrite. Everything in this section is under here.
├── lib/  test/  android/  linux/  ...   Flutter app (bug fixes only until the switch; removed at switch+1)
├── docs/design/native-rewrite-2026-11-01/   the plan (copied from wt/native-plan when approved)
└── .github/workflows/native-*.yml       native CI jobs, `working-directory: native` (only on explicit request; AGENTS.md CI budget)
```

- Agents keep working as today: one `git worktree` of `oc_app` per feature branch. The native integration branch name
  belongs to the delivery plan.
- `native/` never references files outside itself, except `tool/native/kotlin-drift.sh` (S9), which reads
  `../android/app/src/main/kotlin` to report drift.
- At the switch the Flutter tree is deleted in one commit. `native/` stays where it is (moving it would rename every
  path in history and every agent brief for no product value).

**Alternative (if the owner says no to D03-1):** a new repository `oc-native` with the same tree at its root. Nothing in
this section changes except the leading `native/`.

### 1.2 The `native/` tree

```
native/
├── settings.gradle.kts              module discovery (§3.1)
├── build.gradle.kts                 root: plugin versions with `apply false`, nothing else (§3.2)
├── gradle.properties                shared flags (§4.1); per-host caps live in ~/.gradle/gradle.properties (§4.2)
├── version.properties               versionName / versionCode (base 10000, ADR §9.9)
├── gradle/
│   ├── libs.versions.toml           the one version catalog (§5)
│   └── wrapper/gradle-wrapper.properties   Gradle 9.7.1 + distributionSha256Sum
├── gradlew  gradlew.bat
├── AGENTS.md                        the rewrite's rules (§19.7 verbatim) + pointers to this plan
├── MODULE_OWNERS                    one row per module (§2.4)
├── JETPACK_AHEAD.md                 Android-only androidx APIs used ahead of CMP (ADR §7.6); rows owned by kit/shell owners
├── config/
│   ├── compose-stability.conf       (§19.2)
│   ├── detekt.yml                   (§19.3)
│   └── kit-capacity.json            hard max widths per kit part (kit section owns the values)
├── contracts/                       contracts/<slice>.md: frozen interfaces, XUi/XActions, Failure categories, rows, what the slice deletes
├── build-logic/                     included build with the convention plugins (§6)
├── core/        common security model domain data db net net-okhttp markdown testing
├── protocol/    oc2 paseo codex                      (a2a, team only if ADR §12 D6 keeps them)
├── engine/      settings diagnostics profiles connection transcript requests delivery composer
│                runtime agents attention schedule media project integrations entry   (team later)
├── ui/          design words routes kit kit-chat kit-content shell testing
├── feature/     onboarding chats chat chat-composer chat-pages servers phone agents project
│                settings usage schedules about search widget-panels               (team, factsbook later)
├── android/     linux agenthost terminal background schedule widgets media platform migration benchmark
│                                                     (team, termux only if D6/D7 keep them)
├── androidApp/  the APK (§21.1)
├── desktopApp/  kit gallery now, desktop product later (§21.2)
├── quality/     architecture tests and gates (§21.3)
├── tools/strings/  one-off ARB → XML converter + drift check (§21.4)
├── third_party/
│   ├── maven/                       vendored artifacts as a local Maven repo (sherpa-onnx AAR)
│   └── SHA256SUMS                   one line per vendored file; checked by :quality
├── tool/                            shell helpers: format-changed.sh, native/kotlin-drift.sh, qa/*.sh
└── e2e/                             Maestro flows (owned by the testing section)
```

Gradle project path = folder path with `/` replaced by `:` (`native/engine/connection` → `:engine:connection`). That is
the only naming rule for modules.

---

## 2. Module anatomy

### 2.1 A KMP logic module (core, protocol, engine)

```
engine/connection/
├── build.gradle.kts                              (§7.2: three lines)
├── README.md                                     ≤ 15 lines: what it owns, public entry points, catalog areas, owner
├── consumer-rules.pro                            optional; R8 rules this module needs in the app (published by the plugin)
└── src/
    ├── commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/
    │   ├── ConnectionRegistry.kt                 public API (explicitApi forces `public`)
    │   ├── ConnectionGraph.kt                    Metro graph extension (§13.4)
    │   ├── ConnectionSession.kt                  public API
    │   ├── state/ConnectionStatus.kt             public immutable state (stable for Compose, §19.2)
    │   └── internal/ReconnectPolicy.kt           `internal` classes: not visible to other modules
    └── commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/
        ├── ConnectionRegistryTest.kt             test names start with catalog ids (§19.4 K12)
        └── ReconnectPolicyTest.kt
```

- No `androidMain` or `desktopMain` folder unless the module is on the ADR §7.2 list. Konsist rule K10 fails otherwise.
- `internal` is the default for everything that is not the module's contract. The public surface of a module is what
  `contracts/<slice>.md` names.

### 2.2 A feature module

```
feature/chats/
├── build.gradle.kts                              plugins { id("oc.kmp.feature") } + engine deps (§7.2)
└── src/
    ├── commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/
    │   ├── ChatsEntries.kt                       the EntryInstaller; the ONLY file that imports androidx.navigation3.runtime
    │   ├── presentation/                         no androidx.compose.* imports (Konsist K2)
    │   │   ├── ChatsUi.kt                        data class ChatsUi + interface ChatsActions
    │   │   ├── ChatsViewModel.kt                 combines engine flows → StateFlow<ChatsUi>; implements ChatsActions
    │   │   └── ChatsMapping.kt                   pure functions engine state → ChatsUi (unit-tested)
    │   └── ui/
    │       ├── ChatsScreen.kt                    @Composable fun ChatsScreen(state: ChatsUi, actions: ChatsActions)
    │       └── ChatsPreviews.kt                  canned ChatsUi values + FakeChatsActions + @Preview functions
    └── commonTest/kotlin/io/github/eslamasabry/oc/feature/chats/
        └── ChatsViewModelTest.kt
```

The feature's other two files live in shared modules, one file each, owned by the feature agent (ADR §4 row 22):

```
ui/routes/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/routes/ChatsRoutes.kt
ui/words/src/commonMain/composeResources/values/strings_chats.xml        (+ values-ar/, values-es/, values-ja/,
                                                                          values-pt/, values-ru/, values-zh/)
```

### 2.3 An Android library module

```
android/linux/
├── build.gradle.kts
├── src/main/AndroidManifest.xml                  permissions/queries only; NO <service>/<receiver>/<activity> (they live in :androidApp)
├── src/main/kotlin/io/github/eslamasabry/opencode_mobile/   files copied from the Flutter app, package unchanged (S9)
├── src/main/kotlin/io/github/eslamasabry/oc/android/linux/ new files, e.g. AndroidRuntimeHost.kt (@ContributesBinding)
├── src/main/jniLibs/arm64-v8a/ libproot.so libproot-loader.so libtalloc.so ...   (x86_64 likewise)
├── src/test/kotlin/...                           JVM/Robolectric tests (the 30 existing JVM test classes, copied)
└── src/androidTest/kotlin/...                    emulator acceptance classes (BuiltinRestoreAcceptance, ...)
```

Copy recipe for Flutter-era Kotlin (S9), in this order:

1. `cp` the files from `oc_app/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/` into the module's
   `src/main/kotlin/io/github/eslamasabry/opencode_mobile/`. Record the source commit in the module README
   (`Copied from <sha>`).
2. Replace `import io.github.eslamasabry.opencode_mobile.R` (26 files reference `R` today [R]) with the module's own
   `import io.github.eslamasabry.oc.android.linux.R`, and move the resources they read into the module's `src/main/res`.
3. Classes that `:androidApp` must call and that were `internal` become `public`. Nothing else changes in the first
   commit; the copied JVM tests must pass unchanged before any refactor (builtin-linux map risk 2).
4. Delete every `MethodChannel` handler from the copy; replace it with the engine interface implementation (§12).

### 2.4 `MODULE_OWNERS`

One row per module, tab-separated: `path  kind  owner  since`. `kind` is `S` (single owner; others propose changes
through them) or `F` (one feature agent end to end), as in the ADR §5.2 table.

```
:core:common            S   core-owner          slice-0
:engine:connection      S   backend-owner       slice-1
:feature:chats          F   (slice brief names the agent)   slice-2
...
```

`:quality`'s `ModuleOwnersTest` fails when a Gradle project has no row, or a row names a missing project. Only the
coordinator edits `MODULE_OWNERS`.

---

## 3. Settings and root build files

### 3.1 `native/settings.gradle.kts` (complete)

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads JDK 21 for the Robolectric test JVM when a host lacks it (ADR §8: JDK 17 build, JDK 21 Robolectric).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // Termux terminal libraries (Apache-2.0) are built by JitPack from release tags; only that group may resolve here.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.termux.termux-app") }
        }
        // Vendored, hash-pinned artifacts (sherpa-onnx AAR). third_party/SHA256SUMS is checked by :quality.
        exclusiveContent {
            forRepository { maven { url = settingsDir.resolve("third_party/maven").toURI() } }
            filter { includeGroup("com.k2fsa.sherpa.onnx") }
        }
    }
}

rootProject.name = "opencode-mobile-native"

// Module discovery (S2): every direct child folder of these layer folders that holds a build.gradle.kts is a module.
// Adding a module never edits this file. MODULE_OWNERS is the gate (§2.4).
val layerFolders = listOf("core", "protocol", "engine", "ui", "feature", "android", "tools")
for (layer in layerFolders) {
    val dir = settingsDir.resolve(layer)
    if (!dir.isDirectory) continue
    dir.listFiles()!!
        .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":$layer:${it.name}") }
}
listOf("androidApp", "desktopApp", "quality")
    .filter { settingsDir.resolve("$it/build.gradle.kts").isFile }
    .forEach { include(":$it") }
```

### 3.2 `native/build.gradle.kts` (complete)

Every plugin is put on the root classpath once, with `apply false`, so subprojects never repeat versions (Gradle
best practice; the demo did the same [R]).

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.analysis) // applied at the root only; runs on the VPS (ADR §6.3)
}
```

Nothing else goes in the root build file: no `allprojects {}`, no `subprojects {}` (both break project isolation and
configuration cache hygiene). Shared configuration is the convention plugins' job.

---

## 4. Gradle properties

### 4.1 `native/gradle.properties` (checked in)

```properties
# Safe defaults for a 16 GB PC shared with agents (memory pc-capacity-ceiling). The VPS raises them in ~/.gradle.
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
kotlin.code.style=official
# One JVM for Gradle + Kotlin on the PC (the demo's setting); the VPS may switch to the daemon strategy.
kotlin.compiler.execution.strategy=in-process
android.useAndroidX=true
android.nonTransitiveRClass=true
# Targets: iOS klibs only when a job passes -Poc.ios=true (ADR §7.1).
oc.ios=false
# Warnings fail the build only at T2 (-Poc.werror=true), so an agent's T0 loop is not blocked by a deprecation.
oc.werror=false
# Robolectric forks per module test task (PC 1; the VPS sets 3 in ~/.gradle/gradle.properties).
oc.test.forks=1
```

### 4.2 Per-host overrides (`~/.gradle/gradle.properties`, not in the repo)

`~/.gradle/gradle.properties` wins over the project file [R demo note]. The coordinator writes these once per machine.

| Key | PC (8 CPU / 16 GB, one emulator) | VPS 169.58.71.211 (no KVM) |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx2g -XX:MaxMetaspaceSize=768m` | `-Xmx4g -XX:MaxMetaspaceSize=1g` |
| `org.gradle.workers.max` | `2` | unset (all cores) |
| `kotlin.compiler.execution.strategy` | `in-process` | `daemon` with `kotlin.daemon.jvmargs=-Xmx3g` |
| `oc.test.forks` | `1` | `3` |
| `kotlin.incremental.native` | unset | `true` (nightly iOS klib job only) |

Rules (ADR §10.3): one Gradle build at a time on the PC, agents run target-specific tasks only (never `build` or
`assemble` on multi-target modules), full and release builds run on the VPS, midnight–2 am is VPS-only.

---

## 5. Version catalog: `native/gradle/libs.versions.toml` (complete)

Versions are the ADR §8 pins. Every coordinate below was checked to exist on Maven Central or Google Maven on
2026-11-01 [V]. Only the `build-logic` owner edits this file; Kotlin, the Compose compiler, Metro and KSP move together.

```toml
[versions]
# Toolchain (move together; ADR §8)
kotlin = "2.4.21"
agp = "9.3.3"
ksp = "2.3.13"
metro = "1.5.1"
# Compose Multiplatform (fallback 1.12.2 + material3 1.12.0-alpha03, ADR §11 P1)
cmp = "1.13.0-beta01"
cmp-material3 = "1.13.0-beta01"
cmp-adaptive = "1.3.0"
nav3 = "1.2.0"
navevent = "1.1.0"
lifecycle = "2.11.0"
savedstate = "1.4.0"
activity = "1.13.0"
# Kotlin libraries
coroutines = "1.11.0"
serialization = "1.12.0"
collections-immutable = "0.5.2"
datetime = "0.8.0"
kotlinx-io = "0.9.2"
# Network and data
ktor = "3.6.1"
okhttp = "5.5.1"
room3 = "3.0.3"
sqlite = "2.7.1"
datastore = "1.3.0"
# Logging and tracing
kermit = "2.2.0"
tracing = "2.0.5"
# Content
markdown = "0.7.18"
syntaxmp = "0.3.0"
coil = "3.6.3"
telephoto = "0.19.0"
qrose = "1.3.1"
camerax = "1.6.2"
zxing-cpp = "3.1.1"
sherpa-onnx = "2.0.1"
termux = "v0.118.3"
commons-compress = "1.27.1"
# Android platform
androidx-core = "1.19.1"
work = "2.12.0"
appfunctions = "1.0.0-beta01"
profileinstaller = "1.4.1"
benchmark = "1.5.0"
uiautomator = "2.4.0"
androidx-test-ext = "1.3.0"
androidx-test-runner = "1.7.0"
androidx-compose-test-manifest = "1.13.0-beta02"
# Tests and quality
turbine = "1.2.1"
junit4 = "4.13.2"
robolectric = "4.17"
roborazzi = "1.77.0"
preview-scanner = "0.9.3"
konsist = "0.18.1"
kover = "0.9.11"
dependency-analysis = "3.20.1"
# Desktop loop (optional trial, never required; ADR §4 row 16)
hotreload = "1.2.0"

[libraries]
# --- Kotlin
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "collections-immutable" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kotlinx-io-core = { module = "org.jetbrains.kotlinx:kotlinx-io-core", version.ref = "kotlinx-io" }
# --- DI
metro-runtime = { module = "dev.zacsweers.metro:runtime", version.ref = "metro" }
metrox-viewmodel = { module = "dev.zacsweers.metro:metrox-viewmodel", version.ref = "metro" }
metrox-viewmodel-compose = { module = "dev.zacsweers.metro:metrox-viewmodel-compose", version.ref = "metro" }
# --- Compose Multiplatform (catalog coordinates; the plugin's `compose.*` aliases are deprecated)
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "cmp" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "cmp" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "cmp" }
compose-animation = { module = "org.jetbrains.compose.animation:animation", version.ref = "cmp" }
compose-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "cmp" }
compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "cmp" }
compose-ui-test = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "cmp" }
compose-ui-test-junit4 = { module = "org.jetbrains.compose.ui:ui-test-junit4", version.ref = "cmp" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "androidx-compose-test-manifest" }
# material3 and adaptive: kit modules (material3) and kit + shell (adaptive) ONLY — enforced in build-logic (§6.6)
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "cmp-material3" }
compose-material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "cmp-adaptive" }
compose-material3-adaptive-layout = { module = "org.jetbrains.compose.material3.adaptive:adaptive-layout", version.ref = "cmp-adaptive" }
compose-material3-adaptive-navigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "cmp-adaptive" }
# --- Navigation and lifecycle
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3" }
navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "nav3" }
navigationevent-compose = { module = "org.jetbrains.androidx.navigationevent:navigationevent-compose", version.ref = "navevent" }
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
savedstate = { module = "org.jetbrains.androidx.savedstate:savedstate", version.ref = "savedstate" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
# --- Network
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
# --- Data
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
# --- Logging and tracing
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
androidx-tracing = { module = "androidx.tracing:tracing", version.ref = "tracing" }
# --- Content
jetbrains-markdown = { module = "org.jetbrains:markdown", version.ref = "markdown" }
syntaxmp-tokenizer = { module = "com.gallatinapps.syntaxmp:syntaxmp-tokenizer", version.ref = "syntaxmp" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-svg = { module = "io.coil-kt.coil3:coil-svg", version.ref = "coil" }
coil-gif = { module = "io.coil-kt.coil3:coil-gif", version.ref = "coil" }
coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
telephoto-zoomable = { module = "me.saket.telephoto:zoomable", version.ref = "telephoto" }
telephoto-zoomable-image-coil3 = { module = "me.saket.telephoto:zoomable-image-coil3", version.ref = "telephoto" }
qrose = { module = "io.github.alexzhirkevich:qrose", version.ref = "qrose" }
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camerax-compose = { module = "androidx.camera:camera-compose", version.ref = "camerax" }
zxing-cpp = { module = "io.github.zxing-cpp:android", version.ref = "zxing-cpp" }
sherpa-onnx = { module = "com.k2fsa.sherpa.onnx:sherpa-onnx-android", version.ref = "sherpa-onnx" }   # vendored (third_party/maven)
termux-terminal-emulator = { module = "com.github.termux.termux-app:terminal-emulator", version.ref = "termux" }
termux-terminal-view = { module = "com.github.termux.termux-app:terminal-view", version.ref = "termux" }
commons-compress = { module = "org.apache.commons:commons-compress", version.ref = "commons-compress" }
# --- Android platform
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }
androidx-appfunctions = { module = "androidx.appfunctions:appfunctions", version.ref = "appfunctions" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
# --- Tests
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
androidx-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-compose-desktop = { module = "io.github.takahirom.roborazzi:roborazzi-compose-desktop", version.ref = "roborazzi" }
roborazzi-preview-scanner-support = { module = "io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support", version.ref = "roborazzi" }
composable-preview-scanner-android = { module = "io.github.sergio-sastre.ComposablePreviewScanner:android", version.ref = "preview-scanner" }
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
# --- Gradle plugin artifacts (compileOnly in build-logic only)
gradle-android = { module = "com.android.tools.build:gradle", version.ref = "agp" }
gradle-kotlin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
gradle-compose-compiler = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
gradle-compose = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "cmp" }
gradle-metro = { module = "dev.zacsweers.metro:gradle-plugin", version.ref = "metro" }
gradle-ksp = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }
gradle-roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi-gradle-plugin", version.ref = "roborazzi" }
gradle-room3 = { module = "androidx.room3:room3-gradle-plugin", version.ref = "room3" }

[bundles]
compose-core = ["compose-runtime", "compose-foundation", "compose-ui", "compose-animation", "compose-ui-tooling-preview"]
feature-runtime = ["lifecycle-viewmodel-compose", "lifecycle-runtime-compose", "metrox-viewmodel-compose", "navigation3-runtime"]
common-test = ["kotlinx-coroutines-test", "turbine"]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "cmp" }
compose-hotreload = { id = "org.jetbrains.compose.hot-reload", version.ref = "hotreload" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room3 = { id = "androidx.room3", version.ref = "room3" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
dependency-analysis = { id = "com.autonomousapps.dependency-analysis", version.ref = "dependency-analysis" }
# Convention plugins from build-logic (no version)
oc-kmp-library = { id = "oc.kmp.library" }
oc-kmp-compose = { id = "oc.kmp.compose" }
oc-kmp-feature = { id = "oc.kmp.feature" }
oc-jvm-library = { id = "oc.jvm.library" }
oc-android-library = { id = "oc.android.library" }
oc-android-application = { id = "oc.android.application" }
oc-desktop-application = { id = "oc.desktop.application" }
```

Notes for the catalog owner:

- `androidx.navigation3:navigation3-runtime` is published by Google as a multiplatform artifact (it has `-desktop` and
  iOS variants on Google Maven [V]); JetBrains republishes only `navigation3-ui`. Both resolve to 1.2.0.
- `ui-test-manifest` has no CMP republish (404 [V]); the Android-only `androidx.compose.ui:ui-test-manifest` goes in
  `:ui:testing` `androidMain` only. Its version follows the Compose UI that CMP 1.13.0-beta01 maps to; Gradle resolves
  the highest, so the pin is a floor [U, P7 checks the resolved version].
- Detekt 1.23.8 runs as the CLI from `tool/qa/detekt.sh` (as in the Flutter repo [R]), so it has no catalog entry and no
  Gradle plugin (§19.3).
- ktfmt 0.64 is a CLI jar fetched by `tool/format-changed.sh` with a pinned SHA-256 (§19.1); no catalog entry.
- `okhttp-mockwebserver` (`mockwebserver3`, exists for 5.5.1 [V]) is for `:core:net-okhttp`'s pinning tests only;
  add `com.squareup.okhttp3:okhttp-tls:5.5.1` [V] next to it when a test needs a self-signed leaf.

---

## 6. Convention plugins (`native/build-logic`)

### 6.1 Plugin ids and what each one applies

| Id | Class | Applies | Used by |
|---|---|---|---|
| `oc.kmp.library` | `OcKmpLibraryPlugin` | `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `kotlin.plugin.serialization`, `dev.zacsweers.metro`; targets `android` + `jvm("desktop")` (+ iOS with `-Poc.ios=true`); namespace from path; explicit API by layer; opt-ins; common deps; test deps; host tests (S15); consumer keep rules; module rules (§6.6) | core, protocol, engine, `:ui:design`, `:ui:routes` |
| `oc.kmp.compose` | `OcKmpComposePlugin` | `oc.kmp.library` + `kotlin.plugin.compose` + `org.jetbrains.compose`; `compose-stability.conf`; CMP resources config (S18); compose deps; `kotlinx-coroutines-swing` in `desktopMain`; `compose-ui-test` in `commonTest` | `:ui:words`, `:ui:kit*`, `:ui:shell`, `:ui:testing` |
| `oc.kmp.feature` | `OcKmpFeaturePlugin` | `oc.kmp.compose` + the standard feature dependencies (kit, design, words, routes, `core:domain`, `core:model`, `core:common`, lifecycle, metrox-viewmodel-compose, navigation3-runtime) + `:ui:testing` in tests | every `:feature:*` |
| `oc.jvm.library` | `OcJvmLibraryPlugin` | `kotlin("jvm")`, JDK 17 toolchain, `kotlin("test")`, module rules | `:core:net-okhttp`, `:tools:strings`, `:quality` |
| `oc.android.library` | `OcAndroidLibraryPlugin` | `com.android.library` (AGP 9 built-in Kotlin; never `kotlin.android`), Metro, SDK levels, Robolectric on JDK 21, lint, module rules | every `:android:*` except `:android:benchmark` |
| `oc.android.application` | `OcAndroidApplicationPlugin` | `com.android.application`, compose compiler, Metro, baseline profile; id, flavours, version, splits, packaging, R8, signing hook (§21.1) | `:androidApp` only |
| `oc.desktop.application` | `OcDesktopApplicationPlugin` | `kotlin("jvm")`, compose compiler, `org.jetbrains.compose`, desktop application DSL | `:desktopApp` only |

`:android:benchmark` applies `com.android.test` + `androidx.baselineprofile` directly in its own build file (one
module; no plugin needed).

### 6.2 `build-logic/settings.gradle.kts` and `build-logic/convention/build.gradle.kts`

```kotlin
// build-logic/settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
```

```kotlin
// build-logic/convention/build.gradle.kts
plugins { `kotlin-dsl` }

kotlin { jvmToolchain(17) }

dependencies {
    compileOnly(libs.gradle.android)
    compileOnly(libs.gradle.kotlin)
    compileOnly(libs.gradle.compose.compiler)
    compileOnly(libs.gradle.compose)
    compileOnly(libs.gradle.metro)
    compileOnly(libs.gradle.ksp)
    compileOnly(libs.gradle.roborazzi)
    compileOnly(libs.gradle.room3)
    testImplementation(kotlin("test"))          // ModuleRulesTest: the §6.1 table as test vectors
}

gradlePlugin {
    plugins {
        register("ocKmpLibrary") { id = "oc.kmp.library"; implementationClass = "oc.buildlogic.OcKmpLibraryPlugin" }
        register("ocKmpCompose") { id = "oc.kmp.compose"; implementationClass = "oc.buildlogic.OcKmpComposePlugin" }
        register("ocKmpFeature") { id = "oc.kmp.feature"; implementationClass = "oc.buildlogic.OcKmpFeaturePlugin" }
        register("ocJvmLibrary") { id = "oc.jvm.library"; implementationClass = "oc.buildlogic.OcJvmLibraryPlugin" }
        register("ocAndroidLibrary") { id = "oc.android.library"; implementationClass = "oc.buildlogic.OcAndroidLibraryPlugin" }
        register("ocAndroidApplication") { id = "oc.android.application"; implementationClass = "oc.buildlogic.OcAndroidApplicationPlugin" }
        register("ocDesktopApplication") { id = "oc.desktop.application"; implementationClass = "oc.buildlogic.OcDesktopApplicationPlugin" }
    }
}
```

Files in `build-logic/convention/src/main/kotlin/oc/buildlogic/`:

```
Sdk.kt                     compile/min/target SDK constants
Namespaces.kt              path → namespace (§6.3)
Catalog.kt                 Project.libs, lib("alias"), bundle("alias"), version("alias")
ModuleRules.kt             ADR §6.1 + §6.2 as data (§6.6) — coordinator-owned
ExternalRules.kt           which modules may use material3, Ktor, OkHttp, Room, Termux, CameraX, sherpa (§6.6)
EnforceRules.kt            Project.enforceModuleRules(): wires both rule sets into dependency declaration
OcKmpLibraryPlugin.kt      §6.4
OcKmpComposePlugin.kt      §6.5
OcKmpFeaturePlugin.kt
OcJvmLibraryPlugin.kt
OcAndroidLibraryPlugin.kt
OcAndroidApplicationPlugin.kt   §21.1
OcDesktopApplicationPlugin.kt   §21.2
```

### 6.3 Shared helpers

```kotlin
// Sdk.kt
internal object Sdk {
    const val COMPILE = 37      // 37.1 if Compose 1.13 demands it (ADR §8); the minor-level DSL is checked in P1 [U]
    const val TARGET = 36       // targetSdk 37 is its own slice after the switch (ADR §8)
    const val MIN = 26
}

// Namespaces.kt
internal object Namespaces {
    const val ROOT = "io.github.eslamasabry.oc"
    const val APP = "io.github.eslamasabry.opencode_mobile"   // the app's namespace = application id (ADR §5.2)

    /** ":ui:kit-chat" → "io.github.eslamasabry.oc.ui.kitchat". Hyphens drop out: a namespace is a Java package. */
    fun of(path: String): String = when (path) {
        ":androidApp" -> APP
        ":desktopApp" -> "$ROOT.desktop"
        else -> ROOT + "." + path.removePrefix(":").split(':').joinToString(".") { it.replace("-", "") }
    }
}

// Catalog.kt
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()
internal fun VersionCatalog.bundle(alias: String) = findBundle(alias).get()
```

The Kotlin root package of every KMP, JVM and new Android-module file equals the module namespace. Exceptions, and only
these: `:androidApp` OS-visible classes keep `io.github.eslamasabry.opencode_mobile` (ADR §5.2), and Kotlin copied
from the Flutter app keeps that package inside its new `:android:*` module (S9).

### 6.4 `OcKmpLibraryPlugin` (complete)

```kotlin
package oc.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class OcKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("dev.zacsweers.metro")

        val layer = ModuleRules.layerOf(path)
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)
            if (layer.explicitApi) explicitApi()

            // The AGP-KMP `android {}` block. In a build script it is `kotlin { android { … } }`; in a binary plugin the
            // target is reached as an extension of the kotlin extension [U P1: confirm the accessor on AGP 9.3.3].
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = Namespaces.of(path)
                compileSdk = Sdk.COMPILE
                minSdk = Sdk.MIN
                if (layer.hasPlatformSourceSets) withHostTest { isIncludeAndroidResources = true }   // S15
                if (file("consumer-rules.pro").isFile) {
                    optimization { consumerKeepRules.apply { publish = true; file("consumer-rules.pro") } }
                }
            }
            jvm("desktop")
            if (providers.gradleProperty("oc.ios").orNull == "true") {
                iosArm64()
                iosSimulatorArm64()
            }

            compilerOptions {
                // Both are opt-in markers on Kotlin 2.4.21's stdlib today [U: drop each once stable]:
                optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.concurrent.atomics.ExperimentalAtomicApi")
                allWarningsAsErrors.set(providers.gradleProperty("oc.werror").map(String::toBoolean).orElse(false))
                if (path == ":core:common") freeCompilerArgs.add("-Xexpect-actual-classes")  // ADR §7.3: only here
            }

            sourceSets.commonMain.dependencies {
                implementation(libs.lib("kotlinx-coroutines-core"))
                implementation(libs.lib("kotlinx-serialization-json"))
                implementation(libs.lib("kotlinx-collections-immutable"))
            }
            sourceSets.commonTest.dependencies {
                implementation(kotlin("test"))
                implementation(libs.bundle("common-test"))
                if (path != ":core:testing") implementation(project(":core:testing"))
            }
        }

        tasks.withType<Test>().configureEach {
            maxParallelForks = providers.gradleProperty("oc.test.forks").get().toInt()
            // Catalog ids in test names contain spaces; JUnit XML keeps them, the ratchet greps them (§19.6).
        }
        enforceModuleRules()
    }
}
```

`ModuleRules.layerOf(path)` returns a `Layer` with three flags used above:

| Flag | True for |
|---|---|
| `explicitApi` | `:core:*`, `:protocol:*`, `:engine:*`, `:ui:design`, `:ui:routes`, `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content` |
| `hasPlatformSourceSets` | the ADR §7.2 list: `:core:common`, `:core:data`, `:core:net`, `:core:db`, `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content`, `:ui:shell`, `:ui:testing` |
| `isTestOnly` | `:core:testing`, `:ui:testing` (may appear only in test configurations of other modules) |

### 6.5 `OcKmpComposePlugin` and `OcKmpFeaturePlugin` (complete)

```kotlin
class OcKmpComposePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("oc.kmp.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(isolated.rootProject.projectDirectory.file("config/compose-stability.conf"))
            if (providers.gradleProperty("oc.composeReports").orNull == "true") {     // used by P4 and perf work only
                reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
            }
            // Do NOT set StrongSkipping/IntrinsicRemember flags: they are DeprecationLevel.ERROR on Kotlin 2.4 [V research].
        }

        // S18: only these modules own composeResources and a public Res class.
        val ownsResources = path in setOf(":ui:words", ":ui:kit", ":ui:kit-chat", ":ui:kit-content")
        extensions.configure<ComposeExtension> {
            extensions.configure<ResourcesExtension> {
                publicResClass = ownsResources
                packageOfResClass = "${Namespaces.of(path)}.res"
                generateResClass = if (ownsResources) ResourcesExtension.ResourceClassGeneration.Always
                                   else ResourcesExtension.ResourceClassGeneration.Never
            }
        }
        if (ownsResources) {
            extensions.configure<KotlinMultiplatformExtension> {
                extensions.configure<KotlinMultiplatformAndroidLibraryTarget> { androidResources { enable = true } }
            }
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(libs.bundle("compose-core"))
                implementation(libs.lib("compose-resources"))
            }
            sourceSets.getByName("desktopMain").dependencies { implementation(libs.lib("kotlinx-coroutines-swing")) }
            sourceSets.commonTest.dependencies { implementation(libs.lib("compose-ui-test")) }
        }
    }
}

class OcKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("oc.kmp.compose")
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                listOf(":ui:kit", ":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
                    .forEach { implementation(project(it)) }
                implementation(libs.bundle("feature-runtime"))
            }
            sourceSets.commonTest.dependencies { implementation(project(":ui:testing")) }
        }
    }
}
```

A feature's own `build.gradle.kts` adds only what differs: the engines it reads and, if needed, `:ui:kit-chat` or
`:ui:kit-content` (§7.2).

### 6.6 Module rules as code (`ModuleRules.kt`, `ExternalRules.kt`, `EnforceRules.kt`)

The ADR §6.1 table and §6.2 order, executable. A violation fails **configuration**, before any compile, with a message
that tells the agent what to do.

```kotlin
internal object ModuleRules {
    /** ADR §6.2. An engine may use only engines listed above it. Only the coordinator edits this list. */
    val engineOrder = listOf(
        "settings", "diagnostics", "profiles", "connection", "transcript", "requests", "delivery", "composer",
        "runtime", "agents", "attention", "schedule", "media", "project", "integrations", "entry", "team",
    )
    private val core = setOf(":core:common", ":core:security", ":core:model", ":core:domain",
        ":core:data", ":core:db", ":core:net", ":core:markdown")
    private val kits = setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content")
    private val testOnly = setOf(":core:testing", ":ui:testing")

    /** null = allowed. [inTest] is true for any configuration whose name contains "test" (case-insensitive). */
    fun violation(from: String, to: String, inTest: Boolean): String? {
        if (to in testOnly) return if (inTest) null else "$to is test-only; use it from a test source set."
        val ok = when {
            from == ":androidApp" || from == ":quality" -> true
            from == ":desktopApp" -> !to.startsWith(":android:") && to != ":androidApp"
            from == ":core:common" || from == ":core:net-okhttp" || from.startsWith(":tools:") -> false
            from == ":core:security" || from == ":core:model" -> to == ":core:common"
            from == ":core:domain" -> to in setOf(":core:common", ":core:model", ":core:security")
            from == ":core:data" -> to in setOf(":core:common", ":core:security", ":core:model")
            from == ":core:db" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:data")
            from == ":core:net" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:net-okhttp")
            from == ":core:markdown" -> to in setOf(":core:common", ":core:model")
            from == ":core:testing" -> to in core || to == ":core:net-okhttp"
            from.startsWith(":protocol:") ->
                to in setOf(":core:common", ":core:model", ":core:domain", ":core:net", ":core:security")
            from.startsWith(":engine:") -> to in core || (to.startsWith(":engine:") && engineIsAbove(to, from))
            from == ":ui:design" -> to == ":core:common"
            from == ":ui:words" || from == ":ui:routes" -> to in setOf(":core:common", ":core:model")
            from == ":ui:kit" -> to in setOf(":ui:design", ":ui:words", ":core:common", ":core:security", ":core:model")
            from == ":ui:kit-chat" -> to in setOf(":ui:kit", ":ui:design", ":ui:words", ":core:markdown",
                ":core:model", ":core:common", ":core:security")
            from == ":ui:kit-content" -> to in setOf(":ui:kit", ":ui:design", ":ui:words", ":core:model", ":core:common")
            from == ":ui:shell" -> to in kits || to in setOf(":ui:design", ":ui:words", ":ui:routes", ":engine:entry",
                ":engine:attention", ":engine:settings", ":core:domain", ":core:model", ":core:common")
            from == ":ui:testing" -> to in kits || to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:common", ":core:model")
            from.startsWith(":feature:") -> to in kits || to.startsWith(":engine:") ||
                to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
            from == ":android:migration" ->
                to in setOf(":core:common", ":core:security", ":core:data", ":core:model", ":core:db")
            from.startsWith(":android:") ->
                to.startsWith(":core:") || to.startsWith(":engine:") || to == ":ui:words" || to == ":ui:design"
            else -> false
        }
        return if (ok) null else "Module rule: $from may not depend on $to (ADR §6.1/§6.2). " +
            "Move the shared piece down (into a lower engine or :core:domain) or ask the coordinator."
    }

    private fun engineIsAbove(to: String, from: String): Boolean =
        engineOrder.indexOf(to.substringAfterLast(':')) in 0 until engineOrder.indexOf(from.substringAfterLast(':'))

    data class Layer(val explicitApi: Boolean, val hasPlatformSourceSets: Boolean, val isTestOnly: Boolean)

    private val platformSourceSets = setOf(":core:common", ":core:data", ":core:net", ":core:db",
        ":ui:kit", ":ui:kit-chat", ":ui:kit-content", ":ui:shell", ":ui:testing")      // ADR §7.2

    fun layerOf(path: String): Layer = Layer(
        explicitApi = path.startsWith(":core:") || path.startsWith(":protocol:") || path.startsWith(":engine:") ||
            path in setOf(":ui:design", ":ui:routes") || path in kits,
        hasPlatformSourceSets = path in platformSourceSets,
        isTestOnly = path in testOnly,
    )
}
```

```kotlin
internal object ExternalRules {
    /** group (or group:artifact) → module paths that may declare it. Test configurations are exempt for *-mock/*-test. */
    val allowed: Map<String, (String) -> Boolean> = mapOf(
        "org.jetbrains.compose.material3:material3" to { p -> p in setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content") },
        "org.jetbrains.compose.material3.adaptive" to { p -> p.startsWith(":ui:kit") || p == ":ui:shell" },
        "io.ktor" to { p -> p == ":core:net" || p.startsWith(":protocol:") || p == ":core:testing" ||
            p == ":androidApp" || p == ":desktopApp" },
        "com.squareup.okhttp3" to { p -> p == ":core:net-okhttp" },
        "androidx.room3" to { p -> p == ":core:db" },
        "androidx.sqlite" to { p -> p == ":core:db" },
        "co.touchlab" to { p -> p == ":core:common" },                       // Kermit stays behind AppLog (§18)
        "com.github.termux.termux-app" to { p -> p == ":android:terminal" || p == ":ui:kit-content" },
        "androidx.camera" to { p -> p == ":ui:kit-content" || p == ":android:media" },
        "io.github.zxing-cpp" to { p -> p == ":ui:kit-content" || p == ":android:media" },
        "com.k2fsa.sherpa.onnx" to { p -> p == ":android:media" },
        "org.jetbrains:markdown" to { p -> p == ":core:markdown" },
    )
}
```

```kotlin
// EnforceRules.kt
internal fun Project.enforceModuleRules() {
    val from = path
    configurations.configureEach {
        val inTest = name.contains("test", ignoreCase = true)
        val configName = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            ModuleRules.violation(from, path, inTest)?.let { throw GradleException("$it [configuration $configName]") }
        }
        dependencies.withType(ExternalModuleDependency::class.java).configureEach {
            val key = ExternalRules.allowed.keys.firstOrNull { k -> "$group:$name" == k || group == k } ?: return@configureEach
            val isTestTool = inTest && (name.endsWith("-mock") || name.contains("test"))
            if (!isTestTool && !ExternalRules.allowed.getValue(key)(from)) {
                throw GradleException("Module rule: $from may not declare $group:$name (ADR §6.1, §19.4). " +
                    "Use the kit / :core:net / the owning module instead.")
            }
        }
    }
}
```

`ProjectDependency.path` exists since Gradle 8.11 (the old `dependencyProject` is gone in Gradle 9) [R]. The rules
only see **declared** dependencies; transitive leaks are stopped by `implementation` (never `api`) for these
libraries and by the Konsist import rules (§19.4). `build-logic`'s own `ModuleRulesTest` holds one vector per §6.1
row (allowed and denied) and runs in `./gradlew -p build-logic test`.

---

## 7. Module catalogue

### 7.1 Every module: plugin, namespace, platform folders

Namespaces follow §6.3. "a/d" = has `androidMain`/`desktopMain`. "Host" = Android host tests on (S15). Owners and
start slices are in the ADR §5.2 table and `MODULE_OWNERS`; they are not repeated here.

| Module | Plugin | Namespace = root package | a/d | Host | External deps beyond the plugin's |
|---|---|---|---|---|---|
| `:core:common` | kmp.library | `io.github.eslamasabry.oc.core.common` | yes | yes | Kermit; `androidx-tracing` (a); `kotlinx-coroutines-android` (a); `-swing` (d); kotlinx-datetime |
| `:core:security` | kmp.library | `….oc.core.security` | no | no | — |
| `:core:model` | kmp.library | `….oc.core.model` | no | no | kotlinx-datetime |
| `:core:domain` | kmp.library | `….oc.core.domain` | no | no | — |
| `:core:data` | kmp.library | `….oc.core.data` | yes | yes | `androidx-core-ktx` (a); `datastore-preferences-core` (d, desktop phase) |
| `:core:db` | kmp.library + `ksp` + `androidx.room3` | `….oc.core.db` | yes | yes | room3-runtime, sqlite-bundled; `kspAndroid`/`kspDesktop` room3-compiler |
| `:core:net` | kmp.library | `….oc.core.net` | yes | yes | ktor-client-core, -websockets; ktor-client-okhttp + `:core:net-okhttp` (a, d) |
| `:core:net-okhttp` | jvm.library | `….oc.core.netokhttp` | — | — | okhttp |
| `:core:markdown` | kmp.library | `….oc.core.markdown` | no | no | jetbrains-markdown |
| `:core:testing` | kmp.library | `….oc.core.testing` | no | no | `api`: coroutines-test, turbine, ktor-client-mock |
| `:protocol:oc2` / `paseo` / `codex` | kmp.library | `….oc.protocol.oc2` / `.paseo` / `.codex` | no | no | ktor-client-core (+ websockets for paseo, codex, PTY) |
| `:engine:<name>` (16) | kmp.library | `….oc.engine.<name>` | no | no | as the engine needs (none by default) |
| `:ui:design` | kmp.library | `….oc.ui.design` | no | no | — |
| `:ui:words` | kmp.compose | `….oc.ui.words` (`Res` in `….oc.ui.words.res`) | no | no | — |
| `:ui:routes` | kmp.library | `….oc.ui.routes` | no | no | navigation3-runtime |
| `:ui:kit` | kmp.compose | `….oc.ui.kit` | yes | yes | material3; coil-compose; navigationevent-compose; `androidx-core-ktx` (a) |
| `:ui:kit-chat` | kmp.compose | `….oc.ui.kitchat` | yes | yes | material3 |
| `:ui:kit-content` | kmp.compose | `….oc.ui.kitcontent` | yes | yes | material3, syntaxmp, qrose, telephoto, coil-svg/-gif; (a) termux-terminal-view, camerax-compose/-camera2/-lifecycle, zxing-cpp |
| `:ui:shell` | kmp.compose | `….oc.ui.shell` | yes | yes | navigation3-ui, navigationevent-compose, lifecycle-viewmodel-navigation3, material3-adaptive-navigation3, metrox-viewmodel-compose, savedstate |
| `:ui:testing` | kmp.compose | `….oc.ui.testing` | yes | yes | `api`: compose-ui-test; roborazzi-compose-desktop (d); compose-ui-test-junit4, robolectric, roborazzi-compose, ui-test-manifest (a) |
| `:feature:<name>` (15) | kmp.feature | `….oc.feature.<name>` (`chat-composer` → `chatcomposer`) | no | no | engines it reads |
| `:android:<name>` (9) | android.library | `….oc.android.<name>` | — | — | see §12 per service |
| `:android:benchmark` | `com.android.test` | `….oc.android.benchmark` | — | — | benchmark-macro-junit4, uiautomator |
| `:androidApp` | android.application | `io.github.eslamasabry.opencode_mobile` | — | — | activity-compose, work-runtime, profileinstaller, coil-network-ktor3 |
| `:desktopApp` | desktop.application | `….oc.desktop` | — | — | compose desktop, coil-network-ktor3 |
| `:quality` | jvm.library | `….oc.quality` | — | — | konsist (test) |
| `:tools:strings` | jvm.library | `….oc.tools.strings` | — | — | kotlinx-serialization-json |

(`….oc` = `io.github.eslamasabry.oc`.)

### 7.2 Example `build.gradle.kts` files (copy, then change only the marked lines)

```kotlin
// engine/connection/build.gradle.kts
plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:domain"))     // ← the core and engine modules this engine uses
        implementation(project(":core:data"))
        implementation(project(":core:net"))
        implementation(project(":core:security"))
        implementation(project(":core:model"))
        implementation(project(":core:common"))
        implementation(project(":engine:profiles")) // ← must be ABOVE connection in ADR §6.2, or configuration fails
    }
}
```

```kotlin
// feature/chats/build.gradle.kts
plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))   // ← engines this feature reads (public API only)
        implementation(project(":engine:agents"))       //   ChatFeed (merged list across live connections)
        implementation(project(":engine:attention"))
    }
}
```

```kotlin
// protocol/oc2/build.gradle.kts
plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        listOf(":core:common", ":core:model", ":core:domain", ":core:net", ":core:security").forEach { implementation(project(it)) }
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets.commonTest.dependencies { implementation(libs.ktor.client.mock) }
}
```

```kotlin
// android/linux/build.gradle.kts
plugins { id("oc.android.library") }
// jniLibs ship from src/main/jniLibs; extraction to nativeLibraryDir is set in :androidApp (useLegacyPackaging, §21.1)

dependencies {
    implementation(project(":engine:runtime"))   // implements RuntimeHost
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.commons.compress)        // Ubuntu Base tarball (as in the Flutter app [R])
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

```kotlin
// core/db/build.gradle.kts  (starts at switch+1, or earlier by the ADR §9.8 trigger)
plugins {
    id("oc.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}
room3 { schemaDirectory("$projectDir/schemas") }   // schemas are committed; :quality parses them (§19.6)
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:data")); implementation(project(":core:model")); implementation(project(":core:common"))
        implementation(libs.room3.runtime)
        implementation(libs.sqlite.bundled)
    }
}
dependencies {
    add("kspAndroid", libs.room3.compiler)   // KSP 2.3.10+ deprecates plain ksp(...) in KMP [R network-data §6.2]
    add("kspDesktop", libs.room3.compiler)
}
```

---

## 8. Source sets, targets and the tasks agents run

### 8.1 Hierarchy

The default Kotlin hierarchy template applies (no custom `dependsOn`):

```
commonMain ─┬─ androidMain          (AGP-KMP android target)
            ├─ desktopMain          (jvm("desktop"))
            └─ iosMain ─┬─ iosArm64Main            only with -Poc.ios=true
                        └─ iosSimulatorArm64Main
commonTest ─┬─ androidHostTest      only in S15 modules (withHostTest)
            ├─ desktopTest          ← where every commonTest runs by default
            └─ iosTest               nightly klib compile only; never run on Linux
```

There is no android+jvm shared source set (unsupported by Kotlin [R ADR §7.4]); JVM code shared by both goes in an
`oc.jvm.library` module with no project dependencies (`:core:net-okhttp` today).

### 8.2 What runs where (ADR §7.2, made concrete)

| Source set | Put here | Never here |
|---|---|---|
| `commonMain` | all business rules, state machines, ViewModels, screens, kit parts, protocol clients | `android.*`, `java.*`, `javax.*`, `org.json`, `String.format`, `System.*`, `Thread`, `R.*`, `LocalContext` |
| `androidMain` (S15 modules only) | `actual`s (`:core:common`), prefs adoption + Keystore (`:core:data`), OkHttp engine wiring (`:core:net`), Room driver (`:core:db`), `AndroidKitPlatform` + `JETPACK_AHEAD` items (kit, shell), Robolectric helpers (`:ui:testing`) | business rules |
| `desktopMain` | the same modules' desktop bindings; until the desktop phase only in-memory stores or `Unavailable*` stubs | product work before the desktop phase |
| `commonTest` | catalog-row tests: `kotlin.test`, coroutines-test, Turbine, fakes from `:core:testing` | Android types |
| `androidHostTest` | Robolectric tests of the module's `androidMain` (prefs XML parsing, Keystore fakes, kit Android seams, Roborazzi) | logic already covered by `commonTest` |
| Android library `src/test` | Robolectric/JVM tests for `:android:*` | — |
| Android library `src/androidTest` | emulator acceptance (`BuiltinRestoreAcceptance`, migration with a real Keystore) | — |

### 8.3 Tasks per tier (exact names; never `build` or `assemble` on a multi-target module)

| Tier (ADR §10.3) | Module kind | Task |
|---|---|---|
| T0 one row | any KMP | `./gradlew :engine:delivery:desktopTest --tests "*B-STATE-042*"` |
| T0 | KMP with `androidMain` | `./gradlew :core:data:testAndroidHostTest --tests "*B-MIGR-*"` [U P1: exact host-test task name on AGP 9.3.3] |
| T0 | Android library | `./gradlew :android:linux:testDebugUnitTest --tests "*NativeRuntimeOwnership*"` |
| T0 compile check | feature | `./gradlew :feature:chats:compileKotlinDesktop` |
| T1 (VPS) | touched modules | `./gradlew :<m>:desktopTest :<m>:testAndroidHostTest :<m>:verifyRoborazzi<Variant>` (§22.3 P-03-4 names the variant) |
| T2 (VPS) | everything | `./gradlew desktopTest testAndroidHostTest testReleaseUnitTest :quality:test :androidApp:lintStableRelease :androidApp:assembleStableRelease :androidApp:analyzeStableReleaseR8Config` + `tool/qa/detekt.sh` + size gate + component freeze [U P1: flavoured task names] |
| T2 nightly (VPS) | iOS klibs | `./gradlew -Poc.ios=true compileKotlinIosArm64 compileKotlinIosSimulatorArm64` (core, protocol, engine first; UI modules once P1 says they compile) |
| T3 (PC + emulator) | app | `./gradlew :androidApp:connectedStableReleaseAndroidTest` (testBuildType release, as today) + Maestro |
| T4 (PC + emulator) | perf | `./gradlew :android:benchmark:connectedBenchmarkAndroidTest`, `:androidApp:generateStableReleaseBaselineProfile` |

`--tests` filters match catalog ids because test functions are named `` `B-STATE-042 dispatch marker blocks auto resend`() ``.

---

## 9. Packages and naming

### 9.1 Packages inside a module

| Package (under the module root) | Holds | Visibility |
|---|---|---|
| (root) | the module's public API: interfaces, engine classes, entry points | `public` |
| `.state` | immutable state types published in `StateFlow`s (stable for Compose via `compose-stability.conf`) | `public` |
| `.internal` | implementation details | `internal` |
| `.presentation` (features) | `XUi`, `XActions`, `XViewModel`, mapping functions; **no `androidx.compose.*` imports** | `public` for the screen, else `internal` |
| `.ui` (features) | `XScreen`, private composables, previews | `internal` except `XScreen` |
| `.fake` (`:core:testing`, `:ui:testing`) | fakes and fixtures | `public` |

### 9.2 Name suffixes (one meaning each)

| Suffix | Meaning | Example |
|---|---|---|
| `…Gateway` | protocol-neutral server API (`:core:domain`) | `ServerGateway`, `PromptGateway` |
| `…Engine` / plain noun | app- or connection-scoped logic holder exposing `StateFlow` | `TranscriptEngine`, `PromptDelivery`, `ConnectionRegistry` |
| `…Store` | persistence seam (key-value or file) | `KeyValueStore`, `OfflineQueueStore` |
| `…Host` | platform service the engine drives (Android/desktop implementation) | `RuntimeHost`, `AgentHost`, `ScheduleHost` |
| `…Policy` | pure decision function or object | `ExternalLinkPolicy`, `MotionPolicy` |
| `…State` / `…Event` / `…Effect` / `reduce` | a state machine | `TurnState`, `TurnEvent`, `TurnEffect` |
| `XUi` / `XActions` / `XViewModel` / `XScreen` / `XEntries` | one screen's contract, logic, view, navigation entry | `ChatsUi`, `ChatsActions`, … |
| `XRoute` | navigation key (`:ui:routes`) | `ChatRoute` |
| `Fake…` | test double with recorded calls | `FakeServerGateway` |
| `Unavailable…` | binding that reports its capability off (desktop/iOS) | `UnavailableRuntimeHost` |
| `Android…` / `Desktop…` | platform implementation | `AndroidRuntimeHost`, `DesktopKitPlatform` |
| `Kit…` | kit part or kit seam | `KitRow`, `KitPlatform` |

Kotlin style is the official Kotlin coding conventions (`kotlin.code.style=official`), formatted by ktfmt kotlinlang
style (§19.1). Test functions use backtick names that begin with the catalog id.

---

## 10. Core seams (Kotlin signatures)

These are the shapes slice 0 freezes (ADR §10.3 "freeze before fan-out"). Method lists marked "…" are completed by the
owning module's `contracts/<slice>.md`, using the named Flutter file as the source of truth. Signatures use Kotlin
2.4 features only where stable (explicit backing fields, context parameters are allowed; neither is required).

### 10.1 `:core:common`

```kotlin
package io.github.eslamasabry.oc.core.common

// ---- Scopes (Metro). AppScope is Metro's built-in dev.zacsweers.metro.AppScope [V: in metro runtime 1.5.1].
public abstract class ConnectionScope private constructor()

/** Process-lifetime scope: SupervisorJob + default dispatcher + a handler that logs (never crashes the process). */
public class AppCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope
/** One per live connection; a child of AppCoroutineScope. Cancelled by ConnectionSession.close(). */
public class ConnectionCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope

public fun supervisedContext(parent: Job?, dispatcher: CoroutineDispatcher, name: String): CoroutineContext =
    SupervisorJob(parent) + dispatcher + CoroutineName(name) +
        CoroutineExceptionHandler { _, e -> AppLog.tagged(name).e(e) { "uncaught failure in $name" } }

// ---- Results
public sealed interface Outcome<out T> {
    public data class Ok<out T>(val value: T) : Outcome<T>
    public data class Failed(val failure: Failure) : Outcome<Nothing>
}
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) { is Outcome.Ok -> Outcome.Ok(transform(value)); is Outcome.Failed -> this }
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) { is Outcome.Ok -> transform(value); is Outcome.Failed -> this }
public inline fun <T> Outcome<T>.onOk(block: (T) -> Unit): Outcome<T> = also { if (it is Outcome.Ok) block(it.value) }
public inline fun <T> Outcome<T>.onFailed(block: (Failure) -> Unit): Outcome<T> = also { if (it is Outcome.Failed) block(it.failure) }
public fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value
public fun Outcome<*>.failureOrNull(): Failure? = (this as? Outcome.Failed)?.failure

/**
 * The only sanctioned way to call code that may throw (Konsist K3 bans runCatching around suspend calls).
 * Cancellation is rethrown, never turned into a Failure. [classify] is the boundary's translator (§16.1).
 */
public suspend inline fun <T> attempt(classify: (Throwable) -> Failure, block: () -> T): Outcome<T> =
    try { Outcome.Ok(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Throwable) { Outcome.Failed(classify(e)) }

// ---- Failure: a closed hierarchy, one subtype per Flutter ProductFailureCategory (lib/domain/product_failure.dart [R])
public sealed interface Failure {
    /** Technical text for the Details fold ONLY (ADR §9.6). Null only for app-authored refusals. */
    public val details: Redacted?

    public data class Refused(val reason: RefusalReason, override val details: Redacted? = null) : Failure      // `words`
    public data class Unavailable(val feature: FeatureId, override val details: Redacted? = null) : Failure    // capability default
    public data class StagedRevert(override val details: Redacted?) : Failure
    public data class Folder(val problem: FolderProblem, override val details: Redacted?) : Failure          // 4 folder* categories
    public data class Network(override val details: Redacted?) : Failure
    public data class TimedOut(override val details: Redacted?) : Failure                                     // 408, timeouts
    public data class Certificate(override val details: Redacted?) : Failure                                  // pin/chain refused
    public data class SignIn(override val details: Redacted?) : Failure                                       // 401/403, token
    public data class NotFound(override val details: Redacted?) : Failure                                     // 404/410
    public data class Conflict(override val details: Redacted?) : Failure                                     // 409
    public data class Busy(override val details: Redacted?) : Failure                                         // 429, SessionBusyError
    public data class Server(val status: Int?, override val details: Redacted?) : Failure                     // >= 500
    public data class Rejected(val status: Int?, override val details: Redacted?) : Failure                   // other 4xx
    public data class ComputerDidNotAnswer(override val details: Redacted?) : Failure                         // `computer` (Paseo host)
    public data class UnreadableReply(override val details: Redacted?) : Failure                              // decode failed
    public data class Device(override val details: Redacted?) : Failure                                       // platform API refused
    public data class Storage(override val details: Redacted?) : Failure                                      // disk, prefs commit, Keystore
    public data class ProjectGit(override val details: Redacted?) : Failure
    public data class Termux(override val details: Redacted?) : Failure                                       // kept while D7 = migrate-out
    public data class Unexpected(override val details: Redacted?) : Failure                                   // a bug path, still plain words
    public data class Unknown(override val details: Redacted?) : Failure                                      // the generic connectivity line
}
public enum class FolderProblem { NotInstalled, Missing, Denied, Linked }
/** App-authored refusal reasons. Append-only; each has exactly one sentence in :ui:words (exhaustive when). */
public enum class RefusalReason {
    ProfileClosing, ProfileDeleted, StaleScope, ConsentMissing, CleartextNotConfirmed, PasswordMissing,
    AlreadyAnswered, DispatchUncertain, OfflineQueueFull, MigrationPending, NotOnThisPhone,
}
/** Names a ServerCapabilities/PlatformCapabilities flag exactly as spelled in the Kotlin data class. */
@JvmInline public value class FeatureId(public val name: String)

// ---- Redacted (S6)
@RequiresOptIn("Only the Redactor in :core:security constructs Redacted text.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
public annotation class RedactorApi

@JvmInline
public value class Redacted @RedactorApi constructor(public val text: String) {
    public companion object {
        /** Exception class name + optional HTTP status only. A class name cannot carry a secret; a message can. */
        @OptIn(RedactorApi::class)
        public fun shape(error: Throwable?, status: Int? = null): Redacted =
            Redacted(listOfNotNull(error?.let { it::class.simpleName }, status?.let { "HTTP $it" }).joinToString(" · ").ifEmpty { "no message" })
    }
}

// ---- Dispatchers and time (ADR §9.5). Dispatchers.* and Clock.System appear ONLY in this module (Konsist K4).
public interface AppDispatchers {
    public val main: CoroutineDispatcher       // Main.immediate (Android Looper; desktop Swing EDT)
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
    /** One serial lane per engine or per-profile store: default.limitedParallelism(1, name). */
    public fun serial(name: String): CoroutineDispatcher
}
internal expect val platformIoDispatcher: CoroutineDispatcher   // Dispatchers.IO on JVM/Android/Native; the ADR's one allowed expect
internal object PlatformDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = platformIoDispatcher
    override fun serial(name: String): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, name)
}
@ContributesTo(AppScope::class)
public interface CommonBindings {
    @Provides @SingleIn(AppScope::class) public fun dispatchers(): AppDispatchers = PlatformDispatchers
    @Provides public fun clock(): Clock = Clock.System              // kotlin.time.Clock
    @Provides @SingleIn(AppScope::class) public fun appScope(d: AppDispatchers): AppCoroutineScope =
        AppCoroutineScope(supervisedContext(null, d.default, "app"))
}

// ---- Identifiers (value classes; serializable for route keys and storage)
@Serializable @JvmInline public value class ProfileId(public val raw: String)
@Serializable @JvmInline public value class SessionId(public val raw: String)
@Serializable @JvmInline public value class MessageId(public val raw: String)
@Serializable @JvmInline public value class PartId(public val raw: String)
@Serializable @JvmInline public value class RequestId(public val raw: String)
@Serializable @JvmInline public value class ScheduleId(public val raw: String)
@Serializable public data class ConversationRef(val profileId: ProfileId, val sessionId: SessionId)

// ---- Build facts (KMP modules have no BuildConfig; ADR §5.2 :androidApp) and launch mode (crash-loop guard)
public enum class Channel { Stable, Preview, Desktop }
public data class AppBuildInfo(val versionName: String, val versionCode: Int, val channel: Channel, val debuggable: Boolean)
public enum class LaunchMode { Normal, SafeMode }

// ---- Platform view handle (ADR §7.3 item 3): only kit androidMain casts `native`.
public interface PlatformViewHandle { public val native: Any }

// ---- Wire JSON (network-data §4.3): one instance; never echoes input into exceptions.
public val WireJson: Json = Json {
    ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = false
    exceptionsWithDebugInfo = false
}
// + TolerantListSerializer<T>, LenientDoubleSerializer, and the open-enum pattern `@JvmInline value class Phase(val raw: String)`.
```

`AppLog`, `LogSink` and `Trace` are in §18.

### 10.2 `:core:security`

```kotlin
package io.github.eslamasabry.oc.core.security

/** The one redactor (port of KitRedact + the redaction profiles). Registered secrets plus token/URL-credential patterns.
 *  Created in OcApplication.onCreate step 2, BEFORE the graph, and handed to the graph factory (§13.2); not @Inject. */
public class Redactor {
    public fun registerSecret(value: String)          // called on EVERY secret read and write, before any log line (ADR §9.7)
    public fun forget(value: String)
    public fun redact(text: String): String            // known secrets → "•••", Authorization headers, ?token=, user:pass@
    @OptIn(RedactorApi::class)
    public fun details(text: String): Redacted = Redacted(redact(text).take(12_000))   // 12,000 = Flutter sanitize limit [R]
    public fun details(error: Throwable, status: Int? = null): Redacted               // shape + redacted, length-capped message
}

/** The root log sink; AppLog.install accepts only a RedactingLogSink (§18.1). */
public class RedactingWriter(private val redactor: Redactor, private val delegates: List<LogSink>) : RedactingLogSink

// ---- Links (AGENTS.md invariant; S7)
public enum class LinkOrigin { AppAuthored, ServerContent, Markdown, AgentCard, Terminal, Notification }
public class ApprovedLink internal constructor(public val uri: String, public val origin: LinkOrigin)
public sealed interface LinkDecision {
    public data class Open(val link: ApprovedLink) : LinkDecision
    public data class AskFirst(val link: ApprovedLink, val shownHost: String) : LinkDecision   // kit confirm sheet first
    public data class Refuse(val reason: LinkRefusal) : LinkDecision
}
public enum class LinkRefusal { BadScheme, CredentialsInUrl, TooLong, NotAUrl, IntentScheme }
@Inject public class ExternalLinkPolicy { public fun check(raw: String, origin: LinkOrigin): LinkDecision }
/** Implemented once in :android:platform (Intent.ACTION_VIEW lives only there and in the kit handler, Konsist K6). */
public fun interface ExternalLinkOpener { public fun open(link: ApprovedLink): Outcome<Unit> }

// ---- Addresses and cleartext consent (PROTOCOLS-01/02; Android NSC cannot express 100.64.0.0/10, so code decides)
public enum class HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }
public object AddressRules {
    public fun classify(host: String): HostClass       // RFC1918, 100.64.0.0/10, *.ts.net, ::1, fe80::/10 …
    public fun normalize(input: String): Outcome<ServerOrigin>
}
public sealed interface CleartextConsent {             // three-state reads: missing ≠ refused
    public data object Unknown : CleartextConsent
    public data class Granted(val origin: ServerOrigin, val atMs: Long) : CleartextConsent
    public data object Refused : CleartextConsent
}
```

### 10.3 `:core:model`

Plain, immutable, `@Serializable` where stored or routed. All collections are `ImmutableList`/`ImmutableMap`. No
behaviour beyond pure helpers. The full list ports `lib/domain/server_gateway/value_types*.dart` (PROTOCOLS-39).

```kotlin
public enum class BackendKind { OpenCode2, Paseo, Codex }          // OpenCode 1 is recognition-only (ADR §5.2 :protocol:oc2)
@Serializable public data class ServerOrigin(val scheme: String, val host: String, val port: Int)
@Serializable public data class ProfileTrust(val pinnedLeafSha256: String?, val cleartextConsentAtMs: Long?)
@Serializable public data class ServerProfile(
    val id: ProfileId, val name: String, val backend: BackendKind, val origin: ServerOrigin,
    val trust: ProfileTrust, val managedOnPhone: Boolean, val username: String?,  // password lives in SecretStore only
)
public enum class StreamStatus { Connecting, Connected, Reconnecting, Disconnected }
public sealed interface StreamSignal<out T> {
    public data class Status(val status: StreamStatus, val nextAttemptAtMs: Long?) : StreamSignal<Nothing>
    public data class Item<T>(val value: T) : StreamSignal<T>   // after every Connected the consumer reconciles by refetch
}
public data class ServerPage<T>(val items: ImmutableList<T>, val nextCursor: String?)
// Session, MessageInfo, MessageWithParts, sealed Part (Text, Reasoning, Tool, File, Agent, Notice, Unknown),
// ToolState (Pending | Running | Completed | Error), Tokens, ModelRef, ProviderInfo, AgentInfo, FileNode, FileDiff,
// PermissionRequest, FormSpec (the one form model), CardSpec (oc-ui card v1), ScheduleSpec, …  — ported 1:1.
```

### 10.4 `:core:domain` — gateways, capabilities, shared platform services

```kotlin
package io.github.eslamasabry.oc.core.domain

// ---- The live gateway (PROTOCOLS-37). One per connected location. Facets keep agents' briefs small.
public interface HealthGateway { public suspend fun health(): Outcome<Health> }
public interface SessionGateway {
    public suspend fun sessionPage(cursor: String? = null, limit: Int = 100): Outcome<ServerPage<Session>>
    public suspend fun createSession(): Outcome<Session>
    public suspend fun deleteSession(id: SessionId): Outcome<Unit>
    public suspend fun renameSession(id: SessionId, title: String): Outcome<Unit>
    public suspend fun session(id: SessionId): Outcome<Session>
    public suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>>
    public suspend fun messagePage(id: SessionId, cursor: String? = null, limit: Int = 100): Outcome<ServerPage<MessageWithParts>>
    public suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>>
}
public interface PromptGateway {
    public suspend fun prompt(session: SessionId, request: PromptRequest): Outcome<Unit>
    public suspend fun shell(session: SessionId, request: ShellRequest): Outcome<Unit>
    public suspend fun slashCommand(session: SessionId, command: String, args: String, model: ModelSelection?): Outcome<Unit>
    public suspend fun abort(session: SessionId): Outcome<Unit>
}
public data class PromptRequest(
    val text: String, val model: ModelSelection?, val agent: String?,
    val attachments: ImmutableList<PromptAttachment>, val mentions: ImmutableList<PromptAgentMention>,
    val delivery: PromptDelivery?,          // Steer | Queue — the composer offers Steer wherever it exists (owner rule)
    val clientMessageId: MessageId?,        // set only on CorrelatedPromptGateway backends
)
public enum class PromptDelivery { Steer, Queue }
public interface PermissionGateway { /* pendingPermissions(), respondPermission(id, reply, message) — interfaces.dart:131 */ }
public interface QuestionGateway  { /* legacy questions — interfaces.dart:190 */ }
public interface FormGateway      { /* pendingForms(), replyForm(), cancelForm() — interfaces.dart:152; types moved into core:model */ }
public interface InboxGateway     { /* inbox(), cancelInboxItem(), steerInboxItem(), queueInboxItem() — interfaces.dart:176 */ }
public interface ProviderGateway  { /* providers(), configuredProviders(), agents() — interfaces.dart:201 */ }
public interface FileGateway      { /* listFiles(), fileContent(), findFile(), findText() — interfaces.dart:208 */ }
public interface EventGateway {
    /** Cold flow: collecting opens the stream, cancelling closes it. Durable events are never dropped (ADR §9.5). */
    public fun events(scope: EventScope = EventScope.Location): Flow<StreamSignal<ServerEvent>>
}

public interface ServerGateway : HealthGateway, SessionGateway, PromptGateway, PermissionGateway, QuestionGateway,
    FormGateway, InboxGateway, ProviderGateway, FileGateway, EventGateway {
    /** Computed from the probed server version, not fixed per backend (fixes PROTOCOLS-38's known weakness). */
    public val capabilities: StateFlow<ServerCapabilities>
    public val location: StateFlow<ServerLocation>
    public fun setLocation(location: ServerLocation)
    public suspend fun close()
}

// ---- Product operations (ServerOperationsGateway). Every method has a default returning Unavailable, exactly like
// Flutter's ProductRepository, so a backend overrides only what it supports.
public interface SessionOperationsGateway {
    public suspend fun forkSession(id: SessionId, from: MessageId? = null): Outcome<SessionId> = unavailable("sessionFork")
    public suspend fun compactSession(id: SessionId, model: ModelSelection?): Outcome<Unit> = unavailable("sessionCompact")
    public suspend fun revertSession(id: SessionId, to: MessageId): Outcome<Unit> = unavailable("sessionRevert")
    // … the rest of interfaces.dart:252–273
}
public interface ServerOperationsGateway : SessionOperationsGateway, HostGateway, VcsGateway, TerminalGateway,
    ManagedShellGateway, CatalogGateway, McpGateway, IntegrationGateway, RequestGateway
public fun unavailable(feature: String): Outcome<Nothing> = Outcome.Failed(Failure.Unavailable(FeatureId(feature)))

/** Optional facets (CorrelatedPromptGateway, MidTurnPromptGateway, StagedRevertGateway, SessionReadStateGateway, …,
 *  interfaces.dart:90–494) are separate interfaces. Only ENGINES test for them; features gate on capabilities. */
public inline fun <reified F : Any> ServerGateway.facet(): F? = this as? F

// ---- Backends plug in here (S3)
public interface GatewayFactory {
    public val backend: BackendKind
    public suspend fun connect(profile: ServerProfile, credentials: ConnectionCredentials): Outcome<GatewayPair>
}
public data class GatewayPair(val live: ServerGateway, val operations: ServerOperationsGateway)

// ---- Capabilities
/** 85 flags, names identical to Dart's ServerCapabilities fields; defaults identical (PROTOCOLS-38). */
public data class ServerCapabilities(
    val promptAttachments: Boolean = true,
    val fileBrowsing: Boolean = true,
    val terminal: Boolean = true,
    val forms: Boolean = false,
    val inbox: Boolean = false,
    val promptSteering: Boolean = false,
    // … 79 more, generated once from lib/domain/server_capabilities.dart by a script and then hand-owned
) { public companion object { public val Unknown: ServerCapabilities = ServerCapabilities() } }

/** What this build can do on this platform (B-XPLAT-012/013). Bound once per app graph. */
public data class PlatformCapabilities(
    val builtinLinux: Boolean, val termuxMigration: Boolean, val voice: Boolean, val readAloud: Boolean,
    val localPdf: Boolean, val promptPhotos: Boolean, val backgroundService: Boolean, val notifications: Boolean,
    val homeWidgets: Boolean, val launchShortcuts: Boolean, val quickSettingsTiles: Boolean, val qrPairing: Boolean,
    val tailscaleHandoff: Boolean, val usbHostBridge: Boolean, val inAppUpdater: Boolean,
    val desktopReleaseCheck: Boolean, val wifiFileShare: Boolean, val appFunctions: Boolean,
)

// ---- Platform services shared by several engines or by features (ADR §4 row 27)
public interface ActivityResults {                         // impl: :android:platform over MainActivity's registry
    public suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>>
    public suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?>      // null = person cancelled
    public suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?>
    public suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?>
    public suspend fun openDocumentTree(): Outcome<TreeTarget?>
}
public enum class AppPermission { Microphone, Camera, Notifications, ExactAlarms, IgnoreBatteryOptimizations, InstallPackages }
public enum class PermissionStatus { Granted, Denied, PermanentlyDenied, NotApplicable }
public interface PermissionGate {
    public suspend fun status(permission: AppPermission): PermissionStatus
    public suspend fun request(permission: AppPermission): PermissionStatus   // never called from composition
    public fun openSettings(permission: AppPermission)
}
public enum class ForegroundKind { ReplyWatch, LiveMode, PhoneServer, Setup, WidgetAction }   // dataSync, dataSync, specialUse, specialUse, shortService
public interface ForegroundWork {
    public val active: StateFlow<ImmutableSet<ForegroundKind>>
    public suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket>   // Refused when the OS budget is spent
    public suspend fun end(ticket: ForegroundTicket)
}
public interface DeviceFacts {
    public val network: StateFlow<NetworkFact>          // kind + a change counter (evict pool, reconnect at once)
    public val thermal: StateFlow<ThermalLevel>
    public val powerSave: StateFlow<Boolean>
    public val totalRamBytes: Long                      // voice packs gate on total RAM, never memoryClass [R memory]
}
public interface ShareService {
    public fun shareText(text: String, subject: String?): Outcome<Unit>     // excludes this app from the chooser
    public fun shareFile(file: SharedFile): Outcome<Unit>
}
```

### 10.5 `:core:data`

```kotlin
package io.github.eslamasabry.oc.core.data

/** Android: the adopted FlutterSharedPreferences file (`flutter.` prefix and encodings kept, ADR §9.7 step 3).
 *  Reads suspend until MigrationGate is open, then serve the in-memory snapshot. */
public interface KeyValueStore {
    public suspend fun string(key: String): String?
    public suspend fun long(key: String): Long?             // Flutter stored every int as Long: never getInt [R network-data §7.1]
    public suspend fun bool(key: String): Boolean?
    public suspend fun double(key: String): Double?
    public suspend fun stringList(key: String): ImmutableList<String>?   // both Flutter list encodings read
    public suspend fun keys(prefix: String): ImmutableSet<String>
    /** Applied in [lane] with commit(); returns only after the platform acknowledged. A refusal reloads from disk
     *  and returns Failure.Storage; listeners never see the refused value (B-STATE-004, B-STATE-005). */
    public suspend fun edit(lane: WriterLane, block: KeyValueEditor.() -> Unit): Outcome<Unit>
    public val changes: Flow<ImmutableSet<String>>          // keys changed by acknowledged writes
}
public interface KeyValueEditor {
    public fun putString(key: String, value: String); public fun putLong(key: String, value: Long)
    public fun putBool(key: String, value: Boolean); public fun putDouble(key: String, value: Double)
    public fun putStringList(key: String, value: List<String>); public fun remove(key: String)
}

/** Android: Keystore AES-256-GCM, AAD = entry name, own prefs file (ADR §9.8). Every read/write registers with Redactor. */
public interface SecretStore {
    public suspend fun read(name: String): Outcome<String?>     // Failure.Storage on Keystore error; never "" for failure
    public suspend fun write(name: String, value: String): Outcome<Unit>
    public suspend fun delete(name: String): Outcome<Unit>
    public suspend fun names(prefix: String): Outcome<ImmutableSet<String>>
}

/** Files where they are today (ADR §9.7 "files are never moved"). */
public enum class BlobArea(public val dirName: String) {
    DraftAttachments("draft-attachments-v1"), PhotoRecovery("prompt-photo-recovery-v1"), VoiceModels("voice_models"),
    Diagnostics("diagnostics"), Projects("projects"), Linux("linux"), Native("native"), Migration("migration"),
}
public interface BlobStore {
    public fun path(area: BlobArea, relative: String = ""): kotlinx.io.files.Path
    public suspend fun writeAtomically(area: BlobArea, relative: String, bytes: ByteArray, lane: WriterLane): Outcome<Unit>
    public suspend fun read(area: BlobArea, relative: String): Outcome<ByteArray?>
    public suspend fun delete(area: BlobArea, relative: String): Outcome<Unit>
    public suspend fun sizeOf(area: BlobArea): Long
}

/** Per-profile deletion registry (AGENTS.md: keys named oc.<what>.<profileId>; ADR §9.8 tables too). */
public data class ProfileScopeRegistration(
    val owner: String,                         // module path, for the sweep receipt
    val keyShapes: List<String>,               // "oc.sessionDrafts.{profile}" — Konsist K9 checks every literal key
    val secretShapes: List<String> = emptyList(),
    val blobAreas: List<BlobArea> = emptyList(),
    val tables: List<String> = emptyList(),    // Room tables with profile_id (switch+1)
)
public interface ProfileScope {
    public val registrations: ImmutableList<ProfileScopeRegistration>   // from a Metro Set multibinding
    public suspend fun sweep(profile: ProfileId): Outcome<SweepReceipt>   // counts per owner; idempotent
}

/** Admission flag + fair Mutex: drain() returns after every admitted write finished (ADR §9.5, §17.3). */
public class WriterLane(public val name: String) {
    public suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T>
    public fun stopAdmission()
    public suspend fun drain()
}
public interface WriterLanes {
    public val app: WriterLane                             // device-wide preferences
    public fun forProfile(profile: ProfileId): WriterLane  // created on first use, closed by profile close/delete
}

public sealed interface MigrationState {
    public data class Running(val step: Int, val of: Int) : MigrationState
    public data object Open : MigrationState
    public data class Failed(val step: Int, val failure: Failure) : MigrationState
}
public interface MigrationGate {
    public val state: StateFlow<MigrationState>
    public suspend fun awaitOpen()                         // every store, receiver, worker and widget awaits this
}
```

### 10.6 `:core:db` (switch+1, or earlier by the ADR §9.8 trigger)

- One database `oc.db`, `BundledSQLiteDriver` on every target, schemas committed under `core/db/schemas/`.
- DAOs are `internal`; the module's public API is one `OcDatabase` handle that engines use to implement their own
  repository interfaces (S14). Every table has `profile_id` or is on `config/room-allowlist.txt`; `ProfileDao.deleteAll(profileId)`
  runs inside one `immediateTransaction`.
- Import from prefs records counts and a SHA-256 per blob in one transaction; source keys stay for one release.

### 10.7 `:core:net` and `:core:net-okhttp`

```kotlin
package io.github.eslamasabry.oc.core.net

public data class ProfileTransport(val origin: ServerOrigin, val trust: ProfileTrust, val auth: TransportAuth)
public sealed interface TransportAuth { public data object None : TransportAuth
    public data class Basic(val user: String, val secretName: String) : TransportAuth      // read via SecretStore at request time
    public data class Bearer(val secretName: String) : TransportAuth }

/** One client per profile, sharing one connection pool (network-data §2.2). Ktor with no ContentNegotiation,
 *  expectSuccess = false, followRedirects = false, WebSockets installed. */
public interface HttpClientFactory {
    public fun clientFor(transport: ProfileTransport): HttpClient
    public fun onNetworkChanged()             // evicts the shared pool; DeviceFacts.network drives it
}

/** Byte-level SSE parser: CRLF/LF, comments, multi-line data, 16 MiB frame cap (an oversized frame is skipped, the
 *  stream lives), flush() emits a trailing frame. Vectors: B-PROTO-033. */
public class SseParser(maxFrameBytes: Int = 16 shl 20, onFrame: (SseFrame) -> Unit) {
    public fun feed(bytes: ByteArray, offset: Int, length: Int)
    public fun flush()
}
public data class ReconnectPolicy(val firstMs: Long = 1_000, val capMs: Long = 16_000,
    val resetAfterStableMs: Long = 30_000, val disconnectedAfterTries: Int = 6) { public companion object { public val Default: ReconnectPolicy = ReconnectPolicy() } }
/** Our own loop (ADR §3.3: not Ktor's SSE reconnection). The request is rebuilt on every try (auth, after= cursor). */
public fun HttpClient.eventStream(request: () -> HttpRequestBuilder, clock: Clock,
    policy: ReconnectPolicy = ReconnectPolicy.Default): Flow<StreamSignal<SseFrame>>

/** The single transport → Failure translator (§16.1). */
public class TransportFailures @Inject constructor(private val redactor: Redactor) {
    public fun classify(error: Throwable): Failure
    public fun fromStatus(status: Int, body: String?): Failure     // 401/403 SignIn, 404/410 NotFound, 408 TimedOut, 409 Conflict, 429 Busy, ≥500 Server, else Rejected
}

/** Resumable pinned downloads: 206 append, 200 restart, 416 complete, SHA-256 mismatch deletes, atomic rename. */
public interface Downloader { public fun download(request: DownloadRequest): Flow<DownloadProgress> }
```

```kotlin
package io.github.eslamasabry.oc.core.netokhttp   // plain JVM module, no project dependencies (ADR §7.4)

public object OkHttpClients {
    /** retryOnConnectionFailure(false), no redirects, Proxy.NO_PROXY, shared pool (5 idle, 15 s keep-alive),
     *  maxRequestsPerHost = 10, and the leaf-DER pin when [pinnedLeafSha256] is set. */
    public fun hardened(pool: ConnectionPool, pinnedLeafSha256: ByteArray?): OkHttpClient
}
public class PinnedLeafTrustManager(private val pinSha256: ByteArray) : X509TrustManager   // accepts iff sha256(leaf.encoded) == pin
```

### 10.8 `:core:markdown` and `:core:testing`

- `:core:markdown`: `StreamingMarkdown` (wraps `StreamingMarkdownFile`; re-parses only the unstable tail),
  `sealed interface Block` (Prose, Heading, Quote, ListItem, Code, Table, PictureGroup, AgentBlock, Rule),
  `PathChipScanner`, `proseForSpeech(text): String`. Pure; tests from the Markdown fixtures (rendering-chat §11 item 1).
- `:core:testing` (`commonMain`, test-only by rule): `TestDispatchers(scheduler)` (every dispatcher is the test
  dispatcher, `serial()` included), `TestScheduler.asClock()`, `FakeKeyValueStore` (with a "refuse next commit" switch),
  `FakeSecretStore`, `FakeBlobStore` (in memory), `FakeServerGateway` + `FakeOperationsGateway` (record every call,
  scripted replies, scripted `events()`), `FakeForegroundWork`, `FakeDeviceFacts`,
  `WireFixtures.load("oc2/2.1.1/prompt-roundtrip.sse")`, and `catalogRow("B-STATE-042")` (reads the row's
  given/when/then for test messages). `RecordingNavigator` (an `AppNavigator` that records calls) lives in
  `:ui:testing`, because `:core:testing` may not see `:ui:routes` (§6.6).

---

## 11. Engines (public API shape)

### 11.1 The engine template (every engine follows it)

```kotlin
package io.github.eslamasabry.oc.engine.delivery

@SingleIn(ConnectionScope::class)              // or AppScope for app-wide engines
@Inject
public class PromptDelivery(
    private val gateway: ServerGateway,
    private val queue: OfflineQueueStore,      // repository interface owned by this engine (S14)
    private val receipts: CommandReceipts,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val scope: ConnectionCoroutineScope,
) {
    private val lane = dispatchers.serial("delivery")            // ADR §9.5: all state mutation on this lane

    public val state: StateFlow<DeliveryState>                  // public, immutable, stable
        field = MutableStateFlow(DeliveryState.Idle)            // Kotlin 2.4 explicit backing field

    /** Commands are suspend and return Outcome; they never throw for expected failures. */
    public suspend fun send(request: OutgoingPrompt): Outcome<SendReceipt> = withContext(lane) { /* reduce → effects */ }
    public suspend fun steer(session: SessionId, text: String): Outcome<SendReceipt> = withContext(lane) { /* … */ }
    public suspend fun stop(session: SessionId): Outcome<Unit> = withContext(lane) { /* … */ }
}

// The state machine: sealed state + pure reducer, table-tested with no coroutines (ADR §9.1).
public sealed interface TurnState { /* Idle, Sending(id), AwaitingEcho(id), Running(run), Stopping, Stalled(sinceMs), … */ }
public sealed interface TurnEvent { /* SendTapped, Accepted, EchoSeen, StreamStarted, StopTapped, Interrupted, Stalled … */ }
public sealed interface TurnEffect { /* WriteDispatchMarker(id), CallPrompt(req), Refetch(session), ScheduleStallCheck(atMs) … */ }
public data class Transition(val state: TurnState, val effects: List<TurnEffect> = emptyList())
internal fun reduce(state: TurnState, event: TurnEvent): Transition = when (state) { /* exhaustive, no else */ }
```

Rules: no Compose, no copy strings, no `android.*`; every buffered/conflated/debounced flow carries a comment with the
catalog row it implements (ADR §9.5); app-scoped engines never hold a reference to a connection-scoped object.

### 11.2 Each engine's entry points (frozen per slice in `contracts/<slice>.md`)

| Engine (scope) | Public entry points (signatures abbreviated; all commands `suspend … : Outcome<…>`) |
|---|---|
| `settings` (App) | `Preferences { appearance: StateFlow<AppearanceChoice>; motion: StateFlow<MotionLevel>; saver: StateFlow<Boolean>; locale: StateFlow<LocaleChoice>; set(change: PreferenceChange) }`, `TeachingRegistry { tips: Set<TipDefinition> (multibinding); shouldShow(id); recordShown(id) }`, `interface LocaleHost { fun apply(choice: LocaleChoice) }` (Android: `AppCompatDelegate`), `FirstRun`. |
| `diagnostics` (App) | `Diagnostics { errors: StateFlow<ImmutableList<ErrorEntry>> (20, deduped); record(...) }`, `ProblemReportBuilder.build(): ProblemReport` (128 entries / 256 KiB), `interface CrashStore`, `interface ExitHistory`, `PerfTrace { mark(name); span(name) }` (2,000 entries), `UpdateNotice { state: StateFlow<UpdateNoticeState> }`, `interface UpdateInstaller { check(): Outcome<ReleaseInfo?>; install(release, progress): Outcome<Unit> }`, `interface StorageReport`. |
| `profiles` (App) | `ProfileStore { profiles: StateFlow<ImmutableList<ServerProfile>>; upsert(profile, password?); setActive(id) }`, `ProfileDeletion.delete(id)` = run backwards: `ProfileCloser`s (Set multibinding; `:engine:connection` contributes one, so profiles never imports connection), `WriterLanes.forProfile(id).stopAdmission()+drain()`, `ProfileScope.sweep`, secrets, row. `SignInReset`. |
| `connection` (App + Connection) | `ConnectionRegistry` (App, §13.4), `ConnectionGraph` (Connection), `ConnectionSession { status: StateFlow<ConnectionStatus>; capabilities: StateFlow<ServerCapabilities>; location; reconnectNow(); close() }`, `SessionList { sessions: StateFlow<SessionListState>; refresh() }`, `Selection { model/agent/variant per session }`, `ReadState { markRead(ref, idle) }`. |
| `transcript` (Connection) | `TranscriptEngine.open(session): TranscriptHandle` (ref-counted; LRU of open transcripts), `TranscriptHandle { state: StateFlow<TranscriptState>; loadOlder(); close() }`. Publishes at most every 50 ms; first delta at once (ADR §9.3). `TranscriptSearch`. |
| `requests` (Connection) | `RequestsEngine { pending: StateFlow<ImmutableList<PendingRequest>>; answer(id, RequestAnswer) }`, `FormEngine` (one engine for OC2 forms, MCP/ACP elicitation, Codex, AskUserQuestion), `CardCodec`/`CardValidator`/`CardReceipts`. |
| `delivery` (Connection) | `PromptDelivery` (above), `OfflineQueueStore`, `CommandReceipts`; dispatch marker written before a prompt leaves (B-STATE-042). |
| `composer` (Connection) | `Drafts { draft(target): StateFlow<Draft>; onDraftChanged(target, text, selectionUtf16) (debounced save) }`, `AttachmentVault`, `Stash`, `PhotoRecovery`, `PhraseChips`, `PromptHistory`. |
| `runtime` (App) | `interface RuntimeHost` (§12), `PhoneServer { state: StateFlow<PhoneServerState>; ensureRunning(reason); stop(reason) }`, `SetupEngine`, `Healing`, `IdlePolicy`, `ThermalGuard`, `RuntimeScripts` (scripts as resources with SHA-256, generated into Kotlin by a module-local Gradle task). |
| `agents` (App) | `interface AgentHost` (§12), `AgentCatalog` (verified pins copied verbatim), `ChatFeed { items: StateFlow<ImmutableList<ChatFeedItem>> }` (merged across live connections), `Handoff`, `AutoApproval`, `AgentSignIn`. |
| `attention` (App) | `AttentionFeed { items }`, `StatusLine { current: StateFlow<StatusLineState> }` (the one combiner), `NotificationPolicy`, `interface NotificationSurface`, `interface LaunchSurfaces`, `WidgetSnapshotBuilder`. |
| `schedule` (App) | `ScheduleEngine { schedules; save(spec); delete(id); runDue(claim) }`, `Recurrence` (DST golden tests), `interface ScheduleHost { arm(id, atMs); cancel(id) }`, claims `id:revision:dueAtMs`. |
| `media` (App) | `VoiceController`, `interface AudioCapture`, `interface SpeechRecognizer`, `interface SystemTts`, `interface PcmPlayer`, `interface AudioSession`, `ModelPacks` (+ total-RAM gate), `ReadAloud`, `interface PdfPageRenderer`, `AttachmentLimits`, `SvgSanitizer`, `OfficeText`. |
| `project` (Connection) | `ProjectHub`, `FileBrowser`, `GitPage` (GitHub device flow), `ReviewModel` (diff, staged revert), `interface TerminalHost { open(target): Outcome<TerminalModel> }`, `interface TerminalModel { view: PlatformViewHandle; title: StateFlow<String>; write(bytes); resize(cols, rows); close() }`, `DevPreview`, `interface ProjectExport`. |
| `integrations` (Connection) | `Mcp`, `Providers` (never echoes keys), `Usage`, `ByoHost`, `interface ByoSigner`. |
| `entry` (App) | `Bootstrap { state: StateFlow<BootState> }`, `EntryRouter` (§15.8). |

---

## 12. Platform services: interfaces, homes and bindings

### 12.1 Binding table

Rule (ADR §7.3): an interface lives in the engine that consumes it, or in `:core:domain` when several engines or
features consume it. Android implements it in an `:android:*` module with `@ContributesBinding(AppScope::class)`;
`:desktopApp` binds the `Unavailable*` (or a real JVM implementation where marked) explicitly; tests use fakes.

| Interface | Home | Android implementation (module) | Desktop now | Capability flag |
|---|---|---|---|---|
| `KeyValueStore`, `SecretStore`, `BlobStore`, `MigrationGate` | `:core:data` | `FlutterPrefsKeyValueStore`, `KeystoreSecretStore`, `FilesBlobStore`, `AndroidMigrationGate` (`:core:data` a; gate driven by `:android:migration`) | in-memory (d) | — |
| `HttpClientFactory` | `:core:net` | `OkHttpClientFactory` (`:core:net` a → `:core:net-okhttp`) | same (d) | — |
| `GatewayFactory` (set) | `:core:domain` | `Oc2GatewayFactory`, `PaseoGatewayFactory`, `CodexGatewayFactory` (protocol modules, common) | same | — |
| `ActivityResults`, `PermissionGate` | `:core:domain` | `AndroidActivityResults`, `AndroidPermissionGate` (`:android:platform`; attached to `MainActivity`'s registry while it is resumed; calls with no activity return `Failure.Device`) | `Unavailable*` | `promptPhotos`, … |
| `ForegroundWork` | `:core:domain` | `AndroidForegroundWork` (`:android:background`) | in-process no-op that reports active = ∅ | `backgroundService` |
| `DeviceFacts` | `:core:domain` | `AndroidDeviceFacts` (`:android:platform`: `ConnectivityManager` callback, thermal listener, power-save receiver) | `JvmDeviceFacts` (static) | — |
| `ShareService` | `:core:domain` | `AndroidShareService` (`:android:platform`) | `Unavailable*` | — |
| `ExternalLinkOpener` | `:core:security` | `AndroidLinkOpener` (`:android:platform`) | `DesktopLinkOpener` (`xdg-open` via `java.awt.Desktop`, in `:desktopApp`) | — |
| `RuntimeHost` | `:engine:runtime` | `AndroidRuntimeHost` (`:android:linux`) | `UnavailableRuntimeHost` | `builtinLinux` |
| `AgentHost` | `:engine:agents` | `AndroidAgentHost` (`:android:agenthost`) | `Unavailable*` | `builtinLinux` |
| `TerminalHost` | `:engine:project` | `AndroidTerminalHost` (`:android:terminal`) | `Unavailable*` (server PTY still works through `TerminalGateway`) | — |
| `NotificationSurface`, `LaunchSurfaces` | `:engine:attention` | `AndroidNotificationSurface` (`:android:background`), `AndroidLaunchSurfaces` (`:android:widgets` + shortcuts/tiles in `:android:background`) | `Unavailable*` | `notifications`, `homeWidgets`, `launchShortcuts`, `quickSettingsTiles` |
| `ScheduleHost` | `:engine:schedule` | `AndroidScheduleHost` (`:android:schedule`: AlarmManager + WorkManager) | in-process timer (desktop phase) | — |
| `AudioCapture`, `SpeechRecognizer`, `SystemTts`, `PcmPlayer`, `AudioSession`, `PdfPageRenderer` | `:engine:media` | `:android:media` classes of the same names prefixed `Android` | `Unavailable*` | `voice`, `readAloud`, `localPdf` |
| `CrashStore`, `ExitHistory`, `UpdateInstaller`, `StorageReport` | `:engine:diagnostics` | `NativeCrashStore` (reused), `AndroidExitHistory`, `PackageInstallerUpdater`, `AndroidStorageReport` (`:android:platform`) | file-based crash store; `GitHubReleaseCheck` | `inAppUpdater`, `desktopReleaseCheck` |
| `LocaleHost` | `:engine:settings` | `AppCompatLocaleHost` (`:android:platform`) | JVM `Locale.setDefault` | — |
| `ProjectExport` | `:engine:project` | `SafProjectExport` (`:android:platform`) | `Unavailable*` | — |
| `ByoSigner` | `:engine:integrations` | `KeystoreByoSigner` (`:android:platform`) | `Unavailable*` | — |
| `PlatformCapabilities` (value) | `:core:domain` | `@Provides` in `AndroidAppGraph` (by API level) | `@Provides` in `DesktopAppGraph` (all Android-only flags false) | — |
| `KitPlatform` (composable seams) | `:ui:kit` | `AndroidKitPlatform` (`:ui:kit` a) + `AndroidKitContentSlots` (`:ui:kit-content` a) | `DesktopKitPlatform` | — |

### 12.2 `KitPlatform` (the only composable seam; features never see it)

```kotlin
package io.github.eslamasabry.oc.ui.kit.platform

public enum class KitHaptic { Send, Done, Commit, LongPress, Select, ToggleOn, ToggleOff }  // MOT-11 list (motion-native §8)

public interface KitPlatform {
    public fun haptic(kind: KitHaptic)                              // Android: LocalHapticFeedback / View constants; respects system setting
    @Composable public fun pressIndication(): Indication            // platform ripple (RippleDrawable on Android)
    @Composable public fun overscrollFactory(): OverscrollFactory?   // stretch on API 31+, glow on 26–30; null = framework default
    public val motionSignals: StateFlow<MotionSignals>              // animator scale (live ContentObserver), saver, severe heat
    @Composable public fun frameTextDensity(cap: Float = 1.3f): Density   // the 1.3× frame cap, non-linear on Android 14+ (P7)
    @Composable public fun frost(strength: FrostStrength): Modifier  // RenderEffect blur on API 31+; Modifier on solid fallback
    @Composable public fun Modifier.highFrameRate(active: Boolean): Modifier   // preferredFrameRate(High) while revealing
    @Composable public fun Modifier.imeAware(nestedScroll: Boolean): Modifier  // imePadding; + imeNestedScroll on Android (JETPACK_AHEAD)
    public fun dynamicSeed(): ColorSeed?                            // Material You seed on API 31+, else null
    public val linkHandler: UriHandler                              // ExternalLinkUriHandler → ExternalLinkPolicy → ExternalLinkOpener
    public val content: KitContentSlots                            // S11
}

/** Declared in :ui:kit, implemented in :ui:kit-content androidMain (Termux view, CameraX + zxing-cpp, hardened WebView). */
public interface KitContentSlots {
    @Composable public fun Terminal(handle: PlatformViewHandle, onKey: (KeyIntent) -> Unit, modifier: Modifier)
    @Composable public fun Scanner(onCode: (String) -> Unit, onUnavailable: () -> Unit, modifier: Modifier)
    @Composable public fun WebPreview(url: ApprovedLink, modifier: Modifier)
}

public val LocalKitPlatform: ProvidableCompositionLocal<KitPlatform> =
    staticCompositionLocalOf { error("KitPlatform is provided once at the app root") }
```

`:androidApp` builds it once: `AndroidKitPlatform(activity, linkOpener, policy, contentSlots = AndroidKitContentSlots(...))`
and provides it in `MainActivity.setContent { CompositionLocalProvider(LocalKitPlatform provides platform) { App(...) } }`.
`:desktopApp` provides `DesktopKitPlatform` the same way. Konsist K1 bans `LocalKitPlatform` and the
`…ui.kit.platform` package in features.

---

## 13. The DI graph (Metro 1.5.1)

### 13.1 Shape

```
AndroidAppGraph  @DependencyGraph(AppScope::class)            in :androidApp (final graph lives in platform code [R research §3.4])
│  extends ViewModelGraph (metrox)  → metroViewModelFactory
│  Factory.create(application, buildInfo, redactor, logRing, launchMode)   (objects made before the graph, §21.1)
│  merges every @ContributesTo / @ContributesBinding / @ContributesIntoSet / @ContributesIntoMap (AppScope) on the classpath:
│    core (dispatchers, clock, app scope, stores, Redactor, HttpClientFactory) · protocols (GatewayFactory set)
│    engines (App-scoped) · android:* (platform bindings) · ui:shell (navigator, VM factory) · features (VMs, entry installers)
│  accessors used by OS components (§13.6)
│
└── ConnectionGraph  @GraphExtension(ConnectionScope::class)   declared in :engine:connection, one per LIVE connection
       created by ConnectionRegistry.open(profile) with (profile, GatewayPair, ConnectionCoroutineScope)
       merges @ContributesTo(ConnectionScope::class) accessor interfaces from transcript, requests, delivery, composer,
       project, integrations (S4)

DesktopAppGraph  @DependencyGraph(AppScope::class)             in :desktopApp; same contributions minus :android:*,
                                                               plus explicit Unavailable* bindings (§12.1)
```

Graph rules for agents:

1. Classes use **constructor injection** (`@Inject` on the class). No field injection, no service locator calls in
   classes, no `createGraph` outside `:androidApp`/`:desktopApp`/tests of the graph itself.
2. Scope every stateful class: `@SingleIn(AppScope::class)` or `@SingleIn(ConnectionScope::class)`. Stateless helpers
   are unscoped.
3. Bind implementations with `@ContributesBinding(Scope::class)` on the implementation class. One implementation per
   interface per graph; desktop's are bound explicitly in `DesktopAppGraph`.
4. Never inject a `ConnectionScope` type into an `AppScope` class. App-scoped code asks `ConnectionRegistry`.
5. Metro's experimental features stay off (suspend providers, top-level function injection, `hiddenDependencies`)
   [R research §3.5].
6. Tests never build a graph; they construct classes with fakes. The one exception is the graph smoke test (§13.8).

### 13.2 `AndroidAppGraph` and `GraphOwner`

```kotlin
// androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidAppGraph.kt
package io.github.eslamasabry.opencode_mobile.graph

@DependencyGraph(AppScope::class)
interface AndroidAppGraph : ViewModelGraph {
    // Accessors for components the OS instantiates (minSdk 26 < 28, so no metrox-android; ADR §9.1)
    val appScope: AppCoroutineScope
    val migrationGate: MigrationGate
    val migrationRunner: MigrationRunner                        // from :android:migration (ADR §9.7 step 6)
    val prefsWarmup: PrefsWarmup                                // from :core:data androidMain (step 7)
    val activityResults: AndroidActivityResults                 // from :android:platform; MainActivity attaches itself
    val entryRouter: EntryRouter
    val shell: ShellDependencies                 // navigator, installers, settings flows the App() root needs
    val kitPlatformFactory: AndroidKitPlatform.Factory
    val phoneServerServiceBody: PhoneServerServiceBody          // from :android:linux
    val backgroundServiceBody: BackgroundConnectionServiceBody  // from :android:background
    val scheduleReceiverBody: ScheduleReceiverBody              // from :android:schedule
    val scheduledRunWorkerBody: ScheduledRunWorkerBody
    val widgetBodies: WidgetBodies                               // from :android:widgets
    val tileBodies: TileBodies
    val notificationActionBody: NotificationActionBody
    val restoreReceiverBody: RestoreReceiverBody

    /** Android has every platform feature; a few depend on the API level (B-XPLAT-012). */
    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.android(Build.VERSION.SDK_INT)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides buildInfo: AppBuildInfo,
            @Provides redactor: Redactor,        // built in onCreate step 2, before any secret is read (ADR §9.7)
            @Provides logRing: LogRing,          // the diagnostics ring the log chain already writes to (§18.2)
            @Provides launch: LaunchMode,        // Normal | SafeMode, from the crash-loop guard (step 3)
        ): AndroidAppGraph
    }
}

// androidApp — the only way OS components reach the graph
interface GraphOwner { val appGraph: AndroidAppGraph }
internal val Context.appGraph: AndroidAppGraph get() = (applicationContext as GraphOwner).appGraph
```

`OcApplication` (`io.github.eslamasabry.opencode_mobile.OcApplication`, frozen FQCN) implements `GraphOwner` and
builds the graph in step 5 of the ADR §9.7 order (full code in §21.1).

### 13.3 `DesktopAppGraph`

```kotlin
// desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/DesktopAppGraph.kt
@DependencyGraph(AppScope::class)
interface DesktopAppGraph : ViewModelGraph {
    val shell: ShellDependencies
    @Binds val UnavailableRuntimeHost.bindRuntime: RuntimeHost       // explicit Unavailable* bindings (§12.1)
    @Binds val UnavailableAgentHost.bindAgents: AgentHost
    // … one line per §12.1 row with "Unavailable*" in the Desktop column
    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.desktop()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides buildInfo: AppBuildInfo, @Provides redactor: Redactor, @Provides logRing: LogRing): DesktopAppGraph
    }
}
```

[U P1: Metro's exact `@Binds` property syntax on 1.5.1; fallback is an `@Provides` function per binding.]

### 13.4 `ConnectionGraph`, `ConnectionRegistry` and the accessor pattern (S4)

```kotlin
package io.github.eslamasabry.oc.engine.connection

@GraphExtension(ConnectionScope::class)
public interface ConnectionGraph {
    public val profile: ServerProfile
    public val session: ConnectionSession
    public val sessions: SessionList
    public val selection: Selection
    public val readState: ReadState
    public val scope: ConnectionCoroutineScope

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    public fun interface Factory {
        public fun create(
            @Provides profile: ServerProfile,
            @Provides gateways: GatewayPair,
            @Provides scope: ConnectionCoroutineScope,
        ): ConnectionGraph
    }
}

@ContributesTo(ConnectionScope::class)
public interface GatewayBindings {
    @Provides public fun live(pair: GatewayPair): ServerGateway = pair.live
    @Provides public fun operations(pair: GatewayPair): ServerOperationsGateway = pair.operations
}

@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
public class ConnectionRegistry(
    private val factories: Set<GatewayFactory>,
    private val graphFactory: ConnectionGraph.Factory,
    private val credentials: ConnectionCredentialsReader,     // reads SecretStore; registers secrets with the Redactor
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : ProfileCloser {
    init { check(factories.groupBy { it.backend }.all { it.value.size == 1 }) { "two GatewayFactory for one backend" } }

    public val live: StateFlow<ImmutableMap<ProfileId, ConnectionGraph>>
        field = MutableStateFlow(persistentMapOf())

    /** Idempotent: returns the live graph when one exists. Opens gateways, then creates the extension. */
    public suspend fun open(profile: ServerProfile): Outcome<ConnectionGraph>
    /** The conversation's own backend (Flutter ConversationBackendScope). Null = that connection is not live. */
    public fun graphFor(ref: ConversationRef): ConnectionGraph? = live.value[ref.profileId]
    /** Stop admission → cancel streams → drain writer lanes → release (ADR §9.1). */
    public suspend fun close(profile: ProfileId)
    override suspend fun closeForDeletion(profile: ProfileId): Unit = close(profile)
}
```

```kotlin
// in :engine:transcript (and the same three lines in requests, delivery, composer, project, integrations)
@ContributesTo(ConnectionScope::class)
public interface TranscriptAccessors { public val transcriptEngine: TranscriptEngine }
public val ConnectionGraph.transcript: TranscriptEngine get() = (this as TranscriptAccessors).transcriptEngine
```

The cast is safe because Metro merges every `@ContributesTo(ConnectionScope::class)` interface into the generated
extension; the graph smoke test (§13.8) asserts that the real `ConnectionGraph` implements every accessor interface,
so a missing contribution fails on the VPS, never on the phone [U P1: Metro merges contributed interfaces into
graph extensions — documented for `@GraphExtension` [V docs], to be compiled once in P1].

### 13.5 Multibindings (no central lists anywhere)

| Multibinding | Contributed by | Consumed by |
|---|---|---|
| `Set<GatewayFactory>` | `:protocol:*` (`@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())`) | `ConnectionRegistry` |
| `Set<EntryInstaller>` | every feature's `XEntries` | `:ui:shell` `NavShell` |
| `Map<KClass<out ViewModel>, () -> ViewModel>` and the two assisted maps | features (`@ViewModelKey`, `@ManualViewModelAssistedFactoryKey`) | `ShellViewModelFactory` (§14.3) |
| `Set<ProfileScopeRegistration>` | engines and `:android:*` modules that write per-profile data | `ProfileScope` |
| `Set<ProfileCloser>` | `:engine:connection`, `:engine:runtime` (phone server leases), `:engine:schedule` | `ProfileDeletion` |
| `Set<TipDefinition>` | features and engines that own a hidden gesture (owner rule "teach what is hidden") | `TeachingRegistry` |
| `Set<SettingsSearchEntry>` | settings-owning features | `:feature:search` |
| `Set<CapabilityFlow>` (the 21 enable flows, SCREENS-NAV-20) | the feature that can run the flow | kit capability explainer (via `ShellDependencies`) |
| `Set<StartupTask>` | engines that must act once the gate opens (restore phone server, re-arm alarms, warm caches) | `Bootstrap` (runs them on `appScope`, ordered by `StartupTask.order`) |

Declare an empty-allowed multibinding where zero contributions are legal (`@Multibinds(allowEmpty = true)`), in the
module that consumes it.

### 13.6 How Android components reach the graph

Every OS-visible class lives in `:androidApp` at its frozen FQCN and is a thin shell; its logic is a `…Body` class in
an `:android:*` module, obtained from the graph.

```kotlin
// :androidApp — io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt   (FQCN frozen, ADR §9.9)
class BuiltinServerService : Service() {
    private val body by lazy { appGraph.phoneServerServiceBody }
    override fun onCreate() { super.onCreate(); body.onCreate(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = body.onStartCommand(this, intent, startId)
    override fun onDestroy() { body.onDestroy(this); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

// Receivers: never block the main thread; await the migration gate inside goAsync (ADR §9.7)
class ScheduledPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.appGraph
        val pending = goAsync()
        graph.appScope.launch {
            try { graph.migrationGate.awaitOpen(); graph.scheduleReceiverBody.onReceive(intent) }
            finally { pending.finish() }
        }
    }
}

// Workers keep their class names (WorkManager stores them) and the default (Context, WorkerParameters) constructor,
// so the default WorkManager initializer keeps working and no custom WorkerFactory is needed.
class ScheduledPromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        graph.migrationGate.awaitOpen()
        return graph.scheduledRunWorkerBody.run(inputData)      // ≤ 8 min (ADR §9.5)
    }
}
```

A receiver whose work can exceed the `goAsync` window (about 10 s) enqueues a one-time WorkManager job instead of
waiting. Widget providers follow the receiver pattern; tile services call their body from `onStartListening`/`onClick`
on `appScope`.

### 13.7 ViewModels through Metro

```kotlin
// :ui:shell commonMain — one factory for both apps
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class ShellViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
```

- The root provides it once: `CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory)`.
- Plain screens: `val vm = metroViewModel<ChatsViewModel>()` (metrox-viewmodel-compose 1.5.1 [V source]).
- Route-keyed screens (S5): `assistedMetroViewModel<ChatViewModel, ChatViewModel.Factory> { create(route) }` [V source].
- Per-entry ViewModel stores come from `rememberViewModelStoreNavEntryDecorator()` (lifecycle-viewmodel-navigation3
  2.11.0), so a ViewModel dies when its entry leaves the back stack.

```kotlin
@AssistedInject
public class ChatViewModel(
    @Assisted private val route: ChatRoute,
    registry: ConnectionRegistry,
    private val nav: AppNavigator,
) : ViewModel(), ChatActions {
    /** Follows the conversation's own connection; when it closes, state says so instead of holding stale engines. */
    private val graph: StateFlow<ConnectionGraph?> = registry.live
        .map { it[route.ref.profileId] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, registry.graphFor(route.ref))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatViewModel
    }
    // …
}
```

### 13.8 Graph smoke test (`:quality`, VPS, every T2)

`:quality` depends on `:desktopApp` (and, through an Android host test in `:androidApp`, on the Android graph) and:

1. builds `DesktopAppGraph` and the Android graph (Robolectric `Application`), and resolves every accessor;
2. resolves every key of every multibinding in §13.5 and instantiates every ViewModel through the factory with fake
   route arguments;
3. opens a `ConnectionGraph` with a `FakeServerGateway` pair and casts it to every `@ContributesTo(ConnectionScope::class)`
   interface found by Konsist in `engine/*/src/commonMain`;
4. asserts `Set<GatewayFactory>` has exactly one factory per `BackendKind`.

---

## 14. State holders

### 14.1 Who owns what (ADR §9.1)

| Holder | Lives | Owns | Exposes | Never |
|---|---|---|---|---|
| Engine | `AppScope` or `ConnectionScope` | truth, timers, streams, state machines, persistence | `StateFlow<…State>` + `suspend` commands returning `Outcome` | Compose, copy strings, `android.*` |
| ViewModel | one per Nav3 entry | the mapping engine state → `XUi`, small UI-only state (filter, expanded rows) | `StateFlow<XUi>` + `XActions` methods named after intents | business rules, one-shot channels, platform types, copy |
| Screen composable | composition | nothing but `remember`ed visual state (scroll, `TextFieldState` for the composer, ADR §9.2) | — | ViewModel references below the screen; derived models built in composition (crash risk, ADR §9.6) |
| Kit part | composition | its own animation and interaction state | plain parameters + lambdas | a ViewModel, an engine, `Failure` mapping beyond `KitFailureView` |

### 14.2 The feature template, complete (`:feature:chats`)

```kotlin
// presentation/ChatsUi.kt — no androidx.compose.* imports (Konsist K2)
package io.github.eslamasabry.oc.feature.chats.presentation

public data class ChatsUi(
    val loading: Boolean = true,
    val rows: ImmutableList<ChatRowUi> = persistentListOf(),
    val filter: ChatsFilter = ChatsFilter.All,
    val needsYouCount: Int = 0,
    val failure: Failure? = null,                  // rendered by the kit's failure view; never a string
    val undo: UndoOffer? = null,                   // acknowledged state, not an event (§14.4)
)
public data class ChatRowUi(
    val ref: ConversationRef, val title: String, val project: String?, val status: RowStatus,
    val lastActivityMs: Long, val finishedUnseen: Boolean,
)
public enum class ChatsFilter { All, NeedsYou, Running }
public enum class RowStatus { NeedsYou, Running, Failed, Idle }

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    public fun open(ref: ConversationRef)
    public fun startNew()
    public fun showOnly(filter: ChatsFilter)
    public fun refresh()
    public fun undoShown(id: Long)
    public fun undo(id: Long)
}
```

```kotlin
// presentation/ChatsViewModel.kt
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
public class ChatsViewModel(
    feed: ChatFeed,                       // :engine:agents (merged across live connections)
    attention: AttentionFeed,             // :engine:attention
    private val nav: AppNavigator,        // :ui:routes
) : ViewModel(), ChatsActions {
    private val filter = MutableStateFlow(ChatsFilter.All)

    public val state: StateFlow<ChatsUi> =
        combine(feed.items, attention.items, filter, ::toChatsUi)      // pure function in ChatsMapping.kt
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUi())

    override fun open(ref: ConversationRef): Unit = nav.openChat(ref, focusComposer = false)
    override fun startNew(): Unit = nav.open(ChatsRoute.New(profile = null))
    override fun showOnly(filter: ChatsFilter) { this.filter.value = filter }
    override fun refresh() { viewModelScope.launch { /* feed.refresh() returns Outcome; failure goes into state */ } }
    override fun undoShown(id: Long) { /* clear `undo` when the kit bar has gone */ }
    override fun undo(id: Long) { /* … */ }
}
```

```kotlin
// ui/ChatsScreen.kt — arranges kit parts only (Konsist K1)
@Composable
public fun ChatsScreen(state: ChatsUi, actions: ChatsActions) {
    KitScreen(title = stringResource(Res.string.chats_title)) {
        when {
            state.loading -> KitSkeletonList()
            state.failure != null -> KitFailureView(state.failure, onRetry = actions::refresh)
            else -> LazyColumn {
                items(state.rows, key = { it.ref.toString() }, contentType = { "conversation" }) { row ->
                    KitConversationRow(
                        title = row.title, supporting = row.project, status = row.status.toKitStatus(),
                        sharedKey = row.ref,                 // the kit's grow-from-row seam (§15.6)
                        onClick = { actions.open(row.ref) },
                    )
                }
            }
        }
    }
}
```

```kotlin
// ChatsEntries.kt — the only file in the module that imports androidx.navigation3.runtime
@Inject
@ContributesIntoSet(AppScope::class)
public class ChatsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatsRoute.Home> {
            val vm = metroViewModel<ChatsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ChatsScreen(state, vm)
        }
    }
}
```

```kotlin
// ui/ChatsPreviews.kt — UI work starts the day contracts/<slice>.md freezes ChatsUi and ChatsActions
internal object FakeChatsActions : ChatsActions { /* every method empty */ }
internal object ChatsSamples {
    val loading = ChatsUi()
    val list = ChatsUi(loading = false, rows = persistentListOf(/* 3 rows: needs you, running, idle */), needsYouCount = 1)
    val failed = ChatsUi(loading = false, failure = Failure.Network(details = null))
}
@Preview @Composable private fun ChatsListPreview() = KitPreview { ChatsScreen(ChatsSamples.list, FakeChatsActions) }
```

```kotlin
// commonTest — logic only; the widget/screenshot tests come later by owner decision (ADR §4 row 18)
class ChatsViewModelTest {
    @Test
    fun `B-NAV-030 merged list sorts attention first then running then recent`() = runTest {
        withTestMain {                                   // :core:testing helper: Dispatchers.setMain/resetMain
            val vm = ChatsViewModel(FakeChatFeed(samples()), FakeAttentionFeed(), RecordingNavigator())
            vm.state.test { /* Turbine: skip initial, assert order */ }
        }
    }
}
```

### 14.3 Engine state flows into ViewModels

- `combine(...)` + `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` for screens (Google's recommendation
  [R research §8.4]). The initial value is a real `XUi` (loading), never `null`.
- Mapping runs in the `combine` lambda on `Main.immediate` only when cheap; heavy mapping (transcript blocks) happens in
  the engine on its lane and arrives ready (ADR §9.3).
- `collectAsStateWithLifecycle()` in the entry, never `collectAsState()`.

### 14.4 Events become acknowledged state

There are no `Channel`/`SharedFlow` events from ViewModel to UI. Pattern:

1. The ViewModel sets a state field with an id: `undo = UndoOffer(id = 7, target = ref)`.
2. The kit part shows it and calls `actions.undoShown(7)` when it is gone (or `actions.undo(7)`).
3. The ViewModel clears the field only if the id still matches (a newer offer is never swallowed).

Navigation is not an event: ViewModels call `AppNavigator` directly (§15.3). The same pattern is used by `EntryRouter`
(S12).

### 14.5 Saved state and process death

- The back stack is saved by `rememberNavBackStack(configuration, start)` (route keys only; §15.1 rule: ids, enums and
  booleans, nothing secret or large).
- Drafts, selection, scroll anchors that must survive process death are persisted by engines (drafts autosave; the
  Flutter app did the same), not by `SavedStateHandle` [R kmp-cmp-status §5: its KMP support is unconfirmed].
- `rememberSaveable` only for transient UI state (an expanded section).

---

## 15. Navigation graph shape

### 15.1 Route keys (`:ui:routes`)

```kotlin
package io.github.eslamasabry.oc.ui.routes

/** Every destination. Sealed, so the SerializersModule registers all of them in one line (S17). */
@Serializable
public sealed interface AppRoute : NavKey {
    /** How the destination enters and leaves; :ui:shell turns it into transitions (ADR §9.4). */
    public val motion: RouteMotion get() = RouteMotion.SharedAxisX
}
public enum class RouteMotion { Root, SharedAxisX, GrowFromRow, SlideUp }

public object RouteSaving {
    @OptIn(ExperimentalSerializationApi::class)
    public val module: SerializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclassesOfSealed<AppRoute>() }   // kotlinx.serialization ≥ 1.10 [R network-data §4.2]
    }
    public val configuration: SavedStateConfiguration = SavedStateConfiguration { serializersModule = module }
}
```

One file per feature, in the same package (sealed subclasses must share module and package; they may span files):

```kotlin
// ChatsRoutes.kt (owned by the :feature:chats agent)
@Serializable public sealed interface ChatsRoute : AppRoute {
    @Serializable public data object Home : ChatsRoute { override val motion: RouteMotion get() = RouteMotion.Root }
    @Serializable public data class All(val filter: String? = null) : ChatsRoute
    @Serializable public data class New(val profile: ProfileId?, val draftKey: String? = null) : ChatsRoute {
        override val motion: RouteMotion get() = RouteMotion.SlideUp
    }
}
// ChatRoutes.kt (owned by the chat lead)
@Serializable public data class ChatRoute(val ref: ConversationRef, val focusComposer: Boolean = false) : AppRoute {
    override val motion: RouteMotion get() = RouteMotion.GrowFromRow
}
```

Route rules (checked by `:ui:routes` tests and Konsist K8):

1. Parameters are ids (`ProfileId`, `SessionId`, `ConversationRef`, …), enums, booleans, short strings. Never a
   password, token, prompt text, file content or a whole model object (routes are written to saved state).
   Shared text (share-in) travels as a key into the composer engine's draft, not in the route.
2. Every `AppRoute` subclass round-trips through `RouteSaving.module` (a parameterised test enumerates
   `AppRoute::class.sealedSubclasses` on the JVM).
3. A test tag on a route-level element equals the Flutter `ValueKey` string where one existed (ADR §9.1).

Initial route inventory (slices 1–3 freeze theirs in slice 0; later slices add files):

| Route | Feature | Params | Motion |
|---|---|---|---|
| `OnboardingRoute.FirstRun` | onboarding | — | Root |
| `ChatsRoute.Home` / `.All` / `.New` | chats | filter / profile | Root / SharedAxisX / SlideUp |
| `ChatRoute` | chat | `ref`, `focusComposer` | GrowFromRow |
| `ChatPagesRoute.Review` / `.Context` / `.Export` / `.Notes` / `.Relations` | chat-pages | `ref` | SharedAxisX |
| `ServersRoute.List` / `.Editor` / `.Pairing` / `.Details` | servers | `profile?` | Root when no profile, else SharedAxisX; Pairing SlideUp |
| `PhoneRoute.Home` / `.Setup` / `.Linux` / `.Terminal` / `.Health` | phone | `step`, `terminal?` | SharedAxisX |
| `AgentsRoute.Hub` / `.Agent` | agents | `agentId` | SharedAxisX |
| `ProjectRoute.Files` / `.Viewer` / `.Review` / `.Git` / `.Terminal` / `.Preview` | project | `ref`, `path` | SharedAxisX |
| `SettingsRoute.Hub` / `.Group` / `.Appearance` / `.Notifications` / `.Voice` / `.Providers` / `.ManageSpace` | settings | `section?`, `profile?` | SharedAxisX |
| `UsageRoute.Hub` / `.WidgetSettings` | usage | `section?` | SharedAxisX |
| `SchedulesRoute.List` / `.Editor` / `.History` | schedules | `schedule?` | SharedAxisX |
| `AboutRoute.Report` / `.Diagnostics` / `.About` / `.Tips` / `.Updates` | about | — | SharedAxisX |
| `SearchRoute.Launcher` | search | — | SlideUp |

`:feature:widget-panels` has no routes: `WidgetOverlayActivity` hosts exactly one panel per launch.

### 15.2 `EntryInstaller`

```kotlin
/** Features contribute these with @ContributesIntoSet(AppScope::class); the shell installs them all. No central list. */
public fun interface EntryInstaller {
    public fun EntryProviderScope<NavKey>.install()    // EntryProviderScope is Nav3 1.2.0's DSL receiver [V source]
}
```

### 15.3 `AppNavigator` (the only navigation API ViewModels see)

```kotlin
/** Main thread only. Implemented by :ui:shell's ShellNavigator over the NavBackStack; tests use RecordingNavigator. */
public interface AppNavigator {
    public val top: StateFlow<AppRoute?>
    /** Push unless the same route is already on top. A Root-motion route replaces the stack. */
    public fun open(route: AppRoute)
    /** Open a conversation; never stacks a duplicate of the chat on top (Flutter c5842e836 [R]). */
    public fun openChat(ref: ConversationRef, focusComposer: Boolean = false)
    /** Clear to Home ("landing"). */
    public fun land()
    /** Replace the whole stack (e.g. Servers when no saved server exists). */
    public fun replaceRoot(root: AppRoute)
    /** Pop one entry. With one entry left it does nothing: the root never intercepts Back (ADR §9.4, D5). */
    public fun back()
}
```

`ShellNavigator` (`@SingleIn(AppScope::class) @ContributesBinding(AppScope::class)`) holds the `NavBackStack` that
`NavShell` attaches in a `DisposableEffect` and detaches on dispose. Calls made while detached (activity recreation)
are kept, latest wins, and applied on attach.

### 15.4 The shell (`:ui:shell`)

```kotlin
@Composable
public fun NavShell(start: AppRoute, deps: ShellDependencies) {
    val backStack = rememberNavBackStack(RouteSaving.configuration, start)
    DisposableEffect(backStack) { deps.navigator.attach(backStack); onDispose { deps.navigator.detach(backStack) } }
    val window = rememberWindowClass()                              // :ui:design classes: 600 / 840 / 1200, short < 480
    val provider = remember(deps.installers) {
        withRouteMotion(entryProvider { deps.installers.forEach { with(it) { install() } } })
    }
    ShellFrame(window, deps) {                                      // compact: single pane, no bottom bar; medium: rail;
        SharedTransitionLayout {                                    // expanded: sidebar ("absent, never disabled")
            NavDisplay(
                backStack = backStack,
                onBack = { deps.navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategy = ShellScenes.strategyFor(window),    // list-detail for Settings and Files on wide windows
                sharedTransitionScope = this,
                transitionSpec = { ShellMotion.forward(targetState.metadata) },
                popTransitionSpec = { ShellMotion.back(initialState.metadata) },
                predictivePopTransitionSpec = { edge -> ShellMotion.predictiveBack(initialState.metadata, edge) },
                entryProvider = provider,
            )
        }
    }
}

/** Adds each route's RouteMotion to its entry metadata, so features never write motion metadata themselves. */
internal fun withRouteMotion(base: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = base(key)
    NavEntry(key, entry.contentKey, entry.metadata + ShellMotion.metadataFor((key as AppRoute).motion)) { entry.Content() }
}
```

[U P2: the exact `NavDisplay` parameter names on CMP navigation3-ui 1.2.0 (`sceneStrategy` vs a list,
`sharedTransitionScope`, the `predictivePopTransitionSpec` lambda shape) and the public `NavEntry` constructor +
`Content()`; the shape above is from the 1.1/1.2 release notes [R kmp-cmp-status §4].]

`ShellDependencies` (`@Inject`, App-scoped) bundles what the root needs: `navigator`, `installers`,
`entryRouter`, `bootstrap`, `preferences` (theme, motion level, text scale policy), `statusLine`, `capabilityFlows`.

### 15.5 Back and motion policy, implemented once

| Case | Where | How |
|---|---|---|
| Root | shell | no back handler; with one entry Nav3 lets the system take Back (predictive back-to-home) |
| List → chat (GrowFromRow) | shell `ShellMotion` + kit `Modifier.growFromRow(key)` | option A: `sharedBounds` on tap and button-back; off while a gesture is in progress (the kit reads `KitRouteTransition.gestureInProgress`, which the shell provides per entry); option B as a `JETPACK_AHEAD` row in `:ui:shell` `androidMain` |
| Chat with unsaved composer | kit `KitBackGuard(enabled)` used by the chat screen | wraps `NavigationBackHandler`; enabled only while the composer differs from the saved draft |
| Settings pages | shell | shared-axis X with system predictive back |
| New conversation, search launcher | shell | slide up; the gesture closes |
| Sheets and dialogs | kit `KitSheetHost` | screen state, not routes; M3 1.5 in-window `BottomSheet` handles predictive back |
| Reduced motion (system scale 0, user Off/Calm, Saver, severe heat) | kit `MotionPolicy` + shell | cross-fade (Calm) or none (Off) |

Features never import `navigationevent`, `SharedTransitionScope` or `AnimatedContentScope` (Konsist K1); they pass a
`sharedKey` to kit parts.

### 15.6 Kit ↔ shell transition seam

```kotlin
// :ui:kit commonMain
public class KitRouteTransition(
    public val sharedScope: SharedTransitionScope,
    public val contentScope: AnimatedContentScope,
    public val gestureInProgress: State<Boolean>,
)
public val LocalKitRouteTransition: ProvidableCompositionLocal<KitRouteTransition?> = compositionLocalOf { null }
// Kit parts that grow from a row (KitConversationRow, KitScreen) read it and apply sharedBounds only when it is
// non-null, the motion level is Full, and no back gesture is in progress.
```

`:ui:shell` provides `LocalKitRouteTransition` per entry from a `NavEntryDecorator` (it has the `AnimatedContentScope` of
the entry and the navigation-event transition state).

### 15.7 Sheets, dialogs and menus

Screen state, not routes (ADR §9.4). A feature puts `sheet: XSheet?` in its `XUi`; the screen renders
`KitSheetHost(visible = state.sheet != null, onDismiss = actions::closeSheet) { … }`. A sheet becomes a route only when
a deep link must open it or it must survive process death alone; that needs the coordinator's approval in the slice
contract.

### 15.8 `EntryRouter` (`:engine:entry`) and the shell mapping

```kotlin
package io.github.eslamasabry.oc.engine.entry

/** What the Android layer hands over, already whitelisted and bounded (SCREENS-NAV §4: extras stripped from the Intent). */
public sealed interface EntryRequest {
    public data class Shortcut(val action: LaunchAction, val profile: ProfileId?, val session: SessionId?) : EntryRequest
    public data class Link(val raw: String) : EntryRequest                // opencode-mobile://session|team, ≤ 1024 chars, 0x21..0x7e
    public data class ShareText(val text: String) : EntryRequest
    public data class AlertTap(val kind: AlertKind, val ref: ConversationRef?, val monitorToken: String?, val reply: Boolean) : EntryRequest
    public data class ScheduledRunTap(val schedule: ScheduleId, val fingerprint: String) : EntryRequest
    public data class WidgetTap(val action: WidgetTapAction) : EntryRequest
    public data class TileTap(val tile: TileKind) : EntryRequest
}

/** Neutral destinations; :ui:shell maps them to routes (ADR §4 row 26). */
public sealed interface EntryTarget {
    public data class OpenChat(val ref: ConversationRef, val focusComposer: Boolean, val draftKey: String? = null) : EntryTarget
    public data class OpenChats(val needsYou: Boolean) : EntryTarget
    public data class NewConversation(val profile: ProfileId, val draftKey: String?) : EntryTarget
    public data class OpenServers(val notice: EntryNotice?) : EntryTarget
    public data class OpenSettings(val section: SettingsSection) : EntryTarget
    public data class OpenUsage(val section: UsageSection?) : EntryTarget
    public data class OpenPhone(val page: PhonePage) : EntryTarget
    public data class OpenScheduleHistory(val schedule: ScheduleId) : EntryTarget
}

public data class PendingEntry(val id: Long, val target: EntryTarget?, val waiting: EntryWait?)

@SingleIn(AppScope::class) @Inject
public class EntryRouter(/* Bootstrap, MigrationGate, ConnectionRegistry, ProfileStore, ScheduleEngine, AppDispatchers */) {
    /** Called by MainActivity (onCreate/onNewIntent) and receivers. Held until bootstrap + gate; re-evaluated on every
     *  connection change: waiting keeps it (status line "waiting for server"), final conditions resolve it with a notice. */
    public fun submit(request: EntryRequest)
    public val pending: StateFlow<PendingEntry?>
    /** Exactly once (B-NAV-005). A request that arrived during handling stays pending. */
    public fun consume(id: Long)
}
```

The shell collects `pending`, maps the target with one exhaustive `when` in `EntryTargetRouting.kt`, calls
`AppNavigator`, then `consume(id)`. This one mechanism replaces the seven Flutter copies (SCREENS-NAV §4). Nav3's own
deep-link matcher is not used (ADR §4 row 12).

---

## 16. Errors and results

### 16.1 Where translation happens (once, at the boundary; ADR §9.6)

| Boundary | Translator (file) | Turns | Into |
|---|---|---|---|
| `:core:net` | `TransportFailures` | `UnknownHostException`, `ConnectException`, `SocketTimeoutException`, SSL/pin failures, Ktor timeouts, HTTP status | `Network`, `TimedOut`, `Certificate`, `SignIn`, `NotFound`, `Conflict`, `Busy`, `Server`, `Rejected` |
| `:protocol:oc2` | `Oc2Failures` | server `_tag` (`SessionNotFoundError`, `SessionBusyError`, `FormAlreadySettledError`, `ConflictError`, `WorktreeError`, …, protocol notes §12) and decode failures | `NotFound`, `Busy`, `Refused(AlreadyAnswered)`, `Conflict`, `ProjectGit`, `UnreadableReply` |
| `:protocol:paseo`, `:protocol:codex` | `PaseoFailures`, `CodexFailures` | daemon/RPC errors, version mismatch | `ComputerDidNotAnswer`, `UnreadableReply`, `SignIn` |
| `:core:data` | `StorageFailures` | `commit() == false`, `IOException`, `KeyStoreException`, `AEADBadTagException` | `Storage` (and the "enter the password again" flag for secrets, ADR §9.7) |
| `:android:*` | `<Module>Failures` | proot exit codes, `ErrnoException`, `SecurityException`, `ActivityNotFoundException`, CameraX/AudioRecord errors | `Device`, `Storage`, `Folder(...)`, `Termux` |
| engines | none | engines pass `Failure`s through or create `Refused(reason)` / `Unavailable(feature)` | — |

Model and provider failures inside a session (`execution.failed`, `step.failed`, `tool.failed`, `retry.scheduled`)
are **not** `Failure`s; they are transcript parts rendered by `:ui:kit-chat` (PROTOCOLS-36).

Every translator builds `details` with `Redactor.details(...)`, and `details` is never empty: a failed step without a
message says its type and HTTP status (`Redacted.shape`) (PROTOCOLS-36).

### 16.2 Failure → plain words (`:ui:words`)

```kotlin
package io.github.eslamasabry.oc.ui.words

public data class FailureCopy(val title: StringResource, val wayForward: StringResource?, val canRetry: Boolean)

/** Exhaustive: adding a Failure subtype or a RefusalReason fails compilation here until the copy exists. */
public fun Failure.copy(): FailureCopy = when (this) {
    is Failure.Refused -> reason.copy()
    is Failure.Unavailable -> FailureCopy(Res.string.failure_unavailable, null, canRetry = false)
    is Failure.Network -> FailureCopy(Res.string.failure_network, Res.string.failure_network_next, canRetry = true)
    is Failure.TimedOut -> FailureCopy(Res.string.failure_timed_out, Res.string.failure_timed_out_next, canRetry = true)
    is Failure.Certificate -> FailureCopy(Res.string.failure_certificate, Res.string.failure_certificate_next, canRetry = false)
    is Failure.SignIn -> FailureCopy(Res.string.failure_sign_in, Res.string.failure_sign_in_next, canRetry = false)
    // … every subtype; no `else` branch (Konsist K20 checks `when` over Failure has no else in :ui:words)
}
```

The kit renders it: `KitFailureView(failure, onRetry, onReport)` shows the title and way forward in plain words,
Retry when `canRetry`, and a **Details** fold with `failure.details?.text` (left-to-right isolated, copyable) next to
"Report a problem". No other code reads `Redacted.text` (Konsist K14), so there is no path from server or exception
text to a visible label (B-NAV-009, owner rule "no raw errors").

### 16.3 Crashes

1. `OcApplication.onCreate` step 1 installs the uncaught-exception handler: it writes a category-only record to
   `NativeCrashStore` (reused Kotlin; records never contain provider keys or arbitrary exception values, B-SEC-043),
   then defers to the previous handler.
2. Every root scope (`AppCoroutineScope`, `ConnectionCoroutineScope`, service body scopes) has a `SupervisorJob` and a
   `CoroutineExceptionHandler` that logs at Error; one failed child never takes its siblings down.
3. An exception inside composition kills the process (Compose has no error widget), so `XUi` and kit models are built
   in ViewModels and engines, never computed in composition from raw data.
4. The crash-loop guard (three crashes within 10 s of start, `files/native/crashloop.json`) opens safe mode: Report a
   problem, Settings, Start fresh (ADR §9.7 step 3).

---

## 17. Coroutines, scopes and dispatchers

### 17.1 Scopes

| Scope | Created by | Job | Runs on | Ends when |
|---|---|---|---|---|
| `AppCoroutineScope` | `CommonBindings` at graph build | `SupervisorJob()` | `default` | process death |
| `ConnectionCoroutineScope` | `ConnectionRegistry.open` | `SupervisorJob(appJob)` | `default` | `close()`: stop admission → cancel streams → drain lanes → cancel |
| generation scope | `ConnectionSession` on each (re)connect or location change | `Job(connectionJob)` | the engine's lane | the next generation starts (replaces generation counters, ADR §9.5) |
| engine lane | `dispatchers.serial("<engine>")` | — | one task at a time | — |
| `viewModelScope` | ViewModel | `SupervisorJob` | `Main.immediate` | the Nav3 entry leaves the stack |
| service body scope | `…ServiceBody.onCreate` | `SupervisorJob(appJob)` | `default` | `onDestroy` |
| receiver / worker | `goAsync()` + `appScope.launch` / `CoroutineWorker` | child of app scope / WorkManager's | `default` | `finish()` / `doWork` returns |

### 17.2 Dispatcher rules

- State mutation happens only on the owning engine's serial lane (`withContext(lane) { … }` at each public command).
- Heavy pure work (decode a 100-message page, merge 5,000 parts, parse Markdown) runs in `withContext(dispatchers.default)`
  and returns a value to the lane.
- Blocking I/O runs in `dispatchers.io`. `runBlocking` and `GlobalScope` do not exist outside tests (Konsist K16).
- The UI thread runs composition, the navigator and ViewModel glue only.

### 17.3 `WriterLane` (complete; ADR §9.5, research §8.3)

```kotlin
public class WriterLane(public val name: String) {
    private val admitting = AtomicBoolean(true)            // kotlin.concurrent.atomics (opt-in, §6.4)
    private val mutex = Mutex()                            // fair FIFO, like Dart's chained futures

    public suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> {
        if (!admitting.load()) return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        return mutex.withLock { block() }                  // admitted before stopAdmission → still runs
    }
    public fun stopAdmission() { admitting.store(false) }  // synchronous, the first step of close/delete
    public suspend fun drain() { mutex.withLock { } }      // returns when every admitted write has finished
}
```

A serial dispatcher alone is not enough: a write that suspends on disk I/O frees the dispatcher, and a close could
run in the middle. The mutex makes "drain" mean "every admitted write finished" (B-STATE-002, B-STATE-013, B-CHAT-051).

### 17.4 Flow rules

| Purpose | Type | Rule |
|---|---|---|
| Current state | `StateFlow<T>` of an immutable data class | equality must be correct (data classes, `ImmutableList`) |
| Server events | cold `Flow<StreamSignal<ServerEvent>>`, collected by one engine per connection | durable events: unbounded/suspending buffer, never dropped; volatile deltas may be coalesced; after every `Connected`, reconcile by refetch, never replay |
| UI state | `combine(…).stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | initial is a real loading `XUi` |
| One-consumer facts (reply finished → notification) | `SharedFlow(replay = 0)` with an explicit buffer and overflow | only between engines; never to UI |
| Transcript publishing | engine-side `StateFlow<TranscriptState>` updated at most every 50 ms; first delta at once | ADR §9.3; the publish-count test ports `perf_chat_test.dart` |

Every `buffer`, `conflate`, `debounce`, `sample` carries a comment naming the catalog row it implements (ADR §9.5).

### 17.5 Cancellation rules

1. Catch `CancellationException` only to rethrow it. Use `attempt {}`; never `runCatching` around suspend calls (K3).
2. `withContext(NonCancellable)` only to finish an admitted write or record a final receipt; each use names its
   catalog row in a comment, and its writes name the profile and generation they belong to.
3. After an `await`, re-check identity only when writing into a store that outlives the generation (app-scoped caches,
   prefs); otherwise the cancelled generation simply never resumes (prompt cancellation guarantee).
4. Do not pass `Job()` to builders (`launch(Job())`); coroutines 1.11 lints it. Use the scope tree.

### 17.6 Tests

`runTest` with `StandardTestDispatcher`; `TestDispatchers(testScheduler)` and `testScheduler.asClock()` from
`:core:testing`; `backgroundScope` for collectors that never end; virtual time for 50 ms / 5 s / 15 min / 60 min rules;
Turbine `test {}` for flows; `withTestMain {}` for ViewModels (it calls `Dispatchers.setMain`/`resetMain`, which K4
allows in `:core:testing` and test source sets only).

---

## 18. Logging, diagnostics and tracing

### 18.1 `AppLog` (`:core:common`; Kermit 2.2.0 underneath, never imported elsewhere)

```kotlin
public enum class Severity { Debug, Info, Warn, Error }
/** A thrown error is logged by shape only: class name + up to 12 frames "Class.method:line". Never its message. */
public data class ErrorShape(val type: String, val frames: List<String>)
public data class LogEntry(val atMs: Long, val severity: Severity, val tag: String, val message: String, val error: ErrorShape?)
public interface LogSink { public fun write(entry: LogEntry) }
/** Marker for the root sink; only :core:security's RedactingWriter implements it. */
public interface RedactingLogSink : LogSink

public object AppLog {
    /** Once, in OcApplication.onCreate before any secret is read (ADR §9.7 step 2). Entries logged earlier wait in a
     *  64-entry in-memory buffer and are flushed THROUGH [root] here (S13); nothing is ever written unredacted. */
    public fun install(root: RedactingLogSink, minSeverity: Severity)
    public fun tagged(tag: String): Log
}
public class Log internal constructor(public val tag: String) {
    public fun d(message: () -> String)
    public fun i(message: () -> String)
    public fun w(error: Throwable? = null, message: () -> String)
    public fun e(error: Throwable? = null, message: () -> String)
}

// usage, one per class:
private val log = AppLog.tagged("Delivery")
log.i { "queued prompt ${id.raw} for ${profile.raw}" }          // ids are fine; prompt text is not
```

### 18.2 The writer chain

```
AppLog ─► Kermit Logger (min Info in release, Debug in debuggable builds)
        └► RedactingWriter (:core:security; Redactor.redact on message and frames)
             ├► LogRing (:engine:diagnostics; 128 entries / 262,144 bytes — the Flutter report ring [R]; in memory only)
             └► LogcatSink (:core:common androidMain; debuggable builds only — the owner's phone has logcat silenced anyway)
```

The ring is written to disk only when the person exports "Report a problem".

### 18.3 Diagnostics sizes (ported numbers [R lib/diagnostics])

| Store | Size | Notes |
|---|---|---|
| Error list (`Diagnostics.errors`) | 20 entries, repeats folded | message capped at 2,000 chars, stack at 12,000, both redacted |
| Report ring (`LogRing`) | 128 entries / 256 KiB | newest kept |
| `PerfTrace` | 2,000 marks | `OCTRACE <ms> name key=value parent=…` text format kept so the existing analysis scripts still parse it |
| Crash record | 8,192 bytes | category-only (B-SEC-043) |
| Failed-job report | 120 lines / 16,384 chars | setup and runtime jobs |

### 18.4 Never log

Provider credentials (`/config/providers` returns API keys), passwords, tokens, cookies, `Authorization` headers,
full URLs with query strings, prompt text, file contents, clipboard contents. Enforcement:

- K5 bans `android.util.Log`, `println`, `printStackTrace`, OkHttp `HttpLoggingInterceptor` and Ktor's `Logging`
  plugin everywhere.
- `MigrationLogScrubTest` (`:android:migration`) runs every migration path with planted secrets and asserts none of
  them is in the ring (ADR §9.7, state-data §3.3).
- `RedactorTest` vectors port `KitRedact` and the redaction-profile tests (B-SEC rows).

### 18.5 Tracing

```kotlin
// :core:common
public interface Trace {
    public fun <T> section(name: String, block: () -> T): T
    public fun mark(name: String, attributes: Map<String, String> = emptyMap())
}
public expect fun platformTrace(): Trace       // Android: androidx.tracing 2.0.5 sections (Perfetto); desktop: no-op
```

`PerfTrace` (`:engine:diagnostics`) records the Flutter marks with the same names: `app.main`, `app.first_frame`,
`app.first_connected`, `events.connected`, `events.first`, `events.first.session_log`, `messages.page`,
`prompt.admitted`, and HTTP spans ≥ 50 ms as `http oc2 METHOD /path-template` (PROTOCOLS-45). Engines never read traces
or logs: diagnostics are not product state.

---

## 19. Code style and lint

### 19.1 Formatting

- ktfmt 0.64, `--kotlinlang-style`, run by `tool/format-changed.sh` on files changed since the merge base:
  `git diff --name-only --diff-filter=AM "$(git merge-base HEAD "$BASE")" -- '*.kt' '*.kts'`.
- The jar is the Maven Central `com.facebook:ktfmt:0.64` `jar-with-dependencies` artifact, cached under
  `~/.cache/oc-tools/`, verified by a SHA-256 written into the script.
- T2 runs the same script with `--dry-run --set-exit-if-changed`. Agents never format files they did not change
  (memory `agent-format-churn`).

### 19.2 Compiler and Compose settings

- `explicitApi()` per §6.4; `allWarningsAsErrors` at T2 (`-Poc.werror=true`); opt-ins listed in §6.4 only.
- `config/compose-stability.conf` (every `oc.kmp.compose` module):

```
// Types from non-Compose modules that are immutable by construction (ADR §9.3).
io.github.eslamasabry.oc.core.common.**
io.github.eslamasabry.oc.core.model.**
io.github.eslamasabry.oc.core.domain.**
io.github.eslamasabry.oc.engine.**.state.**
io.github.eslamasabry.oc.ui.design.**
kotlinx.collections.immutable.*
```

`core.common.**` is added to the ADR's list because the ids, `ConversationRef` and `Failure` that every `XUi` carries
live there. P4 measures recompositions with and without the file (ADR §11).

### 19.3 detekt

`tool/qa/detekt.sh` runs the detekt 1.23.8 CLI (no type resolution, as in the Flutter repo) with `config/detekt.yml`:
`complexity` (LongMethod 80, CyclomaticComplexMethod 15, `@Composable` functions excluded from LongParameterList),
`exceptions` (`TooGenericExceptionCaught` on, except `attempt` and `*Failures.kt`; `SwallowedException` on),
`style.ForbiddenComment` (a `TODO` must name an owner and a slice: `TODO(slice-3, chat-lead)`), `MaxLineLength` 120.
Formatting rules are off (ktfmt owns formatting). [U P-03-2: detekt 1.23.8 embeds an older Kotlin parser; if it
rejects Kotlin 2.4 syntax such as explicit backing fields, slice 0 either moves to detekt 2.0 or drops detekt — the
Konsist rules and the compiler carry every rule this plan depends on.]

### 19.4 Konsist rules (`:quality`, Konsist 0.18.1) — the rewrite's architecture tests

K1–K12 are the ADR §6.3 rules; K13–K20 are added here.

| # | Rule | Scope |
|---|---|---|
| K1 | **Kit only**: feature files import only the allowlist below; `*Entries.kt` alone may import `androidx.navigation3.runtime`; `*Previews.kt` alone may import `@Preview` | `feature/**` |
| K2 | `presentation` packages import no `androidx.compose.*` and no `…ui.words.res` (no copy in ViewModels); ViewModel constructors take no `android.*` types | `feature/**` |
| K3 | no `runCatching` in a file that declares or calls a `suspend` function | production code |
| K4 | no `Dispatchers.` and no `Clock.System` outside `:core:common` (and `:core:testing`, test source sets) | production code |
| K5 | no `android.util.Log`, `println`, `printStackTrace`, `HttpLoggingInterceptor`, Ktor `Logging` | everywhere |
| K6 | no `openUri`, `Intent.ACTION_VIEW`, `startActivity(Intent(ACTION_VIEW…` outside `ExternalLinkUriHandler` and `:android:platform`'s `AndroidLinkOpener` | everywhere |
| K7 | `getSharedPreferences(` only in `:core:data` `androidMain` and `:android:migration`; copied Kotlin that owns a named prefs file opens it through `AndroidNamedPrefs.open(context, fileName)` from `:core:data` | everywhere |
| K8 | every `@Serializable` class implementing `NavKey` implements `AppRoute` (so S17 registers it) | `ui/routes` |
| K9 | every string literal shaped `"oc.<what>.$…"` matches a `keyShapes` entry of some `ProfileScopeRegistration` | production code |
| K10 | `src/androidMain` and `src/desktopMain` exist only in the §6.4 `hasPlatformSourceSets` modules | all KMP modules |
| K11 | no `String.format`, `System.`, `Thread.`, `java.`, `javax.` in `commonMain` | KMP modules |
| K12 | test function names start with a catalog id `B-[A-Z0-9]+-\d{3}` (infra tests in `:quality` and `build-logic` exempt) | `commonTest`, `test`, `androidHostTest` |
| K13 | `@OptIn(RedactorApi::class)` only in `:core:security` and `Redacted.kt` | everywhere |
| K14 | `Redacted.text` is read only by the kit Details fold, `ProblemReportBuilder` and `RedactingWriter` | everywhere |
| K15 | no `.message`, `.localizedMessage`, `.stackTraceToString()` on a `Throwable` outside `*Failures.kt` translators and `:core:common`'s `AppLog` | production code |
| K16 | no `GlobalScope`, `runBlocking`, `launch(Job())` | production code |
| K17 | in features and `:ui:shell` `commonMain`: no `Color(`, `0xFF…` literals, numeric `.sp`, numeric `.dp` other than `0.dp`, `RoundedCornerShape`, `tween(`, `spring(`, `keyframes(`, `animate…AsState`, `Modifier.clickable`, `combinedClickable`, `Dialog(`, `Popup(`, `performHapticFeedback` (sizes, colours, shapes, motion and press come from kit tokens and parts) | `feature/**`, `ui/shell` |
| K18 | `androidx.compose.material3` imports only in `:ui:kit*`; `androidx.compose.material3.adaptive` also in `:ui:shell` | everywhere |
| K19 | a feature never imports another feature's package | `feature/**` |
| K20 | a `when` over `Failure` or `RefusalReason` in `:ui:words` has no `else` branch | `ui/words` |

K1 in full (the kit-only rule; the allowlist is kit-v2 §9 made concrete, ADR §6.3):

```kotlin
package io.github.eslamasabry.oc.quality

class KitOnlyRuleTest {
    private val allowed = listOf(
        // layout, scroll, lazy lists, state — arranging, not drawing
        "androidx.compose.runtime.",
        "androidx.compose.foundation.layout.",
        "androidx.compose.foundation.lazy.",
        "androidx.compose.foundation.rememberScrollState", "androidx.compose.foundation.ScrollState",
        "androidx.compose.foundation.verticalScroll", "androidx.compose.foundation.horizontalScroll",
        "androidx.compose.foundation.text.input.TextFieldState",        // text state holder for kit fields (ADR §9.2)
        "androidx.compose.foundation.text.input.rememberTextFieldState",
        "androidx.compose.ui.Modifier", "androidx.compose.ui.Alignment",
        "androidx.compose.ui.unit.", "androidx.compose.ui.semantics.", "androidx.compose.ui.focus.",
        "androidx.compose.ui.input.key.",
        "androidx.compose.ui.platform.LocalLayoutDirection", "androidx.compose.ui.platform.LocalDensity",
        "androidx.compose.ui.platform.LocalFocusManager", "androidx.compose.ui.platform.testTag",
        // ViewModels, DI, copy
        "androidx.lifecycle.", "dev.zacsweers.metro.", "dev.zacsweers.metrox.viewmodel.",
        "org.jetbrains.compose.resources.stringResource", "org.jetbrains.compose.resources.pluralStringResource",
        // our layers that features may see (ADR §6.1)
        "io.github.eslamasabry.oc.ui.kit.", "io.github.eslamasabry.oc.ui.kitchat.", "io.github.eslamasabry.oc.ui.kitcontent.",
        "io.github.eslamasabry.oc.ui.design.", "io.github.eslamasabry.oc.ui.words.", "io.github.eslamasabry.oc.ui.routes.",
        "io.github.eslamasabry.oc.engine.", "io.github.eslamasabry.oc.core.domain.", "io.github.eslamasabry.oc.core.model.",
        "io.github.eslamasabry.oc.core.common.",
        "kotlinx.coroutines.flow.", "kotlinx.coroutines.launch", "kotlinx.coroutines.CoroutineScope",
        "kotlinx.collections.immutable.", "kotlinx.serialization.", "kotlin.",
    )
    private val onlyInEntries = listOf("androidx.navigation3.runtime.")
    private val onlyInPreviews = listOf("androidx.compose.ui.tooling.preview.")
    private val bannedInside = listOf(
        "io.github.eslamasabry.oc.ui.kit.platform.",     // KitPlatform / LocalKitPlatform: kit-internal seam
        "io.github.eslamasabry.oc.core.common.AppDispatchers",
    )

    @Test
    fun `K1 features arrange kit parts only`() {
        Konsist.scopeFromDirectory("feature").files.assertTrue { file ->
            val own = file.packagee?.name?.split('.')?.take(6)?.joinToString(".")   // io.github.eslamasabry.oc.feature.<name>
            file.imports.all { imp ->
                val n = imp.name
                when {
                    bannedInside.any { n.startsWith(it) } -> false
                    onlyInEntries.any { n.startsWith(it) } -> file.name.endsWith("Entries")
                    onlyInPreviews.any { n.startsWith(it) } -> file.name.endsWith("Previews")
                    own != null && n.startsWith(own) -> true
                    else -> allowed.any { n.startsWith(it) }
                }
            }
        }
    }
}
```

[U P-03-6 / ADR P9: Konsist's `files`, `imports`, `packagee` names on 0.18.1 and whether `scopeFromDirectory("feature")`
sees `src/commonMain` in this layout; P9 plants `material3`, `LocalContext` and a raw `clickable` in a feature and the
rule must fail on each.]

### 19.5 Android lint

`:androidApp:lintStableRelease` and each `:android:*` module's `lintDebug` at T2, `abortOnError = true`, no baseline.
`MissingTranslation` runs on the generated `strings_os` resources (ADR §7.5). `NewApi` guards minSdk 26.

### 19.6 Other gates in `:quality` (T2)

| Gate | What it checks |
|---|---|
| `ModuleOwnersTest` | every Gradle project has a `MODULE_OWNERS` row and every row names a project (§2.4) |
| `ComponentFreezeTest` | merged-manifest component FQCNs, WorkManager worker class names, channel ids, shortcut ids and the `opencode-mobile://` scheme equal the list extracted from the Flutter 2.1.0+57 APK (ADR §9.9). Until a component's slice lands, it is listed in `config/freeze-pending.txt`, which may only shrink |
| `CatalogRatchetTest` | reads JUnit XML from the T2 run, counts passing tests per catalog id; the number of covered ids may only go up (`config/catalog-coverage.txt`) |
| `KitCapacityTest` | every public `@Composable fun Kit…` in `:ui:kit*` has a hard max-width row in `config/kit-capacity.json` (owner rule "component max width") |
| `NoRedTest` (in `:ui:design`) | no colour role in any style or pack falls in the red hue band, except the owner-approved Signal lamps/Stop/alert ink (ui-kit map R2) |
| `StringDriftTest` (in `:tools:strings`) | every key exists in all 7 languages with matching format args and plurals; `strings_<feature>.xml` names match feature modules |
| `RoomSchemaTest` (once `:core:db` exists) | every table has `profile_id` or is on `config/room-allowlist.txt` |
| `ThirdPartyHashTest` | `third_party/**` files match `third_party/SHA256SUMS` |
| `GraphSmokeTest` | §13.8 |

### 19.7 The rewrite's `native/AGENTS.md` rules (copy verbatim)

```
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
```

---

## 20. How things plug in (recipes)

### 20.1 A new feature (no central file is edited)

1. Read your slice's `contracts/<slice>.md`: `XUi`, `XActions`, routes, catalog rows, and what the slice deletes.
2. Create the folders:
   `mkdir -p native/feature/<name>/src/{commonMain,commonTest}/kotlin/io/github/eslamasabry/oc/feature/<name>/`
   and inside `commonMain/…/<name>/` the packages `presentation/` and `ui/`.
3. Write `native/feature/<name>/build.gradle.kts` from §7.2, listing only the engines you read.
4. Check `MODULE_OWNERS` has your row (the slice brief adds it; only the coordinator edits the file).
5. Add your routes file `native/ui/routes/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/routes/<Name>Routes.kt`
   (a sealed interface extending `AppRoute`; ids-only parameters).
6. Add your copy: `native/ui/words/src/commonMain/composeResources/values/strings_<name>.xml` and the six other
   `values-<lang>/strings_<name>.xml` files (keys `<name>_…`; the l10n owner's rule decides untranslated text).
7. Tests first: turn each catalog row into a failing `commonTest` (`B-…` name) against the mapping functions and the
   ViewModel with fakes from `:core:testing`.
8. Write `presentation/<Name>Ui.kt`, `<Name>Mapping.kt`, `<Name>ViewModel.kt`; then `ui/<Name>Screen.kt` from kit parts;
   then `ui/<Name>Previews.kt` (canned `XUi` + `Fake<Name>Actions`); then `<Name>Entries.kt`.
9. T0: `./gradlew :feature:<name>:desktopTest :feature:<name>:compileKotlinDesktop :quality:test --tests "*KitOnlyRuleTest*"`.
10. Format your files (`tool/format-changed.sh`), commit `[skip ci]`, hand over to the coordinator.

The feature appears in the app because Metro collects your `@ContributesIntoSet` entry installer and
`@ContributesIntoMap` ViewModel when `:androidApp` (which depends on every module) builds its graph.
`:androidApp/build.gradle.kts` lists features with `implementation(project(":feature:<name>"))` — that line is the
coordinator's, added at merge.

### 20.2 A new engine

1. The coordinator places it in the ADR §6.2 order (edit both the ADR and `ModuleRules.engineOrder`) and adds the
   `MODULE_OWNERS` row.
2. `plugins { id("oc.kmp.library") }`; public API in the root package, state in `.state`, the rest `internal`.
3. Platform needs become an interface in this engine (or `:core:domain` if shared) with an `Unavailable…` binding
   for desktop (§12.1).
4. Register per-profile data: `@ContributesTo(AppScope::class) interface XScopeKeys { @Provides @IntoSet fun keys(): ProfileScopeRegistration = … }`.
5. Connection-scoped? Add the accessor interface + extension property (§13.4).

### 20.3 A new backend (protocol)

1. `BackendKind` gets a value (`:core:model`, core owner).
2. New `:protocol:<x>` module; implement `GatewayFactory` with `@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())`.
3. Implement `ServerGateway` facets it supports; inherit every `ServerOperationsGateway` default for the rest.
4. Compute `ServerCapabilities` from the probed version.
5. Write `<X>Failures` (§16.1) and contract tests replaying recorded traffic through `ktor-client-mock`.
   No feature changes: features gate on capabilities.

### 20.4 A new OS component (service, receiver, widget, tile, activity)

1. Logic goes in an `:android:*` module as a `…Body` class (`@Inject`, `@SingleIn(AppScope::class)`).
2. The coordinator adds the accessor to `AndroidAppGraph` and the thin shell class + manifest entry in `:androidApp`.
3. An existing component keeps its exact FQCN, exported flag and intent filters (ADR §9.9); a new one is appended to
   the freeze list. Nothing is ever renamed.

### 20.5 A new kit part

Kit owners only: `Kit…` name, plain parameters, a row in `config/kit-capacity.json`, previews in light/dark × en/ar,
Roborazzi goldens recorded on the VPS, motion through `KitMotion` tokens, haptics through `KitHaptic`, and any Android-
only API behind `KitPlatform` with a `JETPACK_AHEAD.md` row if it is ahead of CMP.

### 20.6 A new string

Add the key to your own `strings_<feature>.xml` in all seven `values*/` folders (never to another feature's file;
`strings_common.xml` belongs to the l10n owner). Read it with `stringResource(Res.string.<key>)` in composables and
`getString(Res.string.<key>)` (suspend) in notifications, widgets and services (ADR §7.5). Copy that an OS surface
reads from XML (app label, shortcut labels, widget descriptions, channel names, tile labels) goes in `strings_os.xml`,
which `:androidApp` copies into generated Android `res`.

### 20.7 Definition of done (every slice, every agent)

- [ ] Every catalog row in the slice contract has a passing test named after it; the ratchet went up.
- [ ] `XUi`/`XActions` match the contract; previews exist for loading, content, failure and empty.
- [ ] No module rule, Konsist rule or detekt finding; no new `@Suppress`.
- [ ] Copy in all seven languages; Arabic checked in RTL; frame text capped at 1.3×; reading text scales fully.
- [ ] Per-profile data registered with `ProfileScope`; deletion test covers it.
- [ ] Failures map to plain words; Details fold shows redacted text only.
- [ ] Motion only from kit tokens; reduced motion respected; hidden gestures have a tip.
- [ ] Desktop compiles (`compileKotlinDesktop`); the Android behaviour is what is judged.
- [ ] What the slice deletes (from the brief) is deleted.
- [ ] Only changed files formatted; commits carry `[skip ci]`.

---

## 21. The application, desktop, quality and tools modules

### 21.1 `:androidApp`

```
androidApp/
├── build.gradle.kts                    plugins { id("oc.android.application") } + one implementation(project(…)) per shipped module
├── key.properties                      gitignored; same format as today's android/key.properties (never committed, D2)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml         every component at its frozen FQCN, exported flags and intent filters (android-platform map §3)
    │   ├── kotlin/io/github/eslamasabry/opencode_mobile/
    │   │   ├── OcApplication.kt  MainActivity.kt  ManageSpaceActivity.kt
    │   │   ├── WidgetConfigureActivity.kt  WidgetRouteActivity.kt  WidgetOverlayActivity.kt
    │   │   ├── BackgroundConnectionService.kt  BuiltinServerService.kt  SetupService.kt  LocalPdfService.kt
    │   │   ├── AttentionTileService.kt  PauseTileService.kt
    │   │   ├── SessionsWidgetProvider.kt  SessionsWideWidgetProvider.kt  SessionsListWidgetProvider.kt
    │   │   ├── SessionsStripWidgetProvider.kt  UsageWidgetProvider.kt  UsageWideWidgetProvider.kt  StatusWidgetProvider.kt
    │   │   ├── WidgetActionReceiver.kt  CodingActionReceiver.kt  StatusActionReceiver.kt  LivePauseReceiver.kt
    │   │   ├── ScheduledPromptReceiver.kt  BuiltinRestoreReceiver.kt  BuiltinIdleReceiver.kt
    │   │   ├── ScheduledPromptWorker.kt  WidgetStatusWorker.kt          (class names stored by WorkManager)
    │   │   ├── TermuxResultService.kt                                    (only while D7 keeps Termux migrate-out)
    │   │   └── graph/  AndroidAppGraph.kt  GraphOwner.kt  AppBuildInfoFactory.kt
    │   ├── keepRules/  app.keep  workmanager.keep  sherpa.keep           (AGP 9.3 keepRules dir, ADR §9.9)
    │   └── res/        launcher + notification icons, widget layouts and provider XML, previewImage PNGs,
    │                   xml/shortcuts.xml, xml/network_security_config.xml (same file and comment as today),
    │                   xml/locales_config.xml; generated values*/strings_os.xml (task copyOsStrings from :ui:words)
    ├── preview/res/    xml/shortcuts.xml targeting …opencode_mobile.native; label "OpenCode Native"
    ├── test/           Robolectric: Android graph smoke, manifest/freeze helpers
    └── androidTest/    upgrade-journey helpers, migration with the real Keystore (ADR §10.2)
```

`OcAndroidApplicationPlugin` essentials:

```kotlin
extensions.configure<ApplicationExtension> {
    namespace = Namespaces.APP
    compileSdk = Sdk.COMPILE
    defaultConfig {
        applicationId = Namespaces.APP                      // io.github.eslamasabry.opencode_mobile (ADR §9.9)
        minSdk = Sdk.MIN; targetSdk = Sdk.TARGET
        versionCode = versionProps.code                     // version.properties; ≥ 10000, never goes back (D3)
        versionName = versionProps.name
        manifestPlaceholders["appLabel"] = "@string/app_label"          // same placeholders as the Flutter build [R]
        manifestPlaceholders["appShortcuts"] = "@xml/shortcuts"
    }
    flavorDimensions += "channel"
    productFlavors {
        create("stable") { dimension = "channel" }                      // S16 (the ADR's "main")
        create("preview") {
            dimension = "channel"
            applicationIdSuffix = ".native"                             // side-by-side preview, own data, no migration
            manifestPlaceholders["appLabel"] = "@string/app_label_preview"
        }
    }
    buildTypes {
        getByName("release") { optimization { enable = true } }        // R8 + resources; rules in src/main/keepRules
        create("benchmark") {
            initWith(getByName("release")); matchingFallbacks += "release"
            applicationIdSuffix = ".bench"; signingConfig = signingConfigs.getByName("debug")
        }
    }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = true } }  // one versionCode
    packaging { jniLibs { useLegacyPackaging = true } }                 // proot executables must be extracted
    buildFeatures { buildConfig = true; compose = true }
    lint { abortOnError = true }
}
```

The release signing config reads `androidApp/key.properties` when present; without it `validateSigning…Release` fails,
which still proves Kotlin compiles (as AGENTS.md says today). Both signer lineages stay exactly as they are (ADR D2).

`OcApplication` — the ADR §9.7 order, in code:

```kotlin
class OcApplication : Application(), GraphOwner {
    override lateinit var appGraph: AndroidAppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val crashes = NativeCrashStore(this)                       // 1. reused class; category-only records, consent epoch kept
        CrashHandler.install(crashes)
        if (!isMainProcess()) return                               //    the isolated :local_pdf process stops here (no graph,
                                                                   //    no migration, no secrets)
        val redactor = Redactor()                                  // 2. before any secret is read
        val logRing = LogRing(maxEntries = 128, maxBytes = 262_144)
        AppLog.install(RedactingWriter(redactor, listOfNotNull(logRing, LogcatSink.ifDebuggable(this))), Severity.Info)
        val launch = CrashLoopGuard(filesDir).onLaunch()           // 3. three crashes within 10 s → LaunchMode.SafeMode
        FlutterBackup.runOnceIfNeeded(this)                        // 4. step 0, synchronous, first native launch only (:android:migration)
        appGraph = createGraphFactory<AndroidAppGraph.Factory>()   // 5. stores are lazy and await MigrationGate
            .create(this, AppBuildInfoFactory.from(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.FLAVOR),
                redactor, logRing, launch)
        appGraph.migrationRunner.start()                           // 6. async on IO; gate opens at once if native.migration.v1
        appGraph.prefsWarmup.start()                               // 7. warm the prefs file on IO
    }
}
```

`onCreate` budget: under 50 ms on the fast path (ADR §9.7); step 0 is timed in P5. Homes of the helpers:
`NativeCrashStore`, `CrashHandler`, `CrashLoopGuard`, `IntentIngress` → `:android:platform`; `FlutterBackup`,
`MigrationRunner` → `:android:migration`; `LogcatSink` → `:core:common` `androidMain`; `LogRing` → `:engine:diagnostics`;
`isMainProcess()` → `:androidApp` (compares `Application.getProcessName()` on API 28+, `/proc/self/cmdline` below).

`MainActivity` (`ComponentActivity`, frozen FQCN, `singleTop`, same intent filters):

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = appGraph
        graph.entryRouter.submit(IntentIngress.take(intent))       // :android:platform: whitelist, bound, strip extras (no replay)
        graph.activityResults.attach(this)                          // AndroidActivityResults uses this activity's registry
        val platform = graph.kitPlatformFactory.create(this)
        setContent { App(graph.shell, platform, graph.metroViewModelFactory) }   // App() is :ui:shell commonMain
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appGraph.entryRouter.submit(IntentIngress.take(intent))
    }
}
```

```kotlin
// :ui:shell commonMain — the root both apps call
@Composable
public fun App(deps: ShellDependencies, platform: KitPlatform, viewModels: MetroViewModelFactory) {
    CompositionLocalProvider(LocalKitPlatform provides platform, LocalMetroViewModelFactory provides viewModels) {
        KitTheme(deps.preferences) {
            val boot by deps.bootstrap.state.collectAsStateWithLifecycle()
            when (val b = boot) {
                is BootState.Opening -> ShellOpening(b)          // "Updating your app…" / "Opening…"; Try again after 8 s
                is BootState.SafeMode -> ShellSafeMode(deps)     // Report a problem, Settings, Start fresh
                is BootState.Ready -> NavShell(start = b.root, deps = deps)
            }
        }
    }
}
```

### 21.2 `:desktopApp`

Slice 0: a window that proves the desktop target runs; slice 1 onward: the kit gallery (every kit part with fake data,
light/dark, LTR/RTL, text scale), used by agents as an emulator-free look loop. The desktop product shell arrives only
in the desktop phase (ADR §1).

```kotlin
fun main() = application {
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(AppBuildInfo.desktop(), Redactor(), LogRing())
    Window(onCloseRequest = ::exitApplication, title = "OpenCode kit gallery") {
        CompositionLocalProvider(LocalKitPlatform provides DesktopKitPlatform()) { KitGallery() }   // gallery/ package here
    }
}
```

Compose Hot Reload (`org.jetbrains.compose.hot-reload` 1.2.0) may be applied here for a trial; it is never a required
step (ADR §4 row 16).

### 21.3 `:quality`

`plugins { id("oc.jvm.library") }`; `testImplementation(libs.konsist)`, `testImplementation(project(":desktopApp"))`
for the desktop graph smoke. Holds the Konsist rules (§19.4) and the gates of §19.6. Run with
`./gradlew :quality:test` (T0 for rule work, always in T2). The Android graph smoke lives in `:androidApp/src/test`
(it needs Robolectric's `Application`).

### 21.4 `:tools:strings`

A JVM CLI (`application` plugin, main `io.github.eslamasabry.oc.tools.strings.MainKt`):

- `./gradlew :tools:strings:run --args="convert --arb ../lib/l10n --out ui/words/src/commonMain/composeResources --map config/strings-feature-map.txt"`:
  the one-off ARB → CMP XML conversion (ICU plurals → `<plurals>`, `{name}` → `%1$s`, the 6 ICU `select` messages
  reported for hand-porting into code, the 1,018 orphan Arabic keys dropped; ADR §7.5). `config/strings-feature-map.txt`
  maps ARB key prefixes to `strings_<feature>.xml`.
- `./gradlew :tools:strings:run --args="check"`: the drift check behind `StringDriftTest`.

After the conversion commit the ARB files are frozen with Flutter; the XML is the only source.

---

## 22. Slice 0: the skeleton, the spikes, the freeze

### 22.1 Mainline modules at the end of slice 0 (ADR §5.2 "Starts 0")

| Module | Files that must exist | Tests that must pass |
|---|---|---|
| (root) | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `version.properties` (`3.0.0-dev`, `10000`), catalog, wrapper (9.7.1 + SHA-256), `AGENTS.md`, `MODULE_OWNERS`, `JETPACK_AHEAD.md` (empty table), `config/{compose-stability.conf, detekt.yml, kit-capacity.json, freeze-pending.txt, catalog-coverage.txt}`, `tool/format-changed.sh`, `tool/qa/detekt.sh`, `tool/native/kotlin-drift.sh` | — |
| `build-logic` | all seven plugins, `ModuleRules.kt`, `ExternalRules.kt`, `EnforceRules.kt` | `ModuleRulesTest` (one allowed + one denied vector per §6.1 row) |
| `:core:common` | §10.1 + §18 types, `actual`s for android and desktop | `attempt` rethrows cancellation; `AppLog` pre-install buffer is flushed through the redacting root; `WireJson` decode errors never contain the input |
| `:core:security` | §10.2 | `Redactor` vectors (B-SEC rows), `ExternalLinkPolicy` vectors, `AddressRules` vectors incl. `100.64.0.0/10` and `*.ts.net` |
| `:core:model` | the types slices 1–3 use (§10.3) | serialization round-trips |
| `:core:domain` | §10.4 with full method lists for the facets slices 1–3 use; all 85 `ServerCapabilities` flags | defaults equal the Dart defaults (generated vector file) |
| `:core:testing` | `TestDispatchers`, `asClock()`, `FakeServerGateway`, `FakeOperationsGateway`, `WireFixtures`, `catalogRow()` | self-tests of the fakes |
| `:ui:routes` | `AppRoute`, `RouteMotion`, `RouteSaving`, `EntryInstaller`, `AppNavigator`, route files for slices 1–3 | every route round-trips; route parameters are ids/enums/booleans only |
| `:android:migration` | the P5 spike: `FlutterBackup` (step 0), `SecureStorageV11Reader` (decrypt one entry), `FlutterPrefsReader` | P5 on the emulator (ADR §11) |
| `:androidApp` | `OcApplication` (steps 1–7, later steps stubbed), `MainActivity` showing a plain "Opening…" placeholder, manifest with only the components already built, graph | `assembleStableRelease` and `assemblePreviewRelease` compile; `ComponentFreezeTest` passes with `freeze-pending.txt` listing every component not yet built |
| `:desktopApp` | `main()` with an empty gallery window | `:desktopApp:run` opens (manual, PC) |
| `:quality` | §19.4 rules, §19.6 gates (those whose inputs exist) | all green; `KitOnlyRuleTest` passes vacuously on mainline |

### 22.2 Proof branches (never merged; findings move into slice-1 briefs)

Each proof runs on its own branch from the slice-0 mainline. A proof branch may add `"spike"` to `layerFolders` in
`settings.gradle.kts` and create `native/spike/<name>` modules; the branch is never merged. What a proof learns is
written to `native/docs/spikes/P<N>.md` on mainline, and reusable code is re-created in its real module by the slice
that starts that module (ADR "slice briefs name what they delete").

| Branch | Temporary modules | ADR proof | Also settles (this section) |
|---|---|---|---|
| `spike/p1-skeleton` | `spike/graph` (a `ConnectionGraph` extension, two ViewModels, two entry installers, the shell's `NavShell`) | P1 | P-03-1, P-03-3, P-03-5 |
| `spike/p2-predictive-back` | `spike/nav` (list + chat screens with `sharedBounds`) | P2 | the §15.4 `NavDisplay` parameter names |
| `spike/p3-transcript` | `spike/transcript` + `:android:benchmark` | P3, P4 | — |
| `spike/p5-identity` | stub classes for every frozen component in `:androidApp` | P5 | — |
| `spike/p6-strings` | `spike/words` + an early `:tools:strings` | P6 | — |
| `spike/p7-kit-seams` | `spike/kit` (KitTappable, KitFrameText, KitSheetHost, frost) + an early `:ui:testing` | P7 | P-03-4 |
| `spike/p9-allowlist` | `feature/spike` with planted `material3`, `LocalContext`, raw `clickable`; `spike/terminal` rendering a Termux `TerminalView` through `PlatformViewHandle` | P9 | P-03-6 |
| (no branch) | measurements on mainline + `spike/p1` | P8, P10 | P-03-2 (detekt) |

### 22.3 This section's own unknowns

| # | Question | Settled by | Fallback |
|---|---|---|---|
| P-03-1 | The AGP-KMP `android {}` accessor from a binary convention plugin (`extensions.configure<KotlinMultiplatformAndroidLibraryTarget>`), `withHostTest` and its task name (`testAndroidHostTest`) on AGP 9.3.3 | P1 | configure the block in each module's build script through a shared `fun KotlinMultiplatformExtension.ocAndroid(path)` helper |
| P-03-2 | detekt 1.23.8 parses Kotlin 2.4 syntax (explicit backing fields, context parameters) | half a day in slice 0 | detekt 2.0 alpha on the VPS only, or no detekt (Konsist + compiler carry the rules) |
| P-03-3 | Metro 1.5.1: `@Provides` parameters on `@GraphExtension.Factory`; contributed interfaces merged into graph extensions; `@Binds` extension-property syntax; `ContributesIntoSet(binding = binding<…>())`; plus Kotlin accepting `@RequiresOptIn` on a value-class constructor | P1 | `@Provides` functions on a contributed interface instead of factory parameters; an internal `RedactedFactory` object instead of the opt-in constructor |
| P-03-4 | Roborazzi record/verify task names for AGP-KMP host tests and for desktop | P7 | record through Robolectric only (ADR P7 fallback) |
| P-03-5 | `subclassesOfSealed<AppRoute>()` registers nested sealed interfaces declared across files; wrapping `NavEntry` in `withRouteMotion` keeps decorators and ViewModel stores working | P1 | an explicit `polymorphic { subclass(…) }` block plus Konsist K8 as the ADR wrote it; per-entry `metadata =` in installers |
| P-03-6 | Konsist 0.18.1 sees `src/commonMain` of nested KMP modules via `scopeFromDirectory`, and its `imports`/`packagee` API | P9 | a plain-text import scanner in `:quality` (regex over `^import `) |
| P-03-7 | `limitedParallelism(1, name)` and `kotlin.concurrent.atomics` need no extra opt-in on coroutines 1.11 / Kotlin 2.4.21 | P1 compile | add the opt-in marker to §6.4's list |

### 22.4 What freezes at the end of slice 0 (ADR §10.3), and where it lives

| Frozen item | File(s) |
|---|---|
| `:core:model` types | `core/model/src/commonMain/**` |
| `Outcome`, `Failure` subtypes, `RefusalReason` values | `core/common/.../Outcome.kt`, `Failure.kt` |
| Gateway interfaces and `ServerCapabilities` | `core/domain/src/commonMain/**` |
| Store interfaces | `core/data` interfaces (§10.5) — written in slice 0 inside `contracts/slice-1.md`, created with the module in slice 1 |
| `AppDispatchers`, `Clock` | `core/common/.../Dispatchers.kt`, `CommonBindings.kt` |
| Route keys for slices 1–3 | `ui/routes/src/commonMain/**` |
| `KitPlatform`, `KitContentSlots` and the first 20 kit part signatures | `contracts/slice-1.md` (from `docs/ux-system/kit-api/*.md`), created with `:ui:kit` in slice 1 |
| Module rules and engine order | `build-logic/.../ModuleRules.kt` (mirrors ADR §6.1/§6.2) |

---

## 23. Acceptance checks for this section

The coordinator runs these on the slice-0 mainline (VPS unless noted) before any slice-1 agent starts. Each must give
the stated result.

| # | Command | Expected |
|---|---|---|
| 1 | `cd native && ./gradlew -p build-logic test` | `ModuleRulesTest` passes |
| 2 | `./gradlew projects` | the project list equals the `MODULE_OWNERS` rows; `ModuleOwnersTest` agrees |
| 3 | add `implementation(project(":core:net"))` to `:core:domain`, run `./gradlew help` | configuration fails with "Module rule: :core:domain may not depend on :core:net (ADR §6.1/§6.2)…"; revert |
| 4 | add `implementation(libs.compose.material3)` to `:ui:routes`, run `./gradlew help` | fails with the `ExternalRules` message; revert |
| 5 | `./gradlew :core:common:desktopTest :core:common:testAndroidHostTest :core:security:desktopTest :core:domain:desktopTest :ui:routes:desktopTest` | green; test names carry catalog ids |
| 6 | `./gradlew -Poc.ios=true :core:common:compileKotlinIosArm64 :core:model:compileKotlinIosArm64 :core:domain:compileKotlinIosArm64` | green (nightly job wiring proven) |
| 7 | `./gradlew :androidApp:assemblePreviewRelease` (debug-key signed for the check) | an APK with package `io.github.eslamasabry.opencode_mobile.native`, `versionCode` ≥ 10000 (`aapt2 dump badging`) |
| 8 | `./gradlew :androidApp:assembleStableRelease` without `key.properties` | fails only at `validateSigningStableRelease` |
| 9 | `./gradlew :quality:test` | green, including `ComponentFreezeTest` (with the pending list) and `ThirdPartyHashTest` |
| 10 | `tool/format-changed.sh --check` on the slice-0 commits | no changes needed |
| 11 | `tool/qa/detekt.sh` | green, or P-03-2's fallback recorded |
| 12 | PC, one Gradle build: `./gradlew :desktopApp:run` | an empty gallery window opens |
| 13 | proof branches P1–P9 | each meets its ADR §11 acceptance; findings in `native/docs/spikes/P<N>.md` |
| 14 | P8 numbers | clean and incremental times recorded for PC and VPS; the per-change vs nightly desktop compile rule (ADR §4 row 17) written into `native/AGENTS.md` |

---

## 24. Owner decision raised here

| # | Decision | Recommended default | What changes if you say no |
|---|---|---|---|
| D03-1 | Where the native code lives: a `native/` folder in the existing `oc_app` repository, beside the frozen Flutter app (removed at switch+1). | **Yes, `native/` in `oc_app`**: one history, the copied Kotlin keeps its blame, agents keep the same worktree habit, CI and docs stay in one place. | A new repository (`oc-native`) with the same tree at its root; every path in this section loses the `native/` prefix; the Flutter-Kotlin copy and drift script work across two repositories. |

Every other choice here is technical and reversible inside slice 0 (§0.2, §22.3).

---

## 25. Sources

- Binding decision: `/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md` (§1–§12).
- Research: `../research/architecture-stacks.md` (§3 DI, §4 state, §5 navigation, §7 errors, §8 concurrency, §9 logging,
  §12 sketches), `../research/kmp-cmp-status.md` (§4, §5, §6, §8, §11, §12), `../research/network-data.md` (§0, §2.2,
  §3.3, §4.3, §6.2, §7.2), `../research/testing-perf.md` (§0, §3.2, §5.4, §9.4, §10.1, §11), `../research/motion-native.md`
  (§8, §9.1, §10, §11), `../research/rendering-chat.md` (§0, §11), `../research/genui.md` (§0).
- Maps: `../map/protocols.md` (PROTOCOLS-36–46), `../map/state-data.md` (§1, §14), `../map/screens-nav.md` (§2, §4),
  `../map/android-platform.md` (§3, §16), `../map/builtin-linux.md` (§12, §13), `../map/ui-kit.md` (§0, §1),
  `../map/media-voice.md` (§13.1), `../map/desktop-web-cross.md` (§9.3), `../map/packages-build.md` (§11, §12).
- Catalog rows cited: B-STATE-002, -004, -005, -013, -042; B-PROTO-033; B-SEC-043; B-NAV-005, -009, -030;
  B-CHAT-051; B-XPLAT-012, -013 (`../catalog/BEHAVIORS.md`).
- Flutter source read (read-only): `lib/domain/server_gateway/interfaces.dart`, `lib/domain/server_gateway/value_types_core.dart`,
  `lib/domain/product_failure.dart`, `lib/platform/platform_capabilities.dart`, `lib/diagnostics/{app_diagnostics,
  crash_diagnostics,report_problem,perf_trace,failed_job_report}.dart`, `android/app/build.gradle.kts` (168 Kotlin
  files in one package; 26 reference `R`; Termux JitPack exclusive repository; `useLegacyPackaging`).
- Demo: `/home/eslam/Storage/Code/kmp-demo` (`settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`,
  `shared/build.gradle.kts`, `androidApp/build.gradle.kts`).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `pc-capacity-ceiling`, `agent-format-churn`,
  `codex-backend-claude-frontend`, `no-raw-errors-rule`, `design-standard-rule`, `component-max-width-rule`,
  `text-scale-cap`, `android-native-feel`, `no-glass-rule`, `phone-logcat-silenced`, `voice-model-memory-gate`.
- Checked today [V], 2026-11-01:
  - Maven Central / Google Maven metadata and POMs for every catalog coordinate in §5 (e.g. `dev.zacsweers.metro:runtime`
    and `metrox-viewmodel-compose` 1.5.1; `androidx.room3:room3-runtime` 3.0.3 (latest 3.1.0-alpha03);
    `androidx.navigation3:navigation3-runtime` 1.2.0 with `-desktop` variants on Google Maven; CMP `ui-test-junit4`
    1.13.0-beta01 exists, CMP `ui-test-manifest` does not; `com.squareup.okhttp3:mockwebserver3` 5.5.1; Metro Gradle
    plugin marker → `dev.zacsweers.metro:gradle-plugin`; ktfmt 0.64; foojay resolver 1.0.0; androidx.test.ext:junit
    1.3.0; androidx.test:runner 1.7.0).
  - Metro sources (`metrox-viewmodel-compose-1.5.1-sources.jar`, `metrox-viewmodel-1.5.1-sources.jar`): `metroViewModel`,
    `assistedMetroViewModel`, `LocalMetroViewModelFactory`, `ViewModelGraph.metroViewModelFactory`,
    `MetroViewModelFactory` provider maps, `@ViewModelKey` (implicit class key), `@ManualViewModelAssistedFactoryKey`,
    `ManualViewModelAssistedFactory`; Metro runtime jar contains `dev.zacsweers.metro.AppScope`.
  - Metro docs: https://zacsweers.github.io/metro/latest/dependency-graphs/ , /aggregation/ , /installation/ (plugin id
    `dev.zacsweers.metro`), /metrox-viewmodel/.
  - Navigation 3 source, `navigation3-runtime/src/commonMain/kotlin/androidx/navigation3/runtime/EntryProvider.kt`
    (androidx-main): `public class EntryProviderScope<T : Any>`, `entry<K>(clazzContentKey, metadata, content)`.
  - Android KMP library plugin page: https://developer.android.com/kotlin/multiplatform/plugin (`android {}` replaces
    `androidLibrary {}` from AGP 8.12; `withHostTest { isIncludeAndroidResources }`, `withDeviceTest`,
    `androidResources { enable = true }`, `optimization { consumerKeepRules … publish = true }`).
  - Konsist scope API: https://docs.konsist.lemonappdev.com/writing-tests/koscope (`scopeFromDirectory`,
    `scopeFromProject(moduleName, sourceSetName)`).
