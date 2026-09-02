import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart' show SessionsTab;
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project-1',
      name: 'OpenCode Mobile',
      directory: '/work/app',
      worktrees: [],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: 'bash', options: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..api = _Api()
    ..repository = _Repository()
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
  controller.sessionsById = {
    'session-1': Session(
      id: 'session-1',
      title: 'New session - 2026-09-02T14:47:06.902Z',
      time: SessionTime(created: 1, updated: 1),
    ),
  };
  controller.busySessions.add('session-1');
  return controller;
}

EventEnvelope _permission() => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': 'request-1',
    'sessionID': 'session-1',
    'permission': 'bash',
    'patterns': ['git status'],
    'metadata': <String, Object?>{},
    'always': <String>[],
  },
);

/// Busy rows animate forever (breathing dot, spinner), so settle by hand.
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('workspace row says Needs you, in the attention tone, while a '
      'permission is pending', (tester) async {
    tester.view.physicalSize = const Size(800, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await _pumpFrames(tester);
    expect(find.textContaining('Working'), findsOneWidget);
    expect(find.textContaining('Needs you'), findsNothing);
    // The quick-ask placeholder loses its timestamp everywhere.
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('session-dismiss-session-1')),
        matching: find.text('New session'),
      ),
      findsOneWidget,
    );
    expect(find.textContaining('2026-09-02T'), findsNothing);

    controller.handleEventForTesting(_permission());
    await _pumpFrames(tester);

    expect(find.textContaining('Working'), findsNothing);
    final subtitle = find.textContaining('Needs you');
    expect(subtitle, findsOneWidget);
    final theme = Theme.of(tester.element(subtitle));
    final span = tester.widget<Text>(subtitle).textSpan! as TextSpan;
    final status = span.children!.first as TextSpan;
    expect(status.text, 'Needs you');
    expect(
      status.style?.color,
      AppTheme.statusColor(theme, AppStatusTone.attention),
    );
    expect(
      find.byKey(const ValueKey('session-attention-icon-session-1')),
      findsOneWidget,
    );
  });

  testWidgets('sessions tab row carries a Needs you chip while a permission '
      'is pending', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: SessionsTab(controller: controller)),
      ),
    );
    await _pumpFrames(tester);
    expect(find.byKey(const Key('session-needs-you-session-1')), findsNothing);
    expect(find.text('New session'), findsOneWidget);

    controller.handleEventForTesting(_permission());
    await _pumpFrames(tester);

    final chip = find.byKey(const Key('session-needs-you-session-1'));
    expect(chip, findsOneWidget);
    expect(
      find.descendant(of: chip, matching: find.text('Needs you')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('session-attention-icon-session-1')),
      findsOneWidget,
    );
  });
}
