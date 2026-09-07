# Changelog

This project is in public alpha. Only the newest preview is supported.

## Unreleased

- Keep existing sessions, error recovery and older-page controls visible when
  Workspace reports “No projects opened”. Add a labelled Search all sessions
  action so previous conversations remain reachable.

- Add Settings → Remaining usage for explicitly trusted, separately
  installed Codex and Claude quota collectors. Report account windows, reset times,
  unknown values and stale results separately from OpenCode consumption.
  The collectors support OAuth logins, perform no credential
  refresh, and require an authenticated same-origin proxy deployment.
- Group consumption by provider while retaining the existing model details.
  Variant records do not inflate model counts, and consumption is never
  labelled as a provider's subscription allowance.
- Cancel quota reads and discard consent immediately on profile deletion,
  source changes or screen disposal. No provider tokens or quota snapshots
  are persisted by the app; provider access was tested with synthetic data.
- Ignore saved Android background-service opt-ins on unsupported platforms,
  including iOS, without calling native channels or changing the saved choice.
  Add isolated iOS remote-client gating tests; an iOS runner/build is not
  included in this change.
- Pin the managed Termux OpenCode server to **1.18.29**, the upstream fix
  that makes `gpt-6-astra` appear correctly for users authenticating via
  Codex OAuth with a personal ChatGPT subscription. New installs pick it
  up automatically; existing managed servers move to it through the
  Termux update flow. Users connecting to their own computer-hosted
  servers need that server updated to see the fix — the app has no
  separate model-list logic.

## 1.0.34+35 - 2026-09-06

