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
import 'support/plain_text_finder.dart';

class _FakeOpenCodeApi extends FakeServerGateway with CompleteMessageHistory {
  _FakeOpenCodeApi() : super(baseUrl: 'http://localhost');

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

  // A phone on its side (2480x1116 at 2.75x): the request card must show its
  // question from the top and scroll, never start cut off; and no chips row
  // sits over the composer.
  testWidgets('landscape phone: the request starts at its question, with no '
      'chips row', (tester) async {
    tester.view.physicalSize = const Size(2480, 1116);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission(
        'request-1',
        'edit',
        '/root/projects/tip-calculator/index.html',
      ),
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    final view = tester.view.physicalSize / tester.view.devicePixelRatio;
    final question = tester.getRect(findPlainText('Edit index.html?'));
    // The question is on screen, below the bar, and not under another part.
    expect(question.top, greaterThan(40));
    expect(question.bottom, lessThan(view.height));
    // No chips row: the approval mode is a row behind the composer's +.
    expect(find.text('Asks first'), findsNothing);
    // One scroller holds the card (nested ones cut the question off the top),
    // and scrolling it brings the last row, Details, fully into view.
    expect(
      find.ancestor(
        of: findPlainText('Edit index.html?'),
        matching: find.byType(Scrollable),
      ),
      findsOneWidget,
    );
    await tester.drag(findPlainText('Edit index.html?'), const Offset(0, -300));
    await tester.pumpAndSettle();
    final last = tester.getRect(find.text('Always allow requests like this'));
    expect(last.bottom, lessThanOrEqualTo(view.height));
    await tester.drag(find.text('Reject'), const Offset(0, 300));
    await tester.pumpAndSettle();
    expect(
      tester.getRect(findPlainText('Edit index.html?')).top,
      greaterThan(40),
    );
  });
}
