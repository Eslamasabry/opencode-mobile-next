import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      // Nothing contradicts the pager: no "no conversations" text, and no
      // Archived row until an archived conversation is actually known
      // (work-tab cleanup item 4). The list pages itself, and archived
      // conversations are a filter of All conversations (R3, R4): the
      // older page loads, and Work grows no archived row of its own.
      expect(
        find.text('No recent conversations in loaded results'),
        findsNothing,
      );
      expect(find.text('Archived conversations'), findsNothing);
      expect(controller.archivedSessions(), isNotEmpty);
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

  testWidgets('workspace groups projects behind one compact context entry', (
    tester,
  ) async {
    final repository = _ProjectsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    // The current project is one entry; management is disclosed in its sheet.
    double topOf(Key key) => tester.getTopLeft(find.byKey(key)).dy;
    final context = topOf(const ValueKey('current-project-entry'));
    final session = topOf(const ValueKey('session-dismiss-session-1'));
    expect(context, lessThan(session));
    expect(find.byKey(const ValueKey('manage-project-entry')), findsNothing);

    // Management destinations no longer sit on the sessions screen at all.
    expect(find.byKey(const ValueKey('worktrees-entry')), findsNothing);
    expect(find.byKey(const ValueKey('project-health-entry')), findsNothing);
    expect(
      find.byKey(const ValueKey('managed-workspaces-entry')),
      findsNothing,
    );

    // The project sheet switches and chooses where it runs; the project's
    // tools live on the Project tab (Manage project merged there,
    // slice-P3.11a), so the sheet has no Manage project row.
    await tester.tap(find.byKey(const ValueKey('current-project-entry')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('workspace-context-sheet')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('context-switch-project')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('manage-project-entry')), findsNothing);
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    final row = find.byKey(const ValueKey('session-dismiss-session-1'));
    expect(row, findsOneWidget);
    // The row's long-press menu remains alongside the swipe affordance.
    await tester.longPress(find.text('Swipe target'));
    await tester.pumpAndSettle();
    expect(find.text('Archive'), findsOneWidget);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();

    // Swipe hides the row at once and offers Undo; nothing reaches the
    // server yet.
    await tester.drag(row, const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsNothing);
    expect(find.text('Archived “Swipe target”'), findsOneWidget);
    expect(repository.archiveCalls, isEmpty);
    expect(api.deleteCalls, isEmpty);

    // Undo brings the row back untouched.
    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(repository.archiveCalls, isEmpty);

    // Letting the undo window expire commits the archive.
    await tester.drag(row, const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsNothing);
    expect(find.text('Archived “Swipe target”'), findsOneWidget);
    // KitUndo's documented window is eight seconds (the old snackbar
    // expired after six). The server is still untouched before it ends.
    expect(repository.archiveCalls, isEmpty);
    await tester.pump(KitUndo.window);
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.longPress(find.text('Menu target'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Delete'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(find.text('Delete conversation?'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('workspace-delete-confirm-button')),
    );
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pump();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(find.text('Search all conversations'), findsOneWidget);
    expect(find.byKey(const ValueKey('search-all-sessions')), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    // The one remaining search action works while project discovery is pending.
    await tester.tap(find.byKey(const ValueKey('search-all-sessions')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.byType(GlobalSessionsScreen), findsOneWidget);
    Navigator.of(tester.element(find.byType(GlobalSessionsScreen))).pop();
    pending.complete(const []);
    await tester.pumpAndSettle();
    expect(find.text('Swipe target'), findsOneWidget);
    expect(find.text('No projects opened'), findsNothing);
    expect(find.text('/work/app'), findsNothing);
    expect(find.byKey(const ValueKey('current-project-entry')), findsOneWidget);
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
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
      expect(find.text('Try again'), findsOneWidget);
      // One status line: Try again on it, Search all behind its menu.
      await tester.tap(find.byKey(const ValueKey('kit-status-more')));
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(
          of: find.byType(KitMenuPanel),
          matching: find.text('Search all conversations'),
        ),
      );
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Swipe target'), findsOneWidget);
      fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('Project list unavailable'), findsNothing);
      expect(find.text('No projects opened'), findsNothing);
      expect(find.text('/work/app'), findsNothing);
      expect(
        find.byKey(const ValueKey('current-project-entry')),
        findsOneWidget,
      );
      expect(find.text('Swipe target'), findsOneWidget);
      expect(find.byKey(const ValueKey('search-all-sessions')), findsOneWidget);
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
    const notice =
        'The saved home folder is not a project. Choose a project folder.';
    controller.locationNotice = notice;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
    expect(find.text(notice), findsOneWidget);
    expect(
      find.byKey(const ValueKey('location-recovery-notice')),
      findsOneWidget,
    );
    expect(find.text('Choose a project folder'), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-open-folder')), findsOneWidget);
    expect(find.byKey(const ValueKey('workspace-quick-ask')), findsNothing);
    expect(find.text('Search all conversations'), findsOneWidget);
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        routes: {
          '/chat/session-1': (_) =>
              const Scaffold(body: Text('Previous chat opened')),
        },
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No projects opened'), findsNothing);
    expect(find.byKey(const ValueKey('current-project-entry')), findsOneWidget);
    expect(find.text('/work/app'), findsNothing);
    expect(find.byKey(const ValueKey('current-project-entry')), findsOneWidget);
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        routes: {
          '/chat/older-chat': (_) =>
              const Scaffold(body: Text('Previous chat opened')),
        },
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('search-all-sessions')));
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    // Said in words at the end of the list; the raw error is under Details.
    expect(find.text('Could not load your conversations.'), findsOneWidget);
    expect(find.textContaining('Session list unavailable'), findsNothing);
    fail = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    // The list pages itself once it can: the older page loads.
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
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
      await _tapAction(tester, 'open-folder-confirm');
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
      await _tapAction(tester, 'open-folder-confirm');
      await tester.pumpAndSettle();
      expect(controller.probed, ['/root/projects/missing']);
      expect(
        find.text('That folder was not found on the server.'),
        findsOneWidget,
      );
      // This app cannot make folders on someone else's server.
      expect(
        find.byKey(const ValueKey('open-folder-create-missing')),
        findsNothing,
      );
      expect(controller.locations, isEmpty);

      // A real folder is opened and only then can a session start in it.
      controller.probeProblem = null;
      await tester.enterText(
        find.byKey(const ValueKey('open-folder-path')),
        '/root/projects/app',
      );
      await _tapAction(tester, 'open-folder-confirm');
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
    await _tapAction(tester, 'new-folder-create');
    await tester.pumpAndSettle();
    expect(created, isEmpty);
    expect(find.textContaining('single folder name'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('new-folder-name')),
      'my-app',
    );
    await _tapAction(tester, 'new-folder-create');
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: WorkspaceScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('workspace-create-folder')), findsNothing);
    expect(find.byKey(const ValueKey('workspace-open-folder')), findsOneWidget);
    expect(find.textContaining('cannot create folders'), findsNothing);
    await tester.tap(find.text('Details'));
    await tester.pumpAndSettle();
    expect(find.textContaining('cannot create folders'), findsOneWidget);
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

    Future<void> openSheet(WidgetTester tester, {bool browse = false}) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('workspace-open-folder')));
      await tester.pumpAndSettle();
      // The sheet opens on its start page; browsing is one tap away.
      if (browse) {
        await tester.tap(find.byKey(const ValueKey('open-project-browse')));
        await tester.pumpAndSettle();
      }
    }

    testWidgets('its projects are listed and open with one tap', (
      tester,
    ) async {
      linux.projects.addAll(['demo', 'hello']);
      await openSheet(tester, browse: true);
      expect(find.byKey(const ValueKey('in-app-projects')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('in-app-project-demo')));
      await tester.pumpAndSettle();
      expect(controller.folderEvents, ['open:/root/projects/demo']);
      expect(linux.created, isEmpty);
      expect(controller.probed, isEmpty, reason: 'OpenCode is never probed');
    });

    testWidgets('a new project needs only a safe name', (tester) async {
      await openSheet(tester);
      await _tapAction(tester, 'open-project-new');
      await tester.pumpAndSettle();
      for (final bad in const ['', '../etc', 'a/b', '.hidden']) {
        await tester.enterText(
          find.byKey(const ValueKey('phone-new-folder-name')),
          bad,
        );
        await _tapAction(tester, 'phone-new-folder-create');
        await tester.pumpAndSettle();
        expect(linux.created, isEmpty, reason: bad);
        // Still on the name step, which says why.
        expect(
          find.byKey(const ValueKey('phone-new-folder-name')),
          findsOneWidget,
          reason: bad,
        );
      }

      await tester.enterText(
        find.byKey(const ValueKey('phone-new-folder-name')),
        'hello',
      );
      await _tapAction(tester, 'phone-new-folder-create');
      await tester.pumpAndSettle();
      expect(linux.created, ['/root/projects/hello']);
      expect(
        linux.scripts.last,
        BuiltinLinux.createFolderScript('/root/projects/hello'),
      );
      // Checked and made through Ubuntu, so OpenCode has nothing stale to
      // forget: it just opens it.
      expect(controller.folderEvents, ['open:/root/projects/hello']);
      expect(controller.probed, isEmpty);
    });

    testWidgets('a name that already exists just opens it', (tester) async {
      linux.projects.add('hello');
      await openSheet(tester);
      await _tapAction(tester, 'open-project-new');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('phone-new-folder-name')),
        'hello',
      );
      await _tapAction(tester, 'phone-new-folder-create');
      await tester.pumpAndSettle();
      expect(linux.created, isEmpty);
      expect(controller.folderEvents, ['open:/root/projects/hello']);
    });

    testWidgets('a typed path that does not exist offers Create it', (
      tester,
    ) async {
      await openSheet(tester, browse: true);
      await tester.tap(find.byKey(const ValueKey('kit-sheet-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Enter a path'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('open-folder-path')),
        '/root/projects/missing',
      );
      await _tapAction(tester, 'open-folder-confirm');
      await tester.pumpAndSettle();
      expect(find.text('That folder does not exist yet.'), findsOneWidget);
      expect(controller.probed, isEmpty, reason: 'checked in Ubuntu only');
      expect(controller.folderEvents, isEmpty);

      await tester.tap(
        find.byKey(const ValueKey('open-folder-create-missing')),
      );
      await tester.pumpAndSettle();
      expect(linux.created, ['/root/projects/missing']);
      expect(controller.folderEvents, ['open:/root/projects/missing']);
    });

    testWidgets('Create a new folder makes it inside the app', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: WorkspaceScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('workspace-create-folder')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('new-folder-name')),
        'app',
      );
      await _tapAction(tester, 'new-folder-create');
      await tester.pumpAndSettle();
      expect(linux.created, ['/root/projects/app']);
      expect(controller.folderEvents, ['open:/root/projects/app']);
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
