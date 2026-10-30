import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_panel.dart';
import 'kit_row.dart';
import 'kit_status_mark.dart';
import 'kit_surface_look.dart';
import 'kit_task_mark.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Choosing colours by looking (docs/ux-system/kit-api/KitSwatch.md,
/// kit-v2.md §5, §9.2): one theme, or one accent for the coming custom
/// theme, as a miniature the person picks by tapping.
///
/// [KitSwatch.new] shows a theme: a miniature of its own [roles] (its
/// ground, a `surface1` panel with a line of its `text1`, and its `accent`
/// and `success` colours) with the [label] under it. [KitSwatch.accent]
/// shows one guarded accent [color] as a circle, for the custom theme's
/// accent picker.
///
/// States: disabled.
///
/// Disabled always carries its reason. Selected (in use) is a look, shown in
/// its gallery's grid scenes.
///
/// The colours drawn in the miniature are the swatch's own [roles], never
/// the current theme's — a swatch stays true to the theme it offers even
/// when the app itself is in a different theme now. The frame around it
/// (the label, the check, the border, the focus ring) always follows the
/// *current* theme.
class KitSwatch extends StatelessWidget {
  /// A theme: a miniature in [roles] with [label] under it.
  const KitSwatch({
    super.key,
    required this.roles,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.disabledReason,
    this.swatchKey,
  }) : color = null,
       colorsAnotherSurface = false,
       assert(
         onPressed != null || disabledReason != null,
         'KitSwatch: disabledReason is required when onPressed is null.',
       );

  /// One accent for the custom theme: a circle of [color].
  const KitSwatch.accent({
    super.key,
    required Color this.color,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.disabledReason,
    this.swatchKey,
    this.colorsAnotherSurface = false,
  }) : roles = null,
       assert(
         onPressed != null || disabledReason != null,
         'KitSwatch: disabledReason is required when onPressed is null.',
       );

  /// The theme this swatch offers (null for [KitSwatch.accent]).
  final ThemeRoles? roles;

  /// The accent this swatch offers (null for [KitSwatch.new]).
  final Color? color;

  /// The pack's name, or the accent's own name ("Graphite", "Blue"), shown
  /// as written.
  final String label;

  /// In use now: a check and selected semantics with the value "In use".
  final bool selected;

  /// Null disables it; [disabledReason] is then required.
  final VoidCallback? onPressed;

  /// Why it cannot be picked ("Needs Android 12 or later"). Shown as
  /// visible text for [KitSwatch.new]; only in semantics for
  /// [KitSwatch.accent] (the picker never offers an invalid accent).
  final String? disabledReason;

  /// Kept by the host (e.g. `ValueKey('theme-pack-${id.name}')`).
  final Key? swatchKey;

  /// True when the colour paints something outside the app (a home-screen
  /// widget's accent), so it is not held to LOOK-39's distance from this
  /// theme's attention. The colour is still only a choice here.
  final bool colorsAnotherSurface;

  bool get _isAccent => color != null;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.chip(child: _buildContent(context));

  Widget _buildContent(BuildContext context) =>
      _SwatchBody(key: swatchKey, swatch: this);
}

/// A single-choice group of [KitSwatch]es (docs/ux-system/kit-api/KitSwatch.md
/// "Adaptive"): columns from the space it has, start-aligned, one Tab stop,
/// arrow keys move within it in reading order (KitChoiceList's group rule,
/// kit-v2.md §8.2).
///
/// States: none — its swatches carry their own looks.
class KitSwatchGrid extends StatelessWidget {
  const KitSwatchGrid({
    super.key,
    required this.label,
    required this.children,
    this.gridKey,
  });

  /// The group's name for semantics: "Theme", "Accent colour".
  final String label;

  /// All theme swatches or all accent swatches (asserted).
  final List<KitSwatch> children;

  final Key? gridKey;

  @override
  Widget build(BuildContext context) {
    assert(
      children.isEmpty ||
          children.every((c) => c._isAccent == children.first._isAccent),
      'KitSwatchGrid: children must all be KitSwatch (theme) or all '
      'KitSwatch.accent, never a mix.',
    );
    return _SwatchGridBody(key: gridKey, label: label, children: children);
  }
}

