# Durable phone store and scheduler

Finish line: authored project commands and admitted task stages survive daemon restart, replay exactly once, reject stale revisions, retain unknown usage, and remain permanently deleted after profile deletion.

Non-goal: HTTP, OpenCode sessions, native lifecycle, repository authority, UI, signing or executing tests; the coordinator owns those integrations.

Read set: `lib/domain/team_project.dart`, `team_project_gateway.dart`, fixture validation, and the engine frozen contract. Write set: `engine/phone/src/store.rs`, `scheduler.rs`, `tests/store*.rs`, and this README only.

## Frozen scheduler contract

`evaluate(project: &Value, job: &Value, jobs: &[Value], context: &AdmissionContext) -> Admission` is pure. `Admission` is `Admit`, `Wait(&'static str)`, or `Pause(&'static str)`. `AdmissionContext` has `now_ms: u64`, `chat_idle: Option<bool>`, `chat_observed_ms: Option<u64>`, `chat_max_age_ms: u64`, `charging: Option<bool>`, `server_online: bool`, `lane_cap: usize`, `utc_day: String`, `observed_total_cost: Option<f64>`, `observed_daily_cost: Option<f64>`, and `observed_task_tokens: Option<u64>`. Its default fails closed. Evidence must belong to the evaluated project/task; daily evidence must be from `utc_day`. Unknown costs stay unknown; a monetary ceiling cannot admit unreported usage.

Store methods match the coordinator's frozen signatures. Errors expose only static codes. `execute` returns `TeamCommandResult` JSON for accepted and semantic rejected commands; infrastructure/invalid envelope errors use `StoreError`. Replays bind the entire canonical JSON command, including revision. A reuse with changed payload returns `requestIdReuse`.

Job objects include id, kind, projectId, taskId, repoId, serverId, roleId, model, fallbackModel, instructions, readOnly, title, spec, criteria, dependsOn, stage, directory, sessionIds, sessionUsage, expectedDevCommit, expectedMainCommit, usage (nullable cost/tokens), and ISO UTC createdAt/updatedAt. Planner jobs use an empty taskId. Active stages recover to interrupted, never queued. Stage patches are compare-and-set and whitelist metadata; `task` carries checked snapshot fields. `sessionUsage` stores cumulative per-role evidence and refuses regressions. Raw server output and credentials are never event metadata. Interrupted work moves only to `resuming`, then observed recorded stages; admission cannot submit it as new work.

Daemon-only repository hooks: `command_result(command: &Value) -> Result<Option<Value>, StoreError>` checks replay before external effects; `execute_with_repositories(command: &Value, imported_repos: &Value) -> Result<Value, StoreError>` binds authored JSON and substitutes only same-id/path/server imports with actual refs; `record_promotion(command: &Value, receipt: &Value) -> Result<Value, StoreError>` writes only confirmed exact-ref physical promotion evidence. A completed task `repoReceipt` patch writes actual merge refs and receipts. Public `execute(promote)` remains unsupported. These hooks do not themselves prove repository authority: only the coordinator's closed daemon pipeline calls them.

Profile deletion keeps a minimal SQLite tombstone, clears authored state, secure-deletes rows, checkpoints/truncates WAL and vacuums. Every mutation checks the tombstone in an immediate transaction. Old handles and subsequent opens cannot recreate the workspace. The owning namespace has mode 0700 and rejects direct namespace/database symlinks; the native boundary owns protection of the root and parent directories.

Daily budget evidence remains date-bound. The coordinator may provide a current-day project view and actual zero ledger evidence only before any dispatch; prior unknown sessions must pause. New-job UI usage remains unreported until observed. Settings and role edits do not rewrite a dispatched job's frozen role/model. Revised plans require unused task IDs so prior job evidence cannot satisfy a new dependency by accident.

## Acceptance and deferred checks

Focused deferred tests cover command replay across reopen, changed request payload, stale revisions, transaction rollback, plan/dependency validation, CAS and recovery, unknown usage, cross-instance deletion/tombstone, profile isolation, event metadata, imported-ref replay, promotion binding and scheduler evidence/caps. Test scratch data uses `OC_ENGINE_TEST_ROOT` or `/home/owner/Storage/tmp/oc-phone-engine-tests`, never `/tmp`.

Verification at the slice boundary: `rustfmt --edition 2021` on the two modules and two test files completed; `git diff --check` completed. Tests, cargo build/check, Flutter checks, analyzer and device checks remain unrun by explicit owner request. No execution or device-readiness pass is claimed.
