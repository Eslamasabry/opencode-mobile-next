# Native switch upgrade gate, 2026-11-08

**Verdict: FAIL** (22 of 29 checks passed, 3 not run)

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `flutter-2.1.4+62-testkey.apk` | `native-switch-release-4c012f945-universal-testkey.apk` |
| Package | `io.github.eslamasabry.opencode_mobile` | `io.github.eslamasabry.opencode_mobile` |
| Version | 2.1.4 (62) | 3.0.0 (10010) |
| APK SHA-256 | `037bd3e6d866d17894b260e39d2d9e3067d9a0a4b8bc5d6b5bd8a3b3da255f75` | `ff8b5ba02691f8acc262be4301b623810433f8b2bda0ddcb70ce7376a8703f4c` |
| Signer SHA-256 | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` |

Expected signer: `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C`

## Device

- Emulator `emulator-5584`, AVD `OCN_U`, held under the emulator lock for the whole run.
- Seed: restored from snapshot `flutter-2.1.4-seeded` (Part A not repeated).

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
| signer-flutter | The flutter APK is signed by the expected key only | PASS | flutter-2.1.4+62-testkey.apk signed by 3796C2105095518A... |
| signer-native | The native APK is signed by the expected key only | PASS | native-switch-release-4c012f945-universal-testkey.apk signed by 3796C2105095518A... |
| same-signer | Both APKs carry the same signer | PASS |  |
| same-package | Both APKs are io.github.eslamasabry.opencode_mobile | PASS | io.github.eslamasabry.opencode_mobile / io.github.eslamasabry.opencode_mobile |
| version-order | The native versionCode is above the Flutter one | PASS | 62 -> 10010 |
| part-b | Part C passed on the Flutter app first (the baseline) | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| first-launch | A first launch killed mid-migration resumes | PASS |  |
| conversations | Conversations shown, no welcome screen and no first-run guide | PASS |  |
| settings | Language, text size, style and notification choices kept | PASS |  |
| servers | Servers listed and connect without re-typing passwords | PASS |  |
| chat-messages | A seeded chat opens and shows its messages | PASS |  |
| drafts | Draft, attachment, saved prompt, pins and always-allow rule kept; queued prompt sends once | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| send-prompt | A new prompt sends and its reply arrives | PASS |  |
| linux-ui | Built-in Linux, the project and its server there without reinstall | PASS |  |
| terminal | `pwd && ls` runs in the seeded project's terminal | PASS |  |
| voice-ready | Voice pack shows Ready | PASS |  |
| schedule | Scheduled prompt listed and still fires | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| old-files | Attachments from the old documents folder open | PASS |  |
| no-secret-in-log | No password or token in the log during the update | PASS |  |
| linux-inodes | Linux inode count unchanged | **FAIL** | ubuntu.ready True -> True; rootfs inodes 16141 -> 16143 |
| voice-files | Voice pack files kept, not downloaded again | PASS | 4 files, 375485787 bytes; identical |
| schedule-armed | Scheduled prompt still has an alarm armed | NOT RUN | app alarms 0 -> 1 (B-153: 2.1.4 armed no alarm before the update) |
| widgets | Placed widgets kept | NOT RUN | B-153: Part A placed no widget (place-widget does not place on API 35) |
| tiles | Quick Settings tiles kept | PASS | ['.PauseTileService', '.AttentionTileService'] -> ['.PauseTileService', '.AttentionTileService'] |
| shortcuts | Launcher shortcuts kept | NOT RUN | B-153: Part A left no shortcut (2.1.4 has no pin action) |
| channels | Every old notification channel kept with its importance | PASS | new after: opencode_builtin_server, opencode_coding_action, opencode_coding_action_quiet, opencode_coding_status |
| grants | Permission grants and app-ops identical | PASS | granted ['android.permission.CAMERA', 'android.permission.POST_NOTIFICATIONS', 'android.permission.RECORD_AUDIO'] |
| updated-in-place | Updated in place to the native version code | PASS | versionCode 62 -> 10010 |
| flow | E2E-39 ran to the end (assertions are judged one by one above) | PASS | E2E-39 PASSED |

## Screens

- `screens/native-appearance.png`: Appearance: the style and text size chosen on 2.1.4.
- `screens/native-chat.png`: The seeded conversation with its earlier messages.
- `screens/native-conversations.png`: The Conversations list right after the update, no welcome screen.
- `screens/native-linux.png`: This phone: built-in Linux and its server running.
- `screens/native-saved-prompts.png`: Saved prompts: the prompt saved for later on 2.1.4.
- `screens/native-send.png`: A prompt sent after the update and its reply.
- `screens/native-servers.png`: Servers: every saved server connected, no password asked.
- `screens/native-terminal.png`: The project's terminal after `pwd && ls`.
- `screens/native-voice.png`: Voice typing set up and ready, nothing downloaded again.
- `screens/failure-10.png`: a failed step's screenshot (see the notes below).

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10 run 4c, added by hand)

Run 4b's saved snapshot restored (Part A not repeated), harness c3c0cacfd (fixes from run 4b), the same 3.0.0
(10010) switch release from 4c012f945. Nothing touched by hand. This run checks the harness fixes; run 4b is the
clean reseed of record.

| Check | Run 4b | Run 4c | Why |
|---|---|---|---|
| part-b | FAIL | FAIL | Only S12 now: restored from the snapshot, 2.1.4 has already caught up its schedule, its `nextRunAt` still names that occurrence, and it does not run anything on the three-day jump. The terminal relaunch now passes. |
| drafts | FAIL | FAIL | Saved prompt now passes (+ tray). The one failure is the queued prompt count: FX-OC2-B's request log is not part of the snapshot, so run 4b's send is still counted (restore-mode limit, B-153). |
| voice-ready | FAIL | **PASS** | Harness fix. |
| schedule | FAIL | FAIL | Listed: Go to still does not open (native). Fires: **B-148 reproduced** (below). |
| linux-inodes | PASS | **FAIL** | 16141 -> 16143 after a snapshot restore, as on run 3; 0 on both fresh runs (4, 4b). |
| schedule-armed | NOT RUN | NOT RUN | `0 -> 1`: the before count was taken by the run's own (unfixed) parser copy; native kept one alarm armed. |
| everything else | | unchanged | first-launch, conversations, settings, servers, chat-messages, send-prompt, linux-ui, terminal, old-files, no-secret-in-log, voice-files, tiles, channels, grants, updated-in-place, flow PASS; widgets, shortcuts NOT RUN. |

**B-148 on the device.** The snapshot holds 2.1.4's settled claim for the 09:00 Nov 8 occurrence, taken at Nov 10
03:47 (run 4b's Part B jump), with `nextRunAt` still Nov 8. The native S12 jump went to Nov 11 04:40. The Nov 10
09:00 run fell due after the settled one and was owed; native instead moved the schedule to Nov 11 09:00
(`nextRunAt` 2026-11-11T05:00Z, alarm `origWhen` 2026-11-11 09:00, three workers SUCCESS at 04:40 with nothing
sent). That is the bug fixed on `native/fix-b148` (6b0ec4235). An APK with that fix has not been run on the gate yet.
