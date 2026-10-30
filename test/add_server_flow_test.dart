// Add server rebuilt (ledger row 15, slice B of
// docs/design/motion-and-illustration-2026-09-25.md): the type is one clear
// choice, pairing comes first, the address waits under "Enter the address
// instead", Save & connect checks the connection by itself and says what
// failed, Test connection is a tertiary at most, and the drawing tells the
// moment (linking while it checks, linked when paired, broken on failure).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/codex_connection_probe.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/server_editor.dart';

/// A stand-in serve password. Never a live one.
const _password = 'fixture-not-a-live-serve-password-000000000';

class _Store extends ProfileStore {
  _Store({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = [...seeded];

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => null;

  @override
  Future<String?> secureStorageProblem() async => null;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((p) => p.id == profile.id)
      ..add(profile);
  }
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  final connected = <ServerProfile>[];

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connected.add(profile);
    api = FakeServerGateway(baseUrl: profile.baseUrl);
  }
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example.net',
);

/// Servers beside one saved server, with Add server open on the connect
/// step for [kind] (null stays on the first step, what runs there).
Future<(_Store, _Connection)> _openAddServer(
  WidgetTester tester, {
  String? kind = 'opencode',
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    seeded: [_laptop],
  );
  final connection = _Connection(store);
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(connection),
      ],
      child: MaterialApp(
        routes: {
          '/home': (_) => const Scaffold(body: Text('home-route')),
          '/guide': (_) => const Scaffold(body: Text('guide-route')),
        },
        home: const ServersScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.text('Add server'));
  await tester.pumpAndSettle();
  expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
  if (kind != null) await chooseServerKind(tester, kind: kind);
  return (store, connection);
}

Future<void> _openManualAddress(WidgetTester tester) async {
  final header = find.byKey(const ValueKey('server-manual-address'));
  await tester.ensureVisible(header);
  await tester.pumpAndSettle();
  await tester.tap(header);
  await tester.pumpAndSettle();
}

Future<void> _typeAddress(WidgetTester tester) async {
  await _openManualAddress(tester);
  await tester.enterText(
    find.byKey(const ValueKey('server-url-field')),
    'https://build.example.net',
  );
  await tester.pump();
}

