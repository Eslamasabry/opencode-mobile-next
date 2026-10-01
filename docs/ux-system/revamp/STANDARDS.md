# Revamp standards: the one rulebook (2026-09-26)

> **Owner decision 2026-09-27: Arabic is dropped from the revamp.** Builders do not render Arabic or RTL galleries, do not add Arabic translations for new copy (new ARB keys go only in `app_en.arb`), and reviewers do not check RTL. Any rule below that requires `_ar_` goldens, Arabic copy or RTL checks is suspended for revamp units. Existing Arabic strings are left as they are.

The owner's instruction: "Make sure all rules and standards are established before agents."

This file is that rulebook for the whole-app revamp (`PLAN.md`, `work-units.json`). It is written for the builder agents (about 160 units) and for their reviewers. Every rule here was taken from a committed source, deduplicated, and given a stable id. Where the sources disagreed, the conflict was resolved by the order of authority in §0, and the resolution is listed in Appendix A. Questions only the owner can answer are in Appendix B, each with the rule that applies until he answers.

## Principles

These are the aims behind the rules. They are taste, so no gate checks them; the numbered rules below are the checkable form.

- **A calm instrument.** One neutral ground, one accent, depth from surface steps, and nothing that moves without a reason.
- **Very, very sharp and crisp** (owner, 2026-09-26). Whole pixels, one-pixel hairlines, no soft glows, no blur on content.
- **The person's words.** Say what is happening now and what to do next. Engine words, ids and paths stay behind Details.
- **Automation first.** The app does the obvious thing, says so afterwards in one line, and asks only when only the person can decide.
- **Honest state.** Words never contradict the screen, and no wait is silent.
- **One of each.** One kit, one installer, one Needs-you list, one answer card, one status line, one way to say each thing.
- **Nothing is lost.** Typed input, queued prompts and data survive mistakes, crashes and lost connections.

---

## 0. How to use this

### 0.1 Reading a rule

Each rule has four parts:

| Column | Meaning |
|---|---|
| **ID** | Stable. Cite it in commits, review findings and QA records (`LOOK-12`). Ids are never reused or renumbered; a retired rule stays with "retired". |
| **Rule** | One objective, checkable sentence. |
| **Source** | Where it comes from (abbreviations below). |
| **Gate** | What enforces it: a test path (it fails when the rule breaks for the cases it names; a gate that covers only part of the rule says **partial: <what it covers>**); **missing: Gxx** (the check to build, specified in §18); **reviewer** (judgement; checked with §17, and for runtime questions only from the evidence PROC-31 names); or **none** (builder discipline, not reviewed). |

A rule with "missing: Gxx" still applies today. Until the gate exists, the reviewer checks it by hand, from the QA record's evidence (PROC-31).

A rule marked **Applies from: <unit id>** depends on a part or service that unit builds. Before that unit has merged, a builder uses the nearest existing part, follows the rest of the rule, and lists the gap under NOT proven (§0.4).

### 0.2 Order of authority

When two sources disagree, the higher one wins:

1. The owner's decisions, as quoted in the docs (`programme-decisions-2026-09-26.json` and the slices of the programmes it approves in `programmes.json`, `owner-verdicts-2026-09-26.json`, the owner rules quoted in the visual language, the owner rules quoted in `kit-v2.md` §8 (adaptive for phone, tablet and PC) and §9 (kit only), the owner's decisions recorded in `PLAN.md` §1 (colours are a theme the person can change; Android is the target), and the owner's audit-trail and design-standard rules).
2. `AGENTS.md`.
3. `docs/design/visual-language-2026-09-26.md`.
4. `docs/ux-system/kit-v2.md`.
5. `docs/design/design-standard.md`.
6. Everything else (`target-ia.md`, `personas-verticals.*`, `journeys.json`, `capabilities.md`, `principles.md`, `ux-reorganization-plan-2026-09-19.md`, `CONTRIBUTING.md`, `technical-overview.md`, QA READMEs). Within this group, the frozen contracts in `PLAN.md` §3 (`target-ia.md`, `taxonomy.md`, `map/all.json`) win over the others.

For revamp units, **this file ranks directly under the owner's decisions and `AGENTS.md`**: where it differs from VL, K2, DS or any other source, this file wins, and a passage overridden by Appendix A is void even if it has not yet been amended in place (§0.5 step 8). If you find this file wrong, or find a contract wrong, follow PROC-20: record the problem in the build and QA records, leave that one item at today's behaviour, and never invent a third behaviour. The coordinator fixes the contract before the next wave.

**Deferred by the owner.** Programme P2 (Setup-assistant agent) is "later" (`programme-decisions-2026-09-26.json`). Everything in the frozen contracts that belongs to it is out of scope until the owner approves P2: "Ask the setup assistant" (TIA L37), the assistant option of `mcp-add-sheet` (TIA L106), `embedded-config-change-card` (TIA L108), the assistant fallback of search (TIA L33, L192) and the assistant door of job 44 (TIA L189). No unit builds them or leaves a row that leads to them (AUTO-20).

### 0.3 Source abbreviations

| Abbreviation | File |
|---|---|
| AG | `AGENTS.md` |
| CON | `CONTRIBUTING.md` |
| TO | `docs/technical-overview.md` |
| VL | `docs/design/visual-language-2026-09-26.md` |
| K2 | `docs/ux-system/kit-v2.md` |
| DS | `docs/design/design-standard.md` |
| PR | `docs/design/principles.md` |
| UXR | `docs/design/ux-reorganization-plan-2026-09-19.md` |
| TIA | `docs/ux-system/target-ia.md` |
| PV | `docs/ux-system/personas-verticals.md` and `.json` |
| JN | `docs/ux-system/journeys.json` |
| CAP | `docs/ux-system/capabilities.md` |
| OV | `docs/ux-system/owner-verdicts-2026-09-26.json` |
| PLAN | `docs/ux-system/revamp/PLAN.md` |
| QA | `docs/qa/README.md` |
| MEM | an owner rule or definition recorded in the maintainer's memory (audit trail, design standard, verify fixes with a failing test, background task killer, the chat transcript turn model of 2026-09-20) |
| PD | `docs/ux-system/programme-decisions-2026-09-26.json` and the approved slices in `docs/ux-system/programmes.json` |
| MAP | `docs/ux-system/map/all.json` |
| AR | `docs/qa/ar-review-2026-09-25/README.md` and `docs/qa/e7-arabic-core/README.md` |
| TB | `docs/design/team-board-2026-09-26.md`, `docs/design/aiteam-redesign-2026-09-24.md` |

### 0.4 Who reads what

- **Builder of a kit part or kit change (wave 1):** §1–§10, §12, §13, §15, §16 (§3 only ARCH-5, ARCH-6 and ARCH-11; §11 only for KitNeedsYou, KitRequestCard and KitAutoLine). Kit parts own default copy ("Close", "Copied", "Try again", "Still waiting", "Not confirmed yet", "Details"), the 8 s escalation, receipts, drafts, the secret field and redaction, so every string a part shows is an ARB key in `app_en.arb` and `app_ar.arb` with a `kit` prefix (COPY-1, COPY-3, COPY-22).
- **Builder of a screen unit (wave 2):** all of §1–§16, but §11 only where the map asks for it; MAP-1, STATE-20 and STATE-21 decide how much of the map record is this wave's work.
- **Builder of a programme slice (wave 3):** all of it.
- **Every builder:** uses a kit part only once the unit that builds or changes it has merged into the integration branch; before that, the nearest existing part, with the gap listed under NOT proven. A blocked unit follows PROC-32; a wrong rule or contract follows PROC-20.
- **Reviewer:** §17, then the rule text for anything that fails. Reviewers are read-only (PROC-27) and answer runtime questions only from recorded evidence (PROC-31).
- **Integrator:** §2 (shared files and registries, PROC-13; blocked units, PROC-32), §15, §16, §18.

### 0.5 Before wave 1 (coordinator only)

These have to be done before any builder starts, so that the rules above are the rules the builders are given:

1. Bring `feat/visual-language-v1` to §5, then merge it (PLAN §8). On that branch, before the merge:
   - add the roles `glassRimLight`, `glassRimDark` and `glassShadow` to `ThemeRoles` (derived in `deriveRoles`), and paint `kit_glass.dart`'s rim and shadow from them instead of `Colors.white`/`Colors.black` (LOOK-1);
   - change `KitTokens.rowValue` to the `secondary` role (14) and `typedName` to `mono` 13, and map every Material `textTheme` slot in `kit_text.dart` onto a LOOK-12 role, with no other size (no 57/45/36/28/15; LOOK-12, LOOK-17);
   - give `AppTheme.raised` one value per brightness as LOOK-20 writes it, or remove it, and remove `AppTheme.glow` and its use on the composer (LOOK-20);
   - take `KitGlass`'s radius from a `KitTokens` name, not `Radius.circular(22)` (KIT-9);
   - replace the `orange` entry in `graphiteAccents` with teal (owner, B15), dark and light values chosen to pass LOOK-8 and LOOK-39;
   - update the tests that still encode the old look (`test/app_theme_test.dart`, `test/theme_packs_test.dart`) and change `tool/capture/fixtures.dart` to load Geist.
   Whatever still breaks a kit gate after this is baselined per file (G17, G21 are ratchets inside the kit until slice-P9.10).
2. Add the kit seams every wave-1 unit shares, each with tests (new parts in their NAME-1 file): `KitCopy.copy(context, text)` in `kit_copy.dart` (KIT-23); `KitBidi.ltr` and `KitBidi.auto` (COPY-30); `KitTokens.hairlineWidth(context)` and `focusRingWidth(context)` (LOOK-21); the named layout widths in `KitLayout` (LAY-2); the named waits in `kit_motion.dart` (`escalateAfter`, `undoWindow`, `copiedHold`; MOT-1).
3. Build the gates marked "W1" in §18. Make G4, G5, G6, G8x and G14x discover parts from the `kit.dart` manifest, so that no kit unit edits `kit_motion_test.dart`, `kit_keyboard_test.dart`, `text_scale_overflow_test.dart` or the gallery harness (PROC-13). Extend `_namedLiteral` in `test/l10n_coverage_test.dart` to every public `String` parameter of the kit API and re-record that baseline once on the pre-wave base (COPY-1).
4. Fill `finishLine`, `nonGoal`, `acceptance`, `read` (with the rule ids the part implements, for example kit-KitReceipt: STATE-5, STATE-10, AUTO-4) and `checks` on every unit in `work-units.json`, using the defaults in §1.2 (PROC-19, G30). Add a `tests` list to every unit, generated by `build_units.py` from the test files that import or pump its write-set files (PROC-10). Give every `remove`, `merge-into:*` and `redesign` page an owning slice and every page a screen unit, or record why not (MAP-1, G30). Add a `kit-KitScrollbar` unit to wave 1 (KIT-6).
5. Point the workflow at this file (`revamp.workflow.js`): `RULES` lists this file first, as the rulebook that wins; the reviewer prompt becomes "Answer every §17 question the diff touches; report each 'no' with its rule id; `kind: contract` when the rule itself is wrong (PROC-20)"; `REVIEW_SCHEMA` items gain a required `ruleId` (`^[A-Z0-9]+-\d+$` or `new`) and `kind` (`defect` or `contract`); `BUILD_SCHEMA` gains `status` (`done` or `blocked`), `blockers[]` and `contractProblems[]` (PROC-20, PROC-32); `integrate()` skips blocked units and units with a blocking contract problem, and a unit starts only when every `after` dependency was integrated; the QA path becomes `docs/qa/revamp-<unit id>-<YYYY-MM-DD>/` with the date passed to the builder and fixer (EVID-1).
6. Fix the Flutter version drift (PROC-1): `CONTRIBUTING.md`, `scripts/release.sh`, the CI workflows and `test/repository_hygiene_test.dart` say 3.47.2; the pin is 3.47.1 (revision 91f8bd7).
7. Install the secure-storage mock globally in `test/flutter_test_config.dart` (TEST-4).
8. Amend VL, K2 and `design-standard.md` in place for every Appendix A resolution, or put a banner at each overridden passage naming the rule id that replaces it (for example K2 §1.3 fade-scale, K2 §1.6 wrapping chips, K2 §9.2 KitSurface levels, K2 KitText roles, K2 §8.1 `paneListWidth`, DS glass placement and "never right-aligned clusters", VL §3 "packs change only the accent", VL §5 "answer buttons in place (Work, Inbox)"), so the one design standard does not contradict itself. Update PLAN §7 step 2 to the three AVD sizes of LAY-16.

---

## 1. Definition of done

### 1.1 The checklist

A unit may be merged only when every box is ticked. The reviewer copies this list into the finding report.

**Scope and process**

- [ ] The branch is `revamp/<unit id>` in its own worktree, and every changed file is in the unit's write set, a new kit part file, the unit's own tests and goldens (PROC-10), its QA record, or a shared-merge file or registry changed by the rules of PROC-13 (PROC-9, PROC-10, PROC-13).
- [ ] The unit is not blocked, or it stopped and reported as PROC-32 says; any wrong rule or contract is recorded as PROC-20 says.
- [ ] Nothing in `packages/opencode_sdk/`, `lib/main.dart`, `lib/state/connection.dart`, `lib/domain/server_gateway.dart` or `lib/api/product_repository.dart` changed unless the unit owns it (PROC-11, PROC-12, PROC-25).
- [ ] Every commit message has `[skip ci]`, a body saying what changed and why, and the attribution trailers (PROC-14).
- [ ] Changed Dart files are formatted with `dart format --language-version=3.10` (PROC-5).
- [ ] `flutter analyze` on the whole `lib/` and `test/` of the worktree reports no errors and no new warnings or infos in changed paths, and no analyzer suppression was added (PROC-2, PROC-3).
- [ ] The unit's finish line is met and its non-goal was respected (PROC-19).

**Kit and look**

- [ ] Every file the unit rebuilt has a G16 count of zero. Every other touched file's counts went down or stayed the same, except a rise KIT-44 allows (KIT-1, KIT-4, KIT-44).
- [ ] No colour, size, radius, spacing, animation duration or curve literal was added outside the kit and theme files (LOOK-1, LOOK-12, LOOK-19, LAY-7, MOT-1).
- [ ] No glass, shadow or blur was added outside the places §5 allows (LOOK-20, LOOK-22, LOOK-27).
- [ ] Layout is directional, the unit's pages pass the overflow check at the LAY-4 sizes, and their adaptive goldens exist (LAY-8, LAY-4, TEST-10).

**Copy**

- [ ] Every new string is in `app_en.arb` **and** `app_ar.arb` with a description, and no file's hardcoded-string count rose (COPY-1, COPY-3).
- [ ] New copy follows the glossary, the button rules, the Arabic vocabulary and bidi isolation (COPY-6 to COPY-30).

**Behaviour**

- [ ] Every state STATE-20 and STATE-21 give the unit's pages has its KitStateView/KitNotice and a test, no wait is silent past 8 s, and nothing contradicts the state (STATE-1 to STATE-13, STATE-20, STATE-21); each page is handled by its map proposal (MAP-1).
- [ ] Input survives as DATA-1 scopes it, and every act follows the undo-or-confirm table (DATA-1, DATA-2, DATA-11).
- [ ] Security invariants hold: external links, storage keys, secrets (SEC-1 to SEC-4, DATA-5).

**Tests and evidence**

- [ ] New behaviour has tests that assert what the person sees, what is sent, or what is stored; every fix has a test that failed before the fix, with its output saved; existing tests the change broke were handled as TEST-19 says (TEST-1, TEST-2, TEST-19).
- [ ] Affected test files pass with the pinned Flutter and `-j 1`, and the ratchet, design-standard, l10n, glossary and ledger tests pass (PROC-4, §15).
- [ ] Changed goldens were regenerated deliberately, looked at, named as TEST-20 says, and listed in the record (TEST-6, TEST-20).
- [ ] A kit part has its galleries, contract tests, motion, keyboard, accessibility and overflow checks (TEST-9, TEST-15).
- [ ] A migrated screen is in `_migrated` with its goldens and overflow checks (TEST-10).
- [ ] The QA record exists, follows §16, accounts for the map's missing items and the approved renders, and says what was **not** proven (EVID-1 to EVID-12).

### 1.2 Default finish lines and non-goals

Use these when `work-units.json` gives none (PROC-19).

| Unit kind | Finish line | Non-goal |
|---|---|---|
| `kit-part` | `Kit<Part>` exists in its own file under `lib/ui/kit/`, is exported with one row in the `kit.dart` table, and has its §4 API, every state, its galleries (TEST-9), its contract tests (TEST-15) and its structural asserts (G37). | No call site outside the kit changes. |
| `kit-change` | The part matches this file, K2 and VL (look, API, states, adaptive), its galleries are regenerated and reviewed, and the change is additive: every call site still compiles with unchanged behaviour, and `flutter analyze` on the whole tree stays clean (KIT-43). | No screen is restyled, and no public name is renamed or removed; removals happen in the unit that brings the old pattern's count to zero (KIT-43). |
| `screen-revamp` | Every file in the write set has a G16 count of zero and is in `_migrated` with its goldens; each page is handled by its map proposal (MAP-1), with the wave-2 missing states (STATE-21) and actions; the look is VL. | Behaviour planned for wave 3; no gateway call, controller field or persistence is added (STATE-21). |
| `programme-slice` | As written in `work-units.json`. | As written. |

How much of a page's map record is a screen unit's work depends on its proposal:

| ID | Rule | Source | Gate |
|---|---|---|---|
| MAP-1 | A page is handled by its map `proposal`. `keep`: kit-only rebuild in the VL look, no behaviour change. `fix`: kit-only rebuild plus the map's `actionsMissing` and the wave-2 `statesMissing` (STATE-21). `redesign`: kit-only rebuild of today's layout; the new structure waits for its wave-3 slice, and the record says "deferred to <slice id>". `remove` or `merge-into:X`: make the code kit-only with the least change (no new states, copy or goldens beyond what the ratchets force) and put `// revamp: <proposal> (<slice id>)` above the page's widget class. A page already gone from the code: its ledger entry is deleted (a removal PROC-13 allows) and the record says so. | MAP; PLAN §4 | missing: G30 (every page has a screen unit, every remove/merge/redesign page an owning slice); reviewer |

---

## 2. Process, git and ownership

