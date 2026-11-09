# Android emulator exploratory QA — 2026-09-28

## Result

Second pass of the 1.0.44 (2058) release APK, alongside the earlier first-run
pass. The second pass reached the real Work and chat experience using both a
host OpenCode server and the completed This phone setup. A reply streamed and
completed on each server; settings discovery, provider/model lists, the MCP
catalogue, notification permission, conversation actions, and appearance were
also exercised. This remains a **partial exploratory pass**: the emulator
repeatedly exited during long runs, and Inbox/Project, full settings depth,
network-loss recovery, and some display states remain unverified.

No application source was changed. Screens 01–53 are from the first pass;
screens 54 onward continue capture numbering. I reviewed each retained image
against [the visual language](../../design/visual-language-2026-09-26.md) and
the repository security/UI rules. Malformed input attempts, empty captures,
and one Android home-screen capture were removed.

## Environment

| Item | Value |
| --- | --- |
| Branch / source | `codex/qa`, APK built from `ef4057591b70b29838f4e409ddd51064b5a8abfd` |
| APK | `/home/owner/Storage/tmp/oc-apk-share/opencode-mobile-2058.apk` |
| App | `io.github.eslamasabry.opencode_mobile`, version `1.0.44`, code `2058` |
| APK SHA-256 | `3a80b6424070be2c2af9d63aefbee1bd6266b2078715f745916cf49da814a8ef` |
| Device | `OC_API35`, Android 15 / API 35, x86_64, emulator 36.2.12 |
| Second-pass limits | 4096 MB, 4 cores, 1080×2400 at 420 dpi; guest reports 4013940 kB MemTotal |
| Boot | Cold, headless, `swiftshader_indirect`; successful boots took about 18–24 s |
| Host server | Official OpenCode v1.18.32 baseline binary; SHA-256 verified before extraction: `763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce` |
| Host server endpoint | `http://127.0.0.1:4123` tunneled with `adb -s emulator-5554 reverse tcp:4123 tcp:4123` |
| Host server PID | `1599956` (exact process stopped after QA; port 4123 no longer listening) |
| Host project | Isolated scratch project at `/home/owner/Storage/tmp/qa-opencode/project` |

Every device command used `adb -s emulator-5554`. No physical device was
queried or changed. The direct `http://10.0.2.2:4123` address was rejected by
the app's HTTP URL validation, so the emulator reverse tunnel and loopback URL
were used for server-backed testing. The official binary ran without model
credentials; OpenCode Zen Big Pickle returned both host and This phone replies.
No provider credential values were requested, logged, or captured.

The in-app server setup initially estimated about 4 minutes and 208 MB. Setup
reached 46%, was interrupted when the emulator disappeared, resumed from its
saved progress, and completed in about 12 minutes wall time. It installed
Ubuntu 24.04.5, Git 2.43.0, Python 3.12.3, Node 24.21.0, and OpenCode 1.18.32.
The app created the `my-app` project and its “Quick check-in” conversation.
The guest repeatedly disappeared from ADB during this run and later reconnect
attempts; this was observed as environment instability and is not attributed to
the app.

## Timings

`am start -W -n io.github.eslamasabry.opencode_mobile/.MainActivity` returned
3,380 ms on one second-pass launch. The existing conversation card was visible
immediately in the local-server recovery view, then the page resolved to the
explicit stopped-server action. Another measured first-run cold launch was
5,678 ms. The earlier first-run-only launches took 1,542–2,410 ms and opened
the onboarding choice screen, without a restored workspace. These are a few
emulator observations, not a performance claim.

| Second-pass observation | Time |
| --- | ---: |
| Cold boot to `sys.boot_completed=1` | about 18–24 s |
| `am start -W` with saved This phone content | 3,380 ms |
| This phone setup, including one recoverable interruption | about 12 min |

## Findings

| ID | Screenshot file(s) | Page | What's wrong | Severity | Steps to reproduce |
| --- | --- | --- | --- | --- | --- |
| QA-01 | [04-phone-mode.png](04-phone-mode.png), [05-phone-other-ways.png](05-phone-other-ways.png), [96-phone-setup-start.png](96-phone-setup-start.png) | This phone setup | **Product question for the coordinator:** on a nominal 2 GB emulator, setup previously reported 1972 MB and disabled the This phone flow, despite a stated 2048 MB minimum. The second pass completed setup on 4 GB. Please decide whether nominal 2 GB phones should be supported; this finding does not assert a product defect. | P1 — product question / broken flow on tested 2 GB configuration | Fresh install on a nominal 2 GB device → **On this phone** → compare detected memory with the 2048 MB requirement. |
| QA-02 | [51-demo-relaunch.png](51-demo-relaunch.png), [52-relaunch-settled.png](52-relaunch-settled.png), [53-relaunch-after-touch.png](53-relaunch-after-touch.png) | App relaunch | First pass saw a thin bright green outline around the viewport after cold relaunch. It remained after waiting and touching the screen; it did not reproduce in this second pass, so cause and current status are unknown. | P2 — visual/UX, not reproduced | Open the demo, force-stop the app, relaunch with `am start -W`, then inspect the app edge. |

