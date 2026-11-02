# Unit P12-MIGRATION: the in-place update over the Flutter app

Date: 2026-11-02/03 (parity push). Branch pushed: `claude/native-parity-push-3deucr` (the session's designated
branch; the brief named `native/p12-migration-2026-11-03`, which this session may not push to). Base:
`native/m1-2026-11-03` at `8a4eee69`.

**Finish line:** a native build with the Flutter app's package, installed over the Flutter app, shows "Updating your
app…" on first launch, adopts the Flutter data in place (servers, passwords/tokens, drafts, queue, schedules,
settings, widget choices, chat caches stay readable; files stay where they are), and opens the app; a failure stops
safely on a screen with visible ways out (Try again, Start fresh that keeps a copy, Open storage settings, Report a
problem) and never loses data.
**Non-goal:** R2 "Restore data from before the update" (plan 05 §9.4 RestoreTransaction with outbound hold), the
v2/v3 Room import and cleanup, and the features that own each adopted key (widgets, schedules, Linux, voice).

## Shipping state (kept apart, AGENTS rule 8)

| State | Status |
|---|---|
| Implemented | Yes: runner + engine + gate + app wiring + Start fresh + same-package flavour |
| Enabled | On the real package only (`switch` flavour). The `.native` preview never migrates (gate open from the start) |
| Verified | **Partly.** JVM tests only (below). **No Gradle build ran in this session**: the network policy blocks `dl.google.com`, so neither the Android SDK nor the Android Gradle Plugin (Google Maven redirects there) could be fetched, and Gradle cannot even configure the project. Compose and Android source (the gate screen, `MigrationGateHost`, `MigrationWiring.kt`, `MainActivity`/`PreviewStores` edits, the Android adapters of `:android:migration`) were **not compiled**. Every API they call was checked against the source by hand |
| Committed / pushed | Yes, small `[skip ci]` commits on the branch above |
| Deployed / released | No. No APK built, signed or installed; no device or emulator run |

**First thing the integrator must do:** `./gradlew :engine:migration:desktopTest :feature:gate:desktopTest
:android:migration:testQaUnitTest :androidApp:assemblePreviewDebug :androidApp:assembleSwitchDebug :quality:test`.

## 1. Merges this unit owned

| Branch | What was taken | What was not, and why |
|---|---|---|
| `origin/native/migrrun-2026-11-01` (S2-MIGRRUN) | All of `native/android/migration/**` (runner, journal, S0 backup, M1–M6, ledger, readers, `qa` StepInterrupter, 59 tests) and its report | — (merged cleanly; compiles against today's `:core:common`/`:core:security`) |
| `origin/native/gateui-2026-11-01` (S2-GATEUI) | Gate input/presenter/UI/screen and their tests, **moved** from `:ui:shell` to `:feature:gate`; `strings_migration.xml` (en/ar) in `:ui:words` | `GateLocale` expect/actual and the Robolectric goldens + locale host test: feature modules may have no platform or host-test source sets (K10, `withHostTest` only for platform modules). They stay in history at the merge commit. `:ui:shell/build.gradle.kts` is back to its base |
| `origin/native/proof-p5-2026-11-01` (P5 proof) | `docs/native/slice0/DEVICE-PROOFS-2026-11-01.md` + device evidence; the same-package **`switch`** flavour in `:androidApp` | `P5ProofProvider` (a proof harness that ran at every process start) and `:android:surfaces` (placeholder widget/tile stubs that would duplicate the real frozen-FQCN classes in `:android:widgets`). The emulator host-alias address in the report was replaced by words |

## 2. What now works (implemented; device-unverified)

1. **First launch over Flutter** (real package): `MigrationWiring` builds the S2-MIGRRUN runner and the
   `MigrationEngine` at graph creation. The engine publishes `:core:data`'s `GateState` (Closed → Running(step) →
   Open) for every store: `PreviewStores` now takes the injected `MigrationGate` and **waits for it before warming the
   prefs file**, and `GatedSecretStore` already waits. `MainActivity` wraps `App(...)` in `MigrationRoot` → the update
   gate: "Opening…", "Updating your app…", after 8 s "Still updating" + "Try moving your data again".
2. **What is adopted:** the prefs file `FlutterSharedPreferences.xml` *is* the native store (same name, `flutter.`
   prefix, encodings), so servers (`oc.profiles`, `oc.activeProfile`), per-profile drafts, queue, schedules,
   settings, consents, chat caches (`oc.sessionInventory`, `oc.chatTail`, …) and widget choices are read in place;
   M3 applies the four legacy transforms. M2 decrypts flutter_secure_storage 11.x with the package's Keystore alias
   and re-stores each secret in the native store under the **same names** the native `ProfileStore` reads
   (`pw.<id>`, `oc.codexToken.<id>`, …), read back and compared. Secrets it cannot read are flagged
   (`oc.secretReentry.<id>`), never invented. Files (`app_flutter`, Linux rootfs, voice models, projects) are only
   inventoried and stay where they are.
