import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:shared_preferences/shared_preferences.dart';

const agentCard = ExternalAgentCard(
  name: 'Color agent',
  description: 'Synthetic fixture',
  cardUrl: 'https://agent.example/.well-known/agent-card.json',
  endpoint: 'https://agent.example/rpc',
  version: '1',
  auth: ExternalAgentAuth.bearer,
  supported: true,
  skills: [],
);
const waitingTask = ExternalTask(
  id: 'task-1',
  contextId: 'context-1',
  state: ExternalTaskState.inputRequired,
  statusMessageId: 'question-1',
  parts: [ExternalResultPart(text: 'Which color?')],
);
const workingTask = ExternalTask(
  id: 'task-1',
  contextId: 'context-1',
  state: ExternalTaskState.working,
);
const completeTask = ExternalTask(
  id: 'task-1',
  contextId: 'context-1',
  state: ExternalTaskState.completed,
  parts: [ExternalResultPart(text: 'Blue result')],
);

class FakeExternalGateway implements ExternalAgentGateway {
  int sends = 0, queries = 0, cancels = 0;
  bool closed = false;
  ExternalTask next = waitingTask;
  ExternalTask cancelResult = const ExternalTask(
    id: 'task-1',
    contextId: 'context-1',
    state: ExternalTaskState.canceled,
  );
  ExternalTask? continuation;
  Future<ExternalTask> Function()? sendAction;
  Future<ExternalTask> Function()? queryAction;
  @override
  Future<ExternalAgentCard> discover(String address) async => agentCard;
  @override
  Future<ExternalTask> send(
    ExternalAgentCard card,
    String? credential,
    String text, {
    ExternalTask? continuation,
  }) async {
    sends++;
    this.continuation = continuation;
    return await (sendAction?.call() ?? Future.value(next));
  }

