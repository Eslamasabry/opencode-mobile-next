// KitChoiceCards (docs/ux-system/kit-api/KitChoiceCards.md): large answers
// to one question, a tap chooses, the chosen card has the ring and check.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  Size size = const Size(412, 915),
  bool reduced = false,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reduced,
        ),
        child: Directionality(textDirection: direction, child: widget!),
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
}

const _choices = [
  KitChoice(
    key: ValueKey('card-phone'),
    value: 'phone',
    leading: KitIcon(AppIconography.phone),
    title: 'On this phone',
    supporting: 'About 4 minutes · 208 MB download',
  ),
  KitChoice(
    key: ValueKey('card-computer'),
    value: 'computer',
    leading: KitIcon(AppIconography.computer),
    title: 'On my computer',
    supporting: 'Pair with a code, or type its address',
  ),
];

KitChoiceCards<String> _cards(
  String? selected, {
  String? busy,
  ValueChanged<String>? onSelected,
}) => KitChoiceCards<String>(
  choices: _choices,
  selected: selected,
  busy: busy,
  semanticsLabel: 'Where should your agent run?',
  onSelected: onSelected ?? (_) {},
);

void main() {
  kitMotionStillTests(
    'KitChoiceCards',
    builds: {
      'default': () => _cards('phone'),
      'working': () => _cards('phone', busy: 'phone'),
      'disabled': () => KitChoiceCards<String>(
        choices: const [
          KitChoice(
            value: 'a',
            title: 'On this phone',
            enabled: false,
            disabledReason: 'Not on this device',
          ),
        ],
        selected: null,
        onSelected: (_) {},
      ),
    },
    changes: {
      'selection changes': KitMotionChange(
        build: () => _cards('phone'),
        act: (tester, stage) => stage.rebuild(_cards('computer')),
        shows: 'On my computer',
      ),
    },
  );

  testWidgets('a tap chooses exactly once and goes nowhere', (tester) async {
    final taps = <String>[];
    await _pump(tester, _cards('phone', onSelected: taps.add));
    expect(find.text('About 4 minutes · 208 MB download'), findsOneWidget);
    await tester.tap(find.text('On my computer'));
    await tester.pump();
    expect(taps, ['computer']);
    expect(
      tester.getSemantics(find.byKey(const ValueKey('card-phone'))),
      isSemantics(
        isSelected: true,
        isInMutuallyExclusiveGroup: true,
        isButton: true,
        hasTapAction: true,
        label: 'On this phone. About 4 minutes · 208 MB download',
      ),
    );
  });

  testWidgets('the chosen check fades in from slightly smaller, no overshoot', (
    tester,
  ) async {
    final check = find.descendant(
      of: find.byKey(const ValueKey('card-computer')),
      matching: find.byIcon(AppIconography.check),
    );
    // The drawn width of the check as a share of its settled width.
    double drawn() =>
        tester.renderObject<RenderBox>(check).getTransformTo(null).entry(0, 0);
    await _pump(tester, _cards('phone'));
    await tester.pumpAndSettle();
    await _pump(tester, _cards('computer'));
    final frames = <double>[];
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 16));
      frames.add(drawn());
    }
    await tester.pumpAndSettle();
    final settled = drawn();
    final shares = [for (final f in frames) f / settled];
    expect(shares.first, greaterThanOrEqualTo(KitMotion.markFrom - 1e-6));
    expect(shares.first, lessThan(1), reason: 'it does grow in');
    expect(shares.every((s) => s <= 1 + 1e-6), isTrue, reason: 'no overshoot');
  });

  testWidgets('choosing never moves the words', (tester) async {
    await _pump(tester, _cards('phone'), reduced: true);
    final before = tester.getTopLeft(find.text('On my computer'));
    await _pump(tester, _cards('computer'), reduced: true);
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('On my computer')), before);
  });

  testWidgets('while one answer is acted on every card waits', (tester) async {
    final taps = <String>[];
    await _pump(
      tester,
      _cards('phone', busy: 'phone', onSelected: taps.add),
      reduced: true,
    );
    expect(find.byKey(const ValueKey('kit-choice-card-busy')), findsOneWidget);
    await tester.tap(find.text('On my computer'));
    await tester.pump();
    expect(taps, isEmpty);
  });

  testWidgets('a disabled answer says why and cannot be chosen', (
    tester,
  ) async {
    final taps = <String>[];
    await _pump(
      tester,
      KitChoiceCards<String>(
        choices: const [
          KitChoice(
            value: 'a',
            title: 'On this phone',
            supporting: 'About 4 minutes',
            enabled: false,
            disabledReason: 'Not on this device',
          ),
        ],
        selected: null,
        onSelected: taps.add,
      ),
    );
    expect(find.text('Not on this device · About 4 minutes'), findsOneWidget);
    await tester.tap(find.text('On this phone'));
    await tester.pump();
    expect(taps, isEmpty);
  });

  for (final direction in TextDirection.values) {
    testWidgets('320 dp at 200 % text wraps without overflow ($direction)', (
      tester,
    ) async {
      await _pump(
        tester,
        _cards('phone'),
        size: const Size(320, 1400),
        textScale: 2,
        direction: direction,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('On my computer'), findsOneWidget);
      for (final key in ['card-phone', 'card-computer']) {
        final rect = tester.getRect(find.byKey(ValueKey(key)));
        expect(rect.left, greaterThanOrEqualTo(0), reason: key);
        expect(rect.right, lessThanOrEqualTo(320), reason: key);
      }
    });
  }
}
