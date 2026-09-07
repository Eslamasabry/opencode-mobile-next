# Historical release notes — BROKEN Alpha 1.0.34+35

**Current source: `dev`, version 1.0.36+37, unreleased.** This batch is source
work only: no new APK, CI/native build, signing, tag or publication.
The focused 1.0.35+36 CI APK was already delivered with the stable installed
signer; see [verification](verification/empty-project-session-recovery-2026-09-07.md).

The notes below preserve the historical `v1.0.34+35` release and its signing
provenance. [That public release is marked BROKEN](https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v1.0.34%2B35).
They are not current installation advice or release notes for 1.0.36+37.
Publication evidence remains on the historical GitHub release.

---

# [BROKEN] OpenCode Mobile - Alpha 1.0.34+35

**Known regression:** a connected server with no opened projects can hide
existing sessions. Do not install this version to recover session access.
The feature summary below records the original release claims.

This release aimed to make it easier to resume coding from your phone: keep unfinished
prompts and attachments, find earlier work, review what the agent sees, and
move conversations between supported servers.

**Changes since [1.0.33+34](https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v1.0.33%2B34),
released September 2, 2026.** It includes the interim `dev-06447b6` preview
and all subsequent changes through this release tag.

OpenCode Mobile is an independent community app built with substantial AI
assistance. It is not affiliated with the official OpenCode project.
**This is still an alpha:** Android is the primary target; desktop builds
are experimental, and full OpenCode feature parity is not complete.

## Write a prompt without losing your place

- A full-width composer, quieter controls, larger touch targets, image
  thumbnails, reply actions, and stable keyboard focus and selection.
- Automatic draft saving per server and conversation, including attachment-only
  drafts and durable private copies of ordinary draft attachments.
- Visible Copy/Retry recovery when saving fails, explicit choices before
  leaving unsaved work, and review when only some attachments can be restored.
- Android Camera and Photo library actions with recovery back to the original
  conversation. Photos are validated before they are added to the prompt.
- An Older drafts screen to search, read, copy, or append ambiguous legacy
  text without overwriting the current draft or removing the original.
- A prompt stash with up to 50 entries per server, searchable reuse, sent-text
  history, and Clear draft text with Undo. Queued prompts retain failed saves
  and cannot silently move to a different server or workspace.

## Find and move your work

- Local pinned conversations and a pinned section in Workspace.
- Transcript search with highlighted excerpts, tool/filename matches, older
  history search, and keyboard shortcuts.
- Newest-first message loading, stable older-page navigation, and complete
  pagination for scoped and all-project conversation lists.
- Unread completion tracking with an option to keep read history local.
- **OpenCode 2:** complete JSON export with redaction on by default, plus
  reviewed JSON import with explicit destination and conflict recovery.

## Understand and control the agent

- Per-conversation model and reasoning selection, server-synchronized OpenCode
  2 choices, and per-server favorites/recents in a searchable model picker.
- Running work for related agents and supported commands, with output paging,
  Copy/Follow, timeout controls, Stop confirmation, and reconnect recovery.
- A reachable Background action for supported work, plus Ctrl+B.
- **OpenCode 2:** Note for the agent, reviewed session skill activation with
  optional immediate execution, and an Active context inspector with search,
  filters, selectable text, and Copy.
- **OpenCode 2:** Usage and cost totals by date range and project, including
  token, model, and tool summaries from the server.

## Fewer interruptions while reviewing or reconnecting

- Review keeps selected patches and viewed state; staged revert protects
  unfinished prompts. Files supports Back-to-parent and search while typing.
- Permission/question sheets retire when resolved, keep replies scoped to the
  right location, and block duplicate submissions.
- Command retries preserve arguments and destination. Refresh failures keep
  cached content visible with Retry; stale network results are ignored.
- Provider/OAuth operations retain their original destination. OpenCode 2 MCP
  setup explains its runtime-only lifetime and prevents accidental replacement.
- Terminal reconnect offsets handle multibyte text correctly. File preview
  controls wrap on phones, and Copy includes all loaded text.
- More and Settings use grouped, searchable rows; small-screen and large-text
  layouts receive fixes. Managed local-server restart checks ownership and work.
- Startup avoids unnecessary native photo-recovery calls. Dependency notices
  include the newly added photo/file-picker packages and their license texts.

## Android download and upgrade

Historical assets **`opencode-mobile-1.0.34+35.apk`** and **`SHA256SUMS`**
remain on the broken release for provenance. They are not the replacement APK.

**Check the signing identity before replacing an existing installation.**
The package is `io.github.eslamasabry.opencode_mobile`. This release uses the
permanent public signer:

```text
842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C
```

- Android permits in-place updates only when the package ID and signer match;
  this does not make the broken release a recommended update.
- The previous `1.0.33+34` public APK used signer `8F51FBCA…C82053`;
  its private key was lost, so it cannot update in place to this release.
- CI previews, including `dev-06447b6`, use signer `2D010C21…18D0EC`;
  they also cannot update in place to this public build.
- The maintainer's replacement APKs must always use the installed stable CI
  signer `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`.
  The delivered 1.0.35+36 CI APK preserves it; do not substitute the public signer.
- **Uninstalling erases local profiles, drafts, stashed/queued prompts, and
  other local app data.** Preserve the existing installation and obtain a
  matching-signer update. Uninstalling is not a fix for the session-list regression.

Settings → About shows the version, package ID, and certificate in builds that
support this view. Notification-based tool approval requires device
authentication on Android 12 and newer.

## Desktop and compatibility

Linux packages use `opencode-mobile` and protect the separate OpenCode server
command. Linux assets, when attached, use **`SHA256SUMS-linux`**. Windows
builds remain available through CI artifacts. Both platforms are experimental;
see [desktop setup](https://github.com/Eslamasabry/opencode-mobile-next/blob/v1.0.34%2B35/docs/desktop.md).

OpenCode 1 and the repository's pinned OpenCode 2 beta contract are supported;
features marked OpenCode 2 depend on server capability. Unsupported endpoints
are not a promise of full parity. Active context shows the server's active
messages after compaction, not the exact provider request or token accounting.

English is the supported UI language. Play Store, iOS, and macOS distribution
are not included. Physical-device camera, process-death recovery, and
install/upgrade smoke checks remain incomplete. Camera/library input supports
PNG, JPEG, GIF, and WebP within the composer limits; HEIC and video are not supported.

## Full changelog and feedback

[Detailed changelog](https://github.com/Eslamasabry/opencode-mobile-next/blob/v1.0.34%2B35/CHANGELOG.md)
· [All changes since 1.0.33+34](https://github.com/Eslamasabry/opencode-mobile-next/compare/v1.0.33%2B34...v1.0.34%2B35)
· [Changes since the interim dev APK](https://github.com/Eslamasabry/opencode-mobile-next/compare/dev-06447b6...v1.0.34%2B35)

Use **More → Report a bug** or Settings → About. Include the app version,
server version, and steps to reproduce; remove credentials and private
conversation content before submitting.
