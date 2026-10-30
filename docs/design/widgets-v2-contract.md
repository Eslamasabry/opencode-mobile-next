# Home-screen widgets v2

Owner selection: 2026-10-15, controls 1–5, motion 6–8, responsive layouts 9–11,
and colours C1/C3/C4/C5. This branch supplies native rendering, background
routing, snapshot/action contracts and the native floating Flutter host. The
configure and steer/voice Flutter screens belong to the UI builder. They are
not implemented or device-qualified here.

## Ownership and data flow

`ConnectionController` publishes the compact snapshot when state changes.
`WidgetSessionSnapshot` persists it in `oc.widgetSessions` (native SharedPreferences
key `flutter.oc.widgetSessions`). Providers render this data without network
access. An immutable widget PendingIntent targets `BackgroundConnectionService`
directly. An existing live engine uses `dispatchWidgetAction`, the `oc/background`
channel and the controller's domain gateway. With no usable live channel, an
explicit button tap can own one temporary headless engine and connection for at
most 30 seconds. It submits one revalidated action and closes after settlement.
It never opts into persistent background mode or starts automatically.

Android 14+ uses shortService alone for temporary work; older versions use
bounded dataSync. Persistent live mode retains its dataSync budget and pause
handling. There is no periodic server poll, automatic mutation replay or promise
of background lifetime. Chronometer and ViewFlipper run in the launcher only
while the connection is live. Platform rules and limitations are recorded in
[the 2322 fixes ledger](../qa/widgets-v2-2026-10-15/fixes-2322.md).

No provider keys, auth material, prompts, tool arguments/output, raw server
errors or drafted messages belong in the snapshot, action ledger, logs or
notification text. Titles are short, sanitized display titles; step lines are
safe generic activity labels. Checklist labels are localized numbered steps,
with completion flags from the real task list, rather than raw task bodies.
Routing IDs are private implementation data and must not be displayed.

## Snapshot version 2

The existing shared sessions snapshot is extended, not moved. Its deletion
transaction remains responsible for erasing the shared blob. Old data without
v2 action identity has no actionable controls. Providers also retain their
existing usage-plan snapshot input.

| Field | Meaning |
| --- | --- |
| `version` | Integer `2`. |
| `profileID` | Profile that owns all rows. |
| `scopeToken` | Opaque controller scope; changes with transport/location identity. |
| `connected` | Whether this snapshot represents a connected transport. |
| `runningCount`, `pendingCount` | Nonnegative totals before the display row cap. |
| `lastProjectID` | Opaque routing identity for the last connected project; never UI copy. |
| `appTheme` | Optional ARGB integers `background`, `text`, `muted`, `accent`, from the selected app palette. |
| `sessions` | Bounded array of the rows below. |
| `onDemand` | Optional opaque descriptor `{fingerprint}` only. Directory/workspace never enter the snapshot; the fingerprint covers saved non-secret profile routing metadata. |
| `actionResult` | Optional latest receipt: `{id, action, status, error?}`. Never submitted text. |

App-private routing is stored separately in `oc.widgetTarget.<profileID>`
(native prefix `flutter.`), containing `{profileID, scopeToken, fingerprint,
directory, workspace}`. The app writes it before the opaque snapshot. The headless
runner reads this key and requires matching profile, scope and fingerprint;
missing or replaced routing rejects the tap, without using a default directory.
Profile deletion sweeps the key in Dart and native. An older snapshot carrying
raw routing is migrated and stripped on the next app publication or suspension.

There are at most four display rows, including at most one AI Team project.
Display titles are capped at 80 Unicode code points. A row has `id`, `title`, `busy`, `updatedAt` (epoch milliseconds), `startedAt`
(epoch milliseconds for the observed run), `step`, `requestID` (the exact
answerable permission), `steerMode`, `todos: [{title, done}]`, `tasksDone`,
`tasksTotal`, optional `onDemandActions` (allowOnce/reject/stop/approvePlan/resume),
optional `runMarker` (server user-message ID, never its body), and optional `team`.

