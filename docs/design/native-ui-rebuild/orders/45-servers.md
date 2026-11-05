ORDER: 45-servers
ENGINE: sonnet
DEPENDS: 13,21,29
WRITES: native/feature/servers/src/commonMain/kotlin/**/ui/** native/feature/servers/src/commonMain/kotlin/**/*Screen.kt native/feature/servers/src/commonMain/kotlin/**/*Sheet*.kt native/feature/servers/src/desktopTest/kotlin/**/rb/o45/** native/feature/servers/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb45.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb45.xml docs/design/native-ui-rebuild/waivers/45.tsv docs/design/native-ui-rebuild/evidence/45.md
GATE: native/tool/design/gate_order.sh 45
TIMEOUT_MIN: 480
---
# Order 45-servers: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-45`, branch `native/rb-45-servers`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/45.gate.tsv` (155 goldens). Gate command: `native/tool/design/gate_order.sh 45`.
- Depends on: order 13, order 21, order 29. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-45-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/servers`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-E01` | E01 | `ServersScreen` (+ `servers/*`, welcome, `PhoneSetupWelcomeEntry`) | page | `ServersRoute.Servers` (root when no **active** profile or re-entry; pushed from Settings) + `ServersArrival` (Connect, Add, EnterPhoneCredentials, Edit, Forget; §2.5) |
| `screen-E02` | E02 | profile editor (`servers/profile_editor*.dart`, 7 files) | page | `ServersRoute.Editor(mode, connectOnSave, focusPassword)` |
| `screen-E03` | E03 | `PairingScannerScreen` | page | `ServersRoute.PairingScanner` |
| `screen-E04` | E04 | `ServerPageScreen` / `openServerPage` (`settings/server_page.dart`) | page | `ServersRoute.ServerPage(profileId)` |
| `screen-E05` | E05 | `ConnectionCheckScreen` | page | `ServersRoute.ConnectionCheck(profileId)` |
| `screen-E06` | E06 | `ReachComputerScreen` | page | `ServersRoute.ReachComputer` |
| `screen-E07` | E07 | `HostManagementScreen` | page | `ServersRoute.HostSetup(profileId)` |
| `screen-E08` | E08 | `ServerCapabilitiesScreen` | page | `ServersRoute.Capabilities(profileId)` |
| `screen-E09` | E09 | `TailscaleSetupScreen` | page | `ServersRoute.Tailscale` |
| `screen-E10` | E10 | `showSessionAddressSheet`, `showSessionHandoff` | sheet | `SheetKey.SessionAddress(linkTicket)` |
| `screen-E11` | E11 | `openMonitoredRequest` switch dialog | dialog | `SheetKey.SwitchServer(profileId, reason)` |
| `screen-E12` | E12 | `PhoneServerCard`, `LocalServerRow`, `TermuxRunningServerEntry`, `LocalAgentServerEntry` | part | rows inside E01 and F01 (kit rows) |
| `screen-E13` | E13 | `queued_prompt_move_sheet.dart` | sheet | `SheetKey.QueuedPromptMove(fromProfileId)` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/add_*`, `test/goldens/servers_*`, `test/goldens/connection_*`, `test/goldens/connector_*`, `test/revamp/goldens/servers_*`, `test/revamp/goldens/server_*`, `test/revamp/goldens/cred_*` — 155 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/servers/src/commonMain/kotlin/**/ui/**`
- `native/feature/servers/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/servers/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/servers/src/desktopTest/kotlin/**/rb/o45/**`
- `native/feature/servers/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb45.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb45.xml`
- `docs/design/native-ui-rebuild/waivers/45.tsv`
- `docs/design/native-ui-rebuild/evidence/45.md`

## 4. Gate and done

1. `cd ~/wt/rb-45 && native/tool/design/gate_order.sh 45` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o45.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/45.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
