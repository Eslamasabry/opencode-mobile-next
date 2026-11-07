# Score group B: `:ui:kit-chat` gate cases (orders 14-17), 2026-11-07

Branch `native/s17-chat-cases`, from `native/screens-merge22` (e6a10900e). Scoreboard taken at 89ec49e55
([SCOREBOARD-2026-11-07.md](../SCOREBOARD-2026-11-07.md) "Group B"). Test files only, plus one two-line fix in the
shared gate reader (`:ui:gate`, noted below).

## Before / after (strict: every shot judged on its measurement, no DEPENDS_ON waiver)

Scope: all goldens of orders 14 (286), 16 (16) and 17 (40), and the work line and step timeline of order 15 (34):
376 goldens.

| | goldens | rendered by a gate case | pass |
|---|---:|---:|---:|
| Before (scoreboard, 89ec49e55) | 376 | 56 (6 o14 probe stems, 40 step light, 4 o15) | **0** |
| After (this branch) | 376 | **376** | **69** |

Per part, after:

| order | part | goldens | pass | fail |
|---|---|---:|---:|---:|
| 14 | `part-kit_agent_strip` | 24 | 0 | 24 |
| 14 | `part-kit_ask_line` | 30 | 12 | 18 |
| 14 | `part-kit_composer` | 32 | 16 | 16 |
| 14 | `part-kit_composer_chips` | 24 | 10 | 14 |
| 14 | `part-kit_diff_view` | 36 | 0 | 36 |
| 14 | `part-kit_markdown` | 30 | 4 | 26 |
| 14 | `part-kit_message` | 28 | 2 | 26 |
| 14 | `part-kit_queued_message` | 28 | 4 | 24 |
| 14 | `part-kit_tool_row` | 26 | 0 | 26 |
| 14 | `part-kit_turn` | 28 | 0 | 28 |
| 15 | `part-kit_step_timeline` | 16 | 1 | 15 |
| 15 | `part-kit_work_line` | 18 | 8 | 10 |
| 16 | `part-kit_composer_status_strip` | 6 | 0 | 6 |
| 16 | `part-kit_markdown_table` | 4 | 0 | 4 |
| 16 | `part-kit_transcript_excerpt` | 6 | 0 | 6 |
| 17 | `part-kit_step_light` | 40 | 12 | 28 |
| **all** | | **376** | **69** | **307** |

### How it was measured (reproducible)

