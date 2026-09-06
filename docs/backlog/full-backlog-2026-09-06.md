# Full backlog — epics, stories, order — 2026-09-06

*Refined 2026-09-06 by read-only verification passes against source @
`b618d30` (protocol contract, state/privacy, platform, UI/design-system);
story claims corrected where the code or contract disagreed.*

**Lead correction:** the [execution plan](roadmap-2026-09-06.md) now owns
ordering and evidence levels. This inventory preserves scope, not commitments
or readiness. Earlier review conclusions are leads until their cited code,
contract, or device result supports the specific claim. Sizes remain estimates.

Decomposition behind the [shaping summary](roadmap-2026-09-06.md). Formats:
epic hypotheses (if/then + validation measures), user stories and testable
acceptance scenarios (including failure/race cases; a single When/Then is not
a repository requirement). UI specs reference the mobile design system (§ tokens from
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
| **Review & steer** | Diffs, staged revert, export/import | Final-candidate device evidence still belongs to E1; not yet verified here |
| **Trust the tool** | Privacy posture, diagnostics redaction | Persona validation loop → **E10** |

## 2. Epic register

| ID | Epic | Size | Lane | Depends on | Gate |
|---|---|---|---|---|---|
| E1 | Release evidence & publication | M | Continuous gate | Actual candidate | Separate publication approval for S7 |
| E2 | Journey hardening | M | Now→Next | E1-S3 findings | — |
| E3 | Provider credential management | M | Next | — | — |
| E4 | MCP lifecycle completion | S | Next | serialize with E3 (same files) | — |
| E5 | Prompt-stash payload migration | M | Next | serialize with E2 chat edits | Transaction/GC/deletion proof |
| E6 | Web search attach | M | Later | E2 evidence + persona rule 4 | D-explicit call |
| E7 | Localization | L | Ongoing externalization; translations later | Human language review | No ratchet increases |
| E8 | Desktop runtime verification | S | Later | — | contributor hands |
| E9 | Shorebird patch readiness | S | Later | — | D2 (promise patches?) |
| E10 | Persona validation | S | parallel | E1 candidate exists | D3 (go/no-go) |
| E11 | Advanced server surface | — | Hold | — | per-item triggers (§4) |
| E12 | iOS remote-control client | L | Parallel preparation | macOS for native build | Signing only gates distribution |
| E13 | Provider usage & remaining quota | M/L | Next product slice | Provider/account contract + collector boundary | One provider end-to-end first |
| F1a | Voice: speak the run | M | Next differentiator | Audio/privacy/engine proof | execution plan |
| F1b/c | Voice conversation → ambient | L | Frontier | probe + E10 | [innovation doc](innovation-2026-09-06.md) |
| F2 | Plugins + mobile variants | M | Frontier | contract spike + upstream | 〃 |
| F3 | Cross-server attention inbox | M/L | Frontier | E10 demand probe | 〃 |
| F4 | Session handoff phone↔desktop | S/M | Candidate | CLI/deep-link scope proof | 〃 |
| F5 | Smart completion digests | S/M | Frontier | concierge probe | 〃 |
| F6 | Launch surfaces | S/M | Candidate | Native routing and exposure review | 〃 |
| F7 | Phone-first overnight mode | S/M | Frontier | Termux signal | 〃 |
| F8 | Demo mode (zero-setup first run) | S/M | Candidate | Complete demo isolation test | 〃 |
| F9 | Tailnet & tunnel connectivity | S/M | Secure-path design | HTTPS/loopback policy unchanged | 〃 |
| F10 | Codex / pi / ACP integration research | L | Separate protocol spikes | Authenticated remote transport for CLI-only backends | 〃 |

Pull order is in the [execution plan](roadmap-2026-09-06.md): validate the
existing pin diff, E13 quota proof/UI, independent iOS preparation, E5/input
correctness, then voice and onboarding. E1 tests the actual candidate; it is
not a prerequisite to all feature research. E3/E4 serialize shared adapters.

## 3. Epics in detail

### E1 — Release evidence & publication

**Goal.** Run a recorded device journey against an identified, verified
candidate and attach evidence only to the requirements actually exercised.
The `v1.0.34+35` source tag alone does not prove a signed APK is available.
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
and chat part files are single-owner — one fix batch at a time, serial. Do
not interpret every 409 or busy response as "already resolved": reconcile the
exact request and scope. Keep recoverable failures actionable; do not revive
resolved requests by rolling back an old optimistic snapshot.

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
pattern, established haptics). Re-read the displayed connection identity;
do not assume which account is active when the server cannot report it.
Verify server behavior for removing the active credential before promising
successor selection or a reset-to-none operation.
**E3-S5 · Command-method sign-in** — *As a* a user of a command-only provider,
*I want* guided terminal sign-in, *so that* I'm not told to use a flow that
doesn't exist for my provider.
- **Given** a provider whose methods include `command` **When** I tap Connect
  **Then** I see the declared sign-in instructions and accurate attempt
  status with Cancel. A method named `command` does not prove the response
  contains a copyable command; confirm the start/status payloads and whether
  the command executes on the server before writing the UI.
