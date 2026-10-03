// Census scenes for the ledger part `h-termux`
// (docs/design/ui-ledger/parts/h-termux.json). See tool/capture/census_test.dart.
//
// This phone (one page for OpenCode in Termux and inside the app, with its
// switch, log, add-tools and remove sheets), Running now, Storage on this
// phone, development services, Claude Code on this phone and phone setup v2
// with its Termux host (slice P1.3/P1.5 retired the Termux wizard and the
// in-app steps page). The Termux side is one scripted `oc/termux`
// channel (support/h_termux_fakes.dart); phone setup v2 uses the golden
// scenes (test/support/phone_setup_scenes.dart).
//
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_host_setup.dart'
    show TermuxHostJob;
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxRuntime;
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';
import 'package:opencode_mobile/ui/screens/local_agent_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_customize_sheet.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_processes_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_storage_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/setup_terminal.dart';

import '../../../../test/support/development_service_fakes.dart';
import '../../../../test/support/phone_setup_scenes.dart';
import '../../fixtures.dart';
import '../census_core.dart';
import '../support/h_termux_fakes.dart';

// ---------------------------------------------------------------------------
// Mounting
// ---------------------------------------------------------------------------

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved, this.activeProfileId});

  final List<ServerProfile> saved;
  final String? activeProfileId;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => activeProfileId;

  @override
  Future<void> upsert(ServerProfile profile) async {}

  @override
  Future<void> setActiveId(String? id) async {}
}

final _routes = <String, WidgetBuilder>{
  '/home': (_) => const Scaffold(),
  '/servers': (_) => const Scaffold(),
  '/guide': (_) => const Scaffold(),
};

/// A controller over [profiles] with [active] the saved server in use;
/// connected when [connected].
Future<CaptureController> _controller(
  CensusKit kit, {
  List<ServerProfile> profiles = const [],
  String? active,
  bool connected = false,
  String version = '1.18.29',
}) async {
  final store = _Store(
    prefs: await kit.prefs(),
    saved: profiles,
    activeProfileId: active,
  );
  final controller = CaptureController(store);
  if (connected) {
    controller
      ..api = CaptureApi()
      ..repository = CaptureRepository()
      ..status = StreamStatus.connected
      ..directory = projectDirectory
      ..version = version;
  }
  kit.onDispose(controller.dispose);
  return controller;
}

/// Installs [fake] on the Termux channel and a loopback probe that answers.
void _phone(CensusKit kit, TermuxFake fake) {
  kit.mockChannel('oc/termux', fake.handle);
  final previous = termuxRunningServerProbe;
  termuxRunningServerProbe =
      ({required baseUrl, username, password, cancellation}) async =>
          const ServerProbeResult.success('2.0.10');
  kit.onDispose(() => termuxRunningServerProbe = previous);
}

/// Pushes [page] over a plain page (so Back shows, as in the app) and
/// settles.
Future<void> _pushed(
  CensusKit kit,
  Widget page,
  ConnectionController controller, {
  Duration settleFor = const Duration(seconds: 2),
}) async {
  await kit.pumpApp(
    const Scaffold(),
    controller: controller,
    store: controller.store,
    routes: _routes,
    settleFor: const Duration(milliseconds: 200),
  );
  await kit.push(page, settleFor: settleFor);
}

/// This phone for Termux over [fake] with [profiles] saved.
Future<void> _setup(
  CensusKit kit,
  TermuxFake fake, {
  List<ServerProfile> profiles = const [],
  String? active,
  bool connected = false,
}) async {
  _phone(kit, fake);
  final controller = await _controller(
    kit,
    profiles: profiles,
    active: active,
    connected: connected,
  );
  await _pushed(
    kit,
    const ThisPhoneScreen(kind: PhoneHostKind.termux),
    controller,
  );
}

/// Phone setup's progress for the Termux host over [fake]: [job] starts on
/// its own once Termux answers.
Future<void> _termuxHost(
  CensusKit kit,
  TermuxFake fake, {
  TermuxHostJob job = TermuxHostJob.install,
}) async {
  _phone(kit, fake);
  final controller = await _controller(kit);
  await _pushed(
    kit,
    PhoneSetupTermuxScreen(job: job, firstSetup: job == TermuxHostJob.install),
    controller,
  );
}

