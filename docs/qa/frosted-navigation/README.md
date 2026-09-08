# Frosted navigation evidence

Base: `576e391`. This is real in-app Flutter GPU backdrop blur on Android, not Android OS cross-window glass or a Compose component. It uses the supported `Scaffold.extendBody`, measured body bottom padding, `ClipRRect`, and one `BackdropFilter` with sigma 12. No new package, shader or full-page filter. Empty content naturally produces a quiet tint; scrolling rows provide the actual underlay.

The dock keeps its 72dp normal geometry and 16dp rails. Root scroll lists add the measured bottom inset as scroll space; Workspace's fixed CTA stays 12dp above the visible dock. Last files can scroll completely above it. Keyboard insets hide the dock and remove its reserved space. High contrast, accessible navigation, and disabled animations remove the filter/shadow and use a solid surface.

Translucency: dark .78, light .72. Small foreground labels and icon strokes use neutral white/black locally. Muted, primary, and some theme onSurface colors failed worst-case compositing checks. Selected identity remains the duotone shape, indicator and font weight. AppGlyph's null explicit colors inherit the local NavigationBarTheme icon override; the widget test checks the actual IconTheme at selected and unselected icons.

Worst contrast against 125 RGB background samples, including black/white, computed with actual painted fill and selected indicator: OpenCode light 7.92/dark 5.96; Catppuccin 7.35/5.46; Gruvbox 7.06/5.72; Solarized 7.45/5.83. Minimum 5.46 exceeds 4.5. Device-harvested Material You palettes were not available in these fixtures.

Pinned Flutter 3.47.2, Shorebird e16cf749ccaa38d7050335ff305def49b1c7c84c:

- Pub get passed; nine changed Dart files formatted after package resolution.
- Home navigation and glass tests: 30 passed, including last-file reachability, keyboard, reduced-effects fallback, palette contrast and existing Back/retention cases. Added foreground inheritance assertion and contrast logging: targeted nine cases passed.
- RetainedTabView unchanged; its three behavior tests passed in the initial slice check.
- Final light/dark shell accessibility and 2.5x layout: three passed.
- Final six capture cases passed, producing eight PNGs. Real mock session rows with working-state color pass under the dock at scroll offset 220; normal variants also show list end. Both themes include reduced effects and 320dp/2.5x. Fixture data is not live server content.
- Scoped analyzer: no issues in all nine changed Dart files.

No ADB, FPS or native compilation claim is made for these widget captures. Device verification should inspect the actual reduced-effects flags: emulator animation/accessibility settings can intentionally select the solid fallback. Existing large-type session/CTA truncation is outside this material/inset slice.
