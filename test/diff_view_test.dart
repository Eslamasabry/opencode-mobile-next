import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/rendering.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/widgets/diff_view.dart';

Future<void> _pump(WidgetTester tester, List<FileDiff> diffs) async {
  await tester.pumpWidget(
    MaterialApp(
      home: RepaintBoundary(
        key: const Key('review-capture'),
        child: DiffView(diffs: diffs),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('patch view shows a sticky file header, gutter numbers and '
      'a count-only gap between hunks', (tester) async {
    await _pump(tester, [
      FileDiff(
        file: 'lib/ui/widgets/markdown.dart',
        additions: 2,
        deletions: 1,
        patch:
            '--- a/lib/ui/widgets/markdown.dart\n'
            '+++ b/lib/ui/widgets/markdown.dart\n'
            '@@ -10,3 +10,4 @@\n'
            ' context ten\n'
            '-old eleven\n'
            '+new eleven\n'
            '+new twelve\n'
            ' context twelve\n'
            '@@ -60,2 +61,2 @@\n'
            ' context sixty\n'
            ' context sixty-one\n',
      ),
    ]);

    expect(find.byKey(const Key('diff-view')), findsOneWidget);
    expect(find.text('markdown.dart'), findsOneWidget);
    expect(find.text('Review'), findsOneWidget);
    expect(find.textContaining('lib/ui/widgets/'), findsOneWidget);
    expect(find.text('+2'), findsOneWidget);
    expect(find.text('−1'), findsOneWidget);
    // Removed lines number by the old file, added by the new file.
    expect(find.text('11'), findsNWidgets(2));
    expect(find.text('12'), findsOneWidget);
    expect(find.text('old eleven'), findsOneWidget);
    expect(find.text('new twelve'), findsOneWidget);
    // The patch does not carry the skipped lines, so the gap only reports.
    expect(
      find.text('47 unchanged lines not included in patch'),
      findsOneWidget,
    );
    expect(find.text('Expand'), findsNothing);
  });

  testWidgets('before/after pairs collapse context and expand 20 lines per '
      'tap', (tester) async {
    final before = List.generate(60, (i) => 'line ${i + 1}');
    final after = [...before]..[30] = 'changed line 31';
    await _pump(tester, [
      FileDiff(
        file: 'notes.txt',
        before: before.join('\n'),
        after: after.join('\n'),
      ),
    ]);

    expect(find.text('changed line 31'), findsOneWidget);
    expect(find.text('line 31'), findsOneWidget);
    // Three lines of context stay on each side; the rest folds.
    expect(find.text('line 30'), findsOneWidget);
    expect(find.text('line 27'), findsNothing);
    expect(find.text('Show 20 previous lines (27 hidden)'), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const Key('diff-gap-0'))).height,
      greaterThanOrEqualTo(48),
    );

    await tester.tap(find.byKey(const Key('diff-gap-0')));
    await tester.pumpAndSettle();
    // Chevron-up reveals the 20 lines just above the hunk.
    expect(find.text('Show 7 previous lines (7 hidden)'), findsOneWidget);
    expect(find.text('line 8'), findsOneWidget);
    expect(find.text('line 7'), findsNothing);
    await tester.tap(find.text('Hide revealed context').first);
    await tester.pumpAndSettle();
    expect(find.text('line 8'), findsNothing);
    expect(find.text('Show 20 previous lines (27 hidden)'), findsOneWidget);
    expect(find.text('changed line 31'), findsOneWidget);
  });

  testWidgets('code wraps below 600dp and scrolls sideways above it', (
    tester,
  ) async {
    final diff = FileDiff(
      file: 'a.dart',
      before: 'x',
      after: 'y ${'long ' * 80}',
    );
    await tester.binding.setSurfaceSize(const Size(400, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await _pump(tester, [diff]);
    expect(find.byKey(const Key('diff-view-horizontal')), findsNothing);

    await tester.binding.setSurfaceSize(const Size(900, 800));
    await _pump(tester, [diff]);
    expect(find.byKey(const Key('diff-view-horizontal')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('change identity is visible and spoken without color', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();

    await _pump(tester, [
      FileDiff(file: 'a.dart', before: 'old value', after: 'new value'),
    ]);
    expect(find.text('+'), findsOneWidget);
    expect(find.text('−'), findsOneWidget);
    expect(find.bySemanticsLabel('Added, line 1: new value'), findsOneWidget);
    expect(find.bySemanticsLabel('Removed, line 1: old value'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('large text keeps header and wrapped changes readable', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    const file = 'lib/deep/project/long_readable_file_name.dart';
    final longLine = 'return ${'aLongExpression + ' * 8}';
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(2.5)),
          child: child!,
        ),
        home: DiffView(
          diffs: [FileDiff(file: file, before: 'old', after: longLine)],
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byKey(const Key('diff-file-header-$file'))).height,
      greaterThan(44),
    );
    expect(find.byTooltip(file), findsOneWidget);
    final changed = find
        .ancestor(of: find.text(longLine), matching: find.byType(Container))
        .first;
    final decoration =
        tester.widget<Container>(changed).decoration! as BoxDecoration;
    expect((decoration.border! as Border).left.width, 3);
    expect(tester.getSize(changed).height, greaterThan(60));
    expect(find.text('+'), findsOneWidget);
  });

  testWidgets('copy confirms the copied object and close returns to caller', (
    tester,
  ) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String;
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
              onPressed: () => DiffView.open(context, [
                FileDiff(file: 'a.dart', before: 'old', after: 'new'),
              ]),
              child: const Text('Review changes'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Review changes'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Copy updated file'));
    await tester.pumpAndSettle();
    expect(copied, 'new');
    expect(find.text('Updated file copied'), findsOneWidget);
    await tester.tap(find.byType(CloseButton));
    await tester.pumpAndSettle();
    expect(find.text('Review changes'), findsOneWidget);
    expect(find.byType(DiffView), findsNothing);
  });
  testWidgets('review capture at normal and large text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final captureDir = Platform.environment['REVIEW_CAPTURE_DIR'];
    if (captureDir != null) {
      await tester.runAsync(() async {
        for (final entry in {
          'AppMono': 'assets/fonts/JetBrainsMono-Regular.ttf',
          'AppDisplay': 'assets/fonts/SpaceGrotesk-Regular.ttf',
        }.entries) {
          await (FontLoader(
            entry.key,
          )..addFont(rootBundle.load(entry.value))).load();
        }
        final sdk = Platform.environment['REVIEW_FLUTTER_SDK'];
        if (sdk != null) {
          for (final entry in {
            'Roboto': 'Roboto-Regular.ttf',
            'MaterialIcons': 'MaterialIcons-Regular.otf',
          }.entries) {
            final bytes = await File(
              '$sdk/bin/cache/artifacts/material_fonts/${entry.value}',
            ).readAsBytes();
            await (FontLoader(
              entry.key,
            )..addFont(Future.value(ByteData.sublistView(bytes)))).load();
          }
        }
      });
    }
    for (final scale in [1.0, 2.5]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: RepaintBoundary(
            key: const Key('review-capture'),
            child: DiffView(
              diffs: [
                FileDiff(
                  file: 'lib/ui/welcome_message.dart',
                  patch:
                      '@@ -1,2 +1,2 @@\n-const greeting = "Hello";\n+const greeting = "Welcome aboard. Your workspace is ready.";\n return greeting;',
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('welcome_message.dart'), findsOneWidget);
      expect(find.text('Review'), findsOneWidget);
      expect(find.text('+1'), findsOneWidget);
      expect(find.text('−1'), findsOneWidget);
      if (captureDir != null) {
        final boundary = tester.renderObject<RenderRepaintBoundary>(
          find.byKey(const Key('review-capture')),
        );
        await tester.runAsync(() async {
          final image = await boundary.toImage(pixelRatio: 2.625);
          final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
          final output = File(
            '$captureDir/review-${scale == 1 ? "normal" : "large"}.png',
          );
          await output.parent.create(recursive: true);
          await output.writeAsBytes(bytes!.buffer.asUint8List());
          image.dispose();
        });
      }
    }
  });
}