// -----------------------------------------------------------------------
// KitSwatch: focus, hover and the visual tile.
// -----------------------------------------------------------------------

/// Carried by [KitSwatchGrid] down to each [KitSwatch] so the grid's own
/// [FocusNode] is the one the swatch uses (roving tabindex: only the active
/// swatch is a Tab stop) and arrow keys reach the grid's navigation. Purely
/// an implementation seam between the two widgets in this file — invisible
/// outside it, so it changes nothing in the frozen public API.
class _SwatchFocusHost extends InheritedWidget {
  const _SwatchFocusHost({
    required this.node,
    required this.skipTraversal,
    required this.onOtherKey,
    required super.child,
  });

  final FocusNode node;
  final bool skipTraversal;
  final KeyEventResult Function(KeyEvent event) onOtherKey;

  static _SwatchFocusHost? maybe(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_SwatchFocusHost>();

  @override
  bool updateShouldNotify(_SwatchFocusHost oldWidget) =>
      oldWidget.node != node ||
      oldWidget.skipTraversal != skipTraversal ||
      oldWidget.onOtherKey != onOtherKey;
}

/// The interactive shell of a [KitSwatch]: semantics, focus (its own node
/// standalone, or the grid's when hosted), hover and the tap target. The
/// visual tile itself is [_SwatchTile].
class _SwatchBody extends StatefulWidget {
  const _SwatchBody({super.key, required this.swatch});

  final KitSwatch swatch;

  @override
  State<_SwatchBody> createState() => _SwatchBodyState();
}

class _SwatchBodyState extends State<_SwatchBody> {
  FocusNode? _ownNode;
  bool _hovered = false;

  FocusNode _nodeFor(_SwatchFocusHost? hosted) =>
      hosted?.node ?? (_ownNode ??= FocusNode(debugLabel: 'KitSwatch'));

  @override
  void dispose() {
    _ownNode?.dispose();
    super.dispose();
  }

