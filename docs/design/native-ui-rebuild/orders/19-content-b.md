ORDER: 19-content-b
ENGINE: commandcode
DEPENDS: 01
WRITES: native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitViewerBytes.kt native/ui/kit-content/src/commonTest/kotlin/**/KitViewerBytesTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/KitViewerBytesTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/KitViewerBytesTest.kt native/ui/kit-content/src/screenshots/KitViewerBytes/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerPaths.kt native/ui/kit-content/src/commonTest/kotlin/**/ViewerPathsTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/ViewerPathsTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/ViewerPathsTest.kt native/ui/kit-content/src/screenshots/ViewerPaths/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerText.kt native/ui/kit-content/src/commonTest/kotlin/**/ViewerTextTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/ViewerTextTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/ViewerTextTest.kt native/ui/kit-content/src/screenshots/ViewerText/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeyBar.kt native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalKeyBarTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalKeyBarTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalKeyBarTest.kt native/ui/kit-content/src/screenshots/KitTerminalKeyBar/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeybar.kt native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalKeybarTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalKeybarTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalKeybarTest.kt native/ui/kit-content/src/screenshots/KitTerminalKeybar/** native/ui/kit-content/src/desktopTest/kotlin/**/rb/o19/** native/ui/words/src/commonMain/composeResources/values/strings_rb19.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb19.xml docs/design/native-ui-rebuild/waivers/19.tsv docs/design/native-ui-rebuild/evidence/19.md
GATE: native/tool/design/gate_order.sh 19
TIMEOUT_MIN: 240
---
# Order 19-content-b: content and charts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Command Code (GLM) (`commandcode`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-19`, branch `native/rb-19-content-b`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/19.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 19`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-19-commandcode` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_viewer_content` | `lib/ui/kit/kit_viewer_content.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_viewer_content.dart`) | – | 0 | T2 | `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitViewerBytes.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerPaths.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerText.kt` | – |
| 2 | `part-terminal_key_bar` | `lib/ui/kit/terminal_key_bar.dart` (`~/wt/flutter-ref/lib/ui/kit/terminal_key_bar.dart`) | – | 0 | T2 | `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeyBar.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeybar.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o19/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_viewer_content`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/KitViewerBytes.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/KitViewerBytesTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/KitViewerBytesTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/KitViewerBytesTest.kt`
- `native/ui/kit-content/src/screenshots/KitViewerBytes/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerPaths.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/ViewerPathsTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/ViewerPathsTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/ViewerPathsTest.kt`
- `native/ui/kit-content/src/screenshots/ViewerPaths/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/viewer/ViewerText.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/ViewerTextTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/ViewerTextTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/ViewerTextTest.kt`
- `native/ui/kit-content/src/screenshots/ViewerText/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeyBar.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalKeyBarTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalKeyBarTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalKeyBarTest.kt`
- `native/ui/kit-content/src/screenshots/KitTerminalKeyBar/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeybar.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalKeybarTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalKeybarTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalKeybarTest.kt`
- `native/ui/kit-content/src/screenshots/KitTerminalKeybar/**`
- `native/ui/kit-content/src/desktopTest/kotlin/**/rb/o19/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb19.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb19.xml`
- `docs/design/native-ui-rebuild/waivers/19.tsv`
- `docs/design/native-ui-rebuild/evidence/19.md`

## 4. Gate and done

1. `cd ~/wt/rb-19 && native/tool/design/gate_order.sh 19` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o19.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/19.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
