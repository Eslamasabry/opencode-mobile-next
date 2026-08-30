# OpenCode 2 capability gaps — product verdicts

Status: **proposed** (design only; no code, no build order committed).
Date: 2026-08-30. Companion to `docs/opencode2-ui-design.md` (LOCKED) —
this document extends that design language, it does not amend it.

Authoritative inputs:

- `docs/opencode2-protocol-notes.md` (live-captured wire behaviour, beta-18600)
- `docs/opencode2-port-matrix.md` §3 (the unused-surface inventory)
- `contracts/opencode2-openapi-beta-18600.json` (schemas quoted below)
- `docs/audits/ui-ux-audit-2026-08-29.md` §3 (the product model), §4 (P0s)
- `docs/opencode2-ui-design.md` §7 (gating rules), §9 (motion), conventions
- Live CLI: `opencode2 <cmd> --help` at v0.0.0-beta-18600
- Current code: `lib/api2/`, `lib/domain/server_gateway.dart`,
  `lib/ui/screens/activity_screen.dart`, `chat/timeline_sheet.dart`,
  `settings/personal_settings_screens.dart`

---

## 0. The rule this document obeys

The UI/UX audit's finding is that the app **exposes too much capability
simultaneously**, and its §3 product model says a user should feel three
objects: **Workspace** (where am I working), **Session** (what is the agent
doing and what does it need from me), **Output** (what changed, do I approve
it). Every verdict below is measured against that model, not against the API.

Consequences, applied without exception:

1. **A capability that does not serve one of those three objects does not
   ship.** Analytics, plugin inventories, daemon management and web search all
   fail this test in whole or in part.
2. **New capability folds into an existing surface, or is invisible.** This
   document proposes **one** new screen out of sixteen capabilities, and it is
   the lowest priority item in it. Everything else lands in the session actions
   sheet, the transcript, the probe verdict, Settings → Privacy, Settings →
   Server, or App diagnostics.
3. **"No" is a finished answer.** Nine of the sixteen are do-not-build. Most
   are developer-workstation concepts that acquire a mobile use case only if
   you invent one.
4. **v2-only surfaces vanish on v1** per design §7 rule 5 — hidden, not
   disabled, with no explainer, because the user has never seen them. New
   capability flags follow the existing `ServerCapabilities` convention
   (`lib/domain/server_gateway.dart:695`): default `false` in the const
   constructor like `forms`/`inbox`, set `true` in `api2ServerCapabilities`
   (`lib/api2/gateway_mappers.dart:32`).

Verdict vocabulary: **Build** · **Fold in quietly** (ships, but the user never
sees a new control) · **Do not build**.

---

## 1. Revert — staged preview and commit

**Verdict: Build. Highest priority in this document.**
Capability flag: `revertStaging`.

### 1.1 Does a phone user ever want this?

Yes — this is the strongest mobile case in the entire list, and it is the only
one where the phone is *better* than the workstation. On a laptop you undo with
`git checkout`. On a bus you have no shell, and the agent is still running.

### 1.2 The mobile situation it serves

You are on a bus. You sent "clean up the auth module" twenty minutes ago and
have been half-watching. You open the app and the agent has rewritten six files
in a way you can see is wrong from the diff header alone. You want it undone,
you want it undone before the next tool call compounds it, and you want to see
exactly which files you are about to throw away before you confirm.

### 1.3 The gap this closes is not cosmetic

`lib/api2/gateway_operations.dart:143` maps v1's one-shot `revertSession` onto
**`POST /session/{id}/revert/stage` only**, and `restoreSession` onto
`/revert/clear`. `revert/commit` **is called nowhere in `lib/` or `test/`.**

Per protocol notes §6.3 the v2 model is three-phase: `stage` computes the
revert and returns `Session.Revert` (`{messageID, partID?, snapshot?, files:
[FileDiff.Info]}`); `commit` applies it; `clear` discards it. So on a v2 server
the user taps "Revert last prompt", the app reports success, and the file
changes are still there — the session merely carries a staged `revert`
projection that the TUI will render as a pending revert. The one-shot mental
model does not survive the port. **Verify this against a live server before
scheduling — but design for the three-phase model either way**, because the
API is three-phase and a two-step confirm is the right mobile shape regardless.

### 1.4 Where it lives

Existing surfaces only:

- **Entry points stay exactly where they are**: `_SessionActionsSheet` "Revert
  last prompt" (`lib/ui/screens/chat/timeline_sheet.dart`), the `/undo` chat
  command (`chat_screen.dart:2576`), and message long-press "Revert to here".
- **The new step is a sheet, not a screen**: a **Revert preview sheet** built
  from the §3 permission-sheet anatomy — header, context line, a rounded-12
  `surfaceContainerHigh` resources card, and the pinned apply-bar recipe. It is
  the same component family, so it costs no new design language.
- **The staged state lives in the transcript**, not in a modal the user can
  lose: a `TranscriptMarker` (§4) plus a persistent strip above the composer,
  because a staged revert is a session-wide pending decision and leaving it
  invisible is how a user strands a session.

### 1.5 Interaction spec

**Step 1 — stage.** Tapping Revert calls `POST .../revert/stage`
`{messageID, files: true}` with a spinner-in-button (the `_PermissionDialogState`
pattern). No confirmation dialog before this: staging is non-destructive, and
an extra "are you sure" before a preview is the noise the audit complains about.

