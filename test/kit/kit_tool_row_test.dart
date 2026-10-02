// KitToolRow (docs/ux-system/kit-api/KitToolRow.md): the frozen "Tests
// required" contract, the reduced-motion sample (G8x) and the keyboard
// behaviour (G14). Arabic copy checks are not run: owner decision 2026-09-27
// dropped Arabic (no Arabic copy for new keys); layout under RTL is still
// checked for overflow.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_task_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_motion_parts.dart';

import 'kit_motion_still.dart';

const _rowKey = ValueKey('embedded-tool-row');

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  bool reduceMotion = true,
  Size size = const Size(412, 915),
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
      builder: (context, widget) {
        Widget body = MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            disableAnimations: reduceMotion,
          ),
          child: widget!,
        );
        if (direction != null) {
          body = Directionality(textDirection: direction, child: body);
        }
        return body;
      },
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
}

ThemeRoles _roles(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(KitToolRow).first)).roles;

Finder _richText(String text) => find.byWidgetPredicate(
  (w) => w is RichText && w.text.toPlainText() == text,
);

Color? _colorOf(WidgetTester tester, String text) {
  final rich = tester.widget<RichText>(_richText(text).first);
  return rich.text.style?.color;
}

Iterable<Color> _paintedColors(WidgetTester tester, Finder scope) sync* {
  for (final rich in tester.widgetList<RichText>(
    find.descendant(of: scope, matching: find.byType(RichText)),
  )) {
    final colors = <Color>[];
    rich.text.visitChildren((span) {
      final color = span.style?.color;
      if (color != null) colors.add(color);
      return true;
    });
    yield* colors;
  }
  for (final icon in tester.widgetList<Icon>(
    find.descendant(of: scope, matching: find.byType(Icon)),
  )) {
    final color = icon.color;
    if (color != null) yield color;
  }
}

KitToolRow _row({
  KitToolStatus status = KitToolStatus.done,
  String title = 'Read main.dart',
  String? path,
  String? detail,
  int? added,
  int? removed,
  Duration? duration,
  List<Widget> body = const [],
  bool? expanded,
  ValueChanged<bool>? onExpansionChanged,
}) => KitToolRow(
  rowKey: _rowKey,
  kind: KitToolKind.read,
  title: title,
  status: status,
  path: path,
  detail: detail,
  added: added,
  removed: removed,
  duration: duration,
  body: body,
  expanded: expanded,
  onExpansionChanged: onExpansionChanged,
);

List<Widget> _body() => const [
  SizedBox(key: ValueKey('body-0'), height: 24, child: Text('first output')),
  SizedBox(key: ValueKey('body-1'), height: 24, child: Text('second output')),
];

