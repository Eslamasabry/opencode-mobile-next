# Changelog

This project is in public alpha. Only the newest preview is supported.

## Unreleased — test builds after 1.2.0+52 (up to 2208, 2026-10-10)

Local test builds handed to the maintainer; not published. Build 2208 is the
current candidate (full suite green at `27488c0ee`).

### Claude Code and Pi chats (Paseo)
- **Files, Changes, Worktrees and Terminal** now work for Claude Code and Pi
  chats: browse and read files, see the agent's uncommitted changes, create and
  delete worktrees, open terminals on the computer, type into them, rename and
  stop them.
- **Fast mode** and an agent's other switches: a chip beside the model chip
  opens "Claude Code settings"; each switch saves at once.
- **Import from Claude Code**: pick a conversation started in Claude Code on
  the computer (title, folder, when) and carry on from the phone.
- **Update agents on <computer>**: updates the agent software on the computer
  after a confirmation, shows progress, and reconnects when it restarts.
- **Check for agents again** on the server page, so signing in to Claude Code
  on the computer is noticed without reconnecting.
- Tool steps show what each tool answered (MCP results, searches, fetches,
  sub-agents, skills), and permission requests show exactly what they ask.
- Claude chats list only Claude models with Claude's thinking levels; a
  reconnect while Claude is working restores the live turn and Stop; short
  link drops stay quiet; the composer says "Ask Claude Code…" / "Ask Pi…".
- Failures say "Couldn't reach your computer…" instead of "It changed on the
  server in the meantime".

### Conversations and chat
- **Delete and Archive conversation** are back in the conversation's ⋯ menu
  (lost on 2026-10-03), each confirming by name.
- Failed conversations carry a **Failed** tag.
- An inline step timeline summarises the work; images in replies show as
  tiles; a suggested-connector card connects MCP servers in place, and
  connector search answers at once.
- **Search inside files** (OpenCode servers): typing in Files search offers
  "Search inside files for …" with file and line for each match.

### AI Team
- An agent that can't start says why (for example its model isn't set up on
  the computer); the host's own text is under Technical details.
- **Pause / Resume project** and **Delete project** on the project page
  (folder and files on the computer stay).
- **Force stop** an agent, also offered when a normal Stop didn't work.
- **Scheduled jobs** listed with plain schedules ("Every day at 09:00") and
  switched on or off.
- Workers' questions show their text and choices, and tool approvals can be
  answered from the phone.

### Agents on the phone
- Sign-in shows Open/Copy buttons and "Signed in" only after a real check,
  with the account line and Sign out; Remove agent frees its space; a
  half-finished install offers Remove; installs reserve download plus
  unpacked space first.
- The phone runtime pauses when idle to save battery (Resume from the
  notification), restarts after a crash with back-off, and comes back after a
  reboot or app update.

### Servers, settings and languages
- Server page: Edit and Remove named for the server, version and ready
  agents after a check, seven more rows under "Available on this server".
- Usage shows the busiest days; plan limits say when they reset; failed
  installs say why.
- Arabic covers about 2,500 more strings; Japanese, Chinese, Spanish,
  Portuguese and Russian cover first run and chat; other device languages
  fall back to English. Android notification text follows the app language.
- Tools is one page with tabs; voice licences moved to About.

### Privacy and errors
- Long access tokens are masked in shared problem reports, crash reports and
  performance traces; the Markdown export warns that it is not redacted.
- Saved crash reports are opt-in.
- Raw technical errors no longer appear as copy when opening a folder, loading
  models or commands, loading older conversations or connecting; the
  technical text is under Details.

### Not possible on current servers
Undo a merge, create or delete scheduled jobs, a per-agent permission switch
(AI Team), and searching inside files on Claude Code or OpenCode 2.

### How it was checked
Every field each server sends is shown or ignored with a written reason, and
every server action is reachable or recorded as not offered
(`docs/qa/coverage-gates-2026-10-10/`, `docs/qa/od1-2026-10-10/`).

## 1.2.0+52 — 2026-10-07

Claude Code on the phone beside OpenCode, every connection on the phone in
one conversation list, Claude's sub-agents as conversations of their own,
and the first Shorebird (over-the-air) release since 1.0.17. Full notes:
[docs/releases/v1.2.0+52.md](docs/releases/v1.2.0+52.md).

## Unreleased — Paseo backend (2026-09-19)

Built and verified locally on `feat/paseo-backend-20260919`; not merged.

