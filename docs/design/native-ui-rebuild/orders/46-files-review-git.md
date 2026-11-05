ORDER: 46-files-review-git
ENGINE: codex
DEPENDS: 13,18
WRITES: native/feature/files/src/commonMain/kotlin/**/ui/** native/feature/files/src/commonMain/kotlin/**/*Screen.kt native/feature/files/src/commonMain/kotlin/**/*Sheet*.kt native/feature/files/src/desktopTest/kotlin/**/rb/o46/** native/feature/files/build.gradle.kts native/feature/review/src/commonMain/kotlin/**/ui/** native/feature/review/src/commonMain/kotlin/**/*Screen.kt native/feature/review/src/commonMain/kotlin/**/*Sheet*.kt native/feature/review/src/desktopTest/kotlin/**/rb/o46/** native/feature/review/build.gradle.kts native/feature/git/src/commonMain/kotlin/**/ui/** native/feature/git/src/commonMain/kotlin/**/*Screen.kt native/feature/git/src/commonMain/kotlin/**/*Sheet*.kt native/feature/git/src/desktopTest/kotlin/**/rb/o46/** native/feature/git/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb46.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb46.xml docs/design/native-ui-rebuild/waivers/46.tsv docs/design/native-ui-rebuild/evidence/46.md
GATE: native/tool/design/gate_order.sh 46
TIMEOUT_MIN: 360
---
# Order 46-files-review-git: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-46`, branch `native/rb-46-files-review-git`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/46.gate.tsv` (67 goldens). Gate command: `native/tool/design/gate_order.sh 46`.
- Depends on: order 13, order 18. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-46-codex` (standard §11).

## 2. Screens

Native modules: `native/feature/files`, `native/feature/review`, `native/feature/git`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-D04` | D04 | row actions sheet | sheet | local sheet of D02 |
| `screen-D05` | D05 | `showFileShareSheet` (Wi-Fi share), freeze tip `link_file_share.dart` | sheet | local sheet of D03; and a local sheet of C01 for a file link tapped in a reply ("Share on Wi-Fi") |
| `screen-D06` | D06 | `SharedStorageAccessFlow` | flow | `ActivityResults` + `PermissionGate` service (`:android:platform`) with a kit confirm in D02 |
| `screen-D09` | D09 | `showGitSheet` (`lib/ui/git/*`) | sheet | `SheetKey.Git(profileId, projectDir)` |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/folder_*`, `test/revamp/goldens/file_*`, `test/revamp/goldens/files_*`, `test/revamp/goldens/review_*` — 67 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/files/src/commonMain/kotlin/**/ui/**`
- `native/feature/files/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/files/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/files/src/desktopTest/kotlin/**/rb/o46/**`
- `native/feature/files/build.gradle.kts`
- `native/feature/review/src/commonMain/kotlin/**/ui/**`
- `native/feature/review/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/review/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/review/src/desktopTest/kotlin/**/rb/o46/**`
- `native/feature/review/build.gradle.kts`
- `native/feature/git/src/commonMain/kotlin/**/ui/**`
- `native/feature/git/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/git/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/git/src/desktopTest/kotlin/**/rb/o46/**`
- `native/feature/git/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb46.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb46.xml`
- `docs/design/native-ui-rebuild/waivers/46.tsv`
- `docs/design/native-ui-rebuild/evidence/46.md`

## 4. Gate and done

1. `cd ~/wt/rb-46 && native/tool/design/gate_order.sh 46` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o46.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/46.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
