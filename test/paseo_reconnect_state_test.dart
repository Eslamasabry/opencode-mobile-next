import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_turn.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson, stream;

const _id = 'claude-live';
const _dir = '/work/app';

class _Daemon extends FakeDaemon {
  @override
  void push(String type, Map<String, dynamic> payload) {
    super.push(
      type,
      type == 'status' && payload['status'] == 'server_info'
          ? {...payload, 'version': '0.9.2'}
          : payload,
    );
  }
}

class _World {
  String remoteStatus = 'idle';
  final sockets = <FakeDaemon>[];
  late final PaseoGateway gateway;
  late final ConnectionController controller;

  Future<void> init() async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.load();
    gateway = PaseoGateway(
      directory: _dir,
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async {
          final socket = _Daemon();
          socket.handlers['fetch_agents_request'] = (_) => (
            'fetch_agents_response',
            {
              'entries': [
                {'agent': agentJson(_id, status: remoteStatus)},
              ],
              'pageInfo': {'hasMore': false},
            },
          );
          socket.handlers['fetch_agent_request'] = (_) => (
            'fetch_agent_response',
            {'agent': agentJson(_id, status: remoteStatus)},
          );
          socket.handlers['fetch_agent_timeline_request'] = (_) => (
            'fetch_agent_timeline_response',
            {
              'agentId': _id,
              'epoch': 'epoch-1',
              'hasOlder': false,
              'hasNewer': false,
              'entries': [
                {
                  'id': 'answer',
                  'timestamp': '2026-10-01T00:00:00Z',
                  'item': {
                    'type': 'assistant_message',
                    'messageId': 'answer',
                    'text': 'Working on the task',
                  },
                },
              ],
            },
          );
          socket.handlers['agent.provider_subagents.list.request'] = (_) => (
            'agent.provider_subagents.list.response',
            {'subagents': <Object>[]},
          );
          socket.handlers['get_providers_snapshot_request'] = (_) =>
              ('get_providers_snapshot_response', {'entries': <Object>[]});
          sockets.add(socket);
          return socket;
        },
      ),
    );
    controller = ConnectionController(
      store,
      isAgentBackend: true,
      paseoGatewayFactory: (_) => (gateway: gateway, operations: gateway),
    );
    await controller.connect(
      ServerProfile(
        id: 'paseo-test',
        name: 'Claude Code',
        baseUrl: 'ws://127.0.0.1:6767',
        backend: ServerBackend.paseo,
        codexDirectory: _dir,
        transientTransport: true,
      ),
    );
  }

  void close() => controller.dispose();
}

Future<void> _drain(WidgetTester tester) async {
  for (var i = 0; i < 15; i++) {
    await tester.pump();
  }
}

