# Unit S1-ENG: `:engine:settings`, `:engine:diagnostics`, `:engine:profiles`

Date: 2026-11-01. Branch: `native/eng-2026-11-01`, created from `claude/native-prefs-diagnostics-5ksi5d` at
`6b9a2b21` (= `native/m0-2026-11-01` at that time). Write set: `native/engine/{settings,diagnostics,profiles}/**` (three
new modules) and this report. Nothing outside it was committed.

Inputs read: `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*` (S1-DATA and S1-ANALYTICS in full),
`native/contracts/s1.md`, plan 01 (binding), plan 15 (row S1-ENG and the scope dictionary), 03 §10.3, §10.5, §11.2,
§13.5, 05 §3–§4 and Appendix A, 06 §5.2 and §11.1, 07 §5 I (settings scope engine) and A02, 11 §7.3 and the
exit/crash contracts, 16 §1.4, §1.10, §5, SI-5, SI-22, K-SEC-11/12, 18.

**Result.** The S1-ENG finish line is met on this container, in common (JVM) tests. These are green:

- the preferences facade;
- three-state consent reads;
- the teaching registry;
- the diagnostics ring with crash categories and the exit classifier, including `MemoryLimiter:AnonSwap`;
- the profile store with trust, pins and the backwards deletion transaction over every retained copy (Astra M1).

Diagnostics reports crash, freeze and error **categories** through its own `DiagnosticsSignals` port. It does not
call `:engine:analytics` directly: the module rules forbid that edge today (see §5.1).

Shipping state: implemented and verified locally (desktop JVM tests; `compileAndroidMain` for all three). Committed
and pushed to the branch. Not merged, no PR, no CI run, not wired into an app graph, not run on a device.

---

## 1. What was built

Package root `io.github.eslamasabry.oc.engine`. `explicitApi()` is on (engine layer). About 12,600 lines including
tests. Module edges: settings → `:core:common`, `:core:data`; diagnostics → `:core:common`, `:core:security`;
profiles → `:core:common`, `:core:model`, `:core:data`, `:core:security`. No engine-to-engine edge.

### 1.1 `:engine:settings`

| Package | What it does |
|---|---|
| `prefs` | `Preferences(store, lane, scope, consent)`: one device-preferences facade over `KeyValueStore`. It exposes `value: StateFlow<DevicePreferences>` plus `appearance`/`motion`/`saver`/`locale`/`revision`/`privacyEpoch`, and `set(PreferenceChange): Outcome<DevicePreferences>`. Each change is ONE commit on the app lane (multi-key changes are all-or-nothing). Listeners only see acknowledged values; a refused or throwing write restores the cache. Legacy keys keep their defaults, and the two old celebration switches fold into Motion. `oc.glass` is read as the frost strength (CR06-5). `RetiredPreferences.drop` removes retired keys and nothing else. `DevicePreferenceKeys.SHAPES` lists the key shapes. |
| `scope` | The plan 07 §5 I types (`SettingsTarget`, `EffectiveSetting`, `SettingsEditToken` with an internal constructor, `SettingsAccess`), plus `SettingValue`, `SettingsScopeCatalog` (all **69** doors of the freeze tip, generated from the Dart file), and `LocalSettingsAccess` (read/watch/beginEdit/write; tokens are issuer-bound, forged or replayed tokens fail, a newer revision gives Conflict, owner replacement invalidates tokens). Ports: `SettingsOwners` (implemented by profiles at wiring time) and `ReadStateConsentPort`. `ChatAgentChoices` holds per-chat agent overrides, published only after acknowledgement; it has `forgetSession`, `closeProfile` and `forgetProfile`. |
| `defaults` | `InteractionDefaults` (pure: one-option pickers, default project and model, send defaults to Queue, review scope) and `DefaultNotices` (per-profile once-only notices and last project: one owner per store and profile, absent profile refused, secret-bearing values refused, deleted with the profile). |
| `consent` | The shared shape `ConsentRead { Unknown, Granted, Refused }`: only `Granted` authorises, and a missing or corrupt record reads `Unknown`. `ConsentStore` (`read`, `observe`, `revision`, `closeProfile`, `forgetProfile`). Three stores built on it: `MobileDownloadConsentStore` (strict 50 MB threshold on an explicit mobile route; unknown, VPN, offline or failed probes never run; claims once under concurrency; queued revocation; corrupt storage never authorises; `NetworkRouteProbe` seam), `InFlowConsentStore` (battery exemption and maker auto-start asked once per server; the "needs you" preset), and `ReadStateConsentStore` (private by default; one-cohort legacy migration; never optimistically true; a failed opt-out stays private; a `SharingTicket` lets a receipt check it is still allowed; the journal is pruned on deletion). Each store also has `reopenProfile` for a failed deletion. `ConsentKeys.shapes` lists the key shapes. |
| `teaching` | `TeachingRegistry` (tip and tour definitions with ids, copy **keys**, anchors and triggers; `problems()` checks unique ids and 2–4 steps; `defaults` holds the 5 Flutter entries). `TipController` (`oc.tips` read unchanged; at most 2 showings, one at a time, 2 min cooldown, one new tip per screen visit, never while a question waits or the keyboard covers the anchor; a replay counts nothing; reset clears all). `NudgeRegistry` + `ConversationNudgeWatcher` (the Flutter conversation nudges and their triggers). |

