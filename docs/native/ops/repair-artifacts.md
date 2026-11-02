# Preserve repair bundles before cleanup

Implemented with OpenCode's `zai-coding-plan/glm-5.3` worker on the build VPS, then hardened during coordinator review. The final suite has 18 passing regression tests on the VPS (2026-11-02).

The old repair runner removed worktrees when their branch had not advanced. Workers with read-only shared Git metadata had saved their commits as bundles instead, including two bundles under those worktrees' `native/build` directories. Removing the worktrees removed those bundles. Both were recovered from their temporary Git repositories.

`preserve-repair.py` preserves an explicitly selected bundle and a receipt outside its source worktree. It does not run a worker, import commits, update refs, push, or remove any worktree.

## Usage

```bash
python3 docs/native/ops/preserve-repair.py \
  --repository /path/to/surviving/repository \
  --bundle /path/to/worker/native/build/repair.bundle \
  --archive-dir /path/to/durable/repair-archive \
  --worktree /path/to/worker
```

The repository must resolve the bundle's prerequisite commits. Keep that repository or another copy of those prerequisites: an incremental bundle is not a complete backup by itself.

Always supply `--worktree` when deciding whether its removal is safe. If omitted, the tool tries to discover the Git worktree containing the source bundle; a source outside Git cannot identify a different worktree that the caller intends to remove. The archive must be outside every directory scheduled for cleanup. Symlinked input paths are resolved before the nesting check.

## What a successful result proves

1. A private temporary snapshot of the source bundle was copied and fsynced in the archive directory.
2. That exact snapshot passed `git bundle verify` against the selected repository. Its heads, prerequisites, byte count, and SHA-256 describe the same verified bytes.
3. Those bytes were published as `<stem>-<sha256>.bundle` without replacing an existing file. A competing destination with different bytes is refused.
4. A JSON receipt was fsynced and published without replacement, followed by directory fsync. The JSON printed on stdout matches the validated, persisted receipt. Existing valid provenance wins when the same bytes arrive from another source path. A corrupt or contradictory receipt is refused.

Changing the source after snapshot verification cannot change the archived bytes. Repeating the same command preserves the first artifact and receipt. Failed preservation leaves the source alone and removes only the tool's own temporary files.

Exit codes:

| Code | Meaning |
|---|---|
| 0 | Verified artifact and validated durable receipt are available. |
| 1 | Preservation was refused or an I/O/durability step failed. Keep the source worktree; do not treat leftover archive files as a success receipt. |
| 2 | Invalid CLI arguments. |

A nonzero exit can leave a complete archived bundle without a valid receipt. Retry or investigate; never infer permission to clean up from the bundle filename alone.

## Runner integration

The prior runners remain stopped. This utility does not change or restart them.

For each completed job, determine where its commits actually live. If its branch did not advance, inspect its completion report for a bundle or temporary Git repository; zero branch commits does not mean there is no work. If only a temporary repository remains, export its intended commit range to a bundle first.

Run this utility and require exit 0 before considering any separately authorized cleanup. Record the receipt and archive location in the job report. Import the preserved commits into an isolated integration branch, inspect their scoped diff, and rerun the affected checks. Preservation does not establish correctness of the code inside the bundle.

## Checks

```bash
python3 docs/native/ops/test_preserve_repair.py -v
```

The tests use temporary Git repositories and cover incremental prerequisites, corrupt/missing bundles, nested/non-directory archives, idempotence, conflicting destinations, survival of source removal and subsequent import, odd paths, source replacement during verification, publication races, bad existing receipts, first-provenance retention, fsync failure, and inferred worktree boundaries. They do not use real devices, servers, or credentials.
