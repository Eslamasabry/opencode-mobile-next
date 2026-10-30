import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/conversation_title_memory.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _dir = '/root/projects/settings-demo';

Map<String, dynamic> _agent(
  String id, {
  String? title,
  String updated = '2026-10-25T08:00:00Z',
  String model = 'google/gemini-3-pro-image',
}) => {
  'id': id,
  'provider': 'kilo',
  'cwd': _dir,
  'model': model,
  'title': title,
  'status': 'idle',
  'createdAt': '2026-10-25T08:00:00Z',
  'updatedAt': updated,
  'pendingPermissions': <Object>[],
  'labels': <String, String>{},
  'archivedAt': null,
  'persistence': {'provider': 'kilo', 'sessionId': 'ses_1'},
};

void main() {
  late FakePaseoSocket socket;
  late SharedPreferences prefs;
  late List<Map<String, dynamic>> agents;
  final open = <PaseoGateway>[];

  PaseoGateway gateway() {
    final g = PaseoGateway(
      directory: _dir,
      transport: PaseoTransport(
        endpoint: 'ws://100.64.0.20:6767',
        socketFactory: (_, _) async => socket,
      ),
    );
    open.add(g);
    return g;
  }

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    socket = FakePaseoSocket();
    socket.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          for (final agent in agents) {'agent': agent},
        ],
        'pageInfo': {'hasMore': false, 'nextCursor': null, 'prevCursor': null},
      },
    );
    socket.handlers['fetch_agent_request'] = (m) => (
      'fetch_agent_response',
      {
        'agent': agents.firstWhere((a) => a['id'] == m['agentId']),
        'error': null,
      },
    );
  });
  tearDown(() async {
    for (final g in open) {
      g.close();
    }
    open.clear();
    await socket.close();
  });

  test('the header of a resumed copy says what its row says', () async {
    agents = [
      _agent('old', title: 'Say pong'),
      _agent('new', updated: '2026-10-27T08:00:00Z'),
    ];
    final source = PaseoChatFeedSource(
      gateway(),
      savedTitle: (key) => ConversationTitleMemory.read(prefs, 'p1', key),
      saveTitle: (key, title) =>
          ConversationTitleMemory.remember(prefs, 'p1', key, title),
    );
    await source.refreshChatFeed();
    final row = source.chatFeed().items.single;
    expect(row.title, 'Say pong');
    // The conversation opens on its own gateway, hinted with the row title.
    final chat = gateway()..keepTitle(row.sessionID, row.title);
    expect((await chat.session(row.sessionID)).title, 'Say pong');
    await source.dispose();
  });

  test('a lone record without a title takes the one saved for it', () async {
    // The older copies were archived after their title was saved; only the
    // titleless copy is left, and the conversation opens on its own gateway.
    await ConversationTitleMemory.remember(
      prefs,
      'p1',
      'kilo:ses_1',
      'Say pong',
    );
    agents = [_agent('new', updated: '2026-10-27T08:00:00Z')];
    final chat = gateway()
      ..savedTitle = (key) => ConversationTitleMemory.read(prefs, 'p1', key);
    expect((await chat.session('new')).title, 'Say pong');
    expect(chat.hasOwnTitle('new'), isTrue);
  });

  test('without a saved title the stand-in stays', () async {
    agents = [_agent('new', updated: '2026-10-27T08:00:00Z')];
    final chat = gateway()
      ..savedTitle = (key) => ConversationTitleMemory.read(prefs, 'p1', key);
    expect((await chat.session('new')).title, 'kilo conversation');
  });

  test('a model reported as provider/model is the list model', () async {
    socket.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'kilo',
            'status': 'ready',
            'label': 'Kilo',
            'models': [
              {'id': 'google/gemini-3-pro-image', 'label': 'Nano Banana Pro'},
            ],
          },
        ],
      },
    );
    agents = [
      _agent(
        'new',
        updated: '2026-10-27T08:00:00Z',
        model: 'kilo/google/gemini-3-pro-image',
      ),
    ];
    final chat = gateway();
    await chat.providers();
    final session = await chat.session('new');
    expect(session.selection?.model?.modelID, 'google/gemini-3-pro-image');
  });
}
