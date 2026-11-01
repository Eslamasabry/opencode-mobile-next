# KMP + Compose Multiplatform status (research for the native rewrite)

Topic: `kmp-cmp-status`. Written 2026-11-01. Sources were read on 2026-11-01. The full list with URLs is at the end.

Labels used in this file:

- **[V]**: verified on 2026-11-01 from a primary source. That means official docs or release notes, Maven Central or Google Maven metadata, the GitHub/YouTrack trackers, or the Gradle module metadata of the published artifacts.
- **[S]**: secondary source, such as a vendor testimonial, a conference slide or a community post.
- **[U]**: unverified, or my projection. Check it in slice 0 before relying on it.

---

## 0. Bottom line

1. **On Android, Compose Multiplatform runs Jetpack Compose.** [V] The Android variant of
   `org.jetbrains.compose.material3:material3:1.13.0-beta01` depends on
   `androidx.compose.material3:material3:1.5.0-beta01`. Likewise `org.jetbrains.compose.ui:ui-android:1.12.2`
   depends on `androidx.compose.ui:ui:1.12.1` (checked in the published `.module` files).
   - So writing the UI in `commonMain` costs nothing in smoothness on Android.
   - Ripple, stretch overscroll, predictive back and haptics are Google's own Android code.
   - The demo's "so much smoother" result applies directly.
2. **Android, iOS and desktop (JVM) are Stable. Web is Beta.** [V/S]
   - iOS has been stable since CMP 1.8.0 (May 2025).
   - Linux desktop works, but it renders through X11/XWayland. Native Wayland is still an open request (SKIKO-28, open, updated 2026-09-11).
3. **Material 3 is the weak spot.** [V]
   - The only *stable* multiplatform Material3 is `1.9.0`, based on Jetpack M3 1.4.0. In that version the Expressive theme is internal.
   - The Expressive line has been alpha since CMP 1.10. As of 2026-10-23 it is **beta**: `material3:1.13.0-beta01`, based on Jetpack M3 `1.5.0-beta01`.
   - Jetpack M3 `1.5.0` is itself still beta (`1.5.0-beta02`, 2026-10-21).
   - Most Expressive components graduated to non-experimental during the 1.5 alphas.
   - `LoadingIndicator` and `MaterialShapes` were reverted to experimental.
4. **Use Navigation 3 for navigation.** [V]
   - Jetpack Navigation3 1.2.0 went stable on 2026-09-23. The multiplatform `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0` was published 2026-10-23.
   - 1.2.0 adds `NavigationBackHandler` for predictive back, mid-flight predictive back, a result API and KMP deep links.
   - 1.1.0 added shared elements between scenes.
5. **Toolchain to pin today** (detail in §1 and §11):
   - Kotlin 2.4.21, AGP 9.3.3, Gradle 9.7.x, JDK 17.
   - CMP 1.13.0-beta01, moving to 1.13.0 stable when it ships. 1.12.2 is the fallback the demo has proven.
   - Lifecycle 2.11.0, Navigation3 1.2.0.
   - Kotlin 2.5.0 is planned for Dec 2026. AGP 9.5.0 is stable but outside Kotlin 2.4.21's tested AGP range.
6. **iOS cannot be built on Linux.** [V] Final Apple binaries need a macOS host. The VPS and the PC can still compile common code to iOS klibs as long as nothing uses cinterop.

---

## 1. Version snapshot (as of 2026-11-01)

| Component | Latest stable | Latest pre-release | Source / date |
|---|---|---|---|
| Kotlin | **2.4.21** (2026-10-08) | 2.5.0-Beta2 (Maven, 2026-10-21); 2.5.0 planned Dec 2026 | kotlinlang releases page; Maven Central KGP metadata [V] |
| Kotlin 2.4.0 | released June 2026 | - | blog URL `2026/06/kotlin-2-4-0-released`. The "What's new" page says July 14; the releases page says June 3 [V, dates conflict] |
| Compose Multiplatform (plugin + runtime/ui/foundation) | **1.12.2** (2026-10-15) | **1.13.0-beta01** (2026-10-23) | GitHub releases + Maven Central [V] |
| CMP Material3 (multiplatform) | 1.9.0 (Jetpack M3 1.4.0; no Expressive) | **1.13.0-beta01** (= Jetpack M3 1.5.0-beta01). CMP 1.12.2 ships `1.12.0-alpha03` (= M3 1.5.0-alpha22) | CMP release notes [V] |
| Jetpack Compose (Android) | 1.12.1 (1.12.0 on 2026-08-12) | 1.13.0-beta02 (2026-10-21) | developer.android.com [V] |
| Jetpack Material3 | 1.4.0 | **1.5.0-beta02** (2026-10-21) | developer.android.com [V] |
| CMP Material3 Adaptive | 1.3.0 | 1.4.0-alpha01 | Maven Central [V] |
| Navigation 3 (CMP) | **1.2.0** (Maven 2026-10-23) | - | Maven Central [V] |
| Navigation 3 (Jetpack) | 1.2.0 (2026-09-23) | 1.3.0-alpha03 (2026-10-21) | developer.android.com [V] |
| navigation-compose (CMP) | 2.10.0 | - | Maven Central [V] |
| Navigation Event (CMP) | 1.1.0 | - | Maven Central [V] |
| Lifecycle / ViewModel (CMP) | **2.11.0** | Jetpack 2.12.0-alpha05 | Maven Central, Google Maven [V] |
| SavedState (CMP) | 1.4.0 | - | Maven Central [V] |
| Compose Hot Reload | 1.2.0 | 1.3.0-rc01 (2026-10-30) | Maven Central [V] |
| AGP | **9.5.0** (Oct 2026), 9.4.1, 9.3.3 | 9.6.0-alpha02 | Google Maven; AGP release notes [V] |
| Gradle | **9.8.2** (2026-10-19) | - | gradle.org/releases [V] |
| KGP 2.4.20–2.4.21 tested ranges | Gradle 7.6.3–**9.7.0**, AGP 8.5.2–**9.3.1**, Xcode 26.4 | - | KMP compatibility guide [V] |
| activity-compose | 1.13.0 | 1.14.0-alpha03 | Google Maven [V] |
| Glance (Android only) | 1.2.0 | 1.3.0-alpha02 | Google Maven [V] |
| Ktor | 3.6.1 | - | Maven Central (2026-10-29) [V] |
| kotlinx.serialization | 1.12.0 | - | Maven Central [V] |
| kotlinx.coroutines | 1.11.0 | - | Maven Central [V] |
| kotlinx-datetime / kotlinx-io | 0.8.0 / 0.9.2 | - | Maven Central [V] |
| Coil 3 | 3.6.3 | - | Maven Central [V] |
| Room 3 (KMP) / SQLDelight | 3.0.3 / 2.4.1 | Room 3.1.0-alpha03 | Google Maven / Maven Central [V] |
| DataStore (KMP) | 1.3.0 | - | Google Maven [V] |
| Koin / Metro (DI) | 4.2.2 / 1.5.1 | - | Maven Central [V] |
| Roborazzi (incl. Compose Desktop) | 1.77.0 | - | Maven Central [V] |

