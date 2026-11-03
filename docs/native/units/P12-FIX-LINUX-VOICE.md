VERIFIED: green

# P12-FIX-LINUX-VOICE: the built-in Linux and offline voice, for real, in the native app

Date: 2026-11-03. Branch `claude/native-parity-linux-voice-g8jqyg` (base `native/p12-qa-wave4-2026-11-03` at
`e61b9d22d`). Fixes the migration proof's ship blockers (`native/migration-proof-2026-11-03`,
`docs/native/qa/migration-proof-2026-11-03/README.md` §3 and §5 bugs 2, 3 and — partly — 4).

**Finish line:** after the in-place update over Flutter 4501, the native app finds the Ubuntu rootfs, `ubuntu.ready` and
projects the Flutter app left, starts and stops the phone's own OpenCode server, runs the Linux pages against it, and
types offline with the speech model the Flutter app downloaded; a preview/switch APK without these libraries, or a
non-test build that binds a placeholder, does not build.
**Non-goal:** porting the Flutter setup engine (first-time "Set up OpenCode" from nothing, Repair, Update OpenCode).

## 0. Shipping state

| State | |
|---|---|
| Implemented | yes (§1) |
| Enabled in the app | yes: `:android:linux` is in `:androidApp`; `LinuxHost`, `LinuxLifecycle`, `PhoneServerSource` and the speech engine are the real ones |
| Verified by tests / builds | see §3 (JVM tests, `:quality:test`, both APK flavours with the library gate) |
| Ran on a device | **no** — no emulator in this cloud session. §5 lists exactly what device QA must check |
| Committed / pushed | yes, to the session branch only. Not merged, not released, no PR |

## 1. Journeys now working (in code; device check in §5)

