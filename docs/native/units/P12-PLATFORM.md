# P12-PLATFORM — Android platform pieces live in the native app

Branch `claude/native-parity-p12-platform-7d25lm` (brief name `native/p12-platform-2026-11-03`), base
`native/m1-2026-11-03` at `8a4eee69`. Date 2026-11-02/03.

**Finish line:** a person on the preview app gets coding alerts in the shade (and can Allow once / Reject from it),
finished-reply alerts, live home-screen widgets, the two Quick Settings tiles, the launcher shortcuts at the Flutter ids,
share-into-app and the in-app update notice with a verified install.
**Non-goal:** the background live-mode foreground service (`BackgroundConnectionService`), the status row (4747) and
the widget overlay / approve-from-widget runners. Those are the S4-BG / S4-ATTNE bodies.

## Read this first: what was and was not verified

**The Android build did not run.** The session's network policy refuses `dl.google.com` (HTTP 403 from the egress
proxy). That host serves both the Android SDK and Google's Maven repository, including AGP, so **no Gradle task could
run**: no `assemblePreviewDebug`, no module tests, no Konsist, no Metro graph validation.

What was actually checked, using a standalone Kotlin 2.4.21 compiler (from GitHub) and jars from Maven Central:

| Check | Result |
|---|---|
| `:core:common` (common + desktop) compiled with kotlinc | compiles |
| `:engine:updates` main, with `-Xexplicit-api=strict` | compiles, no warnings |
| `:engine:updates` tests (`AppUpdatesTest`, `AppReleaseTest`) on JUnit 4 | **21/21 pass** |
| `:feature:updates` pure mapping (`UpdatesUi.kt`, `UpdateNoticeUi.kt`) + `UpdatesUiTest` | **10/10 pass** |
| `AndroidUpdates.kt` + `AppUpdateStatusReceiver.kt` against Robolectric `android-all` API 34 | compiles |
| `:android:tiles` `LaunchActions`, `SharedTextIntake`, `AttentionTileCache`, `PauseTileState`, `TilesComponents` against `android-all` | compiles |

**Not compiled at all:** anything that needs androidx, Compose or the Metro compiler plugin. That means the tile
services, `PinnedShortcuts`, `PlatformWiring.kt`, the receivers, the `MainActivity` / `OcApplication` edits, the
`:feature:updates` screen, ViewModel and Compose tests, the Robolectric tests in `:android:tiles`, and the
`:feature:chats` edit. Signatures were checked by reading the sources, but **the integrator must run the gates below
before merging**.

## Journeys implemented (implemented ≠ verified on a device)

