# FQ9 retained-turn observation

Finish line: a single 2198 HOME dwell has real tool progress at five and thirty
minutes, or preserves its original terminal state and the 19–21 minute log
window before any resume, abort or deletion. Non-goals: production changes,
Claude/account actions, BD7, physical-device qualification or changing runtime
timeouts globally.

The old twenty-call fixture could stop early when the model stopped scheduling
calls. Use one foreground shell loop with ninety sequential 30-second sleeps,
fixed numeric output and an explicit 3,000,000 ms tool timeout. It requires at
least 45 minutes if completed. Admit only its exact command/timeout and real
persisted output counters; a model claiming completion is not progress.

The frozen OC1 source tree 545f51d26cc39a907d2867492d498d9607ea5fa4
uses `packages/opencode/src/tool/shell.ts` (not the OC2 core bash tool): positive
optional timeout, no ten-minute upper bound, metadata output updates during the
command. Retrieved source hashes: shell.ts
342d742ae324782d222465c202dcdfcb7cc35a4cecf04e87f9881f1794d921ac;
shell/prompt.ts f3c6bdb216a9df2dd871d3a786b2abe3a20ea3e44b9121ce0610c648256fb2b6.
Device admission must verify the actual timeout and output, not infer them from
these sources.

Ownership: retention worker edits background.py/test_background.py; terminal
worker adds terminal.py/test_terminal.py; logs worker adds
diagnostic_logs.py/test_diagnostic_logs.py. Root owns runtime/CLI integration,
fixture, collector, device execution and evidence. Workers run focused checks
one at a time with machine_lock and OC_TEST_SLOTS=1.

`project_terminal(history,status,receipt)` exports fixed outcome, finish/error
category and numeric message/tool times only. `project_log(bytes,source,window,
app_pids)` exports timestamp/level/fixed category only; omitted lines remain
explicit. Raw histories/logs and runtime password stay bounded in memory.

Collect logs periodically within 19–21 minutes, then collect owned terminal
state before foregrounding. The zero-argument `before_cleanup` callback returns
literal True only after exclusive evidence creation and fsync. Capture failure
retains the owned session and private receipt; no abort/delete or resume. A
persisted capture failure is not a terminal-state diagnosis. Empty projected
events do not mean no errors occurred. Cleanup still revalidates ownership.

Device work queues behind BA under flock -w 1800, one reservation through normal
2198 restoration. BD7 is parked pending BB's stale CHECK-ticket answer.
