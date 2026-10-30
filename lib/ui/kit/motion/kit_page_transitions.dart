import 'dart:math' as math;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../../theme_roles.dart';
import '../kit_motion.dart';
import '../kit_tokens.dart';

/// Android's own page transition (owner rule 2026-10-26, kit-v2 §7.1: "max
/// Android native animation"): Flutter's [PredictiveBackPageTransitionsBuilder].
/// The system back gesture previews the page underneath as the finger moves
/// (Android 14+, and 13 with the developer option) and can be dragged back
/// to cancel; a Back button, a pushed page and a programmatic pop play the
/// Android 16 fade-forwards slide ([FadeForwardsPageTransitionsBuilder]),
/// over the page background ([ThemeRoles.ground]).
///
/// Under reduced motion it is [KitPageTransitionsBuilder] instead, which
/// shows a new page at once and only fades a closing one. The page keeps
/// its state across the switch: a route's page sits under the route's own
/// global key, so a different transition wrapper reparents it.
class KitAndroidPageTransitionsBuilder extends PageTransitionsBuilder {
  const KitAndroidPageTransitionsBuilder();

  /// Whether pages here move the Android way: an Android build, not the
  /// browser.
  static bool appliesTo(BuildContext context) =>
      !kIsWeb && Theme.of(context).platform == TargetPlatform.android;

  static const _still = KitPageTransitionsBuilder();

  @override
  Duration get transitionDuration => const Duration(
    milliseconds: FadeForwardsPageTransitionsBuilder.kTransitionMilliseconds,
  );

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (KitMotion.reduced(context)) {
      return _still.buildTransitions<T>(
        route,
        context,
        animation,
        secondaryAnimation,
        child,
      );
    }
    final predictive = PredictiveBackPageTransitionsBuilder(
      fallbackColor: ThemeRoles.of(context).ground,
    );
    // Under a page that grows out of one of this page's rows, this page
    // stays still: the row is where the page above comes from and goes
    // back to.
    final secondary = KitPageOrigin.covers(route)
        ? kAlwaysDismissedAnimation
        : secondaryAnimation;
    final origin = KitPageOrigin._originOf(route);
    // A page opened from a row grows out of it, and a Back button or a
    // programmatic pop shrinks it back in. The system back gesture keeps
    // Android's own predictive preview (the page follows the finger and
    // can be dragged back): Flutter's builder takes over for as long as
    // the gesture runs, as it does for its own fallback.
    if (origin != null && !route.popGestureInProgress) {
      return predictive.buildTransitions<T>(
        route,
        context,
        kAlwaysCompleteAnimation,
        secondary,
        _GrowFromRow(animation: animation, origin: origin, child: child),
      );
    }
    return predictive.buildTransitions<T>(
      route,
      context,
      animation,
      secondary,
      child,
    );
  }
}

/// Where a page opens from (owner request, 2026-10-27): the conversation
/// row the person tapped. The next page pushed above that row's page grows
/// out of the row's box into the full screen, its contents fading in as it
/// grows (Material's container transform), and closing it with Back
/// shrinks it into the row again. The page under it stays still. The
/// system back gesture keeps Android's predictive back preview. Under
/// reduced motion pages open as they always do.
///
/// Call [mark] with the row's context just before the push; a mark no page
/// claims within [claimWithin] (the push failed) is dropped, and [forget]
/// drops it at once.
abstract final class KitPageOrigin {
  /// How long a mark waits for its page (reopening an old agent's
  /// conversation can take a few seconds before the push).
  static const claimWithin = Duration(seconds: 10);

  static _KitOrigin? _pending;
  static final List<_KitOrigin> _active = [];
  static final Expando<_KitOrigin> _claimed = Expando('kit-page-origin');

  static final ValueNotifier<int> _changes = ValueNotifier(0);

  /// Notifies when a row is marked, a mark is dropped, or a page that grew
  /// out of a row has closed: when [covers] may have changed. Never during
  /// a build.
  static Listenable get changes => _changes;

