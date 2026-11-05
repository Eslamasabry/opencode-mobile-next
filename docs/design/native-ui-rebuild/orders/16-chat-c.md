ORDER: 16-chat-c
ENGINE: sonnet
DEPENDS: 13
WRITES: native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitFindExcerpt.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitFindExcerptTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitFindExcerptTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitFindExcerptTest.kt native/ui/kit-chat/src/screenshots/KitFindExcerpt/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdownTable.kt native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerStripReveal.kt native/ui/kit-chat/src/commonTest/kotlin/**/ComposerStripRevealTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/ComposerStripRevealTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/ComposerStripRevealTest.kt native/ui/kit-chat/src/screenshots/ComposerStripReveal/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerStop.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitComposerStopTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitComposerStopTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitComposerStopTest.kt native/ui/kit-chat/src/screenshots/KitComposerStop/** native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerTray.kt native/ui/kit-chat/src/commonTest/kotlin/**/KitComposerTrayTest.kt native/ui/kit-chat/src/desktopTest/kotlin/**/KitComposerTrayTest.kt native/ui/kit-chat/src/androidHostTest/kotlin/**/KitComposerTrayTest.kt native/ui/kit-chat/src/screenshots/KitComposerTray/** native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o16/** native/ui/words/src/commonMain/composeResources/values/strings_rb16.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb16.xml docs/design/native-ui-rebuild/waivers/16.tsv docs/design/native-ui-rebuild/evidence/16.md
GATE: native/tool/design/gate_order.sh 16
TIMEOUT_MIN: 240
---
# Order 16-chat-c: chat

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-16`, branch `native/rb-16-chat-c`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/16.gate.tsv` (16 goldens). Gate command: `native/tool/design/gate_order.sh 16`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-16-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_transcript_excerpt` | `lib/ui/kit/chat/kit_transcript_excerpt.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_transcript_excerpt.dart`) | test/goldens/kit/kit_transcript_excerpt_golden_test.dart | 6 | T3 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitFindExcerpt.kt` | KitTranscriptExcerpt (conversation) |
| 2 | `part-kit_markdown_table` | `lib/ui/kit/chat/kit_markdown_table.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_markdown_table.dart`) | test/goldens/kit/kit_markdown_table_golden_test.dart | 4 | T3 | NEW `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdownTable.kt` | – |
| 3 | `part-kit_composer_caption` | `lib/ui/kit/chat/kit_composer_caption.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_caption.dart`) | – | 0 | T2 | folded into `kit_composer` (order 14-chat-a): verify only | – |
| 4 | `part-kit_composer_classic` | `lib/ui/kit/chat/kit_composer_classic.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_classic.dart`) | – | 0 | T2 | folded into `kit_composer_glow` (order 15-chat-b): verify only | – |
| 5 | `part-kit_composer_edge` | `lib/ui/kit/chat/kit_composer_edge.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_edge.dart`) | – | 0 | T2 | folded into `kit_composer` (order 14-chat-a): verify only | – |
| 6 | `part-kit_composer_layer` | `lib/ui/kit/chat/kit_composer_layer.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_layer.dart`) | – | 0 | T2 | folded into `kit_composer` (order 14-chat-a): verify only | – |
| 7 | `part-kit_composer_status_strip` | `lib/ui/kit/chat/kit_composer_status_strip.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_status_strip.dart`) | test/goldens/kit/kit_composer_status_strip_golden_test.dart | 6 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerStripReveal.kt`<br>`native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerStop.kt` | KitComposerStatusStrip (conversation) |
| 8 | `part-kit_composer_tray` | `lib/ui/kit/chat/kit_composer_tray.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_tray.dart`) | – | 0 | T2 | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerTray.kt` | – |
| 9 | `part-kit_composer_types` | `lib/ui/kit/chat/kit_composer_types.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_composer_types.dart`) | – | 0 | T2 | folded into `kit_composer` (order 14-chat-a): verify only | – |
| 10 | `part-kit_find_mark` | `lib/ui/kit/chat/kit_find_mark.dart` (`~/wt/flutter-ref/lib/ui/kit/chat/kit_find_mark.dart`) | – | 0 | T2 | folded into `kit_transcript_excerpt` (order 16-chat-c): verify only | – |

Folded parts: their Dart code lives in a native file another order owns. Read them, check that the owner ported them (every constant, every branch), and record each gap in `evidence/16.md` under "Folded gaps"; never edit the owner's file.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/KitFindExcerpt.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitFindExcerptTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitFindExcerptTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitFindExcerptTest.kt`
- `native/ui/kit-chat/src/screenshots/KitFindExcerpt/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdownTable.kt`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/ComposerStripReveal.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/ComposerStripRevealTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/ComposerStripRevealTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/ComposerStripRevealTest.kt`
- `native/ui/kit-chat/src/screenshots/ComposerStripReveal/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerStop.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitComposerStopTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitComposerStopTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitComposerStopTest.kt`
- `native/ui/kit-chat/src/screenshots/KitComposerStop/**`
- `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerTray.kt`
- `native/ui/kit-chat/src/commonTest/kotlin/**/KitComposerTrayTest.kt`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/KitComposerTrayTest.kt`
- `native/ui/kit-chat/src/androidHostTest/kotlin/**/KitComposerTrayTest.kt`
- `native/ui/kit-chat/src/screenshots/KitComposerTray/**`
- `native/ui/kit-chat/src/desktopTest/kotlin/**/rb/o16/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb16.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb16.xml`
- `docs/design/native-ui-rebuild/waivers/16.tsv`
- `docs/design/native-ui-rebuild/evidence/16.md`

## 4. Gate and done

1. `cd ~/wt/rb-16 && native/tool/design/gate_order.sh 16` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o16.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/16.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
