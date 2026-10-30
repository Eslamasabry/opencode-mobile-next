// Scenes for the Servers, "On this phone" and Plugins cleanup
// (docs/design/phone-server-screens-cleanup-2026-09-24.md): the three
// screens the owner showed from his phone, each in the state he saw.
//
// Shared by the goldens (test/goldens/phone_server_screens_golden_test.dart)
// and the before/after renders (tool/capture/phone_server_screens_test.dart).
// It drives public widgets and the Termux channel only, so the same file
// renders the old code and the new.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/server_plugins_section.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'fake_setup_engine.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';

import '../../tool/capture/fixtures.dart';
import 'setup_capture_preferences.dart';

/// The scenes, in the order the QA record lists them.
enum PhoneServerScene {
  /// Servers: the phone's OpenCode 2 running in Termux, and a remote server
  /// the app is connected to.
  servers,

  /// Settings › On this phone with OpenCode 2 running and in use.
  phoneRunning,

  /// The same page with OpenCode installed and stopped.
  phoneStopped,

  /// Settings › This server › Plugins on this server: seven built in and
  /// one the person added.
  plugins,
}

/// File-name slug of a scene.
String phoneServerSceneName(PhoneServerScene scene) => switch (scene) {
  PhoneServerScene.servers => 'servers_phone',
  PhoneServerScene.phoneRunning => 'phone_running',
  PhoneServerScene.phoneStopped => 'phone_stopped',
  PhoneServerScene.plugins => 'plugins_server',
};

const _readyStatus =
    'phase=ready\nmessage=OpenCode is ready\nport=4096\nrunner=proot\n'
    'version=2.1.1\nruntime=opencode2\npid=123\n__OC_SETUP_OUTPUT__\n';
const _stoppedStatus =
    'phase=stopped\nmessage=Stopped\nport=4096\nrunner=proot\n'
    'version=2.1.1\nruntime=opencode2\npid=\n__OC_SETUP_OUTPUT__\n';

/// Twelve processes at 29 % CPU in all, as on the owner's phone.
String _processes() => jsonEncode([
  for (var i = 0; i < 12; i++)
    {
      'pid': 1000 + i,
      'ppid': 1,
      'group': i == 0 ? 'opencode' : 'other',
      'name': i == 0 ? 'opencode' : 'helper$i',
      'cmd': i == 0 ? 'opencode serve' : 'helper',
      'cpu_pct': i == 0 ? 18 : 1,
      'cpu_seconds': 10,
      'rss_kb': 20480,
      'elapsed_s': 600,
      'cwd': '/root/projects/shopfront',
    },
]);

void _mockPlatform(WidgetTester tester, {required bool running}) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const termux = MethodChannel('oc/termux');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  Map<String, Object> result(String stdout) => {
    'stdout': stdout,
    'stderr': '',
    'exitCode': 0,
    'err': -1,
    'errorMessage': '',
  };
  messenger.setMockMethodCallHandler(termux, (call) async {
    switch (call.method) {
      case 'getCapabilities':
        return <String, Object>{
          'installed': true,
          'version': '0.118',
          'serviceAvailable': true,
          'protocolSupported': true,
          'permissionGranted': true,
        };
      case 'runInTermux':
        final script = (call.arguments as Map)['script'] as String;
        if (script.contains('ubuntu=absent')) {
          return result('ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n');
        }
        if (script.contains("printf 'opencode-bridge-ok'")) {
          return result('opencode-bridge-ok');
        }
        if (script.contains(r'exec "$TOOLS" storage-summary')) {
          return result(
            'state=done\ntotal_bytes=46385646387\nscanned_at=1790000000\n',
          );
        }
        if (script.contains(r'exec "$TOOLS" procs-scan')) {
          return result(_processes());
        }
        if (script == TermuxBridge.statusScript() ||
            script.contains('__OC_SETUP_OUTPUT__')) {
          return result(running ? _readyStatus : _stoppedStatus);
        }
        return result('');
      case 'getSigningCertificateSha256':
        return null;
    }
    return true;
  });
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(termux, null);
  });
}

class _PluginApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(pluginInventory: true);
}

class _PluginRepository extends CaptureRepository implements PluginGateway {
  @override
  Future<List<PluginInfo>> listPlugins() async => [
    for (final id in const [
      'opencode.tool.input.repair',
      'opencode.config.worktree',
      'opencode.browser',
      'opencode.config.mcp',
      'opencode.tool.web.fetch',
      'opencode.config.lsp',
      'opencode.snapshot',
    ])
      PluginInfo(
        id: id,
        status: PluginStatus.active,
        source: PluginSourceKind.builtin,
        terminalUi: false,
      ),
    const PluginInfo(
      id: '@acme/opencode-wakatime',
      status: PluginStatus.active,
      source: PluginSourceKind.package,
      packageName: '@acme/opencode-wakatime',
      terminalUi: false,
    ),
  ];

