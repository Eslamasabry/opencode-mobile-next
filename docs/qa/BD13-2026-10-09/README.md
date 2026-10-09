# BD13 — Claude helper disconnects, 2026-10-09

Finish line: explain the process budget from the shipped implementation and existing
measurements, preserve a helper's observed exit safely, and give the frontend a
named diagnostic/notice contract. Non-goals: reproduce the owner's incident,
change Android settings, alter recovery policy, builds, emulator or live accounts.
Base: `feat/genui-fe` at `c3444414787b16f536c2b63de01e97add285db47`.

## Finding

Android phantom-process trimming is plausible and previously demonstrated in this
app, but **the owner's Claude incident is not diagnosed**. There is no process
sample, ActivityManager kill record or native exit snapshot from that turn here.
An exit code alone cannot establish low memory versus phantom trimming versus
another SIGKILL. An ordinary transport failure also does not prove any process died.

The shipped Paseo 0.9.2 has **four persistent Node processes**, not one: CLI,
supervisor, daemon worker, terminal worker. With its proot process and one native
Claude Code process, a fresh active conversation therefore has a **source-derived
minimum of six processes outside Flutter**, before tools, MCP, other conversations,
restored terminals, OpenCode/AI Team, or other apps. This is a lower bound, not an
observed peak and not a claim of one extra OS process per sub-agent.

## Process budget and source evidence

[shipped-source.txt](shipped-source.txt) records package versions, file hashes and
short source excerpts from the existing local package cache (no downloads):
`@getpaseo/{cli,server}` 0.9.2, Claude Agent SDK 0.3.246. The app pins native
Claude Code 2.1.283 in [claude_scripts.dart](../../../lib/builtin/setup/claude_scripts.dart)
and Node 24.21.0 in [paseo_scripts.dart](../../../lib/builtin/agents/paseo_scripts.dart).

| Process component | Fresh conversation lower bound | Evidence / variability |
| --- | ---: | --- |
| proot | 1 | `BuiltinLinux.prootCommand`, `--kill-on-exit`; guest uid is not a separate Android app budget |
| foreground `paseo daemon run` CLI (Node) | 1 | Native host shell uses `exec`; CLI awaits `startDaemonInstance` exit, so remains alive |
| supervisor (Node) | 1 | `daemon-instance.js` spawns supervisor entrypoint using `process.execPath` |
| daemon worker (Node) | 1 | supervisor spawns/forks worker, `restartOnCrash: true` |
| terminal worker (Node) | 1 | bootstrap unconditionally calls factory, which immediately forks the worker even without a terminal |
| Claude Code | 1 per retained SDK query | `claude/query.js` calls `spawnProcess(..., shell: false)`; provider supplies native `pathToClaudeCodeExecutable` |
| MCP children | variable | Each stdio MCP can be its own process/tree; user, project, local settings are loaded. HTTP MCP is not inherently another Claude child |
| shells and their commands | variable | Concurrent Bash tools/background tasks, pipelines, git/tool subprocesses; shell `exec` can replace rather than add a process |
| extra conversations / sub-agents | variable | Separate retained Claude queries add CLI processes. Paseo observes Claude `local_agent` task events, not a separate Paseo launch per sub-agent. Native Claude scheduling is not visible in this JS source |
| other phone workloads | additive | OpenCode, AI Team, terminals, sign-in/setup probes and other apps share system pressure |

A useful accounting equation for a fresh single-query helper is
`5 (proot + four Node) + 1 Claude + extra Claude processes + MCP trees + tool trees + restored/open terminal trees`.
Do not substitute a count of sub-agent cards for `extra Claude processes`: the
provider's Task/sidechain records describe logical work, not OS process identity.
The native Claude executable is opaque in these sources. Thus the exact peak for
the owner's sub-agent turn cannot be answered offline; six is the defensible lower
bound, with potentially many concurrent children above it. No new process monitor
or polling subprocess is added by this change.

Existing measurements are consistent with higher counts:

