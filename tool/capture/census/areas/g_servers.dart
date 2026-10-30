// Census scenes for the ledger part `g-servers`
// (docs/design/ui-ledger/parts/g-servers.json). See tool/capture/census_test.dart.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/agent_account.dart';
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/domain/profile_monitor.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/state/agent_account.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/state/profile_monitor.dart' show ProfileMonitor;
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/usage_overview.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/agent_account_screen.dart';
import 'package:opencode_mobile/ui/screens/external_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';
import 'package:opencode_mobile/ui/screens/usage_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../test/support/account_fakes.dart';
import '../../../../test/support/profile_monitor_fixture.dart';
import '../../../../test/support/servers_motion_scenes.dart';
import '../../fixtures.dart';
import '../census_core.dart';

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

Widget _rawApp(Widget home, {bool light = false}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: light ? AppTheme.light() : AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

final _demoTime = DateTime.utc(2026, 9, 20, 12);

// ---------------------------------------------------------------------------
// servers / servers-welcome
// ---------------------------------------------------------------------------

List<ServerProfile> _demoServers({bool passwordReentry = false}) => [
  ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
    serverVersion: '1.18.25',
  )..requiresPasswordReentry = passwordReentry,
  ServerProfile(
    id: 'studio',
    name: 'Studio Mac',
    baseUrl: 'https://studio.tail0c1.ts.net',
    flavor: ServerFlavor.v2,
    serverVersion: '2.0.10',
  ),
  ServerProfile(
    id: 'codex-box',
    name: 'Codex box',
    baseUrl: 'wss://codex.example',
    backend: ServerBackend.codex,
    codexDirectory: '/work/shopfront',
  ),
];

Future<CaptureController> _serversController(
  CensusKit kit, {
  bool passwordReentry = false,
}) async {
  final prefs = await kit.prefs();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: _demoServers(passwordReentry: passwordReentry),
  );
  final controller = CaptureController(store)
    ..api = CaptureApi()
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory;
  kit.onDispose(controller.dispose);
  return controller;
}

// ---------------------------------------------------------------------------
// pairing-scanner: only the non-camera recovery states render here.
// ---------------------------------------------------------------------------

class _FakeCamera implements CameraPlatform {
  _FakeCamera({
    this.hasCameraValue = true,
    this.permission = CameraPermission.denied,
  });
  final bool hasCameraValue;
  final CameraPermission permission;
  @override
  Future<bool> hasCamera() async => hasCameraValue;
  @override
  Future<CameraPermission> requestCameraPermission() async => permission;
  @override
  Future<void> openAppSettings() async {}
}

void _withCamera(CensusKit kit, CameraPlatform fake) {
  final previous = cameraPlatform;
  cameraPlatform = fake;
  kit.onDispose(() => cameraPlatform = previous);
}

// ---------------------------------------------------------------------------
// agent-account: the panel is what AgentAccountScreen renders once its
// gateway session is bound (see its own doc comment); driving it directly
// with a fake session avoids standing up a whole connected server.
// ---------------------------------------------------------------------------

Future<AgentAccountController> _agentAccount(
  CensusKit kit, {
  bool signedIn = false,
}) async {
  final session = FakeAccountSession();
  if (signedIn) {
    session.account = fixtureSignedIn;
    session.buckets = [
      AccountRateBucket(
        'Codex',
        AccountRateWindow(32, 300, DateTime.utc(2026, 9, 20, 18)),
        const AccountRateWindow(68, 10080, null),
      ),
    ];
    session.tokens = const AccountTokenUsage(
      lifetimeTokens: 184250,
      peakDailyTokens: 12640,
    );
  }
  final controller = AgentAccountController(session);
  await controller.refresh();
  kit.onDispose(controller.dispose);
  return controller;
}

// ---------------------------------------------------------------------------
// External agents: a small fixed agent card and task, kept local so this
// file never imports outside test/support, tool/capture or its own area.
// ---------------------------------------------------------------------------