No additional confirmed app defect was found in this pass. The local-server
recovery screen retained the “Quick check-in” card across emulator restarts and
offered **Start and connect** after the in-app server stopped. The first
connection state showed progress and last-known content before changing to
that recovery screen; this was not treated as a defect.

## What worked well

- The direct external HTTP attempt gave a clear validation message. The
  emulator reverse tunnel allowed the host server to connect without changing
  the app or exposing a credential.
- Work showed the connected host profile and project; a real “Quick check-in”
  prompt completed with an assistant response. The stop control was visible
  while the request was in flight. See [58-server-loopback-connected.png](58-server-loopback-connected.png),
  [68-chat-request-running.png](68-chat-request-running.png), and
  [69-chat-request-result.png](69-chat-request-result.png).
- The host Settings surface exposed server details, AI setup, model selection,
  providers, MCP, commands/tools, plugins, and AI Team. The provider list
  loaded without exposing credential values. MCP’s catalogue explained the
  registry source before loading its list. See screenshots 73–90.
- Android notification permission was presented by the OS and granted. See
  [70-notification-permission.png](70-notification-permission.png) and
  [71-notifications-allowed.png](71-notifications-allowed.png).
- This phone setup resumed after interruption and completed. A local prompt
  received a reply, and the conversation menu exposed Go to actions (Changes,
  Timeline, Find, Subagents, Details) and Do actions (Share, Compact context,
  Fork, Rename, Continue on computer, Open on another phone). See screenshots
  118–130 and [125-phone-chat-result.png](125-phone-chat-result.png).
- The slash command sheet displayed command choices on the host session. The
  command/tools settings also showed Commands, Skills, Tools, and References
  tabs; MCP catalogue loaded successfully.
- In-app Appearance switched from dark to light. At font scale 1.3, the stopped
  This phone recovery copy and actions remained readable. At 2.0, copy wrapped
  over more lines but remained visible. Rotation to landscape and back to
  portrait worked on the recovery view. See
  [93-appearance-light.png](93-appearance-light.png),
  [140-font-1_3.png](140-font-1_3.png), [142-font-2_0.png](142-font-2_0.png),
  [143-rotation-landscape.png](143-rotation-landscape.png), and
  [146-rotation-restored.png](146-rotation-restored.png).
- A cold relaunch restored last-known local conversation content. The app
  clearly said the local OpenCode process had stopped and retained a
  target-named restart action. A later launch again showed startup progress
  while retaining that conversation. See [137-relaunch-after-18s.png](137-relaunch-after-18s.png)
  and [151-last-launch-content.png](151-last-launch-content.png).

## Coverage still open

The following were not verified in this second pass:

- Inbox, including content from another server, and the Project tab.
- Every Settings page one level deep. Server, AI setup, model/provider lists,
  tools, MCP and catalogue, commands/tools sub-tabs, plugins, AI Team, and
  Appearance were opened; Notifications/background, privacy/data, usage, and
  setup-guide pages were not fully inspected.
- Host server stop → status-line wording → restart/reconnect. The emulator
  disappeared before this sequence could be performed. Airplane-mode recovery
  was also not tested.
- Tapping Stop on an in-flight request, completing Go to/Do actions beyond
  opening Changes and Timeline, command execution, the Termux move entry, and a
  full dark/light system-theme matrix.
  In-app dark and light Appearance states were checked; Android `uimode`
  toggles were issued, but the app's in-app theme remained the displayed theme.
- A complete 1.3/2.0 and landscape audit across all screens. Font/rotation
  captures in this pass use the stopped-server recovery screen; font 2.0 caused
  large text wrapping but no visible clipping in that screen.

The first-pass external-server attempt and its result are documented above and
at [18-server-connect-attempt.png](18-server-connect-attempt.png). Tailscale
was not installed, so its handoff was not followed. No automated tests were
run; this deliverable records manual emulator QA only.

## Pass 3 — build 2060

### Environment and result

