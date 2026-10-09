// Coverage ratchet for what a server answers when the app checks it
// (see paseo_coverage_support.dart for the rules). Each case is the answer
// the way the server sends it: OpenCode 1 and 2 health/info over the real
// connection probe, a Paseo daemon's server_info and provider snapshot over
// the real Paseo probe. The verdict is drawn by the real Add server screen.
// Messages the app never asks a server for are in the ledger as ignored.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/paseo_connection_probe.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/first_run_path.dart';
import '../support/server_editor.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

/// A connection whose health answer is the one the case produced.
class _HealthApi extends OpenCodeApi {
  _HealthApi(this._health, {this.abilities})
    : super(baseUrl: 'https://lab.example.net:4096');

  final Health _health;
  final ServerCapabilities? abilities;

  @override
  ServerCapabilities get capabilities => abilities ?? super.capabilities;

  @override
  Future<Health> health() async => _health;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('servers_wire', prefix: '');
  registerLedgerTests(family);

  final oldSocketProbe = socketAgentProbe;
  final oldWarmup = (providerWarmupAttempts, providerWarmupInterval);
  setUp(() {
    // An agent still starting is part of the case; do not wait out the real
    // retry delay.
    providerWarmupAttempts = 2;
    providerWarmupInterval = const Duration(milliseconds: 1);
  });
  tearDown(() {
    providerWarmupAttempts = oldWarmup.$1;
    providerWarmupInterval = oldWarmup.$2;
    serverProbe = probeServerConnection;
    serverProbeAdapterFactory = null;
    socketAgentProbe = oldSocketProbe;
  });

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('server check · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      mockNoTermux();
      final flavor = variant['flavor'] as String;
      final payload = Map<String, dynamic>.from(variant['payload'] as Map);
      PaseoGateway paseoGateway(String directory) {
        final daemon = WireDaemon(payload);
        daemon.handlers['fetch_agents_request'] = (_) => (
          'fetch_agents_response',
          {
            'entries': <Object>[],
            'pageInfo': {
              'nextCursor': null,
              'prevCursor': null,
              'hasMore': false,
            },
          },
        );
        daemon.handlers['get_providers_snapshot_request'] = (_) => (
          'get_providers_snapshot_response',
          {
            'entries': variant['providers'],
            'generatedAt': '2026-10-09T08:00:00.000Z',
          },
        );
        return PaseoGateway(
          transport: PaseoTransport(
            endpoint: 'ws://127.0.0.1:6767',
            socketFactory: (_, _) async => daemon,
          ),
          directory: directory,
        );
      }

      if (flavor == 'paseo') {
        socketAgentProbe =
            ({
              required backend,
              required baseUrl,
              required secret,
              required directory,
            }) => probePaseoConnection(
              baseUrl: baseUrl,
              password: secret,
              directory: directory,
              gatewayFactory:
                  ({required baseUrl, required password, required directory}) {
                    return paseoGateway(directory);
                  },
            );
      } else {
        serverProbeAdapterFactory = () => WireAdapter((options) {
          final path = options.path;
          if (flavor == 'oc1') {
            return path.endsWith('/global/health')
                ? wireJson(payload)
                : wireEmpty(404);
          }
          // OpenCode 2: the beta answers at /api/health, the stable line
          // (which dropped that route) at /api/info.
          final beta = variant['id'] == 'oc2_beta_health';
          if (path.endsWith('/api/health')) {
            return beta ? wireJson(payload) : wireEmpty(404);
          }
          if (path.endsWith('/api/info')) {
            return beta ? wireEmpty(404) : wireJson(payload);
          }
          return wireEmpty(404);
        });
      }

      final (store, controller) = await serversState();
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(serversApp(boundary, store, controller));
      await tester.pumpAndSettle();
      await openFirstRunConnect(
        tester,
        agent: flavor == 'paseo' ? 'paseo' : 'opencode',
      );
      if (flavor == 'paseo') {
        await tester.enterText(
          find.byKey(const ValueKey('codex-server-address-field')),
          'ws://127.0.0.1:6767',
        );
        await tester.enterText(
          find.byKey(const ValueKey('codex-project-directory-field')),
          '/work/shop',
        );
      } else {
        await openServerManualAddress(tester);
        await tester.enterText(
          find.byKey(const ValueKey('server-url-field')),
          'https://box.example:4096',
        );
      }
      await tester.pump(autoTestPause + const Duration(milliseconds: 200));
      await frames(tester, 12);
      final test = find.text('Test connection');
      if (flavor == 'paseo' && test.evaluate().isNotEmpty) {
        await tester.ensureVisible(test.first);
        await tester.tap(test.first);
        await frames(tester, 16);
      }
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'srvwire_$id');

      // The same answer as the connected server's own page reads it.
      Health? health;
      if (flavor == 'oc1') health = Health.fromJson(payload);
      if (flavor == 'paseo') {
        health = await tester.runAsync(
          () => paseoGateway('/work/shop').health(),
        );
      }
      if (health != null) {
        final (settingsStore, unused) = await serversState(
          profiles: [
            flavor == 'paseo'
                ? ServerProfile(
                    id: 'srv',
                    name: 'Build box',
                    baseUrl: 'ws://build.example.net:6767',
                    backend: ServerBackend.paseo,
                    codexDirectory: '/work/shop',
                  )
                : ServerProfile(
                    id: 'srv',
                    name: 'Lab OpenCode',
                    baseUrl: 'https://lab.example.net:4096',
                  ),
          ],
        );
        unused.dispose();
        final connection = ServersConnection(settingsStore)
          ..api = _HealthApi(
            health,
            abilities: flavor == 'paseo' ? paseoServerCapabilities : null,
          )
          ..status = StreamStatus.connected;
        addTearDown(connection.dispose);
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(seconds: 1));
        await tester.pumpWidget(
          serversApp(
            boundary,
            settingsStore,
            connection,
            home: ServerSettingsScreen(controller: connection),
          ),
        );
        await frames(tester, 10);
        seen.add(screenText(tester).join('\n'));
        await writeCasePng(tester, boundary, 'srvwire_${id}_page');
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      // A Claude Code or Pi server's page is not an OpenCode install guide.
      if (flavor == 'paseo' && screen.contains('Run as a Linux service')) {
        problems.add('a Paseo server page offers the OpenCode Linux service');
      }
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
