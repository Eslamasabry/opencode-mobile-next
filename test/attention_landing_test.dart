// P4.2a "Inbox rows land on the card" (docs/ux-system/revamp/work-units.json
// slice-P4.2a; contract docs/qa/codex-inbox-2026-09-28/README.md): a chat
// opened for one request lands on that card, which leads and is marked
// once; a chat opened for a failed run lands on its newest failed turn; a
// notification lands on the card instead of a sheet over a list.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/profile_monitor.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/navigation/attention_landing.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _Api extends CaptureApi {
  _Api(this.history) {
    busy = {};
  }

  final List<MessageWithParts> history;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? history : const []);
}

/// Monitored requests are checked on their server before a switch; here
/// that check is the test's answer.
class _Controller extends CaptureController {
  _Controller(super.store);

  bool monitoredReady = true;

  @override
  Future<bool> prepareMonitoredRequest(MonitoredRoute target) async =>
      monitoredReady;
}

PermissionRequest _permission(String id) => PermissionRequest(
  id: id,
  sessionID: checkoutSessionID,
  permission: 'edit',
  patterns: ['lib/$id.dart'],
);

List<MessageWithParts> _history({bool failed = false}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now - 9000),
      parts: [textPart('part_user', userPrompt)],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'msg_failed',
        sessionID: checkoutSessionID,
        role: 'assistant',
        time: MsgTime(created: now - 8000, completed: now - 7000),
        errorText: failed ? 'Provider is overloaded.' : null,
      ),
      parts: [textPart('part_reply', 'Working on it.')],
    ),
  ];
}

Future<_Controller> _pump(
  WidgetTester tester, {
  Map<String, PermissionRequest> permissions = const {},
  bool failed = false,
  Widget Function(_Controller controller)? home,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final api = _Api(_history(failed: failed));
  final controller =
      _Controller(
          SeededProfileStore(
            prefs: prefs,
            seeded: [
              ServerProfile(
                id: 'laptop',
                name: 'Laptop',
                baseUrl: 'http://192.168.1.20:4096',
              ),
            ],
          ),
        )
        ..api = api
        ..repository = CaptureRepository()
        ..status = StreamStatus.connected
        ..directory = projectDirectory
        ..sessionsById = Map.of(api.sessionsById)
        ..busySessions = <String>{}
        ..permissions = Map.of(permissions);
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 3));
    controller.dispose();
  });
  await tester.pumpWidget(
    captureApp(
      home: home?.call(controller) ?? const Scaffold(),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  await tester.pump();
  return controller;
}

Future<void> _frames(WidgetTester tester, [int count = 8]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// The accent wash KitArrival paints over the card it landed on.
bool _washed(WidgetTester tester, Finder card) => tester
    .widgetList<AnimatedOpacity>(
      find.descendant(
        of: find.ancestor(of: card, matching: find.byType(KitArrival)),
        matching: find.byType(AnimatedOpacity),
      ),
    )
    .any((opacity) => opacity.child is ColoredBox && opacity.opacity == 1);

BuildContext _context(WidgetTester tester) =>
    tester.element(find.byType(Scaffold).first);

NavigatorState _navigator(WidgetTester tester) =>
    tester.state<NavigatorState>(find.byType(Navigator));

MonitoredRoute _route(_Controller controller, String requestID) {
  final profile = controller.store.profiles.single;
  return MonitoredRoute(
    profileID: profile.id,
    requestID: requestID,
    sessionID: checkoutSessionID,
    kind: MonitoredRequestKind.permission,
    createdAt: DateTime.now(),
    serverUrl: profile.baseUrl,
    sourceIdentity: 'fixture',
    directory: projectDirectory,
  );
}

void main() {
  testWidgets('a row for the second of two permissions lands on that card: '
      'it leads and is marked once', (tester) async {
    final controller = await _pump(
      tester,
      permissions: {
        'perm-a': _permission('perm-a'),
        'perm-b': _permission('perm-b'),
      },
    );
    unawaited(
      _navigator(tester).push(
        chatLandingRoute(
          sessionID: checkoutSessionID,
          landOnRequestID: 'perm-b',
        ),
      ),
    );
    await _frames(tester);

    final card = find.byKey(const ValueKey('permission-card-perm-b'));
    expect(card, findsOneWidget);
    expect(find.byKey(const ValueKey('permission-card-perm-a')), findsNothing);
    expect(_washed(tester, card), isTrue);
    // Nothing was answered by opening it.
    expect(controller.answered, isEmpty);

    // The mark fades after its hold and never comes back on a rebuild.
    await tester.pump(KitArrival.hold);
    await _frames(tester);
    expect(_washed(tester, card), isFalse);
  });

  testWidgets('a notification whose request cannot be confirmed on this '
      'server opens its conversation, never a sheet', (tester) async {
    final controller = await _pump(tester);
    controller.monitoredReady = false;
    unawaited(
      openMonitoredRequest(
        _context(tester),
        controller,
        _route(controller, 'perm-a'),
      ),
    );
    await _frames(tester);
    // Already on that server: the conversation opens; there is no card to
    // mark until the server lists the request.
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('permission-card-perm-a')), findsNothing);
    expect(find.byKey(const Key('permission-sheet')), findsNothing);
  });

  testWidgets('a failed run opens on its newest failed turn, marked', (
    tester,
  ) async {
    await _pump(tester, failed: true);
    unawaited(
      _navigator(tester).push(
        chatLandingRoute(sessionID: checkoutSessionID, landOnFailure: true),
      ),
    );
    await _frames(tester);

    final turns = tester.widgetList<KitTurn>(find.byType(KitTurn)).toList();
    expect(turns.where((turn) => turn.highlighted), isNotEmpty);
  });

  testWidgets('a notification for a request lands on its card in the '
      'conversation, not on a sheet over a list', (tester) async {
    final controller = await _pump(
      tester,
      permissions: {'perm-a': _permission('perm-a')},
    );
    unawaited(
      openMonitoredRequest(
        _context(tester),
        controller,
        _route(controller, 'perm-a'),
      ),
    );
    await _frames(tester);

    expect(find.byKey(const Key('permission-sheet')), findsNothing);
    final card = find.byKey(const ValueKey('permission-card-perm-a'));
    expect(card, findsOneWidget);
    expect(_washed(tester, card), isTrue);
  });
}
