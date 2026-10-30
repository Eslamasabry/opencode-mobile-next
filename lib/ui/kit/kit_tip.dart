import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';

import '../../l10n/app_localizations.dart';
import 'kit_buttons.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The teaching system's tip (docs/ux-system/teaching.md): one sentence of
/// 3–8 words anchored to the control it teaches ("Hold to stop after this
/// step"). It shows while [visible], over everything and never in the way:
/// it takes no touch of its own, any touch anywhere fades it, and it fades
/// by itself after [hold]. Then [onDismissed] is called once.
///
/// The words are announced politely to a screen reader when it appears.
/// It fades in and rises a few points; under reduced motion it is there at
/// once, with no movement (MOT-7).
/// It sits above the anchor when there is room, below it otherwise, and
/// lines up with the anchor's nearer edge so it never leaves the screen.
/// It reads in RTL and wraps at 2× text. Never amber: amber means "needs
/// you".
///
/// The host decides when (the tip controller's rules); this part only draws.
///
/// States: none — it draws the one sentence it is given, or nothing.
class KitTip extends StatefulWidget {
  const KitTip({
    super.key,
    required this.child,
    required this.message,
    required this.visible,
    required this.onDismissed,
    this.hold = defaultHold,
    this.tipKey,
  });

  /// The control the tip points at.
  final Widget child;

  /// One short sentence.
  final String message;

  final bool visible;

  /// The tip faded (by itself or on a touch).
  final VoidCallback onDismissed;

  /// How long it stays without a touch.
  final Duration hold;

  /// On the bubble, for tests.
  final Key? tipKey;

  static const defaultHold = Duration(seconds: 4);

  /// The fade in and out.
  static const fade = Duration(milliseconds: 200);

  /// The widest a tip grows.
  static const maxWidth = KitLayout.popoverMaxWidth - 40;

  @override
  State<KitTip> createState() => _KitTipState();
}

class _KitTipState extends State<KitTip> with SingleTickerProviderStateMixin {
  final _portal = OverlayPortalController();
  final _link = LayerLink();
  late final _fade = AnimationController(vsync: this, duration: KitTip.fade);
  Timer? _holdTimer;
  bool _routed = false;
  bool _closing = false;

  /// Where the bubble goes, worked out once when it appears.
  ({bool above, bool alignEnd}) _place = (above: true, alignEnd: true);

