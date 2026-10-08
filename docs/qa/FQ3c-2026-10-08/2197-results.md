# APK 2197: five OC2 reruns and bounded follow-ups

The latest targeted checks pass permission Allow, permission Deny and the real
cards tool/answer round trip. Model switch and image remain failed: their selected
`opencode/exo-free` produced ten HTTP 503 retries without a usable reply.
No application backend defect was reproduced; changes are confined to QA.

All device attempts used the app-managed OC2 server, version 2.0.10, app 2197,
UID 10217 and explicit `opencode/big-pickle` as the starting model. The model and
image scenarios independently selected `exo-free`. Each phase held the shared
emulator lock, used distinct owned titles, deleted its exact sessions and verified
normal 2197 before releasing the lock. No global grants, provider credentials,
Claude sign-in state, engine choice or app data were changed. No APK was built.

## Outcomes and classification

| Capability | Latest observed result | Evidence and classification |
|---|---|---|
| Model switch | Failed | [Phase](fq3-20261008c-2197-b-opencode2-model.json): selection event and HTTP retention succeeded; `exo-free` had ten HTTP 503 retries and an execution failure, no text delta. **Server/model inference failure**, no app selection defect shown. |
| Image | Failed | [Phase](fq3-20261008c-2197-b-opencode2-image.json): selected vision model `exo-free`, ten HTTP 503 retries, no text delta or semantic answer before the deadline. **Server/model inference failure**; this phase does not independently attest retained PNG bytes. No attachment bug is demonstrated. |
| Permission Allow | Passed after QA repair | [Confirmed pass](fq3-20261008c-2197-output-fixed-opencode2-allow.json) proves own ask/reply, same-command tool success, actual stdout, zero exit and idle settlement. **Proven harness contract mismatch**, repaired below. [Initial timeout](fq3-20261008c-2197-b-opencode2-allow.json) occurred before any ask/terminal; that particular stalled turn's reason remains unproven. |
| Permission Deny | Passed | [Phase](fq3-20261008c-2197-b-opencode2-deny.json): owned ask/reply, aborted tool failure, interrupted execution, no successful/progress/command-start evidence after rejection, no pending request and idle. **Earlier harness tool/terminal mismatch**, repaired in FQ3c; no app bug shown. |
| Cards | Passed on bounded follow-up | [Confirmed pass](fq3-20261008c-2197-card-shape-opencode2-cards.json): connected exact-location helper, one fresh real successful show call, retained valid top-level confirmation input, real tagged receipt and semantic acknowledgment. [Earlier follow-up](fq3-20261008c-2197-output-fixed-opencode2-cards.json) proves a real helper rejection, not a missing transport call. **Server/model/helper execution rejection** on those attempts; the exact rejected field or contemporaneous marker state was not captured and is not asserted. No backend parser bug was reproduced. |

The [marker check](2197-card-marker-check.json) observed enabled bytes, and
the [final check](2197-final-device-check.json) confirmed a regular marker file.
These separate observations do not reconstruct marker state at an earlier call.
The successful follow-up changes diagnostic collection only; it does not change
the prompt, enable a marker, relax the card predicate or waive the answer receipt.
The prior helper rejections remain failed attempts rather than being relabeled.

## Proven Allow harness repair

The [confirming failing phase](fq3-20261008c-2197-allow-confirm-opencode2-allow.json)
had a genuine request/reply, tool success and execution success, then failed
`permission_command_output_missing`. The pinned stable
[ShellTool](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/tool/plugin/shell.ts)
returns stdout and a separate exit notice; the
[notice and metadata producer](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/shell/result.ts)
preserves the command's exit status. Joining both text items and comparing the
combined text to the marker was a false negative.

`oc2ShellOutputVerified` now checks the distinct stdout item, exact exit-zero
notice and metadata (zero exit, no truncation/timeout, completed state). Own
request, reply, source, command, fresh matching success and idle checks remain.
Stable fixtures were updated to this version-matched output shape, rather than
the old synthetic stdout-only result. Nonzero exits, timeout, truncation,
missing/fake stdout, extra output and foreign outcomes remain rejected.
[Behavioral red proof](2197-shell-output-red.txt) precedes the fix.

Other QA repairs: exact package identity amid Android's prefix-matching
preview/test results ([red proof](2197-package-red.txt)); owned global events
with omitted location ([red proof](2197-observation-red.txt)); fixed provider
and card rejection/input counters ([red proof](2197-card-diagnostic-red.txt)).
Raw provider errors, transcripts, tool arguments, passwords and configurations
are never exported by diagnostics.

## Provenance, cleanup and limits

The [five-phase original batch](fq3-20261008c-2197-b-summary.json) is generated
by [summarize_partial.dart](../../../tool/qa/fq3/summarize_partial.dart), using
the existing strict phase validator. It deliberately retains one pass and four
failures at source `6dc4e516486def7020281338eeaa75cf3e1a6d8e`, attempt
`attempt-2197-b`. A wrong-revision summary invocation refused with exit 1.
The Allow repair was confirmed separately at
`5e0f6546531f5f224587415aea0d7d0a29b8b58e`; the final cards follow-up used
`c12fe7c7982aa7428b17e296ebcd3cc068154b77`. Each linked file retains its own
revision/attempt. This table is **not** one combined full certification run.

An initial [short-revision preflight](fq3-20261008c-2197-a-opencode2-model.json)
was refused by the ownership validator before any session was created. It is
excluded from capability results. No artifact was edited to replace a failure.

[Artifact preflight](2197-artifact-preflight.json) and
[final device check](2197-final-device-check.json) verified exact installed
normal 2197 bytes, posted hash, same local signer and unchanged UID. Normal
2197 was already exact, so it was retained without restarting the server.
Restoration, when needed, now uses `adb install -r`, without clearing data.
Every executed phase has `cleanupCode: null`; no owned journal remains.

The historical 2196 certification matrix and application capability gates remain
unchanged. No earlier passes were imported into 2197. These checks do not prove
the OC1/full dual-engine/history journey, UI rendering, physical-device behavior,
provider enrollment, release readiness or FQ9's pending long-duration gates.

Focused verification: 90 Python FQ9 tests; 92 final affected Dart tests (OC2
adapter, permissions and diagnostics); the two device-identity Dart tests also
passed at the initial checkpoint. Serial `machine_lock`, pinned Flutter 3.47.1,
clean focused analyzers, formatting, JSON/link and diff checks. No full repository
suite, Gradle/Kotlin job, APK build, signing, push or publication was run.

Coordinator copy contract: model selection/prompt admission do not promise a
reply; the tested alternative/vision model failed at inference. Allow/Reject are
verified for the tested runtime/model. A rejected card call is not evidence of a
broken tagged-answer channel; that channel subsequently completed. Leave UI copy
and capability promotion with the coordinator; no product strings changed.

SEQUENCE COMPLETE: FQ3c device reruns and classification.
