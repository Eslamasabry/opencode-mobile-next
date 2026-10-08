# FQ3d: PC OpenCode 2 cause isolation

Finish line: run the real app Dart client against one owned PC OpenCode 2
2.0.10 server, distinguish model selection from inference and image receipt
from visual use, retain bounded public facts, and clean up owned resources.
Non-goals: emulator/APK work, provider enrollment, credential copying,
production changes without a reproduced app defect, or phone qualification.

Base: `e20b0977be0e90f8eeaf0203e2aad4c974be4203`, branch
`sol/bc-oc2-causes`. Detailed results and focused verification follow below.

## Repeatable probe

The PATH `opencode2` is an unrun-postinstall stub for the obsolete
`@opencode-ai/cli` beta. The probe uses an existing pinned `@opencode/cli`
2.0.10 native binary through a private `opencode2` symlink, with exactly
`serve --port 4097 --hostname 127.0.0.1`. The shared launcher is untouched.
`FQ3D_BINARY` selects another already-installed 2.0.10 binary; version and
SHA-256 are recorded before launch.

`tool/qa/fq3d_run.py` owns the server's exact process handle/PID and refuses an
occupied port. Startup stdout/stderr are bounded and never forwarded or saved.
It parses the generated Basic password privately and passes it only in the
Dart test child's environment. The real `Api2Client` runs without UI through
pinned Flutter, since its production imports need Flutter. The launcher drains
server output, stops only its owned PID, and removes only its temporary project
and optional isolated XDG directories after confirmed process exit.

```bash
FQ3D_MODEL_B=opencode/mimo-v2.5-free \
FQ3D_ISOLATED_CONFIG=1 \
FQ3D_BINARY=/home/eslam/Storage/Code/oc2-spike/new/node_modules/@opencode/cli-linux-x64-baseline/bin/opencode \
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- python3 tool/qa/fq3d_run.py
```

There is no silent model fallback: defaults are `opencode/big-pickle` and
historical `opencode/exo-free`; explicit `FQ3D_MODEL_A`/`FQ3D_MODEL_B` select a
separate run. Requested IDs must match an initially enabled catalog row from the explicit
provider. Diagnostic sends retain that explicit ID if it subsequently disappears,
so its rejection can be recorded; before/after fresh-catalog booleans expose
that condition instead of silently replacing the model. The runner uses the app's `api2CatalogModelID` mapping rather
than underlying API `modelID` (modes can share that ID).

Passing a text turn requires HTTP selection retention, a fresh successful
execution and an error-free completed assistant from that selected model.
Exact token compliance is retained as a separate boolean, because this probe's
model-switch assertion checks which model answered. Image proof additionally
checks exact persisted PNG bytes and the semantic answer to an unlabeled
red-left/blue-right fixture. Prompts omit expected colors. Admissions, enabled
catalog rows and idle alone never count as replies. Provider auth is the exact
`provider.auth` discriminator; it stops the run at the failing send, before any
other model/image attempt or credential enrollment. Transport Basic-auth
failures are classified separately.

## PC prerequisites and observed catalog

Both regular and baseline 2.0.10 binaries failed to bind within 120 seconds
with inherited PC XDG/config; their captured processes were stopped with exact
SIGTERM/SIGKILL escalation. The regular binary also stalled under a Python
launcher, so this is not confined to Flutter child launch. `/proc` observations
showed mostly system CPU, not a proven memory or binary-compatibility cause.
No default config, credential file, cache or other live process was changed.

The same baseline binary **started with isolated XDG directories**, with no
copied credentials. The real client reached authenticated health/info and
observed version 2.0.10. This establishes a PC configuration/data/cache-context
startup prerequisite; the particular offending file/component is not isolated
and solving that unrelated host setup issue is outside this lane.

[Current catalog proof](pc-current-catalog-1791478127453979.json) has enabled
`big-pickle` and vision-capable `mimo-v2.5-free`, but no `exo-free`. The old
model is unavailable at catalog admission today. This is distinct from the
[earlier phone observation](../FQ3b-2026-10-08/README.md), where advertised
`exo-free` repeatedly returned HTTP 503 while `big-pickle` answered. Neither
proves missing app-injected auth. The old unscoped timeout artifacts cannot be
reconstructed as a specific event-versus-inference failure after session cleanup.

## Capability results and classification

[Final real-client proof](pc-client-proof.json) explicitly uses
`big-pickle` then `mimo-v2.5-free`, with no silent fallback:

