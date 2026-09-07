// Synthetic source captures; no live server traffic or plugin execution.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/mobile_tool_view.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/plugin_command_mappings.dart';
import 'package:opencode_mobile/ui/screens/plugins_screen.dart';
import 'package:opencode_mobile/ui/widgets/mobile_task_view.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _Api extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(pluginInventory: true);
}

class _Repository extends CaptureRepository implements PluginGateway {
  @override
  Future<List<PluginInfo>> listPlugins() async => const [
    PluginInfo(
      id: 'code-review',
      status: PluginStatus.active,
      source: PluginSourceKind.package,
      packageName: '@example/code-review',
      terminalUi: true,
    ),
  ];
  @override
  Future<List<CommandInfo>> listCommands() async => const [
    CommandInfo(name: 'review', subtask: false),
    CommandInfo(name: 'test', subtask: false),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets(
      'personal plugin links and bundled task view ${light ? 'light' : 'dark'}',
      (tester) async {
        tester.view.physicalSize = const Size(1170, 2532);
        tester.view.devicePixelRatio = captureDevicePixelRatio;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final controller = await captureController(
          prefs: await setupCapturePreferences(),
          api: _Api(),
          repository: _Repository(),
        );
        try {
          final profile = controller.profile!;
          final mappings = PluginCommandMappings(
            controller.store.prefs,
            profile.id,
            () => controller.isProfileReadable(profile.id),
          );
          await mappings.set(
            PluginCommandMappings.scope(
              baseUrl: profile.baseUrl,
              username: profile.username,
              directory: controller.directory,
              workspace: controller.workspace,
            ),
            'code-review',
            ['review', 'test'],
          );
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
          expect(find.text('Review /review'), findsOneWidget);
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/plugins/personal-${light ? 'light' : 'dark'}.png',
            await capturePng(tester, key),
          );
          await tester.pumpWidget(
            captureApp(
              home: Scaffold(
                appBar: AppBar(title: const Text('Task list')),
                body: SingleChildScrollView(
                  padding: const EdgeInsets.all(20),
                  child: MobileTaskList(
                    view: MobileTaskView.fromTodos(const [
                      {
                        'content':
                            'Inspect the current workspace and preserve previous sessions',
                        'status': 'completed',
                      },
                      {
                        'content': 'Review recovery controls with the user',
                        'status': 'in_progress',
                      },
                      {
                        'content': 'Run focused checks before committing',
                        'status': 'pending',
                      },
                    ])!,
                  ),
                ),
              ),
              boundaryKey: key,
              controller: controller,
              store: controller.store,
              light: light,
            ),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/plugins/tasks-${light ? 'light' : 'dark'}.png',
            await capturePng(tester, key),
          );
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
        }
      },
    );
  }
}