Finder _drawing(String state) => find.byKey(ValueKey('server-link-$state'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const termux = MethodChannel('oc/termux');

  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      termux,
      (call) async => call.method == 'getCapabilities'
          ? <String, Object>{'installed': false}
          : null,
    );
    debugPlatformCapabilities = const PlatformCapabilities.android();
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(termux, null);
    serverProbe = probeServerConnection;
    debugPlatformCapabilities = null;
  });

  testWidgets('the type is one choice of three rows, without jargon', (
    tester,
  ) async {
    await _openAddServer(tester, kind: null);
    for (final key in const [
      'server-backend-opencode',
      'server-backend-codex',
      'server-backend-paseo',
    ]) {
      expect(find.byKey(ValueKey(key)), findsOneWidget, reason: key);
    }
    expect(find.text('OpenCode on a computer'), findsOneWidget);
    expect(find.text('Claude Code or Pi'), findsOneWidget);
    expect(find.textContaining('experimental'), findsNothing);
    expect(find.textContaining('Paseo:'), findsNothing);
    // Nothing on the form is written with raw backticks.
    for (final text in tester.widgetList<Text>(find.byType(Text))) {
      expect(text.data ?? '', isNot(contains('`')));
    }
    for (final text in tester.widgetList<SelectableText>(
      find.byType(SelectableText),
    )) {
      expect(text.data ?? '', isNot(contains('`')));
    }
  });

  testWidgets('pairing comes first; the address waits folded', (tester) async {
    await _openAddServer(tester);
    final scan = find.byKey(const ValueKey('server-pairing-scan'));
    final paste = find.byKey(const ValueKey('server-pairing-paste'));
    expect(scan, findsOneWidget);
    expect(paste, findsOneWidget);
    // Scan and Paste code are the same kind of button, side by side.
    expect(
      tester.getSize(scan).height,
      moreOrLessEquals(tester.getSize(paste).height),
    );
    expect(tester.getTopLeft(scan).dy, tester.getTopLeft(paste).dy);
    expect(find.byType(FilledButton), findsNWidgets(3));
    // The address and password are not on screen until asked for.
    expect(find.byKey(const ValueKey('server-url-field')), findsNothing);
    expect(find.byKey(const ValueKey('server-password-field')), findsNothing);
    final manual = find.byKey(const ValueKey('server-manual-address'));
    expect(manual, findsOneWidget);
    expect(
      tester.getTopLeft(manual).dy,
      greaterThan(tester.getTopLeft(scan).dy),
    );

    await _openManualAddress(tester);
    expect(find.byKey(const ValueKey('server-url-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('server-password-field')), findsOneWidget);
    // Test connection is there, as a text button (a tertiary at most).
    final test = find.byKey(const ValueKey('test-server-connection'));
    expect(test, findsOneWidget);
    expect(
      find.ancestor(
        of: find.text('Test connection'),
        matching: find.byType(TextButton),
      ),
      findsOneWidget,
    );
  });

  testWidgets('Save & connect checks first, and a failure is explained, '
      'not saved', (tester) async {
    final (store, connection) = await _openAddServer(tester);
    final checked = <String>[];
    serverProbe = ({required baseUrl, username, password}) async {
      checked.add(baseUrl);
      return const ServerProbeResult.failure(
        'The connection was refused. Is opencode serve running on that host '
        'and port?',
        suggestsMissingServer: true,
      );
    };
    await _typeAddress(tester);
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(checked, ['https://build.example.net']);
    expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
    expect(find.textContaining('refused'), findsWidgets);
    expect(store.saved.map((p) => p.id), ['laptop']);
    expect(connection.connected, isEmpty);
    expect(_drawing('failed'), findsOneWidget);
    // Still the editor, with a way to keep the server anyway.
    final anyway = find.byKey(const ValueKey('server-save-anyway'));
    expect(anyway, findsOneWidget);
    await tester.tap(anyway);
    await tester.pumpAndSettle();
    expect(store.saved, hasLength(2));
    expect(connection.connected.single.baseUrl, 'https://build.example.net');
  });

  testWidgets('Save & connect saves what the check found and connects', (
    tester,
  ) async {
    final (store, connection) = await _openAddServer(tester);
    final pending = Completer<ServerProbeResult>();
    var checks = 0;
    serverProbe = ({required baseUrl, username, password}) {
      checks++;
      return pending.future;
    };
    await _typeAddress(tester);
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    // While it checks: the phone and the computer linking, and one line.
    expect(_drawing('linking'), findsOneWidget);
    expect(find.text('Checking build.example.net…'), findsOneWidget);
    expect(find.text('Checking the connection…'), findsOneWidget);
    expect(connection.connected, isEmpty);

    pending.complete(
      const ServerProbeResult.success('2.0.10', flavor: ServerFlavor.v2),
    );
    await tester.pumpAndSettle();
    expect(checks, 1);
    final added = store.saved.singleWhere((p) => p.id != 'laptop');
    expect(added.flavor, ServerFlavor.v2);
    expect(added.serverVersion, '2.0.10');
    expect(connection.connected.single.id, added.id);
    // The flow ends on its ready moment; its one way on opens the app.
    expect(find.byKey(const ValueKey('server-ready-step')), findsOneWidget);
    await openReadyServer(tester);
    expect(find.text('home-route'), findsOneWidget);
  });

  testWidgets('a pairing code that answers shows the spark, and Save does '
      'not check again', (tester) async {
    final (store, connection) = await _openAddServer(tester);
    var checks = 0;
    serverProbe = ({required baseUrl, username, password}) async {
      checks++;
      return const ServerProbeResult.success('2.0.10', flavor: ServerFlavor.v2);
    };
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async => call.method == 'Clipboard.getData'
          ? {
              'text':
                  '{"urls":["https://studio.example.net:4097"],'
                  '"username":"opencode","password":"$_password"}',
            }
          : null,
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();
    expect(_drawing('linked'), findsOneWidget);
    expect(find.text('Paired with studio.example.net.'), findsOneWidget);
    // Paired without opening the address.
    expect(find.byKey(const ValueKey('server-url-field')), findsNothing);
    // The password is never shown in the clear.
    for (final text in tester.widgetList<Text>(find.byType(Text))) {
      expect(text.data ?? '', isNot(contains(_password)));
    }

    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(checks, 1);
    final added = store.saved.singleWhere((p) => p.id != 'laptop');
    expect(added.baseUrl, 'https://studio.example.net:4097');
    expect(added.password, _password);
    expect(connection.connected.single.id, added.id);
  });

  testWidgets('an empty address on save opens the fold at the error', (
    tester,
  ) async {
    final (store, connection) = await _openAddServer(tester);
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(find.text('Enter a server URL.'), findsOneWidget);
    expect(store.saved.map((p) => p.id), ['laptop']);
    expect(connection.connected, isEmpty);
  });

  for (final (type, label) in const [
    ('codex', 'Connection token'),
    ('paseo', 'Daemon password (optional)'),
  ]) {
    testWidgets('choosing $type shows its own fields, not pairing', (
      tester,
    ) async {
      final (store, connection) = await _openAddServer(tester, kind: null);
      await tester.tap(find.byKey(ValueKey('server-backend-$type')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('codex-server-address-field')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('codex-project-directory-field')),
        findsOneWidget,
      );
      expect(find.text(label), findsOneWidget);
      expect(
        find.byKey(const ValueKey('server-pairing-actions')),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('server-manual-address')), findsNothing);

      // Save & connect checks this type too, with its own probe.
      final probes = <ServerBackend>[];
      final previous = socketAgentProbe;
      socketAgentProbe =
          ({
            required backend,
            required baseUrl,
            required secret,
            required directory,
          }) async {
            probes.add(backend);
            return const CodexConnectionProbeResult(
              ok: false,
              message: 'The Codex server could not be reached.',
            );
          };
      addTearDown(() => socketAgentProbe = previous);
      await tester.enterText(
        find.byKey(const ValueKey('codex-server-address-field')),
        'ws://127.0.0.1:4500',
      );
      await tester.enterText(
        find.byKey(const ValueKey('codex-project-directory-field')),
        '/work/project',
      );
      await tester.enterText(
        find.byKey(const ValueKey('codex-connection-token-field')),
        'token-value',
      );
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();
      expect(probes, [
        type == 'codex' ? ServerBackend.codex : ServerBackend.paseo,
      ]);
      expect(find.byKey(const ValueKey('codex-test-failure')), findsOneWidget);
      expect(find.byKey(const ValueKey('server-save-anyway')), findsOneWidget);
      expect(store.saved.map((p) => p.id), ['laptop']);
      expect(connection.connected, isEmpty);
    });
  }

  testWidgets('the Servers welcome is the question, at rest', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final store = _Store(prefs: await SharedPreferences.getInstance());
    final connection = _Connection(store);
    addTearDown(connection.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: const MaterialApp(home: ServersScreen()),
      ),
    );
    await tester.pumpAndSettle();
    // Vision 2026-10-20: no drawing and no value paragraph; the question
    // is the page.
    expect(find.byKey(const ValueKey('servers-welcome-hero')), findsNothing);
    expect(find.text('Where should your agent run?'), findsOneWidget);
    // A resting screen: nothing keeps moving.
    expect(tester.hasRunningAnimations, isFalse);
  });
}
