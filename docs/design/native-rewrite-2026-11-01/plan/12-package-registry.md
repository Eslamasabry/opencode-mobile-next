# 12. Package registry: what replaces every Flutter package

Written 2026-11-01. This section decides **which library (or no library) does each job** in the native app. It sits
under `01-architecture-decision.md` (the ADR, binding) and next to `03-architecture-modules.md` §5, which holds the
exact `libs.versions.toml`. The ADR wins over this file; this file wins over the catalog on *which* library is used;
the build-logic owner keeps the two in step (`RegistryRowTest`, §12).

## For the owner, in five lines

1. All 43 Flutter packages now have a named native answer: 13 become features already built into Android or Kotlin,
   15 become a native library, 5 become a little code of our own, 8 are simply dropped, and 2 (FactsBook only) wait
   until after the switch.
2. The phone download should shrink from about 36 MB to about 17–18 MB (an estimate until the first native release
   build is measured); roughly 10 MB of that is the offline voice engine, which stays inside the app on purpose.
3. No library from Google Play Services, Firebase, ML Kit, Shorebird or any analytics company can enter any build of
   the app: a check fails if one sneaks in, and library licences are checked automatically. The one Google name left is
   a manifest line that lets older phones use the system photo picker; an F-Droid build drops it. What still stands
   between us and F-Droid (prebuilt voice, Linux, terminal and scanner parts) is listed in §7.1 and in section 16.
4. Two things need fixing on the way: the terminal view must be lightly forked so server terminals can show, and the
   licence notices for the built-in Linux tools (proot) and for the parts inside the voice engine are missing today
   and must be added.
5. Libraries change only for a named reason (a monthly window, or a security fix within 72 hours), and every change
   carries a checksum, a licence check and a size report. Nothing in this section waits on you: where the usage counts
   go is section 16's D16-1, and the source-code offer for the Linux tools is 16's D16-8.

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
the raw artifact size on Maven today [V raw], which is an upper bound before R8 shrinks it. The 4511 build has no
`--split-debug-info`, so §11's "Today" column uses the published arm64 split instead (Dart AOT 9.4 MB, not 12.9 MB).

### 0.4 What an agent does before adding any dependency (step list)

1. Search this file and `native/docs/DEPENDENCIES.md` (the living copy of §1–§7). If a row exists, use exactly that
   library and version. If the library is in §7 (banned), stop.
2. If no row exists, write a proposed row in your slice's `contracts/<slice>.md` with every column of Table D:
   coordinates, version, targets, licence, raw size, risk, the module that declares it, and what it replaces.
3. The build-logic owner (ADR §5.2, coordinator) adds it to `native/gradle/libs.versions.toml`, to
   `ExternalRules` (03 §6, extended in §12.2 here), to `native/docs/dependencies.toml` (the machine-readable registry,
   §12.1) and to `DEPENDENCIES.md`, in one commit.
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
| R-01 | `flutter` SDK (Shorebird engine 3.47.1) | the whole UI toolkit and engine (all) | LIB | Compose Multiplatform **1.13.0-beta01** (on Android = Jetpack Compose 1.13.x); fallback 1.12.2 for development only (it pairs with material3 1.12.0-alpha03, which D12 forbids at the switch, §10.5); must be a stable 1.13.x at the switch (D12) | `org.jetbrains.compose.runtime:runtime`, `org.jetbrains.compose.foundation:foundation`, `org.jetbrains.compose.ui:ui`, `org.jetbrains.compose.animation:animation` + plugin `org.jetbrains.compose` | ✓/✓/✓ | Apache-2.0 | libflutter 6.68 + libapp 9.4 MB (published split; 12.90 in the 4511 test build) → about 2–2.5 MB of dex for Compose, M3, Nav3 and Ktor together (the demo APK was 2.5 MB) [R] | Medium | `oc.kmp.compose` modules |
| R-02 | `flutter_localizations` SDK | Material/Cupertino delegates, RTL, locale plumbing | LIB | CMP resources (`strings_<feature>.xml`, `<plurals>`) in `:ui:words`; per-app language through `LocaleManager` on API 33+ and a configuration override on 26–32; the system language list from AGP's generated LocaleConfig (06 K16, §13.6) | `org.jetbrains.compose.components:components-resources` 1.13.0-beta01 | ✓/✓/✓ | Apache-2.0 | 6.9 MB of generated Dart source → plain resource XML [R] | Medium (P6) | `:ui:words` |
| R-03 | `intl` 0.20.3 (`any`) | dates, numbers, currency, plurals beside gen-l10n, relative times (34/2) | PLATFORM | plurals → CMP `<plurals>` (CLDR); numbers, percents, currency, date skeletons, fixed patterns and times → `LocaleFormats` (W-03) over `android.icu.text.*` (API 24+); relative times from `:ui:words` plurals as today; bidi → `KitBidi` port (W-15) | none (platform `android.icu`; `java.text`/`java.time.format` on desktop; `NSDateFormatter`/`NSNumberFormatter` on iOS, compiled by the nightly klib job from day one) | ✓/✓/✓ | — | 0 | Medium (B-L10N-012 digit and currency parity) | `:core:common` (a/d/i) |
| R-04 | `cupertino_icons` 1.0.9 | nothing: zero imports (0/0) | DROP | — | — | — | MIT | −0.26 MB font [R] | None | — |
| R-05 | `clock` 1.1.2 | injectable time for `KitSince` and tests (38/67) | PLATFORM | `kotlin.time.Clock` (stable since Kotlin 2.3.0 [V]) behind `:core:common` `Clock`; `TestClock` in `:core:testing` | stdlib | ✓/✓/✓ | Apache-2.0 | 0 | Low | `:core:common` |
| R-06 | `desktop_drop` 0.8.4 | desktop file drop; its Android plugin class compiles but is never used (1/0) | DROP | desktop phase: CMP `Modifier.dragAndDropTarget` (built in) | — | –/–/later | Apache-2.0 | removes one Gradle subproject and its KGP warning | None | `:desktopApp` (later) |
| R-07 | `dio` 5.11.1 | every HTTP call, SSE, the perf interceptor (25/38) | LIB | Ktor client **3.6.1** + OkHttp **5.5.1** engine (hardened builder W-06 in `:core:net-okhttp`); own SSE loop; own `PerfTracePlugin` (W-16) | `io.ktor:ktor-client-core`, `io.ktor:ktor-client-okhttp`, `io.ktor:ktor-websockets`, `com.squareup.okhttp3:okhttp` | ✓/later (`ktor-client-darwin`)/✓ | Apache-2.0 | 3.6 MB raw jars [V raw] → about 0.6–0.9 MB after R8 [est] | Medium (OkHttp retry and redirect defaults) | `:core:net`, `:core:net-okhttp`, `:protocol:*` |
| R-08 | `file_picker` 12.2.0 | attach, import, export, save (5/1) | PLATFORM | `ActivityResultContracts.OpenDocument`, `OpenMultipleDocuments`, `CreateDocument(mime)` behind `ActivityResults` (ADR §7.3) | `androidx.activity:activity` 1.13.0 (already present) | ✓/later (FileKit 0.17.0)/later (FileKit) | Apache-2.0 | 0 | Low | `:android:platform` |
| R-09 | `flutter_riverpod` 3.4.3 | provider graph / service locator around the controllers (82/197) | LIB | Metro **1.5.1** (compile-time DI, `AppScope`, `ConnectionScope`) + ViewModel and `StateFlow` (lifecycle **2.11.0**); plan B Koin 4.2.2 | `dev.zacsweers.metro:runtime`, `dev.zacsweers.metro:metrox-viewmodel-compose`, plugin `dev.zacsweers.metro`; `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | ✓/✓/✓ | Apache-2.0 | Metro runtime 152 KB raw [V raw] | Medium (compiler plugin moves with Kotlin) | annotations anywhere; graphs in apps |
| R-10 | `flutter_timezone` 5.1.0 | IANA zone id for usage days and reminders (2/0) | LIB | `kotlinx.datetime.TimeZone.currentSystemDefault().id` (kotlinx-datetime **0.8.0**) | `org.jetbrains.kotlinx:kotlinx-datetime` | ✓/✓/✓ | Apache-2.0 | shared with the rest of the app | Low | any KMP module |
| R-11 | `highlight` 0.7.0 | colours in code blocks and diffs (2/0) | LIB | SyntaxMP **0.3.0** behind our `CodeTokenizer` (W-17); benchmark against Highlights 1.1.0; fallback kotlin-textmate 0.3.0 (A/D only) or plain text | `com.gallatinapps.syntaxmp:syntaxmp-tokenizer` | ✓/✓/✓ | Apache-2.0 [V POM] | 554 KB raw [V raw] | Medium–High (4 stars, young; Apache-2.0 so we can vendor it) | `:ui:kit-content` |
| R-12 | `flutter_svg` 2.3.0 | SVG file preview; `KitIcon`; bundled marks (3/6) | LIB + OWN | previews: our `StaticSvg` sanitizer (W-05), then Coil **3.6.3** `coil-svg` (AndroidSVG 1.4 on Android, Skia elsewhere); icons and marks: generated `ImageVector`s (06 §4.11, C-02, C-03) | `io.coil-kt.coil3:coil-svg` (pulls `com.caverock:androidsvg-aar:1.4`) | ✓/✓/✓ | Apache-2.0 [V POM] | 23 KB + AndroidSVG 198 KB raw [V raw] | Medium (AndroidSVG's last release is 2019-05 [V]; only sanitised SVG reaches it) | `:ui:kit-content` |
| R-13 | `xml` 7.0.1 | SVG sanitiser events and DOM; Word/Excel/PowerPoint text (2/2) | OWN | our own small XML pull reader `XmlPull` (W-19; 10 M7): no DTD, no external entities, size and depth caps; shared by `StaticSvg` and `OfficeText` | none | ✓/✓/✓ | — | 0 | Low–Medium (XXE refused by construction) | `:engine:media` |
| R-14 | `flutter_secure_storage` 11.2.0 | server passwords, tokens, keys (9/21) | OWN | `SecretStore` over an AndroidKeyStore AES-256-GCM key, AAD = entry name, file `oc_secrets_v1` (W-07); one-time v11 decryptor in `:android:migration` (W-08) | none (AndroidKeyStore, `javax.crypto`) | ✓/later (Keychain)/later (Secret Service) | — | 0 | High (data loss if wrong; byte backup + read-back verify) | `:core:data` (a), `:android:migration` |
| R-15 | `crypto` 3.0.7 | SHA-256 scopes and fingerprints, vault ids, HMAC-SHA256 identity salts, SHA-1 git objects, MD5 default-logo check (36/20) | OWN | the `:core:common` primitives of W-01 (10 §4.2 names, extended): incremental `Digest` (SHA-256, SHA-1, MD5) with `Sha256()`, `hmacSha256`, `secureRandomBytes`. `MessageDigest`/`Mac`/`SecureRandom` on Android and desktop; CommonCrypto and `SecRandomCopyBytes` on iOS, in the same PR | none | ✓/✓/✓ | — | 0 (about 120 lines with the iOS actuals) | Low (Dart-exact hex, golden vectors) | `:core:common` |
| R-16 | `cryptography` 2.9.0 | Ed25519 for the FactsBook owner key (1/0) | LATER | FactsBook ships after the switch (D6). Then: BouncyCastle **1.86** light-weight `Ed25519Signer` (no provider registration) | `org.bouncycastle:bcprov-jdk18on` | ✓/later/✓ | MIT (Bouncy Castle Licence) [V POM] | 7 MB raw [V raw] → under 150 KB with R8, light-weight API only [est] | Medium | FactsBook's Android module (later) |
| R-17 | `local_auth` 3.0.2 | FactsBook device check before signing (1/1) | LATER | FactsBook only. Then the platform, no library (default, R12-9): API 30+ `android.hardware.biometrics.BiometricPrompt` with `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`; API 26–29 `KeyguardManager.createConfirmDeviceCredentialIntent` through `ActivityResults`. Fallback: androidx.biometric **1.1.0** (the only stable) hosted in a dedicated `FragmentActivity`, a named X-24 exception | none (fallback `androidx.biometric:biometric`) | ✓/later/– | Apache-2.0 | 0 (fallback: 190 KB raw plus appcompat 1.2.0 and fragment 1.2.5 transitively [V POM]) | Low | FactsBook's Android module (later) |
| R-18 | `image_picker` 1.2.3 | prompt photos (2048 px edge, JPEG 85, no metadata), camera, card photos (3/4) | PLATFORM + OWN | `PickVisualMedia`, `PickMultipleVisualMedia`, `TakePicture` + `FileProvider`; own `ImageCodec` / `AndroidImageCodec` (W-09, 10 §10); EXIF rotation on API 26–27 from the platform `android.media.ExifInterface(InputStream)` (10 §2.1 agrees) | `androidx.activity:activity` 1.13.0, `androidx.core:core-ktx` 1.19.1 (both already present) | ✓/later/later | Apache-2.0 | 0 | Medium (process death with the camera open, B-MEDIA-008) | `:android:platform` (pickers), `:android:media` (codec) |
| R-19 | `mobile_scanner` 7.4.2 | pairing QR scan: CameraX + bundled ML Kit (1/0) | LIB | CameraX **1.6.2** (`CameraXViewfinder`, `ImageAnalysis`) + zxing-cpp **3.1.1**: Google-free (owner freeze decision 1) | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` (1.6.2); `io.github.zxing-cpp:android` (3.1.1) | ✓/later (AVFoundation)/later (paste only, 10 §14.2) | Apache-2.0 [V POM] | ML Kit 2.11 MB + 0.9 MB models → zxing 0.73 MB [I] + CameraX JNI helpers about 0.1 MB; CameraX dex (≤ 1.2 MB, 10 §2.5 budget, unmeasured) is already in today's APK through `mobile_scanner` and is counted in the §11 app-code row | Low–Medium | `:ui:kit-content` (a), `:android:media` |
| R-20 | `path_provider` 2.1.6 | app folders (17/0) | PLATFORM | `Context.filesDir`, `cacheDir`, `getExternalFilesDir`, behind an `AppDirs` binding; the migration reads `File(applicationInfo.dataDir, "app_flutter")` only if it exists, and never calls `getDir("flutter", …)` (which would create the folder) | none | ✓/later/later | — | 0 | Low (path equality, B-MIGR-008/009) | `:core:data` (a) |
| R-21 | `qr` 3.0.2 | drawing the handoff and Wi-Fi share QR (1/1) | LIB | `qrose-encoder-matrix` **1.3.1** (pure Kotlin matrix, EC level M) + a kit `Canvas` painter with whole-pixel modules (`KitQr`, 10 M13 / §14.1); not the `qrose` Compose painter | `io.github.alexzhirkevich:qrose-encoder-matrix` | ✓/✓/✓ | MIT [V POM] | ≤ 0.1 MB (10 §2.5 budget) [U] | Low | `:ui:kit-content`, `:engine:media` |
| R-22 | `record` 7.1.1 | microphone, PCM16 mono 16 kHz (1/0) | PLATFORM + OWN | `AudioRecord` + `AudioFocusRequest`; own `AudioCapture` / `AndroidAudioCapture` (W-10, 10 §5) | none | ✓/later/later | — | 0 (about 200 lines) | Low | `:android:media` |
| R-23 | `scrollable_positioned_list` 0.3.8 | chat anchor jumps (1/3) | PLATFORM | Compose `LazyListState.requestScrollToItem` / `scrollToItem` / `animateScrollToItem` | (in R-01) | ✓/✓/✓ | Apache-2.0 | 0 | Low | `:feature:chat` |
| R-24 | `sherpa_onnx` 2.0.1 | Whisper speech-to-text, Kokoro voice (2/0) | LIB | sherpa-onnx **2.0.1** Kotlin API, vendored AAR `sherpa-onnx-2.0.1.aar` (sha256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` [V GitHub digest]) | `com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1` from `third_party/maven` (not on Maven Central [I]) | ✓/later (xcframework)/later (`sherpa-onnx-jvm` jar) | Apache-2.0 (+ ONNX Runtime MIT, which bundles Eigen under MPL-2.0 and more; R-24 notes) | about 10.0 → about 9.9 MB (the C and C++ API libs replaced by the JNI lib) [I] | High (vendored; R8 and JNI) | `:android:media` only |
| R-25 | `shared_preferences` 2.5.5 | every preference (105/616) | PLATFORM | `KeyValueStore` over the **same** `FlutterSharedPreferences.xml`, adopted in place through `Context.getSharedPreferences` (`flutter.` prefix and every encoding kept, `commit()`); DataStore 1.3.0 only for desktop/iOS later | none on Android; `androidx.datastore:datastore-preferences-core` 1.3.0 later | ✓/later/later | Apache-2.0 | removes `libdatastore_shared_counter.so` [R] | Medium (encodings, B-MIGR-005) | `:core:data` (a), `:android:migration` |
| R-26 | `shorebird_code_push` 2.0.7 (+ engine fork) | code-push patches and the "update ready" line (1/0) | OWN | `AppUpdater` (W-12), specified by 16 §8 (lineage- and install-source-aware, N16-D8); no library at runtime; Shorebird ends at the switch | none | ✓/–/later (link to the release page) | — | removes the Shorebird engine fork | Medium (developer verification D18) | the 16 §8.1 split: `:engine:diagnostics`, `:core:net`, `:android:platform`, `:androidApp` |
| R-27 | `url_launcher` 6.3.2 | external links, only through `openExternalLink` (1/0) | PLATFORM | `ExternalLinkPolicy` (`:core:security`) + `ExternalLinkUriHandler` (kit) + the `:android:platform` link opener (`Intent.ACTION_VIEW`) (W-13) | none | ✓/later/✓ (`Desktop.browse`) | — | 0 | Low (security invariant) | `:core:security`, `:ui:kit`, `:android:platform` |
| R-28 | `webview_flutter` 4.14.1 | the dev-server preview (1/0) | PLATFORM | `android.webkit.WebView` through `AndroidView` in `:ui:kit-content` androidMain; `http`/`https` to the same host and port only, no user info; JavaScript on, no JavaScript interface; storage cleared on open and close (R-28 notes) | none | ✓/later (WKWebView)/– | — | 0 | Medium (B-SEC-058) | `:ui:kit-content` (a) |
| R-29 | `webview_flutter_android` 4.14.1 | Android half of R-28 (0 direct imports) | DROP | covered by R-28 | — | — | BSD-3-Clause | — | None | — |
| R-30 | `uuid` 4.6.0 | ids for engine, handoff, orchestration (6/0) | PLATFORM | `kotlin.uuid.Uuid.random()` (stable since Kotlin 2.4.0 [V]) | stdlib | ✓/✓/✓ | Apache-2.0 | 0 | Low | any |
| R-31 | `xterm` (vendored 4.0.0, 12,871 lines, + `convert`, `meta`, `quiver`, `equatable`, `zmodem`) | terminal widget: VT parser, buffer, selection, links, key bar (7/8) | LIB + fork | Termux `terminal-emulator` **v0.118.3** (JitPack, unmodified) + a **source fork of `terminal-view` v0.118.3** with a session-port patch (W-14, R12-2); the port itself sits in a tiny view-free module | `com.github.termux.termux-app:terminal-emulator:v0.118.3`; modules `:thirdparty:termux-session-port` (ours) and `:thirdparty:termux-terminal-view` (fork) | ✓/later (common renderer)/later (common renderer) | Apache-2.0 | 84 KB + 42 KB AAR, `libtermux.so` 11 KB [V raw] | Medium–High (fork) | `:android:terminal` (emulator + port), `:ui:kit-content` (a) (view fork + port) |
| R-32 | `dynamic_color` 1.9.0 | Material You palette on Android 12+ (1/0) | PLATFORM | `dynamicLightColorScheme(ctx)` / `dynamicDarkColorScheme(ctx)` from M3 in the kit's androidMain (`AndroidKitPlatform.dynamicSource()`, 06 §4.2) | (material3, kit only) | ✓/–/– | Apache-2.0 | 0 | Low | `:ui:kit` (a) |
| R-33 | `package_info_plus` 10.2.1 | version, build number (4/4) | PLATFORM | `AppBuildInfo` generated in `:androidApp` + `PackageManager.getPackageInfo(pkg, GET_SIGNING_CERTIFICATES)` for the About signer line | none | ✓/later/✓ | — | 0 | Low | `:androidApp`, `:android:platform` |
| R-34 | `window_manager` 0.5.2 | desktop window icon and state (2/0) | DROP | desktop phase: Compose Desktop `Window` / `rememberWindowState` (built in) | — | –/–/later | MIT | 0 | None | `:desktopApp` (later) |
| R-35 | `screen_retriever` 0.2.2 | keeping a restored window on an attached display (1/0) | DROP | desktop phase: `java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices` | — | –/–/later | MIT | 0 | None | `:desktopApp` (later) |
| R-36 | `path` 1.9.1 | path joins (3/0) | LIB | `kotlinx.io.files.Path` (kotlinx-io **0.9.2**) in common code; `java.io.File` in `:android:*` | `org.jetbrains.kotlinx:kotlinx-io-core` | ✓/✓/✓ | Apache-2.0 | 146 KB raw [V raw] | Low | any |
| R-37 | `flutter_test` SDK (dev) | logic and widget tests | LIB (test) | `kotlin.test` + kotlinx-coroutines-test 1.11.0 + Turbine 1.2.1 + CMP `ui-test` (`runComposeUiTest`) + Robolectric 4.17 + Roborazzi 1.77.0 | see Table D, test group | ✓/✓/✓ | Apache-2.0 / MIT | not shipped | Low | test configurations |
| R-38 | `integration_test` SDK (dev) | device smoke tests (BD9, BB8) | LIB (test) | Maestro CLI 2.12.0 (release-build device flows) + UIAutomator 2.4.0 + `androidx.test` runner + Macrobenchmark 1.5.0; the `PhoneEngine*` and `Builtin*Acceptance` runners carry over; BD9 and BB8 are rewritten (R-37 – R-43 notes) | see Table D | ✓/–/– | Apache-2.0 | not shipped | Low | `:androidApp` androidTest, `:android:benchmark` |
| R-39 | `flutter_lints` 6.0.0 (dev) | analyzer rules | LIB (tool) | Konsist 0.18.1 + detekt 1.23.8 + Android lint + `explicitApi()` + warnings as errors at T2; ktfmt 0.64 (03 §19.1) | see Table D | — | Apache-2.0 | not shipped | Low | `:quality`, build-logic |
| R-40 | `cross_file` 0.3.5+5 (dev) | file fakes in tests | DROP | fakes in `:core:testing` | — | — | BSD-3-Clause | — | None | — |
| R-41 | `local_auth_platform_interface` 1.1.0 (dev) | `local_auth` fake | DROP | gone with R-17 | — | — | BSD-3-Clause | — | None | — |
| R-42 | `webview_flutter_platform_interface` 2.15.1 (dev) | WebView fake | DROP | the preview's navigation rule is a pure `PreviewNavigationPolicy` tested in `commonTest` | — | — | BSD-3-Clause | — | None | — |
| R-43 | `shared_preferences_platform_interface` 2.4.2 (dev) | a store that refuses writes (fail-closed tests) | OWN (test) | `FakeKeyValueStore(refuseWrites = true)` in `:core:testing` | — | ✓/✓/✓ | — | not shipped | Low | `:core:testing` |

Tally: PLATFORM 13 (R-03, 05, 08, 18, 20, 22, 23, 25, 27, 28, 30, 32, 33), LIB 15 (R-01, 02, 07, 09, 10, 11, 12, 19,
21, 24, 31, 36, 37, 38, 39), OWN 5 (R-13, 14, 15, 26, 43), DROP 8 (R-04, 06, 29, 34, 35, 40, 41, 42), LATER 2 (R-16, 17).

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
- The P1 fallback (CMP 1.12.2, 03 §5) pairs with CMP material3 **1.12.0-alpha03**. That is an alpha, so it cannot pass
  D12: the fallback is for development builds only. If CMP 1.13.x is not stable when the switch is otherwise ready,
  the switch waits, unless the **owner** amends D12 to allow that one alpha inside the kit (D12 is an owner decision,
  ADR §12; the coordinator brings the question with the dates).
- What leaves the APK: `libflutter.so`, `libapp.so`, `flutter_assets/`, `NOTICES.Z`, `shaders/ink_sparkle.frag`, and
  the four headless Dart entrypoints (map PACKAGES-BUILD-39).
- Accept: for each shipped release variant (`stableRelease`, `previewRelease`; 03 §21.1 flavours),
  `./gradlew :androidApp:dependencyInsight --configuration stableReleaseRuntimeClasspath --dependency
  androidx.compose.ui:ui` (and the same with `previewReleaseRuntimeClasspath`) shows one version; no `io.flutter` class
  in `classes*.dex` (`apkanalyzer dex packages`).

**R-02 `flutter_localizations` → CMP resources.** One-off ARB → XML by `:tools:strings` (ADR §7.5); the six ICU
`select` messages become code; Arabic six-form plurals (zero, one, two, few, many, other). Per-app language follows
06 K16 / §13.6: `LocaleManager.setApplicationLocales` on API 33+, `Locale.setDefault` plus a configuration override on
26–32. The system "App languages" list comes from `androidResources { generateLocaleConfig = true }` plus
`src/main/res/resources.properties` (`unqualifiedResLocale=en`); it lists exactly the seven languages only because
`:android:os-strings` carries a `strings_os.xml` in all seven `values-*` folders, and 06's `LocaleConfigTest` reads the
generated config and expects exactly the seven tags of `SupportedLanguages`. No hand-written `xml/locales_config.xml`
(the build fails if both exist; 03 §21.1 still lists one, §14 item 10). **No `androidx.appcompat`** (R12-3). Catalog:
B-L10N-001, -002, -008, -009, -010, -013. Accept: P6 (ADR §11) and `LocaleConfigTest`.

**R-03 `intl` → platform ICU behind one seam.**
- Plurals: CMP `pluralStringResource` (CLDR categories). An ICU `=N` exact selector is not a CLDR category; 06 §13.4
  handles those.
- Numbers, percents, currency, dates, times: `LocaleFormats` (W-03), an `expect object` in `:core:common`, which the
  ADR allows for primitives. Android uses `android.icu.text.NumberFormat`, `NumberFormat.getCurrencyInstance`,
  `NumberFormat.getPercentInstance`, `DateFormat.getInstanceForSkeleton` and `SimpleDateFormat` for fixed patterns;
  desktop uses `java.text` / `java.time.format`; iOS uses `NSDateFormatter.dateFormatFromTemplate` and
  `NSNumberFormatter`. The kit's `KitPlatform.formatNumber` (06 §12.1 item 8) delegates to it, and notifications and
  widgets call it too, so every surface formats the same way.
- The date API takes the **skeletons today's Dart code uses** outside l10n [R grep of `lib/`, 2026-11-01]: `MMMd` ×11,
  `yMMMd` ×9, `E` ×5, `yMd` ×4, `Hm` ×4, `jm` ×3, `yMMMMEEEEd` ×2, `MMMEd`, `EEEE`, `d`. A Short/Medium/Long style would
  make agents approximate and lose en/ar parity. The one fixed pattern, `'yyyy-MM-dd HH:mm'` with `en_US`
  (`lib/domain/work_row_status.dart:199`), becomes `datePattern(…, "yyyy-MM-dd HH:mm", "en-US", zone)`, so it keeps
  ASCII digits in every app language, as today. Numbers add `percent()` (one `NumberFormat.percentPattern` today) and
  the two fixed patterns `'0.0'` and `'0.#'` through `numberPattern`. Every date call takes a
  `kotlinx.datetime.TimeZone` (callers pass the device zone), so tests are deterministic.
- Catalog: B-L10N-004 (relative times follow the supplied locale), B-L10N-012 (Arabic puts the currency symbol after
  the amount). Accept: `LocaleFormatsTest` in `:core:common` androidHostTest, and the same vectors in `desktopTest`,
  with en **and** ar vectors for every skeleton, the fixed pattern, percent and currency, copied from the Flutter tests
  or generated once by a Dart script with `intl` 0.20.3 and committed with that script.

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

**R-13 `xml` → our own `XmlPull` (10 M7).** Section 10 owns the media code and chose a small pull reader of our own
over xmlutil: no DTD, no external entities (a DOCTYPE fails the read), only the five predefined entities, caps on size
and depth (10 §10.7, §11.4). The Dart sanitiser's order stays: reject `<!\s*(DOCTYPE|ENTITY)` by regex first, then read events
(`lib/domain/static_svg.dart:83–103`) [R]. Office text (W-04) uses the same reader. No XML library enters the app
(xmlutil was N-39 in an earlier draft; withdrawn, and R12-5 with it). Accept: a billion-laughs fixture and an
external-entity fixture are refused before parsing; the docx and xlsx fixtures of B-MEDIA-006 pass.

**R-14 `flutter_secure_storage` → own `SecretStore`.** The interface and Keystore format are in 05 §4 (design from
network-data §7.2); the v11 decrypt recipe (RSA-OAEP SHA-256 with MGF1-SHA1 unwrap of the AES key, then AES-GCM
`IV[12] || ct+tag`) is in 05 §7.5. Rules from this registry:
- No library on Android. `security-crypto` is deprecated [V]; KSafe, KVault and multiplatform-settings would own our
  secret format (X-06).
- Every secret is registered with the `Redactor` before any log line (B-SEC-056).
- A secret that cannot be decrypted sets that profile's "enter the password again" flag. It is never treated as an
  empty password (B-MIGR-006, 02 S-4).

**R-17 `local_auth` → platform device check (FactsBook, later).** `androidx.biometric` 1.1.0 does not fit this app:
`BiometricPrompt` needs a `FragmentActivity` or `Fragment` host, but `MainActivity` is a `ComponentActivity` (03 S21);
its POM pulls `appcompat` 1.2.0 and `fragment` 1.2.5 [V POM]; and `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is not
supported on API 28–29, inside minSdk 26 [androidx `setAllowedAuthenticators` docs]. So the FactsBook builder uses, by
default (R12-9): on API 30+ the platform `android.hardware.biometrics.BiometricPrompt` with
`setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`; on API 26–29
`KeyguardManager.createConfirmDeviceCredentialIntent` launched through `ActivityResults`. Fallback: androidx.biometric
1.1.0 inside one dedicated `FragmentActivity` used only by FactsBook, recorded as a named X-24 exception.

**R-18 `image_picker` → system pickers + `ImageCodec` (`AndroidImageCodec` in `:android:media`, 10 §10.3–§10.4).**
- Record the request before launch, so a process death with the camera open recovers (B-MEDIA-008).
- Decode with `ImageDecoder` on API 28+ (it applies EXIF rotation itself). On 26–27 decode with
  `BitmapFactory.Options.inSampleSize` and rotate by the **platform** `android.media.ExifInterface(InputStream)`
  (API 24+), so no `androidx.exifinterface` dependency.
- Then scale to a 2048 px long edge and compress JPEG 85. Re-encoding drops EXIF, including location.
- Catalog: B-MEDIA-007, B-MEDIA-008, B-CHAT-053.

**R-19 `mobile_scanner` → CameraX + zxing-cpp.**
- The scanner code and its options live in 10 §14.2 (`BarcodeReader.Options(formats = setOf(QR_CODE), tryInvert =
  true, tryRotate = true, tryHarder = false)`, one single-thread decode executor, unbind on the first accepted value).
- The decoded value is never logged, stored or put in semantics (SEC-2).
- `ImageAnalysis` keeps only the latest frame (`STRATEGY_KEEP_ONLY_LATEST`).
- Move to CameraX 1.7.x only once it is stable (1.7.0-rc01 today [V]): it brings built-in tap-to-focus and pinch.
- zxing-cpp ships a consumer keep rule (`-keep class zxingcpp.** { *; }`) and 16 KB-aligned `.so` files [I].
- JVM tests decode the app's own QR renders with `com.google.zxing:core` 3.5.4 (N-86, test-only; X-37 keeps it out of
  every release classpath).
