// Behaviour of the Servers, "On this phone" and Plugins cleanup
// (docs/design/phone-server-screens-cleanup-2026-09-24.md), in the states
// the owner saw on his phone (test/support/phone_server_scenes.dart):
//
// - a server saved at Termux's old address is an ordinary saved row;
// - the server in use is marked in its row;
// - crash recovery is not configured on the servers list;
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
    testWidgets('a server saved for Termux is an ordinary saved row', (
      tester,
    ) async {
      final done = await mount(tester, PhoneServerScene.servers);
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('server-row-phone')), findsOneWidget);
      expect(find.text(phoneProfile().name), findsOneWidget);
      // No Termux row, and nothing that runs anything in Termux.
      expect(find.text('Termux'), findsNothing);
      expect(find.byKey(const ValueKey('termux-running-server')), findsNothing);
      await done();
    });

    testWidgets('the server in use carries the current mark', (tester) async {
      final done = await mount(tester, PhoneServerScene.servers);
      final studio = find.byKey(const ValueKey('server-row-studio'));
      final phone = find.byKey(const ValueKey('server-row-phone'));
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
