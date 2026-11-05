ORDER: 40-first-run-setup
ENGINE: sonnet
DEPENDS: 13,22,24,29,30
WRITES: native/feature/onboarding/src/commonMain/kotlin/**/ui/** native/feature/onboarding/src/commonMain/kotlin/**/*Screen.kt native/feature/onboarding/src/commonMain/kotlin/**/*Sheet*.kt native/feature/onboarding/src/desktopTest/kotlin/**/rb/o40/** native/feature/onboarding/build.gradle.kts native/feature/setup/src/commonMain/kotlin/**/ui/** native/feature/setup/src/commonMain/kotlin/**/*Screen.kt native/feature/setup/src/commonMain/kotlin/**/*Sheet*.kt native/feature/setup/src/desktopTest/kotlin/**/rb/o40/** native/feature/setup/build.gradle.kts native/feature/setupassistant/src/commonMain/kotlin/**/ui/** native/feature/setupassistant/src/commonMain/kotlin/**/*Screen.kt native/feature/setupassistant/src/commonMain/kotlin/**/*Sheet*.kt native/feature/setupassistant/src/desktopTest/kotlin/**/rb/o40/** native/feature/setupassistant/build.gradle.kts native/feature/setupreview/src/commonMain/kotlin/**/ui/** native/feature/setupreview/src/commonMain/kotlin/**/*Screen.kt native/feature/setupreview/src/commonMain/kotlin/**/*Sheet*.kt native/feature/setupreview/src/desktopTest/kotlin/**/rb/o40/** native/feature/setupreview/build.gradle.kts native/feature/phonesetup/src/commonMain/kotlin/**/ui/** native/feature/phonesetup/src/commonMain/kotlin/**/*Screen.kt native/feature/phonesetup/src/commonMain/kotlin/**/*Sheet*.kt native/feature/phonesetup/src/desktopTest/kotlin/**/rb/o40/** native/feature/phonesetup/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb40.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb40.xml docs/design/native-ui-rebuild/waivers/40.tsv docs/design/native-ui-rebuild/evidence/40.md
GATE: native/tool/design/gate_order.sh 40
TIMEOUT_MIN: 480
---
# Order 40-first-run-setup: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-40`, branch `native/rb-40-first-run-setup`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/40.gate.tsv` (58 goldens). Gate command: `native/tool/design/gate_order.sh 40`.
- Depends on: order 13, order 22, order 24, order 29, order 30. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-40-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/onboarding`, `native/feature/setup`, `native/feature/setupassistant`, `native/feature/setupreview`, `native/feature/phonesetup`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-A04` | A04 | `ManagedMoveView` | state | root route `OnboardingRoute.ManagedMove` |
| `screen-F02` | F02 | `PhoneSetupStartScreen` (+ `SetupCustomizeSheet` local) | page | `PhoneRoute.SetupStart(addTools)` |
| `screen-F03` | F03 | `PhoneSetupProgressScreen` | page | `PhoneRoute.SetupProgress` |
| `screen-F04` | F04 | `PhoneSetupReadyScreen` | page | `PhoneRoute.SetupReady` |
| `screen-F05` | F05 | `PhoneSetupTermuxScreen`, `PhoneSetupTermuxJobScreen` | page | **dropped** (D7 migrate-out only) |
| `screen-F06` | F06 | `PhoneSetupUnsupportedScreen` | page | **dropped**: off Android the phone routes are not registered |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/first_*`, `test/goldens/setup_*`, `test/goldens/kit_setup_*`, `test/goldens/kit_portal_*`, `test/goldens/kit_folders_*`, `test/revamp/goldens/setup_*` — 58 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/onboarding/src/commonMain/kotlin/**/ui/**`
- `native/feature/onboarding/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/onboarding/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/onboarding/src/desktopTest/kotlin/**/rb/o40/**`
- `native/feature/onboarding/build.gradle.kts`
- `native/feature/setup/src/commonMain/kotlin/**/ui/**`
- `native/feature/setup/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/setup/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/setup/src/desktopTest/kotlin/**/rb/o40/**`
- `native/feature/setup/build.gradle.kts`
- `native/feature/setupassistant/src/commonMain/kotlin/**/ui/**`
- `native/feature/setupassistant/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/setupassistant/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/setupassistant/src/desktopTest/kotlin/**/rb/o40/**`
- `native/feature/setupassistant/build.gradle.kts`
- `native/feature/setupreview/src/commonMain/kotlin/**/ui/**`
- `native/feature/setupreview/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/setupreview/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/setupreview/src/desktopTest/kotlin/**/rb/o40/**`
- `native/feature/setupreview/build.gradle.kts`
- `native/feature/phonesetup/src/commonMain/kotlin/**/ui/**`
- `native/feature/phonesetup/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/phonesetup/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/phonesetup/src/desktopTest/kotlin/**/rb/o40/**`
- `native/feature/phonesetup/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb40.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb40.xml`
- `docs/design/native-ui-rebuild/waivers/40.tsv`
- `docs/design/native-ui-rebuild/evidence/40.md`

## 4. Gate and done

1. `cd ~/wt/rb-40 && native/tool/design/gate_order.sh 40` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o40.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/40.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