  KeyEventResult _onKeyEvent(
    FocusNode node,
    KeyEvent event,
    _SwatchFocusHost? hosted,
  ) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final onPressed = widget.swatch.onPressed;
    final key = event.logicalKey;
    if (onPressed != null &&
        (key == LogicalKeyboardKey.enter ||
            key == LogicalKeyboardKey.numpadEnter ||
            key == LogicalKeyboardKey.space)) {
      onPressed();
      return KeyEventResult.handled;
    }
    if (hosted != null) return hosted.onOtherKey(event);
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final swatch = widget.swatch;
    final hosted = _SwatchFocusHost.maybe(context);
    final node = _nodeFor(hosted);
    final enabled = swatch.onPressed != null;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // One node per swatch: its name is the label, "In use" the value, the
    // reason the hint. The visible label, reason and tooltip below are
    // excluded so a screen reader never reads the name twice (A11Y-1).
    return Semantics(
      container: true,
      button: true,
      enabled: enabled,
      selected: swatch.selected,
      value: swatch.selected ? l10n.kitSwatchInUse : null,
      hint: swatch.disabledReason,
      label: swatch.label,
      inMutuallyExclusiveGroup: true,
      child: Focus(
        focusNode: node,
        canRequestFocus: enabled,
        skipTraversal: hosted?.skipTraversal ?? !enabled,
        onKeyEvent: (n, e) => _onKeyEvent(n, e, hosted),
        child: MouseRegion(
          cursor: enabled ? SystemMouseCursors.click : MouseCursor.defer,
          onEnter: (_) => setState(() => _hovered = true),
          onExit: (_) => setState(() => _hovered = false),
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: swatch.onPressed == null
                ? null
                : () {
                    // A tap also focuses it, like a real button: Enter or
                    // Space then repeats the same press (G14).
                    node.requestFocus();
                    swatch.onPressed!();
                  },
            child: ExcludeSemantics(
              child: AnimatedBuilder(
                animation: node,
                builder: (context, _) => _SwatchTile(
                  swatch: swatch,
                  hovered: _hovered,
                  focused: node.hasFocus,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The purely visual tile: the miniature or the accent circle, the label
/// and the check, drawn from tokens only.
class _SwatchTile extends StatelessWidget {
  const _SwatchTile({
    required this.swatch,
    required this.hovered,
    required this.focused,
  });

  final KitSwatch swatch;
  final bool hovered;
  final bool focused;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final enabled = swatch.onPressed != null;
    return swatch._isAccent
        ? _accentTile(context, tokens, roles, enabled)
        : _themeTile(context, tokens, roles, enabled);
  }

  /// KitTappable's hover treatment (KitTappable.md, tests 7): a tile on
  /// `surface1` steps up to `surface2` in dark and `surface3` in light.
  Color _fill(KitTokens tokens, BuildContext context, bool enabled) {
    if (!hovered || !enabled) return tokens.fillOf(KitSurfaceLevel.surface1);
    return tokens.fillOf(
      Theme.of(context).brightness == Brightness.dark
          ? KitSurfaceLevel.surface2
          : KitSurfaceLevel.surface3,
    );
  }

  /// The keyboard focus ring: [focusRingWidth] in `accent`, drawn outside
  /// the state border (never replacing it), so selected and focused both
  /// show at once. Painted over the child, outside its box.
  Widget _focusRing(
    BuildContext context,
    ThemeRoles roles, {
    required BoxShape shape,
    BorderRadius? radius,
    required Widget child,
  }) {
    if (!focused) return child;
    return DecoratedBox(
      key: const ValueKey('kit-swatch-focus-ring'),
      position: DecorationPosition.foreground,
      decoration: BoxDecoration(
        shape: shape,
        borderRadius: radius,
        border: Border.all(
          color: roles.accent,
          width: KitTokens.focusRingWidth(context),
          strokeAlign: BorderSide.strokeAlignOutside,
        ),
      ),
      child: child,
    );
  }

  Widget _themeTile(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    bool enabled,
  ) {
    final borderColor = enabled && swatch.selected
        ? roles.accent
        : roles.hairline;
    final reason = swatch.disabledReason;
    final radius = BorderRadius.circular(tokens.panelCornerRadius);
    return _focusRing(
      context,
      roles,
      shape: BoxShape.rectangle,
      radius: radius,
      child: DecoratedBox(
        key: const ValueKey('kit-swatch-tile'),
        // The fill and the border share one rounded shape, so the hover
        // fill never shows square corners past the border. The tile is a
        // control in the style (chosen: its selected look); the miniature
        // inside is the theme's own preview.
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: _fill(tokens, context, enabled),
            border: Border.all(
              color: borderColor,
              width: KitTokens.hairlineWidth(context),
            ),
            borderRadius: radius,
          ),
          role: KitSurfaceRole.control,
          level: hovered && enabled
              ? KitSurfaceLevel.surface2
              : KitSurfaceLevel.surface1,
          selected: enabled && swatch.selected,
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Padding(
            padding: EdgeInsets.all(tokens.space2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(tokens.iconTileRadius),
                  child: SizedBox(
                    height: KitTokens.swatchPreviewHeight,
                    child: switch (swatch.roles) {
                      final swatchRoles? when enabled => _Miniature(
                        roles: swatchRoles,
                      ),
                      // Disabled, or no palette to show at all (`roles:
                      // null`, e.g. Material You below Android 12):
                      // unavailable is said, not faded (STATE-8, LOOK-14).
                      _ => _UnavailableMiniature(roles: roles),
                    },
                  ),
                ),
                SizedBox(height: tokens.space2),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: _SwatchLabel(
                        label: swatch.label,
                        enabled: enabled,
                      ),
                    ),
                    if (swatch.selected) ...[
                      SizedBox(width: tokens.space1),
                      Icon(
                        AppIconography.check,
                        key: const ValueKey('kit-swatch-check'),
                        size: tokens.smallIconSize,
                        color: roles.accent,
                      ),
                    ],
                  ],
                ),
                if (!enabled && reason != null) ...[
                  SizedBox(height: tokens.space1),
                  KitText(
                    reason,
                    key: const ValueKey('kit-swatch-reason'),
                    role: KitTextRole.secondary,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _accentTile(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    bool enabled,
  ) {
    assert(
      !enabled ||
          swatch.colorsAnotherSurface ||
          accentKeepsMeaning(swatch.color!, roles),
      'KitSwatch.accent: color must keep LOOK-39\'s distance from the '
      'current theme\'s attention (hue ≥ 30°, ΔE2000 ≥ 20; '
      'accentKeepsMeaning in theme_roles.dart).',
    );
    final circle = DecoratedBox(
      key: const ValueKey('kit-swatch-accent-circle'),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: enabled ? swatch.color! : roles.surface3,
        border: Border.all(
          color: roles.hairline,
          width: KitTokens.hairlineWidth(context),
        ),
      ),
      child: SizedBox.square(
        dimension: tokens.markSize,
        child: swatch.selected
            ? Center(
                child: Icon(
                  AppIconography.check,
                  key: const ValueKey('kit-swatch-accent-check'),
                  size: tokens.smallIconSize,
                  color: onColor(swatch.color!),
                ),
              )
            : null,
      ),
    );
    // Selected: a 1 px text1 ring outside the circle, the circle's own
    // hairline kept. The focus ring sits outside the 48 dp target, so the
    // two never cover each other.
    return _focusRing(
      context,
      roles,
      shape: BoxShape.circle,
      child: SizedBox.square(
        dimension: tokens.minTarget,
        child: Center(
          child: swatch.selected
              ? DecoratedBox(
                  key: const ValueKey('kit-swatch-accent-ring'),
                  position: DecorationPosition.foreground,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: roles.text1,
                      width: KitTokens.hairlineWidth(context),
                      strokeAlign: BorderSide.strokeAlignOutside,
                    ),
                  ),
                  child: circle,
                )
              : circle,
        ),
      ),
    );
  }
}

/// A swatch's name, isolated with [KitBidi.auto] and cut to two lines. On a
/// fine pointer a cut name gets a tooltip with the whole of it (the pointer
/// rule in KitSwatch.md "Adaptive"); semantics carry the full name anyway.
class _SwatchLabel extends StatelessWidget {
  const _SwatchLabel({required this.label, required this.enabled});

  final String label;
  final bool enabled;

  static const _maxLines = 2;

  @override
  Widget build(BuildContext context) {
    final tone = enabled ? null : KitTextTone.tertiary;
    final text = KitText(
      KitBidi.auto(label),
      key: const ValueKey('kit-swatch-label'),
      role: KitTextRole.label,
      tone: tone,
      maxLines: _maxLines,
      overflow: TextOverflow.ellipsis,
    );
    if (!KitLayout.finePointer(context)) return text;
    return LayoutBuilder(
      builder: (context, constraints) {
        final painter = TextPainter(
          text: TextSpan(
            text: KitBidi.auto(label),
            style: KitText.styleOf(context, KitTextRole.label, tone: tone),
          ),
          maxLines: _maxLines,
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: constraints.maxWidth);
        final cut = painter.didExceedMaxLines;
        painter.dispose();
        if (!cut) return text;
        return Tooltip(
          key: const ValueKey('kit-swatch-tooltip'),
          message: label,
          excludeFromSemantics: true,
          child: text,
        );
      },
    );
  }
}

/// The theme's own miniature: its ground, a panel with a line of its text,
/// and its accent and success dots. Always the swatch's own [roles], never
/// the ambient theme.
class _Miniature extends StatelessWidget {
  const _Miniature({required this.roles});

  final ThemeRoles roles;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return DecoratedBox(
      key: const ValueKey('kit-swatch-ground'),
      decoration: BoxDecoration(color: roles.ground),
      child: Padding(
        padding: EdgeInsets.all(tokens.space2),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: DecoratedBox(
                key: const ValueKey('kit-swatch-panel'),
                decoration: BoxDecoration(
                  color: roles.surface1,
                  borderRadius: BorderRadius.circular(tokens.iconTileRadius),
                ),
                child: Padding(
                  padding: EdgeInsets.symmetric(
                    horizontal: tokens.space2,
                    vertical: tokens.space3,
                  ),
                  child: DecoratedBox(
                    key: const ValueKey('kit-swatch-text-line'),
                    decoration: BoxDecoration(
                      color: roles.text1,
                      borderRadius: BorderRadius.circular(
                        KitTokens.progressBarRadius,
                      ),
                    ),
                    child: SizedBox(
                      height: KitTokens.progressBarHeight,
                      width: double.infinity,
                    ),
                  ),
                ),
              ),
            ),
            SizedBox(width: tokens.space2),
            DecoratedBox(
              key: const ValueKey('kit-swatch-accent-dot'),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: roles.accent,
              ),
              child: const SizedBox(
                width: KitTokens.swatchDot,
                height: KitTokens.swatchDot,
              ),
            ),
            SizedBox(width: tokens.space1),
            DecoratedBox(
              key: const ValueKey('kit-swatch-success-dot'),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: roles.success,
              ),
              child: const SizedBox(
                width: KitTokens.swatchDot,
                height: KitTokens.swatchDot,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The disabled miniature: a plain `surface3` field with the sparkle glyph
/// in `text3` (the *current* theme's roles: unavailable is said, never a
/// theme's own colours faded).
class _UnavailableMiniature extends StatelessWidget {
  const _UnavailableMiniature({required this.roles});

  final ThemeRoles roles;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    key: const ValueKey('kit-swatch-unavailable'),
    decoration: BoxDecoration(color: roles.surface3),
    child: Center(
      child: Icon(
        AppIconography.sparkle,
        size: KitTokens.of(context).smallIconSize,
        color: roles.text3,
      ),
    ),
  );
}

// -----------------------------------------------------------------------
// KitSwatchGrid body: columns from the space it has, roving-tabindex
// keyboard navigation (kit-v2.md §8.2, the KitChoiceList group rule).
// -----------------------------------------------------------------------

class _SwatchGridBody extends StatefulWidget {
  const _SwatchGridBody({
    super.key,
    required this.label,
    required this.children,
  });

  final String label;
  final List<KitSwatch> children;

  @override
  State<_SwatchGridBody> createState() => _SwatchGridBodyState();
}

class _SwatchGridBodyState extends State<_SwatchGridBody> {
  late List<FocusNode> _nodes = _buildNodes();
  late int _active = _initialActive();

  List<FocusNode> _buildNodes() => List.generate(
    widget.children.length,
    (_) => FocusNode(debugLabel: 'KitSwatchGrid item'),
  );

  bool _enabled(int i) => widget.children[i].onPressed != null;

  /// The one Tab stop: the selected swatch when it can take focus, else the
  /// first that can. A disabled swatch never holds it (it cannot take
  /// focus, so Tab would never enter the grid).
  int _initialActive() {
    final selected = widget.children.indexWhere(
      (c) => c.selected && c.onPressed != null,
    );
    if (selected >= 0) return selected;
    final first = widget.children.indexWhere((c) => c.onPressed != null);
    return first >= 0 ? first : 0;
  }

  @override
  void initState() {
    super.initState();
    for (final node in _nodes) {
      node.addListener(_handleFocusChange);
    }
  }

  @override
  void didUpdateWidget(covariant _SwatchGridBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.children.length != widget.children.length) {
      for (final node in _nodes) {
        node.removeListener(_handleFocusChange);
        node.dispose();
      }
      _nodes = _buildNodes();
      for (final node in _nodes) {
        node.addListener(_handleFocusChange);
      }
    }
    if (_active >= _nodes.length || !_enabled(_active)) {
      _active = _initialActive();
    }
  }

  @override
  void dispose() {
    for (final node in _nodes) {
      node.removeListener(_handleFocusChange);
      node.dispose();
    }
    super.dispose();
  }

  void _handleFocusChange() {
    final i = _nodes.indexWhere((n) => n.hasFocus);
    if (i >= 0 && i != _active) setState(() => _active = i);
  }

  /// The next swatch from [from] in steps of [step] that can take focus,
  /// skipping disabled ones; null when there is none that way.
  int? _nextEnabled(int from, int step) {
    for (var i = from + step; i >= 0 && i < _nodes.length; i += step) {
      if (_enabled(i)) return i;
    }
    return null;
  }

  KeyEventResult _onOtherKey(int index, KeyEvent event, int columns) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final ltr = Directionality.of(context) == TextDirection.ltr;
    final key = event.logicalKey;
    final int? target;
    if (key == LogicalKeyboardKey.arrowDown) {
      target = _nextEnabled(index, columns);
    } else if (key == LogicalKeyboardKey.arrowUp) {
      target = _nextEnabled(index, -columns);
    } else if (key == LogicalKeyboardKey.arrowRight) {
      target = _nextEnabled(index, ltr ? 1 : -1);
    } else if (key == LogicalKeyboardKey.arrowLeft) {
      target = _nextEnabled(index, ltr ? -1 : 1);
    } else if (key == LogicalKeyboardKey.home) {
      target = _nextEnabled(-1, 1);
    } else if (key == LogicalKeyboardKey.end) {
      target = _nextEnabled(_nodes.length, -1);
    } else {
      return KeyEventResult.ignored;
    }
    if (target != null) _nodes[target].requestFocus();
    return KeyEventResult.handled;
  }

  /// `KitTokens.swatchMinWidth`-based columns (`_new-tokens.md`), one fewer
  /// from 1.3× text (A11Y: the label keeps at least 8 characters per line).
  static int columnsFor(double width, double textScale) {
    var columns = (width / KitTokens.swatchMinWidth).floor().clamp(2, 6);
    if (textScale >= 1.3) columns = math.max(columns - 1, 1);
    return columns;
  }

  /// Accent circles are 48 dp targets 8 dp apart (LAY-9): as many as fit
  /// the width, for the ↑ and ↓ row step.
  static int accentColumnsFor(double width, double target, double spacing) =>
      math.max(((width + spacing) / (target + spacing)).floor(), 1);

  Widget _hosted(int i, int columns) => _SwatchFocusHost(
    node: _nodes[i],
    skipTraversal: i != _active,
    onOtherKey: (event) => _onOtherKey(i, event, columns),
    child: widget.children[i],
  );

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final accents =
        widget.children.isNotEmpty && widget.children.first._isAccent;
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : KitTokens.swatchMinWidth * 3;
        final Widget grid;
        if (accents) {
          // Each accent keeps its own 48 dp target; start-aligned, 8 dp
          // apart, never stretched to a theme tile's column.
          final spacing = tokens.space2;
          final columns = accentColumnsFor(width, tokens.minTarget, spacing);
          // The full width, so the circles start at the start edge
          // whatever the host centres (a [Wrap] alone shrinks to its run).
          grid = SizedBox(
            width: width,
            child: Wrap(
              spacing: spacing,
              runSpacing: spacing,
              children: [
                for (var i = 0; i < widget.children.length; i++)
                  _hosted(i, columns),
              ],
            ),
          );
        } else {
          final textScale = MediaQuery.textScalerOf(context).scale(1);
          final columns = columnsFor(width, textScale);
          final spacing = tokens.space3;
          // Floored, so the row's total width never rounds up past what
          // [Wrap] was given (which would push the last column onto a new
          // line). [columnsFor] already keeps `width / columns` at or above
          // [KitTokens.swatchMinWidth] except at the fixed 2-column floor on
          // a very narrow window, so no extra floor is applied here.
          final tileWidth = math.max(
            ((width - spacing * (columns - 1)) / columns).floorToDouble(),
            1.0,
          );
          grid = Wrap(
            spacing: spacing,
            runSpacing: spacing,
            children: [
              for (var i = 0; i < widget.children.length; i++)
                SizedBox(width: tileWidth, child: _hosted(i, columns)),
            ],
          );
        }
        return Semantics(
          label: widget.label,
          container: true,
          explicitChildNodes: true,
          child: grid,
        );
      },
    );
  }
}

// -----------------------------------------------------------------------
// KitThemePreview
// -----------------------------------------------------------------------

/// Test seam, debug builds only: adapts the theme [KitThemePreview] builds
/// for its sample before it is applied. A gallery uses it to give the
/// sample's button styles the Arabic font fallback a device supplies from
/// its system fonts (the test engine has none). Never set in app code; a
/// test that sets it resets it to null.
@visibleForTesting
ThemeData Function(ThemeData theme)? debugKitThemePreviewTheme;

/// A picture of a candidate theme (docs/ux-system/kit-api/KitSwatch.md):
/// the app's own parts — a panel with a row (icon tile, title, "Working ·
/// 2 min" with the working mark), a code line, the needs-you word, a
/// selected segment's check, a primary and a secondary button — all drawn
/// in [roles] rather than the app's current theme, so a preview sheet shows
/// what a theme looks like before it is applied.
///
/// One `Theme` override, inside the kit (kit-v2.md §9.1, KIT-5): the real
/// kit parts pick up [roles] because they are actually rendered in them,
/// not copied — when the v2 parts land, the preview follows automatically.
///
/// One state: it draws what it is given. It is a picture, not a control —
/// one semantics node labelled [label], and nothing inside it is
/// interactive or announced as a button.
///
/// States: none — a picture drawn from the roles it is given.
class KitThemePreview extends StatelessWidget {
  const KitThemePreview({
    super.key,
    required this.roles,
    required this.label,
    this.previewKey,
  });

