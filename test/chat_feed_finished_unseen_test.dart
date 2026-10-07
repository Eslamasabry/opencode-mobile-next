// The Conversations list's "Done" signal: an idle OpenCode 2 session whose
// last run finished after this device last opened it.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
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
        ..api = OpenCodeApi(baseUrl: 'http://localhost')
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
}
