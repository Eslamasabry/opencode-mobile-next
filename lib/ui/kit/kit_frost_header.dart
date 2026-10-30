import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import 'kit_frost.dart';
import 'kit_tokens.dart';

/// The chat header's glass (owner, 2026-10-30): solid at rest, frosted
/// while the conversation is scrolled under it.
///
/// A page with a bar ([KitFrostHeader.frame], built by `KitScreen`) paints
/// its content first and its bar over it, and offers a [KitFrostHeaderHost]
/// to its body. A floating composer layer (`KitComposer.layer`) in that
/// body measures how far its own top is from the bar's; when nothing sits
/// between them (no status line, no loading bar running) it lets its body
/// run up under the bar by that much ([KitFrostHeaderHost.extendUp]) and
/// says whether content is scrolled under it ([KitFrostHeaderHost.under]).
///
/// The body opts in by reading [KitFrostHeader.topClearanceOf] for its
/// scroll view's top padding: a body that never reads it is never run
/// under the bar, so its first item is never hidden.
class KitFrostHeaderHost {
  KitFrostHeaderHost();

  /// How far the body under the bar is run up under it; 0 keeps it below.
  final ValueNotifier<double> extendUp = ValueNotifier(0);

  /// Content is scrolled under the bar now.
  final ValueNotifier<bool> under = ValueNotifier(false);

  /// Around the bar, to measure it.
  final GlobalKey barKey = GlobalKey(debugLabel: 'kit-frost-header-bar');

  /// The page's loading bar runs (between the bar and the body): no glass.
  bool loading = false;

  bool _disposed = false;

  /// A composer layer leaving the page: back to solid after this frame
  /// (listeners cannot rebuild while the tree is being torn down).
  void release() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_disposed) return;
      extendUp.value = 0;
      under.value = false;
    });
  }

  void dispose() {
    _disposed = true;
    extendUp.dispose();
    under.dispose();
  }
}

abstract final class KitFrostHeader {
  /// A page's [bar] over its [child] (KitScreen's page frame), frosted
  /// while content is under it; [extendTop] is the status bar above it.
  static Widget frame({
    required KitFrostHeaderHost host,
    required Widget bar,
    required Widget child,
    double extendTop = 0,
  }) => _KitFrostHeaderFrame(
    host: host,
    bar: bar,
    extendTop: extendTop,
    child: child,
  );

  /// Publishes the top clearance [value] to a body (the composer layer
  /// around its body); [claim] records whether the body read it.
  static Widget clearance({
    required double value,
    required KitFrostTopClaim claim,
    required Widget child,
  }) => _KitFrostTopClearance(value: value, claim: claim, child: child);

  /// The host of the page this context is on, or null (no bar).
  static KitFrostHeaderHost? hostOf(BuildContext context) =>
      context.getInheritedWidgetOfExactType<_KitFrostHeaderScope>()?.host;

  /// How much of the top of this body is under the frosted bar: a scroll
  /// view adds it to its top padding so its first item clears the bar.
  /// Reading it is the body's opt-in to running under the bar. 0 outside a
  /// composer layer, or while the bar is not over the body.
  static double topClearanceOf(BuildContext context) {
    final scope = context
        .dependOnInheritedWidgetOfExactType<_KitFrostTopClearance>();
    if (scope == null) return 0;
    scope.claim.honoured = true;
    return scope.value;
  }

  /// [host] around [child] (KitScreen's page frame).
  static Widget scope({
    required KitFrostHeaderHost host,
    required Widget child,
  }) => _KitFrostHeaderScope(host: host, child: child);

  /// Whether the body under [claim] has read [topClearanceOf].
  static bool honoured(KitFrostTopClaim claim) => claim.honoured;

  /// How far [body] (a composer layer's box) may run up under [host]'s
  /// bar: the distance from the bar's top to its own, when it sits straight
  /// under the bar; else 0.
  static double extendUpFor(KitFrostHeaderHost host, RenderBox body) {
    final bar = host.barKey.currentContext?.findRenderObject();
    return bar is RenderBox ? _extendUp(bar, body) : 0;
  }
}

/// Set when a body reads [KitFrostHeader.topClearanceOf] under a
/// [KitFrostHeader.clearance]; the composer layer keeps one per body.
class KitFrostTopClaim {
  bool honoured = false;
}

class _KitFrostTopClearance extends InheritedWidget {
  const _KitFrostTopClearance({
    required this.value,
    required this.claim,
    required super.child,
  });

  final double value;
  final KitFrostTopClaim claim;

