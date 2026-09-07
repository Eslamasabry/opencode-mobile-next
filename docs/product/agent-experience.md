# Agent experience — portfolio A scope ledger

Owner: the voice/agent-experience worker (one feature per isolated worktree).
This file records what is built, what is queued, and the evidence each slice
rests on. It is updated on the branch that does the work; it is not a plan
for other portfolios.

## Implemented and locally verified: voice reply pipeline

**Finish line.** An explicitly enabled conversation reads the completed reply
to the reviewed voice send once, with visible waiting/speaking/Stop/Exit states.
**Non-goal.** Ambient recording, automatic microphone reopening, automatic
sending, account login, and provider calls.

**Evidence.** Offline STT, the reviewed voice composer, the voice
conversation mode (`chat/voice_conversation.dart`), and manual Read aloud
with consent and offline-voice selection (`chat/read_aloud.dart`,
`voice/read_aloud.dart`) already existed. The gap was an opt-in turn flow
that speaks the reply to the message the user just sent.

**Journey.** Tools → Voice conversation → Listen → review transcript → Insert
→ turn on *Speak replies* (consent sheet, then offline voice picker, before
anything is spoken) → Send → *Waiting for the reply…* → *Speaking the reply*
with Stop → Listen again or Exit. The microphone never opens by itself; the
reply text stays on screen.

**Correlation (P1 correction).** Automatic playback requires an app-authored
message ID carried unchanged on the dispatched v1 request, the exact live user
echo of that ID, and completed assistant messages whose `parentID` matches it.
The heuristic text/attachment/time `canonicalID` is never playback authority.
Correlated optimistic sends also refuse heuristic reconciliation against another
ID. A single foreign identical-text echo can complete before our own echo is
delayed or absent: it remains silent, consumes the watch conservatively, and
cannot cause later history/echo events to re-arm playback.

