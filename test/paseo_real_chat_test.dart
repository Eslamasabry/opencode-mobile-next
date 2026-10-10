// A Claude Code / Pi conversation over the real Paseo gateway (not a
// harness): the composer names the conversation's own agent, the model chip
// shows the model in use, and the model sheet offers nothing that does not
// apply to the computer's agents.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/work/app';

Future<(PaseoGateway, ConnectionController)> _open(
  WidgetTester tester, {
  required String provider,
  required String model,
}) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final gateway = PaseoGateway(
    directory: _dir,
    transport: PaseoTransport(
      endpoint: 'ws://127.0.0.1:6767',
      socketFactory: (_, _) async {
        final daemon = FakeDaemon();
        final agent = {
          ...agentJson('a1'),
          'provider': provider,
          'model': model,
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
            'hasOlder': false,
            'hasNewer': false,
            'entries': <Object>[],
          },
        );
        daemon.handlers['agent.provider_subagents.list.request'] = (_) => (
          'agent.provider_subagents.list.response',
          {'subagents': <Object>[]},
        );
        daemon.handlers['get_providers_snapshot_request'] = (_) => (
          'get_providers_snapshot_response',
          {
            'entries': [
              {
                'provider': 'claude',
                'status': 'ready',
                'label': 'Claude',
                'models': [
                  {
                    'id': 'opus',
                    'label': 'Opus',
                    'aliases': ['claude-opus-4-8'],
                  },
                ],
              },
              {
                'provider': 'pi',
                'status': 'ready',
                'label': 'Pi',
                'models': [
                  {'id': 'pi-1', 'label': 'Pi One'},
                ],
              },
            ],
          },
        );
        return daemon;
      },
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
  tester.view.physicalSize = const Size(412, 915);
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
  for (var i = 0; i < 25; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  return (gateway, controller);
}

String _hint(WidgetTester tester) => tester
    .widgetList<Text>(find.byType(Text))
    .map((t) => t.data ?? '')
    .firstWhere((t) => t.startsWith('Ask '), orElse: () => '');

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

  testWidgets('a Claude Code conversation: its agent, its model, no connect', (
    tester,
  ) async {
    final (_, c) = await _open(
      tester,
      provider: 'claude',
      model: 'claude-opus-4-8',
    );
    expect(
      find.textContaining('Claude Code', findRichText: true),
      findsWidgets,
    );
    expect(_hint(tester), contains('Claude Code'));
    expect(_hint(tester), isNot(contains('OpenCode')));
    expect(find.text('Opus'), findsWidgets);
    expect(find.text('Server default'), findsNothing);
    await tester.tap(find.text('Opus').first);
    await tester.pumpAndSettle();
    expect(find.text('Choose a model'), findsOneWidget);
    expect(find.text('Connect a provider'), findsNothing);
    expect(find.text('Choose a model first.'), findsNothing);
    await close(tester, c);
  });

  testWidgets('a Pi conversation says Pi, not the server or Claude Code', (
    tester,
  ) async {
    final (_, c) = await _open(tester, provider: 'pi', model: 'pi-1');
    expect(_hint(tester), contains('Pi'));
    expect(_hint(tester), isNot(contains('Claude Code')));
    await close(tester, c);
  });

  testWidgets(
    'before the first send the server names the agent, as the app does',
    (tester) async {
      final (gateway, c) = await _open(
        tester,
        provider: 'claude',
        model: 'claude-opus-4-8',
      );
      // The daemon's own label is "Claude"; the app says Claude Code.
      expect(ConnectionChatsHost(c).startAgentName, 'Claude Code');
      expect(gateway.agentFeaturesOwner('a1'), 'Claude Code');
      await close(tester, c);
    },
  );
}
