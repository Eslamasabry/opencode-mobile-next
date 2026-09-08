// Synthetic Workspace evidence; never connects to a server or runs a prompt.
// flutter test --concurrency=1 tool/capture/quiet_workspace_test.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import '../../test/support/setup_capture_preferences.dart';

import 'fixtures.dart';

const _directory = '/home/developer/projects/mobile/shopfront';

class _Repository extends CaptureRepository {
  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project_shopfront',
      name: 'Shopfront',
      directory: _directory,
      worktrees: [],
      updatedAt: 1,
    ),
  ];
}

class _Controller extends CaptureController {
  _Controller(super.store);
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  for (final light in [true, false]) {
    for (final scale in [1.0, 2.0]) {
      for (final empty in [false, true]) {
        testWidgets(
          'Workspace ${empty ? 'empty' : 'recent'} ${light ? 'light' : 'dark'} at ${scale}x',
          (tester) async {
            tester.view.physicalSize = const Size(1170, 2532);
            tester.view.devicePixelRatio = 3;
            tester.platformDispatcher.textScaleFactorTestValue = scale;
            addTearDown(tester.view.resetPhysicalSize);
            addTearDown(tester.view.resetDevicePixelRatio);
            addTearDown(
              tester.platformDispatcher.clearTextScaleFactorTestValue,
            );
            final messenger = TestDefaultBinaryMessengerBinding
                .instance
                .defaultBinaryMessenger;
            messenger.setMockMethodCallHandler(storage, (_) async => null);
            addTearDown(
              () => messenger.setMockMethodCallHandler(storage, null),
            );
            final prefs = await setupCapturePreferences();
            final store = SeededProfileStore(
              prefs: prefs,
              seeded: [
                ServerProfile(
                  id: 'laptop',
                  name: 'Laptop',
                  baseUrl: 'http://localhost',
                ),
              ],
            );
            final controller = _Controller(store)
              ..api = CaptureApi()
              ..repository = _Repository()
              ..status = StreamStatus.connected
              ..directory = _directory
              ..sessionsById = empty
                  ? {}
                  : {
                      'design': Session(
                        id: 'design',
                        title: 'Refine the checkout experience',
                        directory: _directory,
                        time: SessionTime(
                          created:
                              DateTime.now().millisecondsSinceEpoch - 3600000,
                        ),
                      ),
                    }
              ..busySessions = {};
            addTearDown(controller.dispose);
            final boundary = GlobalKey();
            await tester.pumpWidget(
              captureApp(
                home: const HomeScreen(),
                boundaryKey: boundary,
                controller: controller,
                light: light,
              ),
            );
            for (var i = 0; i < 20; i++) {
              await tester.pump(const Duration(milliseconds: 100));
            }
            expect(find.text('Shopfront'), findsOneWidget);
            expect(find.text('Review status unknown'), findsOneWidget);
            expect(find.text('New session'), findsOneWidget);
            expect(tester.getTopLeft(find.text('Shopfront')).dx, 60);
            expect(
              tester.getTopLeft(find.text('Review status unknown')).dx,
              60,
            );
            if (!empty) {
              expect(
                tester
                    .getTopLeft(find.text('Refine the checkout experience'))
                    .dx,
                60,
              );
            }
            expect(
              tester
                  .getTopLeft(find.widgetWithText(FilledButton, 'New session'))
                  .dx,
              16,
            );
            expect(tester.takeException(), isNull);
            final tone = light ? 'light' : 'dark';
            await writePng(
              'docs/qa/workspace-page-2026-09-09/after-${empty ? 'empty' : 'recent'}-$tone-${scale.toInt()}x.png',
              await capturePng(tester, boundary),
            );
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump();
          },
        );
      }
    }
  }
}
