# Android platform surfaces for the native rewrite (Android 16 and 17, late 2026)

Topic: `android-surfaces`. Written 2026-11-01. All web sources were read on 2026-11-01; each one's own "last updated" date is in §18.

Scope: Glance and RemoteViews widgets, AppFunctions, notifications (Live Updates, ProgressStyle, MetricStyle), foreground-service types and limits, WorkManager and alarms, phantom processes and the new Android 17 memory limiter, predictive back and the other native-feel surfaces, Quick Settings tiles, launcher shortcuts, share targets, credentials and biometrics, distribution (developer verification, signing, 16 KB pages), and what changes at targetSdk 36 and 37.

Repo facts come from `/home/eslam/Storage/Code/wt/oc_app-batch1` (read-only) and from `map/android-platform.md` and `map/builtin-linux.md` in this folder. This file does not repeat those maps. It adds what the platform does now and what that means for the rewrite.

Labels:

- **[V]** verified on 2026-11-01 from a primary source: developer.android.com, source.android.com, AndroidX release notes, or the repo itself.
- **[S]** secondary source: press, a vendor blog, a localized or mirrored copy, or a forum.
- **[U]** unverified, or my inference. Check it in slice 0 before relying on it.

---

## 0. Bottom line

1. **Ship the first native release at `targetSdk 36` and `compileSdk 37`, then move to `targetSdk 37` in its own slice.**
   - These are the same SDK levels as the Flutter build today. [V] Flutter 3.47.1 defaults to `targetSdkVersion = 36`, and `android/app/build.gradle.kts` sets `compileSdk = 37`.
   - Keeping them means the in-place update brings no platform behaviour change of its own.
   - Targeting 37 brings four changes the person can see or that break code: local-network blocking with a runtime prompt, certificate transparency on by default, `IntentSender` background-launch hardening (which touches the self-updater), and a hard RemoteViews bitmap budget. Each needs its own work and test pass. [V]
   - Play requires API 36 from 2026-08-31 and has no date for API 37. [S] That matters only if Play distribution ever happens.
2. **The local network permission (`ACCESS_LOCAL_NETWORK`, Android 17, enforced at targetSdk 37) is the biggest user-facing change for this app.** [V]
   - It blocks servers reached over Wi-Fi or Ethernet: a laptop on the LAN, a Paseo daemon on the LAN, and the Wi-Fi file-share server.
   - Blocked TCP connections usually fail with a **timeout**, not a permission error.
   - It does not apply to VPN interfaces, so Tailscale's 100.64.0.0/10 is not affected. [V, from the permission's reference text]
   - It most likely does not apply to loopback, so the phone server on 127.0.0.1 is safe. [S]
   - Until the app targets 37 it keeps an implicit grant. Plan an in-context ask for when the app targets 37.
3. **Android 17's Memory Limiter matters for the built-in Linux.** [V]
   - It is a per-process cgroup v2 limit, tiered by device RAM. For a 16 GB phone the example config gives 10 GiB while visible and 5 GiB while "not visible", which is the foreground-service state.
   - Forked children inherit their parent's cgroup, so the whole proot tree probably counts against the app process. [U]
   - When the limit hits, the exit shows up as `REASON_OTHER` with `MemoryLimiter:AnonSwap` in the description.
   - Add that string to exit classification on day 1, and test with `am memory-limiter`.
4. **Foreground-service rules are unchanged since Android 15.** [V]
   - `dataSync` (and `mediaProcessing`) get 6 h per 24 h in the background. The budget resets when the person opens the app.
   - A `dataSync` service cannot be started from `BOOT_COMPLETED` (targetSdk 35+). `shortService` gets about 3 minutes.
   - `specialUse` has no timeout. Its subtype text is reviewed only by Play.
   - Since Android 15, a network request made outside a valid process lifecycle throws. So "tell me when the reply finishes" while the app is in the background still needs a foreground service (or push).
   - The docs do not say `dataSync` is deprecated. [V] For the Android 16/17 FGS timeout rules, the timeout page lists nothing new.
5. **Live Updates are real but strictly scoped.** [V]
   - Requirements: `POST_PROMOTED_NOTIFICATIONS`, `setRequestPromotedOngoing`, an ongoing notification with a title, no custom views, not colorized, not a group summary, and a channel above `IMPORTANCE_MIN`.
   - Google's policy says they are only for user-initiated, in-progress, time-sensitive activities. It names "chat messages, alerts" as inappropriate.
   - The app's status row qualifies only while a run the person started is working (or waiting on them inside that run).
   - Use `NotificationCompat` from **androidx.core 1.19.1**: `ProgressStyle` and `setRequestPromotedOngoing` since 1.17.0-alpha01, `MetricStyle` since 1.19.0-alpha01.
   - OEMs may add their own criteria. Whether ZTE MyOS promotes notifications at all is unknown.
6. **Widgets: keep the RemoteViews providers on day 1** with the same component names (map T1, T14). [V/U]
   - **Glance 1.2.0** went stable on 2026-08-26 and has generated previews. [V]
   - Glance's sizing modes, however, are built around size-keyed layouts. That is exactly what the owner's ZTE launcher mis-resolves (T14). Whether `SizeMode.Exact` emits a size map on Android 12+ is undocumented. [U]
   - Android 17 (targetSdk 37) rejects RemoteViews whose bitmaps exceed `6 × w × h` bytes. The app uses no bitmaps today, so keep it that way. [V]
   - Since Android 15, a force-stop greys out every widget until the app is launched again. Nubia's swipe-from-Recents counts as a force stop on the owner's phone, so this is user-visible. [V platform / S device]
7. **AppFunctions are still pre-1.0.** [V]
   - The library is `androidx.appfunctions:1.0.0-beta01` (2026-10-21). The annotation was renamed `@AppFunctionDeclaration` in alpha13, and the app-metadata `description` field became `instructions` in beta01.
   - The platform requirement is Android 16 or later.
   - Gemini integration is a "private preview with trusted testers" as of May 2026, per the docs page dated 2026-10-22.
   - Port the five functions as a thin service over the core, but do not make any product flow depend on an assistant calling them.
8. **Predictive back.**
   - At targetSdk 36 it is on by default: `onBackPressed` is not called and `KEYCODE_BACK` is not dispatched. [V]
   - Use the Navigation 3 1.2.0 / NavigationEvent 1.1.x `NavigationBackHandler` stack, which is KMP and supports gestures started mid-flight. [V]
   - The demo's fight between the container transform and predictive back needs a prototype in slice 0 (§9.2).
9. **Developer verification is now a distribution surface.** [V]
   - Enforcement started on 2026-09-30, but only for "participating stores" in Brazil, Indonesia, Singapore and Thailand. The global rollout is "2027 and beyond", with no month given.
   - Once it reaches the owner's users, an unregistered app can only be installed or **updated** through a one-day "advanced flow" or adb. That would break the planned in-app self-update.
   - Register the package and both signer fingerprints in the Android Developer Console well before then.
   - Do **not** adopt Android 17's hybrid PQC signing (APK Signature Scheme v3.2). It needs new keys and lineage rotation, and AGENTS.md forbids rotating signers.
10. **Things that look tempting but should be rejected:**
    - `systemExempted` FGS, even though its docs say holders of `SCHEDULE_EXACT_ALARM` are eligible.
    - UIDT jobs for "watching a reply": WorkManager does not support them, and the system may stop them "if the job has run for longer than necessary".
    - Glance on day 1.
    - Lock-screen (keyguard) widgets, because anyone can see them.
    - Biometric app lock on day 1, because androidx.biometric's latest stable is still 1.1.0 from 2021.

---

## 1. Version and platform snapshot (as of 2026-11-01)