### 1.2 `:engine:diagnostics`

| Package | What it does |
|---|---|
| root | `DiagnosticsSignals` (the analytics port: `crash(CrashCategory, fatal)`, `freeze(FreezeKind, durationMs, foreground)`, `error(Failure)`, `None`). Its enum names match `:engine:analytics`' `CrashCategory` and `FreezeKind` exactly. `Diagnostics`: a 20-entry ring that redacts secrets, URLs and long token-like values (keeping long code names), coalesces bursts within 10 s and is thread-safe and non-suspending. `failureShown(failure)` records only the failure's kind and sends `error(failure)`; it never reads `Redacted` text. |
| `crash` | `CrashStore` (file seam returning `Outcome`), `CrashRecordCategory`/`CrashSource`, `CrashDiagnostics` (consent-gated: opt-in file, whitelisted class names, fixed category messages), `CrashStartup` (never blocks launch: it gives up after a 300 ms budget and readiness resolves later; late replies are handled; a reset drops old replies and cancels replays; restored records replay once; clearing before import prevents restoring), `NativeCrashRecord` (bounded, fails closed, no paths or sign-in data), and `CrashLoopGuard` (3 crashes in 10 s → safe mode, plan 07 A02). |
| `exit` | `ExitClassifier`: every `ApplicationExitInfo` reason maps to one `ExitKind`; force stop is recognised; a normal exit or an app update is silent; `REASON_OTHER` whose description contains `MemoryLimiter:AnonSwap` becomes `MemoryLimit` (plan 11 §7.3). `ExitRecord` takes the description as a constructor argument only and keeps just that flag. `ExitHistory`/`PolicyExitHistory` over a `LifecycleSource`: invalid limits make no request; supported-empty, unsupported and absent are distinct; typed errors; newest first, capped; an unreadable record is never an empty success. `LaunchIntake`: once per process, each crash is signalled once (`CrashSignalLedger`), ANR exits become `freeze(NotResponding, …)`, and crash exits become `crash(category, fatal = true)`. `KeepAliveAdvice`: per-maker steps as wording ids. |
| `perf` | `PerfTrace`: a 2,000-entry ring; spans nest; a closed parent never absorbs later work; errors pass through and mark the span; `markOnce`; fixed log line shape; nearest-rank stats. `TraceLabels` turns paths into templates without ids or query values and gives the password script a generic label; secret-named attributes are redacted. |

### 1.3 `:engine:profiles`

