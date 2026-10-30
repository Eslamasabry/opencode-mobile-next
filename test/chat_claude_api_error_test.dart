// Owner report 2026-10-29: after a short network drop a Claude Code turn
// ended on Claude Code's own line, "API Error: Can't reach the API server —
// check your internet or DNS (EAI_AGAIN)", which the chat drew as the reply's
// prose with a copy footer; under it the background line ("grep · 7 min") and
// the phrase chips. "I don't know the status, whether it continues or just
// looping or idle." Now the turn ends on the error notice in plain words,
// with Continue, the line under it says the work runs on after the turn
// stopped, and the raw line is only under Error details.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/background_tasks.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/root/projects/IPTV_King';
const _apiError =
    "API Error: Can't reach the API server — check your internet or DNS "
    '(EAI_AGAIN)';

Future<void> _frames(WidgetTester tester, [int count = 20]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

String _ago(Duration ago) =>
    DateTime.now().toUtc().subtract(ago).toIso8601String();

Map<String, dynamic> _entry(int seq, Map<String, dynamic> item, Duration ago) =>
    {
      'provider': 'claude',
      'item': item,
      'timestamp': _ago(ago),
      'seqStart': seq,
      'turnId': 'turn-1',
    };

Future<(FakeDaemon, ConnectionController)> _openClaude(
  WidgetTester tester,
) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final daemon = FakeDaemon();
  final agent = agentJson('a1', cwd: _dir);
  daemon.handlers['fetch_agents_request'] = (_) => (
    'fetch_agents_response',
    {
      'entries': [
        {'agent': agent},
      ],
      'pageInfo': {'hasMore': false},
    },
  );
  daemon.handlers['fetch_agent_request'] = (_) =>
      ('fetch_agent_response', {'agent': agent});
  daemon.handlers['fetch_agent_timeline_request'] = (_) => (
    'fetch_agent_timeline_response',
    {
      'agentId': 'a1',
      'agent': agent,
      'epoch': 'e1',
      'endCursor': {'epoch': 'e1', 'seq': 3},
      'hasOlder': false,
      'hasNewer': false,
      'entries': [
        _entry(1, {
          'type': 'user_message',
          'text': 'Install the owner build once it is done',
          'messageId': 'u1',
        }, const Duration(minutes: 8)),
        _entry(2, {
          'type': 'tool_call',
          'callId': 'toolu_01Wait',
          'name': 'Bash',
          'status': 'completed',
          'error': null,
          'detail': {
            'type': 'shell',
            'command':
                'until grep -q "BUILD SUCCESSFUL" /tmp/build.log; do sleep 30; '
                'done',
            'output':
                'Command running in background with ID: b7. Output is being '
                'written to: /tmp/claude-1000/tasks/b7.output',
          },
        }, const Duration(minutes: 7)),
        _entry(3, {
          'type': 'assistant_message',
          'text': _apiError,
          'messageId': 'a-err',
        }, const Duration(minutes: 6)),
      ],
    },
  );
  daemon.handlers['agent.provider_subagents.list.request'] = (_) =>
      ('agent.provider_subagents.list.response', {'subagents': <Object>[]});
  daemon.handlers['get_providers_snapshot_request'] = (_) => (
    'get_providers_snapshot_response',
    {
      'entries': [
        {
          'provider': 'claude',
          'status': 'ready',
          'label': 'Claude Code',
          'models': [
            {
              'provider': 'claude',
              'id': 'claude-haiku-4-5',
              'label': 'Haiku 4.5',
            },
          ],
          'modes': [
            {'id': 'default', 'label': 'Always Ask'},
          ],
        },
      ],
    },
  );
  daemon.handlers['send_agent_message_request'] = (_) => (
    'send_agent_message_response',
    {'agentId': 'a1', 'accepted': true, 'error': null},
  );
  final gateway = PaseoGateway(
    directory: _dir,
    transport: PaseoTransport(
      endpoint: 'ws://127.0.0.1:6767',
      socketFactory: (_, _) async => daemon,
    ),
  );
  final controller = ConnectionController(
    store,
    isAgentBackend: true,
    paseoGatewayFactory: (_) => (gateway: gateway, operations: gateway),
  );
  await controller.connect(
    ServerProfile(
      id: 'paseo-test',
      name: 'This phone',
      baseUrl: 'ws://127.0.0.1:6767',
      backend: ServerBackend.paseo,
      codexDirectory: _dir,
      transientTransport: true,
    ),
  );
  tester.view.physicalSize = const Size(412, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(
        debugShowCheckedModeBanner: false,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(sessionID: 'a1'),
      ),
    ),
  );
  await _frames(tester, 25);
  return (daemon, controller);
}

void main() {
  setUp(BackgroundTaskBook.shared.clear);
  tearDown(BackgroundTaskBook.shared.clear);

  testWidgets('a turn that ended on an API error is the error notice with '
      'Continue, never prose', (tester) async {
    final (daemon, c) = await _openClaude(tester);

    // The plain words, with the agent and the company it lost.
    final row = find.byKey(const Key('error-card-agent-connection'));
    expect(row, findsOneWidget);
    expect(
      find.descendant(
        of: row,
        matching: find.text(
          'Claude Code lost its connection to Anthropic, so the turn '
          'stopped.',
        ),
      ),
      findsOneWidget,
    );
    // Claude Code's own line is not copy.
    expect(find.textContaining('API Error'), findsNothing);
    expect(find.textContaining('EAI_AGAIN'), findsNothing);
    expect(find.byKey(const Key('assistant-text-block')), findsNothing);

    // The work it started runs on; the line never reads as the agent working.
    expect(
      find.bySemanticsLabel(
        RegExp('Still running after the turn stopped · Waiting for grep'),
      ),
      findsOneWidget,
    );

    // The conversations list says it failed, not that it is working.
    final listed = c.chatFeed().items.where((item) => item.sessionID == 'a1');
    expect(listed.single.status, ChatStatus.failed);

    // Details holds the raw line.
    await tester.tap(find.byKey(const Key('error-action-details')));
    await tester.pumpAndSettle();
    final sheet = find.byKey(const ValueKey('chat-error-details'));
    expect(
      find.descendant(of: sheet, matching: find.textContaining('EAI_AGAIN')),
      findsWidgets,
    );
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();

    // Continue asks the agent to pick up where the turn stopped.
    await tester.tap(find.byKey(const Key('error-action-continue')));
    await _frames(tester);
    expect(daemon.of('send_agent_message_request').single['text'], 'Continue');

    await tester.pumpWidget(const SizedBox());
    c.dispose();
    await tester.pump(const Duration(seconds: 3));
  });
}