| Item | Value | Label |
|---|---|---|
| Android 16 (API 36) | stable June 2025. QPR1 (Sept 2025) brought Live Update chips to Pixel. QPR2 = **API 36.1** (Dec 2025): minor SDK, `SDK_INT_FULL`/`VERSION_CODES_FULL.BAKLAVA_1`, lock-screen widgets on phones, automatic themed icons | V (features page) / S (QPR dates) |
| Android 17 (API 37) | stable **2026-06-16**. Platform stability at Beta 3 (2026-03-27). QPR1 = API 37.1 (Pixel, Sept 2026). QPR2 = 37.2 in beta (Beta 7 on 2026-10-09) | S |
| Play target API | new apps and updates must target **36** from 2026-08-31. No date for 37 | S |
| androidx.core | **1.19.1** (2026-09-23). `NotificationCompat.ProgressStyle` + `setRequestPromotedOngoing` since 1.17.0-alpha01. `MetricStyle` since 1.19.0-alpha01. The `NotificationCompat.Builder` reference lists `setShortCriticalText` (version not confirmed). Repo pins **1.13.1** | V / S (shortCriticalText) |
| WorkManager | **2.12.0** (2026-09-23): minSdk 24, experimental `work-analytics` and event listeners. Repo pins **2.10.5** | V |
| Glance | **1.2.0** stable (2026-08-26), 1.3.0-alpha02 (2026-07-01). 1.3 alphas need compileSdk 37 + AGP ≥ 9.2 and add Remote Compose snap scrolling (Android 17 devices only) | V |
| AppFunctions | **1.0.0-beta01** (2026-10-21). No RC or stable | V |
| Navigation 3 | 1.2.0 (2026-09-23): `NavigationBackHandler`, `onBackCompleted`/`onBackCancelled`, mid-flight predictive back. Shared elements between scenes since 1.1.0 | V |
| NavigationEvent | 1.1.x (1.1.0 2026-04-22: compileSdk 37 / AGP ≥ 9.2; all KMP targets since 1.1.0-alpha01) | S (localized release-note copies) |
| androidx.biometric | stable **1.1.0** (2021). **1.4.0-alpha07** (2026-04-22) adds `biometric-compose`, `registerForAuthenticationResult()` and up to four fallback options on 36.1 | V |
| androidx.credentials | 1.6.0 (2026-04-08), 1.7.0-alpha02 (2026-05-06) | S |
| Remote Compose (`androidx.compose.remote`) | alphas only (foundation 1.0.0-alpha04, 2026-10-07) | S |

Repo today (`oc_app-batch1`, read 2026-11-01) [V]:

- SDK levels: `compileSdk 37`, `minSdk max(flutter,26)=26`, `targetSdk = flutter.targetSdkVersion = 36`.
- Manifest:
  - `enableOnBackInvokedCallback="true"`.
  - Foreground-service types: `dataSync|shortService` on `BackgroundConnectionService`, `specialUse` on `BuiltinServerService` and `SetupService`.
  - `POST_PROMOTED_NOTIFICATIONS` is declared.
- Every widget provider is `widgetCategory="home_screen"`.
- The demo (`kmp-demo`) uses `targetSdk 36`, `compileSdk 37`, and a `dataSync` `ReplyService`.

Owner phone: the task brief says NX721J on Android 16. Memory files written on 10-17 and on 2026-11-01 (phantom-process-limit) say Android 15. [U] Check with `adb shell getprop ro.build.version.release` and `ro.build.version.sdk_full`. Read-only adb on the owner's phone is allowed. The answer decides whether Live Updates and AppFunctions can be tested there at all.

---

## 2. What changes at targetSdk 36 and 37, and for everyone on 16 and 17

### 2.1 targetSdk 36 (the Flutter app already targets this)

| Change | Impact on the rewrite | Label |
|---|---|---|
| Predictive back on by default: `onBackPressed` not called, `KEYCODE_BACK` not dispatched | All back handling must go through `OnBackPressedDispatcher`, Compose `BackHandler`/`PredictiveBackHandler`, or NavigationEvent/Nav3. See §9 | V |
| Edge-to-edge opt-out (`windowOptOutEdgeToEdgeEnforcement`) removed | Compose `enableEdgeToEdge()` + insets everywhere, including the IME (`imePadding`) | V |
| Orientation, resizability and aspect-ratio requests ignored on sw ≥ 600dp. Opt-out property `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is temporary | Adaptive layouts are mandatory on tablets and foldables (already an owner rule). Expect more Activity recreation | V |
| `elegantTextHeight` ignored. "UI fonts" discontinued for **Arabic** and other scripts | Arabic line heights change. Kit parts with fixed heights (chips, bars, composer) must be measured in Arabic at 1.0× and 1.3× | V |
| `scheduleAtFixedRate` runs at most one missed execution | Don't use it for anything that must catch up. The scheduler uses alarms and WorkManager, so this is fine | V |
| Safer Intents (`intentMatchingFlags="enforceIntentFilter"`) is opt-in | Optional. The app already uses explicit internal intents and unique `data` URIs (T17) | V |
| Local network protection: opt-in testing only on 16 | Test early with `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>` + reboot | V |

### 2.2 targetSdk 37 (do as a separate slice after migration)

| Change | Impact on this app | Action | Label |
|---|---|---|---|
| **`ACCESS_LOCAL_NETWORK`** runtime permission. Without it, LAN traffic is blocked and TCP **times out**. Covers mDNS, `.local` and inbound LAN. It is in the `NEARBY_DEVICES` group. Local network = "broadcast-capable interface such as Wi-Fi or Ethernet, but excludes cellular (WWAN) or VPN connections" | LAN servers (laptop at 192.168.x.x), LAN Paseo, the Wi-Fi file-share server (inbound) and LAN QR pairing break until granted. Tailscale (VPN) and loopback are unaffected | Ask in context, the first time the person connects to or shares on a local address. Use plain words and one visible row ("Let OpenCode reach computers on your Wi-Fi"). Map "timeout + not granted" to that ask, never to a raw error. `NsdManager` with `FLAG_SHOW_PICKER` avoids the permission for discovery | V (loopback [S]) |
| Certificate transparency on by default | Public-CA HTTPS (Tailscale certs, Let's Encrypt) is fine because those certificates are CT-logged. **Self-signed + pinned servers** may fail if pinning still runs through the platform trust manager | Test both on API 37. A per-domain `<certificateTransparency enabled="false">` exists in the network security config, but domains are runtime-entered, so prefer a custom pinning TrustManager | V (default) / U (self-signed path) |
| BAL hardening extended to `IntentSender`. `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is legacy and should move to `..._ALLOW_IF_VISIBLE` | Self-update (`PackageInstaller` session, `STATUS_PENDING_USER_ACTION`) and any `PendingIntent.send()` that opens UI | Start the installer's user-action intent from the visible Activity. Set `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)` where the app sends its own PendingIntents | V |
| RemoteViews bitmap memory cap `6 × w × h` bytes (screen metrics). `IllegalArgumentException` above it. `partiallyUpdateAppWidget` keeps old images alive | No bitmaps today | Keep widgets bitmap-free, never send images by partial update, and add a test that renders every provider at its largest size | V |
| Background audio: playback, focus and volume need a while-in-use FGS (or the exact-alarm + `USAGE_ALARM` path) | Read-aloud is foreground-only today, so it is safe. "Keep reading with the screen off" would need a `mediaPlayback` FGS | Keep foreground-only, or design the FGS deliberately | V |
| `static final` fields unmodifiable (reflection → `IllegalAccessException`, JNI write → crash). New lock-free `MessageQueue` breaks reflection on its internals | Old libraries that use reflection hacks | Audit dependencies at the targetSdk-37 slice | V |
| Large-screen opt-out gone | Already adaptive | none | V |
| `setContentCaptureEnabled(false)` no longer works. Use `FLAG_SECURE` | Overlay panels use `FLAG_SECURE` already | keep | V |
| Encrypted Client Hello used where the TLS library and the server support it | none expected | none | V |
| Contacts Provider, SMS OTP, Bluetooth RFCOMM, touchpad | not used | – | V |

### 2.3 All apps on Android 16 and 17 (no opt-out by target)

