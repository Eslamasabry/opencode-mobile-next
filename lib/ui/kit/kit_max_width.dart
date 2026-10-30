import 'dart:math' as math;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import 'kit_layout.dart';

/// The one place a kit part gets its maximum width (docs/ux-system/kit-v2.md
/// §8.6): a part never paints wider than its role's cap, whatever its caller
/// gives it. A part builds what it draws inside the constructor of its role;
/// the caller never repeats the number.
///
/// The screen owns the column (centred, [KitScreen]); a part fills the column
/// it is given, so a cap only bites on an absurdly wide parent. Where it
/// does, the part does not jump to the start edge on its own: page-level
/// parts (conversation, reading, list, state, sheet, confirm, control, glyph)
/// stay in the middle of the room they were given, as the column above them
/// would be; inline ones (a chip, a menu, a lane, words that start their
/// line) keep the start edge, and a value that ends its row keeps the end.
/// Where the box is no wider than the cap the child is laid out exactly as
/// it would be without this wrapper. Height is never limited.
///
/// States: none — a layout wrapper that draws nothing.
abstract final class KitMaxWidth {
  /// [child] at most [maxWidth] wide, in the middle of a wider box, or at
  /// [alignment] when that is given.
  static Widget cap({
    Key? key,
    required double maxWidth,
    AlignmentGeometry alignment = AlignmentDirectional.center,
    required Widget child,
  }) => _KitMaxWidthBox(
    key: key,
    maxWidth: maxWidth,
    alignment: alignment,
    child: child,
  );

  /// The conversation column: messages, turns, tool rows, the composer.
  static Widget conversation({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.paneDetailMaxWidth, child: child);

  /// Prose, cards, notices and forms.
  static Widget reading({
    Key? key,
    AlignmentGeometry alignment = AlignmentDirectional.center,
    required Widget child,
  }) => cap(
    key: key,
    maxWidth: KitLayout.readingWidth,
    alignment: alignment,
    child: child,
  );

  /// Rows, lists, tables, code and logs.
  static Widget list({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.listWidth, child: child);

  /// A page state: a short column in the middle.
  static Widget state({
    Key? key,
    AlignmentGeometry alignment = AlignmentDirectional.center,
    required Widget child,
  }) => cap(
    key: key,
    maxWidth: KitLayout.stateMaxWidth,
    alignment: alignment,
    child: child,
  );

  /// A board lane and its cards: one of a row of lanes, from the start.
  static Widget lane({Key? key, required Widget child}) => cap(
    key: key,
    maxWidth: KitLayout.laneMaxWidth,
    alignment: AlignmentDirectional.centerStart,
    child: child,
  );

  /// One button or compact control.
  static Widget control({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.controlMaxWidth, child: child);

  /// A chip, pill, tag or inline value: it hugs its words, from the start.
  static Widget chip({Key? key, required Widget child}) => cap(
    key: key,
    maxWidth: KitLayout.chipMaxWidth,
    alignment: AlignmentDirectional.centerStart,
    child: child,
  );

  /// An icon, mark, ring or avatar.
  static Widget glyph({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.glyphMaxWidth, child: child);

  /// A sheet's panel.
  static Widget sheet({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.sheetMaxWidth, child: child);

  /// A confirmation's panel.
  static Widget confirm({Key? key, required Widget child}) =>
      cap(key: key, maxWidth: KitLayout.confirmMediumWidth, child: child);

  /// A menu or bubble, from the start.
  static Widget popover({Key? key, required Widget child}) => cap(
    key: key,
    maxWidth: KitLayout.popoverMaxWidth,
    alignment: AlignmentDirectional.centerStart,
    child: child,
  );

  /// A sliver capped the same way: it gets whatever the scroll area has
  /// beyond [maxWidth] as padding on both sides,
  /// and is untouched where the area is no wider than the cap.
  static Widget sliver({required double maxWidth, required Widget sliver}) =>
      SliverLayoutBuilder(
        builder: (context, constraints) {
          final spare = math.max(0.0, constraints.crossAxisExtent - maxWidth);
          if (spare <= 0) return sliver;
          final before = spare / 2;
          final after = spare / 2;
          return SliverPadding(
            padding: EdgeInsetsDirectional.only(start: before, end: after),
            sliver: sliver,
          );
        },
      );
}

class _KitMaxWidthBox extends SingleChildRenderObjectWidget {
  const _KitMaxWidthBox({
    super.key,
    required this.maxWidth,
    required this.alignment,
    required super.child,
  });

  final double maxWidth;
  final AlignmentGeometry alignment;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderKitMaxWidth(maxWidth, alignment, _directionOf(context));

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderKitMaxWidth renderObject,
  ) {
    renderObject
      ..maxWidth = maxWidth
      ..alignment = alignment
      ..textDirection = _directionOf(context);
  }
}

TextDirection _directionOf(BuildContext context) =>
    Directionality.maybeOf(context) ?? TextDirection.ltr;

class _RenderKitMaxWidth extends RenderShiftedBox {
  _RenderKitMaxWidth(this._maxWidth, this._alignment, this._textDirection)
    : super(null);

  double _maxWidth;
  set maxWidth(double value) {
    if (value == _maxWidth) return;
    _maxWidth = value;
    markNeedsLayout();
  }

  AlignmentGeometry _alignment;
  set alignment(AlignmentGeometry value) {
    if (value == _alignment) return;
    _alignment = value;
    markNeedsLayout();
  }

  TextDirection _textDirection;
  set textDirection(TextDirection value) {
    if (value == _textDirection) return;
    _textDirection = value;
    markNeedsLayout();
  }

  BoxConstraints _inner(BoxConstraints c) {
    if (c.maxWidth <= _maxWidth) return c;
    return c.copyWith(
      minWidth: math.min(c.minWidth, _maxWidth),
      maxWidth: _maxWidth,
    );
  }

  double _dx(double outer, double inner) {
    final spare = outer - inner;
    if (spare <= 0) return 0;
    return (_alignment.resolve(_textDirection).x + 1) / 2 * spare;
  }

  @override
  double computeMinIntrinsicWidth(double height) =>
      math.min(child?.getMinIntrinsicWidth(height) ?? 0, _maxWidth);

  @override
  double computeMaxIntrinsicWidth(double height) =>
      math.min(child?.getMaxIntrinsicWidth(height) ?? 0, _maxWidth);

  @override
  double computeMinIntrinsicHeight(double width) =>
      child?.getMinIntrinsicHeight(math.min(width, _maxWidth)) ?? 0;

  @override
  double computeMaxIntrinsicHeight(double width) =>
      child?.getMaxIntrinsicHeight(math.min(width, _maxWidth)) ?? 0;

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final child = this.child;
    if (child == null) return constraints.smallest;
    final inner = child.getDryLayout(_inner(constraints));
    return constraints.constrain(inner);
  }

  @override
  double? computeDryBaseline(
    BoxConstraints constraints,
    TextBaseline baseline,
  ) {
    final child = this.child;
    if (child == null) return null;
    return child.getDryBaseline(_inner(constraints), baseline);
  }

  @override
  void performLayout() {
    final child = this.child;
    if (child == null) {
      size = constraints.smallest;
      return;
    }
    child.layout(_inner(constraints), parentUsesSize: true);
    size = constraints.constrain(child.size);
    (child.parentData! as BoxParentData).offset = Offset(
      _dx(size.width, child.size.width),
      0,
    );
  }
}
