# Score group A (gate cases): `:ui:kit` orders 02, 07, 09, 10, 20, 22–25, 29, 30

Builder branch `native/s17-kit-cases`, from `native/screens-merge22` (e6a10900e). Scoreboard baseline:
[SCOREBOARD-2026-11-07](../SCOREBOARD-2026-11-07.md) (taken at 89ec49e55). Flutter ref 8302de537. Orders 03, 04, 26 and 27
have no `:ui:kit` goldens in their manifests, so they add nothing here.

Scope: test and gate-case files only (`native/ui/kit/src/desktopTest/.../rb/oNN`). No production kit file was changed.
Every mismatch in the kit parts is listed below with measurements for the kit owners.

## How it was measured

On this container: `rb_gate.py fetch` for each order, with `FLUTTER_REPO` pointing at this repository, which holds 8302de537. Then
`./gradlew :ui:kit:desktopTest --tests '*.rb.oNN.*'` for all eleven orders in one run, then `rb_gate.py evaluate --order NN`.
I did not run `gate_order.sh`: it needs the VPS paths and Gradle slots, a clean tree, and the tracker.

- **Tests that threw:** 29 tests threw before the picture was taken, from layout-guard or align-gate findings (listed below). I re-ran
  just those classes with `OC_ALIGN=report OC_LAYOUT_BASELINE=update` to get their pictures. Then I restored
  `config/layout/baseline/ui-kit.tsv` with `git checkout`. **No baseline was committed.** Those shots still fail the gate as
  committed.
- **Before:** the scoreboard TSV, restricted to the `:ui:kit` rows of these orders.

## Before / after

| | goldens | pass | fail (rendered) | waived / deferred | unrendered |
|---|---:|---:|---:|---:|---:|
| before (scoreboard, 89ec49e55) | 615 | 26 | 22 | 0 | 567 |
| after (this branch) | 615 | 287 | 264 | 14 | 50 |

- **Where the new passes come from:** most of them (orders 07, 09, 20, 29, 30) come from gate cases that were merged into
  screens-merge22 after the scoreboard was taken. Those orders had no rendered shots in the scoreboard; they are all rendered now.
- **What this branch adds:** 152 goldens rendered by new cases in orders 10, 22, 23 and 24, plus the presence scene fix
  (four more passes).
- **The 50 still unrendered:** these goldens belong to parts with **no native counterpart**, so a case would have to hand-draw the part:

| order | part | goldens | note |
|---|---|---:|---|
| 22 | kit_choice_tiles | 12 | no `KitChoiceTiles` |
| 22 | kit_fine_tune_panel | 10 | no `KitFineTunePanel` |
| 23 | kit_agent_line | 6 | no `KitAgentLine` |
| 23 | kit_spec_block | 8 | no `KitSpecBlock` |
| 24 | kit_error_fallback | 6 | no `KitErrorFallback` (only the string `kitErrorFallbackMessage` exists) |
| 24 | kit_task_chip | 6 | no `KitTaskChip` (only its strings exist) |
| 25 | kit_frost_header | 2 | gated in `:ui:kit-chat` (`kitchat/rb/o25/KitFrostHeaderGateTest.kt`); the Dart scene needs `KitCodeBlock`. Fix: change the module column of `25.gate.tsv` to `:ui:kit-chat`. |

## Per part (after)

