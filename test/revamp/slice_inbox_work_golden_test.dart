// Golden renders of slice-inbox-work (P4.2b + P5.5): the Inbox's one list
// with the connected server's request, another server's request naming
// its server, a failed run here, and the server whose checks are off.
// Phone 412x915 and one wide window
// (1280x800), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_inbox_work_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart' show StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/profile_monitor_fixture.dart';
import '../support/stash_memory_vault.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

class _Gateway extends MonitorTestGateway {
  _Gateway({super.requests});
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [];
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(
    items: [
      Session(
        id: 'same-session',
        title: 'Update the docs site',
        directory: directory,
        workspaceID: workspace,
      ),
    ],
  );
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [
    WorkspaceProject(
      id: 'project-1',
      name: 'app',
      directory: '/work/app',
      worktrees: [],
      updatedAt: 1,
    ),
  ];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: 'bash', options: []);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store, {super.monitorGatewayFactory})
    : super(
        draftAttachmentVault: StashMemoryVault(),
        stashAttachmentVault: StashMemoryVault(),
      );
  void poke() => notifyListeners();

  // The capture's server is always reachable: no wake-up reconciliation.
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> revalidateRestoredLocation() async {}
}

/// Laptop (connected) waits on one permission and had a run fail; Home PC
/// is checked and waits on one; Build box is saved with checks off.
Future<_Controller> _controller() async {
  final store = await monitorStore(count: 3);
  const names = ['Laptop', 'Home PC', 'Build box'];
  for (var i = 0; i < 3; i++) {
    await store.upsert(
      ServerProfile(
        id: 'profile-${i + 1}',
        name: names[i],
        baseUrl: 'https://server${i + 1}.example',
        username: '',
        password: '',
      ),
    );
  }
  await store.setActiveId('profile-1');
  final controller = _Controller(
    store,
    monitorGatewayFactory: (_) => (
      gateway: _Gateway(requests: [request(7)]),
      operations: MonitorTestOperations(),
    ),
  );
  controller
    ..api = _Gateway(requests: [request(1)])
    ..repository = _Repository()
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
  controller.sessionsById = {
    'same-session': Session(
      id: 'same-session',
      title: 'Fix the login flow',
      directory: '/work/app',
      time: SessionTime(created: 1, updated: _ago(3)),
    ),
    'busy-1': Session(
      id: 'busy-1',
      title: 'Refactor the parser',
      directory: '/work/app',
      time: SessionTime(created: 1, updated: _ago(1)),
    ),
    'failed-1': Session(
      id: 'failed-1',
      title: 'Nightly build',
      directory: '/work/app',
      time: SessionTime(created: 1, updated: _ago(12)),
    ),
    'idle-1': Session(
      id: 'idle-1',
      title: 'Write the release notes',
      directory: '/work/app',
      time: SessionTime(created: 1, updated: _ago(90)),
    ),
  };
  controller.busySessions.add('busy-1');
  await controller.refreshPendingPermissions();
  await controller.refreshPendingQuestions();
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.error',
      properties: {
        'sessionID': 'failed-1',
        'error': {'message': 'fixture diagnostic'},
      },
    ),
  );
  await controller.profileMonitor.setRules(
    'profile-2',
    const ProfileNotifyRules(enabled: true),
  );
  await controller.profileMonitor.refresh();
  return controller;
}

int _ago(int minutes) =>
    DateTime.now().subtract(Duration(minutes: minutes)).millisecondsSinceEpoch;

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget Function(_Controller) home,
  Size size = _phone,
  Future<void> Function(_Controller)? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final controller = await _controller();
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      ProviderScope(
        child: RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: Scaffold(body: home(controller)),
          ),
        ),
      ),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    if (then != null) {
      await then(controller);
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    // Disposed inside the test: the monitor's next-check timer goes with it.
    controller.dispose();
    await tester.pump();
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    AutomationPolicyController.resetShared();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  Widget inbox(_Controller c) => ActivityScreen(controller: c, embedded: true);

  testWidgets('inbox one list · phone dark', (tester) async {
    await _shot(tester, 'inbox_work_inbox', light: false, home: inbox);
  });
  testWidgets('inbox one list · wide light', (tester) async {
    await _shot(
      tester,
      'inbox_work_inbox',
      light: true,
      size: _wide,
      home: inbox,
    );
  });
}