/// The installed runtime page with OpenCode 1 running and in use.
Future<TermuxFake> _installedRunning(CensusKit kit) async {
  final fake = TermuxFake()
    ..inventory = 'ubuntu=installed\nversion=1.18.29\nruntime=opencode1\n'
    ..status = managerStatus(
      phase: 'ready',
      message: 'OpenCode is ready',
      version: '1.18.29',
      runtime: 'opencode1',
      output: '[oc] authenticated server ready on 127.0.0.1:4096\n',
    );
  final phone = phoneProfileV1();
  await _setup(
    kit,
    fake,
    profiles: [phone, laptopProfile()],
    active: phone.id,
    connected: true,
  );
  kit.expectText('This phone');
  return fake;
}

/// The installed runtime page with OpenCode 2 installed and stopped.
Future<void> _installedStopped(CensusKit kit) async {
  final fake = TermuxFake()
    ..inventory = 'ubuntu=installed\nversion=2.0.10\nruntime=opencode2\n'
    ..status = managerStatus(
      phase: 'stopped',
      message: 'Local server stopped',
      version: '2.0.10',
      runtime: 'opencode2',
      pid: '',
    );
  await _setup(
    kit,
    fake,
    profiles: [laptopProfile(), phoneProfileV2()],
    active: 'laptop',
  );
  kit.expectText('Stopped');
}

// ---------------------------------------------------------------------------
// Local fakes
// ---------------------------------------------------------------------------

/// OpenCode inside the app, for the walkthrough.
class _CensusLinux extends BuiltinLinux {
  _CensusLinux({
    this.installed = false,
    this.openCode = false,
    this.running = false,
  });

  final bool installed;
  final bool openCode;
  final bool running;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    serverRunning: running,
    serverPort: running ? BuiltinLinux.serverPort : null,
    abi: 'arm64-v8a',
    bytesUsed: installed ? 1181116006 : null,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    if (script == BuiltinLinux.versionScript(TermuxRuntime.openCode1) ||
        script == BuiltinLinux.versionScript(TermuxRuntime.openCode2)) {
      return openCode
          ? const BuiltinLinuxRunResult(exitCode: 0, output: '1.18.29\n')
          : const BuiltinLinuxRunResult(exitCode: 1, output: '');
    }
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<String> serverLog({int tailBytes = 32768}) async =>
      running ? _serverLog : '';
}

const _serverLog =
    '''INFO  2026-09-25T09:12:04 +0ms service=default version=1.18.29 args=["serve","--hostname","127.0.0.1","--port","4097"] opencode
INFO  2026-09-25T09:12:05 +812ms service=server listening on http://127.0.0.1:4097
INFO  2026-09-25T09:12:09 +4102ms service=provider init
INFO  2026-09-25T09:12:11 +2011ms service=session id=ses_7f2a created
INFO  2026-09-25T09:13:40 +89002ms service=bus type=message.updated publishing''';

/// This phone for OpenCode inside the app, over [linux].
Future<void> _builtin(CensusKit kit, _CensusLinux linux) async {
  debugPlatformCapabilities = const PlatformCapabilities.android();
  kit.onDispose(() => debugPlatformCapabilities = null);
  final phone = ServerProfile(
    id: 'phone-in-app',
    name: 'This phone',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
    password: 'synthetic-test-secret',
    serverVersion: '1.18.29',
  );
  final controller = await _controller(
    kit,
    profiles: [phone],
    active: phone.id,
  );
  final starter = BuiltinServerStarter(linux: linux);
  kit.onDispose(starter.dispose);
  await _pushed(
    kit,
    ThisPhoneScreen(
      host: InAppPhoneHost(
        linux: linux,
        starter: starter,
        connection: controller,
        pollInterval: null,
      ),
    ),
    controller,
  );
  kit.expectText('This phone');
}

/// Development services over a server that tracks commands, with
/// "Shopfront preview" registered when [registered].
Future<ServicesConnection> _services(
  CensusKit kit, {
  bool registered = true,
  bool supported = true,
}) async {
  final prefs = await kit.prefs();
  final store = _Store(
    prefs: prefs,
    saved: [laptopProfile()],
    activeProfileId: 'laptop',
  );
  final connection = ServicesConnection(store, ServiceRepository())
    ..directory = sampleService.directory
    ..status = StreamStatus.connected
    ..supported = supported;
  kit.onDispose(connection.dispose);
  if (registered) {
    await DevelopmentServiceStore(
      preferences: prefs,
      profileID: 'laptop',
      canWrite: () => true,
    ).save(sampleService);
  }
  await _pushed(
    kit,
    DevelopmentServicesScreen(controller: connection),
    connection,
  );
  kit.expectText('Development services');
  return connection;
}

