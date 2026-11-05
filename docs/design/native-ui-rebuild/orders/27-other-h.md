ORDER: 27-other-h
ENGINE: codex
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitPlatform.kt native/ui/kit/src/commonTest/kotlin/**/KitPlatformTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitPlatformTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitPlatformTest.kt native/ui/kit/src/screenshots/KitPlatform/** native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidKitPlatform.kt native/ui/kit/src/commonTest/kotlin/**/AndroidKitPlatformTest.kt native/ui/kit/src/desktopTest/kotlin/**/AndroidKitPlatformTest.kt native/ui/kit/src/androidHostTest/kotlin/**/AndroidKitPlatformTest.kt native/ui/kit/src/screenshots/AndroidKitPlatform/** native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/DesktopKitPlatform.kt native/ui/kit/src/commonTest/kotlin/**/DesktopKitPlatformTest.kt native/ui/kit/src/desktopTest/kotlin/**/DesktopKitPlatformTest.kt native/ui/kit/src/androidHostTest/kotlin/**/DesktopKitPlatformTest.kt native/ui/kit/src/screenshots/DesktopKitPlatform/** native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitDesktopSettingsMenu.kt native/ui/kit/src/commonTest/kotlin/**/KitDesktopSettingsMenuTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitDesktopSettingsMenuTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitDesktopSettingsMenuTest.kt native/ui/kit/src/screenshots/KitDesktopSettingsMenu/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskSheet.kt native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalLinks.kt native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalLinksTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalLinksTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalLinksTest.kt native/ui/kit-content/src/screenshots/KitTerminalLinks/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinks.kt native/ui/kit-content/src/commonTest/kotlin/**/TerminalLinksTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/TerminalLinksTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/TerminalLinksTest.kt native/ui/kit-content/src/screenshots/TerminalLinks/** native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinkHost.kt native/ui/kit-content/src/commonTest/kotlin/**/TerminalLinkHostTest.kt native/ui/kit-content/src/desktopTest/kotlin/**/TerminalLinkHostTest.kt native/ui/kit-content/src/androidHostTest/kotlin/**/TerminalLinkHostTest.kt native/ui/kit-content/src/screenshots/TerminalLinkHost/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitTextHalo.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitChatHeaderPartsTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitChatHeaderPartsTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitChatHeaderPartsTest.kt native/ui/kit-chat/src/screenshots/KitChatHeaderParts/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/logo/KitProviderLogo.kt native/ui/kit/src/commonTest/kotlin/**/KitProviderLogoTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitProviderLogoTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitProviderLogoTest.kt native/ui/kit/src/screenshots/KitProviderLogo/** native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitStaticSvg.kt native/ui/kit/src/commonTest/kotlin/**/KitStaticSvgTest.kt native/ui/kit/src/desktopTest/kotlin/**/KitStaticSvgTest.kt native/ui/kit/src/androidHostTest/kotlin/**/KitStaticSvgTest.kt native/ui/kit/src/screenshots/KitStaticSvg/** native/ui/kit/src/desktopTest/kotlin/**/rb/o27/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o27/** native/ui/kit-content/src/desktopTest/kotlin/**/rb/o27/** native/ui/words/src/commonMain/composeResources/values/strings_rb27.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb27.xml docs/design/native-ui-rebuild/waivers/27.tsv docs/design/native-ui-rebuild/evidence/27.md
GATE: native/tool/design/gate_order.sh 27
TIMEOUT_MIN: 240
---
# Order 27-other-h: other parts

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-27`, branch `native/rb-27-other-h`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/27.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 27`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-27-codex` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_surface_paint` | `lib/ui/kit/kit_surface_paint.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_surface_paint.dart`) | – | 0 | T2 | folded into `kit_surface` (order 01-foundation-a): verify only | – |
| 2 | `part-kit_system_bars` | `lib/ui/kit/kit_system_bars.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_system_bars.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitPlatform.kt`<br>`native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidKitPlatform.kt`<br>`native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/DesktopKitPlatform.kt`<br>`native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitDesktopSettingsMenu.kt` | – |
| 3 | `part-kit_task_sheet` | `lib/ui/kit/kit_task_sheet.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_task_sheet.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskSheet.kt` | showKitTaskSheet (sheet) |
| 4 | `part-kit_terminal_links` | `lib/ui/kit/kit_terminal_links.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_terminal_links.dart`) | – | 0 | T2 | `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalLinks.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinks.kt`<br>`native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinkHost.kt` | showKitTerminalLinkSheet (sheet) |
| 5 | `part-kit_text_halo` | `lib/ui/kit/kit_text_halo.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_text_halo.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitTextHalo.kt` | KitTextHalo (wrapper) |
| 6 | `part-kit_top_bar_centred` | `lib/ui/kit/kit_top_bar_centred.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_top_bar_centred.dart`) | – | 0 | T2 | folded into `kit_top_bar` (order 13-frame-nav): verify only | – |
| 7 | `part-kit_top_bar_pill` | `lib/ui/kit/kit_top_bar_pill.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_top_bar_pill.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt` | – |
| 8 | `part-kit_top_bar_scope` | `lib/ui/kit/kit_top_bar_scope.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_top_bar_scope.dart`) | – | 0 | T2 | folded into `kit_top_bar` (order 13-frame-nav): verify only | – |
| 9 | `part-kit_top_bar_tail` | `lib/ui/kit/kit_top_bar_tail.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_top_bar_tail.dart`) | – | 0 | T2 | folded into `kit_top_bar` (order 13-frame-nav): verify only | – |
| 10 | `part-kit_vector_logo` | `lib/ui/kit/kit_vector_logo.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_vector_logo.dart`) | – | 0 | T2 | `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/logo/KitProviderLogo.kt`<br>`native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitStaticSvg.kt` | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/27.md` under "Folded gaps"; never edit the owner's file.

Logic-only parts (no widget, no golden): each needs `rb/o27/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_system_bars`, `kit_terminal_links`, `kit_text_halo`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitPlatform.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitPlatformTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitPlatformTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitPlatformTest.kt`
- `native/ui/kit/src/screenshots/KitPlatform/**`
- `native/ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidKitPlatform.kt`
- `native/ui/kit/src/commonTest/kotlin/**/AndroidKitPlatformTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/AndroidKitPlatformTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/AndroidKitPlatformTest.kt`
- `native/ui/kit/src/screenshots/AndroidKitPlatform/**`
- `native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/DesktopKitPlatform.kt`
- `native/ui/kit/src/commonTest/kotlin/**/DesktopKitPlatformTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/DesktopKitPlatformTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/DesktopKitPlatformTest.kt`
- `native/ui/kit/src/screenshots/DesktopKitPlatform/**`
- `native/ui/kit/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitDesktopSettingsMenu.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitDesktopSettingsMenuTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitDesktopSettingsMenuTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitDesktopSettingsMenuTest.kt`
- `native/ui/kit/src/screenshots/KitDesktopSettingsMenu/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTaskSheet.kt`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalLinks.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/KitTerminalLinksTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/KitTerminalLinksTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/KitTerminalLinksTest.kt`
- `native/ui/kit-content/src/screenshots/KitTerminalLinks/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinks.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/TerminalLinksTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/TerminalLinksTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/TerminalLinksTest.kt`
- `native/ui/kit-content/src/screenshots/TerminalLinks/**`
- `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/TerminalLinkHost.kt`
- `native/ui/kit-content/src/commonTest/kotlin/**/TerminalLinkHostTest.kt`
- `native/ui/kit-content/src/desktopTest/kotlin/**/TerminalLinkHostTest.kt`
- `native/ui/kit-content/src/androidHostTest/kotlin/**/TerminalLinkHostTest.kt`
- `native/ui/kit-content/src/screenshots/TerminalLinkHost/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitTextHalo.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitChatHeaderPartsTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitChatHeaderPartsTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitChatHeaderPartsTest.kt`
- `native/ui/kit-chat/src/screenshots/KitChatHeaderParts/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/logo/KitProviderLogo.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitProviderLogoTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitProviderLogoTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitProviderLogoTest.kt`
- `native/ui/kit/src/screenshots/KitProviderLogo/**`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/KitStaticSvg.kt`
- `native/ui/kit/src/commonTest/kotlin/**/KitStaticSvgTest.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/KitStaticSvgTest.kt`
- `native/ui/kit/src/androidHostTest/kotlin/**/KitStaticSvgTest.kt`
- `native/ui/kit/src/screenshots/KitStaticSvg/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o27/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o27/**`
- `native/ui/kit-content/src/desktopTest/kotlin/**/rb/o27/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb27.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb27.xml`
- `docs/design/native-ui-rebuild/waivers/27.tsv`
- `docs/design/native-ui-rebuild/evidence/27.md`

## 4. Gate and done

1. `cd ~/wt/rb-27 && native/tool/design/gate_order.sh 27` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o27.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/27.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