| order | part | goldens | before pass / rendered | after pass / rendered | after unrendered |
|---|---|---:|---:|---:|---:|
| 02 | part-kit_tokens | 10 | 0 / 10 | 0 / 10 | 0 |
| 07 | part-kit_status_tag | 6 | 2 / 2 | 6 / 6 | 0 |
| 07 | part-kit_buttons | 32 | 2 / 2 | 30 / 32 | 0 |
| 09 | part-kit_details_fold | 20 | 1 / 2 | 4 / 20 | 0 |
| 09 | part-kit_group_note | 16 | 0 / 0 | 0 / 16 | 0 |
| 09 | part-kit_feed_item | 10 | 0 / 0 | 10 / 10 | 0 |
| 09 | part-kit_consequences | 8 | 0 / 0 | 6 / 8 | 0 |
| 09 | part-kit_last_known | 8 | 0 / 0 | 8 / 8 | 0 |
| 09 | part-kit_mini_table | 7 | 0 / 0 | 2 / 7 | 0 |
| 09 | part-kit_sliver_row_group | 6 | 0 / 0 | 0 / 6 | 0 |
| 09 | part-kit_swipe_action | 6 | 0 / 0 | 0 / 6 | 0 |
| 10 | part-kit_row_parts | 20 | 0 / 0 | 0 / 20 | 0 |
| 20 | part-kit_server_trust | 24 | 0 / 0 | 12 / 24 | 0 |
| 20 | part-kit_digest | 22 | 0 / 0 | 15 / 22 | 0 |
| 20 | part-kit_findings_card | 22 | 0 / 0 | 18 / 22 | 0 |
| 20 | part-kit_merge_queue | 22 | 0 / 0 | 19 / 22 | 0 |
| 20 | part-kit_milestone_row | 22 | 0 / 0 | 17 / 22 | 0 |
| 20 | part-kit_phase_card | 22 | 0 / 0 | 15 / 22 | 0 |
| 20 | part-kit_plan_card | 22 | 0 / 0 | 15 / 22 | 0 |
| 20 | part-kit_project_row | 22 | 0 / 0 | 17 / 22 | 0 |
| 20 | part-kit_promote_card | 22 | 0 / 0 | 15 / 22 | 0 |
| 22 | part-kit_choice_cards | 12 | 0 / 0 | 0 / 12 | 0 |
| 22 | part-kit_choice_tiles | 12 | 0 / 0 | 0 / 0 | 12 |
| 22 | part-kit_progress_ring | 12 | 0 / 0 | 3 / 12 | 0 |
| 22 | part-kit_carousel | 10 | 0 / 2 | 0 / 10 | 0 |
| 22 | part-kit_fine_tune_panel | 10 | 0 / 0 | 0 / 0 | 10 |
| 22 | part-kit_guide | 10 | 0 / 0 | 0 / 10 | 0 |
| 22 | part-kit_live_look_preview | 10 | 4 / 4 | 4 / 10 | 0 |
| 22 | part-kit_step_slider | 10 | 0 / 0 | 10 / 10 | 0 |
| 22 | part-kit_option_card | 8 | 0 / 0 | 0 / 8 | 0 |
| 22 | part-kit_page_heading | 8 | 0 / 0 | 0 / 8 | 0 |
| 23 | part-kit_presence | 8 | 0 / 0 | 4 / 8 | 0 |
| 23 | part-kit_start_box | 8 | 0 / 0 | 0 / 8 | 0 |
| 23 | part-kit_status_answer | 8 | 0 / 0 | 2 / 8 | 0 |
| 23 | part-kit_wheel | 8 | 0 / 0 | 0 / 8 | 0 |
| 23 | part-kit_window_row | 8 | 0 / 0 | 0 / 8 | 0 |
| 23 | part-kit_spec_block | 8 | 0 / 0 | 0 / 0 | 8 |
| 23 | part-kit_agent_choice | 6 | 0 / 0 | 0 / 6 | 0 |
| 23 | part-kit_agent_grid | 6 | 0 / 0 | 0 / 6 | 0 |
| 23 | part-kit_agent_line | 6 | 0 / 0 | 0 / 0 | 6 |
| 24 | part-kit_error_fallback | 6 | 0 / 0 | 0 / 0 | 6 |
| 24 | part-kit_floating_panel | 6 | 0 / 0 | 0 / 6 | 0 |
| 24 | part-kit_hero_question | 6 | 2 / 4 | 2 / 6 | 0 |
| 24 | part-kit_ink_layer | 6 | 1 / 6 | 3 / 6 | 0 |
| 24 | part-kit_live_background | 6 | 6 / 6 | 6 / 6 | 0 |
| 24 | part-kit_scroll_fade | 6 | 6 / 6 | 6 / 6 | 0 |
| 24 | part-kit_session_tabs | 6 | 0 / 0 | 6 / 6 | 0 |
| 24 | part-kit_task_chip | 6 | 0 / 0 | 0 / 0 | 6 |
| 24 | part-kit_tip | 6 | 2 / 4 | 2 / 6 | 0 |
| 25 | part-kit_widget_preview | 6 | 0 / 0 | 6 / 6 | 0 |
| 25 | part-kit_widget_style_form | 6 | 0 / 0 | 0 / 6 | 0 |
| 25 | part-kit_frost_header | 2 | 0 / 0 | 0 / 0 | 2 |
| 29 | gallery-o29 | 22 | 0 / 0 | 16 / 22 | 0 |
| 30 | gallery-o30 | 8 | 0 / 0 | 8 / 8 | 0 |

