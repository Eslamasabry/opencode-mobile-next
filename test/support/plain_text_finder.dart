import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

/// [text] without the bidi isolates and word joiners the app puts around a
/// command, a path or a project name inside a sentence.
String plainText(String text) =>
    text.replaceAll(RegExp('[\u2066-\u2069\u2060]'), '');

/// Text widgets that read [text] once the isolates are taken out ("Run git
/// status?", "Edit main.dart?").
Finder findPlainText(String text) => find.byWidgetPredicate(
  (widget) =>
      widget is Text &&
      plainText(widget.data ?? widget.textSpan?.toPlainText() ?? '') == text,
  description: 'text "$text" (isolates ignored)',
);
