// Behaviour of the Servers, "On this phone" and Plugins cleanup
// (docs/design/phone-server-screens-cleanup-2026-09-24.md), in the states
// the owner saw on his phone (test/support/phone_server_scenes.dart):
//
// - the phone's server is one row on Servers, not a card, a saved row and a
//   health block;
// - the server in use is marked in its row;
// - This phone puts its status action before management, and Stop asks first;
// - crash recovery lives on On this phone, not on the servers list;
// - Plugins shows plain names, the raw id only under Details, and folds the
//   built-in plugins into one row.
//
// Each of these fails on the code before the cleanup (df81ce51).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'support/phone_server_scenes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<Future<void> Function()> mount(
    WidgetTester tester,
    PhoneServerScene scene,
  ) =>
      mountPhoneServerScene(tester, scene, light: false, boundary: GlobalKey());

  group('Servers', () {
    testWidgets('the phone server appears once, as one row', (tester) async {
      final done = await mount(tester, PhoneServerScene.servers);
      expect(tester.takeException(), isNull);
      // One row named "Termux" (where it runs) with what it
      // runs and where it stands.
      expect(find.text('Termux'), findsOneWidget);
      expect(find.text('OpenCode 2 · Running'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('termux-running-server')),
        findsOneWidget,
      );
      // Not again as the saved sign-in it is reached through, nor as a
      // health block with its own controls.
      expect(find.text(phoneProfile().name), findsNothing);
      expect(find.textContaining('127.0.0.1'), findsNothing);
      expect(find.text('Server found on this phone'), findsNothing);
      expect(find.text('On-device server'), findsNothing);
      // No big buttons in the list: Connect is the row, Restart and Stop
      // are in its menu.
      expect(find.byType(FilledButton), findsOneWidget); // Add server
      expect(find.text('Restart'), findsNothing);
      expect(find.text('Stop'), findsNothing);
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-menu')),
      );
      await tester.pumpAndSettle();
      for (final label in ['Restart', 'Stop', 'Details']) {
        expect(find.text(label), findsOneWidget, reason: label);
      }
      await tester.tapAt(const Offset(5, 5));
      await tester.pumpAndSettle();
      await done();
    });

    testWidgets('the server in use carries the current mark', (tester) async {
      final done = await mount(tester, PhoneServerScene.servers);
      final studio = find.byKey(const ValueKey('server-row-studio'));
      final phone = find.byKey(const ValueKey('termux-running-server'));
      final mark = find.byKey(const ValueKey('kit-row-current-mark'));
      expect(find.descendant(of: studio, matching: mark), findsOneWidget);
      expect(find.descendant(of: phone, matching: mark), findsNothing);
      expect(
        find.descendant(
          of: studio,
          matching: find.textContaining('Connected · '),
        ),
        findsOneWidget,
      );
      // Said to a screen reader too, not only drawn.
      final semantics = tester.ensureSemantics();
      await tester.pump();
      expect(tester.getSemantics(studio), isSemantics(isSelected: true));
      expect(tester.getSemantics(phone), isNot(isSemantics(isSelected: true)));
      semantics.dispose();
      await done();
    });

    testWidgets('restarting after a crash is not configured on the list', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.servers);
      await tester.drag(find.byType(Scrollable).first, const Offset(0, -900));
      await tester.pump();
      expect(find.byType(Switch), findsNothing);
      expect(find.textContaining('Attempts used'), findsNothing);
      await done();
    });
  });

  group('Server switcher', () {
    // The owner's phone, 2026-09-25: the sheet showed the saved sign-in
    // "This device (Termux) · Connected" and, under it, "This phone ·
    // Connected · OpenCode 2 · Running": one server, twice.
    testWidgets('the phone server in use appears once, named with Termux', (
      tester,
    ) async {
      final done = await mountPhoneServerSwitcher(tester);
      expect(tester.takeException(), isNull);
      final sheet = find.byKey(const ValueKey('server-switcher-sheet'));
      expect(
        find.descendant(of: sheet, matching: find.text('Termux')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: sheet, matching: find.text(phoneProfile().name)),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('server-switcher-current')),
        findsNothing,
      );
      // The one row says it is the server in use.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('termux-running-server')),
          matching: find.textContaining('Connected'),
        ),
        findsOneWidget,
      );
      // The other saved server is still offered.
      expect(
        find.descendant(of: sheet, matching: find.text('Studio Mac')),
        findsOneWidget,
      );
      await done();
    });
  });

  group('On this phone', () {
    testWidgets('one status row, Stop before management, and Stop confirms', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.phoneRunning);
      // P1.5 replaced the old Termux screen with This phone: runtime is
      // the title, the version/host are its detail, and state has one word.
      expect(find.text('In-app Ubuntu'), findsOneWidget);
      expect(find.text('OpenCode 2'), findsOneWidget);
      expect(find.text('Running'), findsOneWidget);
      expect(
        find.textContaining('2.0.10 · In Termux', findRichText: true),
        findsOneWidget,
      );
      final management = find.byKey(const ValueKey('this-phone-list'));
      final stop = find.byKey(const ValueKey('this-phone-stop'));
      // P1.5 keeps the current action before management. This fixture has
      // the pinned version, so there is no redundant Update action.
      expect(find.byKey(const ValueKey('this-phone-update')), findsNothing);
      expect(
        tester.getTopLeft(management).dy,
        greaterThanOrEqualTo(tester.getBottomLeft(stop).dy),
      );

      await tester.tap(stop);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('stop-local-server-confirm-sheet')),
        findsOneWidget,
      );
      await tester.tap(find.text('Keep running'));
      await tester.pumpAndSettle();
      expect(find.text('Running'), findsOneWidget);
      expect(stop, findsOneWidget);
      await done();
    });

    testWidgets('restarting after a crash and Claude Code are rows here', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.phoneRunning);
      final recovery = find.byKey(const ValueKey('managed-recovery-switch'));
      await tester.scrollUntilVisible(recovery, 200);
      expect(recovery, findsOneWidget);
      final claude = find.byKey(const ValueKey('local-agent-row'));
      await tester.scrollUntilVisible(claude, 200);
      expect(
        find.descendant(of: claude, matching: find.text('Optional')),
        findsOneWidget,
      );
      await tester.tap(claude);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('local-agent-block')), findsOneWidget);
      await done();
    });

    testWidgets('stopped, the same place offers Start first', (tester) async {
      final done = await mount(tester, PhoneServerScene.phoneStopped);
      expect(find.text('Stopped'), findsOneWidget);
      final start = find.byKey(const ValueKey('this-phone-start'));
      final management = find.byKey(const ValueKey('this-phone-list'));
      expect(start, findsOneWidget);
      expect(find.byKey(const ValueKey('this-phone-update')), findsNothing);
      expect(
        tester.getTopLeft(management).dy,
        greaterThan(tester.getBottomLeft(start).dy),
      );
      expect(find.byKey(const ValueKey('this-phone-stop')), findsNothing);
      await done();
    });
  });

  group('Plugins', () {
    testWidgets('rows show plain names; the raw id is under Details', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.plugins);
      expect(find.textContaining('opencode.'), findsNothing);
      expect(find.text('Wakatime'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('plugins-builtin-group')));
      await tester.pumpAndSettle();
      for (final name in ['Input repair', 'Worktree', 'Browser', 'MCP']) {
        expect(find.text(name), findsOneWidget, reason: name);
      }
      expect(find.textContaining('opencode.'), findsNothing);
      // No "Link commands" or "Built in" line in the rows.
      expect(find.text('Link commands'), findsNothing);
      expect(find.text('Clear personal command links'), findsNothing);

      await tester.tap(find.text('Input repair'));
      await tester.pumpAndSettle();
      final id = find.byKey(const ValueKey('plugin-details-id'));
      expect(id, findsOneWidget);
      expect(tester.widget<KitText>(id).text, 'opencode.tool.input.repair');
      await done();
    });

    testWidgets('built-in plugins fold into one row that opens', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.plugins);
      final group = find.byKey(const ValueKey('plugins-builtin-group'));
      expect(group, findsOneWidget);
      expect(
        find.descendant(of: group, matching: find.text('Built in')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: group, matching: find.text('7 active')),
        findsOneWidget,
      );
      // Folded: none of the seven is listed; the one the person added is.
      expect(find.text('Worktree'), findsNothing);
      expect(find.text('Wakatime'), findsOneWidget);
      await tester.tap(group);
      await tester.pumpAndSettle();
      expect(find.text('Worktree'), findsOneWidget);
      await done();
    });

    testWidgets('a plugin row opens its details; it has no menu', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.plugins);
      expect(
        find.byKey(const ValueKey('plugin-menu-@acme/opencode-wakatime')),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('plugins-section-menu')), findsNothing);
      await tester.tap(find.text('Wakatime'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('plugin-details-sheet')),
        findsOneWidget,
      );
      await done();
    });
  });
}
