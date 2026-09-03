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

class _ComposerApi extends OpenCodeApi {
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

  testWidgets('the Stop tooltip is dismissed when Stop is pressed', (
    tester,
  ) async {
    final api = _ComposerApi();
    final controller = await _pump(tester, api, busy: true);

    // Stop has its own button next to a live Send while the run is active.
    final stop = find.byKey(const Key('chat-stop-button'));
    expect(tester.widget<IconButton>(stop).tooltip, 'Stop');
    // Long-press shows the tooltip the way a touch user would see it.
    await tester.longPress(stop);
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('Stop'), findsOneWidget);

    await tester.tap(stop);
    await tester.pump();
    expect(api.abortCalls, 1);
    // The run ends and Stop goes away; nothing may still say Stop.
    controller.busySessions.remove('session-1');
    controller.notifyListeners();
    await tester.pumpAndSettle();

    expect(find.text('Stop'), findsNothing);
    expect(stop, findsNothing);
    expect(
      tester
          .widget<IconButton>(find.byKey(const Key('chat-send-button')))
          .tooltip,
      'Send',
    );
  });
}
