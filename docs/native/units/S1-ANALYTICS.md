# S1-ANALYTICS: the analytics core

Date: 2026-11-01. Branch: `native/analytics-2026-11-01`, created from `claude/native-analytics-implementation-1jdbqw`
at `6627d320`. Write set: `native/engine/analytics/**` (new module) and this report.

Inputs: `native/AGENTS.md`, `docs/native/slice0/P1-skeleton.md`, `DRAFT-STATUS.md` (owner decisions), plan
`01` (ADR), `02` (G7, G8, §1.1 row 17, NG15, S-22, S-23, S-29, S02-5), `15` (S4-ANALYTICS, C15-9, R12-1),
`16` §5 (the analytics core, N16-D9, K-SEC-11, K-SEC-12). No `audit/codex-*.md` finding touches analytics (the
two audits cover 01+03 and 04+05).

## Result

**Implemented and verified on desktop (JVM) and compiled for Android. Not wired into an app, not enabled, and
nothing is sent:** no remote sink exists, and the module is not yet bound in `:androidApp` / `:desktopApp`.

- `:engine:analytics:desktopTest`: **49 tests, 0 failures** (8 classes).
- `:engine:analytics:compileAndroidMain`: passes.
- `:quality:test`: Konsist (11), graph smoke, kit-only and desktop UI tests pass on the new module.
  `ModuleOwnersTest` **fails** because `MODULE_OWNERS` has no `:engine:analytics` row (only the coordinator edits
  that file; see the requests below). That is the only failure.
- Planted check: adding `val note: String` to an event class makes `AnalyticsFieldTypesTest` fail with
  `SettingChanged.note is java.lang.String`; reverted.

## What was built (package `io.github.eslamasabry.oc.engine.analytics`, commonMain only)

