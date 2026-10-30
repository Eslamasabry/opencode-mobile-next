import 'package:opencode_mobile/domain/server_gateway.dart';
// While a request card asks for the person, the conversation does not say it
// a second time above the card: no tool row spinning on the same command and
// no "Waiting for you…" line. When the request is answered the step is back.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _command = 'flutter test test/checkout_test.dart';
final _en = lookupAppLocalizations(const Locale('en'));

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    return [
      MessageWithParts(
        info: MessageInfo(
          id: 'msg-u',
          sessionID: 'session-1',
          role: 'user',
          time: MsgTime(created: now - 5000, completed: now - 5000),
        ),
        parts: [
          Part(id: 'pu', messageID: 'msg-u', type: 'text', text: 'Fix it'),
        ],
      ),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg-a',
          sessionID: 'session-1',
          role: 'assistant',
          parentID: 'msg-u',
          time: MsgTime(created: now - 4000),
        ),
        parts: [
          Part(
            id: 'pa',
            messageID: 'msg-a',
            type: 'text',
            text: 'I will run the test to see it fail.',
          ),
          Part(
            id: 'pt',
            messageID: 'msg-a',
            type: 'tool',
            callID: 'call-1',
            toolName: 'bash',
            toolState: ToolState(
              status: 'running',
              input: const {'command': _command},
            ),
          ),
        ],
      ),
    ];
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _Api()
    ..status = StreamStatus.connected
    ..busySessions = {'session-1'};
}

EventEnvelope _asked({required bool linked}) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': 'request-1',
    'sessionID': 'session-1',
    'permission': 'bash',
    'patterns': [_command],
    'metadata': {'command': _command},
    'always': const <String>[],
    if (linked) 'tool': {'messageID': 'msg-a', 'callID': 'call-1'},
  },
);

/// A running turn keeps animating: a few frames, never pumpAndSettle.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 250));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final waitingLine = _en.kitTurnLiveNow(_en.kitTurnLiveWaitingForYou);

  for (final linked in [true, false]) {
    testWidgets(
      'while the card asks, its tool row and the waiting line are not '
      'repeated above it (server links the step: $linked)',
      (tester) async {
        final controller = await _controller();
        await tester.pumpWidget(
          ProviderScope(
            overrides: [connProvider.overrideWithValue(controller)],
            child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
          ),
        );
        await _settle(tester);
        // Before the ask the step runs, and the turn's one live line says
        // so: no tool row beside it.
        expect(find.byType(ToolCard), findsNothing);
        expect(
          find.byKey(const ValueKey('kit-turn-live-line')),
          findsOneWidget,
        );

        controller.handleEventForTesting(_asked(linked: linked));
        await _settle(tester);

        expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
        // The command is said once: in the card.
        expect(find.byType(ToolCard), findsNothing);
        expect(find.textContaining(_command), findsOneWidget);
        expect(find.text(waitingLine), findsNothing);
        expect(
          find.text('I will run the test to see it fail.'),
          findsOneWidget,
        );

        // Answered elsewhere: the card goes, the live line names the step.
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'permission.replied',
            properties: const {'requestID': 'request-1', 'reply': 'once'},
          ),
        );
        await _settle(tester);
        expect(find.byKey(const Key('permission-card-allow')), findsNothing);
        expect(find.byType(ToolCard), findsNothing);
        expect(
          find.byKey(const ValueKey('kit-turn-live-line')),
          findsOneWidget,
        );
      },
    );
  }

  testWidgets('the request card has the label, the ask, two answers and the '
      'quiet line', (tester) async {
    final controller = await _controller();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await _settle(tester);
    controller.handleEventForTesting(_asked(linked: true));
    await _settle(tester);
    // How long it has waited on this phone (vision 2026-10-20).
    expect(find.textContaining(RegExp(r'^Needs you · \d+ s$')), findsOneWidget);
    expect(find.text('Run this command?'), findsOneWidget);
    expect(find.text('Reject'), findsOneWidget);
    expect(find.text('Allow once'), findsOneWidget);
    expect(find.text('Details'), findsOneWidget);
    // The old tail and the reassurance are gone from the card.
    expect(find.textContaining('waiting less than'), findsNothing);
    expect(find.text(_en.chatRequestIfIgnored), findsNothing);
    expect(find.byType(KitRequestCard), findsOneWidget);
  });
}