  /// The next page pushed above [context]'s page grows out of [context]'s
  /// box (a row of a list). Does nothing when [context] has no laid-out box
  /// or no page.
  static void mark(BuildContext context) {
    final box = context.findRenderObject();
    final source = ModalRoute.of(context);
    if (box is! RenderBox || !box.hasSize || source == null) {
      _pending = null;
      return;
    }
    _pending = _KitOrigin(
      source: source,
      context: context,
      rect: box.localToGlobal(Offset.zero) & box.size,
      marked: DateTime.now(),
    );
    _changes.value++;
  }

  /// Drops a mark no page has claimed (the page did not open).
  static void forget() {
    if (_pending == null) return;
    _pending = null;
    _changes.value++;
  }

  /// Whether [route] grew out of a marked row (it closes back into it).
  static bool grewFromRow(Route<Object?> route) =>
      route is PageRoute<Object?> && _claimed[route] != null;

  /// [route]'s origin: claimed by the first page pushed above the marked
  /// row's page.
  static _KitOrigin? _originOf(PageRoute<Object?> route) {
    final claimed = _claimed[route];
    if (claimed != null) return claimed;
    final pending = _pending;
    if (pending == null) return null;
    if (DateTime.now().difference(pending.marked) > claimWithin ||
        !pending.source.isActive) {
      _pending = null;
      return null;
    }
    if (identical(pending.source, route) ||
        pending.source.isCurrent ||
        route.animation?.status != AnimationStatus.forward) {
      return null;
    }
    _pending = null;
    _claimed[route] = pending;
    pending.page = route;
    _active.add(pending);
    final animation = route.animation!;
    void ended(AnimationStatus status) {
      if (status != AnimationStatus.dismissed) return;
      animation.removeStatusListener(ended);
      _active.remove(pending);
      _changes.value++;
    }

    animation.addStatusListener(ended);
    return pending;
  }

  /// A page above [route] grows out of one of its rows (or is about to,
  /// once marked), and has not closed yet: it is opening, open or
  /// shrinking back. A list holds its rows still while this is true, so the
  /// page lands in the row it came from; [changes] tells it when to look
  /// again.
  static bool covers(ModalRoute<Object?> route) {
    // A page replaced or removed without closing never reports its end. A
    // page shrinking back is no longer active either (it is popping): it
    // stays until it has landed, when its end drops it.
    _active.removeWhere((origin) {
      final page = origin.page;
      if (page == null) return true;
      return !page.isActive && !(page.animation?.isAnimating ?? false);
    });
    return identical(_pending?.source, route) ||
        _active.any((origin) => identical(origin.source, route));
  }
}

/// A marked row: its page, and where it is.
class _KitOrigin {
  _KitOrigin({
    required this.source,
    required this.context,
    required this.rect,
    required this.marked,
  });

  final ModalRoute<Object?> source;
  final BuildContext context;
  final DateTime marked;

  /// The page that grew out of the row, once it claimed the mark.
  PageRoute<Object?>? page;

  /// Where the row was when marked, global.
  Rect rect;

  /// Where the row is now, global: measured again while it is on screen
  /// (the list may have moved under the page), else where it was.
  Rect get where {
    if (context.mounted) {
      final box = context.findRenderObject();
      if (box is RenderBox && box.attached && box.hasSize) {
        rect = box.localToGlobal(Offset.zero) & box.size;
      }
    }
    return rect;
  }
}

