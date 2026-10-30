// KitDiffView on a Markdown file (owner request 2026-10-27): the changes are
// drawn as Markdown with their + / − and tint kept, "Show source" brings back
// the written lines, other files are unchanged, and copying selected lines
// copies what is written.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';

import 'kit_motion_still.dart';

Widget _app(Widget child, {bool reduced = false}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, widget) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
    child: widget!,
  ),
  home: Scaffold(body: child),
);

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view
    ..physicalSize = const Size(412, 915)
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(child));
  await tester.pumpAndSettle();
}

const _before = '# IPTV King\n\nA player for TV.\n';

const _after =
    '# IPTV King\n'
    '\n'
    'A player for TV.\n'
    '## Player engine (ExoPlayer fork)\n'
    '\n'
    '- Android playback is our fork of the official `video_player_android`\n'
    '  (`packages/video_player_android`, BSD-3; `dependency_overrides` in\n'
    '  pubspec). Changes are marked **IPTV King:**.\n'
    '- See [the docs](https://example.com/docs).\n'
    '\n'
    '```dart\n'
    'final fork = true;\n'
    '```\n';

KitDiffFile _md([String path = 'CLAUDE.md']) =>
    KitDiffFile.fromTexts(path, before: _before, after: _after);

/// Whether a [marker] ('+' or '−') is drawn at the start of the row that
/// holds [finder], on its line.
bool _markerBeside(Finder finder, WidgetTester tester, String marker) {
  final row = tester.getRect(finder);
  return find.text(marker).evaluate().any((element) {
    final box = element.renderObject! as RenderBox;
    final center = box.localToGlobal(box.size.center(Offset.zero));
    return center.dx < row.left &&
        (center.dy - row.center.dy).abs() < row.height;
  });
}

Finder _heading() =>
    find.text('Player engine (ExoPlayer fork)', findRichText: true);

