import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart' show StreamStatus;
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/staged_revert_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

SessionRevert stage(String id, {String snapshot = 'original'}) => SessionRevert(
  messageID: id,
  snapshot: snapshot,
  files: [FileDiff(file: 'lib/main.dart', before: 'new', after: 'old')],
);

class RevertApi extends OpenCodeApi {
  RevertApi() : super(baseUrl: 'http://localhost');
  Session value = Session(id: 'a', title: 'Keep my title', cost: 7);
  Future<Session> Function()? read;
  @override
  Future<Session> session(String id) async => read == null ? value : read!();
}

class RevertController extends ConnectionController {
  RevertController(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'p', name: 'Fixture', baseUrl: 'http://localhost');
  @override
  Future<OpenCodeApi?> prepareActionTransport() async => api as OpenCodeApi?;
}

class RevertOperations extends ProductRepository
    implements StagedRevertGateway {
  final RevertApi api;
  RevertOperations(this.api);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
  final writes = <String>[];
  bool validTarget = true;
  Future<SessionRevert> Function()? staging;
  Future<void> Function()? committing;
  @override
  Future<String?> sessionRevertPrompt(String id, String messageID) async =>
      validTarget ? 'Update the settings screen' : null;
  @override
  Future<SessionRevert> stageSessionRevert(
    String id,
    String messageID, {
    required bool applyFiles,
  }) async {
    writes.add('stage:$id:$messageID:$applyFiles');
    if (staging != null) return staging!();
    final result = stage(messageID);
    api.value = api.value.copyWith(stagedRevert: result);
    return result;
  }

  @override
  Future<void> commitSessionRevert(String id) async {
    writes.add('commit:$id');
    if (committing != null) return committing!();
    api.value = api.value.copyWith(stagedRevert: null);
  }

  @override
  Future<void> clearSessionRevert(String id) async {
    writes.add('clear:$id');
    api.value = api.value.copyWith(stagedRevert: null);
  }
}

Future<({ConnectionController controller, RevertApi api, RevertOperations ops})>
setup({SessionRevert? staged}) async {
  SharedPreferences.setMockInitialValues({});
  final api = RevertApi();
  api.value = api.value.copyWith(stagedRevert: staged);
  final ops = RevertOperations(api);
  final controller =
      RevertController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = api
        ..repository = ops;
  controller.sessionsById['a'] = api.value;
  addTearDown(controller.dispose);
  return (controller: controller, api: api, ops: ops);
}

