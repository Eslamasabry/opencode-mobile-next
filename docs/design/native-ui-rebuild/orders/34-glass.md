ORDER: 34-glass
ENGINE: sonnet
DEPENDS: 03,05,06,08,11,21,25,27
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitGlass.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitWordsPlate.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitSurfaceGlass.kt native/ui/gate/src/desktopTest/kotlin/**/rb/o34/** native/ui/kit/src/desktopTest/kotlin/**/rb/o34/** native/ui/words/src/commonMain/composeResources/values/strings_rb34.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb34.xml docs/design/native-ui-rebuild/waivers/34.tsv docs/design/native-ui-rebuild/evidence/34.md
GATE: native/tool/design/gate_order.sh 34
TIMEOUT_MIN: 420
---
# Order 34-glass: glass (unparked: owner 2026-11-05 wants Glass as a user-selectable school)

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 420 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-34`, branch `native/rb-34-glass`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`; Glass sources at GLASS_REF `1bbdc3f9c7779148a689186e6d5ca3cd59062ab5` in `~/Storage/Code/oc_app` (standard §2.2).
- Gate manifest: `docs/design/native-ui-rebuild/orders/34.gate.tsv` (64 goldens). Gate command: `native/tool/design/gate_order.sh 34`.
- Depends on: order 03, order 05, order 06, order 08, order 11, order 21, order 25, order 27. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-34-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_glass` | `lib/ui/kit/glass/kit_glass.dart` (`~/wt/flutter-ref/lib/ui/kit/glass/kit_glass.dart`) | test/goldens/kit/kit_glass_golden_test.dart | 18 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitGlass.kt` | KitGlass (wrapper) |
| 2 | `part-glass-kit_words_plate` | `lib/ui/kit/kit_words_plate.dart` @ GLASS_REF (`git -C ~/Storage/Code/oc_app show 1bbdc3f9c:lib/ui/kit/kit_words_plate.dart`) | test/goldens/kit/kit_words_plate_golden_test.dart, test/goldens/kit/kit_surfaces_golden_test.dart, kit_controls_styles_golden_test.dart, kit_fields_styles_golden_test.dart @ GLASS_REF | 14 | T6 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitWordsPlate.kt` | – |
| 3 | `part-glass-kit_surface_glass` | `lib/ui/kit/kit_surface_look.dart` @ GLASS_REF (`git -C ~/Storage/Code/oc_app show 1bbdc3f9c:lib/ui/kit/kit_surface_look.dart`) | test/goldens/kit/kit_words_plate_golden_test.dart, test/goldens/kit/kit_surfaces_golden_test.dart, kit_controls_styles_golden_test.dart, kit_fields_styles_golden_test.dart @ GLASS_REF | 32 | T6 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitSurfaceGlass.kt` | – |

Also read, at GLASS_REF: `lib/ui/kit/style/kit_style_glass.dart`, `lib/ui/kit/kit_surface_paint.dart`, `lib/ui/kit/glass/kit_glass.dart`, `lib/ui/kit/kit_text_halo.dart`, `lib/ui/kit/kit_text.dart`, `lib/ui/kit/style/kit_style.dart`.

## 3. Lead prerequisites (the lead lands these before dispatch; you never edit these files)

- In `KitSurface.kt` (order 01): one call `Modifier.kitSurfaceGlass(decoration)` from NEW `KitSurfaceGlass.kt`, which the lead creates as a stub returning the modifier unchanged; you own and fill the stub.
- In `KitText.kt` (order 01): the words-plate hook calls `kitWordsPlate(...)` from NEW `KitWordsPlate.kt` (lead stub; you fill it).

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitGlass.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitWordsPlate.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitSurfaceGlass.kt`
- `native/ui/gate/src/desktopTest/kotlin/**/rb/o34/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o34/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb34.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb34.xml`
- `docs/design/native-ui-rebuild/waivers/34.tsv`
- `docs/design/native-ui-rebuild/evidence/34.md`

## 5. Gate and done

1. `cd ~/wt/rb-34 && native/tool/design/gate_order.sh 34` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o34.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/34.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
