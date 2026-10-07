# BA4 certification projection

Base after FA5: c4abcd82, source matrix merged at 7f7293f9.
Finish line and frontend API: docs/design/BA4-contract.md.

Implemented: bundled verbatim FQ1 matrix, parity/generation check, exact agent
pin + observed connected helper + host architecture matching, independent
pass-cell capability projection. Removed the Claude identity-based resume
proof. No UI, native, persisted data, or credential changes.

Current qualification: FQ1 rows omit agentVersion/helperVersion/architecture.
All bundled flags remain false until FQ1 records matching metadata. Images has
no matrix column/cell yet and remains false. No certification evidence invented.

Pinned Flutter 3.47.1, shared test lock, --no-pub --concurrency=1:
- Controller new BA4 regression passed; restoring old Claude override failed
  (resumeVerified true instead of false).
- Restored agent_certification_test.dart, phone_agents_controller_test.dart,
  phone_agents_test.dart: 93 passed.
- Projection negative control (all-false stub) failed independent positive-cell
  test; restored agent_certification_test.dart: 8 passed.
- python3 tool/agents/generate_certification.py --check: exact parity passed.

Full suite/device qualification belongs to the coordinator/FQ lane. Final
analyzer checkpoint is recorded in the BA9/round-2 handoff.
