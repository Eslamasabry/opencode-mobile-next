// Behaviour tests for KitMarkdown (docs/ux-system/kit-api/KitMarkdown.md,
// "Tests required" 1-13; the owner's 2026-09-27 decision drops the Arabic
// review, so 12 and 13 run the English/LTR halves plus one direction check).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/widgets/agent_blocks.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

import 'kit_motion_still.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  double textScale = 1,
  TextDirection? direction,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: direction == null
            ? inner!
            : Directionality(textDirection: direction, child: inner!),
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
  await tester.pump();
}

/// The KitText (of any constructor) whose plain text contains [text].
KitText _kitTextWith(WidgetTester tester, String text) {
  final matches = tester
      .widgetList<KitText>(find.byType(KitText))
      .where((w) => (w.span?.toPlainText() ?? w.text).contains(text))
      .toList();
  expect(matches, isNotEmpty, reason: 'no KitText contains "$text"');
  return matches.first;
}

/// The TextSpan (inside any rich text) whose own text is [text].
TextSpan? _spanWith(WidgetTester tester, String text) {
  TextSpan? found;
  for (final w in tester.widgetList<KitText>(find.byType(KitText))) {
    w.span?.visitChildren((span) {
      if (span is TextSpan && span.text == text) {
        found = span;
        return false;
      }
      return true;
    });
    if (found != null) break;
  }
  return found;
}

