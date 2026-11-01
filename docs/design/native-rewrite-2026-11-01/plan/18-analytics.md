# 18. The analytics core

Written 2026-11-01, after the owner's decision of the same day. This section owns the analytics event schema, the two
tiers, the identifiers, the local queue and upload policy, and the sink interface (03 §0.3). Section 16 keeps what may
never be collected, PRIVACY.md, the "What we collect" disclosure and the F-Droid label (16 §4, §5). Where 16 §5.1–§5.7
and this file differ, this file wins; 16 §5 points here.

Tags: **[V]** checked today in code or a primary source; **[R]** read in the repo, a report or memory; **[U]** not
verified, with the step that settles it.

---

## For the owner (plain words)

1. The new app counts a few anonymous things from day one, so we can see whether the switch works on real phones:
   app and Android version, the kind of phone (phone, tablet, desktop), crashes and freezes (only their kind), how fast
   the app opens, how often it is used each day, how many servers and agents of each kind, and the kind of errors,
   never their text. This is "Basic", always on, and every item is listed on a "What we collect" page in the app.
2. "Detailed" is off until you switch it on: which screens and features you use, how far you get in a flow, and how
   long things take.
3. Never collected: what you type or read, file names or paths, server addresses, passwords, keys or tokens.
4. Events wait on the phone and go out in small batches. In the background they go only over Wi-Fi or another
   unmetered network, never on mobile data. Nothing is sent at all until you pick where they go (Aptabase, PostHog,
   Firebase, Plausible or our own server); the design can send to several of them later.
5. F-Droid labels always-on counting as "Tracking". We decide what to do about that when we submit to F-Droid, after
   the switch.

---

## 0. Status and sources

| State | What |
|---|---|
| Decided | Owner, 2026-11-01 (memory `freeze-owner-decisions-2026-11-01` item 6, and the 2026-11-01 brief): an analytics core from day one that can later send to other analytics tools; BASIC always on, DETAILED opt-in; the lists in §2. |
| Implemented | `:engine:analytics`, branch `native/analytics-2026-11-01` (cloud session, unit S1-ANALYTICS): commonMain only, 49 desktop tests green, compiles for Android [R `docs/native/units/S1-ANALYTICS.md` on that branch]. |
| Not done | Platform wiring (§8), any remote sink, the "What we collect" screen, PRIVACY.md, catalog rows. Nothing is enabled and nothing is sent. |
| Not merged | The branch is not on `native/m0-2026-11-01`; `MODULE_OWNERS` and `ModuleRules.engineOrder` need the coordinator's rows first (03 S27, §0.3). |

Inputs: 02 (G7, G8, row 17, NG15, S-22, S-23, S-29, S02-5); 03 S27; 09 §6.8 (chat events); 13 §12.2 (radio and
battery); 16 §4–§5 (privacy, K-SEC-11, K-SEC-12, B-SEC-191, wire formats, D16-1, D16-2); 12 W-20, X-01, X-04.

---

## 1. Decisions

| # | Decision | Replaces |
|---|---|---|
| A18-1 | Own module `:engine:analytics`, package `io.github.eslamasabry.oc.engine.analytics`, engine order #2 (after `settings`, before `diagnostics`). Depends only on `:core:common`. | 16 N16-D9 (a package in `:engine:diagnostics`) |
| A18-2 | BASIC is the owner's list (§2.1): it now includes crashes and freezes (category only), start-up time, daily use counts, server and agent kinds as counts, error categories and the device class. | 16 §5.3 (crash, cold start and failures were DETAILED) |
| A18-3 | A **random install id that rotates every 30 days** is in every envelope. It is 128 random bits from a secure source, unrelated to any device, account or advertising id, replaced at once by "Delete everything". | 16 §5.4 ("install id: never sent") |
| A18-4 | Uploads in the background go only over an **unmetered** validated network; on mobile data only while the app is in the foreground. Never a scheduled wake-up. | 16 §5.6 rule 4 (Data Saver only) |
| A18-5 | One `AnalyticsSink` interface. A build binds one sink, or a fan-out sink over several, so "talk to other tools later" adds a sink and changes no emitter. | 16 §5.2 "exactly one `AnalyticsSender`" |
| A18-6 | A Firebase destination, if chosen, goes through plain HTTPS from our own code (the GA4 Measurement Protocol) **with no Firebase or Google SDK** in any build (G8, 12 X-01). [U exact endpoint and fields; checked when chosen] | 16 §5.5 "Firebase SDK: not offered" (still true: no SDK) |
| A18-7 | The F-Droid question (keep BASIC on and accept the "Tracking" label, or a build whose BASIC sink is off) is decided at the F-Droid submission, after the switch. | 16 §5.9 "answer recorded" |

