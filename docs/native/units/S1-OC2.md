# S1-OC2 · OpenCode 2 DTOs, event union and fixtures

Date: 2026-11-01. Branch: `native/oc2-2026-11-01` (from `claude/native-oc2-setup-c4cx8k` at `6627d320`).
Write set: `native/protocol/oc2/**`, `native/fixtures/oc2/**` (plus this report).
Inputs: `DRAFT-STATUS.md`, plan 01 (binding), plan 15 row S1-OC2, plan 04 §2, §7.1–7.6 and §11.2,
`docs/opencode2-protocol-notes.md`, `audit/codex-04…` (no finding touches this unit's slice), `native/AGENTS.md`,
`docs/native/slice0/P1-skeleton.md`, and the Dart client in `lib/api2/`.

**Result.** The brief's slice is done: OpenCode 2 DTOs on kotlinx.serialization (session, message, part/content,
tool state, inbox, permission, form, catalog, error, usage), the 83-type event union with unknown-type tolerance,
the 24 Flutter fixtures (plus two sample sets) with a SHA-256 manifest, and **proof that every fixture decodes to the
same variants and fields as the Dart client**. The proof is checked against a golden that the Dart parsers wrote
themselves. The GatewayEvent mapping is in one adapter file on local types marked `TODO(move-to-core)`.

**Not done (plan 15 finish line, second half):** "live proof steps 1–25 pass against OpenCode 2.1.1 on the VPS".
That needs the transport (S1-NET, `:core:net`), the client and the gateways, which are outside this brief. This
container also has no VPS access.

Shipping state: implemented, verified locally (45 tests), committed and pushed to the branch. No PR, no CI run, no
release.

---

## 1. What was built

### 1.1 `native/fixtures/oc2/`

| Path | Content |
|---|---|
| `flutter/api2/*` (24 files) | `test/fixtures/api2/` copied byte for byte: 23 JSON bodies plus `events.sse` (a captured prompt round-trip on beta-18600, 30 envelopes) |
| `flutter/coverage/oc2_{events,messages}_samples.json` | `test/fixtures/coverage/` sample sets: 16 event cases (88 envelopes) and 21 message cases (46 messages). They cover event families the 24 do not (compaction, revert, retry, forms, permissions, inbox phases) |
| `MANIFEST.sha256` | 26 entries, `sha256sum` format |
| `parity/dart-variants.json` | What the **Dart** parsers decode each of the 26 fixtures to: a neutral descriptor (variant tag, phase, ids and fields; nulls omitted) |
| `tool/dart_variants.dart` + `tool/dart_variants.sh` | The golden generator. It imports `package:opencode_mobile/api2/{models,events}.dart` and `domain/usage_statistics.dart`. The script gives a plain Dart SDK a one-package config, so neither Flutter nor `pub get` is needed. Those three libraries import only `dart:core` |
| `README.md` | Provenance, the checks and how to regenerate |

### 1.2 `native/protocol/oc2/` (`:protocol:oc2`, `oc.kmp.library`, explicit API; depends only on `:core:common`)

Discovered by `settings.gradle.kts` (layer `protocol`); no settings edit was needed.

