# First-install runtime choice

Actual `TermuxSetupScreen` rendered with synthetic, read-only Termux channel
responses. The fixture reports an absent managed installation and a previously
connected Termux bridge. It does not run installation commands or save profiles.

| View | Default OpenCode 1 | Selected OpenCode 2 beta |
| --- | --- | --- |
| Light, 390x844 at 3x | [Choice](light-choice.png) | [Beta](light-beta.png) |
| Dark, 390x844 at 3x | [Choice](dark-choice.png) | [Beta](dark-beta.png) |
| Light, 320x844 at 2x, text scale 2 | [Choice](large-choice.png) | [Beta](large-beta.png) |

The capture helper scrolls to the choice and then the install action. Content
cropped at a viewport edge is outside the scroll window; the large-text choice
and action are shown in separate views. All six images were inspected for
readable labels, selection state, wrapping, contrast and reachable controls.

Reproduce with the pinned SDK:

```text
flutter test --no-pub --concurrency=1 tool/capture/termux_runtime_choice_test.dart
```

Three capture cases passed. Behavioral tests also cover legacy and installed
environments, saving the matching profile before launch, and retaining the
selected generation through ready/Stop/reinspection. See the
[verification note](../../verification/first-run-runtime-2026-09-08.md) for
runtime evidence and remaining limits.
