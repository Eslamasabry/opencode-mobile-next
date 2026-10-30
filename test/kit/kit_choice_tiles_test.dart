// KitChoiceTiles (docs/ux-system/kit-api/KitChoiceTiles.md): a few big
// choices that lead with the reason, a tap chooses, one quiet "more" line.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_tiles.dart';

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
    key: ValueKey('tile-default'),
    value: 'sonnet',
    title: 'Default',
    supporting: 'Claude Sonnet 5.5',
  ),
  KitChoice(
    key: ValueKey('tile-recent'),
    value: 'sol',
    title: 'Recently used',
    supporting: 'GPT-6 Sol',
  ),
  KitChoice(
    key: ValueKey('tile-cheap'),
    value: 'pickle',
    title: 'Cheapest',
    supporting: 'Big Pickle · Free',
  ),
];

KitChoiceTiles<String> _still(String? selected, {String? busy}) =>
    KitChoiceTiles<String>(
      choices: _choices,
      selected: selected,
      busy: busy,
      onSelected: (_) {},
      more: KitChoiceMore(label: 'All models (9)', onTap: () {}),
    );

void main() {
  kitMotionStillTests(
    'KitChoiceTiles',
    builds: {
      'default': () => _still('sonnet'),
      'working': () => _still('sonnet', busy: 'pickle'),
      'disabled': () => KitChoiceTiles<String>(
        choices: const [
          KitChoice(
            value: 'a',
            title: 'Default',
            enabled: false,
            disabledReason: 'Saving the choice…',
          ),
        ],
        selected: null,
        onSelected: (_) {},
      ),
    },
    changes: {
      'selection changes': KitMotionChange(
        build: () => _still('sonnet'),
        act: (tester, stage) => stage.rebuild(_still('pickle')),
        shows: 'Cheapest',
      ),
    },
  );

  testWidgets('a tap chooses exactly once; the chosen tile is checked', (
    tester,
  ) async {
    final taps = <String>[];
    await _pump(
      tester,
      KitChoiceTiles<String>(
        choices: _choices,
        selected: 'sonnet',
        onSelected: taps.add,
      ),
    );
    expect(find.text('Default'), findsOneWidget);
    expect(find.text('Big Pickle · Free'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('kit-choice-tile-mark-on')),
      findsOneWidget,
    );
    await tester.tap(find.text('Cheapest'));
    await tester.pump();
    expect(taps, ['pickle']);
    expect(
      tester.getSemantics(find.text('Default')),
      isSemantics(isSelected: true, isInMutuallyExclusiveGroup: true),
    );
  });

  testWidgets('while one choice is applied every tile waits', (tester) async {
    final taps = <String>[];
    await _pump(
      tester,
      KitChoiceTiles<String>(
        choices: _choices,
        selected: 'sonnet',
        busy: 'sol',
        onSelected: taps.add,
        more: KitChoiceMore(
          label: 'All models (9)',
          onTap: () => taps.add('more'),
        ),
      ),
      reduced: true,
    );
    expect(find.byKey(const ValueKey('kit-choice-tile-busy')), findsOneWidget);
    await tester.tap(find.text('Cheapest'));
    await tester.tap(find.text('All models (9)'));
    await tester.pump();
    expect(taps, isEmpty);
  });

  testWidgets('the more line opens everything else', (tester) async {
    var opened = 0;
    await _pump(
      tester,
      KitChoiceTiles<String>(
        choices: _choices,
        selected: null,
        onSelected: (_) {},
        more: KitChoiceMore(
          key: const Key('more'),
          label: 'All models (9)',
          onTap: () => opened++,
        ),
      ),
    );
    expect(find.byKey(const ValueKey('kit-choice-tile-mark-on')), findsNothing);
    await tester.tap(find.byKey(const Key('more')));
    await tester.pump();
    expect(opened, 1);
  });

  testWidgets('a disabled choice says why and cannot be chosen', (
    tester,
  ) async {
    final taps = <String>[];
    await _pump(
      tester,
      KitChoiceTiles<String>(
        choices: const [
          KitChoice(
            value: 'a',
            title: 'Default',
            supporting: 'Claude Sonnet 5.5',
            enabled: false,
            disabledReason: 'Saving the choice…',
          ),
        ],
        selected: null,
        onSelected: taps.add,
      ),
    );
    expect(find.text('Saving the choice… · Claude Sonnet 5.5'), findsOneWidget);
    await tester.tap(find.text('Default'));
    await tester.pump();
    expect(taps, isEmpty);
  });

  for (final direction in TextDirection.values) {
    testWidgets('320 dp at 200 % text wraps without overflow ($direction)', (
      tester,
    ) async {
      await _pump(
        tester,
        KitChoiceTiles<String>(
          choices: _choices,
          selected: 'sonnet',
          onSelected: (_) {},
          more: KitChoiceMore(label: 'All models (9)', onTap: () {}),
        ),
        size: const Size(320, 1400),
        textScale: 2,
        direction: direction,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Recently used'), findsOneWidget);
    });
  }
}
