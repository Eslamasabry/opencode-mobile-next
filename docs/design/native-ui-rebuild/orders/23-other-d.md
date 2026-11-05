ORDER: 23-other-d
ENGINE: codex
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitPresence.kt native/ui/kit/src/commonTest/kotlin/**/KitPresenceTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitPresenceTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitPresenceTest.kt native/ui/kit/src/screenshots/KitPresence/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitStartBox.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusAnswer.kt native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitWebPreview.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitWheel.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitWindowRow.kt native/ui/kit/src/commonTest/kotlin/**/KitWindowRowTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitWindowRowTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitWindowRowTest.kt native/ui/kit/src/screenshots/KitWindowRow/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitSpecBlock.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentChoice.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentGrid.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentLine.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o23/** native/ui/kit-content/src/desktopTest/kotlin/**/rb/o23/** native/ui/words/src/commonMain/composeResources/values/strings_rb23.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb23.xml docs/design/native-ui-rebuild/waivers/23.tsv docs/design/native-ui-rebuild/evidence/23.md
GATE: native/tool/design/gate_order.sh 23
TIMEOUT_MIN: 240
---
# Order 23-other-d: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-23`, branch `native/rb-23-other-d`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/23.gate.tsv` (74 goldens). Gate command: `native/tool/design/gate_order.sh 23`.
- Depends on: order 13, order 14. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-23-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_presence` | `lib/ui/kit/kit_presence.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_presence.dart`) | test/goldens/kit/kit_presence_golden_test.dart | 8 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitPresence.kt` | KitPresence (wrapper) |
| 2 | `part-kit_start_box` | `lib/ui/kit/kit_start_box.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_start_box.dart`) | test/goldens/kit/kit_start_box_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitStartBox.kt` | KitStartBox (conversation) |
| 3 | `part-kit_status_answer` | `lib/ui/kit/kit_status_answer.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_status_answer.dart`) | test/goldens/kit/kit_status_answer_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusAnswer.kt` | KitStatusAnswer (conversation) |
| 4 | `part-kit_web_preview` | `lib/ui/kit/kit_web_preview.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_web_preview.dart`) | test/goldens/kit/kit_web_preview_golden_test.dart | 8 | T2 | NEW `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitWebPreview.kt` | KitWebPreview (reading) |
| 5 | `part-kit_wheel` | `lib/ui/kit/kit_wheel.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_wheel.dart`) | test/goldens/kit/kit_wheel_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitWheel.kt` | KitWheel (control) |
| 6 | `part-kit_window_row` | `lib/ui/kit/kit_window_row.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_window_row.dart`) | test/goldens/kit/kit_window_row_golden_test.dart | 8 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitWindowRow.kt` | KitWindowRow (list) |
| 7 | `part-kit_spec_block` | `lib/ui/kit/team/kit_spec_block.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_spec_block.dart`) | test/goldens/kit/kit_spec_block_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitSpecBlock.kt` | KitSpecBlock (reading) |
| 8 | `part-kit_agent_choice` | `lib/ui/kit/kit_agent_choice.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_agent_choice.dart`) | test/goldens/kit/kit_agent_choice_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentChoice.kt` | KitAgentChoice (reading) |
| 9 | `part-kit_agent_grid` | `lib/ui/kit/kit_agent_grid.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_agent_grid.dart`) | test/goldens/kit/kit_agent_grid_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentGrid.kt` | KitAgentGrid (reading) |
| 10 | `part-kit_agent_line` | `lib/ui/kit/kit_agent_line.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_agent_line.dart`) | test/goldens/kit/kit_agent_line_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentLine.kt` | KitAgentLine (conversation) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitPresence.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitPresenceTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitPresenceTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitPresenceTest.kt`
- `native/ui/kit/src/screenshots/KitPresence/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitStartBox.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusAnswer.kt`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitWebPreview.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitWheel.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitWindowRow.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitWindowRowTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitWindowRowTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitWindowRowTest.kt`
- `native/ui/kit/src/screenshots/KitWindowRow/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitSpecBlock.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentChoice.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentGrid.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/agent/KitAgentLine.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o23/**`
- `native/ui/kit-content/src/desktopTest/kotlin/**/rb/o23/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb23.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb23.xml`
- `docs/design/native-ui-rebuild/waivers/23.tsv`
- `docs/design/native-ui-rebuild/evidence/23.md`

## 4. Gate and done

1. `cd ~/wt/rb-23 && native/tool/design/gate_order.sh 23` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o23.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/23.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
