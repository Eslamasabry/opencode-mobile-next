// 13B: "Always allow" on the permission card asks one confirm, then sends
// exactly one 'always' reply; Cancel grants nothing; a server without
// standing grants never shows it; a notification can never grant it.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeOpenCodeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeOpenCodeApi({this.noStandingGrants = false})
    : super(baseUrl: 'http://localhost');

  final bool noStandingGrants;

  @override
  ServerCapabilities get capabilities =>
      noStandingGrants ? codexServerCapabilities : super.capabilities;

  final List<({String requestID, String reply})> replies = [];
  bool failReplies = false;
  bool permissionNotFound = false;
  List<PermissionRequest> pendingPermissionsResult = [];

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    replies.add((requestID: requestID, reply: reply));
    if (permissionNotFound) {
      permissionNotFound = false;
      throw ApiException(
        'Permission request not found',
        statusCode: 404,
        errorTag: 'PermissionNotFoundError',
        requestID: requestID,
      );
    }
    if (failReplies) {
      throw ApiException('server refused the reply', statusCode: 400);
    }
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async =>
      pendingPermissionsResult;

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

Future<ConnectionController> _controller(_FakeOpenCodeApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
}

EventEnvelope _permission(
  String id,
  String permission,
  String pattern, {
  List<String> always = const [],
}) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': id,
    'sessionID': 'session-1',
    'permission': permission,
    'patterns': [pattern],
    'metadata': <String, Object?>{},
    'always': always,
  },
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpChat(
    WidgetTester tester,
    ConnectionController controller,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();
  }

  const always = Key('permission-card-always');
  const confirm = Key('permission-card-always-confirm');

  testWidgets('Always allow asks one confirm naming the command and project', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status', always: ['git *']),
    );
    await pumpChat(tester, controller);

    await tester.tap(find.byKey(always));
    await tester.pumpAndSettle();
    // The command is bidi-isolated, so match the words around it.
    expect(
      find.textContaining(
        RegExp(r'^Always allow .*git status.* in this project\?$'),
      ),
      findsOneWidget,
    );
    expect(find.textContaining('git *'), findsWidgets);
    expect(find.text('Cancel'), findsOneWidget);
    expect(api.replies, isEmpty);
  });

  testWidgets('Cancel grants nothing and the card keeps its answers', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(_permission('request-1', 'bash', 'ls'));
    await pumpChat(tester, controller);

    await tester.tap(find.byKey(always));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(api.replies, isEmpty);
    expect(controller.permissions, contains('request-1'));
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(find.byKey(always), findsOneWidget);
  });

  testWidgets('confirming sends exactly one always reply for the request', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(_permission('request-1', 'bash', 'ls'));
    controller.handleEventForTesting(_permission('request-2', 'bash', 'pwd'));
    await pumpChat(tester, controller);

    await tester.tap(find.byKey(always));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(confirm));
    await tester.pump();
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    expect(api.replies, [(requestID: 'request-1', reply: 'always')]);
  });

  testWidgets('a server without standing grants hides Always allow', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi(noStandingGrants: true);
    final controller = await _controller(api);
    controller.handleEventForTesting(_permission('request-1', 'bash', 'ls'));
    await pumpChat(tester, controller);

    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(find.byKey(always), findsNothing);
  });

  testWidgets('a notification action never sends always', (tester) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(_permission('request-1', 'bash', 'ls'));

    Map<String, Object?> action(String decision) => {
      'kind': 'permission',
      'sessionID': 'session-1',
      'requestID': 'request-1',
      'decision': decision,
    };
    final result = await controller.backgroundLive.handleNativeAction(
      action('always'),
    );
    expect(result['handled'], isFalse);
    expect(api.replies, isEmpty);

    await controller.backgroundLive.handleNativeAction(action('allow'));
    expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
  });
}
