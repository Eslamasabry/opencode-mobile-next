# Slice 0: device proofs P6, P3, P5 and start-up (2026-11-01)

Emulators only. Nothing ran on the owner's phone. Evidence is under `docs/native/slice0/device/`.

| Proof | Verdict | Branch |
|---|---|---|
| P6 language | **Met on API 35** (screen + notification switch at runtime, Arabic plurals right). API 30 path not run. | `native/proof-p6-2026-11-01` |
| P3 streaming | **History stream met, long reply not met** (emulator, indicative only) | `native/proof-p3-2026-11-01` |
| Start-up | **Measured**: 305 ms → 246 ms → 234 ms (none / library profiles / app baseline profile) | `native/proof-p3-2026-11-01` |
| P5 install over Flutter | **Partly met**: prefs adopted, secret decrypted, rootfs and prefs bytes untouched, tiles survive. Widgets, pinned shortcut and channel settings **not proven**. Manifest shortcuts **lost**. | `native/proof-p5-2026-11-01` (this report) |

Emulator numbers are **indicative only**. `emulator-5584` is `OC_Launch_Demo` (API 35, x86_64, 838×1860, 60 Hz),
rendering through ANGLE on the host. P5 ran on `OC_API37` (API 37, google_apis, rootable) as `emulator-5586`, because
`emulator-5584` was holding the real app. The owner's phone was attached all day; every `adb` command carried `-s`.

## Toolchain and a fix found on the way

- Gradle on JDK 21.0.13, `-Xmx2g`, 2 workers. AGP 9.3.3, Kotlin 2.4.21, CMP 1.13.0-beta01, benchmark 1.5.0.
- **`native/m0-2026-11-01` at `45cf36eb` does not compile `:ui:kit-chat`.** `TranscriptList.kt` imports
  `io.github.eslamasabry.oc.ui.design.MotionTokens` with `LONG_MS`/`SHORT_MS`. These were P1 placeholders; P7 replaced them
  with `ui.design.motion.MotionTokens` (`STANDARD` = 250, `QUICK` = 150). The P3 branch fixes the import with the same values.
  The P6 debug build did not show this because the app does not depend on kit-chat. The M0 tip `6b9a2b21` still has the
  bad import, and in the shared checkout `:engine:connection` also failed to compile against the newly merged `core`.
- The coordinator was merging into `/home/eslam/Storage/Code/cloud-launch` while this ran. P3 and P5 were therefore built in
  their own worktree, `/home/eslam/Storage2/wt/native-proofs`.

## P6: per-app language (Android 13+), screen and notification, Arabic plurals

**Wiring** (`native/proof-p6-2026-11-01`, commit `c120791f`):
- `res/xml/locales_config.xml` (en, ar) and `android:localeConfig` in the app manifest.
- A debug-only `LanguageProofActivity`. It shows CMP strings from two files in the same `values` folder
  (`strings_chats.xml` and a new `strings_chat_pages.xml`) and the real converted plural `e7WorkspaceFileCount` for
  0, 1, 2, 3, 11 and 100.
- The activity posts a notification from Android resources (`strings_os.xml`, the OS-surface path) with the converted
  plural `notifStatusNeedsYou`.
- Its buttons switch the language through `LocaleManager.setApplicationLocales`.
- All plural text was copied verbatim from the arb2xml output.

**Run on emulator-5584 (API 35):** `pm grant … POST_NOTIFICATIONS`, then
`am start -n io.github.eslamasabry.opencode_mobile.native/io.github.eslamasabry.opencode_mobile.LanguageProofActivity`.

| Step | Screen | Notification (`dumpsys notification --noredact`) |
|---|---|---|
| en | "Conversations"; 0 files, 1 file, 2 files … (`p6-screen-en.png`) | "Reconnecting" / "0 need you · 1 needs you · …" |
| tap العربية (LocaleManager, in-app) | "المحادثات", right to left; 0 لا توجد ملفات · 1 ملف واحد · 2 ملفان · 3 ملفات · 11 ملفًا · 100 ملف (`p6-screen-ar.png`) | "إعادة الاتصال" / "٠ طلب ينتظرك · طلب واحد ينتظرك · طلبان ينتظرانك · ٣ طلبات تنتظرك · ١١ طلبًا ينتظرك · ١٠٠ طلب ينتظرك" (`p6-notification-ar.png`) |
| `cmd locale set-app-locales … --locales en` (the Settings path) | back to English | back to English |

- The process stayed the same (pid 9518): only the activity was recreated.
- The Arabic categories are correct: zero, one, two, few, many and other for 0, 1, 2, 3, 11 and 100.

**Finding (needs a decision):** the two surfaces write Arabic numbers differently.
- Android `getQuantityString` formats numbers with Arabic-Indic digits (٣, ١١).
- CMP `pluralStringResource` inserts them with `toString()` and Western digits (3, 11).
- A screen and its notification would therefore disagree. The plan's `NumberArgKeys` / locale-formatted arguments (06 §13.4)
  should cover every `%d`, not only the 35 `decimalPattern` keys, or `strings_os` should get pre-formatted strings.

