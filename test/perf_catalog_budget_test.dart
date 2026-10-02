import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The owner's phone, 2026-10-02, 20 minutes of chatting on the built-in
/// OpenCode 1 server: catalog.load 13x (p50 2.6 s), GET /provider 28x
/// (twice per load), lifecycle.resume 6x at 2.5 s, permissions and
/// questions read 14x each. These replay that session on a fake server
/// with the phone's latencies and pin the request budget.

class _RealHttpOverrides extends HttpOverrides {}

/// Calls per endpoint, with the phone's p50 latencies (fake time).
class _Server {
  final calls = <String, int>{};
  bool eventsConnect = true;

  Future<T> answer<T>(String path, int ms, T Function() value) async {
    calls[path] = (calls[path] ?? 0) + 1;
    await Future<void>.delayed(Duration(milliseconds: ms));
    return value();
  }

  int operator [](String path) => calls[path] ?? 0;

  static final zen = ProviderInfo(
    id: 'opencode',
    name: 'OpenCode Zen',
    modelIDs: const ['big-pickle'],
  );
  ProvidersResponse providerList() => ProvidersResponse(
    providers: [zen],
    defaultProviderID: 'opencode',
    defaultModelID: 'big-pickle',
  );
}

class _Api extends OpenCodeApi {
  _Api(this.server) : super(baseUrl: 'http://127.0.0.1:1');
  final _Server server;

  @override
  Future<Health> health() => server.answer(
    '/global/health',
    40,
    () => Health(healthy: true, version: '1.14.0'),
  );
  @override
  Future<List<Session>> sessions() =>
      server.answer('/session', 150, () => const <Session>[]);
  @override
  Future<Map<String, String>> sessionStatuses() =>
      server.answer('/session/status', 50, () => const <String, String>{});
  @override
  Future<ProvidersResponse> providers() =>
      server.answer('/provider', 1192, server.providerList);
  @override
  Future<ProvidersResponse> configuredProviders() =>
      server.answer('/config/providers', 1029, server.providerList);
  @override
  Future<List<AgentInfo>> agents() =>
      server.answer('/agent', 300, () => [AgentInfo(name: 'build')]);
  @override
  Future<List<PermissionRequest>> pendingPermissions() =>
      server.answer('/permission', 180, () => const <PermissionRequest>[]);
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Repository extends SdkProductRepository {
  _Repository(OpenCodeApi api, this.server) : super(api.sdkClient);
  final _Server server;

  @override
  Future<ChatDefaults> loadChatDefaults() =>
      server.answer('/config', 1017, () => const ChatDefaults());
  @override
  Future<List<PendingQuestion>> listQuestions() =>
      server.answer('/question', 180, () => const <PendingQuestion>[]);
  @override
  Future<CatalogSnapshot> loadCatalog() => server.answer(
    '/api/model',
    500,
    () => const CatalogSnapshot(providers: [], models: [], agents: []),
  );
  @override
  Future<List<IntegrationInfo>> listIntegrations() async {
    // The real one reads /api/integration, /provider/auth and /provider.
    await server.answer('/api/integration', 525, () => null);
    await server.answer('/provider/auth', 595, () => null);
    return server.answer('/provider', 1192, () => const <IntegrationInfo>[]);
  }

  @override
  Future<void> refreshProviderRuntime() async {}
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
}

/// An event stream that connects 20 ms after it starts, like the phone's.
class _Events extends EventStream {
  _Events(
    this.server, {
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });
  final _Server server;
  Timer? _connect;

  @override
  void start() {
    onStatus(StreamStatus.connecting);
    if (!server.eventsConnect) return;
    _connect = Timer(
      const Duration(milliseconds: 20),
      () => onStatus(StreamStatus.connected),
    );
  }

  @override
  Future<void> dispose() async => _connect?.cancel();
}

Future<ConnectionController> _connect(
  WidgetTester tester,
  _Server server,
) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  // The one-time provider runtime migration ran on an earlier launch.
  await store.markProviderRuntimeRefreshed('phone');
  final controller = ConnectionController(
    store,
    apiFactory: (_) => _Api(server),
    repositoryFactory: (api) => _Repository(api, server),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _Events(
              server,
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  final connect = controller.connect(
    ServerProfile(id: 'phone', name: 'phone', baseUrl: 'http://127.0.0.1:1'),
  );
  await tester.pump(const Duration(seconds: 10));
  await connect;
  await tester.pump(const Duration(seconds: 10));
  expect(controller.catalog, isNotNull);
  return controller;
}

int _spans(String name) =>
    PerfTrace.spans.where((span) => span.name == name).length;

Future<void> _resume(
  WidgetTester tester,
  ConnectionController controller,
) async {
  controller.suspendForLifecycle();
  await tester.pump(const Duration(seconds: 10));
  final resume = controller.resumeFromLifecycle();
  await tester.pump(const Duration(seconds: 10));
  await resume;
}

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);
  setUp(() {
    PerfTrace.resetForTesting();
    PerfTrace.logSink = null;
  });
  tearDown(PerfTrace.resetForTesting);

