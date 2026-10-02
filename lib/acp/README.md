# ACP feasibility client

This isolated spike implements ACP v1 newline-delimited UTF-8 JSON-RPC. It is
not registered as a server flavor, gateway, profile, or enabled app feature.
It has no network code, persistence, credential storage, file access or terminal
execution. Its caller must supply a trusted local process stream or an approved
Tailscale/SSH relay; public internet access is not an acceptable transport.

`AcpClient(input, write, requestTimeout: ..., maxFrameBytes: ..., onPermission: ...)`
accepts `Stream<List<int>>` and `Future<void> Function(List<int>)`. Subscribe to
`updates` before prompting, call `initialize()`, then `newSession(cwd: absoluteUnixHostPath)`.
`prompt(sessionId: ..., text: ...)` returns `AcpPromptResult.stopReason` while
`AcpSessionUpdate` emits content/tool events. `cancel(sessionId)` sends a
notification; completion still comes from the agent. `authenticate(methodId)`
is explicit and requires an advertised method. The spike supplies no auth
credentials, terminal auth, MCP servers, list/load/revert/worktrees or rich
prompt content. `dispose()` abandons pending operations; the caller closes
its own transport/process.

The permission callback returns one offered `optionId`, or null to cancel.
The default is cancelled. Unknown option kinds, duplicate selected IDs, callback
errors and callback timeouts are cancelled. Cancellation invalidates outstanding
callbacks and answers pending requests with cancelled. No approval is persisted.
Agents retain their own tools: declining client FS/terminal capabilities is not
an agent sandbox and does not prevent host-side tools from executing.

Frame size is bounded (1 MiB default), outgoing requests, queued writes and outstanding
permission callbacks are each capped at 64, writes serialize and time out, and any request timeout
closes the client. No prompt is retried: transport loss means unknown turn outcome.
Notifications are volatile broadcast events; callers must consume without retaining
unbounded buffers. Agent JSON-RPC error text/data and I/O exception text never
enter `AcpException`; only a local failure enum and numeric RPC code are exposed.
Typed models preserve unknown fields in `fields`; those maps, content, titles,
paths and authentication metadata remain untrusted/private and must not be logged.

Protocol basis: [transports](https://agentclientprotocol.com/protocol/v1/transports),
[initialization](https://agentclientprotocol.com/protocol/v1/initialization),
[sessions](https://agentclientprotocol.com/protocol/v1/session-setup),
[prompt turns](https://agentclientprotocol.com/protocol/v1/prompt-turn), and
[permission/tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls).

Run the local bounded probe with `dart run tool/acp_smoke.dart -- gemini --acp`.
It only initializes, creates a temporary empty cwd, discards stderr, reports safe
primitive metadata, then terminates its exact child PID. It never installs an
agent, authenticates, creates a session, submits a prompt, or prints wire payloads.
