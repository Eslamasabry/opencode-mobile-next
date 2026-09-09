// Workspace tab hierarchy: a session that is blocked on a permission,
// question or form appears once, under "Needs you", above Pinned, Active
// and Recent; the row names the blocker; and the Recent caption's actions
// stay reachable on a narrow phone at large text.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api({this.terminal = true}) : super(baseUrl: 'http://localhost');

  final bool terminal;

  @override
  ServerCapabilities get capabilities => ServerCapabilities(terminal: terminal);

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

class _Repository extends ProductRepository {
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
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  int createCalls = 0;

  @override
  Future<Session> createSession() async {
    createCalls++;
    return Session(id: 'created', directory: directory);
  }

  // Pins are scoped to a server profile; the bare controller has none.
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}
}

Session _session(String id, int updated) => Session(
  id: id,
  title: id,
  directory: '/work/app',
  time: SessionTime(created: 1, updated: updated),
);

PermissionRequest _permission(String sessionID) => PermissionRequest(
  id: 'perm-$sessionID',
  sessionID: sessionID,
  permission: 'bash',
  patterns: const ['git status'],
);

PendingQuestion _question(String sessionID) => PendingQuestion(
  id: 'question-$sessionID',
  sessionID: sessionID,
  prompts: const [
    QuestionPrompt(
      title: 'Deployment',
      question: 'Which target?',
      multiple: false,
      custom: true,
      choices: [QuestionChoice(label: 'Staging', description: 'Test')],
    ),
  ],
);

Api2FormInfo _form(String sessionID) => Api2FormInfo(
  id: 'form-$sessionID',
  sessionID: sessionID,
  title: 'Connect to Sentry',
);

/// Five sessions covering every section: two pinned, two busy, one idle.
/// Newest first by `updated` inside each group, matching the sort.
Future<_Controller> _controller({bool terminal = true}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..api = _Api(terminal: terminal)
    ..repository = _Repository()
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
  controller.sessionsById = {
    'pinned-blocked': _session('pinned-blocked', 50),
    'pinned-idle': _session('pinned-idle', 40),
    'busy-blocked': _session('busy-blocked', 30),
    'busy-working': _session('busy-working', 20),
    'recent-idle': _session('recent-idle', 10),
  };
  controller.busySessions.addAll({'busy-blocked', 'busy-working'});
  for (final id in ['pinned-blocked', 'pinned-idle']) {
    await controller.setSessionPinned(
      id,
      true,
      locationRevision: controller.locationRevision,
    );
  }
  return controller;
}

Widget _app(
  ConnectionController controller, {
  double textScale = 1,
  bool dark = false,
  Map<String, WidgetBuilder> routes = const {},
}) => MaterialApp(
  routes: routes,
  theme: dark ? AppTheme.dark() : AppTheme.light(),
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: Scaffold(body: WorkspaceScreen(controller: controller)),
);

/// Busy rows animate forever (breathing dot), so settle by hand.
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Finder _row(String id) => find.byKey(ValueKey('session-dismiss-$id'));