`steerMode` is `steer`, `afterReply`, `restart`, or `unknown`. `team` contains
`runID`, `gateID`, `revision`, `action` (`approvePlan` or `resume`), `done`, and `total`.
These identifiers represent the domain project and its revision-bound action;
the widget does not invent permission to approve or resume from display text.
Only the currently supported next action is published. Unknown/unsupported
capabilities have no actionable control.

## Actions, results and fencing

The live `oc/background` native-to-Dart method is `widgetAction`. Its payload is:

```text
{id, action, profileID, scopeToken, sessionID, requestID,
 runID, gateID, lastProjectID, expectedStartedAt, text?}
```

`id` identifies this attempt. Direct buttons have a stable identity tied to the
published request/run. Text submission has a fresh attempt identity. `text` is
transient and bounded; it is never part of persisted action identity or receipts.
`expectedStartedAt` binds Stop/Steer to the displayed run. Profile, scope,
request, session and team revision are checked again before mutation. Profile
switch, reconnect, deletion, a replacement request, or a replacement run rejects
the old control. A bounded native claim ledger prevents duplicate dispatch and
process-death replay; an uncertain attempt must be inspected in the chat before
another submission.

| Action | Domain operation and truthful acknowledgement |
| --- | --- |
| `allowOnce` | Resolve the exact still-pending permission with `once`; never Always allow. |
| `reject` | Resolve that exact permission with `reject`. |
| `stop` | Abort the observed running session through the domain gateway; cold dispatch additionally verifies the saved server run marker. |
| `steer` | Submit transient text using the runtime's supported running-turn behavior below. |
| `voice` | After dictation and explicit Send, create a conversation in the bound last project and send the text. |
| `approvePlan` | Domain AI Team plan approval against the published revision. |
| `resume` | Domain AI Team resume against the published revision. |

The result is `{id, action, status, error?}`. `status` is `accepted`, `rejected`,
or `uncertain`. Safe error values are `invalidRequest`, `staleRequest`,
`unsupported`, `unavailable`, `busy`, and `uncertainDelivery`. Accepted means the
operation was acknowledged, not that the agent has already consumed the new
instruction. A transport failure after dispatch may be uncertain. Never retry
it as a new turn or silently switch to another delivery mode.

Temporary engine contract: entrypoint URI
`package:opencode_mobile/background/widget_action_entrypoint.dart`, function
`@pragma('vm:entry-point') widgetActionMain`, exported by `lib/main.dart` for AOT.
It installs `MethodChannel('oc/widget_action')`, then calls native `ready`.
Native invokes `execute` with `{action: <above payload>, snapshot: <v2>,
deadlineEpochMs: <integer>}`. Result is `{id, action, status, error?, snapshot?}`;
only an accepted matching result may patch the stored snapshot's safe status
fields. It never mounts the app shell or creates a ConnectionController.
The deadline includes startup; native owns a monotonic watchdog and destroys the
engine at expiry. Late/unknown results never become success or trigger a retry.

Saved OC1/OC2 permissions and runs, and already-reachable phone-engine Team
projects, can advertise cold actions. Socket-agent approvals tied to another
transport, transient profiles and missing run proofs do not advertise them.
Lifecycle suspension retains cached target identity but marks it disconnected.
The runner freshly checks the saved profile, scope and target plus server state
before dispatch. Unavailable/unsupported targets retain the explicit Background
settings route. Pending controls remain tappable so an abrupt process kill cannot
strand disabled launcher views; the native lease and claim ledger reject overlap.

Follow [the steering contract](steering-contract.md): native steering uses
`PromptDelivery.steer` when the gateway advertises it; a mixed-runtime
`MidTurnPromptGateway.joinsTurn` uses its normal prompt path. OC1's supported
behavior is labelled “Send after this reply.” A per-session `restartsTurn` is identified as `restart`, but this widget slice
keeps it unavailable: it does not add an interrupt-and-restart control without
the corresponding consent UI. `unknown` is also unavailable until real session
details are known. The builder must not label either state as native steering. No queue chooser
exists for steer-capable agents. Dictated and typed input use the same action.

