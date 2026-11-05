ORDER: 22-other-c
ENGINE: sonnet
DEPENDS: 13
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceCard.kt native/ui/kit/src/commonTest/kotlin/**/KitChoiceCardTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitChoiceCardTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitChoiceCardTest.kt native/ui/kit/src/screenshots/KitChoiceCard/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceTiles.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitProgressRing.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitCarousel.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitFineTunePanel.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitGuide.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLookPreview.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitStepSlider.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitOptionCard.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitPageHeading.kt native/ui/kit/src/commonTest/kotlin/**/KitPageHeadingTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitPageHeadingTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitPageHeadingTest.kt native/ui/kit/src/screenshots/KitPageHeading/** native/ui/kit/src/desktopTest/kotlin/**/rb/o22/** native/ui/words/src/commonMain/composeResources/values/strings_rb22.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb22.xml docs/design/native-ui-rebuild/waivers/22.tsv docs/design/native-ui-rebuild/evidence/22.md
GATE: native/tool/design/gate_order.sh 22
TIMEOUT_MIN: 240
---
# Order 22-other-c: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-22`, branch `native/rb-22-other-c`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/22.gate.tsv` (102 goldens). Gate command: `native/tool/design/gate_order.sh 22`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-22-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_choice_cards` | `lib/ui/kit/kit_choice_cards.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_choice_cards.dart`) | test/goldens/kit/kit_choice_cards_golden_test.dart | 12 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceCard.kt` | KitChoiceCards (reading) |
| 2 | `part-kit_choice_tiles` | `lib/ui/kit/kit_choice_tiles.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_choice_tiles.dart`) | test/goldens/kit/kit_choice_tiles_golden_test.dart | 12 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceTiles.kt` | KitChoiceTiles (reading) |
| 3 | `part-kit_progress_ring` | `lib/ui/kit/kit_progress_ring.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_progress_ring.dart`) | test/goldens/kit/kit_progress_ring_golden_test.dart | 12 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitProgressRing.kt` | KitProgressRing (glyph) |
| 4 | `part-kit_carousel` | `lib/ui/kit/kit_carousel.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_carousel.dart`) | test/goldens/kit/kit_carousel_golden_test.dart | 10 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitCarousel.kt` | KitCarousel (reading) |
| 5 | `part-kit_fine_tune_panel` | `lib/ui/kit/kit_fine_tune_panel.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_fine_tune_panel.dart`) | test/goldens/kit/kit_fine_tune_panel_golden_test.dart | 10 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitFineTunePanel.kt` | KitFineTunePanel (reading) |
| 6 | `part-kit_guide` | `lib/ui/kit/kit_guide.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_guide.dart`) | test/goldens/kit/kit_guide_golden_test.dart | 10 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitGuide.kt` | KitGuide (fill) |
| 7 | `part-kit_live_look_preview` | `lib/ui/kit/kit_live_look_preview.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_live_look_preview.dart`) | test/goldens/kit/kit_live_look_preview_golden_test.dart | 10 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLookPreview.kt` | KitLiveLookPreview (reading) |
| 8 | `part-kit_step_slider` | `lib/ui/kit/kit_step_slider.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_step_slider.dart`) | test/goldens/kit/kit_step_slider_golden_test.dart | 10 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitStepSlider.kt` | KitStepSlider (reading) |
| 9 | `part-kit_option_card` | `lib/ui/kit/kit_option_card.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_option_card.dart`) | test/goldens/kit/kit_option_card_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitOptionCard.kt` | KitAccentRow (reading), KitOptionCard (reading) |
| 10 | `part-kit_page_heading` | `lib/ui/kit/kit_page_heading.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_page_heading.dart`) | test/goldens/kit/kit_page_heading_golden_test.dart | 8 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitPageHeading.kt` | KitPageHeading (reading) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceCard.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitChoiceCardTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitChoiceCardTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitChoiceCardTest.kt`
- `native/ui/kit/src/screenshots/KitChoiceCard/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceTiles.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitProgressRing.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitCarousel.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitFineTunePanel.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitGuide.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLookPreview.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitStepSlider.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitOptionCard.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitPageHeading.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitPageHeadingTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitPageHeadingTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitPageHeadingTest.kt`
- `native/ui/kit/src/screenshots/KitPageHeading/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o22/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb22.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb22.xml`
- `docs/design/native-ui-rebuild/waivers/22.tsv`
- `docs/design/native-ui-rebuild/evidence/22.md`

## 4. Gate and done

1. `cd ~/wt/rb-22 && native/tool/design/gate_order.sh 22` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o22.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/22.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
