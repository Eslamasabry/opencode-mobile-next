---
name: quality-review
description: Use for code review, regression analysis, Flutter test design, localization review, accessibility, semantics, text scale, or golden tests.
---

# Quality Review

## Review Contract

Lead with findings ordered High, Medium, Low. Every finding states location,
evidence, user impact, violated rule, and exact verification. Distinguish a
verified defect from missing coverage or historical backlog. If no findings
exist, say so and list residual risks.

## Test Engineering

- Use Flutter 3.47.2 and run tests with `--concurrency=1`.
- New behavior needs a test that fails without the change.
- Prefer visible behavior and negative call assertions over private details.
- Use controlled completers for races and exact pumps for timer boundaries.
- Avoid `pumpAndSettle` around periodic timers, progress indicators, SSE, or
  permanent animation.
- Register teardown immediately for controllers, streams, channels, view state,
  platform overrides, globals, and semantics handles.
- Mock `plugins.it_nomads.com/flutter_secure_storage` whenever a widget test
  reaches `ProfileStore.load` or `upsert`.
- Verify persistence after reconstructing state and verify failures at the
  durable boundary, not only an in-memory cache.

## Accessibility

- Interactive targets are at least 48x48dp.
- Controls have meaningful labels, state, enabled/disabled reasons, and logical
  traversal order.
- Status cannot rely on color alone.
- Hidden gestures have visible alternatives.
- Test 360x740 at 2.5x text and keyboard/view insets where relevant.
- Test light/dark contrast and reduced motion.
- Widget guidelines do not prove TalkBack, modal focus restoration, or device
  behavior; request device evidence for critical flows.

Run `androidTapTargetGuideline`, `labeledTapTargetGuideline`, and
`textContrastGuideline` for critical surfaces.

## Localization

- New app-authored strings use `AppLocalizations`; server-authored content is
  not translated.
- ARB keys are descriptive camelCase and include translator descriptions.
- Declare typed placeholders and use ICU plurals/date formatting.
- Run `flutter gen-l10n` and the localization ratchet.
- Never raise the hardcoded-string baseline to make a test pass.
- Bare `MaterialApp` tests need localization delegates unless the component has
  an intentional established test fallback.

## Goldens

The theme gallery covers four static packs in light/dark at a fixed 420x900
surface. Dynamic color, narrow layouts, large text, keyboard insets, and device
accessibility require separate evidence. Never update goldens blindly.

## Final Gate

```bash
flutter analyze --no-pub
flutter test --no-pub --concurrency=1
```

Add SDK, native, security, or release checks according to the touched domain.