## UI builder: exact floating-host integration

Launcher configuration enters exported `WidgetConfigureActivity`; widget
composer taps enter private `WidgetRouteActivity`. Both validate widget ownership
and readiness before opening the private host. Native host:
`WidgetOverlayActivity`, a transparent `FlutterActivity` with a
separate UI engine. Dart entry point: top-level
`@pragma('vm:entry-point') void widgetOverlayMain()`. Implement it without the
normal app shell or an independent connection controller. It renders kit-only
content and communicates with native over `MethodChannel('oc/widgets')`.

The manifest metadata
`io.github.eslamasabry.opencode_mobile.WIDGET_OVERLAY_READY` stays `false` until
the entry point and all three routes exist. Set it `true` only in the builder's
completed UI commit. Missing UI must not fall back to launching the full app.
Widget configuration is optional during this handoff so placement can still
show a default widget. Enable the placement configuration journey when the UI
lands, retaining reconfiguration support.

Routes:

- `/widgets/configure`: a small kit style selector for this widget instance.
- `/widgets/steer`: a small kit composer with typing, dictation and the fixed
  action appropriate to `steerMode`.
- `/widgets/voice`: start dictation with normal microphone permission handling;
  show the transcript for correction and explicit Send in the last project.

Call `getLaunchArguments` before rendering. It returns `route`, `widgetId`,
`profileID`, `scopeToken`, `sessionID`, `requestID`, `runID`, `gateID`,
`lastProjectID`, `expectedStartedAt`, `steerMode`, and
`style: {background, accent}`. Treat routing data as read-only. Never obtain
credentials or reconstruct a server client in this engine.

| `oc/widgets` call | Arguments/result |
| --- | --- |
| `getLaunchArguments` | No arguments; returns the bound launch map above. |
| `saveStyle` | `{background, accent}`; validates and saves only this widget's style, then redraws. |
| `dictate` | Optional `{languageTag}`; opens the system speech recognizer and returns `{text}` for editing. No automatic Send. Errors: `cancelled`, `unavailable`, `busy`; bounded to 60 seconds. |
| `submitAction` | `{text}`; native supplies the immutable launch identity and action; returns the action result map. |
| `close` | No arguments; dismisses the floating host. |

The system speech activity owns microphone permission; the overlay does not
record audio. Dictation results and typed drafts are bounded to 8,000 UTF-16
code units. Style failures use `PlatformException` codes `wrongRoute`,
`invalidStyle`, `staleSnapshot` or `unavailable`; action failures use the result
enum above. `saveStyle` returns `{saved: true}` and closes the host on success.
The supplied Dart wrapper is `WidgetOverlayPort` in
`lib/background/widget_v2_contract.dart`.

Background choices: `solid`, `tinted`, `transparent`. Accent choices:
`wallpaper`, `app`, `green`, `teal`, `blue`, `amber`. Do not send colours or profile
IDs supplied by a text field. Retain drafts only in this UI's memory on failure;
do not log, persist or auto-submit them. Prevent a second submission while one
is outstanding. On uncertainty, show `widgetV2Uncertain` and require the user to
check the conversation. English and Arabic `widgetV2*` copy lives in the ARBs;
localization generation and screen/Widgetbook work belong to the UI integration.

The native keys `oc.widgetStyle.<profileID>` and
`oc.widgetActions.<profileID>` use the required profile suffix. They contain
per-widget style maps and bounded action IDs respectively. Profile deletion
must clear native-owned keys as well as the shared snapshot; cached Dart prefs
alone cannot discover native writes. Removed widget instances drop their style.

## Responsive rendering, motion and colour

