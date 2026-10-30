// The capacity gate's probes (docs/ux-system/kit-v2.md §8.6): read a laid-out
// render tree and say how wide a part really paints, which overflow rule
// each of its text slots has, and which text is cut without saying so.
// Pure functions over render objects, so test/kit/kit_capacity_test.dart can
// prove each one on small stand-ins before the shards trust them.
import 'dart:math' as math;

import 'package:flutter/rendering.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';

import '../support/stress_content.dart';

/// Calls [visit] on every render object under [root] (itself included) that
/// is shown: an offstage subtree or one under a fully transparent opacity is
/// skipped, as nothing in it reaches the screen.
void visitShown(RenderObject root, void Function(RenderObject node) visit) {
  void walk(RenderObject node) {
    if (node is RenderOffstage && node.offstage) return;
    if (node is RenderOpacity && node.opacity == 0) return;
    if (node is RenderAnimatedOpacity && node.opacity.value == 0) return;
    visit(node);
    node.visitChildren(walk);
  }

  walk(root);
}

bool _visible(Color? color) => color != null && color.a > 0;

/// Whether [box] itself puts ink on the screen: text, an image, a filled or
/// outlined decoration, a coloured box, a physical shape or a custom painter.
/// Layout boxes, hit areas and transparent decorations do not count, so a
/// part that centres a 720 dp card in a 1,600 dp box is 720 dp wide.
bool paintsInk(RenderBox box) {
  if (box is RenderParagraph) return box.text.toPlainText().isNotEmpty;
  if (box is RenderEditable) return true;
  if (box is RenderImage) return box.image != null;
  if (box is RenderCustomPaint) {
    return _painterShows(box.painter) || _painterShows(box.foregroundPainter);
  }
  if (box is RenderPhysicalModel) {
    return _visible(box.color) || box.elevation > 0;
  }
  if (box is RenderPhysicalShape) {
    return _visible(box.color) || box.elevation > 0;
  }
  if (box is RenderDecoratedBox) return _decorationShows(box.decoration);
  // ColoredBox's render object is private; its type name is stable.
  if (box.runtimeType.toString() == '_RenderColoredBox') {
    try {
      return _visible((box as dynamic).color as Color);
    } on Object {
      return true;
    }
  }
  return false;
}

/// A Material's outline painter draws its shape's side and nothing else: a
/// shape with no visible side (a text button's) puts no ink down. Any other
/// painter counts.
bool _painterShows(CustomPainter? painter) {
  if (painter == null) return false;
  if (painter.runtimeType.toString() != '_ShapeBorderPainter') return true;
  try {
    final border = (painter as dynamic).border;
    return border is! OutlinedBorder || _sideShows(border.side);
  } on Object {
    return true;
  }
}

bool _sideShows(BorderSide side) =>
    side.style != BorderStyle.none && side.width > 0 && _visible(side.color);

bool _decorationShows(Decoration decoration) {
  // The style resolver's surface: a text button's is all clear.
  if (decoration is KitSurfaceDecoration) {
    return _visible(decoration.fill) ||
        _visible(decoration.fillStart) ||
        (decoration.edgeWidth > 0 &&
            (_visible(decoration.edgeStart) || _visible(decoration.edgeEnd))) ||
        _visible(decoration.lightShadow) ||
        _visible(decoration.darkShadow) ||
        _visible(decoration.hardShadow) ||
        decoration.shadows.isNotEmpty;
  }
  if (decoration is BoxDecoration) {
    return _visible(decoration.color) ||
        decoration.border != null ||
        decoration.gradient != null ||
        decoration.image != null ||
        (decoration.boxShadow?.isNotEmpty ?? false);
  }
  if (decoration is ShapeDecoration) {
    final shape = decoration.shape;
    final outlined = shape is OutlinedBorder && _sideShows(shape.side);
    return _visible(decoration.color) ||
        decoration.gradient != null ||
        decoration.image != null ||
        (decoration.shadows?.isNotEmpty ?? false) ||
        outlined;
  }
  return true;
}

/// The global rect of [box], or null when it is not laid out or attached.
/// The box's four corners are carried through every transform above it, so
/// a glyph turned half a turn (an open chevron) is where it is painted, not
/// a box's size past the turned origin.
Rect? globalRect(RenderBox box) {
  if (!box.attached || !box.hasSize) return null;
  try {
    return MatrixUtils.transformRect(
      box.getTransformTo(null),
      Offset.zero & box.size,
    );
  } on Object {
    return null;
  }
}