**Step 2 — the preview sheet.** Key `revert-preview-sheet`.

| slot | content |
|---|---|
| header | `Icons.history_rounded` + "Undo changes after this prompt" (`titleMedium`) |
| context line | `bodyMedium`: "This rolls the working tree back to the snapshot taken before *<prompt title, 1 line>*." |
| summary | `labelLarge`: "6 files · +18 −214" from `files[]` sums |
| files card | one row per `FileDiff.Info`: status glyph (`added`/`modified`/`deleted`), path in `AppMono` `bodySmall` ellipsized **from the left** (basename must survive), trailing `+n −n`. Caps at ~240 px and scrolls internally. Tapping a row opens the existing `_FileDiffView` (`chat/session_sheets.dart:239`) on `patch`. Key `revert-file-<path>` on each row, `revert-files` on the card. |
| empty case | `files` absent or empty → "No file changes to undo — this only removes messages after that point." The commit button stays enabled; the copy stops lying. |
| apply bar | key `revert-apply-bar`, apply-bar recipe (`Material` + `elevation: 6` + `SafeArea`) |

Apply bar, stacked full-width:

- `FilledButton` styled `error`/`onError` — **"Undo these changes"** — key
  `revert-commit`. `POST .../revert/commit`. This is the only destructive
  primary button in the app that is *not* preceded by a second confirm, and
  that is deliberate: the sheet **is** the confirmation, it shows the exact
  file list, and a second dialog would push the real information off screen.
- `TextButton` — **"Keep changes"** — key `revert-clear` → `POST
  .../revert/clear`, closes.
- Dismissing the sheet by scrim/back **does not clear** the stage; it leaves
  the staged banner (below). Copy on the banner tells the user how to finish.

**Staged banner.** While `session.revert` is non-null, a `TranscriptMarker`-family
strip sits above the composer (key `revert-staged-banner`): `Icons.history_rounded`
+ `labelSmall` "Revert staged — 6 files not yet undone", trailing `TextButton`
"Review" reopening the sheet. Reuses the `PendingSendsStrip` position and
bubble metrics (§5) so the composer area gains no new layout concept.

**Safety — the four failure modes that matter.**

| condition | treatment |
|---|---|
| session is busy (409 `SessionBusyError` on stage or commit) | Do not silently fail. Dialog: title "The agent is still working", body "Stop the current run before undoing its changes.", primary `FilledButton` **"Stop and stage revert"** (key `revert-stop-first`) → `POST .../interrupt` then retry stage once; secondary "Cancel". This is the bus case: the agent is mid-flight and stopping it *is* the intent. |
| working tree moved underneath | Before enabling `revert-commit`, re-read `GET /api/vcs/status` and compare paths. Any staged file that also has uncommitted status not attributable to the snapshot gets an inline warning row inside the files card: `Icons.warning_amber_rounded` in `tertiary`, "Changed since this snapshot — your later edits will be lost too." If ≥1 such file, the button label becomes **"Undo anyway"** and a `bodySmall` line above the apply bar names the count. Never block; the user may genuinely want it. |
| another client committed or cleared | `session.revert.committed` / `.cleared` events close the sheet with a toast "Handled on another device" — same treatment as the permission sheet's cross-device dismissal (§3). |
| commit fails (500/transport) | Sheet stays open, `form-error-banner`-style banner pinned above the apply bar (key `revert-error-banner`): "Could not undo the changes. Try again." Stage survives; nothing is half-applied from the client's side. |

**Transcript truth.** On `session.revert.committed`, insert a
`TranscriptMarker` (§4) `transcript-marker-revert-<messageID>`:
`Icons.history_rounded`, copy "Reverted — 6 files restored". The
`_EarlierMessagesPill` behaviour is unchanged. A revert must never be a silent
mutation of history.

**Gating.** v1 keeps its current one-shot dialog path unchanged (v1 has
`revert`/`unrevert`); only the preview sheet and the staged banner are behind
`revertStaging`. This is the rare case where the gate selects between two
flows rather than hiding one — say so in code comments so a future reader does
not "simplify" it into a hidden row.

### 1.6 Cost / risk

**M.** One new sheet built from two existing recipes, one strip built from an
existing strip, three new API calls on an existing gateway, plus the vcs-status
cross-check. Risk is concentrated in the stage/commit semantics — they must be
verified live, not from the spec.