**Not run:** API 30 (`attachBaseContext` path; no API 30 AVD here); the pt count-0 English-fallback case; widgets.

## P3: release Macrobenchmark of the stream

**Wiring** (`native/proof-p3-2026-11-01`, commit `a62a9a9f`):
- A `benchmark` build type: release + R8 (`proguard-android-optimize`, `-dontobfuscate`), `<profileable shell>`, debug
  key, `largeHeap` (the harness builds the 12 MB fixture in memory and ran out of the 192 MB default heap).
- `StreamProofActivity` (benchmark source set only) feeds the real `TranscriptEngine` into `:feature:chat`
  `ChatTranscript` (the kit-chat `LazyColumn` with the draw-phase fade, `KitMotionLevel.Full`).
- A self-instrumenting `:benchmark` test module runs `FrameTimingMetric`, `CompilationMode.Full`, 3 iterations and
  `androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY,UNLOCKED`.

Command (installed APKs, run by hand to keep it on one device):
`adb -s emulator-5584 shell am instrument -w -e class io.github.eslamasabry.oc.benchmark.StreamBenchmark#<test> -e androidx.benchmark.suppressErrors EMULATOR,LOW-BATTERY,UNLOCKED io.github.eslamasabry.oc.benchmark/androidx.test.runner.AndroidJUnitRunner`

| Scenario | Frames / iter | CPU ms P50 / P90 / P95 / P99 | Overrun ms P50 / P95 / P99 | Frames over budget |
|---|---|---|---|---|
| `history5000`: the recorded 5,004-part OC2 stream (59,779 events). Everything older than the last 20 s is loaded as history; the last 20 s (924 events) streams live at its recorded times | 1,123 | 9.3 / 13.9 / 15.8 / 20.1 | −6.6 / 0.1 / 5.4 | 2.5 %, 5.0 %, 8.5 % |
| `stream6000`: the 6,000-word reply with the 400-line open fence, about 85 s | ~5,160 | 24.1 / 29.4 / 30.9 / 34.3 | +8.2 / 16.0 / 19.9 | 67.6 %, 45.4 %, 87.4 % |

- "Over budget" means `frameOverrunMs > 0` at 60 Hz. Raw data: `device/p3/*-benchmarkData.json`. The Perfetto traces were
  kept on the PC (about 150 MB), not committed.
- **Verdict.** On the emulator, the 5,000-part history with live tail stays mostly in budget (P95 overrun ≈ 0), but
  2.5–8.5 % of frames still miss it, so ADR §11's "no frame over budget" is not met even here. The 6,000-word reply fails
  clearly.
- **The cost is not the open fence alone.** Median CPU per frame by tenth of the run:
  - iteration 0: 11 10 11 17 26 27 27 26 27 27
  - iteration 1: 10 11 11 18 11 11 25 27 27 27
  - iteration 2: 11 24 24 25 27 27 27 26 27 28

  The time per frame grows as the reply grows and stays high after the fence closes. This points at work that grows with
  the length of the newest turn: P3 open problems 3 (newest-turn rebuild is O(parts in the turn)) and 6 (`RevealRegistry`
  never forgets), more than at the fence. Settled-fence chunks alone will probably not fix it.
- Treat these numbers as relative. The emulator renders in software through ANGLE, and the PC load average was 2.5–50
  during the runs, with other agents building. The gate stays a release Macrobenchmark on the NX721J.

## Start-up (release, R8, cold, 10 iterations, `StartupTimingMetric`)

| Build | timeToInitialDisplay median (min–max) |
|---|---|
| `CompilationMode.None()` (no AOT) | 305.2 ms (293.1–367.4) |
| `Partial(Require)`, only the library baseline profiles (Compose and the rest) | 245.6 ms (231.0–265.9) |
| `Partial(Require)`, plus the app's generated start-up profile (`androidApp/src/main/baseline-prof.txt`, 5,957 rules; `BaselineProfileRule`, 44 s) | 234.4 ms (217.1–266.3) |

- The app profile saves about 11 ms (5 %) over the library profiles, and about 71 ms (23 %) over no compilation.
- `timeToFullDisplay` is not reported because the app does not call `reportFullyDrawn` yet.
- These are emulator numbers.

## P5: install the native skeleton over Flutter 2.1.0+57

