// Synthetic captures of the real command-preview dialog. No provider, server,
// native bridge, or clipboard is used.
// Run: flutter test --concurrency=1 tool/capture/session_handoff_test.dart
// Writes docs/qa/handoff/{light,dark}.png at 390x844 logical pixels.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/session_command_handoff.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/session_handoff.dart';
import 'package:opencode_mobile/demo/demo_store.dart';

import 'fixtures.dart';

class _HandoffRepository extends CaptureRepository
    implements SessionCommandHandoffGateway {
  @override
  Future<Session> getSessionDetails(String id) async => Session(
    id: 'session-1',
    projectID: 'project-1',
    directory: '/srv/project',
  );

  @override
  SessionCommandHandoff createSessionCommandHandoff({
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  }) => SessionCommandHandoff.openCode1(
    serverURL: 'https://code.example.test',
    sessionID: sessionID,
    directory: directory,
    workspaceID: workspaceID,
    username: username,
  );
}

class _HandoffController extends CaptureController {
  _HandoffController(super.store) {
    repository = _HandoffRepository();
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('handoff ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const storage = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      messenger.setMockMethodCallHandler(storage, (_) async => null);
      var clipboardCalls = 0;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method.startsWith('Clipboard.')) clipboardCalls++;
        return null;
      });
      final store = SeededProfileStore(
        prefs: DemoPreferences(),
        seeded: [
          ServerProfile(
            id: 'capture-handoff',
            name: 'Sample server',
            baseUrl: 'https://code.example.test',
            username: 'opencode',
          ),
        ],
      );
      final controller = _HandoffController(store);
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            boundaryKey: key,
            controller: controller,
            store: store,
            light: light,
            home: Scaffold(
              appBar: AppBar(title: const Text('Welcome message')),
              body: Builder(
                builder: (context) => Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Sample session',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      const Text('/srv/project'),
                      const SizedBox(height: 24),
                      FilledButton.icon(
                        icon: const Icon(Icons.computer_outlined),
                        label: const Text('Continue on computer'),
                        onPressed: () => showSessionHandoff(
                          context,
                          controller: controller,
                          sessionID: 'session-1',
                          projectID: 'project-1',
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Continue on computer'));
        await tester.pumpAndSettle();
        expect(find.text('Copy command'), findsOneWidget);
        expect(tester.takeException(), isNull);
        expect(clipboardCalls, 0);
        await writePng(
          'docs/qa/handoff/${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
        await tester.tap(find.text('Cancel'));
        await tester.pumpAndSettle();
        expect(clipboardCalls, 0);
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        controller.dispose();
        messenger.setMockMethodCallHandler(storage, null);
        messenger.setMockMethodCallHandler(SystemChannels.platform, null);
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}
