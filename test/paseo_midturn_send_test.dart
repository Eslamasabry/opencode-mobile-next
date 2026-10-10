// Sending while a Claude Code turn runs (owner report on build 2208): the
// daemon's default for a mid-turn message is to interrupt the turn. Claude
// then reports the stopped step twice: once as "canceled" with its name and
// input, and once more, inside the new turn, as a nameless failed "tool"
// whose input is gone (its result is "[Request interrupted by user for tool
// use]"). The app showed that second report: a "Tool" row, Failed, "{}".
//
// The event sequences below follow @getpaseo/server 0.9.1:
// agent-prompt.js (steerOrReplaceActiveRun / startOrReplaceRun),
// claude/agent.js (startTurn's requestCancel → flushPendingToolCalls →
// mapClaudeCanceledToolCall, steerActiveTurn, handleToolResult without a
// cached entry) and agent-manager.js (recordAcceptedSteer).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/work/app';
const _command = 'du -sh /work/app/box';

Map<String, dynamic> _bash(String status) => {
  'type': 'tool_call',
  'callId': 'toolu_1',
  'name': 'Bash',
  'status': status,
  'error': null,
  'detail': {'type': 'shell', 'command': _command},
};

/// What claude/agent.js handleToolResult emits for the interrupted call
/// once its cache entry is gone: name "tool", no input, a raw error block.
final _staleInterruptResult = {
  'type': 'tool_call',
  'callId': 'toolu_1',
  'name': 'tool',
  'status': 'failed',
  'detail': {'type': 'unknown', 'input': null, 'output': null},
  'error': {
    'type': 'tool_result',
    'tool_use_id': 'toolu_1',
    'content': '[Request interrupted by user for tool use]',
    'is_error': true,
  },
};

Map<String, dynamic> _entry(Map<String, dynamic> item, int seq) => {
  'provider': 'claude',
  'item': item,
  'timestamp': '2026-10-10T10:00:0$seq.000Z',
  'seqStart': seq,
  'turnId': 'turn-1',
};

Map<String, dynamic> _stream(Map<String, dynamic> event, {int? seq}) => {
  'agentId': 'a1',
  'event': event,
  'timestamp': '2026-10-10T10:00:30.000Z',
  'seq': ?seq,
  if (seq != null) 'epoch': 'e1',
};

Map<String, dynamic> _timeline(Map<String, dynamic> item, String turn) => {
  'type': 'timeline',
  'provider': 'claude',
  'item': item,
  'turnId': turn,
};

Future<(FakeDaemon, ConnectionController)> _open(
  WidgetTester tester, {
  required bool daemonSteers,
  String provider = 'claude',
}) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final daemon = FakeDaemon();
  final agent = {
    ...agentJson('a1', status: 'running'),
    'provider': provider,
    'model': 'opus',
  };
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
      'epoch': 'e1',
      'endCursor': {'epoch': 'e1', 'seq': 3},
      'hasOlder': false,
      'hasNewer': false,
      'entries': [
        _entry({
          'type': 'user_message',
          'text': 'Tidy the box',
          'messageId': 'u1',
        }, 1),
        _entry({
          'type': 'assistant_message',
          'text': 'Checking the box first.',
          'messageId': 'msg_1',
        }, 2),
        _entry(_bash('running'), 3),
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
          'provider': provider,
          'status': 'ready',
          'label': provider == 'claude' ? 'Claude' : 'GitHub Copilot',
          'models': [
            {'id': 'opus', 'label': 'Opus'},
          ],
        },
      ],
    },
  );
  daemon.handlers['send_agent_message_request'] = (m) {
    final id = m['messageId'] as String;
    final user = {
      'type': 'user_message',
      'text': m['text'],
      'messageId': id,
      'clientMessageId': id,
    };
    if (daemonSteers && m['activeTurnBehavior'] == 'steer') {
      // Claude takes the message into the running turn (priority "next"):
      // the daemon records it in that turn and the step keeps running.
      daemon.push('agent_stream', _stream(_timeline(user, 'turn-1'), seq: 4));
    } else {
      // "interrupt" (the daemon default), or a provider that cannot steer.
      daemon
        ..push(
          'agent_stream',
          _stream(_timeline(_bash('canceled'), 'turn-1'), seq: 4),
        )
        ..push(
          'agent_stream',
          _stream({
            'type': 'turn_canceled',
            'provider': 'claude',
            'reason': 'Interrupted',
            'turnId': 'turn-1',
          }),
        )
        ..push(
          'agent_stream',
          _stream({
            'type': 'turn_started',
            'provider': 'claude',
            'turnId': 'turn-2',
          }),
        )
        ..push('agent_stream', _stream(_timeline(user, 'turn-2'), seq: 5))
        ..push(
          'agent_stream',
          _stream(_timeline(_staleInterruptResult, 'turn-2'), seq: 6),
        );
    }
    return (
      'send_agent_message_response',
      {'agentId': 'a1', 'accepted': true, 'error': null},
    );
  };
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
      name: 'Work laptop',
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
  await _frames(tester);
  return (daemon, controller);
}

