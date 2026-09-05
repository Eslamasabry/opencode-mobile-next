# Composer draft recovery — cycle 21

The primary persona needs to leave a phone task and return without losing input.
A review found that text draft persistence ignored failed writes, published the
new value in memory before persistence succeeded, keyed drafts by session ID
alone and silently evicted old unsent drafts after 50 entries.

## Implemented behavior

- Draft identity now includes server profile and session ID. Equal session IDs
  from separate servers survive storage/reload independently. Existing records
  already containing a profile ID use the new lookup without rewriting content.
- Save, clear and profile-deletion writes are serialized. The controller only
  publishes an acknowledged save. A refused preference write reloads the plugin
  cache because the plugin updates that cache before returning platform failure.
- Save requests capture their original profile. Profile deletion drains pending
  writes, and late writes cannot recreate the removed profile's drafts.
- At 50 drafts, adding a new entry fails visibly and preserves existing drafts.
  Updating or clearing an existing draft remains possible; there is no silent
  eviction of unrelated unsent text.
- A persistent composer warning offers Copy and Retry. Tight layouts use an
  “Unsaved” label with the full explanation in a tooltip and semantic label.
  Failure to clear a saved draft is distinguished from failure to save new text.
- Back waits for the save. If it fails, the user can keep editing or deliberately
  leave without saving. Edits made during the awaited save keep the route open.
  Retry success removes the warning without replacing the user's text.

## Evidence

`test/session_draft_test.dart` covers two-server ID collisions, disk refusal and
retry, queued save/clear ordering, late writes following deletion, the 50-draft
capacity limit, navigation/reopening and clearing after a queued send. The real
composer recovery flow passes at 320×640, 1.7 text scale and a 260px keyboard inset.
The compact exercise exposed a banner overflow; the compact presentation fixes it.

Existing profile deletion, offline queue, settings storage, prompt stash/history
and active-context checks were exercised. The localization check also verifies
the context label fix from cycle 20. The initial combined validation was stopped
after a formatting diagnostic; subsequent completed runs provide the evidence,
not the interrupted run. Local logs are `cycle21-draft-final.log`,
`cycle21-tests-final.log`, and `cycle21-regressions.log` (the latter records the
compact failure before its targeted correction).

## Remaining work

- This batch covers ordinary draft **text**. Automatic attachment persistence
  still needs a file-backed store; the existing attachment-discard confirmation
  remains. Stashed prompts and queued sends retain their separate behavior.
- Unattributed drafts from versions predating profile ownership are retained.
  Automatic restoration is restricted to an unambiguous single-profile setup;
  ambiguous legacy drafts still need an explicit recovery UI.
- Camera/gallery input, large-payload stash storage and actual process-death/
  install-upgrade/device recovery verification remain in the release scope.
- A confirmed failed save cannot promise survival if Android kills the process.
  The warning exposes that failure and permits copying or retrying.

The accompanying [persona](../product-persona.md) guides delivery priority and
does not reduce the full parity or release requirements.
