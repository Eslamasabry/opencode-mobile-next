ORDER: 48-this-phone
ENGINE: sonnet
DEPENDS: 13,22,24
WRITES: native/feature/phone/src/commonMain/kotlin/**/ui/** native/feature/phone/src/commonMain/kotlin/**/*Screen.kt native/feature/phone/src/commonMain/kotlin/**/*Sheet*.kt native/feature/phone/src/desktopTest/kotlin/**/rb/o48/** native/feature/phone/build.gradle.kts native/feature/background/src/commonMain/kotlin/**/ui/** native/feature/background/src/commonMain/kotlin/**/*Screen.kt native/feature/background/src/commonMain/kotlin/**/*Sheet*.kt native/feature/background/src/desktopTest/kotlin/**/rb/o48/** native/feature/background/build.gradle.kts native/feature/linux/src/commonMain/kotlin/**/ui/** native/feature/linux/src/commonMain/kotlin/**/*Screen.kt native/feature/linux/src/commonMain/kotlin/**/*Sheet*.kt native/feature/linux/src/desktopTest/kotlin/**/rb/o48/** native/feature/linux/build.gradle.kts native/feature/manage-space/src/commonMain/kotlin/**/ui/** native/feature/manage-space/src/commonMain/kotlin/**/*Screen.kt native/feature/manage-space/src/commonMain/kotlin/**/*Sheet*.kt native/feature/manage-space/src/desktopTest/kotlin/**/rb/o48/** native/feature/manage-space/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb48.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb48.xml docs/design/native-ui-rebuild/waivers/48.tsv docs/design/native-ui-rebuild/evidence/48.md
GATE: native/tool/design/gate_order.sh 48
TIMEOUT_MIN: 480
---
# Order 48-this-phone: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-48`, branch `native/rb-48-this-phone`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/48.gate.tsv` (166 goldens). Gate command: `native/tool/design/gate_order.sh 48`.
- Depends on: order 13, order 22, order 24. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-48-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/phone`, `native/feature/background`, `native/feature/linux`, `native/feature/manage-space`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-F01` | F01 | `ThisPhoneScreen` / `openThisPhone` (+ `this_phone/*`); freeze tip process-budget card | page | `PhoneRoute.ThisPhone` |
| `screen-F07` | F07 | `LinuxManagerScreen` / `openLinuxManager`; freeze tip `kit_vitals_strip.dart` | page | `PhoneRoute.Linux` |
| `screen-F08` | F08 | `LinuxProcessesPage` + `RunningOnPhoneScreen` | page | **merged**: `PhoneRoute.Running` (owner-approved S8, memory `linux-manager-plan`) |
| `screen-F09` | F09 | `LinuxPortsPage` | page | `PhoneRoute.LinuxPorts` |
| `screen-F10` | F10 | `LinuxStoragePage` | page | `PhoneRoute.LinuxStorage` |
| `screen-F11` | F11 | `LinuxPackagesPage` + `LinuxJobPanel` | page | `PhoneRoute.LinuxPackages` |
| `screen-F12` | F12 | Linux services pages (branch `feat/linux-services-ui-2026-10-31`) | page | `PhoneRoute.LinuxServices`, `LinuxService(name)`, `LinuxServiceAdd` |
| `screen-F13` | F13 | Linux backups (branch `feat/linux-backup-2026-11-01`) + `ProjectExportScreen` in-app use | page | `PhoneRoute.LinuxBackups` ("Projects only" = the former Export projects) |
| `screen-F14` | F14 | `LocalTerminalView` | page | `PhoneRoute.LocalTerminal` |
| `screen-F15` | F15 | `openKeepRunningScreen` / `KeepRunningSection` | page | `PhoneRoute.KeepRunning` |
| `screen-F16` | F16 | `TermuxMigrationScreen` / `openTermuxMigration` + `TermuxMigrationRow` / `Offer` | page | `PhoneRoute.TermuxMigration` |
| `screen-F17` | F17 | `TermuxProcessesScreen`, `TermuxStorageScreen`, `showTermuxOtherAppsSheet`, `TermuxRunawayWatcher` / `WorkRunawayNotice` | page/sheet | **dropped** (D7) |
| `screen-F18` | F18 | `LocalAgentScreen` (+ `LocalAgentOnboardingBlock`, `showLocalAgentProjectSheet`) | page | **merged** into `AgentsRoute.Hub` (§6.2 M3) |
| `screen-F19` | F19 | `ManagedServerRecoveryOption` | part | row in F01 |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/phone_*`, `test/goldens/this_*`, `test/goldens/background_*`, `test/goldens/manage_*`, `test/goldens/runtime_*`, `test/revamp/goldens/phone_*`, `test/revamp/goldens/termux_*` — 166 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/phone/src/commonMain/kotlin/**/ui/**`
- `native/feature/phone/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/phone/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/phone/src/desktopTest/kotlin/**/rb/o48/**`
- `native/feature/phone/build.gradle.kts`
- `native/feature/background/src/commonMain/kotlin/**/ui/**`
- `native/feature/background/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/background/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/background/src/desktopTest/kotlin/**/rb/o48/**`
- `native/feature/background/build.gradle.kts`
- `native/feature/linux/src/commonMain/kotlin/**/ui/**`
- `native/feature/linux/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/linux/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/linux/src/desktopTest/kotlin/**/rb/o48/**`
- `native/feature/linux/build.gradle.kts`
- `native/feature/manage-space/src/commonMain/kotlin/**/ui/**`
- `native/feature/manage-space/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/manage-space/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/manage-space/src/desktopTest/kotlin/**/rb/o48/**`
- `native/feature/manage-space/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb48.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb48.xml`
- `docs/design/native-ui-rebuild/waivers/48.tsv`
- `docs/design/native-ui-rebuild/evidence/48.md`

## 4. Gate and done

1. `cd ~/wt/rb-48 && native/tool/design/gate_order.sh 48` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o48.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/48.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
