import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _WorkspaceSessionsApi extends OpenCodeApi {
  _WorkspaceSessionsApi() : super(baseUrl: 'http://localhost');

  final deleteCalls = <String>[];
  bool deleted = false;

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

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => List.of(projects);

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
}


/// A fresh server with zero projects: no location is ever selected, and the
/// session-create call must still work against the server's own default
/// directory (the transport omits the directory parameter when none is set).
class _FreshServerController extends _ProjectsController {
  _FreshServerController(super.store, super.projectsRepository) {
    directory = null;
  }

  int createSessionCalls = 0;
  String? createSessionDirectory;

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

  testWidgets('workspace groups projects behind one compact native entry', (
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
    expect(find.byType(ChoiceChip), findsNothing);
    await tester.tap(find.byKey(const ValueKey('current-project-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectsScreen), findsOneWidget);
    expect(find.byKey(const ValueKey('project-project-1')), findsOneWidget);
  });

  testWidgets('recent session end-swipe runs the delete confirm flow', (
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

    await tester.drag(row, const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('Delete session?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();

    expect(api.deleteCalls, ['session-1']);
    expect(find.text('Swipe target'), findsNothing);
  });

  testWidgets('a fresh server with zero projects keeps the quick-ask pill', (
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

    // The empty state explains the situation without dead-ending the flow.
    expect(find.text('No projects opened'), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-quick-ask')), findsOneWidget);
  });

  testWidgets(
    'the quick-ask pill creates a first session with no directory selected',
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

      await tester.tap(find.byKey(const ValueKey('workspace-quick-ask')));
      await tester.pumpAndSettle();

      expect(controller.createSessionCalls, 1);
      // No project means no directory parameter: the transport omits it and
      // the server scopes the session to its own default directory.
      expect(controller.createSessionDirectory, isNull);
      expect(find.text('opened:/chat/session-fresh'), findsOneWidget);
    },
  );
}
