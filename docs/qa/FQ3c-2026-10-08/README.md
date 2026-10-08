# FQ3c: five OC2 failures

Branch `sol/bc-fq3c`, base `feat/genui-fe` f0e33d96d407ff68782b49702e88269d197ae96e. [Backend and coordinator copy contract](../../design/BC-FQ3c-contract.md).

Finish line: classify the five OC2 failures, repair proven defects with regressions, and record cleaned, model-scoped device results on the coordinator-posted normal APK 2197. No UI/localization edits, provider enrollment, credential copying, app-data reset or push. Device work is pending the coordinator's APK 2197 post; no new device qualification is claimed here.

## Source classification and repairs

| Assertion | Current classification | Repair / required device evidence |
|---|---|---|
| Model switch | No app payload defect demonstrated. Server/model availability is suspected; a missing selection event is a distinct harness observation failure. | Record the actual alternative and stage; preserve selection event, HTTP retention and completed exact-model reply checks. |
| Permission Allow | Proven harness mismatch: stable tool/action is `shell`, while the probe assumed `bash`. No app transport/parser defect demonstrated. | Stable shell Ask and exact command; require own request/reply, matching tool success/output, no pending request and idle state. Saved user grants are preserved and may affect whether Ask occurs. |
| Permission Deny | Proven harness tool mismatch plus wrong successful-execution expectation. Stable plain Reject aborts the tool and interrupts execution. | Match the rejected call, its aborted error, interrupted terminal, no success/progress/command start, cleared pending state and idle settlement. Interruption alone cannot pass. |
| Image | No app attachment payload defect demonstrated. Prior catalog order suggests unavailable `exo-free`, but old phase reports did not retain actual model/stage. | Record selected vision model, retries and terminal stage; require the real PNG and exact completed-model semantic answer. No fallback or relaxed image assertion. |
| Cards | Proven harness false negative: `executed` describes provider execution, so a completed local MCP call may have `executed:false`. The app mapper already handles this correctly. | Check connected `oc-ui` in exact-location inventory; use a session-only display-tool Allow rule; require fresh matching tool-success plus retained input/output, then actual receipt and semantic acknowledgment. |

Detailed version-matched primary-source notes: [model/image](model-image-classification.md), [permissions](permission-classification.md), [cards](cards-classification.md). These distinguish proven contract mismatches from hypotheses about the old runtime failures. No real application backend defect has been reproduced, so no app-source fix or fabricated app regression is included.

The harness now emits `observations` separately from qualification facts. Only fixed stages, validated public model references, bounded event counts and a cache-truncation boolean are exported. Counts use fresh event identity rather than a shifting index into the rolling SSE cache. They describe retained events; a saturated window may omit older events, and diagnostic zero counts must not be treated as proof of complete absence. These observations cannot grant a capability pass. Raw errors, provider configurations, replies and credentials remain in memory.

## Regression evidence

Pre-fix failures: `red-permission-shell.txt`, `red-deny-terminal.txt`, `red-local-mcp-card.txt`, `red-mcp-inventory-scope.txt`, `red-observation-cache.txt`, `red-build-dart.txt`, and `red-build-python.txt`. The observation location guard also has a guard-removal counterexample, `red-observation-location.txt`; its source was restored exactly before the final checks. Permission fixtures reject unrelated sessions/requests/calls, fabricated interrupted outcomes, retained pending/active state and execution after rejection. Cards fixtures reject unavailable inventory, wrong location, stale/foreign call success, malformed/incomplete output and missing receipt.

The current runner is pinned to 2197 and `docs/qa/FQ3c-2026-10-08/`. Historical schema-1 reports and build-2196 schema-2 reports are still accepted unchanged. A build-2197 report cannot use schema 1 to bypass final cleanup. Existing matrix/snapshot and older FQ3/FQ3b evidence remain unchanged until device evidence is collected.

## Device rerun after APK 2197 is posted

Verify the coordinator-posted APK's package/build and known same signer before using it. Device phases prepare and restore the approved normal app inside the shared lock, preserve data, generate unique run/engine/scenario/ordinal titles, and clean exact owned sessions. Do not uninstall or clear data. A nonzero phase process exit may indicate cleanup/restoration failure; retain its journal and stop rather than running another phase over it.

The focused five-phase rerun uses the existing standalone phase interface. Choose a new run ID, frozen source revision and unique attempt for each run; pass the same source/attempt across these five calls:

```sh
DART=/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/cache/dart-sdk/bin/dart
# FQ3_RUN, FQ3_REVISION and FQ3_ATTEMPT are recorded per-run identifiers.
for FQ3_CASE in model allow deny image cards; do
  flock /home/eslam/Storage/tmp/oc-emulator.lock "$DART" tool/qa/fq3_certify.dart \
    --phase --engine opencode2 --case "$FQ3_CASE" \
    --run-id "$FQ3_RUN" --revision "$FQ3_REVISION" --attempt "$FQ3_ATTEMPT" \
    --model opencode/big-pickle || break
done
```

Each completed standalone phase cleans its sessions before writing evidence. Actual assertion failures remain failed even when the phase process exits successfully. A focused five-phase run does not replace full dual-engine/history certification or combine earlier build-2196 passes into a build-2197 all-capabilities claim. The complete parent runner remains available for a later integration qualification.

## Local verification and shipping state

The frozen host candidate passed 185 focused Dart tests across 12 affected files, run serially with pinned Flutter 3.47.1 through `tool/qa/machine_lock.sh`; the matrix validator passed 26 Python tests. Full pinned `flutter analyze` is clean. `git diff --check`, the generated certification snapshot check, historical evidence schema validation, local document links and application-source/archive preservation checks also passed. [Machine-readable host validation](host-validation.json) records the scope.

Implemented harness repairs and source classifications are committed locally; the commit is recorded in `BC-status.md`. APK 2197 has not been posted and no device phase ran in this slice. Model-switch and image availability hypotheses therefore remain unverified. No new matrix qualification or application backend fix is claimed. No full repository suite, native build, APK signing, publication or push is part of this slice.
