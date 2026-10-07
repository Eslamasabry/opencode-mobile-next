# BA4 optional architecture and images follow-up

Base: 5650d5a6 on sol/ba-agent-cert. Coordinator decision: 2026-10-07.
Contract: [BA4-contract.md](../../design/BA4-contract.md).

Implemented: agentVersion and helperVersion remain required exact matches.
An absent architecture field makes protocol evidence apply across CPUs,
including an unobserved CPU. Present arm64/x64 must match the observed CPU;
present null, empty, invalid or unknown values invalidate the record.

Images already had an independent pass-cell projection; explicit photos tests
now prove cards alone cannot enable photos, and partial/fail/off/untested or
missing evidence cannot enable images. No evidence or runtime versions invented.
The coordinator owns the pending matrix rows and merge instruction. The current
bundled matrix still lacks exact agent/helper versions and grants zero flags.

Pinned Flutter 3.47.1, shared machine lock, no-pub, concurrency=1:

- agent_certification_test.dart: 11 passed (test-result.txt).
- phone_agents_test.dart: 13 passed (phone-agent-test-result.txt).
- Reverting the projection to HEAD's strict architecture rule, with new tests
  retained, failed the cross-CPU test with all flags false (exit 1).
- Disabling only images failed the photos test's passing images assertion
  (exit 1). Both negative controls restored the implementation in finally.
- The initial photos fixture inferred a map that rejected null evidence;
  widening the fixture value type corrected it before the final passing run.
- flutter analyze --no-pub: No issues found (21.0s), analyzer-result.txt.
- Pinned dart format --language-version=3.10, git diff --check, and
  python3 tool/agents/generate_certification.py --check passed.

Negative-control logs and candidate hashes are adjacent. Focused checks only;
no full suite, native build, device install, UI/Kotlin edit or matrix merge.