- Catalog: B-ANDR-049, B-PROTO-069, B-KIT-026. Accept: for each release variant, `dependencyInsight --dependency
  com.google.mlkit` is empty; a scan from the emulator camera pairs. "Scan from a picture" is not at the switch (10
  D10-3: after the switch; "Paste the code instead" covers it).

**R-20 `path_provider` → `AppDirs`.** `Context.getDir(name, mode)` creates the folder when it is missing, so calling
`getDir("flutter", …)` on a fresh native install, or after the migration's cleanup, would make an empty `app_flutter/`
and mislead any "is this a Flutter-era install" or "leftovers present" check. The migration reads
`File(applicationInfo.dataDir, "app_flutter")` with an existence check, and Konsist bans `getDir("flutter"` everywhere.
Accept: a Robolectric test in `:android:migration` runs the migration on a fresh install and asserts that no
`app_flutter` folder exists afterwards. Catalog: B-MIGR-008, B-MIGR-009.

**R-24 `sherpa_onnx` → vendored AAR.**
- Placement: `tools/vendor/fetch.sh` downloads
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar`, checks the SHA-256 above, and
  writes it to `native/third_party/maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar`.
  Only the **AAR file** is git-ignored (it is 49,441,341 bytes [V]): the `.gitignore` line is
  `native/third_party/maven/**/*.aar`, never the folder, because Git cannot re-include a file whose parent folder is
  ignored. The hand-written `.pom` next to it (packaging `aar`, licence Apache-2.0, no dependencies) is committed;
  `ThirdPartyHashTest` (03 §19.6) also fails when `git ls-files native/third_party/maven` does not list that `.pom`.
  The AAR's hash goes into `third_party/SHA256SUMS`. `settings.gradle.kts` fails early with "run tools/vendor/fetch.sh"
  when the file is missing.
- Packaging in `:androidApp`: there is **one** packaging block, 13 §8.1 (ABI splits for arm64-v8a and x86_64 plus the
  universal APK, and `jniLibs.excludes` of `lib/armeabi-v7a/**`, `lib/x86/**`, `lib/armeabi/**`, `lib/mips/**`,
  `lib/mips64/**`). Never add `ndk { abiFilters … }`: AGP refuses to configure a build that has both ndk abiFilters
  and ABI splits. Sherpa adds only these lines to that block, and a keep rule:
  ```kotlin
  packaging { jniLibs {
      excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")   // Kotlin uses only the JNI lib
      useLegacyPackaging = true                                                         // already required for proot
  } }
  ```
  `keepRules/sherpa.keep`: `-keep class com.k2fsa.sherpa.onnx.** { *; }`. The AAR's `proguard.txt` is empty [I] and
  the JNI code reads config fields by name.
- Licences inside the AAR: the hand-written POM can only say "Apache-2.0", so Licensee cannot see the third-party code
  compiled into `libonnxruntime.so` and `libsherpa-onnx-jni.so`. sherpa-onnx 2.0.1 builds against ONNX Runtime
  **1.28.2** [V `build-android-arm64-v8a.sh`], and ONNX Runtime's `ThirdPartyNotices.txt` lists Eigen under
  **MPL-2.0** among many others [V `main` branch; U exact list at tag v1.28.2]. sherpa-onnx's own CMake pulls Eigen,
  kaldi-native-fbank, kaldi-decoder, OpenFst, nlohmann/json, utf8proc, hclust-cpp and simple-sentencepiece [V file
  list at tag v2.0.1; U each licence]. So `BUNDLED.toml` gets one entry per component of those two lists (with
  ONNX Runtime's notices file shipped verbatim), the MPL-2.0 sign-off (Eigen, header-only, unmodified) is recorded in
  §8.1, and the texts go into `LICENSES/`. Owner: media lead, slice 5, before the first APK with voice.
- Models stay in `files/voice_models/` and are read in place, with no re-download (B-MIGR-009 lists them).
- R12-6 measures the `sherpa-onnx-static-link-onnxruntime-2.0.1.aar` variant (38.6 MB AAR [V]).
- Catalog: B-MEDIA-031, B-MEDIA-033. Accept: a release-build smoke test constructs `OfflineRecognizer` and `OfflineTts`
  from fixture paths on the emulator (catches R8 breakage).

**R-25 `shared_preferences` → the same file, adopted.** Exact encodings (Long ints; list prefix
`VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu` with `!` for JSON lists; the double prefix) are in 05 §2.2 and network-data §8.1.
Never read ints with `getInt`: Flutter stored them as Long, which throws `ClassCastException` (network-data §7.1).
Konsist rule 7 (ADR §6.3) keeps `getSharedPreferences(` inside `:core:data` androidMain and `:android:migration`.
Catalog: B-MIGR-005, B-MIGR-007.

**R-26 `shorebird_code_push` → `AppUpdater`.** Section 16 §8 owns the updater: when it may check (N16-D8: only when
the installed signer is the public lineage and the first installer was not an F-Droid client; at most every 6 hours,
never while hidden), the asset choice (arm64, then x86_64, then universal, else "needs a 64-bit phone"), the
verification pipeline, the module split (§8.1) and the port of the freeze-tip `AppUpdateChannel` (from
`feat/app-updates-2026-10-31`, merged at `6b686bb7c`, 02 and 15). The registry facts are only these:
- **No library at runtime.** The signer check is `PackageManager.getPackageArchiveInfo(file, GET_SIGNING_CERTIFICATES)`
  on the downloaded, checksum-verified file, so a wrong signer is **refused after download, before install** (16 §8.3
  steps 5–7). The platform installer would refuse a signer mismatch anyway.
- `com.android.tools.build:apksig` is used **at build time only**, by 16 §6.2's `VerifyApkSignerTask` in build-logic
  (N-17).
- Needs `REQUEST_INSTALL_PACKAGES` in the stable and preview manifests (an F-Droid build removes it, §7.1). The
  automation policy key `applyCodePush` stays parseable (map PACKAGES-BUILD-29).
- PRIVACY.md drops the Shorebird paragraph and names the GitHub API.
- Catalog: B-MIGR-012, B-BUILD-010 (newer = a strictly larger build number; 16 §8.3 step 1), B-BUILD-011, B-BUILD-012.
  B-BUILD-011 says today's checker reads the releases **list**, skips drafts and picks the highest valid build among
  unordered entries, so a malformed or unversioned entry (for example `third-party-sources-1`) never hides an update.
  16 §8.1 names `GET …/releases/latest` instead; §14 item 19 asks 16 to list releases (or to prove parity another
  way).
- Risk: once Android developer verification reaches the owner's users ("2027 and beyond"), an unregistered app cannot
  self-update (D18).

**R-27 `url_launcher`.** One door, as today: `ExternalLinkPolicy.decide(url)` in common, `ExternalLinkUriHandler` as
the Compose `LocalUriHandler`, and one Android opener (`ACTION_VIEW`, `CATEGORY_BROWSABLE`, `FLAG_ACTIVITY_NEW_TASK`).
No Custom Tabs (`androidx.browser` is not added; today's Flutter opens the external browser). Konsist rule 6.
Catalog: B-SEC-064, B-SEC-065, B-SEC-095.

**R-28 `webview_flutter` → platform WebView.** Ported from `lib/ui/kit/kit_web_preview.dart` and
`devPreviewMayLoad` (`lib/domain/dev_preview.dart:225`) [R]:
- Settings: `javaScriptEnabled = true` (today's `JavaScriptMode.unrestricted`; dev servers need it),
  `allowFileAccess = false`, `allowContentAccess = false`, `setGeolocationEnabled(false)`,
  `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)`, no `addJavascriptInterface`,
  `WebView.setWebContentsDebuggingEnabled(false)` in release builds.
- Lifecycle clears, when the preview opens **and** when it closes: `webView.clearCache(true)`,
  `WebStorage.getInstance().deleteAllData()`, `CookieManager.getInstance().removeAllCookies(null)`.
- `PreviewNavigationPolicy` (pure, `commonTest`, R-42), from `devPreviewMayLoad`: load only when the scheme is `http`
  or `https` (either, not "the same scheme"), the host equals the origin's host case-insensitively, the port equals
  the origin's port, and there is no user info. Otherwise: a **main-frame** navigation goes to the external opener
  (the host's `onLeave`, which uses `openExternalLink`); a sub-frame or script navigation elsewhere is dropped
  silently.
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
2. Put the port in its **own tiny, view-free module** `:thirdparty:termux-session-port` (one Java interface, ours,
   MIT), which depends only on `terminal-emulator`. Both the view fork and `:android:terminal` depend on it, so
   `:android:terminal` never depends on the view library: ADR §7.3 item 3 keeps the wrapped view a dependency of the
   kit's `androidMain` alone. `ModuleRules` (§12.2): `:thirdparty:termux-session-port` may be used by
   `:thirdparty:termux-terminal-view`, `:ui:kit-content` and `:android:terminal`; `:thirdparty:termux-terminal-view`
   only by `:ui:kit-content`.
3. Patch: replace the `TerminalSession` type in `TerminalView`, `TerminalViewClient` and the text-selection classes with
   `TerminalSessionPort` (below). The interface carries every member the view calls on the session at v0.118.3
   [V source]: `getEmulator()` (lines 899, 974), `write(String)` (lines 767, 902 and 1049; in Termux it is the final
   `TerminalOutput.write(String)`), `writeCodePoint` (line 886) and `updateSize` (line 973), plus `getTitle()`. That
   type swap is the only patch.
4. `:android:terminal` implements the port twice: `LocalPtySession` (our `PtyAccess.java` + proot, as today) and
   `RemoteTerminalSession` (a `TerminalEmulator` fed by WebSocket bytes, whose `TerminalOutput.write` sends input
   frames).
5. A remote `TerminalEmulator` also needs a `TerminalSessionClient` (its constructor's last argument). The emulator
   itself calls only `getTerminalCursorStyle()`, `onTerminalCursorStateChange(boolean)` and the `log*` methods [V
   source]; the session-taking callbacks (`onTextChanged(TerminalSession)` and the rest) are never called by it, because
   the remote session is not a `TerminalSession`. So `RemoteTerminalSession` passes a `RemoteSessionClient` whose
   session-taking methods are no-ops that tolerate a null session, whose cursor methods forward to the view, and whose
   `log*` methods drop the message (escape-sequence fragments can carry terminal content; `AppLog` never sees them).
6. Our `TerminalOutput.onCopyTextToClipboard` enforces the OSC 52 rules: set only, never answer a query, size cap
   (B-XPLAT-009). Links across soft wraps come from our own `TerminalLinkScanner` over `TerminalBuffer`
   (B-XPLAT-010). The key bar sends bytes `0x03` and `0x04` (B-XPLAT-011).
7. Extend ADR P9: render one local **and** one remote (fake WebSocket) session in the slot.

```java
// native/thirdparty/termux-session-port/src/main/java/io/github/eslamasabry/oc/thirdparty/termux/TerminalSessionPort.java
// (new, ours, MIT; module :thirdparty:termux-session-port, depends only on terminal-emulator v0.118.3)
package io.github.eslamasabry.oc.thirdparty.termux;
import com.termux.terminal.TerminalEmulator;
import java.nio.charset.StandardCharsets;
public interface TerminalSessionPort {
    TerminalEmulator getEmulator();                       // null until the first updateSize
    void write(byte[] data, int offset, int count);       // keyboard and paste input
    default void write(String data) {                     // TerminalView calls this for IME text, key codes and paste
        if (data == null) return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }
    void writeCodePoint(boolean prependEscape, int codePoint);
    void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);
    String getTitle();
}
```

**R-33 `package_info_plus`.** `AppBuildInfo` (versionName, versionCode, flavor, git short hash) is generated by a
`:androidApp` task, because KMP modules have no `BuildConfig` (ADR §5.2). The About screen's signer line reads
`PackageInfo.signingInfo` (API 28+), or `signatures` on API 26–27. Catalog: B-NAV-001.

**R-37 – R-43 (dev).** The Flutter test harness, golden harness, widgetbook and analyzer gates die with Flutter
(map PACKAGES-BUILD-38). The 46 Kotlin JVM test classes (645 tests) move unchanged (K-05 to K-07). Of the
instrumentation runners (map PACKAGES-BUILD-41) [R `android/app/src/androidTest`]:
- **Carry over:** `PhoneEngineAcceptance` and `PhoneEngineDeviceBoundaryRegressions` (only the parts that cover the
  sandbox helper that ships at the switch, C-06a), and the `Builtin*Acceptance` runners (`BuiltinRuntimeAcceptance`,
  `BuiltinIdleAcceptance`, `BuiltinRestoreAcceptance`, `BuiltinComponentUpdateAcceptance`,
  `BuiltinComponentFilesystemAcceptance`, `BuiltinRuntimeReclaimAcceptance`), minus the Flutter `integration_test`
  registrant they keep today.
- **Rewritten, not moved:** `Bd9DeviceSmoke` and `Bb8DeviceSmoke` (with `RuntimeQaGuard.java` and `Bb8MainCallback`)
  host Flutter `integration_test` Dart targets (`--target integration_test/bd9_device_smoke_test.dart`), so they become
  Maestro flows on the release build, or UIAutomator tests where a flow needs adb-level checks.
- **Accessibility checks** (today's `androidTapTargetGuideline`, `labeledTapTargetGuideline`, `textContrastGuideline`
  widget-test gates) get a named native tool now, even though porting the widget tests waits for the owner: Compose
  `enableAccessibilityChecks()` in the Android UI tests, backed by Google's Accessibility Test Framework, or
  Roborazzi's `roborazzi-accessibility-check` 1.77.0 on the VPS screenshot tests (N-87, test-only).

The widget-test port itself waits for the owner (ADR §4 row 18).

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
| C-05 | proot 5.1.107.94, its loader, libtalloc 2.4.3, libandroid-shmem 0.7 | proot **GPL-2.0**; talloc **LGPL-3.0-or-later** upstream (Termux labels it `GPL-3.0` [V build.sh]); shmem **BSD-3-Clause** [V] | `jniLibs/{arm64-v8a,x86_64}` via `tool/builtin_linux/fetch_proot.sh` (sha256-pinned `.deb`s, patchelf soname rewrite) | same script, same pins, `:android:linux` `jniLibs`, `useLegacyPackaging = true` | **Gap found:** `THIRD_PARTY_NOTICES.md` and `LICENSES/` do not mention proot, talloc or shmem today [V grep]. They run as separate executables (not linked into the app process), but distributing GPL binaries still requires the licence text and the corresponding source, and we modify them (patchelf). Fix: R12-8 (source offer per 16 D16-8). |
| C-06a | Rust `libaiteam_sandbox.so` (the AI Team protection helper) | own code + crates (`LICENSES/Rust-Phone-Engine.txt`) | `jniLibs` + hash manifest | **ships at the switch** (11 D11-1, default yes), arm64 and x86_64, in `:android:linux` `jniLibs`: a phone that ever turned on AI Team protection holds `files/oc.teamEngine.protection-required`, and from then on every built-in Linux launch goes through this helper (builtin-linux map BUILTIN-LINUX-56; 11 U11-13 checks the owner's phone). Kept with it: its entry in `aiteam-engine-manifest.json` and the hash check, `verify<Variant>PhoneEngineApk`, the `keepDebugSymbols` strip exclusion, a `BUNDLED.toml` entry and the Rust licence file | 0.34 MB raw, about 0.15 MB compressed arm64 [R map PACKAGES-BUILD-07, 13 §8.5]. Runtime owner, slice 3. |
| C-06b | Rust `libaiteam_engine.so`, `libaiteam_boundary_probe.so` | own code + crates (`LICENSES/Rust-Phone-Engine.txt`, libgit2 GPL-2.0 with linking exception) | `jniLibs` + hash manifest | only when AI Team returns (D6, after the switch) | engine 2.44 + probe 0.53 MB compressed arm64 [R]. |
| C-07 | `libtermux.so` (inside K-04) | Apache-2.0 | from the AAR | same | 11 KB. |
| C-08 | Whisper tiny/base/small int8, Kokoro int8 | MIT / Apache-2.0 | downloaded to `files/voice_models/` (revisions pinned) | read in place | not in the APK; notices kept (B-MEDIA-031). |
| C-09 | `assets/agents/paseo-package-lock.json` (153 KB) | ours (lists npm packages installed at runtime) | Flutter asset | `:engine:agents` resource | pinned dependencies for the phone agent host. |
| C-10 | `tool/quota/collector.mjs`, `read_once.mjs` | ours | Flutter assets | resources with checksums (ADR `:engine:runtime` "shell scripts as resources") | — |
| C-11 | `assets/aiteam/manifest*.json`, `assets/aiteam-engine-manifest.json` | ours | assets | `aiteam-engine-manifest.json` ships at the switch, because it attests the sandbox helper (C-06a); `assets/aiteam/manifest*.json` only with AI Team | — |
| C-12 | `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `LICENSES/` | ours / various | Flutter assets | Android assets read by the About screen; notices **generated** (§8.3) | **Also bundle `PRIVACY.ar.md`**: the 4511 APK lacks it today, so the Arabic privacy view is empty [R map PACKAGES-BUILD-22]. |
| C-13 | `assets/branding/app-icon-256.png` | ours | Linux window icon | `:desktopApp` (later) | — |
| C-14 | `shaders/ink_sparkle.frag` | Flutter | Material ripple shader | DROP | the platform `RippleDrawable` does it (ADR §9.4). |
| C-15 | ML Kit models (3 tflite) | proprietary | `assets/` | DROP | X-02. |
| C-16 | `assets/dexopt/baseline.prof` | generated | profileinstaller | regenerated by the baseline-profile plugin 1.5.0 + profileinstaller 1.4.1 | Baseline and Startup Profiles from `:android:benchmark`. |
| C-17 | Components downloaded when the person chooses (not in the APK): Ubuntu Base 24.04.5, Node 24.21.0, OpenCode 2.1.1, Paseo, Claude Code, Codex, the Whisper and Kokoro models (C-08) | various (16 §10 item 2) | pinned by the setup engine and the voice catalog | same pins, listed in `native/third_party/DOWNLOADED.toml` (pin, SHA-256, licence, source URL, owner) | feeds the "Downloaded when you choose" section of the notices and of About (W-18, 16 §10 item 2); moved only by app releases (G8, §10.2). |

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
| N-17 | apksig (add, 16 §6.2) | `com.android.tools.build:apksig` (build-logic `implementation`) | = AGP: **9.3.3** [V Google Maven] | Apache-2.0 | `VerifyApkSignerTask` (`verifyStableReleaseSigner`); moves with AGP in G1; never in the app. |

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
| N-35 | Coil 3 (compose, svg, gif) | `io.coil-kt.coil3:coil-compose`, `coil-svg`, `coil-gif` | 3.6.3 | ✓/✓/✓ (gif: Android only) | Apache-2.0 | core 469 + compose 136 + svg 23 + gif 39 KB | Low | ✓ | `:ui:kit` (compose: `KitImage`, `LocalKitImageLoader`), `:ui:kit-content` (compose, svg, gif: `KitImageLoaders`, 10 §9.1). **Not** `:androidApp` or `:desktopApp`: the kit's public API exposes no Coil type (§12.2, R12-4) |
| N-36 | Telephoto zoomable | `me.saket.telephoto:zoomable` only (`zoomable-image-coil3` removed, 10 §2.1/§2.6) | 0.19.0 | ✓/✓/✓ | Apache-2.0 [V POM] | 398 KB | Medium (upstream moved to 1.0.0-alpha02 in April 2026 [V]; 0.19.0 may get no fixes) | ✓ | `:ui:kit-content` |
| N-37 | qrose encoder matrix | `io.github.alexzhirkevich:qrose-encoder-matrix` (not the `qrose` painter, 10 M13) | 1.3.1 [V] | ✓/✓/✓ | MIT [V POM] | ≤ 0.1 MB in the APK (10 §2.5) [U raw] | Low | ✓ | `:ui:kit-content`, `:engine:media` |
| N-38 | SyntaxMP | `com.gallatinapps.syntaxmp:syntaxmp-tokenizer` | 0.3.0 | ✓/✓/✓ | Apache-2.0 | 554 KB | Medium–High | ✓ | `:ui:kit-content` |
| N-39 | *withdrawn* (was xmlutil core 1.0.2) | — | — | — | — | — | — | — | 10 M7 keeps XML parsing in our own `XmlPull` (W-19); no XML library |
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
| N-57 | Termux terminal-emulator | `com.github.termux.termux-app:terminal-emulator` | v0.118.3 | Apache-2.0 (POM says "Other" [V]) | 84 KB | Medium | ✓ | `:android:terminal`, `:thirdparty:termux-session-port`, `:thirdparty:termux-terminal-view` |
| N-58 | Termux terminal-view (**fork**) | project `:thirdparty:termux-terminal-view` (source from tag v0.118.3) | v0.118.3 + `PATCHES.md` | Apache-2.0 | 42 KB | Medium–High | ✓ | `:ui:kit-content` (a) only |
| N-58b | `TerminalSessionPort` | project `:thirdparty:termux-session-port` (one interface, ours) | — | MIT | < 1 KB | Low | ✓ | `:ui:kit-content` (a), `:android:terminal`, the view fork |
| N-59 | commons-compress | `org.apache.commons:commons-compress` | 1.27.1 | Apache-2.0 | 1.06 MB | Low | ✓ | `:android:linux` only |
| N-60 | profileinstaller | `androidx.profileinstaller:profileinstaller` | 1.4.1 | Apache-2.0 | — | Low | ✓ | `:androidApp` |
| N-61 | AppFunctions | `androidx.appfunctions:appfunctions` (+ KSP compiler) | 1.0.0-beta01 [V] | Apache-2.0 | — | High (renames between betas) | ✗ (after the switch, 02 §3.5) | `:android:platform` (later) |
| N-62 | lifecycle-process (add) | `androidx.lifecycle:lifecycle-process` | 2.11.0 [V Google Maven], aligned with lifecycle (N-31) | Apache-2.0 | — (already in the APK through emoji2) | Low | ✓ | `:android:platform` only (`ProcessLifecycleOwner` for `DeviceFacts.appVisible`, 11 CR11-5, 13 SR3); declared because it is used directly, so the dependency-analysis plugin (N-11) does not flag a used transitive |

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
| N-82 | JSON schema validator (add, 04) | `com.networknt:json-schema-validator` | 3.0.8 [V] | Apache-2.0 | `:tools:contracts` drift job; `ExternalRules` confines it there (§12.2) |
| N-83 | Ktor server CIO (add, 04) | `io.ktor:ktor-server-cio` | 3.6.1 | Apache-2.0 | `:tools:contracts` recorder and replay server; **never** in the app (`ExternalRules` §12.2, X-27); in-app servers use W-11 |
| N-84 | Maestro CLI | GitHub release | 2.12.0 | Apache-2.0 | 39 parity flows on the release build |
| N-85 | Perfetto | trace_processor | v59.1 | Apache-2.0 | traces |
| N-86 | ZXing core (add, 10 §2.1) | `com.google.zxing:core` | 3.5.4 [V] | Apache-2.0 | JVM tests decode the app's own QR renders (10 §14.1); test configurations of `:ui:kit-content` and `:engine:media` only; X-37 bans it from every release classpath |
| N-87 | Accessibility checks (add) | Compose `enableAccessibilityChecks()` with `com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework` 4.1.1 [V Google Maven], or `io.github.takahirom.roborazzi:roborazzi-accessibility-check` 1.77.0 [V] | as listed | Apache-2.0 | tap-target, label and contrast checks that replace today's widget-test guidelines (R-37 – R-43 notes); the kit owner picks one in slice 1 |

### 5.5 Later (recorded now, not in the switch release)

| # | For | Library | Version today | Licence | Decision owner and moment |
|---|---|---|---|---|---|
| N-90 | desktop/iOS pickers | FileKit `io.github.vinceglb:filekit-dialogs-compose` (+ `filekit-coil`) | 0.17.0 [V] | MIT | ADR §8; desktop phase kickoff |
| N-91 | iOS HTTP | `io.ktor:ktor-client-darwin` | 3.6.1 | Apache-2.0 | iOS phase |
| N-92 | desktop secrets | Secret Service: dbus-java or a guarded `secret-tool` call | — | — | core owner at desktop phase kickoff (network-data §7.2) |
| N-93 | FactsBook | R-16 BouncyCastle 1.86; R-17 platform `BiometricPrompt` / `KeyguardManager` (fallback androidx.biometric 1.1.0 in a dedicated `FragmentActivity`) | — | MIT, Apache-2.0 | when D6 schedules FactsBook |
| N-94 | widget redesign | Glance 1.2.0 (stable [V]) | 1.2.0 | Apache-2.0 | owner, widget redesign (freeze decision 2) |
| N-95 | video or audio files | Media3 (`androidx.media3:*`) | 1.11.1 [R media] | Apache-2.0 | **banned until a feature needs it** (X-36; 13 §8.7, 10 §2.6: the app plays no audio or video files). Adopting it deletes X-36 in the same commit. |
| N-96 | Gemma assistant | Google LiteRT-LM | checked at that slice | Apache-2.0 | owner, after the switch (02 §3.5); must pass the S-23 banned-group check (no Play Services) |
| N-97 | AppFunctions | N-61 | 1.0.0-beta01 | Apache-2.0 | after the switch (D16) |
| N-98 | the one native glass trial (owner, 2026-11-01, memory `no-glass-rule`: "Haze/RenderEffect blur plus AGSL refraction, on the top bar and sheets only", an optional Style judged on the phone) | Haze `dev.chrisbanes.haze:haze` | pinned at that slice [U] | Apache-2.0 | after the switch; `:ui:kit` only; refraction only through the platform `RuntimeShader` (AGSL) inside the kit; ships only if the owner likes it on the phone. Adopting it narrows X-21 in the same commit |

---

## 6. "Write ourselves" inventory (Table E)

Each is small, has a named home and an exact contract. Line counts are [est]. Every `expect` in `:core:common` gets
its Android, desktop **and iOS** actual in the same PR: the nightly `-Poc.ios=true` klib compile of core and engine
(ADR §11 P1, 03 §8.3) fails on an `expect` without an iOS actual, so "iOS later" is never allowed for an `expect`.

| # | What | Home | Size | Replaces | Contract / signature | Tests (catalog) |
|---|---|---|---|---|---|---|
| W-01 | digests, HMAC, secure random: `Digest` (SHA-256, SHA-1, MD5; incremental), `Sha256` (10 §4.2's name), `hmacSha256`, `secureRandomBytes` | `:core:common` (expect/actual: Android, desktop, iOS) | ~120 lines with the three actuals | `crypto`; `Random.secure()` | below; extends 10 §4.2 (C10-2) without renaming anything (C12-1) | Dart-generated vectors (a committed Dart script with `crypto` 3.0.7) for SHA-256 (incl. `DartJson.sha256Hex`, 05 §2.5), SHA-1, MD5 and HMAC-SHA256, plus RFC 4231 HMAC vectors; chunked `update` over a 256 MiB synthetic file equals the one-shot digest; the provider-logo default MD5 constant (`provider_logo_store.dart:163`); git loose-object and pack-trailer fixtures for SHA-1 and SHA-256 repositories (B-LINUX-072…074); K-SEC-12; "iOS klib compiles" |
| W-02 | inflate and gzip: `RawInflate` (10 §4.2), `ZlibInflate`, `Gzip.encode` | `:core:common` (expect/actual: Android, desktop, iOS) | ~90 lines with the three actuals | `ZLibDecoder(raw: true)`, `zlib.decode`, `gzip.encode` | below | zip-bomb cap, corrupt and truncated input for both inflaters; git loose-object fixtures (zlib-wrapped, `git_link_repair.dart:229`); Paseo setup-script round trip (`paseo_scripts.dart:140`: gzip → base64 → `GZIPInputStream` equals the lockfile; byte equality with Dart's gzip header is not required); "iOS klib compiles" |
| W-03 | `LocaleFormats` | `:core:common` (expect/actual: Android, desktop, iOS) | ~180 lines with the three actuals | `intl` formats | below | B-L10N-004, B-L10N-012; en and ar vectors for every `DateSkeleton`, the fixed pattern, percent and currency (R-03 notes); "iOS klib compiles" |
| W-04 | ZIP central-directory reader + office text | `:engine:media` | ~270 lines (port of `lib/domain/office_text.dart`) | own Dart code | `OfficeText.extract(bytes, kind): Outcome<String>` (10 §10.7) | B-MEDIA-006 |
| W-05 | `StaticSvg` sanitiser | `:engine:media` | ~260 lines (port of `lib/domain/static_svg.dart`) | own Dart code | `StaticSvg.sanitize(source: String): String?` over `XmlPull` (W-19) (10 §11.4) | B-CHAT-107 |
| W-06 | hardened OkHttp builder, pinned-leaf `TrustManager`, SSE parser + `EventStream` | `:core:net-okhttp`, `:core:net` | 04 §3 | `dio` internals | 04 §3.2 | 04's list |
| W-07 | `SecretStore` / `KeystoreSecretStore` | `:core:data` (a) | ~150 lines | `flutter_secure_storage` | 05 §4.2 | B-SEC-056 |
| W-08 | flutter_secure_storage v11 reader | `:android:migration` | ~200 lines | — | 05 §7.5 | B-MIGR-006 |
| W-09 | `ImageCodec` / `AndroidImageCodec` (photo re-encode: long edge ≤ 2048 px, JPEG 85, EXIF dropped) | interface `:engine:media`, implementation `:android:media` (10 §3.3, §4.5) | ~150 lines | `image_picker` options | 10 §4.5 / §10.3 | B-MEDIA-007, -008 (10 N10) |
| W-10 | `AudioCapture` / `AndroidAudioCapture` | interface `:engine:media`, implementation `:android:media` (10 §3.3, §6.2) | ~200 lines | `record` | 10 §4.5: cold `Flow` of 16 kHz mono PCM16 frames plus capture events | B-MEDIA-014, -023, -032 |
| W-11 | `LocalHttpServer` (+ `LoopbackPorts`): one small `ServerSocket` HTTP/1.1 listener for **every** in-app server, configured per use by a policy | interface and policy types `:core:domain` (used by several engines, ADR §4 row 27); implementation `SocketLocalHttpServer` in `:android:platform` (plain `java.net`, so it moves unchanged to an `oc.jvm.library` module in the desktop phase, as 10 §13.3 already planned for its server); owner: android-platform owner (11) | ~300 lines + ~40 | `dart:io` `HttpServer` ×5, `ServerSocket.bind` and `Socket.connect` (BYO SSH) | below; the five users and their limits are in the table after the code | per user, below; plus `SocketLocalHttpServerTest` (JVM, real sockets on 127.0.0.1): head over the cap → 431, slow client → read timeout, fifth connection closed, wrong bearer → 401 in constant time, method outside the allowlist → 405 with `Allow`, body over the cap → 413 and the partial file deleted, after `notAfter` → 410 and stop |
| W-12 | `AppUpdater` | the 16 §8.1 split: `UpdateNotice`, `ReleaseSource`, `UpdateVerifier`, `UpdatePolicy`, `GitHubReleaseSource` in `:engine:diagnostics` commonMain; `ResumableDownloader` in `:core:net`; `PackageInstallerUpdater`, `AndroidApkInspector`, `AndroidInstallSource` in `:android:platform`; `.AppUpdateStatusReceiver` in `:androidApp` | port of the freeze-tip `AppUpdateChannel.kt` (186) and `AppUpdatePolicy.kt` (31) plus new engine code | Shorebird | 16 §8 (N16-D8 lineage and install-source gate, 6-hour cadence, asset order, 10-step verification); no library at runtime | 16 §8.3 `UpdateVerifierTest`, P16-4; B-MIGR-012, B-BUILD-010…012; WP-16.6 (slice 4) |
| W-13 | link door | `:core:security` + `:ui:kit` + `:android:platform` | ~150 lines | `url_launcher` | `ExternalLinkPolicy.decide(url): LinkDecision` | B-SEC-064, -065 |
| W-14 | `TerminalSessionPort` (own module), `LocalPtySession`, `RemoteTerminalSession`, `RemoteSessionClient`, `TerminalLinkScanner` | `:thirdparty:termux-session-port` + the view fork + `:android:terminal` | ~100-line patch + ~550 lines | `xterm` | R-31 | B-XPLAT-009, -010, -011 |
| W-15 | `KitBidi` | `:ui:kit` | port | `intl` bidi helpers + own Dart | FSI U+2068 / LRI U+2066 / PDI U+2069, wrap once | B-L10N-006 |
| W-16 | `PerfTracePlugin` | `:core:net` | ~80 lines | dio interceptor | Ktor `createClientPlugin("PerfTrace")`: one span per request with method, path template, status and bytes | B-BUILD-025, -026 |
| W-17 | `CodeTokenizer` + SyntaxMP adapter + alias table | `:ui:kit-content` | ~200 lines | `highlight` glue | R-11 | B-CHAT-055, -056 |
| W-18 | notices generator | build-logic task `generateThirdPartyNotices` | ~200 lines | `tool/notices/regenerate.py` | §8.3: reads each release variant's Licensee `artifacts.json`, `BUNDLED.toml` and `DOWNLOADED.toml` | B-BUILD-030 |
| W-19 | `XmlPull` (10 M7) | `:engine:media` commonMain | ~250 lines | `xml` 7.0.1 | 10 §10.7 / §11.4: pull events; a DOCTYPE fails the read; only the five predefined entities; size and depth caps | 10 N8 and S6 (`XmlPullTest`): DOCTYPE, billion laughs, external entity, 10,000-deep nesting all refused |
| W-20 | analytics core: `Analytics`, `AnalyticsSchema`, `PrivacyScrubber`, `EventQueue`, `UploadPolicy`, `Identity` (rotating install id), `WhatWeCollectSource`, and the sinks `NoOp`, `JsonDebug` (built) and `Aptabase`, `PostHog`, `Firebase` (GA4 Measurement Protocol over plain HTTPS, no SDK), `Plausible`, self-hosted `Custom`, `FanOut` (after D16-1) | `:engine:analytics` (03 S27; built on `native/analytics-2026-11-01`) | 18 | — (owner decision 2026-11-01, freeze decision 6) | 18 §4 API; sinks are our own HTTP code through `AnalyticsTransport` over `:core:net`; **no vendor SDK and no Firebase/Google SDK** (X-01, X-04); `sender=none` until 16 D16-1 | 16 §5.10: `AnalyticsSchemaTest`, `AnalyticsPrivacyTest` (B-SEC-191), `AnalyticsSenderContractTest` per sender with `ktor-client-mock`, `AnalyticsUploadPolicyTest`, EG-1/EG-2; WP-16.7 (slices 1 and 4) |

```kotlin
// :core:common commonMain — the only module allowed expect/actual (ADR §7.3 item 4). Base: 10 §4.2 (C10-2), whose
// names and signatures stay exactly as written there; this adds what today's code also needs (C12-1).
package io.github.eslamasabry.oc.core.common.bytes

public enum class DigestAlgorithm { Sha256, Sha1, Md5 }   // Sha1: git objects and packs; Md5: the default-logo check only

/** Incremental digest. Android/desktop: java.security.MessageDigest. iOS: platform.CoreCrypto CC_SHA256/CC_SHA1/CC_MD5. */
public expect open class Digest(algorithm: DigestAlgorithm) {
    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)
    public fun digestBytes(): ByteArray      // the object is spent afterwards
    public fun digestHex(): String           // lowercase hex, exactly like Dart `crypto` `Digest.toString()`
}
/** 10 §4.2's type, unchanged for its callers (DartJson.sha256Hex, model packs, the voice stamp). */
public class Sha256 : Digest(DigestAlgorithm.Sha256)

