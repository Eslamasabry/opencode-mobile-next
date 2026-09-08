import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double scale = 1,
  bool rtl = false,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: Scaffold(
        body: MediaQuery(
          data: MediaQueryData(textScaler: TextScaler.linear(scale)),
          child: Directionality(
            textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
            child: SingleChildScrollView(child: child),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  for (final scale in [1.0, 2.5]) {
    testWidgets('short code keeps a single toolbar row at ${scale}x', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(320, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await _pump(
        tester,
        const Padding(
          padding: EdgeInsets.all(16),
          child: CodeBlock(code: 'npm run dev', language: 'bash'),
        ),
        scale: scale,
      );
      final actions = ['Wrap lines', 'Full screen', 'Copy code'];
      final top = tester.getTopLeft(find.byTooltip(actions.first)).dy;
      for (final label in actions) {
        final action = find.byTooltip(label);
        expect(tester.getSize(action), const Size(48, 48));
        expect(tester.getTopLeft(action).dy, top);
        final data = tester.getSemantics(action).getSemanticsData();
        expect('${data.label} ${data.tooltip}', contains(label));
      }
      // Even with 2.5x text, a one-line snippet should not become a card
      // dominated by several rows of actions.
      expect(tester.getSize(find.byType(CodeBlock)).height, lessThan(125));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('RTL code starts at the beginning of its horizontal viewport', (
    tester,
  ) async {
    await _pump(
      tester,
      CodeBlock(code: 'final start = ${'value' * 100};'),
      rtl: true,
    );
    final horizontal = find.byWidgetPredicate(
      (w) =>
          w is Scrollable &&
          axisDirectionToAxis(w.axisDirection) == Axis.horizontal,
    );
    final state = tester.state<ScrollableState>(horizontal);
    expect(state.position.axisDirection, AxisDirection.right);
    expect(state.position.pixels, state.position.minScrollExtent);
    expect(state.position.maxScrollExtent, greaterThan(0));
    await tester.tap(find.byTooltip('Full screen'));
    await tester.pumpAndSettle();
    final readerState = tester.state<ScrollableState>(horizontal);
    expect(readerState.position.axisDirection, AxisDirection.right);
    expect(readerState.position.pixels, readerState.position.minScrollExtent);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('open reader retires controls when source becomes inert', (
    tester,
  ) async {
    final enabled = ValueNotifier(true);
    addTearDown(enabled.dispose);
    await _pump(
      tester,
      ValueListenableBuilder<bool>(
        valueListenable: enabled,
        builder: (_, value, _) => MarkdownInteractionScope(
          enabled: value,
          child: const CodeBlock(code: 'snapshot'),
        ),
      ),
    );
    await tester.tap(find.byTooltip('Full screen'));
    await tester.pumpAndSettle();
    enabled.value = false;
    await tester.pumpAndSettle();
    expect(find.byTooltip('Copy code'), findsNothing);
    expect(find.byTooltip('Wrap lines'), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pageBack();
    await tester.pumpAndSettle();
  });

  testWidgets('reader stays local and retires after source row is removed', (
    tester,
  ) async {
    final visible = ValueNotifier(true);
    addTearDown(visible.dispose);
    await _pump(
      tester,
      ValueListenableBuilder<bool>(
        valueListenable: visible,
        builder: (_, value, _) =>
            value ? const CodeBlock(code: 'snapshot') : const SizedBox(),
      ),
    );
    await tester.tap(find.byTooltip('Full screen'));
    await tester.pumpAndSettle();
    visible.value = false;
    await tester.pumpAndSettle();
    expect(find.byTooltip('Copy code'), findsNothing);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  final fences = <(String, String, bool)>[
    (
      '````markdown\n```dart\nclass A {}\n```\n````',
      '```dart\nclass A {}\n```',
      true,
    ),
    ('~~~dart\nclass A {}\n~~~~', 'class A {}', true),
    (
      '````dart\nfirst\n```\n~~~\n```` extra\nlast\n````',
      'first\n```\n~~~\n```` extra\nlast',
      true,
    ),
    ('~~~dart\nclass A {}\n```', 'class A {}\n```', false),
    ('   ~~~dart\n   one\n two\n   ~~~', 'one\ntwo', true),
    ('```dart\nclass A {', 'class A {', false),
    ('~~~text | hint\n--- | ---\nvalue\n~~~', '--- | ---\nvalue', true),
  ];
  for (var i = 0; i < fences.length; i++) {
    testWidgets('fence delimiter case $i preserves body and streaming state', (
      tester,
    ) async {
      final (input, expected, closed) = fences[i];
      await _pump(tester, MarkdownText(input));
      final block = tester.widget<CodeBlock>(find.byType(CodeBlock));
      expect(block.code, expected);
      expect(block.highlightEnabled, closed);
      if (!closed) {
        final span = tester
            .widget<SelectableText>(find.byType(SelectableText))
            .textSpan!;
        expect(span.children, isNull);
      }
    });
  }

  testWidgets('invalid backtick info and four-space opening are not fences', (
    tester,
  ) async {
    await _pump(
      tester,
      const MarkdownText('```bad`info\ntext\n\n    ~~~dart\ncode'),
    );
    expect(find.byType(CodeBlock), findsNothing);
  });

  testWidgets(
    'Copy preserves original CRLF, indent and trailing newline after wrap',
    (tester) async {
      final copies = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copies.add((call.arguments as Map)['text'] as String);
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await _pump(
        tester,
        const MarkdownText('  ~~~dart\r\n  final a = 1;  \r\n  \r\n  ~~~'),
      );
      await tester.tap(find.byTooltip('Wrap lines'));
      await tester.pump();
      await tester.tap(find.byTooltip('Copy code'));
      await tester.pump();
      expect(copies, ['  final a = 1;  \r\n  \r\n']);
    },
  );

  testWidgets(
    'clipboard failure is recoverable and retries the exact snapshot',
    (tester) async {
      var fail = true;
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            if (fail) throw PlatformException(code: 'denied');
            copied.add((call.arguments as Map)['text'] as String);
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      final source = '${'x' * 1200}\n\n';
      await _pump(tester, CodeBlock(code: source));
      await tester.tap(find.byTooltip('Copy code'));
      await tester.pumpAndSettle();
      expect(find.text('Could not copy code. Try again.'), findsOneWidget);
      expect(find.text('Code copied'), findsNothing);
      fail = false;
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();
      expect(copied, [source]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('inert code has selection and actions disabled', (tester) async {
    await _pump(
      tester,
      const MarkdownInteractionScope(
        enabled: false,
        child: CodeBlock(code: 'local only'),
      ),
    );
    expect(find.byTooltip('Copy code'), findsNothing);
    expect(find.byTooltip('Full screen'), findsNothing);
    expect(find.byTooltip('Wrap lines'), findsNothing);
    final ignored = find.ancestor(
      of: find.byType(SelectableText),
      matching: find.byType(IgnorePointer),
    );
    expect(
      tester.widgetList<IgnorePointer>(ignored).any((w) => w.ignoring),
      isTrue,
    );
  });

  testWidgets(
    'table aligns center/right and grows inline-code headers and long cells at 2x',
    (tester) async {
      tester.view.physicalSize = const Size(320, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await _pump(
        tester,
        const MarkdownText(
          '| Long `inline_header_with_a_long_name` | Center | Right |\n| --- | :---: | ---: |\n| A readable cell with several lines of detail | middle | 123 |',
        ),
        scale: 2,
      );
      final data = tester.widget<DataTable>(find.byType(DataTable));
      expect(data.dataRowMaxHeight, double.infinity);
      final center = tester.widget<Text>(
        find.byWidgetPredicate(
          (w) => w is Text && w.textSpan?.toPlainText() == 'middle',
        ),
      );
      expect(center.textAlign, TextAlign.center);
      final right = tester.widget<Text>(
        find.byWidgetPredicate(
          (w) => w is Text && w.textSpan?.toPlainText() == '123',
        ),
      );
      expect(right.textAlign, TextAlign.right);
      expect(tester.getSize(find.byType(DataTable)).height, greaterThan(180));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'inline code and validated path chips grow once at large text scale',
    (tester) async {
      Widget content() => MarkdownFileLinks(
        validate: (_) async => true,
        open: (_) {},
        child: const MarkdownText('Read `count` and `lib/a.dart`.'),
      );
      double paintedWidth(String value) {
        final box = tester.renderObject<RenderBox>(
          find.byWidgetPredicate(
            (w) => w is Text && w.textSpan?.toPlainText() == value,
          ),
        );
        return (box.localToGlobal(Offset(box.size.width, 0)) -
                box.localToGlobal(Offset.zero))
            .dx;
      }

      await _pump(tester, content());
      await tester.pumpAndSettle();
      final codeWidth = paintedWidth('count');
      final pathWidth = paintedWidth('lib/a.dart\uFFFC');
      await _pump(tester, content(), scale: 2);
      await tester.pumpAndSettle();
      expect(paintedWidth('count'), closeTo(codeWidth * 2, 0.1));
      expect(paintedWidth('lib/a.dart\uFFFC'), closeTo(pathWidth * 2, 0.1));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'table escaped pipes retain literal cells and mismatched delimiters are prose',
    (tester) async {
      await _pump(
        tester,
        const MarkdownText(
          r'| A\|B | C |'
          '\n| --- | --- |\n'
          r'| x\|y | z |',
        ),
      );
      expect(find.byType(DataTable), findsOneWidget);
      expect(
        find.byWidgetPredicate(
          (w) => w is Text && w.textSpan?.toPlainText() == 'x|y',
        ),
        findsOneWidget,
      );
      await _pump(tester, const MarkdownText('| A | B |\n| --- |\n| C | D |'));
      expect(find.byType(DataTable), findsNothing);
    },
  );

  testWidgets(
    'unchanged parse cache and leading code widget survive later streaming',
    (tester) async {
      final source = ValueNotifier(
        '```dart\nclass A {}\n```\n\n~~~dart\nclass B',
      );
      addTearDown(source.dispose);
      final parent = ValueNotifier(0);
      addTearDown(parent.dispose);
      await _pump(
        tester,
        ValueListenableBuilder<int>(
          valueListenable: parent,
          builder: (_, value, _) => ValueListenableBuilder<String>(
            valueListenable: source,
            builder: (_, text, _) => MarkdownText(text),
          ),
        ),
      );
      final before = MarkdownText.debugParseCount;
      final first = tester.widgetList<CodeBlock>(find.byType(CodeBlock)).first;
      parent.value++;
      await tester.pump();
      expect(MarkdownText.debugParseCount, before);
      source.value += ' {\n  int n = 0;';
      await tester.pump();
      expect(
        identical(
          tester.widgetList<CodeBlock>(find.byType(CodeBlock)).first,
          first,
        ),
        isTrue,
      );
      expect(
        tester
            .widgetList<CodeBlock>(find.byType(CodeBlock))
            .last
            .highlightEnabled,
        isFalse,
      );
    },
  );

  testWidgets(
    'reader retains snapshot and selection across parent streaming then returns',
    (tester) async {
      final source = ValueNotifier('~~~dart\nfinal first = 1;');
      addTearDown(source.dispose);
      await _pump(
        tester,
        ValueListenableBuilder<String>(
          valueListenable: source,
          builder: (_, text, _) => MarkdownText(text),
        ),
      );
      await tester.tap(find.byTooltip('Full screen'));
      await tester.pumpAndSettle();
      expect(find.text('Code reader'), findsOneWidget);
      expect(find.textContaining('Snapshot of the code'), findsOneWidget);
      final editable = tester.widget<EditableText>(find.byType(EditableText));
      editable.controller.selection = const TextSelection(
        baseOffset: 0,
        extentOffset: 5,
      );
      source.value += '\nfinal later = 2;\n~~~';
      await tester.pump();
      expect(
        tester.widget<CodeBlock>(find.byType(CodeBlock)).code,
        'final first = 1;',
      );
      expect(
        editable.controller.selection,
        const TextSelection(baseOffset: 0, extentOffset: 5),
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(
        tester.widget<CodeBlock>(find.byType(CodeBlock)).code,
        contains('final later'),
      );
    },
  );

  testWidgets(
    'wrap and reader preserve parent scroll offset and 48dp controls',
    (tester) async {
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              controller: scroll,
              child: Column(
                children: [
                  const SizedBox(height: 150),
                  CodeBlock(code: 'long ${'value ' * 80}'),
                  const SizedBox(height: 900),
                ],
              ),
            ),
          ),
        ),
      );
      scroll.jumpTo(100);
      await tester.pump();
      final before = scroll.offset;
      for (final label in ['Wrap lines', 'Full screen', 'Copy code']) {
        expect(
          tester.getSize(find.byTooltip(label)).height,
          greaterThanOrEqualTo(48),
        );
      }
      await tester.tap(find.byTooltip('Wrap lines'));
      await tester.pump();
      expect(scroll.offset, before);
      await tester.tap(find.byTooltip('Full screen'));
      await tester.pumpAndSettle();
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(scroll.offset, before);
    },
  );
}
