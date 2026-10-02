// KitQueuedMessage (docs/ux-system/kit-api/KitQueuedMessage.md; STATE-17):
// everything waiting to reach the agent as one bubble at the end of the
// conversation. The numbered groups follow the spec's "Tests required".
// Time runs on the fake clock testWidgets drives, through KitSince (C12).
import 'package:clock/clock.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_queued_message.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

const _bubbleKey = ValueKey('bubble');

Widget _app(
  Widget child, {
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      disableAnimations: reduced,
      textScaler: TextScaler.linear(textScale),
    ),
    child: direction == null
        ? inner!
        : Directionality(textDirection: direction, child: inner!),
  ),
  home: Scaffold(
    body: SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [const Text('Earlier reply'), child],
      ),
    ),
  ),
);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    _app(child, reduced: reduced, textScale: textScale, direction: direction),
  );
  await tester.pump();
}

KitQueuedItem _item(
  int i, {
  KitQueuedState state = KitQueuedState.waiting,
  List<KitMenuItem> menu = const [],
  String? reason,
  DateTime? since,
  int attachments = 0,
}) => KitQueuedItem(
  id: 'm$i',
  text: 'Message $i',
  state: state,
  menu: menu,
  reason: reason,
  since: since,
  attachmentCount: attachments,
  key: ValueKey('queued-send-$i'),
);

final _isolates = RegExp('[\\u2066-\\u2069]');

String _plain(String s) => s.replaceAll(_isolates, '');

