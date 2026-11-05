ORDER: 42-chat
ENGINE: sonnet
DEPENDS: 13,14,15,16,17,18,20,21
WRITES: native/feature/chat/src/commonMain/kotlin/**/ui/** native/feature/chat/src/commonMain/kotlin/**/*Screen.kt native/feature/chat/src/commonMain/kotlin/**/*Sheet*.kt native/feature/chat/src/desktopTest/kotlin/**/rb/o42/** native/feature/chat/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb42.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb42.xml docs/design/native-ui-rebuild/waivers/42.tsv docs/design/native-ui-rebuild/evidence/42.md
GATE: native/tool/design/gate_order.sh 42
TIMEOUT_MIN: 480
---
# Order 42-chat: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-42`, branch `native/rb-42-chat`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/42.gate.tsv` (280 goldens). Gate command: `native/tool/design/gate_order.sh 42`.
- Depends on: order 13, order 14, order 15, order 16, order 17, order 18, order 20, order 21. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-42-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/chat`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-C01` | C01 | `ChatScreen` (71 files, `screens/chat_screen.dart` + `screens/chat/*`) | page | `ChatRoute.Chat(ref)` + `ChatArrival` |
| `screen-C02` | C02 | find bar (`chat/transcript_find.dart`) | part | in-page mode of C01 |
| `screen-C03` | C03 | timeline sheet (`chat/timeline_sheet.dart`) | sheet | local sheet of C01 |
| `screen-C04` | C04 | message actions (`chat/chat_message_actions.dart`) | sheet/menu | local `KitMenu` of C01 |
| `screen-C05` | C05 | `showStageRevertSheet` | sheet | local sheet of C01 → `ChatPagesRoute.StagedRevert` |
| `screen-C06` | C06 | session menu (`widgets/session_menu.dart`, `chat/chat_session_menu.dart`) | menu | local `KitMenu` of C01 (top-bar overflow) |
| `screen-C07` | C07 | context ring sheet | sheet | local sheet of C01 → `ChatPagesRoute.Context` |
| `screen-C08` | C08 | `showRunningWorkSheet` | sheet | local sheet of C01 |
| `screen-C09` | C09 | `ShellOutputScreen` | page | `ChatPagesRoute.ShellOutput(ref, shellId)` |
| `screen-C11` | C11 | `showPermissionSheet`, `showPermissionDetails`, `showQuestionSheet`, `showQuestionDetails`, forms (`widgets/form_renderer.dart`) | sheet | `SheetKey.Permission` / `Question` / `Form` (`RequestRef`) |
| `screen-C12` | C12 | `AlwaysAllowInvitation` | part | inline part of C10 |
| `screen-C13` | C13 | `showSessionApprovalsSheet` | sheet | `SheetKey.Approvals(ref)` |
| `screen-C14` | C14 | `showModelPicker` + `ModelCatalogView` | sheet | `SheetKey.ModelPicker(scope)` |
| `screen-C17` | C17 | `showRunCommandDialog` | dialog | `SheetKey.RunCommand(profileId, commandId, ref?)` |
| `screen-C21` | C21 | chat gallery + markup editor (`chat/chat_gallery.dart`, `showKitImageGallery`, `showKitImageMarkup`) | overlay | local full-screen overlay in C01 (kit) |
| `screen-C24` | C24 | `showVoiceModelSetupSheet` | sheet | `SheetKey.VoiceModelSetup` |
| `screen-C25` | C25 | voice conversation, read aloud voice chooser (`chat/voice_conversation.dart`, `chat/read_aloud.dart`) | state/sheet | state of C01; local sheet |
| `screen-C26` | C26 | queued message menu + `showQueuedPromptMoveSheet` | menu/sheet | menu local; `SheetKey.QueuedPromptMove(profileId)` |
| `screen-C28` | C28 | `showContinueWith`, `showContinueOnComputerSheet`, `showContinueOnPhoneSheet` | sheet | `SheetKey.ContinueWith` / `ContinueOnComputer` / `ContinueOnPhone` |
| `screen-C29` | C29 | `showSessionDestinationSheet`, `showConsoleOrganizationSheet` | sheet | `SheetKey.SessionDestination(ref, mode)` |
| `screen-C30` | C30 | rename dialog, archive / delete / share / unshare confirms | dialog | local kit dialogs of C01 |
| `screen-C31` | C31 | `SessionContextScreen` | page | `ChatPagesRoute.Context(ref)` |
| `screen-C32` | C32 | `ActiveContextScreen` | page | `ChatPagesRoute.ActiveContext(ref)` |
| `screen-C33` | C33 | `SessionRelationsScreen` | page | `ChatPagesRoute.Relations(ref)` |
| `screen-C34` | C34 | `SessionNoteScreen` | page | `ChatPagesRoute.Note(ref)` |
| `screen-C35` | C35 | `SessionExportScreen` | page | `ChatPagesRoute.Export(ref)` |
| `screen-C36` | C36 | `StagedRevertScreen` | page | `ChatPagesRoute.StagedRevert(ref, messageId)` |
| `screen-C37` | C37 | `DevPreviewScreen` / `openDevPreview` | page | `ProjectRoute.DevPreview(profileId, secure, port, path, from)` |
| `screen-C38` | C38 | `showDevServerLogs` | sheet | local sheet of C37 |
| `screen-C39` | C39 | `DemoScreen` ("Try it offline") | page | `ChatRoute.Demo` (the production chat on a demo connection graph and in-memory stores) |
| `screen-C40` | C40 | agent cards, connector cards (`chat/agent_cards.dart`, `widgets/agent_card_*`, `connector_card.dart`) | part | kit-chat parts in the transcript |
| `screen-C41` | C41 | watching mode, `TeamWatchLiveScreen`, `TeamConversation*` | page/state | **deferred** with AI Team (D6) |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/chat_*`, `test/goldens/evidence/chat_*`, `test/goldens/handoff_*`, `test/goldens/agent_card_*`, `test/revamp/goldens/chat_*`, `test/revamp/goldens/chat2_*`, `test/revamp/goldens/queued_*`, `test/revamp/goldens/undo_*`, `test/revamp/goldens/close_*` — 280 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/chat/src/commonMain/kotlin/**/ui/**`
- `native/feature/chat/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/chat/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/chat/src/desktopTest/kotlin/**/rb/o42/**`
- `native/feature/chat/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb42.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb42.xml`
- `docs/design/native-ui-rebuild/waivers/42.tsv`
- `docs/design/native-ui-rebuild/evidence/42.md`

## 4. Gate and done

1. `cd ~/wt/rb-42 && native/tool/design/gate_order.sh 42` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o42.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/42.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
