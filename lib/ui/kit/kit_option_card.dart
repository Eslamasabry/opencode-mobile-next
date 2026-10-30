import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/effects.dart';
import '../app_iconography.dart';
import '../theme_roles.dart';
import 'kit_icon.dart';
import 'kit_live_background.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'live_background/live_mock_chat.dart';
import 'style/kit_signal_slip.dart' show kitSignalSample;
import 'style/kit_style.dart';
import 'style/kit_style_theme.dart';

/// One visual choice, drawn: a theme as a compact swatch in its own
/// colours, a look as its live sample, a glowing-border style as the border
/// itself, a surface as a small sample of it, or an icon with a line of words
/// (docs/ux-system/kit-api/KitOptionCard.md). The chosen card has an accent
/// ring and a check badge, so choosing is looking, never colour alone.
///
/// - [KitOptionCard.theme]: a swatch of its own [ThemeRoles]: its ground with
///   the person's live look painted on it as one still frame, a stripe of its
///   main colour and a dot of its partner colour, the name under it. Under
///   Saver ([saverLook]) the ground is black with the look drawn faintly.
///   Disabled (a theme this phone cannot offer) says its reason on the swatch.
/// - [KitOptionCard.look]: a look behind a tiny mock chat, as one still
///   frame (the page behind shows the chosen one moving).
/// - [KitOptionCard.glow]: the message box with its border drawn the way the
///   style draws it.
/// - [KitOptionCard.surface]: a small panel made the way the surface style
///   makes it (Solid, Soft, Brutal, Paper). Disabled, its reason is the host's to say
///   once under the row (all three share it); it is still announced.
/// - [KitOptionCard.plain]: an icon, a title and one line of words.
///
/// Cards are as tall as their content. Inside a [KitCarousel] give it
/// [KitOptionCard.themeHeight] or [KitOptionCard.lookHeight].
///
/// States: disabled.
class KitOptionCard extends StatelessWidget {
  /// A theme: a compact swatch in [roles] with [label] under it.
  const KitOptionCard.theme({
    super.key,
    required ThemeRoles this.roles,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.disabledReason,
    this.saverLook,
  }) : look = null,
       glowStyle = null,
       surfaceStyle = null,
       icon = null,
       caption = null,
       _kind = _Kind.theme,
       assert(
         onPressed != null || disabledReason != null,
         'KitOptionCard: disabledReason is required when onPressed is null.',
       );

  /// A look, as one still frame, behind a tiny mock chat; a null [look] is
  /// "Off": the same chat on the plain ground.
  const KitOptionCard.look({
    super.key,
    required this.look,
    required this.label,
    required this.selected,
    required this.onPressed,
  }) : roles = null,
       glowStyle = null,
       surfaceStyle = null,
       icon = null,
       caption = null,
       disabledReason = null,
       saverLook = null,
       _kind = _Kind.look;

  /// The glowing border of one [style], drawn on a message box; a null
  /// [style] is "Off": the box with its plain edge. [caption] says it in
  /// words.
  const KitOptionCard.glow({
    super.key,
    required KitGlowStyle? style,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.caption,
  }) : glowStyle = style,
       roles = null,
       look = null,
       surfaceStyle = null,
       icon = null,
       disabledReason = null,
       saverLook = null,
       _kind = _Kind.glow;

  /// A small panel made the way [style] makes surfaces.
  const KitOptionCard.surface({
    super.key,
    required KitSurfaceStyle style,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.disabledReason,
  }) : surfaceStyle = style,
       look = null,
       roles = null,
       glowStyle = null,
       icon = null,
       caption = null,
       saverLook = null,
       _kind = _Kind.surface,
       assert(
         onPressed != null || disabledReason != null,
         'KitOptionCard: disabledReason is required when onPressed is null.',
       );

  /// An icon, [label] and one line of words ([caption]).
  const KitOptionCard.plain({
    super.key,
    required IconData this.icon,
    required this.label,
    required this.caption,
    required this.selected,
    required this.onPressed,
  }) : roles = null,
       look = null,
       glowStyle = null,
       surfaceStyle = null,
       disabledReason = null,
       saverLook = null,
       _kind = _Kind.plain;

  final ThemeRoles? roles;
  final KitLiveStyle? look;
  final KitGlowStyle? glowStyle;
  final KitSurfaceStyle? surfaceStyle;
  final IconData? icon;

