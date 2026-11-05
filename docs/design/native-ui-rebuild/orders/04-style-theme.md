ORDER: 04-style-theme
ENGINE: sonnet
DEPENDS: 03
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitTheme.kt native/ui/kit/src/commonTest/kotlin/**/KitThemeTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitThemeTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitThemeTest.kt native/ui/kit/src/screenshots/KitTheme/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitColors.kt native/ui/kit/src/commonTest/kotlin/**/KitColorsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitColorsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitColorsTest.kt native/ui/kit/src/screenshots/KitColors/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitLocals.kt native/ui/kit/src/commonTest/kotlin/**/KitLocalsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitLocalsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitLocalsTest.kt native/ui/kit/src/screenshots/KitLocals/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/MaterialBridge.kt native/ui/kit/src/commonTest/kotlin/**/MaterialBridgeTest.kt native/ui/kit/src/desktopTest/kotlin/**/MaterialBridgeTest.kt native/ui/kit/src/androidHostTest/kotlin/**/MaterialBridgeTest.kt native/ui/kit/src/screenshots/MaterialBridge/** native/ui/kit/src/desktopTest/kotlin/**/rb/o04/** native/ui/words/src/commonMain/composeResources/values/strings_rb04.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb04.xml docs/design/native-ui-rebuild/waivers/04.tsv docs/design/native-ui-rebuild/evidence/04.md
GATE: native/tool/design/gate_order.sh 04
TIMEOUT_MIN: 240
---
# Order 04-style-theme: foundation

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-04`, branch `native/rb-04-style-theme`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/04.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 04`.
- Depends on: order 03. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-04-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_style_theme` | `lib/ui/kit/style/kit_style_theme.dart` (`~/wt/flutter-ref/lib/ui/kit/style/kit_style_theme.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitTheme.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitColors.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitLocals.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/MaterialBridge.kt` | – |

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitTheme.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitThemeTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitThemeTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitThemeTest.kt`
- `native/ui/kit/src/screenshots/KitTheme/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitColors.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitColorsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitColorsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitColorsTest.kt`
- `native/ui/kit/src/screenshots/KitColors/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitLocals.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitLocalsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitLocalsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitLocalsTest.kt`
- `native/ui/kit/src/screenshots/KitLocals/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/MaterialBridge.kt`
- `native/ui/kit/src/commonTest/kotlin/**/MaterialBridgeTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/MaterialBridgeTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/MaterialBridgeTest.kt`
- `native/ui/kit/src/screenshots/MaterialBridge/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o04/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb04.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb04.xml`
- `docs/design/native-ui-rebuild/waivers/04.tsv`
- `docs/design/native-ui-rebuild/evidence/04.md`

## 4. Gate and done

1. `cd ~/wt/rb-04 && native/tool/design/gate_order.sh 04` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o04.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/04.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
