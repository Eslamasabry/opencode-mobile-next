# 12. Package registry: what replaces every Flutter package

Written 2026-11-01. This section decides **which library (or no library) does each job** in the native app. It sits
under `01-architecture-decision.md` (the ADR, binding) and next to `03-architecture-modules.md` §5, which holds the
exact `libs.versions.toml`. The ADR wins over this file; this file wins over the catalog on *which* library is used;
the build-logic owner keeps the two in step (`RegistryRowTest`, §12).

## For the owner, in five lines

1. All 43 Flutter packages now have a named native answer: 13 become features already built into Android or Kotlin,
   16 become a native library, 4 become a little code of our own, 8 are simply dropped, and 2 (FactsBook only) wait
   until after the switch.
2. The phone download should shrink from about 36 MB to about 17–19 MB; roughly 10 MB of that is the offline voice
   engine, which stays inside the app on purpose.
3. Nothing from Google Play Services, Firebase, ML Kit, Shorebird or any analytics company can enter the app: a build
   check fails if one sneaks in, and every library's licence is checked automatically.
4. Two things need fixing on the way: the terminal view must be lightly forked so server terminals can show, and the
   licence notices for the built-in Linux tools (proot) are missing today and must be added.
5. Libraries change only for a named reason (a monthly window, or a security fix within 72 hours), and every change
   carries a checksum, a licence check and a size report. Yours to decide: R12-1 (analytics) and the notice wording (R12-8).

Tags: **[V]** checked today against Maven Central / Google Maven metadata, a POM, a GitHub release or the library's
source. **[R]** read in the repo, a map, a research file, the ADR or memory. **[I]** measured by a research file on the
real artifact. **[U]** unverified; the named proof settles it. **[est]** my estimate. Row ids: `R-nn` (pubspec
rows), `K-nn` (existing Kotlin-side Gradle dependencies), `C-nn` (bundled components), `N-nn` (new native-only
dependencies), `W-nn` (write ourselves), `X-nn` (banned). Behaviour ids like `B-SEC-064` are rows of
`../catalog/BEHAVIORS.md`.

---

## 0. How to use this section

### 0.1 Verdict words

| Verdict | Meaning |
|---|---|
| **LIB** | Replaced by a native library (named, pinned, with Maven coordinates). |
| **PLATFORM** | Replaced by an API that is already there: the Android SDK, the Kotlin standard library, Compose itself. No new dependency. |
| **OWN** | Replaced by our own small code (`W-nn`, §6), because no library fits or the format must stay ours. |
| **DROP** | Not needed natively. Nothing replaces it. |
| **LATER** | Needed only by a feature that ships after the switch (ADR §12 D6) or in the desktop/iOS phase. Recorded now so nobody picks a different library later. |

### 0.2 Risk scale

| Risk | Meaning |
|---|---|
| Low | Stable release, maintained by Google, JetBrains, Square or the platform, or already proven in the demo or the Flutter app's Kotlin. |
| Medium | 0.x, young or single-maintainer, or semantics that bite (defaults, R8). Always behind one of our interfaces with a named fallback. |
| High | Not on Maven Central, a fork, a pre-release at the switch, or a data-loss or security-critical path. Needs a slice-0 or device proof before agents build on it. |

### 0.3 Size conventions

"APK cost" is the **arm64-v8a split, compressed** (the file users download), unless marked otherwise. Numbers come
from the measured 4511 APK (`../map/packages-build.md` PACKAGES-BUILD-07) [R], from research measurements [I], or from
the raw artifact size on Maven today [V raw], which is an upper bound before R8 shrinks it.

### 0.4 What an agent does before adding any dependency (step list)

1. Search this file and `native/docs/DEPENDENCIES.md` (the living copy of §1–§7). If a row exists, use exactly that
   library and version. If the library is in §7 (banned), stop.
2. If no row exists, write a proposed row in your slice's `contracts/<slice>.md` with every column of Table D:
   coordinates, version, targets, licence, raw size, risk, the module that declares it, and what it replaces.
3. The build-logic owner (ADR §5.2, coordinator) adds it to `native/gradle/libs.versions.toml`, to
   `ExternalRules` (03 §6, extended in §12.2 here), and to `DEPENDENCIES.md`, in one commit.
4. Run the per-change checklist in §10.4. A dependency that adds more than **500 KB** to the arm64 APK needs the
   coordinator's sign-off in the PR text.
5. Never declare a version in a module's `build.gradle.kts`; never use `+`, ranges or `latest.release` (§7, X-26).

---

## 1. The registry: every direct pubspec dependency (Table A)

Source: `/home/eslam/Storage/Code/wt/oc_app-batch1/pubspec.yaml` (app `2.1.0+57`) and `pubspec.lock` resolved
versions [R]. 36 runtime + 7 dev = 43 direct dependencies (the packages-build map counts the same 43). "Used for" gives
lib/test file counts from the map's import grep. **A/I/D** = Android / iOS / desktop (JVM): ✓ supported now,
`later` = a named later path, – = not applicable.