void main() {
  kitMotionStillTests(
    'KitToolRow',
    builds: {
      'running': () => _row(status: KitToolStatus.running),
      'failed': () => _row(status: KitToolStatus.failed, body: _body()),
    },
    changes: {
      'output opens': KitMotionChange(
        build: () => _row(body: _body(), expanded: false),
        act: (tester, stage) =>
            stage.rebuild(_row(body: _body(), expanded: true)),
        shows: 'second output',
      ),
      'output closes': KitMotionChange(
        build: () => _row(body: _body(), expanded: true),
        act: (tester, stage) =>
            stage.rebuild(_row(body: _body(), expanded: false)),
        hides: 'second output',
      ),
    },
  );

  group('failed step retry (10A)', () {
    testWidgets('a failed step offers a neutral Retry that fires', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitToolRow(
          kind: KitToolKind.shell,
          title: 'Run flutter test',
          status: KitToolStatus.failed,
          onRetry: () => taps++,
        ),
      );
      expect(find.text('Retry'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-tool-retry')));
      expect(taps, 1);
    });

    testWidgets('no Retry while the step is not failed', (tester) async {
      await _pump(
        tester,
        KitToolRow(
          kind: KitToolKind.shell,
          title: 'Run flutter test',
          status: KitToolStatus.done,
          onRetry: () {},
        ),
      );
      expect(find.text('Retry'), findsNothing);
    });
  });

  group('status marks and words', () {
    final expectations = <KitToolStatus, (String?, Type?)>{
      KitToolStatus.notRun: ('Not run', null),
      KitToolStatus.pending: ('Waiting', KitStatusMark),
      KitToolStatus.running: (null, KitStatusMark),
      KitToolStatus.waitingForYou: ('Waiting for you', KitStatusMark),
      KitToolStatus.done: (null, null),
      KitToolStatus.failed: ('Failed', KitStatusMark),
      KitToolStatus.stopped: ('Stopped', KitTaskMark),
      KitToolStatus.background: ('Started in the background', KitStatusMark),
    };
    for (final entry in expectations.entries) {
      testWidgets('${entry.key.name} shows its mark and word', (tester) async {
        await _pump(tester, _row(status: entry.key));
        final (word, mark) = entry.value;
        if (word != null) expect(_richText(word), findsOneWidget);
        if (mark != null) {
          expect(find.byType(mark), findsOneWidget);
        } else {
          expect(find.byType(KitStatusMark), findsNothing);
          expect(find.byType(KitTaskMark), findsNothing);
        }
      });
    }

    testWidgets('marks carry the right state', (tester) async {
      await _pump(tester, _row(status: KitToolStatus.running));
      expect(
        tester.widget<KitStatusMark>(find.byType(KitStatusMark)).state,
        KitMarkState.working,
      );
      await _pump(tester, _row(status: KitToolStatus.waitingForYou));
      expect(
        tester.widget<KitStatusMark>(find.byType(KitStatusMark)).state,
        KitMarkState.waiting,
      );
      await _pump(tester, _row(status: KitToolStatus.failed));
      expect(
        tester.widget<KitStatusMark>(find.byType(KitStatusMark)).state,
        KitMarkState.failed,
      );
      await _pump(tester, _row(status: KitToolStatus.stopped));
      expect(
        tester.widget<KitTaskMark>(find.byType(KitTaskMark)).state,
        KitTaskState.stopped,
      );
    });

    testWidgets('waitingForYou never shows a progress indicator', (
      tester,
    ) async {
      await _pump(
        tester,
        _row(status: KitToolStatus.waitingForYou),
        reduceMotion: false,
      );
      expect(
        find.descendant(
          of: find.byType(KitToolRow),
          matching: find.byWidgetPredicate((w) => w is ProgressIndicator),
        ),
        findsNothing,
      );
    });

    testWidgets('failed paints no danger role and its word in text1', (
      tester,
    ) async {
      await _pump(tester, _row(status: KitToolStatus.failed));
      final roles = _roles(tester);
      expect(
        _paintedColors(tester, find.byType(KitToolRow)),
        isNot(contains(roles.danger)),
      );
      expect(_colorOf(tester, 'Failed'), roles.text1);
    });
  });

  testWidgets('notRun title is text3 and reads Not run', (tester) async {
    await _pump(tester, _row(status: KitToolStatus.notRun));
    final roles = _roles(tester);
    expect(_colorOf(tester, 'Read main.dart'), roles.text3);
    expect(_richText('Not run'), findsOneWidget);
  });

  testWidgets('done shows the duration words and no mark', (tester) async {
    await _pump(
      tester,
      _row(duration: const Duration(seconds: 3), path: 'lib/main.dart'),
    );
    expect(_richText('3 seconds'), findsOneWidget);
    expect(find.byType(KitStatusMark), findsNothing);
    expect(
      find.bySemanticsLabel('Read main.dart, lib/main.dart, Done, 3 seconds'),
      findsOneWidget,
    );
  });

  group('path', () {
    const longPath =
        'lib/ui/kit/chat/some/really/deep/folder/structure/row.dart';

    testWidgets('mono, LTR under RTL, middle-cut at 320 dp, full in '
        'semantics', (tester) async {
      await _pump(
        tester,
        _row(path: longPath),
        size: const Size(320, 800),
        direction: TextDirection.rtl,
      );
      final cut = find.byWidgetPredicate(
        (w) =>
            w is RichText &&
            w.text.toPlainText().contains('…') &&
            w.text.toPlainText().startsWith('l') &&
            w.text.toPlainText().endsWith('.dart'),
      );
      expect(cut, findsOneWidget);
      expect(tester.widget<RichText>(cut).textDirection, TextDirection.ltr);
      final style = tester.widget<RichText>(cut).text.style;
      expect(style?.fontFamily, isNotNull);
      expect(style!.fontFamily!.toLowerCase(), contains('mono'));
      expect(
        find.bySemanticsLabel(RegExp(RegExp.escape(longPath))),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('+4 −1 shows signs and reads as words', (tester) async {
    await _pump(
      tester,
      _row(
        title: 'Edited main.dart',
        path: 'lib/main.dart',
        added: 4,
        removed: 1,
      ),
    );
    expect(_richText('+4 −1'), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('4 added, 1 removed')), findsOneWidget);
  });

  group('opening', () {
    testWidgets('a row without note or body is not a button', (tester) async {
      await _pump(tester, _row(path: 'lib/main.dart'));
      final node = tester.getSemantics(
        find.bySemanticsLabel(RegExp('^Read main.dart')),
      );
      expect(node.flagsCollection.isButton, isFalse);
      expect(find.byType(KitSpin), findsNothing);
    });

    testWidgets('tap opens note first, then body in order, and closes', (
      tester,
    ) async {
      await _pump(
        tester,
        KitToolRow(
          rowKey: _rowKey,
          kind: KitToolKind.shell,
          title: 'Ran npm test',
          status: KitToolStatus.done,
          note: null,
          body: _body(),
        ),
      );
      expect(find.text('first output'), findsNothing);
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      final first = tester.getTopLeft(find.byKey(const ValueKey('body-0')));
      final second = tester.getTopLeft(find.byKey(const ValueKey('body-1')));
      expect(first.dy, lessThan(second.dy));
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      expect(find.text('first output'), findsNothing);
    });

    testWidgets('the note sits before the body', (tester) async {
      await _pump(
        tester,
        KitToolRow(
          rowKey: _rowKey,
          kind: KitToolKind.edit,
          title: 'Edited main.dart',
          status: KitToolStatus.done,
          note: const KitMarkdown('Because the test waited too little'),
          body: _body(),
          expanded: true,
        ),
      );
      final note = tester.getTopLeft(
        find
            .textContaining(
              'Because the test waited too little',
              findRichText: true,
            )
            .first,
      );
      final body = tester.getTopLeft(find.byKey(const ValueKey('body-0')));
      expect(note.dy, lessThan(body.dy));
    });

    testWidgets('controlled mode only reports', (tester) async {
      final requests = <bool>[];
      await _pump(
        tester,
        _row(body: _body(), expanded: false, onExpansionChanged: requests.add),
      );
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      expect(requests, [true]);
      expect(find.text('first output'), findsNothing);
    });

    testWidgets('opened body sits on the one gutter, with no indent, also '
        'inside a work line', (tester) async {
      double indentOf() =>
          tester.getTopLeft(find.byKey(const ValueKey('body-0'))).dx -
          tester.getTopLeft(find.byType(KitToolRow)).dx;

      await _pump(tester, _row(body: _body(), expanded: true));
      final alone = indentOf();
      expect(alone, closeTo(0, 0.01));

      await _pump(
        tester,
        KitWorkLine(
          counts: const KitWorkCounts(read: 1),
          state: KitWorkState.done,
          expanded: true,
          steps: [_row(body: _body(), expanded: true)],
        ),
      );
      expect(indentOf(), closeTo(alone, 0.01));
    });

    testWidgets('no size animation; reduced motion settles in one pump', (
      tester,
    ) async {
      await _pump(tester, _row(body: _body()));
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      expect(find.byType(AnimatedSize), findsNothing);
      expect(find.byType(SizeTransition), findsNothing);
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.text('first output'), findsOneWidget);
    });

    testWidgets('with motion, the body appears at once and fades in', (
      tester,
    ) async {
      await _pump(tester, _row(body: _body()), reduceMotion: false);
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      expect(find.text('first output'), findsOneWidget);
      await tester.pumpAndSettle();
      expect(find.byType(AnimatedSize), findsNothing);
    });
  });

  group('agent', () {
    testWidgets('done reads title · word, not a button without onOpen', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitToolRow.agent(
          rowKey: _rowKey,
          title: 'Delegated to explore',
          status: KitToolStatus.done,
          task: 'Find where checkout totals are computed',
        ),
      );
      expect(_richText('Delegated to explore · Done'), findsOneWidget);
      final node = tester.getSemantics(find.byKey(_rowKey));
      expect(node.flagsCollection.isButton, isFalse);
      expect(
        node.label,
        'Delegated to explore, Find where checkout totals are computed, Done',
      );
    });

    testWidgets('running ticks by the minute and opens once', (tester) async {
      var opened = 0;
      await _pump(
        tester,
        KitToolRow.agent(
          rowKey: _rowKey,
          title: 'furiosa · Worker',
          status: KitToolStatus.running,
          task: 'Fix the flaky checkout test',
          startedAt: clock.now().subtract(const Duration(minutes: 3)),
          onOpen: () => opened++,
        ),
      );
      expect(_richText('furiosa · Worker · Running for 3 min'), findsOneWidget);
      await tester.pump(const Duration(minutes: 1));
      expect(_richText('furiosa · Worker · Running for 4 min'), findsOneWidget);
      await tester.tap(find.byKey(_rowKey));
      await tester.pump();
      expect(opened, 1);
      expect(
        tester.getSemantics(find.byKey(_rowKey)),
        isSemantics(
          label:
              'furiosa · Worker, Fix the flaky checkout test, Running, '
              'for 4 min',
          hint: 'Open its conversation',
          isButton: true,
        ),
      );
    });
  });

  testWidgets('semantics: expanded button, 48 dp, no live region', (
    tester,
  ) async {
    await _pump(
      tester,
      _row(path: 'lib/main.dart', body: _body(), status: KitToolStatus.done),
    );
    expect(
      tester.getSemantics(find.byKey(_rowKey)),
      isSemantics(
        label: 'Read main.dart, lib/main.dart, Done',
        isButton: true,
        hasExpandedState: true,
        isExpanded: false,
      ),
    );
    final size = tester.getSize(find.byKey(_rowKey));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(size.width, greaterThanOrEqualTo(48));
    expect(
      find.byWidgetPredicate(
        (w) => w is Semantics && (w.properties.liveRegion ?? false),
      ),
      findsNothing,
    );
  });

  testWidgets('desktop: Tab reaches the row, Enter opens it, ring and path '
      'tooltip', (tester) async {
    await _pump(tester, _row(path: 'lib/main.dart', body: _body()));
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    final focused = FocusManager.instance.primaryFocus;
    expect(focused, isNotNull);
    expect(
      find.descendant(
        of: find.byType(KitToolRow),
        matching: find.byType(PositionedDirectional),
      ),
      findsOneWidget,
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(find.text('first output'), findsOneWidget);
    expect(
      tester
          .widgetList<Tooltip>(find.byType(Tooltip))
          .map((t) => t.message)
          .where((m) => m != null && m.contains('lib/main.dart')),
      isNotEmpty,
    );
  }, variant: TargetPlatformVariant.only(TargetPlatform.linux));

  // P3.5: an AI Team step opens its Work sheet: a step row may open
  // elsewhere instead of folding, with a forward chevron and the hint.
  testWidgets('a step with onOpen is one tap that opens it', (tester) async {
    var opened = 0;
    await _pump(
      tester,
      KitToolRow(
        rowKey: _rowKey,
        kind: KitToolKind.todo,
        title: 'Remember the choice',
        status: KitToolStatus.pending,
        onOpen: () => opened++,
      ),
    );
    await tester.tap(find.byKey(_rowKey));
    await tester.pumpAndSettle();
    expect(opened, 1);
    final semantics = tester.getSemantics(find.byKey(_rowKey));
    expect(semantics.hint, 'Open its details');
  });

  testWidgets('a running agent reads its time in hours and days', (
    tester,
  ) async {
    await withClock(Clock.fixed(DateTime.utc(2026, 9, 25, 19, 55)), () async {
      await _pump(
        tester,
        KitToolRow.agent(
          title: 'furiosa · Worker',
          status: KitToolStatus.running,
          startedAt: DateTime.utc(2026, 9, 23, 22, 55),
        ),
      );
      expect(find.textContaining('Running for 1 d 21 h'), findsOneWidget);
      expect(find.textContaining('min'), findsNothing);
    });
  });

  group('200 % text at 320 dp', () {
    for (final direction in TextDirection.values) {
      for (final status in KitToolStatus.values) {
        testWidgets('${status.name} · ${direction.name}', (tester) async {
          await _pump(
            tester,
            Column(
              children: [
                _row(
                  status: status,
                  title: 'Searched the code for checkout totals',
                  path: 'lib/features/checkout/checkout_bloc.dart',
                  added: 12,
                  removed: 3,
                  duration: const Duration(seconds: 42),
                  body: _body(),
                ),
                KitToolRow.agent(
                  title: 'Delegated to explore',
                  status: status,
                  task: 'Find where checkout totals are computed and why',
                  onOpen: () {},
                ),
              ],
            ),
            textScale: 2,
            size: const Size(320, 800),
            direction: direction,
          );
          expect(tester.takeException(), isNull);
        });
      }
    }
  });
}
