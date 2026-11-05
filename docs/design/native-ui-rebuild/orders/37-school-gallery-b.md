ORDER: 37-school-gallery-b
ENGINE: sonnet
DEPENDS: 05,06,07,08,09,10,11,12,13,14,22
WRITES: native/ui/gate/src/desktopTest/kotlin/**/rb/o37/** native/ui/words/src/commonMain/composeResources/values/strings_rb37.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb37.xml docs/design/native-ui-rebuild/waivers/37.tsv docs/design/native-ui-rebuild/evidence/37.md
GATE: native/tool/design/gate_order.sh 37
TIMEOUT_MIN: 300
---
# Order 37-school-gallery-b: school galleries: paper, signal

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 300 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-37`, branch `native/rb-37-school-gallery-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/37.gate.tsv` (64 goldens). Gate command: `native/tool/design/gate_order.sh 37`.
- Depends on: order 05, order 06, order 07, order 08, order 09, order 10, order 11, order 12, order 13, order 14, order 22. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-37-sonnet` (standard §11).

## 2. Gallery goldens (scenes that render several parts)

64 PNGs, 32 scenes, tier T2. Golden tests: `test/goldens/kit/kit_surfaces_golden_test.dart`, `kit_surfaces_tiles_golden_test.dart`, `kit_surfaces_technical_golden_test.dart`, `kit_surfaces_hooks_golden_test.dart`, `kit_controls_styles_golden_test.dart`, `kit_fields_styles_golden_test.dart`.

Scenes: `kit_controls_paper`, `kit_controls_signal`, `kit_fields_paper`, `kit_fields_signal`, `kit_surfaces_appearance_paper`, `kit_surfaces_appearance_signal`, `kit_surfaces_choices_paper`, `kit_surfaces_choices_signal`, `kit_surfaces_floating_paper`, `kit_surfaces_floating_signal`, `kit_surfaces_hooks_paper`, `kit_surfaces_hooks_signal`, `kit_surfaces_lists_paper`, `kit_surfaces_lists_signal`, `kit_surfaces_minor_paper`, `kit_surfaces_minor_signal`, `kit_surfaces_paper`, `kit_surfaces_roles_paper`, `kit_surfaces_roles_signal`, `kit_surfaces_sheet_paper`, `kit_surfaces_sheet_signal`, `kit_surfaces_signal`, `kit_surfaces_technical_paper`, `kit_surfaces_technical_signal`, `kit_surfaces_tiles_alert_paper`, `kit_surfaces_tiles_alert_signal`, `kit_surfaces_tiles_confirm_paper`, `kit_surfaces_tiles_confirm_signal`, `kit_surfaces_tiles_paper`, `kit_surfaces_tiles_signal`, `kit_surfaces_transcript_paper`, `kit_surfaces_transcript_signal`

## 3. What this order does

Verification only for the schools paper, signal: render every gallery scene with the same inputs as the Flutter test, in each school, and pass the gate. The spec files belong to order 03 and the parts to their orders: when a scene fails, find the cause (spec value, painter, part) and mark the scene blocked in the tracker with the exact owner file and line and the Flutter line it contradicts (standard §13). The lead routes the fix. You write tests only.

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/gate/src/desktopTest/kotlin/**/rb/o37/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb37.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb37.xml`
- `docs/design/native-ui-rebuild/waivers/37.tsv`
- `docs/design/native-ui-rebuild/evidence/37.md`

## 5. Gate and done

1. `cd ~/wt/rb-37 && native/tool/design/gate_order.sh 37` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o37.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/37.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
