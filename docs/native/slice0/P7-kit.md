# Slice 0 · P7: kit seams and screenshot tests

Date: 2026-11-01. Branch: `native/p7-kit-2026-11-01`, created from `claude/native-p7-kit-seams-482s6g` at `6627d320`.

Inputs read:
- `docs/native/slice0/P1-skeleton.md`, `native/AGENTS.md`;
- `docs/design/native-rewrite-2026-11-01/DRAFT-STATUS.md`;
- `plan/01-architecture-decision.md` §11 (P7 row, binding);
- `plan/06-ui-kit-design-system.md`: §1, §3–§6, §7.1–§7.4, §9 and §15.3;
- `plan/08-motion-animation.md` §2;
- `audit/codex-01-architecture-decision+03-architecture-modules.md`.

Flutter sources used for the tokens:
- `lib/ui/theme_roles.dart`, `lib/ui/app_theme.dart`;
- from `lib/ui/kit/`: `kit_tokens.dart`, `kit_text.dart`, `kit_shape.dart`, `kit_motion.dart`, `kit_chrome.dart`, `kit_frost.dart`, `kit_tappable.dart`, `kit_ink_layer.dart`, `kit_buttons.dart` and `kit_sheet.dart`;
- `assets/fonts/geist/`.

**Result.** P7's acceptance is met in this container for the parts it names, except the emulator check:
- **One kit part tested three ways.** `KitButton`, which is `KitTappable` plus frame text, is tested on desktop with `runComposeUiTest`, with Robolectric at SDK 37 on JDK 21, and with Roborazzi goldens in light/dark × en/ar.
- **The frame-text cap at 200 %** is exactly Flutter's rule, measured against Android's own non-linear converter. This needed a fix to plan 06 §9.2; see D1.
- **Haptics and ripple** reach the platform through the kit seam.
- **`KitSheetHost`** renders in-window.
- **Frost** draws a real `RenderEffect` blur at API 37 and is solid at API 30.

**Not done:**
- **"Matches Flutter on the emulator."** There is no emulator in this container.
- **"Goldens on the VPS."** The goldens were recorded in this container (see Open problems).

Shipping state: implemented, verified locally, committed and pushed to the branch. There is no PR, no CI run and no release.

---

## 1. What was built

Every change is in `native/ui/design` and `native/ui/kit`. Nothing else in `native/` was edited: not settings, not the catalog, not build-logic, not other modules.

### 1.1 `:ui:design` (pure Kotlin): the real tokens

The slice-0 placeholder `Tokens.kt` is replaced. The packages follow plan 06 §3.2.

