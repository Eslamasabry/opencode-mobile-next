ORDER: 21-other-b
ENGINE: sonnet
DEPENDS: 13,14
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitServerLane.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTimelineDay.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrost.kt native/ui/kit/src/commonTest/kotlin/**/KitFrostTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitFrostTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitFrostTest.kt native/ui/kit/src/screenshots/KitFrost/** native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/FrostPolicy.kt native/ui/design/src/commonTest/kotlin/**/FrostPolicyTest.kt native/ui/design/src/desktopTest/kotlin/**/FrostPolicyTest.kt native/ui/design/src/androidHostTest/kotlin/**/FrostPolicyTest.kt native/ui/design/src/screenshots/FrostPolicy/** native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidFrost.kt native/ui/kit/src/commonTest/kotlin/**/AndroidFrostTest.kt native/ui/kit/src/desktopTest/kotlin/**/AndroidFrostTest.kt native/ui/kit/src/androidHostTest/kotlin/**/AndroidFrostTest.kt native/ui/kit/src/screenshots/AndroidFrost/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitAskField.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitContinuedLine.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFactRow.kt native/ui/kit/src/commonTest/kotlin/**/KitFactRowTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitFactRowTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitFactRowTest.kt native/ui/kit/src/screenshots/KitFactRow/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitHandoffRecord.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitWordText.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/card/KitAgentCard.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitAgentCardTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitAgentCardTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitAgentCardTest.kt native/ui/kit-chat/src/screenshots/KitAgentCard/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitOneTimeCode.kt native/ui/design/src/desktopTest/kotlin/**/rb/o21/** native/ui/kit/src/desktopTest/kotlin/**/rb/o21/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o21/** native/ui/words/src/commonMain/composeResources/values/strings_rb21.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb21.xml docs/design/native-ui-rebuild/waivers/21.tsv docs/design/native-ui-rebuild/evidence/21.md
GATE: native/tool/design/gate_order.sh 21
TIMEOUT_MIN: 240
---
# Order 21-other-b: agents and team

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-21`, branch `native/rb-21-other-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/21.gate.tsv` (173 goldens). Gate command: `native/tool/design/gate_order.sh 21`.
- Depends on: order 13, order 14. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-21-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_server_lane` | `lib/ui/kit/team/kit_server_lane.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_server_lane.dart`) | test/goldens/kit/kit_server_lane_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitServerLane.kt` | KitServerLane (lane) |
| 2 | `part-kit_timeline_day` | `lib/ui/kit/team/kit_timeline_day.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_timeline_day.dart`) | test/goldens/kit/kit_timeline_day_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTimelineDay.kt` | KitTimelineDay (reading) |
| 3 | `part-kit_frost` | `lib/ui/kit/kit_frost.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_frost.dart`) | test/goldens/kit/kit_frost_golden_test.dart | 20 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrost.kt`<br>`native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/FrostPolicy.kt`<br>`native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidFrost.kt` | KitFrost (wrapper) |
| 4 | `part-kit_ask_field` | `lib/ui/kit/kit_ask_field.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_ask_field.dart`) | test/goldens/kit/kit_ask_field_golden_test.dart | 16 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitAskField.kt` | KitAskField (conversation) |
| 5 | `part-kit_continued_line` | `lib/ui/kit/kit_continued_line.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_continued_line.dart`) | test/goldens/kit/kit_continued_line_golden_test.dart | 16 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitContinuedLine.kt` | KitContinuedLine (conversation) |
| 6 | `part-kit_fact_row` | `lib/ui/kit/kit_fact_row.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_fact_row.dart`) | test/goldens/kit/kit_fact_row_golden_test.dart | 16 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFactRow.kt` | KitFactRow (list) |
| 7 | `part-kit_handoff_record` | `lib/ui/kit/kit_handoff_record.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_handoff_record.dart`) | test/goldens/kit/kit_handoff_record_golden_test.dart | 16 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitHandoffRecord.kt` | KitHandoffRecord (conversation) |
| 8 | `part-kit_word_text` | `lib/ui/kit/kit_word_text.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_word_text.dart`) | test/goldens/kit/kit_word_text_golden_test.dart | 16 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitWordText.kt` | KitWordText (reading) |
| 9 | `part-kit_agent_card` | `lib/ui/kit/kit_agent_card.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_agent_card.dart`) | test/goldens/kit/kit_agent_card_golden_test.dart | 15 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/card/KitAgentCard.kt` | KitAgentCard (reading) |
| 10 | `part-kit_one_time_code` | `lib/ui/kit/kit_one_time_code.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_one_time_code.dart`) | test/goldens/kit/kit_one_time_code_golden_test.dart | 14 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitOneTimeCode.kt` | KitOneTimeCode (control) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitServerLane.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTimelineDay.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrost.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitFrostTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitFrostTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitFrostTest.kt`
- `native/ui/kit/src/screenshots/KitFrost/**`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/FrostPolicy.kt`
- `native/ui/design/src/commonTest/kotlin/**/FrostPolicyTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/FrostPolicyTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/FrostPolicyTest.kt`
- `native/ui/design/src/screenshots/FrostPolicy/**`
- `native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidFrost.kt`
- `native/ui/kit/src/commonTest/kotlin/**/AndroidFrostTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/AndroidFrostTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/AndroidFrostTest.kt`
- `native/ui/kit/src/screenshots/AndroidFrost/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitAskField.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitContinuedLine.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFactRow.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitFactRowTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitFactRowTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitFactRowTest.kt`
- `native/ui/kit/src/screenshots/KitFactRow/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitHandoffRecord.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitWordText.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/card/KitAgentCard.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitAgentCardTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitAgentCardTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitAgentCardTest.kt`
- `native/ui/kit-chat/src/screenshots/KitAgentCard/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitOneTimeCode.kt`
- `native/ui/design/src/desktopTest/kotlin/**/rb/o21/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o21/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o21/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb21.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb21.xml`
- `docs/design/native-ui-rebuild/waivers/21.tsv`
- `docs/design/native-ui-rebuild/evidence/21.md`

## 4. Gate and done

1. `cd ~/wt/rb-21 && native/tool/design/gate_order.sh 21` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o21.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/21.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
