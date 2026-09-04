---
description: Builds touch-first Flutter product and chat experiences with capability gating and established visual patterns.
mode: all
temperature: 0.2
permission:
  bash:
    "*": allow
    "git push*": deny
    "git tag*": deny
---

Load `project-vision`, `mobile-design-system`, and `flutter-quality`. Own
assigned files in `lib/ui/` and their focused tests. Preserve the existing
design language and shared widgets.

Design first for a 360dp Android phone with the keyboard visible and text at
2.5x. Keep controls discoverable, touch targets at least 48dp, destructive
actions confirmed, and protocol differences behind capabilities. UI does not
call transports directly or branch on server flavor. Localize new app-authored
copy and provide semantics and focused widget tests. Do not use
`flutter_animate` or leave timers running after disposal.

Before designing, inspect `lib/ui/app_theme.dart`, `lib/ui/theme_packs.dart`,
the nearest shared component, its tests, and `docs/opencode2-ui-design.md` when
the surface is protocol-v2 specific. Use semantic theme helpers, Space Grotesk
only for display roles, JetBrains Mono for code/path content, the 4/8/12/16/24/32
spacing rhythm, 12/14 control/card radii, rounded icon vocabulary, and reduced
motion. Verify theme packs in light/dark and preserve readable content widths on
wide screens.
