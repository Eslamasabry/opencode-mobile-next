ORDER: 09-rows-b
ENGINE: codex
DEPENDS: 05,06,07
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitDetailsFold.kt native/ui/kit/src/commonTest/kotlin/**/KitDetailsFoldTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitDetailsFoldTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitDetailsFoldTest.kt native/ui/kit/src/screenshots/KitDetailsFold/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitGroupNote.kt native/ui/kit/src/commonTest/kotlin/**/KitGroupNoteTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitGroupNoteTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitGroupNoteTest.kt native/ui/kit/src/screenshots/KitGroupNote/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitLastKnown.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitMiniTable.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSliverRowGroup.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwipeAction.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitPanel.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o09/** native/ui/words/src/commonMain/composeResources/values/strings_rb09.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb09.xml docs/design/native-ui-rebuild/waivers/09.tsv docs/design/native-ui-rebuild/evidence/09.md
GATE: native/tool/design/gate_order.sh 09
TIMEOUT_MIN: 240
---
# Order 09-rows-b: rows, groups and states

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-09`, branch `native/rb-09-rows-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/09.gate.tsv` (81 goldens). Gate command: `native/tool/design/gate_order.sh 09`.
- Depends on: order 05, order 06, order 07. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-09-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_details_fold` | `lib/ui/kit/kit_details_fold.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_details_fold.dart`) | test/goldens/kit/kit_details_fold_golden_test.dart | 20 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitDetailsFold.kt` | KitDetailsFold (reading), showKitTechnicalDetails (sheet) |
| 2 | `part-kit_group_note` | `lib/ui/kit/kit_group_note.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_group_note.dart`) | test/goldens/kit/kit_group_note_golden_test.dart | 16 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitGroupNote.kt` | KitGroupNote (reading) |
| 3 | `part-kit_feed_item` | `lib/ui/kit/kit_feed_item.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_feed_item.dart`) | test/goldens/kit/kit_feed_item_golden_test.dart | 10 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt` | KitFeedItem (list) |
| 4 | `part-kit_consequences` | `lib/ui/kit/kit_consequences.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_consequences.dart`) | test/goldens/kit/kit_consequences_golden_test.dart | 8 | T2 | folded into `kit_confirm_sheet` (order 12-sheets-b): verify only | KitConsequences (reading) |
| 5 | `part-kit_last_known` | `lib/ui/kit/kit_last_known.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_last_known.dart`) | test/goldens/kit/kit_last_known_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitLastKnown.kt` | KitLastKnown (list) |
| 6 | `part-kit_mini_table` | `lib/ui/kit/kit_mini_table.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_mini_table.dart`) | test/goldens/kit/kit_mini_table_golden_test.dart | 7 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitMiniTable.kt` | KitMiniTable (list) |
| 7 | `part-kit_sliver_row_group` | `lib/ui/kit/kit_sliver_row_group.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_sliver_row_group.dart`) | test/goldens/kit/kit_sliver_row_group_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSliverRowGroup.kt` | KitSliverRowGroup (list) |
| 8 | `part-kit_swipe_action` | `lib/ui/kit/kit_swipe_action.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_swipe_action.dart`) | test/goldens/kit/kit_swipe_action_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwipeAction.kt` | – |
| 9 | `part-kit_code_block_parts` | `lib/ui/kit/kit_code_block_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_code_block_parts.dart`) | – | 0 | T2 | folded into `kit_code_block` (order 08-rows-a): verify only | – |
| 10 | `part-kit_panel` | `lib/ui/kit/kit_panel.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_panel.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitPanel.kt` | KitPanel (reading) |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/09.md` under "Folded gaps"; never edit the owner's file.

Logic-only parts (no widget, no golden): each needs `rb/o09/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_swipe_action`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitDetailsFold.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitDetailsFoldTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitDetailsFoldTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitDetailsFoldTest.kt`
- `native/ui/kit/src/screenshots/KitDetailsFold/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitGroupNote.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitGroupNoteTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitGroupNoteTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitGroupNoteTest.kt`
- `native/ui/kit/src/screenshots/KitGroupNote/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitLastKnown.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitMiniTable.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSliverRowGroup.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwipeAction.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitPanel.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o09/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb09.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb09.xml`
- `docs/design/native-ui-rebuild/waivers/09.tsv`
- `docs/design/native-ui-rebuild/evidence/09.md`

## 4. Gate and done

1. `cd ~/wt/rb-09 && native/tool/design/gate_order.sh 09` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o09.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/09.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
