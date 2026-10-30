// The Conversations list's "Done" signal: an idle OpenCode 2 session whose
// last run finished after this device last opened it.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/run_result.dart';
import 'package:opencode_mobile/domain/work_row_status.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository extends ProductRepository implements SessionReadStateGateway {
  @override
  Future<void> viewSession(String id, int idle) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Session _session({int idle = 100}) => Session(
  id: 'ses_1',
  title: 'A completed run',
  directory: '/work',
  time: SessionTime(created: 1, updated: idle, idle: idle),
);

Future<ConnectionController> _controller({int idle = 100}) async {
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..repository = _Repository()
        ..api = FakeServerGateway(baseUrl: 'http://localhost')
        ..directory = '/work';
  controller.sessionsById['ses_1'] = _session(idle: idle);
  addTearDown(controller.dispose);
  return controller;
}

ChatFeedItem _row(ConnectionController c) => chatFeedSourceOf(
  c,
).chatFeed().items.singleWhere((item) => item.sessionID == 'ses_1');

Future<void> _open(ConnectionController c, int idle) => c.viewSession(
  'ses_1',
  isForeground: () => true,
  observedIdle: idle,
  expectedLocationRevision: c.locationRevision,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('finished after it was last opened is finished-unseen', () async {
    final c = await _controller(idle: 100);
    expect(_row(c).finishedUnseen, isTrue, reason: 'never opened');
    await _open(c, 100);
    expect(_row(c).finishedUnseen, isFalse);
    c.sessionsById['ses_1'] = _session(idle: 200);
    expect(_row(c).finishedUnseen, isTrue, reason: 'a newer run finished');
  });

  test('opened after it finished is not finished-unseen', () async {
    final c = await _controller(idle: 100);
    await _open(c, 100);
    expect(_row(c).finishedUnseen, isFalse);
  });

  test('a running session is never finished-unseen', () async {
    final c = await _controller(idle: 100);
    c.busySessions.add('ses_1');
    final row = _row(c);
    expect(row.status, ChatStatus.running);
    expect(row.finishedUnseen, isFalse);
  });

  test(
    'a stop pressed here is remembered per session until the next prompt',
    () async {
      final c = await _controller(idle: 100);
      expect(_row(c).stoppedByPerson, isFalse);
      c.markStoppedByPerson('ses_1');
      expect(_row(c).stoppedByPerson, isTrue);
      expect(_row(c).finishedUnseen, isTrue);
      c.clearStoppedByPerson('ses_1');
      expect(_row(c).stoppedByPerson, isFalse);
    },
  );

  test('opening the conversation drops the stop memory', () async {
    final c = await _controller(idle: 100);
    c.markStoppedByPerson('ses_1');
    await c.ensureSession('ses_1').catchError((_) {});
    expect(c.stoppedByPerson('ses_1'), isFalse);
  });

  test(
    'a normal finish the person stopped is Stopped in the work-row facts',
    () {
      final result = RunResult.fromMessages('ses_1', [
        MessageWithParts(
          info: MessageInfo(id: 'u', sessionID: 'ses_1', role: 'user'),
          parts: [Part(type: 'text', text: 'go')],
        ),
        MessageWithParts(
          info: MessageInfo(
            id: 'a',
            sessionID: 'ses_1',
            role: 'assistant',
            finish: 'stop',
            time: MsgTime(created: 1, completed: 2),
          ),
          parts: [Part(type: 'text', text: 'done')],
        ),
      ]);
      WorkRowPhase phase({required bool stopped}) => WorkRowFacts.chat(
        busy: false,
        needsYou: false,
        result: result,
        stoppedByPerson: stopped,
      ).phase;
      expect(phase(stopped: false), WorkRowPhase.done);
      expect(phase(stopped: true), WorkRowPhase.stopped);
    },
  );
}
