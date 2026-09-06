# Innovation backlog — frontier lane — 2026-09-06

*Refined 2026-09-06 by read-only verification passes (protocol contract,
state/privacy, platform, UI/design-system); corrections folded in with
evidence. F2's feasibility probe is answered from the contract snapshot.*

New work streams behind the [full backlog](full-backlog-2026-09-06.md).
**Lane semantics:** E-epics are committed delivery; **F-epics are bets**. Each
carries a hypothesis, a proof-of-life probe sized by
pol-probe-advisor ("cheapest prototype, harshest truth"), and kill criteria.
A stream enters delivery only when its probe passes (or it's marked
**promote-now** because risk is platform-shaped, not demand-shaped).

The AI-shaped lens (ai-shaped-readiness-advisor) sets the flagship's
direction: today's voice is **AI-first** — dictation automates typing. Full
voice mode is **AI-shaped** — it redesigns the interaction into an eyes-free
control loop. The advisor's dependency rule (foundational competencies
first) maps directly: build the loop's **report** side (spoken output) before
the **listen** side, because output requires no new trust model while input
requires microphone trust, interruption semantics, and platform foreground
guarantees. Hence F1's phases run *output → conversation → ambient*.

## Register

| ID | Stream | Probe (type / cost) | Default | Promote trigger |
|---|---|---|---|---|
| F1a | Voice: speak the run | feasibility spikes only | **promote-now** | after E2 |
| F1b | Voice conversation | Wizard-of-Oz inside E10 + feasibility | gated | probe pass |
| F1c | Ambient eyes-free | inherits F1b | gated | F1b + E10 |
| F2 | Plugins + mobile variants | feasibility (contract) + narrative (upstream) | gated | upstream signal |
| F3 | Cross-server attention inbox | E10 observation (demand) | gated | ≥2 servers real use |
| F4 | Session handoff | none (contract check) | **promote-now** | anytime |
| F5 | Smart completion digests | narrative (concierge digest) | gated | 3/5 "keep it" |
| F6 | Launch surfaces | none (platform capability) | **promote-now** (S1) | anytime |
| F7 | Phone-first overnight mode | task test of guidance copy | gated | Termux-user signal |

Parked with reasons (§5): Wear OS, on-device semantic search, proactive
failure suggestions, tablet layouts.

---

## F1 — Voice mode: from dictation to spoken agent

**Hypothesis.** If the agent can *report by voice* and then *converse*, then
our developer keeps working while walking/commuting — the only moments the
product currently loses them entirely.

### F1a — Speak the run (promote-now after E2)

**F1a-S1 · TTS engine feasibility spike (1–2d, delete after).** Verified:
no audio-output dependency exists today (`flutter_tts`/`audio_session` absent
even transitively from `pubspec.lock`; `record` covers capture only). Verify
against the pinned toolchain: `flutter_tts` (exact-pin, platform plugin —
pubspec governance per `desktop_drop`/`mobile_scanner` precedent) vs a thin
`oc/tts` MethodChannel over Android's system TTS, plus audio-focus
acquisition/loss handling (nothing manages focus today). Criteria: pass =
offline system engine speaks, pause/cancel works, no new plugin if the
channel is <150 lines; fail = neither clean → reconsider engine.
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
  resumes cleanly after an interruption (call, headset unplug).
**F1a-S3 · Spoken rendering rules** (the craft story): raw transcripts read
terribly. A pure `transcriptToSpeech` transform: diffs → "3 files changed,
14 added, 2 removed, largest in lib/state/connection.dart"; code blocks →
"code, 12 lines, omitted"; paths spoken segment-wise; tables → row counts.
Unit-tested fixtures per rule; never speaks credential-shaped tokens
(`sanitize`-adjacent guard). This transform is F1's reusable core.
**F1a-S4 · "Read it to me" notification action** on completion
notifications; obey notification privacy rules (summary speaks what the
body already shows).
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
"Needs your approval"). **F1b-S3 · Spoken permission interrupts** — request
arrives mid-conversation → TTS reads it via the S3 transform, non-destructive
answers by voice ("approve", "reject") accepted **only while the app is
foregrounded and unlocked** (SECURITY.md's out-of-band approval threat
model), destructive actions **always** require unlock+tap (standing safety
rule; mirrors notification authentication precedent). **F1b-S4 · Barge-in** — speaking pauses playback
with `Curves.easeOutCubic` state transition.
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
rendering; a mobile manifest without upstream buy-in is a fork. **Pass:**
upstream engages on the manifest; **fail/kill:** attribution stays
upstream-blocked (S1 becomes optional polish behind the heuristic, or drops)
and S2 stays inspect-only.

**F2-S1 · Plugin attribution (conditional, S):** Commands/Skills rows show
their source plugin (`labelSmall` muted caption) — only via location-path
heuristic, labeled inferred, or not at all if the heuristic proves noisy.
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
keeps counts+titles privacy ceiling.
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

