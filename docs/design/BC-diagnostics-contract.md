# BC diagnostics — FD1, FD3, FD2

Finish line: the device diagnostics gateway returns safe exit history, durable
background pause/resume state, and a previewable local crash report with explicit
system sharing. Claude owns the UI and localization. Non-goals: UI changes,
automatic capture consent, uploads, device/emulator jobs before EMULATOR GO.

## Entry point and ownership

Import `lib/domain/app_diagnostics_gateway.dart` for types and
`lib/diagnostics/device_diagnostics_gateway.dart` for
`deviceDiagnosticsGatewayProvider`. The provider uses the existing main
connection's background controller and lifecycle bridge. It is independent of
the selected remote server and its protocol/capabilities. The gateway is a
`Listenable`; listen for pause changes. Refresh on foreground and when opening
diagnostics. UI never calls native channels or reads diagnostic files.

Frozen methods on `AppDiagnosticsGateway`:

| Method | Result |
| --- | --- |
| `exitHistory({int limit = 10})` | `Future<AppExitHistory>` |
| `backgroundPause` | `BackgroundPauseState` getter |
| `refreshBackgroundPause()` | `Future<BackgroundPauseState>` |
| `resumeBackground()` | `Future<BackgroundResumeResult>` |
| `shareSupported` | `bool` getter; hide Share when false |
| `previewCrashReport()` | `Future<CrashReportResult>` |
| `shareCrashReport(CrashReportPreview preview)` | `Future<CrashShareResult>` |

Operations return typed errors, never raw exception text. `DiagnosticsError`:
`unavailable`, `invalidLimit`, `storageFailed`, `captureDisabled`, `noCapture`,
`stalePreview`, `shareUnavailable`, `resumeFailed`, `busy`. A nullable error
means success. Unsupported is explicit, distinct from an empty history.

## FD1: exit history

`AppExitHistory`: `supported`, immutable `entries`, nullable `error`.
Each `AppExitEntry` has numeric `reason`, `importance`, UTC `at`,
`AppExitCategory category` (`normal`, `update`, `forceStop`, `lowMemory`,
`crash`, `killed`) and bounded `description`. The category is a stable key,
localized by UI; details may display the numeric Android reason/importance.
Entries are newest first, at most requested limit. Valid limits are 1–50;
invalid limits return `invalidLimit` without a native request. API <30 or absent
channel returns unsupported. A native read failure returns `unavailable`.

Reuse `AppLifecycleBridge` / `AppLifecycle` and `classifyAppExit`, including its
update/subreason handling. History does not consume the startup recovery notice
or advance its seen-exit timestamp. Only the app's main process is included.
OS descriptions and trace streams may contain secrets: structural redaction
replaces descriptions with fixed safe summaries; arbitrary OS text never crosses
the channel or enters logs. Categories are evidence of process death, not proof
of a battery policy or of an individual service's cause.

## FD3: background pause and resume

`BackgroundPauseState`: `supported`, `active`, `paused`, `reason`, nullable UTC
`at`, `canResume`. Reasons: `none`, `timeLimit`, `batteryRestricted`,
`userStopped`, `interrupted`. Native service state plus a private durable receipt
is authoritative. Timeout records `timeLimit` before stopping; a confirmed
background restriction records `batteryRestricted` when inactive after a prior
run. A task removal is only a hint: never show paused while the service remains
active. A next-launch user-requested exit following a running receipt can be
`userStopped`; it does not distinguish Recents swipe, Settings or OEM behavior.
An unexplained inactive service after a running receipt is `interrupted`.
Ordinary opt-out clears the receipt. The explicit notification Pause action
retains its existing semantics and must not be called a system timeout.
For time limits, `at` is the callback time; for user stops, it is the matching
exit time. For restrictions and unexplained interruptions, it is the observation
time, not an invented exact service-stop time.

The native receipt is app-global private storage (`oc_background_pause`), not
profile data. It records only phase, fixed reason and time. Detection starts
with a confirmed service start in this version; a legacy enabled preference
alone cannot prove that an older service was running. Existing preferences and
crash file formats are retained; report freshness epochs are process-local.

The pause survives cold start and does not auto-resume. Refresh before restoring
an old enabled preference so OS interruption is not silently undone.
`resumeBackground()` is only called from a visible user's Resume tap. It uses
the existing notification permission/service start path; no timeout bypass,
background retry loop, battery exemption or automatic restart. Return
`BackgroundResumeResult(state, error)`: success only once native status reports
active; pending or rejected start keeps the pause and returns `resumeFailed`
(or `busy`). Clear the pause only on confirmed start. Android retains its runtime
limits; no remaining-budget estimate is manufactured.

