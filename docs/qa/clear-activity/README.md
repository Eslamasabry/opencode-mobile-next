# Activity: clear attention

Paired production-widget captures at390×844 logical pixels, in light/dark themes and1×/2× text. Sample permissions, questions and sessions are invented; no network requests or live profile mutations are used. `before/` renders the Activity/ProfileMonitorInbox sources from checkpoint `0eabc2d`; `after/` renders this branch. Each PNG has a same-name JSON file with measured logical text bounds. These are widget-render evidence, not a new APK or measured native frame performance.

| Finding | Result | Evidence |
| --- | --- | --- |
| ACTIVITY-01 | Unknown or disconnected attention stays incomplete; Check again uses the existing wake-safe refresh path. | [Before unknown](before/unknown-dark-1x.png), [after unknown](after/unknown-dark-1x.png) |
| ACTIVITY-02 | Result/requests precede compact saved-server bookkeeping; checked-location scope remains visible. | [Before clear](before/clear-dark-1x.png), [after clear](after/clear-dark-1x.png), [pending light](after/pending-light-1x.png) |
| ACTIVITY-03 | Background updates is an explicit Off settings row; tapping does not claim to enable it. | [After clear](after/clear-dark-1x.png) and `background_discoverability_test.dart` |
| ACTIVITY-04 | Empty sibling sections are omitted; the digest explanation is revealed on expansion. | [Pending dark](after/pending-dark-1x.png) and `activity_screen_test.dart` |
| ACTIVITY-05 | Forms loading/error respects the current capability, as form rows already did. | `activity_screen_test.dart` unsupported-stale-forms case |
| ACTIVITY-06 | Shared icon-family/native motion work is deferred to integration. | No FPS or native animation claim from these PNGs. |

The known result's title moves from y300 to y144,156dp earlier in the identical390dp fixture; Completion digests moves from y534 to y392,142dp earlier. Those values are in the paired JSON files. They measure this captured state, not every server or device configuration. The2× unknown page remains a scrolling page; content below the viewport is not a claim of clipping or disappearance.

Inspected references: [Quo](https://mobbin.com/screens/e48bd893-d026-4592-a8ab-64b6490f8b32) names an empty result's filter cause and offers Clear filters; [Wabi](https://mobbin.com/screens/f23940e0-7a29-4fb9-a336-f707a4de63b1) uses an honest sparse Activity feed. These informed cause/recovery and honest whitespace, without importing iOS geometry or unsupported app behavior.

Validation on Shorebird Flutter3.47.2 frameworke16cf749ccaa38d7050335ff305def49b1c7c84c:

- `flutter pub get`, `flutter gen-l10n`, changed-file Dart format: complete.
- Baseline capture harness:12 captures passed. Its first compile failed for a missing fixture import; fixed before the passing baseline run.
- Final focused serial run:61 tests passed, including12 candidate capture cases. Files: `test/activity_screen_test.dart`, `test/activity_requests_test.dart`, `test/background_discoverability_test.dart`, `test/profile_monitor_screen_test.dart`, `test/profile_monitor_navigation_test.dart`, `test/completion_digest_card_test.dart`, `test/v2_feature_gating_test.dart`, `tool/capture/clear_activity_test.dart`.
- `flutter analyze --no-pub`: no issues. `git diff --check`: clean.
- No native build/sign/install, push, CI or release. The coordinator owns final integration/device gates and the home navigation expectation update from All clear to All clear here.

Recreate candidate captures with the pinned SDK:

```bash
flutter test --no-pub --concurrency=1 tool/capture/clear_activity_test.dart
```
