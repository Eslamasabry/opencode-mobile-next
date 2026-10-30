# Vision slice: Phone setup progress (2026-10-20)

Pick **B** from the vision page: one big calm ring, one sentence of what is
happening now, a slim step trail, a cost line with real numbers only, and
Pause / Continue. The setup must survive an app kill as it does today.

## Audit: every element of the pick, against what the app really has

Read before editing: `lib/builtin/setup/setup_contract.dart` (the screens'
contract), `setup_engine.dart` and its parts (`progress_helpers.dart`),
`setup_scripts.dart` (the `::oc` progress protocol), `components.dart` (the
registry), `lib/platform/network.dart`, `BuiltinLinux.kt` (download resume,
boot receivers) and `AndroidManifest.xml`. This screen is the phone's own
setup job: no server is involved, so no `ServerCapabilities` flag or OC2
event applies (the job runs before any server exists). Paseo and phone agents
(Claude Code, Codex) are installed by a different flow
(`lib/builtin/agents/`), not by this engine.

| Element (concept) | Source in the app | Verdict |
| --- | --- | --- |
| Ring percent | `SetupProgress.overall` is a blend: each component weighted by its *estimated* seconds, and a running component that only reports stages counts as a flat 50 %. Not a measurement. `ComponentProgress.fraction` is real: bytes from `::oc bytes` (curl size vs Content-Length, the native Range download) or apt's own `::oc percent`. | **Build, per step.** The ring shows the current step's measured share ("64 %", with "18 of 30 MB" under it). When the step reports no amount (stage only, checks, starting OpenCode) the ring shows **steps done of total** ("2 of 6"). `overall` is never shown as a percent. A new stage that measures from zero (download → unpack) starts its own arc; within a stage the arc never goes back. |
| "Installing Claude Code" sentence | `SetupProgress.current` + the row's `stage` (scripts print English stages with versions, e.g. "Downloading OpenCode 1.4.2"); `SetupProgressView.sceneFor(...).stage` already classifies download / unpack / install / start. | **Build, with the real step.** One localized sentence from the step's title and its kind: "Downloading Linux base", "Installing OpenCode", "Starting OpenCode". Claude Code is not a step of this job, so it never appears. The script's raw stage stays under Details. |
| Slim step trail | `SetupProgress.components`, the job's own rows in order, with states. | **Build.** One mark per row of this job: done, current, waiting; halted when paused or failed. |
| Download size | `SetupComponent.downloadBytes` is a "display/preflight estimate only" and was already stated on the start screen before anything installed. Live bytes per step are measured. | **Build, measured only.** "18 of 30 MB" inside the ring while a step downloads. No whole-job size here (stated once, before starting; the vision rejects repeated cost). |
| About how long | `SetupProgress.etaSeconds`: null until 10 s of real progress, then the remaining estimates scaled by this job's real pace. The old view also kept it from going up and never let it undercut steps not started. | **Build, same rule.** "About 3 min left" only when the engine gives a figure; "Less than a minute" only on the last step. Nothing before that. |
| Wi-Fi | `NetworkBridge.readings()` (`oc/network`): Android's default network transport (wifi / mobile / other / unknown), no names, no probe. | **Build.** "Wi-Fi" or "Mobile data" in the cost line while the job runs; nothing when unknown (VPN, desktop, tests). |
| Battery cost ("uses some battery", "battery ~3%") | Nothing measures it. | **Leave out.** |
| Pause | `SetupEngine.cancel()` stops the native job; finished components stay installed. `run()` again checks every component and skips what is installed, so it is the resume. Downloads continue where they stopped (`curl -C -`, the Linux base's HTTP Range). A step that was installing packages starts that step again; interrupted package work is repaired first. | **Build as Pause / Continue setup.** Truthful because nothing finished is lost and downloads resume. The paused line says exactly that. No confirmation: nothing is lost. |
| Continue | `SetupProgress.canContinue` (failed, interrupted, cancelled) → `run(sameIds)`. | **Build.** The one solid mint action when setup is paused, interrupted or failed. |
| Survives an app kill | The job runs under the native foreground service and is persisted in setup.json; the screen calls `restore()` on open. | **Kept as today.** |
| "Safe to leave. Setup resumes if your phone restarts." (A) | After a reboot the job reads as *interrupted* and needs Continue setup: no boot receiver resumes it. | **Adapt.** "Safe to leave" is true (the service keeps going); "resumes if your phone restarts" is not, so it is not said. The line promises a notification only when Android allows one (the existing ask). |
| Keep screen on toggle | Not needed: the job runs in a foreground service. | **Remove** (vision: reject). |
| "In progress" pill, cost repeated, two headers | — | **Remove.** |
| Checklist (A) | `KitChecklist` via `SetupProgressView`, with the log and Report this failure. | **Adapt: under Details**, in a sheet. |
| Person steps (Termux grant, sign-in) | Only on the Termux host (`phone_setup_termux_job_screen.dart`). The in-app host has none; its one person step is Android's notification prompt, which the system shows. | **Not on this screen.** The Termux job screen keeps its checklist view; not changed here. |
| Ownership recovery | A failed job whose runtime ownership is unknown cannot be fixed by Continue; the old view offered Open app settings. | **Kept:** it becomes the main action in that case. |

## Visual passes (412x915 dark, against concept B)

Captures: `tool/capture/vision_phone_setup_test.dart` (live aurora still,
blurred shadows, real registry titles). Final images in
`/home/eslam/vision-assets/out/vision-phone-setup/`, earlier passes in its
`pass1/`, `pass2/`, `pass3/`.

1. Pass 1 → 2: the page had no Back (opened as a root route in the
   capture; now pushed as the app pushes it); the arc was one flat colour
   (now a quiet tail-to-head gradient under the soft glow); "4 of 6" was one
   heavy grey block (now a big done count, a smaller "of 6", "steps done");
   the bytes read "MB 64 من 41" in Arabic (figures now isolated left to
   right); at 320 dp / 2x text the last line sat under Pause (the list now
   ends above the pinned button); the Details sheet was full height with
   empty space (now as tall as its rows).
2. Pass 2 → 3: the gradient tail was muddy (lighter mix); the block sat
   low with a heavy empty top (lifted to just above the middle); the
   two-line notes broke with one orphan word (a 320 dp measure).
3. Pass 3 → 4: a halted ring was nearly as bright as a live one (dimmer
   neutral); the finished check was small for the ring; "Checking what's
   already on this phone" took two lines (now "Checking this phone") and
   its trail lit a current step while nothing ran (all waiting); the cost
   line said only "Wi-Fi" while nothing downloaded (the network now shows
   only with a time or a measured download).

Kept from the concept: the ring, the big figure with a smaller percent
sign, the sentence, the trail, one outlined Pause at the thumb, the mint
main action only when there is one (Continue setup). Left out on purpose:
the battery figure (no source), "Keep screen on", the cost pill, the
repeated size line, "In progress" pill.

## Verification (this branch on int 433981f66)

- `flutter analyze`: no issues.
- Focused tests, all passing: `test/phone_setup_progress_screen_test.dart`
  (39), `test/kit/kit_progress_ring_test.dart`, the gallery
  `test/goldens/kit/kit_progress_ring_golden_test.dart`,
  `motion_setup_test`, `design_standard_setup_test`,
  `coverage/phone_job_coverage_test` (ledger: the first-setup fact is no
  longer printed here), `phone_setup_golden_test`,
  `revamp/screen_phone_2_golden_test` (stop-sheet golden replaced by the
  Details sheet), `revamp/slice_qa_phone_setup_*`, `termux_migration_ui`,
  `phone_setup_notification_route`, `launch_shortcut_routing`,
  `phone_setup_welcome_entry`, `network_test`, `kit/kit_manifest_test`,
  `kit_ratchet_test` (G16/G21), `kit_motion_test`,
  `text_scale_overflow_test`, `ui_glossary_test`, `l10n_coverage_test`,
  `no_red_scan_test`. Goldens regenerated with `TZ=UTC` and looked at.
- Widgetbook: the screen and its Details body are catalogued
  (`widgetbook/lib/servers/phone_setup_progress.dart`) and the screen left
  `coverage_baseline.json`. The screen standard's "at most 3 text sizes"
  is broken by design (the big figure is a fourth size) and recorded for
  the four running/halted use-cases in `standard_baseline.json`; the done
  state passes.
- `tool/qa/preflight.sh 433981f66`: format, commit rules, l10n, analyze
  and QA tool tests pass; three repo-scanning tests fail on files this
  branch does not touch (ui-ledger: `settings_group_page.dart`,
  `this_phone_answer.dart`; G26: `tool/capture/vision_model_picker_test.dart`;
  G23: `p310_settings_group_this-app_dark.png`), all already on int.
  `widgetbook/tool/coverage_check.dart` and the standard ratchet likewise
  fail on int for other screens; none of their findings is this screen.
- Not run: the full suite (not asked), any device or emulator.
