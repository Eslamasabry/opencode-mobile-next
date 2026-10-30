// A turn whose ask the stream lost must not say "working" until the
// server's 60-minute sweep (docs/qa/oc2-stuck-tools-2026-10-25).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'support/connection_v2_fixtures.dart';

PermissionRequest _ask(String id, String sessionID) => PermissionRequest(
  id: id,
  sessionID: sessionID,
  permission: 'bash',
  patterns: const ['git status'],
);

/// A tool part the stream reported as waiting to run (an ask comes with it).
EventEnvelope _tool(String sessionID, String status) => EventEnvelope(
  type: 'message.part.updated',
  properties: {
    'sessionID': sessionID,
    'part': {
      'id': 'prt_$sessionID',
      'type': 'tool',
      'sessionID': sessionID,
      'tool': 'bash',
      'state': {'status': status},
    },
  },
);

Future<ConnectionController> _connected(
  WidgetTester tester,
  V2Api api, {
  QuestionRepository? repository,
}) async {
  final controller = ConnectionController(
    await memoryProfileStore(),
    apiFactory: (_) => api,
    repositoryFactory: (_) =>
        repository ?? QuestionRepository(legacyUnavailable: false),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  await controller.connect(
    ServerProfile(id: 'server', name: 'Server', baseUrl: 'http://127.0.0.1:1'),
  );
  // Past the permission read's own retries.
  await tester.pump(const Duration(seconds: 4));
  expect(controller.status, StreamStatus.connected);
  return controller;
}

void main() {
  testWidgets(
    'an ask the stream missed is read again while its turn waits on a tool',
    (tester) async {
      final api = V2Api();
      final controller = await _connected(tester, api);
      addTearDown(controller.dispose);
      var elapsed = Duration.zero;
      controller.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () async => const TurnStallEvidence(
          transportConnected: true,
          endpointReachable: true,
        ),
      );
      controller.noteLocalTurn('s1');
      controller.handleEventForTesting(_tool('s1', 'pending'));
      // The server now lists the ask, but its live event never arrived.
      api.permissions = [_ask('ask-1', 's1')];
      expect(controller.permissions, isEmpty);

      elapsed = const Duration(seconds: 16);
      await tester.pump(const Duration(seconds: 16));
      await tester.pump();

      expect(controller.permissions.keys, ['ask-1']);
      expect(controller.turnStallFor('s1'), isNull);
      controller.dispose();
    },
  );

  testWidgets(
    'a tool turn whose asks cannot be read reaches the stall notice',
    (tester) async {
      // OpenCode 2.0.29: the pending list answers 400 while a glob asks.
      final api = V2Api()
        ..permissionV2Error = ApiException(
          'Expected JSON value at data[2].metadata.hidden',
          statusCode: 400,
        );
      final controller = await _connected(tester, api);
      addTearDown(controller.dispose);
      expect(controller.permissionsError, isNotNull);
      var elapsed = Duration.zero;
      var probes = 0;
      controller.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () async {
          probes++;
          return const TurnStallEvidence(
            transportConnected: true,
            endpointReachable: true,
          );
        },
      );
      controller.noteLocalTurn('s1');
      controller.handleEventForTesting(_tool('s1', 'pending'));

      elapsed = const Duration(seconds: 44);
      await tester.pump(const Duration(seconds: 44));
      expect(controller.turnStallFor('s1'), isNull);
      elapsed = const Duration(seconds: 50);
      await tester.pump(const Duration(seconds: 6));
      await tester.pump();

      expect(probes, 1);
      expect(controller.turnStallFor('s1')?.kind, TurnStallKind.modelSlow);
      controller.dispose();
    },
  );

  testWidgets(
    'a long command with readable asks still holds the stall notice off',
    (tester) async {
      final api = V2Api();
      final controller = await _connected(tester, api);
      addTearDown(controller.dispose);
      var elapsed = Duration.zero;
      controller.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () async => const TurnStallEvidence(
          transportConnected: true,
          endpointReachable: true,
        ),
      );
      controller.noteLocalTurn('s1');
      controller.handleEventForTesting(_tool('s1', 'running'));
      elapsed = const Duration(minutes: 3);
      await tester.pump(const Duration(minutes: 3));
      await tester.pump();
      expect(controller.permissionsError, isNull);
      expect(controller.turnStallFor('s1'), isNull);
      controller.dispose();
    },
  );
}
