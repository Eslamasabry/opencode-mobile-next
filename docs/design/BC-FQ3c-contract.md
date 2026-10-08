# FQ3c OC2 backend diagnosis contract

Base: `feat/genui-fe` f0e33d96d407ff68782b49702e88269d197ae96e. Branch: `sol/bc-fq3c`.

Finish line: classify all five remaining OC2 assertions from current app/harness/server contracts, fix reproduced backend defects with failing tests first, and record a cleaned, model-scoped certification run on coordinator-posted APK 2197.

Non-goal: UI or localization edits, provider enrollment, credential copying, server implementation changes, silent model fallback, new APK builds, pushes, or device jobs before APK 2197 is posted.

## Ownership and checks

Root owns the entire `lib/api2/` protocol cluster, domain/state integration if a proven defect requires it, the shared FQ3 adapters/orchestrator/device configuration, all heavy checks and emulator jobs, report generation, and local commits. BA-owned `phone_agents*.dart` remains outside this lane.

Three independent initial inspections own only their classification notes: model switch plus images; permission allow/deny; cards tool round trip. They read the relevant contract and harness/app path, propose a minimal reproducer, and do not edit shared libraries or launch tests/device processes. Public methods and fixed error codes will be recorded here before any parallel implementation; there are no new product APIs at this stage.

Use the pinned SDK and serial focused tests through `tool/qa/machine_lock.sh`. Device checks use `/home/eslam/Storage/tmp/oc-emulator.lock`, run-specific titles and durable exact-scope ownership cleanup. Read the runtime launch credential only through the established app-UID mechanism; never export credentials, provider configuration or raw transcripts. Preserve app data. Prepare/restore the approved normal APK within the lock after the coordinator posts 2197 and its signer/path. Failed assertions stay failed; classification must distinguish observed facts from hypotheses.

## Frozen harness revision contract

Current evidence lives in `docs/qa/FQ3c-2026-10-08/` and is bound to app build 2197. Existing schema 1 FQ3 archives and schema 2 build-2196 FQ3b evidence must remain accepted unchanged; schema 2 build/directory pairs are exact, and a build-2197 report cannot bypass cleanup acknowledgment through schema 1. `runProtocol2(Fq3Wire, ProbeOptions)` and `PhoneRuntime.inspect/start/restoreNormalApp` signatures stay unchanged. The normal-app restoration remains constrained to the coordinator-posted package/build/signer; it does not migrate user settings or clear data.

Root owns device and adapter changes. Once its cards inspection is complete, the third inspector may own only metadata migration (`evidence.dart`, `session_ownership.dart`, `update_matrix.py`, driver evidence-directory constant, public comparison output directory) and affected fixture tests. Its acceptance requires historical evidence preservation, current-build mismatch refusal, mandatory final cleanup, and no new capability passes from metadata changes.

## Pending classification

| Assertion | Previous result | Classification | Required evidence |
|---|---|---|---|
| model switch | timeout | pending | selected alternative, app versus harness request, completed reply/model |
| permission allow | timeout | pending | effective ask rule, tool invocation, pending request, reply and outcome |
| permission deny | timeout | pending | effective ask rule, tool invocation, pending request, rejection and outcome |
| image | timeout | pending | supported vision model, app versus harness file payload, semantic answer |
| cards | missing oc-ui_show call | pending | runtime tool availability, real call input/output, tagged answer receipt |

## Coordinator UI contract

Pending backend findings. No copy changes are made in this lane. Capability or error changes will include method/result/error behavior and suggested plain-language meaning here; current failed certification cells do not become product capability passes from source inspection or fixture tests.

## Frozen repair and observation contracts

Pinned OC2 2.0.10 source is revision `b8cedc1a7a5e2916bbb65dc1d4b620729c261638`, verified against its CLI package version. Proven harness repairs do not change application APIs:

- Stable permission probes use `shell` in the session ask rule, prompt, asked action and retained tool name; beta probes retain `bash`. No project/global saved permissions are deleted or modified.
- Allow must prove the owned request/reply, matching command call, tool success/completed output marker and idle settlement. Stable plain Reject must prove the owned rejection, matching aborted tool failure/error, absence of same-call success/progress or command start, no pending request and idle settlement. An interrupted terminal alone is never denial proof. Beta keeps its existing terminal contract.
- Cards no longer treats the raw OC2 `executed` bit as local execution proof; that bit describes provider execution. Admission requires a completed matching retained tool and fresh owned `session.tool.success`, then the existing receipt and semantic acknowledgment. Inventory/precondition checks distinguish a missing/disconnected helper from model failure; none qualifies UI/tool capability without a real round trip.

Model/image request shapes remain unchanged until a failing app regression proves otherwise. No semantic answer, model identity, scoped event, receipt, ownership or cleanup guard is relaxed to turn an actual failed inference into a pass.

Observation helper contract: `Oc2ProbeObservation({required capability, required directory})`, `checkpoint(fixedStage)`, `selectModel(validatedPublicReference)`, and `snapshot(events, {required sessionID, required eventStart})` return only fixed stage, validated public model reference and bounded counts/booleans for events belonging to the owned session/location. Raw event/error text and transcripts never enter artifacts. Root integrates observations into phase evidence separately from qualification facts; they cannot grant passes.

Initial classifications: permissions and cards have proven harness defects; model/image have no demonstrated app payload defect, with server/model availability suspected pending a stage/model-bound run. All five retain their old failed cells until new on-device evidence exists. UI copy stays with the coordinator: permission rejection may legitimately produce an interrupted execution; that is not a claimed app crash or a successful command. Missing/unqualified cards remains unavailable; model retries and missing vision replies remain failures, not silent fallback.

No product gateway methods, arguments, results or error tags changed. Existing OC2 execution mapping already turns succeeded/interrupted into idle; plain permission Reject may carry the server's generic interruption reason `shutdown`, which alone does not establish that the server went down. Prompt admission and an enabled catalog entry do not establish successful inference. The raw tool `executed` bit describes provider execution; the app already derives local completion from authoritative tool state. Coordinator copy should reflect those meanings and existing capability gates, without exposing QA stage/error codes to product users.