| File | What it does |
|---|---|
| `StoredProfile.kt` | `StoredProfile`: the persisted `oc.profiles` row with every Flutter field. `orchestration` is `OpaqueJson`, kept byte-for-byte. `StoredProfileJson` is the lenient codec: written rows use the v57 key order first, then every unknown key in its stored order (03 §10.3 "merge over the raw stored object"). |
| `ProfileTrust.kt` | `CertificatePinRecord(origin, leafSha256)` (`oc.certificatePin.<id>`), `CleartextOkRecord(origin)` (`oc.cleartextOk.<id>`), `ProfileTrustRecord.forOrigin`, `ProfileOrigins`. |
| `ProfileStore.kt` | `profiles: StateFlow<ImmutableList<LoadedProfile>>`, `activeId`, `trust(id)`, `load`, `upsert(profile, SecretEdit, TrustInput)`, `setActive`, `secret`, `setTailscaleGuidance`. An origin change drops the pin and the cleartext consent in the same commit. Metadata is kept when a password is unreadable (re-entry flags). A failed secure write leaves metadata and cache unchanged; a failed secure delete keeps the profile. Values are registered with the `Redactor`. |
| `SecureStorageProblem.kt`, `CodexInput.kt` | Linux keyring missing vs the generic device problem, as an enum (no copy). Codex origin, directory and token bounds are validated without echoing the input. |
| `ProfileKeys.kt`, `DeferredFeatureData.kt` | The profile key shapes and secret prefixes (`pw.`, `oc.codexToken.`, GitHub, BYO, quota), plus the deferred AI Team and FactsBook shapes (data kept and swept, features off: 02 §3.3/§3.7 FORCE rows). `register(ProfileScope)`. |
| `ProfileDeletion.kt` | The transaction, in plan 05 §3.5 order: (1) stop admission on the profile lane and every `ProfileCloser`, then drain; (2) compute retained keys; (3–5) embedded-record cleaners (queue, drafts, pending photo, widget, shortcuts, tile) and `ProfileResourceCleaner`s by order; (6) retained Flutter-era copies; (7) the generic `ProfileScope.sweep` minus retained keys; (9) row + active pointer + secrets as one unit, with secrets verified absent; (10) retained keys last. **Any failure keeps the server**: every failing owner is reported (`DeleteProfileResult.failures`), `oc.profiles` is restored if step 9 failed, and every lane and closer is reopened. Concurrent deletes are serialised. |
| `StructuralEdit.kt`, `FlutterEraSweep.kt` | `ProfileStructuralEdit`: one edit applied to the live prefs and to every retained copy (`RetainedCopy`: S0 backup, `replaced-*`, `start-fresh-*`, each `.bak` twin, `v2-sources/`; Astra M1). A copy that refuses keeps the server so the deletion can be retried. |
| `SignInReset.kt` | Removes only app-owned sign-ins and is idempotent. It never parses or erases unreadable metadata, strips secrets from cached profiles, and treats failed enumeration or partial deletion as failure with a safe retry. It also covers the BYO envelope (journal kept). `SignInResetParticipant` is the multibinding seam. |
| `Adapters.kt` | `profileCloser(...)` and `forgetProfileCleaner(...)` turn the settings stores' `closeProfile`/`forgetProfile`/`reopenProfile` into closers and cleaners without an engine edge. |

## 2. Catalog rows covered

All 60 rows of `native/catalog/scope.jsonl` with `slice: 1` and a module in the write set. Test names start with the
row id (AGENTS rule 11). Totals: **40 covered, 11 partly, 9 data-part** (deferred features whose data is kept and
swept).

