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

  group('one card shape with KitRequestCard (FC2)', () {
    KitRequestCard request({
      KitRequestPhase phase = KitRequestPhase.waiting,
      KitReceipt? receipt,
      bool inList = false,
    }) => KitRequestCard.ask(
      kind: KitRequestKind.form,
      title: 'Pick a branch',
      who: 'fox',
      reason: KitNeedsYouReason.decision,
      ifIgnored: 'The agent waits; nothing is lost.',
      announcement: 'Question: Pick a branch',
      phase: phase,
      receipt: receipt,
      answers: const KitRequestInSheet(),
      onDetails: () {},
      inList: inList,
    );

    Finder tile(Finder within) => find.descendant(
      of: within,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is SizedBox &&
            widget.width == KitTokens.requestTileSize &&
            widget.height == KitTokens.requestTileSize,
      ),
    );

    testWidgets('an agent card wears the request heading: glyph in the tile', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          Column(
            children: [
              KeyedSubtree(key: const Key('req'), child: request()),
              KeyedSubtree(
                key: const Key('agent'),
                child: _card(ask: const Text('ASK')),
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      final reqTile = tile(find.byKey(const Key('req')));
      final agentTile = tile(find.byKey(const Key('agent')));
      expect(reqTile, findsOneWidget);
      expect(agentTile, findsOneWidget);
      // The title sits beside the tile in both, at the same inset.
      double titleInset(String key) =>
          tester
              .getTopLeft(
                find.descendant(
                  of: find.byKey(Key(key)),
                  matching: find.text('Pick a branch'),
                ),
              )
              .dx -
          tester.getTopLeft(tile(find.byKey(Key(key)))).dx;
      expect(titleInset('agent'), titleInset('req'));
    });

    testWidgets('the answered row is one shape: the receipt under the title', (
      tester,
    ) async {
      final receipt = KitReceipt(
        state: KitReceiptState.confirmed,
        label: 'Sent: main',
        onUndo: () {},
      );
      await tester.pumpWidget(
        _host(
          Column(
            children: [
              KeyedSubtree(
                key: const Key('req'),
                child: request(
                  phase: KitRequestPhase.answered,
                  receipt: receipt,
                ),
              ),
              KeyedSubtree(
                key: const Key('agent'),
                child: _card(mode: KitAgentCardMode.receipt, onUndo: () {}),
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      Rect rect(String key, Finder what) => tester.getRect(
        find.descendant(of: find.byKey(Key(key)), matching: what),
      );
      for (final key in ['req', 'agent']) {
        final title = rect(key, find.text('Pick a branch'));
        final sent = rect(key, find.byType(KitReceipt));
        // Stacked: the receipt starts where the title starts, right under it.
        expect(sent.left, title.left, reason: key);
        expect(sent.top, closeTo(title.bottom, 0.5), reason: key);
      }
      expect(
        rect('agent', find.text('Pick a branch')).left -
            tester.getTopLeft(find.byKey(const Key('agent'))).dx,
        rect('req', find.text('Pick a branch')).left -
            tester.getTopLeft(find.byKey(const Key('req'))).dx,
      );
    });

    testWidgets('under a list row both sit as the list places them', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          SizedBox(
            width: 360,
            child: Column(
              children: [
                KeyedSubtree(
                  key: const Key('req'),
                  child: request(inList: true),
                ),
                KeyedSubtree(
                  key: const Key('agent'),
                  child: _card(ask: const Text('ASK'), inList: true),
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getSize(find.byKey(const Key('req'))).width, 360);
      final reqFrame = tester.getRect(
        find.byKey(const ValueKey('kit-request-card')),
      );
      final agentFrame = tester.getRect(
        find
            .descendant(
              of: find.byKey(const Key('agent')),
              matching: find.byType(DecoratedBox),
            )
            // Inside the needs-you ring, as the request's keyed frame.
            .at(1),
      );
      expect(reqFrame.left, agentFrame.left);
      expect(reqFrame.width, agentFrame.width);
    });
  });
}