/// How wide the ink under [root] spans on screen: the union of every shown
/// box that [paintsInk], clamped to [window]. Boxes under any of [exclude]
/// (a modal barrier, the page under a sheet) are left out, and so is a box
/// that covers the whole [window] when [skipFullWindow] is set (a scrim).
/// Zero when nothing paints.
double paintedWidth(
  RenderObject root, {
  required Rect window,
  Set<RenderObject> exclude = const {},
  bool skipFullWindow = false,
}) {
  double? left;
  double? right;
  void walk(RenderObject node, Rect? clip) {
    if (exclude.contains(node)) return;
    if (node is RenderOffstage && node.offstage) return;
    if (node is RenderOpacity && node.opacity == 0) return;
    if (node is RenderAnimatedOpacity && node.opacity.value == 0) return;
    if (node is RenderBox && paintsInk(node)) {
      var rect = globalRect(node)?.intersect(window);
      // Ink a clipping ancestor cuts off (a sideways scroller's far end) is
      // not painted.
      if (rect != null && clip != null) rect = rect.intersect(clip);
      final fullWindow =
          rect != null &&
          rect.width >= window.width - .5 &&
          rect.height >= window.height - .5;
      if (rect != null &&
          rect.width > 0 &&
          rect.height > 0 &&
          !(skipFullWindow && fullWindow)) {
        left = left == null ? rect.left : math.min(left!, rect.left);
        right = right == null ? rect.right : math.max(right!, rect.right);
      }
    }
    var inner = clip;
    if (node is RenderBox && _clipsChildren(node)) {
      final own = globalRect(node);
      if (own != null) inner = clip == null ? own : own.intersect(clip);
    }
    node.visitChildren((child) => walk(child, inner));
  }

  walk(root, null);
  if (left == null) return 0;
  return right! - left!;
}

/// Whether [box] cuts off what its children paint outside it: a clip, or a
/// scrolling viewport (their default is a hard edge).
bool _clipsChildren(RenderBox box) {
  if (box is RenderClipRect) return box.clipBehavior != Clip.none;
  if (box is RenderClipRRect) return box.clipBehavior != Clip.none;
  if (box is RenderClipPath) return box.clipBehavior != Clip.none;
  if (box is RenderClipOval) return box.clipBehavior != Clip.none;
  if (box is RenderViewportBase) return box.clipBehavior != Clip.none;
  if (box.runtimeType.toString().contains('Viewport')) {
    try {
      return (box as dynamic).clipBehavior != Clip.none;
    } on Object {
      return false;
    }
  }
  return false;
}

/// Whether [p] draws an icon font glyph (Icon is a RichText of one private
/// use code point), which is not a text slot.
bool isIconText(RenderParagraph p) {
  final text = p.text.toPlainText();
  if (text.isEmpty) return false;
  return text.runes.every(
    (r) => (r >= 0xE000 && r <= 0xF8FF) || r >= 0xF0000 || r == 0x20,
  );
}

bool _hasPlaceholders(InlineSpan span) {
  var found = false;
  span.visitChildren((child) {
    if (child is PlaceholderSpan) found = true;
    return !found;
  });
  return found;
}

/// The nearest scrolling viewport above [node] (a ListView's or a
/// SingleChildScrollView's), or null.
RenderAbstractViewport? scrollingAncestor(RenderObject node) {
  for (var a = node.parent; a != null; a = a.parent) {
    if (a case final RenderAbstractViewport viewport) return viewport;
  }
  return null;
}

/// The axis [viewport] scrolls along, or null when it cannot be read.
Axis? scrollAxis(RenderAbstractViewport viewport) {
  if (viewport is RenderViewportBase) return viewport.axis;
  try {
    return axisDirectionToAxis(
      (viewport as dynamic).axisDirection as AxisDirection,
    );
  } on Object {
    return null;
  }
}

/// The overflow rule a text slot is set up with (§8.6):
///
/// - `wrap`: as many lines as the words need;
/// - `ellipsis:N` / `fade:N`: at most N lines, the cut shown;
/// - `scroll`: one line inside a sideways scroller;
/// - `clip:N` / `visible:N`: at most N lines, the cut not shown. Not a
///   valid contract rule: such a slot loses words without saying so.
String textRule(RenderParagraph p) {
  final ellipsized =
      p.overflow == TextOverflow.ellipsis || p.overflow == TextOverflow.fade;
  // An ellipsis with no line limit keeps the text to one line.
  final lines = p.maxLines ?? ((!p.softWrap || ellipsized) ? 1 : null);
  if (lines == null) return 'wrap';
  final scroller = scrollingAncestor(p);
  if (lines == 1 &&
      scroller != null &&
      scrollAxis(scroller) == Axis.horizontal) {
    return 'scroll';
  }
  return '${p.overflow.name}:$lines';
}

/// Whether [rule] is one a contract may declare.
bool isValidTextRule(String rule) =>
    rule == 'wrap' ||
    rule == 'scroll' ||
    RegExp(r'^(ellipsis|fade):[1-9]\d*$').hasMatch(rule);

