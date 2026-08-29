import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ReplyApi extends OpenCodeApi {
  _ReplyApi() : super(baseUrl: 'http://localhost');

  final permissionReplies = <(String, String, String)>[];
  final questionReplies = <(String, String, List<List<String>>)>[];
  Object? failure;

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    if (failure case final error?) throw error;
    permissionReplies.add((sessionID, requestID, reply));
  }

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    if (failure case final error?) throw error;
    questionReplies.add((sessionID, requestID, answers));
  }
}

class _StubRepository extends ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

typedef _NativeCall = ({String method, Map<String, dynamic>? arguments});

Future<
  ({
    ConnectionController controller,
    BackgroundLiveController backgroundLive,
    _ReplyApi api,
    List<_NativeCall> calls,
  })
>
_harness() async {
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
  final api = _ReplyApi();
  final controller = ConnectionController(
    ProfileStore(prefs: preferences),
    backgroundLive: backgroundLive,
  )
    ..api = api
    ..repository = _StubRepository();
  controller.suspendForLifecycle();
  calls.clear();
  return (
    controller: controller,
    backgroundLive: backgroundLive,
    api: api,
    calls: calls,
  );
}

void _askPermission(ConnectionController controller) {
  controller.handleEventForTesting(
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
}

void _askQuestion(ConnectionController controller, {bool custom = true}) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'question.v2.asked',
      properties: {
        'id': 'question-1',
        'sessionID': 'session-1',
        'questions': [
          {
            'header': 'Direction',
            'question': 'How should this proceed?',
            'custom': custom,
            'options': [
              {'label': 'Ship it', 'description': ''},
            ],
          },
        ],
      },
    ),
  );
}

Future<void> _flush() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('notification Allow once resolves the exact pending permission', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askPermission(harness.controller);
    await _flush();

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'permission',
      'sessionID': 'session-1',
      'decision': 'allow',
      'requestID': 'permission-1',
    });

    expect(result, {'handled': true});
    expect(harness.api.permissionReplies, [
      ('session-1', 'permission-1', 'once'),
    ]);
    expect(harness.controller.permissions, isEmpty);
    await _flush();
    expect(
      harness.calls.where((call) => call.method == 'dismissCodingAlert'),
      isNotEmpty,
    );
  });

  test('notification Deny rejects without a durable grant option', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askPermission(harness.controller);
    await _flush();

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'permission',
      'sessionID': 'session-1',
      'decision': 'deny',
      'requestID': 'permission-1',
    });

    expect(result, {'handled': true});
    expect(harness.api.permissionReplies, [
      ('session-1', 'permission-1', 'reject'),
    ]);
  });

  test('notification quick reply answers a single custom question', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askQuestion(harness.controller);
    await _flush();

    final shown = harness.calls.lastWhere(
      (call) => call.method == 'showCodingAlert',
    );
    expect(shown.arguments?['quickReply'], isTrue);

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'question',
      'sessionID': 'session-1',
      'decision': 'reply',
      'requestID': 'question-1',
      'reply': '  Ship the fix  ',
    });

    expect(result, {'handled': true});
    expect(harness.api.questionReplies, hasLength(1));
    final (sessionID, requestID, answers) = harness.api.questionReplies.single;
    expect(sessionID, 'session-1');
    expect(requestID, 'question-1');
    expect(answers, [
      ['Ship the fix'],
    ]);
    expect(harness.controller.questions, isEmpty);
  });

  test('choice-only questions never advertise quick reply', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askQuestion(harness.controller, custom: false);
    await _flush();

    final shown = harness.calls.lastWhere(
      (call) => call.method == 'showCodingAlert',
    );
    expect(shown.arguments?['quickReply'], isFalse);

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'question',
      'sessionID': 'session-1',
      'decision': 'reply',
      'requestID': 'question-1',
      'reply': 'free text',
    });

    // Nothing eligible to answer; the alert lifecycle is refreshed and the
    // choice question stays pending for the full UI.
    expect(result, {'handled': true});
    expect(harness.api.questionReplies, isEmpty);
    expect(harness.controller.questions, hasLength(1));
  });

  test('a failed reply reports unhandled and keeps the request', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askPermission(harness.controller);
    await _flush();
    harness.api.failure = ApiException('server unreachable');
    harness.calls.clear();

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'permission',
      'sessionID': 'session-1',
      'decision': 'allow',
      'requestID': 'permission-1',
    });

    expect(result, {'handled': false});
    expect(harness.controller.permissions, hasLength(1));
    expect(
      harness.calls.where((call) => call.method == 'dismissCodingAlert'),
      isEmpty,
    );
  });

  test('an empty quick reply is refused before any network call', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askQuestion(harness.controller);
    await _flush();

    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'question',
      'sessionID': 'session-1',
      'decision': 'reply',
      'requestID': 'question-1',
      'reply': '   ',
    });

    expect(result, {'handled': false});
    expect(harness.api.questionReplies, isEmpty);
    expect(harness.controller.questions, hasLength(1));
  });

  test('a stale request ID never resolves a different pending request', () async {
    final harness = await _harness();
    addTearDown(harness.controller.dispose);
    _askPermission(harness.controller);
    harness.controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.asked',
        properties: const {
          'id': 'permission-2',
          'sessionID': 'session-1',
          'action': 'shell',
          'resources': ['rm -rf build'],
        },
      ),
    );
    await _flush();

    // The notification represented permission-1, which was resolved from the
    // app before the user tapped the action. permission-2 must survive.
    harness.controller.permissions.remove('permission-1');
    final result = await harness.backgroundLive.handleNativeAction({
      'kind': 'permission',
      'sessionID': 'session-1',
      'decision': 'allow',
      'requestID': 'permission-1',
    });

    expect(result, {'handled': true});
    expect(harness.api.permissionReplies, isEmpty);
    expect(harness.controller.permissions.keys, ['permission-2']);
  });
}