| Row | Status | Tests / what is left |
|---|---|---|
| B-STATE-003 | covered | `PreferencesTest` |
| B-STATE-004 | covered | `PreferencesTest` (serialised; refused write; multi-key all-or-nothing) |
| B-STATE-005 | covered | `DisplayPreferenceRollbackTest` (write returns false; write throws) |
| B-STATE-081 | covered | `PreferencesTest`. The caller (profile load or migration L1) is wiring. |
| B-KIT-002 | covered | `PreferencesTest` |
| B-SEC-010 | partly | `PreferencesTest` ×2. The shared consent epoch is `privacyEpoch`; a consent change does not bump `revision` as Flutter's single counter did. |
| B-STATE-123 | covered | `SettingsAccessTest` |
| B-STATE-124 | partly | `SettingsAccessTest` ×3 against a fake that follows the port contract; not yet against the real `ReadStateConsentStore` through `asSettingsPort()`. |
| B-STATE-125 | covered | `SettingsAccessTest` ×5 |
| B-STATE-126 | covered | `SharedPreferencesAcrossGraphsTest` |
| B-STATE-127 | covered | `SettingsScopeCatalogTest` (69 doors, see §5.2) |
| B-STATE-128 | covered | `SettingsEditTokenTest` ×6 |
| B-STATE-129 | covered | `SessionAgentSettingTest` ×5 |
| B-STATE-021, B-STATE-022 | covered | `InteractionDefaultsTest` |
| B-STATE-023 | partly | `InteractionDefaultsTest`, `DefaultNoticesTest`. Not ported: the in-memory "said this run" for notices with no profile, and `modelDefaultOf` (needs the connection engine). |
| B-SEC-057 | covered | `DefaultNoticesTest` ×3 |
| B-STATE-030 | partly | `MobileDownloadConsentStoreTest` ×3. Where sizes come from (voice packs, setup) belongs to `:engine:media` and setup. |
| B-STATE-031 | covered | `MobileDownloadConsentStoreTest` ×5 |
| B-SEC-013 | covered (store half) | `MobileDownloadConsentStoreTest` ×5. The end-to-end run through the deletion transaction needs wiring (§6). |
| B-SEC-093 | covered | `InFlowConsentStoreTest` |
| B-SEC-142, B-SEC-143 | covered | `ReadStateConsentStoreTest` (6 + 3) |
| B-SEC-164 | partly | `ReadStateConsentStoreTest` ×6. Sending the receipt is `:engine:connection`'s; the ticket check it must call is tested. |
| B-ANDR-025, B-ANDR-026 | covered | `InFlowConsentStoreTest`. The maker → "has an auto-start step" mapping is platform side. |
| B-NAV-038 | covered | `NudgeRegistryTest`, `TipControllerTest` |
| B-NAV-039 | partly | `NudgeRegistryTest` ×11. "The run changed files" comes in as a fact; computing it needs the transcript engine. |
| B-STATE-142 | covered | `TipControllerTest` ×3 (exact Flutter JSON read and written) |
| B-L10N-020 | partly | `TipControllerTest`: unique ids, 2–4 steps, declared copy keys. The en/ar copy check needs `:ui:words` (§6). |
| B-SEC-008 | covered | `DiagnosticsTest` ×8 |
| B-SEC-117 | covered | `PerfTraceTest` ×4 |
| B-BUILD-025 | covered | `PerfTraceTest` ×8 |
| B-SEC-045 | covered | `CrashStartupTest` ×13, `CrashDiagnosticsTest` ×8, `CrashLoopGuardTest` ×2 |
| B-ANDR-036 | partly | `NativeCrashRecordTest` ×4, `LaunchIntakeTest` ×3. Handing the launch report to the persisted problem report needs `ReportStore` (03 §18.6, not built). |
| B-ANDR-003 | covered | `ExitHistoryTest` ×9 |
| B-ANDR-004, B-ANDR-005 | covered | `ExitClassifierTest` (plus `MemoryLimit`) |
| B-ANDR-006 | partly | `ExitHistoryTest` ×2. `keepAliveInfo`/`openKeepAliveSetting` are `KeepAliveSettings` in `:core:domain` (CR11-9). |
| B-ANDR-007 | covered (ids) | `KeepAliveAdviceTest` ×3. The words belong to `:ui:words`. |
| B-STATE-026 | partly | `ExitClassifierTest`: a crash never offers the keep-alive (phone setting) advice. The notice itself is `:engine:runtime`'s. |
| B-STATE-065 | partly | `ProfileStoreTest`. Variant, location and display/appearance prefs belong to `:engine:connection`/`:engine:settings` per Appendix A. |
| B-STATE-098 | covered | `ProfileStoreTest` |
| B-SEC-134 | covered (engine part) | `ProfileStoreTest`: the enum and Linux-only probe; the sentences go to `:ui:words`. |
| B-SEC-135, B-SEC-136, B-AGENT-244 | covered | `ProfileStoreTest` |
| B-STATE-063, B-STATE-013, B-STATE-061 | covered | `ProfileDeletionTest` |
| B-STATE-064 | covered | `SignInResetTest` ×9 |
| B-STATE-020, B-SEC-075, B-STATE-058, B-AGENT-193, B-AGENT-301, B-AGENT-302, B-STATE-145, B-SEC-176, B-AGENT-311 | data part | `ProfileDeletionTest`, `ProfileDeletionSweepTest`. The shapes are registered and swept for one profile only; closers drain before the sweep; the `<id>.agents` lane is fenced; tombstones are kept until the row. The features themselves are deferred (D6, 02 §3.3). |

Also exercised, though these rows are not in this unit's scope filter: B-SEC-121, B-SEC-154 (trust/pin), B-SEC-201
(`ProfileDeletionSweepTest`, cited from plan 16).

## 3. Commands and results

Environment, set up first:
- Android SDK command-line tools `commandlinetools-linux-16111833_latest.zip` from dl.google.com in
  `/opt/android-sdk/cmdline-tools/latest`, `ANDROID_HOME=/opt/android-sdk`, licences accepted.
