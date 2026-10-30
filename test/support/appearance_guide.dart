// Moving around the guided Settings › Appearance page in widget tests: its
// four chips open their questions, and a question's control may sit below
// the fold.
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

/// The page's scroll view (the question's, or the whole page at large text).
Finder appearanceScroll() => find
    .descendant(
      of: find.byKey(const ValueKey('appearance-settings')),
      matching: find.byType(Scrollable),
    )
    .first;

/// Taps the [group] chip (colours, style, background, comfort) and then,
/// when given, the [question] chip inside it. On the returning person's one
/// page, where every question is already open, it scrolls to the question
/// (by default the group's first) instead.
Future<void> openAppearance(
  WidgetTester tester,
  String group, [
  String? question,
]) async {
  if (find.byKey(const ValueKey('appearance-one-page')).evaluate().isNotEmpty) {
    final first = switch (group) {
      'colours' => 'mode',
      'style' => 'style',
      'background' => 'look',
      _ => 'text',
    };
    await bringIntoView(
      tester,
      find.byKey(ValueKey('appearance-one-${question ?? first}')),
    );
    return;
  }
  final chip = find.byKey(ValueKey('appearance-group-$group'));
  await bringIntoView(tester, chip);
  await tester.tap(chip);
  await tester.pumpAndSettle();
  if (question != null) {
    final inner = find.byKey(ValueKey('appearance-question-$question'));
    await bringIntoView(tester, inner);
    await tester.tap(inner);
    await tester.pumpAndSettle();
  }
}

/// Scrolls the page until [finder] is in view.
Future<void> bringIntoView(WidgetTester tester, Finder finder) async {
  if (finder.evaluate().isEmpty) {
    await tester.scrollUntilVisible(
      finder,
      150,
      scrollable: appearanceScroll(),
    );
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
}

/// Opens every theme ("See all") on the Theme question.
Future<void> seeAllThemes(WidgetTester tester) async {
  final all = find.byKey(const ValueKey('appearance-see-all-themes'));
  await bringIntoView(tester, all);
  await tester.tap(all);
  await tester.pumpAndSettle();
}
