// Scenes for the Servers, "On this phone" and Plugins cleanup
// (docs/design/phone-server-screens-cleanup-2026-09-24.md): the three
// screens the owner showed from his phone, each in the state he saw.
//
// Shared by the goldens (test/goldens/phone_server_screens_golden_test.dart)
// and the before/after renders (tool/capture/phone_server_screens_test.dart).
// The phone's own OpenCode used to run in Termux; a server saved at Termux's
// old address is now an ordinary saved server, so these scenes keep only the
// Servers list and the plugins page.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/server_plugins_section.dart';

import '../../tool/capture/fixtures.dart';
import 'setup_capture_preferences.dart';

/// The scenes, in the order the QA record lists them.
enum PhoneServerScene {
  /// Servers: a server saved at Termux's old address, and a remote server
  /// the app is connected to.
  servers,

  /// Settings › This server › Plugins on this server: seven built in and
  /// one the person added.
  plugins,
}

/// File-name slug of a scene.
String phoneServerSceneName(PhoneServerScene scene) => switch (scene) {
  PhoneServerScene.servers => 'servers_phone',
  PhoneServerScene.plugins => 'plugins_server',
};

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
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

/// The server an older build saved for OpenCode 2 in Termux.
ServerProfile phoneProfile() => ServerProfile(
  id: 'phone',
  name: 'This device',
  baseUrl: 'http://127.0.0.1:4096',
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
  _mockPlatform(tester);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final prefs = await setupCapturePreferences();
  // The first profile is the active one: the server in use.
  final profiles =
      seededProfiles ??
      switch (scene) {
        PhoneServerScene.servers => [_remote(), phoneProfile()],
        PhoneServerScene.plugins => [_remote()],
      };
  final store = SeededProfileStore(prefs: prefs, seeded: profiles);
  final controller = CaptureController(store)
    ..api = scene == PhoneServerScene.plugins ? _PluginApi() : CaptureApi()
    ..repository = scene == PhoneServerScene.plugins
        ? _PluginRepository()
        : CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '2.1.1';
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;

  final home = switch (scene) {
    PhoneServerScene.servers => const ServersScreen(),
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
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugPlatformCapabilities = null;
  };
}
