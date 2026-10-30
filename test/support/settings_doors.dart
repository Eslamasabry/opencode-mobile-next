import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

/// The Settings hub's door to [group] (vision 2026-10-20: a search and five
/// one-word rows, each opening its group's page).
Finder settingsDoor(SettingsGroup group) =>
    find.byKey(ValueKey('settings-hub-row-${group.slug}'));

/// Taps the hub's door to [group] and waits for its page, the way a person
/// reaches a row that used to sit on the hub itself.
Future<void> openSettingsGroup(WidgetTester tester, SettingsGroup group) async {
  final door = settingsDoor(group);
  await tester.ensureVisible(door);
  await tester.pumpAndSettle();
  await tester.tap(door);
  await tester.pumpAndSettle();
}

/// What each group's page holds, read by opening every door the hub shows
/// and going back: the index ids of its rows (KitArrival) and how many
/// "not available on this server" lines it carries.
typedef SettingsPages = ({
  Map<SettingsGroup, List<String>> rows,
  Map<SettingsGroup, int> notes,
});

Future<SettingsPages> visitSettingsGroups(WidgetTester tester) async {
  final rows = <SettingsGroup, List<String>>{};
  final notes = <SettingsGroup, int>{};
  for (final group in SettingsGroup.values) {
    if (settingsDoor(group).evaluate().isEmpty) continue;
    await openSettingsGroup(tester, group);
    final page = find.byType(SettingsGroupScreen);
    expect(page, findsOneWidget, reason: group.slug);
    rows[group] = [
      for (final row in tester.widgetList<KitArrival>(
        find.descendant(of: page, matching: find.byType(KitArrival)),
      ))
        row.id,
    ];
    notes[group] = find
        .descendant(
          of: page,
          matching: find.byKey(const ValueKey('settings-unavailable')),
        )
        .evaluate()
        .length;
    Navigator.of(tester.element(page)).pop();
    await tester.pumpAndSettle();
  }
  return (rows: rows, notes: notes);
}

/// Every row id [pages] saw, on any group's page.
Set<String> settingsRowIds(SettingsPages pages) => {
  for (final ids in pages.rows.values) ...ids,
};

/// Goes back from the open group page to the hub.
Future<void> closeSettingsGroup(WidgetTester tester) async {
  Navigator.of(tester.element(find.byType(SettingsGroupScreen))).pop();
  await tester.pumpAndSettle();
}

/// Makes the Settings row [id] reachable the way a person would: if it is
/// not on screen, back to the hub and through the door of the group that
/// holds it.
Future<void> revealSettingsRow(WidgetTester tester, String id) async {
  final row = find.byKey(ValueKey(id));
  if (row.evaluate().isNotEmpty) return;
  final group = SettingsGroup.values.firstWhere(
    (group) => settingsGroupRowIds[group]!.contains(id),
  );
  if (find.byType(SettingsGroupScreen).evaluate().isNotEmpty) {
    await closeSettingsGroup(tester);
  }
  await openSettingsGroup(tester, group);
}