Future<void> _servicesRunning(CensusKit kit) async {
  await _services(kit);
  await kit.tapText('Start');
  await kit.tap(find.text('Start').last);
  await kit.settle(const Duration(seconds: 2));
  kit.expectText('Logs');
}

/// Claude Code's own page with claude.sh reporting [status].
Future<TermuxFake> _localAgent(
  CensusKit kit,
  String status, {
  String log = '',
}) async {
  final fake = TermuxFake()
    ..claudeStatus = status
    ..claudeLog = log;
  _phone(kit, fake);
  final controller = await _controller(kit);
  await _pushed(kit, LocalAgentScreen(onConnected: () {}), controller);
  return fake;
}

final _claudeReady = claudeStatusLine(
  phase: 'ready',
  installed: true,
  signedIn: 'yes',
);

/// Servers with the phone's OpenCode 2 and a remote server in use, and
/// Claude Code on this phone when [claude] is its status.
Future<void> _servers(
  CensusKit kit, {
  bool running = true,
  String? claude,
}) async {
  final fake = TermuxFake()
    ..inventory = 'ubuntu=installed\nversion=2.0.10\nruntime=opencode2\n'
    ..status = managerStatus(
      phase: running ? 'ready' : 'stopped',
      message: running ? 'OpenCode is ready' : 'Stopped',
      version: '2.0.10',
      runtime: 'opencode2',
      pid: running ? '123' : '',
    )
    ..claudeStatus = claude;
  _phone(kit, fake);
  final controller = await _controller(
    kit,
    profiles: [laptopProfile(), phoneProfileV2()],
    active: 'laptop',
    connected: true,
    version: '2.0.10',
  );
  await kit.pumpApp(
    const ServersScreen(),
    controller: controller,
    store: controller.store,
    routes: _routes,
  );
}

/// A phone setup v2 scene by its golden name, with the engine it installs
/// put back afterwards.
Future<void> _setupScene(CensusKit kit, String name) async {
  final previous = PhoneSetup.engine;
  kit.onDispose(() => PhoneSetup.engine = previous);
  final scene = setupScenes.firstWhere((s) => s.name == name);
  await pumpSetupScene(kit.tester, scene, boundary: kit.boundaryKey);
  await kit.settle(const Duration(seconds: 1));
}

// ---------------------------------------------------------------------------
// The shots
// ---------------------------------------------------------------------------

