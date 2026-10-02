# Command receipt frontend contract

Finish line: a supported queued prompt or OpenCode 2 session creation is dispatched once, its admission receipt survives restart, and Retry checks that receipt without another send.
Non-goals: UI changes, a relay/host/service, blanket idempotency for arbitrary mutations, assistant completion receipts, automatic migration of already-dispatched legacy prompts.

## What is wired

`ConnectionController.flushOfflineQueue()` writes a metadata-only receipt before dispatch for gateways advertising `ServerCapabilities.commandReceipts`. OpenCode 1 uses its existing correlated prompt API; OpenCode 2 supplies the prompt's inbox ID. The queue still persists its dispatch marker first. Reconnect's existing flush hook checks marked queue entries even with automatic sends disabled. Confirmed admission removes the queued entry; lookup failure or absence preserves it. Servers without the capability retain their existing queue/refetch behavior.

The connection library has one editor on this branch. Outside queue.dart its only connection-library change is imports. The v1 correlated gateway adds an optional synchronous admission fence after its asynchronous preflight; ProfileStore adds the journal close/drain to its existing deletion sweep, and capability declarations opt in the proven OC prompt contracts. No UI, generated SDK, native bridge, live server, credential, signing, or release changes.

## Public backend seam

- `commandReceiptsFor(sessionID)` returns immutable metadata for the active profile/location/conversation. Queue tab ownership is the session ID. Consumers filter by conversation; never show receipts in the currently selected different tab.
- `checkQueuedPromptReceipt(queueID)` performs lookup only; true means admission confirmed AND the queue removal persisted. False means unchanged/unknown; it is not permission to send. Storage problems throw a generic `CommandReceiptException`.
- `resendQueuedPrompt(queueID)` delegates to receipt lookup when a journal record exists, even if the current gateway no longer supports receipts. Legacy entries without receipts retain the existing explicit resend behavior.
- `queuedPromptSending`, `queuedPromptAcceptedUnrecorded`, queue review counts and existing listeners still apply. Receipt check changes notify through queue changes. There is no standalone receipt notifier; the coordinator must await an explicit check and refresh the view.
- The neutral `CommandReceiptController(journal).send(receipt, dispatch)` durably claims the command before invoking dispatch; repeated calls for that command never invoke dispatch again. `retry(commandID, lookup)` only reads. A new frontend online-send integration must create and retain its command ID **before** the first attempt, keep the original profile/session/tab/scope binding, use the same transport after reconnect, and close/drain through ProfileStore. Direct existing online sends are not rerouted by this backend slice.
- `SessionCreationReceipts(store: profileStore, gateway: currentGateway, profileID: id)` is the neutral state seam for OpenCode 2 creation. Check `.supported`; call `send(commandID: retainedOpaqueID, tabID: originatingTabID)` once, keep the returned metadata, and `check(commandID)` after uncertainty/restart. Check only reads and returns the original `Session` or null; open it under the receipt's originating tab, never whichever tab is selected later. An invalidated profile/endpoint/location binding returns null; storage failure throws. The existing `createSession()` path is unchanged until its owner integrates this seam. OpenCode 1 session creation stays unsupported. On app restart, recover typed metadata through `PendingCommandJournal.forProfile(profileStore.prefs, profileID).read()`: filter `create:` command IDs by the restored originating tab ID, strip that prefix to call `check`, and restore/open only the original session. Include a confirmed creation whose tab has not yet persisted its resolved session mapping. Never substitute a new command ID merely because the UI lost its in-memory request; tab restoration and that mapping are the UI owner's persistence responsibility.

## States and suggested copy

| State | Plain copy | Action |
| --- | --- | --- |
| sent | Checking whether your message arrived… | Check again; no resend |
| uncertain | We couldn't confirm your message arrived. | Check again; keep or discard local draft |
| confirmed | Message received. | No send action; assistant work may still be running |
| failed | This request wasn't sent. | Only for independently proven pre-dispatch failure; never inferred from HTTP errors |

Use “Check again” for the receipt action. If existing UI labels it “Retry”, it must call the receipt checker, never clear the marker or call promptAsync. A missing receipt can be a request still in flight, an unavailable lookup, cancellation/deletion, or server data loss; it stays uncertain. No “not sent” assertion from 404, timeout, or equal/absent transcript text. A positive HTTP acknowledgement confirms admission only.

Technical text belongs under Details: command/receipt IDs, sent time, and “The connection ended before we could confirm receipt.” Never include prompt body, URLs, headers, provider keys, or raw exception strings. Announce status changes accessibly with kit parts; add localized English/Arabic copy in the UI owner's slice. No screenshots are required from this backend-only branch.

## Persistence, privacy, lifecycle

Journal schema v1 is a JSON envelope at `oc.pendingCommands.<profileId>`. It contains command/receipt/session/tab IDs, a SHA-256 hash of profile endpoint and location scope, timestamp, and state. Prompt bodies remain only in the existing offline queue; receipts duplicate neither payload nor credentials. No receipt logging. IDs must be opaque identifiers, never user copy. Storage corruption, refusal, or capacity exhaustion stops sending. A sent record loaded after restart remains conservative until checked.

At most 1000 receipts / 1 MiB per profile; terminal receipts remain as tombstones to prevent ID reuse. No expiry or silent eviction. At capacity, keep the draft and explain storage cannot safely record a send. Profile deletion synchronously closes new journal writes, drains existing writes, then the generic profile key sweep deletes it. An app-wide queue clear/discard does not delete receipt tombstones. This is SharedPreferences platform persistence, not a proof against device/filesystem data loss or rollback; don't promise exactly-once execution.

## Owner decisions

1. Online commands: route prompt sends and the optional OpenCode 2 creation seam through this layer in the controller/UI owner's slice, or ship queue receipt checking first (recommended). The backend contract is implemented; the whole online product journey is not enabled here.
2. Unsupported mutations/backends: keep current explicit review/refetch behavior (current implementation), or disable resend for every uncertain operation. No captured universal idempotency key exists for permission answers/reverts/shell; do not invent one.
3. Capacity: keep conservative bounded tombstones (current implementation), or commission a transactional database journal with explicit backup/retention semantics before offering pruning.
4. Transport: keep all app remote access Tailscale/SSH-only. This layer uses existing authenticated transports and adds no network service. Public source research is not an app remote access path.

Evidence and version-specific limits: [feasibility matrix](command-receipts-feasibility.md). Never enable by flavor alone or claim a command receipt proves successful assistant completion.
