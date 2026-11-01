# Unit NOTIFY: notification channels, shade actions, reply watch and Live Updates

Date: 2026-11-01. Branch: `native/notify-2026-11-01`, created from `claude/native-notify-2026-11-01-xvcolm` at
`2c8b8b00`. Write set: `native/android/notifications/**` (new module) and this report.
Plan sections: 11 §9.3, §10 (channels, ids, alerts, Live Updates, actions, language, permission), §2.2–§2.5;
16 §1.8 / WP-16.8 / CR16-7 (no secrets in notification text, unlock rule); 05 §7.9 (channel ids frozen).

**Finish line (brief):** notification channels with the same ids as the Flutter app, all 7 languages, permission and
question actions from the shade, the reply-finished notification via a short foreground service, Live Updates where
available; tests.

**Shipping state:** implemented and verified on this container (compile, Android lint, 70 JVM + Robolectric tests).
Committed and pushed to the branch. **Not wired** into `:androidApp` (receiver/service shells, graph bindings) and not
in `MODULE_OWNERS` (both outside the write set; see §6). No emulator run, no PR, no CI, no release.

**Non-goals (kept):** the status *model* (`StatusModel`, `:engine:attention`, S4-ATTNE), live mode, the sign-in claim,
tiles, widgets, scheduled-run notifications, the `ConnectionRegistry.oneShot` path itself (CR11-6), the shells in
`:androidApp`.

---

## 1. What was built

Module `:android:notifications` (`oc.android.library`, namespace `io.github.eslamasabry.oc.android.notifications`).
Depends only on `:core:common` (the flat `Failure`/`Outcome` contract of INTEGRATE-W1; no `Failure.Net.*`),
androidx.core 1.19.1 and coroutines. Every file below is native-authored; the logic is ported from the Flutter app at
`2c8b8b00` (see the module README).

