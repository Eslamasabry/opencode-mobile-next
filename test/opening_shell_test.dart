// Cached opening shell (slice-speed-ui; docs/qa/codex-speed-2026-09-28
// contract item 1). Pumps the real AppBootstrapGate, app root, profile store
// and ConnectionController with fake transports, and records what the first
// frames after bootstrap show while the server has not answered yet.
//
// Before this slice the root showed only "Connecting to …" until the first
// health answer and the Work list showed skeleton rows until the first page.
// Now the titles this server listed last time show at once, read-only, with
// zero session reads and nothing marked connected or live.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_inventory_cache.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _url = 'https://100.64.0.1:4096';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: _url);

  final healthResult = Completer<Health>();
  final firstPage = Completer<ServerPage<Session>>();
  int healthCalls = 0;
  int sessionReads = 0;
  int healthChecksAfterFirst = 0;

  @override
  Future<Health> health() {
    healthCalls++;
    if (healthCalls > 1) {
      healthChecksAfterFirst++;
      return Future.value(Health(healthy: true));
    }
    return healthResult.future;
  }

  @override
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100}) {
    sessionReads++;
    return firstPage.future;
  }

  @override
  Future<List<Session>> sessions() {
    sessionReads++;
    return firstPage.future.then((page) => page.items);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<ProvidersResponse> configuredProviders() => providers();
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Operations extends SdkProductRepository {
  _Operations(OpenCodeApi api) : super(api.sdkClient);

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
}

class _Events extends EventStream {
  _Events({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  @override
  void start() => onStatus(StreamStatus.connecting);
  void connected() => onStatus(StreamStatus.connected);
  @override
  Future<void> dispose() async {}
}

class _Harness {
  _Harness(this.api, this.streams, this.controller);
  final _Api api;
  final List<_Events> streams;
  final ConnectionController Function() controller;
}

final _titles = [
  'Fix the login redirect loop',
  'Release notes for 1.0.45',
  'Why is the build slow on CI?',
];

/// Seeds the profile and, unless [cache] is false, the titles it listed
/// last time for [directory] (null: the location a restart restores).
Future<void> _seed({bool cache = true, String? directory}) async {
  // ProfileStore.load reads secure storage: without this mock it hangs.
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(secure, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'perf', 'name': 'Laptop', 'baseUrl': _url, 'username': ''},
    ]),
    'oc.activeProfile': 'perf',
  });
  if (!cache) return;
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  await SessionInventoryCache(prefs).save(
    'perf',
    SessionInventoryCache.scopeFor(store.profiles.single, directory, null),
    [
      for (var i = 0; i < _titles.length; i++)
        Session(
          id: 'cached-$i',
          title: _titles[i],
          time: SessionTime(
            created: 0,
            updated: DateTime.now()
                .subtract(Duration(hours: i + 1))
                .millisecondsSinceEpoch,
          ),
        ),
    ],
    isCurrent: () => true,
    fetchedAt: DateTime.now().subtract(const Duration(minutes: 12)),
  );
}