**E3-S6 · Resume a pending attempt** — *As a* an interrupted user, *I want*
OAuth/command attempts to survive navigation or app restart, *so that*
finishing sign-in doesn't restart it.
- **Given** an OAuth attempt in flight **When** I background the app past
  process death **Then** Integrations offers "Finish setting up <provider>?"
  with Resume/Cancel, replaying the pinned location snapshot (BE-007/008
  context), never re-prompting from scratch.

**Interaction & UI.** Provider card expands in place (progressive disclosure;
no new screen). Credential rows: `surfaceContainerLow` inline surface, 48dp
minimum target, overflow with a 24dp glyph inside a 48dp button with tooltip.
Active badge = status text +
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
Invalidate event-derived active state on a stream gap; refetch cannot recover
a field the read contract does not expose. A missing event cannot leave an
activation action spinning forever: show accepted/unconfirmed with recovery.
Persistent attempt state (E3-S6) stores `attemptID`, `integrationID`,
location snapshot, and expiry only — **never** the one-time `code` or
`answer` inputs — and must rehydrate into each new gateway generation
(transports are rebuilt per connection; an un-rehydrated attempt throws
"no longer tracked" today). Repository operations use
`prepareActionRepository()`; gateway calls use `prepareActionTransport()`.
Recheck profile/location/repository identity after wake and every await.
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
Reuse `mcpRuntimeAdds`/`mcpConfigWrites` for scope copy, not as proof that
removal is supported. Give the remove operation its own capability or an
optional callable gateway surface with truthful unsupported handling. Wire test in
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
capability-gated `_PromptTool` sheet pattern behind a server-operation
flag); query field, results as `surfaceContainerLow` rows (title, domain,
favicon via the existing domain-only favicon path — never a full URL image
fetch). Single-shot results — the contract defines no cursors; an
unconfigured server's 503 renders as setup guidance, not an error.
- **Given** results shown **When** I tap a result's link icon **Then** it
  opens through `openExternalLink` with the host visible pre-open — never
  `launchUrl` (security invariant).
**E6-S3 · Attach selected results** — multi-select → Attach → reference chips
in the composer (existing chip anatomy); attached set editable before send.
**E6-S4 · Send with the prompt** — keep selected results local until explicit
submission. `POST /api/session/{id}/synthetic` is an available *mutation*,
not a GET/read or a guaranteed atomic attachment+prompt transaction. Compare
it with a reviewed prompt-context block before choosing the transport; prove
cancel, duplicate/uncertain response, staged revert, ordering and export
semantics. Do not inject context while the user merely browses search results.
**Architecture.** `WebSearchGateway` in domain; api2 adapter; UI reads only
the gateway. `websearch.updated` invalidates discovery; synthetic context
also affects history/inbox state. Normal scope/reconnect rules still apply.
All external URLs through the link gate everywhere in the sheet.

### E7 — Localization (ongoing foundation, sequenced translations)

Existing locale/RTL issues remain in scope; lack of interview data is not
evidence of no demand. Continue externalization now; choose pilot language
and human review capacity before promising translated support.
**Stories:** S1 string inventory + externalization
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

### E12 — iOS remote-control client (build and distribution gates separated)

**Hypothesis.** If the controller ships on iOS as a remote-server-only
client, then the primary persona (developer away from the desk) gains phone
choice — and the codebase proves its portability claim. On-device execution
is outside this port's scope: ordinary App Store sandbox/background constraints
do not provide a drop-in Termux environment. This is not a claim that every
form of emulation or local computation on iOS is impossible.
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