- [A14 AI Team evidence](../aiteam-builtin-2026-09-24/README.md#android-14-emulator-5554-the-phantom-process-kill-before-tuning):
  median 23, peak 40; Android explicitly logged `Trimming phantom processes` while
  killing OpenCode and proot. Tuning reduced median to 15 and peak to 33. This is
  AI Team evidence, **not a measured Claude sub-agent turn**.
- [P1.6a](../gate-P1.6a-2026-09-28/README.md): signed-out Claude/Paseo initialization
  measured peaks 10–11 and an idle snapshot of seven children, about 469 MiB summed
  PSS plus 19 MiB SwapPss, plus Flutter. Authentication, tool use and concurrent
  workloads were not qualified. Four-second sampling may miss short bursts.
  The recorded nominal 32 limit is shared, not 32 reserved for Claude or this app.
- [BB1](../BB1-2026-10-07/README.md): exit 137 classification was tested using a
  synthetic service; it is not proof that Android killed that service for memory.

## What was already recorded, and what the person could see

`PhoneAgentHost` registers `agent-host.<profile>` through
`BuiltinLinux.trackPrivateAgentService`. Its watcher already recorded the **outer
Process** exit code, uptime and restart count, synchronously in private
`builtin_service_diagnostics` preferences. `performance.services` returns these
facts, retained after service removal and app restart; deleting the agent profile
clears its service records. Output is drained/discarded, not copied into diagnostics.

Missing: an observed exit time, deliberate-stop distinction, and a helper-specific
Recent exits entry. `AppLifecycle` filters `ApplicationExitInfo` to the app's main
process; it cannot explain a guest child dying while Flutter survives. Existing
chat liveness probes produce a generic helper-stopped message, and transport loss
has a generic reconnect notice. Automatic helper recovery starts after two offline
10-second checks; the 45-second silence probe can miss the stopped interval.
Neither route identifies phantom trimming. `chat_transcript.dart` uses localized
kind-based copy rather than the diagnosis's richer `message`.

Important limits of the existing observation point:

- If proot/the foreground root dies with 137, the runtime can retain that code.
- If the supervisor alone is killed, CLI `daemon-instance.js` maps a signal's
  null code to 1: the root exit may be **1**, not 137.
- If only the worker dies after readiness, the supervisor logs its `code/signal`
  and restarts it. The outer helper can remain alive. A Claude-only death likewise
  need not kill the daemon. Those are not outer helper-exit records.
- The private rotating supervisor log already receives lifecycle lines, but also
  worker output. Do not import the raw file into diagnostics; it may contain
  provider/user data. A future pinned Paseo change could export only structured
  worker role + numeric code/signal + timestamp through a narrow trusted channel.
- Death before successful service registration (the initial 1.5-second startup
  window) still uses the existing safe startup error. If Flutter itself dies, its
  watcher cannot run; Android's main-process history remains the evidence. We do
  not fabricate helper exit times from stale running flags on the next launch.

## Implemented in this branch

- Add `lastExitAtMs` (observation wall clock) and nullable `lastStopRequested` to
  persisted native service diagnostics. Explicit runtime stops mark intent before
  killing; natural exits record false. Old records stay unknown. Durations remain
  monotonic. The new keys are included in profile deletion.
- Add the same narrow facts to `agentHostStatus` and typed
  `BuiltinPhoneAgents.helperStatus()`. Read-only
  `ConnectionController.phoneAgentHelperStatus()` drops results after host/profile
  replacement; existing `helperRunning()` remains compatible.
- `AppDiagnosticsGateway.exitHistory()` supplies a separate immutable
  `helperExits` list, latest observed unexpected exit per helper, sorted/newest and
  bounded. Profile suffixes, argv, credentials, raw logs and paths are excluded.
  Restart retains the last observation. Deliberate/undated/old records are excluded.
  Android `entries`, support/error semantics remain unchanged; failure reading
  helper data does not discard Android history.
- Pass helper metadata through the existing turn-stall probe and expose cautious
  notice copy. No changes to automatic recovery, prompt replay or consent.
- [Frontend contract](../../design/BD13-contract.md) supplies the remaining notice
  and Recent exits rendering/localization work. **No `lib/ui/` edits; the new
  explanatory UI is not enabled by this backend branch.**

Only each service's latest exit is retained, not an unbounded event journal.
Another completed run may supersede it. Clock adjustment can affect display order.
The code classifies 137 as a possible resource kill, not confirmed Android policy.

## Lower-process options

1. Already enabled: one admitted live private helper, `exec` instead of retained
   wrapper shell, Paseo MCP injection off, service proxy/web UI/voice/dictation off,
   and direct binary invocation without `npx`/npm wrappers. Keep these defaults.
2. Audit the **enabled** Claude user/project/local MCP entries with the owner;
   disable unused ones through their supported configuration. The owned `oc-ui`
   registration runs a Node stdio helper only where installed; it is not necessary
   for ordinary text/tool turns but supplies GenUI. Remove/disable through existing
   managed controls when not wanted. Do not delete arbitrary user MCP settings or
   use `claude mcp list` as a cheap probe: the installer explicitly avoids it because
   it launches unrelated servers. Do not confuse hidden tools with stopped servers.
3. Use fewer simultaneous agent conversations, concurrent Bash tasks and unused
   terminals; stop unused AI Team/OpenCode workloads using their existing controls.
   Limit sub-agent concurrency where the agent supports it. This reduces pressure
   without claiming a fixed per-sub-agent process saving.
4. Shared **Node executable** is already used (`process.execPath`, pinned Node),
   but every Node process has its own heap. Sharing the binary does not turn four
   processes into one. Upstream could make the terminal worker lazy (save one when
   no terminals), or expose a supported foreground supervisor entry that avoids
   the waiting CLI (save one). Both require pinned-package/protocol validation;
   bypassing lifecycle/signal/security wrappers locally is not a safe fix here.
5. A shared HTTP MCP service can avoid per-query stdio servers only if its
   authentication, profile isolation, lifecycle and agent support are proven.
   No new shared credential-bearing daemon is introduced on speculation.

Keep the existing Developer-options/ADB mitigation documented for informed manual
use, not an automatic app setting. A foreground service/wake lock does not prove
immunity to process limits or low memory. On future authorized device work, capture
process **counts/roles** and exact UID/PID/start-time ancestry around the turn plus
ActivityManager kill cause and native exit metadata; never export argv/env/raw logs.
Compare baseline, one turn, one sub-agent, MCP enabled/disabled, and concurrent
OpenCode/AI Team. That is the missing evidence for the owner's actual failure.

## Verification

**65 focused tests passed; analyzer clean.** [green.txt](green.txt) contains the
64-test frozen-source run; [controller-green.txt](controller-green.txt) contains
the additional read-only/disposal-fence controller regression. [red.txt](red.txt)
shows that removing the native timestamp mapping makes the helper history test
fail (zero helper entries instead of one); the exact source was restored before
the final green run. [analyze.txt](analyze.txt) records the clean analyzer.
[source-sha256.json](source-sha256.json) identifies the tested source/test snapshot.

Commands (one at a time, pinned Flutter, `OC_TEST_SLOTS=1`):

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test --concurrency 1 test/agent_helper_diagnostics_test.dart --plain-name 'recent exits names only observed unexpected helper exits without profile data'
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test --concurrency 1 test/phone_agents_controller_test.dart --plain-name BD13
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test --concurrency 1 test/agent_helper_diagnostics_test.dart test/device_diagnostics_gateway_test.dart test/phone_agents_host_test.dart test/builtin_performance_diagnostics_test.dart test/turn_stall_test.dart test/app_exit_history_test.dart test/file_size_ratchet_test.dart
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze
```

`<pinned-flutter>` is `/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
The initial focused run waited 261 seconds for the shared slot; no overlapping
check was started. The four unchanged `app_exit_history_test` JVM scenarios reused
cache `e29dcd14ebd9d62cd6f42105b8cf6734b95a59572eef07f23e1f8e8a54b6d290.jar`,
mtime 2026-10-08 02:40:14 UTC: no compilation. New `ServiceDiagnosticsTest` native
cases were extended but **not executed or compiled** under this hold.

No Gradle/APK/native compilation, emulator, live Claude, full suite, settings
changes, push, signing or release was performed. Native/device qualification and
the UI contract remain coordinator follow-up; the owner's incident remains unproven.
