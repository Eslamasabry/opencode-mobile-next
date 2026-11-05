ORDER: 26-other-g
ENGINE: commandcode
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitLinkScope.kt native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/CapRole.kt native/ui/design/src/commonTest/kotlin/**/CapRoleTest.kt native/ui/design/src/desktopTest/kotlin/**/CapRoleTest.kt native/ui/design/src/androidHostTest/kotlin/**/CapRoleTest.kt native/ui/design/src/screenshots/CapRole/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitMaxWidth.kt native/ui/kit/src/commonTest/kotlin/**/KitMaxWidthTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitMaxWidthTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitMaxWidthTest.kt native/ui/kit/src/screenshots/KitMaxWidth/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitOptical.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitReach.kt native/ui/design/src/desktopTest/kotlin/**/rb/o26/** native/ui/kit/src/desktopTest/kotlin/**/rb/o26/** native/ui/words/src/commonMain/composeResources/values/strings_rb26.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb26.xml docs/design/native-ui-rebuild/waivers/26.tsv docs/design/native-ui-rebuild/evidence/26.md
GATE: native/tool/design/gate_order.sh 26
TIMEOUT_MIN: 240
---
# Order 26-other-g: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Command Code (GLM) (`commandcode`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-26`, branch `native/rb-26-other-g`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/26.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 26`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-26-commandcode` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_lead_column` | `lib/ui/kit/kit_lead_column.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_lead_column.dart`) | – | 0 | T2 | folded into `kit_row` (order 08-rows-a): verify only | KitLeadColumn (wrapper) |
| 2 | `part-kit_link_scope` | `lib/ui/kit/kit_link_scope.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_link_scope.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitLinkScope.kt` | KitLinkScope (scope) |
| 3 | `part-kit_log_panel_tools` | `lib/ui/kit/kit_log_panel_tools.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_log_panel_tools.dart`) | – | 0 | T2 | folded into `kit_log_panel` (order 08-rows-a): verify only | – |
| 4 | `part-kit_max_width` | `lib/ui/kit/kit_max_width.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_max_width.dart`) | – | 0 | T2 | `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/CapRole.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitMaxWidth.kt` | – |
| 5 | `part-kit_optical` | `lib/ui/kit/kit_optical.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_optical.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitOptical.kt` | – |
| 6 | `part-kit_reach` | `lib/ui/kit/kit_reach.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_reach.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitReach.kt` | KitReach (wrapper) |
| 7 | `part-kit_request_question` | `lib/ui/kit/kit_request_question.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_request_question.dart`) | – | 0 | T2 | folded into `kit_request_card` (order 11-sheets-a): verify only | – |
| 8 | `part-kit_request_row` | `lib/ui/kit/kit_request_row.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_request_row.dart`) | – | 0 | T2 | folded into `kit_request_card` (order 11-sheets-a): verify only | – |
| 9 | `part-kit_request_shape` | `lib/ui/kit/kit_request_shape.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_request_shape.dart`) | – | 0 | T2 | folded into `kit_request_card` (order 11-sheets-a): verify only | – |
| 10 | `part-kit_surface_look` | `lib/ui/kit/kit_surface_look.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_surface_look.dart`) | – | 0 | T2 | folded into `kit_style` (order 03-styles): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/26.md` under "Folded gaps"; never edit the owner's file.

Logic-only parts (no widget, no golden): each needs `rb/o26/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_link_scope`, `kit_optical`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitLinkScope.kt`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/CapRole.kt`
- `native/ui/design/src/commonTest/kotlin/**/CapRoleTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/CapRoleTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/CapRoleTest.kt`
- `native/ui/design/src/screenshots/CapRole/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitMaxWidth.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitMaxWidthTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitMaxWidthTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitMaxWidthTest.kt`
- `native/ui/kit/src/screenshots/KitMaxWidth/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitOptical.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitReach.kt`
- `native/ui/design/src/desktopTest/kotlin/**/rb/o26/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o26/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb26.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb26.xml`
- `docs/design/native-ui-rebuild/waivers/26.tsv`
- `docs/design/native-ui-rebuild/evidence/26.md`

## 4. Gate and done

1. `cd ~/wt/rb-26 && native/tool/design/gate_order.sh 26` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o26.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/26.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