/** Android/desktop: javax.crypto.Mac("HmacSHA256"). iOS: CCHmac(kCCHmacAlgSHA256). */
public expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

/** Android/desktop: java.security.SecureRandom. iOS: platform.Security SecRandomCopyBytes (K-SEC-12). */
public expect fun secureRandomBytes(count: Int): ByteArray

/** Raw DEFLATE (ZIP method 8). Android/desktop: Inflater(nowrap = true). iOS: platform.zlib inflateInit2(-15). */
public expect object RawInflate {
    /** null when the stream is corrupt, ends early, or would exceed [maxOutput] bytes (a zip bomb guard). */
    public fun inflate(input: ByteArray, offset: Int, length: Int, maxOutput: Int): ByteArray?
}
/** zlib-wrapped DEFLATE (header + Adler-32), e.g. git loose objects. Android/desktop: Inflater(). iOS: inflateInit2(15). */
public expect object ZlibInflate {
    public fun inflate(input: ByteArray, offset: Int, length: Int, maxOutput: Int): ByteArray?
}
/** gzip container (RFC 1952). Android/desktop: GZIPOutputStream. iOS: platform.zlib deflateInit2(windowBits = 31). */
public expect object Gzip {
    public fun encode(input: ByteArray): ByteArray
}
// [U, 10 §4.2] whether the Linux-hosted klib compile resolves platform.CoreCrypto / platform.zlib / platform.Security.
// If it does not, the iOS actuals follow 10 §4.2's fallback: pure Kotlin digests (KotlinCrypto sha2 0.8.0 checked in
// §15 is the reference), arc4random_buf for random bytes, and inflaters that return null until the iOS phase.

