# Web storage — browser profiles, secrets, drafts and attachments (2026-09-13)

Branch `feat/web-storage-20260913`. Slice owned here: `lib/state/profiles.dart`,
`lib/state/draft_attachments.dart`, `lib/state/prompt_shelf.dart`,
`lib/state/prompt_photos.dart`, the new storage modules beneath `lib/state/`,
their focused tests, and this note.

**Finish line.** In a browser build, server profiles and their credentials use
the supported secure storage; composer text drafts, file attachments and the
prompt stash survive a page reload; deleting a profile removes its browser data.

**Non-goals.** Session-only substitutes, a plaintext credential fallback,
clearing unrelated origin storage, or any change to the native on-disk formats.

## Shipping state

| State | What |
|---|---|
| Implemented | Everything below, on this branch, in the owned files only. |
| Enabled | Automatically in a web build once the coordinator adds the `web` dependency (see *Integration needed*). Native builds keep their exact files, keys, and metadata. |
| Verified | **Not yet.** No Flutter, Dart, analyzer, test or browser process was run in this worktree (coordinator serializes them). `dart format` from the pinned SDK was run on the changed files. The checks to run are listed below. |
| Committed | Locally, `[skip ci]`. Nothing pushed. |

## What was wrong before

- `DraftAttachmentVault` kept payloads in `dart:io` files under
  `path_provider`'s app-support directory. Every default entry point —
  `ConnectionController`'s draft vault, `PromptShelfStore.withAttachmentFiles`,
  `PromptPhotoStore` — resolved that directory lazily, so a browser build
  constructed fine and then failed on the first draft save with a data
  attachment, on every stash with an attachment, and inside
  `deleteProfileAndLocalData` (the collect steps), which made profile deletion
  report failure in a browser.
- `flutter_secure_storage_web` needs a secure context (https or localhost) for
  WebCrypto and throws `UnsupportedError` — not the `PlatformException` the
  profile store classified — so a save on an insecure origin surfaced as a raw
  error with no product sentence. It also answers `null` for an entry it can no
  longer decrypt, which the store read as "no password", so a profile whose key
  material had been cleared would try to connect with an empty password rather
  than asking for it again.
- Text drafts (`SessionDraftStore`) and all metadata already worked in a
  browser: `shared_preferences_web` keeps them in the origin's localStorage.
  They were only unreachable because the surrounding attachment calls threw.

## What changed

### Attachment payloads: one vault, two stores

`DraftAttachmentVault` now owns identity, bounds, owner isolation, repair and
ordering, and delegates bytes to a `DraftAttachmentStorage`
(`lib/state/draft_attachment_storage.dart`):

- `draft_attachment_storage_io.dart` — the previous file layout, unchanged on
  disk: `<root>/<sha256(owner)>/<sha256(payload)>.blob`, published via a
  sibling `.tmp` and a rename; `.tmp` leftovers count against the budget and
  are collected. Selected by default (also what the analyzer sees).
- `draft_attachment_storage_web.dart` — one IndexedDB database per vault root
  (`oc.draftAttachments.<root>`), two object stores keyed `<ownerKey>/<id>`:
  `meta` (owner, id, size — listing never loads bytes) and `payloads`. A write
  or delete touches both in one transaction, so a reload sees the old payload
  or the new one, never a prefix. The first write asks
  `navigator.storage.persist()` once (best effort; recorded, not required).
  Selected whenever `dart:js_interop` exists (dart2js and dart2wasm).

Constructor: `DraftAttachmentVault({directory, storage, root})`.
`directory:` keeps its native meaning (tests inject a `Directory`), `storage:`
injects a store outright, and `root:` names the platform-resolved root. The
three roots are unchanged and still isolated from each other:
`draft-attachments-v1`, `prompt-stash-attachments-v1`, `prompt-photo-recovery-v1`.
`PromptShelfStore.withAttachmentFiles` and `PromptPhotoStore` now build their
default vault with `root:` and no longer import `dart:io` or `path_provider`.

Metadata schemas (`DraftAttachmentRef`, `SessionDraft`, `StashedPrompt`,
`PendingPromptPhoto`), hashes, per-owner isolation, the store-before-metadata
ordering, collection and recovery semantics are byte-for-byte the same on both
platforms; a draft index written by one build reads on the other.

### Storage failures are named, never swallowed

