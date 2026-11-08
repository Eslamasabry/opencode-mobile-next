# Native switch upgrade gate, 2026-11-08

**Verdict: FAIL** (23 of 29 checks passed, 3 not run)

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `flutter-2.1.4+62-testkey.apk` | `native-switch-release-a3034eba0-universal-testkey.apk` |
| Package | `io.github.eslamasabry.opencode_mobile` | `io.github.eslamasabry.opencode_mobile` |
| Version | 2.1.4 (62) | 3.0.0 (10010) |
| APK SHA-256 | `037bd3e6d866d17894b260e39d2d9e3067d9a0a4b8bc5d6b5bd8a3b3da255f75` | `8ff6a1b044eb0e57342bf4e338b1700eefb01ae90b541afd39c3400ebd232de0` |
| Signer SHA-256 | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` |

Expected signer: `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C`

## Device

- Emulator `emulator-5584`, AVD `OCN_U`, held under the emulator lock for the whole run.
- Seed: Part A ran on a wiped AVD.

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
| signer-flutter | The flutter APK is signed by the expected key only | PASS | flutter-2.1.4+62-testkey.apk signed by 3796C2105095518A... |
| signer-native | The native APK is signed by the expected key only | PASS | native-switch-release-a3034eba0-universal-testkey.apk signed by 3796C2105095518A... |
| same-signer | Both APKs carry the same signer | PASS |  |
| same-package | Both APKs are io.github.eslamasabry.opencode_mobile | PASS | io.github.eslamasabry.opencode_mobile / io.github.eslamasabry.opencode_mobile |
| version-order | The native versionCode is above the Flutter one | PASS | 62 -> 10010 |
| part-b | Part C passed on the Flutter app first (the baseline) | PASS |  |
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

## Run notes (E00-S10 run 5, closing gate, added by hand)

Switch release 3.0.0 (10010) built on the VPS from `native/screens-merge34` a3034eba0 (B-148 and B-178 merged), the
universal split signed with the throwaway test key; harness `native/e2e-harness-2` 46e3494fd (3-day clock jump);
`--reseed` from a wiped OCN_U at 4 GB RAM, `--skip-resource-checks` (another agent's Gradle on the PC). Nothing was
touched by hand during the gate. The `flutter-2.1.4-seeded` snapshot was saved at 07:16 (snapshot.pb, ram.bin
4.05 GB). Storage2 had 11.3 GB free at the start after I removed my own run artifacts only (the merged fix-b148
worktree, the old snapshot and OCN_U's userdata, which `--reseed` wipes).

### The 29 checks against run 4b

| Check | Run 4b | Run 5 | Why |
|---|---|---|---|
| part-b | FAIL | **PASS** | The whole 2.1.4 baseline passed, scheduled prompt included. |
| drafts | FAIL | FAIL | Saved prompt and always-allow rule pass. The queued prompt for FX-OC2-B was **not sent** within 90 s after the host came back (count 0). It reproduced by hand on the same snapshot: 6 minutes with the server reachable and the app polling it, the Servers row still "1 prompt waiting to send · Not connected". Run 4b sent it once. B-146 reopened. |
| voice-ready | FAIL | **PASS** | Harness fix (tap the row below the search field). |
| schedule | FAIL | FAIL | Listed: Go to opens now (B-178 fixed) but has no "Schedule a prompt" (Details, Conversation note, Files, …): B-163's entry is not offered for this OpenCode 2 chat. Fires: harness. Both apps moved three days: 2.1.4 caught up the 09:00 run at T+3d 07:14 and native looked at T+3d 07:27, before the next 09:00, so it rightly owed nothing; the B-148 fix moved the schedule to that next 09:00. |
| linux-inodes | PASS | **FAIL** | rootfs 16141 -> 16143 on a fresh reseed (0 on runs 4 and 4b, +2 on runs 3 and 4c): an occasional +2, not tied to snapshots. |
| schedule-armed | NOT RUN | NOT RUN | `0 -> 0`: install-native force-stops 2.1.4 before reading, and force-stop cancels its alarm. Fixed (f19b653be). |
| widgets, shortcuts | NOT RUN | NOT RUN | B-153. |
| first-launch, conversations, settings, servers, chat-messages, send-prompt, linux-ui, terminal, old-files, no-secret-in-log, voice-files, tiles, channels, grants, updated-in-place, flow and the 5 APK gates | PASS | PASS | — |

**23 of 29 pass, 3 fail (drafts, schedule, linux-inodes), 3 not run.**

### B-148 on the device (by hand, same snapshot, a3034eba0)

After the gate I restored the saved snapshot, installed the same APK and moved the clock five days (adb `date`).
Within 10 s FX-OC2 received the owed catch-up exactly once (`e2e:echo daily upgrade` count 1 -> 2, still 2 after
the wait); the schedule records one new run (scheduled Nov 11 09:00, started Nov 13 07:46, ran) and the next run
Nov 13 09:00. The 4c012f945 build, without the fix, dropped that run (run 4c). B-148 is fixed.

### Harness change after this run

S12 now moves 2.1.4 one day and native three (f19b653be), so native always has a run it owes; alarms are counted
before install-native's force-stop. Not yet run on the gate.
