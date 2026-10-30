# This phone: one answer (2026-10-20)

Finish line: This phone opens on one big plain answer to "Is it OK?". When the
phone's server runs, that is "Everything is running" with one small cost line;
when it does not, the answer is why, what the app will do by itself (only when
it really schedules it) and one mint "Restart now" at the thumb. Everything the
page had stays reachable as quiet rows below the answer or under Details.

Non-goals: the agents page (`LocalAgentScreen`, where "Add (180 MB)" would
live), the Linux manager pages, Keep running, the Settings hub, any change to
the thermal guard or crash recovery themselves.

Decision: `docs/design/vision-decisions-2026-10-20.md` and the vision page,
section "This phone": pick "C when running, B when stopped".

## Technical-honesty audit (before editing)

Sources read: `lib/state/phone_host.dart` (the page's host model, in-app and
Termux), `lib/builtin/builtin_server_recovery.dart` and
`lib/builtin/phone_server_healing.dart` (crash recovery owner),
`lib/termux/managed_server_recovery.dart` (Termux recovery),
`lib/builtin/thermal_guard.dart` (heat pause), `lib/builtin/phone_server_idle.dart`,
`lib/builtin/deliberate_stop.dart`, `lib/builtin/builtin_linux.dart`
(status, `performance()` service exit diagnostics),
`lib/builtin/linux_manager/linux_processes.dart` and `lib/termux/processes.dart`
(per-process resident memory), `lib/state/lifecycle_report.dart`. This page is a
phone-host page, not a server-protocol page: none of its facts come from the
OpenCode 1/2 gateway, so `ServerCapabilities` flags do not gate them; the gate is
the host kind (in-app vs Termux) and whether each reading exists.

