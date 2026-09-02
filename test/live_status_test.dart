import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _debounce = Duration(milliseconds: 120);

Future<BackgroundLiveController> _live({
  required List<(String, Map<String, dynamic>?)> calls,
  bool enabled = true,
  bool active = true,
  Object? Function()? failWith,
}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: enabled,
  });
  final preferences = await SharedPreferences.getInstance();
  final controller = BackgroundLiveController(
    preferences: preferences,
    liveStatusDebounce: _debounce,
    invoke: (method, [arguments]) async {
      calls.add((method, arguments));
      if (method == 'updateLiveStatus' && failWith != null) {
        throw failWith()!;
      }
      return {
        'enabled': enabled,
        'active': active,
        'notificationGranted': true,
        'batteryOptimizationIgnored': true,
      };
    },
  );
  await controller.restore();
  calls.clear();
  return controller;
}

List<Map<String, dynamic>?> _statusCalls(
  List<(String, Map<String, dynamic>?)> calls,
) => [
  for (final call in calls)
    if (call.$1 == 'updateLiveStatus') call.$2,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('LiveStatus', () {
    test('serialises exactly the four fields Android reads', () {
      const status = LiveStatus(
        runningCount: 2,
        pendingCount: 1,
        title: 'Fix login',
        detail: 'Editing files…',
      );
      expect(status.toPayload(), {
        'runningCount': 2,
        'pendingCount': 1,
        'title': 'Fix login',
        'detail': 'Editing files…',
      });
      expect(LiveStatus.idle.toPayload(), {
        'runningCount': 0,
        'pendingCount': 0,
        'title': null,
        'detail': null,
      });
    });

    test('compares by value', () {
      expect(
        const LiveStatus(runningCount: 1, title: 'a'),
        equals(const LiveStatus(runningCount: 1, title: 'a')),
      );
      expect(
        const LiveStatus(runningCount: 1, title: 'a'),
        isNot(equals(const LiveStatus(runningCount: 1, title: 'b'))),
      );
      expect(
        const LiveStatus(runningCount: 1).hashCode,
        const LiveStatus(runningCount: 1).hashCode,
      );
    });
  });

  group('publishLiveStatus', () {
    test('is a no-op while live mode is off or the service is down', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final off = await _live(calls: calls, enabled: false, active: false);
      addTearDown(off.dispose);
      await off.publishLiveStatus(const LiveStatus(runningCount: 1));
      expect(_statusCalls(calls), isEmpty);

      final inactive = await _live(calls: calls, enabled: true, active: false);
      addTearDown(inactive.dispose);
      await inactive.publishLiveStatus(const LiveStatus(runningCount: 1));
      expect(_statusCalls(calls), isEmpty);
    });

    test('sends the first status at once and skips unchanged ones', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      addTearDown(live.dispose);

      const status = LiveStatus(runningCount: 1, title: 'Build feature');
      await live.publishLiveStatus(status);
      expect(_statusCalls(calls), [status.toPayload()]);
      expect(live.lastPublishedLiveStatus, status);

      await Future<void>.delayed(_debounce * 2);
      await live.publishLiveStatus(const LiveStatus(
        runningCount: 1,
        title: 'Build feature',
      ));
      expect(_statusCalls(calls), hasLength(1));
    });

    test('coalesces a burst into one trailing push with the newest', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      addTearDown(live.dispose);

      await live.publishLiveStatus(const LiveStatus(runningCount: 1));
      await live.publishLiveStatus(const LiveStatus(runningCount: 2));
      await live.publishLiveStatus(const LiveStatus(runningCount: 3));
      await live.publishLiveStatus(
        const LiveStatus(runningCount: 3, detail: 'Editing files…'),
      );
      expect(_statusCalls(calls), hasLength(1), reason: 'leading edge only');

      await Future<void>.delayed(_debounce * 2);
      final sent = _statusCalls(calls);
      expect(sent, hasLength(2));
      expect(sent.last, {
        'runningCount': 3,
        'pendingCount': 0,
        'title': null,
        'detail': 'Editing files…',
      });
    });

    test('a burst that returns to the shown status sends nothing', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      addTearDown(live.dispose);

      await live.publishLiveStatus(const LiveStatus(runningCount: 1));
      await live.publishLiveStatus(const LiveStatus(runningCount: 2));
      await live.publishLiveStatus(const LiveStatus(runningCount: 1));
      await Future<void>.delayed(_debounce * 2);
      expect(_statusCalls(calls), hasLength(1));
    });

    test('swallows a missing platform channel', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(
        calls: calls,
        failWith: () => MissingPluginException('no runner'),
      );
      addTearDown(live.dispose);
      await expectLater(
        live.publishLiveStatus(const LiveStatus(runningCount: 1)),
        completes,
      );
      expect(live.lastError, isNull);
    });

    test('a pause from the notification is not an Android timeout', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      addTearDown(live.dispose);

      live.handleNativeTimeout(const {
        'reason': BackgroundLiveController.pauseReason,
      });
      expect(live.enabled, isFalse);
      expect(live.active, isFalse);
      expect(live.stoppedByAndroidTimeout, isFalse);
      expect(
        live.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );

      live.handleNativeTimeout(const {'reason': 'systemTimeout'});
      expect(live.stoppedByAndroidTimeout, isTrue);
    });
  });

  group('ConnectionController.liveStatus', () {
    Session session(String id, {String? title, int updated = 0}) => Session(
      id: id,
      title: title,
      time: SessionTime(created: updated, updated: updated),
    );

    Future<ConnectionController> controllerWith(
      BackgroundLiveController live,
    ) async {
      final preferences = await SharedPreferences.getInstance();
      final controller = ConnectionController(
        ProfileStore(prefs: preferences),
        backgroundLive: live,
      );
      controller.sessionsById = {
        'old': session('old', title: 'Older run', updated: 10),
        'new': session('new', title: '  Fix login  ', updated: 20),
        'idle': session('idle', title: 'Idle one', updated: 30),
      };
      controller.busySessions = {'old', 'new'};
      controller.permissions = {
        'p1': PermissionRequest(id: 'p1', sessionID: 'new', permission: 'x'),
      };
      controller.questions = {
        'q1': PendingQuestion(id: 'q1', sessionID: 'old', prompts: const []),
      };
      return controller;
    }

    test('counts runs and requests and names the freshest busy session',
        () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      final controller = await controllerWith(live);
      addTearDown(controller.dispose);

      expect(
        controller.liveStatus(),
        const LiveStatus(runningCount: 2, pendingCount: 2, title: 'Fix login'),
      );
    });

    test('publishes through notifyListeners and tool part events', () async {
      final calls = <(String, Map<String, dynamic>?)>[];
      final live = await _live(calls: calls);
      final controller = await controllerWith(live);
      addTearDown(controller.dispose);

      controller.notifyListeners();
      expect(_statusCalls(calls), [
        {
          'runningCount': 2,
          'pendingCount': 2,
          'title': 'Fix login',
          'detail': null,
        },
      ]);

      controller.handleEventForTesting(
        EventEnvelope(type: 'message.part.updated', properties: {
          'part': {
            'type': 'tool',
            'sessionID': 'new',
            'tool': 'edit',
            'state': {'status': 'running'},
          },
        }),
      );
      expect(controller.liveStatus().detail, 'Editing files…');
      await Future<void>.delayed(_debounce * 2);
      expect(_statusCalls(calls).last?['detail'], 'Editing files…');

      controller.handleEventForTesting(
        EventEnvelope(type: 'message.part.updated', properties: {
          'part': {
            'type': 'tool',
            'sessionID': 'new',
            'tool': 'edit',
            'state': {'status': 'completed'},
          },
        }),
      );
      expect(controller.liveStatus().detail, isNull);
    });

    test('tool sentences never repeat the tool input', () {
      expect(ConnectionController.toolSentence('bash'), 'Running a command…');
      expect(ConnectionController.toolSentence('Edit'), 'Editing files…');
      expect(ConnectionController.toolSentence('grep'), 'Searching files…');
      expect(ConnectionController.toolSentence('mcp_thing'), 'Running mcp_thing…');
      expect(ConnectionController.toolSentence(''), 'Working…');
    });
  });
}