  final ThemeRoles roles;

  /// "Preview of Graphite": the semantic label of the whole picture.
  final String label;

  final Key? previewKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final locale = Localizations.localeOf(context);
    var theme = AppTheme.forLocale(AppTheme.fromRoles(roles), locale);
    assert(() {
      final adapt = debugKitThemePreviewTheme;
      if (adapt != null) theme = adapt(theme);
      return true;
    }());
    return Semantics(
      key: previewKey,
      label: label,
      image: true,
      container: true,
      excludeSemantics: true,
      child: IgnorePointer(
        child: Theme(
          data: theme,
          child: Builder(builder: _sample),
        ),
      ),
    );
  }

  Widget _sample(BuildContext context) {
    final tokens = KitTokens.of(context);
    final r = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              KitRow.icon(context, AppIconography.terminal),
              SizedBox(width: tokens.space3),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    KitText(
                      l10n.kitThemePreviewTitle,
                      role: KitTextRole.rowTitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    SizedBox(height: tokens.space1),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const KitStatusMark(state: KitMarkState.working),
                        Flexible(
                          child: KitText(
                            l10n.kitThemePreviewWorking,
                            role: KitTextRole.secondary,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          SizedBox(height: tokens.space3),
          DecoratedBox(
            decoration: BoxDecoration(
              color: r.surface2,
              borderRadius: BorderRadius.circular(tokens.codeRadius),
            ),
            child: Padding(
              padding: EdgeInsets.all(tokens.space2),
              // An LTR island aligned left in every language (COPY-30):
              // the line keeps left-to-right direction, not only its marks.
              child: Directionality(
                textDirection: TextDirection.ltr,
                child: KitBidi.ltrText(
                  l10n.kitThemePreviewCode,
                  key: const ValueKey('kit-theme-preview-code'),
                  style: tokens.technicalValue,
                ),
              ),
            ),
          ),
          SizedBox(height: tokens.space3),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const KitTaskMark(state: KitTaskState.needsYou),
              SizedBox(width: tokens.space1),
              Flexible(
                child: KitText(
                  l10n.kitThemePreviewNeedsYou,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.attention,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          SizedBox(height: tokens.space3),
          // A stand-in for a selected segment until KitSegmented (a planned
          // wave-1 part) merges; then this renders that part instead.
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: DecoratedBox(
              decoration: ShapeDecoration(
                color: r.surface3,
                shape: tokens.shapeOf(KitShape.pill),
              ),
              child: Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.space3,
                  vertical: tokens.space1,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      AppIconography.check,
                      size: tokens.smallIconSize,
                      color: r.accent,
                    ),
                    SizedBox(width: tokens.space1),
                    KitText(
                      l10n.kitThemePreviewSegment,
                      role: KitTextRole.label,
                    ),
                  ],
                ),
              ),
            ),
          ),
          SizedBox(height: tokens.space3),
          Row(
            children: [
              Expanded(
                child: KitButton.primary(
                  label: l10n.kitThemePreviewPrimary,
                  onPressed: () {},
                ),
              ),
              SizedBox(width: tokens.space2),
              Expanded(
                child: KitButton.secondary(
                  label: l10n.kitThemePreviewSecondary,
                  onPressed: () {},
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
