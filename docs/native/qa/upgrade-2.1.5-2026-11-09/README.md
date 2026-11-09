# Upgrade proof: Flutter 2.1.5 (4702) to native switch build, in place (2026-11-09)

**Verdict: PASS for the upgrade and for everything that can be shown without opening a conversation. BLOCKED, not
failed: opening any conversation in the native app kills this emulator (see "Blocked").** No migration bug was found,
so no code fix is on this branch.

## Setup
| What | Value |
|---|---|
| Flutter | `flutter-2.1.5-4702.apk`, versionCode 4702, signer SHA-256 `1DE5BF08...D60C` |
| Native | `:androidApp:assembleSwitchRelease` from branch `native/upgrade-2.1.5` at `97a71a3ff` (merge of `mobile-next/native/next` `4275356c3`, plus the 2.1.5 manifest commit `5ef65ef31`), zipalign, apksigner with `~/.android/debug.keystore`. versionCode 10010, signer `1DE5BF08...D60C`, identical to the Flutter APK (`apksigner verify --print-certs`). The old switch APK (built 16:16) was replaced. |
| Install | `adb install -r`, no uninstall; `firstInstallTime` unchanged; `exit-info` shows `PACKAGE UPDATED` for the Flutter process. |
| Emulator | AVD `OC_API35`, `-read-only`, port 5570, `-gpu swiftshader_indirect`, animation scales 1, started through `pc-heavy.sh`. Read-only means every emulator crash wiped the seed, so the seed was redone through the UI each time (`tools/seed-all.sh`, kept in the artifacts dir, not committed). |
| Servers | two local OpenCode 2 servers on 4311 and 4312, Basic auth, passwords typed in the Flutter UI (never printed). Stopped by PID at the end. |
| Seed (Flutter UI) | 3 servers (4311, 4312, built-in "This phone" with Ubuntu rootfs 1.1 GB and project `my-app`), 3 conversations, Light + Nord + Paper, glass Off, Arabic, usage and crash sharing both on ("Share both"), saved prompt `SAVED-UPG ...` and an unsent draft `DRAFT-UPG ...`, voice model "small fallback" (104 MB) downloaded. |

## Checks
| Check | Result | Evidence |
|---|---|---|
| Update accepted, same install, no uninstall | PASS | `n01-first-launch.png`, install log |
| First start clean: no crash, no ANR, no fatal in logcat, exit info | PASS | `n01-first-launch.png`; `migration-audit.json` (3 profiles, 3 secrets migrated, 0 flagged, 0 undecodable prefs) |
| Servers and passwords work without re-entry | PASS: both computer servers connect and list their chats; 0 secrets flagged, no re-enter sheet | `n02-servers-switcher.png`, `n03-work-box-connected.png`, `c2-conversations-4311.png` (seed) |
| Conversations and projects | PASS: the conversations of both servers are listed under their projects | `n03-work-box-connected.png` |
| Settings: theme, style, language | PASS: Light + Nord, Paper, Arabic all carried | `f07-appearance-set.png` (before), `n06-appearance.png` (after) |
| Stash and prompts | PASS on disk, UI not reachable: audit says `stashPrompts 1, drafts 1`; prefs diff shows no key removed (73 before, 79 after, 6 added by native). The privacy page shows "153 B of unsent work, 1 draft". The saved-prompt list lives in the composer and could not be opened (Blocked). | `n07-privacy.png`, `f11-draft-stash.png` (before), `migration-audit.json` |
| Analytics consent carried over | PASS: `telemetryConsentAsked`, `telemetry.usage`, `telemetry.crashes` all still true; crash-sharing switches show on | `n07-privacy.png`. The native privacy page shows only the crash rows in view, usage row not found by text. |
| Built-in Linux | PASS for install and server: rootfs and project files kept (the only differences are runtime files from the server starting: opencode db, `server.password`), the native app starts the phone server (`server listening on 127.0.0.1:4098`), "This phone" shows connected and offers a project picker and a new conversation. Sending a message to it was Blocked. | `n05-this-phone.png`, `n11-new-chat-phone.png` |
| Voice model | PARTIAL: the model is kept and listed as installed ("مثبّت"); the APK now contains `libsherpa-onnx-jni.so` and `libonnxruntime.so` (they were missing on 11-03). Actual dictation was not exercised (emulator microphone and the Blocked chat screen). | `n09-voice-models.png`, `f10-voice-model.png` |
| Kill during first native start | PASS: Flutter reinstalled with `-d` over the migrated data, native installed again, process force-stopped 1.2 s after launch, relaunch opens normally on "This phone", no crash. The migration itself takes under 1 s, so the kill most likely landed after it; kill points inside steps stay covered by the 11-03 qa build runs. | `k01-after-kill.png` |
| e2e seed manifest 2.1.4 to 2.1.5 | DONE (`5ef65ef31`, `native/tools/flutter-fixtures`) | commit |

## Blocked: opening a conversation crashes the emulator
Opening an existing conversation, or starting a new one, in the native app makes `qemu-system` segfault
("Failed to find ColorBuffer", "bad color buffer handle", host gfxstream). Reproduced 6 times: styles Paper and Solid,
glass Off, `swiftshader_indirect` and guest GPU. The conversation list, settings, servers and the new-conversation
start page do not crash it. The Flutter app's chats never crashed it. It is probably an emulator renderer bug triggered
by something the native chat screen draws, but it is not proven to be emulator-only; it needs one look on the phone
(owner's phone, not touched here) or another emulator image. Draft text, saved prompt, sending to the Linux server and
the transcript were therefore not seen on screen.

## Observations (not migration bugs)
- After `am force-stop` the first native start shows the banner "Android closed OpenCode Mobile a minute ago..."
  although the exit reason was USER REQUESTED / FORCE STOP. After a normal update (reason PACKAGE UPDATED) there is no banner.
- Disk: `/` and Storage2 were at 99 percent; freed old qcow2 overlays in `/tmp/android-eslam`, old CI downloads and `offload/old-test-apks`.
