part of '../phone_agents_controller_test.dart';

class _RemovableHost extends _FakeHost implements PhoneAgentRemovalPort {
  _RemovableHost(super.events, super.profileId, super.state);
  Future<void> Function(String)? removal;
  bool disposed = false;
  AgentRemovalResult? result;
  AgentSetupProgress progress = const AgentSetupProgress(
    agentId: '',
    phase: AgentSetupPhase.idle,
  );
  @override
  AgentSetupProgress get setupProgress => progress;
  @override
  Future<void> dispose() async {
    disposed = true;
    await super.dispose();
  }

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

class _ReplacementForegroundPort implements AgentSignInForegroundPort {
  int reservations = 0;
  @override
  AgentSignInForegroundLease reserveAgentSignInForeground() {
    reservations++;
    return _ReplacementForegroundLease();
  }
}

class _ReplacementForegroundLease implements AgentSignInForegroundLease {
  bool released = false;
  @override
  Future<void> get ready => Future.value();
  @override
  bool get active => !released;
  @override
  Stream<void> get lost => const Stream.empty();
  @override
  Future<void> release() async => released = true;
}

void _phoneRemovalTests() {
  test(
    'BA16 partial payload removal survives restart and unrelated setup',
    () async {
      for (final phase in [AgentSetupPhase.idle, AgentSetupPhase.done]) {
        final w = await _world(null, removalSupported: true);
        try {
          w.state.runtimes = {
            'fx': const PhoneAgentRuntime(agentId: 'fx', payloadPresent: true),
          };
          await w.controller.refreshAgentRows();
          (w.host as _RemovableHost).progress = AgentSetupProgress(
            agentId: 'codex',
            phase: phase,
          );
          await w.controller.refreshAgentRows();
          final row = w.controller.agentRows.singleWhere((r) => r.id == 'fx');
          expect(row.status, PhoneAgentStatus.needsInstall);
          expect(row.chatSelectable, isFalse);
          expect(w.controller.canRemoveAgent('fx'), isTrue);
          expect(w.controller.canRemoveAgent('claude'), isFalse);
          w.state.runtimes['fx'] = const PhoneAgentRuntime(agentId: 'fx');
          await w.controller.refreshAgentRows();
          expect(w.controller.canRemoveAgent('fx'), isFalse);
        } finally {
          w.controller.dispose();
        }
      }
    },
  );
  test(
    'BB6 main foreground binding exists before a terminal request',
    () async {
      final w = await _world(null);
      addTearDown(w.controller.dispose);
      await w.controller.refreshAgentRows();
      final owner = w.controller.agentSignInProfileId!;
      final binding = AgentSignInForegroundRegistry.bindingFor(owner);
      expect(binding, isNotNull);
      expect(binding!.current, isTrue);
      final lease = binding.reserve();
      // The actual shared controller is the production port. Linux cannot
      // start Android's foreground service; admission must fail before a PTY.
      await expectLater(
        lease.ready,
        throwsA(
          isA<AgentSignInForegroundException>().having(
            (e) => e.reason,
            'reason',
            AgentSignInForegroundFailure.unsupported,
          ),
        ),
      );
      await lease.release();
    },
  );
  test('BB6 owner deletion waits for registered terminal cleanup', () async {
    final w = await _world(
      null,
      secure: _FakeSecure({'oc.agentHostSecret.local': 'x' * 64}),
    );
    final c = w.controller;
    addTearDown(c.dispose);
    await c.refreshAgentRows();
    final binding = AgentSignInForegroundRegistry.bindingFor('local');
    expect(binding, isNotNull);
    final entered = Completer<void>();
    final drain = Completer<void>();
    addTearDown(() {
      if (!drain.isCompleted) drain.complete();
    });
    binding!.addCleanup(() async {
      w.events.log.add('terminal.drain');
      entered.complete();
      await drain.future;
    });
    w.events.log.clear();
    final deletion = c.deleteProfileAndLocalData('local');
    await entered.future;
    expect(AgentSignInForegroundRegistry.bindingFor('local'), isNull);
    expect(w.events.log, ['terminal.drain']);
    expect(c.store.profiles.any((p) => p.id == 'local'), isTrue);
    drain.complete();
    expect((await deletion).removedProfile, isTrue);
    expect(w.events.log, [
      'terminal.drain',
      'host.cancelInstall',
      'host.stop',
      'host.dispose',
      'profileStore.cleanup',
    ]);
  });
  test('BB6 protocol alias retains the canonical foreground owner', () async {
    final w = await _world(null);
    final c = w.controller;
    addTearDown(c.dispose);
    await c.refreshAgentRows();
    final binding = AgentSignInForegroundRegistry.bindingFor('local');
    expect(binding, isNotNull);
    var drained = false;
    binding!.addCleanup(() async {
      drained = true;
    });
    final original = c.store.profiles.single;
    final alias = ServerProfile(
      id: 'two',
      name: 'OpenCode 2',
      baseUrl: original.baseUrl,
      flavor: ServerFlavor.v2,
    );
    await c.store.upsert(alias);
    await c.connect(alias);
    await c.refreshAgentRows();
    expect(c.agentSignInProfileId, 'local');
    expect(AgentSignInForegroundRegistry.bindingFor('local'), same(binding));
    expect(AgentSignInForegroundRegistry.bindingFor('two'), isNull);
    expect((await c.deleteProfileAndLocalData('two')).removedProfile, isTrue);
    expect(drained, isFalse);
    expect(binding.current, isTrue);
  });
  test(
    'BB6 failed terminal drain blocks deletion and remains retryable',
    () async {
      final w = await _world(
        null,
        secure: _FakeSecure({'oc.agentHostSecret.local': 'x' * 64}),
      );
      final c = w.controller;
      addTearDown(c.dispose);
      await c.refreshAgentRows();
      final binding = AgentSignInForegroundRegistry.bindingFor('local');
      expect(binding, isNotNull);
      var attempts = 0;
      binding!.addCleanup(() async {
        attempts++;
        if (attempts == 1) throw StateError('cleanup-test-marker');
      });
      w.events.log.clear();
      await expectLater(
        c.deleteProfileAndLocalData('local'),
        throwsA(
          isA<ProductException>().having(
            (e) => e.message,
            'message',
            'Sign-in could not be stopped. Keep the app open and try again.',
          ),
        ),
      );
      expect(c.store.profiles.any((p) => p.id == 'local'), isTrue);
      expect(w.events.log, isEmpty);
      expect(
        (await c.deleteProfileAndLocalData('local')).removedProfile,
        isTrue,
      );
      expect(attempts, 2);
      expect(w.events.log, contains('profileStore.cleanup'));
    },
  );
  test(
    'BB6 old foreground drain cannot dispose replacement host or unbind newer generation',
    () async {
      final w = await _world(null, removalSupported: true);
      final c = w.controller;
      var disposed = false;
      addTearDown(() {
        if (!disposed) c.dispose();
      });
      await c.refreshAgentRows();
      final original = w.host as _RemovableHost;
      final oldBinding = AgentSignInForegroundRegistry.bindingFor('local');
      expect(oldBinding, isNotNull);
      final entered = Completer<void>();
      final drain = Completer<void>();
      addTearDown(() {
        if (!drain.isCompleted) drain.complete();
      });
      oldBinding!.addCleanup(() async {
        entered.complete();
        await drain.future;
      });
      // Exercise an owner replacement against in-memory test preferences,
      // without accessing real native account homes or service processes.
      await c.store.prefs.setString('oc.phoneAgentOwner.local', 'replacement');
      await c.refreshAgentRows();
      await entered.future;
      final replacement = w.host as _RemovableHost;
      final replacementBinding = AgentSignInForegroundRegistry.bindingFor(
        'replacement',
      );
      expect(replacement, isNot(same(original)));
      expect(replacementBinding, isNotNull);
      expect(replacement.disposed, isFalse);
      drain.complete();
      for (var i = 0; i < 30 && !original.disposed; i++) {
        await Future<void>.delayed(Duration.zero);
      }
      expect(original.disposed, isTrue);
      expect(replacement.disposed, isFalse);
      expect(
        AgentSignInForegroundRegistry.bindingFor('replacement'),
        same(replacementBinding),
      );
      final port = _ReplacementForegroundPort();
      final newer = AgentSignInForegroundRegistry.bind('replacement', port);
      c.dispose();
      disposed = true;
      final lease = newer.reserve();
      await lease.ready;
      expect(port.reservations, 1);
      expect(
        AgentSignInForegroundRegistry.bindingFor('replacement'),
        same(newer),
      );
      await lease.release();
      await AgentSignInForegroundRegistry.unbind(newer);
    },
  );
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
