// Captures the actual recovery controls when project discovery fails.
// The session and failed catalog are synthetic; no live server is contacted.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _FailedCatalog extends CaptureRepository {
  @override
  Future<List<WorkspaceProject>> listProjects() async =>
      throw const ProductException('The project service did not respond.');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('project catalog failure recovery ${light ? 'light' : 'dark'}', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final api = CaptureApi()
        ..busy = {}
        ..sessionsById = {
          'previous-work': Session(
            id: 'previous-work',
            title: 'Continue previous work',
            directory: projectDirectory,
            time: SessionTime(created: 1788768000000, updated: 1788768000000),
          ),
        };
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: api,
        repository: _FailedCatalog(),
      );
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: Scaffold(
              appBar: AppBar(title: const Text('Workspace')),
              body: WorkspaceScreen(controller: controller),
            ),
            boundaryKey: key,
            controller: controller,
            store: controller.store,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Project list unavailable'), findsOneWidget);
        expect(find.text('Continue previous work'), findsOneWidget);
        expect(find.text('Search all sessions').hitTestable(), findsOneWidget);
        expect(find.text('Retry projects').hitTestable(), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/session-recovery/project-error-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });
  }
}