| File | Content | Flutter source |
|---|---|---|
| `color/Argb.kt` | `Argb` value class, `argb()`, `withAlpha`, `lerp` (Dart `_mix`), `over` (`Color.alphaBlend`) | `dart:ui` |
| `color/Contrast.kt` | `relativeLuminance`, `wcagContrast` | `contrastRatio` |
| `color/Roles.kt` | `Roles` (24 roles, no red role) + `saver`; `Graphite.dark` / `.light`, every value copied | `theme_roles.dart` `graphiteDark/Light`, `ThemeRoles.saver` |
| `type/TypeRole.kt`, `type/TypeScale.kt` | 10 roles, 7 tones; the code type table (28/34 500 −0.28 … Mono 13/19); Arabic tracking 0; variable weights | `KitText.styleFor`, `AppTheme.forLocale` |
| `tokens/Space.kt`, `tokens/Shape.kt`, `tokens/Tokens.kt` | space 4…24; `KitShapeId` → radius (9/14/14/18/22/24/30, pill, circle); sizes (row 56/64, button 52, target 48, handle 36×5 in 22, …) with the Compact variant; `Alphas` (pressed .12, disabled .38) | `kit_tokens.dart`, `kit_shape.dart`, `KitInk.alpha` |
| `motion/MotionTokens.kt`, `motion/MotionPolicy.kt`, `motion/Haptic.kt` | plan 08 §2.7 tokens in full; Flutter curve control points (`CurveId`); `resolveMotion`; `KitHaptic` (moved here from the kit, plan 06 §3.2) | `kit_motion.dart`, plan 08 |
| `text/TextScalePolicy.kt` | `AppTextSize`, root cap 2.5, frame cap 1.3, `frameDp` (Flutter's per-size rule), `frameScale` / `readingScale`, `isLargeText` (≥ 1.29) | `kit_chrome.dart`, `AppTheme.maxTextScale` |
| `effects/FrostPolicy.kt`, `effects/Appearance.kt` | `FrostRole` (Composer, ChatHeader only); `FrostStrength` (σ / saturation / tint / header tint, stored names); `FrostHold`; `spec()` with dark relief; `blurRadiusPx` (sigma → Skia radius); `KitAppearance` (slice-0 subset) | `kit_frost.dart`, `glass_choice.dart` |

### 1.2 `:ui:kit`

| File | What it is |
|---|---|
| `platform/KitPlatform.kt` | The seam: `family`, `signals: StateFlow<KitSystemSignals>`, `isDebug`, `pressIndication(ink)`, `haptic(kind)`, `frostEffect(spec, density): RenderEffect?`, `frameDensity(base, cap)`. |
| `androidMain/…/AndroidKitPlatform.kt` | Keeps the P1 constructor `AndroidKitPlatform(view)`. Press feedback: M3 `ripple(bounded = true, color = ink)`. Haptics: the plan 06 §4.9 constants through `View.performHapticFeedback` (Send → `VIRTUAL_KEY`, Done → `CONFIRM` on API 30+, Commit → `KEYBOARD_TAP`, LongPress, and the toggle/segment ticks on API 34+ with `CLOCK_TICK` below). P1's map was wrong and is fixed here. Signals: a live `ANIMATOR_DURATION_SCALE` observer, plus power save and 24-hour read once. |
| `androidMain/…/AndroidFrost.kt` | API 31+: `RenderEffect.createBlurEffect` followed by a saturation `ColorMatrix`. This is the only blur in the app. |
| `desktopMain/…/DesktopKitPlatform.kt` | Press and hover fill instead of a ripple, no haptics, a fine pointer, and frost drawn solid. |
| `KitTheme.kt` | Graphite roles (plus Saver), `KitColors`, Geist `KitTypography`, `Tokens` (Saver uses Comfortable), motion resolved from the signals, layout direction from `localeTag`, the root text density (2.5×), `LocalKitAppDensity`, the 12 % `RippleConfiguration`, selection colours and the bridged `MaterialTheme`. Every parameter has a default, so `:ui:shell`'s `KitTheme { … }` compiles unchanged. |
| `theme/KitColors.kt`, `KitTypography.kt`, `KitLocals.kt`, `MaterialBridge.kt` | Roles → `Color`. Geist and Geist Mono variable fonts are instantiated per weight with `FontVariation.weight`, with the system sans for Arabic. The kit locals. `MaterialBridge` ports Dart `schemeFromRoles` slot for slot, with the M3 shapes 9/14/18/22/30. |
| `composeResources/font/*.ttf`, `files/licenses/geist_ofl.txt`, `fonts/PROVENANCE.md` | The four Geist TTFs, byte-for-byte copies; their SHA-256 values are in `PROVENANCE.md`. |
| `foundation/KitTappable.kt` | `KitTappable(onClick, …, disabledReason, role, onClickLabel, onLongClick, onLongClickLabel, haptic, shape, ink)` and the internal `Modifier.kitTappable`. It uses `combinedClickable(hapticFeedbackEnabled = false)` with a 48 dp minimum and is clipped to its shape. A haptic plays on tap only when `haptic` is passed. Long-press always plays `LongPress`, performed by the kit. Disabled → `stateDescription = disabledReason`, and debug builds assert that a reason is given. |
| `foundation/KitPress.kt` | `KitFillIndication`: a pressed / focused / hovered fill at 12 / 10 / 8 % that stays visible at least `PRESS_HOLD` (100 ms). It is used on desktop and under reduced motion. |
| `foundation/KitFrameText.kt`, `KitTextScale.kt`, `KitText.kt` | Frame vs reading text (see D1). `KitText(text, modifier, role, tone, kind, maxLines, textAlign)`. Reading text uses `TextDirection.Content` (K17). The P1 overload `KitText(text, KitTextRole)` is kept for `:feature:chats`. |
| `foundation/KitFrost.kt` | `rememberKitFrostState()`, `Modifier.kitFrostSource(state)` (records into a `GraphicsLayer`), and `Modifier.kitFrost(role, state)`. With an effect: the blur of the recorded layer, then the tint at the strength's alpha, then the role's edge line. Without one: solid. |
| `modal/KitSheet.kt` | `KitSheetHost(modifier) { … }`, `KitSheetHostState`, `LocalKitSheetHost`, and `KitSheet(visible, onDismissRequest, title, closeLabel, subtitle, height, body)`. The sheet is in-window and holds one at a time, so a second sheet replaces the first. It has a scrim tap, a handle, a header (frame text) with Close at the end, a 640 dp cap and a 90 % height cap. It enters on the spatial spring with a `STANDARD` scrim fade, and snaps under Off. |
| `motion/KitMotion.kt` | `KitMotionState`, `KitEasing` (Flutter control points), and `KitMotionSpecs` (`enter`, `quick`, `exit`, `spatial`, `largeTravel`). |
| `KitParts.kt` | `KitScreen` (same signature as P1; it now places the screen's `KitSheetHost`) and `KitButton` (accent face; disabled is `surface3` with `text3`; plus `disabledReason` and `haptic`). Both are built only from kit parts. |

### 1.3 Tests

| Way | Suite | Count | What it proves |
|---|---|---|---|
| — | `:ui:design` `DesignTokensTest` (desktopTest) | 9 | B-KIT-003: Graphite contrast floors in both brightnesses. B-KIT-056: no red role. B-KIT-008: Saver. `alphaBlend` parity. B-KIT-052: the type table. B-KIT-038: compact steps. B-KIT-023: motion resolution. B-KIT-018: frost specs and holds, and the radius↔sigma conversion. B-KIT-059: frame and reading scale. |
| 1 | `:ui:kit` `KitSeamsDesktopTest` (desktopTest, `v2.runComposeUiTest`, headless Skia) | 7 | A tap plays only its meaning's haptic, a plain tap is silent, and a long-press plays LongPress. A disabled tappable states why and does not act. Frame text is 26 dp at 2.0 while reading text is 48 dp per line. The root cap holds at 3.0. A sheet opens and closes from Close and from the scrim. A second sheet replaces the first. Frost is solid without an effect and blurred through one. |
| 2 | `AndroidKitPlatformTest` (Robolectric 4.17, SDK 37, JDK 21) | 2 | The real `AndroidKitPlatform` sends `VIRTUAL_KEY`, then `LONG_PRESS`, to the `View`, and a plain tap sends nothing. The constant table at API levels 29, 30, 33 and 34. |
| 2 | `TextScaleCapTest` (Robolectric, `@Config(fontScale = 2.0 / 1.3 / 1.0)`) | 3 | Each kit text has the same rendered width as a reference drawn at the expected dp. The expectations come from Android's own converter (`Density(context)`); see §3. |
| 2 | `KitFrostFallbackGoldenTest` (SDK 30 and 37) | 2 | `frostEffect` is null below API 31 and non-null at 37. Also captures the API 30 golden. |
| 3 | `KitGoldensTest` (Roborazzi 1.77.0, `@GraphicsMode(NATIVE)`, 412×915 dp at 420 dpi) | 16 | `KitButton` states, frame/reading text, `KitSheet` open on `KitScreen`, and the frost header, each in light-en, dark-en, light-ar and dark-ar. |

The scenes live in `commonTest/KitScenes.kt` and the desktop test and the goldens share them, so the three ways exercise the same composition. `FakeKitPlatform` (plan 06 §6) is in `commonTest` because `:ui:testing` does not exist yet.

---

## 2. Environment set-up

| Step | Command and result |
|---|---|
| SDK tools | `curl -O https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip`; SHA-1 is `e025545c62a8e64c7559119566a569fb1dec5f60`. Unzipped to `/opt/android-sdk/cmdline-tools/latest`. |
| Licences, packages | `yes \| sdkmanager --licenses`, then `sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "platforms;android-37.1" "build-tools;37.0.0" "build-tools;36.1.0"`. These match `Sdk.kt` (compileSdk 37, minor 1) and AGP 9.3.3's default build-tools. |
| `ANDROID_HOME` | `/opt/android-sdk`; `native/local.properties` holds `sdk.dir` and is gitignored. |
| JDKs | JDK 21.0.12.1 was present, and Gradle runs on it. `openjdk-17-jdk-headless` 17.0.20.1 was installed as the build toolchain. `~/.gradle/gradle.properties` lists both JDKs and turns auto-download off. |
| Timeouts | `~/.claude/settings.json` sets `BASH_DEFAULT_TIMEOUT_MS=1800000` and `BASH_MAX_TIMEOUT_MS=3600000`. They take effect in the next session; this session used explicit timeouts. |
| Maven Central | Central answered HTTP 429 through this sandbox's proxy, the same as in P1. A container-only init script, `~/.gradle/init.d/central-mirror.gradle.kts`, puts Google's Central mirror (`maven-central.storage-download.googleapis.com/maven2`) first and drops `repo.maven.apache.org`. The same as P1; it is not in the repo. |
| Robolectric jars | Robolectric fetches `android-all-instrumented` itself from Central, which returned 429 here. Both jars were seeded into `~/.m2/repository` from the mirror, and Robolectric checks that folder first: `17-robolectric-15733970-i7` (SDK 37, SHA-1 `42dc8bf9…5b74`, matching the published `.sha1`) and `11-robolectric-6757853-i7` (SDK 30, SHA-1 `2685e41b…6ac6`). Container only. |

---

## 3. Commands and results

All commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`. The host has 4 vCPUs and 15 GB of RAM. The repo's caps applied: `-Xmx2g` and 2 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew -p build-logic test` | `ModuleRulesTest` 19/19 |
| 2 | `./gradlew -Poc.werror=true :ui:kit:compileKotlinDesktop :ui:kit:compileAndroidMain :ui:design:compileKotlinDesktop` | BUILD SUCCESSFUL, no warnings |
| 3 | `./gradlew -Poc.werror=true :ui:design:desktopTest :ui:kit:desktopTest` | 9 + 7 passed |
| 4 | `./gradlew :ui:kit:recordRoborazziAndroidHostTest` | 19 PNGs written to `ui/kit/src/screenshots/` (about 40 s with a warm cache) |
| 5 | `./gradlew :ui:kit:verifyRoborazziAndroidHostTest` | 23 host tests passed against the committed goldens |
| 6 | Copied `states-dark-en.png` over `states-light-en.png`, then ran #5 for `kitButton` only | `kitButton[light-en] FAILED`, so verify does compare pixels. Restored the original byte for byte. |
| 7 | `./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin` | BUILD SUCCESSFUL. The APK `androidApp-preview-debug.apk` is 14,171,046 bytes; it now includes Geist. |
| 8 | `./gradlew :quality:test` | 16/17 pass: Konsist 11, KitOnlyPlanted 2, GraphSmoke 2 and DesktopShellUiTest 1. P1's shell test still passes against the new kit. **`ModuleOwnersTest` fails, but not because of P7:** `MODULE_OWNERS` has no rows for `:tools:arb2xml` and `:tools:flutter-migration-readers`, which came with the P5/P6 merges. The coordinator owns that file, so it is left for them. |
| 9 | `@Config(fontScale = 2f)` probe of Android's converter at SDK 37 (density 2.625) | See the table below |

Android's own sp → dp at SDK 37 (Robolectric runs the platform's `FontScaleConverter`):

| scale | 13 | 16 | 20 | 22 | 24 | 28 | 34 |
|---|---|---|---|---|---|---|---|
| 1.15 | 15.10 | 18.10 | 21.80 | 23.50 | 25.20 | 28.40 | 34.00 |
| 1.3 | 17.20 | 20.20 | 23.60 | 25.00 | 26.40 | 28.80 | 34.00 |
| 2.0 | 25.00 | 28.00 | 34.00 | 35.00 | 36.00 | 37.33 | 41.54 |

The frame text the kit renders follows Flutter's rule, `min(platform, 1.3 × spec)`. `TextScaleCapTest` asserts each case:

| Case | Size |
|---|---|
| Button 16 sp at 200 % | 20.8 dp; reading text at 200 % is 28.0 dp (non-linear) |
| Title 22 sp at 200 % | 28.6 dp |
| Title 22 sp at 130 % | 25.0 dp, the platform's own non-linear value, not the linear 28.6 |
| Secondary 13 sp at 130 % | 16.9 dp; Android alone would give 17.2, above 1.3× |

---

## 4. Versions actually resolved (from Gradle dependency reports)

| Area | Resolved |
|---|---|
| Roborazzi | `roborazzi`, `-compose`, `-junit-rule`, `-core(-android)` and `-painter(-jvm)` are all 1.77.0. The Gradle plugin `io.github.takahirom.roborazzi` is also 1.77.0, applied from the catalog alias. |
| Robolectric | 4.17. SDK 37 runs on `android-all-instrumented:17-robolectric-15733970-i7` (Robolectric's `DefaultSdkProvider` maps API 37 to Android 17, build 15733970). SDK 30 runs on `11-robolectric-6757853-i7`. |
| Compose test | `org.jetbrains.compose.ui:ui-test-junit4` 1.13.0-beta01 resolves to `androidx.compose.ui:ui-test-junit4(-android)` 1.13.0-beta01. `androidx.compose.ui:ui-test-manifest` is 1.13.0-beta01. Desktop uses `org.jetbrains.compose.ui:ui-test` 1.13.0-beta01. |
| Compose / M3 on Android | `androidx.compose.ui:*` 1.13.0-beta01; `androidx.compose.material3:material3` and `material3-ripple` 1.5.0-beta01. |
| Desktop natives | `org.jetbrains.skiko:skiko-awt-runtime-all` 0.156.0. It arrives through CMP `ui`, so `currentOs` is not needed. |
| Others | `junit:junit` 4.13.2, `androidx.test:core` 1.7.0. Kotlin, AGP, Gradle, Metro and CMP are unchanged from P1 (2.4.21 / 9.3.3 / 9.7.1 / 1.5.1 / 1.13.0-beta01). |
| JDKs | Gradle and the host-test launcher run on 21.0.12.1. Module bytecode targets 17 (17.0.20.1). |

---

## 5. Deviations from the plan, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| **D1** | 06 §9.2: `ScaledFontDensity` overrides `TextUnit.toDp()` to return `min(platform, cap × spec)`, and `KitFrameText` provides it | `scaledFontDensity()` changes **only the font scale** (root 2.5×; `KitFrameText` 1.3× as a fallback). Frame `KitText` computes `TextScalePolicy.frameDp(spec, platform)` from the uncapped `LocalKitAppDensity` and lays its words out at that exact size under font scale 1, with line height and tracking scaled in proportion. | **Measured:** Compose lays text out with the layout coordinator as its `Density`, and the coordinator forwards only `density` and `fontScale`. A custom `toDp` never reaches text on Android, which re-derives sizes from `fontScale` through its own converter. With the plan's class, 16 sp at 200 % rendered at 20.2 dp, not 20.8. Desktop is linear, which hides the problem. Capping the scale alone (the plan's "linear cap" fallback, or `FontScaleConverterFactory`) would leave frame titles up to 21 % smaller than Flutter (28 sp → 28.8 dp, against Flutter's 36.4) and would let 13 sp exceed 1.3×. The per-size path matches Flutter exactly. Non-`KitText` content inside `KitFrameText` (icons sized from text) still gets the scale cap. |
| D2 | 06 §4.8 vs 08 §2.2: `Enter` = androidx `EaseOutCubic` (08) or Flutter's control points (06) | Flutter's control points: `CurveId.Enter = (0.215, 0.61, 0.355, 1)`. | 06 is right: androidx `EaseOutCubic` is (0.33, 1, 0.68, 1), a different curve. 08 §2.2 needs correcting. |
| D3 | 06 §4.3: Compact two-line row 54 | 56 (64 − 8) | Dart `KitTokens._styled` computes `rowHeightTwoLine - space2`, and the code wins (06 §4, rule 0). |
| D4 | 06 K9: the sheet is built on the M3 1.5 in-window `BottomSheet` | Drawn by the kit: scrim, panel, handle, `AnimatedVisibility` on the kit springs. | It keeps the goldens deterministic and the API small for slice 0. M3's predictive-back sheet preview arrives with the M3 sheet in WP K1 (see open problem 4). |
| D5 | 06 §6: `@Composable hapticPerformer()`; `AndroidKitPlatform(context, links, content, buildInfo)` | `fun haptic(kind)`; the constructor stays `AndroidKitPlatform(view)` | `:androidApp`'s `MainActivity` builds it with `window.decorView`, and P7 may not edit `:androidApp`. Haptics go through that `View`, which honours the system Touch feedback setting. |
| D6 | 06 §6: the full `KitPlatform` (system bars, frame rate, formats, clipboard, links, content slots, `dynamicSource`) | Only the P7 members | The rest arrive with the parts that use them, so nothing is frozen early. The freeze belongs in `contracts/kit-v1.md`. |
| D7 | 06 §3.2: `KitTheme`, `KitText`, `KitButton` and `KitScreen` live in sub-packages (`…ui.kit.theme`, `…foundation`) | They stay in `…ui.kit`. The new seams are in `foundation`, `modal`, `motion`, `theme` and `platform`. | `:ui:shell` and `:feature:chats` import them from `…ui.kit`, and P7 may not edit those modules. The move belongs to the contract freeze. |
| D8 | `KitSheet` takes its words from the kit | `KitSheet(…, closeLabel, …)` | The kit has no strings yet, and its words would come from `:ui:words`, which P7 may not edit. |
| D9 | Ripple at 12 % via `RippleConfiguration(rippleAlpha = …)` | Kept, with `@Suppress("DEPRECATION")` | M3 1.5.0-beta01 deprecates the per-state alphas and offers no replacement that sets them; M3's own pressed alpha is 10 %. `Locale.current` (the default `localeTag`) is suppressed the same way; the shell should pass `oc.appLocale`. With these two suppressions the build is clean under `-Poc.werror=true`. |
| D10 | 06 §15.3: `KitPreviewCaptureTest` scans `@Preview`s with ComposablePreviewScanner | Explicit scenes (`KitScenes.kt`), parameterised by light/dark × en/ar | The scanner is not in the catalog, and slice 0 has four parts. Scenes keep the "three ways test the same composition" property. |
| D11 | Test-only artifacts come from the catalog | `ui-test-junit4`, `ui-test-manifest` and the Roborazzi libraries are written as coordinates in `ui/kit/build.gradle.kts`, with versions read from the catalog's `cmp` and `roborazzi` pins | The brief keeps the catalog unchanged. The build-logic owner should add catalog aliases. |
| D12 | Robolectric JVM flags belong in the convention | `ui/kit/build.gradle.kts` adds `--add-exports/--add-opens java.base/jdk.internal.access`, `java.io` and `java.lang` to `*AndroidHostTest*` tasks | Robolectric 4.17's FileDescriptor interceptor fails on JDK 21 without them ("cannot access jdk.internal.access.SharedSecrets"). AGP's unit-test tasks add these flags, but AGP-KMP host tests do not. This should move to `oc.kmp.library` (audit #10). |
| D13 | 06 §15.3: goldens are recorded on the VPS only | Recorded in this container | The brief asked for committed goldens. Re-record them on the VPS before they gate anything (open problem 2). |
| D14 | `KitAppearance` has every field (pack, accent, style, knobs, glow, live) | A subset: mode, text size, motion, haptics, density, frost | The packs, accents and Style resolver are WP K1/K4. They join as defaulted fields. |
| D15 | 06 §4.4: Arabic roles may need +2 sp line height | Not added | The Roborazzi Arabic captures, with diacritics (`مُحَمَّدٌ`, `إِنْ شَاءَ ٱللَّٰهُ`), do not clip at Body or RowTitle. |

Plan statements to correct before slice 1:
- 06 §9.2: replace the `ScaledFontDensity` sketch (D1).
- 06 §6.1: the `JETPACK_AHEAD` row for `FontScaleConverterFactory` is not needed.
- 08 §2.2: `Enter` and `Exit` easings (D2).
- 06 §4.3: Compact 48/56 (D3).
- 06 §15.3: the task names are now known: `recordRoborazziAndroidHostTest` / `verifyRoborazziAndroidHostTest` / `compareRoborazziAndroidHostTest` (also `…Desktop`). This settles P-03-4.

---

## 6. Audit fixes applied

| Audit item | P7 |
|---|---|
| MAJOR 10: test toolchains | Robolectric runs on the JDK 21 launcher that P1 set up. The missing `--add-exports` was found and fixed (D12). This proves "Robolectric on JDK 21 at SDK 37". |
| BLOCKER 2: compileSdk 37.1 | The kit compiles and its host tests run against `platforms;android-37.1`. Robolectric runs SDK 37. |
| MAJOR 13: no alpha artifacts at the switch | P7 adds no alpha artifact. The new test-only betas are `ui-test*` 1.13.0-beta01; the runtime betas are unchanged from P1. |
| Others | Not applicable to the kit seams. |

---

## 7. Goldens committed, for the owner to look at

All are under `native/ui/kit/src/screenshots/`. They were recorded with Robolectric 4.17 native graphics at SDK 37 (one at SDK 30): 412×915 dp at 420 dpi, Solid, Graphite, text 1.0, motion Off. The frost scene uses motion Full, because reduced motion holds frost solid.

| Part | Files |
|---|---|
| `KitButton/` | `states-light-en.png`, `states-dark-en.png`, `states-light-ar.png`, `states-dark-ar.png`: primary (accent), disabled (`surface3` with `text3`), and a tappable row |
| `KitFrameText/` | `scale-1.0-{light-en,dark-en,light-ar,dark-ar}.png`: frame Button line and reading Body and RowTitle, with Arabic diacritics. `button-scale-2.0-light-en.png`: the frame label at 200 % (20.8 dp) |
| `KitSheet/` | `open-{light-en,dark-en,light-ar,dark-ar}.png`: a full screen with the scrim and the in-window sheet; in RTL, Close is on the left |
| `KitFrost/` | `header-api37-{light-en,dark-en,light-ar,dark-ar}.png`: real blur of the stripes under the ground tint (80 % light, 70 % dark). `header-api30-light-en.png`: the solid fallback |

What I checked by eye:
- Geist loads (it is visibly not Roboto).
- Arabic is shaped and right-to-left, in the system sans.
- The sheet's handle, radius 30 and 640 cap look right.
- The API 37 blur differs from the API 30 solid.

They have **not** been compared with Flutter goldens or a device.

---

## 8. Open problems

1. **No emulator check.** P7's "matches Flutter on the emulator" still needs four things on the PC or the VPS emulator:
   - ripple and haptic on a real device;
   - frame text at 200 % next to the Flutter app;
   - frost on the composer and header;
   - sheet drag and back.
2. **Goldens are not from the VPS** (D13). Re-record them there with `:ui:kit:recordRoborazziAndroidHostTest`, then compare with this set; Robolectric native graphics may differ by host. Each Roborazzi bump needs its own re-record commit.
3. **`ModuleOwnersTest` is red on the base branch,** because two `:tools:*` modules are missing from `MODULE_OWNERS`. That is coordinator work.
4. **The sheet has no Back or predictive back, and no drag-to-dismiss.** The topmost overlay should own Back through `NavigationBackHandler` (06 §7.4). That needs `navigationevent-compose` on the kit's classpath and the P2 predictive-back decision. The M3 1.5 in-window sheet (D4) would bring drag and predictive back; try it in WP K1.
5. **Frost is not wired into a screen.** `KitScreen` does not hoist `KitFrostState` yet. Also missing: the seeded 3 % noise tile and the 180 ms `FROST_FADE` as content scrolls under. Both are WP K1/KC2.
6. **Signals are partial.** Power save and 24-hour are read once. Thermal, high contrast, a mouse and touch exploration are not observed. The animator observer is registered for the platform object's lifetime, which is one per activity.
7. **Not attempted:**
   - `MaterialBridgeTest` against Dart-generated JSON (06 §5.4);
   - the glyph generator and `KitIcon` (Close is a word, not an X icon);
   - `KitPreview` / `PreviewKitPlatform`;
   - the `:desktopApp` gallery;
   - Konsist rules for `ripple(` and `RenderEffect` outside their files (06 §2);
   - the contract freeze of the 20 signatures in `contracts/kit-v1.md`.
8. **The catalog and convention edits proposed for the build-logic owner:**
   - aliases for `ui-test-junit4`, `ui-test-manifest` and the Roborazzi libraries (D11);
   - the Robolectric JVM flags in `oc.kmp.library` (D12).
