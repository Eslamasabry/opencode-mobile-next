// The real ChatScreen over a fake connection, for the Paseo coverage ratchets
// that must read what a person reads in a conversation (timeline items,
// permission and question cards).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/form_request.dart' show Api2FormInfo;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import 'paseo_coverage_support.dart' show screenText, writeCasePng;

/// One page of history.
class CoverageApi extends CaptureApi {
  CoverageApi([this.caps]);

  /// The server's abilities; Paseo's for the permission cases.
  final ServerCapabilities? caps;

  /// Forms an OpenCode 2 server is waiting on.
  List<Api2FormInfo> forms = const [];

  @override
  Future<List<Api2FormInfo>> pendingForms() async => forms;

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
  List<Api2FormInfo> forms = const [],
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final api = CoverageApi(capabilities)
    ..forms = forms
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
/// A step that opens another conversation (a sub-agent's) is left shut with
/// [rows] false: tapping it leaves the screen.
Future<void> openFolds(WidgetTester tester, {bool rows = true}) async {
  Future<void> tapOnce(Finder target) async {
    if (target.evaluate().isEmpty) return;
    await tester.tap(target.first, warnIfMissed: false);
    await frames(tester, 6);
  }

  // Every item of a kind, once each (the tree changes as folds open).
  Future<void> tapEach(Finder target) async {
    final count = target.evaluate().length;
    for (var i = 0; i < count; i++) {
      if (i >= target.evaluate().length) return;
      await tester.tap(target.at(i), warnIfMissed: false);
      await frames(tester, 6);
    }
  }

  await tapOnce(find.byKey(const Key('work-group-header')));
  // Commands run on their own (no prompt before them) fold under one line.
  await tapOnce(find.textContaining(RegExp(r'^Ran \d+ commands?')));
  if (rows) await tapEach(find.byType(KitToolRow));
  await tapOnce(find.byKey(const Key('reasoning-toggle')));
  await tapOnce(find.byKey(const Key('error-action-details')));
  // Notices that start shut (an open one would close if tapped).
  await tapEach(
    find.byWidgetPredicate(
      (widget) =>
          widget is TranscriptNotice &&
          !widget.error &&
          !widget.initiallyOpen &&
          widget.text.trim().isNotEmpty,
    ),
  );
}

/// Opens what a pending permission or question card hides, and returns the
/// text readable at each step joined: the card, its Details sheet and, for a
/// question that needs more than a tap, the answer sheet.
Future<String> openRequestCards(
  WidgetTester tester, {
  GlobalKey? boundary,
  String? name,
}) async {
  final seen = <String>[];
  String read() => screenText(tester).join('\n');
  final review = find.byKey(const Key('permission-card-review'));
  if (review.evaluate().isNotEmpty) {
    await tester.tap(review.first, warnIfMissed: false);
    await frames(tester);
  }
  seen.add(read());
  final fold = find.text('Details');
  if (find.byKey(const Key('permission-sheet')).evaluate().isNotEmpty &&
      fold.evaluate().isNotEmpty) {
    await tester.tap(fold.last, warnIfMissed: false);
    await frames(tester);
    seen.add(read());
  }
  final answer = find.text('Answer');
  if (answer.evaluate().isNotEmpty) {
    await tester.tap(answer.first, warnIfMissed: false);
    await frames(tester);
    seen.add(read());
    if (boundary != null && name != null) {
      await writeCasePng(tester, boundary, '${name}_answer');
    }
  }
  return seen.join('\n');
}

/// Opens the status line's menu and its Details (an error's own words).
Future<void> openStatusDetails(WidgetTester tester) async {
  final more = find.byKey(const ValueKey('kit-status-more'));
  if (more.evaluate().isEmpty) return;
  await tester.tap(more.first, warnIfMissed: false);
  await frames(tester);
  final details = find.text('Details');
  if (details.evaluate().isEmpty) return;
  await tester.tap(details.last, warnIfMissed: false);
  await frames(tester);
}

/// Everything a person can open in a conversation, each step read before the
/// next (a sheet that stays open would swallow the next tap): the folds, the
/// status line's Details and the request cards. Returns all the text seen.
Future<String> openEverything(
  WidgetTester tester, {
  bool rows = true,
  GlobalKey? boundary,
  String? name,
}) async {
  final seen = <String>[];
  String read() => screenText(tester).join('\n');
  Future<void> closeSheets() async {
    final navigator = tester.state<NavigatorState>(
      find.byType(Navigator).first,
    );
    await navigator.maybePop();
    await frames(tester, 4);
  }

  await openFolds(tester, rows: rows);
  seen.add(read());
  await closeSheets();
  await openStatusDetails(tester);
  seen.add(read());
  await closeSheets();
  seen.add(await openRequestCards(tester, boundary: boundary, name: name));
  return seen.join('\n');
}
