# Switch header, agent picker, agent chips: evidence record (2026-10-08)

Branch `fe/switch-header-picker`, from `feat/genui-fe` at `e2b3fe16`.
Pinned Flutter 3.47.1 (Shorebird cache). Every run went through
`tool/qa/machine_lock.sh`. No emulator, no APK, no device. The proof is widget
tests and golden renders, so on-device behaviour still needs the owner's
check.

## What changed

1. **The phone runtime switch (OpenCode 2 to 1) shows one calm progress state.**
   This covers the coordinator's device evidence from APK 2195, items a to d.
   - (a) The status line used to say "Reconnecting to In-app Ubuntu · OpenCode 2…".
     It now says **"Switching to OpenCode 1…"** with the sync icon and progress tone.
     The shell's server pill also names the target ("In-app Ubuntu · OpenCode 1 · Switching…").
   - (b) and (c) While the switch runs, the old connection's "isn't answering"
     and "Server password changed — reconnect." no longer show. The switching
     line replaces them in every phase.
   - (d) A slow setup step used to say "Still waiting after 8 s" for as long as
     it waited. It now counts on ("Still waiting after 20 s"), and past a
     minute it says "Waiting 1 min". The live region still announces the
     escalation once; the count is not announced every second.
   - Source of truth: `BuiltinServerStarter.bringingUp` (new, in `lib/builtin/builtin_server.dart`).
     It returns the profile a start is bringing up. After a confirmed start
     has answered, it keeps returning that profile for a 10 s handover, which
     covers the moment before the connect.
   - `phoneRuntimeSwitchTarget` (`lib/ui/widgets/runtime_switch_status.dart`)
     counts a start as a switch only when the target is an in-app server and
     the connection status is about a different in-app server. Restarting the
     same server still says "Reconnecting".
   - No connection files were edited.
2. **FA4: the agent picker.** OpenCode and the agents that are ready and
   certified come first and can be chosen. Every other agent stays listed
   (owner rule: never hide one).
   - Those rows are dimmed (`KitRow.unavailable`) with one plain reason:
     "Not certified on this version yet", "Sign in needed", "Not installed · 95 MB", and so on.
   - Where a way forward exists, the row offers it as a chip.
   - Certification comes from `AgentCertificationMatrix.certifiedForChat`, new
     in `lib/domain/agent_tools/agent_certification.dart`. It reads the
     bundled snapshot of `docs/verification/agent-certification-matrix.json`.
     An agent counts as certified when the matrix has its row at exactly the
     catalog's pinned version, and the `install` and `smoke` cells passed with
     evidence.
   - Why not the matrix's full rule (every cell must pass): no agent meets it
     yet, so the full rule would hide Claude Code.
   - Today only Claude Code 2.1.283 is certified. fx has a row, but its smoke
     cell is `blocked:OW1`.
3. **Owner request: agent row acts as aligned chips.** Settings › Agents and
   the picker now show a short chip ("Install", "Sign in").
   - The chips sit in one trailing column (`KitRow.chipColumnWidth`, 112 dp),
     each at the column's start, so all of them start at one edge.
   - Screen readers still hear the target ("Install Codex").
   - New kit part: `KitRow(chip:)` and `KitRow.unavailable(chip:)` with the
     `KitRowChip` data class. The spec is updated in `docs/ux-system/kit-api/KitRow.md`.
4. **Cards status grammar.** With one name: "OpenCode 2 hasn't been checked…".
   With two or more, the verb agrees and the names read as a list: "Claude
   Code and OpenCode 2 haven't been checked…".
   - English uses an ICU plural. Arabic uses =1, =2 (dual يعملان) and other.

New copy is in `app_en.arb` and `app_ar.arb`: `connectionSwitchingTo`,
`shellServerSwitching`, `agentsStateNotCertified`, `agentsChipSignIn`,
`agentsChipResume`, `agentsChipCheck` and `agentNamesPair`.
`cardsProblemNotQualifiedFor` became a plural. `flutter gen-l10n` was run
once.

## Red proof (tests fail on the base code, pass after)

Logs are in the session scratchpad (`red-1.log`, `red-2.log`).

