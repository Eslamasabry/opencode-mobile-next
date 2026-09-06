# Full backlog — epics, stories, order — 2026-09-06

*Refined 2026-09-06 by read-only verification passes against source @
`b618d30` (protocol contract, state/privacy, platform, UI/design-system);
story claims corrected where the code or contract disagreed.*

Decomposition behind the [shaping summary](roadmap-2026-09-06.md). Formats:
epic hypotheses (if/then + validation measures), user stories (Mike Cohn +
one Gherkin scenario each — single When/Then per story, per repo test
discipline). UI specs reference the mobile design system (§ tokens from
`lib/ui/app_theme.dart`); architecture references current files. Persona:
[developer away from the desk](../product-persona.md). This is a planning
artifact — queues and readiness rows remain the delivery ledger.

## 1. Journey spine (story-map backbone)

The persona's repeat journey is the backbone; every story hangs under one
step. Walking skeleton (thin end-to-end slice) = E1's device journey.

| Backbone step | Ships today | Remaining gaps → epic |
|---|---|---|
| **Install & keep using** | Sideload APK, signer lineage, update notice | Evidence of real install/upgrade; publication hygiene → **E1**, **E2** |
| **Return to the right work** | Sessions, pins, unread, notifications | Native interruption/upgrade preservation evidence → **E2** |
| **Give a useful instruction** | Composer, drafts+attachments recovery, stash, voice, camera | Stash payload migration → **E5**; web attach → **E6** |
| **Understand progress** | Streaming, tool cards, Running work, usage | Live cross-client verification → **E1/E2**; skills live-check → **E1** |
| **Unblock confidently** | Permissions/forms/questions, notification replies | Credential account switching → **E3**; MCP removal → **E4** |
| **Review & steer** | Diffs, staged revert, export/import | — (parity tail verified in E1) |
| **Trust the tool** | Privacy posture, diagnostics redaction | Persona validation loop → **E10** |

## 2. Epic register

| ID | Epic | Size | Lane | Depends on | Gate |
|---|---|---|---|---|---|
| E1 | Release evidence & publication | M | Now | — | D1 (wording) for S7 only |
| E2 | Journey hardening | M | Now→Next | E1-S3 findings | — |
| E3 | Provider credential management | M | Next | — | — |
| E4 | MCP lifecycle completion | S | Next | serialize with E3 (same files) | — |
| E5 | Prompt-stash payload migration | S | Next | serialize with E2 chat edits | — |
| E6 | Web search attach | M | Later | E2 evidence + persona rule 4 | D-explicit call |
| E7 | Localization | L | Later | — | demand signal in issues |
| E8 | Desktop runtime verification | S | Later | — | contributor hands |
| E9 | Shorebird patch readiness | S | Later | — | D2 (promise patches?) |
| E10 | Persona validation | S | parallel | E1 candidate exists | D3 (go/no-go) |
| E11 | Advanced server surface | — | Hold | — | per-item triggers (§4) |
| E12 | iOS remote-control client | L | Next (gated) | D4 prerequisites | phases §3 |
| F1a | Voice: speak the run | S/M | Frontier→promote-now | E2 done | — |
| F1b/c | Voice conversation → ambient | L | Frontier | probe + E10 | [innovation doc](innovation-2026-09-06.md) |
| F2 | Plugins + mobile variants | M | Frontier | contract spike + upstream | 〃 |
| F3 | Cross-server attention inbox | M/L | Frontier | E10 demand probe | 〃 |
| F4 | Session handoff phone↔desktop | S | Frontier→promote-now | — | 〃 |
| F5 | Smart completion digests | S/M | Frontier | concierge probe | 〃 |
| F6 | Launch surfaces (S1 promotable) | S | Frontier→promote-now | — | 〃 |
| F7 | Phone-first overnight mode | S/M | Frontier | Termux signal | 〃 |

Order within Now: **E1-S1 → E1-S2/S3 (device in hand) → E1-S4/S5 → E1-S6
spike in parallel → E1-S7 after D1**. Next cycle: E2 fix batch, then E3 → E4
(serial), E5 alongside if a second editor exists (no shared files with E3/E4).

## 3. Epics in detail

### E1 — Release evidence & publication

**Hypothesis.** If we run one complete, recorded device journey against the
signed `v1.0.34+35` candidate, then we convert ~10 "implemented, pending
verification" readiness rows into evidence and can publish with honest claims.
**Validation.** Within one cycle: every readiness row cites a dated artifact
under `docs/qa/` or `docs/verification/`; zero rows still say "pending" for
the covered scope.