| Journey | How it works now | Reach it |
|---|---|---|
| In-app update notice | `AppUpdates` checks GitHub `releases/latest` at start and every resume, at most once per 6 h (30 min after a failure). It offers a release only if its tag's versionCode is above the installed one. The download is the file for this phone's ABI, with Range resume, then SHA256SUMS, package, signer and versionCode checks, then PackageInstaller (Android asks the person). A mismatching file is deleted, never installed. | **Update** appears in the Conversations top bar while a release waits (the Flutter gear dot). It opens the Updates page, which has Update / Cancel / Install / Try again / Open on GitHub, the checks switch, Check now, What's new and build Details. |
| Permission / question alerts | `PlatformHub` watches `RequestsEngine.pending` of every live connection. While the app is not on screen it posts one alert per conversation through `AlertPolicy` → `AndroidAlertShade` (frozen channel ids and alert ids). An alert whose request is answered elsewhere is dismissed. | The notification shade. |
| Allow once / Reject from the shade | `CodingActionReceiver` (frozen FQCN) → `NotificationActionBody`. It answers with `RequestsEngine.answerNow` on the **exact** request id of the live connection. A question's Reject is `Decline`. With no live connection the alert is reposted with Open only. | Alert buttons. |
| Finished-reply alert | Fires when a feed row goes Running → not running while the app is off screen. Its line is `NotificationText.firstReplyLine` of the row preview. Tapping it opens that chat and clears the alert. | The shade. Tap opens the chat. |
| Notification permission | Asked **once** on Android 13+, on the first resume after a server is saved. | Automatic. |
| Home-screen widgets (both families) | `:android:widgets` is in the app. `WidgetFeed.persistence` is bound at start, and `PlatformHub` publishes a version-2 snapshot from the real conversation feed and pending requests: up to 12 sessions with title, project, server, busy, needsYou and requestID, plus a server list and running count. Unchanged bytes are neither written nor redrawn (the surface's rule). | Add the widget from the launcher. |
| Quick Settings tiles | `AttentionTileService` and `PauseTileService` at the Flutter FQCNs. The attention tile shows the live count, cached by `AttentionTileCache` (stale after 24 h), and opens the app. The pause tile shows "Off in Settings" (unavailable) because there is no background mode yet. | Edit Quick Settings, add "OpenCode" / "Background mode". |
| Launcher shortcuts | Static `connect` and `new_task` (ids, extras and labels as in Flutter; preview flavour targets the `.native` package). `MainActivity` routes Connect → Servers, New conversation → New conversation, any other whitelisted action → Conversations. A pinned-session shortcut tap opens that chat. | Long-press the app icon. |
| Share into the app | `ACTION_SEND text/plain` opens New conversation on the active server. The shared words become the draft of the conversation opened next (`ConversationDrafts`, only if that draft is empty). Each share is consumed once. | Share text from any app → OpenCode Native. |
| Profile deletion | When a profile disappears, its tile count, pinned shortcuts and widget rows are cleared (B-STATE-063). | — |

## Catalog rows

Covered by tests written in this unit (row-named):
- **B-MIGR-012**: engine (20 of the 21 engine tests), feature mapping and VM, Compose screen tests, and the chats bar test.
- **B-ANDR-094**: engine, plus the feature module's tests.
- **B-ANDR-010** (tile cache), **B-ANDR-027 / -028 / -029 / -031** (launch extras, whitelist, manifest), **B-ANDR-030 /
  -053 / -054** (pinned shortcuts), and **B-ANDR-074 / -075** (share intake): the `:android:tiles` Robolectric tests,
  written but not run.

Behaviour wired but with no new test: B-ANDR-011 (alerts raised from requests), B-ANDR-040 (opening the chat clears
its alert), B-ANDR-048 (first reply line), B-ANDR-022 (widget written and redrawn), B-ANDR-117 (unchanged payload not
rewritten, by the existing surface), B-STATE-063 (partial sweep only).

Still missing:
- B-ANDR-009/012–021, -032/-033, -041 to -047: background live mode, pause and the status row. No foreground service
  exists yet.
- B-ANDR-023/024: the once-only "turn on finished-reply notifications" offer. The permission is asked plainly once
  instead.
- B-ANDR-038/039: option buttons and quiet hours. Quiet hours are not read from settings (`quietHours = false`).
- B-ANDR-103 to -109, -118, -119, -121: approve or stop from a widget, the widget overlay and status runners. The
  attention engine's runners are not bound, so widget action buttons do nothing yet.
- B-ANDR-055 (desktop): desktop does not depend on these modules.
- B-MIGR-013 (republish after the update): tiles and widgets redraw on the next feed change. There is no explicit
  `MY_PACKAGE_REPLACED` hook.

## Files

- New modules: `native/engine/updates`, `native/feature/updates`, and `native/android/tiles` (tiles, shortcuts, share
  intake, and the updater's Android half with `AppUpdateStatusReceiver`).
- New route: `native/ui/routes/.../UpdatesRoute.kt`.
- App wiring:
  - `androidApp/build.gradle.kts`: 5 module lines.
  - `graph/PlatformWiring.kt`: `PlatformBindings`, `AppUpdateDoor`, `ReleasePageLinks`, `PlatformHub`.
  - `PlatformReceivers.kt`: `CodingActionReceiver` and `StatusActionReceiver` shells.
  - `AndroidManifest.xml`: SEND filter, shortcuts meta-data, the 2 receivers.
  - `res/xml/shortcuts.xml` and `src/preview/res/xml/shortcuts.xml`.
  - `MainActivity`: `onPostCreate` / `onNewIntent` / `onResume`, one call each.
  - `OcApplication`: one line, `platform.start()`.
- `:feature:chats` (smallest edit): an `UpdateDoor` port with a closed default (`NoUpdateDoor`), `ChatsUi.updateReady`,
  an `openUpdate()` action with a default body, and an **Update** bar word. There is one new test.

## Deviations

1. **Receivers outside the one wiring file.** The OS needs classes at the frozen FQCNs in package
   `io.github.eslamasabry.opencode_mobile`, so they live in `PlatformReceivers.kt`. `AppUpdateStatusReceiver` lives in
   `:android:tiles`.
2. **The updater's Android code is in `:android:tiles`.** That module was the only Android module in the write set.
   It could move to an `:android:updates` module later.
3. **Alerts post without a foreground service.** `replyWatchActive` is "a connection is live in this process". When
   Android freezes or kills the process, alerts stop. That is the S4-BG gap.
4. **Gear badge.** The native gear opens Servers (`PreviewSettingsDoor`), so the badge is a visible **Update** bar word.
   `UpdateOfferSheet` (Update now / Open Settings) exists in `:feature:updates` for the Settings owner to put on the
   gear.
5. **No release is offered to the preview package yet.** Its versionCode is 10000+ and the GitHub releases are the
   Flutter app's (57/58), so the notice correctly stays silent until a native release is published. If one were
   offered, its package check would refuse a Flutter APK with plain words.
6. **The Settings › Updates page in `:feature:settings`** (its `UpdateSettings` port) is not bound here, because that
   module is not in the app. Its owner can adapt `AppUpdates.state` to `UpdateFacts` in the same way.

## What the integrator must do

1. **`MODULE_OWNERS`** (coordinator-only): add rows for `:engine:updates`, `:feature:updates` and `:android:tiles`.
   Without them `ModuleOwnersTest` fails.
2. **Run with a working SDK**, in this order. Expect compile fixes in the never-compiled files listed above.
   ```bash
   cd native
   ./gradlew :engine:updates:desktopTest :feature:updates:desktopTest :feature:chats:desktopTest
   ./gradlew :android:tiles:testDebugUnitTest :android:widgets:testDebugUnitTest :android:notifications:testDebugUnitTest
   ./gradlew :quality:test :androidApp:assemblePreviewDebug
   ```
3. **Metro defaults.** `ChatsViewModel` gained a last parameter `updates: UpdateDoor = NoUpdateDoor()`. The binding
   exists (`AppUpdateDoor` replaces `NoUpdateDoor`), so the default is used only by tests.
4. **On a device:**
   - Add both tiles and both widget families.
   - Long-press the icon for the two shortcuts.
   - Share text into the app.
   - With the app in the background, trigger a permission ask and use Allow once from the shade.
   - Check a finished-reply alert.
