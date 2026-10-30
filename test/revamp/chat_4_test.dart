// Unit chat-4, the command launcher (map page command-launcher-sheet,
// proposal redesign: a kit-only rebuild of today's layout). What the
// person sees: the sheet names the door that opened it, an action reads by
// its name first with its slash word as a trailing hint, no row carries the
// old "mobile" tag, and a search with no match says so with Clear search.
// The team conversation's own tests are in
// test/team_conversation_screen_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'chat_3_support.dart';
import '../support/composer_tools.dart';

Future<void> _openLauncher(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-commands')));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(chat3MockSecureStorage);

  testWidgets('the launcher names its door and reads actions by name', (
    tester,
  ) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _openLauncher(tester);

    expect(find.byKey(const Key('command-launcher-sheet')), findsOneWidget);
    expect(find.text('Commands and agents'), findsWidgets);
    // An action: its name, then its slash word as the typing hint.
    final row = find.byKey(const Key('command-mobile-new'));
    expect(row, findsOneWidget);
    expect(
      find.descendant(of: row, matching: find.text('New conversation')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row, matching: find.textContaining('/new')),
      findsOneWidget,
    );
    // The old tag meant nothing to a person.
    expect(find.text('mobile'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a search with no match says so, and Clear search brings the '
      'list back', (tester) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _openLauncher(tester);

    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'zzqx',
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('command-launcher-no-match')), findsOneWidget);
    expect(find.byKey(const Key('command-mobile-new')), findsNothing);

    await tester.tap(find.text('Clear search'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('command-launcher-no-match')), findsNothing);
    expect(find.byKey(const Key('command-mobile-new')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
