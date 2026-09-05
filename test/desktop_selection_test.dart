import 'support/complete_message_history.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A widget test that runs with the platform reported as Linux desktop. The
/// override must be cleared inside the body: flutter_test asserts no
/// foundation debug variable outlives the test, so tearDown runs too late.
void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

class _TranscriptApi extends OpenCodeApi with CompleteMessageHistory {
  _TranscriptApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: 'session-1',
        role: 'assistant',
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: [
        Part(id: 'p1', type: 'text', text: 'selectable transcript prose'),
      ],
    ),
  ];
}

List<String> _captureClipboard() {
  final written = <String>[];
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          written.add((call.arguments as Map)['text'] as String);
        }
        return null;
      });
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null),
  );
  return written;
}

Future<void> _pumpChat(WidgetTester tester) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final connection = ConnectionController(ProfileStore(prefs: prefs))
    ..api = _TranscriptApi()
    ..status = StreamStatus.connected;
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(connection)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  desktopTest('the transcript is selectable and Ctrl+C copies it', (
    tester,
  ) async {
    final clipboard = _captureClipboard();
    await _pumpChat(tester);

    expect(
      find.byType(SelectionArea),
      findsOneWidget,
      reason: 'desktop restores mouse selection over the chat bubbles',
    );

    final prose = find.text('selectable transcript prose');
    final box = tester.getRect(prose);
    final gesture = await tester.startGesture(
      box.centerLeft + const Offset(2, 0),
      kind: PointerDeviceKind.mouse,
    );
    await tester.pump();
    await gesture.moveTo(box.centerRight - const Offset(2, 0));
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.keyC);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(clipboard, isNotEmpty);
    expect(clipboard.single, contains('selectable transcript prose'));
  });

  testWidgets('android keeps the transcript non-selectable', (tester) async {
    // selectable: false exists so a long press reaches the actions sheet.
    await _pumpChat(tester);
    expect(find.byType(SelectionArea), findsNothing);
  });
}
