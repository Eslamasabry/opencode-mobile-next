ORDER: 47-projects-terminal-dev
ENGINE: codex
DEPENDS: 13,18,19
WRITES: native/feature/projects/src/commonMain/kotlin/**/ui/** native/feature/projects/src/commonMain/kotlin/**/*Screen.kt native/feature/projects/src/commonMain/kotlin/**/*Sheet*.kt native/feature/projects/src/desktopTest/kotlin/**/rb/o47/** native/feature/projects/build.gradle.kts native/feature/terminal/src/commonMain/kotlin/**/ui/** native/feature/terminal/src/commonMain/kotlin/**/*Screen.kt native/feature/terminal/src/commonMain/kotlin/**/*Sheet*.kt native/feature/terminal/src/desktopTest/kotlin/**/rb/o47/** native/feature/terminal/build.gradle.kts native/feature/devservices/src/commonMain/kotlin/**/ui/** native/feature/devservices/src/commonMain/kotlin/**/*Screen.kt native/feature/devservices/src/commonMain/kotlin/**/*Sheet*.kt native/feature/devservices/src/desktopTest/kotlin/**/rb/o47/** native/feature/devservices/build.gradle.kts native/feature/preview/src/commonMain/kotlin/**/ui/** native/feature/preview/src/commonMain/kotlin/**/*Screen.kt native/feature/preview/src/commonMain/kotlin/**/*Sheet*.kt native/feature/preview/src/desktopTest/kotlin/**/rb/o47/** native/feature/preview/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb47.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb47.xml docs/design/native-ui-rebuild/waivers/47.tsv docs/design/native-ui-rebuild/evidence/47.md
GATE: native/tool/design/gate_order.sh 47
TIMEOUT_MIN: 360
---
# Order 47-projects-terminal-dev: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-47`, branch `native/rb-47-projects-terminal-dev`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/47.gate.tsv` (52 goldens). Gate command: `native/tool/design/gate_order.sh 47`.
- Depends on: order 13, order 18, order 19. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-47-codex` (standard §11).

## 2. Screens

Native modules: `native/feature/projects`, `native/feature/terminal`, `native/feature/devservices`, `native/feature/preview`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-D01` | D01 | `ProjectHub` / `openProjectFiles` / `openProjectTool` (`project_hub_screen.dart`) | page | `ProjectRoute.Files(profileId, projectDir?, from?)` (Top; list pane) |
| `screen-D02` | D02 | `FilesScreen` + `files/*` (browse, name / symbol / text search) | page | `ProjectRoute.Folder(profileId, projectDir, path)` (list pane); search is an in-page mode of D01/D02 |
| `screen-D03` | D03 | `showFilePreviewSheet(Loading)` + viewers (`widgets/file_preview.dart`, `pdf_file_preview.dart`, `svg_file_preview.dart`, `delimited_file_preview.dart`) | sheet | `ProjectRoute.FileView(profileId, projectDir?, path, line?)`: a page on compact, the detail pane on expanded (O-07-3) |
| `screen-D07` | D07 | `ReviewWorkspace` / `pushWorkingTreeReview` | page | `ProjectRoute.Review(profileId, projectDir, scope, from?)` |
| `screen-D08` | D08 | `DiffPage` | page | `ProjectRoute.Diff(profileId, source)` |
| `screen-D10` | D10 | `TerminalPage` / `TerminalScreen` (`terminal_screen.dart`, `terminal/*`) | page | `ProjectRoute.Terminal(profileId, projectDir?)` |
| `screen-D11` | D11 | `ProjectHealthScreen` | page | `ProjectRoute.Health` (`projectManagement`) |
| `screen-D12` | D12 | `WorktreesScreen` | page | `ProjectRoute.Worktrees` (`projectManagement` or `worktreeBrowsing`) |
| `screen-D13` | D13 | `DevelopmentServicesScreen` | page | `ProjectRoute.Services` (`developmentServices`) |
| `screen-D14` | D14 | `ManagedWorkspacesScreen` | page | `ProjectRoute.Workspaces` |
| `screen-D15` | D15 | `ProjectsScreen` | page | `ProjectRoute.Projects(profileId)` |
| `screen-D16` | D16 | `FolderBrowserSheet`, `RemoteFolderSheet`, `ProjectFolderActions` | sheet | `SheetKey.OpenProject(profileId, purpose)` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/revamp/goldens/terminal_*`, `test/revamp/goldens/local_*`, `test/revamp/goldens/embedded_*`, `test/revamp/goldens/project_*` — 52 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/projects/src/commonMain/kotlin/**/ui/**`
- `native/feature/projects/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/projects/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/projects/src/desktopTest/kotlin/**/rb/o47/**`
- `native/feature/projects/build.gradle.kts`
- `native/feature/terminal/src/commonMain/kotlin/**/ui/**`
- `native/feature/terminal/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/terminal/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/terminal/src/desktopTest/kotlin/**/rb/o47/**`
- `native/feature/terminal/build.gradle.kts`
- `native/feature/devservices/src/commonMain/kotlin/**/ui/**`
- `native/feature/devservices/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/devservices/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/devservices/src/desktopTest/kotlin/**/rb/o47/**`
- `native/feature/devservices/build.gradle.kts`
- `native/feature/preview/src/commonMain/kotlin/**/ui/**`
- `native/feature/preview/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/preview/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/preview/src/desktopTest/kotlin/**/rb/o47/**`
- `native/feature/preview/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb47.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb47.xml`
- `docs/design/native-ui-rebuild/waivers/47.tsv`
- `docs/design/native-ui-rebuild/evidence/47.md`

## 4. Gate and done

1. `cd ~/wt/rb-47 && native/tool/design/gate_order.sh 47` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o47.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/47.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
