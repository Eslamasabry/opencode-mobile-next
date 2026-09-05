# Prompt stash and sent-prompt history

Cycle 17 implements the client workflow in issue #47 for both protocol adapters.

## Composer behavior

- Prompt tools offers **Stash current prompt** and **Saved prompts**. Stashing
  saves the current text, full attachment URLs/data and structured references,
  then clears only the unchanged composer after storage acknowledges the save.
- Saved prompts supports review, restore/pop, and confirmed deletion. Restoring
  saves a nonempty current prompt first. The source entry stays until its content
  has been applied; a failed removal leaves a recoverable copy.
- There are at most 50 ordinary stash entries per saved server. A full stash
  refuses an additional save rather than evicting another prompt. Restoring over
  a nonempty composer also requires room to preserve that composer first.
- Entries are separate from autosaved per-session text drafts and the offline
  send queue. They persist on this device and do not sync between clients.
- File URLs and structured references retain their original directory/workspace.
  Restore requires that location; snippets are explicitly described as snapshots.
  Server file existence and remote URL expiry are not inferred from a saved URL.
- Temporary attachment schemes, such as content/blob grants, are named before
  an explicit partial restore. The original stash entry stays available. Corrupt
  saved data produces a read error without silently dropping entries.

## History and draft recovery

The existing text reuse sheet includes loaded user prompts in the conversation
and the last 50 distinct successful direct sends on this server. A selected
entry appends text; it never automatically copies prior attachments. This is not
a claim of exhaustive server history or of persistent history for offline queue
flushes. Those delivered messages remain available through conversation history.

With a hardware keyboard, Up at offset zero recalls older text; Down at the end
moves forward and restores the original draft. The exact original text and
selection remain recoverable through a visible **Restore original draft** button,
even if recalled text is edited. Draft autosave keeps the original while browsing
history. Modifiers, noncollapsed selections, IME composition, slash/agent
suggestions, and ordinary movement within a multiline prompt keep their keys.
Existing Enter/Shift+Enter behavior remains in its existing shortcut layer.

Stashing recalled text returns the original draft. A stash restored while browsing
history retains its saved copy, so the original draft's autosave does not erase
the newly restored source on restart. Attachments and staged references still
follow the composer's existing in-memory draft lifetime after restore.

## Storage and failure handling

Each stash entry is stored separately under the server profile namespace, so
adding one entry does not re-encode all attachment payloads. Writes serialize
per profile and visible caches update only after acknowledged storage. History
uses a separate bounded text preference. Profile deletion drains pending writes,
removes the namespace and forgets both runtime caches.

The native preference backend may still rewrite its whole backing file. Large
attachment stashes need a device memory/storage exercise before the final
release; this batch does not establish that a shelf full of maximum-size
attachments is suitable for the native preference backend.

Location revisions guard restore/delete actions. User edits or location changes
while a save is pending prevent clearing/replacing the changed composer. Sending
is disabled during stash mutation. A history-storage error never turns a
successful network send into a failed send or triggers retransmission.

## Verification

Focused storage and real-composer checks cover full Unicode/attachment/reference
round trips, profile separation, 50-entry capacity without eviction, refused
storage with recovery, original draft autosave/selection recovery, keyboard
conflicts, and preserving a current prompt during restore. Phone-width and
enlarged-text interaction and the existing desktop/mobile Enter tests are part
of this batch. Final physical-device and release-candidate checks remain open.
