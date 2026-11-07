# Scoreboard 2026-11-07, group 0 (lead, gate tooling): evidence

Branch `native/s17-gate-tools` from `native/screens-merge22` (e6a10900e). Orders from
[SCOREBOARD-2026-11-07.md](../SCOREBOARD-2026-11-07.md) on `native/rb-score`, "Before any group" 0.1–0.3 and A7.
Modules touched: `:ui:gate` (main and desktopTest), `native/tool/design`, `native/tool/qa`, plus one line in
`native/tool/README.md` and one block in `native/tool/test/run_tests.sh` (the tool self-tests).

## What ran here, what did not

- **Gradle did not run in this container.** Dependency resolution failed (Maven Central answered 429, then the Compose
  plugin `org.jetbrains.compose:1.13.0-beta01` could not be resolved), and there is no Android SDK. The Kotlin changes
  and the three new `:ui:gate` test classes are written to compile but were **not compiled or run**. First run on the
  VPS or PC: `./gradlew :ui:gate:desktopTest` (expects G0-01, G0-02, G0-12, G0-13 to pass).
- Ran here: `python3 -I native/tool/design/rb_gate_test.py`: 5 tests pass. `native/tool/test/run_tests.sh`:
  232 passed, 8 failed; without my change 221 passed and the same 8 failed (snapshot and GATES review cases that need
  this container's GitHub remote). My 11 new cases (G0-07..G0-11) pass. `native/tool/check_commits.sh` on the range: OK.
- No picture was rendered, so this note has no Flutter/native side-by-side: the before/after numbers below are counts the
  tooling now reports, not new pass counts. A fresh scoreboard run is what turns them into passes.

## 0.1 The layout guard no longer hides the pictures

Before: `FlutterGoldenGate.check` called `LayoutGuard.check` first, and a new finding threw before the render. The
scoreboard only got chat pictures with `OC_LAYOUT_BASELINE=update`; order 12's own evidence lost 13 confirm cases the
same way.

Now:
- `check` records the guard's verdict as `looks-out/gate/layout/<name>.tsv` (`name TAB PASS|FAIL TAB one-line detail`),
  renders and compares every mode anyway, then fails the test with the first failure (the rest suppressed).
- `rb_gate.py evaluate` reads those rows: each finding is its own `FAIL L1 <name>: ...` line, marks its part gate-fail,
  and the run prints `layout guard: N gate cases checked, M with findings the baseline does not list`.
- `gate_order.sh` keeps going after a non-zero Gradle exit when any `desktopTest` task ran: it evaluates what rendered
  and still ends `GATE FAIL`. Only a run where no gate case ran stops before evaluation.
- The guard still fails the gate; nothing is baselined. Fixing findings stays with each module's group.

Tests: `LayoutRowTest` G0-01 (a scene with words on the window edge: the row says FAIL edge-text **and** the native
picture exists; before the fix neither did), G0-02 (row format); `rb_gate_test.py` G0-03, G0-04 (evaluate prints L1).

## 0.2 Stale DEPENDS_ON waivers cleared

Before: `order_done()` looked only at `~/wt/ui/state/<NN>.done`, which existed on no machine for 07, 08, 09, 12 and 15.

Now: merged orders are listed in the committed ledger `native/tool/design/orders_done.tsv` (07 c4f9c8540, 08
b9b5bb022, 09 1febfca33, 12 56953e43a, 15 b64cc8dcd), which `order_done()` reads next to RB_STATE. `rb_gate.py stale`
lists every DEPENDS_ON waiver that names a done order:

| waiving order | waits for 07 | 08 | 09 | 12 | 15 | total |
|---|---:|---:|---:|---:|---:|---:|
| 01 | | | 4 | | 4 | 8 |
| 06 | 4 | 40 | | | | 44 |
| 11 | 12 | 20 | | 2 | | 34 |
| 17 | | | | | 40 | 40 |
| 21 | 8 | 26 | | | | 34 |
| 25 | | 2 | | | | 2 |
| **all** | 24 | 88 | 4 | 2 | 44 | **162** |

Before: those 162 shots were DEFER (as gated). After: evaluate rejects the waiver ("order NN is done, the shot must pass
now") and measures them. The scoreboard's strict run found all 146 it measured failing, so expect the as-gated fail count
to rise by about that much: this is the honest count, not a regression. The waiver lines stay in the owning orders'
`waivers/*.tsv` (not my files); `rb_gate.py stale` is the list for those owners to drop. Orders 01b, 05, 11 and 30 are
also named by waivers; I left them out of the ledger because the scoreboard does not say whether they are done.

Tests: `rb_gate_test.py` G0-05, G0-06.

## 0.3 Every desktopTest source set compiles

`native/tool/qa/compile_desktop_tests.sh` finds every module with desktop test sources (83 on this head; same discovery
as `settings.gradle.kts`), runs `<module>:compileTestKotlinDesktop` for all of them in one `--continue` run (no Android
SDK needed), and names each module whose tests do not compile (with its first compiler errors) or whose task never ran.
Exit 0 / 1 / 2 (could not run). Listed in `native/tool/README.md` for the coordinator's merge step: run it on the
integration head before and after every merge. (`compileAllTests` at the root and `:quality:compileAllTestSources`
already exist; T1 runs the first, but merges into `screens-merge` skipped it, which is how `:ui:kit-chat` rotted on
89ec49e55.) Tests: `run_tests.sh` G0-07..G0-11 with a stub Gradle (a module that fails to compile, one whose task never
ran, a Gradle that runs nothing).

## A7 The confirm sheet gate case that drew an empty frame

Re-measured on this head before changing anything: `KitConfirmSheetGateTest` already wraps `KitSheetHost` since order
12's merge (56953e43a, after the scoreboard's 89ec49e55). Order 12's evidence measured its pictures (5.7–23.7 % box), so
the empty frame is fixed in the gate case; what still hid the confirm pictures in a strict run was 0.1.

So the gate now catches the cause: `check` fails a shot whose native render is one colour over the whole window while its
golden has ink, as `empty composition: ... A sheet, dialog or confirm needs a KitSheetHost around it in the gate case`.
The desktop kit is not a debug platform, so `KitDialogFrame` only logs a missing host; before this, the only symptom was a
large box %. Tests: `EmptyCompositionTest` G0-12 (KitConfirm without a host: named as empty) and G0-13 (with the host:
not). The confirm's remaining look differences (handle band, padding, tertiary colour) are group A's (A7 port of
`kit_confirm_sheet.dart`).

## Missing gate cases: where they are

`rb_gate.py coverage --order all` (new; static, from string literals in test sources; `RB_LIST=1` lists the stems):
**4,527 manifest goldens, 1,384 named by a gate case, 3,143 not named** on e6a10900e. Largest gaps: 08 (288), 14
(284), 42 (270), 44 (210), 11 (202), 18 (178), 41 (176), 05 (162), 48 (162), 45 (141). Group 0 owns no kit or screen
module, so it adds no gate cases itself; this list is what each group's "gate cases first" step works from.

## Before / after (what the gate reports)

| | before | after |
|---|---|---|
| shot whose gate case has a new layout finding | thrown before render: no picture, no row | picture rendered and measured, plus its own L1 row |
| DEPENDS_ON waivers naming orders 07/08/09/12/15 | 162 DEFER | 0 DEFER, 162 measured |
| a modal gate case without its host | large box % only | `empty composition` failure naming the cause |
| desktopTest sources that stop compiling | found by the next person to run that module | `compile_desktop_tests.sh` names the module |
| goldens no gate case names | unknown without a 3.5 h run | `rb_gate.py coverage`: 3,143 |

Pass counts themselves need a VPS run: `RB_DEV=1 native/tool/design/gate_order.sh 12` (confirm pictures plus L1
rows), then `scoreboard/tools/score.sh` without `OC_LAYOUT_BASELINE=update`.

## Emulator

Nothing user-visible changed (gate and QA tooling only); no emulator check is needed.