const _agentCard = ExternalAgentCard(
  name: 'Palette agent',
  description:
      'Suggests a small accent-color palette for a UI from a short brief.',
  cardUrl: 'https://agent.example/.well-known/agent-card.json',
  endpoint: 'https://agent.example/rpc',
  version: '1.2',
  auth: ExternalAgentAuth.bearer,
  supported: true,
  skills: [
    ExternalAgentSkill(
      'Palette suggestion',
      'Given a short brief, returns a small color palette.',
    ),
  ],
);

const _waitingTask = ExternalTask(
  id: 'task-1',
  contextId: 'ctx-1',
  state: ExternalTaskState.inputRequired,
  statusMessageId: 'question-1',
  parts: [
    ExternalResultPart(
      text: 'Which accent color should the welcome screen use?',
    ),
  ],
);

const _completeTask = ExternalTask(
  id: 'task-1',
  contextId: 'ctx-1',
  state: ExternalTaskState.completed,
  parts: [
    ExternalResultPart(text: 'Use a calm blue (#2F6FED) as the accent.'),
    ExternalResultPart(
      url: 'https://agent.example/palette/shopfront',
      name: 'Full palette',
    ),
  ],
);

class _AgentGateway implements ExternalAgentGateway {
  ExternalTask next = _waitingTask;
  @override
  Future<ExternalAgentCard> discover(String address) async => _agentCard;
  @override
  Future<ExternalTask> send(
    ExternalAgentCard card,
    String? credential,
    String text, {
    ExternalTask? continuation,
  }) async => next;
  @override
  Future<ExternalTask> getTask(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async => next;
  @override
  Future<ExternalTask> cancel(
    ExternalAgentCard card,
    String? credential,
    String id,
  ) async => const ExternalTask(
    id: 'task-1',
    contextId: 'ctx-1',
    state: ExternalTaskState.canceled,
  );
  @override
  void close() {}
}

/// A disconnected [ConnectionController] whose [SharedPreferences] instance
/// an [ExternalAgentStore] can share, so `SharedPreferences.setMockInitialValues`
/// runs exactly once per shot.
Future<({ConnectionController controller, ExternalAgentStore agents})>
_agentSetup(CensusKit kit) async {
  final controller = await kit.disconnected();
  final agents = ExternalAgentStore(
    controller.store.prefs,
    const FlutterSecureStorage(),
  );
  kit.onDispose(agents.dispose);
  return (controller: controller, agents: agents);
}

Future<
  ({
    ConnectionController controller,
    ExternalAgentStore agents,
    ExternalAgentProfile profile,
  })
>
_agentWithTask(CensusKit kit) async {
  final setup = await _agentSetup(kit);
  final profile = await setup.agents.add(_agentCard, 'sk-demo-0000');
  await setup.agents.saveTask(
    profile.id,
    ExternalTaskRecord(
      localId: 'saved-1',
      title: 'Choose a welcome-screen accent color',
      created: DateTime(2026, 9, 20),
      task: _completeTask,
    ),
  );
  return (controller: setup.controller, agents: setup.agents, profile: profile);
}

Future<
  ({
    ConnectionController controller,
    ExternalAgentStore agents,
    ExternalAgentProfile profile,
    ExternalTaskRecord record,
  })
>
_taskSetup(CensusKit kit, {ExternalTask? task, String draft = ''}) async {
  final setup = await _agentSetup(kit);
  final profile = await setup.agents.add(_agentCard, 'sk-demo-0000');
  final record = ExternalTaskRecord(
    localId: 'task-local-1',
    title: 'Choose a welcome-screen accent color',
    created: DateTime(2026, 9, 20),
    task: task,
    draft: draft,
  );
  await setup.agents.saveTask(profile.id, record);
  return (
    controller: setup.controller,
    agents: setup.agents,
    profile: profile,
    record: record,
  );
}

// ---------------------------------------------------------------------------
// Profile monitor
// ---------------------------------------------------------------------------

Future<({ConnectionController controller, ProfileStore store})> _monitorSetup(
  CensusKit kit, {
  required String activeId,
}) async {
  final store = await monitorStore(count: 2);
  await store.setActiveId(activeId);
  final controller = ConnectionController(
    store,
    monitorGatewayFactory: (_) => (
      gateway: MonitorTestGateway(requests: [request(1)]),
      operations: MonitorTestOperations(),
    ),
  );
  controller
    ..api = (CaptureApi()..busy = {})
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory;
  kit.onDispose(controller.dispose);
  return (controller: controller, store: store);
}

// ---------------------------------------------------------------------------
// Provider quota
// ---------------------------------------------------------------------------

class _DemoProfileStore extends ProfileStore {
  _DemoProfileStore(SharedPreferences prefs, this._profile)
    : super(prefs: prefs);
  final ServerProfile _profile;
  @override
  List<ServerProfile> get profiles => [_profile];
  @override
  String? get activeId => _profile.id;
}

// ---------------------------------------------------------------------------
// Usage
// ---------------------------------------------------------------------------

UsageStatistics _usageStats({double cost = 3.42}) {
  final raw =
      jsonDecode(
            File('test/fixtures/api2/session_stats.json').readAsStringSync(),
          )
          as Map<String, dynamic>;
  final data = Map<String, dynamic>.from(raw['data'] as Map)..['cost'] = cost;
  return UsageStatistics.fromJson(data);
}

class _UsageRepo implements ServerOperationsGateway, UsageStatisticsGateway {
  @override
  bool get usageStatisticsSupported => true;
  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async =>
      _usageStats();
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Unexpected census call: ${invocation.memberName}');
}

class _UsageConnection extends ConnectionController {
  _UsageConnection(super.store) {
    repository = _UsageRepo();
  }
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

Future<ConnectionController> _usageController(CensusKit kit) async {
  final prefs = await kit.prefs();
  final profile = ServerProfile(
    id: 'usage-demo',
    name: 'Shopfront server',
    baseUrl: 'https://shopfront.example',
    username: 'opencode',
    password: 'sk-demo-0000',
  );
  final controller = _UsageConnection(_DemoProfileStore(prefs, profile));
  kit.onDispose(controller.dispose);
  return controller;
}

// ---------------------------------------------------------------------------
// Server settings / host management
// ---------------------------------------------------------------------------

class _HealthyApi extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

Future<CaptureController> _serverSettingsController(
  CensusKit kit, {
  String? available,
  String? installed,
}) async {
  final controller = await kit.connected(api: _HealthyApi());
  controller.availableServerVersion = available;
  controller.installedServerVersion = installed;
  return controller;
}

// ---------------------------------------------------------------------------
// The area
// ---------------------------------------------------------------------------

final gServersArea = CensusArea(
  'g-servers',
  shots: [
    // -- servers -------------------------------------------------------
    CensusShot(
      'servers',
      state: 'loaded',
      (kit) async {
        final controller = await _serversController(kit);
        await kit.pumpApp(
          const ServersScreen(),
          controller: controller,
          store: controller.store,
        );
        kit.expectVisible(find.byKey(const ValueKey('servers-add')));
      },
      note:
          'Laptop is the connected server; Studio Mac (OpenCode 2) and a '
          'Codex host are saved alongside it.',
    ),
    CensusShot('servers', state: 'password-needed', (kit) async {
      final controller = await _serversController(kit, passwordReentry: true);
      await kit.pumpApp(
        const ServersScreen(),
        controller: controller,
        store: controller.store,
      );
      kit.expectVisible(find.byKey(const Key('password-reentry-banner')));
    }),

    // -- servers-welcome -------------------------------------------------
    CensusShot('servers-welcome', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.welcome,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('welcome-choice-computer')));
    }),

