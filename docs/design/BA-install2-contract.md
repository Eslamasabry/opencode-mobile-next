# Install-cert2 app paths

2026-10-08, backend branch `sol/ba-install-cert2` from `feat/genui-fe f0e33d96d`.
Finish line: six account-free installation assessments use actual app paths for
launch/rejection, payload removal and safely injected storage admission.
Non-goal: signing in, logging out Claude, fabricating readiness or filling disk.

## Payload removal for Claude's frontend

The domain source can optionally implement `PhoneAgentRemovalSource`:

- `canRemoveAgent(String agentId) -> bool`: true for installed supported targets
  on a host with the removal port. Claude is excluded.
- `removingAgentId -> String?`: selected target while removal runs; null otherwise.
- `removeAgent(String agentId) -> Future<void>`: await completion and freshly
  inspected rows. Never call sign-out or clear profile/conversation data.

Supported IDs: `codex`, `gemini`, `qwen`, `goose`, `omp-acp`, `fx`.
Expose an installed agent's **Remove <name>** action and a confirmation:
“Remove <name> from this phone? Your account and conversations stay saved.”
Confirmation action: **Remove <name>**. While running: **Removing <name>…**.
After completion the row becomes **Not installed** with **Install** available.
Disable repeated removal/new phone conversations while `removingAgentId != null`.

Plain failures:

- Busy: “This agent is still in use. Finish its setup or conversation and try again.”
- Unsupported older host: “Removal is not available on this phone yet. Update the app and try again.”
- Failure/unsafe layout: “The agent could not be removed. Refresh Agents and try again.”

The optional `PhoneAgentRemovalPort` keeps older fake/host implementations valid.
The host executes only catalog-authored removal through the existing fixed oc
setup bridge. It excludes local preparation, any nonterminal/unreadable native
job, live target processes and unsafe paths. The installer lock is shared;
there are no process signals, shared-helper shutdowns or raw error strings.
Actual create/resume/prompt requests share host admission with removal, including
existing drafts; in-flight requests prevent deletion. Unrelated Claude auth
probes remain available while another target is removed.
Accounts, gate metadata and chats are retained; fresh inspection controls rows.
Current-version launcher validation is conservative: older or unrelated links
are refused. Child payload symlinks are unlinked without following their targets.

UI wiring is coordinator-owned and absent at branch base. A direct adb delete or
running the script outside the app cannot qualify the app-removal cell.

## Injected storage admission

Normal builds use `OC_QA_AGENT_INSTALL_MIN_FREE_BYTES=0` (default). An explicit
QA candidate may use:

```
--dart-define=OC_QA_AGENT_INSTALL_MIN_FREE_BYTES=8589934592
```

This only raises the six target components' metadata, leaving catalog pins,
shared dependencies and actual `File.usableSpace` unchanged. Existing production
native admission checks the raised target before its installation script. No
download or disk filling is required. The native setup params contain only an
`agentInstallGuard` declaration `{agentId, minimumFreeBytes}`; they do not expose
provider data. Setup snapshots do not record component `requiredFreeBytes`.
Offline host tests verify the dispatched native spec; device evidence must
separately label the persisted declaration and the observed native refusal.

`AgentSetupProgress.failure == AgentHostFailure.storage` exposes only the known
native storage failure. Show: “There is not enough free space on this phone.
Free some storage and try again.” Provide **Storage settings** / **Try again**
using the existing frontend storage action where available. Unknown error text
is never exposed. Generic failure alone cannot pass visible storage guidance.

Use a flagged candidate under the shared emulator lock, require healthy real
free space and an absent target, tap the actual Install action, require a fresh
failed job/known storage code/zero bytes/no target mutation, restore the normal
APK with its exact signer, then retry through the app. Normal2197 must not carry
the QA floor; the flagged candidate requires a distinct reviewed build receipt.

## App launch boundary

Stock app launch is New conversation → agent picker → readiness/certification
checks → `startAgentChatIn` → nonempty prompt → Paseo `create_agent_request`.
An empty draft does not launch a daemon agent. Signed-out/unknown-auth targets
cannot reach that request; picker admission additionally requires smoke proof.
The offline driver can observe bounded app-side rejection and a way forward,
without tapping sign-in or any Claude action. This is explicitly not daemon
launch evidence. An explicit QA launch entrypoint or real auth/smoke proof is
needed to close full daemon-launch qualification; do not alter saved gates or
pretend an unknown account is signed in.

No new runtime capability is granted by these contracts. APK2197 and any new
entrypoint/build receipt will be supplied by the coordinator before device work.
