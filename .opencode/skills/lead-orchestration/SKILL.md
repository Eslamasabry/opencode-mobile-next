---
name: lead-orchestration
description: Use for decomposing substantial OpenCode Mobile work across agents, assigning ownership, integrating changes, or fast-tracking roadmap milestones.
---

# Lead Orchestration

## Build A Dependency DAG

Parallelism follows independent ownership, not an agent quota. Use multiple
research agents early, then serialize coupled implementation around frozen
contracts.

Every delegated task specifies:

- base commit and protected pre-existing changes;
- read set and exclusive write set;
- explicit files it must not edit;
- upstream dependency or frozen contract;
- acceptance criteria;
- focused verification command;
- handoff format and residual risks.

Keep an ownership ledger in the active task list. Stop if two editors need the
same file.

## High-Coupling Units

These require one owner at a time:

- `lib/state/connection.dart`
- `lib/ui/screens/chat_screen.dart` and every `chat/*.dart` part as one library
- `lib/domain/server_gateway.dart`
- `lib/api/product_repository.dart`
- each protocol model/event/gateway cluster
- `lib/main.dart`
- `lib/l10n/app_en.arb` plus generated localization output
- contracts, generator inputs, and `packages/opencode_sdk/`
- both Dart and native halves of one MethodChannel contract

## Safe Parallel Work

- Protocol evidence, UI behavior audit, state/security audit, platform audit,
  test/accessibility review, and delivery audit can run concurrently read-only.
- Protocol and UI implementation can overlap only after domain contracts and
  capability semantics are frozen and their write sets do not overlap.
- State and platform work can overlap only after their callback/channel contract
  is frozen.
- Quality review remains read-only while implementers edit.
- Flutter test processes run serially even when research agents are parallel.

## Integration Order

1. Snapshot branch, base SHA, tracked/untracked changes, and protected edits.
2. Establish product intent and protocol/security evidence.
3. Freeze domain contracts and capability semantics.
4. Implement protocol adapters and focused tests.
5. Integrate connection lifecycle and persistence.
6. Integrate platform runtime when needed.
7. Build touch-first UI against the frozen surface.
8. Generate localization once after copy settles.
9. Run focused checks serially.
10. Request independent quality and security review.
11. Integrate findings and review all tracked and untracked changes.
12. Run analyzer and the serial full suite.
13. Ask delivery specialist for patch/baseline eligibility only after the diff
    is stable.

All work remains on `dev`. Promotion to `master` is a separately approved major
milestone action; release publication is a separate explicit approval.