`DraftAttachmentStorageException(failure, message)` replaces the `dart:io`
`FileSystemException` the vault threw for its own budget, and wraps platform
failures: `limit` (this app's 32 MB per draft / 256 MB per root),
`quota` (disk full on native via ENOSPC; `QuotaExceededError` in the browser,
with the origin's usage/quota from `navigator.storage.estimate()` when the
browser reports it), `unavailable` (no IndexedDB in this browsing mode, blocked
site data, a filesystem error). The message is a product sentence without paths
or payload content. Callers that catch everything keep working; the composer's
existing "attachments could not be saved / copy your text" path is what a
browser user sees until the message is surfaced (see *Integration needed*).
Input is never discarded: metadata is only written after the bytes committed,
exactly as before.

### Profiles and secrets in a browser

`ProfileStore` gains a browser probe (`browser_secret_storage*.dart`,
conditional import, injectable for tests):

- `secureStorageProblem()` on a web build answers before a password is typed:
  insecure context → "open the app over https:// or from localhost"; blocked or
  read-only site storage → "allow site data"; otherwise null. It performs one
  write-and-remove of `oc.secure.probe` in localStorage (some private modes
  read fine and refuse writes), and never touches the plugin.
- `upsert`/`remove` classify the web plugin's `UnsupportedError` and the
  browser's own thrown exception objects as secure-storage failures and throw
  `SecureStorageUnavailable` with the matching browser sentence. Profile
  metadata is rolled back first, as on native. A profile without a password
  still saves on an insecure origin (no crypto is needed to delete).
- `load()` on a web build treats "read returned null but the entry exists" as
  the browser's version of an unreadable Keystore entry: the profile loads
  with `requiresPasswordReentry = true` instead of an empty password. Native
  builds are not asked `containsKey` (their tests mock only read/write/delete).

Nothing falls back to plaintext. On an insecure origin the password is refused
and the profile is not saved at all.

## Contracts (public, preserved or added)

- `DraftAttachmentVault(...)`: `directory:` unchanged for native; added
  `storage:` and `root:`; `store`/`restore`/`collect`/`ownerKey`/limits
  unchanged. Subclasses in tests (`StashMemoryVault`, `_Vault`) still compile.
- `DraftAttachmentStorage`, `DraftPayloadEntry`, `DraftAttachmentStorageException`
  (exported from `draft_attachments.dart`).
- `ProfileStore(prefs:, secure:, browserSecretStorage:)`; the new parameter is
  optional. `SecureStorageUnavailable.forBrowser`, `messageForBrowser`, and
  the three browser message constants. `BrowserSecretStorageState` is exported
  from `profiles.dart`.
- `PromptPhotoStore.attachmentRoot` (new constant);
  `PromptShelfStore.attachmentDirectoryName` unchanged.
- Preference keys are unchanged. IndexedDB database names are new and
  namespaced (`oc.draftAttachments.*`), nothing else on the origin is touched.

## Integration needed (coordinator-owned files)

1. **`pubspec.yaml`** — `web: ^1.1.1` under `dependencies` (already resolved
   transitively at 1.1.1; needed so `package:web` imports in
   `draft_attachment_storage_web.dart` and `browser_secret_storage_web.dart`
   pass `depend_on_referenced_packages`). Under `dev_dependencies`, for
   `test/web/profile_web_storage_test.dart` only:
   `flutter_secure_storage_web: ^2.1.1` and
   `flutter_secure_storage_platform_interface: ^2.0.3` (both already in the
   lock as transitive). The browser test entrypoint registers no web plugins,
   so that test installs the plugin's web implementation itself.
2. **`connection.dart` / chat UI (optional, for actionable copy)** — the draft
   save path currently collapses any vault error into
   `SessionDraftFailure.attachments`. To show the browser's actual reason,
   catch `DraftAttachmentStorageException` there and carry `message` to the
   composer warning (and likewise around `PromptShelfStore.stash`). New copy
   would need arb keys; the sentences live in
   `DraftAttachmentStorageException` / `IndexedDbDraftAttachmentStorage` /
   `SecureStorageUnavailable` and can be localized when the UI wiring lands.
3. **Servers screen** already calls `secureStorageProblem()` before the
   password field; on a web build it now returns the browser sentences with no
   further change.

## Checks to run (none executed here)

```bash
# Focused native (VM) coverage of this slice
flutter test --concurrency=1 \
  test/draft_attachment_storage_test.dart \
  test/profile_browser_secrets_test.dart

# Existing suites that exercise the refactored vault and profile store
flutter test --concurrency=1 \
  test/draft_attachments_test.dart test/stash_attachments_test.dart \
  test/stash_lifecycle_test.dart test/prompt_photos_test.dart \
  test/prompt_shelf_test.dart test/session_draft_test.dart \
  test/profile_store_test.dart test/profile_secure_storage_test.dart \
  test/profile_deletion_test.dart test/product_error_text_test.dart

# Browser coverage (real IndexedDB, real flutter_secure_storage_web)
flutter test --platform chrome test/web

flutter analyze
```

`test/web/*` carries `@TestOn('browser')`; the serial VM gate should report the
two files as skipped, not failed. If the runner instead tries to compile them
for the VM, exclude `test/web` from the VM chunks and run it with
`--platform chrome` only.

## Known limitations (honest statement)

- `flutter_secure_storage_web` stores its AES-GCM key material in the same
  localStorage as the ciphertext (`FlutterSecureStorage` next to
  `FlutterSecureStorage.pw.<id>`). That is the package's supported browser
  design and the only supported secure storage available; it protects against
  casual inspection and accidental logging, not against script running on the
  same origin. PRIVACY.md should say so for the web build.
- Persistent storage is requested, not guaranteed: a browser that declines
  keeps the data but may evict the origin under storage pressure. `persisted`
  on the store records the answer for diagnostics.
- Browser tests cannot fake a quota-exceeded write or an insecure context; the
  VM tests cover those paths through an injected store and probe, the browser
  tests cover the real round trips (save → new store → restore → delete).
- `dart:io` stays imported by `main.dart` and other coordinator-owned files;
  this slice removed it from all four owned state files.
