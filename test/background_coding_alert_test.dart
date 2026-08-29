import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

typedef _NativeCall = ({String method, Map<String, dynamic>? arguments});

Future<({ConnectionController controller, List<_NativeCall> calls})>
_backgroundController({bool backgrounded = true}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
  });
  final preferences = await SharedPreferences.getInstance();
  final calls = <_NativeCall>[];
  final backgroundLive = BackgroundLiveController(
    preferences: preferences,
    invoke: (method, [arguments]) async {
      calls.add((method: method, arguments: arguments));
      if (method == 'showCodingAlert') return const {'shown': true};
      if (method == 'dismissCodingAlert') return const {'dismissed': true};
      return const {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      };
    },
  );
  await backgroundLive.restore();
  calls.clear();
  final controller = ConnectionController(
    ProfileStore(prefs: preferences),
    backgroundLive: backgroundLive,
  );
  if (backgrounded) controller.suspendForLifecycle();
  return (controller: controller, calls: calls);
}

Future<void> _flushAlerts() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'background permission and question requests share one session alert',
    () async {
      final harness = await _backgroundController();
      addTearDown(harness.controller.dispose);

      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.v2.asked',
          properties: const {
            'id': 'permission-1',
            'sessionID': 'session-1',
            'action': 'edit',
            'resources': ['lib/main.dart'],
          },
        ),
      );
      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: const {
            'id': 'question-1',
            'sessionID': 'session-1',
            'questions': [
              {'header': 'Choice', 'question': 'Continue?', 'options': []},
            ],
          },
        ),
      );
      await _flushAlerts();

      final shown = harness.calls.where(
        (call) => call.method == 'showCodingAlert',
      );
      expect(shown, hasLength(1));
      expect(shown.single.arguments, {
        'kind': 'permission',
        'sessionID': 'session-1',
        'key': 'input:session-1',
        // A v2 permission alert carries the RemoteInput Reply action: its
        // text maps to reject-with-message (steering by rejection).
        'quickReply': true,
        'requestID': 'permission-1',
      });

      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.v2.replied',
          properties: const {
            'sessionID': 'session-1',
            'requestID': 'permission-1',
          },
        ),
      );
      await _flushAlerts();
      expect(
        harness.calls.where((call) => call.method == 'dismissCodingAlert'),
        isEmpty,
      );
      final updated = harness.calls
          .where((call) => call.method == 'showCodingAlert')
          .toList();
      expect(updated, hasLength(2));
      expect(updated.last.arguments, {
        'kind': 'question',
        'sessionID': 'session-1',
        'key': 'input:session-1',
        'quickReply': true,
        'requestID': 'question-1',
      });

      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.rejected',
          properties: const {
            'sessionID': 'session-1',
            'requestID': 'question-1',
          },
        ),
      );
      await _flushAlerts();
      expect(harness.calls.last.method, 'dismissCodingAlert');
      expect(harness.calls.last.arguments, {'key': 'input:session-1'});
    },
  );

  test(
    'a request already visible is alerted when the app backgrounds',
    () async {
      final harness = await _backgroundController(backgrounded: false);
      addTearDown(harness.controller.dispose);
      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: const {
            'id': 'question-1',
            'sessionID': 'session-1',
            'questions': [],
          },
        ),
      );
      await _flushAlerts();
      expect(harness.calls, isEmpty);

      harness.controller.suspendForLifecycle();
      await _flushAlerts();
      expect(harness.calls, hasLength(1));
      expect(harness.calls.single.method, 'showCodingAlert');
      expect(harness.calls.single.arguments, {
        'kind': 'question',
        'sessionID': 'session-1',
        'key': 'input:session-1',
        // No prompts means no single custom prompt to quick-reply to.
        'quickReply': false,
        'requestID': 'question-1',
      });
    },
  );

  test(
    'assistant completion survives message-done ordering and deduplicates',
    () async {
      final harness = await _backgroundController();
      addTearDown(harness.controller.dispose);
      harness.controller.sessionsById['session-1'] = Session(id: 'session-1');

      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'message.updated',
          properties: const {
            'info': {
              'id': 'message-1',
              'sessionID': 'session-1',
              'role': 'assistant',
              'time': {'created': 1},
            },
          },
        ),
      );
      harness.controller.handleEventForTesting(
        EventEnvelope(
          type: 'message.updated',
          properties: const {
            'info': {
              'id': 'message-1',
              'sessionID': 'session-1',
              'role': 'assistant',
              'time': {'created': 1, 'completed': 2},
            },
          },
        ),
      );
      expect(harness.controller.busySessions, isEmpty);

      final idle = EventEnvelope(
        type: 'session.idle',
        properties: {'sessionID': 'session-1'},
      );
      harness.controller.handleEventForTesting(idle);
      harness.controller.handleEventForTesting(idle);
      await _flushAlerts();

      final shown = harness.calls
          .where((call) => call.method == 'showCodingAlert')
          .toList();
      expect(shown, hasLength(1));
      expect(shown.single.arguments, {
        'kind': 'complete',
        'sessionID': 'session-1',
        'key': 'status:session-1',
        'quickReply': false,
        'requestID': '',
      });
    },
  );

  test(
    'background session errors replace completion and child alerts stay quiet',
    () async {
      final harness = await _backgroundController();
      addTearDown(harness.controller.dispose);
      harness.controller.sessionsById.addAll({
        'session-1': Session(id: 'session-1'),
        'child-1': Session(id: 'child-1', parentID: 'session-1'),
      });

      for (final sessionID in ['session-1', 'child-1']) {
        harness.controller.handleEventForTesting(
          EventEnvelope(
            type: 'session.status',
            properties: {
              'sessionID': sessionID,
              'status': const {'type': 'busy'},
            },
          ),
        );
        harness.controller.handleEventForTesting(
          EventEnvelope(
            type: 'session.error',
            properties: {
              'sessionID': sessionID,
              'error': const {'message': 'Private provider failure details'},
            },
          ),
        );
        harness.controller.handleEventForTesting(
          EventEnvelope(
            type: 'session.idle',
            properties: {'sessionID': sessionID},
          ),
        );
      }
      await _flushAlerts();

      final shown = harness.calls.where(
        (call) => call.method == 'showCodingAlert',
      );
      expect(shown, hasLength(1));
      expect(shown.single.arguments, {
        'kind': 'error',
        'sessionID': 'session-1',
        'key': 'status:session-1',
        'quickReply': false,
        'requestID': '',
      });
      expect(
        shown.single.arguments.toString(),
        isNot(contains('Private provider failure details')),
      );
    },
  );

  test('foreground resume dismisses outstanding coding alerts', () async {
    final harness = await _backgroundController();
    addTearDown(harness.controller.dispose);
    harness.controller.sessionsById['session-1'] = Session(id: 'session-1');
    harness.controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: const {
          'sessionID': 'session-1',
          'status': {'type': 'busy'},
        },
      ),
    );
    harness.controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.idle',
        properties: {'sessionID': 'session-1'},
      ),
    );
    await _flushAlerts();

    await harness.controller.resumeFromLifecycle();
    await _flushAlerts();
    expect(harness.calls.last.method, 'dismissCodingAlert');
    expect(harness.calls.last.arguments, {'key': 'status:session-1'});
  });
}