- **Claude Code and Pi from the app, through a Paseo daemon** (experimental).
  A third connection type next to OpenCode and Codex: the open-source Paseo
  daemon runs beside the agent CLIs on the computer and the app drives it
  over one WebSocket. Sessions, streamed text and tool calls, permission
  cards (including "always allow" as the runtime's own suggested rule),
  permission modes, models per runtime and usage work; files, git, terminal
  and attachments are off for this backend. Plain `ws://` is accepted only on
  this device and on Tailscale addresses, and the daemon's public relay is
  never used. See `docs/paseo-connection.md`.

## 1.0.42+43 — Local checkpoint (2026-09-11, later)

Built and verified locally; no CI, no public download.

### AI Team · Gas City — Sprint B: decisions, controls, merge

- **A small front on the host** (`tool/host/cp_front/front.py`, Python
  stdlib) sits on the computer's Tailscale address in front of the
  supervisor. It identifies each phone by its Tailscale login, allows
  writes only for the logins you list, gives every mutation an idempotent
  receipt, passes event streams through, and adds merge readiness, merge
  request approval, a boundary-checked merge and a policy document. The
  app finds it on port 8373 and prefers it over a bare supervisor.
- **Answer decisions from Activity**: choices, approvals and free-text
  prompts send to exactly the requesting interaction; the row shows
  "Sent · waiting for the host to confirm", becomes "Answered" only when
  the host confirms, and never re-sends on its own ("Sent, unconfirmed —
  check on the host before re-sending" with a manual Retry). Destructive
  confirmations are two-step and red. Failed runs offer Retry, restart or
  reassign, View logs and Cancel work.
- **Notifications** for a decision requested, a run failed, a run ready for
  review and a run completed, with fixed copy and ids only, deep-linking to
  the exact gate.
- **Agent controls**: Message, Nudge, Pause/Resume, Stop and Restart
  (two-step), Reassign work; **run controls**: Cancel run / Close batch;
  **Start a run** sends an objective with a supervision level to the
  planner (Mayor) and shows "Planning… (Mayor)" until a run appears.
- **Merge from the phone**: readiness checklist, one-confirmation Approve
  request, and a two-step Merge that names the missing line or the host
  boundary that blocks it. Force-merge, reset and worktree deletion do not
  exist on the phone.
- **Supervision policy** and boundaries from the host shown read-only on
  the run overview and in the start sheet.
- Host guides for Ubuntu (verified), Fedora/Arch, macOS and Windows (WSL)
  with a performance disclaimer per kind of computer, chosen when you add
  the host. Controls exist only when the host front grants them; a plain
  supervisor stays read-only.

### Evidence

- `docs/qa/ai-team/write-proof-2026-09-11.md`: live writes through the
  front against Gas City 1.4.1 on the PC — sling with idempotent replay,
  nudge round trip (host refused: agent busy), stop, close batch, merge
  readiness — with receipts and stream events.
- Full suite: 3590 tests, 0 failed (table in `docs/qa/ai-team/README.md`).
- On-device: Android 15's seccomp rule found and worked around (Android
  builds of gc/bd/dolt running natively in Termux, agent in proot); polecat
  proven end to end on the emulator; on the phone the polecat starts in 7 s
  and works while Termux is on screen, but Android kills the tree once
  Termux goes to the background — mitigation documented for Sprint C.

## 1.0.41+42 — Local checkpoint (2026-09-11)

Built and verified locally; no CI, no public download.

### AI Team · Gas City (optional plugin, read-only first slice)

- **New optional plugin**, off by default and enabled per saved server:
  More › Plugins › AI Team. Add a Gas City supervisor by address and city,
  or accept the discovery card the app shows once when the connected
  computer runs one. Turning it off removes every cached value and secret
  for that server; deleting the server sweeps them too.
- **Workspace card**: one number, one sentence, one action. Agents
  working, progress bar with done / working / blocked segments, the runs
  that matter, and "Showing data from HH:MM · host unreachable" when stale.
- **AI Team home**: Runs (active → waiting/blocked → completed), Agents
  (fleet with state words and glyphs), Needs you (decisions, failed runs,
  review-ready, blocked agents). Host chip opens technical details with
  provider, version, city and the side-by-side terms ("Work · bead",
  "Run · convoy", "Agent · polecat").
- **Run detail**: Overview answers the five questions top to bottom;
  Work tab as a grouped list (graph view on wide screens with a
  dependency DAG, critical path and blocked chain); Agents tab scoped to
  the run; Timeline with filters and jump-to-latest.
- **Agent detail**: identity, runtime, current work, a step log parsed
  from the agent's transcript, and a live output page (mono, LTR,
  Follow). Output stays on the phone after the host forgets the session.
