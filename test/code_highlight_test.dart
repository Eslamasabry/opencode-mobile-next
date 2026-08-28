import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

Future<void> _pumpMarkdown(WidgetTester tester, String data) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      home: Scaffold(body: MarkdownText(data)),
    ),
  );
}

TextSpan _codeSpan(WidgetTester tester) {
  final selectable = tester.widget<SelectableText>(
    find.byType(SelectableText),
  );
  return selectable.textSpan!;
}

/// Walks the span tree collecting each leaf text with the style it inherits
/// from its ancestors, which is where the highlighter attaches token styles.
List<({String text, TextStyle? style})> _leaves(
  InlineSpan span, [
  TextStyle? inherited,
]) {
  if (span is! TextSpan) return const [];
  final effective = span.style == null
      ? inherited
      : (inherited?.merge(span.style) ?? span.style);
  final result = <({String text, TextStyle? style})>[];
  if (span.text?.isNotEmpty == true) {
    result.add((text: span.text!, style: effective));
  }
  for (final child in span.children ?? const <InlineSpan>[]) {
    result.addAll(_leaves(child, effective));
  }
  return result;
}

void main() {
  testWidgets('fenced Dart code renders emphasized keyword spans', (
    tester,
  ) async {
    await _pumpMarkdown(tester, '```dart\nclass Foo {}\n```');

    final selectable = tester.widget<SelectableText>(
      find.byType(SelectableText),
    );
    expect(selectable.style?.fontFamily, 'AppMono');
    final leaves = _leaves(_codeSpan(tester));
    final keyword = leaves.firstWhere((leaf) => leaf.text == 'class');
    expect(keyword.style?.fontWeight, FontWeight.w600);
  });

  testWidgets('an unknown fence language stays plain text', (tester) async {
    await _pumpMarkdown(tester, '```mystery\nclass Foo {}\n```');

    final span = _codeSpan(tester);
    expect(span.children, isNull);
    expect(span.text, 'class Foo {}');
  });

  testWidgets('diff fences color additions and deletions apart', (
    tester,
  ) async {
    await _pumpMarkdown(tester, '```diff\n+added line\n-removed line\n```');

    final leaves = _leaves(_codeSpan(tester));
    final added = leaves.firstWhere((leaf) => leaf.text.startsWith('+'));
    final removed = leaves.firstWhere((leaf) => leaf.text.startsWith('-'));
    expect(added.style?.color, isNotNull);
    expect(removed.style?.color, isNotNull);
    expect(added.style?.color, isNot(removed.style?.color));
  });
}