| Change | Impact | Label |
|---|---|---|
| **Android 17 Memory Limiter** (cgroup v2 `memory.high` per process, by RAM tier and process state). Exits show `REASON_OTHER` + description `MemoryLimiter:AnonSwap`. Devices with ≥ 18,432 MiB or < 3,200 MiB are exempt. Test with `adb shell am memory-limiter manual <pid> <MB>` / `ignore <uid>` / `status` | proot + node + agents + builds. See §5 | V (mechanism) / U (children counted) |
| Android 17: cross-profile loopback blocked by default (same profile is unaffected) | Only matters if the app runs in Private Space or a work profile while talking to a server in another profile | V |
| Android 17: the IME is **not** restored after a configuration change the app doesn't handle | Rotating with the composer focused drops the keyboard. Either handle `configChanges` in the manifest (Compose copes) or re-request the IME in `onCreate` | V |
| Android 17 QPR1: third-party apps reportedly read `DEVELOPMENT_SETTINGS_ENABLED` and `ADB_ENABLED` as `0` | The planned "Disable child process restrictions" card can no longer tell whether Developer options are on. Whether the phantom flag itself is still readable is unknown | S / U |
| Android 16: JobScheduler and WorkManager runtime quota now also applies to jobs started while visible that keep running, and to jobs running **alongside a foreground service** | Schedule and widget follow-up workers that run while the live FGS is up now count against quota. They are short, so fine; don't stack long workers on the FGS | V |
| Android 16: forced auto-grouping of notifications (AOSP "Notification API hardening") | Every alert kind needs a deliberate group key; T13 already proved auto-grouping breaks taps | S |
| Android 16: 16 KB page-size compat mode with a user-facing dialog for 4 KB-aligned native libraries | Every `.so` (proot set, AI Team engine, Termux PTY JNI, sherpa-onnx, LiteRT-LM) must be 16 KB aligned | V |
| Android 16: `announceForAccessibility` deprecated | Use live regions or pane titles (Compose `liveRegion`, `paneTitle`) | V |
| Android 16: 3-button navigation gets a predictive back preview on long-press | Comes free when predictive back is done right | V |
| Android 16 QPR2: automatic themed icons when no monochrome layer exists | The app ships `ic_launcher_monochrome`; keep it | V |

### 2.4 Older rules that still bind (Android 12 to 15)

- **Android 15, package stopped state** [V]:
  - Entering the stopped state cancels **all** PendingIntents, greys out every widget, and kills alarms.
  - `BOOT_COMPLETED` is delivered when user action brings the app out of that state.
  - `ApplicationStartInfo.wasForceStopped()` tells you it happened.
  - The rewrite must treat `BOOT_COMPLETED` as "re-arm everything and redraw widgets". Today that receiver only restores the server and re-arms schedules.
- **Android 15, background network access** [V]: "apps that start a network request outside of a valid process lifecycle receive an exception." SSE and polling must stop on background unless an FGS or worker owns the work.
- **Android 15, BOOT_COMPLETED** [V]: cannot start `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` or `microphone` FGS. `specialUse` is allowed, so restoring the phone server on boot stays legal.
- **Android 15** [V]: `PendingIntent` creators block background activity launches by default. The `SYSTEM_ALERT_WINDOW` FGS exemption now also needs a visible overlay.
- **Android 14** [V/S]: `TileService.startActivityAndCollapse(Intent)` throws for targetSdk 34+; use the `PendingIntent` overload (the code already does). Types are mandatory on every FGS. Context-registered receivers need an exported flag.
- **Android 12** [V]: background FGS starts need an exemption (§3.3). Notification trampolines are banned. `PendingIntent` mutability must be declared.

---

## 3. Foreground services and background execution

### 3.1 Types this app needs

| Service (keep the class name) | Type | Why | Limits | Label |
|---|---|---|---|---|
| `BackgroundConnectionService`, live / "watch this reply" | `dataSync` | keeps SSE alive to deliver needs-you and finished alerts while the app is in the background | 6 h per 24 h in the background, shared by all `dataSync` services and reset when the app comes to the foreground. `onTimeout(startId, fgsType)` (API 35), then `stopSelf()` within seconds or an internal exception. Restarting after the budget is spent throws `ForegroundServiceStartNotAllowedException`. Not from `BOOT_COMPLETED` | V |
| `BackgroundConnectionService`, widget action (≤ 30 s) | `shortService` (alone) | a widget tap answering Allow once, Stop, etc. | ~3 min. Combining it with another type drops `shortService`. It cannot start other FGS. `onTimeout(startId)` on API 34 | V |
| `BuiltinServerService` (phone server), `SetupService` | `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | runs the person's own server or installer | no platform timeout. Play reviews the subtype text (not on Play today). The Memory Limiter's "not visible" tier applies (§5) | V |
| Optional later: read-aloud in the background | `mediaPlayback` | needed at targetSdk 37 for any background audio | while-in-use FGS rules | V |

Rejected options:

- `systemExempted`: the docs list exact-alarm permission holders as eligible, but the type is "reserved for system applications and specific system integrations". Using it would be a policy and honesty problem. [V]
- `connectedDevice`: needs Bluetooth, NFC, USB or Wi-Fi-change prerequisites and is meant for nearby devices. [V]
- UIDT jobs: these need `RUN_USER_INITIATED_JOBS` and must be scheduled while visible. There is no WorkManager support. The system may stop them "if the job has run for longer than necessary" and retry later, which is wrong for an open SSE stream. [V]

### 3.2 Background strategy recommendation (map §17 decision 8)

- **Default:** when the app goes to the background with a turn running or a request pending, start a `dataSync` "watching this reply" service.
  - Start it while still visible: `onStop` of the last Activity counts as a transition from a user-visible state, which is an exemption.
  - Stop it as soon as the turn ends or the person answers, plus a short grace period to post the finished alert.
  - This is the demo's proven pattern. One long turn uses minutes of the budget, not hours, and every app open resets the 6 h budget.
- **"Stay connected" (always-on live mode)** remains an explicit opt-in with the existing pause receipts (map 02/03). It is the only path that can hit the 6 h cap. Its copy must say so in plain words ("Android lets this run about 6 hours a day while the app is closed").
- **Phone server running:** the `specialUse` FGS already keeps the process alive, and alerts post from the same core. No second service is needed. The shared status row (id 4747) stays one row.
- **Longer term:** push (UnifiedPush or FCM) from a server-side relay is the only way past FGS budgets. It needs a server component and is out of scope for the rewrite.

### 3.3 Starting a foreground service legally (exemptions this app uses) [V]

- Transition from a visible Activity.
- User interaction with a widget, notification or bubble. `PendingIntent.getForegroundService` straight from a widget tap is fine (T20).
- `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`. Not for `dataSync` at targetSdk 35+.
- `TIMEZONE_CHANGED`, `TIME_CHANGED`, `LOCALE_CHANGED`.
- **An exact alarm the user asked for.** This makes it legal to start the phone-server lease (`specialUse`) from a scheduled prompt's alarm. A WorkManager fallback run without an alarm is not covered.
- Battery optimisation turned off by the person.
- Unknown [U]: whether an AppFunction call (the system binds the `AppFunctionService` on behalf of a visible assistant) counts as an exemption for starting the phone server. Test it on API 36/37 before `startConversation` relies on it.

### 3.4 Code notes

```kotlin
// One start helper for every FGS. API 29+ needs the type; API 34+ needs the manifest type too.
ServiceCompat.startForeground(this, STATUS_ID, notification,
    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)

