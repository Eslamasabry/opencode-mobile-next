import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/manage_project_screen.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _WorkspaceSessionsApi extends OpenCodeApi {
  _WorkspaceSessionsApi() : super(baseUrl: 'http://localhost');

  final deleteCalls = <String>[];
  bool deleted = false;
  Future<ServerPage<Session>> Function(String? cursor)? pageHandler;
  @override
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100}) =>
      pageHandler?.call(cursor) ??
      super.sessionPage(cursor: cursor, limit: limit);

  @override
  Future<List<Session>> sessions() async => deleted
      ? const []
      : [
          Session(
            id: 'session-1',
            title: 'Swipe target',
            time: SessionTime(created: 1, updated: 1),
          ),
        ];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<void> deleteSession(String id) async {
    deleteCalls.add(id);
    deleted = true;
  }
}

class _ProjectsRepository implements ProductRepository {
  Future<List<WorkspaceProject>> Function()? projectsLoader;
  List<WorkspaceProject> projects = const [
    WorkspaceProject(
      id: 'project-1',
      name: 'OpenCode Mobile',
      directory: '/work/app',
      worktrees: ['/work/app-proof'],
      updatedAt: 2,
    ),
    WorkspaceProject(
      id: 'project-2',
      name: 'Backend',
      directory: '/work/backend',
      worktrees: [],
      updatedAt: 1,
    ),
  ];
  String? renamedID;
  String? renamedDirectory;
  String? renamedName;
  final archiveCalls = <String>[];
  List<GlobalSessionResult> globalSessions = const [];

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async => ServerPage(items: globalSessions);

  @override
  Future<Session> getSessionDetails(String id) async =>
      globalSessions.singleWhere((result) => result.session.id == id).session;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<void> archiveSession(String id) async => archiveCalls.add(id);

  @override
  Future<List<WorkspaceProject>> listProjects() async =>
      List.of(projectsLoader == null ? projects : await projectsLoader!());

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) async {
    renamedID = projectID;
    renamedDirectory = projectDirectory;
    renamedName = name;
    final previous = projects.singleWhere((project) => project.id == projectID);
    final updated = WorkspaceProject(
      id: previous.id,
      name: name.isEmpty ? 'app' : name,
      directory: previous.directory,
      worktrees: previous.worktrees,
      updatedAt: previous.updatedAt + 1,
    );
    projects = [
      for (final project in projects)
        if (project.id == projectID) updated else project,
    ];
    return updated;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _ProjectsController extends ConnectionController {
  _ProjectsController(super.store, this.projectsRepository) {
    repository = projectsRepository;
    directory = '/work/app';
  }

  final _ProjectsRepository projectsRepository;
  final locations = <({String? directory, String? workspace})>[];

  @override
  Future<ProductRepository?> prepareActionRepository() async =>
      projectsRepository;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locations.add((directory: directory, workspace: workspace));
    this.directory = directory;
    this.workspace = workspace;
    locationError = null;
    notifyListeners();
  }

  @override
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => selectLocation(directory: directory, workspace: workspace);
}

/// A fresh server with zero projects: no location is selected, so Workspace
/// must ask for a project folder instead of running in the server's own
/// default directory (its home). Sessions may only start once a folder is
/// open.
class _FreshServerController extends _ProjectsController {
  _FreshServerController(super.store, super.projectsRepository) {
    directory = null;
  }

  int createSessionCalls = 0;
  String? createSessionDirectory;
  final probed = <String>[];
  String? probeProblem;

  @override
  Future<String?> probeProjectFolder(String directory) async {
    probed.add(directory);
    return probeProblem;
  }

  @override
  Future<Session> createSession() async {
    createSessionCalls++;
    createSessionDirectory = directory;
    return Session(id: 'session-fresh');
  }

  @override
  Future<void> refreshSessions() async {}
}

Future<_ProjectsController> _controller(_ProjectsRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  return _ProjectsController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    repository,
  );
}

Widget _direct(ProjectsScreen screen, {double textScale = 1}) => MaterialApp(
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: screen,
);

Widget _host(ProjectsScreen screen) => MaterialApp(
  home: Builder(
    builder: (context) => Scaffold(
      body: Center(
        child: FilledButton(
          key: const ValueKey('open-projects'),
          onPressed: () => Navigator.of(
            context,
          ).push(MaterialPageRoute<bool>(builder: (_) => screen)),
          child: const Text('Open'),
        ),
      ),
    ),
  ),
);

