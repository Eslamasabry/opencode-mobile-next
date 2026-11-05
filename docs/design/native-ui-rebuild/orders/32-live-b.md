ORDER: 32-live-b
ENGINE: codex
DEPENDS: 31
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveConstellation.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveDotRipple.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveFlowThreads.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveGlowSpill.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHalftone.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHoneycomb.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLightRays.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLiquidBlobs.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMatrixRain.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMesh.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o32/** native/ui/words/src/commonMain/composeResources/values/strings_rb32.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb32.xml docs/design/native-ui-rebuild/waivers/32.tsv docs/design/native-ui-rebuild/evidence/32.md
GATE: native/tool/design/gate_order.sh 32
TIMEOUT_MIN: 240
---
# Order 32-live-b: live background

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-32`, branch `native/rb-32-live-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/32.gate.tsv` (3 goldens). Gate command: `native/tool/design/gate_order.sh 32`.
- Depends on: order 31. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-32-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_live_constellation` | `lib/ui/kit/live_background/styles/kit_live_constellation.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_constellation.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveConstellation.kt` | – |
| 2 | `part-kit_live_dot_ripple` | `lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveDotRipple.kt` | – |
| 3 | `part-kit_live_flow_threads` | `lib/ui/kit/live_background/styles/kit_live_flow_threads.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_flow_threads.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveFlowThreads.kt` | – |
| 4 | `part-kit_live_glow_spill` | `lib/ui/kit/live_background/styles/kit_live_glow_spill.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_glow_spill.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveGlowSpill.kt` | – |
| 5 | `part-kit_live_halftone` | `lib/ui/kit/live_background/styles/kit_live_halftone.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_halftone.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHalftone.kt` | – |
| 6 | `part-kit_live_honeycomb` | `lib/ui/kit/live_background/styles/kit_live_honeycomb.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_honeycomb.dart`) | test/goldens/kit/kit_live_honeycomb_golden_test.dart | 2 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHoneycomb.kt` | – |
| 7 | `part-kit_live_light_rays` | `lib/ui/kit/live_background/styles/kit_live_light_rays.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_light_rays.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLightRays.kt` | – |
| 8 | `part-kit_live_liquid_blobs` | `lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLiquidBlobs.kt` | – |
| 9 | `part-kit_live_matrix_rain` | `lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart`) | test/goldens/kit/kit_live_matrix_rain_golden_test.dart | 1 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMatrixRain.kt` | – |
| 10 | `part-kit_live_mesh` | `lib/ui/kit/live_background/styles/kit_live_mesh.dart` (`~/wt/flutter-ref/lib/ui/kit/live_background/styles/kit_live_mesh.dart`) | – | 0 | T4 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMesh.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o32/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_live_constellation`, `kit_live_dot_ripple`, `kit_live_flow_threads`, `kit_live_glow_spill`, `kit_live_halftone`, `kit_live_honeycomb`, `kit_live_light_rays`, `kit_live_liquid_blobs`, `kit_live_matrix_rain`, `kit_live_mesh`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveConstellation.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveDotRipple.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveFlowThreads.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveGlowSpill.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHalftone.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveHoneycomb.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLightRays.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveLiquidBlobs.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMatrixRain.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveMesh.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o32/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb32.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb32.xml`
- `docs/design/native-ui-rebuild/waivers/32.tsv`
- `docs/design/native-ui-rebuild/evidence/32.md`

## 4. Gate and done

1. `cd ~/wt/rb-32 && native/tool/design/gate_order.sh 32` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o32.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/32.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