void remote(
  ConnectionController controller,
  String phase, {
  String? messageID,
}) {
  final events = Api2EventAdapter().adapt(
    Api2EventEnvelope.fromJson({
      'type': 'session.revert.$phase',
      'data': {
        'sessionID': 'a',
        if (messageID != null)
          'revert': {'messageID': messageID, 'snapshot': 'remote'},
      },
    }),
  );
  for (final event in events) {
    controller.handleEventForTesting(event);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'offline replay keeps queued prompt when a missed remote stage exists',
    () async {
      final f = await setup();
      f.controller.status = StreamStatus.connected;
      f.api.value = f.api.value.copyWith(stagedRevert: stage('msg_1'));
      await f.controller.queuePrompt(
        QueuedPrompt(
          id: 'queued',
          profileID: 'p',
          sessionID: 'a',
          text: 'Keep this draft',
          createdAt: DateTime.now().millisecondsSinceEpoch,
        ),
      );
      await f.controller.flushOfflineQueue();
      expect(f.controller.queuedPromptsFor('a').single.text, 'Keep this draft');
      expect(
        f.controller.queuedPromptsFor('a').single.error,
        contains('staged revert'),
      );
      expect(f.controller.sessionsById['a']!.stagedRevert, isNotNull);
      expect(f.ops.writes, isEmpty);
    },
  );

  test(
    'full hydration and partial updates preserve typed stage and absent preview',
    () {
      final session = mapApi2Session(
        Api2Session.fromJson({
          'id': 'a',
          'revert': {'messageID': 'msg_1', 'partID': 'part_2', 'snapshot': 's'},
        })!,
      );
      expect(session.stagedRevert!.files, isNull);
      expect(session.copyWith(title: 'rename').stagedRevert!.partID, 'part_2');
      expect(session.copyWith(stagedRevert: null).reverted, isFalse);
      expect(session.copyWith(stagedRevert: null).stagedRevert, isNull);
      expect(
        stage('msg_1').fingerprint,
        isNot(stage('msg_1', snapshot: 'changed').fingerprint),
      );
    },
  );

  test(
    'stage keeps metadata, returns preview immediately, and resets history',
    () async {
      final f = await setup();
      final events = <String>[];
      final sub = f.controller.events.listen((e) => events.add(e.type));
      addTearDown(sub.cancel);
      await f.controller.stageSessionRevert(
        f.controller.reviewSessionRevert('a'),
        'msg_1',
        applyFiles: false,
      );
      await Future<void>.delayed(Duration.zero);
      expect(f.ops.writes, ['stage:a:msg_1:false']);
      final saved = f.controller.sessionsById['a']!;
      expect(saved.title, 'Keep my title');
      expect(saved.cost, 7);
      expect(saved.stagedRevert!.files!.single.file, 'lib/main.dart');
      expect(events, contains('session.history.reset'));
      expect(f.controller.sessionRevertSaving('a'), isFalse);
    },
  );

  test(
    'remote stage invalidates an already reviewed commit without clobbering metadata',
    () async {
      final f = await setup(staged: stage('msg_1'));
      final reviewed = f.controller.reviewSessionRevert('a');
      remote(f.controller, 'staged', messageID: 'msg_2');
      await expectLater(
        f.controller.commitSessionRevert(reviewed),
        throwsA(isA<ProductException>()),
      );
      expect(f.ops.writes, isEmpty);
      expect(f.controller.sessionsById['a']!.title, 'Keep my title');
      expect(f.controller.sessionsById['a']!.stagedRevert!.messageID, 'msg_2');
    },
  );

  test(
    'fresh server boundary blocks commit even when its event was missed',
    () async {
      final f = await setup(staged: stage('msg_1'));
      final reviewed = f.controller.reviewSessionRevert('a');
      f.api.value = f.api.value.copyWith(stagedRevert: stage('msg_2'));
      await expectLater(
        f.controller.commitSessionRevert(reviewed),
        throwsA(isA<ProductException>()),
      );
      expect(f.ops.writes, isEmpty);
      expect(f.controller.sessionsById['a']!.stagedRevert!.messageID, 'msg_2');
    },
  );

  test(
    'connection replacement during preflight cannot dispatch to either server',
    () async {
      final f = await setup(staged: stage('msg_1'));
      final read = Completer<Session>();
      f.api.read = () => read.future;
      final pending = f.controller.commitSessionRevert(
        f.controller.reviewSessionRevert('a'),
      );
      final assertion = expectLater(pending, throwsA(isA<ProductException>()));
      f.controller.repository = RevertOperations(f.api);
      read.complete(f.api.value);
      await assertion;
      expect(f.ops.writes, isEmpty);
    },
  );

  test(
    'rejects concurrent mutations and never overwrites a newer remote stage with HTTP',
    () async {
      final f = await setup();
      final response = Completer<SessionRevert>();
      f.ops.staging = () => response.future;
      final reviewed = f.controller.reviewSessionRevert('a');
      final pending = f.controller.stageSessionRevert(
        reviewed,
        'msg_1',
        applyFiles: true,
      );
      await Future<void>.delayed(Duration.zero);
      await expectLater(
        f.controller.stageSessionRevert(reviewed, 'msg_2', applyFiles: true),
        throwsA(isA<ProductException>()),
      );
      remote(f.controller, 'staged', messageID: 'msg_3');
      response.complete(stage('msg_1'));
      await pending;
      expect(f.controller.sessionsById['a']!.stagedRevert!.messageID, 'msg_3');
      expect(f.ops.writes, hasLength(1));
    },
  );

  test(
    'ambiguous commit failure reconciles before retry becomes available',
    () async {
      final f = await setup(staged: stage('msg_1'));
      f.ops.committing = () async {
        f.api.value = f.api.value.copyWith(stagedRevert: null);
        throw TimeoutException('receipt lost');
      };
      await expectLater(
        f.controller.commitSessionRevert(f.controller.reviewSessionRevert('a')),
        throwsA(isA<TimeoutException>()),
      );
      expect(f.controller.sessionsById['a']!.reverted, isFalse);
      expect(f.controller.sessionRevertSaving('a'), isFalse);
      expect(f.controller.sessionRevertErrors['a'], contains('receipt lost'));
    },
  );

  test(
    'clear and commit remove stage and invalidate structural history',
    () async {
      for (final commit in [false, true]) {
        final f = await setup(staged: stage('msg_1'));
        final review = f.controller.reviewSessionRevert('a');
        if (commit) {
          await f.controller.commitSessionRevert(review);
        } else {
          await f.controller.clearSessionRevert(review);
        }
        expect(f.controller.sessionsById['a']!.stagedRevert, isNull);
        expect(
          f.controller.sessionHistoryRevision('a'),
          greaterThan(review.revision),
        );
        expect(f.ops.writes, [commit ? 'commit:a' : 'clear:a']);
      }
    },
  );

  test('unsaved prompts and busy sessions cannot stage', () async {
    final f = await setup();
    f.ops.validTarget = false;
    await expectLater(
      f.controller.stageSessionRevert(
        f.controller.reviewSessionRevert('a'),
        'inbox_1',
        applyFiles: true,
      ),
      throwsA(isA<ProductException>()),
    );
    f.ops.validTarget = true;
    f.controller.busySessions.add('a');
    await expectLater(
      f.controller.stageSessionRevert(
        f.controller.reviewSessionRevert('a'),
        'msg_1',
        applyFiles: true,
      ),
      throwsA(isA<ProductException>()),
    );
    expect(f.ops.writes, isEmpty);
  });

  testWidgets('remote change disables an open permanent-revert confirmation', (
    tester,
  ) async {
    final f = await setup(staged: stage('msg_1'));
    await tester.pumpWidget(
      MaterialApp(
        home: StagedRevertScreen(controller: f.controller, sessionID: 'a'),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('commit-staged-revert')));
    await tester.pumpAndSettle();
    remote(f.controller, 'staged', messageID: 'msg_2');
    await tester.pump();
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('confirm-staged-revert')),
          )
          .onPressed,
      isNull,
    );
    expect(f.ops.writes, isEmpty);
  });

  testWidgets('missing preview and large text remain usable at compact width', (
    tester,
  ) async {
    final f = await setup(staged: SessionRevert(messageID: 'msg_1'));
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: StagedRevertScreen(controller: f.controller, sessionID: 'a'),
      ),
    );
    await tester.scrollUntilVisible(
      find.textContaining('did not provide a file preview'),
      200,
    );
    expect(
      find.textContaining('did not provide a file preview'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clear-staged-revert')),
      200,
    );
    await tester.tap(find.byKey(const ValueKey('clear-staged-revert')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('confirm-staged-revert')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
