# Native 3.0.0: finish plan and end state (2026-11-07)

This file is the control sheet. It says what "done" means, who owns which module right now, and how the work lands.
The coordinator updates it at every merge wave.

## End state: Release Candidate 1 (RC1)

RC1 is the first build that meets all seven gates. Each gate is measured, not judged.

| # | Gate | Measure | Now |
|---|---|---|---|
| G1 | Builds clean | `assemblePreviewBenchmark` + `assembleSwitchRelease` build; every desktopTest source set compiles; unit tests green | app builds (merge 22); tests not yet run as a whole |
| G2 | Looks like Flutter | Daily path (home, chat, composer, sheets, settings hub): every Flutter golden rendered, ≥ 90 % pass. All screens: ≥ 75 % of goldens rendered and passing | 7.5 % overall (scoreboard at 89ec49e55, before today's chat merge) |
| G3 | Aligned | Alignment gate: 0 new findings; baseline shrinks | 133 baselined, gate live |
| G4 | Moves like native | 90/120 Hz on the owner phone; janky frames < 5 %, p99 < 20 ms scrolling chat and home; row→chat transform, animated lists, sheet physics, send morph in | 1.5 % janky at 60 Hz (owner phone 11-07); motion lead building |
| G5 | Keeps data | Upgrade gate Flutter 2.1.4 → native: 29/29 (items 2.1.4 or the emulator can't seed are NOT RUN with a reason) | 21/29 + harness fix for grants |
| G6 | Release shape | Switch build 3.0.0 / versionCode ≥ 10010, R8 + ABI splits, identity gate passes with the owner key | release work merged (merge 22), not yet run with the key |
| G7 | Owner walk | Owner uses it on the phone and says yes | not yet |

Out of RC1 (later, owner decides): Glass and school galleries (order 34-37), AI Team screens, pull requests from the
phone (B-158), Play Store.


## Task list to RC1 (coordinator works it top to bottom; ticked as done)

- [x] T1 Compile everything on the head: app APK + every module's desktopTest sources (G1). Fix what fails.
- [x] T2 Build the head on the PC, install on the owner phone + emulator, screenshot the daily path.
- [ ] T3 (in progress: 205 failures on first run, mostly missing goldens; rerun with Flutter goldens fetched) Run the unit tests of the touched modules (kit, kit-chat, chat, chats, composer, settings); fix failures.
- [x] T4 Merge the motion lead (fenced: kit motion, shell nav, refresh rate, new motion parts) once its tests pass.
- [ ] T5 Motion rollout on screens: row→chat transform, animated list items, sheets/menus physics, send morph, rolling numbers, toast pill (G4).
- [ ] T6 Measure motion on the owner phone: refresh rate, janky %, p99 (G4 numbers).
- [ ] T7 Scoreboard result (merge 28): list what still fails on the daily path; fix those myself, smallest first (G2).
- [ ] T8 Kit order 23 (its builder ended without pushing): port it.
- [ ] T9 Targeted rerun: kit_code_block_* and anything fixed in T7.
- [ ] T10 Upgrade gate run 4 result: fix every real failure (G5); rerun to 29/29.
- [ ] T11 Alignment gate: no new findings on the head (G3).
- [ ] T12 Release shape: build the switch release (3.0.0 / 10010, R8, ABI splits) and run the identity gate with the test key; owner key only with the owner present (G6).
- [ ] T13 Flutter 2.1.5: suite result, release compile, owner yes to publish.
- [ ] T14 RC1: final build, tracker/backlog updated, owner walk (G7).

## Rules for builders (no stepping on each other)

1. One owner per module per wave. The table below is the only source; a builder that needs a change outside its
   modules writes it in its report instead of editing.
2. Before every merge wave the coordinator runs `overlap.sh` (modules and files touched by unmerged branches). Two
   unmerged branches touching the same file: the module owner's version wins and the other is re-briefed.
3. No new builder starts on a module that has an unmerged builder.
4. Layout and look follow Flutter (the Dart file is the spec). Motion and rendering follow native practice.

## Status at merge 28 (2026-11-07 23:30)

Waves 4-7 landed (merges 21-28): chat parity, translucent composer, timeline, Flutter composer layout, chat screen (700 dp pane, find, loading, error), home rows, settings, shell/servers, remaining screens, kit orders 07-10/12/13/18/20-22/25-30, ~1,700 gate cases, gate tooling, release shape, #117 native, background shaders, upgrade-gate harness. Every merge built on the PC and was installed on the owner phone.

Now: G2 scoreboard rerun and G5 upgrade gate run 4 on merge 28; motion lead (G4) finishing its fenced branch; kit order 23 still out. Next: motion rollout batch, last fix orders from the scoreboard, RC1 build.

## Owners during wave 4-7 (history)

| Module / area | Owner (branch) |
|---|---|
| `:ui:kit` orders 01/05/06 files | native/s17-kit-a |
| `:ui:kit` order 08 | native/s15-rb-08 (pushed, merges next wave) |
| `:ui:kit` order 13 | native/s15-rb-13 still pushing: its pushes merge first; native/s17-kit-b takes order 13 after that (on conflict s17-kit-b wins) |
| `:ui:kit` orders 11-12, phrase chips | native/s17-kit-b |
| `:ui:kit` orders 18, 22, 23 files | native/s15-rb-18, s15-rb-22, s15-rb-23 |
| `:ui:kit` live/ + Appearance picker | native/s16-live-merge |
| `:ui:kit` motion/, `:ui:shell` nav, `:androidApp` refresh rate | native/motion-lead (local) |
| `:ui:kit` / `:ui:kit-chat` gate cases (tests only) | native/s17-kit-cases, native/s17-chat-cases |
| `:ui:kit-content` | native/s17-content |
| `:ui:gate`, native/tool/design, native/tool/qa | native/s17-gate-tools |
| `:feature:chat` | native/s17-chat-screen |
| `:feature:chat-composer` + KitComposerBox/Chips | native/s17-composer |
| `:feature:chats` | native/s17-chats |
| `:feature:settings` | native/s17-settings (the Appearance picker lines belong to s16-live-merge) |
| `:ui:shell` gate cases, `:feature:servers` | native/s17-shell-servers |
| onboarding/setup, files/review/git, projects, phone, agents, usage, about, widget panels | native/s17-screens |

## How it lands

- Waves: queued pushes are merged together, then built on the PC (or the VPS from 00:00 to 02:00), installed on
  the owner phone and the emulator, and shown to the owner with screenshots.
- After the wave 4 builders land: (1) scoreboard rerun, (2) upgrade gate rerun on the release-shaped build,
  (3) motion rollout batch on the screens, (4) last fix orders for whatever still fails G2-G5.
- RC1 build: signed switch build, identity gate, then the owner walk (G7).
