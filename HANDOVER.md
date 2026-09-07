# Development handover

Branch: `dev`, upstream `mobile-next/dev`. Continue in the existing checkout.
Source checkpoint: `499c0e1a0aaaddd6edd9b1bd899b79133a687f87`.
All current worker edits are frozen for this machine transfer. Do not start
another feature until the Codex integration checkpoint below is reviewed.

## Current feature

The active major slice is experimental Codex mobile chat: add an authenticated
connection and absolute host project folder, save/connect, open or create a
conversation, stream text, allow once or reject an approval, cancel a turn,
and resume history after reconnect. Existing OpenCode connections retain their
transport and credential behavior.

Implemented: separate secure Codex token storage, controller integration,
connection probe and editor, capability-gated navigation/chat, token recovery,
local offline drafts without automatic replay, and scoped gateway operations.
Root review corrected stale editor errors, existing-profile save/connect
navigation, unsupported persistent approval choices, and item completion
incorrectly ending the busy state before the Codex turn completes.

## Verification checkpoint

Final source checks are recorded in
[the integration report](docs/verification/codex-connection-2026-09-07.md).
The full run exposed six failures, subsequently corrected in focused checks.
A complete rerun on the corrected source remains required; this is a transfer
checkpoint, not a fully verified release candidate. Earlier Android runs
used only synthetic data, plus a separate real local CLI handshake proof.
They do not prove a live provider turn or physical-device behavior.

Earlier Android checks demonstrated connection,
history, normal response, accept/reject, reconnect without automatic send,
explicit cancellation after history resume, new conversation, and restart
persistence. The final APK also confirmed saved startup, the corrected Stop lifecycle and
supported approval choices. The complete editor/re-entry/reconnect/large-text
pass remains pending, as does the corrected-source full test suite.

## Resume commands

Use Flutter 3.47.2 with Dart 3.13.2, JDK 17 and Android SDK API 37.
Read `AGENTS.md` before running tools. The local verification used the matching
Flutter source tag with its standard engine for debug APKs; it was not a
Shorebird release/signing build.

```sh
git fetch mobile-next dev
git switch dev
git pull --ff-only mobile-next dev
flutter pub get
flutter analyze
flutter test --concurrency=1
(cd packages/opencode_sdk && dart analyze && dart test --concurrency=1)
```

Include nested test directories. Serialize Flutter operations and tests; do
not run ten test processes with the workers. If chunking, freeze all relevant
source and retain a complete manifest. Do not combine results from different
source candidates.

A reproducible, standard-library-only synthetic server is in
`tool/qa/codex_fixture/`; see its README. No account or provider setup is needed.
The [connection guide](docs/codex-connection.md) describes the real host setup.

## Remaining product limits

- Text-only Codex prompts. File browsing, terminal, imported sessions, background
  cross-profile attention and unsupported session mutations are unavailable.
- Reconnect restores tracked history, but the protocol has no pending-approval
  listing operation. Review an approval lost during disconnect on the host.
- Interrupted mutation acknowledgements are uncertain; never automatically
  resend. Explicit user review is required.
- A successful empty thread-list probe does not prove the project folder exists.
- No provider/account access, release, signing, native CI, or publication was
  performed for this checkpoint.

## Next major backlog work

Finish any remaining Codex Android checks first. Then F6-S1a provides Android
launcher actions for Connect and New task, with one-shot cold/warm delivery,
explicit navigation, and no automatic prompt send. F5 still needs per-run
outcome evidence; idle alone is not success. Do not replace these journeys with
a new queue of minor polish tasks.

The ten-worker ownership and accepted/rejected work are recorded in
[the rolling work ledger](docs/backlog/rolling-swarm-2026-09-07.md).
Root owns interaction design, shared contracts, integration checks and Git;
workers implement bounded non-overlapping slices. Reuse that division on the
next machine, with fresh agent instances as needed.

## Transfer contents

Source, tests, generalized planning notes and sanitized verification summaries
belong in Git. Generated APKs, machine credentials, transient logs and original
conversation screenshots remain local. No named competitive research is
needed to resume this work.
