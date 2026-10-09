// Action gate for the servers area: every call that CHANGES something (a
// mutating OpenCode endpoint or Paseo message that belongs to a server page)
// and every control on the area's screens has a decision in
// test/fixtures/coverage/servers_actions_ledger.json:
//
//   reachable: <screen> > <control>    the test taps its way there, checks the
//                                      control exists and that it does the
//                                      thing (or `proof:` names an existing
//                                      test that does; the ratchet checks the
//                                      test is still there)
//   not offered: <reason>              why no control calls it; "owner
//                                      decision needed" marks the ones a
//                                      person must decide.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/first_run_path.dart';
import '../support/server_editor.dart';
import 'servers_support.dart';

final _ledger =
    (jsonDecode(
              File(
                'test/fixtures/coverage/servers_actions_ledger.json',
              ).readAsStringSync(),
            )
            as Map)
        .cast<String, String>();

final _wire =
    ((jsonDecode(
                  File(
                    'test/fixtures/coverage/servers_actions_samples.json',
                  ).readAsStringSync(),
                )
                as Map)['wire']
            as List)
        .cast<String>();

/// Every control the area's screens offer, by the name the ledger uses.
const _app = [
  'app servers.add',
  'app servers.connect',
  'app servers.edit',
  'app servers.details',
  'app servers.remove',
  'app servers.about',
  'app servers.account',
  'app servers.move-queued',
  'app servers.this-phone',
  'app editor.test-connection',
  'app editor.save-connect',
  'app editor.save-anyway',
  'app editor.pairing-paste',
  'app editor.pairing-scan',
  'app editor.tailscale',
  'app banner.reconnect',
  'app banner.details',
  'app banner.change-server',
  'app banner.update-password',
  'app banner.update-token',
  'app banner.restart-phone-server',
  'app server-settings.check-again',
  'app server-settings.change-sign-in',
  'app server-settings.host-management',
  'app server-settings.disconnect',
  'app host.copy-command',
  'app capabilities.add-server',
];

/// A status line's connection: reports the chosen status and counts retries.
class _Status extends ServersConnection {
  _Status(super.store, this.snapshot);

  final ConnectionStatusSnapshot snapshot;
  int retries = 0;

  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;

  @override
  Future<void> retryConnection() async => retries++;
}

class _HealthApi extends OpenCodeApi {
  _HealthApi() : super(baseUrl: 'https://lab.example.net:4096');
  int checks = 0;

  @override
  Future<Health> health() async {
    checks++;
    return Health(healthy: true, version: '1.14.52');
  }
}

/// A server with gaps: the page then offers one that has the missing things.
class _GapApi extends OpenCodeApi {
  _GapApi() : super(baseUrl: 'https://lab.example.net:4096');

  @override
  ServerCapabilities get capabilities => const ServerCapabilities();
}

class _Settings extends ConnectionController {
  _Settings(super.store);
  int disconnects = 0;

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async => disconnects++;
}

final _profile = ServerProfile(
  id: 'srv',
  name: 'Studio OpenCode',
  baseUrl: 'https://studio.example.net:4096',
  serverVersion: '1.14.52',
);

