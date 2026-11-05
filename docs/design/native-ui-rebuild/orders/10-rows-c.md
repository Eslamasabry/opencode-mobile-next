ORDER: 10-rows-c
ENGINE: codex
DEPENDS: 05,06,07
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowGroup.kt native/ui/kit/src/commonTest/kotlin/**/KitRowGroupTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitRowGroupTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitRowGroupTest.kt native/ui/kit/src/screenshots/KitRowGroup/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowParts.kt native/ui/kit/src/commonTest/kotlin/**/KitRowPartsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitRowPartsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitRowPartsTest.kt native/ui/kit/src/screenshots/KitRowParts/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitExpandRow.kt native/ui/kit/src/commonTest/kotlin/**/KitExpandRowTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitExpandRowTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitExpandRowTest.kt native/ui/kit/src/screenshots/KitExpandRow/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwitchRow.kt native/ui/kit/src/commonTest/kotlin/**/KitSwitchRowTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSwitchRowTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSwitchRowTest.kt native/ui/kit/src/screenshots/KitSwitchRow/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusSlot.kt native/ui/kit/src/commonTest/kotlin/**/KitStatusSlotTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitStatusSlotTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitStatusSlotTest.kt native/ui/kit/src/screenshots/KitStatusSlot/** native/ui/kit/src/desktopTest/kotlin/**/rb/o10/** native/ui/words/src/commonMain/composeResources/values/strings_rb10.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb10.xml docs/design/native-ui-rebuild/waivers/10.tsv docs/design/native-ui-rebuild/evidence/10.md
GATE: native/tool/design/gate_order.sh 10
TIMEOUT_MIN: 240
---
# Order 10-rows-c: rows, groups and states

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-10`, branch `native/rb-10-rows-c`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/10.gate.tsv` (20 goldens). Gate command: `native/tool/design/gate_order.sh 10`.
- Depends on: order 05, order 06, order 07. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-10-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_row_group_seam` | `lib/ui/kit/kit_row_group_seam.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_row_group_seam.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowGroup.kt` | – |
| 2 | `part-kit_row_parts` | `lib/ui/kit/kit_row_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_row_parts.dart`) | test/goldens/kit/kit_row_parts_golden_test.dart | 20 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowParts.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitExpandRow.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwitchRow.kt` | KitChevron (glyph), KitExpandRow (list), KitRowIcon (glyph), KitRowMenu (glyph), KitSwitchRow (list) |
| 3 | `part-kit_row_value` | `lib/ui/kit/kit_row_value.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_row_value.dart`) | – | 0 | T2 | folded into `kit_row_parts` (order 10-rows-c): verify only | KitRowValue (chip) |
| 4 | `part-kit_status_slot` | `lib/ui/kit/kit_status_slot.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_status_slot.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusSlot.kt` | KitStatusContribution (scope), KitStatusLineSlot (fill), KitStatusScope (scope) |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/10.md` under "Folded gaps"; never edit the owner's file.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowGroup.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitRowGroupTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitRowGroupTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitRowGroupTest.kt`
- `native/ui/kit/src/screenshots/KitRowGroup/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRowParts.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitRowPartsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitRowPartsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitRowPartsTest.kt`
- `native/ui/kit/src/screenshots/KitRowParts/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitExpandRow.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitExpandRowTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitExpandRowTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitExpandRowTest.kt`
- `native/ui/kit/src/screenshots/KitExpandRow/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwitchRow.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSwitchRowTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSwitchRowTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSwitchRowTest.kt`
- `native/ui/kit/src/screenshots/KitSwitchRow/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusSlot.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitStatusSlotTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitStatusSlotTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitStatusSlotTest.kt`
- `native/ui/kit/src/screenshots/KitStatusSlot/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o10/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb10.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb10.xml`
- `docs/design/native-ui-rebuild/waivers/10.tsv`
- `docs/design/native-ui-rebuild/evidence/10.md`

## 4. Gate and done

1. `cd ~/wt/rb-10 && native/tool/design/gate_order.sh 10` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o10.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/10.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
