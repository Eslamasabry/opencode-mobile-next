# PR comment and branch review — 2026-09-05

Reviewed GitHub's open PR list, remote branch tips, and all 18 unresolved inline
review threads on PRs #63–75. There were no open PRs at the start of this review.
Comments are checked against current code rather than applied mechanically.

## Comment decisions

| PR | Finding | Decision |
|---|---|---|
| #63 | Catalog refresh can persist stale shortcut pruning | No change. `refreshCatalog` checks generation, API identity and refresh generation immediately before publishing the catalog/pruning. The mutation is synchronous before the persistence await. `_persistModelLibrary` captures that committed profile/snapshot and serializes writes, so newer shortcut writes remain ordered. A newer refresh cannot interleave before that synchronous commit. |
| #64 | Restart snapshot assignment outside `setState` | Apply explicit `setState` at the snapshot assignment. The existing `finally` already schedules a rebuild, but keeping state publication at the assignment makes the behavior clear. |
| #64 | Tool group announces “1 steps” | Use singular “step” for one tool. |
| #66 | URL edit clears probe state twice | Remove the redundant second `setState`; `_invalidateProbe` performs the reset. |
| #66 | Terminal refresh schedules an empty rebuild | Only reset the process state when the location changes. |
| #67 | Back can exit Files with an empty focused search | Handle keyboard dismissal before folder/search navigation; standalone route gating also retains Back while the keyboard is visible. Extend the existing root-exit interaction check. |
| #68 | Complete-history test mixin can recurse | Supply an empty default `messages` implementation and terminate non-null cursors. Transcript fixtures can still override `messages`. |
| #69 | Refresh retains a stale load-more error | Clear `sessionsMoreError` when the refresh starts. |
| #69 | Archived sheet uses the wrong localization context | No change. `context` is the local `ListView.itemBuilder` argument inside the sheet, not the outer State context. |
| #69 | Avoid the direct session read during chat rehydration | Retain it. A chat can remain loaded while outside the newly fetched inventory page; its cached model/revert/location metadata still needs a fresh read on reconnect. `_refreshOneSession` deduplicates matching in-flight reads. A presence-only check would leave stale metadata. |
| #70 | Partial move events erase location metadata | Omit location fields when the destination is absent. A supplied destination still replaces both directory/workspace fields, so moving back to a local directory clears a previous workspace. Omitted project/path fields remain intact; an explicit null subpath can clear it. |
| #71 | Null-aware map entry is invalid Dart | No change. `'removedFrom': ?removedFrom` is valid for the pinned Dart 3.13.2 toolchain; static analysis and compilation accept it. |
| #72 | Request route closing schedules duplicate work | Make `close` idempotent. Routes added after closing still schedule their own removal through `own`. |
| #73 | Read-cache write failure escapes | Catch platform storage failures inside the serialized write. Keep in-memory watermarks and let a later record persist the retained snapshot. |
| #74 | Unexpected event IDs can fabricate colliding notices | Only synthesize transcript message IDs from a nonempty `evt_` suffix. Other IDs still invalidate note state without creating transcript entries. |
| #74 | Instruction notice punctuation can bypass redaction | Match the `Instructions updated` prefix without requiring a colon. |
| #74 | Clear note-write guards on every location reset | Retain scoped in-flight guards. Keys contain location revision and session; `finally` releases each exact key. Clearing them early could permit another write while the original is still running during a same-revision reload. They do not block another location. |
| #75 | Missing repository appears as unsupported usage | Treat a temporary null repository as an interrupted refresh. Preserve retry controls and the previous snapshot; a later refresh can succeed. |

## Branch reconciliation

| Branch | Integration evidence |
|---|---|
| `codex/model-workflow-polish` | Squash-merged in #63. `git diff c0fcf2c b8d19ed` is empty: the entire branch tree is already integrated even though its original commits are not ancestors. |
| `codex/dev-before-master-sync-ca0b249` | Preserved historical backup. Its useful context, reply, attachment and Termux restart changes were adapted in #64; see the adaptation entry in `docs/backlog/cycles.md`. Its old competing Running work and agent/skill packs are intentionally not restored. |
| `codex/message-history-wip-20260905` | Merged in #68, including the preserved pagination work. |
| `codex/session-inventory-pages` | Merged in #69. |
| `codex/session-selection-sync` | Merged in #70. |
| `codex/staged-revert-workflow` | Merged in #71. |
| `codex/request-lifecycle` | Merged in #72. |
| `codex/session-read-state` | Merged in #73. |
| `codex/session-note` | Merged in #74. |
| `codex/aggregate-usage` | Merged in #75. |
| `codex/mcp-setup-scope` | Carries the current MCP correction and these review follow-ups; integrate through its PR and then fast-forward `dev` to `master`. |

No branch, checkout, or QA artifact was deleted. Main stays clean while the
review follow-ups are prepared in the isolated checkout. This review does not
establish final v1 release readiness or publish a stable APK.