| ID | Rule | Source | Gate |
|---|---|---|---|
| PROC-1 | Every analyze, test, golden update or build whose result is recorded runs with the pinned Flutter 3.47.1 at `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter` (framework revision `91f8bd7…`), never a system Flutter. | AG Toolchain | missing: G25, G46 |
| PROC-2 | Before its last commit, a unit runs `flutter analyze` on the whole `lib/` and `test/` of its worktree: no errors anywhere (a broken call site outside the write set counts), and no new warnings or infos in changed paths. The integrator runs it on the whole tree at every integration and it reports zero issues. | AG Commands | reviewer (from the record's saved output, PROC-31); integrator runs it |
| PROC-3 | No new analyzer suppression is added: no new `// ignore:` or `// ignore_for_file:` and no new `exclude:` entry in `analysis_options.yaml`. | AG Commands; CON | missing: G26 |
| PROC-4 | Builders run only the affected test files, with `-j 1`; the integrator runs the integration set with at most `-j 3`, and only when no builder is running tests. | PLAN §6; AG | reviewer (saved outputs); missing: G32 (partial: the record's commands) |
| PROC-5 | Check ladder: format the changed files (`dart format --language-version=3.10`); run the affected test file once the change is ready; after a failure rerun only that file or test (`--plain-name`); run the analyzer at the end; run `flutter gen-l10n` once after the copy settles. | AG rule 5; MEM | missing: G33 (format) |
| PROC-6 | A full-suite gate uses `tool/qa/run_serial_tests.py` (recursive manifest, including `test/kit`, `test/goldens`, `test/goldens/kit`), with every chunk bounded under 600 s. It records the candidate revision, commands, completed files, skips and failures. It resumes only uncompleted files of an unchanged candidate, never combines results from different snapshots, and never calls partial coverage a completed gate. | AG rule 6; MEM | `tool/qa/test_run_serial_tests.py` |
| PROC-7 | Builders and reviewers never run Gradle, APK builds, emulators or adb, never touch the phone, and never push. | PLAN §6 | reviewer |
| PROC-8 | No process is killed by pattern (`pkill`, `killall`); a test server is started, recorded and killed by its exact PID, and listeners are checked by port or `ps -p <pid>`. | AG | missing: G45 |
| PROC-9 | Each unit has its own branch `revamp/<unit id>` and git worktree, owned by one agent; no agent edits another agent's checkout. | AG Workflow; PLAN §5 | reviewer |
| PROC-10 | A unit writes only its write set, plus a new kit part file (KIT-3), its own tests and goldens, its QA record and the shared-merge files and registries under PROC-13; a needed file outside that set is reported (PROC-32), not edited. A unit's **own tests** are: test files in its write set or its `tests` list; new test files it creates (named by NAME-1); and existing test files that import a file in its write set, which it may change only in tests about that file (TEST-19). A golden is its own when its golden test file is its own; it never regenerates another golden file, but lists the shared goldens its change broke, and the integrator regenerates them after the merge. Appendix A telling a unit to update a named test applies only when that test is its own; otherwise it reports the test. | PLAN §6 | missing: G30 (branch mode) |
| PROC-11 | Single-owner units, one editor at a time across all agents: `lib/state/connection.dart`; the chat library (`lib/ui/screens/chat_screen.dart` and every `chat/*.dart` part); `lib/domain/server_gateway.dart`; `lib/api/product_repository.dart`; `lib/main.dart`; the generated `lib/l10n/app_localizations*.dart` output, whose one owner is the integrator (PROC-13); each protocol cluster; both halves of each MethodChannel (`oc/termux`, `oc/voice`, `oc/camera`, `oc/background`, `oc/share`). | AG Architecture | missing: G30 |
| PROC-12 | `lib/main.dart` and `lib/state/connection.dart` changes go to the coordinator; a unit that needs one describes the change in its record. | PLAN §6 | missing: G30 |
| PROC-13 | Shared-merge files follow fixed rules. **ARB:** add keys (en and ar together); change or delete an existing key's value only if every Dart reference to it is in your write set (`grep -rn <key> lib`); a key used elsewhere is never reworded: add a new key for your pages and leave the old one; never rename, reorder or reformat; the integrator unions additions and, once per wave, prunes keys with no reference in `lib/`. **Generated l10n:** run `flutter gen-l10n` locally so your code compiles and your tests run, but never commit `lib/l10n/app_localizations*.dart` (restore them with `git checkout -- lib/l10n/app_localizations*.dart` before committing); the integrator regenerates and commits them after each merge. **`kit.dart`:** add exactly one export line and one doc-table row per new part. **Baselines** (`test/kit_ratchet_baseline.json`, `_baseline` in `test/l10n_coverage_test.dart`, and every G2/G7/G17/G21/G24/G26–G29 baseline file): lower only the entries for files you changed, never raise one or add one for a new file (new files start at zero; KIT-44 and G31's split rule are the only exceptions); the integrator keeps the per-entry minimum. **Registries** (append-only, one entry per part, page or file, each on its own line or block at the end of its map; never edit or reorder another entry; the only removals are the ones TEST-10 and MAP-1 name; the integrator unions them): `_migrated`, `_migratedClasses` and `_retired` in `test/design_standard_test.dart`, `docs/design/ui-ledger/ledger.json`, `docs/qa/screen-census/manifest.json`, the guard of your own pages in `tool/capture/census/`, and `AppIcons` in `lib/ui/app_theme.dart`. The per-part test lists (`kit_motion_test.dart`, `kit_keyboard_test.dart`, `text_scale_overflow_test.dart`, `accessibility_guidelines_test.dart`) are manifest-driven from `kit.dart` before wave 1 (§0.5 step 3), so kit units do not edit them; until then a unit adds one entry at the end. **Goldens:** regenerate only your own golden test files (PROC-10). | AG; PLAN §5 step 4 | missing: G31 |
| PROC-14 | Every commit message contains `[skip ci]`, has a body that says what changed and why, and ends with the attribution trailers the session gives. | AG Workflow; CON | missing: G33 |
| PROC-15 | Pushes, PRs, CI runs, signing, tags and releases each need a fresh, explicit request from the owner; "get shipping" is not that request. | AG Workflow, rule 8 | reviewer; missing: G45 |
| PROC-16 | Machine-heavy checks (full suite, builds, emulator) are serialised across worktrees by a machine-wide lock. | AG Workflow | missing: G43 |
| PROC-17 | Before building an adapter, confirm the current callable contract, the supported authentication and the permitted credential use; if one fails, the feature stays unavailable, the blocker is logged, and no workaround is built. | AG rule 2; PLAN §4 | reviewer |
| PROC-18 | Discovery for a bounded question stops after about ten minutes: then implement, name a blocker, or say why more research is needed. | AG rule 3 | none (builder discipline) |
| PROC-19 | Before editing, a unit has a one-sentence finish line, an explicit non-goal, read and write sets, dependencies (`after`), acceptance criteria and focused checks. | AG rules 1, 4 | missing: G30 |
| PROC-20 | The contracts in PLAN §3 and this file are frozen for the wave. When one is wrong (it contradicts a source at the same level, contradicts the code's callable reality, breaks an owner rule, or cannot be tested): (1) if §0.2 settles it, follow the higher source and say so in the record; (2) otherwise leave that one item at today's behaviour, finish everything else, and add an entry to the build record's `contractProblems[]` and the QA record's "Contract problems" line: {rule id or doc §, what it says, why it is wrong, evidence path:line, proposed replacement text, blocks: true or false}. `blocks: true` (the finish line cannot be met) means the unit is not merged. Never invent a third behaviour. | PLAN §3 | reviewer; missing: G32 |
| PROC-21 | Reports keep implemented, enabled, verified, committed, deployed and released as separate states, and never offer line counts or test counts as user value. | AG rules 7, 8 | missing: G32 (State table) |
| PROC-22 | The integrator merges only into `feat/phone-setup-v2`; `master` is fast-forwarded (`--ff-only`) only at a milestone the owner approved. | AG Workflow | reviewer |
| PROC-23 | Releases and signing go only through `scripts/release.sh` or `scripts/cut-alpha.sh`, from a clean synced master, dry-run by default, publishing only with `--publish`; a raw `flutter build apk` or the CI test-signed APK is never distributed. | AG; TO | `test/release_script_contract_test.dart` |
| PROC-24 | An APK for the owner's phone is signed with the same certificate as the installed build (compare the installed SHA-256 with `apksigner verify --print-certs` first); a signer is never substituted or rotated. | AG Workflow | missing: G44 |
| PROC-25 | `packages/opencode_sdk/` is never hand-edited; it changes only through `tool/sdk/generate.sh` from `contracts/`, followed by `dart analyze` and `dart test` in the package. | AG | `tool/sdk/verify_generated.dart` (CI); missing: G30 |
| PROC-26 | The Android compile check is `flutter build apk --release` (coordinator only); debug builds are never used, and the Shorebird CLI is used only for release and patch work. | AG; CON | reviewer |
| PROC-27 | Reviewers are read-only: they never edit, and never launch tests, builds or servers. | AG rule 4; PLAN §5 | missing: G45 |
| PROC-28 | A docs-only change is checked with a diff and link check, not with Flutter tests. | AG rule 5 | missing: G34 |
| PROC-29 | CI runs, run URLs and artifact URLs are reported only once they exist; a cancelled run or a focused APK is never claimed as a pass for a combined candidate. | docs/verification | reviewer |
| PROC-30 | Live-server checks use only local test servers (`opencode serve --port 4123`, `opencode2 serve --port 4097 --hostname 127.0.0.1`), never the phone's session server, and never print or commit the OpenCode 2 per-run password. | AG | reviewer; missing: G29 |
| PROC-31 | A reviewer answers a runtime question (text scale, keyboard, reading order, sizes, escalation, crash survival, motion) only from evidence in the QA record: a named test (file and `--plain-name`) with its saved output, or a named golden PNG, listed in the record's Rule evidence table. With no such evidence the answer is "no: no evidence", which is a finding. | AG rule 4; PLAN §5 | missing: G32 (Rule evidence table) |
| PROC-32 | A unit is blocked when it cannot meet its finish line without breaking a rule (a needed file outside the write set, a single-owner file, a dependency that was not integrated, a wrong contract with `blocks: true`, lost quota or permissions, a test it cannot make pass). Then it commits what is done on its branch, sets `status: "blocked"` in the build record with `blockers[]` = {kind: outside-write-set, single-owner, dependency, contract or environment; the file or unit; what is needed; the exact change requested}, writes the QA record with Implemented "partial", and stops. It never edits the outside file. A unit whose `after` dependency was not integrated does not start, and the workflow reports it blocked by that dependency. | AG rule 4; PLAN §5 | missing: G32; integrator |

**Names and places**

| ID | Rule | Source | Gate |
|---|---|---|---|
| NAME-1 | A kit part is class `Kit<Name>` in `lib/ui/kit/kit_<snake>.dart` (chat parts in `lib/ui/kit/chat/`, scenes in `lib/ui/kit/scenes/`); a modal opens through `showKit<Name>`; its tests are `test/kit/kit_<snake>_test.dart` and its gallery `test/goldens/kit/kit_<snake>_golden_test.dart`, with the PNGs beside it. A screen or programme unit's behaviour tests go in `test/<module>_<topic>_test.dart` and its goldens in `test/goldens/<module>_<page>_golden_test.dart`, where `<module>` is the page's map `module`. QA tool scripts go in `tool/qa/`. No new file is added under `lib/ui/widgets/`. | K2 §9.2; work-units.json | missing: G4 (kit), G30 (branch mode) |

---

## 3. Architecture boundaries

| ID | Rule | Source | Gate |
|---|---|---|---|
| ARCH-1 | No file under `lib/ui/` imports `lib/api/` or `lib/api2/`; UI talks only to `lib/domain` (`ServerGateway`, `ServerOperationsGateway`, `ServerCapabilities`). New files have zero such imports; the existing ones may only decrease. | AG; CON | missing: G24 |
| ARCH-2 | UI gates features on `ServerCapabilities` flags and host kind, never on `ServerFlavor`; the only exceptions are profile-editor code that sets or labels the flavour, each allowlisted with a reason. | AG; PV; TIA §4 | missing: G24 |
| ARCH-3 | After an OpenCode 2 reconnect, state is reconciled by refetching, never by replaying the event stream (deltas and `tool.progress` never replay). | AG; CON | `test/connection_v2_test.dart` |
| ARCH-4 | Nothing in `lib/background/` assumes unbounded lifetime: every `dataSync` foreground service handles `onTimeout`, and when Android's 6-hour cap is hit the app says when background checks resume. | AG; PV reliability | `test/background_live_test.dart`; reviewer |
| ARCH-5 | Directory roles: `lib/api` (OpenCode 1), `lib/api2` (OpenCode 2), `lib/domain` (gateway), `lib/state` (profiles, connection, offline queue), `lib/termux`, `lib/background`, `lib/voice`, `lib/platform` (native bridges), `lib/update`, `lib/diagnostics` (services), `lib/ui` (screens and kit); UI uses services only through their public API. | AG; TO | reviewer |
| ARCH-6 | From wave 2 on, `lib/ui/kit/chat/` belongs to the chat library's single owner (the current link of the 2c chain); in wave 1 each chat part is one file with one owner. | K2 §9.2; AG | missing: G30 |
| ARCH-7 | Plain HTTP or `ws://` is allowed only to loopback (`127.0.0.1`, `localhost`, `::1`) and, for AI Team orchestration and Paseo, to Tailscale addresses (100.64.0.0/10) through `isOrchestrationUrlAllowed` and `validatePaseoServerUrl`; the one extension is an OpenCode profile over `http://` to a private network address (`isPrivateNetworkHost`), which needs the inline warning and a per-profile confirmation (`oc.cleartextOk.<profileId>`). Every other server URL is HTTPS or `wss://`. | TO; `lib/state/profiles.dart`; `gascity_probe.dart` | `test/release_blockers_test.dart` ('release URL policy', 'Android cleartext'); the Paseo profile URL tests |
| ARCH-8 | Each entity has one status source that maps its state to words in one place; screens never build status strings by hand. | PV honest-state; K2 §2.3 | reviewer |
| ARCH-9 | The Settings hub and the command palette use one search index. | TIA §4 | `test/search_index_test.dart` (partial); reviewer |
| ARCH-10 | Applies from: slice-P6.7. Notifications are posted only through one `NotificationRouter` (dedupe per server, cancel when answered), which slice-P6.7 creates; after it lands, no other Dart file asks the `oc/background` channel to post a notification. Until then, notifications are posted only by the existing native services (`BackgroundConnectionService`, `BuiltinServerService`, `SetupService`, `ThermalMonitor`), and no unit adds another path. | PV notifications; PD P6.7 | missing: G24 (from slice-P6.7) |
| ARCH-11 | Android is the target. Behaviour, goldens and tests are written for `TargetPlatform.android` (galleries and goldens set `debugDefaultTargetPlatformOverride = TargetPlatform.android`; desktop input is simulated through `debugPlatformCapabilities`). Tablet and PC layouts are Android windows of those sizes, including ChromeOS and desktop windowing, with keyboard and mouse. No unit adds an iOS-, web- or desktop-OS-only code path; the existing Linux and web seams stay as they are and are never a reason to change a layout. | PLAN §1 (owner) | missing: G23 |

---

## 4. Kit only and kit API idioms

The owner's rule (K2 §9): no UI component outside our kit.

| ID | Rule | Source | Gate |
|---|---|---|---|
| KIT-1 | Outside `lib/ui/kit/`, a file under `lib/ui/` constructs only the §9.1 allowlist (layout, scrolling, builders, semantics and focus plumbing, routes until KitPageRoute lands); every other widget comes from a kit part. | K2 §9, §9.1; PLAN §6 | `test/kit_ratchet_test.dart` (G16) |
| KIT-2 | Modal and toast entry points (`showDialog`, `showModalBottomSheet`, `showGeneralDialog`, `AlertDialog`, `SimpleDialog`, `DraggableScrollableSheet`, `showSnackBar`, `SnackBar`, `MaterialBanner`, `showConfirmSheet`) appear only inside `lib/ui/kit/`; `showDatePicker` and `showTimePicker` join this list in the commit that adds KitDateTimePicker. | K2 G1, §9.2 | `test/kit_ratchet_test.dart` (G1) |
| KIT-3 | "Kit only" is about what is constructed. A private widget class in a screen file that constructs only kit parts and §9.1 plumbing is allowed and stays in that file. A new kit part (own file, gallery, tests; NAME-1) is made only for something that draws (paint, a layout rule, input handling) that no kit part offers, even if one page uses it; chat parts live in `lib/ui/kit/chat/`; only shortcuts, intents and routing, which draw nothing, stay outside. Before creating a part, check `kit.dart` and the current wave's unit titles, and name it after what it is, not the page. The QA record lists new parts under "New kit parts"; the integrator merges duplicates (keeping the first and rewriting the other's call sites) before the wave closes. A screen unit may split a write-set file into new files in the same directory only when the coordinator has added them to its write set. | K2 §9, §9.2, §4.12 | G16; missing: G4 |
| KIT-4 | Ratchet baselines only shrink: no file's count rises, a new file starts at zero, and `KIT_RATCHET_WRITE=1` is used only after a count dropped. | K2 §7, §9.3 | `test/kit_ratchet_test.dart`; missing: G31 |
| KIT-5 | Every ratchet or design-standard allowlist entry names its file and gives a reason longer than 10 characters, and the exemption maps hold at most the entries in the committed baseline (they never grow). | K2 §7 | `test/kit_ratchet_test.dart`, `test/design_standard_test.dart` |
| KIT-6 | Applies from: kit-KitScrollbar. Scrollbars come only from `KitScrollbar` in `lib/ui/kit/`, moved from `lib/ui/desktop/desktop_interaction.dart` with its always-visible-on-desktop rule; `desktop_interaction.dart` keeps only the input seam, and the G16 `Scrollbar` exception is removed in that commit. Until then `Scrollbar` is constructed outside the kit only in `desktop_interaction.dart`. | K2 §8.3, §9 (owner) | G16 |
| KIT-7 | The commit that adds KitPageRoute removes `MaterialPageRoute` and `PageRouteBuilder` from the G16 allowlist. | K2 §9.2 | reviewer |
| KIT-8 | Actions are `KitAction` values placed in a kit part's slots (primary, secondary, tertiary, menu); a caller never styles a button. | K2; DS §2 | G16; `test/design_standard_test.dart` |
| KIT-9 | A kit part reads shape, spacing, type and colour only from `KitTokens`, `ThemeRoles` and `KitText`, never from a numeric literal in the part. | K2 §8; VL intro | missing: G21 |
| KIT-10 | Kit parts offer optional `…Key` parameters (`sheetKey`, `confirmKey`, `fieldKey`…); a call site passes one only when a test uses it. | K2; AG Testing | missing: G4, G36 |
| KIT-11 | Every modal part opens through a `showKit…` function returning a Future (`showKitSheet` → `T?`; `showKitConfirm` → `bool`, true only when confirmed; `showKitInputDialog` → `String?`, null on cancel); the one exception is `showKitUndo`, which returns `void`. | K2 §1.1–§1.3 | missing: G4 |
| KIT-12 | Each kit part declares in its doc comment the states it has, from {loading, empty, error, disabled, working, answered}, and renders each declared state in its gallery. A part that shows data it did not create (a list, a panel, a value from the server) declares loading, empty and error; an interactive part declares disabled; a part that sends declares working. | K2; G4 | missing: G4 |
| KIT-13 | A kit part meets its §8.2 phone, tablet and PC behaviour in the commit that adds or changes it. | K2 §8, §8.5 | missing: G4 |
| KIT-14 | `kit.dart`'s doc table has one row for every exported public part. | K2 §2.14, G4 | missing: G4 |
| KIT-15 | Each container has one job: a screen is a place; a `KitSheet` is a choice, a short task or a confirmation; a `KitDialog` is one short text entry or a blocking alert with at most one action; `showKitUndo` is done-with-undo; `KitStatusLine` is a condition on a working screen; `KitNotice` is a message about one part. | K2 §4.6, §1.3 | G1; reviewer |
| KIT-16 | No sheet on a sheet: a confirmation or choice raised inside a `KitSheet` replaces its content in place with its own back step and adds no route. | K2 §4.7 | `test/kit/kit_confirm_sheet_test.dart`; missing: G9 |
| KIT-17 | A sheet body scrolls inside the frame and never builds its own `Scaffold`; pinned actions stay pinned at 200 % text and with the keyboard open. | K2 §1.1 | `test/kit/kit_sheet_test.dart` (partial: frame and scroll); G16; missing: G9x (pinned actions at `textScaler` 2.0 and with `viewInsets.bottom` 300) |
| KIT-18 | `dismissible: false` is used only while an irreversible step runs, and the sheet body says so. | K2 §1.1 | `test/kit/kit_sheet_test.dart`; reviewer |
| KIT-19 | A sheet route is named by its title, focus starts on the title, Close is a labelled 48 dp `KitIconButton` at the end, and the drag handle has a semantic Dismiss action. | K2 §1.1 | `test/kit/kit_sheet_test.dart` (close); missing: G9 |
| KIT-20 | Every text input other than search is a `KitField` with a visible label above it (never a placeholder-only or floating label); search is `KitSearchField`. | K2 §1.4 | G16 |
| KIT-21 | A field shows its counter from 80 % of `maxLength`; its helper is at most 2 lines and never cut; its error sits under it as a live region; validation says "Checking…" in the helper, never a spinner in the field. | K2 §1.3, §1.4 | missing: G9 |
| KIT-22 | Every icon-only control is a `KitIconButton` with a required non-empty label that is both its tooltip and its semantic name. | K2 §1.10 | `test/kit/kit_secret_field_test.dart`; missing: G37 |
| KIT-23 | Copying happens only through the kit's one copy service, `KitCopy.copy(context, text)` in `kit_copy.dart`, used by `KitIconButton.copy`, `KitAction.copy` for text buttons such as "Copy details", and `KitRowMenu` copy items. It announces "Copied" once, shows a check in place, and never shows a snackbar; secrets are never passed to it. | K2 §1.10, §4.8 | missing: G2, G9 |
| KIT-24 | `KitSegmented` has 2–4 segments with word labels, is full width and start-aligned, marks the selected segment with a check, and, when the labels do not fit on one line (long words, text ≥ 2.0), becomes a vertical stack of full-width `KitChoiceRow`s with the same semantics; more options use `KitPickerRow`, on/off uses `KitSwitchRow`. | K2 §1.6 | missing: G37, G6 |
| KIT-25 | A dropdown becomes a `KitPickerRow` that opens a sheet with `KitChoiceList.single`; radio and check tiles become `KitChoiceRow`; a single choice acts on tap with no Apply; choice rows are at least 56 dp, fully tappable, with a shape mark. | K2 §1.7 | G16; missing: G9 |
| KIT-26 | `ListTile`, `SwitchListTile`, `RadioListTile`, `CheckboxListTile` and `ExpansionTile` become `KitRow`, `KitSwitchRow`, `KitChoiceRow` and `KitExpandRow`. | K2 §2.5, §2.11 | G16 |
| KIT-27 | A row is a `KitRow` in a `surface1` panel with hairlines: a leading icon in its tile, a one-line title, a one-line muted supporting line (two when a setting explains itself or carries an error), and a trailing value in `text3`, a chevron, or one icon action; the row carries its own state; larger text follows A11Y-8. | DS §6; VL §5 | G16; missing: G4 |
| KIT-28 | Rows have no per-row ⋮ button: long-press and right-click open `KitRowMenu`, whose items are also exposed as semantic custom actions, with destructive items last after a divider. | VL §1, §5; K2 §4.2 | missing: G37, G14 |
| KIT-29 | A swipe is only an accelerator, and only for an act whose DATA-11 treatment is Undo or neither (for example archive); an act that needs confirmation is never on a swipe. The same act is in the row's `KitRowMenu`. | K2 §2.5, §4.1, §8.3 | missing: G37, G9 |
| KIT-30 | A risky switch (auto-approve, always allow) is `KitSwitchRow.risk` with an inline scope, and while it is on the screen's status line says so; "Always allow" is never a request card's primary; an always-on setting uses `locked:`, not a disabled switch. | K2 §2.6, §2.1 | reviewer |
| KIT-31 | Process output is one `KitLogPanel` per source, folded under Details unless the page exists to show that log; it follows new lines until the person scrolls up (then `KitJumpPill`), polls only while visible, is virtualised, uses the error tone only for errors, and is not a live region. | K2 §1.11, §4.4 | missing: G9 |
| KIT-32 | Every value the app did not write (path, command, host, id, code, branch, URL, typed name) is rendered by a kit part that isolates it left-to-right and makes it copyable (`KitTechnicalValue`, `KitCodeBlock`, `KitLogPanel`, `KitDiffView`, `KitText.mono`, `KitField` mono/path/url/secret). | K2 §4.10 | missing: G4 (Arabic galleries); reviewer |
| KIT-33 | All technical values on a page or sheet go into one `KitDetailsFold`, placed last and collapsed, each value once; a standalone raw error opens with `showKitTechnicalDetails`. | K2 §4.3, §1.8 | G1; missing: G37 |
| KIT-34 | A snackbar is only done-with-undo through `showKitUndo`: one at a time (a new one commits the previous), an 8 s window, never auto-dismissed under accessible navigation, floating above the dock, composer and pinned primary. | K2 §1.17, §4.8 | G1; missing: G9 |
| KIT-35 | Exactly one `KitStatusLine` is visible in a window at a time. Inside the shell it is the shell's `KitStatusLineSlot`, and a screen inside a tab contributes a condition to it through `KitStatusLine.of(status)` rather than drawing its own. A pushed working screen (a conversation, a team task) has its own line, which also takes the shell's condition when that ranks higher. Priority, highest first: connection; Android stopped the app; heat; a risky switch on this screen; update ready. The line replaces every banner and update or release snackbar. | K2 §2.3, §4.6; TIA §1.1 | missing: G37 |
| KIT-36 | A screen inside a tab uses the shell's bar and never adds a second one; `KitTopBar` grows with the text and never cuts its title; its actions are one icon plus overflow on compact, up to three icons on medium, labelled actions on expanded. | K2 §1.18, §8.2 | missing: G37 |
| KIT-37 | Before any install or turn-on primary, `KitNotice.cost` states the cost (download size, installed size, RAM per worker, battery, time, sign-in or subscription, process budget); a turn-on offer is a `KitNotice` with one tertiary action and a dismiss. | K2 §2.4; PV battery-heat; CAP §4 | reviewer |
| KIT-38 | Old wrappers leave by KIT-43: `showConfirmSheet` is deleted by the unit whose change brings its call count to zero (at the latest slice-P9.10), and `ProductErrorState`, `ProductEmptyState` and `ProductInlineEmpty` become `KitStateView` wrappers (kit-KitStateView-v2) and are deleted by the unit that brings their count to zero (at the latest slice-P9.10). | K2 §2.2, §2.14 | G1; missing: G2 |
| KIT-39 | Unless this file, VL, the owner's rules or Appendix A override K2 (for example no raised or tonal surfaces, no fade-scale, the VL type roles, glass only through `KitGlass`), where the K2 spec requires something the code lacks, the spec wins and the kit-change unit adds it (`KitIconButton` `tooltip`/`destructive`/`working`/`selected`/`.copy`; `KitAction.disabledReason`; the destructive stack in `KitActionBlock`); where the code has what the spec is silent on (`KitDraft.controller`, `showKitSheet(loading:)`, `showKitConfirm(action:)`), the code stands. | K2; Appendix A | reviewer |
| KIT-40 | kit-KitField adds `KitField.secret`; `KitSecretField` keeps working by forwarding to it (KIT-43) and is deleted by the unit that brings its call count to zero (at the latest slice-P9.10), so the kit ends with one secret input. | K2 §1.4 | reviewer |
| KIT-41 | Chat parts follow the transcript turn model as STATE-16 in this file states it (the frozen form of the vocabulary comment in `lib/ui/screens/chat/message_view.dart` and the turn-model definitions), and cite STATE-16 in their doc comment. | K2 §5; MEM; Appendix A | reviewer |
| KIT-42 | `KitSurface` has no glass, raised or tonal level; its levels are ground, surface1, surface2 and surface3. Glass exists only as `KitGlass`. | VL §4, §6 (owner); Appendix A | missing: G21 |
| KIT-43 | A kit change is additive: new parameters are optional and keep today's default, and nothing public is renamed or removed in that unit. It never uses `@Deprecated` (it would put infos into every caller's analyze); it keeps the old signature working by forwarding to the new behaviour, marks it `/// Retired by <unit id>: use …`, and adds the old name as a G2 ratchet pattern so call sites only shrink. The unit whose change brings a pattern's count to zero deletes the old API in the same commit; slice-P9.10 deletes whatever is left. | PLAN §4; AG Commands | missing: G2; reviewer |
| KIT-44 | A commit that adds a pattern to a gate (KIT-2, KIT-7, TEST-18, a new G pattern) may raise or add baseline entries only for that pattern. Only the coordinator or the unit the rule names makes such a commit; it lists the pattern in its commit body and QA record, and its commit body contains `ratchet-tighten: <gate> <pattern>`, which G31 accepts. | K2 §7 | missing: G31 |

---

## 5. Look

The approved visual language (VL) is the look. Its values are implemented in `lib/ui/theme_roles.dart`, `lib/ui/app_theme.dart`, `lib/ui/theme_packs*.dart`, `lib/ui/kit/kit_tokens.dart` and `lib/ui/kit/kit_text.dart`. Units read tokens; they never copy the hex values below into code.

### 5.1 Colour and theme roles