  /// A theme card under Saver: its ground turns black and this look (the
  /// person's stored one, which Saver keeps off) is drawn faintly on it, so
  /// the card is never an empty black square. Null: the live look as usual.
  final KitLiveStyle? saverLook;

  /// The choice's name, shown as written.
  final String label;
  final String? caption;

  /// In use now: the ring, the check and selected semantics.
  final bool selected;

  /// Null disables it; [disabledReason] is then required.
  final VoidCallback? onPressed;
  final String? disabledReason;
  final _Kind _kind;

  /// The height a theme swatch takes in a carousel at normal text.
  static const double themeHeight = 168;

  /// The height a look card takes in a carousel at normal text.
  static const double lookHeight = 190;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final glide = reduced ? Duration.zero : KitMotion.standard;
    final enabled = onPressed != null;
    final badge = _CheckBadge(selected: selected, glide: glide);
    if (_kind == _Kind.theme) {
      return KitTappable(
        onTap: onPressed,
        disabledReason: disabledReason,
        label: label,
        selected: selected,
        shape: KitShape.card,
        child: Semantics(
          value: selected ? l10n.kitSwatchInUse : null,
          inMutuallyExclusiveGroup: true,
          child: ExcludeSemantics(
            child: _ThemeSwatch(
              roles: this.roles!,
              label: label,
              selected: selected,
              enabled: enabled,
              reason: disabledReason,
              saverLook: saverLook,
              badge: badge,
              glide: glide,
            ),
          ),
        ),
      );
    }
    final media = switch (_kind) {
      _Kind.look => _LookSample(style: look),
      _Kind.glow => _GlowSample(style: glowStyle),
      _Kind.surface => _SurfaceSample(style: surfaceStyle!),
      _ => null,
    };
    final fills = _kind == _Kind.look;
    final words = Row(
      children: [
        Expanded(
          child: _WholeWords(
            child: KitText(
              label,
              role: KitTextRole.rowTitle,
              tone: enabled ? KitTextTone.primary : KitTextTone.tertiary,
              // A look's name may take two lines in a narrow grid cell, at
              // ordinary text (large text keeps the sample its room).
              maxLines:
                  fills && MediaQuery.textScalerOf(context).scale(16) / 16 < 1.5
                  ? 2
                  : 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ),
        if (_kind != _Kind.plain) badge,
      ],
    );
    final content = Padding(
      padding: EdgeInsets.all(tokens.space3),
      child: Column(
        mainAxisSize: fills ? MainAxisSize.max : MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_kind == _Kind.plain) ...[
            Row(
              children: [
                IconTheme.merge(
                  data: IconThemeData(
                    color: selected ? roles.accent : roles.text2,
                  ),
                  child: KitIcon(icon!, size: KitIconSize.small),
                ),
                const Spacer(),
                badge,
              ],
            ),
            SizedBox(height: tokens.space2),
          ] else if (fills)
            Expanded(child: media!)
          else ...[
            Opacity(opacity: enabled ? 1 : .45, child: media!),
            SizedBox(height: tokens.space2),
          ],
          if (fills) SizedBox(height: tokens.space2),
          words,
          if (caption != null)
            KitText(caption!, role: KitTextRole.secondary, maxLines: 3),
          // A surface card's reason is said once under its row by the host.
          if (disabledReason != null && _kind != _Kind.surface)
            KitText(disabledReason!, role: KitTextRole.secondary, maxLines: 3),
        ],
      ),
    );
    final own = BoxDecoration(
      color: roles.surface1,
      borderRadius: BorderRadius.circular(tokens.cardRadius),
      border: Border.all(
        color: selected ? roles.accent : roles.hairline,
        width: selected
            ? KitTokens.focusRingWidth(context)
            : KitTokens.hairlineWidth(context),
      ),
    );
    final asked = KitSurfaceLook.decorate(
      context,
      solid: own,
      selected: selected,
    );
    // A style that sets panels straight on the page (Paper) would leave the
    // card bare and its sample floating: a choice is a card on its own, so
    // it asks for the raised step, which every style frames (Paper's ruled
    // box; owner, 2026-10-22).
    final frame = KitSurfaceDecoration.isBare(asked)
        ? KitSurfaceLook.decorate(
            context,
            solid: own,
            selected: selected,
            level: KitSurfaceLevel.surface2,
          )
        : asked;
    // The chosen card keeps Solid's accent ring in every style: a style's
    // own chosen face (Brutal's pressed tile, Paper's box) is drawn under
    // it, so the choice is a frame, never the check alone. The card's own
    // face moved by a knob keeps its ring unless Edges › Off took it.
    final ringed =
        selected &&
        !identical(frame, own) &&
        (frame is! BoxDecoration || frame.border == null);
    final chosenRing = ringed
        ? KitSurfaceDecoration(
            borderRadius:
                KitSurfaceDecoration.radiusOf(frame) ??
                BorderRadius.circular(tokens.cardRadius),
            fill: Colors.transparent,
            edgeStart: roles.accent,
            edgeEnd: roles.accent,
            // Over the style's own edge (Brutal's ink box), never thinner.
            edgeWidth: math.max(
              KitTokens.focusRingWidth(context),
              frame is KitSurfaceDecoration ? frame.edgeWidth : 0,
            ),
          )
        : null;
    return KitTappable(
      onTap: onPressed,
      disabledReason: disabledReason,
      label: label,
      selected: selected,
      shape: KitShape.card,
      child: Semantics(
        value: selected ? l10n.kitSwatchInUse : null,
        inMutuallyExclusiveGroup: true,
        child: ExcludeSemantics(
          // The card is a content surface in the style (chosen: its
          // selected look); Solid keeps the ring in the accent.
          child: kitSurfaceBox(
            decoration: frame,
            // Inside the edge, as a Container insets it.
            child: chosenRing == null
                ? Padding(padding: frame.padding, child: content)
                : DecoratedBox(
                    position: DecorationPosition.foreground,
                    decoration: chosenRing,
                    child: Padding(padding: frame.padding, child: content),
                  ),
          ),
        ),
      ),
    );
  }
}

