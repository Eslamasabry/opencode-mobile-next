# Native switch upgrade gate, 2026-11-08

**Verdict: FAIL** (23 of 29 checks passed, 3 not run)

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
| voice-ready | Voice pack shows Ready | PASS |  |
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
- `screens/native-voice.png`: Voice typing set up and ready, nothing downloaded again.
- `screens/failure-9.png`: the native always-allow step: Permissions and automation first lists the saved servers (harness fixed after this run).

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10 run 4, added by hand)

Integration head `native/screens-merge28` 4c012f945 (harness from `native/e2e-harness-2`, the B-145 grants parser fix,
the s15 release shape and the B-163..B-166 fixes). Native APK: `:androidApp:assembleSwitchRelease` on the VPS
(JDK 21; lintVital skipped; the daemon needed `-Xmx6g`, the repo's 2 GB heap thrashed in R8 for an hour), the
universal split `androidApp-switch-universal-release-unsigned.apk` (3.0.0, 10010, R8, ABI splits arm64-v8a/x86_64 +
universal) signed on the PC with the same throwaway test key. AVD `OCN_U`, `--reseed` (Part A from a wiped AVD),
`--skip-resource-checks` (host RAM 6.3 GB free with the other emulator up).

**This run is not clean, and the gate is not closed.**

- Part B (the Flutter baseline) broke on host load, not on the update: 2.1.4 raised "OpenCode Mobile isn't
  responding" while typing in Settings search right after Part A (load 11 on 8 cores: another emulator and two
  Gradle builds on the PC). The dialog covered every later Part B section. I tapped Wait over adb, then force-stopped
  and relaunched 2.1.4 once; the last Part B sections (schedule listed, S9, S11, S12 fired, back to Arabic) passed.
  The harness now answers that dialog with Wait at the start of every section (`_sub/dismiss-notices.yaml`).
- The seeded snapshot was **not** saved: `emu avd snapshot save` ran while Storage2 had about 2.4 GB free (others
  filled it during the run) and failed without an error. A clean rerun (run 4b) could not start: with `--reseed`
  the emulator needs 9.8 GB to create the 8 GB userdata partition and Storage2 had 4.5 GB.
- Native Part C was not touched by hand.

### Against run 3 (5ff3ac113, 21 of 29, 3 not run)

| Check | Run 3 | Run 4 | Why |
|---|---|---|---|
| part-b | FAIL | **FAIL** | Host-load ANR in 2.1.4 (above); S12 now fires on Flutter (the two-day clock move). |
| settings | PASS | PASS | Now also checks the Shape chip shows Sharp (B-166 fixed). |
| drafts | FAIL | **FAIL** | Draft, pins and the queued prompt (sent exactly once) pass. Saved prompt: the More tap was lost before the menu settled; Always allowed: Permissions and automation first asks which server. Both harness, fixed after the run (`_sub/native-go-to.yaml`, pick FX-OC2), not yet rerun. |
| schedule | FAIL | **FAIL** | Listed: the same lost More tap (harness, fixed). Fires: real. Two days ahead, 2.1.4 fired its daily schedule in Part B; native showed no schedule notification within 60 s (screenshot step-1207, only "Ready · nothing running"). |
| linux-inodes | FAIL | PASS | rootfs 16141 -> 16141 (B-147). |
| grants | FAIL | PASS | The check reads the uid's app-op mode (B-145: All-files access was never lost). |
| everything else | | unchanged | first-launch, conversations, servers, chat-messages, send-prompt, linux-ui, terminal, voice-ready, old-files, no-secret-in-log, voice-files, tiles, channels, updated-in-place, flow PASS; schedule-armed, widgets, shortcuts NOT RUN (B-153). |

Native findings left: B-148 (the migrated daily schedule does not fire after the update). To close the gate: free
about 10 GB on Storage2 (or move OCN_U to a disk with room), rerun with `--reseed` so the snapshot is saved, and
judge drafts/schedule-listed on the fixed harness.

Screens looked at: `native-appearance.png`, `native-servers.png`, `native-linux.png`, `native-voice.png`,
`native-terminal.png`, `native-chat.png`, `failure-9.png`, and the run's step-397/410/426 (2.1.4 ANR),
step-906 (menu without Go to opened), step-933 (Permissions server list), step-1155 (chat, no menu) and step-1207
(native shade two days ahead, no schedule notification).
