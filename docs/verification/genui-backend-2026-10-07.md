# Agent cards backend verification — 2026-10-07

Candidate base: `30860c39` (coordinator-frozen API additions). Implementation is
uncommitted in `feat/genui-be`; the coordinator owns commits. The candidate hash
below covers sorted changed/untracked Dart files and `test/fixtures/genui/*`,
concatenating each path, NUL, contents, NUL:

`6d8ae573a557bf5756ca1b292f0b549b6e76ccf8edfd086662b5a1c53d736b0d`

## Implemented

- Frozen public API, strict Dart/JavaScript validator parity, card revision and
  trusted scope/call identity, authoritative transcript state, v1 answer envelopes.
- Bounded history and fresh idle admission for OC1, OC2 and Paseo. Paseo reads use
  private connections with a fresh qualified-version handshake. Recovery has
  20-session/2-page/50-item/1-MiB limits, serial requests, 10-second session and
  60-second pass budgets. Budget-excluded coverage stays unknown on later passes.
- Shared controller state, per-profile bounded metadata journals, three-second
  Undo, duplicate-send protection and durable uncertain-delivery markers. No card
  content, answer values or image bytes enter the journal. No automatic resend.
- Final pre-dispatch fences, including Paseo after asynchronous preparation;
  profile/session deletion and disconnect cannot restore stale state through a
  late response. Canonical phone-feed gateways own routing, including when the
  in-app Ubuntu is a side connection beside a remote main server.
- Needs-you feed projection, scoped card/receipt lookup, effective default-false
  capability and desired-setting/setup-status separation.
- Dart-embedded stdio MCP helper, profile-scoped Claude registration, shared
  ownership-aware OpenCode registration, collision refusal and safe removal.
  Setup checks do not print configuration contents or launch unrelated MCPs.

## Runtime qualification

See [the emulator capture record](genui-runtime-2026-10-07.md). Only
`emulator-5554` was used. Claude Code 2.1.283 / Paseo 0.9.2 / Node v24.21.0
passed real MCP execution and message-answer receipt capture. The exact tool row
is exercised through the real mapper in a focused test. Readiness also requires
profile configuration/helper verification and a currently connected qualified
Paseo daemon; remembered version metadata cannot qualify a reconnect.

OpenCode 1 remains unavailable: this emulator lacks the installer's root-safe
`/usr/bin/node` prerequisite. OpenCode 2 is absent and remains unavailable. Their
bounded readers and registration logic have focused synthetic coverage, not live
qualification. No native changes, assets, dependency changes, APK builds, daemon
MCP injection, commits, pushes, tags or releases were performed.

## Validation

Final result: **172 tests passed across 15 focused files**, no skips/failures.
Full repository `flutter analyze --no-pub` passed with **no issues**.
`git diff --check` passed. All changed Dart files remain under 1,500 lines.
Pinned SDK:
`~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Formatting uses its bundled Dart with `--language-version=3.10`. Heavy commands
use `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh` and tests use `--concurrency=1`.

Each file ran separately with the following command under the serial machine lock:

```text
flutter test --no-pub --concurrency=1 <file>
```

| File | Passed |
|---|---:|
| `test/gen_ui_types_test.dart` | 1 |
| `test/gen_ui_codec_test.dart` | 11 |
| `test/gen_ui_server_test.dart` | 8 |
| `test/gen_ui_install_test.dart` | 15 |
| `test/gen_ui_history_test.dart` | 13 |
| `test/gen_ui_state_test.dart` | 16 |
| `test/gen_ui_controller_test.dart` | 10 |
| `test/gen_ui_runtime_fixture_test.dart` | 2 |
| `test/paseo_correlated_prompt_test.dart` | 4 |
| `test/paseo_gateway_test.dart` | 37 |
| `test/paseo_chat_feed_source_test.dart` | 15 |
| `test/chat_feed_test.dart` | 8 |
| `test/profile_deletion_test.dart` | 17 |
| `test/side_connections_test.dart` | 12 |
| `test/paseo_images_test.dart` | 3 |

The codec/helper corpus additionally covers 69 shared validation fixtures.
The existing full repository suite was not run.

## Limits

This is the backend half of S1. No renderer/UI files were changed. The new Flutter
controller/renderer has not run in an APK on the emulator, and no physical phone
was accessed. A coordinator-built compatible APK is needed for the combined UI,
app-process restart and photo round-trip device gate. The serial full repository
suite and released Shorebird baseline comparison are separate integration/release
gates; focused checks do not claim those gates. Existing native script entry
points are reused, but release patchability still depends on the chosen baseline.
