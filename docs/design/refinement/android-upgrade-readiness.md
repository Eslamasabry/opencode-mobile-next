# Android plugin upgrade readiness — 2026-09-09

Read-only, bounded review of the Kotlin Gradle Plugin warning in candidate 36fc9f1. No dependency, pub-cache, source or toolchain changes; no builds/tests launched by this review.

**Finding:** no newer published package upgrade is available for these three warnings. All three locked versions already contain upstream AGP 9/built-in Kotlin compatibility. Their scripts retain conditional `kotlin-android` application for older hosts. The pinned Flutter warning scans the script text with regular expressions rather than checking which branch Gradle actually executed. Therefore the warning is not evidence that this candidate actually applied legacy KGP, although future Flutter enforcement remains a compatibility item to monitor.

| Dependency locked in pubspec.lock | Latest publisher release checked | Built-in Kotlin evidence | Action |
| --- | --- | --- | --- |
| desktop_drop 0.8.4 | 0.8.4 | Publisher changelog 0.8.4 specifically corrects builtInKotlin-property conditional KGP application;0.8.3 fixes AGP 8/9 configuration. Cached Android script lines 19–36 applies KGP only if AGP<9 or built-in Kotlin is disabled. | Keep current version. No newer published fix to upgrade to. |
| flutter_timezone 5.1.0 | 5.1.0 | Publisher changelog 5.1.0,2026-05-28, adds AGP 9 support. Cached script lines 24–35 skips KGP when AGP>=9 and android.builtInKotlin=true. | Keep current version with explicit true flag. No newer published fix found. |
| mobile_scanner 7.4.0 | 7.4.0 | Publisher changelog 7.3.0 fixes AGP 9;7.2.1 introduced conditional application. Cached 7.4.0 script lines 29–38 honors AGP major and builtInKotlin property. | Keep current version. No newer published fix found. |

Publisher-owned primary references checked live:

- [desktop_drop changelog 0.8.4](https://pub.dev/packages/desktop_drop/changelog#084), including [upstream PR 500](https://github.com/MixinNetwork/flutter-plugins/pull/500).
- [flutter_timezone changelog 5.1.0](https://pub.dev/packages/flutter_timezone/changelog#510---2026-05-28).
- [mobile_scanner changelog 7.4.0 and7.3.0](https://pub.dev/packages/mobile_scanner/changelog).
- [Flutter's current built-in Kotlin migration guidance](https://docs.flutter.dev/release/breaking-changes/migrate-to-built-in-kotlin/for-plugin-authors): enabling built-in Kotlin requires Flutter 3.47+, AGP 9+ and android.builtInKotlin=true. This compatibility is separate from whether future tooling rejects legacy declarations that remain in unreachable compatibility branches.

## Local evidence

Inspected `/home/eslam/Storage/Code/oc_app-ui-integration/pubspec.lock`: desktop_drop at 108–115, flutter_timezone 355–362, mobile_scanner 584–591. Candidate `android/settings.gradle.kts` declares AGP 9.3.2; `android/gradle.properties` explicitly sets android.builtInKotlin=true and android.newDsl=false. The latter is a separate AGP DSL compatibility flag, not a Kotlin-disable flag.

Inspected exact cached package Android build.gradle files:

- `/home/eslam/.pub-cache/hosted/pub.dev/desktop_drop-0.8.4/android/build.gradle`
- `/home/eslam/.pub-cache/hosted/pub.dev/flutter_timezone-5.1.0/android/build.gradle`
- `/home/eslam/.pub-cache/hosted/pub.dev/mobile_scanner-7.4.0/android/build.gradle`

Pinned Flutter source explains the warning mechanism directly:
`/home/eslam/.shorebird/bin/cache/flutter/e16cf749ccaa38d7050335ff305def49b1c7c84c/packages/flutter_tools/gradle/src/main/kotlin/FlutterPluginUtils.kt:624` emits the plugin warning. `getSubprojectPluginState` at 653–705 documents and implements regex scanning of build scripts rather than runtime plugin state; the scanner sees conditional declarations too. This is the precise warning source, not the unrelated app-language detector in project.dart.

## Decision and limitation

Do not change the frozen candidate or replace packages merely to suppress this warning. Current declarations meet the inspected plugins' built-in Kotlin conditions. Preserve the warning in verification notes and revisit against the next pinned Flutter upgrade: ideally upstream packages remove obsolete compatibility declarations, or Flutter recognizes the conditional case. No such newer published release was available in the checked package changelogs. This review did not file upstream issues or assert an existing issue/fix timeline.

Root's native compile outcome remains the build proof. This source review does not independently prove Gradle branch execution, full future compatibility, scanner hardware behavior, drag/drop behavior or timezone behavior. A future release gate must still compile/test the actual chosen Flutter/AGP/package combination.
