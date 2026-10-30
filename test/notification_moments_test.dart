import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/notification_preferences.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');
  final questionReplies = <List<List<String>>>[];
  final aborted = <String>[];

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async => questionReplies.add(answers);

  @override
  Future<void> abort(String sessionID) async => aborted.add(sessionID);
}

class _StubRepository extends ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

typedef _Call = ({String method, Map<String, dynamic>? arguments});

Future<
  ({
    ConnectionController controller,
    BackgroundLiveController live,
    _Api api,
    List<_Call> calls,
  })
>
_harness({bool quiet = false}) async {
  final now = DateTime.now();
  final minute = now.hour * 60 + now.minute;
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
    if (quiet) ...{
      NotificationPreferences.versionKey: 1,
      NotificationPreferences.quietStartKey: (minute - 60) % 1440,
      NotificationPreferences.quietEndKey: (minute + 60) % 1440,
    },
  });
  final preferences = await SharedPreferences.getInstance();
  final calls = <_Call>[];
  final live = BackgroundLiveController(
    preferences: preferences,
    liveStatusDebounce: Duration.zero,
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
      if (method == 'updateLiveStatus') return const {'updated': true};
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
  await live.restore();
  final api = _Api();
  final controller =
      ConnectionController(
          ProfileStore(prefs: preferences),
          backgroundLive: live,
        )
        ..api = api
        ..repository = _StubRepository();
  controller.sessionsById['session-1'] = Session(
    id: 'session-1',
    title: 'Fix login',
    directory: '/home/me/api-server',
  );
  controller.suspendForLifecycle();
  calls.clear();
  return (controller: controller, live: live, api: api, calls: calls);
}

Future<void> _flush() => Future<void>.delayed(const Duration(milliseconds: 20));

void _ask(
  ConnectionController controller, {
  String id = 'permission-1',
  Map<String, dynamic>? permission,
}) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'permission.asked',
      properties: {
        'id': id,
        'sessionID': 'session-1',
        'permission': 'bash',
        'patterns': const <String>[],
        'metadata': permission ?? const {'command': 'npm run build'},
      },
    ),
  );
}

void _question(ConnectionController controller, List<String> labels) {
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
            'custom': false,
            'options': [
              for (final label in labels) {'label': label, 'description': ''},
            ],
          },
        ],
      },
    ),
  );
}

List<_Call> _shown(List<_Call> calls) =>
    calls.where((call) => call.method == 'showCodingAlert').toList();

void _busy(ConnectionController controller) => controller.handleEventForTesting(
  EventEnvelope(
    type: 'session.status',
    properties: const {
      'sessionID': 'session-1',
      'status': {'type': 'busy'},
    },
  ),
);

