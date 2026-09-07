// Synthetic source captures; no live server traffic or plugin execution.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/plugin_command_mappings.dart';
import 'package:opencode_mobile/ui/screens/plugins_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _Api extends CaptureApi {
  int historyReads = 0;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(pluginInventory: true);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    historyReads++;
    return ServerPage(items: await messages(id));
  }
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
        final api = _Api()..busy = {};
        api.messagesHandler = (_) async {
          final now = DateTime.now().millisecondsSinceEpoch;
          return [
            MessageWithParts(
              info: messageInfo('msg_user', 'user', created: now - 2000),
              parts: [
                Part(
                  id: 'prompt',
                  messageID: 'msg_user',
                  type: 'text',
                  text: 'Plan the session recovery work.',
                ),
              ],
            ),
            MessageWithParts(
              info: messageInfo(
                'msg_assistant',
                'assistant',
                created: now - 1000,
                completed: now,
              ),
              parts: [
                Part(
                  id: 'task-plan',
                  messageID: 'msg_assistant',
                  type: 'tool',
                  toolName: 'todowrite',
                  callID: 'call-task-plan',
                  toolState: ToolState(
                    status: 'completed',
                    input: {
                      'todos': [
                        {
                          'content': 'Inspect the current workspace',
                          'status': 'completed',
                        },
                        {
                          'content': 'Review session recovery controls',
                          'status': 'in_progress',
                        },
                        {'content': 'Run focused checks', 'status': 'pending'},
                      ],
                    },
                  ),
                ),
              ],
            ),
          ];
        };
        final controller = await captureController(
          prefs: await setupCapturePreferences(),
          api: api,
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
              home: const ChatScreen(sessionID: checkoutSessionID),
              boundaryKey: key,
              controller: controller,
              store: controller.store,
              light: light,
            ),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          expect(api.historyReads, greaterThan(0));
          expect(find.text('Tasks'), findsOneWidget);
          await tester.tap(find.text('Tasks').hitTestable());
          await tester.pumpAndSettle();
          expect(find.text('Review session recovery controls'), findsOneWidget);
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
