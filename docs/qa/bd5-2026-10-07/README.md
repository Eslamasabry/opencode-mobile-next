# BD5 — Kotlin static analysis gate

Date: 2026-10-07

Finish line: the pinned standalone detekt command checks Android Kotlin sources
and fails on any new finding against the reviewed committed baseline.
Non-goal: calling the existing native code issue-free or rewriting Kotlin to
remove all historical debt in this stability slice.

## Tool and scope

`tool/qa/kotlin_static_analysis.sh` pins **detekt 1.23.8** from the official
[GitHub release](https://github.com/detekt/detekt/releases/tag/v1.23.8):

- Asset: `detekt-cli-1.23.8-all.jar` (69,922,269 bytes).
- SHA-256: `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.
- Cache: `${XDG_CACHE_HOME:-$HOME/.cache}/oc-mobile-tools`; no jar is committed.
- SHA-256 verified before execution on every run, including cached copies.
- Java heap limited to 512 MB; Java runs through `machine_lock.sh build`.
- No Gradle compilation, Android signing, auto-correction, or parallel analysis.

Configuration in `android/config/detekt/detekt.yml` builds on the tool's active
default rules with **maxIssues 0** and invalid-configuration warnings as errors.
The default configuration has 116 active rule entries. `PackageNaming` stays
active with an exact additional pattern for the existing Android namespace
`io.github.eslamasabry.opencode_mobile` and its normally named subpackages;
unrelated underscore packages remain invalid. This CLI intentionally
has no Android classpath: rules requiring full type resolution do not run.
This is a syntax-based analysis gate, alongside existing Android release lint
and Kotlin compilation; it does not replace them. See detekt's official
[CLI](https://detekt.dev/docs/1.23.8/gettingstarted/cli/) and
[type-resolution documentation](https://detekt.dev/docs/1.23.8/gettingstarted/type-resolution/).
Baseline signatures can continue to match findings inside existing methods;
reviewing edits to already-baselined code remains necessary.

Input defaults to `android/`, covering `.kt` and `.kts` files. Generated
`build/` and `.gradle/` directories are excluded; default rule-specific
exclusions (for example, test sources for MagicNumber) remain unchanged.
Missing baselines and empty source inputs fail instead of silently passing.
Reports go to `build/reports/detekt/detekt.txt` and `detekt.xml`.

## Baseline provenance

The baseline is generated from a temporary snapshot of committed native
sources at `8817cef86137050b43969f7641e1b9daa643a57f`, not concurrent BD7 edits:
48 tracked `.kt`/`.kts` files; source-manifest SHA-256
`57b568673b1ae4364c5e17a41649270c6c5be14ffade572f013d67854b22539b`.
The manifest hashes sorted repository-relative paths plus NUL separators and
file contents. The normal gate never rewrites the baseline. Explicit baseline
generation requires `--create-baseline PATH`, and its changes need review.
The baseline contains **532 historical findings across 23 rule IDs**, with
**zero manual suppression entries**. Configuring the established app namespace
removed 45 package-name false positives from the generated baseline. Existing
findings in `CurrentIssues` are historical debt, not proof that they are safe.

## Commands for the PR workflow

```sh
tool/qa/kotlin_static_analysis.sh
OC_DETEKT_INTEGRATION=1 python3 -m unittest tool.qa.test_kotlin_static_analysis -v
```

The first command internally holds the shared build lock for Java. The CI step
exports `OC_BUILD_LOCK_FILE="$RUNNER_TEMP/oc-build.lock"` and
`OC_LOCK_DIR="$RUNNER_TEMP/oc-locks"` so GitHub runners use writable paths.
The coordinator added the `machine_lock.sh` override and
`test_machine_lock.py` (verified that the requested lock remains held), while
preserving the workstation's shared default lock. Workflow changes remain the
coordinator's ownership. CI was not triggered locally.

## Verification

The baseline was generated with the same configuration from the committed-source snapshot.
Initial real fixture regression and wrapper checks passed (6 tests): clean
fixture exit 0, introduced MagicNumber exit 2, restored fixture exit 0; the
baseline bytes remained unchanged.

The first current-tree scan correctly failed on six concurrent BD7 changes.
The existing namespace was addressed in the rule configuration; the other five
findings were sent to the native-code owner for repair. The baseline was never
regenerated from those uncommitted changes.

A subsequent seven-test run hit the old 240-second bound for both Java
fixture commands while queued behind other native builds; its five wrapper
checks passed. The per-command bound was
increased to 600 seconds to budget shared-lock waiting. Final validation after
native-code changes settle is recorded below.

The final checks were batched under one real shared lock:

```sh
tool/qa/machine_lock.sh build -- python3 /tmp/oc-bd5-final-batch.py
```

That temporary validation driver holds the outer workstation build lock for
all Java executions, while nested runner calls use a unique temporary
`OC_BUILD_LOCK_FILE` to avoid reacquiring the same exclusive lock. Java remains
serial. It restores the config in `finally` after the namespace-regression
probe, then runs the seven-test file and the final current-tree gate.

**Final batch passed, exit 0.** Removing the namespace exception made the
namespace regression test fail (test exit 1; detekt exit 2, PackageNaming).
Restoring the exact config passed all **7 focused tests** in 7.346 seconds.
The final settled-tree scan then passed with **zero new findings**, exit 0.
The baseline stayed at 532 historical findings, zero manual suppressions.
Full output: [final-check.log](final-check.log).

Final Kotlin input: **49 files**; source-manifest SHA-256
`5d3b791be26872c014ae67678b22e213038a9de22298fb24efffcd93927a68f1`. The extra source relative to the baseline is BD7's
CrashDiagnosticsBridge.kt. The final scan includes that source and the
settled NativeCrashStore/MainActivity changes.

`bash -n`, Python compile check, and `git diff --check` passed. The coordinator
reports the integration analyzer clean. No full Flutter suite, signing,
publication, or triggered CI run was performed for BD5.

### Explicit historical baseline debt

| Rule | Findings |
| --- | ---: |
| ComplexCondition | 18 |
| CyclomaticComplexMethod | 42 |
| EmptyElseBlock | 2 |
| EmptyFunctionBlock | 2 |
| InstanceOfCheckForException | 3 |
| LargeClass | 3 |
| LongMethod | 20 |
| LongParameterList | 7 |
| LoopWithTooManyJumpStatements | 8 |
| MagicNumber | 226 |
| MaxLineLength | 74 |
| MemberNameEqualsClassName | 1 |
| NestedBlockDepth | 15 |
| ReturnCount | 41 |
| SwallowedException | 8 |
| ThrowingExceptionFromFinally | 1 |
| ThrowingExceptionsWithoutMessageOrCause | 2 |
| ThrowsCount | 21 |
| TooGenericExceptionCaught | 14 |
| TooManyFunctions | 11 |
| UnusedParameter | 1 |
| UnusedPrivateProperty | 1 |
| UseCheckOrError | 11 |

Root portability verification: `python3 -m unittest tool/qa/test_machine_lock.py` passed. Removing `OC_BUILD_LOCK_FILE` handling made the bounded regression wait on the wrong shared lock; restored, the requested temporary lock was verified held. Existing local default lock is unchanged. Workflow YAML and all shell blocks parsed locally.


## BD9 resume static checkpoint (2026-10-08)

Coordinator delegated resolution of the four merged PhoneAgentHost findings.
The unchanged launch shell was extracted, the1500ms grace interval named,
the password-pipe write/flush split, and the same IllegalStateException raised
through Kotlin error(). Existing public methods and behavior remain unchanged.
A fifth finding in the later BD9 classifier (ReturnCount) was resolved by
combining its empty/multiple-result guard; authored failure categories remain
unchanged. No baseline or suppression was added.

Pinned detekt1.23.8 under Temurin17 and the shared build lock: PASS, zero new
findings across the final Android tree. Temporarily restoring both original
files reproduced LongMethod, ThrowsCount, MaxLineLength, MagicNumber and
ReturnCount (exit2). The fixed files were restored in finally, then the same
gate passed again. Private regression output: build/bd9-detekt-regression.txt.

Affected native host/contract tests:20 PASS through machine_lock, pinned Flutter
--no-pub --concurrency=1. Full pinned analyzer: no issues (15.9s). The launch
shell payload equality check and git diff --check also pass. No device proof
is inferred from static/native-unit checks.
