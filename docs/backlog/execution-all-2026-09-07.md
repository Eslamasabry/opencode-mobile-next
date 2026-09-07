# Full backlog execution — 2026-09-07

Maintainer direction: implement the remaining backlog with a dedicated agent
assignment per item. The lead coordinates ownership, audits implementation and
runs integration checks; feature code belongs to the assigned workers. Start
from clean, pushed `2aa1ab0`. Three worker slots are available, so assignments
run in waves. Completed source, enabled behavior, verification, commits and
release remain separate states.

Continue logical commits directly to `dev`, all carrying `[skip ci]`. No PRs,
workflow dispatches, public releases or signing-key changes. Native source and
local build feasibility are included; the installed signer remains fixed.

## Assignment queue

| Item | Usable finish line | Assignment / dependencies |
|---|---|---|
| F3 | Opt-in cross-profile monitor, fresh/unknown inbox counts, safe navigation, quiet/notification rules, deletion | Implemented; focused checks and captures passed after fixes: `setup_feedback_tests` |
| F7 storage/recovery | Real storage observation; explicit persisted, bounded, ownership-checked recovery; stop/delete cancellation | Implemented; focused checks and captures passed: `setup_terminal`; F3 integrated lifecycle/deletion |
| E13 | Supported quota collectors, source-bound thresholds and measurable consumption budgets, fresh opt-in limit attention | GLM/thresholds/budgets implemented and focused checks passed: `consolidation_review`; separate native monitor draft awaits handoff |
| F6 | Android session shortcuts, Connect/New task, attention tile, reviewed inbound draft routing | Queued; native routing ownership after F3 |
| F5 | Per-run evidence-based completion summary and explicitly enabled bounded digests | Queued; depends on F3 notification integration |
| F2 remaining | Explicit plugin-command mapping and versioned app-bundled declarative renderer, safe fallbacks and reviewed actions | Implemented and focused checks/captures passed: `plugin_tools`; F6 shortcut dependency remains |
| E6 | Capability/provider discovery, explicit search, review and editable local source staging before Send | Queued; serialize v2 adapter/chat edits with F2 |
| F4 remaining | Proven v2 command handoff and validated phone deep-link/QR routing | Queued; share frozen routing interface with F6 |
| F9 | Secure discovery/connection assistance and authenticated event-stream validation where test infrastructure exists | Queued; no automatic VPN changes or public exposure |
| F10 Codex | Pinned transport/auth/initialize/thread/turn/approval/cancel/reconnect proof, then usable supported backend | Active proof: `codex_backend`; 0.153.4 schema generated, isolated real-CLI smoke prepared; no adapter enabled |
| F10 pi | Separate pinned project/RPC/topology proof and usable supported backend | Queued; no assumed ACP compatibility |
| F10 ACP | Actual authenticated remote transcript and client integration for a verified backend | Queued; factor shared code only after proof |
| F1 ambient | Explicit per-profile foreground ambient opt-in, bounded audio, route/focus/privacy interruption handling | Queued; chat library ownership required |
| F7 runtime alternatives | Genuine non-proot runtime proof and supported choice/reuse flow without replacing the working installation | `runtime_choices` prepared existing-server/failed-inventory review patch outside repo; native proof unavailable in current proot-traced shell, OC2 CLI absent |
| E7 | Complete copy externalization, locale selection, RTL checks and requested language pilot | Queued; sole l10n writer after feature copy settles; language question pending |
| E11 | Audit remaining advanced surfaces against real callable contracts and implement approved usable workflows | Queued; preserve explicit destructive-worktree/internal-control exclusions |
| E2 | Verify input/session recovery and resolve findings from available process/lifecycle/device checks | Queued; final integrated candidate |
| E3 | Verify provider credential recovery on final supported native/server paths and resolve findings | Queued; separate real account authorization when needed |
| E4 | Verify final MCP removal/reconnect behavior and resolve findings | Queued; isolated server fixture, not the live session server |
| E5 | Verify stash storage/interruption/deletion on final candidate and resolve findings | Queued; source gates plus actual available device evidence |
| E8 | Build/package and exercise desktop targets on available compatible hosts; resolve concrete build findings | Active: `desktop_builds`; Linux ARM64 prerequisites installing, architecture-correct packaging fix drafted; no build yet |
| E12 | Complete remote-client iOS source and native checks on a compatible host | Queued; macOS/Xcode availability is evidence, not assumed |
| E9 | Audit patch eligibility/update UX and perform available rehearsal without changing signing/update policy | Queued; actual Shorebird/native prerequisite required |
| E10 / F8 comprehension | Execute available real-user first-run/journey evaluation and triage findings | Queued; interviews cannot be replaced by synthetic tests |
| E1 | Integrated source gates, local artifact feasibility, same-signer/device evidence and accurate release readiness | Final assignment; publication is a separate state |

