# Chat while a reply runs: native against Flutter — 2026-11-09

Branch `native/chat-running` (from `native/screens-merge41`). Owner, from merge41 on the phone (Glass dark): "Step animation is
totally different and the stop button didn't have a circle as we already have the glowing border turned on. Wtf check
flutter." Reference: `mobile-next/master` lib/ (Flutter).

## Flutter against native, before this branch

| Part | Flutter (master) | Native (merge41) |
|---|---|---|
| Live line | `_KitTurnLiveLine`: an 8 dp accent dot, then what the turn does — the step in hand ("Running npm test"), else Writing / Thinking — and after 5 s for how long ("· 12 s"); a soft light sweeps over the words; a muted second line (the plan step or the newest line a running command printed); a tap shows the steps ("Show steps" / "Hide steps"). Kit `kit_turn.dart:868-1233`. | A plain grey "Working" (`KitQuietLine`): no dot, no step, no time, no sweep, no tap. `KitTurnLiveLine` existed in the kit but nothing used it. |
| Work while a step runs | Hidden: the live line names the step, so the work group draws nothing until the step ends, then unfolds back in where the steps were (owner decision 5, 20 Oct 2026; `transcript_rows.dart:464`). Shown (opened) only after a tap on the live line; a running list keeps its newest three steps, older ones behind "Show N earlier steps". | Always drawn; could stay open while running (the owner's screenshot), cap 25. |
| Step node | The tool glyph (12 dp) in a 20 dp `surface3` tile. | The same tile — the "terminal icon in a small box". |
| Step light | One 2 s lap: a light comes down the rail from the step before, splits at the node's top centre, both sides fill with a bright head each until they meet at the bottom, then a light runs on down the rail to the next node, the rail parts fading; Calm at half speed; still outline under Off. `kit_step_light.dart`. | The whole outline pulsing in alpha (never below 0.25): no filling, no heads, nothing on the rail; Calm ignored. A lone step row drew two lights (row and node) in the live colour. |
| Running step's name | Accent light sweeping over the ink (`_StepSweep`: text1 → accent → text1). | The live line's grey sweep (text3 → text1 → text3). |
| Stop | The neutral "■ Stop" chip at the trailing end of the chips strip above the box, for the whole running turn (owner, 30 Oct 2026), fading in and out; amber when stuck; hold → "Stop after this step". The box keeps mic or Send. The turning ring exists only on voice's "Stop reading" circle, and only while the glow is off. `kit_composer_status_strip.dart:30-160`, `kit_composer.dart:421-435`. | A white disc with a black square inside a turning ring, in the box's round target (moved beside "+" while words were typed). |
| Thought | Title = its first line ("Thinking…" while written); body in the secondary size and tone (grey). Folded unless "Show thinking" is on. | Body in the reply's ink (white) under a grey title that is its own first line: the owner saw it twice. No "Thinking…". |
| Summary words | "Ran 4 commands · fetched 2 pages" | Adds "· waiting to run 1 command" (native P-012). Kept: not part of this complaint. |
| Glowing border | Classic ring breathing round the box while a reply runs. | Same (unchanged, owned by `native/glass-panes`). |

## What changed (native)

- `ui/kit-chat` `KitTurnLiveLine`: step line (reveal), tap target with its label; one live-region node named by the phase.
  `feature/chat` `LiveLineWords.kt` words it as Flutter does; `ChatTranscript` draws it, keeps "steps shown" and folds the
  running work line away behind it (KitReveal), opening it when asked.
- `KitStepLight`: the full lap with the rail parts (rail geometry from `StepRail.spans`), Calm half speed, still outline
  under Off; draw phase only. `KitStepTile`: the glyph bare (no tile — owner's no-boxed-icons rule; the light still traces the
  tile's square). Lone rows draw one light. Running step names sweep in the accent.
- `BasicComposerHost`: Stop is `KitComposerStop` in the strip's trailing slot (hold menu, guard, tip anchor
  `chat-stop-button`), fading in and out (`KitComposerStatusStrip`); the box is one row with mic or Send. `KitComposerStop`
  gained `afterStepButton = false` so the strip shows the chip alone. `KitComposerStopRing` is untouched (voice uses it).
- Thoughts: body in `TypeRole.Secondary`, without the first line the title says; "Thinking…" while written.

Deviation from Flutter, on purpose: the step glyph has no `surface3` tile (owner: no icons in boxes).

Also fixed while here: the work line's words counted a step that simply runs as "waiting to run 1 command" (the
owner's screenshot); like Flutter's `_workCounts` it now reads "Ran 5 commands · fetched 2 pages". "Waiting to …" stays for
steps not started yet and, while a request waits for the person, the step that asks.

## Renders (desktop, the real chat screen and composer, 412 × 915 dp at 2.625, dark)

Made by `native/feature/chat/src/desktopTest/.../ChatRunningRenders.kt` (`CHAT_RUNNING_OUT=<folder>`; it writes nothing
without it) on the VPS at `e50c1a4c6`+`6de258744` (the fonts are the VPS's; Glass falls back to its frost on desktop, so
the liquid pane of `native/glass-panes` is not in these). Still frames: the light and the sweep are drawn at rest (the whole
outline lit), as the kit's still-frame rule does.

| State | Glass | Solid | Next to Flutter's golden |
|---|---|---|---|
| Just started ("Thinking…", Stop chip, glow) | 1-just-started-glass-dark | 1-just-started-solid-dark | compare-1-just-started-{glass,solid} ↔ `kit_turn_starting_dark` |
| Mid-commands: the live line names the step, work hidden | 2-mid-commands-glass-dark | 2-mid-commands-solid-dark | compare-2-mid-commands-* ↔ `kit_turn_running_live_dark` |
| Mid-commands, steps shown (tap on the live line): newest three, "Show 4 earlier steps" | 3-mid-commands-steps-shown-glass-dark | 3-…-solid-dark | compare-3-* ↔ `kit_step_timeline_running_open_dark` |
| Waiting for approval ("Waiting for you…" in amber; no clock, no sweep) | 4-waiting-for-approval-glass-dark | 4-…-solid-dark | compare-4-* ↔ `kit_turn_waiting_for_you_dark` |
| Failed step, the agent thinking on: ⊗ on the rail, the thought said once in grey | 5-failed-step-glass-dark | 5-…-solid-dark | compare-5-* ↔ `kit_work_line_ended_failed_dark` |

Flutter references in `flutter/` (from `mobile-next/master` test/goldens/kit): the turn's live line, the opened running
timeline, the lone running row, the Stop chip strip (`kit_composerstatusstrip_stop_text2_360x800_dark`) and the classic glow.
The request card of state 4 is not in the native render (the scene has no request slot).

## Motion record

`running-steps-solid-dark.mp4`: 60 frames at 30 fps (one 2 s step-light lap, looped three times), steps shown, Solid dark,
Full motion: the light filling round the running step's node, the accent sweeping the command, the live line's light
sweeping its words, the clock ticking. Desktop frame capture (no emulator: the PC was in quiet hours and the VPS has no
KVM). The grey band behind the live line in 3-* and the video is the desktop pointer's hover after the tap, not a fill.

## Proof

- `ChatRunningTurnTest` (4): the live line names the step with the time and the work waits behind it until tapped; an
  opened thought says its first line once; `thoughtBody`; Stop is the strip's chip, never in the box.
- `S8ChatPolishRulesTest`: a running step counts with the rest.
- VPS desktopTest: ChatRunningTurnTest 4/4, QaWave1ComposerTest 6/6, S8ChatPolishRulesTest 11/11, P102StoppedStepsTest
  5/5, TurnFactsTest 22/22, S28ComposerTipsTest 3/3, F1ComposerOneRowTest 2/2, StopGuardTest 5/5, KitStepTimelineTest
  7/7, S10MotionTest 15/15, KitStepLightTest 4/4. The kit gate tests (`KitTurnGateTest`, `KitComposerStatusStripGateTest`,
  `Chat3ComposerGateTest`) need the Flutter goldens the VPS does not have; CI's fast lane runs them (desktop tests 0–2 green
  on run 42519936665).