double _top(WidgetTester tester, Finder finder) => tester.getTopLeft(finder).dy;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'empty Workspace has one New session action that opens a session',
    (tester) async {
      tester.view.physicalSize = const Size(320, 760);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.sessionsById.clear();
      controller.busySessions.clear();
      await tester.pumpWidget(
        _app(
          controller,
          textScale: 2,
          routes: {
            '/chat/created': (_) =>
                const Scaffold(body: Text('New session opened')),
          },
        ),
      );
      await _pumpFrames(tester);
      expect(find.text('New session'), findsOneWidget);
      final action = find.widgetWithText(FilledButton, 'New session');
      expect(tester.getSize(action).height, greaterThanOrEqualTo(48));
      expect(tester.getTopLeft(action).dx, 16);
      expect(tester.takeException(), isNull);
      await tester.tap(action);
      await _pumpFrames(tester);
      expect(controller.createCalls, 1);
      expect(find.text('New session opened'), findsOneWidget);
    },
  );

  testWidgets(
    'a two-line session name remains readable and opens that session',
    (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.busySessions.clear();
      controller.sessionsById = {
        'resume': Session(
          id: 'resume',
          title: 'Fix checkout layout',
          directory: '/work/app',
        ),
      };
      await tester.pumpWidget(
        _app(
          controller,
          textScale: 2,
          routes: {
            '/chat/resume': (_) =>
                const Scaffold(body: Text('Existing session opened')),
          },
        ),
      );
      await _pumpFrames(tester);
      final title = find.text('Fix checkout layout');
      expect(tester.widget<Text>(title).maxLines, 2);
      expect(tester.getTopLeft(title).dx, 60);
      expect(tester.getSize(title).height, greaterThan(40));
      expect(tester.takeException(), isNull);
      await tester.tap(title);
      await _pumpFrames(tester);
      expect(find.text('Existing session opened'), findsOneWidget);
      expect(controller.createCalls, 0);
    },
  );

  testWidgets(
    'project path is compact and its complete value can be inspected',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      const path = '/work/app/very-long-project-folder/nested/directory';
      controller.directory = path;
      await tester.pumpWidget(_app(controller));
      await _pumpFrames(tester);
      final project = find.byKey(const ValueKey('current-project-entry'));
      final pathText = find.descendant(of: project, matching: find.text(path));
      expect(tester.widget<Text>(pathText).maxLines, 1);
      await tester.tap(
        find.descendant(of: project, matching: find.text('OpenCode Mobile')),
      );
      await _pumpFrames(tester);
      expect(find.byType(SelectableText), findsOneWidget);
      expect(
        tester.widget<SelectableText>(find.byType(SelectableText)).data,
        path,
      );
      expect(find.text('Switch project'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  for (final width in [320.0, 390.0]) {
    for (final textScale in [1.0, 2.0]) {
      for (final dark in [false, true]) {
        final label =
            '${width.toInt()}px, ${textScale}x text, ${dark ? 'dark' : 'light'}';
        testWidgets('$label: blocked sessions sit once under Needs you, above '
            'Pinned, Active and Recent, and nothing overflows', (tester) async {
          // Tall enough that every section is laid out; width is what the
          // caption row and the menu have to fit.
          tester.view.physicalSize = Size(width, 2400 * textScale);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final controller = await _controller();
          addTearDown(controller.dispose);
          controller.permissions['perm-busy-blocked'] = _permission(
            'busy-blocked',
          );
          controller.questions['question-pinned-blocked'] = _question(
            'pinned-blocked',
          );

          await tester.pumpWidget(
            _app(controller, textScale: textScale, dark: dark),
          );
          await _pumpFrames(tester);
          expect(tester.takeException(), isNull);

          // One "Needs you" section holding both blocked rows, each once.
          expect(
            find.byKey(const ValueKey('workspace-needs-you')),
            findsOneWidget,
          );
          expect(_row('pinned-blocked'), findsOneWidget);
          expect(_row('busy-blocked'), findsOneWidget);
          expect(
            find.byKey(const ValueKey('session-attention-icon-busy-blocked')),
            findsOneWidget,
          );

          // The blocker is named on the row, in the attention tone.
          final permission = find.textContaining('Permission needed');
          expect(permission, findsOneWidget);
          final theme = Theme.of(tester.element(permission));
          final span = tester.widget<Text>(permission).textSpan! as TextSpan;
          expect((span.children!.first as TextSpan).text, 'Permission needed');
          expect(
            (span.children!.first as TextSpan).style?.color,
            AppTheme.statusColor(theme, AppStatusTone.attention),
          );
          expect(find.textContaining('Answer needed'), findsOneWidget);

          // Section order top to bottom.
          final needsYou = _top(tester, find.text('Needs you'));
          final pinned = _top(tester, find.text('Pinned'));
          final active = _top(tester, find.text('Active sessions'));
          final recent = _top(tester, find.text('Recent sessions'));
          expect(needsYou, lessThan(pinned));
          expect(pinned, lessThan(active));
          expect(active, lessThan(recent));
          // Blocked rows are above the first ordinary row, whatever their
          // pin or busy state.
          expect(_top(tester, _row('busy-blocked')), lessThan(pinned));
          expect(_top(tester, _row('pinned-idle')), lessThan(active));
          expect(_top(tester, _row('busy-working')), lessThan(recent));
          expect(_top(tester, _row('recent-idle')), greaterThan(recent));

          // The caption's actions still fit and open a labelled menu.
          expect(
            find.byKey(const ValueKey('search-all-sessions')),
            findsOneWidget,
          );
          await tester.tap(
            find.byKey(const ValueKey('workspace-section-menu')),
          );
          await _pumpFrames(tester);
          expect(tester.takeException(), isNull);
          expect(find.text('Reload recent sessions'), findsOneWidget);
          expect(
            find.byKey(const ValueKey('workspace-terminal')),
            findsOneWidget,
          );
          expect(find.text('Terminal'), findsOneWidget);
        });
      }
    }
  }

  testWidgets('one session with every blocker shows permission, then question, '
      'then form as each is answered', (tester) async {
    tester.view.physicalSize = const Size(390, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    controller.permissions['perm-busy-blocked'] = _permission('busy-blocked');
    controller.questions['question-busy-blocked'] = _question('busy-blocked');
    controller.forms['form-busy-blocked'] = _form('busy-blocked');

    await tester.pumpWidget(_app(controller));
    await _pumpFrames(tester);
    expect(_row('busy-blocked'), findsOneWidget);
    expect(find.textContaining('Permission needed'), findsOneWidget);
    expect(find.textContaining('Answer needed'), findsNothing);
    expect(find.textContaining('Form response needed'), findsNothing);

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.replied',
        properties: {'requestID': 'perm-busy-blocked', 'reply': 'once'},
      ),
    );
    await _pumpFrames(tester);
    expect(find.textContaining('Permission needed'), findsNothing);
    expect(find.textContaining('Answer needed'), findsOneWidget);

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.replied',
        properties: {'requestID': 'question-busy-blocked'},
      ),
    );
    await _pumpFrames(tester);
    expect(find.textContaining('Answer needed'), findsNothing);
    expect(find.textContaining('Form response needed'), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-needs-you')), findsOneWidget);
    // Still one row, still busy underneath: it never fell into Active.
    expect(_row('busy-blocked'), findsOneWidget);
    expect(find.textContaining('Working'), findsOneWidget); // busy-working
  });

  testWidgets('answering the request returns a pinned session to Pinned in '
      'pin order and a busy one to Active', (tester) async {
    tester.view.physicalSize = const Size(390, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await _pumpFrames(tester);
    expect(find.byKey(const ValueKey('workspace-needs-you')), findsNothing);
    final pinnedBefore = _top(tester, _row('pinned-blocked'));
    expect(pinnedBefore, lessThan(_top(tester, _row('pinned-idle'))));

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: {
          'id': 'perm-pinned',
          'sessionID': 'pinned-blocked',
          'permission': 'bash',
          'patterns': ['git status'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: {
          'id': 'perm-busy',
          'sessionID': 'busy-blocked',
          'permission': 'bash',
          'patterns': ['git status'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        },
      ),
    );
    await _pumpFrames(tester);
    expect(find.byKey(const ValueKey('workspace-needs-you')), findsOneWidget);
    expect(find.text('Active sessions'), findsOneWidget); // busy-working
    expect(
      _top(tester, _row('pinned-blocked')),
      lessThan(_top(tester, find.text('Pinned'))),
    );
    expect(
      _top(tester, _row('busy-blocked')),
      lessThan(_top(tester, find.text('Pinned'))),
    );
    // The pin is still a pin: its menu offers Unpin, not Pin.
    await tester.tap(
      find.descendant(
        of: _row('pinned-blocked'),
        matching: find.byType(PopupMenuButton<String>),
      ),
    );
    await _pumpFrames(tester);
    expect(find.text('Unpin'), findsOneWidget);
    // Close the menu without acting: a tap on the barrier.
    await tester.tapAt(const Offset(4, 4));
    await _pumpFrames(tester);
    expect(find.text('Unpin'), findsNothing);
    expect(controller.isSessionPinned('pinned-blocked'), isTrue);

    for (final id in ['perm-pinned', 'perm-busy']) {
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: {'requestID': id, 'reply': 'once'},
        ),
      );
    }
    await _pumpFrames(tester);
    expect(find.byKey(const ValueKey('workspace-needs-you')), findsNothing);
    expect(_row('pinned-blocked'), findsOneWidget);
    expect(_row('busy-blocked'), findsOneWidget);
    // Back under Pinned, first by recency among pins, as before.
    final pinnedLabel = _top(tester, find.text('Pinned'));
    final activeLabel = _top(tester, find.text('Active sessions'));
    expect(_top(tester, _row('pinned-blocked')), greaterThan(pinnedLabel));
    expect(
      _top(tester, _row('pinned-blocked')),
      lessThan(_top(tester, _row('pinned-idle'))),
    );
    expect(_top(tester, _row('pinned-idle')), lessThan(activeLabel));
    expect(_top(tester, _row('busy-blocked')), greaterThan(activeLabel));
    expect(find.textContaining('Working'), findsNWidgets(2));
  });

  testWidgets('the section menu reloads sessions and omits Terminal when the '
      'server has none', (tester) async {
    tester.view.physicalSize = const Size(390, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(terminal: false);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await _pumpFrames(tester);
    // Menu entries exist only while the menu is open, so absence has to be
    // checked with it open.
    await tester.tap(find.byKey(const ValueKey('workspace-section-menu')));
    await _pumpFrames(tester);
    expect(find.text('Reload recent sessions'), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-terminal')), findsNothing);
    expect(find.text('Terminal'), findsNothing);
    await tester.tap(find.text('Reload recent sessions'));
    await _pumpFrames(tester);
    expect(find.text('Reload recent sessions'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
