// Behaviour of shared-phone-1's rebuilt parts (wave 2a): "This phone"
// (PhoneServerCard) with its own Disconnect, the setup log (SetupTerminal
// on KitLogPanel) and the phone server row's failure line and row menu.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_log_panel.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/widgets/local_server_row.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:opencode_mobile/ui/widgets/setup_terminal.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/fake_setup_engine.dart';
import 'shared_phone_1_fixtures.dart';

Widget app(Widget body, {List<Override> overrides = const []}) => ProviderScope(
  overrides: overrides,
  child: MaterialApp(
    locale: const Locale('en'),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: ListView(children: [body])),
  ),
);

/// The text of a [KitText] found by [key].
String kitText(WidgetTester tester, String key) =>
    tester.widget<KitText>(find.byKey(ValueKey(key))).text;

void main() {
  late PhoneStore store;
  late PhoneConnection connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = PhoneStore(prefs: await SharedPreferences.getInstance());
    connection = PhoneConnection(store);
    PhoneSetup.engine = FakeSetupEngine();
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('1.18.29');
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    connection.dispose();
  });

  group('This phone (PhoneServerCard)', () {
    Future<PhoneLinux> mount(
      WidgetTester tester, {
      bool connected = false,
      bool running = true,
      VoidCallback? onOpen,
      VoidCallback? onDisconnect,
    }) async {
      final linux = PhoneLinux(running: running);
      final profile = phoneProfile();
      store.saved.add(profile);
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        app(
          PhoneServerCard(
            connection: connection,
            profile: profile,
            connected: connected,
            onOpen: onOpen,
            onDisconnect: onDisconnect,
            linux: linux,
            pollInterval: null,
          ),
          overrides: [
            builtinLinuxProvider.overrideWithValue(linux),
            builtinServerStarterProvider.overrideWith((ref) {
              final starter = BuiltinServerStarter(
                linux: linux,
                pollInterval: Duration.zero,
              );
              ref.onDispose(starter.dispose);
              return starter;
            }),
          ],
        ),
      );
      await tester.pumpAndSettle();
      return linux;
    }

    Future<void> openMenu(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
    }

    testWidgets('in use: its menu disconnects from it by name', (tester) async {
      var left = 0;
      await mount(tester, connected: true, onDisconnect: () => left++);
      // The row says it is the one in use, in words and with the mark.
      expect(find.byKey(const ValueKey('kit-row-current-mark')), findsOne);
      expect(
        find.textContaining(l10n.serverRowConnected, findRichText: true),
        findsOneWidget,
      );
      await openMenu(tester);
      final item = find.byKey(const ValueKey('phone-server-disconnect'));
      expect(item, findsOneWidget);
      expect(
        // The default name is lower case inside the sentence.
        find.text(
          l10n.phoneServerCardDisconnect(l10n.phoneServerNameInSentence),
        ),
        findsOneWidget,
      );
      await tester.tap(item);
      await tester.pumpAndSettle();
      expect(left, 1);
    });

    testWidgets('not in use, or no host to leave through: no Disconnect', (
      tester,
    ) async {
      await mount(tester, onDisconnect: () {});
      await openMenu(tester);
      expect(
        find.byKey(const ValueKey('phone-server-disconnect')),
        findsNothing,
      );
      await tester.tapAt(Offset.zero);
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
      store.saved.clear();
      await mount(tester, connected: true);
      await openMenu(tester);
      expect(
        find.byKey(const ValueKey('phone-server-disconnect')),
        findsNothing,
      );
    });

    testWidgets('running, not in use: the row connects; no button repeats '
        'it, and Stop lives in the menu', (tester) async {
      var opened = 0;
      await mount(tester, onOpen: () => opened++);
      expect(
        kitText(tester, 'phone-server-status'),
        l10n.phoneServerCardRunning,
      );
      // Nothing shown twice: the row's tap is the one way to connect.
      expect(find.byKey(const ValueKey('phone-server-open')), findsNothing);
      expect(find.text(l10n.phoneServerCardStopOpenCode), findsNothing);
      await tester.tap(find.byKey(const ValueKey('phone-server-title')));
      await tester.pumpAndSettle();
      expect(opened, 1);
      await openMenu(tester);
      expect(find.byKey(const ValueKey('phone-server-stop')), findsOneWidget);
      expect(find.text(l10n.phoneServerCardStopOpenCode), findsOneWidget);
      expect(
        find.byKey(const ValueKey('phone-server-show-log')),
        findsOneWidget,
      );
    });

    testWidgets('stopped: Start OpenCode starts it', (tester) async {
      final linux = await mount(tester, running: false);
      expect(
        kitText(tester, 'phone-server-status'),
        l10n.phoneServerCardStopped,
      );
      await tester.tap(find.text(l10n.phoneServerCardStartOpenCode));
      await tester.pumpAndSettle();
      expect(linux.calls, contains('start'));
    });

    testWidgets('the server log opens in the one log view', (tester) async {
      await mount(tester);
      await openMenu(tester);
      await tester.tap(find.text(l10n.phoneServerCardShowServerLog));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('phone-server-log')), findsOneWidget);
      expect(find.byType(KitLogPanel), findsOneWidget);
      expect(find.text('opencode server listening'), findsOneWidget);
    });

    testWidgets('Remove asks first, names OpenCode, then removes', (
      tester,
    ) async {
      var removed = false;
      final linux = PhoneLinux();
      final profile = phoneProfile();
      store.saved.add(profile);
      await tester.pumpWidget(
        app(
          PhoneServerCard(
            connection: connection,
            profile: profile,
            linux: linux,
            pollInterval: null,
            onRemoved: () => removed = true,
          ),
          overrides: [
            builtinLinuxProvider.overrideWithValue(linux),
            builtinServerStarterProvider.overrideWith(
              (ref) => BuiltinServerStarter(linux: linux),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('phone-server-remove')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-server-remove-sheet')),
        findsOneWidget,
      );
      expect(linux.calls, isEmpty, reason: 'the question changes nothing');
      final confirm = find.byKey(const ValueKey('phone-server-remove-confirm'));
      expect(
        find.descendant(
          of: confirm,
          matching: find.text(l10n.removeFromPhoneKeepConfirm),
        ),
        findsOneWidget,
      );
      await tester.tap(confirm);
      await tester.pumpAndSettle();
      expect(linux.calls, ['uninstall']);
      expect(connection.deleted, ['phone']);
      expect(removed, isTrue);
      // No toast for a removal the list itself shows (KIT-34).
      expect(find.byType(SnackBar), findsNothing);
    });
  });

  group('Setup output (SetupTerminal)', () {
    Future<void> mount(
      WidgetTester tester, {
      required String output,
      bool running = false,
      VoidCallback? onCopy,
      String? copyTooltip,
    }) async {
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      await tester.pumpWidget(
        app(
          SetupTerminal(
            output: output,
            running: running,
            controller: scroll,
            onCopy: onCopy,
            copyTooltip: copyTooltip,
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('one log panel: control bytes gone, left to right', (
      tester,
    ) async {
      await mount(
        tester,
        output:
            '\x1B[32m[oc] Installing\x1B[0m\nnpm ERR! network\nwarning: slow',
      );
      expect(find.byKey(const ValueKey('setup-live-output')), findsOneWidget);
      expect(find.byType(KitLogPanel), findsOneWidget);
      expect(find.text(l10n.setupTerminalTitle), findsOneWidget);
      expect(find.text('[oc] Installing'), findsOneWidget);
      expect(find.textContaining('\x1B'), findsNothing);
      final body = tester.widget<Directionality>(
        find
            .ancestor(
              of: find.text('npm ERR! network'),
              matching: find.byType(Directionality),
            )
            .first,
      );
      expect(body.textDirection, TextDirection.ltr);
    });

    test('error and warning lines carry their level, the rest is plain', () {
      expect(SetupTerminal.levelOf('npm ERR! network'), KitLogLevel.error);
      expect(SetupTerminal.levelOf('[oc] failed to start'), KitLogLevel.error);
      expect(SetupTerminal.levelOf('warning: slow disk'), KitLogLevel.warning);
      expect(SetupTerminal.levelOf('[oc] setup complete'), KitLogLevel.normal);
    });

    testWidgets('running with no output says it is waiting', (tester) async {
      await mount(tester, output: '', running: true);
      expect(find.text(l10n.setupOutputWaiting), findsOneWidget);
    });

    testWidgets('a host report copy is its own named button', (tester) async {
      var copied = 0;
      await mount(
        tester,
        output: 'failed',
        onCopy: () => copied++,
        copyTooltip: l10n.e7SetupCopyFailureReport,
      );
      final report = find.byKey(const ValueKey('setup-copy-report'));
      expect(
        find.descendant(
          of: report,
          matching: find.text(l10n.e7SetupCopyFailureReport),
        ),
        findsOneWidget,
      );
      await tester.tap(report);
      expect(copied, 1);
    });

    testWidgets('without its words, the panel Copy all is the one copy', (
      tester,
    ) async {
      await mount(tester, output: 'done', onCopy: () {});
      expect(find.byKey(const ValueKey('setup-copy-report')), findsNothing);
      expect(find.byKey(const ValueKey('kit-log-copy-all')), findsOneWidget);
    });
  });

  group('a phone server row (LocalServerRow)', () {
    testWidgets('a failure is words in text1; long-press opens its menu', (
      tester,
    ) async {
      var restarted = 0;
      await tester.pumpWidget(
        app(
          KitRowGroup(
            children: [
              LocalServerRow(
                keyPrefix: 'row',
                title: 'This phone · Termux',
                status: 'OpenCode 2 · Running',
                connectedLabel: l10n.serverRowConnected,
                stopped: false,
                locked: false,
                inProgress: false,
                connected: false,
                menuTooltip: 'Server actions',
                menuItems: const [],
                startLabel: 'Start',
                restartLabel: 'Restart server',
                stopLabel: 'Stop server',
                failure: 'It did not answer',
                onRestart: () => restarted++,
                onStop: () {},
                onConnect: () {},
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      final failure = tester.widget<KitText>(
        find.byKey(const ValueKey('row-failure')),
      );
      expect(failure.tone, KitTextTone.primary);
      await tester.longPress(find.text('This phone · Termux'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('row-restart')));
      await tester.pumpAndSettle();
      expect(restarted, 1);
    });
  });
}
