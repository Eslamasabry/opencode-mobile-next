Verdict: FAIL — 1 BLOCKER, 7 MAJOR, 0 MINOR.

1. **BLOCKER — Editing can permanently delete the saved prompt.** [ShelfSavedPrompts.kt:78](/home/eslam/Storage2/wt/codex-review-p12/native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/presentation/ShelfSavedPrompts.kt:78)  
   Edit durably deletes the original, including unreferenced attachment files, before saving its replacement. Process death or cancellation between those operations loses the prompt. If replacement and rollback writes both fail, the rollback failure is ignored and the original is also lost. The failure test refuses writes *before deletion*, so it misses this path. **Rows: B-STATE-135, B-CHAT-088.**

2. **MAJOR — Restore silently omits attachments and references.** [SavedPromptsViewModel.kt:130](/home/eslam/Storage2/wt/codex-review-p12/native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/presentation/SavedPromptsViewModel.kt:130)  
   Restoring a migrated prompt containing text, files, and review references offers only `prompt.text` to the composer. It never recovers files, stages references, or checks directory/workspace. Attachment-only prompts cannot be restored at all because blank text returns immediately. Flutter restores the complete prompt and checks location. **Rows: B-CHAT-088, B-STATE-140.**

3. **MAJOR — Choosing a slash command can send an ordinary prompt or execute the previous command.** [BasicComposerHost.kt:206](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:206)  
   Command selection uses the append insertion path. With `Explain this` already typed, choosing `/review` produces `Explain this\n/review `, which dispatches as an ordinary prompt. With `/old` already typed, it invokes `old` with `/review` as arguments. Flutter replaces the field with the selected command. **Related row: B-NAV-051.**

4. **MAJOR — Profile deletion leaves saved attachment bytes behind and ignores cleanup failure.** [PreviewRuntime.kt:349](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt:349)  
   Deletion constructs `ProfileDeletion` without a stash closer or resource cleaner. The generic sweep removes stash metadata and its owner marker, then deletes the profile and secret, while attachment blobs remain. Cleanup occurs only on a later Saved prompts operation; inability to delete files cannot retain the profile as Flutter requires. **Rows: B-SEC-172, B-STATE-140.**

5. **MAJOR — Unsaved editor text is lost on activity recreation.** [SavedPromptsScreen.kt:195](/home/eslam/Storage2/wt/codex-review-p12/native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/SavedPromptsScreen.kt:195)  
   Type a new prompt or edit an existing one, then rotate the device. The field uses ordinary `remember`; typed changes are neither saved nor reported to the ViewModel. Recreation rebuilds it from `editor.initial`, discarding the changes. **Catalog row: none specific.**

6. **MAJOR — Failed delete Undo permanently removes its retry control.** [SavedPromptsViewModel.kt:160](/home/eslam/Storage2/wt/codex-review-p12/native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/presentation/SavedPromptsViewModel.kt:160)  
   Delete a prompt, then encounter a transient storage failure while undoing. The failure branch clears the Undo offer; the error notice supplies no retry action. Although the restoration closure remains in memory, the user cannot invoke it again. Flutter retains a failure bar with Try again. **Row: B-STATE-085.**

7. **MAJOR — Wrong-type stash preferences crash instead of showing the unreadable state.** [PreviewDrafts.kt:32](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewDrafts.kt:32)  
   If an adopted `oc.promptStash.<profile>.<id>` value is a Boolean or another non-string type, the production adapter throws `IllegalStateException`. `PromptShelf.stashes()` catches only `StoredFormatException`, and the loading coroutine has no enclosing failure conversion. Opening Saved prompts therefore produces an uncaught exception. Flutter catches the read failure. **Catalog row: none specific.**

8. **MAJOR — Older unattributed drafts never reach Saved prompts.** [ShelfSavedPrompts.kt:46](/home/eslam/Storage2/wt/codex-review-p12/native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/presentation/ShelfSavedPrompts.kt:46)  
   On a migrated installation with multiple profiles and an older draft lacking `profileID`, opening Saved prompts only reads existing stash keys. No older-draft migration is wired or implemented in the composer engine. Ordinary draft lookup also excludes unattributed drafts when multiple profiles exist, leaving that work inaccessible. Flutter migrates it when opening the shelf. **Row: B-STATE-087.**

Static review only; no files changed and no tests or builds run.