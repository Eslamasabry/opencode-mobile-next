import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';

import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../theme_roles.dart';
import 'kit_effects.dart';
import 'kit_shape.dart';
import 'kit_text.dart';
import 'style/kit_style.dart';

export 'kit_shape.dart';

/// The design tokens the kit's parts read (docs/ux-system/kit-v2.md, the
/// visual language in docs/design/visual-language-2026-09-26.md §3–§7):
/// every colour, radius, height, scrim, type style and spacing of a kit part
/// comes from here, never a literal in the part, so a new visual language
/// (or a new theme) lands by changing tokens.
///
/// Colours are the theme's [roles]; shape, space and type are fixed by the
/// visual language. Provided as a [ThemeExtension] on the app's theme;
/// without one, [of] derives the tokens from the theme's roles.
@immutable
class KitTokens extends ThemeExtension<KitTokens> {
  const KitTokens({
    required this.roles,
    required this.space1,
    required this.space2,
    required this.space3,
    required this.space4,
    required this.space5,
    required this.space6,
    required this.rail,
    required this.sheetSurface,
    required this.panelSurface,
    required this.sideSheetSurface,
    required this.scrim,
    required this.sheetRadius,
    required this.panelRadius,
    required this.sheetElevation,
    required this.panelElevation,
    required this.sideSheetElevation,
    required this.panelInset,
    required this.handleColor,
    required this.handleSize,
    required this.handleHeight,
    required this.muted,
    required this.detailsSurface,
    required this.detailsRadius,
    required this.markSize,
    required this.markIconSize,
    required this.markTintAlpha,
    required this.markRadius,
    required this.maxIconScale,
    required this.sheetTitle,
    required this.sheetSubtitle,
    required this.confirmTitle,
    required this.confirmBody,
    required this.note,
    required this.technicalValue,
    required this.typedName,
    required this.accent,
    required this.minTarget,
    required this.smallIconSize,
    required this.panelCornerRadius,
    required this.cardRadius,
    required this.buttonRadius,
    required this.buttonHeight,
    required this.iconTileSize,
    required this.iconTileRadius,
    required this.codeRadius,
    required this.composerRadius,
    required this.composerRadiusWide,
    required this.rowHeight,
    required this.rowHeightTwoLine,
    required this.gutter,
    required this.sectionGap,
    required this.labelGap,
    required this.navHeight,
    required this.navRadius,
    required this.rowTitle,
    required this.rowSupporting,
    required this.rowValue,
    required this.sectionLabel,
    required this.cardCaption,
    required this.cardTitle,
    this.fieldCorner = fieldRadius,
    this.popoverCorner = popoverRadius,
    this.bubbleCorner = bubbleRadius,
    this.bubbleTailCorner = bubbleTailRadius,
    this.pillHeight = chipHeight,
    this.panelInner = panelPadding,
    this.pillRadius = double.infinity,
  });

  /// The theme's colour roles (ground, surfaces, text, accent, attention, …). Parts read colours from here.
  final ThemeRoles roles;

  /// The spacing scale, smallest first (4, 8, 12, 16, 20, 24).
  final double space1;
  final double space2;
  final double space3;
  final double space4;
  final double space5;
  final double space6;

  /// A modal part's side rails.
  final double rail;

  final Color sheetSurface;
  final Color panelSurface;
  final Color sideSheetSurface;

  /// The veil behind a modal part.
  final Color scrim;

  /// A bottom sheet's top corners (30); a centred dialog panel's (24).
  final double sheetRadius;
  final double panelRadius;
  final double sheetElevation;
  final double panelElevation;
  final double sideSheetElevation;

  /// The space a centred panel keeps from the window's edges.
  final double panelInset;

  final Color handleColor;
  final Size handleSize;

  /// The band the handle sits in.
  final double handleHeight;

  /// Secondary words: subtitles, bodies, reasons, labels (`text2`).
  final Color muted;
  final Color detailsSurface;
  final double detailsRadius;

  /// A confirmation's mark: an icon tile, its icon and tint, its corners.
  final double markSize;
  final double markIconSize;
  final double markTintAlpha;
  final double markRadius;

  /// How far a leading icon may grow with the person's text size.
  final double maxIconScale;

  final TextStyle sheetTitle;
  final TextStyle sheetSubtitle;
  final TextStyle confirmTitle;
  final TextStyle confirmBody;

  /// A small muted line: a disabled action's reason, a details label.
  final TextStyle note;

  /// A technical value (path, host): mono.
  final TextStyle technicalValue;

  /// The typed-name field's text: the mono role (13), like every other
  /// technical value.
  final TextStyle typedName;

  /// The tone of a neutral question's mark.
  final Color accent;

  /// The smallest touch target, everywhere (§8.3: never smaller on a PC).
  final double minTarget;

  /// A small trailing icon (a fold's chevron).
  final double smallIconSize;

  /// A grouped panel's corners (18).
  final double panelCornerRadius;

