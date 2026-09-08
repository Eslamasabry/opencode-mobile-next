import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/file_preview.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:flutter_svg/flutter_svg.dart';

void main() {
  Future<void> pump(WidgetTester tester, FilePreviewData data) =>
      tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: FilePreviewBody(data: data)),
        ),
      );

  test(
    'eligible byte-only text decodes strictly and export keeps original bytes',
    () {
      final bytes = Uint8List.fromList(utf8.encode('a,b\r\n1,2\r\n'));
      final data = FilePreviewData(name: 'data.csv', bytes: bytes);
      expect(data.text, 'a,b\r\n1,2\r\n');
      expect(data.exportBytes, same(bytes));
      expect(
        FilePreviewData(
          name: 'data.csv',
          bytes: Uint8List.fromList([0xff]),
        ).text,
        isNull,
      );
      expect(
        FilePreviewData(name: 'data.csv', bytes: Uint8List.fromList([0])).text,
        isNull,
      );
      expect(
        FilePreviewData(
          name: 'data.csv',
          mimeType: 'application/pdf',
          bytes: bytes,
        ).text,
        isNull,
      );
    },
  );
  test('invalid UTF-8 data URL keeps Save bytes without replacement text', () {
    final data = FilePreviewData.fromDataUrl(
      name: 'data.csv',
      mimeType: 'text/csv',
      url: 'data:text/csv;base64,/w==',
    );
    expect(data.text, isNull);
    expect(data.exportBytes, [255]);
    expect(data.error, isNull);
  });
  testWidgets('CSV cells are literal and Source copy preserves exact source', (
    tester,
  ) async {
    const source =
        'name,value,\r\n"a,b","=SUM(A1)",\r\n# Heading,<script>,\r\n';
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
    await pump(tester, FilePreviewData(name: 'report.csv', text: source));
    expect(find.text('Table'), findsOneWidget);
    expect(find.byType(MarkdownText), findsNothing);
    expect(find.text('a,b'), findsOneWidget);
    await tester.tap(find.text('Source'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Copy code'));
    await tester.pump();
    expect(copies, [source]);
    await tester.tap(find.text('Table'));
    await tester.pump();
    expect(find.text('a,b'), findsOneWidget);
  });
  testWidgets(
    'partial source refuses table inference and preserves full copy',
    (tester) async {
      final data = FilePreviewData(
        name: 'report.csv',
        text: 'a,b\n"partial',
        originalText: 'a,b\n"partial but complete",2\n',
        truncated: true,
      );
      await pump(tester, data);
      expect(find.textContaining('Only part of this file'), findsOneWidget);
      expect(find.text('Column 1'), findsNothing);
      expect(
        tester.widget<CodeBlock>(find.byType(CodeBlock)).originalSource,
        data.originalText,
      );
      expect(utf8.decode(data.exportBytes!), data.originalText);
    },
  );
  testWidgets(
    'focused partial source discloses its prefix and never highlights a different target',
    (tester) async {
      final data = FilePreviewData(
        name: 'large.dart',
        text: 'first\nsecond\npartial',
        originalText: 'first\nsecond\npartial complete\nfourth\nfifth',
        truncated: true,
      );
      Future<void> focused(int line) => tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: FilePreviewBody(data: data, initialLine: line),
          ),
        ),
      );
      await focused(2);
      expect(find.textContaining('Only part of this file'), findsOneWidget);
      expect(find.byKey(const Key('file-preview-target-line')), findsOneWidget);
      await focused(5);
      expect(find.textContaining('Only part of this file'), findsOneWidget);
      expect(
        find.textContaining('Line 5 is outside this preview'),
        findsOneWidget,
      );
      expect(find.byKey(const Key('file-preview-target-line')), findsNothing);
      expect(data.copyText, data.originalText);
    },
  );
  testWidgets('malformed and empty tables have useful fallbacks', (
    tester,
  ) async {
    await pump(tester, FilePreviewData(name: 'report.tsv', text: '"open'));
    expect(
      find.textContaining('incomplete or inconsistent quoting'),
      findsOneWidget,
    );
    expect(find.byType(CodeBlock), findsOneWidget);
    await pump(tester, FilePreviewData(name: 'report.tsv', text: ''));
    expect(find.text('This file has no rows.'), findsOneWidget);
  });
  testWidgets(
    'pretty JSON copies original spacing rather than projected formatting',
    (tester) async {
      const source = ' {"n":1} \r\n';
      await pump(tester, FilePreviewData(name: 'report.json', text: source));
      final code = tester.widget<CodeBlock>(find.byType(CodeBlock));
      expect(code.code, '{\n  "n": 1\n}');
      expect(code.originalSource, source);
    },
  );
  testWidgets(
    'static SVG renders locally and Source retains the exact document',
    (tester) async {
      const source =
          '<svg viewBox="0 0 100 50"><rect width="100" height="50" fill="#123456"/></svg>\r\n';
      await pump(tester, FilePreviewData(name: 'drawing.svg', text: source));
      await tester.pumpAndSettle();
      expect(find.byType(SvgPicture), findsOneWidget);
      await tester.tap(find.text('Source'));
      await tester.pumpAndSettle();
      expect(find.byType(SvgPicture), findsNothing);
      expect(
        tester.widget<CodeBlock>(find.byType(CodeBlock)).originalSource,
        source,
      );
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'active SVG stays in useful Source and inert preview disables switches',
    (tester) async {
      const source = '<svg><script>run()</script></svg>';
      await pump(tester, FilePreviewData(name: 'drawing.svg', text: source));
      expect(find.byType(SvgPicture), findsNothing);
      expect(
        tester.widget<CodeBlock>(find.byType(CodeBlock)).originalSource,
        source,
      );
      for (final data in [
        FilePreviewData(name: 'data.csv', text: 'a,b\n1,2'),
        FilePreviewData(
          name: 'drawing.svg',
          text: '<svg viewBox="0 0 10 10"><rect width="10" height="10"/></svg>',
        ),
      ]) {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: MarkdownInteractionScope(
                enabled: false,
                child: FilePreviewBody(data: data),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final sourceButton = tester.widget<TextButton>(
          find.widgetWithText(TextButton, 'Source'),
        );
        expect(sourceButton.onPressed, isNull);
      }
    },
  );
  testWidgets('file-sheet clipboard refusal offers an exact retry', (
    tester,
  ) async {
    var refuse = true;
    final copies = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          if (refuse) throw PlatformException(code: 'denied');
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
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showFilePreviewSheet(
                context,
                FilePreviewData(name: 'report.csv', text: 'a,b\r\n'),
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Copy file contents'));
    await tester.pumpAndSettle();
    expect(
      find.text('Could not copy file contents. Try again.'),
      findsOneWidget,
    );
    refuse = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(copies, ['a,b\r\n']);
  });
}
