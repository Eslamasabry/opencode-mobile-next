ORDER: 15-chat-b
ENGINE: sonnet
DEPENDS: 13
WRITES: native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitWorkLine.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitWorkLineTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitWorkLineTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitWorkLineTest.kt native/ui/kit-chat/src/screenshots/KitWorkLine/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextRing.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitContextRingTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitContextRingTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitContextRingTest.kt native/ui/kit-chat/src/screenshots/KitContextRing/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepTimeline.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSenseAsk.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitBackgroundWorkLine.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileList.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextChart.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitPhraseChips.kt native/ui/kit/src/commonTest/kotlin/**/KitPhraseChipsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitPhraseChipsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitPhraseChipsTest.kt native/ui/kit/src/screenshots/KitPhraseChips/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerGlow.kt native/ui/kit-chat/src/commonTest/kotlin/**/ComposerGlowTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/ComposerGlowTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/ComposerGlowTest.kt native/ui/kit-chat/src/screenshots/ComposerGlow/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/MovingPart.kt native/ui/kit-chat/src/commonTest/kotlin/**/MovingPartTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/MovingPartTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/MovingPartTest.kt native/ui/kit-chat/src/screenshots/MovingPart/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitStreamCaret.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o15/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o15/** native/ui/words/src/commonMain/composeResources/values/strings_rb15.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb15.xml docs/design/native-ui-rebuild/waivers/15.tsv docs/design/native-ui-rebuild/evidence/15.md
GATE: native/tool/design/gate_order.sh 15
TIMEOUT_MIN: 300
---
# Order 15-chat-b: chat

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 300 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-15`, branch `native/rb-15-chat-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/15.gate.tsv` (113 goldens). Gate command: `native/tool/design/gate_order.sh 15`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-15-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_work_line` | `lib/ui/kit/chat/kit_work_line.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_work_line.dart`) | test/goldens/kit/kit_work_line_golden_test.dart | 18 | T3 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitWorkLine.kt` | KitWorkLine (conversation) |
| 2 | `part-kit_context_ring` | `lib/ui/kit/kit_context_ring.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_context_ring.dart`) | test/goldens/kit/kit_context_ring_golden_test.dart | 18 | T4 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextRing.kt` | KitContextRing (glyph) |
| 3 | `part-kit_step_timeline` | `lib/ui/kit/chat/kit_step_timeline.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_step_timeline.dart`) | test/goldens/kit/kit_step_timeline_golden_test.dart | 16 | T3 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepTimeline.kt` | KitStepTimeline (conversation) |
| 4 | `part-kit_sense_ask` | `lib/ui/kit/kit_sense_ask.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_sense_ask.dart`) | test/goldens/kit/kit_sense_ask_golden_test.dart | 11 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSenseAsk.kt` | KitSenseAsk (conversation) |
| 5 | `part-kit_background_work_line` | `lib/ui/kit/chat/kit_background_work_line.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_background_work_line.dart`) | test/goldens/kit/kit_background_work_line_golden_test.dart | 10 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitBackgroundWorkLine.kt` | KitBackgroundWorkLine (conversation) |
| 6 | `part-kit_project_file_list` | `lib/ui/kit/chat/kit_project_file_list.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_project_file_list.dart`) | test/goldens/kit/kit_project_file_list_golden_test.dart | 10 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileList.kt` | KitProjectFileList (list) |
| 7 | `part-kit_context_chart` | `lib/ui/kit/kit_context_chart.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_context_chart.dart`) | test/goldens/kit/kit_context_chart_golden_test.dart | 10 | T4 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextChart.kt` | KitContextChart (reading) |
| 8 | `part-kit_phrase_chips` | `lib/ui/kit/chat/kit_phrase_chips.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_phrase_chips.dart`) | test/goldens/kit/kit_phrase_chips_golden_test.dart | 8 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitPhraseChips.kt` | KitPhraseChips (conversation) |
| 9 | `part-kit_composer_glow` | `lib/ui/kit/chat/kit_composer_glow.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_glow.dart`) | test/goldens/kit/kit_composer_glow_golden_test.dart | 6 | T4 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerGlow.kt`<br>`native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/MovingPart.kt` | – |
| 10 | `part-kit_stream_caret` | `lib/ui/kit/chat/kit_stream_caret.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_stream_caret.dart`) | test/goldens/kit/kit_stream_caret_golden_test.dart | 6 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitStreamCaret.kt` | KitStreamCaret (glyph) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitWorkLine.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitWorkLineTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitWorkLineTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitWorkLineTest.kt`
- `native/ui/kit-chat/src/screenshots/KitWorkLine/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextRing.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitContextRingTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitContextRingTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitContextRingTest.kt`
- `native/ui/kit-chat/src/screenshots/KitContextRing/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepTimeline.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSenseAsk.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitBackgroundWorkLine.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileList.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/turn/KitContextChart.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitPhraseChips.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitPhraseChipsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitPhraseChipsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitPhraseChipsTest.kt`
- `native/ui/kit/src/screenshots/KitPhraseChips/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerGlow.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/ComposerGlowTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/ComposerGlowTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/ComposerGlowTest.kt`
- `native/ui/kit-chat/src/screenshots/ComposerGlow/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/MovingPart.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/MovingPartTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/MovingPartTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/MovingPartTest.kt`
- `native/ui/kit-chat/src/screenshots/MovingPart/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitStreamCaret.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o15/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o15/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb15.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb15.xml`
- `docs/design/native-ui-rebuild/waivers/15.tsv`
- `docs/design/native-ui-rebuild/evidence/15.md`

## 4. Gate and done

1. `cd ~/wt/rb-15 && native/tool/design/gate_order.sh 15` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o15.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/15.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
