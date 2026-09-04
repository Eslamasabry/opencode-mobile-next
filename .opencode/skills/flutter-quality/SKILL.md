---
name: flutter-quality
description: Use for Flutter UI, widget tests, localization, accessibility, adaptive layouts, animations, or quality verification.
---

# Flutter Quality Workflow

Use Flutter 3.47.2 and read `CONTRIBUTING.md` before changing code.

## UI Checklist

- Verify 360dp phone width, keyboard-visible height, and 2.5x text.
- Use 48dp touch targets, semantics, visible focus, and discoverable actions.
- Preserve current content during refresh and surface mutation failures.
- Use shared theme tokens and established components.
- Respect reduced motion and dispose every timer, controller, and subscription.
- Do not add `flutter_animate`.
- Localize new app-authored text with descriptive ARB entries; do not translate
  server-authored model, tool, agent, command, or output content.
- Run `flutter gen-l10n` after ARB edits.

## Test Checklist

- Prefer visible behavior over implementation details.
- Run tests serially.
- Avoid `pumpAndSettle` around periodic timers, spinners, and live streams.
- Reset platform channels, views, globals, and semantics handles in teardown.
- Install localization delegates in isolated harnesses or use the established
  test-safe localized component pattern.
- Test failures, stale async results, narrow layouts, text scale, and negative calls.

Run focused tests first, then `flutter analyze --no-pub` and
`flutter test --no-pub --concurrency=1`.