  @override
  Future<List<CommandInfo>> listCommands() async => const [
    CommandInfo(name: 'review', subtask: false),
  ];
}

/// The phone's own server as setup saves it, for OpenCode 2.
ServerProfile phoneProfile() => ServerProfile(
  id: 'phone',
  name: 'This device (Termux)',
  baseUrl: TermuxBridge.managedServerUrl,
  flavor: ServerFlavor.v2,
  serverVersion: '2.1.1',
  password: 'synthetic-phone',
)..username = 'opencode';

ServerProfile _remote() => ServerProfile(
  id: 'studio',
  name: 'Studio Mac',
  baseUrl: 'https://studio.tail0c1.ts.net',
  flavor: ServerFlavor.v2,
  serverVersion: '2.1.1',
  password: 'synthetic',
);

/// The server switcher on the owner's phone (2026-09-25): OpenCode 2 in
/// Termux running and in use, and a remote server saved. Opens the sheet
/// and settles it. Returns what to call when done.
Future<Future<void> Function()> mountPhoneServerSwitcher(
  WidgetTester tester,
) async {
  debugPlatformCapabilities = const PlatformCapabilities.android();
  _mockPlatform(tester, running: true);
  final previousProbe = termuxRunningServerProbe;
  termuxRunningServerProbe =
      ({required baseUrl, username, password, cancellation}) async =>
          const ServerProbeResult.success('2.1.1');
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [phoneProfile(), _remote()],
  );
  final controller = CaptureController(store)
    ..api = CaptureApi()
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '2.1.1';
  await tester.pumpWidget(
    captureApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => showServerSwitcher(context, controller),
            child: const Text('Switch'),
          ),
        ),
      ),
      boundaryKey: GlobalKey(),
      controller: controller,
      light: false,
    ),
  );
  await tester.tap(find.text('Switch'));
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 200));
  }
  return () async {
    termuxRunningServerProbe = previousProbe;
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugPlatformCapabilities = null;
  };
}

/// Mounts [scene] under [boundary] at 412x915 and settles it. Returns what
/// to call once the frame has been captured.
Future<Future<void> Function()> mountPhoneServerScene(
  WidgetTester tester,
  PhoneServerScene scene, {
  required bool light,
  required GlobalKey boundary,
  List<ServerProfile>? seededProfiles,
}) async {
  final phone = scene != PhoneServerScene.plugins;
  if (phone) {
    debugPlatformCapabilities = const PlatformCapabilities.android();
  }
  _mockPlatform(tester, running: scene != PhoneServerScene.phoneStopped);
  final previousProbe = termuxRunningServerProbe;
  termuxRunningServerProbe =
      ({required baseUrl, username, password, cancellation}) async =>
          const ServerProbeResult.success('2.1.1');
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final prefs = await setupCapturePreferences();
  // The first profile is the active one: the server in use.
  final profiles =
      seededProfiles ??
      switch (scene) {
        PhoneServerScene.servers => [_remote(), phoneProfile()],
        PhoneServerScene.phoneRunning ||
        PhoneServerScene.phoneStopped => [phoneProfile()],
        PhoneServerScene.plugins => [_remote()],
      };
  final store = SeededProfileStore(prefs: prefs, seeded: profiles);
  // The installed-tools check must not reach the platform channel.
  final previousTermux = PhoneSetup.termux;
  PhoneSetup.termux = FakeSetupEngine();
  final controller = CaptureController(store)
    ..api = scene == PhoneServerScene.plugins ? _PluginApi() : CaptureApi()
    ..repository = scene == PhoneServerScene.plugins
        ? _PluginRepository()
        : CaptureRepository()
    ..status = scene == PhoneServerScene.phoneStopped
        ? StreamStatus.disconnected
        : StreamStatus.connected
    ..directory = projectDirectory
    ..version = '2.1.1';
  if (scene == PhoneServerScene.phoneStopped) controller.api = null;
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;

  final home = switch (scene) {
    PhoneServerScene.servers => const ServersScreen(),
    PhoneServerScene.phoneRunning || PhoneServerScene.phoneStopped =>
      const ThisPhoneScreen(kind: PhoneHostKind.termux),
    // The server's plugin inventory as a section of a scrolling page, the
    // way "This server" hosts it.
    PhoneServerScene.plugins => Scaffold(
      body: SingleChildScrollView(
        child: ServerPluginsSection(controller: controller),
      ),
    ),
  };
  await tester.pumpWidget(
    captureApp(
      home: home,
      boundaryKey: boundary,
      controller: controller,
      light: light,
      routes: {
        '/home': (_) => const Scaffold(),
        '/servers': (_) => const Scaffold(),
        '/guide': (_) => const Scaffold(),
      },
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 200));
  }
  return () async {
    termuxRunningServerProbe = previousProbe;
    PhoneSetup.termux = previousTermux;
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugPlatformCapabilities = null;
  };
}
