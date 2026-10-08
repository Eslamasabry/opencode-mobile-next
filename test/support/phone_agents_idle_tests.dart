part of '../phone_agents_controller_test.dart';

PhoneAgentIdleState _idleReceipt({bool helper = true, int token = 7}) =>
    PhoneAgentIdleState(
      supported: true,
      idleStopped: false,
      helperStopped: helper,
      generation: token,
      serverRestartWanted: true,
      serverRunning: true,
    );

class _IdleHost extends _FakeHost
    implements PhoneAgentIdleHostPort, PhoneAgentLivenessPort {
  _IdleHost(super.events, super.profileId, super.state);
  PhoneAgentIdleState receipt = _idleReceipt();
  bool running = false;
  int resumes = 0;
  Completer<void>? readGate;
  Completer<void>? readEntered;
  Completer<void>? resumeGate;

  @override
  Future<bool?> helperRunning() async => running;

  @override
  Future<PhoneAgentIdleState> idleState() async {
    if (readEntered != null && !readEntered!.isCompleted) {
      readEntered!.complete();
    }
    await readGate?.future;
    return receipt;
  }

  @override
  Future<void> resumeAfterIdle({
    required int expectedIdleGeneration,
    required bool Function() stillCurrent,
  }) async {
    if (!stillCurrent() || expectedIdleGeneration != receipt.generation) {
      throw const AgentHostException(AgentHostFailure.stale);
    }
    if (receipt.helperStopped != true) return;
    resumes++;
    events.log.add('idle.start.$expectedIdleGeneration');
    await resumeGate?.future;
    if (!stillCurrent()) throw const AgentHostException(AgentHostFailure.stale);
    running = true;
    receipt = _idleReceipt(helper: false, token: expectedIdleGeneration);
    state.runtimes['claude'] = _ready('claude');
  }
}