- `sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"`.
- Gradle on JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`).
- `native/local.properties` (gitignored) holds `sdk.dir`.
- As in S1-DATA, Maven Central answers 429 through the proxy. A container-only `~/.gradle/init.d/central-mirror.gradle.kts`
  (not in the repo) adds Google Maven, Google's Central mirror and `gradlePluginPortal()`; leaving out the portal
  broke `kotlin-dsl` resolution for `build-logic`.

**Local compile shim (not committed).** `:core:data` does not compile at `6b9a2b21`: three call sites still use the
old `Failure.Storage(details)` constructor, while core now takes `Failure.Storage(StorageProblem, details)`. To build
my modules I patched those three lines locally (`WriteRefused`, `InvalidValue`, `KeystoreUnavailable`) and reverted
the patch before the final push. The integration session owns the real fix.

Gradle builds were serialised through `flock` (one build at a time). From `native/`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:data:compileKotlinDesktop :engine:analytics:compileKotlinDesktop` (no shim) | FAILED: `:core:data` has 3 errors (above); `:engine:analytics` has 11 errors, because `ErrorCategory.of` uses the old flat `Failure` leaves |
| 2 | `./gradlew :engine:settings:desktopTest --rerun :engine:diagnostics:desktopTest --rerun :engine:profiles:desktopTest --rerun` (final, with shim) | BUILD SUCCESSFUL: settings **115/115**, diagnostics **70/70**, profiles **49/49**; 0 skipped |
| 3 | `./gradlew :engine:settings:compileAndroidMain :engine:diagnostics:compileAndroidMain :engine:profiles:compileAndroidMain` | BUILD SUCCESSFUL, 0 warnings from the three modules |
| 4 | `./gradlew :quality:test --continue` | could not run: `:engine:connection` (gateway contract drift) and `:ui:kit-chat` (`MotionTokens`) do not compile, and neither is in my write set |
| 5 | Konsist rules K3, K4, K5, K11, K13, K16 and K-SEC-12 checked by text search over the three modules instead of #4 | one hit, fixed: a diagnostics test opted into `RedactorApi` (K13 scans test folders); it now builds `Redacted` through `Redactor().details(...)` |
| 6 | `git grep` for RFC 1918 / CGNAT addresses and email addresses over the diff | no match |

Not run: `build` (rule 12), Android host tests (none needed: no platform source sets), iOS (`oc.ios=false`), any
emulator or device test, `tool/format-changed.sh` (it does not exist yet; no formatter was run).

How it was built: four parallel workers, one per slice (settings prefs/scope/defaults, settings consent/teaching,
diagnostics, profiles), each limited to its own package folders, with the module build files and the cross-module
contract fixed first. I re-ran every module's tests myself before committing.

Commit note: the crash-store commit (`feat(native): crash categories…`) uses `exit.ExitRecord` from the next commit,
so that single commit does not compile by itself. The branch head does.

## 4. Resolved versions

No new library and no catalog change. Everything comes from the existing catalog through `oc.kmp.library`:
Kotlin 2.4.21, kotlinx-coroutines 1.11.0, kotlinx-collections-immutable 0.5.2, kotlinx-serialization 1.12.0, Metro
runtime 1.5.1 (on the classpath, unused), Turbine 1.2.1, AGP 9.3.3, compileSdk 37.1, Gradle 9.7.1.

## 5. Decisions and deviations

### 5.1 Analytics wiring (plan 18)

`ModuleRules.engineOrder` has no `"analytics"`, so `:engine:diagnostics → :engine:analytics` fails configuration.
`:engine:analytics` also does not compile at `6b9a2b21` (see §3). Diagnostics therefore declares `DiagnosticsSignals`
with the same enum names as analytics. Once A18-1's order lands, the bridge is this adapter (in `:engine:diagnostics`
or the app graph):

```kotlin
class AnalyticsDiagnosticsSignals(private val analytics: Analytics) : DiagnosticsSignals {
    override fun crash(category: CrashCategory, fatal: Boolean) =
        analytics.track(CrashSeen(AnalyticsCrashCategory.valueOf(category.name), fatal))
    override fun freeze(kind: FreezeKind, durationMs: Long, foreground: Boolean) =
        analytics.track(FreezeSeen(AnalyticsFreezeKind.valueOf(kind.name), DurationBucket.ofMillis(durationMs), foreground))
    override fun error(failure: Failure) = analytics.track(ErrorSeen(ErrorCategory.of(failure)))
}
```

