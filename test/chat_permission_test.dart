import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeOpenCodeApi extends OpenCodeApi {
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
    if (failReplies) throw ApiException('server refused the reply');
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async =>
      pendingPermissionsResult;
}

Future<ConnectionController> _controller(_FakeOpenCodeApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))..api = api;
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

  testWidgets('chat queues concurrent permissions and replies by request ID', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );
    controller.handleEventForTesting(
      _permission('request-2', 'edit', 'lib/main.dart'),
    );
    await tester.pumpAndSettle();

    expect(find.text('bash'), findsOneWidget);
    expect(find.text('git status'), findsOneWidget);
    expect(find.text('edit'), findsNothing);

    await tester.tap(find.text('Allow once'));
    await tester.pumpAndSettle();

    expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
    expect(find.text('edit'), findsOneWidget);
    expect(find.text('lib/main.dart'), findsOneWidget);
  });

  testWidgets('chat distinguishes requested patterns from always-allow scope', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status', always: ['git *', 'gh *']),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Requested for this action'), findsOneWidget);
    expect(find.text('git status'), findsOneWidget);
    expect(find.text('Always allow would also cover'), findsOneWidget);
    expect(find.text('git *\ngh *'), findsOneWidget);
    expect(find.text('Always allow'), findsOneWidget);
    await tester.tap(find.text('Always allow'));
    await tester.pumpAndSettle();
    expect(find.text('Confirm broader access'), findsOneWidget);
    expect(find.text('git *\ngh *'), findsWidgets);
    expect(find.textContaining('Allow once is safer'), findsOneWidget);
    expect(api.replies, isEmpty);
    await tester.tap(find.text('Confirm always allow'));
    await tester.pumpAndSettle();
    expect(api.replies, [(requestID: 'request-1', reply: 'always')]);
  });

  testWidgets('chat keeps failed permission open and shows the failure', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()..failReplies = true;
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Allow once'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Reply failed:'), findsOneWidget);
    expect(find.textContaining('server refused the reply'), findsOneWidget);
    expect(controller.permissions, contains('request-1'));
  });

  testWidgets(
    'external resolution dismisses the active dialog and advances the queue',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final controller = await _controller(api);
      controller.handleEventForTesting(
        _permission('request-1', 'bash', 'git status'),
      );
      controller.handleEventForTesting(
        _permission('request-2', 'edit', 'lib/main.dart'),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('bash'), findsOneWidget);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {'requestID': 'request-1', 'reply': 'once'},
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('bash'), findsNothing);
      expect(find.text('edit'), findsOneWidget);
      expect(find.text('lib/main.dart'), findsOneWidget);
      expect(find.textContaining('no longer pending'), findsNothing);
    },
  );

  testWidgets(
    'permission-not-found reply race dismisses and advances the queue',
    (tester) async {
      final api = _FakeOpenCodeApi()..permissionNotFound = true;
      final controller = await _controller(api);
      controller.handleEventForTesting(
        _permission('request-1', 'bash', 'git status'),
      );
      controller.handleEventForTesting(
        _permission('request-2', 'edit', 'lib/main.dart'),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Allow once'));
      await tester.pumpAndSettle();

      expect(controller.permissions.keys, ['request-2']);
      expect(find.text('bash'), findsNothing);
      expect(find.text('edit'), findsOneWidget);
      expect(find.textContaining('Reply failed:'), findsNothing);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {'requestID': 'request-1', 'reply': 'once'},
        ),
      );
      await tester.pump();
      expect(controller.permissions.keys, ['request-2']);
    },
  );

  testWidgets('long permission content keeps actions accessible on mobile', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 480));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: {
          'id': 'request-1',
          'sessionID': 'session-1',
          'permission': 'bash',
          'patterns': List.generate(
            30,
            (index) => 'requested command $index with a long argument',
          ),
          'metadata': <String, Object?>{},
          'always': List.generate(
            20,
            (index) => 'broader always pattern $index *',
          ),
        },
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Allow once'), findsOneWidget);
    await tester.tap(find.text('Allow once'));
    await tester.pumpAndSettle();
    expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
  });

  testWidgets('hydration dismisses an active permission removed remotely', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('bash'), findsOneWidget);

    await controller.refreshPendingPermissions();
    await tester.pumpAndSettle();

    expect(controller.permissions, isEmpty);
    expect(find.text('bash'), findsNothing);
    expect(find.byType(AlertDialog), findsNothing);
  });
}