enum _Kind { theme, look, glow, surface, plain }

/// The chosen card's check: a small accent disc that fades in from slightly
/// smaller.
class _CheckBadge extends StatelessWidget {
  const _CheckBadge({required this.selected, required this.glide});

  final bool selected;
  final Duration glide;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return AnimatedScale(
      scale: selected ? 1 : KitMotion.markFrom,
      duration: glide,
      curve: KitMotion.enter,
      child: AnimatedOpacity(
        opacity: selected ? 1 : 0,
        duration: glide,
        curve: KitMotion.enter,
        // A compact disc (20 dp): the mark of the choice, not a button.
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: roles.accent,
            shape: BoxShape.circle,
          ),
          child: SizedBox.square(
            dimension: tokens.space5,
            child: Icon(
              AppIconography.check,
              size: tokens.space3 + tokens.space1 / 2,
              color: roles.onAccent,
            ),
          ),
        ),
      ),
    );
  }
}

/// A theme as a compact swatch: nothing in the tile reads the current
/// theme's colours. Its ground carries the person's live look as one still
/// frame (or, under Saver, a faint drawing of it on black); a stripe of the
/// main colour and a dot of the partner colour sit at its foot. The name is
/// under the tile; the ring and the check follow the current theme.
class _ThemeSwatch extends StatelessWidget {
  const _ThemeSwatch({
    required this.roles,
    required this.label,
    required this.selected,
    required this.enabled,
    required this.reason,
    required this.saverLook,
    required this.badge,
    required this.glide,
  });