  @override
  Future<ExternalTask> getTask(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async {
    queries++;
    return await (queryAction?.call() ?? Future.value(next));
  }

  @override
  Future<ExternalTask> cancel(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async {
    cancels++;
    return cancelResult;
  }

  @override
  void close() {
    closed = true;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ExternalAgentStore store;
  late ExternalAgentProfile profile;
  late ExternalTaskRecord record;
  late FakeExternalGateway gateway;
  late ExternalTaskController controller;
  final secrets = <String, String>{};
  bool failDelete = false;
  setUp(() async {
    secrets.clear();
    failDelete = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final args = Map<String, dynamic>.from(call.arguments as Map);
            final key = args['key'] as String?;
            switch (call.method) {
              case 'read':
                return secrets[key];
              case 'write':
                secrets[key!] = args['value'] as String;
                return null;
              case 'delete':
                if (failDelete) throw PlatformException(code: 'fixture');
                secrets.remove(key);
                return null;
              default:
                return null;
            }
          },
        );
    SharedPreferences.setMockInitialValues({});
    store = ExternalAgentStore(
      await SharedPreferences.getInstance(),
      const FlutterSecureStorage(),
    );
    profile = await store.add(agentCard, 'fixture-token');
    record = ExternalTaskRecord(
      localId: 'local-1',
      title: 'Choose color',
      created: DateTime(2026, 9, 8),
    );
    await store.saveTask(profile.id, record);
    gateway = FakeExternalGateway();
    controller = ExternalTaskController(
      store: store,
      profile: profile,
      record: record,
      gateway: gateway,
    );
  });
  tearDown(() {
    controller.dispose();
    store.dispose();
  });
  test(
    'delivery marker is durable before send; continuation keeps task/context',
    () async {
      gateway.sendAction = () async {
        expect(store.tasks(profile.id).single.uncertain, isTrue);
        return waitingTask;
      };
      await controller.submit('choose');
      expect(controller.canContinue, isTrue);
      await controller.submit('blue', continuation: true);
      expect(gateway.continuation?.id, 'task-1');
      expect(gateway.continuation?.contextId, 'context-1');
    },
  );
  test('lost submit response survives restart and is never resent', () async {
    gateway.sendAction = () async =>
        throw const ExternalAgentException(ExternalAgentIssue.uncertain);
    await controller.submit('choose');
    await controller.submit('choose');
    expect(gateway.sends, 1);
    expect(store.tasks(profile.id).single.uncertain, isTrue);
    final reopened = ExternalTaskController(
      store: store,
      profile: profile,
      record: store.tasks(profile.id).single,
      gateway: gateway,
    );
    await reopened.refresh();
    await reopened.submit('choose');
    reopened.dispose();
    expect(gateway.sends, 1);
    expect(gateway.queries, 0);
  });
  test(
    'restart opens known task by query and terminal task never sends',
    () async {
      await controller.submit('choose');
      gateway.next = completeTask;
      final reloaded = ExternalAgentStore(store.prefs, store.secure);
      final reopened = ExternalTaskController(
        store: reloaded,
        profile: reloaded.profiles.single,
        record: reloaded.tasks(profile.id).single,
        gateway: gateway,
      );
      await reopened.refresh();
      await reopened.submit('again', continuation: true);
      expect(reopened.record.task?.state, ExternalTaskState.completed);
      expect(gateway.sends, 1);
      expect(gateway.queries, 1);
      reopened.dispose();
      reloaded.dispose();
    },
  );
  test(
    'uncertain reply remains blocked while same input request is reported',
    () async {
      await controller.submit('choose');
      gateway.sendAction = () async =>
          throw const ExternalAgentException(ExternalAgentIssue.uncertain);
      await controller.submit('blue', continuation: true);
      await controller.refresh();
      expect(controller.canContinue, isFalse);
      expect(controller.record.uncertain, isTrue);
      gateway.next = workingTask;
      await controller.refresh();
      expect(controller.record.uncertain, isFalse);
    },
  );
  test(
    'cancel checks fresh state; completion wins without cancel request',
    () async {
      await controller.submit('choose');
      gateway.next = completeTask;
      await controller.cancel();
      expect(gateway.cancels, 0);
      expect(controller.record.task?.state, ExternalTaskState.completed);
    },
  );
  test('active cancellation result is never reported as canceled', () async {
    await controller.submit('choose');
    gateway.cancelResult = workingTask;
    await controller.cancel();
    expect(controller.cancelUnconfirmed, isTrue);
    expect(controller.record.task?.state, ExternalTaskState.working);
    gateway.cancelResult = const ExternalTask(
      id: 'task-1',
      contextId: 'context-1',
      state: ExternalTaskState.canceled,
    );
    await controller.cancel();
    expect(controller.record.task?.state, ExternalTaskState.canceled);
  });
  test(
    'credential change during preflight prevents a pending cancel',
    () async {
      await controller.submit('choose');
      final gate = Completer<ExternalTask>();
      gateway.queryAction = () => gate.future;
      final cancel = controller.cancel();
      await Future<void>.delayed(Duration.zero);
      await store.updateCredential(profile.id, 'replacement');
      gate.complete(waitingTask);
      await cancel;
      expect(gateway.cancels, 0);
      expect(controller.current, isFalse);
    },
  );
  test(
    'agent deletion removes credential and records; late response cannot recreate them',
    () async {
      final gate = Completer<ExternalTask>();
      gateway.sendAction = () => gate.future;
      final send = controller.submit('choose');
      await Future<void>.delayed(Duration.zero);
      expect(gateway.sends, 1);
      await store.delete(profile.id);
      gate.complete(waitingTask);
      await send;
      expect(store.profiles, isEmpty);
      expect(store.tasks(profile.id), isEmpty);
      expect(secrets, isEmpty);
    },
  );
  test('forget during request prevents late record resurrection', () async {
    final gate = Completer<ExternalTask>();
    gateway.sendAction = () => gate.future;
    final send = controller.submit('choose');
    await Future<void>.delayed(Duration.zero);
    await store.removeTask(profile.id, record.localId);
    gate.complete(waitingTask);
    await send;
    expect(store.tasks(profile.id), isEmpty);
  });
  test(
    'failed credential deletion leaves unavailable tombstone and retries cleanly',
    () async {
      failDelete = true;
      await expectLater(
        store.delete(profile.id),
        throwsA(isA<ExternalAgentException>()),
      );
      expect(store.profiles.single.deleting, isTrue);
      expect(store.contains(profile.id), isFalse);
      failDelete = false;
      await store.delete(profile.id);
      expect(store.profiles, isEmpty);
      expect(secrets, isEmpty);
    },
  );
  test(
    'background pauses actions and late status cannot enable reply',
    () async {
      final gate = Completer<ExternalTask>();
      gateway.sendAction = () => gate.future;
      final send = controller.submit('choose');
      await Future<void>.delayed(Duration.zero);
      controller.background();
      gate.complete(waitingTask);
      await send;
      expect(controller.canContinue, isFalse);
      expect(controller.fresh, isFalse);
      await controller.resume();
      expect(controller.canContinue, isTrue);
    },
  );
  test('two stale controllers cannot both claim the same send', () async {
    final gate = Completer<ExternalTask>();
    gateway.sendAction = () => gate.future;
    final second = ExternalTaskController(
      store: store,
      profile: profile,
      record: record,
      gateway: gateway,
    );
    final a = controller.submit('choose');
    final b = second.submit('choose');
    await Future<void>.delayed(Duration.zero);
    expect(gateway.sends, 1);
    gate.complete(waitingTask);
    await Future.wait([a, b]);
    second.dispose();
  });
  test('mismatched context does not replace saved task', () async {
    await controller.submit('choose');
    gateway.next = const ExternalTask(
      id: 'task-1',
      contextId: 'foreign',
      state: ExternalTaskState.completed,
    );
    await controller.refresh();
    expect(controller.issue, ExternalAgentIssue.invalidResponse);
    expect(controller.record.task?.contextId, 'context-1');
  });
  test('no bearer appears in persisted registry or task snapshots', () async {
    await controller.submit('choose');
    final values = store.prefs
        .getKeys()
        .map((key) => store.prefs.get(key))
        .toList();
    expect(jsonEncode(values), isNot(contains('fixture-token')));
    expect(secrets.keys.single, 'oc.a2aBearer.${profile.id}');
  });
  test(
    'full edited unsent draft survives store reconstruction and local deletion',
    () async {
      final text = 'Draft ${'detailed requirement ' * 40}';
      await store.saveDraft(profile.id, record.localId, text);
      final persisted = {
        for (final key in store.prefs.getKeys()) key: store.prefs.get(key)!,
      };
      SharedPreferences.setMockInitialValues(persisted);
      final restored = ExternalAgentStore(
        await SharedPreferences.getInstance(),
        store.secure,
      );
      expect(restored.tasks(profile.id).single.draft, text);
      expect(restored.tasks(profile.id).single.title, record.title);
      expect(gateway.sends, 0);
      await restored.removeTask(profile.id, record.localId);
      expect(restored.tasks(profile.id), isEmpty);
      restored.dispose();
    },
  );
  test(
    'draft writes cannot replace dispatched tasks or revive deleted tasks',
    () async {
      await controller.submit('choose');
      await expectLater(
        store.saveDraft(profile.id, record.localId, 'new draft'),
        throwsA(isA<ExternalAgentException>()),
      );
      await store.removeTask(profile.id, record.localId);
      await expectLater(
        store.saveDraft(profile.id, record.localId, 'late draft'),
        throwsA(isA<ExternalAgentException>()),
      );
      expect(store.tasks(profile.id), isEmpty);
    },
  );
  test(
    'oversized draft refuses persistence and leaves the previous draft intact',
    () async {
      await store.saveDraft(profile.id, record.localId, 'Keep this');
      await expectLater(
        store.saveDraft(profile.id, record.localId, 'x' * 16001),
        throwsA(isA<ExternalAgentException>()),
      );
      expect(store.tasks(profile.id).single.draft, 'Keep this');
    },
  );
  test(
    'oversized local records are rejected without making existing data unreadable',
    () async {
      final huge = record.withTask(
        ExternalTask(
          state: ExternalTaskState.completed,
          parts: [ExternalResultPart(text: 'x' * (4 * 1024 * 1024))],
        ),
      );
      await expectLater(
        store.saveTask(profile.id, huge),
        throwsA(isA<ExternalAgentException>()),
      );
      expect(store.tasks(profile.id).single.task, isNull);
      final card = ExternalAgentCard(
        name: agentCard.name,
        description: 'x' * 256000,
        cardUrl: agentCard.cardUrl,
        endpoint: agentCard.endpoint,
        version: '1',
        auth: ExternalAgentAuth.none,
        supported: true,
        skills: [],
      );
      await expectLater(
        store.add(card, ''),
        throwsA(isA<ExternalAgentException>()),
      );
      expect(store.profiles.length, 1);
    },
  );
}
