# Native switch upgrade gate, 2026-11-07

**Verdict: FAIL** (21 of 29 checks passed, 3 not run)

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `flutter-2.1.4+62-testkey.apk` | `native-switch-release-5ff3ac113-testkey.apk` |
| Package | `io.github.eslamasabry.opencode_mobile` | `io.github.eslamasabry.opencode_mobile` |
| Version | 2.1.4 (62) | 3.0.0-dev (10004) |
| APK SHA-256 | `037bd3e6d866d17894b260e39d2d9e3067d9a0a4b8bc5d6b5bd8a3b3da255f75` | `69afc4bc4805dc0c90e9dd5a045b0ec08ca6aedccf4aa3c01cbd5f043cb6ad47` |
| Signer SHA-256 | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` |

Expected signer: `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C`

## Device

- Emulator `emulator-5584`, AVD `OCN_U`, held under the emulator lock for the whole run.
- Seed: restored from snapshot `flutter-2.1.4-seeded` (Part A not repeated).

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
| signer-flutter | The flutter APK is signed by the expected key only | PASS | flutter-2.1.4+62-testkey.apk signed by 3796C2105095518A... |
| signer-native | The native APK is signed by the expected key only | PASS | native-switch-release-5ff3ac113-testkey.apk signed by 3796C2105095518A... |
| same-signer | Both APKs carry the same signer | PASS |  |
| same-package | Both APKs are io.github.eslamasabry.opencode_mobile | PASS | io.github.eslamasabry.opencode_mobile / io.github.eslamasabry.opencode_mobile |
| version-order | The native versionCode is above the Flutter one | PASS | 62 -> 10004 |
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
| schedule-armed | Scheduled prompt still has an alarm armed | NOT RUN | app alarms 0 -> 0 (B-153: 2.1.4 armed no alarm before the update) |
| widgets | Placed widgets kept | NOT RUN | B-153: Part A placed no widget (place-widget does not place on API 35) |
| tiles | Quick Settings tiles kept | PASS | ['.PauseTileService', '.AttentionTileService'] -> ['.PauseTileService', '.AttentionTileService'] |
| shortcuts | Launcher shortcuts kept | NOT RUN | B-153: Part A left no shortcut (2.1.4 has no pin action) |
| channels | Every old notification channel kept with its importance | PASS | new after: opencode_builtin_server, opencode_coding_action, opencode_coding_action_quiet, opencode_coding_status |
| grants | Permission grants and app-ops identical | **FAIL** | granted ['android.permission.CAMERA', 'android.permission.POST_NOTIFICATIONS', 'android.permission.RECORD_AUDIO']; app-ops changed: MANAGE_EXTERNAL_STORAGE allow -> default |
| updated-in-place | Updated in place to the native version code | PASS | versionCode 62 -> 10004 |
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
- `screens/failure-10.png`: not a stop: the optional wait for an open terminal before New terminal (the terminal section then passed).

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10 run 3, added by hand)

Harness: `native/e2e-harness-2` at 887eccc28 (from 5ff3ac113): Part C walks the native screens where 2.1.4's path
does not exist (B-152), and the device-state checks Part A could not seed read NOT RUN instead of FAIL (B-153).
Native APK: `:androidApp:assembleSwitchRelease` at 5ff3ac113 on the VPS (JDK 21 now needed by the Metro plugin;
lintVital skipped), signed on the PC with the same throwaway test key as the Flutter APK. AVD `OCN_U`, booted from
the snapshot `flutter-2.1.4-seeded` (Part A not repeated), fixtures on OpenCode 2.1.1 with the snapshot's state,
`--seeded`. A first pass with the same build (run 3a) showed three more harness selector gaps (Finished work is a
checked row holding the words; server rows hold name and state as separate children); they were fixed and the
whole gate ran again: the table above is that second pass.

### What flipped against run 2 (b1c7513a0, 14 of 29)

| Check | Run 2 | Run 3 | Why |
|---|---|---|---|
| part-b | PASS | **FAIL** | Seed, not native: after the snapshot restore 2.1.4's daily schedule did not fire within 60 s of the 24 h clock jump (S12; every other Part C section passed on Flutter). Run 2 seeded from nothing. |
| settings | FAIL | PASS | Harness (B-152): Appearance chips show Soft and Smallest; Finished work is on. |
| servers | FAIL | PASS | Harness selector; FX-OC2 connected with no password prompt; names kept (B-149). |
| chat-messages | FAIL | PASS | Native fix (B-144): the draft "@notes.txt upgrade draft text" is restored. |
| drafts | FAIL | FAIL | Now only the always-allow rule: native shows no Always allowed actions for OpenCode 2 (new item). Draft, saved prompt (B-151), pins and the queued prompt pass; the queued prompt reached FX-OC2-B exactly once without connecting to it (B-146). |
| linux-ui | FAIL | PASS | Native fix (B-147): This phone reads "Everything is running" after the update. |
| terminal | FAIL | PASS | Harness (B-152): project chip > Terminal > New terminal; `ls` lists notes.txt (screens/native-terminal.png). |
| voice-ready | FAIL | PASS | Harness (B-152): Voice > Voice typing, High accuracy "Installed". |
| schedule | FAIL | FAIL | Listed: native links the Scheduled prompts list from nowhere (new item). Fires: did not fire on Flutter either after the restore (see part-b), so not attributable. |
| old-files | FAIL | PASS | Follows the restored draft (its notes.txt mention). Opening a local attachment is still not seeded (B-153). |
| linux-inodes | FAIL | FAIL | rootfs 16141 -> 16143 (run 2: +4); built-in Linux not reinstalled (B-147). |
| schedule-armed | FAIL | NOT RUN | No app alarm before the update (B-153). |
| widgets | FAIL | NOT RUN | Part A places no widget on API 35 (B-153). |
| shortcuts | FAIL | NOT RUN | 2.1.4 has no pin action (B-153). |
| channels | FAIL | PASS | Check now compares the old channels only (B-150); the four native additions are listed. |
| grants | FAIL | FAIL | MANAGE_EXTERNAL_STORAGE allow -> default (B-145, still open). |

Unchanged PASS: signer and version gates, first-launch (the killed first launch resumes, S10), conversations,
send-prompt, no-secret-in-log, voice-files, tiles, updated-in-place, flow.

### Still failing, and why

- **part-b**: seed limit, not the app (B-153). Re-seed from nothing, or arm the schedule just before saving the
  snapshot, to judge S12 again.
- **drafts**: native finding. 2.1.4 lists the always-allowed `ls` rule under Always allowed actions; native sets
  `savedPermissionList = false` for OpenCode 2 (Oc2Gateway), so neither settings search nor Permissions and
  automation shows it.
- **schedule**: native finding for "listed" (SchedulesRoute.List opens only from a schedule notification;
  ServersUi.openSchedules has no caller; the + tray's "Send later or repeat" makes a new schedule). "Fires" is not
  attributable in this run (B-148 stays open).
- **linux-inodes**: native finding (B-147), +2 inodes in the rootfs.
- **grants**: native finding (B-145).

Also seen on the walk, not judged by a check: settings search finds no "Text size", "Voice typing" (the row is
"Voice") or "Always allowed actions"; native Appearance offers no Shape (corners) choice for Soft, so the 2.1.4
Sharp tweak cannot be seen. Both are new backlog items.

### Screens looked at

`native-appearance.png` (Style Soft, Text & motion Smallest), `native-servers.png` (names kept, 4097 connected,
4098 "1 prompt waiting to send"), `native-saved-prompts.png` ("upgrade saved prompt"), `native-linux.png`
("Everything is running" after the killed first launch's "The app was closed while OpenCode was running"),
`native-voice.png` (High accuracy Installed), `native-terminal.png` (`pwd && ls` output with notes.txt),
`native-chat.png` (restored draft), `failure-10.png`; and from run 3a the same terminal and chat screens.