/// Why [p] loses words without showing it, or null when every word it was
/// given is on screen or its cut is shown (an ellipsis or a fade):
///
/// - its own box is smaller than its words (more lines than allowed, a long
///   unbroken line, or a parent that fixed its height) and it neither
///   ellipsizes nor fades;
/// - a clipping ancestor (ClipRect, a clipped Stack or Material) cuts its
///   box, up to the nearest scroller, which owns its own overflow.
String? silentCut(RenderParagraph p) {
  if (!p.attached || !p.hasSize || p.debugNeedsLayout) return null;
  if (isIconText(p)) return null;
  final text = p.text.toPlainText();
  if (text.trim().isEmpty) return null;
  final shown =
      p.overflow == TextOverflow.ellipsis || p.overflow == TextOverflow.fade;
  final scroller = scrollingAncestor(p);
  final scrollsSideways =
      scroller != null && scrollAxis(scroller) == Axis.horizontal;
  var exceeded = p.didExceedMaxLines;
  var wide = false;
  var tall = false;
  if (!_hasPlaceholders(p.text)) {
    final painter = TextPainter(
      text: p.text,
      textDirection: p.textDirection,
      textAlign: p.textAlign,
      textScaler: p.textScaler,
      maxLines: p.maxLines,
      locale: p.locale,
      strutStyle: p.strutStyle,
      textWidthBasis: p.textWidthBasis,
      textHeightBehavior: p.textHeightBehavior,
    );
    try {
      painter.layout(maxWidth: p.softWrap ? p.size.width : double.infinity);
      wide = painter.width > p.size.width + .5;
      tall = painter.height > p.size.height + .5;
      exceeded = exceeded || painter.didExceedMaxLines;
    } finally {
      painter.dispose();
    }
  }
  final sample = _prefix(text, 40);
  if (tall && !exceeded) {
    return 'its box is shorter than its lines ("$sample")';
  }
  if ((exceeded || wide) && !shown && !scrollsSideways) {
    return 'cut with no ellipsis (${textRule(p)}, "$sample")';
  }
  final own = globalRect(p);
  if (own == null) return null;
  for (var a = p.parent; a != null; a = a.parent) {
    if (a is RenderAbstractViewport) break;
    // The sheet frame scrolls its header with its body itself (not a
    // viewport): words under it are reached by scrolling, not cut.
    if (a.runtimeType.toString() == '_RenderKitSheetFrame') break;
    if (a is! RenderBox) continue;
    final clips = switch (a) {
      RenderClipRect(:final clipBehavior) ||
      RenderClipRRect(:final clipBehavior) ||
      RenderClipPath(:final clipBehavior) ||
      RenderClipRSuperellipse(:final clipBehavior) ||
      RenderClipOval(:final clipBehavior) ||
      RenderPhysicalModel(:final clipBehavior) ||
      RenderPhysicalShape(:final clipBehavior) ||
      RenderStack(:final clipBehavior) => clipBehavior != Clip.none,
      _ => false,
    };
    if (!clips) continue;
    final bounds = globalRect(a);
    if (bounds == null) continue;
    if (own.left < bounds.left - 1 ||
        own.right > bounds.right + 1 ||
        own.top < bounds.top - 1 ||
        own.bottom > bounds.bottom + 1) {
      return 'cut by ${a.runtimeType} ("$sample")';
    }
  }
  return null;
}

/// The key a text slot is declared under: its words as written, a clock
/// time folded to `#:##` (scenes that read the real clock: "9:05 AM" and
/// "11:40 PM" are one slot), other numbers folded to `#` (so counts do not
/// churn the contract), spaces collapsed, at most 48 characters.
String slotKey(String text) {
  final folded = text
      .replaceAll(RegExp(r'\d{1,2}:\d{2}(?:\s?[AP]M)?'), '#:##')
      .replaceAll(RegExp(r'\d'), '#')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  return _prefix(folded, 48, mark: '');
}

/// At most [length] characters of [text], never splitting a surrogate pair,
/// with [mark] when it was cut.
String _prefix(String text, int length, {String mark = '…'}) {
  final runes = text.runes;
  if (runes.length <= length) return text;
  return '${String.fromCharCodes(runes.take(length))}$mark';
}

/// Pseudo-localizes every text slot under [root] in place (the part's own
/// copy, which a scene cannot reach: labels from the app's localizations,
/// fixed words), keeping styles and inline widgets. Text the scene already
/// pseudo-localized and icon glyphs are left alone. The caller lays the tree
/// out again before reading it.
void pseudoLocalizeTree(RenderObject root) {
  visitShown(root, (node) {
    if (node is! RenderParagraph || isIconText(node)) return;
    final text = node.text;
    if (text.toPlainText().contains(StressContent.pseudoOpen)) return;
    node.text = _pseudoSpan(text);
  });
}

InlineSpan _pseudoSpan(InlineSpan span) {
  if (span is! TextSpan) return span;
  return TextSpan(
    text: span.text == null ? null : StressContent.pseudo(span.text!),
    children: span.children?.map(_pseudoSpan).toList(),
    style: span.style,
    recognizer: span.recognizer,
    mouseCursor: span.mouseCursor,
    onEnter: span.onEnter,
    onExit: span.onExit,
    semanticsLabel: span.semanticsLabel,
    locale: span.locale,
    spellOut: span.spellOut,
  );
}