Future<void> _frames(WidgetTester tester, [int count = 25]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _sendMidTurn(WidgetTester tester, {String? says}) async {
  await tester.enterText(
    find.byKey(const Key('chat-composer-field')),
    'And leave the box now as it is being used.',
  );
  await tester.pump();
  if (says != null) expect(find.text(says), findsWidgets);
  await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
  await _frames(tester);
}

/// Opens every folded work line so the step rows are on screen.
Future<void> _openWork(WidgetTester tester) async {
  final headers = find.byKey(const Key('work-group-header'));
  final count = headers.evaluate().length;
  if (find.byKey(const Key('work-group-steps')).evaluate().length >= count) {
    return;
  }
  for (var i = 0; i < count; i++) {
    await tester.tap(headers.at(i));
    await _frames(tester, 5);
  }
}

String _screenText(WidgetTester tester) => [
  for (final widget in tester.widgetList(find.byType(RichText)))
    (widget as RichText).text.toPlainText(),
].join('\n');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in [
      'plugins.it_nomads.com/flutter_secure_storage',
      'oc/background',
    ]) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (_) async => null,
      );
    }
  });

  Future<void> close(WidgetTester tester, ConnectionController c) async {
    await tester.pumpWidget(const SizedBox());
    c.dispose();
    await tester.pump(const Duration(seconds: 3));
  }

  testWidgets('a message sent while Claude works joins the running turn; '
      'the running step keeps going', (tester) async {
    final (daemon, c) = await _open(tester, daemonSteers: true);
    // Never "Send after this reply": Claude reads it in this turn.
    await _sendMidTurn(tester, says: 'Add to this turn');

    final sent = daemon.of('send_agent_message_request').single;
    expect(sent['activeTurnBehavior'], 'steer');
    await _openWork(tester);
    final text = _screenText(tester);
    expect(text, contains('And leave the box now as it is being used.'));
    expect(text, contains(_command));
    expect(text, isNot(contains('Tool failed.')));
    expect(text, isNot(contains('Stopped')));
    expect(text, isNot(contains('Queued')));
    // The step above the new message is still the running turn's work.
    await tester.pump(const Duration(seconds: 30));
    expect(_screenText(tester), isNot(contains('connection dropped')));
    await close(tester, c);
  });

  testWidgets('when the daemon interrupts the turn, the stopped step keeps '
      'its name and command, says Stopped, and no empty reply is left', (
    tester,
  ) async {
    final (_, c) = await _open(tester, daemonSteers: false);
    await _sendMidTurn(tester);
    await _openWork(tester);

    final text = _screenText(tester);
    expect(text, contains(_command));
    expect(text, isNot(contains('Tool failed.')));
    expect(text, isNot(contains('{}')));
    expect(text, contains('Stopped'));
    expect(text, isNot(contains('connection dropped')));
    expect(text, isNot(contains('Queued')));
    await close(tester, c);
  });

  testWidgets('an agent the daemon restarts (Copilot): Send says Stop and '
      'send and why, never Send after this reply', (tester) async {
    final (_, c) = await _open(
      tester,
      daemonSteers: false,
      provider: 'copilot',
    );
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'And leave the box now as it is being used.',
    );
    await tester.pump();
    expect(find.byTooltip('Stop and send'), findsOneWidget);
    expect(
      find.text(
        'Agents like Claude Code add a message to the turn. This one stops '
        'its reply and starts again with yours.',
      ),
      findsOneWidget,
    );
    expect(find.text('Sends after this reply'), findsNothing);
    expect(find.byTooltip('Send after this reply'), findsNothing);
    await close(tester, c);
  });

  test('the late interrupt report keeps the step it belongs to', () {
    final stopped = paseoItemMessage(
      'a1',
      _staleInterruptResult,
      id: 'toolu_1',
      provider: 'claude',
    )!.parts.single;
    expect(stopped.toolState.status, 'cancelled');
    expect(stopped.toolState.output, isNot(contains('[Request')));
  });

  test('read back from the history, the stopped step stays where it ran, '
      'with its name and command', () {
    final messages = paseoTimelineMessages('a1', {
      'entries': [
        _entry({'type': 'user_message', 'text': 'Tidy', 'messageId': 'u1'}, 1),
        _entry(_bash('canceled'), 2),
        {
          ..._entry({
            'type': 'user_message',
            'text': 'Leave it',
            'messageId': 'u2',
          }, 3),
          'turnId': 'turn-2',
        },
        {..._entry(_staleInterruptResult, 4), 'turnId': 'turn-2'},
      ],
    }, busy: true);
    expect(messages.map((m) => m.info.id), ['u1', 'toolu_1', 'u2']);
    final step = messages[1].parts.single;
    expect(step.toolName, isNot('tool'));
    expect(step.toolState.status, 'cancelled');
    expect(step.toolState.input['command'], _command);
  });
}
