# BB6: protect a phone-agent sign-in terminal in the background

Finish line: the real sign-in terminal reserves the app's shared foreground
service before launching its private PTY, keeps that reservation through the
external browser and account verification, and drains before its account owner
is deleted. Native account deletion, logging out real Claude, changing background
preferences and guaranteeing uninterrupted execution are outside this change.

## Default owner and terminal API

`AgentSignInForegroundRegistry` in
[`agent_sign_in_foreground.dart`](../../lib/domain/agent_sign_in_foreground.dart)
provides the production default, keyed by the canonical phone-account owner:

- `bind(profileId, AgentSignInForegroundPort)` returns an
  `AgentSignInForegroundBinding`. The primary connection supplies its shared
  `BackgroundLiveController`; secondary agent/server connections do not rebind.
- `bindingFor(profileId)` returns the current binding, or null. Protocol aliases
  retain the canonical owner rather than creating another service/account home.
- `unbind(binding)` invalidates that generation and awaits registered terminal
  cleanup before releasing its service protection. It must not remove a newer
  generation's binding.
- A binding exposes `current`, `reserve()`, `addCleanup(...)` and
  `removeCleanup(token)`. Terminal cleanup is registered before asynchronous
  admission, including a PTY launch that has not completed yet.
- A lease exposes `ready`, `active`, `lost` and `release()`. Launch awaits `ready`
  and checks current ownership. A replacement generation waits for the older
  generation's terminal drain before it can admit a new launch.

The existing sign-in terminal screen already calls the local-terminal sign-in
entry point. That entry point uses this default registry, so no UI edit is needed
to request foreground protection. This remains the same private agent sign-in
terminal and canonical account home; it does not create another profile.

## Lifetime and foreground meaning

The service reservation keeps the app process eligible to continue the sign-in
PTY when the person visits the agent's authorization page. It does not keep the
Flutter Activity on top of the browser. Android can still reclaim processes or
stop a foreground service; a reservation is not an uninterrupted-lifetime
guarantee.

Notification permission must be allowed before service admission and PTY launch.
Unsupported, paused, permission-denied or unavailable admission prevents launch
and yields a closed typed failure with plain guidance. The caller must give a
concrete way forward. The sign-in terminal's fixed public copy in
[`LocalTerminalSessions._signInFailure`](../../lib/builtin/local_terminal.dart) is:

| Failure | Public copy |
| --- | --- |
| `unsupported` | Sign-in isn't ready on this phone yet. |
| `paused` | Background work is paused. Resume it before signing in. |
| `permission` | Allow notifications before signing in, then try again. |
| `unavailable` | Could not keep sign-in running. Try again. |
| `cancelled` | Sign-in stopped. Start it again when you're ready. |

Native/provider errors and account identifiers do not enter public error copy.

Keep the reservation when the browser is opened, when it returns, and after the
CLI exits while the app verifies the named account state. CLI exit by itself
does not prove a sign-in and does not release the screen's protection. Screen
end/cancellation, retry replacing the previous terminal, disposal, service loss
or canonical owner deletion drains the registered PTY and releases the lease.
Cleanup and release must be safe to retry and safe against a late old launch.
If Android cannot confirm that the temporarily owned foreground service stopped
(`disable` fails or does not report `active: false`), release fails with the typed
`unavailable` reason. Service ownership and the terminal cleanup binding remain
available for retry; this does not claim a successful stop. A profile deletion
must wait for that retry to succeed before deleting the native account home.

## Owner teardown

The phone owner captures and clears its held binding before the first teardown
await. Foreground unbinding is awaited before other auth/host teardown and before
native account-home deletion. Local feed subscriptions, backend listeners and
socket heartbeat/retry timers retire synchronously on capture/disposal; they do
not own the sign-in PTY or its service reservation. Gateway closure still starts
browser-source revocation before closing its transport. Old owner cleanup
operates only on references captured from that owner; it must not dispose a replacement host, remove a new
binding or clear replacement subscriptions, rows or source maps.

Failed terminal drain blocks account-home deletion and retains its captured
binding, host and cleanup scope for a retry. It also blocks recreating a host for
that same owner while the old drain remains incomplete. A replacement owner may
create its own host; the old close never reads that host or its live maps. The
source throws exactly:

“Sign-in could not be stopped. Keep the app open and try again.”

Removing only a protocol alias retains the shared owner and its binding.
Controller disposal also unbinds its captured generation; a secondary connection
borrows the primary service and must neither replace nor release that ownership.
No foreground reservation writes a background-mode preference.

## Frontend cleanup failure handling

Foreground admission is already wired through the existing terminal entry point.
Claude should also consume `AgentSignInForegroundException` when awaiting
`endSignIn(shell)` from the retry action: keep the old shell handle, show
“Sign-in could not be stopped. Keep the app open and try again.”, and retry that
cleanup before starting a replacement terminal. The current unawaited retry
callback does not handle this new closed cleanup failure yet. Screen disposal
must observe the cleanup future's failure without presenting technical text or
removing the retained registry cleanup; owner deletion can retry it later.
These are frontend error-handling follow-ups, not a requirement to add another
foreground toggle or another account flow. No lib/ui source is changed here.

## Verification boundary

Focused source tests cover the default binding before a terminal request,
registered cleanup ordering before owner deletion, alias retention, failed drain
and retry, and late old-owner cleanup against a replacement host/binding.
Domain/service/terminal tests separately cover admission, loss and lease release.
The root lead runs serial checks with the pinned toolchain and machine lock.
Device/browser/account evidence is recorded separately; this contract does not
claim new device qualification or sign-in to a real account.
