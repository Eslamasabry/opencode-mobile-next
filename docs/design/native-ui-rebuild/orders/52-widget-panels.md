ORDER: 52-widget-panels
ENGINE: codex
DEPENDS: 13,25
WRITES: native/feature/widget-panels/src/commonMain/kotlin/**/ui/** native/feature/widget-panels/src/commonMain/kotlin/**/*Screen.kt native/feature/widget-panels/src/commonMain/kotlin/**/*Sheet*.kt native/feature/widget-panels/src/desktopTest/kotlin/**/rb/o52/** native/feature/widget-panels/build.gradle.kts native/feature/widgetactions/src/commonMain/kotlin/**/ui/** native/feature/widgetactions/src/commonMain/kotlin/**/*Screen.kt native/feature/widgetactions/src/commonMain/kotlin/**/*Sheet*.kt native/feature/widgetactions/src/desktopTest/kotlin/**/rb/o52/** native/feature/widgetactions/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb52.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb52.xml docs/design/native-ui-rebuild/waivers/52.tsv docs/design/native-ui-rebuild/evidence/52.md
GATE: native/tool/design/gate_order.sh 52
TIMEOUT_MIN: 360
---
# Order 52-widget-panels: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-52`, branch `native/rb-52-widget-panels`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/52.gate.tsv` (12 goldens). Gate command: `native/tool/design/gate_order.sh 52`.
- Depends on: order 13, order 25. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-52-codex` (standard §11).

## 2. Screens

Native modules: `native/feature/widget-panels`, `native/feature/widgetactions`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-M01` | M01 | `WidgetConfigureScreen` (`/widgets/configure`) | panel | `WidgetPanel.Style(widgetId)` |
| `screen-M02` | M02 | `WidgetProjectScreen` (`/widgets/project`) | panel | `WidgetPanel.Project(widgetId)` |
| `screen-M03` | M03 | `WidgetSteerScreen` (`/widgets/steer`, `/widgets/voice`) | panel | `WidgetPanel.Steer(widgetId, voice)` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/widget_*` — 12 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/widget-panels/src/commonMain/kotlin/**/ui/**`
- `native/feature/widget-panels/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/widget-panels/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/widget-panels/src/desktopTest/kotlin/**/rb/o52/**`
- `native/feature/widget-panels/build.gradle.kts`
- `native/feature/widgetactions/src/commonMain/kotlin/**/ui/**`
- `native/feature/widgetactions/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/widgetactions/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/widgetactions/src/desktopTest/kotlin/**/rb/o52/**`
- `native/feature/widgetactions/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb52.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb52.xml`
- `docs/design/native-ui-rebuild/waivers/52.tsv`
- `docs/design/native-ui-rebuild/evidence/52.md`

## 4. Gate and done

1. `cd ~/wt/rb-52 && native/tool/design/gate_order.sh 52` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o52.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/52.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
