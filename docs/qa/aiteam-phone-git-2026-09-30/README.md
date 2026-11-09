# Native phone repository authority

Finish line: the native daemon owns canonical `main`/`dev`, seeds independent task clones without canonical origins, imports untrusted task commits, integrates checked commits serially, and records exact confirmed promotion intents/results durably across restart.

Non-goal: implementing or claiming the Android kernel/process boundary, launching workers, UI integration, remote credentials, signing, publishing, or executing tests in this owner-deferred session.

## Ownership and contract

This slice owns only `engine/phone/src/repository.rs`, `engine/phone/tests/repository_authority.rs`, and this evidence file. Cargo/module exports and API/scheduler integration belong to the coordinator; the kernel denial harness belongs to the native owner. Contract: [frozen engine README](../aiteam-phone-engine-2026-09-30/README.md). Decision: [engine decision](../../design/aiteam-engine-decision-2026-09-30.md).

`RepositoryAuthority` implements all seven frozen methods. Repository/task/request IDs use 1–128 ASCII letters, digits, `_`, or `-`; commit parameters are exact 40-hex SHA-1 IDs. Errors expose only a stable code, never native paths, libgit2 errors, credentials, or input contents. Roots are absolute, disjoint, and reject symlink ancestors. The daemon must supply private and rootfs worker roots outside/inside the proven native boundary respectively.

Returned fields:

| Method | JSON fields |
| --- | --- |
| `import_repo` | `repoId`, `mainCommit`, `devCommit`, `before`, `after` |
| `refs` | `repoId`, `mainCommit`, `devCommit` |
| `prepare_worker` | `repoId`, `taskId`, `workerPath`, `branch`, `devCommit`, `taskCommit`, `before`, `after` |
| `collect_worker` | `repoId`, `taskId`, `devCommit`, `baseCommit`, `taskCommit`, `before`, `after` |
| `merge_dev` | IDs, `devCommit`, `mainCommit`, `taskCommit`, `before`, `after` |
| `promote` | `repoId`, `receiptId`, `devCommit`, `mainCommit`, `before`, `after`, `state` |

Each `before`/`after` object contains `devCommit` and `mainCommit`; import's before values are null because no canonical repository existed. Worker preparation/collection report unchanged canonical refs. An idempotent promotion returns its original receipt values, even if a later task advanced dev. `workerPath` is the native host rootfs path; the daemon/OpenCode driver must map it to the guest directory before prompting a worker session. Branch names are `task/<taskId>`.

## Implemented invariants

- The daemon uses libgit2, never shell Git. Canonical repositories are bare, private, and carry independent objects. Import snapshots metadata without following symlinks, refuses linked repositories/local origins/alternates/includes, permits a network import origin only to discard it, and never requests credentials or fetches that origin. Import keeps existing main and dev when dev descends from main; otherwise it refuses.
- Workers are built privately from canonical dev through anonymous Git-aware transport and exported through directory descriptors. They have no canonical main/dev refs, origin, alternates, linked metadata, or `FETCH_HEAD` canonical path. Object files are copied rather than hardlinked. A saved private seed binds repository/task identity independently of agent changes.
- Collection copies `.git` with `openat`/`O_NOFOLLOW` descriptor reads into a private bounded snapshot before libgit2 opens it. The copy refuses symlinks, hardlinks, special files, alternates, include directives, extensions, local origins and all worker remote sections. Config is replaced and hooks removed before native reads. Byte/file/depth caps fail closed. Untrusted changes during collection can cause refusal; the collected commit is pinned by SHA and must descend from the private saved seed.
- `collect_worker(expected_dev)` checks current canonical dev; worker ancestry uses its original `baseCommit`. A merge queue refreshes canonical refs before collection, then gives those exact returned dev/task SHAs to `merge_dev`. Both fast-forward and conflict-free two-parent integration are supported. Stale refs and merge conflicts refuse. Main remains unchanged. Canonical mutations serialize through a private cross-process `flock`, with Git ref transactions locking both protected branches.
- Promotion requires confirmation, exact current dev/main, and a fast-forward from main. A private receipt binds the global request ID to repository, confirmation and both SHAs. Durable JSON intent uses a new mode-0600 file, file fsync, atomic rename and parent fsync before main changes. Objects/refs are synced; acknowledgement follows the durable main ref. Retry reconciles a prepared receipt with before/after refs. Before a newer promotion can advance main, earlier prepared intents are reconciled; a changed exact dev supersedes an unapplied earlier request, and ambiguous main state refuses with `promotion_recovery_required`.
- Worker `reference-transaction` and `pre-receive` hooks reject main writes, with no token exception. Agent hooksPath/config/direct-ref bypasses are expected to affect the worker only. The native authority never runs untrusted hooks. **Hooks do not establish filesystem ownership.** Host tests deliberately demonstrate bypass; actual canonical write denial from inside the sandbox is the native owner's separate proof.

## Deferred verification

The owner explicitly deferred test execution. No Cargo build/check/test, Flutter test/analyzer, device boundary proof, or performance result is claimed by this slice. Rust formatting and `git diff --check` were performed only.

The test file contains 14 focused proofs for independent clone metadata/objects; direct, symbolic-HEAD, shell-tool and hooksPath/direct-file writes; pre-receive refusal; canonical main preservation; successful confirmed promotion and request binding; stale SHA refusal; parallel task merge/conflict behavior; before/after-ref crash receipts; later-dev restart replay and receipt resolution before a newer promotion; malicious alternates/origins/includes/symlinks/hardlinks; invalid IDs/overlapping roots/worker-parent pivots; symbolic task refs and unrelated history; and import-origin stripping.

When authorized, run serially from `engine/phone`:

```sh
OC_ENGINE_TEST_ROOT=/home/owner/Storage/Code/oc_app-phone-engine-test-artifacts \
  cargo test --test repository_authority -- --test-threads=1
```

This harness requires host Git and POSIX shell for hook/tool proofs. Its default fixture directory is `engine/phone/.qa-artifacts` under the Storage checkout; it rejects `/tmp`. Kernel isolation remains a separate native boundary harness; these repository tests do not attest that an agent process cannot see native-private paths.

Implemented: repository authority and deferred proof tests. Enabled: only through coordinator/native integration and proof gates. Verified: formatting and diff whitespace only. Committed: recorded by the slice handoff. Deployed/released/pushed: no.
