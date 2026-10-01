// Issue #10: permission-card edge cases. A notification tap and an in-app tap
// on one request send one reply; a remote withdrawal lowers the attention
// count; a request the server timed out dismisses quietly.
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart' show StreamStatus;
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/profile_monitor_fixture.dart' show monitorStore;
import 'support/stash_memory_vault.dart';

class _ReplyApi extends OpenCodeApi {
  _ReplyApi() : super(baseUrl: 'http://localhost');

  final replies = <(String, String, String)>[];
  Object? failure;
  Completer<void>? pending;

  /// What the server still lists as pending when the app asks.
  List<PermissionRequest> pendingList = [];

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    await pending?.future;
    if (failure case final error?) throw error;
    replies.add((sessionID, requestID, reply));
  }

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => pendingList;

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => pendingList;
}

class _StubRepository extends ProductRepository {
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

typedef _Call = ({String method, Map<String, dynamic>? arguments});

Future<
  ({
    ConnectionController controller,
    BackgroundLiveController live,
    _ReplyApi api,
    List<_Call> calls,
  })
>
_harness() async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
  });
  final preferences = await SharedPreferences.getInstance();
  final calls = <_Call>[];
  final live = BackgroundLiveController(
    preferences: preferences,
    invoke: (method, [arguments]) async {
      calls.add((method: method, arguments: arguments));
      if (method == 'showCodingAlert') return const {'shown': true};
      if (method == 'dismissCodingAlert') return const {'dismissed': true};
      return const {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      };
    },
  );
  await live.restore();
  final api = _ReplyApi();
  final controller =
      ConnectionController(
          ProfileStore(prefs: preferences),
          backgroundLive: live,
        )
        ..api = api
        ..repository = _StubRepository();
  controller.suspendForLifecycle();
  calls.clear();
  addTearDown(controller.dispose);
  return (controller: controller, live: live, api: api, calls: calls);
}

void _ask(ConnectionController controller, String id) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'permission.v2.asked',
      properties: {
        'id': id,
        'sessionID': 'session-1',
        'action': 'edit',
        'resources': ['lib/main.dart'],
      },
    ),
  );
}

Map<String, Object?> _tap(String id, String decision) => {
  'kind': 'permission',
  'sessionID': 'session-1',
  'decision': decision,
  'requestID': id,
};

Future<void> _flush() => Future<void>.delayed(Duration.zero);

ApiException _timedOut(String id) => ApiException(
  'Permission request not found',
  statusCode: 404,
  errorTag: 'PermissionNotFoundError',
  requestID: id,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('notification tap racing an in-app tap', () {
    test('the in-app tap lands first: the later notification Allow sends '
        'nothing and is still handled', () async {
      final h = await _harness();
      _ask(h.controller, 'permission-1');
      await _flush();

      await h.controller.answerPermission('permission-1', 'reject');
      final late = await h.live.handleNativeAction(
        _tap('permission-1', 'allow'),
      );

      expect(late, {'handled': true});
      expect(h.api.replies, [('session-1', 'permission-1', 'reject')]);
      expect(h.controller.permissions, isEmpty);
    });

    test('the notification tap lands first: the later in-app tap sends '
        'nothing and does not throw', () async {
      final h = await _harness();
      _ask(h.controller, 'permission-1');
      await _flush();

      final first = await h.live.handleNativeAction(
        _tap('permission-1', 'deny'),
      );
      await h.controller.answerPermission('permission-1', 'once');

      expect(first, {'handled': true});
      expect(h.api.replies, [('session-1', 'permission-1', 'reject')]);
    });

    test('both taps on the wire together: one reply, the first decision '
        'wins', () async {
      final h = await _harness();
      _ask(h.controller, 'permission-1');
      h.api.pending = Completer<void>();
      final notification = h.live.handleNativeAction(
        _tap('permission-1', 'deny'),
      );
      final inApp = h.controller.answerPermission('permission-1', 'once');
      final inApp2 = h.controller.answerPermission('permission-1', 'once');
      h.api.pending!.complete();
      await Future.wait([inApp, inApp2]);
      expect(await notification, {'handled': true});
      expect(h.api.replies, [('session-1', 'permission-1', 'reject')]);
    });
  });

  group('attention count after a remote withdrawal', () {
    Future<ConnectionController> boot(_ReplyApi api) async {
      final store = await monitorStore(count: 1);
      await store.setActiveId('profile-1');
      final controller = ConnectionController(
        store,
        draftAttachmentVault: StashMemoryVault(),
        stashAttachmentVault: StashMemoryVault(),
      );
      addTearDown(controller.dispose);
      controller
        ..api = api
        ..repository = _StubRepository()
        ..status = StreamStatus.connected;
      return controller;
    }

    test('a reply made on another device drops the request from the count '
        'and the waiting list', () async {
      final api = _ReplyApi();
      final controller = await boot(api);
      _ask(controller, 'permission-1');
      _ask(controller, 'permission-2');
      expect(controller.awaitingPermissions, hasLength(2));
      final before = controller.unifiedAttentionCount;
      expect(before, greaterThanOrEqualTo(2));

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.v2.replied',
          properties: const {'requestID': 'permission-1', 'reply': 'once'},
        ),
      );

      expect(controller.awaitingPermissions.map((p) => p.id), ['permission-2']);
      expect(controller.unifiedAttentionCount, before - 1);

      // A late duplicate of the same withdrawal changes nothing.
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.v2.replied',
          properties: const {'requestID': 'permission-1', 'reply': 'once'},
        ),
      );
      expect(controller.unifiedAttentionCount, before - 1);
    });

    test('a refetch that no longer lists the request clears it, and a '
        'withdrawn request is not resurrected by a stale ask', () async {
      final api = _ReplyApi();
      final controller = await boot(api);
      _ask(controller, 'permission-1');
      expect(controller.unifiedAttentionCount, greaterThanOrEqualTo(1));

      await controller.refreshPendingPermissions();

      expect(controller.permissions, isEmpty);
      expect(controller.awaitingPermissions, isEmpty);
      expect(controller.unifiedAttentionCount, 0);
    });
  });

  group('a request the server timed out', () {
    test(
      'answering it dismisses the card with no error and no throw',
      () async {
        final h = await _harness();
        _ask(h.controller, 'permission-1');
        _ask(h.controller, 'permission-2');
        h.api.failure = _timedOut('permission-1');

        await h.controller.answerPermission('permission-1', 'once');

        expect(h.controller.permissions.keys, ['permission-2']);
        expect(h.controller.lastError, isNull);
        expect(h.api.replies, isEmpty);
      },
    );

    test('a notification Allow on a timed-out request is handled, not '
        'retried by Android', () async {
      final h = await _harness();
      _ask(h.controller, 'permission-1');
      await _flush();
      h.api.failure = _timedOut('permission-1');

      final result = await h.live.handleNativeAction(
        _tap('permission-1', 'allow'),
      );

      expect(result, {'handled': true});
      expect(h.controller.permissions, isEmpty);
      expect(h.controller.lastError, isNull);
    });

    test('a different 404 is a real failure and keeps the request', () async {
      final h = await _harness();
      _ask(h.controller, 'permission-1');
      h.api.failure = ApiException('Not found', statusCode: 404);

      await expectLater(
        h.controller.answerPermission('permission-1', 'once'),
        throwsA(isA<ApiException>()),
      );
      expect(h.controller.permissions.keys, ['permission-1']);
    });
  });
}