- **Activity**: AI Team items join the list in priority order; the gate
  sheet renders choices, confirmations, free-text prompts, gate beads and
  classified run failures, read-only for now ("Answer this on the host").
- **Usage**: "Team today · $0.42 est. · 12.4k tokens" on the run overview
  and agent runtime; always labelled estimated, absent without data.
- Every screen is localised (English and Arabic, ~350 new strings), laid
  out at 320 dp and 2.5× text, LTR and RTL.

### Plumbing

- `OrchestrationGateway` domain interfaces and product models with total
  state enums (`unknown` never crashes a list); Gas City DTOs and mappers
  that round-trip every recorded response; a Python fixture server with
  `normal`, `blocked`, `failed` and `stream-drop` scenarios; the read
  adapter with a host probe, problem+json handling and an SSE client that
  resumes with `Last-Event-ID`. Plain `http://` is accepted only to
  loopback and tailnet addresses (100.64.0.0/10, `*.ts.net`).
- Plugin-off regression: with the plugin off, Workspace, Activity and
  Settings contain no plugin widgets and the OpenCode gateway sees exactly
  the same calls as before.
- Real read proof against Gas City 1.4.1 on the PC, on loopback and over
  the tailnet: counts match `curl`, stream resume has no duplicates.

### Evidence

`docs/qa/ai-team/README.md` indexes the two spike reports (PC and phone),
the recordings, the read proof and the suite results. Headline from the
phone spike: Android 15's seccomp policy kills Go 1.26 programs that call
`faccessat2`; Gas City, beads and Dolt rebuilt for `GOOS=android` run
natively in Termux, with only the OpenCode agent inside proot.

## 1.0.40+41 — Local checkpoint (2026-09-10, later)

Built and verified locally on the same day as 1.0.39; no CI, no public
download.

### Delegation and approvals

- **Approvals per session** (Session menu › Session actions › Approvals):
  keep *Ask each time*, or let this phone answer each permission request
  with "Allow once" while it is connected. Subagent sessions can inherit the
  setting, and can override it. Nothing is ever saved as "always allow";
  the server's own deny rules still apply; a failed automatic answer leaves
  the request visible.
- Background subagents were proven end to end against a real OpenCode 2
  server and a live model: the parent gets one result card, it survives a
  cold restart, and the child stays out of the main session list.

### Reliability found during that proof

- Connecting to an OpenCode 2 host from a profile saved as OpenCode 1 no
  longer fails with a cast error; the app redetects the generation.
- A provider rejecting the model's credentials no longer shows an empty red
  banner; the banner names the problem and where to fix it.
- Choosing a model without a variant no longer reports "This choice is no
  longer available" after it was applied.
- A tool the server ran is never labelled "Not run".

### Handoff and launch surfaces

- **Continue on computer**: copy the exact terminal command that resumes
  this session in its project folder (OpenCode 1 or 2), with a pointer to
  session export for moving between servers.
- **Continue on phone**: show a QR with an `opencode-mobile://session` link
  that opens the same session on another phone holding the same saved
  server; unknown servers get an honest "not saved on this phone" state.
- **Pinned-session shortcuts**: long-press the app icon for up to four
  pinned sessions (titles only), plus Connect and New task.
- **Quick Settings tile**: shows how many requests need you and opens
  Activity; reads a cached count so it works while the app is closed.

## 1.0.39+40 — Local checkpoint (2026-09-10)

Arabic and right-to-left support, plus the daily-use fixes batch. Built and
verified locally; no CI run and no public download yet.

### Language

- Choose **System, English or Arabic** in Settings › Appearance. The choice
  is saved on the device, applies immediately without leaving the current
  screen, and survives restart. A failed save keeps the previous language
  and offers a retry.
- Every app-authored label, hint, error, sheet and empty state is translated.
  Code, file paths, URLs, commands, model and provider names and everything
  the server sends stay in their original form and direction.
- Narrow phones at 2.5x text keep every control reachable in both layout
  directions; long captions wrap instead of pushing buttons off the edge.

### Chat and workspace

- Background subagent results arrive as clear result cards with a link to
  the child session instead of raw wrapper text.
- The session menu groups the everyday views (Results, Find, Timeline,
  Changes, Todos, Subagents) as chips and folds display toggles and session
  actions into two expandable groups.
