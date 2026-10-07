# Cards status for the connected runtime

Date: 2026-10-07. Backend: `ConnectionController.genUiStatus`.

Finish line: the Cards status names phone agents and the connected OpenCode
runtime, and never names the other OpenCode runtime. Installation registration
scope and qualification are unchanged.

The installer continues to stage and remove every managed adapter. Its result
is retained intact; the controller projects that result when the status is read.
The connected profile takes precedence over the saved profile, so switching
OpenCode versions updates the displayed status without another installation.

Only phone adapters that support cards registration and the connected runtime's
adapter can appear in `agents` or `affected`. A standalone phone backend has
only phone adapters; its usual parent delegation retains the parent's status.
Adapters use the product names `OpenCode 1` and `OpenCode 2`, so frontend copy can
continue to use `displayName` without confusing the two runtimes.

Before an installation result exists, `GenUiSetupUnavailable(notQualified)`
names those applicable adapters in `affected`. It does not mark any ready.
For example, an OpenCode 2 connection names Claude Code and OpenCode 2, with
the existing localized wording that they have not been checked for cards yet.

For an installed result:

- Ready and affected agent lists are filtered to the connection.
- A partial result whose particular problem affects only the other runtime
  becomes On for the applicable agents already reported ready. Thus ready
  Claude Code and OpenCode 1 with unchecked OpenCode 2 appears as
  `On for Claude Code, OpenCode 1.` on the OpenCode 1 connection. Switching to
  OpenCode 2 retains Claude Code readiness and the OpenCode 2 unchecked problem.
- If filtering removes every ready agent, On and opposite-runtime-only partial
  results become unavailable, with no inferred readiness.
- Generic partial problems retain their reason. Failed, Off, Installing and
  RestartRequired retain their state; their agent lists are still filtered.
- An unavailable result concerning only the other runtime becomes an unchecked
  result naming applicable agents. No live availability is inferred.

The frontend uses the existing status renderer and localization; no UI changes
are required. This projection never changes adapter qualification or installs
another runtime. Applicable failures retain their existing plain words and
retry path; no technical error content is introduced.

Focused regression coverage is in `test/gen_ui_controller_test.dart` and
`test/agent_tool_adapter_test.dart`. Device integration remains the coordinator's
responsibility.
