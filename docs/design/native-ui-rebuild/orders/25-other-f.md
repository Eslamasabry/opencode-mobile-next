ORDER: 25-other-f
ENGINE: codex
DEPENDS: 13,14
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitWidgetPreview.kt native/ui/kit/src/commonTest/kotlin/**/KitWidgetPreviewTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitWidgetPreviewTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitWidgetPreviewTest.kt native/ui/kit/src/screenshots/KitWidgetPreview/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/widget/KitWidgetStylePreview.kt native/ui/kit/src/commonTest/kotlin/**/KitWidgetStylePreviewTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitWidgetStylePreviewTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitWidgetStylePreviewTest.kt native/ui/kit/src/screenshots/KitWidgetStylePreview/** native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidWidgetThemeColors.kt native/ui/kit/src/commonTest/kotlin/**/AndroidWidgetThemeColorsTest.kt native/ui/kit/src/desktopTest/kotlin/**/AndroidWidgetThemeColorsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/AndroidWidgetThemeColorsTest.kt native/ui/kit/src/screenshots/AndroidWidgetThemeColors/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrostHeader.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffMarkdown.kt native/ui/kit-chat/src/commonTest/kotlin/**/DiffMarkdownTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/DiffMarkdownTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/DiffMarkdownTest.kt native/ui/kit-chat/src/screenshots/DiffMarkdown/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffModel.kt native/ui/kit-chat/src/commonTest/kotlin/**/DiffModelTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/DiffModelTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/DiffModelTest.kt native/ui/kit-chat/src/screenshots/DiffModel/** native/ui/kit/src/desktopTest/kotlin/**/rb/o25/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o25/** native/ui/words/src/commonMain/composeResources/values/strings_rb25.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb25.xml docs/design/native-ui-rebuild/waivers/25.tsv docs/design/native-ui-rebuild/evidence/25.md
GATE: native/tool/design/gate_order.sh 25
TIMEOUT_MIN: 240
---
# Order 25-other-f: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-25`, branch `native/rb-25-other-f`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/25.gate.tsv` (14 goldens). Gate command: `native/tool/design/gate_order.sh 25`.
- Depends on: order 13, order 14. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-25-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_widget_preview` | `lib/ui/kit/kit_widget_preview.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_widget_preview.dart`) | test/goldens/kit/kit_widget_preview_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitWidgetPreview.kt` | KitWidgetPreview (reading) |
| 2 | `part-kit_widget_style_form` | `lib/ui/kit/kit_widget_style_form.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_widget_style_form.dart`) | test/goldens/kit/kit_widget_style_form_golden_test.dart | 6 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/widget/KitWidgetStylePreview.kt`<br>`native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidWidgetThemeColors.kt` | KitWidgetStyleForm (reading) |
| 3 | `part-kit_frost_header` | `lib/ui/kit/kit_frost_header.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_frost_header.dart`) | test/goldens/kit/kit_frost_header_golden_test.dart | 2 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrostHeader.kt` | – |
| 4 | `part-kit_diff_chrome` | `lib/ui/kit/diff/kit_diff_chrome.dart` (`~/wt/flutter-ref/lib/ui/kit/diff/kit_diff_chrome.dart`) | – | 0 | T2 | folded into `kit_diff_view` (order 14-chat-a): verify only | – |
| 5 | `part-kit_diff_markdown` | `lib/ui/kit/diff/kit_diff_markdown.dart` (`~/wt/flutter-ref/lib/ui/kit/diff/kit_diff_markdown.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffMarkdown.kt` | – |
| 6 | `part-kit_diff_model` | `lib/ui/kit/diff/kit_diff_model.dart` (`~/wt/flutter-ref/lib/ui/kit/diff/kit_diff_model.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffModel.kt` | – |
| 7 | `part-kit_diff_rows` | `lib/ui/kit/diff/kit_diff_rows.dart` (`~/wt/flutter-ref/lib/ui/kit/diff/kit_diff_rows.dart`) | – | 0 | T2 | folded into `kit_diff_view` (order 14-chat-a): verify only | – |
| 8 | `part-kit` | `lib/ui/kit/kit.dart` (`~/wt/flutter-ref/lib/ui/kit/kit.dart`) | – | 0 | T2 | none (lead-only token files; parity test only) | – |
| 9 | `part-kit_agent_grid_span` | `lib/ui/kit/kit_agent_grid_span.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_agent_grid_span.dart`) | – | 0 | T2 | folded into `kit_agent_grid` (order 23-other-d): verify only | KitAgentGridSpan (scope) |
| 10 | `part-kit_code_surface` | `lib/ui/kit/kit_code_surface.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_code_surface.dart`) | – | 0 | T2 | folded into `kit_code_block` (order 08-rows-a): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/25.md` under "Folded gaps"; never edit the owner's file.

Logic-only parts (no widget, no golden): each needs `rb/o25/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_diff_markdown`, `kit_diff_model`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitWidgetPreview.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitWidgetPreviewTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitWidgetPreviewTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitWidgetPreviewTest.kt`
- `native/ui/kit/src/screenshots/KitWidgetPreview/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/widget/KitWidgetStylePreview.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitWidgetStylePreviewTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitWidgetStylePreviewTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitWidgetStylePreviewTest.kt`
- `native/ui/kit/src/screenshots/KitWidgetStylePreview/**`
- `native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidWidgetThemeColors.kt`
- `native/ui/kit/src/commonTest/kotlin/**/AndroidWidgetThemeColorsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/AndroidWidgetThemeColorsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/AndroidWidgetThemeColorsTest.kt`
- `native/ui/kit/src/screenshots/AndroidWidgetThemeColors/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrostHeader.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffMarkdown.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/DiffMarkdownTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/DiffMarkdownTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/DiffMarkdownTest.kt`
- `native/ui/kit-chat/src/screenshots/DiffMarkdown/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/DiffModel.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/DiffModelTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/DiffModelTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/DiffModelTest.kt`
- `native/ui/kit-chat/src/screenshots/DiffModel/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o25/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o25/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb25.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb25.xml`
- `docs/design/native-ui-rebuild/waivers/25.tsv`
- `docs/design/native-ui-rebuild/evidence/25.md`

## 4. Gate and done

1. `cd ~/wt/rb-25 && native/tool/design/gate_order.sh 25` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o25.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/25.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
