# Migration step 4 — Flutter screens, part 3

Counts: **already there 0 · ported now 0 · not needed 11 · blocked 0** (11/67 reviewed; remaining rows follow in compiling batches).

Base: `b66708729a70d5f3b873db27cafcfa1b1cc14308`; branch `p12x/40-port-screens-3`.
Implementation comparison only. No tests, APK, device qualification, push, signing or release.
“Not needed” for deferred features describes this native implementation scope under binding plan/01; it is not an approval to pass the eventual switch gate (plan/01 §10.1).

Finish line: every supplied file has a behaviour-based disposition, and each implemented gap is reachable with real native wiring. Non-goal: test or runtime parity qualification.

`lib/ui/screens/team/project_demo_screen.dart` — not needed: Persisted isolated AI Team simulation/demo, deferred with AI Team by binding plan/01 §12 D6; original data stays untouched.
`lib/ui/screens/team/project_destination.dart` — not needed: Request/task routing into the AI Team workspace, deferred with AI Team by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/screen/board_timeline.dart` — not needed: AI Team milestone task lanes, dependency graph and orchestration timeline, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/screen/overview.dart` — not needed: AI Team planning, lane scheduling, approval requests, digest, merge/promotion and budget overview, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/screen/overview_helpers.dart` — not needed: AI Team request answers, orchestration status/cost helpers and confirmed project deletion, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/screen/servers_card.dart` — not needed: AI Team executor lane limits and task relocation between team servers, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/team_merge_flow.dart` — not needed: AI Team checked-task merge-to-dev and promotion-to-main receipts/confirmations, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/team_project_editors_roles.dart` — not needed: AI Team plan recovery, task dependencies and planner/worker/checker role/model editing, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/projects/team_voice_words.dart` — not needed: AI Team orchestration personas and spoken task-state copy, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/role_screen.dart` — not needed: AI Team role creation/edit/reset/delete and assigned worker/task views, deferred by binding plan/01 §12 D6.
`lib/ui/screens/team/team_board_screen.dart` — not needed: Gas City/AI Team work-board filters, confirmed moves and orchestration task creation, deferred by binding plan/01 §12 D6.

Checkpoint 1: documentation only (11 deferred AI Team files); compile gate skipped because this commit changes no Kotlin or Gradle file. Diff whitespace check passed.
