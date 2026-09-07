# Innovation backlog — frontier lane — 2026-09-06

*Refined 2026-09-06 by read-only verification passes (protocol contract,
state/privacy, platform, UI/design-system); corrections folded in with
evidence. F2's feasibility probe is answered from the contract snapshot.*

New work streams behind the [full backlog](full-backlog-2026-09-06.md).
The [current delivery ledger](current-state-2026-09-07.md) owns current status;
the [lead execution plan](roadmap-2026-09-06.md) preserves historical ordering. Both E and F
are planning identifiers, not automatic delivery commitments. Earlier
"promote-now" labels mean candidates for refinement, not low-risk approvals.
Keep the requested innovation scope, but prove each backend, permission and
privacy boundary before enabling it.

Voice remains a flagship: spoken output first is a small end-to-end slice,
then interactive conversation, then optional ambient behavior. Output has its
own trust risks (network TTS engines, bystanders, headset changes, secrets);
it is not privacy-free simply because it does not listen.

## Register

| ID | Stream | Probe (type / cost) | Default | Promote trigger |
|---|---|---|---|---|
| F1a | Voice: speak the run | engine/privacy/audio-focus proof | Next differentiator | execution plan |
| F1b | Voice conversation | Wizard-of-Oz inside E10 + feasibility | gated | probe pass |
| F1c | Ambient eyes-free | inherits F1b | gated | F1b + E10 |
| F2 | Plugins + mobile variants | feasibility (contract) + narrative (upstream) | gated | upstream signal |
| F3 | Cross-server attention inbox | E10 observation (demand) | gated | ≥2 servers real use |
| F4 | Session handoff | CLI syntax + deep-link scope | Candidate | tested route/identity |
| F5 | Smart completion digests | narrative (concierge digest) | gated | 3/5 "keep it" |
| F6 | Launch surfaces | native routing/exposure review | Candidate | no implicit action dispatch |
| F7 | Phone-first overnight mode | task test of guidance copy | gated | Termux-user signal |
| F8 | Demo mode | isolation + first-run usability | Candidate | no production-profile effects |
| F9 | Tailnet & tunnel connectivity | authenticated HTTPS/tunnel proof | Secure-path design | no HTTP range exception |
| F10 | Codex, pi and ACP adapters | separate protocol/topology proofs | Research | one authenticated remote vertical slice |

Parked with reasons (§5): Wear OS, on-device semantic search, proactive
failure suggestions, tablet layouts.

---

## F1 — Voice mode: from dictation to spoken agent

**Hypothesis.** If the agent can *report by voice* and then *converse*, then
our developer keeps working while walking/commuting — the only moments the
product currently loses them entirely.

### F1a — Speak the run (small first voice slice)