// Android 15+: dataSync / mediaProcessing budget reached (also fires for other timed types).
override fun onTimeout(startId: Int, fgsType: Int) {
    pauseStore.timeout()          // commit the pause receipt first (T9)
    statusNotification.pausedByAndroid()
    stopSelf()                    // within seconds, or the system throws
}
// Android 14 only: shortService timeout callback.
@Suppress("OVERRIDE_DEPRECATION")
override fun onTimeout(startId: Int) { settleWidgetActionAsUncertain(); stopSelf() }
```

- Keep the "promised FGS must show its notification" guard (T8). `startForegroundService` from the background without `startForeground` within seconds crashes the app.
- Testing hooks [V]:
  - `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <pkg>`
  - `adb shell device_config put activity_manager data_sync_fgs_timeout_duration <ms>`
  - The emulator CI should run the 6 h path in 60 s.

---

## 4. WorkManager and alarms

- **Pin WorkManager 2.12.0** [V]. It needs minSdk 24 (the app has 26) and defaults to `Dispatchers.Default`. It fixes background network calls failing on Android 15 and periodic work that was never rescheduled after an uncaught exception. Optional `work-analytics` (experimental) can feed the Performance/diagnostics page with run history, all local.
- **WorkManager is Android-only.** The KMP seam is the map's `JobScheduler` expect/actual. Keep the schedule *engine* in `commonMain` and the arming code in `androidMain`.
- **Expedited work** is right for the alarm-fired schedule run. Android 16 quota changes apply to jobs that run alongside an FGS, so keep `RUN_AS_NON_EXPEDITED_WORK_REQUEST` as the fallback. [V]
- **Debugging:**
  - Android 16 adds `JobScheduler#getPendingJobReasons(jobId)` and `getPendingJobReasonsHistory(jobId)`.
  - Android 17 adds `JobDebugInfo.getPendingJobReasonStats()`.
  - Surface them in diagnostics as fixed reason codes, never raw text. [V]
- **Exact alarms** [V]:
  - `SCHEDULE_EXACT_ALARM` is user-grantable. Ask only from an explicit tap (already the rule).
  - Android 17 adds `setExactAndAllowWhileIdle(..., OnAlarmListener)`. A listener only works while the process lives, so it does **not** replace the PendingIntent alarm for schedules.
- **Migration** [V/U]:
  - WorkManager's database keeps worker class names. Keep `ScheduledPromptWorker` and `WidgetStatusWorker` at the same fully qualified names, or cancel by tag and re-enqueue in the first-launch migration.
  - Alarms are dropped when the package is replaced, so re-arm on `MY_PACKAGE_REPLACED` (T2).
  - A force-stopped package gets neither broadcast until it is opened (restore-proof recipe).
- **R8:** keep the worker constructors and `WorkDatabase_Impl` (T27).

---

## 5. Phantom processes and the Android 17 Memory Limiter (built-in Linux)

**Phantom process limit (Android 12+)** [S, the AOSP behaviour is documented by agnostic-apollo]:

- The default is `max_phantom_processes = 32`. **The limit is for all apps combined, not per app.** The owner's ~14 proot-tree processes compete with every other app's forked processes (Termux, for example).
- Background phantom processes that use excessive CPU while their parent app is in the background are also killed. The check runs every 5 min. This hits long builds an agent runs while the app is backgrounded.
- Android 14+ has the Developer-options toggle "Disable child process restrictions" (flag `settings_enable_monitor_phantom_procs`). **Turning Developer options off resets it.**
- Nothing found says Android 15, 16 or 17 changed this. [U]
- The rewrite does not change the count. Only fork/exec children count. In-app Kotlin threads and `:local_pdf`-style AMS-managed processes do not.

**Memory Limiter (Android 17+)** [V, source.android.com 2026-10-20]:

- It is a per-process cgroup v2 `memory.high`. Classes:
  - Visible: `TOP`, `BOUND_TOP`, `IMPORTANT_FOREGROUND`, `TOP_SLEEPING`.
  - Not visible: `FOREGROUND_SERVICE`, `SERVICE`, …
  - Cached: frozen and reclaimed.
- Example tiers (memory / swap, MiB):

  | RAM | Visible | Not visible |
  |---|---|---|
  | 8 GB | 5120 / 3072 | 3072 / 3072 |
  | 12 GB | 8192 / 4096 | 4096 / 4096 |
  | 16 GB | 10240 / 5120 | 5120 / 5120 |

  Devices with ≥ 18,432 MiB are exempt. OEMs set the values in `memory-limiter-config.xml`.
- Over the limit, the kernel reclaims and swaps and the app is throttled. Only in extreme cases does it fail to allocate and die.
- Apps cannot query their limit. Android 17 `ProfilingManager.TRIGGER_TYPE_ANOMALY` can give a heap dump before enforcement.

What it means here [U]:

- In cgroup v2 a forked child stays in its parent's cgroup unless it is moved. If Android puts the app's main process in `…/pid_<n>`, then proot, node, OpenCode, Claude Code and `flutter test` all count against **one** process's limit: about 5 GiB on a 16 GB phone while only the FGS keeps the app alive.
- That turns "the phone got slow" or exit 137 into a new failure class.
- **Actions:**
  1. Classify `REASON_OTHER` + `MemoryLimiter:AnonSwap` as a distinct exit (`memoryLimit`). Never forward the description (T21).
  2. Teach the process budget (process-budget branch) the memory budget too.
  3. Prove the hypothesis in slice 0 on an API 37 emulator: read `/proc/<child>/cgroup` for a proot child, then `am memory-limiter manual <apppid> 1024` and run a memory-heavy guest command.
  4. Plain-words recovery copy ("Android limited this phone's Linux to about 5 GB while OpenCode was in the background").

---

## 6. Notifications and Live Updates

**Library:** `NotificationCompat` from androidx.core **1.19.1** [V]. It gives `ProgressStyle` and `setRequestPromotedOngoing` (1.17.0-alpha01+), `MetricStyle` (1.19.0-alpha01+), and `setShortCriticalText` (listed in the reference [S]). Compat handles the API-level split.

- The current Kotlin calls the *platform* builder behind `SDK_INT_FULL >= BAKLAVA_1` (36.1, commit `3030fb0b7`), so Android 16.0 QPR1 devices never get the chip.
- With Compat the gate moves into the library. Still check `NotificationManager.canPostPromotedNotifications()` and offer `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` as a visible row, not a menu.

**Promotion requirements** [V]:

- Style must be standard, BigText, Call, Progress or Metric.
- `POST_PROMOTED_NOTIFICATIONS` (normal permission).
- `setRequestPromotedOngoing(true)`.
- Ongoing, with a content title (`MetricStyle` may omit it).
- No `RemoteViews`, not colorized, not a group summary, channel not `IMPORTANCE_MIN`.
- Promoted cards are expanded and cannot be collapsed. The person can demote them.
- "Don't repost Live Updates that the user dismissed. Use `setDeleteIntent`." OEMs may add criteria.
- Chip rules: maximum width 96dp. Text under 7 characters shows whole; if less than half fits, only the icon shows. `setWhen` at least 2 min in the future shows a countdown. Chronometer via `setUsesChronometer`.

**Policy fit for this app** [V policy, my mapping]:

| Status kind (map 05) | Promote? | Why |
|---|---|---|
| WORKING / SEVERAL_WORKING (a run the person started) | **yes** | user-initiated, in progress, time-sensitive |
| NEEDS_YOU *inside* a running turn | yes, as that run's state | it is the run's current step. Chip copy "Needs you". Never promote a needs-you that wasn't part of a run the person started (for example a remote team gate) |
| reconnecting / waiting (provider retry) | yes, only while the turn is still active | still the same activity |
| idle / ready / paused / setup finished | **no** | Google lists "quick access to app features" and "alerts" as inappropriate |
| finished-reply alert, check-ins, quota | **no** | alerts and messages |

**Styles:**

- **ProgressStyle** for "5 of 6 tasks": one segment per todo, points for steps, a tracker icon of the agent. Owner decision 7 in the map.
- **MetricStyle** (Android 17): up to 3 metrics (label + value + optional unit), up to 3 actions. Candidates: *elapsed* (`TimeDifference`), *tasks* (`FixedInt`). Skip cost and tokens (simple and natural).
- Android 17 semantic colours (`SEMANTIC_STYLE_SAFE/CAUTION/DANGER/INFO`) map cleanly onto the kit's mint/amber/red. Use CAUTION for needs-you and never DANGER for a normal stop. [V]

**Sketch** (method names follow the platform API; check the Compat equivalents) [U]:

