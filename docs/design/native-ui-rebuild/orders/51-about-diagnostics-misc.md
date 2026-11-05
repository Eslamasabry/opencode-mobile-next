ORDER: 51-about-diagnostics-misc
ENGINE: commandcode
DEPENDS: 13
WRITES: native/feature/diagnostics/src/commonMain/kotlin/**/ui/** native/feature/diagnostics/src/commonMain/kotlin/**/*Screen.kt native/feature/diagnostics/src/commonMain/kotlin/**/*Sheet*.kt native/feature/diagnostics/src/desktopTest/kotlin/**/rb/o51/** native/feature/diagnostics/build.gradle.kts native/feature/updates/src/commonMain/kotlin/**/ui/** native/feature/updates/src/commonMain/kotlin/**/*Screen.kt native/feature/updates/src/commonMain/kotlin/**/*Sheet*.kt native/feature/updates/src/desktopTest/kotlin/**/rb/o51/** native/feature/updates/build.gradle.kts native/feature/desktopupdates/src/commonMain/kotlin/**/ui/** native/feature/desktopupdates/src/commonMain/kotlin/**/*Screen.kt native/feature/desktopupdates/src/commonMain/kotlin/**/*Sheet*.kt native/feature/desktopupdates/src/desktopTest/kotlin/**/rb/o51/** native/feature/desktopupdates/build.gradle.kts native/feature/voice/src/commonMain/kotlin/**/ui/** native/feature/voice/src/commonMain/kotlin/**/*Screen.kt native/feature/voice/src/commonMain/kotlin/**/*Sheet*.kt native/feature/voice/src/desktopTest/kotlin/**/rb/o51/** native/feature/voice/build.gradle.kts native/feature/capabilities/src/commonMain/kotlin/**/ui/** native/feature/capabilities/src/commonMain/kotlin/**/*Screen.kt native/feature/capabilities/src/commonMain/kotlin/**/*Sheet*.kt native/feature/capabilities/src/desktopTest/kotlin/**/rb/o51/** native/feature/capabilities/build.gradle.kts native/feature/integrations/src/commonMain/kotlin/**/ui/** native/feature/integrations/src/commonMain/kotlin/**/*Screen.kt native/feature/integrations/src/commonMain/kotlin/**/*Sheet*.kt native/feature/integrations/src/desktopTest/kotlin/**/rb/o51/** native/feature/integrations/build.gradle.kts native/feature/integrations-catalog/src/commonMain/kotlin/**/ui/** native/feature/integrations-catalog/src/commonMain/kotlin/**/*Screen.kt native/feature/integrations-catalog/src/commonMain/kotlin/**/*Sheet*.kt native/feature/integrations-catalog/src/desktopTest/kotlin/**/rb/o51/** native/feature/integrations-catalog/build.gradle.kts native/feature/monitor/src/commonMain/kotlin/**/ui/** native/feature/monitor/src/commonMain/kotlin/**/*Screen.kt native/feature/monitor/src/commonMain/kotlin/**/*Sheet*.kt native/feature/monitor/src/desktopTest/kotlin/**/rb/o51/** native/feature/monitor/build.gradle.kts native/feature/gate/src/commonMain/kotlin/**/ui/** native/feature/gate/src/commonMain/kotlin/**/*Screen.kt native/feature/gate/src/commonMain/kotlin/**/*Sheet*.kt native/feature/gate/src/desktopTest/kotlin/**/rb/o51/** native/feature/gate/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb51.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb51.xml docs/design/native-ui-rebuild/waivers/51.tsv docs/design/native-ui-rebuild/evidence/51.md
GATE: native/tool/design/gate_order.sh 51
TIMEOUT_MIN: 360
---
# Order 51-about-diagnostics-misc: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Command Code (GLM) (`commandcode`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-51`, branch `native/rb-51-about-diagnostics-misc`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/51.gate.tsv` (108 goldens). Gate command: `native/tool/design/gate_order.sh 51`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-51-commandcode` (standard §11).

## 2. Screens

Native modules: `native/feature/diagnostics`, `native/feature/updates`, `native/feature/desktopupdates`, `native/feature/voice`, `native/feature/capabilities`, `native/feature/integrations`, `native/feature/integrations-catalog`, `native/feature/monitor`, `native/feature/gate`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-L01` | L01 | `AppDiagnosticsScreen` / `openReportProblem` / `openBugReport` / `openFailedJobReport` (route `/debug`) | page | `AboutRoute.ReportProblem(problemId?, jobId?)` |
| `screen-L02` | L02 | `AppDiagnosticsKeptScreen` (+ `PerfTraceSection`, `ExitHistorySection`, `CrashReportsSection`) | page | `AboutRoute.Kept` |
| `screen-L03` | L03 | `openCrashReportShare` | sheet | local sheet of L02 |
| `screen-L04` | L04 | `AboutScreen` (+ notices; freeze tip star / share / What's new) | page | `AboutRoute.About` and `AboutRoute.Notices` |
| `screen-L05` | L05 | `GuideScreen` | page | `AboutRoute.Guide` |
| `screen-L06` | L06 | `TipsScreen` | page | `AboutRoute.Tips` |
| `screen-L07` | L07 | `showPrivacyPolicy` | sheet | `SheetKey.PrivacyPolicy` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/revamp/goldens/crash_*`, `test/revamp/goldens/exit_*`, `test/revamp/goldens/qa_*`, `test/revamp/goldens/voice_*`, `test/revamp/goldens/system_*` — 108 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/diagnostics/src/commonMain/kotlin/**/ui/**`
- `native/feature/diagnostics/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/diagnostics/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/diagnostics/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/diagnostics/build.gradle.kts`
- `native/feature/updates/src/commonMain/kotlin/**/ui/**`
- `native/feature/updates/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/updates/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/updates/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/updates/build.gradle.kts`
- `native/feature/desktopupdates/src/commonMain/kotlin/**/ui/**`
- `native/feature/desktopupdates/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/desktopupdates/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/desktopupdates/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/desktopupdates/build.gradle.kts`
- `native/feature/voice/src/commonMain/kotlin/**/ui/**`
- `native/feature/voice/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/voice/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/voice/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/voice/build.gradle.kts`
- `native/feature/capabilities/src/commonMain/kotlin/**/ui/**`
- `native/feature/capabilities/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/capabilities/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/capabilities/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/capabilities/build.gradle.kts`
- `native/feature/integrations/src/commonMain/kotlin/**/ui/**`
- `native/feature/integrations/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/integrations/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/integrations/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/integrations/build.gradle.kts`
- `native/feature/integrations-catalog/src/commonMain/kotlin/**/ui/**`
- `native/feature/integrations-catalog/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/integrations-catalog/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/integrations-catalog/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/integrations-catalog/build.gradle.kts`
- `native/feature/monitor/src/commonMain/kotlin/**/ui/**`
- `native/feature/monitor/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/monitor/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/monitor/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/monitor/build.gradle.kts`
- `native/feature/gate/src/commonMain/kotlin/**/ui/**`
- `native/feature/gate/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/gate/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/gate/src/desktopTest/kotlin/**/rb/o51/**`
- `native/feature/gate/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb51.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb51.xml`
- `docs/design/native-ui-rebuild/waivers/51.tsv`
- `docs/design/native-ui-rebuild/evidence/51.md`

## 4. Gate and done

1. `cd ~/wt/rb-51 && native/tool/design/gate_order.sh 51` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o51.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/51.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
