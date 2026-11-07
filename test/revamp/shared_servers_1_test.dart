// shared-servers-1 (wave 2a): the server switcher, the other-servers panel
// rebuilt from kit parts, with the map
// records' missing states and actions (server-switcher-sheet). Behaviour only: what the person sees
// and what is sent.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/profile_monitor.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'shared_servers_1_fixtures.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: home),
);

Finder _rich(String text) => find.textContaining(text, findRichText: true);

void main() {
  tearDown(() => debugPlatformCapabilities = null);

  group('server switcher', () {
    Future<SwitcherController> open(
      WidgetTester tester, {
      Map<String, ProfileAttentionSnapshot> snapshots = const {},
      StreamStatus status = StreamStatus.connected,
    }) async {
      SharedPreferences.setMockInitialValues({});
      final controller = SwitcherController(
        SwitcherStore(prefs: await SharedPreferences.getInstance()),
        snapshots: snapshots,
      )..status = status;
      controller.api = FakeServerGateway(baseUrl: controller.profile!.baseUrl);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Center(
              child: TextButton(
                onPressed: () => showServerSwitcher(context, controller),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      return controller;
    }

    testWidgets('is titled Servers with one current mark and the saved '
        'servers after it', (tester) async {
      await open(tester);
      expect(find.text(_l10n.serverSwitcherTitle), findsOneWidget);
      final current = find.byKey(const ValueKey('server-switcher-current'));
      expect(
        find.descendant(of: current, matching: _rich('Connected')),
        findsOneWidget,
      );
      // The one in use gets the accent check, never a filled tile (vision
      // 2026-10-20: a selected row is a check, not a solid fill).
      expect(
        find.descendant(
          of: current,
          matching: find.byKey(const ValueKey('server-switcher-check')),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('kit-row-current-mark')), findsNothing);
      // One unlabelled panel (R1): no "Saved servers" header splits it.
      expect(find.text(_l10n.activitySavedServers), findsNothing);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-switcher-saved')),
          matching: current,
        ),
        findsOneWidget,
      );
      for (final id in ['laptop', 'lab', 'locked']) {
        expect(
          find.byKey(ValueKey('server-switcher-profile-$id')),
          findsOneWidget,
        );
      }
    });

    testWidgets('other servers say Needs you, how many run, or that the '
        'password is needed again', (tester) async {
      await open(
        tester,
        snapshots: {
          'laptop': snapshot('laptop', waiting: 1, running: 1),
          'lab': snapshot('lab', running: 2),
          // A stale snapshot says nothing rather than something old.
          'locked': snapshot('locked', running: 3, current: false),
        },
      );
      Finder line(String id) =>
          find.byKey(ValueKey('server-switcher-profile-$id-status'));
      String text(String id) =>
          (tester
                      .widget<RichText>(
                        find
                            .descendant(
                              of: line(id),
                              matching: find.byType(RichText),
                            )
                            .first,
                      )
                      .text
                  as TextSpan)
              .toPlainText()
              // Each part keeps its words together with no-break spaces.
              .replaceAll('\u00a0', ' ');

      expect(text('laptop'), 'Needs you · 1 working');
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-switcher-profile-laptop')),
          matching: find.byKey(const ValueKey('kit-row-icon-needs-you')),
        ),
        findsOneWidget,
      );
      // The monitor keeps no command and no way to reply: the row opens
      // the server instead of offering Allow once.
      expect(
        find.byKey(const ValueKey('server-switcher-open-laptop')),
        findsOneWidget,
      );
      expect(find.byKey(const Key('permission-card-allow')), findsNothing);
      expect(text('lab'), '2 working');
      expect(text('locked'), startsWith(_l10n.e7SetupPasswordRequired));
      expect(text('locked'), isNot(contains('working')));
      // The name identifies the server; no address to cut off (R5).
      expect(text('lab'), isNot(contains('https://lab.example.test')));
    });

    testWidgets('the one list is ordered by urgency: needs you, working, '
        'then the rest', (tester) async {
      await open(
        tester,
        snapshots: {
          'laptop': snapshot('laptop', running: 1),
          'lab': snapshot('lab'),
          'locked': snapshot('locked', waiting: 1),
        },
      );
      double top(String key) => tester.getTopLeft(find.byKey(ValueKey(key))).dy;
      final order = [
        top('server-switcher-current'),
        top('server-switcher-profile-locked'),
        top('server-switcher-profile-laptop'),
        top('server-switcher-profile-lab'),
      ];
      expect(order, [...order]..sort());
    });

    testWidgets('the current row opens its menu, and Disconnect there '
        'confirms in place before leaving', (tester) async {
      final controller = await open(tester);
      expect(find.text(_l10n.e7SettingsUi8), findsNothing);
      await tester.tap(find.byKey(const ValueKey('server-switcher-current')));
      await tester.pumpAndSettle();
      // It names what it leaves (R2).
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-switcher-disconnect')),
          matching: find.textContaining('Disconnect from '),
        ),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('server-switcher-disconnect')),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('disconnect-confirm-sheet')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('confirm-disconnect')));
      await tester.pumpAndSettle();
      // The sheet only chooses; the shell leaves after it closed.
      expect(find.byKey(const ValueKey('server-switcher-sheet')), findsNothing);
      expect(controller.disconnects, 0);
    });

    testWidgets('while reconnecting the current line says so, not Connected', (
      tester,
    ) async {
      await open(tester, status: StreamStatus.reconnecting);
      expect(_rich('Reconnecting'), findsOneWidget);
      expect(_rich('Connected'), findsNothing);
      // The one shared grace period (3d64653c): once it runs out the line
      // says Offline, as the shell's server pill does.
      await tester.pump(const Duration(seconds: 8));
      expect(_rich('Reconnecting'), findsNothing);
      expect(_rich('Offline'), findsOneWidget);
    });
  });
}
