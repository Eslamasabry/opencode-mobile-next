import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
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

  /// The history the review page counts; empty leaves the count unknown.
  List<MessageWithParts> history = const [];
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? history : const []);
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

  // --- Pages (screen-review-2): stage-revert-sheet, staged-revert and
  // staged-revert-confirm-sheet, rebuilt from kit parts.

  testWidgets(
    'a remote change closes an open "keep the undo" question and says so',
    (tester) async {
      final f = await setup(staged: stage('msg_1'));
      await tester.pumpWidget(
        app(StagedRevertScreen(controller: f.controller, sessionID: 'a')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('commit-staged-revert')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('staged-revert-confirm-sheet')),
        findsOneWidget,
      );
      remote(f.controller, 'staged', messageID: 'msg_2');
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('staged-revert-confirm-sheet')),
        findsNothing,
      );
      expect(find.text('The undo changed'), findsOneWidget);
      expect(find.text('Review latest state'), findsOneWidget);
      expect(f.ops.writes, isEmpty);
    },
  );

  testWidgets('the page quotes the prompt, lists the files and pins its '
      'one decision: Put everything back, or delete the hidden messages', (
    tester,
  ) async {
    final f = await setup(staged: stage('msg_1'));
    await tester.pumpWidget(
      app(StagedRevertScreen(controller: f.controller, sessionID: 'a')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Review the undo'), findsOneWidget);
    expect(find.text('Update the settings screen'), findsOneWidget);
    // The raw message id is gone (owner verdict: drop the id).
    expect(find.text('msg_1'), findsNothing);
    expect(find.text('main.dart'), findsOneWidget);
    // No "Choose what happens" row group: the two outcomes are buttons in
    // the page's bottom block, the destructive one named for what it
    // deletes.
    expect(find.text('Choose what happens'), findsNothing);
    expect(find.text('Keep the undo'), findsNothing);
    expect(
      find.descendant(
        of: find.byType(KitActionBlock),
        matching: find.text('Put everything back'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byType(KitActionBlock),
        matching: find.text('Delete the hidden messages'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('keeping the undo asks first, then says it is done', (
    tester,
  ) async {
    final f = await setup(staged: stage('msg_1'));
    await tester.pumpWidget(
      app(StagedRevertScreen(controller: f.controller, sessionID: 'a')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('commit-staged-revert')));
    await tester.pumpAndSettle();
    expect(find.text('Delete messages in “Keep my title”?'), findsOneWidget);
    expect(find.text("This can't be undone."), findsOneWidget);
    expect(
      find.text('The hidden prompt and every message after it are deleted'),
      findsOneWidget,
    );
    expect(find.text('Files stay as they are now'), findsOneWidget);
    expect(find.text('Delete hidden messages'), findsOneWidget);
    expect(f.ops.writes, isEmpty);
    await tester.tap(find.byKey(const ValueKey('confirm-staged-revert')));
    await tester.pumpAndSettle();
    expect(f.ops.writes, ['commit:a']);
    expect(find.text('Undo kept'), findsOneWidget);
    expect(find.text('Back to the conversation'), findsOneWidget);
  });

  testWidgets('putting everything back names the files it replaces', (
    tester,
  ) async {
    final f = await setup(staged: stage('msg_1'));
    await tester.pumpWidget(
      app(StagedRevertScreen(controller: f.controller, sessionID: 'a')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('clear-staged-revert')));
    await tester.pumpAndSettle();
    expect(find.text('Restore “Keep my title”?'), findsOneWidget);
    expect(
      find.text('1 file is replaced, with any edits made since'),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('confirm-staged-revert')));
    await tester.pumpAndSettle();
    expect(f.ops.writes, ['clear:a']);
    expect(find.text('Everything is back'), findsOneWidget);
  });

  testWidgets('a failed "keep the undo" keeps the question open', (
    tester,
  ) async {
    final f = await setup(staged: stage('msg_1'));
    f.ops.committing = () async => throw StateError('server said no');
    await tester.pumpWidget(
      app(StagedRevertScreen(controller: f.controller, sessionID: 'a')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('commit-staged-revert')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('confirm-staged-revert')));
    await tester.pumpAndSettle();
    expect(f.ops.writes, ['commit:a']);
    expect(find.text('Undo kept'), findsNothing);
    // The question stays open (never a silent close on an error).
    expect(
      find.byKey(const ValueKey('staged-revert-confirm-sheet')),
      findsOneWidget,
    );
    final cancel = find.byKey(const ValueKey('kit-confirm-cancel'));
    await tester.ensureVisible(cancel);
    await tester.pumpAndSettle();
    await tester.tap(cancel);
    await tester.pumpAndSettle();
    // Back on the page, it says the act did not finish.
    expect(
      find.text('That didn\'t finish. Check the conversation, then try again.'),
      findsOneWidget,
    );
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
      app(
        StagedRevertScreen(controller: f.controller, sessionID: 'a'),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.textContaining('did not provide a file preview'),
      200,
    );
    expect(
      find.textContaining('did not provide a file preview'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    // The decision is pinned at the bottom, so it is reachable unscrolled.
    await tester.tap(find.byKey(const ValueKey('clear-staged-revert')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('confirm-staged-revert')), findsOneWidget);
    // No file list: the question cannot count the files it replaces.
    expect(
      find.text('Files in this undo are replaced, with any edits made since'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('the undo sheet keeps the files choice and stays open when '
      'setting up the undo fails', (tester) async {
    final f = await setup();
    bool? result;
    final calls = <bool>[];
    var fail = true;
    await tester.pumpWidget(
      app(
        Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () async {
                result = await showStageRevertSheet(
                  context,
                  controller: f.controller,
                  review: f.controller.reviewSessionRevert('a'),
                  prompt: 'Update the settings screen',
                  stage: (applyFiles) async {
                    calls.add(applyFiles);
                    if (fail) throw StateError('boom');
                  },
                );
              },
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.text('Undo from this prompt?'), findsOneWidget);
    expect(find.text('From this prompt'), findsOneWidget);
    expect(find.text('Update the settings screen'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('stage-revert-files')));
    await tester.pump();
    await tester.tap(find.text('Undo and review'));
    await tester.pumpAndSettle();
    expect(calls, [false]);
    expect(
      find.text("Couldn't set up the undo. Nothing was hidden."),
      findsOneWidget,
    );
    expect(result, isNull);
    expect(find.text('Undo from this prompt?'), findsOneWidget);
    fail = false;
    await tester.tap(find.text('Undo and review'));
    await tester.pumpAndSettle();
    expect(calls, [false, false]);
    expect(result, isFalse);
    expect(find.text('Undo from this prompt?'), findsNothing);
  });

  testWidgets('the undo sheet turns its primary off and says why while the '
      'conversation is busy', (tester) async {
    final f = await setup();
    f.controller.busySessions.add('a');
    bool? result;
    var closed = false;
    await tester.pumpWidget(
      app(
        Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () async {
                result = await showStageRevertSheet(
                  context,
                  controller: f.controller,
                  review: f.controller.reviewSessionRevert('a'),
                  prompt: 'Update the settings screen',
                );
                closed = true;
              },
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(
      find.text('Wait for the current conversation action to finish.'),
      findsWidgets,
    );
    await tester.tap(find.text('Undo and review'), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(closed, isFalse);
    expect(result, isNull);
  });
}

Widget app(Widget home, {double textScale = 1}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: home,
);
