part of '../phone_agents_controller_test.dart';

class _RemovableHost extends _FakeHost implements PhoneAgentRemovalPort {
  _RemovableHost(super.events, super.profileId, super.state);
  Future<void> Function(String)? removal;
  AgentRemovalResult? result;
  AgentSetupProgress progress = const AgentSetupProgress(
    agentId: '',
    phase: AgentSetupPhase.idle,
  );
  @override
  AgentSetupProgress get setupProgress => progress;
  @override
  Future<AgentRemovalResult> removeAgent(String id) async {
    events.log.add('remove.$id');
    await removal?.call(id);
    state.runtimes.remove(id);
    return result ??
        AgentRemovalResult(agentId: id, freedBytes: 8192, alreadyAbsent: false);
  }
}

const _removalBusyCopy = 'This agent is in use. Finish its work and try again.';
const _removalUnsupportedCopy = "This agent can't be removed here.";
const _removalUnconfirmedCopy =
    "Couldn't confirm this agent was removed. Check this phone and try again.";

Matcher _removalFailure(String copy) =>
    throwsA(isA<ProductException>().having((e) => e.message, 'message', copy));

void _phoneRemovalTests() {
  test(
    'removal removes only target rows and keeps Claude chats and account',
    () async {
      final gatePreference = jsonEncode({'claude': 'kept', 'fx': 'kept'});
      final w = await _world(
        null,
        removalSupported: true,
        prefsExtra: {'${phoneAgentGatePrefix}local': gatePreference},
      );
      final c = w.controller;
      addTearDown(c.dispose);
      w.state.runtimes = {'claude': _ready('claude'), 'fx': _ready('fx')};
      w.state.agents = [_agent('claude-kept', _project)];
      await c.rememberLastUsedProject(_project);
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      final phoneCheck = await c.runAgentPhoneCheck('fx');
      final beforeAccount = c.agentAccount('claude')?.state;
      final beforeFxAccount = c.agentAccount('fx')?.state;
      final beforeChats = c
          .chatFeed()
          .items
          .map((row) => row.identity)
          .toList();
      expect(c.canRemoveAgent('fx'), true);
      expect(c.canRemoveAgent('claude'), false);
      expect(c.canRemoveAgent('codex'), false);
      const expected = AgentRemovalResult(
        agentId: 'fx',
        freedBytes: 65536,
        alreadyAbsent: false,
      );
      (w.host as _RemovableHost).result = expected;
      w.events.log.clear();
      final actual = await (c as dynamic).removeAgent('fx');
      expect(actual, same(expected));
      expect(c.removingAgentId, isNull);
      expect(
        c.agentRows.singleWhere((row) => row.id == 'fx').status,
        PhoneAgentStatus.needsInstall,
      );
      expect(
        c.agentRows.singleWhere((row) => row.id == 'claude').status,
        PhoneAgentStatus.ready,
      );
      expect(c.agentAccount('claude')?.state, beforeAccount);
      expect(c.agentAccount('fx')?.state, beforeFxAccount);
      expect(c.agentPhoneCheck('fx'), same(phoneCheck));
      expect(
        c.store.prefs.getString('${phoneAgentGatePrefix}local'),
        gatePreference,
      );
      expect(c.chatFeed().items.map((row) => row.identity), beforeChats);
      expect(w.events.log, ['remove.fx']);
    },
  );
  test('removal excludes new phone chats and a second removal', () async {
    final w = await _world(null, removalSupported: true);
    final c = w.controller;
    addTearDown(c.dispose);
    w.state.runtimes = {'claude': _ready('claude'), 'fx': _ready('fx')};
    await c.refreshAgentRows();
    final gate = Completer<void>();
    (w.host as _RemovableHost).removal = (_) => gate.future;
    final removal = c.removeAgent('fx');
    expect(c.removingAgentId, 'fx');
    expect(c.canRemoveAgent('fx'), false);
    await expectLater(
      c.startAgentChatIn(_project, agentId: 'fx'),
      _removalFailure(_removalBusyCopy),
    );
    await expectLater(
      c.removeAgent('codex'),
      _removalFailure(_removalBusyCopy),
    );
    await expectLater(
      c.installAgent('codex'),
      _removalFailure(_removalBusyCopy),
    );
    gate.complete();
    await removal;
    expect(c.removingAgentId, isNull);
  });
  test('removal returns plain busy guidance and preserves ready row', () async {
    final w = await _world(null, removalSupported: true);
    final c = w.controller;
    addTearDown(c.dispose);
    w.state.runtimes = {'fx': _ready('fx')};
    await c.refreshAgentRows();
    (w.host as _RemovableHost).removal = (_) =>
        Future.error(const AgentHostException(AgentHostFailure.busy));
    await expectLater(
      c.removeAgent('fx'),
      throwsA(
        isA<ProductException>().having(
          (e) => e.message,
          'message',
          _removalBusyCopy,
        ),
      ),
    );
    expect(c.removingAgentId, isNull);
    expect(
      c.agentRows.singleWhere((row) => row.id == 'fx').status,
      PhoneAgentStatus.ready,
    );
  });
  test('older hosts and Claude removal fail closed before dispatch', () async {
    final w = await _world(null);
    addTearDown(w.controller.dispose);
    w.state.runtimes = {'fx': _ready('fx')};
    await w.controller.refreshAgentRows();
    expect(w.controller.canRemoveAgent('fx'), false);
    await expectLater(
      w.controller.removeAgent('fx'),
      _removalFailure(_removalUnsupportedCopy),
    );
    await expectLater(
      w.controller.removeAgent('claude'),
      _removalFailure(_removalUnsupportedCopy),
    );
    expect(w.events.log, isNot(contains('host.stop')));
  });
  test(
    'removal forwards an already-absent result without estimating bytes',
    () async {
      final w = await _world(null, removalSupported: true);
      addTearDown(w.controller.dispose);
      await w.controller.refreshAgentRows();
      const expected = AgentRemovalResult(
        agentId: 'fx',
        freedBytes: 0,
        alreadyAbsent: true,
      );
      (w.host as _RemovableHost).result = expected;
      final actual = await (w.controller as dynamic).removeAgent('fx');
      expect(actual, same(expected));
    },
  );
  test('removal is available for all installed supported agents', () async {
    final w = await _world(null, removalSupported: true);
    addTearDown(w.controller.dispose);
    const ids = ['codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'];
    w.state.runtimes = {for (final id in ids) id: _ready(id)};
    await w.controller.refreshAgentRows();
    for (final id in ids) {
      expect(w.controller.canRemoveAgent(id), true, reason: id);
    }
    expect(w.controller.canRemoveAgent('claude'), false);
  });
  test(
    'removal remains available while an installed agent account is unknown',
    () async {
      final w = await _world(null, removalSupported: true);
      addTearDown(w.controller.dispose);
      w.state.runtimes = {
        'codex': const PhoneAgentRuntime(
          agentId: 'codex',
          installed: true,
          hostAvailable: true,
          architectureQualified: true,
        ),
      };
      await w.controller.refreshAgentRows();
      expect(
        w.controller.agentRows.singleWhere((row) => row.id == 'codex').status,
        PhoneAgentStatus.unavailable,
      );
      expect(w.controller.canRemoveAgent('codex'), true);
    },
  );
  test(
    'removal is available for a failed or interrupted partial target install',
    () async {
      final w = await _world(null, removalSupported: true);
      addTearDown(w.controller.dispose);
      await w.controller.refreshAgentRows();
      final host = w.host as _RemovableHost;
      for (final phase in [
        AgentSetupPhase.failed,
        AgentSetupPhase.interrupted,
      ]) {
        for (final id in [
          'codex',
          'gemini',
          'qwen',
          'goose',
          'omp-acp',
          'fx',
        ]) {
          host.progress = AgentSetupProgress(agentId: id, phase: phase);
          expect(w.controller.canRemoveAgent(id), true, reason: '$id $phase');
        }
      }
      host.progress = const AgentSetupProgress(
        agentId: 'claude',
        phase: AgentSetupPhase.failed,
      );
      expect(w.controller.canRemoveAgent('claude'), false);
      expect(w.controller.canRemoveAgent('fx'), false);
    },
  );
  test(
    'removal hides arbitrary host failures behind fixed confirmation guidance',
    () async {
      final w = await _world(null, removalSupported: true);
      addTearDown(w.controller.dispose);
      w.state.runtimes = {'fx': _ready('fx')};
      await w.controller.refreshAgentRows();
      final host = w.host as _RemovableHost;
      for (final error in [
        const AgentHostException(AgentHostFailure.unavailable),
        const AgentHostException(AgentHostFailure.stale),
        StateError('private-child-output-test-marker'),
      ]) {
        host.removal = (_) => Future.error(error);
        await expectLater(
          w.controller.removeAgent('fx'),
          _removalFailure(_removalUnconfirmedCopy),
        );
        expect(w.controller.removingAgentId, isNull);
      }
    },
  );
  test(
    'removal from a replaced owner cannot clear the new removal or its rows',
    () async {
      final w = await _world(null, removalSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      w.state.runtimes = {'fx': _ready('fx')};
      await c.refreshAgentRows();
      final oldGate = Completer<void>();
      final newGate = Completer<void>();
      addTearDown(() {
        if (!oldGate.isCompleted) oldGate.complete();
        if (!newGate.isCompleted) newGate.complete();
      });
      (w.host as _RemovableHost).removal = (_) => oldGate.future;
      final oldOutcome = (c.removeAgent('fx') as Future).then<Object?>(
        (value) => value,
        onError: (Object error) => error,
      );
      expect(c.removingAgentId, 'fx');
      await c.deleteProfileAndLocalData('local');
      final replacement = ServerProfile(
        id: 'replacement',
        name: 'Replacement phone',
        baseUrl: 'http://127.0.0.1:4097',
      );
      await c.store.upsert(replacement);
      w.state.runtimes = {'codex': _ready('codex')};
      await c.connect(replacement);
      await c.refreshAgentRows();
      expect(w.host.profileId, 'replacement');
      expect(c.removingAgentId, isNull);
      (w.host as _RemovableHost).removal = (_) => newGate.future;
      final newOutcome = (c.removeAgent('codex') as Future).then<Object?>(
        (value) => value,
        onError: (Object error) => error,
      );
      expect(c.removingAgentId, 'codex');
      oldGate.complete();
      expect(
        await oldOutcome,
        isA<ProductException>().having(
          (e) => e.message,
          'message',
          _removalUnconfirmedCopy,
        ),
      );
      expect(c.removingAgentId, 'codex');
      expect(
        c.agentRows.singleWhere((row) => row.id == 'codex').status,
        PhoneAgentStatus.ready,
      );
      newGate.complete();
      expect(await newOutcome, isA<AgentRemovalResult>());
      expect(c.removingAgentId, isNull);
    },
  );
}
