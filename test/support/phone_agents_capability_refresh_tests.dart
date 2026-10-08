part of '../phone_agents_controller_test.dart';

class _VersionSocket extends FakePaseoSocket {
  _VersionSocket(this.version);
  final String? version;

  @override
  void send(String message) {
    if ((jsonDecode(message) as Map)['type'] == 'hello') {
      push('status', {
        'status': 'server_info',
        if (version != null) 'version': version,
      });
      return;
    }
    super.send(message);
  }
}

void _phoneCapabilityRefreshTests() {
  for (final version in ['0.9.2', null, '0.9.3']) {
    test(
      'first helper $version refreshes arm64 Claude capability rows',
      () async {
        final w = await _world(null);
        addTearDown(w.controller.dispose);
        w.state
          ..connectOnOpen = true
          ..projectCapabilities = true
          ..socketFactory = (() => _VersionSocket(version))
          ..runtimes = {'claude': _ready('claude')};
        await w.controller.rememberLastUsedProject(_project);
        expect(w.hosts.expand((host) => host.gateways), isEmpty);
        w.state.capabilityReads.clear();
        await w.controller.refreshAgentRows();

        expect(await w.host.architecture(), AgentArchitecture.arm64);
        expect(w.host.gateways.single.transport.connected, isTrue);
        expect(w.host.gateways.single.transport.serverVersion, version);
        expect(w.state.capabilityReads.first.resumeVerified, isFalse);
        final row = w.controller.agentRows.singleWhere(
          (row) => row.id == 'claude',
        );
        expect(row.status, PhoneAgentStatus.ready);
        expect(row.capabilities.resumeVerified, version == '0.9.2');
        expect(row.capabilities.modelList, version == '0.9.2');
        expect(row.capabilities.cancel, version == '0.9.2');
        expect(
          row.resumeLabel,
          version == '0.9.2' ? null : "Can't reopen old chats",
        );
        // One extra inspection only when the handshake adds version evidence.
        expect(w.state.capabilityReads.length, version == null ? 1 : 2);
        expect(w.host.gateways.length, 1);
      },
    );
  }

  test('first helper capability scan rechecks the actual phone gate', () async {
    final w = await _world(null);
    addTearDown(w.controller.dispose);
    w.state
      ..connectOnOpen = true
      ..projectCapabilities = true
      ..runtimes = {'claude': _ready('claude')};
    w.state.socketFactory = () {
      w.state.runtimes['claude'] = const PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        hostAvailable: true,
        signInPhase: AgentSignInPhase.signedIn,
      );
      return _VersionSocket('0.9.2');
    };
    await w.controller.rememberLastUsedProject(_project);
    await w.controller.refreshAgentRows();
    final row = w.controller.agentRows.singleWhere((row) => row.id == 'claude');
    expect(row.status, PhoneAgentStatus.needsQualification);
    expect(row.chatSelectable, isFalse);
    expect(row.capabilities.resumeVerified, isFalse);
  });

  testWidgets(
    'first helper retry publishes resume without another screen read',
    (tester) async {
      final w = await _world(tester);
      try {
        w.state
          ..connectOnOpen = true
          ..projectCapabilities = true
          ..failOpens = 1
          ..runtimes = {'claude': _ready('claude')};
        await w.controller.rememberLastUsedProject(_project);
        await w.controller.refreshAgentRows();
        expect(
          w.controller.agentRows
              .singleWhere((row) => row.id == 'claude')
              .capabilities
              .resumeVerified,
          isFalse,
        );
        await tester.pump(const Duration(seconds: 3));
        for (var i = 0; i < 20; i++) {
          await tester.pump();
        }
        expect(w.host.gateways.single.transport.serverVersion, '0.9.2');
        final row = w.controller.agentRows.singleWhere(
          (row) => row.id == 'claude',
        );
        expect(row.capabilities.resumeVerified, isTrue);
        expect(row.resumeLabel, isNull);
      } finally {
        w.controller.dispose();
        await tester.pump();
      }
    },
  );
}
