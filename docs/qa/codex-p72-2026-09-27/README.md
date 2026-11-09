# P7.2 queued prompt preservation — backend unit

Finish line: expose a counted removal snapshot, durable kept drafts, and an explicit destination-session move plan, so the controller owner can preserve queued work before removing a server.
Non-goals: queue engine changes, UI, automatic sending, protocol/network changes.

Read set: AGENTS.md; STANDARDS.md sections 2, 3, 13, 15; offline_queue.dart, prompt_shelf.dart, profiles.dart, connection.dart (read only), KitRedact.
Write set: lib/state/queued_prompt_removal.dart, test/queued_prompt_removal_test.dart, this record and verification output.
Dependency: existing queue and shelf models. No new network/authentication contract is needed. Controller integration remains a separate owner's work.
Acceptance: count all source-profile entries; preserve content through restart and source deletion; no silent eviction; explicit destination sessions; reject stale confirmation and uncertain sends; fail closed on corrupt/refused storage.
Checks: pinned Dart format, focused Flutter behaviour test, final analyzer if executable. No full suite, native build, device or release work.

## UI hook-up

Public API: `lib/state/queued_prompt_removal.dart`. Construct one long-lived
`QueuedPromptRemoval(preferences: store.prefs, queue: existingQueueStore)` per
controller. Do not create a second live queue writer.

- `inspect(profileID)` returns `QueuedPromptRemovalPlan`: `count`, `uncertainCount`,
  and `prompts`. The confirmation must name `count`, including uncertain sends,
  and offer Keep as drafts, Move, or Cancel. Do not log/print the raw snapshot.
  Zero is a valid count; unreadable queue is an error, never zero.
- `keepAsDrafts(plan)` durably copies redacted complete queue records and returns
  the count protected. Retry is idempotent. Original queued entries remain until
  the controller's ordinary deletion commits. Full/corrupt/refused storage or
  stale confirmation throws; keep the server and offer retry. A copy completed
  before a later failure stays visible; retry must not overwrite an edited copy.
- `moveToSessions(plan, destinationProfileID:, availableProfileIDs:,
  destinationSessions:)` returns the complete replacement queue without writing
  it. Map each source prompt ID to a destination session ID: source session IDs
  cannot be reused on a different server. Resolve/validate sessions and existing
  model/agent selections through the domain gateway before entering the local
  transaction; confirm the destination still exists. Missing destinations,
  same-server moves, uncertain sends, and file/content/blob attachments fail
  closed. Offer Keep for those cases. Moved entries preserve IDs, text,
  attachments, mentions, model/agent/variant and timestamps; old server error is
  cleared. Existing queue engine rules apply after the move, including automatic
  sending when eligible. No artificial dispatch marker is added.
- `savedDrafts` provides `StashedPrompt` rows for the Saved prompts UI **in
  addition to** its ordinary profile shelf, including when no server is selected.
  `keptPrompts` supplies matching queue metadata and uncertain-send status;
  `QueuedPromptRemoval.draftID(prompt)` identifies its row. These are app-owned
  drafts, never an automatic send source. Do not pass their IDs to ordinary
  profile-shelf restore/remove methods: these entries are stored separately.
  Restore the chosen text/attachments into a composer only after destination
  review; preserve the current composer draft first. File/content URLs may no
  longer resolve, so disclose unavailable filenames and offer partial restore.
  Never treat an old server's file URL as a destination server's file. Review
  mentions after text redaction because stored offsets may refer to the original
  text. Redaction can change text/URLs; disclose that credentials are masked.
- `forgetDraft(id)` explicitly removes a kept entry after the person's action;
  failed writes are errors. `reload()` reconciles storage after failure. Refresh
  the UI after awaited operations (there is no background mutation/stream).

### Required controller-owner integration

`connection.dart` is read-only for this unit. Its existing
`_deleteProfileAndLocalData` queue step currently drops source-profile entries
without consulting a preservation choice. **That behavior remains unchanged
until the coordinator wires this API.**