> Presentation superseded on 2026-10-20 by the owner's "A, adaptive" pick: one card
> in every size (no list layout, no ViewFlipper, no reply preview), the list entry
> hidden from the picker, and picker images rendered from the card itself. See
> [docs/qa/vision-widget-2026-10-20/README.md](../qa/vision-widget-2026-10-20/README.md).
> Snapshot, action and fencing contracts above are unchanged; rows gained `server`
> and `needsYou`.

Launcher cells vary, so dp dimensions select layouts rather than a hardcoded
cell count. Android 12+ gets a responsive `RemoteViews` size map; older Android
uses widget size options and redraws when resized.

Exact policy: compact when width <120dp or height <110dp, list when width
>=250dp, card otherwise. API31 maps include 40×40, 120×110, 120×180,
250×110, 250×180 and 250×280dp. The 180dp card exposes checklist detail;
flipping uses a six-second interval.

- Compact, approximately 1×1: status dot and running/needs-you counts.
- Card, approximately 2×2: top conversation, relevant action and live step;
  multiple working conversations rotate with a local ViewFlipper animation.
- List, approximately 4×2: bounded rows with independent actions.

Only a working row starts its Chronometer and indeterminate progress indicator.
A run ending removes the running state. Android 12+ checklist rows use checked
CompoundButtons as read-only reflection; tapping opens the associated chat,
never changes a server todo. Earlier versions use a noninteractive indicator.
The root uses `@android:id/background` and chat taps use activity PendingIntents
for the Android widget launch transition.

Wallpaper system colours are used on Android 12+, with the app palette as the
older-device fallback. Style changes affect one widget instance. Running stays
in the composer green family, needs-you is amber, and completed/idle is neutral.
Usage moves from green when there is plenty left, to amber near the limit, to
neutral grey when used up. Red is not a widget status or action colour.