Changes since **1.0.33+34** (2026-09-02), including the interim `dev-06447b6`
preview and subsequent work. [Full comparison](https://github.com/Eslamasabry/opencode-mobile-next/compare/v1.0.33%2B34...v1.0.34%2B35).

### Composer, photos, and draft recovery

- Rework the composer with a full-width editor, quieter action row, larger
  touch targets, and stable keyboard focus and selection. Add reply actions,
  image thumbnails, and Clear draft text with Undo.
- Save draft text and attachments per server and conversation. Attachment-only
  drafts use app-private file storage; review partially missing attachments
  before restoring the available content.
- Show Copy and Retry when saving fails. Back and New chat wait for saving;
  leaving unsaved work requires a choice. Full storage does not silently evict
  older drafts.
- Add Android Camera and Photo library actions with recovery tied to the
  original server, conversation, and location. Copy photos into private
  storage and validate image type and size before attaching them.
- Add Older drafts review for ambiguous legacy text: search, read, copy,
  append, or explicitly delete. Appending preserves the source and current
  draft; legacy attachments remain with the source.
- Add a persistent prompt stash with up to 50 entries per server, location
  review on restore, searchable reuse, and the last 50 sent text prompts.
  Keyboard history navigation preserves the unfinished draft.
- Retain queued prompts after storage failures and block stale sends after
  location changes, discard, or removal of the source server profile.

### Finding, organizing, and moving conversations

- Pin conversations locally per server and reopen them from a pinned section.
- Search the transcript, tool text, and filenames with highlighted excerpts
  and match navigation. Search older history on demand; add Ctrl+F, F3,
  and Escape keyboard controls.
- Load newest messages first, preserve the reading position while loading
  older pages, and paginate scoped and all-project conversation lists.
- Track unread completions, mark conversations read while actively viewing
  them, and offer a private local-only read-history option.
- Add complete OpenCode 2 JSON export, redacted by default, with an explicit
  unredacted option and recoverable save failures.
- Add reviewed OpenCode 2 JSON import with a visible destination, validation,
  conflict handling, and reconciliation after uncertain server responses.

### Agent controls and visibility

- Keep model and reasoning choices specific to each conversation. Synchronize
  OpenCode 2 server-owned model and agent selections; retain explicit choices
  in offline snapshots.
- Add per-server model favorites and recents, a searchable picker with
  All/Favorites/Recent tabs, clear filters, and F2 / Shift+F2 cycling.
- Add Running work for related agents and supported OpenCode 2 commands:
  paged output, Copy/Follow, timeouts, confirmed Stop, and reconnect recovery.
- Expose Background for supported running work, including Ctrl+B; honor
  differences between OpenCode 1 and OpenCode 2 capabilities.
- Add an OpenCode 2 Note for the agent editor with conflict review and saved
  transcript feedback, without exposing note contents in status notices.
- Allow reviewed OpenCode 2 session skill activation, with Run agent now
  and protection against duplicate or stale activation.
- Add an OpenCode 2 Active context inspector with search, type filters,
  selectable text, and Copy. It shows server-reported active messages after
  compaction, not the exact provider request or exact token accounting.
- Add Usage and cost totals by date range and all/current-project scope,
  including token, model, and tool summaries where supported by the server.

### Workspace, review, and connection reliability

- Preserve selected files, patches, and viewed state during review refreshes.
  Add explicit staged revert review while protecting unfinished prompts.
- Navigate up folders with Back, search files while typing, refresh sessions
  from Workspace, and show the active execution directory.
- Improve file-preview controls and copy the full loaded text. Resume terminal
  output with correct UTF-8 byte offsets, including Arabic and emoji.
- Keep permission and question replies tied to their original request and
  location; retire resolved sheets and prevent duplicate replies.
- Start a chat from a command, retain arguments and destination on failure,
  and retry without unnecessarily creating another conversation.
- Keep cached content visible during refresh errors with Retry. Ignore stale
  connection probes, catalog loads, and pagination responses.
- Preserve provider/OAuth destinations across location changes. Explain that
  OpenCode 2 MCP additions are runtime-only and prevent accidental replacement.
- Make managed local-server restart respect ownership and active work.
- Replace oversized More tiles with grouped rows, add tools/settings search,
  and improve small-screen and large-text layouts.

### Packaging and maintenance

- Establish a permanent public Android signing identity; show app version,
  package ID, and certificate in About. CI previews use a separate stable signer.
- Require device authentication for notification-based tool approval on
  Android 12 and newer.
- Use `opencode-mobile` for Linux package, launcher, and runtime paths, with
  ownership checks to protect the OpenCode server CLI. Keep Linux and Android
  release checksum manifests separate.
- Update the generated SDK to upstream `f12e14cf`, including
  `ProviderConfig.options.chunkTimeout: false` support.
- Fix unnecessary native photo-recovery calls at startup and bundle accurate
  notices for all 13 added photo/file-picker dependencies.
- Refresh setup, troubleshooting, compatibility, privacy, and support docs;
  add public security-reporting guidance and repository metadata.

### Upgrade notes and known limits

- **Signing transition:** `1.0.33+34` and CI-signed `dev-*` APKs cannot update
  in place to this public signer. Preserve local drafts, queued prompts, and
  connection details before removing an older installation; uninstalling erases
  local app data. See release notes for certificate fingerprints.
- Android remains the primary platform. Desktop builds are experimental;
  English is the supported UI language. This is not a full-parity or stable v1 release.
- OpenCode 2 features depend on the pinned beta contract. Unsupported server
  capabilities remain unavailable; active context is a server snapshot.
- Physical-device camera, process-death recovery, and install/upgrade smoke
  testing remain incomplete. Photos support PNG/JPEG/GIF/WebP, not HEIC or video.

## 1.0.33+34 - 2026-09-02

- Added provider presentation and live Android background status.
- Added OpenCode 1 and OpenCode 2 beta support, QR pairing, activity inbox,
  phone-sized diff review, local voice transcription, and Termux hosting.
- Published the first public-alpha Android APK.

The original Linux assets for this version were withdrawn because their
installer could collide with the OpenCode server command. The Android APK is
still available from the release page.
