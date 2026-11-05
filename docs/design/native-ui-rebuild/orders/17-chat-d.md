ORDER: 17-chat-d
ENGINE: sonnet
DEPENDS: 13
WRITES: native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/FreshFade.kt native/ui/kit-chat/src/commonTest/kotlin/**/FreshFadeTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/FreshFadeTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/FreshFadeTest.kt native/ui/kit-chat/src/screenshots/FreshFade/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/Reveal.kt native/ui/kit-chat/src/commonTest/kotlin/**/RevealTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/RevealTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/RevealTest.kt native/ui/kit-chat/src/screenshots/Reveal/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitPictureTiles.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitPictureTilesTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitPictureTilesTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPictureTilesTest.kt native/ui/kit-chat/src/screenshots/KitPictureTiles/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/image/KitPictureView.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitPictureViewTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitPictureViewTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPictureViewTest.kt native/ui/kit-chat/src/screenshots/KitPictureView/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitPromptLift.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitPromptLiftTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitPromptLiftTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPromptLiftTest.kt native/ui/kit-chat/src/screenshots/KitPromptLift/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileSheet.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepLight.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitStepLightTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitStepLightTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitStepLightTest.kt native/ui/kit-chat/src/screenshots/KitStepLight/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitToolNode.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitToolNodeTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitToolNodeTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitToolNodeTest.kt native/ui/kit-chat/src/screenshots/KitToolNode/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepSignal.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitContextRegion.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o17/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o17/** native/ui/words/src/commonMain/composeResources/values/strings_rb17.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb17.xml docs/design/native-ui-rebuild/waivers/17.tsv docs/design/native-ui-rebuild/evidence/17.md
GATE: native/tool/design/gate_order.sh 17
TIMEOUT_MIN: 240
---
# Order 17-chat-d: chat

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-17`, branch `native/rb-17-chat-d`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/17.gate.tsv` (40 goldens). Gate command: `native/tool/design/gate_order.sh 17`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-17-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_markdown_fresh` | `lib/ui/kit/chat/kit_markdown_fresh.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_markdown_fresh.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/FreshFade.kt`<br>`native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/Reveal.kt` | – |
| 2 | `part-kit_markdown_image` | `lib/ui/kit/chat/kit_markdown_image.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_markdown_image.dart`) | – | 0 | T3 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitPictureTiles.kt`<br>`native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/image/KitPictureView.kt` | – |
| 3 | `part-kit_message_lift` | `lib/ui/kit/chat/kit_message_lift.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_message_lift.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitPromptLift.kt` | – |
| 4 | `part-kit_project_file_sheet` | `lib/ui/kit/chat/kit_project_file_sheet.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_project_file_sheet.dart`) | – | 0 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileSheet.kt` | showKitProjectFileSheet (sheet) |
| 5 | `part-kit_step_light` | `lib/ui/kit/chat/kit_step_light.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_step_light.dart`) | test/goldens/kit/kit_step_light_golden_test.dart | 40 | T4 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepLight.kt` | – |
| 6 | `part-kit_step_node` | `lib/ui/kit/chat/kit_step_node.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_step_node.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitToolNode.kt` | – |
| 7 | `part-kit_step_signal` | `lib/ui/kit/chat/kit_step_signal.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_step_signal.dart`) | – | 0 | T2 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepSignal.kt` | – |
| 8 | `part-kit_context_region` | `lib/ui/kit/kit_context_region.dart` (`~/wt/flutter-ref/lib/ui/kit/kit_context_region.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitContextRegion.kt` | KitContextRegion (wrapper) |

Logic-only parts (no widget, no golden): each needs `rb/o17/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_project_file_sheet`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/FreshFade.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/FreshFadeTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/FreshFadeTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/FreshFadeTest.kt`
- `native/ui/kit-chat/src/screenshots/FreshFade/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/Reveal.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/RevealTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/RevealTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/RevealTest.kt`
- `native/ui/kit-chat/src/screenshots/Reveal/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitPictureTiles.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitPictureTilesTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitPictureTilesTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPictureTilesTest.kt`
- `native/ui/kit-chat/src/screenshots/KitPictureTiles/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/image/KitPictureView.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitPictureViewTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitPictureViewTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPictureViewTest.kt`
- `native/ui/kit-chat/src/screenshots/KitPictureView/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitPromptLift.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitPromptLiftTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitPromptLiftTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitPromptLiftTest.kt`
- `native/ui/kit-chat/src/screenshots/KitPromptLift/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/files/KitProjectFileSheet.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepLight.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitStepLightTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitStepLightTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitStepLightTest.kt`
- `native/ui/kit-chat/src/screenshots/KitStepLight/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitToolNode.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitToolNodeTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitToolNodeTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitToolNodeTest.kt`
- `native/ui/kit-chat/src/screenshots/KitToolNode/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitStepSignal.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitContextRegion.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o17/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o17/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb17.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb17.xml`
- `docs/design/native-ui-rebuild/waivers/17.tsv`
- `docs/design/native-ui-rebuild/evidence/17.md`

## 4. Gate and done

1. `cd ~/wt/rb-17 && native/tool/design/gate_order.sh 17` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o17.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/17.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
