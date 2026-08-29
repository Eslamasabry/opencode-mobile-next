import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

Api2FormInfo _form(String id, String sessionID) => Api2FormInfo.fromJson({
  'id': id,
  'sessionID': sessionID,
  'title': 'Pick one',
  'fields': [
    {
      'key': 'env',
      'type': 'string',
      'options': [
        {'value': 'prod'},
        {'value': 'stage'},
      ],
    },
  ],
})!;

Map<String, dynamic> _formJson(String id, String sessionID) => {
  'form': {
    'id': id,
    'sessionID': sessionID,
    'title': 'Pick one',
    'fields': [
      {'key': 'env', 'type': 'string'},
    ],
  },
};

class _V2InteractionApi extends OpenCodeApi {
  _V2InteractionApi() : super(baseUrl: 'http://127.0.0.1:1');

  List<Api2FormInfo> pendingFormsResult = const [];
  List<Api2InboxItem> inboxResult = const [];
  Object? formReplyError;
  Object? inboxError;
  final formReplies = <(String, String, Map<String, dynamic>)>[];
  final formCancels = <(String, String)>[];
  final inboxCancels = <(String, String)>[];
  final inboxSteers = <(String, String)>[];
  final inboxQueues = <(String, String)>[];
  final permissionReplies =
      <({String sessionID, String requestID, String reply, String? message})>[];

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    legacyQuestionRequests: false,
    forms: true,
    inbox: true,
  );

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Api2FormInfo>> pendingForms() async => pendingFormsResult;

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) async {
    if (formReplyError case final error?) throw error;
    formReplies.add((sessionID, formID, answer));
  }

  @override
  Future<void> cancelForm(String sessionID, String formID) async {
    if (formReplyError case final error?) throw error;
    formCancels.add((sessionID, formID));
  }

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async =>
      inboxResult;

  @override
  Future<void> cancelInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxCancels.add((sessionID, inboxID));
  }

  @override
  Future<void> steerInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxSteers.add((sessionID, inboxID));
  }

  @override
  Future<void> queueInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxQueues.add((sessionID, inboxID));
  }

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    permissionReplies.add((
      sessionID: sessionID,
      requestID: requestID,
      reply: reply,
      message: message,
    ));
  }
}

class _StubRepository implements ProductRepository {
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_V2InteractionApi api) async {
  SharedPreferences.setMockInitialValues(const {});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..repository = _StubRepository();
  return controller;
}

