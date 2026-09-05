# Composer draft recovery — cycles 21–22

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

- Cycle 22 adds automatic ordinary attachment recovery, described below.
  Stashed prompts and queued sends retain their separate storage behavior.
- Unattributed drafts from versions predating profile ownership are retained.
  Automatic restoration is restricted to an unambiguous single-profile setup;
  ambiguous legacy drafts still need an explicit recovery UI.
- Camera/gallery input, large-payload stash storage and actual process-death/
  install-upgrade/device recovery verification remain in the release scope.
- A confirmed failed save cannot promise survival if Android kills the process.
  The warning exposes that failure and permits copying or retrying.

The accompanying [persona](../product-persona.md) guides delivery priority and
does not reduce the full parity or release requirements.

## Cycle 22 — attachment recovery

Ordinary drafts now retain text and up to five attachments, including drafts
containing only attachments. Data URL payloads live in app-private files under
`draft-attachments-v1`, separated by hashed server identity. The preferences
index contains filenames, MIME types, checksums, encoded byte sizes and original
directory/workspace. Files are flushed before publishing metadata. Payload
limits are 32 MiB per draft and 256 MiB across this vault, including data URL
encoding overhead. Content is checked against its checksum on recovery.

Save/restore/cleanup use the existing serialized draft queue. Failed index writes
retain the previous draft and collect newly orphaned files. Shared payloads stay
until their last draft reference is removed; server deletion cleans only the
removed server and unattributed draft data. Cleanup never deletes selected
source files. A corrupt index refuses writes and cleanup instead of treating it
as an empty registry and erasing retained content.

The composer restores attachments on opening, shows loading progress, and
autosaves picker/paste/editor/chip changes. Back and New session wait for saving.
If a file is missing or unreadable, or a server file reference belongs to another
project, the user can use the available attachments or keep the saved draft.
Declining preserves the stored record and exposes Retry; editing/sending stays
disabled until recovery is resolved. Remote HTTP URLs remain references and are
not downloaded. Server file URLs are never read as local filesystem paths.

Focused coverage lives in `test/draft_attachments_test.dart` and
`test/session_draft_test.dart`: exact Unicode recovery through a fresh vault,
owner isolation, shared payload collection, corruption and repair, missing
files, location-bound references, malformed identities, metadata-write failure,
profile cleanup, attachment-only storage, chip removal and explicit partial
recovery. This evidence does not establish Android process-death or installation
upgrade behavior; those remain required device/release checks.

Final local evidence: all 16 draft/controller/widget checks passed in
`cycle22-draft-final.log`; the targeted New session failure-path check passed in
`cycle22-new-session-final.log`; `cycle22-analysis-final.log` reports no issues.
The initial focused run also passed all five vault checks plus attachment-paste,
profile-deletion and localization checks. The broader chat run recorded 104
passes and two failures subsequently corrected and rerun above. Recovery now
stops both composer progress indicators while waiting for a user choice; the
New session test uses a deterministic nonpersistent content reference rather
than depending on an unmocked native directory provider. The interrupted command
with an incorrect literal test filter is not validation evidence.