| Test | On base `e2b3fe16` |
|---|---|
| `test/runtime_switch_status_test.dart` "a switch names the version it goes to, in every phase" | found 0 "Switching to OpenCode 1…" |
| `test/kit/kit_checklist_test.dart` "4b. a slow step counts the wait it shows…" | found 0 "Still waiting after 20 s" |
| `test/agents_ui_test.dart` "certified ready agents come first…" | Claude row below the uncertified agent (693 vs 612) |
| `test/agents_ui_test.dart` "each act is a short chip, all starting at one edge" | found 0 "Install" chips |
| `test/agent_card_view_test.dart` "names agree with their verb and read as a list…" | got "Claude Code, OpenCode 2 hasn't been checked…" |

## Checks after the change

- `flutter analyze --no-pub lib` plus the changed tests: **No issues found!**
- One serial run of 19 files ended at `+378 -2`:
  - The test files: `test/runtime_switch_status_test.dart`, `test/kit/kit_checklist_test.dart`,
    `kit_row_test.dart`, `kit_since_test.dart`, `kit_manifest_test.dart`,
    `kit_keyboard_test.dart`, `test/kit_ratchet_test.dart`, `agents_ui_test.dart`,
    `agent_card_view_test.dart`, `agent_certification_test.dart`,
    `builtin_server_test.dart`, `l10n_coverage_test.dart`,
    `connection_status_presentation_test.dart`, `home_navigation_test.dart` and
    `text_scale_overflow_test.dart`.
  - The goldens: `runtime_switch`, `agents_ui`, `kit_checklist` and `kit_row`.
  - The 2 failures were a teardown double-complete in the new test harness.
    After fixing it, `runtime_switch_status_test.dart` plus its golden: **All tests passed! (+6)**.
- An earlier run of the neighbouring suites passed with **+267**:
  `work_tab_status_line`, `codex_connection_banner`, `chat_states_standard`,
  `release_blockers`, `slice_p4_4`, `no_raw_error_text`, `design_standard`,
  `this_phone_screen` and `phone_server_card`.
- A check run of other goldens that render the touched widgets passed with **+243**:
  shell, work tab, chats home, settings, phone setup, phone server screens,
  agent cards, kit since and kit progress.
- Goldens regenerated only for the files touched. Each changed image was
  inspected:
  - `agents_sheet_list_*` and `agents_settings_agents_*` / `agents_settings_check_*`: the chip change.
  - `kit_checklist_slow_*`: the counter, now "20 s".
  - New: `agents_sheet_mixed_*`, `agents_settings_many_*` and `runtime_switch_{reconnecting,password}_*`.

## Captures (412×915, rendered by the goldens; before = base code, after = this branch)

| | Before | After |
|---|---|---|
| Switch, old server reconnecting | `before/runtime_switch_reconnecting_dark.png`, `_light` | `after/runtime_switch_reconnecting_dark.png`, `_light` |
| Switch, old server refuses its password | `before/runtime_switch_password_dark.png` | `after/runtime_switch_password_dark.png` |
| Agent picker, every kind of row | `before/agents_sheet_mixed_dark.png`, `_light` | `after/agents_sheet_mixed_dark.png`, `_light` |
| Settings › Agents, chips | `before/agents_settings_many_light.png`, `_dark` | `after/agents_settings_many_light.png`, `_dark` |
| Slow setup step counter | `before/kit_checklist_slow_dark.png` | `after/kit_checklist_slow_dark.png` |

## Accessibility

- Each chip is one button node, labelled with the act and its target ("Sign in to fx").
- A dimmed picker row is `enabled: false`, with its reason as the hint. Its
  chip is a separate focusable button, and a tap anywhere on the row runs the
  chip.
- From 1.3× text the chip moves under the reason, like `KitRow.action`.
- The switching line is a progress status (no actions). The slow-step count
  is not a live region.

## Limits and follow-ups

- The Termux runtime switch (`TermuxHostSetup`, `TermuxHostJob.switchRuntime`)
  does not go through `BuiltinServerStarter`, so it is not covered.
  - Its header already names "This phone · Termux", not a version.
  - Covering it would need an accessor in the state layer, for example
    `ConnectionController.runtimeSwitchTarget` (a `ServerProfile?`, set by
    `prepareManagedRuntimeSwitch` and cleared on the next connect), or
    exposing `TermuxHostSetup.target` through a provider.
- "Certified" in the picker is the chat minimum on the pinned catalog version.
  It does not use the helper version, because the domain layer has no pinned
  helper constant. Runtime capabilities still need the exact helper match
  (`capabilitiesFor`).
- If an agent was chosen before this change and its version is not certified,
  it can still be the saved selection. The picker now shows it dimmed.