| File | What |
|---|---|
| `AnalyticsEvent.kt` | `AnalyticsTier` (Basic, Detailed); `AnalyticsEvent` (sealed); `PropValue` (Choice, Flag, Count, Duration, Version only); `EventSpec` / `PropSpec` (each property's full allowlist of wire values); `EventTrigger` |
| `AnalyticsSchema.kt` | The single source of events and the **schema registry** `AnalyticsSchema.events` |
| `Vocabulary.kt` | Closed enums events may use; `ErrorCategory.of(Failure)` is an exhaustive `when` over `:core:common` `Failure`, so an error goes out as a category, never as text |
| `Buckets.kt` | `CountBucket`, `DurationBucket`, `VersionCode` |
| `Wire.kt` | `SystemProps`, `WireEvent` (internal constructor), `WireBatch`, `WireCodec` (our own JSON) |
| `PrivacyScrubber.kt` | `PrivacyScrubber` (schema allowlist + exact id shapes + system-prop formats) and `LeakDetector` (links, emails, addresses, paths and file names, credentials and tokens, ids, free text, control characters, length). A rejection names the reason, field and leak kind, never the value |
| `EventQueue.kt` | Local queue: 500 events / 256 KiB / 7 days, oldest dropped first, batches of 25, drop by tier |
| `UploadPolicy.kt` | `DeviceConditions`, `UploadPolicy`, `HoldReason`, `Backoff` (1 min doubling to 6 h) |
| `Identity.kt` | `IdSource` / `SecureIdSource` (`Uuid.random()`, secure; no `kotlin.random`, K-SEC-12); install id rotating every 30 days; session id per process, rotated after 60 idle minutes, never stored |
| `Consent.kt` | `DetailedConsent` (NotAsked, Granted, Declined), `ConsentState`, `UsageCounter`, the daily ledger |
| `Sinks.kt` | `AnalyticsSink` interface (`SinkId`, `SinkKind` None / LocalDebug / Remote), `NoOpSink`, `JsonDebugSink` |
| `AnalyticsStore.kt` | `AnalyticsStore` interface + `InMemoryAnalyticsStore`; the persisted document (no session id) |
| `WhatWeCollect.kt` | `WhatWeCollectSource`: the data source for the future Privacy › "What we collect" screen (events per tier with every possible value, envelope fields, `NeverCollected`, sending state, rotation periods). Keys only; words belong to `:ui:words` |
| `AnalyticsEngine.kt` | `Analytics` (emitter API) and `AnalyticsEngine` |

### Events

BASIC (always on, anonymous, minimal): `app_start` (cold/warm, start-up time bucket), `app_updated`,
`migration_done`, `backend_connected` (server kind + address class; at most once per kind per UTC day),
`update_result`, `crash_seen` (category, fatal), `freeze_seen` (stall/ANR, duration bucket, foreground),
`error_seen` (category), and once per UTC day `daily_use` (opens, chats started, messages sent as buckets),
`server_kinds` and `agent_kinds` (counts per kind as buckets).
Envelope on each event: app version, build, channel, OS family and major version, **device class**, language,
debug flag, hour (UTC), rotating install id, session id.

DETAILED (opt-in): `screen_open`, `feature_used`, `flow_step` (started / completed / abandoned / failed),
`action_timed` (duration bucket), `setting_changed` (which setting, never its value).

### Pipeline (the engine)

`track(event)` and `count(counter)` never throw, block or suspend: they put the event in the inbox, and a full
inbox (1,024) drops it. The engine has one serial lane. On that lane each event goes through these steps in order:

1. The migration gate: events that arrive before `openGate()` are dropped.
2. The tier is checked against consent.
3. The typed schema check.
4. The once-per-day check for `backend_connected`.
5. The full wire scrub.
6. The queue, then storage.

Uploads run when the app goes to the background and every 30 minutes in the foreground. Each upload checks the
policy, then sends up to 4 batches of 25. Every batch is scrubbed again before it is sent. A sent batch is removed
from the queue; a failed one stays and backs off.
`sent` keeps the last 50 bodies that left the phone, exactly as sent. Declining DETAILED drops queued DETAILED
events at once. `deleteEverything()` clears the queue, the sent log, the ledger, the install id and consent.
Stored events are scrubbed again when they are loaded, so an edited store cannot smuggle content out.
With `NoOpSink` the queue is never written to disk and nothing is ever handed on.

## Catalog rows covered

The catalog (`behaviors.jsonl`, 1,544 rows, last SEC row B-SEC-183) has **no analytics rows**. Plan 15 lists
S4-ANALYTICS as "new SEC rows (top-up)", and plan 16 names B-SEC-191 for `AnalyticsPrivacyTest`. That row is not
in the catalog yet. Tests use B-SEC-191 as plan 16 names it, and use **proposed** rows B-ANLY-001…015. The catalog
owner should add them (see the requests below).

| Row | Behavior | Tests |
|---|---|---|
| B-SEC-191 | No planted canary (path, link, address, email, key, token, id, free text) in any wire field gets past the scrubber; none reaches the queue, storage, sent log, sink output or logs; rejections never echo the value; event classes hold only enums, booleans, buckets, versionCode; the session id is never stored; a throwing sink never surfaces | `AnalyticsPrivacyTest` (8), `AnalyticsFieldTypesTest` (1) |
| B-ANLY-001 | BASIC is recorded without consent; DETAILED only after opting in | `AnalyticsEngineTest` |
| B-ANLY-002 | Declining DETAILED drops queued DETAILED at once and sends no more | `AnalyticsEngineTest`, `EventQueueTest` |
| B-ANLY-003 | Nothing is tracked before the migration gate opens | `AnalyticsEngineTest` |
| B-ANLY-004 | Schema: unique snake_case names ≤ 40, keys unique, every class registered (exhaustive `when` + JVM permitted-subclasses check), every name, key and value passes the detector, BASIC/DETAILED sets | `AnalyticsSchemaTest` (5), `AnalyticsFieldTypesTest` (1) |
| B-ANLY-006 | Queue bounds: 500 events, 256 KiB, 7 days, oldest first | `EventQueueTest` (3) |
| B-ANLY-007 | Batches ≤ 25; failure keeps the batch; backoff 1 min doubling to 6 h | `EventQueueTest`, `UploadPolicyTest`, `AnalyticsEngineTest` |
| B-ANLY-008 | Never on mobile data or Data Saver in the background; validated network only; upload on background and every 30 min in the foreground; no destination → nothing | `UploadPolicyTest` (4), `AnalyticsEngineTest` (2) |
| B-ANLY-009 | Install id random, 30-day rotation (also on clock rollback); secure 128-bit ids; session rotates after 60 idle minutes | `IdentityTest` (4) |
| B-ANLY-010 | No-op sink: never called, nothing on disk, nothing "sent"; JSON debug sink writes exactly the wire body; sent log 50 newest first | `AnalyticsEngineTest` (3) |
| B-ANLY-011 | What-we-collect lists every registered event under its tier with all values, the never-collected list, consent and sink state | `WhatWeCollectTest` (3) |
| B-ANLY-012 | Daily counts roll into one BASIC summary per UTC day as buckets; `backend_connected` once per kind per day | `AnalyticsEngineTest` (2) |
| B-ANLY-013 | `track` never throws or blocks; an overfull inbox drops | `AnalyticsEngineTest` |
| B-ANLY-014 | Queue, consent and install id survive restart, session does not; corrupt or tampered store sends nothing; delete everything | `AnalyticsEngineTest` (3) |
| B-ANLY-015 | Errors as categories, never text; bucket edges | `AnalyticsEngineTest`, `AnalyticsSchemaTest` |

(B-ANLY-005 was folded into B-SEC-191.)

## Commands and results

Environment: the Android SDK from `commandlinetools-linux-16111833_latest.zip` in
`/opt/android-sdk/cmdline-tools/latest`, with `platform-tools` 37.0.1, `platforms;android-37.1`,
`build-tools;37.0.0` (AGP also pulled `build-tools;36.0.0`). `ANDROID_HOME=/opt/android-sdk`, Gradle ran on
JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), foojay provisioned Temurin 17.0.21 for the toolchain, and
`native/local.properties` (gitignored) holds `sdk.dir`.

