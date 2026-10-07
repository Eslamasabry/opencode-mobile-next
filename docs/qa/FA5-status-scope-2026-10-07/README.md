# Cards status follows the connected OpenCode runtime

Finish line: status names only phone agents plus the connection's own OpenCode
runtime. Non-goal: changing the all-runtime installation/removal request.

Coordinator screenshot reviewed:
`/home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad/e2e/ba5-oc1-agents.png`
It shows fx Phone check needed and the Cards partial line naming generic OpenCode
both as ready and unchecked on OpenCode 1. The installer stages both runtimes;
the controller formerly forwarded its global result unchanged. The fallback
introduced in c4abcd82 also named all tool adapters.

Controller now projects ready/affected to the connected runtime plus phone tool
adapters. An opposite-only partial problem becomes On for agents already ready
here; no readiness is inferred. Generic problems remain. Runtime display names
are OpenCode 1 and OpenCode 2. The installer still receives all managed adapters.

Contract: ../../design/FA5-follow-up-contract.md.

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
