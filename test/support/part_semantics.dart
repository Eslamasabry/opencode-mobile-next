import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

/// The semantics of the part [finder] finds: the first node at or below its
/// first render object. A kit part builds inside a `KitMaxWidth` that draws
/// and says nothing, so `tester.getSemantics(finder)` on the part's own type
/// would climb to a page above it; this reads what the part says.
SemanticsNode partSemantics(WidgetTester tester, Finder finder) {
  RenderObject? render = tester.renderObject(finder);
  while (render != null && render.debugSemantics == null) {
    RenderObject? next;
    render.visitChildren((child) => next ??= child);
    render = next;
  }
  return render!.debugSemantics!;
}