## FD2: captured crash report preview and share

`CrashReportResult`: nullable `preview`, nullable `error`.
`CrashReportPreview`: immutable `text`, `byteCount`, `recordCount`.
`CrashShareResult`: `opened`, nullable `error`; opened means chooser opened,
not delivery to a recipient. No report is built when capture is off, storage
failed/unavailable, or no captured evidence exists. BD7 stays OFF by default.

Report inputs are only validated BD7 records: fixed source/category and time.
No exception message, stack, OS trace, log tail, conversation, profile name,
path, host, configuration or credential input is accepted. Format is fixed
plain text, at most 16 KiB UTF-8, at most20 entries. It is kept in memory and
previewed exactly as shared. Creating it causes no consent change, disk write or
share action. After explicit Share, use existing `oc/share.shareText` via
`ShareOut.text`; no network upload. A foreign/modified preview, changed evidence,
disable/clear/re-enable or unavailable store makes the preview stale. Return
`stalePreview`, rebuild and require another preview/Share tap. No silently
updated report is sent. Share errors expose fixed guidance. UI can remove
sharing when unsupported and must not claim the report was sent.

Consent/evidence is checked immediately before invoking the chooser. Turning
capture off after the share invocation cannot retract text already handed to
Android or another app. Opening the chooser is the only reported success.

## Plain copy keys for Claude to localize

| Proposed key | English copy |
| --- | --- |
| `diagnosticsExitHistoryTitle` | Recent app exits |
| `diagnosticsExitHistoryEmpty` | No recent app exits were recorded. |
| `diagnosticsUnavailable` | These details are unavailable. Reopen the app and try again. |
| `diagnosticsBusy` | One moment… |
| `diagnosticsExitNormal` | App closed |
| `diagnosticsExitUpdate` | App updated |
| `diagnosticsExitForceStop` | App stopped |
| `diagnosticsExitLowMemory` | Phone needed memory |
| `diagnosticsExitCrash` | App stopped unexpectedly |
| `diagnosticsExitKilled` | Android ended the app |
| `diagnosticsBatteryPaused` | Paused to save battery |
| `diagnosticsBatteryResume` | Resume |
| `diagnosticsBatteryTimeLimit` | Android paused the background connection after its daily limit. |
| `diagnosticsBatteryRestricted` | Android is restricting this app's background work. |
| `diagnosticsBatteryUserStopped` | The app was stopped while its background connection was running. |
| `diagnosticsBatteryInterrupted` | The background connection stopped. Its reason is unavailable. |
| `diagnosticsBatteryResumeFailed` | Couldn’t resume the background connection. Keep the app open and try again. |
| `diagnosticsReportTitle` | Preview crash report |
| `diagnosticsReportPrivacy` | This report includes error categories and times. It contains no messages or conversations. Nothing is uploaded automatically. |
| `diagnosticsReportCaptureOff` | Saved crash details are off. Turn them on to save future app errors. |
| `diagnosticsReportEmpty` | No saved crash details to share. |
| `diagnosticsReportStale` | Saved details changed. Preview the report again before sharing. |
| `diagnosticsReportShare` | Share report |
| `diagnosticsReportShareFailed` | Couldn’t open sharing. Try again. |

No ARB edits in this backend lane. Show the pause notification row only for
`paused`; use the primary requested label with reason-specific supporting copy.
Resume disabled while busy; announce result accessibly. Report preview is
selectable, scrollable text with an explicit Share button, never an automatic
share during opening. Existing BD7 consent UI is separate.
The UI tracks its own in-flight action Future for button/spinner state; duplicate
actions also return `busy` without another start/share request.

## Android evidence and verification boundary

Android15 dataSync services receive `onTimeout` after six background hours per
24h; foreground user interaction resets the budget. The app must stop promptly.
See [Android timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).
Exit descriptions are unstable human-readable text, not a safe schema:
[ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo).
Background restriction is a current policy fact, not proof of a historical
cause: [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager).

Lead runs focused Dart/channel and host Kotlin tests serially through
machine_lock, regression removals that fail, analyzer and Kotlin gate. No
emulator run until coordinator EMULATOR GO. No claim of real Android timeout,
OEM swipe or physical power-loss qualification from host fixtures.
