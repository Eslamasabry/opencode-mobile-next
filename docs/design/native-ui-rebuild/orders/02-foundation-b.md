ORDER: 02-foundation-b
ENGINE: codex
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitCopy.kt native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/Appearance.kt native/ui/design/src/commonTest/kotlin/**/AppearanceTest.kt native/ui/design/src/desktopTest/kotlin/**/AppearanceTest.kt native/ui/design/src/androidHostTest/kotlin/**/AppearanceTest.kt native/ui/design/src/screenshots/Appearance/** native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/Layout.kt native/ui/design/src/commonTest/kotlin/**/LayoutTest.kt native/ui/design/src/desktopTest/kotlin/**/LayoutTest.kt native/ui/design/src/androidHostTest/kotlin/**/LayoutTest.kt native/ui/design/src/screenshots/Layout/** native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/WindowClass.kt native/ui/design/src/commonTest/kotlin/**/WindowClassTest.kt native/ui/design/src/desktopTest/kotlin/**/WindowClassTest.kt native/ui/design/src/androidHostTest/kotlin/**/WindowClassTest.kt native/ui/design/src/screenshots/WindowClass/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitRedact.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitShapes.kt native/ui/kit/src/commonTest/kotlin/**/KitShapesTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitShapesTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitShapesTest.kt native/ui/kit/src/screenshots/KitShapes/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/RelativeAge.kt native/ui/kit/src/commonTest/kotlin/**/RelativeAgeTest.kt native/ui/kit/src/desktopTest/kotlin/**/RelativeAgeTest.kt native/ui/kit/src/androidHostTest/kotlin/**/RelativeAgeTest.kt native/ui/kit/src/screenshots/RelativeAge/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedRows.kt native/ui/kit/src/commonTest/kotlin/**/KitAnimatedRowsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitAnimatedRowsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitAnimatedRowsTest.kt native/ui/kit/src/screenshots/KitAnimatedRows/** native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/motion/Haptic.kt native/ui/design/src/commonTest/kotlin/**/HapticTest.kt native/ui/design/src/desktopTest/kotlin/**/HapticTest.kt native/ui/design/src/androidHostTest/kotlin/**/HapticTest.kt native/ui/design/src/screenshots/Haptic/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedValue.kt native/ui/kit/src/commonTest/kotlin/**/KitAnimatedValueTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitAnimatedValueTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitAnimatedValueTest.kt native/ui/kit/src/screenshots/KitAnimatedValue/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitDim.kt native/ui/kit/src/commonTest/kotlin/**/KitDimTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitDimTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitDimTest.kt native/ui/kit/src/screenshots/KitDim/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSpin.kt native/ui/kit/src/commonTest/kotlin/**/KitSpinTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSpinTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSpinTest.kt native/ui/kit/src/screenshots/KitSpin/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSwap.kt native/ui/kit/src/commonTest/kotlin/**/KitSwapTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSwapTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSwapTest.kt native/ui/kit/src/screenshots/KitSwap/** native/ui/design/src/desktopTest/kotlin/**/rb/o02/** native/ui/kit/src/desktopTest/kotlin/**/rb/o02/** native/ui/words/src/commonMain/composeResources/values/strings_rb02.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb02.xml docs/design/native-ui-rebuild/waivers/02.tsv docs/design/native-ui-rebuild/evidence/02.md
GATE: native/tool/design/gate_order.sh 02
TIMEOUT_MIN: 240
---
# Order 02-foundation-b: foundation

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-02`, branch `native/rb-02-foundation-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/02.gate.tsv` (10 goldens). Gate command: `native/tool/design/gate_order.sh 02`.
- Depends on: order 01. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-02-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_copy` | `lib/ui/kit/kit_copy.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_copy.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitCopy.kt` | – |
| 2 | `part-kit_effects` | `lib/ui/kit/kit_effects.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_effects.dart`) | – | 0 | T2 | `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/Appearance.kt` | – |
| 3 | `part-kit_layout` | `lib/ui/kit/kit_layout.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_layout.dart`) | – | 0 | T2 | `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/Layout.kt`<br>`native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/WindowClass.kt` | – |
| 4 | `part-kit_redact` | `lib/ui/kit/kit_redact.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_redact.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitRedact.kt` | – |
| 5 | `part-kit_shape` | `lib/ui/kit/kit_shape.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_shape.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitShapes.kt` | – |
| 6 | `part-kit_time` | `lib/ui/kit/kit_time.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_time.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/RelativeAge.kt` | – |
| 7 | `part-kit_tokens` | `lib/ui/kit/kit_tokens.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_tokens.dart`) | test/goldens/kit/kit_tokens_golden_test.dart | 10 | T1 | none (lead-only token files; parity test only) | – |
| 8 | `part-kit_animated_rows` | `lib/ui/kit/motion/kit_animated_rows.dart` (`~/wt/flutter-ref/lib/ui/kit/motion/kit_animated_rows.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedRows.kt` | KitAnimatedRows (wrapper) |
| 9 | `part-kit_haptics` | `lib/ui/kit/motion/kit_haptics.dart` (`~/wt/flutter-ref/lib/ui/kit/motion/kit_haptics.dart`) | – | 0 | T2 | `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/motion/Haptic.kt` | – |
| 10 | `part-kit_motion_parts` | `lib/ui/kit/motion/kit_motion_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/motion/kit_motion_parts.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedValue.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitDim.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSpin.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSwap.kt` | KitAnimatedBox (wrapper), KitAnimatedValue (wrapper), KitDim (wrapper), KitSpin (wrapper), KitSwap (wrapper) |

Logic-only parts (no widget, no golden): each needs `rb/o02/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_copy`, `kit_effects`, `kit_layout`, `kit_redact`, `kit_shape`, `kit_time`, `kit_haptics`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitCopy.kt`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/effects/Appearance.kt`
- `native/ui/design/src/commonTest/kotlin/**/AppearanceTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/AppearanceTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/AppearanceTest.kt`
- `native/ui/design/src/screenshots/Appearance/**`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/Layout.kt`
- `native/ui/design/src/commonTest/kotlin/**/LayoutTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/LayoutTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/LayoutTest.kt`
- `native/ui/design/src/screenshots/Layout/**`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/layout/WindowClass.kt`
- `native/ui/design/src/commonTest/kotlin/**/WindowClassTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/WindowClassTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/WindowClassTest.kt`
- `native/ui/design/src/screenshots/WindowClass/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitRedact.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitShapes.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitShapesTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitShapesTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitShapesTest.kt`
- `native/ui/kit/src/screenshots/KitShapes/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/RelativeAge.kt`
- `native/ui/kit/src/commonTest/kotlin/**/RelativeAgeTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/RelativeAgeTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/RelativeAgeTest.kt`
- `native/ui/kit/src/screenshots/RelativeAge/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedRows.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitAnimatedRowsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitAnimatedRowsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitAnimatedRowsTest.kt`
- `native/ui/kit/src/screenshots/KitAnimatedRows/**`
- `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/motion/Haptic.kt`
- `native/ui/design/src/commonTest/kotlin/**/HapticTest.kt`
- `native/ui/design/src/desktopTest/kotlin/**/HapticTest.kt`
- `native/ui/design/src/androidHostTest/kotlin/**/HapticTest.kt`
- `native/ui/design/src/screenshots/Haptic/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedValue.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitAnimatedValueTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitAnimatedValueTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitAnimatedValueTest.kt`
- `native/ui/kit/src/screenshots/KitAnimatedValue/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitDim.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitDimTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitDimTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitDimTest.kt`
- `native/ui/kit/src/screenshots/KitDim/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSpin.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSpinTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSpinTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSpinTest.kt`
- `native/ui/kit/src/screenshots/KitSpin/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSwap.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSwapTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSwapTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSwapTest.kt`
- `native/ui/kit/src/screenshots/KitSwap/**`
- `native/ui/design/src/desktopTest/kotlin/**/rb/o02/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o02/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb02.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb02.xml`
- `docs/design/native-ui-rebuild/waivers/02.tsv`
- `docs/design/native-ui-rebuild/evidence/02.md`

## 4. Gate and done

1. `cd ~/wt/rb-02 && native/tool/design/gate_order.sh 02` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o02.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/02.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
