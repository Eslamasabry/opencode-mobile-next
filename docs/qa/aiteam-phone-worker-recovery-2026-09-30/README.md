# Undispatched worker clone recovery

Finish line: an explicitly resumed task that durable backend state proves was never dispatched can discard its abandoned clone and private seed record, then receive an independent clone of current canonical dev without changing canonical refs.

Non-goal: resetting a task with any known/uncertain session or prompt dispatch, changing ordinary preparation, implementing store/API resume decisions, UI/native changes, executing tests/builds, or additional authored-tree policy work.

Ownership: `engine/phone/src/repository.rs`, the new `engine/phone/tests/repository_worker_recovery.rs`, and this README. Other repository, store, daemon, native and UI changes remain owned by their editors. This follows the coordinator's R1 abandoned-clone finding: interrupted-before-create jobs may return to queued, but an exported clone must not make that explicit retry fail forever with `worker_exists`.

## API and trusted caller contract

```rust
pub fn prepare_fresh_worker(
    &self,
    repo_id: &str,
    task_id: &str,
) -> Result<serde_json::Value, RepoError>
```

The return schema is the same as `prepare_worker`: `repoId`, `taskId`, native `workerPath`, `branch`, `devCommit`, `taskCommit`, and unchanged canonical `before`/`after` refs. `prepare_worker` continues returning `worker_exists` for an existing clone and never deletes it.

Only the trusted daemon may invoke the new API after its durable queued-to-starting admission proves all of the following: `sessionIds` is empty, `promptDispatch` is empty, and no `sessionCreateUncertain` marker exists. Explicit store resume is permitted only for an interrupted, undispatched job. A known session, recorded prompt or uncertain create must remain interrupted/reconcileable; **the daemon must never call this reset API for that job**. Repository state cannot establish that session proof, so the caller guard remains mandatory and is owned/tested by the coordinator. The API is not an agent command or a general HTTP reset endpoint.

## Safety and crash behavior

- Strict IDs and the private authority `flock` cover reset and recreation together. Canonical repository existence and direct main/dev refs are checked before deletion. Any existing collected task ref returns `worker_reset_refused`, since it contradicts the required undispatched evidence.
- Cleanup opens the worker root and repository parent without following symlinks. `fstatat(AT_SYMLINK_NOFOLLOW)` classifies entries, `openat(O_PATH|O_DIRECTORY|O_NOFOLLOW)` pins each directory, and every unlink uses the parent descriptor. Top-level, parent and nested symlink pivots are refused; no worker-derived absolute path is passed to recursive deletion.
- Mode-000 and readonly directories are made removable via `/proc/self/fd/<pinned-directory-fd>` before opening their contents. This Linux/Android magic link refers to the verified directory inode, not the untrusted pathname, so a concurrent rename/symlink replacement cannot redirect chmod into canonical storage. Regular files are unlinked rather than opened or chmodded. Depth and entry limits bound cleanup. A renamed directory inode or concurrent nonempty replacement refuses with `repository_changed`.
- The private worker seed record must be a regular singly linked file with matching repo/task identity. It is removed descriptor-relatively and its directory fsynced after clone cleanup. A clone exported before seed persistence, a seed left after clone removal, and an absent first clone are all supported. The new seed is atomically persisted by ordinary fresh preparation.
- New worker objects are transported independently from current dev; no linked metadata, canonical refs, local origin or alternates are introduced. Canonical main/dev remain unchanged. Collected refs and promotion receipts are never deleted.
- Cleanup refusal may leave an undispatched clone partially removed; it does not issue a prompt, remove its seed prematurely, or change canonical refs. The caller keeps the job failed/interrupted with the stable redacted error. A crash during cleanup or between cleanup/recreation may be retried only under the same durable undispatched guard.

## Verification

Authored seven focused tests covering current-dev reseeding with dirty/untracked residue and sibling preservation; crash after export before seed persistence; stale seed without clone/first preparation; readonly and mode-zero nested directories; task and parent symlink pivots; nested canonical symlink refusal; and collected-task/private-binding reset refusal.

Rust formatting and `git diff --check` were run. No test/build process was launched by this slice; the coordinator owns focused execution and the daemon known-session guard test. Fixtures use Storage through `OC_ENGINE_TEST_ROOT` or the checkout's `.qa-artifacts`, and reject `/tmp`.

When the coordinator runs the checks:

```sh
OC_ENGINE_TEST_ROOT=/home/owner/Storage/Code/oc_app-phone-engine-test-artifacts \
  cargo test --test repository_worker_recovery -- --test-threads=1
```

Implemented: authority API and focused proofs. Enabled: only after trusted daemon integration. Verified: formatting/diff only in this slice. Committed: recorded in the handoff. Pushed/deployed/released: no.