    // -- servers-remove-server-sheet ---------------------------------------
    CensusShot('servers-remove-server-sheet', (kit) async {
      final controller = await _serversController(kit);
      await kit.pumpApp(
        const ServersScreen(),
        controller: controller,
        store: controller.store,
      );
      await kit.tap(find.byKey(const ValueKey('server-menu-studio')));
      await kit.tapText('Remove');
      kit.expectTextContaining('Remove ');
    }),

    // -- profile-editor: reuse the motion-pass scenes for the add-server
    // flow, which is the same private screen for every entry point.
    CensusShot('profile-editor', state: 'add-opencode', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.addOpenCode,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('server-profile-editor')));
    }, note: 'Default "Add server": OpenCode chosen, the command and pairing.'),
    CensusShot('profile-editor', state: 'add-codex', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.addCodex,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(
        find.byKey(const ValueKey('codex-server-address-field')),
      );
    }),
    CensusShot('profile-editor', state: 'first-run-connect', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.firstRunConnect,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('server-profile-editor')));
    }, note: 'First-run "On my computer" -> OpenCode connect screen.'),
    CensusShot('profile-editor', state: 'failed', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.failed,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('server-test-failure')));
    }),

    // -- profile-editor-discard-sheet ---------------------------------------
    CensusShot('profile-editor-discard-sheet', (kit) async {
      final done = await mountServersMotionScene(
        kit.tester,
        ServersMotionScene.addOpenCode,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tap(find.byKey(const ValueKey('server-backend-codex')));
      await kit.tapTooltip('Close server editor');
      kit.expectText('Discard server changes?');
    }),

    // -- pairing-scanner: non-camera recovery states only -------------------
    CensusShot(
      'pairing-scanner',
      state: 'denied',
      (kit) async {
        _withCamera(kit, _FakeCamera(permission: CameraPermission.denied));
        await kit.pumpRaw(_rawApp(const PairingScannerScreen()));
        kit.expectVisible(find.byKey(const ValueKey('pairing-scanner-denied')));
      },
      note:
          'The camera preview itself needs a real camera; these three '
          'states are the screen\'s whole non-camera surface.',
    ),
    CensusShot('pairing-scanner', state: 'blocked', (kit) async {
      _withCamera(
        kit,
        _FakeCamera(permission: CameraPermission.permanentlyDenied),
      );
      await kit.pumpRaw(_rawApp(const PairingScannerScreen()));
      kit.expectVisible(find.byKey(const ValueKey('pairing-scanner-blocked')));
    }),
    CensusShot('pairing-scanner', state: 'no-camera', (kit) async {
      _withCamera(kit, _FakeCamera(hasCameraValue: false));
      await kit.pumpRaw(_rawApp(const PairingScannerScreen()));
      kit.expectVisible(
        find.byKey(const ValueKey('pairing-scanner-no-camera')),
      );
    }),

    // -- tailscale-setup -------------------------------------------------
    CensusShot('tailscale-setup', state: 'installed', (kit) async {
      kit.mockChannel(
        'oc/tailscale',
        (call) async => call.method == 'check' ? 'installed' : true,
      );
      await kit.pumpRaw(
        _rawApp(
          const TailscaleSetupScreen(
            initialAddress: 'https://workstation.example.ts.net',
          ),
        ),
      );
      kit.expectTextContaining('Tailscale is installed');
    }),
    CensusShot('tailscale-setup', state: 'missing', (kit) async {
      kit.mockChannel(
        'oc/tailscale',
        (call) async => call.method == 'check' ? 'missing' : true,
      );
      await kit.pumpRaw(_rawApp(const TailscaleSetupScreen()));
      kit.expectText('Get official Android app');
    }),

    // -- host-management -------------------------------------------------
    CensusShot('host-management', (kit) async {
      final controller = await kit.connected(api: _HealthyApi());
      controller.version = '2.1.1';
      await kit.pumpApp(
        HostManagementScreen(controller: controller),
        controller: controller,
      );
      kit.expectText('Install and start OpenCode 2');
    }),

    // -- server-settings ---------------------------------------------------
    CensusShot('server-settings', state: 'update-available', (kit) async {
      final controller = await _serverSettingsController(
        kit,
        available: '2.1.1',
      );
      await kit.pumpApp(
        ServerPageScreen(
          controller: controller,
          profileId: controller.profile!.id,
        ),
        controller: controller,
      );
      await kit.settle();
      kit.expectVisible(find.byKey(const Key('server-updates-tile')));
    }),
    CensusShot('server-settings', state: 'pending-restart', (kit) async {
      final controller = await _serverSettingsController(
        kit,
        installed: '2.1.1',
      );
      await kit.pumpApp(
        ServerPageScreen(
          controller: controller,
          profileId: controller.profile!.id,
        ),
        controller: controller,
      );
      await kit.settle();
      kit.expectVisible(find.byKey(const Key('server-updates-tile')));
    }),

    // -- server-settings-upgrade-sheet / restart-dialog ----------------------
    CensusShot('server-settings-upgrade-sheet', (kit) async {
      final controller = await _serverSettingsController(
        kit,
        available: '2.1.1',
      );
      await kit.pumpApp(
        ServerPageScreen(
          controller: controller,
          profileId: controller.profile!.id,
        ),
        controller: controller,
      );
      await kit.settle();
      await kit.tap(find.byKey(const Key('server-updates-tile')));
      kit.expectText('Update remote OpenCode?');
    }),
    CensusShot('server-settings-restart-dialog', (kit) async {
      final controller = await _serverSettingsController(
        kit,
        installed: '2.1.1',
      );
      await kit.pumpApp(
        ServerPageScreen(
          controller: controller,
          profileId: controller.profile!.id,
        ),
        controller: controller,
      );
      await kit.settle();
      await kit.tap(find.byKey(const Key('server-updates-tile')));
      kit.expectText('Restart OpenCode on its host');
    }),

    // -- agent-account -------------------------------------------------
    CensusShot(
      'agent-account',
      state: 'signed-out',
      (kit) async {
        final controller = await _agentAccount(kit);
        await kit.pumpRaw(
          _rawApp(
            AgentAccountPanel(controller: controller, profileName: 'Codex box'),
          ),
        );
        kit.expectText('Ready to sign in');
      },
      note:
          'Renders AgentAccountPanel, the body AgentAccountScreen shows '
          'once its account session is bound.',
    ),
    CensusShot('agent-account', state: 'usage', (kit) async {
      final controller = await _agentAccount(kit, signedIn: true);
      await kit.pumpRaw(
        _rawApp(
          AgentAccountPanel(controller: controller, profileName: 'Codex box'),
        ),
      );
      kit.expectText('5-hour window');
    }),

    // -- external-agents -------------------------------------------------
    CensusShot('external-agents', state: 'empty', (kit) async {
      final setup = await _agentSetup(kit);
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      kit.expectTextContaining('No external agents yet');
    }),
    CensusShot('external-agents', state: 'saved', (kit) async {
      final setup = await _agentSetup(kit);
      await setup.agents.add(_agentCard, 'sk-demo-0000');
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      kit.expectText(_agentCard.name);
    }),
    CensusShot('external-agents', state: 'pending-deletion', (kit) async {
      final setup = await _agentSetup(kit);
      final profile = await setup.agents.add(_agentCard, 'sk-demo-0000');
      kit.mockChannel('plugins.it_nomads.com/flutter_secure_storage', (
        call,
      ) async {
        if (call.method == 'delete') {
          throw PlatformException(code: 'unavailable');
        }
        return switch (call.method) {
          'readAll' => <String, String>{},
          'containsKey' => false,
          _ => null,
        };
      });
      try {
        await setup.agents.delete(profile.id);
      } catch (_) {
        // Expected: the mocked secure-storage delete above always fails.
      }
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      kit.expectText('Try removing again');
    }, note: 'Local deletion left incomplete (secure-storage delete refused).'),

    // -- external-agents-delete-sheet ---------------------------------------
    CensusShot('external-agents-delete-sheet', (kit) async {
      final setup = await _agentSetup(kit);
      final profile = await setup.agents.add(_agentCard, 'sk-demo-0000');
      kit.mockChannel('plugins.it_nomads.com/flutter_secure_storage', (
        call,
      ) async {
        if (call.method == 'delete') {
          throw PlatformException(code: 'unavailable');
        }
        return switch (call.method) {
          'readAll' => <String, String>{},
          'containsKey' => false,
          _ => null,
        };
      });
      try {
        await setup.agents.delete(profile.id);
      } catch (_) {}
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      await kit.tapText('Try removing again');
      kit.expectText('Remove agent');
    }),

    // -- add-agent -------------------------------------------------
    CensusShot('add-agent', state: 'address', (kit) async {
      final setup = await _agentSetup(kit);
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      await kit.tapText('Add agent');
      kit.expectText('Inspect before you connect');
    }),
    CensusShot('add-agent', state: 'inspected', (kit) async {
      final setup = await _agentSetup(kit);
      await kit.pumpApp(
        ExternalAgentsScreen(
          store: setup.agents,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: setup.controller,
        store: setup.controller.store,
      );
      await kit.tapText('Add agent');
      await kit.enterText(
        find.byType(TextField).first,
        'https://agent.example',
      );
      await kit.tapText('Inspect Agent Card');
      kit.expectText('Advertised skills');
    }),

    // -- external-agent-detail -------------------------------------------------
    CensusShot('external-agent-detail', (kit) async {
      final s = await _agentWithTask(kit);
      await kit.pumpApp(
        ExternalAgentDetailScreen(
          store: s.agents,
          profile: s.profile,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      kit.expectText(_agentCard.name);
    }),

    // -- external-agent-detail-input-dialog ---------------------------------
    CensusShot('external-agent-detail-input-dialog', state: 'new-task', (
      kit,
    ) async {
      final s = await _agentWithTask(kit);
      await kit.pumpApp(
        ExternalAgentDetailScreen(
          store: s.agents,
          profile: s.profile,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      await kit.tapText('New task');
      kit.expectText('Task text');
    }),
    CensusShot(
      'external-agent-detail-input-dialog',
      state: 'update-credential',
      (kit) async {
        final s = await _agentWithTask(kit);
        await kit.pumpApp(
          ExternalAgentDetailScreen(
            store: s.agents,
            profile: s.profile,
            gatewayFactory: () => _AgentGateway(),
          ),
          controller: s.controller,
          store: s.controller.store,
        );
        await kit.tapText('Update credential');
        kit.expectText('Agent bearer credential');
      },
    ),

    // -- external-agent-detail-delete-sheet ---------------------------------
    CensusShot('external-agent-detail-delete-sheet', (kit) async {
      final s = await _agentWithTask(kit);
      await kit.pumpApp(
        ExternalAgentDetailScreen(
          store: s.agents,
          profile: s.profile,
          gatewayFactory: () => _AgentGateway(),
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      await kit.tapText('Remove agent');
      kit.expectText('Remove from this phone');
    }),

    // -- external-task -------------------------------------------------
    CensusShot('external-task', state: 'draft', (kit) async {
      final s = await _taskSetup(
        kit,
        draft:
            'Suggest an accent color for the welcome screen that reads '
            'well in sunlight.',
      );
      await kit.pumpApp(
        ExternalTaskScreen(
          store: s.agents,
          profile: s.profile,
          record: s.record,
          gateway: _AgentGateway(),
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      kit.expectText('Not sent');
    }),
    CensusShot('external-task', state: 'input-required', (kit) async {
      final s = await _taskSetup(kit, task: _waitingTask);
      await kit.pumpApp(
        ExternalTaskScreen(
          store: s.agents,
          profile: s.profile,
          record: s.record,
          gateway: _AgentGateway()..next = _waitingTask,
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      kit.expectText('Your input is needed');
    }),
    CensusShot('external-task', state: 'completed', (kit) async {
      final s = await _taskSetup(kit, task: _completeTask);
      await kit.pumpApp(
        ExternalTaskScreen(
          store: s.agents,
          profile: s.profile,
          record: s.record,
          gateway: _AgentGateway()..next = _completeTask,
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      kit.expectText('Completed');
    }),

    // -- external-task-cancel-sheet / forget-sheet ---------------------------
    CensusShot('external-task-cancel-sheet', (kit) async {
      final s = await _taskSetup(kit, task: _waitingTask);
      await kit.pumpApp(
        ExternalTaskScreen(
          store: s.agents,
          profile: s.profile,
          record: s.record,
          gateway: _AgentGateway()..next = _waitingTask,
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      await kit.tapText('Stop task');
      kit.expectText('Ask to stop');
    }),
    CensusShot('external-task-forget-sheet', (kit) async {
      final s = await _taskSetup(kit, task: _completeTask);
      await kit.pumpApp(
        ExternalTaskScreen(
          store: s.agents,
          profile: s.profile,
          record: s.record,
          gateway: _AgentGateway()..next = _completeTask,
        ),
        controller: s.controller,
        store: s.controller.store,
      );
      await kit.tapText('Forget saved task');
      kit.expectText('Remove from this phone');
    }),

    // profile-monitor was removed (slice-close-misc): its checks live in
    // Notifications › Saved servers, which the notifications census covers.

    // -- profile-monitor-switch-server-dialog --------------------------------
    CensusShot('profile-monitor-switch-server-dialog', (kit) async {
      final s = await _monitorSetup(kit, activeId: 'profile-1');
      s.controller.busySessions = {'busy-session'};
      await kit.pumpApp(
        // The dialog opens from an Inbox row; any page can host it.
        const SizedBox.expand(),
        controller: s.controller,
        store: s.store,
      );
      final other = s.store.profiles.firstWhere((p) => p.id == 'profile-2');
      await kit.present(
        (context) => openMonitoredRequest(
          context,
          s.controller,
          MonitoredRoute(
            profileID: 'profile-2',
            requestID: 'request-1',
            sessionID: 'same-session',
            kind: MonitoredRequestKind.permission,
            createdAt: DateTime.now(),
            serverUrl: other.baseUrl,
            sourceIdentity: ProfileMonitor.routeSourceIdentity(other),
          ),
        ),
      );
      kit.expectText('Switch server to review?');
    }),

    // -- embedded-profile-monitor-inbox ---------------------------------
    CensusShot(
      'embedded-profile-monitor-inbox',
      (kit) async {
        final s = await _monitorSetup(kit, activeId: 'profile-2');
        await s.controller.profileMonitor.setEnabled('profile-1', true);
        await s.controller.profileMonitor.refresh();
        await kit.pumpApp(
          const HomeScreen(initialTab: 1),
          controller: s.controller,
          store: s.store,
        );
        await kit.scrollTo(find.text('Saved servers'));
        kit.expectVisible(find.text('Saved servers'));
      },
      note:
          'Host: HomeScreen tab 1 (Inbox/Activity), scrolled to the '
          'embedded saved-servers inbox summary and its pending request row.',
    ),

    // -- usage -------------------------------------------------
    CensusShot('usage', (kit) async {
      final controller = await _usageController(kit);
      final overview = UsageOverview(
        controller,
        clock: () => _demoTime,
        timezoneLoader: () async => 'UTC',
      );
      kit.onDispose(overview.dispose);
      await kit.pumpRaw(
        _rawApp(UsageScreen(controller: controller, overview: overview)),
      );
      kit.expectTextContaining('Activity recorded');
    }),

    // -- usage-budget-dialog / clear-dialog -------------------------
    CensusShot('usage-budget-dialog', (kit) async {
      final controller = await _usageController(kit);
      final overview = UsageOverview(
        controller,
        clock: () => _demoTime,
        timezoneLoader: () async => 'UTC',
      );
      kit.onDispose(overview.dispose);
      await kit.pumpRaw(
        _rawApp(UsageScreen(controller: controller, overview: overview)),
      );
      await kit.settle();
      await kit.tap(find.byKey(const ValueKey('usage-budget-usd')));
      kit.expectText('Set USD budget');
    }),
    CensusShot('usage-budget-clear-dialog', (kit) async {
      final controller = await _usageController(kit);
      final overview = UsageOverview(
        controller,
        clock: () => _demoTime,
        timezoneLoader: () async => 'UTC',
      );
      kit.onDispose(overview.dispose);
      await kit.pumpRaw(
        _rawApp(UsageScreen(controller: controller, overview: overview)),
      );
      await kit.settle();
      await kit.tap(find.byKey(const ValueKey('usage-budget-usd')));
      await kit.tester.enterText(
        find.byKey(const ValueKey('usage-budget-amount')),
        '25',
      );
      await kit.tapText('Save');
      await kit.scrollTo(find.text('Clear both budgets'));
      await kit.tapText('Clear both budgets');
      kit.expectText('Clear budgets');
    }),
  ],
  notRendered: {},
);
