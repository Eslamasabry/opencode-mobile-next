// KitSenseAsk (an ask for a photo, file or voice note): pick, remove, send.
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
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => Directionality(
    textDirection: direction,
    child: MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: app!,
    ),
  ),
  home: Scaffold(body: SingleChildScrollView(child: child)),
);

KitSenseItem _item(int n) => KitSenseItem(
  id: 'p$n',
  name: 'IMG_000$n.jpg',
  removeLabel: 'Remove photo $n',
  detail: '2.1 MB',
);

KitSenseAsk _ask({
  List<KitSenseItem> items = const [],
  int max = 2,
  VoidCallback? onSend,
  VoidCallback? onTake,
  ValueChanged<String>? onRemove,
  bool sending = false,
}) => KitSenseAsk(
  kind: KitSenseKind.photo,
  purpose: 'Show me the broken screen.',
  actions: [
    KitSenseAction(label: 'Take photo', onPressed: onTake ?? () {}),
    KitSenseAction(label: 'Choose photo', onPressed: () {}),
  ],
  items: items,
  max: max,
  countLabel: '${items.length} of $max',
  maxReachedLabel: 'That is all it can take',
  sendLabel: 'Send',
  sendDisabledReason: 'Add a photo first',
  onSend: onSend,
  onRemove: onRemove,
  sending: sending,
);

void main() {
  kitMotionStillTests(
    'KitSenseAsk',
    builds: {
      'empty': () => _ask(),
      'picked': () => _ask(items: [_item(1)], onSend: () {}),
      'sending': () => _ask(items: [_item(1)], sending: true),
    },
    changes: {
      'a photo arrives': KitMotionChange(
        build: () => _ask(),
        act: (tester, stage) =>
            stage.rebuild(_ask(items: [_item(1)], onSend: () {})),
        shows: 'IMG_0001.jpg',
      ),
    },
  );

  testWidgets('shows the purpose and the pick actions', (tester) async {
    await tester.pumpWidget(_host(_ask()));
    expect(find.text('Show me the broken screen.'), findsOneWidget);
    expect(find.text('Take photo'), findsOneWidget);
    expect(find.text('Choose photo'), findsOneWidget);
  });

  testWidgets('Take photo calls out', (tester) async {
    var taken = 0;
    await tester.pumpWidget(_host(_ask(onTake: () => taken++)));
    await tester.tap(find.text('Take photo'));
    expect(taken, 1);
  });

  testWidgets('Send is off with nothing picked and says why', (tester) async {
    var sent = 0;
    await tester.pumpWidget(_host(_ask(onSend: () => sent++)));
    await tester.tap(find.text('Send'), warnIfMissed: false);
    expect(sent, 0);
    expect(find.text('Add a photo first'), findsOneWidget);
  });

  testWidgets('picked items list with detail and Send calls out', (
    tester,
  ) async {
    var sent = 0;
    await tester.pumpWidget(
      _host(_ask(items: [_item(1)], onSend: () => sent++)),
    );
    expect(find.text('IMG_0001.jpg'), findsOneWidget);
    expect(find.text('2.1 MB'), findsOneWidget);
    expect(find.text('1 of 2'), findsOneWidget);
    await tester.tap(find.text('Send'));
    expect(sent, 1);
  });

  testWidgets('remove sends the item id', (tester) async {
    final removed = <String>[];
    await tester.pumpWidget(
      _host(
        _ask(items: [_item(1), _item(2)], onSend: () {}, onRemove: removed.add),
      ),
    );
    await tester.tap(find.byTooltip('Remove photo 2'));
    expect(removed, ['p2']);
  });

  testWidgets('at max the pick actions are off with the reason', (
    tester,
  ) async {
    var taken = 0;
    await tester.pumpWidget(
      _host(
        _ask(items: [_item(1), _item(2)], onSend: () {}, onTake: () => taken++),
      ),
    );
    await tester.tap(find.text('Take photo'), warnIfMissed: false);
    expect(taken, 0);
    expect(find.text('That is all it can take'), findsWidgets);
  });

  testWidgets('while sending nothing answers', (tester) async {
    var sent = 0;
    final removed = <String>[];
    await tester.pumpWidget(
      _host(
        KitSenseAsk(
          kind: KitSenseKind.file,
          purpose: 'p',
          actions: [KitSenseAction(label: 'Choose file', onPressed: () {})],
          items: [_item(1)],
          sendLabel: 'Send',
          onSend: () => sent++,
          onRemove: removed.add,
          sending: true,
        ),
      ),
    );
    await tester.tap(find.byType(KitActionBlock), warnIfMissed: false);
    expect(sent, 0);
    expect(removed, isEmpty);
  });

  testWidgets('2.0 text and RTL do not overflow at 360 px', (tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _host(
        _ask(items: [_item(1), _item(2)], onSend: () {}),
        textScale: 2,
        direction: TextDirection.rtl,
      ),
    );
    expect(tester.takeException(), isNull);
  });
}
