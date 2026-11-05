ORDER: 33-live-c
ENGINE: codex
DEPENDS: 31
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveOscilloscope.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveRadar.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSilk.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSupport.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveToolkit.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveTopo.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o33/** native/ui/words/src/commonMain/composeResources/values/strings_rb33.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb33.xml docs/design/native-ui-rebuild/waivers/33.tsv docs/design/native-ui-rebuild/evidence/33.md
GATE: native/tool/design/gate_order.sh 33
TIMEOUT_MIN: 240
---
# Order 33-live-c: live background

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-33`, branch `native/rb-33-live-c`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/33.gate.tsv` (3 goldens). Gate command: `native/tool/design/gate_order.sh 33`.
- Depends on: order 31. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-33-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_live_oscilloscope` | `lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart`) | test/goldens/kit/kit_live_oscilloscope_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveOscilloscope.kt` | – |
| 2 | `part-kit_live_radar` | `lib/ui/kit/live_background/styles/kit_live_radar.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_radar.dart`) | test/goldens/kit/kit_live_radar_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveRadar.kt` | – |
| 3 | `part-kit_live_silk` | `lib/ui/kit/live_background/styles/kit_live_silk.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_silk.dart`) | test/goldens/kit/kit_live_silk_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSilk.kt` | – |
| 4 | `part-kit_live_support` | `lib/ui/kit/live_background/styles/kit_live_support.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_support.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSupport.kt` | – |
| 5 | `part-kit_live_toolkit` | `lib/ui/kit/live_background/styles/kit_live_toolkit.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_toolkit.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveToolkit.kt` | – |
| 6 | `part-kit_live_topo` | `lib/ui/kit/live_background/styles/kit_live_topo.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_topo.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveTopo.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o33/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_live_oscilloscope`, `kit_live_radar`, `kit_live_silk`, `kit_live_support`, `kit_live_toolkit`, `kit_live_topo`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveOscilloscope.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveRadar.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSilk.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveSupport.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveToolkit.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveTopo.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o33/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb33.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb33.xml`
- `docs/design/native-ui-rebuild/waivers/33.tsv`
- `docs/design/native-ui-rebuild/evidence/33.md`

## 4. Gate and done

1. `cd ~/wt/rb-33 && native/tool/design/gate_order.sh 33` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o33.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/33.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