```kotlin
val style = NotificationCompat.ProgressStyle()
    .setProgressSegments(todos.map { NotificationCompat.ProgressStyle.Segment(1) })
    .setProgress(doneCount)
    .setProgressTrackerIcon(IconCompat.createWithResource(ctx, R.drawable.ic_agent))
val n = NotificationCompat.Builder(ctx, "opencode_live_connection")   // channel id unchanged (T1)
    .setSmallIcon(R.drawable.ic_status)
    .setContentTitle(liveLine)                      // "Running tests" (plain words)
    .setContentText("$chatTitle · $done of $total") // names the target
    .setOngoing(true).setOnlyAlertOnce(true)
    .setStyle(style)
    .setRequestPromotedOngoing(isUserStartedRunActive)
    .setShortCriticalText(chip)                     // ≤ 7 chars recommended, check Arabic plural
    .setDeleteIntent(dismissedPi)                   // remember dismissal; don't re-promote this run
    .setPublicVersion(publicCountOnly)              // lock screen: fixed words (T3)
    .build()
```

**Keep from today** (map 05–07, T3, T4, T11–T13, T17): the one status row id 4747, shared owners, 1 post per 2 s throttle, fixed public versions, Allow-once only with `setAuthenticationRequired(true)` (API 31+), never "Always allow", exact request-id binding, unique request codes and `data` URIs, own group key per scheduled run, channel ids unchanged so the person's per-channel settings survive the update.

**Localisation:**

- A single string table for the app and native surfaces fixes T16 (only en/ar are native today).
- Rename channels in place on a locale change.
- Short chip text must also fit in Arabic. Test `"يعمل"`-length strings at the 96dp cap.

---

## 7. Home-screen widgets

### 7.1 Platform facts

- **Sizing (Android 12+)** [V]:
  - `targetCellWidth/Height` take precedence over `minWidth/Height`. `maxResizeWidth/Height` are a cap the launcher can exceed.
  - Responsive layouts are `RemoteViews(Map<SizeF, RemoteViews>)` from "a small set of layouts".
  - Exact layouts come from `OPTION_APPWIDGET_SIZES` in `onAppWidgetOptionsChanged`. "the list of sizes might be empty or null" on some launchers.
  - Pixel 4 grid formula: portrait `(73n−16)×(118m−16)` dp, landscape `(142n−15)×(66m−15)` dp, so 4×2 ≈ 276×220 portrait / 553×117 landscape.
  - **The ZTE MiFavor launcher mis-resolves size maps (T14).** Keep rendering one RemoteViews for the current size. The documented "exact" path still hands the launcher a map, which is exactly what failed.
- **Previews** [V]:
  - Generated previews need Android 15 (API 35), `AppWidgetManager.setWidgetPreview` (Glance: `providePreview` + `GlanceAppWidgetManager.setWidgetPreviews`).
  - "rate-limited… approximately two calls per hour". There is no system callback: set them on first launch, after the first data, or periodically.
  - `previewImage` is still required for Android 14 and below, when no generated preview is available, and for the owner's launcher (it ignores `previewLayout`; generated-preview support on ZTE is unknown [U]).
- **Updates** [V]:
  - `updatePeriodMillis` has a 30 min minimum; the app uses 0 and is event-driven (keep).
  - Receivers get 10 s before an ANR; use `goAsync` or a worker.
  - Add `FLAG_RECEIVER_FOREGROUND` on widget broadcast PendingIntents for priority.
  - Android 17 (targetSdk 37): bitmap cap and no image swaps by partial update (§2.2).
- **Force-stop** [V]: since Android 15 all PendingIntents are cancelled and widgets grey out until the next launch. On the owner's Nubia a swipe from Recents force-stops (map 44). So: (a) teach "lock OpenCode in Recents" in Keep-running guidance (already there); (b) on any start where `wasForceStopped()` is true, redraw widgets, re-arm schedules and re-publish shortcuts at once.
- **Engagement metrics** [S]: Android 16 `AppWidgetEvent` (clicks, scrolls, impressions) needs compileSdk 36.1, reports hourly, plus `RemoteViews.setAppWidgetEventTag`. Don't adopt. There is no user value, and the app keeps no telemetry.
- **Lock-screen widgets** [S]: Android 16 QPR2 brought them to phones, and "anyone can view them". Keep `widgetCategory="home_screen"` only. A keyguard variant would need a public, content-free layout and is a separate owner decision.

### 7.2 RemoteViews or Glance

| | RemoteViews XML (today, 4.3k Kotlin lines) | Glance 1.2.0 |
|---|---|---|
| Maturity | platform | stable since 2026-08-26. Glance's own overview still says "in active development" |
| Code reuse | binders, policies and Robolectric render tests port as-is | rewrite of every layout |
| ZTE size bug | solved (render once for the current size) | `Responsive` = size map (bad). `Exact` documented as "content for the actual current size" but whether it emits a map on 12+ is undocumented [U]. `Single` + reading `LocalAppWidgetOptions` yourself might work [U] |
| Theming | `WidgetGraphite` colours, Material You via `system_accent1` | `GlanceTheme` + `glance-material3` `ColorProviders(light, dark)`, dynamic colour on 12+ |
| Testing | Robolectric real-layout renders | `runGlanceAppWidgetUnitTest` (no UI Automator) |
| Previews | `setWidgetPreview(RemoteViews)` | `providePreview` |
| Fonts | system | system fonts only, no custom fonts |
| Future | – | Remote Compose (snap scrolling) on Android 17 with Glance 1.3 alpha |

**Recommendation:**

- RemoteViews on day 1. Keep every provider class name, the info XML, `previewImage` PNGs and the render-once policy. Add `setWidgetPreview` on API 35+ from the same binders.
- Re-evaluate Glance only after a ZTE proof that `SizeMode.Exact` (or `Single` with a self-computed size) draws a 4×2 correctly.

**Owner rules applied to widgets:**

- 48dp touch targets.
- Actions name their target: "Allow npm install in <chat>" (content descriptions).
- Mirrored layouts for Arabic. Frame text (header, buttons) capped via dp sizes at `min(fontScale, 1.3)`; row text follows the system scale (map 24 already divides height by font scale).
- Teach hidden widget actions (Edit, Reply) through Help › Tips.

---

## 8. AppFunctions

**Facts** [V]:

- Requirements: Android 16 (API 36) or later and `androidx.appfunctions:1.0.0-beta01` with `ksp("androidx.appfunctions:appfunctions-compiler:1.0.0-beta01")`. Testing artifact: `appfunctions-testing`.
- Recent API churn:
  - alpha10: `@AppFunctionServiceEntryPoint`; every function lives in that service.
  - alpha12: `AppFunctionContext` removed.
  - alpha13: `@AppFunction` → `@AppFunctionDeclaration`; `@AppFunctionAccessLevel` added.
  - beta01: `AppFunctionName` moved; app-metadata `description` → `instructions`; `isDescribedByKDoc` has no default.
  - The overview page (2026-10-22) still shows the old `@AppFunction`, so it lags the library.
- Callers need `EXECUTE_APP_FUNCTIONS`, which only system or approved agents get. Gemini integration: "As of May 2026 … private preview with trusted testers". The Early Access form was closed when the owner tried (memory).
- Testing: `adb shell cmd app_function list-app-functions` / `… execute`. Errors should use `AppFunctionInvalidArgumentException`, `AppFunctionElementNotFoundException` and similar so agents get a reason.

**For this app:**

- Port the five branch functions (`startConversation`, `listActive`, `answerRequest` once/reject only, `stopConversation`, `sendMessage` that **steers** a running turn and never queues) as a thin `AppFunctionService` in `androidMain` that calls the shared core. There is no Dart bridge anymore.
- Restrict with `@AppFunctionAccessLevel` if it supports "system agents only" [U].
- Results never carry passwords, tokens, addresses or paths (map 59).
- `answerRequest` must apply the same "never allow what you cannot read" and exact-request-id rules as notifications (T4).
- Keep it out of the critical path, and keep the library version behind a single catalog entry, since beta → RC may rename again.
- Proof: execution, not only registration, on an API 36/37 device. Execution was flaky on the emulator (memory).

