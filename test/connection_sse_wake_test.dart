import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/connection_sse_fixtures.dart';

/// Integrations answer only when the test completes [integrations]; counts
/// catalog loads, so a test can see what waits for what.
class _SlowIntegrationsRepository extends TestRepository {
  _SlowIntegrationsRepository(super.api, this.integrations, this.catalogLoads);

  final Completer<List<IntegrationInfo>>? integrations;
  final List<String> catalogLoads;

  @override
  Future<List<IntegrationInfo>> listIntegrations() =>
      integrations?.future ?? Future.value(const []);

  @override
  Future<CatalogSnapshot> loadCatalog() {
    catalogLoads.add('catalog');
    return super.loadCatalog();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('v2 requests and PTY lifecycle update reducer signals', (
    tester,
  ) async {
    final controller = ConnectionController(await memoryProfileStore());

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.asked',
        properties: const {
          'id': 'permission-1',
          'sessionID': 'session-1',
          'action': 'bash',
          'resources': ['git status'],
          'save': ['git *'],
          'metadata': {'cwd': '/work'},
          'source': {
            'type': 'tool',
            'messageID': 'message-1',
            'callID': 'call-1',
          },
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.asked',
        properties: const {
          'id': 'question-1',
          'sessionID': 'session-1',
          'questions': [
            {
              'header': 'Choice',
              'question': 'Continue?',
              'multiple': false,
              'custom': true,
              'options': [
                {'label': 'Yes', 'description': 'Continue'},
              ],
            },
          ],
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.created',
        properties: const {
          'sessionID': 'session-1',
          'info': {'id': 'session-1', 'title': 'New'},
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'pty.exited',
        properties: const {'id': 'pty-1', 'exitCode': 0},
      ),
    );

    expect(controller.permissions['permission-1']?.permission, 'bash');
    expect(controller.permissions['permission-1']?.patterns, ['git status']);
    expect(controller.permissions['permission-1']?.tool?.callID, 'call-1');
    expect(controller.questions, contains('question-1'));
    expect(controller.sessionsById, contains('session-1'));
    expect(controller.ptyRevision, 1);
    expect(controller.lastPtyEvent?.type, 'pty.exited');

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.replied',
        properties: const {
          'sessionID': 'session-1',
          'requestID': 'permission-1',
          'reply': 'once',
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.rejected',
        properties: const {'sessionID': 'session-1', 'requestID': 'question-1'},
      ),
    );
    expect(controller.permissions, isEmpty);
    expect(controller.questions, isEmpty);
    controller.dispose();
  });

  test(
    'installation events retain exact available and installed versions',
    () async {
      final controller = ConnectionController(await memoryProfileStore())
        ..version = '1.18.23';

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': 'latest'},
        ),
      );
      expect(controller.availableServerVersion, isNull);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.availableServerVersion, '1.19.0');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.updated',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.availableServerVersion, isNull);
      expect(controller.installedServerVersion, '1.19.0');
      expect(controller.version, '1.18.23');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'server.connected',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.version, '1.19.0');
      expect(controller.installedServerVersion, isNull);
      controller.dispose();
    },
  );

  testWidgets('polling runs only while SSE is unavailable', (tester) async {
    final api = ControlledApi('poll');
    final controller = ConnectionController(await memoryProfileStore())
      ..api = api;
    controller.enablePollingFallback();

    expect(controller.pollingFallbackEnabled, isTrue);
    controller.status = StreamStatus.connected;
    await tester.pump(const Duration(seconds: 5));
    expect(api.sessionsCalls, 0);

    controller.status = StreamStatus.reconnecting;
    await tester.pump(const Duration(seconds: 5));
    await tester.pump();
    expect(api.sessionsCalls, 1);
    controller.dispose();
  });

  testWidgets('failed new-location refresh cannot publish old sessions', (
    tester,
  ) async {
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final oldSessions = Completer<List<Session>>();
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        if (apis.isEmpty) {
          api.sessionsResult = oldSessions;
        } else {
          api.sessionsFailure = ApiException('new location unavailable');
        }
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();
    expect(apis.single.sessionsCalls, 1);

    final selection = controller.selectLocation(directory: '/new');
    oldSessions.complete([Session(id: 'old-session')]);
    await selection;
    await tester.pump();

    expect(controller.sessionsById, isEmpty);
    expect(controller.sessionsError, contains('new location unavailable'));
    expect(controller.locationError, isNotNull);
    controller.dispose();
  });

  testWidgets(
    'keep-live wake reconciliation is shared with foreground actions',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final backgroundLive = BackgroundLiveController(
        preferences: store.prefs,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          return const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      final apis = <ControlledApi>[];
      final streams = <FakeEventStream>[];
      final controller = ConnectionController(
        store,
        backgroundLive: backgroundLive,
        apiFactory: (profile) {
          final api = ControlledApi(profile.id);
          apis.add(api);
          return api;
        },
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory(streams),
      );

      final connect = controller.connect(testProfile('server'));
      await tester.pump();
      apis.single.healthResult.complete(Health(healthy: true, version: '1'));
      await connect;
      await tester.pump();
      final api = apis.single;
      final healthCallsBeforeWake = api.healthCalls;

      controller.suspendForLifecycle();
      expect(controller.lifecycleSuspended, isFalse);

      final resume = controller.resumeFromLifecycle();
      final actionApi = controller.prepareActionTransport();
      await resume;
      expect(await actionApi, same(api));
      expect(api.healthCalls, healthCallsBeforeWake + 1);

      expect(await controller.prepareActionTransport(), same(api));
      expect(api.healthCalls, healthCallsBeforeWake + 1);
      controller.dispose();
    },
  );

  testWidgets('stale keep-live transport is rebuilt before an action', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final backgroundLive = BackgroundLiveController(
      preferences: store.prefs,
      invoke: (method, [arguments]) async {
        if (method == 'getBackgroundPause') {
          return const {
            'supported': true,
            'active': false,
            'paused': false,
            'reason': 'none',
            'at': null,
            'canResume': false,
          };
        }
        return const {
          'enabled': true,
          'active': true,
          'notificationGranted': true,
          'batteryOptimizationIgnored': false,
        };
      },
    );
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final controller = ConnectionController(
      store,
      backgroundLive: backgroundLive,
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    final staleApi = apis.single
      ..healthFailure = ApiException('stale transport');
    controller.suspendForLifecycle();
    final actionApi = controller.prepareActionTransport();
    await tester.pump();

    expect(apis, hasLength(2));
    expect(staleApi.closed, isTrue);
    apis.last.healthResult.complete(Health(healthy: true, version: '2'));
    expect(await actionApi, same(apis.last));
    expect(controller.version, '2');
    controller.dispose();
  });

  testWidgets('opening another folder does not wait for the catalog', (
    tester,
  ) async {
    final apis = <ControlledApi>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        // After the first connect, the catalog never answers: a phone-hosted
        // OpenCode 1 takes seconds to build its 6 MB provider list.
        if (apis.isNotEmpty) api.providersResult = Completer();
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory([]),
    );
    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();
    final shownBefore = controller.providers;

    var opened = false;
    unawaited(
      controller
          .selectLocation(directory: '/work/other')
          .then((_) => opened = true),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    final folderApi = apis.last;
    folderApi.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }

    // Open with the catalog still loading, and the server's models kept on
    // screen meanwhile instead of an empty picker.
    expect(opened, isTrue);
    expect(controller.locationLoading, isFalse);
    expect(controller.directory, '/work/other');
    expect(controller.providers, same(shownBefore));
    controller.dispose();
  });

  testWidgets('the first connect does not wait for the one-time provider '
      'runtime refresh; the catalog loads after it', (tester) async {
    // Android 15 run, 2026-09-24: 7.5 s of the 12.2 s "Start OpenCode" setup
    // step was this refresh, before any conversation could load.
    final apis = <ControlledApi>[];
    final integrations = Completer<List<IntegrationInfo>>();
    final catalogLoads = <String>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) =>
          _SlowIntegrationsRepository(api, integrations, catalogLoads),
      eventStreamFactory: streamFactory([]),
    );
    var connected = false;
    unawaited(
      controller.connect(testProfile('server')).then((_) => connected = true),
    );
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(connected, isTrue);
    expect(apis.single.sessionsCalls, greaterThan(0));
    expect(catalogLoads, isEmpty);

    integrations.complete(const []);
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(catalogLoads, isNotEmpty);
    controller.dispose();
  });

  testWidgets('a new folder opens before the one-time provider runtime '
      'refresh, and its catalog loads after it', (tester) async {
    // Android 15 run, 2026-09-24: creating a project took 7.6 s, 6.8 s of it
    // this refresh (/api/integration, then /provider) that only the model
    // list needs.
    final apis = <ControlledApi>[];
    final integrations = Completer<List<IntegrationInfo>>();
    final catalogLoads = <String>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) => _SlowIntegrationsRepository(
        api,
        apis.length > 1 ? integrations : null,
        catalogLoads,
      ),
      eventStreamFactory: streamFactory([]),
    );
    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    var opened = false;
    unawaited(
      controller
          .selectLocation(directory: '/work/new-project')
          .then((_) => opened = true),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    apis.last.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(opened, isTrue);
    expect(controller.locationLoading, isFalse);
    expect(controller.directory, '/work/new-project');
    final before = catalogLoads.length;

    integrations.complete(const []);
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(catalogLoads.length, greaterThan(before));
    controller.dispose();
  });

  testWidgets('an action after a wake does not wait for the catalog reload', (
    tester,
  ) async {
    final apis = <ControlledApi>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory([]),
    );
    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();
    // A catalog younger than ten minutes is not reloaded on a wake at all;
    // this one is stale.
    await tester.pump(const Duration(minutes: 11));

    controller.suspendForLifecycle();
    final action = controller.prepareActionTransport();
    final woken = apis.last;
    expect(woken, isNot(same(apis.first)));
    // OpenCode 1's /provider is megabytes a phone-hosted server takes
    // seconds to build; the sessions list is cheap.
    woken
      ..sessionsResult = Completer<List<Session>>()
      ..providersResult = Completer<ProvidersResponse>();
    Object? actionApi;
    unawaited(action.then((value) => actionApi = value));
    woken.healthResult.complete(Health(healthy: true, version: '1'));
    await tester.pump();

    expect(actionApi, same(woken));
    // The catalog waits for the cheap reloads so the single-threaded server
    // does not make them queue behind it.
    expect(woken.sessionsCalls, 1);
    expect(woken.providersCalls, 0);
    woken.sessionsResult!.complete([Session(id: 'session-1')]);
    await tester.pump();
    expect(controller.sessionsById, contains('session-1'));
    expect(woken.providersCalls, 1);
    woken.providersResult!.complete(ProvidersResponse(providers: const []));
    await tester.pump();
    expect(controller.catalogLoading, isFalse);
    controller.dispose();
  });
}