Maven Central answered **429 Too Many Requests** to this container (about 14 sessions in parallel). I worked
around it with a **local-only** init script, `~/.gradle/init.d/central-mirror.gradle.kts`. It puts Google's
Central mirror (`maven-central.storage-download.googleapis.com/maven2`) first. It is not in the repository.

```
cd native
./gradlew :engine:analytics:desktopTest                 # 49 tests, 0 failures
./gradlew :engine:analytics:compileAndroidMain           # BUILD SUCCESSFUL
./gradlew :quality:test                                  # only ModuleOwnersTest fails (no MODULE_OWNERS row)
./gradlew :engine:analytics:desktopTest --tests '*AnalyticsFieldTypesTest*'   # with a planted String field: fails as intended
```

No formatter was run: `tool/format-changed.sh` (native/AGENTS.md rule 13) does not exist in the repo yet. I
wrapped the longest lines by hand. Some lines are still up to 128 characters.

## Resolved versions

All come from the catalog, with nothing new: Kotlin 2.4.21, kotlinx-coroutines 1.11.0, kotlinx-serialization
1.12.0, kotlinx-collections-immutable 0.5.2, Metro runtime 1.5.1 (on the classpath by convention, not used yet),
AGP 9.3.3, Gradle 9.7.1. Tests use kotlin-test, coroutines-test and `:core:testing`.

## Deviations from the plan

1. **Module.** I followed the brief and plan 15 (`native/engine/analytics`, `:engine:analytics`), not plan 16
   N16-D9 (`:engine:diagnostics`, package `…engine.diagnostics.analytics`). Plan 02 notes this conflict
   between 15 and 16. The package is `io.github.eslamasabry.oc.engine.analytics`.
2. **Install id.** Plan 16 §5.4 lists "install id" under *never sent*. The owner brief asks for "a random
   install id that rotates". I built the owner's version: a random 128-bit id with no relation to any device or
   account id. It is replaced every 30 days, and Delete everything replaces it at once. Plan 16 §5.4 and
   PRIVACY.md need a matching edit.
