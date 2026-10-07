# Agent cards runtime qualification — 2026-10-07

Scope: dev emulator **emulator-5554** only, package
`io.github.eslamasabry.opencode_mobile`, existing in-app Ubuntu. No physical-phone
command, APK build/copy, dependency install, daemon restart, commit, push or release.
The finish line was a real completed MCP input plus an idle-only message answer
receipt, and finite Paseo history reads. App UI/device-restart qualification was
not part of this probe.

## Result

**Paseo 0.9.2 + Claude Code 2.1.283: transport/helper path qualified.** The actual
Dart-embedded `genUiServerScript` was exported to a temporary CommonJS file and
loaded through Claude's user-scope MCP registration in the daemon's exact profile
HOME/CLAUDE_CONFIG_DIR. A newly created, dedicated Paseo agent exposed and executed
`mcp__oc-ui__show`; bounded history returned its completed structured input. Once
that agent's authoritative status was `idle`, a normal message containing the
v1 answer envelope was sent, and subsequent bounded history contained the matching
user receipt. The probe agent was deleted afterward and the temporary owned
registration removed. This is not proof the new Flutter controller/renderer has
run on a device, nor a claim all older already-running Claude sessions refresh
MCP catalogs in place.

**OpenCode 1.18.32: unavailable for this feature under the current installer
prerequisite.** Its executable is installed, but `/usr/bin/node` is absent. The only
observed Node is the agent-side `/home/oc/.local/node/bin/node` (v24.21.0). No
root-safe helper invocation or OC1 `oc-ui_show` call was qualified. A fake-root
`setpriv --reuid 1000 --regid 1000 --clear-groups id` printed UID/GID 1000, but that
alone is not evidence of kernel-enforced isolation and must not enable OC1.

**OpenCode 2: unavailable.** `/usr/local/bin/opencode2` is not installed in this
emulator. No persistent `codemode:false` configuration or direct OC2 name was
qualified. No new runtime was installed.

## Captures and admission facts

- [Actual Paseo/Claude tool row](../../test/fixtures/genui/runtime_paseo_claude_tool.json).
  `item.type = tool_call`, `item.name = mcp__oc-ui__show`, `item.status = completed`,
  `item.detail.type = unknown`, complete input in `item.detail.input`, and
  `item.error = null`. The helper result is nested as `detail.output.output` and
  states acceptance for display, never confirmed delivery. Only the call ID was
  replaced; timestamps/turn identifiers were omitted. The fixed probe input and
  helper output were retained verbatim.
- The normalized mapper already consumes this shape:
  `lib/paseo/mappers.dart:161` (exact lowercase name), `:181` (unknown-detail input),
  `:221` (call ID identity), `:290` (tool Part and ToolState).
- The dedicated probe first observed `running` tool/agent status, then `completed`
  tool while agent still `running`, then `completed` tool with agent `idle`.
  Its tool permission was approved only for the exact created probe agent and
  exact `mcp__oc-ui__show` tool. No other permission was approved.
- The sent text was `[oc-ui answer runtime-probe] Confirmed`, LF, compact JSON
  with exactly `v:1`, `cardId:runtime-probe`, that actual call ID, and
  `value:{confirm:true}`. A later authoritative `user_message` contained the
  correlated answer. The response alone was not used as a receipt. No photo
  attachment claim was made.
- Direct Claude CLI additionally passed an isolated `--mcp-config`,
  `--strict-mcp-config`, `--allowedTools mcp__oc-ui__show` probe using the same
  helper: init catalog contained the exact name, MCP status connected, complete
  tool input, successful tool result and successful terminal result.

## Finite history and idle status

[Sanitized paging observations](../../test/fixtures/genui/runtime_paseo_paging.json)
record two real existing sessions over a separate, authenticated read-only
WebSocket. Each `fetch_agent_timeline_request` with `direction:tail, limit:1,
projection:projected` returned exactly one row, `hasOlder:true`, `hasNewer:false`,
`reset:false`, `gap:false`, `staleCursor:false`. `startCursor` had `epoch` and `seq`.
A second request with `direction:before`, that cursor and `limit:1` returned one
older row, `hasOlder:true`, `hasNewer:true`, the same epoch, and
`endCursor.seq < previous startCursor.seq`; all error/reset/gap flags remained clear.
One preceding row was a real tool call, demonstrating why last-message-only
recovery misses tools. The client used a 1 MiB WebSocket payload limit. No claim is
made here about a deliberately oversized server response or exhaustive recovery.

Installed package evidence, read from the emulator (not a guessed remote version):

- `@getpaseo/protocol/dist/messages.js:1513`: finite nonnegative `limit`, direction
  tail/before/after, `{epoch,seq}` cursor; **zero means unlimited**.
- Same file `:3981`: response has epoch, reset/staleCursor/gap, window,
  startCursor/endCursor, hasOlder/hasNewer, entries and nullable error.
- `@getpaseo/server/dist/server/server/session.js:5755`: passes requested finite
  limit to `agentManager.fetchTimeline`; uses projected rows.
- `@getpaseo/protocol/dist/agent-lifecycle.js:1`: statuses are exactly
  initializing, idle, running, error, closed. Only raw `idle` is acceptable for a
  card send. The existing general UI mapper at `lib/paseo/mappers.dart:154`
  collapses other statuses to idle and is not suitable for this admission check.

Package root in this installation:
`/home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d/node_modules/`.

## Registration, process ownership and cleanup

A fresh isolated HOME/CLAUDE_CONFIG_DIR proved the pinned CLI command:

```text
claude mcp add --scope user --transport stdio oc-ui -- <absolute-node> <helper>
```

writes `<CLAUDE_CONFIG_DIR>/.claude.json` with only this relevant entry shape:
`mcpServers.oc-ui = {type:"stdio", command:<node>, args:[<helper>], env:{}}`.
A separate pinned `claude mcp list` process returned exit 0 and the own entry's
line ended in `- ✔ Connected`. No unrelated configuration values were emitted.
The actual per-profile probe refused an existing `oc-ui` collision. Cleanup reread
the latest config and removed only the still-exact matching owned entry, retaining
other fields and entries. It did not restart the daemon or pre-existing agents.

The app is non-debuggable, so `run-as` was refused. Emulator `su` permitted safe
inspection. Initial probe files were inadvertently created as real UID 0 while
the live daemon uses Android app UID 10217: a mode-700 temporary directory was
inaccessible to it, and the first dedicated agent failed before any tool call.
That failed agent was deleted and its temporary registration removed. The probe
was corrected to run under **real Android app UID 10217** with the existing proot
launcher; temporary files and the own rewritten config had app ownership restored.
The corrected probe then passed. Guest `--root-id` output is not a substitute for
checking real Android ownership. Probe-only Claude cache/project directories and
temporary helper/config files were removed afterward. Existing app/provider data
was not deleted. Existing services were not killed by pattern or restarted.

The first direct helper probe also supplied a marker
with incorrect contents and received an MCP error. Replacing it with exactly
`enabled` plus LF yielded success; this agrees with
`lib/builtin/agents/gen_ui_server.dart:109`. Neither a process launch nor a visible
catalog entry alone proves tool execution is enabled.

## Remaining limits

No actual Flutter app restart, rendered card, controller Undo/race/deletion path,
photo round trip, or Shorebird release-baseline comparison was executed here.
Those require their separate focused/integration evidence. Runtime installation
was temporary and is now removed: this document does not mean Agent cards is
currently enabled on the emulator. No Flutter test process was launched during runtime capture. A subsequent
focused `test/gen_ui_runtime_fixture_test.dart` run passed both tests through
the real Paseo mapper and card parser.
