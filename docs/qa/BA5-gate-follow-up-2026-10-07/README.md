# BA5 — preserve every checked agent across owner migration

Finish line: sharing the phone owner preserves saved legacy checks for every
agent. Non-goal: merging histories or account files.

## Read-only device evidence

Coordinator reported APK 2190 (feat/genui-fe 281356fb), emulator-5554. Sanitized
inspection of FlutterSharedPreferences.xml under the shared emulator flock found:

| Profile | Runtime | Saved owner | Saved gate agents |
| --- | --- | --- | --- |
| 1790839392073695 | OpenCode 1, in-app Ubuntu | 1790839392073695 | claude |
| 1791381364862609 | OpenCode 2, in-app Ubuntu | 1790839392073695 | fx |

Both gates retain exact fingerprints: Claude 2.1.283 or fx 0.0.12, x64 artifact,
Paseo 0.9.2, package lock 82d16f9c…, Node v24.21.0. No secret preferences or account
file contents were output or saved. fx's gate was not deleted: redirecting its
profile to the Claude owner made its intact legacy gate invisible to inspection.
fa032af2 improves choosing a populated owner, but does not union its other checks.

Read-only home inventory (files/linux/ubuntu/home/oc): selected owner has claude,
claude.lock and paseo; legacy fx owner has paseo. The global .local/bin/fx symlink points to
.local/share/oc-agents/fx/0.0.12/launch. A further file inventory was cancelled
while waiting for the shared emulator lock; no agent processes were interrupted.
The home identity changed; the Ubuntu installation did not.

## Backend fix

Adopt missing legacy gates once and retain a per-owner donor receipt. Existing
owner entries win. Fingerprint/ABI qualification remains in BuiltinPhoneAgents.
Failed checks clear proof permanently across reload; no account files are moved.
Receipt retention and last-alias deletion match gate retention/deletion.

Contract: ../../design/BA5-contract.md.

No replacement APK, installation, signing, release or full-suite run is claimed.


## Focused validation

Pinned Dart format with `--language-version=3.10`: passed. `git diff --check`:
passed. Shared machine lock used for every Flutter invocation.

Final restored candidate: 92 tests passed in five files via:

```sh
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 \
  test/phone_agent_owner_test.dart test/phone_agents_host_test.dart \
  test/gen_ui_controller_test.dart test/agent_tool_adapter_test.dart \
  test/agent_card_view_test.dart
```

The affected six-file development run passed 153 tests, including all 62 installer
tests in `test/gen_ui_install_test.dart`, and failed one widget expectation using
"haven't" where the unchanged localization says "hasn't". The fixture wording
was corrected; all five changed test files passed on the restored final candidate.
The installer file and implementation were unchanged afterward. This is focused
coverage, not a full-suite gate.

`negative-control.txt` records failures with the implementation reverted to
c9ea838e, new tests retained, and implementation restored in a finally block.
`candidate.sha256` identifies the eight source/test files in the restored run.


Integration checkpoint: `tool/qa/machine_lock.sh analyze -- <pinned-flutter>
analyze --no-pub` passed with No issues found (154.4 s). No new ignores.
Coordinator must recheck fx and the Cards line in the next APK; this worktree did
not install or modify emulator preferences or account files.
