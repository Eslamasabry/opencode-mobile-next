VERIFIED: green

# P12-BUILD-INV-UI-C: file-by-file parity inventory, UI part C

Branch `claude/file-inventory-ui-c-parity-ykkja7`, on base `native/p12-integrate-2026-11-03` (`71f74de8`).
This unit does analysis only and produces a machine-readable record. It makes no product code changes.

## Deliverables

- [`docs/native/inventory/ui-c.jsonl`](../inventory/ui-c.jsonl): one JSON line for each Flutter file in this unit. The keys are
  `file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested`, plus
  `uncatalogued_gaps`, the gaps that no catalog row covers.
- [`docs/native/inventory/ui-c.md`](../inventory/ui-c.md) contains:
  - totals by status and by kind
  - the 20 biggest missing or partial files, with their gaps
  - the gaps that no catalog row covers
  - the done files, with evidence
  - the method

## What "VERIFIED: green" means here

- **Coverage:** the JSONL covers every file in the unit's list exactly once. That is 248 unique paths; the brief said 247,
  and the listed paths were taken as the source of truth.
- **Validity:** every line parses as JSON.
- **Native paths:** every path in `native` exists in the tree.
- **Catalog ids:** every id in `catalog_rows` exists in `catalog/behaviors.jsonl`. Reviewers proposed two ids that do not
  exist (`B-NAV-086` and `B-LINUX-253`); they were dropped.
- **Statuses:** a file is "done" only when it names native production files.

I checked the JSONL with a Python script. No Gradle or Flutter build was needed, because no code changed. I did not install
the Android SDK, because nothing in this unit compiles.

## Result in one view

| Status | Files | Flutter lines |
|---|---:|---:|
| done | 10 | 4,503 |
| partial | 110 | 44,318 |
| missing | 60 | 20,532 |
| after-switch | 59 | 24,844 |
| dropped | 6 | 1,699 |
| not-needed | 3 | 232 |

Other counts:

- **Catalog rows:** 260 distinct rows are mapped.
- **No catalog row:** 66 files have none, and 45 of them are partial or missing.
- **No Flutter test:** 32 files have no test that covers their main behaviour.
- **Uncatalogued gaps:** 297 gap phrases are not covered by any catalog row.

## Findings the integrator should act on

1. **Settings hides most of its rows.**
   - `SettingsWiring.kt` OPENS lists only Servers and a few external pages, so these rows never show: Model, Providers,
     Usage, Tools, Agents, Default shell, Permissions, Keep running, Tips and This phone.
   - The Agent group is empty.
   - Engines with no screen: `TipController` has no Help › Tips page, `DefaultNotices` is unused, and the phrase-chips flag
     has no switch.
2. **The server page is absent.**
   - `ServersRoute` has only `Home`.
   - The server page, connection check, Reach your computer, host update, plugins section and AI setup are all missing.
   - The server switcher sheet is missing too (plan 07, A08).
3. **Two security rules differ from Flutter.**
   - **External links:** `ExternalLinkPolicy` is looser. It accepts `mailto:`, gives `http` the same confirmation as
     `https`, does not refuse a link with no host, and drops the port from the host it shows. This conflicts with
     B-SEC-064 and B-SEC-065.
   - **Plain-HTTP consent:** native asks for consent on any non-loopback `http` address, while Flutter lets
     private-network addresses through without asking (B-SEC-120).
   - Both need an owner decision or a fix in `:core:security` and `:feature:servers`.
4. **Chat pages and sheets are missing.**
   - No `ChatPagesRoute` exists, so Export, Note, Relations and Import are missing, and so are the destination sheet and
     the running-work sheet.
   - Context opens as a sheet, with no Compact action and no session totals.
   - Tool cards in the chat do not show these kind-specific blocks: diff, question, images, prose rendered as Markdown,
     and shell exit words.
5. **Some code is built but not wired.**
   - Not reached from any screen: the form sheet (`KitFormSheet` and `FormRequests`), the CSV/TSV parser, SVG rendering,
     `RunningFamily`, `InFlowConsent` and `ProcessBudgetGuard`.
   - The prompt history in `:feature:chat-composer` is not reached, because the chat uses `BasicComposerHost`.
   - Many strings in `:ui:words` are not used by any Kotlin code. Reviewers checked where each string is used, not only
     whether it exists.
6. **Usage covers only Spending.** It has no budgets, Plans tab, subscriptions, quotas or alerts. No
   `:engine:integrations` module exists for quota providers or provider logos.
7. **Several screens have no route:**
   - Terminal (server and local), Worktrees, Tools and Web sources
   - the three widget panels (`:feature:widget-panels`)
   - the Termux migration (plan F16)
8. **Rules that differ from Flutter:**
   - The question sheet decides between compact and full with a different rule from the Dart one.
   - Running on this phone refreshes every 10 s instead of every 5 s, and has no grouping by owner.
9. **After-switch:** all 59 AI Team files (`team/*`, `team_*` widgets, `grace_timer` and `builtin_team_section`) are
   deferred by plan 07 §H and ADR D6. Native has the ported team words (`strings_team.xml`), the key sweep in
   `DeferredFeatureData.kt` and the team link routing in `engine/entry`.

## Catalog rows

The rows each file covers are listed in its `catalog_rows` field.

Gaps missing from the catalog are listed by file in `ui-c.md` under "Gaps no catalog row covers". Behaviour with no row
includes:

- the folder browser and New project
- phone search
- the remove-OpenCode flow
- the running-work sheet and its shell output page
- the server switcher
- the queued-prompt move sheet
- the connection countdown and its details sheet
- the guided Appearance visit
- the Usage alerts and subscription pages
- the terminal list

The owning units should add rows for these or test them directly.

## Method

1. **Script pass.** A script computed:
   - line counts and the `part of` library for each file
   - the tests that exercise each file: tests that import the file or its library and mention its identifiers, or tests
     named after the file
   - catalog rows, mapped from each row's `test:line` source block. A row was "strong" when the block names the file's
     identifiers, and a "candidate" when it came from those tests.
2. **Parallel review.** Eight reviewers each took about 30 files. For each file they:
   - read the Flutter file
   - searched native production sources for class names, route ids and the English text of its l10n keys
   - checked that the native counterpart can be reached through `*Entries.kt` or the app wiring
   - kept a candidate row only when it is about this file
3. **Merge and validate.** The merge step checked that each file appears once, that every native path exists and that
   every catalog id is real.

## Deviations

- **Android SDK and Gradle:** not installed or run, because this unit changes no code. Its verification is the JSONL check
  above.
- **Status depth:** statuses come from reading the source, not from running the app. The status of a partial file names
  what is missing but does not measure how much of the file is ported.
