ORDER: 13-frame-nav
ENGINE: sonnet
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBar.kt native/ui/kit/src/commonTest/kotlin/**/KitTopBarTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitTopBarTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitTopBarTest.kt native/ui/kit/src/screenshots/KitTopBar/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBarScope.kt native/ui/kit/src/commonTest/kotlin/**/KitTopBarScopeTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitTopBarScopeTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitTopBarScopeTest.kt native/ui/kit/src/screenshots/KitTopBarScope/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitExitGlyph.kt native/ui/kit/src/commonTest/kotlin/**/KitExitGlyphTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitExitGlyphTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitExitGlyphTest.kt native/ui/kit/src/screenshots/KitExitGlyph/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt native/ui/kit/src/commonTest/kotlin/**/KitScreenTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitScreenTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitScreenTest.kt native/ui/kit/src/screenshots/KitScreen/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitParts.kt native/ui/kit/src/commonTest/kotlin/**/KitPartsTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitPartsTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitPartsTest.kt native/ui/kit/src/screenshots/KitParts/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitBreadcrumb.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavRail.kt native/ui/kit/src/commonTest/kotlin/**/KitNavRailTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitNavRailTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitNavRailTest.kt native/ui/kit/src/screenshots/KitNavRail/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSidebar.kt native/ui/kit/src/commonTest/kotlin/**/KitSidebarTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitSidebarTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitSidebarTest.kt native/ui/kit/src/screenshots/KitSidebar/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavDestination.kt native/ui/kit/src/commonTest/kotlin/**/KitNavDestinationTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitNavDestinationTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitNavDestinationTest.kt native/ui/kit/src/screenshots/KitNavDestination/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitArrival.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o13/** native/ui/words/src/commonMain/composeResources/values/strings_rb13.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb13.xml docs/design/native-ui-rebuild/waivers/13.tsv docs/design/native-ui-rebuild/evidence/13.md
GATE: native/tool/design/gate_order.sh 13
TIMEOUT_MIN: 300
---
# Order 13-frame-nav: frame and navigation

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 300 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-13`, branch `native/rb-13-frame-nav`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/13.gate.tsv` (84 goldens). Gate command: `native/tool/design/gate_order.sh 13`.
- Depends on: order 11, order 12. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-13-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_top_bar` | `lib/ui/kit/kit_top_bar.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_top_bar.dart`) | test/goldens/kit/kit_top_bar_golden_test.dart | 26 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBar.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBarScope.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitExitGlyph.kt` | KitShellControls (fill), KitTopBar (fill) |
| 2 | `part-kit_screen` | `lib/ui/kit/kit_screen.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_screen.dart`) | test/goldens/kit/kit_screen_golden_test.dart | 18 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitParts.kt` | KitScreen (fill) |
| 3 | `part-kit_breadcrumb` | `lib/ui/kit/kit_breadcrumb.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_breadcrumb.dart`) | test/goldens/kit/kit_breadcrumb_golden_test.dart | 16 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitBreadcrumb.kt` | KitBreadcrumb (list) |
| 4 | `part-kit_nav` | `lib/ui/kit/kit_nav.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_nav.dart`) | test/goldens/kit/kit_nav_golden_test.dart | 10 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavRail.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSidebar.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavDestination.kt` | KitNav (fill), KitNavRail (fill) |
| 5 | `part-kit_arrival` | `lib/ui/kit/kit_arrival.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_arrival.dart`) | test/goldens/kit/kit_arrival_golden_test.dart | 8 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitArrival.kt` | KitArrival (wrapper), KitArrivalScope (scope) |
| 6 | `part-kit_page_route` | `lib/ui/kit/kit_page_route.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_page_route.dart`) | test/goldens/kit/kit_page_route_golden_test.dart | 6 | T2 | folded into `kit_page_transitions` (order 03-styles): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/13.md` under "Folded gaps"; never edit the owner's file.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBar.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitTopBarTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitTopBarTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitTopBarTest.kt`
- `native/ui/kit/src/screenshots/KitTopBar/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopBarScope.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitTopBarScopeTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitTopBarScopeTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitTopBarScopeTest.kt`
- `native/ui/kit/src/screenshots/KitTopBarScope/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitExitGlyph.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitExitGlyphTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitExitGlyphTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitExitGlyphTest.kt`
- `native/ui/kit/src/screenshots/KitExitGlyph/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitScreenTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitScreenTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitScreenTest.kt`
- `native/ui/kit/src/screenshots/KitScreen/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitParts.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitPartsTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitPartsTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitPartsTest.kt`
- `native/ui/kit/src/screenshots/KitParts/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitBreadcrumb.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavRail.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitNavRailTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitNavRailTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitNavRailTest.kt`
- `native/ui/kit/src/screenshots/KitNavRail/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitSidebar.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitSidebarTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitSidebarTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitSidebarTest.kt`
- `native/ui/kit/src/screenshots/KitSidebar/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitNavDestination.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitNavDestinationTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitNavDestinationTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitNavDestinationTest.kt`
- `native/ui/kit/src/screenshots/KitNavDestination/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitArrival.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o13/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb13.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb13.xml`
- `docs/design/native-ui-rebuild/waivers/13.tsv`
- `docs/design/native-ui-rebuild/evidence/13.md`

## 4. Gate and done

1. `cd ~/wt/rb-13 && native/tool/design/gate_order.sh 13` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o13.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/13.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
