# BA4 — certification evidence data shape (dependency FQ1)

The required `docs/verification/agent-certification-matrix.md` is absent from
this candidate. BA4 is skipped until FQ1 supplies recorded evidence. Existing
capability wiring has not been replaced with invented qualifications.

Proposed frontend/coordinator input per catalog agent:

```json
{
  "agentId": "claude",
  "agentVersion": "exact catalog pin",
  "helperVersion": "0.9.2",
  "architecture": "arm64",
  "resume": {"verified": false, "evidence": null},
  "models": {"verified": false, "evidence": null},
  "permissions": {"verified": false, "evidence": null},
  "images": {"verified": false, "evidence": null},
  "cancel": {"verified": false, "evidence": null}
}
```

An evidence value is a repository-relative QA README anchor with candidate
revision, device, date and successful observed behavior. Null/unchecked/blocked
means false. A signed-in account or installed binary establishes no capability.
Records must match agent pin, helper version and architecture before projection
into existing `AgentCapabilities(resumeVerified:, modelList:, permissions:,
images:, cancel:)`. Version changes invalidate the record; unknown agents have
all flags false. FQ1 owns qualification; BA owns projection after the matrix
exists. No account identity, auth token or provider error belongs in this shape.

The eventual `_paHostCapabilities` will read this record by descriptor ID and
pinned runtime instead of the current Claude-specific resume proof. Old chat
rows continue to use existing `AgentResumeNotice`: unverified resume offers
"Can't reopen old chats" / "Starts a new chat" with explicit acknowledgement.