| Item | Value |
| --- | --- |
| Branch / source | `codex/qa`, `e1d651047e6471cee7d377584cd75231bd23aefb` |
| APK | `/home/owner/Storage/tmp/oc-apk-share/opencode-mobile-2060.apk`, package `io.github.eslamasabry.opencode_mobile`, version code `2060` |
| APK SHA-256 | `61e158a8bc868f70c6300301b58c1dd04cb6571ac53b21ae453b2fdf37bd779c` |
| Install | `adb -s emulator-5554 install -r` succeeded; the old package was present before update and the saved “Quick check-in” conversation remained available. |
| Emulator | `OC_API35`, Android 15 / API 35, x86_64, 1080×2400; tried at 4096 MB / 4 cores, then 2048 MB / 2 cores, then returned to 4096 MB / 4 cores. |
| Guest memory | 4 GB boot reported 4013936 kB; 2 GB boot reported 2019400 kB. |
| Host OpenCode | Official v1.18.32 baseline archive from the requested GitHub release URL; SHA-256 checked as `763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce` before extraction. Server PID `1641872`, port 4123; stopped by exact PID and verified no listener. The temporary directory was removed. |

All ADB commands used `-s emulator-5554`. The release update retained app
storage and the This phone project/session. Several emulator launches reached
`sys.boot_completed=1`, then the QEMU process exited with status 139 while the
app was opening its in-app server. This recurred across five 4 GB launches and
one 2 GB launch (PIDs `1639432`, `1643934`, `1648107`, `1651280`, `1657039`,
and `1662876`). Each launch was retried as requested. The final
`adb -s emulator-5554 emu kill` found port 5554 already closed; those exact
QEMU PIDs were no longer running. Screenshots 152–171 are the retained
captures; malformed empty captures were removed.

### Fix verification

| B-ID | Result | Evidence and notes |
| --- | --- | --- |
| B1 — in-app server relaunch / explicit stop | **FAIL, partial** | Build 2060 displayed “Starting OpenCode inside the app” after launch ([152-build2060-initial.png](152-build2060-initial.png), [158-b1-first-transition.png](158-b1-first-transition.png)). After a force-stop launch it later displayed “OpenCode inside the app is stopped” ([161-b1-state.png](161-b1-state.png)); tapping **Start and connect** still showed the stopped page ([163-b1-manual-start.png]). Server management then said “OpenCode on this phone isn't answering” ([164-b1-details.png]). The required 4+ launch sequence, two launches within 15 seconds, successful manual start precondition, and explicit Stop/relaunch check could not be completed because QEMU repeatedly exited. |
| B2 — 2 GB setup allowance and slow-device note | **BLOCKED** | The 2 GB guest booted and reported 2019400 kB. The app remained on the This phone startup path ([168-b2-launch-retry.png]); QEMU exited before I could navigate to the setup gate. I could not verify the allowance or the “It may be slow on this phone” copy. |
| B4 — closing untouched Add server step 1 | **BLOCKED** | The Add server form and close action were not reached in this pass. |
| B5 — key press then cold relaunch, no green frame | **BLOCKED** | No key press plus cold-relaunch cycle completed. The captured recovery screens do not show a green outline, but this does not verify the requested sequence. |
| B6 — compact This phone status during setup for another server | **BLOCKED** | The This phone setup screen with a different unresponsive server was not reached. |
| B9 — plain address errors | **BLOCKED** | Build 2060 address validation copy was not exercised. |
| B10 — provider rows | **BLOCKED** | Build 2060 Providers rows were not reached. |
| B11 — named Tools rows | **BLOCKED** | Build 2060 Tools rows were not reached. |

### Findings

| ID | Screenshot file(s) | Page | What's wrong | Severity | Steps to reproduce |
| --- | --- | --- | --- | --- | --- |
| QA-03 | [158-b1-first-transition.png](158-b1-first-transition.png), [161-b1-state.png](161-b1-state.png), [163-b1-manual-start.png](163-b1-manual-start.png), [164-b1-details.png](164-b1-details.png) | This phone relaunch | On the saved This phone profile, launch showed startup progress but then returned to the stopped page. A tap on **Start and connect** still showed stopped, and server management said the phone server was not answering. This is a partial reproduction; the emulator exited before a longer retry could confirm whether the local server eventually recovered. | P1 — broken flow | Install build 2060 over the saved build 2058 data → cold-launch OpenCode Mobile → wait through the startup screen → observe the stopped page → tap **Start and connect** → open server management and inspect the phone profile. |

### Remaining third-pass coverage

Not verified: Inbox (including the other-server row), Project tab, every
Settings page one level deep, stopping an in-flight reply, host server stop and
reconnect wording, airplane-mode recovery, Move from Termux visibility, AI
setup with a real config, MCP catalogue enable/disable, font scale 2.0 on chat,
and landscape chat. The host server was started with the verified binary but
the emulator exited before server-backed flows could be reached. No provider
credential values were requested, logged, or captured. No app code was changed
and no automated tests were run.