| Element of the pick | Real source | Verdict |
| --- | --- | --- |
| "Everything is running" | `PhoneHost.state == running` (in-app: native `status().serverRunning`; Termux: manager snapshot `isReady`) | Real. Shown only for `running`, with no needs-you problem and no heat hold. It speaks for the phone's server (what this page looks after); agents start on demand. |
| Cost line "Uses 410 MB" | In-app: sum of resident memory (`rssKb`) of every process in the app's Linux from the `/proc` scan the Linux manager already uses (`LinuxManager.scanProcesses`). Termux: the same field in the processes report the page already reads. | Real, as an estimate: worded "Uses about N MB of memory" (shared pages make a resident sum an upper figure). Read once after the page's other probes; left out when it cannot be read. Storage used (`bytesUsed`) is the fallback line on the in-app host. Never invented. |
| Mint "running" dot | Same state | Real. |
| "Paused, your phone is warm" | `ThermalGuard.hold` (paused / stopped) via `thermalGuardSlotProvider` | Real, but it pauses the AI Team on this phone, never the OpenCode server. The answer says so: "Paused, your phone is warm" / "The AI Team resumes by itself …". Muted paused mark, not amber. |
| "Restarts by itself in about 2 min" (heat) | `ThermalPolicy.resumeAt` (= cool since + 2 min; the guard's own timer fires then) and the per-profile `thermalRecovery` automation choice | Real only while the phone is already cool (`resumeAt` set). While still hot there is no time: "resumes by itself once your phone cools". With automatic recovery turned off nothing resumes by itself, and the line says the team stays paused. |
| "Restart now" for heat | none: the guard has no manual resume, and starting a team on a hot phone defeats the guard | Left out. The heat answer has no main action (nobody has to act). Reported. |
| Why it stopped (crash) | In-app: `performance().services['server']` (`exitReason`, `lastStopRequested`) and `DeliberateServerStop`; `PhoneHost.problem == start` for a failed start. Termux: the manager's failure kind (`crash`/`recovery` → `start`). | Real. `memory_or_phantom_kill` is said as "Android closed the server" (the code cannot tell memory from the phantom-process limit, so neither is named). A deliberate stop says "The server is off". Otherwise "The server stopped by itself". |
| "Restarts by itself in …" (crash) | In-app: `BuiltinServerRecovery.value` (`waiting` + `nextAttemptAt`, `restarting`, `exhausted`) from `phoneServerHealingProvider`, read only when that owner already exists. Termux: `ManagedServerRecovery` (`enabled`, `nextAttemptAt`, `exhausted`). | Real when scheduled: countdown from `nextAttemptAt`; "in a moment" when native scheduled it without a time; "Restarting by itself" while it runs. Exhausted (3 tries) says it will not restart by itself. Recovery off, paused or not owned: no promise at all. |
| "Restart now" (stopped / crashed) | `PhoneHost.start` (the page's existing start, which resets the crash budget) | Real. One mint button pinned at the bottom. A deliberate stop says "Start the server" instead. |
| Quiet Restart while running | `PhoneHost.stop` then `start` (no restart call on the host model) | Real. A row "Restart the server on this phone"; asks first only when a reply is being written (`busySessions`). |
| Agents list, Add (180 MB) | `LocalAgentScreen` (separate page) | Kept as the one existing "Agents on this phone" row below the answer. The install cost on Add belongs to that page (non-goal). |
| Memory / Storage meters | — | Rejected by the pick; no meters. Storage stays the existing Storage row. |
| This phone / Other servers tabs | — | Rejected by the pick. |
| Everything else today (update, add tools, installed, storage, Linux, reply speed, export, keep running, terminal, recovery switch, migration, remove rows, log) | unchanged sources | Kept, as quiet rows below the answer; the log, failure text and performance values stay under Details. |

## What changed

| Before | After |
| --- | --- |
| A status row ("OpenCode 2 · Running", version, where) with Connect and Stop buttons under it, then a long list | One centred answer filling the first view: mark, 40 dp words, one small line; only the server's own row peeks under it |
| Running: Connect as a mint button, Stop as a secondary button | Running: no mint at all. "Use this phone's server" is a quiet text action under the answer (only when another server is in use); Restart and Stop are quiet rows below |
| Stopped / crashed: "Stopped" word plus Start | Why ("Android closed the server", "The server stopped by itself", "The server is off"), what the app does by itself (a live "restarts by itself in about 40 s" only when recovery really scheduled it; "it kept stopping…" once the retries are used up) and one mint "Restart now" at the thumb |
| Heat pause shown only as the shell's status line | Running with the AI Team held by the heat guard: "Paused, your phone is warm" (muted pause mark) and "The AI Team resumes by itself in about 2 min" from the guard's own resume time; no button |
| Needs you: "Needs you" inline on the row and a sentence under it | Amber dot, a short answer ("The server didn't start", "Setup didn't finish", "Can't reach Termux", …), the existing plain sentence as the line, the existing fix as the mint button |

Everything else the page had stays, as quiet rows below the answer in the same
order (update / up to date, add tools, agents on this phone, installed,
storage, Linux, reply speed, export, Restart after a crash, keep running,
terminal, remove rows) and the log, failure text and performance values under
Details. New row: "Restart OpenCode on this phone" (asks first only while a
reply is being written).

## Kit

New part `KitStatusAnswer` + `KitAnswerTone` (ok, paused, working, needs-you,
off): centred mark (14 dp dot with a soft halo of its own colour; two pause
bars; spinner; amber dot; hairline ring), 40 dp display words that grow with
text to 1.5x (1.2x under 300 dp), never break inside a word and balance their
lines, one body-size line (balanced the same way) or a live countdown, one
quiet tertiary action. A new answer cross-fades and rises over 250 ms, instant
under reduced motion; the answer is a live region, the words a header. Unit
test, gallery (412x915 and 1280x800, dark/light, 2.0 text), still-motion
registration and an overflow scene.

## Visual passes (against concept C / B)

1. First render: the answer at 28 dp, the server group plus Restart, Stop and
   the update row all in the first view, a mint "current" tile, "Restart
   the server" beside "Stop OpenCode", a pause glyph that read as boxes, and a
   failed start that said "didn't start" twice.
2. The answer in 40 dp display type; only the server row peeks under it;
   Restart/Stop moved to their own quiet group below; the tile is plain (the
   word "Connected" says it once); the pause mark drawn as two bars; one
   naming ("… OpenCode on this phone") for both rows; the failed-start line
   says only the way forward and its button is "Start again".
3. At 320 dp and 2x text the answer broke inside "Everything": display type
   now steps down so the longest word fits, grows only to 1.5x (1.2x on a
   narrow window), lines are balanced (no lone word on the last line), the
   small line too; "40 s" and "410 MB" never split; the margin halves on a
   narrow phone. Mark-to-words gap tightened to the concept's.
4. Storage fallback in round figures ("700 MB", not "700.0 MB").

Not matched, on purpose: the concept's large page title (pushed pages keep the
kit's top bar title), and the concept's soft wide aurora ribbons (the live
background belongs to the aurora agent).

## Evidence

Renders (412x915 at 2x unless named, one screen each) in
`/home/eslam/Storage2/launch-footage/vision-this-phone/`: running-dark,
running-light, running-rows-dark (scrolled: the quiet rows), heat-paused-dark,
crashed-restarts-by-itself-dark / -light, kept-stopping-dark,
stopped-by-you-dark, start-failed-dark, small-320-text2-dark,
small-320-text2-crashed-dark, arabic-rtl-dark, arabic-rtl-crashed-dark.
Capture: `tool/capture/vision_this_phone_test.dart` (the real screen over a fake
in-app Linux whose /proc scan sums to about 410 MB; the heat scene uses the
real ThermalGuard).

Tests run (passed): this_phone_screen (3 new honesty tests: no promise when
nothing is scheduled, the countdown when a retry is, "kept stopping" after the
retries), this_phone_plain_failures, this_phone_speed,
this_phone_component_removal, termux_clarity, termux_migration_ui, text_size,
builtin/tools_agree_after_setup, kit/kit_status_answer, kit_motion,
text_scale_overflow, kit_ratchet (G15/G16/G21), ui_glossary (G28), l10n_coverage.
Goldens regenerated with TZ=UTC and looked at: kit_status_answer gallery (new),
phone_server_screens, revamp screen_phone_1, slice_close_servers_phone,
termux_v2_host (this_phone_remove_tools and team_phone_v2 unchanged).
`flutter analyze`: no issues. kit_manifest_test fails only on KitChoiceTiles
(already failing at the base commit 247e2220e, not this slice). Full suite not
run (owner rule).

## Review round 1 (coordinator, same day)

- The server row says only where it runs ("Connected · In the app"); the
  version moved under Details, where the log panel is titled with it.
- Merged `int/ui-batch-2026-10-14` (no dock; Settings opens as a page);
  l10n regenerated after the merge. This phone, pushed from Settings › Server
  or Search, has the bar's own Back (`this-phone-back`); test "opened as a page
  it has Back…" pushes it through `openThisPhone` and pops it.
- Every render in `launch-footage/vision-this-phone/` is now taken from a
  pushed route (Back visible), including running-dark and
  crashed-restarts-by-itself-dark.