package io.github.eslamasabry.oc.core.common.format

/** The intl skeletons today's Dart code uses outside l10n (R-03 notes). Add one only with en and ar vectors. */
public enum class DateSkeleton(public val pattern: String) {
    MonthDay("MMMd"), YearMonthDay("yMMMd"), Weekday("E"), WeekdayFull("EEEE"), NumericDate("yMd"),
    Hour24Minute("Hm"), HourMinute("jm"), FullDate("yMMMMEEEEd"), WeekdayMonthDay("MMMEd"), Day("d"),
}

public expect object LocaleFormats {
    public fun number(value: Double, languageTag: String, maxFractionDigits: Int = 2): String
    public fun percent(fraction: Double, languageTag: String, maxFractionDigits: Int = 0): String
    public fun currency(amount: Double, isoCurrency: String, languageTag: String): String
    public fun date(epochMillis: Long, skeleton: DateSkeleton, languageTag: String, zone: kotlinx.datetime.TimeZone): String
    /** Fixed pattern, e.g. ("yyyy-MM-dd HH:mm", "en-US") as work_row_status.dart:199 does; no locale re-ordering. */
    public fun datePattern(epochMillis: Long, pattern: String, languageTag: String, zone: kotlinx.datetime.TimeZone): String
    /** Fixed number pattern, e.g. "0.0" or "0.#". */
    public fun numberPattern(value: Double, pattern: String, languageTag: String): String
}
// androidMain: android.icu.text.NumberFormat (+ getPercentInstance, getCurrencyInstance), DateFormat.getInstanceForSkeleton,
// SimpleDateFormat / DecimalFormat for fixed patterns (API 24+). desktopMain: java.text / java.time.format (skeletons
// through a small fixed table, checked by the same vectors). iosMain: NSDateFormatter.dateFormatFromTemplate,
// NSNumberFormatter.
```

```kotlin
// :core:domain commonMain — W-11. One listener for every in-app server; the policy is the whole security contract.
package io.github.eslamasabry.oc.core.domain.localhttp

public sealed interface BindTo {
    public data object Loopback : BindTo                          // 127.0.0.1
    public data class Lan(val ipv4: String) : BindTo              // from WifiAddressSource (10 §13.1); never 0.0.0.0
    public data class RedirectHost(val host: String) : BindTo     // MCP OAuth: must classify as Loopback (04 HostClass)
}
public sealed interface HttpAuth {      // constant-time compares; values are Secrets (16 §1.3; SocketLocalHttpServer joins K-SEC-4's reveal() allowlist)
    public data object None : HttpAuth
    public data class Bearer(val token: Secret) : HttpAuth         // whole "Authorization: Bearer …" value
    public data class PathToken(val token: Secret) : HttpAuth      // first path segment (Wi-Fi share)
    public data class QueryState(val name: String, val expected: Secret) : HttpAuth   // OAuth state
}
public sealed interface PathRule {
    public data class Exact(val path: String) : PathRule
    public data class Pattern(val regex: String) : PathRule        // e.g. "^/f/(\\d{1,6})$"
}
public data class LocalHttpPolicy(
    val bind: BindTo,
    val port: Int = 0,                                  // 0 = ephemeral; OAuth uses the redirect's port
    val methods: Set<String>,                           // others → 405 with Allow
    val path: PathRule,                                 // others → 404
    val auth: HttpAuth,                                 // fails → 401 (403 where today's code answers 403)
    val maxHeadBytes: Int = 8 * 1024,                   // request line + headers; over → 431
    val maxBodyBytes: Long = 0,                         // 0 = no body; over → 413
    val readTimeoutMs: Int = 10_000,
    val idleTimeoutMs: Int = 5_000,
    val maxConnections: Int = 4,                        // more are closed at once
    val notAfterEpochMs: Long? = null,                  // checked on every accept; after it → 410 and stop (16 N16-D11)
)
public class LocalHttpRequest(
    public val method: String, public val path: String, public val query: Map<String, String>,
    public val headers: Map<String, String>,            // names lower-cased
    public val body: kotlinx.io.Source?,                // bounded by maxBodyBytes; streamed, never buffered whole
)
public sealed interface LocalHttpBody {
    public class Bytes(public val bytes: ByteArray) : LocalHttpBody
    public class Stream(public val length: Long, public val open: () -> kotlinx.io.Source) : LocalHttpBody
}
public class LocalHttpResponse(public val status: Int, public val headers: List<Pair<String, String>>, public val body: LocalHttpBody?)
public fun interface LocalHttpHandler { public suspend fun handle(request: LocalHttpRequest): LocalHttpResponse }
public interface RunningLocalServer { public val port: Int; public suspend fun stop() }