  /// A needs-you or request card's corners (22).
  final double cardRadius;

  /// A button's corners (14) and a full-width button's height (52, on the
  /// 4 dp scale; 48 under Compact, never below the touch target).
  final double buttonRadius;
  final double buttonHeight;

  /// A row's leading icon tile: 28 dp of `surface3`, 9 dp corners. Its
  /// width is the leading column's slot ([leadColumn]).
  final double iconTileSize;
  final double iconTileRadius;

  /// The leading column (impeccable layout pass, 2026-10-28: one text rail):
  /// a row's icon slot ([iconTileSize]) and the gap after it ([space3]).
  /// A row's words start this far past the row's own content edge: on a
  /// page, at [textRail] (56 dp); in a sheet, the same 40 dp past its 20 dp
  /// rail. List rows, list notices, the top bar's title and sheet rows all
  /// start their words here, whatever their leading glyph's size.
  double get leadColumn => iconTileSize + space3;

  /// The page's text rail: the gutter and the [leadColumn] (16 + 40 = 56).
  double get textRail => gutter + leadColumn;

  /// The conversation's compact leading column: a 20 dp glyph and [space3]
  /// (32), so a step, a notice and their buttons in the transcript start
  /// their words on one 48 dp rail.
  double get leadColumnCompact => smallIconSize + space3;

  /// A code block's corners.
  final double codeRadius;

  /// The composer: 26 on a phone, 18 on a PC.
  final double composerRadius;
  final double composerRadiusWide;

  /// A row with one line (56) and with two (64), on the 4 dp scale; a
  /// trailing 48 dp target fits inside either without growing it.
  final double rowHeight;
  final double rowHeightTwoLine;

  /// The screen gutter (16), the space between sections (24), and between a
  /// section's label and its panel (8).
  final double gutter;
  final double sectionGap;
  final double labelGap;

  /// The floating tab bar: 60 dp tall, 22 dp corners.
  final double navHeight;
  final double navRadius;

  /// A row's title ([KitTextRole.rowTitle]), its second line and its
  /// trailing value ([KitTextRole.secondary], the value in `text3`).
  final TextStyle rowTitle;
  final TextStyle rowSupporting;
  final TextStyle rowValue;

  /// A section's label above its panel (never uppercase).
  final TextStyle sectionLabel;

  /// A needs-you card's caption ("Needs you · 40 s ago") and its title.
  final TextStyle cardCaption;
  final TextStyle cardTitle;

  // ── Hook H5: the static shape tokens below, as instance tokens that
  // follow Corners and Density ([styled]). Parts with a token object read
  // these; the statics stay for `const` contexts and equal the defaults.

  /// [fieldRadius] under the knobs.
  final double fieldCorner;

  /// [popoverRadius] under the knobs.
  final double popoverCorner;

  /// [bubbleRadius] and [bubbleTailRadius] under the knobs.
  final double bubbleCorner;
  final double bubbleTailCorner;

  /// [chipHeight] under Density.
  final double pillHeight;

  /// [panelPadding] under Density.
  final double panelInner;

  /// Hook H4: a pill's corner radius; infinite is a stadium (Round and
  /// Soft). Corners › Sharp makes chips and pills the style's sharp box.
  final double pillRadius;