  @override
  bool updateShouldNotify(_KitFrostTopClearance oldWidget) =>
      oldWidget.value != value;
}

class _KitFrostHeaderScope extends InheritedWidget {
  const _KitFrostHeaderScope({required this.host, required super.child});

  final KitFrostHeaderHost host;

  @override
  bool updateShouldNotify(_KitFrostHeaderScope oldWidget) =>
      oldWidget.host != host;
}

/// A page's bar over its content: laid out as a column (the bar on top at
/// its own height, the content below it), painted content first and bar
/// last, and hit-tested bar first, so a body run up under the bar
/// ([KitFrostHeaderHost.extendUp]) passes behind it. The bar is frosted
/// while content is under it.
class _KitFrostHeaderFrame extends StatelessWidget {
  const _KitFrostHeaderFrame({
    required this.host,
    required this.bar,
    required this.child,
    this.extendTop = 0,
  });

  final KitFrostHeaderHost host;
  final Widget bar;
  final Widget child;

  /// The status bar's height above the bar: the glass runs up through it.
  final double extendTop;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final band = ListenableBuilder(
      listenable: Listenable.merge([
        host.extendUp,
        host.under,
        GlassChoiceStore.choice,
      ]),
      child: KeyedSubtree(key: host.barKey, child: bar),
      builder: (context, bar) => KitFrost(
        role: KitFrostRole.header,
        strength: KitFrost.strengthOf(context),
        shown: host.extendUp.value > 0 && host.under.value,
        tint: tokens.roles.ground,
        paneInsets: EdgeInsets.only(top: -extendTop),
        child: bar!,
      ),
    );
    return KitFrostHeader.scope(
      host: host,
      child: _BarOverContent(bar: band, content: child),
    );
  }
}

/// A column of [bar] then [content] (the bar at its own height, the content
/// filling the rest), in that order for semantics, focus and finders, but
/// painted content first and hit-tested bar first, so a body run up under
/// the bar passes behind it.
class _BarOverContent extends MultiChildRenderObjectWidget {
  _BarOverContent({required Widget bar, required Widget content})
    : super(children: [bar, content]);

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderBarOverContent();
}

class _FrameParentData extends ContainerBoxParentData<RenderBox> {}

class _RenderBarOverContent extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _FrameParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, _FrameParentData> {
  RenderBox get _bar => firstChild!;
  RenderBox get _content => childAfter(firstChild!)!;

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! _FrameParentData) {
      child.parentData = _FrameParentData();
    }
  }

  @override
  Size computeDryLayout(BoxConstraints constraints) => constraints.biggest;

  @override
  void performLayout() {
    final width = constraints.maxWidth;
    // As a column lays it out: the bar at its own height, unbounded.
    _bar.layout(BoxConstraints.tightFor(width: width), parentUsesSize: true);
    final height = constraints.maxHeight;
    final below = (height - _bar.size.height).clamp(0.0, height);
    _content.layout(BoxConstraints.tightFor(width: width, height: below));
    (_bar.parentData! as _FrameParentData).offset = Offset.zero;
    (_content.parentData! as _FrameParentData).offset = Offset(
      0,
      _bar.size.height,
    );
    size = constraints.constrain(Size(width, height));
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    for (final child in [_content, _bar]) {
      context.paintChild(
        child,
        offset + (child.parentData! as _FrameParentData).offset,
      );
    }
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    for (final child in [_bar, _content]) {
      final data = child.parentData! as _FrameParentData;
      final hit = result.addWithPaintOffset(
        offset: data.offset,
        position: position,
        hitTest: (result, transformed) =>
            child.hitTest(result, position: transformed),
      );
      if (hit) return true;
    }
    return false;
  }
}

/// [KitFrostHeader.extendUpFor]'s measure, relative to the page frame.
double _extendUp(RenderBox bar, RenderBox body) {
  RenderObject? frame = bar.parent;
  while (frame != null && frame is! _RenderBarOverContent) {
    frame = frame.parent;
  }
  if (frame == null || !bar.hasSize || !body.hasSize || !body.attached) {
    return 0;
  }
  final barTop = bar.localToGlobal(Offset.zero, ancestor: frame).dy;
  final bodyTop = body.localToGlobal(Offset.zero, ancestor: frame).dy;
  final gap = bodyTop - (barTop + bar.size.height);
  // Straight under the bar: nothing between them but the idle loading bar.
  if (gap > -.5 && gap <= KitTokens.loadingBarHeight + .5) {
    return bodyTop - barTop;
  }
  return 0;
}