| File (`src/commonMain/.../protocol/oc2/`) | Content |
|---|---|
| `wire/Oc2Wire.kt` | `Oc2Wire.json` (= `WireJson`) and `Oc2Wire.decode`, the **tolerant decode**: one descriptor-driven pass before the generated serializer runs. A null or wrong-typed field is dropped, so the property default applies (Dart `_asString(...) ?? default`). Integer fields take any JSON number truncated toward zero (Dart `num.toInt()`). A nested object or list item that lacks a required property is dropped (Dart `fromJson` → null, `_mapList`). Non-object items in object lists are skipped. Unions and hand-shaped types use `oc2.raw.*` serializers and do their own dispatch. Also holds the `RawObjectSerializer` / `SurrogateSerializer` bases and small readers. Marked `TODO(move-to-core)` (plan 04 §2.2 puts these helpers in `:core:common json/`) |
| `wire/OpenEnums.kt` | `Oc2WireEnum` + `OpenEnumSerializer` (an unknown wire string becomes `Unknown`): `Oc2SessionOutcome`, `Oc2Delivery`, `Oc2PermissionEffect`, `Oc2FormFieldType`, `Oc2FormStatus`; `Oc2PermissionReply` with wire names |
| `wire/Common.kt` | `Oc2Project`, `Oc2Location` (`workspaceID` or `workspace`), `Oc2Tokens` (`cache.read/write`), `Oc2ModelRef` (own-provider prefix stripped, foreign kept, `"default"` kept as sent), `Oc2StructuredError` (blank → null, `name` stands in for `type`), `Oc2Page` (cursors verbatim) |
| `wire/Session.kt` | `Oc2Session` (+ `reverted` from any non-null `revert`, `revert` only with a non-empty `messageID`, `archived`, `directory`), `Oc2SessionTime`, `Oc2Fork`, `Oc2ForkBoundary`, `Oc2SessionRevert`, `Oc2FileAttachment` |
| `wire/Content.kt` | Unions `Oc2ToolResultItem` (text, file, Unknown), `Oc2ToolState` (streaming, running, completed, error, Unknown), `Oc2AssistantContent` (text, reasoning, tool, Unknown); `Oc2ContentTime` (incl. `pruned`) |
| `wire/Message.kt` | `Oc2Message`: the 10 variants (user, assistant, synthetic, system, skill, shell, agent-switched, model-switched, location-switched, compaction) plus `Unknown(type, raw)`. A message without a string `id` gives null |
| `wire/Requests.kt` | `Oc2InboxItem`, `Oc2PermissionRequest/Source/Rule`, `Oc2SavedPermission`, `Oc2FormInfo/Field/Option/Condition` (`holds`: `eq` on a multiselect = includes; numbers compare by value), `Oc2FormState` |
| `wire/Catalog.kt` | `Oc2ModelInfo` (composite id peeled, cost list or single object, base cost preferred over tiers, `raw` kept), `Oc2ModelCost/Limit/Capabilities`, `Oc2ProviderInfo`, `Oc2AgentInfo` (id↔name fallback, `selectable`), `Oc2Command`, `Oc2Skill`, `Oc2ConfigEntry`, `Oc2FsEntry` |
| `wire/Errors.kt` | `Oc2ErrorEnvelope` (`_tag` or `code`, `message`, `detail(key)`); status → `Failure` stays with the transport |
| `wire/Usage.kt` | `Oc2UsageStatistics`: **strict**, as Flutter's `UsageStatistics.fromJson` is (invalid → null) |
| `events/Oc2Event.kt` | The sealed union, ported one to one from `lib/api2/events.dart`. Phase-carrying variants hold `Oc2Phase` (not on the wire). Adds `PassThrough(type, raw)` and `Unknown(type, raw, ignored)` |
| `events/Oc2EventRegistry.kt` | Type string → decoder. `typed` (83, exactly Flutter's set), `passThrough` (10) and `ignored` (10), as plan 04 §7.5 lists them. `decode` never throws; a typed event with an unusable payload (`permission.asked` without `id`) becomes `Unknown` |
| `events/Oc2Envelope.kt` | `Oc2Envelope` (`data`, or the whole envelope for the bare `log.synced`), `Oc2Durable` (`seq: Long`). `decode(JsonElement)` / `parse(text)` never throw |
| `adapter/Oc2EventAdapter.kt` | **The one adapter file.** The top half is local `GatewayEvent`, `MessageInfo`, `Part`, `ToolPart`, `RunOutcome` and the enums, all `TODO(move-to-core)`; names follow plan 04 §11.2 where it has them. The bottom half is `Oc2EventAdapter`, a rule-for-rule port of `Api2EventAdapter` (`lib/api2/gateway_events.dart`). It includes the open-call tracker: at most 256 calls, steps and step models, oldest evicted. Lost results are settled at step end, at the next step and at run end, followed by `TranscriptStale`. `nowMs` is injected (no `Clock.System`) |

Tests: `src/commonTest` (pure: `Oc2ModelsTest`, `Oc2EventsTest`, `Oc2EventAdapterTest`) and `src/desktopTest`. The
desktop tests read the fixtures from disk: `Oc2FixtureManifestTest`, `Oc2FixtureDecodeTest`, `Oc2ParityTest` +
`ParityDescriber`, and `Oc2EventRegistryDocTest`. The build file passes the fixture and repository roots as system
properties. Every test is named `B-AREA-NNN what it proves` and was written before the code it tests.

## 2. Catalog rows

| Row | Status | Where |
|---|---|---|
| B-PROTO-014 | **Covered.** The captured round-trip replays into typed events; unknown types never throw; `log.synced` bare; permission, form, status, mcp, vcs, refresh and execution events; registry lists; every type documented in notes §3.2 is in exactly one list | `Oc2EventsTest`, `Oc2FixtureDecodeTest`, `Oc2ParityTest`, `Oc2EventRegistryDocTest` |
| B-PROTO-027 | **Covered.** Sessions, the message union, receipts, model/provider/agent/catalog listings, location, fs, permissions, forms (six field types, `when`), error envelopes; unknown types and enum values never throw; all fixtures match Dart | `Oc2ModelsTest`, `Oc2FixtureDecodeTest`, `Oc2ParityTest`, `Oc2FixtureManifestTest` |
| B-CHAT-005 | **Covered at adapter level** (not the transcript reducer). Lost results are settled at step end and at the next step; they fail on interrupt; a result left alone stays; other sessions are untouched; an unseen result triggers a reread; the 256 bound holds | `Oc2EventAdapterTest` |
| B-PROTO-047 | **Covered.** Installation events keep exact versions (`PassThrough` → `Installation`) | `Oc2EventsTest`, `Oc2EventAdapterTest` |
| B-PROTO-015 | **Covered on local types.** Round-trip pieces (inbox user message `text-0`, steps, `text-<n>`/`reasoning-<n>`, tool input deltas), idle, failure without idle, interrupt reasons, permission asked with `callId`, renames and moves, refresh hints, credential switch | `Oc2EventAdapterTest` |
| B-PROTO-010 | Partial: page decode with both cursors kept verbatim. Walking the pages needs the client (S1-NET) | `Oc2FixtureDecodeTest` |
| B-PROTO-061 | Partial: the "cursor on the last page too" fact is pinned. The bounded history reader is not built | `Oc2FixtureDecodeTest` |
| B-PROTO-011 | Partial: receipt and inbox list decode. Request wire shapes are not built | `Oc2FixtureDecodeTest` |
| B-PROTO-012 | Partial: error envelope tag, message and extras. The strict `GET /session/active` validation is not built | `Oc2ModelsTest` |
| B-PROTO-017 / 018 / 019 | Decode level only. The mapping to the app message model (`mappers/`) needs `MessageInfo`/`Part` in `:core:model` | `Oc2ParityTest`, `Oc2FixtureDecodeTest` |
| B-PROTO-089 / 090 / 091 / 092 / 093 | Partial: the v2 halves decode (usage, retry, tool timing and `executed`, step and execution errors, cost tiers, release date, agent colour) and map to `SessionUsage` / `RunRetrying` / tool parts. The v1 halves, and downstream tracking, are other units | `Oc2ModelsTest`, `Oc2EventsTest`, `Oc2EventAdapterTest` |
| B-PROTO-034 | Partial: durable markers and `seq` decode, and deltas carry none. The stream and reconnect are `:core:net` | `Oc2EventsTest` |
| B-PROTO-062 | Partial: the decode-side own-prefix strip. Send-side `ModelRef.normalized` is not built | `Oc2ModelsTest` |
| B-PROTO-013, 016, 021, 037, 038, 039; B-SEC-055, 056 | **Not started.** Dialect, capabilities, probe, transport and credential redaction need S1-NET's transport | — |
| B-PROTO-134, B-PROTO-136 | **Not in the catalog.** `behaviors.jsonl` ends at B-PROTO-127. Plan 04 §7.5 calls the registry test B-PROTO-136; it is named B-PROTO-014 here | — |

## 3. Commands and results

Environment, set up first:

- `commandlinetools-linux-16111833_latest.zip` (SHA-1 `e025545c62a8…`) was installed to
  `/opt/android-sdk/cmdline-tools/latest`, with `ANDROID_HOME=/opt/android-sdk` and licences accepted.
- `sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"` installed platform-tools 37.0.1,
  android-37.1 and build-tools 37.0.0. AGP later added build-tools 36.0.0 itself.
- JDK 21.0.12.1 runs Gradle; JDK 17.0.20.1 (`openjdk-17-jdk-headless`) is the toolchain.
- `~/.claude/settings.json` sets `BASH_DEFAULT_TIMEOUT_MS=1800000` (from the next session on).
- `native/local.properties` holds `sdk.dir` (gitignored).
- Maven Central answered 429 through the proxy, as in P1. A container-only `~/.gradle/init.d/central-mirror.gradle.kts`
  (not in the repo) adds Google's Central mirror and keeps the plugin portal and Google; it also drops
  `repo.maven.apache.org`.
- Dart SDK 3.13.7 (the AGENTS.md pin) was installed to `/opt/dartsdk-3.13.7`, for the parity golden only.

All Gradle commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

| # | Command | Result |
|---|---|---|
| 1 | `native/fixtures/oc2/tool/dart_variants.sh` (`DART=/opt/dartsdk-3.13.7/dart-sdk/bin/dart`) | `wrote …/dart-variants.json: 26 fixtures`; byte-identical on rerun |
| 2 | `./gradlew -Poc.werror=true --rerun-tasks :protocol:oc2:desktopTest :protocol:oc2:compileAndroidMain` | BUILD SUCCESSFUL. **45 tests, 0 failures, 0 skipped**: Adapter 12, Events 10, Models 10, FixtureDecode 8, Manifest 2, Parity 2, RegistryDoc 1. Warnings-as-errors clean on both targets |
| 3 | Planted divergence (dropped the `compaction` message variant), `--tests '*Oc2ParityTest*'` | Fails as required, naming `api2/messages_2_1_1.json.data[4].v: "Compaction" (Dart) vs "Unknown"` and the lost fields. Reverted |
| 4 | `./gradlew :quality:test` | 16 of 17 pass (all Konsist rules). `ModuleOwnersTest` fails: `MODULE_OWNERS` lacks `:protocol:oc2`, and also the already-merged `:tools:arb2xml` and `:tools:flutter-migration-readers`, so it was red before this unit (see §6) |
| 5 | `dart format --language-version=3.10` on the generator; `dart analyze` in an isolated root with `avoid_relative_lib_imports`, `avoid_print`, `unnecessary_import`, `prefer_const_constructors` | Formatted; no issues. `flutter analyze` itself was not run (no Flutter in this container) |
| 6 | `DART=… tool/qa/check_commits.sh 6627d320` | `G33: OK: 3 commit(s) … 1 changed Dart file(s) formatted` |
| 7 | `git grep` of the new files for IPv4 literals and e-mail addresses | None |

The iOS klibs were not compiled (`oc.ios=false`; plan: VPS nightly).

## 4. Resolved versions (`:protocol:oc2:dependencies --configuration desktopRuntimeClasspath`)

Kotlin stdlib 2.4.21; kotlinx-serialization-core/json 1.12.0; kotlinx-coroutines-core 1.11.0;
kotlinx-collections-immutable 0.5.2; Metro runtime 1.5.1 (from the convention plugin; unused here). The module adds
no library beyond what `oc.kmp.library` already gives every module.

## 5. Deviations from the plan (and from Flutter), with reasons

| # | Plan / Flutter | Built | Why |
|---|---|---|---|
| D1 | Plan 04 §2.2: tolerant serializers in `:core:common json/` | In `wire/Oc2Wire.kt` and `wire/OpenEnums.kt`, `TODO(move-to-core)` | `:core:**` belongs to S0-CORE |
| D2 | Plan 04 §11.2: `GatewayEvent` in `:core:domain`; `MessageInfo`/`Part` in `:core:model` | Local minimal shapes at the top of `adapter/Oc2EventAdapter.kt`, `TODO(move-to-core)` | Same; the brief asked for one adapter file |
| D3 | Flutter reads `installation.*`, `pty.*`, `worktree.ready/failed`, `workspace.ready/failed` as `UnknownApi2Event` | `Oc2Event.PassThrough` (plan 04 §7.5) | Binding plan. No fixture contains these types, so parity is unaffected; `ParityDescriber` would describe them as Dart does (`Unknown`) |
| D4 | Flutter's adapter puts English into events (`sessionErrorFallbackText`, "The model step failed without a message…", "Tool failed", "Tool execution interrupted", "Session instructions updated.") | A blank message stays `null`, and `StepError(message, type, status)`, `RunOutcome.Failed(type, message?)`, `ToolPart.settledBy` and `NoticeKind` carry what is known | Native rule: no copy outside `:ui:words`; plan 04 §7.6 says "blank message = missing → words from type". Server error text is still passed through |
| D5 | Flutter drops `command`, `skill`, `reference`, `project`, `worktree`, `lsp`, `mcp.*`, `vcs.branch.updated` and `filesystem.changed` | `RefreshHint(kind)` for each | Plan 04 §7.6 refresh row |
| D6 | Plan 04 §7.6: `session.compaction.*` → a compaction notice part | Nothing, as in Flutter; `TODO(adapter review)` in code | The plan gives no part shape; the chat section owns it. Flagged for the Opus adapter review |
| D7 | — | `GatewayEvent.RunIdle` for `session.idle` / status idle | §11.2 has no variant, and §7.6 says "idle ignored on stable"; the event is kept so the consumer decides |
| D8 | Dart `int` is 64-bit everywhere | `Long` for times, tokens, counts, `seq` and limits; `Int` for ordinal, attempt, HTTP status and exit. An `Int` field out of range reads as absent | Kotlin API shape. Equal on every fixture |
| D9 | — | Fixture tests in `desktopTest` (JVM file IO); pure tests in `commonTest` | `commonTest` has no file IO; desktop test source sets are allowed by K10 |
| D10 | Plan 15: "the 24 Flutter fixtures" | 24 plus the two coverage sample sets | They cover 30+ more event and message shapes |
| D11 | — | `Oc2UsageStatistics` is strict, unlike the other DTOs | `session_stats.json` is one of the 24; Flutter's parser for it is strict (lib/domain) |
| D12 | — | Malformed edge cases differ from Dart only where no fixture reaches: non-object non-null `tokens` on a step or assistant message gives `null` (Dart: zero tokens), and Dart keeps integers beyond `Long` | Noted for the mapper owner; not observable on real servers |

## 6. Catalog and coordinator requests

- **No library requests.** kotlinx-serialization-json is already on every KMP module.
- **`MODULE_OWNERS` row** (coordinator-only file): `:protocol:oc2	F	backend-owner (S1-OC2)	slice-1`. The same
  test also needs rows for the merged `:tools:arb2xml` and `:tools:flutter-migration-readers`.
- **Konsist scope:** `KonsistRulesTest.production` scans `core, engine, ui, feature, androidApp, desktopApp`, not
  `protocol`. The K11 rule (no `java.*` in commonMain) and similar rules do not see `:protocol:*`. This module complies
  (checked by hand); ask the coordinator to add the layer.
- **S0-CORE:** when `json/` helpers, `GatewayEvent`, `MessageInfo`, `Part` and `ToolState` land, replace D1 and D2 (the
  adapter's mapping rules stay as they are).

## 7. Open problems

1. **Live proof (plan 15 finish line, part 2) is open.** It needs S1-NET's `:core:net`, then `Oc2Transport`,
   `Oc2Dialect`, `Oc2Routes`, `Oc2Client`, the probe and the gateways (plan 04 §7.1 order), plus VPS access with
   OpenCode 2.1.1.
2. **P-N4 has not run.** If 2.1.1/2.2.5 differ from these beta-18600 fixtures, plan 04 says re-record before WP-04.5.
   `messages_2_1_1.json` already shows a 2.1.1 message type `idle` that both Dart and Kotlin read as Unknown; the
   mapper owner should decide whether it becomes a quiet notice.
3. **The adapter needs its review** (plan 15: Opus reviews it), especially D4–D7, and the open question whether
   `TranscriptStale` should also follow a compaction end.
4. **iOS** compile of this module is untested (VPS nightly job).
5. **`flutter analyze`** was not run here. The generator uses only `package:opencode_mobile/...` imports and passed
   `dart analyze` and `dart format` in isolation; the full Flutter gate should still run once on the PC.
6. **Container-only Gradle mirror** (`~/.gradle/init.d/central-mirror.gradle.kts`) is needed in this sandbox, as P1
   found. Nothing in the repo depends on it.
