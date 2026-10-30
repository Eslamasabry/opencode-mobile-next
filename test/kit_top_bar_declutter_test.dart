// The chat header's quiet rules and micro animations at the kit level: one
// title line on a phone with the full words on a tap, the collapse button and
// the ring coming and going without a jump, the project line's menu, the
// server joining the line, and all of it instant under reduced motion.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _long = 'Find latest finance hub project in /project';
const _collapse = Key('collapse');
const _ring = Key('ring');

Widget _bar({
  String title = _long,
  bool collapseVisible = true,
  bool ringShown = true,
  String? server,
  double textScale = 1,
  bool reduced = false,
}) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: MediaQuery(
    data: MediaQueryData(
      size: const Size(412, 800),
      textScaler: TextScaler.linear(textScale),
      disableAnimations: reduced,
    ),
    child: Scaffold(
      body: KitTopBar(
        title: title,
        titleKey: const Key('title'),
        exit: KitTopBarExit.none,
        actions: [
          KitAction(
            key: _collapse,
            icon: Icons.unfold_less,
            label: 'Collapse all steps',
            visible: collapseVisible,
            onPressed: () {},
          ),
        ],
        accessory: const SizedBox(key: _ring, width: 24, height: 24),
        accessoryShown: ringShown,
        menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
        scope: KitTopBarScope(
          chipKey: const Key('chip'),
          label: 'FinanceHub3',
          suffix: server,
          semanticsLabel: 'Project FinanceHub3',
          icon: Icons.folder_open,
          menuLabel: 'Project menu',
          items: [KitMenuItem(label: 'Files', onSelected: () {})],
        ),
      ),
    ),
  ),
);

Finder get _titleText => find.descendant(
  of: find.byType(KitTopBar),
  matching: find.text(_long, skipOffstage: false),
);

void main() {
  testWidgets('the title keeps one line on a phone, two at large text', (
    tester,
  ) async {
    await tester.pumpWidget(_bar());
    expect(tester.widget<Text>(_titleText).maxLines, 1);
    final one = tester.getSize(find.byKey(const Key('title'))).height;
    await tester.pumpWidget(_bar(textScale: 1.5));
    await tester.pumpAndSettle();
    expect(tester.widget<Text>(_titleText).maxLines, 2);
    expect(
      tester.getSize(find.byKey(const Key('title'))).height,
      greaterThan(one),
    );
  });

  testWidgets('tapping the one-line title shows the full title', (
    tester,
  ) async {
    await tester.pumpWidget(_bar());
    await tester.tap(_titleText);
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text(_long), findsNWidgets(2));
  });

  testWidgets('the collapse button is absent when nothing is open', (
    tester,
  ) async {
    await tester.pumpWidget(_bar(collapseVisible: false));
    expect(find.byKey(_collapse), findsNothing);
    await tester.pumpWidget(_bar());
    await tester.pumpAndSettle();
    expect(find.byKey(_collapse), findsOneWidget);
  });

  testWidgets('the collapse button fades in and out over a short while', (
    tester,
  ) async {
    await tester.pumpWidget(_bar(collapseVisible: false));
    await tester.pumpWidget(_bar());
    await tester.pump(const Duration(milliseconds: 60));
    final mid = tester.widget<Opacity>(
      find
          .ancestor(of: find.byKey(_collapse), matching: find.byType(Opacity))
          .first,
    );
    expect(mid.opacity, inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    await tester.pumpWidget(_bar(collapseVisible: false));
    await tester.pump(const Duration(milliseconds: 60));
    expect(find.byKey(_collapse), findsOneWidget);
    await tester.pumpAndSettle();
    expect(find.byKey(_collapse), findsNothing);
  });

  testWidgets('the ring accessory is not built until shown, then fades in', (
    tester,
  ) async {
    await tester.pumpWidget(_bar(ringShown: false));
    expect(find.byKey(_ring), findsNothing);
    await tester.pumpWidget(_bar());
    await tester.pump(const Duration(milliseconds: 60));
    final fade = tester.widget<Opacity>(
      find
          .ancestor(of: find.byKey(_ring), matching: find.byType(Opacity))
          .first,
    );
    expect(fade.opacity, inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    expect(find.byKey(_ring), findsOneWidget);
  });

  testWidgets('reduced motion: parts come and go at once', (tester) async {
    await tester.pumpWidget(
      _bar(ringShown: false, collapseVisible: false, reduced: true),
    );
    await tester.pumpWidget(_bar(reduced: true));
    await tester.pump();
    expect(find.byKey(_ring), findsOneWidget);
    expect(find.byKey(_collapse), findsOneWidget);
    for (final b in tester.widgetList<TweenAnimationBuilder<double>>(
      find.byType(TweenAnimationBuilder<double>),
    )) {
      expect(b.duration, Duration.zero);
    }
    for (final s in tester.widgetList<AnimatedSwitcher>(
      find.byType(AnimatedSwitcher),
    )) {
      expect(s.duration, Duration.zero);
    }
    for (final r in tester.widgetList<AnimatedRotation>(
      find.byType(AnimatedRotation),
    )) {
      expect(r.duration, Duration.zero);
    }
  });

  testWidgets('the project line opens its menu and Escape closes it', (
    tester,
  ) async {
    // Concept C (owner 20 Oct 2026): the line is quiet words, a place,
    // with no glyph and no chevron.
    await tester.pumpWidget(_bar());
    expect(find.byType(AnimatedRotation), findsNothing);
    await tester.tap(find.byKey(const Key('chip')));
    await tester.pumpAndSettle();
    expect(find.text('Files'), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.text('Files'), findsNothing);
  });

  testWidgets('the server name joins the line, and leaves it', (tester) async {
    await tester.pumpWidget(_bar());
    expect(find.textContaining('Laptop'), findsNothing);
    await tester.pumpWidget(_bar(server: 'Laptop'));
    await tester.pump(const Duration(milliseconds: 60));
    expect(find.textContaining('Laptop'), findsOneWidget);
    await tester.pumpAndSettle();
    await tester.pumpWidget(_bar());
    await tester.pumpAndSettle();
    expect(find.textContaining('Laptop'), findsNothing);
  });

  testWidgets('a new title crosses over from the old one', (tester) async {
    await tester.pumpWidget(_bar(title: 'New chat'));
    await tester.pumpWidget(_bar(title: _long));
    await tester.pump(const Duration(milliseconds: 60));
    expect(find.text('New chat'), findsOneWidget);
    expect(_titleText, findsOneWidget);
    await tester.pumpAndSettle();
    expect(find.text('New chat'), findsNothing);
  });

  testWidgets('the project line starts on the title edge, with room above', (
    tester,
  ) async {
    await tester.pumpWidget(_bar());
    final title = tester.getRect(find.byKey(const Key('title')));
    final line = tester.getRect(find.textContaining('FinanceHub3'));
    expect(line.left, closeTo(title.left, 1));
    // Under the status bar the title's words sit where other pages' titles
    // do: on the buttons' line, with room above.
    final words = tester.getRect(find.text(_long));
    expect(words.top, greaterThanOrEqualTo(10));
  });
}