/// A page growing out of its [origin] row while [animation] runs
/// (Material's container transform): the box grows from the row's box to
/// the whole page on [KitMotion.emphasized] in the first 80 % of the
/// transition, its corners from the row's to square. The page underneath
/// dims to 32 % black over the first 20 %, the box's ground fills in over
/// the row in the same time, and the page rides the box's top-left corner
/// (clipped to the box), its contents fading in between 20 % and 60 %.
///
/// Back shrinks it into the row again over [KitMotion.rowReturn] (300 ms)
/// on the mirrored path ([KitMotion.rowReturnPath]), so it starts moving at
/// once: its contents leave in the first 40 %, the row shows again in the
/// last 20 %, the dim fades throughout. A Back during the opening turns
/// back from where the page is (each part keeps its opening curve until it
/// has stopped).
class _GrowFromRow extends SingleChildRenderObjectWidget {
  const _GrowFromRow({
    required this.animation,
    required this.origin,
    required super.child,
  });

  final Animation<double> animation;
  final _KitOrigin origin;

  @override
  _RenderGrowFromRow createRenderObject(BuildContext context) =>
      _RenderGrowFromRow(
        animation: animation,
        origin: origin,
        ground: ThemeRoles.of(context).ground,
        scrim: ThemeRoles.of(context).scrim,
        corner: KitTokens.of(context).bubbleCorner,
      );

  @override
  void updateRenderObject(BuildContext context, _RenderGrowFromRow box) {
    box
      ..animation = animation
      ..origin = origin
      ..ground = ThemeRoles.of(context).ground
      ..scrim = ThemeRoles.of(context).scrim
      ..corner = KitTokens.of(context).bubbleCorner;
  }
}

class _RenderGrowFromRow extends RenderProxyBox {
  _RenderGrowFromRow({
    required Animation<double> animation,
    required this.origin,
    required this.ground,
    required this.scrim,
    required this.corner,
  }) : _animation = animation {
    _curve();
  }

  static const _bounds = Interval(0, .8, curve: KitMotion.emphasized);
  static const _contents = Interval(.2, .6, curve: KitMotion.enter);

  /// The row under the box fades as the box's ground fills in (Material's
  /// fade through: the closed content leaves in the first 20 %).
  static const _handOff = Interval(0, .2, curve: KitMotion.enter);

  /// The page underneath dims while the box grows, so the box reads
  /// against it (Material's container transform scrim).
  static const _dim = Interval(0, .2, curve: KitMotion.enter);

  /// The dim's strength at most: Android's container transform, 32 % black.
  static const dimMost = .32;

  // Going back: the contents leave in the first 40 % of the close, the row
  // shows through again in its last 20 %.
  static const _contentsBack = Interval(.6, 1, curve: KitMotion.exit);
  static const _handBack = Interval(0, .2, curve: KitMotion.exit);

  /// Whether the page is also scaled to the box's width while it grows
  /// (Material's fit-width `OpenContainer`, about 0.92 to 1 for a row with
  /// 16 dp gutters). Off: pages never scale (MOT-2), the page only rides
  /// the box. Kept so the two can be compared side by side.
  static const _scalesWithBox = false;

  Animation<double> _animation;
  set animation(Animation<double> value) {
    if (identical(value, _animation)) return;
    if (attached) _animation.removeListener(_changed);
    _animation = value;
    _uncurve();
    _curve();
    if (attached) _animation.addListener(_changed);
    _changed();
  }

  // Each part on its own curve for each way (a curved animation keeps the
  // way it started until it stops, so a Back mid-way never jumps).
  late CurvedAnimation _grow;
  late CurvedAnimation _show;
  late CurvedAnimation _fill;
  late CurvedAnimation _shade;

  void _curve() {
    _grow = CurvedAnimation(
      parent: _animation,
      curve: _bounds,
      reverseCurve: KitMotion.rowReturnPath,
    );
    _show = CurvedAnimation(
      parent: _animation,
      curve: _contents,
      reverseCurve: _contentsBack,
    );
    _fill = CurvedAnimation(
      parent: _animation,
      curve: _handOff,
      reverseCurve: _handBack,
    );
    _shade = CurvedAnimation(
      parent: _animation,
      curve: _dim,
      reverseCurve: KitMotion.steady,
    );
  }

  void _uncurve() {
    _grow.dispose();
    _show.dispose();
    _fill.dispose();
    _shade.dispose();
  }