## F4 — Session handoff, phone ↔ computer (promote-now)

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
**F5-S1 · Digest composition decision:** prefer assembling from existing
structured surfaces — `GET /api/session/stats` is the ready-made aggregate
(no extra model call); only if insufficient, a bounded server-side summarize
prompt via existing machinery (cost + privacy notes required either way).
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

## F6 — Launch surfaces (promote-now: S1)

**F6-S1 · App shortcuts:** long-press icon → pinned sessions (dynamic
shortcuts, capped 4, mirrors widget snapshot rules: titles only), plus
static "New task" and "Connect". New `oc/shortcuts` channel pair — Dart +
native halves single-owner. **F6-S2 · Quick Settings tile** (native
TileService): needs-attention count + tap into inbox (F3-S2) or Activity
fallback. **F6-S3 · Launcher voice query:** `ASSIST`/voice intent opens the
composer prefilled with the query text — a 1-line bridge to F1's world
without any hotword claims.

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
within the same caps (operationID pattern from the Termux restart work —
verified real). **Probe:** task-focused test of the S1 copy with one Termux
user; **kill:** guidance alone reads as "don't bother."

## Platform dispositions (2026-09-06)

- **iOS:** controller port is feasible (Flutter + capability gates; needs a
  new `ios/` target, iOS halves for all five channels, APNs-shaped
  notifications) and matches the primary persona. An on-device-server
  equivalent is **not possible**: iOS forbids `fork`/`exec` in the sandbox —
  OpenCode's subprocess-based tools cannot run — and background compute is
  minutes-budgeted. iOS, if ever, is remote-server-only.
- **Baking the server into the app:** rejected for now — it couples agent
  lifetime to our FGS-capped process, collapses the two-UID security
  boundary SECURITY.md relies on, ties weekly server betas to app releases,
  and would force shipping a userland (re-inventing Termux worse). The
  recorded middle path: **spike a single compiled server binary running
  Termux-native** (drop the proot-Ubuntu rootfs, 1.5 GB, and npm install
  time) — F7-adjacent, keeps separation and update independence.
  *The spike in plain English:* today, putting OpenCode on a phone means
  installing a whole mini Linux computer inside Termux (Ubuntu, ~1.5 GB,
  10–15 minutes) and then installing OpenCode inside *that*, because the
  server normally expects a full Linux box. OpenCode is built in a language
  whose build tool can pack the entire program — runtime included — into
  one self-contained file. The spike is a time-boxed experiment asking three
  questions: does that one file run directly inside Termux without the
  Ubuntu middle layer? Can it still find the tools it uses (bash, git)?
  Is updating it just replacing one file? If all three are yes, first-run
  setup drops from ~15 minutes to about one and the download from ~1.5 GB
  to roughly a tenth — while Termux stays a separate app, so the agent
  keeps surviving our app being killed, and the shell still lives in a
  different lockbox than the one holding your passwords. If any answer is
  no, we keep today's setup and lose nothing. We're shipping just the
  chef instead of the whole kitchen — without moving the chef into our
  own   house to do it.
  **Spike result — executed 2026-09-06, on the phone itself** (the dev
  container runs inside the managed Termux server this spike tests):
  the pinned `opencode-linux-arm64-musl` single-file binary from npm
  **runs and serves Termux-native** — no proot, no Ubuntu, no npm.
  Recipe: static musl loader (771 KB) + Alpine's musl-built
  `libstdc++`/`libgcc_s` (~900 KB), `PT_INTERP` patched to the loader,
  `TMPDIR`/`PATH`/`LD_LIBRARY_PATH` exported; invoked from bionic bash it
  printed its version and served `127.0.0.1:4300` with the 401 auth gate
  live. Footprint: **185 MB binary + ~2 MB support, versus the 1.5 GB
  rootfs + Node + npm install**; `opencode upgrade` is a built-in one-file
  update path. Remaining before this becomes an F7 story: (1) real tool
  execution through Termux's shell — the server spawning `/bin/sh`, which
  Termux lacks; a wrapper or `$SHELL` env decides, (2) one true on-device
  run outside proot for final confirmation, (3) the manager-script shape:
  version-pinned download + SHA-256 (reuse the voice-model integrity
  pattern) + a five-line wrapper replacing the proot bootstrap.

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

```
E1/E2 (delivery) ──▶ F1a + F4 + F6-S1   ← promote-now batch (post-E2 cycle)
E10 sessions ──▶ F1b, F3, F5 probes ──▶ pass? enter Next : kill/record
F2 contract spike answered 2026-09-06 (inspect-only; no manifest wire path) — upstream narrative is the remaining probe
F2-S3 + F6-S1 share shortcut machinery → same owner
F7 rides Termux-user signal
```

Frontier stories inherit the full backlog's definition of done (analyzer,
focused serial tests, screenshots + a11y notes, privacy notes — mandatory
for F1/F3/F5 — migration notes for any stored format). Nothing in this file
modifies product code or the committed E-plan; a stream graduates by moving
its rows into the full backlog's register with probe evidence attached.
