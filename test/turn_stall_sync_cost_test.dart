// The stall watch is brought up to date on a notify only while a turn is
// open or tracked; an idle connection's many notifies skip it (2026-10-27).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'support/connection_v2_fixtures.dart';

void main() {
  testWidgets('idle notifies skip the stall watch; an open turn keeps it', (
    tester,
  ) async {
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => V2Api(),
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
    await tester.pump(const Duration(seconds: 1));

    // A tool that finished leaves nothing tracked behind.
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'message.part.updated',
        properties: {
          'sessionID': 'idle',
          'part': {
            'id': 'prt_1',
            'type': 'tool',
            'sessionID': 'idle',
            'state': {'status': 'completed'},
          },
        },
      ),
    );
    final idle = controller.turnStallSyncsForTesting;
    for (var i = 0; i < 5; i++) {
      await controller.refreshPendingPermissions();
      await controller.refreshPendingQuestions();
    }
    await tester.pump();
    expect(controller.turnStallSyncsForTesting, idle);

    var elapsed = Duration.zero;
    controller.configureTurnStallForTesting(
      elapsed: () => elapsed,
      probe: () async => const TurnStallEvidence(
        transportConnected: true,
        endpointReachable: true,
      ),
    );
    controller.noteLocalTurn('s1');
    final open = controller.turnStallSyncsForTesting;
    await controller.refreshPendingPermissions();
    expect(controller.turnStallSyncsForTesting, greaterThan(open));

    // The turn still reaches its notice exactly as before.
    elapsed = turnStallSilence;
    await tester.pump(turnStallSilence);
    await tester.pump();
    expect(controller.turnStallFor('s1')?.kind, TurnStallKind.modelSlow);
    controller.dispose();
  });
}