**E1-S1 · Post-tag doc sweep** — *As a* new installer, *I want* current docs,
*so that* I follow the real release path, not the previous one.
- **Given** the `v1.0.34+35` tag exists **When** I read README "Where things
  stand", `docs/backlog/backend.md:142`, and readiness row 5 **Then** all three
  describe the current release state (or explicitly mark publication pending).
- Touch: README.md, backend.md one-liner, readiness row, release-alpha-notes.

**E1-S2 · Install/upgrade smoke** — *As a* user on `v1.0.33+34`, *I want* the
documented one-uninstall path, *so that* I can move to the new signer line
without mystery.
- **Given** a device with `v1.0.33+34` installed **When** I install the signed
  `1.0.34+35` APK following README instructions **Then** the observed behavior
  matches the documented signer-mismatch/data-loss notes exactly, recorded in
  `docs/qa/`.

**E1-S3 · Core journey device pass** — *As a* developer away from my desk,
*I want* the whole repeat journey on a physical device, *so that* release
claims rest on one contiguous real experience.
- **Given** a signed build on a phone **When** I complete
  pair → new chat → compose text+photo → background 10 min → resume from
  notification → approve a permission → review a diff → leave a follow-up
  **Then** every step succeeds or files a numbered finding (feed for E2),
  captured in `docs/qa/release-journey-<date>.md`.

**E1-S4 · TalkBack pass** — *As a* screen-reader user, *I want* the same
journey audible, *so that* accessibility is interaction-proven, not
semantics-proven. Focus order, live-region announcements for streaming
completion and permission arrival, 48dp targets verified by touch exploration.
- **Given** TalkBack enabled **When** a permission request arrives while the
  transcript is focused **Then** it is announced without stealing focus.

