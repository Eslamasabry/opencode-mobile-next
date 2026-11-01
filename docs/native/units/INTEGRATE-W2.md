# INTEGRATE-W2 — the late units on the flat core contract

Date: 2026-11-01. Branch: `native/integrate-w2-2026-11-01`, created from `claude/native-integrate-w2-jy88u2` at
`7db496b2` (the M0 tip after the S1-KITA1, S1-ENG, S3-COMPE, S3-RTENG, S0-GATES and S1-KITA2 merges). Write set:
anything under `native/` needed to make the tree compile and pass, preferring consumers over core; this report.

**Result:** the merged tree compiles and every module test task passes (§3). The units that started before
INTEGRATE-W1 landed now use W1's flat `Failure`, `:core:common`'s `DartJson`, `:core:model`'s `NetworkStatus`, and
`Failure.Runtime` instead of their stand-ins. **Nothing under `native/core`, `native/protocol/oc2` or
`native/engine/connection` was edited** (REVIEW-FIX-W1 owns them today), nor build-logic or the version catalog.

Shipping state: implemented, verified locally (commands in §3), committed and pushed to the branch. No PR, no CI run,
no signing, no release. `master` and tags untouched.

---

## 1. What changed, per unit

| Unit / module | Change |
|---|---|
| S1-ENG `:engine:settings` | `SettingsFailureReason.asFailure()`/`Failure.settingsReason()` round-trip through the flat list: Unsupported → `Unavailable(settings)`, **InvalidValue → `Rejected(status = null, tag = SETTINGS_INVALID_VALUE)`** (new `WireTag`, D1), StaleTarget → `Refused(StaleScope)`, Conflict → `Conflict(tag)`, StorageFailed → `StorageRefused` (and `SecretUnavailable` maps back to StorageFailed), Disposed → `Unexpected`, Offline → `Network(Offline)`, NeedsSignIn → `SignIn(TokenMissing)`, Managed → `Refused(NotOnThisPhone)`, **OutcomeUnknown → `Refused(DispatchUncertain)`** (W1 D2). Consent, defaults and preferences: every `Storage(...)` → `StorageRefused` (W1 D3 for `InvalidValue`). `DartJson` imported from `:core:common`. |
| S1-ENG `:engine:profiles` | `Storage(KeystoreUnavailable)` → `SecretUnavailable`, the rest → `StorageRefused`. `SecureStorageProblem.of` now keys on `SecretUnavailable` only (it was "any Storage"; only the Keystore kind ever reached it). `DartJson` import. |
| S1-ENG `:engine:diagnostics` | `failureLabel` names the flat subtype plus its enum detail (`Network Reset`, `SignIn PasswordRejected`, `Busy Starting`, `Media …`, `Runtime …`, `HTTP 500`); the `Net.` prefix is gone. `CrashStore` oversize reads answer **`UnreadableReply`** (the flat list's "over-cap body", D2) instead of `Storage(InvalidValue)`; other read problems `StorageRefused`. |
| S3-COMPE `:engine:composer` | Drafts, stash and vault refusals → `StorageRefused` (the five `StorageProblem` kinds collapse; D3). `vault/Sha256` delegates to `DartJson.sha256Hex` (its `TODO(move-to-core)` is done; ~60 duplicated lines gone, the SHA-256 vector tests still run against it). |
| S3-RTENG `:engine:runtime` | The `RuntimeCall<T>` / local `RuntimeProblem` stand-ins (its D1) are gone: ports answer `Outcome<T>`, refusals are `Failure.Runtime(problem, details)` with `:core:common`'s `RuntimeProblem` (same values, same order). `host/RuntimeRefusal.kt` keeps `RuntimeProblem.refused()` and `Outcome.Failed.runtimeProblem`; `PhoneServerStarter` reads the problem through it. |
| S1-KITA1/2 `:ui:kit` | `KitErrorKind.of` and `StateViewRules.isNetwork` were ported to the flat list by the merge but **lost two network cases**: timeouts (old `Unreachable(Timeout)`, now `TimedOut`) and an unknown delivery (old `DeliveryUnknown`, now `Refused(DispatchUncertain)`). Both are back (B-KIT-024's "timeouts … are network"); `KitErrorKind` also counts the transport refusals that were the old `TrustRefused` (`CleartextNotConfirmed`, `OriginChanged`, `CredentialsInUrl`, `PinNeedsHttps`). Tests and scenes use `Network(Offline)`, `TimedOut`, `Certificate(Untrusted)`, `Server(500)`, `Unavailable`, `StorageRefused`. |
| S2-KCB `:ui:kit-chat` | One comment quoted rule 6 as "`Dispatchers.*`", which Konsist K4's text regex flags; reworded. |
| S1-KITA1 → `:ui:shell`, `:quality` tests | `KitTopBar` now draws Back as an icon button with a content description, so `onNodeWithText("Back")` found nothing (B-KIT-054 ×3 and `DesktopShellUiTest`). They press `onNodeWithContentDescription("Back")`. |
| S1-PLAT0 `:android:platform` | `NetworkStatus`/`UnknownNetworkStatus` imported from `:core:model` (W1 D8). `NetworkState` gained `onWifi` before `metered`, so positional calls now name `metered =`/`defaultNetworkId =` (main and `NetworkTrackerTest`). |
| S2-ENTRY → `:android:platform` | `implementation(project(":engine:entry"))` (S2-ENTRY's own request; `ModuleRules` allow android → engine), so `EntryIntentReader.kt` compiles. |
| `MODULE_OWNERS` | Rows the units asked for: `:engine:settings`, `:engine:profiles` (core-owner, S1-ENG), `:engine:diagnostics` (diagnostics-owner), `:android:platform` (F), `:engine:entry` (coordinator), `:engine:composer` (chat-lead), `:engine:runtime` (runtime-owner). `ModuleOwnersTest` is green. |

S0-GATES (`native/tool/**`, shell scripts) needed no change: it holds no Kotlin contract types.

## 2. Catalog rows covered

No new rows; every change keeps the row's meaning on the new shape. Row tests whose bodies changed:

- `:ui:kit` — **B-KIT-024** (classifier: Offline, TimedOut, Certificate, DispatchUncertain and ComputerDidNotAnswer are
  network; Server(500), Unavailable, ConsentMissing and Unexpected are other — one case added), **B-KIT-034**
  (error ways).
- `:ui:shell` — **B-KIT-054** (three back-gesture/frame tests: Back by description).
- `:engine:diagnostics` — **B-SEC-008** (label `Network Reset`, details never in the report), **B-ANDR-003** (exit
  history read errors: `StorageRefused`), **B-SEC-045** crash rows through `FakeCrashStore` (oversize =
  `UnreadableReply`).
- `:engine:settings` — **B-STATE-004/005** (refused saves restore), **B-STATE-023** (defaults), **B-STATE-031**
  (download consent), **B-SEC-013/142/143/164** (consent stores); the scope tests (`SettingsEditTokenTest` checks every
  `SettingsFailureReason` round-trips through `asFailure()`/`settingsReason()`).
- `:engine:profiles` — **B-SEC-135** (a Codex token that cannot be stored is `SecretUnavailable`), **B-SEC-136**,
  **B-STATE-063/098**, **B-STATE-064** (sign-in reset refusals are the old storage family: `StorageRefused` or
  `SecretUnavailable`).
- `:engine:composer` — **B-STATE-100/101/102/136**, **B-CHAT-088**.
- `:engine:runtime` — the S3-RTENG rows run unchanged through `FakeLinuxHost` (now `Outcome`).
- `:android:platform` — **B-ANDR-037** (`NetworkTrackerTest`, named fields).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (JDK 17 for the toolchain via foojay),
`ANDROID_HOME=/opt/android-sdk` (`commandlinetools-linux-16111833_latest.zip`; `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`). Container: 4 vCPUs, 15 GB; `~/.gradle/gradle.properties` `-Xmx6g`,
4 workers. Container-only, not committed: `~/.gradle/init.d/central-mirror.gradle.kts` (Google's Central mirror first,
plugin portal kept, `robolectric.dependency.repo.url` on every `Test` task), as INTEGRATE-W1 used.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew compileKotlinDesktop --continue` (after the Failure/runtime/kit edits, before the import fix) | FAILED: `:engine:profiles`, `:engine:settings` — `Unresolved reference 'dart'` (`DartJson` moved to `:core:common`) |
| 2 | same, after the import fix | BUILD SUCCESSFUL |
| 3 | `./gradlew desktopTest :quality:test --continue` | FAILED: 7 in `:engine:profiles` (storage family assertions), 3 in `:ui:shell` + 1 in `:quality` (Back text), Konsist K4 (kit-chat comment) → fixed |
| 4 | `./gradlew :engine:profiles:desktopTest :ui:shell:desktopTest :quality:test --continue` | BUILD SUCCESSFUL |
| 5 | gate (#7 command) on the first committed tree | FAILED only in `:android:platform:compileDebugKotlin` (`NetworkStatus`, `NetworkState` positions, `:engine:entry`) |
| 6 | `./gradlew :android:platform:testDebugUnitTest` | BUILD SUCCESSFUL, 23 tests, 0 failures |
| 7 | **Final, on the committed tree:** `./gradlew --no-configuration-cache -Pkotlin.incremental=false :androidApp:assemblePreviewDebug compileKotlinDesktop :desktopApp:compileKotlin allTests :quality:test :android:linux:testDebugUnitTest :android:platform:testDebugUnitTest :tools:strings:test :tools:flutter-migration-readers:test --continue` | First run: BUILD SUCCESSFUL (mostly up-to-date from #5). **Then forced: same command with `--no-build-cache --rerun-tasks`: BUILD SUCCESSFUL in 4 min 22 s, 539 of 539 tasks executed.** Preview APK `androidApp-preview-debug.apk`, 18.1 MB. **1 889 tests, 0 failures, 0 skipped** (table below). |
| 8 | `./gradlew -p build-logic test` | `--rerun-tasks`: BUILD SUCCESSFUL, 20 tests, 0 failures |

Per-module test counts of #7 (from the JUnit XML):

| Module | Task | Tests | Failures | Skipped |
|---|---|---|---|---|
| `:android:linux` | testDebugUnitTest | 491 | 0 | 0 |
| `:android:platform` | testDebugUnitTest | 23 | 0 | 0 |
| `:core:common` | desktopTest | 18 | 0 | 0 |
| `:core:common` | testAndroidHostTest | 18 | 0 | 0 |
| `:core:data` | desktopTest | 43 | 0 | 0 |
| `:core:data` | testAndroidHostTest | 37 | 0 | 0 |
| `:core:domain` | desktopTest | 12 | 0 | 0 |
| `:core:model` | desktopTest | 4 | 0 | 0 |
| `:core:net` | desktopTest | 41 | 0 | 0 |
| `:core:net` | testAndroidHostTest | 35 | 0 | 0 |
| `:core:security` | desktopTest | 20 | 0 | 0 |
| `:engine:analytics` | desktopTest | 49 | 0 | 0 |
| `:engine:composer` | desktopTest | 102 | 0 | 0 |
| `:engine:connection` | desktopTest | 3 | 0 | 0 |
| `:engine:diagnostics` | desktopTest | 70 | 0 | 0 |
| `:engine:entry` | desktopTest | 56 | 0 | 0 |
| `:engine:profiles` | desktopTest | 49 | 0 | 0 |
| `:engine:runtime` | desktopTest | 86 | 0 | 0 |
| `:engine:settings` | desktopTest | 115 | 0 | 0 |
| `:engine:transcript` | desktopTest | 19 | 0 | 0 |
| `:feature:chats` | desktopTest | 1 | 0 | 0 |
| `:protocol:oc2` | desktopTest | 51 | 0 | 0 |
| `:quality` | test | 31 | 0 | 0 |
| `:tools:flutter-migration-readers` | test | 84 | 0 | 0 |
| `:tools:strings` | test | 60 | 0 | 0 |
| `:ui:design` | desktopTest | 30 | 0 | 0 |
| `:ui:kit-chat` | desktopTest | 67 | 0 | 0 |
| `:ui:kit-chat` | testAndroidHostTest | 24 | 0 | 0 |
| `:ui:kit` | desktopTest | 83 | 0 | 0 |
| `:ui:kit` | testAndroidHostTest | 126 | 0 | 0 |
| `:ui:routes` | desktopTest | 1 | 0 | 0 |
| `:ui:shell` | desktopTest | 22 | 0 | 0 |
| `:ui:shell` | testAndroidHostTest | 14 | 0 | 0 |
| `:ui:words` | desktopTest | 4 | 0 | 0 |
| **total** | | **1889** | **0** | 0 |

## 4. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | A settings `InvalidValue` is `Failure.Rejected(status = null, tag = SETTINGS_INVALID_VALUE)`. | The flat list has no "invalid value" leaf, and W1 D3's `StorageRefused` would make it indistinguishable from `StorageFailed`; the settings contract (plan 07) needs both reasons to round-trip (`SettingsEditTokenTest`). `Rejected` is "the value was turned down"; its status is already nullable. |
| D2 | A crash-store file over its cap is `UnreadableReply`. | `CrashDiagnostics` must tell "too large, delete it" from "storage broken" (it deletes the records file in the first case); `UnreadableReply` is documented as "undecodable or over-cap body". |
| D3 | All five old `StorageProblem` kinds → `StorageRefused`, except `KeystoreUnavailable` → `SecretUnavailable`. | Plan 03 §10.1 / plan 05 (W1 D1, D3). Tests that asserted `WriteRefused` vs `Full` vs `InvalidValue` now assert `StorageRefused`; where a test meant "the storage family" it accepts either storage subtype. |
| D4 | The kit classifiers include `TimedOut` and `Refused(DispatchUncertain)` (and `KitErrorKind` the four transport refusals). | They were network in the frozen pre-W1 code and in B-KIT-024; the merge dropped them. |
| D5 | `RuntimeProblem` is `:core:common`'s; `:engine:runtime` keeps only two helpers. | S3-RTENG's own D1 ("swap to `Outcome` mechanically once CR11-1 lands"); W1 added `Failure.Runtime`. |
| D6 | Test fixes press Back by content description, not text. | The kit part (S1-KITA1) is right per its contract; the tests are the consumers. |

## 5. Deviations

- `MODULE_OWNERS` (a coordinator file) was edited, as W1 did, because the brief's "every module test task passes"
  includes `ModuleOwnersTest`; each row is the one its unit report asked for. Owner names for settings/profiles
  (`core-owner (S1-ENG)`) are my choice; plan 15 names only the builder (Codex). The coordinator may rename them.
- `:ui:kit` main code (`KitNotice.kt`, `KitStateView.kt`) and one `:ui:kit-chat` comment were edited although they
  are shared modules (rule 14): both were integration regressions of the merge, not new design.

## 6. Catalog requests

None. No library or catalog alias was added.

## 7. Requests to other owners

- **Core owner / `:ui:words`:** the flat list loses distinctions the Flutter copy had: "draft attachment limit" vs
  "storage full" vs "commit refused" (S3-COMPE §6) all read `StorageRefused`. If the words need them, the contract
  needs a detail field (e.g. `StorageRefused(why)`), like `Network(why)`.
- **S2-MODEL:** `MessageErrorKind.fromName/refineFromText` in `:core:model`; `:engine:composer`'s
  `PhraseMomentRules.fromName` still carries its `TODO(move-to-core)`.
- **S1-PLAT0 / S2-PLATFORM:** `networkReading` leaves the new `NetworkState.onWifi` null; plan 04 §6.4 wants it set
  from the default network's Wi-Fi transport (VPN over Wi-Fi = true). The tests would need the new field too.
- **Diagnostics owner:** `:android:platform` still declares its own `CrashStore`/`CrashRecord`/`CrashKind`
  (`TODO(move-to-core)` in `NativeCrashStore.kt`); `:engine:diagnostics` now exists with a different, text-based
  `CrashStore`. Reconciling the two shapes is a design decision, not an import fix.
- **S3-COMPE:** the `ComposerPrefs` → `:core:data` `KeyValueStore` adapter (its build file note) can now be written;
  `:core:data` compiles.
- **S3-RTENG:** passwords are still `String` (its D3); `:core:security`'s `SecretText` is available now.

## 8. Open problems

1. The storage-detail loss above (§7, first item).
2. Live-server and emulator checks were not run (no OpenCode 2 server or emulator here).
3. iOS targets were not compiled (`-Poc.ios=true` not run in this unit).
