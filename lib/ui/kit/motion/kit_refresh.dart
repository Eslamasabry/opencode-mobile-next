import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show CustomSemanticsAction;
import 'package:flutter/services.dart';

import '../kit_illustration.dart';
import '../kit_motion.dart';
import '../scenes/portal_scene.dart';

/// Pull to refresh with the brand's mark (design standard §10) instead of
/// the stock spinner: as the list is pulled, a small disc slides down and
/// the portal's two brackets draw themselves in with the pull; when the
/// pull is far enough the spark lands between them. While the refresh runs
/// the portal breathes and the spark circles (a wait, so it may loop); when
/// it finishes the disc shrinks away.
///
/// A drop-in for [RefreshIndicator]: the same [onRefresh] contract and the
/// same gesture (Flutter's own [RefreshIndicator.noSpinner] runs it), only
/// the drawing is ours. The disc is painted in one layer over the list, so
/// pulling repaints only that layer; the list itself is untouched.
///
/// Reduced motion: the disc still follows the pull (it is the person's own
/// finger) but nothing loops while the refresh runs, and it leaves at once.
///
/// Pulling needs a finger. A screen reader offers the same refresh as a
/// "Refresh" action on the list, and a keyboard as Ctrl+R (Cmd+R) or F5
/// while focus is inside it; both run [onRefresh] with the same drawing.
class KitRefresh extends StatefulWidget {
  const KitRefresh({
    super.key,
    required this.onRefresh,
    required this.child,
    this.displacement = 40,
    this.edgeOffset = 0,
    this.notificationPredicate = defaultScrollNotificationPredicate,
  });

  final RefreshCallback onRefresh;
  final Widget child;

  /// Where the disc rests while refreshing, from the top edge (+ [edgeOffset]).
  final double displacement;

  /// Start the disc below something pinned over the list's top.
  final double edgeOffset;
  final ScrollNotificationPredicate notificationPredicate;

  /// The disc's diameter.
  static const discSize = 40.0;

  @override
  State<KitRefresh> createState() => _KitRefreshState();
}

enum _Phase { idle, pulling, refreshing, leaving }

class _KitRefreshState extends State<KitRefresh> with TickerProviderStateMixin {
  /// 0..1: how far the pull is towards arming (1 = armed and beyond).
  final _pull = ValueNotifier<double>(0);
  late final AnimationController _leave = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
  );
  late final AnimationController _loop = AnimationController(
    vsync: this,
    duration: KitMotion.breath,
  );
  _Phase _phase = _Phase.idle;
  double _dragOffset = 0;
  final _indicator = GlobalKey<RefreshIndicatorState>();

  /// The refresh a pull would start, for a keyboard or a screen reader.
  void _refreshNow() {
    if (_phase == _Phase.refreshing) return;
    _indicator.currentState?.show();
  }

  // Flutter's RefreshIndicator arms at two thirds of a quarter of the
  // viewport; the drawing completes there too.
  static const _armedShare = 1 / 1.5;

  @override
  void dispose() {
    _pull.dispose();
    _leave.dispose();
    _loop.dispose();
    super.dispose();
  }

  void _setPhase(_Phase phase) {
    if (_phase == phase) return;
    setState(() => _phase = phase);
  }

  void _onStatus(RefreshIndicatorStatus? status) {
    switch (status) {
      case RefreshIndicatorStatus.drag:
        _leave.value = 0;
        _dragOffset = 0;
        _pull.value = 0;
        _setPhase(_Phase.pulling);
      case RefreshIndicatorStatus.armed:
        break;
      case RefreshIndicatorStatus.snap:
      case RefreshIndicatorStatus.refresh:
        _pull.value = _armedShare;
        _setPhase(_Phase.refreshing);
        if (KitMotion.loopsIn(context)) _loop.repeat();
      case RefreshIndicatorStatus.done:
      case RefreshIndicatorStatus.canceled:
      case null:
        _loop
          ..stop()
          ..value = 0;
        if (_phase == _Phase.idle || _phase == _Phase.leaving) return;
        _setPhase(_Phase.leaving);
        if (KitMotion.reduced(context)) {
          _finish();
        } else {
          _leave.forward(from: 0).whenCompleteOrCancel(_finish);
        }
    }
  }

  void _finish() {
    if (!mounted || _phase != _Phase.leaving) return;
    _pull.value = 0;
    _leave.value = 0;
    _setPhase(_Phase.idle);
  }

  bool _onScroll(ScrollNotification notification) {
    if (_phase != _Phase.pulling ||
        !widget.notificationPredicate(notification)) {
      return false;
    }
    final down = notification.metrics.axisDirection == AxisDirection.down;
    final up = notification.metrics.axisDirection == AxisDirection.up;
    if (!down && !up) return false;
    final sign = down ? -1.0 : 1.0;
    if (notification is ScrollUpdateNotification) {
      _dragOffset += sign * (notification.scrollDelta ?? 0);
    } else if (notification is OverscrollNotification) {
      _dragOffset += sign * notification.overscroll;
    } else {
      return false;
    }
    final extent = notification.metrics.viewportDimension * .25;
    if (extent > 0) _pull.value = (_dragOffset / extent).clamp(0.0, 1.0);
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final refreshing = _phase == _Phase.refreshing;
    final indicator = IgnorePointer(
      child: RepaintBoundary(
        child: CustomPaint(
          key: const ValueKey('kit-refresh-indicator'),
          size: Size.infinite,
          painter: _RefreshPainter(
            pull: _pull,
            leave: _leave,
            loop: _loop,
            phase: _phase,
            palette: KitPalette.of(Theme.of(context)),
            top: widget.edgeOffset,
            rest: widget.displacement,
          ),
        ),
      ),
    );
    final label = MaterialLocalizations.of(
      context,
    ).refreshIndicatorSemanticLabel;
    final list = NotificationListener<ScrollNotification>(
      onNotification: _onScroll,
      child: Stack(
        children: [
          RefreshIndicator.noSpinner(
            key: _indicator,
            onRefresh: widget.onRefresh,
            onStatusChange: _onStatus,
            notificationPredicate: widget.notificationPredicate,
            child: widget.child,
          ),
          if (_phase != _Phase.idle)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height:
                  widget.edgeOffset +
                  widget.displacement +
                  KitRefresh.discSize * 2,
              child: refreshing
                  ? Semantics(liveRegion: true, label: label, child: indicator)
                  : indicator,
            ),
        ],
      ),
    );
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.keyR, control: true):
            _refreshNow,
        const SingleActivator(LogicalKeyboardKey.keyR, meta: true): _refreshNow,
        const SingleActivator(LogicalKeyboardKey.f5): _refreshNow,
      },
      child: Semantics(
        container: true,
        explicitChildNodes: true,
        customSemanticsActions: {
          CustomSemanticsAction(label: label): _refreshNow,
        },
        child: list,
      ),
    );
  }
}

