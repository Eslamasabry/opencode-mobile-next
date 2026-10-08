# BD5 integration Kotlin gate repair — 2026-10-08

Candidate base: `feat/genui-fe` **6151bda44**, on `sol/bd-detekt`. Integration advanced to `aa3847d36` during validation, with **no Android changes** since that base. The full pinned detekt 1.23.8 gate initially failed with **315 findings / exit 2**, inherited from the BB runtime integration. This is a host-only lint repair; the original runtime behavior and native callable contracts are preserved.

The refactors name existing bounds, wrap long expressions, extract private launch/admission/drain phases and filesystem-path helpers, and split native acceptance/installer QA phases in their existing files. Public and reflected entrypoints, scripts, output/error projections, clock bounds, process identity validation, TERM/wait/KILL/wait ordering, synchronized regions, Stop revocation and generation admission remain unchanged. The run fixture compiles the two production admission constants along with its extracted production methods.

`sol/bb-runtime` was inspected at `15922db1e`, including BB4 CPU leases `618a94c11`. The lease/work-lock block, agent launch and foreground-service shutdown paths were left intact. `BuiltinLinux.startQualifiedInstaller` needed private phase extraction around its identity read and monitor, preserving the permit-write boundary where BB4 adds its lease. `stopAllServices` only wraps its existing condition. `BuiltinServerService` changes are confined to `onStartCommand` phase extraction; its Stop, timeout and destruction paths are intact.

## Narrow baseline additions

Exactly six entries are added to the existing reviewed baseline; no generated wholesale baseline, broad suppression or rule/configuration change. Compatibility wrappers would retain the same findings. Each item is an existing callable contract that cannot be changed within this behavior-neutral repair:

| Exact finding / callable | One-line reason |
| --- | --- |
| `LongParameterList: NativeRuntimeOwnership.plan` | Preserve the receipt/boot/inventory/registered arguments, default `requireCompleteInventory = true` and nonce callback ABI used by BB and standalone native fixtures; replacing that callable with grouped arguments changes its contract. |
| `LongParameterList: NativeInstallerOwnership.drainPlan` | Preserve the existing ticket/rootfs/boot/inventory/readers and absence/nonce callback callable; combining or omitting independent ownership proofs changes the contract or its fail-closed checks. |
| `LongParameterList: NativeRecoveryBudget.manualResetAllowed` | Preserve the nine independent manual-reset admission inputs and their current callers; replacing their callable or dropping a proof changes the reset contract. |
| `LongParameterList: NativeRecoveryBudget.admitted` | Preserve the seven independent runtime-policy/generation/migration admission inputs and their current callers; collapsing or dropping a proof changes the admission contract. |
| `UnusedParameter: NativeRecoveryBudget.reserve(at: Long)` | Preserve the named timestamp argument; using it would introduce scheduling/storage behavior that the native-owned immediate reservation intentionally does not perform. |
| `UnusedParameter: BuiltinLinux.bindServerRecovery(legacy: Map<*, *>?)` | Preserve the bridge callable while intentionally ignoring legacy state after staging/marker acknowledgement; consuming it would let Dart replace native-authoritative counters during bind. |

[Exact added baseline IDs](baseline-additions.txt) include the complete signatures. Existing historical baseline entries are retained.

## Verification

- Initial complete gate: **315 findings, exit 2**; [summary](initial-summary.json), [text](initial-detekt.txt), [XML](initial-detekt.xml).
- Reverted root refactors in an isolated, layout-preserving original-source snapshot with the final six-entry baseline: **90 findings, exit 2**. The working tree was never reverted. This proves the reviewed exceptions do not hide the root refactors' regression signal.
- Reverted **all** Kotlin fixes in an isolated original-source snapshot with the same final baseline and Temurin 17: **309 findings, exit 2**. The six justified exceptions account for the difference from 315; all other finding removal is due to the refactors. [Text](full-revert-detekt/detekt.txt), [XML](full-revert-detekt/detekt.xml).
- Eight affected production helper JUnit classes: **145 tests passed**, compiled by kotlinc with the real API 37 Android jar and Temurin 17.
- All **26** `test/*native*_test.dart` files: **214 passed, one existing opt-in download smoke skipped** (`OC_SMOKE_REAL_OPENCODE=1`, approximately 150 MB). No kotlinc fixture was skipped. This is the focused native set, not the complete Flutter suite.
- Pinned `flutter analyze`: **No issues found**.
- Actual Gradle `:app:testReleaseUnitTest`: **145 tests passed in all eight release JVM classes**, no failures/errors/skips; **`:app:compileReleaseKotlin` and `:app:compileReleaseAndroidTestKotlin` passed**. [Build log](gradle-green.txt), [JUnit summary](release-jvm-summary.json), [cleanup](build-cleanup.json).
- Final complete pinned detekt gate: **zero new findings, exit 0**; [gate](final-detekt/gate.txt), [XML](final-detekt/detekt.xml).

Heavy commands run through `tool/qa/machine_lock.sh`, with Temurin 17; Flutter uses the pinned Shorebird cache. No emulator session, APK installation, signing, push, CI or publication is part of this task. Read-only diff review found no material semantic changes in the runtime/service/setup refactors. The coordinator retains integration/full-suite ownership.

## Host release registration preparation

The first actual Gradle run passed `:app:compileReleaseKotlin` but failed Java compilation because Flutter's ignored generated `GeneratedPluginRegistrant.java` still registered dev-only `integration_test`, which the pinned release Gradle plugin excludes. No production source caused that failure. The pinned CLI's `--config-only` completed but did not regenerate that registration in this SDK. The [release registration helper](release-registration.txt) invokes the pinned SDK's own `injectPlugins(... releaseMode: true, androidPlatform: true)` with its normal template renderer. It updates only the ignored generated Android registration; no tracked Gradle configuration or app code changed. No APK was built or signed.

Run after Dart checks and before Gradle (using the pinned SDK package configuration):

```sh
tool/qa/machine_lock.sh build -- "$PINNED_FLUTTER_DIR/dart" \
  --packages="$PINNED_FLUTTER_DIR/../packages/flutter_tools/.dart_tool/package_config.json" \
  docs/qa/BD5-2026-10-08b/release-registration.txt
```

`PINNED_FLUTTER_DIR` is the pinned cache's `bin` directory. Actual validation used Temurin 17 and `gradle-9.5.0 -p android --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g -XX:ReservedCodeCacheSize=256m' -Pkotlin.compiler.execution.strategy=in-process :app:testReleaseUnitTest :app:compileReleaseAndroidTestKotlin`, inside the shared build lock. The failed run's intermediates were removed and its single-use Gradle daemon PID `2938219` was confirmed exited before the retry.

All tracked source hashes match [verification.json](verification.json). The final source snapshot is host-verified and locally committed; no device qualification or full Flutter suite is claimed.
