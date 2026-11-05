ORDER: 30-scenes-b
ENGINE: sonnet
DEPENDS: 04
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesParts.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesScenes.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesWorkingScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamDiscoverScenes.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamScenes.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o30/** native/ui/words/src/commonMain/composeResources/values/strings_rb30.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb30.xml docs/design/native-ui-rebuild/waivers/30.tsv docs/design/native-ui-rebuild/evidence/30.md
GATE: native/tool/design/gate_order.sh 30
TIMEOUT_MIN: 240
---
# Order 30-scenes-b: scenes

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-30`, branch `native/rb-30-scenes-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/30.gate.tsv` (8 goldens). Gate command: `native/tool/design/gate_order.sh 30`.
- Depends on: order 04. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-30-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-states_parts` | `lib/ui/kit/scenes/states_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/states_parts.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesParts.kt` | – |
| 2 | `part-states_scenes` | `lib/ui/kit/scenes/states_scenes.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/states_scenes.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesScenes.kt` | – |
| 3 | `part-states_working_scene` | `lib/ui/kit/scenes/states_working_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/states_working_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesWorkingScene.kt` | – |
| 4 | `part-team_discover_scenes` | `lib/ui/kit/scenes/team_discover_scenes.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/team_discover_scenes.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamDiscoverScenes.kt` | – |
| 5 | `part-team_scenes` | `lib/ui/kit/scenes/team_scenes.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/team_scenes.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamScenes.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o30/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `states_parts`, `states_scenes`, `states_working_scene`, `team_discover_scenes`, `team_scenes`.

## 3. Gallery goldens (scenes that render several parts)

8 PNGs, 4 scenes, tier T4. Golden tests: `test/goldens/kit/kit_scenes_golden_test.dart`.

Scenes: `kit_scenes_states`, `kit_scenes_team`, `kit_scenes_team_discover`, `kit_scenes_working`

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesParts.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesScenes.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesWorkingScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamDiscoverScenes.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/TeamScenes.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o30/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb30.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb30.xml`
- `docs/design/native-ui-rebuild/waivers/30.tsv`
- `docs/design/native-ui-rebuild/evidence/30.md`

## 5. Gate and done

1. `cd ~/wt/rb-30 && native/tool/design/gate_order.sh 30` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o30.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/30.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
