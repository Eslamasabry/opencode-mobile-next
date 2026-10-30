import '../support/fake_server_capabilities.dart';
// Behaviour of screen-work-4's rebuilt pages (wave 2b): Projects with its
// row menu and in-dialog rename, and the isolated task sheet's staged wait.
// Development services has its own file (test/development_services_screen_
// test.dart).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/isolated_task_sheet.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _app = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: ['/work/app-proof'],
  updatedAt: 2,
);
const _backend = WorkspaceProject(
  id: 'project-2',
  name: 'Backend',
  directory: '/work/backend',
  worktrees: [],
  updatedAt: 1,
);

class ProjectsRepository implements ProductRepository {
  List<WorkspaceProject> projects = const [_app, _backend];
  Object? renameError;
  Object? listError;
  String? renamedName;
  Completer<WorktreeInfo> create = Completer<WorktreeInfo>();

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    if (listError case final error?) throw error;
    return List.of(projects);
  }

  @override
  Future<WorkspaceProject?> loadCurrentProject() async => _app;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => create.future;

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) async {
    if (renameError case final error?) throw error;
    renamedName = name;
    final previous = projects.singleWhere((p) => p.id == projectID);
    final updated = WorkspaceProject(
      id: previous.id,
      name: name.isEmpty ? 'app' : name,
      directory: previous.directory,
      worktrees: previous.worktrees,
      updatedAt: previous.updatedAt,
    );
    projects = [
      for (final p in projects)
        if (p.id == projectID) updated else p,
    ];
    return updated;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class ProjectsController extends ConnectionController {
  ProjectsController(super.store, this.projects) {
    repository = projects;
    directory = '/work/app';
    status = StreamStatus.connected;
  }
  final ProjectsRepository projects;
  String? failSwitch;

  @override
  ServerCapabilities get capabilities => testServerCapabilities;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async => projects;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locationError = failSwitch;
    if (failSwitch != null) return;
    this.directory = directory;
    notifyListeners();
  }
}

Future<ProjectsController> projectsController([
  ProjectsRepository? repository,
]) async {
  SharedPreferences.setMockInitialValues({});
  final controller = ProjectsController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    repository ?? ProjectsRepository(),
  );
  controller.adoptConnectedProfileForTesting(
    ServerProfile(id: 'remote', name: 'Studio', baseUrl: 'http://127.0.0.1:1'),
  );
  return controller;
}

Widget app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Finder rich(String text) => find.textContaining(text, findRichText: true);

/// Opens the isolated task sheet from a button and records its result.
class SheetHost extends StatefulWidget {
  const SheetHost({super.key, required this.controller});
  final ConnectionController controller;

  @override
  State<SheetHost> createState() => SheetHostState();
}

class SheetHostState extends State<SheetHost> {
  final results = <Session?>[];

  @override
  Widget build(BuildContext context) => Center(
    child: GestureDetector(
      key: const Key('open-sheet'),
      behavior: HitTestBehavior.opaque,
      onTap: () async => results.add(
        await showIsolatedTaskSheet(
          context,
          controller: widget.controller,
          project: _app,
        ),
      ),
      child: const SizedBox.square(dimension: 80),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Projects', () {
    testWidgets('the current project says so in words and carries the mark', (
      tester,
    ) async {
      final controller = await projectsController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(
          ProjectsScreen(
            controller: controller,
            selectedProjectID: 'project-1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(rich('Current · '), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('project-project-1')),
          matching: find.byKey(const ValueKey('kit-row-current-mark')),
        ),
        findsOneWidget,
      );
      expect(rich('1 worktree'), findsOneWidget);
      // No per-row pencil: rename lives in the row menu.
      expect(find.byTooltip('Rename OpenCode Mobile'), findsNothing);
    });

    testWidgets('rename runs from the row menu and saves inside the dialog', (
      tester,
    ) async {
      final repository = ProjectsRepository();
      final controller = await projectsController(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(
          ProjectsScreen(
            controller: controller,
            selectedProjectID: 'project-1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.longPress(find.byKey(const ValueKey('project-project-2')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('rename-project-project-2')));
      await tester.pumpAndSettle();
      expect(find.text('Rename project'), findsOneWidget);
      expect(
        find.text('Clear the name to use the project folder name.'),
        findsOneWidget,
      );
      await tester.enterText(
        find.byKey(const ValueKey('project-name-input')),
        'API',
      );
      await tester.tap(find.byKey(const ValueKey('confirm-rename-project')));
      await tester.pumpAndSettle();
      expect(repository.renamedName, 'API');
      expect(find.text('Rename project'), findsNothing);
      expect(find.text('API'), findsOneWidget);
    });

    testWidgets('a failed rename stays in the dialog with the reason', (
      tester,
    ) async {
      final repository = ProjectsRepository()
        ..renameError = const ProductException('The server said no');
      final controller = await projectsController(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(ProjectsScreen(controller: controller, selectedProjectID: null)),
      );
      await tester.pumpAndSettle();
      await tester.longPress(find.byKey(const ValueKey('project-project-2')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('rename-project-project-2')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('project-name-input')),
        'API',
      );
      await tester.tap(find.byKey(const ValueKey('confirm-rename-project')));
      await tester.pumpAndSettle();
      expect(find.text('Rename project'), findsOneWidget);
      expect(find.textContaining('The server said no'), findsOneWidget);
      expect(find.text('API'), findsWidgets, reason: 'typed name is kept');
    });

    testWidgets('search says when nothing matches and clears', (tester) async {
      final controller = await projectsController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(ProjectsScreen(controller: controller, selectedProjectID: null)),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('project-search')),
        'zzz',
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('project-project-1')), findsNothing);
      expect(rich('zzz'), findsWidgets);
      await tester.tap(find.text('Clear search'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('project-project-1')), findsOneWidget);
    });

    testWidgets('a failed switch stays on the list with the reason', (
      tester,
    ) async {
      final controller = await projectsController()
        ..failSwitch = 'Folder not found on the server';
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(ProjectsScreen(controller: controller, selectedProjectID: null)),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-project-2')));
      await tester.pumpAndSettle();
      expect(find.text('Folder not found on the server'), findsOneWidget);
      expect(find.byKey(const ValueKey('project-project-2')), findsOneWidget);
    });

    testWidgets('a list that fails to load offers Try again', (tester) async {
      final repository = ProjectsRepository()
        ..listError = const ProductException('Connection reset');
      final controller = await projectsController(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        app(ProjectsScreen(controller: controller, selectedProjectID: null)),
      );
      await tester.pumpAndSettle();
      expect(find.text("Projects didn't load"), findsOneWidget);
      repository.listError = null;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('project-project-1')), findsOneWidget);
    });