**E1-S5 · Cross-client live checks (#53/#57)** — *As a* user with a desktop
client open, *I want* my phone to agree with it, *so that* I never act on
stale state.
- **Given** the same v2 session open on a second client **When** the desktop
  switches model/agent and completes a run **Then** the phone shows the new
  selection and unread state after reconnect, with no duplicate approvals.

**E1-S6 · Skill-activation spike (time-boxed)** — Learning, not shipping: find
a beta server build whose skill catalog is non-empty, exercise activation
live, or record activation as unverifiable this cycle in
`docs/verification/session-skills.md`. Spike output feeds E3 or dies quietly.

**E1-S7 · Publication** — *As a* prospective user, *I want* a verified public
release, *so that* I can install without GitHub Actions access.
- **Given** D1 answered and S2–S5 artifacts recorded **When** the maintainer
  publishes through the existing tag workflow **Then** the release page,
  checksums, and notes match the shipped artifact. (Maintainer-only action.)

**Interaction/UI.** None new — this epic *observes* existing UI. Findings
format: numbered, screen + step + observed vs expected.
**Architecture.** None. **Verification.** The epic *is* verification.

### E2 — Journey hardening

**Hypothesis.** If we fix what E1-S3/S4 actually observe plus the three known
native races, then the shipped alpha stops losing user work in daily phone
conditions. **Validation.** Re-running the failing journey step passes; no new
regressions in focused serial tests.

**E2-S1 · Observed-break fix batch** — container story; each E1 finding
becomes its own fix story (workflow-steps pattern; spawn, don't hoard).
**E2-S2 · Notification reply races (#10)** — *As a* user replying from the
shade, *I want* exactly one outcome, *so that* a race can't double-approve.
- **Given** an in-app sheet open and a notification reply arriving for the
  same `requestID` **When** both are in flight **Then** the first wins, the
  second reconciles to "already resolved", and no second server call fires.
  Verify badge/ordering with multiple pending sessions natively.
**E2-S3 · Native interruption matrix (cycle 23/24 residuals)** — camera
capture killed mid-pickup, cloud photo picker, process death: recovery store
hands back the pending result in the original conversation.
- **Given** a photo picked but not added **When** the process is killed and
  relaunched **Then** the recovery prompt offers preview/add/discard scoped to
  the original session.
**E2-S4 · Upgrade data preservation** — *As a* upgrading user, *I want* my
drafts, queue, pins, and stash to survive, *so that* updating costs nothing.
- **Given** populated drafts/queue/pins/stash on the old build **When** the
  signed upgrade installs **Then** all four reappear verbatim (distinct from
  E1-S2's signer path: this is same-signer in-place).
**E2-S5 · Fresh final-candidate APK** — build + apksigner verify for the
post-E2 commit (cycle 24's "fresh APK build" residual).

**Architecture.** Fixes land in the owning surfaces; `lib/state/connection.dart`
and chat part files are single-owner — one fix batch at a time, serial. The
contract's typed 409 `ConflictError`/`SessionBusyError` semantics supply the
"already resolved" copy for S2 — cite them rather than inventing wording.

### E3 — Provider credential management (v2)

**Hypothesis.** If we expose per-credential switch/rename/remove and
command-method sign-in, then multi-account users stop disconnecting everything
to change accounts — the last high-fit v2 gap. **Tiny act of discovery**
before build: watch one multi-account user attempt a switch today (E10 can
carry this). **Validation.** Account switch ≤3 taps from Integrations; zero
credential echoed in logs/diagnostics (test-asserted).

Story split = CRUD pattern (epic-breakdown Pattern 2), plus one workflow story:

**E3-S1 · See credentials per provider** — *As a* multi-account user, *I want*
each provider card to list its credentials with the active one marked, *so
that* I know what I'm switching between.
- **Given** a provider with two stored credentials **When** I expand its card
  in More → Integrations **Then** both are listed (label only — the contract
  exposes `{type, id, label}` via `GET /api/integration` connections; there is
  no credential-list route and no added-date field) and the Active badge
  reflects the last `credential.switched` event — cold start renders
  unknown-active honestly rather than guessing.
  Secrets never render.
**E3-S2 · Switch active credential** — … *I want* to set another credential
active *so that* new runs use the other account without disconnecting.
- **Given** two credentials **When** I tap Set active on the inactive one
  **Then** the badge updates only after `credential.switched` confirms the
  new active credential (the 204 confirms the call, not the state — no
  optimistic flip), and switches made by other clients reconcile the same
  way.
**E3-S3 · Rename a credential** — inline edit from the row's overflow menu;
label only.
**E3-S4 · Remove a credential** — overflow → `confirm_sheet` (destructive
pattern, established haptics); removing the active credential requires
choosing a successor or confirming "none".
**E3-S5 · Command-method sign-in** — *As a* a user of a command-only provider,
*I want* guided terminal sign-in, *so that* I'm not told to use a flow that
doesn't exist for my provider.
- **Given** a provider whose methods include `command` **When** I tap Connect
  **Then** I see the command in a copyable mono block (JetBrains Mono, code 12)
  with a "Waiting for authorization…" live-region status and Cancel; polling
  reflects start/status/cancel truthfully.
**E3-S6 · Resume a pending attempt** — *As a* an interrupted user, *I want*
OAuth/command attempts to survive navigation or app restart, *so that*
finishing sign-in doesn't restart it.
- **Given** an OAuth attempt in flight **When** I background the app past
  process death **Then** Integrations offers "Finish setting up <provider>?"
  with Resume/Cancel, replaying the pinned location snapshot (BE-007/008
  context), never re-prompting from scratch.

**Interaction & UI.** Provider card expands in place (progressive disclosure;
no new screen). Credential rows: `surfaceContainerLow` inline surface, 48dp
row, overflow via 24dp icon button with tooltip. Active badge = status text +
`AppTheme.statusColor` (never color alone). Status changes announce via
live-region node (permission-title pattern). Sheets: 24 top radius, actions
pinned above keyboard inset; rename dialog 22. All strings to ARB, no baseline
increase.
**Architecture.** Extend `IntegrationGateway` (domain): enumerate
credentials from the existing integration read (no list route exists),
`activateCredential`, `renameCredential`, `removeCredential`, command
`start/status/cancel`. `Api2OperationsGateway` implements against the
captured routes with location scoping mirroring BE-008 (attempt-pinned
snapshot); v1 adapter leaves capability false → **hide, don't disable**
(capability-gating rule). `credential.updated`/`credential.switched` are
parsed today but collapsed into refresh hints with their payloads discarded
(`lib/api2/events.dart`) — targeted badge updates require typing the
`credential.switched` payload `{integrationID, credentialID}` first;
otherwise reconcile by catalog refetch and keep unknown-active rendering.
Persistent attempt state (E3-S6) stores `attemptID`, `integrationID`,
location snapshot, and expiry only — **never** the one-time `code` or
`answer` inputs — and must rehydrate into each new gateway generation
(transports are rebuilt per connection; an un-rehydrated attempt throws
"no longer tracked" today). All calls through `prepareActionTransport()`.
Never echo secret material — diagnostics sanitization already covers; add
fixture asserting command-block text stays out of logs. New capability flag
touches both `server_gateway.dart` and the `api2ServerCapabilities` const
in `gateway_mappers.dart` — the same serial lane E3/E4 already occupy.
**Dependencies/ownership.** Serializes with E4 (both edit
`lib/api2/gateway_operations.dart` + `lib/domain/server_gateway.dart`).

### E4 — MCP lifecycle completion

**Hypothesis.** If MCP servers can be removed from the phone, then the add
workflow stops being a one-way door. **Validation.** Removal verified live
against beta-18600 (restart behavior recorded truthfully per BE-011).

**E4-S1 · Remove an MCP server** — *As a* a user who added a server by mistake,
*I want* to remove it from the phone, *so that* my server config stays clean.
- **Given** an MCP server listed under Integrations → MCP **When** I choose
  Remove from the row's overflow and confirm in the destructive sheet **Then**
  `DELETE /api/mcp/{server}` fires with the pinned location query, the row
  disappears, and copy states the runtime/restart limit truthfully.
**Interaction & UI.** Same row/overflow/`confirm_sheet` recipe as E3; removal
copy distinguishes runtime removal from persistent config (BE-011 wording).
**Architecture.** `removeMcpServer` beside `addMcpServer`; list reconciliation
by refetch (volatile-stream rule — no removal event exists, only
status/tools/resources refetch pings; 404 `McpServerNotFoundError` is typed).
Reuse the existing `mcpRuntimeAdds`/`mcpConfigWrites` capability pair for
truthful copy — do not invent a new flag. Wire test in
`test/product_repository_test.dart` fixture shape.

### E5 — Prompt-stash payload migration

**Hypothesis.** If stash attachments move from the preferences blob to the
file-backed vault, then large payloads stop bloating prefs and the stash
inherits bounded disk + real cleanup. **Validation.** Migration transparent on
first read; prefs blob shrinks; deletion sweep collects files.

**E5-S1 · File-backed stash payloads + migration** — *As a* a user with
attachment-heavy stashed prompts, *I want* payloads on disk, *so that*
stash/save stays fast and bounded.
- **Given** stash entries with inline data URLs **When** the store loads after
  upgrade **Then** payloads migrate transparently to the vault
  (`lib/state/draft_attachments.dart` pattern: app-private files, checksum,
  bounded), entries render identically, and a one-time cleanup purges the old
  blob values.
**E5-S2 · Deletion and disk hygiene** — profile deletion collects the
profile's stash files (extend `deleteProfileAndLocalData`); bounded-disk
refusal surfaces a recovery banner, never silent eviction (cycle-21 rule).
**E5-S3 · Missing-payload recovery** — a stashed entry whose file is gone
offers keep-text/discard explicitly (cycle-22 pattern).
**Architecture.** `lib/state/prompt_shelf.dart` + composer stash surface
(`lib/ui/screens/chat/prompt_stash.dart` — serialize with E2 chat edits) +
`lib/state/connection.dart` for the deletion cascade. **Vault hazard
(verified):** the draft GC builds its retained set from drafts only and runs
after every draft transaction — stash payloads in the same vault directory
would be silently deleted. Use a separate owner namespace with its own
`collect` step, or merge stash refs into the retained map; decide budgets
explicitly (vault caps are draft-shaped: 256 MiB total, 32 MiB per draft —
a 50-entry shelf needs its own byte/age decision and a statement of whether
the total cap is shared). Stored-format change → migration notes in PR.

### E6 — Web search attach (gated)

**Hypothesis.** If search is a supporting input flow — query, review sources,
deliberately attach — then users feed the agent grounded context without the
agent silently browsing. **Tiny acts of discovery:** (1) read the
`/websearch[/provider]` contract shapes against beta-18600 captures; (2)
concierge test — manually paste 2 search results into prompts with 3 users;
only build the flow if they value it. **Start condition:** E2 evidence says
compose/review is solid AND maintainer explicitly calls it next (persona rule
4).

**E6-S1 · Capability + provider discovery** — v2-only flag (default false,
adapter sets true); empty/multiple provider states handled.
**E6-S2 · Search sheet** — entry from composer tools (extends the
capability-gated `_PromptTool` sheet pattern behind a `supportsWebSearch`
flag); query field, results as `surfaceContainerLow` rows (title, domain,
favicon via the existing domain-only favicon path — never a full URL image
fetch). Single-shot results — the contract defines no cursors; an
unconfigured server's 503 renders as setup guidance, not an error.
- **Given** results shown **When** I tap a result's link icon **Then** it
  opens through `openExternalLink` with the host visible pre-open — never
  `launchUrl` (security invariant).
**E6-S3 · Attach selected results** — multi-select → Attach → reference chips
in the composer (existing chip anatomy); attached set editable before send.
**E6-S4 · Send with the prompt** — attached results ship via
`GET/POST /api/session/{id}/synthetic` (context injection without a user
turn — the contract's purpose-built mechanism, decided over prompt-text
mangling); sent message renders them as references, and export includes
them.
**Architecture.** `WebSearchGateway` in domain; api2 adapter; UI reads only
the gateway. Events: none expected (request/response); volatile rules n/a.
All external URLs through the link gate everywhere in the sheet.

### E7 — Localization (demand-gated)

**Hypothesis.** If non-English demand is real (issue signal), then
externalization + one pilot locale grows the audience honestly; otherwise it
is premature completeness. **Trigger:** recurring non-English reports/issues
(#15/#18–20 referenced). **Stories:** S1 string inventory + externalization
batches (ARB only, ratchet never rises); S2 pseudo-RTL mirroring audit
(start/end paddings, icon mirroring, back handling); S3 locale picker
(More → Appearance, platform face retained); S4 first locale pilot chosen
from actual demand. UI per design system: text-scale 2.5x and 320dp checks
are part of each batch.

### E8 — Desktop runtime verification (contributor-gated)

**Hypothesis.** If contributors hand-verify the packaged builds, then
"experimental" becomes a supported claim. **Stories:** S1 real-machine `.deb`
install + first-run report (guide + QA template); S2 Windows run + report
(workflow already refuses release attachment until this exists); S3 follow-up
fixes for window-state/multi-display findings, if any. Code from us only
after reports land.

### E9 — Shorebird patch readiness (conditional on D2)

**Hypothesis.** If patch delivery becomes a promise, one rehearsed drill makes
it trustworthy. **Stories:** S1 patch drill on a dev baseline — string-change
patch, next-launch `ShorebirdUpdateNotice` pickup, rollback path recorded;
S2 notice a11y check (live region, 48dp). `auto_update: false` and the
single-owner update service stay untouched (release-blocker test guards this).

### E10 — Persona validation (parallel discovery track)

**Hypothesis.** If 3–5 representative users run the repeat journey on their
phones, then observed hesitation re-ranks E6/E7/E11 with data instead of
instinct. **Validation.** ≥3 recorded sessions; findings triaged into E2 fixes
or priority changes in this document.
**Stories:** S1 recruit + session script (tasks mirror the spine; record
hesitation, lost input, misunderstood state); S2 synthesis + re-rank memo.
**Interaction.** No product UI; artifact is the script + memo under
`docs/qa/`.

### E11 — Advanced server surface (hold lane)

Dispositioned, not forgotten. Promote only when its trigger fires:

| Surface | Trigger to promote |
|---|---|
| Persistent session terminals | Users report Running work insufficient for their shell workflow |
| Workspace create/destroy, branch discovery | Desktop/secondary persona evidence (E10) |
| Message content update | Editing requests after attach/Review confusion reported |
| One-shot generate, server-internal controls, destructive worktree reset | Never without a phone workflow — standing non-goal |

### E12 — iOS remote-control client (gated on D4)

**Hypothesis.** If the controller ships on iOS as a remote-server-only
client, then the primary persona (developer away from the desk) gains phone
choice — and the codebase proves its portability claim. On-device server is
explicitly out (platform disposition 2026-09-06: iOS forbids `fork`/`exec`).
**Validation.** A TestFlight build completes the core journey — paste-pair →
chat → approve → review — against a real server, with no feature claiming
to work that doesn't.

**Why this is tractable:** every Android-only surface routes through
`PlatformCapabilities` (`lib/platform/platform_capabilities.dart` — written
anticipating exactly this port); `main.dart`'s desktop window setup is
platform-guarded so iOS skips it; `mobile_scanner`, `record`, `sherpa_onnx`,
`flutter_secure_storage`, `file_picker`, `flutter_timezone` all ship iOS
implementations. The five `oc/*` channels have no iOS halves — they stay
gated off until their phase.

**Phase 0 · Prerequisites (maintainer, = D4):** Apple Developer account;
macOS build story (local Xcode or GitHub macOS runner); TestFlight
go/no-go. Nothing below starts without these.

**E12-S1 · Green shell in CI** — on a macOS host: `flutter create
--platforms=ios .`, add `PlatformCapabilities.ios()` (all gates false),
privacy manifest stub, CI workflow (analyze + test + `flutter build ios
--no-codesign`).
- **Given** the `ios/` target exists **When** macOS CI runs **Then** analyze
  and the serial suite pass and an unsigned build artifact is produced —
  no Android/Linux workflow changes.

**E12-S2 · Remote core journey** — servers screen, paste-pairing (QR stays
hidden), chat with SSE streaming, permission/form approval, drafts, model
picker, export via iOS share sheet. **Truthful limitation, stated in-product
and in release notes: no background alerts on iOS v1** — the SSE transport
lives only in foreground/short-background; push would require a relay that
violates the no-third-party privacy posture.
- **Given** an iOS build paired to a server **When** the app is backgrounded
  mid-run and returned to **Then** the transcript reconciles by refetch
  (existing volatile-stream rule) and the UI never implies it was watched
  live while closed.

**E12-S3 · iOS-native surfaces, one slice each (order by E10 signal):**
QR pairing (camera permission + `mobile_scanner` iOS — the package was
ready; our gate was the blocker), voice input (`record` + sherpa iOS; an
`oc/voice` iOS half in the AppDelegate, single-owner channel rule), share-in
(a share-extension target — the largest native lift, cut if review friction
is high), foreground-only local notifications, WidgetKit home widget.

**E12-S4 · Distribution:** signing + TestFlight, App Store review notes
explaining the no-account, user-hosted-server model, final privacy manifest
(camera/mic usage strings), PRIVACY.md iOS section (no APNs, no relay).

**Architecture.** No domain/state changes are expected — that is the
portability payoff being tested. New code: `PlatformCapabilities.ios()`
flipping gates per phase, channel iOS halves where a phase demands them,
`ios/` runner, one CI workflow. Widget tests pump iOS via
`debugPlatformCapabilities(TargetPlatform.ios)` using the existing
debug seam. **Park criterion:** D4 unanswered after one planning cycle →
E12 parks with no code stranded (phases 1–2 touch only gates, `ios/`, CI).

## 4. Sequencing, dependencies, ownership

```
E1 ──S3 findings──▶ E2 ──evidence──▶ (E6 gate)
│                                    └─ E10 feeds re-rank of E6/E7/E11
└─ E3 ──serialize──▶ E4        E5 (parallel if 2nd editor; else after E2)
E7, E8, E9: triggered lanes, no ordering between them
```

- **Now:** E1 (S7 blocked on D1). **Next:** E2 → (E3 → E4 serial) + E5.
  **Later:** gated lanes. **E12 (iOS):** blocked on D4; phases 1–2 touch only
  `platform_capabilities.dart`, `ios/`, and CI — parallel-safe beside E3–E5
  on a second editor.
- Single-owner serialization: E3+E4 share `gateway_operations.dart` /
  `server_gateway.dart`; E2 chat fixes + E5 share chat part files. Never two
  editors on `lib/state/connection.dart`, the chat library, or a MethodChannel
  pair in one cycle.
- Every code story's definition of done: analyzer clean, focused serial tests
  green, screenshots + a11y notes for UI, privacy notes for anything touching
  credentials/URLs/notifications, migration notes for stored-format changes
  (E5, E3-S6), ARB-only strings.

## 5. Decision points (maintainer)

1. **D1** — publish wording/channel for `1.0.34+35` (blocks E1-S7 only).
2. **D2** — is Shorebird patching a promise? (decides E9).
3. **D3** — green-light E10 sessions (produces the only real data this plan
   can get).
4. **D4** — iOS prerequisites: Apple Developer account, macOS build story
   (local Xcode vs CI runner), TestFlight go/no-go — unblocks E12 phase 1.

## 6. Frontier lane — new work streams

Innovation streams live in [innovation-2026-09-06.md](innovation-2026-09-06.md):
voice mode (speak-the-run → conversation → ambient), plugins with mobile
variants, cross-server attention inbox, session handoff, completion digests,
launch surfaces, and Termux overnight mode. Each is a probe-gated bet with
kill criteria; **F1a, F4, and F6-S1 are promote-now** (platform-shaped risk,
no demand unknown) and form the natural post-E2 cycle alongside E3–E5.
