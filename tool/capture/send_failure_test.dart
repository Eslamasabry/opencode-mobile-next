// Renders of a message that wasn't sent (fix/send-failure-details,
// 2026-10-27): the real ChatScreen of an agent on this phone, dark theme,
// real fonts, phone 412x915 dp at 2x, one full screen per image. The agent
// refused "Say pong" and the chat says why and what to do, on the banner
// and under the prompt that got no reply.
//
//   flutter test --concurrency=1 tool/capture/send_failure_test.dart
//   flutter test --concurrency=1 --dart-define=SEND_FAILURE_OUT=<dir> \
//     tool/capture/send_failure_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/send-failure/<kind>-dark.png
//
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_run_failure.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _out = String.fromEnvironment(
  'SEND_FAILURE_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/send-failure',
);
const _size = Size(412, 915);
const _session = 'kilo-chat';
const _model = 'kilo/google/gemini-3-pro-image';

/// An agent on this phone whose sends fail straight back.
class _Api extends CaptureApi {
  _Api(this.error) {
    final now = DateTime.now().millisecondsSinceEpoch;
    busy = {};
    sessionsById = {
      _session: Session(
        id: _session,
        title: 'Say pong',
        directory: projectDirectory,
        time: SessionTime(created: now - 120000, updated: now - 60000),
      ),
    };
    messagesHandler = (_) async => [
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_user',
          sessionID: _session,
          role: 'user',
          time: MsgTime(created: now - 60000, completed: now - 60000),
        ),
        parts: [
          Part(
            id: 'part_user',
            messageID: 'msg_user',
            type: 'text',
            text: 'Say pong',
          ),
        ],
      ),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_assistant',
          sessionID: _session,
          role: 'assistant',
          providerID: 'kilo',
          time: MsgTime(created: now - 59000, completed: now - 58000),
        ),
        parts: const [],
      ),
    ];
  }

  final Object error;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(offlinePromptQueue: false);

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async => throw error;
}

/// The conversations of the agents on this phone (their own connection).
class _AgentController extends ConnectionController {
  _AgentController(super.store) : super(isAgentBackend: true);

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> refreshPendingForms() async {}

  @override
  Future<void> refreshCatalog() async {}
}

PaseoFailure _refused(String reason) => PaseoFailure(
  PaseoFailureKind.unavailable,
  reason: reason,
  runFailure: AgentRunFailure(reason: reason, provider: 'kilo', model: _model),
);

Future<void> _settle(WidgetTester tester, [int frames = 10]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _render(WidgetTester tester, String name, Object error) async {
  tester.view.physicalSize = _size * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  // An empty in-memory store, through the public platform interface
  // (setMockInitialValues is test-only API and this is tool/).
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  final store = SeededProfileStore(prefs: prefs, seeded: const []);
  final api = _Api(error);
  final controller = _AgentController(store)
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById);
  final key = GlobalKey();
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: _session),
      boundaryKey: key,
      controller: controller,
    ),
  );
  await _settle(tester);
  await tester.enterText(
    find.byKey(const Key('chat-composer-field')),
    'Reply with the word banana',
  );
  await tester.pump();
  await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
  await _settle(tester, 14);
  expect(tester.takeException(), isNull);
  await writePng('$_out/$name-dark.png', await capturePng(tester, key));
  await tester.pumpWidget(const SizedBox.shrink());
  controller.dispose();
  await tester.pump(const Duration(seconds: 3));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('not signed in', (tester) async {
    await _render(
      tester,
      'not-signed-in',
      _refused('Authentication required (code: -32000)'),
    );
  });

  testWidgets('model unavailable', (tester) async {
    await _render(
      tester,
      'model-unavailable',
      _refused('Model $_model is not available without a Kilo account'),
    );
  });
}
