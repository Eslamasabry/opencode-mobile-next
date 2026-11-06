# T2 gates that do not resolve yet (E02-S07, 2026-11-06)

`t2.tasks` now names the flavor that exists (`switch`, not `stable`) and drops `-Poc.debugSignRelease`, which nothing
defined (a release signed with the debug key would also fail `verifyProductionLineage`). `t2.sh --resolve` looks every
step up with `./gradlew --dry-run`. On this tree (7cac253e) five of seven steps resolve:

| Step | Task | Resolves |
|---|---|---|
| build-logic | `-p build-logic test` | yes |
| tests | `desktopTest testAndroidHostTest test` | yes |
| goldens | `verifyRoborazziAndroidHostTest` | yes |
| lint | `:androidApp:lintSwitchRelease` | yes |
| assemble | `:androidApp:assembleSwitchRelease :androidApp:assemblePreviewRelease` | yes |
| r8-config | `:androidApp:analyzeSwitchReleaseR8Config` | **no**: AGP adds it only to minified variants, and `release` has R8 off (only `benchmark` turns it on) |
| catalog-report | `:quality:catalogReport` | **no**: the task was never added to `:quality` |

Both stay in `t2.tasks` and report MISSING, so T2 is red until they exist: a green T2 must mean the switch release is
the build that ships. Neither is the ladder's to fix.

## Proposal to the `:androidApp` owner (coordinator): R8 on the release

ADR §9.9 and plan 01 ("R8 uses the AGP 9.3 `optimization {}` DSL … `analyzeReleaseR8Config` runs at every T2") ship the
release minified, as the `benchmark` build type already proves start-up with. In `native/androidApp/build.gradle.kts`:

```kotlin
buildTypes {
    getByName("release") {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    // benchmark and qa keep initWith(release); benchmark adds benchmark-rules.pro on top.
}
```

That creates `analyzeSwitchReleaseR8Config` and `analyzePreviewReleaseR8Config`. It changes the shipped bytes, so it
needs the T3 flows and the E00-S09 upgrade gate on the minified switch APK before it lands.

Interim, if the coordinator wants a resolvable R8 step before then: `:androidApp:analyzeSwitchBenchmarkR8Config`
analyzes the same rules plus `benchmark-rules.pro`. The ladder did not switch to it on its own, because it would
let T2 pass on a variant that is not the release.

## `:quality:catalogReport`

Owner `:quality` (coordinator). Until it exists the catalog-report step is MISSING; remove the line only if the
catalog report is moved to another gate.
