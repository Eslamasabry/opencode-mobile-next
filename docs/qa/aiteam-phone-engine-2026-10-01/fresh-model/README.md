# Fresh-profile model and failed-planner recovery

Finish line: with fresh app data and only the GLM-5.3 key added, unconfigured roles resolve a usable server model and create → approve spec → plan reaches the real plan card; absent usable defaults return modelNotConfigured before a planner session exists; terminal failed planning exposes an explicit authenticated retry.

Non-goal: UI edits, selecting a team/role model as a QA workaround, provider credential logging, replaying an old prompt, changing canonical-main authority, physical-device claims, push or publication.

Independent video README read fully. Previous QA used populated role settings and missed the fresh-profile inheritance path. Root owns daemon integration and focused serial checks/device QA; independent workers own OC1 model resolver, native retry command, and domain retry presentation.

Fresh baseline reproduced through real app UI on supplied APK 2085 after `pm clear`, reinstalling the phone Linux environment and adding only the existing ZAI Coding Plan Global key through the masked provider field. No team/role/default model changes and no chat. `/config.model` was absent; `/provider.default` selected `glm-5.3-highspeed`. The actual planner assistant used that model and failed HTTP 429 with a subscription-access refusal (safe projection in baseline-assistant.json), producing `interrupted/sessionFailed` and “Stopped unexpectedly” without a retry action. Thus model omission itself is supported by OC1; the fresh default selected a subscription-restricted variant.