**Builds.**
- **Flutter.** `65e3f70a3` (the commit plan 05 names; pubspec `2.1.0+57`), built with the pinned Flutter 3.47.1:
  `flutter build apk --release --build-number=57 --target-platform android-x64`. Signer
  `1DE5BF08…D60C` (the local key; its `storeFile` is this machine's `~/.android/debug.keystore`).
  - **Note:** the tag `v2.1.0+57` points to `e44b581aa`, and `65e3f70a3` is a later dev commit that is not an ancestor
    of it. The plan should name which one the owner's phone runs.
  - x86_64 only, not universal, to save build time.
- **Native.** `native/proof-p5-2026-11-01`: a `switch` flavour with the real package `io.github.eslamasabry.opencode_mobile`,
  versionCode 10000, the same signer (debug keystore = local key, checked with `apksigner`).
  - New library module `:android:surfaces`: the 7 widget providers and 2 tile services under their frozen Flutter FQCNs.
    These are stubs.
  - New library module `:android:migration`: the P5 reader library compiled in unchanged, `AndroidKeystoreUnwrapper`, and
    a proof runner. A `ContentProvider` starts the runner on its own thread at process start.

**Seed, on `OC_API37`.**
- `adb root`, install the Flutter APK, then add a server through the real UI with uiautomator: "Connect to my computer" →
  "Enter the address instead" → `http://10.0.2.2:4123`, "Allow HTTP", password `fixture-pass-1`, "Save anyway".
- That gave 10 `flutter.*` keys and one FSS entry `pw.<id>`, with markers in the namespaced config file.
- A fixture rootfs `files/linux/ubuntu/…` (a 1 MiB binary, a symlink, `ubuntu.ready`) was planted with the app's uid and
  SELinux context.
- Round 2 also added both QS tiles: `cmd statusbar add-tile …/.AttentionTileService`, `…/.PauseTileService`.

**Then:** `adb -s emulator-5586 install -r native-switch-10000.apk`, and launch.

| Check | Result | Evidence |
|---|---|---|
| Update accepted, same signer | versionCode 57 → 10000, `firstInstallTime` kept, signature `105d04ba` unchanged | `p5/package-*.txt` |
| Prefs adopted in place | `getSharedPreferences("FlutterSharedPreferences")` loads all 10 keys (7 String, 2 Boolean, 1 Long) in 0.6 ms; `flutter.oc.profiles` read | `p5/p5-proof.json` |
| One v11 secret decrypted | `pw.<id>`: ok, 14 chars, SHA-256 `33c51355…6465` = SHA-256 of `fixture-pass-1`; `keystoreAliasPresent=true`, `KeystoreAliasMissing` = 0; M2 62 ms (722 ms in run 2, right after a system restart under load) | both proof JSONs |
| Step 0 timed off the main thread | `onMainThread=false`; S0 byte backup of the 4 Flutter files 3.9 ms (61 ms in run 2); runner started 16 ms after process start | proof JSON |
| Rootfs untouched | inode, size, mtime and SHA-256 identical for every entry | `p5/rootfs-before.txt` = `rootfs-after.txt` |
| Prefs untouched | `shared_prefs` tar before and after: byte-identical | (diff run) |
| QS tiles survive | both still in `sysui_qs_tiles` after the update; SystemUI bound both native `TileService`s | `p5/tiles-*.txt` |
| Widget providers from a library module | `dumpsys appwidget` lists all 7 frozen FQCN providers under the app uid | (dumpsys, not committed) |
| First frame, no crash | cold `TotalTime` 975 ms; no app crash, no ANR | logcat |
| **Manifest shortcuts** | **Lost.** Before: `new_task` and `connect` (manifest shortcuts). After: none. The native APK has no `android.app.shortcuts` meta-data, so the component freeze must include `res/xml/shortcuts.xml`. A pinned shortcut to those ids would be disabled. | `p5/shortcuts-*.txt` |
| Placed widgets, pinned shortcut | **Not proven.** In this headless sandbox, `OC_API37` cannot read color buffers back (`hasReadColorBufferDma` assertion). `screencap`, SurfaceFlinger `RegionSampling` and `TaskSnapshotPersister` abort, and going Home restarts `system_server`, so the launcher could not be driven. Fix: a GPU-backed AVD (`-gpu host` needs a display or render node this sandbox does not have) or the real recipe on a desktop session. | — |
| Channel settings | **Not proven.** The Flutter app had created no channels before its first notification. | — |
| 64 MiB prefs under StrictMode (J8b), Room trigger | **Not run.** | — |

## What is left

1. Merge the kit-chat import fix into M0, and fix `:engine:connection` at the M0 tip.
2. P3: profile `stream6000` (newest-turn rebuild, `RevealRegistry`) before settling on fence chunks, then rerun on the NX721J.
3. P5: add `shortcuts.xml` (`new_task`, `connect`) to the frozen components. Redo widgets, pinned shortcut, channel and J8b
   on a GPU-backed AVD.
4. P6: run the API 30 path. Decide on digit formatting for `%d` arguments.

## State left behind

- `emulator-5584`: the real app (versionCode 4501) was never touched. The preview package `….native` now holds the P3
  benchmark build (same local-key signer); the benchmark test APK `io.github.eslamasabry.oc.benchmark` is installed too.
- `OC_API37`: booted with `-no-snapshot-save`; the test package was uninstalled and gesture navigation restored. The
  emulator was shut down and no qemu process for it remains.
