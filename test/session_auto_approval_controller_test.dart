import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Records every permission reply on both protocol paths. [hold] keeps a
/// reply on the wire so the in-flight state can be observed; [fail] makes
/// the server refuse it.
class _FakeApi extends OpenCodeApi {
  _FakeApi() : super(baseUrl: 'http://localhost');

  final legacyReplies = <({String requestID, String reply, String? session})>[];
  final v2Replies = <({String sessionID, String requestID, String reply})>[];
  Completer<void>? hold;
  Object? fail;

  /// What the server reports as waiting when the app (re)reads it.
  final waiting = <PermissionRequest>[];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [...waiting];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  Future<void> _deliver() async {
    await hold?.future;
    if (fail case final error?) throw error;
  }

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    await _deliver();
    legacyReplies.add((
      requestID: requestID,
      reply: reply,
      session: legacySessionID,
    ));
  }

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    await _deliver();
    v2Replies.add((sessionID: sessionID, requestID: requestID, reply: reply));
  }

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async => throw StateError('questions must never be answered automatically');
}

class _ApprovalStore extends ProfileStore {
  _ApprovalStore({required super.prefs});
  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost'),
  ];
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');
}

Future<(_Controller, _FakeApi)> _boot({
  bool connected = true,
  bool allow = true,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  if (allow) {
    await AutomationPolicyController.forProfile(
      prefs,
      'server-a',
    ).setSupervision(AutomationSupervision.balanced);
  }
  final api = _FakeApi();
  final controller = _Controller(_ApprovalStore(prefs: prefs))
    ..api = api
    ..status = connected ? StreamStatus.connected : StreamStatus.reconnecting;
  addTearDown(controller.dispose);
  controller.sessionsById['parent'] = Session(id: 'parent', title: 'Parent');
  controller.sessionsById['child'] = Session(
    id: 'child',
    title: 'Child',
    parentID: 'parent',
  );
  controller.sessionsById['grandchild'] = Session(
    id: 'grandchild',
    title: 'Grandchild',
    parentID: 'child',
  );
  controller.sessionsById['other'] = Session(id: 'other', title: 'Other');
  return (controller, api);
}

EventEnvelope _v1Ask(String id, String session, {String action = 'bash'}) =>
    EventEnvelope(
      type: 'permission.asked',
      properties: {
        'id': id,
        'sessionID': session,
        'permission': action,
        'patterns': ['git status'],
        'metadata': <String, Object?>{},
        'always': ['git *'],
      },
    );

EventEnvelope _v2Ask(String id, String session) => EventEnvelope(
  type: 'permission.v2.asked',
  properties: {
    'id': id,
    'sessionID': session,
    'action': 'edit',
    'resources': ['lib/main.dart'],
    'save': ['lib/**'],
  },
);

EventEnvelope _legacyAsk(String id, String session) => EventEnvelope(
  type: 'permission.updated',
  properties: {
    'id': id,
    'sessionID': session,
    'type': 'bash',
    'pattern': 'rm -rf build',
    'messageID': 'msg-1',
    'callID': 'call-1',
  },
);

EventEnvelope _question(String id, String session) => EventEnvelope(
  type: 'question.v2.asked',
  properties: {
    'id': id,
    'sessionID': session,
    'questions': [
      {
        'question': 'Continue?',
        'header': 'Plan',
        'options': [
          {'label': 'Yes', 'description': ''},
        ],
      },
    ],
  },
);

const _auto = SessionAutoApproval(mode: AutoApprovalMode.autoOnce);
const _autoInherit = SessionAutoApproval(
  mode: AutoApprovalMode.autoOnce,
  inheritToChildren: true,
);

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('new sessions ask: nothing is answered without a setting', () async {
    final (controller, api) = await _boot();
    controller.handleEventForTesting(_v1Ask('req-1', 'parent'));
    await _settle();
    expect(api.legacyReplies, isEmpty);
    expect(controller.permissionsForSession('parent'), hasLength(1));
    expect(controller.awaitingPermissionCount, 1);
  });

  test('OpenCode 1 request is answered once and recorded', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);
    api.hold = Completer<void>();

    controller.handleEventForTesting(_v1Ask('req-1', 'parent'));
    await _settle();

    // In flight: the request is not offered to a person anywhere.
    expect(controller.isAutoApproving('req-1'), isTrue);
    expect(controller.permissions, contains('req-1'));
    expect(controller.permissionsForSession('parent'), isEmpty);
    expect(controller.permissionForSession('parent'), isNull);
    expect(controller.awaitingPermissionCount, 0);
    expect(controller.unifiedAttentionCount, 0);
    expect(controller.liveStatus().pendingCount, 0);

    expect(controller.automaticActsHere, isEmpty);
    api.hold!.complete();
    await _settle();
    await _settle();

    expect(
      controller.automaticActsHere.single.kind,
      AutomaticActKind.permissionApproval,
    );
    expect(api.legacyReplies, [
      (requestID: 'req-1', reply: 'once', session: null),
    ]);
    expect(api.v2Replies, isEmpty);
    expect(controller.isAutoApproving('req-1'), isFalse);
    expect(controller.permissions, isNot(contains('req-1')));
    final approved = controller.autoApprovedFor('parent');
    expect(approved, hasLength(1));
    expect(approved.single.permission, 'bash');
    expect(approved.single.patterns, ['git status']);
    expect(controller.autoApprovalFailure('req-1'), isNull);
  });

  test(
    'High AI Team supervision still answers a conversation set to automatic',
    () async {
      // The person chose automatic for this conversation: that is the
      // consent; the team's supervision level does not apply here.
      final (controller, api) = await _boot(allow: false);
      await controller.setSessionAutoApproval('parent', _auto);
      controller.handleEventForTesting(_v1Ask('blocked', 'parent'));
      await _settle();
      await _settle();
      expect(api.legacyReplies, hasLength(1));
      expect(controller.permissionsForSession('parent'), isEmpty);
    },
  );

  test('OpenCode 2 request replies on the v2 contract, never always', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);

    controller.handleEventForTesting(_v2Ask('perm-1', 'parent'));
    await _settle();
    await _settle();

    expect(api.v2Replies, [
      (sessionID: 'parent', requestID: 'perm-1', reply: 'once'),
    ]);
    expect(api.legacyReplies, isEmpty);
    expect(controller.autoApprovedFor('parent').single.permission, 'edit');
    expect(controller.permissions, isEmpty);
  });

  test('legacy permission.updated shape is answered on its own ids', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);

    controller.handleEventForTesting(_legacyAsk('legacy-1', 'parent'));
    await _settle();
    await _settle();

    expect(api.legacyReplies, [
      (requestID: 'legacy-1', reply: 'once', session: 'parent'),
    ]);
    expect(controller.autoApprovedFor('parent').single.patterns, [
      'rm -rf build',
    ]);
  });

  test('a session that does not inherit keeps its subagents asking', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);

    controller.handleEventForTesting(_v1Ask('req-child', 'child'));
    await _settle();

    expect(api.legacyReplies, isEmpty);
    expect(controller.permissionsForSession('child'), hasLength(1));
  });

  test('subagents inherit through child and grandchild', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _autoInherit);

    controller.handleEventForTesting(_v1Ask('req-child', 'child'));
    controller.handleEventForTesting(_v2Ask('req-grandchild', 'grandchild'));
    controller.handleEventForTesting(_v1Ask('req-other', 'other'));
    await _settle();
    await _settle();

    expect(api.legacyReplies.map((r) => r.requestID), ['req-child']);
    expect(api.v2Replies.map((r) => r.requestID), ['req-grandchild']);
    expect(controller.autoApprovalFor('child').inheritedFrom, 'parent');
    expect(controller.autoApprovalFor('grandchild').inheritedFrom, 'parent');
    // An unrelated session still asks.
    expect(controller.permissionsForSession('other'), hasLength(1));
    expect(controller.autoApprovedFor('child'), hasLength(1));
    expect(controller.autoApprovedFor('grandchild'), hasLength(1));
  });

  test('an explicit child override stops inherited approval', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _autoInherit);
    await controller.setSessionAutoApproval('child', SessionAutoApproval.ask);

    controller.handleEventForTesting(_v1Ask('req-child', 'child'));
    controller.handleEventForTesting(_v1Ask('req-grandchild', 'grandchild'));
    await _settle();

    expect(api.legacyReplies, isEmpty);
    expect(controller.permissionsForSession('child'), hasLength(1));
    expect(controller.permissionsForSession('grandchild'), hasLength(1));

    // Following the parent again re-enables it: the request that waited
    // while asking is answered now, and new ones as they arrive.
    await controller.setSessionAutoApproval('child', null);
    controller.handleEventForTesting(_v1Ask('req-child-2', 'child'));
    await _settle();
    await _settle();
    expect(api.legacyReplies.map((r) => r.requestID).toSet(), {
      'req-child',
      'req-child-2',
      'req-grandchild',
    });
    expect(controller.permissionsForSession('child'), isEmpty);
  });

  test('not connected: the request stays pending and visible', () async {
    final (controller, api) = await _boot(connected: false);
    await controller.setSessionAutoApproval('parent', _autoInherit);

    controller.handleEventForTesting(_v1Ask('req-1', 'parent'));
    controller.handleEventForTesting(_v2Ask('perm-2', 'child'));
    await _settle();
    await _settle();

    expect(api.legacyReplies, isEmpty);
    expect(api.v2Replies, isEmpty);
    expect(controller.isAutoApproving('req-1'), isFalse);
    expect(controller.permissionsForSession('parent'), hasLength(1));
    expect(controller.permissionsForSession('child'), hasLength(1));
    expect(controller.awaitingPermissionCount, 2);
    expect(controller.autoApprovedFor('parent'), isEmpty);
  });

  test(
    'a refused reply leaves the request pending, visible, explained',
    () async {
      final (controller, api) = await _boot();
      await controller.setSessionAutoApproval('parent', _auto);
      api.fail = ApiException('server refused the reply');
      var notified = 0;
      controller.addListener(() => notified++);

      controller.handleEventForTesting(_v2Ask('perm-1', 'parent'));
      await _settle();
      await _settle();

      expect(api.v2Replies, isEmpty);
      expect(controller.isAutoApproving('perm-1'), isFalse);
      expect(controller.permissions, contains('perm-1'));
      expect(controller.permissionsForSession('parent').single.id, 'perm-1');
      expect(controller.awaitingPermissionCount, 1);
      expect(controller.unifiedAttentionCount, 1);
      expect(
        controller.autoApprovalFailure('perm-1'),
        'Could not confirm automatic approval. Review the request.',
      );
      expect(controller.automaticActsHere, isEmpty);
      expect(controller.autoApprovedFor('parent'), isEmpty);
      expect(notified, greaterThan(0));

      // A person can still answer it by hand; the failure note clears.
      api.fail = null;
      await controller.answerPermission('perm-1', 'reject');
      expect(api.v2Replies.single.reply, 'reject');
      expect(controller.autoApprovalFailure('perm-1'), isNull);
      expect(controller.permissions, isEmpty);
    },
  );

  test('an already resolved request is not logged as our approval', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);
    api.fail = ApiException(
      'gone',
      statusCode: 404,
      errorTag: 'PermissionNotFoundError',
      requestID: 'gone',
    );
    controller.handleEventForTesting(_v1Ask('gone', 'parent'));
    await _settle();
    await _settle();
    expect(controller.permissions, isEmpty);
    expect(controller.autoApprovedFor('parent'), isEmpty);
    expect(controller.automaticActsHere, isEmpty);
  });

  test('questions are never answered automatically', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _autoInherit);

    controller.handleEventForTesting(_question('q-1', 'parent'));
    controller.handleEventForTesting(_question('q-2', 'child'));
    await _settle();
    await _settle();

    expect(controller.questions.keys, containsAll(['q-1', 'q-2']));
    expect(api.legacyReplies, isEmpty);
    expect(api.v2Replies, isEmpty);
  });

  test(
    'turning approval off mid-flight does not recall a sent reply, and later requests ask',
    () async {
      final (controller, api) = await _boot();
      await controller.setSessionAutoApproval('parent', _auto);
      api.hold = Completer<void>();
      controller.handleEventForTesting(_v1Ask('req-1', 'parent'));
      await _settle();

      await controller.setSessionAutoApproval(
        'parent',
        SessionAutoApproval.ask,
      );
      controller.handleEventForTesting(_v1Ask('req-2', 'parent'));
      api.hold!.complete();
      await _settle();
      await _settle();

      expect(api.legacyReplies.map((r) => r.requestID), ['req-1']);
      expect(controller.permissionsForSession('parent').map((p) => p.id), [
        'req-2',
      ]);
    },
  );

  test('the record is bounded and cleared with the connection', () async {
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval('parent', _auto);
    for (var i = 0; i < 25; i++) {
      controller.handleEventForTesting(_v1Ask('req-$i', 'parent'));
    }
    await _settle();
    await _settle();
    expect(api.legacyReplies, hasLength(25));
    expect(api.legacyReplies.every((r) => r.reply == 'once'), isTrue);
    final approved = controller.autoApprovedFor('parent');
    expect(approved, hasLength(20));
    expect(approved.last.requestID, 'req-24');

    await controller.disconnect();
    expect(controller.autoApprovedFor('parent'), isEmpty);
    // The stored setting outlives the connection; a reconnect resumes it.
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
  });

  group('an automatic mode answers what is already waiting', () {
    test(
      'turning a conversation to auto answers its waiting request',
      () async {
        final (controller, api) = await _boot();
        controller.handleEventForTesting(_v1Ask('wait-1', 'parent'));
        controller.handleEventForTesting(_v1Ask('wait-2', 'other'));
        await _settle();
        expect(controller.permissionsForSession('parent'), hasLength(1));

        await controller.setSessionAutoApproval('parent', _auto);
        await _settle();
        await _settle();

        expect(api.legacyReplies.map((r) => r.requestID), ['wait-1']);
        expect(controller.permissionsForSession('parent'), isEmpty);
        // Another conversation is not affected.
        expect(controller.permissionsForSession('other'), hasLength(1));
      },
    );

    test(
      'approve everything answers waiting requests on every session',
      () async {
        final (controller, api) = await _boot();
        controller.handleEventForTesting(_v1Ask('wait-1', 'parent'));
        controller.handleEventForTesting(_v1Ask('wait-2', 'other'));
        await _settle();

        await controller.setApprovesEverything(true);
        await _settle();
        await _settle();

        expect(api.legacyReplies.map((r) => r.requestID).toSet(), {
          'wait-1',
          'wait-2',
        });
        expect(controller.permissions, isEmpty);
      },
    );

    test('a conversation set to ask keeps its waiting request', () async {
      final (controller, api) = await _boot();
      await controller.setSessionAutoApproval(
        'parent',
        const SessionAutoApproval(),
      );
      controller.handleEventForTesting(_v1Ask('wait-1', 'parent'));
      await controller.setApprovesEverything(true);
      await _settle();
      expect(api.legacyReplies, isEmpty);
      expect(controller.permissionsForSession('parent'), hasLength(1));
    });

    test('requests found after a reconnect are answered too', () async {
      final (controller, api) = await _boot();
      await controller.setSessionAutoApproval('parent', _auto);
      api.waiting.add(
        PermissionRequest(
          id: 'found-1',
          sessionID: 'parent',
          permission: 'external_directory',
          patterns: const ['/root/x'],
          metadata: const {},
        ),
      );
      await controller.refreshPendingPermissions();
      await _settle();
      await _settle();

      expect(api.legacyReplies.map((r) => r.requestID), ['found-1']);
      expect(controller.permissionsForSession('parent'), isEmpty);
    });

    test('with High AI Team supervision, switching on still answers', () async {
      final (controller, api) = await _boot(allow: false);
      controller.handleEventForTesting(_v1Ask('wait-1', 'parent'));
      await controller.setSessionAutoApproval('parent', _auto);
      await _settle();
      await _settle();
      expect(api.legacyReplies, hasLength(1));
      expect(controller.permissionsForSession('parent'), isEmpty);
    });
  });
}