What the demo (`/home/eslam/Storage/Code/kmp-demo`) pinned: AGP 9.3.2, Kotlin 2.4.0, CMP 1.12.2, material3 1.12.0-alpha03, navigation-compose 2.10.0, navigationevent 1.1.0, lifecycle 2.11.0, Ktor 3.6.1 and Gradle 9.5.0.

- Kotlin 2.4.0's tested AGP range ends at 9.1.0. The demo built fine with AGP 9.3.2, but that combination is untested.
- The Flutter app's `android/` uses the same AGP 9.3.2, Kotlin 2.4.0 and Gradle 9.5.0.

Why compileSdk 37 was needed in the demo:

- AndroidX `core 1.19.0` and `lifecycle 2.11.0` declare compileSdk 37 in their AAR metadata. This is a community PR observation [S].
- API 37 needs AGP ≥ 9.1.1 [S].
- Jetpack Compose `1.13.0-alpha03` raised the Compose libraries' compileSdk to **37.1** [V]. CMP 1.13 pulls Compose 1.13 on Android, so expect this requirement to appear (see Unknowns).

---

## 2. Platform maturity

### Android: production-grade, and it is Jetpack Compose [V]

- CMP supports Android 5.0 (API 21) and up. The Flutter app's floor of minSdk 26 is fine.
- The "Android first and best" requirement is met by construction:
  - The platform-native behaviours come from AndroidX: `Indication`/ripple, stretch overscroll (`LocalOverscrollFactory`), `LocalHapticFeedback`, predictive back through AndroidX `navigationevent`/`activity`, IME insets, autofill and accessibility.
  - Android-only APIs stay reachable from `androidMain` or the app module.
- Recent Jetpack Compose additions you get for free on Android:
  - 1.12.0 (2026-08-12): downloadable variable fonts, wide colour gamut, mesh gradients, Credential Manager integration, automatic interaction sounds.
  - 1.10/1.11 (per a JetBrains slide [S]): Retain API, improved shared element transitions, Flexbox/Grid, Style API, a new SlotTable.

### iOS: Stable since CMP 1.8.0 (May 2025) [V]

- The JetBrains blog declared it "stable and production-ready". It claims about +9 MB app size compared with SwiftUI [V, vendor claim].
- Recent work:
  - Native text input, opt-in and `@ExperimentalComposeUiApi` (1.11).
  - Concurrent/parallel rendering on by default (1.11).
  - Smoother lazy-list scrolling (1.12).
  - Fixes for swipe-back with the keyboard open and for RTL text selection on Mac (1.13.0-beta01).
- Minimum OS:
  - CMP says iOS 14.
  - **Kotlin 2.4.0 raised the Kotlin/Native iOS minimum to 15.0** (an override flag exists).
  - The Tier-1 table lists `iosArm64` and `iosSimulatorArm64` for iOS 15.0+.
  - Plan for iOS 15+.
- **Building:** "Building final binaries for Apple targets on Linux and Windows is also not possible." From a Linux x86_64 host you can produce iOS `.klib`s "only without cinterop dependencies" [V]. The iOS slice therefore needs a Mac or a macOS CI runner.
- Swift export is Alpha in Kotlin 2.4.0: suspend maps to `async`, Flow to `AsyncSequence`. Kotlin 2.4.20 added `Package.swift` generation [V].
- Native feel on iOS: Compose draws its own widgets; it does not use UIKit controls. For an owner who prizes "native feel", iOS will need iOS-adaptive kit variants. JetBrains reports "93% report a native-like experience" [S, vendor survey].

### Desktop (JVM), with Linux in detail [V]

- Supported: Ubuntu 20.04+ on x86-64 and arm64. 64-bit only. Running needs JDK 11+; packaging needs JDK 17+ for `jpackage`.
- Stable (CMP FAQ). JetBrains Toolbox has been Compose for Desktop since 2021 [V]. Gitnuro (a Compose Desktop git client) is on Flathub [V].
- **Linux rendering is X11-based.**
  - On Wayland sessions it runs through XWayland. "Support Wayland display servers natively" is **SKIKO-28, open**. JBR's WLToolkit exists, but Skiko does not use it officially.
  - Open Linux issues as of 2026-11-01:
    - CMP-1847: tray icon on GNOME.
    - CMP-4614: tray menus on Fedora.
    - CMP-5101: occasional crash on Wayland.
    - CMP-9725: input dies after DPMS or suspend on X11.
    - CMP-10993: KDE Wayland TextField focus under hot reload.
    - SKIKO-1097: NVIDIA + COSMIC black window.
  - Fractional scaling under XWayland on GNOME is blurry. GNOME calls this a general XWayland limitation; I did not test it with CMP [S].
