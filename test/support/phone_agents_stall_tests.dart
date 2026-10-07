part of '../phone_agents_controller_test.dart';

void _turnStallControllerTests() {
  for (final entry in {
    TurnStallKind.modelSlow: const TurnStallEvidence(
      transportConnected: true,
      endpointReachable: true,
    ),
    TurnStallKind.helperDown: const TurnStallEvidence(
      transportConnected: true,
      helperRunning: false,
    ),
    TurnStallKind.network: const TurnStallEvidence(
      transportConnected: false,
      endpointReachable: false,
    ),
  }.entries) {
    testWidgets(
      'BA7 silent turn diagnoses ${entry.key.name} within sixty seconds',
      (tester) async {
        final w = await _world(tester);
        final c = w.controller;
        var elapsed = Duration.zero;
        var probes = 0;
        c.configureTurnStallForTesting(
          elapsed: () => elapsed,
          probe: () async {
            probes++;
            return entry.value;
          },
        );
        c.noteLocalTurn('silent');
        elapsed = const Duration(seconds: 44);
        await tester.pump(const Duration(seconds: 44));
        expect(c.turnStallFor('silent'), isNull);
        elapsed = const Duration(seconds: 45);
        await tester.pump(const Duration(seconds: 1));
        await tester.pump();
        expect(c.turnStallFor('silent')?.kind, entry.key);
        expect(probes, 1);
        // Other-session activity cannot hide the silent turn.
        c.handleEventForTesting(
          EventEnvelope(
            type: 'message.part.delta',
            properties: {'sessionID': 'other', 'delta': 'x'},
          ),
        );
        expect(c.turnStallFor('silent')?.kind, entry.key);
        c.handleEventForTesting(
          EventEnvelope(
            type: 'message.part.delta',
            properties: {'sessionID': 'silent', 'delta': 'x'},
          ),
        );
        expect(c.turnStallFor('silent'), isNull);
        c.dispose();
      },
    );
  }

  testWidgets(
    'BA7 waiting for permission pauses diagnosis and late probes cannot revive idle turn',
    (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      var elapsed = Duration.zero;
      final result = Completer<TurnStallEvidence>();
      c.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () => result.future,
      );
      c.noteLocalTurn('silent');
      c.permissions['ask'] = PermissionRequest(
        id: 'ask',
        sessionID: 'silent',
        permission: 'edit',
        patterns: const [],
      );
      elapsed = const Duration(seconds: 60);
      await tester.pump(const Duration(seconds: 60));
      expect(c.turnStallFor('silent'), isNull);
      c.permissions.clear();
      await tester.pump(const Duration(seconds: 5));
      elapsed = const Duration(seconds: 110);
      await tester.pump(const Duration(seconds: 45));
      c.handleEventForTesting(
        EventEnvelope(
          type: 'session.idle',
          properties: {'sessionID': 'silent'},
        ),
      );
      result.complete(
        const TurnStallEvidence(
          transportConnected: true,
          endpointReachable: true,
        ),
      );
      await tester.pump();
      expect(c.turnStallFor('silent'), isNull);
      c.dispose();
    },
  );
}