Crash signals come from the launch report, not from the consent-gated local crash store, so `crash_seen` stays
BASIC with the category only (D18-1). Local crash details stay behind the off-by-default capture (B-SEC-042). An ANR
exit reports `freeze(NotResponding, 5 000 ms, …)`; the 5 s figure is a stand-in, because Android gives no duration.

### 5.2 Other deviations

1. **Catalog size.** The freeze tip's Dart settings catalog has 69 doors (it adds `settings-updates` and
   `settings-browser-mode`); B-STATE-127 and plan 07 still say 67. All 69 were copied.
2. **Settings failures.** `:core:common` has no settings failure, so the mapping is lossy:

   | Settings reason | Mapped to |
   |---|---|
   | Unsupported | `Unavailable(FeatureId("settings"))` |
   | InvalidValue | `Storage(InvalidValue)` |
   | StaleTarget | `Refused(StaleScope)` |
   | Conflict | `Net.Conflict(tag = "settings-revision")` |
   | StorageFailed | `Storage(WriteRefused)` |
   | Disposed | `Unexpected` |
3. **Write atomicity.** Every preference change, consent save (read-state: default + origin + value) and deletion
   sweep is one commit. Flutter wrote keys one by one, so its "half written after a failed rollback" states cannot
   happen. The sweep at step 7 is all-or-nothing: a refused key keeps every scoped key.
4. **Consent stores.** One instance serves every profile, instead of Flutter's per-profile owner objects. Stale
   owners are fenced by a closed set plus an `oc.profiles` check, so a stale writer cannot bring back a deleted
   profile's key (in-flow consent adds this check; Flutter did not have it).
5. **Tips.** Plan 03's `shouldShow` + `recordShown` became one atomic `TipController.offer`, because one-tip-at-a-time
   needs check and record together. Tip and nudge persistence is fire-and-forget in an injected scope.
6. **Profiles.**
   - `ProfileTrust` is not a `StateFlow` per id: `trust(id)` returns a snapshot, and the trust is also published on
     `profiles`.
   - Upsert takes `SecretEdit { Keep | Set | Clear }` because the engine holds no secret copy.
   - `KeyShape` in `:core:data` has no `retained` field and `ProfileScope` has no cleaner registry, so `RetainedKeys`
     and `ProfileResourceCleaner` live in `:engine:profiles`.
   - `oc.agentHostSecret.<owner>` is left to `:engine:agents`, because the owner can be shared between profiles.
7. **Key ownership.** Plan 05 Appendix A lists some keys under other modules:
   - `oc.mobileDownloadConsent.<id>` under `:engine:media`;
   - `oc.shareSessionViews*` and its journal under `:engine:connection`;
   - `oc.sessionAgents.<id>` under `:engine:connection`.

   This unit builds them in `:engine:settings`, as the S1-ENG brief and the plan 15 scope rows say. The ledger's owner
   column needs updating.
8. **Exit history.** Plan 11 ports the policy; here it runs over a platform-neutral `ExitRecord`, and the OS
   description never leaves the constructor. When every record of the main process is malformed, the result is
   `Unavailable`, never an empty success.

## 6. Catalog requests and requests to other owners

No library requests.

- **Coordinator, `native/MODULE_OWNERS`:** add `:engine:settings`, `:engine:diagnostics` and `:engine:profiles`
  (slice-1). `ModuleOwnersTest` will be red until then.
- **Coordinator, build-logic `ModuleRules.engineOrder`:** add `"analytics"` after `"settings"` (plan 18 A18-1), then
  add `implementation(project(":engine:analytics"))` to `:engine:diagnostics` and the §5.1 adapter.
- **Coordinator / app graph wiring:**
  - one `Preferences`, built after `PrefsKeyValueStore.warm()`;
  - register `DevicePreferenceKeys.SHAPES`, `ChatAgentChoices.SHAPE`, `DefaultNotices.SHAPES`, `ConsentKeys.shapes`,
    `ProfileKeys.register`, `DeferredFeatureData.register` with `ProfileScope`;
  - bind `SecretFilter` to `Redactor`;
  - call `RetiredPreferences.drop` (profile load or migration L1);
  - plug `ReadStateConsentStore.asSettingsPort()` into `LocalSettingsAccess`;
  - implement `SettingsOwners` from `ProfileStore` (fingerprint changes on replacement, address, backend, user name or
    folder; no credentials; null when gone);
  - adapt every settings store into the deletion through `profileCloser`/`forgetProfileCleaner`;
  - a prefs-backed `CrashSignalLedger`;
  - `PerfTrace` with an `AppLog` sink.

  Once that wiring exists, B-SEC-013, B-SEC-093 and B-SEC-143 can be run end to end through `ProfileDeletion`.