In this cloud container (JDK 21, no Android SDK; Maven Central answered 429, so a local, uncommitted Gradle init
script pointed `repo.maven.apache.org` at Google's mirror of Central):

```
FLUTTER_REPO=$PWD GLASS_REPO=$PWD python3 -I native/tool/design/rb_gate.py fetch --order 14   # also 15, 16, 17
cd native && OC_ALIGN=report OC_LAYOUT_BASELINE=update ./gradlew :ui:kit-chat:desktopTest \
  --tests '*.rb.o14.*' --tests '*.rb.o15.KitWorkLineGateTest' --tests '*.rb.o15.KitStepTimelineGateTest' \
  --tests '*.rb.o16.*' --tests '*.rb.o17.KitStepLightGateTest'
git checkout native/config/layout/baseline/ui-kit-chat.tsv          # the guard's findings are listed below, not baselined
RB_DEV=1 python3 -I native/tool/design/rb_gate.py evaluate --order 14   # 16; 17 with RB_RECHECK=15;
RB_PARTS="part-kit_work_line part-kit_step_timeline" RB_DEV=1 python3 -I native/tool/design/rb_gate.py evaluate --order 15
```

`OC_ALIGN=report` and `OC_LAYOUT_BASELINE=update` are measurement-only, as on the scoreboard (its item 0.1): without
them the guards throw before the picture is taken and the shot has no measurement at all. Order 14 rows are from the
first full run; orders 15 and 17 were re-run after the status-words change below (order 14 does not use those files).

## What changed

1. **B1 / B2: gate cases for every golden of orders 14 and 16.** `rb/o14/*GateTest.kt` (10 parts, 145 stems) and
   `rb/o16/*GateTest.kt` (3 parts, 8 stems), each with the Dart scene's exact inputs and one `matrix()` per part.
   Where native has no part, the case stands in with the nearest kit parts and the file's KDoc lists each gap as
   `Mismatch:`.
2. **B3: work line and step timeline.** Both gate cases now use `Frame.Part` (Flutter's `kitGalleryPart`). The
   step-timeline case also lacked the scene's two 8 dp gaps (`kit_step_timeline_golden_test.dart:33,35`): that, not
   the part, was the "9 px less before, 7 px less after" the scoreboard measured. All 34 stems are covered now (was 2).
3. **B4: step light.** The rows now sit on an opened `KitStepTimeline` ("Ran 3 commands", terminal glyph), as in
   Flutter. Box fell from 12.1-17.3 % to 1.0-1.7 % on every shot except the Arabic 2.0 one. Test bugs fixed on the way:
   - the lap offset counted one gate settle, not two, so every "t40" shot was taken at 0 % of the lap;
   - `first_t40` / `last_t40` never dropped a row;
   - `waiting` and `waiting_breathe` used Pending, not WaitingForYou;
   - change-of-state shots (tick at 300 ms, flash at 100 ms) were taken 1.2 s late; the change now runs on the
     scene's clock;
   - rows had an empty status label where Flutter shows its status words (`kitToolFailed` …).
4. **Shared fix in `:ui:gate` (design-authority's module):** `AlignRead` built an inverted box for a node turned by a
   layer (the timeline's chevron rotates 180° when open), and `coerceIn` threw. Every opened timeline or work-line
   shot died before rendering. Corners are now sorted; `AlignReadTest` B-GATE-201 fails without the fix and passes
   with it.

## Remaining mismatches (for the part owners)

Native has no part, so these shots stand in and cannot pass until the part exists:

- KitMessage (prompt time placement, attachments in the bubble, thought, notice and marker);
- KitTurn phases (starting, live line, find band, request card);
- KitQueuedMessage (whole bubble and receipt states);
- KitAgentStrip and its agent chip;
- KitComposer (assembled from the native pieces), suggestion panel and attachment chips;
- KitTranscriptExcerpt;
- the diff view's file list and header, and its loading, empty, error, binary, renamed and too-big states;
- the tool row's retry, not-run, agent and note slot;
- the markdown secondary role;
- the step timeline's preview card on a Write step;
- the stalled and background step states.

Numbers per failing shot (box, blob, ink, line shift), from `rb_gate.py evaluate`:

**part-kit_agent_strip** (24 failing)

- `kit_agent_strip_done_1280x800_dark`: box 25.07% (max 2.0), blob 2437 px (max 36), line ink 0.64x (allowed 0.90-1.11), line moved 26.5 px (max 1.75)
- `kit_agent_strip_done_1280x800_light`: box 3.72% (max 2.0), blob 113 px (max 36), line ink 0.89x (allowed 0.90-1.11), line moved 29.6 px (max 1.75)
- `kit_agent_strip_done_dark`: box 22.74% (max 2.0), blob 2118 px (max 36), line ink 0.64x (allowed 0.90-1.11), line moved 16.5 px (max 1.75)
- `kit_agent_strip_done_light`: box 2.96% (max 2.0), blob 73 px (max 36), line ink 0.89x (allowed 0.90-1.11), line moved 19.6 px (max 1.75)
- `kit_agent_strip_lead_only_1280x800_dark`: box 12.16% (max 2.0), blob 1961 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 17.4 px (max 1.75)
- `kit_agent_strip_lead_only_1280x800_light`: box 2.29% (max 2.0), blob 114 px (max 36), line moved 18.0 px (max 1.75)
- `kit_agent_strip_lead_only_dark`: box 8.77% (max 2.0), blob 1570 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 7.4 px (max 1.75)
- `kit_agent_strip_lead_only_light`: blob 54 px (max 36), line moved 8.0 px (max 1.75)
- `kit_agent_strip_mixed_1280x800_dark`: box 25.33% (max 2.0), blob 2437 px (max 36), line ink 0.64x (allowed 0.90-1.11), line moved 27.5 px (max 1.75)
- `kit_agent_strip_mixed_1280x800_light`: box 4.10% (max 2.0), blob 113 px (max 36), line ink 0.89x (allowed 0.90-1.11), line moved 29.9 px (max 1.75)
- `kit_agent_strip_mixed_dark`: box 22.69% (max 2.0), blob 2118 px (max 36), line ink 0.64x (allowed 0.90-1.11), line moved 17.5 px (max 1.75)
- `kit_agent_strip_mixed_light`: box 2.99% (max 2.0), blob 73 px (max 36), line ink 0.89x (allowed 0.90-1.11), line moved 19.9 px (max 1.75)
- `kit_agent_strip_needs_you_1280x800_dark`: box 26.06% (max 2.0), blob 2947 px (max 36), line ink 0.67x (allowed 0.90-1.11), line moved 27.3 px (max 1.75)
- `kit_agent_strip_needs_you_1280x800_light`: box 5.48% (max 2.0), blob 189 px (max 36), line moved 31.4 px (max 1.75)
- `kit_agent_strip_needs_you_dark`: box 23.46% (max 2.0), blob 2626 px (max 36), line ink 0.67x (allowed 0.90-1.11), line moved 17.3 px (max 1.75)
- `kit_agent_strip_needs_you_light`: box 4.21% (max 2.0), blob 91 px (max 36), line moved 21.4 px (max 1.75)
- `kit_agent_strip_overflow_1280x800_dark`: box 19.85% (max 2.0), blob 3234 px (max 36), line ink 0.24x (allowed 0.90-1.11), line moved 67.6 px (max 1.75)
- `kit_agent_strip_overflow_1280x800_light`: box 2.98% (max 2.0), blob 152 px (max 36), line ink 0.26x (allowed 0.90-1.11), line moved 62.4 px (max 1.75)
- `kit_agent_strip_overflow_dark`: box 22.37% (max 2.0), blob 1810 px (max 36), line ink 0.72x (allowed 0.90-1.11), line moved 3.4 px (max 1.75)
- `kit_agent_strip_overflow_light`: box 3.83% (max 2.0), blob 92 px (max 36), line moved 6.1 px (max 1.75)
- `kit_agent_strip_paused_1280x800_dark`: box 24.90% (max 2.0), blob 2411 px (max 36), line ink 0.65x (allowed 0.90-1.11), line moved 21.6 px (max 1.75)
- `kit_agent_strip_paused_1280x800_light`: box 5.07% (max 2.0), blob 113 px (max 36), line moved 25.6 px (max 1.75)
- `kit_agent_strip_paused_dark`: box 21.65% (max 2.0), blob 2096 px (max 36), line ink 0.65x (allowed 0.90-1.11), line moved 11.6 px (max 1.75)
- `kit_agent_strip_paused_light`: box 3.67% (max 2.0), blob 74 px (max 36), line moved 16.1 px (max 1.75)

**part-kit_ask_line** (18 failing)

- `kit_ask_line_ask_inline_1280x800_dark`: box 9.92% (max 2.0), blob 95 px (max 36), line moved 4.0 px (max 1.75)
- `kit_ask_line_ask_inline_1280x800_light`: box 9.83% (max 2.0), blob 82 px (max 36), line moved 7.2 px (max 1.75)
- `kit_ask_line_ask_inline_1600x1000_dark`: box 9.92% (max 2.0), blob 95 px (max 36), line moved 4.0 px (max 1.75)
- `kit_ask_line_ask_inline_1600x1000_light`: box 9.83% (max 2.0), blob 82 px (max 36), line moved 7.2 px (max 1.75)
- `kit_ask_line_ask_inline_360x800_dark`: box 8.12% (max 2.0), blob 199 px (max 36), line moved 7.7 px (max 1.75)
- `kit_ask_line_ask_inline_360x800_light`: box 7.52% (max 2.0), blob 87 px (max 36), line moved 6.5 px (max 1.75)
- `kit_ask_line_ask_inline_800x1280_dark`: box 9.92% (max 2.0), blob 95 px (max 36), line moved 4.0 px (max 1.75)
- `kit_ask_line_ask_inline_800x1280_light`: box 9.83% (max 2.0), blob 82 px (max 36), line moved 7.2 px (max 1.75)
- `kit_ask_line_ask_inline_915x412_dark`: box 9.54% (max 2.0), blob 117 px (max 36), line moved 3.7 px (max 1.75)
- `kit_ask_line_ask_inline_915x412_light`: box 9.66% (max 2.0), blob 84 px (max 36), line moved 7.0 px (max 1.75)
- `kit_ask_line_ask_inline_ar_1280x800_dark`: box 8.00% (max 2.0), blob 79 px (max 36), line moved 6.4 px (max 1.75)
- `kit_ask_line_ask_inline_ar_dark`: box 8.03% (max 2.0), blob 77 px (max 36), line moved 5.1 px (max 1.75)
- `kit_ask_line_ask_inline_dark`: box 7.02% (max 2.0), blob 105 px (max 36), line moved 15.4 px (max 1.75)
- `kit_ask_line_ask_inline_light`: box 6.46% (max 2.0), blob 87 px (max 36), line moved 12.8 px (max 1.75)
- `kit_ask_line_ask_inline_text2_1280x800_dark`: box 10.42% (max 2.0), blob 275 px (max 36), line moved 15.2 px (max 1.75)
- `kit_ask_line_ask_inline_text2_dark`: box 10.49% (max 2.0), blob 275 px (max 36), line moved 15.2 px (max 1.75)
- `kit_ask_line_ask_stacked_dark`: box 3.01% (max 2.0), blob 141 px (max 36), line moved 15.2 px (max 1.75)
- `kit_ask_line_ask_stacked_light`: box 2.85% (max 2.0), blob 124 px (max 36), line moved 15.2 px (max 1.75)

**part-kit_composer** (16 failing)

- `kit_composer_busy_text_text2_dark`: box 4.85% (max 2.0), line moved 2.5 px (max 1.75)
- `kit_composer_busy_text_text2_light`: box 4.59% (max 2.0), line moved 2.9 px (max 1.75)
- `kit_composer_idle_text_dark`: blob 70 px (max 36), line ink 0.83x (allowed 0.90-1.11), line moved 6.9 px (max 1.75)
- `kit_composer_idle_text_light`: blob 3126 px (max 36), line moved 8.3 px (max 1.75)
- `kit_composer_model_above_dark`: blob 535 px (max 36), line moved 4.6 px (max 1.75)
- `kit_composer_model_above_light`: blob 61 px (max 36), line ink 1.11x (allowed 0.90-1.11), line moved 4.4 px (max 1.75)
- `kit_composer_read_only_dark`: blob 123 px (max 36), line ink 7.11x (allowed 0.90-1.11), line moved 32.8 px (max 1.75)
- `kit_composer_read_only_light`: blob 99 px (max 36), line ink 7.93x (allowed 0.90-1.11), line moved 37.5 px (max 1.75)
- `kit_composer_suggestions_dark`: blob 230 px (max 36), line ink 0.43x (allowed 0.90-1.11), line moved 47.4 px (max 1.75)
- `kit_composer_suggestions_light`: blob 162 px (max 36), line ink 0.41x (allowed 0.90-1.11), line moved 42.1 px (max 1.75)
- `kit_composer_voice_listening_dark`: blob 1288 px (max 36), line ink 0.65x (allowed 0.90-1.11), line moved 61.5 px (max 1.75)
- `kit_composer_voice_listening_light`: box 3.64% (max 2.0), blob 4435 px (max 36), line ink 2.03x (allowed 0.90-1.11), line moved 50.7 px (max 1.75)
- `kit_composer_voice_mic_denied_dark`: blob 152 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 31.1 px (max 1.75)
- `kit_composer_voice_mic_denied_light`: blob 133 px (max 36), line ink 1.53x (allowed 0.90-1.11), line moved 29.5 px (max 1.75)
- `kit_composer_voice_speaking_dark`: blob 1377 px (max 36), line ink 0.58x (allowed 0.90-1.11), line moved 71.0 px (max 1.75)
- `kit_composer_voice_speaking_light`: box 3.58% (max 2.0), blob 4403 px (max 36), line ink 0.45x (allowed 0.90-1.11), line moved 74.3 px (max 1.75)

**part-kit_composer_chips** (14 failing)

- `kit_composer_chips_attachments_dark`: box 4.83% (max 2.0), blob 880 px (max 36), line ink 0.80x (allowed 0.90-1.11), line moved 16.9 px (max 1.75)
- `kit_composer_chips_attachments_light`: box 13.27% (max 2.0), blob 2775 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 37.4 px (max 1.75)
- `kit_composer_chips_attachments_read_only_dark`: box 9.07% (max 2.0), blob 3440 px (max 36), line ink 0.00x (allowed 0.90-1.11), line moved 33.8 px (max 1.75)
- `kit_composer_chips_attachments_read_only_light`: box 20.01% (max 2.0), blob 7407 px (max 36), line ink 0.00x (allowed 0.90-1.11), line moved 48.8 px (max 1.75)
- `kit_composer_chips_default_1280x800_dark`: box 13.31% (max 2.0), blob 2100 px (max 36), line ink 0.75x (allowed 0.90-1.11), line moved 20.8 px (max 1.75)
- `kit_composer_chips_default_1280x800_light`: box 13.73% (max 2.0), blob 2558 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 55.9 px (max 1.75)
- `kit_composer_chips_default_dark`: box 4.69% (max 2.0), blob 880 px (max 36), line ink 0.80x (allowed 0.90-1.11), line moved 16.9 px (max 1.75)
- `kit_composer_chips_default_light`: box 12.22% (max 2.0), blob 2775 px (max 36), line ink 0.63x (allowed 0.90-1.11), line moved 37.4 px (max 1.75)
- `kit_composer_chips_default_text2_1280x800_dark`: box 20.36% (max 2.0), blob 14242 px (max 36), line ink 0.00x (allowed 0.90-1.11), line moved 60.5 px (max 1.75)
- `kit_composer_chips_default_text2_1280x800_light`: box 19.91% (max 2.0), blob 11318 px (max 36), line ink 0.00x (allowed 0.90-1.11), line moved 61.2 px (max 1.75)
- `kit_composer_chips_default_text2_dark`: box 6.64% (max 2.0), blob 808 px (max 36), line ink 0.85x (allowed 0.90-1.11), line moved 26.6 px (max 1.75)
- `kit_composer_chips_default_text2_light`: box 12.50% (max 2.0), blob 4354 px (max 36), line moved 30.2 px (max 1.75)
- `kit_composer_chips_suggestions_dark`: box 9.45% (max 2.0), blob 163 px (max 36), line ink 3.61x (allowed 0.90-1.11), line moved 54.2 px (max 1.75)
- `kit_composer_chips_suggestions_light`: box 8.50% (max 2.0), blob 127 px (max 36), line ink 3.34x (allowed 0.90-1.11), line moved 49.3 px (max 1.75)

**part-kit_diff_view** (36 failing)

- `kit_diff_view_binary_renamed_1280x800_dark`: box 9.49% (max 3.0), blob 17482 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 146.7 px (max 1.75)
- `kit_diff_view_binary_renamed_1280x800_light`: blob 116 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 170.5 px (max 1.75)
- `kit_diff_view_binary_renamed_dark`: box 5.69% (max 3.0), blob 120 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 15.7 px (max 1.75)
- `kit_diff_view_binary_renamed_light`: box 4.96% (max 3.0), blob 103 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 21.8 px (max 1.75)
- `kit_diff_view_empty_1280x800_dark`: box 18.46% (max 3.0), blob 668 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 56.2 px (max 1.75)
- `kit_diff_view_empty_1280x800_light`: box 12.71% (max 3.0), blob 116 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 55.7 px (max 1.75)
- `kit_diff_view_empty_dark`: box 18.46% (max 3.0), blob 668 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 56.2 px (max 1.75)
- `kit_diff_view_empty_light`: box 12.71% (max 3.0), blob 116 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 55.7 px (max 1.75)
- `kit_diff_view_error_1280x800_dark`: box 12.54% (max 3.0), blob 4589 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 56.2 px (max 1.75)
- `kit_diff_view_error_1280x800_light`: box 11.17% (max 3.0), blob 4491 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 55.7 px (max 1.75)
- `kit_diff_view_error_dark`: box 37.01% (max 3.0), blob 19522 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 56.2 px (max 1.75)
- `kit_diff_view_error_light`: box 35.74% (max 3.0), blob 19430 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 55.7 px (max 1.75)
- `kit_diff_view_json_1280x800_dark`: box 8.05% (max 3.0), blob 14367 px (max 49), line ink 4.74x (allowed 0.88-1.14), line moved 152.7 px (max 1.75)
- `kit_diff_view_json_1280x800_light`: box 22.39% (max 3.0), blob 38068 px (max 49), line ink 0.12x (allowed 0.88-1.14), line moved 417.5 px (max 1.75)
- `kit_diff_view_json_dark`: box 12.15% (max 3.0), blob 2593 px (max 49), line ink 6.98x (allowed 0.88-1.14), line moved 135.6 px (max 1.75)
- `kit_diff_view_json_light`: box 10.88% (max 3.0), blob 2711 px (max 49), line ink 3.41x (allowed 0.88-1.14), line moved 134.4 px (max 1.75)
- `kit_diff_view_loading_1280x800_dark`: box 11.02% (max 3.0), blob 6966 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 343.8 px (max 1.75)
- `kit_diff_view_loading_1280x800_light`: box 11.01% (max 3.0), blob 6966 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 344.3 px (max 1.75)
- `kit_diff_view_loading_dark`: box 13.04% (max 3.0), blob 2626 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 66.8 px (max 1.75)
- `kit_diff_view_loading_light`: box 12.97% (max 3.0), blob 2625 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 67.3 px (max 1.75)
- `kit_diff_view_selecting_1280x800_dark`: box 11.90% (max 3.0), blob 61811 px (max 49), line ink 287.68x (allowed 0.88-1.14), line moved 225.1 px (max 1.75)
- `kit_diff_view_selecting_1280x800_light`: box 20.34% (max 3.0), blob 110318 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 659.3 px (max 1.75)
- `kit_diff_view_selecting_dark`: box 30.75% (max 3.0), blob 28624 px (max 49), line ink 0.06x (allowed 0.88-1.14), line moved 156.6 px (max 1.75)
- `kit_diff_view_selecting_light`: box 31.02% (max 3.0), blob 49359 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 173.1 px (max 1.75)
- `kit_diff_view_too_big_1280x800_dark`: box 13.74% (max 3.0), blob 78975 px (max 49), line ink 0.56x (allowed 0.88-1.14), line moved 380.6 px (max 1.75)
- `kit_diff_view_too_big_1280x800_light`: box 39.43% (max 3.0), blob 363519 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 328.1 px (max 1.75)
- `kit_diff_view_too_big_dark`: box 36.49% (max 3.0), blob 100304 px (max 49), line ink 1.38x (allowed 0.88-1.14), line moved 20.2 px (max 1.75)
- `kit_diff_view_too_big_light`: box 12.14% (max 3.0), blob 3443 px (max 49), line ink 0.51x (allowed 0.88-1.14), line moved 27.5 px (max 1.75)
- `kit_diff_view_unified_1280x800_dark`: box 11.94% (max 3.0), blob 17496 px (max 49), line ink 1.94x (allowed 0.88-1.14), line moved 225.1 px (max 1.75)
- `kit_diff_view_unified_1280x800_light`: box 23.69% (max 3.0), blob 29748 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 659.3 px (max 1.75)
- `kit_diff_view_unified_dark`: box 31.52% (max 3.0), blob 15642 px (max 49), line ink 9.89x (allowed 0.88-1.14), line moved 162.3 px (max 1.75)
- `kit_diff_view_unified_light`: box 24.16% (max 3.0), blob 15376 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 173.1 px (max 1.75)
- `kit_diff_view_unified_text2_1280x800_dark`: box 25.39% (max 3.0), blob 95880 px (max 49), line ink 81.74x (allowed 0.88-1.14), line moved 279.1 px (max 1.75)
- `kit_diff_view_unified_text2_1280x800_light`: box 28.01% (max 3.0), blob 98550 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 647.7 px (max 1.75)
- `kit_diff_view_unified_text2_dark`: box 46.83% (max 3.0), blob 123817 px (max 49), line ink 15.03x (allowed 0.88-1.14), line moved 202.7 px (max 1.75)
- `kit_diff_view_unified_text2_light`: box 23.42% (max 3.0), blob 30663 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 196.0 px (max 1.75)

**part-kit_markdown** (26 failing)

- `kit_markdown_default_1280x800_dark`: blob 108 px (max 49), line moved 43.9 px (max 1.75)
- `kit_markdown_default_1280x800_light`: blob 108 px (max 49), line ink 0.76x (allowed 0.88-1.14), line moved 133.8 px (max 1.75)
- `kit_markdown_default_dark`: blob 751 px (max 49), line ink 5.06x (allowed 0.88-1.14), line moved 43.8 px (max 1.75)
- `kit_markdown_default_light`: blob 751 px (max 49), line ink 1309.75x (allowed 0.88-1.14), line moved 349.6 px (max 1.75)
- `kit_markdown_default_text2_dark`: box 10.85% (max 3.0), blob 533 px (max 49), line moved 12.0 px (max 1.75)
- `kit_markdown_default_text2_light`: box 10.07% (max 3.0), blob 291 px (max 49), line moved 12.0 px (max 1.75)
- `kit_markdown_images_inline_dark`: box 23.34% (max 3.0), blob 1101 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 62.8 px (max 1.75)
- `kit_markdown_images_inline_light`: box 20.69% (max 3.0), blob 1173 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 59.6 px (max 1.75)
- `kit_markdown_images_reply_ar_dark`: box 5.56% (max 3.0), blob 2260 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 11.4 px (max 1.75)
- `kit_markdown_images_reply_ar_light`: box 7.42% (max 3.0), blob 6627 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 10.6 px (max 1.75)
- `kit_markdown_images_reply_dark`: box 3.63% (max 3.0), blob 277 px (max 49), line ink 0.53x (allowed 0.88-1.14), line moved 44.5 px (max 1.75)
- `kit_markdown_images_reply_light`: box 8.38% (max 3.0), blob 7049 px (max 49), line ink 0.68x (allowed 0.88-1.14), line moved 40.8 px (max 1.75)
- `kit_markdown_images_unread_dark`: box 5.71% (max 3.0), blob 347 px (max 49), line ink 0.58x (allowed 0.88-1.14), line moved 6.5 px (max 1.75)
- `kit_markdown_images_unread_light`: box 26.35% (max 3.0), blob 4084 px (max 49), line ink 0.76x (allowed 0.88-1.14), line moved 7.0 px (max 1.75)
- `kit_markdown_path_link_1280x800_dark`: box 8.84% (max 3.0), blob 420 px (max 49), line ink 0.75x (allowed 0.88-1.14), line moved 39.9 px (max 1.75)
- `kit_markdown_path_link_1280x800_light`: box 8.77% (max 3.0), blob 350 px (max 49), line ink 0.75x (allowed 0.88-1.14), line moved 39.8 px (max 1.75)
- `kit_markdown_path_link_dark`: box 15.26% (max 3.0), blob 940 px (max 49), line ink 1.46x (allowed 0.88-1.14), line moved 73.6 px (max 1.75)
- `kit_markdown_path_link_light`: box 13.27% (max 3.0), blob 882 px (max 49), line ink 1.55x (allowed 0.88-1.14), line moved 67.2 px (max 1.75)
- `kit_markdown_secondary_1280x800_dark`: box 20.70% (max 3.0), blob 345 px (max 49), line ink 2.67x (allowed 0.88-1.14), line moved 26.2 px (max 1.75)
- `kit_markdown_secondary_1280x800_light`: box 17.69% (max 3.0), blob 118 px (max 49), line ink 2.01x (allowed 0.88-1.14), line moved 26.3 px (max 1.75)
- `kit_markdown_secondary_dark`: box 19.82% (max 3.0), blob 347 px (max 49), line ink 252705.74x (allowed 0.88-1.14), line moved 47.6 px (max 1.75)
- `kit_markdown_secondary_light`: box 16.97% (max 3.0), blob 118 px (max 49), line ink 160370.35x (allowed 0.88-1.14), line moved 47.8 px (max 1.75)
- `kit_markdown_streaming_1280x800_dark`: blob 108 px (max 49), line moved 33.2 px (max 1.75)
- `kit_markdown_streaming_1280x800_light`: blob 108 px (max 49), line ink 0.85x (allowed 0.88-1.14), line moved 77.7 px (max 1.75)
- `kit_markdown_streaming_dark`: blob 557 px (max 49), line ink 2.83x (allowed 0.88-1.14), line moved 76.0 px (max 1.75)
- `kit_markdown_streaming_light`: blob 558 px (max 49), line ink 8.88x (allowed 0.88-1.14), line moved 241.4 px (max 1.75)

**part-kit_message** (26 failing)

- `kit_message_default_1280x800_dark`: box 7.46% (max 3.0), blob 66 px (max 49), line moved 608.2 px (max 1.75)
- `kit_message_default_1280x800_light`: box 7.35% (max 3.0), blob 65 px (max 49), line moved 608.3 px (max 1.75)
- `kit_message_default_dark`: blob 66 px (max 49), line moved 320.2 px (max 1.75)
- `kit_message_default_light`: blob 65 px (max 49), line moved 320.3 px (max 1.75)
- `kit_message_default_text2_1280x800_dark`: box 8.54% (max 3.0), blob 226 px (max 49), line moved 556.3 px (max 1.75)
- `kit_message_default_text2_1280x800_light`: box 8.00% (max 3.0), blob 215 px (max 49), line moved 556.4 px (max 1.75)
- `kit_message_default_text2_dark`: box 8.19% (max 3.0), blob 273 px (max 49), line moved 268.3 px (max 1.75)
- `kit_message_default_text2_light`: box 7.61% (max 3.0), blob 243 px (max 49), line moved 268.4 px (max 1.75)
- `kit_message_marker_dark`: box 5.47% (max 3.0), blob 221 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 194.3 px (max 1.75)
- `kit_message_marker_light`: box 4.84% (max 3.0), blob 159 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 194.2 px (max 1.75)
- `kit_message_notice_dark`: box 10.65% (max 3.0), blob 232 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 124.3 px (max 1.75)
- `kit_message_notice_failed_dark`: box 10.64% (max 3.0), blob 130 px (max 49), line moved 13.5 px (max 1.75)
- `kit_message_notice_failed_light`: box 8.51% (max 3.0), blob 81 px (max 49), line moved 13.5 px (max 1.75)
- `kit_message_notice_light`: box 9.48% (max 3.0), blob 160 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 110.3 px (max 1.75)
- `kit_message_prompt_attachments_dark`: box 14.32% (max 3.0), blob 4939 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 161.2 px (max 1.75)
- `kit_message_prompt_attachments_light`: box 5.66% (max 3.0), blob 173 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 173.8 px (max 1.75)
- `kit_message_prompt_dark`: box 5.19% (max 3.0), blob 66 px (max 49), line moved 320.2 px (max 1.75)
- `kit_message_prompt_light`: box 5.21% (max 3.0), blob 65 px (max 49), line moved 320.3 px (max 1.75)
- `kit_message_prompt_long_dark`: blob 60 px (max 49), line moved 7.5 px (max 1.75)
- `kit_message_prompt_long_light`: blob 55 px (max 49), line moved 7.1 px (max 1.75)
- `kit_message_reply_code_dark`: box 6.00% (max 3.0), blob 679 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 64.4 px (max 1.75)
- `kit_message_reply_code_light`: box 5.40% (max 3.0), blob 665 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 346.3 px (max 1.75)
- `kit_message_thought_folded_dark`: box 9.32% (max 3.0), blob 219 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 121.7 px (max 1.75)
- `kit_message_thought_folded_light`: box 8.68% (max 3.0), blob 161 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 121.9 px (max 1.75)
- `kit_message_thought_open_dark`: box 17.91% (max 3.0), blob 219 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 154.6 px (max 1.75)
- `kit_message_thought_open_light`: box 15.90% (max 3.0), blob 161 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 154.9 px (max 1.75)

**part-kit_queued_message** (24 failing)

- `kit_queued_message_after_reply_dark`: blob 60 px (max 49), line moved 4.3 px (max 1.75)
- `kit_queued_message_after_reply_light`: blob 51 px (max 49), line moved 3.5 px (max 1.75)
- `kit_queued_message_default_1280x800_dark`: blob 94 px (max 49), line moved 5.0 px (max 1.75)
- `kit_queued_message_default_1280x800_light`: blob 67 px (max 49), line moved 4.3 px (max 1.75)
- `kit_queued_message_default_dark`: blob 123 px (max 49), line moved 7.1 px (max 1.75)
- `kit_queued_message_default_light`: blob 67 px (max 49), line moved 6.1 px (max 1.75)
- `kit_queued_message_default_text2_1280x800_dark`: box 3.06% (max 3.0), blob 236 px (max 49), line moved 8.0 px (max 1.75)
- `kit_queued_message_default_text2_1280x800_light`: blob 233 px (max 49), line moved 7.3 px (max 1.75)
- `kit_queued_message_default_text2_dark`: box 3.58% (max 3.0), blob 231 px (max 49), line moved 11.8 px (max 1.75)
- `kit_queued_message_default_text2_light`: box 3.32% (max 3.0), blob 227 px (max 49), line moved 11.7 px (max 1.75)
- `kit_queued_message_failed_dark`: blob 74 px (max 49), line ink 0.66x (allowed 0.88-1.14), line moved 6.6 px (max 1.75)
- `kit_queued_message_failed_light`: blob 60 px (max 49), line ink 0.88x (allowed 0.88-1.14), line moved 5.5 px (max 1.75)
- `kit_queued_message_mixed_dark`: line ink 0.67x (allowed 0.88-1.14), line moved 11.9 px (max 1.75)
- `kit_queued_message_mixed_light`: line ink 0.88x (allowed 0.88-1.14), line moved 4.0 px (max 1.75)
- `kit_queued_message_not_confirmed_dark`: line ink 0.74x (allowed 0.88-1.14), line moved 11.9 px (max 1.75)
- `kit_queued_message_not_confirmed_light`: line moved 4.0 px (max 1.75)
- `kit_queued_message_offline_dark`: blob 53 px (max 49), line moved 2.0 px (max 1.75)
- `kit_queued_message_offline_light`: blob 52 px (max 49), line moved 2.3 px (max 1.75)
- `kit_queued_receipt_checking_dark`: blob 74 px (max 49), line moved 2.2 px (max 1.75)
- `kit_queued_receipt_checking_light`: blob 74 px (max 49), line moved 2.7 px (max 1.75)
- `kit_queued_receipt_uncertain_checked_dark`: line ink 0.66x (allowed 0.88-1.14), line moved 12.3 px (max 1.75)
- `kit_queued_receipt_uncertain_checked_light`: line ink 0.87x (allowed 0.88-1.14), line moved 4.3 px (max 1.75)
- `kit_queued_receipt_uncertain_dark`: line ink 0.66x (allowed 0.88-1.14), line moved 3.2 px (max 1.75)
- `kit_queued_receipt_uncertain_light`: line ink 0.88x (allowed 0.88-1.14), line moved 2.5 px (max 1.75)

**part-kit_tool_row** (26 failing)

- `kit_tool_row_agent_dark`: box 10.85% (max 3.0), blob 353 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 37.8 px (max 1.75)
- `kit_tool_row_agent_done_dark`: box 9.58% (max 3.0), blob 353 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 23.9 px (max 1.75)
- `kit_tool_row_agent_done_light`: box 8.48% (max 3.0), blob 253 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 24.3 px (max 1.75)
- `kit_tool_row_agent_light`: box 9.48% (max 3.0), blob 253 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 40.4 px (max 1.75)
- `kit_tool_row_default_1280x800_dark`: box 4.14% (max 3.0), blob 221 px (max 49), line moved 10.1 px (max 1.75)
- `kit_tool_row_default_1280x800_light`: box 3.48% (max 3.0), blob 172 px (max 49), line moved 10.2 px (max 1.75)
- `kit_tool_row_default_text2_1280x800_dark`: box 9.17% (max 3.0), blob 237 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 249.2 px (max 1.75)
- `kit_tool_row_default_text2_1280x800_light`: box 8.38% (max 3.0), blob 208 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 251.7 px (max 1.75)
- `kit_tool_row_default_text2_dark`: box 10.83% (max 3.0), blob 288 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 60.1 px (max 1.75)
- `kit_tool_row_default_text2_light`: box 9.94% (max 3.0), blob 224 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 62.8 px (max 1.75)
- `kit_tool_row_done_dark`: box 8.20% (max 3.0), blob 353 px (max 49), line moved 28.9 px (max 1.75)
- `kit_tool_row_done_light`: box 7.01% (max 3.0), blob 172 px (max 49), line moved 35.4 px (max 1.75)
- `kit_tool_row_done_open_dark`: box 7.96% (max 3.0), blob 221 px (max 49), line ink 0.22x (allowed 0.88-1.14), line moved 82.2 px (max 1.75)
- `kit_tool_row_done_open_light`: box 7.08% (max 3.0), blob 172 px (max 49), line ink 333.26x (allowed 0.88-1.14), line moved 114.5 px (max 1.75)
- `kit_tool_row_edit_open_dark`: box 8.00% (max 3.0), blob 353 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 74.5 px (max 1.75)
- `kit_tool_row_edit_open_light`: box 7.09% (max 3.0), blob 253 px (max 49), line ink 191110.65x (allowed 0.88-1.14), line moved 74.0 px (max 1.75)
- `kit_tool_row_failed_dark`: box 8.83% (max 3.0), blob 221 px (max 49), line moved 30.4 px (max 1.75)
- `kit_tool_row_failed_light`: box 7.48% (max 3.0), blob 172 px (max 49), line ink 1.21x (allowed 0.88-1.14), line moved 24.4 px (max 1.75)
- `kit_tool_row_failed_retry_dark`: box 10.96% (max 3.0), blob 353 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 98.4 px (max 1.75)
- `kit_tool_row_failed_retry_light`: box 9.34% (max 3.0), blob 251 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 98.4 px (max 1.75)
- `kit_tool_row_not_run_dark`: box 8.29% (max 3.0), blob 221 px (max 49), line moved 10.2 px (max 1.75)
- `kit_tool_row_not_run_light`: box 7.07% (max 3.0), blob 172 px (max 49), line moved 10.2 px (max 1.75)
- `kit_tool_row_running_dark`: box 8.30% (max 3.0), blob 314 px (max 49), line moved 31.5 px (max 1.75)
- `kit_tool_row_running_light`: box 7.00% (max 3.0), blob 172 px (max 49), line ink 1.14x (allowed 0.88-1.14), line moved 39.1 px (max 1.75)
- `kit_tool_row_waiting_for_you_dark`: box 8.54% (max 3.0), blob 228 px (max 49), line ink 1.15x (allowed 0.88-1.14), line moved 24.8 px (max 1.75)
- `kit_tool_row_waiting_for_you_light`: box 7.26% (max 3.0), blob 172 px (max 49), line ink 1.18x (allowed 0.88-1.14), line moved 28.3 px (max 1.75)

**part-kit_turn** (28 failing)

- `kit_turn_default_1280x800_dark`: box 4.82% (max 3.0), line moved 4.3 px (max 1.75)
- `kit_turn_default_1280x800_light`: box 4.72% (max 3.0), line moved 10.5 px (max 1.75)
- `kit_turn_default_text2_1280x800_dark`: box 6.16% (max 3.0), blob 210 px (max 49), line moved 42.8 px (max 1.75)
- `kit_turn_default_text2_1280x800_light`: box 5.79% (max 3.0), blob 187 px (max 49), line moved 41.1 px (max 1.75)
- `kit_turn_default_text2_dark`: box 6.77% (max 3.0), blob 273 px (max 49), line moved 22.5 px (max 1.75)
- `kit_turn_default_text2_light`: box 6.32% (max 3.0), blob 243 px (max 49), line moved 21.8 px (max 1.75)
- `kit_turn_failed_dark`: box 3.03% (max 3.0), blob 727 px (max 49), line ink 0.65x (allowed 0.88-1.14), line moved 17.8 px (max 1.75)
- `kit_turn_failed_light`: blob 60 px (max 49), line ink 0.66x (allowed 0.88-1.14), line moved 25.9 px (max 1.75)
- `kit_turn_finished_dark`: line moved 5.8 px (max 1.75)
- `kit_turn_finished_latest_dark`: line moved 5.8 px (max 1.75)
- `kit_turn_finished_latest_light`: line moved 5.7 px (max 1.75)
- `kit_turn_finished_light`: line moved 5.7 px (max 1.75)
- `kit_turn_highlighted_dark`: line ink 0.25x (allowed 0.88-1.14), line moved 107.3 px (max 1.75)
- `kit_turn_highlighted_light`: line ink 0.32x (allowed 0.88-1.14), line moved 105.1 px (max 1.75)
- `kit_turn_interrupted_dark`: line moved 5.8 px (max 1.75)
- `kit_turn_interrupted_light`: line moved 5.7 px (max 1.75)
- `kit_turn_running_dark`: blob 124 px (max 49), line moved 9.4 px (max 1.75)
- `kit_turn_running_light`: blob 124 px (max 49), line moved 10.6 px (max 1.75)
- `kit_turn_running_live_dark`: blob 124 px (max 49), line ink 0.79x (allowed 0.88-1.14), line moved 9.4 px (max 1.75)
- `kit_turn_running_live_light`: blob 124 px (max 49), line ink 0.75x (allowed 0.88-1.14), line moved 10.6 px (max 1.75)
- `kit_turn_starting_dark`: box 3.65% (max 3.0), line moved 5.8 px (max 1.75)
- `kit_turn_starting_light`: box 3.70% (max 3.0), line moved 5.7 px (max 1.75)
- `kit_turn_starting_slow_dark`: box 3.65% (max 3.0), line moved 5.8 px (max 1.75)
- `kit_turn_starting_slow_light`: box 3.70% (max 3.0), line moved 5.7 px (max 1.75)
- `kit_turn_stopped_dark`: line moved 5.8 px (max 1.75)
- `kit_turn_stopped_light`: line moved 5.7 px (max 1.75)
- `kit_turn_waiting_for_you_dark`: box 33.71% (max 3.0), blob 45479 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 53.7 px (max 1.75)
- `kit_turn_waiting_for_you_light`: box 23.62% (max 3.0), blob 31145 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 61.5 px (max 1.75)

**part-kit_step_timeline** (15 failing)

- `kit_step_timeline_collapsed_dark`: line moved 2.2 px (max 1.75)
- `kit_step_timeline_expanded_1280x800_dark`: box 15.14% (max 3.0), blob 42014 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 105.1 px (max 1.75)
- `kit_step_timeline_expanded_1280x800_light`: blob 172 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 332.6 px (max 1.75)
- `kit_step_timeline_expanded_ar_dark`: box 16.89% (max 3.0), blob 22422 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 50.9 px (max 1.75)
- `kit_step_timeline_expanded_ar_light`: box 5.93% (max 3.0), blob 128 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 110.6 px (max 1.75)
- `kit_step_timeline_expanded_dark`: box 15.04% (max 3.0), blob 22235 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 36.1 px (max 1.75)
- `kit_step_timeline_expanded_light`: box 4.25% (max 3.0), blob 172 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 178.1 px (max 1.75)
- `kit_step_timeline_expanded_text2_1280x800_dark`: box 17.25% (max 3.0), blob 84891 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 75.5 px (max 1.75)
- `kit_step_timeline_expanded_text2_1280x800_light`: box 3.64% (max 3.0), blob 328 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 76.8 px (max 1.75)
- `kit_step_timeline_expanded_text2_dark`: box 16.41% (max 3.0), blob 45820 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 84.6 px (max 1.75)
- `kit_step_timeline_expanded_text2_light`: box 5.36% (max 3.0), blob 349 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 55.4 px (max 1.75)
- `kit_step_timeline_running_dark`: blob 72 px (max 49), line moved 9.8 px (max 1.75)
- `kit_step_timeline_running_light`: blob 68 px (max 49), line moved 9.3 px (max 1.75)
- `kit_step_timeline_running_open_dark`: box 15.49% (max 3.0), blob 22541 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 44.1 px (max 1.75)
- `kit_step_timeline_running_open_light`: box 4.88% (max 3.0), blob 172 px (max 49), line ink 0.00x (allowed 0.88-1.14), line moved 178.1 px (max 1.75)

**part-kit_work_line** (10 failing)

- `kit_work_line_default_text2_1280x800_dark`: box 3.98% (max 3.0), line moved 2.0 px (max 1.75)
- `kit_work_line_default_text2_1280x800_light`: box 3.67% (max 3.0), line moved 2.0 px (max 1.75)
- `kit_work_line_default_text2_dark`: box 4.51% (max 3.0), line moved 2.0 px (max 1.75)
- `kit_work_line_default_text2_light`: box 4.16% (max 3.0), line moved 2.0 px (max 1.75)
- `kit_work_line_done_expanded_dark`: blob 134 px (max 49), line moved 2.0 px (max 1.75)
- `kit_work_line_done_expanded_light`: blob 134 px (max 49), line moved 3.4 px (max 1.75)
- `kit_work_line_ended_failed_dark`: blob 166 px (max 49), line moved 2.2 px (max 1.75)
- `kit_work_line_ended_failed_light`: blob 166 px (max 49), line moved 3.8 px (max 1.75)
- `kit_work_line_running_dark`: line moved 2.6 px (max 1.75)
- `kit_work_line_running_light`: blob 64 px (max 49), line moved 2.5 px (max 1.75)

**part-kit_composer_status_strip** (6 failing)

- `kit_composerstatusstrip_stop_ar_text2_360x800_dark`: box 45.62% (max 2.0), blob 9954 px (max 36), line ink 2.00x (allowed 0.90-1.11), line moved 42.7 px (max 1.75)
- `kit_composerstatusstrip_stop_stuck_ar_text2_360x800_dark`: box 45.62% (max 2.0), blob 9954 px (max 36), line ink 1.94x (allowed 0.90-1.11), line moved 42.8 px (max 1.75)
- `kit_composerstatusstrip_stop_stuck_text2_360x800_dark`: box 45.11% (max 2.0), blob 6491 px (max 36), line ink 1.78x (allowed 0.90-1.11), line moved 22.0 px (max 1.75)
- `kit_composerstatusstrip_stop_text2_360x800_dark`: box 45.08% (max 2.0), blob 6491 px (max 36), line ink 1.83x (allowed 0.90-1.11), line moved 22.0 px (max 1.75)
- `kit_composerstatusstrip_wrapped_ar_text2_360x800_dark`: box 50.89% (max 2.0), blob 9021 px (max 36), line ink 2.46x (allowed 0.90-1.11), line moved 51.1 px (max 1.75)
- `kit_composerstatusstrip_wrapped_text2_360x800_dark`: box 50.56% (max 2.0), blob 8846 px (max 36), line ink 2.45x (allowed 0.90-1.11), line moved 51.8 px (max 1.75)

**part-kit_markdown_table** (4 failing)

- `kit_markdown_table_1280x800_dark`: line ink 2.83x (allowed 0.88-1.14)
- `kit_markdown_table_1280x800_light`: line ink 3.17x (allowed 0.88-1.14)
- `kit_markdown_table_dark`: line ink 2.83x (allowed 0.88-1.14)
- `kit_markdown_table_light`: line ink 3.17x (allowed 0.88-1.14)

**part-kit_transcript_excerpt** (6 failing)

- `kit_transcript_excerpt_loading_1280x800_dark`: box 9.10% (max 3.0), blob 288 px (max 49), line ink 611220.14x (allowed 0.88-1.14), line moved 84.4 px (max 1.75)
- `kit_transcript_excerpt_loading_1280x800_light`: box 8.00% (max 3.0), blob 194 px (max 49), line ink 390349.88x (allowed 0.88-1.14), line moved 93.8 px (max 1.75)
- `kit_transcript_excerpt_loading_dark`: box 13.35% (max 3.0), blob 288 px (max 49), line ink 304964.08x (allowed 0.88-1.14), line moved 123.6 px (max 1.75)
- `kit_transcript_excerpt_loading_light`: box 11.68% (max 3.0), blob 194 px (max 49), line ink 194753.08x (allowed 0.88-1.14), line moved 116.8 px (max 1.75)
- `kit_transcript_excerpt_loading_text2_1280x800_dark`: box 13.37% (max 3.0), blob 399 px (max 49), line ink 110579.85x (allowed 0.88-1.14), line moved 14.0 px (max 1.75)
- `kit_transcript_excerpt_loading_text2_dark`: box 20.56% (max 3.0), blob 440 px (max 49), line ink 25.02x (allowed 0.88-1.14), line moved 84.3 px (max 1.75)

**part-kit_step_light** (28 failing)

- `kit_step_glow_background_t40_dark`: line moved 14.2 px (max 2.5)
- `kit_step_glow_background_t40_light`: line ink 0.22x (allowed 0.75-1.33), line moved 41.2 px (max 2.5)
- `kit_step_glow_fail_flash_dark`: blob 361 px (max 100)
- `kit_step_glow_fail_flash_light`: blob 187 px (max 100), line moved 3.0 px (max 2.5)
- `kit_step_glow_finish_tick_light`: line moved 4.6 px (max 2.5)
- `kit_step_glow_first_t40_dark`: line moved 3.7 px (max 2.5)
- `kit_step_glow_first_t40_light`: line ink 0.19x (allowed 0.75-1.33), line moved 2.9 px (max 2.5)
- `kit_step_glow_last_t40_dark`: line moved 3.9 px (max 2.5)
- `kit_step_glow_last_t40_light`: line ink 0.00x (allowed 0.75-1.33), line moved 4.7 px (max 2.5)
- `kit_step_glow_queued_light`: line moved 5.8 px (max 2.5)
- `kit_step_glow_reduced_dark`: blob 118 px (max 100), line moved 3.4 px (max 2.5)
- `kit_step_glow_reduced_light`: blob 119 px (max 100), line moved 8.4 px (max 2.5)
- `kit_step_glow_running_t10_light`: line ink 0.31x (allowed 0.75-1.33)
- `kit_step_glow_running_t40_ar_text2_dark`: box 7.22% (max 4.0), blob 377 px (max 100), line moved 11.2 px (max 2.5)
- `kit_step_glow_running_t40_ar_text2_light`: box 7.16% (max 4.0), blob 274 px (max 100), line moved 10.4 px (max 2.5)
- `kit_step_glow_running_t40_dark`: line moved 2.7 px (max 2.5)
- `kit_step_glow_running_t40_light`: line ink 0.17x (allowed 0.75-1.33)
- `kit_step_glow_running_t55_dark`: blob 127 px (max 100), line moved 4.8 px (max 2.5)
- `kit_step_glow_running_t55_light`: blob 128 px (max 100), line ink 0.72x (allowed 0.75-1.33), line moved 13.2 px (max 2.5)
- `kit_step_glow_running_t80_dark`: blob 124 px (max 100), line moved 3.5 px (max 2.5)
- `kit_step_glow_running_t80_light`: blob 126 px (max 100), line moved 9.7 px (max 2.5)
- `kit_step_glow_stalled_light`: line ink 0.24x (allowed 0.75-1.33)
- `kit_step_glow_stop_fade_dark`: blob 124 px (max 100), line moved 3.3 px (max 2.5)
- `kit_step_glow_stop_fade_light`: blob 126 px (max 100), line moved 10.1 px (max 2.5)
- `kit_step_glow_waiting_breathe_dark`: blob 104 px (max 100)
- `kit_step_glow_waiting_breathe_light`: blob 104 px (max 100), line moved 6.7 px (max 2.5)
- `kit_step_glow_waiting_dark`: line moved 3.7 px (max 2.5)
- `kit_step_glow_waiting_light`: line moved 10.4 px (max 2.5)

## Layout guard and alignment findings (not baselined)

The layout guard raised 190 findings over these shots (117 phone, 73 tablet):

| Rule | Findings |
|---|---:|
| edge-text | 62 |
| edge-tap | 38 |
| ellipsis | 35 |
| small-target | 30 |
| tight | 22 |
| overlap | 2 |
| wide-control | 1 |

By part:

- **Diff view: 126.** Mostly the "Wrap lines" and "Next change" controls at the window edge, 20 small gutter targets
  and 8 "Change # of #" lines that sit too tight.
- **Composer chips: 14.**
- **Tool row: 13.**
- **Markdown: 12.**
- **Others: 25.**

The alignment gate raised:

- `ellipsis-frame` on the status strip's model chip (Flutter cuts it too);
- `start-edge` on the step-light rows. Flutter's scene pads 12 dp, so the rows start at 44 dp, not on a 16 dp column.

The step-light finding is a conflict between the rule and the Flutter scene, not a part defect. It needs the gate
owner's decision; per the order, neither finding was baselined.

## Not run here

- No emulator; all changes are test files, so there is nothing to check on a device.
- `gate_order.sh` itself (it expects the VPS paths and slot locks); the steps above are its Gradle and evaluate
  phases.