3. **Re-enter sheet once:** names each server ("Home · Enter password"; "A saved server" when the name is unreadable);
   a row opens the Servers page; "Sign in to these later" or closing acknowledges it for good.
4. **Deferred notice once:** "AI Team and Memory aren't in this version yet…" when the census found that data.
5. **Safe failure:** every block (LowSpace with the needed MB, PrefsUnreadable, WriteRefused, AuthorityChanged,
   StepCrashed, BackupInvalid) shows a plain title + "Nothing was deleted…" + rows + `Details · M2 · StepCrashed`
   (step and class only). A runner that throws becomes `Blocked(StepCrashed)` with Try again, never a crash loop or an
   endless wait. Rows that would do nothing are hidden (Restore until R2 exists).
6. **Try again** resumes the runner (single-flight on both sides).
7. **Start fresh (keeps a copy)** — new in this unit, plan 05 §9.4 two-phase: phase 1 journals `pendingSwap` and
   restarts the app; phase 2 (next start, in `MigrationWiring`'s init, before any prefs file or the secret store
   opens) moves `FlutterSharedPreferences.xml` and `oc_secrets_v1.xml` with their `.bak` twins into
   `files/migration/replaced-<at>/`, then completes v1 as source `startFresh`. Nothing deleted; S0 backup and the
   Flutter secure-storage file stay. Offered only while blocked and before the gate ever opened.
8. **Report a problem** shares "OpenCode Mobile update · M3 · WriteRefused" through the system share sheet (no path,
   value or address). **Open storage settings** opens the system storage page.
9. **Preview package** (`.native`): `MigrationRunnerPort.Nothing`; the gate is open before the first read, so the
   existing preview behaves as before.

**How to reach it:** it is the app's first screen on a same-package install (no navigation); on later launches the
journal says v1 is complete and the gate opens at once.

## 3. Building the same-package APK

The `switch` flavour has no `applicationIdSuffix`: package `io.github.eslamasabry.opencode_mobile`, versionCode from
`native/version.properties` (10003 today; must stay above the installed Flutter build's code, 57 for 2.1.0+57),
launcher name "OpenCode Mobile".

```bash
cd native
export JAVA_HOME=<JDK 21>; export ANDROID_HOME=<sdk with platforms;android-37.1, build-tools;37.0.0>
./gradlew :androidApp:assembleSwitchRelease
# → androidApp/build/outputs/apk/switch/release/androidApp-switch-release-unsigned.apk (no signing config in the repo)
apksigner sign --ks <the key the installed Flutter build was signed with> \
  --out opencode-native-switch.apk androidApp/build/outputs/apk/switch/release/androidApp-switch-release-unsigned.apk
apksigner verify --print-certs opencode-native-switch.apk   # SHA-256 must equal the installed app's certificate
adb install -r opencode-native-switch.apk                   # never -d, never uninstall (that wipes the data)
```

- Signer: the owner's phone runs builds signed `1DE5BF08…D60C` (local release key) — the switch APK must be signed
  with that same key, or Android refuses the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Public CI sideload builds
  (`842284B2…`) can only be updated by a build signed with the CI key. Ask for the installed certificate and compare
  before delivery (root AGENTS.md). Never substitute or rotate a signer.
- `assembleSwitchDebug` signs with the build machine's debug key; it updates only a Flutter build signed with that same
  debug key (the P5 lab setup).
- Not authorized by this unit: signing, delivery, or installing on the owner's phone.

## 4. Catalog rows

| Row | Status | Tests |
|---|---|---|
| B-MIGR-004 | runner + engine level (fresh install is a clean first run; nothing-to-adopt build is open before any read). The install itself is device work (J1) | `MigrationEngineTest`, `MigrationRunnerTest` |
| B-MIGR-005 | runner level (undeclared keys byte-identical; gate never closes again once open; stores wait for the gate) | `MigrationRunnerTest`, `MigrationEngineTest` |
| B-MIGR-006 | runner level (all secret kinds, re-store and verify) + gate (re-enter rows name servers, ids only, asked once) | `SecretsMigrationTest`, `MigrationEngineTest`, `GateBindingTest`, `GatePresenterTest` |
| B-MIGR-009 | part (deferred notice once; files inventoried, not moved) | `MigrationEngineTest`, `MigrationRunnerTest` |
| B-MIGR-010 | covered at JVM level (kill at every step/S0 point, three crashes → StepCrashed, Try again single-flight, LowSpace, BackupInvalid, unreadable journal, throwing runner → safe block, Start fresh two-phase with kill at every point) | `MigrationRunnerTest`, `StartFreshTest`, `MigrationEngineTest`, `GateBindingTest`, `GatePresenterTest` |
| B-MIGR-011 | covered at JVM level (no secret/path in journal, audit, events, gate state, report text) | `MigrationAuditPrivacyTest`, `MigrationEngineTest`, `GateBindingTest` |
| B-MIGR-001, 003; B-STATE-068, 081, 088; B-SEC-063, 142 | runner level (S2-MIGRRUN) | `LegacyTransformsTest`, `SecretsMigrationTest` |
| B-MIGR-002, 007, 008, 013, B-LINUX-032 | part (runner keeps keys/files; the owning features must read them) | as S2-MIGRRUN |
| **Missing** | B-MIGR-012 (Shorebird end / update notice); J1–J8 device journeys; widgets, tiles, shortcuts and schedules re-armed after the update (B-MIGR-013) need `:android:widgets` etc. in the app | — |

## 5. Tests and results (this container)

Gradle could not run (see Shipping state). Fallback, recorded honestly: the Kotlin 2.4.21 compiler from the official
GitHub release and jars from Google's Maven Central mirror compiled the pure-Kotlin sources as JVM
(`:core:common` common+desktop, `:core:model`, `:core:security`, `MigrationGate.kt`, `:engine:migration`, the
non-Compose files of `:feature:gate`, and the non-Android part of `:android:migration`) with the module's opt-ins,
then JUnit ran the tests:

| Suite | Result |
|---|---|
| `MigrationEngineTest` (new) | 12/12 |
| `GateBindingTest` (new) | 6/6 |
| `GatePresenterTest` (S2-GATEUI, after the K1 rewrite) | 10/10 |
| `:android:migration` JVM tests (`MigrationRunnerTest`, `SecretsMigrationTest`, `LegacyTransformsTest`, `MigrationAuditPrivacyTest`, `RestoreAuthorityCheckTest`) + new `StartFreshTest` | 60/60 |
| Red evidence | engine tests first failed 9/11 on a harness error (background work and `advanceUntilIdle`), fixed to `runCurrent`; `StartFreshTest` first failed 4/5 on a wrong crash-point name; mutants (leave the `.bak` twin behind, keep `blocked`) → 2 `StartFreshTest` failures |
| Not run | `GateScreenDesktopTest` (Compose), the 2 Robolectric and 2 `qa` tests of `:android:migration`, `:quality:test` (K1/K2/K10/K-SEC), Metro graph validation, APK assembly, lint |

## 6. Deviations and decisions

1. **Write set:** besides `engine/migration/**` and `feature/gate/**`: the merged `android/migration/**` (owned
   merge; it is also where the Android runner must live: engines may not have `androidMain`, K10), new
   `ui/words/.../strings_migration.xml` (en/ar), `MODULE_OWNERS` (3 rows, ModuleOwnersTest), `androidApp/build.gradle.kts`
   (3 module lines + the P5 `switch` flavour), `androidApp/src/switch/res/values/strings.xml` (launcher name), the
   unit's `graph/MigrationWiring.kt`, and two small existing-file edits: `PreviewStores` (inject the gate, wait for it,
   drop the preview-only package check) and `MainActivity` (wrap `App` in `MigrationRoot`).