**F1a-S1 · TTS engine feasibility spike (1–2d, delete after).** Verified:
no audio-output dependency exists today (`flutter_tts`/`audio_session` absent
even transitively from `pubspec.lock`; `record` covers capture only). Verify
against the pinned toolchain: `flutter_tts` (exact-pin, platform plugin —
pubspec governance per `desktop_drop`/`mobile_scanner` precedent) vs a thin
`oc/tts` MethodChannel over Android's system TTS, plus audio-focus
acquisition/loss handling (nothing manages output focus today). Criteria:
an available local voice speaks with network disabled; Stop is prompt;
interruption, lifecycle, unavailable language and engine failures recover
without leaking output. Select the engine on behavior/maintenance evidence,
not an arbitrary native-code line count. Verify whether pause needs chunked
playback and an utterance cursor rather than promising native pause support.
**Privacy:** PRIVACY.md's voice promises cover *input* (local transcription,
never auto-sent) — it has never promised anything about spoken *output*.
F1a ships with a new PRIVACY.md section disclosing that spoken replies are
rendered by the device's TTS engine, which the user may have chosen from a
third party.
**F1a-S2 · Read a completion aloud** — *As a* developer walking away from my
desk, *I want* the finished reply played from the chat, *so that* I keep
moving while absorbing the result.
- **Given** a completed assistant reply visible in chat **When** I tap the
  row's Read aloud action **Then** playback starts with Play/Pause/Stop in
  the now-playing chip, acquires audio focus (pauses user's music), and
  pauses safely on calls, lost audio focus, screen lock and headset removal.
  Headset unplug must not automatically resume private text on the speaker.
  Resumption is explicit until audio-route policy is tested.
**F1a-S3 · Spoken rendering rules** (the craft story): raw transcripts read
terribly. A pure `transcriptToSpeech` transform: diffs → "3 files changed,
14 added, 2 removed, largest in lib/state/connection.dart"; code blocks →
"code, 12 lines, omitted"; paths spoken segment-wise; tables → row counts.
Unit-test fixtures per rule. Redaction is defense in depth, not a guarantee
that a pattern matcher can recognize every secret. Start with user-selected
assistant prose; omit raw tool arguments, outputs and private metadata by
default. Structural diff counts must match actual diffs, not generated prose.
This transform is F1's reusable core.
**F1a-S4 · Later notification entry** opens the identified conversation after
unlock; playback requires foreground confirmation. Do not pipe transcript
text or an automatic speak action through lock-screen notification extras.
**UI.** Read-aloud is a row action (48dp, tooltip) + compact playback chip
(`surfaceContainerHigh`, pinned above composer while active, stadium pill).
State announced via live region; static level indicator when
`disableAnimations` (no waveform requirement).
**Arch.** `lib/voice/speech_output.dart` (transform, pure) + engine adapter;
audio-focus handling in the adapter; no background audio claims — foreground
app only in F1a.

### F1b — Voice conversation (gated)

**Probe (before any streaming-ASR work).** (1) *Feasibility, 1–2d:* does
sherpa-onnx **1.13.7** (pinned) expose streaming zipformer + silero VAD on
Android arm64, and what's the incremental model size? Check against
`lib/voice/model_manifest.dart` integrity machinery. (2) *Wizard-of-Oz
inside E10:* facilitator plays the agent's lines via system TTS following
the real transcript; user speaks replies; we observe whether a spoken loop
survives real phone conditions. **Pass:** ≥3/5 users complete a 5-turn
hands-free exchange and choose to continue; **fail:** users reach for the
screen to *check* state every turn → pivot to F1a-only + richer spoken
status.

**F1b-S1 · Streaming capture** — partial transcript in muted body-14,
finalized on silence (VAD) or tap; 30s cap retained per burst, bursts
concatenate. **F1b-S2 · Conversation screen** — canvas background, 96dp mic
target in thumb reach, live region narrating agent state ("Working…",
"Needs your approval"). Provide visible Listening, Review, Sending, Waiting,
Speaking, Paused and Error states; silence finalizes a draft, not a tool
approval. Explicitly review opt-in spoken-send behavior and retain text mode.
**F1b-S3 · Spoken permission interrupts** announce that review is needed;
the first version keeps all tool approvals in the existing visible, exact
request-bound confirmation flow. A recognizer or model cannot reliably label
arbitrary shell commands "non-destructive" or authenticate the speaker.
Voice-only decisions are a separate threat-model proposal, not implied scope.
**F1b-S4 · Barge-in** stops playback immediately; stopping speech is distinct
from aborting server work. UI transitions respect reduced motion, but audio
safety must not wait for an animation. Bound mic time/decoded buffers across
bursts, not just per 30-second segment.
**Arch.** Extends `lib/voice/recognizer.dart` isolate pattern (streaming
variant, generation guards so cancelled audio never becomes a draft — same
rule as today); mic while-in-use semantics documented honestly (foreground
screen-scoped first; no background mic promises — Android 14+ FGS mic
restrictions make that a separate, later fight). New model pack =
SHA-pinned download via existing manager.

### F1c — Ambient eyes-free (gated on F1b + E10)

Auto-speak completions by preference (`oc.voiceAmbient.<profileId>` — sweep-
compatible), speakerphone warning (privacy: bystanders), headset-button
toggle, do-not-disturb respect. Kill if E10 shows ambient use is rare.

## F2 — Plugins with mobile variants

**Hypothesis.** If plugins can declare *mobile variants* — a presentation
manifest with icon, description, and safe quick actions — then the phone
stops being a generic terminal and becomes a composable remote for the
user's own server extensions.

**Probes.** (1) *Feasibility — answered 2026-09-06 from the snapshot:* the
contract exposes only `GET /api/plugin` (enabled plugins + status:
`{id, source: builtin|package|local|sdk, status: active|failed, tui}`). There
is **no enable/disable wire path** (v2 has no config-write endpoint) and
**no contributed-commands/skills metadata** — `Skill.location` is a content
path (`/builtin/opencode.md`), so attribution would be path-prefix
inference: fragile, and must be labeled as such if used at all.
`plugin.added`/`plugin.updated` refetch events exist for reconciliation.
(2) *Narrative (still open):* upstream conversation — storyboard the mobile
manifest in an OpenCode issue/discussion **before** building client
rendering; a new remote manifest requires an explicit extension contract.
Upstream participation would help portability but is not required for the
app-bundled pilot below. Without discovery metadata, S1 does not invent
attribution and S2 stays inspect-only.

**F2-S1 · Plugin attribution:** show a source only when declared by verified
metadata or an explicit user-reviewed mapping. Do not infer authority or
plugin ownership from a content path.
**F2-S2 · Plugin inspection:** More → Plugins lists installed plugins with
status and source (inspect-only — the wire supports nothing else today;
truthful copy says so). **F2-S3 · Mobile variants (the innovation):**
render declared quick actions — surfaced in the plugin's card and as
long-press app-shortcut targets (shares F6-S1 machinery). Actions are
server-defined command invocations, never client-side URL/JS execution;
external links from plugin descriptions go through `openExternalLink`.
**Arch.** `PluginGateway` (domain), v2-only capability, default false →
hide. Reconcile by refetch on `plugin.added/updated` pings. No plugin code
is *executed* by the app — only server command invocation through existing
prompt/command paths.

**F2-S4 · App-bundled mobile variant pilot:** one first-party renderer for a
reviewed tool/form result can be tested before upstream standardizes discovery.
Use a versioned declarative schema, text fallback, size limits and explicit
capability actions. No downloaded Dart/JS/native code, automatic provider
calls, credential fields, arbitrary URLs, or shortcut-triggered shell runs.
An upstream discovery extension is future integration, not an existing route.

## F3 — Cross-server attention inbox

**Hypothesis.** If every server's needs-you state aggregates into one
surface, then multi-project users stop polling profiles and the app's core
promise ("see what needs attention") finally spans their whole setup.
**Probe (demand, via E10):** how many servers do observed users really run?
**Pass:** ≥2 servers actively used by 2+ participants; **kill:** single-
server reality → the current per-profile model is correct and cheaper.

**F3-S1 · Headless profile monitor:** bounded polling per saved profile
(Wi-Fi-only pref, backoff intervals honest about battery), no UI, feeds
badge + notifications. **Arch risk, stated:** `ConnectionController` is a
single-connection, single-owner unit — this wants a lightweight sibling
(`ProfileMonitor`) sharing `profiles.dart` + probe/gateway factories, *not*
a controller rewrite. **F3-S2 · Unified Attention surface:** promote
Activity into a cross-server inbox (rows: server dot + session title + age);
tap switches profile+location through existing navigation. **F3-S3 ·
Per-profile notify rules:** needs-attention-only, quiet hours; storage
`oc.notifyRules.<profileId>` (deletion sweep finds it); notification copy
keeps the existing native-owned alert-copy contract; ongoing status and
user-placed widgets have different allowed content. Do not conflate them.
**UI.** Nav badge sums cross-server pending count; inbox rows use
`surfaceContainerLow`, status via `AppTheme.statusColor` + text (never color
alone); server dot is a **new component** (name it, don't imply it exists).
Activity is already the single needs-attention destination for the active
connection — F3-S2 extends it cross-profile, reusing its resolver routing.
**Constraints (verified):** alert-open routing today stamps `profileID` only
for widget taps — the monitor must stamp it into notification open intents
(and per-profile alert keys) or cross-server taps collide on bare session
IDs; a backgrounded poller lives under the FGS dataSync 6h/24h cap and must
not silently flip the app-wide `oc.keepLiveInBackground` pref; monitor tests
must mock the secure-storage channel (standing test trap) since they load
saved profiles.

## F4 — Session handoff, phone ↔ computer (candidate)

**Hypothesis.** If a session moves between phone and terminal in one step,
then "I'll finish this at my desk" stops being a copy/paste archaeology dig.
**F4-S1 · Continue on computer:** chat menu emits the CLI resume command for
the session (mono copy block, code 12). Verified constraints: the command
must carry/derive the session's project directory (sessions are
location-scoped); exact CLI syntax is in neither contract — verify live via
`--help` before shipping; v2 has no TUI-navigation endpoint, so handoff is
command-copy only. **F4-S2 · Continue on phone:** QR of a session deep link
— requires a new `VIEW` intent filter (manifest edit, ask-first category);
route IDs only (mirror coding-alert extras), never session content.
Cross-*server* fallback the docs initially missed: the existing
`export`/`import` pair is the stronger primitive for server-to-server moves.
No demand probe — cost is S, the delight is on the spine's "Return to the
right work" step.

## F5 — Smart completion digests

**Hypothesis.** If each completed run arrives as a 3-line digest instead of
"finished," then users triage overnight work in seconds from the shade.
**Probe (narrative/concierge):** manually send 5 users 3 digests each
(crafted by hand from real completions). **Pass:** 3/5 say "keep sending";
**fail:** digests feel redundant or noisy → drop.
**F5-S1 · Digest composition decision:** use session outcome, pending
decisions and changed-file evidence for the specific run. Aggregate statistics
can supplement cost; they cannot prove what changed or whether tests passed.
Prefer deterministic summaries. Any model-generated summary is an explicit,
cost-labelled operation that must not silently add a user turn or resume work.
**F5-S2 · Digest notification** extends copy beyond the current ceiling
(counts + session title + generic tool sentence on the ongoing
notification; fixed native copy on alerts — "the native side owns all
user-visible copy" is the contract being changed, and today's one
server-controlled string reaching the shade is the interpolated tool *name*
in the generic sentence; digests must not widen that). Requires an explicit
privacy-review note, a per-profile toggle, and default off.
**F5-S3 · Morning widget digest** (widget_snapshot shape: titles/counts
only). **Kill criterion:** any privacy review that can't draw a clean line →
S2/S3 die, keep in-app digests only.

## F6 — Launch surfaces (candidate)

**F6-S1 · App shortcuts:** long-press icon → pinned sessions (dynamic
shortcuts, capped 4, mirrors widget snapshot rules: titles only), plus
static "New task" and "Connect". New `oc/shortcuts` channel pair — Dart +
native halves single-owner. **F6-S2 · Quick Settings tile** (native
TileService): needs-attention count + tap into inbox (F3-S2) or Activity
fallback. **F6-S3 · Launcher voice query:** investigate the actual assistant
role/intent restrictions before claiming support. Treat inbound text as an
untrusted draft with visible destination review, not an auto-send or tool
approval. It is new native routing, not a one-line hotword bridge.

## F7 — Phone-first overnight mode (Termux persona)

**Hypothesis.** If long overnight runs are *truthfully* supported within
Android's caps, then the secondary persona can genuinely leave work cooking.
**F7-S1 · Honest overnight guidance:** pre-run card states what may pause
when. Verified: only the *post-hoc* timeout state reaches Dart
(`backgroundServiceTimeout` push + persisted-pref flip) — no surface
computes remaining budget, and Android exposes no public query. Spike
decides: self-tracked runtime bookkeeping (across process death/reboot) vs
conservative static copy; no false promises either way (repo standing rule).
**F7-S2 · Termux health card** on the servers list for the managed server:
process/version/runner state is available today via the existing bridge;
storage is app-side proxy or script-parse (Termux-side `df` output is
currently log text only); **battery has no existing surface** — needs a
Termux:API script or a new channel method (ask-first), so the card ships
without it first. **F7-S3 · Crash auto-recovery** of the managed server
with an explicit opt-in retry policy, bounded attempts and ownership checks
(reuse the operationID pattern). Distinguish the mobile app's `dataSync`
lifetime from the separate Termux server's lifetime; neither is guaranteed
unbounded survival. **Probe:** task-focused test of the S1 copy with one Termux
user; **kill:** guidance alone reads as "don't bother."

## F8 — Demo mode: the whole product in thirty seconds

**Hypothesis.** If a visitor with no server can feel the core loop — stream,
permission, diff — in thirty seconds from the empty state, then curiosity
helps users decide whether to connect a server. **Candidate** (S/M): prove
demo isolation and observe first-run comprehension; conversion improvement
is a hypothesis, not a measured guarantee.

**F8-S1 · `DemoGateway`** — implement the existing
`ServerGateway`/`ServerOperationsGateway` contracts against an in-memory
scripted timeline (reuse the beta-18600 fixture machinery from the test
suite). No transport exists at all; capabilities are explicit and
truthful; the demo profile is ephemeral — never written to `oc.profiles`,
never touches the Keystore.
- **Given** a fresh install with no server configured **When** I tap Try the
  demo **Then** a session streams a scripted reply that hits a permission
  request and ends with a reviewable diff — entirely offline, through the
  production event-adapter path (that is the point: real UI, real code).
**F8-S2 · Empty-state entry** — primary action on the welcome screen beside
Connect to your server; a persistent-but-unobtrusive Demo badge so the
session is never mistaken for a real server; an exit CTA after the run:
"Set up your own server."
**F8-S3 · Scripted scenario** — one bug-fix narrative (reasoning → tool
call → permission → diff → completion), deterministic timings so widget
tests assert the whole journey; all copy through ARB.
**F8-S4 · Isolation rules** — no server gateway alone is not proof of no
network: images/favicons, update checks, diagnostics and route callbacks may
still make requests. Test the complete demo scope for outbound calls, disk
changes, notifications, leaked timers and switching back to the real profile.
Preserve the real draft/queue and any existing connection preference.
**Arch.** Inject a demo implementation behind the existing gateways, with
isolated ephemeral state and synthetic fixtures. Prefer a `lib/demo/` adapter
over concrete demo dependencies in the domain contracts. Do not add a new
wire-protocol flavor or persist a fake profile just to launch the demo.
Only touch shared contracts if the first fixture proves a genuine gap.

## F9 — Tailnet & tunnel connectivity

**Hypothesis.** If the app speaks the self-hoster's native connectivity —
tailnet addresses and sensible tunnels — then the largest practical barrier
between a working server and a working phone is reduced. Research leads for
verification: third-party VPN-status/intent contracts, package visibility,
private Serve versus public Funnel, and each tunnel's SSE limitations. A
manifest-exported action is not by itself a supported third-party SDK.
Guidance names only paths validated against our auth and event transports.

**F9-S1 · Secure private-network setup** — retain HTTPS outside loopback;
prefer a private HTTPS reverse proxy/Serve endpoint over the user's tailnet.
An address in `100.64.0.0/10`, an installed VPN app or a confirmation dialog
does not establish encrypted routing. The previous range exception is
withdrawn; see [connectivity decision](../connectivity-private-networks.md).
**F9-S2 · Connectivity guide** separates private reachability from public
exposure. Verify auth, SSE buffering/heartbeat/reconnect, origin paths and
URL lifetimes for each suggested service; no install/login requirement is
hidden. Funnel is public exposure, not the private default. Quick-tunnel
limitations are service-specific and must be rechecked before publishing.
**F9-S3 · Optional Tailscale assistance** may open an approved setup/help
path after verifying its API and permission contract. App presence is only
a hint. Never toggle a VPN or promise encrypted transport automatically.
**F9-S4 · Phone-hosted remote access** stays a separate, unapproved exposure
design: existing managed-server loopback binding and credentials stay intact;
evaluate a forwarding layer before widening the listen address.
**F9-S5 · Docs: switching/adding clients** —
[docs/switching-clients.md](../switching-clients.md).

**F9-S6 · LAN discovery (mDNS):** discover advertising OpenCode servers on
the local network and offer one-tap add (M).

## F10 — Alternate-agent support: separate native protocols from ACP

**Hypothesis.** Reuse the mobile session/stream/approval experience with other
agent runtimes without reducing OpenCode parity or requiring a hosted account.
Do not treat third-party adapter LOC as our effort estimate: shared machinery,
host daemons, auth and recovery often live outside the tiny adapter file.

**F10-Q1 · Codex proof:** pin the actual app-server version, transport and
JSON-RPC contract; prove initialize, create/resume thread, turn streaming,
approval, cancel and reconnect. App-server RPC is not automatically ACP
because both use JSON-RPC. Do not assert absence of network transports from
examples that happened to use stdio.

**F10-Q2 · pi proof:** identify the intended project/version, document native
RPC and any separate ACP adapter, then run the same semantic matrix. Do not
assume a community bridge implies the runtime speaks ACP natively.

**F10-Q3 · Topology:** prefer a documented authenticated network endpoint
where supported. For stdio-only runtimes, an optional companion on the
developer's computer can own the process and expose a scoped TLS/WebSocket
interface; this supports Android and iOS. Termux hosting is a separate option,
not a prerequisite or a reason to reject otherwise useful remote support.
RUN_COMMAND returns a bounded command result; it does not provide duplex
stdin/stdout, reconnect or durable stream replay for this protocol.

**Delivery after proof:** one optional gateway facet/capability set per
backend; thin end-to-end connect → send → stream → approve/cancel → resume.
Only factor out a shared ACP client after an actual ACP transcript validates
it. Keep server-side credentials server-side and bind requests to the backend,
profile, location and session. Never implement tool execution inside Flutter
or replace the deep OpenCode adapters with lowest-common-denominator scraping.

## Platform dispositions (2026-09-06)

- **iOS:** E12 is remote-control scope, not a shipped target. Implement only
  required iOS capabilities; Termux and Android FGS channels remain unavailable.
  Paid signing enrollment does not gate source/scaffold/widget work. Native
  simulator/device compilation still needs macOS/Xcode. Background push is a
  separate technical/privacy design, not synonymous with analytics.
- **Baking in a server:** not selected for the current roadmap. It requires
  a native runtime/toolchain, explicit process isolation, filesystem policy,
  lifecycle and distribution work. It is technically different from embedding
  arbitrary code in the Flutter process; do not claim they are necessarily the
  same architecture. The Android `dataSync` limit constrains this app's current
  service design, not every possible server-hosting implementation.
- **Termux-native spike: INCONCLUSIVE.** The earlier version/HTTP checks ran
  inside proot, including those launched by bionic bash. Those subprocesses
  remained subject to proot path/syscall translation. A 401 proves an HTTP
  auth rejection, not usable authenticated API, streaming, tools or restart.
  See the [corrected evidence](../verification/termux-native-server-spike-2026-09-06.md).
  The measured package sizes are not an installed-footprint comparison, and
  one-minute setup/swap-one-file updates were not measured. A new test must
  start from a genuine Termux shell without the guest layer, retain the
  current working installation, and prove auth, shell/Git, TLS/DNS, SSE,
  rollback and data preservation before any migration is proposed.

## Parked, with reasons

- **Wear OS tile** — no device evidence, zero user reports; revisit after F3.
- **On-device semantic transcript search** — literal find (cycle 18) covers
  the observed job; embeddings add model weight without a demonstrated gap.
- **Proactive failure suggestions** ("this run failed twice — suggest
  revert?") — needs server-side model calls per failure; privacy + cost
  review first; revisit after F5's digest decision.
- **Tablet/multi-window layouts** — desktop lane (E8) covers large-canvas
  verification; no phone-persona demand yet.
- **Residual contract surface** (recorded, no persona-job pull yet):
`GET/DELETE /api/debug/location` eviction, experimental wellknown
integration registration, `GET /api/experimental/migration/v1` progress —
each needs a user workflow before any row exists.

## Sequencing

The [execution plan](roadmap-2026-09-06.md) replaces the earlier
"promote-now" batch. Quota visibility and iOS preparation reflect explicit
requests; voice remains the next differentiating experience. Evidence gates
are scoped per slice, not an indefinite freeze on product work. Pull demo,
secure connectivity and declarative plugin pilots in bounded batches after
shared-contract ownership is assigned.

All implemented slices require focused checks, analyzer and the serial full
suite; visible/native/data changes add their respective QA, privacy and
migration evidence. Device operations, signing and public releases remain
explicitly approved actions.

## UX absorption batch (landscape skim, 2026-09-06)

Candidate patterns from source skims, not proof that our implementation is
missing them or that a copied implementation is correct. Guiding rule: **absorb state machines,
copy, and physics — not rendering layers or gesture-only affordances**
(landscape keyboard/list code is where their live bugs concentrate, and
hidden-gesture navigation violates our standing rule).

| Pattern | Lane | Size |
|---|---|---|
| Anchor-maintaining scroll + IME-resize reading physics for the transcript | chat polish (with E2) | M |
| Settled/pending markdown split (memoize completed blocks, re-parse tail) | transcript perf | M |
| Verdict-classified connection status with remedy copy; dot+text from one object | E2 / reconnect banner | M |
| Exact-request pending state + stale-result rejection; refetch reconciliation, never rollback a resolved approval | E2 (#10) | S/M |
| Graded approval bar (once/always/reject, destructive styling, mono target) | permission card | M |
| 3-state session vocabulary + elapsed-time disclosure, live region | status chips | S |
| `+12 −3` stats badges on collapsed tool groups | tool cards | S |
| Voice phase FSM + generation counter (feeds F1b); optional thinking tone | F1b | S/M |
| Durable notification intents, declarative per-profile routing | F3 constraints | M |
| Visible editable queued-message card (v2 steer/queue already backs it) | chat | S |
| Assertive send-failure banner; reconnect banner pair; cold/warm load split | E2 / chat hydration | S each |
| Live token/cost chip in composer | E13 synergy | S |

Rejected on evidence: edge-swipe-only navigation, duplex-voice transport,
share-link relay machinery, in-app code editing surfaces, sub-48dp targets,
color-only status. This inventory records generalized interaction patterns.

This rejects blind transplantation, not whole future workstreams. Before
absorbing a pattern: check current source/tests (queue/steer, tool grouping,
QR pairing, file/worktree/terminal views already exist), reproduce the gap,
record a benchmark or interaction failure, and review licensing. Reuse our
theme roles instead of importing another product's palette or source code.