    for (final (label, size, scale) in [
      ('phone', const Size(412, 915), 1.0),
      ('narrow large text', const Size(320, 800), 2.0),
      ('wide', const Size(1280, 800), 1.0),
    ]) {
      testWidgets('lays out without overflow: $label', (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = scale;
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        addTearDown(tester.view.reset);
        final controller = await projectsController();
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          app(
            ProjectsScreen(
              controller: controller,
              selectedProjectID: 'project-1',
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      });
    }
  });

  group('Isolated task sheet', () {
    Future<SheetHostState> open(
      WidgetTester tester,
      ProjectsController controller,
    ) async {
      await tester.pumpWidget(
        app(Scaffold(body: SheetHost(controller: controller))),
      );
      await tester.tap(find.byKey(const Key('open-sheet')));
      await tester.pumpAndSettle();
      return tester.state<SheetHostState>(find.byType(SheetHost));
    }

    testWidgets('the form offers Close, and back closes it too', (
      tester,
    ) async {
      final controller = await projectsController();
      addTearDown(controller.dispose);
      final host = await open(tester, controller);
      expect(find.byKey(const Key('isolated-task-prompt')), findsOneWidget);
      await tester.ensureVisible(find.byKey(const Key('isolated-task-close')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('isolated-task-close')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('isolated-task-sheet')), findsNothing);
      expect(host.results, [null]);

      await tester.tap(find.byKey(const Key('open-sheet')));
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('isolated-task-sheet')), findsNothing);
      expect(host.results, [null, null]);
    });

    Future<void> start(WidgetTester tester) async {
      await tester.ensureVisible(find.byKey(const Key('isolated-task-start')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('isolated-task-start')));
    }

    testWidgets('the wait is staged, says how long it usually takes and '
        'what stopping means', (tester) async {
      final controller = await projectsController();
      addTearDown(controller.dispose);
      await open(tester, controller);
      await start(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text('Making the copy…'), findsOneWidget);
      expect(rich('Making the copy'), findsWidgets);
      expect(rich('1 of 3'), findsOneWidget);
      expect(rich('Usually 1–3 minutes'), findsOneWidget);
      expect(rich('Project › Worktrees'), findsOneWidget);
      expect(find.byKey(const Key('isolated-task-stop')), findsOneWidget);
      await tester.tap(find.byKey(const Key('isolated-task-stop')));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pump(const Duration(seconds: 1));
      expect(find.byKey(const Key('isolated-task-sheet')), findsNothing);
    });

    testWidgets('a failed create says so in words and offers Try again and '
        'Close', (tester) async {
      final repository = ProjectsRepository();
      final controller = await projectsController(repository);
      addTearDown(controller.dispose);
      await open(tester, controller);
      await start(tester);
      await tester.pump();
      repository.create.completeError(
        const ProductException('Not a Git repository'),
      );
      await tester.pumpAndSettle();
      expect(find.text("Couldn't make the copy"), findsOneWidget);
      expect(find.byKey(const Key('isolated-task-try-again')), findsOneWidget);
      await tester.ensureVisible(
        find.byKey(const Key('isolated-task-dismiss')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('isolated-task-dismiss')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('isolated-task-sheet')), findsNothing);
    });
  });
}
