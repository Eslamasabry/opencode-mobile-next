import 'dart:async';
import 'dart:io';

import 'package:clock/clock.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

/// What an OpenCode 1 server does: the `/api/...` (v2) endpoints answer
/// "not found", the plain ones answer.
class _Counts {
  int permissionsV2 = 0, questionsV2 = 0, detailedCatalog = 0, providers = 0;
  Completer<void>? catalogGate;
  void Function(EventEnvelope)? emit;
}

class _Api extends OpenCodeApi {
  _Api(this.counts, {this.gated = false})
    : super(baseUrl: 'http://127.0.0.1:1');
  final _Counts counts;
  final bool gated;
  final health_ = Completer<Health>();

  @override
  Future<Health> health() => gated
      ? health_.future
      : Future.value(Health(healthy: true, version: '1'));
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<ProvidersResponse> providers() async {
    counts.providers += 1;
    return ProvidersResponse(providers: const []);
  }

  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() {
    counts.permissionsV2 += 1;
    return Future.error(ApiException('Not found', statusCode: 404));
  }

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() {
    counts.questionsV2 += 1;
    return Future.error(ApiException('Not found', statusCode: 404));
  }

  @override
  Future<List<FileNode>> listFiles(String path) async => const [];
}

class _Stream extends EventStream {
  _Stream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
    required this.counts,
  });
  final _Counts counts;
  @override
  void start() {
    counts.emit = onEvent;
    onStatus(StreamStatus.connecting);
  }

  @override
  Future<void> dispose() async {}
}

class _Repo extends SdkProductRepository {
  _Repo(OpenCodeApi api, this.counts) : super(api.sdkClient);
  final _Counts counts;
  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<CatalogSnapshot> loadCatalog() async {
    counts.detailedCatalog += 1;
    await counts.catalogGate?.future;
    throw ProductException(
      'Could not load models and agents',
      cause: ApiException('Not found', statusCode: 404),
    );
  }

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
}

Future<ConnectionController> _connect(
  WidgetTester tester,
  _Counts counts,
) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  final apis = <_Api>[];
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _Api(counts, gated: apis.isEmpty);
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _Repo(api, counts),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _Stream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
              counts: counts,
            ),
  );
  final connect = controller.connect(
    ServerProfile(id: 'server', name: 'server', baseUrl: 'http://127.0.0.1:1'),
  );
  await tester.pump();
  apis.first.health_.complete(Health(healthy: true, version: '1'));
  await connect;
  await tester.pump();
  return controller;
}

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  testWidgets('an OpenCode 1 server is asked for /api once per connection', (
    tester,
  ) async {
    final counts = _Counts();
    final c = await _connect(tester, counts);
    // The connection's own first reads may have probed already.
    await c.refreshPendingPermissions();
    await c.refreshPendingQuestions();
    final permissions = counts.permissionsV2;
    final questions = counts.questionsV2;
    expect(permissions, lessThanOrEqualTo(1));
    expect(questions, lessThanOrEqualTo(1));
    for (var i = 0; i < 3; i++) {
      await c.refreshPendingPermissions();
      await c.refreshPendingQuestions();
    }
    expect(counts.permissionsV2, permissions, reason: 'no more /api probes');
    expect(counts.questionsV2, questions, reason: 'no more /api probes');

    await c.refreshCatalog();
    final detailed = counts.detailedCatalog;
    expect(detailed, lessThanOrEqualTo(1));
    await c.refreshCatalog();
    await c.refreshCatalog();
    expect(counts.detailedCatalog, detailed, reason: 'no more /api catalog');

    // A new connection asks again, once.
    final before = counts.permissionsV2;
    await c.connect(
      ServerProfile(
        id: 'server',
        name: 'server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    await c.refreshPendingPermissions();
    await c.refreshPendingPermissions();
    expect(counts.permissionsV2, before + 1);
    c.dispose();
  });

  testWidgets('opening Settings reuses the catalog read at connect', (
    tester,
  ) async {
    final counts = _Counts();
    final c = await _connect(tester, counts);
    await tester.pump(const Duration(milliseconds: 50));
    expect(counts.providers, 1, reason: 'one /provider read at connect');

    // What Settings does on open: wake-reconcile the transport, then read
    // the catalog for its model row.
    await c.prepareActionTransport();
    await c.ensureCatalog();
    await tester.pump(const Duration(milliseconds: 50));
    expect(counts.providers, 1, reason: 'Settings opening reuses it');

    // A wake rebuilds the transport and clears the loaded marker, not the
    // answer: the catalog read moments ago is still this server's and folder's.
    await c.retryConnection();
    await c.ensureCatalog();
    await tester.pump(const Duration(milliseconds: 50));
    final afterFolder = counts.providers;
    expect(afterFolder, 1, reason: 'a wake inside the window reads nothing');

    // A real change event and a manual reload still read at once.
    counts.emit?.call(EventEnvelope(type: 'config.updated'));
    await tester.pump(const Duration(milliseconds: 50));
    expect(counts.providers, afterFolder + 1);
    await c.refreshCatalog();
    expect(counts.providers, afterFolder + 2);

    // Past the window a screen open reads it again.
    await withClock(
      Clock.fixed(DateTime.now().add(const Duration(minutes: 11))),
      () async {
        await c.ensureCatalog();
      },
    );
    expect(counts.providers, afterFolder + 3);
    c.dispose();
  });
}