void main() {
  testWidgets(
    'archived pager remains reachable through an empty filtered first page',
    (tester) async {
      final api = _WorkspaceSessionsApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [Session(id: 'child', parentID: 'root')],
                nextCursor: 'older',
              )
            : ServerPage(
                items: [
                  Session(
                    id: 'archived',
                    title: 'Older archived chat',
                    time: SessionTime(created: 1, archived: 2),
                  ),
                ],
              );
      final controller = await _controller(_ProjectsRepository())
        ..api = api
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);
      await controller.refreshSessions();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('No recent sessions in loaded results'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Archived sessions'),
        180,
        scrollable: find
            .descendant(
              of: find.byType(CustomScrollView),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      // Move the row above the docked quick-ask control before tapping it.
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -220));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Archived sessions'));
      await tester.pumpAndSettle();
      expect(
        find.text('No archived sessions in loaded results'),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('session-inventory-more')).hitTestable(),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('archived-session-archived')),
        findsOneWidget,
      );
      expect(find.text('Older archived chat'), findsOneWidget);
    },
  );

  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('project browser and rename dialog fit compact large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _direct(
        ProjectsScreen(controller: controller, selectedProjectID: 'project-1'),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Projects'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('project-project-1')),
      200,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('projects-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(find.byKey(const ValueKey('project-project-1')), findsOneWidget);
    expect(tester.takeException(), isNull);

    // The create/open folder entries above the list push the row lower at
    // large text; bring the rename control fully on screen before tapping.
    await tester.ensureVisible(
      find.byKey(const ValueKey('rename-project-project-1')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('rename-project-project-1')));
    await tester.pumpAndSettle();
    expect(find.text('Rename project'), findsOneWidget);
    expect(
      find.text('Clear the name to use the project folder name.'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('project search and reset-name use server project truth', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _direct(
        ProjectsScreen(controller: controller, selectedProjectID: 'project-1'),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('project-search')),
      'backend',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('project-project-1')), findsNothing);
    expect(find.byKey(const ValueKey('project-project-2')), findsOneWidget);

    await tester.enterText(find.byKey(const ValueKey('project-search')), '');
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('rename-project-project-1')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('project-name-input')),
      'app',
    );
    await tester.tap(find.byKey(const ValueKey('confirm-rename-project')));
    await tester.pumpAndSettle();

    expect(repository.renamedID, 'project-1');
    expect(repository.renamedDirectory, '/work/app');
    expect(repository.renamedName, '');
    expect(find.text('app'), findsOneWidget);
  });

  testWidgets('selecting a project opens its exact local directory', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _host(
        ProjectsScreen(controller: controller, selectedProjectID: 'project-1'),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-projects')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('project-project-2')));
    await tester.pumpAndSettle();

    expect(controller.directory, '/work/backend');
    expect(controller.workspace, isNull);
    expect(controller.locations, [
      (directory: '/work/backend', workspace: null),
    ]);
    expect(find.byKey(const ValueKey('open-projects')), findsOneWidget);
  });

  testWidgets('workspace groups projects behind one compact context entry', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('current-project-entry')), findsOneWidget);
    // Audit UX-P0-02: no separate strip of horizontal workspace chips — the
    // context header opens one coherent sheet instead.
    expect(find.byType(ChoiceChip), findsNothing);
    await tester.tap(find.byKey(const ValueKey('current-project-entry')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('workspace-context-sheet')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('context-switch-project')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectsScreen), findsOneWidget);
    expect(find.byKey(const ValueKey('project-project-1')), findsOneWidget);
  });

  testWidgets('workspace puts sessions above the one management route', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ProjectsRepository();
    final api = _WorkspaceSessionsApi();
    final controller = await _controller(repository)
      ..api = api
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    controller.sessionsById = {
      'session-1': Session(
        id: 'session-1',
        title: 'Swipe target',
        time: SessionTime(created: 1, updated: 1),
      ),
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    // Management is a labelled action on the project row itself, above the
    // sessions, so it reads as part of the project rather than as a session.
    double topOf(Key key) => tester.getTopLeft(find.byKey(key)).dy;
    final context = topOf(const ValueKey('current-project-entry'));
    final session = topOf(const ValueKey('session-dismiss-session-1'));
    final manage = topOf(const ValueKey('manage-project-entry'));
    expect(context, lessThan(session));
    expect(manage, lessThan(session));

    // Management destinations no longer sit on the sessions screen at all.
    expect(find.byKey(const ValueKey('worktrees-entry')), findsNothing);
    expect(find.byKey(const ValueKey('project-health-entry')), findsNothing);
    expect(
      find.byKey(const ValueKey('managed-workspaces-entry')),
      findsNothing,
    );

    await tester.tap(find.byKey(const ValueKey('manage-project-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(ManageProjectScreen), findsOneWidget);
    for (final key in const [
      'switch-project-entry',
      'worktrees-entry',
      'managed-workspaces-entry',
      'project-health-entry',
    ]) {
      expect(find.byKey(ValueKey(key)), findsOneWidget, reason: key);
    }
  });

  testWidgets('the quick-ask pill stays reachable without scrolling', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    final pill = find.byKey(const ValueKey('workspace-quick-ask'));
    expect(pill, findsOneWidget);
    expect(tester.getBottomLeft(pill).dy, lessThanOrEqualTo(640));
    expect(tester.takeException(), isNull);
  });

  testWidgets('recent session end-swipe archives with an undo window', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final api = _WorkspaceSessionsApi();
    final controller = await _controller(repository)
      ..api = api
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    controller.sessionsById = {
      'session-1': Session(
        id: 'session-1',
        title: 'Swipe target',
        time: SessionTime(created: 1, updated: 1),
      ),
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    final row = find.byKey(const ValueKey('session-dismiss-session-1'));
    expect(row, findsOneWidget);
    // The trailing popup menu remains alongside the swipe affordance.
    expect(find.byType(PopupMenuButton<String>), findsWidgets);

    // Swipe hides the row at once and offers Undo; nothing reaches the
    // server yet.
    await tester.drag(row, const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsNothing);
    expect(find.text('Archived “Swipe target”'), findsOneWidget);
    expect(repository.archiveCalls, isEmpty);
    expect(api.deleteCalls, isEmpty);

    // Undo brings the row back untouched.
    await tester.tap(find.widgetWithText(SnackBarAction, 'Undo'));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(repository.archiveCalls, isEmpty);

    // Letting the snackbar expire commits the archive.
    await tester.drag(row, const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsNothing);
    expect(find.text('Archived “Swipe target”'), findsOneWidget);
    await tester.pump(const Duration(seconds: 6));
    await tester.pumpAndSettle();
    expect(find.text('Archived “Swipe target”'), findsNothing);
    expect(repository.archiveCalls, ['session-1']);
    expect(api.deleteCalls, isEmpty);
  });

  testWidgets('delete stays in the session menu behind a confirm', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final api = _WorkspaceSessionsApi();
    final controller = await _controller(repository)
      ..api = api
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    controller.sessionsById = {
      'session-1': Session(
        id: 'session-1',
        title: 'Menu target',
        time: SessionTime(created: 1, updated: 1),
      ),
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Session actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(find.text('Delete session?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();

    expect(api.deleteCalls, ['session-1']);
    expect(find.text('Menu target'), findsNothing);
  });

  testWidgets('project discovery does not block already loaded conversations', (
    tester,
  ) async {
    final pending = Completer<List<WorkspaceProject>>();
    final repository = _ProjectsRepository()
      ..projectsLoader = () => pending.future;
    final controller = await _controller(repository)
      ..api = _WorkspaceSessionsApi()
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    await controller.refreshSessions();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pump();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(find.text('Search all sessions'), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    pending.complete(const []);
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(find.text('No projects opened'), findsOneWidget);
  });

  testWidgets(
    'failed project discovery still finds and opens a previous chat',
    (tester) async {
      const secureChannel = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        secureChannel,
        (_) async => null,
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          secureChannel,
          null,
        ),
      );
      final repository = _ProjectsRepository()
        ..projectsLoader = () async {
          throw const ProductException(
            'Project service temporarily unavailable',
          );
        }
        ..globalSessions = [
          GlobalSessionResult(
            session: Session(
              id: 'older-chat',
              title: 'Previous conversation',
              directory: '/work/previous',
            ),
            projectDirectory: '/work/previous',
          ),
        ];
      final controller = await _controller(repository)
        ..api = _WorkspaceSessionsApi()
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);
      await controller.store.upsert(
        ServerProfile(
          id: 'server',
          name: 'Test server',
          baseUrl: 'http://localhost',
        ),
      );
      await controller.store.setActiveId('server');
      await controller.refreshSessions();
      await tester.pumpWidget(
        MaterialApp(
          routes: {
            '/chat/older-chat': (_) =>
                const Scaffold(body: Text('Previous chat opened')),
          },
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Project list unavailable'), findsOneWidget);
      expect(find.text('Swipe target'), findsOneWidget);
      expect(find.text('Retry projects'), findsOneWidget);
      await tester.tap(find.text('Search all sessions'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Previous conversation'));
      await tester.pumpAndSettle();
      expect(controller.locations, [
        (directory: '/work/previous', workspace: null),
      ]);
      expect(find.text('Previous chat opened'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'retrying the project catalog keeps loaded conversations visible',
    (tester) async {
      var fail = true;
      final repository = _ProjectsRepository()
        ..projectsLoader = () async {
          if (fail) {
            throw const ProductException(
              'Project service temporarily unavailable',
            );
          }
          return const [];
        };
      final controller = await _controller(repository)
        ..api = _WorkspaceSessionsApi()
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);
      await controller.refreshSessions();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Swipe target'), findsOneWidget);
      fail = false;
      await tester.tap(find.text('Retry projects'));
      await tester.pumpAndSettle();
      expect(find.text('Project list unavailable'), findsNothing);
      expect(find.text('No projects opened'), findsOneWidget);
      expect(find.text('Swipe target'), findsOneWidget);
      expect(find.text('Search all sessions'), findsOneWidget);
    },
  );

  testWidgets('a fresh server with zero projects asks for a project folder', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final repository = _ProjectsRepository()..projects = const [];
    final controller = _FreshServerController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
      repository,
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    // The server's home folder is never a workspace: the chooser replaces
    // the session list and the quick-ask pill, but the server-wide session
    // finder stays reachable so earlier conversations are not lost.
    expect(
      find.byKey(const ValueKey('workspace-folder-chooser')),
      findsOneWidget,
    );
    expect(find.text('Choose a project folder'), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-open-folder')), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-quick-ask')), findsNothing);
    expect(find.text('Search all sessions'), findsOneWidget);
  });

  testWidgets('a home-folder project is never opened automatically', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final repository = _ProjectsRepository()
      ..projects = [
        WorkspaceProject(
          id: 'global',
          name: 'root',
          directory: '/root',
          worktrees: const [],
          updatedAt: 10,
        ),
      ];
    final controller = _FreshServerController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
      repository,
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(controller.locations, isEmpty);
    expect(controller.directory, isNull);
    expect(
      find.byKey(const ValueKey('workspace-folder-chooser')),
      findsOneWidget,
    );
  });

  testWidgets('an empty project catalog does not hide existing sessions', (
    tester,
  ) async {
    final repository = _ProjectsRepository()..projects = const [];
    final controller = await _controller(repository)
      ..api = _WorkspaceSessionsApi()
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    await controller.refreshSessions();
    await tester.pumpWidget(
      MaterialApp(
        routes: {
          '/chat/session-1': (_) =>
              const Scaffold(body: Text('Previous chat opened')),
        },
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No projects opened'), findsOneWidget);
    expect(find.text('Swipe target'), findsOneWidget);
    await tester.tap(find.text('Swipe target'));
    await tester.pumpAndSettle();
    expect(find.text('Previous chat opened'), findsOneWidget);
  });

  testWidgets('zero projects still allows finding and opening an older chat', (
    tester,
  ) async {
    const secureChannel = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secureChannel,
      (_) async => null,
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        secureChannel,
        null,
      ),
    );
    final repository = _ProjectsRepository()
      ..projects = const []
      ..globalSessions = [
        GlobalSessionResult(
          session: Session(
            id: 'older-chat',
            title: 'Previous conversation',
            directory: '/work/previous',
          ),
          projectDirectory: '/work/previous',
        ),
      ];
    final controller = await _controller(repository)
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    await controller.store.upsert(
      ServerProfile(
        id: 'server',
        name: 'Test server',
        baseUrl: 'http://localhost',
      ),
    );
    await controller.store.setActiveId('server');
    await tester.pumpWidget(
      MaterialApp(
        routes: {
          '/chat/older-chat': (_) =>
              const Scaffold(body: Text('Previous chat opened')),
        },
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Search all sessions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Previous conversation'));
    await tester.pumpAndSettle();
    expect(controller.locations, [
      (directory: '/work/previous', workspace: null),
    ]);
    expect(find.text('Previous chat opened'), findsOneWidget);
  });

  testWidgets('zero projects keeps session errors and older pages reachable', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var fail = true;
    final api = _WorkspaceSessionsApi()
      ..pageHandler = (cursor) async {
        if (fail) throw ApiException('Session list unavailable');
        return cursor == null
            ? const ServerPage(items: [], nextCursor: 'older')
            : ServerPage(
                items: [Session(id: 'older-chat', title: 'Older conversation')],
              );
      };
    final repository = _ProjectsRepository()..projects = const [];
    final controller = await _controller(repository)
      ..api = api
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    await controller.refreshSessions();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('Session list unavailable'), findsOneWidget);
    fail = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Load more sessions'));
    await tester.pumpAndSettle();
    expect(find.text('Older conversation'), findsOneWidget);
    expect(controller.hasMoreSessions, isFalse);
  });

  testWidgets(
    'a first session starts only after a project folder is opened by path',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final repository = _ProjectsRepository()..projects = const [];
      final controller = _FreshServerController(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
        repository,
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          onGenerateRoute: (settings) => MaterialPageRoute<void>(
            settings: settings,
            builder: (_) => Scaffold(
              appBar: AppBar(title: const Text('Chat route')),
              body: Text('opened:${settings.name}'),
            ),
          ),
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('workspace-quick-ask')), findsNothing);

      await tester.tap(find.byKey(const ValueKey('workspace-open-folder')));
      await tester.pumpAndSettle();

      // The home folder is refused before the server is asked.
      await tester.enterText(
        find.byKey(const ValueKey('open-folder-path')),
        '/root',
      );
      await tester.tap(find.byKey(const ValueKey('open-folder-confirm')));
      await tester.pumpAndSettle();
      expect(find.textContaining('home folder'), findsWidgets);
      expect(controller.probed, isEmpty);
      expect(controller.locations, isEmpty);

      // A missing folder is reported from the server check and not opened.
      controller.probeProblem = 'That folder was not found on the server.';
      await tester.enterText(
        find.byKey(const ValueKey('open-folder-path')),
        '/root/projects/missing',
      );
      await tester.tap(find.byKey(const ValueKey('open-folder-confirm')));
      await tester.pumpAndSettle();
      expect(controller.probed, ['/root/projects/missing']);
      expect(
        find.text('That folder was not found on the server.'),
        findsOneWidget,
      );
      expect(controller.locations, isEmpty);

      // A real folder is opened and only then can a session start in it.
      controller.probeProblem = null;
      await tester.enterText(
        find.byKey(const ValueKey('open-folder-path')),
        '/root/projects/app',
      );
      await tester.tap(find.byKey(const ValueKey('open-folder-confirm')));
      await tester.pumpAndSettle();
      expect(controller.locations, [
        (directory: '/root/projects/app', workspace: null),
      ]);
      expect(
        find.byKey(const ValueKey('workspace-folder-chooser')),
        findsNothing,
      );

      await tester.tap(find.byKey(const ValueKey('workspace-quick-ask')));
      await tester.pumpAndSettle();
      expect(controller.createSessionCalls, 1);
      expect(controller.createSessionDirectory, '/root/projects/app');
      expect(find.text('opened:/chat/session-fresh'), findsOneWidget);
    },
  );

  testWidgets('creating a folder on the managed server opens it', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final repository = _ProjectsRepository()..projects = const [];
    final controller = _FreshServerController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
      repository,
    );
    addTearDown(controller.dispose);
    final created = <String>[];
    ProjectFolderActions.canCreateOverride = true;
    ProjectFolderActions.createFolderOverride = (name) async {
      created.add(name);
      return '/root/projects/$name';
    };
    addTearDown(() {
      ProjectFolderActions.canCreateOverride = null;
      ProjectFolderActions.createFolderOverride = null;
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('workspace-create-folder')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('new-folder-name')),
      '../etc',
    );
    await tester.tap(find.byKey(const ValueKey('new-folder-create')));
    await tester.pumpAndSettle();
    expect(created, isEmpty);
    expect(find.textContaining('single folder name'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('new-folder-name')),
      'my-app',
    );
    await tester.tap(find.byKey(const ValueKey('new-folder-create')));
    await tester.pumpAndSettle();
    expect(created, ['my-app']);
    expect(controller.locations, [
      (directory: '/root/projects/my-app', workspace: null),
    ]);
    expect(
      find.byKey(const ValueKey('workspace-folder-chooser')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('workspace-quick-ask')), findsOneWidget);
  });

  testWidgets('remote servers offer open by path but not create', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final repository = _ProjectsRepository()..projects = const [];
    final controller = _FreshServerController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
      repository,
    );
    addTearDown(controller.dispose);
    ProjectFolderActions.canCreateOverride = false;
    addTearDown(() => ProjectFolderActions.canCreateOverride = null);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('workspace-create-folder')), findsNothing);
    expect(find.byKey(const ValueKey('workspace-open-folder')), findsOneWidget);
    expect(find.textContaining('cannot create folders'), findsOneWidget);
  });
}
