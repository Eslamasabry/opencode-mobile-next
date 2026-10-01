part of 'kit_sheet.dart';

/// Lays out the frame's header, scrolling body and pinned action block
/// (KIT-17) so that it never overflows, whatever the text size and the
/// keyboard leave:
///
/// - the pinned block keeps its natural height, up to the frame's height
///   less [reserve] (past that it is cut at the bottom, never overflowed:
///   its primary comes first);
/// - the header stays fixed on top while it leaves the body at least
///   [reserve] and at least as much room as it takes itself ([_bodyShare]
///   of the room under the pinned block);
///   otherwise it takes its place inside the body's scroll view (through
///   [_KitHeaderSpacer]) and scrolls away with it, so at 250 % text on a
///   small phone the body is not squeezed under a header that fills the
///   sheet;
/// - the body takes the rest and scrolls.
///
/// Children, in reading order: header, body scroll view, optional actions.
/// The header stays outside the scroll view in both modes, so a swipe on
/// the grabber or the header still drags the bottom sheet.
class _KitSheetFrame extends MultiChildRenderObjectWidget {
  const _KitSheetFrame({
    required this.fill,
    required this.reserve,
    required super.children,
  }) : assert(children.length == 2 || children.length == 3);

  final bool fill;
  final double reserve;

  @override
  _RenderKitSheetFrame createRenderObject(BuildContext context) =>
      _RenderKitSheetFrame(fill: fill, reserve: reserve);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderKitSheetFrame renderObject,
  ) {
    renderObject
      ..fill = fill
      ..reserve = reserve;
  }
}

class _KitSheetFrameParentData extends ContainerBoxParentData<RenderBox> {}

class _RenderKitSheetFrame extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _KitSheetFrameParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, _KitSheetFrameParentData> {
  _RenderKitSheetFrame({required bool fill, required double reserve})
    : _fill = fill,
      _reserve = reserve;

  bool _fill;
  set fill(bool value) {
    if (value == _fill) return;
    _fill = value;
    markNeedsLayout();
  }

  double _reserve;
  set reserve(double value) {
    if (value == _reserve) return;
    _reserve = value;
    markNeedsLayout();
  }

  /// The share of the room above the pinned block a fixed header must
  /// leave the body.
  static const double _bodyShare = 0.5;

  /// The header's place inside the scroll view, and how far it scrolled.
  _RenderKitHeaderSpacer? _spacer;
  double _spacerExtent = 0;
  double _scrolled = 0;

  /// The header scrolls with the body (no room to keep it fixed).
  bool _scrollsHeader = false;

  /// The pinned block is taller than the room it has, and is cut.
  bool _actionsCut = false;

  final _clip = LayerHandle<ClipRectLayer>();
  final _headerClip = LayerHandle<ClipRectLayer>();