---

## 2. What is collected

### 2.1 BASIC (always on, anonymous, minimal)

As built [R S1-ANALYTICS]; every property is an enum, a boolean, a bucket or the version code.

| Event | When | Properties |
|---|---|---|
| `app_start` | each process start that shows UI, after the migration gate opens | kind (cold / warm), start-up time (`DurationBucket`) |
| `app_updated` | first start after the versionCode changed | `from_code` (`VersionCode`) |
| `migration_done` | once, when `native.migration.v1` completes or the runner gives up (05 M5) | outcome, failed step, `reentry_count` (bucket), duration (bucket) |
| `backend_connected` | first successful connect per backend kind per UTC day | kind (OpenCode 2 / Paseo / Codex), where (`AddressClass`: this phone, tailnet, local network, internet), never an address |
| `update_result` | an in-app update attempt that ends without the app being replaced | result (cancelled, signer mismatch, checksum mismatch, no space, failed) |
| `crash_seen` | the first start after a crash | category (`CrashCategory`), fatal (bool). Never a stack, class or message |
| `freeze_seen` | a main-thread stall or ANR reported by `ApplicationExitInfo` or the stall watchdog | kind (stall / ANR), duration bucket, foreground (bool) |
| `error_seen` | a `Failure` shown to the person | category (`ErrorCategory.of(Failure)`, an exhaustive `when`) |
| `daily_use` | once per UTC day | opens, chats started, messages sent (each a `CountBucket`) |
| `server_kinds` | once per UTC day | count per backend kind (buckets) |
| `agent_kinds` | once per UTC day | count per agent kind (buckets) |

