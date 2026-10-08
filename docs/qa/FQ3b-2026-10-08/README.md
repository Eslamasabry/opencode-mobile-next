# FQ3b diagnosis and repeatable certification

Normal app: **2196**, emulator-5554, application UID10217. All device work takes `/home/eslam/Storage/tmp/oc-emulator.lock`. The runner restores the approved same-signer2196 APK before and after locked device work when needed, preserving application data. A restoration restarts the normal app because APK updates terminate its process; its stored engine/model choice is retained. An already installed2196 is retained so the app-managed server is not restarted unnecessarily.

## OC2: default model versus app-selected model

On2196 the exact app-managed`:4097` server reported2.0.10, matching its installed CLI. The ordinary app-shaped prompt (`text`, without a queue/resume option) using the server default **opencode/exo-free** produced ten retry events with HTTP503, no completed reply and no fresh delta within100seconds. The same server and ordinary prompt shape selecting **opencode/big-pickle** completed with a fresh delta and execution-success event. An owned server using the same installed Ubuntu runtime and explicit big-pickle also completed through the certification adapter.

This establishes a model-specific inference failure at the time of the run; it does not establish missing app credentials or an owned-server configuration defect. The app can restore a selected profile/session model. The old harness followed the backend default; its existing `--oc2-model` option already supported explicit selection. FQ3b makes that choice part of phase/report/matrix provenance. No provider secrets are copied, no default configuration is rewritten, and no fallback silently turns a failed default-model attempt into a pass. Model-switch and image scenarios still require real replies from their independently selected alternative/vision models.

| Development diagnostic | Server/model | Result |
|---|---|---|
| [App-shaped default](fq3-20261008b-oc2-managed-app-shape-diagnostic.json) | app-managed / exo-free | failed;10HTTP503 retries |
| [App-shaped selected](fq3-20261008b-oc2-managed-bp-diagnostic.json) | app-managed / big-pickle | completed reply;fresh delta |
| [Adapter selected](fq3-20261008b-oc2-owned-bp-diagnostic.json) | owned / big-pickle | completed reply;fresh delta |
| [OC1 abort follow-up](fq3-20261008b-oc1-abort-owned-diagnostic.json) | owned / GLM-5.3 | fresh completed reply;0foreign parts |

The diagnostic artifacts record counts and fixed outcomes, not replies, provider configurations or credentials. Public model references are validated before export. The first OC2 diagnostic accidentally checked the OC1 role/parts shape: it is explicitly invalidated and is never a matrix input. The corrected helper uses OC2 type/content and has a failing regression against the old shape. Diagnostic artifacts from development identify their dirty source state; the final certification names the committed candidate below.

## OC1: reply after Stop

A live interrupted reply followed by a fresh prompt on the same session completed successfully on2196 with **zai-coding-plan/glm-5.3**. The final reply was parented to that fresh prompt, its text part belonged to the final assistant/session, its model matched the request, and the expected nonce appeared only in the owned final reply. No foreign parts or numeric continuation from the interrupted turn were observed.

The old `oc1_after_abort_reply_mismatch` means a completed assistant did not contain the requested literal token. Its redacted artifact cannot identify that reply's contents after the disposable session was deleted. Consequently the original mismatch cannot be attributed conclusively to model noncompliance or a stale-tail bug. The fresh run did not reproduce stale parts, and source inspection found preserved parent/message/part identity in the app path; no application gateway/state fix is justified by the evidence. The harness now refuses foreign text parts, a pre-final token or a reply from the wrong model instead of concatenating those into a false pass. Fixtures retain a failure for a genuine token mismatch and an old-parent token. The raw protocol harness does not certify rendering in the UI. A read-only trace found a separate conditional presentation hypothesis: an unseen old assistant appended after a new user could be grouped by display order. This was not reproduced, and cannot explain the original raw-HTTP harness mismatch; it is not a claimed backend defect or fix.

## Ownership and interruption recovery

Each title includes run, engine, scenario and session ordinal. A durable intent is written before creating a session; every returned ID is immediately journaled. Cleanup checks exact project directory plus owned title/ID before deleting, and removes only this harness's rows. Interrupted creation can be recovered by bounded discovery within the recorded run/scenario scope. Archived cleanup requires explicitly recorded IDs plus exact legacy directory/title, never a blanket title search. Failed cleanup retains its journal and invalidates all current-run passes before matrix generation. Shared-device handoffs initially left another installed build: the batch correctly refused it with `installed_build_changed`. The runner now prepares the approved2196 before inspecting the runtime, as well as restoring it afterward; wrong UID/engine/scope still refuse. Schema2 adds a required final cleanup acknowledgment, which the independent Python validator enforces.

