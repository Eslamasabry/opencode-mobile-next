import 'package:opencode_mobile/domain/server_gateway.dart';
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _TranscriptApi extends FakeServerGateway with CompleteMessageHistory {
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
  // Working indicators can animate indefinitely, so use bounded frames.
  if (busy) {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  } else {
    await tester.pumpAndSettle();
  }
  return controller;
}

MessageWithParts _running(
  String id,
  List<Part> parts, {
  required int created,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    providerID: 'anthropic',
    modelID: 'claude',
    time: MsgTime(created: created),
  ),
  parts: parts,
);

Part _tool(String id, String status, String command) => Part(
  id: id,
  type: 'tool',
  callID: id,
  toolName: 'bash',
  toolState: ToolState.fromJson({
    'status': status,
    'input': {'command': command},
    if (status == 'completed') 'output': 'ok',
  }, toolName: 'bash'),
);

MessageWithParts _compaction(String id, String kind, int created) =>
    _message(id, 'user', [
      Part(
        id: '$id-p',
        messageID: id,
        type: 'v2:compaction',
        toolName: kind,
        text: '',
      ),
    ], created: created);

// Owner's phone, 2026-10-28 (a Claude Code chat): after a compaction in the
// middle of a running turn the newest row read "This part of OpenCode hit an
// error", and "Compacting conversation…" stayed above the summary. Settling
// dropped the stale compacting row, so the rows, the parts and the turn
// owners no longer shared one index and the newest row read past the end
// (RangeError in _transcriptRow).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final variants = <String, List<MessageWithParts> Function()>{
    'running compaction then a running step': () => [
      _message('u1', 'user', [_text('u1-t', 'Continue the work')], created: 1),
      _message('a1', 'assistant', [
        _tool('t1', 'completed', 'adb connect'),
      ], created: 2),
      _compaction('c1', 'running', 3),
      _compaction('c2', 'completed', 4),
      _running('a2', [
        _text('a2-t', 'It is an ANR.'),
        _tool('t2', 'running', 'cd /root && grep x'),
      ], created: 5),
    ],
    'running compaction last': () => [
      _message('u1', 'user', [_text('u1-t', 'Continue the work')], created: 1),
      _message('a1', 'assistant', [
        _tool('t1', 'completed', 'adb connect'),
      ], created: 2),
      _compaction('c1', 'running', 3),
    ],
    'only compaction rows after the summary': () => [
      _compaction('c1', 'running', 3),
      _compaction('c2', 'completed', 4),
      _running('a2', [
        _tool('t2', 'running', 'cd /root && grep x'),
      ], created: 5),
    ],
    'compaction completed, running step, no prompt': () => [
      _compaction('c2', 'completed', 4),
      _running('a2', [
        _text('a2-t', 'It is an ANR.'),
        _tool('t2', 'running', 'grep'),
      ], created: 5),
    ],
  };
  for (final entry in variants.entries) {
    testWidgets('a compaction mid-turn draws every row: ${entry.key}', (
      tester,
    ) async {
      final errors = <FlutterErrorDetails>[];
      final old = FlutterError.onError;
      FlutterError.onError = errors.add;
      await _pump(tester, entry.value(), busy: true);
      FlutterError.onError = old;
      expect(errors.map((e) => e.exception), isEmpty);
      // The compacting line shows only while it is the newest thing.
      expect(
        find.text('Compacting conversation…'),
        entry.key == 'running compaction last' ? findsOneWidget : findsNothing,
      );
    });
  }
}