**Phase 0 · Preparation:** audit plugin targets, bootstrap, secure-storage
entitlements, picker/URL behavior, lifecycle and capability tests. Scaffold
with the pinned Flutter tooling in a controlled diff; Linux can host source
preparation, but native compilation/simulator validation needs macOS/Xcode.
Paid Apple enrollment and signing credentials gate TestFlight/distribution,
not architecture work, widget tests, or an unsigned simulator build.

**E12-S1 · Green shell in CI** — generate the iOS target using the pinned
Flutter (`flutter create --platforms=ios .` only after reviewing generator
side effects). Keep unsupported Android features gated; add the actual required
entitlements/privacy declarations, not empty placeholders. On macOS, run
analyze, serial tests and `flutter build ios --simulator`; separately prove
the device target with `flutter build ios --release --no-codesign`.
- **Given** the `ios/` target exists **When** macOS CI runs **Then** analyze
  and the serial suite pass and a usable simulator artifact is produced.
  Passing source/widget gates is not native plugin, device, or signing proof.

**E12-S2 · Remote core journey** — servers screen, paste-pairing (QR stays
hidden), chat with SSE streaming, permission/form approval, drafts, model
picker and export/save through a verified iOS file path (a share sheet is a
separate native integration, not already available). **Truthful limitation, stated in-product
and in release notes: no background alerts on iOS v1** — the SSE transport
liveness is not guaranteed during suspension. Later push would need an
explicit APNs/backend design and privacy review; it is not inherently forbidden
by a no-analytics policy. Do not disguise long-lived SSE as an iOS background mode.
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

**Architecture.** Reuse the gateways; verify rather than assume lifecycle,
storage and native plugin compatibility. Tests assign
`debugPlatformCapabilities = const PlatformCapabilities(platform: TargetPlatform.iOS)`
and restore it in teardown. New work may touch bootstrap, photo/draft recovery,
privacy/entitlements, native files, CI and platform tests as well as gates.
Do not declare the port a one-file change or implement all Android channels
on iOS; add only the capabilities the remote-control slice needs.

### E13 — Provider usage and actual remaining subscription quota

**Implementation checkpoint:** Codex/Claude core-window collectors, the scoped
app view and provider consumption groups exist in the working tree. They are
not deployed or released. See [verification](../verification/provider-quota-2026-09-06.md)
for the passed checks and the latest assertion-only failure/correction. GLM,
MiniMax, Gemini, additional scoped windows, budgets and alerts remain open.

**User job:** know which account has capacity, what window limits it, and when
capacity resets across Codex/ChatGPT, Claude, GLM, MiniMax and Gemini. Real
remaining quota is the goal; a manual budget is not a substitute.

**Current evidence:** `UsageStatisticsGateway`, `UsageOverviewController` and
`usage_screen.dart` implement server consumption. `UsageModel` already carries
`providerID` and `modelID`; no catalog lookup is required to group totals.
The pinned contracts do not establish a provider-quota API. Community endpoint
surveys are research leads, not successful authenticated tests or guarantees
of provider support. Preserve raw credential material outside this app.

**Data distinctions (never merge silently):**
- Provider quota: account/plan-wide, often includes use from other clients;
  retain the provider's exact window, unit and reset timestamp.
- OpenCode consumption: selected server/project/date range, tokens and cost;
  it cannot reconstruct opaque subscription limits.
- Personal budget: user-entered target in a compatible measurable unit;
  label separately. No token-to-hour/message/quota-percent conversions.

**E13-Q0 · Verify one provider:** begin with Codex/ChatGPT OAuth, then Claude.
For each, record pinned source, auth identity, endpoint, permitted scope,
official/undocumented status, payload/reset semantics and a sanitized fixture.
Live credential use needs explicit approval; it is not authorized by this plan.
GLM, MiniMax and Gemini get separate provider adapters, not assumed identical
five-hour/weekly limits. Missing quota remains unavailable, not zero or unlimited.

**E13-S4 · Typed quota contract and first read-only view** — do this before
adding five collectors. Proposed `ProviderUsageGateway` returns account-scoped
snapshots with provider/account reference, source, fetched-at/expiry, windows,
used/remaining/limit or reported percent, reset-at, units, and status
(`fresh`, `stale`, `unsupported`, `authRequired`, `rateLimited`, `unavailable`).
These names are design proposals, not an existing upstream route.
- Given fresh provider windows, opening Usage → Remaining shows each window
  and its reset separately; a weekly limit does not mask an exhausted short window.
