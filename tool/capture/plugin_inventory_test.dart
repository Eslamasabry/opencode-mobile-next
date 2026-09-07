// Synthetic plugin metadata capture only; no plugin code or server requests.
// Run: flutter test --concurrency=1 tool/capture/plugin_inventory_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/plugins_screen.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _PluginApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(pluginInventory: true);
}

class _PluginRepository extends CaptureRepository implements PluginGateway {
  @override
  Future<List<PluginInfo>> listPlugins() async => const [
    PluginInfo(
      id: 'code-review',
      status: PluginStatus.active,
      source: PluginSourceKind.package,
      packageName: '@example/code-review@1.2.0',
      terminalUi: true,
    ),
    PluginInfo(
      id: 'workspace-tools',
      status: PluginStatus.active,
      source: PluginSourceKind.builtin,
      terminalUi: false,
    ),
    PluginInfo(
      id: 'local-helper',
      status: PluginStatus.failed,
      source: PluginSourceKind.local,
      terminalUi: false,
    ),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('plugins ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: _PluginApi(),
        repository: _PluginRepository(),
      );
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: PluginsScreen(controller: controller),
            boundaryKey: key,
            controller: controller,
            store: controller.store,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('code-review'), findsOneWidget);
        expect(find.text('Failed'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/plugins/${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        controller.dispose();
      }
    });
  }
}
