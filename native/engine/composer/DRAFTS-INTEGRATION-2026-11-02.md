# Conversation text drafts — 2026-11-02

Finish line: type, acknowledge autosave, restart, and restore; confirmed send clears only its captured revision; deleting a profile fences its writers before removing its drafts. Attachments and Flutter migration are not part of this preview integration.

## Engine contract

`ConversationDrafts(Drafts, AppCoroutineScope, AppDispatchers)` performs no constructor reads. `open(ref)` restores on the injected serial dispatcher. Until restoration succeeds, editing is refused; a corrupt or unavailable store remains unchanged and publishes a typed failure. Restoration uses the existing `Drafts.restoreFor` policy. The feature supplies its additional delivery/history guard before allowing restored text to send.

Each canonical `DraftSession` provides `DraftState`, `changed`, `flush`, `captureSubmission`, `clearAfterSend`, and `close`. `DraftState.editRevision` is the exact observable revision used by submission tokens; `restoreRevision` changes only for initial restoration or an acknowledged external clear. Ordinary keystrokes do not reset selection. Both public value types redact draft words in `toString`.

- Reuses `DraftAutosave` and the real `Drafts` store. Its internal revision callback distinguishes acknowledgement of a captured snapshot from newer typing. The 600 ms timer runs in the same injected serial lane; replacing a timer never cancels an admitted write.
- Exact text-only writes preserve whitespace. The existing general draft API retains its Flutter-compatible blank-clears behavior.
- Saved attachments are explicitly refused by this text-only facade. A second guard inside the real draft writer also refuses attachments added after restoration, so neither an empty editor nor an acknowledged clear silently drops their metadata or payloads.
- Submission tokens belong to one handle by object identity. A newer edit, including changing away and back to identical words, makes an older token harmless. Equivalent captures within one current revision reuse a token; ordinary edits retire obsolete tokens.
- A valid confirmed-send clear removes the displayed submitted revision and then waits for durable deletion. New typing during that write remains separate unsent work. If deletion fails, the old acknowledged words stay hidden, the handle is blocked, and retry flushes the deletion rather than saving the sent words again. A restart still requires the feature's canonical-history/receipt guard; this engine does not invent delivery evidence.
- Display close starts an app-scope flush with a bounded wait. Timing out does not cancel an already admitted platform write. Reopening keeps a failed clear protected. Empty closed handles are retired atomically so a simultaneous reopen cannot split two writers.
- Profile closure fences handles before dispatcher admission, waits for the store's serialized cleanup, and refuses all late resurrection. Explicit `reopenProfile(ProfileId)` recovery waits for earlier cleanup, verifies the profile/index still exists and is readable, and installs a fresh writer generation; old handles and queued old writes remain fenced, including inside the real store lane. Unattributed legacy rows with several possible profile owners refuse cleanup without changing anyone's bytes. Cleanup failure leaves metadata available for recovery.

The public state view narrowly opts into `ExperimentalForInheritanceCoroutinesApi`: it directly projects the same atomic snapshot used for capture, without a separate lagging cache or a process-lifetime collector per closed chat.

## Verification

The coordinator observed the initial 14 behavior tests red against a compiling refusal scaffold: 13 failed and only the redacted-diagnostics case passed. The initial full composer suite then passed: 135 tests, zero failures and zero skips.

The final refinement adds observable edit-revision/token-lifetime coverage, a slow-write display-close regression, explicit profile recovery and queued-writer fencing: 19 focused cases total. GLM 5.3 review additionally exposed three legacy store defects; all three were observed red (3 tests, 3 failures): a whitespace save dropped null-preserved files, a wrong-typed preference looked absent, and a throwing read escaped the direct store API. Null attachment edits now always retain files (only an explicit replacement list changes them), and present wrong-typed/unavailable indexes remain unreadable and unwritable without exceptions escaping the read boundary. Final counts and combined feature/runtime/emulator qualification are recorded by the coordinator after its final check. No Gradle, Java, test process, commit, push or device operation was launched by this engine worker.