/// Pumps the real gate and root, completes bootstrap and pumps until the
/// connect attempt has asked for health once.
Future<_Harness> _open(WidgetTester tester) async {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(secure, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
  debugPlatformCapabilities = const PlatformCapabilities(
    platform: TargetPlatform.android,
    isWeb: true,
  );
  addTearDown(() => debugPlatformCapabilities = null);
  PerfTrace.resetForTesting();
  PerfTrace.logSink = null;
  addTearDown(PerfTrace.resetForTesting);
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final loaded = Completer<AppBootstrap>();
  final diagnostics = AppDiagnosticsController();
  addTearDown(diagnostics.dispose);
  final api = _Api();
  final streams = <_Events>[];
  ConnectionController? controller;
  await tester.pumpWidget(
    AppBootstrapGate(
      diagnostics: diagnostics,
      loader: () => loaded.future,
      controllerFactory: (store, diagnostics) =>
          controller = ConnectionController(
            store,
            diagnostics: diagnostics,
            apiFactory: (_) => api,
            repositoryFactory: (api) => _Operations(api),
            eventStreamFactory:
                ({required api, required onEvent, required onStatus, onError}) {
                  final stream = _Events(
                    api: api,
                    onEvent: onEvent,
                    onStatus: onStatus,
                    onError: onError,
                  );
                  streams.add(stream);
                  return stream;
                },
          ),
    ),
  );
  loaded.complete(AppBootstrap(store));
  for (var i = 0; i < 5 && api.healthCalls == 0; i++) {
    await tester.pump();
  }
  return _Harness(api, streams, () => controller!);
}

Future<void> _close(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  // Unmounting the shell schedules zero-length timers; let them run.
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}

Finder _lastKnownRow(int i) =>
    find.byKey(ValueKey('last-known-session-cached-$i'));

void main() {
  testWidgets('before: no saved titles, the root says only "Connecting"', (
    tester,
  ) async {
    await _seed(cache: false);
    final h = await _open(tester);
    expect(find.byKey(const ValueKey('saved-server-connecting')), findsOne);
    expect(find.byType(KitLastKnown), findsNothing);
    for (final title in _titles) {
      expect(find.text(title), findsNothing);
    }
    debugPrint(
      'OPENING_SHELL before: titles_visible=0 '
      'health_calls=${h.api.healthCalls} session_reads=${h.api.sessionReads}',
    );
    await _close(tester);
  });

  testWidgets('after: last titles show while health is still pending, with '
      'zero session reads and nothing marked live', (tester) async {
    await _seed();
    final h = await _open(tester);
    final conn = h.controller();

    // Health is held: the server has not answered anything yet.
    expect(h.api.healthCalls, 1);
    expect(h.api.sessionReads, 0);
    for (final title in _titles) {
      expect(find.text(title), findsOneWidget, reason: title);
    }
    expect(find.text('Updated 12 min ago · Refreshing'), findsOneWidget);
    // The connection state stays honest and in charge.
    expect(find.byKey(const ValueKey('saved-server-connecting')), findsOne);
    expect(find.text('Connected'), findsNothing);
    expect(find.byType(HomeScreen), findsNothing);
    expect(conn.hasConnectedServer, isFalse);
    expect(conn.sessionsById, isEmpty);
    // Read-only: no live session rows, nothing to tap on the titles.
    expect(find.byKey(const ValueKey('session-row-cached-0')), findsNothing);
    expect(
      find.descendant(
        of: find.byType(KitLastKnown),
        matching: find.byType(KitTappable),
      ),
      findsNothing,
    );
    await tester.tap(_lastKnownRow(0), warnIfMissed: false);
    await tester.pump();
    expect(find.byType(HomeScreen), findsNothing);
    debugPrint(
      'OPENING_SHELL after: titles_visible=${_titles.length} '
      'health_calls=${h.api.healthCalls} session_reads=${h.api.sessionReads} '
      'connected=${conn.hasConnectedServer} live_rows=${conn.sessionsById.length}',
    );

    // Held for a long time: still the titles, now under "not answering".
    await tester.pump(const Duration(seconds: 12));
    expect(find.text(_titles.first), findsOneWidget);
    expect(conn.hasConnectedServer, isFalse);
    await _close(tester);
  });

  testWidgets('the connecting page stays below the status bar', (tester) async {
    // A phone with a 40 dp status bar and a 24 dp gesture bar.
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(412, 915);
    tester.view.padding = const FakeViewPadding(top: 40, bottom: 24);
    tester.view.viewPadding = const FakeViewPadding(top: 40, bottom: 24);
    addTearDown(tester.view.reset);
    await _seed();
    await _open(tester);
    final page = find.byKey(const ValueKey('opening-shell'));
    expect(page, findsOneWidget);
    expect(tester.getTopLeft(page).dy, greaterThanOrEqualTo(40));
    expect(tester.getBottomLeft(page).dy, lessThanOrEqualTo(915 - 24));
    await _close(tester);
  });

  testWidgets('a failed connect keeps the titles, no longer "Refreshing"', (
    tester,
  ) async {
    await _seed();
    final h = await _open(tester);
    h.api.healthResult.completeError(
      ApiException('connection refused', statusCode: 0),
    );
    await tester.pump();
    await tester.pump();
    expect(find.byKey(const ValueKey('saved-server-failed')), findsOne);
    expect(find.text(_titles.first), findsOneWidget);
    expect(find.text('Updated 12 min ago'), findsOneWidget);
    expect(find.textContaining('Refreshing'), findsNothing);
    // The raw failure is not copy on the page.
    expect(find.text('connection refused'), findsNothing);
    expect(h.controller().hasConnectedServer, isFalse);
    await _close(tester);
  });

  testWidgets('titles saved for another project never show', (tester) async {
    await _seed(directory: '/elsewhere');
    await _open(tester);
    expect(find.byType(KitLastKnown), findsNothing);
    expect(find.text(_titles.first), findsNothing);
    expect(find.byKey(const ValueKey('saved-server-connecting')), findsOne);
    await _close(tester);
  });
}
