// shared-servers-1 (wave 2a): the server switcher, the other-servers panel
// and Claude Code's row on this phone, rebuilt from kit parts, with the map
// records' missing states and actions (server-switcher-sheet,
// embedded-local-agent-server-entry). Behaviour only: what the person sees
// and what is sent.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/profile_monitor.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/local_agent_runtime.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_task_mark.dart';
import 'package:opencode_mobile/ui/widgets/local_agent_server_entry.dart';
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
      controller.api = OpenCodeApi(baseUrl: controller.profile!.baseUrl);
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
      expect(
        find.byKey(const ValueKey('kit-row-current-mark')),
        findsOneWidget,
      );
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
              .toPlainText();

      expect(text('laptop'), 'Needs you · 1 working');
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-switcher-profile-laptop')),
          matching: find.byWidgetPredicate(
            (w) => w is KitTaskMark && w.state == KitTaskState.needsYou,
          ),
        ),
        findsOneWidget,
      );
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

  group('Claude Code on this phone', () {
    Future<FakeAgentRuntime> pump(
      WidgetTester tester,
      LocalAgentStatus status, {
      List<ServerProfile> profiles = const [],
      VoidCallback? onManage,
    }) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final runtime = FakeAgentRuntime(status);
      await tester.pumpWidget(
        _app(
          LocalAgentServerEntry(
            profiles: profiles,
            busy: false,
            revision: 0,
            runtime: runtime,
            onConnect: (_) {},
            onManage: onManage ?? () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      return runtime;
    }

    Future<void> openMenu(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('local-agent-server-menu')));
      await tester.pumpAndSettle();
    }

    testWidgets('signed out: the line says so and the menu signs in through '
        'Termux', (tester) async {
      final runtime = await pump(
        tester,
        agentStatus(LocalAgentPhase.ready, signedIn: LocalAgentSignIn.no),
      );
      expect(
        find.text(_l10n.localAgentEntrySignedOut(_l10n.phoneServerCardRunning)),
        findsOneWidget,
      );
      await openMenu(tester);
      await tester.tap(
        find.byKey(const ValueKey('local-agent-server-sign-in')),
      );
      await tester.pumpAndSettle();
      expect(runtime.calls, ['signin']);
    });

    testWidgets('signed in: no sign-in entry', (tester) async {
      await pump(tester, agentStatus(LocalAgentPhase.ready));
      expect(find.text(_l10n.phoneServerCardRunning), findsOneWidget);
      await openMenu(tester);
      expect(
        find.byKey(const ValueKey('local-agent-server-sign-in')),
        findsNothing,
      );
    });

    testWidgets('a start past 8 s says it is still starting', (tester) async {
      final runtime = await pump(tester, agentStatus(LocalAgentPhase.installed))
        ..holdStart();
      await tester.tap(find.byKey(const ValueKey('local-agent-server-start')));
      await tester.pump();
      expect(find.text(_l10n.phoneServerCardStarting), findsOneWidget);
      await tester.pump(const Duration(seconds: 9));
      expect(find.text(_l10n.localAgentEntryStillStarting), findsOneWidget);
      runtime.releaseStart(agentStatus(LocalAgentPhase.ready));
      await tester.pumpAndSettle();
      expect(find.text(_l10n.phoneServerCardRunning), findsOneWidget);
    });

    testWidgets('a failed start says it did not start, with the reason, and '
        'a tap tries again', (tester) async {
      final runtime = await pump(tester, agentStatus(LocalAgentPhase.installed))
        ..startFailure = const LocalAgentFailure(
          LocalAgentFailureKind.portInUse,
          'port busy',
        );
      await tester.tap(find.byKey(const ValueKey('local-agent-server-start')));
      await tester.pumpAndSettle();
      expect(find.text(_l10n.localAgentEntryDidNotStart), findsOneWidget);
      expect(
        find.text(
          _l10n.localAgentCardActionFailed(_l10n.localAgentFailedPortInUse),
        ),
        findsOneWidget,
      );
      runtime.startFailure = null;
      await tester.tap(find.byKey(const ValueKey('local-agent-server-start')));
      await tester.pumpAndSettle();
      expect(runtime.calls, ['start', 'start']);
    });

    testWidgets('Remove confirms first, then removes', (tester) async {
      final runtime = await pump(
        tester,
        agentStatus(LocalAgentPhase.installed),
      );
      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('local-agent-server-remove')));
      await tester.pumpAndSettle();
      expect(find.text(_l10n.localAgentRemoveTitle), findsOneWidget);
      await tester.tap(find.text(_l10n.localAgentRemoveKeep));
      await tester.pumpAndSettle();
      expect(runtime.calls, isEmpty);

      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('local-agent-server-remove')));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_l10n.localAgentRemove).last);
      await tester.pumpAndSettle();
      expect(runtime.calls, ['remove']);
    });

    testWidgets('an older Paseo offers Update, which opens the page where '
        'updating shows its progress', (tester) async {
      var managed = 0;
      await pump(
        tester,
        agentStatus(LocalAgentPhase.ready, paseo: '0.0.1'),
        onManage: () => managed++,
      );
      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('local-agent-server-update')));
      await tester.pumpAndSettle();
      expect(managed, 1);
    });

    testWidgets('Termux not answering keeps a saved server as Not answering '
        'with Try again', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final runtime = FakeAgentRuntime(agentStatus(LocalAgentPhase.ready))
        ..statusFailure = const LocalAgentFailure(
          LocalAgentFailureKind.bridge,
          'no answer',
        );
      await tester.pumpWidget(
        _app(
          LocalAgentServerEntry(
            profiles: [savedAgentProfile()],
            busy: false,
            revision: 0,
            runtime: runtime,
            onConnect: (_) {},
            onManage: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_l10n.phoneServerRowNotAnswering), findsOneWidget);
      await openMenu(tester);
      expect(
        find.byKey(const ValueKey('local-agent-server-recheck')),
        findsOneWidget,
      );
    });
  });
}