  RenderBox get _header => firstChild!;
  RenderBox get _scroll => childAfter(_header)!;
  RenderBox? get _actions => childAfter(_scroll);

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! _KitSheetFrameParentData) {
      child.parentData = _KitSheetFrameParentData();
    }
  }

  void _placeHeader() {
    final data = _header.parentData! as _KitSheetFrameParentData;
    data.offset = Offset(
      0,
      _scrollsHeader ? -math.min(_scrolled, _spacerExtent) : 0,
    );
  }

  /// The spacer reports the body's scroll offset on every layout.
  void _scrolledTo(double scrolled) {
    if (scrolled == _scrolled) return;
    _scrolled = scrolled;
    if (!_scrollsHeader) return;
    _placeHeader();
    markNeedsPaint();
    markNeedsSemanticsUpdate();
  }

  @override
  void performLayout() {
    final constraints = this.constraints;
    final width = constraints.maxWidth;
    final widthOnly = BoxConstraints.tightFor(width: width);
    final maxHeight = constraints.maxHeight;
    final targetHeight = _fill && maxHeight.isFinite
        ? maxHeight
        : constraints.minHeight;

    var actionsHeight = 0.0;
    final actions = _actions;
    if (actions != null) {
      actions.layout(widthOnly, parentUsesSize: true);
      actionsHeight = actions.size.height;
      if (maxHeight.isFinite) {
        actionsHeight = math.min(
          actionsHeight,
          math.max(0.0, maxHeight - _reserve),
        );
      }
      _actionsCut = actionsHeight < actions.size.height;
    } else {
      _actionsCut = false;
    }

    final header = _header..layout(widthOnly, parentUsesSize: true);
    final headerHeight = header.size.height;
    final room = maxHeight - actionsHeight;
    final minBody = room.isFinite
        ? math.max(_reserve, room * _bodyShare)
        : _reserve;
    _scrollsHeader = headerHeight + minBody > room;
    final spacerExtent = _scrollsHeader ? headerHeight : 0.0;
    if (spacerExtent != _spacerExtent) {
      invokeLayoutCallback<BoxConstraints>((_) {
        _spacerExtent = spacerExtent;
        _spacer?.markNeedsLayout();
      });
    }

    final fixedHeader = _scrollsHeader ? 0.0 : headerHeight;
    final scrollRoom = math.max(0.0, room - fixedHeader);
    final scrollMin = math.min(
      scrollRoom,
      math.max(0.0, targetHeight - actionsHeight - fixedHeader),
    );
    final scroll = _scroll
      ..layout(
        BoxConstraints(
          minWidth: width,
          maxWidth: width,
          minHeight: scrollMin,
          maxHeight: scrollRoom,
        ),
        parentUsesSize: true,
      );
    size = constraints.constrain(
      Size(width, fixedHeader + scroll.size.height + actionsHeight),
    );
    (scroll.parentData! as _KitSheetFrameParentData).offset = Offset(
      0,
      fixedHeader,
    );
    if (actions != null) {
      (actions.parentData! as _KitSheetFrameParentData).offset = Offset(
        0,
        size.height - actionsHeight,
      );
    }
    _placeHeader();
  }

  /// Where the scroll view sits; a header that scrolls with the body is
  /// drawn (and hit) only inside it, never over the pinned block.
  Rect get _scrollRect {
    final data = _scroll.parentData! as _KitSheetFrameParentData;
    return data.offset & _scroll.size;
  }

  Offset _offsetOf(RenderBox child) =>
      (child.parentData! as _KitSheetFrameParentData).offset;

  @override
  void paint(PaintingContext context, Offset offset) {
    if (_actionsCut) {
      _clip.layer = context.pushClipRect(
        needsCompositing,
        offset,
        Offset.zero & size,
        _paintChildren,
        oldLayer: _clip.layer,
      );
    } else {
      _clip.layer = null;
      _paintChildren(context, offset);
    }
  }

  void _paintChildren(PaintingContext context, Offset offset) {
    context.paintChild(_scroll, offset + _offsetOf(_scroll));
    if (_scrollsHeader) {
      _headerClip.layer = context.pushClipRect(
        needsCompositing,
        offset,
        _scrollRect,
        (context, offset) =>
            context.paintChild(_header, offset + _offsetOf(_header)),
        oldLayer: _headerClip.layer,
      );
    } else {
      _headerClip.layer = null;
      context.paintChild(_header, offset + _offsetOf(_header));
    }
    if (_actions case final actions?) {
      context.paintChild(actions, offset + _offsetOf(actions));
    }
  }

  bool _hitChild(BoxHitTestResult result, RenderBox child, Offset position) =>
      result.addWithPaintOffset(
        offset: _offsetOf(child),
        position: position,
        hitTest: (result, transformed) =>
            child.hitTest(result, position: transformed),
      );

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    if (_actions case final actions?) {
      if (_hitChild(result, actions, position)) return true;
    }
    if (!_scrollsHeader) {
      return _hitChild(result, _header, position) ||
          _hitChild(result, _scroll, position);
    }
    // A header that scrolls with the body lies over the scroll view: its
    // own controls take taps first, and the scroll view still gets the
    // pointer, so a drag on the header scrolls (it may fill the view).
    if (!_scrollRect.contains(position)) return false;
    final header = _hitChild(result, _header, position);
    final scroll = _hitChild(result, _scroll, position);
    return header || scroll;
  }

  @override
  Rect? describeApproximatePaintClip(RenderObject child) {
    if (child == _header && _scrollsHeader) return _scrollRect;
    if (_actionsCut) return Offset.zero & size;
    return null;
  }

  @override
  double? computeDistanceToActualBaseline(TextBaseline baseline) =>
      defaultComputeDistanceToFirstActualBaseline(baseline);

  @override
  void dispose() {
    _clip.layer = null;
    _headerClip.layer = null;
    super.dispose();
  }
}

/// The header's place at the top of the frame's scroll view: empty while
/// the header stays fixed, the header's height while it scrolls with the
/// body. It reports the scroll offset to the frame that draws the header.
class _KitHeaderSpacer extends LeafRenderObjectWidget {
  const _KitHeaderSpacer();

  @override
  _RenderKitHeaderSpacer createRenderObject(BuildContext context) =>
      _RenderKitHeaderSpacer();
}

class _RenderKitHeaderSpacer extends RenderSliver {
  _RenderKitSheetFrame? _frame;

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    RenderObject? node = parent;
    while (node != null && node is! _RenderKitSheetFrame) {
      node = node.parent;
    }
    _frame = node as _RenderKitSheetFrame?;
    _frame?._spacer = this;
  }

  @override
  void detach() {
    if (_frame?._spacer == this) _frame?._spacer = null;
    _frame = null;
    super.detach();
  }

  @override
  void performLayout() {
    final extent = _frame?._spacerExtent ?? 0.0;
    final paintExtent = calculatePaintOffset(constraints, from: 0, to: extent);
    geometry = SliverGeometry(
      scrollExtent: extent,
      paintExtent: paintExtent,
      maxPaintExtent: extent,
      hitTestExtent: paintExtent,
      cacheExtent: calculateCacheOffset(constraints, from: 0, to: extent),
    );
    _frame?._scrolledTo(constraints.scrollOffset);
  }
}