  @override
  void initState() {
    super.initState();
    if (widget.visible) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _show());
    }
  }

  @override
  void didUpdateWidget(KitTip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.visible && !oldWidget.visible) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _show());
    } else if (!widget.visible && oldWidget.visible) {
      _hide(notify: false);
    }
  }

  void _show() {
    if (!mounted || !widget.visible || _portal.isShowing) return;
    final box = context.findRenderObject() as RenderBox?;
    final size = MediaQuery.sizeOf(context);
    if (box != null && box.hasSize) {
      final centre = box.localToGlobal(box.size.center(Offset.zero));
      final top = box.localToGlobal(Offset.zero).dy;
      _place = (
        above: top > size.height * .25,
        alignEnd: centre.dx > size.width / 2,
      );
    }
    _closing = false;
    _portal.show();
    // Reduced motion: it is there at once, as every kit part (MOT-7).
    _fade.duration = KitMotion.reduced(context) ? Duration.zero : KitTip.fade;
    unawaited(_fade.forward(from: 0));
    if (!_routed) {
      GestureBinding.instance.pointerRouter.addGlobalRoute(_onPointer);
      _routed = true;
    }
    _holdTimer?.cancel();
    _holdTimer = Timer(widget.hold, () => _hide(notify: true));
    final view = View.of(context);
    final direction = Directionality.of(context);
    unawaited(
      SemanticsService.sendAnnouncement(view, widget.message, direction),
    );
  }

  void _onPointer(PointerEvent event) {
    if (event is PointerDownEvent) _hide(notify: true);
  }

  void _unroute() {
    if (!_routed) return;
    GestureBinding.instance.pointerRouter.removeGlobalRoute(_onPointer);
    _routed = false;
  }

  void _hide({required bool notify}) {
    _holdTimer?.cancel();
    _unroute();
    if (!_portal.isShowing || _closing) return;
    _closing = true;
    _fade.duration = KitMotion.reduced(context) ? Duration.zero : KitTip.fade;
    _fade.reverse().whenComplete(() {
      if (!mounted) return;
      if (_closing) _portal.hide();
      _closing = false;
      if (notify) widget.onDismissed();
    });
  }

  @override
  void dispose() {
    _holdTimer?.cancel();
    _unroute();
    _fade.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final still = KitMotion.reduced(context);
    return OverlayPortal(
      controller: _portal,
      overlayChildBuilder: (context) {
        final tokens = KitTokens.of(context);
        final above = _place.above;
        final end = _place.alignEnd;
        final targetAnchor = above
            ? (end ? Alignment.topRight : Alignment.topLeft)
            : (end ? Alignment.bottomRight : Alignment.bottomLeft);
        final followerAnchor = above
            ? (end ? Alignment.bottomRight : Alignment.bottomLeft)
            : (end ? Alignment.topRight : Alignment.topLeft);
        final width = math.min(
          KitTip.maxWidth,
          MediaQuery.sizeOf(context).width - 2 * tokens.gutter,
        );
        final rise = still ? 0.0 : (above ? 4.0 : -4.0);
        return CompositedTransformFollower(
          link: _link,
          showWhenUnlinked: false,
          targetAnchor: targetAnchor,
          followerAnchor: followerAnchor,
          offset: Offset(0, above ? -tokens.space2 : tokens.space2),
          child: Align(
            alignment: followerAnchor,
            child: IgnorePointer(
              child: AnimatedBuilder(
                animation: _fade,
                builder: (context, child) {
                  final t = KitMotion.enter.transform(_fade.value);
                  return Opacity(
                    opacity: t,
                    child: Transform.translate(
                      offset: Offset(0, rise * (1 - t)),
                      child: child,
                    ),
                  );
                },
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: width),
                  child: KitTipBubble(
                    key: widget.tipKey,
                    message: widget.message,
                  ),
                ),
              ),
            ),
          ),
        );
      },
      child: CompositedTransformTarget(link: _link, child: widget.child),
    );
  }
}

/// The tip's look on its own: one sentence on the panel surface, in the
/// primary text tone. KitTip, the tour offer and the tour's card draw it.
///
/// States: none — it draws the sentence and actions it is given.
class KitTipBubble extends StatelessWidget {
  const KitTipBubble({super.key, required this.message, this.actions});

  final String message;

