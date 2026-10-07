// KitAgentCard (the frame for an agent's card): modes, receipt, undo, expand,
// and the still-motion registration G8 requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(
  Widget child, {
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
  Size? size,
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => Directionality(
    textDirection: direction,
    child: MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: TextScaler.linear(textScale),
        size: size ?? MediaQuery.sizeOf(context),
      ),
      child: app!,
    ),
  ),
  home: Scaffold(body: SingleChildScrollView(child: child)),
);

KitAgentCard _card({
  KitAgentCardMode mode = KitAgentCardMode.full,
  Widget? ask,
  bool inList = false,
  VoidCallback? onUndo,
  String? expandLabel,
}) => KitAgentCard(
  eyebrow: 'Claude Code asks',
  title: 'Pick a branch',
  body: const [Text('The deploy touches two branches.')],
  ask: ask,
  mode: mode,
  inList: inList,
  receiptLabel: 'Sent: main',
  onUndo: onUndo,
  expandLabel: expandLabel,
  passedOverLabel: 'Not answered',
  unreadableLabel: 'The card could not be shown',
  detailsLabel: 'Details',
  details: 'unknown key "x" at nodes[2]',
);

void main() {
  kitMotionStillTests(
    'KitAgentCard',
    builds: {
      'asking': () => _card(ask: const Text('ASK')),
      'report': () => _card(),
      'receipt': () => _card(mode: KitAgentCardMode.receipt),
      'passed over': () => _card(mode: KitAgentCardMode.passedOver),
      'unreadable': () => _card(mode: KitAgentCardMode.unreadable),
    },
    changes: {
      'answered': KitMotionChange(
        build: () => _card(ask: const Text('ASK')),
        act: (tester, stage) =>
            stage.rebuild(_card(mode: KitAgentCardMode.receipt)),
        // KitReceipt isolates the act's words (FSI ... PDI).
        shows: '\u2068Sent: main\u2069',
      ),
    },
  );

  testWidgets('a full card shows eyebrow, title, body and ask', (tester) async {
    await tester.pumpWidget(_host(_card(ask: const Text('ASK'))));
    await tester.pumpAndSettle();
    expect(find.text('Claude Code asks'), findsOneWidget);
    expect(find.text('Pick a branch'), findsOneWidget);
    expect(find.text('The deploy touches two branches.'), findsOneWidget);
    expect(find.text('ASK'), findsOneWidget);
  });

  testWidgets('a waiting card wears the needs-you ring, a report does not', (
    tester,
  ) async {
    int rings() => find
        .byWidgetPredicate(
          (w) =>
              w is DecoratedBox &&
              w.decoration is ShapeDecoration &&
              (w.decoration as ShapeDecoration).color != null &&
              (w.decoration as ShapeDecoration).color!.a < .1 &&
              (w.decoration as ShapeDecoration).color!.a > 0,
        )
        .evaluate()
        .length;
    await tester.pumpWidget(_host(_card(ask: const Text('ASK'))));
    await tester.pumpAndSettle();
    final withAsk = rings();
    await tester.pumpWidget(_host(_card()));
    await tester.pumpAndSettle();
    expect(withAsk, greaterThan(rings()));
  });

  testWidgets('receipt shows the act and Undo calls back', (tester) async {
    var undone = 0;
    await tester.pumpWidget(
      _host(_card(mode: KitAgentCardMode.receipt, onUndo: () => undone++)),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Sent: main'), findsOneWidget);
    expect(find.text('Claude Code asks'), findsNothing);
    await tester.tap(find.text('Undo'));
    expect(undone, 1);
  });

  testWidgets('without onUndo there is no Undo', (tester) async {
    await tester.pumpWidget(_host(_card(mode: KitAgentCardMode.receipt)));
    await tester.pumpAndSettle();
    expect(find.text('Undo'), findsNothing);
  });

  testWidgets('a receipt opens its body read-only on tap', (tester) async {
    await tester.pumpWidget(
      _host(
        _card(mode: KitAgentCardMode.receipt, expandLabel: 'Show the card'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('The deploy touches two branches.'), findsNothing);
    await tester.tap(find.text('Pick a branch'));
    await tester.pumpAndSettle();
    expect(find.text('The deploy touches two branches.'), findsOneWidget);
    await tester.tap(find.text('Pick a branch'));
    await tester.pumpAndSettle();
    expect(find.text('The deploy touches two branches.'), findsNothing);
  });

  testWidgets('passed over is one quiet line with nothing to answer', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(_card(mode: KitAgentCardMode.passedOver, ask: const Text('ASK'))),
    );
    await tester.pumpAndSettle();
    expect(find.text('Not answered'), findsOneWidget);
    expect(find.text('ASK'), findsNothing);
  });

  testWidgets('unreadable keeps the technical text under Details', (
    tester,
  ) async {
    await tester.pumpWidget(_host(_card(mode: KitAgentCardMode.unreadable)));
    await tester.pumpAndSettle();
    expect(find.text('The card could not be shown'), findsOneWidget);
    expect(find.text('unknown key "x" at nodes[2]'), findsNothing);
    await tester.tap(find.text('Details'));
    await tester.pumpAndSettle();
    expect(find.textContaining('unknown key'), findsWidgets);
  });

  testWidgets('inList drops the reading-width centring', (tester) async {
    await tester.pumpWidget(
      _host(
        _card(inList: true, ask: const Text('ASK')),
        size: const Size(1200, 800),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(KitEntrance), findsNothing);
  });

  testWidgets('2.0 text and RTL do not overflow', (tester) async {
    for (final mode in KitAgentCardMode.values) {
      await tester.pumpWidget(
        _host(
          _card(mode: mode, ask: const Text('ASK'), expandLabel: 'Show'),
          textScale: 2,
          direction: TextDirection.rtl,
          size: const Size(360, 740),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull, reason: '$mode');
    }
  });
}