void main() {
  kitMotionStillTests(
    'KitQueuedMessage',
    builds: {
      'waiting': () => KitQueuedMessage(items: [_item(1)]),
      'sending': () =>
          KitQueuedMessage(items: [_item(1, state: KitQueuedState.sending)]),
    },
    changes: {
      'message added': KitMotionChange(
        build: () => KitQueuedMessage(items: [_item(1)]),
        act: (tester, stage) =>
            stage.rebuild(KitQueuedMessage(items: [_item(1), _item(2)])),
        shows: 'Message 2',
      ),
      'message removed': KitMotionChange(
        build: () => KitQueuedMessage(items: [_item(1), _item(2)]),
        act: (tester, stage) =>
            stage.rebuild(KitQueuedMessage(items: [_item(1)])),
        hides: 'Message 2',
      ),
    },
  );

  testWidgets('1. empty items render nothing and no semantics node', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      const KitQueuedMessage(items: [], bubbleKey: _bubbleKey),
    );
    expect(find.byKey(_bubbleKey), findsNothing);
    expect(tester.getSize(find.byType(KitQueuedMessage)).height, 0);
    expect(find.bySemanticsLabel(RegExp('Waiting')), findsNothing);
    semantics.dispose();
  });

  testWidgets('2. three items in order in one end-aligned prompt-fill bubble '
      'with 20/20/6/20 corners, no border, no danger', (tester) async {
    await _pump(
      tester,
      KitQueuedMessage(
        bubbleKey: _bubbleKey,
        items: [_item(1), _item(2), _item(3)],
      ),
    );
    expect(
      find.text("Waiting to send · 3 · Sends when you're back online"),
      findsOneWidget,
    );
    final y = [
      for (final i in [1, 2, 3]) tester.getTopLeft(find.text('Message $i')).dy,
    ];
    expect(y[0] < y[1] && y[1] < y[2], isTrue);
    for (final i in [1, 2, 3]) {
      expect(
        find.descendant(
          of: find.byKey(_bubbleKey),
          matching: find.text('Message $i'),
        ),
        findsOneWidget,
      );
    }

    final bubble = tester.getRect(find.byKey(_bubbleKey));
    // Pane is 412 - 2*16 = 380 wide, starting at 16.
    expect(bubble.right, 396);
    expect(bubble.width, lessThanOrEqualTo(380 - KitLayout.bubbleStartInset));

    final roles = KitTokens.of(tester.element(find.byKey(_bubbleKey))).roles;
    final box = tester.widget<DecoratedBox>(find.byKey(_bubbleKey));
    final decoration = box.decoration as ShapeDecoration;
    expect(decoration.color, kitPromptBubbleFill(roles));
    final shape = decoration.shape as RoundedRectangleBorder;
    expect(shape.side, BorderSide.none);
    expect(
      shape.borderRadius,
      const BorderRadiusDirectional.only(
        topStart: Radius.circular(20),
        topEnd: Radius.circular(20),
        bottomStart: Radius.circular(20),
        bottomEnd: Radius.circular(6),
      ),
    );
    final colors = <Color?>[];
    for (final text in tester.widgetList<RichText>(
      find.descendant(
        of: find.byKey(_bubbleKey),
        matching: find.byType(RichText),
      ),
    )) {
      text.text.visitChildren((span) {
        colors.add(span.style?.color);
        return true;
      });
      colors.add(text.text.style?.color);
    }
    expect(colors, isNot(contains(roles.danger)));
  });

  testWidgets('3. each state shows its words; sending escalates after 8 s; '
      'reachedServer never offers Try again', (tester) async {
    await _pump(
      tester,
      KitQueuedMessage(
        items: [
          _item(1),
          _item(2, state: KitQueuedState.sending),
          _item(3, state: KitQueuedState.notConfirmed),
          _item(4, state: KitQueuedState.reachedServer),
          _item(5, state: KitQueuedState.failed, reason: 'the disk is full'),
          _item(6, state: KitQueuedState.afterThisReply),
          _item(7, state: KitQueuedState.addToThisTurn),
          _item(8, state: KitQueuedState.contextUpdate, attachments: 2),
        ],
      ),
    );
    expect(find.text('Waiting to send · 8'), findsOneWidget);
    expect(find.text('Waiting to send'), findsOneWidget);
    expect(find.text('Sending…'), findsOneWidget);
    expect(find.text('Not confirmed yet'), findsOneWidget);
    expect(find.text('Reached the server'), findsOneWidget);
    final failed = find.textContaining('Not accepted: ');
    expect(failed, findsOneWidget);
    expect(
      _plain(tester.widget<Text>(failed).textSpan!.toPlainText()),
      'Not accepted: the disk is full',
    );
    expect(find.text('Sends after this reply'), findsOneWidget);
    expect(find.text('Adds to this turn'), findsOneWidget);
    expect(find.text('Update waiting'), findsOneWidget);
    expect(find.text('2 attachments'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);

    // Sending with since 7 s ago: still "Sending…", then slow at 8 s.
    await tester.pumpWidget(const SizedBox.shrink());
    await _pump(
      tester,
      KitQueuedMessage(
        items: [
          _item(
            1,
            state: KitQueuedState.sending,
            since: clock.now().subtract(const Duration(seconds: 7)),
          ),
        ],
      ),
    );
    expect(find.text('Sending…'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('Sending…'), findsNothing);
    expect(find.text('Not confirmed yet'), findsOneWidget);
  });

  testWidgets('4. ten items are all laid out, no internal scroll or cap', (
    tester,
  ) async {
    await _pump(
      tester,
      KitQueuedMessage(
        bubbleKey: _bubbleKey,
        items: [for (var i = 1; i <= 10; i++) _item(i)],
      ),
    );
    expect(tester.takeException(), isNull);
    final bubble = tester.getRect(find.byKey(_bubbleKey));
    for (var i = 1; i <= 10; i++) {
      final rect = tester.getRect(find.text('Message $i', skipOffstage: false));
      expect(bubble.contains(rect.center), isTrue);
    }
    expect(
      find.descendant(
        of: find.byKey(_bubbleKey),
        matching: find.byType(Scrollable),
      ),
      findsNothing,
    );
  });

  group('5. per-item menu', () {
    List<KitMenuItem> menu(List<String> ran) => [
      KitMenuItem(label: 'Edit', onSelected: () => ran.add('Edit')),
      KitMenuItem(label: 'Remove', onSelected: () => ran.add('Remove')),
    ];

    testWidgets('long-press opens the item menu; items are custom actions; '
        'no per-item icon button', (tester) async {
      final ran = <String>[];
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitQueuedMessage(
          bubbleKey: _bubbleKey,
          items: [
            _item(1, menu: menu(ran)),
            _item(2),
          ],
        ),
      );
      expect(find.byType(KitIconButton), findsNothing);
      expect(find.byType(IconButton), findsNothing);

      final data = tester
          .getSemantics(find.byKey(const ValueKey('queued-send-1')))
          .getSemanticsData();
      final labels = [
        for (final id in data.customSemanticsActionIds ?? <int>[])
          CustomSemanticsAction.getAction(id)!.label,
      ];
      expect(labels, containsAll(['Edit', 'Remove']));

      await tester.longPress(find.text('Message 1'));
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(ran, ['Remove']);

      // An item with an empty menu is inert.
      await tester.longPress(find.text('Message 2'));
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsNothing);
      semantics.dispose();
    });

    testWidgets('right-click opens the same menu (desktop capabilities)', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      final ran = <String>[];
      await _pump(tester, KitQueuedMessage(items: [_item(1, menu: menu(ran))]));
      await tester.tap(
        find.text('Message 1'),
        buttons: kSecondaryMouseButton,
        kind: PointerDeviceKind.mouse,
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Edit'), findsOneWidget);
      expect(find.text('Remove'), findsOneWidget);
    });
  });

  testWidgets('6. action and secondaryAction call back once; neither: no '
      'action line', (tester) async {
    var sent = 0;
    var edited = 0;
    await _pump(
      tester,
      KitQueuedMessage(
        bubbleKey: _bubbleKey,
        items: [_item(1)],
        action: KitAction(label: 'Send now', onPressed: () => sent++),
        secondaryAction: KitAction(label: 'Edit', onPressed: () => edited++),
      ),
    );
    expect(
      find.descendant(
        of: find.byKey(_bubbleKey),
        matching: find.byType(KitButton),
      ),
      findsNWidgets(2),
    );
    await tester.tap(find.text('Send now'));
    await tester.tap(find.text('Edit'));
    await tester.pump();
    expect((sent, edited), (1, 1));

    await _pump(tester, KitQueuedMessage(items: [_item(1)]));
    expect(find.byType(KitButton), findsNothing);
  });

  group('7. arrivals and departures', () {
    testWidgets('a removed item folds away and leaves after standard', (
      tester,
    ) async {
      await _pump(
        tester,
        KitQueuedMessage(items: [_item(1), _item(2), _item(3)]),
      );
      await _pump(tester, KitQueuedMessage(items: [_item(1), _item(3)]));
      expect(find.text('Message 2'), findsOneWidget, reason: 'folding away');
      await tester.pump(KitMotion.standard);
      await tester.pumpAndSettle();
      expect(find.text('Message 2'), findsNothing);
      expect(
        find.text('Waiting to send · 2 · Sends when you\'re back online'),
        findsOneWidget,
      );
    });

    testWidgets('reduced motion: one pump settles', (tester) async {
      await _pump(
        tester,
        KitQueuedMessage(items: [_item(1), _item(2)]),
        reduced: true,
      );
      await tester.pumpWidget(
        _app(KitQueuedMessage(items: [_item(1)]), reduced: true),
      );
      await tester.pump();
      expect(find.text('Message 2'), findsNothing);
      // The bubble leaves at once too.
      await tester.pumpWidget(
        _app(const KitQueuedMessage(items: []), reduced: true),
      );
      await tester.pump();
      expect(find.text('Message 1'), findsNothing);
      expect(tester.getSize(find.byType(KitQueuedMessage)).height, 0);
    });
  });

  testWidgets('8. the part never calls back on its own', (tester) async {
    final ran = <String>[];
    await _pump(
      tester,
      KitQueuedMessage(
        items: [
          _item(
            1,
            state: KitQueuedState.sending,
            since: clock.now(),
            menu: [
              KitMenuItem(label: 'Try again', onSelected: () => ran.add('r')),
            ],
          ),
          _item(2, state: KitQueuedState.notConfirmed),
        ],
        action: KitAction(label: 'Send now', onPressed: () => ran.add('a')),
        secondaryAction: KitAction(
          label: 'Edit',
          onPressed: () => ran.add('e'),
        ),
      ),
    );
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(seconds: 5));
    }
    expect(find.text('Not confirmed yet'), findsNWidgets(2));
    expect(ran, isEmpty);
  });

  testWidgets('9. semantics: container named by the head line, item labels, '
      'no live region, 48 dp targets 8 dp apart', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      KitQueuedMessage(
        bubbleKey: _bubbleKey,
        items: [
          _item(1),
          _item(2, attachments: 1),
          _item(
            3,
            menu: [KitMenuItem(label: 'Edit', onSelected: () {})],
          ),
        ],
        action: const KitAction(
          label: 'Send now',
          onPressed: null,
          disabledReason: "You're offline",
        ),
        secondaryAction: KitAction(label: 'Edit', onPressed: () {}),
      ),
    );
    final head = find.bySemanticsLabel(
      "Waiting to send · 3 · Sends when you're back online",
    );
    expect(head, findsOneWidget);
    final headNode = tester.getSemantics(head);
    expect(headNode.getSemanticsData().flagsCollection.isLiveRegion, isFalse);

    for (final (i, state) in [
      (1, 'Waiting to send'),
      (2, 'Waiting to send, 1 attachment'),
      (3, 'Waiting to send'),
    ]) {
      final node = find.bySemanticsLabel(
        RegExp('^Waiting message $i of 3: .?Message $i.?\\. $state\$'),
      );
      expect(node, findsOneWidget, reason: 'item $i');
      expect(
        tester
            .getSemantics(node)
            .getSemanticsData()
            .flagsCollection
            .isLiveRegion,
        isFalse,
      );
    }

    final send = tester.getRect(
      find.ancestor(
        of: find.text('Send now'),
        matching: find.byType(KitButton),
      ),
    );
    final edit = tester.getRect(
      find.ancestor(
        of: find.text('Edit').last,
        matching: find.byType(KitButton),
      ),
    );
    expect(send.height, greaterThanOrEqualTo(48));
    expect(edit.height, greaterThanOrEqualTo(48));
    expect(edit.left - send.right, greaterThanOrEqualTo(8));
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    semantics.dispose();
  });

  testWidgets('10. keyboard: Tab reaches each item with a menu, then the '
      'actions; Shift+F10 opens the item menu', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    addTearDown(() => debugPlatformCapabilities = null);
    final menu = [KitMenuItem(label: 'Remove', onSelected: () {})];
    await _pump(
      tester,
      KitQueuedMessage(
        items: [
          _item(1, menu: menu),
          _item(2),
          _item(3, menu: menu),
        ],
        action: KitAction(label: 'Send now', onPressed: () {}),
      ),
    );
    Rect focused() => FocusManager.instance.primaryFocus!.rect;
    bool inside(Finder finder) =>
        focused().contains(tester.getRect(finder).center);

    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(inside(find.text('Message 1')), isTrue);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(inside(find.text('Message 3')), isTrue, reason: 'item 2 is inert');
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(inside(find.text('Send now')), isTrue);

    // Back to the last item, then open its menu from the keyboard.
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pump();
    expect(inside(find.text('Message 3')), isTrue);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.f10);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pumpAndSettle();
    expect(find.byType(KitMenuPanel), findsOneWidget);
    expect(find.text('Remove'), findsOneWidget);
  });

  for (final direction in TextDirection.values) {
    testWidgets('11. 200 % text at 320 dp, ${direction.name}: no overflow', (
      tester,
    ) async {
      await _pump(
        tester,
        KitQueuedMessage(
          items: [
            const KitQueuedItem(
              id: 'long',
              text:
                  'Refactor the whole cache layer so it reads from SQLite '
                  'and writes through to the server when online',
              state: KitQueuedState.failed,
              reason: 'the server refused a message this long',
              attachmentCount: 3,
            ),
            _item(2, state: KitQueuedState.afterThisReply),
          ],
          action: KitAction(label: 'Try again', onPressed: () {}),
          secondaryAction: KitAction(label: 'Edit', onPressed: () {}),
        ),
        size: const Size(320, 800),
        textScale: 2,
        direction: direction,
      );
      expect(tester.takeException(), isNull);
    });
  }
}
