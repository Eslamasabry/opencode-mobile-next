import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ComposerApi extends OpenCodeApi with CompleteMessageHistory {
  _ComposerApi() : super(baseUrl: 'http://localhost');

  final prompts = <String>[];
  int abortCalls = 0;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

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
  }) async {
    prompts.add(text);
  }

  @override
  Future<void> abort(String sessionID) async {
    abortCalls += 1;
  }
}

Future<ConnectionController> _pump(
  WidgetTester tester,
  _ComposerApi api, {
  bool busy = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
  if (busy) controller.busySessions.add('session-1');
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  // The working indicator blinks forever, so a busy chat never settles.
  if (busy) {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  } else {
    await tester.pumpAndSettle();
  }
  return controller;
}

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('desktop: Enter sends the draft', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final api = _ComposerApi();
    await _pump(tester, api);

    await _type(tester, 'ship it');
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();

    expect(api.prompts, ['ship it']);
  });

  testWidgets('desktop: Shift+Enter does not send (newline stays with the '
      'field)', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final api = _ComposerApi();
    await _pump(tester, api);

    await _type(tester, 'first line');
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pumpAndSettle();

    expect(api.prompts, isEmpty);
  });

  testWidgets('desktop: Ctrl+Enter still sends', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final api = _ComposerApi();
    await _pump(tester, api);

    await _type(tester, 'go');
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(api.prompts, ['go']);
  });

  testWidgets('mobile: plain Enter keeps inserting a newline, never sends', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.android,
    );
    final api = _ComposerApi();
    await _pump(tester, api);

    await _type(tester, 'draft');
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();

    expect(api.prompts, isEmpty);
  });

  testWidgets('a sent prompt runs at once: the status in the turn, Send '
      'becomes Stop, and the mic stays beside it', (tester) async {
    final semantics = tester.ensureSemantics();
    final api = _ComposerApi();
    await _pump(tester, api);

    await _type(tester, 'go');
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(api.prompts, ['go']);

    // The server has not said it is busy yet; the turn still says it runs
    // (a send that has not been answered reads as thinking).
    expect(find.text('Thinking…'), findsOneWidget);
    final stop = find.byKey(const Key('chat-stop-button'));
    expect(stop, findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('Stop the reply')), findsOneWidget);
    // Stop took Send's slot (nothing left to send); the mic, where the
    // platform has one, is its own button beside it.
    expect(find.byKey(const Key('chat-send-button')), findsNothing);
    expect(
      find.byKey(const Key('composer-voice-button')).evaluate().length,
      lessThanOrEqualTo(1),
    );

    await tester.tap(stop);
    await tester.pumpAndSettle();
    expect(api.abortCalls, 1);
    // Stopped on purpose: no status, and never "No reply came back".
    expect(stop, findsNothing);
    expect(find.text('Thinking…'), findsNothing);
    expect(find.text('No reply came back'), findsNothing);
    semantics.dispose();
  });
}
