# Run results (F5) — synthetic captures, 2026-09-08

Rendered by `build/traycer/run_results_capture_test.dart` (an ignored helper
that mirrors `tool/capture/fixtures.dart`) from a server-shaped message
fixture through the real `RunResult.fromMessages` and `RunResultView`. No
device, network or model call is involved.

| File | What it shows |
|---|---|
| `light-completed.png` | 390 px, light. Latest turn: two assistant steps, provider finish `stop`, three changed files from completed edit/write/patch tools, three commands (recorded exit 0 + textual "looks like a test command" label, exit code not recorded, output pruned), "observed live" line. |
| `dark-completed.png` | Same fixture, dark theme, "recovered from server history" line. |
| `light-output-sheet.png` | Tapping a command opens the recorded tool output in the transcript's own ToolCard. Nothing is re-fetched or summarised. |
| `light-failed-partial.png` | Newest step carries a provider error; the user message that started the run was not in the loaded pages, so the partial-history notice and "at least N steps" wording appear; no tool parts → explicit "no evidence" notice. |
| `narrow-completed.png` | 320 px at 2x text scale. |

## Evidence policy the screen follows

- Outcome comes only from the newest assistant step's `finish`, `error` and
  `time.completed`. Session idle and message prose are never used. Earlier
  step errors are reported as a note, not as the outcome.
- "Run" is a conversation turn inferred from server messages (assistant
  steps after the latest user message), labelled with the first step's id.
  It is not a server-side run id.
- Changed files come from COMPLETED, executed `edit`/`write`/`patch` tool
  inputs and metadata. Commands come from `bash`/`shell` tool parts; the exit
  code is shown only when the server recorded one.
- The "looks like a test command" tag is derived from the command text and
  says so; it never claims tests ran or passed.
- History is walked back at most five pages. If the run's user message is not
  reached, the view says so and treats counts as lower bounds.
- "Observed live" appears only when this connection received the
  `message.updated` completion of that exact assistant message id.
  Nothing is persisted; a restart rebuilds the view from server history and
  honestly reports the step as not observed.
