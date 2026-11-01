part of '../phone_agents_controller_test.dart';

/// An installed agent whose version is behind the app's pin: what an app
/// update leaves behind (lib/state/phone_agents_auto_update.dart).
const _behind = PhoneAgentRuntime(
  agentId: 'kilo',
  installed: true,
  payloadPresent: true,
  hostAvailable: false,
  architectureQualified: false,
  checkRequiredReason: 'agentVersion',
);

AgentRow _kiloRow(_World w) =>
    w.controller.agentRows.singleWhere((row) => row.id == 'kilo');

class _AutoHost extends _ColdStartHost implements PhoneAgentAutoUpdatePort {
  _AutoHost(super.events, super.profileId, super.state);
  @override
  bool get autoUpdateCanYield => true;
  @override
  bool get lastAutoUpdateYielded => state.lastYielded;
  @override
  Future<AgentPhoneCheckResult> autoUpdate(String agentId) async {
    state.autoUpdates++;
    return await state.autoUpdateHandler?.call(agentId) ??
        AgentPhoneCheckResult(
          agentId: agentId,
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
  }
}

/// Qualified on its older version, an update owed.
const _runsOld = PhoneAgentRuntime(
  agentId: 'kilo',
  installed: true,
  payloadPresent: true,
  hostAvailable: true,
  architectureQualified: true,
  behindPin: true,
  signInPhase: AgentSignInPhase.signedIn,
);

void _phoneAutoUpdateTests() {
  tearDown(() {
    autoUpdateNetworkReader = () => NetworkBridge().current();
  });

  test('an update set aside for the person\'s work is tried again, not '
      'counted as a failed try, and the row keeps running on the older '
      'version', () async {
    final w = await _world(
      null,
      livenessSupported: true,
      autoUpdateSupported: true,
    );
    addTearDown(w.controller.dispose);
    w.state.runtimes = {'kilo': _runsOld};
    w.state.autoUpdateHandler = (id) async {
      if (w.state.autoUpdates == 1) {
        w.state.lastYielded = true;
        return AgentPhoneCheckResult(
          agentId: id,
          architecture: AgentArchitecture.arm64,
          passed: false,
          completed: const [],
          failure: AgentHostFailure.stale,
        );
      }
      w.state.lastYielded = false;
      w.state.runtimes['kilo'] = _ready('kilo');
      return AgentPhoneCheckResult(
        agentId: id,
        architecture: AgentArchitecture.arm64,
        passed: true,
        completed: AgentPhoneCheckStep.values,
      );
    };
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(w.state.autoUpdates, 1);
    final row = _kiloRow(w);
    expect(row.status, PhoneAgentStatus.ready, reason: 'still usable');
    expect(row.chatSelectable, isTrue);
    expect(row.autoUpdate, AgentAutoUpdate.waiting, reason: 'owed, not failed');
    expect(
      agentRowLine(AppLocalizationsEn(), row),
      contains('Will update when nothing is running'),
    );
    // Nothing is running any more: the next look starts it again.
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(w.state.autoUpdates, 2);
    expect(_kiloRow(w).behindPin, isFalse);
    expect(_kiloRow(w).autoUpdate, isNull);
  });

  test(
    'a big download waits for Wi-Fi and says so, then starts on it',
    () async {
      final w = await _world(
        null,
        livenessSupported: true,
        autoUpdateSupported: true,
      );
      addTearDown(w.controller.dispose);
      w.state.runtimes = {'kilo': _runsOld};
      autoUpdateNetworkReader = () async => const NetworkReading(
        status: NetworkStatus.online,
        transport: NetworkTransport.mobile,
        metered: true,
      );
      w.state.autoUpdateHandler = (id) async {
        w.state.runtimes['kilo'] = _ready('kilo');
        return AgentPhoneCheckResult(
          agentId: id,
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
      };
      await w.controller.refreshAgentRows();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(w.state.autoUpdates, 0, reason: 'no 80 MB over mobile data');
      final row = _kiloRow(w);
      expect(row.autoUpdate, AgentAutoUpdate.waitingWifi);
      expect(
        agentRowLine(AppLocalizationsEn(), row),
        contains('Will update on Wi-Fi'),
      );
      expect(row.status, PhoneAgentStatus.ready, reason: 'it keeps working');
      autoUpdateNetworkReader = () async => const NetworkReading(
        status: NetworkStatus.online,
        transport: NetworkTransport.wifi,
        metered: false,
      );
      await w.controller.refreshAgentRows();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(w.state.autoUpdates, 1);
      expect(_kiloRow(w).status, PhoneAgentStatus.ready);
      expect(_kiloRow(w).autoUpdate, isNull);
    },
  );

  test('an agent behind the pin updates by itself, says so, and is ready '
      'afterwards without a tap', () async {
    final w = await _world(null, livenessSupported: true);
    addTearDown(w.controller.dispose);
    final l10n = AppLocalizationsEn();
    final release = Completer<void>();
    final seen = <AgentAutoUpdate?>[];
    var runs = 0;
    w.state.runtimes = {'kilo': _behind};
    w.state.selfTestHandler = (id) async {
      runs++;
      seen.add(_kiloRow(w).autoUpdate);
      await release.future;
      w.state.runtimes['kilo'] = _ready('kilo');
      return AgentPhoneCheckResult(
        agentId: id,
        architecture: AgentArchitecture.arm64,
        passed: true,
        completed: AgentPhoneCheckStep.values,
      );
    };
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(Duration.zero);
    expect(runs, 1, reason: 'idle: it starts by itself');
    final row = _kiloRow(w);
    expect(row.autoUpdate, AgentAutoUpdate.updating);
    final pin = AgentCatalog.builtIn.byId('kilo')!.recipe!.version;
    expect(row.autoUpdateVersion, pin);
    final line = agentRowLine(l10n, row);
    expect(line, 'Updating to $pin…');
    expect(line, isNot(contains('required agent version')));
    expect(row.fixAction, isNull, reason: 'the app is already doing it');
    release.complete();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(_kiloRow(w).status, PhoneAgentStatus.ready);
    expect(_kiloRow(w).autoUpdate, isNull);
    expect(seen, [AgentAutoUpdate.updating]);
  });

  test(
    'it waits while anything is running, never shows the "required '
    'version changed" text while it waits, and starts when work ends',
    () async {
      final w = await _world(null, livenessSupported: true);
      addTearDown(w.controller.dispose);
      final l10n = AppLocalizationsEn();
      var runs = 0;
      w.state.helperRunning = true; // a helper with unknown work: not idle
      w.state.runtimes = {'kilo': _behind};
      w.state.selfTestHandler = (id) async {
        runs++;
        w.state.runtimes['kilo'] = _ready('kilo');
        return AgentPhoneCheckResult(
          agentId: id,
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
      };
      await w.controller.refreshAgentRows();
      await Future<void>.delayed(Duration.zero);
      expect(runs, 0);
      final waiting = _kiloRow(w);
      expect(waiting.autoUpdate, AgentAutoUpdate.waiting);
      final line = agentRowLine(l10n, waiting);
      expect(line, contains('when nothing is running'));
      expect(line, isNot(contains('required agent version')));
      w.state.helperRunning = false;
      await w.controller.refreshAgentRows();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(runs, 1);
      expect(_kiloRow(w).status, PhoneAgentStatus.ready);
    },
  );

  test('a failed update is tried once, leaves the old install alone, and '
      'falls back to the row\'s Check', () async {
    final w = await _world(null, livenessSupported: true);
    addTearDown(w.controller.dispose);
    final l10n = AppLocalizationsEn();
    var runs = 0;
    w.state.runtimes = {'kilo': _behind};
    w.state.selfTestHandler = (id) async {
      runs++;
      return AgentPhoneCheckResult(
        agentId: id,
        architecture: AgentArchitecture.arm64,
        passed: false,
        completed: const [],
        failure: AgentHostFailure.unavailable,
      );
    };
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    await w.controller.refreshAgentRows();
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(runs, 1, reason: 'one try per pin per app run');
    final row = _kiloRow(w);
    expect(row.autoUpdate, isNull);
    expect(row.fixAction, PhoneAgentFixAction.runPhoneCheck);
    expect(
      agentRowLine(l10n, row),
      'The required agent version changed. Check this phone again.',
    );
    // Nothing removed the old install: the host still reports it installed.
    expect(w.state.runtimes['kilo']!.installed, isTrue);
    expect(w.events.log.where((e) => e.contains('remove')), isEmpty);
    expect(w.controller.agentPhoneCheck('kilo')!.passed, isFalse);
  });

  test('an agent that was never installed, or needs a person, is left '
      'alone', () async {
    final w = await _world(null, livenessSupported: true);
    addTearDown(w.controller.dispose);
    var runs = 0;
    w.state.runtimes = {
      'kilo': const PhoneAgentRuntime(
        agentId: 'kilo',
        installed: true,
        checkRequiredReason: 'firstCheck',
      ),
    };
    w.state.selfTestHandler = (id) async {
      runs++;
      throw StateError('must not run');
    };
    await w.controller.refreshAgentRows();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(runs, 0);
    expect(_kiloRow(w).autoUpdate, isNull);
  });
}