void _enqueue(
  ConnectionController controller, {
  String sessionID = 'ses_1',
  String inboxID = 'msg_1',
  String text = 'queued prompt',
  String delivery = 'queue',
  String type = 'user',
}) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.inbox.enqueued',
      properties: {
        'sessionID': sessionID,
        'inboxID': inboxID,
        'item': {
          'type': type,
          'payload': {'text': text},
          'delivery': delivery,
        },
      },
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('form state', () {
    test('form.v2.created adds the form; replied/cancelled settle it',
        () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );
      expect(controller.forms, contains('frm_1'));
      expect(controller.formForSession('ses_1')?.id, 'frm_1');
      expect(controller.formForSession('ses_other'), isNull);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.replied',
          properties: const {'id': 'frm_1', 'sessionID': 'ses_1'},
        ),
      );
      expect(controller.forms, isEmpty);

      // A late duplicate created event for the settled form is ignored on
      // the next hydration pass (resolved IDs are filtered).
      api.pendingFormsResult = [_form('frm_1', 'ses_1')];
      await controller.refreshPendingForms();
      expect(controller.forms, isEmpty);
    });

    test('refreshPendingForms hydrates the pending list', () async {
      final api = _V2InteractionApi()
        ..pendingFormsResult = [
          _form('frm_a', 'ses_1'),
          _form('frm_global', 'global'),
        ];
      final controller = await _controller(api);
      addTearDown(controller.dispose);

      await controller.refreshPendingForms();
      expect(controller.forms.keys, containsAll(['frm_a', 'frm_global']));
      expect(controller.formsLoading, isFalse);
      expect(controller.formsError, isNull);
    });

    test('replyForm posts the answer and settles the form', () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );

      await controller.replyForm('frm_1', {'env': 'prod'});
      final reply = api.formReplies.single;
      expect(reply.$1, 'ses_1');
      expect(reply.$2, 'frm_1');
      expect(reply.$3, {'env': 'prod'});
      expect(controller.forms, isEmpty);
    });

    test('an already-settled reply resolves locally and rethrows', () async {
      final api = _V2InteractionApi()
        ..formReplyError = ApiException(
          'settled',
          statusCode: 409,
          errorTag: 'FormAlreadySettledError',
        );
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );

      await expectLater(
        controller.replyForm('frm_1', {'env': 'prod'}),
        throwsA(isA<ApiException>()),
      );
      expect(controller.forms, isEmpty);
    });

    test('a 400 invalid answer keeps the form pending and rethrows',
        () async {
      final api = _V2InteractionApi()
        ..formReplyError = ApiException(
          'Invalid option for form field: env',
          statusCode: 400,
          errorTag: 'FormInvalidAnswerError',
        );
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );

      await expectLater(
        controller.replyForm('frm_1', {'env': 'nope'}),
        throwsA(isA<ApiException>()),
      );
      expect(controller.forms, contains('frm_1'));
    });

    test('cancelForm posts the cancel and settles the form', () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );

      await controller.cancelForm('frm_1');
      expect(api.formCancels.single, ('ses_1', 'frm_1'));
      expect(controller.forms, isEmpty);
    });

    test('deleting a session drops its forms and inbox items', () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'form.v2.created',
          properties: _formJson('frm_1', 'ses_1'),
        ),
      );
      _enqueue(controller);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.deleted',
          properties: const {
            'info': {'id': 'ses_1'},
          },
        ),
      );
      expect(controller.forms, isEmpty);
      expect(controller.inboxItemsFor('ses_1'), isEmpty);
    });
  });

  group('inbox state', () {
    test('enqueued/delivery.changed/delivered project the pending sends',
        () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);

      _enqueue(controller, inboxID: 'msg_1', delivery: 'queue');
      _enqueue(controller, inboxID: 'msg_2', delivery: 'steer', text: 'next');
      final items = controller.inboxItemsFor('ses_1');
      expect(items, hasLength(2));
      expect(items.first.id, 'msg_1');
      expect(items.first.delivery, Api2Delivery.queue);
      expect(items.first.promptText, 'queued prompt');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.inbox.delivery.changed',
          properties: const {
            'sessionID': 'ses_1',
            'inboxID': 'msg_1',
            'delivery': 'steer',
          },
        ),
      );
      expect(
        controller.inboxItemsFor('ses_1').first.delivery,
        Api2Delivery.steer,
      );

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.inbox.delivered',
          properties: const {'sessionID': 'ses_1', 'inboxID': 'msg_1'},
        ),
      );
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.inbox.cancelled',
          properties: const {'sessionID': 'ses_1', 'inboxID': 'msg_2'},
        ),
      );
      expect(controller.inboxItemsFor('ses_1'), isEmpty);
    });

    test('refreshInbox reconciles a session from REST', () async {
      final api = _V2InteractionApi()
        ..inboxResult = [
          Api2InboxItem.fromJson({
            'id': 'msg_9',
            'sessionID': 'ses_1',
            'timeCreated': 5,
            'type': 'user',
            'payload': {'text': 'from rest'},
            'delivery': 'queue',
          })!,
        ];
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      _enqueue(controller, inboxID: 'msg_stale');

      await controller.refreshInbox('ses_1');
      final items = controller.inboxItemsFor('ses_1');
      expect(items.single.id, 'msg_9');
    });

    test('cancelInboxItem returns the text for the composer', () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      _enqueue(controller, inboxID: 'msg_1', text: 'bring me back');

      final text = await controller.cancelInboxItem('ses_1', 'msg_1');
      expect(text, 'bring me back');
      expect(api.inboxCancels.single, ('ses_1', 'msg_1'));
      expect(controller.inboxItemsFor('ses_1'), isEmpty);
    });

    test('a 409 already-delivered cancel drops the item and rethrows',
        () async {
      final api = _V2InteractionApi()
        ..inboxError = ApiException(
          'delivered',
          statusCode: 409,
          errorTag: 'ConflictError',
        );
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      _enqueue(controller, inboxID: 'msg_1');

      await expectLater(
        controller.cancelInboxItem('ses_1', 'msg_1'),
        throwsA(isA<ApiException>()),
      );
      expect(controller.inboxItemsFor('ses_1'), isEmpty);
    });

    test('setInboxDelivery flips the mode on the wire and locally', () async {
      final api = _V2InteractionApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      _enqueue(controller, inboxID: 'msg_1', delivery: 'queue');

      await controller.setInboxDelivery(
        'ses_1',
        'msg_1',
        delivery: Api2Delivery.steer,
      );
      expect(api.inboxSteers.single, ('ses_1', 'msg_1'));
      expect(
        controller.inboxItemsFor('ses_1').single.delivery,
        Api2Delivery.steer,
      );

      await controller.setInboxDelivery(
        'ses_1',
        'msg_1',
        delivery: Api2Delivery.queue,
      );
      expect(api.inboxQueues.single, ('ses_1', 'msg_1'));
      expect(
        controller.inboxItemsFor('ses_1').single.delivery,
        Api2Delivery.queue,
      );
    });
  });

  group('notification Reply on v2 permissions', () {
    Future<
      ({
        ConnectionController controller,
        BackgroundLiveController live,
        _V2InteractionApi api,
      })
    >
    harness() async {
      SharedPreferences.setMockInitialValues(const {
        'oc.keepLiveInBackground': true,
      });
      final prefs = await SharedPreferences.getInstance();
      final live = BackgroundLiveController(
        preferences: prefs,
        invoke: (method, [arguments]) async {
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
      final api = _V2InteractionApi();
      final controller =
          ConnectionController(ProfileStore(prefs: prefs), backgroundLive: live)
            ..api = api
            ..repository = _StubRepository();
      controller.suspendForLifecycle();
      return (controller: controller, live: live, api: api);
    }

    void askV2Permission(ConnectionController controller) {
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.v2.asked',
          properties: const {
            'id': 'per_1',
            'sessionID': 'ses_1',
            'action': 'bash',
            'resources': ['git push*'],
          },
        ),
      );
    }

    test('Reply maps to reject-with-message on the exact request', () async {
      final h = await harness();
      addTearDown(h.controller.dispose);
      askV2Permission(h.controller);

      final result = await h.live.handleNativeAction({
        'kind': 'permission',
        'sessionID': 'ses_1',
        'decision': 'reply',
        'requestID': 'per_1',
        'reply': 'use the staging remote instead',
      });
      expect(result, {'handled': true});
      expect(h.api.permissionReplies.single, (
        sessionID: 'ses_1',
        requestID: 'per_1',
        reply: 'reject',
        message: 'use the staging remote instead',
      ));
      expect(h.controller.permissions, isEmpty);
    });

    test('a stale requestID refreshes the alert instead of resolving',
        () async {
      final h = await harness();
      addTearDown(h.controller.dispose);
      askV2Permission(h.controller);

      final result = await h.live.handleNativeAction({
        'kind': 'permission',
        'sessionID': 'ses_1',
        'decision': 'reply',
        'requestID': 'per_stale',
        'reply': 'whatever',
      });
      expect(result, {'handled': true});
      expect(h.api.permissionReplies, isEmpty);
      expect(h.controller.permissions, contains('per_1'));
    });

    test('an empty reply is not delivered', () async {
      final h = await harness();
      addTearDown(h.controller.dispose);
      askV2Permission(h.controller);

      final result = await h.live.handleNativeAction({
        'kind': 'permission',
        'sessionID': 'ses_1',
        'decision': 'reply',
        'requestID': 'per_1',
        'reply': '   ',
      });
      expect(result, {'handled': false});
      expect(h.api.permissionReplies, isEmpty);
    });
  });
}
