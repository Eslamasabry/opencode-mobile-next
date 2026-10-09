// The real ChatScreen over a fake connection, for the Paseo coverage ratchets
// that must read what a person reads in a conversation (timeline items,
// permission and question cards).
//
// ignore_for_file: invalid_use_of_protected_member
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

/// One page of history.
class CoverageApi extends CaptureApi {
  CoverageApi([this.caps]);

  /// The server's abilities; Paseo's for the permission cases.
  final ServerCapabilities? caps;

  @override
  ServerCapabilities get capabilities => caps ?? super.capabilities;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<void> frames(WidgetTester tester, [int count = 8]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// Pumps the chat of [checkoutSessionID] showing [messages] (and whatever
/// [setUp] puts on the controller: pending permissions, questions) in a
/// 412x915 window, under [boundary]. [busy] makes the conversation look
/// like the agent is working.
Future<CaptureController> pumpCoverageChat(
  WidgetTester tester,
  GlobalKey boundary, {
  required List<MessageWithParts> messages,
  void Function(CaptureController controller)? setUp,
  bool busy = false,
  ServerCapabilities? capabilities,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final api = CoverageApi(capabilities)
    ..busy = busy ? {checkoutSessionID} : {}
    ..messagesHandler = (_) async => messages;
  final controller = await captureController(
    prefs: await SharedPreferences.getInstance(),
    api: api,
  );
  setUp?.call(controller);
  final navigatorKey = GlobalKey<NavigatorState>();
  await tester.pumpWidget(
    captureApp(
      home: Builder(
        builder: (context) => ListenableBuilder(
          listenable: controller,
          builder: (context, _) => AppConditionsScope(
            conditions: [
              connectionKitStatus(
                context,
                controller,
                actionContext: () =>
                    navigatorKey.currentState?.overlay?.context,
              ),
            ],
            child: const ChatScreen(sessionID: checkoutSessionID),
          ),
        ),
      ),
      boundaryKey: boundary,
      navigatorKey: navigatorKey,
      controller: controller,
    ),
  );
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });
  await frames(tester);
  return controller;
}

/// Taps every fold a person would open to read a conversation, once each:
/// the work line, a step in it, a thought, an error's details and a
/// compaction's notice.
Future<void> openFolds(WidgetTester tester) async {
  Future<void> tapOnce(Finder target) async {
    if (target.evaluate().isEmpty) return;
    await tester.tap(target.first, warnIfMissed: false);
    await frames(tester, 6);
  }

  await tapOnce(find.byKey(const Key('work-group-header')));
  await tapOnce(find.byType(KitToolRow));
  await tapOnce(find.byKey(const Key('reasoning-toggle')));
  await tapOnce(find.byKey(const Key('error-action-details')));
  await tapOnce(
    find.byWidgetPredicate(
      (widget) =>
          widget is TranscriptNotice &&
          widget.key.toString().contains('compaction-completed'),
    ),
  );
}
