# BA4 — evidence-backed agent capabilities

Finish line: exact recorded agent/helper versions grant only capabilities whose
individual certification cells pass, respecting any recorded architecture scope.
Non-goal: creating certification evidence, signing in agents, or claiming that
an installed binary is qualified. FQ1 owns the matrix and device qualification.

## Backend API

`lib/domain/agent_tools/agent_certification.dart` exposes:

```dart
AgentCertificationMatrix.bundled
AgentCertificationMatrix.fromJson(Map<String, dynamic> json)
AgentCapabilities capabilitiesFor({
  required AgentDescriptor descriptor,
  required AgentArchitecture? architecture,
  required String? helperVersion,
})
```

`bundled` uses the exact generated snapshot of
`docs/verification/agent-certification-matrix.json`; no filesystem read or
network request occurs in the app. To regenerate after an FQ1 evidence change:

```bash
python3 tool/agents/generate_certification.py
python3 tool/agents/generate_certification.py --check
```

The parity test compares the bundled JSON with the reviewed file verbatim.
Editing the matrix without regeneration fails the check/test. No pubspec asset
registration is needed.

## Accepted evidence

Each `agents` row uses the FQ1 `id` and `cells` shape, with these additional
qualification fields supplied by the certifier:

```json
{
  "id": "claude",
  "agentVersion": "exact catalog recipe pin",
  "helperVersion": "exact observed Paseo helper version",
  "architecture": "arm64",
  "cells": {
    "resume": {"state": "pass", "evidence": "docs/qa/item/README.md#resume"},
    "images": {"state": "pass", "evidence": "docs/qa/item/README.md#photos"}
  }
}
```

The example fields are a shape, not evidence or real version pins. Both
versions must be nonempty exact tokens and equal the selected descriptor's
`recipe.version` and observed connected helper version before any flag is granted.
`architecture` is optional. When absent, protocol-level evidence applies on
every architecture, including when the host architecture has not been observed.
When present, it must be `arm64` or `x64` and match the observed host architecture;
an unknown host architecture cannot satisfy a scoped row. Explicit null, empty,
or invalid architecture values invalidate the row rather than widening its scope.
This follows the coordinator's 2026-10-07 decision: Paseo protocol evidence from
the x64 emulator may qualify arm64 phones unless the certifier limits its scope.
Unknown helper, absent recipe, version changes, unknown agents,
duplicate IDs (even one malformed duplicate), malformed cells and unrecognized
cell names/states return no capabilities. `opencode1` is the sole explicit ID
alias for catalog `opencode`; alias collisions also invalidate the record.
Input is copied into immutable records; later JSON mutation cannot grant flags.

| Matrix cell | `AgentCapabilities` field |
| --- | --- |
| `resume` | `resumeVerified` |
| `models` | `modelList` |
| `permission` | `permissions` |
| `images` | `images` |
| `abort` | `cancel` |

Only exact `state: "pass"` with a nonempty repository document citation grants
its individual flag. A safe citation is a `docs/` path ending in `.md` and
optionally `#anchor` (whitespace before `#` is accepted, as in FQ1's ` #2`).
Absolute paths, URLs, traversal, control characters, queries, encoded paths,
empty anchors and prose substitutes are rejected. FQ1's `.md (forms)`
annotation must be normalized to a document path/anchor before it can grant a
flag. `partial`, `untested`, `blocked`, `off`, `fail`, `n/a` and missing cells
grant nothing; a passing install/version/smoke/cards cell grants none of these
five capabilities. A passing subset remains a subset, not agent-wide
certification. Evidence files remain owned and reviewed by FQ1.
The `images` cell qualifies sending photos to an agent, including photo cards.
Passing `cards` alone does not qualify images; the two cells are independent.

## Current state and frontend behavior

The coordinator's versioned 2026-10-07 matrix qualifies Claude Code `2.1.283`
through an observed connected Paseo `0.9.2` helper for resume, models,
permissions, images and cancel. Architecture is omitted, so these protocol
capabilities also apply to arm64 phones. Other agents, including fx, remain
unverified. A missing or different helper version still grants no capabilities.
The bundled snapshot is regenerated from that reviewed matrix. The images
citation is normalized to the same document and `#5` anchor, removing its prose
annotation; evidence and pass state are unchanged. Do not invent metadata from
the catalog or a release constant to activate other agents.

Connection uses the observed connected helper version and host architecture;
unknown observations are null. When source synchronization supplies a different
helper observation, the pending row refresh re-inspects the host once before it
completes. This second scan does not synchronize sources again; it reads fresh
phone gates and auth, rather than adding capabilities to an old Ready row.
A delayed source retry also refreshes rows when its handshake supplies evidence,
without requiring the person to reopen a screen. Unknown or mismatched versions
still grant nothing. No host release constant substitutes for a live observation.
The certification projection replaces the Claude
identity-based resume override. An unqualified Claude can use the existing
`AgentResumeNotice` UI when `resumeVerified` is false: "Can't reopen old chats"
and "Starts a new chat", with explicit acknowledgement before a fresh chat.
No raw technical error or credential appears in this contract. Missing evidence
is an unverified state, not a user-facing exception. No stored-format migration
or new persisted credential/data exists.

## Focused verification

Root runs `test/agent_certification_test.dart` under the shared test lock, plus
affected connection tests after integration. It covers independent positive
flags, exact agent/helper matching, optional architecture and scoped matching,
independent photos qualification, null metadata, version invalidation,
non-pass and unsafe evidence, malformed/unknown records, duplicates and alias
collisions, immutable inputs, bundled parity, Claude's actual bundled evidence
on arm64/x64, other agents staying unverified and unverified resume copy.
A negative control that returns all-false from `capabilitiesFor`
must fail the independent positive projection test; restoring it must pass.
Reverting optional architecture must fail the cross-CPU regression; disabling
the images projection must fail the photos regression.
