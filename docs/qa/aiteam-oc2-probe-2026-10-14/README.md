# AI Team OC2 feasibility probe — 2026-10-14

**Latest verdict: the live permission boundary passed on OpenCode 2.0.29 with
`zai-coding-plan/glm-5.3-flash`.** The earlier blocked attempts below are historical.
Host wire proof clears adapter implementation; it is not emulator or owner-phone
qualification. See the [integration contract](../../design/aiteam-oc2-contract.md)
for implementation and validation status.

## Second PC follow-up: ZAI provider and real tools

The coordinator reported restoring the accidental ancestor npm install from the
previous attempt. This follow-up did not touch `/home/owner`'s package files.
It created its own `package.json`, verified `npm prefix` equalled the scratch
directory, and used `npm install --prefix <scratch> @opencode/cli@2.0.29`.

- Private root: `/home/owner/Storage/tmp/oc2-probe-aiteam-pc/run-followup3-9VN1kf`,
  umask 077, separate HOME, all XDG directories, and TMPDIR. Server bound only
  `127.0.0.1:4193`, version 2.0.29, listener PID **4193270**.
- `/api/integration` advertised `zai-coding-plan` with key authentication. A
  script selected only that authorized entry from the owner's auth JSON and
  sent `{key}` to `/api/integration/zai-coding-plan/connect/key`: **204**.
  Location reload returned 204. No key was printed, copied to another file, or
  committed. No owner credential store was modified. ZAI worked, so the fallback
  `minimax` credential was not read or used; Zen was not used either.
- The live catalog advertised tool-capable `glm-5.3-flash`, selected for this
  bounded probe. Six short prompts were used, including two discovery attempts
  that exposed the `bash` versus `shell` naming difference described below.

### Results that clear the feasibility gate

