// Servers in a browser: Codex and Paseo cannot be reached (their WebSocket
// upgrade needs a header a page cannot send), a plain-http network origin
// cannot keep a password or read the clipboard, and the app root never starts
// a connection that is certain to fail. Each limit says why, in the person's
// words, where the person meets it.
//
// Everything runs on the VM under a pinned `PlatformCapabilities.browser()`.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';

final _en = lookupAppLocalizations(const Locale('en'));

void inBrowser({bool secureContext = true}) {
  debugPlatformCapabilities = PlatformCapabilities.browser(
    secureContext: secureContext,
  );
  addTearDown(() => debugPlatformCapabilities = null);
}

/// Presents [seeded] without touching the real secure-storage channel, which
/// is unmocked in widget tests and would hang a real upsert.
class _Store extends ProfileStore {
  _Store({required super.prefs, this.seeded = const []});

  final List<ServerProfile> seeded;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);

  @override
  String? get activeId => seeded.isEmpty ? null : seeded.first.id;

  @override
  Future<String?> secureStorageProblem() async => null;
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  int connectCalls = 0;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connectCalls++;
  }
}

ServerProfile _agentSocket(ServerBackend backend) => ServerProfile(
  id: 'agent-1',
  name: 'Agent box',
  baseUrl: 'ws://100.64.0.9:4141',
  backend: backend,
  codexDirectory: '/work/project',
);

Future<_Connection> _pump(
  WidgetTester tester, {
  List<ServerProfile> seeded = const [],
  Widget home = const ServersScreen(),
}) async {
  tester.view.physicalSize = const Size(1200, 2600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    seeded: seeded,
  );
  final connection = _Connection(store);
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(connection),
      ],
      child: home is ServersScreen
          ? MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: home,
            )
          : home,
    ),
  );
  await tester.pumpAndSettle();
  return connection;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Unmocked flutter_secure_storage reads hang inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('Add server in a browser', () {
    testWidgets('Codex and Paseo are disabled with the reason, not hidden', (
      tester,
    ) async {
      inBrowser();
      await _pump(tester);
      await chooseComputerOnWelcome(tester);

      expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('server-backend-opencode')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-backend-codex')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-backend-paseo')),
        findsOneWidget,
      );
      // One reason on each of the two disabled choices.
      expect(find.text(_en.browserAgentSocketUnavailable), findsNWidgets(2));

      // Choosing one does nothing: the flow stays on its first step.
      await tester.tap(find.byKey(const ValueKey('server-backend-codex')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('on a native build the same choices stay live', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = null);
      await _pump(tester);
      await chooseComputerOnWelcome(tester);

      expect(find.text(_en.browserAgentSocketUnavailable), findsNothing);
      await tester.tap(find.byKey(const ValueKey('server-backend-codex')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('server-kind-step')), findsNothing);
    });

    testWidgets('a plain-http origin says before a password is typed, and '
        'swaps Paste for guidance', (tester) async {
      inBrowser(secureContext: false);
      await _pump(tester);
      await chooseComputerOnWelcome(tester);
      await chooseServerKind(tester);

      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-secure-storage-notice')),
          matching: find.text(_en.browserEditorInsecureOrigin),
        ),
        findsOneWidget,
      );
      // The pairing block points at keyboard paste; no button that fails.
      expect(
        find.byKey(const ValueKey('server-pairing-paste-unavailable')),
        findsOneWidget,
      );
      expect(find.text(_en.browserPairingPasteHint), findsOneWidget);
      expect(find.byKey(const ValueKey('server-pairing-paste')), findsNothing);
      expect(find.byKey(const ValueKey('server-pairing-scan')), findsNothing);

      // The secret field loses its Paste button too; the reveal stays.
      await openServerManualAddress(tester);
      expect(find.byKey(const ValueKey('server-password-paste')), findsNothing);
      expect(
        find.byKey(const ValueKey('server-password-visibility')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('a secure origin keeps the Paste buttons and shows no notice', (
      tester,
    ) async {
      inBrowser();
      await _pump(tester);
      await chooseComputerOnWelcome(tester);
      await chooseServerKind(tester);

      expect(
        find.byKey(const ValueKey('server-secure-storage-notice')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('server-pairing-paste-unavailable')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('server-pairing-paste')),
        findsOneWidget,
      );
      await openServerManualAddress(tester);
      expect(
        find.byKey(const ValueKey('server-password-paste')),
        findsOneWidget,
      );
    });
  });

  group('a saved agent-socket server in a browser', () {
    for (final backend in [ServerBackend.codex, ServerBackend.paseo]) {
      testWidgets('${backend.name}: the row says why, and Connect explains', (
        tester,
      ) async {
        inBrowser();
        final connection = await _pump(tester, seeded: [_agentSocket(backend)]);

        expect(
          find.textContaining(
            _en.browserAgentSocketRowUnavailable,
            findRichText: true,
          ),
          findsWidgets,
        );

        await tester.tap(find.byKey(const ValueKey('server-row-menu-agent-1')));
        await tester.pumpAndSettle();
        await tester.tap(find.text(_en.e7SetupConnect).last);
        await tester.pumpAndSettle();

        expect(find.text(_en.browserAgentSocketUnavailable), findsOneWidget);
        expect(connection.connectCalls, 0, reason: 'nothing was attempted');
        expect(find.byType(ServersScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      });
    }

    testWidgets('on a native build the row carries no browser line', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = null);
      await _pump(tester, seeded: [_agentSocket(ServerBackend.codex)]);
      expect(
        find.textContaining(
          _en.browserAgentSocketRowUnavailable,
          findRichText: true,
        ),
        findsNothing,
      );
    });
  });

  group('the app root in a browser', () {
    for (final backend in [ServerBackend.codex, ServerBackend.paseo]) {
      testWidgets('never auto-connects a saved ${backend.name} server', (
        tester,
      ) async {
        inBrowser();
        final connection = await _pump(
          tester,
          seeded: [_agentSocket(backend)],
          home: const OcApp(),
        );
        // Let the first frames and the post-frame connect hook run.
        await tester.pump(const Duration(milliseconds: 100));

        expect(connection.connectCalls, 0);
        expect(find.byType(ServersScreen), findsOneWidget);
        expect(find.byType(HomeScreen), findsNothing);
        expect(
          find.textContaining(
            _en.browserAgentSocketRowUnavailable,
            findRichText: true,
          ),
          findsWidgets,
        );
        expect(tester.takeException(), isNull);
      });
    }
  });
}
