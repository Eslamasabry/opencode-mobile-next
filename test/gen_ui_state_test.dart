import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/delayed_answers.dart';
import 'package:opencode_mobile/state/gen_ui_journal.dart';
import 'package:opencode_mobile/state/gen_ui_state.dart';

const scope = GenUiScope(
  profileID: 'phone',
  sourceId: 'opencode',
  directory: '/work',
);

MessageWithParts tool({
  String id = 'card',
  String session = 's',
  Map<String, Object>? ask,
}) => MessageWithParts(
  info: MessageInfo(id: 'm', sessionID: session, role: 'assistant'),
  parts: [
    Part(
      id: 'p',
      type: 'tool',
      messageID: 'm',
      callID: 'c',
      toolName: 'oc-ui_show',
      toolState: ToolState(
        status: 'completed',
        input: {
          'v': 1,
          'id': id,
          'title': 'Continue?',
          'body': [],
          'ask': ask ?? {'kind': 'confirm'},
        },
      ),
    ),
  ],
);

final class Gateway
    implements ServerGateway, GenUiHistoryGateway, CorrelatedPromptGateway {
  List<MessageWithParts> history = [tool()];
  int sent = 0, reads = 0;
  bool idle = true, fail = false, hasMore = false;
  Completer<void>? pause;
  Completer<void>? beforeWire;
  Completer<void>? resumeWire;
  @override
  final capabilities = const ServerCapabilities(
    genUi: true,
    promptAttachments: true,
  );
  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async {
    reads++;
    final snapshot = history;
    await pause?.future;
    return GenUiHistoryPage(
      items: snapshot,
      hasMore: hasMore,
      olderCursor: hasMore ? 'older' : null,
    );
  }

  @override
  Future<bool> genUiSessionIdle(String sessionID) async => idle;
  @override
  String createPromptMessageID() => 'answer-${sent + 1}';
  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeWire?.complete();
    await resumeWire?.future;
    beforeSend?.call();
    sent++;
    if (fail) throw StateError('test uncertain transport');
    history = [
      ...history,
      MessageWithParts(
        info: MessageInfo(id: messageID, sessionID: sessionID, role: 'user'),
        parts: [Part(type: 'text', text: text)],
      ),
    ];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late DelayedAnswers delayed;
  late GenUiStateController state;
  late Gateway gateway;
  void register({bool ready = true, GenUiScope target = scope}) {
    state.register(
      target,
      gateway,
      endpoint: 'endpoint-digest',
      current: () => true,
      ready: ready,
    );
    state.observe(target, 's', gateway.history, tailComplete: true);
  }

  GenUiCard card() => state.waiting(scope, 's').single;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    delayed = DelayedAnswers(window: const Duration(hours: 1));
    state = GenUiStateController(prefs, delayedAnswers: delayed);
    gateway = Gateway();
    register();
  });
  tearDown(() async {
    state.dispose();
    delayed.dispose();
    await state.journal.drained;
  });

  test('qualified authoritative parts only; source and revision isolation', () {
    final original = card();
    expect(
      state.cardForPart(scope, 's', 'm', gateway.history.first.parts.first),
      isA<GenUiParsed>(),
    );
    const other = GenUiScope(
      profileID: 'other',
      sourceId: 'opencode',
      directory: '/work',
    );
    expect(state.waiting(other, 's'), isEmpty);
    state.observe(scope, 's', [tool(id: 'changed')], tailComplete: true);
    expect(state.state(original), GenUiCardState.unknown);
    register(ready: false);
    expect(state.waiting(scope, 's'), isEmpty);
  });

  test('unknown session deltas do not notify card listeners', () async {
    await state.journal.drained;
    var notifications = 0;
    state.addListener(() => notifications++);
    state.stale(scope, 'unseen');
    state.stale(scope, 'another-unseen');
    expect(notifications, 0);
  });

  test('repeated stale deltas notify only on coverage transition', () async {
    await state.journal.drained;
    var notifications = 0;
    state.addListener(() => notifications++);
    state.stale(scope, 's');
    expect(notifications, 1);
    state.stale(scope, 's');
    expect(notifications, 1);
  });

  test(
    'profile close before wire clears only the unsent dispatch marker',
    () async {
      final current = card();
      gateway.beforeWire = Completer<void>();
      gateway.resumeWire = Completer<void>();
      final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
      final checked = expectLater(answer, throwsA(isA<ProductException>()));
      final flushing = delayed.flush();
      await gateway.beforeWire!.future;
      expect(state.journal.read('phone').single.dispatchID, isNotNull);
      await state.closeProfile('phone');
      gateway.resumeWire!.complete();
      await flushing;
      await checked;
      expect(gateway.sent, 0);
      state.reopenProfile('phone');
      register();
      await state.journal.drained;
      expect(state.delivery(current), GenUiDeliveryState.idle);
      expect(
        state.journal.read('phone').where((entry) => entry.dispatchID != null),
        isEmpty,
      );
    },
  );

  test(
    'unsent cleanup preserves other uncertain dispatches after close',
    () async {
      final current = card();
      gateway.beforeWire = Completer<void>();
      gateway.resumeWire = Completer<void>();
      final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
      final checked = expectLater(answer, throwsA(isA<ProductException>()));
      final flushing = delayed.flush();
      await gateway.beforeWire!.future;
      await state.journal.put(
        GenUiReference(
          scope: scope,
          sessionID: 'uncertain-session',
          messageID: 'other-message',
          callID: 'other-call',
          revision: current.revision,
          endpoint: 'endpoint-digest',
          dispatchID: 'uncertain-token',
        ),
      );
      await state.closeProfile('phone');
      gateway.resumeWire!.complete();
      await flushing;
      await checked;
      expect(gateway.sent, 0);
      expect(state.journal.read('phone').single.dispatchID, 'uncertain-token');
    },
  );

  test('unsent cleanup cannot resurrect a deleted profile journal', () async {
    gateway.beforeWire = Completer<void>();
    gateway.resumeWire = Completer<void>();
    final answer = state.answer(card(), const GenUiConfirmAnswer(true), []);
    final checked = expectLater(answer, throwsA(isA<ProductException>()));
    final flushing = delayed.flush();
    await gateway.beforeWire!.future;
    await state.closeProfile('phone');
    await prefs.remove(GenUiJournal.key('phone'));
    gateway.resumeWire!.complete();
    await flushing;
    await checked;
    expect(gateway.sent, 0);
    expect(prefs.containsKey(GenUiJournal.key('phone')), isFalse);
  });

  test('unsent cleanup preserves a replacement dispatch token', () async {
    final current = card();
    gateway.beforeWire = Completer<void>();
    gateway.resumeWire = Completer<void>();
    final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
    final checked = expectLater(answer, throwsA(isA<ProductException>()));
    final flushing = delayed.flush();
    await gateway.beforeWire!.future;
    await state.closeProfile('phone');
    await GenUiJournal(prefs).put(
      GenUiReference.card(
        current,
        'endpoint-digest',
        dispatchID: 'replacement-token',
      ),
    );
    gateway.resumeWire!.complete();
    await flushing;
    await checked;
    expect(gateway.sent, 0);
    expect(state.journal.read('phone').single.dispatchID, 'replacement-token');
  });

  test('Undo drops held answer from both surfaces', () async {
    final current = card();
    final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
    expect(state.delivery(current), GenUiDeliveryState.held);
    state.undo(current);
    await answer;
    await delayed.flush();
    expect(gateway.sent, 0);
    expect(state.delivery(current), GenUiDeliveryState.idle);
  });

  test(
    'duplicate taps send once and only authoritative echo produces receipt',
    () async {
      final current = card();
      final first = state.answer(current, const GenUiConfirmAnswer(true), []);
      final second = state.answer(current, const GenUiConfirmAnswer(true), []);
      expect(identical(first, second), isTrue);
      await delayed.flush();
      await first;
      expect(gateway.sent, 1);
      expect(state.state(current), GenUiCardState.answered);
      expect(state.summary(current), isNotEmpty);
      expect(state.delivery(current), GenUiDeliveryState.idle);
    },
  );

  test(
    'fresh busy admission refuses a send without a dispatch marker',
    () async {
      final current = card();
      gateway.idle = false;
      final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
      final checked = expectLater(answer, throwsA(isA<ProductException>()));
      await delayed.flush();
      await checked;
      expect(gateway.sent, 0);
      expect(state.delivery(current), GenUiDeliveryState.failed);
      expect(
        state.journal.read('phone').where((r) => r.dispatchID != null),
        isEmpty,
      );
    },
  );

  test(
    'uncertain send persists without values and blocks resend after restart',
    () async {
      final current = card();
      gateway.fail = true;
      final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
      final checked = expectLater(answer, throwsA(isA<ProductException>()));
      await delayed.flush();
      await checked;
      expect(state.delivery(current), GenUiDeliveryState.deliveryUnknown);
      final persisted = prefs.getString(GenUiJournal.key('phone'))!;
      expect(persisted, isNot(contains('Continue?')));
      expect(persisted, isNot(contains('confirm')));
      state.dispose();
      state = GenUiStateController(prefs, delayedAnswers: delayed);
      register();
      expect(state.delivery(current), GenUiDeliveryState.deliveryUnknown);
      await expectLater(
        state.answer(current, const GenUiConfirmAnswer(true), []),
        throwsA(isA<ProductException>()),
      );
      expect(gateway.sent, 1);
    },
  );

  test('deletion cancels hold and prevents late read resurrection', () async {
    final current = card();
    final answer = state.answer(current, const GenUiConfirmAnswer(true), []);
    gateway.pause = Completer<void>();
    final read = state.recoverOne(const GenUiTarget(scope, 's'));
    await state.closeProfile('phone');
    await answer;
    await delayed.flush();
    gateway.pause!.complete();
    await read;
    expect(gateway.sent, 0);
    expect(state.waiting(scope, 's'), isEmpty);
  });

  test(
    'typed reply passes over card; stale coverage never offers controls',
    () {
      final current = card();
      state.stale(scope, 's');
      expect(state.waiting(scope, 's'), isEmpty);
      expect(state.state(current), GenUiCardState.unknown);
      state.observe(scope, 's', [
        ...gateway.history,
        MessageWithParts(
          info: MessageInfo(id: 'u', sessionID: 's', role: 'user'),
          parts: [Part(type: 'text', text: 'Let us do something else')],
        ),
      ], tailComplete: true);
      expect(state.state(current), GenUiCardState.passedOver);
    },
  );

  test('recovery is capped at twenty sessions and two pages', () async {
    gateway.history = [];
    gateway.hasMore = true;
    await state.recover([
      for (var i = 0; i < 25; i++) GenUiTarget(scope, 's$i'),
    ]);
    expect(gateway.reads, lessThanOrEqualTo(40));
    expect(state.recoveryIncomplete, isTrue);
  });

  test(
    'corrupt persisted state fails closed and cannot authorize resend',
    () async {
      final current = card();
      await state.journal.drained;
      await prefs.setString(GenUiJournal.key('phone'), '{broken');
      expect(state.delivery(current), GenUiDeliveryState.deliveryUnknown);
      await expectLater(
        state.answer(current, const GenUiConfirmAnswer(true), []),
        throwsA(isA<ProductException>()),
      );
    },
  );

  test('attachments cannot be smuggled into a non-photo answer', () async {
    await expectLater(
      state.answer(card(), const GenUiConfirmAnswer(true), [
        const PromptAttachment(
          mime: 'image/png',
          filename: 'x',
          url: 'file:///private',
        ),
      ]),
      throwsA(isA<ProductException>()),
    );
    expect(gateway.sent, 0);
  });
  test(
    'session deletion fences a late history read and candidate write',
    () async {
      await state.journal.drained;
      gateway.pause = Completer<void>();
      final read = state.recoverOne(const GenUiTarget(scope, 's'));
      await Future<void>.delayed(Duration.zero);
      await state.removeSession(scope, 's');
      gateway.pause!.complete();
      await read;
      state.observe(scope, 's', gateway.history, tailComplete: true);
      await state.journal.drained;
      expect(state.waiting(scope, 's'), isEmpty);
      expect(state.journal.read('phone'), isEmpty);
    },
  );

  test(
    'a stale recovery cannot replace a newer authoritative transcript',
    () async {
      final current = card();
      gateway.pause = Completer<void>();
      final read = state.recoverOne(const GenUiTarget(scope, 's'));
      await Future<void>.delayed(Duration.zero);
      state.observe(scope, 's', [
        tool(),
        MessageWithParts(
          info: MessageInfo(id: 'typed', sessionID: 's', role: 'user'),
          parts: [Part(type: 'text', text: 'Skip this')],
        ),
      ], tailComplete: true);
      gateway.pause!.complete();
      await read;
      expect(state.state(current), GenUiCardState.passedOver);
    },
  );

  test('earlier envelope cannot supply the later answer receipt summary', () {
    final current = card();
    MessageWithParts receipt(String id, bool value) => MessageWithParts(
      info: MessageInfo(id: id, sessionID: 's', role: 'user'),
      parts: [
        Part(
          type: 'text',
          text: genUiAnswerText(current, GenUiConfirmAnswer(value)),
        ),
      ],
    );
    state.observe(scope, 's', [
      receipt('before', false),
      tool(),
      receipt('after', true),
    ], tailComplete: true);
    expect(
      state.summary(current),
      genUiAnswerIn(receipt('after', true), current)!.summary,
    );
  });
  test('unknown coverage is not cleared by an unrelated small pass', () async {
    gateway.history = [];
    await state.recover([
      for (var i = 0; i < 25; i++) GenUiTarget(scope, 'outside-$i'),
    ]);
    expect(state.recoveryIncomplete, isTrue);
    await state.recover(const [GenUiTarget(scope, 'single')]);
    expect(state.recoveryIncomplete, isTrue);
  });

  test('disconnect fences a first read with no cached view', () async {
    state.invalidateScope(scope);
    gateway.pause = Completer<void>();
    final read = state.recoverOne(const GenUiTarget(scope, 's'));
    await Future<void>.delayed(Duration.zero);
    state.invalidateScope(scope);
    state.register(
      scope,
      gateway,
      endpoint: 'endpoint-digest',
      current: () => true,
      ready: true,
    );
    gateway.pause!.complete();
    await read;
    expect(state.waiting(scope, 's'), isEmpty);
  });
  test('photo answers validate matching attachments before dispatch', () async {
    gateway.history = [
      tool(ask: {'kind': 'photo', 'purpose': 'Show the item'}),
    ];
    register();
    final current = card();
    await expectLater(
      state.answer(current, const GenUiPhotoAnswer(1), []),
      throwsA(isA<ProductException>()),
    );
    final answer = state.answer(current, const GenUiPhotoAnswer(1), [
      const PromptAttachment(
        mime: 'image/png',
        filename: 'item.png',
        url: 'data:image/png;base64,aGVsbG8=',
      ),
    ]);
    await delayed.flush();
    await answer;
    expect(gateway.sent, 1);
    expect(state.state(current), GenUiCardState.answered);
    expect(
      prefs.getString(GenUiJournal.key('phone')),
      isNot(contains('aGVsbG8=')),
    );
  });
}