class _RefreshPainter extends CustomPainter {
  _RefreshPainter({
    required this.pull,
    required this.leave,
    required this.loop,
    required this.phase,
    required this.palette,
    required this.top,
    required this.rest,
  }) : super(repaint: Listenable.merge([pull, leave, loop]));

  final ValueNotifier<double> pull;
  final AnimationController leave;
  final AnimationController loop;
  final _Phase phase;
  final KitPalette palette;
  final double top;
  final double rest;

  static const _scene = KitPortalScene();

  @override
  void paint(Canvas canvas, Size size) {
    const disc = KitRefresh.discSize;
    const armed = _KitRefreshState._armedShare;
    final p = pull.value;
    // The disc comes down with the pull, rests at [rest] once armed, and
    // gives a little more if the person keeps pulling.
    final reach = math.min(1.0, p / armed);
    final beyond = math.max(0.0, p - armed) / (1 - armed);
    final y = top - disc + (rest + disc) * KitMotion.enter.transform(reach);
    final centre = Offset(size.width / 2, y + disc / 2 + 12 * beyond);
    final gone = KitMotion.enter.transform(leave.value);
    final scale = 1 - gone;
    if (scale <= 0 || reach <= 0) return;

    canvas.save();
    canvas.translate(centre.dx, centre.dy);
    canvas.scale(scale);
    final opacity = math.min(1.0, reach * 2);
    canvas.drawCircle(
      Offset.zero,
      disc / 2,
      KitDraw.fill(KitDraw.fade(palette.surface, opacity)),
    );
    canvas.drawCircle(
      Offset.zero,
      disc / 2,
      KitDraw.pen(KitDraw.fade(palette.line, opacity), 1),
    );
    // The portal, drawn at 28 dp inside the disc.
    const inner = 28.0;
    final box = _scene.box;
    canvas.translate(-inner / 2, -inner / 2);
    canvas.scale(inner / box.width);
    final looping = phase == _Phase.refreshing && loop.isAnimating;
    _scene.paint(
      canvas,
      KitSceneFrame(
        entrance: phase == _Phase.pulling ? reach : 1,
        // Two turns of the spark per breath: a wait of a second or two
        // still sees it move.
        loop: looping ? (loop.value * 2) % 1 : 0,
        looping: looping,
        palette: palette,
      ),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_RefreshPainter old) =>
      old.phase != phase ||
      old.palette != palette ||
      old.top != top ||
      old.rest != rest;
}
