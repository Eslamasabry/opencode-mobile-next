// Issue #95: "Always allow" on a phone agent's permission card did nothing.
// The Paseo gateway knows when the daemon can keep a standing rule (only when
// the agent suggested one; never for an ACP agent's request), but the request
// lost that fact on its way to the chat as an event, so every card offered
// "Always allow" and the daemon refused it. The card offers it only where it
// can work, and where it is offered the confirm sends the agent's own rule.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;
import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _dir = '/work/app';

class _PaseoChatApi extends FakeServerGateway with CompleteMessageHistory {
  _PaseoChatApi() : super(baseUrl: 'http://localhost');

  final replies = <({String requestID, String reply})>[];

  @override
  ServerCapabilities get capabilities => paseoServerCapabilities;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async => replies.add((requestID: requestID, reply: reply));

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

Map<String, dynamic> _shell(String id, {bool suggested = false}) => {
  'id': id,
  'provider': 'claude',
  'name': 'Bash',
  'kind': 'tool',
  'detail': {'type': 'shell', 'command': 'pwd'},
  if (suggested)
    'suggestions': [
      {
        'type': 'addRules',
        'rules': [
          {'toolName': 'Bash', 'ruleContent': 'pwd'},
        ],
        'behavior': 'allow',
        'destination': 'localSettings',
      },
    ],
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  /// The `permission.asked` event the Paseo gateway emits for [request].
  Future<EventEnvelope> asked(Map<String, dynamic> request) async {
    final daemon = FakeDaemon();
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agentJson('a1')},
        ],
        'pageInfo': {'nextCursor': null, 'prevCursor': null, 'hasMore': false},
      },
    );
    final gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
    final events = <EventEnvelope>[];
    gateway.openEventChannel(onEvent: events.add, onStatus: (_) {}).start();
    await gateway.sessions();
    daemon.push('agent_permission_request', {
      'agentId': 'a1',
      'request': request,
    });
    await pumpEventQueue();
    gateway.close();
    return events.singleWhere((event) => event.type == 'permission.asked');
  }

  Future<(ConnectionController, _PaseoChatApi)> chat(
    WidgetTester tester,
    EventEnvelope event,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final api = _PaseoChatApi();
    final controller = ConnectionController(ProfileStore(prefs: prefs))
      ..api = api
      ..status = StreamStatus.connected;
    controller.handleEventForTesting(event);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'a1')),
      ),
    );
    await tester.pumpAndSettle();
    return (controller, api);
  }

  const always = Key('permission-card-always');

  testWidgets(
    'a request the agent suggested no rule for offers no Always allow',
    (tester) async {
      final event = await tester.runAsync(() => asked(_shell('perm-1')));
      final (controller, _) = await chat(tester, event!);

      expect(controller.permissions['perm-1']?.canAlwaysAllow, isFalse);
      expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
      expect(find.byKey(always), findsNothing);
    },
  );

  testWidgets('a suggested rule offers Always allow and the confirm sends it', (
    tester,
  ) async {
    final event = await tester.runAsync(
      () => asked(_shell('perm-2', suggested: true)),
    );
    final (controller, api) = await chat(tester, event!);

    expect(controller.permissions['perm-2']?.canAlwaysAllow, isTrue);
    await tester.tap(find.byKey(always));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('permission-card-always-confirm')));
    await tester.pump();
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    expect(api.replies, [(requestID: 'perm-2', reply: 'always')]);
  });

  test('OpenCode requests keep Always allow when the field is absent', () {
    final request = PermissionRequest.fromJson({
      'id': 'p',
      'sessionID': 's',
      'permission': 'bash',
      'patterns': ['ls'],
    });
    expect(request.canAlwaysAllow, isTrue);
  });
}