void main() {
  kitMotionStillTests(
    'KitMarkdown',
    builds: {
      'prose': () => const KitMarkdown(
        'Review **checkout** before release.',
        selectable: false,
      ),
      'streaming fence': () =>
          const KitMarkdown('```dart\nfinal ready = true;', selectable: false),
    },
    changes: {
      'reply grows': KitMotionChange(
        build: () => const KitMarkdown('Checking files', selectable: false),
        act: (tester, stage) => stage.rebuild(
          const KitMarkdown('The checkout is ready.', selectable: false),
        ),
        shows: 'The checkout is ready.',
      ),
    },
  );

  setUp(() => KitMarkdown.debugParseCount = 0);

  testWidgets('1. blocks render in the role table', (tester) async {
    await _pump(
      tester,
      const KitMarkdown(
        '# Title\n\n### Small\n\nSome **bold** and `code` here.\n\n'
        '- one\n- two\n\n> quoted\n\n---\n\n[site](https://example.com)',
      ),
    );
    expect(_kitTextWith(tester, 'Title').role, KitTextRole.headline);
    expect(_kitTextWith(tester, 'Small').role, KitTextRole.rowTitle);
    expect(_kitTextWith(tester, 'Some bold').role, KitTextRole.body);
    expect(_kitTextWith(tester, 'one').role, KitTextRole.body);
    final quote = _kitTextWith(tester, 'quoted');
    expect(quote.tone, KitTextTone.secondary);
    // Inline code is mono; bold is bold, not asterisks.
    final code = _spanWith(tester, 'code')!;
    expect(
      code.style!.fontFamily,
      KitText.styleFor(KitTextRole.mono).fontFamily,
    );
    expect(find.textContaining('**', findRichText: true), findsNothing);
    // The link is accent + underline (never colour alone).
    final link = _spanWith(tester, 'site')!;
    expect(link.style!.decoration, TextDecoration.underline);
    expect(link.recognizer, isNotNull);
    // Secondary role for a thought or a tool's note.
    await _pump(
      tester,
      const KitMarkdown('A thought', role: KitTextRole.secondary),
    );
    expect(_kitTextWith(tester, 'A thought').role, KitTextRole.secondary);
  });

  testWidgets('1b. an ordered list split by a code block keeps counting', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitMarkdown(
        '1. Upgrade the router\n\n```\nnpm install router@6\n```\n\n'
        '2. Or keep v5\n3. Then test\n\n- a\n- b',
      ),
    );
    // CommonMark starts a list at its first number: 1, then 2 and 3.
    expect(find.text('1.'), findsOneWidget);
    expect(find.text('2.'), findsOneWidget);
    expect(find.text('3.'), findsOneWidget);
  });

  testWidgets('2. streaming parses once per change and reuses earlier blocks', (
    tester,
  ) async {
    const head = 'First paragraph.\n\n```dart\nfinal a = 1;\n```\n\n';
    await _pump(tester, const KitMarkdown('${head}Tail'));
    expect(KitMarkdown.debugParseCount, 1);
    final firstBefore = tester.widget(
      find.ancestor(
        of: find.byWidget(_kitTextWith(tester, 'First paragraph.')),
        matching: find.byWidgetPredicate(
          (w) => w.runtimeType.toString() == '_KitMdParagraph',
        ),
      ),
    );
    await _pump(tester, const KitMarkdown('${head}Tail grows'));
    expect(KitMarkdown.debugParseCount, 2);
    final firstAfter = tester.widget(
      find.ancestor(
        of: find.byWidget(_kitTextWith(tester, 'First paragraph.')),
        matching: find.byWidgetPredicate(
          (w) => w.runtimeType.toString() == '_KitMdParagraph',
        ),
      ),
    );
    expect(identical(firstBefore, firstAfter), isTrue);
    // A rebuild with the same data does not parse again.
    await _pump(tester, const KitMarkdown('${head}Tail grows'));
    expect(KitMarkdown.debugParseCount, 2);
  });

  testWidgets('3. an unclosed fence is unhighlighted until it closes', (
    tester,
  ) async {
    await _pump(tester, const KitMarkdown('```dart\nfinal a = 1;'));
    expect(
      tester.widget<KitCodeBlock>(find.byType(KitCodeBlock)).highlight,
      isFalse,
    );
    await _pump(tester, const KitMarkdown('```dart\nfinal a = 1;\n```'));
    final block = tester.widget<KitCodeBlock>(find.byType(KitCodeBlock));
    expect(block.highlight, isTrue);
    expect(block.language, 'dart');
    expect(block.copyText, 'final a = 1;\n');
    expect(block.maxLines, 12);
  });

  testWidgets('4. blockBuilder claims fences, once per closed fence', (
    tester,
  ) async {
    final calls = <String>[];
    Widget? builder(BuildContext context, String info, String body) {
      calls.add('$info:$body');
      return info == 'choices'
          ? const Text('CLAIMED', key: ValueKey('claimed'))
          : null;
    }

    await _pump(tester, KitMarkdown('```choices\nA\nB', blockBuilder: builder));
    // Still streaming: never offered to the builder.
    expect(calls, isEmpty);
    expect(find.byType(KitCodeBlock), findsOneWidget);
    const closed = '```choices\nA\nB\n```\n\n```sh\nls\n```';
    await _pump(tester, KitMarkdown(closed, blockBuilder: builder));
    expect(find.byKey(const ValueKey('claimed')), findsOneWidget);
    expect(find.byType(KitCodeBlock), findsOneWidget);
    expect(calls, ['choices:A\nB', 'sh:ls']);
    // More tokens after the fences: the claimed fence is not rebuilt.
    await _pump(tester, KitMarkdown('$closed\n\nmore', blockBuilder: builder));
    expect(calls, ['choices:A\nB', 'sh:ls', 'sh:ls']);
  });

  testWidgets('5. the highlighter decorates without changing the text', (
    tester,
  ) async {
    TextSpan mark(BuildContext context, TextSpan span, {String? source}) =>
        TextSpan(
          style: const TextStyle(backgroundColor: Color(0x33FFEE00)),
          children: [span],
        );
    await _pump(tester, KitMarkdown('Find **this** word.', highlighter: mark));
    final text = _kitTextWith(tester, 'Find');
    expect(text.span!.toPlainText(), 'Find this word.');
    expect(
      (text.span! as TextSpan).style?.backgroundColor,
      const Color(0x33FFEE00),
    );
  });

  testWidgets(
    '6. a link goes through openExternalLink; non-interactive is text',
    (tester) async {
      await _pump(
        tester,
        const KitMarkdown('Read [the docs](https://docs.example.com/a).'),
      );
      final link = _spanWith(tester, 'the docs')!;
      (link.recognizer! as TapGestureRecognizer).onTap!();
      await tester.pumpAndSettle();
      // openExternalLink's confirmation names the host before anything opens.
      expect(find.textContaining('docs.example.com'), findsWidgets);

      await _pump(
        tester,
        const KitMarkdown(
          'Read [the docs](https://docs.example.com/a).',
          interactive: false,
        ),
      );
      await tester.pumpAndSettle();
      expect(_spanWith(tester, 'the docs')!.recognizer, isNull);
      // A bare URL is a link too.
      await _pump(tester, const KitMarkdown('See https://example.com/x.'));
      expect(_spanWith(tester, 'https://example.com/x')!.recognizer, isNotNull);
    },
  );

  testWidgets('7. a path chip lights up only after validate says true', (
    tester,
  ) async {
    final gate = Completer<bool>();
    final opened = <String>[];
    await _pump(
      tester,
      KitMarkdown(
        'Open `lib/a/b.dart:12` now.',
        fileLinks: KitMarkdownFileLinks(
          validate: (_) => gate.future,
          open: opened.add,
        ),
      ),
    );
    expect(find.byKey(const Key('path-link-lib/a/b.dart:12')), findsNothing);
    gate.complete(true);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('path-link-lib/a/b.dart:12')));
    expect(opened, ['lib/a/b.dart:12']);

    await _pump(
      tester,
      KitMarkdown(
        'Missing `/tmp/gone.txt` file.',
        fileLinks: KitMarkdownFileLinks(
          validate: (_) async => false,
          open: (_) => fail('must not open'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('path-link-/tmp/gone.txt')), findsNothing);
    expect(find.text('/tmp/gone.txt'), findsOneWidget);
    // Flush the bounded re-validation timers.
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(seconds: 23));
    }
    expect(KitMarkdown.looksLikeFilePath('and/or'), isFalse);
    expect(KitMarkdown.stripPathLineSuffix('lib/a.dart:120'), 'lib/a.dart');
  });

  testWidgets('8. a 12-column table scrolls inside its box at 320 dp', (
    tester,
  ) async {
    final header =
        '| ${[for (var i = 0; i < 12; i++) 'Column$i'].join(' | ')} |';
    final rule = '|${List.filled(12, ' --- ').join('|')}|';
    final row = '| ${[for (var i = 0; i < 12; i++) 'value$i'].join(' | ')} |';
    await _pump(
      tester,
      KitMarkdown('$header\n$rule\n$row'),
      size: const Size(320, 800),
    );
    expect(tester.takeException(), isNull);
    final scroller = find.descendant(
      of: find.byType(KitMarkdown),
      matching: find.byWidgetPredicate(
        (w) =>
            w is SingleChildScrollView && w.scrollDirection == Axis.horizontal,
      ),
    );
    expect(scroller, findsOneWidget);
    // First column never narrower than its longest word: it stays on one
    // line.
    final painter = TextPainter(
      text: TextSpan(
        text: 'Column0',
        style: KitText.styleFor(
          KitTextRole.body,
        ).copyWith(fontWeight: FontWeight.w600),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    final first = tester.getSize(find.text('Column0', findRichText: true));
    expect(first.width, greaterThanOrEqualTo(painter.width));
    expect(first.height, lessThanOrEqualTo(painter.height + 1));
    painter.dispose();

    // A small table fits and does not scroll.
    await _pump(
      tester,
      const KitMarkdown('| A | B |\n| --- | --- |\n| 1 | 2 |'),
      size: const Size(320, 800),
    );
    expect(
      find.byWidgetPredicate(
        (w) =>
            w is SingleChildScrollView && w.scrollDirection == Axis.horizontal,
      ),
      findsNothing,
    );
  });

  testWidgets('9. codeLanguage and onOpenCode', (tester) async {
    await _pump(
      tester,
      const KitMarkdown('# not a heading\nplain', codeLanguage: 'md'),
    );
    final block = tester.widget<KitCodeBlock>(find.byType(KitCodeBlock));
    expect(block.text, '# not a heading\nplain');
    expect(block.language, 'md');
    expect(
      find.byType(KitText).evaluate().where((e) {
        final w = e.widget as KitText;
        return w.role == KitTextRole.headline;
      }),
      isEmpty,
    );

    final opened = <String>[];
    final long = [for (var i = 0; i < 30; i++) 'line $i'].join('\n');
    await _pump(
      tester,
      KitMarkdown(
        '```txt\n$long\n```',
        onOpenCode: (code, language) => opened.add('$language'),
      ),
    );
    final capped = tester.widget<KitCodeBlock>(find.byType(KitCodeBlock));
    expect(capped.onOpenFull, isNotNull);
    capped.onOpenFull!();
    expect(opened, ['txt']);
  });

  testWidgets('10. the MarkdownText wrapper forwards to the kit', (
    tester,
  ) async {
    final chosen = <String>[];
    await _pump(
      tester,
      MarkdownText('Pick:\n\n```choices\nRed\nBlue\n```', onChoice: chosen.add),
    );
    expect(find.byType(KitMarkdown), findsOneWidget);
    expect(find.byType(AgentChoicesBlock), findsOneWidget);
    await tester.tap(find.text('Blue'));
    await tester.pump();
    expect(chosen, ['Blue']);
    expect(MarkdownText.debugParseCount, KitMarkdown.debugParseCount);
    MarkdownText.debugParseCount = 7;
    expect(KitMarkdown.debugParseCount, 7);

    expect(
      markdownProseForSpeech('# Hi\n\n```\ncode\n```\n[a](https://x.y)'),
      'Hi\n\na',
    );
  });

  testWidgets('11. semantics: header and link nodes, no live region', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await _pump(
      tester,
      const KitMarkdown(
        '# Heading\n\nText with [a link](https://example.com).\n\n'
        '| A | B |\n| --- | --- |\n| 1 | 2 |',
        selectable: false,
      ),
    );
    expect(
      tester.getSemantics(find.text('Heading', findRichText: true)),
      isSemantics(isHeader: true),
    );
    final nodes = <SemanticsNode>[];
    void collect(SemanticsNode node) {
      nodes.add(node);
      node.visitChildren((child) {
        collect(child);
        return true;
      });
    }

    collect(
      tester
          .binding
          .renderViews
          .first
          .owner!
          .semanticsOwner!
          .rootSemanticsNode!,
    );
    expect(
      nodes.any((n) => n.flagsCollection.isLink && n.label == 'a link'),
      isTrue,
    );
    expect(nodes.any((n) => n.flagsCollection.isLiveRegion), isFalse);
    expect(nodes.any((n) => n.label.contains('Table, 1 row')), isTrue);
    handle.dispose();
  });

  testWidgets('12. an English paragraph in an RTL app reads LTR', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitMarkdown('An English reply with `code`.'),
      direction: TextDirection.rtl,
    );
    final text = find.byWidget(_kitTextWith(tester, 'An English reply'));
    final direction = find.ancestor(
      of: text,
      matching: find.byType(Directionality),
    );
    expect(
      tester.widget<Directionality>(direction.first).textDirection,
      TextDirection.ltr,
    );
  });

  for (final width in [320.0, 412.0]) {
    testWidgets('13. 200 % text at $width dp does not overflow', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitMarkdown(
          '# A long heading that must wrap on a phone\n\n'
          'Paragraph with `lib/state/connection.dart` and **bold** words.\n\n'
          '1. first item that is long enough to wrap\n2. second\n\n'
          '| Name | Path |\n| --- | --- |\n| conn | `lib/state/connection.dart` |\n\n'
          '```dart\nfinal value = compute(something, somethingElse);\n```',
        ),
        size: Size(width, 915),
        textScale: 2,
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('empty data renders nothing', (tester) async {
    await _pump(tester, const KitMarkdown('  \n '));
    expect(find.byType(KitText), findsNothing);
    expect(KitMarkdown.debugParseCount, 0);
  });

  test('proseForSpeech drops fences, tables and link targets', () {
    expect(
      KitMarkdown.proseForSpeech(
        'Hello **there**\n\n| a | b |\n| --- | --- |\n\n```\nx\n```\n'
        '[label](https://secret.example)',
      ),
      isNot(contains('secret')),
    );
  });
}
