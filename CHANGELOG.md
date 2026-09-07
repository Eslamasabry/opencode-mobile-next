# Changelog

This project is in public alpha. Only the newest preview is supported.

## 1.0.36+37 — Unreleased

Changes since **1.0.34+35** (2026-09-06), whose source is tagged
[`v1.0.34+35`](https://github.com/Eslamasabry/opencode-mobile-next/tree/v1.0.34%2B35)
at `b618d30`. This section describes the development candidate, including the
quota batch and subsequent integration work. It is **not** an announcement of
available downloads: final verification and CI packaging are still in progress.

### On-device setup and session recovery

- Show setup progress immediately, including while Termux is still responding.
  Use a flat page with readable, colored output that fills the available height.
- Inspect the managed Ubuntu installation before choosing a path. Reuse installed
  OpenCode without reinstalling, install the tested OpenCode 1 in Ubuntu, or
  connect an existing OpenCode 1/2 server. OpenCode 2 and musl installation remain
  unavailable until their runtime paths are verified.
- Require the compatible Ubuntu binary package explicitly during npm setup,
  retain version pinning and temporary-cache cleanup, and expose install output.
  The reported musl error is an upstream fallback; its original glibc failure
  could not be reconstructed from that log alone.
- Keep prior sessions and global search accessible when the project list is empty.
  This session fix also appeared in the focused 1.0.35+36 CI APK.

### Saved prompts and attachment recovery

- Move new stash attachment payloads from preferences into a separate,
  app-private file vault. Ordinary draft cleanup no longer shares ownership of
  stash payloads. The existing stash feature and 50-entry limit are retained.
- Migrate older inline attachments when **Saved prompts** opens. Failed writes
  leave the original saved content intact and expose a retry; no silent eviction
  or truncation is used to make migration fit.
- Search saved prompt text, attachment filenames, review references and locations
  without loading attachment payloads.
- Restore attachments asynchronously, identify missing/corrupt/temporary files,
  and ask before restoring only the available content. Canceling or changing the
  profile, project or conversation cannot replace the wrong composer.
- Save the current prompt before replacing it. Remove a fully restored source
  only after the destination draft is saved; retain source copies for partial
  recovery and structured review references.
- Include stash files in profile deletion, retain cleanup ownership after
  failures, and allow retry without losing track of orphaned payloads. Bound
  attachment reads while streaming, not just with a pre-read file-size check.

### Provider accounts and sign-in — supported OpenCode 2 servers

- Add **Manage accounts** for individual saved credentials: inspect labels,
  request activation, rename, or confirm removal without disconnecting every
  account. Environment-backed connections remain server-managed.
- Show **Active** only after a confirming server event. Initial loads and stream
  gaps show unknown active state; an accepted switch request alone does not
  establish which account is active.
- Add command-method sign-in with explicit confirmation before the selected
  server executes its declared authentication method. Check or cancel the
  existing attempt; the app does not execute a shell command on the phone.
- Retain bounded OAuth/command recovery metadata across navigation and app
  restarts. Resume, check, enter a code, cancel, and forget locally are explicit
  actions tied to the original profile, server origin and project/workspace.
- Keep browser authorization URLs, one-time codes, submitted answers and provider
  tokens out of recovery preferences. Disclose failed recovery writes; never
  automatically restart an uncertain sign-in. An attempt ID lost before the
  start response arrives cannot be recovered through the current contract.
- Route authorization links through the shared external-link policy. Credential
  changes and authentication recovery use fresh, scope-checked operations;
  uncertain outcomes remain actionable rather than being reported as success.

### MCP management — supported OpenCode 2 servers

- Add confirmed removal of an MCP server from the current **runtime location**.
  Recheck the inventory before dispatch and prevent duplicate removal requests.
- Refresh server and resource lists after removal, including uncertain network
  outcomes. Keep existing content available when refresh fails.
- Distinguish runtime removal from persistent configuration: a configured server
  may return after restart. Unsupported servers do not advertise the action.

### Remaining quota and consumption

- Add **Settings → Remaining usage** for Codex account windows, with reported
  percentages, reset times, freshness, unknown values and stale/error states.
  This requires an explicitly trusted, separately deployed collector and an
  authenticated same-origin proxy; installing the app alone does not enable it.
- Require consent on each screen visit and an explicit read. Cancel old reads on
  profile deletion, source changes and disposal. No quota background polling,
  quota notifications or persisted snapshots are added.
- The optional collector uses a selected Codex OAuth source, never refreshes or
  writes credentials, and does not forward provider tokens to the phone. Its
  internal upstream endpoint can change; no live-account validation or collector
  deployment is claimed by this implementation.
- Keep Claude subscription collection **unavailable** pending a supported,
  permitted integration. The experimental path introduced during development is
  disabled before credential or network access. Retired Claude settings warn
  without preventing a valid Codex collector from starting.
- Group existing **Usage and cost** records by provider. Add provider/model/
  variant filters, matching-record subtotals and clear filters while retaining
  the selected report's date/project totals. Variant records do not inflate
  distinct model counts; consumption is never converted into subscription quota.

### Android voice

- Add **Read reply prose** with explicit speech-engine consent, a picker of
  installed voices marked offline, and a visible **Stop reading aloud** control.
  Read only the loaded reply prose; omit code blocks and tool details, and
  reject oversized input instead of silently truncating it.
- Add a foreground **Voice conversation** loop using existing local dictation:
  listen, review/edit, insert, explicitly Send, and optionally read a reply.
  There is no automatic listening, sending, reading or voice-only tool approval.
- Pause for pending decisions, active turns or a disconnected transport. Stop
  playback before microphone capture, and invalidate stale audio/transcript
  results on interruption, backgrounding or source changes.
- Voice-conversation drafts are temporary: unsent text is discarded on leaving
  the mode, chat or app, rather than autosaved or placed in the offline retry
  queue. Ordinary typed-draft behavior is unchanged.
- Speech is handled by the separately installed system engine. An offline voice
  flag is not a network-isolation guarantee; that engine's privacy practices
  apply. No speech engine or voice is installed automatically.

### Onboarding, attention and navigation

- Add **Try demo** to first-run Servers: a simulated prompt, reply, review and
  approval journey that can be reset or exited without accessing a real server,
  provider, profile or file.
- Add **Guide → Connection help** to explain pasted address rules locally.
  Include private HTTPS/reverse-proxy and device-side tunnel guidance, explain
  why another computer's localhost is not this phone, and keep remote HTTP
  blocked even on LAN/tailnet ranges. This is not a connectivity or VPN test.
- Add **Server attention** from Servers using available local data. Inactive
  profiles and unavailable counts stay unknown; this is not concurrent live
  monitoring across every saved server.
- Add on-demand **Completion digests** in Activity from cached server metadata,
  with conversation/review actions. Idle does not establish success, and no AI
  summary or additional model request is made.
- Add reviewed **web-source text** to the composer: public URLs and optional
  user-pasted excerpts. No page is fetched and no prompt is sent automatically.
  This is URL review, not an implemented web-search adapter.
- Improve global/related-session navigation and add confirmed **Copy handoff**
  metadata references. These contain session/project IDs, not sign-in secrets;
  they are not CLI commands, public share links or automatic execution links.

### Desktop, iOS preparation and fixes

- Add keyboard-focusable desktop context menus with **Shift+F10/Menu**, visible
  focus, corrected popup placement, and protection against duplicate or stale
  actions. Surface file-drop failures with safe recovery guidance and prevent
  overlapping drop handlers.
- Fix About tab layout at large text sizes and use platform-accurate secure-
  storage guidance instead of promising Linux libsecret on every platform.
- Ignore saved Android background-service opt-ins on unsupported platforms
  without making native calls or changing the saved preference.
- Add an experimental **iOS 15+ remote-client source target**, branded icons,
  Keychain configuration and unsigned simulator CI. It does not enable Termux,
  Android background services, local voice, camera or QR scanning on iOS.
  This is preparation—not a released or device-verified iPhone app.
- Update the managed Termux OpenCode pin from **1.18.25 to 1.18.29**. New managed
  installs use it; existing installations update through the Termux update flow.
  Remote computer-hosted servers remain independently managed. Dependency
  versions are unchanged in this candidate.

### Upgrade and delivery notes

- Stash storage now has a separate **256 MiB** vault budget, with up to **five
  attachments / 32 MiB of encoded payloads per stash**. Oversized legacy entries
  remain unmigrated and recoverable. Older app versions do not understand the
  new file-reference format; downgrade compatibility is not guaranteed.
- Local sign-in recovery is limited to **16 records / 512 KiB per profile**, with
  a recovery window of at most **24 hours**. Expiry or local forgetting does not
  cancel a remote process or revoke provider credentials. Profile deletion
  removes local recovery data only.
- Intended Android CI downloads are **test-signed**, not production-signed.
  Updating an existing installation requires a compatible signing certificate;
  do not uninstall an existing app with unsaved local data just to force an
  incompatible build to install.
- Native code and bundled assets changed, so this is **not a Dart-only Shorebird
  patch**. Linux/Windows builds remain experimental; iOS native/plugin/privacy
  and device validation remain outstanding. Artifact availability and final
  verification results will be recorded separately when completed.

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
