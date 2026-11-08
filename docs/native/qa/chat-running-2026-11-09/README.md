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

## Renders (desktop, 412 × 915 dp at 2.625, dark)

See the table at the end (filled in from `ChatRunningRenders`, run with `CHAT_RUNNING_OUT=<this folder>`).
