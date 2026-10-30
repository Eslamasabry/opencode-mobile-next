// Native press feedback (docs/ux-system/kit-v2.md §7.1, owner rule
// 2026-10-26: "Yes, max Android native animation and support."). On Android
// a touch on any kit control answers with the platform's Material ripple —
// `InkSparkle`, Flutter's Material 3 default there — in the theme's ink,
// bounded to the part's shape. Other platforms, and Android under reduced
// motion, keep the flat pressed fill from the surface steps.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'kit_motion.dart';
import 'kit_tokens.dart';

/// Whether, and how, a kit control draws the native ripple.
abstract final class KitInk {
  /// The ripple's opacity over the part's own ink (Material 3's pressed
  /// state layer is 10 %; 12 % keeps it visible on the matte Solid grounds).
  static const double alpha = .12;

  /// True where a press draws the ripple instead of the flat pressed fill:
  /// an Android build (not the browser), with motion on. Under the system's
  /// "remove animations" or Animations: Off the flat fill answers instead,
  /// which does not move.
  static bool on(BuildContext context) =>
      !kIsWeb &&
      Theme.of(context).platform == TargetPlatform.android &&
      !KitMotion.reduced(context);

  /// The ripple colour: [ink] (the part's own foreground; `text1` when
  /// null) at [alpha].
  static Color color(BuildContext context, [Color? ink]) =>
      (ink ?? KitTokens.of(context).roles.text1).withValues(alpha: alpha);

  /// The splash an `InkWell` in the kit uses: the theme's (InkSparkle on
  /// Android) where [on], none elsewhere. InkSparkle's sparkle noise is
  /// seeded once, not per press, so a frame mid-ripple is the same in
  /// every run (golden screenshots stay exact); a person sees the same
  /// sparkle either way.
  static InteractiveInkFeatureFactory splashFactory(BuildContext context) {
    if (!on(context)) return NoSplash.splashFactory;
    final theme = Theme.of(context).splashFactory;
    return identical(theme, InkSparkle.splashFactory)
        ? InkSparkle.constantTurbulenceSeedSplashFactory
        : theme;
  }

  /// An `InkWell.overlayColor` that keeps the kit's flat hover and focus
  /// colours and gives a press the ripple colour where [on] (transparent
  /// elsewhere: the flat pressed fill answers).
  static WidgetStateProperty<Color?> overlay(
    BuildContext context, {
    Color? hover,
    Color? focus,
    Color? ink,
  }) {
    final pressed = on(context) ? color(context, ink) : Colors.transparent;
    return WidgetStateProperty.resolveWith((states) {
      if (states.contains(WidgetState.pressed)) return pressed;
      if (states.contains(WidgetState.focused)) {
        return focus ?? Colors.transparent;
      }
      if (states.contains(WidgetState.hovered)) {
        return hover ?? Colors.transparent;
      }
      return Colors.transparent;
    });
  }
}

/// A layer over [child] that draws the native ripple of a press on it, in
/// front of whatever [child] paints (a filled button face, a card surface),
/// clipped to [shape]. It takes no pointer events and has no semantics: the
/// press is reported by the part's `KitPressTracker`, whose `inkKey` is this
/// layer's key ([KitInkLayerState.start], [KitInkLayerState.confirm],
/// [KitInkLayerState.cancel]).
///
/// The tree is the same on every platform (a [Stack] around [child]); only
/// the ripple surface comes and goes with [KitInk.on], so turning reduced
/// motion on or off never remounts [child].
///
/// States: none of its own — at rest it draws nothing; pressed (a ripple
/// spreading from the finger), released (it finishes and fades) and
/// cancelled (it fades at once) come from the host's press.
class KitInkLayer extends StatefulWidget {
  const KitInkLayer({
    super.key,
    required this.shape,
    required this.child,
    this.color,
  });

  /// The part's outline; the ripple never spreads past it.
  final ShapeBorder shape;

  /// The part's own foreground (the ripple is it at [KitInk.alpha]); null
  /// is `text1`.
  final Color? color;

  final Widget child;

  @override
  State<KitInkLayer> createState() => KitInkLayerState();
}

class KitInkLayerState extends State<KitInkLayer> {
  final _surface = GlobalKey(debugLabel: 'KitInkLayer surface');
  final _splashes = <InteractiveInkFeature>{};
  InteractiveInkFeature? _current;

  /// Starts a ripple from [globalPosition] (where the finger went down).
  /// Nothing where [KitInk.on] is false.
  void start(Offset globalPosition) {
    final surface = _surface.currentContext;
    if (!mounted || surface == null) return;
    final box = surface.findRenderObject();
    final controller = Material.maybeOf(surface);
    if (box is! RenderBox || !box.hasSize || controller == null) return;
    _current?.confirm();
    late final InteractiveInkFeature splash;
    splash = KitInk.splashFactory(context).create(
      controller: controller,
      referenceBox: box,
      position: box.globalToLocal(globalPosition),
      color: KitInk.color(context, widget.color),
      textDirection: Directionality.of(context),
      containedInkWell: true,
      rectCallback: () => Offset.zero & box.size,
      customBorder: widget.shape,
      onRemoved: () {
        _splashes.remove(splash);
        if (identical(_current, splash)) _current = null;
      },
    );
    _splashes.add(splash);
    _current = splash;
  }

  /// The press became a tap (or a long-press): the ripple finishes
  /// spreading and fades.
  void confirm() {
    _current?.confirm();
    _current = null;
  }

  /// The press became a scroll or was lost: the ripple fades at once.
  void cancel() {
    _current?.cancel();
    _current = null;
  }

  void _disposeSplashes() {
    for (final splash in _splashes.toList()) {
      splash.dispose();
    }
    _splashes.clear();
    _current = null;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Reduced motion turned on mid-press: the ripple surface is about to
    // go, so its ripples go first.
    if (!KitInk.on(context)) _disposeSplashes();
  }

  @override
  void deactivate() {
    // Before the ripple surface (a descendant) goes, like InkResponse.
    _disposeSplashes();
    super.deactivate();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.passthrough,
      children: [
        widget.child,
        if (KitInk.on(context))
          Positioned.fill(
            child: IgnorePointer(
              child: ExcludeSemantics(
                child: Material(
                  type: MaterialType.transparency,
                  child: SizedBox.expand(key: _surface),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
