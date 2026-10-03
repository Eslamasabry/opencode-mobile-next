// Evidence renders of slice-fix-inbox-status (emulator QA 2026-09-28):
// the Inbox after a run with the server's untitled placeholder failed and
// the app reconnected by itself five times (F3 title, F4). Phone 412x915 and one wide window
// (1280x800), the app's real fonts at DPR 1.
//
// The rows' ages and "as of" times are the wall clock, so these shots are
// evidence only, never a gate:
//   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
//     test/revamp/slice_fix_inbox_status_golden_test.dart
// The images are copied into docs/qa/slice-fix-inbox-status-2026-09-28/.
// Regenerate deliberately, and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart' show StreamStatus;
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/profile_monitor_fixture.dart';
import '../support/stash_memory_vault.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);
const _evidence = bool.fromEnvironment('CAPTURE_EVIDENCE');

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project-1',
      name: 'qa2-project',
      directory: '/work/qa2-project',
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
  _Controller(super.store)
    : super(
        draftAttachmentVault: StashMemoryVault(),
        stashAttachmentVault: StashMemoryVault(),
      );

  bool connected = true;

  @override
  bool get isConnected => connected;
  void poke() => notifyListeners();
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
  @override
  Future<void> revalidateRestoredLocation() async {}
  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}
}

Future<_Controller> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.upsert(
    ServerProfile(
      id: 'phone',
      name: '127.0.0.1',
      baseUrl: 'http://127.0.0.1:4096',
      username: '',
      password: '',
    ),
  );
  await store.setActiveId('phone');
  final controller = _Controller(store)
    ..api = MonitorTestGateway()
    ..repository = _Repository()
    ..directory = '/work/qa2-project'
    ..status = StreamStatus.connected;
  final now = DateTime.now();
  controller.sessionsById = {
    'untitled': Session(
      id: 'untitled',
      title: 'New session - 2026-09-28T19:15:50.446Z',
      directory: '/work/qa2-project',
      time: SessionTime(
        created: 1,
        updated: now
            .subtract(const Duration(minutes: 3))
            .millisecondsSinceEpoch,
      ),
    ),
  };
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.error',
      properties: {
        'sessionID': 'untitled',
        'error': {'name': 'ProviderAuthError'},
      },
    ),
  );
  for (final minutes in [39, 23, 19, 16]) {
    await controller.recordServerAct(
      profileId: 'phone',
      kind: AutomaticActKind.reconnect,
      eventId: 'reconnect-$minutes',
      at: now.subtract(Duration(minutes: minutes)),
    );
  }
  return controller;
}

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
    AutomaticActivityController.resetShared();
    for (final channel in const [
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      MethodChannel('oc/background'),
    ]) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async => null);
    }
  });
  tearDown(AutomaticActivityController.resetShared);

  Widget inbox(_Controller c) => ActivityScreen(controller: c, embedded: true);

  for (final (size, light) in const [(_phone, false), (_wide, true)]) {
    testWidgets('inbox · ${size.width.toInt()}', skip: !_evidence, (
      tester,
    ) async {
      await _shot(
        tester,
        'fix_inbox_status_inbox',
        light: light,
        size: size,
        home: inbox,
      );
    });
  }
}