## Frozen first-wave interfaces and ownership

F3 owns `connection.dart`, `profiles.dart`, `main.dart`, background Dart/native
bridge and minimal Activity/home integration. `ProfileMonitor` owns bounded
sequential refresh, profile opt-in/rules, runtime/network policy, deletion and
fresh/stale/unknown snapshots. Background work is permitted only while the
already opted-in foreground service is active; it does not enable that service.

F7 owns `lib/termux/`, setup/managed-health UI and its Servers integration.
Storage comes from a bounded strictly parsed Termux read. Recovery is one owner
per managed installation, with persisted bounded attempts, explicit-stop
inhibition, operation/process identity checks and cancellation on deletion.
F3 integrates agreed lifecycle hooks; F7 does not edit shared connection files.

E13 owns quota/consumption controllers, domain models, screens, collector and
synthetic contract tests. Credentials remain server-side; unsupported auth
contracts stay unavailable. Personal thresholds never become provider allowance.
F3 integrates requested scoped key and fixed-copy notification hooks.

F3 froze `MainActivity.kt` and shared controller/native ownership. E13 receives
that ownership after the first commits, followed by F6. New copy is collected
in per-item temporary JSON fragments. E13 merged E13/F3/F7/F2 copy; F2 temporarily
owned its final clear-history keys and then released localization. The dedicated
integration cleanup changed only analyzer-listed braces/imports/comments and a
capture platform override. No worker edits generated SDK code.

## Integration discipline

Each worker supplies its frozen interface, changed-file set, meaningful tests,
UI capture harness and privacy/persistence notes. Workers do not start test or
build processes. The lead schedules focused tests serially, audits user-visible
flows, and returns concrete findings to the owner. A stable integrated boundary
gets analysis and the complete recursive test manifest, with one unchanged
candidate and explicit skips/failures. CI budget remains untouched.

A missing provider contract, native host, signed artifact, account authorization
or participant must be stated precisely. It does not close other runnable source
work, and it is not permission to claim the corresponding evidence completed.

## Focused check boundary — first wave

Source checkpoint: `1f35828`, with logical commits `7f71b29` (copy), `9ae79b4`
(quotas/budgets), `2de4744` (Termux), `d787b29` (monitoring) and `1f35828`
(plugin actions/task view). Every commit carries `[skip ci]`. The following
record is focused verification, not a completed final integration gate.

- Collector: `node --test tool/quota/collector.test.mjs tool/quota/minimax.test.mjs tool/quota/glm.test.mjs` — 49 passed, 0 failed.
- Seven quota/usage Flutter files initially had 127 passes, 1 optional preview
  skip and 2 failures from unrelated widget publication. The controller fix
  removed that publication. Rerunning the two affected files with repaired
  budget captures passed 65 tests, skipped 1 optional preview and failed none.
- Six Termux files passed 67 tests; managed-server light/dark captures passed 2.
  Checks cover three-attempt persistence, native cancellation failures, Stop
  despite preference-cleanup failure, and isolated executable shell policy.
- Thirteen monitor/background/Activity/deletion/widget files initially passed
  88 tests with 7 failures. Product Material/network fixes, real-scroll fixture
  fixes and completed localization resolved them: the five affected files
  passed 28 tests. The monitor capture harness initially stalled after rendering
  Ahem fonts; the lead stopped only its exact test PID. Shared capture helpers
  fixed it and the capture-only rerun passed both themes.
- Five plugin/tool files passed 38 tests. The confirmed profile-wide clear
  action then added capacity/restart/failed-remove coverage; its two affected
  files plus light/dark capture harness passed 19 tests.
- These counts are separate commands with overlap, not one combined full-suite
  count. All Flutter test commands used `--no-pub --concurrency=1`.
- The integration analyzer initially reported 74 lint/import/capture issues.
  Mechanical cleanup resolved them; `flutter analyze --no-pub` reports no issues.
  The managed-server capture rerun after its platform-fixture cleanup passed 2.
- No complete recursive suite, native build or release for this candidate yet.
  Focused Flutter checks use available Flutter 3.47.2; its Shorebird provenance
  has not been verified. Final full-manifest coverage remains required after
  the remaining product integrations settle.
