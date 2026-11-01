# Slice 0 · P1: the skeleton

Date: 2026-11-01. Branch: `native/p1-skeleton-2026-11-01` (from `claude/native-p1-skeleton-7l4drp` at `becde5fb`).
Inputs: `docs/design/native-rewrite-2026-11-01/` — `DRAFT-STATUS.md`, `plan/01-architecture-decision.md` (ADR, binding,
§11 P1), `plan/03-architecture-modules.md`, and `audit/codex-01-architecture-decision+03-architecture-modules.md`.

**Result: P1's acceptance is met on this container for Android and desktop:**
- the app assembles;
- the Metro graph is checked at compile time on Android, and both at compile time and at run time on desktop;
- the `ConnectionScope` extension opens with accessors contributed from another module;
- Nav3 per-entry ViewModels (plain and route-keyed) render and navigate in a headless desktop UI test;
- the Konsist rules and module rules pass, and they fail when a violation is planted.

**Not done:** the iOS klib job is written but was not run, as the brief said. Emulator checks are also not done; there
is no emulator in this container.

Shipping state: implemented, verified locally, committed and pushed to the branch. It has no PR, no CI run, no
signing and no release.

---

## 1. What was built

All new code is under `native/`. That is the Gradle root, following plan 03 S1 / D03-1.

### 1.1 Root and build logic