- Given failed refresh, retain and mark the old snapshot stale with Retry;
  reaching a reset timestamp alone never fabricates a replenished allowance.
- Given profile/account change during refresh, discard the late result and
  never relabel it as the newly selected account's allowance.

**E13-S5 · Authorized server-side collector** — prefer a supported upstream
read if available; otherwise evaluate an optional collector/companion beside
the user's server, whether computer-hosted or Termux-hosted. Require explicit
credential-store access, exact vendor-host allowlists, no arbitrary target URL,
no token forwarding to the client, no raw-body logging, bounded caching/backoff,
and safe token-refresh ownership. Do not copy auth stores or race CLI refreshes.
An additional loopback endpoint still needs authentication, scope checks and
revocation; loopback is not an authorization boundary. No secret goes in a
command line or persistent URI. Version/sign the collector independently only
after its update/rollback policy is reviewed; a shell script alone is not the
complete security or lifecycle implementation.

**E13-S1 · Provider consumption rollup** — keep the current Usage view and
group its `UsageModel` entries directly by provider; drill into model/variant
totals without labelling them subscription remaining. Quota support must not
depend on v2 statistics, so v1 or future backends can use a supported collector.

**E13-S2 · Optional personal budgets** — later, only for units the selected
source measures. Persist scope/window/unit in `oc.budgets.<profileId>`; prove
failed-write and deletion behavior. No pretend weekly-hour meter from tokens.

**E13-S3 · Limits and attention** — distinguish real provider-window
exhaustion from generic 429/busy/transport errors. Add alerts only after source,
dedupe, freshness, opt-in and privacy rules are proven; never auto-switch
accounts or providers to evade limits.

**UI:** Usage keeps separate Consumption and Remaining destinations. Provider
cards show a safe account label, plan if reported, every returned window,
freshness and source. Unknown has no percentage bar. No credentials, hidden
raw responses or server errors in the view/notifications. Reuse theme roles,
ARB, 48dp controls and scrollable/wrapping layouts at enlarged text.

**Focused acceptance:** sanitized fixtures for supported/absent fields,
fraction/percent units, multiple windows, no-reset fields, expired auth,
provider 429, schema drift, offline stale cache, account switching, deletion
and a cold reconnect. First-provider end-to-end evidence is required before
claiming a multi-provider monitor. Size and dates are revised after Q0.

## 4. Sequencing, dependencies, ownership

Follow the [execution plan](roadmap-2026-09-06.md), not the earlier
release-first sequence. E13 collector/contract proof can proceed alongside
read-only iOS preparation; concrete shared state/gateway/native edits serialize.
- Single-owner serialization: E3+E4 share `gateway_operations.dart` /
  `server_gateway.dart`; E2 chat fixes + E5 share chat part files. Never two
  editors on `lib/state/connection.dart`, the chat library, or a MethodChannel
  pair in one cycle.
- Every code story's definition of done: analyzer clean, focused serial tests
  green, screenshots + a11y notes for UI, privacy notes for anything touching
  credentials/URLs/notifications, migration notes for stored-format changes
  (E5, E3-S6), ARB-only strings.

## 5. Decision points (maintainer)

1. **D1** — approve publication for a verified candidate; a tag is not approval.
2. **D2** — approve any Shorebird rehearsal involving signing/publication;
   Shorebird remains the preferred eligible Android delivery path.
3. **D3** — consent and recruitment for user observations; do not block all
   engineering evidence on interviews.
4. **D4** — macOS build access for native iOS validation; Apple enrollment and
   signing only for subsequent distribution, not source preparation.
5. **D5** — authorize a particular quota collector's credential access and
   any live-provider checks after reviewing its security design.

## 6. Frontier lane — new work streams

Innovation streams live in [innovation-2026-09-06.md](innovation-2026-09-06.md):
voice mode (speak-the-run → conversation → ambient), plugins with mobile
variants, cross-server attention inbox, session handoff, completion digests,
launch surfaces, Termux, onboarding, connectivity and alternate backends.
Pull small validated slices under the execution plan; no idea is risk-free
because another application implements it. All prior research is retained
locally as advisory evidence, not copied into public positioning.