Extend the removal orchestration to accept the confirmed plan and explicit
choice. Suspend admission and flush for source and destination, drain in-flight
queue writes/sends, and perform preservation inside the existing
`_serializeQueueChange` deletion step **before** filtering source entries.
Compare the live queue/cache with the durable snapshot; call `validateCurrent`
before the destructive step. If anything changed since confirmation, abort and
ask for a new confirmation with the new count. Never silently refresh a plan.

For Keep, await `keepAsDrafts`, then filter the source entries and use the
existing checked queue save/cache update. For Move, use the returned replacement
queue as the basis for that checked save and install it into `_offlineQueue`;
only then continue the existing deletion sweep. Recheck destination existence
under the same exclusion. A false queue save must abort profile removal. Do not
wrap the public delete method in another queue lock: it acquires that lock itself.
Hold admission/flush exclusion through the full deletion attempt, release it in
`finally`, and surface partial cleanup errors through the existing result.

This hook is necessary for race safety; calling the service from a screen and
then separately calling the old deletion method is **not** a safe integration.
The service does not claim to provide that controller exclusion.

## Persistence, privacy and deletion

New key `oc.keptQueuedPrompts` holds a JSON list of existing `QueuedPrompt`
records. It is deliberately app-owned after an explicit Keep choice, so removal
of the source server cannot delete it. No migration of existing queue or shelf
formats. Existing profile-scoped keys/sweep are unchanged. Saved rows retain
source profile/session IDs solely as local provenance; no server credentials
are copied from profiles. Every string in each record passes through KitRedact
before persistence; identity redaction rejects the operation. No logging.
The store refuses more than 50 entries or 60 MiB of encoded records, with no
silent eviction. Draft deletion uses `forgetDraft`; a future app-wide erase
must also remove `QueuedPromptRemoval.draftsKey`. No shared attachment-vault
files are created; inline payloads stay in the retained record.

## Verification and state

Base: `64128dba`, branch `codex/p72`; initial tree clean. Only the new state
service, its behaviour tests, this QA record and command outputs were changed.

Implemented: backend preservation and move-preparation API. Enabled: no;
controller/UI owner integration above is pending. Verified: source/diff review
and formatter rewrite only; behaviour tests and analyzer are **not run**.
Committed: see final response/COMMIT_MSG.txt. Deployed/released: no.

Focused tests cover counted keep/retry, restart and real profile-key sweep,
redaction, attachment/metadata retention, explicit draft removal, move routing
and other-profile preservation, stale/corrupt input, uncertain/local-attachment
move refusal, storage refusal/retry, conflicting drafts and capacity refusal.
No widget/golden/device evidence is claimed; no UI files were edited.

Commands attempted with pinned toolchain:

```sh
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart format --language-version=3.10 lib/state/queued_prompt_removal.dart test/queued_prompt_removal_test.dart
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/queued_prompt_removal_test.dart
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter analyze lib test
```

Flutter launchers fail before execution because their engine stamp/realm cache
is read-only; see [test output](test-output.txt) and
[analyzer output](analyze-output.txt). Package configuration is also absent.
The pinned bundled `bin/cache/dart-sdk/bin/dart` parsed and formatted both changed
files with `--language-version=3.10`; it then failed writing telemetry to a
read-only directory. See [formatter output](format-output.txt). No analyzer or
runtime success can be inferred from formatting. A verifier must run the focused
test and full lib/test analyzer with writable toolchain/package caches.

Contract problems: no new remote contract. Product enablement is pending the
explicitly excluded connection/UI owner hooks above; do not mark P7.2 delivered
from this backend unit alone.

Commit attempt: `git add` failed creating the worktree `index.lock` because
`/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p72` is read-only.
Nothing was staged or committed. The exact requested message and trailers are
in repository-root `COMMIT_MSG.txt`. Working files remain available for the
coordinator. No push attempted.