  _KitOrigin origin;
  Color ground;
  Color scrim;
  double corner;

  final LayerHandle<ClipRRectLayer> _clip = LayerHandle<ClipRRectLayer>();
  final LayerHandle<TransformLayer> _transform = LayerHandle<TransformLayer>();
  final LayerHandle<OpacityLayer> _fade = LayerHandle<OpacityLayer>();

  bool get _moving => _animation.value < 1;

  @override
  bool get alwaysNeedsCompositing => child != null && _moving;

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    _wasMoving = _moving;
    _animation.addListener(_changed);
  }

  @override
  void detach() {
    _animation.removeListener(_changed);
    super.detach();
  }

  bool _wasMoving = false;

  void _changed() {
    if (_moving != _wasMoving) markNeedsCompositingBitsUpdate();
    _wasMoving = _moving;
    markNeedsPaint();
  }

  @override
  void dispose() {
    _uncurve();
    _clip.layer = null;
    _transform.layer = null;
    _fade.layer = null;
    super.dispose();
  }

  /// The box the page grows in now, and where the page's top-left corner
  /// sits inside it (and how much it is scaled, with [_scalesWithBox]);
  /// null once the page has grown.
  ({RRect box, Offset shift, double scale})? _now() {
    final t = _animation.value;
    if (t >= 1 || !hasSize) return null;
    final global = origin.where;
    final from = globalToLocal(global.topLeft) & global.size;
    final grown = _grow.value;
    final rect = Rect.lerp(from, Offset.zero & size, grown)!;
    return (
      box: RRect.fromRectAndRadius(rect, Radius.circular(corner * (1 - grown))),
      shift: rect.topLeft,
      scale: _scalesWithBox && size.width > 0 ? rect.width / size.width : 1.0,
    );
  }

  /// How the page is drawn into the box: moved to its top-left corner (and
  /// scaled with it, with [_scalesWithBox]).
  static Matrix4 _carry(({RRect box, Offset shift, double scale}) now) {
    final matrix = Matrix4.translationValues(now.shift.dx, now.shift.dy, 0);
    if (now.scale != 1) {
      matrix.multiply(Matrix4.diagonal3Values(now.scale, now.scale, 1));
    }
    return matrix;
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final child = this.child;
    if (child == null) return;
    final now = _now();
    if (now == null) {
      _clip.layer = null;
      _transform.layer = null;
      _fade.layer = null;
      context.paintChild(child, offset);
      return;
    }
    final page = Offset.zero & size;
    final dim = math.min(scrim.a, dimMost) * _shade.value;
    if (dim > 0) {
      context.canvas.drawRect(
        page.shift(offset),
        Paint()..color = scrim.withValues(alpha: dim),
      );
    }
    final fill = _fill.value;
    final alpha = (255 * _show.value).round();
    // Nothing of the page shows yet: it is not drawn at all (an opacity
    // layer at 0 would still draw the whole page offscreen every frame).
    void contents(PaintingContext context, Offset at) {
      if (alpha <= 0) {
        _fade.layer = null;
      } else if (alpha >= 255) {
        _fade.layer = null;
        context.paintChild(child, at);
      } else {
        _fade.layer = context.pushOpacity(
          at,
          alpha,
          (context, offset) => context.paintChild(child, offset),
          oldLayer: _fade.layer,
        );
      }
    }

    _clip.layer = context.pushClipRRect(
      needsCompositing,
      offset,
      page,
      now.box,
      (context, offset) {
        if (fill > 0) {
          context.canvas.drawRect(
            offset & size,
            Paint()..color = ground.withValues(alpha: ground.a * fill),
          );
        }
        if (now.scale == 1) {
          _transform.layer = null;
          contents(context, offset + now.shift);
          return;
        }
        _transform.layer = context.pushTransform(
          needsCompositing,
          offset,
          _carry(now),
          contents,
          oldLayer: _transform.layer,
        );
      },
      oldLayer: _clip.layer,
    );
  }

  @override
  void applyPaintTransform(RenderBox child, Matrix4 transform) {
    final now = _now();
    if (now != null) transform.multiply(_carry(now));
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    final now = _now();
    if (now == null) {
      return super.hitTestChildren(result, position: position);
    }
    if (!now.box.contains(position)) return false;
    return result.addWithPaintTransform(
      transform: _carry(now),
      position: position,
      hitTest: (result, position) =>
          super.hitTestChildren(result, position: position),
    );
  }
}

