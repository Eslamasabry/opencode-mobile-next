VERIFIED: green

# P12-BUILD-INV-REST — file inventory of 199 Flutter files (INVENTORY-REST)

Branch `claude/file-inventory-parity-push-61o9yf`, base `native/p12-integrate-2026-11-03` at `71f74de8` (waves 1–7).
Date 2026-11-03. **Analysis only: no product code changed.** Write set: `docs/native/inventory/rest.jsonl`,
`docs/native/inventory/rest.md`, this file.

## What "green" means here

The brief defines green for this unit as: the jsonl covers every listed file and is valid JSON lines. Checked by script:

| Check | Result |
|---|---|
| Every listed file has exactly one line (the list has **199** files; the brief said 198 — the list itself holds 199 unique paths) | 199/199, no duplicates, no extras |
| Every line parses as JSON with the 11 required keys | pass |
| `kind`, `status`, `portability` use only the allowed values | pass |
| Every path in `native` exists in the base tree | pass (checked with `os.path.exists`) |
| Every id in `catalog_rows` exists in `native/catalog/behaviors.jsonl` | pass |
| No `done` line without native evidence | pass |
| No IP addresses or e-mail addresses in the diff | pass |

**No Gradle build or test was run**, because this unit changes no code (docs only, AGENTS.md check ladder: "docs-only
changes need a diff/link check"). The Android SDK was not installed for that reason. Product modules are untouched,
so the app compiles exactly as the base does.

## Result in one table

| status | files |
|---|---:|
| done | 41 |
| partial | 69 |
| missing | 36 |
| not-needed | 16 |
| after-switch (incl. desktop-phase) | 11 |
| dropped | 26 |
| **total** | **199** |

- 461 distinct catalog rows are attributed to these files.
- 57 gaps are marked **[uncataloged]**: behaviour that is missing or partial natively and that no catalog row
  describes. They are listed by owning module in `rest.md` ("Gaps no catalog row covers").
- 23 files have no Flutter test exercising their main behaviour (`untested: true`).
- Portability: {'platform-specific': 71, 'agent-portable': 108, 'needs-design': 20}.

## Where the missing/partial work lands (files per target module)

- `:protocol:oc2`: 19
- `:engine:diagnostics`: 10
- `:engine:entry`: 9
- `:core:net`: 7
- `:engine:runtime`: 7
- `:feature:widget-panels`: 6
- `:engine:attention`: 5
- `:engine:integrations`: 5
- `:protocol:codex`: 5
- `:android:platform`: 5
- `:engine:project`: 4
- `:engine:schedules`: 3
- `:android:tiles`: 2
- `:android:background`: 2
- `:ui:words`: 2
- `:feature:settings`: 2
- `:engine:media`: 2
- `:feature:voice`: 2
- `:engine:connection`: 1
- `:feature:about`: 1
- `:core:common`: 1
- `:feature:schedules`: 1
- `:android:schedules`: 1
- `:feature:linux`: 1
- `:android:termux`: 1
- `:android:voice`: 1

## Main findings for the integrator

1. **Whole modules the plan names do not exist yet**: `:protocol:codex` (all 5 `lib/codex` files missing),
   `:engine:integrations` (BYO host: `lib/host/*` 4 files; provider quota), `:engine:project` (phone project scan,
   phone storage folders, All files access), `:android:termux` (Termux move-out), `:feature:widget-panels`
   (the 6 `lib/widgets_overlay` files: floating style/project/steer/voice panels).
2. **Engines exist but are not wired into the Android app**: `engine:entry` (`EntryRouter`, `AppBootstrap`,
   `OpeningGate`) is not used by `MainActivity`; widget action/status runners and `LiveWidgetDispatch` are not bound;
   the automatic first-mic voice setup engine has no caller; crash capture and exit history are not fed on device;
   the shared `Pacer` is bound to constant foreground visibility, so hidden-app pacing never applies.
3. **Background live mode** (`lib/background/live_background.dart`, 1,003 lines) has no `BackgroundConnectionService`
   / foreground-work implementation natively; the status notification content is not computed.
4. **OpenCode 2 gateway surface is narrower than Flutter's**: forms, inbox steer/queue, web search, terminal
   WebSocket, MCP, project management, development services, VCS status and the global event channel are switched
   off in the native capability matrix. The automatic-approval path is never enabled (uncataloged).
5. **No session-link intent filter** in the app manifest, so session links never reach the app;
   launcher/tile actions other than Connect and New task land on Conversations.
6. **Transport errors**: DNS failure is folded into "connection refused" (`OkHttpEngineFactory.classify`), so the
   app cannot say "the address did not resolve" (uncataloged).
7. **Diagnostics**: no persisted problem report across restarts, no HTTP/navigation perf spans, no GitHub issue link.
8. **Termux**: 13 files are dropped (Termux as a runtime host, D7/NG7); several switch-scope rows were only tested
   through those files (e.g. B-LINUX-231, B-LINUX-075..077, B-LINUX-241..243, B-SEC-092) and have no native test
   yet — the `gaps` of those lines name them so the Linux owner can re-home them.
9. `scope.jsonl` names the schedules module `:engine:schedule`; the real module is `:engine:schedules`. This inventory
   uses the real name.

## Method

1. Mechanical pass (script): line counts; Flutter tests importing each file (`package:opencode_mobile/...`, part files
   resolved to their library); catalog rows whose `sources` are those tests, kept only for close-named or
   narrow-import tests to avoid attributing rows from tests that import dozens of files.
2. Per-file pass (8 parallel readers, ~25 files each): read the Flutter file, search `native/**` for its classes,
   strings, routes, endpoints and channel names, decide status strictly (`done` only with named native files), prune
   and top up catalog rows (including row ids cited by native tests), mark untested files and uncataloged gaps.
3. Merge and validation script (checks above), then `rest.md` generated from the jsonl.

## Deviations

- The brief says 198 files; the list contains 199 unique paths, all covered.
- `desktop-phase` files (`lib/desktop/*`, `lib/update/desktop_release_check.dart`) use status `after-switch` with
  "desktop-phase" in `gaps`, since the status vocabulary has no desktop value.
- `catalog_rows` come from `native/catalog/behaviors.jsonl` (the superset including top-up rows), not only the
  1,544-line copy under `docs/design/`.
- No Android SDK install and no Gradle runs (docs-only unit, see above).

## For the integrator

- Nothing to wire: no module, route or Gradle change.
- Merge `docs/native/inventory/rest.jsonl` alongside the other six inventory files; the key set matches the brief, so
  the seven files can be concatenated into one inventory.
- Consider adding top-up catalog rows for the [uncataloged] gaps in `rest.md` before the owning units port them.
