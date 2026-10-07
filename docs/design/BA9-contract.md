# BA9 — trusted browser launch hooks

Finish line: phone-host Claude conversations reserve a native launch generation
before any prompt, mode/model change or scoped command request, and retire it
before abort/replacement/deletion/host cleanup. Non-goal: enabling or qualifying
the browser, editing native code, installing BE's transport or changing UI.

## Installation and frontend state

The connection constructor accepts `browserClaudeLaunchRegistry`, shared by
its phone chat and feed gateways and side controllers. Construct it with BE's
adapter implementing `BrowserClaudeLaunchPort`. The default registry uses
`const NoopBrowserClaudeLaunchPort()` (null reservation, no-op revoke).
No browser choice is made by default; ordinary chats keep their original create
and prompt path. A reservation is non-secret generation metadata, **not** an
MCP grant, browser lease, permission, provider qualification or effective capability.

UI calls the active phone conversation's `ConnectionController`:

```dart
Future<void> setAgentBrowserRequestedForSession(
  String sessionID, {required bool requested}
);
```

This is an explicit transient choice, never persisted. Setting true does not
launch or grant anything. Only Claude is eligible. A failed reservation refuses
the requested operation with fixed plain copy:

> The agent browser is unavailable for this chat. Turn it off to continue, or start a new chat.

Turning the choice off explicitly revokes its generation and permits ordinary
conversation work. A cleanup failure quarantines new enrollments and retains
the choice; no prompt silently falls back to an unenrolled launch. BE's runtime,
network, consent, user-visible Stop and takeover gates remain independent and
closed until qualified. No new technical text is surfaced; native errors are
contained by the registry. No account/provider credentials cross these hooks.

## Port owned by BA, adapter owned by BE

Types live in `lib/domain/agent_tools/browser_claude_launch.dart`:

```dart
abstract interface class BrowserClaudeLaunchPort {
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  });
  Future<void> revokeBrowserClaudeLaunch(BrowserLaunchReservation reservation);
}
```

`BrowserEnrollmentTarget` carries those four IDs.
`BrowserLaunchReservation(id, launchId, target)` carries the exact opaque native
generation; its shape/validation mirrors BE2. BE converts these BA types to/from
its enrollment coordinator types explicitly; BA imports no BE-owned files.
The BE adapter must refuse an already-spawned unenrolled process, including a
resume implementation that spawned before the reservation. A successful hook
must establish a pending pre-spawn intent through the native-owned host; it
cannot grant authority retroactively from a daemon ID alone.

IDs come from the trusted owner mapping: the real phone host profile (never its
transient chat profile suffix), `paseo:<actual directory>` supplied by the owner,
and canonical mapped daemon conversation ID for both sessionId/daemonAgentId.
Feed and chat gateways therefore share a reservation for the same conversation.
The owner supplies a source resolver when a gateway is moved to another folder;
retiring that transport preserves the old folder's choice. Source/profile
removal explicitly clears it. No guest-supplied PID, model or tool argument
selects this scope. Existing BE2 limits apply: IDs use its <=128 UTF-8-byte,
non-control shape; unsupported scopes fail safely.

## Ordering and lifecycle

- New requested conversation: create without initialPrompt, images or message
  ID; validate provider/directory and map the daemon ID; reserve; send the exact
  original message and images once. A cancellation/deletion during cold create
  cannot resurrect the draft; only its validated cold daemon record is archived.
- Follow-up and public mode/model setters: resolve the mapped daemon ID,
  reserve/reuse its current generation, then mutate. Every final send checks
  current scope, choice and generation after asynchronous work.
- Commands: folder-wide `listCommands()` returns empty whenever this source has
  a browser request, even before a second gateway has session inventory.
  `PaseoGateway.listBrowserCommandsForSession(id)` lists through the mapped
  conversation only after reservation. Slash commands use the prompt path.
  A cold draft's scoped discovery is unavailable until its ID is mapped.
- Resume: revoke the previous generation before resume; validate/map returned
  ID, transfer transient intent, reserve before any subsequent spawn path.
  The port must reject a resume that already spawned unenrolled Claude.
- Replacement, abort and session deletion revoke the old exact generation.
  Transport loss/gateway retirement revoke authority but retain explicit intent.
  Closed/error daemon snapshots retire authority. Source/profile deletion and
  phone host stop/disposal revoke before host cleanup; cleanup failures do not
  prevent deletion. Pending/late ACKs cannot become active after retirement.

`BrowserClaudeLaunchRegistry` bounds active/pending/retiring native work to
at most eight operations and two-second waits; unresolved timed-out calls retain
slots. Transient choices have a separate limit of 128. Returned IDs are checked
for exact target/generation and reuse; inconsistent IDs or failed cleanup
quarantine enrollment. BE must revoke exact generations idempotently before
resource cleanup, retire replaced native generations atomically, and use a new
native epoch after uncertain cleanup. A fresh registry is needed after quarantine.

No persistent data or migration is introduced. BA9 does not claim device/native
browser proof; BE owns its adapter and qualification, coordinator owns frontend.
