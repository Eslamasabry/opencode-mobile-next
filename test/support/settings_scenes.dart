// Settings-area scenes for the design-standard migration (standard §9 step
// 6): the hub, its sub-screens, the servers list and form, diagnostics,
// About and the old Termux setup screen, each in a fixed synthetic state.
//
// Shared by the goldens (test/goldens/settings_golden_test.dart) and the
// before/after renders (tool/capture/design_standard_settings_test.dart).
// It drives public widgets only, so the same file renders the old code and
// the new one.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitPageRoute;
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../tool/capture/fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import '../../tool/capture/audit/audit_env.dart';
import 'server_editor.dart';
import 'setup_capture_preferences.dart';

/// The scenes, in the order the QA record lists them.
enum SettingsScene {
  hub,
  server,
  notifications,
  appearance,
  privacy,
  diagnostics,
  diagnosticsEmpty,
  about,
  servers,
  addServer,
  addServerFailed,
  automation,
}

/// File-name slug of a scene: `settings_hub`, `servers_list`, ...
String settingsSceneName(SettingsScene scene) => switch (scene) {
  SettingsScene.hub => 'settings_hub',
  SettingsScene.server => 'settings_this_server',
  SettingsScene.notifications => 'settings_notifications',
  SettingsScene.appearance => 'settings_appearance',
  SettingsScene.privacy => 'settings_privacy',
  SettingsScene.diagnostics => 'settings_diagnostics',
  SettingsScene.diagnosticsEmpty => 'settings_diagnostics_empty',
  SettingsScene.about => 'settings_about',
  SettingsScene.servers => 'servers_list',
  SettingsScene.addServer => 'servers_add',
  SettingsScene.addServerFailed => 'servers_add_failed',
  SettingsScene.automation => 'settings_automation_loaded',
};

final _en = lookupAppLocalizations(const Locale('en'));

class _Api extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

/// Unsent work the privacy screen can offer to clear.
class _SettingsController extends CaptureController {
  _SettingsController(super.store);

  @override
  int get totalQueuedPromptCount => 3;
  @override
  int get totalSessionDraftCount => 2;
  @override
  int get queuedPromptBytes => 18 * 1024;
  @override
  bool get queuedPromptStorageReadable => true;
  @override
  int get sessionDraftBytes => 3 * 1024;
}

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  messenger.setMockMethodCallHandler(
    info,
    (call) async => <String, dynamic>{
      'appName': 'OpenCode Mobile',
      'packageName': 'com.opencode.mobile',
      'version': '1.0.44',
      'buildNumber': '52',
      'buildSignature': '',
    },
  );
  // Termux installed, the bridge unlocked, nothing installed in it yet.
  messenger.setMockMethodCallHandler(const MethodChannel('oc/termux'), (
    call,
  ) async {
    Map<String, Object> result(String stdout) => {
      'stdout': stdout,
      'stderr': '',
      'exitCode': 0,
      'err': -1,
      'errorMessage': '',
    };
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
          return result('ubuntu=absent\nversion=\n');
        }
        if (script.contains("printf 'opencode-bridge-ok'")) {
          return result('opencode-bridge-ok');
        }
        return result(
          'phase=idle\nmessage=No setup has been started\nport=4096\n'
          'runner=\nversion=\npid=\n__OC_SETUP_OUTPUT__\n',
        );
      case 'getSigningCertificateSha256':
        return null;
    }
    return true;
  });
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
    messenger.setMockMethodCallHandler(const MethodChannel('oc/termux'), null);
  });
}

Future<_SettingsController> _controller(
  WidgetTester tester, {
  List<ServerProfile>? profiles,
}) async {
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded:
        profiles ??
        [
          ServerProfile(
            id: 'laptop',
            name: 'Laptop',
            baseUrl: 'http://192.168.1.20:4096',
            password: 'synthetic',
          ),
        ],
  );
  final api = _Api();
  final controller = _SettingsController(store)
    ..api = api
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '1.18.25';
  // AUDIT TOOLING (audit/style-coherence): the stored Style, as main.dart has it.
  if (AuditEnv.on) controller.effects.value = AuditEnv.effects;
  controller.catalog = sampleCatalog();
  final model = controller.catalog!.models.first;
  controller.selectedModel = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  );
  controller.selectedAgent = 'build';
  return controller;
}

List<ServerProfile> _savedServers() => [
  ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
    password: 'synthetic',
    serverVersion: '1.18.25',
  ),
  ServerProfile(
    id: 'studio',
    name: 'Studio Mac',
    baseUrl: 'https://studio.tail0c1.ts.net',
    flavor: ServerFlavor.v2,
    serverVersion: '2.0.10',
  ),
  ServerProfile(
    id: 'office',
    name: 'Office build box',
    baseUrl: 'http://10.0.4.12:4096',
  )..requiresPasswordReentry = true,
];

