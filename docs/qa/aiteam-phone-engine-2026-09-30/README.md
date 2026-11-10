> Latest critical/major review gate and bundle hashes: [review closure](review-closure/README.md). Earlier evidence below is historical.

# Phone project engine — first slice

Finish line: a phone-resident native Rust daemon persists project commands and task stages, drives planner → worker → checker sessions on one pinned OC1 server using isolated worker clones, merges checked changes into private canonical dev, and permits confirmed expected-SHA promotion only through the authenticated API and a proven filesystem boundary.

Non-goal: UI changes, OC2 execution, multi-host migration, signing, publishing, unbounded Android lifetime, or per-session CPU/IO priority claims.

**Latest follow-up:** [x86_64 and live acceptance handoff](x86-acceptance/README.md)
records dual-ABI packaging, the release preview runner, startup/recovery review
fixes and current focused results. ARM64-only statements and earlier counts
below describe the preceding candidate.

The coordinator now requires focused engine and Dart proofs before merge. Local verification is recorded below; phone runtime proof is performed at each native startup and no device pass is inferred from host checks. Build/test artifacts use Storage.

## Ownership and frozen contracts

- Store slice owns `engine/phone/src/store.rs`, `scheduler.rs`, store tests and its QA README.
- Git slice owns `engine/phone/src/repository.rs`, git proof tests and its QA README.
- Native slice owns both halves of the builtin MethodChannel, Kotlin lifecycle/launcher, `engine/phone/src/bin/oc-engine-sandbox.rs`, boundary proof and its QA README.
- Protocol slice owns `opencode.rs`, protocol evidence/tests and its README.
- Gateway slice owns the additive domain/Dart adapter/profile wiring, deletion and its tests/README.
- Coordinator owns Cargo/package/build integration, config/HTTP/reconcile pipeline, cross-slice integration fixes and the integration README. No UI is edited. Each execution slice has its own Storage worktree and local `[skip ci]` commits.

Store interface: `Store::open(root: &Path, profile: &str) -> Result<Store, StoreError>`; `workspace() -> Result<Value, StoreError>`; `execute(&Value) -> Result<Value, StoreError>` matching `TeamProjectCommand` and `TeamCommandResult`; `events(after: i64, limit: usize) -> Result<Vec<Value>, StoreError>`; `delete_profile() -> Result<(), StoreError>`; `jobs() -> Result<Vec<Value>, StoreError>`; `update_job(id: &str, expected_stage: &str, patch: &Value) -> Result<Value, StoreError>`; `recover() -> Result<(), StoreError>`. StoreError has public `code() -> &str`, no raw SQL/path/payload exposure. State uses `oc.teamEngine.<profile>` subdirectory and transactionally persists idempotent commands, project revisions, job stages, usage, receipts and event metadata. Running stages recover as `interrupted`, never automatically resubmitted. Jobs hold task/project/repo ids, role/model, criteria, dependency ids, stage, directory, session ids and expected commits.

Repository interface: `RepositoryAuthority::new(private_root: PathBuf, worker_root: PathBuf) -> Result<Self, RepoError>`; `import_repo(repo_id: &str, source: &Path) -> Result<Value, RepoError>`; `prepare_worker(repo_id: &str, task_id: &str) -> Result<Value, RepoError>`; `collect_worker(repo_id: &str, task_id: &str, expected_dev: &str) -> Result<Value, RepoError>`; `merge_dev(repo_id: &str, task_id: &str, expected_dev: &str, expected_task: &str) -> Result<Value, RepoError>`; `promote(repo_id: &str, expected_dev: &str, expected_main: &str, confirmed: bool, request_id: &str) -> Result<Value, RepoError>`; `refs(repo_id: &str) -> Result<Value, RepoError>`. RepoError exposes only `code() -> &str`. Canonical roots must never overlap worker roots; untrusted paths/symlinks/local origins cannot reach protected state. libgit2 only, no shell git in daemon. Mutations return before/after refs; promotion is idempotent, binds exact dev/main and is crash-reconcilable. No force refs or force pushes. Worker hooks are defense in depth, not authority.