- While a run is active, the delivery hint or the Steer/Queue toggle appears
  only once you start typing. OpenCode 1 says plainly that steering mid-run
  needs OpenCode 2.
- Workspace and Activity show one inventory notice instead of repeating
  "loaded / incomplete" in three places, and say when a count is partial.

### Files and review

- Files remembers source-first ordering and your code wrap choice per saved
  server; the same choice follows code into snapshots, previews and diffs.
- Review controls stay reachable at compact sizes with large text.

### Appearance

- The theme picker previews real text, code, buttons and surfaces before you
  apply; browsing never changes the live theme, and Apply is explicit.

## 1.0.37+38 — Unreleased

Changes since **1.0.34+35** (2026-09-06), whose source is tagged
[`v1.0.34+35`](https://github.com/Eslamasabry/opencode-mobile-next/tree/v1.0.34%2B35)
at `b618d30`. This section describes the development candidate, including the
quota batch and subsequent integration work. Downloads are not yet available:
the combined verification and fresh Android installation checks are in progress.

### Reading and working from a phone

- Keep streamed Markdown readable while you scroll away from new messages.
  Horizontal code/table gestures do not change whether the conversation follows
  new content. Use **Jump to latest** when you are ready to catch up.
- Wrap code, open a full-screen snapshot with stable selection, or copy its
  original fenced body. Copy failures have a retry. Tables grow to fit their
  content, including large text and inline-code headings; code starts at the
  beginning of the line in both left-to-right and right-to-left layouts.
- Read CSV and TSV as literal tables or original source, view a bounded static
  SVG locally, and turn PDF pages on Android 10 or newer. Copy and Save retain
  original content; partial, unsupported and malformed files have explicit
  fallbacks. PDF rendering runs in a separate isolated process.
- Add a **Context capsule** from Chat Add: collect labeled notes, errors, code
  and selected screenshots, review them, then append to the existing draft.
  Cancel preserves that draft, and applying never sends it.
- Put permission, question and form blockers together under **Needs you** in
  Workspace, with each session shown once. Keep Search directly accessible and
  move occasional section actions into a labeled menu.
- Add Android **Connect** and **New task** launcher shortcuts. New task opens a
  blank conversation on the current server and preserves other saved drafts.

### Results, return visits and development services

- Add **Run results** to Activity digests: inspect server-recorded outcomes,
  command exit codes when supplied, and file evidence from completed tools.
  Incomplete history and missing outcomes remain explicit; an idle session does
  not become a success claim. Open the recorded output or original conversation.
- Show a compact Workspace return brief for unreviewed sessions and outstanding
  decisions. Review results, answer, or continue directly. Dismissal is saved for
  the exact displayed work and does not mark conversations read or resolve requests.
- Start a blank task in a fresh Git worktree on supported OpenCode 1 servers.
  Show preparation, failure and unconfirmed states, allow an explicit open-anyway
  choice, and leave created worktrees intact when the user stops waiting.
- Add **Manage project → Development services**. Save a command and visit URL
  without executing it; supported OpenCode 2 servers can start a managed service,
  inspect bounded output, and stop or restart the exact service the app created.
  Lost responses are reconciled before another start. Other servers retain the
  save/copy/visit actions.
- Keep queued prompts whose delivery was not confirmed for review instead of
  automatically sending them again after reconnect or restart. Review, edit,
  explicitly resend, or discard; a failed local write keeps the recoverable draft.
- Opt into check-in reminders for sessions seen busy at checks spanning the
  chosen interval. Activity shows due check-ins separately from pending
  decisions. Private notifications make at most one dispatch attempt per
  observed interval; sampled observations do not claim uninterrupted work.

### External agents and private connections

- Open **External agents** from Servers for the supported A2A 1.0 JSON-RPC
  text-task journey. Inspect an agent card, explicitly send a reviewed task,
  answer requested input, inspect bounded results, reopen saved tasks or request
  cancellation. Full unsent drafts survive Back and restart; uncertain sends
  require review and are never automatically resent.
- Connect through the official Android **Tailscale** app with install/open/return
  guidance, a reviewed HTTPS address and the existing server authentication and
  connection test. Typed fields survive the handoff. App presence is reported
  separately from VPN or server connectivity; no tailnet is configured for you.
- Open **Codex account** from a connected supported host's server menu. Start
  device-code sign-in explicitly, cancel the attempt owned by this view, and
  refresh account status and independently reported usage/rate windows. The
  host keeps credentials; missing data stays unavailable. Initial support is
  limited to the verified Codex CLI 0.153.4 protocol.

### Backlog follow-through

- Inspect installed plugins from Library on supported OpenCode 2 servers.
  Read status and safe package/source details; changing location or reconnecting
  clears old-source results and refreshes the inventory.
- View MiniMax general subscription-pool percentages through an explicitly
  configured collector. The app requests fresh consent for the selected provider;
  missing, unsupported or unverified allowances never become a zero balance.
- Check the app-managed Termux server's observed process state and version from
  Servers, with direct access to setup controls and no automatic restart.
- Copy a reviewed, safely quoted OpenCode 1 attach command for a reachable HTTPS
  server. The command contains no password and is revalidated before copying.
  Unsupported connections retain the metadata reference.
- Run the offline demo through production chat, streaming, edit permissions and
  diff review with its own in-memory gateway and stores. Reset and exit dispose
  the simulation while preserving saved servers and unsent work.

### On-device setup and session recovery

- Show setup progress immediately, including while Termux is still responding.
  Use a flat page with readable, colored output that fills the available height.
- Inspect the managed Ubuntu installation before choosing a path. A fresh install
  offers OpenCode 1 (**1.18.29**, the default) or OpenCode 2 beta
  (**0.0.0-beta-18600**). Existing installations retain their runtime choice across
  repair, stop and restart; the app does not silently migrate them.
- Guide the first Termux connection with copy/open, paste and keyboard-Enter
  illustrations. Verify automatically after returning from Termux, with explicit
  permission, error and retry states.
- Keep the installation elapsed time tied to the accepted operation, including
  backgrounding and reopening the setup screen. Reduce optional Ubuntu package
  installation while retaining Git, SSH, certificates and the pinned runtime.
- Require the compatible Ubuntu binary package explicitly during npm setup,
  retain version pinning and temporary-cache cleanup, and expose install output.
- Keep prior sessions and global search accessible when the project list is empty.
  This session fix also appeared in the focused 1.0.35+36 CI APK.
- Tidy **Workspace** so sessions are the subject: an empty Unreviewed work
  brief collapses to one quiet status line, the project-recovery notice is one
  short dismissible sentence, rows no longer repeat the open project's folder,
  **Manage** sits on the project row instead of a session-like row below, the
  server-wide search is labelled **All sessions**, and the composer pill is
  replaced by explicit **New session** and **Isolated task** actions.
- Redesign **All sessions**: conversations are grouped into one card per
  working directory, newest folder first and newest session first inside it.
  Folder chips narrow the list, a summary line counts sessions and folders,
  rows use relative times and the shared title rule, and folders that share a
  name are told apart by their parent.
- Never work in the server's home folder. Workspace now asks you to **Create a
  new folder** (managed Termux server) or **Open a project folder** by its path
  before a session can start; the home folder and the filesystem root are
  refused everywhere a location is chosen or restored, and a previously saved
  home-folder location is forgotten with a notice. The managed server starts in
  `/root/projects` instead of `/root`. Earlier conversations from the home
  folder stay reachable through **Search all sessions**.

### Saved prompts and attachment recovery

- Update the compatible file-picker and Riverpod dependencies, preserve the
  integrated file-rendering packages, and refresh exact dependency notices.

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
- Add an optional, temporary **Speak replies** switch. After consent and explicit
  Send, compatible OpenCode 1 connections can read the completed reply to that
  exact dispatched message. Unsupported or ambiguous reply ownership stays
  manual. Stop, Exit, backgrounding and scope changes retire pending speech.
  The microphone never opens automatically, and tool approvals remain visual.
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
  with reviewed attach commands where supported and session/project metadata
  references as the fallback. Neither includes sign-in secrets or executes
  a command automatically.

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
  Remote computer-hosted servers remain independently managed.

### Upgrade and delivery notes

- Stash storage now has a separate **256 MiB** vault budget, with up to **five
  attachments / 32 MiB of encoded payloads per stash**. Oversized legacy entries
  remain unmigrated and recoverable. Older app versions do not understand the
  new file-reference format; downgrade compatibility is not guaranteed.
- Local sign-in recovery is limited to **16 records / 512 KiB per profile**, with
  a recovery window of at most **24 hours**. Expiry or local forgetting does not
  cancel a remote process or revoke provider credentials. Profile deletion
  removes local recovery data only.
- Older app versions do not understand the queued-send dispatch marker and can
  retry an unconfirmed entry. Review those entries before downgrading.
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