- Startup:
  - CMP 1.13.0-alpha01 adds AppCDS/AOT support (PR #5644).
  - Informal reports: macOS 2–5× faster, Windows 1.5×, and on Java 25 about 400 ms vs 800 ms. A JBR-team caveat: the cache may not survive shipping. [S]
  - Linux numbers are unknown.
- New in CMP 1.12 (experimental): Window/Dialog API v2 (`androidx.compose.ui.window.v2`), and an MCP server in Compose Hot Reload for AI agents.

### Web: Beta since CMP 1.9

Not in scope for this app.

---

## 3. Material 3 and Material 3 Expressive

Facts [V]:

- **Stable CMP Material3 = `1.9.0`**, based on Jetpack M3 1.4.0. Jetpack 1.4.0-beta01 *removed* every `@ExperimentalMaterial3ExpressiveApi` API ("switch to 1.5.0-alpha"). In 1.4.0, `MaterialExpressiveTheme` is internal.
- The CMP Material3 line has tracked the Jetpack M3 1.5 alphas since CMP 1.10:

| CMP | CMP material3 | Based on Jetpack M3 |
|---|---|---|
| 1.10.3 | 1.10.0-alpha05 | 1.5.0-alpha08 |
| 1.11.1 | 1.11.0-alpha07 | 1.5.0-alpha17 |
| 1.12.2 | 1.12.0-alpha03 | 1.5.0-alpha22 |
| 1.13.0-beta01 | **1.13.0-beta01** | **1.5.0-beta01** |

- Expressive items that were graduated or promoted in the Jetpack 1.5.0 alphas (developer.android.com M3 notes):
  - `MotionScheme`: non-experimental (alpha15, 2026-02-25).
  - "materialExpressTheme" (as the notes spell it) and `expressiveLightColorScheme`: promoted (alpha18, 2026-04-22). alpha18 also added a `Material3ExpressiveApi` annotation that needs no opt-in.
  - `SplitButton` (alpha20).
  - `ButtonGroup` and `FloatingToolbar` (alpha22).
  - Flexible top and bottom app bars, and Expressive list items (alpha23).
  - `BottomAppBar` (alpha26).
  - `LocalMotionScheme` removed in favour of `MaterialTheme.motionScheme` (alpha27).
- Items reverted to or kept experimental:
  - `MaterialShapes` and `LoadingIndicator` promotions were **reverted**.
  - `PullToRefreshDefaults.loadingIndicatorColor` is experimental again (alpha21).
  - `AppBarWithSearch` is back under `@ExperimentalMaterial3Api` (alpha24).
- CMP's API docs list `MaterialExpressiveTheme` in `common`, with no opt-in annotation shown (1.10.0 docs), so it is available to every target [V for common, U for per-platform polish].
- Jetpack M3 `1.5.0-beta01` (2026-10-07) API changes:
  - `Slider`/`RangeSlider` now require `onValueChange`.
  - `PolygonShape` and carousel changes.
  - Compose UI 1.13.0-beta01 renamed `Modifier.blur`'s `radius` parameter.

  This is the churn to expect until stable.

What this means for the app:

- The kit should be built on the **M3 1.5 line** (CMP `material3:1.13.0-beta01` → stable). Do not use stable `1.9.0`: the rewrite will run longer than the beta, and 1.9 lacks the Expressive components and theme.
- The kit-only rule makes this safe. Only the kit imports `androidx.compose.material3.*`, so API churn stays inside one owner's module. Enforce it with a dependency rule: only `:ui:kit` may depend on material3.
- Keep still-experimental pieces (`LoadingIndicator`, `MaterialShapes`, `AppBarWithSearch`) inside the kit behind kit names, or avoid them.
- Motion: owner rule is "subtle 150–250 ms" plus "max Android-native".
  - `MaterialExpressiveTheme` defaults to the expressive (bouncier) motion scheme.
  - Recommendation: pass `motionScheme = MotionScheme.standard()` explicitly, and let the kit's own duration tokens drive page and element motion.
  - "No glass" means no blur or translucency tokens.

---

## 4. Navigation

### Options and status [V]

- **Navigation 3** is a user-owned back stack: a `SnapshotStateList` of `@Serializable` `NavKey`s rendered by `NavDisplay`.
  - Multiplatform since CMP 1.10.
  - CMP artifact `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0`. Only `navigation3-ui` has a CMP-specific implementation; it pulls `navigation3-common`.
  - Optional: `lifecycle-viewmodel-navigation3` and `material3.adaptive:adaptive-navigation3`.
  - Jetpack 1.1.0 (2026-04-08): shared elements between scenes (pass a `SharedTransitionScope` to `NavDisplay`), `SceneDecoratorStrategy`, a list of `SceneStrategy`s, a type-safe `MetadataKey` DSL, and overlay exit animations.
  - Jetpack 1.2.0 (2026-09-23):
    - `NavigationBackHandler` + `rememberNavigationEventState` wired to `SceneState` (`onBackCompleted` / `onBackCancelled`).
    - Predictive back can start mid-flight during a forward animation.
    - A result API (`ResultEventBus`, `ResultEffect`).
    - **KMP deep links** (`DeepLinkRequest`, `DeepLinkUri`, `DeepLinkMatcher`).
- **navigation-compose (Nav2) multiplatform 2.10.0** works and is what the demo used. Google's Nav3 page does not formally deprecate Nav2, but Nav3 is the actively developed Compose-first library.
- Multiplatform requirement: on non-JVM targets there is no reflection. Use the `rememberNavBackStack(SavedStateConfiguration, start)` overload with a `SerializersModule` that registers `NavKey` subclasses, for example via `subclassesOfSealed<Route>()` [V].
- `PredictiveBackHandler` is deprecated (CMP 1.10) in favour of `NavigationBackHandler` (Navigation Event) [V].
- iOS: CMP enables an iOS back gesture by default. Nav3 can enable end-edge pan (`EndEdgePanGestureBehavior`, off by default) [V].

### The demo's predictive-back vs container-transform fight

- Nav3 docs [V] give three transition hooks:
  - `transitionSpec`, `popTransitionSpec` and `predictivePopTransitionSpec` (the last receives the swipe edge).
  - Per-entry overrides through `NavDisplay.TransitionKey`, `PopTransitionKey` and `PredictivePopTransitionKey` metadata.
  - Shared elements: wrap `NavDisplay` in `SharedTransitionLayout` and pass `sharedTransitionScope = this`.
- The docs give no guidance on combining a container transform with predictive back [V, gap].
- The pattern the demo found, which is what the Flutter app does too (`screens-nav.md` §3.2):
  - Container transform (`sharedBounds`) on forward navigation and on button-back.
  - The platform predictive-back motion while the gesture is live. Drop the shared bounds while `transitionState is NavigationEventTransitionState.InProgress`.
  - This must be the first prototype in the navigation slice [U: exact hook point in Nav3 1.2].

### Recommendation

Use Nav3 1.2.0 with:

- one sealed `Route : NavKey` hierarchy per feature module, aggregated in the app shell;
- one back stack owner, matching today's "one Navigator, named routes";
- `rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`;
- scenes for the wide-window rail and list-detail layout (material3-adaptive 1.3.0);
- Nav3 deep links mapping the existing `oc/link`, share-in, notification and widget entry routes.

---

## 5. Lifecycle, ViewModel, SavedState [V]

- `org.jetbrains.androidx.lifecycle:lifecycle-*:2.11.0` is multiplatform and stable (CMP 1.12/1.13 both pin it). `savedstate:1.4.0` and `navigationevent-compose:1.1.0` are also multiplatform and stable.
- In common code, `viewModel()` needs an initializer: `viewModel { ChatViewModel(...) }`. Non-JVM targets have no reflection.
- Desktop: `viewModelScope` uses `Dispatchers.Main.immediate`. Add `kotlinx-coroutines-swing` to `jvmMain`.
- iOS has no built-in `ViewModelStoreOwner`. Only relevant if a SwiftUI shell is ever used; with full CMP UI it does not matter.
- Kotlin 2.4 made **explicit backing fields** stable. This removes the `_state`/`state` boilerplate in ViewModels:

  ```kotlin
  val state: StateFlow<ChatUi>
      field = MutableStateFlow(ChatUi())
  ```

  Context parameters are stable too [V].
- Compose compiler (bundled with Kotlin):
  - The `StrongSkipping` and `IntrinsicRemember` flags are at `DeprecationLevel.ERROR` and are removed in 2.5.0. Do not set them.
  - Internal types' stability is inferred at runtime (Kotlin 2.4) [V].
- `SavedStateHandle` in common code: [U] not stated on the CMP ViewModel page. Verify in slice 0 if process-death restore of drafts relies on it. The Flutter app persists drafts itself, so plan on explicit persistence anyway.

---

## 6. Resources and localization [V]

- `compose.components.resources` (`components-resources`, same version as CMP) generates a `Res` class.
  - Strings, string templates, string arrays, plurals, drawables (PNG/JPG/WebP/XML vector; **SVG on every platform except Android**), fonts and raw `files/`.
  - Qualifiers: language/region (`values-ar`, `values-b+zh+Hans`), theme (`light`/`dark`) and density.
- **On Android, all multiplatform resources are packed as Android *assets*** (since CMP 1.7). They are therefore **not** Android `R` resources. Anything Android reads from XML must stay in `androidMain/res` or the app's `res`:
  - the app label in the manifest;
  - widget provider XML and descriptions;
  - `shortcuts.xml`;
  - notification channel names, if declared statically;
  - locale config.
- Non-composable reads use suspend functions: `getString(Res.string.x)` and `getPluralString(...)`. Use these for notification text, widget text and services.
- The KMP Android library plugin needs `androidResources { enable = true }` to use `Res` in the android target.
- Large raw files cannot be streamed. Use `Res.getUri()` to hand them to a platform API.
- Arabic RTL: Compose sets `LayoutDirection` from the locale. Also provide `values-ar/strings.xml`.
  - ICU `select` messages from the Flutter ARB do not exist in Compose resources. Port that logic into code.
  - ICU plurals map to `<plurals>`.
  - Write the ARB→XML conversion as a script, once.
- [U] Whether CMP resources follow Android's per-app language (`AppCompatDelegate.setApplicationLocales` / Android 13 locale config) automatically. The "Manage local resource environment" page covers in-app language overrides; test it in slice 0.

---

## 7. Linux desktop packaging

| Route | Status | Notes |
|---|---|---|
| `jpackage` via the CMP Gradle plugin: `TargetFormat.Deb` / `Rpm` | Built in [V] | Build on Linux (no cross-compiling); JDK 17+. Tasks: `packageDeb`, `packageReleaseDeb` (ProGuard). Linux DSL: `packageName`, `debMaintainer`, `menuGroup`, `appCategory`, `appRelease`, `rpmLicenseType`, `iconFile` (PNG). `jlink` does **not** detect modules automatically; run `suggestModules` and list them in `modules(...)` |
| `TargetFormat.AppImage` | Built in, but misleading [V] | In the plugin source, `AppImage("app-image", currentOS)` is jpackage's *app-image directory*. It is **not** a `.AppImage` file. Wrap `createReleaseDistributable` output with `appimagetool` to get a real AppImage [U: no official recipe] |
| Flatpak | Not built in [V] | Model: `flathub/com.jetpackduba.Gitnuro` (a Compose Desktop app on Flathub; a Kotlin Slack thread describes it as "mostly a FatJar + SDK + .desktop") [S]. Needs an X11 socket, with a Wayland fallback that will not help until SKIKO-28 lands |
| Conveyor (Hydraulic) | Third party; Gradle plugin 2.0 | Self-updating packages for Linux/macOS/Windows from a Linux build host. **Free for projects under an OSI license**; this repo is MIT [V]. The CMP docs point to it for online updates [V] |
| Tarball + `install.sh` | What the Flutter app ships today (`linux/packaging/`, AppStream `metainfo.xml`) | Reuse the `.desktop` and metainfo files |
| Startup | AppCDS/AOT in CMP 1.13.0-alpha01 | Gains reported for macOS/Windows; none for Linux [S]. JDK 25 (LTS) has the AOT cache |

---

## 8. Gradle: structure, build times, best practice

### AGP 9 + KMP structure (mandatory) [V]

- With AGP 9, the KMP plugin **no longer works with `com.android.application` or `com.android.library` in the same module**.
- Shared modules use `com.android.kotlin.multiplatform.library` with a `kotlin { android { … } }` block. In the AGP-9 migration guide this was `androidLibrary {}`, and `androidTarget` is renamed to `android`.
- The Android app is a **separate** `androidApp` module that applies `com.android.application` and depends on the shared modules. Remove `org.jetbrains.kotlin.android`: AGP 9 has built-in Kotlin, and applying the plugin fails the build.
- The shared module's namespace must differ from the app's namespace.
- `kapt` is incompatible with built-in Kotlin. Use KSP.
- What the Android-KMP library plugin (min AGP 8.10, KGP 2.0) does **not** support [V]:
  - build types and flavors (single variant);
  - `externalNativeBuild` (NDK/CMake);
  - data binding and view binding;
  - `BuildConfig` generation (use BuildKonfig or similar);
  - AIDL and RenderScript.
- Two defaults to change in that plugin:
  - Consumer keep rules are **not** published by default. Set `optimization { consumerKeepRules { publish = true; file(...) } }`.
  - Host and device tests are opt-in: `withHostTest {}` / `withDeviceTest {}`.
- AGP 9.3 [V]:
  - adds the `analyzeReleaseR8Config` task;
  - adds the `optimization {}` DSL;
  - lets keep rules live in `src/<variant>/keepRules/*.keep`, which also covers consumer rules in KMP modules.
- AGP 10 (second half of 2026, per JetBrains) removes the legacy variant API (`android.enableLegacyVariantApi`).

For this app, the Flutter app's `android/` ships **prebuilt `jniLibs`** and has no CMake. The prebuilts are `libproot.so`, `libproot-loader.so` and the `libaiteam_*` Rust engine. They, the foreground services, widgets, notifications, the update installer and anything needing build variants all belong in **plain Android modules**: the `androidApp` module or `com.android.library` modules. They do not belong in KMP modules.

### Best practice (Kotlin + Gradle docs) [V]

- Kotlin DSL; **one version catalog** (`gradle/libs.versions.toml`); **convention plugins in a `build-logic` included build**. Gradle's best-practice index lists "Use Convention Plugins" and "Favor build-logic composite builds".
- Modularize so a change rebuilds one module. This also maps onto the repo's single-owner rule and the one-agent-per-feature worktrees.
- `org.gradle.configuration-cache=true` (this implies parallel) and `org.gradle.caching=true`.
- In multi-target modules, **never run `build`/`assemble` routinely**. Run the target task, for example `:androidApp:assembleRelease`, `:shared:jvmTest`, `:shared:testAndroidHostTest` or `linkDebug*` for native.
- Kotlin/Native (iOS, later):
  - `org.gradle.jvmargs=-Xmx3g`;
  - keep `~/.konan` between CI runs;
  - `kotlin.incremental.native=true` (Beta);
  - debug link only during development; a release link takes about 10× longer;
  - keep `kotlin.native.binary.gc` at the new CMS default;
  - avoid `transitiveExport`.
  - Kotlin 2.4 halved peak memory of release links in one report [V].
- The CMP Gradle plugin's `compose.ui`-style dependency aliases are deprecated (since 1.10). Use catalog coordinates, as the demo already does.

### Build-time realities for this setup [U, reasoned rather than measured]

- An Android-only KMP build (android + jvm targets) costs about the same as a plain Compose Android app. Kotlin/Native is the expensive part.
- On a Linux host, gate iOS targets so the PC and VPS never try to compile them by accident (see §12.2).
- Each worktree runs its own Gradle daemon (2–3 GB) plus a Kotlin daemon.
  - On the 16 GB PC (ceiling: 2 Codex + 1 Claude) that allows about one build at a time. Keep `org.gradle.workers.max=2` and `-Xmx2g`, as the demo does.
  - Run full and release builds on the VPS.
- The local build cache lives in the shared Gradle user home. Worktrees on the same host can reuse each other's task outputs when inputs match. Free speed for parallel agents, provided paths stay relocatable.

---

## 9. Testing and agent tooling (only what is relevant to KMP)

- Logic tests: write `commonTest` with `kotlin.test` + `kotlinx-coroutines-test`. They run on JVM (`jvmTest` / `testAndroidHostTest`) on the VPS, with no emulator. This is the natural home for the 6,663 cataloged logic tests.
- UI tests (the later 7,967 widget tests): **common Compose UI tests** via `androidx.compose.ui.test.v2.runComposeUiTest` (Experimental).
  - They run on desktop JVM (`:shared:jvmTest`), iOS simulator, web, and Android instrumented.
  - They do **not** run on Android local/host JVM [V].
  - Roborazzi 1.77.0 supports Compose Desktop screenshots [V].
  - Android-only behaviours (predictive back, ripple, IME, haptics) still need an emulator: `connectedAndroidTest`.
- **Compose Hot Reload** is desktop JVM only, bundled with the CMP Gradle plugin since 1.10. Its **MCP server** (experimental, CMP 1.12) lets coding agents inspect and drive a running app.
  - A `jvm("desktop")` target from day 1 gives agents a fast, emulator-free UI loop on the PC or VPS.
  - The emulator pass before any APK still applies.

---

## 10. Production KMP + CMP apps

Shared **UI** with CMP, from JetBrains case studies and the CMP landing page; the claims are the companies' own [S]:

| App | What is shared | Numbers |
|---|---|---|
| **Markaz** (e-commerce, Pakistan) | 100% of UI in CMP, with native camera, QR, payments, nav and analytics | **100+ screens**, 5M+ downloads, 1M+ active users, under 10 MB on Android (137 MB on iOS) |
| **Bitkey** (Block) | KMP + CMP unified UI | 95% of the mobile codebase |
| **Feres** (taxi) | 100% of logic, >90% of UI | 1M+ downloads |
| **Physics Wallah** | about 20% of the main app in CMP; new apps (Acadfly, Parent) fully CMP | 10M+ Play downloads, 17M active users |
| **Instabox / Instabee** | almost all UI; maps, camera and video stay native | 340K+ installs (Sweden) |
| **Wrike** | Calendars, Boards, Dashboards, Charts, Timesheet Approvals | in production since CMP beta |
| **Fast&Fit**, **Music Work**, **Pocket Bard** (750K+), **Fabuly** (350K+), **Perk/TravelPerk**, **Sony** headphone companion, **BiliBili** IM, **Pincode Business** (Android + desktop) | CMP UI | various |

Logic-only KMP at scale [S/V]:

- Google Docs on iOS: in production, with performance "on par or better" (Google I/O 2025 post).
- Cash App, Netflix studio apps, McDonald's, Duolingo, Forbes (>80%), Bolt, Booking.com, X, Workday, Quizlet (100M installs), Meituan (Android/iOS/Windows), Autodesk, OpenSooq.

Desktop with Compose:

- **JetBrains Toolbox App**: Compose for Desktop since 2021, about 1M monthly users per the 2021 case study.
- **Gitnuro**: on Flathub.

Adoption figures from JetBrains [S]: "81% of KMP developers use CMP". The State of Kotlin 2026 report (Sept 2026) exists, but I could not open its PDF.

No public case study I found matches this app's exact shape: a streaming chat client plus an on-device Linux runtime plus widgets. The closest evidence of scale is Markaz (100+ screens, fully CMP). The app's native services are plain Android code either way.

---

## 11. Recommendations for this app

1. **Architecture choice: CMP UI in `commonMain` from day 1, Android-first.**
   - On Android this *is* Jetpack Compose (§0.1), so no smoothness is lost.
   - Do not write Android-only Jetpack Compose UI now with a plan to port it later. That doubles the UI work when iOS and Linux arrive.
   - Platform-specific UI goes behind small kit-owned `expect`/`actual` or interface seams:
     - haptic flavours;
     - the system back-gesture visuals;
     - the reduced-motion source (animator scale on Android);
     - text-scale caps;
     - window insets.
2. **Pin now, upgrade on named triggers:**

   | Item | Pin now | Move when |
   |---|---|---|
   | Kotlin (+ Compose compiler plugin, same version) | 2.4.21 | 2.5.0 (planned Dec 2026), once CMP's next release declares support |
   | Compose Multiplatform | 1.13.0-beta01 | 1.13.0 stable. Projection: about mid-Dec 2026, from the 1.12 beta→stable gap of about 8 weeks [U]. Fallback: 1.12.2 + material3 1.12.0-alpha03 (demo-proven) if 1.13 betas break the slice-0 build |
   | CMP material3 | 1.13.0-beta01 | stable once Jetpack M3 1.5.0 is stable (date unknown) |
   | AGP | 9.3.3 (inside KGP 2.4.21's tested ≤9.3.1 line) | 9.5.x together with Kotlin 2.5 |
   | Gradle | 9.7.x (KGP 2.4.21 tested ≤9.7.0) | 9.8.x with Kotlin 2.5 |
   | JDK | 17 (repo pin) for Android; 21 or 25 for desktop packaging and AOT [U] | - |
   | compileSdk / minSdk | 37 (37.1 if Compose 1.13 demands it) / 26 | targetSdk: match the Flutter build at the switch |
   | Lifecycle / Nav3 / NavEvent / SavedState / M3-adaptive | 2.11.0 / 1.2.0 / 1.1.0 / 1.4.0 / 1.3.0 | follow CMP's release table |

   Never mix an androidx Compose version that is newer than the one CMP compiled its common API against into `commonMain` APIs. Android-only code may use newer androidx directly, because Gradle picks the highest version.
3. **Targets:**
   - Declare `android` + `jvm("desktop")` now.
   - Declare `iosArm64` + `iosSimulatorArm64` behind a Gradle property, and run a Linux CI job that compiles common code to iOS klibs. This keeps `commonMain` free of JVM-only APIs.
   - [U] Whether an android+jvm-only `commonMain` can see JDK APIs is unverified. Do not rely on either answer; the klib check settles it.
   - iOS linking and tests need a macOS runner. This is a budget item for the iOS phase, not now.
4. **Module layout matched to single-owner work:**
   - KMP modules:
     - `:core:model`;
     - `:core:protocol-oc1`, `:core:protocol-oc2`, `:core:paseo-acp`;
     - `:core:domain` (the gateway + `ServerCapabilities`);
     - `:core:data` (stores and migrations interfaces);
     - `:ui:kit` (the **only** module allowed to depend on material3);
     - `:feature:<name>` (screens + ViewModels).
   - Android modules: `:androidApp` (application id `io.github.eslamasabry.opencode_mobile`, signing, services, Glance widgets, notification and foreground-service code, `jniLibs`, first-launch migration of the Flutter data) and `:android:*` libraries for native bridges.
   - Desktop module: `:desktopApp`.
   - Enforce "UI never imports protocol modules" with Gradle dependencies, not by convention.
5. **Navigation: Nav3 1.2.0** (§4). Prototype predictive back + the container transform first, gating `sharedBounds` off during a live gesture.
6. **Kit on M3 1.5:**
   - `MaterialExpressiveTheme(motionScheme = MotionScheme.standard())` or a plain `MaterialTheme` with the 1.5 schemes.
   - Avoid or wrap the experimental Expressive pieces.
   - Ripple, stretch overscroll and haptics come from AndroidX defaults. Do not re-implement them.
7. **Strings:**
   - Compose resources for all UI copy (`values/`, `values-ar/`).
   - A small Android `res/values*/strings.xml` for manifest, widget and shortcut XML.
   - A one-off ARB→XML converter.
   - Check per-app language in slice 0.
8. **Build hygiene:**
   - `build-logic` convention plugins: `oc.kmp.library`, `oc.kmp.compose`, `oc.android.application`, `oc.android.library`, `oc.desktop.application`.
   - One version catalog.
   - Configuration and build cache on.
   - Per-host `gradle.properties` caps for the PC (2 workers, 2 GB).
   - Heavy, release and full-suite builds on the VPS.
   - Agents run target-specific tasks only.
9. **Linux desktop later:**
   - Ship `.deb` + tarball (parity with today), reusing the `.desktop` and metainfo.
   - Build a real AppImage from the app-image via `appimagetool`.
   - Flatpak after that (Gitnuro model), or Conveyor (free for MIT).
   - Test on GNOME Wayland with fractional scaling, and expect blur and tray gaps until SKIKO-28 is fixed.
10. **The in-place update is unaffected by KMP.**
    - `:androidApp` keeps the package name and signing key.
    - The shared modules' namespaces must differ from the app's (an AGP 9 rule).
    - R8 keep rules for `kotlinx.serialization` (Nav3 keys, protocol models) must reach the app. Publish consumer keep rules from KMP modules or put them in `:androidApp`.

---

## 12. Code-level notes (sketches; check against the pinned versions)

### 12.1 `gradle/libs.versions.toml` (core)

```toml
[versions]
kotlin = "2.4.21"
agp = "9.3.3"
cmp = "1.13.0-beta01"          # fallback "1.12.2"
cmp-material3 = "1.13.0-beta01" # fallback "1.12.0-alpha03"
cmp-adaptive = "1.3.0"
nav3 = "1.2.0"
navevent = "1.1.0"
lifecycle = "2.11.0"
savedstate = "1.4.0"
activity = "1.13.0"
ktor = "3.6.1"
serialization = "1.12.0"
coroutines = "1.11.0"
hotreload = "1.2.0"

[libraries]
compose-runtime    = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "cmp" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "cmp" }
compose-ui         = { module = "org.jetbrains.compose.ui:ui", version.ref = "cmp" }
compose-animation  = { module = "org.jetbrains.compose.animation:animation", version.ref = "cmp" }
compose-resources  = { module = "org.jetbrains.compose.components:components-resources", version.ref = "cmp" }
compose-uitest     = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "cmp" }
material3          = { module = "org.jetbrains.compose.material3:material3", version.ref = "cmp-material3" }
material3-adaptive-nav3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "cmp-adaptive" }
nav3-ui            = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "nav3" }
navevent-compose   = { module = "org.jetbrains.androidx.navigationevent:navigationevent-compose", version.ref = "navevent" }
lifecycle-vm-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-vm-nav3  = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
coroutines-swing   = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library     = { id = "com.android.library", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler     = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "cmp" }
compose-hotreload    = { id = "org.jetbrains.compose.hot-reload", version.ref = "hotreload" }
```

### 12.2 Convention plugin: one place decides targets (`build-logic/.../KmpLibraryConventionPlugin.kt`)

```kotlin
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)
            android {                       // AGP-KMP DSL (was androidTarget / androidLibrary)
                namespace = "io.github.eslamasabry.oc." + path.removePrefix(":").replace(':', '.')
                compileSdk = 37
                minSdk = 26
                withHostTest { }            // logic tests on the JVM
                optimization { consumerKeepRules { publish = true; file("consumer-rules.pro") } }
            }
            jvm("desktop")
            // iOS: off by default on Linux hosts; CI job passes -Poc.ios=true to compile klibs.
            if (providers.gradleProperty("oc.ios").orNull == "true") {
                iosArm64(); iosSimulatorArm64()
            }
            sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
        }
    }
}
```

Notes:

- Namespace: it must differ from the app's `io.github.eslamasabry.opencode_mobile` [V rule].
- Gating iOS behind a property costs one CI job. The alternative of always declaring the targets, which Linux then skips for linking, also works. Choose in slice 0 after timing both [U].

### 12.3 Nav3 shell with shared elements and predictive back (sketch)

```kotlin
@Serializable sealed interface Route : NavKey {
    @Serializable data object Conversations : Route
    @Serializable data class Chat(val serverId: String, val sessionId: String) : Route
}

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclassesOfSealed<Route>() }
    }
}

@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(navConfig, Route.Conversations)
    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sharedTransitionScope = this,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { KitMotion.forward() },          // kit tokens, 150–250 ms
            popTransitionSpec = { KitMotion.back() },
            predictivePopTransitionSpec = { edge -> KitMotion.predictiveBack(edge) },
            entryProvider = entryProvider {
                entry<Route.Conversations> { ConversationsScreen(onOpen = { backStack += it }) }
                entry<Route.Chat> { key -> ChatScreen(key) }  // sharedBounds gated off while a back gesture is InProgress
            },
        )
    }
}
```

`KitMotion` is a placeholder for the kit's own transition tokens. Check the exact parameter names (`sharedTransitionScope`, the `predictivePopTransitionSpec` lambda shape) against the 1.2.0 API when coding [U].

### 12.4 Frame-text cap at 1.3× (owner rule), applied in the kit

```kotlin
@Composable
fun FrameTextScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, fontScale = minOf(d.fontScale, max)),
        content = content,
    )
}
```

[U] On Android 14+, Compose applies non-linear font scaling through a converter attached to the platform density. A plain `Density(...)` may fall back to linear scaling for capped text. Check at 200% system font size against the Flutter behaviour.

### 12.5 Per-host `gradle.properties` (PC vs VPS)

```properties
# PC (8 CPU / 16 GB, shared with agents): small and serial-ish
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.compiler.execution.strategy=in-process
# VPS: -Xmx3g or more, default workers, plus kotlin.incremental.native=true once iOS targets exist
```

---

## 13. Unknowns (verify in slice 0 or watch)

1. **Dates:**
   - Jetpack M3 1.5.0 stable is beta02 as of 2026-10-21; no date.
   - CMP 1.13.0 stable is projected about mid-Dec 2026 [U].
   - Kotlin 2.5.0 is "planned December 2026"; its tested AGP and Gradle ranges are not published yet.
2. **compileSdk 37.1:** Jetpack Compose 1.13 requires it. Which AGP version accepts a minor compileSdk, and the exact DSL, are not confirmed. Check that AGP 9.3.3 builds CMP 1.13.0-beta01 on the VPS.
3. **AGP 9.5.0 + Kotlin 2.4.21:** outside the tested range (KGP lists AGP ≤ 9.3.1). Avoid until Kotlin 2.5.
4. **Predictive back + container transform under Nav3 1.2:** the exact hook to switch `sharedBounds` off during a gesture. Prototype first.
5. **Resources:** whether CMP resources follow Android per-app language automatically, and how `LocalResourceEnvironment` interacts with `AppCompatDelegate` locales.
6. **Reduced motion:** whether CMP on Android scales or disables animations under "Remove animations" / animator duration scale, or whether the kit must read the setting and provide a `LocalReducedMotion`. Plan for the kit to own it.
7. **`commonMain` JDK leakage:** whether android+jvm-only `commonMain` exposes JDK APIs. Sidestep it with an iOS klib compile in CI.
8. **`SavedStateHandle` in common ViewModels:** support details were not on the CMP ViewModel page.
9. **Linux:**
   - Native Wayland (SKIKO-28, open).
   - Fractional-scaling blur on GNOME/XWayland.
   - Tray (CMP-1847, CMP-4614).
   - AppCDS/AOT gains on Linux, and whether a shipped cache works.
   - jlinked package size and RAM.
   - A real AppImage and Flatpak recipe for CMP.
10. **iOS:**
    - Mac CI cost and availability.
    - Kotlin/Native min iOS 15 vs CMP's stated 14.
    - How far "native feel" can go with Compose on iOS vs SwiftUI shells for some surfaces.
11. **Compose Hot Reload MCP server:** maturity and usefulness for agents. It is experimental; trial it on the desktop target before building any workflow around it.
12. **Build-time numbers for this codebase size:** none measured. The 3,170-line demo says nothing about a ~100k-line Kotlin rewrite. Measure in slice 0: clean and incremental `:androidApp:assembleRelease` and `jvmTest` on the VPS and on the PC.

---

## Sources (all accessed 2026-11-01)

Official / primary:

- Compose Multiplatform releases (GitHub), including the 1.13.0-beta01 notes: https://github.com/JetBrains/compose-multiplatform/releases ; https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.13.0-beta01
- What's new in CMP 1.12 / 1.11 / 1.10 / 1.9: https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html ; https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html ; https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html ; https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
- CMP compatibility and versioning: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- KMP compatibility guide (KGP ↔ Gradle/AGP/Xcode table): https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- AGP 9 migration for KMP: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html ; JetBrains blog (Jan 2026): https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/
- Android-KMP library plugin: https://developer.android.com/kotlin/multiplatform/plugin
- Kotlin releases: https://kotlinlang.org/docs/releases.html ; What's new in 2.4.0: https://kotlinlang.org/docs/whatsnew24.html ; release blog: https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/
- Kotlin/Native host and target support (page dated 2026-08-19): https://kotlinlang.org/docs/native-target-support.html
- Kotlin/Native compile-time tips: https://kotlinlang.org/docs/native-improving-compilation-time.html ; Kotlin Gradle best practices: https://kotlinlang.org/docs/gradle-best-practices.html ; Gradle best-practice index: https://docs.gradle.org/current/userguide/best_practices_index.html
- AGP releases: https://developer.android.com/build/releases/gradle-plugin ; AGP 9.3.0 notes: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Gradle releases: https://gradle.org/releases/
- Jetpack Compose release table: https://developer.android.com/jetpack/androidx/releases/compose ; Compose UI notes: https://developer.android.com/jetpack/androidx/releases/compose-ui ; Material3 notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Navigation 3: https://developer.android.com/jetpack/androidx/releases/navigation3 ; https://developer.android.com/guide/navigation/navigation-3 ; animations: https://developer.android.com/guide/navigation/navigation-3/animate-destinations ; CMP Nav3: https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html ; CMP navigation: https://kotlinlang.org/docs/multiplatform/compose-navigation.html
- CMP ViewModel: https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html
- CMP resources: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html ; setup: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html ; usage: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html
- CMP UI testing: https://kotlinlang.org/docs/multiplatform/compose-test.html
- CMP native distributions: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html ; `TargetFormat.kt` source: https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/dsl/TargetFormat.kt
- CMP API doc `MaterialExpressiveTheme`: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-material-expressive-theme.html
- CMP iOS stable (May 2025): https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- YouTrack (Wayland and Linux issues, queried via API): https://youtrack.jetbrains.com/issue/SKIKO-28 , CMP-1847, CMP-4614, CMP-5101, CMP-9725, CMP-10993, SKIKO-1097
- Maven Central metadata (versions): https://repo1.maven.org/maven2/org/jetbrains/compose/ ; https://repo1.maven.org/maven2/org/jetbrains/androidx/ ; Google Maven group indexes: https://dl.google.com/android/maven2/
- Published Gradle module metadata, proving Android delegation: https://repo1.maven.org/maven2/org/jetbrains/compose/material3/material3-android/1.13.0-beta01/material3-android-1.13.0-beta01.module
- Case studies: https://kotlinlang.org/case-studies/?type=multiplatform ; CMP landing page: https://kotlinlang.org/compose-multiplatform/ ; use cases page (2025-12-10): https://kotlinlang.org/docs/multiplatform/use-cases-examples.html
- Google I/O 2025 KMP post (Google Docs iOS): https://android-developers.googleblog.com/2025/05/android-kotlin-multiplatform-google-io-kotlinconf-2025.html
- JetBrains Toolbox case study: https://blog.jetbrains.com/kotlin/2021/12/compose-multiplatform-toolbox-case-study/
- Flathub Gitnuro: https://flathub.org/apps/com.jetpackduba.Gitnuro ; Conveyor pricing: https://hydraulic.dev/pricing.html

Secondary (labelled [S] in the text):

- JetBrains talk "What's new in Compose Multiplatform" (App Dev Con Berlin, Oct 2026): https://speakerdeck.com/zsmb/whats-new-in-compose-multiplatform-next-app-dev-con-berlin-2026
- InfoWorld on CMP 1.12: https://www.infoworld.com/article/4216661/compose-multiplatform-1-12-0-welcomes-coding-agents-with-mcp-server.html
- AppCDS/AOT discussion (PR #5644 summary): https://slack-chats.kotlinlang.org/t/34186722/https-github-com-jetbrains-compose-multiplatform-pull-5644-p
- Compose Desktop X11-only thread: https://slack-chats.kotlinlang.org/t/34118029/i-got-frustrated-by-compose-desktop-being-x11-only-on-linux- ; GNOME XWayland fractional-scaling blur: https://discourse.gnome.org/t/is-there-any-way-i-could-fix-or-mitigate-the-blur-on-applications-using-xwayland-caused-by-fractional-scaling/17052
- compileSdk 37 AAR-metadata observation: https://github.com/anod/android-framework-lib/pull/2 ; AGP minimum for API 37: https://developer.android.com/build/releases/about-agp
- Kotlin 2.5.0-Beta1 announcement (Slack, 2026-09-23): https://slack-chats.kotlinlang.org/t/34448641/rocket-kotlin-2-5-0-beta1-the-first-preview-release-in-the-2
