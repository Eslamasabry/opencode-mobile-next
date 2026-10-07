import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeOpenCodeApi extends OpenCodeApi with CompleteMessageHistory {
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

/// An answer waits out its undo window before it is sent.
Future<void> _afterHold(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 3));
  await tester.pumpAndSettle();
}

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

  // chat-5: the card answers in place (one tap for Allow once); Details
  // opens the one request sheet.
  testWidgets('chat queues concurrent permissions and replies by request ID', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    await pumpChat(tester, controller);

    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );
    controller.handleEventForTesting(
      _permission('request-2', 'edit', 'lib/main.dart'),
    );
    await tester.pumpAndSettle();

    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.text('Edit a file'), findsNothing);
    expect(find.textContaining('1 more request is waiting'), findsOneWidget);

    await tester.tap(find.byKey(const Key('permission-card-allow')));
    await _afterHold(tester);

    expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
    expect(find.text('Edit a file'), findsOneWidget);
  });

  group('undo window', () {
    Future<(_FakeOpenCodeApi, ConnectionController)> setUpChat(
      WidgetTester tester,
    ) async {
      final api = _FakeOpenCodeApi();
      final controller = await _controller(api);
      controller.handleEventForTesting(_permission('request-1', 'bash', 'ls'));
      await pumpChat(tester, controller);
      return (api, controller);
    }

    testWidgets('Allow once is held for 3 s with Undo, then sent', (
      tester,
    ) async {
      final (api, _) = await setUpChat(tester);

      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(api.replies, isEmpty);
      expect(
        find.textContaining('Allowed', findRichText: true),
        findsOneWidget,
      );
      expect(find.byKey(const Key('permission-card-undo')), findsOneWidget);
      expect(find.byKey(const Key('permission-card-allow')), findsNothing);

      await tester.pump(const Duration(milliseconds: 2400));
      expect(api.replies, isEmpty);

      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
    });

    testWidgets('Reject says Rejected while held', (tester) async {
      final (api, _) = await setUpChat(tester);

      await tester.tap(find.byKey(const Key('permission-card-reject')));
      await tester.pump();
      expect(
        find.textContaining('Rejected', findRichText: true),
        findsOneWidget,
      );
      expect(api.replies, isEmpty);
      await _afterHold(tester);
      expect(api.replies, [(requestID: 'request-1', reply: 'reject')]);
    });

    testWidgets('Undo within the window sends nothing and brings the card '
        'back', (tester) async {
      final (api, controller) = await setUpChat(tester);

      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await tester.pump(const Duration(seconds: 1));
      await tester.tap(find.byKey(const Key('permission-card-undo')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
      expect(find.byKey(const Key('permission-card-undo')), findsNothing);
      await tester.pump(const Duration(seconds: 5));
      expect(api.replies, isEmpty);
      expect(controller.permissions, contains('request-1'));
    });

    testWidgets('leaving the chat sends the held answer at once', (
      tester,
    ) async {
      final (api, _) = await setUpChat(tester);

      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await tester.pump();
      expect(api.replies, isEmpty);

      await tester.pumpWidget(const MaterialApp(home: SizedBox()));
      await tester.pump();
      expect(api.replies, [(requestID: 'request-1', reply: 'once')]);
    });

    testWidgets('answered elsewhere while held drops the held answer', (
      tester,
    ) async {
      final (api, controller) = await setUpChat(tester);

      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await tester.pump();
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {'requestID': 'request-1', 'reply': 'once'},
        ),
      );
      await _afterHold(tester);
      expect(api.replies, isEmpty);
    });
  });

  testWidgets('Always allow states its scope in the sheet before it sends', (
    tester,
  ) async {
    // The card now carries Always allow, so it is taller than the default
    // 600 dp test window leaves room for above the composer.
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status', always: ['git *', 'gh *']),
    );
    await pumpChat(tester, controller);

    // The request lands as an inline card above the composer, not as a
    // modal sheet: nothing steals the keyboard until the person asks.
    expect(find.byKey(const Key('permission-sheet')), findsNothing);
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);

    await tester.tap(find.byKey(const Key('permission-allow-always')));
    await tester.pumpAndSettle();
    expect(find.textContaining('git *, gh *'), findsOneWidget);
    expect(api.replies, isEmpty);
    await tester.ensureVisible(find.text('Turn on'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Turn on'));
    await _afterHold(tester);
    expect(api.replies, [(requestID: 'request-1', reply: 'always')]);
  });

  testWidgets('a failed reply keeps the card with the refusal in words', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()..failReplies = true;
    final controller = await _controller(api);
    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );
    await pumpChat(tester, controller);

    await tester.tap(find.byKey(const Key('permission-card-allow')));
    await _afterHold(tester);

    expect(find.textContaining('Not accepted'), findsOneWidget);
    // The refusal in plain words; the server's prose is Details only
    // (6cdfca4e, a65dcea9: no raw errors as copy).
    expect(
      find.textContaining("The server didn't accept the request."),
      findsOneWidget,
    );
    expect(find.textContaining('server refused the reply'), findsNothing);
    expect(controller.permissions, contains('request-1'));
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
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
      expect(find.text('Run a shell command'), findsOneWidget);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {'requestID': 'request-1', 'reply': 'once'},
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Run a shell command'), findsNothing);
      expect(find.text('Edit a file'), findsOneWidget);
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
      await pumpChat(tester, controller);

      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await _afterHold(tester);

      expect(controller.permissions.keys, ['request-2']);
      expect(find.text('Run a shell command'), findsNothing);
      expect(find.text('Edit a file'), findsOneWidget);
      expect(find.textContaining('Not accepted'), findsNothing);

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
    await pumpChat(tester, controller);

    expect(tester.takeException(), isNull);
    final allow = find.byKey(const Key('permission-card-allow'));
    await tester.ensureVisible(allow);
    await tester.pumpAndSettle();
    await tester.tap(allow);
    await _afterHold(tester);
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
    expect(find.text('Run a shell command'), findsOneWidget);

    await controller.refreshPendingPermissions();
    await tester.pumpAndSettle();

    expect(controller.permissions, isEmpty);
    expect(find.text('Run a shell command'), findsNothing);
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets('the attention card keeps the composer focus and Details opens '
      'a dismissible sheet', (tester) async {
    // The card now carries Always allow, so it is taller than the default
    // 600 dp test window leaves room for above the composer.
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    await pumpChat(tester, controller);
    final composer = find.byKey(const Key('chat-composer-field'));
    await tester.tap(composer);
    await tester.pumpAndSettle();
    final field = tester.widget<EditableText>(
      find.descendant(of: composer, matching: find.byType(EditableText)),
    );
    expect(field.focusNode.hasFocus, isTrue);
    await tester.enterText(composer, 'Keep this draft');
    await tester.pumpAndSettle();

    controller.handleEventForTesting(
      _permission('request-1', 'bash', 'git status'),
    );
    await tester.pumpAndSettle();

    // Inline card, focus untouched, no modal route.
    expect(find.byKey(const Key('permission-card-request-1')), findsOneWidget);
    expect(field.focusNode.hasFocus, isTrue);
    expect(find.byKey(const Key('permission-sheet')), findsNothing);

    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    // Tapping outside dismisses the sheet and leaves the card in place.
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsNothing);
    expect(find.byKey(const Key('permission-card-request-1')), findsOneWidget);
    expect(api.replies, isEmpty);
    expect(field.controller.text, 'Keep this draft');
  });
}
