# Native switch upgrade gate, 2026-11-07

**Verdict: FAIL** (14 of 29 checks passed)

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `flutter-2.1.4+62-testkey.apk` | `native-switch-release-b1c7513a0-testkey.apk` |
| Package | `io.github.eslamasabry.opencode_mobile` | `io.github.eslamasabry.opencode_mobile` |
| Version | 2.1.4 (62) | 3.0.0-dev (10004) |
| APK SHA-256 | `037bd3e6d866d17894b260e39d2d9e3067d9a0a4b8bc5d6b5bd8a3b3da255f75` | `3eab9d3fc13e725dde8307a70c1fc4b0de4e47d3a128a40d5dd4810b4ac030ce` |
| Signer SHA-256 | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` |

Expected signer: `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C`

## Device

- Emulator `emulator-5584`, AVD `OCN_U`, held under the emulator lock for the whole run.
- Seed: Part A ran on a wiped AVD.

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
| signer-flutter | The flutter APK is signed by the expected key only | PASS | flutter-2.1.4+62-testkey.apk signed by 3796C2105095518A... |
| signer-native | The native APK is signed by the expected key only | PASS | native-switch-release-b1c7513a0-testkey.apk signed by 3796C2105095518A... |
| same-signer | Both APKs carry the same signer | PASS |  |
| same-package | Both APKs are io.github.eslamasabry.opencode_mobile | PASS | io.github.eslamasabry.opencode_mobile / io.github.eslamasabry.opencode_mobile |
| version-order | The native versionCode is above the Flutter one | PASS | 62 -> 10004 |
| part-b | Part C passed on the Flutter app first (the baseline) | PASS |  |
| first-launch | A first launch killed mid-migration resumes | PASS |  |
| conversations | Conversations shown, no welcome screen and no first-run guide | PASS |  |
| settings | Language, text size, style and notification choices kept | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| servers | Servers listed and connect without re-typing passwords | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| chat-messages | A seeded chat opens and shows its messages | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| drafts | Draft, attachment, saved prompt, pins and always-allow rule kept; queued prompt sends once | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| send-prompt | A new prompt sends and its reply arrives | PASS |  |
| linux-ui | Built-in Linux, the project and its server there without reinstall | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| terminal | `pwd && ls` runs in the seeded project's terminal | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| voice-ready | Voice pack shows Ready | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| schedule | Scheduled prompt listed and still fires | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| old-files | Attachments from the old documents folder open | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| no-secret-in-log | No password or token in the log during the update | PASS |  |
| linux-inodes | Linux inode count unchanged | **FAIL** | ubuntu.ready True -> True; rootfs inodes 16141 -> 16145 |
| voice-files | Voice pack files kept, not downloaded again | PASS | 4 files, 375485787 bytes; identical |
| schedule-armed | Scheduled prompt still has an alarm armed | **FAIL** | app alarms 0 -> 0 |
| widgets | Placed widgets kept | **FAIL** | Part A placed no widget |
| tiles | Quick Settings tiles kept | PASS | ['.PauseTileService', '.AttentionTileService'] -> ['.PauseTileService', '.AttentionTileService'] |
| shortcuts | Launcher shortcuts kept | **FAIL** | Part A left no shortcut |
| channels | Notification channels and their importance identical | **FAIL** | new after: opencode_builtin_server, opencode_coding_action, opencode_coding_action_quiet, opencode_coding_status |
| grants | Permission grants and app-ops identical | **FAIL** | granted ['android.permission.CAMERA', 'android.permission.POST_NOTIFICATIONS', 'android.permission.RECORD_AUDIO']; app-ops changed: MANAGE_EXTERNAL_STORAGE allow -> default |
| updated-in-place | Updated in place to the native version code | PASS | versionCode 62 -> 10004 |
| flow | E2E-39 ran to the end (assertions are judged one by one above) | PASS | E2E-39 PASSED |

## Screens

- `screens/native-conversations.png`: The Conversations list right after the update, no welcome screen.
- `screens/native-send.png`: A prompt sent after the update and its reply.
- `screens/failure-3.png`: Where the flow stopped (step-991-runFlow).

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10 rerun, added by hand)

Branch `native/e2e-harness` at the harness commits listed in the backlog; native APK `assembleSwitchRelease` at
b1c7513a0 (compiles now, B-133), both APKs on a throwaway test key; fixtures on OpenCode 2.1.1 behind FX-CTL; AVD
`OCN_U` (android-35 google_apis, made like `native/e2e/avd/create.sh`).

Part A seeded Flutter 2.1.4 end to end and Part B (Part C on Flutter) passed every section. The native APK went
over it with `adb install -r`, its first launch was killed and relaunched (S10 PASS), and Part C ran every section
on native. The seeded snapshot `flutter-2.1.4-seeded` was saved before the install.

What a FAIL means here, per line (checked by hand on the same device after the run, `screens/manual-*`):

- Real native findings: the draft is not restored ("Draft not saved. Copy your text or retry.", empty composer,
  no notes.txt chip: chat-messages, drafts, old-files); All-files access is reset (`MANAGE_EXTERNAL_STORAGE` allow ->
  default, grants); the three saved computers all read "Computer" (Flutter: "Computer at 10.0.2.2:4097", ":4098",
  ":8443 (secure)"); the queued prompt for FX-OC2-B stays queued until the user connects to that server (then it
  is sent exactly once; native does not watch it by itself: drafts); the phone server is not running after the
  update ("This phone: Not connected" until "Connect to this server", then healthy on 2.1.1 with the rootfs kept:
  linux-ui, linux-inodes +4); the scheduled prompt has no alarm and did not fire on the clock jump (schedule);
  four new notification channels (channels).
- Harness, not the app: settings (native keeps Soft, Smallest and notifications on, `screens/manual-native-
  appearance.png`, but its settings search finds no "Text size"); servers (FX-OC2 is connected with no password
  prompt; the row text differs, selector fixed after the run); terminal, schedule listed and voice-ready (native has
  no "Files" behind the project chip and its settings search finds no "Voice typing"; the voice pack files are
  kept: voice-files PASS); widgets and shortcuts (Part A cannot place a widget or pin on 2.1.4); schedule-armed (0
  alarms before the update too).
- S13 (update offer) fails as the flow already predicted: the switch release does not read FX-GH.
