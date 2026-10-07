# BA4 activation from coordinator evidence

Finish line: the reviewed Claude Code 2.1.283 + connected Paseo 0.9.2 evidence
grants resume, models, permissions, images and cancel on an arm64 host; other
agents remain unverified. Non-goal: creating certification evidence or claiming
arm64 device qualification. The coordinator owns runtime/device proof.

Merge: 3211c56ea01b039890a3caa2e96c3f52651c0193. feat/genui-fe had advanced
from the requested 66a86114 to 2fb96f15e199d51d1fe712161952a165399d8151;
66a86114 is an ancestor. Merge had no conflicts and preserves the BA7 timer fix,
legacy-host auth fix and optional architecture implementation.

Regenerated the bundled JSON with tool/agents/generate_certification.py.
Claude's images pass citation had trailing prose that the safe-citation parser
rejects. Normalized it to docs/qa/agent-tools-2026-10-07/README.md#5, retaining
the evidence document, anchor and pass state. Parser rules stay unchanged.
Architecture is absent, so the evidence applies across CPUs. Agent/helper
versions stay exact; missing or changed helper versions grant no capabilities.
No fx/other-agent proof is inferred from installation or identity.

Direct bundled-evidence test verifies all five flags on arm64 and x64. The
connection test uses an arm64 host and a real Paseo transport with scripted
0.9.2 server_info, then verifies all five flags supplied to host inspection.
Both passed. These are Dart tests, not an arm64 physical-device run.

Regression controls, implementation restored in finally:

- Old bundled snapshot: actual-evidence regression failed resume (false), exit 1.
- Unnormalized images citation: same regression failed only images, exit 1.
- Optional architecture and independent images negative controls are also
  recorded in ../BA4-contract-follow-up-2026-10-07/.

Final focused verification results are recorded below after completion.
All Flutter checks use the pinned 3.47.1 SDK and shared machine lock;
tests use --no-pub --concurrency=1. Manifest, logs and candidate hashes adjacent.
Dependencies refreshed with pinned flutter pub get because the merge changed
pubspec/lockfile. No full repository suite claim, manual UI/Kotlin edits,
native build, device session, signing, push or release.

## Final verification

- All six files in test-manifest.txt completed: 128 tests passed, no failures
  or skips (test-result.txt and test-result.json). This includes the watchdog
  lifecycle tests and original Agents Settings placement tests.
- Analyzer: No issues found (37.2s), analyzer-result.txt.
- Pinned Dart format language-version=3.10: no remaining changes.
- Generator parity, git diff --check and candidate SHA-256 verification passed.
- Current activation is implemented and verified in Dart, committed locally;
  deployed/released device state is unchanged. Coordinator runs the full suite
  and APK/device integration after merging.