- **`:engine:settings` follow-up:** `ChatAgentChoices` and `DefaultNotices` have `closeProfile`/`forgetProfile` but no
  `reopenProfile`, so after a failed deletion their lanes stay closed until restart. The consent stores have it.
- **Core owner (`:core:common` / `:core:data`):**
  - fix the three `Failure.Storage` call sites in `:core:data`;
  - add a settings failure case (or `RefusalReason.Conflict`);
  - expose registered secret prefixes on `ProfileScope`;
  - consider plan 05 §3.3's `registerCleaner`/`retained`;
  - `DartJson` is imported from `:core:data` and moves when it moves.
- **`:engine:analytics` owner:** `ErrorCategory.of` must follow the S0-CORE `Failure` tree (`Failure.Net.*`); it does
  not compile today.
- **`:ui:words` / `:quality` (B-L10N-020, B-SEC-134, B-ANDR-007):**
  - en + ar copy for every `TeachingRegistry.defaults` copy key (`tipStopAfterStepTitle`, `tipStopAfterStep`,
    `tipSendLaterTitle`, `tipSendLater`, `tipHoldMicTitle`, `tipHoldMic`, `tourMakeItYoursTitle`,
    `tourMakeItYoursOffer`, `tourAppearanceStart`, `tourNotNow`, `tourMakeItYoursColours`, `tourMakeItYoursStyle`,
    `tourMakeItYoursBackground`, `tourMakeItYoursComfort`, `whatsNewTipsTitle`, `whatsNewRowDetail`);
  - sentences for `SecureStorageProblem`;
  - sentences for the keep-alive wording ids.
- **`:android:platform` / `:android:migration`:**
  - `NetworkRouteProbe` (VPN or unnamed route reads Unknown);
  - `LifecycleSource` and `CrashStore` over the copied `AppExitHistoryPolicy` and `NativeCrashStore`;
  - the maker → auto-start mapping;
  - the file-backed `RetainedCopy` (XML parse, `.tmp`, fsync, rename).
- **Other engines:**
  - `:engine:transcript`: "the run changed files" for nudges;
  - `:engine:connection`: the read-receipt ticket check and the session-agent hook;
  - `:engine:delivery` / `:engine:composer`: the live queue, drafts and stash cleaners, plus the `confirmedQueue` check;
  - `:engine:agents`: the phone-agent owner `RetainedKeys` and its `oc.agentHostSecret` cleaner.
- **Plans/catalog:** B-STATE-127 and plan 07 say 67 doors (the tip has 69); plan 05 still marks `oc.glass` I
  (CR06-5 says A); the owner columns in §5.2 item 7.

## 7. Open problems

1. Everything was verified against a locally patched `:core:data`. The branch needs the integration session's core
   fix before it builds on m0.
2. `:quality:test` (Konsist, graph smoke, `ModuleOwnersTest`) has not run over these modules. Only the text checks
   in §3 row 5 have.
3. A failed opt-out of read-state sharing stays private only in memory. After a restart, the disk value (still on) is
   read again, as in Flutter.
4. After a refused write, mobile-download and in-flow consent stay unavailable until `reopenProfile` or a restart (as
   in Flutter).
5. `ChatAgentChoices` reads straight from the store, so between draining a pending write and removing the key that
   write is briefly visible.
6. Crash-loop guard: a launch that is killed without a crash before 10 s leaves the streak unchanged, and safe mode
   resets it to 0. The plan 07 A02 owner should confirm both.
7. `StoredProfileJson.plainServerName` ports Dart's display-name fallback and returns English text ("This phone",
   "Computer at <address>") from an engine, against the no-copy rule. It should return a kind plus the address and
   let `:ui:words` word it.
8. Test fixtures use only loopback, reserved documentation addresses (`203.0.113.x`) and `*.example` hosts. The
   private and CGNAT addresses the first draft used were replaced before push.
9. Not built (out of scope or later units): `ReportStore`/`ProblemReportBuilder`, the updater (S4-UPDATER),
   `LocaleHost`, `FirstRun`, `SearchMatcher` (slice 4), `KitTip`/Help › Tips (S2-TEACH, S4), and the HTTP perf
   interceptor.