3. **BASIC set.** The brief's BASIC tier adds things plan 16 §5.3 does not have:
   - crashes and freezes (plan 16 had `crash_seen` in DETAILED, gated by local crash capture);
   - start-up time (plan 16 had `cold_start` in DETAILED);
   - daily use counts;
   - server and agent kinds as counts;
   - error categories (plan 16 had `failure_seen` in DETAILED);
   - device class.

   I followed the brief. `app_open` became `app_start` with the start kind and a duration bucket. An integrator
   may still gate `crash_seen` on B-SEC-042 (crash capture off by default) if the owner wants that.
4. **Names.** I renamed some names because the leak detector flags the words `secret` and `token`:
   - `migration_done.secrets_reentry` became `reentry_count`;
   - `TimedAction.FirstToken` became `FirstReply`;
   - `update_result`'s class is `UpdateOutcome`, because an `UpdateResult` enum already exists.
5. **`VersionCode`** is a plain class, not a `@JvmInline value class`. On the JVM an inline value class is just
   an `int` field, so the K-SEC-11 reflection test could not tell it from a raw number.
6. **Sender naming.** The brief says "sink" and plan 16 says `AnalyticsSender`. It is one interface,
   `AnalyticsSink`. It takes `WireBatch` and returns `Outcome<Delivery>`, where `Delivery.body` is exactly the
   bytes sent. That is the "json is exactly the body sent" rule of plan 16 §5.2.
7. **Wire envelope.** `SystemProps` uses `OsFamily` + `osMajor` and adds `device: DeviceClass`. `WireEvent` adds
   `install`.
8. **Not built here:** Remote senders (Aptabase, PostHog, Plausible, Custom over `:core:net`) are out of scope
   until D16-1 / R12-1. The file-backed store (`files/native/analytics/queue.json` through a WriterLane) is in
   `:core:data`. Metro bindings, the `analytics.properties` build config and the PRIVACY.md table are also not
   built here. Each has a `TODO(move-to-core)` or a note in the KDoc.

## Catalog and coordinator requests

- **MODULE_OWNERS:** add `:engine:analytics	S	diagnostics-owner	slice-1` (or whichever owner the coordinator
  chooses). Without it `ModuleOwnersTest` fails.
- **ModuleRules.engineOrder (build-logic):** add `"analytics"`. Today it is absent, so no other engine may
  depend on `:engine:analytics`. Plan 15 C15-9 says "after diagnostics". For every engine to emit, place it
  right after `"settings"` (or after `"diagnostics"`, with settings changes emitted by observing flows, plan 16 §5.1).
  The module depends only on `:core:common`.
- **Behaviour catalog:** add B-SEC-191 (plan 16 names it) and rows B-ANLY-001…004 and 006…015 as tabled above.
  The `B-ANLY` prefix is a proposal; renumber into SEC if preferred, and the tests will be renamed.
- **No new libraries** were needed.
- **Konsist K-SEC-11 / K-SEC-12** are covered inside the module (a reflection test; `Uuid.random()` instead of
  `kotlin.random`). The `:quality` owner may lift them into `KonsistRulesTest`.

## Open problems

- `:ui:routes` and the agents engine do not exist yet. `ScreenId`, `UsedFeature`, `AgentKind` and the other
  DETAILED enums are this module's first lists, each with an `Other` fallback where it makes sense. When those
  modules land, map them (TODO in `Vocabulary.kt`).
- The `lang` envelope field accepts any 2–3 letter lowercase code. Narrowing it to the app's exact language
  set needs that list from `:ui:words`.
- The platform side is not written: mapping ConnectivityManager and the lifecycle into `DeviceConditions`, the
  first-frame `start()`, `openGate()` at the migration gate, and binding `NoOpSink` for `sender=none`.
- `LeakDetector` is a second line of defence and is pattern-based by nature. The guarantee comes from the
  typed API plus the per-property allowlist. The detector keeps a mis-registered spec value from looking like
  content. `AnalyticsSchemaTest` checks every allowed value against it.
