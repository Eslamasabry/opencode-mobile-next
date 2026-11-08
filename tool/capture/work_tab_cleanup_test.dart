// Before/after captures for the Work tab cleanup (2026-09-24,
// docs/design/work-tab-cleanup-2026-09-24.md): the Work tab inside the real
// shell at 412x915 dp, dark theme, real fonts, in the states the owner's
// phone screenshots showed.
//
// This file only drives the public shell (HomeScreen, the saved-server card)
// with controller state, so the same file renders the old code and the new:
//
//   flutter test --concurrency=1 --dart-define=WORK_TAB_CAPTURE=before \
//     tool/capture/work_tab_cleanup_test.dart     # on the old commit
//   flutter test --concurrency=1 tool/capture/work_tab_cleanup_test.dart
//
// Output: docs/qa/work-tab-cleanup-2026-09-24/<before|after>-<state>.png
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _prefix = String.fromEnvironment(
  'WORK_TAB_CAPTURE',
  defaultValue: 'after',
);
const _out = 'docs/qa/work-tab-cleanup-2026-09-24';
const _size = Size(412, 915);

const _root = '/root/projects';
const _current = '$_root/FinanceHub3';
const _other = '$_root/FinanceHub';
const _third = '$_root/Tradebook';

final _now = DateTime.now().millisecondsSinceEpoch;
const _minute = 60 * 1000;

Session _session(
  String id,
  String title, {
  String directory = _current,
  required int ago,
  int? idle,
}) => Session(
  id: id,
  title: title,
  directory: directory,
  time: SessionTime(
    created: _now - ago - 30 * _minute,
    updated: _now - ago,
    idle: idle,
  ),
);

/// The phone's projects: a catalog that can be held back to show loading.
class _Repository extends CaptureRepository implements SessionReadStateGateway {
  Completer<void>? holdProjects;

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    await holdProjects?.future;
    return [
      const WorkspaceProject(
        id: 'p-fh3',
        name: 'FinanceHub3',
        directory: _current,
        worktrees: [],
        updatedAt: 3,
      ),
      const WorkspaceProject(
        id: 'p-fh',
        name: 'FinanceHub',
        directory: _other,
        worktrees: [],
        updatedAt: 2,
      ),
      const WorkspaceProject(
        id: 'p-tb',
        name: 'Tradebook',
        directory: _third,
        worktrees: [],
        updatedAt: 1,
      ),
    ];
  }

  @override
  Future<void> viewSession(String sessionID, int idle) async {}
}

class _Controller extends CaptureController {
  _Controller(super.store);

  List<ProfileLocation> recents = const [];
  List<ElsewhereConversation> elsewhere = const [];

  /// Never completes: the restore stays in flight for the frame.
  bool holdSelection = false;

  @override
  List<ProfileLocation> get recentLocations => recents;

  @override
  Future<List<ElsewhereConversation>> conversationsElsewhere({
    int limit = 6,
  }) async => elsewhere;

  @override
  Future<void> refreshSessions() async {}

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> selectInitialLocation({
    String? directory,
    String? workspace,
  }) async {
    if (holdSelection) await Completer<void>().future;
  }

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    if (holdSelection) await Completer<void>().future;
  }
}

Future<_Controller> _controller({
  String? directory = _current,
  StreamStatus status = StreamStatus.connected,
  Map<String, Session> sessions = const {},
  Set<String> busy = const {},
  bool savedLocation = true,
  _Repository? repository,
}) async {
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'phone',
        name: 'This device (Termux)',
        baseUrl: 'http://127.0.0.1:4096',
      ),
    ],
  );
  if (savedLocation) await store.setLocation('phone', directory: _current);
  final controller = _Controller(store)
    ..api = CaptureApi()
    ..repository = repository ?? _Repository()
    ..status = status
    ..directory = directory
    ..sessionsById = Map.of(sessions)
    ..busySessions = Set.of(busy)
    ..recents = const [
      ProfileLocation(directory: _current),
      ProfileLocation(directory: _other),
      ProfileLocation(directory: _third),
    ]
    ..elsewhere = [
      ElsewhereConversation(
        session: _session(
          'e1',
          'building modern financehub',
          directory: _other,
          ago: 62 * _minute,
        ),
        directory: _other,
        running: true,
      ),
      ElsewhereConversation(
        session: _session(
          'e2',
          'Service-led sales research and demo',
          directory: _third,
          ago: 26 * 60 * _minute,
        ),
        directory: _third,
        running: false,
      ),
    ];
  return controller;
}

