# BD12 Claude picker — 2026-10-09

Base: `feat/genui-fe` at `17decdca4389198c007b768121aea2a8de3c722c`.
Branch: `sol/bd-claude-picker`; checkout: `/home/eslam/Storage/Code/oc_app-sol-bd`.

The picker projects its shared catalog through `catalogForSession(sessionID)`.
Paseo conversations retain their provider, including empty drafts; unknown
provider identity exposes no choices. The complete catalog remains available
for new conversations and other providers. OpenCode continues allowing model
provider changes. `agentSelection` suppresses the misleading Agent control,
including its direct-entry menu and apply wording, for Paseo. No layout added.

Model `thinkingOptions` retain their wire IDs and display labels in catalog
variants. A draft keeps the selection until creation. Existing conversations
send `set_agent_thinking_request` after model changes, before a follow-up prompt.
An empty variant resets via `thinkingOptionId: null`. Session snapshots retain
`thinkingOptionId`, so refreshes can restore the displayed level. Rejected
thinking changes prevent sending the next prompt and use existing fixed errors.

## Protocol evidence

`protocol-evidence.json` records versions and SHA-256s from the local package
installed from `assets/agents/paseo-package-lock.json` (`@getpaseo/*` 0.9.2).
The provider catalog's `snapshotPayload` only compacts thinking sets when the
client advertises compact-provider support. This client does not advertise it;
models therefore carry plain `thinkingOptions`. No guessed thinking levels or
OpenCode provider model IDs are added to a Claude conversation.

Relevant shipped source:

- `server/.../agent/providers/claude/model-manifest.js`: model-specific effort
  levels, defaults, and `off` / `Off` plus `ultracode` / `Ultra Code` where supported.
- `protocol/dist/messages.js`: `SetAgentThinkingRequestMessageSchema` requires
  `agentId`, nullable `thinkingOptionId`, and `requestId`.
- `server/.../session/agent-config/agent-config-session.js`: model-before-thinking
  ordering; `set_agent_thinking_response` reports accepted/error.
- `protocol/dist/provider-snapshot-codec.js`: the alternate compact format is
  capability-negotiated; not requested by this client.

## Verification

All Flutter tests use the pinned SDK and exactly:

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- \
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency 1 <files>
```

`red.txt`: seven new tests fail against the unchanged base, covering catalog
variants, restored thinking, live changes, default reset, cross-provider draft
rejection, draft selection, and the misleading Agent chip. No compile failure.
Final verification results are recorded below after the complete focused run.

Contract: [BD12-contract.md](../../design/BD12-contract.md).
No Gradle/APK/emulator/full-suite run, signing, push, or release. Widget tests
cover existing controls; no device screenshot or real Claude account proof.
No credential handling or stored-format migration is introduced.

Final frozen-source run: **148 passed, zero failures/skips** in `green.txt`.

- Seven files: `paseo_model_catalog_test.dart`, `model_picker_test.dart`,
  `paseo_gateway_test.dart`, `paseo_browser_launch_test.dart`,
  `paseo_correlated_prompt_test.dart`, `session_selection_sync_test.dart`, and
  `kit_ratchet_test.dart`.
- Additional failing-first evidence: `unresolved-red.txt` proves a missing
  session must not inherit the global provider; `location-red.txt` proves the
  follow-up mutation must stop after a scope change; `default-red.txt` proves
  accepting the advertised effective default is needed to close the picker.
- `source-sha256.json` pins every changed Dart source/test in the final run.

The initial red fixture used an arbitrary `disabled` option to prove verbatim
mapping; final fixtures use shipped IDs (`off`, `ultracode`) and their labels.
Paseo may report its effective default level after accepting a null reset. The
picker accepts that advertised default and then shows the returned level.

Pinned full `flutter analyze`: **No issues found**, exit 0 (`analyze.txt`).
Three initial style findings (two brace blocks and a redundant import) were
fixed before the final 148-test run and final analyzer pass. Commit-message,
Dart-format and whitespace checks pass. Test logs have trailing whitespace
normalized for the repository diff check.
