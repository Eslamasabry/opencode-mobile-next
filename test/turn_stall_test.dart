import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';

void main() {
  test('changed liveness evidence rechecks without resetting silence', () {
    var elapsed = Duration.zero;
    final tracker = TurnStallTracker(elapsed: () => elapsed);
    tracker.begin('turn');
    elapsed = const Duration(seconds: 45);
    final token = tracker.takeProbe('turn')!;
    tracker.completeProbe(
      token,
      const TurnStallEvidence(
        transportConnected: true,
        endpointReachable: true,
      ),
    );
    expect(tracker.diagnosisFor('turn')?.kind, TurnStallKind.modelSlow);
    tracker.invalidateEvidence('turn');
    expect(tracker.dueSessionIds, ['turn']);
    tracker.completeProbe(
      tracker.takeProbe('turn')!,
      const TurnStallEvidence(transportConnected: false),
    );
    expect(tracker.diagnosisFor('turn')?.kind, TurnStallKind.network);
  });
  const reachable = TurnStallEvidence(
    transportConnected: true,
    helperRunning: true,
    endpointReachable: true,
  );

  test(
    'BA7 classifies healthy silence as model slow, stopped helper and connection loss',
    () {
      expect(classifyTurnStall(reachable), TurnStallKind.modelSlow);
      expect(
        classifyTurnStall(
          const TurnStallEvidence(
            transportConnected: true,
            helperRunning: false,
            endpointReachable: true,
          ),
        ),
        TurnStallKind.helperDown,
      );
      expect(
        classifyTurnStall(
          const TurnStallEvidence(
            transportConnected: false,
            helperRunning: true,
            endpointReachable: true,
          ),
        ),
        TurnStallKind.network,
      );
      expect(
        classifyTurnStall(
          const TurnStallEvidence(
            transportConnected: true,
            helperRunning: true,
            endpointReachable: false,
          ),
        ),
        TurnStallKind.network,
      );
      expect(
        classifyTurnStall(const TurnStallEvidence(transportConnected: true)),
        TurnStallKind.network,
      );
    },
  );

  testWidgets(
    'BA7 timer cadence and one hung probe remain within sixty seconds',
    (tester) async {
      var now = Duration.zero;
      final tracker = TurnStallTracker(elapsed: () => now)..begin('one');
      final hung = Completer<TurnStallEvidence>();
      TurnStallDiagnosis? result;
      now = turnStallSilence + turnStallTick;
      final token = tracker.takeProbe('one')!;
      boundedTurnStallProbe(
        probe: () => hung.future,
        transportConnected: true,
      ).then((evidence) {
        result = tracker.completeProbe(token, evidence);
      });
      await tester.pump();
      await tester.pump(turnStallProbeBudget - const Duration(milliseconds: 1));
      expect(result, isNull);
      now += turnStallProbeBudget;
      await tester.pump(const Duration(milliseconds: 1));
      expect(result?.kind, TurnStallKind.network);
      expect(result?.silentFor, const Duration(seconds: 60));
      expect(
        result?.message,
        'The connection could not be checked. Reconnect and try again.',
      );
      hung.complete(reachable);
      await tester.pump();
      expect(result?.kind, TurnStallKind.network);
    },
  );

  test('BA7 throwing probe returns only safe unknown evidence', () async {
    final evidence = await boundedTurnStallProbe(
      probe: () => throw StateError('private technical detail'),
      transportConnected: true,
    );
    expect(evidence.helperRunning, isNull);
    expect(evidence.endpointReachable, isNull);
    expect(classifyTurnStall(evidence), TurnStallKind.network);
  });

  test(
    'BA7 silence is per turn and progress invalidates an in-flight probe',
    () {
      var now = Duration.zero;
      final tracker = TurnStallTracker(elapsed: () => now)
        ..begin('one')
        ..begin('two');
      now = const Duration(seconds: 44);
      expect(tracker.dueSessionIds, isEmpty);
      tracker.noteProgress('two');
      now = const Duration(seconds: 45);
      expect(tracker.dueSessionIds, ['one']);
      final stale = tracker.takeProbe('one')!;
      expect(tracker.takeProbe('one'), isNull);
      tracker.noteProgress('one');
      expect(tracker.completeProbe(stale, reachable), isNull);
      now += turnStallSilence;
      expect(tracker.dueSessionIds, ['one', 'two']);
      final fresh = tracker.takeProbe('one')!;
      expect(
        tracker.completeProbe(fresh, reachable)?.kind,
        TurnStallKind.modelSlow,
      );
      expect(
        tracker.diagnosisFor('one')?.message,
        'The model may be taking longer. Wait or stop and try again.',
      );
      expect(tracker.takeProbe('one'), isNull);
      tracker.noteProgress('one');
      expect(tracker.diagnosisFor('one'), isNull);
    },
  );

  test(
    'BA7 waiting for user suppresses stalls and restarts silence when answered',
    () {
      var now = Duration.zero;
      final tracker = TurnStallTracker(elapsed: () => now)..begin('one');
      now = turnStallSilence;
      final token = tracker.takeProbe('one')!;
      tracker.setWaiting('one', true);
      expect(tracker.completeProbe(token, reachable), isNull);
      now = const Duration(minutes: 5);
      expect(tracker.dueSessionIds, isEmpty);
      tracker.setWaiting('one', false);
      expect(tracker.takeProbe('one'), isNull);
      now += turnStallSilence;
      expect(tracker.takeProbe('one'), isNotNull);
    },
  );

  test('BA7 completed, replaced and reset connections discard old probes', () {
    var now = Duration.zero;
    final tracker = TurnStallTracker(elapsed: () => now)..begin('one');
    now = turnStallSilence;
    final old = tracker.takeProbe('one')!;
    tracker.finish('one');
    tracker.begin('one');
    expect(tracker.completeProbe(old, reachable), isNull);
    now += turnStallSilence;
    final current = tracker.takeProbe('one')!;
    tracker.clear();
    expect(tracker.completeProbe(current, reachable), isNull);
    expect(tracker.dueSessionIds, isEmpty);
  });

  test('BA7 repeated busy statuses do not keep resetting silence', () {
    var now = Duration.zero;
    final tracker = TurnStallTracker(elapsed: () => now)..begin('one');
    now = const Duration(seconds: 40);
    tracker.begin('one');
    now = turnStallSilence;
    expect(tracker.takeProbe('one'), isNotNull);
  });

  test('BA7 tracker memory is bounded and blank session ids are ignored', () {
    var now = Duration.zero;
    final tracker = TurnStallTracker(elapsed: () => now, maximumSessions: 2)
      ..begin('')
      ..begin('one')
      ..begin('two')
      ..begin('three');
    now = turnStallSilence;
    expect(tracker.dueSessionIds, ['two', 'three']);
    tracker.noteProgress('one');
    tracker.setWaiting('unknown', true);
    expect(tracker.takeProbe('one'), isNull);
  });
}
