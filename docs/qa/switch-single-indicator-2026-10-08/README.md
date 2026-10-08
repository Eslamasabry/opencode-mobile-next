# One indicator for switches and reconnects: evidence record (2026-10-08)

- **Branch:** `fe/switch-single-indicator`, cut from `feat/genui-fe` at `6e387668`.
- **Toolchain:** the pinned Flutter 3.47.1. Every run went through `tool/qa/machine_lock.sh`.
- **Evidence type:** widget tests and golden images only. No emulator, no APK
  and no device were used.

## Owner principle

"Why show 2 spinners… show one only."

During a runtime switch the home shell said the same thing twice:

- the server pill ("In-app Ubuntu · OpenCode 1 · Switching…", with a spinner);
- the status line under it ("⟳ Switching to OpenCode 1…").

A plain reconnect doubled up the same way: the pill said "Reconnecting" and
the line said "Reconnecting to …".

## Change

`lib/ui/screens/home_screen.dart`: when the pill already shows progress, the
shell's status slot drops the connection line (`KitScreen.bodySays: {connection}`).
The pill shows progress during:

- a switch between this phone's OpenCode versions;
- connecting;
- reconnecting.

The line comes back for problems that need the person, with its action:

- "isn't answering" (with Reconnect or Restart);
- a refused password;
- a credential that can't be read.

The rule applies only where the pill is. A page without the pill, such as a
pushed page, still says "Switching to OpenCode 1…" in its own status line.
That is still one indicator per screen.

## Red proof (on `6e387668`)

Logs are in the session scratchpad (`red-3.log`, `red-3b.log`).

| Test | On base |
|---|---|
| `runtime_switch_status_test.dart` "a switch is said once, in the server pill, in every phase" | found the `connection-status-banner` line |
| `runtime_switch_status_test.dart` "a plain reconnect is said once too; a real problem keeps its line and its action" | found the line while reconnecting |
| `home_navigation_test.dart` "automatic SSE reconnect still offers a manual retry" (updated to the rule) | found the line while reconnecting |
| `runtime_switch_status_test.dart` "a page without the pill says the switch in its status line" | passes on base too, as it should: it guards the line off the shell |

The test harness now hosts `AppConnectionStatusScope` above the navigator, as
`main.dart` does, so pushed pages read it.

## Checks after

- `flutter analyze --no-pub` on the changed files: **No issues found!**
- One serial run of 28 files: **All tests passed! (+314)**. The files:
  - `runtime_switch_status_test`, `runtime_switch_golden_test` and `home_navigation_test`;
  - `server_switcher`, `first_run_landing`, `codex_navigation`, `files_project_chip` and `project_hub`;
  - `accessibility_guidelines`, `safety_confirms`, `text_scale_overflow`, `app_lifecycle`,
    `desktop_shortcuts`, `desktop_scrollbar` and `release_blockers`;
  - the revamp shell and slice goldens (`screen_shell_2`, `slice_p5_1`, `p52`, `p63`, `p34`);
  - the goldens `shell_integrated`, `shell_chats_first` and `chats_home`;
  - `connection_status_presentation`, `work_tab_status_line` and `kit_ratchet`.
- Goldens regenerated: only `runtime_switch_*`, the file this slice owns. A new
  scene, `restart` (a plain reconnect), was added. Each image was inspected.
- **Not caused by this slice:** `test/goldens/settings_golden_test.dart`
  `settings_diagnostics*` fails on `feat/genui-fe` `6e387668` with a 0.13 %
  diff in the "Details" row.
  - The diagnostics page was changed by the merged `fe/crash-consent` /
    `sol/bc-diagnostics` work. This slice touches neither that page nor its test.
  - Left for the coordinator.

## Captures (412×915; before = `6e387668`, after = this branch)

| | Before | After |
|---|---|---|
| Switching, old server reconnecting | `before/runtime_switch_reconnecting_dark.png`, `_light` | `after/runtime_switch_reconnecting_dark.png`, `_light` |
| Switching, old server refuses its password | `before/runtime_switch_password_dark.png` | `after/runtime_switch_password_dark.png` |
| Same server restarting (plain reconnect) | `before/runtime_switch_restart_dark.png`, `_light` | `after/runtime_switch_restart_dark.png`, `_light` |

## "Can't reopen old conversations" on Claude Code: findings

**In tests and goldens, this is fixture data.**

- `agentRowFor('claude', FakeAgentStage.ready)` in `test/support/agents_fakes.dart`
  builds `AgentCapabilities(resumeVerified: false)`.
- Only `FakeAgentStage.readyVerifiedResume` sets it to true.

**In the app, it is also a real stale flag in the state layer.**

The rows are built in `lib/state/connection/phone_agents.dart`. That file is
read-only for this slice, so the cause and the needed change are recorded
here instead of edited.

- **What should happen:** the matrix grants resume to Claude 2.1.283 only
  through helper 0.9.2.
- **Where the helper version comes from:** `_paHostCapabilities` reads it from
  the Paseo transports that are connected at that moment.
- **The ordering problem:** `_paRefreshRows` computes each row's capabilities
  *before* it calls `await _paSyncSources()`, which is what connects those
  transports.
  - On the first row read after the app starts, no transport is connected, so
    the helper version is null.
  - The row then gets no capabilities, and shows "Can't reopen old conversations".
  - The rows are not rebuilt when a source connects later. They correct
    themselves only on the next `refreshAgentRows()`, for example when the
    Agents screen or the sheet is opened again.
  - That matches the owner's screenshot, where a later read showed plain "Ready".
- **Needed change (state-layer owner), either of these:**
  1. In `_paRefreshRows`, after `await _paSyncSources()`, rebuild the rows when
     the observed helper version changed. For example, keep the version used
     for the projection and call `refreshAgentRows()` once when it goes from
     null to known.
  2. Have `_paHostCapabilities` use the helper version the host reports at
     inspection or in the phone check's Version step, not only connected
     transports. This also covers a phone with no Paseo source yet.

  Both keep the BA4 rule (no unknown evidence). They only make the known
  evidence arrive before the row is drawn.
