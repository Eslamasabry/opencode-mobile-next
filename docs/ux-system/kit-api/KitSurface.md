# KitSurface: API freeze (wave 0)

Unit: `kit-KitSurface` (kind `kit-part`, tier 1a; cut review C23). Spec for `revamp.workflow.js`.

## Purpose

KitSurface is the one solid box: a fill from the surface steps, a token radius, token padding, and optionally a hairline edge. Depth comes from surface steps, never from shadows (VL §4). `KitPanel` becomes its `panel` form.

**Contract resolution (STANDARDS §0.2, KIT-42, Appendix A #24 and #41).** The unit's title and kit-v2 §9.2 say "plain, raised, tonal, glass". STANDARDS, which is owner-derived from VL §4 and §6, overrides that:
- "plain" is `ground`;
- "raised" is `surface1` (the panel);
- there is no tonal level: the attention look belongs only to `KitNeedsYou`, `KitRequestCard` and `KitNotice.card` (LOOK-24);
- glass is not a level: `KitGlass` stays the one glass part and KitSurface does not wrap it (G21 fails `KitSurfaceLevel.(glass|raised|tonal)`).

## Replaces

- **kit-v2.json `assignment` → `KitPanel`** (18 elements; KitPanel becomes `KitSurface.panel`):
  - `add-agent#add-agent-card`;
  - `agent-account#agent-account-state`;
  - `appearance-picker-sheet#appearance-preview-card`;
  - `attention-overview#attention-overview-card`;
  - `context-capsule#context-capsule-excerpt`;
  - `development-services#hero-card`, `development-services#service-card`;
  - `embedded-local-agent-onboarding-block#block-card`;
  - `embedded-team-card#team-card`;
  - `embedded-team-discovery-card#discovery-card`;
  - `embedded-team-phone-reoffer-card#reoffer-card`;
  - `embedded-team-planning-card#planning-card`;
  - `external-agent-detail#external-agent-detail-card`;
  - `global-sessions#global-sessions-group`;
  - `local-agent-page#whole-page-card`;
  - `plugins-settings#builtin-team-section`, `plugins-settings#team-discovery-card`;
  - `termux-setup-connect-termux#guide-cards`.
- **G16 baseline:** 228 in 80 files:
  - `Container` 127 in 49 files;
  - `Material` 36 in 23;
  - `Card` 26 in 17;
  - `DecoratedBox` 18 in 14;
  - `ColoredBox` 10 in 8;
  - `ClipRRect` 10 in 8;
  - `ClipRect` 1.

  The largest are `tool_card.dart` 21, `review_workspace.dart` 19, `chat/message_view.dart` 16, `personal_settings_screens.dart` 8, `composer.dart` 7 and `pickers.dart` 7.
- **`lib/ui/kit/kit_panel.dart`** is in this unit's write set (C23). `KitPanel` keeps its whole signature and forwards to `KitSurface.panel` (KIT-43):
  - Its header icon moves from 18 to 20 (VL §7).
  - `tone:` other than neutral keeps today's look for its callers (team `agent_screen`, `merge_section`, `start_run_sheet` and `team_board_card` pass `AppStatusTone.attention`), and is marked `/// Retired by kit-KitSurface: attention look is KitNeedsYou/KitRequestCard (LOOK-24)`.
  - `KitPanel(` joins the G2 ratchet.
- **Not replaced here:**
  - `Container`s that only pad or size become `Padding` or `SizedBox` (the allowlist).
  - Glass (`GlassSurface`, `KitGlass`) is `kit-KitNav` and the LOOK-27 files.
  - The needs-you card is `KitNotice.card` and `KitRequestCard`.

## File

`lib/ui/kit/kit_surface.dart` (new) and `lib/ui/kit/kit_panel.dart` (forwarder). The write set is those two files, `test/kit/kit_surface_test.dart` and `test/goldens/kit/kit_surface_golden_test.dart`.

## Public API

```dart
// ── Shared enums: a pre-wave seam in lib/ui/kit/kit_tokens.dart (see Open
// questions). KitSurface, KitTappable, KitImage and KitMotionParts all name
// them, so none depends on another.
enum KitSurfaceLevel { ground, surface1, surface2, surface3 } // KIT-42: exactly these

enum KitShape {
  square,  // 0
  tile,    // KitTokens.iconTileRadius (9)
  code,    // KitTokens.codeRadius (14)
  button,  // KitTokens.buttonRadius (14)
  panel,   // KitTokens.panelCornerRadius (18)
  card,    // KitTokens.cardRadius (22): needs-you and request cards only
  dialog,  // KitTokens.panelRadius (24)
  sheet,   // KitTokens.sheetRadius (30), top corners only
  pill,    // a stadium (VL "999")
  circle,
}
// KitTokens gains: ShapeBorder shapeOf(KitShape shape); Color fillOf(KitSurfaceLevel level).

// ── kit_surface.dart
enum KitSurfacePadding { none, compact, panel } // 0 · KitTokens.space3 (12) · KitTokens.space4 (16)

class KitSurface extends StatelessWidget {
  const KitSurface({
    Key? key,
    required Widget child,
    KitSurfaceLevel level = KitSurfaceLevel.surface1,
    KitShape shape = KitShape.panel,
    KitSurfacePadding padding = KitSurfacePadding.panel,
    bool outlined = false,  // a hairline edge, exactly one physical pixel (LOOK-21)
    bool clip = true,       // clip the child to the shape (ClipRRect's job)
  });

  /// A grouped panel (VL §5; KitPanel's look): surface1, 18 dp corners, 16 dp
  /// inside, and an optional one-line header (a 20 dp icon in text2 and a
  /// headline title in text1) above the child.
  const KitSurface.panel({
    Key? key,
    required Widget child,
    String? title,
    IconData? icon,           // an AppIconography glyph
    KitSurfacePadding padding = KitSurfacePadding.panel,
    Key? titleKey,
  });

  /// A panel set into a sheet or a dialog, such as a confirmation's
  /// consequences: KitTokens.insetSurface (surface1 in dark, ground in light).
  const KitSurface.inset({
    Key? key,
    required Widget child,
    KitSurfacePadding padding = KitSurfacePadding.panel,
  });

  /// A leading icon in its tile (VL §4, LOOK-34): 30 dp of surface3 with 9 dp
  /// corners, a 20 dp glyph in [tone] (text1 by default). It replaces tonal
  /// circles (LOOK-23).
  const KitSurface.tile(
    IconData icon, {
    Key? key,
    KitTextTone? tone,
    String? semanticsLabel,
  });
}

// ── kit_panel.dart (forwarder, unchanged signature)
class KitPanel extends StatelessWidget {
  const KitPanel({
    Key? key,
    required Widget child,
    AppStatusTone tone = AppStatusTone.neutral, // non-neutral is retired (see Replaces)
    IconData? icon,
    String? title,
    Key? titleKey,
    EdgeInsetsGeometry padding = const EdgeInsets.all(16),
    VoidCallback? onTap,  // kept: today's InkWell; new code wraps with KitTappable
  });
  static Color borderOf(ThemeData theme, AppStatusTone tone); // kept
}
```

- **Not interactive.** KitSurface has no `onTap`. A surface that opens something is `KitTappable(shape: KitShape.panel, child: KitSurface.panel(...))`, so the hover and pressed fills and the focus ring follow the same shape. This keeps kit-KitSurface in tier 1a (C25 gives it no dependencies).
- **Material ancestor.** KitSurface paints through a `Material` with type canvas and elevation 0, so a kit part inside that needs a Material ancestor (a field, a menu anchor) keeps working. No elevation or shadow is ever set (LOOK-20).

## States

- A surface is a container: loading, empty, error, working and disabled belong to its content (`KitStateView` inline, `KitSkeletonRows`).
- The only visual variants are the level, `outlined` and the `panel` header.
- There is no "selected" surface: selection belongs to KitChoiceRow, KitSegmented and KitNav.
- There is no disabled surface: disabled content uses tones, and non-text uses `KitDim`.

## Tokens

- **ThemeRoles:** `ground`, `surface1`, `surface2`, `surface3`, `hairline`, `text1` and `text2` (the panel header).
- **KitTokens:**
  - `panelCornerRadius`, `cardRadius`, `panelRadius`, `sheetRadius`, `buttonRadius`, `codeRadius`, `iconTileRadius` and `iconTileSize`;
  - `space3`, `space4` and `labelGap`;
  - `insetSurface`;
  - `cardTitle` (headline) for the panel title;
  - `smallIconSize` (20).
- **Pre-wave (§0.5 step 2, already planned):** `KitTokens.hairlineWidth(context)`.
- **New (pre-wave `kit_tokens.dart`, `_new-tokens.md`):** the enums `KitSurfaceLevel` and `KitShape`, plus `KitTokens.fillOf(KitSurfaceLevel)` and `KitTokens.shapeOf(KitShape)`.

## Adaptive

- compact, medium, expanded and large are the same: the radii and padding do not change with the window.
  - The one window-dependent radius, the composer's 26 → 18, is `kit-KitComposer`'s and is not a `KitShape`.
  - Width comes from the host layout (`KitScreen`, `KitLayout` widths).
- **Pointer and keyboard:** none. The surface is not focusable. Hover and focus come from a wrapping `KitTappable`.

## Accessibility

- A surface adds no semantics node, except `KitSurface.tile` with a `semanticsLabel`. Its content speaks for itself.
- The `panel` title is not a heading by default, because a panel is not a section. A section label above a group is `SectionLabel` or `KitRowGroup.label`.
- **200 % text:** the panel's header title wraps, never ellipsises. Padding stays at the token.
- **48 dp** does not apply here; it applies to the controls inside.
- **Contrast:** content colours are checked against the surface's own level (LOOK-8 covers text2 and text3 on every surface).

## RTL

- All insets are directional (`EdgeInsetsDirectional`). The panel header's icon sits at the start.
- `KitShape.sheet` rounds only the top corners in both directions.
- G7 is zero in the file.

## Motion and haptics

- A static surface does not move. A change of level or outline over time is `KitAnimatedBox` (KitMotionParts), which paints only.
- No `KitHaptics`.

## Data safety and honest state

- A surface never carries a state on its own. There is no tonal wash that implies "needs you" or "failed", so the words and marks inside must say it (STATE-9, LOOK-24).
- The retired `KitPanel(tone:)` keeps its look only so existing callers do not change silently. The screen units replace it with the proper part.

## Depends on

- The VL merge (`ThemeRoles`, `KitTokens`, `KitText`).
- The pre-wave seams: `KitSurfaceLevel`, `KitShape`, `fillOf`, `shapeOf` and `hairlineWidth`.
- No wave-1 part. The tile glyph is drawn by the kit-internal `Icon` at `smallIconSize`; `kit-hygiene` (2d) swaps it to `KitIcon` once both have merged.

## Tests required

`test/kit/kit_surface_test.dart`:

1. Each level paints exactly `fillOf(level)`, and none has a `BoxShadow` or elevation.
2. `shape: panel` clips a full-bleed child to the 18 dp radius (a pixel probe at the corner shows the ground).
3. `outlined` paints an edge of exactly `1 / devicePixelRatio` at DPR 2.625 and 3.0, with no doubled line.
4. `KitSurface.panel(title:, icon:)` renders a header with a 20 dp glyph and a headline title, and the title wraps at 2.0 text.
5. `KitSurface.inset` is surface1 in dark and ground in light.
6. `KitSurface.tile` is 30×30 surface3 with 9 dp corners and a 20 dp glyph. With `semanticsLabel` it has one image node; without, none.
7. `KitPanel`:
   - its old signature builds;
   - `tone: neutral` equals `KitSurface.panel`;
   - `tone: attention` keeps `attentionSurface` and `attentionLine`;
   - `onTap` still fires.
8. A descendant needing a Material ancestor (a `TextField` inside a kit part) builds without an error.
9. Under RTL the header icon is at the right.
10. Reduced motion: no ticker.

## Galleries required

`test/goldens/kit/kit_surface_golden_test.dart`, at DPR 3, Android:

- **States:**
  - `levels`: ground, surface1, surface2 and surface3 side by side, each plain and outlined;
  - `shapes`: every `KitShape`;
  - `panel`: with and without a header;
  - `inset`: inside a sheet mock;
  - `tile`: three tiles in different tones.
  Each in dark and light at 412×915.
- **Default state (`panel`):** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **`_text2` and `_ar`:** `panel` and `tile` at 412×915 and 1280×800.
- **G6 overflow matrix** without images.

## Non-goals

- No glass level and no raised, tonal, attention or elevated variant (KIT-42).
- No tap, hover or focus: that is `KitTappable`.
- No border colours other than `hairline`.
- No gradients or ambient fields: those are the theme's (LOOK-9).
- No needs-you card.
- No sheet or dialog frame: that is `KitSheet` / `KitDialog`, which may use `KitShape.sheet` and `KitShape.dialog`.
- No call-site migration beyond the `kit_panel.dart` forwarder.

## Open questions

None. The shared enums `KitSurfaceLevel` and `KitShape`, with `KitTokens.fillOf` and `shapeOf`, are pre-wave seams in `kit_tokens.dart` (KitTappable, KitImage, KitMotionParts, KitChip, KitJumpPill and the chat parts name them), listed with every other new token in `_new-tokens.md`; `kit_tokens.dart` is in no wave-1 unit's write set, so the coordinator adds them before wave 1. If that does not happen, the enums move into `kit_surface.dart`, and kit-KitTappable, kit-KitImage and kit-KitMotionParts gain `after: kit-KitSurface` (kit-KitImage and kit-KitMotionParts move to tier 1b). The other pre-wave tokens then become PROC-20 contract problems for their parts.

- **`flatInLists` (2026-10-26, flat polish):** a box that holds a page's leading group (Report a problem's summary) sits on the page itself, with no fill, edge or padding, where the style sets its lists flat (Solid); every other style draws it as given. Default constructor only.