| ID | Rule | Source | Gate |
|---|---|---|---|
| LOOK-1 | Every colour comes from a `ThemeRoles` role (ground, surface1–3, hairline, text1–3, accent, onAccent, attention, attentionFill, onAttentionFill, attentionSurface, attentionLine, danger, dangerFill, onDangerFill, success, scrim, code*, ambient, glassRimLight, glassRimDark, glassShadow); no `Color(0x…)` and no `Colors.*` except `Colors.transparent` outside `app_theme.dart`, `theme_packs*.dart` and `theme_roles.dart`. | VL §3; PLAN §6 | missing: G17 |
| LOOK-2 | Outside the kit, code reads colour and type through `ThemeRoles.of` and `KitText`, never `Theme.of(context).colorScheme` or `.textTheme`. | K2 §9.2; theme_roles.dart | missing: G17 |
| LOOK-3 | `hairline` is already translucent and is never passed through `withValues(alpha:)`. | theme_roles.dart | missing: G17 |
| LOOK-4 | The attention roles mean only "needs you": progress, warnings and degraded states never use them, and only kit parts reference them. | VL §1; OV | missing: G17, G18 |
| LOOK-5 | `dangerFill` appears only on the confirm button of a `KitConfirmSheet` of kind destructive, stop or discard; `danger` marks only icons and text for acts that lose data or end running work. Neither is used for restart, cancelling a pending send, withdrawing to draft, a removal that can be redone, signing out, or a failure state. **Stop is red and is the one red word of a running chat** (owner decision 2026-09-29/30): the composer's Stop is a `dangerFill` circle with an `onDangerFill` square, and the living edge's Stop square and the turn's "Stop reply" are `danger` (`lib/ui/kit/chat/kit_composer.dart:1647-1654`, `:1792-1800`; `kit_turn.dart:707-716`). Failures are neutral (B2): field validation errors, error log lines, a failed send on the composer edge and error-tone state icons use `text1`/`text2` words with a neutral error glyph, never `danger`. | VL §1, §3, §5; K2 §4.2 | `test/kit/kit_confirm_sheet_test.dart` (partial: stop, destructive and discard use the error tone); missing: G9x (KitConfirmKind.neutral for restart, cancel send and withdraw has no dangerFill); reviewer |
| LOOK-6 | The accent appears only on: the primary button fill; working marks and the loading and progress bars; link text; the current-selection mark (segment check, choice-row mark, selected dock tab or rail glyph, switch on track); the focus ring; the text cursor and selection handles. Nothing else uses it. Done, connected and added lines use `success` with their word (STATE-9), never `accent`; in the default pack `success` equals the accent until the owner decides otherwise. | VL §1, §3; theme_roles.dart | reviewer; missing: G17 (`accent` referenced outside the kit) |
| LOOK-7 | The colours are a theme the person chooses: a theme pack, or their own accent and ground (`ThemeRoles.withAccent`, `deriveRoles`). A theme may set every role's value, but never its meaning: attention is always an amber or orange hue meaning only "needs you", danger always a red hue meaning destroy or stop, success always green. Every theme, including a custom one, passes through the contrast guard, and LOOK-8 and LOOK-39 hold for it in dark and light. | PLAN §1 (owner); theme_roles.dart; VL §3 as amended | `test/theme_roles_test.dart` (partial, arrives with the VL merge); missing: G18 |
| LOOK-8 | Contrast in every pack, dark and light: text1 ≥ 7:1 on ground and surface1–3; text2 and text3 ≥ 4.5:1 on ground and every surface they sit on; accent ≥ 4.5:1 on ground and surface1; onAccent ≥ 4.5:1 on accent; onDangerFill ≥ 4.5:1 on dangerFill; onAttentionFill ≥ 4.5:1 on attentionFill; attention text ≥ 4.5:1 on attentionSurface; icons ≥ 3:1 on ground and on surface1–3. Where a VL hex misses a minimum, the minimum wins. | PR §9; VL §3 | `test/accessibility_guidelines_test.dart` (partial: default theme, ten screens); `test/theme_roles_test.dart` (partial: pack floors, arrives with the VL merge); missing: G18 |
| LOOK-9 | Ambient colour fields on the ground belong to the theme (`ThemeRoles.ambient`, 0–3 fields, each at most 10 % alpha; none is valid); screens never add their own gradients. | VL §6 | missing: G18, G21 |
| LOOK-39 | An accent, from a pack or chosen by the person, keeps a hue distance of at least 30° from attention and from danger, and a CIEDE2000 ΔE of at least 20 from each, in dark and light. The accent picker offers no colour that fails this, and `deriveRoles` moves a custom accent out of the band. | VL §1 (owner: amber always means "needs you"); LOOK-4 | missing: G18 |

### 5.2 Type

| ID | Rule | Source | Gate |
|---|---|---|---|
| LOOK-10 | Interface type is Geist (variable, bundled as AppSans) and technical values are Geist Mono (AppMono); Space Grotesk and JetBrains Mono are removed; both OFL licences ship. | VL §2 | `test/repository_hygiene_test.dart` (licences); missing: G19 |
| LOOK-11 | Arabic uses the system face with the Noto fallback and zero letter spacing, and tests load Noto Sans Arabic from `test/fixtures/fonts`. | VL §2 | missing: G19 |
| LOOK-12 | Screens name a `KitText` role, never a size. The roles are exactly: largeTitle 32/38 w650 −0.025em; title 24/30 w650 −0.02em; headline 17/22 w600 −0.01em; body 16/24 w400; rowTitle 16/22 w500; secondary 14/20 w400; label 13/18 w600; caption 12/16 w600 +0.02em; button 16/20 w600; mono 13/19 w400. | VL §2 as rounded by §7 | missing: G19, G21 |
| LOOK-13 | Font sizes are integers. | VL §7 (owner) | missing: G19, G21 |
| LOOK-14 | At rest, text is painted in an opaque role colour (alpha 255): no `withValues(alpha:)` or `withOpacity` on a text colour and no `Opacity` wrapper around text; only a `KitMotion` transition fades it while it moves. Disabled text uses `text3`. No text shadow and no glow. | VL §7 (owner) | missing: G19, G21 |
| LOOK-15 | Section labels are sentence case, never uppercase and never wide-tracked. | VL §2; DS §6 | missing: G19, G21, G28 |
| LOOK-16 | Mono is only for technical values and is always laid out left-to-right. | VL §2; K2 §4.10 | missing: G19 |
| LOOK-17 | The top bar title uses `headline`; no text uses a size outside the role table (no 15 px slots, no 20/26 AppBar title). | VL §2 | missing: G19 |
| LOOK-18 | Numbers that line up in columns use their role with tabular figures; there is no separate `number` role. | K2 §9.2; VL §2 | reviewer |

### 5.3 Shape and depth

| ID | Rule | Source | Gate |
|---|---|---|---|
| LOOK-19 | Radii come from tokens: panel 18; needs-you and request card 22; sheet top 30; dialog 24; button 14; chip and pill 999; icon tile, code block and any other radius from its named token; composer 26 on compact and medium, 18 from expanded. | VL §4 | missing: G20, G21 |
| LOOK-20 | Depth comes from surface steps (ground < surface1 < surface2 < surface3), never from shadows or elevation on content. The only exceptions are the needs-you ring (a 4 px attention ring at 6 %, no blur) and one shadow on floating glass (y 6, blur 16, the `glassShadow` role at 30 % black in both brightnesses). Theme elevations for dialogs, sheets, snackbars, cards, popups and FABs are 0. | VL §4, §7 (owner) | missing: G18, G21, G22 |
| LOOK-21 | Every stroke (separators, card and field borders, `attentionLine`, the selected-segment outline, fold borders, glass rims) is exactly one physical pixel (`1 / devicePixelRatio`; Divider thickness 0), and focus rings are exactly two physical pixels, all snapped to the pixel grid. `KitTokens.hairlineWidth(context)` and `focusRingWidth(context)` give them; no part passes a logical width to `BorderSide`. A glass rim is a 1 px `glassRimLight` line on top and a 1 px `glassRimDark` line at the bottom, never a glow. | VL §7 (owner) | missing: G18, G21, G22 |
| LOOK-22 | Blur is used only behind glass: never on content, text, icons, transitions, or the modal scrim. | VL §7 (owner) | missing: G21 |
| LOOK-23 | Buttons: primary is accent with onAccent text; secondary is surface3; tertiary is a neutral text action (`text1`; `danger` only for Stop and destructive acts, `text3` when disabled; owner decision 1.1.0: secondary and inline actions such as Try again, Undo, Cancel and dismiss are neutral, and accent green marks only the main action); destructive is dangerFill only inside a confirmation; there are no tonal pills, and a state icon sits in an icon tile, not a tonal circle. | VL §5 | missing: G4; reviewer |
| LOOK-24 | In the conversation, Needs-you is the attention card (`KitRequestCard`: attentionSurface, attentionLine, radius 22) with its answer buttons in place. In Work and team lists it is a `KitRow` carrying the `KitNeedsYou` mark and word that opens the conversation scrolled to the card. In the Inbox it is the request card itself, answerable in place on any server (owner B8, AUTO-17). Only `KitNeedsYou` and `KitRequestCard` draw the attention look. | VL §1, §5 as amended; PD P4.2a; TIA §1.2; K2 §4.5 | missing: G17 |
| LOOK-25 | Sheets have a grabber, an icon tile and a start-aligned `title`; consequences are a surface1 panel of rows; buttons are stacked full width on compact and sit in one end-aligned row from medium up. | VL §5; K2 §4.7 | `test/goldens/kit/kit_sheet_golden_test.dart` (partial: detects change only); reviewer against the approved render (EVID-12) |
| LOOK-26 | Transcript and composer: the prompt is an end-aligned surface2 bubble (radii 20/20/6/20); agent text is plain body; work folds into one chip ("Read 3 files · edited 1"); a code block has a file header with `+n −n` and copy; the prompt bubble is wide by default (`KitBubbleWidth.auto` hugs its words up to the width minus 48 dp, no 85 % cap; the 85 % `compact` bubble is an appearance option, `kit_message.dart:30-43`); the composer is a surface2 glass pill holding attach, field, model chip, voice, and send; live status and Stop sit on its top edge (the living edge, `kit_composer.dart:892-908`), the mic stays usable while a reply runs, and a Stop circle replaces the trailing control only for a host that passes `onStop`; send is an accent circle and Stop a red `dangerFill` circle with an `onDangerFill` square. Sheets stay solid (LOOK-27). | VL §5 | reviewer against the approved render (chat goldens, EVID-12) |

### 5.4 Glass and metal

| ID | Rule | Source | Gate |
|---|---|---|---|
| LOOK-27 | Glass (`KitGlass`) appears only on the floating navigation layer: the top controls (server pill, search button), the composer, the floating tab bar with its active-tab lens, the navigation rail (`KitNavRail`) from medium up, whose selected destination is the lens, and on PC the sidebar header and toolbar. Never on rows, cards, the needs-you card, the transcript, sheet bodies, a jump pill, a settings page, a whole screen or a background. Settings › Appearance › Effects previews glass through the real navigation layer on screen; if it needs a sample, it is a miniature floating tab bar built by the kit. Files allowed to construct glass: `lib/ui/kit/glass/**` and the kit files of the navigation-layer parts (composer, floating tab bar, rail, top controls, desktop sidebar header and toolbar); outside the kit, only `lib/ui/screens/home_screen.dart` and `lib/ui/widgets/glass_surface.dart`, as a shrinking allowlist until those move into kit parts. | VL §6 (owner) | missing: G21 |
| LOOK-28 | Glass never sits on glass, and several glass parts on one screen share one backdrop read through `BackdropGroup`. | VL §6; DS §10 | `test/kit_glass_test.dart`; missing: G22 |
| LOOK-29 | Glass is liquid (`shaders/kit_glass.frag`) on Impeller with Android 12+; frosted before the shader loads and on older devices; `surface2` at 94 % when Effects › Glass is off, and automatically under battery saver or SEVERE heat (MOT-13); fully opaque `surface2` under high contrast, accessible navigation or remove animations. | VL §6; DS §10; PV battery-heat | `test/kit_glass_test.dart`, `test/appearance_effects_test.dart`; missing: G22 |
| LOOK-30 | Glass that holds text or a field has a dimming layer so what is behind reads as colour, never as letters, and its labels stay at 4.5:1 or more over any backdrop, liquid or frosted. | VL §6; DS §10 | `test/glass_surface_test.dart`; missing: G22 |
| LOOK-31 | A unit may merge a new glass surface; the wave checkpoint measures it with glass on and off by the G42 protocol, and a failure reverts or solidifies it before the next wave. The shader stays one pass over its own rect, with two texture reads and no loops. | DS §10 | missing: G42 (checkpoint); reviewer (shader) |
| LOOK-32 | Metal is dropped: nothing has a metallic or chrome treatment. | VL §6 (owner) | missing: G21 |

### 5.5 Icons, images and drawings

| ID | Rule | Source | Gate |
|---|---|---|---|
| LOOK-33 | Icons are designed at 20, 22 or 24 logical px; only leading and state icons grow with the person's text size, by at most `KitTokens.maxIconScale` (1.5), with the scaled size rounded to a whole physical pixel (until the owner answers B16). They come from one family (Phosphor, regular weight), aligned to whole pixels, with one glyph per verb through `AppIcons`; a fill or duotone glyph is used only for the dock's selected tab. | VL §4, §7 | `test/app_iconography_test.dart`; missing: G20, G21 |
| LOOK-34 | A row's leading icon sits in a 30 px surface3 tile; the bottom navigation is a floating bar 60 px tall with radius 22, surface2 at 82 %, in glass. | VL §4 | missing: G20 |
| LOOK-35 | Images are decoded at the device pixel ratio with `FilterQuality.high`; illustrations are vector (`KitScene`) or shader, never scaled bitmaps. | VL §7; DS §10 | missing: G21 |
| LOOK-36 | Drawings share one style (line art in the brand stroke with rounded caps and joins, one accent for what matters, muted strokes otherwise, and flat accent fills at no more than 12 % alpha with hard edges: no gradient, blur, glow or feathering), drawn in code as a `KitScene`; `KitPortalScene` is the reference. | DS §10 | `test/kit_states_scenes_test.dart`; reviewer |
| LOOK-37 | Drawings appear only at moments (first run and setup, waiting, finished, empty, a few failures), never in a row, field or dialog, between 140 and 200 dp tall, and never push the primary off a 412×915 screen. | DS §10 | missing: G21; reviewer |
| LOOK-38 | A drawing is excluded from semantics unless it says something the text does not. | DS §10; PR §9 | `test/kit_illustration_test.dart` |

---

## 6. Layout and adaptive

**Words for windows.** In every rule, "phone" means the compact class; "tablet" means medium and expanded; "PC" means large, or expanded with a fine pointer (`KitLayout.finePointer`). Rules never test the platform (ARCH-11).

