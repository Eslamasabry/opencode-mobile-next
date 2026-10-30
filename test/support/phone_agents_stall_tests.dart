part of '../phone_agents_controller_test.dart';

void _turnStallControllerTests() {
  testWidgets(
    'BA7 busy snapshots without an owned transport have no watchdog wakeups',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final c = ConnectionController(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
      )..api = _OcApi(_OcScript());
      addTearDown(c.dispose);
      c.noteLocalTurn('snapshot');
      c.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: {
            'sessionID': 'snapshot',
            'status': {'type': 'busy'},
          },
        ),
      );
      await tester.pump();
      expect(c.busySessions, contains('snapshot'));
      expect(c.turnStallFor('snapshot'), isNull);
      // Test invariants run before addTearDown. A snapshot must own no timer.
    },
  );

  for (final end in [
    'idle',
    'deleted',
    'error',
    'dispose',
    'disconnect',
    'suspend',
  ]) {
    testWidgets('BA7 $end cancels a pending probe deadline', (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      var elapsed = Duration.zero;
      final hung = Completer<TurnStallEvidence>();
      var probes = 0;
      c.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () {
          probes++;
          return hung.future;
        },
      );
      c.noteLocalTurn('silent');
      elapsed = const Duration(seconds: 44);
      await tester.pump(elapsed);
      expect(probes, 0);
      elapsed = turnStallSilence;
      await tester.pump(const Duration(seconds: 1));
      expect(probes, 1);
      switch (end) {
        case 'idle':
          c.handleEventForTesting(
            EventEnvelope(
              type: 'session.idle',
              properties: {'sessionID': 'silent'},
            ),
          );
        case 'deleted':
          c.handleEventForTesting(
            EventEnvelope(
              type: 'session.deleted',
              properties: {
                'info': {'id': 'silent'},
              },
            ),
          );
        case 'error':
          c.handleEventForTesting(
            EventEnvelope(
              type: 'session.error',
              properties: {
                'sessionID': 'silent',
                'error': {'name': 'MessageAbortedError'},
              },
            ),
          );
        case 'dispose':
          c.dispose();
        case 'disconnect':
          await c.disconnect();
        case 'suspend':
          c.suspendForLifecycle();
      }
      await tester.pump();
      expect(c.turnStallFor('silent'), isNull);
      c.dispose();
      await tester.pump();
      // Leave the probe unresolved: teardown must find no timeout timer.
    });
  }

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

  testWidgets(
    'a running command is activity: no stall until it ends, then 45 s again',
    (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      var elapsed = Duration.zero;
      var probes = 0;
      c.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () async {
          probes++;
          return const TurnStallEvidence(
            transportConnected: true,
            endpointReachable: true,
          );
        },
      );
      void tool(String status) => c.handleEventForTesting(
        EventEnvelope(
          type: 'message.part.updated',
          properties: {
            'sessionID': 'long',
            'part': {
              'id': 'prt_1',
              'type': 'tool',
              'sessionID': 'long',
              'tool': 'bash',
              'state': {'status': status},
            },
          },
        ),
      );
      c.noteLocalTurn('long');
      tool('running');
      elapsed = const Duration(minutes: 3);
      await tester.pump(const Duration(minutes: 3));
      await tester.pump();
      expect(probes, 0);
      expect(c.turnStallFor('long'), isNull);
      tool('completed');
      elapsed = const Duration(minutes: 3, seconds: 44);
      await tester.pump(const Duration(seconds: 44));
      expect(probes, 0);
      elapsed = const Duration(minutes: 3, seconds: 50);
      await tester.pump(const Duration(seconds: 6));
      await tester.pump();
      expect(c.turnStallFor('long')?.kind, TurnStallKind.modelSlow);
      c.dispose();
    },
  );

  testWidgets(
    'a shell command still running after a return from the background is '
    'activity: no stall after more than 10 silent minutes (owner, 2026-10-27)',
    (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      var elapsed = Duration.zero;
      var probes = 0;
      c.configureTurnStallForTesting(
        elapsed: () => elapsed,
        probe: () async {
          probes++;
          return const TurnStallEvidence(
            transportConnected: true,
            endpointReachable: true,
          );
        },
      );
      // `timeout 900 flutter test …`: one long Bash call, then nothing.
      c.noteLocalTurn('long');
      c.handleEventForTesting(
        EventEnvelope(
          type: 'message.part.updated',
          properties: {
            'sessionID': 'long',
            'part': {
              'id': 'prt_flutter_test',
              'type': 'tool',
              'sessionID': 'long',
              'tool': 'bash',
              'state': {'status': 'running'},
            },
          },
        ),
      );
      // The person looks at another app and comes back: the link is rebuilt
      // and the running call is not sent again.
      c.suspendForLifecycle();
      await c.resumeFromLifecycle();
      c.notifyListeners();
      elapsed = const Duration(minutes: 11);
      await tester.pump(const Duration(minutes: 11));
      await tester.pump();
      expect(probes, 0);
      expect(c.turnStallFor('long'), isNull);
      expect(c.hasRunningTool, isTrue);
      c.dispose();
    },
  );

  testWidgets(
    'an alive but silent phone server is busy: quiet at first, one neutral line after a long command',
    (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      c
        ..localServerAliveProbe = (() async => true)
        ..localBusyLongAfter = Duration.zero;
      c.status = StreamStatus.disconnected;
      await tester.pump();
      await tester.pump();
      expect(c.localServerBusy, isTrue);
      expect(c.defersTurnInterruption, isTrue);
      expect(c.connectionStatus.visible, isFalse);
      c.handleEventForTesting(
        EventEnvelope(
          type: 'message.part.updated',
          properties: {
            'sessionID': 's',
            'part': {
              'id': 'p',
              'type': 'tool',
              'sessionID': 's',
              'state': {'status': 'running'},
            },
          },
        ),
      );
      await tester.pump(const Duration(seconds: 10));
      expect(c.connectionStatus.phase, ConnectionStatusPhase.serverBusy);
      expect(c.connectionStatus.visible, isTrue);
      c.localServerAliveProbe = () async => false;
      await tester.pump(const Duration(seconds: 10));
      expect(c.localServerBusy, isFalse);
      expect(c.connectionStatus.phase, isNot(ConnectionStatusPhase.serverBusy));
      c.dispose();
    },
  );
}