  final ThemeRoles roles;
  final String label;
  final bool selected;
  final bool enabled;
  final String? reason;
  final KitLiveStyle? saverLook;
  final Widget badge;
  final Duration glide;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final frame = tokens.roles;
    // Inside the card's edge; never below square (Corners › Sharp).
    final radius = BorderRadius.circular(
      (tokens.cardRadius - tokens.space1).clamp(0.0, double.infinity),
    );
    final saver = saverLook;
    final swatch = saver == null ? roles : roles.saver;
    final ground = saver == null
        ? KitLiveBackground(
            roles: swatch,
            // A small sample reads at a look card's strength.
            strength: 1.6,
            still: true,
            child: const SizedBox.expand(),
          )
        : Opacity(
            opacity: .35,
            child: KitLiveBackground(
              style: saver,
              roles: swatch,
              strength: .8,
              still: true,
              ignoreSetting: true,
              child: const SizedBox.expand(),
            ),
          );
    final foot = Row(
      children: [
        Expanded(
          child: FractionallySizedBox(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: .72,
            child: Container(
              height: tokens.space1 + 2,
              decoration: BoxDecoration(
                color: swatch.accent,
                borderRadius: BorderRadius.circular(tokens.space1),
              ),
            ),
          ),
        ),
        SizedBox(width: tokens.space2),
        Container(
          width: tokens.space3,
          height: tokens.space3,
          decoration: BoxDecoration(
            color: swatch.partner,
            shape: BoxShape.circle,
          ),
        ),
      ],
    );
    final chosen = selected && enabled;
    final own = BoxDecoration(color: swatch.ground, borderRadius: radius);
    // The tile is a card in the person's Style (Brutal's ink edge and hard
    // shadow), its fill still the theme's own ground; Solid keeps [own].
    final styled = KitSurfaceLook.decorate(
      context,
      solid: own,
      color: swatch.ground,
      selected: chosen,
    );
    // The style's edge goes over the swatch's ground, which would hide it.
    final edge = switch (styled) {
      KitSurfaceDecoration(
        :final edgeStart,
        :final edgeEnd,
        :final edgeWidth,
        :final borderRadius,
      )
          when edgeWidth > 0 && (edgeStart.a > 0 || edgeEnd.a > 0) =>
        KitSurfaceDecoration(
          borderRadius: borderRadius,
          fill: Colors.transparent,
          edgeStart: edgeStart,
          edgeEnd: edgeEnd,
          edgeWidth: edgeWidth,
        ),
      _ => null,
    };
    final tile = AnimatedContainer(
      duration: glide,
      curve: KitMotion.enter,
      foregroundDecoration: !chosen && edge != null
          ? edge
          : BoxDecoration(
              borderRadius: radius,
              border: Border.all(
                color: chosen ? frame.accent : frame.hairline,
                width: chosen
                    ? KitTokens.focusRingWidth(context)
                    : KitTokens.hairlineWidth(context),
              ),
            ),
      decoration: styled,
      child: ClipRRect(
        borderRadius: radius,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Positioned.fill(child: ground),
            if (!enabled && reason != null)
              // Unavailable is said on the swatch, not faded (STATE-8).
              ColoredBox(
                color: frame.surface1.withValues(alpha: .86),
                child: Padding(
                  padding: EdgeInsets.all(tokens.space2),
                  child: Center(
                    child: KitText(
                      reason!,
                      role: KitTextRole.caption,
                      tone: KitTextTone.secondary,
                      textAlign: TextAlign.center,
                      maxLines: 4,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              )
            else
              Padding(
                padding: EdgeInsets.all(tokens.space3),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(alignment: AlignmentDirectional.topEnd, child: badge),
                    const Spacer(),
                    foot,
                  ],
                ),
              ),
          ],
        ),
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(child: tile),
        SizedBox(height: tokens.space2),
        Padding(
          padding: EdgeInsetsDirectional.only(start: tokens.space1),
          child: KitText(
            label,
            role: KitTextRole.rowTitle,
            tone: enabled ? KitTextTone.primary : KitTextTone.tertiary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
}

/// A tiny scene made the way one surface style makes it, whatever style is
/// in force: the theme under that style's own roles (Brutal's cream paper,
/// Paper's newsprint) on the page's plain ground, and on it a card holding a
/// line of words, a chip and the main action's button. Every piece comes
/// from the style's own resolver ([KitSurfaceLook.resolve] at the style's
/// defaults, compact so its shadows stay small), so a school's tuning shows
/// here with no change to this card. The difference reads before choosing
/// (owner, 2026-10-22: the light samples were blank pills that looked
/// alike).
class _SurfaceSample extends StatelessWidget {
  const _SurfaceSample({required this.style});

  final KitSurfaceStyle style;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final spec = kitStyleSpec(style).tokens;
    if (spec.track) {
      // A style on a track (Signal) shows its track and signals: a card
      // and a chip would look like any other style.
      final base = Theme.of(context).extension<KitTokens>() ?? tokens;
      return kitSignalSample(
        height: tokens.space6 * 2.6,
        radius: KitStyles.corner(
          base.cardRadius - tokens.space1,
          spec.defaults.corners,
          sharp: spec.sharpRadius,
        ),
      );
    }
    final knobs = spec.defaults.copyWith(density: KitDensity.compact);
    // The theme as this style makes it, not as the one in force does.
    final roles = ThemeRoles.resolve(
      kitStyleRestyled(Theme.of(context), style),
    );
    // The style's own corners, not the ones in force.
    final base = Theme.of(context).extension<KitTokens>() ?? tokens;
    BorderRadius corner(double radius) => BorderRadius.circular(
      KitStyles.corner(radius, knobs.corners, sharp: spec.sharpRadius),
    );
    Decoration piece(
      KitSurfaceRole role,
      BorderRadius radius, {
      Color? color,
      KitSurfaceLevel level = KitSurfaceLevel.surface2,
    }) => KitSurfaceLook.resolve(
      roles: roles,
      style: style,
      role: role,
      level: level,
      knobs: knobs,
      color: color,
      solid: BoxDecoration(
        color: color ?? roles.surface1,
        borderRadius: radius,
        border: color == null
            ? Border.all(
                color: roles.hairline,
                width: KitTokens.hairlineWidth(context),
              )
            : null,
      ),
    );

    final unit = tokens.space1;
    final pill = corner(999);
    final line = CustomPaint(
      size: Size.fromHeight(unit),
      painter: _Mark(
        roles.text1.withValues(alpha: .8),
        corner(unit / 2).topLeft.x,
      ),
    );
    // A chip, and the main action's button; both give way on a narrow card.
    Widget block(double width, Decoration decoration) => Flexible(
      child: SizedBox(
        width: width,
        height: unit * 3.5,
        child: DecoratedBox(decoration: decoration),
      ),
    );
    final row = Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        block(unit * 7, piece(KitSurfaceRole.control, pill)),
        SizedBox(width: unit),
        block(
          unit * 6,
          piece(KitSurfaceRole.control, pill, color: roles.accent),
        ),
      ],
    );
    final cardRadius = corner(base.cardRadius / 2);
    final card = piece(KitSurfaceRole.content, cardRadius);
    final words = Padding(
      padding: EdgeInsets.all(tokens.space2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          FractionallySizedBox(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: .7,
            child: line,
          ),
          row,
        ],
      ),
    );
    final mini = DecoratedBox(decoration: card, child: words);
    final stripRadius = corner(base.cardRadius - tokens.space1);
    return SizedBox(
      key: ValueKey('kit-style-sample-${style.name}'),
      height: tokens.space6 * 2.6,
      child: ClipRRect(
        borderRadius: stripRadius,
        child: Stack(
          fit: StackFit.expand,
          children: [
            DecoratedBox(
              decoration: piece(
                KitSurfaceRole.ground,
                stripRadius,
                color: roles.ground,
                level: KitSurfaceLevel.ground,
              ),
            ),
            Padding(
              // Room for the card's shadow inside the strip.
              padding: EdgeInsetsDirectional.fromSTEB(
                tokens.space2,
                tokens.space2,
                tokens.space2 + unit,
                tokens.space2 + unit,
              ),
              child: mini,
            ),
          ],
        ),
      ),
    );
  }
}

