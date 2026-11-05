ORDER: 43-chat-composer
ENGINE: sonnet
DEPENDS: 13,14,15,16,20
WRITES: native/feature/chat-composer/src/commonMain/kotlin/**/ui/** native/feature/chat-composer/src/commonMain/kotlin/**/*Screen.kt native/feature/chat-composer/src/commonMain/kotlin/**/*Sheet*.kt native/feature/chat-composer/src/desktopTest/kotlin/**/rb/o43/** native/feature/chat-composer/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb43.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb43.xml docs/design/native-ui-rebuild/waivers/43.tsv docs/design/native-ui-rebuild/evidence/43.md
GATE: native/tool/design/gate_order.sh 43
TIMEOUT_MIN: 480
---
# Order 43-chat-composer: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-43`, branch `native/rb-43-chat-composer`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/43.gate.tsv` (16 goldens). Gate command: `native/tool/design/gate_order.sh 43`.
- Depends on: order 13, order 14, order 15, order 16, order 20. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-43-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/chat-composer`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-C10` | C10 | request card (`chat/attention_card.dart`) | part | kit-chat part above the composer in C01 |
| `screen-C15` | C15 | `showAgentFeaturesSheet` | sheet | local sheet of the composer |
| `screen-C16` | C16 | command sheet (`widgets/command_sheet.dart`) | sheet | local sheet of the composer; body is the kit part `KitCommandList` (also used by Settings › Tools › Commands) |
| `screen-C18` | C18 | `!` shell dialog, command-auth sheet (`library/command_auth_sheet.dart`) | dialog/sheet | local to the composer |
| `screen-C19` | C19 | + tray and More sheet (`chat/composer_tools.dart`, `composer_tray.dart`) | part/sheet | composer row + local sheet |
| `screen-C20` | C20 | `WebSourcesScreen` | page | `ComposerRoute.WebSources(ref)` |
| `screen-C22` | C22 | full-screen prompt editor (`chat/prompt_editor.dart`) | overlay | local full-screen overlay in C01, **not a route**: it shares the composer's `TextFieldState` (ADR §9.2) |
| `screen-C23` | C23 | saved prompts, reuse a prompt (`chat/prompt_stash.dart`, `prompt_history.dart`) | sheet | local sheets of the composer |
| `screen-C27` | C27 | schedule from chat (`chat/chat_schedule_send.dart`) | sheet | `SheetKey.Schedule(null, ref)` after `:engine:schedule` seeds the draft from the composer text |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/chat_approval_*` — 16 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/chat-composer/src/commonMain/kotlin/**/ui/**`
- `native/feature/chat-composer/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/chat-composer/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/chat-composer/src/desktopTest/kotlin/**/rb/o43/**`
- `native/feature/chat-composer/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb43.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb43.xml`
- `docs/design/native-ui-rebuild/waivers/43.tsv`
- `docs/design/native-ui-rebuild/evidence/43.md`

## 4. Gate and done

1. `cd ~/wt/rb-43 && native/tool/design/gate_order.sh 43` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o43.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/43.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
