# Agent handoff feasibility — 2026-10-14

This is source-contract evidence, not a device or live-server qualification.
No owner server was contacted. The checked-in clients and contracts were
inspected first, followed by official upstream source. The Codex follow-up
installed only the exact official CLI package in a scratch directory under
`/home/owner/Storage/tmp` to generate its protocol schema; no owner package
files were touched and no app-server or model session was started. No Flutter
command was run.

The owner clarified that the source is **direct Codex**, using app-server
0.153.4 and GPT-6.1 Sol, rather than an OpenCode conversation. The main
destination remains Claude Code through Paseo using the owner's Claude
subscription. These are separate agent runtimes; switching an OpenCode
provider is not the solution to this journey.

## Callable operations

| Backend | Conversation reads | Creation and first prompt | Project file writes |
| --- | --- | --- | --- |
| OpenCode 1 | `SessionGateway.messagePage`, `limit`/`before`, response continuation; enumerate until complete | `createSession`, then `promptAsync` with model and agent | No file-write operation in the pinned schema; `/file`, `/file/content`, `/file/status`, `/find/file` are GET operations |
| OpenCode 2 | Descending cursor pages mapped back to chronological order | Location-scoped `createSession`, then `promptAsync` with model and agent | Pinned filesystem routes are GET `/api/fs/read/*`, `/api/fs/list`, `/api/fs/find` |
| Direct Codex 0.153.4 | `thread/read` with `includeTurns: true` hydrates rollout turns; each turn must report `itemsView: full` | `thread/start` accepts cwd/model; `turn/start` accepts text input and model | Explicit `fs/*` APIs exist, but destination remains unavailable until a safe writer adapter is qualified; see below |
| Paseo | Projected timeline; bounded `genUiHistoryPage` checks epochs, gaps, stale cursors and older continuation | Local draft; first prompt sends `create_agent_request` with provider, model, cwd and initialPrompt | Supported by explicit file creation and optimistic file write operations when the daemon advertises `workspaceFileEditing` |

