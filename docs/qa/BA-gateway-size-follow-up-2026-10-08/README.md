# Paseo gateway file-size follow-up

Finish line: gateway.dart and its parts meet the unchanged 1500-line gate while
Paseo command discovery and browser launch behavior remain unchanged.
Non-goal: changing protocol behavior, capability proof, UI or the size baseline.

Base: feat/genui-fe 94d0dbd4422256db00f5de27d62a1d5d9dabb8bb.
Branch: sol/ba-fixes, created from that coordinator candidate after merging it
into the prior sol/ba-agent-cert branch (fast-forward).

Verification results follow after the final focused checks.

Moved folder command discovery and the shared command response parser into
lib/paseo/gateway/commands.dart using the existing private extension pattern.
PaseoGateway retains its public listCommands method and interface signature;
it delegates to the unchanged async implementation. Scoped browser command
paths use the same parser. Implementation bodies match the base byte-for-byte,
except for the private helper name (move-equivalence.txt).

Gateway shrank from 1517 to 1461 lines; new part has 62 lines. The original
file-size test, its limit/baseline and all test snapshots remain unchanged.
No frontend contract change or persisted-format migration is required.

Pinned Flutter 3.47.1, machine-locked, no-pub, concurrency=1:

- Before-change ratchet: failed at gateway.dart's 1517 lines (exit 1).
- Final three-file run: 54 passed, no failures/skips (test-result.txt).
  Includes the named file-size ratchet, Paseo gateway behavior/command parsing,
  and browser reservation ordering, shared command suppression and revocation.
- Reverted gateway to the base while retaining the part: ratchet failed again
  at 1517 lines (exit 1, negative-control.txt). Implementation restored in
  finally; restored ratchet passed both tests (restored-ratchet-result.txt).
- Pinned Dart format language-version=3.10 and git diff --check passed.

Focused checks only; no full repository suite/native build/device/signing/
push/release. Analyzer: No issues found (19.0s), analyzer-result.txt.
Candidate source hashes verified after all checks.
