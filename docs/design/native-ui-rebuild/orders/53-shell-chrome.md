ORDER: 53-shell-chrome
ENGINE: sonnet
DEPENDS: 13,24
WRITES: native/ui/shell/src/commonMain/kotlin/**/ui/** native/ui/shell/src/commonMain/kotlin/**/*Screen.kt native/ui/shell/src/commonMain/kotlin/**/*Sheet*.kt native/ui/shell/src/desktopTest/kotlin/**/rb/o53/** native/ui/shell/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb53.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb53.xml docs/design/native-ui-rebuild/waivers/53.tsv docs/design/native-ui-rebuild/evidence/53.md
GATE: native/tool/design/gate_order.sh 53
TIMEOUT_MIN: 480
---
# Order 53-shell-chrome: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-53`, branch `native/rb-53-shell-chrome`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/53.gate.tsv` (50 goldens). Gate command: `native/tool/design/gate_order.sh 53`.
- Depends on: order 13, order 24. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-53-sonnet` (standard §11).

## 2. Screens

Native modules: `native/ui/shell`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-A01` | A01 | `AppBootstrapGate` (`lib/main_parts/bootstrap_gate.dart`) | state | Shell opening gate (pre-graph composable): Opening… / Updating your app… (migration) / Try again after 8 s / failure with Report a problem + Start fresh |
| `screen-A02` | A02 | — (new) | state | Shell safe mode: Report a problem, Settings, Start fresh |
| `screen-A03` | A03 | `_Root` (`lib/main_parts/root.dart`) | logic | `RootDecision` (`:engine:entry`) + `RootViewModel` (S) calling `replaceRoot`; profile removal through N12 (§2.6) |
| `screen-A05` | A05 | `HomeScreen` + `HomeOpening` | page | **merged away**: shell chrome (rail/sidebar/top-bar controls, §8.2) + `ChatsRoute.Conversations` root; opening state lives in Conversations |
| `screen-A06` | A06 | `SavedServerConnectionCard` | state | state block of Conversations (connecting / starting / not answering / credentials / start the phone server) |
| `screen-A07` | A07 | `AppConnectionStatusScope`, `AppExitNoticeLine`, `ThermalNoticeLine`, background pause notice, one-shot notices | part | shell `StatusSlot` drawn by the top page's `KitScreen`; conditions from `:engine:attention` combiner (§10.1) |
| `screen-A08` | A08 | `showServerSwitcher` | sheet | `SheetKey.ServerSwitcher` |
| `screen-A09` | A09 | `showConnectionDetailsSheet` | sheet | `SheetKey.ConnectionDetails(profileId)` |
| `screen-A10` | A10 | `KitCapabilityExplainer` + `registerCapabilityFlows` (`capability_flows.dart`) | sheet | `SheetKey.CapabilityExplainer(flow)` + `EnableFlows` table (§7.12) |
| `screen-A11` | A11 | `showProductError` | dialog | kit `KitAlert` in the screen that failed (only when no part can carry the failure) |
| `screen-A14` | A14 | `DesktopReleaseNotice`, `ShorebirdUpdateNotice`, freeze tip `kit_update_dot.dart`, `kit_update_sheet.dart`, the gear tap in `home_screen.dart` | part | status line kind `update` + `SheetKey.AppUpdate`; an update dot on the gear (compact) and on the rail/sidebar Settings item (medium+); a gear or rail-Settings tap while an update waits opens `AppUpdate` first (§8.2) |
| `screen-A15` | A15 | freeze tip `whats_new_sheet.dart` + `whats_new_places.dart` | sheet | `SheetKey.WhatsNew` (§2.3) |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/shell_*`, `test/revamp/goldens/shell_*`, `test/goldens/states_*` — 50 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/shell/src/commonMain/kotlin/**/ui/**`
- `native/ui/shell/src/commonMain/kotlin/**/*Screen.kt`
- `native/ui/shell/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/ui/shell/src/desktopTest/kotlin/**/rb/o53/**`
- `native/ui/shell/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb53.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb53.xml`
- `docs/design/native-ui-rebuild/waivers/53.tsv`
- `docs/design/native-ui-rebuild/evidence/53.md`

## 4. Gate and done

1. `cd ~/wt/rb-53 && native/tool/design/gate_order.sh 53` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o53.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/53.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
