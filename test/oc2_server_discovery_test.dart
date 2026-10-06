import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_job_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import 'support/fake_setup_engine.dart';
import 'support/server_editor.dart';
import 'support/setup_capture_preferences.dart';
import 'support/voice_device_channel.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved});
  final List<ServerProfile> saved;
  @override
  List<ServerProfile> get profiles => saved;
  @override
  String? get activeId => null;
  @override
  Future<void> upsert(ServerProfile profile) async {
    saved.removeWhere((p) => p.id == profile.id);
    saved.add(profile);
  }
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
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }
}

Future<(_Store, _Connection)> _state(List<ServerProfile> profiles) async {
  final store = _Store(prefs: await setupCapturePreferences(), saved: profiles);
  return (store, _Connection(store));
}

Widget _app(_Store store, _Connection controller, {double scale = 1}) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(controller),
      ],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: child!,
        ),
        routes: {
          '/home': (_) => const Scaffold(body: Text('Connected')),
          '/this-phone': (context) => Scaffold(
            body: Text(
              'Termux route: ${ModalRoute.of(context)!.settings.arguments}',
            ),
          ),
        },
        home: const ServersScreen(),
      ),
    );

Future<void> _reveal(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    160,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
}

void main() {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    serverProbe = probeServerConnection;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
  });

  testWidgets(
    'saved labels distinguish confirmed generation from legacy default',
    (tester) async {
      final (store, conn) = await _state([
        ServerProfile(
          id: 'unknown',
          name: 'Unprobed',
          baseUrl: 'https://legacy.example',
        ),
        ServerProfile(
          id: 'v1',
          name: 'Computer',
          baseUrl: 'https://one.example',
          serverVersion: '1.18.25',
        ),
        ServerProfile(
          id: 'v2',
          name: 'Next',
          baseUrl: 'https://two.example',
          flavor: ServerFlavor.v2,
        ),
        ServerProfile(
          id: 'codex',
          name: 'Codex computer',
          baseUrl: 'wss://codex.example',
          backend: ServerBackend.codex,
          serverVersion: '1.0',
        ),
      ]);
      addTearDown(conn.dispose);
      await tester.pumpWidget(_app(store, conn));
      await tester.pumpAndSettle();
      for (final entry in {
        'unknown': 'OpenCode',
        'v1': 'OpenCode 1',
        'v2': 'OpenCode 2',
      }.entries) {
        // The generation leads the row's supporting line (standard §6).
        final finder = find.byKey(ValueKey('server-row-${entry.key}'));
        await _reveal(tester, finder);
        // The kind, never the address (that is in the row menu's Details).
        expect(_supporting(tester, entry.key), entry.value);
      }
      expect(_supporting(tester, 'codex'), isNot(contains('OpenCode')));
      expect(store.saved.first.flavor, ServerFlavor.v1);
    },
  );

  for (final flavor in [ServerFlavor.v1, ServerFlavor.v2]) {
    testWidgets('OpenCode 2 entry preserves actual $flavor autodetection', (
      tester,
    ) async {
      serverProbe = ({required baseUrl, username, password}) async =>
          ServerProbeResult.success('fixture-version', flavor: flavor);
      // First run no longer names product generations (UX plan 5.6); the
      // OpenCode 2 shortcut is offered beside the saved servers.
      final (store, conn) = await _state([
        ServerProfile(
          id: 'existing',
          name: 'Existing',
          baseUrl: 'https://other.example',
        ),
      ]);
      addTearDown(conn.dispose);
      await tester.pumpWidget(_app(store, conn));
      await tester.pumpAndSettle();
      // Add server's default choice pairs OpenCode 1 or 2 (R3: no separate
      // "Connect OpenCode 2" door on the list).
      expect(
        find.byKey(const ValueKey('connect-existing-opencode2')),
        findsNothing,
      );
      final entry = find.byKey(const ValueKey('servers-add'));
      await tester.tap(entry);
      await tester.pumpAndSettle();
      // The same autodetecting editor: OpenCode, 1 or 2, chosen.
      expect(find.text('OpenCode on a computer'), findsOneWidget);
      // The check after the address says which one it found; no line
      // promising it up front.
      expect(
        find.byKey(const ValueKey('opencode-autodetect-help')),
        findsNothing,
      );
      await openServerManualAddress(tester);
      final url = find.byKey(const ValueKey('server-url-field'));
      await _reveal(tester, url);
      await tester.enterText(url, 'https://existing.example');
      final test = find.byKey(const ValueKey('test-server-connection'));
      await _reveal(tester, test);
      await tester.tap(test);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();
      final added = store.saved.singleWhere((p) => p.id != 'existing');
      expect(added.flavor, flavor);
      expect(added.serverVersion, 'fixture-version');
    });
  }

  for (final mode in ['mixed-local', 'single-local', 'mixed-remote']) {
    testWidgets('$mode connection routes runtime choices only when needed', (
      tester,
    ) async {
      final url = mode == 'mixed-remote'
          ? 'https://work.example'
          : 'http://127.0.0.1:4096';
      final (store, conn) = await _state([
        ServerProfile(
          id: 'one',
          name: 'OpenCode one',
          baseUrl: url,
          serverVersion: '1.18.25',
        ),
        if (mode != 'single-local')
          ServerProfile(
            id: 'two',
            name: 'OpenCode two',
            baseUrl: url,
            flavor: ServerFlavor.v2,
          ),
      ]);
      await tester.pumpWidget(_app(store, conn));
      await tester.pumpAndSettle();
      // The phone's own server is the one "Termux" row; a remote one
      // keeps its saved name.
      await tester.tap(
        find.text(mode == 'mixed-remote' ? 'OpenCode one' : 'Termux'),
      );
      await tester.pumpAndSettle();
      expect(conn.connectCalls, mode == 'mixed-local' ? 0 : 1);
      expect(
        find.text('Termux route: PhoneHostKind.termux'),
        mode == 'mixed-local' ? findsOneWidget : findsNothing,
      );
      expect(store.saved.first.flavor, ServerFlavor.v1);
      if (mode != 'single-local') {
        expect(store.saved.last.flavor, ServerFlavor.v2);
      }
      // The connection's server monitor keeps a refresh timer; the tree goes
      // first, then the connection, before the test's timer check.
      await tester.pumpWidget(const SizedBox());
      conn.dispose();
    });
  }

  for (final changed in [true, false]) {
    testWidgets(
      'editing endpoint ${changed ? "clears" : "retains"} cached generation evidence',
      (tester) async {
        final (store, conn) = await _state([
          ServerProfile(
            id: 'server',
            name: 'Saved server',
            baseUrl: 'https://old.example',
            flavor: ServerFlavor.v2,
            serverVersion: '0.0.0-beta',
          ),
        ]);
        addTearDown(conn.dispose);
        await tester.pumpWidget(_app(store, conn));
        await tester.pumpAndSettle();
        // A saved server's menu opens on long-press of its row (71417a2f).
        await tester.longPress(find.byKey(const ValueKey('server-row-server')));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Edit'));
        await tester.pumpAndSettle();
        final url = find.byKey(const ValueKey('server-url-field'));
        await _reveal(tester, url);
        await tester.enterText(
          url,
          changed ? 'https://new.example' : 'https://old.example/',
        );
        await tester.tap(find.byKey(const ValueKey('save-server-profile')));
        await tester.pumpAndSettle();
        final saved = store.saved.single;
        expect(saved.flavor, changed ? ServerFlavor.v1 : ServerFlavor.v2);
        expect(saved.serverVersion, changed ? isNull : '0.0.0-beta');
        // The row says what the server is, never where: its address moved
        // to the menu's Details.
        expect(
          _supporting(tester, 'server'),
          changed ? 'OpenCode' : 'OpenCode 2',
        );
      },
    );
  }

  testWidgets('known OC1 user reaches phone setup with no runtime forced', (
    tester,
  ) async {
    // Phone setup's pre-flight reads the device when its screen opens.
    answerVoiceDeviceProbe();
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final (store, conn) = await _state([
      ServerProfile(
        id: 'v1',
        name: 'Computer',
        baseUrl: 'https://one.example',
        serverVersion: '1.18.25',
      ),
    ]);
    addTearDown(conn.dispose);
    await tester.pumpWidget(_app(store, conn, scale: 2));
    await tester.pumpAndSettle();
    PhoneSetup.engine = FakeSetupEngine();
    // Termux is phone setup's second host with its own engine (P1.2); a
    // channel engine would wait on a platform that is not there.
    PhoneSetup.termux = FakeSetupEngine();
    // Phone setup v2: Add server's "On this phone" opens phone setup, and
    // Termux (where OpenCode 1 or 2 is chosen) is one of its Other ways.
    await tester.tap(find.byKey(const ValueKey('servers-add')));
    await tester.pumpAndSettle();
    final entry = find.byKey(const ValueKey('quick-add-phone-card'));
    await _reveal(tester, entry);
    await tester.tap(entry);
    await tester.pumpAndSettle();
    expect(find.byType(PhoneSetupStartScreen), findsOneWidget);
    final otherWays = find.byKey(
      const ValueKey('phone-setup-start-other-ways'),
    );
    await tester.ensureVisible(otherWays);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Other ways'));
    await tester.pumpAndSettle();
    final termux = find.byKey(const ValueKey('phone-setup-start-use-termux'));
    await tester.ensureVisible(termux);
    await tester.pumpAndSettle();
    await tester.tap(termux);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    // Termux is a host of phone setup: installing is the v2 job with Termux
    // as its host (P1.2, 935945d6), its progress, not a wizard.
    expect(find.byType(PhoneSetupTermuxJobScreen), findsOneWidget);
    expect(store.saved.single.flavor, ServerFlavor.v1);
    expect(tester.takeException(), isNull);
  });
}

/// The saved server row's supporting line: its state and generation.
String _supporting(WidgetTester tester, String id) => tester
    .widget<KitRow>(find.byKey(ValueKey('server-row-$id')))
    .supporting!
    .toPlainText();