public interface LocalHttpServer {
    /** HTTP/1.1, one request per connection (Connection: close), no TLS, no chunked bodies, no compression.
     *  Never logs a path, query, header or body: tokens live there. */
    public suspend fun start(policy: LocalHttpPolicy, handler: LocalHttpHandler): Outcome<RunningLocalServer>
}

/** BYO-host SSH tunnel (lib/host/byo_host_ssh_runner.dart:583, 604); same implementation module. */
public interface LoopbackPorts {
    public suspend fun reserveFree(): Int                            // bind 127.0.0.1:0, read the port, close
    public suspend fun accepts(port: Int, timeoutMs: Int): Boolean   // TCP connect to 127.0.0.1:port
}
```

The five users of W-11 (each builds its policy in its own engine and owns its tests):

| User (today's code) | Engine (owner) | Bind, port | Methods and path | Auth | Limits | Catalog / screen |
|---|---|---|---|---|---|---|
| MCP OAuth callback (`lib/domain/mcp_oauth.dart:151`) | `:engine:integrations` | `RedirectHost(redirect host)`, the redirect's port; refused unless the host is Loopback | GET, the redirect path | `QueryState("state")` | 10-minute lifetime; one completion; an `error` answer becomes a plain page and a typed failure | 07 I15 "loopback OAuth callback completes sign-in" (SCREENS-NAV-95) |
| Connector search bridge (`lib/state/mcp_connector_search_bridge.dart:46`) | `:engine:integrations` | Loopback, 0 | POST `/find-connectors`, no query | `Bearer` (random) | request ≤ 2 KiB, response ≤ 16 KiB, 5 s deadline, ≤ 4 active (429), 403 when the source is gone | 04 row 049 `ConnectorSearchStatusTest` |
| Git link repair receiver (`lib/builtin/git_link_repair.dart:676`) | `:engine:project` | Loopback, 0 | PUT `/f/<n>` | `Bearer` (32 random bytes) | body ≤ the repair's max file size, streamed to a file; 204, else 403 | B-LINUX-072…074 |
| Termux migrate-out receiver (`lib/builtin/migration/termux_migration_transport.dart:382`) | `:engine:runtime` (D7) | Loopback, 0 | PUT `/archive` with `x-offset` | `Bearer` (32 random bytes) | resumable chunked upload appended to the target file; 5 s idle | B-LINUX-233…238, B-SEC-177 |
| Wi-Fi share (`lib/platform/wifi_file_share.dart:165`; 10 §13) | `:engine:media` (media lead) | `Lan(Wi-Fi address)`, 0 | GET and HEAD `/<token>/<name>` | `PathToken` | head 8 KiB (431), 10 s, 4 connections, 15 min `notAfter` (410), body streamed from the descriptor opened at start (16 N16-D11) | B-MEDIA-034, B-SEC-192 |

Slices: the listener lands with its first user, the Termux migrate-out receiver (slice 3, 07 F16); the Wi-Fi share
binding stays in 10's module and slice (`:engine:media` policy + `ShareHttp` handler, `WifiAddress` in `:android:media`,
slice 5, 10 C10-3), so 10's `AndroidLanFileServer` is replaced by this one implementation (C12-2).

---

## 7. Banned list (Table F)

Patterns match `group:artifact` **exactly**: `*` stands for any run of characters except `:`, and the match must end
at the artifact name. There is no "except" syntax, so a row names the exact artifacts it bans. Unless the row says
otherwise, a ban covers every **shipped release variant** of `:androidApp` (`stableRelease`, `previewRelease`, and
`fdroidRelease` if that flavour is ever added, §7.1), and later `:desktopApp`. Test-only use is allowed only where the
row says so. Data file: `native/quality/data/banned-dependencies.txt`, one `pattern | X-id | variants` per line; the
third field is optional (empty = every release variant).

| # | Pattern | Why | Use instead |
|---|---|---|---|
| X-01 | `com.google.android.gms:*`, `com.google.firebase:*`, `com.google.android.play:*`, `com.google.android.datatransport:*`, `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | F-Droid-ready and private (02 G8, S-23). This bans **libraries**; the analytics senders are our own HTTP code (W-20), and a Firebase destination, if chosen, is our own HTTPS call (GA4 Measurement Protocol, 18 A18-6), never the SDK | platform APIs |
| X-02 | `com.google.mlkit:*` | Google telemetry, 2.4 MB, freeze decision 1 | zxing-cpp (R-19) |
| X-03 | `dev.shorebird:*` | code push ends at the switch | `AppUpdater` (W-12) |
| X-04 | analytics and crash SDKs: `io.sentry:*`, `com.bugsnag:*`, `com.posthog:*`, `com.aptabase:*`, `com.mixpanel.android:*`, `com.amplitude:*`, `com.segment.analytics.kotlin:*`, `io.embrace:*`, `com.datadoghq:*`, `com.instabug.library:*`, `com.microsoft.appcenter:*` | S-23, NG15. Vendor SDKs stay banned even though the owner decided on usage counts (freeze decision 6): a plain HTTP sink over `:core:net` to Aptabase, PostHog, Firebase (GA4 Measurement Protocol), Plausible or our own endpoint is allowed and is how 18 §5 builds them | the analytics core W-20 (`:engine:analytics`) |
| X-05 | `com.github.termux.termux-app:app`, `com.github.termux.termux-app:termux-shared`, `com.github.termux.termux-app:terminal-view` | GPLv3 (`app`; `termux-shared` per its own LICENSE); `terminal-view` because the fork replaces it and a JitPack copy would duplicate `com.termux.view.*`. `terminal-emulator` (K-04) is required and matches no pattern | K-04 + the fork (N-58) |
| X-06 | `androidx.security:security-crypto*`, `eu.anifantakis:ksafe*` (Android), `com.liftric:kvault*`, `com.russhwolf:multiplatform-settings*`, `com.google.crypto.tink:*` | deprecated, or a third party would own our secret and preference formats | `SecretStore`, `KeyValueStore` |
| X-07 | `com.google.dagger:*`, `io.insert-koin:*`, `me.tatarka.inject:*` | ADR §3.3 (Koin is plan B and needs a coordinator change to this file) | Metro |
| X-08 | `com.slack.circuit:*`, `app.cash.molecule:*`, `com.arkivanov.decompose:*`, `com.arkivanov.mvikotlin:*`, `org.orbit-mvi:*`, `com.squareup.workflow1:*`, `cafe.adriel.voyager:*` | one state idiom for agents (ADR §3.3) | ViewModel + `StateFlow`, Nav3 |
| X-09 | `androidx.navigation:navigation-compose`, `org.jetbrains.androidx.navigation:navigation-compose` | Navigation 2 | Navigation 3 |
| X-10 | `androidx.glance:*` | ZTE launcher size-map trap; working RemoteViews (ADR §3.3) | RemoteViews; N-94 later |
| X-11 | `androidx.a2ui:*`, `androidx.compose.remote:*` | pre-1.0, Android-only, breaks kit-only (genui §0) | the `oc-ui` card contract |
| X-12 | `org.openapitools:*`, `io.ktor:ktor-client-content-negotiation*`, `io.ktor:ktor-serialization-kotlinx*`, `io.ktor:ktor-client-logging*`, `com.squareup.okhttp3:logging-interceptor`, `io.ktor:ktor-client-cio*`, `io.ktor:ktor-client-android*` | hand-written client; we decode with `WireJson`; bodies and headers are never logged; one engine. `io.ktor:ktor-serialization` and `ktor-serialization-jvm` stay **allowed**: `ktor-client-core-jvm` 3.6.1 pulls them as compile dependencies through `ktor-websocket-serialization-jvm` [V POMs] | Ktor core + OkHttp engine |
| X-13 | `com.google.code.gson:*`, `com.squareup.moshi:*`, `com.fasterxml.jackson.*:*`, `org.json:json` outside test configurations | one serializer | kotlinx.serialization; platform `org.json` stays inside existing `:android:*` code |
| X-14 | `com.squareup.retrofit2:*`, `io.reactivex.rxjava*:*` | — | Ktor, coroutines |
| X-15 | `com.jakewharton.timber:*`, `io.github.aakira:napier*`, `org.slf4j:slf4j-android`, `org.slf4j:slf4j-simple` | one logger, redacted first | `AppLog` (Kermit) |
| X-16 | `io.arrow-kt:*` | — | `Outcome` / `Failure` |
| X-17 | `app.cash.paparazzi:*`, the `com.android.compose.screenshot` plugin | Android-only alpha / unmaintained (ADR §3.3) | Roborazzi |
| X-18 | `com.mikepenz:multiplatform-markdown-renderer*`, `com.halilibo.compose-richtext:*`, `io.github.huarangmeng:markdown*` | reference implementations only | own kit renderers on `org.jetbrains:markdown` |
| X-19 | `dev.snipme:highlights` outside the benchmark branch; any WebView-based highlighter or Markdown renderer | missing languages / not native | SyntaxMP behind `CodeTokenizer` |
| X-20 | `androidx.compose.material:material`, `org.jetbrains.compose.material:material` (Material 2), `*:material-icons-extended` | M3 kit only; icons are generated Phosphor | `:ui:kit` |
| X-21 | `dev.chrisbanes.haze:*` and other blur or glass libraries | no-glass rule; frost is `RenderEffect` inside the kit (D10). **Until** the one post-switch glass trial the owner approved (N-98): then Haze is allowed in `:ui:kit` only, refraction only through the platform `RuntimeShader` inside the kit, and this row is narrowed in the same commit | `KitFrost` |
| X-22 | `com.airbnb.android:lottie*`, `io.github.alexzhirkevich:compottie*` | motion goes through kit tokens and framework APIs | `KitMotion` |
| X-23 | `com.google.accompanist:*` | deprecated pieces | activity / Compose APIs |
| X-24 | `androidx.appcompat:appcompat*`, `com.google.android.material:material` (MDC) as **direct** dependencies | `MainActivity` is a `ComponentActivity`; locale via `LocaleManager` (R12-3). The only foreseen exception is R-17's fallback (androidx.biometric in a dedicated FactsBook `FragmentActivity`), which must be named here when used | platform themes, `LocaleManager` |
| X-25 | any version containing `-alpha`, `-dev`, `-eap`, `-SNAPSHOT`, or `-beta` / `-rc` outside the M3 allowlist | D12 | stable releases |
| X-26 | dynamic versions: `+`, ranges, `latest.*`; versions declared in a module script | reproducible builds | the version catalog |
| X-27 | `org.nanohttpd:*`, `io.ktor:ktor-server-*` | NanoHTTPD is unmaintained; Ktor server stays in `:tools:contracts` only (N-83) | `LocalHttpServer` (W-11) |
| X-28 | `androidx.biometric:*` alphas | D12 | the platform path of R-17 (fallback 1.1.0, later) |
| X-29 | `com.android.billingclient:*`, `com.google.android.ump:*`, any ad SDK | no billing, no ads | — |
| X-30 | **API-level** bans inside allowed libraries (Konsist): `io.ktor.client.plugins.sse.*`, `io.ktor.client.plugins.HttpRequestRetry`, `io.ktor.client.plugins.contentnegotiation.*`, `io.ktor.client.plugins.logging.*`, `okhttp3.logging.*`, `okhttp3.CertificatePinner` (it pins SPKI after chain validation, not our self-signed leaf); `Context.getDir("flutter"` (R-20) | ADR §3.3, network-data §2–§3 | our SSE loop, explicit GET-only retries, `WireJson`, `AppLog`, `PinnedLeafTrustManager` |
| X-31 | debug-only tooling in release: `*:ui-tooling` (non-preview), `androidx.tracing:tracing-perfetto-binary`, `androidx.compose.runtime:runtime-tracing` | never ship debug and trace binaries (testing-perf §1) | `debugImplementation` or the `benchmark` build type only |
| X-32 | `org.jetbrains.kotlin:kotlin-reflect` | size (13 §8.7); test configurations may use it (03: `:ui:routes` `desktopTest`) | `sealedSubclasses` only in tests |
| X-33 | `com.google.guava:guava` | size (13 §8.7); the `com.google.guava:listenablefuture` stub stays allowed | `androidx.concurrent:concurrent-futures` |
| X-34 | `androidx.pdf:*` | 10 M12 and D10-7: parses untrusted PDFs in the app process and pulls Material Views | the isolated `LocalPdfService` |
| X-35 | `androidx.core:core-splashscreen` | 13 SR6: no splash hold | theme `windowSplashScreenBackground` |
| X-36 | `androidx.media3:*` | 13 §8.7, 10 §2.6: the app plays no audio or video files | `AudioTrack`; "Open with another app". Adopting Media3 (N-95) deletes this row in the same commit |
| X-37 | `com.google.zxing:*` | test-only JVM decoder (N-86) | zxing-cpp in the app |
| X-38 | `io.github.pdvrieze.xmlutil:*`, `com.fleeksoft.ksoup:*` | 10 M7 and §2.6: no XML library; DTD and entity surface | `XmlPull` (W-19) |
| X-39 | `com.k2fsa.sherpa.onnx:*` in the `fdroidRelease` variant only (third field of its data line) | 10 §2.7 D10-6 "Lite": the F-Droid build carries no prebuilt voice binary (16 F5) | `UnavailableSpeechRecognizer`, `UnavailableSpeechSynthesizer` |