/// One mark of a sample's drawing (a line of words): a
/// rounded rectangle filling its size, [radius] at its corners.
class _Mark extends CustomPainter {
  const _Mark(this.color, this.radius);

  final Color color;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) => canvas.drawRRect(
    RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(radius)),
    Paint()..color = color,
  );

  @override
  bool shouldRepaint(_Mark old) => old.color != color || old.radius != radius;
}

class _LookSample extends StatelessWidget {
  const _LookSample({required this.style});

  /// Null: no look, the chat on the plain ground.
  final KitLiveStyle? style;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(
        tokens.iconTileRadius + tokens.space1,
      ),
      // One still frame: the preview above the picker shows the chosen look
      // moving, so a carousel of samples costs no battery.
      child: switch (style) {
        final style? => KitLiveBackground(
          style: style,
          strength: 1.6,
          ignoreSetting: true,
          still: true,
          child: liveMockChat(context, scale: .7),
        ),
        null => DecoratedBox(
          decoration: KitSurfaceDecoration(fill: tokens.roles.ground),
          child: liveMockChat(context, scale: .7),
        ),
      },
    );
  }
}

/// The message box with the border of one glow style, drawn so the two read
/// apart at a glance: Classic is a thin dim line with one bright head on it
/// (the head travels in the app); Soft ring is a full bright line inside a
/// wide soft wash with no head.
class _GlowSample extends StatelessWidget {
  const _GlowSample({required this.style});

