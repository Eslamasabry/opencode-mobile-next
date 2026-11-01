Verdict: **BLOCKER — this range does not compile against its core contracts and contains draft-loss and credential-persistence bugs.**

Static review of `43e65935..6c83fe9a`. No edits, builds, tests or mutations were run.

1. **BLOCKER — Removed core failure types prevent compilation.**  
   [Drafts.kt:6](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/Drafts.kt:6)  
   **Problem/evidence:** Composer imports `StorageProblem` and constructs `Failure.Storage` throughout drafts, stash, vault and their tests. Neither exists in `:core:common` at `6c83fe9a`; the contract defines `Failure.StorageRefused` and `Failure.Media`. The report’s branch-local green tests do not establish that the merged range compiles.  
   **Fix:** Map failures to the existing core hierarchy and update assertions. Verify the merged module with `compileKotlinDesktop`, Android compilation and `desktopTest`.

2. **MAJOR — Autosave cancels its own save and loses the pending snapshot.**  
   [DraftAutosave.kt:53](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/DraftAutosave.kt:53)  
   **Problem/evidence:** The debounce job calls `flush()`, which cancels that same job, clears `pending`, then calls `drafts.save()`. Real file operations use cancellable `withContext(io)`; they can throw before persistence, bypassing the code that restores a failed snapshot. A subsequent `flush()` finds nothing pending and returns `Ok`. The memory preferences and payload stores do not suspend, concealing this defect in the 600 ms test.  
   **Fix:** Separate timer cancellation from save execution. Retain a versioned pending snapshot until acknowledgement; preserve it on cancellation. Test with a suspending preferences/payload implementation, including leaving while autosave is active.

3. **MAJOR — Saved prompts persist credentials without sanitization.**  
   [PromptShelf.kt:166](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/stash/PromptShelf.kt:166)  
   **Problem/evidence:** The secret check covers only id, directory and workspace. Prompt text, filenames, reference snippets/comments and encoded text attachments reach preferences or vault files unchanged. Registering a canary secret with the injected `Redactor` does not change this. This violates **B-SEC-151**, which requires redaction of saved copy, encoded text payloads and filename metadata.  
   **Fix:** Sanitize the complete snapshot before externalization: reject sensitive routing identities, redact copy and metadata, decode/redact/re-encode supported textual data URLs, and preserve binary media. Assert against both persisted JSON and decoded vault content.

4. **MAJOR — A refused deletion can destroy the prompt and discard its Undo.**  
   [PromptShelf.kt:182](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/stash/PromptShelf.kt:182)  
   **Problem/evidence:** `remove()` commits metadata deletion before collection. If collection fails, it returns `Failed`; `deleteWithUndo()` consequently returns that failure without the captured Undo. Reproduce by saving an attached prompt, setting `MemoryPayloadStore.failLists = true`, then deleting it: the prompt key disappears, no Undo is returned, and retry cannot find the source. This contradicts **B-STATE-085**.  
   **Fix:** Once deletion commits, return the Undo and track cleanup failure separately for retry, or restore the original metadata before reporting deletion failure. Add a failure injected specifically after metadata removal.

5. **MAJOR — File read/list failures escape the `Outcome` boundary.**  
   [FilePayloadStore.kt:32](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/vault/FilePayloadStore.kt:32)  
   **Problem/evidence:** `list()`, `length()` and `read()` do not translate filesystem `IOException`s. `AttachmentVault.store()` catches only its custom exceptions, so an unreadable vault directory or existing payload makes `Drafts.save()`/`stash()` throw instead of returning a storage failure. An uncaught autosave exception also bypasses pending-state recovery.  
   **Fix:** Translate expected I/O failures consistently across all payload operations, preserving cancellation. Test throwing filesystem implementations rather than only custom failures from `MemoryPayloadStore`.

