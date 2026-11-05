ORDER: 07-controls-c
ENGINE: sonnet
DEPENDS: 04
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusTag.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitButton.kt native/ui/kit/src/commonTest/kotlin/**/KitButtonTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitButtonTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitButtonTest.kt native/ui/kit/src/screenshots/KitButton/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitActionBlock.kt native/ui/kit/src/commonTest/kotlin/**/KitActionBlockTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitActionBlockTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitActionBlockTest.kt native/ui/kit/src/screenshots/KitActionBlock/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInlineAction.kt native/ui/kit/src/commonTest/kotlin/**/KitInlineActionTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitInlineActionTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitInlineActionTest.kt native/ui/kit/src/screenshots/KitInlineAction/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/KitIllustration.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitSkeletonTranscript.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitTaskMark.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o07/** native/ui/words/src/commonMain/composeResources/values/strings_rb07.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb07.xml docs/design/native-ui-rebuild/waivers/07.tsv docs/design/native-ui-rebuild/evidence/07.md
GATE: native/tool/design/gate_order.sh 07
TIMEOUT_MIN: 240
---
# Order 07-controls-c: controls

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-07`, branch `native/rb-07-controls-c`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/07.gate.tsv` (38 goldens). Gate command: `native/tool/design/gate_order.sh 07`.
- Depends on: order 04. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-07-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_status_tag` | `lib/ui/kit/kit_status_tag.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_status_tag.dart`) | test/goldens/kit/kit_status_tag_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusTag.kt` | KitStatusTag (chip) |
| 2 | `part-kit_buttons` | `lib/ui/kit/kit_buttons.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_buttons.dart`) | test/goldens/kit/kit_buttons_golden_test.dart | 32 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitButton.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitActionBlock.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInlineAction.kt` | KitActionBlock (reading), KitButton (control), KitInset (wrapper) |
| 3 | `part-kit_illustration` | `lib/ui/kit/kit_illustration.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_illustration.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/KitIllustration.kt` | KitIllustration (reading) |
| 4 | `part-kit_skeleton_transcript` | `lib/ui/kit/kit_skeleton_transcript.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_skeleton_transcript.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitSkeletonTranscript.kt` | KitSkeletonTranscript (conversation) |
| 5 | `part-kit_task_mark` | `lib/ui/kit/kit_task_mark.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_task_mark.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitTaskMark.kt` | KitTaskMark (glyph) |
| 6 | `part-kit_technical_value` | `lib/ui/kit/kit_technical_value.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_technical_value.dart`) | – | 0 | T2 | folded into `kit_details_fold` (order 09-rows-b): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/07.md` under "Folded gaps"; never edit the owner's file.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusTag.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitButton.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitButtonTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitButtonTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitButtonTest.kt`
- `native/ui/kit/src/screenshots/KitButton/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitActionBlock.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitActionBlockTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitActionBlockTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitActionBlockTest.kt`
- `native/ui/kit/src/screenshots/KitActionBlock/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitInlineAction.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitInlineActionTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitInlineActionTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitInlineActionTest.kt`
- `native/ui/kit/src/screenshots/KitInlineAction/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/KitIllustration.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitSkeletonTranscript.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitTaskMark.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o07/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb07.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb07.xml`
- `docs/design/native-ui-rebuild/waivers/07.tsv`
- `docs/design/native-ui-rebuild/evidence/07.md`

## 4. Gate and done

1. `cd ~/wt/rb-07 && native/tool/design/gate_order.sh 07` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o07.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/07.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
