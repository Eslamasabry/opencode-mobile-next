# Camera and photo-library input — cycle 23

The Android composer tools sheet now exposes Photo library and Take photo.
Both use Flutter's pinned `image_picker` 1.2.3 plugin. Desktop retains Attach
file; no desktop camera capability is claimed. The expanded prompt editor
continues to use the existing file chooser.

The draft is acknowledged before launching the native activity. A separate
pending-photo record stores the original server, session, directory and workspace
before launch. Picker results are limited to 10 MiB and recognized PNG, JPEG,
GIF or WebP headers. Normal selections enter the draft without sending. The
composer still enforces five attachments and 20 MiB overall, and its attachment
chip supports preview and removal.

Bootstrap calls `retrieveLostData` before chat routes open. A recovered result
stays with its original destination and is never inserted into an arbitrary
current chat. The original conversation exposes a pending-photo row with preview,
add-to-draft and discard actions. Project changes block applying a photo to the
wrong context. Another picker cannot overwrite an unresolved photo; choosing a
replacement requires explicitly discarding the pending item.

Pending payloads use a separate app-private `prompt-photo-recovery-v1` vault,
with checksum-checked references in preferences. The native cache path is recorded
before copying bytes, so a failed copy can be retried while that cache file still
exists. Once copied, source-file deletion does not affect recovery. The pending
copy is removed only after draft persistence succeeds, or explicit discard/server
removal. Serialized cleanup invalidates a result returning after profile deletion.
An otherwise empty new conversation is retained while a photo awaits recovery,
so leaving the chat cannot delete the pending image's destination.
No selected source file is deleted. Permission denial and cancellation clear
requests that never produced a photo.

## Verification

- Eight storage/picker tests cover origin-before-launch, local copy durability,
  cancellation, permission denial/retry, refused preference writes, lost native
  results, profile deletion during a picker, pending-result protection and size/
  format validation. See `test/prompt_photos_test.dart`.
- The composer checks exercise both source choices at 320×640 with 1.7 text
  scaling, verify saving before launch and preserve the draft on cancellation.
  The tools remain reachable with touch targets and semantics. Existing draft,
  profile deletion, platform and localization checks also passed.
- The first photo run exposed a validation mistake: the existing MIME helper
  inferred media from filenames and did not inspect image signatures. This path
  now recognizes supported headers directly; the corrected photo/composer run
  passed all 24 checks, followed by all eight storage checks including denial.
- Final storage plus desktop-gating run: 25 passed (`cycle23-final.log`). The
  pending-photo destination regression passed separately
  (`cycle23-destination-final.log`). Initial test-helper import/lint errors were
  corrected before these completed runs.
- `cycle23-analysis-final.log` reports no static-analysis issues.
- The local Android debug build reached Gradle but failed in
  `:app:mergeDebugResources` with `AccessDeniedException` for an existing
  `build/app/intermediates/merged_res_blame_folder` directory in OneDrive.
  This does not establish a successful native build. The fresh Linux CI Android
  job is the required build follow-up. No emulator was running during this batch.

## Remaining device/release evidence

Real camera availability and permission interactions, OEM/cloud photo providers,
and actual Android activity/process destruction still require native device
verification. Mocked lost-result recovery is not proof of OS lifecycle behavior.
HEIC and video are not accepted by this composer path. Initial dependency
resolution hit Windows' symlink requirement; project-local junctions were created
for generated desktop plugin links without changing Developer Mode.

Plugin contract: [Flutter image picker](https://pub.dev/packages/image_picker),
including Android lost-data recovery and temporary native camera files.

## Cycle 24 startup follow-up

Commit 525baca passed Windows CI, but Android/Linux tests timed out in the
bootstrap retry scenario before reaching their build steps. Recovery called the
native picker even when no photo request existed. The store now skips that call
when there is no persisted request, or its payload is already durable. Every
app-initiated picker launch commits its origin first, so this does not skip an
unrecovered request belonging to this app. Pending requests still use native
lost-data recovery. All 13 startup/diagnostics/photo tests passed in
`cycle24-startup.log`, including the failing bootstrap retry and a new assertion
that an ordinary startup makes no native picker call.