HTTP contract v1: every endpoint requires Bearer auth; loopback bind only; `GET /v1/health`, `GET /v1/workspace`, `POST /v1/commands` (TeamProjectCommand JSON), `POST /v1/chatBusy` (sequenced app lease), `GET /v1/events?after=<seq>&limit=<n>` (metadata array), `DELETE /v1/profile`. Health contains schemaVersion=1, engineVersion, profileId, capabilities (execution/boundary/oc1Verified/oc2) and commandActions array. Responses never contain tokens, provider credentials, SQL/native errors or raw server output. Missing proof => execution unavailable. `close()` releases app resources without stopping daemon; `deleteLocalData()` drains client writes and calls durable engine deletion. No command auto-retry after ambiguous transport failure.


## Integrated outcome and remaining device acceptance

App-authoritative heartbeat and signed runtime proof supersede the previous
unavailable global-idle gate. No UI or main.dart changed. The additive contract is
[the engine handoff](../../design/aiteam-inapp-engine-2026-09-30.md).

- A native generation enables canonical import only after complete isolated
  controls and verified exact-bundle receipt. Lanes/promotion additionally require
  the pinned OC1 driver. A missing/changed receipt or unsupported kernel stays closed.
- The app pushes bounded idle/busy leases through an authenticated endpoint;
  actual prompt/shell/slash dispatch waits for busy acknowledgment. Missing or
  expired evidence while the parent is alive is unknown. Whole-parent death
  normally stops the daemon; absence fallback needs global SSE and strict known
  directory polling. Another client's unseen-directory/racing reply remains a
  documented scope gap, not a promise of complete global priority.
- Durable confinement survives app restart/update/profile deletion. Existing
  legacy server/terminal or uncertain inventory requires owner intervention;
  nothing kills the person's chat automatically. Startup re-runs controls after
  updates and each new daemon generation, not just when a mutable flag changes.
- Interrupted sessions retain uncertain checkpoints; no replacement prompt is
  resent automatically. Full automatic resume of every checkpoint, charging
  telemetry, daily-cost attribution and a native project test runner are not
  established. Unknown requirements pause honestly. OC2 and advanced fix/conflict/
  manual-merge/migration commands remain unavailable.

## Executed checks

Current candidate: `52159d53` plus the coordinator integration diff committed
with this README. The packaged manifest records the exact Cargo/src digest;
`sourceRevision` is the pre-commit snapshot and `sourceDirty=true` is intentional.
All heavy commands used the machine lock, pinned toolchain and Storage artifacts.

| Check | Result |
| --- | --- |
| `cargo test --locked -- --include-ignored --test-threads=1` | **68 passed, 0 failed, 0 ignored** including actual raw Linux syscall/proc/metadata/namespace/descendant controls and real protected Git fixture |
| Three requested Dart files, pinned `flutter test --no-pub --concurrency=1` | **33 passed**; gateway, controller, native bridge |
| Existing `connection_status_test.dart` regression | **7 passed**; combined serial final run **40 passed** |
| Pinned `flutter analyze --no-pub` | Passed, no issues; merged heartbeat into the existing notifier, preserving clock/widget behavior |
| ARM64 Android release cross-build/stage, NDK 28.2.13676358/API 26 | Passed for daemon, launcher and real Git fixture probe; updated dependency notices and SHA/source manifest |
| Gradle `:app:compileReleaseKotlin`, JDK 17 | Passed, including release Flutter compilation; fixed unavailable Android `Os.unlink` to the SDK-supported `Os.remove`; existing Gradle/Kotlin warnings remain |
| Rust/Dart format, packaged source/binary hashes and Git whitespace/ownership | Passed; manifest matches exact current Rust source and all three ELF files; no UI/main.dart/generated SDK edits |
| Full repository Flutter suite, real provider-backed task, Android kernel/proot/device acceptance | Not run in this focused gate; no device pass claimed |
| APK signing/install/release, push/PR/CI | Not performed |

The first focused runs found and fixed: libgit2 leaving a canonical pathname in
worker `FETCH_HEAD`; an incomplete workflow fixture's required settings; stale
chat latch test expectations; duplicate notifier integration (the existing clock/widget notifier is preserved);
and two boundary-control assumptions. Landlock
REFER link denial is `EXDEV`; public namespace-handle acquisition is not a secret
read, so the proof instead requires actual `setns`/`unshare` authority to fail and
retains all private-file/process denials. The policy also closes the new mount
syscalls. A real canonical fixture makes proot Git attack controls meaningful;
missing `.git` is never proof. Ordinary worker writes and rename still succeed.