---

## 9. Native-feel surfaces: predictive back, edge-to-edge, haptics, IME

### 9.1 Predictive back [V]

- Android 13 had a developer option; Android 15 shows the system animations for opted-in apps; at targetSdk 36 it is the default and the old callbacks stop.
- Android 16 adds a long-press back preview on 3-button navigation and `PRIORITY_SYSTEM_NAVIGATION_OBSERVER` (observe without consuming). Use it for analytics-free "left this screen" bookkeeping instead of consuming back.
- Compose: `BackHandler` / `PredictiveBackHandler` (activity-compose ≥ 1.8). In common code use the NavigationEvent `NavigationBackHandler` (KMP) and Navigation 3 1.2.0, which handles gestures started during a forward transition (1.1.7 / 1.2.0-beta01 fix b/456218398).
- Never keep a callback enabled "just in case". Enable or disable it from UI state, or the system animation stops (back-to-home, cross-activity).

### 9.2 The demo's container-transform conflict

Options, in order:

1. Nav3 shared elements between scenes (1.1.0+, `SharedTransitionScope` into `NavDisplay`) with `Modifier.sharedBounds` for row → page, letting the back gesture *seek* the same transform in reverse.
2. If seeking is janky, use the container transform only for forward navigation and the standard Material predictive-back page motion (scale + edge shift) for back. This is the demo's workaround, made explicit.

Either way: 150–250 ms for the release animation, honour reduced motion (no shared-bounds morph, plain fade), no new UI levels. Unknown [U]: whether `sharedBounds` + predictive seeking is smooth on Compose 1.13 / CMP 1.13 beta. Prototype it in slice 0 on the emulator and the ZTE phone.

### 9.3 Edge-to-edge and IME

- Edge-to-edge is enforced (targetSdk 35/36).
- Use `adjustResize` + `WindowInsets.ime` / `imePadding()`. The composer should follow the IME with `WindowInsetsAnimation` (Compose does this) so the keyboard opening feels native.
- Android 17 won't restore the IME after recreation. The Flutter manifest handles most config changes itself; keep a similar `configChanges` set on `MainActivity` (Compose handles them) to avoid recreation, keyboard loss and scroll loss on rotate, fold or locale change. [V/U]

### 9.4 Haptics

- Compose `LocalHapticFeedback` with `HapticFeedbackType` constants that live in **common** code (KMP): `Confirm`, `Reject`, `ToggleOn/Off`, `SegmentTick`, `GestureThresholdActivate`, `ContextClick`, `LongPress`, `TextHandleMove`, `KeyboardTap`, `VirtualKey`. [S]
- On Android they map to `View.performHapticFeedback`, so the person's touch-feedback setting is respected.
- Use them for: Send (`Confirm`), Allow (`Confirm`), Stop (`ContextClick`; avoid `Reject`, which signals a failure), swipe actions crossing their threshold (`GestureThresholdActivate`), model/agent pickers (`SegmentTick`).
- Android 16 envelope haptics (`VibrationEffect` amplitude/frequency curves) are not needed.

### 9.5 Other

- Stretch overscroll is the default in Compose on Android 12+.
- Ripples come from Material 3 `ripple()` (kit-only).
- Text scale: frame text capped at 1.3× via a clamped `TextUnit`/`Density` in kit parts; reading text uses full scale (owner rule).

---

## 10. Quick Settings tiles [V]

- Keep `AttentionTileService` and `PauseTileService` class names (T1).
- Use **active mode** (`<meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="true"/>`) and call `TileService.requestListeningState(ctx, ComponentName(...))` when the pending count or pause state changes. The system then binds only on change, instead of on every shade pull.
- Declare the Pause tile `TOGGLEABLE_TILE`.
- Labels up to 18 characters (Arabic too). Icon: 24dp white `VectorDrawable`. Use `setSubtitle` / `setStateDescription` for "2 need you".
- Activity launch only via `startActivityAndCollapse(PendingIntent)` with `FLAG_ACTIVITY_NEW_TASK`. Use `unlockAndRun` for anything that reveals content on a secure lock screen.
- Long-press opens App info unless an Activity handles `ACTION_QS_TILE_PREFERENCES`. Route it to Settings › Notifications › Background, so the long-press lands somewhere useful.
- `requestAddTileService()` (Android 13+), "only in context": offer "Add a Pause tile to Quick Settings" as a visible row on the Background settings page, not a pop-up. The system stops showing the prompt after repeated denials.
- No Android 15, 16 or 17 tile changes were found.

---

## 11. Launcher shortcuts [V]

- The API allows up to 15 static + dynamic shortcuts; Google recommends **4**. Short label ≤ 10 characters, long label ≤ 25. Call `reportShortcutUsed()` when the action happens in-app. Use `pushDynamicShortcut` for recency.
- Keep:
  - static ids `connect` and `new_task`;
  - dynamic `oc.session:<profile>:<session>` (max 4);
  - "disable, don't remove" for pinned shortcuts that went stale;
  - the `MainActivity` FQCN (pinned shortcuts store it).
- Dynamic shortcuts aren't restored on a new device (and `allowBackup=false`), so re-publish on every launch.
- Rate limiting exists (`ShortcutManager.isRateLimitingActive`). Swallow errors as today.
- Long-press shortcuts are hidden gestures: register a tip (teach-what-is-hidden).
- `core-google-shortcuts` (donate to Google) is not needed. AppFunctions is the assistant surface.

---

## 12. Share in and share out

- **In** (today `SEND text/plain`):
  - Add `SEND` / `SEND_MULTIPLE` for `image/*` (prompt photos) and maybe `text/*` files, behind the same consume-once capture (T18).
  - Shared content lands in the composer of a new or chosen conversation. It is never auto-sent.
- **Direct Share** [V]:
  - Sharing shortcuts (`<share-target>` in `shortcuts.xml` + `setCategories` + `setLongLived(true)` on the same session shortcuts) would put "Fix login bug · api-server" in the system share sheet.
  - The rules: Direct Share targets must open "a UI where they can perform an action directly on the subject", the composer of that conversation with the text in place, with no disambiguation screen. Don't publish conversations idle for more than 30 days. Share targets count toward the launcher's long-press total.
  - Owner decision: worth it as "obvious option" or noise? Recommend yes for the top 2–3 recent conversations.
- **Out** [V]:
  - Keep `EXTRA_EXCLUDE_COMPONENTS` (never offer itself).
  - Android 18 plans to stop automatic URI grants for `ACTION_SEND` / `SEND_MULTIPLE` / `IMAGE_CAPTURE`, so always add `FLAG_GRANT_READ_URI_PERMISSION` and a `ClipData` when sharing a `FileProvider` URI (`openLocalFile`, Wi-Fi share, diagnostics).
  - Detect gaps with `StrictMode.VmPolicy.detectImplicitUriPermissionGrant()` in debug builds.

---

## 13. Credentials, Keystore and biometrics

- **Secrets** (detail in `map/state-data.md` §2.2) [V/U]:
  - Read the `flutter_secure_storage` v11 envelope (RSA-OAEP-wrapped AES-128-GCM key, Keystore alias `<package>.FlutterSecureStoragePluginKeyOAEP`).
  - Re-store each secret under a new non-exportable Keystore AES-256-GCM key.
  - Do **not** bind those keys to user authentication (`setUserAuthenticationRequired`). Workers, widget actions and scheduled runs must read secrets with no UI (map R11). `androidx.security:security-crypto` is deprecated; don't use it.
  - The Android 17 per-app key limit (50,000 at targetSdk 37) is irrelevant at this scale. [V]
- **Unlocked-phone gates:**
  - keep `Notification.Action.Builder.setAuthenticationRequired(true)` for Allow once (API 31+);
  - keep `KeyguardManager.isKeyguardLocked` refusal for widget actions;
  - use `TileService.unlockAndRun` for tiles.
  These are platform-native and need no biometric library.