| Path | Content |
|---|---|
| `settings.gradle.kts` | Module discovery from layer folders (S2), the foojay toolchain resolver, and `FAIL_ON_PROJECT_REPOS`. |
| `build.gradle.kts` | The plugins slice 0 uses, applied with `apply false`. |
| `gradle.properties` | The PC caps from plan 03 §4.1: `-Xmx2g`, 2 workers, configuration cache and build cache on, `oc.ios=false`. |
| `version.properties` | `3.0.0-dev` / `10000` (ADR §9.9). |
| `gradle/libs.versions.toml` | The ADR §8 pins. |
| Gradle wrapper | 9.7.1, with `distributionSha256Sum` `acd53f1e…d20a`. |
| `build-logic/convention` | The seven plugins: `oc.kmp.library`, `oc.kmp.compose`, `oc.kmp.feature`, `oc.jvm.library`, `oc.android.library`, `oc.android.application`, `oc.desktop.application`. |
| Rule files in `build-logic` | `ModuleRules.kt` (ADR §6.1/§6.2), `ExternalRules.kt` and `EnforceRules.kt`. |
| `ModuleRulesTest` | 19 vectors: one allowed and one denied edge per §6.1 row, plus the audit edges. |
| `MODULE_OWNERS` | One row per module. |
| `AGENTS.md` | Plan 03 §19.7 verbatim, plus toolchain notes. |
| `JETPACK_AHEAD.md` | An empty table. |
| `config/compose-stability.conf` | The stability configuration (see §5, audit #12). |
| `tool/qa/ios-klib-nightly.sh` | The nightly iOS klib job. |
| `tool/qa/measure-build.sh` | The P8 probe. |

### 1.2 Modules (16 Gradle projects plus the included `build-logic`)

| Module | Plugin | What is in it |
|---|---|---|
| `:core:common` | kmp.library (c, a, d, ios) | See below. |
| `:core:security` | kmp.library | See below. |
| `:core:model` | kmp.library | `BackendKind`, `ServerOrigin`, `ProfileTrust`, `ServerProfile` (skeleton shape), `StreamStatus`, `Session`. |
| `:core:domain` | kmp.library | `ServerGateway` (health and sessions facets), `ServerOperationsGateway` (defaults return `Unavailable`), `GatewayFactory`, `GatewayPair`, `ServerCapabilities` (a subset), `PlatformCapabilities`. |
| `:core:testing` | kmp.library (test only) | `TestDispatchers`, `FakeServerGateway`, `FakeOperationsGateway`, `FakeGatewayFactory`. |
| `:engine:connection` | kmp.library | `ConnectionGraph` `@GraphExtension(ConnectionScope::class)` with a contributed factory; `GatewayBindings`; `ConnectionSession` (connection-scoped); `ConnectionRegistry` (open is idempotent; close cancels and joins the scope, then closes the gateway). |
| `:engine:transcript` | kmp.library | A P1 placeholder `TranscriptEngine` (connection-scoped) plus `TranscriptAccessors` and the `ConnectionGraph.transcript` extension. It proves plan 03 S4 across modules. |
| `:ui:design` | kmp.library (no Compose) | The placeholder palette (ARGB), spacing and motion tokens. |
| `:ui:words` | kmp.compose (resources) | `values/strings_chats.xml` and `values-ar/strings_chats.xml`. `Res` is in `io.github.eslamasabry.oc.ui.words.res`. |
| `:ui:routes` | kmp.library | `AppRoute : NavKey` (sealed), `RouteMotion`, `RouteSaving` (`subclassesOfSealed<AppRoute>()`), `EntryInstaller`, `AppNavigator`, `ChatsRoutes.kt`, `ChatRoutes.kt`. |
| `:ui:kit` | kmp.compose (c, a, d) | `KitPlatform` + `LocalKitPlatform`; `AndroidKitPlatform` (haptics through the `View`); `DesktopKitPlatform`; `KitTheme` (Material 3); placeholder `KitScreen`, `KitText`, `KitButton`. |
| `:ui:shell` | kmp.compose (c) | `App()`; `NavShell`: Nav3 `NavDisplay` with `rememberNavBackStack(RouteSaving.configuration, start)` and the decorators `rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`. Also `ShellNavigator` (`AppNavigator`; attach/detach with pending calls), `ShellDependencies`, and `ShellViewModelFactory : MetroViewModelFactory`. |
| `:feature:chats` | kmp.feature | `ChatsViewModel` (`@ViewModelKey`, `metroViewModel()`); `ChatsAllViewModel` (`@AssistedInject` with a `ManualViewModelAssistedFactory` taking `ChatsRoute.All`, `assistedMetroViewModel()`); kit-only screens; `ChatsEntries` (`@ContributesIntoSet` `EntryInstaller`). |
| `:androidApp` | android.application | `OcApplication` (Redactor → `AppLog.install` → `createGraphFactory<AndroidAppGraph.Factory>()`); `MainActivity` (`ComponentActivity`, edge-to-edge, `setContent { App(…) }`); `AndroidAppGraph : ViewModelGraph` + `GraphOwner`; the manifest with only the built components; a preview label. |
| `:desktopApp` | desktop.application | `DesktopAppGraph : ViewModelGraph`; `main()` opens a window running the same `App()`. |
| `:quality` | jvm.library (+ Metro, + Compose compiler) | The Konsist rules, `KitOnly` + `KitOnlyPlantedTest`, `ModuleOwnersTest`, `GraphSmokeTest`, `DesktopShellUiTest`. |

What `:core:common` contains:
- `Outcome`, `attempt {}` and the closed `Failure` hierarchy with `RefusalReason`;
- `Redacted` with the `@RedactorApi` opt-in on its constructor;
- `AppDispatchers` and `CommonBindings` (a Metro `@BindingContainer`);
- `ConnectionScope`, `AppCoroutineScope`, `ConnectionCoroutineScope` and `supervisedContext`;
- the ids and `ConversationRef`;
- `AppBuildInfo`, `Channel`, `LaunchMode` and `PlatformViewHandle`;
- `AppLog` (a 64-entry buffer before install, flushed through the redacting root);
- `Trace` / `platformTrace()` and `WireJson`;
- the `expect`s `platformIoDispatcher`, `platformName`, `stackFramesOf` and `platformTrace`, with android, desktop
  **and iOS** actuals.

What `:core:security` contains:
- the `Redactor` (registered secrets, URL user info, `?token=`-style queries) and `RedactingWriter`;
- `ExternalLinkPolicy` + `ApprovedLink` (internal constructor) + `ExternalLinkOpener`;
- `AddressRules` (loopback, RFC1918, `100.64.0.0/10`, `*.ts.net`, link-local) with `normalize` returning
  `ServerOrigin`.

The app launches to one placeholder kit screen, "Conversations": a supporting line plus a "Show all conversations"
button. The button pushes `ChatsRoute.All` through `AppNavigator`, and that entry gets its own ViewModel store. On
desktop this was exercised headless by `DesktopShellUiTest`. On Android it is proven to compile and to package, not
launched (§7).

The application id is **`io.github.eslamasabry.opencode_mobile.native` only**. The plugin defines a single `preview`
flavour. The real package is not produced by any task on this branch. The namespace and the activity FQCNs stay
`io.github.eslamasabry.opencode_mobile.*`, so the component freeze (ADR §9.9) keeps working later.

---

## 2. Environment set-up (done first, as asked)

| Step | Command / result |
|---|---|
| Download the SDK tools | `curl -O https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip` (SHA-1 `e025545c…5f60`), unzipped to `/opt/android-sdk/cmdline-tools/latest`. `sdkmanager --version` prints `1.1.16717390 (Android CLI)`. It warns that `sdkmanager` is deprecated in favour of `android sdk`, and it lists packages with `/` separators; `;` still works for install. |
| Licences | `yes \| sdkmanager --licenses` |
| Packages | `sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "platforms;android-37.1" "build-tools;37.0.0" "build-tools;36.1.0"`. Installed: platform-tools 37.0.1, android-36 r2, android-37.0 r2, android-37.1 r1, build-tools 37.0.0 and 36.1.0. Also available but not installed: `android-37.2` (rev 2) and 37.2 betas. |
| `ANDROID_HOME` | `/opt/android-sdk`; `native/local.properties` has `sdk.dir` (gitignored). |
| JDK | JDK 21 was present (21.0.12.1). The ADR pins JDK 17 for the build, so `openjdk-17-jdk-headless` 17.0.20.1 was installed. **Gradle itself must run on 21** (see D1). |
| Long builds | `~/.claude/settings.json` gets `env.BASH_DEFAULT_TIMEOUT_MS=1800000` and `BASH_MAX_TIMEOUT_MS=3600000`. They apply from the next session; this session used explicit timeouts and background runs. |
| Maven Central | `repo1`/`repo.maven.apache.org` answered **HTTP 429** through this sandbox's proxy, and Gradle treats a 429 as fatal. A container-only init script, `~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo), therefore puts Google Maven and Google's official Central mirror `maven-central.storage-download.googleapis.com/maven2` first, and drops `MavenRepo`. The artifacts are the same. The checked-in settings are the plan's. |

---

## 3. Commands and results

All commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.
The container has 4 vCPUs and 15 GB RAM. The repo's PC caps applied: `-Xmx2g`, 2 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew -p build-logic test` | BUILD SUCCESSFUL. `ModuleRulesTest` 19/19 passed. |
| 2 | `./gradlew :androidApp:assembleDebug` | BUILD SUCCESSFUL, 185 tasks. It produces `androidApp/build/outputs/apk/preview/debug/androidApp-preview-debug.apk` (13.7 MB, unminified debug). `aapt2 dump badging` shows `package: name='io.github.eslamasabry.opencode_mobile.native' versionCode='10000' versionName='3.0.0-dev' … compileSdkVersion='37'`, `targetSdkVersion:'36'`, `launchable-activity: io.github.eslamasabry.opencode_mobile.MainActivity`, and both `composeResources/…ui.words.res/values{,-ar}/strings_chats.commonMain.cvr` packaged. |
| 3 | `./gradlew compileKotlinDesktop :desktopApp:compileKotlin` | BUILD SUCCESSFUL. Every KMP module compiles for `jvm("desktop")`, and so does the desktop app. |
| 4 | `./gradlew desktopTest testAndroidHostTest :quality:test` | BUILD SUCCESSFUL. 13 `desktopTest` + 4 `testAndroidHostTest` (`:core:common`) + 17 `:quality` tests; 0 failures, 0 skipped. |
| 5 | Planted `import androidx.compose.foundation.clickable` in `ChatsScreen.kt`, then `:quality:test --tests '*KonsistRulesTest*'` | `K1 features arrange kit parts only() FAILED`, as required. Reverted. |
| 6 | Planted `material3`, `LocalContext` and `clickable` together | The **compiler** fails first: `LocalContext` does not exist in `commonMain`, and `material3` is not on a feature's classpath. `KitOnlyPlantedTest` covers the same imports at the rule level. |
| 7 | Added `implementation(project(":engine:connection"))` to `:core:domain`, ran `./gradlew help` | Configuration fails: `Module rule: :core:domain may not depend on :engine:connection (ADR §6.1/§6.2)… [configuration commonMainImplementation]`. Reverted. |
| 8 | Added `implementation(libs.compose.material3)` to `:feature:chats` | Fails at dependency resolution: `Module rule: :feature:chats may not declare org.jetbrains.compose.material3:material3…`. It does **not** fail at `help` (see D9). Reverted. |
| 9 | compileSdk set to 37 without the minor level, then `:androidApp:checkPreviewDebugAarMetadata` | Fails: `Dependency 'androidx.compose.animation:animation-android:1.13.0-beta01' requires libraries and applications that…` / `Update this project to use a newer compileSdk`. This confirms audit BLOCKER 2. Restored to 37.1, which passes. |

Tests that passed:

| Module | Suite | Tests |
|---|---|---|
| build-logic | `ModuleRulesTest` | 19 |
| `:core:common` | `CommonTest` (desktopTest and testAndroidHostTest): `attempt` rethrows cancellation and classifies other errors; the AppLog pre-install buffer is flushed through the redacting root; `WireJson` errors never contain the input | 4 + 4 |
| `:core:model` | `ServerProfile` round-trip | 1 |
| `:core:security` | Redactor vectors, link policy vectors, address rules (100.64/10, ts.net, RFC1918, `[::1]`) | 3 |
| `:core:domain` | operations default to `Unavailable(FeatureId)` | 1 |
| `:engine:connection` | open is idempotent and close cancels the scope; a missing backend gives `Unavailable` | 2 |
| `:ui:routes` | every route round-trips through `RouteSaving.module` (nested sealed routes across files) | 1 |
| `:feature:chats` | the assisted ViewModel gets the route argument; back goes through `AppNavigator` | 1 |
| `:quality` | `KonsistRulesTest` 11 (K1, K2, K3, K4, K5, K10, K11, K13, K16, K18, K19), `KitOnlyPlantedTest` 2, `ModuleOwnersTest` 1, `GraphSmokeTest` 2, `DesktopShellUiTest` 1 | 17 |

`GraphSmokeTest` has two halves:
1. `DesktopAppGraph` resolves `ShellDependencies`, the `ChatsEntries` installer, `ChatsViewModel` through
   `metroViewModelFactory`, and the manual assisted factory for `ChatsAllViewModel`.
2. A test graph with one fake `GatewayFactory` does three things: `ConnectionRegistry.open()` creates a
   `ConnectionGraph` that **is a `TranscriptAccessors`**, connection-scoped singletons are stable per extension, and
   close empties the registry.

`DesktopShellUiTest` uses `runComposeUiTest`, headless. It runs `App()` from the real desktop graph and checks this
sequence: "Conversations" → click "Show all conversations" → "All conversations" / "Filter: No filter" → "Back" →
"Conversations".

Test names are not catalog ids (K12). These are infrastructure tests of slice-0 seams; catalog-row tests start with the
slice-1 contracts.

---

## 4. Build cost (feeds P8)

Measured with `tool/qa/measure-build.sh`, which samples the RSS of every `java` process every 0.5 s. Settings: Gradle
daemon `-Xmx2g`, in-process Kotlin compiler, 2 workers, configuration cache and build cache on unless stated.

"Clean" means all of the following: `--stop`, `clean`, the configuration cache deleted, and `--no-build-cache`. So it
is a cold daemon, a cold configuration, and full compilation. Dependencies were already downloaded.

| Scenario | Wall time | Peak RSS, Gradle daemon | Peak RSS, all JVMs |
|---|---|---|---|
| Clean, Android only (`:androidApp:assembleDebug`) | 56.6 s (final run 56.2 s) | 2068 MiB | 2882 MiB¹ |
| Clean, desktop only (`compileKotlinDesktop :desktopApp:compileKotlin`) | 43.5 s | 1691 MiB | 2506 MiB¹ |
| Clean, Android + desktop | 64.3 s | 2227 MiB | 3041 MiB¹ |
| Clean, full proof (assembleDebug + desktop compile + desktopTest + testAndroidHostTest + :quality:test) | 85.7 s | 2213 MiB | **2483 MiB** |
| Full proof again, nothing changed (configuration cache hit) | 2.1–2.9 s | 2158 MiB | 2260 MiB |
| Incremental, non-ABI edit in `:feature:chats`, Android only | 6.8 s (17 of 185 tasks ran) | 2282 MiB | — |
| Incremental, non-ABI edit in `:feature:chats`, Android + desktop | 5.7 s | 2337 MiB | — |
| Incremental, ABI edit in `:core:model` (new public function), Android only | 4.3 s | 2345 MiB | — |
| Incremental, ABI edit in `:core:model`, Android + desktop | 4.4 s (38 of 222 tasks; every dependent `compileKotlinDesktop` reran) | 2362 MiB | — |
| Clean, full proof restored from the local build cache | 38.5 s | 1713 MiB | 2625 MiB¹ |
| `-p build-logic test` from cold | 13.6 s | 848 MiB | — |

¹ These totals include an idle, unrelated Gradle daemon:
- in the first runs, a Gradle 8.14 daemon left over from generating the wrapper (about 710 MiB);
- in the cache and final runs, the `build-logic` daemon (about 550 MiB).

The 2483 MiB row was measured after that daemon was stopped, so it is the clean all-JVM peak.

Readings:
- **Desktop adds about 14% to a clean Android build** at slice-0 size (56.6 s → 64.3 s). Alone, desktop costs about
  77% of Android. The ADR estimated 1.3–1.6× compile work; on this tiny graph the real cost is lower. Re-measure at
  about 40 modules.
- **Incremental changes cost 4–7 s with or without desktop.** On these numbers a per-change desktop compile is
  affordable for T1. The final rule (ADR §4 row 17) still needs the PC and VPS runs that P8 names.
- **The Gradle daemon reaches its 2 GiB cap.** RSS is 2.1–2.4 GiB with `-Xmx2g`, which includes metaspace. No
  out-of-memory error or GC thrashing was seen. At about 60 modules, `-Xmx2g` on the PC is the number to watch.

---

## 5. Versions actually resolved (from Gradle, not memory)

Sources: `./gradlew :androidApp:dependencies --configuration previewDebugRuntimeClasspath`, `:desktopApp:dependencies
--configuration runtimeClasspath`, `:quality:dependencies --configuration testRuntimeClasspath`, and the plugin jars in
the Gradle cache.

| Area | Resolved |
|---|---|
| Gradle | 9.7.1 (wrapper). services.gradle.org reports 9.8.2 as current. |
| AGP | 9.3.3: `com.android.tools.build:gradle`, `gradle-api` and `builder`. Google Maven also has 9.4.1 and 9.5.0 stable. |
| Kotlin / Compose compiler | 2.4.21; `kotlin-stdlib` 2.4.21. Central also has 2.5.0-Beta2. |
| Metro | `dev.zacsweers.metro:runtime`, `gradle-plugin`, `metrox-viewmodel` and `metrox-viewmodel-compose` all 1.5.1. |
| CMP | `org.jetbrains.compose.{runtime,ui,foundation}` and `components-resources` 1.13.0-beta01; `material3` 1.13.0-beta01; desktop `ui-desktop` 1.13.0-beta01; skiko `skiko-awt` / `skiko-awt-runtime-all` 0.156.0. |
| Jetpack under CMP on Android | `androidx.compose.{runtime,ui,foundation,animation}` **1.13.0-beta01**. The ADR's notes say 1.13.0-beta02 was the latest; CMP maps to beta01 and nothing raised it. `androidx.compose.material3:material3` **1.5.0-beta01**. |
| Navigation 3 | `androidx.navigation3:navigation3-runtime` 1.2.0 (android and desktop variants); `navigation3-ui` 1.2.0 (Google), under `org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0. |
| navigationevent | `androidx.navigationevent:navigationevent(-compose)` **1.1.1**, pulled transitively; the catalog pin of 1.1.0 is not used yet. |
| Lifecycle | `org.jetbrains.androidx.lifecycle:lifecycle-{viewmodel,viewmodel-navigation3,runtime-compose}` 2.11.0 → `androidx.lifecycle:*` 2.11.0. |
| SavedState | `org.jetbrains.androidx.savedstate:savedstate` 1.4.0 → `androidx.savedstate:savedstate` **1.5.0** on Android (higher than the pin, from transitive requests). |
| Activity | `androidx.activity:activity-compose` 1.13.0. |
| kotlinx | coroutines 1.11.0, serialization-json 1.12.0, collections-immutable 0.5.2. |
| Konsist | 0.18.1, with `kotlin-compiler-embeddable` 2.4.21. It parses Kotlin 2.4 sources fine. |
| JDKs | Gradle on OpenJDK 21.0.12.1; toolchain and bytecode 17 (OpenJDK 17.0.20.1); Android host tests on a JDK 21 launcher. |
| Android SDK | compileSdk 37 minor 1 (`platforms;android-37.1` r1), targetSdk 36, minSdk 26; build-tools 37.0.0 (AGP's default). |
| foojay resolver | 1.0.0 (applied). Auto-download is off on this host through `~/.gradle/gradle.properties`, because both JDKs are installed. |

Catalog pins exist for the rest of the stack, but **slice 0 resolved none of them**: Ktor, OkHttp, Room 3, KSP,
Kermit, androidx.tracing, Coil, Robolectric, Roborazzi, Kover, dependency-analysis, Hot Reload.

---

## 6. Deviations from plans 01 and 03, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | JDK 17 for the build; `build-logic` uses `jvmToolchain(17)` (03 §6.2) | Gradle runs on **JDK 21**, and `build-logic` uses `jvmToolchain(21)`. Every module still compiles with toolchain 17 / `JvmTarget.JVM_17`. | Metro 1.5.1's Gradle plugin is published for JVM 21. Resolving it from a JVM 17 build fails with "Dependency requires at least JVM runtime version 21". |
| D2 | ~12 slice-0 modules (03 §22.1): core common/security/model/domain/testing, `:ui:routes`, `:android:migration`, `:androidApp`, `:desktopApp`, `:quality` | Added `:engine:connection`, `:engine:transcript`, `:ui:design`, `:ui:words`, `:ui:kit`, `:ui:shell` and `:feature:chats`. **Skipped `:android:migration`.** | P1's acceptance (graph extension, per-entry ViewModels, entry installers, a kit screen) needs them. Plan 03 §22.2 puts this in a `spike/graph` module; here it is real modules, kept as placeholders. `:android:migration` is P5's spike. |
| D3 | `:androidApp` flavours `stable` (the real id) and `preview` (`.native`) | Only `preview`. | The brief forbids using the real package. `stable` arrives with P5 / the switch. |
| D4 | `CommonBindings` as a `@ContributesTo` interface; `GatewayBindings` the same | `@BindingContainer` objects. | Metro 1.5.1 warns "Consider making this a binding container with `@BindingContainer`", and warnings fail T2 (`-Poc.werror=true`). |
| D5 | `kotlinx-coroutines-core` and `kotlinx-collections-immutable` as `implementation` in `oc.kmp.library` (03 §6.4) | `api`. | `StateFlow`, `ImmutableList` and `ImmutableMap` appear in the public engine and gateway signatures, so consumers failed with "Cannot access class kotlinx.collections.immutable.ImmutableMap". |
| D6 | `-Xexpect-actual-classes` only for `:core:common` | Set for every KMP module. | It is harmless, and Konsist K10 together with review still limit `expect` to `:core:common`. Narrow it back if wanted. |
| D7 | `AppLog` over Kermit; `platformTrace()` over `androidx.tracing` 2.0.5; a Logcat sink | `AppLog` writes straight to the redacting chain, with no Kermit. Android `Trace` uses `android.os.Trace`. There is no Logcat sink. | Keeps P1 small. Kermit, androidx.tracing and the sinks come with the diagnostics port in slice 1; the public `AppLog` / `Trace` API matches §18. |
| D8 | Desktop slice 0 shows an empty kit gallery | The desktop window runs the shared `App()` shell. | It proves the desktop graph and Nav3 at run time, which `DesktopShellUiTest` uses. The gallery replaces it in slice 1. |
| D9 | §23 #4: material3 in `:ui:routes` fails `./gradlew help` | The `ExternalRules` check fires when the configuration is resolved (any compile), not at `help`. | Catalog dependencies in KMP `sourceSets { dependencies {} }` are added lazily, so `withType<ExternalModuleDependency>().configureEach` sees them only when the configuration is realised. Project dependencies still fail at `help`. |
| D10 | `gradle.properties` / settings use `mavenCentral()` | The checked-in files are unchanged. This container swaps in Google's Central mirror through an untracked init script. | The proxy answers HTTP 429 from Maven Central. |
| D11 | Root `build.gradle.kts` lists every plugin with `apply false` (KSP, Room 3, Roborazzi, baseline profile, Kover, dependency-analysis) | Only the plugins slice 0 applies. | Avoids resolving unused plugins. Each one joins in the slice that uses it. |
| D12 | §6.4 opt-ins `ExperimentalTime`, `ExperimentalAtomicApi` | Kept. P-03-7: `limitedParallelism(1, name)` needed no extra opt-in on coroutines 1.11 / Kotlin 2.4.21. | — |
| D13 | `oc.kmp.feature` adds `:ui:testing` to `commonTest` | Not added. | `:ui:testing` starts in slice 1 (P7). |
| D14 | `:quality` is a plain `oc.jvm.library` | It also applies the Metro plugin (for its test graph) and the Compose compiler plugin (for the headless UI test). | Without the Compose compiler the test fails at run time with `NoSuchMethodError: ComposeUiTest.setContent(Function0)`. |
| D15 | The nightly iOS job on the VPS | `tool/qa/ios-klib-nightly.sh` plus the crontab line in its header (00:30). There is no GitHub workflow. | The ADR places it on the VPS, and AGENTS.md's CI budget asks for no new CI runs. The core and engine modules are always in scope; UI modules join with `OC_IOS_UI=1` (the ADR P1 fallback). |
| D16 | Kit parts | `KitScreen`, `KitText` and `KitButton` are placeholders, and `KitPlatform` has `haptic()` only. | The real kit signatures and the full `KitPlatform` freeze in `contracts/slice-1.md` (03 §22.4). |

The plan's unknowns settled by P1 (03 §22.3):

- **P-03-1, the AGP-KMP accessor:** settled. A binary plugin reaches the target with
  `(kotlin as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>()`. `withHostTest` works,
  and the task is named `testAndroidHostTest`.
- **P-03-3, Metro:** settled.
  - `@Provides` parameters on `@GraphExtension.Factory` work.
  - Interfaces contributed from another module are merged into the extension.
  - `@ContributesIntoSet(binding = binding<…>())` works.
  - `@RequiresOptIn` on a value-class constructor compiles and is enforced across modules.
  - Not tried: `@Binds` extension properties. They are not used yet.
  - A new finding: a `@DependencyGraph` or `@GraphExtension.Factory` cannot have a `companion object`, because Metro
    makes the companion implement the factory.
- **P-03-5, routes:** settled. `subclassesOfSealed<AppRoute>()` registers nested sealed routes across files. The
  `withRouteMotion` wrapper was not built; it is P2.
- **P-03-6, Konsist:** settled. Konsist 0.18.1 `scopeFromDirectory` sees `src/commonMain` of nested KMP modules, and
  `imports` / `packagee` work.
- **P-03-7:** settled (D12).
- **P-03-2 (detekt) and P-03-4 (Roborazzi):** not attempted.

---

## 7. Audit fixes applied (Codex audit of 01 + 03)

| Audit item | Applied in the skeleton |
|---|---|
| **BLOCKER 2:** compileSdk vs Compose 1.13 | `Sdk.COMPILE = 37`, `COMPILE_MINOR = 1`, applied through the AGP DSL `compileSdk { version = release(37) { minorApiLevel = 1 } }`. The same `CompileSdkSpec` is shared by the application, library and AGP-KMP conventions (`Sdk.ocCompileSdk()`). `platforms;android-37.1` is installed. Proven: without the minor level, `checkAarMetadata` rejects the Compose 1.13.0-beta01 AARs (§3 #9). |
| **BLOCKER 3:** forbidden edges in public signatures | `ModuleRules` allows `:core:security → :core:model`, used for real by `AddressRules.normalize(): Outcome<ServerOrigin>`, and `:ui:kit-content → :core:security` (for `WebPreview(ApprovedLink)`). Both have `ModuleRulesTest` vectors. For the third item (`:core:net` credentials), the rule stays: net may not depend on data. The `ModuleRules` KDoc records the fix: `:core:net` declares a credential-reader interface, with its `SecretStore` adapter bound above both modules. The interface itself is slice-1 work, because `:core:net` does not exist yet. |
| **BLOCKER 1:** `WriterLane` drain race | It is not in the skeleton, because `:core:data` is slice 1. The related admission rule was applied where the skeleton has a lifecycle: `ConnectionRegistry` serialises whole `open`/`close` operations with a `Mutex`, and `close` cancels **and joins** the connection scope before closing the gateway. The atomic admission-and-drain `WriterLane` contract must land in `contracts/slice-1.md`. |
| **BLOCKER 4:** two incompatible storage contracts | It is not in the skeleton, for the same reason. Flagged as an open problem below; one compilable contract is a slice-1 prerequisite. |
| MAJOR 5: incomplete profile model | `ServerProfile` is marked in KDoc as the skeleton shape, **not** the persisted DTO. The full DTO must be frozen in `contracts/slice-1.md`. |
| MAJOR 6: a serial dispatcher is not a mutex | `AppDispatchers.serial` is documented as "not a mutex"; `ConnectionRegistry` uses a `Mutex` for whole operations. |
| MAJOR 10: test toolchains and iOS actuals | Android host tests (`*AndroidHostTest*`) and `oc.android.library` tests get `javaLauncher = JDK 21`; module compiles stay on 17. iOS actuals exist for every `:core:common` `expect` (`platformIoDispatcher`, `platformName`, `stackFramesOf`, `platformTrace`). They are written but not compiled here, because the iOS job was not run. |
| MAJOR 12: stability config too broad | `config/compose-stability.conf` lists audited value packages only: `Outcome`, `Failure`, `core.common.ids.**`, `core.model.**`, `core.domain.capabilities.**`, `engine.**.state.**`, `ui.design.**` and immutable collections. Whole `core.common.**` and `core.domain.**` (scopes, service interfaces) are not listed. P4 decides the rest. |
| MAJOR 7, 8, 9, 11, 13 and MINOR 14 | Not applicable to P1. They cover WorkManager/startup, backup, the locale host, the Room façade, release-dependency policy and Flutter removal dates. They are open for P5, P6 and slice 1. |

---

## 8. Open problems

1. **The Android app was not launched.** There is no emulator in this container. `assembleDebug` proves that Kotlin,
   Metro graph validation, resources and the manifest all work. The run-time path (Nav3 on Android, per-entry
   ViewModel stores, edge-to-edge) still needs one emulator launch on the PC, and the Android half of the graph smoke
   still needs a Robolectric test in `:androidApp/src/test`. Robolectric 4.17 at compileSdk 37.1 is untested (P7).
2. **The iOS klib job was not run.** It is written in `tool/qa/ios-klib-nightly.sh`. Whether CMP 1.13.0-beta01 UI
   modules compile for iOS is unknown, so they stay behind `OC_IOS_UI=1`.
3. **Metro warns about unused graph inputs.** `[Metro/UnusedGraphInputs]` was fixed in `AndroidAppGraph` by exposing
   the inputs as accessors. New graph inputs will trip the warning again and break `-Poc.werror=true` builds.
4. **The `ExternalRules` timing differs from the plan (D9).** Acceptance check 03 §23 #4 expects a failure at `help`.
   Either change the check to a compile task, or switch the rule to inspect declared configurations in an
   `afterEvaluate` audit.
5. **The CMP and Jetpack lag.** Android resolves Jetpack Compose 1.13.0-beta01, not beta02. Raising it in
   `:androidApp` would be an ADR §7.6 "fix" bump and needs a reason plus T2.
6. **Pre-release dependencies on the classpath.** These are allowed now but blocked at the switch by D12 / audit #13:
   CMP 1.13.0-beta01, Compose 1.13.0-beta01 and M3 1.5.0-beta01. A grep of the full transitive
   `previewDebugRuntimeClasspath` tree found **no alpha artifact**; the only betas are those Compose and Material
   artifacts. A Gradle check that enforces this at T2 is not written yet.
7. **Plan statements to correct before slice 1:**
   - 03 §6.2: JDK 21 for `build-logic` and Gradle.
   - 03 §6.4: `api` for coroutines and immutable collections; `-Xexpect-actual-classes` scope.
   - 03 §3.2: the plugin list.
   - 03 §23 #4: when the failure fires.
   - 03 §21.2: what desktop shows in slice 0.
   - 01 §8: JDK row.
8. **The slice-1 prerequisites from the audit are still open:**
   - one compilable storage contract (BLOCKER 4);
   - an atomic `WriterLane` (BLOCKER 1);
   - the full persisted profile DTO (MAJOR 5);
   - the `:core:net` credential-reader seam (BLOCKER 3, third item).
9. **Not attempted in P1:** detekt (P-03-2), ktfmt and `tool/format-changed.sh`, `ComponentFreezeTest`,
   `ThirdPartyHashTest`, `version.properties`-driven release builds, `assemblePreviewRelease` with R8.
10. **P8 numbers come from a 4-vCPU cloud container,** not the PC or the VPS. Plan 03 §23 #14 still needs both hosts.