final hTermuxArea = CensusArea(
  'h-termux',
  shots: [
    // -- This phone (Termux) ----------------------------------------------------
    CensusShot('termux-setup-installed', state: 'running', (kit) async {
      await _installedRunning(kit);
      kit.expectVisible(find.byKey(const ValueKey('this-phone-stop')));
    }),
    CensusShot('termux-setup-installed', state: 'stopped', (kit) async {
      await _installedStopped(kit);
      kit.expectVisible(find.byKey(const ValueKey('this-phone-start')));
    }),
    CensusShot(
      'termux-setup-installed',
      state: 'switch-pending',
      note:
          'A switch from OpenCode 1 to OpenCode 2 beta stopped half way: '
          'Try again and Return lead the status row.',
      (kit) async {
        final fake = TermuxFake()
          ..inventory =
              'ubuntu=installed\nversion=0.0.0-beta-18600\nruntime=opencode2\n'
          ..status = managerStatus(
            phase: 'failed',
            message:
                'OpenCode server did not become authenticated and ready '
                'within 30 seconds',
            version: '0.0.0-beta-18600',
            runtime: 'opencode2',
            extra:
                'switch_return=opencode1\nswitch_previous=opencode1\n'
                'switch_target=opencode2\nswitch_phase=starting\n',
            output: '[oc] Checking the selected runtime\n',
          );
        await _setup(
          kit,
          fake,
          profiles: [laptopProfile(), phoneProfileV1(), phoneProfileV2()],
          active: 'laptop',
        );
        kit.expectVisible(
          find.byKey(const ValueKey('this-phone-switch-retry')),
        );
      },
    ),
    CensusShot('termux-setup-installed', state: 'needs-attention', (kit) async {
      await _setup(
        kit,
        TermuxFake()
          ..inventory = 'ubuntu=installed\nversion=1.18.29\nruntime=opencode1\n'
          ..status = managerStatus(
            phase: 'failed',
            message:
                'OpenCode server did not become authenticated and ready '
                'within 30 seconds',
            version: '1.18.29',
            runtime: 'opencode1',
            output: failedLog,
          ),
        profiles: [laptopProfile(), phoneProfileV1()],
        active: 'laptop',
      );
      kit.expectText('Needs you');
    }),
    CensusShot(
      'termux-setup-installed',
      state: 'installed-open',
      note: 'Running, with "Installed on this phone" unfolded.',
      (kit) async {
        await _installedRunning(kit);
        await kit.scrollTo(
          find.byKey(const ValueKey('this-phone-installed-header')),
        );
        await kit.tapKey('this-phone-installed-header');
        kit.expectText('Linux base');
      },
    ),
    CensusShot(
      'termux-setup-installed',
      state: 'in-app-running',
      note: 'The same page for OpenCode inside the app.',
      (kit) async {
        await _builtin(
          kit,
          _CensusLinux(installed: true, openCode: true, running: true),
        );
        kit.expectText('In the app');
      },
    ),
    CensusShot(
      'termux-setup-installed',
      state: 'in-app-not-set-up',
      note: 'Inside the app, nothing installed yet.',
      (kit) async {
        await _builtin(kit, _CensusLinux());
        kit.expectVisible(find.byKey(const ValueKey('this-phone-set-up')));
      },
    ),
    CensusShot('termux-setup-switch-runtime-sheet', (kit) async {
      await _installedRunning(kit);
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-switch')));
      await kit.tapKey('this-phone-switch');
      kit.expectText('Switch version');
    }),
    CensusShot('termux-setup-installed', state: 'details-log', (kit) async {
      await _builtin(
        kit,
        _CensusLinux(installed: true, openCode: true, running: true),
      );
      // The log is folded under Details, last on This phone (P1.5).
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-details')));
      await kit.tapKey('this-phone-details');
      await kit.realWait();
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-log')));
      kit.expectVisible(find.byKey(const ValueKey('this-phone-log')));
    }),
    CensusShot('this-phone-add-tools-sheet', (kit) async {
      await _installedRunning(kit);
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-add-tools')));
      await kit.tapKey('this-phone-add-tools');
      kit.expectVisible(find.byKey(const ValueKey('local-agent-row')));
    }),
    CensusShot('remove-from-phone-sheet', (kit) async {
      await _builtin(
        kit,
        _CensusLinux(installed: true, openCode: true, running: true),
      );
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-remove')));
      await kit.tapKey('this-phone-remove');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-server-remove-confirm')),
      );
    }),
    CensusShot('remove-from-phone-everything-sheet', (kit) async {
      await _builtin(
        kit,
        _CensusLinux(installed: true, openCode: true, running: true),
      );
      await kit.scrollTo(find.byKey(const ValueKey('this-phone-remove')));
      await kit.tapKey('this-phone-remove');
      await kit.tapKey('phone-server-remove-everything');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-server-delete-everything-confirm')),
      );
    }),

    // -- Phone setup's progress for the Termux host --------------------------
    CensusShot(
      'phone-setup-progress',
      state: 'termux-get-termux',
      note:
          'Termux host: Termux is not installed, a step only the person can do.',
      (kit) async {
        await _termuxHost(kit, TermuxFake()..installed = false);
        kit.expectVisible(find.byKey(const ValueKey('phone-setup-termux-get')));
      },
    ),
    CensusShot(
      'phone-setup-progress',
      state: 'termux-allow',
      note: 'Termux host: the RUN_COMMAND permission is not granted.',
      (kit) async {
        await _termuxHost(kit, TermuxFake()..permissionGranted = false);
        kit.expectVisible(
          find.byKey(const ValueKey('phone-setup-termux-allow')),
        );
      },
    ),
    CensusShot(
      'phone-setup-progress',
      state: 'termux-installing',
      note: 'Termux host: the manager is setting up Ubuntu.',
      (kit) async {
        await _termuxHost(
          kit,
          TermuxFake()
            ..status = managerStatus(
              phase: 'installing_ubuntu',
              message: 'Downloading Ubuntu 24.04',
              output: ubuntuLog,
            ),
        );
        kit.expectText('Setting up OpenCode on this phone');
      },
    ),
    CensusShot(
      'phone-setup-progress',
      state: 'termux-too-old',
      note: 'Termux host: Termux cannot return command results.',
      (kit) async {
        await _termuxHost(kit, TermuxFake()..protocolSupported = false);
        kit.expectVisible(
          find.byKey(const ValueKey('setup-progress-continue')),
        );
      },
    ),
    CensusShot(
      'phone-setup-progress',
      state: 'termux-unsupported',
      note: 'Reached on a desktop build (platform capabilities: Linux).',
      (kit) async {
        debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
        kit.onDispose(() => debugPlatformCapabilities = null);
        final controller = await _controller(kit);
        await _pushed(kit, const PhoneSetupTermuxScreen(), controller);
        kit.expectText('Setup on this phone is Android only');
      },
    ),

    // -- Running now -------------------------------------------------------
    CensusShot('termux-processes', state: 'loaded', (kit) async {
      _phone(kit, TermuxFake());
      await _pushed(kit, const TermuxProcessesScreen(), await _controller(kit));
      kit.expectText('Running now');
      kit.expectVisible(find.byKey(const ValueKey('termux-proc-200')));
    }),
    CensusShot('termux-processes', state: 'empty', (kit) async {
      _phone(kit, TermuxFake()..processes = '[]');
      await _pushed(kit, const TermuxProcessesScreen(), await _controller(kit));
      kit.expectVisible(find.byKey(const ValueKey('termux-procs-empty')));
    }),
    CensusShot('termux-processes-details-sheet', state: 'stoppable', (
      kit,
    ) async {
      _phone(kit, TermuxFake());
      await _pushed(kit, const TermuxProcessesScreen(), await _controller(kit));
      await kit.tapKey('termux-proc-300');
      kit.expectVisible(find.byKey(const ValueKey('termux-procs-details')));
    }),
    CensusShot(
      'termux-processes-details-sheet',
      state: 'orphan',
      note:
          'An orphaned helper (Stop without a second confirm). The protected '
          'form of this sheet ("Protected · open the server controls") is '
          'unreachable: a protected row\'s tap opens On this phone directly '
          '(_ProcessRow.onTap).',
      (kit) async {
        _phone(kit, TermuxFake());
        await _pushed(
          kit,
          const TermuxProcessesScreen(),
          await _controller(kit),
        );
        await kit.tapKey('termux-proc-200');
        kit.expectVisible(find.byKey(const ValueKey('termux-procs-details')));
      },
    ),
    CensusShot('termux-processes-stop-one-sheet', (kit) async {
      _phone(kit, TermuxFake());
      await _pushed(kit, const TermuxProcessesScreen(), await _controller(kit));
      await kit.tapKey('termux-proc-300');
      await kit.tapKey('termux-procs-details-stop');
      kit.expectVisible(
        find.byKey(const ValueKey('termux-procs-confirm-stop')),
      );
    }),
    CensusShot('termux-processes-stop-group-sheet', (kit) async {
      _phone(kit, TermuxFake());
      await _pushed(kit, const TermuxProcessesScreen(), await _controller(kit));
      await kit.tapKey('termux-procs-stop-group-ai_team');
      kit.expectVisible(
        find.byKey(const ValueKey('termux-procs-confirm-stop')),
      );
    }),

    // -- Storage on this phone -----------------------------------------------
    CensusShot('termux-storage', state: 'intro', (kit) async {
      _phone(kit, TermuxFake());
      await _pushed(kit, _storage(), await _controller(kit));
      kit.expectVisible(find.byKey(const ValueKey('termux-storage-intro')));
    }),
    CensusShot('termux-storage', state: 'scanning', (kit) async {
      _phone(
        kit,
        TermuxFake()
          ..storageStatus =
              'state=running\n__OC_TOOLS_LOG__\n$storageScanLog\n',
      );
      await _pushed(kit, _storage(), await _controller(kit));
      kit.expectVisible(find.byKey(const ValueKey('termux-storage-scanning')));
    }),
    CensusShot('termux-storage', state: 'report', (kit) async {
      _phone(kit, TermuxFake()..storageStatus = _storageDone());
      await _pushed(kit, _storage(), await _controller(kit));
      kit.expectVisible(find.byKey(const ValueKey('termux-storage-total')));
    }),
    CensusShot('termux-storage', state: 'category-open', (kit) async {
      _phone(kit, TermuxFake()..storageStatus = _storageDone());
      await _pushed(kit, _storage(), await _controller(kit));
      await kit.tapKey('termux-storage-cat-build_caches');
      kit.expectVisible(
        find.byKey(const ValueKey('termux-storage-clean-build_caches')),
      );
    }),
    CensusShot('termux-storage-clean-sheet', (kit) async {
      _phone(kit, TermuxFake()..storageStatus = _storageDone());
      await _pushed(kit, _storage(), await _controller(kit));
      await kit.tapKey('termux-storage-cat-build_caches');
      await kit.tapKey('termux-storage-clean-build_caches');
      kit.expectVisible(
        find.byKey(const ValueKey('termux-storage-confirm-remove')),
      );
    }),

    // -- Development services ------------------------------------------------
    CensusShot('development-services', state: 'empty', (kit) async {
      await _services(kit, registered: false);
      kit.expectText('Register service');
    }),
    CensusShot('development-services', state: 'registered', (kit) async {
      await _services(kit);
      kit.expectText('Shopfront preview');
    }),
    CensusShot('development-services', state: 'running', (kit) async {
      await _servicesRunning(kit);
    }),
    CensusShot(
      'development-services',
      state: 'unsupported',
      note: 'A server without tracked commands: saving works, Start hides.',
      (kit) async {
        await _services(kit, supported: false);
        kit.expectText('Shopfront preview');
      },
    ),
    CensusShot('development-services-editor-sheet', (kit) async {
      await _services(kit, registered: false);
      await kit.tapText('Register service');
      kit.expectText('Save service');
    }),
    CensusShot('development-services-logs-sheet', (kit) async {
      await _servicesRunning(kit);
      await kit.tapText('Logs');
      await kit.settle(const Duration(seconds: 1));
      kit.expectTextContaining('VITE ready');
    }),
    CensusShot('development-services-confirm-sheet', state: 'start', (
      kit,
    ) async {
      await _services(kit);
      await kit.tapText('Start');
      kit.expectText('Start · Shopfront preview');
    }),
    CensusShot(
      'development-services-confirm-sheet',
      state: 'remove',
      note: 'The destructive form of the same sheet.',
      (kit) async {
        await _services(kit);
        await kit.tapText('Remove configuration');
        kit.expectTextContaining('· Shopfront preview');
      },
    ),

    // -- Servers cards ------------------------------------------------------
    CensusShot(
      'embedded-termux-running-server-entry',
      state: 'running',
      note: 'Host: Servers, with a remote server in use.',
      (kit) async {
        await _servers(kit);
        kit.expectVisible(
          find.byKey(const ValueKey('termux-running-server-menu')),
        );
      },
    ),
    CensusShot(
      'embedded-termux-running-server-entry',
      state: 'menu-open',
      note: 'Host: Servers; the card\'s More menu.',
      (kit) async {
        await _servers(kit);
        await kit.tapKey('termux-running-server-menu');
        kit.expectVisible(
          find.byKey(const ValueKey('termux-running-server-manage')),
        );
      },
    ),
    CensusShot(
      'embedded-termux-running-server-entry',
      state: 'stopped',
      note: 'Host: Servers.',
      (kit) async {
        await _servers(kit, running: false);
        kit.expectVisible(
          find.byKey(const ValueKey('termux-running-server-start')),
        );
      },
    ),
    CensusShot(
      'embedded-setup-terminal',
      state: 'live-output',
      note: 'Host: the Claude Code block while it installs.',
      (kit) async {
        await _localAgent(
          kit,
          claudeStatusLine(
            phase: 'installing',
            busy: true,
            step: 'paseo',
            verb: 'install',
            message: 'Installing the Paseo daemon',
          ),
          log: claudeInstallLog,
        );
        await kit.scrollTo(find.byType(SetupTerminal));
        kit.expectVisible(find.byType(SetupTerminal));
      },
    ),
    CensusShot(
      'embedded-setup-terminal',
      state: 'last-output',
      note: 'Host: the Claude Code block after its install failed.',
      (kit) async {
        await _localAgent(
          kit,
          claudeStatusLine(
            phase: 'failed',
            message: 'npm install failed: ECONNRESET',
            failureKind: 'network',
          ),
          log: '$claudeInstallLog\nnpm error code ECONNRESET\n',
        );
        await kit.scrollTo(find.byType(SetupTerminal));
        kit.expectVisible(find.byType(SetupTerminal));
      },
    ),

    // -- Claude Code on this phone -----------------------------------------
    CensusShot('local-agent-page', state: 'offer', (kit) async {
      await _localAgent(kit, claudeStatusLine(phase: 'absent'));
      kit.expectText('Claude Code');
      kit.expectVisible(find.byKey(const ValueKey('local-agent-set-up')));
    }),
    CensusShot('local-agent-page', state: 'installing', (kit) async {
      await _localAgent(
        kit,
        claudeStatusLine(
          phase: 'installing',
          busy: true,
          step: 'paseo',
          verb: 'install',
          message: 'Installing the Paseo daemon',
        ),
        log: claudeInstallLog,
      );
      kit.expectText('Claude Code');
    }),
    CensusShot('local-agent-page', state: 'sign-in', (kit) async {
      await _localAgent(
        kit,
        claudeStatusLine(phase: 'installed', installed: true),
      );
      kit.expectVisible(find.byKey(const ValueKey('local-agent-sign-in-open')));
    }),
    CensusShot('local-agent-page', state: 'ready', (kit) async {
      await _localAgent(kit, _claudeReady);
      kit.expectVisible(find.byKey(const ValueKey('local-agent-connect')));
    }),
    CensusShot(
      'embedded-local-agent-onboarding-block',
      state: 'needs-ubuntu',
      note: 'Host: the Claude Code page, before phone setup ran.',
      (kit) async {
        await _localAgent(kit, claudeStatusLine(phase: 'needs_ubuntu'));
        kit.expectVisible(
          find.byKey(const ValueKey('local-agent-needs-ubuntu-refresh')),
        );
      },
    ),
    CensusShot(
      'embedded-local-agent-onboarding-block',
      state: 'failed',
      note: 'Host: the Claude Code page, after the install failed.',
      (kit) async {
        await _localAgent(
          kit,
          claudeStatusLine(
            phase: 'failed',
            message: 'npm install failed: ECONNRESET',
            failureKind: 'network',
          ),
          log: '$claudeInstallLog\nnpm error code ECONNRESET\n',
        );
        kit.expectVisible(find.byKey(const ValueKey('local-agent-retry')));
      },
    ),
    CensusShot(
      'embedded-local-agent-onboarding-block',
      state: 'menu-open',
      note: 'Host: the Claude Code page; the block\'s More menu.',
      (kit) async {
        await _localAgent(kit, _claudeReady);
        await kit.tapKey('local-agent-menu');
        kit.expectVisible(find.byKey(const ValueKey('local-agent-remove')));
      },
    ),
    CensusShot('local-agent-project-sheet', (kit) async {
      await _localAgent(kit, _claudeReady);
      await kit.tapKey('local-agent-connect');
      await kit.settle(const Duration(seconds: 1));
      kit.expectText('Choose a project folder');
    }),
    CensusShot('remove-local-agents-confirm-sheet', (kit) async {
      await _localAgent(kit, _claudeReady);
      await kit.tapKey('local-agent-menu');
      await kit.tapKey('local-agent-remove');
      kit.expectText('Remove Claude Code?');
    }),
    CensusShot(
      'embedded-local-agent-server-entry',
      state: 'running',
      note: 'Host: Servers, under the phone\'s OpenCode card.',
      (kit) async {
        await _servers(kit, claude: _claudeReady);
        await kit.scrollTo(
          find.byKey(const ValueKey('local-agent-server-menu')),
        );
        kit.expectVisible(
          find.byKey(const ValueKey('local-agent-server-menu')),
        );
      },
    ),
    CensusShot(
      'embedded-local-agent-server-entry',
      state: 'stopped',
      note: 'Host: Servers.',
      (kit) async {
        await _servers(
          kit,
          claude: claudeStatusLine(
            phase: 'installed',
            installed: true,
            signedIn: 'yes',
          ),
        );
        await kit.scrollTo(
          find.byKey(const ValueKey('local-agent-server-start')),
        );
        kit.expectVisible(
          find.byKey(const ValueKey('local-agent-server-start')),
        );
      },
    ),
    CensusShot(
      'embedded-local-agent-server-entry',
      state: 'menu-open',
      note: 'Host: Servers; the row\'s More menu.',
      (kit) async {
        await _servers(kit, claude: _claudeReady);
        await kit.tapKey('local-agent-server-menu');
        kit.expectVisible(
          find.byKey(const ValueKey('local-agent-server-stop')),
        );
      },
    ),
    CensusShot('stop-local-agents-confirm-sheet', (kit) async {
      await _servers(kit, claude: _claudeReady);
      await kit.tapKey('local-agent-server-menu');
      await kit.tapKey('local-agent-server-stop');
      kit.expectText('Stop Claude Code on this phone?');
    }),
    CensusShot('restart-local-agents-sheet', (kit) async {
      await _servers(kit, claude: _claudeReady);
      await kit.tapKey('local-agent-server-menu');
      await kit.tapKey('local-agent-server-restart');
      kit.expectText('Restart Claude Code on this phone?');
    }),

    // -- Phone setup v2 ------------------------------------------------------
    CensusShot('phone-setup-start', state: 'first-time', (kit) async {
      await _setupScene(kit, 'setup_start');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-setup-start-primary')),
      );
    }),
    CensusShot('phone-setup-start', state: 'stopped-part-way', (kit) async {
      await _setupScene(kit, 'setup_start_stopped');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-setup-start-primary')),
      );
    }),
    CensusShot(
      'phone-setup-start',
      state: 'ready-in-app',
      note: 'OpenCode is already installed inside the app.',
      (kit) async {
        await _setupScene(kit, 'setup_start_ready');
        kit.expectVisible(
          find.byKey(const ValueKey('phone-setup-start-primary')),
        );
      },
    ),
    CensusShot(
      'phone-setup-start',
      state: 'termux-other-ways',
      note: 'OpenCode already runs in Termux; "Other ways" unfolded.',
      (kit) async {
        await _setupScene(kit, 'setup_start_termux');
        kit.expectVisible(
          find.byKey(const ValueKey('phone-setup-start-set-up-here')),
        );
      },
    ),
    CensusShot('phone-setup-customize-sheet', state: 'first-setup', (
      kit,
    ) async {
      await _setupScene(kit, 'setup_customize');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-setup-customize-sheet')),
      );
    }),
    CensusShot(
      'phone-setup-customize-sheet',
      state: 'add-tools',
      note: 'Add mode, from the "This phone" card on Servers.',
      (kit) async {
        await _setupScene(kit, 'phone_card_running');
        await kit.present(
          (context) => showSetupCustomizeSheet(
            context,
            engine: PhoneSetup.engine,
            addMode: true,
          ),
        );
        kit.expectVisible(
          find.byKey(const ValueKey('phone-setup-customize-sheet')),
        );
      },
    ),
    CensusShot('phone-setup-progress', state: 'running', (kit) async {
      await _setupScene(kit, 'setup_progress_running');
      kit.expectVisible(find.byKey(const ValueKey('setup-progress-cancel')));
    }),
    CensusShot('phone-setup-progress', state: 'details-open', (kit) async {
      await _setupScene(kit, 'setup_progress_log');
      kit.expectTextContaining('Setting up git');
    }),
    CensusShot('phone-setup-progress', state: 'failed', (kit) async {
      await _setupScene(kit, 'setup_progress_failed');
      kit.expectVisible(find.byKey(const ValueKey('setup-progress-continue')));
    }),
    CensusShot('phone-setup-progress-stop-sheet', (kit) async {
      await _setupScene(kit, 'setup_progress_running');
      await kit.tapKey('setup-progress-cancel');
      kit.expectVisible(
        find.byKey(const ValueKey('phone-setup-progress-stop-confirm')),
      );
    }),
    CensusShot('phone-setup-ready', (kit) async {
      await _setupScene(kit, 'setup_ready');
      kit.expectVisible(find.byKey(const ValueKey('phone-setup-ready-create')));
    }),
  ],
  notRendered: {
    'embedded-phone-server-card':
        'Rendered as part of Servers and the server switcher (g-servers, '
        'a-shell); this slice only records its Manage and Remove items.',
  },
);

Widget _storage() => TermuxStorageScreen(
  now: () => DateTime.fromMillisecondsSinceEpoch(1788800120000),
);

String _storageDone() =>
    'state=done\n__OC_TOOLS_LOG__\n$storageScanLog\n'
    '__OC_TOOLS_JSON__\n${storageReportJson()}\n';
