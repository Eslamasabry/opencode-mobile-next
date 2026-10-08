import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/connection_v2_fixtures.dart';

class _FailingProfileStore extends ProfileStore {
  _FailingProfileStore({required super.prefs});

  @override
  Future<void> setActiveId(String? id) =>
      Future.error(StateError('disk is read-only'));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'health false and active-profile persistence failure fail closed',
    (tester) async {
      final unhealthyApi = V2Api(healthy: false);
      final unhealthyController = ConnectionController(
        await memoryProfileStore(),
        apiFactory: (_) => unhealthyApi,
        repositoryFactory: (_) => QuestionRepository(),
      );
      addTearDown(unhealthyController.dispose);
      await unhealthyController.connect(
        ServerProfile(
          id: 'unhealthy',
          name: 'Unhealthy',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      expect(unhealthyController.api, isNull);
      expect(unhealthyController.status, StreamStatus.disconnected);
      expect(unhealthyController.lastError, contains('unhealthy'));
      expect(unhealthyApi.closed, isTrue);

      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final unwritableApi = V2Api();
      final unwritableController = ConnectionController(
        _FailingProfileStore(prefs: prefs),
        apiFactory: (_) => unwritableApi,
        repositoryFactory: (_) => QuestionRepository(),
      );
      addTearDown(unwritableController.dispose);
      await unwritableController.connect(
        ServerProfile(
          id: 'unwritable',
          name: 'Unwritable',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      expect(unwritableController.api, isNull);
      expect(unwritableController.status, StreamStatus.disconnected);
      expect(unwritableController.lastError, contains('Could not save'));
      expect(unwritableApi.healthCalls, 0);
      expect(unwritableApi.closed, isTrue);
      // Each attempt keeps its eight-second connection-status grace clock
      // until it runs out or the controller is disposed (3d64653c); let both
      // run out inside the test instead of leaving them to tearDown.
      await tester.pump(const Duration(seconds: 8));
    },
  );

  testWidgets('lifecycle suspend and concurrent resume recreate one location', (
    tester,
  ) async {
    final apis = <V2Api>[];
    final streams = <FakeEventStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) {
        final api = V2Api();
        apis.add(api);
        return api;
      },
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            );
            streams.add(stream);
            return stream;
          },
    );
    addTearDown(controller.dispose);
    final profile = ServerProfile(
      id: 'server',
      name: 'Server',
      baseUrl: 'http://127.0.0.1:1',
    );
    await controller.connect(profile);
    await tester.pump();
    await controller.selectLocation(
      directory: '/work/project',
      workspace: 'workspace-1',
    );
    expect(apis, hasLength(2));
    final refreshRevisionBeforeResume = controller.dataRefreshRevision;

    final suspendedApi = apis.last;
    final suspendedStream = streams.last;
    controller.suspendForLifecycle();
    expect(suspendedApi.closed, isTrue);
    expect(suspendedStream.disposed, isTrue);
    expect(controller.api, isNull);
    expect(controller.directory, '/work/project');
    expect(controller.workspace, 'workspace-1');

    final firstResume = controller.resumeFromLifecycle();
    final secondResume = controller.resumeFromLifecycle();
    expect(secondResume, same(firstResume));
    await Future.wait([firstResume, secondResume]);

    expect(apis, hasLength(3));
    expect(apis.last.directory, '/work/project');
    expect(apis.last.workspace, 'workspace-1');
    expect(apis.last.healthCalls, 1);
    expect(controller.status, StreamStatus.connected);
    expect(controller.dataRefreshRevision, refreshRevisionBeforeResume + 1);
    controller.dispose();
  });

  testWidgets('a second suspend invalidates an in-flight lifecycle resume', (
    tester,
  ) async {
    final delayedHealth = Completer<Health>();
    final apis = <V2Api>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) {
        final api = V2Api();
        if (apis.length == 1) api.healthCompleter = delayedHealth;
        apis.add(api);
        return api;
      },
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);
    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    controller.suspendForLifecycle();

    final staleResume = controller.resumeFromLifecycle();
    expect(apis, hasLength(2));
    controller.suspendForLifecycle();
    expect(apis[1].closed, isTrue);
    final currentResume = controller.resumeFromLifecycle();
    expect(apis, hasLength(3));

    delayedHealth.complete(Health(healthy: true, version: 'stale'));
    await Future.wait([staleResume, currentResume]);
    expect(controller.api, same(apis[2]));
    expect(controller.version, 'v2');
    expect(controller.status, StreamStatus.connected);
    controller.dispose();
  });
}