| Requirement | Actual wire/tool evidence |
|---|---|
| Readonly read and role delivery | With deny-all + read/glob/grep, real `glob` and `read` tools completed; final text included both `READ_FIXTURE_F3_4193_OK` and the instruction-only `ROLE_ENTRY_F3_4193` marker. Durable idle outcome was `succeeded`. |
| Real shell refusal under readonly | Start a turn with shell advertised, then replace its policy with readonly and read back the exact array while the provider is running. The subsequent real `shell` call recorded `state.status:error`, `error.type:permission.rejected`, `Permission denied: shell`. No marker and no pending permission; final idle `succeeded` reflects the model reporting the refusal. |
| Worker execution | Deny-all + read/glob/grep/edit/**shell** exposed the real shell tool. Its command wrote `SHELL_WORKER_ALLOWED` to the fixture before entering a sleep. The transcript contains the actual `shell` call; marker alone is not the proof. |
| Busy, success, interrupt | Readonly and denial turns were observed running, then inactive with durable `succeeded`. During the worker's real shell execution, interrupt returned **`{interrupted:true}`**, tool error `aborted`, and idle outcome `interrupted`. The post-sleep marker remained absent after more than the requested delay. |
| Pending `ask` | A session policy with `shell:ask` caused a real tool-generated pending request with action `shell`, command in `resources`, and source `type:tool`. The session permission list exposed it. No marker before approval; interrupt cleared the request without a marker. |
| Refetch surfaces | Final active snapshot was empty. Per-session permission and form lists returned 200 with empty arrays. A positive form interaction was not exercised. |

The denial test deliberately lets the model receive a shell tool definition
before replacing the policy. This forces a real dispatch through the permission
check. With readonly set before prompting, OC2 removes shell from the tool list;
that earlier observation alone was not counted as runtime denial proof. The
policy readback preceded the rejected call, and no retry or substitute write
was used in the successful denial test.

**OC2 uses `shell`, not `bash`, as its shell permission action and tool name.**
The original API-only matrix accepted the arbitrary string `bash`, which did not
enable shell. In the first worker discovery attempt, the model substituted
`write` to create the requested marker even though it had no bash tool. That
attempt proves neither shell execution nor shell cancellation and is retained
as discovery evidence only. Allowing `edit` also permits the `write` tool, matching
the intended worker file-edit capability. The corrected tests require a real
`shell` record. Readonly continues to deny all actions except read/glob/grep.

### Evidence, cleanup, and limits

[provider-results.json](provider-results.json) contains sanitized typed message
records and tool results, exact policy readbacks, the pending request, fixture
states, and cleanup receipts. It excludes model reasoning and credentials.
Both misleading discovery attempts are retained and distinguished from the
corrected shell tests. No claim is inferred from a marker alone.

After all sessions were inactive, `ss -ltnp 'sport = :4193'` identified PID
4193270 and `/proc/4193270/cwd` matched the private repo. SIGTERM was sent only
to that PID. Process exit and listener absence were verified, then the entire
owned scratch directory—including credential storage, logs, and cache—was
deleted. No pattern kill or owner-server action was used.

Only **2.0.29** was exercised. OpenCode 2.0.28, positive forms, live event-stream
reconnection, full AI Team orchestration, and Android confinement remain
unproven. These limits must remain distinct from the host permission proof.

### Queued-stop follow-up (no model call)

Review identified a separate stop window: prompt acceptance precedes active
execution. A fresh isolated 2.0.29 server on port 4193 admitted one prompt with
`resume:false`. Its inbox contained the accepted ID while `/api/session/active`
was empty. Interrupt returned `{interrupted:false}` and **left the queued item
intact**. After deny-all PATCH and exact readback,
`DELETE /api/session/{id}/inbox/{messageID}` returned 204; the inbox became empty
and active remained empty. This proves why the driver must cancel queued input
as well as interrupt active execution.

[cancel-results.json](cancel-results.json) records the result. This scratch run
used its own verified npm prefix, HOME/XDG/TMPDIR, and no provider credential or
model call. Its exact listener was stopped and the entire owned scratch root
deleted after verification.

## PC follow-up with the authorized Zen key

The follow-up job requested a fresh **local PC** server and use of only the
owner's existing `opencode` API credential. This attempt also stopped before
tool execution. [pc-results.json](pc-results.json) contains allowlisted evidence
and cleanup receipts.

- Created private scratch root
  `/home/owner/Storage/tmp/oc2-probe-aiteam-pc/run-20261014-qr4XrD` with umask 077,
  separate HOME and XDG config/data/cache/state directories. The retained server
  also used a scratch `TMPDIR` and bound **127.0.0.1:4193** only.
- Installed and verified `@opencode/cli@2.0.29`. The corrected isolated install
  used a private `package.json` and explicit `npm install --prefix <scratch>`;
  see the setup incident below.
- Checked `/openapi.json`: key connection takes `{key,answer?,label?}`. A script
  selected only `opencode` from the existing auth JSON and POSTed its key to
  `/api/integration/opencode/connect/key`; **204**. The key stayed in script
  memory until transmitted to the scratch server's credential store. No key was
  printed, logged by the script, committed, or copied to an additional file.
  The `opencode-go` credential was not selected or used. No writes were made to
  the owner's auth JSON or real OpenCode data directory.
- An initial background server exited when its command session ended. The
  subsequent connection attempt reported transport failure. After starting the
  retained server in a foreground execution session, a read of the integration
  list confirmed **zero** scratch connections before the successful key POST.
- `POST /api/location/reload` returned 204 and made the paid model catalog
  available in this scratch location. No owner server was reloaded.
- Selected `opencode/claude-haiku-5-5`, a small tool-capable model tied with
  `gpt-6-luna` at the lowest advertised nonzero base input price: **$0.10 input /
  $0.50 output per million tokens**. These are catalog prices, not billing proof.
- Created a session with the verified readonly policy and an instruction entry
  requiring `ROLE_ENTRY_PC_4193`. Requested a real read of `readable.txt`, followed
  by one bash attempt to create `readonly-marker.txt`. The fixture contained
  `READ_FIXTURE_4193_OK`. The instruction entry was read back successfully.

| Model | Prompt receipt / active state | Durable result |
|---|---|---|
| `opencode/claude-haiku-5-5` | 200; observed running then inactive | **402 `provider.quota`**, insufficient account funds; empty assistant content, failed outcome |
| `opencode/big-pickle` | 200; observed running then inactive | **403 `provider.auth`**, free-tier client restriction despite the signed-in key; empty assistant content, failed outcome |

The second and final prompt used the advertised zero-cost, tool-capable model
with the same authorized credential. Neither attempt called read or bash,
created a pending permission request, or returned the role marker. The fixture
was unchanged and the write marker absent, which **does not prove a tool was
refused**. No worker turn, successful busy-to-idle transition, active interrupt,
or positive `ask` test was attempted after this blocking result. No adapter or
Dart gate change was made.

### PC cleanup and setup incident

`ss -ltnp 'sport = :4193'` identified **PID 4187939**; its working directory
matched the owned scratch repo. Sent SIGTERM to that exact PID and verified both
process exit and listener absence. Then deleted the **entire owned scratch run
directory**, including the scratch credential database, logs, caches and scripts.
Only the sanitized evidence remains in this checkout. No pattern kill was used.

The initial npm install was a scope mistake: without a scratch manifest, npm
resolved the ancestor package prefix to **`/home/owner`**. It updated
`/home/owner/package.json`, `/home/owner/package-lock.json`, and `node_modules`.
Its debug log recorded five additions (`@opencode/cli` and four Linux x64 binary
packages) and one change (`@code-yeongyu/comment-checker`). This was corrected for
the probe by creating the private manifest and using an explicit prefix. The
ancestor package changes **were not rolled back in that attempt**, because no
pre-install baseline was available and a guessed rollback could alter other
work. The coordinator subsequently reported restoring the binary links and
removing the added `@opencode` packages/manifest/lockfile entries. This agent did
not modify those package files again. No real OpenCode server was signalled or
restarted.

Local validation of this follow-up: JSON evidence assertions, documentation link
checks, and `git diff --check`. No Rust/Dart runtime code changed, so cargo and
Flutter were not run. B2 remains blocked pending a usable authorized model.

## Original build-server attempt: environment and boundaries

- Checkout: `codex/aiteam-oc2`, starting revision
  `efbc3a5ea73c9a914feb315c3a87099f7f8c386a`.
- Build host: `the build server`, SSH key authentication.
- Scratch root: `/home/owner/oc2-probe-aiteam-20261014-3jL7XM`.
  Separate `home`, `config`, `data`, `cache`, and `state` directories supplied
  `HOME`, `XDG_CONFIG_HOME`, `XDG_DATA_HOME`, `XDG_CACHE_HOME`, and `XDG_STATE_HOME`.
- Installed with `npm i @opencode/cli@2.0.29`; explicitly ran
  `node node_modules/@opencode/cli/postinstall.mjs` from its package directory
  (the actual command there was `node postinstall.mjs`).
- Started the installed `opencode serve --port 4191 --hostname 127.0.0.1`
  in the scratch `repo` with an empty git commit and a `readable.txt` fixture.
- Authenticated with Basic user `opencode`. Python read the generated password
  from the private server log into the Authorization header. No password was
  printed, copied into this checkout, or committed. No existing provider
  credentials were used. Scratch files were created with umask 077.
- Authenticated `/api/info` reported **2.0.29**, PID **224894**. `/openapi.json`
  supplied the current callable contract. **2.0.28 was not probed.**
- No phone, emulator, Flutter run, native build, release, or live user server
  was involved.

## Live results

| Requirement | Observation | Qualification |
|---|---|---|
| Session deny-all | Create accepted `permissions:[{action:"*",resource:"*",effect:"deny"}]`; session GET returned it | API storage proven |
| Readonly policy | Deny-all followed by allow `read`, `glob`, `grep`; evaluator allowed these and denied `edit`, `bash`, `webfetch`, `task`, and `unknown_probe` | Evaluator proven; actual tools unproven |
| Worker policy | Same plus allow `edit`, `bash`; other tested actions remained denied | Evaluator proven; actual tools unproven |
| PATCH replace/append | PATCH returned 204; GET exactly matched each new array through deny-all → readonly → worker → deny-all. Final deny-all removed worker allowances | **Replacement** observed, not append-only |
| Rule precedence | Later action-specific allows took precedence over initial wildcard deny in these cases | Observed for tested actions/resources only |
| Role instructions | PUT experimental instruction entry returned 204; GET returned exact role text with `ROLE_WIRE_4191` | Storage proven; model delivery unproven |
| Per-session model | Create stored Muse; model-switch POST returned 204 and GET stored JEV; Cat Jam appears on the later assistant record | Session state proven |
| Prompt acceptance | Three prompts returned 200 with a durable user inbox receipt | Acceptance proven, not successful execution |
| Busy → idle | Muse and Cat Jam appeared as `running` in `/api/session/active`, then disappeared; durable transcript recorded `outcome:"failed"` | Failure transition proven; successful completion unproven |
| Interrupt | Idle POST with `resume=false` returned 200 `{interrupted:false}` | Idle no-op only; active interruption unproven |
| Pending permissions/forms | Both location-wide and per-session lists returned 200 with empty arrays | Empty-list contract proven; positive pending requests/forms unproven |

The permission evaluation requests were
`POST /api/session/{sessionID}/permission {action,resources:["*"]}`.
These evaluate a policy; **they do not execute a tool**. All four policy readbacks
matched exactly, including removing the previous worker policy. The evidence
does not establish resource-specific matching or every possible tool action.

## Why the model proof stopped

All attempts used the installed CLI's normal server execution and advertised
free models; no custom provider headers or authentication workarounds were used.

1. `opencode/muse-spark-1.3-contributor-free`: prompt admitted, then
   `provider.auth`, HTTP **403**, before any tool call. The provider response said
   its free tier can only be used from within OpenCode. The assistant had empty
   content and `finish:"error"`.
2. `opencode/jev-1.13-free`: model switch was accepted, but execution immediately
   produced `provider.no-route` (model unavailable). Unlike the earlier probe
   mentioned in the job, this attempt did **not** produce HTTP 500.
3. `opencode/cat-jam-free`: advertised by `/api/model`; prompt admitted, then the
   same `provider.auth`/**403**, empty assistant content, and failed outcome.

The prompt requested a real read of the fixture and a forbidden
`bash printf DENIED_SHELL_4191` invocation. Neither tool ran. No assistant emitted
the role marker. A provider error and an absent filesystem mutation cannot stand
in for a tool-denial receipt. Further adapter work is blocked until a permitted,
working model can exercise that boundary.

## Wire contract captured for a future attempt

All paths below include the `/api` prefix. Model references use `{id,providerID}`,
not `{modelID,providerID}` (the latter returned 400 during discovery).

| Method/path | Operation ID / relevant body |
|---|---|
| `GET /api/info` | `server.info`; unwrapped server identity |
| `POST /api/session` | `session.create`; `{location:{directory},agent:"build",model:{id,providerID},permissions}` → `{data:session}` |
| `GET /api/session/{sessionID}` | `session.get`; `{data:session}` with `location`, `permissions`, and `model` |
| `PATCH /api/session/{sessionID}` | `session.update`; `{permissions:[...]}` → 204; follow with GET |
| `POST /api/session/{sessionID}/model` | `session.switchModel`; `{model:{id,providerID}}` → 204 |
| `PUT /api/experimental/session/{sessionID}/instructions/entries/{key}` | `experimental.session.instructions.entry.put`; `{value:roleText}` → 204 |
| `GET /api/experimental/session/{sessionID}/instructions/entries` | `experimental.session.instructions.entry.list`; `{data:[{key,value}]}` |
| `POST /api/session/{sessionID}/prompt` | `session.prompt`; `{text}` → 200 inbox receipt |
| `GET /api/session/active` | `session.active`; `{data:{sessionID:{type:"running"}}}`; absent IDs are inactive |
| `POST /api/session/{sessionID}/interrupt?resume=false` | `session.interrupt`; `{interrupted:boolean}` |
| `POST /api/session/{sessionID}/permission` | `session.permission.create`; `{action,resources}` → `{data:{id,effect}}` |
| `GET /api/permission/request` | `permission.request.list`; location query `location[directory]` |
| `GET /api/form` | `form.list`; location query `location[directory]` |

Session message history uses typed records, including `user`, `assistant`,
`model-switched`, and `idle`, plus pagination cursors. A failed idle transition
must not be interpreted as success. The event stream was not exercised here;
volatile-event reconciliation remains required by the existing protocol notes.
The documented 8 KB instruction limit was not boundary-tested.

## Evidence and cleanup

- [results.json](results.json): allowlisted policy readbacks, 32 evaluator
  decisions, transcript metadata for all three attempts, pending-list counts,
  instruction-storage check, and idle interrupt result. No raw provider response
  bodies, credentials, or user text are retained.
- [collect.py](collect.py): collector run on the build host against the recorded
  scratch session after the prompts. It leaves deny-all permissions. It is an
  evidence collector for this attempt, not an automated qualification test; the
  blocked verdict reflects the separately observed provider failures. It does
  not replay the prompt attempts or start/stop the server.
- Server cleanup: `ss -ltnp 'sport = :4191'` identified PID **224894**; its
  `/proc/224894/cwd` resolved to the scratch `repo`. Sent **SIGTERM to 224894**,
  then verified no listener remained on port 4191. No process-pattern kill.
- Private scratch logs/data remain on the build host for diagnosis; they are not
  checked into git. The server is stopped.
- Local validation: Python syntax, evidence assertions, relative-link checks,
  and `git diff --check`. No Rust or Dart code changed; cargo and Flutter tests
  were not run.

See the [blocked integration contract](../../design/aiteam-oc2-contract.md).