  testWidgets('20 minutes of chat with 6 wakes and 3 chat opens load the '
      'catalog at most twice', (tester) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    for (var minute = 1; minute <= 20; minute++) {
      var rest = const Duration(minutes: 1);
      if (const {2, 5, 8, 11, 14, 17}.contains(minute)) {
        await _resume(tester, controller);
        rest -= const Duration(seconds: 20);
      }
      if (const {3, 9, 15}.contains(minute)) {
        // A chat opens its model picker and command sheet.
        unawaited(controller.ensureCatalog());
        unawaited(controller.ensureCatalog());
      }
      await tester.pump(rest);
    }
    // Was 10 loads, 20 GET /provider, 14 reads each of the waiting
    // permissions and questions.
    expect(_spans('lifecycle.resume'), 6);
    expect(_spans('catalog.load'), lessThanOrEqualTo(2));
    expect(server['/provider'], lessThanOrEqualTo(2));
    expect(server['/provider/auth'], 0);
    expect(server['/api/integration'], 0);
    expect(server['/permission'], lessThanOrEqualTo(7));
    expect(server['/question'], lessThanOrEqualTo(7));
    expect(controller.catalog, isNotNull);
    controller.dispose();
  });

  testWidgets('a wake is ready in one health check; the reload behind it '
      'skips a fresh catalog', (tester) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    final providerBefore = server['/provider'];
    controller.suspendForLifecycle();
    await tester.pump(const Duration(seconds: 10));
    final resume = controller.resumeFromLifecycle();
    var waited = Duration.zero;
    while (_spans('lifecycle.resume') == 0 &&
        waited < const Duration(seconds: 5)) {
      await tester.pump(const Duration(milliseconds: 10));
      waited += const Duration(milliseconds: 10);
    }
    // Was 2.5 s on the phone (1.6 s here): the span held the sessions,
    // waiting requests and the whole catalog reload.
    expect(waited, lessThan(const Duration(milliseconds: 300)));
    await tester.pump(const Duration(seconds: 5));
    await resume;
    expect(server['/provider'], providerBefore);
    expect(_spans('lifecycle.reload'), 1);
    controller.dispose();
  });

  testWidgets('concurrent readers share one load; Reload during a load '
      'queues exactly one more', (tester) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    expect(_spans('catalog.load'), 1);
    // Stale: a config change arrived.
    controller.handleEventForTesting(
      EventEnvelope(type: 'config.updated', properties: const {}),
    );
    unawaited(controller.ensureCatalog());
    unawaited(controller.ensureCatalog());
    final reload = controller.refreshCatalog();
    final again = controller.refreshCatalog();
    await tester.pump(const Duration(seconds: 20));
    await Future.wait([reload, again]);
    expect(_spans('catalog.load'), 3);
    // One GET /provider per load, never two.
    expect(server['/provider'], 3);
    expect(controller.catalogLoading, isFalse);
    controller.dispose();
  });

  testWidgets('a change event refreshes behind the list already shown', (
    tester,
  ) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    final shown = controller.catalog;
    controller.handleEventForTesting(
      EventEnvelope(type: 'catalog.updated', properties: const {}),
    );
    await tester.pump(const Duration(milliseconds: 100));
    expect(controller.catalog, same(shown));
    expect(controller.catalogLoading, isFalse);
    await tester.pump(const Duration(seconds: 10));
    expect(_spans('catalog.load'), 2);
    expect(controller.catalog, isNotNull);
    controller.dispose();
  });

  testWidgets('past ten minutes a reader refreshes it in the background', (
    tester,
  ) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    await controller.ensureCatalog();
    expect(_spans('catalog.load'), 1);
    await tester.pump(const Duration(minutes: 11));
    unawaited(controller.ensureCatalog());
    await tester.pump(const Duration(milliseconds: 100));
    expect(controller.catalogLoading, isFalse);
    expect(controller.catalog, isNotNull);
    await tester.pump(const Duration(seconds: 10));
    expect(_spans('catalog.load'), 2);
    controller.dispose();
  });

  testWidgets('a load cut off by going to the background is dropped and '
      'never leaves the list loading', (tester) async {
    final server = _Server();
    final controller = await _connect(tester, server);
    final reload = controller.refreshCatalog();
    await tester.pump(const Duration(milliseconds: 100));
    expect(controller.catalogLoading, isTrue);
    controller.suspendForLifecycle();
    await tester.pump(const Duration(seconds: 10));
    await reload;
    expect(controller.catalogLoading, isFalse);
    expect(controller.catalog, isNotNull);
    controller.dispose();
  });

  testWidgets('without an event stream the waiting requests are still read '
      'on a wake', (tester) async {
    final server = _Server()..eventsConnect = false;
    final controller = await _connect(tester, server);
    final permissions = server['/permission'];
    final questions = server['/question'];
    await _resume(tester, controller);
    expect(server['/permission'], permissions + 1);
    expect(server['/question'], questions + 1);
    controller.dispose();
    // A session poll may still be in flight on the fake server.
    await tester.pump(const Duration(seconds: 1));
  });
}