  /// A chip's or pill's border with [side]: a stadium, or the sharp box
  /// under Corners › Sharp.
  OutlinedBorder pillBorder({BorderSide side = BorderSide.none}) =>
      pillRadius.isFinite
      ? RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(pillRadius),
          side: side,
        )
      : StadiumBorder(side: side);

  /// The one shadow in the app (visual language §4, §7): under a floating
  /// surface only (the dock, the rail, the top controls, the composer), one
  /// tight drop (y 6, blur 16, spread -6) in the theme's `elevationShadow`
  /// role. Content, cards and sheets get none.
  List<BoxShadow> get surfaceShadows => [
    BoxShadow(
      color: roles.elevationShadow,
      blurRadius: 16,
      spreadRadius: -6,
      offset: const Offset(0, 6),
    ),
  ];

  /// The floating layer's shadow here ([surfaceShadows]'s place): Solid's
  /// is quieter, the `elevationShadow` colour at .10 in one tight drop
  /// (y 4, blur 12, spread -4) in light and none in dark, where surface2
  /// and the hairline already lift it. Every other style keeps
  /// [surfaceShadows].
  List<BoxShadow> floatingShadows(BuildContext context) =>
      KitStyles.styleOf(context) != KitSurfaceStyle.solid
      ? surfaceShadows
      : roles.isDark
      ? const []
      : [
          BoxShadow(
            color: roles.elevationShadow.withValues(alpha: .10),
            blurRadius: 12,
            spreadRadius: -4,
            offset: const Offset(0, 4),
          ),
        ];

  /// A panel set into a sheet or a dialog (a confirmation's consequences):
  /// one step below the sheet's `surface2`, which in light is the ground.
  Color get insetSurface => roles.isDark ? roles.surface1 : roles.ground;

  // ── Pre-wave named tokens (STANDARDS §0.5 step 2; kit-api/_new-tokens.md).
  // Fixed by the visual language, the same in every theme, so static.

  /// LOOK-21 (KitDivider.md and most specs): one physical pixel, in logical
  /// pixels.
  static double hairlineWidth(BuildContext context) {
    final dpr = MediaQuery.devicePixelRatioOf(context);
    return dpr > 0 ? 1 / dpr : 1;
  }

  /// LOOK-21 (KitTappable.md, KitField.md): the focus ring, two physical
  /// pixels, never under one logical pixel at a ratio of 1 or less.
  static double focusRingWidth(BuildContext context) {
    final dpr = MediaQuery.devicePixelRatioOf(context);
    if (dpr <= 1) return dpr > 0 ? math.max(2 / dpr, 1) : 2;
    return 2 / dpr;
  }

  /// The stroke of the kit's small indeterminate spinners: a working
  /// [KitButton] or [KitIconButton], and [KitStatusMark]'s working ring.
  /// Logical pixels, so the arc keeps its weight on every screen density
  /// (the focus ring is physical pixels; a spinner is a glyph).
  static const double spinnerStroke = 2;

  /// KitChip.md: the visual pill height; its 48 dp target is padding.
  static const double chipHeight = 32;

  /// KitChoiceList.md: a choice row's minimum height (KIT-25).
  static const double choiceRowMinHeight = 56;

  /// KitComposer.md: the Send, Stop and mic circles.
  static const double composerActionSize = 40;

  /// KitComposer.md: Stop's ground square (corners a quarter of it).
  static const double composerStopSquare = 14;

  /// The live line's dot in a running turn (concept C): the small mint
  /// mark before "Writing index.html · 12 s".
  static const double liveDot = 8;

  /// KitBreadcrumb.md: the widest an ancestor crumb grows before it truncates.
  static const double crumbMaxWidth = 160;

  /// KitDetailsFold.md: the label column from expanded up.
  static const double detailsLabelColumn = 160;

  /// KitMotionParts.md: a disabled part's opacity.
  static const double disabledAlpha = .38;

  /// KitMotionParts.md: a stale value's dim.
  static const double staleAlpha = .6;

  /// KitIcon.md: the alpha of a duotone glyph's background.
  static const double duotoneWash = .2;

  /// KitField.md: a field's corners.
  static const double fieldRadius = 14;

  /// KitImage.md: the initials' text-scale clamp in a fixed avatar (A11Y-8).
  static const double monogramMaxTextScale = 1.3;

  /// KitIconButton.md, KitMenu.md, KitTerm.md: the one popover radius (tooltip, menu, term bubble).
  static const double popoverRadius = 14;

  /// KitMessage.md, KitQueuedMessage.md: a prompt bubble's corners (VL §5).
  static const double bubbleRadius = 20;

  /// KitMessage.md, KitQueuedMessage.md: the bubble's bottom-end corner (VL §5).
  static const double bubbleTailRadius = 6;

  /// KitLogPanel.md: the folded log panel's height in lines.
  static const int logFoldedLines = 12;

  /// KitTerm.md: the window-height share above which the explanation opens as a sheet.
  static const double termBubbleMaxHeight = .4;

  /// KitStatusLine.md, KitAskLine.md: the status and ask line minimum height.
  static const double statusLineMinHeight = 52;

  /// KitProgress.md, KitChecklist.md: the thin indeterminate bar.
  static const double loadingBarHeight = 2;

  /// The inside of a panel (G20: panel padding 16), for parts whose default
  /// padding must be a compile-time constant.
  static const double panelPadding = 16;

  /// Between a row's title and the line under it (its supporting line or
  /// its `below` part).
  static const double rowLineGap = 2;

  /// One placeholder row of `KitSkeletonRows`.
  static const double skeletonRowHeight = 64;

  /// A visually hidden live region keeps one logical pixel, so screen
  /// readers keep its node (A11Y-3).
  static const double liveRegionSize = 1;

  /// The ring of a [KitChoiceList] radio or check mark: a glyph stroke in
  /// logical pixels, like [spinnerStroke].
  static const double choiceMarkStroke = 2;

  /// The corners of a [KitChoiceList] check mark.
  static const double choiceMarkRadius = 4;

  /// `KitPriorityGlyph`'s urgent plate and its bars.
  static const double priorityPlateRadius = 4;
  static const double priorityBarRadius = 1;

  /// KitProgress.md, KitChecklist.md, KitProgressRow.md: the job bar.
  static const double progressBarHeight = 4;

  /// KitProgress.md, KitProgressRow.md: the progress bar's corners.
  static const double progressBarRadius = 2;

  /// KitNav.md: the dock and rail labels' text-scale clamp (A11Y-8).
  static const double navLabelMaxScale = 2;

  /// KitLevelMeter.md: the number of bars.
  static const int meterBars = 9;

  /// KitLevelMeter.md: a bar's width, snapped to physical pixels.
  static const double meterBarWidth = 6;

  /// KitLevelMeter.md: the centre bar's height.
  static const double meterBarMin = 12;

  /// KitLevelMeter.md: the end bars' height.
  static const double meterBarMax = 20;

  /// KitNeedsYou.md: a count badge's height (a pill).
  static const double badgeHeight = 18;

  /// KitNeedsYou.md: a count badge's minimum width.
  static const double badgeMinWidth = 18;

  /// KitNeedsYou.md: the badge's text-scale clamp (A11Y-8).
  static const double badgeTextScaleMax = 1.3;

  /// KitNeedsYou.md: how far a count badge sits past its child's top-end
  /// corner, on both axes: a third of [badgeHeight].
  static const double badgeOffset = badgeHeight / 3;

  /// KitQr.md: the QR code's largest side.
  static const double qrMaxSize = 240;

  /// KitQr.md: the quiet zone the QR standard requires, in modules.
  static const int qrQuietModules = 4;

  /// KitRequestCard.md: the needs-you ring outside the border (LOOK-20).
  static const double needsYouRingWidth = 4;

  /// KitRequestCard.md: the needs-you ring's attention alpha.
  static const double needsYouRingAlpha = .06;

  /// KitRequestCard.md: the question look's "Needs you" dot.
  static const double needsYouDotSize = 8;

  /// KitRequestCard.md: the question look's attention line, the attention
  /// colour at this alpha (stronger than `attentionLine` on its calm card).
  static const double needsYouLineAlpha = .5;

  /// KitRequestCard.md: the question look's attention edge at the start.
  static const double needsYouEdgeWidth = 4;

  /// KitRequestCard.md: the request card's tile.
  static const double requestTileSize = 36;

  /// KitRequestCard.md: the request card tile's corners.
  static const double requestTileRadius = 9;

  /// KitRequestCard.md: the card's window-height cap at 2.0 text.
  static const double requestMaxHeightShare = .45;

  /// KitRequestCard.md: the same cap at 2.5 text, where a decide button
  /// wraps to two lines and the answers alone outgrow 45 %.
  static const double requestMaxHeightShareLarge = .6;

  /// KitScanner.md: the side of the square scan window.
  static const double scannerWindow = 240;

  /// KitScanner.md: the length of each corner bracket's arms.
  static const double scannerBracket = 28;

  /// KitScenes.md: an illustration's wash alpha.
  static const double sceneWashAlpha = .12;

  /// KitScenes.md: an illustration's default page width.
  static const double illustrationPage = 160;

  /// KitScenes.md: an illustration's inline width (KitStateView).
  static const double illustrationInline = 88;

  /// KitStateView.md: the page state's top and bottom padding.
  static const double stateVerticalPadding = 32;

  /// KitStatusMark.md: the leading status slot.
  static const double markSlotSize = 32;

  /// KitStatusMark.md: the waiting ring.
  static const double markRingSize = 10;

  /// KitStatusMark.md: the still-working dot under reduced motion.
  static const double markDotSize = 12;

  /// KitSwatch.md: the narrowest theme tile.
  static const double swatchMinWidth = 112;

  /// KitSwatch.md: the miniature's height.
  static const double swatchPreviewHeight = 56;

  /// KitSwatch.md: the accent and success dots.
  static const double swatchDot = 12;

  /// KitTerminalView.md: the terminal face's text-scale clamp (A11Y-8).
  static const double terminalMaxTextScale = 2;

  /// KitTerminalView.md: the key caps' text-scale clamp (A11Y-8).
  static const double terminalKeyMaxTextScale = 1.3;

  /// KitWorkGraph.md: a node's width at 1x text.
  static const double graphNodeWidth = 156;

  /// KitWorkGraph.md: the gap between columns.
  static const double graphColumnGap = 24;

  /// KitWorkGraph.md: the gap between rows.
  static const double graphRowGap = 48;

  /// KitWorkGraph.md: the rows gutter's lane cap.
  static const int graphMaxLanes = 6;

  /// KitWorkGraph.md: the dash length of blocked links.
  static const double graphDash = 4;

  /// KitQr.md: the QR ink, dark on light in every theme (graphiteLight).
  static Color get qrInk => graphiteLight.text1;

  /// KitQr.md: the QR paper (graphiteLight's `surface2`, pure white).
  static Color get qrPaper => graphiteLight.surface2;

  /// The kit's one status tone map (README.md decision D12): neutral →
  /// secondary, progress → accent, ok → success, attention → primary,
  /// failure → primary. Attention's amber belongs only to the needs-you
  /// parts (LOOK-4, LOOK-24); a failure is said in words and a neutral
  /// error glyph, not in red (LOOK-5, B2 interim). Attention and failure
  /// share `text1` and differ by [glyphFor]'s shape. KitIcon.status and
  /// KitStatusMark read this, never a local map.
  static KitTextTone toneFor(AppStatusTone status) => switch (status) {
    AppStatusTone.neutral => KitTextTone.secondary,
    AppStatusTone.progress => KitTextTone.accent,
    AppStatusTone.ok => KitTextTone.success,
    AppStatusTone.attention => KitTextTone.primary,
    AppStatusTone.failure => KitTextTone.primary,
  };

  /// The one glyph per status, paired with [toneFor]: every status has its
  /// own shape, so no state is told apart by colour alone (STATE-9): an
  /// empty ring idle, turning arrows under way, a check done, a warning
  /// triangle needs-you, the neutral error glyph failed.
  static IconData glyphFor(AppStatusTone status) => switch (status) {
    AppStatusTone.neutral => AppIconography.radioEmpty,
    AppStatusTone.progress => AppIconography.sync,
    AppStatusTone.ok => AppIconography.check,
    AppStatusTone.attention => AppIconography.warning,
    AppStatusTone.failure => AppIconography.error,
  };

  /// [toneFor] resolved against [roles].
  static Color toneColor(ThemeRoles roles, AppStatusTone status) =>
      KitText.toneColor(roles, toneFor(status));

  /// KitImage.md: the avatar's failed-image badge (the [glyphFor] failure
  /// glyph on a `ground` ring), and the ring's width.
  static const double avatarBadgeSize = 16;
  static const double avatarBadgeRing = 2;

  /// KitSurface.md: [shape] as a border with the kit radii.
  ShapeBorder shapeOf(KitShape shape) {
    RoundedRectangleBorder r(double radius) =>
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));
    return switch (shape) {
      KitShape.square => const RoundedRectangleBorder(),
      KitShape.tile => r(iconTileRadius),
      KitShape.code => r(codeRadius),
      KitShape.button => r(buttonRadius),
      KitShape.panel => r(panelCornerRadius),
      KitShape.card => r(cardRadius),
      KitShape.dialog => r(panelRadius),
      KitShape.sheet => RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(sheetRadius)),
      ),
      KitShape.pill => pillBorder(),
      KitShape.circle => const CircleBorder(),
    };
  }

  /// KitSurface.md: the role colour of surface step [level].
  Color fillOf(KitSurfaceLevel level) => switch (level) {
    KitSurfaceLevel.ground => roles.ground,
    KitSurfaceLevel.surface1 => roles.surface1,
    KitSurfaceLevel.surface2 => roles.surface2,
    KitSurfaceLevel.surface3 => roles.surface3,
  };

  /// KitProgressRow.md: the four stacked-bar fills, from existing roles.
  List<Color> get segmentFills => [
    roles.accent,
    roles.text2,
    roles.text3,
    roles.surface3,
  ];

  /// The tokens in force: the theme's extension (or ones derived from it)
  /// under the Style's Fine-tune knobs ([styled]). At Round and Comfortable
  /// this is the theme's own object, so nothing changes.
  static KitTokens of(BuildContext context) {
    final theme = Theme.of(context);
    final base = theme.extension<KitTokens>() ?? KitTokens.fallback(theme);
    final knobs = KitStyles.knobsOf(context);
    final spec = kitStyleSpec(KitStyles.styleOf(context)).tokens;
    if (knobs.corners == KitCorners.round &&
        knobs.density == KitDensity.comfortable &&
        spec.type.isNone) {
      return base;
    }
    return base.styled(knobs, sharpRadius: spec.sharpRadius, type: spec.type);
  }

  static final Expando<
    Map<(KitCorners, KitDensity, double, KitStyleType), KitTokens>
  >
  _styledCache = Expando('KitTokens.styled');

  /// These tokens under Corners and Density (contract §3.1, §3.7): radii
  /// through [KitStyles.corner], and the compact row, gap and button sizes.
  /// Touch targets never shrink. Memoised per base and knobs.
  KitTokens styled(
    KitKnobs knobs, {
    double sharpRadius = 2,
    KitStyleType type = const KitStyleType(),
  }) {
    if (knobs.corners == KitCorners.round &&
        knobs.density == KitDensity.comfortable &&
        type.isNone) {
      return this;
    }
    final cache = _styledCache[this] ??= {};
    return cache[(knobs.corners, knobs.density, sharpRadius, type)] ??= _styled(
      knobs,
      sharpRadius,
      type,
    );
  }

  KitTokens _styled(KitKnobs knobs, double sharpRadius, KitStyleType type) {
    // Hook H1: the token text styles take the Style's type tweaks too.
    final arabic = rowTitle.fontFamily == 'sans-serif';
    TextStyle t(KitTextRole role, TextStyle style) =>
        KitText.applyType(role, style, type, arabic: arabic);
    double r(double radius, {double? sharp}) => KitStyles.corner(
      radius,
      knobs.corners,
      sharp: sharp == null ? sharpRadius : math.min(sharp, sharpRadius * 2),
    );
    final compact = knobs.density == KitDensity.compact;
    return KitTokens(
      roles: roles,
      space1: space1,
      space2: space2,
      space3: space3,
      space4: space4,
      space5: space5,
      space6: space6,
      rail: rail,
      sheetSurface: sheetSurface,
      panelSurface: panelSurface,
      sideSheetSurface: sideSheetSurface,
      scrim: scrim,
      sheetRadius: r(sheetRadius, sharp: 4),
      panelRadius: r(panelRadius),
      sheetElevation: sheetElevation,
      panelElevation: panelElevation,
      sideSheetElevation: sideSheetElevation,
      panelInset: panelInset,
      handleColor: handleColor,
      handleSize: handleSize,
      handleHeight: handleHeight,
      muted: muted,
      detailsSurface: detailsSurface,
      detailsRadius: r(detailsRadius),
      markSize: markSize,
      markIconSize: markIconSize,
      markTintAlpha: markTintAlpha,
      markRadius: r(markRadius),
      maxIconScale: maxIconScale,
      sheetTitle: t(KitTextRole.title, sheetTitle),
      sheetSubtitle: t(KitTextRole.secondary, sheetSubtitle),
      confirmTitle: t(KitTextRole.title, confirmTitle),
      confirmBody: t(KitTextRole.body, confirmBody),
      note: t(KitTextRole.secondary, note),
      technicalValue: technicalValue,
      typedName: typedName,
      accent: accent,
      minTarget: minTarget,
      smallIconSize: smallIconSize,
      panelCornerRadius: r(panelCornerRadius),
      cardRadius: r(cardRadius),
      buttonRadius: r(buttonRadius),
      // Compact steps stay on the 4 dp scale (52 → 48, 56 → 48, 64 → 56,
      // 24 → 16, 8 → 4); touch targets never shrink.
      buttonHeight: compact
          ? math.max(minTarget, buttonHeight - space1)
          : buttonHeight,
      iconTileSize: iconTileSize,
      iconTileRadius: r(iconTileRadius),
      codeRadius: r(codeRadius),
      composerRadius: r(composerRadius, sharp: 4),
      composerRadiusWide: r(composerRadiusWide),
      rowHeight: compact ? math.max(minTarget, rowHeight - space2) : rowHeight,
      rowHeightTwoLine: compact
          ? math.max(minTarget, rowHeightTwoLine - space2)
          : rowHeightTwoLine,
      gutter: gutter,
      sectionGap: compact ? sectionGap - space2 : sectionGap,
      labelGap: compact ? labelGap - space1 : labelGap,
      navHeight: navHeight,
      navRadius: r(navRadius, sharp: 4),
      rowTitle: t(KitTextRole.rowTitle, rowTitle),
      rowSupporting: t(KitTextRole.secondary, rowSupporting),
      rowValue: t(KitTextRole.secondary, rowValue),
      sectionLabel: t(KitTextRole.label, sectionLabel),
      cardCaption: t(KitTextRole.caption, cardCaption),
      cardTitle: t(KitTextRole.headline, cardTitle),
      fieldCorner: r(fieldCorner),
      popoverCorner: r(popoverCorner),
      bubbleCorner: r(bubbleCorner),
      bubbleTailCorner: r(bubbleTailCorner),
      pillHeight: compact ? pillHeight - 4 : pillHeight,
      panelInner: compact ? panelInner - 4 : panelInner,
      pillRadius: knobs.corners == KitCorners.sharp ? sharpRadius : pillRadius,
    );
  }

  /// [size] grown with the person's text size, up to [maxIconScale], so a
  /// leading icon keeps up with the words beside it.
  double iconSize(BuildContext context, double size) => MediaQuery.textScalerOf(
    context,
  ).clamp(maxScaleFactor: maxIconScale).scale(size);

  /// Tokens derived from [theme]'s roles and type.
  factory KitTokens.fallback(ThemeData theme) =>
      KitTokens.fromRoles(ThemeRoles.resolve(theme), theme.textTheme);

  /// The visual language's tokens in the colours of [r], with [text] (the
  /// theme's type scale, already in the kit's roles) for the styles.
  factory KitTokens.fromRoles(ThemeRoles r, TextTheme text) {
    // The theme's face (Geist, or the system face under an Arabic locale,
    // where tracking stays zero for connected shaping) with the role's
    // metrics; mono keeps its own face.
    final face = text.bodyLarge;
    final system = face?.fontFamily == 'sans-serif';
    TextStyle role(KitTextRole role, Color color) {
      final style = KitText.styleFor(role);
      if (role == KitTextRole.mono) return style.copyWith(color: color);
      return style.copyWith(
        fontFamily: face?.fontFamily,
        fontFamilyFallback: face?.fontFamilyFallback,
        letterSpacing: system ? 0 : style.letterSpacing,
        color: color,
      );
    }

    return KitTokens(
      roles: r,
      space1: 4,
      space2: 8,
      space3: 12,
      space4: 16,
      space5: 20,
      space6: 24,
      rail: 20,
      sheetSurface: r.surface2,
      panelSurface: r.surface2,
      sideSheetSurface: r.surface2,
      scrim: r.scrim,
      sheetRadius: 30,
      panelRadius: 24,
      sheetElevation: 0,
      panelElevation: 0,
      sideSheetElevation: 0,
      panelInset: 24,
      handleColor: r.text3,
      handleSize: const Size(36, 5),
      handleHeight: 22,
      muted: r.text2,
      detailsSurface: r.isDark ? r.surface1 : r.ground,
      detailsRadius: 14,
      markSize: 44,
      markIconSize: 22,
      markTintAlpha: .16,
      markRadius: 12,
      maxIconScale: 1.5,
      sheetTitle: role(KitTextRole.title, r.text1),
      sheetSubtitle: role(KitTextRole.secondary, r.text2),
      confirmTitle: role(KitTextRole.title, r.text1),
      confirmBody: role(KitTextRole.body, r.text2),
      note: role(KitTextRole.secondary, r.text2),
      technicalValue: role(KitTextRole.mono, r.text1),
      typedName: role(KitTextRole.mono, r.text1),
      accent: r.accent,
      minTarget: 48,
      smallIconSize: 20,
      panelCornerRadius: 18,
      cardRadius: 22,
      buttonRadius: 14,
      buttonHeight: 52,
      iconTileSize: 28,
      iconTileRadius: 9,
      codeRadius: 14,
      composerRadius: 26,
      composerRadiusWide: 18,
      rowHeight: 56,
      rowHeightTwoLine: 64,
      gutter: 16,
      sectionGap: 24,
      labelGap: 8,
      navHeight: 60,
      navRadius: 22,
      rowTitle: role(KitTextRole.rowTitle, r.text1),
      rowSupporting: role(KitTextRole.secondary, r.text2),
      rowValue: role(KitTextRole.secondary, r.text3),
      sectionLabel: role(KitTextRole.label, r.text2),
      cardCaption: role(KitTextRole.caption, r.attention),
      cardTitle: role(KitTextRole.headline, r.text1),
    );
  }

  @override
  KitTokens copyWith({
    Color? sheetSurface,
    Color? panelSurface,
    Color? sideSheetSurface,
    Color? scrim,
    double? sheetRadius,
    double? panelRadius,
    Color? muted,
    TextStyle? sheetTitle,
    TextStyle? confirmTitle,
    TextStyle? confirmBody,
  }) => KitTokens(
    roles: roles,
    space1: space1,
    space2: space2,
    space3: space3,
    space4: space4,
    space5: space5,
    space6: space6,
    rail: rail,
    sheetSurface: sheetSurface ?? this.sheetSurface,
    panelSurface: panelSurface ?? this.panelSurface,
    sideSheetSurface: sideSheetSurface ?? this.sideSheetSurface,
    scrim: scrim ?? this.scrim,
    sheetRadius: sheetRadius ?? this.sheetRadius,
    panelRadius: panelRadius ?? this.panelRadius,
    sheetElevation: sheetElevation,
    panelElevation: panelElevation,
    sideSheetElevation: sideSheetElevation,
    panelInset: panelInset,
    handleColor: handleColor,
    handleSize: handleSize,
    handleHeight: handleHeight,
    muted: muted ?? this.muted,
    detailsSurface: detailsSurface,
    detailsRadius: detailsRadius,
    markSize: markSize,
    markIconSize: markIconSize,
    markTintAlpha: markTintAlpha,
    markRadius: markRadius,
    maxIconScale: maxIconScale,
    sheetTitle: sheetTitle ?? this.sheetTitle,
    sheetSubtitle: sheetSubtitle,
    confirmTitle: confirmTitle ?? this.confirmTitle,
    confirmBody: confirmBody ?? this.confirmBody,
    note: note,
    technicalValue: technicalValue,
    typedName: typedName,
    accent: accent,
    minTarget: minTarget,
    smallIconSize: smallIconSize,
    panelCornerRadius: panelCornerRadius,
    cardRadius: cardRadius,
    buttonRadius: buttonRadius,
    buttonHeight: buttonHeight,
    iconTileSize: iconTileSize,
    iconTileRadius: iconTileRadius,
    codeRadius: codeRadius,
    composerRadius: composerRadius,
    composerRadiusWide: composerRadiusWide,
    rowHeight: rowHeight,
    rowHeightTwoLine: rowHeightTwoLine,
    gutter: gutter,
    sectionGap: sectionGap,
    labelGap: labelGap,
    navHeight: navHeight,
    navRadius: navRadius,
    rowTitle: rowTitle,
    rowSupporting: rowSupporting,
    rowValue: rowValue,
    sectionLabel: sectionLabel,
    cardCaption: cardCaption,
    cardTitle: cardTitle,
    fieldCorner: fieldCorner,
    popoverCorner: popoverCorner,
    bubbleCorner: bubbleCorner,
    bubbleTailCorner: bubbleTailCorner,
    pillHeight: pillHeight,
    panelInner: panelInner,
    pillRadius: pillRadius,
  );

  @override
  KitTokens lerp(covariant KitTokens? other, double t) {
    if (other == null) return this;
    double d(double a, double b) => lerpDouble(a, b, t)!;
    Color c(Color a, Color b) => Color.lerp(a, b, t)!;
    TextStyle s(TextStyle a, TextStyle b) => TextStyle.lerp(a, b, t)!;
    return KitTokens(
      roles: roles.lerp(other.roles, t),
      space1: d(space1, other.space1),
      space2: d(space2, other.space2),
      space3: d(space3, other.space3),
      space4: d(space4, other.space4),
      space5: d(space5, other.space5),
      space6: d(space6, other.space6),
      rail: d(rail, other.rail),
      sheetSurface: c(sheetSurface, other.sheetSurface),
      panelSurface: c(panelSurface, other.panelSurface),
      sideSheetSurface: c(sideSheetSurface, other.sideSheetSurface),
      scrim: c(scrim, other.scrim),
      sheetRadius: d(sheetRadius, other.sheetRadius),
      panelRadius: d(panelRadius, other.panelRadius),
      sheetElevation: d(sheetElevation, other.sheetElevation),
      panelElevation: d(panelElevation, other.panelElevation),
      sideSheetElevation: d(sideSheetElevation, other.sideSheetElevation),
      panelInset: d(panelInset, other.panelInset),
      handleColor: c(handleColor, other.handleColor),
      handleSize: Size.lerp(handleSize, other.handleSize, t)!,
      handleHeight: d(handleHeight, other.handleHeight),
      muted: c(muted, other.muted),
      detailsSurface: c(detailsSurface, other.detailsSurface),
      detailsRadius: d(detailsRadius, other.detailsRadius),
      markSize: d(markSize, other.markSize),
      markIconSize: d(markIconSize, other.markIconSize),
      markTintAlpha: d(markTintAlpha, other.markTintAlpha),
      markRadius: d(markRadius, other.markRadius),
      maxIconScale: d(maxIconScale, other.maxIconScale),
      sheetTitle: s(sheetTitle, other.sheetTitle),
      sheetSubtitle: s(sheetSubtitle, other.sheetSubtitle),
      confirmTitle: s(confirmTitle, other.confirmTitle),
      confirmBody: s(confirmBody, other.confirmBody),
      note: s(note, other.note),
      technicalValue: s(technicalValue, other.technicalValue),
      typedName: s(typedName, other.typedName),
      accent: c(accent, other.accent),
      minTarget: d(minTarget, other.minTarget),
      smallIconSize: d(smallIconSize, other.smallIconSize),
      panelCornerRadius: d(panelCornerRadius, other.panelCornerRadius),
      cardRadius: d(cardRadius, other.cardRadius),
      buttonRadius: d(buttonRadius, other.buttonRadius),
      buttonHeight: d(buttonHeight, other.buttonHeight),
      iconTileSize: d(iconTileSize, other.iconTileSize),
      iconTileRadius: d(iconTileRadius, other.iconTileRadius),
      codeRadius: d(codeRadius, other.codeRadius),
      composerRadius: d(composerRadius, other.composerRadius),
      composerRadiusWide: d(composerRadiusWide, other.composerRadiusWide),
      rowHeight: d(rowHeight, other.rowHeight),
      rowHeightTwoLine: d(rowHeightTwoLine, other.rowHeightTwoLine),
      gutter: d(gutter, other.gutter),
      sectionGap: d(sectionGap, other.sectionGap),
      labelGap: d(labelGap, other.labelGap),
      navHeight: d(navHeight, other.navHeight),
      navRadius: d(navRadius, other.navRadius),
      rowTitle: s(rowTitle, other.rowTitle),
      rowSupporting: s(rowSupporting, other.rowSupporting),
      rowValue: s(rowValue, other.rowValue),
      sectionLabel: s(sectionLabel, other.sectionLabel),
      cardCaption: s(cardCaption, other.cardCaption),
      cardTitle: s(cardTitle, other.cardTitle),
      fieldCorner: d(fieldCorner, other.fieldCorner),
      popoverCorner: d(popoverCorner, other.popoverCorner),
      bubbleCorner: d(bubbleCorner, other.bubbleCorner),
      bubbleTailCorner: d(bubbleTailCorner, other.bubbleTailCorner),
      pillHeight: d(pillHeight, other.pillHeight),
      panelInner: d(panelInner, other.panelInner),
      pillRadius: t < .5 ? pillRadius : other.pillRadius,
    );
  }
}
