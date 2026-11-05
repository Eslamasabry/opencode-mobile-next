ORDER: 24-other-e
ENGINE: codex
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitErrorFallback.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitFloatingPanel.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitHeroQuestion.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInkLayer.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBackground.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitPlanBlock.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollFade.kt native/ui/kit/src/commonTest/kotlin/**/KitScrollFadeTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitScrollFadeTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitScrollFadeTest.kt native/ui/kit/src/screenshots/KitScrollFade/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSessionTabs.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskChip.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitTip.kt native/ui/kit/src/commonTest/kotlin/**/KitTipTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitTipTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitTipTest.kt native/ui/kit/src/screenshots/KitTip/** native/ui/kit/src/desktopTest/kotlin/**/rb/o24/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o24/** native/ui/words/src/commonMain/composeResources/values/strings_rb24.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb24.xml docs/design/native-ui-rebuild/waivers/24.tsv docs/design/native-ui-rebuild/evidence/24.md
GATE: native/tool/design/gate_order.sh 24
TIMEOUT_MIN: 240
---
# Order 24-other-e: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-24`, branch `native/rb-24-other-e`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/24.gate.tsv` (60 goldens). Gate command: `native/tool/design/gate_order.sh 24`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-24-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_error_fallback` | `lib/ui/kit/kit_error_fallback.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_error_fallback.dart`) | test/goldens/kit/kit_error_fallback_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitErrorFallback.kt` | KitErrorFallback (fill) |
| 2 | `part-kit_floating_panel` | `lib/ui/kit/kit_floating_panel.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_floating_panel.dart`) | test/goldens/kit/kit_floating_panel_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitFloatingPanel.kt` | KitFloatingPanel (fill) |
| 3 | `part-kit_hero_question` | `lib/ui/kit/kit_hero_question.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_hero_question.dart`) | test/goldens/kit/kit_hero_question_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitHeroQuestion.kt` | KitHeroQuestion (reading) |
| 4 | `part-kit_ink_layer` | `lib/ui/kit/kit_ink_layer.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_ink_layer.dart`) | test/goldens/kit/kit_ink_layer_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInkLayer.kt` | KitInkLayer (wrapper) |
| 5 | `part-kit_live_background` | `lib/ui/kit/kit_live_background.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_live_background.dart`) | test/goldens/kit/kit_live_background_golden_test.dart | 6 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBackground.kt` | KitLiveBackground (fill) |
| 6 | `part-kit_plan_block` | `lib/ui/kit/kit_plan_block.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_plan_block.dart`) | test/goldens/kit/kit_plan_block_golden_test.dart | 6 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitPlanBlock.kt` | KitPlanBlock (conversation) |
| 7 | `part-kit_scroll_fade` | `lib/ui/kit/kit_scroll_fade.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_scroll_fade.dart`) | test/goldens/kit/kit_scroll_fade_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollFade.kt` | KitScrollFade (wrapper) |
| 8 | `part-kit_session_tabs` | `lib/ui/kit/kit_session_tabs.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_session_tabs.dart`) | test/goldens/kit/kit_session_tabs_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSessionTabs.kt` | KitSessionTabs (fill) |
| 9 | `part-kit_task_chip` | `lib/ui/kit/kit_task_chip.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_task_chip.dart`) | test/goldens/kit/kit_task_chip_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskChip.kt` | KitTaskChip (chip) |
| 10 | `part-kit_tip` | `lib/ui/kit/kit_tip.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_tip.dart`) | test/goldens/kit/kit_tip_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitTip.kt` | KitTip (popover), KitTipBubble (popover) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitErrorFallback.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitFloatingPanel.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitHeroQuestion.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInkLayer.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBackground.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitPlanBlock.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollFade.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitScrollFadeTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitScrollFadeTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitScrollFadeTest.kt`
- `native/ui/kit/src/screenshots/KitScrollFade/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSessionTabs.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskChip.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/teaching/KitTip.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitTipTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitTipTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitTipTest.kt`
- `native/ui/kit/src/screenshots/KitTip/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o24/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o24/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb24.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb24.xml`
- `docs/design/native-ui-rebuild/waivers/24.tsv`
- `docs/design/native-ui-rebuild/evidence/24.md`

## 4. Gate and done

1. `cd ~/wt/rb-24 && native/tool/design/gate_order.sh 24` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o24.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/24.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