Envelope on every event: app version and build, channel (stable / preview / desktop), OS family and major version,
**device class** (phone / tablet / desktop / other, from the window class, never the model), app language (one of the
app's languages), debug flag, the hour (UTC epoch truncated to the hour), the rotating install id, the session id.

**Open point (owner, D18-1):** 16 kept crash reporting behind local crash capture (B-SEC-042, off by default). The
owner's BASIC list includes crashes. Default: `crash_seen` is BASIC as the owner listed it, and carries only the
category; B-SEC-042 keeps governing the **local** crash details, which never leave the phone.

### 2.2 DETAILED (opt-in, off by default)

| Event | Properties |
|---|---|
| `screen_open` | `ScreenId` (one per route, never its arguments) |
| `feature_used` | `UsedFeature` |
| `flow_step` | flow id, step state (started / completed / abandoned / failed) |
| `action_timed` | timed action, duration bucket |
| `setting_changed` | which setting (`SettingId`), never its value |
| chat events of 09 §6.8 | as listed there, through `:engine:delivery` and `:engine:requests` |

Switching DETAILED off drops queued DETAILED events at once and sends no more.

### 2.3 Never collected (types make it impossible, K-SEC-11)

Prompt or reply text, file names or paths, server addresses or names, profile ids, conversation or session ids of
chats, model or provider names, error or exception text, credentials, keys, tokens, device model or manufacturer,
screen size, time zone, Android id, advertising id, contact or account data. The app adds no country; a destination
may derive one from the IP, which PRIVACY.md says.

---

## 3. Identifiers

| Id | Made by | Lifetime | Stored |
|---|---|---|---|
| install id | `SecureIdSource` (`Uuid.random()`, 128 random bits; never `kotlin.random`, K-SEC-12) | 30 days, then replaced; also replaced when the clock moves backwards past the rotation point, and by Delete everything | the analytics store (app-private file), never prefs, logs, diagnostics or the problem report |
| session id | the same source | one process, rotated after 60 idle minutes | memory only |

The install id lets a destination count active installs over a month without following a person across months.
`IdentityTest` covers rotation, clock rollback and Delete everything [R S1-ANALYTICS B-ANLY-009].

---

## 4. Module, API and pipeline

### 4.1 Public API (shape as built; frozen in `contracts/slice-1.md`)

```kotlin
package io.github.eslamasabry.oc.engine.analytics

public enum class AnalyticsTier { Basic, Detailed }
/** Every event is a class in AnalyticsSchema.kt; constructor properties are enums, Boolean, buckets or VersionCode. */
public sealed interface AnalyticsEvent { public val spec: EventSpec }
public sealed interface PropValue  // Choice, Flag, Count, Duration, Version only

public interface Analytics {
    /** Never throws, blocks or suspends; a full inbox (1,024) drops the event. */
    public fun track(event: AnalyticsEvent)
    /** Daily counters rolled into one BASIC summary per UTC day. */
    public fun count(counter: UsageCounter)
    public val consent: StateFlow<ConsentState>                 // DETAILED: NotAsked, Granted, Declined
    public suspend fun setDetailed(granted: Boolean): Outcome<Unit>
    public val sent: StateFlow<ImmutableList<Delivery>>          // last 50 bodies exactly as sent; memory only
    public suspend fun deleteEverything(): Outcome<Unit>         // queue, sent log, ledger, install id, consent
}

public interface AnalyticsSink {
    public val id: SinkId
    public val kind: SinkKind                                    // None, LocalDebug, Remote
    public suspend fun send(batch: WireBatch): Outcome<Delivery> // Delivery.body = the exact bytes sent
}
/** HTTP for remote sinks; bound in the app graph over :core:net, so this module never depends on :core:net. */
public interface AnalyticsTransport {
    public suspend fun postJson(url: String, headers: List<Pair<String, String>>, body: ByteArray): Outcome<Int>
}
```

Built names that differ from 16 §5.2, kept as built: `AnalyticsSink` (16: `AnalyticsSender`), `app_start` (16:
`app_open`), `reentry_count` and `FirstReply` (the leak detector flags the words "secret" and "token"),
`UpdateOutcome`, and `VersionCode` as a plain class (a JVM inline class could not be told from a raw number by the
K-SEC-11 reflection test).

### 4.2 Pipeline (one serial lane)

1. Before the migration gate opens, events are dropped (nothing is tracked during the migration except
   `migration_done`, which `:androidApp` hands over after the gate opens, 05 M5).
2. The tier is checked against consent.
3. The typed schema check (`AnalyticsSchema.events`, every property against its allowlist of wire values).
4. Once-per-day checks (`backend_connected`, the daily summaries).
5. The full wire scrub (`PrivacyScrubber` + `LeakDetector`: links, e-mails, addresses, paths, credentials, ids, free
   text, control characters, length). A rejection names the reason and field, never the value. The detector is a
   second line; the guarantee is the typed API.
6. The queue, then the store.

### 4.3 Queue and upload

- Queue: 500 events / 256 KiB; events expire after 7 days, the one-time switch events (`migration_done`, and the
  `app_updated` whose `from_code` is below 10000) after 90 days, so a destination chosen in 3.0.x still learns how the
  switch went. Oldest dropped first. Stored events are scrubbed again when loaded.
- With `NoOpSink` (the default until D16-1) nothing is handed on. BASIC events are still kept on disk under the limits
  above, so the switch's outcome survives until a destination exists; a person can see and delete them (§6).
- Upload triggers: when the app goes to the background, and every 30 minutes while in the foreground. Never a scheduled
  alarm, job or wake-up (13 §12.2).
- Network: a validated network only. **In the background: unmetered only, never mobile data, never under Data Saver.**
  In the foreground: any validated network unless Data Saver restricts it.
- Up to 4 batches of 25 per upload; every batch is scrubbed again before it is sent; a sent batch leaves the queue; a
  failed one stays and backs off (1 min doubling to 6 h). Failures never reach the person; diagnostics records the
  category.
- Store file: `files/native/analytics/` (app-wide, no profile data, so not in `ProfileScope`), written through one
  `WriterLane`; Manage space › Delete everything removes it.

---

## 5. Sinks

| Sink | Kind | Endpoint and body | Status |
|---|---|---|---|
| `NoOpSink` | None | — | built; default binding |
| `JsonDebugSink` | LocalDebug | writes the exact wire body locally (qa and tests) | built |
| Aptabase | Remote | 16 §5.5 row (`/api/v0/event`, `App-Key`) | not built; after D16-1 |
| PostHog | Remote | 16 §5.5 row (`/batch/`, project token, `$process_person_profile: false`; discard client IPs in the project) | not built |
| Plausible | Remote | 16 §5.5 row [U exact fields] | not built |
| Firebase (GA4 Measurement Protocol, no SDK) | Remote | 16 §5.5 GA4 row [U endpoint and fields; Google documents it as a supplement to SDK collection, so some reports stay empty] | not built; only if the owner picks it |
| Self-hosted (`Custom`) | Remote | our own JSON `{"events":[…]}` to an HTTPS URL | not built |
| `FanOutSink` | Remote | sends each batch to every bound sink; a batch leaves the queue only when all accept | not built; the "later, to other tools" path |
| `QaFixture` | Remote (qa only) | `QaHooks.analyticsEndpoint` on `10.0.2.2` only; absent from release DEX (16 N16-D13) | not built |

Every remote sink: HTTPS only, the host is a `KnownHosts` Fetch host (16 §4.2, `verifyAnalyticsEndpoint`), no cookies,
`User-Agent: OpenCodeMobile`, no redirects, and it goes through `AnalyticsTransport` over the hardened client (04 §3).
No vendor SDK in any build (12 X-04, G8). Build configuration is `native/androidApp/analytics.properties`
(`sender=none` today; 16 §5.5).

---

## 6. "What we collect" (the disclosure page)

- **Where:** Settings › This app › Privacy › **What we collect**, one page, kit parts only, reached from the existing
  Privacy page (no new level, 16 §5.8). The rows of 16 §5.8 stay: the DETAILED switch and "What was sent".
- **Content:** generated from `WhatWeCollectSource` (built): every registered event under its tier with every value it
  can take, the envelope fields, the never-collected list, the sending state ("Not sent yet" while the sink is none,
  else the destination's name) and the rotation periods. Keys only; the words live in `:ui:words`
  (`strings_privacy.xml`, 7 languages). A test fails if an event has no words (B-ANLY-011).
- PRIVACY.md lists the same table (16 §4.3); `AnalyticsSchemaTest` fails if an event has no PRIVACY.md row.

---

## 7. F-Droid

An always-on BASIC tier earns F-Droid's "Tracking" anti-feature. With reproducible builds the F-Droid APK is the same
bytes as ours, so a BASIC-off F-Droid build would be a separate build. The owner decides at the F-Droid submission,
after the switch (15 §12, 16 D16-2): keep BASIC on and declare the label, or ship that build with `sender=none`.
Nothing in the core changes either way.

---

## 8. Work units and slice placement

| Unit | Slice | Module | Who | State |
|---|---|---|---|---|
| S1-ANALYTICS core | 1 | `:engine:analytics` | cloud session (high effort only if it touches the schema contract; medium otherwise) | built 2026-11-01, 49 tests; awaiting `MODULE_OWNERS`, the engine-order edit and merge |
| S1-ANALYTICS-WIRE | 1 | `:androidApp`, `:android:platform`, `:core:data` | Sonnet | `DeviceConditions` from `ConnectivityManager` and the lifecycle; `start()` after the first frame; `openGate()` at the migration gate; the file-backed store through a `WriterLane`; `NoOpSink` binding for `sender=none`; `AnalyticsTransport` over `:core:net` |
| S1-ANALYTICS-EMIT | 1–2 | `:engine:diagnostics`, `:engine:connection`, `:engine:delivery`, `:engine:requests` | each engine's owner | crash/freeze/error categories, `backend_connected`, daily counters, 09 §6.8 chat events |
| S4-ANALYTICS-UI | 4 | `:feature:settings`, `:ui:words` | Sonnet | the "What we collect" page and the 16 §5.8 rows |
| Sinks | after D16-1 | `:engine:analytics` + `:androidApp` | Codex | the chosen sink(s), `AnalyticsSenderContractTest` per sink, `verifyAnalyticsEndpoint` |

---

## 9. Tests and catalog rows

B-SEC-191 (16) plus the proposed rows B-ANLY-001…004 and 006…015 from the S1-ANALYTICS report (005 folded into
B-SEC-191). The catalog owner adds them, or renumbers them into SEC. Tests as built: `AnalyticsPrivacyTest`,
`AnalyticsFieldTypesTest` (a planted `String` field fails it), `AnalyticsSchemaTest`, `EventQueueTest`,
`UploadPolicyTest`, `IdentityTest`, `WhatWeCollectTest`, `AnalyticsEngineTest`. Still to add with the wiring: the
egress harness EG-1/EG-2 (16 §2.5: with `sender=none`, **0** analytics requests leave the device), the canary scan of
the queue file (14), and a radio check that no upload happens on mobile data in the background.

---

## 10. Owner decisions

| # | Question | Default |
|---|---|---|
| D16-1 | Where do BASIC events go? | Aptabase EU; nothing is sent until you answer |
| D18-1 | `crash_seen` is BASIC (category only) as you listed it, while local crash details stay behind the off-by-default crash capture? | Yes |
| D18-2 | (at the F-Droid submission, after the switch) keep BASIC on with the "Tracking" label, or an F-Droid build with BASIC off? | Ask then |

---

## 11. Changes other sections take

- 01 §5.2, §6.2, §9.6: the module, its order place, and "nothing phones home except the analytics core". Done.
- 03 S27, §0.3, §1.2, §6.6, §11.2, §13.5: module, ownership, vectors, bindings. Done.
- 12 W-20: the module name and the sink list. Done.
- 15 §5 / S1: the unit is built; S4-ANALYTICS-UI. Done.
- 16 §5: points here for schema, tiers, ids and upload; §5.4's "install id" line replaced; §5.9 deferred. Done.
- 13 §12.2 last bullet: superseded by §4.3 here (no wake-ups, background only on unmetered networks).
- 02 S-29 and the catalog: add the B-ANLY rows (catalog owner).
