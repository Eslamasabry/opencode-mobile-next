---
name: mobile-design-system
description: Use for OpenCode Mobile themes, colors, typography, spacing, icons, surfaces, motion, responsive layouts, or interaction design.
---

# Mobile Design System

## Authority

Use current code before prose:

1. `lib/ui/app_theme.dart` and `lib/ui/theme_packs.dart`
2. Current shared widgets in `lib/ui/widgets/`
3. Theme, accessibility, text-scale, and golden tests
4. `docs/opencode2-ui-design.md` for locked OpenCode 2 behavior
5. `docs/design-inspiration.md` only as inspiration

Dated audits are historical until current source confirms them.

## Product Shape

- Design first for Android at 360dp width, keyboard visible, and 2.5x text.
- Keep the prompt, transcript, diffs, files, and pending decisions dominant.
- Put primary actions in easy thumb reach or pinned bottom action regions.
- Every long press, swipe, or shortcut needs a visible accessible alternative.
- Prefer progressive disclosure and concise sheets over dense control panels.
- Preserve focus, selection, scroll, drafts, and visible content during refresh.

## Theme API

- Resolve colors from `ThemeData.colorScheme`; do not use legacy identity
  constants as pack-neutral colors.
- Use `AppTheme.successOf(theme)` for success and diff additions.
- Use `AppTheme.statusColor(theme, tone)` for semantic status.
- Use `AppTheme.mutedOf(theme)` instead of `theme.hintColor`.
- Use `AppTheme.hairline(theme)` for subtle borders.
- Use `AppTheme.liveTint(theme)` for active low-emphasis surfaces.
- Use `AppTheme.raised(theme)` only for genuinely floating or pinned content.
- Status always combines text/icon with color.

Supported packs are OpenCode, Catppuccin, Gruvbox, Solarized, and Material You.
Every new color treatment must remain readable in light and dark across static
packs. Dynamic color falls back to OpenCode when unavailable.

## Typography

- `AppTheme.displayFamily` is Space Grotesk and belongs to display, headline,
  and major title roles.
- Body copy uses the platform face.
- `AppTheme.monoFamily` is JetBrains Mono for code, paths, commands, IDs, and
  server patterns. Never hardcode `AppMono`.
- Prefer Material text roles over one-off sizes.
- Standard custom references are code 12, caption 11, and body 14.
- Sheet titles use `titleMedium`; metadata uses muted `labelSmall`; section
  labels use the shared `SectionLabel` recipe.

## Geometry And Surfaces

- Preferred spacing scale: 4, 8, 12, 16, 24, 32.
- Reuse a component's existing anatomy before introducing a new spacing value.
- Controls use `AppTheme.radiusControl` (12); cards use
  `AppTheme.radiusCard` (14).
- Sheets use 24 top radius; dialogs 22; composer 24 or 20 compact; pills use a
  stadium shape.
- Canvas uses the pack background.
- Inline surfaces use `surfaceContainerLow`.
- Grouped and pinned regions use `surfaceContainerHigh`.
- Strong transient regions use `surfaceContainerHighest`.
- Avoid nested cards and borders when spacing and typography can establish
  hierarchy.

## Icons And Controls

- Use rounded Material symbols and `AppIcons` for repeated verbs.
- Do not create icon synonyms for copy, run, stop, send, queue, retry, or links.
- Root navigation uses filled selected and outlined unselected variants.
- Icon-only actions need a tooltip/semantic label and a 48dp target.
- Destructive actions require explicit confirmation and established haptics.

## Motion

- Use framework implicit animations or `TweenAnimationBuilder`.
- Default curve is `Curves.easeOutCubic`; normal duration is 150-400ms.
- Use 120-150ms for immediate feedback, 180-240ms for state transitions, and
  300-400ms only for continuity.
- Respect `MediaQuery.disableAnimationsOf(context)` and retain a clear static
  state when motion is disabled.
- Do not add `flutter_animate`, shimmer, hero choreography, or a custom route
  animation system.
- Dispose every timer, animation controller, and subscription.

## Responsive Knowledge

- Root navigation changes from bottom bar to rail at 760dp and extends at
  1040dp.
- Chat content caps at 860dp; prose caps at 640dp.
- Chat compact mode is based on device height below 520dp, not keyboard-reduced
  constraints, so opening the keyboard does not replace the focused composer.
- Files use master/detail at 900dp; Review at 840dp.
- Diff wraps below 600dp and uses whole-surface horizontal scrolling above it.
- Use `AppTheme.stackedActions(context)` when scaled labels need vertical actions.
- Keep sheet content scrollable and primary actions reachable above keyboard
  insets.

## Established Components

- Product states and section labels: `lib/ui/widgets/product_states.dart`
- Confirmations: `lib/ui/widgets/confirm_sheet.dart`
- External links: `lib/ui/widgets/external_link.dart`
- Forms: `lib/ui/widgets/form_renderer.dart`
- Pickers: `lib/ui/widgets/pickers.dart`
- Tool presentation: `lib/ui/widgets/tool_card.dart`
- Entry motion: `lib/ui/widgets/entrance.dart`
- Chat composer and attention flows: `lib/ui/screens/chat/`

Do not copy a known deviation merely because it exists. Prefer shared tokens
and the current component recipe.

## Verification

Run focused behavior tests plus, when relevant:

```bash
flutter test --no-pub --concurrency=1 test/app_theme_test.dart
flutter test --no-pub --concurrency=1 test/theme_packs_test.dart
flutter test --no-pub --concurrency=1 test/accessibility_guidelines_test.dart
flutter test --no-pub --concurrency=1 test/text_scale_overflow_test.dart
flutter test --no-pub --concurrency=1 test/app_text_scale_test.dart
flutter test --no-pub --concurrency=1 test/l10n_coverage_test.dart
```

Review golden changes deliberately; never update goldens simply to make tests
pass.