Android references: [responsive widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts),
[widget transitions and colours](https://developer.android.com/develop/ui/views/appwidgets/enhance),
and [RemoteViews supported APIs](https://developer.android.com/reference/android/widget/RemoteViews).

## Emulator acceptance plan (not executed here)

1. Test API 30 fallback, API 31 responsive/CompoundButton/transition behavior,
   and API 35+ service timeout. Place both widget types, resize through compact,
   card and list, rotate, and try large font sizes plus Arabic RTL.
2. Use synthetic profiles and short fixture titles only. Verify event updates,
   zero polling, two running cards with latest steps, chronometer continuity,
   stopped timer/ring after completion, and read-only checklist taps opening the
   correct profile/session. Confirm launcher pause/resume and reduced animation
   behavior; take screenshots/video of each size and state.
3. Allow once/reject the exact pending request without an activity opening.
   Repeated taps must dispatch once. Replace the request, run, profile and scope
   before tapping an old PendingIntent; each must reject without a mutation.
4. Stop one of several running sessions and verify only that session stops.
   Test accepted, rejected, missing engine, service inactive, background timeout,
   and delivery-uncertain receipts. Verify direct user-tap startup, bounded cleanup,
   and no automatic restart; follow the expanded 2322 emulator plan.
5. After the UI builder lands, configure two instances independently; cancel
   placement/reconfiguration, relaunch, rotate and recreate the overlay. Test
   solid/tinted/transparent and all accents on light/dark wallpapers. Check
   contrast and confirm no red for usage or request states.
6. Type and dictate in the floating box. Verify OC2/Codex/Paseo supported agents
   add to the same turn, OC1 has truthful after-reply delivery, restart runtimes
   have the correct warning, and unknown behavior cannot submit. End the turn
   while the box is open, replace scope, fail the network after dispatch, deny
   microphone permission, and send a second edit while the first is pending.
   No draft loss, duplicate send, queue fallback or full app shell is allowed.
7. Voice starts a new message in the last project only after reviewed Send.
   Switch/delete that profile while the overlay is open; verify no new session
   or style can be recreated. Inspect only synthetic preferences for erased
   snapshot/style/ledger data; never inspect owner credentials.
8. Exercise AI Team plan approval/resume with real domain revisions. Change the
   project revision before tapping and verify rejection. Progress must match
   completed/total tasks, not timer estimates or simulated workspace data.
9. Simulate the Android dataSync time limit and reconnect after foreground
   resume. Widgets must show current reconciled state and must never bypass the
   OS limit. Validate usage colour boundaries and unknown/stale usage data.

## Verification ledger

Supplied automated checks and their execution status are recorded below at the
integration checkpoint. The AI Team progress row currently opens the app normally when tapped; its
synthetic project ID must not be used as a chat ID. Launcher layout, RTL, font
scaling and touch targets remain emulator acceptance work. Compact list actions
are 36dp high and card buttons 32dp; these need accessibility review/tuning on
the host before calling the presentation qualified. An abrupt process kill can
leave the launcher’s cached timer visible until the next redraw; direct actions
require cold reconciliation without the live service. Known service shutdown/timeout invalidates
the connected snapshot.

No Flutter command, emulator, live provider, signing,
release, push or deployment is part of this task.

Local checks on 2026-10-15 (base `851f9f2f7ceb21d4553f1c8b88e1c80c89e5ef72`):

- `tool/qa/machine_lock.sh build -- python3 tool/qa/widgets_v2_native_check.py`
  passed: API 37 AAPT2 resource compile/link, focused Kotlin API compilation,
  and **21 JVM tests** (`WidgetActionPolicyTest`, `WidgetV2PolicyTest`, existing
  `UsageWidgetFitTest`). The harness compiles the exact service dispatch method,
  with MainActivity/service lifecycle and the unrelated AppCompat base theme
  stubbed. It is not a Gradle build, full application compile, instrumentation
  run, Android permission/lifecycle test, or launcher rendering proof.
- `python3 tool/l10n/check_completeness.py` passed: no new missing Arabic keys.
  New builder-facing ARB entries have not been generated into localization
  classes; no current implementation references ungenerated getters.
- Pinned Dart SDK analysis passed with **No issues found** for
  `lib/background/widget_snapshot.dart`, `lib/background/widget_v2_contract.dart`,
  `lib/background/live_background.dart`, `lib/state/connection.dart`,
  `lib/state/connection/widgets.dart`, and the three supplied/affected test files.
  It used the worktree-local cached package map, with no dependency installation
  and no Flutter invocation.
- `git diff --check` passed. XML resources passed native resource compilation.

Flutter test files supplied/affected, **not executed**:

- `test/widget_v2_contract_test.dart`: 17 new tests covering snapshot/action boundaries, fake-gateway
  dispatch, stale routing/run/request guards, delivery selection, deduplication,
  uncertain delivery, privacy limits, priority ordering and native deletion.
- `test/widget_snapshot_test.dart`: existing snapshot/delete regression, updated
  priority ordering expectations.
- `test/profile_deletion_test.dart`: native clear acknowledgement fixture updated.

Additional integration regressions to run with the pinned Flutter toolchain:
`test/background_action_test.dart`, `test/background_live_test.dart`,
`test/background_coding_alert_test.dart`, `test/profile_deletion_test.dart`,
`test/usage_widget_snapshot_test.dart`, `test/codex_steering_test.dart`,
`test/paseo_midturn_send_test.dart`. Full serial suite, localization generation,
Android lint/Gradle release compilation and the emulator plan above remain owed.
The owner-phone signer, release, deployment and push states are unchanged.

## APK 2322 follow-up

The [2322 fixes and verification ledger](../qa/widgets-v2-2026-10-15/fixes-2322.md)
supersede the initial behavior for inactive service recovery and successful
receipt display. `background_settings` opens the existing background settings
section without enabling it. Accepted action receipts no longer occupy the
header. Snapshot titles use the app's presented title helper and locale, and
card step text wraps to two lines. Usage now starts at 120×110dp / 2×2; narrow
layouts show one tightest window and widths at least 180dp show two. Owner
placement/style fixes remain intact. The Usage footprint diagnosis is not yet
an emulator reproduction; follow the linked retest plan.