## Failing parts, worst measurement

| order | part | failing | worst box (shot) | max blob px | max line moved px |
|---|---|---:|---|---:|---:|
| 02 | part-kit_tokens | 2 | 0.00 % (`kit_foundation_type_light`) | 66 | 7.7 |
| 07 | part-kit_buttons | 2 | 0.00 % (`kit_action_block_overflow_light`) | 282 | 0.0 |
| 09 | part-kit_details_fold | 16 | 2.21 % (`kit_details_fold_open_values_text2_dark`) | 151 | 20.1 |
| 09 | part-kit_group_note | 16 | 2.56 % (`kit_group_note_default_text2_dark`) | 136 | 8.0 |
| 09 | part-kit_consequences | 2 | 0.00 % (`kit_consequences_default_light`) | 0 | 3.1 |
| 09 | part-kit_mini_table | 5 | 3.57 % (`kit_mini_table_default_ar_dark`) | 63 | 9.2 |
| 09 | part-kit_sliver_row_group | 6 | 10.23 % (`kit_sliver_row_group_default_text2_dark`) | 133 | 6.1 |
| 09 | part-kit_swipe_action | 6 | 21.62 % (`kit_swipe_action_revealing_dark`) | 26368 | 360.9 |
| 10 | part-kit_row_parts | 20 | 49.67 % (`kit_switch_row_risk_step_dark`) | 17912 | 146.0 |
| 20 | part-kit_server_trust | 12 | 6.39 % (`kit_server_trust_certificate_text2_1280x800_dark`) | 98 | 11.4 |
| 20 | part-kit_digest | 7 | 12.37 % (`kit_digest_summary_text2_1280x800_dark`) | 19046 | 131.3 |
| 20 | part-kit_findings_card | 4 | 9.80 % (`kit_findings_card_text2_1280x800_dark`) | 19046 | 102.0 |
| 20 | part-kit_merge_queue | 3 | 12.37 % (`kit_merge_queue_text2_1280x800_dark`) | 17308 | 388.7 |
| 20 | part-kit_milestone_row | 5 | 0.00 % (`kit_milestone_row_working_dark`) | 0 | 3.3 |
| 20 | part-kit_phase_card | 7 | 12.37 % (`kit_phase_card_text2_1280x800_dark`) | 19046 | 131.3 |
| 20 | part-kit_plan_card | 7 | 12.37 % (`kit_plan_card_text2_1280x800_dark`) | 19046 | 131.3 |
| 20 | part-kit_project_row | 5 | 0.00 % (`kit_project_row_working_dark`) | 0 | 3.3 |
| 20 | part-kit_promote_card | 7 | 12.37 % (`kit_promote_card_text2_1280x800_dark`) | 19046 | 130.9 |
| 22 | part-kit_choice_cards | 12 | 13.04 % (`kit_choice_cards_default_text2_dark`) | 2548 | 102.2 |
| 22 | part-kit_progress_ring | 9 | 10.15 % (`kit_progress_ring_measured_text2_dark`) | 4164 | 6.1 |
| 22 | part-kit_carousel | 10 | 7.17 % (`kit_carousel_many_light`) | 2566 | 5.8 |
| 22 | part-kit_guide | 10 | 5.86 % (`kit_guide_default_text2_1280x800_dark`) | 8981 | 144.0 |
| 22 | part-kit_live_look_preview | 6 | 11.91 % (`kit_live_look_preview_on_text2_dark`) | 896 | 287.2 |
| 22 | part-kit_option_card | 8 | 76.08 % (`kit_option_card_disabled_dark`) | 20132 | 213.7 |
| 22 | part-kit_page_heading | 8 | 12.23 % (`kit_page_heading_switcher_text2_dark`) | 869 | 17.4 |
| 23 | part-kit_presence | 4 | 9.36 % (`kit_presence_shown_text2_1280x800_dark`) | 0 | 2.0 |
| 23 | part-kit_start_box | 8 | 26.81 % (`kit_start_box_typed_light`) | 644 | 12.7 |
| 23 | part-kit_status_answer | 6 | 5.64 % (`kit_status_answer_default_text2_dark`) | 298 | 18.9 |
| 23 | part-kit_wheel | 8 | 0.00 % (`kit_wheel_default_text2_light`) | 375 | 2.7 |
| 23 | part-kit_window_row | 8 | 22.94 % (`kit_window_row_default_text2_dark`) | 4607 | 446.8 |
| 23 | part-kit_agent_choice | 6 | 10.61 % (`kit_agent_choice_default_text2_dark`) | 1472 | 153.5 |
| 23 | part-kit_agent_grid | 6 | 9.85 % (`kit_agent_grid_default_text2_dark`) | 1132 | 153.5 |
| 24 | part-kit_floating_panel | 6 | 38.65 % (`kit_floating_panel_default_dark`) | 12309 | 55.3 |
| 24 | part-kit_hero_question | 4 | 25.83 % (`kit_hero_question_default_1280x800_dark`) | 553 | 66.6 |
| 24 | part-kit_ink_layer | 3 | 20.16 % (`kit_ink_layer_rest_1280x800_dark`) | 7509 | 144.0 |
| 24 | part-kit_tip | 4 | 9.66 % (`kit_tip_default_text2_dark`) | 159 | 54.3 |
| 25 | part-kit_widget_style_form | 6 | 11.66 % (`kit_widget_style_form_default_text2_dark`) | 2256 | 143.8 |