Rust tests cover signed exact-byte receipt verification, generation/boot/parent/
policy/hash binding and live revalidation; API authentication and capability
closure on receipt replacement; heartbeat ordering/expiry/busy/unknown; durable
store, scheduler caps, Git authority and a component task/check/dev/promotion
workflow. Dart tests cover idle and busy renewal, actual OC1 transport dispatch,
strict ACK, uncertain/overlapping turns, alias fences, stop failure/retry, deletion
and native status. No provider credentials or live model were used.

Earlier ddf3a6fe compile/analyze results and Phase A suite results are historical,
not coverage of this candidate. Host boundary controls prove this Linux host only.
Device startup independently requires native + real proot/Git controls for the
exact packaged binary bundle before it signs/enables anything. Dependency notices
include the newly added P256 signing-verification crates. Native artifacts are
ARM64 PIE with the Android linker; other ABIs fail unavailable.

## Owner steps on the phone after coordinator integration

1. Use the existing installed/authenticated phone Ubuntu/OC1 setup. No computer or
   host service is required. Finish the current reply, explicitly stop the old
   built-in OpenCode server/services and close Ubuntu terminal sessions before
   the first engine proof. Stop/restart a prior blocked store-only engine to rerun
   its startup proof. A failed/uncertain inventory reports restart required.
2. Start the phone engine through the coordinator's app UI/controller. It runs
   isolated controls automatically; no token/signature/config editing is needed.
   A failed kernel/proot/control/signature keeps canonical import, promotion and
   lanes off and reports a safe reason. Do not treat host tests as a bypass.
3. After a successful proof, start/connect the ordinary built-in OC1 server;
   launches are automatically confined by the durable marker. Probe/readiness
   refetch confirms the pinned driver. A fresh known app heartbeat then permits
   a reviewed single task (spec → plan approval → worker → checker → dev merge).
   Confirm expected refs to promote. Busy/unknown chat waits.
4. App updates/new daemon generations re-run the proof automatically. The
   foreground-service cap can stop work; uncertain checkpoints require honest
   reconciliation/review. Receipt verification is not a lifetime exemption.

The owner need not run a host daemon or manually generate promotion credentials.
No replacement APK is delivered by this branch; installation/signing and the
combined UI journey remain the coordinator's separate authorized step.

## Repeatable focused checks and remaining phone acceptance

Use pinned Flutter and machine locks. Keep test scratch/build outputs on Storage;
run commands sequentially. These are the focused merge gates requested by the coordinator. The full
unchanged-candidate app suite remains a separate integration boundary:

```bash
OC_TEST_SLOTS=1 \
TMPDIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/tmp \
CARGO_TARGET_DIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/target \
OC_ENGINE_TEST_ROOT=/home/owner/Storage/tmp/aiteam-phone-engine-tests \
OC_PHONE_PROOF_ROOT=/home/owner/Storage/tmp/aiteam-phone-boundary-tests \
tool/qa/machine_lock.sh test -- \
  cargo test --manifest-path engine/phone/Cargo.toml --locked -- --include-ignored --test-threads=1

OC_TEST_SLOTS=1 \
TMPDIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/tmp \
tool/qa/machine_lock.sh test -- \
  /home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency=1 test/phone_project_engine_gateway_test.dart \
  test/phone_project_engine_controller_test.dart test/builtin_phone_engine_test.dart
```

The task_workflow Rust test is a **component fixture**: scripted session IDs/JSON,
real independent Git clone/commit/check binding/dev merge/promotion receipt,
reopen and deletion. It makes no OpenCode/model request and is not a live-session
proof. HTTP tests separately cover authentication, capability refusals and durable
deletion. Store/scheduler and Git attack tests are described in
[store QA](../aiteam-phone-store-2026-09-30/README.md) and
[Git QA](../aiteam-phone-git-2026-09-30/README.md); Dart lifecycle/deletion tests in
[gateway QA](../aiteam-phone-gateway-2026-09-30/README.md).

The optional raw Linux boundary test and automatic native startup probe require
isolated fixtures and real kernel positive/negative controls. Follow native QA;
a successful ABI query or harness summary alone does not enable production.
Later device acceptance must also prove exact packaged hashes, cold first start,
UI closure, bounded foreground lifetime, interrupted session reconciliation,
profile deletion and confirmed expected-SHA promotion without an accessible
canonical path through legacy server/PTY/process entry points. Runtime authority
is enabled only after those exact device controls pass. A real
approved one-task plan/session/check/dev journey then establishes phone product
acceptance; local component fixtures do not substitute for it.
