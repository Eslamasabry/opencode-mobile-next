# 11. Android platform native: built-in Linux, services, notifications, widgets, schedules and OS surfaces

Written 2026-11-01. Binding parent: `01-architecture-decision.md` (the ADR): §5 module graph, §6 dependency rules, §7
multiplatform rules, §8 versions, §9.5 threading and Android lifetime, §9.7 startup and migration, §9.9 identity and
release, §10.2 upgrade journey, §11 proofs, §12 D2, D6, D7, D12, D16, D18. Siblings this section leans on and does not
repeat: `03-architecture-modules.md` (module anatomy, §2.3 Android library recipe, §12 binding table, §13.6 OS shells),
`05-data-migration.md` (stores, M1–M6, first-launch hooks H1–H3, Appendix A/D keys), `07-screens-navigation.md` (§7
`EntryRouter`, every intent shape, the frozen entry contract), `10-media-voice.md` (voice, read aloud, PDF, camera, Wi-Fi
share, clipboard image). Where this file adds a choice the ADR did not make it is listed in §0.2; where it needs a
change in another section it is listed in §0.3. When this file and the ADR disagree, the ADR wins and this file is
corrected.

Tags: **[V]** checked today against a primary source (Google Maven metadata, a downloaded AAR's classes, the repo).
**[R]** read in the repo, a map, a research file, a sibling section or owner memory. **[S]** secondary source.
**[U]** unverified; the named proof settles it (§23). Ids: map features `ANDROID-PLATFORM-NN`, `BUILTIN-LINUX-NN`;
catalog rows `B-ANDR-NNN`, `B-LINUX-NNN`, …; device flows `E2E-NN`; this section's ids `S11-n` (choices), `CR11-n`
(change requests), `D11-n` (owner decisions), `U11-n` (unknowns), `W11-n` (work units), `A11-n` (acceptance checks).

## For the owner, in five lines

1. Almost everything that makes the app "Android" (the phone's own Linux, the background services, notifications,
   widgets, tiles, schedules, restart after an update) is already written in Kotlin today, so it moves over nearly
   as-is; what disappears is the glue that talked to Flutter and the two hidden Flutter engines behind widget taps and
   scheduled runs.
2. Your phone server keeps coming back after a reboot or an app update exactly as now: same files, same restart rules,
   same "never restart what you stopped", and it is proven on the emulator by installing the new app over a real old
   one before your phone gets it.
3. Widgets stay the same two (Conversations 4×2, Status 2×2), drawn the same way that works on your ZTE launcher; a
   widget redesign and Glance come after the switch.
4. A reply that is still running when you leave the app keeps going in a short background service, so "reply
   finished" reaches you without leaving "Stay connected" on all day; notifications speak all seven app languages.
5. iOS and Linux desktop get the same rules through one set of interfaces; things only Android can do (built-in
   Linux, widgets, tiles) simply report "not available" there instead of becoming broken buttons.

Decision needed: no. The 13 owner questions in §25 have defaults and work proceeds on them.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read | Then |
|---|---|---|
| Coordinator | §0, §1, §21, §22, §25 | create the W11 units; route the CR11 change requests |
| Runtime owner (Opus for the split, Codex for the policy port) | §3, §4, §5, §6, §7, §8, §21 W11-01..07 | port tests first (§22.1), then move code |
| Background/notifications builder | §2, §5.4 (service seams), §6.5, §9, §10, §14.5, §16 | never edit `:android:linux` or `:android:widgets`; the runtime owner deletes the moved service bodies from `:android:linux` in the same merge (S11-12) |
| Widgets builder | §2, §11, §16 | test on the owner's launcher rules (§11.4), not only a Pixel emulator |
| Schedules builder | §12, §16 | the Dart and Kotlin suites are the spec (map risk R9) |
| Platform bridges builder | §13–§18 | the entry routing itself is 07 §7 |
| Reviewer | §0.2, §2.4, §24 | reviewers never launch test processes (ADR §10.3) |

### 0.2 Choices this section adds to the ADR

| # | Choice | Why (what it removes) |
|---|---|---|
| S11-1 | **Two-step move for the runtime.** Slice 1 copies every built-in Linux Kotlin file (runtime, setup, idle, agent host, terminal PTY, phone-engine seam) into **one** module `:android:linux`, unchanged, and its 30 JVM tests and 11 instrumented acceptance classes must pass with zero edits. Slice 3 splits it (§5.3) and only then carves out `:android:agenthost` (slice 3) and `:android:terminal` (slice 5). | The map's top risk is behaviour drift in ownership and drain code. A green copy first removes "did the move or the refactor break it" from every later bug. |
| S11-2 | **One lock family survives the split.** Every class split out of `BuiltinLinux` receives the same two monitors (`RuntimeLocks.instance` = today's `this`, `RuntimeLocks.recovery` = today's `recoveryLock`). No split class creates a new lock. | Today's correctness rests on `@Synchronized` + `recoveryLock` ordering. New locks would invent new races. |
| S11-3 | **Shell scripts become checked-in `.sh.tmpl` files** in `:engine:runtime` (`src/commonMain/scripts/`), turned into Kotlin string constants by a build-logic generator, with a SHA-256 list test and the dash+bash script tests. Values go in through `@@NAME@@` placeholders filled by one `ShellQuote.single()`; a missing or unknown placeholder throws. | ~6,000 lines of shell embedded in Dart strings with `$` escapes cannot be reviewed or linted. Placeholders + one quoting function remove ad-hoc string building. |
| S11-4 | **Reply watch** (ADR §9.5) is a third owner of the existing status row and the existing `BackgroundConnectionService`, not a new service class. It starts in `MainActivity.onStop` only when a run is active, an alert would be posted and live mode is off; it stops 10 s after the last run settles. | Keeps the component freeze, the one-row rule (map T11) and the pause receipts; removes the need to leave "Stay connected" on for finished-reply alerts. |
| S11-5 | **Restore receivers promise the foreground service before they wait for the migration gate**, then wait inside the service (≤ 60 s). | Waiting first would lose the `MY_PACKAGE_REPLACED`/`BOOT_COMPLETED` background-start exemption on the first native launch. |
| S11-6 | **Preview flavour gets its own loopback ports**: phone server 4197, phone engine 4198, agent host 4199, AI Team 8572 (stable keeps 4097/4098/4099/8472). Ports come from `AppBuildInfo.ports`. | Loopback ports are shared by every app on the phone; the `.native` preview's server could not bind 4097 while the Flutter app's server runs. |
| S11-7 | Pause tile declares `android.service.quicksettings.TOGGLEABLE_TILE`; both tiles keep **non-active** mode at the switch and are poked with `TileService.requestListeningState` on change. | TalkBack reads the Pause tile as a switch; non-active mode keeps today's behaviour, so a missed poke cannot leave a stale tile. |
| S11-8 | All Android-surface copy is read through the `:ui:words` suspend `Words` resolver with the stored app language (`oc.appLocale`) per render; XML-read strings come from the generated `strings_os` resources (ADR §7.5), which are registered on `:androidApp` **and** `:android:widgets` (the widget layouts reference `@string/`). | One string table for app and native surfaces (fixes map T16: only en/ar natively today) without moving 28 `@string/` references out of the widget layouts. |
| S11-9 | Notification dismissal is respected: the status row carries a `setDeleteIntent`; a dismissed row is not promoted again for the same run. | Google's Live Update rule ("don't repost Live Updates that the user dismissed") [R android-surfaces §6]. No visible change otherwise. |
| S11-10 | `LocalPdfService` (isolated process) builds its body directly, never through the app graph; `OcApplication.onCreate` returns at once in every process other than the main one. | An isolated process cannot read app data or prefs; a graph build there would crash or leak. |
| S11-11 | One orchestrator for "the OS forgot us" events: `SystemEventRestorer` in `:engine:entry`, called by the restore receivers, the first-launch hook H2 (05 §7.9) and a start after a force stop. | Today the work is spread over two receivers and app start, and widgets/shortcuts are never redrawn on boot (android-surfaces §2.4). |
| S11-12 | **The phone-server and setup service bodies end in `:android:background`** (ADR §5.2 lists the phone-server FGS host there) and reach the runtime only through two `:engine:runtime` interfaces, `PhoneServerForeground` (runtime → service) and `PhoneServerServiceHooks` (service → runtime) (§5.4). Until W11-09 (slice 4) they stay in `:android:linux` with a copy of today's status-row code, which is the interim 03 §13.2 shows. | The status row's `Notification` is built in `:android:background`; without the two interfaces `:android:linux` would need an edge to `:android:background`. |

### 0.3 Change requests to other sections (the coordinator routes them; work proceeds on these defaults)

| # | To | Request | Default if refused |
|---|---|---|---|
| CR11-0 | ADR §6.1 (coordinator) | Allow exactly two `:android:*` → `:android:*` edges: `:android:agenthost → :android:linux` and `:android:terminal → :android:linux` (they launch through the proot launcher and register in its process inventory). `:android:linux` never depends back; it exposes `GuestLauncher` and `GuestProcessRegistry` (§5.4) that the other two call. | Keep agent host and terminal as packages inside `:android:linux` (two modules fewer). |
| CR11-1 | 03 §10.1 `Failure` (before the slice-0 freeze) | Add `Failure.Runtime(val problem: RuntimeProblem, details)` with the enum in §5.5, so the built-in Linux keeps its typed vocabulary (map T10) through one exhaustive `:ui:words` mapping. | Use `Failure.Refused` with new `RefusalReason` values (more values, less clarity). |
| CR11-2 | 03 §10.1 `RefusalReason` | Add `BackgroundLimit` (Android refused a foreground start or ended one: dataSync budget, background-start rule), `NotificationsBlocked`, `PhoneLocked`. | `Failure.Device` with a fixed category in `details`. |
| CR11-3 | 03 §10.4 `ForegroundKind` | Add `SignIn` (agent sign-in may run during a durable pause; live mode may not; map T10). | Sign-in borrows `LiveMode` and the pause rule moves into the caller (worse). |
| CR11-4 | 03 §10.4 `AppPermission` | Add `AllFilesAccess` (`MANAGE_EXTERNAL_STORAGE` on API 30+, legacy storage on 26–29; asked only when a shared-storage project opens). | A separate `StorageAccess` interface in `:core:domain`. |
| CR11-5 | 03 §10.4 `DeviceFacts` | Add `thermalHeadroom: StateFlow<Float?>` (polled at most every 30 s, only while collected), `battery: StateFlow<BatteryFact>` (`level: Int?`, `charging: Boolean?`) and `appVisible: StateFlow<Boolean>`. | Each engine polls the platform itself (duplicate listeners). |
| CR11-6 | 04 / 03 §13.4 (`:engine:connection`) | `ConnectionRegistry.oneShot(profileId, purpose: OneShotPurpose): Outcome<OneShotGateway>`: a gateway with no event stream, built from the saved profile, secret, consent and pin, closed after use; `purpose ∈ {WidgetAction, WidgetStatus, ScheduledRun, NotificationAction, AssistantCall}`. Refuses transient transports, unconfirmed cleartext and password re-entry exactly like the Flutter widget runner. | Each runner builds its own transport (three copies of the trust rules). |
| CR11-7 | 03 §12.1 | Split `LaunchSurfaces` into `WidgetSurface` (`:android:widgets`), `ShortcutSurface` and `TileSurface` (`:android:background`); `LaunchSurfaces` becomes a plain common class in `:engine:attention` composing the three. | One interface implemented across two modules (needs a third module to compose them). |
| CR11-8 | 06 §13.2 (l10n owner) | Register the `generateOsStrings` output on `:android:widgets` too (S11-8). | Move the 28 `@string/` references in widget layouts into binder code; preview layouts keep English only. |
| CR11-9 | 03 §10.4 | Add three small interfaces to `:core:domain`: `TailscaleApp` (§14.6), `KeepAliveSettings` (§16), `OsSettingsPages` (opens a named system page; §16). | Features call `:android:platform` (forbidden by the dependency rules). |
| CR11-10 | 03 §13.2 | The accessor comments `phoneServerServiceBody // from :android:linux` and `restoreReceiverBody` become "`:android:background` from slice 4" (S11-12). | 03 stays as the interim description. |

### 0.4 What this section does not decide

The entry routing (07 §7), the stored formats and the migration steps (05), the screens that show these features
(07: This phone, Keep running, Linux manager, Notifications settings, Schedules, widget panels), media capture and
playback (10), release signing and the APK pipeline (the release section). It gives each of them an Android home.

---

## 1. Scope: every map feature, where it lives, when it lands

Slice numbers are the ADR §5.2 slices: 0 skeleton and proofs, 1 foundations, 2 chat daily path, 3 phone runtime and
agents, 4 surfaces, settings and schedules, 5 project, media and integrations, 6 migration hardening and switch.
"Later" = after the switch by an ADR decision.

### 1.1 android-platform map (65 features)

| Map features | What | Engine (common) | Android module | Slice |
|---|---|---|---|---|
| 01 | process bootstrap, crash-safe start | `:engine:diagnostics` | `:androidApp` (`OcApplication`), `:android:platform` (`NativeCrashStore`) | 0–1 |
| 02, 03, 16 | live background mode, pause receipts, sign-in foreground claim | `:engine:attention` (`LiveModeController`, `PausePolicy`) | `:android:background` | 4 (sign-in claim 3) |
| 04 | Quick Settings tiles | `:engine:attention` (tile snapshot) | `:android:background` | 4 |
| 05–10, 15 | status row, alerts, action routing, native copy, permission hooks, team alerts, heat notice | `:engine:attention` (`StatusModel`, `AlertCopy`, `AlertButtons`, `NotificationPolicy`, `NotificationText`) | `:android:background` | 4 (team kinds later, D6) |
| 11–13 | phone-server, setup and idle-return notifications | `:engine:runtime` | `:android:linux` (bodies) | 3 |
| 14 | voice-model download notification | `:engine:media` | `:android:media` (10) | 5 |
| 17–28 | widgets: registry, Conversations, retired, Status, Usage, snapshot, style, sizing, overlay, actions, follow-ups, lifecycle | `:engine:attention` (`WidgetSnapshotBuilder`, `WidgetActionRunner`, `WidgetStatusRunner`, policies) | `:android:widgets`; panels in `:feature:widget-panels` | 4 |
| 29–31 | static shortcuts, pinned-session shortcuts, launch actions | `:engine:entry` (`LaunchAction`), `:engine:attention` (pins) | `:android:platform` (`EntryIntentReader`), `:android:background` (`ShortcutSurface`) | 2 (static), 4 (pinned) |
| 32, 33 | deep links, share in and out | `:engine:entry` | `:android:platform` | 2 |
| 34 | Tailscale handoff | `:core:security` (address rules) | `:android:platform` | 2 |
| 35–41 | scheduled prompts | `:engine:schedule` | `:android:schedule` | 4 |
| 42 | network monitor | `:core:domain` `DeviceFacts` | `:android:platform` | 1 |
| 43 | thermal monitor (+ battery, power save) | `DeviceFacts` + `:engine:runtime` (`HeatTierPolicy`) | `:android:platform` | 3 |
| 44 | lifecycle, exit history, keep-running guidance | `:engine:diagnostics` (`ExitClassifier`), `:engine:runtime` (`AppExitRecovery`) | `:android:platform` | 3 |
| 45 | crash capture | `:engine:diagnostics` | `:android:platform` + `:androidApp` | 0–1 |
| 46 | shared storage access, phone folders | `:engine:project` | `:android:platform` | 5 |
| 47 | camera, microphone, device facts | `:engine:media` | `:android:media` / `:android:platform` (10) | 5 |
| 48 | network trust posture | `:core:security`, `:core:net` (04) | `:androidApp` manifest | 1 |
| 49, 50 | local PDF, read aloud | `:engine:media` | `:android:media` (10) | 5 |
| 51 | project export, Manage space | `:engine:project`, `:engine:diagnostics` | `:android:platform`; `ManageSpaceActivity` shell | 5 |
| 52 | BYO-host SSH signer | `:engine:integrations` | `:android:platform` | 5 |
| 53 | Termux bridge | `:engine:runtime` (migrate-out only, D7) | `:android:termux` | 5 |
| 54 | built-in Linux seam | `:engine:runtime` | `:android:linux` | 1 (move), 3 (wire) |
| 55 | Wi-Fi file share | `:engine:media` | `:android:media` (10) | 5 |
| 56 | platform capabilities | `:core:domain` `PlatformCapabilities` | `:androidApp` (`@Provides`) | 0 |
| 57 | resources, build, packaging | — | `:androidApp`, build-logic | 0 |
| 58 | pure logic to port | the engines above | — | with each feature |
| 59 | AppFunctions | `:engine:entry` (`AssistantActions`) | `:android:platform` (`appfunctions` package) | **later** (02 S02-3, D16) |
| 60 | in-app APK update | `:engine:diagnostics` (update state) | `:android:platform` (`PackageInstallerUpdater`) | 4 (ships in the switch release, ADR §9.9) |
| 61 | Gemma assistant bridge | — | — | **later** (02 §3.5) |
| 62 | clipboard image paste | `:engine:composer` | `:android:platform` (10 owns the policy) | 3 |
| 63 | process budget, child-process card, agent priority, notice gate, battery | `:engine:runtime` | `:android:linux`, `:android:platform` | 3 |
| 64 | open a phone file, Wi-Fi share links | `:engine:media` | `:android:platform` (FileProvider), `:android:media` | 5 |
| 65 | F-Droid switches, Google-free QR | build-logic, `:android:media` (zxing-cpp) | — | 5 (F-Droid submission later) |

### 1.2 builtin-linux map (64 features)

| Map features | What | Engine | Android module | Slice |
|---|---|---|---|---|
| 01–07 | proot runtime, Ubuntu install, guest preparation, project storage, shared-storage binds, remove, `run` | `:engine:runtime` (rules, scripts) | `:android:linux` | 1 (move), 3 |
| 08–14 | installer slot, ownership, stop trees, orphan reaper, process list, phantom budget, services, performance | `:engine:runtime` (`PhoneProcessWatch`, `ProcessBudget`) | `:android:linux` | 1, 3 |
| 15–28 | server start, profile, script, stop rules, gated launch, crash recovery, consumer, restore, reclaim, stop, idle, healing, scheduled lease, exit recovery | `:engine:runtime` | `:android:linux` | 3 |
| 29–32 | FGS, setup FGS, work leases, reply wake | `:engine:runtime` | `:android:linux` | 3 |
| 33–39 | setup engine, runner, components, preflight, journals, removal, voice hook | `:engine:runtime` (engine, registry, preflight) | `:android:linux` (runner) | 3 |
| 40–44 | Linux manager, storage, packages, reset, This phone | `:engine:runtime` | `:android:linux` (via `GuestRunner`) | 3–5 |
| 45–48 | services supervisor, services pages, vitals, backup (freeze-tip, switch scope 02 §3.5) | `:engine:runtime` | `:android:linux` | 5 |
| 49 | designed, not built (S2b native vitals, S5 reboot restart of services, S6 catalog, S7 full copy) | — | — | later |
| 50 | local terminal | `:engine:project` (`TerminalHost`) | `:android:terminal` | 5 |
| 51 | thermal guard | `:engine:runtime` (`ThermalPolicy`, services heat hold) | `:android:platform` | 3 (AI Team part later) |
| 52–54 | private agent processes, agent host, sign-in | `:engine:agents` | `:android:agenthost` | 3 |
| 55, 56 | AI Team supervisor, phone engine tiers | `:engine:team` | `:android:team` (only the protection consumer at the switch, D11-1) | later |
| 57–62 | rootfs folders, git on the phone, export, quota collector, FactsBook host, OC1→2 history copy | `:engine:project`, `:engine:integrations`, `:engine:runtime` | `:android:linux` | 3–5 (FactsBook later) |
| 63 | move from Termux | `:engine:runtime` | `:android:termux` | 5 |
| 64 | Termux as a host | — | — | not ported (NG7) |

---

## 2. Module map for Android-native code

### 2.1 Modules (ADR §5.2, made concrete for this area)

| Module | Plugin | Owner (ADR) | Holds | Depends on (ADR §6.1 + CR11-0) | Starts |
|---|---|---|---|---|---|
| `:android:linux` | `oc.android.library` | S runtime | proot launcher, installer slot, ownership proofs, recovery, restore logic, idle, work leases, service registry, process inventory + orphan reaper, setup runner, scripts host, `/proc` code, `jniLibs`, `AndroidRuntimeHost`, `AndroidPhoneServerServiceHooks` (until slice 4 also the phone-server and setup service bodies, S11-12) | `:core:*`, `:engine:runtime`, `:ui:words` | 1 |
| `:android:agenthost` | `oc.android.library` | S runtime | `PhoneAgent*`, `PaseoStaleLock`, `NativeAgentHostAdmission`, agent process launch, sign-in pipes, auth probe; `AndroidAgentHost` | `:core:*`, `:engine:agents`, `:android:linux` (CR11-0) | 3 |
| `:android:terminal` | `oc.android.library` | F | `LocalTerminal` session manager, `PtyAccess.java`, Termux `terminal-emulator`; `AndroidTerminalHost` | `:core:*`, `:engine:project`, `:android:linux` (CR11-0) | 5 |
| `:android:background` | `oc.android.library` | F | channel registry, status row, alerts, receivers' bodies, live mode, reply watch, sign-in claim, pause receipts, tiles, pinned shortcuts, the phone-server and setup FGS hosts and the restore receiver body (from slice 4, S11-12), `AndroidForegroundWork`, `AndroidNotificationSurface`, `AndroidPhoneServerForeground`, `ShortcutSurface`, `TileSurface` | `:core:*`, `:engine:attention`, `:engine:settings`, `:ui:words`, `:ui:design` | 4 |
| `:android:widgets` | `oc.android.library` | F | providers' bodies, binders, layouts, drawables, preview PNGs, size policy, widget actions (claim ledger, lease), follow-up worker body, overlay session; `AndroidWidgetSurface`, `WidgetOverlayPort` impl | `:core:*`, `:engine:attention`, `:ui:words`, `:ui:design` | 4 |
| `:android:schedule` | `oc.android.library` | F | `AndroidScheduleHost` (AlarmManager + WorkManager), receiver and worker bodies, run notifications | `:core:*`, `:engine:schedule`, `:ui:words` | 4 |
| `:android:platform` | `oc.android.library` | F | `EntryIntentReader`, `AndroidPermissionGate`, `AndroidActivityResults`, `AndroidDeviceFacts` (network, thermal, battery, power save, visibility), exit info, `NativeCrashStore`, storage access, `SafProjectExport`, `KeystoreByoSigner`, Tailscale, `PackageInstallerUpdater`, `AndroidShareService` (+ FileProvider grants), `AndroidLinkOpener`, `AppCompatLocaleHost`, clipboard image, AppFunctions (later) | `:core:*`, `:engine:*`, `:ui:words` | 1 (network, crash), 2 (entry, permissions, share, links), 3–5 rest |
| `:android:media` | `oc.android.library` | F (10) | speech, TTS, Kokoro, camera + zxing-cpp, `LocalPdfServiceBody`, Wi-Fi share, voice-download notification | 10 | 3/5 (10 C10-3) |
| `:android:termux` | `oc.android.library` | F | migrate-out only (D7): `TermuxActivityBridge`, `…Commands`, `…Environment`, `…Permissions`, `TermuxResultService` body | `:core:*`, `:engine:runtime` | 5 |
| `:android:team` | `oc.android.library` | S runtime | `PhoneEngineNative`, `PhoneEngineAttestation`, `NativeEngineReadiness`, `libaiteam_engine.so`, `libaiteam_boundary_probe.so` | — | later (D6) |
| `:android:migration` | `oc.android.library` | S migration | 05 | 05 | 0, 6 |
| `:androidApp` | `oc.android.application` | S coordinator | every OS-visible class at its frozen FQCN as a thin shell, the manifest, `strings_os` res, shortcut XML, widget provider XML, launcher icons, signing, R8 rules | everything | 0 |

### 2.2 The shell rule (03 §13.6, applied to every component of this area)

Every class the OS instantiates lives in `:androidApp` at today's FQCN `io.github.eslamasabry.opencode_mobile.<Name>` and
holds no logic. It obtains a `…Body` from `context.appGraph` and forwards. Receivers and workers wait for the migration
gate inside a coroutine (`goAsync`, `CoroutineWorker`), never on the main thread (ADR §9.7, 05 §7.1: at most 20 s, then
an expedited `MigrationFollowUpWorker`). Exceptions to "body from the graph": `LocalPdfService` (S11-10) and the restore
paths of S11-5, which start the foreground service before the gate.

```kotlin
// :androidApp — io/github/eslamasabry/opencode_mobile/StatusActionReceiver.kt   (FQCN frozen)
class StatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.appGraph
        val pending = goAsync()
        graph.appScope.launch {
            try {
                if (graph.migrationGate.awaitOpen(timeout = 20.seconds)) graph.notificationActionBody.onStatusAction(intent)
                else graph.migrationFollowUp.enqueue(FollowUp.StatusAction(StatusActionExtras.read(intent)))
            } finally { pending.finish() }
        }
    }
}
```

`StatusActionExtras.read(intent)` copies only the frozen extras of 07 §7.13 and §2.5 into a typed value that becomes
WorkManager `Data` (never arbitrary extras; map T3).

### 2.3 Resources and where they live

| Resource | Module | Rule |
|---|---|---|
| `AndroidManifest.xml` with every component, permission, `<queries>`, provider, intent filter | `:androidApp` | library manifests declare nothing but `<uses-permission>` and `<queries>` they need (03 §2.3) |
| `strings_os` (app label, shortcut labels, widget descriptions, channel names, tile labels, the 148 native strings, widget/schedule/idle strings; names unchanged) | generated into `:androidApp` and `:android:widgets` (S11-8, CR11-8) | lint `MissingTranslation` on both copies; the two copies are byte-identical (a `:quality` test compares them) |
| `res/xml/shortcuts.xml` (stable) and `res/xml/shortcuts_native.xml` (preview, `android:targetPackage="io.github.eslamasabry.opencode_mobile.native"`), chosen by the manifest placeholder `${appShortcuts}` exactly as today (`shortcuts_preview.xml` is retired with the Flutter `.preview` flavour) | `:androidApp` | static shortcut XML cannot use placeholders itself, so each flavour has its own file; ids `connect`, `new_task`; the app label likewise stays the placeholder `${appLabel}` |
| widget provider info XML (`widget_*_info.xml`), `previewImage` PNGs (`drawable-nodpi`, `drawable-night-nodpi`, 7 each) | `:androidApp` | names unchanged (placed widgets keep their provider info) |
| widget layouts (`res/layout/widget_*.xml`, 11 files), shape drawables (13), usage bar drawables, `WidgetGraphite` colour resources (+ night) | `:android:widgets` | moved, names unchanged; `R` import changes to `io.github.eslamasabry.oc.android.widgets.R` (03 §2.3 step 2) |
| notification small icons (`ic_status`, `ic_launcher_monochrome`), shortcut icons (`ic_shortcut_connect`, `ic_shortcut_new_task`, `ic_shortcut_session`) | `:androidApp` (icons used by the manifest and shortcut XML) and `:android:background` (icons used in code) | the code copy uses the module's `R`; the XML copy stays in the app; same drawable names |
| `network_security_config.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `local_file_paths.xml` | `:androidApp` | unchanged (map T28; 05) |
| `jniLibs/{arm64-v8a,x86_64}/libproot.so, libproot-loader.so, libtalloc.so, libandroid-shmem.so` (+ `libaiteam_sandbox.so` per D11-1) | `:android:linux` | packaged with `useLegacyPackaging = true` in `:androidApp`; 16 KB ELF alignment checked at every T2 (§18.3) |

### 2.4 Rules every builder of this area follows

1. **Copy, never `git mv`, from the freeze tip** (`int/freeze-2026-11-01`, later `flutter-final`; 03 S9). Record
   `Copied from <sha>` in each module README. `tool/native/kotlin-drift.sh` lists later Flutter-side Kotlin commits; each
   one is re-applied (twin rule, 02 §5.6).
2. **Packages of copied files do not change** (`io.github.eslamasabry.opencode_mobile`). New files use
   `io.github.eslamasabry.oc.android.<module>`.
3. **Every frozen string is copied verbatim**: intent actions, extras, data-URI schemes, channel ids, notification ids,
   prefs file names and keys, WorkManager names, tags and `Data` keys, alarm request codes (§2.5).
4. **Delete channel glue in the same commit that adds the interface implementation.** Never leave a dead
   `MethodChannel` handler.
5. **No new `MethodChannel`, `EventChannel` or headless engine anywhere.** A Konsist rule bans `io.flutter.*` imports
   in `native/`.
6. **Crash-sensitive writes keep `commit()`** and temp-file + `fsync` + rename (map T7, H-rules in §5.8).
7. **Time:** deadlines and leases use `SystemClock.elapsedRealtime()` (in common code: the injected `Clock`'s monotonic
   source), wall clock only for display; boot id from `/proc/sys/kernel/random/boot_id` is the epoch for process
   identity (map T9).
8. **Signals go only to verified `(pid, start ticks, uid)` identities.** Never by name, pattern or port (map T2,
   AGENTS.md).

### 2.5 Frozen strings this area owns (add to `quality/freeze/`, checked at every T2 with the component freeze)

| Kind | Values (exact) |
|---|---|
| Notification channel ids | §10.1 |
| Notification ids | §10.2 |
| Prefs files (Kotlin-owned) | `oc_lifecycle`, `oc_background_native`, `oc_background_pause`, `builtin_server_recovery`, `builtin_component_writer`, `builtin_service_diagnostics`, `camera_permissions`, `voice_permissions` (05 Appendix D) |
| Intent actions | `io.github.eslamasabry.opencode_mobile.action.WIDGET_ACTION`, `…action.STATUS`, `…RESTORE_SERVER_EVENT`, `…IDLE_CHECK`, `…SCHEDULED_PROMPT`, `stop` (server service), `ACTION_CODING_ALERT` and the coding-alert extras (`BackgroundConnectionService` companion, copied verbatim) |
| Data-URI schemes | `opencode-widget://action/<widgetId>/<id>`, `opencode-widget://new-conversation/<id>`, `opencode://status/stop_reply`, `opencode://coding-alert/<key>/<decision>`, `oc-schedule://<profile>/<occurrence>`, `oc-schedule-open://<profile>/<occurrence>` |
| WorkManager | worker FQCNs `…ScheduledPromptWorker`, `…WidgetStatusWorker`; unique names `oc.schedule.<profile>.<occurrence>.fallback`, `….alarm`, `….manual`, `widget-status-<profile>-<session>`; tags `oc.schedule.<profile>`, `widget-status-profile-<profile>`; `Data` keys `profileId`, `scheduleId`, `revision`, `scheduledAtMs`, `manual` (schedules) and `profileID`, `sessionID`, `token` (widget status) |
| PendingIntent identities that the OS holds | idle alarm request code 4095 + action `…IDLE_CHECK`; schedule alarms (`oc-schedule://…` data, explicit `ScheduledPromptReceiver`); widget actions (`opencode-widget://…`); status Stop (`opencode://status/stop_reply`) — rebuilt identically so `FLAG_UPDATE_CURRENT`/`cancel()` match the Flutter-era ones |
| Meta-data | `io.github.eslamasabry.opencode_mobile.WIDGET_OVERLAY_READY = true`; `android.app.shortcuts` on `MainActivity` |
| FGS subtype texts | the two `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` values, verbatim (§9.1) |

### 2.6 The owner's design rules on OS surfaces

| Rule | How it shows up in this area |
|---|---|
| Android-native feel | system notifications, tiles, shortcuts, widgets and the share sheet are the platform's own; nothing is imitated in-app |
| Plain words, never raw errors | every failure is a typed value (`Failure.Runtime(RuntimeProblem)`, `StartProblem`, alert reason tokens) mapped once in `:ui:words`; native text only under Details (§5.5, §10.4) |
| Actions name their target | notification and widget buttons and their content descriptions say what and where: "Allow npm install in <chat>", "Stop the reply in <chat>", "Stop OpenCode on this phone" |
| Steer, never queue | widget Steer uses steer delivery (B-AGENT-317); AppFunctions `sendMessage` refuses rather than queues (§13); scheduled runs never queue, steer or interrupt (§12.1) |
| Obvious options as visible rows | the child-process card, exact alarms, battery exemption, the promoted-notification page (D11-6) are rows on their pages, never hidden menus |
| No new bar or row levels | one status notification row for everything (4747, map T11); no second ongoing notification per feature |
| Arabic RTL, text scale | widgets mirror; titles in bidi isolates; widget frame text capped at 1.3×, row text follows the system (§11.4) |
| Teach hidden gestures | tips for the tile long-press, the launcher long-press shortcuts and the widget Edit come with the widget redesign after the switch (no such tips exist today; NG9) |
| Reduced motion, heat | the kit's `MotionPolicy` reads the heat and power-save signals of `DeviceFacts` (§8.3) |

---

## 3. Components the OS knows about (all names frozen; ADR §9.9, 07 §7.13)

Every row keeps its FQCN `io.github.eslamasabry.opencode_mobile.<Name>`, its `exported` value and its intent filters.
"Shell" = thin class in `:androidApp`; "Body" = the class it forwards to and the module that owns it. The
component-freeze test (`:quality`, every T2) compares the merged manifest with the list extracted from the Flutter
2.1.0+57 APK and from the freeze-tip build; rows marked **added** are the only permitted additions (superset rule,
02 §5.5), rows marked **removed** the only permitted removals.

| Component | Kind | Exported | Filters, type, attributes (unchanged unless noted) | Body (module) | Change vs Flutter |
|---|---|---|---|---|---|
| `.OcApplication` | Application | — | `allowBackup=false`, `supportsRtl`, `enableOnBackInvokedCallback`, `manageSpaceActivity`, `networkSecurityConfig`, `usesCleartextTraffic=false` (overridden by the config, map T28) | `:androidApp` itself; implements `GraphOwner` and `androidx.work.Configuration.Provider` (05 §7.1) | rewrite: ADR §9.7 order; returns at once outside the main process (S11-10) |
| `.MainActivity` | Activity | yes | `MAIN/LAUNCHER`; `SEND text/plain`; `VIEW` + `BROWSABLE` `opencode-mobile://session`, `://team`; `android.app.shortcuts` meta; `singleTop`, `taskAffinity=""`, full `configChanges` list, `adjustResize`; **added** `ACTION_QS_TILE_PREFERENCES` filter (07 §7.11) | `:ui:shell` via `setContent { App(...) }`; intents through `EntryIntentReader` (`:android:platform`, 07 §7.1) | rewrite as `ComponentActivity`; `io.flutter…NormalTheme` meta removed |
| `.ManageSpaceActivity` | Activity | yes | system "Manage space" entry | Compose host of the Manage space page (`:feature:settings`); awaits the migration gate; never connects, never starts the server, reads no extras | rewrite (no Flutter engine) |
| `.WidgetConfigureActivity` | Activity (subclass of `WidgetRouteActivity`) | yes | `APPWIDGET_CONFIGURE`; named in provider XML | `WidgetRouteBody` (`:android:widgets`) | shell only |
| `.WidgetRouteActivity` | Activity | no | trampoline for steer, voice, configure; checks `WIDGET_OVERLAY_READY` and `ownsWidget` | `WidgetRouteBody` (`:android:widgets`) | shell only |
| `.WidgetOverlayActivity` | Activity | no | transparent, `FLAG_SECURE`, same `configChanges`, `adjustResize` | Compose host of `:feature:widget-panels` (`WidgetPanel.Style/Project/Steer`, 07 §5 M01–M03); routing identity bound natively by `WidgetOverlaySession` | rewrite (no Flutter engine; `oc/widgets` channel gone) |
| `.BackgroundConnectionService` | Service | no | `foregroundServiceType="dataSync\|shortService"` | `BackgroundConnectionServiceBody` (`:android:background`): live mode, reply watch (S11-4), sign-in claim, widget-action FGS, coding alerts | shell only |
| `.BuiltinServerService` | Service | no | `specialUse` + subtype text (verbatim, §9.1) | `PhoneServerServiceBody` (`:android:background` from slice 4; `:android:linux` before, S11-12) | shell only |
| `.SetupService` | Service | no | `specialUse` + subtype text (verbatim) | `SetupServiceBody` (`:android:background` from slice 4; `:android:linux` before) | shell only |
| `.TermuxResultService` | Service | no | RUN_COMMAND results | `TermuxResultBody` (`:android:termux`) | kept while D7 = migrate-out |
| `.LocalPdfService` | Service | no | `isolatedProcess=true`, `process=":local_pdf"` | `LocalPdfServiceBody` (`:android:media`), built directly (S11-10) | shell only (10 M12) |
| `.AttentionTileService` | TileService | yes | `BIND_QUICK_SETTINGS_TILE`, label `@string/tile_label`, icon `ic_launcher_monochrome` | `AttentionTileBody` (`:android:background`) | shell only |
| `.PauseTileService` | TileService | yes | same permission, label `@string/pause_tile_label`; **added** meta `android.service.quicksettings.TOGGLEABLE_TILE=true` (S11-7) | `PauseTileBody` (`:android:background`) | shell + one meta-data |
| `.SessionsWidgetProvider` (card 2×2, hidden) | AppWidgetProvider | yes | `APPWIDGET_UPDATE`, `widget_sessions_info.xml` | `SessionsWidgetBody` (`:android:widgets`) | shell only |
| `.SessionsWideWidgetProvider` (picker: Conversations 4×2) | AppWidgetProvider | yes | `widget_sessions_wide_info.xml` (reconfigurable) | same | shell only |
| `.SessionsListWidgetProvider` (4×4, hidden), `.SessionsStripWidgetProvider` (4×1, hidden) | AppWidgetProvider | yes | `…list_info`, `…strip_info` | same | shell only (D11-3) |
| `.UsageWidgetProvider` (2×2, hidden), `.UsageWideWidgetProvider` (4×2, hidden) | AppWidgetProvider | yes | `widget_usage_info`, `widget_usage_wide_info` | `UsageWidgetBody` (`:android:widgets`) | shell only (D11-3) |
| `.StatusWidgetProvider` (picker: Status 2×2) | AppWidgetProvider | yes | `widget_status_info.xml` | `StatusWidgetBody` (`:android:widgets`) | shell only |
| `.WidgetActionReceiver` | Receiver | no | broadcast form of a widget action | `WidgetActionBody` (`:android:widgets`) | shell only |
| `.CodingActionReceiver` | Receiver | no | Allow once / Reject / legacy Reply and option decisions | `NotificationActionBody` (`:android:background`) | shell only |
| `.StatusActionReceiver` | Receiver | no | `opencode://status/stop_reply` | `NotificationActionBody` | shell only |
| `.LivePauseReceiver` | Receiver | no | `ACTION_PAUSE_LIVE` from the Pause tile and notification | `LiveModeBody` (`:android:background`) | shell only |
| `.ScheduledPromptReceiver` | Receiver | no | `…SCHEDULED_PROMPT` (alarm), `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | `ScheduleReceiverBody` (`:android:schedule`) | shell only |
| `.BuiltinRestoreReceiver` | Receiver | no | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` | `RestoreReceiverBody` (`:android:background` from slice 4; `:android:linux` before) + `SystemEventRestorer` (`:engine:entry`, S11-11) | shell calls both (§15) |
| `.BuiltinIdleReceiver` | Receiver (`internal` today, nested in `NativeIdleTimer.kt`) | no | action `…IDLE_CHECK`, request code 4095 | `IdleAlarmBody` (`:android:linux`) | moved to its own shell file, same FQCN |
| `.AppUpdateStatusReceiver` | Receiver | no | `PackageInstaller` session status (freeze tip) | `PackageInstallerUpdater` (`:android:platform`) | shell only |
| `androidx.core.content.FileProvider` | Provider | no | authority `${applicationId}.localfiles`, `grantUriPermissions=true`, `@xml/local_file_paths` | — | kept from the freeze tip (§14.3) |
| `androidx.startup.InitializationProvider` → `androidx.work.WorkManagerInitializer` | Provider meta | — | **removed** with `tools:node="remove"` (WorkManager on-demand init, 05 §7.1) | — | removal permitted (it is a library component) |
| `ScheduledPromptWorker`, `WidgetStatusWorker` | Workers (WorkManager stores the class name) | — | — | `ScheduledRunWorkerBody` (`:android:schedule`), `WidgetStatusWorkerBody` (`:android:widgets`); shells become `CoroutineWorker` with the `(Context, WorkerParameters)` constructor | superclass `Worker` → `CoroutineWorker` (allowed: WorkManager instantiates by name) |
| `MigrationFollowUpWorker` | Worker | — | — | `:android:migration` (05) | **added** |
| `.BuiltinInstallerProducerQaService` | QA service | no | not in the shipped manifest today | moves to `:android:linux` `src/androidTest` + the `qa` build type manifest | out of `src/main` |
| `.appfunctions.OcAppFunctionService` | AppFunctionService (KSP-generated) | yes | `BIND_APP_FUNCTION_SERVICE`, `tools:targetApi="36"` | §13 | **added later** (D16) |
| Flutter plugin components (`io.flutter.plugins.imagepicker.ImagePickerFileProvider`, `mobile_scanner`/ML Kit) | — | — | — | — | **removed** (10 C10-10) |

---

## 4. Built-in Linux: the Kotlin that carries over

Source: the 177 Kotlin files at the freeze tip (168 at `65e3f70a3` plus `AgentPriority`, `AppUpdateChannel`,
`AppUpdatePolicy`, `ChildProcessLimit`, `ClipboardImageBridge`, `ClipboardImagePolicy`, `LocalLlmBridge`,
`LocalLlmPolicy`, `ServiceNoticeGate`) [V `git diff --stat 65e3f70a3 int/freeze-2026-11-01`]. Verdicts:
**copy** = moved unchanged in slice 1, refactored only by the named unit; **split** = §5.3; **port** = becomes common
Kotlin in an engine (with its tests); **shell** = §3; **delete** = channel glue or headless-engine code, replaced by
the named interface; **later** = follows an ADR decision.

### 4.1 Runtime files (`:android:linux` in slice 1; final home after the split)

| File (lines) | Verdict | Final home | Notes |
|---|---|---|---|
| `BuiltinLinux.kt` (4,511 + 52 at freeze tip) | split | `:android:linux` (§5.3) | `SharedStorageBinds` (inside it) stays as an object; mirrors `lib/domain/shared_storage_path.dart`, which is ported to `:core:security` with a shared test vector file so both copies stay in step (map T15) |
| `NativeRuntimeOwnership` (179), `NativeInstallerOwnership` (162), `NativeInstallerAdmission` (24), `NativeInstallerVisibility` (41), `NativeProcRace` (73), `NativeProcScan` (75), `NativeProcessTree` (54), `NativeProcessList` (133), `NativeOrphanReaper` (100), `NativeComponentUpdateRecovery` (308) | copy | `:android:linux` | the safety rules; **never "simplify" the `NativeRuntimeOwnership.plan` family** (map risk 2). They read `/proc`, so they stay JVM-Android until desktop work starts (ADR §7.4) |
| `NativeRecoveryBudget` (122), `RestartBackoff` (26), `NativeServerRecipe` (101), `NativeServerRestoreEvent` (89), `IdleStopPolicy` (43), `NativeIdleState` (157), `NativeIdleHeartbeat` (45), `NativeIdleReturnNotification` (50), `WorkLeases` (145), `ServiceDiagnostics` (45), `ServiceNoticeGate` (56), `SetupStepRetry` (33), `SetupScriptCompletion` (26), `SetupWorkScopes` (32), `ChildProcessLimit` (64) | copy | `:android:linux` | pure policy with JVM tests (`ServiceDiagnostics` labels exit 137 `memory_or_phantom_kill`, never proof of memory pressure). Candidates for `:engine:runtime` common later; not moved before the switch (nothing gained, drift risk) |
| `NativeIdleTimer` (182), `NativeIdleReturnNotificationHost` (56), `NativeWorkLeaseHost` (287), `NativeOrphanReaperHost` (77) | copy | `:android:linux` | Android hosts (AlarmManager, notification, wake lock `OpenCode:work`, reaper thread). `BuiltinIdleReceiver` moves out to its shell |
| `SetupRunner` (621), `SetupJob` (251), `SetupPersistence` (34) | copy | `:android:linux` | `files/linux/setup.json` format unchanged so an interrupted Flutter-era job resumes; `MainActivityBuiltinSupport.setupSpec` becomes a typed constructor |
| `BuiltinProjectStorage` (203), `BuiltinGitConfig` (51), `AndroidGroupNames` (48) | copy | `:android:linux` | H5, H6, H28 |
| `ScheduledPromptPhoneLease` (57) | copy | `:android:linux` | the temporary phone-server lease for scheduled runs (BUILTIN-LINUX-27); reached through `PhoneServerHost.acquireScheduledServer` |
| `AgentPriority` (102) | copy | `:android:linux` | moves the Paseo helpers' process trees to the background scheduling class; called through `ProcessHost.setAgentPriority` |
| `PhoneAgentHost` (239), `PhoneAgentChecks` (132), `PhoneAgentPaths` (32), `PhoneAgentClaudeHome` (180), `PhoneAgentSignIn` (442), `PhoneAgentInventory` (65), `PhoneAgentCheckOutput` (7), `PhoneAgentAuth{Capture,ColdOwner,Deadline,Inventory,Json,Lock,OtherOwners,Probe,ProcessTree,Projection,Request,Runtime}` (12 files, 645), `NativeAgentHostAdmission` (62), `PaseoStaleLock` (38) | copy, then move in slice 3 | `:android:agenthost` | `TESTED_CLAUDE_VERSIONS` stays equal to the agents engine's list by a test (map T15) |
| `LocalTerminal` (390), `java/com/termux/terminal/PtyAccess.java` | copy, then move in slice 5 | `:android:terminal` | PTY back-pressure (256 KiB pending, 32 KiB chunks, 512 KiB replay) unchanged; licence: `terminal-emulator` + `terminal-view` Apache-2.0, `termux-app`/`termux-shared` GPLv3 never used |
| `PhoneEngineNative` (593), `PhoneEngineAttestation` (115), `NativeEngineReadiness` (71) | copy (compile only), then move | `:android:team` (later, D6) | in slice 1 they compile and stay unreachable; the split (§5.3) leaves only the protection **consumer** in `:android:linux` (D11-1) |
| `BuiltinServerService` (243), `SetupService` (239), `BuiltinRestoreReceiver` (22), `StatusNotification*` (copy for slice 1–3) | shell + body | `:androidApp` + `:android:linux` in slice 1; bodies move to `:android:background` in W11-09 (S11-12) | §6, §9 |
| `MainActivityBuiltinSupport` (52), `MainActivity.handleBuiltinLinux` | delete | `RuntimeHost` (§5.4) | |
| `BuiltinInstallerProducerQaService` (633) | move | `:android:linux` `src/androidTest` / `qa` manifest | QA hooks read `AppBuildInfo.qaHooks.builtinRuntime` instead of `BuildConfig.BUILTIN_RUNTIME_QA` |

### 4.2 Platform, background, widgets and schedules files

| Files | Verdict | Home |
|---|---|---|
| `BackgroundConnectionService` (840) | shell + split body: `LiveModeHost`, `ReplyWatchHost`, `SignInClaimHost`, `WidgetActionFgsHost`, `CodingAlertPoster` | `:android:background` |
| `StatusNotification` (199), `StatusNotificationModel` (298), `StatusNotificationRenderer` (130), `BackgroundPauseStore` (119), `BackgroundPauseReceipt` (66), `LivePauseReceiver` body (74), `CodingAlertRequest` (25), `NativeNotificationLocale` (37) | copy | `:android:background`; the pure parts of `StatusNotificationModel` (`StatusContent.fromMap` clamps, `AlertCopy`, `AlertButtons`, `StatusThrottle`, `StatusOwners`) are ported to `:engine:attention` common in slice 4 and the Android copy then calls them |
| `CodingActionReceiver` (116), `StatusActionReceiver` (57), `AttentionTileService` (114), `PauseTileService` (96), `PinnedSessionShortcuts` (81) | shell + body | `:android:background` |
| `NativeStrings` (52) | delete | `Words` resolver with per-render locale (S11-8) |
| `MainActivityBackground` (141), `MainActivityBackgroundChannel` (122), `NativeChannelReplies` (63) | delete | `ForegroundWork`, `NotificationSurface`, `PermissionGate`; the 5 s / 50 ms start-acknowledgement wait moves into `AndroidForegroundWork.begin` |
| `Widget*`, `Session*Widget*`, `ConversationsWidgetBinder` (331), `StatusWidget*`, `UsageWidget*` (≈ 4.3k lines) | copy | `:android:widgets`; pure policies (`WidgetV2Policy`, `WidgetActionPolicy`, `WidgetStatusPolicy`, `WidgetLayoutPolicy`/`Rules`, `UsageWidgetFit`/`Plans`) stay Android-side until the switch; the Dart halves are ported to `:engine:attention` (§11.3) |
| `BackgroundWidgetActionController` (169) | copy | `:android:widgets` as `WidgetActionAdmission` (it needs the widget policies; the FGS is begun through `ForegroundWork`) |
| `WidgetActionRuntime` (90), `ScheduledPromptRuntime` (63) | delete | headless Flutter engines; replaced by `WidgetActionRunner` / `ScheduledRunner` coroutines (§11.3, §12) |
| `WidgetOverlaySession` (139), `WidgetOverlayStyle` (87) | copy | `:android:widgets` (implements `WidgetOverlayPort`) |
| `ScheduledPrompt{Calendar,Claims,Notifications,Policy,Receipts,Recovery,Scheduler,Settlement,Store,Worker}` | copy, then the recurrence and store rules move to `:engine:schedule` (§12) | `:android:schedule` |
| `ScheduledPromptChannel` (96), `ScheduledPromptCalls` (112) | delete | `ScheduleEngine` + `ScheduleHost` |
| `LaunchActivityBridge` (74), `LaunchLinkChannel` (63), `LaunchShareChannel` (96 + 35), `LaunchShortcutChannel` (107), `SessionLinkIngress` (9) | copy the validators, delete the channels | `EntryIntentReader` (`:android:platform`, 07 §7.1) |
| `AppLifecycle` (261), `AppExitHistoryPolicy` (130), `NetworkMonitor` (153), `ThermalMonitor` (203 + 35), `StorageAccess` (100), `TailscaleHandoff` (43), `ProjectExportBridge` (283 + 81), `NativeCrashStore` (154), `ByoHostSigner` (226), `ByoHostAgentProtocol` (99), `ClipboardImageBridge` (78), `ClipboardImagePolicy` (44), `AppUpdateChannel` (186), `AppUpdatePolicy` (31) | copy minus channel glue | `:android:platform` (`AppExitHistoryPolicy` and the Dart `classifyAppExit` port to `:engine:diagnostics` common) |
| `CrashDiagnosticsBridge` (53) | delete | `CrashStore` |
| `ReadAloudBridge` (477), `LocalPdfBridge` (198), `LocalPdfService` (102), `VoiceDownloadNotifications` (170), `MainActivityMedia` (162) | per 10 | `:android:media`; permission parts → `AndroidPermissionGate` |
| `LocalLlmBridge` (232), `LocalLlmPolicy` (42) | later | Gemma assistant after the switch (02 §3.5) |
| `Termux{ActivityBridge,ActivityCommands,ActivityEnvironment,ActivityPermissions,ResultService}` | copy minus channel | `:android:termux` (migrate-out) |
| `TermuxActivitySetup` (74), `TermuxSetupRunner` (192), `TermuxSetupShell` (128) | delete | Termux as a host is not ported (NG7) |
| `MainActivity` (597), `OcApplication` (11), `ManageSpaceActivity` (40), `WidgetOverlayActivity` (65), `WidgetRouteActivity` (84) | rewrite as shells | `:androidApp` |
| `GeneratedPluginRegistrant.java` | delete | — |

### 4.3 Tests that come with the files (green in slice 1 before any edit)

- JVM (Robolectric 4.17, JUnit 4.13.2, `org.json` test dependency): the 30 runtime classes listed in builtin-linux §7
  and the 16 platform classes `StatusNotificationModelTest`, `StatusWidgetModelTest`, `TwoWidgetsRenderTest`,
  `UsageWidgetFitTest`, `UsageWidgetPlansTest`, `WidgetActionLeaseTest`, `WidgetActionPolicyTest`,
  `WidgetCardRenderTest`, `WidgetConversationPresentationTest`, `WidgetProvidersTest`, `WidgetSessionCardLayoutTest`,
  `WidgetStatusPolicyTest`, `WidgetV2PolicyTest`, `ScheduledPromptPolicyTest`, `ScheduledPromptRecoveryTest`,
  `ScheduledPromptStoreTest` — 46 classes [V `ls android/app/src/test`]. Each moves with its subject.
- Instrumented (T3, PC emulator): `BuiltinComponentFilesystemAcceptance`, `BuiltinComponentUpdateAcceptance`,
  `BuiltinIdleAcceptance`, `BuiltinRestoreAcceptance`, `BuiltinRuntimeAcceptance`, `BuiltinRuntimeReclaimAcceptance`,
  `BuiltinWorkLeaseAcceptance` (→ `:android:linux` `androidTest`); `PhoneEngineAcceptance`,
  `PhoneEngineDeviceBoundaryRegressions`, `PhoneEngineNativeRegressions` (→ `:android:team`, later);
  `Bb8DeviceSmoke`, `Bb8MainCallback`, `Bd9DeviceSmoke` (→ retired with the Flutter QA flavours; their checks are
  covered by E2E-17/18). The QA switches `ocBuiltinRuntimeQa`, `ocBb8Smoke`, `ocBd9Smoke`, `ocStableEngineQa` become
  one `qa` build type with `AppBuildInfo.qaHooks`.

---

## 5. Built-in Linux: channels that disappear, the split, the interfaces

### 5.1 MethodChannels and headless engines that disappear (complete list for this area)

No channel survives. Each row names what replaces it; the error vocabulary survives, the transport does not.

| Channel / engine today | Kotlin half (today) | Replaced by (home) |
|---|---|---|
| `io.github.eslamasabry.opencode_mobile/builtin_linux` (~70 methods, each on a worker thread, answered on main) | `MainActivity.handleBuiltinLinux` → `BuiltinLinux` | `RuntimeHost` facets (`:engine:runtime`, §5.4), `AgentHost` (`:engine:agents`), later `TeamEngineHost` (`:engine:team`) |
| `…/local_terminal` + `…/local_terminal/events` | `LocalTerminal.register` | `TerminalHost` (`:engine:project`); output as `Flow<TerminalChunk>` with explicit `ack()` (back-pressure kept) |
| `oc/thermal` + `/events` (`current`, `notify`, freeze tip `battery`, `powerSave`, `agentPriority`) | `ThermalMonitor` | `DeviceFacts.thermal/thermalHeadroom/battery/powerSave` (CR11-5); heat notice through `NotificationSurface.heat()`; `ProcessHost.setAgentPriority` |
| `oc/lifecycle` (`launchReport`, `exitHistory`, `keepAliveInfo`, `openKeepAliveSetting`) | `AppLifecycle` | `ExitHistory` (`:engine:diagnostics`), `KeepAliveSettings` (`:core:domain`, CR11-9) |
| `oc/project_export` | `ProjectExportBridge` | `ProjectExport` (`:engine:project`), `StorageReport` (`:engine:diagnostics`: `cacheBytes`, `clearCache`, `clearAllData`) |
| `oc/storage` | `StorageAccess` | `PermissionGate(AllFilesAccess)` (CR11-4) |
| `oc/background` (status, enable/disable, pause, battery exemption, app settings, network policy, alerts, live status, widgets, locale, freeze-tip `childProcessLimit`, `openDeveloperOptions`) | `MainActivityBackgroundChannel`, `BackgroundConnectionService`, `WidgetV2Bridge` | `ForegroundWork`, `NotificationSurface`, `WidgetSurface`, `PermissionGate`, `DeviceFacts.network`, `ProcessHost.childProcessLimit()`, `OsSettingsPages` |
| `oc/shortcut`, `oc/link`, `oc/share` | `Launch*Channel` | `EntryIntentReader` → `EntryRouter` (07 §7); `ShortcutSurface`; `ShareService` |
| `oc/scheduled_prompts` | `ScheduledPromptChannel/Calls` | `ScheduleEngine` (`:engine:schedule`) + `ScheduleHost` (`:android:schedule`) + `PhoneServerHost.acquireScheduledServer` |
| headless engine `widgetActionMain` + `oc/widget_action` | `WidgetActionRuntime` | `WidgetActionRunner` coroutine (`:engine:attention`) inside the same short service |
| headless engine `scheduledPromptMain` | `ScheduledPromptRuntime` | `ScheduledRunner` coroutine (`:engine:schedule`) inside the same worker |
| engine `widgetOverlayMain` + `oc/widgets` | `WidgetOverlayActivity/Session/Style` | `:feature:widget-panels` + `WidgetOverlayPort` (`:engine:attention`, impl `:android:widgets`) |
| engine `manageSpaceMain` | `ManageSpaceActivity` | Compose host (§3) |
| `oc/network` + `/events` | `NetworkMonitor` | `DeviceFacts.network` (04 §8 uses it for pool eviction) |
| `oc/tailscale` | `TailscaleHandoff` | `TailscaleApp` (`:core:domain`, CR11-9) |
| `oc/camera`, `oc/voice`, `oc/local_pdf`, `oc/read-aloud`, `oc/clipboard`, `oc/local_llm` | `MainActivityMedia`, media bridges | 10 (`PermissionGate`, `DeviceFacts`, `PdfPageRenderer`, `SystemTts`, `PcmPlayer`, clipboard), Gemma later |
| `oc/crash_diagnostics` | `CrashDiagnosticsBridge` | `CrashStore` (`:engine:diagnostics`) |
| `oc/byo_host_signer` | `ByoHostSigner` | `ByoSigner` (`:engine:integrations`) |
| `oc/termux` | `TermuxActivityBridge` | `TermuxHost` (`:engine:runtime`, migrate-out calls only, D7) |
| `oc/app_update` (freeze tip) | `AppUpdateChannel` | `UpdateInstaller` (`:engine:diagnostics`) |
| AppFunctions headless entrypoint (branch) | `appfunctions_entrypoint.dart` | direct calls into `AssistantActions` (§13), later |

What else goes away: the "answer every call exactly once" wrapper (`NativeChannelReplies`) becomes ordinary
`suspend` + `Outcome`; the Dart `_invoke` traces `linux.<method>` become `Trace` spans named `runtime.<facet>.<method>`;
the 2×/s `status` poll during setup becomes a pushed `StateFlow` (§5.4) with the same rate limits.

### 5.2 Dart policy that becomes Kotlin (the heavy half, map §0.2)

Each row: port the Dart tests to `commonTest` first (catalog rows named), then the code. All land in `:engine:runtime`
unless named.

| Dart today | Kotlin (class) | Catalog rows (examples) |
|---|---|---|
| `builtin_server.dart` `startBuiltinServer`, `BuiltinServerStarter`, `BuiltinStartProblem` | `PhoneServerStarter`, `StartProblem` (same 8 values: `exited, timedOut, interrupted, passwordMissing, passwordNotSaved, refused, componentRecovery, busy`) | B-LINUX-025, -037, -038, -240 |
| `builtin_server_recovery.dart` | `ServerRecoveryConsumer` (states `idle, paused, stopped, checking, restarting, waiting, ready, exhausted, unconfirmed, storageUnavailable`; 5 s / 45 s cadence, foreground only) | B-LINUX-031..036, -232 |
| `phone_server_healing.dart` | `PhoneServerHealing` (connect retry 10–60 s, stale-server replace, owner pointer) | B-LINUX-151..156 |
| `phone_server_idle.dart`, `phone_agent_work_watch.dart` | `PhoneServerIdleResume`; `PhoneAgentWorkWatch` (`:engine:agents`) | B-LINUX-018, -019, -154 |
| `deliberate_stop.dart` | `DeliberateStop` (`oc.phoneServerStopped.<p>`) | B-LINUX-038 |
| `app_exit_recovery.dart`, `lifecycle_report.dart` | `AppExitRecovery`, `LifecycleReport` | B-LINUX-010..013, B-STATE-025, -077 |
| `automation_policy.dart` | `AutomationPolicy` (`:engine:settings`, 05 Appendix A) | B-LINUX-011 |
| `reply_watch.dart` | `PhoneReplyWake` (10 min hold, 5 min renew, 6 h ceiling; reply timings `oc.replySpeed.inApp`) | builtin-linux H9 |
| `process_budget.dart`, `heat_tier.dart`, `phone_budget.dart` (freeze tip) | `ProcessBudget`, `ProcessBudgetGuard`, `HeatTierPolicy`, `AgentPhoneNote` (`:engine:agents`) | catalog top-up (02 §3.5) |
| `phone_processes.dart`, `phone_process_watch.dart` | `PhoneProcessWatch` (heavy = ≥ 80 % in every reading for ≥ 2 min; stuck = orphan, or ≥ 90 % twice on a process older than 10 min with no conversation) | B-LINUX-143..147, -180 |
| `thermal_guard.dart`, `thermal_guard_teams.dart` | `ThermalPolicy` + services heat hold (switch); `TeamThermalGuard` (`:engine:team`, later) | B-ANDR-083..092 |
| `setup/*` (engine, components, preflight, journals, removal, voice hook) | `SetupEngine`, `ComponentRegistry`, `Preflight`, `ComponentRemoval` | B-LINUX-001, -007, -014, -157, -181..220 |
| `linux_manager/*`, `linux_services/*`, `linux_vitals.dart`, linux backup (freeze tip) | `LinuxManager`, `LinuxServices`, `LinuxVitals`, `LinuxBackup` | B-LINUX-054..065, -082..093, -247..249 |
| `builtin_folders.dart` | `RootfsFolders` over `GuestFiles` (never follows links, map T13) | B-LINUX-016, -231 |
| `git_shell.dart`, `git_link_repair.dart` | `:engine:project` (`PhoneGitShell`, `GitLinkRepair`) | B-LINUX-072..074 |
| `quota_collector_host.dart` | `:engine:integrations` (`QuotaCollectorHost`) | B-LINUX-176..178 |
| `migration/*` (Termux move) | `TermuxMigration` (D7) | B-LINUX-233..238 |
| `agents/*` | `:engine:agents` (agents section) | B-LINUX-049..053, -114..142 |
| `factsbook_node_host.dart`, `team/*` | later (D6) | B-LINUX-039..048, -066..069 |

### 5.3 Splitting `BuiltinLinux.kt` (slice 3, unit W11-03, Opus)

Line ranges are from `oc_app-batch1` (`65e3f70a3`); the freeze tip adds 52 lines (`startServerForLaunch`, the
`ServiceNoticeGate` call in `serviceSetChanged`, the lease monitor) [V]. The split is **moves only**: no logic edits, no
renamed private helpers except where two classes now need one, each moved block keeps its comments.

| New class (`:android:linux`, package `io.github.eslamasabry.opencode_mobile`) | Lines today | Holds |
|---|---|---|
| `BuiltinLinux` (façade, process singleton `get(context)`) | 39–59, 4206–4468 (companion) | construction, shared fields, `RuntimeLocks`, delegates; every public method that the old class had keeps its signature here for the copied tests |
| `RuntimeInstall` | 152–206, 3922–4205 | `install`, `download` (resume, manual redirects ≤ 5, SHA-256), `unpack` (tar.gz; hard links copied last), `configure`, `installInBackground`, `uninstall`, `projectStorage`, `bytesUsed` |
| `GuestScriptRunner` | 208–315 | `run`, `admitCheckProcess`, `start` (named so it is not confused with the `GuestRunner` interface it implements) |
| `InstallerSlot` | 316–668 | ticket, plan, drain, `reclaimDeadInstaller`, `recoverColdComponentUpdates`, `startInstaller`, the 100 ms ownership monitor (5 strikes) |
| `ProotLauncher` | 60–150, 669–744, 1080–1119, 1722–1730 | `prootCommand`, `prepareGuestForProot`, fake `/proc` binds, `procMaskBinds`, `prootEnvironment`, shared-storage binds, **protection-marker consumer** (`markerPresent`, `protectionTier`, `prootLaunchPath`, `protectedCommand`) |
| `AgentProcesses` | 745–1079, 2093–2131 | agent homes, sign-in command, private agent processes, host admission tickets (moves to `:android:agenthost` behind `GuestLauncher` in the same unit, CR11-0) |
| `PhoneEngineBridge` | 1120–1721 | boundary probes, phone engine start/stop/status, protected server (moves to `:android:team`, later) |
| `ServiceRegistry` | 1731–1872, 3352–3363, 3494–3745 | services map, diagnostics records, `startService`/`launchService`/`monitorServiceExit`/`stopService`/`stopAllServices`, `serviceSetChanged` (+ `ServiceNoticeGate`), `recordRunning`, logs |
| `IdleController` | 1873–2092 | idle observe/stop/resume/complete, return notification |
| `ServerRecovery` | 2132–2189, 2270–3351, 3364–3493 | gate launch + receipts, ownership, drains, event restore, cold restore, budget, native recovery, wanted/stop/cancel/confirm, `startServer`, `restartServer`, scheduled phone lease |
| `ProcessInventory` | 2190–2269, 3746–3820 | kernel identity, same-uid inventory, orphans, `processTable`, `stopProcesses` |
| `WorkLeaseBridge` | 3821–3890 | chat/agent/setup leases, `trackWork` |
| `PerformanceReport` | 3891–3921 | `performance`, seccomp filter comparison |
| `SharedStorageBinds` (object) | 4469–end | unchanged |

Recipe:

1. Create `RuntimeLocks(val instance: Any, val recovery: Any)` with the old `this` and `recoveryLock`; pass the same
   object to every split class (S11-2). Replace `@Synchronized` on moved methods with `synchronized(locks.instance)`.
2. Move one class per commit, top of the table first; after each commit run T0
   `./gradlew :android:linux:testDebugUnitTest` (all 30 runtime classes) and T1 on the VPS.
3. Before the first move, add `RuntimeLockOrderTest`: a Robolectric test that drives stop, idle stop, cold restore and
   service exit on four threads and asserts no deadlock within 10 s; it also records which methods take both monitors
   and in which order, and fails if a moved method nests them differently from the original.
4. After the last move, run the 7 runtime acceptance classes (T3) and the restore proofs (§6.8).
5. Only then delete `MainActivity.handleBuiltinLinux` and add `AndroidRuntimeHost` (§5.4).

### 5.4 The interfaces (Kotlin, `:engine:runtime` commonMain unless named)

```kotlin
package io.github.eslamasabry.oc.engine.runtime.host

/** The phone's own Linux. Android: AndroidRuntimeHost (:android:linux, @ContributesBinding(AppScope::class)).
 *  Desktop and iOS: UnavailableRuntimeHost (available = false) until the desktop phase (ADR §7.4). */
public interface RuntimeHost {
    public val available: Boolean                               // == PlatformCapabilities.builtinLinux
    public val status: StateFlow<RuntimeStatus>                 // pushed on every native change (§5.7)
    public suspend fun refreshStatus(): Outcome<RuntimeStatus>  // one shared in-flight read, as today
    public val install: RuntimeInstallHost
    public val guest: GuestRunner
    public val server: PhoneServerHost
    public val services: GuestServiceHost
    public val recovery: ServerRecoveryHost
    public val idle: IdleHost
    public val work: WorkLeaseHost
    public val processes: ProcessHost
    public val setup: SetupJobHost
    public val files: GuestFiles
}

public interface RuntimeInstallHost {
    public suspend fun installUbuntu(): Outcome<Unit>                         // returns at once; progress in status
    public suspend fun projectStorage(): Outcome<ProjectStorageSize>          // runtimeBytes, projectsBytes, measuredAt
    /** confirmationName must equal "OpenCode" when alsoDeleteProjects (BUILTIN-LINUX-04). */
    public suspend fun removeRuntime(alsoDeleteProjects: Boolean, confirmationName: String?): Outcome<Unit>
    public suspend fun setSharedProjects(roots: List<String>): Outcome<Unit>  // union over profiles; atomic file
    public suspend fun performance(): Outcome<RuntimePerformance>
}

public interface GuestRunner {
    /** The single installer slot, 10 s admission, dash `/bin/sh -c`, output tail ≤ 64 KiB, exitCode -1 = unreadable.
     *  Never for long-running programs: proot dies with the script (--kill-on-exit, map T3). */
    public suspend fun run(script: GuestScript, timeout: Duration = 600.seconds, agentUser: Boolean = false): Outcome<GuestRunResult>
}

public interface PhoneServerHost {
    public suspend fun start(script: GuestScript, port: Int, recipe: RestoreRecipe?): Outcome<Unit>
    public suspend fun startForLaunch(script: GuestScript, port: Int, expectedGeneration: Long, recipe: RestoreRecipe?): Outcome<Unit>
    public suspend fun restart(script: GuestScript, port: Int, expectedGeneration: Long): Outcome<Unit>
    /** No I/O, safe on the main thread: bumps the revisions and writes wanted=false/userStopped=true with commit()
     *  before returning (map T8). The drain (SIGTERM → 3 s → SIGKILL, exact identities) runs afterwards. */
    public fun requestStop(reason: StopReason, includeOtherServices: Boolean): StopTicket
    public suspend fun awaitStopped(ticket: StopTicket): Outcome<Unit>
    public suspend fun log(tailBytes: Int = 16_384): Outcome<String>
    public suspend fun acquireScheduledServer(profile: ProfileId, password: SecretText, deadline: Instant): Outcome<ScheduledServerLease>
    public suspend fun releaseScheduledServer(token: String): Outcome<Unit>   // stops only that exact tree
    public fun activeScheduledLease(profile: ProfileId): ScheduledLeaseState? // boot id + monotonic deadline
}
public enum class StopReason { Person, Uninstall, EngineSetup, SystemTimeout }

public interface GuestServiceHost {
    /** Names `[a-z][a-z0-9-]{0,31}`; one proot per service; an earlier run of the same name is stopped first. */
    public suspend fun start(name: ServiceName, script: GuestScript, port: Int?, notice: StatusNoticeKey?): Outcome<Unit>
    public suspend fun stop(name: ServiceName): Outcome<Unit>
    public suspend fun log(name: ServiceName, tailBytes: Int = 16_384): Outcome<String>
}

public interface ServerRecoveryHost {                  // all failures → Failure.Runtime(RecoveryUnavailable)
    public suspend fun stage(profile: ProfileId, legacyBudget: RecoveryBudget?): Outcome<Unit>
    public suspend fun bind(profile: ProfileId, legacyBudget: RecoveryBudget?, enabled: Boolean): Outcome<Unit>
    public suspend fun budget(profile: ProfileId): Outcome<RecoveryBudget>
    public suspend fun receipts(profile: ProfileId): Outcome<List<RecoveryReceipt>>
    public suspend fun ack(profile: ProfileId, eventId: String): Outcome<Unit>
    public suspend fun updateReceipt(profile: ProfileId, budget: RecoveryBudget): Outcome<Unit>
    public suspend fun confirmManualStart(profile: ProfileId): Outcome<Unit>
    public suspend fun confirmPresence(profile: ProfileId): Outcome<Unit>
    public suspend fun unbind(profile: ProfileId): Outcome<Unit>
    public suspend fun delete(profile: ProfileId): Outcome<Unit>             // profile deletion, after suspend (map T12)
    public suspend fun cancel(): Outcome<Unit>
    public suspend fun confirm(expectedGeneration: Long): Outcome<Unit>
}

public interface IdleHost {
    public suspend fun setPolicy(enabled: Boolean, idleMinutes: Int): Outcome<Unit>       // 1..60
    public suspend fun observeAgentWork(profile: ProfileId, busy: Boolean): Outcome<Unit>  // fresh 45 s
    public suspend fun resume(profile: ProfileId, expectedIdleGeneration: Long): Outcome<IdleResume>
    public suspend fun complete(resume: IdleResume): Outcome<Unit>
}

public interface WorkLeaseHost {                       // WorkLeases: ≤ 15 min hold, renew every 5 min, ≤ 6 h aggregate
    public fun chatLease(id: LeaseName, on: Boolean, forMs: Long): LeaseAnswer           // held, capped
    public fun agentChatLease(profile: ProfileId, id: LeaseName, on: Boolean, forMs: Long): LeaseAnswer
    public fun holdAwake(on: Boolean, forMs: Long): LeaseAnswer                          // legacy reply lease
}

public interface ProcessHost {
    /** Two /proc scans 1 s apart. Callers sample only while a page is open (map T11). */
    public suspend fun sample(): Outcome<ProcessSample>      // rows, sampledAt, orphansReaped, orphansLastReapedAt
    public suspend fun stop(pids: List<Int>): Outcome<StopResult>  // ≤ 256; never the app, never a tree root
    public fun appProcessCount(): Int?                       // what Android counts against the 32 (§7)
    public fun childProcessLimit(): ChildProcessLimitState   // sdkInt, developerOptionsEnabled?, restrictionsOff?
    public suspend fun setAgentPriority(level: AgentPriorityLevel): Outcome<Int>  // Normal | Slow | Hold; returns pids moved
}

public interface SetupJobHost {
    public suspend fun start(job: SetupJobSpec): Outcome<Unit>      // typed, replaces MainActivityBuiltinSupport.setupSpec
    public suspend fun status(): Outcome<SetupJobRecord>            // files/linux/setup.json, parsed tolerant
    public suspend fun cancel(): Outcome<Unit>
    public suspend fun completeStep(jobId: String, componentId: String, ok: Boolean, error: String?, version: String?): Outcome<Unit>
}

/** Reads and writes inside files/linux/ubuntu and files/projects from the app side. lstat only, never follows links,
 *  temp file + rename for writes, mode bits explicit (git tokens 0600; map H26). */
public interface GuestFiles {
    public suspend fun list(root: GuestRoot, path: String): Outcome<List<GuestEntry>>
    public suspend fun read(root: GuestRoot, path: String, maxBytes: Int): Outcome<ByteArray>
    public suspend fun writeAtomic(root: GuestRoot, path: String, bytes: ByteArray, mode: Int): Outcome<Unit>
    public suspend fun delete(root: GuestRoot, path: String): Outcome<Unit>
}
public enum class GuestRoot { Rootfs, Projects }
```

The two service seams of S11-12 (also `:engine:runtime` commonMain):

```kotlin
/** Runtime → foreground service. Impl AndroidPhoneServerForeground (:android:background): starts BuiltinServerService
 *  with startService while the app is visible, startForegroundService (a promise) otherwise; ServiceNoticeGate inside. */
public interface PhoneServerForeground {
    public val shown: StateFlow<Boolean>
    public fun keep(notice: StatusNoticeKey?)                 // called by ServiceRegistry.serviceSetChanged
    public fun release()                                      // last guest service stopped
    public fun startForRestore(ticket: RestoreTicketId): Boolean
}
/** Foreground service → runtime. Impl AndroidPhoneServerServiceHooks (:android:linux). Ticket ids are opaque handles to
 *  in-memory NativeServerRestoreTicket objects; nothing is serialized or rebuilt from intent extras. */
public interface PhoneServerServiceHooks {
    public fun prepareEventRestore(event: RestoreEvent): RestoreTicketId?      // BOOT_COMPLETED | MY_PACKAGE_REPLACED; reads Kotlin-owned prefs only
    public suspend fun restoreAfterEvent(ticket: RestoreTicketId)               // after startForeground and the migration gate (§6.5)
    public fun cancelEventRestore(ticket: RestoreTicketId)
    public fun onNullIntentRestart(): ReclaimDecision                           // Armed → START_STICKY path; Rejected → stop
    public fun onStopAction()                                                   // the row's Stop OpenCode
    public fun onTimeout()                                                      // stopRuntime("systemTimeout")
    public fun onDestroyed()                                                    // revoke foreground work leases, cancel tickets
    public fun keepsForeground(): Boolean                                       // recovery delay pending or work leases held
}
```

`:android:linux` only (Kotlin, not common), the two seams of CR11-0:

```kotlin
package io.github.eslamasabry.oc.android.linux

/** How :android:agenthost and :android:terminal start guest programs. Every launch goes through ProotLauncher, so a
 *  terminal, a check, a server and an agent see the same filesystem and environment (BUILTIN-LINUX-01). */
public interface GuestLauncher {
    public fun prootCommand(program: List<String>, agentUser: Boolean): List<String>
    public fun environment(): Map<String, String>                 // PROOT_LOADER, PROOT_TMP_DIR, LD_LIBRARY_PATH only
    public fun startRegistered(owner: ProcessOwner, command: List<String>, privateOutput: Boolean): Process
}
/** Inverts today's BuiltinLinux → LocalTerminal / PhoneAgent* calls (they become registrations). */
public interface GuestProcessRegistry {
    public fun register(owner: ProcessOwner, process: Process)
    public fun unregister(process: Process)
    public fun registerRootPids(owner: ProcessOwner, pids: () -> Collection<Int>)   // terminals, agent hosts
}
public enum class ProcessOwner { App, OpenCode, Paseo, AgentSignIn, Engine, Helper, Task, Terminal, Orphan, Other }
```

Threading of `AndroidRuntimeHost`: every blocking call runs in `withContext(runtimeIo)` where
`runtimeIo = dispatchers.io.limitedParallelism(8)`; nothing touches the main thread except `requestStop`, which never
does I/O. `BuiltinLinux.get(context)` stays the process-wide singleton the services and receivers also use.

### 5.5 Failures (CR11-1)

```kotlin
// :core:common, next to Failure
public enum class RuntimeProblem {
    OwnershipUnknown,        // runtime_ownership_unknown: "Open app settings, force stop OpenCode, then open it again."
    SlotBusy,                // "Another phone task is running": busy, never broken (map T4); readers retry twice, 700 ms
    RecoveryUnavailable,     // recovery_unavailable
    IdleResumeUnavailable, IdleResumeStale,
    SetupPersistence,        // setup.json could not be written
    InvalidProfile, ConfirmationRequired, StorageUnavailable, RemovalFailed, WorkLeaseInvalid,
    ServerStartUnavailable, NotInstalled, Installing, ComponentRecoveryRequired,
    ProtectionUnavailable,   // the AI Team protection marker demands a launcher this build lacks (D11-1)
    Generic,                 // builtin_linux
}
```

`details` is `Redacted` and carries what `serverStartDetail` carries today (exception kind, message, first 3 frames,
≤ 600 chars) for the Details fold only (no raw errors, map T10). `:ui:words` maps every value to one plain sentence and
one named fix; the Flutter sentences are the source (copy parity, 02 §6.6).

### 5.6 Scripts (S11-3)

| Step | What | Check |
|---|---|---|
| 1 | `tool/native/extract_scripts.dart` (a one-off program run against the Flutter reference) calls every Dart script builder with sentinel values and writes `native/engine/runtime/src/commonMain/scripts/<group>/<name>.sh.tmpl` with `@@NAME@@` in place of each sentinel, plus `golden/<name>.sample.json` (values) and `golden/<name>.sample.sh` (Dart's rendered output). Groups: `setup/` (prelude, `oc_download`, `oc_apt_install`, component check/install/remove/presence/size), `server/` (server script, password write, OC1 history copy, phone context), `manager/` (process scan, ports, storage, packages, reset), `services/` (`oc-svd` v2, launch), `backup/`, `agents/` (agent, Paseo, removal, gen-UI install), `git/`, `quota/`, `termux/` (migrate-out). | every Dart builder has a template (a list test) |
| 2 | build-logic `OcScriptsPlugin`, task `generateRuntimeScripts`: emits `RuntimeScripts.kt` (`public object RuntimeScripts { public val setupPrelude: ScriptTemplate = … }`, raw strings with `$` escaped) and `scripts.sha256` | generated, never edited |
| 3 | `ScriptTemplate.render(values: Map<String, String>): GuestScript`: every value through `ShellQuote.single()` (POSIX single quotes, `'` → `'\''`); a missing or unknown placeholder throws `IllegalArgumentException` | `ScriptTemplateTest` |
| 4 | `ScriptGoldenTest` (commonTest): `render(sample.json) == sample.sh` byte for byte for every script | parity with Dart |
| 5 | `ScriptChecksumTest`: template SHA-256 equals `scripts.sha256` (an accidental edit fails; an intended edit updates the list in the same commit) | every T1 |
| 6 | `ScriptShellTest` (desktopTest, VPS): `dash -n` and `bash -n` on every rendered sample; the ported behaviour suites (`setup_scripts_test`, `setup_apt_repair_test`, `linux_manager_parse_test`, `linux_services_script_test`, `linux_backup_plan_test`) run the real scripts under dash and bash (map T14) | T1 on the VPS |

Rules kept from today: scripts target dash; no bashisms; `tar` never opens its own `.tar.gz` under proot (H15); every
install script starts with the setup prelude; component pins (Ubuntu Base 24.04.5, Node v24.21.0, OpenCode 2.1.1,
Paseo 0.11.2, Claude Code tested set) move verbatim into `ComponentRegistry` with their SHA-256s.

### 5.7 Status contract (frozen; builtin-linux §13)

`RuntimeStatus` carries exactly today's keys, parsed as strictly as Dart's `BuiltinLinuxStatus.fromMap` (negative or
fractional numbers → null; an invalid idle receipt → `serverIdleReceiptValid = false` and idle and healing refuse to
act): `installed, phase (Idle|Installing|Ready|Failed), message, serverRunning, serverRestartWanted,
serverRecoveryGeneration, serverRecoveryAuthority, serverRecoveryScheduled, restorePhase (Idle|Waiting|Restoring|
Unavailable), restoreReason (Stopped|PolicyDisabled|BudgetExhausted|OwnershipUnknown|StorageUnavailable|
ComponentRecoveryRequired|SystemTimeout|IdleStopped), serverPort, serverUptimeMs, services, abi, bytesUsed,
serverIdlePolicySupported, serverIdleEnabled, serverIdleMinutes, serverIdleStopped, serverIdleHelperStopped,
serverIdleGeneration`. Push rule: `AndroidRuntimeHost` emits a new value on every internal change (service set, phase,
restore phase, idle state) and polls `setup.json` every 500 ms only while a setup job runs; `bytesUsed` stays a
≥ 10-minute cached background walk, paused while a reply holds the wake lease. `restorePhase`/`restoreReason` are now
shown (map Q5): This phone says "Restarting your server…" while `restorePhase = Restoring` (07 F01 owns the words).

### 5.8 Hard-won fixes: each has a native proof (builtin-linux §9)

| H | Behaviour | Native proof |
|---|---|---|
| H1, H2, H3, H17 | EPERM/ESRCH/ENOENT mean gone; 5-strike monitor; tracer walk for orphans; pid + start ticks | copied `NativeInstallerVisibilityTest`, `NativeProcRaceTest`, `NativeInstallerOwnershipTest`, `NativeProcessTreeTest` |
| H4, H29 | orphan reaper, `(deleted)` loader accepted | copied `NativeOrphanReaperTest`; T3 `BuiltinRuntimeAcceptance` |
| H5, H6 | `core.createObject = rename`; Android groups named | `BuiltinGitObjectsTest`; B-LINUX-009 |
| H7 | boot/update resets a settled restore count; healthy open resets once per visit | `NativeRecoveryBudgetTest`, `NativeServerRestoreEventTest`; §6.7 proof |
| H8 | connect retry 10–60 s; stale server replaced | `PhoneServerHealingTest` (41 behaviours ported) |
| H9, H25 | reply wake bounded; leases never override FGS policy | `WorkLeasesTest`, `NativeWorkLeaseHostTest`, `PhoneReplyWakeTest` |
| H10, H18, H19, H20, H21, H22 | Node tarball + `--foreground-scripts`; clean env; `CODEX_HOME` exists; Claude sign-in link + `CLAUDE_HOME`; stale locks cleared; project folder exists before OpenCode sees it | `ScriptShellTest`, `PhoneAgentClaudeHomeTest`, `PaseoStaleLockTest`, B-LINUX-125 |
| H11, H27 | setup poll survives one bad read; interrupted steps retried ≤ 3, failing ones not | `SetupStepRetryTest`, `SetupEngineTest` (B-LINUX-157, -203) |
| H12 | ports scan POSIX sh, listed only when a loopback connect works | `LinuxManagerParseTest`, B-LINUX-085..087 |
| H13 | Reset Linux: marker first, setup restarts at once | `LinuxResetFlowTest` (B-LINUX-093) |
| H14 | manager reads retry twice on ownership/busy | B-LINUX-092 |
| H15 | tar never opens its own archive under proot | `ScriptShellTest` (backup group) |
| H16 | process ages from `elapsedRealtime` | `NativeProcessListTest` |
| H23, H24 | promised FGS shows its notification; keep FGS during recovery delay; `onTimeout` handled | §9.4 tests |
| H26 | git tokens as 0600 random files written app-side | `GuestFilesTest` |
| H28 | projects outside the rootfs survive removal | B-LINUX-017, -028, -029 |
| H30 | running-services set persisted with `commit()` on every change | `AppLifecycleTest` (`oc_lifecycle.running_services`) |

---

## 6. Phone server lifecycle and the restart-after-update rule

### 6.1 What the person sees, and where the truth is

The phone server's visible state (This phone card, status row, Status widget Linux line) is derived in
`PhoneServerState` (`:engine:runtime`) from `RuntimeStatus` plus an authenticated loopback probe. A pid alone is never
evidence (B-LINUX-240, B-STATE-025).

| State | Derived from | Words owner |
|---|---|---|
| `NotInstalled` | `installed = false` | 07 F01 |
| `Stopped(by: Person \| Idle \| Itself \| Android \| Unknown)` | `serverRunning = false` + `oc.phoneServerStopped.<p>`, idle receipt, exit classification | 07 F01, B-LINUX-250 |
| `Starting(step)` | `PhoneServerStarter` running | 07 F01 |
| `Running` | probe answered with our password | — |
| `NotAnswering` | process alive, probe fails ("isn't answering", map BUILTIN-LINUX-26) | 07 F01 |
| `Restarting(attempt, of = 3, nextAt)` | recovery budget pending | 07 F01 |
| `Restoring` | `restorePhase = Restoring` (shown natively for the first time, map Q5) | 07 F01 |
| `GaveUp(reason)` | `exhausted`, or `restoreReason ∈ {BudgetExhausted, OwnershipUnknown, PolicyDisabled, StorageUnavailable, ComponentRecoveryRequired}` | 07 F01 + `Failure.Runtime` words |

### 6.2 The one start path (BUILTIN-LINUX-15; setup, opening card, healing and recovery all use it)

1. Refuse if the profile still needs the OpenCode 2 move (`requiresOpenCode2Migration`) unless the move is part of this
   start.
2. A lost Keystore password is replaced (`renewBuiltinPassword`: 32 random bytes, base64url) and saved to
   `SecretStore` under the same name `pw.<profileId>` (05 Appendix B), registered with the `Redactor` first.
3. `guest.run(RuntimeScripts.passwordWrite.render(…))`: `umask 077`, write `server.password.tmp`, `mv` to
   `/root/.oc-builtin/server.password`. The password never appears on a command line or in a log.
4. `server.start(RuntimeScripts.serverScript.render(…), port = AppBuildInfo.ports.phoneServer, recipe =
   RestoreRecipe(version = 1, profileId, runtime = "openCode2"))`, or `server.restart(…, expectedGeneration)` for
   automatic recovery, or `server.startForLaunch(…, expectedGeneration)` for the app-open start (freeze tip: refused if
   a Stop moved the generation first).
5. Probe `http://127.0.0.1:<port>` with user `opencode` every 2 s for up to 90 s; each miss reads `refreshStatus()`;
   `!serverRunning` → `StartProblem.exited`; deadline → `timedOut(90)`; success → `recovery.confirm(expectedGeneration)`
   for an automatic start, `recovery.confirmManualStart(profile)` for a manual one.
6. After success: clear the deliberate-stop mark, restart the quota collector if it was set up (AI Team when D6 lands).

App open allows exactly one automatic start per open (`allowAutoStart`); a failure path never calls it, so a dying
server is not looped (B-LINUX-038, -151).

### 6.3 Crash recovery (native, unchanged; BUILTIN-LINUX-20)

When `server` exits by itself and recovery is admitted (supervision enabled for the bound profile, wanted, not
user-stopped, generation matches, migration marker valid, automation policy permits), thread `phone-native-recovery`
waits `RestartBackoff` (1 s doubling to 60 s; a run ≥ 30 s resets it; persisted per boot), reserves attempt N of
**3** (`oc.builtinRecoveryBudget.<p>`), and relaunches with `ServiceLaunch(nativeOwned = true)`. The foreground
service is **kept** during the wait (re-requesting a background FGS start would lose admission, H23). A cold app
(`setActivityResumed(false)`) cancels an unconfirmed automatic attempt, never a confirmed one. Confirmed attempts land
in `oc.builtinRecoveryReceipts.<p>` (≤ 16) until the app acknowledges them as "While you were away" entries.

### 6.4 The restart-after-update rule (R1–R9)

| # | Rule | Source |
|---|---|---|
| R1 | **Who comes back:** only a server the person wanted (`wanted = true`, `userStopped = false`), whose supervision and restore owner equal the recipe's profile, whose automation policy allows it (`behaviors.restartPhoneServer && behaviors.pollRestartHealth`; absent = allowed, malformed = denied), whose recovery marker is exactly `{"version":2,"nativeAuthority":true}`, with no pending component recovery or installer ticket and at most 2 attempts after the event reset. | BUILTIN-LINUX-21, -22; 05 M4 |
| R2 | **When:** `MY_PACKAGE_REPLACED` (app update), `BOOT_COMPLETED` (reboot, and on Android 15+ also "the person opened a stopped app"), `START_STICKY` null-intent reclaim (only while restoration is armed), app open (once), and an OpenCode component update (R7). | BUILTIN-LINUX-22, -23 |
| R3 | **Recipe compatibility:** an update accepts `recipe.packageVersion ≤ versionCode` and rewrites it upward; a reboot requires an exact match; `rootfsGeneration` must equal `builtin_component_writer.rootfsGeneration`. The native `versionCode` base **10000** (ADR §9.9) is above every Flutter build (57 public, 4300–4511 local), so the first native update always passes R3. | BUILTIN-LINUX-22 |
| R4 | **Budget:** a boot or update event resets a settled restore count; an open app with a healthy server resets it once per visit. Without this, every fourth update left the server off. | H7 |
| R5 | **Drain first:** the previous boot's server is drained exactly by its receipt; after a reboot, cross-boot quiescence (every same-uid process accounted for) is required before launch. | BUILTIN-LINUX-19, -22 |
| R6 | **Never:** a server the person stopped; an idle-stopped server (only a foreground resume brings it back); policy off; budget exhausted (until a manual start or a healthy open); ownership unknown (restore disarmed with that reason, shown on This phone). | BUILTIN-LINUX-18, -24, -25 |
| R7 | **OpenCode component update** (in-app auto-updater, B-LINUX-078/079, freeze tip "the phone server restarts itself after an OpenCode update"): runs only when idle (no setup job, no reply, no handoff); after the journalled activation (`*.oc-pending` → committed) the server restarts once through `server.restart(…, expectedGeneration)`; a failed restart rolls the journal back to `*.oc-good` and restarts the old binary; one "Updated OpenCode to <version>" entry in While you were away. | BUILTIN-LINUX-37, 02 §3.5 |
| R8 | **First native launch** (the first native process is usually started by `MY_PACKAGE_REPLACED` with no UI): the sequence in §6.5. | S11-5, 05 §7.1 |
| R9 | **Force stop:** a force-stopped package gets no broadcast until it is opened (Android 15 stopped state; on the owner's Nubia a swipe from Recents counts). At the next open, `SystemEventRestorer.onAppStart(forceStopped = true)` re-arms and redraws (§15) and the normal app-open start (§6.2) brings the server back once; `AppExitRecovery` shows one notice ("Android closed OpenCode Mobile at 00:06 …"). | android-surfaces §2.4, BUILTIN-LINUX-28 |

### 6.5 First native launch, step by step (S11-5)

1. Android delivers `MY_PACKAGE_REPLACED` to `.BuiltinRestoreReceiver` and `.ScheduledPromptReceiver`.
2. `OcApplication.onCreate` runs ADR §9.7 steps 1–7 (crash handler, Redactor, crash-loop guard, S0 byte backup, graph,
   `MigrationRunner` on IO, prefs warm-up).
3. `BuiltinRestoreReceiver` shell → `RestoreReceiverBody` → `PhoneServerServiceHooks.prepareEventRestore(event)`
   (implemented in `:android:linux`). It reads **only** the Kotlin-owned
   `builtin_server_recovery` file (`wanted`, `userStopped`, `restoreOwner`, `oc.builtinRuntimeRecipe.<owner>`), which
   the migration never touches (05 §7.7), so no gate is needed. Fail → record `restoreReason`, finish. Pass →
   `BuiltinServerService.startForRestore(context, ticket)` at once, inside the broadcast's start exemption. The ticket
   stays in memory only (never serialized, never rebuilt from extras).
4. `PhoneServerServiceBody.onStartCommand`: `ServiceCompat.startForeground(4747, row, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`
   with the native fallback wording "Starting OpenCode on this phone" before anything else (H23).
5. Body: `migrationGate.awaitOpen(timeout = 60.seconds)`. Timeout → stop the service with
   `restoreReason = StorageUnavailable`; the next app open starts the server (R2).
6. Full admission `NativeServerEventPolicy.admitted` with the adopted Flutter keys (`oc.builtinRecovery.<p>`,
   `oc.automation.<p>`, `oc.phoneAgentOwner.<p>`; M4 proved them unchanged).
7. `restoreServerAfterSystemEvent(ticket)` exactly as today: wait ≥ 1 s, re-validate, drain by receipt, reserve a fresh
   attempt (`afterSystemEvent`), rewrite `packageVersion`, launch `NativeServerRecipe.restorationScript()` (nothing
   app-supplied after a reboot).
8. The same shell also calls `SystemEventRestorer.onSystemEvent(PackageReplaced)` after the gate (§15).
9. Live mode is **not** auto-resumed: `PausePolicy.reconcile` classifies the Flutter process's exit (reason 16) as
   `appUpdated` (`ApplicationExitInfo.REASON_PACKAGE_UPDATED`), the mode stays paused with Resume (B-ANDR-015), exactly as
   the Flutter app behaves after an update. The reconcile writes `oc.keepLiveInBackground = false` (commit), so 05 §7.9
   H2's live-mode step finds it off and starts nothing.

`ScheduledPromptReceiver` handles the same broadcast independently: `goAsync`, gate ≤ 20 s, `ScheduleEngine.rearmAll
(PackageReplaced)` (idempotent), else `MigrationFollowUpWorker`.

### 6.6 Idle stop and resume (BUILTIN-LINUX-25, unchanged)

Setting "Stop the phone server after N idle minutes" (1–60). A stop needs: app in the background, `logicalWorkBusy()
= false`, the agent heartbeat fresh (45 s) and not busy, the services set ⊆ {`server`, `agent-host.<owner>`}, and a
complete ownership plan. The alarm (`BuiltinIdleReceiver`, request code 4095, 8 s receiver budget) only re-checks. The
"Paused · nothing was running" notification (id 4100, channel `opencode_builtin_server`) posts only when stopped for
idleness, wanted, not user-stopped, app not in front; posting never starts a service or lease. Resume is foreground
only: status → resume server → resume the agent helper → status → complete, epoch-guarded.

### 6.7 Stop (BUILTIN-LINUX-24)

`server.requestStop(Person, …)` on the main thread: bumps `wantedRevision`, `recoveryGeneration`,
`supervisionGeneration`; cancels recovery, idle and event tickets; writes `wanted = false, userStopped = true`
(`commit()`); removes `restoreOwner` and the recipe; captures the owed drain. Then `awaitStopped` drains (SIGTERM → 3 s →
SIGKILL on exact identities, 5 s total). Entry points: This phone Stop, the status row's **Stop OpenCode**
(`BuiltinServerService` action `stop`), uninstall, and `stopServer(forPhoneEngineSetup)` (later).

### 6.8 Proofs (T3, PC emulator `OC_API37` + an API 35 image; unit W11-14)

| Proof | Steps | Pass |
|---|---|---|
| Update restore (extends memory `restore-proof-recipe`) | install the local-key Flutter 2.1.0+57 build; finish phone setup; start the server; **do not force-stop** (or run `pm unstop <pkg>` after any force stop: a stopped package never receives `MY_PACKAGE_REPLACED`); `adb install -r` the native release APK (versionCode ≥ 10000, same signer); poll `adb shell ps -A -o PID,PPID,NAME -u <app uid>` for `opencode2` every 2 s for 120 s | `opencode2` runs within 60 s; `dumpsys activity broadcasts history` shows the delivery; status shows `Running` at first open |
| Budget reset across updates | `adb root`; stop the app with `am force-stop`; edit `shared_prefs/builtin_server_recovery.xml` in place (sed to `/data/local/tmp`, `cat >` back to keep the owner) to `attempts=3`; `pm unstop`; install the next native build | server returns (R4) |
| Reboot restore | native app with the server running; `adb reboot`; wait for `sys.boot_completed=1` | server returns; no `dataSync` start attempted from `BOOT_COMPLETED` |
| Person stopped | Stop on This phone; install the next build; reboot | server stays stopped across both (R6) |
| Interrupted first launch | install over Flutter, then `am kill` during the gate wait (05 §10.5 interruption matrix) | the next start completes; server restored or auto-started at open |
| Force stop | `am force-stop`; open the app | widgets redrawn, schedules re-armed, server auto-starts once (R9) |

---

## 7. Process budget, the phantom limit and the Android 17 memory limiter

### 7.1 Facts the design bends around

- Android 12+ kills an app's child processes above **32**, oldest first; `max_phantom_processes = 32` is shared by
  **every app on the phone**, not per app [S agnostic-apollo, R memory `phantom-process-limit`]. On 2026-11-01 the
  owner's Claude Code helper got SIGKILL 137 twice with 5 parallel agents plus a build.
- Background phantom processes using too much CPU are also killed (check every 5 min).
- Android 14+ has the Developer-options toggle "Disable child process restrictions"
  (`settings_enable_monitor_phantom_procs`); turning Developer options off resets it. Android 17 QPR1 reportedly makes
  `DEVELOPMENT_SETTINGS_ENABLED` read as 0 for third-party apps [S] (U11-8).
- Only fork/exec children count. Threads, `:local_pdf` (registered with ActivityManager) and the app itself do not. One
  service = one proot ≈ 3 processes (proot, sh, program); the `oc-svd` supervisor costs 2; an agent session ≈ 4.
- Android 17's Memory Limiter puts a per-process cgroup v2 `memory.high` on apps (16 GB phone: 10 GiB visible, 5 GiB
  while only a foreground service keeps it alive; ≥ 18,432 MiB devices exempt). If forked children stay in the app's
  cgroup, the whole proot tree counts against one process (U11-3) [V mechanism, android-surfaces §5].

### 7.2 The native rules (port of the freeze-tip budget; nothing new)

| Rule | Value | Where |
|---|---|---|
| Count | `ProcessHost.appProcessCount()` = `/proc` entries owned by the app uid, minus the main process and AMS-registered app processes | `:android:linux` |
| Ceiling | 32 while Android's limit is on; 48 when the person turned it off (heat and battery still hold work) | `ProcessBudget.cap`, `liftedCap` |
| Reserve | 4 kept free for the tools of running work | `ProcessBudget.reserve` |
| Heavy jobs | agent session 4, AI Team 6 (later), setup job 3, Linux service 2 | `HeavyJob` |
| Admission | `ProcessBudget.decide`: heat first (`ThermalPolicy.hot` or `HeatTier.hold`), then battery (≤ 20 % unplugged), then room; the guard **waits**, never kills; waiting work starts by itself when room frees | `ProcessBudgetGuard` (`:engine:runtime`), called by `:engine:agents`, `SetupEngine`, `LinuxServices` |
| Warning | near limit from **28** (`PhoneProcessWatch.nearLimit`) on Running on this phone | 07 F-pages |
| Baseline | the quota collector, gen-UI servers and per-refresh sign-in probes do not idle-run (freeze tip) | `:engine:integrations`, `:engine:agents` |
| Kill line | a helper exit 137 reads "Android stopped this phone's agents because too many programs ran at once" + Resume; never "crashed" | `:ui:words` |
| Guided card | API 34+ only: one row that opens Developer options (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` through `OsSettingsPages`) with the steps; detects the state with `ChildProcessLimit.restrictionsOff` (Settings.Global flag, then `getprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs`); **never changes the setting** | `:feature:phone` row, `ProcessHost.childProcessLimit()` |
| Agent priority | `HeatTier.slow`/`hold` → `ProcessHost.setAgentPriority(Slow)` or `(Hold)` (Paseo helper trees to the background scheduling class / nice 19); nothing is signalled | `AgentPriority` |
| Services | at most 16 user services; names and ports validated (`LinuxServices`) | BUILTIN-LINUX-45 |

**Preview flavour trap:** the 32 is shared by all apps, and loopback ports too (S11-6). On the owner's phone, never run
the Flutter app's phone server and the `.native` preview's at the same time; the preview's This phone page says so in
one line while another OpenCode package has a server running (detected with `PackageManager` + a loopback probe of
4097).

### 7.3 Memory limiter readiness (Android 17)

1. `ExitClassifier` (`:engine:diagnostics`) maps `REASON_OTHER` whose description contains `MemoryLimiter:AnonSwap` to
   `ExitKind.MemoryLimit`. The description itself is never stored or forwarded (map T21).
2. Copy: "Android limited this phone's Linux to about 5 GB while OpenCode was in the background."
3. If U11-3 proves the proot tree is inside the app's cgroup, W11-04 adds a memory term to `ProcessBudget`: heavy work
   waits while the summed RSS of the app's tree is above 80 % of the not-visible tier for the phone's RAM (table in
   android-surfaces §5), only when the app is not visible. Until then, nothing changes.

### 7.4 Tests and proofs

- Ported: `process_budget_test`, `heat_tier_test`, `phone_process_watch_test` → `ProcessBudgetTest`,
  `HeatTierPolicyTest`, `PhoneProcessWatchTest` (commonTest); new `ChildProcessLimitTest` (the `restrictionsOffFrom`
  table: `""`/null → unknown, `"false"` → off, `"true"` → on).
- Emulator (T3): `adb shell device_config put activity_manager max_phantom_processes 8` [S], start two agent sessions:
  the guard holds the second with the waiting line; then `device_config delete activity_manager max_phantom_processes`.
- API 37 emulator (U11-3): `cat /proc/<proot child>/cgroup` vs the app's; `am memory-limiter manual <app pid> 1024`
  and a memory-heavy guest command; the exit is classified `MemoryLimit`.

---

## 8. Thermal and battery policy

### 8.1 Readings (`AndroidDeviceFacts`, `:android:platform`; CR11-5)

| Reading | API | Rule |
|---|---|---|
| Thermal status | `PowerManager.addThermalStatusListener` (29+) | one listener for the process; values `none, light, moderate, severe, critical, emergency, shutdown, unknown` |
| Headroom (30 s forecast) | `PowerManager.getThermalHeadroom(30)` (30+) | read at most every **30 s** and only while someone collects `thermalHeadroom` (NaN if asked faster than ~1/s; NaN or negative → null; map T22) |
| Battery | `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)`, `isCharging` | read when a policy decides, at most every 60 s; unknown → null (the battery then does not count) |
| Power save | `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` | feeds `HeatTierPolicy` and the kit's `MotionPolicy` Saver signal (06) |
| App visible | `ProcessLifecycleOwner` started/stopped | `HeatTierPolicy(hidden)`, reply watch (§9.3) |

### 8.2 Policies (common, numbers unchanged from the freeze tip)

| Policy | Rule |
|---|---|
| `HeatTierPolicy` | tiers `normal`, `slow` (agents at lower priority), `hold` (and new heavy work waits). Thresholds: `slowHeadroom = 0.7`, `holdHeadroom = 0.9`, `slowBattery = 20`, `holdBattery = 10` (unplugged), moderate status ⇒ hold; a step is left only after **2 minutes** clear (`calmFor`); `oc.fullSpeed` ("Always full speed", Keep running) returns `normal` |
| `ThermalPolicy` | SEVERE or headroom ≥ 0.95 → pause the AI Team; CRITICAL/EMERGENCY/SHUTDOWN → stop it (later, D6); resume only what the guard paused after ≤ MODERATE with headroom < 0.95 for 2 minutes |
| Services heat hold | at CRITICAL or worse the `hold` file stops every Linux service (wanted choices untouched), given back after 2 cool minutes; persisted as a file so it outlives the app (BUILTIN-LINUX-46) |
| Never | the OpenCode server is never stopped for heat |

### 8.3 Effects

| Tier / event | Agents | New heavy work | Linux services | Motion (06) | Notification |
|---|---|---|---|---|---|
| `normal` | normal priority | admitted | run | Full | — |
| `slow` | `AgentPriority.Slow` | admitted | run | Full | — |
| `hold` | `AgentPriority.Hold` | waits (`BudgetWait.warm` / `.battery`) | run | Calm | — |
| CRITICAL+ | hold | waits | held | Calm | one heat notice per pause and per resume, **only while the app is in the background**, on the existing channel `opencode_coding_status`, id `0x7E4A1`; nothing if that channel is silenced (ANDROID-PLATFORM-15) |

### 8.4 Battery exemption and keep-running advice

- The first phone-server start asks once for the battery-optimisation exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
  and the maker's auto-start page; concurrent starts claim the ask once; a refusal is remembered (B-ANDR-025).
- `KeepAliveSettings` (`:core:domain`, CR11-9; impl `AppLifecycle` parts): `info()` → manufacturer, brand, sdk,
  `batteryOptimizationIgnored`; `open(setting: Battery | Autostart | AppDetails)` tries the intents in turn and returns
  false if nothing opened (maker component lists copied verbatim from `AppLifecycle.autostartComponents`).
- `KeepAliveAdvice` (common, port of `keep_alive_advice.dart`): battery first everywhere; Nubia/RedMagic/ZTE
  `[battery, lock in Recents, app details, autostart]` (B-ANDR-007).

---

## 9. Foreground services and the dataSync cap

### 9.1 The matrix (every foreground use in the app; ADR §9.5)

| `ForegroundKind` | Service (FQCN frozen) | FGS type | Row | Legal start points | OS limit | Ends when |
|---|---|---|---|---|---|---|
| `LiveMode` ("Stay connected") | `BackgroundConnectionService` | `dataSync` | 4747, owner `live` | visible Settings toggle; Resume on the Pause tile / notification (user interaction); never from `BOOT_COMPLETED` | 6 h per 24 h in the background, shared by all `dataSync`, reset by opening the app | Pause, turn off, `onTimeout`, refused start |
| `ReplyWatch` (S11-4) | `BackgroundConnectionService` | `dataSync` | 4747, owner `reply` | `MainActivity.onStop` (transition from a visible state) | same budget | §9.3 |
| `SignIn` (CR11-3) | `BackgroundConnectionService` | `dataSync` | 4747, owner `signin` | visible sign-in screen; allowed during a durable pause (map T10) | same budget | sign-in ends or is cancelled; never restarted by itself |
| `WidgetAction` | `BackgroundConnectionService` | `shortService` alone on 34+; `dataSync` on 29–33; plain service on 26–28 | 4748 (generic copy, removed at settlement) | `PendingIntent.getForegroundService` straight from the widget tap (user-interaction exemption, map T20) | ~3 min (`shortService`); our lease 30 s | settlement or the 30 s lease; never demotes a live owner |
| `PhoneServer` | `BuiltinServerService` | `specialUse` | 4747, owner `server` | visible start (`startService`); background promise (`startForegroundService`) from `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` restore, `START_STICKY` reclaim | no platform timeout; Memory Limiter "not visible" tier (§7.3) | last guest service stops; Stop; `onTimeout` → `stopRuntime("systemTimeout")` |
| `Setup` | `SetupService` | `specialUse` | 4098 progress, 4099 result | visible setup screen | none | job ends; not sticky; `onTimeout` revokes setup work |

The two `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` values are copied verbatim: server "Runs the user's own coding-agent server
(OpenCode) inside the app, on this device only, while they use other apps."; setup "Installs the user's own coding
agent (OpenCode) inside the app, on this device only, and keeps the install going while they use other apps." [V
manifest]. Rejected types (android-surfaces §3.1): `systemExempted`, `connectedDevice`, user-initiated data transfer
jobs, and `mediaPlayback` until background read-aloud is wanted (D11-10).

### 9.2 `AndroidForegroundWork` (`:android:background`) and the promise discipline

```kotlin
@ContributesBinding(AppScope::class)
@Inject public class AndroidForegroundWork(
    private val app: Application,
    private val pause: BackgroundPauseStore,          // copied, SP oc_background_pause
    private val acks: ServiceAcks,                    // StateFlow set by the service bodies after startForeground
    private val visibility: AppVisibility,
    private val clock: Clock,
) : ForegroundWork {
    override val active: StateFlow<ImmutableSet<ForegroundKind>>
    /** Starts or joins the hosting service, then waits up to 5 s (50 ms steps, MainActivityBackground today) for
     *  the service's startForeground acknowledgement. Refused start → Failure.Refused(BackgroundLimit);
     *  receipt not saved → Failure.Storage; no acknowledgement → Failure.TimedOut. Never throws
     *  ForegroundServiceStartNotAllowedException (fix 84ec04d90). */
    override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket>
    override suspend fun end(ticket: ForegroundTicket)
}
```

Rules every service body keeps (H23, map T8, T9, T11):

1. **Promise first.** Every `onStartCommand` path, including malformed widget taps and unknown actions, calls
   `ServiceCompat.startForeground(service, id, notification, type)` [V androidx.core 1.19.1] before any other work.
   `promised`, `foregroundShown` and `stopPending` are tracked; a stop that arrives before the row shows waits for it.
2. **One row, many owners.** `StatusOwners` holds `{live, server, reply, signin}`; `end(owner)` returns true only for
   the last owner; otherwise the row is re-posted with what remains true and the leaving service uses
   `STOP_FOREGROUND_DETACH`.
3. **Both timeout callbacks.** `onTimeout(startId, fgsType)` (API 35+) and `onTimeout(startId)` (API 34
   `shortService`) exist in `BackgroundConnectionService`; both commit the pause receipt (`timeout()`) **before**
   stopping, post the "Paused · Android's background limit" row only if live mode was an owner, and call `stopSelf()`
   within seconds. A widget action in flight settles as `uncertain`.
4. **Never `dataSync` from `BOOT_COMPLETED`** (targetSdk 35+ rule). `BootPathTest` drives the boot and update bodies
   with a fake `ForegroundWork` and asserts `LiveMode`, `ReplyWatch` and `SignIn` are never begun there.
5. **A refused start is a pause, not a crash** (`refused`, `receiptUnsaved`, `ackTimeout` stay the reason codes).
6. **No unbounded lifetime** (AGENTS.md): every owner has an end condition in the table above; the work-lease rules of
   §5.4 apply under every service.
7. **Notice gate:** a service start whose words did not change does nothing; changed words go out at once if the last
   start was ≥ 5 s ago, else once after the remaining delay (`ServiceNoticeGate`, freeze tip; it cut 804 starts and
   3,015 row updates in 1 h 50 min, 755 of them refused by Android).

### 9.3 Reply watch (S11-4; ADR §9.5; amends B-ANDR-013, D11-2)

Starts, in `MainActivity.onStop` when `!isChangingConfigurations`, only if **all** hold:

- some live `ConnectionGraph` has a run active or a request pending;
- `NotificationPolicy.wouldAlert(connection, kinds = {FinishedReply, NeedsYou})` is true (permission granted, channel
  not `IMPORTANCE_NONE`, the person's `oc.notify.*` rules allow it for that server);
- `LiveMode` is not already active;
- no durable pause whose reason is the person's own Pause (`userPause`); other pause reasons (time limit, app updated)
  do not block it.

Stops when no run is active and no request is pending for **10 s** (time to post the finished alert after the turn
settles), when the app becomes visible, on `onTimeout`, or when the person taps Pause. It costs minutes of the 6 h
budget per long turn, and every app open resets the budget. The row shows the same "working" content as live mode.

### 9.4 Tests and proofs

- Ported (catalog): `background_live_test`, `background_pause_test`, `background_pause_native_test`,
  `background_pause_notice_test`, `background_signin_foreground_test`, `local_terminal_signin_foreground_test`,
  `background_work_test` → B-ANDR-001, -009, -012, -014..020, -081, -090.
- New: `BackgroundPausePolicyTest` (the `reconcile` table: batteryRestricted, userStopped, appUpdated, deviceRestarted,
  timeLimit, interrupted; map §13 "add one"), `ForegroundStartLegalityTest`, `ReplyWatchPolicyTest`, `BootPathTest`,
  Robolectric `ServiceController` tests for promise-before-stop on all three services.
- Emulator (T3): `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <pkg>` and
  `adb shell device_config put activity_manager data_sync_fgs_timeout_duration 60000` run the 6 h path in 60 s: the
  paused row appears, the receipt reads `timeLimit`, a new start is refused without a crash, and opening the app
  resets the budget [V android-surfaces §3.4].

---

## 10. Notifications

All posting goes through `AndroidNotificationSurface` (`:android:background`) behind `NotificationSurface`
(`:engine:attention`). Builders are `NotificationCompat` from androidx.core **1.19.1** [V Google Maven, AAR inspected:
`NotificationCompat.Builder.setRequestPromotedOngoing(boolean)`, `setShortCriticalText(String)`,
`NotificationCompat.ProgressStyle` with `Segment(int)`/`Point`, `NotificationCompat.MetricStyle`,
`NotificationManagerCompat.canPostPromotedNotifications()`, `ServiceCompat.startForeground(Service, int, Notification,
int)`].

```kotlin
// :engine:attention commonMain
public interface NotificationSurface {
    public suspend fun showStatus(content: StatusContent, owner: StatusOwner): Outcome<Unit>      // throttled 1 per 2 s, trailing post
    public suspend fun endStatus(owner: StatusOwner): Boolean                                     // true = last owner left
    public suspend fun showAlert(alert: CodingAlert): Outcome<AlertShown>                         // Shown | Blocked(channelOff | permission)
    public suspend fun dismissAlert(key: AlertKey): Outcome<Unit>                                 // refreshes both group summaries
    public suspend fun showScheduledRun(result: ScheduledRunNotice): Outcome<Unit>
    public suspend fun heat(notice: HeatNotice): Boolean                                          // false if channel missing or silenced
    public suspend fun ensureChannels(): ChannelsReport                                           // H3: create missing ids only
    public suspend fun relocalize(): Outcome<Unit>                                                // rename channels in place, re-post visible rows
    public fun permission(): NotificationPermissionState                                         // granted, channelImportance per id, canPromote
}
```

### 10.1 Channels (ids frozen; the person's per-channel choices survive only on identical ids)

| Channel id | Importance | Used by | Name key (`strings_os`) |
|---|---|---|---|
| `opencode_live_connection` | LOW, no badge | status row 4747, widget-action row 4748 | existing key, unchanged |
| `opencode_coding_action` | HIGH | requests (permission, question, team decision) | unchanged |
| `opencode_coding_action_quiet` | LOW | the same requests during quiet hours (no sound, no heads-up) | unchanged |
| `opencode_coding_status` | DEFAULT | finished, stopped, quota, check-in, test; heat notice | unchanged |
| `opencode_team_progress` | LOW | AI Team progress (later, D6); the channel is kept if it exists, never deleted | unchanged |
| `opencode_phone_setup` | LOW | setup 4098/4099 | unchanged |
| `opencode_builtin_server` | LOW | idle-return 4100 | unchanged |
| `opencode_scheduled_prompts` | DEFAULT | scheduled-run results | unchanged |
| `opencode_voice_setup` | LOW | voice-model download (10) | unchanged |
| `bb9_qa_producer` | LOW | QA builds only; never created in `stable` or `preview` release builds | — |

Rules: channels are never deleted or recreated with another importance; `ensureChannels()` (first-launch hook H3, 05
§7.9, and every process start) creates only missing ids of features this build ships (so `opencode_team_progress` is
not created before D6, and an existing one is left alone); `relocalize()` renames channels in place (copied
`NativeNotificationLocale`), which keeps the person's settings.

### 10.2 Notification ids (frozen; a Flutter-posted notification is dismissed by the native app with the same id)

| Id | What |
|---|---|
| 4747 | the one status row (live, server, reply watch, sign-in) |
| 4748 | transient widget-action row |
| 4098 / 4099 | setup progress / setup result (4099 cleared when the status row starts) |
| 4100 | "Paused · nothing was running" |
| `6000 + (key.hashCode() & 0x0fffffff)` | alerts. `key` = the dedupe key (`input:<profile>:<session>`, `status:<profile>:<session>`, `team:<profile>:gate:<gate>`, `team:<profile>:run:<run>`). the expression is copied verbatim, so ids match Flutter-era alerts |
| 5998 / 5999 | group summaries `opencode_needs_you` / `opencode_finished`, only with ≥ 2 children |
| tag = work name, id 0 | scheduled-run results (own group key per run, map T13) |
| `0x701CE` | voice-model download (10) |
| `0x7E4A1` | heat notice |

### 10.3 The status row (ANDROID-PLATFORM-05)

- **Model** (`StatusModel`, `:engine:attention`, port of `buildNotificationStatus`, 511 lines + tests): situations,
  highest wins: `needs_you` → `paused_android` → `reconnecting` → `working` / `several_working` (at most 2 inbox lines
  then "+N more") → `paused_hot` → `waiting` (provider retry: "Tries again at 1:12") → `paused_idle` → `setup` →
  `idle/ready`. The server name appears only when more than one server is live (B-ANDR-047). Task progress bar only
  with real tasks, never an endless spinner (B-ANDR-042).
- **Validation** (`StatusContent`, ported pure from `StatusNotificationModel`): title, text and line ≤ 240 chars, ≤ 4
  lines, ≤ 3 actions, progress 0..100; a malformed content is refused so a bad update never replaces a good one.
  `StatusKind` and `StatusAction` wire names unchanged (`open`, `stop_reply`, `start`, `new_chat`, `stop_server`).
- **Throttle**: one post per **2,000 ms**, only on changed content, trailing post of the newest (Android drops > ~5/s;
  map T12). The ViewModel-side debounce of the Flutter app disappears (one throttle, in the surface).
- **Actions**: Open / Start / New chat are activity `PendingIntent`s (request codes 0/10/11/12; New chat carries
  `oc.shortcut=new_task`); **Stop the reply** is a broadcast to `StatusActionReceiver` with
  `opencode://status/stop_reply`; **Stop OpenCode** is `BuiltinServerService.stopIntent` (`getService`, action
  `stop`). Every action names its target in its content description ("Stop the reply in <chat>").
- **Lock screen**: `VISIBILITY_PRIVATE` with a count-only public version (map T3).
- **Dismissal** (S11-9): `setDeleteIntent` → the run id is remembered; that run is not promoted again.

### 10.4 Alerts ("moments", ANDROID-PLATFORM-06)

| Topic | Rule (ported unchanged) |
|---|---|
| Kinds | `permission, question, complete, error, quota, quota_reset, checkin, test` at the switch; `team_*` kinds later (D6) |
| Validity | non-blank `sessionID` and `key`; quota kinds need `sessionID == "quota"`, a profile id and a 64-hex `monitorToken`; agent name only if `^[A-Za-z0-9 .-]{1,32}$`, else "OpenCode" |
| When posted | (live mode **or** reply watch active, D11-2) ∧ app backgrounded ∧ permission granted ∧ the server's alerts on. Quiet hours do not stop requests and failures (they go to the quiet channel); finished replies wait for the morning. Finished alerts skip sub-agent sessions. A failed `shown` rolls back the dedupe so a later attempt can retry. Opening a chat or answering anywhere dismisses (B-ANDR-011, -039, -040) |
| Copy | shade: the person's words, masked (`NotificationText` common port of `notification_text.dart`: `redactForNotification`, `commandHead`, `permissionAskOf`, `notificationOptionsOf`, `stoppedReasonToken`, `firstReplyLine`); lock screen: fixed public words. Error alerts use a reason token (`auth, context, length, filter, model, other`), never error text (B-SEC-105, -106) |
| Buttons | at most two. **Allow once** + **Reject** only when the exact command or file is readable in the body; Allow once has `setAuthenticationRequired(true)` (API 31+, phone unlocked); **Always allow never**; everything else has **Open** only. Questions with ≤ 3 short options (≤ 24 chars, unique) show them as buttons (B-ANDR-038). Legacy RemoteInput Reply and `option*` decisions from Flutter-era alerts are still accepted (07 §7.13) |
| Groups | `opencode_needs_you` (permission, question) and `opencode_finished` (complete); summaries 5998/5999 (InboxStyle, ≤ 5 lines) only with ≥ 2 children; every kind has an explicit group key (Android 16 forced auto-grouping, android-surfaces §2.3) |
| Timeouts | `complete` `setTimeoutAfter(1 h)`; `setOnlyAlertOnce` except quota kinds |
| First reply line | fetched with a 2 s timeout; never ends on a colon (joins a list intro with its first item, B-ANDR-048) |

### 10.5 Live Updates (promoted ongoing) policy

| Status kind | Promote? | At the switch |
|---|---|---|
| `working`, `several_working` (runs the person started) | yes | `setRequestPromotedOngoing(true)` + `setShortCriticalText(chip)` (plurals `native_chip_running`, ≤ 7 characters recommended, checked in Arabic at the 96 dp cap) |
| `needs_you` inside a running turn | yes | chip `native_chip_pending` ("Needs you") |
| `reconnecting`, `waiting` while the turn is still active | no at the switch (parity) | candidate after the switch |
| `idle/ready`, `paused_*`, `setup` | **never** (Google: not for "quick access to app features") | — |
| finished, check-in, quota alerts | **never** ("alerts and messages" are not Live Updates) | — |

- The Flutter code gates the platform calls on `SDK_INT_FULL >= BAKLAVA_1` (commit `3030fb0b7`); the native code calls
  the Compat methods unconditionally and lets androidx.core decide. Proof U11-10: Robolectric at SDK 36 and an API
  36.0 emulator post a promoted row without `NoSuchMethodError`.
- Requirements kept: `POST_PROMOTED_NOTIFICATIONS` declared (B-ANDR-034), ongoing, a content title, no custom views,
  not colorized, not a group summary, channel above `IMPORTANCE_MIN`.
- `ProgressStyle` (segments per todo, tracker icon) and `MetricStyle` (elapsed, tasks) come after the switch (D11-5);
  until then the standard style with `setProgress` for a real fraction, exactly as today.
- `canPostPromotedNotifications() == false`: nothing is shown at the switch (parity); a visible row that opens
  `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` is D11-6.
- Whether ZTE MyOS promotes at all is U11-2 (read-only adb `dumpsys notification` on the owner's phone).

### 10.6 Actions and receivers (ANDROID-PLATFORM-07)

- `CodingActionReceiver` and `StatusActionReceiver` call `NotificationActionBody` directly (no Dart hop). A decision
  is bound to the **exact** `requestID`; an empty or stale id refreshes the alert instead of answering another request
  (B-AGENT-031, B-SEC-183). A permission is answered through the live connection of that profile.
- No live connection for that profile, or the answer failed: **repost the alert if it is gone from the shade**, then
  open the app at the exact request (`EntryRequest.AlertOpen`); parity with today. Answering from a cold process through
  `ConnectionRegistry.oneShot` (CR11-6) is a candidate after the switch.
- A notification Allow and an in-app tap racing send exactly one reply (B-CHAT-074); answering a request the server
  already timed out dismisses quietly (B-CHAT-076).
- Every `PendingIntent` is `FLAG_IMMUTABLE`; request codes `notificationID * 4 + index + 1`; per-decision data URIs
  `opencode://coding-alert/<key>/<decision>` so extras never merge (map T17).

### 10.7 Language

Per render, copy is resolved through `Words` with the stored `oc.appLocale` (S11-8); all seven app languages ship
natively (D11-4, fixes map T16). After a language write is acknowledged (B-L10N-002), `relocalize()` renames channels
in place, re-posts visible rows, and `WidgetSurface.redrawAll()` runs. Mechanism proof: ADR P6 (a notification switches
language at runtime on API 33 and API 30).

### 10.8 Permission

`POST_NOTIFICATIONS` (API 33+) is asked only in context through `PermissionGate.request(Notifications)`: turning on
"Stay connected", the one-time offer after a first finished reply (B-ANDR-023, -024), the needs-you preset (B-ANDR-026),
or "Send a test notification". The test notification posts on `opencode_coding_action` and reports
`Blocked(channelOff | permission)` in plain words when the OS refuses (P0.6). Never asked at start-up.

---

## 11. Home-screen widgets

### 11.1 Decision: RemoteViews at the switch (ADR §3.3, binding)

Keep the RemoteViews providers, layouts, binders, size policy and `previewImage` PNGs, ported as-is, with every
provider class name unchanged. Glance is not used before the switch (NG13), and the widget redesign the owner asked
for ("widgets are redone in the native rewrite", freeze decision 2) is an after-switch slice.

Why, in one line each: Glance's `SizeMode.Responsive` hands the launcher a size-keyed map, which is exactly what the
owner's ZTE MiFavor launcher mis-resolves (it drew a strip inside a 4×2, fix `851f9cc70`); whether `SizeMode.Exact`
emits a map on Android 12+ is undocumented (U11-6); generated previews on ZTE are unknown (U11-7); and the 4.3k lines
plus their Robolectric render tests already work.

Glance re-evaluation gate (all must pass before the redesign may use it): G1 a Glance 4×2 draws correctly on the
owner's launcher with `SizeMode.Exact` or `Single` + self-computed size; G2 the picker shows something useful on ZTE
(previewImage or generated preview); G3 `runGlanceAppWidgetUnitTest` covers what the Robolectric render tests cover
today; G4 48 dp targets, RTL mirroring and the frame-text cap hold; G5 the owner picked the concept image
(`judge-visuals-hard`). Versions today: Glance 1.2.0 stable, 1.3.0-alpha02 newest [V Google Maven 2026-11-01].

### 11.2 Providers

| Picker | Provider | Size | Configure |
|---|---|---|---|
| shown | `SessionsWideWidgetProvider` "Conversations" | 4×2 (min 340×110 dp, resize h/v) | reconfigurable: Edit scopes to one project |
| shown | `StatusWidgetProvider` "Status" | 2×2 (120×110 dp) | none (style via the overlay) |
| hidden (`hide_from_picker`), still drawn | `SessionsWidgetProvider` 2×2, `SessionsListWidgetProvider` 4×4, `SessionsStripWidgetProvider` 4×1, `UsageWidgetProvider` 2×2, `UsageWideWidgetProvider` 4×2 | — | — |

All have `updatePeriodMillis = 0` (event-driven only) and `widgetCategory="home_screen"` (no lock-screen widgets,
D11-11). Every redraw walks the registry lists (`WidgetProviders`): sessions providers, then Status; usage, then
Status.

### 11.3 Data and code paths natively

| Piece | Today | Native | Owner |
|---|---|---|---|
| Snapshot `oc.widgetSessions` v2 | Dart `widget_snapshot.dart` (651) | `WidgetSnapshotBuilder` (`:engine:attention`): same key, same JSON fields, same limits (≤ 12 sessions, title ≤ 80 cp, preview ≤ 240, `<think>` removed, bidi and control characters stripped, generic project names hidden); `oc.widgetTarget.<p>` written **first** with the byte-exact Dart `jsonEncode` fingerprint (05 §2.5); serialized writes; unchanged bytes skipped | W11-10 |
| Usage feed | Dart `usage_widget_snapshot.dart` (425) | `UsageWidgetSnapshot` (`:engine:attention`): `oc.widgetUsageSubs` v1, `oc.widgetUsage.<p>`, `oc.usageWidget.v1`; never accounts, tokens, addresses, plan names, raw errors (B-SEC-179) | W11-10 |
| Attention tile `oc.attentionTile` | Dart | `TileSnapshot` (`:engine:attention`) | W11-09 |
| Reading in providers | parse SP JSON on every render | `WidgetSnapshotCache` (`:android:widgets`): the parsed snapshot plus a version stamp in memory while the process lives; prefs (after the gate) otherwise; redraw only when bytes changed | W11-10 |
| Rendering | copied binders | unchanged; copy from `Words` (S11-8); colours from `WidgetGraphite` + `:ui:design` roles | W11-10 |
| Actions (Allow once, Reject, Stop, Steer, Voice, Approve plan, Resume) | native admission + headless Dart runner | `WidgetActionAdmission` (copied) → `WidgetActionRunner` (`:engine:attention`, port of `widget_action_runner.dart` 530 lines) inside the `WidgetAction` foreground service | W11-10 |
| Follow-up checks | `WidgetStatusWorker` + headless Dart | same worker class, body runs `WidgetStatusRunner` (common port) | W11-10 |
| Overlay panels | transparent FlutterActivity | `WidgetOverlayActivity` (Compose) + `:feature:widget-panels` + `WidgetOverlayPort` | W11-11 |

Where the ADR's "widget-action runner" lives: the admission, the claim ledger and the 30 s lease are in `:android:widgets`; the foreground service that hosts the work is `BackgroundConnectionService` (`:android:background`, begun through `ForegroundWork`); the server calls are in `WidgetActionRunner` (`:engine:attention`), because `:android:*` modules may not see `:protocol:*` (ADR §6.1).

Widget-action rules (ANDROID-PLATFORM-26, map T4, T5; unchanged):

1. Identity = `{profileID, scopeToken, sessionID, requestID, runID, gateID, lastProjectID, expectedStartedAt}`; action
   id = SHA-256 over action + identity (text submissions use a fresh UUID).
2. `WidgetActionPolicy.validate` re-checks the payload against the current snapshot (stale → `staleRequest`); refused
   while the keyguard is locked; Allow only when the exact command is shown whole.
3. Meet the foreground contract first (`ForegroundWork.begin(WidgetAction)`), never demote a live owner.
4. **Claim before the network**: append the id to `oc.widgetActions.<p>` (last 128) with `commit()`; an id already
   present → `busy`; an old claim cannot prove delivery failed, so it reads `uncertain`. Only a definite `rejected`
   releases the claim.
5. Lease **30 s** total (monotonic watchdog); a second tap never extends it.
6. Runner: re-read durable ownership before dispatch and after settlement; gateway from the live connection if that
   profile is live, else `ConnectionRegistry.oneShot(profile, WidgetAction)` (CR11-6; refuses Codex/Paseo socket
   approvals, transient transports, password re-entry, unconfirmed cleartext; pin only when the pin origin matches);
   `health()` first; exact pending permission; capture the run marker; reply `once`/`reject` (never always); Stop
   needs `busy`, matching `startedAt`, run marker equal to the server's latest user-message id and a fresh busy
   status; Steer uses steer delivery where supported (B-AGENT-317); then poll status up to 5 s at 250 ms.
7. Result `{status: accepted | rejected | uncertain, error ∈ invalidRequest | staleRequest | unavailable | unsupported |
   busy | uncertainDelivery}`; `WidgetActionSnapshot.apply` patches only reviewed fields; a concurrent foreground
   update wins; accepted results leave no banner.
8. After an accepted action: three one-off follow-up checks at 30 s, 90 s, 480 s (unique work
   `widget-status-<profile>-<session>`, `REPLACE`, tag `widget-status-profile-<profile>`), each a 15 s read-only check;
   a restarted worker never repeats a read (`runAttemptCount == 0`).

Overlay (ANDROID-PLATFORM-25): `WidgetConfigureActivity`/`WidgetRouteActivity` check `ownsWidget` and
`WIDGET_OVERLAY_READY`; a configure with no snapshot yet opens with an empty identity and saves under the `_device` key
(the launcher drops a widget whose configure is cancelled; map T15). `WidgetOverlayActivity` keeps `FLAG_SECURE`, reads
display preferences only (never `SecretStore`), dictates through the system `RecognizerIntent` (60 s, language-tag
checked, ≤ 8,000 chars), and allows one submission in flight with a `submissionId` that survives recreation.

```kotlin
// :engine:attention commonMain (CR11-7)
public interface WidgetSurface {
    public suspend fun publish(snapshot: WidgetSnapshot, target: WidgetTarget): Outcome<PublishResult>   // Unchanged | Written
    public suspend fun redrawAll(reason: RedrawReason): Outcome<Unit>    // Start, Locale, Style, PackageReplaced, ForceStopped, Boot
    public suspend fun invalidateConnection(lastSeenAt: Instant): Outcome<Unit>   // connected = false, titles kept
    public suspend fun clearProfile(profile: ProfileId): Outcome<ClearOutcome>    // NothingToClear | Cleared | Failed
}
public interface WidgetOverlayPort {
    public fun launchArguments(): WidgetLaunchArguments
    public suspend fun saveStyle(background: WidgetBackground, accent: WidgetAccent): Outcome<Unit>
    public suspend fun saveProject(project: String): Outcome<Unit>      // must be in the published list, or "" for all
    public suspend fun dictate(languageTag: String?): Outcome<String?>
    public suspend fun submit(text: String): Outcome<WidgetActionResult>
    public fun close()
}
```

### 11.4 The owner's launcher (ZTE MiFavor, `com.zte.mifavor.launcher`; map T14, memory `zte-launcher-widget-traps`)

1. It ignores `android:previewLayout`: every provider keeps `android:previewImage` (7 PNGs in `drawable-nodpi` and 7 in
   `drawable-night-nodpi`), regenerated from the Robolectric render tests whenever a layout changes.
2. It mis-resolves size-keyed `RemoteViews(Map<SizeF, RemoteViews>)`: render **one** RemoteViews for
   `WidgetSizePolicy.currentSize` (portrait → tallest entry, landscape → widest); the preset map is used only when the
   host reports no sizes. Launcher sizes are untrusted (1..2000 dp, ≤ 16 variants).
3. Effective height = height / max(fontScale, 1); frame text (header, buttons) capped at 1.3× through dp sizes; row
   text follows the system scale (owner rule `text-scale-cap`).
4. A stopped chronometer's spoken words cannot be customised (`d060c2e83` was reverted); timers tick only while the
   connection is live.
5. `dumpsys appwidget` does not print sizes; a home-screen read on the owner's phone needs their OK each time. Visual
   checks on their phone are their photo, never an adb screencap (memory `no-owner-phone-screenshots`).

### 11.5 Platform hygiene (no visible change)

- Since Android 15 a force stop cancels every PendingIntent and greys the widgets until the next launch:
  `WidgetSurface.redrawAll(ForceStopped)` runs at the first start after `ApplicationStartInfo.wasForceStopped()`
  (API 35+) or a force-stop exit reason (below 35) (§15).
- No bitmaps in RemoteViews (Android 17 at targetSdk 37 rejects more than `6 × w × h` bytes; partial updates keep old
  images alive): `WidgetBitmapBudgetTest` renders every provider at its largest declared size under Robolectric and
  asserts no `Bitmap` action is present.
- Widget broadcast PendingIntents add `FLAG_RECEIVER_FOREGROUND`; providers use `goAsync` and finish within 10 s,
  handing longer work to WorkManager.
- `AppWidgetManager.setWidgetPreview` (API 35+) comes with the redesign, not before (NG13).
- Accessibility: 48 dp targets; content descriptions name the target (`widget_allow_for`, `widget_reply_for`,
  `widget_step_for`); layouts mirror for Arabic; titles wrapped in bidi isolates (map R10).

### 11.6 Tests

Copied Robolectric render tests (`WidgetCardRenderTest`, `TwoWidgetsRenderTest`, `WidgetSessionCardLayoutTest`,
`WidgetConversationPresentationTest`) and policy tests; ported Dart suites `widget_snapshot_test`,
`widget_action_runner_test`, `widget_action_runner_http_test`, `widget_status_runner_test`,
`widget_status_runner_http_test`, `widget_v2_contract_test`, `usage_widget_snapshot_test`,
`usage_widget_settings_test`, `usage_widget_claude_home_test`, `attention_tile_snapshot_test` (rows B-ANDR-010, -022,
-095, -099..121, B-SEC-179..183, B-AGENT-317); new `ConversationsWidgetPlanTest`, `WidgetBitmapBudgetTest`. Device:
E2E-26 on the emulator; before the switch APK, the owner places both widgets on the ZTE launcher and sends one photo
each (A11-8).

---

## 12. Scheduled prompts

### 12.1 What moves where

| Piece | Home | Rule |
|---|---|---|
| Model, recurrence, due rules | `:engine:schedule` common | **one** implementation (map risk R3) on kotlinx-datetime: `once(at)`, `daily(h, m)` and `weekdays(h, m)` built by calendar (wall clock survives DST), `interval(hours 1..168, anchor)` on an absolute UTC anchor; late = > 15 min after due; all missed occurrences collapse into one catch-up or one skip; occurrence id `id:revision:dueAtMs`; digest at h:m with a `lastDeliveredAt` claim |
| Store | `:engine:schedule` common over `KeyValueStore` (Room only if the ADR §9.8 trigger fires) | envelope v1 at `oc.scheduledPrompts.<p>` (≤ 100 schedules, ≤ 500 runs, ≤ 4 MiB) and companions `oc.scheduleIndex.<p>`, `oc.scheduleClaims.<p>` (≤ 256), `oc.scheduleReceipts.<p>` (≤ 256), `oc.scheduleDeleted.<p>`, `oc.schedulePhoneLease.<p>`; compare-and-set on one per-profile `WriterLane` (replaces the JVM lock and the 20 optimistic retries); reads and writes refused for a profile missing from `oc.profiles` or tombstoned; corrupt or unknown schema → `storage_corrupt`, never empty success; delete removes history but claims stay fenced until profile deletion; fixed error codes (`schedule_limit, stale_schedule, schedule_missing, schedule_expired, storage_limit, invalid_run_transition, history_limit, invalid_profile, unsupported`) |
| Runner | `:engine:schedule` common (`ScheduledRunner`, port of `runner.dart` 378 + `entrypoint.dart` 258) | deadline-aware; `guard()` before and after every read; profile fingerprint (byte-exact Dart `jsonEncode`, 05 §2.5) must equal the saved one; gateway from `ConnectionRegistry.oneShot(profile, ScheduledRun)` (CR11-6); 90 s health loop only when this run started the phone server; `capabilities.scheduledPrompts` else `unsupported`; existing session must be in `projectPath` (`wrong_project`); route persisted **before** dispatch; busy → `skipped`; a create counts as dispatched and is never repeated after an ambiguous answer; agent and model applied by field patches with an idle re-check before each; `promptAsync`; follow at 2 s until a new assistant reply is done → `ran` (+ title and ≤ 100-char line), `agent_failed`, or `needs_you`; `uncertain_delivery`, `timeout`, `auth_required`, `certificate_changed`, `certificate_invalid`, `cleartext_unconfirmed`, `unavailable` |
| Never | — | a scheduled run never queues, steers, interrupts or auto-approves (map T6) |
| Arming | `:android:schedule` `AndroidScheduleHost` | below |
| UI | `:feature:schedules` (07 K01) | — |

```kotlin
// :engine:schedule commonMain
public interface ScheduleHost {
    /** Re-derives the index from the latest envelope (never from a UI payload), cancels removed entries, arms current. */
    public suspend fun replace(profile: ProfileId, index: List<ArmedOccurrence>, digestAt: Instant?): Outcome<Unit>
    public suspend fun cancelProfile(profile: ProfileId): Outcome<Unit>
    public suspend fun restoreProfile(profile: ProfileId): Outcome<Unit>          // aborted deletion
    public suspend fun runNow(profile: ProfileId, schedule: ScheduleId, revision: Long): Outcome<Unit>
    public fun status(): ScheduleHostStatus   // supported, exactAllowed, batteryExempt, notificationsAllowed
}
```

### 12.2 Arming on Android (ANDROID-PLATFORM-37, unchanged)

1. For each current occurrence: WorkManager one-time work, unique name `oc.schedule.<p>.<occurrence>.fallback`,
   `ExistingWorkPolicy.KEEP`, initial delay = due − now, tag `oc.schedule.<p>`, `Data` `{profileId, scheduleId,
   revision, scheduledAtMs}`.
2. And, when `AlarmManager.canScheduleExactAlarms()` (always true below API 31) and the due time is in the future:
   `setExactAndAllowWhileIdle(RTC_WAKEUP, due, PendingIntent.getBroadcast(…, explicit ScheduledPromptReceiver, action
   …SCHEDULED_PROMPT, data oc-schedule://<p>/<occurrence>, FLAG_IMMUTABLE))`.
3. The alarm's receiver enqueues `….alarm`, **expedited** on API 31+ with
   `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`, only for a real alarm broadcast with zero delay (never for a
   restore or the digest). Alarm and fallback exclude each other through the durable claim, never by cancelling a
   running worker.
4. Receiver actions: `FIRE` → enqueue from the alarm; `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, exact-alarm permission
   change → `rearmAll`; `TIME_SET`, `TIMEZONE_CHANGED` → `rearmAll(rebaseCalendar = true)` (future daily/weekday
   occurrences recomputed, overdue ones kept for catch-up/skip; map T29). Orphans whose profile is gone are cancelled.
5. Exact-alarm access is asked only from an explicit tap on the Schedules page (`PermissionGate.request(ExactAlarms)`
   opens `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` for our package). Without it Android runs schedules
   approximately; a force stop prevents runs until the app opens.
6. WorkManager initializes on demand (`OcApplication : Configuration.Provider`; the startup initializer removed;
   05 §7.1), with the default `WorkerFactory` (workers keep the `(Context, WorkerParameters)` constructor).

### 12.3 The worker (ANDROID-PLATFORM-38)

`ScheduledPromptWorker` (shell, `CoroutineWorker`) → `ScheduledRunWorkerBody`: one global `Mutex` (today
`Semaphore(1)`); worker budget **480 s**, runner budget **420 s**, minimum runner window **15 s** (`Result.retry()`
only **before** the claim). Steps: not due yet (clock moved back) → re-arm with `REPLACE` and stop; `claim(entry)` →
`new | recover | duplicate | stale` (`recover` writes the interrupted history only, never sends); run; a `not_due`
answer while the entry is still in the future un-claims a fresh attempt and re-arms; `settle` (claim finished; a still
current occurrence gets an `interrupted` failure and a receipt); post the notification. Never `Result.retry()` after the
claim (a transport failure can follow an accepted delivery). **Run now** (`….manual`, `Data.manual = true`): same
worker and runner, no claim, never moves `nextRunAt`.

Phone server for a run (ANDROID-PLATFORM-40, BUILTIN-LINUX-27): when the profile's server is the managed in-app one,
`PhoneServerHost.acquireScheduledServer(profile, password, deadline)`: requires an installed runtime, no install, no
other lease, and a password equal to `/root/.oc-builtin/server.password`; an already running server is used and not
adopted; otherwise `opencode2 serve` wrapped in `timeout --signal=TERM --kill-after=5s <budget>s`, the admission marker
committed before launch, a watchdog thread. The lease never starts `BuiltinServerService` and never arms restart
policy; the worker keeps the process alive. While a lease is active, interactive sends are blocked (B-ANDR-070).

### 12.4 Run notifications

Channel `opencode_scheduled_prompts`; tag = work name, id 0, **own group key per run** (map T13); title = conversation
title (≤ 80, secret-like tokens replaced by `…`) for a finished run, generic otherwise; the lock screen is always
generic; body = first reply line (≤ 100) or "Started", skipped, or a plain reason from the code (`needs_you`,
`auth_required`, `agent_failed`, `timeout`/`uncertain_delivery`/`interrupted`, else unavailable); quiet skips
(`stale_target`, `not_due`, `disabled`) post nothing; the digest posts counts only when > 0. Tap → `MainActivity` with
data `oc-schedule-open://<p>/<occurrence>` and extras `scheduledProfileId`, `scheduledPromptId`,
`scheduledSessionId` (routing: 07 §7.9).

### 12.5 Release and migration facts

- WorkManager upgrades its own database (2.10.5 → **2.12.0** [V]); worker FQCNs, unique names, tags and `Data` keys
  are frozen (§2.5); H2 re-arms every profile (idempotent) (05 §7.9).
- R8: keep the two worker constructors and `androidx.work.impl.WorkDatabase_Impl()` explicitly (`keepRules/work.keep`;
  APK 900055 start-up crash, map T27) until a T2 run without them proves WorkManager's own consumer rules suffice; then
  delete them in a commit that names that run.
- Tests: the 9 Dart suites (`scheduled_prompt_{models,store,controller,entrypoint,runner,open_route,send_admission,
  standard}_test`; `…screens_test` is a widget test, later) and the 3 Kotlin classes → rows B-STATE-089..097,
  B-AGENT-215..219, B-NAV-054, -055, B-SEC-152, B-L10N-014, B-ANDR-070. New `RecurrenceGoldenTest`: every kind across
  2026 DST changes in Europe/London (03-29, 10-25), America/New_York (03-08, 11-01), Australia/Lord_Howe (30-minute
  shift), Africa/Cairo (DST reintroduced 2023), Asia/Dubai (no DST, the owner's zone); the Flutter outputs for the same
  inputs are the expected values. Device: E2E-27.

---

## 13. AppFunctions (after the switch; 02 §3.5, D16)

Facts [V 2026-11-01]: `androidx.appfunctions:appfunctions:1.0.0-beta01` (2026-10-21) with
`appfunctions-compiler` (KSP) and `appfunctions-testing`; annotations `@AppFunctionServiceEntryPoint(serviceName,
appFunctionXmlFileName)`, `@AppFunctionDeclaration(isDescribedByKDoc, isEnabled)`, `@AppFunctionAccessLevel(level,
isCompatEnforcementEnabled)`, base class `AppFunctionService` (AAR classes inspected). Service binding needs Android
16 (API 36). Callers need `EXECUTE_APP_FUNCTIONS` (system or approved agents only); Gemini's use is a private preview.
On the branch `feat/appfunctions-2026-10-30`, registration is proven on an API 37 emulator, execution is not.

Plan (unit W11-18, after the switch):

1. Code in `:android:platform`, package `io.github.eslamasabry.opencode_mobile.appfunctions`, so the KSP-generated
   service keeps the branch's FQCN `…appfunctions.OcAppFunctionService` and XML `oc_app_function_service`.
2. The five functions call `AssistantActions` (`:engine:entry`, common), never a headless engine:
   `startConversation(project?, agent?, text)`, `listActive()`, `answerRequest(conversationId, allow)` (Allow once or
   Reject only, the exact pending request, never what is not readable; map T4), `stopConversation(id)`,
   `sendMessage(id, text)`: steers a running turn where the server supports steering; with a turn running and no
   steering, it refuses in plain words ("This server can't take a message mid-reply; try when it finishes") and never
   queues (owner rule `steer-not-queue`); idle → a normal send.
3. Results carry ids, titles and project names only; never passwords, tokens, addresses or paths. Failures map to
   `AppFunctionInvalidArgumentException`, `AppFunctionElementNotFoundException`, `AppFunctionDeniedException` and
   `AppFunctionAppUnknownException`, with plain-word messages (the branch's mapping, copied).
4. A call wakes a cold process: graph, migration gate, then the live connection or `oneShot(profile, AssistantCall)`.
   Starting the phone server from a call only after U11-9 proves the foreground start is legal there; until then
   `startConversation` on the in-app server requires it to be running.
5. The library version sits behind one catalog entry; the module compiles the service only with
   `-Poc.appfunctions=true` until W11-18 lands; `PlatformCapabilities.appFunctions = SDK_INT >= 36 && flag`.
6. Proof: `adb shell cmd app_function list-app-functions`, then execution of each function on an API 36/37 emulator
   started with `-gpu host` and enough free RAM (memory `appfunctions-plan`), then on the owner's Android 17 phone when
   they have it. Exact `cmd app_function` execute syntax: copy from the branch's QA README [U].

---

## 14. Intents and launcher surfaces

The routing of every entry (where a tap lands, what waits, what is consumed once) is 07 §7. This section owns the
Android producers and consumers.

### 14.1 Capture

`MainActivity` hands every intent to `EntryIntentReader` (`:android:platform`), which reuses the Flutter-era validators
unchanged: `SessionLinkIngress.accepts` (non-empty, ≤ 1,024 chars, every char `0x21..0x7e`, hosts `session` and `team`
only; rejected links are consumed too), the `LaunchShortcutChannel` whitelist (`connect, new_task, activity, usage,
usage_settings, background_settings, phone_setup, phone_setup_done, linux`) and the `usage_target` regex, and
`ScheduledPromptPolicy.validId`. Our extras are stripped and the intent reset to `MAIN` after capture, so rotation or
recreation never replays it (map T18); an activity restored from Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is not
captured (07 §7.1). Rows: B-ANDR-027, -028, -029, -031, -071..077, -095.

### 14.2 Share in and out

- **In** (switch): `SEND text/plain` only; subject joined as the first line unless the body already starts with it;
  ≤ 8,000 chars; memory only; lands unsent in a new conversation's composer (07 §7.7). Images, other `text/*` files and
  Direct Share are after the switch (07 O-07-4, D11-7).
- **Out**: `ShareService.shareText(text, subject)` opens the chooser with `EXTRA_EXCLUDE_COMPONENTS = [MainActivity]` so
  the app never offers itself (a diagnostics report must not start a session with itself); `ActivityNotFoundException`
  → `Outcome.Failed(Failure.Device)` and plain words. `ShareService.shareFile(file)` always adds
  `FLAG_GRANT_READ_URI_PERMISSION` and a `ClipData` with the `FileProvider` URI (Android 18 stops implicit grants for
  `SEND`) [V android-surfaces §12]. Rows: B-ANDR-074, -077, B-SEC-050, -053, -054.

### 14.3 FileProvider and opening a phone file

- Declared once in `:androidApp`: `androidx.core.content.FileProvider`, authority `${applicationId}.localfiles` (the
  preview flavour therefore gets `…opencode_mobile.native.localfiles`), `exported=false`,
  `grantUriPermissions=true`, paths `@xml/local_file_paths` = `<external-path name="shared_storage" path="."/>` only
  [V freeze-tip manifest].
- **No other root** (`files-path`, `cache-path`, `root-path`) may be added without a security review row in
  `docs/security/`: the app's files hold the rootfs, the server password and the agents' sign-ins.
- Opening a file a chat links to (freeze tip, 10 owns the screen): the path is canonicalised with the
  `SharedStorageBinds` rules and must sit under `/storage/emulated/<n>/`; `ACTION_VIEW` with the MIME type from the
  extension through the chooser; an `.apk` opens the system installer view with `application/vnd.android.package-archive`
  (needs `REQUEST_INSTALL_PACKAGES`; Android asks the person every time; never a silent install).

### 14.4 Launcher shortcuts

| Kind | Ids | Rules |
|---|---|---|
| Static | `connect`, `new_task` (explicit `MAIN` to `.MainActivity`, extra `oc.shortcut`) | stable: `res/xml/shortcuts.xml`; preview flavour: `res/xml/shortcuts_native.xml` with `android:targetPackage="io.github.eslamasabry.opencode_mobile.native"`, picked by the manifest placeholder `${appShortcuts}` (static shortcut XML cannot use placeholders itself); labels from `strings_os`, names unchanged |
| Pinned sessions (dynamic) | `oc.session:<profile>:<session>`, at most **4** | published by `ShortcutSurface` (`:android:background`) with `ShortcutManagerCompat` (androidx.core 1.19.1), label = title (≤ 80, localized "Untitled"), icon `ic_shortcut_session`, extras `oc.shortcut.profile`, `oc.shortcut.session`; republished on every launch and on pin changes (dynamic shortcuts are not restored and `allowBackup=false`); stale ids **disabled, not removed** (`shortcut_session_unavailable`), re-pinned ones re-enabled; nothing published while not connected; disconnect withdraws all; profile deletion withdraws only that profile's (`NothingToClear`, `Cleared` or `Failed`); `oc.pinnedShortcuts` remembers the last payload; rate-limit and launcher errors are swallowed (B-ANDR-030, -053..055) |

```kotlin
// :engine:attention commonMain (CR11-7)
public interface ShortcutSurface {
    public suspend fun publishPinned(profile: ProfileId, pins: List<PinnedSessionShortcut>): Outcome<PublishResult>
    public suspend fun withdrawAll(): Outcome<Unit>
    public suspend fun clearProfile(profile: ProfileId): Outcome<ClearOutcome>
}
public interface TileSurface {
    public suspend fun publishAttention(snapshot: TileSnapshot): Outcome<PublishResult>   // writes oc.attentionTile, pokes the tile
    public suspend fun clearProfile(profile: ProfileId): Outcome<ClearOutcome>
    public fun pokeAll()                                                                   // TileService.requestListeningState for both
}
```

### 14.5 Quick Settings tiles (ANDROID-PLATFORM-04)

- **Attention tile**: "N need you" / "All clear" from `oc.attentionTile` = `{pendingCount, profileID, updatedAt}`; a
  count older than **24 h** is ignored (plain "OpenCode"); Android 10 and lower put the state in the label
  (`tile_label_with_count`). Tap → `MainActivity` with `oc.shortcut=activity` through
  `startActivityAndCollapse(PendingIntent)` on API 34+ (the `Intent` overload throws at targetSdk 34+) and the
  `Intent` form below; `unlockAndRun` when the phone is locked.
- **Pause tile**: Running → `STATE_ACTIVE`; paused by the person → `STATE_INACTIVE`, a tap resumes through
  `LivePauseReceiver` (a refused start re-marks paused and opens the app); not enabled in settings → `STATE_UNAVAILABLE`.
  Subtitles `pause_tile_on/paused/off`. Declares `TOGGLEABLE_TILE` (S11-7). Native-only pref `oc_background_native`
  key `pausedByUser`.
- Both: non-active mode (bind on shade open, as today) plus `pokeAll()` after every change; labels ≤ 18 characters in
  every language (checked by a `strings_os` length test); icon `ic_launcher_monochrome`; long-press →
  `ACTION_QS_TILE_PREFERENCES` on `MainActivity` (07 §7.11). An in-context "Add the Pause tile" row
  (`StatusBarManager.requestAddTileService`, API 33+) is after the switch (a new row, NG9).

### 14.6 Tailscale handoff (ANDROID-PLATFORM-34)

```kotlin
// :core:domain (CR11-9); impl AndroidTailscaleApp (:android:platform), copied TailscaleHandoff
public enum class TailscaleAppState { Installed, Missing, Unavailable }
public interface TailscaleApp {
    public fun state(): TailscaleAppState     // package com.tailscale.ipn, needs the <queries> entry
    public fun open(): Boolean                // launches only that package's launch intent; no VPN control, no tailnet state
}
```

Address rules live in `:core:security` (04 §4: `100.64.0.0/10`, `fd7a:115c:a1e0::/48`, names ending `.ts.net`),
because Android's network security config cannot express an IP range (the reason the app checks addresses itself).
The handoff descriptor fetch (`/.well-known/opencode-mobile-session-handoff`, HTTPS to a tailnet address only, resolved
once, no redirects, ≤ 8,192 bytes, 10 s) is 04's. At targetSdk 37 the local-network permission does not cover VPN
interfaces, so Tailscale is unaffected [V android-surfaces §2.2; U11-11 proves it on a device]. Rows: B-SEC-122, -174,
B-AGENT-244.

---

## 15. Boot, update and force-stop restore (`SystemEventRestorer`, S11-11)

```kotlin
// :engine:entry commonMain — the one place that answers "the OS forgot us"
public sealed interface SystemEvent { Boot, PackageReplaced, TimeChanged, TimezoneChanged, ExactAlarmPermissionChanged, LocaleChanged }
public data class AppStartFacts(val forceStopped: Boolean, val firstNativeLaunch: Boolean)

@Inject public class SystemEventRestorer(
    private val schedules: ScheduleEngine, private val widgets: WidgetSurface, private val shortcuts: ShortcutSurface,
    private val tiles: TileSurface, private val notifications: NotificationSurface, private val live: LiveModeController,
) {
    public suspend fun onSystemEvent(event: SystemEvent)      // one at a time (Mutex), idempotent
    public suspend fun onAppStart(facts: AppStartFacts)
}
```

| Trigger (component) | Event | Actions, in order |
|---|---|---|
| `BuiltinRestoreReceiver` (after its server restore promise, §6.5) | `Boot`, `PackageReplaced` | `notifications.ensureChannels()`; `widgets.redrawAll()` (Flutter-era PendingIntents are replaced by native ones; 07 §7.10); `shortcuts` republish from `oc.pinnedShortcuts`; `tiles.pokeAll()`; `live.reconcile()` (pause receipts decide; **never** a `dataSync` start from boot) |
| `ScheduledPromptReceiver` | `Boot`, `PackageReplaced`, `ExactAlarmPermissionChanged` → `rearmAll`; `TimeChanged`, `TimezoneChanged` → `rearmAll(rebaseCalendar = true)` | schedules only (its own body; the restorer does not re-arm twice) |
| `OcApplication`/`MainActivity` first resumed start | `onAppStart` | `forceStopped` (API 35+: `ActivityManager.getHistoricalProcessStartReasons(1)` → `wasForceStopped()`; below: the previous exit classified `forceStop` by `ExitClassifier`) → the same set as `Boot` plus `schedules.rearmAll`; `firstNativeLaunch` → 05 §7.9 H1–H3 |
| `LocaleChanged` (system language, only while the app follows it) | `relocalize()` + `widgets.redrawAll(Locale)` | — |

What it never does: start the phone server (that is §6), resume live mode by itself, post a notification about having
restored something. Diagnostics get one counts-only line per event. Rows: B-MIGR-013, B-ANDR-022, A11-4.

---

## 16. Device bridges

| Bridge | Interface (home) | Android impl (`:android:platform` unless named) | Rules kept (map) | Rows |
|---|---|---|---|---|
| Network | `DeviceFacts.network` (`:core:domain`) | `AndroidDeviceFacts` over `registerDefaultNetworkCallback` | callback arguments are authoritative (never query inside a callback); VPN or Wi-Fi+cellular → transport `Unknown`; a late `onLost` for a replaced default is ignored; no probes, no network names, nothing persisted; a no-network reading is advice and never blocks a loopback or built-in server (T26) | B-ANDR-037 |
| Thermal, battery, power save, visibility | `DeviceFacts` (CR11-5) | `AndroidDeviceFacts` | §8.1 | B-ANDR-069, -093 |
| Exit history and launch report | `ExitHistory` (`:engine:diagnostics`) | `AndroidExitHistory` (copied `AppLifecycle` parts) | `launchReport` once per process (newest `ApplicationExitInfo` not reported before, `oc_lifecycle.seen_exit_timestamp`); `previousServices` from `oc_lifecycle.running_services` written with `commit()` on every change (a force stop runs none of our code, H30); `exitHistory(limit 1..50)` reads all records (`maxNum = 0`) then filters by process; subreason parsed from `toString()`; the description is never forwarded (T21); `MemoryLimit` kind (§7.3); API < 30 → unsupported | B-ANDR-003..006, B-NAV-012, B-SEC-059 |
| Crash capture | `CrashStore` (`:engine:diagnostics`) | copied `NativeCrashStore`, installed by `OcApplication` step 1 before the graph | opt-in consent file `files/crash-diagnostics-consent`; whitelisted class names and fixed category messages only; atomic write (`.tmp` + `fsync` + rename, owner-only); re-checks consent before rename; rethrows a scrubbed `RuntimeException("Native application error")` with an empty stack | B-ANDR-036, B-SEC-043 |
| Keep-running advice | `KeepAliveSettings` (`:core:domain`, CR11-9) | copied `AppLifecycle` maker lists | §8.4 | B-ANDR-007, -008, -025 |
| Shared storage access | `PermissionGate(AllFilesAccess)` (CR11-4) | `AndroidPermissionGate` | API 30+: `Environment.isExternalStorageManager()`, else `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with `package:` and a fallback to the global page; ≤ 29: the runtime dialog; asked **only** when a shared-storage project opens or is created; waits up to 10 min for the person to return | B-ANDR-051, -052, -078..080, B-LINUX-221 |
| Project export and Manage space | `ProjectExport` (`:engine:project`), `StorageReport` (`:engine:diagnostics`) | `SafProjectExport`, `AndroidStorageReport` (copied `ProjectExportBridge`) | `ACTION_CREATE_DOCUMENT` zip; a plan file of `name\tsource` lines; sources must be regular files inside `filesDir` checked with `lstat` (a swapped-in link is never followed); cancel deletes the partial zip; `clearCache` never touches the running server's `proot-tmp`; `clearAllData` = `ActivityManager.clearApplicationUserData()`; `ManageSpaceActivity` never connects or starts the server | B-ANDR-035, B-LINUX-102 |
| BYO-host SSH signer | `ByoSigner` (`:engine:integrations`) | `KeystoreByoSigner` (copied `ByoHostSigner`, `ByoHostAgentProtocol`) | EC P-256 per profile, alias `oc.byoHostSsh.<p>`, `PURPOSE_SIGN`, no user auth; private bytes never leave the Keystore; the agent is a same-uid UNIX socket under the rootfs `tmp/.oa-<16hex>/s` (dir 0700, no links, uid checked, ≤ 16 agents, 10 s); requests 11 and 13 only; the alias survives the in-place update (same package, same signer) | B-SEC-025, -027, -031, -034 |
| System pages | `OsSettingsPages` (`:core:domain`, CR11-9) | `AndroidOsSettingsPages` | `open(page)` for `AppNotificationSettings`, `AppDetails`, `BatteryOptimization`, `ExactAlarms`, `AllFilesAccess`, `DeveloperOptions`, `InternalStorage`, `PromotedNotifications` (API 36+), each with today's fallbacks; returns false when nothing opened; never from composition | — |
| In-app update | `UpdateInstaller` (`:engine:diagnostics`) | `PackageInstallerUpdater` (copied `AppUpdateChannel`, `AppUpdatePolicy`) + `.AppUpdateStatusReceiver` | GitHub `releases/latest`; `SHA256SUMS`; same signer as the installed app (`ownSignerSha256`); ABI choice from `supportedAbis`; `PackageInstaller` session; `STATUS_PENDING_USER_ACTION` intent started from the visible Activity (Android 17 `IntentSender` rule); never silent; the update notice keeps its lowest-priority slot (ADR §9.9, memory `app-updates-two-layers`) | B-MIGR-012, B-BUILD-010..012, B-ANDR-094 |
| Locale | `LocaleHost` (`:engine:settings`) | `AppCompatLocaleHost` | 06 §13.6 | B-L10N-002, -013 |
| Clipboard image, camera, microphone, TTS, PDF, Wi-Fi share | 10 | `:android:media` / `:android:platform` | 10 | — |

---

## 17. Permissions

Every runtime or special permission is asked through `PermissionGate` (`:core:domain`, impl `AndroidPermissionGate`
over `MainActivity`'s `ActivityResultRegistry` while it is resumed; a call with no activity returns
`Failure.Device`), only from a ViewModel action that follows a person's tap, never from composition, never at start-up.
`PermissionStatus.PermanentlyDenied` comes from `shouldShowRequestPermissionRationale` after a first request (the
`camera_permissions` / `voice_permissions` "requested" flags are kept). The answer is always a visible row with the next
step, never a dialog loop.

| Permission | Kind | Asked when | Notes |
|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_PROMOTED_NOTIFICATIONS` | normal | never asked | `shortService` needs no permission |
| `POST_NOTIFICATIONS` | runtime (33+) | §10.8 | B-ANDR-012, -023..026 |
| `SCHEDULE_EXACT_ALARM` | special, user-grantable, off by default on new installs | explicit tap on Schedules (§12.2) | treated as optional; `USE_EXACT_ALARM` is **not** declared (alarm-clock apps only) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | special | first phone-server start, once (§8.4) | refusal remembered |
| `MANAGE_EXTERNAL_STORAGE`; `READ_EXTERNAL_STORAGE` (max 32), `WRITE_EXTERNAL_STORAGE` (max 29) | special / runtime | a shared-storage project opens (§16) | never at start-up |
| `REQUEST_INSTALL_PACKAGES` | special | Update now; opening an `.apk` | Android asks the person every time; declared once (the freeze tip declares it twice; the native manifest keeps one line) |
| `RECORD_AUDIO`, `CAMERA` | runtime | 10 | features `required=false` |
| `com.termux.permission.RUN_COMMAND` | Termux's | the move-from-Termux flow (D7) | removed in the release after migrate-out is dropped (D11-8) |
| `ACCESS_LOCAL_NETWORK` | runtime (Android 17, enforced at targetSdk 37) | not at the switch (targetSdk 36 keeps an implicit grant); the targetSdk-37 slice asks in context the first time a LAN address is used (§18.2) | — |

`<queries>`: `TTS_SERVICE` intent, `com.termux`, `com.tailscale.ipn`, `PROCESS_TEXT`. Never declared: `QUERY_ALL_PACKAGES`,
`SYSTEM_ALERT_WINDOW`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (D11-10), `BIND_ACCESSIBILITY_SERVICE`,
`READ_PHONE_STATE`, any location permission. A `:quality` test compares the merged manifest's permission list with this
table.

---

## 18. Manifest, SDK levels and native libraries

### 18.1 SDK levels (ADR §8)

`compileSdk 37`, `targetSdk 36`, `minSdk 26` for the switch release: the same levels as the Flutter build today, so the
in-place update brings no platform behaviour change of its own [R android-surfaces §0.1].

### 18.2 The targetSdk 37 slice (after the switch, unit W11-17)

| Change at targetSdk 37 | Action | Test |
|---|---|---|
| `ACCESS_LOCAL_NETWORK`: LAN traffic blocked, TCP **times out**, not a permission error | ask in context the first time the person connects to, pairs with or shares on a local (Wi-Fi/Ethernet) address: one row "Let OpenCode reach computers on your Wi-Fi"; map "timeout + not granted" to that ask in `:ui:words`, never a raw error; loopback and Tailscale unaffected (U11-11) | targetSdk-37 test build on API 37 against 127.0.0.1, 100.x and 192.168.x with the permission denied |
| Certificate transparency on by default | self-signed pinned servers go through our own pinning `TrustManager` (`:core:net-okhttp`, 04), not the platform CT path | API 37 test against a self-signed OpenCode 2 server |
| `IntentSender` background-activity-launch hardening | the installer's confirm intent starts from the visible Activity; our own `PendingIntent.send()` calls set `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)` | updater flow on API 37 |
| RemoteViews bitmap budget `6 × w × h` | already bitmap-free (`WidgetBitmapBudgetTest`) | — |
| `static final` fields unmodifiable; new `MessageQueue` | audit every dependency for reflection hacks | release build + E2E smoke on API 37 |
| Background audio needs a while-in-use FGS | read aloud stays foreground-only (D11-10) | — |

### 18.3 Native libraries and packaging

- `:androidApp`: `packaging.jniLibs.useLegacyPackaging = true` (programs may only be executed from
  `nativeLibraryDir`; the proot set ships as `libproot.so`, `libproot-loader.so`, `libtalloc.so`, `libandroid-shmem.so`
  for `arm64-v8a` and `x86_64`, fetched by `tool/builtin_linux/fetch_proot.sh` with its SHA-256 pins: proot
  5.1.107.94, libtalloc 2.4.3, libandroid-shmem 0.7). ABI splits `arm64-v8a`, `x86_64`, universal (one versionCode,
  ADR §9.9).
- The `PROOT_LOADER` path changes with every install (new `nativeLibraryDir`); the reaper already accepts
  `libproot-loader*.so (deleted)` (H29), so guest programs from the previous install are still recognised.
- **16 KB check at every T2** (`tool/native/check_16kb.sh`): for every `.so` in the release APKs,
  `llvm-readelf -l <so> | awk '/LOAD/'` must show alignment `0x4000` or larger, and `zipalign -c -P 16 -v 4 <apk>`
  passes. Covers the proot set, `libaiteam_sandbox.so` (D11-1), the Termux PTY JNI from the `terminal-emulator` AAR
  (U11-15), sherpa-onnx and zxing-cpp (10).
- R8 (`keepRules/*.keep`, AGP 9.3 `optimization {}`): workers + `WorkDatabase_Impl` (§12.5); `com.termux.terminal.**`
  natives (JNI names); AppFunctions generated classes ship their own rules (later). `analyzeReleaseR8Config` at T2.

### 18.4 `:androidApp` manifest (this area's part, complete)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
  <!-- §17 permission list, exactly; <queries>: TTS_SERVICE, com.termux, com.tailscale.ipn, PROCESS_TEXT -->
  <application android:name=".OcApplication" android:label="${appLabel}" android:icon="@mipmap/ic_launcher"
      android:roundIcon="@mipmap/ic_launcher" android:supportsRtl="true" android:allowBackup="false"
      android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="@xml/backup_rules"
      android:usesCleartextTraffic="false" android:networkSecurityConfig="@xml/network_security_config"
      android:enableOnBackInvokedCallback="true" android:manageSpaceActivity=".ManageSpaceActivity">
    <provider android:name="androidx.startup.InitializationProvider" android:authorities="${applicationId}.androidx-startup"
        android:exported="false" tools:node="merge">
      <meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove"/>
    </provider>
    <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.localfiles"
        android:exported="false" android:grantUriPermissions="true">
      <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/local_file_paths"/>
    </provider>
    <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop" android:taskAffinity=""
        android:theme="@style/LaunchTheme" android:windowSoftInputMode="adjustResize"
        android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
      <intent-filter><action android:name="android.intent.action.SEND"/><category android:name="android.intent.category.DEFAULT"/><data android:mimeType="text/plain"/></intent-filter>
      <intent-filter><action android:name="android.intent.action.VIEW"/><category android:name="android.intent.category.DEFAULT"/><category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="opencode-mobile" android:host="session"/><data android:scheme="opencode-mobile" android:host="team"/></intent-filter>
      <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE_PREFERENCES"/></intent-filter>
      <meta-data android:name="android.app.shortcuts" android:resource="${appShortcuts}"/>  <!-- @xml/shortcuts | @xml/shortcuts_native -->
    </activity>
    <!-- ManageSpaceActivity, WidgetConfigureActivity (APPWIDGET_CONFIGURE), WidgetRouteActivity, WidgetOverlayActivity
         (FLAG_SECURE set in code), meta WIDGET_OVERLAY_READY=true: attributes as in §3 -->
    <!-- Services: BackgroundConnectionService (dataSync|shortService), BuiltinServerService + SetupService (specialUse +
         the two subtype properties, verbatim), TermuxResultService, LocalPdfService (isolatedProcess, :local_pdf),
         AttentionTileService + PauseTileService (BIND_QUICK_SETTINGS_TILE, QS_TILE; Pause adds TOGGLEABLE_TILE) -->
    <!-- Receivers: 7 widget providers (APPWIDGET_UPDATE + provider XML), WidgetActionReceiver, CodingActionReceiver,
         StatusActionReceiver, LivePauseReceiver, AppUpdateStatusReceiver, BuiltinIdleReceiver, ScheduledPromptReceiver
         (6 actions), BuiltinRestoreReceiver (BOOT_COMPLETED, MY_PACKAGE_REPLACED); all exported=false except providers -->
  </application>
</manifest>
```

The exact intent-filter order and every attribute come from the freeze-tip manifest; the component-freeze test fails on
any difference not listed in §3. The intent-filter `<data>` lines for `opencode-mobile` must stay one filter with both
hosts (B-ANDR-072).

---

## 19. iOS and Linux desktop: equivalents and gaps

Both platforms come later (ADR §1, D13, D14). Until their phases, each interface below binds an `Unavailable*` and its
`PlatformCapabilities` flag is false, so the UI shows the feature as absent, never as a disabled button (07 "absent,
never disabled"). The engines' rules (budgets, claims, recurrence, copy, privacy) are common code and carry over
unchanged; only the hosts differ.

| Capability (flag) | Android (this section) | iOS (later) | Linux desktop (later) |
|---|---|---|---|
| Built-in Linux (`builtinLinux`) | proot + Ubuntu in app storage, `RuntimeHost` | **Not possible**: no fork/exec of downloaded programs on iOS. Remote servers only; This phone pages absent | Not needed: a `RuntimeHost` in `:engine:runtime-jvm` (ADR §7.4) runs OpenCode and Paseo directly on the host with `ProcessBuilder`, the same setup scripts minus proot; no orphan reaper, no phantom limit |
| Keep a reply alive, finished alert (`backgroundService`) | `dataSync` reply watch / live mode | ~30 s of `beginBackgroundTask`, then suspended. A finished-reply alert needs a **push relay** (APNs) from a server-side component (D11-13, ADR D13); without it, alerts only while the app is open | the process lives while the window or tray icon lives; in-process |
| Ongoing status | notification 4747 + Live Update | Live Activity (ActivityKit; Swift widget extension; push-updated, so the relay again) | tray icon state + tooltip (Compose Desktop `Tray`) |
| Alerts with actions (`notifications`) | channels, Allow once / Reject / Open | `UNUserNotificationCenter` categories; Allow once with the `.authenticationRequired` action option; local while running, push otherwise | freedesktop notifications over D-Bus; action support varies by desktop [U] |
| Home-screen widgets (`homeWidgets`) | RemoteViews providers | WidgetKit (Swift) with App Intents buttons (interactive widgets) | none |
| Quick Settings tiles (`quickSettingsTiles`) | two `TileService`s | Control Center controls (`ControlWidget`, Swift) | none |
| Launcher shortcuts (`launchShortcuts`) | static + 4 pinned sessions | Home Screen quick actions (`UIApplicationShortcutItem`) | `.desktop` file `Actions=` (Connect, New conversation) |
| Scheduled prompts | AlarmManager + WorkManager, exact when allowed | `BGTaskScheduler` is opportunistic (no exact time): schedules only where the server runs them, otherwise "not available on iPhone" | in-process timer while running; a systemd user timer is a later option |
| Share in | `SEND text/plain` | Share Extension (Swift) | drag and drop onto the window |
| Deep links | `opencode-mobile://session`, `://team` | custom URL scheme (+ universal links later) | `.desktop` `MimeType=x-scheme-handler/opencode-mobile;` |
| Assistant actions (`appFunctions`) | AppFunctions (later) | App Intents (Swift; Siri, Shortcuts) | none |
| Tailscale handoff (`tailscaleHandoff`) | `com.tailscale.ipn` launch intent | `canOpenURL` on a declared Tailscale scheme [U], else the App Store link through the link policy | detect the `tailscale` command on `PATH`; otherwise the download page through the link policy |
| Exit reasons, crash records | `ApplicationExitInfo`, `NativeCrashStore` | MetricKit diagnostics, on device only | file crash store (03 §12.1) |
| Thermal, battery, network | `PowerManager`, `BatteryManager`, `ConnectivityManager` | `ProcessInfo.thermalState`, `UIDevice.batteryLevel`, `NWPathMonitor` | `/sys/class/thermal`, `/sys/class/power_supply` (optional), interface polling |
| BYO-host SSH signer | AndroidKeyStore EC P-256 | Secure Enclave P-256 | key file 0600 or libsecret |
| In-app update (`inAppUpdater`, `desktopReleaseCheck`) | `PackageInstaller` | App Store / TestFlight only | release check + download link |
| Permissions | `PermissionGate` | Info.plist usage strings + the same gate | none needed |

iOS-only work items for the D13 checklist: a Mac or macOS CI, the Apple Developer Program, the push relay, Swift
targets for WidgetKit, App Intents, Control Center controls and the Share Extension (bridged through SKIE or Obj-C
export, ADR D13).

---

## 20. Delivery order by slice (the delivery plan owns dates)

| Slice | This section's work | Exit check |
|---|---|---|
| 0 | `:androidApp` manifest skeleton with every §3 shell compiling against stub bodies; the `quality/freeze` lists of §2.5 and §3; `PlatformCapabilities` provider; P5 identity proof with these components living in library modules (ADR §11); read-only checks U11-1, U11-12, U11-13 | component-freeze test green against the Flutter 2.1.0+57 and freeze-tip lists |
| 1 | W11-01 move-green; W11-02 starts (script extraction); network and crash bridges (W11-13a) | 46 JVM classes + 7 runtime acceptance classes green, unchanged |
| 2 | `EntryIntentReader`, `PermissionGate`, `ActivityResults`, `ShareService`, link opener, Tailscale (W11-13b); static shortcuts | E2E-25 entry parts on the native preview build |
| 3 | W11-03 split; W11-04 runtime policy port + process budget + thermal; W11-05 setup engine; W11-07 agent host; `ReplyWatch` stub owner | E2E-17, E2E-18 on the preview build; §6.8 update-restore proof between two native builds |
| 4 | W11-09 background and notifications; W11-10 widgets; W11-11 overlay panels; W11-12 schedules; W11-14a `SystemEventRestorer`; W11-15 updater | E2E-07, -08, -09, -25, -26, -27 |
| 5 | W11-06 Linux manager, services, vitals, backup; W11-08 terminal; W11-16 Termux migrate-out; export, storage access, BYO signer (W11-13c) | E2E-16, -19, -20 |
| 6 | W11-14b upgrade-journey proofs over the Flutter build (§6.8, 05 §10.5); owner phone checks (§22.4) | E2E-39 green; A11-1..A11-12 |
| after | W11-17 targetSdk 37; W11-18 AppFunctions; widget redesign (Glance gate §11.1); D11 items the owner takes | — |

---

## 21. Work units

Builders follow ADR §10.3: Codex for engine and protocol code from a contract, Claude (Sonnet) for Android modules and
UI, Opus for the runtime split, the migration and the restore paths. Every unit: one worktree, one branch, its own APK
build-number range from the coordinator, reviewers never launch test processes. Estimates are agent-weeks (aw) and are
re-planned at M0 from proof P10.

| Unit | What | Module(s) written | Builder | Slice | Depends | Acceptance | Focused checks |
|---|---|---|---|---|---|---|---|
| W11-01 | Move-green: copy every runtime Kotlin file, `PtyAccess.java`, jniLibs, the 30 runtime JVM tests and 7 acceptance classes into `:android:linux`; `R` imports fixed; nothing else | `:android:linux` | Sonnet, runtime owner reviews | 1 | P1 | all copied tests green with zero edits; `Copied from <sha>` recorded | `./gradlew :android:linux:testDebugUnitTest`; T3 acceptance on the PC emulator |
| W11-02 | Script templates, generator, golden + checksum + shell tests (S11-3) | `:engine:runtime` (scripts), build-logic `OcScriptsPlugin` | Codex | 1–2 | — | every Dart builder has a template; `ScriptGoldenTest` byte-equal | `:engine:runtime:desktopTest --tests "*Script*"` on the VPS (dash + bash) |
| W11-03 | Split `BuiltinLinux.kt` (§5.3), `RuntimeLocks`, `RuntimeLockOrderTest`, `AndroidRuntimeHost`, delete the channel | `:android:linux` | **Opus** | 3 | W11-01, contract `RuntimeHost` frozen | moves-only diff; all runtime tests + acceptance green after each move | T0 per move; T3 at the end; §6.8 proof |
| W11-04 | Runtime policy port: starter, recovery consumer, healing, idle resume, deliberate stop, exit recovery, lifecycle report, `PhoneReplyWake`, process budget, heat tier, process watch, thermal policy, `ExitClassifier` (+ `MemoryLimit`) | `:engine:runtime`, `:engine:diagnostics` | Codex | 3 | W11-02, W11-03 contract | catalog rows of §5.2 green as `commonTest` | `:engine:runtime:desktopTest` |
| W11-05 | Setup engine, component registry (pins + SHA-256), preflight, journals, removal, voice hook | `:engine:runtime` | Codex | 3 | W11-02 | B-LINUX-001, -007, -014, -157, -181..220 | `desktopTest`; E2E-17 on the emulator |
| W11-06 | Linux manager, storage, packages, reset, services supervisor, vitals, backup (freeze tip) | `:engine:runtime` | Codex | 5 | W11-04 | B-LINUX-054..065, -082..093, -247..249 + top-up rows | `desktopTest`; E2E-19, -20 |
| W11-07 | `:android:agenthost` carve-out behind `GuestLauncher` / `GuestProcessRegistry`; `AndroidAgentHost` | `:android:agenthost`, `:android:linux` (seams) | Opus (with the agents section) | 3 | W11-03, CR11-0 | copied agent JVM tests green; agents section's rows | T0 + T3 |
| W11-08 | `:android:terminal` carve-out; `AndroidTerminalHost`; Termux view slot (03 S11) | `:android:terminal` | Sonnet | 5 | W11-03 | B-LINUX-097..101, -222..226; `yes`, Ctrl-C, resize, 512 KiB replay | T0; E2E-16 |
| W11-09 | Background: `AndroidForegroundWork`, service bodies (incl. moving the phone-server, setup and restore bodies out of `:android:linux` behind `PhoneServerForeground` / `PhoneServerServiceHooks`, S11-12), status row (+ `StatusModel` port), alerts (+ `NotificationText`, `AlertCopy`, `AlertButtons`, `NotificationPolicy` ports), receivers, live mode + pause receipts, reply watch, sign-in claim, tiles, pinned shortcuts, channel registry, relocalize | `:android:background`, `:engine:attention` | Codex (engine) + Sonnet (Android) | 4 | W11-13a, CR11-2/3/5/7 | B-ANDR-001, -009, -011..020, -023..034, -038..048, -053..068, -081, -090, -096..098; §9.4 new tests | T0; E2E-07, -08, -09; FGS time-limit proof |
| W11-10 | Widgets: snapshot builder, usage feed, action runner, status runner (common ports); providers' bodies, cache, admission, follow-up worker | `:android:widgets`, `:engine:attention` | Codex (engine) + Sonnet (Android) | 4 | W11-09, CR11-6/7/8 | B-ANDR-010, -022, -095, -099..121, B-SEC-179..183; render tests; `WidgetBitmapBudgetTest` | T0 Robolectric; E2E-26 |
| W11-11 | Overlay: `WidgetOverlayActivity` Compose host, `WidgetOverlayPort`, `:feature:widget-panels` VMs | `:feature:widget-panels`, `:android:widgets` | Sonnet | 4 | W11-10, 07 M01–M03 | B-ANDR-108, -109 | E2E-26 overlay steps |
| W11-12 | Schedules: model, recurrence, store, runner (common); `AndroidScheduleHost`, receiver and worker bodies, run notifications | `:engine:schedule`, `:android:schedule` | Codex + Sonnet | 4 | CR11-6, W11-04 (phone lease) | §12.5 rows; `RecurrenceGoldenTest` | `desktopTest`; T3 alarm + time-change runs; E2E-27 |
| W11-13 | Platform bridges: (a) network, crash, exit history; (b) entry reader, permission gate, activity results, share, link opener, Tailscale, `OsSettingsPages`, locale host; (c) storage access, export, Manage space, BYO signer, keep-alive | `:android:platform` | Sonnet | 1, 2, 5 | CR11-4/5/9 | §16 rows | T0 Robolectric |
| W11-14 | (a) `SystemEventRestorer` + first-launch hooks H2/H3; (b) the §6.8 proofs over a real Flutter install | `:engine:entry`, `:android:linux`, `:androidApp` shells | **Opus** (with the migration owner) | 4, 6 | W11-03, W11-09, W11-10, W11-12, 05 | §6.8 table all pass; B-MIGR-013 | T3 on the PC emulator |
| W11-15 | In-app updater (`PackageInstallerUpdater`, status receiver) | `:android:platform`, `:engine:diagnostics` | Sonnet (with the release section) | 4 | ADR §9.9 | B-MIGR-012, B-BUILD-010..012, B-ANDR-094 | T3 install of a newer test build signed with the same key |
| W11-16 | Termux migrate-out bridge | `:android:termux`, `:engine:runtime` | Sonnet | 5 | W11-04 | B-LINUX-233..238 | T0 |
| W11-17 | targetSdk 37 slice (§18.2) | several | Sonnet + Codex | after | the switch | §18.2 tests | API 37 emulator |
| W11-18 | AppFunctions (§13) | `:android:platform`, `:engine:entry` | Sonnet | after | U11-9 | execution proven on a device | `cmd app_function` |

Rough size: W11-01 0.5–1 aw; W11-02 1.5–2; W11-03 2–3; W11-04 3–4; W11-05 2–3; W11-06 3; W11-07 1.5; W11-08 1.5;
W11-09 3–4; W11-10 3; W11-11 1; W11-12 3; W11-13 3; W11-14 1.5; W11-15 1.5; W11-16 1. About 31–37 aw before the switch.

---

## 22. Tests and proofs

### 22.1 Port first (failing tests before code; ADR §10.3 test-writer lane)

1. The 46 copied JVM classes and 7 runtime acceptance classes (§4.3): green before any change (W11-01).
2. The Dart logic suites of this area, translated to `commonTest` and named after catalog rows
   (`` `B-ANDR-027 pinned shortcut tap is delivered once on cold start`() ``): notifications
   (`notification_status_test`, `notification_status_controller_test`, `notification_text_test`,
   `notification_moments_test`, `notification_preferences_test`, `native_notification_copy_test`, `live_status_test`,
   `background_coding_alert_test`, `background_notification_navigation_test`, `chat_notify_offer_test`,
   `first_reply_notify_offer_test`); background (§9.4); widgets (§11.6); launch (`launch_shortcut*_test`,
   `share_intent*_test`, `share_routing_test`, `session_link*_test`, owned jointly with 07); schedules (§12.5);
   platform (`app_exit_history_test`, `app_exit_recovery_test`, `app_lifecycle_test`, `lifecycle_report_test`,
   `native_crash_test`, `phone_crash_native_test`, `crash_diagnostics_test`, `network_test`, `thermal_guard_test`,
   `platform_capabilities_test`, `phone_storage*_test`, `shared_storage*_test`, `shared_project_roots_test`,
   `tailscale_profile_test`, `tailscale_setup_test`, `desktop_platform_gating_test`, `ios_remote_platform_gating_test`);
   built-in Linux (builtin-linux §14: `setup_engine_test`, `linux_manager_parse_test`, `phone_server_healing_test`,
   `builtin_server_recovery*_test`, `builtin_server_test`, `setup_preflight_test`, `listening_ports_test`, …).
3. The freeze-tip top-up rows (02 §3.5: process budget, heat tier, agent priority, services, vitals, backup, restart
   after an OpenCode update, file open, Wi-Fi share links) before slice 1 closes.
4. New tests this section adds because nothing covered them: `BackgroundPausePolicyTest`, `AppExitHistoryPolicyTest`,
   `ConversationsWidgetPlanTest`, `NetworkMonitorRaceTest` (callback ordering), `ShortcutDisableEnableTest`,
   `LaunchWhitelistTest`, `SessionLinkIngressTest`, `ForegroundStartLegalityTest`, `ReplyWatchPolicyTest`,
   `BootPathTest`, `RuntimeLockOrderTest`, `WidgetBitmapBudgetTest`, `RecurrenceGoldenTest`, `ChildProcessLimitTest`,
   `ScriptGoldenTest`, `ScriptChecksumTest`, `EntryContractTest` (07), `ReceiverContractTest` (the receiver extras of
   §2.5 accepted from Flutter-shaped intents), `OsStringsParityTest` (the two `strings_os` copies), the manifest
   permission-list test (§17).

The 7,967 widget tests (UI) are not ported now (owner, ADR §4 row 18; NG12). Zero-cost preparation only: test tags equal
the Flutter `ValueKey` strings.

### 22.2 Device flows this section must turn green (Flutter first, native second; catalog `e2e-flows.md`)

E2E-07 (answer from the shade), E2E-08 (needs-you, finished, status row), E2E-09 (keep running), E2E-16 (terminal),
E2E-17 (Linux setup), E2E-18 (phone server lifecycle), E2E-19 (remove and reset), E2E-20 (dev servers, ports,
processes), E2E-25 (shortcuts, tile, links, share-in), E2E-26 (widgets), E2E-27 (schedules), E2E-39 (upgrade in place).
E2E-28 and E2E-32 wait for D6.

### 22.3 Emulator recipes (T3; the PC has one emulator and one lock holder; midnight–2 am no emulator)

| Recipe | Commands | Proves |
|---|---|---|
| Update restore | §6.8 (`pm unstop` before `install -r` if the app was ever force-stopped) | R1–R5, R8 |
| FGS time limit | `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <pkg>`; `adb shell device_config put activity_manager data_sync_fgs_timeout_duration 60000` | §9.2 rules 3 and 5, B-ANDR-014 |
| Phantom limit | `adb shell device_config put activity_manager max_phantom_processes 8` [S], two agent sessions; then `device_config delete …` | §7.2 guard, kill line |
| Memory limiter (API 37 image) | `cat /proc/<child>/cgroup`; `am memory-limiter manual <pid> 1024`; `am memory-limiter status` | U11-3, `MemoryLimit` exit |
| Force stop | `am force-stop <pkg>`; observe greyed widgets; open the app | R9, §11.5 |
| Schedules | `adb root`; `adb shell date <MMDDhhmmYYYY.ss>` to jump the clock; `adb shell dumpsys alarm \| grep <pkg>`; `adb shell cmd jobscheduler run -f <pkg> <jobId>` (id from `dumpsys jobscheduler`) [S]; zone changes through Settings by a Maestro flow | §12.2, T29 |
| Widgets | place both widgets with a Maestro flow; Robolectric renders regenerate the preview PNGs | §11 |

### 22.4 Owner's phone (NX721J; read-only adb only when allowed, no screencaps, installs only after an emulator pass)

1. `adb shell getprop ro.build.version.release` and `ro.build.version.sdk_full` (U11-1: memory says Android 15, the brief
   says 16).
2. Signer check before any install: ask the owner for the installed certificate SHA-256 and compare with
   `apksigner verify --print-certs <apk>` (AGENTS.md; local key `1DE5BF08…D60C`; never substitute).
3. After the switch APK install: `adb shell ps -A -o PID,PPID,NAME -u <uid>` shows `opencode2` (R8); `dumpsys
   activity exit-info <pkg>` for exit reasons (logcat is silenced on that phone, memory `phone-logcat-silenced`).
4. `adb shell dumpsys notification --noredact | grep -i promot` while a run works (U11-2).
5. The owner sends one photo of each widget on the ZTE home screen and of the widget picker (§11.4).

---

## 23. Unknowns (settled by the named proof; work proceeds on the stated default meanwhile)

| # | Unknown | How to settle | Default until settled | When |
|---|---|---|---|---|
| U11-1 | Owner phone OS: Android 15 or 16, QPR level | §22.4 step 1 | test both API 35 and 36 images | slice 0 |
| U11-2 | Does ZTE MyOS promote Live Updates? | §22.4 step 4 | promote per §10.5; harmless if ignored | slice 4 |
| U11-3 | Are proot children inside the app's Memory Limiter cgroup? | §22.3 memory-limiter recipe | no memory term in the budget (§7.3) | slice 3 |
| U11-4 | Is a `startForegroundService` from `MainActivity.onStop` always inside the "leaving a visible state" exemption on API 34–37? | instrumented test: background the app with a run active, assert the reply-watch row appears; repeat 20× | start in `onStop` (not `ProcessLifecycleOwner`, which fires ~700 ms later) | slice 4 |
| U11-5 | Does `AppWidgetService` resend `APPWIDGET_UPDATE` after a package replace? | log in `onUpdate` during the §6.8 proof | never rely on it: the restorer redraws | slice 4 |
| U11-6 | Glance `SizeMode.Exact`: one RemoteViews or a size map on 12+? | Robolectric inspection, then the owner's launcher | not used (RemoteViews) | after switch |
| U11-7 | Does the ZTE launcher show generated previews? | API 35+ device | keep `previewImage` | after switch |
| U11-8 | Can apps read `settings_enable_monitor_phantom_procs` and Developer-options state on Android 17 QPR1+? | API 37.1 emulator | card shows the steps without a "detected" state when unreadable | slice 3 |
| U11-9 | Does an AppFunction call allow a background foreground-service start (phone server)? | API 36/37 device: execute `startConversation` with the app in the background | require a running server | after switch |
| U11-10 | Compat `setRequestPromotedOngoing` / `setShortCriticalText` on API 36.0 (before QPR1) | Robolectric SDK 36 + API 36.0 image | call unconditionally through Compat | slice 4 |
| U11-11 | Loopback and Tailscale exempt from `ACCESS_LOCAL_NETWORK` | targetSdk-37 test build on API 37 | assume exempt (VPN documented [V], loopback [S]) | W11-17 |
| U11-12 | `device_config … max_phantom_processes` honoured on API 35–37 emulators | run the recipe once | if not, prove the guard with 32 real processes | slice 0 |
| U11-13 | Does the owner's phone hold `files/oc.teamEngine.protection-required`, and which tier? | This phone › Details or the problem report from the Flutter build; else ask the owner | ship `libaiteam_sandbox.so` (D11-1) | slice 0 |
| U11-14 | CMP `getString` in a service with no Activity follows the stored app language on API 26–32 | ADR P6 includes a service-posted notification | `createConfigurationContext` wrapper per render (S11-8) | slice 0 |
| U11-15 | 16 KB alignment of the Termux PTY JNI from JitPack v0.118.3 | `tool/native/check_16kb.sh` | rebuild the JNI from source with 16 KB `-Wl,-z,max-page-size=16384` if it fails | slice 1 |

---

## 24. Risks and acceptance checks

### 24.1 Risks (ranked)

| # | Risk | Effect | Mitigation |
|---|---|---|---|
| 1 | A component, channel, worker, extra or `Data` key name drifts | placed widgets vanish, tiles and pinned shortcuts break, channel choices reset, queued work fails silently (map R1) | §2.5 and §3 freeze lists checked at every T2; E2E-39 with every surface placed |
| 2 | Restore authority lost at the switch | the phone server silently stops coming back after reboots and updates (builtin-linux risk 1) | 05 M4 + §6.5 order + §6.8 proofs, including the budget-reset and interrupted-launch variants |
| 3 | Behaviour drift in ownership and drain code | kills the wrong process, orphans spin, hot phone (H1–H4) | S11-1 copy first; S11-2 one lock family; moves-only split; acceptance classes on a real kernel |
| 4 | The first native process starts from `MY_PACKAGE_REPLACED` and loses the FGS exemption while waiting for migration | server not restored on the owner's first update | S11-5 promise-before-gate; §6.8 interrupted-launch proof |
| 5 | Headless-engine removal moves failures into the app process (map R8) | a widget or schedule crash takes the app down | `SupervisorJob` + logging handler on every runner scope; 30 s / 420 s watchdogs kept; `Outcome` everywhere; crash-loop guard |
| 6 | Exactly-once semantics of widget actions and schedules simplified away (map R9) | double sends or lost runs | claims committed before the network; tests ported first; "never `Result.retry()` after claim" enforced by a test |
| 7 | Fingerprint byte drift (map R2) | every schedule and widget target reads stale | byte-exact `DartJson` (05 §2.5) + vectors from the real install |
| 8 | dataSync budget and OEM kills (map R4) | alerts stop after hours; Nubia swipe force-stops | reply watch uses minutes; honest copy; keep-running guidance; R9 restorer |
| 9 | Phantom limit and memory limiter | agents killed mid-work | §7 guard, priority, kill line, guided card; U11-3 |
| 10 | Preview flavour collides with the Flutter app on the owner's phone | port clash, shared phantom budget | S11-6 own ports; one-line warning (§7.2) |
| 11 | The ZTE launcher differs from the Pixel emulator | widget looks fine in CI, broken on the owner's phone | RemoteViews kept; owner photo check before the switch (A11-8) |
| 12 | Shorebird ends; every Linux pin bump is an APK (builtin-linux risk 7) | slower OpenCode/Paseo/Claude updates | the in-app updater ships in the switch release (W11-15); a signed remote pin manifest is a later option |

### 24.2 Acceptance checks for the switch release (this section's part of the parity gate, ADR §10.1)

| # | Check | How |
|---|---|---|
| A11-1 | Every row of §1 lands in a slice or is named "later" by an ADR decision | `scope.jsonl` coverage test (02 §3.6) |
| A11-2 | All B-ANDR and B-LINUX rows in switch scope have passing native tests | catalog ratchet (ADR §6.3) |
| A11-3 | Merged manifest equals the §3 list (additions and removals only as marked) and the §17 permission list | component-freeze + permission tests at T2 |
| A11-4 | After `install -r` over Flutter 2.1.0+57 on the emulator: placed widgets redraw and their taps work, both tiles work, a Flutter-pinned shortcut opens its conversation, a Flutter-posted alert's Allow reaches the exact request or opens it, schedules fire at the next due time, channel customisations survive, the phone server is back within 60 s | E2E-39 + §6.8 |
| A11-5 | Reboot restores the server; a server the person stopped stays stopped | §6.8 |
| A11-6 | The 6 h `dataSync` path ends in the paused row with the right receipt and no crash; a new start is refused cleanly | §22.3 FGS recipe |
| A11-7 | Zero `MethodChannel`, `EventChannel` or `io.flutter` reference in `native/` | Konsist rule (§2.4 rule 5) |
| A11-8 | Both widgets and the picker look right on the owner's ZTE launcher | owner photos (§22.4 step 5) |
| A11-9 | Every `.so` in the release APKs is 16 KB aligned | `check_16kb.sh` at T2 |
| A11-10 | Notification, widget, tile and shortcut copy exists in all seven languages; Arabic widgets mirror | `OsStringsParityTest`, lint `MissingTranslation`, Roborazzi of widget renders in `ar` |
| A11-11 | The guard holds work near the limit; a 137 kill shows the plain line with Resume | §22.3 phantom recipe |
| A11-12 | No secret, prompt, path, server error or credential in any notification, widget snapshot, shortcut label, WorkManager `Data` or log line | `RedactionSurfaceTest` (fake surfaces fed with secret-bearing fixtures), B-SEC-105, -106, -179..183 |

---

## 25. Owner decisions (each has a default; work proceeds on it)

| # | Question | Recommended default |
|---|---|---|
| D11-1 | AI Team waits until after the switch (ADR D6), but a phone that ever turned on its protection keeps a marker that makes every Linux launch go through `libaiteam_sandbox.so`. Keep that 343 KB helper (and its manifest hash) in the switch APK? | **Yes**: keep the sandbox helper so the built-in Linux never breaks on such a phone; the 5.4 MB engine and the 1.1 MB probe stay out until AI Team returns |
| D11-2 | A reply still running when you leave the app keeps a short background service until it finishes, so "reply finished" arrives without "Stay connected" on all day (ADR §9.5). This changes one old rule ("alerts only with Stay connected on", B-ANDR-013). | **Yes** |
| D11-3 | The five hidden old widget sizes (still drawn if placed): keep them working, or retire them after a notice? The classes must stay either way. | Keep them working |
| D11-4 | Notifications, widgets, tiles and shortcuts in all seven app languages from day 1 (today only English and Arabic). | Yes |
| D11-5 | The richer Android 16 progress card (segments per task, agent icon) for the running status. | After the switch |
| D11-6 | When Android won't show the live status chip, a visible row that opens the system page for it. | After the switch |
| D11-7 | Share photos and files into the app, and the top 2–3 conversations in the system share sheet (Direct Share). | After the switch (07 O-07-4) |
| D11-8 | The Termux command permission stays only while "Move from Termux" exists. | Keep it through the switch, drop it one release later |
| D11-9 | An "Add the Pause tile to Quick Settings" row on the Background settings page. | After the switch |
| D11-10 | Read aloud continuing with the screen off (needs a media foreground service). | No |
| D11-11 | Lock-screen widgets (anyone can see them). | No |
| D11-12 | An app lock with fingerprint or PIN. | No (no need today; the stable biometric library is from 2021) |
| D11-13 | A small server-side push relay, so finished-reply alerts work without any background service (and on iPhone later). | Later, with the iOS decision (ADR D13) |

---

## 26. Sources

- Binding: `01-architecture-decision.md` (§3.3, §4 rows 12, 18, 25, 27, §5.2, §6.1–§6.3, §7, §8, §9.1, §9.5, §9.7, §9.9,
  §10, §11, §12).
- Sections: `02-principles-scope.md` (§2 NG7, NG9, NG12, NG13, NG21; §3.2; §3.5 freeze-tip delta),
  `03-architecture-modules.md` (§0.2 S9, S11, S14; §2.3; §10.1; §10.4; §12.1; §13.2; §13.6), `05-data-migration.md`
  (§2.5, §7.1, §7.4 M4, §7.7, §7.9, §10.5, Appendix A, D), `06-ui-kit-design-system.md` (§13.2, §13.6),
  `07-screens-navigation.md` (§7.1–§7.13), `10-media-voice.md` (M12, M14, C10-3, C10-10).
- Maps: `../map/android-platform.md` (all 65 features, §3, §5–§8, §11 T1–T30, §13–§17), `../map/builtin-linux.md`
  (all 64 features, §5–§14, H1–H30, T1–T15).
- Research: `../research/android-surfaces.md` (§0–§17, U1–U15), `../research/kmp-cmp-status.md`,
  `../research/testing-perf.md`.
- Catalog: `../catalog/BEHAVIORS.md`, `behaviors.jsonl` (B-ANDR-001..121, B-LINUX-001..251, B-MIGR-013, B-SEC, B-STATE,
  B-AGENT, B-NAV rows cited), `e2e-flows.md` (E2E-07..09, -16..20, -25..27, -39).
- Repo (read-only): `/home/eslam/Storage/Code/wt/oc_app-batch1/android/app/src/main/AndroidManifest.xml`, `K/*.kt`
  (168 files; `BuiltinLinux.kt` structure by line), `src/test` (46 classes), `src/androidTest` (13 classes),
  `res/{layout,xml,values*}`, `jniLibs` (sizes); freeze tip `int/freeze-2026-11-01` (`git diff --stat 65e3f70a3`:
  `AgentPriority`, `ChildProcessLimit`, `ServiceNoticeGate`, `AppUpdateChannel`, FileProvider, `lib/builtin/
  process_budget.dart`, `heat_tier.dart`, `agents/phone_budget.dart`); branch `feat/appfunctions-2026-10-30`
  (`AppFunctionsService.kt`, manifest and Gradle diff).
- Demo: `/home/eslam/Storage/Code/kmp-demo/androidApp/src/main/kotlin/…/ReplyService.kt` (`dataSync` reply service).
- Owner memory: `zte-launcher-widget-traps`, `restore-proof-recipe`, `proot-orphans-heat`, `phantom-process-limit`,
  `appfunctions-plan`, `app-updates-two-layers`, `android-native-feel`, `phone-logcat-silenced`,
  `test-on-emulator-not-phone`, `owner-phone-debug-allowed`, `no-owner-phone-screenshots`, `inapp-server-clean-env`,
  `built-in-linux-plan`, `linux-manager-plan`, `freeze-owner-decisions-2026-11-01`, `dev-freeze-native-rewrite-2026-11-01`,
  `steer-not-queue`, `text-scale-cap`, `teach-what-is-hidden`, `pc-capacity-ceiling`, `quiet-hours-vps-only`.
- Checked today [V], 2026-11-01: Google Maven metadata `androidx.work:work-runtime` 2.12.0 (2026-09-23),
  `androidx.core:core` 1.19.1 (2026-09-23), `androidx.appfunctions:appfunctions` 1.0.0-beta01 (2026-10-21),
  `androidx.glance:glance-appwidget` 1.2.0 stable / 1.3.0-alpha02; `core-1.19.1.aar` classes inspected with `javap`
  (`NotificationCompat.Builder.setRequestPromotedOngoing`, `setShortCriticalText`, `NotificationCompat.ProgressStyle`,
  `ProgressStyle.Segment(int)`, `NotificationCompat.MetricStyle`, `NotificationManagerCompat
  .canPostPromotedNotifications`, `ServiceCompat.startForeground(Service, int, Notification, int)`);
  `appfunctions-1.0.0-beta01.aar` classes inspected (`AppFunctionDeclaration(isDescribedByKDoc, isEnabled)`,
  `AppFunctionServiceEntryPoint(serviceName, appFunctionXmlFileName)`, `AppFunctionAccessLevel(level,
  isCompatEnforcementEnabled)`, `AppFunctionService`); Maven Central `commons-compress` latest 1.28.0 (the catalog keeps
  1.27.1, as today, until a reason to move).