Local evidence: `lib/domain/server_gateway/interfaces.dart`,
`lib/api/opencode_api.dart` (`_messagePage`),
`lib/api2/gateway.dart` (`_messagePage`, `createSession`, `promptAsync`),
`contracts/opencode-openapi-f12e14cf.json`,
`contracts/opencode2-openapi-beta-17823.json`,
`lib/paseo/gateway/prompts.dart`, `lib/paseo/gateway/gen_ui_history.dart`.
The [official OpenCode server reference](https://opencode.ai/docs/server/)
also documents session creation, model-bearing prompts and read-only file APIs.

The app's existing `FileGateway` was read-only. Upstream investigation found an
explicit Paseo writer, so this slice adds a narrow handoff adapter rather than
using shell commands or asking an agent to manufacture the file.

## Exact direct Codex evidence

The current system CLI reports 0.162.1 and was not used as evidence for the
requested 0.153.4 contract. The official npm package
[`@openai/codex@0.153.4`](https://registry.npmjs.org/@openai/codex/0.153.4)
was installed with:

```sh
npm --prefix /home/owner/Storage/tmp/agent-handoff-codex-01534 install \
  --ignore-scripts --no-audit --no-fund @openai/codex@0.153.4
```

That scratch binary printed `codex-cli 0.153.4`. Its
`app-server generate-json-schema --out <scratch>/schema` and
`app-server generate-ts --out <scratch>/ts` commands completed successfully.
These generate protocol definitions without starting a listener or reading a
conversation. The package lock resolved the main package exactly to 0.153.4
with npm integrity
`sha512-wbHDmit7S/YvBGVX1DQmk13xtWblZ2cApeJ/pB7xDZ10Cna+DZc5ij7f0F4OxdsXN4FW1oLT48OpogUI1+8Y2w==`.

Relevant generated definitions and their SHA-256 digests:

| Definition under `ts/` | SHA-256 |
| --- | --- |
| `v2/ThreadReadParams.ts` | `c24743cb5655b4c71a853a2a290412d78e1a27e0278f2a0cde9bd9d9b78118a8` |
| `v2/ThreadTurnsListParams.ts` | `ff03522c5cd95e5dd0b03a6d918bf7c2453b491fb47b8109420d52fe9df619b3` |
| `v2/ThreadItem.ts` | `ee25d621ee645f49a88e494effcdf625d88cc229edbb184827d8cf4c135ff6f4` |
| `v2/FsWriteFileParams.ts` | `bcdebb3360d3a42089f32443850f9e02d33d609f7880b8cc8ed9f7beb1e08a06` |

`ThreadReadParams.includeTurns` requests turns and items from rollout history.
Its documentation explicitly calls this full-history hydration and deprecates
it for paginated threads in favor of `thread/turns/list` and
`thread/items/list`. The reply's `Thread.historyMode` distinguishes `legacy`
and `paginated`; each `Turn.itemsView` distinguishes `notLoaded`, `summary`
and `full`. A summary or unloaded turn is not complete export evidence.
`thread/turns/list` defaults to descending turns and summary items, so callers
using it must explicitly request the intended order and item detail, follow
`nextCursor`, and validate completion. `thread/items/list` can filter by turn
and returns `{turnId, item}` entries with its own continuation cursor.

The handoff source reads the API, not the currently rendered chat messages or
the mapper's shortened tool summaries. The existing transport rejects frames
over 4 MiB. A response exceeding that limit, a truncated/unsupported item view,
or changed connection/project must fail rather than silently export a cache
tail. The common handoff builder applies its separate output-size bounds and
shared credential redaction after extraction; source read limits must never be
presented as successful full-history coverage. Paging is a possible follow-up
for histories too large for the bounded full-read path.

Persisted `ThreadItem` includes user text, assistant messages with nullable
phase, command execution with cwd/command/status/output/exit code, file changes,
MCP and dynamic tools, textual plans, and a `contextCompaction` marker.
`MessagePhase` explicitly allows unknown legacy phase: preserve that text as
phase-unknown rather than falsely classifying it as a final answer. Explicit
commentary and private reasoning are excluded from final assistant texts.
Compaction markers do not supply discarded text; attachments and unavailable
content must be disclosed. Persisted `plan.text` is available, but the schema
does not turn it into a complete open-task checklist. Thread runtime status
and turn failure/interruption status are distinct; waiting flags include
`waitingOnApproval` and `waitingOnUserInput`.

### Direct Codex destination decision

The pinned contract **does expose filesystem writes**:

- `fs/writeFile`: absolute `path` plus `dataBase64`.
- `fs/createDirectory`: absolute `path`, optional `recursive` (default true).
- `fs/readFile`: returns `dataBase64`.
- `fs/getMetadata`: returns `isDirectory`, `isFile`, `isSymlink` and times.

It also supports `thread/start` with cwd/model and `turn/start` with text input
and a model override. Therefore the reason for withholding direct Codex as a
handoff destination is not absence of a generic write or conversation API.
The schema does not establish exclusive file creation, compare-and-swap
revision checks, no-follow behavior, or atomic project-contained writes.
A metadata check followed by a path-based write cannot establish those
properties on its own. Those semantics and the app's narrow writer adapter
remain unqualified. Direct Codex consequently keeps handoff-file writing
unavailable with a reason, while the already scoped Paseo adapter remains the
main destination. No shell-command writer or agent-prompt workaround is used.

## Exact Paseo evidence

The supported v0.11.2 tag resolves to commit
`75953250959979b8f088bbca8dbd6ee61d0151b1`.
Its [protocol schema](https://github.com/getpaseo/paseo/blob/75953250959979b8f088bbca8dbd6ee61d0151b1/packages/protocol/src/messages.ts)
defines `fs.entry.create.request` with cwd, parentPath, name and file/directory
kind; `fs.file.write.request` requires content and expectedModifiedAt, with an
optional expectedRevision. Results distinguish written, conflict and error.
The server-info feature flag `workspaceFileEditing` is optional: absence denies
the app's handoff writer. The file-read response includes version metadata.

The [file service](https://github.com/getpaseo/paseo/blob/75953250959979b8f088bbca8dbd6ee61d0151b1/packages/server/src/server/file-explorer/service.ts)
creates files exclusively and directories without recursive overwrite. Writes
check revision metadata, write a temporary file, and rename it into place.
Scoped-path resolution checks workspace containment. Directory listing uses
directory entries, so a symlink is not reported as a directory.

The same protocol schema specifies that timeline `limit: 0` means all matching
rows in the query window. Therefore the compatibility Paseo `messages()` call
must not be described as an intentionally truncated tail. However, that method
does not validate coverage flags; a faithful exporter should use the bounded
history path and retain its completeness evidence. Projected timelines merge
stream fragments; they are the user-visible record, not provider raw logs.

## Writer behavior and limits

`PaseoGateway.writeAgentHandoff` accepts a UUID-like safe record identifier and
at most 96 KiB of UTF-8 markdown. It creates a new exclusive directory
`.handoff/<recordId>/`, writes and reads back its own `.gitignore` containing
`*`, and only then creates, writes and reads back `record.md`. The nested ignore
rule also ignores itself. Existing shared ignore files and existing transfer
directories are never overwritten. New UUID identifiers avoid normal VCS path
collisions; this is not a git-index inspection API or a guarantee against a
concurrent actor force-adding files. A failed transfer can leave an empty or
ignored transfer directory; no automatic deletion or ambiguous write retry is
performed.

The adapter requires the captured project directory and socket epoch for every
request. Directory changes, disconnects, absent negotiated support, conflicting
writes, invalid paths and mismatching readback fail before returning a usable
record path. Errors do not include daemon payloads or conversation content.

The caller supplies the redacted record; the writer additionally rejects input
changed by the shared credential and opaque-token redactor. This is heuristic
filtering, not proof that arbitrary private text contains no secret. Permission
to use a CLI subscription remains with the target runtime's
normal signed-in account; no provider credentials are copied or reinterpreted.

## Project identity and completeness

Equal absolute paths on two servers do not prove they name the same files.
Cross-profile handoff requires trusted managed-host/project identity or another
explicit mapping verified by the application. A user-selected display path or
matching project name is insufficient. A readable target file alone also does
not prove the source working tree is shared.

OC2 has no session todo endpoint; its diff is the location's working-tree diff,
not session attribution. Paseo projects native agent history into app messages.
Unavailable attachments, omitted history, compacted output and inferred changed
files must be labelled; a partial in-memory snapshot must not claim to be the
full conversation. Assistant text should retain its visible content, while
reasoning is excluded. User text remains verbatim except required secret
redaction, which must be disclosed.

Same-server OpenCode model selection uses the existing session and next-prompt
model choice. It only offers models in the configured connected inventory;
the owner's separate Claude subscription is not an Anthropic provider on an
OpenCode server.

## Verification supplied

`test/paseo_agent_handoff_test.dart` covers negotiated capability loss, scope and
size validation, exclusively created transfer directories, ignore-before-record
ordering, shared-ignore preservation, symlink rejection, write conflicts and
readback failure. Tests are supplied but **not run**, per the task restriction.
Dart formatting and focused analysis completed using an existing cached
package map localized to this worktree. No Dart package install or Flutter run
was performed (the Codex schema package was installed only in scratch as above).
The writer also holds the managed host payload lease for
the complete file operation, preventing host maintenance from racing it.
