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

**Correlation.** The reply is the set of assistant messages after the
server's echo of the sent user message and before any later user message.
On OpenCode v1 the wire `parentID` (now parsed into `MessageInfo.parentID`)
associates replies with the observed echo. Automatic playback requires one
new live user echo matching the send and explicit assistant parent IDs.
Missing parent IDs (including current v2/Codex mappings), duplicate matching
echoes, and intervening user messages keep reading manual. This is conservative
observed correlation, not a server-issued delivery receipt: a concurrent client
submitting identical text cannot be distinguished until both echoes arrive.
Playback happens once, only after dispatch acceptance, observed busy → idle,
and every reply message completed or errored. Stop, Exit, a server/session/scope
switch, a lifecycle pause, or a pending permission/question/form cancels
pending speech. Disconnect or connection rehydration invalidates the watch;
completion or failure consumes it, so duplicate events cannot replay speech.
Stop while waiting also cancels owed playback without cancelling/resending the
server turn or erasing an independently typed draft. No preference is persisted:
automatic speech is off again after exiting, changing scope, or backgrounding.

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

## Queued: Codex account panel (next E2E slice)

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
