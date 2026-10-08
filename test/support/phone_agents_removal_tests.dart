part of '../phone_agents_controller_test.dart';

class _RemovableHost extends _FakeHost implements PhoneAgentRemovalPort {
  _RemovableHost(super.events, super.profileId, super.state);
  Future<void> Function(String)? removal;
  @override
  Future<void> removeAgent(String id) async {
    events.log.add('remove.$id');
    await removal?.call(id);
    state.runtimes.remove(id);
  }
}

void _phoneRemovalTests() {
  test(
    'removal removes only target rows and keeps Claude chats and account',
    () async {
      final w = await _world(null, removalSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      w.state.runtimes = {'claude': _ready('claude'), 'fx': _ready('fx')};
      w.state.agents = [_agent('claude-kept', _project)];
      await c.rememberLastUsedProject(_project);
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      final beforeAccount = c.agentAccount('claude')?.state;
      final beforeChats = c
          .chatFeed()
          .items
          .map((row) => row.identity)
          .toList();
      expect(c.canRemoveAgent('fx'), true);
      expect(c.canRemoveAgent('claude'), false);
      expect(c.canRemoveAgent('codex'), false);
      w.events.log.clear();
      await c.removeAgent('fx');
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
    await expectLater(
      c.startAgentChatIn(_project, agentId: 'fx'),
      throwsA(isA<ProductException>()),
    );
    await expectLater(c.removeAgent('codex'), throwsA(isA<ProductException>()));
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
          'This agent is still in use. Finish its setup or conversation and try again.',
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
      throwsA(isA<ProductException>()),
    );
    await expectLater(
      w.controller.removeAgent('claude'),
      throwsA(isA<ProductException>()),
    );
    expect(w.events.log, isNot(contains('host.stop')));
  });
}
