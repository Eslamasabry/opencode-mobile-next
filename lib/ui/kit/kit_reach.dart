import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

/// How far a flat list inside may reach out past its own box into the
/// padding around it, on each side (docs/ux-system/kit-api/KitReach.md):
/// a sheet's rails. A row group with no fill of its own (Solid's flat
/// lists, Paper) uses it to run its rows to the sheet's edges, the way it
/// runs them to the screen's edges on a page, so the rows' words and
/// hairlines start on the sheet title's line instead of one row inset in.
///
/// [KitSheet] sets it for its body; it is zero everywhere else. A part that
/// reaches out ([extend]) sets it back to zero for what it holds, so
/// nothing reaches twice.
///
/// States: none — a scope that only carries a distance.
class KitReach extends InheritedWidget {
  const KitReach({super.key, required this.reach, required super.child})
    : assert(reach >= 0);

  /// On each side, in logical pixels.
  final double reach;

  /// The reach in force here: zero outside any [KitReach].
  static double of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<KitReach>()?.reach ?? 0;

  /// [child] laid out [by] wider on each side and drawn that far out past
  /// this box, which keeps its own width; taps in the reach land on
  /// [child] where its parents pass them on. Zero returns [child] as it is.
  static Widget extend({required double by, required Widget child}) {
    if (by <= 0) return child;
    return _KitReachBox(
      by: by,
      child: KitReach(reach: 0, child: child),
    );
  }

  @override
  bool updateShouldNotify(KitReach oldWidget) => oldWidget.reach != reach;
}

class _KitReachBox extends SingleChildRenderObjectWidget {
  const _KitReachBox({required this.by, required super.child});

  final double by;

  @override
  RenderObject createRenderObject(BuildContext context) => _RenderKitReach(by);

  @override
  void updateRenderObject(BuildContext context, _RenderKitReach renderObject) {
    renderObject.by = by;
  }
}

class _RenderKitReach extends RenderShiftedBox {
  _RenderKitReach(this._by) : super(null);

  double _by;
  set by(double value) {
    if (value == _by) return;
    _by = value;
    markNeedsLayout();
  }

  BoxConstraints _wider(BoxConstraints c) => BoxConstraints(
    minWidth: c.minWidth + 2 * _by,
    maxWidth: c.maxWidth + 2 * _by,
    minHeight: c.minHeight,
    maxHeight: c.maxHeight,
  );

  @override
  double computeMinIntrinsicWidth(double height) =>
      ((child?.getMinIntrinsicWidth(height) ?? 0) - 2 * _by).clamp(
        0,
        double.infinity,
      );

  @override
  double computeMaxIntrinsicWidth(double height) =>
      ((child?.getMaxIntrinsicWidth(height) ?? 0) - 2 * _by).clamp(
        0,
        double.infinity,
      );

  @override
  double computeMinIntrinsicHeight(double width) =>
      child?.getMinIntrinsicHeight(width + 2 * _by) ?? 0;

  @override
  double computeMaxIntrinsicHeight(double width) =>
      child?.getMaxIntrinsicHeight(width + 2 * _by) ?? 0;

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final child = this.child;
    if (child == null) return constraints.smallest;
    final inner = child.getDryLayout(_wider(constraints));
    return constraints.constrain(Size(inner.width - 2 * _by, inner.height));
  }

  @override
  void performLayout() {
    final child = this.child;
    if (child == null) {
      size = constraints.smallest;
      return;
    }
    child.layout(_wider(constraints), parentUsesSize: true);
    size = constraints.constrain(
      Size(child.size.width - 2 * _by, child.size.height),
    );
    (child.parentData! as BoxParentData).offset = Offset(-_by, 0);
  }

  /// The reach is part of this box for taps: a press at the very edge of a
  /// row that runs out to the sheet's edge still presses it.
  @override
  bool hitTest(BoxHitTestResult result, {required Offset position}) {
    final reach = Rect.fromLTWH(-_by, 0, size.width + 2 * _by, size.height);
    if (!reach.contains(position)) return false;
    if (hitTestChildren(result, position: position)) {
      result.add(BoxHitTestEntry(this, position));
      return true;
    }
    return false;
  }
}