## What differs, as seen (side by side: Flutter | native | diff, half size)

| shot | image | what differs | spec / native |
|---|---|---|---|
| presence | [png](score-A-kit-cases/kit_presence_shown_dark.png) | **Fixed in the scene.** The Compose `Row` wrapped its content and the gallery centred it 35 px in, while Flutter's `Row` fills the width. The text-1.0 shots now pass at box 0.00 %. The text-2.0 shots stay 2 px low: KitText line height at scale 2. | `kit_presence_golden_test.dart`; `KitText` |
| switch row on | [png](score-A-kit-cases/kit_switch_row_on_dark.png) | The title and supporting line sit about 4 px higher, and the switch has no end padding. | `kit_row_parts.dart:551-552` (switch end pad space2); `KitSwitchRow.kt:168` |
| switch row risk step | — | Native unfolds "Turn on / Not now" side by side. Flutter shows the scope, three full-width "until" buttons, then "Not now": box 49.7 %. | `kit_row_parts.dart:455-492`; `KitSwitchRow.kt:170-191` (no `KitRisk` until-choices) |
| expand row | — | The chevron sits bare where Flutter puts it in a 48 dp square with end padding. Revealed rows are indented by the gutter with 8 dp gaps; Flutter stacks them flush. The supporting line allows 3 lines; Flutter caps it at 1. | `kit_row_parts.dart:877,968-990`; `KitExpandRow.kt:95-103` |
| page heading | [png](score-A-kit-cases/kit_page_heading_switcher_dark.png) | The tappable line is amber with a right chevron; Flutter's default is secondary text with a down chevron. Native adds S3 bottom padding and an S1 gap. | `kit_page_heading.dart`; `KitPageHeading.kt:40-48`, `KitHeadingLine.kt:22-23` |
| option card | [png](score-A-kit-cases/kit_option_card_default_dark.png) | No glow-card row (Classic / Soft ring) and no accent row, so everything sits 48 px higher. The disabled theme card has no "Not on this phone" (box 76 %). Swatches follow the frame's light/dark mode where Flutter always uses dark roles. | `kit_option_card.dart:521-529`; `KitOptionCard.kt:55` |
| choice cards | [png](score-A-kit-cases/kit_choice_cards_default_dark.png) | Native draws a compact one-row card: icon, title and line side by side, 72 dp tall. Flutter stacks the icon over a Title-role title, minimum height `minTarget*3`, padding space5. There is no busy or disabled input. | `kit_choice_cards.dart`; `KitChoiceCard.kt:83-130` |
| agent choice / grid | [png](score-A-kit-cases/kit_agent_choice_default_dark.png) | No brand marks (the terminal glyph instead), no strength line, no "Set up" state, and no reading cap on the wide window. | `KitAgentChoice.kt:59,127,137` |
| live look preview on | [png](score-A-kit-cases/kit_live_look_preview_on_dark.png) | Native draws only the "off" tile; it has no `style`, `intensity` or `enabled` input. The layout guard also flags the tile as 688 dp wide on a tablet against the control cap of 400. | `KitLiveLookPreview` |
| carousel | [png](score-A-kit-cases/kit_carousel_five_dark.png) | The card is taller than Flutter's and the label sits lower (moved 2.5-5.8 px). The scene's cards are hand-drawn swatches. `KitThemeOptionCard` could replace them, but it follows the frame mode where the Dart scene always uses dark roles. | `KitCarousel` height parameter |
| window row | [png](score-A-kit-cases/kit_window_row_default_dark.png) | Stacked rows have no gutter or vertical padding, and there is no tone (Week, Month and Last read draw the accent bar). The note uses Secondary where Flutter uses caption. The value is not tabular. | `kit_window_row.dart`; `KitWindowRow.kt:54-76` |
| guide question | [png](score-A-kit-cases/kit_guide_question_dark.png) | There is no `KitGuide` frame, so the scene arranges its parts. The crumb dots were removed (E20-S08), the chips are 8 dp apart where Flutter's are 6, and the back crumb has no minimum target height. | `KitGuide.kt:101,114,128,285` |
| progress ring measured | [png](score-A-kit-cases/kit_progress_ring_measured_dark.png) | blob 3.8-4.2 k px, moved 3-4 px: the ring stroke and the trail marks differ. | `kit_progress_ring.dart` |
| session tabs | [png](score-A-kit-cases/kit_session_tabs_default_dark.png) | Passes (6 / 6). | — |
| team cards, text 2.0 wide | — | `kit_{plan,phase,promote,digest,findings}_card_text2_1280x800`: box 12.37 %, blob 19,046. At text 2.0 the primary action ("Approve and start") moves to the start of the card; Flutter keeps it at the end. Five parts share this one cause. | `KitTeamCard` action row |