Fix uses the connected configured default when usable; for omitted ZAI Coding Plan choice, it selects ordinary GLM-5.3, which [ZAI lists for every plan](https://docs.z.ai/devpack/overview#supported-models). Explicit configured/role choices remain authored. Runtime catalog membership is not entitlement or quota proof and no undocumented provider endpoint is used. Empty-role resolution and immutable per-role checkpoints prevent accidental server-default switching during recovery. Absent candidates return modelNotConfigured before any queue/session mutation.

The new retryPlan command preserves the terminal failed job and approved spec, queues one new planner with current roles, and advertises the existing domain failure editor. Unknown or possibly running dispatches require reconciliation; no old prompt is replayed. UI ownership remained with Claude.

Local gates at source 949469a6 + resolver policy 731db78b: cargo fmt/diff check clean; locked full Rust suite including ignored boundary controls passed **228**, zero failures/ignored. Six focused phone Dart files passed **94** serial; pinned Flutter analyze clean. Machine-heavy checks serialized through machine_lock with OC_TEST_SLOTS=1; emulator stopped. Initial native test compilation ran out of Storage before tests (not counted as a gate); cargo clean removed only this slice’s disposable dev/test artifacts, then debug-free native test compilation passed.

Rust command: CARGO_INCREMENTAL=0 CARGO_PROFILE_DEV_DEBUG=0 CARGO_PROFILE_TEST_DEBUG=0 CARGO_BUILD_JOBS=2 OC_PHONE_PROOF_ROOT=/home/owner/Storage/tmp/aiteam-phone-engine-build/proofs CARGO_TARGET_DIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/target TMPDIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/tmp OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- cargo test --manifest-path engine/phone/Cargo.toml --locked -- --test-threads=1 --include-ignored.

Dart files: phone_engine_status_presentation_test, phone_project_engine_gateway_test, phone_project_engine_controller_test, builtin_phone_engine_test, phone_chat_admission_recovery_test, phone_engine_connection_review_test. No repository-wide Flutter suite claim.

Dual-ABI native package commit **19ba98a0**, source **949469a6**, source digest **88a390ce9012c61a806e898362174efb3f923dc007e218f6d10baa45d4117a0b**. SourceDirty in manifest is true because QA report/screenshots were untracked at packaging; verify_phone_engine_apk rehashed the exact crate sources successfully. QA composition uses coordinator UI ae579349 and backend 19ba98a0 in root-owned qa/aiteam-run4-ui; coordinator worktree was read-only. Local QA release-mode APK (Android debug signer matching the installed emulator build) SHA-256 **3b1f596cb1031fe8793e10b64bf6bd258f11ae245f41f281bdd55e3388a89a2f**, version 2085. Gradle assembleRelease passed; verify_phone_engine_apk verified every native library in both ABIs and the source digest. No production signing key, CI, push or publication.

Update-over-baseline check: updated native package reached Ready (0s reply, 24s stop, 4s device proof, 15s protected restart). Original terminal sessionFailed job and its dispatched session remained intact. Backend advertises retryPlan and domain maps it to planFailed, but coordinator UI ae579349 only renders its project failure notice for failed and offers no route into the existing planFailed editor. Thus live retry-button verification is **UI handoff pending**, not claimed PASS. Exact hook-up recorded in the engine design contract; UI files untouched. Fresh create → plan acceptance proceeds separately.

Final device acceptance on **OC_API35 API 35 x86_64, memory 3072, port 5554**, using only `adb -s emulator-5554` under the shared emulator flock:

| Step | Result | Evidence |
| --- | --- | --- |
| Fresh `pm clear`, On this phone → setup → first project | PASS | Rootfs/OpenCode reinstalled, no retained role defaults or chat history |
| Add only existing ZAI Coding Plan Global key in masked provider field | PASS | Connected 7 models; no provider/role/team/server model selected |
| Seed scratch repository through the app terminal | PASS | Empty initial commit on master; no task commands through a harness |
| Turn on → confirm stop → phone check → protected restart → Ready | PASS | 0s reply + 11s stop + 5s proof + 9s restart; fresh-ready.png |
| New project → single lane → No limit → Start planning | PASS | Real UI created draft spec |
| Open spec → add Greeting milestone/criterion → Approve spec | PASS | Accepted once, one planner session |
| Planner uses ordinary GLM-5.3 and completes | PASS | Empty authored role model, immutable resolvedModels planner=zai-coding-plan/glm-5.3, actual assistant metadata has finish=stop and no error; final-plan-safe.json |
| Plan card → Review plan | PASS | “Plan ready to review”, one proposed task/phase; fresh-plan-card.png and fresh-plan-editor.png |
| No usable candidate → modelNotConfigured before queue/session | PASS (regression) | Native command preflight test proves workspace unchanged, no jobs and no HTTP mutations |
| Failed-planner retry admission/idempotency/ambiguity guards | PASS (regression + live advertisement) | Seven store retry regressions, Dart command/revision/capability checks; device health advertises retryPlan |
| Failed-planner retry button through current UI | UI HANDOFF PENDING | ae579349 does not route planFailed into failure notice/editor; retry-ui-handoff.png and engine design contract |

Final server config.model remains absent even though provider.default still nominates highspeed (final-default-safe.json); all Planner/Worker/Checker model and fallbackModel values remain empty. No initial personal chat turn/model selection supplied a hidden fallback. Device health confirms execution, boundary and OC1 enabled and tier=proot (OC2=false); signed receipt bound to the exact x86_64 packaged engine hash confirms canonical/proc paths denied and hygiene controls. Proot is ptrace-based path translation, **not a kernel boundary**; nativeAttacksDenied=false is recorded honestly. No physical-phone/ARM64 runtime or API34 claim. Only the requested create → plan journey was run in this fresh-profile slice; worker/checker/merge/promotion acceptance belongs to the prior run5 evidence.

Screenshots and evidence are filtered projections; no raw config/provider/auth response or credential persisted to the repo. Temporary host QA key is removed during cleanup.

Supplied APK 2085 restored over the fresh QA data after verification; installed base APK hash matches supplied artifact **271aa81f57012815eefb42a2fe5cbe8ea01429de17e99819f3eaecac3f9abc66**. The fresh project is left at its unapproved plan card; no worker or main write was triggered in this slice.

Cleanup verified: exact owned emulator PID 1217153 exited; both read-only adb forwards removed; shared emulator lock released and independently acquired with flock -n; temporary host credential file (0600, directory 0700) deleted. No tests/builds were run with the owned emulator active. Backend analyze is the clean gate above; a separate composed-worktree analyzer request waited for the coordinator’s full-suite slot and was cancelled before it started, so no composed analyze pass is claimed. Composed release-mode Dart/Kotlin/Android compilation passed.

Committed backend source, tests, both ABI bundles, the copied video report and this evidence on build/aiteam-phone-engine with skip-ci commits. No lib/ui files edited, no push. The remaining owner/UI follow-up is the planFailed → failure notice/editor route; retryPlan is implemented, authenticated, advertised and regression-tested.