The pinned v1 generated request already exposes optional `messageID`. Exact
[upstream prompt source](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/session/prompt.ts#L619)
uses it as the user message ID and preserves it on save. The same source assigns
assistant `parentID` from the user ID. The app's random ascending message ID uses
the pinned [identifier format](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/id/id.ts#L48).
This is correlation, not an idempotency or resend contract. No generated SDK
files were edited. Unsupported transports or servers that ignore/replace the
ID remain manual; missing parent IDs, mixed parents, or multiple live user
messages also keep reading manual.

Playback still requires dispatch acceptance, observed busy → idle, and all
reply messages completed or errored. Stop, Exit, lifecycle/scope changes,
approvals/questions/forms, disconnect and history rehydration invalidate the
watch. Nothing automatically opens the microphone or resends a prompt. The
feature stores no preference or pending speech across scope/background changes.

**Correction verification (2026-09-08).** On the corrected source from `a8b27bf`,
**131 focused checks passed**: prompt transport (3), voice pipeline (23 including
3 capture cases), existing voice composer/read-aloud (13), and chat live events
(92). The new cases complete a single foreign identical-text turn while our own
echo is delayed or absent; no TTS or extra microphone/send occurs. A transport
without the correlation capability stays manual. The generated HTTP request
fixture verifies that the authored ID reaches the wire unchanged and all other
prompt fields retain their previous representation.

The first serial run passed 128 and failed only its three capture cases because
the newly selected output directory did not exist. After creating that ignored
directory, only those three capture cases were rerun: all passed. No behavioral
source changes followed the tests. Two analyzer style findings were corrected
(braces and equivalent null-aware map syntax); final analyzer passed with no
issues. Changed files were formatted and the diff check passed. No l10n copy
changed, so generation was unnecessary.

Commands/evidence for the correction (ignored):

- `flutter test --no-pub --concurrency=1 test/prompt_transport_test.dart
  test/voice_reply_pipeline_test.dart test/voice_composer_test.dart
  test/read_aloud_test.dart test/chat_live_events_test.dart`:
  `build/traycer/voice-correlation-tests.log`.
- Exact capture rerun: `flutter test --no-pub --concurrency=1
  test/voice_reply_pipeline_test.dart --plain-name "capture production voice states"`:
  `build/traycer/voice-correlation-captures.log` (3 passed).
- `flutter analyze --no-pub`: `build/traycer/voice-correlation-analyze-final.log`
  (no issues, 14.4 seconds).
- `OC_VOICE_REPLY_CAPTURE_DIR=build/traycer/voice-captures-correlation`: 13 fresh
  PNGs. Dark waiting, light speaking, large error and large scrolled controls
  were visually inspected. These remain synthetic widget renders with mocked
  native audio; no provider/live-account/server or hardware-speech proof.

The earlier verification record below describes the original implementation
and captures, not a substitute for this correction's results.

**Verification (2026-09-08, Flutter 3.47.2 / Dart 3.13.2).** `test/voice_reply_pipeline_test.dart`
covers default off, completion/acceptance ordering, Stop during dispatch and
waiting, Exit/background/scope/disconnect/approval cancellation, ambiguous
echo/parent correlation, native failure with explicit retry, and duplicate
reconnect events. Microphone invocation counts guard against reopening.
Optional production-widget captures use `OC_VOICE_REPLY_CAPTURE_DIR` and write
off/waiting/speaking/error PNGs in dark, light, and 2× text variants beneath the
ignored `build/traycer/` tree. These are fixture renders, not device speech proof.
The final serial run passed **33 tests**: 17 pipeline regressions, 3 capture
cases, and 13 existing composer/read-aloud tests. All 13 PNGs were rendered;
dark/light states and enlarged-text rendering were visually inspected, including
the scrolled large-text Exit action. The panel has an explicit scrollbar at
enlarged text sizes; no render-overflow exceptions occurred.

Commands/evidence (ignored local files):

- `flutter pub get`: dependencies resolved and this worktree's package config
  generated; exit 1 at the Windows desktop plugin symlink step because Developer
  Mode is unavailable. No machine setting was changed. Subsequent checks used
  the resolved packages with `--no-pub`.
- `flutter gen-l10n`, pinned Dart formatting, and `git diff --check`: passed.
- `flutter test --no-pub --concurrency=1 test/voice_reply_pipeline_test.dart
  test/voice_composer_test.dart test/read_aloud_test.dart`, with capture directory
  set: 33 passed, 0 skipped (`build/traycer/voice-final-tests.log`).
- `flutter analyze --no-pub`: no issues (`build/traycer/voice-analyze-final.log`).
- PNGs: `build/traycer/voice-captures/{dark,light,large}-{off,waiting,speaking,error}.png`
  and `large-off-actions.png`.

Native TTS and recording are mocked in these tests. No device, provider, or live
server was contacted; native compilation, hardware audio validation, and the
repository-wide serial integration gate remain unrun. Nothing is deployed or
released by this branch. Correlation on v2/Codex remains manual as described
above; this does not claim automatic voice replies across all transports.

## Next priority after voice correction: A2A interoperability

Queued by the coordinator: a phone client for external A2A agents, with card
inspection, supported authentication, explicit task dispatch, progress and
input-required states, results/reopen/cancel. Pin and prove the official 1.0.1
binding against a synthetic official server before enabling an adapter. No
hosting, public listener, or provider spend. SaaS is cancelled. This priority
comes before resuming the account WIP; it is not implemented in this branch.

The proposed "Try two approaches" idea stays queued behind isolation and usage
proof: explicit separate workspaces, visible added compute, compare results,
no automatic winner/merge. It does not authorize another feature or paid run.

## Preserved WIP: Codex account panel

Account source was checkpointed separately at `a7e2b3a` on
`feature/codex-account-panel`; it must not be merged. The installed 0.153.4
schema includes the account/device-code/rate-limit/usage methods. Callable
scratch proof, generated localization, fixtures, tests, analyzer and captures
remain pending. The original queued scope below is context, not a finished
feature or current verification claim.

**Evidence.** The pinned Codex app-server adapter (`lib/codex/transport.dart`,
`lib/codex/gateway.dart`) connects with a capability token and uses
`initialize`, `thread/*`, `turn/*`, `model/list` only. The published
app-server surface (root-verified, https://learn.chatgpt.com/docs/app-server)
also lists `account/login/start` (type `chatgptDeviceCode`),
`account/login/cancel`, `account/login/completed`, `account/read`,
`account/rateLimits/read`, `account/usage/read`. None is wired. Exact
availability on the pinned CLI build must be verified against that binary
before any of it is enabled.

**Journey (target).** Servers → Codex profile → *Account* panel: signed-in
state and plan as `account/read` reports it; rate limits and usage as the
server reports them, with the server's timestamps; a *Sign in* action that
starts device-code login through the app-server, shows the code and URL
large and copyable, polls `account/login/completed`, and cancels cleanly.
The host owns credential storage; the app never reads or copies auth files
or tokens. No provider spend and no real login in development.

**Feasible first slice.** Read-only panel (`account/read`,
`account/rateLimits/read`, `account/usage/read`) with stateful, large-text
UI and honest "not reported" states; device-code sign-in as the second
slice once the pinned CLI is confirmed to serve it.

**Blockers.** Pinned CLI capability check; whether the capability token
grants the `account/*` methods; UI copy for plan names must come from the
server, not be invented.

## Queued: Claude official-runtime route

**Evidence.** Claude Code's legal terms (root-verified,
https://code.claude.com/docs/en/legal-and-compliance) allow preinstalling
or hosting the unmodified CLI under its commercial conditions with
user-owned billing, and prohibit offering our own claude.ai login or
collecting, storing or intermediating subscription tokens. Custom Agent SDK
UI must use supported API/provider auth.

**Journey (target).** A truthful route that runs the official CLI's own
login inside the managed environment (the CLI owns the credential), with the
app showing state and opening the CLI's flow — never a claude.ai login form
of our own, never token copy, never shared-subscription resale.

**Blockers.** Managed-environment support for the CLI on the device or
host; a clear statement to the user of who bills whom; scope to be planned
after the voice slice is tested.

## Queued: mobile voice/context tools

Small, testable additions on the existing voice and context surfaces
(e.g. a spoken summary of pending decisions, voice-friendly context pickers).
Each gets its own worktree and evidence section here when started.