## Guard findings (the gate as committed still throws on these; fix the kit, don't baseline)

- **Align gate:**
  - **`kit_switch_row_*`, `kit_expand_row_*` (10 tests):** words start at x 32, which is not one of the page's columns. Flutter's own scene (`ListView` with gutter
    padding around a `KitRowGroup`) also puts the words at x 32 (checked on the golden), so the scene is faithful. The rule and
    the Flutter part disagree, and the align-rule owner should decide. Also flagged: an icon by "Built-in plugins" is 9 dp off the label centre, and an icon starts at x 36.
  - **`kit_consequences_default*` (4, existing case):** words start at x 68.
  - **`kit_details_fold_*_sheet` (2, existing):** words start at x 32 in the technical-details sheet.
  - **`kit_swipe_action_*` (3, existing):** the row is 72 dp tall against 64 dp for the list's rows, and the undo words start at x 32.
- **Layout guard:**
  - **`kit_live_look_preview_on*` (4):** "Tablet wide-control", the tile is 688 dp wide against the control cap of 400.
  - **`kit_step_slider_*` (6):** "tight", the tick label ("Default" / "Small") sits 0 dp under the track where the rule needs 4. The pictures pass (10 / 10).
- **Matrix (`rb_gate.py evaluate` X1/X2):**
  - **Cap measured on the whole scene:** `part-kit_row_parts` measures the whole scene against the glyph caps of
    KitChevron, KitRowIcon and KitRowMenu (928 dp > 96 dp). `part-kit_progress_ring` measures ring plus label at 280 dp > 96 dp.
  - **Other:** `part-kit_floating_panel` has one unlabelled node, and `part-kit_tip` can't measure its popover cap.
  - **Parts already over their cap before this branch:** `part-kit_server_trust` (686 > 640) and `part-kit_details_fold` (719 > 640).

## Not done

- **Order 02 (`kit_foundation_*`):** 8 of its 10 shots are deferred (DEPENDS_ON). Of the other two, `kit_foundation_type_light` fails with
  blob 66 px and line moved 7.7 px. The scoreboard says to re-measure after A3-A6 land.
- **Kit-part mismatches above:** none of them are fixed. They are production files owned by other builders.