void _phoneIdleTests() {
  test(
    'BA idle known stopped helper is idle; cold and unknown owner are unknown',
    () async {
      final w = await _world(null, idleSupported: true);
      addTearDown(w.controller.dispose);
      expect(w.controller.localPhoneAgentWorkBusy('unknown'), isNull);
      await w.controller.refreshAgentRows();
      expect(w.controller.localPhoneAgentWorkBusy('local'), isFalse);
      w.controller.dispose();
      expect(w.controller.localPhoneAgentWorkBusy('local'), isNull);
    },
  );

  test(
    'BA idle cold helper restoration coalesces without existing backend',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      expect(w.hosts, isEmpty);
      expect(c.localPhoneAgentWorkBusy('local'), isNull);
      final first = c.resumePhoneAgentsAfterIdle(
        profileId: 'local',
        expectedIdleGeneration: 7,
      );
      final host = w.host as _IdleHost;
      host.resumeGate = Completer<void>();
      expect(c.localPhoneAgentWorkBusy('local'), isTrue);
      final second = c.resumePhoneAgentsAfterIdle(
        profileId: 'local',
        expectedIdleGeneration: 7,
      );
      expect(identical(first, second), isTrue);
      await pumpEventQueue();
      final starts = host.resumes;
      host.resumeGate!.complete();
      await first;
      expect(starts, 1);
      expect(host.running, isTrue);
      expect(w.events.log, isNot(contains('host.start')));
    },
  );

  test(
    'BA idle background rows and explicit helper resume cannot bypass idle',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      w.state.runtimes['claude'] = PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        architectureQualified: true,
        stoppedInBackground: true,
        capabilities: _ready('claude').capabilities,
      );
      c.suspendForLifecycle();
      w.events.log.clear();
      await c.refreshAgentRows();
      await pumpEventQueue();
      expect(w.events.log, isNot(contains('host.start')));
      await expectLater(c.resumeAgentHost(), throwsA(isA<ProductException>()));
      expect(w.events.log, isNot(contains('host.start')));
    },
  );

  test(
    'BA idle foreground ordinary resume cannot consume idle generation',
    () async {
      final w = await _world(null, idleSupported: true);
      addTearDown(w.controller.dispose);
      await w.controller.refreshAgentRows();
      w.events.log.clear();
      await expectLater(
        w.controller.resumeAgentHost(),
        throwsA(isA<ProductException>()),
      );
      expect(w.events.log, isNot(contains('host.start')));
    },
  );

  test('BA idle helper not recorded live remains stopped', () async {
    final w = await _world(null, idleSupported: true);
    addTearDown(w.controller.dispose);
    await w.controller.refreshAgentRows();
    final host = w.host as _IdleHost;
    host.receipt = _idleReceipt(helper: false);
    await w.controller.resumePhoneAgentsAfterIdle(
      profileId: 'local',
      expectedIdleGeneration: 7,
    );
    expect(host.resumes, 0);
    expect(host.running, isFalse);
  });

  test(
    'BA idle foreground loss during receipt read prevents dispatch',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      await c.refreshAgentRows();
      final host = w.host as _IdleHost;
      host.readEntered = Completer<void>();
      host.readGate = Completer<void>();
      final resume = c.resumePhoneAgentsAfterIdle(
        profileId: 'local',
        expectedIdleGeneration: 7,
      );
      final failure = expectLater(resume, throwsA(isA<ProductException>()));
      await host.readEntered!.future;
      c.suspendForLifecycle();
      host.readGate!.complete();
      await failure;
      expect(host.resumes, 0);
    },
  );

  test('BA idle mismatched token refuses without helper launch', () async {
    final w = await _world(null, idleSupported: true);
    addTearDown(w.controller.dispose);
    await w.controller.refreshAgentRows();
    final host = w.host as _IdleHost;
    await expectLater(
      w.controller.resumePhoneAgentsAfterIdle(
        profileId: 'local',
        expectedIdleGeneration: 8,
      ),
      throwsA(isA<ProductException>()),
    );
    expect(host.resumes, 0);
  });

  test(
    'BA idle connected unknown inventory overrides stale helper stop',
    () async {
      final w = await _world(null, idleSupported: true);
      addTearDown(w.controller.dispose);
      w.state.runtimes['claude'] = _ready('claude');
      w.state.agents = [_agent('idle-parent', _project)];
      w.state.gatewayFactory = (transport, directory) => PaseoGateway(
        transport: transport,
        directory: directory,
        trackLocalWork: true,
      );
      // Unsupported child inventory means unknown, despite an earlier native
      // not-running observation during row inspection.
      w.state.configureSocket = (socket) {
        socket.handlers['agent.provider_subagents.list.request'] = (_) =>
            ('rpc_error', {'error': 'unsupported'});
      };
      await w.controller.rememberLastUsedProject(_project);
      await w.controller.refreshAgentRows();
      await w.controller.refreshChatFeed();
      expect(
        w.host.gateways.any((gateway) => gateway.transport.connected),
        isTrue,
      );
      expect(w.controller.localPhoneAgentWorkBusy('local'), isNull);
    },
  );

  test(
    'BA idle controller propagates all-folder busy changes and loss',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      w.state.runtimes['claude'] = _ready('claude');
      await c.refreshAgentRows();
      (w.host as _IdleHost).running = true;
      w.state.gatewayFactory = (transport, directory) => PaseoGateway(
        transport: transport,
        directory: directory,
        trackLocalWork: true,
      );
      late FakePaseoSocket observed;
      w.state.configureSocket = (socket) {
        observed = socket;
        socket.handlers['fetch_agents_request'] = (_) => (
          'fetch_agents_response',
          {
            'entries': [
              {
                'agent': _agent('elsewhere-codex', '/root/projects/other')
                  ..['provider'] = 'codex',
              },
            ],
            'pageInfo': {'hasMore': false, 'nextCursor': null},
          },
        );
        socket.handlers['agent.provider_subagents.list.request'] = (request) =>
            (
              'agent.provider_subagents.list.response',
              {
                'parentAgentId': request['parentAgentId'],
                'subagents': <Object?>[],
                'error': null,
              },
            );
      };
      await c.rememberLastUsedProject(_project);
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      expect(c.localPhoneAgentWorkBusy('local'), isFalse);
      var notifications = 0;
      c.addListener(() => notifications++);
      final before = notifications;
      observed.push('agent_stream', {
        'agentId': 'elsewhere-codex',
        'epoch': 'one',
        'seq': 1,
        'event': {'type': 'turn_started', 'provider': 'codex'},
      });
      await pumpEventQueue();
      expect(c.localPhoneAgentWorkBusy('local'), isTrue);
      expect(notifications, greaterThan(before));
      observed.push('agent_stream', {
        'agentId': 'elsewhere-codex',
        'epoch': 'one',
        'seq': 2,
        'event': {'type': 'turn_completed', 'provider': 'codex'},
      });
      await pumpEventQueue();
      expect(c.localPhoneAgentWorkBusy('local'), isFalse);
      for (final gateway in w.host.gateways) {
        gateway.close();
      }
      await pumpEventQueue();
      expect(c.localPhoneAgentWorkBusy('local'), isNull);
    },
  );

  for (final deletion in [false, true]) {
    test(
      'BA idle ${deletion ? 'deletion' : 'disposal'} during receipt read prevents launch',
      () async {
        final w = await _world(null, idleSupported: true);
        final c = w.controller;
        addTearDown(c.dispose);
        await c.refreshAgentRows();
        final host = w.host as _IdleHost;
        host.readEntered = Completer<void>();
        host.readGate = Completer<void>();
        final resume = c.resumePhoneAgentsAfterIdle(
          profileId: 'local',
          expectedIdleGeneration: 7,
        );
        final failure = expectLater(resume, throwsA(isA<ProductException>()));
        await host.readEntered!.future;
        if (deletion) {
          await c.deleteProfileAndLocalData('local');
        } else {
          c.dispose();
        }
        host.readGate!.complete();
        await failure;
        expect(host.resumes, 0);
        expect(c.localPhoneAgentWorkBusy('local'), isNull);
      },
    );
  }

  test(
    'BA idle native generation revoked during feed cannot report restored',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
      await c.refreshAgentRows();
      final host = w.host as _IdleHost;
      await c.rememberLastUsedProject(_project);
      var revoked = false;
      w.state.configureSocket = (socket) {
        final fetch = socket.handlers['fetch_agents_request']!;
        socket.handlers['fetch_agents_request'] = (request) {
          host.receipt = _idleReceipt(helper: false, token: 8);
          revoked = true;
          return fetch(request);
        };
      };
      await expectLater(
        c.resumePhoneAgentsAfterIdle(
          profileId: 'local',
          expectedIdleGeneration: 7,
        ),
        throwsA(
          isA<ProductException>().having(
            (error) => error.cause,
            'cause',
            'idle_resume_stale',
          ),
        ),
      );
      expect(revoked, isTrue);
      expect(host.resumes, 1);
      expect(w.events.log, isNot(contains('host.start')));
    },
  );

  test(
    'BA idle hooks share the canonical owner across protocol aliases',
    () async {
      final w = await _world(null, idleSupported: true);
      final c = w.controller;
      addTearDown(c.dispose);
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
      expect(c.localPhoneAgentWorkBusy('local'), isFalse);
      expect(c.localPhoneAgentWorkBusy('two'), isFalse);
      final host = w.host as _IdleHost;
      expect(host.profileId, 'local');
      host.resumeGate = Completer<void>();
      final first = c.resumePhoneAgentsAfterIdle(
        profileId: 'local',
        expectedIdleGeneration: 7,
      );
      final second = c.resumePhoneAgentsAfterIdle(
        profileId: 'two',
        expectedIdleGeneration: 7,
      );
      expect(identical(first, second), isTrue);
      host.resumeGate!.complete();
      await first;
      expect(host.resumes, 1);
    },
  );
}