Map<String, Session> _loadedSessions({int extra = 0}) => {
  'unreviewed': _session(
    'unreviewed',
    'Reconcile the March ledger import',
    ago: 12 * _minute,
    idle: _now - 12 * _minute,
  ),
  'waiting': _session(
    'waiting',
    'Upgrade the charting library',
    ago: 3 * _minute,
  ),
  'busy': _session('busy', 'Add CSV export to reports', ago: 1 * _minute),
  'older': _session(
    'older',
    'Explain the budget rules engine',
    ago: 5 * 60 * _minute,
  ),
  for (var i = 0; i < extra; i++)
    'old-$i': _session(
      'old-$i',
      'Earlier conversation ${i + 1}',
      ago: (26 + i) * 60 * _minute,
    ),
};

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

void _viewport(WidgetTester tester) {
  tester.view.physicalSize = _size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> _shot(
  WidgetTester tester,
  String state,
  _Controller controller, {
  Future<void> Function()? before,
  Widget home = const HomeScreen(initialTab: 0),
}) async {
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      captureApp(home: home, boundaryKey: boundary, controller: controller),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await before?.call();
    expect(tester.takeException(), isNull);
    await writePng(
      '$_out/$_prefix-$state.png',
      await capturePng(tester, boundary, pixelRatio: 1),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('1 restoring the saved project', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller(directory: null)
      ..holdSelection = true
      ..sessionsLoading = true;
    await _shot(tester, '1-restoring', controller);
  });

  testWidgets('2 first load', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final repository = _Repository()..holdProjects = Completer<void>();
    final controller = await _controller(repository: repository)
      ..sessionsLoading = true;
    await _shot(tester, '2-loading', controller);
  });

  testWidgets('3 loaded and empty', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller();
    await _shot(tester, '3-empty', controller);
  });

  testWidgets('4 loaded: needs you, unreviewed, other projects', (
    tester,
  ) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller(
      sessions: _loadedSessions(),
      busy: {'busy'},
    );
    controller.permissions['perm'] = PermissionRequest(
      id: 'perm',
      sessionID: 'waiting',
      permission: 'bash',
      patterns: const ['npm install chart.js@5'],
    );
    await _shot(tester, '4-loaded', controller);
  });

  testWidgets('5 server not answering after 8 s', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller(
      status: StreamStatus.reconnecting,
      sessions: _loadedSessions(),
    );
    await _shot(
      tester,
      '5-not-answering',
      controller,
      before: () async {
        await tester.pump(const Duration(seconds: 9));
        await tester.pump(const Duration(milliseconds: 300));
      },
    );
  });

  testWidgets('6 end of a long list', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller(sessions: _loadedSessions(extra: 8));
    await _shot(
      tester,
      '6-list-end',
      controller,
      before: () async {
        await tester.drag(
          find.byKey(const PageStorageKey<String>('workspace-scroll')),
          const Offset(0, -4000),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 800));
      },
    );
  });

  testWidgets('7 connecting card after 8 s', (tester) async {
    _mockSecureStorage(tester);
    _viewport(tester);
    final controller = await _controller(status: StreamStatus.connecting);
    await _shot(
      tester,
      '7-connecting-card',
      controller,
      home: Scaffold(
        body: SafeArea(
          child: SavedServerConnectionCard(
            profileName: 'This device (Termux)',
            baseUrl: 'http://127.0.0.1:4096',
            error: null,
            attempts: 1,
            onChangeServer: () {},
            onRetry: () {},
            onStartPhoneServer: () {},
          ),
        ),
      ),
      before: () async {
        await tester.pump(const Duration(seconds: 9));
        await tester.pump(const Duration(milliseconds: 300));
      },
    );
  });

  for (final starting in [false, true]) {
    testWidgets(
      starting ? '9 phone server starting' : '8 phone server stopped',
      (tester) async {
        _mockSecureStorage(tester);
        _viewport(tester);
        final controller = await _controller(status: StreamStatus.disconnected);
        await _shot(
          tester,
          starting ? '9-starting-card' : '8-stopped-card',
          controller,
          home: Scaffold(
            body: SafeArea(
              child: SavedServerConnectionCard(
                profileName: 'This device (Termux)',
                baseUrl: 'http://127.0.0.1:4096',
                error:
                    'Cannot reach http://127.0.0.1:4096: '
                    'Connection refused (errno = 111)',
                attempts: 1,
                onChangeServer: () {},
                onRetry: () {},
                onStartPhoneServer: () {},
                startingPhoneServer: starting,
              ),
            ),
          ),
          before: () async {
            await tester.pump(const Duration(milliseconds: 300));
          },
        );
      },
    );
  }
}
