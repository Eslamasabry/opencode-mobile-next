ORDER: 06-controls-b
ENGINE: codex
DEPENDS: 04
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitDivider.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitLevelMeterRules.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitLevelMeterRulesTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitLevelMeterRulesTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitLevelMeterRulesTest.kt native/ui/kit-chat/src/screenshots/KitLevelMeterRules/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitVoicePill.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitVoicePillTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitVoicePillTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitVoicePillTest.kt native/ui/kit-chat/src/screenshots/KitVoicePill/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSearchField.kt native/ui/kit/src/commonTest/kotlin/**/KitSearchFieldTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSearchFieldTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSearchFieldTest.kt native/ui/kit/src/screenshots/KitSearchField/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitChip.kt native/ui/kit/src/commonTest/kotlin/**/KitChipTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitChipTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitChipTest.kt native/ui/kit/src/screenshots/KitChip/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitNeedsYou.kt native/ui/kit/src/commonTest/kotlin/**/KitNeedsYouTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitNeedsYouTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitNeedsYouTest.kt native/ui/kit/src/screenshots/KitNeedsYou/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/KitQr.kt native/ui/kit-content/src/commonTest/kotlin/**/KitQrTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/KitQrTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/KitQrTest.kt native/ui/kit-content/src/screenshots/KitQr/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrEncoder.kt native/ui/kit-content/src/commonTest/kotlin/**/QrEncoderTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/QrEncoderTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/QrEncoderTest.kt native/ui/kit-content/src/screenshots/QrEncoder/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrMatrix.kt native/ui/kit-content/src/commonTest/kotlin/**/QrMatrixTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/QrMatrixTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/QrMatrixTest.kt native/ui/kit-content/src/screenshots/QrMatrix/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrPayload.kt native/ui/kit-content/src/commonTest/kotlin/**/QrPayloadTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/QrPayloadTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/QrPayloadTest.kt native/ui/kit-content/src/screenshots/QrPayload/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrRules.kt native/ui/kit-content/src/commonTest/kotlin/**/QrRulesTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/QrRulesTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/QrRulesTest.kt native/ui/kit-content/src/screenshots/QrRules/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitKeyValue.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChip.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChips.kt native/ui/kit/src/commonTest/kotlin/**/KitFilterChipsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitFilterChipsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitFilterChipsTest.kt native/ui/kit/src/screenshots/KitFilterChips/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSectionLabel.kt native/ui/kit/src/commonTest/kotlin/**/KitSectionLabelTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSectionLabelTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSectionLabelTest.kt native/ui/kit/src/screenshots/KitSectionLabel/** native/ui/kit/src/desktopTest/kotlin/**/rb/o06/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o06/** native/ui/kit-content/src/desktopTest/kotlin/**/rb/o06/** native/ui/words/src/commonMain/composeResources/values/strings_rb06.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb06.xml docs/design/native-ui-rebuild/waivers/06.tsv docs/design/native-ui-rebuild/evidence/06.md
GATE: native/tool/design/gate_order.sh 06
TIMEOUT_MIN: 240
---
# Order 06-controls-b: controls

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-06`, branch `native/rb-06-controls-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/06.gate.tsv` (131 goldens). Gate command: `native/tool/design/gate_order.sh 06`.
- Depends on: order 04. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-06-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_divider` | `lib/ui/kit/kit_divider.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_divider.dart`) | test/goldens/kit/kit_divider_golden_test.dart | 22 | T1 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitDivider.kt` | KitDivider (fill) |
| 2 | `part-kit_level_meter` | `lib/ui/kit/kit_level_meter.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_level_meter.dart`) | test/goldens/kit/kit_level_meter_golden_test.dart | 22 | T4 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitLevelMeterRules.kt`<br>`native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitVoicePill.kt` | KitLevelMeter (reading) |
| 3 | `part-kit_search_field` | `lib/ui/kit/kit_search_field.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_search_field.dart`) | test/goldens/kit/kit_search_field_golden_test.dart | 20 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSearchField.kt` | KitSearchField (list), KitSearchNoMatch (state) |
| 4 | `part-kit_chip` | `lib/ui/kit/kit_chip.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_chip.dart`) | test/goldens/kit/kit_chip_golden_test.dart | 16 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitChip.kt` | KitChip (chip), KitChipWrap (reading) |
| 5 | `part-kit_needs_you` | `lib/ui/kit/kit_needs_you.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_needs_you.dart`) | test/goldens/kit/kit_needs_you_golden_test.dart | 16 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitNeedsYou.kt` | – |
| 6 | `part-kit_qr` | `lib/ui/kit/kit_qr.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_qr.dart`) | test/goldens/kit/kit_qr_golden_test.dart | 10 | T4 | `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/KitQr.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrEncoder.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrMatrix.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrPayload.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrRules.kt` | KitQr (control) |
| 7 | `part-kit_key_value` | `lib/ui/kit/kit_key_value.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_key_value.dart`) | test/goldens/kit/kit_key_value_golden_test.dart | 7 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitKeyValue.kt` | KitKeyValue (reading) |
| 8 | `part-kit_filter_chip` | `lib/ui/kit/kit_filter_chip.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_filter_chip.dart`) | test/goldens/kit/kit_filter_chip_golden_test.dart | 6 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChip.kt` | KitFilterChip (chip) |
| 9 | `part-kit_filter_chips` | `lib/ui/kit/kit_filter_chips.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_filter_chips.dart`) | test/goldens/kit/kit_filter_chips_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChips.kt` | KitFilterChips (reading) |
| 10 | `part-kit_section_label` | `lib/ui/kit/kit_section_label.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_section_label.dart`) | test/goldens/kit/kit_section_label_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSectionLabel.kt` | KitSectionLabel (list) |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitDivider.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitLevelMeterRules.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitLevelMeterRulesTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitLevelMeterRulesTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitLevelMeterRulesTest.kt`
- `native/ui/kit-chat/src/screenshots/KitLevelMeterRules/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitVoicePill.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitVoicePillTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitVoicePillTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitVoicePillTest.kt`
- `native/ui/kit-chat/src/screenshots/KitVoicePill/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSearchField.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSearchFieldTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSearchFieldTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSearchFieldTest.kt`
- `native/ui/kit/src/screenshots/KitSearchField/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitChip.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitChipTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitChipTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitChipTest.kt`
- `native/ui/kit/src/screenshots/KitChip/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitNeedsYou.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitNeedsYouTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitNeedsYouTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitNeedsYouTest.kt`
- `native/ui/kit/src/screenshots/KitNeedsYou/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/KitQr.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/KitQrTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/KitQrTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/KitQrTest.kt`
- `native/ui/kit-content/src/screenshots/KitQr/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrEncoder.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/QrEncoderTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/QrEncoderTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/QrEncoderTest.kt`
- `native/ui/kit-content/src/screenshots/QrEncoder/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrMatrix.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/QrMatrixTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/QrMatrixTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/QrMatrixTest.kt`
- `native/ui/kit-content/src/screenshots/QrMatrix/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrPayload.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/QrPayloadTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/QrPayloadTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/QrPayloadTest.kt`
- `native/ui/kit-content/src/screenshots/QrPayload/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/qr/QrRules.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/QrRulesTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/QrRulesTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/QrRulesTest.kt`
- `native/ui/kit-content/src/screenshots/QrRules/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitKeyValue.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChip.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/chip/KitFilterChips.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitFilterChipsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitFilterChipsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitFilterChipsTest.kt`
- `native/ui/kit/src/screenshots/KitFilterChips/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSectionLabel.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSectionLabelTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSectionLabelTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSectionLabelTest.kt`
- `native/ui/kit/src/screenshots/KitSectionLabel/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o06/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o06/**`
- `native/ui/kit-content/src/desktopTest/kotlin/**/rb/o06/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb06.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb06.xml`
- `docs/design/native-ui-rebuild/waivers/06.tsv`
- `docs/design/native-ui-rebuild/evidence/06.md`

## 4. Gate and done

1. `cd ~/wt/rb-06 && native/tool/design/gate_order.sh 06` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o06.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/06.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
