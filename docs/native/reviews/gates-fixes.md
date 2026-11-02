# Gates review fixes

2026-11-01. Reviewed checkout base: `066a00be`. The requested review is absent from that checkout; its authoritative
text was read from `3bb7632e:docs/native/reviews/gates.md`. Compared every BLOCKER and MAJOR with the current scripts
before editing. **All eleven still applied; none was already fixed or rejected.** The adjacent MINOR was also fixed.

Only `native/tool/**` and this report changed. No Kotlin contract, module, dependency, or shared owner file outside
the unit changed. INTEGRATE-W1/W2's flat core contract remains intact.

## Per-finding disposition

Test names below are methods of `native/tool/test/test_gates_review.py::GatesReviewTest`.

| Finding | Disposition and change | Regression tests |
|---|---|---|
| 1 BLOCKER: pushed objects differ from scanned objects | **Fixed.** Push exactly the resolved scanned commit to one branch destination. Reject options that can add refs or redirect the push; disable automatic tag publication and mirror/submodule behavior from configuration. | `test_GATES_01_reject_expanding_push_options`; `test_GATES_01_push_pins_scanned_commit_and_disables_follow_tags` (changes the branch immediately before Git pushes and enables follow-tags in config). |
| 2 MAJOR: private hosts bypass blocking | **Fixed.** Remove last-octet exemptions. Only explicitly enumerated canonical IPv4/IPv6 network range literals are exempt, outside URL authorities. Numeric URL paths and oversized pseudo-prefixes cannot exempt a host. | `test_GATES_02_private_hosts_are_not_range_boundaries` (synthetic probes stay in memory). |
| 3 MAJOR: forbidden-file detection misses renames | **Fixed.** Scan each commit's destination paths using NUL-delimited `diff-tree`, independently of patch content. Also recognize rename/diff headers in patch-only mode. | `test_GATES_03_forbidden_rename_and_empty_destination` (binary renames, properties file, empty file, newline in filename). |
| 4 MAJOR: unavailable merge base passes write-set/T1 | **Fixed.** Capture and check changed-file enumeration before populating arrays. Propagate failures from every Git enumeration command. Both callers return 2 when history cannot be compared. | `test_GATES_04_unrelated_base_cannot_pass_write_set_or_T1`. |
| 5 MAJOR: VPS source replacement during builds | **Fixed.** Default workspace includes the worktree name and an absolute-path digest. Acquire the global remote build lock before synchronization and retain it through every build and pull. T2 adds its exclusion lock for the complete batch. Remote commands inherit the lock owner's descriptors. Compare synchronized source digests and withhold LAST_GREEN after local tree changes. | `test_GATES_05_default_workspace_identifies_worktree`; `test_GATES_05_remote_lock_covers_source_sync`; `test_GATES_05_remote_lock_covers_result_collection`; `test_GATES_05_T2_holds_remote_exclusion_between_steps`. |
| 6 MAJOR: unsynchronized APK tree qualifies T2 | **Fixed.** Prepare each batch workspace before its first task, including the separate APK workspace. `--no-sync` requires both a matching synchronization record and a fresh remote source digest. | `test_GATES_06_T2_synchronizes_APK_workspace_before_assembly`; `test_GATES_06_no_sync_refuses_missing_stale_or_modified_source`. |
| 7 MAJOR: local builds bypass shared PC slot | **Fixed.** Local T2 and T3 instrumented Gradle steps use the same `oc_build_lock_file` as T0/T1. Lock timeout is exit 2. T2/emulator exclusion remains separate. | `test_GATES_07_T2_and_T3_obey_shared_build_slot` (held lock blocks invocation; releasing it permits both ladders). |
| 8 MAJOR: cleanup overwrites cancellation status | **Fixed.** Capture the entry exit status before cleanup; any restoration failure raises status 2. Signal handlers stop and wait for the active process group, including grandchildren, before restoring settings and writing the done-file. Remaining children receive forced termination after five seconds. Animation restoration accumulates failures across all three settings. | `test_GATES_08_T3_and_T4_cancel_children_before_done_file`; `test_GATES_08_cleanup_failure_cannot_pass`. |
| 9 MAJOR: recorded goldens never return | **Fixed.** Retrieve each selected recording task's `src/screenshots/`, including removals, while still holding the remote lease. Retrieval failures return 2. Other modules' screenshots are untouched. | `test_GATES_09_record_returns_only_selected_module_goldens`; `test_GATES_09_failed_golden_retrieval_cannot_pass`. |
| 10 MAJOR: T1 misses Android compilation | **Fixed.** Every touched KMP module receives `compileAndroidMain`, independently of Android host-test source presence; desktop tests and existing host/golden tasks remain selected. | `test_GATES_10_T1_compiles_android_without_host_test_sources`. |
| 11 MAJOR: VPS strings check lacks repository inputs | **Fixed.** Run the strings drift command locally against the same checkout's frozen Flutter inputs, under the shared PC slot, even when T1's module tests use the VPS. | `test_GATES_11_VPS_T1_checks_strings_against_local_repo_inputs`. |
| 12 MINOR: short private hostname diagnostic leaks | **Fixed.** Replace the suffix mask with a fixed redaction marker. | `test_GATES_12_short_private_hostname_is_fully_masked`. |

