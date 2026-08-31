# Aptabase telemetry integration plan

Goal: optional, anonymous, opt-in usage statistics for oc_app, built on
Aptabase, that does not break any promise PRIVACY.md currently makes. The
feature is off by default, sends no identifiers, and is compiled out of the
UI entirely when no backend is configured — so F-Droid-style builds keep the
current "no analytics" property byte for byte.

Everything here was written against the codebase on 2026-08-31. Ground truth
first, then decisions, then the phased work breakdown.

## Principles (non-negotiable)

1. **Opt-in, default off.** No packet related to telemetry may leave the
   device before the user flips a Settings switch. This matches the
   App-diagnostics philosophy (`lib/diagnostics/app_diagnostics.dart`:
   process-local, never uploaded automatically) and F-Droid's tracking
   policy.
2. **No identifiers, ever.** No device ID, no user ID, no advertising ID,
   no fingerprinting, no persistent cookies. Session IDs are ephemeral and
   rotate after one hour of inactivity (SDK behaviour, see ground truth).
3. **No content.** Prompts, chat messages, file names, server URLs, hostnames
   and model names never appear in telemetry. Props are coarse enums only,
   enforced by an allowlist in code, not by convention.
4. **Honest "anonymous".** The server necessarily sees an IP at transport
   time. Aptabase derives a coarse country from it for the dashboard and does
   not retain the address. PRIVACY.md will say exactly this, in plain words.
5. **Inspectable.** The event schema is published in this repo (table below).
   A release-gate step captures the actual wire payload and records it under
   `docs/audits/` so the claim "this is all we send" is verifiable, not
   asserted.

## Ground truth captured

- SDK: `aptabase_flutter` 0.5.0 (published 2026-07-23, publisher
  aptabase.com, **MIT** license). Supports Android, iOS, macOS, Linux,
  Windows, Web — covers this repo's Android + desktop targets.
- API: `await Aptabase.init(appKey, InitOptions(host:, tickDuration:,
  batchLength:, printDebugMessages:))`, then
  `Aptabase.instance.trackEvent(name, {props})`. Props must be strings or
  numbers (no bool/list/map). `trackEvent` is non-blocking; events queue
  locally and flush every 30 s in batches of up to 25.
- Automatic collection by the SDK: OS name, OS version, locale, app version,
  build number. Nothing else, and no automatic events — every event is a
  manual `trackEvent` call. `kDebugMode` builds are auto-detected.
- App keys encode the region: `A-EU-*`, `A-US-*`, `A-SH-*` (self-hosted;
  self-hosted keys additionally require `InitOptions(host:)`).
- Server: AGPLv3, `github.com/aptabase/self-hosting` (Docker Compose:
  app + ClickHouse + Postgres). Running an unmodified self-hosted instance
  imposes no obligation on this repo; only modifying the server would.
- Android `INTERNET` permission: already declared — no manifest change.
- Transitive deps of the SDK: `device_info_plus`, `package_info_plus`,
  `shared_preferences`, `universal_io`. `device_info_plus` is used for the
  OS version only; the Phase 5 wire-capture audit must confirm no hardware
  identifier reaches the payload.
- Repo facts the design leans on: settings sub-screens are `part of
  '../settings_screen.dart'` files in `lib/ui/screens/settings/` and use the
  Listenable/ValueNotifier pattern on controllers created in `main()`
  (`ConnectionController.appearance`, `themePack`);
  `AppDiagnosticsController` is the precedent for a small controller created
  in `main()` and threaded through `AppBootstrapGate`; preferences are
  `SharedPreferences`, services take it via constructor; tests are flat in
  `test/*_test.dart`; settings screens currently use raw English strings
  (l10n via `app_en.arb` exists but is not used on these screens — follow
  the local convention, l10n pass can absorb it later).

## Decisions

- **D1 — opt-in, default off: decided.** See principles. Consequence:
  PRIVACY.md intro must be amended (draft in Phase 4) since it currently
  states the app provides no analytics service.
