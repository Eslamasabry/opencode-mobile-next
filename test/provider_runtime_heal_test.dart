import 'dart:async';
import 'dart:io';

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

/// An OpenCode 1 server whose provider runtime is stale: the auth store holds
/// an OpenAI OAuth credential, so `/provider` reports OpenAI as connected with
/// the whole models.dev catalog, but the cached instance never loaded it, so
/// `/config/providers` omits it and every prompt fails with "Model not found".
/// Disposing the instance (refreshProviderRuntime) loads the provider with the
/// models the ChatGPT sign-in can actually serve.
class _StaleRuntimeServer {
  bool openaiLoaded = false;
  bool refreshFails = false;
  int refreshes = 0;

  static final _catalogOpenAI = ProviderInfo(
    id: 'openai',
    name: 'OpenAI',
    modelIDs: const ['gpt-5.6', 'gpt-5.6-pro', 'gpt-5.6-sol'],
  );
  static final _runtimeOpenAI = ProviderInfo(
    id: 'openai',
    name: 'OpenAI',
    modelIDs: const ['gpt-5.6-sol', 'gpt-5.5'],
  );
  static final _zen = ProviderInfo(
    id: 'opencode',
    name: 'OpenCode Zen',
    modelIDs: const ['big-pickle'],
  );

  ProvidersResponse providerList() => ProvidersResponse(
    providers: [openaiLoaded ? _runtimeOpenAI : _catalogOpenAI, _zen],
    defaultProviderID: 'openai',
    defaultModelID: 'gpt-5.6',
  );

  ProvidersResponse configuredProviders() =>
      ProvidersResponse(providers: [if (openaiLoaded) _runtimeOpenAI, _zen]);
}

class _StaleRuntimeApi extends OpenCodeApi {
  _StaleRuntimeApi(this.server) : super(baseUrl: 'http://127.0.0.1:1');

  final _StaleRuntimeServer server;
  final healthResult = Completer<Health>();

  @override
  Future<Health> health() => healthResult.future;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<ProvidersResponse> providers() async => server.providerList();

  @override
  Future<ProvidersResponse> configuredProviders() async =>
      server.configuredProviders();

  @override
  Future<List<AgentInfo>> agents() async => [AgentInfo(name: 'build')];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _FakeEventStream extends EventStream {
  _FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  @override
  void start() => onStatus(StreamStatus.connecting);

  @override
  Future<void> dispose() async {}
}

class _StaleRuntimeRepository extends SdkProductRepository {
  _StaleRuntimeRepository(OpenCodeApi api, this.server) : super(api.sdkClient);

  final _StaleRuntimeServer server;

  @override
  Future<void> refreshProviderRuntime() async {
    server.refreshes += 1;
    if (server.refreshFails) {
      throw const ProductException('Could not refresh the provider runtime');
    }
    server.openaiLoaded = true;
  }

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];

  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
}

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

Future<ConnectionController> _connect(
  WidgetTester tester,
  _StaleRuntimeServer server,
) async {
  final apis = <_StaleRuntimeApi>[];
  final controller = ConnectionController(
    await _store(),
    apiFactory: (_) {
      final api = _StaleRuntimeApi(server);
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _StaleRuntimeRepository(api, server),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  final connect = controller.connect(
    ServerProfile(id: 'server', name: 'server', baseUrl: 'http://127.0.0.1:1'),
  );
  await tester.pump();
  apis.first.healthResult.complete(Health(healthy: true, version: '1'));
  await connect;
  await _settleCatalog(tester, controller);
  return controller;
}

Future<void> _settleCatalog(
  WidgetTester tester,
  ConnectionController controller,
) async {
  for (var i = 0; i < 40; i++) {
    await tester.pump(const Duration(milliseconds: 10));
    if (controller.catalog != null && !controller.catalogLoading) return;
  }
  fail('catalog never finished loading');
}

Set<String> _openaiModels(ConnectionController controller) => {
  for (final model in controller.catalog!.models)
    if (model.providerID == 'openai') model.id,
};

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  test('unloadedProviders compares the connected list with the runtime', () {
    final connected = ProvidersResponse(
      providers: [
        ProviderInfo(id: 'openai', name: 'OpenAI', modelIDs: const ['a']),
        ProviderInfo(id: 'opencode', name: 'Zen', modelIDs: const ['b']),
      ],
    );
    final runtime = ProvidersResponse(
      providers: [
        ProviderInfo(id: 'opencode', name: 'Zen', modelIDs: const ['b']),
      ],
    );
    expect(ConnectionController.unloadedProviders(connected, runtime), {
      'openai',
    });
    expect(
      ConnectionController.unloadedProviders(connected, connected),
      isEmpty,
    );
    // No runtime view (older servers answer both routes from one list).
    expect(ConnectionController.unloadedProviders(connected, null), isEmpty);
  });

  testWidgets(
    'a provider the server signed in to but never loaded is healed on load',
    (tester) async {
      final server = _StaleRuntimeServer();
      final controller = await _connect(tester, server);

      expect(server.refreshes, 1, reason: 'instance disposed exactly once');
      expect(controller.unloadedProviderIDs, isEmpty);
      // Only the models the loaded provider can serve remain; the catalog-only
      // gpt-5.6 the stale server advertised is gone.
      expect(_openaiModels(controller), {'gpt-5.6-sol', 'gpt-5.5'});
      expect(controller.selectedModel?.modelID, isNot('gpt-5.6'));
      expect(controller.catalogError, isNull);
      controller.dispose();
    },
  );

  testWidgets('an unhealable provider is flagged once and reloads on request', (
    tester,
  ) async {
    final server = _StaleRuntimeServer()..refreshFails = true;
    final controller = await _connect(tester, server);

    expect(server.refreshes, 1);
    expect(controller.unloadedProviderIDs, {'openai'});
    // The stale list stays visible so the user still sees what the server
    // claims; the picker notice explains why prompts fail.
    expect(_openaiModels(controller), contains('gpt-5.6'));
    expect(controller.catalogError, isNull);

    // A plain refresh does not hammer a server that could not reload.
    await controller.refreshCatalog();
    expect(server.refreshes, 1);
    expect(controller.unloadedProviderIDs, {'openai'});

    // The explicit reload retries and picks up the recovered runtime.
    server.refreshFails = false;
    await controller.reloadProviderRuntime();
    expect(server.refreshes, 2);
    expect(controller.unloadedProviderIDs, isEmpty);
    expect(_openaiModels(controller), {'gpt-5.6-sol', 'gpt-5.5'});
    controller.dispose();
  });
}