/// The app's page transition off Android (design standard §10; Android
/// uses [KitAndroidPageTransitionsBuilder] since 2026-10-26): Material 3's
/// shared axis along x, timed by [KitMotion].
///
/// Opening a page: the page underneath fades out in the first 30 % of
/// [KitMotion.standard] while it drifts [travel] dp back, then the new page
/// fades in while it slides the same distance into place, on
/// [KitMotion.emphasized]. Going back plays the same in reverse. The travel
/// follows the reading direction.
///
/// Short travel and a fade-through instead of a full-width slide: the two
/// pages never overlap half-visible, and only opacity and a translation
/// animate, over each route's own repaint boundary, so nothing is laid out
/// or repainted during the transition. With the system's "remove
/// animations" a new page shows at once and a closing page only fades; the
/// widget structure stays the same either way, so switching the setting
/// while a page is open never rebuilds it (a draft survives).
class KitPageTransitionsBuilder extends PageTransitionsBuilder {
  const KitPageTransitionsBuilder();

  /// How far a page travels, in dp.
  static const travel = 30.0;

  /// The share of the transition the leaving page takes to fade out.
  static const fadeOutShare = .3;

  @override
  Duration get transitionDuration => KitMotion.standard;

  @override
  DelegatedTransitionBuilder? get delegatedTransition => _covered;

  static Widget? _covered(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    bool allowSnapshotting,
    Widget? child,
  ) {
    // Under a page that grows out of one of its rows the page stays still.
    final route = ModalRoute.of(context);
    if (route != null && KitPageOrigin.covers(route)) return child;
    return _KitCoveredPage(
      secondaryAnimation: secondaryAnimation,
      child: child,
    );
  }

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    // A page opened from a row grows out of it ([KitPageOrigin]).
    final origin = KitMotion.reduced(context)
        ? null
        : KitPageOrigin._originOf(route);
    final secondary = KitPageOrigin.covers(route)
        ? kAlwaysDismissedAnimation
        : secondaryAnimation;
    return _KitSharedAxisPage(
      animation: origin == null ? animation : kAlwaysCompleteAnimation,
      secondaryAnimation: secondary,
      child: origin == null
          ? child
          : _GrowFromRow(animation: animation, origin: origin, child: child),
    );
  }
}

double _share(double t, double begin, double end, Curve curve) {
  if (t <= begin) return 0;
  if (t >= end) return 1;
  return curve.transform((t - begin) / (end - begin));
}

double _direction(BuildContext context) =>
    Directionality.of(context) == TextDirection.rtl ? -1 : 1;

/// Moves [child] by [dx] × [KitPageTransitionsBuilder.travel] and fades it,
/// both read from [animation] each frame without rebuilding [child].
class _Axis extends StatelessWidget {
  const _Axis({
    required this.animation,
    required this.opacity,
    required this.offset,
    required this.child,
  });

  final Animation<double> animation;
  final double Function(double t) opacity;
  final double Function(double t) offset;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final direction = _direction(context);
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        final t = animation.value;
        return Opacity(
          opacity: opacity(t).clamp(0.0, 1.0),
          child: Transform.translate(
            offset: Offset(
              direction * KitPageTransitionsBuilder.travel * offset(t),
              0,
            ),
            child: child,
          ),
        );
      },
      child: child,
    );
  }
}

class _KitSharedAxisPage extends StatelessWidget {
  const _KitSharedAxisPage({
    required this.animation,
    required this.secondaryAnimation,
    required this.child,
  });

