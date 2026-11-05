ORDER: 41-conversations
ENGINE: sonnet
DEPENDS: 13,14,15,24
WRITES: native/feature/chats/src/commonMain/kotlin/**/ui/** native/feature/chats/src/commonMain/kotlin/**/*Screen.kt native/feature/chats/src/commonMain/kotlin/**/*Sheet*.kt native/feature/chats/src/desktopTest/kotlin/**/rb/o41/** native/feature/chats/build.gradle.kts native/feature/search/src/commonMain/kotlin/**/ui/** native/feature/search/src/commonMain/kotlin/**/*Screen.kt native/feature/search/src/commonMain/kotlin/**/*Sheet*.kt native/feature/search/src/desktopTest/kotlin/**/rb/o41/** native/feature/search/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb41.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb41.xml docs/design/native-ui-rebuild/waivers/41.tsv docs/design/native-ui-rebuild/evidence/41.md
GATE: native/tool/design/gate_order.sh 41
TIMEOUT_MIN: 480
---
# Order 41-conversations: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-41`, branch `native/rb-41-conversations`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/41.gate.tsv` (186 goldens). Gate command: `native/tool/design/gate_order.sh 41`.
- Depends on: order 13, order 14, order 15, order 24. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-41-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/chats`, `native/feature/search`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-A12` | A12 | `showCommandPalette` (`desktop/shortcuts.dart`) | sheet | `SheetKey.CommandLauncher` |
| `screen-A13` | A13 | `showShortcutsHelp` | sheet | `SheetKey.ShortcutsHelp` |
| `screen-B01` | B01 | `ChatsHomeScreen` (`screens/chats/chats_home_screen.dart`) | page | `ChatsRoute.Conversations` (root, Top) |
| `screen-B02` | B02 | request cards inside rows (`chats_host.dart listRequest`) | part | part of B01 (kit-chat request card, secondary buttons) |
| `screen-B03` | B03 | `showChatsProjectSheet` | sheet | local sheet of B01 |
| `screen-B04` | B04 | `showChatsSourcesSheet` | sheet | local sheet of B01 |
| `screen-B05` | B05 | `InboxScreen` / `openInbox` | page | `ChatsRoute.Inbox` |
| `screen-B06` | B06 | `ProfileMonitorInbox` | part | **dropped**: not mounted anywhere in this checkout; its rows are Inbox `InboxWatchedEntry` rows |
| `screen-B07` | B07 | `NewChatScreen` / `showNewChat` | page | `ChatsRoute.NewConversation(profileId?, projectDir?)` |
| `screen-B08` | B08 | `showAgentModelSheet` (`screens/agents/agent_model_sheet.dart`) | sheet | local sheet of B07 |
| `screen-B09` | B09 | `showGitRepairSheet` | sheet | local sheet of B07 |
| `screen-B10` | B10 | `showIsolatedTaskSheet` | sheet | local sheet of B07 only (Flutter's only caller is New conversation, `new_chat_screen.dart:235` → `startSeparateCopy`) |
| `screen-B11` | B11 | `showClaudeImport` / `ClaudeImportScreen` | page | `ChatsRoute.ClaudeImport(profileId, projectDir)` |
| `screen-B12` | B12 | `GlobalSessionsScreen` | page | `ChatsRoute.AllConversations(profileId, archived)` |
| `screen-B13` | B13 | `SessionImportScreen` | page | `ChatsRoute.ImportConversation(profileId)` |
| `screen-B14` | B14 | `OlderSessionsPager`, `LastKnownSessions`, `KitFeedItem` rows | part | kit parts used by B01 |
| `screen-B15` | B15 | `ReturnBrief` (`lib/state/return_brief_state.dart` + its Conversations part) | part | first block of B01 on the first visit after the person was away (STATE-DATA-24), above Today; never a page, never a new bar level (it takes the place of the first rows and folds into them) |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/chats_*`, `test/goldens/work_*`, `test/revamp/goldens/work_*`, `test/revamp/goldens/recent_*`, `test/revamp/goldens/sessionlink_*` — 186 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/chats/src/commonMain/kotlin/**/ui/**`
- `native/feature/chats/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/chats/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/chats/src/desktopTest/kotlin/**/rb/o41/**`
- `native/feature/chats/build.gradle.kts`
- `native/feature/search/src/commonMain/kotlin/**/ui/**`
- `native/feature/search/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/search/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/search/src/desktopTest/kotlin/**/rb/o41/**`
- `native/feature/search/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb41.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb41.xml`
- `docs/design/native-ui-rebuild/waivers/41.tsv`
- `docs/design/native-ui-rebuild/evidence/41.md`

## 4. Gate and done

1. `cd ~/wt/rb-41 && native/tool/design/gate_order.sh 41` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o41.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/41.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
