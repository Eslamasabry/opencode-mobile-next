ORDER: 36-school-gallery-a
ENGINE: sonnet
DEPENDS: 05,06,07,08,09,10,11,12,13,14,22
WRITES: native/ui/gate/src/desktopTest/kotlin/**/rb/o36/** native/ui/words/src/commonMain/composeResources/values/strings_rb36.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb36.xml docs/design/native-ui-rebuild/waivers/36.tsv docs/design/native-ui-rebuild/evidence/36.md
GATE: native/tool/design/gate_order.sh 36
TIMEOUT_MIN: 300
---
# Order 36-school-gallery-a: school galleries: solid, soft, brutal

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 300 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-36`, branch `native/rb-36-school-gallery-a`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/36.gate.tsv` (95 goldens). Gate command: `native/tool/design/gate_order.sh 36`.
- Depends on: order 05, order 06, order 07, order 08, order 09, order 10, order 11, order 12, order 13, order 14, order 22. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-36-sonnet` (standard §11).

## 2. Gallery goldens (scenes that render several parts)

95 PNGs, 48 scenes, tier T2. Golden tests: `test/goldens/kit/kit_surfaces_golden_test.dart`, `kit_surfaces_tiles_golden_test.dart`, `kit_surfaces_technical_golden_test.dart`, `kit_surfaces_hooks_golden_test.dart`, `kit_controls_styles_golden_test.dart`, `kit_fields_styles_golden_test.dart`.

Scenes: `kit_controls_brutal`, `kit_controls_soft`, `kit_controls_solid`, `kit_fields_brutal`, `kit_fields_soft`, `kit_fields_solid`, `kit_surfaces_appearance_brutal`, `kit_surfaces_appearance_soft`, `kit_surfaces_appearance_solid`, `kit_surfaces_brutal`, `kit_surfaces_choices_brutal`, `kit_surfaces_choices_soft`, `kit_surfaces_choices_solid`, `kit_surfaces_floating_brutal`, `kit_surfaces_floating_soft`, `kit_surfaces_floating_solid`, `kit_surfaces_hooks_brutal`, `kit_surfaces_hooks_soft`, `kit_surfaces_hooks_solid`, `kit_surfaces_lists_brutal`, `kit_surfaces_lists_soft`, `kit_surfaces_lists_solid`, `kit_surfaces_minor_brutal`, `kit_surfaces_minor_soft`, `kit_surfaces_minor_solid`, `kit_surfaces_roles_brutal`, `kit_surfaces_roles_soft`, `kit_surfaces_roles_solid`, `kit_surfaces_sheet_brutal`, `kit_surfaces_sheet_soft`, `kit_surfaces_sheet_solid`, `kit_surfaces_soft`, `kit_surfaces_soft_ar`, `kit_surfaces_solid`, `kit_surfaces_technical_brutal`, `kit_surfaces_technical_soft`, `kit_surfaces_technical_solid`, `kit_surfaces_tiles_alert_brutal`, `kit_surfaces_tiles_alert_soft`, `kit_surfaces_tiles_alert_solid`, `kit_surfaces_tiles_brutal`, `kit_surfaces_tiles_confirm_brutal`, `kit_surfaces_tiles_confirm_soft`, `kit_surfaces_tiles_confirm_solid`, `kit_surfaces_tiles_soft`, `kit_surfaces_tiles_solid`, `kit_surfaces_transcript_brutal`, `kit_surfaces_transcript_solid`

## 3. What this order does

Verification only for the schools solid, soft, brutal: render every gallery scene with the same inputs as the Flutter test, in each school, and pass the gate. The spec files belong to order 03 and the parts to their orders: when a scene fails, find the cause (spec value, painter, part) and mark the scene blocked in the tracker with the exact owner file and line and the Flutter line it contradicts (standard §13). The lead routes the fix. You write tests only.

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/gate/src/desktopTest/kotlin/**/rb/o36/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb36.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb36.xml`
- `docs/design/native-ui-rebuild/waivers/36.tsv`
- `docs/design/native-ui-rebuild/evidence/36.md`

## 5. Gate and done

1. `cd ~/wt/rb-36 && native/tool/design/gate_order.sh 36` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o36.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/36.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