- **D2 — backend: OPEN, recommendation self-hosted.** `A-SH-*` key against
  an instance the project controls keeps the strongest story ("data goes to
  infrastructure the publisher controls, source you can read, AGPLv3").
  Managed cloud (`A-EU-*`, EU region) is the low-maintenance fallback and is
  an acceptable v1 if no server exists yet. The app code is identical for
  both; only the compiled key + host differ. Decide before Phase 5.
- **D3 — gate architecture: decided.** A thin `TelemetryController` owns the
  enabled flag and a `TelemetryBackend` seam wraps the static `Aptabase`
  calls. `Aptabase.init` is called **lazily, only after enablement** — never
  at startup when the switch is off — so a disabled build has zero network
  surface and zero SDK timers. The seam also makes the whole thing testable
  without touching the real SDK.
- **D4 — key shipping: decided.** `--dart-define=APTABASE_APP_KEY=...`
  (and `APTABASE_HOST=...` for `A-SH-*`). Empty/unset ⇒ feature compiled
  inert and the Settings entry is hidden. No keys in the repository.
- **D5 — event schema: proposed (v1 below).** Four event names maximum.
  Adding a name or prop requires a PR that edits the allowlist and this
  table together.

## Event schema v1

| Event | Props | Trigger | Why |
|---|---|---|---|
| `app_open` | — | first resume per process, after telemetry enabled | basic DAU-sized adoption signal |
| `server_connected` | `stack`: `v1` \| `v2` | successful first connection in a session | the v1→v2 migration decision needs real usage data |
| `feature_used` | `feature`: `voice_input` \| `termux_setup` \| `background_mode` \| `attachment` \| `diagnostics_send` \| `desktop` | first use of that feature per session | tells us what to maintain/port first |
| `telemetry_disabled` | — | user turns the switch off | lets the dashboard read honest churn instead of a silent flat line |

Explicitly **not** tracked: anything with prompt/chat/file content, server
URLs or hostnames, model names, session durations, screen flows, crash
reports (the App-diagnostics flow already covers errors, user-initiated and
redacted — it stays the only error channel).

## Architecture

New module `lib/telemetry/`:

- `telemetry_controller.dart` — `TelemetryController extends ChangeNotifier`,
  constructed in `main()` next to `AppDiagnosticsController` and passed
  through `AppBootstrapGate` like it. Fields:
  - `ValueListenable<bool> enabled`
  - `restoreFromPrefs(SharedPreferences)` — reads the single pref key
    `telemetry.enabled_v1` (default `false`); called during bootstrap load,
    never blocks first frame.
  - `Future<void> setEnabled(bool)` — persists the key; on `true` performs
    lazy `Aptabase.init` (unawaited — `init` does no network, but must
    complete before the first `trackEvent`, so the controller awaits it
    internally before emitting events) and tracks `app_open`; on `false`
    flips the gate and tracks `telemetry_disabled` **before** closing the
    gate, so the off-event itself gets out. Already-queued events from the
    enabled period may flush afterwards (SDK has no shutdown); disclosed in
    PRIVACY.md rather than hidden.
  - `void track(String name, [Map<String, Object> props])` — no-op unless
    enabled **and** initialized; validates `name` against the const
    allowlist and each prop against the per-event prop table (unknown names
    dropped, values coerced to short strings/numbers, hard cap 3 props,
    64 chars). This is the anti-PII wall: future call sites can only send
    what the schema table admits.
- `telemetry_backend.dart` — `abstract class TelemetryBackend { Future<void>
  init(String appKey, {String? host}); void trackEvent(String name, [Map<String,
  Object>? props]); }` plus `AptabaseBackend` (wraps `Aptabase.init` /
  `Aptabase.instance.trackEvent`) and a recording `FakeTelemetryBackend` for
  tests. Reads `APTABASE_APP_KEY` / `APTABASE_HOST` via
  `const String.fromEnvironment`.

Settings UI:

- New part file `lib/ui/screens/settings/privacy_settings_screen.dart`
  (registered as a category row in `settings_screen.dart`, same pattern as
  the other sub-screens). One `SwitchListTile` bound to
  `TelemetryController.enabled` with a two-line subtitle: what is sent,
  where it goes, off-by-default. Hidden entirely when the app key dart-define
  is absent (check via `const bool.hasEnv`-style `fromEnvironment` sentinel),
  which keeps unconfigured builds identical to today's.

## Phased work breakdown

**Phase 1 — gate + backend seam (no UI, no dependency yet wired to network)**
Half a day. Add `lib/telemetry/` per the architecture section with the
dependency added but init gated. Files: two new module files, `main.dart`
controller construction, bootstrap restore hook.
Accept: `test/telemetry_gate_test.dart` proves (a) default off ⇒ no
`init`/`trackEvent` ever called, (b) enable ⇒ init then `app_open`,
(c) unknown event names and over-cap/oversized props are dropped,
(d) disable emits `telemetry_disabled` then closes the gate.

**Phase 2 — Settings toggle.** Half a day. Privacy category row, switch,
hidden-when-unconfigured logic, controller threading to the settings
sub-screen.
Accept: `test/telemetry_settings_test.dart` — toggle reflects pref state,
persists across a fresh controller, entry absent when no key compiled.

**Phase 3 — instrumentation.** Half a day. `track` calls at ~6 sites: first
resume, first successful connect (v1 vs v2 known at the domain-interface
seam from the dual-stack port), first voice-input session, termux setup
completion, background-mode enable, diagnostics Send tap, plus desktop
first-window on the desktop entry path. Each call site passes enum-string
props only.
Accept: grep audit — no `track(` call outside the schema; all props appear
in the D5 table.

**Phase 4 — docs + compliance.** Half a day, mostly writing.
- PRIVACY.md: intro clause becomes "It does not provide a developer-operated
  account, advertising, or crash-reporting service." plus a new **Usage
  statistics (optional)** section: off by default; what is sent (event name,
  app version, OS, language, coarse country derived from the connection IP,
  which is then discarded); what is never sent (any message, file, or server
  content; no identifiers); that queued events may flush briefly after
  switch-off; how to disable permanently (toggle persists; uninstall erases
  the pref).
- `THIRD_PARTY_NOTICES.md`: MIT notice for `aptabase_flutter`.
- `SECURITY.md`/README: one line each pointing at the schema table.
- Store listing notes: Play data-safety — "App interactions: collected,
  not linked to the user, encrypted in transit, deletable by disabling +
  aggregate-only server-side"; F-Droid — tracking is opt-in and disabled by
  default, no anti-feature label expected.
Accept: PRIVACY.md diff reviewed by someone who did not write it; effective
date bumped.

**Phase 5 — backend stand-up + release gate.** Half a day plus ops.
- Execute D2: self-host via `aptabase/self-hosting` compose behind TLS, or
  create the EU cloud app; mint the key; wire it into release build args.
- **Wire-capture audit:** run a debug build through a proxy, capture the
  exact event payload, confirm fields ⊆ {event name, props, session id,
  OS, OS version, locale, app version, build number, SDK version} and that
  no device identifier appears; record screenshots/payload in
  `docs/audits/aptabase-payload-audit.md`. This audit is the release gate —
  no store submission before it exists.
Accept: dashboard shows events only from opted-in builds; audit doc
committed; toggle-off leaves zero further traffic in a 10-minute capture.

## Risks and mitigations

- **SDK transitive scope creep** (a future `aptabase_flutter` major could
  widen collection): the Phase 5 capture audit is repeated on every SDK
  major bump; the version is pinned with a rationale comment in
  `pubspec.yaml`, matching the `desktop_drop` precedent.
- **Accidental PII through props**: impossible-by-construction via the
  controller allowlist; tests enforce it.
- **Perceived contradiction with PRIVACY.md** if marketing text lags the
  release: Phase 4 lands in the same release as Phase 1–3; the toggle stays
  hidden in any build without a key, so store/F-Droid builds ship only after
  the policy text is merged.
- **Trust of the existing user base**: the honest framing is that this adds
  a *consent-based* channel that previously did not exist; announce it in
  release notes with a link to the schema table and the audit doc, not as a
  footnote.

## Rollback

Remove the dart-define (feature goes inert + invisible), or revert the
module. The pref key and server data need no migration in either direction.
