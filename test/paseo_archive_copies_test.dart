import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/conversation_title_memory.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _dir = '/root/projects/IPTV_King';

Map<String, dynamic> _agent(
  String id, {
  String? title,
  String status = 'idle',
  String updated = '2026-10-03T08:00:00Z',
  String session = 'native-1',
}) => {
  'id': id,
  'provider': 'claude',
  'cwd': _dir,
  'model': null,
  'title': title,
  'status': status,
  'createdAt': '2026-10-02T08:00:00Z',
  'updatedAt': updated,
  'pendingPermissions': <Object>[],
  'labels': <String, String>{},
  'archivedAt': null,
  'persistence': {'provider': 'claude', 'sessionId': session},
};

Map<String, dynamic> _page(List<Map<String, dynamic>> agents) => {
  'entries': [
    for (final agent in agents) {'agent': agent},
  ],
  'pageInfo': {'hasMore': false, 'nextCursor': null, 'prevCursor': null},
};

void main() {
  late FakePaseoSocket socket;
  late SharedPreferences prefs;
  late List<Map<String, dynamic>> agents;
  final open = <PaseoGateway>[];
  final feeds = <PaseoChatFeedSource>[];

  PaseoChatFeedSource feed() {
    final gateway = PaseoGateway(
      directory: _dir,
      transport: PaseoTransport(
        endpoint: 'ws://100.64.0.20:6767',
        socketFactory: (_, _) async => socket,
      ),
    );
    open.add(gateway);
    final source = PaseoChatFeedSource(
      gateway,
      savedTitle: (key) => ConversationTitleMemory.read(prefs, 'p1', key),
      saveTitle: (key, title) =>
          ConversationTitleMemory.remember(prefs, 'p1', key, title),
    );
    feeds.add(source);
    return source;
  }

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    socket = FakePaseoSocket();
    socket.handlers['fetch_agents_request'] = (_) =>
        ('fetch_agents_response', _page(agents));
    socket.handlers['fetch_agent_request'] = (m) => (
      'fetch_agent_response',
      {
        'agent': agents.firstWhere((a) => a['id'] == m['agentId']),
        'error': null,
      },
    );
    socket.handlers['archive_agent_request'] = (m) => (
      'agent_archived',
      {'agentId': m['agentId'], 'archivedAt': '2026-10-28T05:00:00.000Z'},
    );
  });
  tearDown(() async {
    for (final f in feeds) {
      await f.dispose();
    }
    for (final g in open) {
      g.close();
    }
    feeds.clear();
    open.clear();
    await socket.close();
  });

  // Archiving runs after the list is ready, not waited on by the refresh.
  Future<void> settle() =>
      Future<void>.delayed(const Duration(milliseconds: 100));

  List<Object?> archived() =>
      socket.of('archive_agent_request').map((m) => m['agentId']).toList();

  test('an idle older copy is archived, the newer one is not', () async {
    agents = [
      _agent('old', title: 'Fix the login bug'),
      _agent('new', updated: '2026-10-04T08:00:00Z'),
    ];
    final source = feed();
    await source.refreshChatFeed();
    await settle();
    expect(archived(), ['old']);
    expect(source.chatFeed().items.single.sessionID, 'new');
    expect(source.chatFeed().items.single.title, 'Fix the login bug');
    await source.refreshChatFeed();
    await settle();
    expect(archived(), ['old'], reason: 'not archived twice');
  });

  test('a busy older copy is left alone', () async {
    agents = [
      _agent('old', title: 'Fix the login bug', status: 'running'),
      _agent('new', updated: '2026-10-04T08:00:00Z'),
    ];
    final source = feed();
    await source.refreshChatFeed();
    await settle();
    expect(archived(), isEmpty);
  });

  test('the title survives the archive and an app restart', () async {
    agents = [
      _agent('old', title: 'Fix the login bug'),
      _agent('new', updated: '2026-10-04T08:00:00Z'),
    ];
    await feed().refreshChatFeed();
    await settle();
    expect(archived(), ['old']);
    // Restart: the daemon no longer lists the archived record.
    agents = [_agent('new', updated: '2026-10-04T08:00:00Z')];
    final again = feed();
    await again.refreshChatFeed();
    expect(again.chatFeed().items.single.title, 'Fix the login bug');
    expect(
      open.last.hasOwnTitle('new'),
      isTrue,
      reason: 'the header shows it too',
    );
  });

  test('a failed archive does not break the list', () async {
    socket.handlers['archive_agent_request'] = (_) => throw StateError('x');
    agents = [
      _agent('old', title: 'Fix the login bug'),
      _agent('new', updated: '2026-10-04T08:00:00Z'),
    ];
    final source = feed();
    await source.refreshChatFeed();
    expect(source.chatFeed().items.single.title, 'Fix the login bug');
  });
}
