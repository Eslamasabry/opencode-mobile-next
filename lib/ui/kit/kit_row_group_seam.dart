part of 'kit_row.dart';

// How a KitRowGroup keeps a small gap under another framed group: the
// panel's mark and the seam above it.

/// Marks a [KitRowGroup]'s panel (inside its margin) for [_KitGroupSeam].
class _KitRowGroupMark extends SingleChildRenderObjectWidget {
  const _KitRowGroupMark({required super.child});

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderKitRowGroupMark();
}

class _RenderKitRowGroupMark extends RenderProxyBox {}

/// The space above an unlabelled [KitRowGroup] whose style frames its
/// panel: [gap] when the thing right above it is another row group with
/// nothing between them (no margin, padding or spacer), 0 otherwise. So two
/// framed groups never touch, and a group anywhere else keeps its place.
class _KitGroupSeam extends LeafRenderObjectWidget {
  const _KitGroupSeam({required this.gap});

  final double gap;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderKitGroupSeam(gap);

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderKitGroupSeam renderObject,
  ) {
    renderObject
      ..gap = gap
      // A group inserted or removed above it may change the answer even
      // when the gap itself did not.
      ..markNeedsLayout();
  }
}

class _RenderKitGroupSeam extends RenderBox {
  _RenderKitGroupSeam(this._gap);

  double _gap;
  set gap(double value) {
    if (value == _gap) return;
    _gap = value;
    markNeedsLayout();
  }

  double _height() => _abuts() ? _gap : 0;

  /// Walks up while this box is the first thing in each parent, until a
  /// parent that stacks its children: there, whether the child right
  /// before is a row group that ends flush.
  bool _abuts() {
    RenderObject node = this;
    while (true) {
      final parent = node.parent;
      if (parent == null || parent is RenderAbstractViewport) return false;
      // Space the caller put above the group already parts the two.
      if (parent is RenderPadding &&
          parent.padding.resolve(TextDirection.ltr).top > 0) {
        return false;
      }
      final data = node.parentData;
      if (data is ContainerParentDataMixin<RenderObject> &&
          ((parent is RenderFlex && parent.direction == Axis.vertical) ||
              data is SliverMultiBoxAdaptorParentData)) {
        final previous = data.previousSibling;
        if (previous != null) return _endsInGroup(previous);
      }
      node = parent;
    }
  }

  /// Whether [node] is a row group's panel with nothing below it, looking
  /// through wrappers that draw nothing.
  static bool _endsInGroup(RenderObject node) {
    RenderObject? current = node;
    while (current != null) {
      if (current is _RenderKitRowGroupMark) return true;
      if (current is RenderPadding) {
        if (current.padding.resolve(TextDirection.ltr).bottom > 0) {
          return false;
        }
        current = current.child;
      } else if (current is RenderProxyBox) {
        current = current.child;
      } else if (current is RenderShiftedBox) {
        // An Align or the width cap of a part: it draws nothing.
        current = current.child;
      } else {
        return false;
      }
    }
    return false;
  }

  @override
  double computeMinIntrinsicWidth(double height) => 0;

  @override
  double computeMaxIntrinsicWidth(double height) => 0;

  @override
  double computeMinIntrinsicHeight(double width) => _height();

  @override
  double computeMaxIntrinsicHeight(double width) => _height();

  @override
  Size computeDryLayout(covariant BoxConstraints constraints) =>
      constraints.constrain(Size(0, _height()));

  @override
  void performLayout() {
    size = constraints.constrain(Size(0, _height()));
  }
}
