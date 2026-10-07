# BD7 — saved crash details frontend contract

Backend is implemented; the coordinator owns consent UI, localization, rendered
accessibility checks and Android device qualification. Capture defaults OFF.

## Callable state

Import `lib/diagnostics/crash_diagnostics.dart`.

- `CrashDiagnosticsStartup.ready`: startup result; null means this store is
  unavailable. `main()` starts it after the opening frame; readiness resolves
  within 300 ms. A timed-out or late reply cannot enable Flutter capture during
  this run. Crash-store disk reads/import writes do not hold the UI isolate.
  App-diagnostics notifications occur after readiness resolves; the separate
  existing problem-report writer retains its own persistence behavior. Restart to retry.
- `CrashDiagnosticsStartup.current`: same controller after startup succeeds.
- `CrashDiagnosticsController.enabled`: whether this controller loaded/saved
  a valid explicit consent epoch. Defaults false; it is not a device-proof flag.
- `storageFailed`: the latest storage operation failed. Treat this as unavailable
  and do not confirm that an attempted setting change or deletion succeeded.
- `savedCount`: number of saved Flutter/category/ANR records, bounded to 20.
  The legacy native summary file is separate; imports become `native` records.
- The controller is a `ChangeNotifier`; listen to refresh these fields.
- `bool setEnabled(bool enabled)`: explicit consent choice; returns true only
  after the local change finishes. Enabling an already enabled healthy controller
  preserves evidence. Disabling remains an active cleanup even when already off.
- `bool clear()`: removes evidence and current App diagnostics, retaining consent
  if it was on. Advances the consent epoch so old OS-reported ANRs do not return.
- The existing `AppDiagnosticsController.clear()` also clears this crash store.
- `CrashDiagnosticsStartup.capture(diagnostics, error, stack, source)` retains
  fixed categories in memory while startup is pending/unavailable; no stack or
  exception text is recorded.
- `capture(Object error, StackTrace? stack, String source)` is for global hooks,
  not UI. Supported sources: `flutter`, `platform`, `widget`. No error value or
  stack text is read or persisted.

The app owns the controller for its lifetime. UI must not dispose it. UI must
not call the native channel, manage files or access credential/config responses.
No upload, external URL or telemetry request occurs.

## UI states and words

| State | Primary words | Action |
| --- | --- | --- |
| Opening | Checking saved crash details… | Await `ready` |
| Available, OFF | Save crash details on this device | Explicit toggle |
| Consent explanation | Save the kind of app error and when it happened. Error messages, conversations, credentials and stack traces are not saved. Nothing is sent automatically. | Turn on / Cancel |
| Available, ON | Saved crash details are on | Turn off and clear saved diagnostics / Clear saved diagnostics |
| Restart import | OpenCode stopped unexpectedly. Reopen the app to continue. | Reopen the conversation; offer App diagnostics |
| OS ANR import | Android reported that OpenCode stopped responding. | Reopen the app; offer App diagnostics |
| Unavailable / failed change | Couldn’t update saved crash details. Restart the app and try again. | Retry after restart; do not announce success |

These are proposed plain-word strings for coordinator localization, not literals
added to UI/ARB by this lane. Technical source/category/timestamp belongs under
Details. Avoid promising that a saved record diagnoses the cause or that every
Android exit produces a record.

Disable/clear also clears the current App diagnostics controller. Its existing
`ReportProblemCapture` listener clears the saved problem report, including timing
history. Consent copy must say **saved diagnostics**, not only one crash entry.
No profile, credential, conversation or server data is deleted.

## Persistence, recovery and limits

The store is private to the app. Consent is an atomically replaced timestamp
file; Flutter evidence is an atomically replaced JSON ring (20 entries, at most
8 KiB). At most one additional 8 KiB temporary file coexists during replacement.
Java native evidence is one bounded properties snapshot (read limit 24 KiB;
new class-only payload is substantially smaller), with one temporary file.
Both preserve the previous committed snapshot during replacement and flush
before rename. Corrupt/oversized or unsupported Flutter records are discarded.
Symlink targets are rejected. IO failures expose fixed state, not file paths.

The native handler installed by `OcApplication` consults the same consent file
before writes and reads. It records fixed Java exception categories, up to three
fixed cause categories, and timestamps; arbitrary messages, class names and
stack text are discarded. Its previous fatal handler is still invoked, receiving
a fixed sanitized throwable with no stack or cause so its logger does not get
the original exception values.

`oc/crash_diagnostics.open` supplies only the private directory, structured native
summary and newest OS-reported ANR timestamp. ANR lookup requires Android API 30+,
exact main process name, consent predating the event and the fixed ANR exit
reason. OS descriptions/trace streams are never read. This is next-launch exit
evidence, not a live watchdog or capture of every temporary freeze. Force-stop,
memory pressure and normal exit are not classified as crashes by this feature.

Native writes recheck the consent epoch after flushing and before rename. This
narrows the disable/write race but does not establish cross-runtime locking:
a concurrent fatal crash in the final-check/rename window may leave a safe
category-only native file after disable/clear. Disabled reads and next startup
erase it and do not import it. Do not claim unconditional physical erasure in
that concurrent-fatal window or against power/storage loss. Storage failures
also require an unavailable state; a failed call is not successful deletion.

Existing handled-error reports, performance timings and OS lifecycle recovery
are separate features with their existing policy. This opt-in governs the new
global Flutter crash record and the formerly always-on native crash summary;
it does not turn off Android’s own exit bookkeeping.
