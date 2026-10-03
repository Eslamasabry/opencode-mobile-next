import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/phone_project_scan.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

  /// Disposes and opens in order, so a test can see a new folder's cached
  /// instance dropped before the folder is opened.
  final folderEvents = <String>[];

  @override
  Future<void> disposeFolderInstance(String directory) async =>
      folderEvents.add('dispose:$directory');

  @override
  Future<void> selectLocation({String? directory, String? workspace}) {
    folderEvents.add('open:$directory');
    return super.selectLocation(directory: directory, workspace: workspace);
  }

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
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: screen,
);

Widget _host(ProjectsScreen screen) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
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

// Kit fields rebuild validation and button state on the next frame; sheet
// actions may need scrolling after the keyboard or an error changes the body.
Future<void> _tapAction(WidgetTester tester, String key) async {
  await tester.pump();
  final action = find.byKey(ValueKey(key));
  await tester.ensureVisible(action);
  await tester.pumpAndSettle();
  await tester.tap(action);
}

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

    // Rename lives in the project's row menu (screen-work-4).
    await tester.longPress(find.byKey(const ValueKey('project-project-1')));
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

  testWidgets('the project list leaves out the AI Team\'s own folders', (
    tester,
  ) async {
    final repository = _ProjectsRepository()
      ..projects = const [
        WorkspaceProject(
          id: 'team-refinery',
          name: 'refinery',
          directory: '/root/aiteam/city/.gc/worktrees/my-app/refinery',
          worktrees: [],
          updatedAt: 9,
        ),
        WorkspaceProject(
          id: 'team-origin',
          name: 'my-app.git',
          directory: '/root/aiteam/origins/my-app.git',
          worktrees: [],
          updatedAt: 8,
        ),
        WorkspaceProject(
          id: 'my-app',
          name: 'my-app',
          directory: '/root/projects/my-app',
          worktrees: [
            '/root/aiteam/city/.gc/worktrees/my-app/polecats/gastown.furiosa',
          ],
          updatedAt: 1,
        ),
      ];
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _direct(ProjectsScreen(controller: controller, selectedProjectID: null)),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('project-my-app')), findsOneWidget);
    expect(find.byKey(const ValueKey('project-team-refinery')), findsNothing);
    expect(find.byKey(const ValueKey('project-team-origin')), findsNothing);
  });

  testWidgets('R13: no refresh in the top bar, and Open projects sits one '
      'section gap under the folder actions', (tester) async {
    var loads = 0;
    final repository = _ProjectsRepository()
      ..projectsLoader = () async {
        loads += 1;
        return const [
          WorkspaceProject(
            id: 'my-app',
            name: 'my-app',
            directory: '/root/projects/my-app',
            worktrees: [],
            updatedAt: 1,
          ),
        ];
      };
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _direct(ProjectsScreen(controller: controller, selectedProjectID: null)),
    );
    await tester.pumpAndSettle();

    expect(find.byTooltip('Refresh projects'), findsNothing);
    final actions = tester.getRect(
      find.byKey(const ValueKey('projects-open-folder')),
    );
    expect(
      tester.getTopLeft(find.text('Open projects')).dy - actions.bottom,
      moreOrLessEquals(22, epsilon: 0.01),
    );
    expect(loads, 1);
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
    await tester.longPress(find.byKey(const ValueKey('project-project-1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('rename-project-project-1')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('project-name-input')),
      'app',
    );
    await _tapAction(tester, 'confirm-rename-project');
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

  group('OpenCode inside the app', () {
    late _InAppLinux linux;
    late _FreshServerController controller;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      linux = _InAppLinux();
      ProjectFolderActions.builtinLinuxOverride = linux;
      // Its fake Ubuntu has no files on disk: folders are listed through it.
      ProjectFolderActions.folderListerOverride = BuiltinFolders.throughUbuntu(
        linux,
      ).list;
      controller = _FreshServerController(
        _InAppStore(prefs: await SharedPreferences.getInstance()),
        _ProjectsRepository()..projects = const [],
      );
    });

    tearDown(() {
      ProjectFolderActions.builtinLinuxOverride = null;
      ProjectFolderActions.folderListerOverride = null;
      controller.dispose();
    });

    Future<void> mountProjects(WidgetTester tester) async {
      await tester.pumpWidget(
        _direct(
          ProjectsScreen(controller: controller, selectedProjectID: null),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('restored folders the server has not opened are listed', (
      tester,
    ) async {
      ProjectFolderActions.projectSpaceOverride = () async => [
        const PhoneProject(
          name: 'restored',
          path: '/root/projects/restored',
          kind: PhoneProjectKind.node,
          hasGit: true,
        ),
        const PhoneProject(
          name: 'plain',
          path: '/root/projects/plain',
          kind: PhoneProjectKind.git,
          hasGit: false,
        ),
      ];
      addTearDown(() => ProjectFolderActions.projectSpaceOverride = null);
      await mountProjects(tester);
      expect(find.text('In \u2066/root/projects\u2069'), findsOneWidget);
      expect(find.text('restored'), findsOneWidget);
      expect(find.text('plain'), findsOneWidget);
      // Each row says what it is; a repository carries the Git badge.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('project-space-restored')),
          matching: find.text('Git'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('project-space-plain')),
          matching: find.text('Git'),
        ),
        findsNothing,
      );
      await tester.tap(find.text('restored'));
      await tester.pumpAndSettle();
      expect(controller.folderEvents, ['open:/root/projects/restored']);
    });

    testWidgets('folders already opened are not listed twice, and an empty '
        'section is hidden', (tester) async {
      ProjectFolderActions.projectSpaceOverride = () async => [
        const PhoneProject(
          name: 'known',
          path: '/root/projects/known',
          kind: PhoneProjectKind.node,
          hasGit: false,
        ),
      ];
      addTearDown(() => ProjectFolderActions.projectSpaceOverride = null);
      controller = _FreshServerController(
        _InAppStore(prefs: await SharedPreferences.getInstance()),
        _ProjectsRepository()
          ..projects = const [
            WorkspaceProject(
              id: 'k',
              name: 'known',
              directory: '/root/projects/known',
              worktrees: [],
              updatedAt: 1,
            ),
          ],
      );
      await mountProjects(tester);
      expect(find.text('In \u2066/root/projects\u2069'), findsNothing);
      expect(find.byKey(const ValueKey('project-space-known')), findsNothing);
      expect(find.byKey(const ValueKey('project-k')), findsOneWidget);
    });

    testWidgets('the empty state is one short line', (tester) async {
      ProjectFolderActions.projectSpaceOverride = () async => const [];
      addTearDown(() => ProjectFolderActions.projectSpaceOverride = null);
      await mountProjects(tester);
      expect(find.text('No projects opened'), findsOneWidget);
      expect(
        find.text('Projects you open or create appear here.'),
        findsOneWidget,
      );
      expect(find.textContaining('choose one'), findsNothing);
    });
  });
}

class _InAppStore extends ProfileStore {
  _InAppStore({required super.prefs});

  final profile = ServerProfile(
    id: 'builtin',
    name: 'This phone, built-in (OpenCode)',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
  );

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

/// Ubuntu inside the app, as far as project folders go: a set of folders
/// under /root/projects that the scripts list, test and create.
class _InAppLinux extends BuiltinLinux {
  final projects = <String>[];
  final extraFolders = <String>{};
  final created = <String>[];
  final scripts = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
  );

  bool _exists(String path) =>
      extraFolders.contains(path) ||
      projects.any((name) => '${BuiltinLinux.projectsDir}/$name' == path);

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    scripts.add(script);
    if (script == BuiltinLinux.listProjectsScript()) {
      return BuiltinLinuxRunResult(
        exitCode: 0,
        output: projects.map((name) => '$name\n').join(),
      );
    }
    final test = RegExp(r"^test -d '(.*)'$").firstMatch(script);
    if (test != null) {
      return BuiltinLinuxRunResult(
        exitCode: _exists(test[1]!) ? 0 : 1,
        output: '',
      );
    }
    final create = RegExp(r"dir='([^']*)'").firstMatch(script);
    if (create != null) {
      final path = create[1]!;
      if (_exists(path)) {
        return BuiltinLinuxRunResult(exitCode: 0, output: 'exists $path\n');
      }
      created.add(path);
      extraFolders.add(path);
      return BuiltinLinuxRunResult(exitCode: 0, output: 'created $path\n');
    }
    return const BuiltinLinuxRunResult(exitCode: 127, output: 'unexpected');
  }
}
