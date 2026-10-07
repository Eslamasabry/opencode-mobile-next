# BA6 — OpenCode 1 agent cards (2026-10-07)

State: implemented; focused checks pending coordinator; device chat/list answers
pending a candidate APK. No new APK built or installed by the BA6 worker.

Scope: existing captured OC1 `oc-ui_show` dialect + owner-approved one Ubuntu
trust zone + live authenticated readiness check. Contract:
[BA6-contract](../../design/BA6-contract.md).

Read-only emulator inspection used the shared lock and only `emulator-5554`.
The installed app reports versionName 1.2.0, and the managed OC1 directory under
`files/linux/ubuntu/root/.oc-genui/openCode1` contains its helper, enabled marker,
lock and owners manifest. Their contents and server credentials were not
printed. This observation does not establish readiness or answer delivery.

Previously captured evidence: the two OC1 tool parts copied byte-for-byte to
`test/fixtures/genui/runtime_opencode1_tool.json`, SHA256
`47eff268d0240e0fc3d96fba94503fe0906b5efe8bf34a5a007143934eaea7e7`.
The source and maintainer-supplied MCP connected/version evidence are described
in [OC1 runtime evidence](../../verification/oc-gaps-2026-10-07.md#evidence-and-what-it-establishes).
The old isolation objection is superseded by the explicit one-zone owner
choice, not by a new security protection.

Focused check command (coordinator owns serialized execution):

```sh
tool/qa/machine_lock.sh test -- ~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/gen_ui_install_test.dart test/agent_tool_adapter_test.dart
```

Regression proof: retain the tests/fixture, temporarily restore the three BA6
production files from HEAD, run these files with `--plain-name BA6`, and restore
the implementation. The readiness, root bridge and live MCP checks must fail
before the fix and pass after restoration. No full suite belongs to this lane.

Remaining device acceptance:

1. Coordinator builds and installs the signed candidate on emulator-5554,
   preserving installed data and certificate; BA6 does not change the server
   configuration without coordinator review.
2. Confirm live `/global/health` version 1.18.32 and `oc-ui` connected through
   the root verifier; retain only booleans/version, never its auth data.
3. Prompt a disposable OC1 chat to call `oc-ui_show` with a top-level confirm
   ask. Show the card, answer in chat, capture a small JPG and tagged answer
   receipt in sanitized logs.
4. Prompt a second disposable chat in a different folder; answer its card from
   the conversation list. Confirm the same receipt and clear pending state.
5. Reopen/restart the app and confirm an unanswered card is recovered without
   duplicate receipt. Record failures plainly and retain the item as blocked.

Lead validation: 71 affected installer/adapter tests passed through the shared
lock. Reverting the three BA6 production files produced three failures (OC1
qualification, root verifier routing, and live readiness); restoring them passed
all four BA6 focused regression cases. No full suite run.