| File | What it does |
|---|---|
| `Frozen.kt` | Notification ids (4747, 4748, 4098/4099, 4100, 5998/5999, `0x701CE`, `0x7E4A1`, alerts `6000 + (key.hashCode() & 0x0fffffff)` verbatim), request codes `id*4+index+1`, alert keys, group keys, the coding-alert/status actions, extras and data URIs copied verbatim, and the frozen FQCNs of `MainActivity`, `CodingActionReceiver`, `StatusActionReceiver`, `BackgroundConnectionService`, `BuiltinServerService` (addressed by `ComponentName`, since the shells live in `:androidApp`). |
| `Channels.kt` | `NotifyChannel`: the 10 ids of plan 11 §10.1 with the Flutter importances. `ChannelRegistry.ensure(ships)` creates only missing ids of shipped features (never `opencode_team_progress` before D6, never `bb9_qa_producer` outside QA), never recreates one; `relocalize()` renames name/description through the existing channel object (keeps importance and every user choice); `isOff()`. |
| `NotifyWords.kt` | Resolves copy through a context configured with the stored app language (`oc.appLocale`, read per call); unsupported/missing → system language. |
| `res/values{,-ar,-es,-ja,-pt,-ru,-zh}/strings_notify.xml` | 55 strings + 4 plurals per language. en/ar are the Flutter `native_*` strings verbatim (renamed `notify_*`); es, ja, pt, ru, zh are new translations; 3 new strings (`answer_in_app`, `reply_watch_title`, `status_public_working`). Plural categories per CLDR (ar six, ru four, es/pt one/many/other, ja/zh other). |
| `NotificationText.kt` | Port of `notification_text.dart`: `redact` (same patterns, same order), `firstReplyLine`, `commandHead`, `permissionAsk`, `stoppedReason` (same matching as `MessageErrorKind.fromName`), `subtitle`. |
| `Alerts.kt` | `CodingAlert` (validity, safe agent name, word-free `toString`), `AlertButtons` (Allow once with unlock + Reject only when the command/file is readable and the request id exact; questions Open only; never Always allow), `AlertCopy` (port), `AlertPolicy` (when posted: live mode or reply watch, backgrounded, permission, server rules, quiet hours → quiet channel or skip, Wi-Fi-only quota, sub-agent finished skipped; test ignores the live gate but not the permission). |
| `AlertShade.kt` | `AndroidAlertShade`: posts with `NotificationCompat` (private visibility, fixed public version, groups with explicit keys, `setTimeoutAfter(1 h)` for finished, `setOnlyAlertOnce` except quota, summaries 5998/5999 with ≥ 2 children, ≤ 5 lines), every `PendingIntent` immutable with per-decision data URIs, `showOpenOnly` (plain "Open OpenCode to answer", no buttons, content intent to the exact request), `dismiss`, `Blocked(Permission | ChannelOff)`. |
| `ShadeActions.kt` | `ShadeTap.read(intent)` (frozen extras only, incl. legacy RemoteInput reply and `option<N>` from Flutter-era alerts), `ShadeAnswer` bound to `ProfileId`/`SessionId`/`RequestId`; seams `ShadeAnswerer`, `ReplyStopper`, `DeviceLock`. |
| `NotificationActionBody.kt` | Body for both receivers: exact request id (empty → repost Open only), API 26–30 locked-phone check for unlock decisions (Reject exempt), empty legacy reply refused before any call, one in-flight answer per request, `NotFound`/`AlreadyAnswered` → quiet dismiss, `StaleScope`/failure/15 s timeout → repost Open only; Stop the reply stops exactly the named chat; the row's delete intent ends reply watch for that run. **No `startActivity` anywhere.** |
| `StatusRow.kt` | `StatusKind`/`StatusAction` (wire names unchanged), `StatusContent.of` (validation and caps), `StatusThrottle` (2 s, changed-only, trailing newest), `LiveUpdatePolicy` (working, several working, needs-you in a running turn; never idle/paused/setup/waiting/reconnecting; never a dismissed run). |
| `StatusRenderer.kt` | Row 4747 on `opencode_live_connection`: Compat `setRequestPromotedOngoing(true)` + `setShortCriticalText(plural chip)` called unconditionally; promoted rows use `BigTextStyle` (lines joined by newlines), others `InboxStyle`; progress only with a real fraction; count-only public version; Open/Start/New chat activity intents (request codes 10/11/12, content 0), Stop the reply broadcast (13, `opencode://status/stop_reply`), Stop OpenCode service intent (1, action `stop`), delete intent (14, new). |
| `ReplyWatch.kt` | `ReplyWatchPolicy.onStop` (start / join server row / skip with reason) and `end` (10 s no progress, 2 h cap, app visible, server left a joined row); `ReplyWatchServiceBody`: promise first (`ServiceCompat.startForeground(4747, …, DATA_SYNC)`, a refused start is a stop), watches `ReplyRunSource`, posts finished/needs-you alerts through `AlertPolicy` + the shade, throttled row updates, `onTimeout`, `onRowDismissed`, `START_NOT_STICKY`. |
| `AndroidManifest.xml` | `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` (B-ANDR-034), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`. No components. |

## 2. Catalog rows covered (tests are named after the row)

| Row | Test class › proof |
|---|---|
| B-SEC-106 | `NotificationTextTest` (ordinary commands whole; bearer, keys, passwords, flags, URL credentials keeping host, token shapes, long runs; one ellipsised line), `AlertRulesTest` (no words in `toString`) |
| B-SEC-105 | `NotificationTextTest` (program + sub-command, right verb, no target when nothing to show, subtitle), `AlertRulesTest`, `AlertShadeTest` (shade only, secret masked, fixed public words) |
| B-SEC-104 | `AlertShadeTest` (stopped chat = reason sentence) |
| B-SEC-184 | `AlertRulesTest`, `AlertShadeTest` (Allow once `isAuthenticationRequired`, Reject not), `NotificationActionBodyTest` (API 30 locked → nothing answered, repost at that request; Reject answers; API 31 left to Android) |
| B-SEC-183, B-AGENT-031 | `NotificationActionBodyTest` (exact id; empty id never answers; malformed / Open / Always taps do nothing) |
| B-AGENT-032 | `NotificationActionBodyTest` (empty reply refused before any call; failed reply keeps request and reposts) |
| B-PROTO-046 | `NotificationActionBodyTest` (stale id refreshes instead of resolving) |
| B-CHAT-074, B-CHAT-076 | `NotificationActionBodyTest` (two taps → one reply; in-app winner and server-timed-out → quiet dismiss) |
| B-ANDR-011, -013, -039, -059, -097 | `AlertRulesTest` (posting rules), `ReplyWatchServiceTest` (quiet hours: no finished alert) |
| B-ANDR-038 (as amended by CR11-16) | `AlertRulesTest`, `AlertShadeTest` (Open only, options as one line, no RemoteInput) |
| B-ANDR-040 | `AlertRulesTest`, `AlertShadeTest` (finished names its chat, 1 h timeout, summary, dismissal), `NotificationActionBodyTest` (Stop the reply exact chat, others ignored) |
| B-ANDR-047 | `StatusRowRulesTest` (validation, equal inputs equal), `AlertShadeTest` (Arabic copy, lock screen), `PromotableStatusTest` (count-only public version) |
| B-ANDR-048 | `NotificationTextTest` (first reply line, list intro joined, stopped tokens) |
| B-ANDR-032 | `StatusRowRulesTest` (first at once, unchanged skipped, burst → one trailing post, return-to-shown sends nothing) |
| B-ANDR-034 | `PromotableStatusTest` (merged manifest requests `POST_PROMOTED_NOTIFICATIONS`) |
| B-ANDR-042 | `PromotableStatusTest` (bar only with a real fraction, never indeterminate) |
| B-L10N-002, B-L10N-013 | `ChannelsTest` (relocalize in place keeps importance; every channel/alert word present and translated in all 7 languages; fallback) |
| plan rows without a catalog id | `ChannelsTest` (05 §7.9 ids/importances verbatim, H3 ensure), `AlertRulesTest` (11 §10.2 ids), `PromotableStatusTest` (11 §10.5), `ReplyWatchPolicyTest` + `ReplyWatchServiceTest` (S11-4, S11-9, §9.2 promise first) |

Test-first honesty: the tests were written from the rows alongside the code, not strictly before it. The first run of
the full set was red (26 of 70: resources not included in unit tests, one wrong expectation of the Dart masking order,
the SDK 36 runtime needing a JDK export); those were fixed, then one real finding (§4 D5).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`;
AGP added `build-tools;36.0.0` itself). Container-only, not committed: `~/.gradle/init.d/central-mirror.gradle.kts`
adds Google's Maven Central mirror in `beforeSettings` (Central answers 429 through the proxy, including for the
foojay settings plugin) and sets `robolectric.dependency.repo.url`; `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :android:platform:testDebugUnitTest` (toolchain check) | `:android:platform` does not compile on this tree (`NetworkStatus`, `engine.entry` unresolved) — not this unit's; why this module depends only on `:core:common` |
| 2 | `./gradlew :android:notifications:compileDebugKotlin` | BUILD SUCCESSFUL |
| 3 | `./gradlew :android:notifications:testDebugUnitTest` (first) | 70 tests, 26 failed → fixed (above) |
| 4 | same, after fixes | 1 failed (D5) → fixed |
| 5 | `./gradlew :android:notifications:lintDebug` | passed; 7 warnings (5 `UseKtx` → fixed; `InlinedApi` on `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, harmless: `ServiceCompat` ignores the type below 29; `AppBundleLocaleChanges`, n/a to sideload/Shorebird) |
| 6 | **Final:** `./gradlew :android:notifications:testDebugUnitTest :android:notifications:lintDebug` on the committed tree | **BUILD SUCCESSFUL; 70 tests, 0 failures**: AlertRulesTest 14, AlertShadeTest 8, ChannelsTest 6, NotificationActionBodyTest 13, NotificationTextTest 9, PromotableStatusTest 5 (SDK 36), ReplyWatchPolicyTest 5, ReplyWatchServiceTest 7, StatusRowRulesTest 3 |

Not run: `:quality:test` (needs the whole tree to compile, and `ModuleOwnersTest` will flag this module until its
`MODULE_OWNERS` row exists), emulator proofs (U11-10 on an API 36 image, E2E-07/08), Konsist.

## 4. Decisions and deviations

| # | Decision | Why |
|---|---|---|
| D1 | A new module `:android:notifications` instead of `:android:background` (plan 11 §2.1, S4-BG). | The brief's write set. It is the notification half of `:android:background`; S4-BG can fold it in or depend on it. |
| D2 | Strings live in this module as `notify_*` (7 languages), not in `:ui:words` `strings_os`. | `:ui:words` is single-owner and its `strings_os` has no es/ja/pt/ru/zh notification copy. Distinct names avoid resource clashes with `:android:linux`'s `native_*` copies when both join `:androidApp`. Words-owner request in §6. |
| D3 | `AlertPolicy` (the "when posted" table) lives here. | `:engine:attention` does not exist; it is pure and moves as `NotificationPolicy` with S4-ATTNE. |
| D4 | Answering goes through the `ShadeAnswerer` seam with the flat contract: `NotFound`/`Refused(AlreadyAnswered)` = gone, `Refused(StaleScope)` = stale, anything else = repost. | `ConnectionRegistry.oneShot` (CR11-6) and `:engine:requests` are not built; the seam fixes the contract for them. |
| D5 | `PromotableStatusTest` checks the Android 16 QPR1 (36.1) Live Update rules directly and only *calls* `Notification.hasPromotableCharacteristics()`. | Robolectric 4.17's SDK 36 runtime (`android-all-instrumented 16-robolectric-13921718`) still has the 36.0 pre-release rule: it returns false unless the row is **colorized**, which contradicts the shipped rule (not colorized). Plan 11 §10.5 asks for the platform method; the platform call is proven to exist (no `NoSuchMethodError`), the rule check is ours. Recheck when Robolectric ships 36.1. |
| D6 | Status row: a delete intent (`opencode://status/dismissed`, extra action `dismissed`, request code 14) to `StatusActionReceiver`. | S11-9 needs it; it is a new identity (no Flutter-era row holds it), not a frozen one. |
| D7 | `NotificationActionBody` reposts with Open only after any failure, timeout, stale or empty id, or a locked API 26–30 phone; it never starts an activity. | Plan 11 §10.6 and CR16-7. A `MigrationFollowUpWorker` path for a closed gate is the shell's (§2.2). |
| D8 | Legacy reply and `option<N>` taps are accepted (with the unlock check and the empty-reply refusal); no native alert offers them. | Plan 11 §10.4: Flutter-era alerts may still be in the shade after the update. |
| D9 | Question alerts accept no Allow from the shade; Allow on a question is ignored. | Only permissions have Allow once. |
| D10 | The small icon is a module vector `notify_ic_status`. | Plan 11 §2.3 puts code icons with the code; `ic_status` is not in the native tree yet. Replace when the design authority supplies it. |
| D11 | Test task gets `--add-opens java.base/java.io` and `--add-exports java.base/jdk.internal.access`. | Robolectric's SDK 36 runtime on JDK 21 (`ApplicationSharedMemory`). Module build file only. |
| D12 | No formatter run. | No ktfmt/detekt is configured in `native/` yet; lines up to ~150 columns remain in tests. |

## 5. Catalog requests

None. Everything used is already in the catalog (`androidx-core-ktx`, `kotlinx-coroutines-core`, `junit4`,
`robolectric`, `kotlinx-coroutines-test`).

## 6. Requests to other owners

- **Coordinator:** add `:android:notifications	F	NOTIFY (folds into S4-BG)	slice-4` (or the owner you choose) to
  `native/MODULE_OWNERS`, or `ModuleOwnersTest` fails once the tree compiles.
- **`:androidApp` (coordinator) / S4-BG:** shells at the frozen FQCNs — `CodingActionReceiver` and
  `StatusActionReceiver` forward to `NotificationActionBody.onCodingAction/onStatusAction` inside `goAsync` after the
  migration gate; `BackgroundConnectionService` forwards `onStartCommand`, both `onTimeout`s and `onDestroy` to
  `ReplyWatchServiceBody`, with `foregroundServiceType="dataSync|shortService"`; `MainActivity.onStop` calls
  `ReplyWatchPolicy.onStop`. Bind `NotifyWords` to the stored `oc.appLocale`, `DeviceLock` to `KeyguardManager` and
  `Build.VERSION.SDK_INT`, and call `ChannelRegistry.ensure()` at H3 and every process start.
- **Connection engine / S2-REQ:** implement `ShadeAnswerer` (live connection, else `oneShot`, `health()` first,
  once/reject only, exact request) and `ReplyStopper`; provide `ReplyRunSource` (progressing excludes runs waiting on a
  shown needs-you alert).
- **S4-ATTNE:** take `AlertPolicy` as `NotificationPolicy`; produce `StatusContent` (with `count`) and `CodingAlert`
  (words already masked with `NotificationText`).
- **`:ui:words` owner:** absorb `strings_notify.xml` (7 languages) into `strings_os` and have a native speaker review
  the five new languages; the Flutter app never had them.
- **Konsist (coordinator):** the planned rule banning `startActivity` in receiver bodies can include this module.

## 7. Open problems

1. Not wired: no shells, no graph bindings, so nothing posts in the app yet (§6).
2. Robolectric's SDK 36 `hasPromotableCharacteristics()` encodes the 36.0 rule (D5); emulator proof U11-10 on an API
   36.1 image and U11-2 (ZTE) remain.
3. `canPostPromotedNotifications() == false` shows nothing (parity); the D11-6 row is not built.
4. The `team_*` alert kinds (D6) and `opencode_team_progress` posting are not ported.
5. The five new translations are machine-authored by this unit and unreviewed.
6. Intermediate commits were not compiled one by one; the final tree was (§3 #6).
