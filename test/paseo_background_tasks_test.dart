// A Claude Code conversation whose turn ended with a command still running in
// the background (Bash with run_in_background) counts as Running on the
// conversations list until Paseo's task_notification for that command
// arrives, or until the agent's process is seen down. Shapes are Paseo
// 0.11.2's (fetch_agent_timeline_response, agent_stream, agent_update).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/background_tasks.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _directory = '/root/projects/IPTV_King';
const _callId = 'toolu_01BuildOwner';

String _ago(Duration ago) =>
    DateTime.now().toUtc().subtract(ago).toIso8601String();

Map<String, dynamic> _agent({bool loaded = true, String status = 'idle'}) => {
  'id': 'kilo',
  'provider': 'claude',
  'persistence': {'provider': 'claude', 'sessionId': 'native-kilo'},
  'cwd': _directory,
  'model': 'claude-opus-5-5',
  'title': 'Build the owner APK',
  'status': status,
  'createdAt': _ago(const Duration(hours: 1)),
  'updatedAt': _ago(const Duration(minutes: 1)),
  if (loaded) 'activeTurn': null,
  'pendingPermissions': <Object>[],
  'labels': <String, String>{},
  'archivedAt': null,
};

Map<String, dynamic> _entry(int seq, Map<String, dynamic> item, Duration ago) =>
    {
      'seqStart': seq,
      'seqEnd': seq,
      'provider': 'claude',
      'timestamp': _ago(ago),
      'item': item,
    };

final _bash = {
  'type': 'tool_call',
  'callId': _callId,
  'name': 'Bash',
  'status': 'completed',
  'error': null,
  'detail': {
    'type': 'shell',
    'command': 'bash tool/build_tv_release.sh --owner',
    'output':
        'Command running in background with ID: b1x2y3. Output is being '
        'written to: /tmp/claude-1000/tasks/b1x2y3.output',
  },
};

final _notice = {
  'type': 'tool_call',
  'callId': 'task_notification_b1x2y3',
  'name': 'task_notification',
  'status': 'completed',
  'error': null,
  'detail': {
    'type': 'plain_text',
    'label': 'Background command "Build the owner APK" completed (exit code 0)',
    'icon': 'wrench',
  },
  'metadata': {
    'synthetic': true,
    'source': 'claude_task_notification',
    'taskId': 'b1x2y3',
    'toolUseId': _callId,
    'status': 'completed',
  },
};