void main() {
  testWidgets('a .md diff shows a drawn heading with its + marker', (
    tester,
  ) async {
    await _pump(tester, KitDiffView(files: [_md()], inline: true));

    expect(_heading(), findsOneWidget);
    expect(_markerBeside(_heading(), tester, '+'), isTrue);
    // The marks are drawn, not written.
    expect(find.textContaining('## Player', findRichText: true), findsNothing);
    expect(find.textContaining('**', findRichText: true), findsNothing);
    // A hard-wrapped list item reads as one item.
    expect(
      find.textContaining('dependency_overrides', findRichText: true),
      findsOneWidget,
    );
    // Code fences stay code: written lines, numbered.
    expect(find.text('```dart', findRichText: true), findsOneWidget);
    // Unchanged lines stay neutral: the title is drawn without a marker.
    final title = find.text('IPTV King', findRichText: true);
    expect(title, findsOneWidget);
    expect(_markerBeside(title, tester, '+'), isFalse);
    // Each block still says what happened to it.
    expect(
      find.bySemanticsLabel(RegExp(r'^Line 4 added: Player engine')),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel(RegExp(r'^Lines 6–8 added: Android playback')),
      findsOneWidget,
    );
  });

  testWidgets('Show source shows the written lines, and back', (tester) async {
    await _pump(tester, KitDiffView(files: [_md()], inline: true));
    await tester.tap(find.text('Source'));
    await tester.pumpAndSettle();

    expect(
      find.text('## Player engine (ExoPlayer fork)', findRichText: true),
      findsOneWidget,
    );
    expect(_heading(), findsNothing);

    await tester.tap(find.text('Formatted'));
    await tester.pumpAndSettle();
    expect(_heading(), findsOneWidget);
  });

  testWidgets('a non-Markdown file is unchanged', (tester) async {
    final file = KitDiffFile.fromTexts(
      'lib/a.dart',
      before: 'void main() {}\n',
      after: 'void main() {}\n// ## not a heading\n',
    );
    await _pump(tester, KitDiffView(files: [file], inline: true));
    expect(find.text('Source'), findsNothing);
    expect(
      find.text('// ## not a heading', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('the preview counts written lines and cuts a block', (
    tester,
  ) async {
    await _pump(
      tester,
      KitDiffView(files: [_md()], inline: true, maxLines: 6, onOpenAll: () {}),
    );
    expect(find.text('Showing 6 of 13 lines'), findsOneWidget);
    expect(find.text('Open all'), findsOneWidget);
    expect(find.text('Source'), findsOneWidget);
    // Line 6 starts the list item; its wrapped lines 7–8 are past the cut.
    expect(
      find.textContaining('Android playback', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.textContaining('dependency_overrides', findRichText: true),
      findsNothing,
    );
  });

  testWidgets('Open all opens the page, drawn with its + marker', (
    tester,
  ) async {
    await _pump(
      tester,
      Builder(
        builder: (context) => KitDiffView(
          files: [_md()],
          inline: true,
          maxLines: 3,
          onOpenAll: () => showKitDiff(
            context,
            title: 'Changes in CLAUDE.md',
            files: [_md()],
          ),
        ),
      ),
    );
    expect(_heading(), findsNothing);
    await tester.tap(find.text('Open all'));
    await tester.pumpAndSettle();

    expect(find.text('Changes in CLAUDE.md'), findsOneWidget);
    expect(_heading(), findsOneWidget);
    expect(_markerBeside(_heading(), tester, '+'), isTrue);
    // The page's header carries the same switch.
    await tester.tap(find.text('Source'));
    await tester.pumpAndSettle();
    expect(
      find.text('## Player engine (ExoPlayer fork)', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('a removed block keeps its − marker', (tester) async {
    final file = KitDiffFile.fromTexts(
      'notes.markdown',
      before: '# Notes\n\n## Old part\n',
      after: '# Notes\n\n',
    );
    await _pump(tester, KitDiffView(files: [file], inline: true));
    final heading = find.text('Old part', findRichText: true);
    expect(heading, findsOneWidget);
    expect(_markerBeside(heading, tester, '−'), isTrue);
    expect(
      find.bySemanticsLabel(RegExp(r'^Line 3 removed: Old part')),
      findsOneWidget,
    );
  });

  testWidgets('a fence opened above added lines keeps them code', (
    tester,
  ) async {
    final file = KitDiffFile.fromTexts(
      'README.md',
      before: '```\n# a comment\n```\n',
      after: '```\n# a comment\n# another comment\n```\n',
    );
    await _pump(tester, KitDiffView(files: [file], inline: true));
    expect(find.text('# another comment', findRichText: true), findsOneWidget);
  });

  group('copy', () {
    final clipboard = <String>[];
    setUp(() {
      clipboard.clear();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, (call) async {
            if (call.method == 'Clipboard.setData') {
              clipboard.add((call.arguments as Map)['text'] as String);
            }
            return null;
          });
    });
    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, null);
    });

    testWidgets('selected blocks copy the written lines', (tester) async {
      await _pump(
        tester,
        KitDiffView(files: [_md()], readOnly: false, onComment: (_) {}),
      );
      await tester.tap(
        find.byKey(const ValueKey('kit-diff-line-0-current-4')),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Copy lines'));
      await tester.pumpAndSettle();
      expect(clipboard.last, '## Player engine (ExoPlayer fork)');

      // A wrapped list item selects and copies all of its lines.
      await tester.tap(
        find.byKey(const ValueKey('kit-diff-line-0-current-6')),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      expect(find.text('3 lines selected'), findsOneWidget);
      await tester.tap(find.text('Copy lines'));
      await tester.pumpAndSettle();
      expect(clipboard.last, _after.split('\n').sublist(5, 8).join('\n'));
    });
  });

  kitMotionStillTests(
    'KitDiffView',
    builds: {
      'rendered': () => KitDiffView(files: [_md()], inline: true),
    },
    changes: {
      'show source': KitMotionChange(
        build: () => KitDiffView(files: [_md()], inline: true),
        act: (tester, stage) => stage.press(
          find.ancestor(
            of: find.text('Source'),
            matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
          ),
        ),
        shows: '## Player engine (ExoPlayer fork)',
      ),
    },
  );
}
