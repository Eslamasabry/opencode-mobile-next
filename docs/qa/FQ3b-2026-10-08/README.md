# FQ3b diagnosis and repeatable certification

Normal app: **2196**, emulator-5554, application UID10217. All device work takes `/home/eslam/Storage/tmp/oc-emulator.lock`. The runner restores the approved same-signer2196 APK inside that lock when needed, preserving application data. An already installed2196 is retained so the app-managed server is not restarted unnecessarily.

## OC2: default model versus app-selected model

On2196 the exact app-managed`:4097` server reported2.0.10, matching its installed CLI. The ordinary app-shaped prompt (`text`, without a queue/resume option) using the server default **opencode/exo-free** produced ten retry events with HTTP503, no completed reply and no fresh delta within100seconds. The same server and ordinary prompt shape selecting **opencode/big-pickle** completed with a fresh delta and execution-success event. An owned server using the same installed Ubuntu runtime and explicit big-pickle also completed through the certification adapter.

This establishes a model-specific inference failure at the time of the run; it does not establish missing app credentials or an owned-server configuration defect. The app can restore a selected profile/session model. The old harness followed the backend default; its existing `--oc2-model` option already supported explicit selection. FQ3b makes that choice part of phase/report/matrix provenance. No provider secrets are copied, no default configuration is rewritten, and no fallback silently turns a failed default-model attempt into a pass. Model-switch and image scenarios still require real replies from their independently selected alternative/vision models.

| Development diagnostic | Server/model | Result |
|---|---|---|
| [App-shaped default](fq3-20261008b-oc2-managed-app-shape-diagnostic.json) | app-managed / exo-free | failed;10HTTP503 retries |
| [App-shaped selected](fq3-20261008b-oc2-managed-bp-diagnostic.json) | app-managed / big-pickle | completed reply;fresh delta |
| [Adapter selected](fq3-20261008b-oc2-owned-bp-diagnostic.json) | owned / big-pickle | completed reply;fresh delta |
| [OC1 abort follow-up](fq3-20261008b-oc1-abort-owned-diagnostic.json) | owned / GLM-5.3 | fresh completed reply;0foreign parts |

The diagnostic artifacts record counts and fixed outcomes, not replies, provider configurations or credentials. Public model references are validated before export. The first OC2 diagnostic accidentally checked the OC1 role/parts shape: it is explicitly invalidated and is never a matrix input. The corrected helper uses OC2 type/content and has a failing regression against the old shape. Diagnostic artifacts from development identify their dirty source state; the final certification will name a committed candidate.

## OC1: reply after Stop

A live interrupted reply followed by a fresh prompt on the same session completed successfully on2196 with **zai-coding-plan/glm-5.3**. The final reply was parented to that fresh prompt, its text part belonged to the final assistant/session, its model matched the request, and the expected nonce appeared only in the owned final reply. No foreign parts or numeric continuation from the interrupted turn were observed.

The old `oc1_after_abort_reply_mismatch` means a completed assistant did not contain the requested literal token. Its redacted artifact cannot identify that reply's contents after the disposable session was deleted. Consequently the original mismatch cannot be attributed conclusively to model noncompliance or a stale-tail bug. The fresh run did not reproduce stale parts, and source inspection found preserved parent/message/part identity in the app path; no application gateway/state fix is justified by the evidence. The harness now refuses foreign text parts, a pre-final token or a reply from the wrong model instead of concatenating those into a false pass. Fixtures retain a failure for a genuine token mismatch and an old-parent token. The raw protocol harness does not certify rendering in the UI. A read-only trace found a separate conditional presentation hypothesis: an unseen old assistant appended after a new user could be grouped by display order. This was not reproduced, and cannot explain the original raw-HTTP harness mismatch; it is not a claimed backend defect or fix.

## Ownership and interruption recovery

Each title includes run, engine, scenario and session ordinal. A durable intent is written before creating a session; every returned ID is immediately journaled. Cleanup checks exact project directory plus owned title/ID before deleting, and removes only this harness's rows. Interrupted creation can be recovered by bounded discovery within the recorded run/scenario scope. Archived cleanup requires explicitly recorded IDs plus exact legacy directory/title, never a blanket title search. Failed cleanup retains its journal and invalidates all current-run passes before matrix generation. Schema2 adds a required final cleanup acknowledgment, which the independent Python validator enforces.

## Repeat the final run

Run from the recorded committed candidate. The parent takes the shared lock separately for each phase and history/cleanup job:

```sh
DART=/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/cache/dart-sdk/bin/dart
"$DART" tool/qa/fq3_certify.dart --run-id fq3-20261008b-cert \
  --app-managed-engine opencode2 \
  --oc1-model zai-coding-plan/glm-5.3 --oc2-model opencode/big-pickle
python3 tool/agents/generate_certification.py
```

The app-managed-only OC2 check refuses another engine at4097. The OC1 auxiliary server runs under the application UID in the app's installed Ubuntu. Both CLI and HTTP pins, app build/UID, attempt, model scope, phase/session manifest and final cleanup must agree. Actual unavailable capabilities remain failures. The generated protocol namespace stays separate from the existing app/tool certification cells.

Final results and verification will be appended after the committed-candidate run. No new APK, application code, provider enrollment, full-suite claim, publication or push is part of this lane.

## Local checks before candidate freeze

Pinned Flutter3.47.1; `tool/qa/machine_lock.sh test -- flutter test --concurrency=1` across all ten FQ3/agent-certification files:132tests passed. Independent Python matrix validator:23tests passed. Six guard-removal proofs are retained as `red-*.txt`; each expected failure restored its source immediately. The four affected Dart files were then rerun serially:46tests passed. Full pinned `flutter analyze`:clean, no issues. This is focused validation; the coordinator owns the integration full-suite gate.