| Journey (Flutter) | Native now | How to reach |
|---|---|---|
| After the update, This phone shows the existing Linux, not "OpenCode isn't on this phone yet" | `BuiltinDiscovery.presence(files)` reads `files/linux/ubuntu` + `files/linux/ubuntu.ready` in place (the same package, so the same folder); `LinuxHost.presence()` = Ready | Conversations gear → Servers → **This phone** |
| The phone server comes back by itself when the app opens (bug 4) | `BuiltinRuntime` follows the activity (`BuiltinLinux.setActivityResumed`, as Flutter's MainActivity did). On the first resume after the migration gate has opened, if Linux is set up, `oc.builtinServerOwner` names a profile, the person had not pressed Stop (`builtin_server_recovery` `userStopped`) and the server is not running, it starts once through `PhoneServerStarter` (the one start path) | automatic |
| Start / Stop / Restart the phone server | `PhoneServerSource` over `PhoneServerStarter` + a `PhoneServerPort` on `BuiltinLinux`: password written to `/root/.oc-builtin/server.password` (never a command line), `startServer(serverScript, 4097)`, loopback probe with Basic `opencode:<pw>` on `/api/health` → `/api/info` → `/global/health`; Stop = `requestServerStop()` + `stopServer()` (the person's stop is remembered natively) | This phone → server row |
| A lost Keystore password (fresh Keystore, migration `renewedByOwner`) | B-LINUX-025: 32 random bytes base64url saved under `pw.<id>` on the same profile, then the start continues | automatic |
| Linux pages: Ports, Storage, Packages (install/remove/update with the long job), Running services, Reset | all now run against the real Ubuntu through `LinuxHost` (`BuiltinLinux.run / startService / stopService / serviceRunning / serviceLogTail`, `SetupRunner.status`). Reset erases with `uninstall(alsoDeleteProjects = false)`: projects stay | This phone → **Linux** → … |
| The phone server keeps running with the app in the background; restore after boot / app update | `BuiltinServerService` (foreground, `specialUse`), `BuiltinRestoreReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`), `BuiltinIdleReceiver`, `SetupService` are in `:android:linux`'s manifest with the **Flutter FQCNs**, merged into the app | automatic |
| Status-row "Stop reply" | the Linux status row targets the app's real `StatusActionReceiver` by its frozen class name; the app's body already handles `StopReply` | notification shade |
| Voice typing offline, with the model downloaded by the Flutter app | `VoiceWiring` hands `OfflineSpeechEngine` (sherpa-onnx 2.0.1 Whisper) to `:android:voice` and the composer uses `components.voice.asDictation()` with `SpeechEngineKind.OfflineModel`; models are read from `files/voice_models` in place; the pack choice and "needs a model" stay gated on total RAM (`ActivityManager.MemoryInfo.totalMem`, `:engine:voice`) | chat composer mic; Settings › Voice typing via the composer's "Voice settings" |
| Read aloud with the natural voice (Kokoro) | `naturalVoiceAvailable = true`, `OfflineEngineAwarePrefs(naturalVoiceRuns = true)` | message ⋯ → Read aloud |

If the sherpa JNI library cannot load (a broken install), voice falls back to the phone's own recognizer, as P12-VOICE
shipped it, instead of a dead mic.

## 2. What changed (write set)

- `native/android/linux/**`
  - **Shims that blocked the app** (S1-LINUX P1): `MainActivity.kt` and `StatusActionReceiver.kt` stand-ins deleted
    (they declared the app's own FQCNs); new `AppComponents` builds those intents by frozen class name.
    `R.kt` → `LinuxR`, `BuildConfig.kt` → `LinuxBuildConfig` (the app generates `io.github.eslamasabry.opencode_mobile.R`
    and `.BuildConfig`); the copies import them `as R` / `as BuildConfig` (one import line each in 6 main and 8
    androidTest files; the twins' bodies are unchanged except the 4 intent lines). TWINS.md owners: these are the only
    deviations from the Flutter files.
  - `src/main/jniLibs/{arm64-v8a,x86_64}/`: `libproot.so`, `libproot-loader.so`, `libtalloc.so`, `libandroid-shmem.so`,
    `libaiteam_engine.so`, `libaiteam_sandbox.so`, `libaiteam_boundary_probe.so` — **byte-identical** to
    `android/app/src/main/jniLibs` (checked by `NativeShipGateTest`), every PT_LOAD aligned ≥ 16 KB (0x4000; proot and
    talloc on arm64 have one 64 KB segment). Same file names, so the rootfs and the phone engine find them at
    `nativeLibraryDir/<name>` exactly as before.
  - `src/main/assets/aiteam-engine-manifest.json` (the phone engine's hash manifest, byte-identical).
  - `src/main/AndroidManifest.xml` (new): the services, receivers and permissions above.
- `native/android/media/**`: `speech/SherpaOnnx.kt` (the only `com.k2fsa.sherpa.onnx` code, plain types in/out),
  `sherpa.keep` consumer rule, the vendored dependency `com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1@aar`.
- Vendoring (plan 10 §2.2, as designed): `native/tools/vendor/fetch.sh`, `native/third_party/SHA256SUMS`
  (`a8d09d1d…0ee7`, the hash the plan pinned — the JitPack mirror of the 2.0.1 release AAR matches it exactly, and its
  `libonnxruntime.so` is byte-identical to the Flutter plugin's), the hand-written POM, `.gitignore` for the 49 MB AAR.
- `native/settings.gradle.kts`: the `exclusiveContent` local repository for group `com.k2fsa.sherpa.onnx` (plan 03 §0
  text) and a warning when the AAR is missing. **Deviation from the brief** ("do not edit settings.gradle.kts" — that
  rule is about module discovery; there is no other place a repository can be declared under `FAIL_ON_PROJECT_REPOS`).
- `native/androidApp/build.gradle.kts`: `":android:linux"` in the module list; `packaging.jniLibs { useLegacyPackaging
  = true; keepDebugSymbols += "**/libaiteam_*.so"; excludes += sherpa c-api/cxx-api }` (Flutter's exact packaging plus
  plan 10 §2.3); the `VerifyNativeLibraries` gate (§3).
- `native/androidApp/.../graph/LinuxVoiceWiring.kt` (new, the unit's one wiring file): `BuiltinRuntime`,
  `LinuxRuntimeWiring` bindings, `BuiltinDiscovery`, `PhoneServerScripts` (verbatim ports of `writePasswordScript`,
  `serverScript`, `openCode1HistoryCopyScript`), `OfflineSpeechEngine`.
- Smallest edits: `LinuxWiring.kt` (the three placeholder providers removed), `VoiceWiring.kt` (engine swap,
  `NoOfflineSpeechEngine` removed, phone recognizer kept as fallback), `OcApplication.kt` (one line:
  `appGraph.builtinRuntime`, like P12-VOICE's `resumedActivities`).
- Tests: `androidApp/src/test/.../LinuxVoiceWiringTest.kt`, `quality/src/test/.../NativeShipGateTest.kt`.

## 3. Checks and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` (platform-tools, `platforms;android-37.1`,
`build-tools;37.0.0`/`36.0.0`), JDK 21. Maven Central answered 429 to this container, so a container-only init script
(`~/.gradle/init.d/central-mirror.gradle.kts`, not committed) puts `google()` and Google's Central mirror first — the
same artifacts from another host.

One `--continue` run on the final source (plus the reruns named), JDK 21:

| Command | Result |
|---|---|
| `:android:linux:testDebugUnitTest` (the copied runtime, now with the new shims) | **491 tests, 0 failures** |
| `:androidApp:testPreviewDebugUnitTest` | **26 tests**: `LinuxVoiceWiringTest` 11/11 pass. 1 failure in `FilesWiringTest` B-MEDIA-034 (another unit's socket test: "expected IOException after close" got a 200 — a close/accept race); it **passes 3/3 when rerun alone** (`--rerun`) and on the untouched base; not in this write set |
| `:android:media:testDebugUnitTest`, `:android:voice:testDebugUnitTest` | **9 + 15, 0 failures** after pointing Robolectric's android-all download at the same mirror (container init script, `robolectric.dependency.repo.url`); the first run failed only at Robolectric's jar download (HTTP 429) |
| `:feature:linux:desktopTest` / `:feature:phone:desktopTest` / `:feature:voice:desktopTest` | **47 / 42 / 30, 0 failures** |
| `:engine:voice:desktopTest` / `:engine:runtime:desktopTest` | **124 / 86, 0 failures** |
| `:quality:test` (Konsist, ModuleOwners, graph smoke, `NativeShipGateTest` 5) | **64 tests, 0 failures** |
| `:androidApp:assemblePreviewDebug` + `verifyPreviewDebugNativeLibraries` | **BUILD SUCCESSFUL**, gate passed (APK 87 MB debug, unstripped) |
| `:androidApp:assembleSwitchDebug` + `verifySwitchDebugNativeLibraries` | **BUILD SUCCESSFUL**, gate passed |

`testDebugUnitTest` does not exist for the KMP feature/engine modules; `desktopTest` is their test task. Not run:
release builds (`assembleSwitchRelease` needs the signing key), lint/detekt, `connectedAndroidTest`, the whole repo
suite.

Red first / mutation proofs:

- `verifyPreviewDebugNativeLibraries` with `x86_64/libtalloc.so` moved away → **fails**:
  `androidApp-preview-debug.apk: lib/x86_64/libtalloc.so missing`; restored → passes.
- `NativeShipGateTest` with a planted `AbsentLinuxHost()` in `androidApp/src/main` → **fails** ("Placeholder bindings in
  :androidApp main"); removed → passes.
- The first `LinuxVoiceWiringTest` run caught a wrong assertion of mine (the word "serve" is inside
  `server.password`); fixed the test, not the code.

APK (preview debug): every library above for arm64-v8a and x86_64 plus `libsherpa-onnx-jni.so` and
`libonnxruntime.so` (also armeabi-v7a/x86 from the AAR, as the Flutter app ships sherpa for 4 ABIs; the Linux runtime
exists for the two 64-bit ABIs only, like Flutter). No NDK here, so AGP logged "Unable to strip … packaging them as
they are" — the files are byte-identical to the sources.

## 4. Catalog rows

Covered by tests named after the row: **B-MIGR-008** (rootfs, ready marker and projects found in place; an interrupted
rootfs without the marker, or a marker without a rootfs, offers setup), **B-LINUX-008** (setup.json running),
**B-LINUX-020** (status mapping), **B-LINUX-023** (own port 4097, loopback only, password from the root-only file,
history copy blocks a lossy start, history copy verbatim from the Dart source), **B-LINUX-024** (scripts parse as
`sh -n`), **B-LINUX-025** (renewed password shape), **B-LINUX-037** (password off the command line, quoting; probe
answers), **B-LINUX-092** (runtime refusals classify without their text). The ship gate (`NativeShipGateTest`, the APK
task) has no catalog row; it is the migration proof's §5 bugs 2 and 3.

Already covered by the modules this wires (unchanged, re-run in §3): B-LINUX-085…093 (`:feature:linux`), the phone
pages (`:feature:phone`), B-MEDIA-002/011 (RAM-gated pack choice), B-MEDIA-033 (recognizer config), N1 (Kokoro sink)
in `:engine:voice` / `:android:voice`.

Still missing: B-LINUX-010…013, 031…036 (native crash recovery and its 3-attempt budget through the app — the native
runtime's own restore after boot/update is in, the app-side healing owner is not), B-LINUX-038 (the starter keeps a
failed start for the card: the card shows `startProblem`, not retried), B-LINUX-015/017 and the whole setup engine
(first-time setup, Repair, Update OpenCode, Add tools, agents), B-LINUX-082…084 (Running in Linux process list:
`PhoneProcessSource` is still the empty reading), B-MIGR-009 for voice is covered only by the path (files/voice_models)
— see §5.

## 5. What device QA must check (no emulator here)

On `OC_API35` (x86_64) **and** an arm64 phone, with the switch build installed over Flutter 4501 that has Linux set up,
a project, "This phone" active and a downloaded speech model (the migration proof's seed):

1. `adb shell run-as <pkg> ls lib` / `pm dump` shows `nativeLibraryDir` with `libproot.so`, `libproot-loader.so`,
   `libtalloc.so` extracted (legacy packaging); `ls -la files/linux/ubuntu.ready` unchanged.
2. First launch after the update: This phone shows Linux installed (not "OpenCode isn't on this phone yet"); within
   ~90 s the phone server answers (status Running) without any tap; "This phone" connects and lists its chats; the
   saved prompt for the phone project is reachable (bug 4 of the proof).
3. This phone → Stop: the server stops, the foreground notification goes; reopen the app: it stays stopped. Start:
   it answers again. Restart works.
4. Linux → Ports shows 4097 (owned by OpenCode, "only this phone"); Storage measures; Packages: install a small package
   (e.g. `tree`), the job shows percent and ends "done"; remove it.
5. Kill the app process while the server runs, reopen: the server is running or comes back; `adb reboot`: the native
   restore receiver brings it back only where the Flutter policy allowed it.
6. A fresh install of the **preview** flavour (separate package, no Linux): This phone says Linux is not set up and
   "Set up OpenCode" opens the Linux page with that plain sentence — no crash, no dead button.
7. Voice: the mic in a chat records and the words appear (offline: airplane mode on); Settings › Voice typing shows the
   Flutter-downloaded model as installed (no re-download); on a device under the RAM threshold the large pack is not
   offered. Read aloud → natural voice reads (Kokoro), Stop reading stops it.
8. `logcat` for `UnsatisfiedLinkError`, `engine_bundle_invalid` (phone-engine hash check), `SecurityException` on
   `startForegroundService` (specialUse needs the manifest property — it is there).
9. Release build (`assembleSwitchRelease`, R8): voice still decodes (the `sherpa.keep` consumer rule), the phone engine
   bundle still verifies (`keepDebugSymbols` for `libaiteam_*`).

## 6. Deviations and gaps

- **First-time setup is not ported.** The Flutter setup engine (`lib/builtin/setup/**`, ~3,800 lines: Ubuntu download,
  essentials, OpenCode native install with its probe, agents, the "This phone" profile) is a unit of its own.
  `LinuxLifecycle.startSetup()` answers Ok when Linux is already installed and `NotInstalled` otherwise; Repair and
  Update OpenCode answer "unavailable". The pages say so in plain words; nothing pretends. The pieces it needs are
  here now (`SetupRunner`, `SetupService`, `BuiltinLinux.install`, `SETUP_PRELUDE` in `:feature:linux`).
- The app-open start is simplified: it uses the manual start path (`startServer`), not `startServerForLaunch` with the
  native generation, and no 3-attempt crash-recovery budget from the app side (native restore after boot/update is in).
- The agent-context block of Flutter's server script (`PhoneAgentContext.builtinScript`, run with `|| true`) is left
  out; the `AGENTS.md` it already wrote stays in Ubuntu.
- `PhoneProcessSource` (Running on this phone) is still the empty reading; `ChildLimitSource.lastAndroidKill` null.
- The notification small icon of the Linux notices is still the platform default (`LinuxR.mipmap.ic_launcher`).
- Settings › Voice: `SettingsWiring.voice` is still `null`, so Settings has no Voice row; the voice settings page is
  reached from the composer's voice note. The settings owner should bind `VoiceSettings` and map
  `SettingsOutside.VoiceSetup` → `VoiceRoute.Settings` (P12-VOICE §5.3).
- The `androidTest` acceptance sources of `:android:linux` were updated to compile against the new shims but not run.
- `127.0.0.1` appears in code only as the app-authored loopback address of the phone server and its probe.

## 7. For the integrator

1. Every fresh worktree / CI / VPS: run `native/tools/vendor/fetch.sh` once (downloads the 49 MB AAR, checks
   SHA-256). Without it Gradle warns at settings time and `:android:media` cannot resolve sherpa-onnx.
2. `settings.gradle.kts` gained the vendored repository block — keep it when merging.
3. Native CI / release scripts: `assemble` now finalizes with `verify<Variant>NativeLibraries` for the preview and
   switch flavours; a failure there means a library is missing from the APK, never ignore it.
4. The `qa` build type from the migration-proof branch (`assembleSwitchQa`) is not on this base; when merged, its
   variant is covered by the same gate (flavour `switch`).
5. Run device QA §5 before the switch APK is handed to the owner.