- **BiometricPrompt** [V]: androidx.biometric stable is still **1.1.0** (2021). The modern API (`registerForAuthenticationResult`, `biometric-compose`, up to 4 fallback options on 36.1) is **1.4.0-alpha07**. `IDENTITY_CHECK` is a privileged authenticator bit, not for normal apps. There is no day-1 need. If the owner later wants an app lock, call the platform `BiometricPrompt` (API 30+ `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`) directly rather than pinning an alpha.
- **Credential Manager** (androidx.credentials 1.6.0 [S]): no passkey or password sign-in exists in this app. GitHub uses device flow. Not needed.
- **Autofill:** mark server password fields with Compose `ContentType.Password` and username fields as usernames so the person's password manager can fill them. It is free, native, and removes typing.

---

## 14. Distribution surfaces: developer verification, signing, 16 KB

- **Developer verification** [V]:
  - Timeline: the console opened to all in March 2026; August 2026 brought the APIs, limited-distribution accounts and the "advanced flow"; **2026-09-30** enforcement began for installs from participating stores (Google Play, HONOR, OPPO, Galaxy Store, Palm, V-Appstore, GetApps [S]) in BR/ID/SG/TH; global rollout in "2027 and beyond".
  - **Updates** to an unregistered app need the advanced flow (one-day wait, biometric re-auth, then 7 days or indefinitely) or adb.
  - Registration needs the package name + **SHA-256 of every signing key**. The console "lets you add and verify multiple signing keys for a single package". "If you lose your signing key you won't be able to register".
  - A full-distribution account costs $25 with ID verification. Limited distribution (≤ 20 devices, free) suits the owner-only phase but not "real users".
  - Open-source repositories that re-sign (F-Droid) need their key added, or an `adi-registration.properties` asset.
  - **Action:** register `io.github.eslamasabry.opencode_mobile` (and `.preview`) with both known signers (`1DE5BF08…D60C` local release, `2D010C21…D0EC` stable CI) before the global date. Note: Egypt isn't named in any phase yet [U].
- **Signing** [V/S]: Android 17 adds APK Signature Scheme v3.2 (hybrid classical + ML-DSA). It needs new hybrid keys and lineage rules, and Android 16 and lower ignore it. Don't adopt: same signer on day 1 (AGENTS.md, task brief).
- **16 KB pages** [V]: Android 15+ supports 16 KB devices; Android 16 shows a compat-mode dialog for 4 KB-aligned libraries. The builtin-linux map says the proot set is aligned. Re-check every native dependency the rewrite adds or keeps (Termux `terminal-emulator` JNI from JitPack, sherpa-onnx, LiteRT-LM, the AI Team engine) with `zipalign -c -P 16 -v 4 app.apk` and an ELF `p_align` check in CI [U per library].

---

## 15. Day-1 in-place update checklist for these surfaces

1. **Same component FQCNs**: providers, tiles, receivers, services, `MainActivity`, `WidgetConfigureActivity`, workers (map §3, T1). Placed widgets, tiles, pinned shortcuts and WorkManager rows survive only on exact names.
2. **Same notification channel ids**: the person's sound and importance choices survive. Granted runtime permissions (notifications, exact alarm, battery exemption, mic, camera) survive a same-package update.
3. **`MY_PACKAGE_REPLACED` receiver**: re-arm alarms (T2), restore the phone server (specialUse is allowed), redraw every widget, re-publish dynamic shortcuts. [U] Whether AppWidgetService itself re-sends `APPWIDGET_UPDATE` after the update; don't rely on it.
4. **`BOOT_COMPLETED` = also "left the stopped state"** (Android 15): same re-arm and redraw set.
5. **targetSdk stays 36** for the switch release (§0.1).
6. **The in-app updater** must exist in release 1 (map R5). Plan for developer verification and the Android 17 `IntentSender` rules before targetSdk 37.
7. **Emulator proof of the update path** (restore-proof recipe): install the Flutter build, place both widgets, both tiles, a pinned shortcut, a schedule and a channel customisation, `pm unstop`, `install -r` the native APK, and check that all of them survive.

---

## 16. Recommendations for this app (ranked)

1. **targetSdk 36 / compileSdk 37 for the first native release.** Schedule a "targetSdk 37" slice right after, with: the local-network permission UX (§2.2), a CT test against self-signed and pinned servers, the installer under the `IntentSender` BAL rules, a reflection audit, and the RemoteViews budget test.
2. **Background:**
   - a `dataSync` "watch this reply" service started on background-with-active-turn, stopped at settle;
   - live mode stays an opt-in with honest 6 h copy;
   - phone server stays `specialUse`;
   - implement both `onTimeout` overloads;
   - classify `ForegroundServiceStartNotAllowedException` and `onTimeout` as plain pause reasons (existing receipts).
3. **Memory Limiter readiness:** new exit class, diagnostics hook, slice-0 cgroup proof on API 37, budget guard extended to memory.
4. **Notifications on `NotificationCompat` 1.19.1:** promote only an active user-started run, ProgressStyle for todos, delete-intent respect, semantic colours on 17, no promotion for alerts.
5. **Widgets:** RemoteViews day 1, unchanged names and previews; add `setWidgetPreview` on API 35+; redraw on start after `wasForceStopped()`; no bitmaps; home screen only.
6. **AppFunctions:** thin native service on beta01 behind one version-catalog entry; execution proven on a device before claiming it; never a required path.
7. **Predictive back:** NavigationEvent + Nav3 1.2.0, and a slice-0 prototype of shared-bounds seeking vs forward-only transform.
8. **Tiles:** active mode + toggleable + preference long-press + in-context "Add tile" row.
9. **Shortcuts and share:** keep the ids; add `image/*` share-in; consider Direct Share for 2–3 recent conversations; explicit URI grants on share-out.
10. **Distribution:** register package + both signers in the Android Developer Console in Q4 2026; no PQC signing; 16 KB alignment check in CI.
11. **Library pins** (re-check at slice 0): androidx.core 1.19.1, work-runtime(-ktx) 2.12.0, appfunctions 1.0.0-beta01, glance (later) 1.2.0, navigation3 1.2.0, navigationevent 1.1.x, activity-compose 1.13.0. The version sources are in `kmp-cmp-status.md` and §1.

---

## 17. Unknowns: verify in slice 0

