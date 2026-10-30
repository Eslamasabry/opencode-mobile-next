// Behaviour tests for KitProgressRow (docs/ux-system/kit-api/KitProgressRow.md):
// the states, the automatic near/at-limit words, the tone assert, the
// merged semantics node, the tap target and reduced motion (STANDARDS.md
// TEST-15). Arabic and RTL galleries are dropped for the revamp (owner
// decision 2026-09-27; STANDARDS.md header), so this file stays English.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_progress_row.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import '../support/part_semantics.dart';

const _rowKey = ValueKey('row-under-test');

/// One instance for every pump: two separately built `AppTheme.dark()`
/// calls do not compare equal, so a second pump with a fresh one would
/// animate `MaterialApp`'s own `AnimatedTheme` between them — the harness
/// moving, not the part under test (kit_progress_test.dart's pattern).
final _theme = AppTheme.dark();

Future<void> _pumpRow(
  WidgetTester tester,
  Widget row, {
  bool reduced = false,
}) => tester.pumpWidget(
  MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: _theme,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
      child: child!,
    ),
    home: Scaffold(body: Center(child: row)),
  ),
);

/// The bar: the one painted box under the title exactly
/// [KitTokens.progressBarHeight] tall.
RenderBox _bar(WidgetTester tester) => tester
    .renderObjectList<RenderBox>(
      find.descendant(
        of: find.byKey(_rowKey),
        matching: find.byType(CustomPaint),
      ),
    )
    .singleWhere((box) => box.size.height == KitTokens.progressBarHeight);