**Ship before the founder demo.** It is the best story in the document ("the
agent went wrong, I undid it from my phone, here are the exact files"), and it
closes a live correctness gap where the app currently reports a revert it does
not perform.

---

## 2. View tracking (`POST /api/session/{id}/view`)

**Verdict: Build — but as plumbing plus one privacy control and one dot.**
Capability flag: `sessionViewTracking`.

### 2.1 Does a phone user ever want this?

They want its *effects* and must never meet its mechanism. Nobody wants a
feature called "view tracking". Everybody wants to stop being buzzed about a
session they are literally reading.

### 2.2 The mobile situation it serves

Two, and the second is the one that justifies the work:

1. You are in the transcript watching the run land. It finishes. Your phone
   buzzes to tell you the thing on your screen finished. Today
   `_canShowCodingAlert` (`lib/state/connection.dart:495`) already suppresses
   this *on this device while foregrounded*, so case 1 alone would not justify
   the endpoint.
2. You read the session on your phone at the bus stop, get to your desk, and
   the TUI still shows it as needing attention — or you pick the phone up
   again after a cold start and the app has forgotten what you read, because
   the local suppression state lives in memory. `POST .../view {idle}` moves
   the read watermark to the server, where it survives restarts and is shared
   with every client via `session.viewed`.

The payoff is a genuine **unread** signal. `Api2SessionTime` already parses
`idle` and `viewed` (`lib/api2/models.dart:199`) and **nothing consumes them**;
`time.idle > time.viewed` means "this session finished while you weren't
looking", which is exactly the state Activity's Recent section currently cannot
express.

### 2.3 Where it lives

- The POST itself: **invisible**, in `AppConnection`.
- The visible payoff: an unread dot on session rows in Activity ("Running" and
  "Recent" sections, `lib/ui/screens/activity_screen.dart:331,342`) and on
  Workspace session rows. 8 px `primary` dot in the row's leading slot, with a
  `Semantics(label: 'Unread')` wrapper because status must never be colour
  alone (audit §13). Key `activity-unread-<sessionID>`.
- The disclosure: **one row in Settings → Privacy & permissions**
  (`personal_settings_screens.dart:212`), beside the existing Storage-used and
  Clear-drafts rows.

### 2.4 Interaction spec

**When the app POSTs** (all three, nothing else):

1. The chat screen for session S becomes the foreground route **and** S is
   idle — debounced 1000 ms so a scroll-through of Activity does not mark six
   sessions read.
2. App resumes from background with a chat screen already on top.
3. The user taps a "finished" notification for S (the tap is a read).

Body `{idle: <session.time.idle>}`. Skip entirely when `time.idle == 0`
(never ran), when `time.idle <= time.viewed` (already marked), for subagent
sessions (`parentID != null` — they are never separately notified today), and
whenever the app is backgrounded. Never send from a background poll: "the
server thinks I read it because my phone was in my pocket" is the exact bug
this feature would otherwise introduce.

**Failure handling: silent.** 204 → update the local `Session`. Any error →
one retry at the next foreground transition, then drop. This must never
produce a toast, a banner, or a retry loop; a failed read receipt has no user
consequence, and surfacing it would be worse than the bug.

**The privacy control.** Key `privacy-read-receipts`, `SwitchListTile` in the
Privacy & permissions list:

> **Share read state with the server**
> Lets OpenCode stop re-notifying you about sessions you have already read.
> Your server records the time you last opened each session.

Default **on**. Off → no POST ever; unread dots fall back to a local
`SharedPreferences` watermark per session, so the feature degrades instead of
disappearing. The same two sentences go into `PRIVACY.md` under the existing
server-data section. This is not optional politeness: the launch audit's trust
line is that the app states what leaves the device, and "when you read things"
is new data the server did not previously have.

**Gating.** v2-only. Per design §7 rule 5 the row is **hidden** on v1 rather
than shown disabled — the inverse list in §7 already establishes that v2-only
features get no explainer, and an explainer here would be the first time a v1
user ever heard the phrase "read state".

### 2.5 Cost / risk

**S.** One call site cluster in `connection.dart`, one settings row, one dot,
one PRIVACY.md paragraph. Risk: over-firing. Keep the trigger list to the
three above and write the widget test that asserts no POST while backgrounded.

**Ship before the demo**, bundled with the privacy row — never without it.

---

## 3. Export / import

**Verdict: Build export. Do not build import.**
Capability flag: `sessionExport`.

### 3.1 Does a phone user ever want export?

Yes, for two different reasons that happen to share an endpoint.

**Colleague case:** the agent solved something interesting and you want to send
the session to someone. This matters more on v2 than it did on v1, because
design §7 rows 10–11 **hide session share entirely** (beta-18600 has no
share/unshare). Export is now the *only* way a session leaves the phone.

**Portability case:** the public launch audit's item 32 ("add user-facing local
storage management and export where safe") and the general "can I get my data
out" expectation. A JSON export answers it in one tap.

### 3.2 Does a phone user ever want import?

**No.** Import writes a session into a server from a JSON file. On a phone
there is no natural source file, the act is a migration chore, and the
`POST /api/session/import` body wants `{info, messages, location?}` with
parents imported before children — a batch operation with no phone-shaped
moment. The one plausible mobile motive is resurrecting v1's "Continue here"
(port matrix §2 row 7: rebuild steal on export + import + move), and that is a
*cross-server session move* wizard — a multi-step flow needing a second server
profile, a location picker, and conflict handling. Do not build it
speculatively. Revisit only if research shows people run two servers.

### 3.3 Where it lives

The chat command that already exists. `_ChatCommandAction.export` → "Export
transcript" (`chat_screen.dart:2576`) today renders local Markdown and writes
it with `FilePicker.saveFile` (`chat_screen.dart:3028`). That row gains a
format step. **No new entry point, no new menu, no More tile.**

### 3.4 Interaction spec

Tapping **Export transcript** opens a compact sheet (≤4 controls → sheet, per
§2's layout rule). Key `session-export-sheet`.

| control | key | behaviour |
|---|---|---|
| Radio card group, 2 options | `export-format-markdown` / `export-format-json` | **Markdown (readable)** — subtitle "The conversation as you see it. Good for sharing." Unchanged existing code path. **JSON (complete)** — subtitle "Everything the server stored, including tool output and file contents." |
| `SwitchListTile` **Redact sensitive data** | `export-sanitize` | Visible only when JSON is selected (`AnimatedSize`, 240 ms, the §2 `when` treatment). Default **on** → `?sanitize=true`. Subtitle: "Removes file contents and command output that may contain secrets." Mirrors the CLI's `--sanitize`. |
| pinned apply bar | `export-apply-bar` / `export-confirm` | `FilledButton` "Save file", spinner-in-button while fetching. |

Filename: `opencode-<slug-or-8-char-id>.md` / `.json` — keep the existing
truncation. Delivery: `FilePicker.saveFile` for both, as today.

**Deliberately not adding a share sheet for now.** The app has no `share_plus`
dependency (see `pubspec.yaml`), and `FilePicker.saveFile` already lands the
file wherever the user wants, from which every Android share path starts. A
true share-sheet handoff is a one-line follow-up (`share_plus` + `XFile`) worth
doing *after* the demo, not a new plugin dependency on the critical path.

**Failures.** 404 `SessionNotFoundError` → "That session is no longer on the
server." Transport error → the standard normalized error copy with **Try
again**. Large exports: a full JSON export of a long session is megabytes;
show the spinner, do not preview, do not hold two copies in memory — stream
straight into the picked file.

**Gating.** The JSON option is hidden on v1 (`sessionExport == false`); the
sheet then contains one option and collapses to today's direct save, so v1
loses nothing and gains no dead control.

### 3.5 Cost / risk

**S–M.** One sheet, one gateway method, one existing writer. Privacy risk is
real and handled by defaulting `sanitize` on — a raw export carries file
contents and shell output.

**Ship before the demo.** "Here is the whole session, sent to you" is a
30-second demo beat and it answers the portability audit item.

---

## 4. Instructions entries

**Verdict: Build, scoped to a single note. After the demo.**
Capability flag: `sessionInstructions`.

### 4.1 Does a phone user ever want this?

Yes, but not as the API models it. `GET/PUT/DELETE
/api/session/{id}/instructions/entries[/{key}]` is a key/value store of
arbitrary JSON (≤8 KB, `InstructionEntryValueTooLargeError` above), announced
to the model at the next step boundary. That is a *tooling* surface. The live
capture shows the server's own entries (`core/environment`, `core/date`), and
the spec's key pattern `^[a-z0-9][a-z0-9._-]*$` excludes `/`, so those
server-owned keys are not client-writable — a helpful natural boundary.

A key/value editor on a phone is exactly the over-exposure the audit condemns.
A *single note the agent always sees* is a feature people actually ask for.

### 4.2 The mobile situation it serves

The agent keeps using `npm` in a `pnpm` repo, or keeps touching the migrations
directory. You are away from the machine, you cannot edit `AGENTS.md`, and you
do not want to retype the correction in every prompt. You write it once and it
rides along for the rest of the session.

### 4.3 Where it lives

One row in the existing `_SessionActionsSheet` (`chat/timeline_sheet.dart`),
under the **Context** section label beside "Compact context":
`Icons.sticky_note_2_outlined`, label **"Session note for the agent"**,
value `'note'`. It opens a small sheet. No screen, no tab, no More tile.

### 4.4 Interaction spec

Sheet key `session-note-sheet`. Body: title "Session note", `bodySmall`
explainer "The agent sees this with every message in this session. It is not
part of the conversation.", one outlined `TextField`
(`minLines: 3, maxLines: 8`, key `session-note-field`, 8000-char counter),
apply bar (`session-note-apply-bar`) with `FilledButton` **Save** (key
`session-note-save`) and `TextButton` **Remove** (key `session-note-clear`,
shown only when a note exists).

Wire: read `GET .../instructions/entries`, use the single key `mobile.note`,
value `{"text": "..."}`; Save → `PUT .../instructions/entries/mobile.note`;
Remove → `DELETE`. Other keys present on the session are **read and ignored**,
never listed and never edited — the app owns one key.

Confirmation of effect: `session.instructions.updated` already exists as an
event; render it as a `TranscriptNotice` (§6) with
`Icons.sticky_note_2_outlined`, header "Session note updated", preview = the
new text. The user must be able to see that the thing they typed reached the
agent.

Failures: 413 → inline `errorText` "Too long — keep the note under 8 KB"
(counter turns `error` past 8000). 404 session → "That session is gone."

**Gating.** v2-only; hide the row (design §7 rule 3: menus list possible
actions only).

### 4.5 Cost / risk

**S.** One sheet, three calls, one notice variant reuse. Risk is scope creep:
the moment this becomes a key/value list it has violated the audit. Write the
constraint into the widget test — one field, one key.

**After the demo.** It is genuinely useful and genuinely not urgent, and the
session actions sheet should not gain rows in the same release that the
convergence work is trying to make it read cleanly.

---

## 5. Session stats (`GET /api/session/stats`)

**Verdict: Build last, small, in Settings. Optional demo candy.**
Capability flag: `sessionStats` (already named in design §7's inverse list).

### 5.1 Does a phone user ever want this?

Partly. Honest reading: this is a curiosity, not a workflow. The CLI's own
framing — `stats` = "Show shareable usage statistics" — tells you the intended
genre is a year-in-review flex, not an operations dashboard. But there is one
real anxious moment it answers, and phones are where anxiety happens.

### 5.2 The mobile situation it serves

You are away from the machine, you left an agent running on a large task, and
you want to know what this month has cost you. That question has no answer in
the app today; the context meter shows one session's window, nothing shows
spend across sessions.

It is emphatically **not** an Activity concern. Activity means "what needs me
now" (audit §8). A cost chart in Activity would re-create exactly the
duplicate-control-centre problem UX-P0-01 just removed.

### 5.3 Where it lives

**Settings → Server**, one row: **"Usage"**, subtitle carrying the headline
number so the row itself answers the common question without navigation —
"$12.40 · 34 sessions in the last 30 days". Key `settings-usage-row`.

It opens **the one new screen this document proposes**: `UsageScreen`
(`lib/ui/screens/settings/usage_screen.dart`). Justification, since the bar is
high: a 30-day range selector plus four totals plus a per-model breakdown plus
an activity series cannot live in a bottom sheet at 2.5× text scale, and every
existing screen it could squat in is about the present, not the aggregate. It
sits behind two taps in Settings, which is where low-frequency reference
material belongs (audit §11).

### 5.4 Interaction spec

`SessionStats.Info` gives `{range, sessions, subagents, prompts, steps, tokens,
cost, tools, activeDays, streak, activity[], models[]}`.

- **Range chips** (`ChoiceChip` row, keys `usage-range-today`,
  `usage-range-30d`, `usage-range-year`, `usage-range-all`) → `from`/`to`
  epoch-ms. Default 30d. Always send `timezone` from the device, or daily
  buckets land on the wrong days.
- **Four stat tiles** (2×2 grid, rounded-12 `surfaceContainerHigh`): Cost,
  Sessions, Prompts, Tokens. Money formatted to two decimals; tokens compacted
  (`1.2M`). Key `usage-tile-<name>`.
- **Streak line**: `labelSmall` "Active 14 of 30 days · 5-day streak" —
  from `activeDays`/`streak`. One line, no flame iconography.
- **Activity**: a plain bar series from `activity[]` (`{date, steps}`), fixed
  height 88, `primary` bars on `surfaceContainerHigh`, no axis furniture, no
  chart library, no animation beyond the standard 400 ms tween the
  `_ContextMeterLine` already uses. Key `usage-activity`.
- **Models**: list of `SessionStats.ModelUsage` rows — model ref, steps, cost.
  Key `usage-model-<providerID>-<id>`.
- **Tool reliability**: request `tools=summary` by default and render one line
  ("1,204 tool calls · 98% succeeded"). An expander **"Show tool reliability"**
  (key `usage-tools-expand`) refetches with `tools=detail` and lists
  `SessionStats.ToolUsage` rows (name, calls, failed, p50 duration). Do not
  fetch detail you are not rendering.
- **Project scope**: default to the current project (`project=<projectID>`)
  with a single toggle "All projects" (key `usage-scope-all`) — the mobile
  question is usually about the project in front of you.

Empty range → the existing empty-state component: "No activity in this range."
Failure → normalized error with **Try again**. Never invent progress or
extrapolate.

**Gating.** v2-only; the Settings row is **hidden** on v1 (design §7's inverse
list already names session stats as v2-only-hidden — that overrides rule 2 for
this row).

### 5.5 Cost / risk

**M**, almost entirely in the chart and the range plumbing. No risk to any
existing flow.

**After the demo** — with one caveat: if the demo needs a "this is a finished
product" beauty shot and there is slack, this is the cheapest beautiful screen
in the document. That is the only argument for pulling it forward, and it is
an argument about the demo, not about users.

---

## 6. Server binding (`opencode2 service set hostname 0.0.0.0`)

**Verdict: Fold in quietly — one copy change, no service management UI.**
No capability flag (it is copy in an existing verdict).

### 6.1 Does a phone user want to manage the background service?

**No, and mostly they cannot.** `service start|stop|restart|status|get|set|
unset` manage a daemon through a local state file
(`~/.local/state/opencode/service.json`, protocol notes §1). There is no HTTP
API for any of it. The decisive argument: **if the service is stopped, the app
cannot reach it to ask it to start.** A "Start server" button that only works
when the server is already running is worse than no button.

### 6.2 But one fact inside that command set is the most important sentence here

`service set hostname 0.0.0.0` is how the server becomes reachable from the
phone at all. A server bound to `127.0.0.1` is invisible across the network,
and today that produces a generic transport failure whose advice ("check the
address and that `opencode2 serve` is running") is *wrong for the actual
cause*. The address is fine. The server is running. It is listening on
loopback. This is the single most likely way a founder demo dies in its first
minute.

### 6.3 Where it lives

The existing Test-connection verdict row (design §1, key
`server-probe-verdict`) and the Setup guide (`guide_screen.dart`).

### 6.4 Interaction spec

Extend the §1 probe error taxonomy with a fifth row:

| condition | verdict copy | follow-up |
|---|---|---|
| transport error / timeout **and** the host is a private LAN address (RFC1918, `.local`, or a bare hostname) — not loopback, not a public name | "Could not reach the server. If OpenCode is running on that machine, it may be listening on localhost only." + a mono command block: `opencode2 service set hostname 0.0.0.0` and `opencode2 service restart` | `TextButton.icon` **"Copy commands"**, key `server-probe-fix-command` → clipboard, snackbar "Commands copied". Users will paste this into a terminal or into a message to themselves. |

Loopback hosts (`127.0.0.1`, `localhost`) keep today's copy — on-device Termux
is the expected case there and the advice would be wrong.

The same two commands, verbatim, go into the Setup guide's remote-server
section and the remote-setup docs, next to the existing security guidance
(audit §6: do not recommend LAN HTTP; pair this with the existing TLS advice
rather than presenting `0.0.0.0` as free).

**Also worth one line of documentation, not a build:** `opencode2 pair`
("Show server pairing information") is the natural future backing for the
QR / `?auth_token=` pairing flow that design §1 deliberately deferred. When
pairing UI is revisited, start there. Do not stub anything now.

### 6.5 Cost / risk

**XS.** Copy plus a host-classification helper plus a clipboard button.

**Ship before the demo.** Highest value-to-effort ratio in this document.

---

## 7. Plugins (`GET /api/plugin`, `plugin list|add|remove`)

**Verdict: Do not build a plugin surface. Fold *failed* plugins into
diagnostics.** Capability flag: `pluginInventory` (diagnostics only).

### 7.1 Does a phone user ever want this?

A plugin inventory: no. Plugins are installed on the machine with
`opencode2 plugin add`, they are global configuration, and the phone can only
read the list. A "Plugins" tile in the More grid is precisely the noise design
§7 already deleted the Tools inventory tile for (row 20) — and the More grid is
the shop window.

Installing or removing plugins from the phone: **emphatically no.** That is
authorizing arbitrary code execution on your development machine from a device
you might be holding in a pub. There is no v2 API for it either (config writes
are gone), so building it would mean inventing a channel that does not exist.

### 7.2 The one situation that does justify something

A plugin has `status: "failed"` with an `error` string. The agent then behaves
oddly — a tool is missing, a hook does not fire — and the user on a phone has
no way to learn why. That is a *diagnostic* need, not a management need.

### 7.3 Where it lives

**App diagnostics** (`lib/ui/screens/app_diagnostics_screen.dart`), one
section, no actions. Key `diagnostics-plugins`.

### 7.4 Interaction spec

Section header "Server plugins" with a one-line summary
(`labelSmall`, `onSurfaceVariant`): "4 active · 1 failed". Failed entries only
are listed: plugin `id` in `AppMono` `bodySmall`, `error` beneath in `error`
colour, `SelectableText` so it can be copied into a bug report. Active plugins
are counted, not listed. No connect, no reload, no remove. If `data` is empty
the section renders "No plugins loaded" rather than disappearing — in a
diagnostics screen an absent section reads as a broken screen.

**Gating.** Hidden on v1 (no endpoint).

### 7.5 Cost / risk

**XS.** After the demo. Nobody demos a plugin list.

---

## 8. Web search (`GET /api/websearch/provider`, `POST /api/websearch`)

**Verdict: Do not build.**

### 8.1 Does a phone user ever want this?

No. The agent already searches the web — as a *tool*, server-side, under the
existing permission flow. `POST /api/websearch` from the client would make the
app a search engine: a second, worse browser inside a coding client, on a
device where the real browser is one swipe away. Every result would then need
its own reader view, its own link-safety policy (audit UX-008), and its own
place in the composer.

There is no mobile moment where "search the web from inside my coding agent's
transcript" beats "switch to Chrome". If a user wants the *agent* to search,
they ask the agent — which already works.

### 8.2 The residual

`GET /api/websearch/provider` answers "is a search provider even configured
here?", which is a diagnostic. If it is ever wanted, it is one line in the same
diagnostics section as plugins, not a feature. Not proposed here; the section
should not accumulate lines nobody asked for.

**Do not build. No follow-up.**

---

## 9. Synthetic messages (`POST /api/session/{id}/synthetic`)

**Verdict: Already used as plumbing. Never surface it.**

### 9.1 Status

Already wired: `addSessionLocationReminder`
(`lib/api2/gateway_operations.dart`) posts a synthetic
`<system-reminder>` with `resume: false` when a session's directory changes —
the correct v2 replacement for v1's fake prompt (port matrix §2). Nothing to
build.

### 9.2 Does a phone user ever want a user-facing version?

No, and it is a footgun. A synthetic message is context the model sees that is
*not* presented as the user's turn. Offering "whisper something to the agent"
produces two predictable failures: users use it for instructions (for which
§4's session note is the honest surface, because it persists and is visible),
and users are then confused when the transcript does not show what they said
in the place they said it. Transcript truth is a principle the design already
defends in three places (form cards never disappear, markers are inserted for
every switch, reverts leave a marker). A user-writable invisible message
violates it directly.

Two internal uses may arise later and are both fine because they stay
invisible: attaching Review context, and image captions. Neither needs a
control. Note that the audit's UX-103 explicitly wants Review → prompt handoff
to land in the **composer**, visibly — so do not use synthetic for that.

**Do not surface.**

---

## 10. Wait (`POST /api/session/{id}/wait`)

**Verdict: Do not build UI. Hand to the platform lane as an optional
transport experiment.**

### 10.1 Does a phone user ever want this?

They cannot want it — it has no user-visible form. It is a long-poll that
returns 204 when the agent loop goes idle (~3 s in the live capture).

### 10.2 The only real question is an engineering one

The background service already learns idleness from SSE (`session.status`,
`session.execution.succeeded`) while holding a foreground connection. `/wait`
could replace status polling with one blocking request per watched session.
Whether that is *better* on Android depends on doze behaviour, the
foreground-service time limit the app already handles
(`live_background.dart:122`), and per-session connection cost — none of which
is a design question.

Verdict: no product decision here. If the platform lane wants to measure it,
measure it; adopt only on evidence of a battery or latency win. Nothing in this
document depends on it.

---

## 11. Editing a sent message (`PATCH /api/session/{id}/message/{messageID}`)

**Verdict: Do not build.**

### 11.1 What it actually is

Not what the name suggests. The body is `{content: [AssistantContent...]}` and
protocol notes §5 say it "edits a completed **assistant** message in an idle
session" (409 `SessionBusyError` otherwise), emitting
`session.message.content.updated`. It rewrites **what the model said**, not
what you sent.

### 11.2 Does a phone user ever want it?

No. Offering "edit the agent's reply" on a phone invites users to rewrite
history and then wonder why the model reasons from something it never
concluded. It is a transcript-falsification tool that exists for
post-processing tooling.

Meanwhile the need users *actually* have — "I sent the wrong prompt" — is
already covered three ways: Stop (`chat-stop-button`), the pending-sends strip
(cancel returns the text to the composer as a draft, design §5 — "that *is*
the edit affordance"), and §1's revert for anything already executed.

Design §7 row 14 already ruled that PATCH-edit must not be dressed up as
delete. This extends that ruling: do not surface it in any form.

**One future exception worth naming so it is not rediscovered as a feature:**
redacting a secret that leaked into a transcript is a legitimate privacy
operation. If that is ever built it is a *privacy* tool with its own
destructive confirm, living beside saved permissions — not a pencil icon on a
message bubble.

---

## 12. Console login (`opencode2 console login`)

**Verdict: Do not build.**

`console login` authenticates to OpenCode Console, the hosted org product.
Design §7 row 8 already **hid** the console org list and switch because
beta-18600 exposes no org endpoints at all (port matrix §2: `GET
/experimental/console/orgs` and `POST /experimental/console/switch` are both
**NONE**). Building a login for a service whose management surface does not
exist produces a screen that can authenticate and then do nothing.

There is also a trust argument the launch audit supports: a third-party
account OAuth flow inside a client whose entire security story is "one Basic
credential to one server you run" is a new and unnecessary trust surface.

**If org endpoints return**, this needs no new UI at all: design §8's
Integrations detail sheet already specifies OAuth and **command** connect
methods with attempt cards, polling and cancel — a console login would drop
straight into that as another `method`. That is the right time and the right
place.

---

## 13. `debug agents | config | paths`

**Verdict: `agents` and `paths` — do not build. `config` — fold one read-only
list into diagnostics.** Capability flag: `configSources`.

### 13.1 `debug agents`

Already covered: `GET /api/agent` powers the model/agent picker
(`lib/ui/widgets/pickers.dart`) and the More hub's active-setup card. A second,
debug-flavoured rendering of the same list is duplication of exactly the kind
UX-P0-01 was written about. **Do not build.**

### 13.2 `debug paths`

Data/config/cache/state directories **on the server machine**. Nothing on a
phone can act on them. Pure workstation trivia. **Do not build.**

### 13.3 `debug config` — the one that earns its place

`GET /api/config` returns an **array** of entries lowest→highest priority
(`{type: "document", path?, info}` | `directory` | `agents` | `claude`,
protocol notes §11). That answers a question a remote user genuinely cannot
answer otherwise: *which file is making the agent behave like this?* You cannot
`cat` a file from a bus, and v2 config is read-only, so seeing the source list
is the whole available remedy.

**Where:** App diagnostics, directly beneath §7's plugin section. Key
`diagnostics-config-sources`.

**Spec:** section header "Configuration sources", `labelSmall` note "Listed
lowest to highest priority — later entries win." One row per entry: type chip
(`document` / `directory` / `agents` / `claude`) plus `path` in `AppMono`
`bodySmall`, ellipsized from the left, `SelectableText`, long-press copies the
path. No editing, no navigation into files, no diff. Empty → "No configuration
files loaded."

**Gating:** hidden on v1 (v1's `/config` returns a merged document, not a
source list — a v1 rendering would be a different feature).

**Cost:** XS, shares the diagnostics work with §7. **After the demo.**

---

## 14. Priority ordering

Ordered by *mobile consequence per unit of build*, not by API interest.

| # | Item | Size | Ship | Why here |
|---|---|---|---|---|
| 1 | **Server-binding copy in the probe verdict** (§6) | XS | **Before demo** | Highest ratio in the document. The demo's riskiest minute is the first one, and today's advice for the most common failure is actively wrong. Copy, not code. |
| 2 | **Revert: stage → preview → commit** (§1) | M | **Before demo** | The best mobile story here *and* a correctness gap: the app currently stages a revert and never commits it, so "Revert last prompt" does not revert files on v2. Everything else in this list is an enhancement; this one is a promise the app is not keeping. |
| 3 | **View tracking + privacy row + unread dots** (§2) | S | **Before demo** | Makes notifications feel like they know what you have seen, gives Activity a real unread state from data already parsed and ignored, and costs one settings row. Never ships without the privacy row. |
| 4 | **Export: format sheet, JSON, sanitize** (§3) | S–M | **Before demo** | Export is now the *only* way a session leaves the phone (share is gated off on v2), it answers a launch-audit item, and it is a 30-second demo beat on a control that already exists. |
| 5 | **Session note** (single instructions entry) (§4) | S | After demo | Real user value, no urgency, and the session actions sheet should not gain rows during the convergence release. |
| 6 | **Diagnostics: failed plugins + config sources** (§7, §13) | XS | After demo | Two small read-only sections in one screen that already exists; turns "the agent is behaving oddly" from unanswerable into answerable. Bundle them. |
| 7 | **Usage screen** (§5) | M | After demo | The only new screen proposed, deliberately last. Pull forward *only* if the demo wants a beauty shot and there is slack — that is an argument about the demo, not about users. |

Nothing above changes navigation, adds a bottom-tab destination, or adds a More
grid tile. Six of the seven land in surfaces that already exist.

**Dependencies:** items 1–4 are mutually independent. Item 2 wants the existing
`_FileDiffView` and `vcs/status`, both already wired. Item 6 wants no new
gateway pattern. Nothing here blocks the phases in design §10.

---

## 15. Do not build

| Capability | One-line reason |
|---|---|
| `POST /api/session/import` (as a user feature) | No phone-shaped source file; the only real motive is a cross-server move wizard nobody has asked for. |
| `PATCH .../message/{id}` (edit a sent message) | It rewrites the **assistant's** words, not yours — a transcript-falsification tool; the real need is already served by Stop, pending-send cancel, and revert. |
| `GET /api/plugin` as a screen or More tile | Read-only inventory of machine-side config; same noise §7 already deleted the Tools tile for. Failed plugins alone go to diagnostics. |
| `plugin add` / `plugin remove` | Authorizing arbitrary code execution on your dev machine from a phone. No v2 API exists for it either. |
| `POST /api/websearch` in the composer | Turns a coding client into a worse browser; the agent already searches as a permissioned tool. |
| `POST /api/session/{id}/synthetic` as a user control | Invisible user-authored context breaks transcript truth; the session note is the honest surface. (Stays as existing plumbing.) |
| `POST /api/session/{id}/wait` as UI | It has no user-visible form; at most a background-transport experiment for the platform lane, adopted on measurement only. |
| `service start/stop/restart/status/get/set/unset` management UI | No HTTP API, and if the service is down the app cannot reach it to start it. Only the `hostname 0.0.0.0` *fact* ships, as copy. |
| `console login` | Authenticates to a product whose management endpoints do not exist in beta-18600 (§7 row 8 already hid them); a dead-end trust surface. If it returns, design §8's Integrations attempt-card absorbs it with no new UI. |
| `debug agents` | Duplicate rendering of the agent list the picker already shows. |
| `debug paths` | Server-machine filesystem trivia a phone cannot act on. |

---

## 16. New keys and capability flags introduced here

Widget keys (kebab-case `ValueKey`, per the locked convention):

`revert-preview-sheet`, `revert-files`, `revert-file-<path>`,
`revert-apply-bar`, `revert-commit`, `revert-clear`, `revert-stop-first`,
`revert-error-banner`, `revert-staged-banner`,
`transcript-marker-revert-<messageID>`,
`activity-unread-<sessionID>`, `privacy-read-receipts`,
`session-export-sheet`, `export-format-markdown`, `export-format-json`,
`export-sanitize`, `export-apply-bar`, `export-confirm`,
`session-note-sheet`, `session-note-field`, `session-note-apply-bar`,
`session-note-save`, `session-note-clear`,
`settings-usage-row`, `usage-range-today`, `usage-range-30d`,
`usage-range-year`, `usage-range-all`, `usage-scope-all`,
`usage-tile-<name>`, `usage-activity`, `usage-model-<providerID>-<id>`,
`usage-tools-expand`,
`server-probe-fix-command`,
`diagnostics-plugins`, `diagnostics-config-sources`.

Capability flags to add to `ServerCapabilities`
(`lib/domain/server_gateway.dart:695`) — default `false` in the const
constructor like `forms`/`inbox`, `true` in `api2ServerCapabilities`:

`revertStaging`, `sessionViewTracking`, `sessionExport`,
`sessionInstructions`, `sessionStats`, `pluginInventory`, `configSources`.

All seven follow design §7 rule 5 (v2-only → **hidden** on v1, no explainer),
including their Settings rows — the inverse list in §7 already establishes that
override for session stats, and the same reasoning applies to the rest: a v1
user has never seen these, so a disabled row would be the first they hear of
them.

---

## 17. Open questions to settle before implementation

1. **Revert semantics against a live server.** Confirm that `stage` alone does
   not mutate the working tree and that `commit` does, and capture what
   `session.revert` looks like on `Session.Info` between the two. The whole of
   §1 (and the claim that the current mapping under-delivers) rests on it.
2. **Does `stage` return `files` reliably** when called with `{files: true}`
   for a large change set, or is it capped? The preview sheet's honesty depends
   on the list being complete; if it can be partial, the summary line must say
   so.
3. **`session.viewed` fan-out.** Verify that a `POST .../view` from the phone
   actually suppresses the TUI's unread state — that is the second and stronger
   half of §2's justification.
4. **Export size.** Measure a long session's JSON export; if it routinely
   exceeds tens of MB, §3 needs a size warning before the save, not after.
5. **Stats timezone handling.** Confirm the server buckets `activity[]` by the
   supplied `timezone` and not UTC, or the streak line will lie for users east
   of UTC.
