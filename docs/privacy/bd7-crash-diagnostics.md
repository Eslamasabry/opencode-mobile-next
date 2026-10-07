# Optional saved crash details

Saved crash details are OFF until the person explicitly turns them on. The
backend is ready for a consent control; that UI and its Arabic copy are owned
by the frontend lane and are not complete in this branch.

When enabled, the app saves the kind of global Flutter/native error and its
timestamp. Android can also supply the timestamp of a main-process exit it
classified as “app not responding”, on Android 11/API 30 and later. The native
summary can include up to three fixed cause categories. The capture code does
not save exception values, conversations, provider response bodies, credentials,
URLs, device/user identifiers, arbitrary class names, filesystem paths, thread
names or stack traces. Fixed categories are selected before storage; this policy
does not depend on a provider key matching a regex or being registered first.

Records and consent stay in app-private storage. Nothing is uploaded, logged,
notified, copied or shared automatically by this feature. Global Flutter hooks
and delegated native fatal handlers receive fixed error categories rather than
the original exception values. Manual diagnostics export remains a separate
explicit action in the existing App diagnostics UI.

Flutter evidence is a 20-entry, 8 KiB ring. Native evidence is a single bounded
private snapshot. Flush plus atomic rename provides process-crash durability,
not a guarantee against power failure or broken storage. Invalid records are
discarded rather than rendered. Capture failures never throw filesystem errors
into crash handling.

Turning off removes consent and saved Flutter/native records; clearing retains
consent and starts a new epoch. Both clear current App diagnostics, which also
clears the existing saved problem report and its timing history. This does not
delete profiles, credentials, conversations or server files. Native code checks
consent again before committing. A fatal crash concurrent with the final
check/rename can still leave a safe categorical file; disabled reads and the
next startup delete it without importing it. Failed storage operations must
show an unavailable state, never a successful deletion confirmation.

This feature does not collect OS ANR traces or predict the cause of a crash.
Android’s own lifecycle records and existing handled-error/performance reports
retain their separate behavior. Device restart/crash/ANR qualification and the
complete consent journey are still unverified; focused local evidence is in
`docs/qa/bd7-2026-10-07/README.md`.