  /// Tertiary words under the sentence (the tour offer's two choices).
  final List<KitAction>? actions;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final actions = this.actions ?? const <KitAction>[];
    final style = KitSurfaceLook.styleOf(context);
    final radius = BorderRadius.circular(tokens.popoverCorner);
    return Material(
      type: MaterialType.transparency,
      // A floating piece: the style paints it.
      child: kitSurfaceBox(
        style: style,
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: tokens.panelSurface,
            borderRadius: radius,
            border: Border.all(
              color: tokens.roles.hairline,
              width: KitTokens.hairlineWidth(context),
            ),
            boxShadow: tokens.floatingShadows(context),
          ),
          level: KitSurfaceLevel.surface2,
          role: KitSurfaceRole.floating,
          borderRadius: radius,
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            end: tokens.space3,
            top: tokens.space2,
            bottom: actions.isEmpty ? tokens.space2 : tokens.space1,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Semantics(
                container: true,
                label: message,
                child: ExcludeSemantics(
                  child: KitText(
                    message,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.primary,
                  ),
                ),
              ),
              if (actions.isNotEmpty)
                Wrap(
                  spacing: tokens.space1,
                  children: [
                    for (final action in actions)
                      KitButton.fromAction(
                        action,
                        role: KitButtonRole.tertiary,
                      ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// One step of a [KitTour]: a real control on screen, found by its key,
/// and one sentence about it.
@immutable
class KitTourStep {
  const KitTourStep({
    required this.anchors,
    required this.message,
    this.around,
  });

  /// The control's keys (a list, so a step can point at whichever of
  /// several cards is on screen).
  final List<Key> anchors;

  /// Highlights the nearest enclosing widget of this type instead (the
  /// carousel around a card).
  final Type? around;

  final String message;
}

/// The teaching system's mini tutorial (docs/ux-system/teaching.md): 2–4
/// steps, each highlighting one real control with one sentence, with Next
/// and Skip. It is offered ([offer]), never forced, and can be left at any
/// step (Skip, Back, or a tap outside the card). Each step scrolls its
/// control into view and cuts it out of a dim scrim.
///
/// Reduced motion: the highlight jumps and the card fades; otherwise the
/// highlight glides between controls. The card's words are a live region,
/// read as each step appears. It reads in RTL and wraps at 2× text.
abstract final class KitTour {
  /// Runs [steps] over the screen of [context]. Completes with true when
  /// the last step's Done was pressed, false when it was left early.
  static Future<bool> show(
    BuildContext context, {
    required List<KitTourStep> steps,
  }) async {
    assert(steps.length >= 2 && steps.length <= 4, 'KitTour: 2–4 steps');
    final navigator = Navigator.of(context);
    final still = KitMotion.reduced(context);
    // A popup route, not a page route: a page route on top drives the
    // screen's own exit transition, which fades the very screen the tour
    // points at out of sight and leaves the cut-out black.
    final done = await navigator.push<bool>(
      RawDialogRoute<bool>(
        barrierColor: null,
        barrierDismissible: false,
        transitionDuration: still ? Duration.zero : KitTip.fade,
        pageBuilder: (_, _, _) =>
            _TourView(steps: steps, scope: navigator.context),
        transitionBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
      ),
    );
    return done ?? false;
  }

  /// Offers a tour once, as a tip-like card at the bottom of the screen of
  /// [context]: [message] ("Your Appearance studio"), [startLabel] ("Show
  /// me how · 30 s") and [notNowLabel]. It never blocks: the screen works
  /// under it, and it leaves when either is pressed or the screen is left.
  /// Completes with true when the person chose to start.
  static Future<bool> offer(
    BuildContext context, {
    required String message,
    required String startLabel,
    required String notNowLabel,
    Key? offerKey,
  }) {
    final result = Completer<bool>();
    final overlay = Overlay.maybeOf(context, rootOverlay: true);
    final route = ModalRoute.of(context);
    if (overlay == null) return Future.value(false);
    late final OverlayEntry entry;
    void close(bool start) {
      if (result.isCompleted) return;
      result.complete(start);
      entry.remove();
    }

    entry = OverlayEntry(
      builder: (_) => _TourOffer(
        key: offerKey,
        route: route,
        message: message,
        startLabel: startLabel,
        notNowLabel: notNowLabel,
        onClose: close,
      ),
    );
    overlay.insert(entry);
    final view = View.of(context);
    final direction = Directionality.of(context);
    unawaited(SemanticsService.sendAnnouncement(view, message, direction));
    return result.future;
  }

  /// The last element under [scope] whose widget has one of [keys] and a
  /// laid-out box: the newest route's copy wins over one behind it.
  static Element? _find(BuildContext scope, List<Key> keys) {
    Element? found;
    void visit(Element element) {
      final key = element.widget.key;
      if (key != null && keys.contains(key)) {
        final box = element.renderObject;
        if (box is RenderBox && box.attached && box.hasSize) found = element;
      }
      element.visitChildElements(visit);
    }

    (scope as Element).visitChildElements(visit);
    return found;
  }
}

class _TourOffer extends StatefulWidget {
  const _TourOffer({
    super.key,
    required this.route,
    required this.message,
    required this.startLabel,
    required this.notNowLabel,
    required this.onClose,
  });

  final ModalRoute<Object?>? route;
  final String message, startLabel, notNowLabel;
  final void Function(bool start) onClose;

  @override
  State<_TourOffer> createState() => _TourOfferState();
}

class _TourOfferState extends State<_TourOffer>
    with SingleTickerProviderStateMixin {
  late final _fade = AnimationController(vsync: this, duration: KitTip.fade)
    ..forward();

  @override
  void initState() {
    super.initState();
    widget.route?.animation?.addStatusListener(_routeMoved);
    widget.route?.secondaryAnimation?.addStatusListener(_routeMoved);
  }

  /// The screen is leaving, or another covers it: the offer goes too.
  void _routeMoved(AnimationStatus _) {
    final route = widget.route;
    if (route == null) return;
    final leaving =
        route.animation?.status == AnimationStatus.reverse ||
        route.animation?.status == AnimationStatus.dismissed;
    final covered = route.secondaryAnimation?.status == AnimationStatus.forward;
    if (leaving || covered) {
      scheduleMicrotask(() => widget.onClose(false));
    }
  }

  @override
  void dispose() {
    widget.route?.animation?.removeStatusListener(_routeMoved);
    widget.route?.secondaryAnimation?.removeStatusListener(_routeMoved);
    _fade.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final padding = MediaQuery.paddingOf(context);
    return Positioned(
      left: tokens.gutter,
      right: tokens.gutter,
      bottom: padding.bottom + tokens.space4,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: KitLayout.stateMaxWidth),
          child: FadeTransition(
            opacity: _fade,
            child: KitTipBubble(
              message: widget.message,
              actions: [
                KitAction(
                  key: const ValueKey('kit-tour-offer-start'),
                  label: widget.startLabel,
                  onPressed: () => widget.onClose(true),
                ),
                KitAction(
                  key: const ValueKey('kit-tour-offer-not-now'),
                  label: widget.notNowLabel,
                  onPressed: () => widget.onClose(false),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _TourView extends StatefulWidget {
  const _TourView({required this.steps, required this.scope});

  final List<KitTourStep> steps;
  final BuildContext scope;

  @override
  State<_TourView> createState() => _TourViewState();
}

class _TourViewState extends State<_TourView> {
  int _index = 0;
  Rect? _hole;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _locate());
  }

  Future<void> _locate() async {
    if (!mounted) return;
    final step = widget.steps[_index];
    var element = KitTour._find(widget.scope, step.anchors);
    if (element != null && step.around != null) {
      Element? outer;
      element.visitAncestorElements((ancestor) {
        if (ancestor.widget.runtimeType != step.around) return true;
        outer = ancestor;
        return false;
      });
      element = outer ?? element;
    }
    if (element != null) {
      final still = KitMotion.reduced(context);
      await Scrollable.ensureVisible(
        element,
        alignment: .3,
        duration: still ? Duration.zero : KitMotion.standard,
        curve: KitMotion.enter,
      );
    }
    if (!mounted) return;
    final box = element?.renderObject as RenderBox?;
    final hole = box != null && box.attached && box.hasSize
        ? box.localToGlobal(Offset.zero) & box.size
        : null;
    // A little room around the control, kept on the screen.
    final screen = Offset.zero & MediaQuery.sizeOf(context);
    setState(() => _hole = hole?.inflate(6).intersect(screen.deflate(3)));
    final view = View.of(context);
    final direction = Directionality.of(context);
    unawaited(SemanticsService.sendAnnouncement(view, step.message, direction));
  }

  void _next() {
    if (_index == widget.steps.length - 1) {
      Navigator.of(context).pop(true);
      return;
    }
    setState(() => _index++);
    unawaited(_locate());
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final still = KitMotion.reduced(context);
    final size = MediaQuery.sizeOf(context);
    final padding = MediaQuery.paddingOf(context);
    final step = widget.steps[_index];
    final last = _index == widget.steps.length - 1;
    final hole = _hole;
    final below = hole == null || hole.center.dy < size.height / 2;

    final card = KitTipBubble(
      key: ValueKey('kit-tour-step-$_index'),
      message: step.message,
      actions: [
        KitAction(
          key: const ValueKey('kit-tour-next'),
          label: last ? l10n.kitTourDone : l10n.kitTourNext,
          onPressed: _next,
        ),
        if (!last)
          KitAction(
            key: const ValueKey('kit-tour-skip'),
            label: l10n.kitTourSkip,
            onPressed: () => Navigator.of(context).pop(false),
          ),
      ],
    );
    final counted = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space1,
            bottom: tokens.space1,
          ),
          child: KitText(
            l10n.kitTourStep(_index + 1, widget.steps.length),
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ),
        card,
      ],
    );

    final top = hole == null
        ? null
        : below
        ? math.min(hole.bottom + tokens.space3, size.height - padding.bottom)
        : null;
    final bottom = hole == null
        ? padding.bottom + tokens.space6
        : below
        ? null
        : size.height - hole.top + tokens.space3;

    return Semantics(
      scopesRoute: true,
      namesRoute: true,
      explicitChildNodes: true,
      label: l10n.kitTourStep(_index + 1, widget.steps.length),
      child: Material(
        type: MaterialType.transparency,
        child: Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                excludeFromSemantics: true,
                onTap: () => Navigator.of(context).pop(false),
                // Until the first control is found: the scrim alone.
                child: hole == null
                    ? CustomPaint(
                        painter: _ScrimPainter(
                          hole: null,
                          scrim: roles.ground.withValues(alpha: .72),
                          ring: roles.accent,
                          ringWidth: KitTokens.focusRingWidth(context),
                        ),
                      )
                    : TweenAnimationBuilder<Rect?>(
                        tween: RectTween(begin: hole, end: hole),
                        duration: still ? Duration.zero : KitMotion.standard,
                        curve: KitMotion.enter,
                        builder: (context, rect, _) => CustomPaint(
                          painter: _ScrimPainter(
                            hole: rect,
                            scrim: roles.ground.withValues(alpha: .72),
                            ring: roles.accent,
                            ringWidth: KitTokens.focusRingWidth(context),
                          ),
                        ),
                      ),
              ),
            ),
            Positioned(
              left: tokens.gutter,
              right: tokens.gutter,
              top: top,
              bottom: bottom,
              child: Align(
                alignment: below ? Alignment.topCenter : Alignment.bottomCenter,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: KitLayout.stateMaxWidth,
                  ),
                  child: Semantics(
                    liveRegion: true,
                    child: AnimatedSwitcher(
                      duration: still ? KitTip.fade : KitMotion.quick,
                      child: KeyedSubtree(
                        key: ValueKey(_index),
                        child: counted,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ScrimPainter extends CustomPainter {
  _ScrimPainter({
    required this.hole,
    required this.scrim,
    required this.ring,
    required this.ringWidth,
  });

  final Rect? hole;
  final double ringWidth;
  final Color scrim;
  final Color ring;

  @override
  void paint(Canvas canvas, Size size) {
    final whole = Path()..addRect(Offset.zero & size);
    final hole = this.hole;
    if (hole == null) {
      canvas.drawPath(whole, Paint()..color = scrim);
      return;
    }
    final cut = RRect.fromRectAndRadius(
      hole,
      const Radius.circular(KitTokens.popoverRadius),
    );
    canvas.drawPath(
      Path.combine(PathOperation.difference, whole, Path()..addRRect(cut)),
      Paint()..color = scrim,
    );
    canvas.drawRRect(
      cut,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = ringWidth
        ..color = ring,
    );
  }

  @override
  bool shouldRepaint(_ScrimPainter old) =>
      old.hole != hole ||
      old.scrim != scrim ||
      old.ring != ring ||
      old.ringWidth != ringWidth;
}
