ORDER: 50-usage-plans-schedules-prompts
ENGINE: codex
DEPENDS: 13,18,22,23
WRITES: native/feature/usage/src/commonMain/kotlin/**/ui/** native/feature/usage/src/commonMain/kotlin/**/*Screen.kt native/feature/usage/src/commonMain/kotlin/**/*Sheet*.kt native/feature/usage/src/desktopTest/kotlin/**/rb/o50/** native/feature/usage/build.gradle.kts native/feature/plans/src/commonMain/kotlin/**/ui/** native/feature/plans/src/commonMain/kotlin/**/*Screen.kt native/feature/plans/src/commonMain/kotlin/**/*Sheet*.kt native/feature/plans/src/desktopTest/kotlin/**/rb/o50/** native/feature/plans/build.gradle.kts native/feature/schedules/src/commonMain/kotlin/**/ui/** native/feature/schedules/src/commonMain/kotlin/**/*Screen.kt native/feature/schedules/src/commonMain/kotlin/**/*Sheet*.kt native/feature/schedules/src/desktopTest/kotlin/**/rb/o50/** native/feature/schedules/build.gradle.kts native/feature/prompts/src/commonMain/kotlin/**/ui/** native/feature/prompts/src/commonMain/kotlin/**/*Screen.kt native/feature/prompts/src/commonMain/kotlin/**/*Sheet*.kt native/feature/prompts/src/desktopTest/kotlin/**/rb/o50/** native/feature/prompts/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb50.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb50.xml docs/design/native-ui-rebuild/waivers/50.tsv docs/design/native-ui-rebuild/evidence/50.md
GATE: native/tool/design/gate_order.sh 50
TIMEOUT_MIN: 360
---
# Order 50-usage-plans-schedules-prompts: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-50`, branch `native/rb-50-usage-plans-schedules-prompts`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/50.gate.tsv` (25 goldens). Gate command: `native/tool/design/gate_order.sh 50`.
- Depends on: order 13, order 18, order 22, order 23. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-50-codex` (standard §11).

## 2. Screens

Native modules: `native/feature/usage`, `native/feature/plans`, `native/feature/schedules`, `native/feature/prompts`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-J01` | J01 | `UsageHubScreen` (+ `UsagePlansPage`, `UsageScreen` spending, `UsageRefreshSlot`) | page | `UsageRoute.Hub(section)` |
| `screen-J02` | J02 | `UsageSubscriptionScreen` | page | `UsageRoute.Subscription(key)` |
| `screen-J03` | J03 | `UsageAddPickerScreen` + `UsageAddScreen` | page | **merged** (owner choice O-07-6, default merge): `UsageRoute.Add(profileId?)` with the connection choice as its first step (§6.2 M4) |
| `screen-J04` | J04 | `UsageReadFromScreen` | page | `UsageRoute.ReadFrom(profileId?, providerId?)` |
| `screen-J05` | J05 | `UsageAlertsScreen` | page | `UsageRoute.Alerts` |
| `screen-J06` | J06 | `UsageWidgetScreen` | page | `UsageRoute.WidgetSettings` |
| `screen-J07` | J07 | budget set / clear dialogs | dialog | local dialogs of J01 |
| `screen-K01` | K01 | `ScheduledPromptsScreen` / `openScheduledPrompts` / `openStoredScheduledPrompts` | page | `SchedulesRoute.List(profileId, projectDir, from?)` (+ `HighlightArrival(scheduleId)`) |
| `screen-K02` | K02 | `showScheduleSheet` (+ `KitWheel`, history) | sheet | `SheetKey.Schedule(scheduleId?, ref?)` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/revamp/goldens/usage_*`, `test/revamp/goldens/saved_*` — 25 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/usage/src/commonMain/kotlin/**/ui/**`
- `native/feature/usage/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/usage/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/usage/src/desktopTest/kotlin/**/rb/o50/**`
- `native/feature/usage/build.gradle.kts`
- `native/feature/plans/src/commonMain/kotlin/**/ui/**`
- `native/feature/plans/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/plans/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/plans/src/desktopTest/kotlin/**/rb/o50/**`
- `native/feature/plans/build.gradle.kts`
- `native/feature/schedules/src/commonMain/kotlin/**/ui/**`
- `native/feature/schedules/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/schedules/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/schedules/src/desktopTest/kotlin/**/rb/o50/**`
- `native/feature/schedules/build.gradle.kts`
- `native/feature/prompts/src/commonMain/kotlin/**/ui/**`
- `native/feature/prompts/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/prompts/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/prompts/src/desktopTest/kotlin/**/rb/o50/**`
- `native/feature/prompts/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb50.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb50.xml`
- `docs/design/native-ui-rebuild/waivers/50.tsv`
- `docs/design/native-ui-rebuild/evidence/50.md`

## 4. Gate and done

1. `cd ~/wt/rb-50 && native/tool/design/gate_order.sh 50` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o50.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/50.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