How each row is enforced (§12.1), for every release variant: the resolved release graph (`BannedDependenciesTest`, in
`:quality:graph`) checks X-01 … X-23, X-27 … X-29 and X-31 … X-39; `BannedPatternsTest` (in `:quality:rules`) runs the
patterns against fixtures of required coordinates (none may match) and known banned ones (each must match).
**Declared** dependencies (build-logic `ExternalRules`, at configuration time) check X-24, so a library that pulls
AppCompat in transitively (R-17's fallback) is a reviewed exception, not a build break. `PrereleaseGuardTest` checks
X-25. `CatalogOnlyTest` checks X-26 (no version literal in a module script; no `+`, range or `latest.` in
`libs.versions.toml`). Konsist checks X-30. Manifest entries are invisible to all of these, so
`ManifestGoogleRefsTest` (§12.1) reads each release variant's merged manifest.

### 7.1 F-Droid view (owner freeze decision 1: F-Droid-ready; submission after the rewrite)

Every row of Tables A–E is the **same** in an F-Droid build except the ones below. Section 16 §9.1 owns the blocker list
and the submission work; this table is the dependency side of it. Whether F-Droid builds our `stable` flavour byte
for byte (16 §5.9, F12) or a separate `fdroid` flavour (10 §2.7) is still open (§14 item 15); the checks iterate over
every release variant, so either answer is covered.

| Row | In an F-Droid build | 16 §9.1 |
|---|---|---|
| R-24 / N-56 sherpa-onnx AAR (prebuilt ONNX Runtime) | **absent** in an `fdroid` flavour (10 D10-6 Lite: phone voices only; X-39 enforces), or **source-built** in the recipe later | F5 |
| R-19 / N-55 zxing-cpp (native AAR from Maven Central) | same, if F-Droid's scanner accepts it [U]; else **source-built** (Apache-2.0, CMake) | F6 |
| R-31 / K-04 / N-57 Termux `terminal-emulator` (JitPack) + N-58 fork | **source-vendored** as modules in the recipe (the fork already is source) | F7 |
| C-05 proot, loader, talloc, shmem (prebuilt from Termux `.deb`s) | **source-built** in the recipe with the pinned NDK | F4 |
| C-06a `libaiteam_sandbox.so` (prebuilt Rust, ships at the switch per 11 D11-1) | **source-built** in the recipe (16 F8 still says "not shipped at the switch"; §14 item 14) | F8 |
| W-12 `AppUpdater` + `REQUEST_INSTALL_PACKAGES` | permission **removed** by the `fdroid` manifest (10 §2.7); in a byte-identical build the updater turns itself off for F-Droid installs (`InstalledByStore`, 16 N16-D8) | F11 |
| Manifest `com.google.android.gms.metadata.ModuleDependencies` (Photo Picker backport, a manifest entry, no library; 10 §15.2) | present in `stable` and `preview`; **removed** in an `fdroid` flavour (`tools:node="remove"`) | — |
| W-20 analytics core | same code; BASIC always on earns the "Tracking" anti-feature (16 D16-2) | F10 |
| C-08 / C-17 downloads (models, Ubuntu, Node, agents) | same; disclosed in the metadata (`NonFreeNet`/`NonFreeAdd` [U]) | F9 |

Checks: X-39 (per-variant ban); `ManifestGoogleRefsTest` (stable and preview may hold exactly the `ModuleDependencies`
service and its `MODULE_DEPENDENCIES` action; `fdroid` holds no `com.google.android.gms` or `com.google.firebase`
string and no `REQUEST_INSTALL_PACKAGES`).

---

## 8. Licence policy and notices

### 8.1 Allowed licences

| Where | Allowed | Needs coordinator sign-off per artifact | Never |
|---|---|---|---|
| Release classpath (linked into the app process) | Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, Bouncy Castle Licence (MIT) | MPL-2.0 (file-level copyleft, unmodified only) | GPL, LGPL, AGPL, SSPL, BUSL, the JSON License, any "non-commercial" licence, proprietary SDK terms |
| Bundled fonts | OFL-1.1 | — | fonts without an embedding licence |
| Separate executables run inside the built-in Linux (C-05) | GPL-2.0 (proot), LGPL/GPL-3.0 (talloc), BSD-3-Clause (shmem), as today | any new one | linking any of them into the app process |
| Test and build tools | the release list + EPL-1.0/2.0 (JUnit) + the JSON License (K-06 only) | — | — |

Sign-offs recorded so far: **Eigen (MPL-2.0)**, header-only and unmodified, compiled into `libonnxruntime.so` and
`libsherpa-onnx-jni.so` (R-24 notes). It ships today in the Flutter app's sherpa-onnx; the native app adds only the
notice. Each sign-off is a `BUNDLED.toml` entry with `signoff = "<who>, <date>"`.

### 8.2 Licensee configuration (in the `oc.android.application` convention plugin)

Licensee 1.14.1 registers one task per Android variant, `licenseeAndroid<Variant>`, writing
`build/reports/licensee/android<Variant>/artifacts.json`, and with `bundleAndroidAsset` one
`copy<Variant>LicenseeReportToAssets` task per variant [V `plugin.kt` at tag 1.14.1]. So the release tasks are
`licenseeAndroidStableRelease` and `licenseeAndroidPreviewRelease` (plus `licenseeAndroidFdroidRelease` if that flavour
exists); T2 runs all of them.

```kotlin
// build-logic: applied to :androidApp (and :desktopApp in the desktop phase). A convention plugin is plain Kotlin,
// so properties use .set(); bundleAndroidAsset and androidAssetReportPath are Property types [V pluginExtension.kt 1.14.1].
pluginManager.apply("app.cash.licensee")
extensions.configure<app.cash.licensee.LicenseeExtension> {
    allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allow("ISC")
    allowUrl("https://www.bouncycastle.org/licence.html") { because("MIT-style; only when FactsBook ships") }
    allowDependency("com.github.termux.termux-app", "terminal-emulator", "v0.118.3") {
        because("Apache-2.0 per termux-app LICENSE.md exception; the JitPack POM says 'Other'")
    }
    allowDependency("com.k2fsa.sherpa.onnx", "sherpa-onnx-android", "2.0.1") {
        because("Apache-2.0 per our vendored POM; the native parts inside (ONNX Runtime, Eigen MPL-2.0, …) are BUNDLED.toml entries, R-24")
    }
    bundleAndroidAsset.set(true)                           // feeds the About › Open-source licences list, per variant
    androidAssetReportPath.set("licenses/artifacts.json")
}
```

Licensee sees only Maven POMs. Code compiled into a vendored or prebuilt binary (the sherpa AAR, the proot set, the
Rust sandbox) is invisible to it, so those components must be `BUNDLED.toml` entries; `NoticesCurrentTest` fails when a
`third_party/SHA256SUMS` file or a `jniLibs` `.so` has no `BUNDLED.toml` entry.

### 8.3 Notices file

- `generateThirdPartyNotices` (W-18) writes `THIRD_PARTY_NOTICES.md` from the union of every release variant's
  Licensee report (`build/reports/licensee/androidStableRelease/artifacts.json`,
  `…/androidPreviewRelease/artifacts.json`; an entry that ships in only some variants names them), plus
  `native/third_party/BUNDLED.toml` (Table C as data: name, version, licence, licence file, source URL, modification
  note, sign-off), plus `native/third_party/DOWNLOADED.toml` (C-17: pin, SHA-256, licence, source URL, owner), which
  becomes the "Downloaded when you choose" section that 16 §10 item 2 requires.
- `NoticesCurrentTest` (`:quality:graph`, T2) fails when the committed file differs from the generated one. This
  replaces "notices match `pubspec.lock`" (B-BUILD-030).
- `LICENSES/` gains `GPL-2.0-proot.txt`, `LGPL-3.0-talloc.txt` (plus GPL-3.0, which LGPL-3.0 incorporates),
  `BSD-3-Clause-libandroid-shmem.txt`, `Apache-2.0-zxing-cpp` attribution, `MIT-qrose.txt`, the Termux fork's `NOTICE`,
  ONNX Runtime's `ThirdPartyNotices.txt` verbatim, `MPL-2.0-Eigen.txt` and the other texts of R-24's lists, and keeps
  `Rust-Phone-Engine.txt` (C-06a). It loses the Flutter-only texts (flutter-svg, path-parsing, vector-graphics, record,
  scrollable-positioned-list, image-picker, file-selection).
- The GPL/LGPL source offer for the proot set is 16 §10 item 3 (D16-8): one immutable `third-party-sources-1` GitHub
  release, named in the notices (R12-8).
- The About screen lists the variant's bundled `artifacts.json` entries plus `BUNDLED.toml` and `DOWNLOADED.toml`
  entries, through kit parts only (16 §10 item 1).

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
- `--write-verification-metadata` records only what the named tasks resolve, and strict mode then fails every other
  resolution: T3's `connectedStableReleaseAndroidTest` (androidx.test, UIAutomator), `:android:benchmark`
  (Macrobenchmark), Kover and dependency-analysis on the VPS, and the nightly `-Poc.ios=true` job (Kotlin/Native
  prebuilt, `*-iosarm64` klibs). So every convention plugin registers `resolveAllDependencies` in its project, which
  resolves every resolvable configuration:
  ```kotlin
  tasks.register("resolveAllDependencies") {
      notCompatibleWithConfigurationCache("resolves every configuration on purpose")
      doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
  }
  ```
- Create or refresh the file on the VPS with exactly these two runs (the second adds the iOS artifacts):
  `./gradlew --no-configuration-cache --write-verification-metadata sha256 resolveAllDependencies`, then
  `./gradlew --no-configuration-cache -Poc.ios=true --write-verification-metadata sha256 resolveAllDependencies`.
  [U P1] whether build-logic's own plugin resolutions are verified against this file (it is an included build); if
  not, build-logic gets the same task and its own `gradle/verification-metadata.xml`.
- `<trusted-artifacts>` in the file's `<configuration>`: `<trust file=".*-sources[.]jar" regex="true"/>` and
  `<trust file=".*-javadoc[.]jar" regex="true"/>` (IDE downloads; never on a classpath).
- Every dependency PR uses the same two commands. Review rule: only entries of the bumped group may change. Any other
  changed checksum blocks the merge until explained.
- `--dependency-verification strict` is the default (`org.gradle.dependency.verification=strict` in
  `gradle.properties`).

### 9.3 Vendored artifacts

| Artifact | Fetched by | Pin | Committed? | Rebuild owner |
|---|---|---|---|---|
| sherpa-onnx 2.0.1 AAR | `tools/vendor/fetch.sh` | sha256 `a8d09d1d…0ee7` | POM yes; AAR no (`.gitignore`: `native/third_party/maven/**/*.aar`, 49 MB) | media owner |
| proot / talloc / shmem `.deb`s | `tool/builtin_linux/fetch_proot.sh` (moved to `tools/vendor/`) | per-file sha256 (C-05) | the extracted `.so` files, as today | runtime owner |
| Termux terminal-view source | one-time copy at tag v0.118.3 | git tag + `PATCHES.md` | yes (source) | kit-content owner |
| Rust `libaiteam_sandbox.so` (C-06a) | `engine/phone/tool/build-android.sh` | `aiteam-engine-manifest.json` hash | as today | runtime owner, **at the switch** (11 D11-1) |
| Rust `libaiteam_engine.so`, `libaiteam_boundary_probe.so` (C-06b) | same | same | as today | runtime owner, only when AI Team returns (D6) |

`ThirdPartyHashTest` (03 §19.6) checks `third_party/SHA256SUMS` and that `git ls-files` lists every vendored `.pom`.
CI and the VPS run `tools/vendor/fetch.sh` before Gradle.

---

## 10. Update policy

### 10.1 Who

The **build-logic owner** (coordinator, ADR §5.2) is the only editor of `libs.versions.toml`,
`verification-metadata.xml`, `ExternalRules`, `dependencies.toml` and `DEPENDENCIES.md`. Feature agents propose; they
never bump. `DOWNLOADED.toml` (G8) is edited by the runtime and media owners, reviewed by the coordinator.

### 10.2 When (named triggers only)

| Group (moves as one PR) | Members | Trigger |
|---|---|---|
| G1 Toolchain | Kotlin, Compose compiler, Metro, KSP; then AGP + Gradle + apksig (N-17, same version as AGP) | ADR §8: Kotlin 2.5.0 once Metro and CMP declare support; AGP 9.5.x + Gradle 9.8.x together with it |
| G2 Compose | CMP (runtime … resources), material3, adaptive, Nav3, NavigationEvent, lifecycle, savedstate, ui-test | CMP 1.13.0 stable (first), then each CMP stable release; Android-only androidx *fix* bumps per ADR §7.6 |
| G3 AndroidX platform | core-ktx, work, activity, camera, profileinstaller, benchmark, tracing, lifecycle-process (moves with lifecycle in G2 when versions must match) | monthly window; CameraX 1.7.x when stable |
| G4 Network and data | Ktor + OkHttp (OkHttp follows Ktor's `requires`), serialization, coroutines, datetime, io, collections, Room 3 + sqlite-bundled | monthly window |
| G5 Content | Coil, Telephoto, qrose-encoder-matrix, markdown, SyntaxMP | monthly window; SyntaxMP or Telephoto only with the kit-content owner's visual check; qrose with the `KitQr` decode test (10 §14.1) |
| G6 Native binaries | sherpa-onnx, zxing-cpp, Termux emulator and fork, proot set, the Rust sandbox helper (C-06a), commons-compress | only on a named trigger (security fix, a needed feature, R12-6, R12-7), with a device proof; a sherpa bump also refreshes the ONNX Runtime and sherpa third-party entries (R-24) |
| G7 Test and quality tools | everything in §5.4, Licensee, ktfmt, detekt | quarterly, or when a tool blocks |
| G8 Managed-host runtime pins (`DOWNLOADED.toml`, C-17) | Ubuntu Base, Node, OpenCode, Paseo, Claude Code, Codex, Python; the Whisper and Kokoro model revisions | a named reason (security fix, a needed feature, a broken upstream) plus the phone-setup proof on the emulator (builtin-linux setup run, or voice-pack install for models); moved **only by app releases** (owner's layer-2 rule "tested pins only", memory `app-updates-two-layers`), so every bump ships in a full release; owners: runtime owner (11) for the host pins, media lead (10) for the models, with 13's size and setup-time budgets |

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
2. Edit only `libs.versions.toml`, plus `verification-metadata.xml` (§9.2, both `resolveAllDependencies` runs), the
   matching `dependencies.toml` entry and the `DEPENDENCIES.md` row (version, date, `[V]`).
3. T2 on the VPS: all JVM tests, Konsist, lint, `assembleStableRelease` and `assemblePreviewRelease`, the R8 analyzer,
   the size gate, the component freeze, `BannedDependenciesTest`, `BannedPatternsTest`, `PrereleaseGuardTest`,
   `ManifestGoogleRefsTest`, `licenseeAndroidStableRelease` and `licenseeAndroidPreviewRelease`, `NoticesCurrentTest`,
   `RegistryRowTest`.
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
runs in **report** mode until the switch candidate, then in **fail** mode, over every release variant.

The P1 fallback (CMP 1.12.2 with material3 `1.12.0-alpha03`, 03 §5) is an **alpha** and fails this rule. It is for
development builds only. If CMP 1.13.x is not stable when the switch is otherwise ready, the switch waits, unless the
owner amends D12 to allow that one M3 alpha inside the kit; only then does the allowlist gain
`org.jetbrains.compose.material3:*:1.12.0-alpha03`, in the same commit as the D12 amendment.

---

## 11. Size budget (arm64 split, compressed) [est]

"Today" is the **published** arm64 split APK (built with `--split-debug-info`, about 36 MB [R map PACKAGES-BUILD-07]),
not the 4511 test build (no `--split-debug-info`, so its `libapp.so` is 12.9 MB instead of 9.4 MB).

| Part | Today (published arm64 split) | Native at the switch |
|---|---|---|
| Dart AOT + engine (`libapp`, `libflutter`) | 16.1 MB (9.4 + 6.7) | 0 |
| App code: Compose, M3, Nav3, Ktor, OkHttp, serialization, Metro, Coil, Telephoto, markdown, SyntaxMP, CameraX (dex), WorkManager, our Kotlin | 2.0 MB dex | about 4–5 MB dex [est, **unmeasured**]: the demo's 2.5 MB APK covered Compose, M3, Nav3, Ktor and serialization; CameraX dex (≤ 1.2 MB) and Coil (≤ 1.0 MB) are 10 §2.5 budgets; our own code is a guess for about 170k lines of Kotlin |
| Speech: ONNX Runtime + sherpa | about 10.0 MB (7.96 + C API 1.89 + C++ API about 0.15) | about 9.9 MB (7.96 + JNI 1.9; 10 §2.5 cap; R12-6 may cut it) |
| QR | 3.0 MB (ML Kit 2.11 + 0.9 MB models) | about 0.8 MB (zxing-cpp 0.73 + CameraX JNI helpers about 0.1) |
| proot set + `libtermux` | about 0.2 MB | about 0.2 MB |
| Rust AI Team | 3.1 MB (engine 2.44 + probe 0.53 + sandbox about 0.15) | about 0.15 MB: the sandbox helper only (C-06a, 11 D11-1); engine and probe when AI Team returns (D6) |
| Room `sqlite-bundled` | — | 0 at the switch (D9 default); about 0.6–1.3 MB when Room lands |
| Resources, strings (7 languages), fonts, licences | about 2.4 MB (`res` 1.4 + `flutter_assets` 0.9 + profile) | about 2 MB (Phosphor TTFs gone; 7-language XML) |
| **Total** | **≈ 36–37 MB** (matches the published ≈ 36 MB) | **≈ 17–18 MB** |

**Where the ≈ 19 MB difference comes from (same ABI, same signing, Astra N1):**

| Cause | MB [est] | Kind |
|---|---|---|
| Flutter engine and Dart AOT gone, Compose/Kotlin dex added (16.1 − about 2.5 net dex growth) | ≈ 13.6 | framework replacement |
| ML Kit → zxing-cpp | ≈ 2.2 | library choice (same feature) |
| AI Team engine and probe not in the switch release | ≈ 2.95 | **deferred feature** (D6); comes back with M8 |
| Phosphor TTFs, smaller resources | ≈ 0.4 | packaging |

So the like-for-like saving from the rewrite itself is about 14 MB of 36; about 3 MB is a feature that returns later.
The owner-facing comparison is always **arm64 split vs arm64 split**, both release-signed, measured with `apkanalyzer`
on the real files at each milestone (13 §8.2); the 107 MB figure the README used earlier was not the arm64 split a phone downloads (Astra N1) and
is not used for comparisons.

The gate belongs to 13 §8.2 (working target ≤ 20.0 MB, gate 22 MB, per-component sub-budgets), not to this section.
Because the native dex figure is unmeasured, this section asks 13 (§14 item 17) to make the absolute number a
**ratchet**: set it from the first measured slice-1/2 release APK (with P8 and P10's numbers) plus the named, known
additions still to come (voice, scanner, sandbox), and from then on only lower it. Until that measurement exists, the
gates that apply are `check_apk_size.py`'s 5 MiB growth rule against the previous tag's arm64 asset and the +500 KB
per-dependency sign-off (§0.4).

## 12. Acceptance checks (all run at T2 unless marked)

### 12.1 Tests and tasks

Variants: 03 §21.1 declares the flavour dimension `channel` with `stable` and `preview`, so AGP creates
`stableReleaseRuntimeClasspath` and `previewReleaseRuntimeClasspath`; there is no plain `releaseRuntimeClasspath`.
Every check below runs **per shipped release variant**, discovered with
`androidComponents.onVariants(selector().withBuildType("release"))`, so an `fdroid` flavour (§7.1) is covered the day it
is added. A check that finds no release variant fails.

The two `:quality` modules keep 03 S22: `:quality:rules` has no project dependencies (T0-fast, pure file checks);
`:quality:graph` may depend on `:androidApp` and runs at T2 on the VPS only, so every check that needs the resolved
release graph or a merged manifest lives there.

| Check | Where | Fails when |
|---|---|---|
| `releaseClasspathReport<Variant>` tasks | build-logic, on `:androidApp` | the variant's runtime classpath is empty (writes `build/reports/oc/release-classpath-<variant>.txt`, one `group:artifact:version` per line) |
| `BannedDependenciesTest` | `:quality:graph` | a line of any variant's report matches `banned-dependencies.txt` (X-01 … X-23, X-27 … X-29, X-31 … X-39; X-39 for `fdroidRelease` only); or the `stableRelease`/`previewRelease` report is missing |
| `BannedPatternsTest` | `:quality:rules` | a required coordinate (K-04 `terminal-emulator`, `ktor-client-core(-jvm)`, `ktor-serialization(-jvm)`, `ktor-websocket-serialization-jvm`, `material3-android`, `material-ripple-android`, `ui-tooling-preview-android`, `guava:listenablefuture`, `kotlin-stdlib`) matches a ban, or a known banned one (`termux-shared`, `ktor-serialization-kotlinx-json-jvm`, `ktor-client-content-negotiation-jvm`, `play-services-base`, `guava:guava`, `kotlin-reflect`, `media3-exoplayer`) matches none |
| `PrereleaseGuardTest` | `:quality:graph` | X-25 in any variant, outside the M3 allowlist (report mode until the switch candidate) |
| `ManifestGoogleRefsTest` | `:quality:graph` | a variant's merged manifest holds a `com.google.android.gms` / `com.google.firebase` string beyond the Photo Picker backport `ModuleDependencies` entry (stable, preview), or any at all, or `REQUEST_INSTALL_PACKAGES`, in `fdroid` (§7.1) |
| `RegistryRowTest` | `:quality:rules` | a `[libraries]` or `[plugins]` alias in `libs.versions.toml` has no entry in `native/docs/dependencies.toml`, an entry's version differs from the catalog, or an entry's `row` id has no row in `native/docs/DEPENDENCIES.md` |
| `ExternalRules` (03 §6, extended in 12.2) | build-logic, configuration time | a module declares a library it may not |
| Konsist X-30 rules | `:quality:rules` | a banned import inside an allowed library, or `getDir("flutter"` |
| Licensee `licenseeAndroidStableRelease`, `licenseeAndroidPreviewRelease` | `:androidApp` | a licence is outside §8.1 |
| `NoticesCurrentTest` | `:quality:graph` | `THIRD_PARTY_NOTICES.md` differs from `generateThirdPartyNotices`' output, or a vendored or `jniLibs` binary has no `BUNDLED.toml` entry |
| `ThirdPartyHashTest` | `:quality:rules` | a vendored file's SHA-256 differs, or `git ls-files` does not list a vendored `.pom` |
| dependency verification | every Gradle run | a checksum differs (§9.2) |
| `CatalogOnlyTest` | `:quality:rules` | a `build.gradle.kts` outside build-logic contains a `"group:artifact:version"` literal |
| size gate | T2 + release | 13 §8.2's gate or the 5 MiB growth rule (§11) |
| release smoke (T3) | emulator, release build | R8 strips sherpa JNI fields, `WorkDatabase_Impl`, zxing-cpp, a serializer, or a Metro graph accessor |

**Inputs, so the build cache cannot hide a change** (03 §4.1 turns the build cache on). A test task is up to date
when its declared inputs are unchanged, so every file a `:quality` test reads from outside its own module is a declared
input:
- `:quality:graph` gets the reports **as a resolved artifact of `:androidApp`**, never as a path string. Gradle then
  runs the producing tasks first and fingerprints the files.
- `:quality:rules` declares its outside files with `inputs.files(…)`: `third_party/SHA256SUMS`, the vendored files,
  `native/quality/data/banned-dependencies.txt`, `libs.versions.toml`, `native/docs/dependencies.toml`,
  `DEPENDENCIES.md`, and the module scripts `CatalogOnlyTest` reads. `:quality:graph` declares
  `THIRD_PARTY_NOTICES.md`, `BUNDLED.toml`, `DOWNLOADED.toml` and the banned list the same way.

```kotlin
// build-logic, inside the oc.android.application convention plugin. No cross-project configuration (03 §3.2).
val OC_REPORT: Attribute<String> = Attribute.of("io.github.eslamasabry.oc.report", String::class.java)

abstract class ReleaseClasspathReport : DefaultTask() {
    @get:Input abstract val variantName: Property<String>
    @get:Input abstract val modules: ListProperty<String>
    @get:OutputFile abstract val output: RegularFileProperty
    @TaskAction fun write() {
        val lines = modules.get().sorted()
        check(lines.isNotEmpty()) { "Empty release classpath for ${variantName.get()}" }
        output.get().asFile.writeText(lines.joinToString("\n"))
    }
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

// One consumable configuration carries every release report of this app module to :quality:graph.
val releaseReports = configurations.consumable("ocReleaseReportsElements") {
    attributes.attribute(OC_REPORT, "release-reports")
}
extensions.getByType<ApplicationAndroidComponentsExtension>()
    .onVariants(selector().withBuildType("release")) { variant ->            // stableRelease, previewRelease (, fdroidRelease)
        val cap = variant.name.replaceFirstChar(Char::uppercaseChar)
        val report = tasks.register<ReleaseClasspathReport>("releaseClasspathReport$cap") {
            variantName.set(variant.name)
            modules.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                .map(::moduleCoordinates))                                   // [U P1] configuration-cache-safe form
            output.set(layout.buildDirectory.file("reports/oc/release-classpath-${variant.name}.txt"))
        }
        val manifest = tasks.register<Copy>("copyMergedManifest$cap") {      // ManifestGoogleRefsTest input
            from(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            rename { "merged-manifest-${variant.name}.xml" }
            into(layout.buildDirectory.dir("reports/oc/manifest-${variant.name}"))
        }
        releaseReports.configure {
            outgoing.artifact(report.flatMap { it.output })
            outgoing.artifact(layout.buildDirectory.file("reports/oc/manifest-${variant.name}/merged-manifest-${variant.name}.xml")) {
                builtBy(manifest)
            }
        }
    }
// generateThirdPartyNotices (W-18) publishes its output file on the same configuration.
```

```kotlin
// native/quality/graph/build.gradle.kts — resolves the reports, so Gradle orders the tasks and fingerprints the files.
val releaseReportsScope = configurations.dependencyScope("releaseReports")
val releaseReportFiles = configurations.resolvable("releaseReportFiles") {
    extendsFrom(releaseReportsScope.get())
    attributes.attribute(OC_REPORT, "release-reports")                       // the same attribute object, from build-logic
}
dependencies { add("releaseReports", project(":androidApp")) }               // allowed: :quality:graph → any (03 §6.6)

abstract class QualityInputs : CommandLineArgumentProvider {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val reports: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val banned: RegularFileProperty
    override fun asArguments() = listOf(
        "-Doc.releaseReports=" + reports.files.joinToString(File.pathSeparator),   // file names carry the variant
        "-Doc.bannedList=" + banned.get().asFile.path,
    )
}
tasks.test {
    jvmArgumentProviders += objects.newInstance<QualityInputs>().apply {
        reports.from(releaseReportFiles)
        banned.set(layout.projectDirectory.file("../data/banned-dependencies.txt"))
    }
    inputs.files("../../THIRD_PARTY_NOTICES.md", "../../third_party/BUNDLED.toml", "../../third_party/DOWNLOADED.toml")
        .withPathSensitivity(PathSensitivity.RELATIVE)                        // [U P1: exact relative paths]
}
```

```kotlin
// shared by both :quality modules (a copy in each test source set; 20 lines, so no project dependency is needed)
internal data class Ban(val regex: Regex, val id: String, val variants: Set<String>)    // empty variants = all

internal fun parseBans(text: String): List<Ban> = text.lines().map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .map { line ->
        val parts = line.split("|").map(String::trim)
        // exact group:artifact glob: '*' never crosses ':', and the match must end at the artifact (then ':version')
        val regex = Regex("^" + parts[0].split("*").joinToString("[^:]*") { Regex.escape(it) } + ":")
        Ban(regex, parts[1], parts.getOrNull(2).orEmpty().split(",").map(String::trim).filter(String::isNotEmpty).toSet())
    }

// native/quality/graph/src/test/kotlin/io/github/eslamasabry/oc/quality/graph/BannedDependenciesTest.kt
class BannedDependenciesTest {
    private val reports = System.getProperty("oc.releaseReports").split(File.pathSeparator).map(::File)
        .filter { it.name.startsWith("release-classpath-") }
    private val bans = parseBans(File(System.getProperty("oc.bannedList")).readText())
    private fun File.variant() = name.removePrefix("release-classpath-").removeSuffix(".txt")

    @Test fun `every shipped release variant has a report`() {
        val variants = reports.map { it.variant() }.toSet()
        assertTrue(variants.containsAll(setOf("stableRelease", "previewRelease")), "Release variant reports: $variants")
    }

    @Test fun `S-23 no banned artifact ships in any release variant`() {
        val hits = reports.flatMap { file ->
            file.readLines().filter(String::isNotBlank).flatMap { gav ->
                bans.filter { (it.variants.isEmpty() || file.variant() in it.variants) && it.regex.containsMatchIn(gav) }
                    .map { "${file.variant()}: $gav (${it.id})" }
            }
        }
        assertTrue(hits.isEmpty(), "Banned (plan 12 Table F):\n" + hits.joinToString("\n"))
    }
}

class PrereleaseGuardTest {
    private val anyPre = Regex("-(alpha|dev|eap|snapshot|beta|rc)", RegexOption.IGNORE_CASE)
    private val betaOrRc = Regex("-(beta|rc)", RegexOption.IGNORE_CASE)
    private val m3Groups = listOf("org.jetbrains.compose.material3:", "androidx.compose.material3:")
    private val failMode = System.getProperty("oc.switchCandidate") == "true"

    @Test fun `D12 no pre-release outside the M3 allowlist in any release variant`() {
        val bad = System.getProperty("oc.releaseReports").split(File.pathSeparator).map(::File)
            .filter { it.name.startsWith("release-classpath-") }
            .flatMap { file -> file.readLines().filter { gav ->
                val version = gav.substringAfterLast(':')
                val allowedM3Beta = m3Groups.any(gav::startsWith) && betaOrRc.containsMatchIn(version)
                anyPre.containsMatchIn(version) && !allowedM3Beta
            }.map { "${file.name}: $it" } }
        if (failMode) assertTrue(bad.isEmpty(), "Pre-release in the switch release (ADR D12): $bad")
        else println("D12 report (not failing before the switch candidate): $bad")
    }
}
```

**`native/docs/dependencies.toml`** (the machine-readable registry `RegistryRowTest` reads; `DEPENDENCIES.md` stays the
human view and cites the same row ids). One table per catalog alias, for example:

```toml
[ktor-client-core]
row = "N-40"          # must exist as "| N-40 |" in DEPENDENCIES.md
version = "3.6.1"     # must equal libs.versions.toml
modules = [":core:net", ":protocol:oc2", ":protocol:paseo", ":protocol:codex", ":core:testing"]
licence = "Apache-2.0"
variants = ["stable", "preview", "fdroid"]   # §7.1; "fdroid" absent for the sherpa AAR

[qrose-encoder-matrix]
row = "N-37"
version = "1.3.1"
modules = [":ui:kit-content", ":engine:media"]
licence = "MIT"
variants = ["stable", "preview", "fdroid"]
```

### 12.2 `ExternalRules` additions for 03 §6 (build-logic owner)

`ExternalRules.allowed` is a `mapOf`, and `EnforceRules` takes the **first** key that matches (`group:artifact` or
`group`), so the order below matters: specific `group:artifact` keys come before their group's key, and 03's
`coil-network-*` → false entries stay above the Coil group entry.

```kotlin
// Add to ExternalRules.allowed (03 §6.6). Everything not listed stays free for any module that the layer rules allow.
"io.coil-kt.coil3" to { p -> p == ":ui:kit" || p == ":ui:kit-content" },        // below 03's coil-network-* → false
"me.saket.telephoto" to { p -> p == ":ui:kit-content" },
"io.github.alexzhirkevich" to { p -> p == ":ui:kit-content" || p == ":engine:media" },   // qrose-encoder-matrix (10 §2.1)
"com.gallatinapps.syntaxmp" to { p -> p == ":ui:kit-content" },
"com.google.zxing" to { p -> p == ":ui:kit-content" || p == ":engine:media" },  // test configurations only; X-37 keeps it out of release
"org.apache.commons:commons-compress" to { p -> p == ":android:linux" },
"androidx.work" to { p -> p in setOf(":android:schedule", ":android:widgets", ":android:background", ":androidApp") },
"androidx.lifecycle:lifecycle-process" to { p -> p == ":android:platform" },   // N-62
"androidx.appfunctions" to { p -> p == ":android:platform" },                    // after the switch
"org.bouncycastle" to { p -> p == ":android:factsbook" },                         // later, D6 (module name fixed then)
"androidx.biometric" to { p -> p == ":android:factsbook" },                       // only R-17's fallback, later
"androidx.datastore" to { p -> p == ":core:data" },                               // desktop/iOS source sets only
"androidx.profileinstaller" to { p -> p == ":androidApp" },
"com.google.android.material" to { _ -> false },                                  // X-24, MDC
"io.ktor:ktor-server-cio" to { p -> p == ":tools:contracts" },                    // N-83; ABOVE 03's "io.ktor" key
"com.networknt" to { p -> p == ":tools:contracts" },                              // N-82
// Replace 03's termux entry (the fork and the port module declare terminal-emulator too):
"com.github.termux.termux-app" to { p -> p in setOf(":android:terminal", ":ui:kit-content",
    ":thirdparty:termux-session-port", ":thirdparty:termux-terminal-view") },
// Already in 03 §6.6, unchanged: material3, adaptive, io.ktor (net, protocol, testing, apps), okhttp3, room3, sqlite,
// co.touchlab, androidx.camera, io.github.zxing-cpp, com.k2fsa.sherpa.onnx, markdown, coil-network-* → false,
// androidx.appcompat → false.
```

**Coil and the app modules (decision, was R12-4).** Neither `:androidApp` nor `:desktopApp` declares any Coil artifact.
10 §9.1 already builds the loader in `:ui:kit-content` (`KitImageLoaders.create`); the remaining leak is that
`LocalKitImageLoader` is typed with Coil's `ImageLoader`, so the app module that provides it would need Coil on its
compile classpath, and 03 §6.6 forbids `api` leaks. So the kit exposes **no Coil type**: `LocalKitImageLoader` holds
our own `KitImageLoader` (in `:ui:kit`, wrapping Coil internally), `KitImageLoaders.create(context, resolver):
KitImageLoader` lives in `:ui:kit-content`, and the profile-eviction hook is `KitImageLoader.evict(profile)`. The app
module calls `create` once and provides the result; MV-3's `androidApp/**/AppImageLoader.kt` shrinks to that call
(§14 item 13).

`ModuleRules` (03 §6.6) additions (R12-2): `"thirdparty"` joins the module-discovery folders (03 §3.1), and
- `:thirdparty:termux-session-port` may be used by `:thirdparty:termux-terminal-view`, `:ui:kit-content` and
  `:android:terminal`;
- `:thirdparty:termux-terminal-view` may be used by `:ui:kit-content` only;
- a `:thirdparty:*` module itself has no project dependencies except the view fork → the port module.

### 12.3 Slice placement

| Slice | Registry work |
|---|---|
| 0 | catalog + `dependencies.toml` + verification metadata (both `resolveAllDependencies` runs) + `ExternalRules` additions; `releaseClasspathReport<Variant>`, the `:quality:graph` wiring, `BannedDependenciesTest`, `BannedPatternsTest`, `PrereleaseGuardTest` (report mode), `RegistryRowTest`, `CatalogOnlyTest`; Licensee trial (N-12); `DEPENDENCIES.md` seeded from §1–§7; `tools/vendor/fetch.sh`; W-01, W-02 with their iOS actuals |
| 1 | W-03 (with iOS actuals), W-07, W-06 (with 04); W-20 API, `None` sender and queue (WP-16.7); accessibility tool choice (N-87) |
| 2 | R-11 benchmark decision; W-17; W-16 |
| 3 | R-31 fork + session-port module (W-14) + P9 addendum; K-03 / R12-7; W-11 listener with its first user (Termux migrate-out receiver); C-06a sandbox helper (runtime owner); media starts (10 C10-3): W-04, W-05, W-19, W-09, R-19 scanner (MV-9) |
| 4 | W-12 `AppUpdater` (WP-16.6; it ships in the switch release); W-20 senders and Privacy rows (WP-16.7). The other W-11 users (connector search, git repair) land with their features' slices |
| 5 | R-24 sherpa + its third-party notices + R12-6; W-10; the Wi-Fi share binding of W-11 (10 MV-8); the MCP OAuth callback user of W-11 (07 I15) |
| 6 (switch) | `PrereleaseGuardTest` in fail mode; notices with C-05 fixed and the `third-party-sources-1` release named (16 D16-8); `ManifestGoogleRefsTest` on every variant; size check against 13's gate; licence review of the final classpath |

---

## 13. Decisions needed

Each has a default; work proceeds on it until the named person answers. Nothing here waits on the owner.

| # | Decision | Who | Default |
|---|---|---|---|
| R12-1 | **Analytics: decided** (owner, 2026-11-01, memory `freeze-owner-decisions-2026-11-01` item 6, "don't re-raise these"). The core is built from day one: section 18 (`:engine:analytics`, 03 S27; supersedes 16 N16-D9), 15 C15-9, 02 G7 / row 17 / NG15 / S02-5. The registry's part is W-20 and the bans X-01 and X-04 (no vendor SDK; our own HTTP senders). **Open, and owned elsewhere:** which destination (16 D16-1 / 02 S02-5) and the F-Droid "Tracking" label (16 D16-2). | — | Core built 2026-11-01 (`native/analytics-2026-11-01`, 49 tests, not merged); `sender=none` until 16 D16-1 is answered; F-Droid label decided at submission (18 §7). |
| R12-2 | Fork Termux `terminal-view` (W-14) as `:thirdparty:termux-terminal-view`, with the port in its own `:thirdparty:termux-session-port`, and add `thirdparty` to the module discovery folders (03 §3.1). | coordinator | Fork, because the stock view cannot show remote PTYs (R-31, verified in source). The alternative is our own `TerminalRenderer`-based view, about 1,500 lines. |
| R12-3 | No `androidx.appcompat`: the launch themes move from `Theme.AppCompat.DayNight.NoActionBar` to platform `android:Theme.Material.Light.NoActionBar` with a `values-night` override, and the locale host is `PlatformLocaleHost` (`LocaleManager`; 03 S21 and 06 K16 now agree). **Also:** ADR §4 row 6 names the P6 fallback as an override "driven by the `AppCompatDelegate` locale", which X-24 makes impossible; amend it to "driven by the `LocaleManager` locale on API 33+, or the persisted `oc.appLocale` tag applied through `createConfigurationContext` on API 26–32" (§14 item 6). | coordinator + android-platform owner | No appcompat; ADR §4 row 6 amended as written. |
| R12-4 | Coil never fetches a URL, and the app modules declare no Coil artifact: 03 §5 and §6.6 already ban every `coil-network-*`; the kit exposes no Coil type, so `:androidApp`/`:desktopApp` provide a `KitImageLoader` of ours (§12.2). | build-logic owner + kit-content owner | As stated (10 §9.1 adjusts its `LocalKitImageLoader` type, §14 item 13). |
| R12-5 | *Withdrawn.* xmlutil is not used (10 M7, C10-20); ADR §7.2 needs no new line. | — | — |
| R12-6 | Measure the `sherpa-onnx-static-link-onnxruntime-2.0.1.aar` variant. | media owner, slice 5 | Adopt only if the arm64 APK shrinks by ≥ 1 MB and the release smoke plus the dictation and Kokoro tests pass. |
| R12-7 | commons-compress 1.27.1 → 1.28.0. | runtime owner, slice 3 | Move once the Ubuntu Base tar fixtures pass on 1.28.0; otherwise stay. |
| R12-8 | **Licence gap for proot, talloc and shmem** (C-05): add the texts, the exact versions, the patchelf note and a source pointer. This also affects today's Flutter releases. | runtime owner does it; the source offer itself is 16 D16-8 (owner) | 16 §10 item 3 (D16-8): **one immutable GitHub release `third-party-sources-1`** (never "latest"; a tag that is not version-shaped, so the updater skips it) carrying the proot, talloc and shmem source tarballs and the Termux patches; `THIRD_PARTY_NOTICES.md` names that URL; a new `third-party-sources-N` only when a pin changes. Never attach tarballs to app releases: a release's asset set is exactly the APKs plus `SHA256SUMS` (`scripts/verify_github_release.py`, map PACKAGES-BUILD-31/32). |
| R12-9 | FactsBook, when it is scheduled: (a) crypto: BouncyCastle 1.86 light-weight Ed25519 on every API level, or platform `Signature("Ed25519")` on API 33+ with BouncyCastle below; (b) device check (R-17): platform `BiometricPrompt` on API 30+ and `KeyguardManager.createConfirmDeviceCredentialIntent` on 26–29, or androidx.biometric 1.1.0 in a dedicated `FragmentActivity` (an X-24 exception). | FactsBook builder | (a) BouncyCastle everywhere (one code path, RFC 8032 vectors, B-SEC-066); (b) the platform path (no library, no AppCompat). |
| R12-10 | Licensee vs our own notices task only. | build-logic owner, slice 0 | Licensee if it runs on AGP 9.3.3 + Gradle 9.7.1; otherwise W-18 alone reads POM licences. |
| R12-11 | Where the one in-app HTTP listener (W-11) lives. | coordinator + android-platform owner | Interface and policy in `:core:domain`; `SocketLocalHttpServer` in `:android:platform`, moved unchanged to an `oc.jvm.library` module in the desktop phase. The alternative is a new `oc.jvm.library` module now (an ADR §5.2 module-list change). |

---

## 14. Disagreements found with sibling sections (for the coordinator)

Checked against the sibling files as they stood at about 16:30 on 2026-11-01 (several were being revised at the time).

1. **03 §5** still declares `ktor-client-websockets`; **04 §2.3** says it is not used on Android or desktop and adds
   `ktor-websockets`. The registry follows 04 (R-07). Open.
2. *Resolved:* 03 §5 and §6.6 now ban every `coil-network-*` artifact (was R12-4).
3. *Resolved:* 03 S21 / §12.3 now uses `PlatformLocaleHost` with `LocaleManager`, as 06 K16 does (was R12-3).
4. **ADR §8 / 03 §3.1 / 03 §5** take Termux `terminal-view` from JitPack and 03's module discovery has no `thirdparty`
   folder; a remote PTY needs the fork and the port module (R-31, R12-2). ADR §4 row 15's decision (Termux view on day
   1) stands. 03 §6.6's termux `ExternalRules` entry must also admit the two `:thirdparty:*` modules (§12.2).
5. *Resolved:* xmlutil is not used; 10 M7 stands (10 C10-20 path "if M7 stands" is now done here: N-39 withdrawn, R12-5
   withdrawn, G5 and `ExternalRules` cleaned, X-38 added).
6. **ADR §4 row 6** names an `AppCompatDelegate`-driven P6 fallback, which X-24 forbids; amend as R12-3 says.
7. **ADR §9.6** "Nothing phones home" vs the owner's analytics decision: routed by 16 CR16-1; 02 (G7, row 17, NG15,
   S02-5) already follows the decision. **15** C15-9 and S4-ANALYTICS still plan a new `:engine:analytics` module with
   R12-1 as the decider, while 16 N16-D9 puts the core in `:engine:diagnostics` with D16-1 as the decider; the registry
   follows 16 (W-20). 15 should point to 16 D16-1 and N16-D9.
8. **Today's notices** omit proot, talloc and shmem (R12-8), and the sherpa/ONNX Runtime third-party parts (R-24).
9. **03 §6.6 `ExternalRules`** lacks entries for Coil, Telephoto, qrose, SyntaxMP, ZXing core, commons-compress,
   WorkManager, lifecycle-process, the `:tools:contracts` libraries (Ktor server CIO must sit above the `io.ktor` key)
   and the later libraries (§12.2 gives them, in order).
10. **03 §21.1** still lists a hand-written `xml/locales_config.xml` in `:androidApp/res`; **06 §13.6 item 4** uses
    `generateLocaleConfig` and says the build fails if both exist. The registry follows 06 (R-02); 03 should drop the
    file.
11. **10 M8 / §4.2 (C10-2)** defines `Sha256`, `RawInflate` and `secureRandomBytes`; the code also needs SHA-1, MD5,
    HMAC-SHA256, zlib-wrapped inflate and gzip encode (W-01, W-02). **C12-1 to 03 §10.1:** adopt W-01/W-02's merged
    contract, which keeps every 10 §4.2 name and signature, so 10 needs no change. 16 K-SEC-12 is satisfied by the
    same `secureRandomBytes`.
12. **10 M14 / §13.3** builds a dedicated `AndroidLanFileServer` in `:android:media`, and no section builds the other
    four in-app servers (MCP OAuth callback, which 07 I15 relies on; connector search bridge; git link repair receiver;
    Termux migrate-out receiver) or the BYO SSH port helpers. **C12-2 to 10 and 03 §10.4:** one `LocalHttpServer` (W-11)
    in `:core:domain` + `:android:platform`; 10's Wi-Fi share keeps its rules, `ShareHttp`, `WifiAddress`, module and
    slice, and binds through it (its `LocalFileServer` seam becomes a thin common adapter over W-11). The behaviour of
    10 §13.1–§13.3 and 16 N16-D11 is unchanged.
13. **10 §9.1** types `LocalKitImageLoader` with Coil's `ImageLoader`, and 10 MV-3 still lists
    `androidApp/**/AppImageLoader.kt`; the app module would then need Coil on its compile classpath, which §12.2 and
    03's "never `api`" rule refuse. Ask 10 and 06 to type it with our `KitImageLoader` (R12-4).
14. **16 §9.1 F8** and **13 §8.2 / §8.5** say no `libaiteam_*` ships at the switch; **11 D11-1** (default yes) ships
    `libaiteam_sandbox.so`. The registry follows 11 (C-06a, about +0.15 MB); 13's sub-budget and 16's F8 row need the
    sandbox line, and ADR §9.9's "if AI Team ships" guard wording should say "for the sandbox helper at the switch,
    and the engine when AI Team ships".
15. **10 §2.7** describes an `fdroid` flavour "defined by section 16" (no sherpa AAR, no `REQUEST_INSTALL_PACKAGES`, no
    `ModuleDependencies` entry), but **16 §5.9 and F12** assume F-Droid rebuilds our stable APK byte for byte, and 16
    defines no such flavour. Decider: coordinator with 16's release lane, before the F-Droid slice (after the switch).
    The registry's checks iterate over every release variant, so either answer works (§7.1).
16. **10 §2.2** repeats the git-ignored-folder wording for the sherpa AAR; it must ignore only `*.aar` so the
    committed `.pom` is tracked (R-24).
17. **13 §8.2** fixes a 20.0 MB working target and a 22 MB gate before any native release APK has been measured;
    the registry asks 13 to make it a ratchet seeded by the first measured slice-1/2 release APK (§11).
18. **16 §6.2** uses `com.android.tools.build:apksig` at build time; the registry now has its build-logic row (N-17,
    version = AGP), so `RegistryRowTest` and dependency verification cover it.
19. **16 §8.1** fetches `releases/latest`, but catalog row **B-BUILD-011** requires reading the releases list, skipping
    drafts and picking the highest valid build among unordered entries (malformed or unversioned entries never hide an
    update). Decider: 16's release lane; the registry is unaffected (no library either way).

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
- **Added in the review pass (2026-11-01, [V] unless marked):**
  - Repo: the five `dart:io` servers and the BYO SSH sockets (`lib/domain/mcp_oauth.dart:151`,
    `lib/state/mcp_connector_search_bridge.dart:20-22,46`, `lib/builtin/git_link_repair.dart:221-245,676`,
    `lib/builtin/migration/termux_migration_transport.dart:382` (PUT `/archive`, `x-offset`),
    `lib/platform/wifi_file_share.dart:165`, `lib/host/byo_host_ssh_runner.dart:583,604`); `md5.convert`
    (`lib/domain/provider_logo_store.dart:163,204`), `Hmac(sha256, …)` (`profile_monitor.dart:92`,
    `provider_quota_monitor.dart:186`), `gzip.encode` (`lib/builtin/agents/paseo_scripts.dart:140`); the `intl` skeleton
    counts of R-03; `lib/ui/kit/kit_web_preview.dart` and `devPreviewMayLoad`; the instrumentation runners under
    `android/app/src/androidTest`.
  - Termux tag v0.118.3: `TerminalView` calls `mTermSession.write(String)` at lines 767, 902, 1049; `TerminalOutput.write(String)`
    is `final`; `TerminalEmulator` calls only `getTerminalCursorStyle`, `onTerminalCursorStateChange` and logging on its
    `TerminalSessionClient`; termux-app `LICENSE.md` (GPLv3 with the view/emulator exception).
  - Licensee 1.14.1 `plugin.kt`: `licenseeAndroid<Variant>`, `reports/licensee/android<Variant>`,
    `copy<Variant>LicenseeReportToAssets`; `pluginExtension.kt`: `bundleAndroidAsset` and `androidAssetReportPath` are
    `Property` types.
  - POMs: `ktor-client-core-jvm` 3.6.1 → `ktor-websocket-serialization-jvm` → `ktor-serialization-jvm` (compile scope);
    `androidx.biometric` 1.1.0 → `appcompat` 1.2.0, `fragment` 1.2.5; `qrose-encoder-matrix` 1.3.1 MIT.
  - Google Maven: `com.android.tools.build:apksig` 9.3.3; `androidx.lifecycle:lifecycle-process` 2.11.0; Accessibility
    Test Framework 4.1.1. Maven Central: `roborazzi-accessibility-check` 1.77.0.
  - sherpa-onnx v2.0.1: `build-android-arm64-v8a.sh` pins ONNX Runtime 1.28.2 and enables TTS; its `cmake/` folder
    lists eigen, kaldi-native-fbank, kaldi-decoder, openfst, json, utf8proc, hclust-cpp, simple-sentencepiece. ONNX
    Runtime `ThirdPartyNotices.txt` (`main`) lists Eigen under MPL-2.0 [U: identical at v1.28.2].
  - Plan siblings: 03 §3.1, §4.1, §5, §6.6, §21.1; 06 §13.6; 10 §0.2–§0.3, §2.1–§2.7, §4.2, §4.5, §9.1, §13, §14;
    11 D11-1, U11-13, line 442; 13 SR3, SR6, §8.1–§8.7; 15 C15-9, S4-ANALYTICS; 16 §5, §6.2, §8, §9.1, §10, §12; 07 I15.
- **[U], settled later:** CMP 1.13.0 stable date; Licensee on AGP 9.3.3; the configuration-cache-safe form of
  `releaseClasspathReport<Variant>` and of the `:quality:graph` wiring; whether build-logic's resolutions are covered
  by the root verification file; SyntaxMP's quality on real agent output; the static-link sherpa AAR's size; the native
  dex size (§11); the iOS platform libraries on the Linux klib host (W-01–W-03); F-Droid's verdict on Maven-Central
  native AARs; Dependabot security-update support for Gradle version catalogs.

---

## Review notes (2026-11-01)

Changed (issue → fix):
- **In-app servers** (two reports): every `dart:io` server now has an allowed home. New W-11 `LocalHttpServer`
  (+ `LoopbackPorts` for BYO SSH): interface and policy in `:core:domain`, one `ServerSocket` implementation in
  `:android:platform`, with a table of its five users (MCP OAuth callback, connector search, git repair, Termux
  receive, Wi-Fi share), their engines, limits and catalog rows. Wi-Fi share keeps 10's module and slice and binds
  through it (C12-2, §14 item 12). X-27 now names `LocalHttpServer` and also bans Ktor server from the app.
- **`:quality` wiring:** reports reach `:quality:graph` as a resolved artifact of `:androidApp` with fingerprinted
  inputs (no path strings); every outside file a `:quality` test reads is a declared input; S22 kept by putting
  graph-dependent tests in `:quality:graph`.
- **`:core:common` primitives** (two reports): one merged contract that keeps 10 §4.2's names (`Sha256`, `RawInflate`,
  `secureRandomBytes`) and adds incremental `Digest` (SHA-1, MD5), `hmacSha256`, `ZlibInflate` and `Gzip.encode`,
  with Dart-generated vectors, git fixtures, the logo MD5 and a Paseo round trip; recorded as C12-1 (§14 item 11).
- **Flavoured names** (two reports): every check runs per release variant (`onVariants(withBuildType("release"))`),
  task and report names are `stableRelease`/`previewRelease` (Licensee's real `licenseeAndroid<Variant>` and
  `android<Variant>/` paths), and an `fdroid` flavour is covered automatically.
- **Ban patterns:** X-05 and X-12 name exact artifacts (K-04 and Ktor's `ktor-serialization(-jvm)` no longer match);
  the matcher escapes the whole pattern; `BannedPatternsTest` runs fixtures of required and banned coordinates.
- **R-24 packaging** (two reports): `ndk.abiFilters` removed; R-24 points to 13 §8.1's single block.
- **iOS actuals:** every `expect` gets its iOS actual in the same PR, and "iOS klib compiles" is in W-01–W-03's tests.
- **Coil in app modules:** decided that no app module declares Coil; the kit exposes no Coil type (`KitImageLoader`),
  recorded in §12.2, R12-4 and §14 item 13.
- **`LocaleFormats`:** skeleton enum for the ten skeletons in use, `percent`, fixed date and number patterns, a time-zone
  parameter, en and ar vectors for each.
- **Verification metadata:** `resolveAllDependencies` in every project, two runs (normal and `-Poc.ios=true`),
  trusted `-sources`/`-javadoc`, same commands in every dependency PR.
- **Terminal port:** `write(String)` added; `RemoteSessionClient` documented; the port moved to its own
  `:thirdparty:termux-session-port` so `:android:terminal` never depends on the view; `ModuleRules` and
  `ExternalRules` updated.
- **AppCompat fallback in ADR §4 row 6:** recorded in R12-3 and §14 item 6 with the amended wording.
- **AppUpdater** (two reports): R-26 and W-12 reduced to dependency facts and pointed to 16 §8 (cadence, lineage and
  install-source gate, asset order, module split); "refused after download, before install"; apksig added as a
  build-logic row (N-17). While checking B-BUILD-010/011, found that 16 §8.1's `releases/latest` does not match
  B-BUILD-011 (list, highest valid build); recorded as §14 item 19.
- **FactsBook device check** (two reports): R-17 defaults to the platform path (API 30+ `BiometricPrompt`, API 26–29
  `KeyguardManager`), with androidx.biometric in a dedicated `FragmentActivity` as the named fallback (R12-9).
- **Analytics** (two reports): R12-1 is recorded as decided; new W-20 for the analytics core per 16 §5; X-01 and X-04
  say vendor SDKs stay banned while our own HTTP senders are allowed; R12-1 removed from the owner's lines.
- **Vendored POM:** `.gitignore` ignores only `*.aar`; `ThirdPartyHashTest` checks `git ls-files` lists the POM.
- **Size budget:** "Today" uses the published split APK (≈ 36–37 MB adds up); one CameraX figure; the native dex is
  marked unmeasured; the absolute gate is left to 13 with a ratchet request.
- **`RegistryRowTest`:** a machine-readable `native/docs/dependencies.toml` keyed by catalog alias.
- **Section 10 reconciliation:** adopted M7 (R-13 OWN, W-19 `XmlPull`, N-39 and R12-5 withdrawn, X-38), M13
  (`qrose-encoder-matrix` in `:ui:kit-content` and `:engine:media`), M14 (Wi-Fi share in `:android:media`/`:engine:media`,
  slice 5, now as a binding of W-11), `zoomable-image-coil3` dropped, ZXing core 3.5.4 test-only (N-86, X-37),
  "scan from a picked picture" removed (D10-3); also aligned W-09/W-10 names and homes with 10. Tally is now
  13/15/5/8/2.
- **C-06 split:** `libaiteam_sandbox.so` ships at the switch (11 D11-1) with its manifest hash, verify task, strip
  exclusion, `BUNDLED.toml` entry and licence; §9.3, §11 and §12.3 updated; §14 item 14 flags 13 and 16.
- **F-Droid view:** new §7.1 table (per row: same, absent or source-built, with 16 §9.1 pointers), the per-variant
  ban X-39, `ManifestGoogleRefsTest` for the Photo Picker backport entry, and owner line 3 reworded to what the checks
  cover.
- **R12-8:** default is now 16's single immutable `third-party-sources-1` release; tarballs are never attached to app
  releases.
- **13 §8.7 bans:** X-32 kotlin-reflect, X-33 guava, X-34 androidx.pdf, X-35 core-splashscreen, X-36 Media3; N-95
  says Media3 is banned until a feature needs it.
- **lifecycle-process:** new N-62, declared only in `:android:platform`.
- **CMP fallback:** stated that 1.12.2 pairs with material3 1.12.0-alpha03, which fails D12; the switch waits unless
  the owner amends D12.
- **WebView:** exact settings, lifecycle clears and the `PreviewNavigationPolicy` cases from today's code.
- **Haze:** LATER row N-98 for the approved post-switch glass trial; X-21 scoped to "until then".
- **Voice-engine licences:** ONNX Runtime 1.28.2 and sherpa-onnx third-party entries in `BUNDLED.toml`, the Eigen
  MPL-2.0 sign-off in §8.1, texts in `LICENSES/`.
- **Downloaded components:** `DOWNLOADED.toml` (C-17) read by W-18, and update group G8 moved only by app releases.
- **`ExternalRules`:** the termux entry admits the two `:thirdparty:*` modules; `io.ktor:ktor-server-cio` and
  `com.networknt` are confined to `:tools:contracts`; first-match ordering stated.
- **Instrumentation runners:** only the `PhoneEngine*` (sandbox part) and `Builtin*Acceptance` runners carry over; BD9
  and BB8 are rewritten; accessibility checks get N-87.
- **`app_flutter`:** the migration reads `File(dataDir, "app_flutter")` with an existence check; `getDir("flutter"`
  is banned (X-30); a fresh-install test asserts no folder is created.

Rejected or changed from the suggested fix, and why:
- **Locale config (hand-written `locales_config.xml`):** rejected. 06 §13.6 item 4 owns this and already answers the
  concern: `:android:os-strings` puts a `strings_os.xml` in all seven `values-*` folders, `resources.properties` is
  set, and `LocaleConfigTest` fails unless the generated config lists exactly the seven tags. Android's docs say a
  hand-written file and `generateLocaleConfig` together fail the build. R-02 now points there; §14 item 10 asks 03 to
  drop its file.
- **androidx.exifinterface 1.4.2 row:** not added. Section 10 has since switched to the platform
  `android.media.ExifInterface(InputStream)` for API 26–27 (10 §2.1), matching R-18, so there is no conflict left.
- **A new `oc.jvm.library` module for the HTTP listener now:** not adopted as the default. The implementation sits in
  `:android:platform` and moves unchanged when desktop work starts (the ADR's pattern for JVM code, and 10 §13.3's own
  plan), which avoids an ADR module-list change today; recorded as R12-11 with the alternative.
- **GA4 Measurement Protocol as an allowed sender:** not listed. 16 §5.5 owns the sender list and does not offer
  Firebase/GA4; the registry only states that no sender may link a library.
- **"Route the stale 02 G7, row 17 and NG15 wording":** 02 already follows the decision (G7, row 17, NG15, S02-5); the
  remaining stale places are ADR §9.6 (already routed by 16 CR16-1) and 15 C15-9 / S4-ANALYTICS (§14 item 7).
- **Coil in `:androidApp`/`:desktopApp`:** of the two offered options, chose "the kit owns the loader" over "allow Coil
  in the app modules", because 10 §9.1 already builds the loader in the kit and 03 forbids `api` leaks.
- **`PathSensitivity.NONE` for the reports:** used `NAME_ONLY`, because the file name carries the variant the test
  reports.