| # | Unknown | How to settle |
|---|---|---|
| U1 | Owner phone OS: Android 15 or 16? QPR level? | `adb shell getprop ro.build.version.release`, `ro.build.version.sdk_full` (read-only adb is allowed) |
| U2 | Does ZTE MyOS promote Live Updates at all? | post a promoted test notification. Read `FLAG_PROMOTED_ONGOING` and `canPostPromotedNotifications()` over adb (`dumpsys notification`) |
| U3 | Are proot children inside the app process's Memory Limiter cgroup? | API 37 emulator: `cat /proc/<proot-child>/cgroup`, then `am memory-limiter manual <pid> 1024` + a memory hog in the guest |
| U4 | Is loopback exempt from `ACCESS_LOCAL_NETWORK` (Android docs don't say), and is a Tailscale 100.x route always "VPN"? | targetSdk-37 test build on API 37: connect to 127.0.0.1, 100.x and 192.168.x with the permission denied |
| U5 | CT at targetSdk 37 with self-signed pinned certs | same test build against a self-signed OpenCode 2 server |
| U6 | Glance `SizeMode.Exact`: single RemoteViews or size map on 12+? | inspect `RemoteViews` produced in a Robolectric/Glance unit test, then try it on the ZTE launcher |
| U7 | Does the ZTE launcher show generated previews (`setWidgetPreview`)? | API 35+ device only. Owner phone if Android 16 |
| U8 | Does an AppFunction call permit a background FGS start (phone server)? | API 36/37 device: `cmd app_function execute … startConversation` with the app backgrounded |
| U9 | Can apps still read `settings_enable_monitor_phantom_procs` on Android 17 QPR1+, given the reported redaction of developer settings? | API 37.1 emulator image, `Settings.Global.getString` from the app |
| U10 | Exact API levels of `setRequestPromotedOngoing` / `canPostPromotedNotifications` (36 vs 36.1) | API reference "Added in" lines. Compat removes the need in practice |
| U11 | Does AppWidgetService resend `APPWIDGET_UPDATE` after a package replace? | emulator update test with logging in `onUpdate` |
| U12 | 16 KB alignment of every third-party `.so` the native app keeps | CI `zipalign -P 16` + ELF check |
| U13 | Is the `@AppFunctionAccessLevel` semantics enough to restrict callers? | read beta01 KDoc / sample |
| U14 | Shared-bounds seeking smoothness under predictive back in Compose 1.13 / CMP 1.13 | slice-0 prototype on emulator + ZTE |
| U15 | Developer verification: date and region for global enforcement; whether Egypt-based developers can register | re-check developer.android.com/developer-verification monthly |

---

## 18. Sources (read 2026-11-01; page date in brackets)

Primary [V]:

- Android 17 behavior changes, apps targeting 37: https://developer.android.com/about/versions/17/behavior-changes-17 [2026-10-31]
- Android 17 behavior changes, all apps: https://developer.android.com/about/versions/17/behavior-changes-all [2026-10-28]
- Android 17 features and APIs: https://developer.android.com/about/versions/17/features [2026-10-28]
- Android 16 behavior changes, targeting 36: https://developer.android.com/about/versions/16/behavior-changes-16 [2026-10-28]
- Android 16 behavior changes, all apps: https://developer.android.com/about/versions/16/behavior-changes-all [2026-10-28]
- Android 16 features: https://developer.android.com/about/versions/16/features [2026-10-28]
- Android 15 behavior changes, all apps (stopped state, background network, 16 KB): https://developer.android.com/about/versions/15/behavior-changes-all [2026-10-28]
- Android 15 behavior changes, targeting 35 (BOOT_COMPLETED FGS types, BAL): https://developer.android.com/about/versions/15/behavior-changes-15 [2026-10-28]
- Local network permission: https://developer.android.com/privacy-and-security/local-network-permission [2026-10-02]
- `ACCESS_LOCAL_NETWORK` reference text (Android reference, mirrored by Microsoft Learn): https://learn.microsoft.com/en-us/dotnet/api/android.manifest.permission.accesslocalnetwork?view=net-android-37.0 [2026-10-16]. Canonical: https://developer.android.com/reference/android/Manifest.permission#ACCESS_LOCAL_NETWORK
- Memory Limiter (AOSP): https://source.android.com/docs/core/perf/memory-limiter [2026-10-20]
- Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update [2026-10-28]
- androidx.core releases: https://developer.android.com/jetpack/androidx/releases/core
- FGS timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout [2026-10-28]
- FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types [2026-10-28]
- FGS background-start exemptions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start [2026-10-28]
- UIDT jobs: https://developer.android.com/develop/background-work/background-tasks/uidt [2026-10-28]
- dataSync alternatives: https://developer.android.com/about/versions/15/changes/datasync-migration [2026-02-26]
- WorkManager releases: https://developer.android.com/jetpack/androidx/releases/work
- Glance releases: https://developer.android.com/jetpack/androidx/releases/glance
- Glance generated previews: https://developer.android.com/develop/ui/compose/glance/generated-previews [2026-10-28]
- Glance build UI / SizeMode: https://developer.android.com/develop/ui/compose/glance/build-ui [2026-10-22]
- Glance theme: https://developer.android.com/develop/ui/compose/glance/theme [2026-10-22]
- Glance overview: https://developer.android.com/develop/ui/compose/glance [2026-09-15]
- Widget layouts: https://developer.android.com/develop/ui/views/appwidgets/layouts [2026-10-28]
- Advanced widgets (updates, Android 17 partial-update warning): https://developer.android.com/develop/ui/views/appwidgets/advanced [2026-10-28]
- AppFunctions releases: https://developer.android.com/jetpack/androidx/releases/appfunctions
- AppFunctions overview: https://developer.android.com/ai/appfunctions [2026-10-22]
- Predictive back guide: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture [2026-10-28]
- Navigation 3 releases: https://developer.android.com/jetpack/androidx/releases/navigation3
- Quick Settings tiles: https://developer.android.com/develop/ui/views/quicksettings-tiles [2026-10-28]
- Shortcut best practices: https://developer.android.com/develop/ui/views/launch/shortcuts/best-practices [2026-10-28]
- Direct Share targets: https://developer.android.com/training/sharing/direct-share-targets [2026-10-28]
- androidx.biometric releases: https://developer.android.com/jetpack/androidx/releases/biometric
- Developer verification: https://developer.android.com/developer-verification (no page date; newest linked post June 2026); FAQ https://developer.android.com/developer-verification/guides/faq [2026-09-30]; open-source registration https://developer.android.com/developer-verification/guides/open-source-app-registration [2026-10-23]
- Repo: `/home/eslam/Storage/Code/wt/oc_app-batch1/android/app/build.gradle.kts`, `AndroidManifest.xml`, `res/xml/widget_*_info.xml`, `StatusNotificationRenderer.kt` (commit `3030fb0b7`); Flutter 3.47.1 `FlutterExtension.kt` (targetSdkVersion 36)

Secondary [S]:

- Android 17 release date and QPR schedule: https://androidauthority.com/android-17-release-date-3639790 , https://www.androidauthority.com/android-17-3561251/ , https://www.issoh.co.jp/md/tech/10993
- Android 17 QPR1 hides developer-settings state: https://androidauthority.com/android-17-qpr1-developer-settings-3713036 (403 on fetch; via search), https://www.comss.ru/page.php?id=22075 , https://helentech.jp/news-android-17-qpr1-developer-options-91428/
- Android 16 QPR1 Live Updates: https://www.androidauthority.com/android-16-qpr1-live-updates-3573399
- Android 16 forced auto-grouping (AOSP change mirror): https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_base/+/02b4a9758eaf35a02b3ebcaea8e6e01ea11e0635%5E%21 ; https://androidauthority.com/android-16-force-group-notifications-3565400
- Lock-screen widgets on phones (16 QPR2): https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/
- MetricStyle guide (search snippet): https://developer.android.com/develop/ui/views/notifications/metric-style
- `setShortCriticalText` in `NotificationCompat.Builder` reference (search snippet): https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Builder
- Widget metrics (search snippet): https://developer.android.com/develop/ui/compose/glance/metrics
- NavigationEvent releases (localized copies): https://developer.android.com/jetpack/androidx/releases/navigationevent
- Remote Compose foundation releases: https://developer.android.com/jetpack/androidx/releases/compose-remote-foundation
- Credentials releases (localized copies): https://developer.android.com/jetpack/androidx/releases/credentials
- `IDENTITY_CHECK` authenticator: https://developer.android.com/reference/androidx/biometric/BiometricManager.Authenticators
- APK Signature Scheme v3.2: https://source.android.com/docs/security/features/apksigning/v3-2 ; https://blog.google/security/security-for-the-quantum-era-implementing-post-quantum-cryptography-in-android/
- Play target API (search snippets): https://developer.android.com/google/play/requirements/target-sdk
- Network security config CT element: https://developer.android.com/privacy-and-security/security-config ; https://www.securityweek.com/android-17-beta-strengthens-secure-by-default-design-for-privacy-and-app-security/
- Phantom processes: https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md
- AppFunctions + Gemini status: https://9to5google.com/2026/02/25/android-appfunctions-gemini/
- Developer verification store list: https://foxdata.com/en/blogs/google-confirms-android-developer-verification-mandate-september-30-enforcement-hits-4-countries/
- `startActivityAndCollapse(Intent)` throws at targetSdk 34: https://developer.android.com/about/versions/14/behavior-changes-14 (localized copy via search), AOSP https://android.googlesource.com/platform/frameworks/base/+/3576834649d7%5E%21/
- Compose `HapticFeedbackType`: https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType
- Loopback and local network (forum): https://discuss.grapheneos.org/d/33471-android-17-access-local-network
