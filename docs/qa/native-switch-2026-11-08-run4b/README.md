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
- Seed: Part A ran on a wiped AVD.

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
| voice-ready | Voice pack shows Ready | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| schedule | Scheduled prompt listed and still fires | **FAIL** | failed on the device; see maestro.log and the failure screenshot |
| old-files | Attachments from the old documents folder open | PASS |  |
| no-secret-in-log | No password or token in the log during the update | PASS |  |
| linux-inodes | Linux inode count unchanged | PASS | ubuntu.ready True -> True; rootfs inodes 16141 -> 16141 |
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
- `screens/native-send.png`: A prompt sent after the update and its reply.
- `screens/native-servers.png`: Servers: every saved server connected, no password asked.
- `screens/native-terminal.png`: The project's terminal after `pwd && ls`.
- `screens/failure-8.png`: Part B (2.1.4) after the terminal section's relaunch: Back pressed before Conversations drew left the app (harness, fixed in c3c0cacfd).

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10 run 4b, added by hand)

Clean run: `--reseed` from a wiped OCN_U (4 GB RAM), harness 88d73b661 (+ README commit 0951de960), the same 3.0.0
(10010) universal switch release from 4c012f945 on the test key, `--skip-resource-checks` (another agent's Gradle on
the PC). Nothing was touched by hand. The seeded snapshot **was saved**: `snapshots/flutter-2.1.4-seeded/` with
`snapshot.pb`, `ram.bin` (4.1 GB) and `textures.bin`, written at 03:49 just before the native install.

### Against run 4 (23 of 29, 3 not run)

| Check | Run 4 | Run 4b | Why |
|---|---|---|---|
| part-b | FAIL | FAIL | Not an ANR this time: after the terminal section relaunched 2.1.4, Back was pressed before Conversations drew and left the app (harness; fixed). Every other Part B section passed, S12 included. |
| drafts | FAIL | FAIL | Draft, pins, the always-allow rule (Permissions and automation > FX-OC2 > `ls`, B-164) and the queued prompt (sent once) pass. Saved prompt failed: native's More actions > Go to group does not open (below). Harness now uses Attach and more > More > Prompts > Saved prompts, which native has. |
| voice-ready | PASS | **FAIL** | Harness: the search field did not expose the query, so "Voice" index 1 did not exist; the result row is now tapped below the field. The same build passed this check on run 4. |
| schedule | FAIL | FAIL | Listed: Go to does not open (below). Fires: correct, not a bug: see B-148. |
| everything else | | unchanged | first-launch, conversations, settings (with Sharp corners), servers, chat-messages, send-prompt, linux-ui, terminal, old-files, no-secret-in-log, linux-inodes, voice-files, tiles, channels, grants, updated-in-place, flow PASS; schedule-armed, widgets, shortcuts NOT RUN. |

**schedule-armed was NOT RUN for the wrong reason**: 2.1.4 did arm its daily alarm (`RTC_WAKEUP …SCHEDULED_PROMPT`
for 09:00) and native re-armed it (`…NATIVE_SCHEDULED_PROMPT`), but API 35's alarm record carries `whenElapsed`, which
the parser did not accept. Fixed (c3c0cacfd, with a test); it is judged from the next run.

### Native finding: More actions groups do not open

On 4c012f945 (switch release) the conversation's More actions menu shows Find, Rename, Fork, Copy transcript, Go to
and More; Copy transcript works, but tapping Go to or More (adb tap and Maestro alike) leaves the same menu open, no
group. On 5ff3ac113 Go to opened its group. Everything under it is unreachable from the chat: Saved prompts (also in
the + tray), Files, Terminal, and B-163's Schedule a prompt, which is the only way into the scheduled prompts list.

### B-148: does not reproduce as a bug in this window; a real bug one day further

Device state read during the run (adb, read-only): after Part B, 2.1.4 had caught up the 09:00 Nov 8 occurrence on
its two-day jump (claim `finished`, run `ran`, late) but left `nextRunAt` at Nov 8 09:00. On the first native launch
the native engine re-armed that occurrence; on its own two-day jump (Nov 10 04:00) the alarm fired, the claim read
Duplicate, the schedule moved to Nov 10 09:00 and that was armed. Nothing was owed between the two jumps, so sending
nothing was right, and S12's two-day window could not show a firing.

One day further it is a bug: the engine moved a settled-but-not-advanced schedule to the first occurrence after
*now*, so an occurrence that fell due after the settled run (Nov 10 09:00, with the native app first looking on Nov
11) was dropped instead of caught up. Fixed on `native/fix-b148` (6b0ec4235, from native/screens-merge 6744c2fcc):
`advanceSettled` moves to the first occurrence after the settled attempt ran (its claim's `at`). New test
`B-148 a settled late run leaves the occurrences due after it owed, so the next one catches up`: the 27 schedules
tests pass with the fix; without it that test fails (VPS, `:engine:schedules:desktopTest`). S12 now moves three
days, which an APK without the fix should fail and one with it should pass.