  final Animation<double> animation;
  final Animation<double> secondaryAnimation;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final still = KitMotion.reduced(context);
    const share = KitPageTransitionsBuilder.fadeOutShare;
    return DualTransitionBuilder(
      animation: animation,
      // Arriving: waits for the page underneath to clear, then fades in
      // while it slides in from the end side.
      forwardBuilder: (context, animation, child) => _Axis(
        animation: animation,
        opacity: (t) => still ? 1 : _share(t, share, 1, KitMotion.enter),
        offset: (t) => still ? 0 : 1 - KitMotion.emphasized.transform(t),
        child: child,
      ),
      // Leaving (back): fades out first while it slides towards the end.
      reverseBuilder: (context, animation, child) => IgnorePointer(
        // Only while it is on its way out.
        ignoring: animation.status == AnimationStatus.forward,
        child: _Axis(
          animation: animation,
          opacity: (t) =>
              still ? 1 - t : 1 - _share(t, 0, share, KitMotion.exit),
          offset: (t) => still ? 0 : KitMotion.emphasized.transform(t),
          child: child,
        ),
      ),
      child: _KitCoveredPage(
        secondaryAnimation: secondaryAnimation,
        child: child,
      ),
    );
  }
}

/// The page underneath a page being opened (or closed): it fades out while
/// drifting back towards the start side, and returns the same way. While it
/// moves, the screen behind it is the page background, so the fade-through
/// never shows the window behind the app.
class _KitCoveredPage extends StatefulWidget {
  const _KitCoveredPage({required this.secondaryAnimation, this.child});

  final Animation<double> secondaryAnimation;
  final Widget? child;

  @override
  State<_KitCoveredPage> createState() => _KitCoveredPageState();
}

class _KitCoveredPageState extends State<_KitCoveredPage> {
  late Animation<double> _reversed = ReverseAnimation(
    widget.secondaryAnimation,
  );

  @override
  void initState() {
    super.initState();
    widget.secondaryAnimation.addStatusListener(_onStatus);
  }

  @override
  void didUpdateWidget(_KitCoveredPage old) {
    super.didUpdateWidget(old);
    if (old.secondaryAnimation != widget.secondaryAnimation) {
      old.secondaryAnimation.removeStatusListener(_onStatus);
      widget.secondaryAnimation.addStatusListener(_onStatus);
      _reversed = ReverseAnimation(widget.secondaryAnimation);
    }
  }

  @override
  void dispose() {
    widget.secondaryAnimation.removeStatusListener(_onStatus);
    super.dispose();
  }

  void _onStatus(AnimationStatus _) {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    // With reduced motion the page underneath simply stays: the page
    // above is opaque and appears (or fades) over it.
    final still = KitMotion.reduced(context);
    const share = KitPageTransitionsBuilder.fadeOutShare;
    final moving = DualTransitionBuilder(
      animation: _reversed,
      // Uncovered again (the page above closes): fades back in.
      forwardBuilder: (context, animation, child) => _Axis(
        animation: animation,
        opacity: (t) => still ? 1 : _share(t, share, 1, KitMotion.enter),
        offset: (t) => still ? 0 : -(1 - KitMotion.emphasized.transform(t)),
        child: child,
      ),
      // Covered (a page opens above): fades out first, drifting back.
      reverseBuilder: (context, animation, child) => _Axis(
        animation: animation,
        opacity: (t) => still ? 1 : 1 - _share(t, 0, share, KitMotion.exit),
        offset: (t) => still ? 0 : -KitMotion.emphasized.transform(t),
        child: child,
      ),
      child: widget.child,
    );
    final opaque = ModalRoute.opaqueOf(context) ?? true;
    if (!opaque) return moving;
    return ColoredBox(
      color: widget.secondaryAnimation.isAnimating
          ? ThemeRoles.of(context).ground
          : Colors.transparent,
      child: moving,
    );
  }
}
