// Golden renders of FG6 (Settings and Library with fewer pages): the Settings
// hub and the pages the merges produce, phone 412x915, dark and light, with
// the app's real fonts at DPR 1. The "before" images of the pages these
// replace are in docs/qa/fg6-2026-10-08/before/.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/fg6_fewer_pages_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../external_agent_state_test.dart' show agentCard;
import '../support/setup_capture_preferences.dart';

class _Api extends CaptureApi {
  _Api(this._capabilities);
  final ServerCapabilities _capabilities;
  @override
  ServerCapabilities get capabilities => _capabilities;
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
  @override
  Future<List<McpServerInfo>> listMcpServers() async => const [
    McpServerInfo(name: 'playwright', status: 'connected'),
    McpServerInfo(name: 'github', status: 'needs_auth'),
    McpServerInfo(name: 'postgres', status: 'failed'),
  ];
  @override
  Future<List<McpResourceInfo>> listMcpResources() async => const [
    McpResourceInfo(
      name: 'Schema',
      server: 'postgres',
      uri: 'postgres://db/schema',
    ),
  ];
  @override
  Future<List<CommandInfo>> listCommands() async => const [
    CommandInfo(
      name: 'review',
      description: 'Review the staged changes',
      subtask: false,
    ),
    CommandInfo(
      name: 'init',
      description: 'Create or update AGENTS.md',
      subtask: false,
    ),
    CommandInfo(
      name: 'test',
      description: 'Run the test suite and fix failures',
      subtask: true,
    ),
  ];
  @override
  Future<List<SkillInfo>> listSkills() async => const [];
  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) async => const [];
  @override
  Future<List<String>> listCodingToolIDs() async => const [];
  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() async =>
      const ExperimentalServerCapabilities(backgroundSubagents: false);
}

void _mockPlatform(WidgetTester tester) {
  final messenger = tester.binding.defaultBinaryMessenger;
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  const termux = MethodChannel('oc/termux');
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
  messenger.setMockMethodCallHandler(termux, (call) async => null);
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
    messenger.setMockMethodCallHandler(termux, null);
  });
}

Future<void> _shot(
  WidgetTester tester,
  String name, {
  required bool light,
  required Widget Function(ConnectionController controller) home,
  Size size = const Size(412, 915),
  ServerCapabilities capabilities = ServerCapabilities.allV1,
  bool asset = false,
  Future<void> Function()? act,
}) async {
  _mockPlatform(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
        password: 'synthetic',
      ),
    ],
  );
  final controller = CaptureController(store)
    ..api = _Api(capabilities)
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '1.18.25';
  controller.catalog = sampleCatalog();
  final model = controller.catalog!.models.first;
  controller.selectedModel = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  );
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      captureApp(
        home: home(controller),
        boundaryKey: boundary,
        controller: controller,
        light: light,
        routes: {'/servers': (_) => const Scaffold()},
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    if (asset) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 300)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    }
    await tester.pumpAndSettle();
    if (act != null) {
      await act();
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/fg6_$name.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('hub · $mode', (tester) async {
      await _shot(
        tester,
        'settings_hub_$mode',
        light: light,
        home: (c) => SettingsScreen(controller: c),
        size: const Size(412, 2300),
      );
    });
    testWidgets('Tools, MCP tab · $mode', (tester) async {
      await _shot(
        tester,
        'tools_mcp_$mode',
        light: light,
        home: (c) => CapabilitiesScreen(controller: c),
      );
    });
    testWidgets('Tools, Commands tab · $mode', (tester) async {
      await _shot(
        tester,
        'tools_commands_$mode',
        light: light,
        home: (c) => CapabilitiesScreen(
          controller: c,
          initialSection: ToolsSection.commands,
        ),
      );
    });
    testWidgets('Tools, External agents tab · $mode', (tester) async {
      final prefs = await setupCapturePreferences();
      final agents = ExternalAgentStore(prefs, const FlutterSecureStorage());
      addTearDown(agents.dispose);
      _mockPlatform(tester);
      await agents.add(agentCard, 'fixture-token');
      await _shot(
        tester,
        'tools_external_agents_$mode',
        light: light,
        home: (c) => CapabilitiesScreen(
          controller: c,
          initialSection: ToolsSection.externalAgents,
          externalAgentStore: agents,
        ),
      );
    });
    testWidgets('Tools on Codex · $mode', (tester) async {
      await _shot(
        tester,
        'tools_codex_$mode',
        light: light,
        home: (c) => CapabilitiesScreen(controller: c),
        capabilities: codexServerCapabilities,
      );
    });
  }
}