  /// Null: no glow, the box's plain edge.
  final KitGlowStyle? style;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final soft = style == KitGlowStyle.softRing;
    final off = style == null;
    final radius = BorderRadius.circular(tokens.composerRadius);
    final box = Container(
      height: tokens.space6 * 1.5,
      padding: EdgeInsetsDirectional.only(
        start: tokens.space3,
        end: tokens.space1,
      ),
      decoration: BoxDecoration(
        color: roles.ground,
        borderRadius: radius,
        border: Border.all(
          color: off
              ? roles.hairline
              : soft
              ? roles.accent
              : roles.accent.withValues(alpha: .35),
          width: soft
              ? KitTokens.focusRingWidth(context) * 1.5
              : KitTokens.hairlineWidth(context),
        ),
        boxShadow: [
          if (soft) ...[
            BoxShadow(
              color: roles.accent.withValues(alpha: .55),
              blurRadius: tokens.space4,
              spreadRadius: tokens.space1 / 2,
            ),
          ],
        ],
      ),
      child: Row(
        children: [
          Container(
            width: tokens.space6,
            height: tokens.space1,
            decoration: BoxDecoration(
              color: roles.text3,
              borderRadius: BorderRadius.circular(tokens.space1),
            ),
          ),
          const Spacer(),
          Container(
            width: tokens.space5,
            height: tokens.space5,
            decoration: BoxDecoration(
              color: roles.accent,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Container(
                width: tokens.space2,
                height: tokens.space2,
                color: roles.onAccent,
              ),
            ),
          ),
        ],
      ),
    );
    return Padding(
      // Room for the wash so the card does not clip it.
      padding: EdgeInsets.symmetric(vertical: tokens.space2),
      child: soft || off
          ? box
          : Stack(
              clipBehavior: Clip.none,
              children: [
                box,
                // Classic's bright head, on the top edge.
                PositionedDirectional(
                  start: tokens.space6 * 1.4,
                  top: -KitTokens.focusRingWidth(context),
                  child: Container(
                    width: tokens.space6,
                    height: KitTokens.focusRingWidth(context) * 2,
                    decoration: BoxDecoration(
                      color: roles.accent,
                      borderRadius: BorderRadius.circular(tokens.space1),
                      boxShadow: [
                        BoxShadow(
                          color: roles.accent.withValues(alpha: .8),
                          blurRadius: tokens.space2,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

/// One swatch of a [KitAccentRow]: its stored [id], its [color] as it will be
/// used here (already guarded for this theme) and its name for a screen
/// reader.
@immutable
class KitAccentSwatch {
  const KitAccentSwatch({
    required this.id,
    required this.color,
    required this.label,
  });

  final String id;
  final Color color;
  final String label;
}

/// The Accent choice of Settings › Appearance (docs/ux-system/kit-api/
/// KitOptionCard.md "KitAccentRow"): a "Match theme" tile first (the theme's
/// own two colours, overlapping) and then round swatches, one line that
/// scrolls sideways when it does not fit. The chosen one has a ring in its
/// own colour and a check, so choosing is looking, never colour alone. Every
/// swatch is its own 48 dp target and one of a mutually exclusive group.
///
/// [KitAccentRow.grid]: every swatch in sight, four to a row (two rows for
/// Match and seven swatches), each with its name under it and the chosen one
/// ringed in the accent; nothing scrolls sideways. It sits on the host's
/// rails (no gutter of its own).
///
/// [selected] is a swatch's id, or null for "Match theme".
///
/// States: none — every swatch can always be chosen.
class KitAccentRow extends StatelessWidget {
  const KitAccentRow({
    super.key,
    required this.semanticsLabel,
    required this.matchLabel,
    required this.theme,
    required this.swatches,
    required this.selected,
    required this.onChanged,
  }) : grid = false;

  /// Four to a row with their names, nothing scrolling sideways.
  const KitAccentRow.grid({
    super.key,
    required this.semanticsLabel,
    required this.matchLabel,
    required this.theme,
    required this.swatches,
    required this.selected,
    required this.onChanged,
  }) : grid = true;

  /// The rows of four with names under the swatches.
  final bool grid;

  /// The group's name: "Accent".
  final String semanticsLabel;

  /// "Match theme".
  final String matchLabel;

  /// The theme's own roles: its main colour alone is drawn on the "Match
  /// theme" tile (no second hue in the chrome).
  final ThemeRoles theme;
  final List<KitAccentSwatch> swatches;

  /// The chosen swatch's id; null: "Match theme".
  final String? selected;

  /// Called with a swatch's id, or null for "Match theme".
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final glide = KitMotion.reduced(context) ? Duration.zero : KitMotion.quick;
    final main = theme.accent;
    final matchOn = selected == null;
    final dot = tokens.space4;
    if (grid) {
      final cells = <Widget>[
        _AccentCircle(
          key: const ValueKey('appearance-accent-match'),
          swatch: KitAccentSwatch(id: '', color: main, label: matchLabel),
          selected: matchOn,
          glide: glide,
          caption: matchLabel,
          ring: roles.accent,
          onTap: matchOn ? () {} : () => onChanged(null),
        ),
        for (final swatch in swatches)
          _AccentCircle(
            key: ValueKey('appearance-accent-${swatch.id}'),
            swatch: swatch,
            selected: swatch.id == selected,
            glide: glide,
            caption: swatch.label,
            ring: roles.accent,
            onTap: swatch.id == selected ? () {} : () => onChanged(swatch.id),
          ),
      ];
      const perRow = 4;
      return Semantics(
        container: true,
        explicitChildNodes: true,
        label: semanticsLabel,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var row = 0; row < cells.length; row += perRow) ...[
              if (row > 0) SizedBox(height: tokens.space2),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var i = row; i < row + perRow; i++)
                    Expanded(
                      child: i < cells.length
                          ? cells[i]
                          : const SizedBox.shrink(),
                    ),
                ],
              ),
            ],
          ],
        ),
      );
    }
    final matchOwn = BoxDecoration(
      color: roles.surface1,
      borderRadius: BorderRadius.circular(tokens.buttonRadius),
      border: Border.all(
        color: matchOn ? roles.accent : roles.hairline,
        width: matchOn
            ? KitTokens.focusRingWidth(context)
            : KitTokens.hairlineWidth(context),
      ),
    );
    // The tile is a control in the person's Style (Brutal's ink box);
    // Solid keeps its own.
    final matchFace = KitSurfaceLook.decorate(
      context,
      solid: matchOwn,
      role: KitSurfaceRole.control,
      selected: matchOn,
    );
    final matchRing = matchOn && !identical(matchFace, matchOwn)
        ? KitSurfaceDecoration(
            borderRadius:
                KitSurfaceDecoration.radiusOf(matchFace) ??
                BorderRadius.circular(tokens.buttonRadius),
            fill: Colors.transparent,
            edgeStart: roles.accent,
            edgeEnd: roles.accent,
            edgeWidth: KitTokens.focusRingWidth(context),
          )
        : null;
    // The tile is as tall as a swatch's target; its words may take a second
    // line at larger text.
    final match = KitTappable(
      key: const ValueKey('appearance-accent-match'),
      onTap: matchOn ? () {} : () => onChanged(null),
      label: matchLabel,
      selected: matchOn,
      shape: KitShape.button,
      child: Semantics(
        value: matchOn ? l10n.kitSwatchInUse : null,
        inMutuallyExclusiveGroup: true,
        child: ExcludeSemantics(
          child: AnimatedContainer(
            duration: glide,
            curve: KitMotion.enter,
            constraints: BoxConstraints(
              minHeight: tokens.minTarget + tokens.space3,
              minWidth: tokens.minTarget + tokens.space3,
            ),
            padding: EdgeInsets.symmetric(
              horizontal: tokens.space2,
              vertical: tokens.space2,
            ),
            decoration: matchFace,
            // A style's own chosen face keeps the ring in the accent over
            // it, so the choice is a frame.
            foregroundDecoration: matchRing,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _Dot(color: main, size: dot),
                SizedBox(height: tokens.space1),
                ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: tokens.minTarget * 1.2),
                  child: KitText(
                    matchLabel,
                    role: KitTextRole.caption,
                    tone: KitTextTone.secondary,
                    textAlign: TextAlign.center,
                    maxLines: 2,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: semanticsLabel,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            match,
            SizedBox(width: tokens.space2),
            for (final swatch in swatches)
              _AccentCircle(
                key: ValueKey('appearance-accent-${swatch.id}'),
                swatch: swatch,
                selected: swatch.id == selected,
                glide: glide,
                onTap: swatch.id == selected
                    ? () {}
                    : () => onChanged(swatch.id),
              ),
          ],
        ),
      ),
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot({required this.color, required this.size});

  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(color: color, shape: BoxShape.circle),
  );
}

/// One round swatch in its 48 dp target: chosen, a ring in its own colour
/// stands off it and a check sits on it.
class _AccentCircle extends StatelessWidget {
  const _AccentCircle({
    super.key,
    required this.swatch,
    required this.selected,
    required this.glide,
    required this.onTap,
    this.caption,
    this.ring,
  });

  final KitAccentSwatch swatch;
  final bool selected;
  final Duration glide;
  final VoidCallback onTap;

  /// The name under the swatch (the grid form).
  final String? caption;

  /// The chosen ring's colour; default the swatch's own.
  final Color? ring;

  /// [circle] with its name under it, in the grid form.
  Widget _withCaption(BuildContext context, Widget circle) {
    final words = caption;
    if (words == null) return circle;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        circle,
        KitText(
          words,
          role: KitTextRole.caption,
          tone: selected ? KitTextTone.primary : KitTextTone.secondary,
          textAlign: TextAlign.center,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final circle = tokens.minTarget - tokens.space3;
    return KitTappable(
      onTap: onTap,
      label: swatch.label,
      selected: selected,
      shape: KitShape.circle,
      child: Semantics(
        value: selected ? l10n.kitSwatchInUse : null,
        inMutuallyExclusiveGroup: true,
        child: ExcludeSemantics(
          child: _withCaption(
            context,
            SizedBox.square(
              dimension: tokens.minTarget,
              child: Center(
                child: AnimatedContainer(
                  duration: glide,
                  curve: KitMotion.enter,
                  padding: selected
                      ? EdgeInsets.all(tokens.space1 * .75)
                      : EdgeInsets.zero,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: selected ? ring ?? swatch.color : roles.hairline,
                      width: selected
                          ? KitTokens.focusRingWidth(context)
                          : KitTokens.hairlineWidth(context),
                    ),
                  ),
                  child: Container(
                    width: selected
                        ? circle -
                              tokens.space1 * 1.5 -
                              2 * KitTokens.focusRingWidth(context)
                        : circle,
                    height: selected
                        ? circle -
                              tokens.space1 * 1.5 -
                              2 * KitTokens.focusRingWidth(context)
                        : circle,
                    decoration: BoxDecoration(
                      color: swatch.color,
                      shape: BoxShape.circle,
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        AnimatedScale(
                          scale: selected ? 1 : KitMotion.markFrom,
                          duration: glide,
                          curve: KitMotion.enter,
                          child: AnimatedOpacity(
                            opacity: selected ? 1 : 0,
                            duration: glide,
                            curve: KitMotion.enter,
                            child: Icon(
                              AppIconography.check,
                              size: tokens.smallIconSize,
                              color: onColor(swatch.color),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Keeps a name's words whole: when its longest word is wider than the room,
/// the text is set smaller until that word fits, so a long name wraps between
/// words and never splits one ("Constellati/on").
class _WholeWords extends StatelessWidget {
  const _WholeWords({required this.child});

  final KitText child;

  @override
  Widget build(BuildContext context) {
    final style = KitText.styleOf(context, child.role);
    final scaler = MediaQuery.textScalerOf(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final room = constraints.maxWidth;
        if (!room.isFinite) return child;
        var widest = 0.0;
        for (final word in child.text.split(RegExp(r'\s+'))) {
          if (word.isEmpty) continue;
          final painter = TextPainter(
            text: TextSpan(text: word, style: style),
            textDirection: Directionality.of(context),
            textScaler: scaler,
            maxLines: 1,
          )..layout();
          if (painter.width > widest) widest = painter.width;
          painter.dispose();
        }
        // At large text the name is one ellipsised line: no word splits.
        if (child.maxLines != 2 || widest <= room || widest == 0) return child;
        final shrink = (room / widest).clamp(0.5, 1.0);
        return MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: _ShrunkScaler(scaler, shrink)),
          child: child,
        );
      },
    );
  }
}

class _ShrunkScaler extends TextScaler {
  const _ShrunkScaler(this.base, this.factor);

  final TextScaler base;
  final double factor;

  @override
  double scale(double fontSize) => base.scale(fontSize) * factor;

  @override
  double get textScaleFactor => scale(1);
}