void _idle(ConnectionController controller) => controller.handleEventForTesting(
  EventEnvelope(
    type: 'session.status',
    properties: const {
      'sessionID': 'session-1',
      'status': {'type': 'idle'},
    },
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('what the shade shows, never the lock screen', () {
    test('a permission alert asks to run the command it shows', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _ask(h.controller);
      await _flush();
      final args = _shown(h.calls).single.arguments!;
      // "Run npm run?" over the exact command; server · project below.
      expect(args['askAction'], 'run');
      expect(args['askTarget'], 'npm run');
      expect(args['detail'], 'npm run build');
      expect(args['subtext'], 'api-server');
      expect(args['chatTitle'], 'Fix login');
    });

    test('a secret in the command never leaves the app', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _ask(
        h.controller,
        permission: const {
          'command':
              'curl -H "Authorization: Bearer sk-live-ABCDEFGHIJ1234" '
              'https://api.example.com --password hunter2hunter2',
        },
      );
      await _flush();
      final text = _shown(h.calls).single.arguments.toString();
      expect(text, isNot(contains('sk-live-ABCDEFGHIJ1234')));
      expect(text, isNot(contains('hunter2hunter2')));
      expect(text, contains('curl'));
    });
  });

  test('a file permission asks to edit the file by name', () async {
    final h = await _harness();
    addTearDown(h.controller.dispose);
    h.controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.asked',
        properties: const {
          'id': 'permission-2',
          'sessionID': 'session-1',
          'action': 'edit',
          'resources': ['lib/main.dart'],
        },
      ),
    );
    await _flush();
    final args = _shown(h.calls).single.arguments!;
    expect(args['askAction'], 'edit');
    expect(args['askTarget'], 'main.dart');
    expect(args['detail'], 'lib/main.dart');
  });

  group('questions', () {
    test(
      'three or fewer short options are listed under the question',
      () async {
        final h = await _harness();
        addTearDown(h.controller.dispose);
        _question(h.controller, ['Ship it', 'Wait']);
        await _flush();
        expect(_shown(h.calls).single.arguments!['options'], [
          'Ship it',
          'Wait',
        ]);
      },
    );

    test('four options are not listed: the alert just opens the app', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _question(h.controller, ['a', 'b', 'c', 'd']);
      await _flush();
      expect(_shown(h.calls).single.arguments!.containsKey('options'), isFalse);
    });

    test('an option button answers the question with its own label', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _question(h.controller, ['Ship it', 'Wait']);
      await _flush();
      final result = await h.live.handleNativeAction({
        'kind': 'question',
        'sessionID': 'session-1',
        'decision': 'reply',
        'requestID': 'question-1',
        'reply': 'Ship it',
      });
      expect(result, {'handled': true});
      expect(h.api.questionReplies, [
        [
          ['Ship it'],
        ],
      ]);
    });

    test('free text is still refused for a choice-only question', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _question(h.controller, ['Ship it', 'Wait']);
      await _flush();
      await h.live.handleNativeAction({
        'kind': 'question',
        'sessionID': 'session-1',
        'decision': 'reply',
        'requestID': 'question-1',
        'reply': 'something else entirely',
      });
      expect(h.api.questionReplies, isEmpty);
    });
  });

  group('quiet hours', () {
    test('a request still posts, silently', () async {
      final h = await _harness(quiet: true);
      addTearDown(h.controller.dispose);
      _ask(h.controller);
      await _flush();
      final shown = _shown(h.calls);
      expect(shown, hasLength(1));
      expect(shown.single.arguments!['silent'], isTrue);
    });

    test('outside quiet hours a request is not silenced', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _ask(h.controller);
      await _flush();
      expect(_shown(h.calls).single.arguments!.containsKey('silent'), isFalse);
    });

    test('a failure still posts, silently, with a plain reason', () async {
      final h = await _harness(quiet: true);
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      h.controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.error',
          properties: const {
            'sessionID': 'session-1',
            'error': {
              'name': 'ProviderAuthError',
              'message': 'Bad key sk-secret-12345678',
            },
          },
        ),
      );
      await _flush();
      final args = _shown(h.calls).single.arguments!;
      expect(args['kind'], 'error');
      expect(args['silent'], isTrue);
      expect(args['reason'], 'auth');
      expect(args['chatTitle'], 'Fix login');
      expect(args.toString(), isNot(contains('sk-secret')));
    });

    test('a finished reply waits for the morning instead', () async {
      final h = await _harness(quiet: true);
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      _idle(h.controller);
      await _flush();
      expect(_shown(h.calls), isEmpty);
    });
  });

  group('finished and stopped', () {
    test('a finished reply names its chat', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      _idle(h.controller);
      await _flush();
      final args = _shown(h.calls).single.arguments!;
      expect(args['kind'], 'complete');
      expect(args['chatTitle'], 'Fix login');
    });

    test('opening the chat clears its finished alert', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      _idle(h.controller);
      await _flush();
      h.calls.clear();
      unawaited_(h.controller.ensureSession('session-1'));
      await _flush();
      expect(
        h.calls.where((c) => c.method == 'dismissCodingAlert').single.arguments,
        {'key': 'status:session-1'},
      );
    });
  });

  group('Stop the reply', () {
    test('stops exactly the chat the status named, while it runs', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      final result = await h.live.handleNativeStatusAction({
        'action': 'stop_reply',
        'sessionID': 'session-1',
      });
      expect(result, {'handled': true});
      expect(h.api.aborted, ['session-1']);
    });

    test('a chat that is no longer running is left alone', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      final result = await h.live.handleNativeStatusAction({
        'action': 'stop_reply',
        'sessionID': 'session-1',
      });
      expect(result, {'handled': false});
      expect(h.api.aborted, isEmpty);
    });

    test('any other action is not handled here', () async {
      final h = await _harness();
      addTearDown(h.controller.dispose);
      _busy(h.controller);
      expect(
        await h.live.handleNativeStatusAction({
          'action': 'format_disk',
          'sessionID': 'session-1',
        }),
        {'handled': false},
      );
      expect(h.api.aborted, isEmpty);
    });
  });
}

void unawaited_(Future<void> future) {}
