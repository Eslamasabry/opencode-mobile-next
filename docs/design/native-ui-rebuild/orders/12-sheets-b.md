ORDER: 12-sheets-b
ENGINE: codex
DEPENDS: 08,09,10
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitFab.kt native/ui/kit/src/commonTest/kotlin/**/KitFabTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitFabTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitFabTest.kt native/ui/kit/src/screenshots/KitFab/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitConfirm.kt native/ui/kit/src/commonTest/kotlin/**/KitConfirmTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitConfirmTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitConfirmTest.kt native/ui/kit/src/screenshots/KitConfirm/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollbar.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o12/** native/ui/words/src/commonMain/composeResources/values/strings_rb12.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb12.xml docs/design/native-ui-rebuild/waivers/12.tsv docs/design/native-ui-rebuild/evidence/12.md
GATE: native/tool/design/gate_order.sh 12
TIMEOUT_MIN: 240
---
# Order 12-sheets-b: sheets, dialogs, menus and requests

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-12`, branch `native/rb-12-sheets-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/12.gate.tsv` (42 goldens). Gate command: `native/tool/design/gate_order.sh 12`.
- Depends on: order 08, order 09, order 10. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-12-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_floating_action` | `lib/ui/kit/kit_floating_action.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_floating_action.dart`) | test/goldens/kit/kit_floating_action_golden_test.dart | 10 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitFab.kt` | KitFloatingAction (glyph) |
| 2 | `part-kit_confirm_sheet` | `lib/ui/kit/kit_confirm_sheet.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_confirm_sheet.dart`) | test/goldens/kit/kit_confirm_sheet_golden_test.dart | 32 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitConfirm.kt` | KitConfirmSheet (confirm), showKitConfirm (confirm) |
| 3 | `part-kit_request_answers` | `lib/ui/kit/kit_request_answers.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_request_answers.dart`) | – | 0 | T2 | folded into `kit_request_card` (order 11-sheets-a): verify only | – |
| 4 | `part-kit_scrollbar` | `lib/ui/kit/kit_scrollbar.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_scrollbar.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollbar.kt` | KitOwnScrollbar (wrapper), KitScrollArea (wrapper), KitScrollbar (wrapper) |
| 5 | `part-kit_sheet_frame` | `lib/ui/kit/kit_sheet_frame.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_sheet_frame.dart`) | – | 0 | T2 | folded into `kit_sheet` (order 11-sheets-a): verify only | – |
| 6 | `part-kit_sheet_modal` | `lib/ui/kit/kit_sheet_modal.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_sheet_modal.dart`) | – | 0 | T2 | folded into `kit_sheet` (order 11-sheets-a): verify only | – |
| 7 | `part-kit_sheet_parts` | `lib/ui/kit/kit_sheet_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_sheet_parts.dart`) | – | 0 | T2 | folded into `kit_sheet` (order 11-sheets-a): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/12.md` under "Folded gaps"; never edit the owner's file.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitFab.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitFabTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitFabTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitFabTest.kt`
- `native/ui/kit/src/screenshots/KitFab/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitConfirm.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitConfirmTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitConfirmTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitConfirmTest.kt`
- `native/ui/kit/src/screenshots/KitConfirm/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScrollbar.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o12/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb12.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb12.xml`
- `docs/design/native-ui-rebuild/waivers/12.tsv`
- `docs/design/native-ui-rebuild/evidence/12.md`

## 4. Gate and done

1. `cd ~/wt/rb-12 && native/tool/design/gate_order.sh 12` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o12.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/12.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