Future<void> _withWorld(
  WidgetTester tester,
  Future<void> Function(_World world) body, {
  String status = 'idle',
}) async {
  final world = _World()..remoteStatus = status;
  try {
    await world.init();
    await _drain(tester);
    await body(world);
  } finally {
    await tester.pumpWidget(const SizedBox());
    world.close();
    await tester.pump(const Duration(seconds: 3));
  }
}

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

  testWidgets('fresh agent snapshot restores busy after cached idle', (
    tester,
  ) async {
    await _withWorld(tester, (world) async {
      expect(world.controller.busySessions, isEmpty);
      world.remoteStatus = 'running';
      await world.controller.refreshSessions();
      await _drain(tester);
      expect(world.controller.busySessions, contains(_id));
    });
  });

  testWidgets(
    'hydrating a running agent publishes status without a turn event',
    (tester) async {
      await _withWorld(tester, (world) async {
        world.remoteStatus = 'running';
        await world.gateway.session(_id);
        await _drain(tester);
        expect(world.controller.busySessions, contains(_id));
      });
    },
  );

  for (final resumedStatus in ['running', 'idle']) {
    testWidgets(
      'drop mid-reply then reconnect $resumedStatus restores chat and composer',
      (tester) async {
        await _withWorld(tester, (world) async {
          await tester.pumpWidget(
            ProviderScope(
              overrides: [connProvider.overrideWithValue(world.controller)],
              child: const MaterialApp(
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: ChatScreen(sessionID: _id),
              ),
            ),
          );
          await _drain(tester);
          expect(world.controller.busySessions, contains(_id));
          expect(find.text('Working on the task'), findsOneWidget);
          expect(
            tester.widget<KitTurn>(find.byType(KitTurn).last).phase,
            KitTurnPhase.running,
          );
          world.sockets.first.push(
            'agent_stream',
            stream(_id, {'type': 'turn_started'}, seq: 1),
          );
          await _drain(tester);
          await world.sockets.first.close();
          await _drain(tester);
          await tester.pump(const Duration(milliseconds: 200));
          expect(world.controller.status, StreamStatus.reconnecting);
          expect(
            tester.widget<KitTurn>(find.byType(KitTurn).last).phase,
            KitTurnPhase.interrupted,
          );
          expect(
            find.text(
              'Connection lost. Reconnecting to get the rest of this reply.',
            ),
            findsOneWidget,
          );
          // Reproduce the reported lost local busy hint. Only the new
          // transport's authoritative snapshot may restore it.
          world.controller.busySessions.clear();
          world.remoteStatus = resumedStatus;
          await tester.pump(const Duration(seconds: 1));
          await _drain(tester);
          await tester.pump(const Duration(milliseconds: 200));
          expect(world.sockets.where((socket) => !socket.closed), hasLength(1));
          expect(world.controller.status, StreamStatus.connected);
          expect(
            world.controller.busySessions.contains(_id),
            resumedStatus == 'running',
          );
          expect(
            find.text('The connection dropped before this reply finished.'),
            findsNothing,
          );
          final composer = tester.widget<KitComposer>(find.byType(KitComposer));
          expect(composer.busy, resumedStatus == 'running');
          final turn = tester.widget<KitTurn>(find.byType(KitTurn).last);
          expect(
            turn.phase,
            resumedStatus == 'running'
                ? KitTurnPhase.running
                : KitTurnPhase.finished,
          );
          if (resumedStatus == 'running') {
            world.remoteStatus = 'idle';
            world.sockets
                .singleWhere((socket) => !socket.closed)
                .push(
                  'agent_stream',
                  stream(_id, {'type': 'turn_completed'}, seq: 2),
                );
            await _drain(tester);
          }
        }, status: 'running');
      },
    );
  }

  testWidgets(
    'idle snapshot completes a streamed reply without a terminal event',
    (tester) async {
      await _withWorld(tester, (world) async {
        world.sockets.first.push(
          'agent_stream',
          stream(_id, {
            'type': 'timeline',
            'provider': 'claude',
            'item': {
              'type': 'assistant_message',
              'messageId': 'streamed-answer',
              'text': 'Partial reply',
            },
          }, seq: 1),
        );
        await _drain(tester);
        expect(
          world.controller.observedCompletedMessageIDs,
          isNot(contains('streamed-answer')),
        );
        world.remoteStatus = 'idle';
        await world.gateway.session(_id);
        await _drain(tester);
        expect(world.controller.busySessions, isNot(contains(_id)));
        expect(
          world.controller.observedCompletedMessageIDs,
          contains('streamed-answer'),
        );
      }, status: 'running');
    },
  );

  testWidgets(
    'subagent running and completed events reach controller busy state',
    (tester) async {
      await _withWorld(tester, (world) async {
        for (final status in ['running', 'completed']) {
          world.sockets.first.push('agent.provider_subagents.update', {
            'kind': 'upsert',
            'subagent': {'id': 'child', 'parentAgentId': _id, 'status': status},
          });
          await _drain(tester);
          expect(
            world.controller.busySessions.contains('sub_child'),
            status == 'running',
          );
        }
      });
    },
  );
}
