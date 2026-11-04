# :android:notifications

Unit NOTIFY (2026-11-01). The notification half of `:android:background` (plan 11 §2.1), carved out so it can land
before slice 4: frozen channel ids, alerts and their buttons, the shade-action body, the status row with Live Updates,
and the reply-watch foreground-service body. Report: `docs/native/units/NOTIFY.md`.

Native-authored ports, not twins: the logic is ported from the Flutter app's `BackgroundConnectionService`,
`StatusNotificationModel`, `StatusNotificationRenderer`, `CodingActionReceiver`, `NativeNotificationLocale` and
`lib/background/notification_text.dart` at `2c8b8b00`, with the plan 11 §10 changes (no RemoteInput reply or option
buttons, no trampolines, unconditional Compat Live Update calls, BigTextStyle on promoted rows).

| File | Holds |
|---|---|
| `Frozen.kt` | notification ids, alert keys, group keys, intent actions/extras/URIs, OS component FQCNs (all frozen) |
| `Channels.kt` | `NotifyChannel` (ids and importances verbatim), `ChannelRegistry.ensure/relocalize/isOff` |
| `NotifyWords.kt` | copy in the stored app language (seven languages) |
| `NotificationText.kt` | masking and the shade's short words (`redact`, `commandHead`, `permissionAsk`, `firstReplyLine`, …) |
| `Alerts.kt` | `CodingAlert`, `AlertButtons`, `AlertCopy`, `AlertPolicy` (moves to `:engine:attention` with S4-ATTNE) |
| `AlertShade.kt` | `AndroidAlertShade`: posts, groups, summaries, open-only reposts, dismissals |
| `ShadeActions.kt` | `ShadeTap` (frozen extras only), `ShadeAnswerer` / `ReplyStopper` / `DeviceLock` seams |
| `NotificationActionBody.kt` | the body behind `CodingActionReceiver` and `StatusActionReceiver` |
| `StatusRow.kt`, `StatusRenderer.kt` | `StatusContent`, `StatusThrottle`, `LiveUpdatePolicy`, the 4747 renderer |
| `ReplyWatch.kt` | `ReplyWatchPolicy` and `ReplyWatchServiceBody` (S11-4) |
| `StatusModel.kt` | `StatusInput` → `StatusContent` (port of `notification_status.dart`), `StatusWords` / `AndroidStatusWords` |
| `StatusController.kt` | chat state → `StatusInput` (busy set, runs, plans, pending requests, pauses; B-ANDR-041) |
| `AlertMoments.kt` | which alerts a profile's sessions get and when they go (B-ANDR-011, -039, -040) |
| `ShadeReplyGuard.kt` | `QuestionShape` (shade options, typed-reply rule), `GuardedShadeAnswerer`, `RunningOnlyStopper` |
| `ReplyExcerpt.kt` | the finished alert's line from assistant, non-synthetic text (B-ANDR-048) |

OS-visible classes (`CodingActionReceiver`, `StatusActionReceiver`, `BackgroundConnectionService`) are thin shells in
`:androidApp` at their frozen FQCNs (plan 11 §2.2); this module only names them. The seams (`ShadeAnswerer`,
`ReplyStopper`, `ReplyRunSource`) are bound by the engines in the app graph.

Strings: `res/values*/strings_notify.xml`, names `notify_*`. en and ar are the Flutter app's `native_*` strings,
verbatim; es, ja, pt, ru and zh are new. The status row's words are `strings_notify_status.xml` (`notify_status_*`;
en and ar are the Flutter `notifStatus*` arb strings, verbatim). They move into `:ui:words` `strings_os` when its owner takes them.

```bash
./gradlew :android:notifications:testDebugUnitTest   # JVM + Robolectric (SDK 35, and 36 for Live Updates)
./gradlew :android:notifications:lintDebug
```