6. **MAJOR — Failed photo publication leaves bytes that discard and profile cleanup skip.**  
   [PhotoRecovery.kt:220](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/photo/PhotoRecovery.kt:220)  
   **Problem/evidence:** `complete(..., path = null)` writes the photo blob before committing its reference. If that final commit fails—or the process dies between those steps—the durable record still has neither `ref` nor `path`. `discardLocked()` therefore skips collection and removes the record. Later `clearForProfile()` also returns without collecting when no record exists. Private photo bytes remain orphaned and consume the root budget.  
   **Fix:** Collect the recorded owner’s photo root regardless of those optional fields, and make profile cleanup collect that owner even without a pending record. Test failure immediately after blob publication followed by restart and cleanup.

7. **MAJOR — Editing a legacy draft deletes its attachments.**  
   [Drafts.kt:168](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/Drafts.kt:168)  
   **Problem/evidence:** With one profile, `draft()` restores an unattributed legacy draft through the session-only key. A subsequent text-only `save(..., attachments = null)` reads previous attachments only from the new profile/session key, obtains none, removes the legacy entry at line 185, then collects its blobs. Thus an ordinary text edit permanently loses restored files despite the documented “null keeps saved files” contract and **B-STATE-101**.  
   **Fix:** Resolve the effective previous draft consistently during saving. Preserve or safely migrate its attachment ownership before removing the legacy entry and collecting files. Test legacy restore → text-only edit → restart → exact attachment recovery.

8. **MAJOR — File-only share intents inherit unrelated saved text.**  
   [Drafts.kt:259](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/Drafts.kt:259)  
   **Problem/evidence:** Only nonempty `initialText` takes the replacement branch. Sharing attachments alone falls through and restores the previous draft’s words alongside the shared files. Plan **09 §10.8 rule 2** requires either shared text or attachments to replace the saved draft for that opening. `DraftsTest:222` checks only attachment recovery, leaving the unwanted text unasserted.  
   **Fix:** Take the replacement branch when either initial text or attachments exist; assert the complete restored state for file-only shares.

9. **MAJOR — Stash acknowledges attachments that cannot survive restart.**  
   [PromptShelf.kt:133](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/stash/PromptShelf.kt:133)  
   **Problem/evidence:** Only data URLs pass through `AttachmentVault.store()`. Every other scheme becomes metadata directly, including `content:` or `blob:`. Stashing returns `Ok`, but restoration rejects those schemes, so the acknowledged saved prompt cannot recover its full input. The vault already implements the missing refusal. This breaks **B-CHAT-088**.  
   **Fix:** Validate every attachment through the vault before publishing metadata. Assert unsupported-scheme refusal leaves no acknowledged stash and preserves the caller’s input.

10. **MINOR — Three tests miss the defects their titles claim to detect.**  
    These are static mutation-sensitivity checks, not executed mutations:

    - [DraftsTest.kt:124](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/composer/DraftsTest.kt:124): Removing the attachment snapshot copy would still pass. The list changes before the default `async` body runs, and the only final assertion observes a later deletion. **Fix:** Gate an earlier save, start the tested save undispatched, mutate its input while queued, then inspect its persisted attachments before deletion.
    - [PhotoRecoveryTest.kt:129](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/composer/PhotoRecoveryTest.kt:129): Removing ownership rechecks after copying would still pass. The profile is removed before `recover()` starts, so only initial admission is exercised. **Fix:** Suspend the copy, delete the profile during it, then resume and verify metadata and payload cleanup.
    - [AttachmentVaultTest.kt:82](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/composer/AttachmentVaultTest.kt:82): Removing the 256 MiB root-budget guard would still pass. It exercises attachment count, draft size and injected quota failure, but never root capacity. **Fix:** Seed root usage and test exact-boundary acceptance and overflow across owners.

11. **MINOR — No-project starters contradict the claimed catalog row.**  
    [Starters.kt:53](/home/eslam/Storage/Code/wt/native-reviews/src-compe/native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/phrases/Starters.kt:53)  
    **Problem/evidence:** **B-CHAT-017** and plan **09 §10.4** require no starters for no project/projects root, but this branch returns `ListFolder` and `FindBug`. `StartersTest:48` asserts the conflicting behavior. Additionally, `/root/projects/` fails the root check and receives project starters.  
    **Fix:** Return an empty list without a project, normalize trailing separators before root detection, and correct the test expectations.