## Repeat the final run

Run from the recorded committed candidate. The parent takes the shared lock separately for each phase and history/cleanup job:

```sh
DART=/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/cache/dart-sdk/bin/dart
"$DART" tool/qa/fq3_certify.dart --run-id fq3-20261008b-cert \
  --oc1-model zai-coding-plan/glm-5.3 --oc2-model opencode/big-pickle
python3 tool/agents/generate_certification.py
```

The runner reuses the exact engine at 4097 when available and otherwise starts an owned server. Each phase records which server it actually used. Add `--app-managed-engine opencode2` to require app-managed OC2 throughout; that mode refuses an unavailable or different engine. A frozen-source managed-only diagnostic refused an unavailable server after a shared-device handoff, so the complete run used automatic exact-engine reuse. Seven of its eight OC2 phases, including streaming, reconnect and abort, used app-managed OC2; permission allow used an owned server. All OC1 phases used an owned server under the application UID in the app's installed Ubuntu. Both CLI and HTTP pins, app build/UID, attempt, model scope, phase/session manifest and final cleanup must agree. Actual unavailable capabilities remain failures. The generated protocol namespace stays separate from the existing app/tool certification cells.

## Final committed-candidate result

The [complete run](fq3-20261008b-cert.json) on app **2196**, UID **10217**, used source **13d00fbb7649672491aa8458b7cd4cd807b188af** and one attempt across all phases. It produced **OC1 10/11** and **OC2 6/11**. Both engines passed live abort followed by a usable fresh reply. OC2 streaming and reconnect passed with explicit `opencode/big-pickle`; this does not qualify server-default `exo-free` inference.

| Engine | Failed assertions | Remaining result |
|---|---|---|
| OC1 1.18.32 | stream: `oc1_prompt_error` | version, create, models, model switch, abort, reconnect, permission allow/deny, image and cards passed |
| OC2 2.0.10 | model switch, permission allow/deny and image: `timeout`; cards: `cards_tool_call_missing` | version, create, models, stream, abort and reconnect passed |

The [bounded OC1 error diagnostic](fq3-20261008b-cert-stream-error-diagnostic.json) found one persisted `APIError` marked retryable, with no recoverable HTTP status or identified quota/auth/model cause. It did not recover the original error text. At the exact upstream OC1 revision, retry handling precedes persisting the assistant error and completed timestamp; the stored error therefore marks a terminal failure, even when its retryable flag remains true ([processor](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/processor.ts#L574-L655), [retry](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/retry.ts#L170-L191)). The failed stream assertion was retained. This source comparison is not a binary hash attestation or a reconstruction of its redacted cause.

The [history switch](fq3-20261008b-cert-histories.json) passed for **16 OC1 and 16 OC2 sessions**, comparing stored content through fresh OC1→OC2→OC1→OC2 clients. Final owned-session cleanup succeeded, the report has `cleanupCompleted: true`, no ownership journals remained, and the runner restored the approved normal app 2196 inside its lock. Separately, [archived cleanup](archived-cleanup-record.json) confirmed **56 recorded older session IDs deleted or already absent**, without deleting unrelated sessions by title. New titles contain run, engine, scenario and ordinal.

The [matrix](../../verification/agent-certification-matrix.md) was generated by the runner after final cleanup; its bundled Dart snapshot was regenerated with `tool/agents/generate_certification.py`. Existing app/tool certification cells are unchanged. No new APK, application gateway/state code, provider enrollment, UI qualification, full-suite claim, publication or push is part of this lane.

## Local checks before candidate freeze

Pinned Flutter3.47.1; `tool/qa/machine_lock.sh test -- flutter test --concurrency=1` across all ten FQ3/agent-certification files:132tests passed. Independent Python matrix validator:23tests passed. Six guard-removal proofs are retained as `red-*.txt`; each expected failure restored its source immediately. The four affected Dart files were then rerun serially:46tests passed. Full pinned `flutter analyze`:clean, no issues. This is focused validation; the coordinator owns the integration full-suite gate.

After the locked normal-app preflight change, the three affected test files passed **22 tests** serially and the changed-file analyzer was clean. After final matrix/snapshot generation, `test/agent_certification_test.dart` passed **14 tests** serially; Python validator **23 tests** passed again; snapshot `--check`, evidence/phase/history binding, zero journals, unchanged prior app/tool cells and unchanged older FQ3 evidence passed ([host validation](final-validation.json)). Final full pinned `flutter analyze` found no issues. `git diff --check` was clean. No full repository suite or native build was run.