| # | Flutter package (resolved) | Used for (lib/test files) | Verdict | Native replacement and pin | Maven coordinates | A/I/D | Licence | APK cost (today → native) | Risk | Declared in |
|---|---|---|---|---|---|---|---|---|---|---|
| R-01 | `flutter` SDK (Shorebird engine 3.47.1) | the whole UI toolkit and engine (all) | LIB | Compose Multiplatform **1.13.0-beta01** (on Android = Jetpack Compose 1.13.x); fallback 1.12.2; must be a stable 1.13.x at the switch (D12) | `org.jetbrains.compose.runtime:runtime`, `org.jetbrains.compose.foundation:foundation`, `org.jetbrains.compose.ui:ui`, `org.jetbrains.compose.animation:animation` + plugin `org.jetbrains.compose` | ✓/✓/✓ | Apache-2.0 | libflutter 6.68 + libapp 12.90 MB → about 2–2.5 MB of dex for Compose, M3, Nav3 and Ktor together (the demo APK was 2.5 MB) [R] | Medium | `oc.kmp.compose` modules |
| R-02 | `flutter_localizations` SDK | Material/Cupertino delegates, RTL, locale plumbing | LIB | CMP resources (`strings_<feature>.xml`, `<plurals>`) in `:ui:words`; per-app language through `LocaleManager` on API 33+ and a configuration override on 26–32 (06 K16) | `org.jetbrains.compose.components:components-resources` 1.13.0-beta01 | ✓/✓/✓ | Apache-2.0 | 6.9 MB of generated Dart source → plain resource XML [R] | Medium (P6) | `:ui:words` |
| R-03 | `intl` 0.20.3 (`any`) | dates, numbers, currency, plurals beside gen-l10n, relative times (34/2) | PLATFORM | plurals → CMP `<plurals>` (CLDR); numbers, currency, dates and times → `LocaleFormats` (W-03) over `android.icu.text.*` (API 24+); relative times from `:ui:words` plurals as today; bidi → `KitBidi` port (W-15) | none (platform `android.icu`; `java.text` on desktop; `NSFormatter` on iOS later) | ✓/later/✓ | — | 0 | Medium (B-L10N-012 digit and currency parity) | `:core:common` (a/d) |
| R-04 | `cupertino_icons` 1.0.9 | nothing: zero imports (0/0) | DROP | — | — | — | MIT | −0.26 MB font [R] | None | — |
| R-05 | `clock` 1.1.2 | injectable time for `KitSince` and tests (38/67) | PLATFORM | `kotlin.time.Clock` (stable since Kotlin 2.3.0 [V]) behind `:core:common` `Clock`; `TestClock` in `:core:testing` | stdlib | ✓/✓/✓ | Apache-2.0 | 0 | Low | `:core:common` |
| R-06 | `desktop_drop` 0.8.4 | desktop file drop; its Android plugin class compiles but is never used (1/0) | DROP | desktop phase: CMP `Modifier.dragAndDropTarget` (built in) | — | –/–/later | Apache-2.0 | removes one Gradle subproject and its KGP warning | None | `:desktopApp` (later) |
| R-07 | `dio` 5.11.1 | every HTTP call, SSE, the perf interceptor (25/38) | LIB | Ktor client **3.6.1** + OkHttp **5.5.1** engine (hardened builder W-06 in `:core:net-okhttp`); own SSE loop; own `PerfTracePlugin` (W-16) | `io.ktor:ktor-client-core`, `io.ktor:ktor-client-okhttp`, `io.ktor:ktor-websockets`, `com.squareup.okhttp3:okhttp` | ✓/later (`ktor-client-darwin`)/✓ | Apache-2.0 | 3.6 MB raw jars [V raw] → about 0.6–0.9 MB after R8 [est] | Medium (OkHttp retry and redirect defaults) | `:core:net`, `:core:net-okhttp`, `:protocol:*` |
| R-08 | `file_picker` 12.2.0 | attach, import, export, save (5/1) | PLATFORM | `ActivityResultContracts.OpenDocument`, `OpenMultipleDocuments`, `CreateDocument(mime)` behind `ActivityResults` (ADR §7.3) | `androidx.activity:activity` 1.13.0 (already present) | ✓/later (FileKit 0.17.0)/later (FileKit) | Apache-2.0 | 0 | Low | `:android:platform` |
| R-09 | `flutter_riverpod` 3.4.3 | provider graph / service locator around the controllers (82/197) | LIB | Metro **1.5.1** (compile-time DI, `AppScope`, `ConnectionScope`) + ViewModel and `StateFlow` (lifecycle **2.11.0**); plan B Koin 4.2.2 | `dev.zacsweers.metro:runtime`, `dev.zacsweers.metro:metrox-viewmodel-compose`, plugin `dev.zacsweers.metro`; `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | ✓/✓/✓ | Apache-2.0 | Metro runtime 152 KB raw [V raw] | Medium (compiler plugin moves with Kotlin) | annotations anywhere; graphs in apps |
| R-10 | `flutter_timezone` 5.1.0 | IANA zone id for usage days and reminders (2/0) | LIB | `kotlinx.datetime.TimeZone.currentSystemDefault().id` (kotlinx-datetime **0.8.0**) | `org.jetbrains.kotlinx:kotlinx-datetime` | ✓/✓/✓ | Apache-2.0 | shared with the rest of the app | Low | any KMP module |
| R-11 | `highlight` 0.7.0 | colours in code blocks and diffs (2/0) | LIB | SyntaxMP **0.3.0** behind our `CodeTokenizer` (W-17); benchmark against Highlights 1.1.0; fallback kotlin-textmate 0.3.0 (A/D only) or plain text | `com.gallatinapps.syntaxmp:syntaxmp-tokenizer` | ✓/✓/✓ | Apache-2.0 [V POM] | 554 KB raw [V raw] | Medium–High (4 stars, young; Apache-2.0 so we can vendor it) | `:ui:kit-content` |
| R-12 | `flutter_svg` 2.3.0 | SVG file preview; `KitIcon`; bundled marks (3/6) | LIB + OWN | previews: our `StaticSvg` sanitizer (W-05), then Coil **3.6.3** `coil-svg` (AndroidSVG 1.4 on Android, Skia elsewhere); icons and marks: generated `ImageVector`s (06 §4.11, C-02, C-03) | `io.coil-kt.coil3:coil-svg` (pulls `com.caverock:androidsvg-aar:1.4`) | ✓/✓/✓ | Apache-2.0 [V POM] | 23 KB + AndroidSVG 198 KB raw [V raw] | Medium (AndroidSVG's last release is 2019-05 [V]; only sanitised SVG reaches it) | `:ui:kit-content` |
| R-13 | `xml` 7.0.1 | SVG sanitiser events and DOM; Word/Excel/PowerPoint text (2/2) | LIB | xmlutil `core` **1.0.2** (pull reader in `commonMain`) | `io.github.pdvrieze.xmlutil:core` | ✓/✓/✓ | Apache-2.0 [V POM] | 699 KB raw [V raw] → about 0.2 MB [est] | Low–Medium | `:engine:media` (needs the ADR §7.2 list entry, R12-5) |
| R-14 | `flutter_secure_storage` 11.2.0 | server passwords, tokens, keys (9/21) | OWN | `SecretStore` over an AndroidKeyStore AES-256-GCM key, AAD = entry name, file `oc_secrets_v1` (W-07); one-time v11 decryptor in `:android:migration` (W-08) | none (AndroidKeyStore, `javax.crypto`) | ✓/later (Keychain)/later (Secret Service) | — | 0 | High (data loss if wrong; byte backup + read-back verify) | `:core:data` (a), `:android:migration` |
| R-15 | `crypto` 3.0.7 | SHA-256 scopes and fingerprints, vault ids, HMAC (36/20) | OWN | `Digests` expect/actual in `:core:common` (W-01): `MessageDigest`/`Mac` on Android and desktop, CommonCrypto on iOS | none | ✓/✓/✓ | — | 0 (about 60 lines) | Low (Dart-exact hex, golden vectors) | `:core:common` |
| R-16 | `cryptography` 2.9.0 | Ed25519 for the FactsBook owner key (1/0) | LATER | FactsBook ships after the switch (D6). Then: BouncyCastle **1.86** light-weight `Ed25519Signer` (no provider registration) | `org.bouncycastle:bcprov-jdk18on` | ✓/later/✓ | MIT (Bouncy Castle Licence) [V POM] | 7 MB raw [V raw] → under 150 KB with R8, light-weight API only [est] | Medium | FactsBook's Android module (later) |
| R-17 | `local_auth` 3.0.2 | FactsBook device check before signing (1/1) | LATER | FactsBook only. Then: androidx.biometric **1.1.0** (the only stable; never the 1.4 alphas), `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` | `androidx.biometric:biometric` | ✓/later/– | Apache-2.0 | 190 KB raw [V raw] | Low | FactsBook's Android module (later) |
| R-18 | `image_picker` 1.2.3 | prompt photos (2048 px edge, JPEG 85, no metadata), camera, card photos (3/4) | PLATFORM + OWN | `PickVisualMedia`, `PickMultipleVisualMedia`, `TakePicture` + `FileProvider`; own `PromptPhotoEncoder` (W-09) | `androidx.activity:activity` 1.13.0, `androidx.core:core-ktx` 1.19.1 (both already present) | ✓/later/later | Apache-2.0 | 0 | Medium (process death with the camera open, B-MEDIA-008) | `:android:platform` |
| R-19 | `mobile_scanner` 7.4.2 | pairing QR scan: CameraX + bundled ML Kit (1/0) | LIB | CameraX **1.6.2** (`CameraXViewfinder`, `ImageAnalysis`) + zxing-cpp **3.1.1**: Google-free (owner freeze decision 1) | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` (1.6.2); `io.github.zxing-cpp:android` (3.1.1) | ✓/later (AVFoundation)/later (pick an image + zxing) | Apache-2.0 [V POM] | ML Kit 2.11 MB + 0.9 MB models → zxing 0.73 MB [I] + CameraX about 1 MB [est] | Low–Medium | `:ui:kit-content` (a), `:android:media` |
| R-20 | `path_provider` 2.1.6 | app folders (17/0) | PLATFORM | `Context.filesDir`, `cacheDir`, `getExternalFilesDir`, and `getDir("flutter", MODE_PRIVATE)` (= `app_flutter/`, read once by the migration), behind an `AppDirs` binding | none | ✓/later/later | — | 0 | Low (path equality, B-MIGR-008/009) | `:core:data` (a) |
| R-21 | `qr` 3.0.2 | drawing the handoff and Wi-Fi share QR (1/1) | LIB | qrose **1.3.1** `rememberQrCodePainter` | `io.github.alexzhirkevich:qrose` | ✓/✓/✓ | MIT [V POM] | 157 KB raw [V raw] | Low | `:ui:kit-content` |
| R-22 | `record` 7.1.1 | microphone, PCM16 mono 16 kHz (1/0) | PLATFORM + OWN | `AudioRecord` + `AudioFocusRequest`; own `MicCapture` (W-10) | none | ✓/later/later | — | 0 (about 200 lines) | Low | `:android:media` |
| R-23 | `scrollable_positioned_list` 0.3.8 | chat anchor jumps (1/3) | PLATFORM | Compose `LazyListState.requestScrollToItem` / `scrollToItem` / `animateScrollToItem` | (in R-01) | ✓/✓/✓ | Apache-2.0 | 0 | Low | `:feature:chat` |
| R-24 | `sherpa_onnx` 2.0.1 | Whisper speech-to-text, Kokoro voice (2/0) | LIB | sherpa-onnx **2.0.1** Kotlin API, vendored AAR `sherpa-onnx-2.0.1.aar` (sha256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` [V GitHub digest]) | `com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1` from `third_party/maven` (not on Maven Central [I]) | ✓/later (xcframework)/later (`sherpa-onnx-jvm` jar) | Apache-2.0 (+ ONNX Runtime MIT) | 9.8 MB → 9.8 MB (C API libs replaced by the JNI lib) [I] | High (vendored; R8 and JNI) | `:android:media` only |
| R-25 | `shared_preferences` 2.5.5 | every preference (105/616) | PLATFORM | `KeyValueStore` over the **same** `FlutterSharedPreferences.xml`, adopted in place through `Context.getSharedPreferences` (`flutter.` prefix and every encoding kept, `commit()`); DataStore 1.3.0 only for desktop/iOS later | none on Android; `androidx.datastore:datastore-preferences-core` 1.3.0 later | ✓/later/later | Apache-2.0 | removes `libdatastore_shared_counter.so` [R] | Medium (encodings, B-MIGR-005) | `:core:data` (a), `:android:migration` |
| R-26 | `shorebird_code_push` 2.0.7 (+ engine fork) | code-push patches and the "update ready" line (1/0) | OWN | `AppUpdater` (W-12): GitHub `releases/latest`, `SHA256SUMS`, same-signer check, `PackageInstaller`; Shorebird ends at the switch | none | ✓/–/later (link to the release page) | — | removes the Shorebird engine fork | Medium (developer verification D18) | `:android:platform`; notice state in `:engine:diagnostics` |
| R-27 | `url_launcher` 6.3.2 | external links, only through `openExternalLink` (1/0) | PLATFORM | `ExternalLinkPolicy` (`:core:security`) + `ExternalLinkUriHandler` (kit) + the `:android:platform` link opener (`Intent.ACTION_VIEW`) (W-13) | none | ✓/later/✓ (`Desktop.browse`) | — | 0 | Low (security invariant) | `:core:security`, `:ui:kit`, `:android:platform` |
| R-28 | `webview_flutter` 4.14.1 | the dev-server preview (1/0) | PLATFORM | `android.webkit.WebView` through `AndroidView` in `:ui:kit-content` androidMain; same host and port only; no JavaScript interface | none | ✓/later (WKWebView)/– | — | 0 | Medium (B-SEC-058) | `:ui:kit-content` (a) |
| R-29 | `webview_flutter_android` 4.14.1 | Android half of R-28 (0 direct imports) | DROP | covered by R-28 | — | — | BSD-3-Clause | — | None | — |
| R-30 | `uuid` 4.6.0 | ids for engine, handoff, orchestration (6/0) | PLATFORM | `kotlin.uuid.Uuid.random()` (stable since Kotlin 2.4.0 [V]) | stdlib | ✓/✓/✓ | Apache-2.0 | 0 | Low | any |
| R-31 | `xterm` (vendored 4.0.0, 12,871 lines, + `convert`, `meta`, `quiver`, `equatable`, `zmodem`) | terminal widget: VT parser, buffer, selection, links, key bar (7/8) | LIB + fork | Termux `terminal-emulator` **v0.118.3** (JitPack, unmodified) + a **source fork of `terminal-view` v0.118.3** with a session-port patch (W-14, R12-2) | `com.github.termux.termux-app:terminal-emulator:v0.118.3`; fork module `:thirdparty:termux-terminal-view` | ✓/later (common renderer)/later (common renderer) | Apache-2.0 | 84 KB + 42 KB AAR, `libtermux.so` 11 KB [V raw] | Medium–High (fork) | `:android:terminal`, `:ui:kit-content` (a) |
| R-32 | `dynamic_color` 1.9.0 | Material You palette on Android 12+ (1/0) | PLATFORM | `dynamicLightColorScheme(ctx)` / `dynamicDarkColorScheme(ctx)` from M3 in the kit's androidMain (`AndroidKitPlatform.dynamicSource()`, 06 §4.2) | (material3, kit only) | ✓/–/– | Apache-2.0 | 0 | Low | `:ui:kit` (a) |
| R-33 | `package_info_plus` 10.2.1 | version, build number (4/4) | PLATFORM | `AppBuildInfo` generated in `:androidApp` + `PackageManager.getPackageInfo(pkg, GET_SIGNING_CERTIFICATES)` for the About signer line | none | ✓/later/✓ | — | 0 | Low | `:androidApp`, `:android:platform` |
| R-34 | `window_manager` 0.5.2 | desktop window icon and state (2/0) | DROP | desktop phase: Compose Desktop `Window` / `rememberWindowState` (built in) | — | –/–/later | MIT | 0 | None | `:desktopApp` (later) |
| R-35 | `screen_retriever` 0.2.2 | keeping a restored window on an attached display (1/0) | DROP | desktop phase: `java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices` | — | –/–/later | MIT | 0 | None | `:desktopApp` (later) |
| R-36 | `path` 1.9.1 | path joins (3/0) | LIB | `kotlinx.io.files.Path` (kotlinx-io **0.9.2**) in common code; `java.io.File` in `:android:*` | `org.jetbrains.kotlinx:kotlinx-io-core` | ✓/✓/✓ | Apache-2.0 | 146 KB raw [V raw] | Low | any |
| R-37 | `flutter_test` SDK (dev) | logic and widget tests | LIB (test) | `kotlin.test` + kotlinx-coroutines-test 1.11.0 + Turbine 1.2.1 + CMP `ui-test` (`runComposeUiTest`) + Robolectric 4.17 + Roborazzi 1.77.0 | see Table D, test group | ✓/✓/✓ | Apache-2.0 / MIT | not shipped | Low | test configurations |
| R-38 | `integration_test` SDK (dev) | device smoke tests (BD9, BB8) | LIB (test) | Maestro CLI 2.12.0 (release-build device flows) + UIAutomator 2.4.0 + `androidx.test` runner + Macrobenchmark 1.5.0; the existing instrumentation runners carry over | see Table D | ✓/–/– | Apache-2.0 | not shipped | Low | `:androidApp` androidTest, `:android:benchmark` |
| R-39 | `flutter_lints` 6.0.0 (dev) | analyzer rules | LIB (tool) | Konsist 0.18.1 + detekt 1.23.8 + Android lint + `explicitApi()` + warnings as errors at T2; ktfmt 0.64 (03 §19.1) | see Table D | — | Apache-2.0 | not shipped | Low | `:quality`, build-logic |
| R-40 | `cross_file` 0.3.5+5 (dev) | file fakes in tests | DROP | fakes in `:core:testing` | — | — | BSD-3-Clause | — | None | — |
| R-41 | `local_auth_platform_interface` 1.1.0 (dev) | `local_auth` fake | DROP | gone with R-17 | — | — | BSD-3-Clause | — | None | — |
| R-42 | `webview_flutter_platform_interface` 2.15.1 (dev) | WebView fake | DROP | the preview's navigation rule is a pure `PreviewNavigationPolicy` tested in `commonTest` | — | — | BSD-3-Clause | — | None | — |
| R-43 | `shared_preferences_platform_interface` 2.4.2 (dev) | a store that refuses writes (fail-closed tests) | OWN (test) | `FakeKeyValueStore(refuseWrites = true)` in `:core:testing` | — | ✓/✓/✓ | — | not shipped | Low | `:core:testing` |

Tally: PLATFORM 13 (R-03, 05, 08, 18, 20, 22, 23, 25, 27, 28, 30, 32, 33), LIB 16 (R-01, 02, 07, 09, 10, 11, 12, 13, 19,
21, 24, 31, 36, 37, 38, 39), OWN 4 (R-14, 15, 26, 43), DROP 8 (R-04, 06, 29, 34, 35, 40, 41, 42), LATER 2 (R-16, 17).

Transitive Flutter-only natives that disappear with these rows [R map §4]: `libflutter.so`, `libapp.so`,
`libdartjni.so` (`jni`/`jni_flutter`), `libdatastore_shared_counter.so`, `libbarhopper_v3.so` + three ML Kit tflite
models, `libsherpa-onnx-c-api.so` and `-cxx-api.so`, the `flutter_plugin_android_lifecycle` and `android_file_picker`
classes, and every `*_platform_interface`, `*_android`, `*_ios`, `*_linux`, `*_windows`, `*_web` federated package.

---

## 2. Notes per row: acceptance, catalog rows, traps

Only rows that need more than the table says. Each "Accept" line is a check an agent can run.

**R-01 `flutter` → Compose Multiplatform.**
- On Android the JetBrains artifacts are thin shims over Google's `androidx.compose.*` (ADR §2.2, [V]). Gradle resolves
  the highest version, so `:androidApp` may raise an androidx Compose version for a *fix* (ADR §7.6).
- Switch gate (D12): the release classpath may hold no alpha and no beta except Material 3 1.5 inside the kit. So CMP
  runtime/ui/foundation must be a stable 1.13.x by the switch (projected mid-December 2026 [U]). `PrereleaseGuardTest`
  (§12.1) enforces it.
- What leaves the APK: `libflutter.so`, `libapp.so`, `flutter_assets/`, `NOTICES.Z`, `shaders/ink_sparkle.frag`, and
  the four headless Dart entrypoints (map PACKAGES-BUILD-39).
- Accept: `./gradlew :androidApp:dependencyInsight --configuration releaseRuntimeClasspath --dependency
  androidx.compose.ui:ui` shows one version; no `io.flutter` class in `classes*.dex` (`apkanalyzer dex packages`).

**R-02 `flutter_localizations` → CMP resources.** One-off ARB → XML by `:tools:strings` (ADR §7.5); the six ICU
`select` messages become code; Arabic six-form plurals (zero, one, two, few, many, other). Per-app language follows
06 K16: `LocaleManager.setApplicationLocales` on API 33+, `Locale.setDefault` plus a configuration override on 26–32,
`androidResources { generateLocaleConfig = true }`. **No `androidx.appcompat`** (R12-3). Catalog: B-L10N-001, -002,
-008, -009, -010, -013. Accept: P6 (ADR §11).

**R-03 `intl` → platform ICU behind one seam.**
- Plurals: CMP `pluralStringResource` (CLDR categories). An ICU `=N` exact selector is not a CLDR category; 06 §13.4
  handles those.
- Numbers, currency, dates, times: `LocaleFormats` (W-03), an `expect object` in `:core:common`, which the ADR allows
  for primitives. Android uses `android.icu.text.NumberFormat`, `NumberFormat.getCurrencyInstance`, `DateFormat`
  skeletons; desktop uses `java.text`. The kit's `KitPlatform.formatNumber` (06 §12.1 item 8) delegates to it, and
  notifications and widgets call it too, so every surface formats the same way.
- Catalog: B-L10N-004 (relative times follow the supplied locale), B-L10N-012 (Arabic puts the currency symbol after
  the amount). Accept: `LocaleFormatsTest` in `:core:common` androidHostTest with en and ar vectors copied from the
  Flutter tests.

**R-07 `dio` → Ktor + OkHttp.** The rules live in 04 §3; the registry facts are:
- No `ktor-client-content-negotiation`, no `ktor-client-logging`, no `HttpRequestRetry`, no Ktor SSE plugin (X-12, X-31).
- OkHttp defaults that must be changed: `retryOnConnectionFailure(false)` (else a POST can be sent twice [V source]),
  Ktor `followRedirects = false`, `Proxy.NO_PROXY`, a pinned-leaf `X509TrustManager`.
- kotlinx.serialization `Json { exceptionsWithDebugInfo = false }` (else JSON input, possibly provider keys, lands in
  exception text [V]).
- `ktor-client-websockets` (in 03 §5) is not declared on Android or desktop; 04 §2.3 uses `io.ktor:ktor-websockets`
  frames over OkHttp's upgrade. It returns only with the iOS Darwin engine.
- Catalog: B-SEC-139, B-SEC-146, B-SEC-153, B-PROTO-037, B-BUILD-026. Accept: 04's transport contract tests, including
  "a POST is not resent after a stale pooled connection" (MockWebServer 5.5.1).

**R-09 `flutter_riverpod` → Metro.** Metro 1.5.1 is tested against Kotlin 2.3.0 → 2.5.0-Beta2 [V research]. Kotlin,
the Compose compiler, Metro and KSP move together (ADR §8). `metrox-android` is not used (needs API 28; minSdk 26).
Accept: P1 graph smoke.

**R-11 `highlight` → SyntaxMP behind `CodeTokenizer`.**
- The interface is ours, so the library can change without touching the kit API:
  ```kotlin
  // :ui:kit-content commonMain
  public fun interface CodeTokenizer {
      /** null = render plain (unknown language, over 20,000 chars, or tokenizer failure). */
      public fun tokenize(source: String, languageAlias: String?): List<CodeSpan>?
  }
  public data class CodeSpan(val start: Int, val endExclusive: Int, val role: CodeRole)
  ```
- Alias table: today's about 45 aliases map onto SyntaxMP's 39 languages, plus `scss→css`, `gradle→kotlin`,
  `svg→xml`, `console→bash`, `patch→diff` (rendering-chat §5).
- Keep today's limits: plain above 20,000 chars, parse cache of 48 entries (B-CHAT-056).
- The benchmark (rendering-chat §8.2, kit-content owner, slice 2) decides between SyntaxMP and kotlin-textmate. If
  kotlin-textmate wins it is Android/JVM only, adds about 2 MB raw (jcodings), and iOS falls back to SyntaxMP or plain.
- Highlights 1.1.0 lacks JSON, YAML, TOML, HTML, XML, CSS, SQL and diff, so it is benchmark-only (X-19).

**R-12 `flutter_svg` → sanitiser + coil-svg.**
- Order is fixed: bytes → `StaticSvg.sanitize` (W-05) → re-serialised allowlisted SVG → Coil decode. Raw server SVG
  never reaches AndroidSVG.
- Coil gets its bytes from our gateway `Fetcher` (media §2), never from a URL. Provider logos from `models.dev` go
  through the same sanitiser (B-AGENT-205, B-AGENT-207).
- Catalog: B-CHAT-107. Accept: the ported Flutter SVG fixtures (active, external, malformed, DOCTYPE, oversized,
  dash-exploding) all fall back; one golden of a gradient icon renders.

**R-13 `xml` → xmlutil.** The Dart sanitiser first rejects `<!\s*(DOCTYPE|ENTITY)` by regex, then reads events
(`lib/domain/static_svg.dart:83–103`) [R]. Keep that order, so xmlutil never sees a DTD. Office text (W-04) uses the
same reader. Accept: a billion-laughs fixture and an external-entity fixture are refused before parsing; the docx and
xlsx fixtures of B-MEDIA-006 pass.

**R-14 `flutter_secure_storage` → own `SecretStore`.** The interface and Keystore format are in 05 §4 (design from
network-data §7.2); the v11 decrypt recipe (RSA-OAEP SHA-256 with MGF1-SHA1 unwrap of the AES key, then AES-GCM
`IV[12] || ct+tag`) is in 05 §7.5. Rules from this registry:
- No library on Android. `security-crypto` is deprecated [V]; KSafe, KVault and multiplatform-settings would own our
  secret format (X-06).
- Every secret is registered with the `Redactor` before any log line (B-SEC-056).
- A secret that cannot be decrypted sets that profile's "enter the password again" flag. It is never treated as an
  empty password (B-MIGR-006, 02 S-4).

**R-18 `image_picker` → system pickers + `PromptPhotoEncoder`.**
- Record the request before launch, so a process death with the camera open recovers (B-MEDIA-008).
- Decode with `ImageDecoder` on API 28+ (it applies EXIF rotation itself). On 26–27 decode with
  `BitmapFactory.Options.inSampleSize` and rotate by the **platform** `android.media.ExifInterface(InputStream)`
  (API 24+), so no `androidx.exifinterface` dependency.
- Then scale to a 2048 px long edge and compress JPEG 85. Re-encoding drops EXIF, including location.
- Catalog: B-MEDIA-007, B-MEDIA-008, B-CHAT-053.

**R-19 `mobile_scanner` → CameraX + zxing-cpp.**
- zxing-cpp `BarcodeReader.Options(formats = setOf(QR_CODE), tryInvert = true, tryHarder = false)`, decoding on one
  single-thread executor. Unbind on the first valid result.
- The decoded value is never logged, stored or put in semantics (SEC-2).
- `ImageAnalysis` keeps only the latest frame (`STRATEGY_KEEP_ONLY_LATEST`).
- Move to CameraX 1.7.x only once it is stable (1.7.0-rc01 today [V]): it brings built-in tap-to-focus and pinch.
- zxing-cpp ships a consumer keep rule (`-keep class zxingcpp.** { *; }`) and 16 KB-aligned `.so` files [I].
- Catalog: B-ANDR-049, B-PROTO-069, B-KIT-026. Accept: `dependencyInsight --dependency com.google.mlkit` is empty;
  scan from the emulator camera and from a picked picture both pair.

**R-24 `sherpa_onnx` → vendored AAR.**
- Placement: `tools/vendor/fetch.sh` downloads
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar`, checks the SHA-256 above, and
  writes it to `native/third_party/maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar`.
  That folder is **git-ignored**, because the file is 49,441,341 bytes [V]. Next to it sits a committed hand-written
  `.pom` (packaging `aar`, licence Apache-2.0, no dependencies), and the hash goes into `third_party/SHA256SUMS`
  (`ThirdPartyHashTest`, 03 §19.6). `settings.gradle.kts` fails early with "run tools/vendor/fetch.sh" when the file
  is missing.
- Packaging in `:androidApp`:
  ```kotlin
  android {
      defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }   // the ADR §9.9 ABI set
      packaging { jniLibs {
          excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
          useLegacyPackaging = true                                       // already required for proot
      } }
  }
  ```
  `keepRules/sherpa.keep`: `-keep class com.k2fsa.sherpa.onnx.** { *; }`. The AAR's `proguard.txt` is empty [I] and
  the JNI code reads config fields by name.
- Models stay in `files/voice_models/` and are read in place, with no re-download (B-MIGR-009 lists them).
- R12-6 measures the `sherpa-onnx-static-link-onnxruntime-2.0.1.aar` variant (38.6 MB AAR [V]).
- Catalog: B-MEDIA-031, B-MEDIA-033. Accept: a release-build smoke test constructs `OfflineRecognizer` and `OfflineTts`
  from fixture paths on the emulator (catches R8 breakage).

**R-25 `shared_preferences` → the same file, adopted.** Exact encodings (Long ints; list prefix
`VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu` with `!` for JSON lists; the double prefix) are in 05 §2.2 and network-data §8.1.
Never read ints with `getInt`: Flutter stored them as Long, which throws `ClassCastException` (network-data §7.1).
Konsist rule 7 (ADR §6.3) keeps `getSharedPreferences(` inside `:core:data` androidMain and `:android:migration`.
Catalog: B-MIGR-005, B-MIGR-007.

**R-26 `shorebird_code_push` → `AppUpdater`.**
- Flow: check on start and on resume with a 15-minute throttle (today's rule) → `GET
  https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest` through `:core:net` → pick the
  `-arm64-v8a.apk` asset for this ABI → download with the resumable pinned downloader → check `SHA256SUMS` →
  `PackageManager.getPackageArchiveInfo(file, GET_SIGNING_CERTIFICATES)` must list exactly the installed signer →
  `PackageInstaller` session.
- Needs `REQUEST_INSTALL_PACKAGES` in the manifest. The automation policy key `applyCodePush` stays parseable (map
  PACKAGES-BUILD-29).
- No library: `com.android.tools.build:apksig` is not needed, because the platform installer refuses a signer mismatch
  anyway, and the archive-info check gives the plain-words refusal before download.
- PRIVACY.md drops the Shorebird paragraph and names the GitHub API.
- Catalog: B-MIGR-012, B-BUILD-010, B-BUILD-011, B-BUILD-012.
- Risk: once Android developer verification reaches the owner's users ("2027 and beyond"), an unregistered app cannot
  self-update (D18).

**R-27 `url_launcher`.** One door, as today: `ExternalLinkPolicy.decide(url)` in common, `ExternalLinkUriHandler` as
the Compose `LocalUriHandler`, and one Android opener (`ACTION_VIEW`, `CATEGORY_BROWSABLE`, `FLAG_ACTIVITY_NEW_TASK`).
No Custom Tabs (`androidx.browser` is not added; today's Flutter opens the external browser). Konsist rule 6.
Catalog: B-SEC-064, B-SEC-065, B-SEC-095.

**R-28 `webview_flutter` → platform WebView.**
- Settings: `allowFileAccess = false`, `allowContentAccess = false`, no `addJavascriptInterface`,
  `WebView.setWebContentsDebuggingEnabled(false)` in release builds.
- `shouldOverrideUrlLoading` keeps the same scheme, host and port and sends everything else to the external opener.
- The data folder `app_webview/` is kept.
- `androidx.webkit` 1.18.0 [V] is **not** added; nothing in today's behaviour needs it.
- Catalog: B-SEC-058.

**R-31 `xterm` → Termux emulator + forked view.** Fact [V, source at tag v0.118.3]: `TerminalSession` is `public final
class`, and its `initializeEmulator` always forks a local process through JNI (`JNI.createSubprocess`). `TerminalView`
(1,476 lines) accepts only a `TerminalSession`. So the stock view cannot show a **remote** PTY: the OpenCode 2
terminal over WebSocket, Paseo terminal frames, screen D10. The emulator side is open enough: `TerminalOutput` is
`public abstract`, and `TerminalEmulator` takes any `TerminalOutput`. Plan (W-14, decision R12-2):
1. Copy `terminal-view/src/main/java/com/termux/view/**` at tag `v0.118.3` (about 2,600 lines, Apache-2.0) into
   `native/thirdparty/termux-terminal-view/`. Keep the licence headers; add `PATCHES.md` and a `NOTICE` line.
2. Patch: replace the `TerminalSession` type in `TerminalView`, `TerminalViewClient` and the text-selection classes with
   `com.termux.view.TerminalSessionPort` (below). That is the only patch.
3. `:android:terminal` implements the port twice: `LocalPtySession` (our `PtyAccess.java` + proot, as today) and
   `RemoteTerminalSession` (a `TerminalEmulator` fed by WebSocket bytes, whose `TerminalOutput.write` sends input
   frames).
4. Our `TerminalOutput.onCopyTextToClipboard` enforces the OSC 52 rules: set only, never answer a query, size cap
   (B-XPLAT-009). Links across soft wraps come from our own `TerminalLinkScanner` over `TerminalBuffer`
   (B-XPLAT-010). The key bar sends bytes `0x03` and `0x04` (B-XPLAT-011).
5. Extend ADR P9: render one local **and** one remote (fake WebSocket) session in the slot.

```java
// native/thirdparty/termux-terminal-view/src/main/java/com/termux/view/TerminalSessionPort.java (new, ours, MIT)
package com.termux.view;
import com.termux.terminal.TerminalEmulator;
public interface TerminalSessionPort {
    TerminalEmulator getEmulator();                       // null until the first updateSize
    void write(byte[] data, int offset, int count);       // keyboard and paste input
    void writeCodePoint(boolean prependEscape, int codePoint);
    void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);
    String getTitle();
}
```

**R-33 `package_info_plus`.** `AppBuildInfo` (versionName, versionCode, flavor, git short hash) is generated by a
`:androidApp` task, because KMP modules have no `BuildConfig` (ADR §5.2). The About screen's signer line reads
`PackageInfo.signingInfo` (API 28+), or `signatures` on API 26–27. Catalog: B-NAV-001.

**R-37 – R-43 (dev).** The Flutter test harness, golden harness, widgetbook and analyzer gates die with Flutter
(map PACKAGES-BUILD-38). The 46 Kotlin JVM test classes (645 tests) and the instrumentation runners move unchanged (K-05
to K-07). The widget-test port waits for the owner (ADR §4 row 18).

---

## 3. Kotlin-side Gradle dependencies that already exist (Table B)

From `android/app/build.gradle.kts` [R map §2]. These are already native and carry over.

| # | Today | Native | Coordinates | Notes |
|---|---|---|---|---|
| K-01 | `androidx.work:work-runtime:2.10.5` | **2.12.0** [V] | `androidx.work:work-runtime` | `:android:schedule`, `:android:widgets`, `:android:background`, `:androidApp`. Keep the `WorkDatabase_Impl` and `public <init>(Context, WorkerParameters)` keep rules (map PACKAGES-BUILD-06). WorkManager carries its own Room **2.x** inside; that is expected and separate from Room 3 (`androidx.room3`, different package). |
| K-02 | `androidx.core:core:1.13.1` | `core-ktx` **1.19.1** [V] | `androidx.core:core-ktx` | `NotificationCompat` `ProgressStyle` / `setRequestPromotedOngoing` (1.17+) / `MetricStyle` (1.19+), `ShortcutManagerCompat`, `FileProvider`, `ServiceCompat`. |
| K-03 | `org.apache.commons:commons-compress:1.27.1` | **1.27.1** now; 1.28.0 [V] on trigger R12-7 | `org.apache.commons:commons-compress` | reads the Ubuntu Base tarball. `:android:linux` only. 1.06 MB raw; R8 keeps the tar and gzip readers [est]. |
| K-04 | `com.github.termux.termux-app:terminal-emulator:v0.118.3` | **v0.118.3** | same (JitPack, `exclusiveContent`, 03 §3.1) | unmodified; ships `libtermux.so`. Never `termux-shared` or `app` (GPLv3, X-05). |
| K-05 | `junit:junit:4.13.2` (test) | 4.13.2 | `junit:junit` | EPL-1.0, test only. |
| K-06 | `org.json:json:20180813` (test) | **20180813, never bumped without proof** | `org.json:json` | The existing Kotlin code uses Android's platform `org.json` (56 imports [R]); this jar stands in for it in JVM tests, so its behaviour must stay close to the platform's. Licence: the JSON License (non-OSI), allowed only because it is test-only and never shipped (Licensee runs on release configurations). |
| K-07 | `org.robolectric:robolectric:4.17` (test) | 4.17 | `org.robolectric:robolectric` | Needs JDK 21 for SDK 36/37 (testing-perf §1). MIT. |
| K-08 | `:integration_test` Flutter project | DROP | — | replaced by R-38. |
| K-09 | detekt CLI 1.23.8 jar, sha256-pinned | keep | `io.gitlab.arturbosch.detekt:detekt-cli:1.23.8` [V] | detekt 2.0 is alpha (`dev.detekt` 2.0.0-alpha.6 [V]); stay on 1.23.8 until 2.0 is stable. |
| K-10 | Flutter Gradle glue: `flutter.sdk`, `includeBuild(flutter_tools)`, `android.newDsl=false`, `copyFlutterAssets*` | DROP | — | map PACKAGES-BUILD-04. |

---

## 4. Bundled components that are not packages (Table C)

| # | Component | Licence | Today | Native | Notes |
|---|---|---|---|---|---|
| C-01 | Geist and Geist Mono variable TTFs (4 files) | OFL-1.1 | `assets/fonts/geist/` | `:ui:kit` `composeResources/font/` byte for byte, SHA-256 in `PROVENANCE.md` (06 §4.4) | weights through `FontVariation.weight`. |
| C-02 | Phosphor 2.1.0 icons (3 TTFs) | MIT | icon fonts | generated `KitGlyphs.kt` `ImageVector`s by `tools/glyphs/extract.py` (fontTools, build time only); the TTFs are not shipped (06 §4.11) | about 1.5 MB less in the APK [R 06]. |
| C-03 | lobe-icons agent and provider marks (79 SVG) | MIT | `assets/marks/` | generated `KitMarks.kt` (same script) | CMP resources cannot load SVG on Android [R]. |
| C-04 | Open Portal brand mark | ours | `assets/branding/open-portal/mark.svg` | `KitBrandMark` | — |
| C-05 | proot 5.1.107.94, its loader, libtalloc 2.4.3, libandroid-shmem 0.7 | proot **GPL-2.0**; talloc **LGPL-3.0-or-later** upstream (Termux labels it `GPL-3.0` [V build.sh]); shmem **BSD-3-Clause** [V] | `jniLibs/{arm64-v8a,x86_64}` via `tool/builtin_linux/fetch_proot.sh` (sha256-pinned `.deb`s, patchelf soname rewrite) | same script, same pins, `:android:linux` `jniLibs`, `useLegacyPackaging = true` | **Gap found:** `THIRD_PARTY_NOTICES.md` and `LICENSES/` do not mention proot, talloc or shmem today [V grep]. They run as separate executables (not linked into the app process), but distributing GPL binaries still requires the licence text and the corresponding source, and we modify them (patchelf). Fix: R12-8. |
| C-06 | Rust `libaiteam_engine.so`, `_sandbox`, `_boundary_probe` | own code + crates (`LICENSES/Rust-Phone-Engine.txt`, libgit2 GPL-2.0 with linking exception) | `jniLibs` + hash manifest | only if AI Team ships (D6, after the switch); keep `verify<Variant>PhoneEngineApk` and the strip exclusion | 3.0 MB compressed arm64 [R]. |
| C-07 | `libtermux.so` (inside K-04) | Apache-2.0 | from the AAR | same | 11 KB. |
| C-08 | Whisper tiny/base/small int8, Kokoro int8 | MIT / Apache-2.0 | downloaded to `files/voice_models/` (revisions pinned) | read in place | not in the APK; notices kept (B-MEDIA-031). |
| C-09 | `assets/agents/paseo-package-lock.json` (153 KB) | ours (lists npm packages installed at runtime) | Flutter asset | `:engine:agents` resource | pinned dependencies for the phone agent host. |
| C-10 | `tool/quota/collector.mjs`, `read_once.mjs` | ours | Flutter assets | resources with checksums (ADR `:engine:runtime` "shell scripts as resources") | — |
| C-11 | `assets/aiteam/manifest*.json`, `assets/aiteam-engine-manifest.json` | ours | assets | only with AI Team | — |
| C-12 | `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `LICENSES/` | ours / various | Flutter assets | Android assets read by the About screen; notices **generated** (§8.3) | **Also bundle `PRIVACY.ar.md`**: the 4511 APK lacks it today, so the Arabic privacy view is empty [R map PACKAGES-BUILD-22]. |
| C-13 | `assets/branding/app-icon-256.png` | ours | Linux window icon | `:desktopApp` (later) | — |
| C-14 | `shaders/ink_sparkle.frag` | Flutter | Material ripple shader | DROP | the platform `RippleDrawable` does it (ADR §9.4). |
| C-15 | ML Kit models (3 tflite) | proprietary | `assets/` | DROP | X-02. |
| C-16 | `assets/dexopt/baseline.prof` | generated | profileinstaller | regenerated by the baseline-profile plugin 1.5.0 + profileinstaller 1.4.1 | Baseline and Startup Profiles from `:android:benchmark`. |

---

## 5. New native-only dependencies (Table D)

These have no pubspec counterpart, or exist only because of the new stack. Versions equal ADR §8 and 03 §5 unless the
row says "add". **Gate** = whether it may be in the *switch* release classpath under D12 (✓ stable; **M3-β** = the one
allowed beta; ✗ = must be stable or absent by then).

### 5.1 Toolchain and Gradle plugins (build time, not in the APK)

| # | What | Coordinates / plugin id | Version | Licence | Notes |
|---|---|---|---|---|---|
| N-01 | Kotlin (KGP, serialization plugin, Compose compiler plugin) | `org.jetbrains.kotlin.multiplatform`, `.jvm`, `.plugin.serialization`, `.plugin.compose` | 2.4.21 | Apache-2.0 | moves with Metro and KSP (ADR §8). |
| N-02 | Android Gradle Plugin | `com.android.application`, `.library`, `.kotlin.multiplatform.library`, `.test` | 9.3.3 | Apache-2.0 | `optimization {}` + `keepRules` DSL. |
| N-03 | Gradle / JDK | wrapper 9.7.1; JDK 17 build, JDK 21 Robolectric (foojay resolver 1.0.0) | — | Apache-2.0 | 03 §3.1. |
| N-04 | KSP | `com.google.devtools.ksp` | 2.3.13 | Apache-2.0 | Room 3 only; AppFunctions later. |
| N-05 | Metro plugin | `dev.zacsweers.metro` | 1.5.1 | Apache-2.0 | — |
| N-06 | Compose Multiplatform plugin | `org.jetbrains.compose` | 1.13.0-beta01 | Apache-2.0 | resources generation. |
| N-07 | Room 3 plugin | `androidx.room3` | 3.0.3 | Apache-2.0 | when `:core:db` starts (D9). |
| N-08 | Roborazzi plugin | `io.github.takahirom.roborazzi` | 1.77.0 | Apache-2.0 | goldens on the VPS only. |
| N-09 | Baseline profile plugin | `androidx.baselineprofile` | 1.5.0 | Apache-2.0 | — |
| N-10 | Kover | `org.jetbrains.kotlinx.kover` | 0.9.11 | Apache-2.0 | — |
| N-11 | Dependency analysis | `com.autonomousapps.dependency-analysis` | 3.20.1 [V] | Apache-2.0 | VPS only. |
| N-12 | **Licensee** (add) | `app.cash.licensee` | **1.14.1** [V] (2025-10-08) | Apache-2.0 | licence allowlist gate + `artifacts.json` for the About screen (§8). AGP 9 + Gradle 9.7 untested by its changelog [U] → slice-0 item; fallback W-18 without it. |
| N-13 | ktfmt | `com.facebook:ktfmt:0.64` jar | 0.64 | Apache-2.0 | 03 §19.1. |
| N-14 | detekt CLI | K-09 | 1.23.8 | Apache-2.0 | — |
| N-15 | Compose Hot Reload (optional, desktop) | `org.jetbrains.compose.hot-reload` | 1.2.0 | Apache-2.0 | trial only (ADR §4 row 16). |
| N-16 | fontTools (Python) | pip `fonttools` | pinned in `tools/glyphs/requirements.txt` | MIT | glyph extraction, run once with `python3 -I`. |

### 5.2 Runtime: common (KMP) libraries

| # | Library | Coordinates | Version | A/I/D | Licence | Raw size [V raw] | Risk | Gate | Declared in |
|---|---|---|---|---|---|---|---|---|---|
| N-20 | kotlinx-coroutines (+ `-android`, `-swing`) | `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android` / `-swing` | 1.11.0 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | any; `-android` / `-swing` in `:core:common` a/d |
| N-21 | kotlinx-serialization-json | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | ✓/✓/✓ | Apache-2.0 | 0.7 MB | Low | ✓ | any |
| N-22 | kotlinx-collections-immutable | `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.2 | ✓/✓/✓ | Apache-2.0 | 235 KB | Low | ✓ | any |
| N-23 | kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | 0.8.0 | ✓/✓/✓ | Apache-2.0 | 708 KB | Low | ✓ | any |
| N-24 | kotlinx-io | `org.jetbrains.kotlinx:kotlinx-io-core` | 0.9.2 | ✓/✓/✓ | Apache-2.0 | 146 KB | Low | ✓ | any |
| N-25 | Metro runtime + ViewModel helpers | `dev.zacsweers.metro:runtime`, `metrox-viewmodel`, `metrox-viewmodel-compose` | 1.5.1 | ✓/✓/✓ | Apache-2.0 | 152 KB | Medium | ✓ | any (runtime); `:ui:shell`, features |
| N-26 | CMP core (runtime, foundation, ui, animation, resources, ui-tooling-preview) | `org.jetbrains.compose.*` (03 §5) | 1.13.0-beta01 | ✓/✓/✓ | Apache-2.0 | — | Medium | ✗ until 1.13.x stable | `oc.kmp.compose` |
| N-27 | CMP Material 3 | `org.jetbrains.compose.material3:material3` | 1.13.0-beta01 | ✓/✓/✓ | Apache-2.0 | — | Medium | **M3-β** | `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content` only |
| N-28 | CMP Material 3 adaptive (+ layout, + navigation3) | `org.jetbrains.compose.material3.adaptive:*` | 1.3.0 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | kits + `:ui:shell` |
| N-29 | Navigation 3 | `androidx.navigation3:navigation3-runtime` (Google, KMP [V]), `org.jetbrains.androidx.navigation3:navigation3-ui` | 1.2.0 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | `:ui:routes` (runtime), `:ui:shell` (ui), feature `Entries.kt` |
| N-30 | NavigationEvent | `org.jetbrains.androidx.navigationevent:navigationevent-compose` | 1.1.0 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | `:ui:kit`, `:ui:shell` |
| N-31 | Lifecycle + SavedState | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel(-compose, -navigation3)`, `lifecycle-runtime-compose`; `org.jetbrains.androidx.savedstate:savedstate` | 2.11.0; 1.4.0 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | features, `:ui:shell` |
| N-32 | Kermit | `co.touchlab:kermit` | 2.2.0 | ✓/✓/✓ | Apache-2.0 [V POM] | 38 KB | Low | ✓ | `:core:common` only (behind `AppLog`) |
| N-33 | androidx.tracing | `androidx.tracing:tracing` | 2.0.5 | ✓/✓/✓ | Apache-2.0 | — | Low | ✓ | `:core:common` |
| N-34 | JetBrains Markdown | `org.jetbrains:markdown` | 0.7.18 | ✓/✓/✓ | Apache-2.0 [V POM] | 603 KB | Low–Medium | ✓ | `:core:markdown` only |
| N-35 | Coil 3 (compose, svg, gif) | `io.coil-kt.coil3:coil-compose`, `coil-svg`, `coil-gif` | 3.6.3 | ✓/✓/✓ (gif: Android only) | Apache-2.0 | core 469 + compose 136 + svg 23 + gif 39 KB | Low | ✓ | `:ui:kit` (compose), `:ui:kit-content` (svg, gif) |
| N-36 | Telephoto zoomable | `me.saket.telephoto:zoomable` (+ `zoomable-image-coil3`, Android only) | 0.19.0 | ✓/✓/✓ | Apache-2.0 [V POM] | 398 KB | Medium (upstream moved to 1.0.0-alpha02 in April 2026 [V]; 0.19.0 may get no fixes) | ✓ | `:ui:kit-content` |
| N-37 | qrose | `io.github.alexzhirkevich:qrose` | 1.3.1 | ✓/✓/✓ | MIT | 157 KB | Low | ✓ | `:ui:kit-content` |
| N-38 | SyntaxMP | `com.gallatinapps.syntaxmp:syntaxmp-tokenizer` | 0.3.0 | ✓/✓/✓ | Apache-2.0 | 554 KB | Medium–High | ✓ | `:ui:kit-content` |
| N-39 | **xmlutil core** (add) | `io.github.pdvrieze.xmlutil:core` | 1.0.2 [V] (2026-08-25, built with Kotlin 2.4.10) | ✓/✓/✓ | Apache-2.0 | 699 KB | Low–Medium | ✓ | `:engine:media` only |
| N-40 | Ktor client | `io.ktor:ktor-client-core`, `ktor-client-okhttp`, `ktor-websockets` (add per 04 §2.3) | 3.6.1 | ✓/later (darwin)/✓ | Apache-2.0 | core 943 + io 299 + utils 389 + http 448 + websockets 188 KB | Medium | ✓ | `:core:net`, `:protocol:*`, `:core:testing` (mock) |
| N-41 | Room 3 + bundled SQLite | `androidx.room3:room3-runtime` (+ `room3-compiler` KSP), `androidx.sqlite:sqlite-bundled` | 3.0.3; 2.7.1 | ✓/✓/✓ | Apache-2.0 (SQLite public domain) | 522 KB + 2.49 MB AAR (1.28 MB `.so` arm64 [I]) | Medium (KSP spike) | ✓ — but default **switch+1** (D9) | `:core:db` only |
| N-42 | DataStore preferences core | `androidx.datastore:datastore-preferences-core` | 1.3.0 | –/later/later | Apache-2.0 | — | Low | not on Android | `:core:data` desktopMain/iosMain (later) |

### 5.3 Runtime: Android-only libraries

| # | Library | Coordinates | Version | Licence | Raw size [V raw] | Risk | Gate | Declared in |
|---|---|---|---|---|---|---|---|---|
| N-50 | OkHttp | `com.squareup.okhttp3:okhttp` (Okio 3.18.2 comes transitively; never declared in KMP code) | 5.5.1 | Apache-2.0 | 941 KB + okio 383 KB | Medium (defaults) | ✓ | `:core:net-okhttp` only |
| N-51 | activity-compose | `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | — | Low | ✓ | `:androidApp`, `:android:platform` (`ActivityResults`) |
| N-52 | androidx.core | `androidx.core:core-ktx` | 1.19.1 | Apache-2.0 | 1.57 MB | Low | ✓ | `:android:*`, kit/shell/core androidMain |
| N-53 | WorkManager | `androidx.work:work-runtime` | 2.12.0 | Apache-2.0 | 1.24 MB | Low (R8 keep rules) | ✓ | `:android:schedule`, `:android:widgets`, `:android:background`, `:androidApp` |
| N-54 | CameraX | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` | 1.6.2 | Apache-2.0 | 1.19 + 1.16 MB + 42 KB | Low | ✓ | `:ui:kit-content` (a), `:android:media` |
| N-55 | zxing-cpp | `io.github.zxing-cpp:android` | 3.1.1 | Apache-2.0 | 2.9 MB AAR, 4 ABIs; 0.73 MB arm64 compressed [I] | Low | ✓ | `:ui:kit-content` (a), `:android:media` |
| N-56 | sherpa-onnx (vendored) | `com.k2fsa.sherpa.onnx:sherpa-onnx-android` | 2.0.1 | Apache-2.0 + MIT | 49.4 MB AAR; 9.8 MB arm64 compressed [I] | High | ✓ | `:android:media` only |
| N-57 | Termux terminal-emulator | `com.github.termux.termux-app:terminal-emulator` | v0.118.3 | Apache-2.0 (POM says "Other" [V]) | 84 KB | Medium | ✓ | `:android:terminal` (+ the fork module) |
| N-58 | Termux terminal-view (**fork**) | project `:thirdparty:termux-terminal-view` (source from tag v0.118.3) | v0.118.3 + `PATCHES.md` | Apache-2.0 | 42 KB | Medium–High | ✓ | `:ui:kit-content` (a), `:android:terminal` |
| N-59 | commons-compress | `org.apache.commons:commons-compress` | 1.27.1 | Apache-2.0 | 1.06 MB | Low | ✓ | `:android:linux` only |
| N-60 | profileinstaller | `androidx.profileinstaller:profileinstaller` | 1.4.1 | Apache-2.0 | — | Low | ✓ | `:androidApp` |
| N-61 | AppFunctions | `androidx.appfunctions:appfunctions` (+ KSP compiler) | 1.0.0-beta01 [V] | Apache-2.0 | — | High (renames between betas) | ✗ (after the switch, 02 §3.5) | `:android:platform` (later) |

### 5.4 Tests, quality and device tools (never in the APK)

| # | Tool | Coordinates | Version | Licence | Use |
|---|---|---|---|---|---|
| N-70 | kotlin.test | `org.jetbrains.kotlin:kotlin-test` | = Kotlin | Apache-2.0 | `commonTest` |
| N-71 | coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Apache-2.0 | `runTest`, virtual time |
| N-72 | Turbine | `app.cash.turbine:turbine` | 1.2.1 | Apache-2.0 | Flow tests |
| N-73 | Ktor mock | `io.ktor:ktor-client-mock` | 3.6.1 | Apache-2.0 | `MockEngine` |
| N-74 | MockWebServer + TLS | `com.squareup.okhttp3:mockwebserver3`, `okhttp-tls` (add, 04) | 5.5.1 | Apache-2.0 | engine-level transport tests, self-signed leaves |
| N-75 | kotest-property (add, 04) | `io.kotest:kotest-property` | 6.2.6 [V] | Apache-2.0 | SSE chunk-boundary fuzz |
| N-76 | CMP ui-test (+ junit4) | `org.jetbrains.compose.ui:ui-test`, `ui-test-junit4`; `androidx.compose.ui:ui-test-manifest` | 1.13.0-beta01; 1.13.0-beta02 | Apache-2.0 | Compose tests |
| N-77 | Robolectric | `org.robolectric:robolectric` | 4.17 | MIT | Android host tests (JDK 21) |
| N-78 | Roborazzi (+ compose, compose-desktop, preview-scanner-support) + ComposablePreviewScanner | `io.github.takahirom.roborazzi:*`; `io.github.sergio-sastre.ComposablePreviewScanner:android` | 1.77.0; 0.9.3 | Apache-2.0 | screenshots (VPS host only) |
| N-79 | Konsist | `com.lemonappdev:konsist` | 0.18.1 | Apache-2.0 | architecture rules |
| N-80 | androidx.test | `androidx.test.ext:junit` 1.3.0, `androidx.test:runner` 1.7.0, `androidx.test.uiautomator:uiautomator` 2.4.0 | — | Apache-2.0 | instrumentation |
| N-81 | Macrobenchmark | `androidx.benchmark:benchmark-macro-junit4` | 1.5.0 | Apache-2.0 | `:android:benchmark` |
| N-82 | JSON schema validator (add, 04) | `com.networknt:json-schema-validator` | 3.0.8 [V] | Apache-2.0 | `:tools:contracts` drift job |
| N-83 | Ktor server CIO (add, 04) | `io.ktor:ktor-server-cio` | 3.6.1 | Apache-2.0 | `:tools:contracts` recorder and replay server; **never** in the app |
| N-84 | Maestro CLI | GitHub release | 2.12.0 | Apache-2.0 | 39 parity flows on the release build |
| N-85 | Perfetto | trace_processor | v59.1 | Apache-2.0 | traces |

### 5.5 Later (recorded now, not in the switch release)

| # | For | Library | Version today | Licence | Decision owner and moment |
|---|---|---|---|---|---|
| N-90 | desktop/iOS pickers | FileKit `io.github.vinceglb:filekit-dialogs-compose` (+ `filekit-coil`) | 0.17.0 [V] | MIT | ADR §8; desktop phase kickoff |
| N-91 | iOS HTTP | `io.ktor:ktor-client-darwin` | 3.6.1 | Apache-2.0 | iOS phase |
| N-92 | desktop secrets | Secret Service: dbus-java or a guarded `secret-tool` call | — | — | core owner at desktop phase kickoff (network-data §7.2) |
| N-93 | FactsBook | R-16 BouncyCastle 1.86, R-17 androidx.biometric 1.1.0 | — | MIT, Apache-2.0 | when D6 schedules FactsBook |
| N-94 | widget redesign | Glance 1.2.0 (stable [V]) | 1.2.0 | Apache-2.0 | owner, widget redesign (freeze decision 2) |
| N-95 | video or audio files | Media3 | 1.11.1 [R media] | Apache-2.0 | only when a feature needs it |
| N-96 | Gemma assistant | Google LiteRT-LM | checked at that slice | Apache-2.0 | owner, after the switch (02 §3.5); must pass the S-23 banned-group check (no Play Services) |
| N-97 | AppFunctions | N-61 | 1.0.0-beta01 | Apache-2.0 | after the switch (D16) |

---

## 6. "Write ourselves" inventory (Table E)

Each is small, has a named home and an exact contract. Line counts are [est].

| # | What | Home | Size | Replaces | Contract / signature | Tests (catalog) |
|---|---|---|---|---|---|---|
| W-01 | `Digests` | `:core:common` (expect/actual) | ~60 lines | `crypto` | below | Dart-generated vectors for `DartJson.sha256Hex` (05 §2.5) |
| W-02 | `inflateRaw` | `:core:common` (expect/actual) | ~40 lines | `dart:io` `ZLibDecoder(raw: true)` | below | zip-bomb cap, corrupt input |
| W-03 | `LocaleFormats` | `:core:common` (expect/actual) | ~120 lines | `intl` formats | below | B-L10N-004, B-L10N-012 |
| W-04 | ZIP central-directory reader + office text | `:engine:media` | ~270 lines (port of `lib/domain/office_text.dart`) | own Dart code | `OfficeText.extract(bytes, kind): Outcome<String>` | B-MEDIA-006 |
| W-05 | `StaticSvg` sanitiser | `:engine:media` | ~260 lines (port of `lib/domain/static_svg.dart`) | own Dart code | `StaticSvg.sanitize(source: String): String?` | B-CHAT-107 |
| W-06 | hardened OkHttp builder, pinned-leaf `TrustManager`, SSE parser + `EventStream` | `:core:net-okhttp`, `:core:net` | 04 §3 | `dio` internals | 04 §3.2 | 04's list |
| W-07 | `SecretStore` / `KeystoreSecretStore` | `:core:data` (a) | ~150 lines | `flutter_secure_storage` | 05 §4.2 | B-SEC-056 |
| W-08 | flutter_secure_storage v11 reader | `:android:migration` | ~200 lines | — | 05 §7.5 | B-MIGR-006 |
| W-09 | `PromptPhotoEncoder` | `:android:platform` | ~150 lines | `image_picker` options | `suspend fun encode(uri: Uri, maxEdgePx: Int = 2048, jpegQuality: Int = 85): Outcome<EncodedPhoto>` | B-MEDIA-007, -008 |
| W-10 | `MicCapture` | `:android:media` | ~200 lines | `record` | `fun frames(): Flow<ShortArray>` (16 kHz mono PCM16, 4,096-sample buffers) | B-MEDIA-014, -023, -032 |
| W-11 | `WifiShareServer` | `:android:platform` | ~250 lines | `dart:io HttpServer` | GET/HEAD only, one 192-bit token path, 404 elsewhere, 405 for other methods; request line ≤ 8 KB; headers ≤ 32 KB; 10 s read timeout; binds the Wi-Fi address only | B-MEDIA-034 |
| W-12 | `AppUpdater` | `:android:platform` | ~600 lines | Shorebird | R-26 flow | B-MIGR-012, B-BUILD-010…012 |
| W-13 | link door | `:core:security` + `:ui:kit` + `:android:platform` | ~150 lines | `url_launcher` | `ExternalLinkPolicy.decide(url): LinkDecision` | B-SEC-064, -065 |
| W-14 | `TerminalSessionPort`, `LocalPtySession`, `RemoteTerminalSession`, `TerminalLinkScanner` | fork module + `:android:terminal` | ~100-line patch + ~500 lines | `xterm` | R-31 | B-XPLAT-009, -010, -011 |
| W-15 | `KitBidi` | `:ui:kit` | port | `intl` bidi helpers + own Dart | FSI U+2068 / LRI U+2066 / PDI U+2069, wrap once | B-L10N-006 |
| W-16 | `PerfTracePlugin` | `:core:net` | ~80 lines | dio interceptor | Ktor `createClientPlugin("PerfTrace")`: one span per request with method, path template, status and bytes | B-BUILD-025, -026 |
| W-17 | `CodeTokenizer` + SyntaxMP adapter + alias table | `:ui:kit-content` | ~200 lines | `highlight` glue | R-11 | B-CHAT-055, -056 |
| W-18 | notices generator | build-logic task `generateThirdPartyNotices` | ~200 lines | `tool/notices/regenerate.py` | §8.3 | B-BUILD-030 |

```kotlin
// :core:common commonMain — the only module allowed expect/actual (ADR §7.3 item 4)
package io.github.eslamasabry.oc.core.common.bytes

public expect object Digests {
    public fun sha256(bytes: ByteArray): ByteArray
    public fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}
/** Lower-case hex, exactly like Dart `crypto` `Digest.toString()`. */
public fun ByteArray.toLowerHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/** Raw DEFLATE (no zlib header), for ZIP entries. Returns null on corrupt input or when output would exceed the cap. */
public expect fun inflateRaw(input: ByteArray, maxOutputBytes: Int): ByteArray?

// androidMain and desktopMain actuals: java.security.MessageDigest("SHA-256"), javax.crypto.Mac("HmacSHA256"),
// java.util.zip.Inflater(nowrap = true). iosMain later: platform.CoreCrypto CC_SHA256 / CCHmac, platform.zlib.

package io.github.eslamasabry.oc.core.common.format

public expect object LocaleFormats {
    public fun number(value: Double, languageTag: String, maxFractionDigits: Int = 2): String
    public fun currency(amount: Double, isoCurrency: String, languageTag: String): String
    public fun date(epochMillis: Long, languageTag: String, style: DateStyle): String
    public fun time(epochMillis: Long, languageTag: String, use24Hour: Boolean): String
}
public enum class DateStyle { Short, Medium, Long }
// androidMain: android.icu.text.NumberFormat / DateFormat (API 24+). desktopMain: java.text / java.time.format.
```

---

## 7. Banned list (Table F)

Patterns match `group:artifact`. Unless the row says otherwise, a ban covers every configuration that ships:
`releaseRuntimeClasspath` of `:androidApp`, and later `:desktopApp`. Test-only use is allowed only where the row says
so. Data file: `native/quality/src/test/resources/banned-dependencies.txt` (one `pattern | X-id` per line).

| # | Pattern | Why | Use instead |
|---|---|---|---|
| X-01 | `com.google.android.gms:*`, `com.google.firebase:*`, `com.google.android.play:*`, `com.google.android.datatransport:*`, `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | F-Droid-ready and private (02 G8, S-23) | platform APIs |
| X-02 | `com.google.mlkit:*` | Google telemetry, 2.4 MB, freeze decision 1 | zxing-cpp (R-19) |
| X-03 | `dev.shorebird:*` | code push ends at the switch | `AppUpdater` (W-12) |
| X-04 | analytics and crash SDKs: `io.sentry:*`, `com.bugsnag:*`, `com.posthog:*`, `com.aptabase:*`, `com.mixpanel.android:*`, `com.amplitude:*`, `com.segment.analytics.kotlin:*`, `io.embrace:*`, `com.datadoghq:*`, `com.instabug.library:*`, `com.microsoft.appcenter:*` | S-23, NG15 (and see R12-1) | local diagnostics (`:engine:diagnostics`) |
| X-05 | `com.github.termux.termux-app:*` except `terminal-emulator` | GPLv3 (`termux-shared`, `app`) | K-04 + the fork |
| X-06 | `androidx.security:security-crypto*`, `eu.anifantakis:ksafe*` (Android), `com.liftric:kvault*`, `com.russhwolf:multiplatform-settings*`, `com.google.crypto.tink:*` | deprecated, or a third party would own our secret and preference formats | `SecretStore`, `KeyValueStore` |
| X-07 | `com.google.dagger:*`, `io.insert-koin:*`, `me.tatarka.inject:*` | ADR §3.3 (Koin is plan B and needs a coordinator change to this file) | Metro |
| X-08 | `com.slack.circuit:*`, `app.cash.molecule:*`, `com.arkivanov.decompose:*`, `com.arkivanov.mvikotlin:*`, `org.orbit-mvi:*`, `com.squareup.workflow1:*`, `cafe.adriel.voyager:*` | one state idiom for agents (ADR §3.3) | ViewModel + `StateFlow`, Nav3 |
| X-09 | `androidx.navigation:navigation-compose`, `org.jetbrains.androidx.navigation:navigation-compose` | Navigation 2 | Navigation 3 |
| X-10 | `androidx.glance:*` | ZTE launcher size-map trap; working RemoteViews (ADR §3.3) | RemoteViews; N-94 later |
| X-11 | `androidx.a2ui:*`, `androidx.compose.remote:*` | pre-1.0, Android-only, breaks kit-only (genui §0) | the `oc-ui` card contract |
| X-12 | `org.openapitools:*`, `io.ktor:ktor-client-content-negotiation`, `io.ktor:ktor-serialization-*`, `io.ktor:ktor-client-logging`, `com.squareup.okhttp3:logging-interceptor`, `io.ktor:ktor-client-cio` and `io.ktor:ktor-client-android` in app code | hand-written client; we decode with `WireJson`; bodies and headers are never logged; one engine | Ktor core + OkHttp engine |
| X-13 | `com.google.code.gson:*`, `com.squareup.moshi:*`, `com.fasterxml.jackson.*:*`, `org.json:json` outside test configurations | one serializer | kotlinx.serialization; platform `org.json` stays inside existing `:android:*` code |
| X-14 | `com.squareup.retrofit2:*`, `io.reactivex.rxjava*:*` | — | Ktor, coroutines |
| X-15 | `com.jakewharton.timber:*`, `io.github.aakira:napier*`, `org.slf4j:slf4j-android`, `org.slf4j:slf4j-simple` | one logger, redacted first | `AppLog` (Kermit) |
| X-16 | `io.arrow-kt:*` | — | `Outcome` / `Failure` |
| X-17 | `app.cash.paparazzi:*`, the `com.android.compose.screenshot` plugin | Android-only alpha / unmaintained (ADR §3.3) | Roborazzi |
| X-18 | `com.mikepenz:multiplatform-markdown-renderer*`, `com.halilibo.compose-richtext:*`, `io.github.huarangmeng:markdown*` | reference implementations only | own kit renderers on `org.jetbrains:markdown` |
| X-19 | `dev.snipme:highlights` outside the benchmark branch; any WebView-based highlighter or Markdown renderer | missing languages / not native | SyntaxMP behind `CodeTokenizer` |
| X-20 | `androidx.compose.material:material`, `org.jetbrains.compose.material:material` (Material 2), `*:material-icons-extended` | M3 kit only; icons are generated Phosphor | `:ui:kit` |
| X-21 | `dev.chrisbanes.haze:*` and other blur or glass libraries | no-glass rule; frost is `RenderEffect` inside the kit (D10) | `KitFrost` |
| X-22 | `com.airbnb.android:lottie*`, `io.github.alexzhirkevich:compottie*` | motion goes through kit tokens and framework APIs | `KitMotion` |
| X-23 | `com.google.accompanist:*` | deprecated pieces | activity / Compose APIs |
| X-24 | `androidx.appcompat:appcompat*`, `com.google.android.material:material` (MDC) as **direct** dependencies | `MainActivity` is a `ComponentActivity`; locale via `LocaleManager` (R12-3) | platform themes, `LocaleManager` |
| X-25 | any version containing `-alpha`, `-dev`, `-eap`, `-SNAPSHOT`, or `-beta` / `-rc` outside the M3 allowlist | D12 | stable releases |
| X-26 | dynamic versions: `+`, ranges, `latest.*`; versions declared in a module script | reproducible builds | the version catalog |
| X-27 | `org.nanohttpd:*` | unmaintained | `WifiShareServer` (W-11) |
| X-28 | `androidx.biometric:*` alphas | D12 | 1.1.0 (later) |
| X-29 | `com.android.billingclient:*`, `com.google.android.ump:*`, any ad SDK | no billing, no ads | — |
| X-30 | **API-level** bans inside allowed libraries (Konsist): `io.ktor.client.plugins.sse.*`, `io.ktor.client.plugins.HttpRequestRetry`, `io.ktor.client.plugins.contentnegotiation.*`, `io.ktor.client.plugins.logging.*`, `okhttp3.logging.*`, `okhttp3.CertificatePinner` (it pins SPKI after chain validation, not our self-signed leaf) | ADR §3.3, network-data §2–§3 | our SSE loop, explicit GET-only retries, `WireJson`, `AppLog`, `PinnedLeafTrustManager` |
| X-31 | debug-only tooling in release: `*:ui-tooling` (non-preview), `androidx.tracing:tracing-perfetto-binary`, `androidx.compose.runtime:runtime-tracing` | never ship debug and trace binaries (testing-perf §1) | `debugImplementation` or the `benchmark` build type only |

How each row is enforced (§12.1): the resolved release graph (`BannedDependenciesTest`) checks X-01 … X-23, X-27 …
X-29 and X-31. **Declared** dependencies (build-logic `ExternalRules`, at configuration time) check X-24, so a library
that pulls AppCompat in transitively later (androidx.biometric, for FactsBook) is a reviewed exception, not a build
break. `PrereleaseGuardTest` checks X-25. `CatalogOnlyTest` checks X-26 (no version literal in a module script; no `+`,
range or `latest.` in `libs.versions.toml`). Konsist checks X-30.

---

## 8. Licence policy and notices

### 8.1 Allowed licences

| Where | Allowed | Needs coordinator sign-off per artifact | Never |
|---|---|---|---|
| Release classpath (linked into the app process) | Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, Bouncy Castle Licence (MIT) | MPL-2.0 (file-level copyleft, unmodified only) | GPL, LGPL, AGPL, SSPL, BUSL, the JSON License, any "non-commercial" licence, proprietary SDK terms |
| Bundled fonts | OFL-1.1 | — | fonts without an embedding licence |
| Separate executables run inside the built-in Linux (C-05) | GPL-2.0 (proot), LGPL/GPL-3.0 (talloc), BSD-3-Clause (shmem), as today | any new one | linking any of them into the app process |
| Test and build tools | the release list + EPL-1.0/2.0 (JUnit) + the JSON License (K-06 only) | — | — |

### 8.2 Licensee configuration (in the `oc.android.application` convention plugin)

```kotlin
// build-logic: applied to :androidApp (and :desktopApp in the desktop phase). [U] exact property syntax for 1.14.1.
pluginManager.apply("app.cash.licensee")
extensions.configure<app.cash.licensee.LicenseeExtension> {
    allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allow("ISC")
    allowUrl("https://www.bouncycastle.org/licence.html") { because("MIT-style; only when FactsBook ships") }
    allowDependency("com.github.termux.termux-app", "terminal-emulator", "v0.118.3") {
        because("Apache-2.0 per termux-app LICENSE.md exception; the JitPack POM says 'Other'")
    }
    allowDependency("com.k2fsa.sherpa.onnx", "sherpa-onnx-android", "2.0.1") {
        because("Apache-2.0; ONNX Runtime inside is MIT; our vendored POM")
    }
    bundleAndroidAsset = true                              // feeds the About › Open-source licences list
    androidAssetReportPath = "licenses/artifacts.json"
}
```

### 8.3 Notices file

- `generateThirdPartyNotices` (W-18) writes `THIRD_PARTY_NOTICES.md` from Licensee's
  `build/reports/licensee/androidRelease/artifacts.json` plus `native/third_party/BUNDLED.toml` (Table C as data:
  name, version, licence, licence file, source URL, modification note).
- `NoticesCurrentTest` (T2) fails when the committed file differs from the generated one. This replaces "notices match
  `pubspec.lock`" (B-BUILD-030).
- `LICENSES/` gains `GPL-2.0-proot.txt`, `LGPL-3.0-talloc.txt` (plus GPL-3.0, which LGPL-3.0 incorporates),
  `BSD-3-Clause-libandroid-shmem.txt`, `Apache-2.0-zxing-cpp` attribution, `MIT-qrose.txt` and the Termux fork's
  `NOTICE`. It loses the Flutter-only texts (flutter-svg, path-parsing, vector-graphics, record,
  scrollable-positioned-list, image-picker, file-selection).
- The About screen lists `artifacts.json` entries plus `BUNDLED.toml` entries, through kit parts only.

---

## 9. Supply chain

### 9.1 Repositories (03 §3.1 is the file; the rules are these)

1. `google()`, `mavenCentral()`, `gradlePluginPortal()` (plugins only).
2. JitPack only through `exclusiveContent` for group `com.github.termux.termux-app`.
3. `third_party/maven` (local) only through `exclusiveContent` for `com.k2fsa.sherpa.onnx`.
4. `repositoriesMode = FAIL_ON_PROJECT_REPOS`, so no module can add a repository.

### 9.2 Gradle dependency verification (add)

- Commit `native/gradle/verification-metadata.xml` with SHA-256 for every artifact, so a JitPack rebuild or a tampered
  mirror fails the build.
- Create or refresh it on the VPS with the T2 task list:
  `./gradlew --write-verification-metadata sha256 :androidApp:assembleRelease :androidApp:testReleaseUnitTest
  desktopTest :quality:test`.
- Review rule: in a dependency PR, only entries of the bumped group may change. Any other changed checksum blocks the
  merge until explained.
- `--dependency-verification strict` is the default (`org.gradle.dependency.verification=strict` in
  `gradle.properties`).

### 9.3 Vendored artifacts

| Artifact | Fetched by | Pin | Committed? | Rebuild owner |
|---|---|---|---|---|
| sherpa-onnx 2.0.1 AAR | `tools/vendor/fetch.sh` | sha256 `a8d09d1d…0ee7` | POM yes, AAR no (git-ignored, 49 MB) | media owner |
| proot / talloc / shmem `.deb`s | `tool/builtin_linux/fetch_proot.sh` (moved to `tools/vendor/`) | per-file sha256 (C-05) | the extracted `.so` files, as today | runtime owner |
| Termux terminal-view source | one-time copy at tag v0.118.3 | git tag + `PATCHES.md` | yes (source) | kit-content owner |
| Rust `libaiteam_*` | `engine/phone/tool/build-android.sh` | hash manifest | as today | runtime owner (only with AI Team) |

`ThirdPartyHashTest` (03 §19.6) checks `third_party/SHA256SUMS`. CI and the VPS run `tools/vendor/fetch.sh` before
Gradle.

---

## 10. Update policy

### 10.1 Who

The **build-logic owner** (coordinator, ADR §5.2) is the only editor of `libs.versions.toml`,
`verification-metadata.xml`, `ExternalRules` and `DEPENDENCIES.md`. Feature agents propose; they never bump.

### 10.2 When (named triggers only)

| Group (moves as one PR) | Members | Trigger |
|---|---|---|
| G1 Toolchain | Kotlin, Compose compiler, Metro, KSP; then AGP + Gradle | ADR §8: Kotlin 2.5.0 once Metro and CMP declare support; AGP 9.5.x + Gradle 9.8.x together with it |
| G2 Compose | CMP (runtime … resources), material3, adaptive, Nav3, NavigationEvent, lifecycle, savedstate, ui-test | CMP 1.13.0 stable (first), then each CMP stable release; Android-only androidx *fix* bumps per ADR §7.6 |
| G3 AndroidX platform | core-ktx, work, activity, camera, profileinstaller, benchmark, tracing | monthly window; CameraX 1.7.x when stable |
| G4 Network and data | Ktor + OkHttp (OkHttp follows Ktor's `requires`), serialization, coroutines, datetime, io, collections, Room 3 + sqlite-bundled | monthly window |
| G5 Content | Coil, Telephoto, qrose, markdown, SyntaxMP, xmlutil | monthly window; SyntaxMP or Telephoto only with the kit-content owner's visual check |
| G6 Native binaries | sherpa-onnx, zxing-cpp, Termux emulator and fork, proot set, commons-compress | only on a named trigger (security fix, a needed feature, R12-6, R12-7), with a device proof |
| G7 Test and quality tools | everything in §5.4, Licensee, ktfmt, detekt | quarterly, or when a tool blocks |

- **Monthly window:** the first Monday of each month, on the VPS (never on the PC between midnight and 2 am). One PR per
  group that has an update; skip groups with nothing to do.
- **Security fixes:** a GitHub advisory or CVE that reaches code in the release classpath gets a fix PR within
  **72 hours**: critical or high severity, or anything in OkHttp, Ktor, commons-compress, zxing-cpp, sherpa-onnx,
  WebView handling or `SecretStore` paths. Medium and low wait for the window. Publishing the fixed release still needs
  the owner's explicit yes (AGENTS.md).
- **Watchers:** Dependabot keeps doing GitHub Actions (as today). For Gradle it is set to **security updates only**
  (`open-pull-requests-limit: 0` for version updates), because CI runs cost budget. Version news comes from
  `tools/deps/check_latest.py` (below), run in the monthly window.

### 10.3 `tools/deps/check_latest.py` (add, about 120 lines, Python 3, run with `python3 -I`)

- Reads `native/gradle/libs.versions.toml`.
- For every `module`, fetches `maven-metadata.xml` from Google Maven, Maven Central or JitPack.
- Prints one table row per library: current pin, latest stable, latest pre-release, `lastUpdated`.
- Marks a row `STALE` when the pin is more than 12 months older than the newest stable, and `ABANDONED?` when nothing
  has been published for 18 months (AndroidSVG 1.4 shows this today).
- Writes nothing; its output goes into the window PR text.

### 10.4 Per-change checklist (copy into every dependency PR)

1. Trigger: name the trigger from §10.2 and link the release notes you read.
2. Edit only `libs.versions.toml`, plus `verification-metadata.xml` (§9.2) and the matching `DEPENDENCIES.md` row
   (version, date, `[V]`).
3. T2 on the VPS: all JVM tests, Konsist, lint, `assembleRelease`, the R8 analyzer, the size gate, the component
   freeze, `BannedDependenciesTest`, `PrereleaseGuardTest`, Licensee, `NoticesCurrentTest`, `RegistryRowTest`.
4. Release-APK smoke on the emulator, because R8 breaks things only in release (map PACKAGES-BUILD-06): cold start,
   open a conversation, stream a reply. Add the scanner for G6 camera bumps, voice for sherpa bumps, a schedule firing
   for WorkManager, and the terminal for Termux.
5. G2 bumps also run T4: the Macrobenchmark journeys (ADR §7.6).
6. Size: paste `apkanalyzer apk compare old.apk new.apk` for the arm64 split. Over +500 KB needs the coordinator's
   sign-off; the existing 5 MiB release growth gate stays.
7. Regenerate notices (`generateThirdPartyNotices`) when the classpath changed.
8. Commit message `build(deps): <group> <old>→<new> [skip ci]` with the trailers G33 requires.

### 10.5 Pre-release rule (D12, as a test)

The release classpath may contain no `-alpha`, `-dev`, `-eap`, `-SNAPSHOT`. A `-beta` or `-rc` is allowed only for
`org.jetbrains.compose.material3:*` and `androidx.compose.material3:*`, and only if Google has not shipped M3 1.5.0
stable by then. Before the switch, pre-release Compose pins are allowed on development builds; `PrereleaseGuardTest`
runs in **report** mode until the switch candidate, then in **fail** mode.

---

## 11. Size budget (arm64 split, compressed) [est]

| Part | Today (4511) | Native at the switch |
|---|---|---|
| Dart AOT + engine (`libapp`, `libflutter`) | 19.6 MB (12.9 + 6.7) | 0 |
| App code: Compose, M3, Nav3, Ktor, OkHttp, serialization, Metro, Coil, Telephoto, markdown, SyntaxMP, xmlutil, CameraX Java, WorkManager, our Kotlin | 2.0 MB dex | about 4–5 MB dex (the demo's 2.5 MB covered the first nine; our code adds about 2 MB) |
| Speech: ONNX Runtime + sherpa | 9.8 MB | 9.8 MB (R12-6 may cut it) |
| QR | 2.1 MB + 0.9 MB models | 0.73 MB zxing-cpp + about 0.1 MB CameraX helpers |
| proot set + `libtermux` | about 0.2 MB | about 0.2 MB |
| Rust AI Team engine | 3.0 MB | 0 until AI Team ships (D6) |
| Room `sqlite-bundled` | — | 0 at the switch (D9 default); about 0.6–1.3 MB when Room lands |
| Resources, strings (7 languages), fonts, licences | about 2.5 MB | about 2 MB (Phosphor TTFs gone; 7-language XML) |
| **Total** | **≈ 36 MB** | **≈ 17–19 MB** |

Budget rule (proposed): the arm64 switch APK stays **≤ 20 MiB**; the universal APK (arm64 + x86_64) stays ≤ 32 MiB.
`check_apk_size.py` keeps its 5 MiB growth gate against the previous tag's arm64 asset.

---

## 12. Acceptance checks (all run at T2 unless marked)

### 12.1 Tests and tasks

| Check | Where | Fails when |
|---|---|---|
| `releaseClasspathReport` task | build-logic, on `:androidApp` | — (writes `build/reports/oc/release-classpath.txt`, one `group:artifact:version` per line) |
| `BannedDependenciesTest` | `:quality` | a line matches `banned-dependencies.txt` (the resolved-graph rows: X-01 … X-23, X-27 … X-29, X-31) |
| `PrereleaseGuardTest` | `:quality` | X-25, outside the M3 allowlist (report mode until the switch candidate) |
| `RegistryRowTest` | `:quality` | a `[libraries]` alias in `libs.versions.toml` has no row in `native/docs/DEPENDENCIES.md`, or a row's version differs from the catalog |
| `ExternalRules` (03 §6, extended in 12.2) | build-logic, configuration time | a module declares a library it may not |
| Konsist X-30 rules | `:quality` | a banned import inside an allowed library |
| Licensee `licenseeAndroidRelease` | `:androidApp` | a licence is outside §8.1 |
| `NoticesCurrentTest` | `:quality` | `THIRD_PARTY_NOTICES.md` is stale |
| `ThirdPartyHashTest` | `:quality` | a vendored file's SHA-256 differs |
| dependency verification | every Gradle run | a checksum differs (§9.2) |
| `CatalogOnlyTest` | `:quality` | a `build.gradle.kts` outside build-logic contains a `"group:artifact:version"` literal |
| size gate | T2 + release | §11 budget or the 5 MiB growth rule |
| release smoke (T3) | emulator, release build | R8 strips sherpa JNI fields, `WorkDatabase_Impl`, zxing-cpp, a serializer, or a Metro graph accessor |

```kotlin
// native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/BannedDependenciesTest.kt
class BannedDependenciesTest {
    private val classpath: List<String> =
        File(requireNotNull(System.getProperty("oc.releaseClasspath"))).readLines().filter { it.isNotBlank() }
    private val banned: List<Pair<Regex, String>> =
        javaClass.getResource("/banned-dependencies.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> val (pattern, id) = line.split("|").map(String::trim)
                   // anchored on both sides of group:artifact, so "material:material" never hits "material-ripple"
                   Regex("^" + pattern.replace(".", "\\.").replace("*", "[^:]*") + ":") to id }

    @Test fun `S-23 no banned artifact ships`() {
        val hits = classpath.flatMap { gav -> banned.filter { (re, _) -> re.containsMatchIn(gav) }.map { "$gav (${it.second})" } }
        assertTrue(hits.isEmpty(), "Banned in releaseRuntimeClasspath (see plan §12 Table F):\n" + hits.joinToString("\n"))
    }
}

class PrereleaseGuardTest {
    private val anyPre = Regex("-(alpha|dev|eap|snapshot|beta|rc)", RegexOption.IGNORE_CASE)
    private val betaOrRc = Regex("-(beta|rc)", RegexOption.IGNORE_CASE)
    private val m3Groups = listOf("org.jetbrains.compose.material3:", "androidx.compose.material3:")
    private val failMode = System.getProperty("oc.switchCandidate") == "true"

    @Test fun `D12 no pre-release outside the M3 allowlist`() {
        val bad = File(requireNotNull(System.getProperty("oc.releaseClasspath"))).readLines().filter { gav ->
            val version = gav.substringAfterLast(':')
            val allowedM3Beta = m3Groups.any(gav::startsWith) && betaOrRc.containsMatchIn(version)
            anyPre.containsMatchIn(version) && !allowedM3Beta
        }
        if (failMode) assertTrue(bad.isEmpty(), "Pre-release in the switch release (ADR D12): $bad")
        else println("D12 report (not failing before the switch candidate): $bad")
    }
}
```

```kotlin
// build-logic, inside the oc.android.application convention plugin. No cross-project configuration (03 §3.2).
abstract class ReleaseClasspathReport : DefaultTask() {
    @get:Input abstract val modules: ListProperty<String>
    @get:OutputFile abstract val output: RegularFileProperty
    @TaskAction fun write() = output.get().asFile.writeText(modules.get().sorted().joinToString("\n"))
}

internal fun moduleCoordinates(root: ResolvedComponentResult): List<String> {
    val seen = HashSet<ComponentIdentifier>(); val out = ArrayList<String>(); val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        if (!seen.add(c.id)) continue
        (c.id as? ModuleComponentIdentifier)?.let { out += "${it.group}:${it.module}:${it.version}" }
        c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue += it.selected }
    }
    return out
}

tasks.register<ReleaseClasspathReport>("releaseClasspathReport") {
    modules.set(configurations.named("releaseRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }.map(::moduleCoordinates))   // [U] config-cache check in P1
    output.set(layout.buildDirectory.file("reports/oc/release-classpath.txt"))
}
// :quality/build.gradle.kts reads the file by path; T2 runs :androidApp:releaseClasspathReport before :quality:test.
// tasks.test { systemProperty("oc.releaseClasspath",
//     layout.projectDirectory.file("../androidApp/build/reports/oc/release-classpath.txt").asFile.path) }
```

### 12.2 `ExternalRules` additions for 03 §6 (build-logic owner)

```kotlin
// Add to ExternalRules.allowed (03 §6). Everything not listed stays free for any module that the layer rules allow.
"io.coil-kt.coil3" to { p -> p == ":ui:kit" || p == ":ui:kit-content" },
"me.saket.telephoto" to { p -> p == ":ui:kit-content" },
"io.github.alexzhirkevich" to { p -> p == ":ui:kit-content" },
"com.gallatinapps.syntaxmp" to { p -> p == ":ui:kit-content" },
"io.github.pdvrieze.xmlutil" to { p -> p == ":engine:media" },
"org.apache.commons:commons-compress" to { p -> p == ":android:linux" },
"androidx.work" to { p -> p in setOf(":android:schedule", ":android:widgets", ":android:background", ":androidApp") },
"androidx.appfunctions" to { p -> p == ":android:platform" },                    // after the switch
"org.bouncycastle" to { p -> p == ":android:factsbook" },                         // later, D6 (module name fixed then)
"androidx.biometric" to { p -> p == ":android:factsbook" },                       // later, D6
"androidx.datastore" to { p -> p == ":core:data" },                               // desktop/iOS source sets only
"androidx.profileinstaller" to { p -> p == ":androidApp" },
"androidx.appcompat" to { _ -> false },                                           // X-24 (R12-3)
"com.google.android.material" to { _ -> false },                                  // X-24, MDC
"io.github.zxing-cpp" to { p -> p == ":ui:kit-content" || p == ":android:media" }, // already in 03
"co.touchlab" to { p -> p == ":core:common" },                                    // already in 03
"com.squareup.okhttp3" to { p -> p == ":core:net-okhttp" },                       // already in 03 (tests exempt)
```

And in `ModuleRules` (03 §6): `to.startsWith(":thirdparty:")` is allowed only from `:ui:kit-content` and
`:android:terminal` (R12-2).

### 12.3 Slice placement

| Slice | Registry work |
|---|---|
| 0 | catalog + verification metadata + `ExternalRules` additions; `releaseClasspathReport`, `BannedDependenciesTest`, `PrereleaseGuardTest` (report mode), `RegistryRowTest`, `CatalogOnlyTest`; Licensee trial (N-12); `DEPENDENCIES.md` seeded from §1–§7; `tools/vendor/fetch.sh`; W-01, W-02 |
| 1 | W-03, W-07, W-06 (with 04) |
| 2 | R-11 benchmark decision; W-17; W-16 |
| 3 | R-31 fork (W-14) + P9 addendum; K-03 / R12-7; W-10 |
| 4 | W-12 `AppUpdater` (it ships in the switch release); W-11 |
| 5 | R-19 scanner; R-24 sherpa + R12-6; W-04, W-05, W-09 |
| 6 (switch) | `PrereleaseGuardTest` in fail mode; notices with C-05 fixed (R12-8); size budget check; licence review of the final classpath |

---

## 13. Decisions needed

Each has a default; work proceeds on it until the named person answers.

| # | Decision | Who | Default |
|---|---|---|---|
| R12-1 | **Analytics.** The ADR (§9.6 "nothing phones home") and 02 (G7, NG15) say no telemetry. The owner's 2026-11-01 memory (freeze decision 6) says the opposite for the native app: "build tracking SDK day one that can later talk to other analytics apps", with BASIC always on and DETAILED opt-in. These cannot both be true at the switch. | **owner** | The registry bans every third-party analytics SDK either way (X-04). If you confirm tracking, it is our own event API in a new engine, sending over `:core:net` with no vendor SDK, and the ADR and 02 are amended. F-Droid then shows the "Tracking" anti-feature for an always-on tier. Until you answer: nothing is sent. |
| R12-2 | Fork Termux `terminal-view` (W-14) as `:thirdparty:termux-terminal-view`, and add `thirdparty` to the module discovery folders (03 §3.1). | coordinator | Fork, because the stock view cannot show remote PTYs (R-31, verified in source). The alternative is our own `TerminalRenderer`-based view, about 1,500 lines. |
| R12-3 | No `androidx.appcompat`: the launch themes move from `Theme.AppCompat.DayNight.NoActionBar` to platform `android:Theme.Material.Light.NoActionBar` with a `values-night` override, and 03's `AppCompatLocaleHost` becomes `PlatformLocaleHost` (`LocaleManager`, as 06 K16 already says). | coordinator + android-platform owner | No appcompat. |
| R12-4 | Remove `coil-network-ktor3` from `:androidApp` / `:desktopApp` (03 §7.1). Images come only through the gateway `Fetcher`, so Coil should be unable to fetch a URL at all. | build-logic owner | Remove. |
| R12-5 | Add `io.github.pdvrieze.xmlutil:core` to ADR §7.2's `commonMain` allowed list. | coordinator | Add (one line in the ADR). |
| R12-6 | Measure the `sherpa-onnx-static-link-onnxruntime-2.0.1.aar` variant. | media owner, slice 5 | Adopt only if the arm64 APK shrinks by ≥ 1 MB and the release smoke plus the dictation and Kokoro tests pass. |
| R12-7 | commons-compress 1.27.1 → 1.28.0. | runtime owner, slice 3 | Move once the Ubuntu Base tar fixtures pass on 1.28.0; otherwise stay. |
| R12-8 | **Licence gap for proot, talloc and shmem** (C-05): add the texts, the exact versions, the patchelf note and a source pointer (the Termux package sources at the pinned versions, mirrored as a release asset or offered in writing). This also affects today's Flutter releases. | owner approves the wording; runtime owner does it | Mirror the three source tarballs as assets of each release; mention them in `THIRD_PARTY_NOTICES.md`. |
| R12-9 | FactsBook crypto, when it is scheduled: BouncyCastle 1.86 light-weight Ed25519 on every API level, or platform `Signature("Ed25519")` on API 33+ with BouncyCastle below. | FactsBook builder | BouncyCastle everywhere (one code path, RFC 8032 vectors, B-SEC-066). |
| R12-10 | Licensee vs our own notices task only. | build-logic owner, slice 0 | Licensee if it runs on AGP 9.3.3 + Gradle 9.7.1; otherwise W-18 alone reads POM licences. |

---

## 14. Disagreements found with sibling sections (for the coordinator)

1. **03 §5** declares `ktor-client-websockets`; **04 §2.3** says it is not used on Android or desktop and adds
   `ktor-websockets`. The registry follows 04 (R-07).
2. **03 §7.1** puts `coil-network-ktor3` in both app modules; media §2 says Coil must never fetch server URLs (R12-4).
3. **03 §12** `LocaleHost` "Android: `AppCompatDelegate`" vs **06 K16** `LocaleManager`; with a `ComponentActivity`,
   AppCompat's backport does nothing below API 33 anyway (R12-3).
4. **ADR §8 / 03 §3.1** take Termux `terminal-view` from JitPack; a remote PTY needs the fork (R-31, R12-2). ADR §4
   row 15's decision (Termux view on day 1) stands.
5. **ADR §7.2** `commonMain` list lacks xmlutil (R12-5).
6. **ADR §9.6 / 02 NG15** vs owner memory freeze decision 6 on analytics (R12-1).
7. **Today's notices** omit proot, talloc and shmem (R12-8).
8. **03 §6 `ExternalRules`** lacks entries for Coil, Telephoto, qrose, SyntaxMP, xmlutil, commons-compress, WorkManager
   and the later libraries (§12.2 gives them).

---

## 15. Sources and verification log (2026-11-01)

- Repo (read-only): `/home/eslam/Storage/Code/wt/oc_app-batch1/pubspec.yaml`, `THIRD_PARTY_NOTICES.md`, `LICENSES/`,
  `android/app/src/main/res/values/styles.xml` (AppCompat parents), `lib/domain/static_svg.dart`,
  `lib/domain/office_text.dart`, `lib/platform/wifi_file_share.dart`, `tool/builtin_linux/fetch_proot.sh`, the Kotlin
  import inventory under `android/app/src/main/kotlin` (56 `org.json` imports).
- Plan: `01-architecture-decision.md` (§3.3, §5, §6, §7, §8, §9, §12), `02-principles-scope.md` (G7, G8, S-23, NG15),
  `03-architecture-modules.md` (§3, §5, §6, §7.1, §19), `04-protocols-networking.md` §2.3, `05-data-migration.md`
  (§2.5, v11 recipe), `06-ui-kit-design-system.md` (K6, K7, K16, §4.11, §12.1).
- Maps: `../map/packages-build.md` (§0, PACKAGES-BUILD-03 … 41, §4 dependency table). Research: `kmp-cmp-status.md`,
  `architecture-stacks.md`, `network-data.md` (§0, §2, §6, §7, §8), `media.md` (§0, §2, §4, §7, §12),
  `rendering-chat.md` (§0, §5), `android-surfaces.md` (§0, §1, §13), `testing-perf.md` §1, `genui.md` §0.
- Catalog rows cited: B-L10N-001…013, B-MIGR-005…013, B-SEC-056/058/064/065/066/095/139/146/153, B-CHAT-053/055/056/107,
  B-MEDIA-006/007/008/014/023/031/032/033/034, B-PROTO-037/069/122, B-ANDR-049, B-KIT-026, B-XPLAT-004…011,
  B-BUILD-010…012/025/026/030, B-AGENT-205/207, B-NAV-001.
- Owner memory: `freeze-owner-decisions-2026-11-01`, `dev-freeze-native-rewrite-2026-11-01`, `app-updates-two-layers`,
  `gemma-assistant`, `no-glass-rule`, `android-native-feel`, `voice-model-memory-gate`, `pc-capacity-ceiling`,
  `quiet-hours-vps-only`.
- **[V] today**, Maven Central / Google Maven `maven-metadata.xml`: commons-compress latest 1.28.0; xmlutil core
  1.0.2 (2026-08-25, Apache-2.0, `core-android` → `core-jvm`); bcprov-jdk18on 1.86; FileKit 0.17.0; Tink Android
  1.24.0; kotlin-multiplatform-diff 1.4.0; Okio 3.18.2; org.json latest 20260814; AndroidSVG 1.4 (last updated
  2019-05-28); cryptography-kotlin 0.6.0; KotlinCrypto sha2 0.8.0; qrose 1.3.1; SyntaxMP 0.3.0; Telephoto 0.19.0 stable
  and 1.0.0-alpha02 (2026-04-02); androidx.biometric latest 1.4.0-alpha07 (stable still 1.1.0); androidx.webkit
  1.18.0; core-ktx 1.19.1; work-runtime 2.12.0; CameraX 1.6.2 stable and 1.7.0-rc01; AppFunctions 1.0.0-beta01;
  profileinstaller 1.4.1; androidx.exifinterface 1.4.2 stable; Glance 1.2.0 and 1.3.0-alpha02; Jetpack M3
  1.5.0-beta02; security-crypto 1.1.0; Licensee 1.14.1 (CHANGELOG); detekt 1.23.8 and 2.0.0-alpha.6;
  dependency-analysis 3.20.1; kotest-property 6.2.6; json-schema-validator 3.0.8; Kover 0.9.11;
  `androidx.navigation3:navigation3-runtime` 1.2.0 publishes a desktop variant.
- **[V] POM licences:** zxing-cpp Apache-2.0, SyntaxMP Apache-2.0, qrose MIT, Telephoto Apache-2.0, AndroidSVG
  Apache-2.0, JetBrains markdown Apache-2.0, Metro Apache-2.0, Kermit Apache-2.0, bcprov "Bouncy Castle Licence",
  org.json 20180813 "The JSON License", xmlutil Apache-2.0; Termux terminal-view POM licence "Other".
- **[V] raw artifact sizes** (Content-Length): listed in Tables A and D.
- **[V] GitHub:** sherpa-onnx v2.0.1 (2026-10-26) assets and SHA-256 digests (`sherpa-onnx-2.0.1.aar` 49,441,341 B,
  `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`; static-link variant 38,577,050 B); Termux
  `termux-app` tag v0.118.3 sources: `TerminalSession` is `public final class` and `initializeEmulator` calls
  `JNI.createSubprocess`, `TerminalOutput` is `public abstract`, `TerminalView.attachSession(TerminalSession)`;
  JitPack serves `terminal-view` and `terminal-emulator` v0.118.3 (HTTP 200); `termux/libandroid-shmem` LICENSE is
  BSD 3-Clause; Termux `packages/proot/build.sh` licence GPL-2.0, `packages/libtalloc/build.sh` licence GPL-3.0.
- **[V] kotlinlang.org:** "What's new in Kotlin 2.4.0": UUID API stable in common stdlib; "What's new in 2.3.0":
  `kotlin.time.Clock` and `kotlin.time.Instant` stable.
- **[U], settled later:** CMP 1.13.0 stable date; Licensee on AGP 9.3.3; the exact Licensee 1.14.1 property syntax;
  the configuration-cache-safe form of `releaseClasspathReport`; SyntaxMP's quality on real agent output; the
  static-link sherpa AAR's size; Dependabot security-update support for Gradle version catalogs.
