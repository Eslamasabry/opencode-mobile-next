ORDER: 31-live-a
ENGINE: sonnet
DEPENDS: 04
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveClock.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyle.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyles.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/LiveMockChat.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAurora.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBlueprint.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBokeh.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBrushedMetal.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCaustics.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCircuit.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o31/** native/ui/words/src/commonMain/composeResources/values/strings_rb31.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb31.xml docs/design/native-ui-rebuild/waivers/31.tsv docs/design/native-ui-rebuild/evidence/31.md
GATE: native/tool/design/gate_order.sh 31
TIMEOUT_MIN: 240
---
# Order 31-live-a: live background

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-31`, branch `native/rb-31-live-a`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/31.gate.tsv` (6 goldens). Gate command: `native/tool/design/gate_order.sh 31`.
- Depends on: order 04. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-31-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_live_clock` | `lib/ui/kit/live_background/kit_live_clock.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/kit_live_clock.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveClock.kt` | – |
| 2 | `part-kit_live_style` | `lib/ui/kit/live_background/kit_live_style.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/kit_live_style.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyle.kt` | – |
| 3 | `part-kit_live_styles` | `lib/ui/kit/live_background/kit_live_styles.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/kit_live_styles.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyles.kt` | – |
| 4 | `part-live_mock_chat` | `lib/ui/kit/live_background/live_mock_chat.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/live_mock_chat.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/LiveMockChat.kt` | – |
| 5 | `part-kit_live_aurora` | `lib/ui/kit/live_background/styles/kit_live_aurora.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_aurora.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAurora.kt` | – |
| 6 | `part-kit_live_blueprint` | `lib/ui/kit/live_background/styles/kit_live_blueprint.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_blueprint.dart`) | test/goldens/kit/kit_live_blueprint_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBlueprint.kt` | – |
| 7 | `part-kit_live_bokeh` | `lib/ui/kit/live_background/styles/kit_live_bokeh.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_bokeh.dart`) | test/goldens/kit/kit_live_bokeh_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBokeh.kt` | – |
| 8 | `part-kit_live_brushed_metal` | `lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart`) | test/goldens/kit/kit_live_brushed_metal_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBrushedMetal.kt` | – |
| 9 | `part-kit_live_caustics` | `lib/ui/kit/live_background/styles/kit_live_caustics.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_caustics.dart`) | test/goldens/kit/kit_live_caustics_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCaustics.kt` | – |
| 10 | `part-kit_live_circuit` | `lib/ui/kit/live_background/styles/kit_live_circuit.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_circuit.dart`) | test/goldens/kit/kit_live_circuit_golden_test.dart | 2 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCircuit.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o31/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_live_clock`, `kit_live_style`, `kit_live_styles`, `live_mock_chat`, `kit_live_aurora`, `kit_live_blueprint`, `kit_live_bokeh`, `kit_live_brushed_metal`, `kit_live_caustics`, `kit_live_circuit`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveClock.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyle.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveStyles.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/LiveMockChat.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAurora.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBlueprint.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBokeh.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveBrushedMetal.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCaustics.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveCircuit.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o31/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb31.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb31.xml`
- `docs/design/native-ui-rebuild/waivers/31.tsv`
- `docs/design/native-ui-rebuild/evidence/31.md`

## 4. Gate and done

1. `cd ~/wt/rb-31 && native/tool/design/gate_order.sh 31` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o31.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/31.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
