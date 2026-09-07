// Synthetic captures of the fresh-worktree task launch. No server, provider,
// native bridge or network is used; the "server" is an in-memory fake whose
// create call is completed by hand, and readiness arrives through the
// controller's test-only event injection.
// Run: flutter test --concurrency=1 tool/capture/isolated_task_test.dart
// Writes docs/qa/isolated-task/*.png at 390x844 logical pixels.
// ignore_for_file: invalid_use_of_visible_for_testing_member

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/isolated_task_sheet.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fixtures.dart';

const _outputDir = 'docs/qa/isolated-task';
const _worktreeDirectory = '/home/dev/worktrees/shopfront/checkout-retry';

const _project = WorkspaceProject(
  id: 'project_shopfront',
  name: 'shopfront',
  directory: projectDirectory,
  worktrees: [],
  updatedAt: 1,
);

class _Repository extends CaptureRepository {
  final create = Completer<WorktreeInfo>();

  @override
  Future<List<WorkspaceProject>> listProjects() async => [_project];

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => create.future;

  @override
  Future<WorkspaceProject?> loadCurrentProject() async => _project;
}

class _Controller extends CaptureController {
  _Controller(super.store);

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    if (this.directory != directory || this.workspace != workspace) {
      locationRevision++;
      connectionRevision++;
    }
    this.directory = directory;
    this.workspace = workspace;
    notifyListeners();
  }
}

Future<SharedPreferences> _prefs() async {
  SharedPreferences.setMockInitialValues({});
  return SharedPreferences.getInstance();
}

Future<void> _onPhone(WidgetTester tester, Future<void> Function() body) async {
  tester.view.physicalSize = const Size(1170, 2532);
  tester.view.devicePixelRatio = captureDevicePixelRatio;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  messenger.setMockMethodCallHandler(storage, (_) async => null);
  try {
    await body();
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    messenger.setMockMethodCallHandler(storage, null);
    debugDefaultTargetPlatformOverride = null;
  }
}

Future<void> _settle(WidgetTester tester, double seconds) async {
  final frames = (seconds * 30).round();
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 33));
  }
}

Future<_Controller> _controller(_Repository repository) async {
  final store = SeededProfileStore(
    prefs: await _prefs(),
    seeded: [
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
      ),
    ],
  );
  final api = CaptureApi();
  final controller = _Controller(store)
    ..api = api
    ..repository = repository
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = Set.of(api.busy);
  controller.adoptConnectedProfileForTesting(store.profiles.first);
  return controller;
}

/// Hosts the real sheet behind a button, like the workspace pill does.
Widget _host(_Controller controller) => Scaffold(
  body: Builder(
    builder: (context) => Center(
      child: FilledButton.icon(
        icon: const Icon(Icons.account_tree_outlined),
        label: const Text('Start a task in a fresh worktree'),
        onPressed: () => showIsolatedTaskSheet(
          context,
          controller: controller,
          project: _project,
          readinessTimeout: const Duration(seconds: 3),
        ),
      ),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    ProviderLogo.imageProviderOverride = (_) => null;
  });
  tearDownAll(() => ProviderLogo.imageProviderOverride = null);

  for (final light in [true, false]) {
    final tone = light ? 'light' : 'dark';

    testWidgets('stale sheet $tone', (tester) async {
      await _onPhone(tester, () async {
        tester.view.physicalSize = const Size(960, 2532);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final controller = await _controller(_Repository());
        addTearDown(controller.dispose);
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: _host(controller),
            boundaryKey: key,
            controller: controller,
            light: light,
          ),
        );
        await tester.tap(find.text('Start a task in a fresh worktree'));
        await tester.pumpAndSettle();
        controller.locationRevision++;
        controller.notifyListeners();
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('isolated-task-start')), findsNothing);
        expect(find.textContaining('Close this sheet'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          '$_outputDir/sheet-stale-$tone.png',
          await capturePng(tester, key),
        );
        await tester.tap(find.byKey(const Key('isolated-task-close')));
        await tester.pumpAndSettle();
      });
    });

    testWidgets('workspace entry $tone', (tester) async {
      await _onPhone(tester, () async {
        final repository = _Repository();
        final controller = await _controller(repository);
        addTearDown(controller.dispose);
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: const HomeScreen(),
            boundaryKey: key,
            controller: controller,
            light: light,
          ),
        );
        await _settle(tester, 2);
        expect(
          find.byKey(const ValueKey('workspace-isolated-task')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          '$_outputDir/workspace-$tone.png',
          await capturePng(tester, key),
        );
      });
    });

    testWidgets('sheet states $tone', (tester) async {
      await _onPhone(tester, () async {
        final repository = _Repository();
        final controller = await _controller(repository);
        addTearDown(controller.dispose);
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: _host(controller),
            boundaryKey: key,
            controller: controller,
            light: light,
          ),
        );
        await tester.tap(find.text('Start a task in a fresh worktree'));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const Key('isolated-task-name')),
          'checkout-retry',
        );
        await tester.pump();
        await writePng(
          '$_outputDir/sheet-form-$tone.png',
          await capturePng(tester, key),
        );

        await tester.tap(find.byKey(const Key('isolated-task-start')));
        await tester.pump();
        expect(find.text('Creating the worktree…'), findsOneWidget);
        await _settle(tester, 0.5);
        await writePng(
          '$_outputDir/sheet-creating-$tone.png',
          await capturePng(tester, key),
        );

        repository.create.complete(
          const WorktreeInfo(
            name: 'checkout-retry',
            directory: _worktreeDirectory,
            branch: 'opencode/checkout-retry',
          ),
        );
        await _settle(tester, 0.5);
        expect(
          find.text('checkout-retry was created. OpenCode is preparing it…'),
          findsOneWidget,
        );
        await writePng(
          '$_outputDir/sheet-preparing-$tone.png',
          await capturePng(tester, key),
        );

        // The 3 s readiness wait expires without an event.
        await tester.pump(const Duration(seconds: 3));
        await _settle(tester, 0.3);
        expect(
          find.byKey(const Key('isolated-task-open-anyway')),
          findsOneWidget,
        );
        await writePng(
          '$_outputDir/sheet-unconfirmed-$tone.png',
          await capturePng(tester, key),
        );

        // A late failure keeps the worktree listed; nothing is deleted.
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'worktree.failed',
            directory: _worktreeDirectory,
            project: _project.id,
            properties: const {'message': 'setup: npm install exited 1'},
          ),
        );
        await _settle(tester, 0.3);
        expect(
          find.text('OpenCode could not prepare the worktree.'),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          '$_outputDir/sheet-failed-$tone.png',
          await capturePng(tester, key),
        );
        await tester.tap(find.byKey(const Key('isolated-task-dismiss')));
        await tester.pumpAndSettle();
      });
    });
  }
}
