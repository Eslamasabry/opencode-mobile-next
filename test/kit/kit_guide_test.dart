// KitGuide (docs/ux-system/kit-api/KitGuide.md): a settings page that asks one
// decision at a time — a rail of chips with their values, the open question
// with its "Changes" line, and on a guided visit a Next bar. No preview: the
// page itself wears the choices.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _groups = [
  KitGuideItem(
    id: 'colours',
    key: ValueKey('g-colours'),
    label: 'Colours',
    value: 'Dark, Nord',
    next: true,
  ),
  KitGuideItem(id: 'style', label: 'Style', value: 'Glass', done: true),
  KitGuideItem(
    id: 'background',
    label: 'Background',
    value: 'Off in Saver',
    held: true,
  ),
  KitGuideItem(id: 'comfort', label: 'Text & motion', value: 'Default'),
];

const _questions = [
  KitGuideItem(
    id: 'mode',
    key: ValueKey('q-mode'),
    label: 'Mode',
    value: 'Dark',
    semanticsLabel: 'Mode, Dark, question 1 of 3 in Colours',
  ),
  KitGuideItem(
    id: 'theme',
    key: ValueKey('q-theme'),
    label: 'Theme',
    value: 'Nord',
  ),
  KitGuideItem(id: 'accent', label: 'Accent', value: 'Match theme'),
];

KitGuide guide({
  String? selected,
  ValueChanged<String>? onItem,
  VoidCallback? onBack,
  VoidCallback? onNext,
  bool ready = false,
}) => KitGuide(
  items: selected == null ? _groups : _questions,
  selected: selected,
  onItem: onItem ?? (_) {},
  group: selected == null
      ? null
      : KitGuideGroup(label: 'Colours', onBack: onBack ?? () {}),
  question: selected == null
      ? null
      : KitGuideQuestion(
          id: selected,
          title: selected == 'mode' ? 'Light or dark?' : 'Which colours?',
          changes: 'Every page',
          child: KitText('Answer for $selected'),
        ),
  home: const KitText('Start with Colours'),
  next: KitGuideNext(
    key: const ValueKey('next'),
    count: '1 of 10',
    label: 'Next: Theme',
    onPressed: onNext ?? () {},
    ready: ready,
  ),
);

Widget _host(Widget child, {double textScale = 1}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: app!,
  ),
  home: Scaffold(body: child),
);

void main() {
  kitMotionStillTests(
    'KitGuide',
    builds: {
      'start view': () => guide(),
      'a question open': () => guide(selected: 'mode'),
    },
    changes: {
      'a chip opens its question': KitMotionChange(
        build: () => guide(),
        act: (tester, stage) => stage.rebuild(guide(selected: 'mode')),
        shows: 'Light or dark?',
      ),
      'the next question slides in': KitMotionChange(
        build: () => guide(selected: 'mode'),
        act: (tester, stage) => stage.rebuild(guide(selected: 'theme')),
        shows: 'Which colours?',
      ),
    },
  );

  testWidgets('the start view shows every chip with its value', (tester) async {
    String? tapped;
    await tester.pumpWidget(_host(guide(onItem: (id) => tapped = id)));
    for (final item in _groups) {
      expect(find.text(item.label), findsOneWidget);
      expect(find.text(item.value), findsOneWidget);
    }
    expect(find.text('Start with Colours'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('g-colours')));
    expect(tapped, 'colours');
  });

  testWidgets('an open question names what it changes, with a way back', (
    tester,
  ) async {
    var back = 0;
    String? tapped;
    await tester.pumpWidget(
      _host(
        guide(
          selected: 'mode',
          onBack: () => back++,
          onItem: (id) => tapped = id,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Light or dark?'), findsOneWidget);
    expect(find.textContaining('Every page'), findsOneWidget);
    expect(find.text('Changes'), findsNothing); // one line, with the words
    expect(find.text('Answer for mode'), findsOneWidget);
    // The question only: the start view's words are not built.
    expect(find.text('Start with Colours'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('q-theme')));
    expect(tapped, 'theme');
    await tester.tap(find.text('All'));
    expect(back, 1);
  });

  testWidgets('the Next bar shows the count and runs its step', (tester) async {
    var next = 0;
    await tester.pumpWidget(
      _host(guide(selected: 'mode', onNext: () => next++, ready: true)),
    );
    await tester.pumpAndSettle();
    expect(find.text('1 of 10'), findsOneWidget);
    await tester.tap(find.text('Next: Theme'));
    expect(next, 1);
  });

  testWidgets('a chip reads its value and its place', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_host(guide(selected: 'mode')));
    await tester.pumpAndSettle();
    expect(
      find.bySemanticsLabel('Mode, Dark, question 1 of 3 in Colours'),
      findsOneWidget,
    );
    expect(find.bySemanticsLabel('Theme, Nord'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('at 2x text and 320 dp nothing overflows', (tester) async {
    tester.view
      ..physicalSize = const Size(320, 640)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    for (final selected in [null, 'mode']) {
      await tester.pumpWidget(_host(guide(selected: selected), textScale: 2));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('a medium window keeps the rail on top of the question', (
    tester,
  ) async {
    tester.view
      ..physicalSize = const Size(1280, 800)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(guide(selected: 'mode')));
    await tester.pumpAndSettle();
    expect(find.text('Preview'), findsNothing);
    final rail = tester.getCenter(find.byKey(const ValueKey('q-mode')));
    final question = tester.getCenter(find.text('Light or dark?'));
    expect(rail.dy, lessThan(question.dy));
    expect(tester.takeException(), isNull);
  });

  // Flat polish (sweep 3730, 04-appearance): the Solid start view's rows had
  // no chevron though each opens a page, and their hairlines ran off the
  // end while every other list stops at the margin.
  testWidgets('Solid start view: rows that open a page, parted like every '
      'list', (tester) async {
    tester.view
      ..physicalSize = const Size(412, 915)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(guide()));
    await tester.pumpAndSettle();
    expect(find.byIcon(AppIconography.chevronRight), findsNWidgets(4));
    final words = tester.getTopLeft(find.text('Colours')).dx;
    final lines = find.byKey(const ValueKey('kit-divider-line'));
    // Three hairlines between the four rows (the Next bar's own rule is
    // edge to edge, under them).
    final between = [
      for (var i = 0; i < lines.evaluate().length; i++)
        if (tester.getRect(lines.at(i)).left > 0) tester.getRect(lines.at(i)),
    ];
    expect(between, hasLength(3));
    for (final rect in between) {
      expect(rect.left, words);
      expect(rect.right, 412 - words);
    }
  });
}
