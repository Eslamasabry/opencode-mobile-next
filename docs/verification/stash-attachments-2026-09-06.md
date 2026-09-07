# Stash attachment migration — backend groundwork

Base: `84e1e0f` plus the protected iOS/Claude follow-up. This is **development
source, not an activated migration or a completed E5 feature**. No real user
store was read or migrated. The earlier quota/analyzer/partial-suite results
predate these storage changes and do not validate them.

## Implemented boundary

- `PromptShelfStore.withAttachmentFiles` is an explicit opt-in backend using
  `prompt-stash-attachments-v1` under application support. The existing default
  constructor remains inline; `ConnectionController` has not switched to it.
- Metadata retains the same `oc.promptStash.<profile>.<stashId>` keys, IDs,
  timestamps, text, location and review references. `attachmentFormat: 1` and
  `attachmentRefs` replace inline payloads only after file publication and a
  successful preference write. Unknown formats and ambiguous records fail
  closed. A legacy-only consumer refuses file-backed records rather than
  pretending they have no attachments.
- The stash root is separate from ordinary drafts and pending photos. The
  deliberate limits are **five attachments, 32 MiB encoded data URLs per stash,
  and 256 MiB per stash vault**, shared across profiles within that root—not
  shared with the draft/photo disk budgets. The existing 50-entry limit remains;
  there is no automatic age expiry or silent eviction.
- Migration returns deferred entry IDs on storage/format/capacity failure and
  leaves their legacy JSON intact. It never truncates an oversized legacy
  entry, downloads URL references, refreshes credentials, or emits local file
  paths as server attachment URLs. Temporary grants remain explicitly
  unavailable during recovery, not falsely converted into durable attachments.
- A shelf-wide transaction queue covers file writes, metadata commits,
  migration, recovery and collection. Inputs are snapshotted before queueing.
  Preference refusal/exception reloads the optimistic cache; a failed reload
  blocks cache-dependent operations and garbage collection until recovery.
- Recovery names missing/corrupt/wrong-location attachments without consuming
  the source. Collection refuses unknown/corrupt metadata and is owner-scoped.
  Profile cleanup removes metadata first, reports file-cleanup failure, and
  can retry orphan cleanup when metadata was already removed.
- Shared vault reads now enforce the expected size while streaming instead of
  trusting a pre-read file length. Negative metadata sizes cannot reduce the
  recovery budget. This shared helper change also affects draft/photo paths
  and needs their focused regression checks before integration is considered
  verified.

## Ownership and tests

State/security reviewed storage and migration boundaries; quality reviewed the
existing failure/deletion fixtures. Both had empty write sets. The UI research
slice was unavailable; the lead inspected the stash sheet and retains ownership
of the complete chat part library and `connection.dart`.

The lead's implementation set is `prompt_shelf.dart`, `draft_attachments.dart`,
`test/stash_attachments_test.dart` and these notes. New synthetic tests cover
metadata/file ordering, restart, idempotence, failed publication/metadata,
uncertain-cache GC suppression, isolated ownership, partial recovery, cleanup
retry, legacy limits and queued-input snapshots. The new file alone was run:

```sh
flutter test --no-pub --concurrency=1 --reporter expanded test/stash_attachments_test.dart
```

**17 focused tests passed**. Changed Dart files were formatted and
`git diff --check` passed. No existing suite was rerun for this backend slice;
there is no new analyzer or full-suite claim for E5, nor controller/UI/native
migration evidence yet.

## Required next integration

1. Add asynchronous controller/sheet migration and recovery, including visible
   deferred-migration and missing-payload feedback. Lists use metadata names and
   counts, not loaded data URLs.
2. Capture/recheck profile, location revision and destination session around
   awaits. Cancellation or a scope change must not replace the composer; save
   the destination draft before removing a completely restored stash.
3. Block new profile writes before drain/cleanup, invoke `clearForProfile` in
   the deletion cascade, retain the profile/secret on incomplete cleanup and
   clear stale shelf caches even on partial failure.
4. Only then switch the live writer and update privacy/migration copy. Older
   binaries do not understand this format: assess rollback explicitly before
   distribution rather than treating it as a transparent downgrade.
5. Run the focused stash/draft/photo/deletion checks once the slice is wired;
   reserve analyzer/full-suite verification for the agreed batch boundary.
