// A reply that is still streaming never shows half-written Markdown: the
// unfinished tail is drawn as if closed (or, for a link, as its label), and
// the words that already showed do not fade in again when the syntax
// completes. A reply that stopped streaming draws its exact text.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown_unfinished.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Future<void> _pump(
  WidgetTester tester,
  String data, {
  bool streaming = true,
  bool interactive = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 380,
              child: KitMarkdown(
                data,
                streaming: streaming,
                selectable: false,
                interactive: interactive,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

/// Everything drawn, as plain words (the caret's placeholder left out).
String _drawn(WidgetTester tester) => tester
    .widgetList<RichText>(find.byType(RichText))
    .map((t) => t.text.toPlainText().replaceAll('￼', ''))
    .join('\n');

/// The style of the run that draws [words], with its parents' merged in.
TextStyle? _styleOf(WidgetTester tester, String words) {
  TextStyle? found;
  void walk(InlineSpan span, TextStyle? inherited) {
    if (span is! TextSpan) return;
    final style = inherited == null
        ? span.style
        : span.style == null
        ? inherited
        : inherited.merge(span.style);
    if ((span.text ?? '').contains(words)) found = style;
    for (final child in span.children ?? const <InlineSpan>[]) {
      walk(child, style);
    }
  }

  for (final text in tester.widgetList<RichText>(find.byType(RichText))) {
    walk(text.text, null);
  }
  return found;
}

double _alphaOf(WidgetTester tester, String words) {
  var lowest = 1.0;
  for (final text in tester.widgetList<RichText>(find.byType(RichText))) {
    text.text.visitChildren((span) {
      if (span is TextSpan && (span.text ?? '').trim().isNotEmpty) {
        final own = span.text!.trim();
        if (words.contains(own)) {
          final a = span.style?.color?.a;
          if (a != null && a < lowest) lowest = a;
        }
      }
      return true;
    });
  }
  return lowest;
}

void main() {
  // partial text, what is drawn while it streams, the finished text.
  const cases = <(String, String, String, String)>[
    ('bold', 'Make it **bold', 'Make it bold', 'Make it **bold**'),
    (
      'bold by underscores',
      'Make it __bold',
      'Make it bold',
      'Make it __bold__',
    ),
    ('italic', 'Make it *ita', 'Make it ita', 'Make it *ita*'),
    ('code', 'Run `cod', 'Run cod', 'Run `cod`'),
    ('strike', 'Make it ~~stri', 'Make it stri', 'Make it ~~stri~~'),
    (
      'link, label only',
      'See [the docs',
      'See the docs',
      'See [the docs](https://a.dev)',
    ),
    (
      'link, bracket closed',
      'See [the docs]',
      'See the docs',
      'See [the docs](https://a.dev)',
    ),
    (
      'link, half address',
      'See [the docs](htt',
      'See the docs',
      'See [the docs](https://a.dev)',
    ),
    ('mark with no words yet', 'Make it **', 'Make it ', 'Make it **bold**'),
    (
      'half a closing mark',
      'Make it **bold*',
      'Make it bold',
      'Make it **bold**',
    ),
    (
      'nested in a list',
      '- one\n- Make **bo',
      'one\nMake bo',
      '- one\n- Make **bo**',
    ),
  ];

  for (final (name, partial, shown, _) in cases) {
    testWidgets('streaming: $name shows no raw marks', (tester) async {
      await _pump(tester, partial);
      final text = _drawn(tester);
      expect(text, contains(shown.split('\n').last));
      expect(text, isNot(contains('*')));
      expect(text, isNot(contains('`')));
      expect(text, isNot(contains('~~')));
      expect(text, isNot(contains('__')));
      expect(text, isNot(contains('](')));
      expect(text, isNot(contains('[')));
    });
  }

  testWidgets('streaming: words arrive already bold, italic, in code, struck', (
    tester,
  ) async {
    await _pump(tester, 'Make it **bold');
    expect(_styleOf(tester, 'bold')?.fontWeight, FontWeight.w700);
    await _pump(tester, 'Make it *ita');
    expect(_styleOf(tester, 'ita')?.fontStyle, FontStyle.italic);
    await _pump(tester, 'Run `cod');
    expect(_styleOf(tester, 'cod')?.fontFamily, isNotNull);
    await _pump(tester, 'Make it ~~stri');
    expect(_styleOf(tester, 'stri')?.decoration, TextDecoration.lineThrough);
    await _pump(tester, 'See [the docs](htt', interactive: true);
    expect(_drawn(tester), 'See the docs');
  });

  testWidgets('closing the syntax does not fade shown words in again', (
    tester,
  ) async {
    for (final (name, partial, _, done) in cases.where(
      (c) => c.$1 != 'nested in a list' && c.$1 != 'mark with no words yet',
    )) {
      await _pump(tester, '');
      await _pump(tester, partial);
      await tester.pumpAndSettle();
      expect(_alphaOf(tester, 'Make it See Run'), 1, reason: name);
      await _pump(tester, done);
      final word = RegExp(
        r'(bold|ita|cod|stri|the docs)',
      ).firstMatch(done)![0]!;
      expect(_alphaOf(tester, word), 1, reason: '$name: "$word" stays solid');
      await tester.pumpAndSettle();
    }
  });

  testWidgets('a partial table row is held back until it has two cells', (
    tester,
  ) async {
    const table = '| a | b |\n| --- | --- |\n| 1 | 2 |\n';
    await _pump(tester, '$table| **x');
    expect(_drawn(tester), isNot(contains('|')));
    expect(_drawn(tester), isNot(contains('*')));
    await _pump(tester, '$table| **x** | **y');
    expect(_drawn(tester), isNot(contains('*')));
    expect(_drawn(tester), contains('y'));
  });

  testWidgets('an open fence is left as it is', (tester) async {
    await _pump(tester, '```dart\nfinal a = **b;\nx = `y');
    expect(_drawn(tester), contains('final a = **b;'));
    expect(_drawn(tester), contains('x = `y'));
  });

  testWidgets('a finished reply draws its exact text', (tester) async {
    for (final text in [
      'Make it **bold',
      'Make it *',
      'Run `cod',
      'See [the docs](htt',
      'Make it ~~stri',
      'a_b and [1]',
    ]) {
      await _pump(tester, text, streaming: false);
      expect(_drawn(tester), contains(text.replaceAll('**', '**')));
    }
    await _pump(tester, 'Ends with a lone *', streaming: false);
    expect(_drawn(tester), contains('Ends with a lone *'));
  });

  testWidgets('the end of streaming draws the real text', (tester) async {
    await _pump(tester, 'It ends with *');
    expect(_drawn(tester), isNot(contains('*')));
    await _pump(tester, 'It ends with *', streaming: false);
    expect(_drawn(tester), contains('It ends with *'));
  });

  testWidgets('streaming re-parses once per delta, as before', (tester) async {
    await _pump(tester, 'Make it');
    final before = KitMarkdown.debugParseCount;
    for (final more in ['Make it **', 'Make it **bo', 'Make it **bold**']) {
      await _pump(tester, more);
    }
    expect(KitMarkdown.debugParseCount - before, 3);
    // Same text again: cached.
    await _pump(tester, 'Make it **bold**');
    expect(KitMarkdown.debugParseCount - before, 3);
    // The reply ends and its text was already exact: no new parse.
    await _pump(tester, 'Make it **bold**', streaming: false);
    expect(KitMarkdown.debugParseCount - before, 3);
  });

  group('kitMarkdownCloseTail', () {
    test('only the last line changes', () {
      expect(
        kitMarkdownCloseTail('**one\n\nthen **two'),
        '**one\n\nthen **two**',
      );
    });
    test('finished syntax is untouched', () {
      for (final s in [
        'a **b** c',
        'a `b` c',
        'see [a](https://b.c) now',
        '2 * 3',
        'arr[0',
        '- [ ] todo',
        '- [x] done',
        'snake_case_name',
        '![Image](/tmp/x.png)',
        'a \\*b',
        '',
      ]) {
        expect(kitMarkdownCloseTail(s), s);
      }
    });
    test('unfinished syntax closes', () {
      expect(kitMarkdownCloseTail('a **b *c'), 'a **b *c***');
      expect(kitMarkdownCloseTail('a **b `c'), 'a **b `c`**');
      expect(kitMarkdownCloseTail('a ~~b '), 'a ~~b~~ ');
      expect(kitMarkdownCloseTail('a [b](c'), 'a b');
      expect(kitMarkdownCloseTail('a `'), 'a ');
    });
  });
}