void main() {
  late FakePaseoSocket socket;
  late PaseoGateway gateway;
  late PaseoChatFeedSource source;
  late Map<String, dynamic> agent;
  late String lastWords;

  setUp(() {
    BackgroundTaskBook.shared.clear();
    socket = FakePaseoSocket();
    agent = _agent();
    lastWords = 'The owner build is compiling.';
    socket.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agent},
        ],
        'pageInfo': {'hasMore': false, 'nextCursor': null, 'prevCursor': null},
      },
    );
    socket.handlers['fetch_agent_request'] = (_) =>
        ('fetch_agent_response', {'agent': agent});
    socket.handlers['fetch_agent_timeline_request'] = (request) => (
      'fetch_agent_timeline_response',
      {
        'agentId': request['agentId'],
        'agent': agent,
        'epoch': 'epoch-1',
        'entries': [
          _entry(1, {
            'type': 'user_message',
            'text': 'Make the owner build',
            'messageId': 'u1',
          }, const Duration(minutes: 3)),
          _entry(2, _bash, const Duration(minutes: 2)),
          _entry(3, {
            'type': 'assistant_message',
            'text': lastWords,
            'messageId': 'a1',
          }, const Duration(minutes: 2)),
        ],
        'endCursor': {'seq': 3},
        'hasOlder': false,
        'hasNewer': false,
      },
    );
    gateway = PaseoGateway(
      directory: _directory,
      defaultProviderModes: const {'claude': 'default'},
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => socket,
      ),
    );
    source = PaseoChatFeedSource(gateway);
  });

  tearDown(() async {
    await source.dispose();
    gateway.close();
    await socket.close();
    BackgroundTaskBook.shared.clear();
  });

  ChatFeedItem row() => source.chatFeed().items.single;

  test('a finished turn with a command still running reads as Running, '
      'until its notice arrives', () async {
    await source.refreshChatFeed();
    expect(row().status, ChatStatus.idle);

    await gateway.messages('kilo');
    expect(gateway.backgroundTasksRunning('kilo'), isTrue);
    expect(row().status, ChatStatus.running);
    expect(
      source.chatFeed(const ChatFeedFilter(running: true)).items,
      hasLength(1),
    );
    expect(source.projectSummaries.single.runningCount, 1);

    socket.push('agent_stream', {
      'agentId': 'kilo',
      'seq': 4,
      'epoch': 'epoch-1',
      'timestamp': _ago(Duration.zero),
      'event': {'type': 'timeline', 'provider': 'claude', 'item': _notice},
    });
    await pumpEventQueue();
    expect(gateway.backgroundTasksRunning('kilo'), isFalse);
    expect(row().status, ChatStatus.idle);
    expect(source.chatFeed(const ChatFeedFilter(running: true)).items, isEmpty);
  });

  test('the agent seen without its process ends what ran before', () async {
    await source.refreshChatFeed();
    await gateway.messages('kilo');
    expect(gateway.backgroundTasksRunning('kilo'), isTrue);
    expect(gateway.agentRuntimeGapAt('kilo'), isNull);

    // The helper restarted: the agent is a saved record again.
    socket.push('agent_update', {
      'kind': 'upsert',
      'agent': _agent(loaded: false),
    });
    await pumpEventQueue();
    expect(gateway.agentRuntimeGapAt('kilo'), isNotNull);
    expect(gateway.backgroundTasksRunning('kilo'), isFalse);
    expect(row().status, ChatStatus.idle);
  });

  // Owner report 2026-10-29: after a short network drop the turn ended on
  // Claude Code's own "API Error: …" line, the list said the conversation
  // was working (the grep loop still ran), and the owner could not tell
  // whether the agent continued, looped or was idle.
  test('a turn that ended on an API error reads as Failed, not Running, '
      'whatever it left running', () async {
    lastWords =
        "API Error: Can't reach the API server — check your internet or DNS "
        '(EAI_AGAIN)';
    await source.refreshChatFeed();
    await gateway.messages('kilo');
    expect(gateway.backgroundTasksRunning('kilo'), isTrue);
    expect(gateway.lastTurnFailed('kilo'), isTrue);
    expect(row().status, ChatStatus.failed);
    expect(source.chatFeed(const ChatFeedFilter(running: true)).items, isEmpty);

    // A task's notice is not the agent going on.
    socket.push('agent_stream', {
      'agentId': 'kilo',
      'seq': 4,
      'epoch': 'epoch-1',
      'timestamp': _ago(Duration.zero),
      'event': {'type': 'timeline', 'provider': 'claude', 'item': _notice},
    });
    await pumpEventQueue();
    expect(gateway.lastTurnFailed('kilo'), isTrue);

    // The next prompt starts a new turn.
    socket.push('agent_stream', {
      'agentId': 'kilo',
      'seq': 5,
      'epoch': 'epoch-1',
      'timestamp': _ago(Duration.zero),
      'event': {
        'type': 'timeline',
        'provider': 'claude',
        'item': {'type': 'user_message', 'text': 'Continue', 'messageId': 'u2'},
      },
    });
    await pumpEventQueue();
    expect(gateway.lastTurnFailed('kilo'), isFalse);
    expect(row().status, isNot(ChatStatus.failed));
  });

  test('a record read before the history counts nothing as running', () async {
    // The app reads a conversation whose process is gone: the history still
    // holds the background step and no notice.
    agent = _agent(loaded: false);
    await gateway.messages('kilo');
    expect(gateway.backgroundTasksRunning('kilo'), isFalse);
  });
}
