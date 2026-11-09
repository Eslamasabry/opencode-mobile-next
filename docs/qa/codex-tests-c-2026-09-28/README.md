# Codex tests-c — 2026-09-28

Latest verification: see [merge follow-up](#merge-follow-up-tests-d-integration). Earlier batch counts and source locations describe the pre-merge snapshot.

Finish line: repair the assigned non-golden tests against the intentional September 27 UI changes; preserve meaningful failures for product-owner handoff. Non-goals: production edits, golden refresh, gate or baseline changes, publication.

## Prerequisite integration

Merged `feat/phone-setup-v2` at `dbac9c48` into `codex/tests-b` (previous head `93aab671`). The two conflicts were test-only resolutions:

- `team_controls_test.dart`: P3.6's worker conversation composer replaces the removed message sheet; the agent page offers one Open conversation action. Kept tests-b receipt, capability, manual-reassignment removal, and 320dp/2.5x layout checks. Dismiss the overflow menu before replacing its screen in the layout fixture.
- `builtin_server_autostart_test.dart`: kept both fake setup engines and the voice-device probe mock alongside incoming autostart checks.

Serial pinned-Flutter merge verification (`flutter test --no-pub --concurrency=1 <file>`): team_controls **31/31**, builtin_server_autostart **6/6**, kit_ratchet **34/34**, redaction **16/16**, ui_glossary **21/21**, no_raw_error_text **5/5**. Initial team-controls run was 29/31 because the fixture retained its open menu across screen replacement; corrected fixture rerun is 31/31. Those original `/tmp/codex-tests-c/merge-*.jsonl` logs were lost in the host crash; recorded counts survived. Final gate reruns are recorded below.

The merge includes upstream product, baseline and image changes. This lane did not author those changes or regenerate images; subsequent tests-c edits are restricted to tests and this evidence record.

## Chat batch result

Tests-only repairs follow the intentional September 27 changes. Genuine product failures remain enabled for their owners; this is not an all-green suite or a release approval. `context_capsule_chat_test.dart` was already deleted by P3.3 and was skipped as absent.

**Totals:** before **346 pass / 154 fail / 3 existing skips**; after **710 pass / 24 fail / 3 existing skips**. The original 503 cases now have 17 product failures (137 previously failing cases repaired). Expanded overflow coverage adds 234 cases, including seven more product failures. Counts are per-file focused evidence, not a full repository run.

The host crash lost the original `/tmp` baseline logs, but completed before counts survived. Interrupted work was rerun; no interrupted result is counted as a pass. Raw JSON logs and the serial runner are on the persistent volume at `/home/owner/Storage/tmp/codex-tests-c-20260928/`. Before counts for the previously in-flight overflow matrix come from its completed `recovered-before` rerun. Counts exclude hidden setup/loading events; skip counts are separate.

| File (`test/`) | Before pass / fail / skip | After pass / fail / skip | Evidence phase | Repair / retained behavior |
|---|---:|---:|---|---|
| `chat_live_events_test.dart` | 72 / 33 / 0 | 103 / 2 / 0 | `final` | Kit menus, diff/viewer, accessible attachments, context presentation, prompt editor and delegation; payload checks retained. |
| `product_ui_regression_test.dart` | 5 / 22 / 0 | 23 / 4 / 0 | `round3` | Files/review/terminal kit routes, copy announcements, CSV and thinking menus; four product guards retained. |
| `stable_chat_layout_test.dart` | 3 / 7 / 0 | 10 / 0 / 0 | `verified` | Kit editor/controls and actual viewport; focus, draft and geometry checks retained. |
| `chat_reference_send_test.dart` | 2 / 4 / 0 | 6 / 0 / 0 | `after` | Kit editor and combined draft warning; references and exact send payloads retained. |
| `offline_queue_test.dart` | 52 / 5 / 0 | 57 / 0 / 0 | `verified` | Secure-storage fixture, reachable kit actions and documented Undo lifetime; durable queue behavior retained. |
| `chat_transcript_placement_test.dart` | 13 / 4 / 0 | 17 / 0 / 0 | `after` | Current reply/user containers and kit text; alignment and nonoverlap retained. |
| `read_aloud_test.dart` | 7 / 1 / 0 | 8 / 0 / 0 | `verified` | Current consent dialog and settled controls; consent/background draft checks retained. |
| `desktop_context_menu_test.dart` | 4 / 1 / 0 | 5 / 0 / 0 | `verified` | Current file row menus and viewer navigation; open and copy behavior retained. |
| `desktop_selection_test.dart` | 1 / 1 / 0 | 2 / 0 / 0 | `verified` | Phone reply-local KitSelectable and desktop transcript-wide selection; real clipboard checks. |
| `release_blockers_test.dart` | 19 / 4 / 0 | 22 / 1 / 0 | `round4` | Kit actions, real Privacy and data viewer, inline About licences and share menu; compact prompt failure retained. |
| `codex_chat_capabilities_test.dart` | 6 / 3 / 0 | 9 / 0 / 0 | `verified` | Kit attachment/menu interactions; unsupported actions stay disabled and no transport is called. |
| `chat_menu_hierarchy_test.dart` | 1 / 1 / 0 | 2 / 0 / 0 | `after` | KitSwitchRow toggles and current menu navigation; persisted behavior retained. |
| `chat_server_state_ui_test.dart` | 8 / 1 / 0 | 8 / 1 / 0 | `after` | Unchanged; provider-auth navigation exposes page primary-action defect. |
| `chat_states_standard_test.dart` | 4 / 2 / 0 | 5 / 1 / 0 | `after` | Current state actions; original technical Details requirement retained. |
| `nudge_moments_test.dart` | 12 / 8 / 0 | 18 / 2 / 0 | `after` | Kit notices, current copy, real viewport and reachable actions; approval overflows retained. |
| `text_scale_overflow_test.dart` | 101 / 2 / 0 | 329 / 8 / 0 | `final` | Manifest utility classification strengthened; 54 missing exported kit parts covered with real state fixtures. Existing sizes, scales and zero-overflow baseline unchanged. |
| `voice_composer_test.dart` | 3 / 3 / 0 | 6 / 0 / 0 | `round3` | Kit voice radio semantics, primary-first layout, real viewport and bundled license viewers. |
| `voice_model_localization_test.dart` | 7 / 2 / 0 | 9 / 0 / 0 | `after` | Current kit controls, settled scrolling and localization delegates; Arabic reachability retained. |
| `voice_reply_pipeline_test.dart` | 0 / 20 / 3 | 19 / 1 / 3 | `round4` | Current consent/switch and scrollable reply controls; speech dispatch, stale reply and opt-in assertions retained; three existing optional capture skips unchanged. |
| `pending_sends_strip_test.dart` | 11 / 5 / 0 | 16 / 0 / 0 | `verified` | Actual queue menu and fresh draft for long press; explicit delivery default, remembered choice and queue safety retained. |
| `model_picker_test.dart` | 3 / 23 / 0 | 22 / 4 / 0 | `final` | Retired options dialog migrated to P3.3 model sheet, Thinking menu, reload-provider row and secure-storage mock; persistence and race guards retained. |
| `transcript_search_test.dart` | 7 / 2 / 0 | 9 / 0 / 0 | `after` | Kit text selection/highlighting; search and no-reparse assertions retained. |
| `team_agent_chat_test.dart` | 5 / 0 / 0 | 5 / 0 / 0 | `after` | Unchanged; current worker-conversation tests already pass. |

`context_capsule_chat_test.dart`: absent before and after. `app_exit_recovery_test.dart`: merge left one duplicate import; removed for clean analysis. An identical duplicate-import cleanup was needed in `offline_queue_test.dart`.

The overflow matrix adds real widgets and modal entry points for previously uncovered exports, plus a classifier regression and the existing gate's required long-label segmented scene. It covers static states across the same nine sizes, three text scales and both directions (54 combinations per scene); dedicated kit tests retain interaction-state coverage. No placeholder scene, new skip, error suppression, golden refresh, gate relaxation or baseline increase is used. The manifest now accepts each known non-widget utility only with its expected superclass, and still counts it if changed into a widget.

## Intentional-change evidence

- [P3.3](../slice-P3.3-2026-09-27/README.md), `3fcace2c`: one model sheet, Thinking footer menu, provider reload row, capsule deletion and friendly headline/raw Details split.
- [P3.6](../slice-P3.6-2026-09-27/README.md), `af07cc7a`: worker is its conversation; agent page has one Open conversation action.
- [Chat 3](../revamp-chat-3/README.md), `e28442b0`, and [Chat 9](../revamp-chat-9-2026-09-27/README.md), `592d7c50`: kit composer, TextFormField, empty mic, busy Queue default, context chip, menu and search placement. [Shared chat](../revamp-shared-chat-1-2026-09-27/README.md), `fa9fc679`: KitSwitchRow and retired options.
- [KitMarkdown](../revamp-kit-KitMarkdown-2026-09-27/README.md), `79d941e4`: SelectableText expectations intentionally became stale. [Chat 1](../revamp-chat-1/README.md): phone-local selection with desktop-wide transcript selection retained.
- [Files](../revamp-screen-files-1-2026-09-27/README.md), `6f21d378`, [Review](../revamp-screen-review-1-2026-09-27/README.md), and [Terminal](../revamp-screen-terminal-1/README.md): kit viewer and menus, file row tap, separate diff glyphs, terminal key strip and named confirmations.
- [Voice](../revamp-screen-voice-1-2026-09-27/README.md), `83af7522`: primary-first actions, radios and per-row licence viewers. Chat consent `e52f11c4` and automatic voice `5c36473d` explain the current reply flow.
- [P3.10](../slice-P3.10-2026-09-27/README.md): About tabs folded into the page; policy lives in Privacy and data.

## Product-owner handoff

All cases below remain enabled and failing; production files are untouched. Source locations are for the merged product snapshot `8f94659e`.

- **Provider authentication recovery:** `lib/ui/screens/library/integrations_screen.dart:784`, `:1350`, `:1417` each build an inline error state with a primary retry action when providers, MCP and resources all fail to load. Opening Providers from the chat auth error (`chat_server_state_ui_test.dart`, provider-auth recovery scenario) trips the three-primary assertion at `lib/ui/kit/kit_screen.dart:652`. P3.3's QA record already identifies this pre-existing issue. Fix the page's action hierarchy; the gate is unchanged.
- **Permission plus approval tip:** `lib/ui/screens/chat_screen.dart:7006` (`_aboveComposer` Column) overflows its bottom by 43 pixels on the default 800×600 test viewport when the third same-kind permission request surfaces the approval tip. `nudge_moments_test.dart` scenarios “the third request of one kind points at Approvals” and “closing it removes it for good” both reproduce it. The no-overflow checks remain active; chat owner must fit the combined content.

- **Missing load diagnostics:** `lib/ui/screens/chat/chat_states.dart:57` passes `productErrorText` to the error state's Details. In `chat_states_standard_test.dart`, “a conversation that could not load…” expects the original safe technical diagnostic under Details; it gets the friendly headline again. `productErrorDetails` is the documented diagnostic path. Final state-file check is **5 pass / 1 fail**.
- **Model sheet does not close with a search:** `lib/ui/widgets/pickers.dart:137` uses `maybePop` after applying. `lib/ui/kit/kit_search_field.dart:463` intercepts that pop while a query exists, clears it and leaves the sheet open. Both “model selector searches and persists…” and session-selection reopening retain their failed dismissal assertions.
- **Thinking-menu race:** `lib/ui/widgets/pickers.dart:395` resyncs an untouched draft after an external model change, but the open thinking menu still describes the previous model. Its callback at `:783` applies that previous model's variant to the new model. “the thinking menu keeps edits bound to the displayed model” retains its identity assertion.
- **Dismiss while model apply is pending:** `lib/ui/widgets/pickers.dart:142` disposes the apply notifiers when the sheet closes, while the exiting body can still finish `_applySelection`; its finally block at `:1331` calls `_setApplying` (`:412`) on the disposed notifier. The existing authorized-agent-choice/dismissal test remains enabled.

- **Home large-text toolbar:** `lib/ui/kit/kit_top_bar.dart:946` lays out the home context in a horizontal Row that overflows by 127 pixels on the 360×740, 2.5× home-shell scenario in `text_scale_overflow_test.dart`. The test now includes the original Flutter diagnostic as its failure reason, preserving the no-exception assertion.
- **MCP command destination:** the live-events `/mcps` command reaches IntegrationsScreen, where simultaneous MCP/resource load failures independently create retry primaries (`lib/ui/screens/library/integrations_screen.dart:1350`, `:1417`). This is the same page-action hierarchy defect as provider-auth recovery, with two sections instead of three; its navigation test remains enabled.

- **Completed context with unknown limit is treated as loading:** `lib/ui/screens/session_context_screen.dart:604` supplies null usage with a known 1,000-token value label. `lib/ui/kit/kit_progress_row.dart:156`, `:171` and `:423` classify null as loading and omit that known value. The live-events native context-destination assertion is retained.
- **Voice permission arrival creates two primary actions:** `lib/ui/screens/chat_screen.dart:7070` renders permission attention (`chat/permission_sheet.dart:168` → `kit_request_card.dart:1019` Allow), alongside voice controls at `chat_screen.dart:7203` (`chat/voice_conversation.dart:483` Listen). A permission while waiting for a spoken reply trips `kit_screen.dart:652`; `voice_reply_pipeline_test.dart` “late reply stays silent after approval” remains enabled.

- `lib/ui/kit/kit_undo.dart:229` inserts the Undo bar in the root overlay. `lib/ui/screens/files_screen.dart:1034` shows it after staging a file; reopening the changed-files sheet leaves that bar above the row. The actual hit-test path for `changed-file-lib/main.dart` reaches `files-staged-notice` / `_KitUndoBar` instead. “changes card opens the changed set…” retains its hit-testable assertion; waiting out Undo would conceal the obstruction.
- `lib/ui/kit/kit_search_field.dart:174` schedules the debounce; `:292` forwards IME submission without cancelling it. Both call `FilesScreen._onSearchChanged` at `lib/ui/screens/files_screen.dart:1209`, so typing `ProjectHealth` then submitting immediately issues the same query twice. “symbol search opens the exact source line…” keeps the single-request expectation.
- `lib/ui/screens/terminal_screen.dart:546` and `:555` reload after rename/remove completes without checking the captured location. The two stale-callback tests change workspace/repository while the operation is pending and observe an unwanted extra reload of the new location. Both original call-count guards remain.


- **Prompt editor button blocked at large text:** `lib/ui/kit/chat/kit_composer.dart:621` places the bottom toolbar directly after the field; `lib/ui/kit/kit_field.dart:756` gives the composer zero inner padding. In `release_blockers_test.dart`, type a draft at 320×640, 2× text, keyboard inset 180: the prompt-editor button's center is intercepted by `_SelectionHandleOverlay`. The actual hit-test owner chain is captured and the reachability assertion remains; hiding the handle would conceal the failure.
- **48dp composer chips overflow:** `lib/ui/kit/chat/kit_composer_chips.dart:501` scales icons while the glyph-only Row at `:539` holds both icons and padding. The documented narrow fallback overflows by 12px at 1.3× and 20px at 2× in `KitComposerChips/narrow`.
- **Loading diff exceeds short landscape:** `lib/ui/kit/kit_diff_view.dart:1256`, `:1272` uses a fixed header and six skeleton rows. `KitDiffView/loading` overflows the bottom by 26px in the 412dp-high landscape case.
- **Shell status overflows:** `lib/ui/kit/kit_top_bar.dart:946`, `:957` has an unflexed status label. `KitShellControls/{connected,reconnecting,not_answering,needs_you}` overflow at narrow widths and large text; reconnecting reaches 57px in RTL at 320dp/2×. Same root cause as the home scenario above.
- **Segmented long labels do not stack:** `lib/ui/kit/kit_segmented.dart:335` stays a horizontal Row, and `:428` uses ellipsis instead of the KIT-24 stacked choice rows. The new long bilingual labels scene exercises the pre-existing `labelsOverflow` gate without modifying it.

## Verification and remaining work

Pinned Flutter/Dart were used throughout. Dependencies: `flutter pub get --offline`. Formatting: `dart format --language-version=3.10` on changed Dart files. Each listed file was run separately with `flutter test --no-pub --concurrency=1 <file>`; the lead serialized Flutter work, workers did not launch test processes. Full repository/golden tests are outside this non-golden file batch; no full-suite pass is claimed.

The full overflow run completed at **329 pass / 8 fail**. An isolated follow-up replaced runtime-type string matching with generic-safe KIT-24 predicates and extended the existing selftest. Both affected tests were rerun (`kit24-targeted.jsonl`): selftest passes; the segmented scene still fails, now correctly reporting zero stacked choice rows in eight phone combinations. Other matrix code/scenes did not change; the full matrix was not repeated after this isolated helper repair. The table preserves the completed full-run count and this follow-up evidence separately.

Final checks:

- `flutter analyze --no-pub`: **No issues found** (`analyze-final.log`).
- `kit_ratchet_test.dart`: **34/34**; `redaction_test.dart`: **16/16**; `ui_glossary_test.dart`: **21/21**; `no_raw_error_text_test.dart`: **5/5** (`final-<file>.jsonl`).
- Pinned Dart format verification: **27 files, zero changes**, language version 3.10. `git diff --check` clean; local documentation links resolve.
- Tests-c diff relative to merge `8f94659e`: tests and this README only. No production, golden PNG or baseline-file edits; no new skips or ignores. Commits carry `[skip ci]` and the requested attribution/session trailers. No push.

Repair commits: `19fbb4f7` reference/menu/localization; `036cf28c` transcript/capabilities; `d2f8ad75` queue/desktop; `0bfe00e5` voice/nudges; `dfd55589` product UI; `b1f78307` voice pipeline/release; `68f23ddd` model sheet; `3d34b1e9` overflow coverage; `5bb46ad3` live events. The final evidence commit also removes the two redundant imports discovered by analysis.

**Left for product owners:** fix the 24 enabled failures detailed above, then rerun those scenarios/files. This test lane made no product fixes because the chat/team and state libraries are concurrently owned. The reviewed golden refresh remains separate. No unexplained stale failure remains in this batch.


## Merge follow-up: tests-d integration

Merged `feat/phone-setup-v2` at `90db3565` into `codex/tests-b` at `b6fcc8ba`. Resolved all seven conflicts while retaining both jobs' repairs:

- `lib/ui/setup_commands.dart` exactly matches the incoming branch, including both private `IFS= read -rsp` password commands.
- Chat and home fixtures retain the incoming shared connection-status scope and prior behavior assertions. Home attention fixtures now include the saved profile required by the profile-scoped feed; disconnected fixtures stop polling before timer checks. Its initial merge run was 18 pass / 7 fail; corrected rerun is 25/25, with all seven expectations retained.
- Nudges use the incoming `KitNotice.offer` while preserving prior reachability checks.
- Both overflow scene sets remain. Incoming additions use `tests-d-` state IDs to avoid collisions; builders, hosts and KIT-24 flags are unchanged. Deleted product-state wrappers (`LoadingList`, `ProductEmptyState`, `ProductErrorState`, `ProductInlineEmpty`, `SectionLabel`) and the retired `KitSecretField` have no scenes. Strict manifest inheritance and typed generic KIT-24 detection remain.

Only conflicted test files and the four requested gates were run, serially with pinned Flutter, `--no-pub --concurrency=1`. The conflicted scene registry is exercised through the overflow test, not as a standalone test entrypoint. Persistent logs: `/home/owner/Storage/tmp/codex-tests-c-20260928/merge2-*.jsonl`.

| Test | Pass | Fail |
|---|---:|---:|
| chat_live_events | 103 | 2 |
| chat_states_standard | 6 | 0 |
| home_navigation | 25 | 0 |
| nudge_moments | 18 | 2 |
| text_scale_overflow | 498 | 4 |
| kit_ratchet | 34 | 0 |
| redaction | 16 | 0 |
| ui_glossary | 21 | 0 |
| no_raw_error_text | 5 | 0 |

The five conflicted test entrypoints total **650 pass / 8 fail**. The eight retained product failures are: unknown-limit context token display and MCP primary-action hierarchy (live events); two approval-tip overflow scenarios (nudges); narrow composer chips, loading diff, and both jobs' long-label segmented scenes (overflow). All remain enabled; no gate was weakened or baseline raised. The incoming branch fixes the prior standard-state diagnostic failure and home/shell status overflows.

`flutter pub get --offline` succeeded. `flutter analyze --no-pub`: **No issues found** (`merge2-analyze.log`). Pinned Dart formatting at language version 3.10: all seven resolved files clean. Conflict-marker and staged diff checks pass. No golden generation, unrelated test run or push.