/// Paints a rectangle in [color] (compared as 32-bit ARGB, as painted)
/// whose bounds pass [where].
PaintPattern _paintsRect(Color color, bool Function(Rect rect) where) =>
    paints..something((method, arguments) {
      if (method != #drawRect) return false;
      final paint = arguments[1] as Paint;
      return paint.color.toARGB32() == color.toARGB32() &&
          where(arguments[0] as Rect);
    });

SemanticsData _rowSemantics(WidgetTester tester, [Key key = _rowKey]) =>
    partSemantics(tester, find.byKey(key)).getSemanticsData();

void main() {
  kitMotionStillTests(
    'KitProgressRow',
    builds: {
      'loading': () => const KitProgressRow(title: 'Context', value: null),
      'measured': () => const KitProgressRow(
        title: 'Context',
        value: .6,
        valueLabel: '60 percent used',
      ),
    },
    changes: {
      'measurement arrives': KitMotionChange(
        build: () => const KitProgressRow(title: 'Context', value: null),
        act: (tester, stage) => stage.rebuild(
          const KitProgressRow(
            title: 'Context',
            value: .6,
            valueLabel: '60 percent used',
          ),
        ),
        shows: '60 percent used',
      ),
    },
  );

  group('KitProgressRow loading', () {
    testWidgets('a null value shows no percentage and says loading', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Claude · 5-hour window',
          value: null,
        ),
      );
      await tester.pump();
      expect(find.textContaining('%'), findsNothing);
      final data = _rowSemantics(tester);
      expect(data.label, 'Claude · 5-hour window, Loading');
      semantics.dispose();
    });
  });

  group('KitProgressRow loaded', () {
    testWidgets('renders the value label and carries it in semantics', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Claude · 5-hour window',
          value: 0.62,
          valueLabel: '62 % · resets in 3 h',
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('62 % · resets in 3 h'), findsOneWidget);
      final data = _rowSemantics(tester);
      expect(data.label, contains('62 percent'));
      expect(data.label, contains('62 % · resets in 3 h'));
      semantics.dispose();
    });
  });

  group('KitProgressRow automatic near/at limit (LOOK-4)', () {
    testWidgets('80 % shows Near limit, 100 % shows Limit reached, 79 % '
        'shows neither', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Provider quota',
          value: 0.80,
        ),
        reduced: true,
      );
      await tester.pump();
      expect(find.text('Near limit'), findsOneWidget);
      expect(find.text('Limit reached'), findsNothing);

      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Provider quota', value: 1.0),
        reduced: true,
      );
      await tester.pump();
      expect(find.text('Limit reached'), findsOneWidget);
      expect(find.text('Near limit'), findsNothing);

      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Provider quota',
          value: 0.79,
        ),
        reduced: true,
      );
      await tester.pump();
      expect(find.text('Near limit'), findsNothing);
      expect(find.text('Limit reached'), findsNothing);
    });

    // Owner polish 2026-09-28: a full limit is a failure. The bar and its
    // word take the danger role, never the text colour and never amber.
    testWidgets('100 % draws the full bar and "Limit reached" in danger', (
      tester,
    ) async {
      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Provider quota', value: 1.0),
        reduced: true,
      );
      await tester.pump();
      final roles = AppTheme.rolesOf(_theme);
      final bar = _bar(tester);
      final size = bar.size;
      expect(
        bar,
        _paintsRect(roles.text1, (rect) => rect == Offset.zero & size),
      );
      final word = tester.widgetList<RichText>(find.byType(RichText)).expand((
        text,
      ) {
        final spans = <TextSpan>[];
        text.text.visitChildren((span) {
          if (span is TextSpan && span.text == 'Limit reached') {
            spans.add(span);
          }
          return true;
        });
        return spans;
      }).single;
      expect(word.style?.color, roles.text1);
    });

    testWidgets('an explicit tone never adds the automatic words', (
      tester,
    ) async {
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Provider quota',
          value: 1.0,
          tone: AppStatusTone.failure,
          valueLabel: 'Failed',
        ),
        reduced: true,
      );
      await tester.pump();
      expect(find.text('Limit reached'), findsNothing);
      expect(find.text('Failed'), findsOneWidget);
    });
  });

  group('KitProgressRow tone (LOOK-4)', () {
    testWidgets('attention throws an AssertionError', (tester) async {
      expect(
        () => KitProgressRow(
          title: 'x',
          value: 0.5,
          tone: AppStatusTone.attention,
        ),
        throwsA(isA<AssertionError>()),
      );
    });
  });

  group('KitProgressRow stale (asOf)', () {
    testWidgets('renders "as of" in the person\'s 12/24-hour clock (F15)', (
      tester,
    ) async {
      final at = DateTime(2026, 9, 27, 10, 42);
      await _pumpRow(
        tester,
        KitProgressRow(
          key: _rowKey,
          title: 'Usage',
          value: 0.4,
          valueLabel: '40 % used',
          asOf: at,
        ),
        reduced: true,
      );
      await tester.pump();
      // The emulator QA (F15) saw "23:19" here beside "11:36 PM" in the
      // banner: the device's 12-hour setting now holds for both.
      const expected = '10:42 AM';
      expect(find.textContaining('as of $expected'), findsOneWidget);
      final data = _rowSemantics(tester);
      expect(data.label, contains('as of $expected'));
    });
  });

  group('KitProgressRow.segments', () {
    const segments = [
      KitProgressSegment(
        label: 'Conversation',
        value: 0.41,
        valueLabel: '41k tokens',
      ),
      KitProgressSegment(
        label: 'System prompt',
        value: 0.1,
        valueLabel: '10k tokens',
      ),
      KitProgressSegment(label: 'Tools', value: 0.05, valueLabel: '5k tokens'),
      KitProgressSegment(
        label: 'History',
        value: 0.05,
        valueLabel: '5k tokens',
      ),
      KitProgressSegment(label: 'Cache', value: 0.05, valueLabel: '5k tokens'),
    ];

    testWidgets('the legend lists every label with its value label, and a '
        'fifth segment folds into Other', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow.segments(
          key: _rowKey,
          title: 'Context used',
          segments: segments,
          valueLabel: '71 % of 200k tokens',
        ),
        reduced: true,
      );
      await tester.pump();
      expect(find.text('Conversation'), findsOneWidget);
      expect(find.text('41k tokens'), findsOneWidget);
      expect(find.text('System prompt'), findsOneWidget);
      expect(find.text('Tools'), findsOneWidget);
      expect(find.text('History'), findsOneWidget);
      // The fifth segment (Cache) folds into "Other"; its own name is gone.
      expect(find.text('Cache'), findsNothing);
      expect(find.text('Other'), findsOneWidget);
      final data = _rowSemantics(tester);
      expect(data.label, contains('Context used, 71 % of 200k tokens'));
      expect(data.label, contains('Conversation 41k tokens'));
      expect(data.label, contains('Other'));
    });

    testWidgets('a single full segment fills the whole bar, at the bar '
        'height, in the accent', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow.segments(
          key: _rowKey,
          title: 'Context used',
          segments: [KitProgressSegment(label: 'Conversation', value: 1)],
        ),
        reduced: true,
      );
      await tester.pump();
      final bar = _bar(tester);
      final size = bar.size;
      expect(size.height, KitTokens.progressBarHeight);
      expect(
        bar,
        _paintsRect(
          AppTheme.rolesOf(_theme).accent,
          (rect) => rect == Offset.zero & size,
        ),
      );
    });

    testWidgets('a surface3 slice marks its end against the track with a '
        'hairline, so the bar does not read short', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow.segments(
          key: _rowKey,
          title: 'Context used',
          segments: [
            KitProgressSegment(label: 'Conversation', value: 0.5),
            KitProgressSegment(label: 'System prompt', value: 0.1),
            KitProgressSegment(label: 'Tools', value: 0.1),
            KitProgressSegment(label: 'History', value: 0.1),
          ],
        ),
        reduced: true,
      );
      await tester.pump();
      final bar = _bar(tester);
      final end = bar.size.width * 0.8;
      final hairline = AppTheme.rolesOf(_theme).hairline;
      expect(
        bar,
        _paintsRect(
          hairline,
          (rect) =>
              (rect.center.dx - end).abs() <= 1 &&
              rect.height == KitTokens.progressBarHeight,
        ),
      );
    });

    testWidgets('segments summing over 1 assert in debug', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow.segments(
          key: _rowKey,
          title: 'Context used',
          segments: [
            KitProgressSegment(label: 'A', value: 0.7),
            KitProgressSegment(label: 'B', value: 0.5),
          ],
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });
  });

  group('KitProgressRow onTap and target', () {
    testWidgets('the whole row is one target and tapping calls it once', (
      tester,
    ) async {
      var taps = 0;
      await _pumpRow(
        tester,
        KitProgressRow(
          key: _rowKey,
          title: 'Provider quota',
          value: 0.4,
          valueLabel: '40 % used',
          onTap: () => taps++,
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      expect(
        tester.getSize(find.byKey(_rowKey)).height,
        greaterThanOrEqualTo(48),
      );
      await tester.tap(find.byKey(_rowKey));
      expect(taps, 1);
      final data = _rowSemantics(tester);
      expect(data.flagsCollection.isButton, isTrue);
      expect(data.hasAction(SemanticsAction.tap), isTrue);
    });

    testWidgets('without onTap there is no button semantics', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Provider quota',
          value: 0.4,
          valueLabel: '40 % used',
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      final data = _rowSemantics(tester);
      expect(data.flagsCollection.isButton, isFalse);
      expect(data.hasAction(SemanticsAction.tap), isFalse);
    });
  });

  group('KitProgressRow motion (G8)', () {
    testWidgets('a value change settles after one pump() under reduced '
        'motion', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Usage', value: 0.2),
        reduced: true,
      );
      await tester.pump();
      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Usage', value: 0.8),
        reduced: true,
      );
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('without reduced motion a value change keeps animating '
        'briefly', (tester) async {
      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Usage', value: 0.2),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await _pumpRow(
        tester,
        const KitProgressRow(key: _rowKey, title: 'Usage', value: 0.8),
      );
      await tester.pump(const Duration(milliseconds: 50));
      expect(tester.hasRunningAnimations, isTrue);
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitProgressRow adaptive', () {
    Future<void> pumpAt(WidgetTester tester, Size size) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await _pumpRow(
        tester,
        const KitProgressRow(
          key: _rowKey,
          title: 'Usage',
          value: 0.1,
          valueLabel: '10 %',
        ),
        reduced: true,
      );
      await tester.pump();
    }

    testWidgets('on a phone a short title and value still stack', (
      tester,
    ) async {
      await pumpAt(tester, const Size(412, 915));
      final title = tester.getRect(find.text('Usage'));
      final value = tester.getRect(find.text('10 %'));
      expect(value.top, greaterThanOrEqualTo(title.bottom));
    });

    testWidgets('at 1280 they share the title line', (tester) async {
      await pumpAt(tester, const Size(1280, 800));
      final title = tester.getRect(find.text('Usage'));
      final value = tester.getRect(find.text('10 %'));
      expect(value.center.dy, closeTo(title.center.dy, 4));
      expect(value.left, greaterThan(title.right));
    });
  });
}