## Validation

Each finding was reproduced before its fix. A separate baseline replay copied the unchanged `066a00be` tool scripts
into a throwaway directory and ran the review regressions: **all 19 test methods failed** (31 failing subcases).
The strengthened grandchild-wait and first-animation-restoration probes also failed before their additional fixes.
No real remote push, VPS connection, emulator, or device action was performed: all such regression actions use local
throwaway repositories and SSH/device shims.

Final commands and results:

- `python3 native/tool/test/test_gates_review.py`: **19 tests, 0 failures**.
- `JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 bash native/tool/test/run_tests.sh`: **128 checks, 0 failures**;
  includes the 19-method review suite. The original positive boundary-host fixture now uses an explicitly permitted
  canonical range, and task-list expectations include Android compilation.
- Bash syntax checks for changed shell scripts, Python AST parsing for changed Python files, and `git diff --check`:
  passed. There is no `tool/format-changed.sh` in this checkout; no Kotlin files needed formatting.
- From `native/`, holding the existing shared PC `oc-build.lock` for the entire invocation:

  ```sh
  JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 ANDROID_HOME=/home/eslam/Android/Sdk \
    ./gradlew --no-daemon -Pkotlin.incremental=false \
    :core:common:desktopTest :core:common:testAndroidHostTest :core:common:compileAndroidMain
  ```

  **BUILD SUCCESSFUL**, 22 seconds; **19 desktop + 19 Android host tests, 0 failures/errors, 0 Kotlin warnings**.
  The repository's `-Xmx2g`, two-worker and in-process Kotlin defaults were retained. No Kotlin module was edited;
  this representative KMP module verifies the added Android compile task against the installed toolchain.

## Local commits and handoff limitation

The linked worktree's actual Git metadata is mounted read-only outside the writable roots. The initial `git add`
failed creating its `index.lock`; its branch cannot be advanced in this session. Changes remain in this worktree.
Small commits, with the requested subjects and both requested trailers, are stored in temporary Git metadata at
`/tmp/codexfix-gates-commits.git`. The original sparse-checkout flags were preserved in that temporary index; the
commits contain only the intended tool changes and this report.

Code commits: `2eb4ad85` (findings 1–4), `c2abdb38` (local ladder fixes), and `99d4e0a1` (VPS transactions and
final regression hardening). This report is a separate final commit.

The complete local commit series is exported as `native/build/gates-fixes.bundle`. Once Git metadata is writable,
from the repository root it can be imported with:

```sh
git fetch native/build/gates-fixes.bundle HEAD
git cherry-pick 066a00be..FETCH_HEAD
```

No push was performed. Live VPS, actual golden rendering, and device qualification remain unrun; the evidence here
is offline tooling regressions and the focused local Gradle invocation above.