/// Mounts [scene] under [boundary] at 412x915 and settles it. Returns what
/// to call once the frame has been captured.
Future<Future<void> Function()> mountSettingsScene(
  WidgetTester tester,
  SettingsScene scene, {
  required bool light,
  required GlobalKey boundary,
  Size size = const Size(412, 915),
  void Function(dynamic controller)? configure,
  // The theme the app would build from the controller's pack, Accent and
  // light, dark or Saver (main.dart), instead of the plain capture theme.
  bool followAppearance = false,
  // Opened from a page below it, as the app opens it, so its Back shows.
  bool pushed = false,
}) async {
  _mockPlatform(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final previousProbe = serverProbe;
  final controller = await _controller(
    tester,
    profiles: switch (scene) {
      SettingsScene.servers ||
      SettingsScene.addServer ||
      SettingsScene.addServerFailed => _savedServers(),
      _ => null,
    },
  );
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;
  configure?.call(controller);
  // The hub shows them as the badge on Report a problem (P8.2).
  if (scene == SettingsScene.diagnostics || scene == SettingsScene.hub) {
    final base = DateTime(2026, 9, 24, 9, 41, 7);
    controller.diagnostics
      ..record(
        StateError('Connection closed before the reply finished'),
        StackTrace.fromString('#0 SseClient._read (lib/api/sse.dart:212:7)'),
        source: 'sse',
        at: base,
      )
      ..record(
        const FormatException('Unexpected character in tool output'),
        StackTrace.fromString('#0 Part.fromJson (lib/domain/models.dart:88:5)'),
        source: 'flutter',
        at: base.add(const Duration(minutes: 3)),
      );
  }
  final home = switch (scene) {
    SettingsScene.hub => SettingsScreen(controller: controller),
    SettingsScene.server => ServerPageScreen(
      controller: controller,
      profileId: controller.profile!.id,
    ),
    SettingsScene.notifications => NotificationsSettingsScreen(
      controller: controller,
    ),
    SettingsScene.appearance => AppearanceSettingsScreen(
      controller: controller,
    ),
    SettingsScene.privacy => PrivacySettingsScreen(controller: controller),
    SettingsScene.diagnostics || SettingsScene.diagnosticsEmpty =>
      AppDiagnosticsScreen(controller: controller),
    SettingsScene.about => const AboutScreen(),
    // What runs by itself is the last section of the merged page.
    SettingsScene.automation => NotificationsSettingsScreen(
      controller: controller,
    ),
    SettingsScene.servers ||
    SettingsScene.addServer ||
    SettingsScene.addServerFailed => const ServersScreen(),
  };
  ThemeData? theme;
  if (followAppearance) {
    final pack = effectiveThemePack(controller.themePack.value);
    final accent = appearanceAccentById(controller.appearanceAccent.value);
    theme = switch (controller.appearance.value) {
      AppAppearance.light => AppTheme.light(pack, accent?.light),
      AppAppearance.saver => AppTheme.saver(pack, accent?.dark),
      _ => AppTheme.dark(pack, accent?.dark),
    }.copyWith(platform: TargetPlatform.android);
    theme = AppTheme.forLocale(theme, tester.platformDispatcher.locales.first);
  } else {
    theme = null;
  }
  await tester.pumpWidget(
    captureApp(
      home: pushed ? _PushHost(page: home) : home,
      theme: theme,
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
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
  if (pushed) {
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }
  if (scene == SettingsScene.about) {
    // The documents come from the asset bundle, off the fake clock.
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  }
  if (scene == SettingsScene.addServer ||
      scene == SettingsScene.addServerFailed) {
    await tester.tap(find.text(_en.e7SetupAddServer).last);
    await tester.pumpAndSettle();
  }
  if (scene == SettingsScene.addServerFailed) {
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure(
          'Nothing answered at this address. Check that OpenCode is running '
          'and the phone is on the same network.',
          suggestsMissingServer: true,
        );
    // Add server first asks what runs there (P3.9, 3d251f37); the address
    // then waits under "Enter the address instead", and Save & connect
    // checks it and explains the failure (ledger row 15).
    await openServerManualAddress(tester);
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://build.example.net',
    );
    await tester.pump(const Duration(seconds: 1));
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
  }
  return () async {
    serverProbe = previousProbe;
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  };
}

/// A blank first page that opens [page] on top of itself, so the page has a
/// way back as it does in the app.
class _PushHost extends StatefulWidget {
  const _PushHost({required this.page});

  final Widget page;

  @override
  State<_PushHost> createState() => _PushHostState();
}

class _PushHostState extends State<_PushHost> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      Navigator.of(
        context,
      ).push(KitPageRoute<void>(builder: (_) => widget.page));
    });
  }

  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}
