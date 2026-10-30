// KitTip and KitTour (the teaching system's kit parts, docs/ux-system/
// teaching.md): a tip shows while visible, takes no touch, fades on any
// touch or after its hold, and is announced politely; reduced motion is a
// plain fade; the tour walks its steps with Next and Skip.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _host(Widget child, {bool reduce = false, Locale? locale}) =>
    MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.dark(),
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
        child: widget!,
      ),
      home: Scaffold(body: child),
    );

const _bubble = ValueKey('tip');

Widget _tip({required bool visible, required VoidCallback onDismissed}) =>
    Align(
      alignment: const Alignment(.8, .8),
      child: KitTip(
        tipKey: _bubble,
        message: 'Hold to stop after this step',
        visible: visible,
        onDismissed: onDismissed,
        child: KitIconButton(
          icon: Icons.stop,
          tooltip: 'Stop',
          onPressed: () {},
        ),
      ),
    );

void main() {
  // MOT-7: settles at once under reduced motion and Off.
  kitMotionStillTests(
    'KitTip',
    builds: {
      'hidden': () => _tip(visible: false, onDismissed: () {}),
      'shown': () => _tip(visible: true, onDismissed: () {}),
    },
  );
  kitMotionStillTests(
    'KitTipBubble',
    builds: {
      'plain': () =>
          const KitTipBubble(message: 'Hold to stop after this step'),
    },
  );

  testWidgets('shows while visible, above the control, and fades after '
      'its hold', (tester) async {
    var dismissed = 0;
    await tester.pumpWidget(
      _host(_tip(visible: true, onDismissed: () => dismissed++)),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(_bubble), findsOneWidget);
    final bubble = tester.getRect(find.byKey(_bubble));
    final control = tester.getRect(find.byType(KitIconButton));
    expect(bubble.bottom, lessThanOrEqualTo(control.top));
    expect(bubble.right, lessThanOrEqualTo(800));
    await tester.pump(KitTip.defaultHold);
    await tester.pumpAndSettle();
    expect(find.byKey(_bubble), findsNothing);
    expect(dismissed, 1);
  });

  testWidgets('any touch fades it and still reaches what was touched', (
    tester,
  ) async {
    var dismissed = 0;
    var pressed = 0;
    await tester.pumpWidget(
      _host(
        Column(
          children: [
            TextButton(onPressed: () => pressed++, child: const Text('Other')),
            Expanded(
              child: _tip(visible: true, onDismissed: () => dismissed++),
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Other'));
    await tester.pumpAndSettle();
    expect(pressed, 1);
    expect(dismissed, 1);
    expect(find.byKey(_bubble), findsNothing);
  });

  testWidgets('it takes no touch of its own', (tester) async {
    await tester.pumpWidget(_host(_tip(visible: true, onDismissed: () {})));
    await tester.pumpAndSettle();
    expect(
      find.ancestor(
        of: find.byKey(_bubble),
        matching: find.byType(IgnorePointer),
      ),
      findsWidgets,
    );
  });

  testWidgets('is announced politely to a screen reader', (tester) async {
    final announced = <Map<Object?, Object?>>[];
    tester.binding.defaultBinaryMessenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      (message) async {
        announced.add((message as Map)['data'] as Map<Object?, Object?>);
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger
          .setMockDecodedMessageHandler<dynamic>(
            SystemChannels.accessibility,
            null,
          ),
    );
    await tester.pumpWidget(_host(_tip(visible: true, onDismissed: () {})));
    await tester.pumpAndSettle();
    expect(announced.single['message'], 'Hold to stop after this step');
    // Polite (0), never assertive.
    expect(announced.single['assertiveness'] ?? 0, 0);
  });

  testWidgets('reduced motion: no rise, no fade', (tester) async {
    await tester.pumpWidget(
      _host(_tip(visible: true, onDismissed: () {}), reduce: true),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    final moves = tester.widgetList<Transform>(
      find.ancestor(of: find.byKey(_bubble), matching: find.byType(Transform)),
    );
    for (final move in moves) {
      expect(move.transform.getTranslation().y, 0);
    }
    await tester.pumpAndSettle();
  });

  testWidgets('reads right to left and wraps at 2x text', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ar'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: AppTheme.dark(),
        builder: (context, widget) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: widget!,
        ),
        home: Scaffold(body: _tip(visible: true, onDismissed: () {})),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byKey(_bubble)).width, lessThanOrEqualTo(280));
  });

  testWidgets('a tour walks its steps; Skip leaves, Done finishes', (
    tester,
  ) async {
    late BuildContext context;
    await tester.pumpWidget(
      _host(
        Builder(
          builder: (inner) {
            context = inner;
            return const Column(
              children: [
                SizedBox(key: ValueKey('a'), height: 80, width: 80),
                SizedBox(height: 300),
                SizedBox(key: ValueKey('b'), height: 80, width: 80),
              ],
            );
          },
        ),
      ),
    );
    const steps = [
      KitTourStep(anchors: [ValueKey('a')], message: 'First'),
      KitTourStep(anchors: [ValueKey('b')], message: 'Second'),
    ];
    final done = KitTour.show(context, steps: steps);
    await tester.pumpAndSettle();
    expect(find.text('First'), findsOneWidget);
    expect(find.text('Step 1 of 2'), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('kit-tour-next')));
    await tester.pumpAndSettle();
    expect(find.text('Second'), findsOneWidget);
    await tester.tap(find.text('Done'));
    await tester.pumpAndSettle();
    expect(await done, isTrue);

    final left = KitTour.show(context, steps: steps);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-tour-skip')));
    await tester.pumpAndSettle();
    expect(await left, isFalse);
    expect(find.text('First'), findsNothing);
  });

  // On the phone the tour hid the very screen it pointed at: a page route
  // on top runs the screen's own exit transition (Android's zoom fades it
  // out), so the cut-out showed black.
  testWidgets('the screen under a tour stays in sight', (tester) async {
    late BuildContext context;
    await tester.pumpWidget(_host(const SizedBox()));
    final navigator = tester.state<NavigatorState>(find.byType(Navigator));
    unawaited(
      navigator.push(
        KitPageRoute<void>(
          builder: (inner) {
            context = inner;
            return const Column(
              children: [
                SizedBox(key: ValueKey('a'), height: 80, width: 80),
                SizedBox(key: ValueKey('b'), height: 80, width: 80),
              ],
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    const steps = [
      KitTourStep(anchors: [ValueKey('a')], message: 'First'),
      KitTourStep(anchors: [ValueKey('b')], message: 'Second'),
    ];
    final screen = ModalRoute.of(context)!;
    final left = KitTour.show(context, steps: steps);
    await tester.pumpAndSettle();
    expect(find.text('First'), findsOneWidget);
    expect(screen.secondaryAnimation!.value, 0);
    await tester.tap(find.byKey(const ValueKey('kit-tour-skip')));
    await tester.pumpAndSettle();
    expect(await left, isFalse);
  });
}
