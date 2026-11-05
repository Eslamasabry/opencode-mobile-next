ORDER: 20-other-a
ENGINE: codex
DEPENDS: 13,14
WRITES: native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitSteerBox.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitServerTrust.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitDigest.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitFindingsCard.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMergeQueue.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMilestoneRow.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPhaseCard.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPlanCard.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitProjectRow.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPromoteCard.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o20/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o20/** native/ui/words/src/commonMain/composeResources/values/strings_rb20.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb20.xml docs/design/native-ui-rebuild/waivers/20.tsv docs/design/native-ui-rebuild/evidence/20.md
GATE: native/tool/design/gate_order.sh 20
TIMEOUT_MIN: 240
---
# Order 20-other-a: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-20`, branch `native/rb-20-other-a`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/20.gate.tsv` (230 goldens). Gate command: `native/tool/design/gate_order.sh 20`.
- Depends on: order 13, order 14. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-20-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_steer_box` | `lib/ui/kit/kit_steer_box.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_steer_box.dart`) | test/goldens/kit/kit_steer_box_golden_test.dart | 30 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitSteerBox.kt` | KitSteerBox (conversation) |
| 2 | `part-kit_server_trust` | `lib/ui/kit/kit_server_trust.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_server_trust.dart`) | test/goldens/kit/kit_server_trust_golden_test.dart | 24 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitServerTrust.kt` | KitServerTrustBody (sheet), showKitServerTrust (sheet) |
| 3 | `part-kit_digest` | `lib/ui/kit/team/kit_digest.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_digest.dart`) | test/goldens/kit/kit_digest_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitDigest.kt` | KitDigest (reading) |
| 4 | `part-kit_findings_card` | `lib/ui/kit/team/kit_findings_card.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_findings_card.dart`) | test/goldens/kit/kit_findings_card_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitFindingsCard.kt` | KitFindingsCard (reading) |
| 5 | `part-kit_merge_queue` | `lib/ui/kit/team/kit_merge_queue.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_merge_queue.dart`) | test/goldens/kit/kit_merge_queue_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMergeQueue.kt` | KitMergeQueue (reading) |
| 6 | `part-kit_milestone_row` | `lib/ui/kit/team/kit_milestone_row.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_milestone_row.dart`) | test/goldens/kit/kit_milestone_row_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMilestoneRow.kt` | KitMilestoneRow (list) |
| 7 | `part-kit_phase_card` | `lib/ui/kit/team/kit_phase_card.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_phase_card.dart`) | test/goldens/kit/kit_phase_card_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPhaseCard.kt` | KitPhaseCard (reading) |
| 8 | `part-kit_plan_card` | `lib/ui/kit/team/kit_plan_card.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_plan_card.dart`) | test/goldens/kit/kit_plan_card_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPlanCard.kt` | KitPlanCard (reading) |
| 9 | `part-kit_project_row` | `lib/ui/kit/team/kit_project_row.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_project_row.dart`) | test/goldens/kit/kit_project_row_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitProjectRow.kt` | KitProjectRow (list) |
| 10 | `part-kit_promote_card` | `lib/ui/kit/team/kit_promote_card.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_promote_card.dart`) | test/goldens/kit/kit_promote_card_golden_test.dart | 22 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPromoteCard.kt` | KitPromoteCard (reading) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitSteerBox.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitServerTrust.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitDigest.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitFindingsCard.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMergeQueue.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitMilestoneRow.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPhaseCard.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPlanCard.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitProjectRow.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitPromoteCard.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o20/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o20/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb20.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb20.xml`
- `docs/design/native-ui-rebuild/waivers/20.tsv`
- `docs/design/native-ui-rebuild/evidence/20.md`

## 4. Gate and done

1. `cd ~/wt/rb-20 && native/tool/design/gate_order.sh 20` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o20.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/20.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