| Item | Actual PC result | Classification |
|---|---|---|
| Model A | `big-pickle` retained, successful execution, completed exact-token reply from `big-pickle` | Real client/inference pass |
| Model switch | Mimo selection retained and prompt admitted; no assistant; `provider.no-route`, `model_unavailable`; Mimo absent from fresh catalog both before and after send | Harness stale startup catalog + server model availability; no app payload bug shown |
| Image | One URI-only PNG admitted and retained byte-for-byte; same `provider.no-route`, `model_unavailable`; no generation/semantic answer | Same availability blocker; server received image, visual use **unverified** |

The initial catalog is not settled availability. This is a concrete harness
expectation defect, not evidence that `switchModel` or inline attachments are
broken. A subsequent qualification run must refetch/settle the current location
catalog before choosing an alternative or vision model; a disappearing model
remains an unavailable/precondition failure, never a capability pass. There is
no currently demonstrated usable vision alternative in this run. A model key
was not enrolled or copied. No `provider.auth` error occurred, so **missing
provider auth is not asserted as the exact runtime error**.

The pinned server's [model list handler](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/server/src/handlers/model.ts)
reads `Model.available()`, while the [session resolver](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/runner/model.ts)
raises `ModelUnavailableError` if the selected provider/id is absent from that
location's available set. The [error mapping](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/to-session-error.ts)
converts that into `provider.no-route`, rather than `provider.auth`. The
[OpenCode provider plugin](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/plugin/provider/opencode.ts#L243-L256)
disables models with positive input cost when there is no account/key; this is
a possible catalog-filter mechanism, not proof that it caused this particular
Mimo removal. Initial availability, settled availability and authentication
are distinct facts.

Earlier phone timeouts remain historically unresolved at their exact stage:
they lacked scoped alternative/vision-model facts. The PC proves an actual
catalog/availability failure and correct app request admission, not a restored
phone capability pass or a retrospective identification of each old timeout.
No backend API/gateway change is justified by these results.
PC proof never replaces the phone/APK certification matrix or deferred 2197 runs.
No application/library change is justified without a reproduced app defect.

## Focused verification

Commands use pinned Flutter/Dart and `machine_lock` for jobs. The ten Dart
fixtures falsify stale, unfinished, erroneous, wrong-model and event-only
assistant proof; catalog mode identity collapse; malformed password lines;
wrong/missing PNG bytes; wrong image semantics; auth-status confusion; and omitted-versus-foreign event location.
Twelve Python fixtures cover port refusal, version pinning, bounded startup,
missing/malformed/duplicate password, private credential handoff, exact process
cleanup/escalation and keeping an owned project when process termination fails.

```bash
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- python3 -m unittest discover -s test -p fq3d_launcher_test.py -v
# With FLUTTER set to the pinned 3.47.1 binary:
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 test/fq3d_proof_test.dart
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh analyze -- "$FLUTTER" analyze --no-pub tool/qa/fq3d_live_test.dart tool/qa/fq3d/proof.dart test/fq3d_proof_test.dart
ruff format --check tool/qa/fq3d_run.py test/fq3d_launcher_test.py
ruff check --select F,E9 tool/qa/fq3d_run.py test/fq3d_launcher_test.py
```

No emulator, ADB, APK/build, signing, push or provider enrollment occurred.

Final checks: 12 Python launcher tests and 20 serial Dart tests (10 new proof
fixtures plus 10 existing `api2_sse_test.dart` cases) passed. Python formatting
and F/E9 checks are clean. The live test verifies probe safety; its green exit
is **not** a claim that the two failed capabilities passed. Both owned sessions
were deleted, the captured server exited, and port 4097 was left free.

The probe itself initially discarded globally owned execution events with no
`location`, despite the app adapter already accepting an omitted location.
The retained probe-location/terminal artifacts demonstrate that false
negative against a completed Big Pickle reply. Its corrected predicate accepts
only the exact owned session and rejects an explicitly foreign directory;
regression fixtures cover all three cases. Terminal audits synchronize the
per-session log before a new send and reconcile content with fresh HTTP state;
old model-A replay cannot qualify model-B output. No app behavior was changed.

Final focused analyzer: clean, no ignores. Format, F/E9, local-link, JSON,
credential-field and final diff checks passed. Full repository suite and phone
qualification were not run for this QA-only diagnosis.