| ID | Rule | Source | Gate |
|---|---|---|---|
| LAY-1 | Window classes come only from `KitLayout` (compact < 600, medium 600–839, expanded 840–1199, large ≥ 1200, measured on the window); nothing outside the kit compares a width to a literal. | K2 §8.1, G15 | `test/kit_ratchet_test.dart` (G15) |
| LAY-2 | Inside the kit, only `kit_layout.dart` compares a window width to a literal (the class breakpoints). Every layout width is a named constant in `KitLayout` (reading 720, list 960, panes 296/700/340, dialog 560, confirm 480, end sheet 400–480, medium sheet cap 640), and parts read those names and decide layout from the window class (`KitActionBlock` goes to one row from medium up). | K2 §8.1 | missing: G15x, G20 |
| LAY-3 | A window shorter than 480 dp keeps the compact, stacked layout. | K2 §8.5 | `test/kit/kit_sheet_test.dart` |
| LAY-4 | The one size set. **Overflow sizes** (G6, A11Y-2, TEST-10): widths 320, 360, 412, 600, 800, 840, 1280 and 1600 dp, plus 915×412 landscape. **Gallery sizes** (TEST-9): 360×800, 412×915, 915×412, 800×1280, 1280×800 and 1600×1000. Every part and screen works at every overflow size: on expanded a sheet becomes a 560 dialog or a 400–480 end sheet, a confirm is 480 centred, and `KitScreen` caps reading content at 720 and lists at 960. | K2 §8.2, §8.4 | missing: G4, G6 |
| LAY-5 | From expanded, `twoPane` uses the list pane at 296 (`KitLayout.paneListWidth`, replacing K2's 360) and the conversation up to 700; on large, the changes pane is 340. | VL §5; K2 §8.1 as amended | missing: G20 |
| LAY-6 | The screen body's 16 dp side gutter comes from `KitScreen`; no screen pads its body by hand; the last content scrolls clear of the dock, the pinned primary and the keyboard. | VL §4; DS §1 | missing: G21; reviewer |
| LAY-7 | Spacing comes only from named `KitTokens` values: space1–6 (4, 8, 12, 16, 20, 24), panel padding 16, one-line row 54, two-line row 60, 22 between sections, 8 between a section label and its panel; separators are inset to the text start. | VL §4; KitTokens | missing: G20, G21 |
| LAY-8 | Layout is directional: no `EdgeInsets.only(left:/right:)`, asymmetric `EdgeInsets.fromLTRB`, `Alignment.centerLeft/Right`, `TextAlign.left/right` or `Positioned(left:/right:)` (use the directional forms). Back sits at the start and Close at the end. Directional glyphs mirror in RTL through `AppIcons` (`matchTextDirection`): back, forward, chevrons, reply, undo and redo, send, list indent and progress direction; play and media controls, check, clock, search, brand logos and anything showing code or a keyboard key do not. Inside a forced-LTR technical value (`KitTechnicalValue`, `KitCodeBlock`, `KitLogPanel`, `KitDiffView`) left alignment is correct. | K2 G7, §1.1, §1.18 | missing: G7 |
| LAY-9 | Every touch target is at least 48×48 dp on every window and input, and no two targets' 48 dp areas overlap; a destructive target has at least 8 dp between its 48 dp area and any other target. Buttons are 50 dp tall. A fine pointer adds hover, never smaller targets. | PR §6; K2 §8.3 | `test/accessibility_guidelines_test.dart` (size); missing: G5, G20, G37 (destructive gap in KitActionBlock) |
| LAY-10 | On a PC everything works from the keyboard: Tab follows reading order and reaches every action with a visible focus ring; Esc closes the top modal and obeys draft and dirty rules; Enter confirms only a neutral confirmation. | K2 §8.2, §8.3, G14 | `test/kit/kit_keyboard_test.dart`; missing: G14x |
| LAY-11 | Right-click and long-press open the same `KitRowMenu`, and a tooltip only repeats a label the semantics already carry. | K2 §8.3 | missing: G14x |
| LAY-12 | A screen or state shows at most one visible primary; on compact it is full width and pinned at the bottom or in the lower half, never at the end of a long scroll. | DS §1, §2; PR §6 | missing: G37 |
| LAY-13 | An action block orders primary, then secondary (full width, stacked), then at most two tertiary actions at the start edge, with the rest in More; from medium up it may be one end-aligned row with the primary at the end; labels wrap to two lines. | DS §2; VL §5 | missing: G4 |
| LAY-14 | A destructive action never sits next to a frequent one: `KitActionBlock` switches to the stacked layout when a tertiary action is destructive. | K2 §2.7, §4.2 | missing: G37 |
| LAY-15 | The dock is exactly Work · Inbox · Project · Settings; Project appears only on servers with project tools; the AI Team is not a tab. | TIA §1.1 | `test/home_navigation_test.dart` (partial) |
| LAY-16 | Each wave checkpoint proves the migrated pages on a phone AVD, a tablet AVD and a large-window AVD (at least 1280 dp wide, keyboard and mouse). | PLAN §1 (owner: adaptive); K2 §8 | reviewer (checkpoint); missing: G32 |

---

## 7. Interaction, motion and haptics

| ID | Rule | Source | Gate |
|---|---|---|---|
| MOT-1 | Animation durations and curves (anything passed to an `AnimationController`, an `Animated*` widget, a transition or a scroll animation) come only from `KitMotion` (quick 150 ms, standard 250 ms, entrance 900 ms, celebration 1.4 s, breath 4 s; curves enter, exit, emphasized). Under `lib/ui/`, outside `kit_motion.dart`, no `duration:` or `reverseDuration:` argument is a `Duration(` literal and no `Curves.` appears. Waiting, escalation and hold times in the kit are named constants in `kit_motion.dart` (`KitMotion.escalateAfter` 8 s, `undoWindow` 8 s, `copiedHold`). Timeouts, polls and backoff outside `lib/ui/` are not motion and are not counted, but they are named constants, not bare literals at call sites. | DS §10; K2 §4.11, §1.10 | missing: G2 |
| MOT-2 | Transitions slide or cross-fade on quick or standard; they never blur, scale or fade-scale (this includes `KitTabSwitcher` and `KitDialog`). | VL §7 (owner) | missing: G21 |
| MOT-3 | Every platform but iOS uses the one `KitPageTransitionsBuilder`; screens never choose a transition. | DS §10 | `test/kit_motion_app_test.dart`; missing: G21 |
| MOT-4 | Pull to refresh is always `KitRefresh`. | DS §10 | G16 |
| MOT-5 | Animate paint and transforms only: layout animation exists only inside `KitReveal` and the `KitButton` spinner slot, never in a scrolling list item; tickers stop off screen. | DS §10; K2 §4.11 | missing: G2, G21 |
| MOT-6 | A drawing's entrance plays once; an ambient loop runs only on a waiting screen, at most one per screen, only under Animations: Full (`KitMotion.loopsIn`), and stops when the wait ends; resting screens are still. | DS §10; K2 §1.12 | `test/kit_illustration_test.dart`; reviewer |
| MOT-7 | Under the system's remove animations or Effects › Animations: Off, every part settles after one `pump()` with no running ticker, and every drawing shows its finished frame. | K2 G8; DS §10 | `test/kit_motion_test.dart`; missing: G8x |
| MOT-8 | Only `kit_motion.dart` reads reduced motion (`disableAnimationsOf`); every other part asks `KitMotion.reduced(context)`. | K2 §2.9 | missing: G21 |
| MOT-9 | Scenes take their time only from `KitIllustration` (no AnimationController, Ticker or Timer in `lib/ui/kit/scenes`). | DS §10 | `test/design_standard_test.dart` |
| MOT-10 | `flutter_animate` is never a dependency and never imported anywhere. | AG Testing | missing: G25 |
| MOT-11 | Vibration goes only through `KitHaptics`: send (the person's words leave), done (a waited-for finish, once) and commit (a confirmed stop, delete or discard); nothing on scroll, rows, failures, local choices, copy, sheets or dialogs; nothing with Vibration off. | DS §10; K2 §2.13 | `test/kit/kit_confirm_sheet_test.dart`, `test/kit_motion_app_test.dart`; missing: G2 |
| MOT-12 | Settings › Appearance › Effects holds four app-wide choices (Glass, Animations Full/Calm/Off, Celebrations, Vibration) stored next to the theme; the system's remove animations always wins and the page says so; code reads these choices only through the kit. | DS §10 | `test/appearance_effects_test.dart`; missing: G21 |
| MOT-13 | Under battery saver or SEVERE heat, effects drop automatically to Animations: Calm and to the Effects-off glass (`surface2` at 94 %, LOOK-29). | PV battery-heat | missing: G39 |
| MOT-14 | New animation meets the G42 thresholds at the wave checkpoint. | DS §10; PR M2 | missing: G42 |

---

## 8. Copy and localisation

### 8.1 Files and mechanics

| ID | Rule | Source | Gate |
|---|---|---|---|
| COPY-1 | All user-facing copy lives in `lib/l10n/app_en.arb`, with its Arabic in `lib/l10n/app_ar.arb` added in the same change; no file gains a hardcoded string, including a literal passed to a kit part's `String` parameter (`title:`, `label:`, `message:`, `body:`, `supporting:`, `why:` …), and the baseline is never raised. | AG; PLAN §6 | `test/l10n_coverage_test.dart` (partial: `Text(` and seven named parameters until §0.5 step 3); missing: G27 |
| COPY-2 | Text from the server (tool titles, model names, agent descriptions) is shown as sent, not translated. | localization-todo | reviewer |
| COPY-3 | ARB keys are camelCase `<prefix><Thing>`. For screen copy the prefix is the page's map `module` in camelCase (`chat`, `work`, `phone`, `team`, `library`, `servers`, `settings`, `system`, `shell`, `review`, `terminal`, `usage`, `voice`, `files`); for copy owned by a kit part it is `kit` (`kitSheetClose`, `kitUndoAction`). No new key uses the legacy prefixes `e7`, `teamUi`, `aiteam`, `termux`, `builtin` or `local`, and no new key is numbered; every new key has an `@description` saying where it shows and what it means; every placeholder is declared with a type; counts use ICU plurals and dates use `intl`, never string concatenation. | ui-feature-audit §7; localization-todo | missing: G27 |
| COPY-4 | The generated `lib/l10n/app_localizations*.dart` files are never edited by hand; they come from `flutter gen-l10n`. | AG; l10n.yaml | missing: G27 (integrator step) |
| COPY-5 | `app_ar.arb` is the source of truth for Arabic and is edited directly; `tool/assemble_arabic_arb.py` is not run, because it would revert reviewed values. | AR; Appendix A | reviewer |

### 8.2 English words

| ID | Rule | Source | Gate |
|---|---|---|---|
| COPY-6 | The visible nouns are Conversation (never session or chat), Project (never workspace or location), Server (never profile or host), Inbox (never activity or attention) and "On this phone" (never Termux setup, On-device setup or Local server); "task" is used only for AI Team work; code identifiers and slash commands keep their old names. | UXR §3, §6; TB | `test/ui_glossary_test.dart`; missing: G28 |
| COPY-7 | The verbs are fixed: "Try again" to recover from a failure (never Retry, Reload, Check again or Abort); "Delete" only when data is gone; "Remove" only when something is forgotten on this device; "Stop" to end running work ("Cancel" only dismisses a dialog or sheet); "Disconnect" to leave a server. | UXR §6 | `test/ui_glossary_test.dart`; missing: G28 |
| COPY-8 | A button label is a verb that says what will happen; no button is OK, Yes, No, Continue, Confirm or Submit. | PR §2; K2 §4.2 | missing: G28 |
| COPY-9 | A confirmation's title is a question naming the thing, ending in "?" (Arabic "؟"); its confirm label names the act and the thing in at least two words ("Delete conversation"); its cancel word follows the kind ("Keep running" for stop, "Keep editing" for discard, otherwise "Cancel"); its body is at most two sentences and says whether the act can be undone. | K2 §1.2, §4.2, G11 | `test/kit/kit_confirm_sheet_test.dart`; missing: G11, G37 |
| COPY-10 | The fixed words of a page, sheet, dialog or state-view title (the ARB value with its placeholders removed) are at most four English words in sentence case, and a placeholder counts as one word whatever it holds; a body is at most two short sentences. Titles that are data (a conversation, project, task or server name) are shown as written and wrap (KIT-36, COPY-2). | PR §2; K2 §1.1, §1.18 | missing: G28 |
| COPY-11 | Paths, ports, ids, status codes, plugin ids, versions and engine words (convoy, formula, bead, rig, city, polecat, refinery, sling, wisp, mayor, gastown, PTY, SSE, 127.0.0.1) appear only (a) inside `KitDetailsFold`, `KitLogPanel`, `KitCodeBlock` or `KitTechnicalValue`; (b) as the value or example of a `KitField` in mono, path or url mode where the person types that value; or (c) on a technical page AUTO-1 lists. An ARB key carrying one has an `@description` that starts with `Technical:` or `Field example:`. | PR §2; K2 §1.8, G11; AUTO-1 | `test/ui_glossary_test.dart` (AI Team); missing: G11 |
| COPY-12 | Backend and product names (OpenCode, Codex, Paseo, Gas City) appear in labels only where the person chooses between them ("Ask {agent}…"). | UXR §4 | missing: G28 |
| COPY-13 | AI Team words: a run is a task named by its title with at most four stages (Waiting, Working, Reviewing, Done); agents are named by role from `lib/ui/widgets/team_vocabulary.dart`; the host line is "On this phone" or "On {computer}", plus "· Paused" or "· Not answering" when true; the board's columns are Backlog · Ready · Working · Review · Done, and Blocked is a flag on a card. | TB | `test/team_redesign_test.dart`; reviewer |
| COPY-14 | Server and provider errors reach the screen only through `agentErrorWords(raw, l10n)`: a recognised cause shows the app's headline and hint, an unknown one keeps its own sentence minus programmer talk, and the raw text goes behind Details. | lib/ui/agent_error_words.dart | `test/agent_error_text_test.dart` |
| COPY-15 | A permission request's title is its plain action (Run a shell command, Edit a file, Read a file, Access an external directory, Continue after repeated failures); an unknown or empty permission id is titled "Permission needed", with the id behind Details. | permission_presentation.dart; PR §2 | missing: G47 |
| COPY-16 | Refresh: no refresh button on the primary tabs (Work, Inbox, a conversation) or on live pages; any other screen has at most one refresh control, pull or icon, not both. | UXR §6; K2 §1.10; PV | missing: G37 |
| COPY-17 | Words never contradict the state on screen or on the host: no Working with stopped, Paused with idle, or Connected with reconnecting; a starting service says "Starting…", not "Stopped". | PR §2; K2 G11 | missing: G11 |
| COPY-18 | One action has one ARB label key and one `AppIcons` glyph, reused by every surface that offers it (menus, slash commands, the desktop palette). | PR §7; UXR §4 | reviewer |
| COPY-19 | A term that needs explaining is explained in place by `KitTerm`, never by a separate page. | TIA §1.4; OV | missing: G41 |
| COPY-20 | Each nudge is shown once, with "Show tips again" in Settings › Help; a nudge that cannot name a concrete benefit at its moment is dropped. | UXR §10.5 | `test/nudge_moments_test.dart`; reviewer |
| COPY-21 | There is no simple or expert mode and no persona picker: one set of screens serves everyone through summary, then the Details fold, then a technical page. | PV §1 | missing: G28 |

### 8.3 Arabic and right-to-left

| ID | Rule | Source | Gate |
|---|---|---|---|
| COPY-22 | Arabic has exactly the English key set, with the same placeholders per key; an Arabic value identical to the English is allowed only on an allowlist (product names, templates). | PLAN §6; AR | missing: G27 |
| COPY-23 | Arabic is Modern Standard Arabic in written register (no spoken forms such as قل), and every plural has the zero, one, two, few, many and other forms. | AR | missing: G27 |
| COPY-24 | Technical tokens stay in Latin script and literal (OpenCode, Termux, Ubuntu, Git, commands, URLs, IPs, variables, field names, unit symbols, key combinations), and Arabic marks commands, fields and controls with «», never backticks or ASCII quotes. | AR | missing: G27 |
| COPY-25 | The Arabic vocabulary is fixed: AI Team = فريق الذكاء الاصطناعي; computer = حاسوب; tap = اضغط (never انقر, and اضغط means only tap); touch and hold = اضغط مطولًا; Try again = إعادة المحاولة; Usage = الاستخدام; blocked = معطّل; task · step · agent · worker = مهمة · خطوة · وكيل · عامل; conversation = محادثة (never جلسة); project = مشروع (never مساحة العمل); progress is phrased جارٍ …. | AR; UXR | missing: G27 |
| COPY-26 | Arabic values contain no directional control characters (U+200E, U+200F, U+202A–U+202E, U+2066–U+2069); isolation is the kit's job (KIT-32). | AR | missing: G27 |
| COPY-27 | Where the English avoids engine words, the Arabic does too. | AR | missing: G27 |
| COPY-28 | Every `AgentErrorCause` has an Arabic headline and hint that differ from the English. | agent_error_text_test | missing: G27 |
| COPY-29 | Until Arabic is complete, the language choice says "Arabic · partly translated (N %)" from the real coverage; the language follows the system by default. | PV rtl-l10n | reviewer |
| COPY-30 | A placeholder value inside a sentence is wrapped at the call site by the kit: `KitBidi.ltr(value)` (LRI…PDI) for a technical value (path, command, host, id, version, size with a unit) and `KitBidi.auto(value)` (FSI…PDI) for a name the person or the server chose (project, server, conversation, agent). No U+2066–U+2069 or U+200E literal appears outside `lib/ui/kit/`. A standalone technical value is laid out LTR but aligned to the start of its row; code blocks, logs and diffs are LTR blocks aligned left in both directions. Digits come only from `intl` formatting for the current locale; no unit converts digits by hand (the digit shape is B17). | K2 §4.10; AR | missing: G7 (bidi literal ratchet) |

---

## 9. States and honesty

| ID | Rule | Source | Gate |
|---|---|---|---|
| STATE-1 | Every moment that is not the normal content is a `KitStateView` (page or inline) with fixed slots: icon, a title saying the state now, a body of at most two sentences, optional progress, the §2 actions, and Details last and collapsed; a message about one part is a `KitNotice`; no card wraps a message. | DS §3; K2 §2.2 | `test/design_standard_test.dart`; missing: G2 |
| STATE-2 | An empty screen says what will be there and offers the first step. | DS §3; PR §3 | reviewer |
| STATE-3 | An error-tone state offers "Copy details" and "Report a problem" (prefilled with that error, redacted, previewed); a network error offers a fix (Try again, Switch server) instead of a report. | K2 §2.2; PV help-feedback; TIA §1.2 | missing: G9 |
| STATE-4 | A screen shows one 2 dp loading bar directly under the top bar, plus any `KitProgressRow` bars; loading lists use `KitSkeletonRows`. | DS §4; K2 §1.13 | `test/design_standard_test.dart`; G16 |
| STATE-5 | No wait is silent past 8 s: `KitStateView`, `KitStatusLine`, `KitReceipt`, `KitField` validation and `KitChecklist` take `since` and escalate by themselves ("Still waiting", "Not confirmed yet") with a way out. | PR §3; K2 §4.9 | missing: G9 |
| STATE-6 | A job that may take more than 30 s states its expected time before it starts and then shows staged or measured progress with one line of words ("29 of 30 MB · about 1 min left"), never an unlabelled spinner. | PR §3; K2 §2.8 | reviewer |
| STATE-7 | A button never shows lasting status: `working` is only for its own tap in flight, and lasting progress belongs in a state view or progress part. | DS §2; K2 §4.9 | reviewer |
| STATE-8 | A disabled control shows its reason as visible text near it (`disabledReason`, or the row's supporting line), never only in a tooltip; otherwise it is hidden. | K2 §2.7, §4.9, §8.3 | missing: G37 |
| STATE-9 | A state is never shown by colour alone: every mark has its word ("Connected · …", "Needs you", "Near limit", a check on the selected segment, +/− on diff lines), and the current row's supporting line starts with the state word. | PR §9; DS §6; K2 | missing: G37 |
| STATE-10 | Every write the phone sends shows a `KitReceipt` in place (sending, sent, confirmed, not confirmed, refused); "Sent" is never shown as "Done"; confirmed needs the server's echo; after 8 s without one it says "Not confirmed yet" with Try again. | K2 §1.16; JN receipts | `test/team_gate_answer_test.dart` (team); missing: G9 |
| STATE-11 | Partial data says it is partial ("12 loaded · searching the server…"); a checklist mark comes from the engine's state, never inferred, and an existing install shows "Already installed". | K2 §1.5, §1.12 | missing: G9 |
| STATE-12 | A capability the server lacks never shows as a dead row: one muted line says where it is available and, if it can be turned on, offers that; `whenMissing: hidden` is allowed only where no enable flow exists. | CAP §4; TIA §1.3; PLAN §4 | missing: G13 |
| STATE-13 | There are no dead ends: a "finish X first" message carries the button that does X. | CAP §4 | missing: G37 |
| STATE-14 | The shell has exactly one status line under the header, showing the highest-priority condition in the KIT-35 order (connection, Android stopped the app, heat, a risky switch on the current screen, update ready). | TIA §1.1; JN | `test/chat_states_standard_test.dart` (chat); missing: G37 |
| STATE-15 | A failure the agent got past stays folded and is not reddened ("The agent carried on after this."); the work line opens and reddens only when the turn ended on a failure. | message_view.dart | `test/chat_transcript_placement_test.dart` |
| STATE-16 | The transcript follows the turn model: a prompt, then a turn (everything until the agent hands back), made of steps whose boundaries are not drawn, and notices that do not end the turn; a finished turn has one footer and one More control on its last step; a running turn has no footer; a prompt has no control row and opens its menu on long-press; Copy on a turn copies the whole turn's reply; text between steps folds under the work line, titled by what was done. This rule is the frozen statement of the turn model; chat parts cite it (KIT-41). | message_view.dart (vocabulary comment); MEM turn model | `test/chat_transcript_placement_test.dart`, `test/chat_transcript_lens_test.dart` |
| STATE-17 | Everything waiting to reach the agent (offline drafts and OpenCode 2 inbox sends) is merged into one queued bubble at the end of its conversation. It is styled as the person's prompt bubble (`surface2`, LOOK-26), lists each waiting message in order, and carries one clear action line inside it ("3 waiting · Send now", "Edit"), with per-message actions in its `KitRowMenu`. The Inbox never lists the offline queue. | OV (embedded-pending-sends-strip); message_view.dart; TIA §1.1 | `test/pending_sends_strip_test.dart` (to update by the chat chain) |
| STATE-18 | Offline, values show the last known data with its age. | PV reliability | `test/release_blockers_test.dart` (partial); reviewer |
| STATE-19 | App updates, the code push and server updates are the "update ready" condition of the one status line (KIT-35, lowest priority), never a separate line; the code push downloads silently, applies at the next cold start and is announced once. | PV upgrade; TIA §2 | `test/release_blockers_test.dart` (partial); reviewer |
| STATE-20 | Each page has at least the states for its kind. A screen or tab that reads the server: loading (skeleton or bar), empty (STATE-2), error (STATE-3), not answering (the last known data with its age if the page had data, otherwise a `KitStateView` with Try again or Switch server), and loaded, plus partial where it pages. A sheet that fetches: loading inline, an inline `KitNotice` error with Try again, loaded; offline it shows the last value with its age, or a disabled primary with its `disabledReason`. A sheet or dialog with only local data: loaded, plus working and failed if it writes (DATA-14). An overlay or embedded part: the states of its host. The page's state list is its map `states` plus its wave-2 `statesMissing` (STATE-21); each has a test, and its goldens follow TEST-10. | DS §3; K2 §2.2; MAP | reviewer; missing: G32 (state list in the record) |
| STATE-21 | A map `statesMissing` entry is wave-2 work when the condition can already be detected from state the page receives today (a controller field, a gateway call it already makes, a capability flag); the unit then renders it with the kit state part and a test. Otherwise it is deferred: the QA record lists it under "Deferred states" with the reason ("needs <data>") and the owning slice id, or "no owner" for the coordinator. A unit never adds a gateway call, controller field or persistence to render a missing state in wave 2. | PLAN §4; MAP | reviewer; missing: G32 |

---

## 10. Data safety

| ID | Rule | Source | Gate |
|---|---|---|---|
| DATA-1 | The composer, every multiline input, dictated text, queued prompts and chosen attachments survive back, swipe, Esc, crash, force stop and a lost server (`KitDraft` or the offline queue). Other input survives back, swipe, Esc and close through the DATA-3 discard question; losing it on a crash is allowed. | PV data-safety; K2 §4.1 | `test/kit/kit_draft_test.dart` (partial: drafts); reviewer (PROC-31) |
| DATA-2 | Every multiline input in a sheet uses `KitDraft`: back, swipe, Esc and close keep the text silently, reopening restores it, and the caller clears it once used; `dirty` alone is only for input that cannot be kept as a draft. | K2 §1.1, §1.4 | `test/kit/kit_draft_test.dart`; missing: G48 |
| DATA-3 | With only `dirty` set, swipe, back, Esc, close and a tap outside ask the discard question inside the sheet ("Keep editing" is the default); clean input closes without asking. | K2 §1.1, §8.2 | `test/kit/kit_sheet_test.dart`, `test/kit/kit_keyboard_test.dart` |
| DATA-4 | Per-profile storage keys are named `oc.<what>.<profileId>` (drafts `oc.draft.<target>.<profileId>`) so that `ProfileStore.profileScopedPreferenceKeys` removes them on profile deletion. A draft that belongs to the app rather than a server (Report a problem, which also opens with no server) uses `KitDraft.appWide` (`oc.draft.<target>.app`), is cleared when used, and is listed with its reason in G10's `_appWideDrafts`; a profile id never falls back to a literal (kit-polish 2026-09-27). | AG Security | `test/profile_deletion_test.dart`, `test/kit/kit_draft_test.dart`; missing: G29 |
| DATA-5 | Per-profile data kept in a shared blob is removed by extending `ConnectionController.deleteProfileAndLocalData`, with a delete-profile test for each new blob. | AG Security | `test/profile_deletion_test.dart`; reviewer |
| DATA-6 | Choices are remembered per server and never asked again: supervision, project, model, new-conversation mode and delivery (Queue or Steer). | PV; TIA §1.1 | `test/team_discover_test.dart` (partial); reviewer |
| DATA-7 | Nothing is deleted silently: removing a server first lists its queued prompts with their count, and evictions are reported. | PV reliability | `test/offline_queue_test.dart`; reviewer |
| DATA-8 | Before anything is deleted, the screen says what survives; "Remove from this phone" defaults to keeping the projects. | CAP §4 | reviewer |
| DATA-9 | A stored-format change ships with a one-time silent migration that is tested to run once and leaves no permanent screen. | PV upgrade; AG Workflow | `test/notification_preferences_test.dart` (one case); reviewer |
| DATA-10 | An update never loses data or needs a reinstall; existing installs are detected and adopted by the v2 check scripts. | PV upgrade | `test/setup_scripts_test.dart` (partial) |
| DATA-11 | Every act gets one treatment by asking "if tapped by mistake, what is lost?": nothing, so act; something the app can restore, so act and offer Undo; something nobody can restore, so confirm first. An act is never both confirmed and undoable, gets the same treatment from every door, and follows the K2 §4.1 table. (a) Undo for a server act is an inverse call only when the gateway exposes one (a capability flag); without one, a deleting act is confirmed. (b) A local act uses a deferred commit (`onCommit`), which runs when the window closes, when a new undo arrives, when the route pops, or on `AppLifecycleState.paused`, so nothing is pending across a kill. (c) Stopping the agent's current turn is "neither" (the conversation keeps everything); stopping a team task or a worker mid-work is confirm (`stop`). (d) An act the table lacks is proposed as a new row through PROC-20; until then the unit applies the question. | K2 §4.1; JN | `test/safety_confirms_test.dart` (partial); missing: G40 |
| DATA-12 | A heavy delete (the phone's Ubuntu, a worktree or workspace with changes) needs the exact name typed; the confirm stays disabled with a visible reason until then. | K2 §1.2, §4.1 | `test/kit/kit_confirm_sheet_test.dart`; missing: G40 |
| DATA-13 | A confirmation's consequences list everything else the act removes, with counts ("Queued prompts for this server: 3"). | K2 §1.2 | reviewer |
| DATA-14 | While a confirmed act runs the confirmation shows working; on failure it stays open with a `KitNotice` and Try again, and never closes on an error. | K2 §1.2 | `test/kit/kit_confirm_sheet_test.dart` |

---

## 11. Automation and the Needs-you contract

| ID | Rule | Source | Gate |
|---|---|---|---|
| AUTO-1 | Technical detail lives only in the Details fold, `KitRowMenu`, technical pages reached from Diagnostics or a server's details, and shortcuts and the palette, never inside a primary flow. | PV §1; TIA §3 | missing: G41 |
| AUTO-2 | One job has one landing place: the same job started from any door ends on the page named in TIA §2. | TIA §2 | reviewer |
| AUTO-3 | Glance pages (Work, Inbox, Team home) show one line per item and at most one secondary line; work pages put content first; only the technical pages AUTO-1 lists may show more than two lines per item. | PV §1 | reviewer |
| AUTO-4 | Every automatic action is announced afterwards in one line (`KitAutoLine`) and recorded in Inbox › While you were away, with Undo where the server allows. | PV §1, §3 | missing: G39 |
| AUTO-5 | Each automatic behaviour ships with a test that fails when the automation is reverted. | PV automation-first | missing: G39 |
| AUTO-6 | Manual levers (nudge, reassign, restart, stop, per-agent pause, manual refresh, manual address entry) stay available in the overflow or `KitRowMenu`, beside what the automation did, never as primary buttons. | PV §3; TIA §4 | reviewer |
| AUTO-7 | Never automated: deleting a project, the phone's Ubuntu, server data or a conversation; applying a revert; resetting or removing a worktree; public share links; removing credentials; switching organisation; signing in. | PV §3 | missing: G39 |
| AUTO-8 | A picker with one option is skipped, and the app picks defaults instead of asking: the only or last-used project, "my-app" on first run, the server's default model, the voice pack by total RAM (never `memoryClass`), the voice by locale, Queue rather than Steer, a review scope that has changes. | PV automation; MEM | missing: G39 |
| AUTO-9 | The app raises Needs-you only for: a decision only the person can make (a permission outside saved rules, a question or form, a reserved gate, a merge under High supervision); work that cannot continue (credentials rejected, a limit with no fallback, storage full, a step still failing after automatic retries, saying what was tried); a first-time consent at the moment it becomes relevant. | PV §3 | missing: G38 |
| AUTO-10 | Every Needs-you item says why it is asking and what happens if it is ignored ("the team waits; nothing is lost"). | PV §3 | missing: G37, G38 |
| AUTO-11 | Needs-you items come from one attention source, are counted once per request across servers, are listed oldest first, and each names its server. | PV §3; TIA §1.1 | `test/home_navigation_test.dart` (partial); missing: G38 |
| AUTO-12 | An item answered anywhere disappears everywhere (tab badge, Inbox row, Work line, server row, header word) and its notification is cancelled. | PV §3; K2 §4.5 | `test/elsewhere_attention_test.dart` (partial); missing: G38 |
| AUTO-13 | Reconnecting, restarts, heat pauses, updates, progress and finished work are never Needs-you: they go on quiet status lines and the quiet notification channel, and are collected in While you were away. | PV §3 | missing: G38 |
| AUTO-14 | "Done" notifications are opt-in per kind, on by default only for team tasks and long runs. | PV §3 | `test/notification_preferences_test.dart` (partial); missing: G38 |
| AUTO-15 | While a request waits, the conversation's work line says "Waiting for you", never "Running tools" or a spinner. | JN; TIA §1.2 | missing: G38 |
| AUTO-16 | A Needs-you notification for a permission request carries the quick answers "Allow once" and "Don't allow" (owner, B7), and every answer leaves a receipt; a question, form or team gate has no quick answers. A tap on any Needs-you notification lands on the conversation scrolled to the request card (on another server, without switching the active server, owner B8), or says it was already answered and when. | TIA §1.2; PV notifications | `test/background_notification_navigation_test.dart` (to update); missing: G38 |
| AUTO-17 | All agent requests (permission, question, form, team gate) use the `KitRequestCard` family in the conversation: the header says who asks about what, the body is the ask in one line, the common answer is in place, Details opens one `KitRequestSheet`, and every answer shows a receipt; The card can be answered where it appears: in the conversation, and in the Inbox for any server without switching the active server (owner, B8); a permission can also be answered from its notification (AUTO-16). Work and team rows point to the card. | JN; TIA §1.2, §2 | missing: G9; reviewer |
| AUTO-18 | A missing capability is offered at the moment of need; Settings shows its state but is never the only way in; a screen shows at most one row or notice per capability; "Not now" folds the offer into one quiet row, offered again only when the context changes. | CAP §4 | `test/team_discover_test.dart` (AI Team); reviewer |
| AUTO-19 | There is one installer (phone setup v2) for every install, landing on one ready page; pre-flight (ABI, free space, RAM) runs before any download; every long job resumes after a kill, reboot or network return. | CAP §4; TIA §1.2 | `test/setup_preflight_test.dart`, `test/setup_scripts_test.dart`; missing: G41 |
| AUTO-20 | Deferred with P2 (owner, 2026-09-26): no unit adds "Ask the setup assistant", an assistant row, `embedded-config-change-card`, or `mcp-add-sheet`'s assistant option, and no row may lead to them; the TIA rows that mention the assistant are skipped until the owner approves P2 (§0.2). When P2 is approved: configuration jobs offer the assistant beside the form, and a change it proposes arrives as a change card applied only on approval. | PD (P2 "later"); CAP §4; TIA §1.3, §2 | reviewer |
| AUTO-21 | The heat guard pauses the phone's team at SEVERE or headroom ≥ 0.95, stops it at CRITICAL, resumes after two cool minutes, and is on by default. | CAP §2; PV | `test/thermal_guard_test.dart` |

---

## 12. Accessibility

| ID | Rule | Source | Gate |
|---|---|---|---|
| A11Y-1 | Every control has a semantic label; an icon alone is never enough. | PR §9; K2 §1.10 | `test/accessibility_guidelines_test.dart`; missing: G5 |
| A11Y-2 | Every screen and part reflows at 200 % text with nothing clipped and no overflow at the LAY-4 overflow sizes; critical flows also pass at `AppTheme.maxTextScale` (2.5) on 360×740; supporting lines wrap rather than truncate (A11Y-8). | PV; K2 G6 | `test/text_scale_overflow_test.dart` (partial: nine critical flows at 2.5), `test/app_text_scale_test.dart`; missing: G6 (kit parts), TEST-10 overflow pumps (screens) |
| A11Y-3 | A status change is announced exactly once, by its host's live region; rows and log lines are not live regions. | K2; PV | `test/work_tab_status_line_test.dart` (partial); missing: G9 |
| A11Y-4 | In each gallery and golden fixture, the semantics traversal order is top to bottom, then start to end by rect. | PV screen reader | missing: G5 |
| A11Y-5 | Every gesture has a visible twin (a button or a menu item) or, for row menus, a semantic custom action. | PR §6; VL §5; Appendix B | `test/gesture_audit_test.dart` (partial: the audit document); missing: G37 (swipe and menu twin), G14x |
| A11Y-6 | The critical flows (chat, busy composer, home shell, workspace, Activity, Settings, manage project, servers first run, permission sheet, form renderer) and every kit gallery pass `androidTapTargetGuideline`, `labeledTapTargetGuideline` and `textContrastGuideline` in dark and light. | K2 G5 | `test/accessibility_guidelines_test.dart`; missing: G5 |
| A11Y-7 | Each wave checkpoint record contains one TalkBack walk of the five top journeys. | PV a11y | reviewer (checkpoint); missing: G32 |
| A11Y-8 | Only kit parts clamp text scale, each clamp a named `KitTokens` constant with a reason (dock and rail labels, badges and counts, icons through `maxIconScale`); the top-bar title is never clamped below 2.0. No file outside the kit uses `TextScaler.linear`, `.clamp(maxScaleFactor:` or `TextScaler.noScaling`. Text may truncate only in a row title (one line below 1.3× text, two lines from 1.3×), a name or path in a list (middle ellipsis for paths) and a chip; every truncated text carries its full value in semantics and in a tooltip or its detail page. All other text wraps. | PV; K2 §8.2; `AppTheme.maxTextScale` | missing: G21 (clamp ratchet); reviewer |

Contrast, target size, colour-alone and glass fallbacks are in LOOK-8, LAY-9, STATE-9 and LOOK-29.

---

## 13. Security and privacy

| ID | Rule | Source | Gate |
|---|---|---|---|
| SEC-1 | Every URL the app did not author (server, form field, network response, Markdown or viewer link) opens through `openExternalLink` (`lib/ui/widgets/external_link.dart`); `launchUrl` appears only there and in launchers passed into it, each allowlisted with a reason. | AG Security | `test/external_link_test.dart`; missing: G2 |
| SEC-2 | Provider credentials, API keys and bearer tokens never reach logs, diagnostics, bug reports, notification copy, the clipboard, screenshots or test output. | AG Security | `test/app_diagnostics_test.dart`, `test/perf_trace_test.dart`; missing: G12 |
| SEC-3 | Secrets are entered only through the secret field: obscured with a reveal during entry, paste allowed, never prefilled, no suggestions, autocorrect or IME learning, excluded from diagnostics; once saved, the value is never shown again, only "Saved · Replace". | K2 §1.4, §4.10 | `test/kit/kit_secret_field_test.dart`; missing: G9 |
| SEC-4 | `KitDetailsFold` refuses a value the redactor matches, `KitLogPanel` lines pass through the redactor, and a command in `KitCodeBlock` uses placeholders (`<your key>`), never a real token. | K2 §1.8, §1.9, §1.11 | missing: G12 |
| SEC-5 | No real secret (provider key, token, the OpenCode 2 password) appears in a fixture, golden, log, test output or QA record; tests use fakes. | AG | missing: G29 |
| SEC-6 | No keystore and no populated `android/key.properties` is committed; the release keystore lives outside the repository, is not a symlink, and has no group or other permissions. | TO; release.sh | `scripts/release.sh`; missing: G25 |
| SEC-7 | `.claude/skills/` is never committed. | AG | `test/repository_hygiene_test.dart` (.gitignore); missing: G25 |
| SEC-8 | Remote scripts and downloads are pinned by SHA-256, manifests are HTTPS and public, and nothing pipes `curl` into a shell without verifying. | PV security | `test/setup_scripts_test.dart`, `test/shipped_download_urls_test.dart`; missing: G29 |
| SEC-9 | A risky switch states its scope and duration, shows an indicator while on, and can be turned off where it is shown. | PV security | reviewer |
| SEC-10 | A shell-run dialog says it skips approval rules; a public share shows "Stop sharing" beside its URL and names the upload host. | PV security | `test/release_blockers_test.dart` (partial) |
| SEC-11 | "Report a problem" is at most two taps from any error, shows a preview of exactly what will be sent and where, offers Copy and Share first, and needs no GitHub account. | PV help-feedback; TIA §1.2 | reviewer |
| SEC-12 | Secrets are obscured only through the kit: no `obscureText` outside `lib/ui/kit/`. | K2 §4.10 | missing: G29 |
| SEC-13 | Copy goes through `KitCopy.copy`. It redacts (`redact: true`, the default) for technical values, logs, diagnostics, notices' Copy details, report previews and `KitCodeBlock` (a block is often tool output or a command, so a key in it is masked on screen and in the copy alike; kit-polish 2026-09-27 matched this row to the code and G12). It copies verbatim (`redact: false`) for diffs, markdown and message text, which are the person's own content that masking would corrupt. A part chooses one and says which in its spec. | Coordinator decision 2026-09-27, after two Codex audits of KitRedact (docs/qa: codex audits) | `test/kit/kit_copy_test.dart` |

---

## 14. Performance, battery and reliability

| ID | Rule | Source | Gate |
|---|---|---|---|
| PERF-1 | Against the 1.0.44+50 baseline on the same emulator and fixture: cold start to a usable Work tab ≤ 3.4 s; open a project ≤ 1.6 s; open Settings ≤ 0.2 s; the app's share of send to first token ≤ 0.5 s; streaming and scrolling frame times within the G42 thresholds of the baseline (p90 40/36 ms); on a real arm64 phone p90 ≤ 17 ms. Checked at the wave checkpoint by the G42 protocol. | PV perf | `tool/qa/issue87/measure.sh`; missing: G42 |
| PERF-2 | Tool output and code blocks inside a list are capped at a fixed number of lines with "Open full output"; paging is bounded. | PV perf | `test/session_inventory_paging_test.dart` (partial); missing: G9 |
| PERF-3 | Nothing runs on the phone unannounced: every live process appears on "Running on this phone" with its cost and a safe stop, and background polling states its interval. | PV battery-heat | reviewer |
| PERF-4 | Live pages follow their output, poll only while visible, and never poll off screen. | PV automation; K2 §1.11 | missing: G9 |
| PERF-5 | After a force stop, low-memory kill or crash, the next open says so once in one line and brings back what was running. | PV reliability | `test/app_exit_recovery_test.dart` |
| PERF-6 | Diagnostics (errors, OCTRACE, exit reasons, thermal) are persisted, bounded and redacted, and survive a crash or force stop. | PV help-feedback | `test/app_diagnostics_test.dart` (redaction); reviewer |
| PERF-7 | Each wave checkpoint records the device recipes for force stop, reboot, airplane mode and a low-memory kill. | PV reliability | reviewer (checkpoint); missing: G32 |

---

## 15. Tests and goldens

| ID | Rule | Source | Gate |
|---|---|---|---|
| TEST-1 | Every change carries tests for its new behaviour, and tests assert what the person sees, what is sent or what is stored, not private widget types or call order (the source-scan ratchets are the deliberate exception). | AG Testing; PLAN §1 | reviewer |
| TEST-2 | Every fix is proven by a test that fails with the fix reverted (or on the base) with an assertion failure, not a compile error, and that failing output is saved as a `.txt` in the QA record. New code that fixes nothing needs no failing-first run. | MEM; QA | missing: G32 |
| TEST-3 | A fix that a merge could silently revert also gets a source-level guard test (see `test/connection_transport_factory_guard_test.dart`). | MEM | reviewer |
| TEST-4 | A test that reaches `ProfileStore.load` or `upsert` mocks the `plugins.it_nomads.com/flutter_secure_storage` channel (pattern: `test/offline_queue_test.dart`), until the mock is installed globally in `test/flutter_test_config.dart`. | AG Testing | missing: G36 |
| TEST-5 | A widget `Key` in `lib/` exists only because a test uses it; tests otherwise find widgets by visible text. A key string is kebab-case `<page id or part>-<element>[-<id>]` (`chat-older-history`, `kit-confirm-cancel`, `archived-session-${id}`), and a kit part's internal keys start with `kit-<part>-`. A rebuild keeps every existing key that `test/` or `tool/capture/` uses, on the element that now plays the same role; a key is renamed only together with its test, in the same commit. | AG Testing | missing: G36 |
| TEST-6 | Goldens are regenerated deliberately, one test file at a time (`--update-goldens <file>`); every changed PNG is opened and looked at before it is committed, and listed in the QA record with what changed and why. | golden headers; PLAN §6 | missing: G32 |
| TEST-7 | Every golden test file starts with the "Regenerate deliberately … look at every changed image before committing it" header. | golden headers | missing: G23 |
| TEST-8 | Goldens load the app's real fonts (`loadCaptureFonts` or `loadKitGalleryFonts`; Geist once VL is merged), and any render in Arabic also loads Noto Sans Arabic. | VL §2; fixtures.dart | missing: G23 |
| TEST-9 | A kit part's galleries render at device pixel ratio 3.0: every declared state (KIT-12) in dark and light at 412×915; the default state in dark and light at the other LAY-4 gallery sizes (360×800, 915×412, 800×1280, 1280×800, 1600×1000); the default state at text 2.0 and in Arabic right-to-left at 412×915 and 1280×800. The G6 overflow matrix (no images) covers every other state and size. | VL §7 (owner); K2 G4, §8.4 | missing: G4, G23 |
| TEST-10 | A migrated screen is in `_migrated` (or `_migratedClasses`), from which an entry leaves only when its file is deleted, its patterns then moving to `_retired`. It has zero forbidden patterns and these goldens at DPR 1: one per STATE-20 state at 412×915 in dark and light; for the loaded state also `_ar_dark` and `_text2_dark` at 412×915, `_1280x800_dark`, and `_800x1280_dark` for pages whose layout changes on medium. Its widget tests pump the loaded state at every LAY-4 overflow size with no exception. | DS §8; K2 G3, §8; PLAN §1 (owner: adaptive) | `test/design_standard_test.dart` (partial: 412×915 dark and light); missing: G3x, G31 |
| TEST-11 | Golden fixtures are deterministic: nothing depends on the wall clock, the network or random values. | chat_states_golden_test | reviewer |
| TEST-12 | Golden-failure artefacts (`**/failures/`) are never committed. | tool/qa/README | missing: G25 |
| TEST-13 | Goldens are regenerated only on the maintainer's PC (not the phone container), with the pinned Flutter. | PLAN §2 | reviewer |
| TEST-14 | Each drawn scene has dark and light goldens of its finished frame. | DS §10 | `test/kit_states_scenes_test.dart`; missing: G4 |
| TEST-15 | Each kit part has `test/kit/<part>_test.dart` with its K2 G9 contracts, is in the reduced-motion test (G8), the keyboard test if it is modal or a row (G14), the gallery accessibility check (G5) and the overflow matrix (G6). | K2 §7 | missing: G4 |
| TEST-16 | Every file under `lib/ui/screens/` has a page or element in `docs/design/ui-ledger/ledger.json`. | ledger | `test/ui_ledger_coverage_test.dart` |
| TEST-17 | The screen census lives under `tool/capture/`, guards that each shot shows the page it names, has at most four states per page, never changes `lib/` to render a page, and keeps one manifest entry per ledger page. Units never re-render or commit `docs/qa/screen-census/**`; a unit that changes a page's visible anchor text updates only that page's guard in `tool/capture/census/` (a PROC-13 registry). The coordinator re-renders the census once per wave checkpoint and commits it with the checkpoint record. | census README | missing: G35 |
| TEST-18 | `test/kit_ratchet_flutter_widgets.json` is regenerated with `tool/kit/flutter_widget_names.dart` whenever the pinned Flutter revision changes. | kit_ratchet_test | missing: G25 |
| TEST-19 | When an existing test fails after a change: (1) if it asserts how something looks or is built (a widget type, a Material part, old copy that the glossary or VL replaced, a silent absence that STATE-12 replaces), change its finder or expectation to the new visible behaviour in the same commit and list each changed expectation in the QA record with the rule id that required it; (2) if it asserts behaviour the unit's finish line does not change (what is sent, stored or reachable), the code is wrong: fix the code, not the test; (3) never delete a test, add `skip:`, or widen a matcher (`findsWidgets` for `findsOneWidget`) to get a pass. When unsure, treat it as (2) and report it (PROC-32). Only the unit's own tests are changed (PROC-10). | MEM (verify fixes with a failing test); revamp.workflow.js | reviewer |
| TEST-20 | Golden names: `<module>_<page>_<state>[_ar][_text2][_<W>x<H>]_<dark\|light>.png` for screens and `kit_<part>_<state>[_ar][_text2][_<W>x<H>]_<dark\|light>.png` for kit parts, with the size left out only for 412×915. No unit adds more than 60 PNGs or 8 MB of goldens; more needs the coordinator's approval in the record. | PLAN §2 (one machine) | missing: G23, G32 |

---

## 16. Evidence

### 16.1 Rules

| ID | Rule | Source | Gate |
|---|---|---|---|
| EVID-1 | Every unit leaves `docs/qa/revamp-<unit id>-<YYYY-MM-DD>/README.md`, where the date is the UTC date of the unit's first build commit and the unit id is used verbatim (letters, digits, `.`, `-`); the fix round and any retry reuse the same folder. Every wave checkpoint leaves `docs/qa/revamp-wave-<n>-<YYYY-MM-DD>/README.md`. | MEM audit trail; PLAN §5 | missing: G32 |
| EVID-2 | The README has, in order: Scope, Builds, Devices, Runs, Evidence, How to reproduce, NOT proven, followed by the State table. | QA | missing: G32 |
| EVID-3 | Builds names the branch, the base commit, and the last code commit before the record commit (`code head`), plus an APK's SHA-256 or "No APK". The record commit follows `code head`; after it, only the QA folder and fix-ups listed in the record change. | QA | missing: G32 |
| EVID-4 | Devices names the model or AVD, Android version and ABI, or says "None: tests, goldens and renders only". | QA | missing: G32 |
| EVID-5 | Runs is a numbered table of expected against actual, each row PASS or FAIL. | QA | missing: G32 |
| EVID-6 | Evidence files sit next to the README, every file it names exists, and videos are linked, never committed. | QA | missing: G32 |
| EVID-7 | How to reproduce gives exact commands, with the pinned Flutter path and any environment variables. | QA | missing: G32 |
| EVID-8 | NOT proven is never empty; a record without a device run says so. | QA; MEM | missing: G32 |
| EVID-9 | The integrator adds each merged record as a row in `docs/qa/README.md` (units do not edit the index, to avoid conflicts). | QA; MEM | missing: G32 |
| EVID-10 | A UI change's record has before and after images (before: the base revision's golden or census PNG for the same page and state, `git show <base>:<path> > before-<page>-<state>.png`, or "no before render" with the page id; after: the unit's regenerated golden, copied as `after-<page>-<state>.png`; device images belong only to the checkpoint record), accessibility notes (labels, targets, text scale), privacy and security notes when credentials, stored data, external links or notifications change, and migration notes when a stored format changes. | AG Workflow; CON | missing: G32 |
| EVID-11 | For each page in the unit's `pages`, the record lists every `actionsMissing`, `statesMissing` and `couldBeAutomatic` item of its map record with "done: <test or golden>" or "deferred to <unit id>" (or "no owner"). | PLAN §5 step 2; MAP | missing: G32 |
| EVID-12 | For each changed golden that has an approved VL canvas render, the record names the approved render path beside the new golden and lists the visible differences, or "none". | PLAN §5 step 2; VL | missing: G32 |

### 16.2 Template

Copy this into `docs/qa/revamp-<unit id>-<YYYY-MM-DD>/README.md` and fill every section. Write "n/a: <reason>" rather than leaving a section out.

````markdown
# revamp-<unit id>: <unit title> (<YYYY-MM-DD>)

## 1. Scope

- Unit: `<unit id>` (wave <n>, <kind>). Finish line: <one sentence>. Non-goal: <one sentence>.
- Files changed: <list, or "see git diff base...head">.
- Pages (map ids): <ids>.
- Specs followed: STANDARDS.md rules <ids>; kit-v2 §<n>; visual-language §<n>.
- Contract problems (PROC-20): <none | {rule or doc §, what it says, why wrong, evidence, proposed text, blocks}>.
- New kit parts (KIT-3): <none | list>.
- Map items (EVID-11): <page: item → done: <test or golden> | deferred to <unit id>>.
- States per page (STATE-20): <page: loading, empty, error, not answering, loaded, … → test or golden>.
- Deferred states (STATE-21): <none | state → needs <data>, owner <slice id | no owner>>.

## 2. Builds

- Branch `revamp/<unit id>`, base `<hash>`, code head `<hash>`.
- No APK (unit agents do not build).

## 3. Devices

None: tests, goldens and renders only. Device proof is in the wave <n> checkpoint record.

## 4. Runs

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Fixes only: the fix's test on the base (without the fix) | fails with an assertion | failed: see `failing-first.txt` | PASS |
| 2 | `test/<file>_test.dart` | passes | 12 passed | PASS |
| 3 | Ratchet, design-standard, l10n, glossary, ledger tests | pass | passed | PASS |
| 4 | `flutter analyze lib test` | no errors; no new issues in changed paths | no issues | PASS |

## 5. Evidence

- `failing-first.txt`: output of step 1 (fixes only).
- Rule evidence (PROC-31):

  | Rule | Test (`file` + `--plain-name`) or golden | Output |
  |---|---|---|
  | KIT-17 | `test/kit/kit_sheet_test.dart` "actions stay pinned at 2.0 text" | `run-2.txt` |

- Changed test expectations (TEST-19): <none | test → old → new, rule id>.
- Goldens changed (each opened and looked at):
  - `test/goldens/<name>_dark.png`: <what changed and why>; approved render `<path>`: <differences | none> (EVID-12).
- Before and after: `before-<page>-<state>.png` (from base `<path>`), `after-<page>-<state>.png` (EVID-10).
- Accessibility: <labels added, target sizes, 200 % text and Arabic checked at …>.
- Privacy and security: <n/a: no credentials, stored data, links or notifications changed | notes>.
- Migration: <n/a: no stored format changed | notes>.

## 6. How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F test -j 1 test/<file>_test.dart test/kit_ratchet_test.dart test/design_standard_test.dart
$F analyze lib test
```

## 7. NOT proven

- Not run on a device or emulator.
- <anything else these runs do not show>.

## State

| State | Yes/No | Where |
|---|---|---|
| Implemented | Yes (or "partial" when blocked, PROC-32) | `revamp/<unit id>` |
| Enabled | <Yes / No: behind …> | |
| Verified | <tests and goldens only / on device> | this record |
| Committed | Yes | code head `<hash>` |
| Deployed | No | |
| Released | No | |
````

---

## 17. Reviewer checklist

Answer every question the unit's diff touches with yes or no. "n/a" is allowed only when the unit touches nothing the rule covers. Any "no" is a finding: report it with its rule id (`ruleId`) and `kind: defect`, or `kind: contract` when the rule itself is wrong (PROC-20). A runtime question is answered only from the record's Rule evidence (PROC-31). Reviewers are read-only (PROC-27).

**Process, git and ownership**

- PROC-1: Were all recorded results produced with the pinned Flutter 3.47.1?
- PROC-2: Does the saved analyze output over `lib` and `test` show no errors, and no new issues in changed paths?
- PROC-3: Were no analyzer suppressions or excludes added?
- PROC-4: Did the builder run only affected tests, with `-j 1`?
- PROC-5: Are all changed Dart files formatted, and was gen-l10n run once at the end (and its output left uncommitted, PROC-13)?
- PROC-6: Does any full-suite claim cite one unchanged candidate with every chunk passed?
- PROC-7: Did the builder avoid Gradle, emulators, adb, the phone and pushes?
- PROC-8: Were processes killed only by exact PID?
- PROC-9: Is the work on `revamp/<unit id>` in its own worktree, with no edits to another checkout?
- PROC-10: Is every changed file in the write set, a new kit file, the unit's own tests and goldens (as PROC-10 defines them), its record, or a PROC-13 file or registry?
- PROC-11: Were single-owner units left alone unless this unit owns them?
- PROC-12: Were `main.dart` and `connection.dart` left to the coordinator?
- PROC-13: Were ARB keys added (or reworded only where every reference is in the write set), generated l10n left uncommitted, one export and row added to `kit.dart`, baselines only lowered, registries only appended, and only the unit's own goldens regenerated?
- PROC-14: Does every commit have `[skip ci]`, a what-and-why body and the trailers?
- PROC-15: Was nothing pushed, opened as a PR, run in CI, signed or released?
- PROC-16: Were machine-heavy checks run under the lock (integrator)?
- PROC-17: For an adapter, was the contract, authentication and credential use confirmed first, and a blocker logged rather than worked around?
- PROC-19: Does the unit have a finish line, non-goal, read and write sets, acceptance and checks, and was the non-goal respected?
- PROC-20: Were frozen contracts followed, and each problem recorded with evidence and a proposed text, the item left at today's behaviour?
- PROC-21: Does the report keep the six shipping states apart, without line or test counts as value?
- PROC-22: Was nothing merged anywhere but `feat/phone-setup-v2` by the integrator?
- PROC-23: Were releases, if any, made only through the release scripts?
- PROC-24: Was any APK for the owner's phone signer-checked?
- PROC-25: Is `packages/opencode_sdk/` untouched, or regenerated and checked?
- PROC-26: Were no debug builds and no Shorebird commands used for everyday work?
- PROC-27: Did the reviewer stay read-only?
- PROC-28: Were docs-only changes link-checked rather than tested?
- PROC-29: Are CI or artifact URLs reported only if they exist?
- PROC-30: Were live checks run only on local test servers, with no password printed?
- PROC-31: Is every runtime answer in this review backed by a named test or golden in the record?
- PROC-32: If the unit was blocked, did it stop, record its blockers and leave outside files untouched?
- MAP-1: Was each page handled by its map proposal?
- NAME-1: Are new files, classes, tests and goldens named and placed by the rule?

**Architecture**

- ARCH-1: Does no changed `lib/ui` file import `lib/api/` or `lib/api2/` more than before, and no new file at all?
- ARCH-2: Are features gated on capability flags, not on `ServerFlavor`?
- ARCH-3: Is OpenCode 2 state reconciled by refetch after reconnect?
- ARCH-4: Does background code handle the lifetime cap and `onTimeout`?
- ARCH-5: Does UI use services only through their public API?
- ARCH-6: Was `lib/ui/kit/chat/` edited only by the chat library's current owner?
- ARCH-7: Is plain HTTP or `ws://` limited to loopback and, through the two validators, Tailscale addresses?
- ARCH-8: Are status words produced by one mapping per entity, not by screens?
- ARCH-9: Do Settings search and the palette share one index?
- ARCH-10: Are notifications posted only through `NotificationRouter` (from slice-P6.7), and no new notification path added before it?
- ARCH-11: Are tests and goldens written for Android, with no iOS-, web- or desktop-OS-only path added?

**Kit**

- KIT-1: Does every rebuilt file use only allowlisted framework widgets outside the kit?
- KIT-2: Are all modals and toasts opened through kit functions?
- KIT-3: Was each missing drawing need built as a kit part (after checking for an existing one), with page-only compositions kept private in their file?
- KIT-4: Did every ratchet count stay the same or go down?
- KIT-5: Does every new allowlist entry have a real reason?
- KIT-6: Do scrollbars come only from `KitScrollbar` (or, before it lands, only from `desktop_interaction.dart`)?
- KIT-7: If KitPageRoute landed, were the route widgets removed from the allowlist?
- KIT-8: Are actions passed as `KitAction` values, never styled by callers?
- KIT-9: Do kit parts read every size and colour from tokens and roles?
- KIT-10: Are `…Key` parameters optional, and passed only when a test uses them?
- KIT-11: Does every modal part open through a `showKit…` Future (except `showKitUndo`)?
- KIT-12: Does the part declare its states and render each declared state in its gallery?
- KIT-13: Does the part meet its phone, tablet and PC behaviour now?
- KIT-14: Does `kit.dart` have a row for every export?
- KIT-15: Does each container do its one job?
- KIT-16: Does a confirm or choice inside a sheet replace the content without a new route?
- KIT-17: Does the sheet body scroll inside the frame, with actions pinned at 200 % and with the keyboard?
- KIT-18: Is `dismissible: false` used only during an irreversible step, and said?
- KIT-19: Is the sheet named by its title, focused on it, with a labelled Close and a Dismiss handle?
- KIT-20: Is every input a `KitField` with a visible label, and search a `KitSearchField`?
- KIT-21: Do counters, helpers, errors and validation behave as specified?
- KIT-22: Is every icon-only control a labelled `KitIconButton`?
- KIT-23: Is copying done only through `KitCopy.copy`, with no snackbar and no secret copied?
- KIT-24: Does `KitSegmented` have 2–4 word labels, a check and a stacked `KitChoiceRow` fallback?
- KIT-25: Are dropdowns picker rows, and single choices acted on at tap?
- KIT-26: Are ListTile-family widgets replaced by kit rows?
- KIT-27: Are rows `KitRow`s in panels with the specified lines and trailing parts?
- KIT-28: Are there no per-row ⋮ buttons, with the menu on long-press, right-click and semantics?
- KIT-29: Is every swipe an Undo-or-neither act with a menu twin, and no confirm-first act on a swipe?
- KIT-30: Do risky switches show scope and an indicator, and never "Always allow" as primary?
- KIT-31: Is process output one follow-mode `KitLogPanel` per source, folded, polling only when visible?
- KIT-32: Are values the app did not write isolated LTR and copyable through kit parts?
- KIT-33: Is there one Details fold per page, last and collapsed?
- KIT-34: Are snackbars used only for Undo through `showKitUndo`?
- KIT-35: Is exactly one status line visible per window, with screens feeding the shell's line and conditions in the stated order?
- KIT-36: Is there one top bar, with the right number of actions for the window?
- KIT-37: Is the cost stated before every install or turn-on primary?
- KIT-38: Were no new uses of old wrappers or `Product*` states added, and were they deleted by the unit that brought their count to zero?
- KIT-39: Does the part match the spec where the spec requires more than the code had, except where this file, VL or the owner override K2?
- KIT-40: Is there one secret input in the kit after `KitField` lands?
- KIT-41: Do chat parts cite and follow STATE-16's turn model?
- KIT-42: Does `KitSurface` have only the four solid levels, with glass only through `KitGlass`?
- KIT-43: Is the kit change additive, with no `@Deprecated`, a forwarding old API and a retire note?
- KIT-44: Does any baseline rise come with a `ratchet-tighten:` line from the coordinator or the named unit?

**Look**

- LOOK-1: Does every colour come from a role, with no colour literals outside theme files?
- LOOK-2: Does code outside the kit avoid `colorScheme` and `textTheme`?
- LOOK-3: Is `hairline` used without extra alpha?
- LOOK-4: Is amber used only for Needs-you, and only by kit parts?
- LOOK-5: Is red used only for acts that lose data or end running work, with dangerFill only on a destructive, stop or discard confirm, and never for failures, and is Stop the one red word of a running chat?
- LOOK-6: Is the accent used only on the listed places, with done states in `success`?
- LOOK-7: Does every theme, packs and custom, keep each role's meaning and hue family and pass the contrast guard?
- LOOK-8: Are all contrast minima met in both modes?
- LOOK-9: Are ambient fields only the theme's, with no screen gradients?
- LOOK-39: Does every offered accent keep its distance from attention and danger?
- LOOK-10: Is the type Geist and Geist Mono only?
- LOOK-11: Does Arabic use the system face with zero letter spacing?
- LOOK-12: Do screens name KitText roles and never sizes?
- LOOK-13: Are all font sizes integers?
- LOOK-14: Is text opaque at rest (no alpha, no `Opacity` wrapper), disabled text `text3`, with no shadows or glow?
- LOOK-15: Are section labels sentence case and untracked?
- LOOK-16: Is mono used only for technical values, laid out LTR?
- LOOK-17: Does the top bar use headline, with no off-table sizes?
- LOOK-18: Do aligned numbers use tabular figures within their role?
- LOOK-19: Do all radii come from tokens with the specified values?
- LOOK-20: Is there no shadow or elevation except the needs-you ring and the glass shadow?
- LOOK-21: Is every stroke one physical pixel and every focus ring two, from `KitTokens`, with the two-line rim?
- LOOK-22: Is blur used only behind glass?
- LOOK-23: Do buttons use the four looks, with no tonal pills?
- LOOK-24: Is Needs-you the answer card in the conversation and a pointing `KitNeedsYou` row elsewhere, drawn only by the kit?
- LOOK-25: Do sheets have grabber, icon tile, start title, consequence panel and the right button layout, matching the approved render (EVID-12)?
- LOOK-26: Do the transcript and composer match VL §5 and the approved render (EVID-12)?
- LOOK-27: Is glass used only on the floating navigation layer (rail included), from the allowed files, and nowhere in a settings page?
- LOOK-28: Is glass never on glass, sharing one backdrop?
- LOOK-29: Does glass fall back correctly in each mode?
- LOOK-30: Does glass holding text dim what is behind and keep 4.5:1?
- LOOK-31: Was a new glass surface measured at the checkpoint by the G42 protocol?
- LOOK-32: Is there no metal or chrome?
- LOOK-33: Are icons designed at 20, 22 or 24 px from one family, one glyph per verb, scaled only within `maxIconScale`?
- LOOK-34: Are icon tiles 30 px and the nav bar 60 px, radius 22?
- LOOK-35: Are images decoded at DPR with high quality, and illustrations vector?
- LOOK-36: Do drawings follow the one style?
- LOOK-37: Do drawings appear only at moments, within size limits?
- LOOK-38: Are drawings excluded from semantics unless meaningful?

**Layout and adaptive**

- LAY-1: Are window classes read only from `KitLayout`?
- LAY-2: Does the kit compare widths only in `kit_layout.dart`, reading every layout width by its `KitLayout` name?
- LAY-3: Does a short window keep the compact layout?
- LAY-4: Does it pass at every LAY-4 overflow size?
- LAY-5: Are the panes 296 / 700 from expanded and 340 on large?
- LAY-6: Is the 16 dp gutter from `KitScreen`, and does the last content clear the dock and keyboard?
- LAY-7: Does spacing come only from named tokens?
- LAY-8: Is layout directional throughout, with Back at the start, Close at the end and the right glyphs mirrored?
- LAY-9: Are targets 48 dp without overlap, destructive targets 8 dp apart, and buttons 50 dp?
- LAY-10: Does everything work from the keyboard with visible focus?
- LAY-11: Do right-click and long-press open the same menu?
- LAY-12: Is there at most one primary, placed low on compact?
- LAY-13: Are action blocks in the specified order and layout?
- LAY-14: Is no destructive action beside a frequent one?
- LAY-15: Is the dock exactly Work · Inbox · Project · Settings?
- LAY-16: Did the checkpoint prove the pages on phone, tablet and large-window AVDs?

**Motion and haptics**

- MOT-1: Do all animation durations and curves come from `KitMotion`, and waits from its named constants?
- MOT-2: Do transitions only slide or cross-fade?
- MOT-3: Is the one kit page transition used?
- MOT-4: Is pull to refresh `KitRefresh`?
- MOT-5: Is there no layout animation outside `KitReveal` and the button spinner?
- MOT-6: Do loops run only while waiting, at most one per screen?
- MOT-7: Does everything settle in one pump under reduced motion?
- MOT-8: Is reduced motion read only by `kit_motion.dart`?
- MOT-9: Do scenes take time only from `KitIllustration`?
- MOT-10: Is `flutter_animate` absent?
- MOT-11: Is vibration only send, done or commit through `KitHaptics`?
- MOT-12: Are Effects read only through the kit?
- MOT-13: Do effects drop to Calm and Effects-off glass under battery saver and SEVERE heat?
- MOT-14: Did new animation meet the G42 thresholds at the checkpoint?

**Copy and localisation**

- COPY-1: Is every new string in both ARB files, with no hardcoded strings added, including those passed to kit parts?
- COPY-2: Is server text shown untranslated?
- COPY-3: Do new keys use the module or `kit` prefix and follow the description, placeholder and plural rules?
- COPY-4: Is generated l10n output untouched by hand?
- COPY-5: Was Arabic edited directly in `app_ar.arb`, without the assembler?
- COPY-6: Are the glossary nouns used?
- COPY-7: Are the glossary verbs used?
- COPY-8: Is every button a verb, never OK or Continue?
- COPY-9: Do confirmations have a question title, a two-word label, the right cancel word and a short body?
- COPY-10: Are the app's fixed title words ≤ 4 in sentence case (placeholders one word), data titles shown whole, and bodies ≤ 2 sentences?
- COPY-11: Do technical values and engine words appear only in the technical parts, typed fields or technical pages?
- COPY-12: Are product names used only where the person chooses?
- COPY-13: Do AI Team screens use the team words?
- COPY-14: Do server errors go through `agentErrorWords`?
- COPY-15: Are permission titles plain actions?
- COPY-16: Are there no refresh buttons on primary tabs and live pages, and at most one elsewhere?
- COPY-17: Do words never contradict the state?
- COPY-18: Does each action keep one ARB label and one `AppIcons` glyph?
- COPY-19: Are terms explained in place?
- COPY-20: Are nudges shown once, each with a concrete benefit?
- COPY-21: Is there no simple or expert mode?
- COPY-22: Does Arabic have every key with the same placeholders?
- COPY-23: Is Arabic written MSA with full plural forms?
- COPY-24: Do technical tokens stay Latin, marked with «»?
- COPY-25: Is the fixed Arabic vocabulary used?
- COPY-26: Are there no bidi control characters in Arabic values?
- COPY-27: Is Arabic free of engine words where English is?
- COPY-28: Does every error cause have its own Arabic wording?
- COPY-29: Does the language choice show honest coverage?
- COPY-30: Are placeholders isolated with `KitBidi`, with no bidi literals outside the kit?

**States and honesty**

- STATE-1: Is every non-normal moment a `KitStateView` or `KitNotice` with the fixed slots?
- STATE-2: Do empty states say what will be there and offer a first step?
- STATE-3: Do error states offer Copy details and Report, or a fix for network errors?
- STATE-4: Is there one loading bar, and skeleton rows for lists?
- STATE-5: Does every wait escalate after 8 s with a way out?
- STATE-6: Do long jobs give an estimate and real progress?
- STATE-7: Do buttons avoid showing lasting status?
- STATE-8: Does every disabled control show a visible reason?
- STATE-9: Is every state a word plus a mark?
- STATE-10: Does every write show its receipt, with the 8 s rule?
- STATE-11: Is partial data labelled, and checklist marks taken from the engine?
- STATE-12: Does a missing capability explain itself instead of vanishing or showing a dead row?
- STATE-13: Does every "do X first" carry the button for X?
- STATE-14: Is there one status line in the shell, in the KIT-35 order?
- STATE-15: Do recovered failures stay folded and unreddened?
- STATE-16: Does the transcript follow the turn model?
- STATE-17: Are waiting sends merged into one queued bubble with one action line, never in the Inbox?
- STATE-18: Do offline values show their age?
- STATE-19: Are updates the lowest-priority condition of the one status line?
- STATE-20: Does each page have the states for its kind, each with a test?
- STATE-21: Were only detectable missing states built, the rest listed as deferred with an owner?

**Data safety**

- DATA-1: Do the composer, multiline input, dictation, queue and attachments survive a crash, and other input survive through the discard question?
- DATA-2: Does every multiline sheet input use `KitDraft`?
- DATA-3: Do dirty-only sheets ask in place, and clean ones close?
- DATA-4: Are per-profile keys `oc.<what>.<profileId>`?
- DATA-5: Is every new shared blob removed on profile deletion, with a test?
- DATA-6: Are choices remembered per server?
- DATA-7: Are queued prompts listed before removing a server, and evictions reported?
- DATA-8: Does the screen say what survives a delete?
- DATA-9: Is a stored-format change migrated once, silently, with a test?
- DATA-10: Can an update not lose data?
- DATA-11: Does each act get exactly one of act, Undo or confirm, the same from every door, with Undo only where an inverse or deferred commit exists?
- DATA-12: Do heavy deletes need the typed name?
- DATA-13: Do confirmations list what else is removed, with counts?
- DATA-14: Does a failed confirmed act stay open with Try again?

**Automation and Needs-you**

- AUTO-1: Is technical detail kept out of primary flows?
- AUTO-2: Does each job land on its one page from every door?
- AUTO-3: Do glance pages show one line per item?
- AUTO-4: Is every automatic action announced and logged in While you were away?
- AUTO-5: Does each automation have a test that fails when reverted?
- AUTO-6: Are manual levers kept in overflow, never primary?
- AUTO-7: Are the never-automated acts left manual?
- AUTO-8: Are one-option pickers skipped and defaults picked?
- AUTO-9: Is Needs-you raised only for the three allowed reasons?
- AUTO-10: Does each Needs-you item say why and what happens if ignored?
- AUTO-11: Is each request counted once, oldest first, naming its server?
- AUTO-12: Does an answer anywhere clear it everywhere, including the notification?
- AUTO-13: Are quiet events kept out of Needs-you?
- AUTO-14: Are Done notifications opt-in with the right defaults?
- AUTO-15: Does a waiting request show "Waiting for you"?
- AUTO-16: Does a notification land on the card, or say it was already answered?
- AUTO-17: Do requests use the card family, answered only on the card?
- AUTO-18: Are capabilities offered at the moment of need, once, without nagging?
- AUTO-19: Does every install go through setup v2 with pre-flight and resume?
- AUTO-20: Was nothing of the deferred setup assistant built or linked?
- AUTO-21: Does the heat guard pause, stop and resume as specified?

**Accessibility**

- A11Y-1: Does every control have a semantic label?
- A11Y-2: Does everything reflow at 200 % (and critical flows at 2.5×) without clipping?
- A11Y-3: Is each status change announced once?
- A11Y-4: Is the semantics order top to bottom, then start to end, in the galleries and fixtures?
- A11Y-5: Does every gesture have a visible or semantic twin?
- A11Y-6: Do the critical flows and galleries pass the three guidelines in both modes?
- A11Y-7: Does the checkpoint record include the TalkBack walk?
- A11Y-8: Are text-scale clamps only in kit tokens, and truncation only where allowed, with the full value reachable?

**Security and privacy**

- SEC-1: Do all non-authored URLs go through `openExternalLink`?
- SEC-2: Do no credentials reach logs, reports, notifications, clipboard or tests?
- SEC-3: Are secrets entered only through the secret field and never shown after saving?
- SEC-4: Do technical parts redact and use placeholders?
- SEC-5: Are there no real secrets in fixtures, goldens or records?
- SEC-6: Are no keystores or key properties committed?
- SEC-7: Is `.claude/skills/` not committed?
- SEC-8: Are downloads pinned by SHA-256 over HTTPS?
- SEC-9: Do risky switches state scope and duration?
- SEC-10: Do shell-run and share dialogs say what they skip and where data goes?
- SEC-11: Is Report a problem two taps away, previewed, with Copy and Share first?
- SEC-12: Is `obscureText` used only in the kit?

**Performance and reliability**

- PERF-1: Did the checkpoint meet the performance budgets?
- PERF-2: Are long outputs in lists capped with "Open full output"?
- PERF-3: Does every live process appear with its cost and a stop?
- PERF-4: Do live pages poll only while visible?
- PERF-5: Does the app explain and restore after a kill?
- PERF-6: Do diagnostics survive a crash, bounded and redacted?
- PERF-7: Did the checkpoint record the four device recipes?

**Tests and goldens**

- TEST-1: Does new behaviour have behaviour tests?
- TEST-2: Does each fix have a failing-first test with saved assertion output?
- TEST-3: Does a merge-revertible fix have a source guard?
- TEST-4: Do ProfileStore tests mock secure storage?
- TEST-5: Is every new Key used by a test and named by the pattern, with existing test keys kept?
- TEST-6: Was every changed golden looked at and listed?
- TEST-7: Do golden files carry the regenerate header?
- TEST-8: Do goldens load the real fonts, plus Noto for Arabic?
- TEST-9: Do galleries cover every declared state, the default at every gallery size, text 2.0 and Arabic, at DPR 3?
- TEST-10: Is each migrated screen in `_migrated` with its state, Arabic, text 2.0 and adaptive goldens and its overflow pumps?
- TEST-11: Are golden fixtures deterministic?
- TEST-12: Are no failure artefacts committed?
- TEST-13: Were goldens rendered on this machine with the pinned Flutter?
- TEST-14: Does each scene have finished-frame goldens?
- TEST-15: Does each kit part have its contract, motion, keyboard, accessibility and overflow tests?
- TEST-16: Does every screen file have a ledger entry?
- TEST-17: Were census renders left to the coordinator, with only the unit's own guards changed?
- TEST-18: Was the widget catalogue regenerated if the Flutter pin changed?
- TEST-19: Were broken existing tests handled by the three cases, with none deleted, skipped or widened?
- TEST-20: Are golden names by the pattern and within the budget?

**Evidence**

- EVID-1: Is the record at the dated path (date of the first build commit)?
- EVID-2: Does it have the seven sections in order plus the State table?
- EVID-3: Does Builds give branch, base, code head and APK status?
- EVID-4: Does Devices say what ran, or "None"?
- EVID-5: Is Runs a numbered PASS/FAIL table?
- EVID-6: Do all named evidence files exist, with no committed videos?
- EVID-7: Are the reproduce commands exact?
- EVID-8: Is NOT proven filled in honestly?
- EVID-9: Did the integrator index the record?
- EVID-10: Are before (from the base) and after images, accessibility, privacy and migration notes present where needed?
- EVID-11: Is every missing map item of the unit's pages done or deferred with an owner?
- EVID-12: Is each changed golden compared with its approved render?

---

## 18. Gates

### 18.1 Rule to gate

Existing gates are marked **exists**; everything else must be built. "When" says when it has to exist: **W1** before wave 1 starts, **part** with the kit part it checks (built by that part's unit), **W3** by the programme slice that introduces the feature, **CP** at the first wave checkpoint.

| Gate | Test or script | Status | When | Rules |
|---|---|---|---|---|
| G1 | `test/kit_ratchet_test.dart` 'G1' | exists | – | KIT-2, KIT-15, KIT-33, KIT-34, KIT-38 |
| G2 | `test/kit_ratchet_test.dart` 'G2' | missing | W1 | KIT-23, KIT-38, KIT-43, MOT-1, MOT-5, MOT-11, SEC-1, STATE-1 |
| G3x | `test/design_standard_test.dart` (extended) | partial | W1 | TEST-10 |
| G4 | `test/kit/kit_manifest_test.dart` | missing | W1 | KIT-3, KIT-10–KIT-14, KIT-27, KIT-32, LAY-4, LAY-13, LOOK-23, NAME-1, TEST-9, TEST-14, TEST-15 |
| G5 | `test/goldens/kit/kit_gallery.dart` (guidelines and reading order in every shot) | missing | W1 | A11Y-1, A11Y-4, A11Y-6, LAY-9 |
| G6 | `test/text_scale_overflow_test.dart` (kit matrix) | missing | W1 | A11Y-2, LAY-4, KIT-24 |
| G7 | `test/kit_ratchet_test.dart` 'G7' | missing | W1 | LAY-8, COPY-30 |
| G8 / G8x | `test/kit_motion_test.dart` (manifest-driven) | partial | W1 | MOT-7 |
| G9 / G9x | `test/kit/<part>_test.dart` | partial | part | KIT-16, KIT-17, KIT-19, KIT-21, KIT-23, KIT-25, KIT-29, KIT-31, KIT-34, STATE-3, STATE-5, STATE-10, STATE-11, SEC-3, A11Y-3, PERF-2, PERF-4, AUTO-17, LOOK-5 |
| G10 | `test/kit/kit_draft_test.dart`, `test/profile_deletion_test.dart` | exists | – | DATA-1, DATA-3, DATA-4 |
| G11 | `test/ui_glossary_test.dart` 'G11' | missing | W1 | COPY-9, COPY-11, COPY-17 |
| G12 | `test/redaction_test.dart` | missing | part (KitLogPanel, KitDetailsFold, KitCodeBlock) | SEC-2, SEC-4 |
| G13 | `tool/ux/check_kit_map.py` | missing | W1 | STATE-12 |
| G14 / G14x | `test/kit/kit_keyboard_test.dart` (manifest-driven) | partial | part | LAY-10, LAY-11, KIT-28, A11Y-5 |
| G15 | `test/kit_ratchet_test.dart` 'G15' | exists | – | LAY-1 |
| G15x | `test/kit_ratchet_test.dart` 'G15 inside the kit' | missing | W1 (after §0.5 step 2) | LAY-2 |
| G16 | `test/kit_ratchet_test.dart` 'G16' | exists | – | KIT-1, KIT-3, KIT-6, KIT-8, KIT-17, KIT-20, KIT-25–KIT-27, MOT-4, STATE-4 |
| G17 | `test/kit_ratchet_test.dart` 'G17 colour' | missing | W1 | LOOK-1–LOOK-4, LOOK-6, LOOK-24 |
| G18 | `test/theme_roles_test.dart` (extended) | partial (arrives with the VL merge: pack contrast floors, whole role set, custom accent and ground, whole-pixel sizes, Geist faces) | W1 | LOOK-4, LOOK-7–LOOK-9, LOOK-20, LOOK-21, LOOK-39 |
| G19 | `test/kit/kit_text_test.dart` | missing | W1 | LOOK-10–LOOK-17 |
| G20 | `test/kit/kit_tokens_test.dart` (extended) | partial | W1 | LOOK-19, LOOK-33, LOOK-34, LAY-2, LAY-5, LAY-7, LAY-9 |
| G21 | `test/kit_ratchet_test.dart` 'G21 look and motion' | missing | W1 | KIT-9, KIT-42, LOOK-9, LOOK-12–LOOK-15, LOOK-19, LOOK-20, LOOK-21, LOOK-22, LOOK-27, LOOK-32, LOOK-33, LOOK-35, LOOK-37, LAY-6, LAY-7, MOT-2, MOT-3, MOT-5, MOT-8, MOT-12, A11Y-8 |
| G22 | `test/kit/kit_glass_contract_test.dart` | missing | W1 | LOOK-20, LOOK-21, LOOK-28–LOOK-30 |
| G23 | `test/golden_harness_test.dart` | missing | W1 | TEST-7, TEST-8, TEST-9, TEST-20, ARCH-11 |
| G24 | `test/architecture_boundaries_test.dart` | missing | W1 (ARCH-10 part with slice-P6.7) | ARCH-1, ARCH-2, ARCH-10 |
| G25 | `test/repository_hygiene_test.dart` (extended) | partial | W1 | PROC-1, MOT-10, SEC-6, SEC-7, TEST-12, TEST-18 |
| G26 | `test/analyzer_suppressions_test.dart` | missing | W1 | PROC-3 |
| G27 | `test/l10n_arb_test.dart` + integrator gen-l10n step | missing | W1 | COPY-1, COPY-3, COPY-4, COPY-22–COPY-28 |
| G28 | `test/ui_glossary_test.dart` (glossary extension) | missing | W1 | COPY-6–COPY-8, COPY-10, COPY-12, COPY-21, LOOK-15 |
| G29 | `test/security_invariants_test.dart` | missing | W1 | DATA-4, SEC-5, SEC-8, SEC-12, PROC-30 |
| G30 | `tool/qa/check_work_units.py` | missing | W1 | PROC-10–PROC-12, PROC-19, PROC-25, ARCH-6, MAP-1, NAME-1 |
| G31 | `tool/qa/check_ratchets_only_shrink.py` | missing | W1 | PROC-13, KIT-4, KIT-44, TEST-10 |
| G32 | `tool/qa/check_qa_record.py` | missing | W1 | PROC-4, PROC-20, PROC-21, PROC-31, PROC-32, STATE-20, STATE-21, TEST-2, TEST-6, TEST-20, EVID-1–EVID-12, A11Y-7, PERF-7, LAY-16 |
| G33 | `.githooks/commit-msg` + `tool/qa/check_commits.sh` | missing | W1 | PROC-5, PROC-14 |
| G34 | `tool/qa/check_doc_links.py` | missing | W1 | PROC-28 |
| G35 | `test/census_manifest_test.dart` | missing | W1 | TEST-17 |
| G36 | `test/test_hygiene_test.dart` | missing | W1 | TEST-4, TEST-5, KIT-10 |
| G37 | `test/kit/kit_asserts_test.dart` (asserts live in the parts) | missing | part | KIT-22, KIT-24, KIT-28, KIT-29, KIT-33, KIT-35, KIT-36, LAY-9, LAY-12, LAY-14, COPY-9, COPY-16, STATE-8, STATE-9, STATE-13, STATE-14, AUTO-10, A11Y-5 |
| G38 | `test/needs_you_contract_test.dart` | missing | W3 | AUTO-9–AUTO-16 |
| G39 | `test/automation_policy_test.dart` | missing | W3 | AUTO-4, AUTO-5, AUTO-7, AUTO-8, MOT-13 |
| G40 | `test/act_treatment_test.dart` | missing | W3 | DATA-11, DATA-12 |
| G41 | `tool/qa/check_ui_ledger.py` (removed pages, routes) | partial | W3 | AUTO-1, AUTO-19, COPY-19 |
| G42 | `tool/qa/issue87/compare.py` | missing | CP | PERF-1, MOT-14, LOOK-31 |
| G43 | `tool/qa/machine_lock.sh` | missing | W1 | PROC-16 |
| G44 | `tool/qa/verify_apk_signer.sh` | missing | CP | PROC-24 |
| G45 | Claude Code hooks and permission rules (owner approval needed) | missing | W1 | PROC-8, PROC-15, PROC-27 |
| G46 | `tool/qa/run_serial_tests.py` preflight + `scripts/release.sh` | missing | W1 | PROC-1 |
| G47 | `test/permission_presentation_test.dart` | missing | W1 | COPY-15 |
| G48 | `test/kit_ratchet_test.dart` 'G48 drafts' | missing | W1 | DATA-2 |

Rules whose gate is only "reviewer" are the judgement rules listed in §17. The other existing test paths named in the rule tables stay in force as they are.

### 18.2 What each missing gate checks

- **G2 (ratchet).** In `lib/` outside `lib/ui/kit/`, a per-file baseline that only shrinks, for `Clipboard.setData(`, `HapticFeedback.`, `launchUrl(`/`launchUrlString(` (outside `external_link.dart`, plus a reasoned allowlist), `AnimatedSize(`, `ProductErrorState(|ProductEmptyState(|ProductInlineEmpty(`, `showConfirmSheet(`, `KitSecretField(`, and every old name a kit change retires (KIT-43). In `lib/ui/` only (not `lib/state`, `lib/api*`, `lib/background` and other services): `(duration|reverseDuration):\s*(const\s+)?Duration\(` and `Curves\.`. Inside the kit, absolute: `HapticFeedback.` only in `kit_haptics.dart`, `Clipboard.setData(` only in `kit_copy.dart`, `Duration(` in a `duration:`/`reverseDuration:` argument and `Curves.` only in `kit_motion.dart` (other kit waits read the named `KitMotion` constants), `AnimatedSize(` only in the allowlisted button spinner.
- **G3x (absolute).** `_forbidden` in `design_standard_test.dart` gains every G1, G2, G7, G17 and G21 pattern for files in `_migrated`, and a file added to `_migrated` after this date also needs `<name>_ar_dark.png`.
- **G4 (ratchet on a shrinking allowlist).** Parse `lib/ui/kit/kit.dart` exports and collect public widget classes and `showKit…` functions. Each widget lives in the NAME-1 file, declares its states in its doc comment (KIT-12) with one gallery scene per declared state, and has `test/goldens/kit/<snake>_golden_test.dart` using `kitGallerySizes` and `kitGalleryScaledSizes` with `text2` and `_ar_` variants, `test/kit/<snake>_test.dart`, and a row in the `kit.dart` doc table; the motion, keyboard, overflow and gallery-guideline tests read this manifest instead of hand lists. Each `showKit…` returns `Future<` (except `showKitUndo`) and declares at least one optional `Key? …Key` parameter. Parts that predate the gate (KitIconButton, KitSecretField, KitInset) are on an allowlist that only shrinks.
- **G5 (absolute).** `kitGalleryShot` runs `androidTapTargetGuideline`, `labeledTapTargetGuideline` and `textContrastGuideline` on every shot, in both themes, and checks that the semantics traversal order is top to bottom, then start to end by rect (A11Y-4).
- **G6 (absolute).** Every G4 gallery scene is pumped at the LAY-4 overflow sizes (320, 360, 412, 600, 800, 840, 1280 and 1600 dp wide, and 915×412), at text 1.0, 1.3 and 2.0, LTR and RTL; `tester.takeException()` is null, and KitSegmented reaches its stacked `KitChoiceRow` layout when labels do not fit.
- **G7 (ratchet; zero in the kit except the allowlist).** `EdgeInsets\.only\([^)]*\b(left|right):`, `EdgeInsets.fromLTRB(a, b, c, d)` only when `a` and `c` differ textually, `Alignment\.center(Left|Right)`, `TextAlign\.(left|right)`, `Positioned\([^)]*\b(left|right):`, and the bidi literals U+2066–U+2069 and U+200E outside `lib/ui/kit/` (COPY-30). Allowlist by file, with a reason: `TextAlign.left` and `Alignment.centerLeft` inside the forced-LTR subtrees of `KitTechnicalValue`, `KitCodeBlock`, `KitLogPanel` and `KitDiffView`.
- **G8x (absolute).** `kit_motion_test.dart` iterates the G4 manifest: under `disableAnimations` and under Effects Off each part settles after one `pump()` with no ticker running.
- **G9x (absolute, per part).** The K2 §7 G9 contracts for each part, plus: fake-async 8 s escalation for every waiting part; receipt transitions; KitStateView error actions (Copy details and Report, or the network fix); KitSheet pinned actions visible at `textScaler` 2.0 and with `viewInsets.bottom` 300 (KIT-17); `KitConfirmKind.neutral` for restart, cancel send and withdraw paints no `dangerFill` (LOOK-5); a list code block of 10,000 lines renders at most the cap with "Open full output"; KitLogPanel does not poll with `TickerMode` off; one announcement per status change.
- **G11 (absolute, with a reasoned allowlist).** Resolve the ARB key passed as the title and confirm label of every `showKitConfirm(` call: the English title ends with "?", the Arabic with "؟" or "?"; the label has at least two words and is not OK/Yes/Continue/Confirm/Delete. Collect rendered text from every golden fixture and fail on a contradiction pair (Working + stopped, Paused + idle, Connected + reconnecting). Scan all ARB values for engine words, ports, paths and versions, allowing only keys whose `@description` starts with `Technical:` or `Field example:` (COPY-11).
- **G12 (absolute).** Fake provider keys and bearer tokens fed through KitLogPanel, KitDetailsFold, KitCodeBlock, KitIconButton.copy, diagnostics, the report preview and notification copy never appear in rendered text, the mocked clipboard or captured `debugPrint` output.
- **G13 (ratchet).** Every `kit-v2.json` `replaces` entry exists in `map/all.json`; the count of `kit: "none"` only goes down; `whenMissing: hidden` appears only where the capability has no enable flow.
- **G14x (absolute).** For every modal part and KitRow under desktop capabilities: Esc closes (obeying draft and dirty), Enter confirms only neutral confirms, Tab reaches every action, right-click and long-press open the same KitRowMenu items, and a KitIconButton's tooltip equals its semantic label.
- **G15x (absolute).** The G15 width-comparison patterns at zero inside `lib/ui/kit/` except `kit_layout.dart`, and no numeric `maxWidth:`/`width:` layout width in a kit part other than a `KitLayout` name (LAY-2).
- **G17 (per-file ratchet, inside the kit too, baselined at creation; absolute inside the kit from slice-P9.10).** In `lib/ui/` except `app_theme.dart`, `theme_packs*.dart` and `theme_roles.dart`: `\bColor\(0x`, `\bColors\.(?!transparent\b)\w+`; outside the kit also `\.colorScheme\.`, `\.textTheme\.` and `\.accent\b` (LOOK-6); everywhere `hairline\S*\.withValues\(`; `attention` roles referenced only inside `lib/ui/kit/`. A kit unit brings every file in its write set to zero.
- **G18 (absolute; extends the VL branch's `test/theme_roles_test.dart`, which already checks pack contrast floors, the whole role set, a custom accent and ground, whole-pixel sizes and Geist faces).** For every theme pack, and for `deriveRoles` over a sweep of accents (every offered accent plus hues every 15°) and grounds, in dark and light: the LOOK-8 contrast ratios computed on the roles; attention's hue within 20–50°, danger's within 340–20°, success's within 90–160°; every offered accent at least 30° of hue and CIEDE2000 ΔE 20 from attention and from danger, and `deriveRoles` moving a custom accent out of that band (LOOK-39); `statusColor(progress) == accent` and no tone other than attention resolves to the attention role; `ambient.length <= 3`, each alpha ≤ 0.10; theme elevations 0 for dialog, sheet, snackbar, card, popup and FAB; `dividerTheme.thickness == 0`.
- **G19 (absolute; whole-pixel sizes and Geist faces are already in G18's test).** `KitText.styleFor` for each role equals LOOK-12 exactly; every Material `textTheme` slot maps onto a LOOK-12 role; under Arabic, letter spacing is 0 and the fallback contains Noto Sans Arabic; `label` tracking is 0; `mono` under `TextDirection.rtl` lays out LTR; every role colour has alpha 255 and no shadows; `pubspec.yaml` lists no Space Grotesk or JetBrains Mono asset.
- **G20 (absolute).** `KitTokens` pins: radii (LOOK-19), gutter 16, panel padding 16, rows 54 and 60, section gap 22, label gap 8, space1–6, `minTarget` 48, button height 50, icon sizes in {20, 22, 24} and `maxIconScale` 1.5, icon tile 30, nav height 60 and radius 22, glass shadow (0, 6, 16, 0.30); `hairlineWidth` is `1/dpr` and `focusRingWidth` `2/dpr`; and on `KitLayout`: reading 720, list 960, `paneListWidth` 296, conversation 700, changes 340, dialog 560, confirm 480, end sheet 400–480, medium sheet cap 640.
- **G21 (per-file ratchet, inside the kit too, baselined at creation; absolute where noted; absolute inside the kit from slice-P9.10).** It scans `lib/ui/` only, excluding `app_theme.dart`, `theme_packs*.dart` and `theme_roles.dart`. Outside `lib/ui/kit/`: `fontSize:`, `TextStyle(`, `BorderRadius\.circular\(\d`, `Radius\.circular\(\d`, numeric `EdgeInsets…(\d`, `SizedBox\((width|height):\s*\d`, `BoxShadow(`, `boxShadow:`, `shadows:\s*\[`, `ImageFilter\.blur`, `BackdropFilter(`, `Border\.all\(`, `thickness:`, `\.toUpperCase\(\)`, `(Radial|Linear|Sweep)Gradient\(`, `Image\.(asset|network|memory|file)\(`, icon `size:` other than 20/22/24, `PageRouteBuilder(`, `transitionsBuilder:`, `KitEffects\.of\(`, `TextScaler\.linear|maxScaleFactor:|TextScaler\.noScaling` (A11Y-8), and `withOpacity\(|Opacity\(` around text (LOOK-14). Absolute: `KitGlass(`/`GlassSurface(` only in the LOOK-27 file allowlist; `KitSurfaceLevel\.(glass|raised|tonal)` nowhere (KIT-42); `disableAnimationsOf` only in `kit_motion.dart`; `Transform\.scale|ScaleTransition` nowhere in kit transitions; `(?i)\b(metal|chrome)\w*` nowhere in `lib/` or `shaders/` identifiers or asset names; inside the kit (ratchet until slice-P9.10), numeric literals in radii, insets, `SizedBox` and `fontSize` only in `kit_tokens.dart` and `kit_text.dart`, and a `BorderSide(` with a `width:` not read from `KitTokens` fails (LOOK-21). A kit unit brings every file in its write set to zero.
- **G22 (absolute).** A KitGlass inside another KitGlass fails a debug assert; KitGlass has exactly one shadow (0, 6, blur 16, the `glassShadow` role); its rim painter draws a `glassRimLight` top line and a `glassRimDark` bottom line of `1/dpr`; its foreground comes from roles; Effects Glass off, battery saver and SEVERE heat give surface2 at 94 %; text contrast ≥ 4.5:1 for the composer and top controls in liquid and frosted modes.
- **G23 (absolute).** `kit_gallery.dart` sets `devicePixelRatio = 3.0` and every golden harness sets `debugDefaultTargetPlatformOverride = TargetPlatform.android` (ARCH-11); golden file names match TEST-20; every `test/goldens/**/*_golden_test.dart` has the regenerate header and calls `loadCaptureFonts` or `loadKitGalleryFonts`; any file using `Locale('ar')` loads Noto Sans Arabic; `loadCaptureFonts` registers the families `AppTheme` uses.
- **G24 (ratchet).** Per `lib/ui` file: imports of `lib/api/` and `lib/api2/`, and uses of `ServerFlavor.` or `.flavor ==`, against a baseline that only shrinks (new files zero; the flavour allowlist gives reasons). From slice-P6.7, absolute: the `oc/background` method that posts a notification is invoked only from the `NotificationRouter` file (ARCH-10).
- **G25 (absolute).** No `flutter_animate` in `pubspec.yaml`, `pubspec.lock` or any `lib/`/`test/` import; `git ls-files` lists nothing under `.claude/skills/`, no `*.jks`/`*.keystore`, no `android/key.properties`, nothing under `*/failures/`, no `*.mp4|*.webm|*.mov` under `docs/qa/`; the pubspec version matches `^\d+\.\d+\.\d+\+[1-9]\d*$`; `compileSdk = 37` and Java 17 in the Android build files; the running Flutter's `bin/cache/flutter.version.json` framework revision starts with `91f8bd75` and equals `kit_ratchet_flutter_widgets.json`.
- **G26 (ratchet).** Per-file count of `//\s*ignore(_for_file)?:` in `lib/` and `test/`, and the `analysis_options.yaml` `exclude:` list, against a committed baseline.
- **G27 (absolute for new keys; ratchet for existing gaps).** en/ar key sets equal; placeholder names equal per key; every placeholder declared with a type; Arabic plurals have zero/one/two/few/many/other; no bidi controls in Arabic values; no backtick or ASCII `"` in Arabic; keys match `^[a-z][A-Za-z0-9]*$`; keys added after the baseline start with a COPY-3 prefix and not with `e7`, `teamUi`, `aiteam`, `termux`, `builtin` or `local`; the count of keys ending in a digit and of keys without `@description` never rises, and any key added after the baseline has a description; Arabic identical to English only on an allowlist; banned Arabic terms (انقر, الفريق الذكي, الكمبيوتر, جلسة and derived forms, مساحة العمل, and حاول مرة أخرى in labels) only shrink; engine words absent from Arabic values of the AI Team prefixes; every `AgentErrorCause` has Arabic wording different from English. Integrator step after each merge: `flutter gen-l10n`, then commit `lib/l10n/app_localizations*.dart` (units never commit them, PROC-13); at the wave end, prune ARB keys with no reference in `lib/`.
- **G28 (ratchet with reasoned allowlist).** Label scan over `app_en.arb` adds: `Reload`; `^Cancel\s+(run|task|job|work|install|download)\b`; "Sign out"/"Close" on server keys; nouns workspace, location, profile, activity, attention, and host outside `teamUi*`; Termux setup, On-device setup, Local server; OpenCode, Codex, Paseo, Gas City in short labels outside chooser keys; values exactly OK, Ok, Yes, No, Continue, Confirm, Submit; values matching `^[A-Z\s]{4,}$`; `*Title` keys whose English value, with each placeholder counted as one word, is over four words or has a capitalised non-first word not on a proper-noun list (COPY-10); `*Body` keys over two sentences; "(expert|simple|advanced|basic) mode". The noun rule also runs over sentences, as a ratchet.
- **G29 (absolute; ratchet for existing hits).** Interpolated `'oc.…'` key literals in `lib/` end in a profile-id interpolation or are on the app-wide allowlist; `test/`, `tool/` and `docs/qa/` contain no `sk-ant-`, `sk-[A-Za-z0-9]{20,}`, `ghp_`, `Bearer [A-Za-z0-9._-]{20,}` or "server password" lines; `curl … | (ba)?sh` in `lib/` needs a sha256 check in the same script (baseline the known one); `obscureText` only in `lib/ui/kit/`.
- **G30 (absolute).** Every unit has `id`, `wave`, `kind`, `title`, `write`, `read`, `after`, `finishLine`, `nonGoal`, `acceptance[]`, `checks[]` and `proof`; write sets are pairwise disjoint within a wave (except the PROC-13 shared-merge files); units touching a single-owner group are ordered by `after`; no unit lists `packages/opencode_sdk/**`, `lib/main.dart`, `lib/state/connection.dart` or `lib/l10n/app_localizations*.dart` in `write`; every map page is in a screen unit's `pages`, and every `remove`, `merge-into:*` or `redesign` page in a slice's `pages` or on a reasoned list (MAP-1). Branch mode: `git diff --name-only base...revamp/<id>` ⊆ write set ∪ new `lib/ui/kit/**` files named by NAME-1 ∪ the unit's own tests (PROC-10: write set, `tests` list, new NAME-1 test files, and existing tests that import a write-set file) and goldens ∪ its QA folder ∪ the PROC-13 shared-merge files and registries; `lib/l10n/app_localizations*.dart` is never in a unit's diff.
- **G31 (absolute).** Against the merge base: no count in `kit_ratchet_baseline.json`, the `l10n_coverage_test.dart` `_baseline` or any G2/G7/G17/G21/G24/G26/G27/G28/G29 baseline rises or gains a key, except for a pattern named by a `ratchet-tighten: <gate> <pattern>` line in a commit body (KIT-44); a key may disappear only if its file no longer exists at HEAD; when a file is split, the new files' counts added together do not exceed the old file's, and the commit body says `split: <old> -> <new...>`; `_migrated`, `_migratedClasses` and `_retired` keep every base entry whose file still exists, and a deleted file's entry moves to `_retired`.
- **G32 (absolute for records created after this file).** Folder name matches `^revamp-[A-Za-z0-9.-]+-\d{4}-\d{2}-\d{2}$` (or the feature-date form); the seven headings in order plus a State table with the six states; the date equals the UTC date of the unit's first build commit; Builds has a `code head` 7–40 hex hash that is an ancestor of HEAD, with only the QA folder and listed fix-ups changed after it, and an APK SHA-256 or "No APK"; Devices is non-empty; Runs has a table whose rows contain PASS or FAIL; every referenced `.png`/`.txt` exists; if the unit's commits include a `fix(` subject or Scope says "Fix:", a `failing-first*.txt` exists and contains an assertion failure (`Expected:` and `Actual:`), not only a compile error; Scope has the Contract problems, New kit parts, Map items (EVID-11) and Deferred states lines; the Rule evidence table names an existing test or golden for each row (PROC-31); a blocked unit's State says Implemented "partial" and its build record lists blockers (PROC-32); every golden PNG changed in the branch is named, with its approved render or "no approved render" (EVID-12), and the branch adds at most 60 PNGs or 8 MB unless the record carries the coordinator's approval (TEST-20); unit records contain no `-j [2-9]`; when `lib/ui/` changed there is at least one `.png` and the Accessibility line; when storage or credentials changed, Privacy and Migration lines; checkpoint records have TalkBack, device-recipe and phone/tablet/large-window sections (LAY-16) and the census re-render; the integrator's index row exists after merge.
- **G33 (absolute).** A committed `commit-msg` hook (`core.hooksPath=.githooks`) and a pre-merge script: every commit in `base..HEAD` contains `[skip ci]` and, if not a merge, a non-empty body; `dart format --output=none --set-exit-if-changed --language-version=3.10` passes on changed Dart files.
- **G34 (absolute).** Relative links in changed `docs/**/*.md` resolve to existing files.
- **G35 (absolute).** `docs/qa/screen-census/manifest.json` page ids equal the ledger's page ids, each with states or notRendered plus a reason; the harness fails (not skips) on a fifth state; every `CensusShot` render closure calls `expectVisible` or a named guard.
- **G36 (ratchet).** Every `Key('…')`/`ValueKey('…')` string literal under `lib/ui/` appears in `test/` or `tool/capture/`; every test file constructing `ProfileStore(` mocks the secure-storage channel or relies on the global mock (absolute once it exists).
- **G37 (absolute).** Debug asserts in the parts that never depend on data (no word counts of titles), each with a test that pumps the bad configuration and expects an `AssertionError`: KitIconButton label non-empty; disabled KitAction, KitButton, KitField and KitSegmented have a `disabledReason`; a destructive primary only inside the confirm body; KitActionBlock with a destructive tertiary stacks; KitRowMenu destructive items last; KitSegmented 2–4; KitTopBar action limits per window; per KitScreen at most one visible primary, one KitStatusLine, one KitDetailsFold (last) and one refresh control; KitRow with `swipe` has a matching menu item and its act's treatment is not confirm (KIT-29); KitActionBlock keeps 8 dp around a destructive target (LAY-9); KitNeedsYou and KitRequestCard need `reason` and `ifIgnored`; status marks need a label; a prerequisite state view needs its action; a confirm body passed as a fixed ARB value is ≤ 2 sentences.
- **G38 (absolute).** The Needs-you reason is a closed enum with exactly the three AUTO-9 kinds; quiet event kinds map to the quiet channel and never to the badge; one request seen through two paths counts once; Inbox is oldest first with a server chip on each row; answering in the conversation clears the Inbox row, badge and header word and calls the router's cancel for that id; the work line says "Waiting for you"; the Done-notification defaults; a notification tap lands on the card, or shows "Already answered at …".
- **G39 (absolute).** Every entry in the automation registry writes a While-you-were-away line and names a test that exists; the registry contains none of the AUTO-7 acts; a one-option picker opens no sheet; power save or SEVERE heat turns motion Calm and glass solid.
- **G40 (absolute).** A table mirroring K2 §4.1 (act → confirm, Undo or none) driven from each door (swipe, menu, button, desktop context menu); heavy deletes pass `typedName`.
- **G41 (ratchet to absolute).** `check_ui_ledger.py` fails when a TIA §1.4 removed page id appears in the ledger or `lib/`; technical pages are reachable only from Diagnostics, a server's details or the palette; after the installer slice, no route to `/termux-setup` and no import of `builtin_server_screen.dart`.
- **G42 (absolute, at the checkpoint).** Protocol: the same AVD and fixture as `baseline.csv`, the scripted scene in `tool/qa/issue87`, three 30 s runs, median. A regression is a janky-frame share more than 1.0 percentage point above baseline, a p90 frame time more than 10 % above baseline, any frame over 32 ms that the baseline lacks, or a PERF-1 budget missed. For new glass (LOOK-31) it compares glass on and off by the same thresholds. It exits non-zero on any regression, and its output goes into the checkpoint record.
- **G43 (absolute).** `flock` on a machine-wide lock file, taken by `run_serial_tests.py` and the integrator's build and emulator commands.
- **G44 (absolute, at the checkpoint).** `verify_apk_signer.sh <apk> <expected-sha256>` fails on mismatch; its output is pasted into the record.
- **G45 (absolute; needs the owner's approval to change harness settings).** A PreToolUse hook that denies Bash commands matching `\b(pkill|killall)\b`; for reviewer agents, denies `flutter test`, `dart test` and builds; asks before `git push`, `gh pr create` and `gh workflow run`.
- **G46 (absolute).** `run_serial_tests.py` refuses to start, and `release.sh` refuses to continue, unless the Flutter framework revision is `91f8bd7…` (read from `bin/cache/flutter.version.json`, not the version string).
- **G47 (absolute).** Each known permission id maps to its plain-action getter in English and Arabic, and an unknown or empty id yields "Permission needed".
- **G48 (ratchet).** A file that calls `showKitSheet(` and contains a multiline field (`KitFieldKind.multiline`, `maxLines: null` or `minLines:`) also contains `draft:`; existing offenders are baselined.

---

## Appendix A. Resolved conflicts

| # | Conflict | Resolution |
|---|---|---|
| 1 | Flutter 3.47.1 (AG, the Shorebird cache) vs 3.47.2 (CON, TO, `release.sh`, CI, hygiene test). | 3.47.1, revision 91f8bd7 (AG). The others are fixed before wave 1 (§0.5). |
| 2 | Suite split: `ls test/*.dart` (CON) vs recursive (AG, runner). | Recursive `run_serial_tests.py` (AG). CON's "CI splits into thirds" is stale. |
| 3 | Test concurrency: `-j 1` (PLAN), `-j 3` allowed (AG), "use the PC in parallel" (MEM). | Parallelism comes from parallel agents; each builder uses `-j 1`, and the integrator at most `-j 3` when no builder is testing (PROC-4). |
| 4 | Chunk timeouts of 900–1200 s vs harness kills over about 600 s (MEM); always run in background (MEM). | Waits may run in the background, but every command is bounded under 600 s, including each suite chunk (PROC-6). |
| 5 | English only (TO, l10n test header) vs English and Arabic (owner, AG). | English and Arabic (owner). |
| 6 | `flutter run` debug (TO) vs no debug builds (AG). | No debug builds (AG). |
| 7 | CI as the gate (CON, TO) vs `[skip ci]` and local checks (AG). | AG: local gates, `[skip ci]` on every commit. |
| 8 | The branch ledger in `docs/verification/` does not exist. | For the revamp the ledger is PLAN §7 (updated at each checkpoint) plus the QA records. |
| 9 | OpenCode 2 replay wording differs (AG vs CON). | Both conclude refetch; ARCH-3. |
| 10 | Absolute UI/domain boundary vs 51 files that break it. | Shrinking baseline, new files zero (ARCH-1, G24). |
| 11 | `lib/l10n` output is single-owner, yet every unit adds copy; no owner for ARB files, `kit.dart` or baselines. | Shared-merge rules (PROC-13): units add ARB keys and registry entries, the integrator unions them; the generated `app_localizations*.dart` has one owner, the integrator, who regenerates and commits it after each merge (AG single-owner rule; see #79). |
| 12 | `app_ar.arb` is assembled by a script, but the Arabic review edited it directly. | `app_ar.arb` is the source of truth; the assembler is not run (COPY-5). |
| 13 | 102 units lack finish lines and non-goals. | §1.2 defaults; G30 before wave 1. |
| 14 | QA path `docs/qa/revamp-<unit id>/` (PLAN) vs `<feature>-<date>` (owner audit rule, QA). | `docs/qa/revamp-<unit id>-<YYYY-MM-DD>/` (owner rule wins; EVID-1). |
| 15 | Device proof for every change (owner, QA) vs no emulator for units (PLAN). | Unit records say "Devices: None"; device proof is in each wave checkpoint record. |
| 16 | K2 "a part joins the kit when two pages need it" (§4.12, entry rule, §5 module parts). | K2 §9 kit only (owner) overrides for anything that draws; a private composition of kit parts inside one screen file stays there (K2 §4.12), and everything that draws is a kit part (KIT-3). |
| 17 | `showDatePicker` allowed (K2 G1) vs KitDateTimePicker (§9.2). | Allowed until KitDateTimePicker merges, then counted (KIT-2). |
| 18 | Spec vs code API drift (KitIconButton, KitAction, KitActionBlock, KitDraft, showKitSheet, KitSecretField). | KIT-39, KIT-40. |
| 19 | Disabled KitIconButton explains in its tooltip (K2 §1.10) vs visible reason (§4.9, §8.3). | Visible reason or hidden (STATE-8). |
| 20 | `KitAction.destructive` "always confirm" (code doc) vs Undo for restorable deletes (K2 §4.1). | K2 §4.1 (DATA-11); the code doc is corrected by the kit-change unit. |
| 21 | `showKitUndo` returns `void` vs "every modal returns a Future". | `showKitUndo` is the one exception (KIT-11). |
| 22 | KitTopBar "at most one icon" (§1.18) vs "at most 2 icons" (§8.2). | One action icon plus overflow on compact (two icons), three on medium, labels on expanded (KIT-36). |
| 23 | KitDialog fade-and-scale (K2 §1.3) and KitTabSwitcher scale vs "never fade-scale" (VL §7). | VL: slide or cross-fade (MOT-2). |
| 24 | KitJumpPill and a general `KitSurface` glass level (K2) vs glass only on the navigation layer (VL §6). | VL: the jump pill is solid; glass only on the LOOK-27 list; `KitSurface` has no glass level (KIT-42). |
| 25 | Galleries at DPR 1 (harness) vs DPR 3 (VL §7, PLAN). | DPR 3 for kit galleries (TEST-9, G23). |
| 26 | KitText roles in K2 (title, heading, body, label, caption, mono, number) vs VL roles. | VL roles (LOOK-12); `heading` is `headline`; numbers use tabular figures within their role (LOOK-18). |
| 27 | Fractional sizes in VL §2 vs integers in VL §7. | §7 integers (owner). `shared-visual-system.md` is superseded. |
| 28 | `target-ia.md` §4 "Kit v2 does not restyle; glass on dock and composer only". | Superseded by VL (later, owner-approved). |
| 29 | Theme packs change only the accent (VL §3) vs "colours are a user-customisable theme via roles" (owner) and `deriveRoles`. | Owner (PLAN §1, and the theme code's "meaning stays with the role; only the hue follows the theme"): a theme, pack or custom, may set every role's value but never its meaning; attention stays amber or orange, danger red, success green, each checked by hue band and contrast (LOOK-7, G18). VL §3 "packs change only the accent; attention and danger never change" is amended by the coordinator. B3 is closed. |
| 30 | Accent "only primary and working" vs links, current server, illustration, selected nav. | Primary, working, links and the current-selection mark (LOOK-6); illustrations use their own drawing accent from the scene. |
| 31 | Spacing scale 4/8/12/16/24/32 (PR) vs KitTokens and VL values. | KitTokens and VL named values (LAY-7). |
| 32 | At most three type sizes per screen (PR §8). | Dropped: not achievable with the VL roles; LOOK-12 replaces it. |
| 33 | Buttons 44–52 tall (VL §4) vs 48 dp targets. | 50 dp buttons, 48 dp minimum target (LAY-9). |
| 34 | Scrim blurred at σ 3 (VL §3) vs blur only behind glass (VL §7, owner rule). | No scrim blur (LOOK-22). |
| 35 | Shadow exceptions and `AppTheme.raised`/`glow`, elevations 5–8, KitGlass y2/blur 8. | VL §7: only the needs-you ring and one glass shadow y6/16/30 % (LOOK-20). |
| 36 | Glass placement lists differ (DS §10 vs VL §6). | VL §6 (LOOK-27). |
| 37 | Glass off: solid (DS, code) vs surface2 at 94 % (VL); extra solid conditions in DS. | Effects off → surface2 at 94 %; high contrast, accessible navigation or remove animations → fully solid; liquid needs Impeller and Android 12+ (LOOK-29). |
| 38 | Layout animation banned vs `KitReveal`, `KitAnimatedRows`, `KitButton` use size animation. | Allowed only in `KitReveal` and the button spinner, never in scrolling lists; `KitAnimatedRows` animates paint (MOT-5). |
| 39 | Icon stroke 1.8 and "one stroke weight" vs Phosphor font glyphs, fill and duotone, sizes 14–21 in use. | One family at regular weight, sizes 20/22/24, fill only for the selected dock tab (LOOK-33); see Appendix B. |
| 40 | Off-table sizes in the visual worktree (AppBar 20/26, 15 px slots, tooltip radius 8). | VL roles and token radii only (LOOK-17, LOOK-19). |
| 41 | Surface levels "raised" and "tonal" (K2 §9.2), tonal buttons and circles (DS). | VL: no raised or tonal; icon tiles (LOOK-23); `KitSurface` levels are ground and surface1–3 only (KIT-42). |
| 42 | "Never right-aligned clusters" (DS §2) vs PC sheet buttons in a right-aligned row (VL §5). | VL: one end-aligned row from medium up (LAY-13). |
| 43 | PC panes 296/700/340 (VL) vs 360/720 (K2 §8.1). | VL (LAY-5): `paneListWidth` becomes 296 from expanded; KitScreen's 720 reading width stays for non-conversation pages. |
| 44 | Spec hexes changed in code for contrast (#087F43, #D93B40, light danger #C62828). | Contrast minima win over hex values (LOOK-8); VL gets updated by the coordinator. |
| 45 | Gates described as existing that do not (G2, G4–G7, G11–G13). | Listed as missing in §18. |
| 46 | Material tertiary maps to attention, and old code uses tertiary for progress. | No code outside kit and theme reads `colorScheme` (LOOK-2, G17). |
| 47 | Transcript: "a prompt is a ruled line, not a bubble" (test, message_view) vs an end-aligned surface2 bubble (VL §5). | VL (LOOK-26); the chat chain updates `chat_transcript_placement_test.dart`. |
| 48 | Chat parts in the chat library (K2 §5) vs in `lib/ui/kit/chat/` (K2 §9). | §9 (ARCH-6). |
| 49 | Missing turn-model document. | STATE-16 in this file is the frozen statement, drawn from the vocabulary comment in `message_view.dart` and the turn-model definitions (MEM); chat parts cite it (KIT-41). A line range in a file the chat chain rewrites is not a source. |
| 50 | "Retry" in PR, DS and K2 vs the glossary's "Try again". | "Try again" (COPY-7); Retry names the concept only. |
| 51 | "Hide, don't disable" (UXR) vs "explain instead of vanish" (CAP, TIA, PLAN). | Explain (STATE-12); tests asserting silent absence are updated by the unit that changes the behaviour. |
| 52 | "Continue" banned on confirms only (K2) vs on any button (PR). | Any button (COPY-8). |
| 53 | "task" banned (UXR) vs AI Team tasks (TB). | "task" only for AI Team work (COPY-6). |
| 54 | Refresh buttons banned on primary screens (UXR) vs migrated refresh icons (K2 §1.10). | None on primary tabs and live pages; at most one refresh control elsewhere (COPY-16). |
| 55 | Uppercase labels in ARB; bidi controls in `app_ar.arb`; Arabic vocabulary drift; "session" and "workspace" in the Arabic glossary. | VL, AR and the English glossary win; existing values are ratcheted down (G27, G28). |
| 56 | "Use {permission}" puts a raw id in a title. | "Permission needed", id behind Details (COPY-15). |
| 57 | `localization-todo.md` claims to list strings not moved; it does not. | The inventory is the `l10n_coverage_test.dart` baseline; the doc is corrected by the coordinator. |
| 58 | Minimum phone and text scale differ (360×740 at 2.5, 320 at 2.0, 200 % reviews). | Both: the G6 matrix for parts, and 2.5 on 360×740 for critical flows (A11Y-2). |
| 59 | Discard: keep drafts silently (PV) vs "Discard, Keep editing" (JN) vs the tests. | K2 §1.1: drafts kept silently; only dirty non-draft input asks (DATA-2, DATA-3). |
| 60 | "Destructive always confirmed" (PV) vs Undo for restorable deletes. | K2 §4.1 (DATA-11). |
| 61 | Error states: "Try again and Report" (JN) vs a fix for network errors (PV, TIA). | STATE-3. |
| 62 | Request card primary "Review" (current test) vs answer in place (JN, TIA). | TIA (AUTO-17); the slice that builds it updates `chat_states_standard_test.dart`. |
| 63 | Notification landing on a sheet (current test) vs on the card (TIA). | TIA (AUTO-16). |
| 64 | Voice capped at 30 s (test) vs "Why only 30 seconds, this needs rework" (owner verdict). | Owner: the cap goes; the voice slice replaces the test. The new limit is in Appendix B. |
| 65 | Header words "Working 2 min / Needs you / Done" vs "Working / Needs you / Not answering". | TIA §1.1; "Done" belongs to While you were away. |
| 66 | AI Team door only after the first finished conversation (TIA §3) vs a quiet door until first use (TIA §1.1, CAP). | TIA §1.1. |
| 67 | Report "needs no GitHub knowledge" vs sent as a GitHub issue. | Copy and Share first, GitHub optional (SEC-11). |
| 68 | "Secrets never displayed" vs a reveal toggle. | Reveal during entry only; never after saving (SEC-3). |
| 69 | 409 conflicts shown as toasts (tests) vs snackbars only for Undo (K2 §4.8). | In-place receipt or notice; the tests are updated by the chat units. |
| 70 | `external-agents` with integrations (JN) vs in Tools (TIA). | TIA. |
| 71 | The heat guard standard (PV) omits the stop at CRITICAL. | Included (AUTO-21). |
| 72 | Kit `…Key` parameters (K2) vs keys only for tests (AG). | Optional parameters, passed only when a test uses them (KIT-10). |
| 73 | Architecture directory lists differ (AG, TO, CON). | ARCH-5 lists all of them. |
| 74 | VL §5 "Needs you: always an attention card with its answer buttons in place (Work, Inbox, the transcript)" vs TIA §1.2 and journeys "Inbox, notifications and team rows point to the card; they never answer it". | The owner-approved slice P4.2a ("every Inbox row, the Work 'N need you' line and notifications open the conversation scrolled to the card") outranks VL: one answer card in the conversation, pointing rows elsewhere (LOOK-24, AUTO-17). The coordinator amends VL §5. |
| 75 | TIA shows "Ask the setup assistant", its `mcp-add-sheet` option, the change card and the search fallback; the owner deferred P2 (Setup-assistant agent). | Owner: deferred; no unit builds or links them (AUTO-20, §0.2). |
| 76 | STATE-17 "one list … one bubble anatomy" vs the owner's verdict on `embedded-pending-sends-strip` ("merged in one bubble with a clear call to action inside"). | Owner verdict: one queued bubble with one action line (STATE-17). |
| 77 | LAY-8 "close and back sit at the end" vs K2 §1.18 "Back is mirrored and the actions sit at the end" and K2 §1.1 "close at the end". | Back at the start, Close at the end, both mirrored (LAY-8). |
| 78 | KitSegmented falls back to wrapping choice chips (K2 §1.6) vs "stacked from 2.0 text" (K2 §8.2, G6). | Stacked full-width `KitChoiceRow`s: wrapping chips break start alignment and target spacing unpredictably at 200 % (KIT-24). K2 §1.6 is amended. |
| 79 | PROC-13 "commit your gen-l10n output" vs PROC-11 and AGENTS.md (generated `lib/l10n/` output is single-owner) and G30. | AGENTS.md: units regenerate locally but never commit the generated files; the integrator regenerates and commits after each merge (PROC-13, G27). |
| 80 | B4 interim "success uses the accent" vs LOOK-6 (accent places) and the separate `success` role. | `success` for done, connected and added lines, always with its word; never `accent` (LOOK-6). B4 is closed. |
| 81 | MOT-1 "no literal Duration outside kit_motion.dart" vs the kit's 8 s escalation, Undo window and copied hold (K2: "a state timer, not a motion") and service timeouts. | Motion only: animation durations and curves come from KitMotion; kit waits are named KitMotion constants; timeouts outside `lib/ui/` are not counted (MOT-1, G2). |
| 82 | KIT-23 "copy only through KitIconButton.copy" vs "Copy details" text actions (STATE-3), Report a problem's Copy (SEC-11) and KitRowMenu copy items. | One copy service, `KitCopy.copy`, used by all three (KIT-23). |
| 83 | STATE-14 (one shell line), STATE-19 (an update line), KIT-35 (one per screen) and K2 §4.6 (a line on a working screen). | One visible line per window: the shell's, fed by screens; a pushed working screen's own line takes the shell's condition when higher; updates are its lowest condition (KIT-35, STATE-14, STATE-19). |
| 84 | COPY-10 and G37 "titles ≤ 4 words" (a runtime assert) vs data titles shown as written (KIT-36, COPY-2) and confirm titles that name the thing (COPY-9). | The app's fixed title words only, placeholders counted as one; checked statically on the ARB (COPY-10, G28). |
| 85 | K2 §4.2 (the error colour is primary in stop confirms; red "destroys or stops", VL §1) vs B9 interim "Stop is not red". | Red on the confirm of a stop, destructive or discard sheet, and on Stop itself (the composer's circle and the living edge's square, LOOK-5). |
| 86 | KIT-29 "a swipe always uses Undo" vs DATA-11 and K2 §4.1 (the same treatment from every door; irreversible acts are confirmed). | A confirm-first act is never on a swipe (KIT-29). |
| 87 | KIT-6 allowed `Scrollbar` outside the kit vs the owner's kit-only rule and PLAN's absolute G16. | `KitScrollbar` in the kit; the exception leaves when it lands (KIT-6). |
| 88 | LOOK-27's Appearance glass-preview exception vs VL §6 "only the navigation layer". | No exception; the preview uses the real navigation layer or a kit miniature (LOOK-27). |
| 89 | DS §10 "soft accent washes" vs the owner's sharp-and-crisp rule. | Flat hard-edged accent fills at ≤ 12 % (LOOK-36). |
| 90 | ARCH-7 loopback-only vs the shipped Tailscale allowance for AI Team and Paseo. | The code and its release-blocker test: loopback plus Tailscale through the two validators (ARCH-7). |
| 91 | ARCH-10's `NotificationRouter` does not exist; notifications are posted by native services; slice P6.7 introduces the router. | ARCH-10 applies from slice-P6.7; until then no new notification path. |
| 92 | Glass fallbacks defined in LOOK-29 and MOT-13 with different outcomes. | One definition in LOOK-29; MOT-13 points to it. |
| 93 | The kit-builder reading list skipped §8–§10 and §13 though kit parts own copy, states, drafts and redaction. | §0.4: kit builders read §1–§10, §12, §13, §15, §16. |
| 94 | VL §7 "icons 20, 22 or 24 only" vs `KitTokens.maxIconScale` 1.5 (icons grow with text). | Designed sizes 20/22/24; leading and state icons scale within `maxIconScale`, rounded to whole physical pixels (LOOK-33); the owner is asked (B16). |
| 95 | TEST-9's full state × size × theme matrix at DPR 3 (thousands of PNGs) vs one machine and the serial-suite budget. | Every state at 412×915, the default state at every gallery size, overflow checks without images for the rest (TEST-9, TEST-20). |
| 96 | PROC-13 "never reword another unit's keys" vs COPY-6/COPY-7 rewording 5,305 existing keys that belong to no unit. | Reword in place only when every reference is in the write set; otherwise add a key (PROC-13). |
| 97 | TEST-10 `_migrated` "only grows" and G31 supersets vs pages that wave 3 deletes or splits. | An entry leaves only with its file, moving to `_retired`; split counts are conserved (TEST-10, G31). |
| 98 | Kit-change units must keep call sites compiling (§1.2) vs KIT-38/KIT-40 removals and renames. | Kit changes are additive; the unit that brings a pattern to zero deletes the old API (KIT-43). |

## Appendix B. Owner and coordinator decisions (2026-09-26)

The owner answered four questions on 2026-09-26. The coordinator decided the rest under the owner's standing instruction to decide by the UI/UX rules and personas; the owner may override any of them.

| # | Question | Decision | By |
|---|---|---|---|
| B1 | Rows lose their ⋮ button. How are a row's extra actions reached? | Long-press, right-click and swipe, like the system apps. The same actions are semantic custom actions for TalkBack and are also reachable from the item's own page (KIT-28). | owner |
| B2 | May a failed state use red? | No. Red stays for acts that destroy or stop something. A failure is said in words (`text1`) with a neutral error glyph (LOOK-5). | coordinator |
| B3 | Colours | Closed: colours are a theme the person can change (LOOK-7). | owner |
| B4 | Success equals the accent in Graphite. | Done, connected and added lines use the `success` role with their word and a check glyph, so they are never told apart by colour alone. Graphite keeps `success` equal to the accent (LOOK-6). | coordinator |
| B5 | Light surface1 and surface2 are both white. | Keep. Sheets are separated by the scrim and the grabber. | coordinator |
| B6 | Phosphor cannot draw a 1.8 stroke. | Accept Phosphor regular as the one stroke, with fill only for the selected tab (LOOK-33). | coordinator |
| B7 | Should a Needs-you notification carry answers? | Permission requests carry "Allow once" and "Don't allow". Questions, forms and team gates open the card (AUTO-16). | owner |
| B8 | A request from another server | Answered in place, from the Inbox card or the notification, without switching the active server (AUTO-16, AUTO-17, LOOK-24). | owner |
| B9 | Stop and Send side by side while busy (OpenCode 2 steering) | Superseded: one trailing control; Stop leads the row when Send trails it (never side by side). Stop is red (LOOK-5, LOOK-26). | coordinator |
| B10 | The word for withdrawing a team card that is not running | "Withdraw". Running work is "Stop". | coordinator |
| B11 | What replaces the 30-second voice cap? | Recording runs until the person stops, with a 10-minute safety stop that says so, and transcription in chunks. Slice P10.3/P10.4 builds it. | coordinator |
| B12 | Which signer for which audience | The owner's phone uses the local release key `1DE5BF08…`. Public releases use `842284B2…`. CI uses `2D010C21…`. Compare with the installed build every time (PROC-24). Builders never build APKs. | coordinator (facts from the release records) |
| B13 | Harness settings gate G45 | Not added. Reviewers check PROC-8, PROC-15 and PROC-27. The owner's Claude Code settings are not changed. | coordinator |
| B14 | Arabic hint "اختصر المحادثة" | Accepted. اضغط means only "tap" (COPY-25). | coordinator |
| B15 | The orange accent clashes with amber, which means "needs you". | Replace orange with teal. The accents are green (default), blue, teal and violet (LOOK-39). | owner |
| B16 | Icons 20/22/24 only, or grow with text size? | Grow within `maxIconScale` 1.5, rounded to a whole physical pixel, for accessibility (LOOK-33). | coordinator |
| B17 | Arabic digits | Digits come only from `intl` formatting for the locale; nobody converts them by hand (COPY-30). | coordinator |
