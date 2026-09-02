import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _TranscriptApi extends OpenCodeApi {
  _TranscriptApi(this.transcript) : super(baseUrl: 'http://localhost');

  final List<MessageWithParts> transcript;

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  required int created,
  Tokens? tokens,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: 'anthropic',
    modelID: 'claude',
    tokens: tokens,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

Part _text(String id, String text) => Part(id: id, type: 'text', text: text);

Future<ConnectionController> _pump(
  WidgetTester tester,
  List<MessageWithParts> transcript, {
  bool busy = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = _TranscriptApi(transcript)
    ..status = StreamStatus.connected;
  if (busy) controller.busySessions.add('session-1');
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  // The composer's activity ring animates forever, so a busy chat never
  // settles.
  if (busy) {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  } else {
    await tester.pumpAndSettle();
  }
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a busy session shows working on the composer, not as a '
      'transcript row', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'First question')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'First answer')], created: 2),
      _message('u2', 'user', [_text('u2-t', 'Second question')], created: 3),
      _message('a2', 'assistant', [_text('a2-t', 'Second answer')], created: 4),
    ], busy: true);

    // The transcript is only the messages: the newest turn is the last row
    // and nothing sits under it.
    expect(find.byKey(const ValueKey('typing-indicator')), findsNothing);
    expect(find.byKey(const ValueKey('message-a2')), findsOneWidget);
    final activity = find.byKey(const ValueKey('composer-activity'));
    expect(activity, findsOneWidget);
    final lastBubbleBottom = tester
        .getBottomLeft(find.byKey(const ValueKey('message-a2')))
        .dy;
    expect(
      tester.getTopLeft(activity).dy,
      greaterThanOrEqualTo(lastBubbleBottom),
    );
    expect(find.bySemanticsLabel('Assistant is working'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('an idle session shows no working indicator', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Question')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'Answer')], created: 2),
    ]);
    expect(find.byKey(const ValueKey('typing-indicator')), findsNothing);
    expect(find.byKey(const ValueKey('composer-activity')), findsNothing);
    expect(find.bySemanticsLabel('Assistant is working'), findsNothing);
    semantics.dispose();
  });

  testWidgets('step-finish, patch and snapshot parts leave no stray row', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Rename the file')], created: 1),
      _message('a1', 'assistant', [
        Part(id: 'a1-step', type: 'step-start'),
        Part(
          id: 'a1-edit',
          type: 'tool',
          callID: 'call-1',
          toolName: 'edit',
          toolState: ToolState.fromJson(const {
            'status': 'completed',
            'input': {'filePath': '/work/lib/main.dart'},
            'output': 'ok',
            'metadata': {'diff': ''},
          }, toolName: 'edit'),
        ),
      ], created: 2),
      // The bookkeeping tail of the turn: nothing a reader can act on.
      _message(
        'a2',
        'assistant',
        [
          Part(id: 'a2-patch', type: 'patch'),
          Part(id: 'a2-snapshot', type: 'snapshot'),
          Part(id: 'a2-finish', type: 'step-finish'),
        ],
        created: 3,
        tokens: Tokens(input: 10, output: 5),
      ),
    ]);

    expect(find.text('Edit'), findsOneWidget);
    // No "…" placeholder and no orphan actions row for the empty message.
    expect(find.text('…'), findsNothing);
    expect(find.byKey(const ValueKey('message-actions-a2')), findsNothing);
    // The real message keeps its actions affordance.
    expect(find.byKey(const ValueKey('message-actions-a1')), findsOneWidget);
  });
}