Map<String, WidgetBuilder> _routes(List<String> pushed) => {
  '/about': (_) => const Scaffold(body: Text('about-route')),
  '/servers': (context) {
    final arguments = ModalRoute.of(context)?.settings.arguments;
    pushed.add('/servers:$arguments');
    return Scaffold(body: Text('servers-route:$arguments'));
  },
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });
  tearDown(() {
    serverProbe = probeServerConnection;
    KitCapabilities.debugReset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  // ------------------------------------------------------------- ledger
  test('every mutating call and control has a decision', () {
    final wanted = {..._wire, ..._app};
    expect(
      wanted.difference(_ledger.keys.toSet()),
      isEmpty,
      reason: 'calls and controls with no entry in servers_actions_ledger.json',
    );
    expect(
      _ledger.keys.toSet().difference(wanted),
      isEmpty,
      reason: 'ledger entries for calls or controls that no longer exist',
    );
    for (final entry in _ledger.entries) {
      final ok =
          entry.value.startsWith('reachable: ') ||
          (entry.value.startsWith('not offered: ') && entry.value.length > 30);
      expect(ok, isTrue, reason: '${entry.key}: reachable / not offered');
    }
  });

  test('a proof names a test that still exists', () {
    for (final entry in _ledger.entries) {
      final at = entry.value.indexOf('; proof: ');
      if (at < 0) continue;
      final parts = entry.value
          .substring(at + '; proof: '.length)
          .split(' :: ');
      expect(parts, hasLength(2), reason: entry.key);
      final file = File(parts[0]);
      expect(file.existsSync(), isTrue, reason: '${entry.key}: ${parts[0]}');
      expect(
        file.readAsStringSync(),
        contains(parts[1]),
        reason: '${entry.key}: no test called "${parts[1]}" in ${parts[0]}',
      );
    }
  });

  test('every reachable control without a proof has a tap path here', () {
    final tapped = {
      for (final entry in _ledger.entries)
        if (entry.value.startsWith('reachable: ') &&
            !entry.value.contains('; proof: '))
          entry.key,
    };
    expect(
      tapped.difference(_paths.keys.toSet()),
      isEmpty,
      reason: '"reachable" with no tap path and no proof',
    );
    expect(
      _paths.keys.toSet().difference(tapped),
      isEmpty,
      reason: 'tap paths the ledger does not call "reachable"',
    );
  });

  // ------------------------------------------------------------ tap paths
  path('app servers.add', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await tester.tap(find.byKey(const ValueKey('servers-add')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
  });

  path('app servers.connect', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await tester.tap(find.byKey(const ValueKey('server-row-srv')));
    await tester.pumpAndSettle();
    expect(controller.connects.map((p) => p.id), ['srv']);
    expect(find.text('home-route'), findsOneWidget);
  });

  path('app servers.edit', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await rowMenu(tester, 'Edit');
    expect(find.text('Edit server'), findsOneWidget);
  });

  path('app servers.details', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await rowMenu(tester, 'Details');
    expect(
      find.byKey(const ValueKey('server-details-sheet-srv')),
      findsOneWidget,
    );
  });

  path('app servers.remove', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await rowMenu(tester, 'Remove');
    final confirm = find.byKey(const ValueKey('confirm-remove-server-srv'));
    expect(confirm, findsOneWidget);
    await tester.tap(confirm);
    // The removal sweeps several stores; give it real time to finish.
    for (var i = 0; i < 20 && store.removed.isEmpty; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(store.removed, ['srv']);
  });

  path('app servers.about', (tester) async {
    final (store, controller) = await serversState(profiles: [_profile]);
    await pump(tester, store, controller);
    await tester.tap(find.byKey(const ValueKey('servers-about')));
    await tester.pumpAndSettle();
    expect(find.text('about-route'), findsOneWidget);
  });

  // The editor, on the first-run computer path.
  Future<(ServersStore, ServersConnection)> editor(
    WidgetTester tester,
    ServerProbe probe,
  ) async {
    serverProbe = probe;
    final (store, controller) = await serversState();
    await pump(tester, store, controller);
    await openFirstRunConnect(tester);
    await openServerManualAddress(tester);
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://box.example:4096',
    );
    await tester.pump();
    return (store, controller);
  }

  path('app editor.test-connection', (tester) async {
    final probed = <String>[];
    await editor(tester, ({required baseUrl, username, password}) async {
      probed.add(baseUrl);
      return const ServerProbeResult.success('1.14.52');
    });
    final test = find.text('Test connection');
    expect(test, findsOneWidget);
    await tester.ensureVisible(test);
    await tester.tap(test);
    await frames(tester, 10);
    expect(probed, isNotEmpty);
    expect(find.byKey(const ValueKey('server-test-success')), findsOneWidget);
  });

  path('app editor.save-connect', (tester) async {
    final (store, controller) = await editor(
      tester,
      ({required baseUrl, username, password}) async =>
          const ServerProbeResult.success('1.14.52'),
    );
    tester.testTextInput.hide();
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(store.saved.single.baseUrl, 'https://box.example:4096');
    expect(controller.connects.single.baseUrl, 'https://box.example:4096');
  });

  path('app editor.save-anyway', (tester) async {
    final (store, controller) = await editor(
      tester,
      ({required baseUrl, username, password}) async =>
          const ServerProbeResult.failure('Server unreachable'),
    );
    tester.testTextInput.hide();
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(store.saved, isEmpty);
    final anyway = find.byKey(const ValueKey('server-save-anyway'));
    expect(anyway, findsOneWidget);
    await tester.ensureVisible(anyway);
    await tester.tap(anyway);
    await tester.pumpAndSettle();
    expect(store.saved.single.baseUrl, 'https://box.example:4096');
    expect(controller.connects, isNotEmpty);
  });

  // The status line.
  Future<_Status> status(
    WidgetTester tester,
    ConnectionStatusPhase phase, {
    bool token = false,
    List<String>? pushed,
  }) async {
    final (store, base) = await serversState();
    base.dispose();
    final controller = _Status(
      store,
      ConnectionStatusSnapshot(
        phase: phase,
        profileId: 'srv',
        serverName: 'Studio OpenCode',
        usesToken: token,
      ),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      captureApp(
        boundaryKey: GlobalKey(),
        controller: controller,
        store: store,
        routes: _routes(pushed ?? []),
        home: Builder(
          builder: (context) => AppConditionsScope(
            conditions: [connectionKitStatus(context, controller)],
            child: const KitScreen(body: SizedBox.expand()),
          ),
        ),
      ),
    );
    await frames(tester, 6);
    return controller;
  }

  path('app banner.reconnect', (tester) async {
    final controller = await status(tester, ConnectionStatusPhase.notAnswering);
    final retry = find.byKey(const ValueKey('connection-banner-retry'));
    expect(retry, findsOneWidget);
    await tester.tap(retry);
    await frames(tester, 4);
    expect(controller.retries, 1);
  });

  Future<void> openMore(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await frames(tester, 6);
  }

  path('app banner.details', (tester) async {
    await status(tester, ConnectionStatusPhase.notAnswering);
    await openMore(tester);
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await frames(tester, 10);
    expect(
      find.byKey(const ValueKey('connection-banner-details-sheet')),
      findsOneWidget,
    );
  });

  path('app banner.change-server', (tester) async {
    final pushed = <String>[];
    await status(tester, ConnectionStatusPhase.notAnswering, pushed: pushed);
    await openMore(tester);
    await tester.tap(
      find.byKey(const ValueKey('connection-banner-change-server')),
    );
    await frames(tester, 10);
    expect(pushed, ['/servers:null']);
  });

  path('app banner.update-password', (tester) async {
    final pushed = <String>[];
    await status(
      tester,
      ConnectionStatusPhase.credentialsRequired,
      pushed: pushed,
    );
    await tester.tap(find.byKey(const ValueKey('banner-update-password')));
    await frames(tester, 10);
    expect(pushed, ['/servers:edit-active']);
  });

  path('app banner.update-token', (tester) async {
    final pushed = <String>[];
    await status(
      tester,
      ConnectionStatusPhase.credentialsRequired,
      token: true,
      pushed: pushed,
    );
    await tester.tap(find.byKey(const ValueKey('banner-update-token')));
    await frames(tester, 10);
    expect(pushed, ['/servers:edit-active']);
  });

  // Server settings.
  Future<(_Settings, _HealthApi, List<String>)> settings(
    WidgetTester tester,
  ) async {
    final (store, base) = await serversState(
      profiles: [
        ServerProfile(
          id: 'srv',
          name: 'Lab OpenCode',
          baseUrl: 'https://lab.example.net:4096',
        ),
      ],
    );
    base.dispose();
    final api = _HealthApi();
    final controller = _Settings(store)
      ..api = api
      ..status = StreamStatus.connected
      ..version = '1.14.52';
    addTearDown(controller.dispose);
    final pushed = <String>[];
    await tester.pumpWidget(
      captureApp(
        boundaryKey: GlobalKey(),
        controller: controller,
        store: store,
        routes: _routes(pushed),
        home: ServerSettingsScreen(controller: controller),
      ),
    );
    await frames(tester, 10);
    return (controller, api, pushed);
  }

  path('app server-settings.check-again', (tester) async {
    final (_, api, _) = await settings(tester);
    final before = api.checks;
    await tester.tap(find.byKey(const ValueKey('server-health-check')));
    await frames(tester, 10);
    expect(api.checks, before + 1);
  });

  path('app server-settings.change-sign-in', (tester) async {
    final (_, _, pushed) = await settings(tester);
    await tester.tap(find.byKey(const Key('server-authentication')));
    await frames(tester, 10);
    expect(pushed, ['/servers:edit-active']);
  });

  path('app server-settings.host-management', (tester) async {
    await settings(tester);
    final entry = find.byKey(const Key('host-management-entry'));
    await tester.ensureVisible(entry);
    await tester.tap(entry);
    await frames(tester, 10);
    expect(find.byKey(const ValueKey('host-service-intro')), findsOneWidget);
  });

  path('app server-settings.disconnect', (tester) async {
    final (controller, _, pushed) = await settings(tester);
    final row = find.byKey(const ValueKey('server-disconnect'));
    await tester.ensureVisible(row);
    await tester.tap(row);
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('confirm-disconnect')));
    await frames(tester, 10);
    expect(controller.disconnects, 1);
    expect(pushed, ['/servers:null']);
  });

  path('app capabilities.add-server', (tester) async {
    final requests = <KitEnableRequest>[];
    KitCapabilities.registerFlow(
      KitEnableFlows.addServer,
      (context, request) async => requests.add(request),
    );
    final (store, controller) = await serversState(profiles: [_profile]);
    controller.dispose();
    final server = ConnectionController(store)
      ..api = _GapApi()
      ..status = StreamStatus.connected;
    addTearDown(server.dispose);
    // A server that lacks features: the page offers one that has them.
    await tester.pumpWidget(
      captureApp(
        boundaryKey: GlobalKey(),
        controller: server,
        store: store,
        home: ServerCapabilitiesScreen(controller: server),
      ),
    );
    await frames(tester, 10);
    final add = find.byKey(const ValueKey('capabilities-add-server'));
    expect(add, findsOneWidget);
    await tester.tap(add);
    await frames(tester, 6);
    expect(requests.single.capability, 'server.any');
  });
}

final _paths = <String, Future<void> Function(WidgetTester)>{};

/// Registers one tap path as a widget test named after its ledger key.
void path(String key, Future<void> Function(WidgetTester tester) body) {
  _paths[key] = body;
  testWidgets('action · $key', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await body(tester);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

Future<void> pump(
  WidgetTester tester,
  ServersStore store,
  ConnectionController controller, {
  List<String>? pushed,
}) async {
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    serversApp(GlobalKey(), store, controller, routes: _routes(pushed ?? [])),
  );
  await tester.pumpAndSettle();
}

/// Presses and holds the saved server's row and chooses [label].
Future<void> rowMenu(WidgetTester tester, String label) async {
  await tester.longPress(find.byKey(const ValueKey('server-row-srv')));
  await frames(tester, 4);
  await tester.tap(find.text(label).last);
  await frames(tester, 8);
}