2. **K1 kit-only:** the gate screen used `AnimatedContent`/`fadeIn` and `StringResource`; it now uses `KitSwap`
   (keyed by screen kind) and resolved strings. The 8 s clock moved to `:engine:migration` (`slowAfter`) so the
   presenter imports no `delay`/`Job`.
3. **`GateReentry.targetName` is nullable** ("A saved server" fallback, new string en/ar).
4. **Restore (R2) is not offered**: rows that would do nothing are hidden; the runner's `restoreAvailable` is masked.
5. **Prefs opened before S0:** `AndroidDataStores.flutterPrefs` calls `getSharedPreferences` when `PreviewStores` is
   built, before the runner's S0 byte backup (plan 05 §7.1 wants it after). Android then applies its own `.bak` rule
   first, so S0 backs up the recovered file Android would load anyway. Fixing it needs a lazy backend in `:core:data`.
6. **Start fresh phase 2 runs on the main thread** (graph creation in `Application.onCreate`): a few renames + fsync.
7. **Signals** (`app_updated`, `migration_done`, the M5 line) go to the redacted app log; `:engine:analytics` is not
   in the app graph yet.
8. **Re-entry opens the Servers page**: there is no "edit password" yet; the person removes and re-adds the server.

## 7. For the integrator

1. Run the commands at the top; fix any compile slip in the uncompiled Android/Compose files.
2. Add `:android:widgets` (and tiles/notifications) to the app before a same-package build ships, or the Flutter home
   widgets and tiles vanish on update (B-MIGR-013).
3. Route engine secret writes through `MigrationRunner.secretChanges()` (the ledgered writer, plan 05 §4.4 rule 1)
   once R1 rollback matters; today engines write through `GatedSecretStore` directly.
4. Bind `MigrationSignals` to `:engine:analytics`/`:engine:diagnostics` when they join the graph.
5. R2 RestoreTransaction (plan 05 §9.4) and the safe-mode Start fresh after first open remain S6 work.
6. Device journeys J1 (install over Flutter 2.1.0+57 on an emulator with the local key), J2 kill points (`qa`
   build type: `files/native/qa-kill-after`), J8 low space.
