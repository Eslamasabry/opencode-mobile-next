# FC1 — approval mode chip on the composer, gated on a capability (2026-10-07)

Finish line: the approval mode (Asks first / Auto-approve) is a visible chip
on the composer bar for runtimes that ask before they act, and absent for a
runtime that never asks. Non-goal: per-agent permission modes of the
runtime itself (Claude's plan / accept-edits modes).

## State before
The chip already sat on the composer's status line (left of the model
chip), but it was shown in every non-walkthrough conversation, whatever the
runtime: a runtime that never sends a permission request still read
"Asks first".

## Change
- `ServerCapabilities.permissionRequests` (default true; every current
  gateway sends permission requests, so nothing changes for them today).
- `_approvalModeOffered(conn)` in `chat/approval_mode_menu.dart`:
  not the walkthrough and `capabilities.permissionRequests`. It gates the
  composer chip (`chat_composer_region.dart`) and the approvals nudge
  (`nudge_slot.dart`); the `/approvals` command is gated on the capability
  (`chat_commands.dart`). Never the flavor enum.

## Open need (not in this lane)
Per-agent gating on a Paseo host needs the agent backend to narrow
`capabilities.permissionRequests` per runtime from the agent's
`AgentCapabilities.permissions` once that is proven (connection library /
`lib/paseo/`, owned by other leads). Until then every phone agent keeps the
chip, as before.

## Evidence
`tool/capture/fc_chat_2026_10_07_test.dart` (`FC_ONLY=FC1`).
`contact-sheet.jpg`: before on a runtime that never asks (chip shown) ·
after on that runtime (no chip) · after on a runtime that asks (chip shown).
Crops and full screens: `<before|after>-composer-<asks|never-asks>[-crop].jpg`.

## Tests
`test/approval_mode_chip_test.dart`: new "a runtime that never asks before
acting shows no chip" (fails with the gate reverted, passes with it); plus
`chat_empty_start_test`, `e7_session_approvals_layout_test`,
`goldens/chat_approval_chip_golden_test`: 51 passed.
